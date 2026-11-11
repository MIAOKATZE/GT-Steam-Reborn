// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.turbine;

/** Per-tick generation truncates to whole EU. Prepaid whole EU retain the legacy 1/200 EU credit unit. */
public final class HeavySteamTurbineCycle {

    public static final int BRONZE_OUTPUT_EUT = 256;
    public static final int STEEL_OUTPUT_EUT = 1024;
    public static final int ENERGY_UNITS_PER_EU = 200;
    public static final int STEAM_PER_WATER = 160;
    public static final int TICKS_PER_SECOND = 20;

    private HeavySteamTurbineCycle() {}

    public static int output(int tier) {
        return tier == 1 ? BRONZE_OUTPUT_EUT : tier == 2 ? STEEL_OUTPUT_EUT : 0;
    }

    public static int ratedSteam(int tier, boolean superheated) {
        if (superheated && tier != 2) return 0;
        return output(tier) * (superheated ? 1 : 2);
    }

    public static int efficiencyLimit(int tier, boolean superheated) {
        return tier == 1 ? (superheated ? 0 : 125) : tier == 2 ? (superheated ? 200 : 150) : 0;
    }

    public static int warmUp(int efficiency, int limit, boolean fullFlowSecond) {
        if (efficiency < 100) return Math.min(100, efficiency + 2);
        return fullFlowSecond && efficiency < limit ? efficiency + 1 : efficiency;
    }

    public static int coolDown(int efficiency) {
        return Math.max(0, efficiency - 10);
    }

    /** Discard sub-EU generation each tick; retain prepaid whole EU from litre rounding or refused output. */
    public static Settlement settle(int tier, boolean superheated, int efficiency, int credit, int availableSteam,
        long outputRoom, int coolantSteamRoom) {
        int rated = ratedSteam(tier, superheated);
        int prepaidEU = credit / ENERGY_UNITS_PER_EU;
        if (rated == 0 || outputRoom <= 0) return new Settlement(0, 0, prepaidEU * ENERGY_UNITS_PER_EU, 0, false);
        int flow = Math.min(rated, Math.min(availableSteam, coolantSteamRoom));
        int limitedEfficiency = Math.min(efficiency, efficiencyLimit(tier, superheated));
        int unitEnergy = limitedEfficiency * (superheated ? 2 : 1);
        long fullFlowOutput = prepaidEU + (long) rated * unitEnergy / ENERGY_UNITS_PER_EU;
        boolean fullFlow = flow == rated && outputRoom >= fullFlowOutput;
        int effectiveEfficiency = fullFlow ? limitedEfficiency : Math.min(100, limitedEfficiency);
        unitEnergy = effectiveEfficiency * (superheated ? 2 : 1);
        if (unitEnergy > 0 && !fullFlow) {
            long demand = Math.max(0L, outputRoom - prepaidEU) * ENERGY_UNITS_PER_EU;
            flow = (int) Math.min(flow, (demand + unitEnergy - 1) / unitEnergy);
        }
        int energy = prepaidEU + flow * unitEnergy / ENERGY_UNITS_PER_EU;
        int output = (int) Math.min(outputRoom, energy);
        return new Settlement(
            flow,
            output,
            (energy - output) * ENERGY_UNITS_PER_EU,
            effectiveEfficiency,
            fullFlow && flow == rated);
    }

    public static long usableDynamoCapacity(long voltage, long amperes, long capacity, long stored) {
        return Math.min(voltage * amperes, Math.max(0, capacity - stored));
    }

    public static final class Settlement {

        private final int steam, output, credit, efficiency;
        private final boolean fullFlow;

        private Settlement(int steam, int output, int credit, int efficiency, boolean fullFlow) {
            this.steam = steam;
            this.output = output;
            this.credit = credit;
            this.efficiency = efficiency;
            this.fullFlow = fullFlow;
        }

        public int steam() {
            return steam;
        }

        public int output() {
            return output;
        }

        public int credit() {
            return credit;
        }

        public int efficiency() {
            return efficiency;
        }

        public boolean fullFlow() {
            return fullFlow;
        }
    }
}
