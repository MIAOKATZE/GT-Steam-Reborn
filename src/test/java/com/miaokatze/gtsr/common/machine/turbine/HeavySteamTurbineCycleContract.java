// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.turbine;

/** Standalone contract for the actual production arithmetic; no game runtime or fabricated machine ticks. */
public final class HeavySteamTurbineCycleContract {

    private HeavySteamTurbineCycleContract() {}

    public static void main(String[] args) {
        conversion(1, false);
        conversion(2, false);
        conversion(2, true);
        var rejected = HeavySteamTurbineCycle.settle(1, true, 100, 0, 1000, 10000, 10000);
        require(rejected.steam() == 0 && rejected.output() == 0, "bronze rejects superheated steam");
        require(HeavySteamTurbineCycle.usableDynamoCapacity(32, 1, 1000, 0) == 32, "LV throughput");
        require(HeavySteamTurbineCycle.usableDynamoCapacity(128, 1, 1000, 999) == 1, "remaining room");
        require(HeavySteamTurbineCycle.usableDynamoCapacity(512, 4, 1000, 1000) == 0, "full dynamo stops");
        require(HeavySteamTurbineCycle.usableDynamoCapacity(8192, 1, 100000, 0) >= 512, "high tier accepted");
        int efficiency = 0;
        for (int second = 0; second < 50; second++) efficiency = HeavySteamTurbineCycle.warmUp(efficiency, 125, false);
        require(efficiency == 100, "low steam warms from zero to 100 in 50 seconds");
        require(HeavySteamTurbineCycle.warmUp(100, 125, false) == 100, "low steam cannot exceed 100");
        require(HeavySteamTurbineCycle.warmUp(100, 125, true) == 101, "rated flow gains one point above 100");
        require(HeavySteamTurbineCycle.warmUp(125, 125, true) == 125, "material limit");
        require(HeavySteamTurbineCycle.warmUp(200, 150, true) == 200, "fuel changes preserve saved rotor efficiency");
        require(HeavySteamTurbineCycle.coolDown(125) == 115, "stopped decay is ten points per second");
        require(HeavySteamTurbineCycle.coolDown(5) == 0, "decay reaches zero");
        var stopped = HeavySteamTurbineCycle.settle(2, true, 200, 523, 1024, 0, 1024);
        require(stopped.steam() == 0 && stopped.credit() == 400, "full output preserves prepaid whole EU and fuel");
        var coolantBlocked = HeavySteamTurbineCycle.settle(2, true, 100, 0, 1024, 2048, 0);
        require(coolantBlocked.steam() == 0, "blocked coolant prevents fuel withdrawal");
        var startup = HeavySteamTurbineCycle.settle(1, false, 0, 0, 16, 256, 512);
        require(startup.steam() == 16 && startup.output() == 0, "zero efficiency still consumes fuel and warms");
        System.out
            .println("PASS actual production arithmetic: partial/rated flow, warm-up, fuel and credit conservation");
    }

    private static void conversion(int tier, boolean heated) {
        int rated = HeavySteamTurbineCycle.ratedSteam(tier, heated);
        int limit = HeavySteamTurbineCycle.efficiencyLimit(tier, heated);
        require(HeavySteamTurbineCycle.output(tier) == (tier == 1 ? 256 : 1024), "100 percent rating");
        int credit = 0;
        long energyUnits = 0, energy = 0, totalSteam = 0, coolant = 0;
        int waterRemainder = 0;
        // One litre and fragmentary dynamo room exercise partial flow and whole-EU prepaid balances.
        for (int tick = 0; tick < 2000; tick++) {
            int steam = tick % 2 == 0 ? 1 : rated;
            long room = tick % 3 == 0 ? 1 : 4096;
            var result = HeavySteamTurbineCycle.settle(tier, heated, limit, credit, steam, room, rated);
            require(result.steam() <= steam && result.steam() <= rated && result.output() <= room, "physical budgets");
            if (result.steam() < rated) require(result.efficiency() <= 100, "partial flow efficiency cap");
            energyUnits += (long) result.steam() * result.efficiency() * (heated ? 2 : 1) / 200 * 200;
            energy += result.output();
            totalSteam += result.steam();
            credit = result.credit();
            if (heated) coolant += result.steam();
            else {
                waterRemainder += result.steam();
                coolant += waterRemainder / 160;
                waterRemainder %= 160;
            }
            require(energyUnits == energy * 200 + credit, "whole-EU generation and prepaid balance conservation");
            require(
                heated ? coolant == totalSteam : coolant * 160 + waterRemainder == totalSteam,
                "coolant conservation");
        }
        var full = HeavySteamTurbineCycle.settle(tier, heated, limit, 0, rated * 2, 4096, rated);
        require(full.steam() == rated && full.fullFlow(), "rated throughput cap");
        require(full.output() == HeavySteamTurbineCycle.output(tier) * limit / 100, "full-flow efficiency output");
        var low = HeavySteamTurbineCycle.settle(tier, heated, limit, 0, rated / 2, 4096, rated);
        require(low.efficiency() == 100 && low.output() == HeavySteamTurbineCycle.output(tier) / 2, "half flow");
        System.out.println("tier=" + tier + " superheated=" + heated + " rated=" + rated + " limit=" + limit + " PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
