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
    private static void ignite(NBTTagCompound n, Port p, long start) {
        for (int i = 0; i < 400; i++) tick(n, p, start + i);
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) for (Class<?> type : new Class<?>[] { CriticalStellarRuntime.class,
            CriticalStellarPolicy.class, CriticalEnergyDebit.class }) {
            java.nio.file.Path actual = java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!actual.toRealPath().equals(java.nio.file.Path.of(args[0]).toRealPath()))
                throw new AssertionError("Product overlay: " + type.getName());
        }
        check(CriticalStellarPolicy.runEuPerTick(101, 400, 3) == 16, "One rounding after actual parallel multiplication");
        check(CriticalStellarPolicy.runEuPerTick(100000, 1, 1) == 100, "99.9 percent discount clamp");
        check(CriticalStellarPolicy.runEuPerTick(100000, 8, 1) == 100, "Lower clamp boundary");
        check(CriticalStellarPolicy.runEuPerTick(100000, 200, 1) == 2500, "Shorter native recipe has stronger discount");
        check(CriticalStellarPolicy.runEuPerTick(100000, 400, 1) == 5000, "Twenty second anchor discounts 95 percent");
        check(CriticalStellarPolicy.runEuPerTick(100000, 800, 1) == 10000, "90 percent upper boundary");
        check(CriticalStellarPolicy.runEuPerTick(100000, Integer.MAX_VALUE, 1) == 10000, "90 percent discount clamp");
        check(CriticalStellarPolicy.runEuPerTick(2_147_483_647L, 400, 8196) == 880038798541L, "Long precision");
        check(CriticalStellarPolicy.runEuPerTick(0, 400, 1) == 0, "Zero native energy");
        check(CriticalStellarPolicy.powerLimitedParallel(15, 101, 400, 8196) == 2, "Real remaining power limit");
        check(CriticalStellarPolicy.powerLimitedParallel(15, 101, 8, 8196) == 148, "Discount participates in real parallel limit");
        check(CriticalStellarPolicy.processingPowerBudget(9, 100, true) == 9, "Sustain not added back");
        check(CriticalStellarPolicy.processingPowerBudget(9, 100, false) == 0, "Unpaid sustain blocks fusion");
        for (int duration : new int[]{1, 17, 400, 6400, Integer.MAX_VALUE})
            check(CriticalStellarPolicy.duration(duration) == 400, "All production durations fixed at 400");
        check(CriticalStellarPolicy.nativeCapability(20_480_000_000L, 8_388_608L, 8_388_608L), "MkV admitted");
        check(!CriticalStellarPolicy.nativeCapability(20_480_000_001L, 1, 8_388_608L), "Over MkV rejected");
        check(CriticalStellarPolicy.IGNITION_EU / 400 == 25_000_000_000L
            && CriticalStellarPolicy.IGNITION_EU / 400 <= 34_359_738_240L, "New 20 second ignition fits unchanged shared cap");
        NBTTagCompound n = new NBTTagCompound(); Port p = new Port();
        p.available = 100; tick(n,p,1);
        check(n.getLong("ignitionPaid") == 100 && p.burned == 0 && n.getInteger("ignitionProgress") == 0,
            "Partial EU cannot advance or burn ignition fuel");
        CriticalStellarRuntime.tick(n,p,"machine",2,true,false,true,true,false);
        check(p.spent == 100, "Unloaded billing frozen");
        p.available = Long.MAX_VALUE; p.fuel = 3; tick(n,p,3);
        check(!n.getBoolean("hot") && n.getLong("ignitionHelium3Paid") == 3 && n.getInteger("ignitionProgress") == 0,
            "Partial stage fuel cannot advance");
        check(n.getLong("pendingPlasmaL") == 1 && n.getInteger("plasmaHalfRemainder") == 1,
            "Only actual extraction produces exact plasma halves");
        n = (NBTTagCompound)n.copy(); p.fuel = Long.MAX_VALUE;
        for (int i = 0; i < 399; i++) tick(n,p,4+i);
        check(!n.getBoolean("hot") && n.getInteger("ignitionProgress") == 399, "Ignition not complete before 400 paid ticks");
        tick(n,p,403);
        check(n.getBoolean("hot") && !CriticalStellarRuntime.ready(n), "400th ignition tick grants no free production");
        check(p.spent == CriticalStellarPolicy.IGNITION_EU && p.burned == CriticalStellarPolicy.IGNITION_HELIUM3_L,
            "Reload neither duplicates nor drops ignition payment");
        check(n.getLong("ignitionEuConsumed") == 0, "Ignition consumed marker cleared");
        CriticalStellarRuntime.tick(n,p,"machine",404,true,true,false,true,false);
        check(CriticalStellarRuntime.ready(n) && n.getInteger("sustainProgress") == 1,
            "Paused production still pays continuous sustain");
        check(p.spent == CriticalStellarPolicy.IGNITION_EU + CriticalStellarPolicy.SUSTAIN_EUT,
            "Real sustain EU remains a baseline");
        check(CriticalStellarRuntime.payProcessing(n,p,"batch",16), "Processing additionally funded");
        check(!CriticalStellarRuntime.payProcessing(n,p,"batch",16), "No duplicate tick progress");
        CriticalStellarRuntime.finishBatch(n,p);
        for (int i=405;i<804;i++) tick(n,p,i);
        check(n.getInteger("sustainProgress") == 0 && p.burned == CriticalStellarPolicy.IGNITION_HELIUM3_L + 400,
            "400 paid maintain ticks complete exactly one persistent phase");
        long before = p.spent;
        CriticalStellarRuntime.tick(n,p,"machine",804,false,true,true,true,false);
        check(p.spent == before && !CriticalStellarRuntime.ready(n), "Untrusted owner freezes billing");
        NBTTagCompound copy = (NBTTagCompound)n.copy(); CriticalStellarRuntime.markItemCopy(copy); tick(copy,p,805);
        check(p.spent == before && copy.getBoolean("itemCopy"), "Item copy cannot resume assets");
        p.outputCapacity = 0; p.fuel = 0; tick(n,p,805);
        check(!n.getBoolean("hot") && !CriticalStellarRuntime.ready(n), "Fuel interruption extinguishes immediately");
        p.outputCapacity = Long.MAX_VALUE; CriticalStellarRuntime.settleRefund(n,p);
        check(!CriticalStellarRuntime.hasAssets(n), "Extinct assets settle completely");
        n = new NBTTagCompound(); p = new Port(); p.fuel = 1250; tick(n,p,1);
        n = (NBTTagCompound)n.copy(); CriticalStellarRuntime.shutdown(n,p);
        check(p.plasma == 625 && p.refunded == CriticalStellarPolicy.IGNITION_EU / 800
            && p.spent-p.refunded == CriticalStellarPolicy.IGNITION_EU/800,
            "Half-stage cancellation pays only actually burned fraction and yields only actual plasma");
        n = new NBTTagCompound(); p = new Port();
        for(int i=1;i<=200;i++)tick(n,p,i);
        CriticalStellarRuntime.shutdown(n,p);
        check(p.spent == CriticalStellarPolicy.IGNITION_EU/2 && p.refunded == 0 && p.plasma == 250000,
            "Cancelling completed half ignition neither refunds spent EU nor removes produced plasma");
        n = new NBTTagCompound(); p = new Port(); n.setInteger("schema",2);n.setInteger("billingVersion",2);
        n.setString("host",p.identity);n.setString("structure","machine");n.setLong("ignitionPaid",CriticalStellarPolicy.LEGACY_IGNITION_EU);
        n.setLong("ignitionHelium3Paid",500000);n.setLong("ignitionEuConsumed",CriticalStellarPolicy.LEGACY_IGNITION_EU/2);
        tick(n,p,1);
        check(n.getInteger("schema")==3 && n.getInteger("ignitionProgress")==201 && p.spent==0 && p.burned==2500,
            "Old partial ignition resumes from prepaid receipts without recharging");
        CriticalStellarRuntime.shutdown(n,p);
        check(p.refunded==CriticalStellarPolicy.LEGACY_IGNITION_EU/2-CriticalStellarPolicy.LEGACY_IGNITION_EU/400,
            "Migrated prepaid unused EU refunds exactly");
        n = new NBTTagCompound();p = new Port(); n.setInteger("schema",2);n.setInteger("billingVersion",2);
        n.setString("host",p.identity);n.setString("structure","machine");n.setBoolean("hot",true);tick(n,p,1);
        check(CriticalStellarRuntime.ready(n)&&p.spent==CriticalStellarPolicy.SUSTAIN_EUT,
            "Existing hot star keeps its paid ignition while adopting new phases");
        check(n.getLong("ignitionEuRequired")==CriticalStellarPolicy.LEGACY_IGNITION_EU,
            "Migrated hot star retains its old price snapshot until shutdown");
        CriticalStellarRuntime.shutdown(n,p);before=p.spent;tick(n,p,2);
        check(n.getLong("ignitionEuRequired")==CriticalStellarPolicy.IGNITION_EU
            && p.spent-before==CriticalStellarPolicy.IGNITION_EU/400,
            "Shutdown clears old ignition snapshot and restart adopts current price");
        n = new NBTTagCompound();p = new Port();n.setInteger("schema",3);n.setInteger("billingVersion",3);
        n.setString("host",p.identity);n.setString("structure","machine");
        n.setLong("ignitionEuRequired",CriticalStellarPolicy.LEGACY_IGNITION_EU);
        n.setLong("ignitionPaid",CriticalStellarPolicy.LEGACY_IGNITION_EU/400);
        n.setLong("ignitionEuConsumed",CriticalStellarPolicy.LEGACY_IGNITION_EU/400);
        n.setLong("ignitionHelium3Paid",2500);n.setInteger("ignitionProgress",1);tick(n,p,1);
        check(!n.getBoolean("uncertain")&&n.getInteger("ignitionProgress")==2
            && p.spent==CriticalStellarPolicy.LEGACY_IGNITION_EU/400,
            "Current schema partial session bills frozen snapshot independently of current default");
        n = new NBTTagCompound();p = new Port();n.setInteger("schema",2);n.setInteger("billingVersion",2);
        n.setString("host",p.identity);n.setString("structure","machine");tick(n,p,1);
        check(n.getLong("ignitionEuRequired")==CriticalStellarPolicy.IGNITION_EU
            && p.spent==CriticalStellarPolicy.IGNITION_EU/400,
            "Settled cold v2 machine begins a new ignition at current price");
        n = new NBTTagCompound();p = new Port();n.setInteger("schema",1);n.setInteger("billingVersion",1);
        n.setString("host",p.identity);n.setString("structure","machine");n.setBoolean("hot",true);
        n.setLong("reserve",123);n.setLong("processingPaid",45);n.setLong("ignitionPaid",67);
        CriticalStellarRuntime.tick(n,p,"machine",1,true,true,false,true,false);
        check(CriticalStellarRuntime.supported(n)&&!n.getBoolean("hot")&&p.refunded==235, "v1 heat cools and refunds unused funds");
        n = new NBTTagCompound();p = new Port();p.fuelUncertain=true;tick(n,p,1);
        check(n.getBoolean("uncertain")&&!n.getBoolean("hot")&&n.getLong("pendingPlasmaL")==1250,
            "Uncertain extraction retains receipts and stops");
        before=p.burned;tick(n,p,2);check(p.burned==before,"Uncertain extraction cannot replay");
        n = new NBTTagCompound();p = new Port();tick(n,p,1);p.outputThrows=true;tick(n,p,2);
        check(n.getBoolean("uncertain")&&n.getLong("pendingPlasmaL")==1250,"Uncertain output retains evidence");
        n = new NBTTagCompound();p = new Port();ignite(n,p,1);
        CriticalStellarRuntime.tick(n,p,"machine",401,true,true,true,false,false);
        check(!n.getBoolean("hot"),"Structure loss extinguishes");
        n = new NBTTagCompound();p = new Port();tick(n,p,1);p.identity="other";before=p.spent;tick(n,p,2);
        check(n.getBoolean("identityMismatch")&&p.spent==before,"Different host cannot spend");
        n.setInteger("schema",999);check(CriticalStellarRuntime.hasAssets(n)&&!CriticalStellarRuntime.ready(n),"Unknown schema retained");
        System.out.println("PASS " + checks + " stellar lifecycle checks");
    }
}
