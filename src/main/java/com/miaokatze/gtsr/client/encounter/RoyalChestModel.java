package com.miaokatze.gtsr.client.encounter;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/** Explicit face UVs on one 32x32 atlas; world-unit carved wood, bands and hinged lid. */
final class RoyalChestModel {

    static void render(float opening) {
        box(-.4375, 0, -.4375, .4375, .60, .4375, false);
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(0, .60, .4375);
            GL11.glRotated(opening * 105, 1, 0, 0);
            box(-.4375, 0, -.875, .4375, .275, 0, true);
            // Broad crown clasp stays with the lid.
            box(-.075, -.105, -.904, .075, .135, -.875, true);
        }
    }

    private static void box(double x0, double y0, double z0, double x1, double y1, double z1, boolean lid) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorOpaque_F(1, 1, 1);
        face(
            t,
            new double[][] { { x0, y0, z0 }, { x0, y1, z0 }, { x1, y1, z0 }, { x1, y0, z0 } },
            0,
            0,
            -1,
            16,
            lid ? 12 : 0,
            32,
            lid ? 16 : 12);
        face(
            t,
            new double[][] { { x1, y0, z1 }, { x1, y1, z1 }, { x0, y1, z1 }, { x0, y0, z1 } },
            0,
            0,
            1,
            0,
            12,
            16,
            24);
        face(
            t,
            new double[][] { { x0, y0, z1 }, { x0, y1, z1 }, { x0, y1, z0 }, { x0, y0, z0 } },
            -1,
            0,
            0,
            0,
            12,
            16,
            24);
        face(
            t,
            new double[][] { { x1, y0, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x1, y0, z1 } },
            1,
            0,
            0,
            0,
            12,
            16,
            24);
        if (lid) {
            horizontal(t, x0, y1, z0, x1, z1, 0);
        } else {
            // Open timber cavity, rather than a second decorated lid beneath the hinge.
            double rim = .055, floor = y0 + .045;
            t.setColorOpaque_F(.35F, .30F, .25F);
            horizontal(t, x0 + rim, floor, z0 + rim, x1 - rim, z1 - rim, 24);
            face(
                t,
                new double[][] { { x1 - rim, floor, z0 + rim }, { x1 - rim, y1, z0 + rim }, { x0 + rim, y1, z0 + rim },
                    { x0 + rim, floor, z0 + rim } },
                0,
                0,
                1,
                0,
                12,
                16,
                24);
            face(
                t,
                new double[][] { { x0 + rim, floor, z1 - rim }, { x0 + rim, y1, z1 - rim }, { x1 - rim, y1, z1 - rim },
                    { x1 - rim, floor, z1 - rim } },
                0,
                0,
                -1,
                0,
                12,
                16,
                24);
            face(
                t,
                new double[][] { { x0 + rim, floor, z0 + rim }, { x0 + rim, y1, z0 + rim }, { x0 + rim, y1, z1 - rim },
                    { x0 + rim, floor, z1 - rim } },
                1,
                0,
                0,
                0,
                12,
                16,
                24);
            face(
                t,
                new double[][] { { x1 - rim, floor, z1 - rim }, { x1 - rim, y1, z1 - rim }, { x1 - rim, y1, z0 + rim },
                    { x1 - rim, floor, z0 + rim } },
                -1,
                0,
                0,
                0,
                12,
                16,
                24);
            t.setColorOpaque_F(1, 1, 1);
            horizontal(t, x0, y1, z0, x0 + rim, z1, 24);
            horizontal(t, x1 - rim, y1, z0, x1, z1, 24);
            horizontal(t, x0 + rim, y1, z0, x1 - rim, z0 + rim, 24);
            horizontal(t, x0 + rim, y1, z1 - rim, x1 - rim, z1, 24);
        }
        face(
            t,
            new double[][] { { x0, y0, z1 }, { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 } },
            0,
            -1,
            0,
            0,
            24,
            16,
            32);
        t.draw();
    }

    private static void horizontal(Tessellator t, double x0, double y, double z0, double x1, double z1, int v) {
        face(
            t,
            new double[][] { { x0, y, z0 }, { x0, y, z1 }, { x1, y, z1 }, { x1, y, z0 } },
            0,
            1,
            0,
            0,
            v,
            16,
            v + (v == 0 ? 12 : 8));
    }

    private static void face(Tessellator t, double[][] v, float nx, float ny, float nz, double u0, double v0, double u1,
        double v1) {
        t.setNormal(nx, ny, nz);
        double[][] uv = { { u0, v1 }, { u0, v0 }, { u1, v0 }, { u1, v1 } };
        for (int i = 0; i < 4; i++) t.addVertexWithUV(v[i][0], v[i][1], v[i][2], uv[i][0] / 32, uv[i][1] / 32);
    }
}
