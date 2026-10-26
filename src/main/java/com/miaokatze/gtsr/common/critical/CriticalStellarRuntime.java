package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;
import java.util.UUID;
import java.util.function.LongConsumer;

import net.minecraft.nbt.NBTTagCompound;

/** Loaded-tick thermal ledger. Stored only by the canonical construction journal owner. */
public final class CriticalStellarRuntime {

    public static final int SCHEMA = 3;

    public interface PowerPort {

        long available();

        CriticalEnergyDebit debit(long requested);

        long refund(long requested);

        /** Journal each confirmed fuel extraction before touching the next source. */
        boolean burnHelium3(long requested, LongConsumer confirmed);

        long emitHeliumPlasma(long requested);

        String identity();

        void changed();
    }

    private CriticalStellarRuntime() {}

    public static boolean hasAssets(NBTTagCompound n) {
        return !supported(n) || n.getBoolean("hot")
            || n.getLong("ignitionPaid") > 0
            || n.getLong("reserve") > 0
            || n.getLong("sustainPaid") > 0
            || n.getLong("pendingPlasmaL") > 0
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
            && n.getBoolean("tickAuthorized");
    }

    /** Called before production/work-switch early returns. Pausing fusion never suspends a burning star. */
    public static void tick(NBTTagCompound n, PowerPort port, String structureId, long tick, boolean identityTrusted,
        boolean loaded, boolean enabled, boolean formed, boolean legacyBatch) {
        n.setBoolean("tickAuthorized", false);
        n.setBoolean("sustainPaidThisTick", false);
        if (!loaded || !identityTrusted || n.getBoolean("itemCopy")) return;
        if (!migrate(n, port, structureId) || !bind(n, port, structureId)) return;
        if (n.hasKey("lastTick") && n.getLong("lastTick") == tick) return;
        n.setLong("lastTick", tick);
        if (n.getBoolean("uncertain")) return;
        settleRefund(n, port);
        if (n.getBoolean("uncertain") || n.getLong("refund") > 0 || legacyBatch) return;
        if (!formed) {
            shutdown(n, port);
            return;
        }
        if (n.getBoolean("hot")) {
            if (!credit(n, port, "sustainPaid", CriticalStellarPolicy.SUSTAIN_EUT)
                || !burn(n, port, "sustainHelium3Paid", CriticalStellarPolicy.SUSTAIN_HELIUM3_L_PER_TICK)) {
                if (!n.getBoolean("uncertain")) shutdown(n, port);
                return;
            }
            n.setLong("sustainPaid", 0);
            n.setLong("sustainHelium3Paid", 0);
            n.setInteger(
                "sustainProgress",
                (n.getInteger("sustainProgress") + 1) % CriticalStellarPolicy.DURATION_TICKS);
            n.setBoolean("sustainPaidThisTick", true);
            n.setBoolean("tickAuthorized", true);
            port.changed();
            return;
        }
        if (!enabled) {
            shutdown(n, port);
            return;
        }
        int progress = n.getInteger("ignitionProgress");
        if (progress < 0 || progress >= CriticalStellarPolicy.DURATION_TICKS) {
            n.setBoolean("uncertain", true);
            port.changed();
            return;
        }
        if (!n.hasKey("ignitionEuRequired")) {
            n.setLong("ignitionEuRequired", CriticalStellarPolicy.IGNITION_EU);
            port.changed();
        }
        if (!n.hasKey("ignitionHe3Required")) {
            n.setLong("ignitionHe3Required", ignitionHelium3Required(n));
            port.changed();
        }
        long ignitionEu = ignitionRequired(n);
        long ignitionHe3 = ignitionHelium3Required(n);
        long euTarget = BigInteger.valueOf(ignitionEu)
            .multiply(BigInteger.valueOf(progress + 1L))
            .divide(BigInteger.valueOf(CriticalStellarPolicy.DURATION_TICKS))
            .longValueExact();
        long heliumTarget = ignitionHe3 / CriticalStellarPolicy.DURATION_TICKS * (progress + 1L);
        // A migrated v2 ignition can already hold more credit than this stage's cumulative target.
        if (ignitionEu <= 0 || ignitionHe3 <= 0
            || n.getLong("ignitionPaid") < 0
            || n.getLong("ignitionPaid") > ignitionEu
            || n.getLong("ignitionHelium3Paid") < 0
            || n.getLong("ignitionHelium3Paid") > ignitionHe3) {
            n.setBoolean("uncertain", true);
            port.changed();
            return;
        }
        if (n.getLong("ignitionPaid") < euTarget && !credit(n, port, "ignitionPaid", euTarget)
            || n.getLong("ignitionHelium3Paid") < heliumTarget && !burn(n, port, "ignitionHelium3Paid", heliumTarget))
            return;
        n.setInteger("ignitionProgress", progress + 1);
        port.changed();
        if (progress + 1 < CriticalStellarPolicy.DURATION_TICKS) return;
        n.setLong("ignitionPaid", 0);
        n.setLong("ignitionHelium3Paid", 0);
        n.setLong("ignitionEuConsumed", 0);
        n.removeTag("ignitionHe3Required");
        n.setBoolean("hot", true);
        n.setInteger("ignitionProgress", 0);
        n.setInteger("sustainProgress", 0);
        n.setLong("sessionEpoch", n.getLong("sessionEpoch") + 1);
        n.setString(
            "session",
            UUID.randomUUID()
                .toString());
        // No free fusion on the ignition tick; subsequent ticks must pay sustain first.
        port.changed();
    }

    /** Known paid sessions retain their frozen ignition targets; unknown or uncertain journals stay locked. */
    private static boolean migrate(NBTTagCompound n, PowerPort port, String structureId) {
        if (supported(n)) return true;
        if (n.getInteger("schema") == SCHEMA && n.getInteger("billingVersion") == 3
            && !n.getBoolean("uncertain")
            && !n.getBoolean("itemCopy")
            && sameHost(n, port)
            && structureId != null
            && structureId.equals(n.getString("structure"))) {
            // Actual paid litres keep their original value; only the next sustain debit uses the new rate.
            if (!n.getBoolean("hot") && ignitionInFlight(n))
                n.setLong("ignitionHe3Required", CriticalStellarPolicy.LEGACY_IGNITION_HELIUM3_L);
            n.setInteger("billingVersion", CriticalStellarPolicy.VERSION);
            port.changed();
            return true;
        }
        if (n.getInteger("schema") == 2 && n.getInteger("billingVersion") == 2
            && !n.getBoolean("uncertain")
            && !n.getBoolean("itemCopy")
            && sameHost(n, port)
            && structureId != null
            && structureId.equals(n.getString("structure"))) {
            long fuel = n.getLong("ignitionHelium3Paid");
            long paid = n.getLong("ignitionPaid");
            if (fuel < 0 || fuel > CriticalStellarPolicy.LEGACY_IGNITION_HELIUM3_L
                || paid < 0
                || paid > CriticalStellarPolicy.LEGACY_IGNITION_EU) return false;
            int progress = (int) Math.min(
                fuel / (CriticalStellarPolicy.LEGACY_IGNITION_HELIUM3_L / CriticalStellarPolicy.DURATION_TICKS),
                paid / (CriticalStellarPolicy.LEGACY_IGNITION_EU / CriticalStellarPolicy.DURATION_TICKS));
            // A fully prepaid journal still needs its final real loaded tick to become hot.
            n.setInteger("ignitionProgress", Math.min(CriticalStellarPolicy.DURATION_TICKS - 1, progress));
            n.setInteger("sustainProgress", 0);
            n.setLong(
                "ignitionEuRequired",
                n.getBoolean("hot") || paid > 0 || fuel > 0 ? CriticalStellarPolicy.LEGACY_IGNITION_EU
                    : CriticalStellarPolicy.IGNITION_EU);
            if (!n.getBoolean("hot") && (paid > 0 || fuel > 0))
                n.setLong("ignitionHe3Required", CriticalStellarPolicy.LEGACY_IGNITION_HELIUM3_L);
            n.setInteger("schema", SCHEMA);
            n.setInteger("billingVersion", CriticalStellarPolicy.VERSION);
            port.changed();
            return true;
        }
        if (n.getInteger("schema") != 1 || n.getInteger("billingVersion") != 1
            || n.getBoolean("uncertain")
            || n.getBoolean("itemCopy")
            || !sameHost(n, port)
            || structureId == null
            || !structureId.equals(n.getString("structure"))) return false;
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
        n.setBoolean("hot", false);
        n.setInteger("ignitionProgress", 0);
        n.setInteger("sustainProgress", 0);
        n.setString("processingBatch", "");
        n.setLong("runEuPerTick", 0);
        n.removeTag("holdPaid");
        n.removeTag("initializingTicks");
        n.removeTag("lostTicks");
        n.setInteger("schema", SCHEMA);
        n.setInteger("billingVersion", CriticalStellarPolicy.VERSION);
        n.setBoolean("migratedFromV1", true);
        port.changed();
        return true;
    }

    private static boolean bind(NBTTagCompound n, PowerPort port, String structureId) {
        if (structureId == null || structureId.isEmpty()) return false;
        if (!n.hasKey("schema")) {
            n.setInteger("schema", SCHEMA);
            n.setInteger("billingVersion", CriticalStellarPolicy.VERSION);
            n.setString("structure", structureId);
            n.setString("host", port.identity());
            n.setLong("ignitionEuRequired", CriticalStellarPolicy.IGNITION_EU);
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

    /** Fuel is consumed only from receipts; each two litres produce one whole litre plus a saved half remainder. */
    private static boolean burn(NBTTagCompound n, PowerPort port, String key, long target) {
        long paid = n.getLong(key);
        if (paid < 0 || paid > target) {
            n.setBoolean("uncertain", true);
            port.changed();
            return false;
        }
        long requested = target - paid;
        if (requested > 0) {
            n.setBoolean("uncertain", true);
            n.setString("debitPurpose", key);
            n.setLong("debitRequested", requested);
            port.changed();
            try {
                boolean certain = port.burnHelium3(requested, actual -> {
                    long previous = n.getLong(key);
                    if (actual < 0 || actual > target - previous) throw new IllegalStateException("Helium receipt");
                    n.setLong(key, previous + actual);
                    if ("ignitionHelium3Paid".equals(key)) {
                        // Keep the full credited amount for credit(); only its refundable portion decreases.
                        n.setLong(
                            "ignitionEuConsumed",
                            BigInteger.valueOf(ignitionRequired(n))
                                .multiply(BigInteger.valueOf(previous + actual))
                                .divide(BigInteger.valueOf(ignitionHelium3Required(n)))
                                .longValueExact());
                    }
                    long half = actual + n.getInteger("plasmaHalfRemainder");
                    n.setLong("pendingPlasmaL", Math.addExact(n.getLong("pendingPlasmaL"), half / 2));
                    n.setInteger("plasmaHalfRemainder", (int) (half % 2));
                    port.changed();
                });
                n.setBoolean("uncertain", !certain);
            } catch (RuntimeException failure) {
                n.setBoolean("uncertain", true);
            }
            port.changed();
        }
        return !n.getBoolean("uncertain") && n.getLong(key) == target;
    }

    /** Fusion is additional to the continuous sustain cost already paid by tick(). */
    public static boolean payProcessing(NBTTagCompound n, PowerPort port, String batchId, long runEuPerTick) {
        if (!ready(n) || !n.getBoolean("sustainPaidThisTick") || runEuPerTick < 0) return false;
        if (n.hasKey("processedTick") && n.getLong("processedTick") == n.getLong("lastTick")) return false;
        if (!n.getString("processingBatch")
            .isEmpty() && !batchId.equals(n.getString("processingBatch"))) {
            n.setBoolean("uncertain", true);
            port.changed();
            return false;
        }
        n.setString("processingBatch", batchId);
        n.setLong("runEuPerTick", runEuPerTick);
        if (!credit(n, port, "processingPaid", runEuPerTick)) return false;
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

    /** Explicit off/teardown preserves unused real EU as a local refund journal, never a wireless credit. */
    public static void shutdown(NBTTagCompound n, PowerPort port) {
        if (!migrate(n, port, n.getString("structure")) || n.getBoolean("uncertain") || n.getBoolean("itemCopy"))
            return;
        if (!sameHost(n, port)) return;
        long ignitionPaid = n.getLong("ignitionPaid"), ignitionConsumed = n.getLong("ignitionEuConsumed");
        if (ignitionConsumed < 0 || ignitionConsumed > ignitionPaid) {
            n.setBoolean("uncertain", true);
            port.changed();
            return;
        }
        n.setBoolean("hot", false);
        n.setBoolean("sustainPaidThisTick", false);
        n.setBoolean("tickAuthorized", false);
        n.setLong(
            "refund",
            Math.addExact(
                n.getLong("refund"),
                Math.addExact(
                    ignitionPaid - ignitionConsumed,
                    Math.addExact(n.getLong("sustainPaid"), n.getLong("processingPaid")))));
        n.setLong("ignitionPaid", 0);
        n.setInteger("ignitionProgress", 0);
        n.setInteger("sustainProgress", 0);
        n.removeTag("ignitionEuRequired");
        n.removeTag("ignitionHe3Required");
        n.setLong("ignitionEuConsumed", 0);
        n.setLong("sustainPaid", 0);
        n.setLong("ignitionHelium3Paid", 0);
        n.setLong("sustainHelium3Paid", 0);
        n.setLong("processingPaid", 0);
        n.setString("processingBatch", "");
        n.setLong("runEuPerTick", 0);
        settleRefund(n, port);
        port.changed();
    }

    /** Also call in state 0 and teardown before canRemove: full buffers retain the unpaid refund balance. */
    public static void settleRefund(NBTTagCompound n, PowerPort port) {
        if (!migrate(n, port, n.getString("structure")) || n.getBoolean("uncertain") || n.getBoolean("itemCopy"))
            return;
        if (!sameHost(n, port)) return;
        long plasma = n.getLong("pendingPlasmaL");
        if (plasma > 0) {
            n.setBoolean("uncertain", true);
            n.setString("debitPurpose", "helium_plasma_output");
            port.changed();
            try {
                long accepted = port.emitHeliumPlasma(plasma);
                if (accepted < 0 || accepted > plasma) return;
                n.setLong("pendingPlasmaL", plasma - accepted);
                n.setBoolean("uncertain", false);
            } catch (RuntimeException failure) {
                n.setBoolean("uncertain", true);
            }
            port.changed();
            if (n.getBoolean("uncertain")) return;
        }
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
            !supported(n) || n
                .getBoolean("uncertain") || n.getBoolean("identityMismatch") || n.getBoolean("itemCopy")
                    ? 6
                    : n.getBoolean("hot") ? n.getString("processingBatch")
                        .isEmpty() ? 3 : 4
                        : n.getLong("ignitionPaid") > 0 || n.getLong("ignitionHelium3Paid") > 0 ? 1 : 0);
        result.setLong("ignitionRequired", ignitionRequired(n));
        result.setLong("ignitionHelium3Required", ignitionHelium3Required(n));
        result.setLong("sustainEuPerTick", CriticalStellarPolicy.SUSTAIN_EUT);
        result.setLong("sustainHelium3PerTick", CriticalStellarPolicy.SUSTAIN_HELIUM3_L_PER_TICK);
        result.setInteger("phaseDuration", CriticalStellarPolicy.DURATION_TICKS);
        result.setInteger("phaseProgress", n.getInteger(n.getBoolean("hot") ? "sustainProgress" : "ignitionProgress"));
        return result;
    }

    private static long ignitionRequired(NBTTagCompound n) {
        if (n.hasKey("ignitionEuRequired")) return n.getLong("ignitionEuRequired");
        if (n.getInteger("schema") == 2 && n.getInteger("billingVersion") == 2
            && (n.getBoolean("hot") || ignitionInFlight(n))) return CriticalStellarPolicy.LEGACY_IGNITION_EU;
        return CriticalStellarPolicy.IGNITION_EU;
    }

    private static boolean ignitionInFlight(NBTTagCompound n) {
        return n.getLong("ignitionPaid") > 0 || n.getLong("ignitionHelium3Paid") > 0
            || n.getInteger("ignitionProgress") > 0;
    }

    private static long ignitionHelium3Required(NBTTagCompound n) {
        if (n.hasKey("ignitionHe3Required")) return n.getLong("ignitionHe3Required");
        if (!n.getBoolean("hot") && ignitionInFlight(n)
            && (n.getInteger("schema") == 2 && n.getInteger("billingVersion") == 2
                || n.getInteger("schema") == SCHEMA && n.getInteger("billingVersion") == 3))
            return CriticalStellarPolicy.LEGACY_IGNITION_HELIUM3_L;
        return CriticalStellarPolicy.IGNITION_HELIUM3_L;
    }
}
