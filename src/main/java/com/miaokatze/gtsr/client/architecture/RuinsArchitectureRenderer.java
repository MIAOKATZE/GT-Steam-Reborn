package com.miaokatze.gtsr.client.architecture;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.architecture.RuinsArchitecture;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class RuinsArchitectureRenderer implements ISimpleBlockRenderingHandler {

    public static void registerRenderer() {
        if (RuinsArchitecture.detailRenderId != 0) return;
        RuinsArchitecture.detailRenderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new RuinsArchitectureRenderer());
    }

    @Override
    public int getRenderId() {
        return RuinsArchitecture.detailRenderId;
    }

    @Override
    public boolean shouldRender3DInInventory(int id) {
        return true;
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess w, int x, int y, int z, Block b, int id, RenderBlocks r) {
        if (!(b instanceof RuinsArchitecture.Detail)) return false;
        double[] old = { r.renderMinX, r.renderMinY, r.renderMinZ, r.renderMaxX, r.renderMaxY, r.renderMaxZ };
        boolean all = r.renderAllFaces, lock = r.lockBlockBounds;
        try {
            r.lockBlockBounds = false;
            r.renderAllFaces = true;
            List<double[]> parts = ((RuinsArchitecture.Detail) b).parts(w, x, y, z, false);
            for (double[] p : parts) {
                r.setRenderBounds(p[0], p[1], p[2], p[3], p[4], p[5]);
                r.renderStandardBlock(b, x, y, z);
            }
            if (((RuinsArchitecture.Detail) b).shape == 2) {
                Tessellator.instance.setBrightness(b.getMixedBrightnessForBlock(w, x, y, z));
                Tessellator.instance.setColorOpaque_F(1, 1, 1);
                gaugeFace(b, w.getBlockMetadata(x, y, z), parts.get(parts.size() - 1), x, y, z);
            }
            return true;
        } finally {
            r.lockBlockBounds = false;
            r.setRenderBounds(old[0], old[1], old[2], old[3], old[4], old[5]);
            r.lockBlockBounds = lock;
            r.renderAllFaces = all;
        }
    }

    @Override
    public void renderInventoryBlock(Block b, int meta, int id, RenderBlocks r) {
        if (!(b instanceof RuinsArchitecture.Detail)) return;
        double[] old = { r.renderMinX, r.renderMinY, r.renderMinZ, r.renderMaxX, r.renderMaxY, r.renderMaxZ };
        boolean lock = r.lockBlockBounds;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(-.5F, -.5F, -.5F);
            r.lockBlockBounds = false;
            List<double[]> parts = ((RuinsArchitecture.Detail) b).parts(null, 0, 0, 0, false);
            for (double[] p : parts) {
                r.setRenderBounds(p[0], p[1], p[2], p[3], p[4], p[5]);
                for (int face = 0; face < 6; face++) {
                    IIcon icon = b.getIcon(face, meta);
                    Tessellator t = Tessellator.instance;
                    t.startDrawingQuads();
                    if (face == 0) {
                        t.setNormal(0, -1, 0);
                        r.renderFaceYNeg(b, 0, 0, 0, icon);
                    }
                    if (face == 1) {
                        t.setNormal(0, 1, 0);
                        r.renderFaceYPos(b, 0, 0, 0, icon);
                    }
                    if (face == 2) {
                        t.setNormal(0, 0, -1);
                        r.renderFaceZNeg(b, 0, 0, 0, icon);
                    }
                    if (face == 3) {
                        t.setNormal(0, 0, 1);
                        r.renderFaceZPos(b, 0, 0, 0, icon);
                    }
                    if (face == 4) {
                        t.setNormal(-1, 0, 0);
                        r.renderFaceXNeg(b, 0, 0, 0, icon);
                    }
                    if (face == 5) {
                        t.setNormal(1, 0, 0);
                        r.renderFaceXPos(b, 0, 0, 0, icon);
                    }
                    t.draw();
                }
            }
            if (((RuinsArchitecture.Detail) b).shape == 2) {
                Tessellator.instance.startDrawingQuads();
                gaugeFace(b, 1, parts.get(parts.size() - 1), 0, 0, 0);
                Tessellator.instance.draw();
            }
        } finally {
            r.lockBlockBounds = false;
            r.setRenderBounds(old[0], old[1], old[2], old[3], old[4], old[5]);
            r.lockBlockBounds = lock;
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    /** Gauge face uses the complete dial image rather than a cropped cuboid UV. */
    private static void gaugeFace(Block b, int face, double[] p, double x, double y, double z) {
        if (face < 0 || face > 5) face = 1;
        IIcon icon = ((RuinsArchitecture.Detail) b).getDialIcon();
        Tessellator t = Tessellator.instance;
        double[][] v;
        double e = .0005;
        if (face == 0) v = new double[][] { { p[0], p[1] - e, p[2] }, { p[3], p[1] - e, p[2] },
            { p[3], p[1] - e, p[5] }, { p[0], p[1] - e, p[5] } };
        else if (face == 1) v = new double[][] { { p[0], p[4] + e, p[5] }, { p[3], p[4] + e, p[5] },
            { p[3], p[4] + e, p[2] }, { p[0], p[4] + e, p[2] } };
        else if (face == 2) v = new double[][] { { p[3], p[1], p[2] - e }, { p[0], p[1], p[2] - e },
            { p[0], p[4], p[2] - e }, { p[3], p[4], p[2] - e } };
        else if (face == 3) v = new double[][] { { p[0], p[1], p[5] + e }, { p[3], p[1], p[5] + e },
            { p[3], p[4], p[5] + e }, { p[0], p[4], p[5] + e } };
        else if (face == 4) v = new double[][] { { p[0] - e, p[1], p[2] }, { p[0] - e, p[1], p[5] },
            { p[0] - e, p[4], p[5] }, { p[0] - e, p[4], p[2] } };
        else v = new double[][] { { p[3] + e, p[1], p[5] }, { p[3] + e, p[1], p[2] }, { p[3] + e, p[4], p[2] },
            { p[3] + e, p[4], p[5] } };
        t.setNormal(
            face == 4 ? -1 : face == 5 ? 1 : 0,
            face == 0 ? -1 : face == 1 ? 1 : 0,
            face == 2 ? -1 : face == 3 ? 1 : 0);
        for (int i = 0; i < 4; i++) t.addVertexWithUV(
            x + v[i][0],
            y + v[i][1],
            z + v[i][2],
            i == 0 || i == 3 ? icon.getMinU() : icon.getMaxU(),
            i < 2 ? icon.getMaxV() : icon.getMinV());
    }

}
