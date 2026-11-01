import java.util.Arrays;

/** Tests the exact extracted production policy methods without loading Minecraft or starting Gradle. */
public final class MotionBudgetContract {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static boolean[] select(int limit, int groupLimit, int held, int heldGroups, int[] members, int[] groups,
        long[] granted, long[] leases, boolean[] active, long tick) {
        int[] extra = new int[members.length], extraGroups = new int[members.length];
        for (int i = 0; i < members.length; i++) if (!active[i]) { extra[i] = members[i]; extraGroups[i] = groups[i]; }
        return MotionBudgetUnderTest.selectAdmissions(limit, groupLimit, held, heldGroups, members, groups,
            extra, extraGroups, granted, leases, active, tick);
    }
    public static void main(String[] args) {
        for (int q = 0; q < 4; q++) {
            check(MotionBudgetUnderTest.memberLimit(q) == new int[] { 0, 128, 512, 1024 }[q], "member limit " + q);
            check(MotionBudgetUnderTest.groupLimit(q) == new int[] { 0, 16, 64, 128 }[q], "group limit " + q);
        }
        int[] fiveMembers = { 4, 18, 30, 6, 11 }, fiveGroups = { 2, 1, 2, 1, 1 };
        long[] never = new long[5]; Arrays.fill(never, Long.MIN_VALUE);
        boolean[] five = select(128, 16, 0, 0, fiveMembers, fiveGroups, never, new long[5], new boolean[5], 0);
        for (boolean admitted : five) check(admitted, "all five selected machines must fit LOW");
        boolean[] result = select(128, 16, 98, 2, new int[] { 30 }, new int[] { 2 },
            new long[] { 0 }, new long[] { 0 }, new boolean[] { false }, 1);
        check(result[0], "exact member boundary fits");
        result = select(128, 16, 99, 2, new int[] { 30 }, new int[] { 2 },
            new long[] { 0 }, new long[] { 0 }, new boolean[] { false }, 1);
        check(!result[0], "one member above boundary defers");
        result = select(128, 16, 0, 15, new int[] { 4 }, new int[] { 2 },
            new long[] { 0 }, new long[] { 0 }, new boolean[] { false }, 1);
        check(!result[0], "independent group boundary defers");
        result = select(128, 16, 120, 8, new int[] { 30, 30, 30, 30, 30 }, new int[] { 2, 2, 2, 2, 2 },
            new long[] { 0, 0, 0, 0, Long.MIN_VALUE }, new long[] { 120, 120, 120, 120, 0 },
            new boolean[] { true, true, true, true, false }, 120);
        for (boolean admitted : result) check(!admitted, "expired leases retire, but cannot spend pending restoration");
        check(!MotionBudgetUnderTest.restorationAcknowledged(3, 2, false), "old static upload cannot free reservation");
        check(!MotionBudgetUnderTest.restorationAcknowledged(3, 3, true), "hidden upload cannot free reservation");
        check(MotionBudgetUnderTest.restorationAcknowledged(3, 3, false), "current static upload releases reservation");
        result = select(128, 16, 0, 0, new int[] { 30 }, new int[] { 2 },
            new long[] { Long.MIN_VALUE }, new long[] { 0 }, new boolean[] { false }, 121);
        check(result[0], "ACK-freed space admits waiter");
        result = select(128, 16, 4, 2, new int[] { 4 }, new int[] { 2 },
            new long[] { 0 }, new long[] { 120 }, new boolean[] { true }, 900);
        check(result[0], "expired lease remains stable without pressure");
        result = select(128, 16, 512, 32, new int[] { 30 }, new int[] { 2 },
            new long[] { 0 }, new long[] { 999 }, new boolean[] { true }, 1);
        check(!result[0], "quality reduction retires existing debt without hiding a skipped draw");
        result = select(0, 0, 69, 7, fiveMembers, fiveGroups, never, new long[5], new boolean[] { true, true, true, true, true }, 1);
        for (boolean admitted : result) check(!admitted, "OFF retires every ordinary motion group");
        int[] members = { 64, 64, 64, 64, 64 }, groups = { 8, 8, 8, 8, 8 };
        long[] granted = never.clone(), leases = new long[5];
        boolean[] active = new boolean[5], seen = new boolean[5];
        int held = 0, heldGroups = 0;
        for (int round = 0; round < 3; round++) {
            long start = round * 121L;
            result = select(128, 16, held, heldGroups, members, groups, granted, leases, active, start);
            int admitted = 0;
            for (int i = 0; i < result.length; i++) if (result[i]) {
                admitted++; seen[i] = true; granted[i] = start; leases[i] = start + 120;
            }
            check(admitted == 2, "capacity filled by complete hosts");
            active = result;
            held = admitted * 64; heldGroups = admitted * 8;
            for (int t = 1; t < 120; t++) {
                boolean[] stable = select(128, 16, held, heldGroups, members, groups, granted, leases, active, start + t);
                check(Arrays.equals(active, stable), "no per-tick lease churn " + round + ':' + t);
            }
            boolean[] retiring = select(128, 16, held, heldGroups, members, groups, granted, leases, active, start + 120);
            for (boolean selected : retiring) check(!selected, "restoration must finish before replacing holders");
            // The next epoch represents explicit static-upload acknowledgements for all retiring reservations.
            active = new boolean[5]; held = heldGroups = 0;
        }
        for (boolean selected : seen) check(selected, "each fitting waiter gets a finite turn");
        System.out.println("PASS source policy checks=" + checks + "; no Minecraft/Forge/GL acceptance");
    }
}
