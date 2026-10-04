package com.miaokatze.gtsr.client.encounter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/** Shared chest-style arc calligraphy and translucent voxel light, always inside a caller's GlScope. */
public final class FineRuneRenderer {

    private static final ResourceLocation GLYPHS = new ResourceLocation("gtsr", "textures/fx/rune_glyphs.png");
    private static final ResourceLocation GLOW = new ResourceLocation("gtsr", "textures/misc/glow_soft.png");

    private FineRuneRenderer() {}

    public static void unlit() {
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void arc(double radius, double width, double height, double time, int layer, double fraction) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        int segments = 72;
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < segments * Math.max(0, Math.min(1, fraction)); i++) {
            if ((i + layer * 5) % 18 >= 15) continue;
            double a = i * Math.PI * 2 / segments, b = (i + 1) * Math.PI * 2 / segments;
            double ya = height + .012 * Math.sin(a * 5 + time * .04), yb = height + .012 * Math.sin(b * 5 + time * .04);
            GL11.glVertex3d(Math.cos(a) * (radius - width), ya, Math.sin(a) * (radius - width));
            GL11.glVertex3d(Math.cos(a) * (radius + width), ya, Math.sin(a) * (radius + width));
            GL11.glVertex3d(Math.cos(b) * (radius + width), yb, Math.sin(b) * (radius + width));
            GL11.glVertex3d(Math.cos(b) * (radius - width), yb, Math.sin(b) * (radius - width));
        }
        GL11.glEnd();
    }

    public static void glyph(int index, double width, double height) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(GLYPHS);
        double u = (index & 7) / 8D;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(u, 1);
        GL11.glVertex3d(-width, -height, 0);
        GL11.glTexCoord2d(u + .125, 1);
        GL11.glVertex3d(width, -height, 0);
        GL11.glTexCoord2d(u + .125, 0);
        GL11.glVertex3d(width, height, 0);
        GL11.glTexCoord2d(u, 0);
        GL11.glVertex3d(-width, height, 0);
        GL11.glEnd();
    }

    public static void orbit(double radius, double time, int count, float r, float g, float b, float alpha,
        double jump) {
        unlit();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        for (int layer = 0; layer < 2; layer++) {
            try (GlScope ring = new GlScope()) {
                GL11.glRotated(layer == 0 ? 12 : -46, 1, 0, 0);
                GL11.glRotated(time * (layer == 0 ? .65 : -.85), 0, 1, 0);
                GL11.glColor4f(r, g, b, alpha * .14F);
                arc(radius + layer * .055, .025, 0, time, layer, 1);
                GL11.glColor4f(r, g, b, alpha * .65F);
                arc(radius + layer * .055, .006, 0, time, layer, 1);
            }
        }
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / Math.max(1, count) + time * .011;
            try (GlScope glyph = new GlScope()) {
                GL11.glTranslated(
                    Math.cos(angle) * radius,
                    Math.sin(time * .07 + i * 1.7) * (.045 + jump * .08),
                    Math.sin(angle) * radius);
                GL11.glRotated(90 - angle * 180 / Math.PI, 0, 1, 0);
                GL11.glRotated(Math.sin(time * .025 + i) * 7, 0, 0, 1);
                float breath = (float) (.78 + .22 * Math.sin(time * .075 + i));
                GL11.glColor4f(r, g, b, alpha * breath * .21F);
                glyph(i, .15, .2);
                GL11.glColor4f(r, g, b, alpha * breath);
                glyph(i, .1, .145);
            }
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** A soft emissive surround, three transparent layers and a fine cube edge, never an opaque white block. */
    public static void voxel(double x, double y, double z, double size, float r, float g, float b, float alpha,
        boolean halo) {
        if (alpha <= 0 || size <= 0) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, alpha * .16F);
        CubeRuneParticle.box(x, y, z, size * 1.2);
        GL11.glColor4f(r, g, b, alpha * .42F);
        CubeRuneParticle.box(x, y, z, size * .72);
        GL11.glColor4f(Math.min(1, r + .12F), Math.min(1, g + .12F), Math.min(1, b + .12F), alpha * .7F);
        edges(x, y, z, size * .72);
        if (halo) glow(x, y, z, size * 2.5, r, g, b, alpha * .19F);
    }

    public static void glow(double x, double y, double z, double size, float r, float g, float b, float alpha) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(GLOW);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glColor4f(r, g, b, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        for (int plane = 0; plane < 3; plane++) for (int corner = 0; corner < 4; corner++) {
            double a = (corner == 0 || corner == 3) ? -size : size, vertical = corner < 2 ? -size : size;
            GL11.glTexCoord2d(corner == 0 || corner == 3 ? 0 : 1, corner < 2 ? 1 : 0);
            GL11.glVertex3d(
                x + (plane == 1 ? 0 : a),
                y + (plane == 2 ? 0 : plane == 1 ? a : vertical),
                z + (plane == 0 ? 0 : vertical));
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void edges(double x, double y, double z, double size) {
        GL11.glLineWidth(1F);
        GL11.glBegin(GL11.GL_LINES);
        for (int axis = 0; axis < 3; axis++) for (int a = -1; a <= 1; a += 2) for (int b = -1; b <= 1; b += 2) {
            GL11.glVertex3d(
                x + (axis == 0 ? -size : a * size),
                y + (axis == 1 ? -size : axis == 0 ? a * size : b * size),
                z + (axis == 2 ? -size : b * size));
            GL11.glVertex3d(
                x + (axis == 0 ? size : a * size),
                y + (axis == 1 ? size : axis == 0 ? a * size : b * size),
                z + (axis == 2 ? size : b * size));
        }
        GL11.glEnd();
    }
}
