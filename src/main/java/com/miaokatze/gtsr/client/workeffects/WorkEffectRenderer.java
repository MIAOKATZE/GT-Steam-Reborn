package com.miaokatze.gtsr.client.workeffects;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
    private static final String FOREGROUND_SHA = "bf46e1b766edd4562d85371d875c5606953d2eb90a02c3ead9e4f2b8968078f9";
    private static final String BRONZE_SHA = "9da8c4276feb8d1acc4e2a20be0cd4101da04f6faaf63c4675b56a9976e081cf";
    private static final String STEEL_SHA = "37a904e7c4f6a78c35a70e003557f332b499cf000b282d59841fca19ff799016";
    private static final int[][] NORMALS = { { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };
    private static boolean bronzeCompatible;
    private static boolean steelCompatible;
    private static boolean foregroundAvailable;
    private static long renderedFrames;
    private static long renderedFaces;

    public static void listen() {
        ((IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager()).registerReloadListener(new WorkEffectRenderer());
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        bronzeCompatible = matches(manager, "MACHINE_CASING_FIREBOX_BRONZE", BRONZE_SHA);
        steelCompatible = matches(manager, "MACHINE_CASING_FIREBOX_STEEL", STEEL_SHA);
        foregroundAvailable = matches(manager, FOREGROUND, FOREGROUND_SHA);
    }

    private static boolean matches(IResourceManager manager, String name, String expected) {
        return matches(
            manager,
            new ResourceLocation("gregtech", "textures/blocks/iconsets/" + name + ".png"),
            expected);
    }

    private static boolean matches(IResourceManager manager, ResourceLocation location, String expected) {
        try (InputStream stream = manager.getResource(location)
            .getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024];
            for (int size; (size = stream.read(buffer)) != -1;) digest.update(buffer, 0, size);
            StringBuilder hash = new StringBuilder(64);
            for (byte value : digest.digest()) hash.append(String.format("%02x", value & 255));
            return hash.toString()
                .equals(expected);
        } catch (IOException missing) {
            return false;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
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
                if (meta == 13 ? steelOnly || !bronzeCompatible : meta != 14 || !steelCompatible) continue;
                for (int side = 2; side < 6; side++) {
                    int[] normal = NORMALS[side - 2];
                    int nx = x + normal[0], ny = y + normal[1], nz = z + normal[2];
                    if (world.blockExists(nx, ny, nz) && block.shouldSideBeRendered(world, nx, ny, nz, side)) {
                        tess.setNormal(normal[0], normal[1], normal[2]);
                        face(tess, offset.get0(), offset.get1(), offset.get2(), side);
                        renderedFaces++;
                    }
                }
            }
            tess.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void face(Tessellator t, double x, double y, double z, int side) {
        switch (side) {
            case 2 -> {
                t.addVertexWithUV(x + 1, y, z, 0, 1);
                t.addVertexWithUV(x, y, z, 1, 1);
                t.addVertexWithUV(x, y + 1, z, 1, 0);
                t.addVertexWithUV(x + 1, y + 1, z, 0, 0);
            }
            case 3 -> {
                t.addVertexWithUV(x, y, z + 1, 0, 1);
                t.addVertexWithUV(x + 1, y, z + 1, 1, 1);
                t.addVertexWithUV(x + 1, y + 1, z + 1, 1, 0);
                t.addVertexWithUV(x, y + 1, z + 1, 0, 0);
            }
            case 4 -> {
                t.addVertexWithUV(x, y, z, 0, 1);
                t.addVertexWithUV(x, y, z + 1, 1, 1);
                t.addVertexWithUV(x, y + 1, z + 1, 1, 0);
                t.addVertexWithUV(x, y + 1, z, 0, 0);
            }
            default -> {
                t.addVertexWithUV(x + 1, y, z + 1, 0, 1);
                t.addVertexWithUV(x + 1, y, z, 1, 1);
                t.addVertexWithUV(x + 1, y + 1, z, 1, 0);
                t.addVertexWithUV(x + 1, y + 1, z + 1, 0, 0);
            }
        }
    }

    public static String diagnostics() {
        return "foreground=" + foregroundAvailable
            + ",bronze="
            + bronzeCompatible
            + ",steel="
            + steelCompatible
            + ",frames="
            + renderedFrames
            + ",faces="
            + renderedFaces;
    }
}
