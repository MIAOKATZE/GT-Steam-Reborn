package com.miaokatze.gtsr.client.encounter;

import net.minecraft.client.model.ModelChest;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;

/** Vanilla single chest with emissive orbiting rune strokes; no inventory is rendered or created. */
public final class SealedChestRenderer extends TileEntitySpecialRenderer {

    private final ModelChest chest = new ModelChest();

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partial) {
        TileEntitySealedChest seal = (TileEntitySealedChest) tile;
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(x, y + 1, z + 1);
            GL11.glScalef(1, -1, -1);
            GL11.glTranslatef(0.5F, 0.5F, 0.5F);
            int metadata = tile.getBlockMetadata();
            GL11.glRotatef(metadata == 2 ? 180 : metadata == 4 ? 90 : metadata == 5 ? -90 : 0, 0, 1, 0);
            GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
            bindTexture(new ResourceLocation("textures/entity/chest/normal.png"));
            chest.chestLid.rotateAngleX = 0;
            chest.renderAll();
        }
        try (GlScope scope = new GlScope()) {
            double opening = seal.getOpeningTicks() < 0 ? 0 : Math.min(1, (seal.getOpeningTicks() + partial) / 60.0);
            double time = tile.hasWorldObj() ? tile.getWorldObj()
                .getTotalWorldTime() + partial : 0;
            GL11.glTranslated(x + 0.5, y + 0.55, z + 0.5);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glColor4d(1, 0.08, 0.12, 0.85 * (1 - opening));
            GL11.glLineWidth(2);
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI / 6 + time * 0.009;
                double radius = 0.76 + opening * 1.5;
                try (GlScope rune = new GlScope()) {
                    GL11.glTranslated(
                        Math.cos(angle) * radius,
                        Math.sin(time * 0.04 + i) * 0.06,
                        Math.sin(angle) * radius);
                    GL11.glRotated(-angle * 180 / Math.PI + 90, 0, 1, 0);
                    GL11.glBegin(GL11.GL_LINES);
                    stroke(-0.08, -0.10, 0.08, 0.10);
                    stroke(0.08, -0.10, -0.08, 0.10);
                    stroke(-0.08, 0.10, 0.08, 0.10);
                    stroke(0, -0.14, 0, 0.14);
                    GL11.glEnd();
                }
            }
        }
    }

    private static void stroke(double x1, double y1, double x2, double y2) {
        GL11.glVertex3d(x1, y1, 0);
        GL11.glVertex3d(x2, y2, 0);
    }
}
