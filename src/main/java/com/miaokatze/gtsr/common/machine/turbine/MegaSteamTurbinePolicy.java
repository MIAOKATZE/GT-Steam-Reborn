// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.turbine;

/** Authoritative additive tuning, shared by runtime and the cumulative tooltip examples. */
public final class MegaSteamTurbinePolicy {

    public static final int MAX_CASING_TIER = 8; // UV; higher casings are rejected, never downgraded.

    private MegaSteamTurbinePolicy() {}

    public static boolean supportsCasing(int tier) {
        return tier >= 1 && tier <= MAX_CASING_TIER;
    }

    public static int powerMultiplier(int singularity) {
        return singularity == 2 ? 5 : singularity == 1 ? 2 : 1;
    }

    /** 100 units = one percentage point. Material is one unified channel, counted once. */
    public static int efficiencyLimit(int steamBase, int casing, int rings, int material, int singularity) {
        return steamBase + 500 * (casing - 1)
            + 1000 * rings
            + (material == 3 ? 2000 : material == 2 ? 1000 : 0)
            + (singularity == 2 ? 10000 : singularity == 1 ? 5000 : 0);
    }

    public static int savingsPercent(int powerParameter, int rings, int material, int singularity) {
        return Math.min(
            100,
            Math.max(0, 20 - ((10 - powerParameter) / 2) * 5) + 2 * rings
                + (material == 3 ? 5 : material == 2 ? 2 : 0)
                + (singularity == 2 ? 10 : singularity == 1 ? 5 : 0));
    }
}
