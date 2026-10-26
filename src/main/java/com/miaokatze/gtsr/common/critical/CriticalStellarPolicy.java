package com.miaokatze.gtsr.common.critical;

/** Single ignition contract; provisional balance values live here for both runtime and recipe displays. */
public final class CriticalStellarPolicy {

    public static final int VERSION = 4, MAX_PARALLEL = 8196;
    public static final int DURATION_TICKS = 400;
    public static final long IGNITION_EU = 10_000_000_000_000L;
    public static final long LEGACY_IGNITION_EU = 1_000_000_000_000_000L;
    public static final long LEGACY_IGNITION_HELIUM3_L = 1_000_000L;
    public static final long IGNITION_HELIUM3_L = 50_000_000L;
    public static final long SUSTAIN_EUT = 1_000_000L, SUSTAIN_HELIUM3_L_PER_TICK = 100L;
    public static final long MKV_STARTUP = 20_480_000_000L;

    private CriticalStellarPolicy() {}

    public static boolean nativeCapability(long threshold, long nativeEut, long mkVVoltage) {
        return threshold >= 0 && threshold <= MKV_STARTUP && nativeEut >= 0 && nativeEut <= mkVVoltage;
    }

    public static int duration(int nativeTicks) {
        if (nativeTicks <= 0) throw new IllegalArgumentException("nativeTicks");
        return DURATION_TICKS;
    }

    /** Frozen v2 billing, retained only for in-flight batches saved before the duration policy changed. */
    public static long runEuPerTick(long nativeEuPerTick, int parallel) {
        return runEuPerTick(nativeEuPerTick, 400, parallel);
    }

    public static long runEuPerTick(long nativeEuPerTick, int nativeTicks, int parallel) {
        if (parallel < 1 || parallel > MAX_PARALLEL) throw new IllegalArgumentException("parallel");
        if (nativeEuPerTick < 0) throw new IllegalArgumentException("nativeEuPerTick");
        if (nativeTicks <= 0) throw new IllegalArgumentException("nativeTicks");
        // Remaining fraction = clamp(nativeTicks / 8000, 0.001, 0.10). Round the actual sum once.
        int numerator = Math.min(800, Math.max(8, nativeTicks));
        return java.math.BigInteger.valueOf(nativeEuPerTick)
            .multiply(java.math.BigInteger.valueOf(parallel))
            .multiply(java.math.BigInteger.valueOf(numerator))
            .add(java.math.BigInteger.valueOf(7999))
            .divide(java.math.BigInteger.valueOf(8000))
            .longValueExact();
    }

    public static int powerLimitedParallel(long power, long nativeEuPerTick, int maximum) {
        return powerLimitedParallel(power, nativeEuPerTick, 400, maximum);
    }

    public static int powerLimitedParallel(long power, long nativeEuPerTick, int nativeTicks, int maximum) {
        int low = 0, high = Math.min(MAX_PARALLEL, Math.max(0, maximum));
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (runEuPerTick(nativeEuPerTick, nativeTicks, middle) <= power) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    /** Sustain was already charged from the shared tick cap. Fusion receives only the real remainder. */
    public static long processingPowerBudget(long remainingPower, long tickCap, boolean sustainPaid) {
        if (!sustainPaid || remainingPower < 0 || tickCap <= 0) return 0;
        return Math.min(tickCap, remainingPower);
    }
}
