package com.miaokatze.gtsr.common.machine.turbine;

/** Standalone contract for the actual production arithmetic; no game runtime or fabricated machine ticks. */
public final class HeavySteamTurbineCycleContract {

    private HeavySteamTurbineCycleContract() {}

    public static void main(String[] args) {
        for (int tier = 1; tier <= 2; tier++) {
            int credit = 0, steamRemainder = 0;
            long steamTotal = 0, waterTotal = 0;
            int period = tier == 1 ? 19 : 9;
            for (int tick = 0; tick < period * 1000; tick++) {
                int steam = HeavySteamTurbineCycle.steamRequired(tier, credit);
                credit = HeavySteamTurbineCycle.nextCredit(tier, credit, steam);
                require(credit >= 0 && credit < HeavySteamTurbineCycle.efficiency(tier), "credit bounds");
                steamTotal += steam;
                steamRemainder += steam;
                waterTotal += steamRemainder / HeavySteamTurbineCycle.STEAM_PER_WATER;
                steamRemainder %= HeavySteamTurbineCycle.STEAM_PER_WATER;
                long output = (long) HeavySteamTurbineCycle.output(tier) * (tick + 1);
                require(
                    steamTotal * HeavySteamTurbineCycle.efficiency(tier)
                        == output * HeavySteamTurbineCycle.ENERGY_UNITS_PER_EU + credit,
                    "energy conservation");
                require(
                    waterTotal * HeavySteamTurbineCycle.STEAM_PER_WATER + steamRemainder == steamTotal,
                    "water conservation");
            }
            require(credit == 0, "full accounting period must settle");
            require(HeavySteamTurbineCycle.output(tier) == (tier == 1 ? 128 : 512), "fixed tier output");
            System.out.println(
                "tier=" + tier
                    + " output="
                    + HeavySteamTurbineCycle.output(tier)
                    + " efficiency="
                    + HeavySteamTurbineCycle.efficiency(tier)
                    + "% steam="
                    + steamTotal);
        }
        require(HeavySteamTurbineCycle.usableDynamoCapacity(32, 1, 1000, 0) == 32, "LV throughput");
        require(HeavySteamTurbineCycle.usableDynamoCapacity(128, 1, 1000, 999) == 1, "remaining room");
        require(HeavySteamTurbineCycle.usableDynamoCapacity(512, 4, 1000, 1000) == 0, "full dynamo stops");
        require(HeavySteamTurbineCycle.usableDynamoCapacity(8192, 1, 100000, 0) >= 512, "high tier accepted");
        System.out.println("PASS conversion conservation and dynamo throughput/capacity contract");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
