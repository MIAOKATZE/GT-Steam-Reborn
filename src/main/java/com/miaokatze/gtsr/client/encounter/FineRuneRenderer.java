package com.miaokatze.gtsr.client.encounter;

import net.minecraft.util.ResourceLocation;

import com.miaokatze.miaokatzecore.client.fx.RuneRenderer;

/** Existing GTSR API and resource-pack paths remain available. */
public final class FineRuneRenderer {

    private static final RuneRenderer RENDERER = new RuneRenderer(
        new ResourceLocation("gtsr", "textures/fx/rune_glyphs.png"),
        new ResourceLocation("gtsr", "textures/misc/glow_soft.png"));

    private FineRuneRenderer() {}

    public static void unlit() {
        RENDERER.unlit();
    }

    public static void arc(double radius, double width, double height, double time, int layer, double fraction) {
        RENDERER.arc(radius, width, height, time, layer, fraction);
    }

    public static void glyph(int index, double width, double height) {
        RENDERER.glyph(index, width, height);
    }

    public static void orbit(double radius, double time, int count, float r, float g, float b, float alpha,
        double jump) {
        RENDERER.orbit(radius, time, count, r, g, b, alpha, jump);
    }

    public static void voxel(double x, double y, double z, double size, float r, float g, float b, float alpha,
        boolean halo) {
        RENDERER.voxel(x, y, z, size, r, g, b, alpha, halo);
    }

    public static void glow(double x, double y, double z, double size, float r, float g, float b, float alpha) {
        RENDERER.glow(x, y, z, size, r, g, b, alpha);
    }
}
