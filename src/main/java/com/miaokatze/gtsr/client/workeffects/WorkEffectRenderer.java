// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.workeffects;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.miaokatze.gtsr.common.machine.MTEReinforcedBrickBlastFurnace;
import com.miaokatze.gtsr.common.machine.MTESiemensMartinFurnace;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualHost;
import com.miaokatze.gtsr.common.workeffects.WorkEffectDefinition;
import com.miaokatze.gtsr.common.workeffects.WorkEffectDefinition.Cell;
import com.miaokatze.gtsr.common.workeffects.WorkEffectDefinitions;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Separate fullbright grate pixels over the unchanged GT firebox. No top/bottom, solid trim or world light is changed.
 */
@SideOnly(Side.CLIENT)
public final class WorkEffectRenderer implements IResourceManagerReloadListener {

    private static final ResourceLocation FOREGROUND = new ResourceLocation("gtsr", "workeffects/firebox_grate.png");
    private static final int[][] NORMALS = { { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };
    private static List<double[]> bronzeOpenings = Collections.emptyList();
    private static List<double[]> steelOpenings = Collections.emptyList();
    private static boolean foregroundAvailable;
    private static long renderedFrames;
    private static long renderedFaces;

    public static void listen() {
        ((IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager()).registerReloadListener(new WorkEffectRenderer());
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        BufferedImage foreground = image(manager, FOREGROUND);
        bronzeOpenings = openings(manager, "MACHINE_CASING_FIREBOX_BRONZE", foreground);
        steelOpenings = openings(manager, "MACHINE_CASING_FIREBOX_STEEL", foreground);
        foregroundAvailable = true;
    }

    private static List<double[]> openings(IResourceManager manager, String name, BufferedImage foreground) {
        return FireboxGrateMask.openings(
            image(manager, new ResourceLocation("gregtech", "textures/blocks/iconsets/" + name + ".png")),
            foreground);
    }

    private static BufferedImage image(IResourceManager manager, ResourceLocation location) {
        try (InputStream stream = manager.getResource(location)
            .getInputStream()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) throw new IllegalStateException("Invalid firebox image: " + location);
            return image;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load firebox image: " + location, failure);
        }
    }

    /** Caller supplies a fresh authoritative working snapshot and an unrotated controller-local GL origin. */
    public static void render(MachineVisualHost host, boolean working) {
        if (!working || !foregroundAvailable) return;
        WorkEffectDefinition definition = WorkEffectDefinitions.forHost(host);
        if (definition == null || definition.fireboxes()
            .isEmpty()) return;
        IGregTechTileEntity base = host.getBaseMetaTileEntity();
        World world = base.getWorld();
        if (!CloudParticleQueue.isLive(host, world)) return;
        Block block = GregTechAPI.sBlockCasings3;
        boolean steelOnly = host instanceof MTESiemensMartinFurnace || host instanceof MTEReinforcedBrickBlastFurnace;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(FOREGROUND);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .01F);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1F, -1F);
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.setBrightness(0x00f000f0);
            tess.setColorOpaque_F(1F, 1F, 1F);
            renderedFrames++;
            for (Cell cell : definition.fireboxes()) {
                Vec3Impl offset = host.getExtendedFacing()
                    .getWorldOffset(new Vec3Impl(cell.a(), cell.b(), cell.c()));
                int x = base.getXCoord() + offset.get0();
                int y = base.getYCoord() + offset.get1();
                int z = base.getZCoord() + offset.get2();
                if (!world.blockExists(x, y, z) || world.getBlock(x, y, z) != block) continue;
                int meta = world.getBlockMetadata(x, y, z);
                if (meta == 13 ? steelOnly : meta != 14) continue;
                List<double[]> openings = meta == 13 ? bronzeOpenings : steelOpenings;
                if (openings.isEmpty()) continue;
                for (int side = 2; side < 6; side++) {
                    int[] normal = NORMALS[side - 2];
                    int nx = x + normal[0], ny = y + normal[1], nz = z + normal[2];
                    if (world.blockExists(nx, ny, nz) && block.shouldSideBeRendered(world, nx, ny, nz, side)) {
                        tess.setNormal(normal[0], normal[1], normal[2]);
                        for (double[] opening : openings) {
                            face(tess, offset.get0(), offset.get1(), offset.get2(), side, opening);
                        }
                        renderedFaces++;
                    }
                }
            }
            tess.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void face(Tessellator t, double x, double y, double z, int side, double[] opening) {
        double u0 = opening[0], v0 = opening[1], u1 = opening[2], v1 = opening[3];
        switch (side) {
            case 2 -> {
                t.addVertexWithUV(x + 1 - u0, y + 1 - v1, z, u0, v1);
                t.addVertexWithUV(x + 1 - u1, y + 1 - v1, z, u1, v1);
                t.addVertexWithUV(x + 1 - u1, y + 1 - v0, z, u1, v0);
                t.addVertexWithUV(x + 1 - u0, y + 1 - v0, z, u0, v0);
            }
            case 3 -> {
                t.addVertexWithUV(x + u0, y + 1 - v1, z + 1, u0, v1);
                t.addVertexWithUV(x + u1, y + 1 - v1, z + 1, u1, v1);
                t.addVertexWithUV(x + u1, y + 1 - v0, z + 1, u1, v0);
                t.addVertexWithUV(x + u0, y + 1 - v0, z + 1, u0, v0);
            }
            case 4 -> {
                t.addVertexWithUV(x, y + 1 - v1, z + u0, u0, v1);
                t.addVertexWithUV(x, y + 1 - v1, z + u1, u1, v1);
                t.addVertexWithUV(x, y + 1 - v0, z + u1, u1, v0);
                t.addVertexWithUV(x, y + 1 - v0, z + u0, u0, v0);
            }
            default -> {
                t.addVertexWithUV(x + 1, y + 1 - v1, z + 1 - u0, u0, v1);
                t.addVertexWithUV(x + 1, y + 1 - v1, z + 1 - u1, u1, v1);
                t.addVertexWithUV(x + 1, y + 1 - v0, z + 1 - u1, u1, v0);
                t.addVertexWithUV(x + 1, y + 1 - v0, z + 1 - u0, u0, v0);
            }
        }
    }

    public static String diagnostics() {
        return "foreground=" + foregroundAvailable
            + ",bronze="
            + !bronzeOpenings.isEmpty()
            + ",steel="
            + !steelOpenings.isEmpty()
            + ",bronzeOpenings="
            + bronzeOpenings.size()
            + ",steelOpenings="
            + steelOpenings.size()
            + ",frames="
            + renderedFrames
            + ",faces="
            + renderedFaces;
    }
}
