package com.miaokatze.gtsr.common.machine;

/** Shared live and NEI probabilities, in GT's 1/10000 units. */
public final class GeothermalByproducts {

    public static final int CHIP_REQUIRED = 1;
    private static final int[] CHANCES = { 2000, 1000, 800, 400, 600, 200, 100, 50, 35, 10, 5, 2 };

    private GeothermalByproducts() {}

    public static double probability(int output) {
        return CHANCES[output] / 10000D;
    }

    public static int[] chances(boolean chip) {
        return java.util.Arrays.copyOf(CHANCES, chip ? CHANCES.length : 9);
    }
}
