// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.turbine;

/** Fixed acceptance examples exercise the same policy used by real fuel settlement. */
public final class MegaSteamTurbinePolicyContract {

    public static void main(String[] args) {
        require(MegaSteamTurbinePolicy.efficiencyLimit(15000, 1, 0, 1, 0) == 15000, "LV steel baseline");
        require(MegaSteamTurbinePolicy.efficiencyLimit(15000, 1, 0, 2, 0) == 16000, "titanium counted once");
        require(MegaSteamTurbinePolicy.efficiencyLimit(15000, 1, 0, 3, 0) == 17000, "tungstensteel counted once");
        require(MegaSteamTurbinePolicy.efficiencyLimit(15000, 1, 4, 1, 0) == 19000, "four added rings");
        require(MegaSteamTurbinePolicy.efficiencyLimit(15000, 6, 4, 3, 1) == 28500, "LuV entangled cumulative cap");
        require(MegaSteamTurbinePolicy.efficiencyLimit(15000, 6, 4, 3, 2) == 33500, "LuV critical cumulative cap");
        require(MegaSteamTurbinePolicy.efficiencyLimit(30000, 8, 4, 3, 2) == 49500, "UV dense critical maximum");
        int[] loads = { 10, 8, 6, 4, 2 }, savings = { 20, 15, 10, 5, 0 };
        for (int i = 0; i < loads.length; i++)
            require(MegaSteamTurbinePolicy.savingsPercent(loads[i], 0, 1, 0) == savings[i], "load savings " + loads[i]);
        require(MegaSteamTurbinePolicy.savingsPercent(10, 0, 2, 0) == 22, "titanium steam savings");
        require(MegaSteamTurbinePolicy.savingsPercent(10, 0, 3, 0) == 25, "tungstensteel steam savings");
        require(MegaSteamTurbinePolicy.savingsPercent(10, 4, 1, 0) == 28, "four rings steam savings");
        require(MegaSteamTurbinePolicy.savingsPercent(10, 4, 3, 1) == 38, "entangled steam savings");
        require(MegaSteamTurbinePolicy.savingsPercent(10, 4, 3, 2) == 43, "critical steam savings");
        require(MegaSteamTurbinePolicy.powerMultiplier(0) == 1, "ordinary power");
        require(MegaSteamTurbinePolicy.powerMultiplier(1) == 2, "entangled power");
        require(MegaSteamTurbinePolicy.powerMultiplier(2) == 5, "critical power");
        for (int tier = -1; tier <= 14; tier++)
            require(MegaSteamTurbinePolicy.supportsCasing(tier) == (tier >= 1 && tier <= 8), "casing boundary " + tier);
        System.out.println(
            "PASS mega production policy: cumulative caps/savings, load settings, singularities, LV–UV boundary");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
