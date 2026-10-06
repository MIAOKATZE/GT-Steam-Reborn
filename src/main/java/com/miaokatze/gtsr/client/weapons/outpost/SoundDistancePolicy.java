package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */

/** Fixed world-space ranges: source gain never extends the audible radius. */
public final class SoundDistancePolicy {

    private SoundDistancePolicy() {}

    private static final java.util.Set<String> VARIANTS = java.util.Collections.unmodifiableSet(
        new java.util.HashSet<>(
            java.util.Arrays.asList(
                "ak130_fire",
                "ak130_impact",
                "autocannon_fire",
                "cannon_fire",
                "casing_drop",
                "casing_drop_gx2",
                "coilgun_arc",
                "coilgun_charge",
                "coilgun_fire",
                "fab5000_impact",
                "fab500_impact",
                "gatling_fire",
                "gatling_motor",
                "gau8_fire",
                "gun_2a3_fire",
                "gun_2a3_impact",
                "hj9_fire",
                "howitzer_fire",
                "hq10_fire",
                "hq9_fire",
                "judgement_fire",
                "ke80_fire",
                "ke80_impact",
                "laser_zap",
                "m1935_fire",
                "m1935_impact",
                "mortar_fire",
                "nuclear_geiger",
                "nuclear_impact",
                "nuclear_launch",
                "pj11_fire",
                "pj26_fire",
                "positron_fire",
                "pr3_beam_loop",
                "pr3_fire",
                "prism_pulse",
                "qlz04_fire",
                "railgun_fire",
                "rg510_fire",
                "rocket_whoosh",
                "storm_mlrs_fire",
                "support_airburst_impact",
                "support_cruisejet_entry",
                "support_heavy_impact",
                "support_rocket_entry",
                "vls_fire",
                "vls_hatch",
                "vls_ignite")));

    public static boolean ui(String key) {
        return key.equals("kill_confirm") || key.startsWith("kill_confirm_")
            || key.startsWith("ui_")
            || key.startsWith("terminal_")
            || key.equals("support_ready");
    }

    public static double range(String key) {
        if (ui(key)) return Double.POSITIVE_INFINITY;
        if (key.startsWith("nuclear")) return 1800;
        if (key.contains("impact") || key.contains("explosion")) return 512;
        if (key.contains("motor") || key.contains("charge")) return 32;
        if (key.contains("loop") || key.equals("pj11_fire")
            || key.equals("gau8_fire")
            || key.equals("gatling_fire")
            || key.contains("brrrt")) return 96;
        if (key.contains("fire") || key.contains("zap")) return 128;
        return 96;
    }

    public static float gain(String key, double distance, float sourceGain) {
        if (!Double.isFinite(distance) || distance < 0 || !Float.isFinite(sourceGain)) return 0;
        float base = Math.max(0, Math.min(1, sourceGain));
        if (ui(key)) return base;
        double range = range(key), near = Math.min(12, range * .125);
        if (distance >= range) return 0;
        if (distance <= near) return base;
        double q = (distance - near) / (range - near);
        return (float) (base * (1 - q) * (1 - q) / (1 + 5 * q));
    }

    public static int tier(String key, double distance) {
        if (!VARIANTS.contains(key)) return 0;
        double range = range(key);
        return distance < Math.min(20, range * .25) ? 0 : distance < range * .55 ? 1 : 2;
    }

    public static int stableTier(String key, double distance, int current) {
        double margin = range(key) * .04;
        int next = tier(key, distance);
        return next > current ? tier(key, Math.max(0, distance - margin))
            : next < current ? tier(key, distance + margin) : current;
    }
}
