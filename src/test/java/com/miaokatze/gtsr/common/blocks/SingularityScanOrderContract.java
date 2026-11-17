// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Compare the production packed order with the previous stable distance sort, including equal-distance ties and RNG.
 */
public final class SingularityScanOrderContract {

    private SingularityScanOrderContract() {}

    public static void main(String[] args) {
        for (double radius : new double[] { 0.5, 1, 8, 9.25, 16, 31.7, 48 }) {
            for (int mask : new int[] { 0, 7 }) verify(radius, mask);
        }
        long[] points = SingularityScanOrder.reserve(SingularityScanOrder.EMPTY, 0);
        require(SingularityScanOrder.reserve(points, 1) == points, "spare capacity must not allocate");
        System.out.println("PASS 14 reference-order/RNG cases; packed offsets and reuse checked");
    }

    private static void verify(double radius, int mask) {
        int r = (int) Math.ceil(radius);
        List<int[]> old = new ArrayList<>();
        long[] packed = SingularityScanOrder.EMPTY;
        int used = 0;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            double distance = (double) x * x + (double) y * y + (double) z * z;
            if (distance > radius * radius || ((x * 31 + y * 17 + z) & mask) != 0) continue;
            old.add(new int[] { x, y, z });
            packed = SingularityScanOrder.reserve(packed, used);
            packed[used++] = SingularityScanOrder.pack(x, y, z, (int) distance);
        }
        old.sort(Comparator.comparingInt(p -> p[0] * p[0] + p[1] * p[1] + p[2] * p[2]));
        SingularityScanOrder.sort(packed, used);
        for (int i = 0; i < used; i++) {
            int[] expected = old.get(i);
            require(expected[0] == SingularityScanOrder.x(packed[i]), "x/tie order changed");
            require(expected[1] == SingularityScanOrder.y(packed[i]), "y/tie order changed");
            require(expected[2] == SingularityScanOrder.z(packed[i]), "z/tie order changed");
            require(
                expected[0] * expected[0] + expected[1] * expected[1] + expected[2] * expected[2]
                    == SingularityScanOrder.distanceSquared(packed[i]),
                "distance changed");
        }
        for (int speed : new int[] { 0, 1, 3, 100 }) {
            Random before = new Random(761349), after = new Random(761349);
            int hits = 0, cap = Math.max(1, (int) Math.round(speed * 4 / 20.0));
            for (int i = 0; i < used; i++) {
                int[] p = old.get(i);
                double oldDistance = Math.sqrt((double) p[0] * p[0] + (double) p[1] * p[1] + (double) p[2] * p[2]);
                double newDistance = Math.sqrt(SingularityScanOrder.distanceSquared(packed[i]));
                double oldChance = Math
                    .min(1, speed * 0.6 * speedFactor(oldDistance, radius) * (0.5 + before.nextDouble()) * 4 / 20);
                double newChance = Math
                    .min(1, speed * 0.6 * speedFactor(newDistance, radius) * (0.5 + after.nextDouble()) * 4 / 20);
                boolean oldHit = before.nextDouble() < oldChance, newHit = after.nextDouble() < newChance;
                require(oldHit == newHit, "absorption/RNG decision changed");
                if (oldHit && ++hits == cap) break;
            }
            require(before.nextLong() == after.nextLong(), "world RNG position changed after cap");
        }
    }

    private static double speedFactor(double d, double r) {
        double result = d <= r * 0.5 ? 5 - 8 * d / r : 1 - 1.6 * (d - r * 0.5) / r;
        return Math.max(0.2, Math.min(5, result));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
