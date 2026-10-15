package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;

/** Exact volume/energy arithmetic shared by the two energy backends. No power or tier cap. */
public final class CriticalEnergyMath {

    public static final int CYCLE_TICKS = 400;
    public static final int IRRADIATION_PARALLEL = 256;
    public static final double WATER_A = 1_000_000D, WATER_B = 1_000_000D;
    public static final double SALT_A = 1_000_000D, SALT_B = 1_000_000D;
    private static final BigInteger WATER_DENOMINATOR = BigInteger.valueOf(160);

    private CriticalEnergyMath() {}

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
        if (volumes.length != 6 || previousRemainder < 0 || previousRemainder >= 160)
            throw new IllegalArgumentException("Invalid steam snapshot or condensation remainder");
        BigInteger eu = BigInteger.ZERO, numerator = BigInteger.valueOf(previousRemainder);
        int[] rate = { 16, 48, 80, 16000, 48000, 80000 };
        for (int i = 0; i < 6; i++) {
            if (volumes[i].signum() < 0) throw new IllegalArgumentException("Negative steam");
            eu = eu.add(volumes[i].multiply(BigInteger.valueOf(rate[i])));
            numerator = numerator.add(volumes[i].multiply(BigInteger.valueOf(i < 3 ? 1 : 1000)));
        }
        BigInteger[] water = numerator.divideAndRemainder(WATER_DENOMINATOR);
        return new TurbineBatch(eu, water[0], water[1].intValue());
    }

    public static final class TurbineBatch {

        public final BigInteger eu, water;
        public final int remainder;

        private TurbineBatch(BigInteger eu, BigInteger water, int remainder) {
            this.eu = eu;
            this.water = water;
            this.remainder = remainder;
        }
    }
}
