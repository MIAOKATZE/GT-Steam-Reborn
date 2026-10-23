package com.miaokatze.gtsr.common.critical;

import net.minecraft.nbt.NBTTagCompound;

/** Real-NBT, synthetic power receipts: no FML, hatches, network, or world startup. */
public final class CriticalStellarContract {

    private static int checks;

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }

    private static final class Port implements CriticalStellarRuntime.PowerPort {

        long available = Long.MAX_VALUE, spent, refunded, refundCapacity = Long.MAX_VALUE;
        boolean uncertain;
        String identity = "0:1:2:3";

        public long available() { return available; }
        public CriticalEnergyDebit debit(long requested) {
            long actual = Math.min(available, requested);
            spent += actual;
            return new CriticalEnergyDebit(requested, actual, uncertain);
        }
        public long refund(long requested) {
            long actual = Math.min(refundCapacity, requested);
            refunded += actual;
            refundCapacity -= actual;
            return actual;
        }
        public String identity() { return identity; }
        public void changed() {}
    }

    private static void tick(NBTTagCompound n, Port port, long tick) {
        CriticalStellarRuntime.tick(n, port, "machine", tick, true, true, true, true, false);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) for (Class<?> type : new Class<?>[] { CriticalStellarRuntime.class,
            CriticalStellarPolicy.class, CriticalEnergyDebit.class }) {
            java.nio.file.Path actual = java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!actual.toRealPath().equals(java.nio.file.Path.of(args[0]).toRealPath()))
                throw new AssertionError("Product overlay: " + type.getName() + " from " + actual);
            System.out.println("SOURCE " + type.getName() + "=" + actual);
        }
        check(CriticalStellarPolicy.duration(1) == 1 && CriticalStellarPolicy.duration(17) == 2,
            "Native duration rounds upward, at least one tick");
        check(CriticalStellarPolicy.duration(Integer.MAX_VALUE) == 134217728, "Native duration does not overflow");
        check(CriticalStellarPolicy.runEuPerTick(8196) == 10_000_000L, "Full parallel costs exactly ten million");
        check(CriticalStellarPolicy.runEuPerTick(1) > 1_000_000L, "Fractional load does not truncate to zero");
        check(CriticalStellarPolicy.powerLimitedParallel(1_000_000L, 8196) == 0, "Hold alone cannot buy processing");
        check(CriticalStellarPolicy.powerLimitedParallel(10_000_000L, 8196) == 8196, "Full real power supports cap");
        check(CriticalStellarPolicy.processingPowerBudget(9_000_000L, 34_359_738_240L, true) == 10_000_000L,
            "Already-paid hold restores the running budget and full parallel");
        check(CriticalStellarPolicy.processingPowerBudget(Long.MAX_VALUE, 5_000_000L, true) == 5_000_000L,
            "Running budget cannot exceed the real host cap or overflow");
        check(CriticalStellarPolicy.processingPowerBudget(10_000_000L, 34_359_738_240L, false) == 0,
            "Unpaid hold cannot authorize a material-consuming batch");
        check(CriticalStellarPolicy.nativeCapability(20_480_000_000L, 7_864_320L, 8_388_608L), "MkV accepted");
        check(!CriticalStellarPolicy.nativeCapability(20_480_000_001L, 1, 8_388_608L)
            && !CriticalStellarPolicy.nativeCapability(-1, 1, 8_388_608L), "Unknown/over-MkV threshold rejected");
        Port port = new Port();
        NBTTagCompound n = new NBTTagCompound();
        port.available = 100;
        tick(n, port, 1);
        check(n.getLong("ignitionPaid") == 100 && !n.getBoolean("hot"), "Ignition is segmented from real debit");
        CriticalStellarRuntime.tick(n, port, "machine", 2, true, false, true, true, false);
        check(n.getLong("ignitionPaid") == 100 && port.spent == 100, "Unloading freezes payment");
        n = (NBTTagCompound) n.copy();
        tick(n, port, 2);
        check(n.getLong("ignitionPaid") == 200, "NBT reload preserves paid ignition");
        port.uncertain = true;
        tick(n, port, 3);
        check(n.getBoolean("uncertain") && n.getLong("ignitionPaid") == 300, "Known partial prefix survives uncertain tail");
        tick(n, port, 4);
        check(port.spent == 300, "Uncertain debit is never replayed");
        port = new Port(); n = new NBTTagCompound(); tick(n, port, 1);
        check(n.getBoolean("hot") && n.getInteger("initializingTicks") == 200
            && n.getLong("reserve") == CriticalStellarPolicy.RESERVE_EU, "Ignition separately buys real reserve");
        check(port.spent == CriticalStellarPolicy.IGNITION_EU + CriticalStellarPolicy.RESERVE_EU,
            "Initialization is included in the ignition fee");
        for (int t = 2; t <= 201; t++) tick(n, port, t);
        check(!CriticalStellarRuntime.ready(n), "Final initialization tick cannot process before paying hold");
        tick(n, port, 202);
        check(CriticalStellarRuntime.ready(n), "Hot session starts after paid hold");
        long before = port.spent;
        check(CriticalStellarRuntime.payProcessing(n, port, "batch", 10_000_000L), "Paid processing tick advances");
        check(port.spent - before == 9_000_000L, "Running pays only the difference above already-paid hold");
        check(!CriticalStellarRuntime.payProcessing(n, port, "batch", 10_000_000L), "Same tick cannot buy two advances");
        CriticalStellarRuntime.finishBatch(n, port);
        port.available = 0;
        tick(n, port, 203);
        check(n.getLong("reserve") == CriticalStellarPolicy.RESERVE_EU - CriticalStellarPolicy.HOLD_EUT,
            "Outage consumes paid reserve without free reset");
        long reserve = n.getLong("reserve");
        port.available = 500_000L;
        CriticalStellarRuntime.refillReserve(n, port);
        check(n.getLong("reserve") == reserve + 500_000L, "Spare power refills only real prepaid reserve");
        reserve = n.getLong("reserve");
        CriticalStellarRuntime.tick(n, port, "machine", 204, false, true, true, true, false);
        check(n.getLong("reserve") == reserve && !CriticalStellarRuntime.ready(n), "Untrusted owner freezes heat and production");
        NBTTagCompound copy = (NBTTagCompound) n.copy();
        CriticalStellarRuntime.markItemCopy(copy);
        tick(copy, port, 205);
        check(!CriticalStellarRuntime.ready(copy) && copy.getLong("reserve") == reserve,
            "Controller item copy cannot resume paid session");
        port.refundCapacity = 25;
        CriticalStellarRuntime.shutdown(n, port);
        check(!n.getBoolean("hot") && n.getLong("refund") == reserve - 25,
            "Full local buffer leaves auditable unused-EU refund journal");
        port.refundCapacity = Long.MAX_VALUE;
        CriticalStellarRuntime.settleRefund(n, port);
        check(!CriticalStellarRuntime.hasAssets(n) && port.refunded == reserve,
            "Only unused prepaid EU returns locally; consumed ignition is never refunded");
        port = new Port(); n = new NBTTagCompound(); tick(n, port, 1);
        port.identity = "0:9:9:9"; tick(n, port, 2);
        check(n.getBoolean("identityMismatch") && !CriticalStellarRuntime.ready(n), "Canonical host mismatch blocks copied heat");
        CriticalStellarRuntime.shutdown(n, port);
        CriticalStellarRuntime.settleRefund(n, port);
        check(port.refunded == 0 && n.getBoolean("hot"), "Off/refund cannot redeem a mismatched host's copied heat");
        n = new NBTTagCompound(); n.setInteger("schema", 99);
        tick(n, port, 5);
        check(CriticalStellarRuntime.hasAssets(n) && !CriticalStellarRuntime.ready(n), "Future schema freezes instead of resetting");
        port = new Port(); n = new NBTTagCompound(); tick(n, port, 1);
        for (int t = 2; t <= 202; t++) tick(n, port, t);
        port.available = 0;
        for (int t = 203; t < 203 + CriticalStellarPolicy.GRACE_TICKS - 1; t++)
            CriticalStellarRuntime.tick(n, port, "machine", t, true, true, true, false, false);
        check(n.getBoolean("hot") && n.getInteger("lostTicks") == 1199, "Confirmed structure loss gets loaded-tick grace");
        CriticalStellarRuntime.tick(n, port, "machine", 1402, true, true, true, false, false);
        check(!n.getBoolean("hot"), "Loss grace expires without inventing a new free reserve");
        port = new Port(); n = new NBTTagCompound(); port.available = 100; tick(n, port, 1);
        CriticalStellarRuntime.tick(n, port, "machine", 2, true, true, false, true, false);
        check(port.refunded == 100 && !CriticalStellarRuntime.hasAssets(n), "Explicit off returns unused segmented ignition locally");
        System.out.println("PASS CriticalStellarContract checks=" + checks);
    }
}
