package com.miaokatze.gtsr.client.encounter;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;

/** Layered world-width calligraphy, phase-driven release and original 32px carved vessel. */
public final class SealedChestRenderer extends TileEntitySpecialRenderer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        "gtsr",
        "textures/entity/royal_chest_sealed.png");
    private static final ResourceLocation RUNES = new ResourceLocation("gtsr", "textures/fx/rune_glyphs.png");

    static void faceDirection(int meta) {
        GL11.glRotatef(meta == 3 ? 180 : meta == 4 ? 90 : meta == 5 ? -90 : 0, 0, 1, 0);
    }

    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partial) {
        TileEntitySealedChest seal = (TileEntitySealedChest) tile;
        double phase = seal.getOpeningTicks() < 0 ? 0 : Math.min(1, (seal.getOpeningTicks() + partial) / 60.0);
        double time = tile.hasWorldObj() ? tile.getWorldObj()
            .getTotalWorldTime() + partial : 0;
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(x + .5, y, z + .5);
            faceDirection(tile.getBlockMetadata());
            bindTexture(TEXTURE);
            GL11.glColor4f(1, 1, 1, 1);
            RoyalChestModel.render((float) Math.max(0, (phase - .85) / .15) * .18F);
        }
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(x + .5, y + .54, z + .5);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDepthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            double alpha = phase < .8 ? .8 : (1 - phase) * 4;
            double amber = Math.min(1, phase * 2), jade = Math.max(0, phase * 2 - 1);
            double red = 1 - jade * .30, green = .13 + amber * .49 + jade * .36, blue = .19 + jade * .67;
            double burst = Math.max(0, (phase - .70) / .30);
            double radius = .77 - Math.sin(Math.min(phase, .70) / .70 * Math.PI / 2) * .26 + burst * 1.45;
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            for (int layer = 0; layer < 3; layer++) {
                try (GlScope ring = new GlScope()) {
                    GL11.glRotated(layer == 0 ? 0 : layer == 1 ? 62 : -53, 1, 0, 0);
                    GL11.glRotated(time * (layer % 2 == 0 ? .65 : -.85) + phase * 210, 0, 1, 0);
                    color(red, green, blue, alpha * .22);
                    ring(radius + layer * .10, .027 + burst * .025, layer * .055, time, layer);
                    color(red, green, blue, alpha * .85);
                    ring(radius + layer * .10, .008, layer * .055, time, layer);
                }
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            bindTexture(RUNES);
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI / 6 + time * .011 + phase * 2.8;
                try (GlScope glyph = new GlScope()) {
                    GL11.glTranslated(
                        Math.cos(angle) * radius,
                        Math.sin(time * .047 + i * 1.7) * .11 + burst * (i % 3 - 1) * .3,
                        Math.sin(angle) * radius);
                    GL11.glRotated(90 - angle * 180 / Math.PI, 0, 1, 0);
                    GL11.glRotated(Math.sin(time * .028 + i) * 10 + burst * 100, 0, 0, 1);
                    double breath = .72 + .28 * Math.sin(time * .07 + i);
                    color(red, green, blue, alpha * breath * .30);
                    glyph(i % 8, .215, .28);
                    color(1, green, blue, alpha * breath);
                    glyph(i % 8, .15, .21);
                }
            }
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            color(1, Math.min(1, green + .2), Math.min(1, blue + .2), alpha);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            for (int i = 0; i < 24; i++) {
                double a = i * 2.399 + time * .025;
                double r = radius + .15 + .12 * Math.sin(time * .055 + i);
                double px = Math.cos(a) * r, pz = Math.sin(a) * r;
                double py = .24 * Math.sin(i + time * .04) + burst * .6;
                double size = .012 + .008 * Math.sin(i + time * .1);
                t.addVertex(px - size, py, pz);
                t.addVertex(px, py + size * 2, pz);
                t.addVertex(px + size, py, pz);
                t.addVertex(px, py - size * 2, pz);
            }
            t.draw();
        }
    }

    private static void color(double r, double g, double b, double a) {
        GL11.glColor4d(r, g, b, a);
    }

    private static void ring(double radius, double width, double height, double time, int layer) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int i = 0; i < 96; i++) {
            if ((i + layer * 7) % 24 >= 20) continue;
            double a = i * Math.PI / 48, b = (i + 1) * Math.PI / 48;
            double waveA = .018 * Math.sin(a * 5 + time * .05), waveB = .018 * Math.sin(b * 5 + time * .05);
            t.addVertex(Math.cos(a) * (radius - width), height + waveA, Math.sin(a) * (radius - width));
            t.addVertex(Math.cos(a) * (radius + width), height + waveA, Math.sin(a) * (radius + width));
            t.addVertex(Math.cos(b) * (radius + width), height + waveB, Math.sin(b) * (radius + width));
            t.addVertex(Math.cos(b) * (radius - width), height + waveB, Math.sin(b) * (radius - width));
        }
        t.draw();
    }

    private static void glyph(int index, double w, double h) {
        double u = index / 8.0;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(-w, -h, 0, u, 1);
        t.addVertexWithUV(w, -h, 0, u + .125, 1);
        t.addVertexWithUV(w, h, 0, u + .125, 0);
        t.addVertexWithUV(-w, h, 0, u, 0);
        t.draw();
    }
}
