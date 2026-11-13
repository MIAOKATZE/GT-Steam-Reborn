// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.machinevisual;

/** Real admission policy; the binder supplies one complete group or refuses it without publishing partial cells. */
public final class MachineVisualEntranceContracts {

    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        MachineVisualVisibility.Entrance entrance = new MachineVisualVisibility.Entrance();
        int[] reads = { 0 }, valid = { 1 };
        java.util.function.IntPredicate bind = index -> {
            reads[0]++;
            return (valid[0] & 1 << index) != 0;
        };
        check(entrance.filter(3, 0, bind) == 1 && reads[0] == 2, "wrong-material group never enters the hidden mask");
        for (int tick = 0; tick < 200; tick++)
            check(entrance.filter(3, 1, bind) == 1, "steady desired mask preserves accepted group and refusal");
        check(reads[0] == 2, "accepted and refused groups never periodically re-read their members");
        valid[0] = 3;
        check(
            entrance.filter(3, 1, bind) == 1 && reads[0] == 2,
            "world mutation alone does not retry animation admission");
        check(entrance.filter(1, 1, bind) == 1, "withdraw only refused group while the other keeps moving");
        check(
            entrance.filter(3, 1, bind) == 3 && reads[0] == 3,
            "explicit re-entry samples the formerly refused group once");
        check(entrance.filter(0, 3, bind) == 0, "stop revokes groups without another material read");
        check(entrance.filter(3, 0, bind) == 3 && reads[0] == 5, "resume binds current materials once for both groups");
        valid[0] = 0;
        check(entrance.filter(0, 3, bind) == 0, "another stop releases both groups");
        check(
            entrance.filter(3, 0, bind) == 0 && reads[0] == 7,
            "unmatched re-entry cannot claim restoration reservations");
        check(entrance.filter(0, 0, bind) == 0 && reads[0] == 7, "empty/effect-only host never queries materials");
        System.out.println("PASS MachineVisualEntrance contracts: " + checks);
    }
}
