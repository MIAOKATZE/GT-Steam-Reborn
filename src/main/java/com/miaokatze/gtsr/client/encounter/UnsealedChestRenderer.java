package com.miaokatze.gtsr.client.encounter;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntityUnsealedChest;

public final class UnsealedChestRenderer extends TileEntitySpecialRenderer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        "gtsr",
        "textures/entity/royal_chest_unsealed.png");

    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partial) {
        TileEntityUnsealedChest chest = (TileEntityUnsealedChest) tile;
        float angle = chest.prevLidAngle + (chest.lidAngle - chest.prevLidAngle) * partial;
        angle = 1 - (float) Math.pow(1 - angle, 3);
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(x + .5, y, z + .5);
            SealedChestRenderer.faceDirection(tile.getBlockMetadata());
            bindTexture(TEXTURE);
            GL11.glColor4f(1, 1, 1, 1);
            RoyalChestModel.render(angle);
        }
    }
}
