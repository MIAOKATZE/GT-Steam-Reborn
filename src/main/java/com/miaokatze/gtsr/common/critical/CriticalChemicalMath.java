package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;

/** RC2 chemical policy, in whole base EU (never the turbine's half-EU units). */
public final class CriticalChemicalMath {

    public static final int RULE_VERSION = 1;
    public static final int WINDOW_COUNT = 4;
    // Balance anchor: economic full load = GTValues.V[10] (UEV) average EU/t for 400 ticks.
    public static final BigInteger CHEMICAL_BASE_HEAT_CAP_EU = BigInteger.valueOf(167_772_160L);
    private static final int[][] WEIGHTS = { { 1, 1, 0 }, { 1, 1, 1 }, { 1, 1, 4 } };
    private static final int[] MULTIPLIERS = { 8, 20, 8, 1 };

    private CriticalChemicalMath() {}

    public static int window(int window) {
        return Math.floorMod(window, WINDOW_COUNT);
    }

    public static int multiplier(int window) {
        return MULTIPLIERS[window(window)];
    }

    public static BigInteger cap(int window) {
        return CHEMICAL_BASE_HEAT_CAP_EU.multiply(BigInteger.valueOf(window(window) == 2 ? 4 : 1));
    }

    /** Exact shared proportion; an unmatched paid fraction stays in escrow for a later batch. */
    public static BigInteger[] match(int window, BigInteger[] paidHeat, BigInteger cap) {
        if (paidHeat.length != 3 || cap.signum() < 0) throw new IllegalArgumentException("Invalid chemical heat");
        for (BigInteger heat : paidHeat) if (heat.signum() < 0) throw new IllegalArgumentException("Negative heat");
        BigInteger[] result = { BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO };
        int mode = window(window);
        if (mode == 3) {
            BigInteger left = cap;
            for (int i = 0; i < 3; i++) {
                result[i] = paidHeat[i].min(left);
                left = left.subtract(result[i]);
            }
            return result;
        }
        int sum = 0;
        for (int weight : WEIGHTS[mode]) sum += weight;
        BigInteger common = cap.divide(BigInteger.valueOf(sum));
        for (int i = 0; i < 3; i++)
            if (WEIGHTS[mode][i] > 0) common = common.min(paidHeat[i].divide(BigInteger.valueOf(WEIGHTS[mode][i])));
        for (int i = 0; i < 3; i++) result[i] = common.multiply(BigInteger.valueOf(WEIGHTS[mode][i]));
        return result;
    }

    public static BigInteger sum(BigInteger[] heat) {
        return heat[0].add(heat[1])
            .add(heat[2]);
    }

    public static BigInteger litresForHeat(BigInteger heat, BigInteger euPerL) {
        if (heat.signum() <= 0) return BigInteger.ZERO;
        if (euPerL.signum() <= 0) throw new IllegalArgumentException("Nonpositive fuel heat");
        return heat.add(euPerL)
            .subtract(BigInteger.ONE)
            .divide(euPerL);
    }
}
