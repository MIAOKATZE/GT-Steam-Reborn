package com.miaokatze.gtsr.client.architecture;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterBlock;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterBlocks;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Each state renders the exact authored cuboids used by server collision checks. */
@SideOnly(Side.CLIENT)
public final class RemasterBlockRenderer implements ISimpleBlockRenderingHandler {

    public static void registerRenderer() {
        if (RemasterBlocks.renderId != 0) return;
        RemasterBlocks.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new RemasterBlockRenderer());
    }

    @Override
    public int getRenderId() {
        return RemasterBlocks.renderId;
    }

    @Override
    public boolean shouldRender3DInInventory(int id) {
        return true;
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int id,
        RenderBlocks renderer) {
        if (!(block instanceof RemasterBlock remaster)) return false;
        double[] old = bounds(renderer);
        boolean all = renderer.renderAllFaces, lock = renderer.lockBlockBounds;
        try {
            renderer.lockBlockBounds = false;
            renderer.renderAllFaces = true;
            int meta = world.getBlockMetadata(x, y, z);
            if ("cross".equals(remaster.renderShape())) {
                Tessellator.instance.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z));
                Tessellator.instance.setColorOpaque_F(1, 1, 1);
                crossed(block.getIcon(0, meta), x, y, z);
            } else for (double[] part : remaster.boxes(meta)) {
                renderer.setRenderBounds(part[0], part[1], part[2], part[3], part[4], part[5]);
                renderer.renderStandardBlock(block, x, y, z);
            }
            // Unsolved objects have a world-space sign. The chunk renderer keeps normal depth testing;
            // an opaque wall hides it and synchronized completion metadata removes it on rebuild.
            if (remaster.guidanceActive(world, x, y, z)) hint(renderer, block, x, y, z);
            return true;
        } finally {
            renderer.lockBlockBounds = false;
            renderer.setRenderBounds(old[0], old[1], old[2], old[3], old[4], old[5]);
            renderer.lockBlockBounds = lock;
            renderer.renderAllFaces = all;
        }
    }

    private static double[] bounds(RenderBlocks renderer) {
        return new double[] { renderer.renderMinX, renderer.renderMinY, renderer.renderMinZ, renderer.renderMaxX,
            renderer.renderMaxY, renderer.renderMaxZ };
    }

    private static void hint(RenderBlocks renderer, Block block, int x, int y, int z) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.setBrightness(0xF000F0);
        tessellator.setColorOpaque_F(.70F, .94F, .58F);
        IIcon icon = Blocks.wool.getIcon(0, 0);
        double near = block.renderAsNormalBlock() ? -.002 : .055;
        double far = block.renderAsNormalBlock() ? 1.002 : .945;
        // Four corner brackets are readable at eye height from each approach. They use the normal
        // chunk depth test and stay at the object's edge; walls still occlude the entire marker.
        double[][] bars = { { .08, .36, .12, .88 }, { .88, .36, .92, .88 }, { .08, .36, .29, .40 },
            { .71, .36, .92, .40 }, { .08, .84, .29, .88 }, { .71, .84, .92, .88 } };
        for (double[] b : bars) {
            renderer.setRenderBounds(b[0], b[1], near, b[2], b[3], far);
            renderer.renderFaceZNeg(block, x, y, z, icon);
            renderer.renderFaceZPos(block, x, y, z, icon);
            renderer.setRenderBounds(near, b[1], b[0], far, b[3], b[2]);
            renderer.renderFaceXNeg(block, x, y, z, icon);
            renderer.renderFaceXPos(block, x, y, z, icon);
        }
        tessellator.setColorOpaque_F(1, 1, 1);
    }

    @Override
    public void renderInventoryBlock(Block block, int meta, int id, RenderBlocks renderer) {
        if (!(block instanceof RemasterBlock remaster)) return;
        double[] old = bounds(renderer);
        boolean lock = renderer.lockBlockBounds;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(-.5F, -.5F, -.5F);
            renderer.lockBlockBounds = false;
            if ("cross".equals(remaster.renderShape())) {
                Tessellator.instance.startDrawingQuads();
                Tessellator.instance.setNormal(0, 1, 0);
                crossed(block.getIcon(0, meta), 0, 0, 0);
                Tessellator.instance.draw();
            } else for (double[] part : remaster.boxes(meta)) {
                renderer.setRenderBounds(part[0], part[1], part[2], part[3], part[4], part[5]);
                for (int side = 0; side < 6; side++) {
                    Tessellator t = Tessellator.instance;
                    t.startDrawingQuads();
                    IIcon icon = block.getIcon(side, meta);
                    switch (side) {
                        case 0:
                            t.setNormal(0, -1, 0);
                            renderer.renderFaceYNeg(block, 0, 0, 0, icon);
                            break;
                        case 1:
                            t.setNormal(0, 1, 0);
                            renderer.renderFaceYPos(block, 0, 0, 0, icon);
                            break;
                        case 2:
                            t.setNormal(0, 0, -1);
                            renderer.renderFaceZNeg(block, 0, 0, 0, icon);
                            break;
                        case 3:
                            t.setNormal(0, 0, 1);
                            renderer.renderFaceZPos(block, 0, 0, 0, icon);
                            break;
                        case 4:
                            t.setNormal(-1, 0, 0);
                            renderer.renderFaceXNeg(block, 0, 0, 0, icon);
                            break;
                        default:
                            t.setNormal(1, 0, 0);
                            renderer.renderFaceXPos(block, 0, 0, 0, icon);
                    }
                    t.draw();
                }
            }
        } finally {
            renderer.lockBlockBounds = false;
            renderer.setRenderBounds(old[0], old[1], old[2], old[3], old[4], old[5]);
            renderer.lockBlockBounds = lock;
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void crossed(IIcon icon, double x, double y, double z) {
        double[][][] quads = { { { 0, 0, 0 }, { 0, 1, 0 }, { 1, 1, 1 }, { 1, 0, 1 } },
            { { 1, 0, 0 }, { 1, 1, 0 }, { 0, 1, 1 }, { 0, 0, 1 } } };
        Tessellator t = Tessellator.instance;
        for (double[][] quad : quads) for (int direction = 0; direction < 2; direction++) {
            for (int step = 0; step < 4; step++) {
                int index = direction == 0 ? step : 3 - step;
                double[] point = quad[index];
                double u = index < 2 ? icon.getMinU() : icon.getMaxU();
                double v = index == 0 || index == 3 ? icon.getMaxV() : icon.getMinV();
                t.addVertexWithUV(x + point[0], y + point[1], z + point[2], u, v);
            }
        }
    }
}
