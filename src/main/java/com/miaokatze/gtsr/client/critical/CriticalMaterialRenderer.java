package com.miaokatze.gtsr.client.critical;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.miaokatze.gtsr.common.critical.CriticalLoomProxyVisibility;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Static materials remain chunk geometry; only emission-mask pixels receive full brightness. */
@SideOnly(Side.CLIENT)
public final class CriticalMaterialRenderer implements ISimpleBlockRenderingHandler {

    public static void register() {
        CriticalAngelicaCompat.register();
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
                if (block instanceof CriticalMaterials.MaterialBlock material
                    && material.materialId.startsWith("v2_")) {
                    int[] normal = CriticalMaterialTextures.NORMALS[side];
                    t.setNormal(normal[0], normal[1], normal[2]);
                    face(t, 0, 0, 0, side, icon, 0);
                    t.draw();
                    continue;
                }
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
        // Hand-placed loom parts stay visible until their canonical anchor owns the moving mesh.
        if (block instanceof CriticalMaterials.MaterialBlock material && material.proxy
            && (!material.loom || CriticalLoomProxyVisibility.animated(
                world instanceof net.minecraft.world.World real ? real
                    : net.minecraft.client.Minecraft.getMinecraft().theWorld,
                x,
                y,
                z,
                block,
                world.getBlockMetadata(x, y, z))))
            return false;
        if (block instanceof CriticalMaterials.MaterialBlock material && material.materialId.startsWith("v2_")) {
            return renderV2(world, x, y, z, material);
        }
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

    private static boolean renderV2(IBlockAccess world, int x, int y, int z, CriticalMaterials.MaterialBlock material) {
        Tessellator tessellator = Tessellator.instance;
        boolean rendered = false;
        for (int side = 0; side < 6; side++) {
            int[] normal = CriticalMaterialTextures.NORMALS[side];
            int nx = x + normal[0], ny = y + normal[1], nz = z + normal[2];
            if (!material.shouldSideBeRendered(world, nx, ny, nz, side)) continue;
            int mask = CriticalMaterialTextures.worldMask(world, x, y, z, material, side);
            IIcon albedo = CriticalMaterialTextures.icon(material.materialId, side, mask, false, false);
            if (albedo == null) continue;
            // Packed native sky/block light for this exposed face, baked when the chunk mesh is rebuilt.
            tessellator.setBrightness(material.getMixedBrightnessForBlock(world, nx, ny, nz));
            float shade = side == 0 ? .5F : side == 1 ? 1 : side < 4 ? .8F : .6F;
            tessellator.setColorOpaque_F(shade, shade, shade);
            face(tessellator, x, y, z, side, albedo, 0);
            IIcon emission = CriticalMaterialTextures.icon(material.materialId, side, mask, true, false);
            if (emission != null) {
                tessellator.setBrightness(0x00f000f0);
                tessellator.setColorOpaque_F(1, 1, 1);
                // Chunk rendering uses LEQUAL: identical vertices share exact depth with the albedo.
                // A tiny world-space offset loses precision at distance and makes the mask peel away.
                face(tessellator, x, y, z, side, emission, 0);
            }
            rendered = true;
        }
        return rendered;
    }

    private static void face(Tessellator tessellator, double x, double y, double z, int side, IIcon icon,
        double offset) {
        int[] normal = CriticalMaterialTextures.NORMALS[side], u = CriticalMaterialTextures.U[side],
            v = CriticalMaterialTextures.V[side];
        double cx = x + .5 + normal[0] * (.5 + offset), cy = y + .5 + normal[1] * (.5 + offset),
            cz = z + .5 + normal[2] * (.5 + offset);
        for (int corner = 0; corner < 4; corner++) {
            int us = corner == 0 || corner == 3 ? -1 : 1, vs = corner < 2 ? -1 : 1;
            tessellator.addVertexWithUV(
                cx + .5 * (us * u[0] + vs * v[0]),
                cy + .5 * (us * u[1] + vs * v[1]),
                cz + .5 * (us * u[2] + vs * v[2]),
                us < 0 ? icon.getMinU() : icon.getMaxU(),
                vs < 0 ? icon.getMinV() : icon.getMaxV());
        }
    }
}
