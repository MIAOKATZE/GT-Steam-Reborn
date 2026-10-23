package com.miaokatze.gtsr.common.critical;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

/** Loaded-tick thermal ledger. Stored only by the canonical construction journal owner. */
public final class CriticalStellarRuntime {

    public static final int SCHEMA = 1;

    public interface PowerPort {

        long available();

        CriticalEnergyDebit debit(long requested);

        long refund(long requested);

        String identity();

        void changed();
    }

    private CriticalStellarRuntime() {}

    public static boolean hasAssets(NBTTagCompound n) {
        return !supported(n) || n.getBoolean("hot")
            || n.getLong("ignitionPaid") > 0
            || n.getLong("reserve") > 0
            || n.getLong("processingPaid") > 0
            || n.getLong("refund") > 0
            || n.getBoolean("uncertain");
    }

    /** Item copies retain their assets as evidence, but can never resume a world-bound thermal session. */
    public static void markItemCopy(NBTTagCompound n) {
        if (hasAssets(n)) n.setBoolean("itemCopy", true);
    }

    public static boolean supported(NBTTagCompound n) {
        return (!n.hasKey("schema") || n.getInteger("schema") == SCHEMA)
            && (!n.hasKey("billingVersion") || n.getInteger("billingVersion") == CriticalStellarPolicy.VERSION);
    }

    public static boolean ready(NBTTagCompound n) {
        return supported(n) && n.getBoolean("hot")
            && !n.getBoolean("uncertain")
            && !n.getBoolean("itemCopy")
            && !n.getBoolean("identityMismatch")
            && n.getBoolean("tickAuthorized")
            && n.getInteger("initializingTicks") == 0
            && n.getInteger("lostTicks") == 0;
    }

    /**
     * Call once BEFORE production early returns. loaded=false and untrusted identity freeze all accounting.
     * enabled is the true GT work switch; a production-only pause must keep enabled=true.
     * legacyBatch suppresses new ignition while the original paid batch is settled.
     */
    public static void tick(NBTTagCompound n, PowerPort port, String structureId, long tick, boolean identityTrusted,
        boolean loaded, boolean enabled, boolean formed, boolean legacyBatch) {
        n.setBoolean("tickAuthorized", false);
        if (!supported(n) || !loaded || !identityTrusted || n.getBoolean("itemCopy")) return;
        if (!bind(n, port, structureId)) return;
        if (n.hasKey("lastTick") && n.getLong("lastTick") == tick) return;
        n.setLong("lastTick", tick);
        if (n.getBoolean("uncertain")) return;
        boolean hadRefund = n.getLong("refund") > 0;
        if (!enabled) shutdown(n, port);
        settleRefund(n, port);
        if (!enabled || hadRefund || n.getLong("refund") > 0 || legacyBatch) return;
        if (!formed) {
            if (n.getBoolean("hot")) {
                n.setInteger("lostTicks", n.getInteger("lostTicks") + 1);
                if (n.getInteger("initializingTicks") == 0) hold(n, port);
                if (n.getInteger("lostTicks") >= CriticalStellarPolicy.GRACE_TICKS) shutdown(n, port);
            }
            port.changed();
            return;
        }
        n.setInteger("lostTicks", 0);
        if (n.getBoolean("hot")) {
            if (n.getInteger("initializingTicks") > 0) {
                n.setInteger("initializingTicks", n.getInteger("initializingTicks") - 1);
                port.changed();
                return;
            }
            hold(n, port);
            n.setBoolean(
                "tickAuthorized",
                n.getBoolean("hot") && n.getBoolean("holdPaid") && !n.getBoolean("uncertain"));
            return;
        }
        if (!credit(n, port, "ignitionPaid", CriticalStellarPolicy.IGNITION_EU)) return;
        if (!credit(n, port, "reserve", CriticalStellarPolicy.RESERVE_EU)) return;
        n.setLong("ignitionPaid", 0); // Ignition is consumed exactly once; no free refill on reload.
        n.setBoolean("hot", true);
        n.setInteger("initializingTicks", 200);
        n.setLong("sessionEpoch", n.getLong("sessionEpoch") + 1);
        n.setString(
            "session",
            UUID.randomUUID()
                .toString());
        // The paid ignition fee includes initialization. Hold starts only after the 200-tick sequence.
        port.changed();
    }

    private static boolean bind(NBTTagCompound n, PowerPort port, String structureId) {
        if (structureId == null || structureId.isEmpty()) return false;
        if (!n.hasKey("schema")) {
            n.setInteger("schema", SCHEMA);
            n.setInteger("billingVersion", CriticalStellarPolicy.VERSION);
            n.setString("structure", structureId);
            n.setString("host", port.identity());
            port.changed();
        }
        if (!structureId.equals(n.getString("structure")) || !port.identity()
            .equals(n.getString("host"))) {
            n.setBoolean("identityMismatch", true);
            port.changed();
            return false;
        }
        n.setBoolean("identityMismatch", false);
        return true;
    }

    private static boolean credit(NBTTagCompound n, PowerPort port, String key, long target) {
        long paid = n.getLong(key);
        if (paid < 0 || paid > target) {
            n.setBoolean("uncertain", true);
            port.changed();
            return false;
        }
        long requested = Math.min(target - paid, Math.max(0, port.available()));
        if (requested > 0) {
            // Persist intent before invoking an external debit. Exceptions leave an audit stop.
            n.setBoolean("uncertain", true);
            n.setString(
                "debitOperation",
                UUID.randomUUID()
                    .toString());
            n.setString("debitPurpose", key);
            n.setLong("debitRequested", requested);
            port.changed();
            try {
                CriticalEnergyDebit receipt = port.debit(requested);
                n.setLong(key, paid + receipt.confirmed);
                n.setLong("debitConfirmed", receipt.confirmed);
                n.setBoolean("uncertain", receipt.uncertain);
            } catch (RuntimeException failure) {
                n.setBoolean("uncertain", true);
            }
            port.changed();
        }
        return !n.getBoolean("uncertain") && n.getLong(key) == target;
    }

    private static void hold(NBTTagCompound n, PowerPort port) {
        n.setBoolean("holdPaid", false);
        long reserve = n.getLong("reserve");
        if (reserve < CriticalStellarPolicy.HOLD_EUT) {
            if (!credit(n, port, "reserve", CriticalStellarPolicy.HOLD_EUT)) {
                if (!n.getBoolean("uncertain")) shutdown(n, port);
                return;
            }
            reserve = n.getLong("reserve");
        }
        n.setLong("reserve", reserve - CriticalStellarPolicy.HOLD_EUT);
        n.setBoolean("holdPaid", true);
        // Refill only from real receipts, bounded by one hold tick. Outages never gift reserve.
        long refillTarget = Math
            .min(CriticalStellarPolicy.RESERVE_EU, n.getLong("reserve") + CriticalStellarPolicy.HOLD_EUT);
        credit(n, port, "reserve", refillTarget);
        port.changed();
    }

    /** Hold was paid by tick(); only the extra running cost is charged here, without double charging. */
    public static boolean payProcessing(NBTTagCompound n, PowerPort port, String batchId, long runEuPerTick) {
        if (!ready(n) || !n.getBoolean("holdPaid") || runEuPerTick < CriticalStellarPolicy.HOLD_EUT) return false;
        if (n.hasKey("processedTick") && n.getLong("processedTick") == n.getLong("lastTick")) return false;
        if (!n.getString("processingBatch")
            .isEmpty() && !batchId.equals(n.getString("processingBatch"))) {
            n.setBoolean("uncertain", true);
            port.changed();
            return false;
        }
        n.setString("processingBatch", batchId);
        n.setLong("runEuPerTick", runEuPerTick);
        long extra = runEuPerTick - CriticalStellarPolicy.HOLD_EUT;
        if (!credit(n, port, "processingPaid", extra)) return false;
        n.setLong("processingPaid", 0);
        n.setLong("processedTick", n.getLong("lastTick"));
        port.changed();
        return true;
    }

    public static void finishBatch(NBTTagCompound n, PowerPort port) {
        n.setString("processingBatch", "");
        n.setLong("runEuPerTick", 0);
        port.changed();
    }

    /** After the production decision, use only the remaining shared tick budget to refill paid hold reserve. */
    public static void refillReserve(NBTTagCompound n, PowerPort port) {
        if (ready(n)) credit(n, port, "reserve", CriticalStellarPolicy.RESERVE_EU);
    }

    /** Explicit off/teardown preserves unused real EU as a local refund journal, never a wireless credit. */
    public static void shutdown(NBTTagCompound n, PowerPort port) {
        if (!supported(n) || n.getBoolean("uncertain") || n.getBoolean("itemCopy")) return;
        if (!sameHost(n, port)) return;
        n.setBoolean("hot", false);
        n.setBoolean("holdPaid", false);
        n.setBoolean("tickAuthorized", false);
        n.setInteger("lostTicks", 0);
        n.setInteger("initializingTicks", 0);
        n.setLong(
            "refund",
            Math.addExact(
                n.getLong("refund"),
                Math.addExact(
                    n.getLong("ignitionPaid"),
                    Math.addExact(n.getLong("reserve"), n.getLong("processingPaid")))));
        n.setLong("ignitionPaid", 0);
        n.setLong("reserve", 0);
        n.setLong("processingPaid", 0);
        n.setString("processingBatch", "");
        settleRefund(n, port);
        port.changed();
    }

    /** Also call in state 0 and teardown before canRemove: full buffers retain the unpaid refund balance. */
    public static void settleRefund(NBTTagCompound n, PowerPort port) {
        if (!supported(n) || n.getBoolean("uncertain") || n.getBoolean("itemCopy")) return;
        if (!sameHost(n, port)) return;
        long remaining = n.getLong("refund");
        if (remaining <= 0) return;
        n.setBoolean("uncertain", true);
        n.setString("debitPurpose", "local_refund");
        port.changed();
        try {
            long accepted = port.refund(remaining);
            if (accepted < 0 || accepted > remaining) return;
            n.setLong("refund", remaining - accepted);
            n.setBoolean("uncertain", false);
        } catch (RuntimeException failure) {
            n.setBoolean("uncertain", true);
        }
        port.changed();
    }

    private static boolean sameHost(NBTTagCompound n, PowerPort port) {
        if (n.getBoolean("identityMismatch") || !n.getString("host")
            .isEmpty()
            && !n.getString("host")
                .equals(port.identity())) {
            n.setBoolean("identityMismatch", true);
            port.changed();
            return false;
        }
        return true;
    }

    public static NBTTagCompound snapshot(NBTTagCompound n) {
        NBTTagCompound result = (NBTTagCompound) n.copy();
        result.setInteger(
            "phase",
            !supported(n) || n.getBoolean("uncertain")
                || n.getBoolean("identityMismatch")
                || n.getBoolean("itemCopy")
                    ? 6
                    : n.getInteger("lostTicks") > 0 ? 5
                        : n.getBoolean("hot")
                            ? n.getInteger("initializingTicks") > 0 ? 2
                                : n.getString("processingBatch")
                                    .isEmpty() ? 3 : 4
                            : n.getLong("ignitionPaid") > 0 ? 1 : 0);
        result.setLong("ignitionRequired", CriticalStellarPolicy.IGNITION_EU);
        result.setLong("reserveCapacity", CriticalStellarPolicy.RESERVE_EU);
        result.setLong("holdEuPerTick", CriticalStellarPolicy.HOLD_EUT);
        return result;
    }
}
