package com.miaokatze.gtsr.client.critical;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.miaokatze.gtsr.common.critical.CriticalMaterials;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Static materials remain chunk geometry; only emission-mask pixels receive full brightness. */
@SideOnly(Side.CLIENT)
public final class CriticalMaterialRenderer implements ISimpleBlockRenderingHandler {

    public static void register() {
        CriticalMaterials.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new CriticalMaterialRenderer());
    }

    @Override
    public int getRenderId() {
        return CriticalMaterials.renderId;
    }

    @Override
    public boolean shouldRender3DInInventory(int id) {
        return true;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int id, RenderBlocks renderer) {
        org.lwjgl.opengl.GL11.glPushMatrix();
        org.lwjgl.opengl.GL11.glTranslatef(-.5F, -.5F, -.5F);
        try {
            Tessellator t = Tessellator.instance;
            for (int side = 0; side < 6; side++) {
                t.startDrawingQuads();
                IIcon icon = block.getIcon(side, metadata);
                switch (side) {
                    case 0 -> {
                        t.setNormal(0, -1, 0);
                        renderer.renderFaceYNeg(block, 0, 0, 0, icon);
                    }
                    case 1 -> {
                        t.setNormal(0, 1, 0);
                        renderer.renderFaceYPos(block, 0, 0, 0, icon);
                    }
                    case 2 -> {
                        t.setNormal(0, 0, -1);
                        renderer.renderFaceZNeg(block, 0, 0, 0, icon);
                    }
                    case 3 -> {
                        t.setNormal(0, 0, 1);
                        renderer.renderFaceZPos(block, 0, 0, 0, icon);
                    }
                    case 4 -> {
                        t.setNormal(-1, 0, 0);
                        renderer.renderFaceXNeg(block, 0, 0, 0, icon);
                    }
                    default -> {
                        t.setNormal(1, 0, 0);
                        renderer.renderFaceXPos(block, 0, 0, 0, icon);
                    }
                }
                t.draw();
            }
        } finally {
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int id,
        RenderBlocks renderer) {
        boolean rendered = renderer.renderStandardBlock(block, x, y, z);
        if (!(block instanceof CriticalMaterials.MaterialBlock material) || !material.emissive
            || material.emission == null) return rendered;
        Tessellator tessellator = Tessellator.instance;
        tessellator.setBrightness(0x00f000f0);
        tessellator.setColorOpaque_F(1, 1, 1);
        double[] old = { renderer.renderMinX, renderer.renderMinY, renderer.renderMinZ, renderer.renderMaxX,
            renderer.renderMaxY, renderer.renderMaxZ };
        renderer.setRenderBounds(-.0005, -.0005, -.0005, 1.0005, 1.0005, 1.0005);
        IIcon icon = material.emission;
        try {
            if (block.shouldSideBeRendered(world, x, y, z - 1, 2)) renderer.renderFaceZNeg(block, x, y, z, icon);
            if (block.shouldSideBeRendered(world, x, y, z + 1, 3)) renderer.renderFaceZPos(block, x, y, z, icon);
            if (block.shouldSideBeRendered(world, x - 1, y, z, 4)) renderer.renderFaceXNeg(block, x, y, z, icon);
            if (block.shouldSideBeRendered(world, x + 1, y, z, 5)) renderer.renderFaceXPos(block, x, y, z, icon);
        } finally {
            renderer.setRenderBounds(old[0], old[1], old[2], old[3], old[4], old[5]);
        }
        return rendered;
    }
}
