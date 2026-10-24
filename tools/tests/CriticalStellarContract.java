package com.miaokatze.gtsr.common.critical;

import net.minecraft.nbt.NBTTagCompound;

/** Real NBT and synthetic confirmed receipts, no claim of real hatch or cross-save atomicity. */
public final class CriticalStellarContract {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static final class Port implements CriticalStellarRuntime.PowerPort {
        long available = Long.MAX_VALUE, spent, fuel = Long.MAX_VALUE, burned, plasma, refunded;
        long refundCapacity = Long.MAX_VALUE, outputCapacity = Long.MAX_VALUE;
        boolean uncertain, fuelUncertain, outputThrows;
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
        public boolean burnHelium3(long requested, java.util.function.LongConsumer confirmed) {
            long actual = Math.min(fuel, requested);
            burned += actual;
            fuel -= actual;
            confirmed.accept(actual);
            return !fuelUncertain;
        }
        public long emitHeliumPlasma(long requested) {
            if (outputThrows) throw new IllegalStateException("uncertain output");
            long actual = Math.min(outputCapacity, requested);
            plasma += actual;
            outputCapacity -= actual;
            return actual;
        }
        public String identity() { return identity; }
        public void changed() {}
    }
    private static void tick(NBTTagCompound n, Port p, long tick) {
        CriticalStellarRuntime.tick(n, p, "machine", tick, true, true, true, true, false);
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) for (Class<?> type : new Class<?>[] { CriticalStellarRuntime.class,
            CriticalStellarPolicy.class, CriticalEnergyDebit.class }) {
            java.nio.file.Path actual = java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!actual.toRealPath().equals(java.nio.file.Path.of(args[0]).toRealPath()))
                throw new AssertionError("Product overlay: " + type.getName());
        }
        check(CriticalStellarPolicy.runEuPerTick(101, 3) == 16, "One rounding after actual parallel multiplication");
        check(CriticalStellarPolicy.runEuPerTick(2_147_483_647L, 8196) == 880038798541L, "Long precision above int range");
        check(CriticalStellarPolicy.runEuPerTick(0, 1) == 0, "Zero native energy remains zero");
        check(CriticalStellarPolicy.powerLimitedParallel(15, 101, 8196) == 2, "Real remaining power limits fusion");
        check(CriticalStellarPolicy.processingPowerBudget(9, 100, true) == 9, "Sustain is not added back to budget");
        check(CriticalStellarPolicy.processingPowerBudget(9, 100, false) == 0, "No unpaid sustain authorizes fusion");
        check(CriticalStellarPolicy.duration(Integer.MAX_VALUE) == 134217728, "Duration ceil divided by sixteen");
        check(CriticalStellarPolicy.nativeCapability(20_480_000_000L, 8_388_608L, 8_388_608L), "MkV admitted");
        check(!CriticalStellarPolicy.nativeCapability(20_480_000_001L, 1, 8_388_608L), "Over MkV rejected");
        NBTTagCompound n = new NBTTagCompound();
        Port p = new Port();
        p.available = 100;
        tick(n, p, 1);
        check(n.getLong("ignitionPaid") == 100 && p.burned == 0, "Segmented ignition holds only actual EU");
        CriticalStellarRuntime.tick(n,p,"machine",2,true,false,true,true,false);
        check(p.spent == 100, "Unloaded ticks freeze billing");
        p.available = Long.MAX_VALUE;
        p.fuel = 3;
        tick(n,p,3);
        check(!n.getBoolean("hot") && n.getLong("ignitionHelium3Paid") == 3, "Partial fuel never grants ignition");
        check(n.getLong("pendingPlasmaL") == 1 && n.getInteger("plasmaHalfRemainder") == 1, "Partial real extraction alone produces exact halves");
        n = (NBTTagCompound)n.copy();
        p.fuel = Long.MAX_VALUE;
        tick(n,p,4);
        check(n.getBoolean("hot") && !CriticalStellarRuntime.ready(n), "Full ignition without free processing tick");
        check(p.spent == CriticalStellarPolicy.IGNITION_EU && p.burned == CriticalStellarPolicy.IGNITION_HELIUM3_L,
            "Reloaded ignition neither duplicates fuel nor EU");
        check(n.getLong("ignitionEuConsumed") == 0, "Complete ignition clears the consumed proportion marker");
        CriticalStellarRuntime.tick(n,p,"machine",5,true,true,false,true,false);
        check(CriticalStellarRuntime.ready(n) && p.burned == CriticalStellarPolicy.IGNITION_HELIUM3_L + 1,
            "Disabled work switch still pays continuous helium");
        check(p.spent == CriticalStellarPolicy.IGNITION_EU + CriticalStellarPolicy.SUSTAIN_EUT,
            "Disabled work switch still pays continuous EU");
        check(CriticalStellarRuntime.payProcessing(n,p,"batch",16), "Additional processing payment authorizes progress");
        check(p.spent == CriticalStellarPolicy.IGNITION_EU + CriticalStellarPolicy.SUSTAIN_EUT + 16,
            "Processing never replaces or double counts sustain");
        check(!CriticalStellarRuntime.payProcessing(n,p,"batch",16), "Same tick cannot advance twice");
        CriticalStellarRuntime.finishBatch(n,p);
        p.outputCapacity = 0;
        tick(n,p,6);
        check(n.getInteger("plasmaHalfRemainder") == 0 && n.getLong("pendingPlasmaL") == 1,
            "Two sustain ticks accumulate one litre under output backpressure");
        long before = p.spent;
        CriticalStellarRuntime.tick(n,p,"machine",7,false,true,true,true,false);
        check(p.spent == before && !CriticalStellarRuntime.ready(n), "Untrusted owner freezes all billing");
        NBTTagCompound copy = (NBTTagCompound)n.copy();
        CriticalStellarRuntime.markItemCopy(copy);
        tick(copy,p,8);
        check(p.spent == before && copy.getBoolean("itemCopy"), "Item copy never resumes world assets");
        p.fuel = 0;
        tick(n,p,8);
        check(!n.getBoolean("hot") && !CriticalStellarRuntime.ready(n), "No fuel extinguishes immediately without grace");
        check(n.getLong("pendingPlasmaL") == 1, "Full output retains byproduct after extinction");
        p.outputCapacity = Long.MAX_VALUE;
        CriticalStellarRuntime.settleRefund(n,p);
        check(!CriticalStellarRuntime.hasAssets(n), "Settled extinct journal has no whole assets");
        NBTTagCompound cancelled = new NBTTagCompound();
        Port cancelledPort = new Port();
        cancelledPort.fuel = CriticalStellarPolicy.IGNITION_HELIUM3_L / 2;
        tick(cancelled, cancelledPort, 1);
        cancelled = (NBTTagCompound) cancelled.copy();
        CriticalStellarRuntime.shutdown(cancelled, cancelledPort);
        check(cancelledPort.plasma == CriticalStellarPolicy.IGNITION_HELIUM3_L / 4
            && cancelledPort.refunded == CriticalStellarPolicy.IGNITION_EU / 2
            && cancelledPort.spent - cancelledPort.refunded == CriticalStellarPolicy.IGNITION_EU / 2,
            "Cancelling half-fuel ignition retains its half EU cost and delivers only its actual plasma");
        NBTTagCompound old = new NBTTagCompound();
        old.setInteger("schema",1); old.setInteger("billingVersion",1); old.setString("host",p.identity);
        old.setString("structure","machine"); old.setBoolean("hot",true); old.setLong("reserve",123);
        old.setLong("processingPaid",45); old.setLong("ignitionPaid",67);
        before = p.refunded;
        CriticalStellarRuntime.tick(old,p,"machine",9,true,true,false,true,false);
        check(CriticalStellarRuntime.supported(old) && !old.getBoolean("hot") && p.refunded-before == 235,
            "Legacy heat becomes cold and refunds only unused real credit");
        n = new NBTTagCompound(); p = new Port(); p.fuelUncertain = true;
        tick(n,p,1);
        check(n.getBoolean("uncertain") && !n.getBoolean("hot") && n.getLong("pendingPlasmaL") == 500000,
            "Uncertain extraction keeps confirmed byproduct and locks retry");
        before = p.burned; tick(n,p,2);
        check(p.burned == before, "Uncertain fuel cannot be replayed");
        n = new NBTTagCompound(); p = new Port(); tick(n,p,1); p.outputThrows = true; tick(n,p,2);
        check(n.getBoolean("uncertain") && n.getLong("pendingPlasmaL") == 500000,
            "Uncertain output retains evidence without automatic retry");
        n = new NBTTagCompound(); p = new Port(); tick(n,p,1);
        CriticalStellarRuntime.tick(n,p,"machine",2,true,true,true,false,false);
        check(!n.getBoolean("hot"), "Structure loss extinguishes immediately");
        n = new NBTTagCompound(); p = new Port(); tick(n,p,1); p.identity = "other"; before=p.spent; tick(n,p,2);
        check(n.getBoolean("identityMismatch") && p.spent == before, "Changed host cannot spend canonical assets");
        n.setInteger("schema",999);
        check(CriticalStellarRuntime.hasAssets(n) && !CriticalStellarRuntime.ready(n), "Future schema is retained for audit");
        System.out.println("PASS " + checks + " stellar lifecycle checks");
    }
}
