// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.blocks;

import java.util.Arrays;

/** Distance then the original dx/dy/dz order, bounded by the existing 48-block scan cap. */
final class SingularityScanOrder {

    static final long[] EMPTY = new long[0];

    private SingularityScanOrder() {}

    static long pack(int dx, int dy, int dz, int distanceSquared) {
        return ((long) distanceSquared << 21) | ((long) (dx + 64) << 14) | ((long) (dy + 64) << 7) | (dz + 64);
    }

    static int x(long point) {
        return (int) ((point >> 14) & 127) - 64;
    }

    static int y(long point) {
        return (int) ((point >> 7) & 127) - 64;
    }

    static int z(long point) {
        return (int) (point & 127) - 64;
    }

    static int distanceSquared(long point) {
        return (int) (point >>> 21);
    }

    static long[] reserve(long[] points, int used) {
        return used < points.length ? points : Arrays.copyOf(points, Math.max(64, used + used / 2 + 1));
    }

    static void sort(long[] points, int used) {
        Arrays.sort(points, 0, used);
    }
}
