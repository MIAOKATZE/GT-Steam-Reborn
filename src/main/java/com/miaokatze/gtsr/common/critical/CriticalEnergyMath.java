package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;

/** Exact volume/energy arithmetic. Raw heat never includes the adaptive efficiency multiplier. */
public final class CriticalEnergyMath {

    public static final int CYCLE_TICKS = 400;
    public static final int IRRADIATION_PARALLEL = 256;
    public static final int FUSION_PARALLEL = 8196;
    public static final int MIN_EFFICIENCY = 100, MAX_EFFICIENCY = 2000, EFFICIENCY_STEP = 10;
    public static final int TURBINE_WARMUP_TICKS = 3600;
    public static final int TURBINE_FULL_WARMUP_BASE_AMPS_MAX = 16;
    public static final double WATER_A = 1_000_000D, WATER_B = 1_000_000D;
    public static final double SALT_A = 1_000_000D, SALT_B = 1_000_000D;
    private static final BigInteger WATER_DENOMINATOR = BigInteger.valueOf(160);

    private CriticalEnergyMath() {}

    public static int efficiency(int value) {
        return Math.max(MIN_EFFICIENCY, Math.min(MAX_EFFICIENCY, value));
    }

    /** Retained source compatibility; changing input heat can never award permanent efficiency. */
    public static int trendEfficiency(int current, BigInteger previous, BigInteger raw) {
        return efficiency(current);
    }

    public static int runningEfficiency(int paidRunningTicks) {
        int ticks = Math.max(0, Math.min(TURBINE_WARMUP_TICKS, paidRunningTicks));
        return MIN_EFFICIENCY + (MAX_EFFICIENCY - MIN_EFFICIENCY) * ticks / TURBINE_WARMUP_TICKS;
    }

    /** Absolute paid half-EU load, independent of steam identity, volume and earlier batches. */
    public static int targetWarmupTicks(BigInteger halfHeat) {
        if (halfHeat.signum() <= 0) return 0;
        BigInteger fullLoad = BigInteger.valueOf(CriticalSolarPolicy.MAX_VOLTAGE)
            .multiply(BigInteger.valueOf(2L * CYCLE_TICKS * TURBINE_FULL_WARMUP_BASE_AMPS_MAX));
        return halfHeat.multiply(BigInteger.valueOf(TURBINE_WARMUP_TICKS))
            .divide(fullLoad)
            .min(BigInteger.valueOf(TURBINE_WARMUP_TICKS))
            .intValue();
    }

    public static BigInteger generatedEU(BigInteger raw, int efficiency) {
        return raw.multiply(BigInteger.valueOf(efficiency(efficiency)))
            .divide(BigInteger.valueOf(100));
    }

    /** Half-EU per litre, matching the mega turbine's full overlimit steam cascade. */
    public static BigInteger steamHeatPerL(int mode) {
        return BigInteger.valueOf(new int[] { 1, 3, 5, 1000, 3000, 5000 }[mode]);
    }

    public static long logarithmic(long stock, double factor, double a, double b) {
        if (stock <= 0 || !(factor > 0) || !(a > 0) || !(b > 0)) return 0;
        double result = Math.floor(factor * a * Math.log1p(stock / b));
        return result >= stock ? stock : result <= 0 ? 0 : (long) result;
    }

    public static BigInteger logarithmic(BigInteger stock, double factor, double a, double b) {
        if (stock.signum() <= 0 || !(factor > 0) || !(a > 0) || !(b > 0)) return BigInteger.ZERO;
        double amount = Math.floor(factor * a * Math.log1p(stock.doubleValue() / b));
        if (Double.isInfinite(amount)) return stock;
        if (!(amount > 0)) return BigInteger.ZERO;
        return new java.math.BigDecimal(amount).toBigInteger()
            .min(stock);
    }

    public static TurbineBatch turbine(long[] volumes, int previousRemainder) {
        BigInteger[] big = new BigInteger[6];
        for (int i = 0; i < 6; i++) big[i] = BigInteger.valueOf(volumes[i]);
        return turbine(big, previousRemainder);
    }

    /** Order: normal, superheated, supercritical, then their three dense counterparts. */
    public static TurbineBatch turbine(BigInteger[] volumes, int previousRemainder) {
        return turbine(volumes, previousRemainder, 0);
    }

    public static TurbineBatch turbine(BigInteger[] volumes, int previousRemainder, int previousEnergyRemainder) {
        if (volumes.length != 6 || previousRemainder < 0 || previousRemainder >= 160)
            throw new IllegalArgumentException("Invalid steam snapshot or condensation remainder");
        if (previousEnergyRemainder < 0 || previousEnergyRemainder > 1)
            throw new IllegalArgumentException("Invalid half-EU remainder");
        BigInteger heat = BigInteger.ZERO, numerator = BigInteger.valueOf(previousRemainder);
        for (int i = 0; i < 6; i++) {
            if (volumes[i].signum() < 0) throw new IllegalArgumentException("Negative steam");
            heat = heat.add(volumes[i].multiply(steamHeatPerL(i)));
            numerator = numerator.add(volumes[i].multiply(BigInteger.valueOf(i < 3 ? 1 : 1000)));
        }
        BigInteger[] water = numerator.divideAndRemainder(WATER_DENOMINATOR);
        BigInteger[] energy = heat.add(BigInteger.valueOf(previousEnergyRemainder))
            .divideAndRemainder(BigInteger.valueOf(2));
        return new TurbineBatch(energy[0], water[0], water[1].intValue(), heat, energy[1].intValue());
    }

    public static final class TurbineBatch {

        public final BigInteger eu, water, rawHeat;
        public final int remainder, energyRemainder;

        private TurbineBatch(BigInteger eu, BigInteger water, int remainder, BigInteger rawHeat, int energyRemainder) {
            this.eu = eu;
            this.water = water;
            this.remainder = remainder;
            this.rawHeat = rawHeat;
            this.energyRemainder = energyRemainder;
        }
    }
}
