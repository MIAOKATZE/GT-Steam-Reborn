package com.miaokatze.gtsr.common.machine.turbine;

/** Integer accounting: ordinary steam carries 0.5 EU/L before the material efficiency. */
public final class HeavySteamTurbineCycle {

    public static final int BRONZE_OUTPUT_EUT = 128;
    public static final int STEEL_OUTPUT_EUT = 512;
    public static final int BRONZE_EFFICIENCY_PERCENT = 95;
    public static final int STEEL_EFFICIENCY_PERCENT = 90;
    public static final int ENERGY_UNITS_PER_EU = 200;
    public static final int STEAM_PER_WATER = 160;

    private HeavySteamTurbineCycle() {}

    public static int output(int tier) {
        return tier == 1 ? BRONZE_OUTPUT_EUT : tier == 2 ? STEEL_OUTPUT_EUT : 0;
    }

    public static int efficiency(int tier) {
        return tier == 1 ? BRONZE_EFFICIENCY_PERCENT : tier == 2 ? STEEL_EFFICIENCY_PERCENT : 0;
    }

    /** Credit is prepaid energy in 1/200 EU, so it remains valid across material changes and reloads. */
    public static int steamRequired(int tier, int credit) {
        int efficiency = efficiency(tier);
        int demand = output(tier) * ENERGY_UNITS_PER_EU - credit;
        return (demand + efficiency - 1) / efficiency;
    }

    public static int nextCredit(int tier, int credit, int steam) {
        return credit + steam * efficiency(tier) - output(tier) * ENERGY_UNITS_PER_EU;
    }

    public static long usableDynamoCapacity(long voltage, long amperes, long capacity, long stored) {
        return Math.min(voltage * amperes, Math.max(0, capacity - stored));
    }
}
