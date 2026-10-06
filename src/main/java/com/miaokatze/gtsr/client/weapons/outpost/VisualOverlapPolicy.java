package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */

/** Allocation-free spatial rules shared by the actual visual consumers. */
public final class VisualOverlapPolicy {

    private VisualOverlapPolicy() {}

    public static boolean blastCovers(double ax, double ay, double az, double radius, double bx, double by, double bz,
        double otherRadius) {
        if (radius < otherRadius || radius <= 0) return false;
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        double reach = Math.max(.35, radius - Math.max(0, otherRadius) * .35);
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    public static boolean sameTrail(double dx, double dy, double dz, double ax, double ay, double az, double bx,
        double by, double bz, double length) {
        double dot = ax * bx + ay * by + az * bz;
        if (dot < .985) return false;
        double along = dx * ax + dy * ay + dz * az;
        double lateralSq = Math.max(0, dx * dx + dy * dy + dz * dz - along * along);
        return lateralSq <= .16 && Math.abs(along) <= Math.max(.5, length * .45);
    }
}
