package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */

/** World-block geometry bound shared by visual tests, clouds and the actual refraction pass. */
public final class BlastFxGeometry {

    public static final double MAX_RADIUS = 128D * 1.4D;

    private BlastFxGeometry() {}

    public static double radius(double actual) {
        return Double.isFinite(actual) && actual >= 0 ? Math.min(MAX_RADIUS, actual) : 0;
    }

    public static double cloudScale(double actual) {
        return radius(actual) * .625;
    }

    public static double waveRadius(double actual, float age) {
        return radius(actual) * Math.max(0, Math.min(1, .12 + age / 20D));
    }

    public static double dustTravel(double actual, double growth, int index) {
        return radius(actual) * .625 * growth * (1 + (index % 3) * .18);
    }

    public static double dustHalfWidth(double actual, float age) {
        return radius(actual) * Math.min(.125, .625 * (.13 + age * .0017));
    }
}
