package com.miaokatze.gtsr.client.encounter;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.CombatGeometry;

/** World-space, server-authored gravity paths; callers retain the exact damage footprint underneath. */
public final class RoyalSkillVisual {

    private RoyalSkillVisual() {}

    public static void render(CombatGeometry g, int skill, int direction, int stage, double age, double duration,
        double meteorTick) {
        render(g, skill, direction, stage, age, duration, meteorTick, 3, 3);
    }

    public static void render(CombatGeometry g, int skill, int direction, int stage, double age, double duration,
        double meteorTick, double halfX, double halfZ) {
        double fade = stage == 0 ? 1 : Math.max(0, 1 - age / Math.max(1, duration));
        try (GlScope local = new GlScope()) {
            GL11.glTranslated(g.x, g.y + .16, g.z);
            FineRuneRenderer.unlit();
            if (skill == 3) {
                if (stage == 0) meteor(meteorTick, age);
                else {
                    double progress = Math.min(1, age / 12);
                    for (int layer = 0; layer < 3; layer++) {
                        GL11.glColor4f(.8F, .45F, 1F, (float) (fade * (.46 - layer * .1)));
                        FineRuneRenderer.arc(
                            Math.min(g.radius, (progress + layer * .13) * g.radius),
                            .06,
                            .2 + layer * .5,
                            age,
                            layer,
                            1);
                    }
                    FineRuneRenderer.voxel(0, .3 + age * .14, 0, 1.3 * fade, .8F, .5F, 1F, (float) (fade * .65), true);
                }
            } else if (skill == 1) {
                double progress = stage == 0 ? Math.min(1, age / 20) : 1;
                double top = stage == 0 ? 4.5 * (1 - progress) + .55 : .55 * Math.max(0, 1 - age / 8);
                for (int level = 0; level < 3; level++) {
                    GL11.glColor4f(.77F, .4F, 1F, (float) (fade * (.4 - level * .08)));
                    FineRuneRenderer.arc(g.radius, .028, top + level * .32, age, level, 1);
                }
                GL11.glColor4f(1F, .77F, .28F, (float) (fade * .6));
                GL11.glBegin(GL11.GL_LINES);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6, x = Math.cos(a) * g.radius, z = Math.sin(a) * g.radius;
                    GL11.glVertex3d(x, 0, z);
                    GL11.glVertex3d(x, Math.max(.3, top), z);
                    GL11.glVertex3d(x, top, z);
                    GL11.glVertex3d(x * .92, top + .22, z * .92);
                }
                GL11.glEnd();
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    FineRuneRenderer.voxel(
                        Math.cos(a) * g.radius * .72,
                        top,
                        Math.sin(a) * g.radius * .72,
                        .085,
                        .82F,
                        .45F,
                        1F,
                        (float) (fade * .65),
                        true);
                }
            } else if (skill == 5) {
                double[] d = direction(direction);
                GL11.glColor4f(.78F, .45F, 1F, (float) (fade * .55));
                GL11.glBegin(GL11.GL_LINES);
                for (int side = -1; side <= 1; side += 2) {
                    GL11.glVertex3d(side * .4, .9, 0);
                    GL11.glVertex3d(d[0] * 5 + side * .4, .9 + d[1] * 5, d[2] * 5);
                    GL11.glVertex3d(0, .9, side * .4);
                    GL11.glVertex3d(d[0] * 5, .9 + d[1] * 5, d[2] * 5 + side * .4);
                }
                GL11.glEnd();
                for (int i = 0; i < 10; i++) {
                    double t = ((age * .045 + i * .1) % 1) * 5;
                    FineRuneRenderer.voxel(
                        d[0] * t,
                        .9 + d[1] * t,
                        d[2] * t,
                        .075,
                        .9F,
                        .55F,
                        1F,
                        (float) (fade * .72),
                        i % 3 == 0);
                }
            } else if (skill == 2 || skill == 4) {
                double progress = Math.min(1, age / (skill == 4 ? 12 : 18));
                double radius = skill == 4 ? g.radius : Math.min(14, g.radius);
                for (int layer = 0; layer < 3; layer++) {
                    GL11.glColor4f(
                        skill == 4 ? 1F : .7F,
                        skill == 4 ? .75F : .36F,
                        skill == 4 ? .2F : 1F,
                        (float) (fade * (.52 - layer * .1)));
                    if (skill == 4)
                        domainBoundary(halfX, halfZ, g.radius * Math.min(1, progress + layer * .12), .4 + layer * .2);
                    else FineRuneRenderer.arc(
                        Math.max(.25, radius * Math.min(1, progress + layer * .12)),
                        .025,
                        .4 + layer * .2,
                        age,
                        layer,
                        1);
                }
                FineRuneRenderer.orbit(
                    skill == 4 ? Math.sqrt(halfX * halfX + halfZ * halfZ) + .25 : Math.min(2.8, g.radius * .65),
                    age * 3,
                    skill == 4 ? 8 : 6,
                    skill == 4 ? 1F : .7F,
                    skill == 4 ? .75F : .36F,
                    skill == 4 ? .2F : 1F,
                    (float) (fade * .76),
                    .1);
            }
        }
    }

    /** Rounded AABB perimeter matches the server's shortest distance to the king's rectangular body. */
    private static void domainBoundary(double halfX, double halfZ, double distance, double height) {
        GL11.glLineWidth(1.5F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int corner = 0; corner < 4; corner++) {
            double cx = corner == 0 || corner == 3 ? halfX : -halfX;
            double cz = corner < 2 ? halfZ : -halfZ;
            for (int step = 0; step <= 12; step++) {
                double angle = (corner * Math.PI / 2) + (step / 12D) * Math.PI / 2;
                GL11.glVertex3d(cx + Math.cos(angle) * distance, height, cz + Math.sin(angle) * distance);
            }
        }
        GL11.glEnd();
    }

    /** King chooses world-axis directions on the server; zero is the compatible upward fallback. */
    public static double[] direction(int id) {
        return id == 2 ? new double[] { -1, 0, 0 }
            : id == 3 ? new double[] { 1, 0, 0 }
                : id == 4 ? new double[] { 0, 0, -1 } : id == 5 ? new double[] { 0, 0, 1 } : new double[] { 0, 1, 0 };
    }

    public static double meteorHeight(double tick) {
        double progress = Math.max(0, Math.min(1, (tick - 20) / 100D));
        return 32 * (1 - progress * progress);
    }

    private static void meteor(double tick, double age) {
        if (tick < 0) return;
        double y = meteorHeight(tick) + 1.5;
        try (GlScope core = new GlScope()) {
            GL11.glTranslated(0, y, 0);
            GL11.glRotated(tick * 1.4, 1, .3, 1);
            FineRuneRenderer.voxel(0, 0, 0, 1.45, .77F, .4F, .95F, .8F, true);
            for (int i = 0; i < 7; i++) {
                double a = i * 2.399963;
                FineRuneRenderer
                    .voxel(Math.cos(a) * 1.4, Math.sin(a * 2) * .7, Math.sin(a) * 1.4, .18, 1F, .72F, .3F, .66F, true);
            }
        }
        try (GlScope orbit = new GlScope()) {
            GL11.glTranslated(0, y, 0);
            FineRuneRenderer.orbit(2.1, tick * 2, 8, 1F, .7F, .25F, .78F, .1);
        }
        for (int i = 0; i < 4; i++)
            FineRuneRenderer.voxel(0, y + 2 + i * 1.2, 0, 1 - i * .16, .65F, .28F, 1F, (4 - i) * .09F, true);
        GL11.glColor4f(.7F, .35F, 1F, .22F);
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            GL11.glVertex3d(Math.cos(a) * .6, .2, Math.sin(a) * .6);
            GL11.glVertex3d(Math.cos(a) * .6, y - 1.5, Math.sin(a) * .6);
        }
        GL11.glEnd();
    }
}
