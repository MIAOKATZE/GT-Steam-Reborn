// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.machine;

/** Executes the production output and receipt ledger, without launching a game or fabricating native machine ticks. */
public final class SteamFluidDrillCycleContract {

    private static int checks;

    public static void main(String[] args) {
        equal(20, SteamFluidDrillCycle.TICKS, "one-second cycle");
        equal(200, output(1, 0, 0, false, false), "bronze cold water");
        equal(2_000, output(1, 10_000, 0, false, false), "bronze full water");
        equal(4_000, output(1, 10_000, 0, false, true), "bronze superheated full water");
        equal(16_000, output(2, 10_000, 0, false, true), "steel superheated full water");
        equal(3_200, output(2, 10_000, 1, false, true), "steel superheated distilled water");
        equal(1_600, output(2, 10_000, 2, false, true), "steel superheated salt water");
        equal(80, output(2, 10_000, 3, false, true), "overworld superheated lava");
        equal(800, output(2, 10_000, 3, true, true), "nether superheated lava");
        // The mode's ordinary whole-litre result is doubled; rounding cannot create a partial-litre bonus.
        equal(2, output(2, 1, 3, false, true), "cold lava integer boundary");
        for (int tier = 1; tier <= 2; tier++) for (int efficiency = 0; efficiency <= 10_000; efficiency += 137)
            for (int mode = 0; mode < 4; mode++) for (boolean nether : new boolean[] { false, true }) equal(
                output(tier, efficiency, mode, nether, false) * 2,
                output(tier, efficiency, mode, nether, true),
                "same-condition superheated multiplier");

        SteamFluidDrillCycle payment = new SteamFluidDrillCycle();
        equal(1, payment.select(1_000, 1_000, 1), "one hot sample cannot boost ordinary steam");
        equal(0, payment.select(1_000, 999, 999), "different fuels cannot complete one payment");
        equal(2, payment.select(1_000, 1_000, 1_000), "full hot supply wins");
        equal(0, payment.prepaid(), "simulation alone never pays");
        payment.paid(300, true);
        equal(0, payment.select(1_000, 1_000, 699), "short receipt waits for selected fuel");
        boolean rejected = false;
        try {
            payment.spend(1_000);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        require(rejected && payment.prepaid() == 300, "short receipt cannot produce or lose prepaid steam");
        SteamFluidDrillCycle restored = new SteamFluidDrillCycle();
        restored.restore(payment.prepaid(), payment.superheated());
        equal(2, restored.select(1_000, 0, 700), "reloaded partial receipt needs only the unpaid remainder");
        restored.paid(700, true);
        restored.spend(1_000);
        equal(0, restored.prepaid(), "completion charges exactly one full cycle");
        equal(1, restored.select(1_000, 1_000, 0), "exhausted hot supply can use ordinary next cycle");
        restored.paid(1_000, false);
        rejected = false;
        try {
            restored.paid(1, true);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected && restored.prepaid() == 1_000, "cannot relabel an ordinary prepaid receipt");
        restored.spend(1_000);
        restored.paid(3_000, true);
        restored.spend(1_000);
        equal(2_000, restored.prepaid(), "mode change preserves excess physical payment");
        equal(2, restored.select(1_000, 0, 0), "existing surplus can fund a later cycle");
        restored.spend(1_000);
        restored.spend(1_000);
        equal(0, restored.select(1_000, 0, 0), "three payments cannot fund a fourth cycle");
        uncertainWithdrawal();
        System.out.println("PASS drill production arithmetic and payment ledger: " + checks + " checks");
    }

    private static void uncertainWithdrawal() {
        SteamFluidDrillCycle payment = new SteamFluidDrillCycle();
        int[] sourceBalance = { 1_000 }, calls = { 0 };
        Runnable source = () -> {
            calls[0]++;
            sourceBalance[0] -= 300;
            // Real third-party failure boundary: stock changed, but the return/callback never happened.
            throw new IllegalStateException("withdrawn, receipt unavailable");
        };
        require(
            !payment.attemptPayment(1_000, true, source) && payment.isUncertain(),
            "post-withdraw exception locks payment");
        equal(700, sourceBalance[0], "fixture actually withdrew before throwing");
        equal(0, payment.prepaid(), "unknown withdrawal cannot fabricate a confirmed receipt");
        equal(0, payment.select(1_000, 1_000, 1_000), "uncertain payment authorizes neither fuel");
        require(
            payment.superheated() && payment.unresolvedRequest() == 1_000,
            "unknown request keeps its fuel and volume");
        require(!payment.attemptPayment(1_000, true, source), "next cycle cannot replay unknown withdrawal");
        equal(1, calls[0], "source not called a second time");
        SteamFluidDrillCycle restored = new SteamFluidDrillCycle();
        restored.restore(payment.prepaid(), payment.superheated(), payment.isUncertain(), payment.unresolvedRequest());
        require(!restored.attemptPayment(1_000, true, source), "reload preserves the exception lock");
        require(restored.superheated() && restored.unresolvedRequest() == 1_000, "reload preserves the unknown target");
        equal(1, calls[0], "reloaded ledger never calls source again");
        boolean rejected = false;
        try {
            restored.spend(1_000);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        require(rejected, "unknown payment cannot release production");
        SteamFluidDrillCycle prefix = new SteamFluidDrillCycle();
        prefix.paid(1_000, true);
        require(!prefix.attemptPayment(1_000, true, source), "later exception also locks a known paid prefix");
        equal(1_000, prefix.prepaid(), "confirmed prefix survives the exception");
        rejected = false;
        try {
            prefix.spend(1_000);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        require(rejected, "even a full known prefix cannot bypass uncertainty");
    }

    private static int output(int tier, int efficiency, int mode, boolean nether, boolean heated) {
        return SteamFluidDrillCycle.output(tier, efficiency, mode, nether, heated);
    }

    private static void equal(int expected, int actual, String message) {
        require(expected == actual, message + ": expected=" + expected + ", actual=" + actual);
    }

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
