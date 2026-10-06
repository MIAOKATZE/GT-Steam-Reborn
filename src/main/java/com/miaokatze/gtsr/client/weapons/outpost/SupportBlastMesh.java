package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */

import org.lwjgl.opengl.GL11;

/** Bounded analytic support blast meshes. Coordinates and heading (radians) are the real impact pose. */
public final class SupportBlastMesh {

    private SupportBlastMesh() {}

    /** Each independently retired layer reaches zero before its cutoff, including short fireballs. */
    public static float layerOpacity(float age, float lifetime) {
        if (age < 0 || lifetime <= 0 || age >= lifetime) return 0;
        double q = Math.max(0D, (age - lifetime * .6D) / (lifetime * .4D));
        return (float) (1D - q * q * (3D - 2D * q));
    }

    public static int fireballLifetime(int id) {
        return id == 1 ? 80 : id == 3 || id == 4 ? 85 : 55;
    }

    public static int lifetime(int id) {
        return id == 4 ? 200 : id == 1 || id == 5 ? 145 : id == 3 ? 110 : id == 6 ? 110 : 110;
    }

    public static int impactLifetime(boolean ke80, boolean missile) {
        return lifetime(ke80 ? 1 : missile ? 0 : 2);
    }

    /** No event registration, distance cutoff, damage or nuclear timeline is performed here. */

    public static void renderImpact(double x, double y, double z, float age, int detail, double actualRadius,
        boolean missile) {
        renderImpact(false, x, y, z, age, detail, actualRadius, missile);
    }

    public static void renderImpact(boolean ke80, double x, double y, double z, float age, int detail,
        double actualRadius, boolean missile) {
        renderImpact(ke80, x, y, z, age, detail, actualRadius, missile, true);
    }

    public static void renderImpact(boolean ke80, double x, double y, double z, float age, int detail,
        double actualRadius, boolean missile, boolean ground) {
        render(ke80 ? 1 : missile ? 0 : 2, x, y, z, 0, age, detail, actualRadius, ground);
    }

    /** Explicit actual-radius overload also serves frozen server-driven remote theater impacts. */
    public static void render(int id, double x, double y, double z, float heading, float age, int detail,
        double actualRadius) {
        render(id, x, y, z, heading, age, detail, actualRadius, id != 6);
    }

    public static void render(int id, double x, double y, double z, float heading, float age, int detail,
        double actualRadius, boolean ground) {
        render(id, x, y, z, heading, age, detail, actualRadius, ground, null);
    }

    public static void render(int id, double x, double y, double z, float heading, float age, int detail,
        double actualRadius, boolean ground, double[] fragmentSurfaces) {
        double groundY = ground ? y : Double.NEGATIVE_INFINITY;
        if (id < 0 || id >= 7 || age < 0 || age >= lifetime(id)) return;
        double bound = BlastFxGeometry.radius(actualRadius);
        if (bound <= 0) return;
        // Airburst has the same 155mm central detonation; its larger radius belongs to the fragment fan.
        double size = BlastFxGeometry.cloudScale(id == 6 ? Math.min(8, bound) : bound),
            growth = 1 - Math.exp(-age / (id == 1 ? 22D : 16D));
        if (id == 1) size *= 1.40;
        double tail = Math.max(0D, Math.min(1D, (age - lifetime(id) * .45) / (lifetime(id) * .55)));
        float fade = (float) (1 - tail * tail * (3 - 2 * tail));
        int segments = detail > 1 ? 12 : 8, bands = detail > 1 ? 6 : 4;
        float heat = (float) Math.exp(-age / (id == 1 ? 26 : id == 4 ? 38 : id == 3 || id == 5 ? 24 : 15));
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        float fireballFade = layerOpacity(age, fireballLifetime(id));
        if (id != 6 && fireballFade > 0) for (int i = 0; i < 5; i++) {
            double a = i * 2.39996, spread = size * growth * .35;
            cloud(
                PixelBlastMesh.Kind.FIREBALL,
                groundY,
                x + Math.cos(a) * spread,
                y + size * .2,
                z + Math.sin(a) * spread,
                size * (.14 + growth * .32),
                size * (.17 + growth * .28),
                size * (.14 + growth * .32),
                1,
                .45F + .4F * heat,
                .1F + .45F * heat,
                heat * fireballFade * (id == 3 || id == 4 ? .46F : .36F),
                i,
                segments,
                bands);
        }
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // A broad rolling cloud, never a nuclear stem/cap. Heavy bombs retain a much larger cooled plume.
        for (int i = 0; i < (id == 3 || id == 4 || id == 5 ? 18 : 10); i++) {
            double a = i * 2.39996, spread = size * growth * (.2 + (i % 3) * .15);
            double rise = age * (id == 4 ? .105 : id == 3 || id == 5 ? .075 : .045) + (i % 4) * size * .16;
            double r = size * (.19 + growth * .22) * (1 + .1 * Math.sin(i * 1.7));
            // TNT-like pale smoke remains translucent; payload size, heat and dust distinguish the families.
            float grey = (id == 6 ? .96F
                : id == 2 ? .94F : id == 1 ? .90F : id == 3 || id == 4 || id == 5 ? .96F : id == 0 ? .90F : .92F)
                + (i % 4) * .008F;
            cloud(
                PixelBlastMesh.Kind.SMOKE,
                groundY,
                x + Math.cos(a) * spread,
                y + rise,
                z + Math.sin(a) * spread,
                r,
                r * 1.15,
                r,
                Math.min(1, grey + heat * .45F),
                Math.min(1, grey + heat * .18F),
                id == 3 || id == 4 ? grey * .97F : grey,
                fade * Math.min(1F, age / 5F) * (id == 1 ? .10F : id == 6 ? .075F : .08F),
                i + 13,
                segments,
                bands);
        }
        if (ground && id != 6) PixelBlastMesh.particles(x, y, z, bound, age, id);
        if (id == 6) {
            // Each ray is straight and lands once inside the server-owned 140-degree footprint.
            for (int i = 0; i < 22; i++) {
                double[] ray = AirburstFragmentGeometry.ray(i, bound, heading);
                double progress = Math.min(1, age / AirburstFragmentGeometry.FLIGHT_TICKS);
                double fx = x + ray[0] * progress, fy = y - 12 * progress, fz = z + ray[1] * progress;
                if (progress < 1) {
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    fragmentTrail(fx, fy, fz, ray[0] * .24, -12 * .24, ray[1] * .24, 1);
                    cloud(PixelBlastMesh.Kind.DEBRIS, groundY, fx, fy, fz, .11, .11, .11, 1F, .98F, .82F, .9F, i, 6, 3);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                } else if (fragmentSurfaces != null && i < fragmentSurfaces.length
                    && Double.isFinite(fragmentSurfaces[i])) {
                        float dustAge = (float) (age - AirburstFragmentGeometry.FLIGHT_TICKS);
                        float dustFade = Math.max(0, 1 - dustAge / 25F);
                        double width = .22 + dustAge * .015;
                        cloud(
                            PixelBlastMesh.Kind.GROUND_DUST,
                            fragmentSurfaces[i],
                            x + ray[0],
                            fragmentSurfaces[i] + .2 + Math.max(0, dustAge - 5) * .006,
                            z + ray[1],
                            width,
                            .14 + dustAge * .008,
                            width,
                            .63F,
                            .59F,
                            .49F,
                            dustFade * .23F,
                            i,
                            segments,
                            bands);
                    }
            }
            return;
        }
        if (!ground) return;
        // Discrete rising dust billows and ballistic rubble give depth without a coloured expanding ring.
        for (int i = 0; i < 12; i++) {
            double a = i * 2.39996, distance = BlastFxGeometry.dustTravel(bound, growth, i);
            cloud(
                PixelBlastMesh.Kind.GROUND_DUST,
                groundY,
                x + Math.cos(a) * distance,
                y + size * .06 + Math.max(0, age - 18) * .012,
                z + Math.sin(a) * distance,
                BlastFxGeometry.dustHalfWidth(bound, age),
                size * (.04 + age * .0007),
                BlastFxGeometry.dustHalfWidth(bound, age),
                .46F,
                .43F,
                .37F,
                fade * .12F,
                i + 31,
                segments,
                bands);
            double height = age * size * .035 - .012 * age * age;
            double debrisTravel = Math.min(bound * .9, age * size * .025);
            if (height > 0) cloud(
                PixelBlastMesh.Kind.DEBRIS,
                groundY,
                x + Math.cos(a) * debrisTravel,
                y + height,
                z + Math.sin(a) * debrisTravel,
                size * .015,
                size * .009,
                size * .02,
                .64F,
                .61F,
                .54F,
                fade * .35F * (float) Math.min(1D, height / Math.max(.001D, size * .1D)),
                i,
                6,
                3);
        }
    }

    private static void fragmentTrail(double x, double y, double z, double dx, double dy, double dz, float fade) {
        for (int i = 0; i < 8; i++) {
            double q = i / 8D, r = .12 * (1 - q * .6);
            PixelBlastMesh
                .cloud(x - dx * q, y - dy * q, z - dz * q, r, r, r, 1F, .98F, .82F, fade * (float) (1 - q) * .75F, i);
        }
    }

    private static void cloud(PixelBlastMesh.Kind kind, double floorY, double x, double y, double z, double rx,
        double ry, double rz, float r, float g, float b, float alpha, int seed, int segments, int bands) {
        PixelBlastMesh.particle(kind, x, y, z, rx, ry, rz, r, g, b, alpha, seed, floorY);
    }
}
