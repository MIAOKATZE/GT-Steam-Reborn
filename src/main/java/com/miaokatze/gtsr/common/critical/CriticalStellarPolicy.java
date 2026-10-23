package com.miaokatze.gtsr.common.critical;

/** Versioned RC2 stellar pricing; independent of native recipe EU/t and startup capability. */
public final class CriticalStellarPolicy {

    public static final int VERSION = 1, MAX_PARALLEL = 8196, GRACE_TICKS = 1200;
    public static final long IGNITION_EU = 1_000_000_000_000_000L;
    public static final long HOLD_EUT = 1_000_000L, RESERVE_EU = HOLD_EUT * 1200L;
    public static final long MKV_STARTUP = 20_480_000_000L;

    private CriticalStellarPolicy() {}

    public static boolean nativeCapability(long threshold, long nativeEut, long mkVVoltage) {
        return threshold >= 0 && threshold <= MKV_STARTUP && nativeEut >= 0 && nativeEut <= mkVVoltage;
    }

    public static int duration(int nativeTicks) {
        return (int) Math.max(1L, ((long) nativeTicks + 15L) / 16L);
    }

    public static long runEuPerTick(int parallel) {
        if (parallel < 1 || parallel > MAX_PARALLEL) throw new IllegalArgumentException("parallel");
        return HOLD_EUT + (long) Math.ceil(9_000_000D * Math.sqrt(parallel / (double) MAX_PARALLEL));
    }

    public static int powerLimitedParallel(long power, int maximum) {
        int low = 0, high = Math.min(MAX_PARALLEL, Math.max(0, maximum));
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (runEuPerTick(middle) <= power) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    /** Running includes the hold already paid earlier this tick, but never exceeds the host's real cap. */
    public static long processingPowerBudget(long remainingPower, long tickCap, boolean holdPaid) {
        if (!holdPaid || remainingPower < 0 || tickCap <= 0) return 0;
        long combined = remainingPower > Long.MAX_VALUE - HOLD_EUT ? Long.MAX_VALUE : remainingPower + HOLD_EUT;
        return Math.min(tickCap, Math.min(runEuPerTick(MAX_PARALLEL), combined));
    }
}
