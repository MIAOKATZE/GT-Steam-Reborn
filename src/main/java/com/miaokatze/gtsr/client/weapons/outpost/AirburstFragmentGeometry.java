package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */

/** Rays share the authoritative downward 140-degree fan and 12-block airburst altitude. */
public final class AirburstFragmentGeometry {

    public static final double FLIGHT_TICKS = 10;

    private AirburstFragmentGeometry() {}

    public static double[] ray(int index, double radius, double heading) {
        if (index < 0 || index >= 22) throw new IllegalArgumentException("fragment index");
        double angle = heading + Math.toRadians(-69.5 + 139D * index / 21);
        double distance = BlastFxGeometry.radius(radius) * (.45 + (index % 5) * .11);
        return new double[] { Math.cos(angle) * distance, Math.sin(angle) * distance };
    }
}
