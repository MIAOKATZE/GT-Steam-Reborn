// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.machinevisual;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.miaokatze.gtsr.client.render.ClientMesh;
import com.miaokatze.gtsr.client.render.ClientMeshCache;
import com.miaokatze.gtsr.common.critical.CriticalStructureAlignment;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure;
import com.miaokatze.gtsr.common.machinevisual.VirtualStructureFrame;

/** Installed packages are template instances, never world blocks. Only visible cache misses compile geometry. */
final class CenterModuleRenderer implements AutoCloseable {

    private static final ClientMeshCache<String> CACHE = new ClientMeshCache<>();
    private static final Map<String, int[]> TEMPLATE_BOUNDS = new HashMap<>();
    private static int compilesRemaining;
    private final Part[] parts = new Part[36];
    private final java.util.List<Part> ordered = new java.util.ArrayList<>();
    private final int x, y, z;
    private final ForgeDirection facing;

    CenterModuleRenderer(VirtualStructureFrame frame) {
        x = frame.x;
        y = frame.y;
        z = frame.z;
        facing = ForgeDirection.getOrientation(frame.visual.getInteger("facing"));
        int[] types = frame.visual.getIntArray("types"), grades = frame.visual.getIntArray("grades");
        for (int i = 0; i < 36; i++) if (grades[i] > 0) {
            parts[i] = new Part(i, types[i], grades[i]);
            ordered.add(parts[i]);
        }
    }

    static void beginFrame() {
        compilesRemaining = 1;
    }

    static void clearCache() {
        CACHE.clear();
    }

    void render(World world, ICamera camera, double cx, double cy, double cz, int pass) {
        if (pass == 1) ordered.sort(
            java.util.Comparator.comparingDouble((Part part) -> part.distanceSquared(cx, cy, cz))
                .reversed());
        for (Part part : ordered) {
            if (!camera.isBoundingBoxInFrustum(part.bounds)) continue;
            double dx = (part.bounds.minX + part.bounds.maxX) * .5 - cx;
            double dy = (part.bounds.minY + part.bounds.maxY) * .5 - cy;
            double dz = (part.bounds.minZ + part.bounds.maxZ) * .5 - cz;
            if (dx * dx + dy * dy + dz * dz > 512D * 512D) continue;
            for (int emission = 0; emission < 2; emission++) {
                int layer = pass * 2 + emission;
                if (part.meshes[layer] == null) {
                    String key = part.key + ':' + layer;
                    if (!CACHE.contains(key)) {
                        if (compilesRemaining == 0 || !com.miaokatze.gtsr.client.render.ClientRenderFrame.tryBuild())
                            continue;
                        compilesRemaining--;
                    }
                    final boolean emissive = emission != 0;
                    part.meshes[layer] = CACHE.acquire(key, () -> compile(part.template, pass, emissive));
                }
                GL11.glPushMatrix();
                try {
                    GL11.glTranslated(x - cx, y - cy, z - cz);
                    part.transform.rewind();
                    GL11.glMultMatrix(part.transform);
                    int px = (int) ((part.bounds.minX + part.bounds.maxX) * .5);
                    int pz = (int) ((part.bounds.minZ + part.bounds.maxZ) * .5);
                    int light = world.getChunkProvider()
                        .chunkExists(px >> 4, pz >> 4)
                            ? world.getLightBrightnessForSkyBlocks(px, Math.max(0, Math.min(255, y + 4)), pz, 0)
                            : 0x00f000f0;
                    if (emission != 0) {
                        GL11.glDisable(GL11.GL_LIGHTING);
                        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
                    } else {
                        GL11.glEnable(GL11.GL_LIGHTING);
                        OpenGlHelper
                            .setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 65535, light >>> 16);
                    }
                    part.meshes[layer].draw();
                } finally {
                    GL11.glPopMatrix();
                }
            }
        }
    }

    private static ClientMesh compile(CenterStructure.ModuleTemplate template, int pass, boolean emission) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            return ClientMesh.capture(DefaultVertexFormat.POSITION_TEXTURE_COLOR_NORMAL, () -> {
                FaceRenderer renderer = new FaceRenderer();
                renderer.enableAO = false;
                Map<Long, Integer> occupied = new HashMap<>();
                for (int i = 0; i < template.count(); i++)
                    occupied.put(cell(template.x(i), template.y(i), template.z(i)), i);
                Tessellator tess = Tessellator.instance;
                try {
                    for (int i = 0; i < template.count(); i++) {
                        Block block = template.block(i);
                        if (block == Blocks.air || !block.canRenderInPass(pass)
                            || (block.getLightValue() > 0) != emission) continue;
                        int vx = template.x(i), vy = template.y(i), vz = template.z(i);
                        renderer.visible = 0;
                        for (int side = 0; side < 6; side++) {
                            ForgeDirection dir = ForgeDirection.getOrientation(side);
                            Integer other = occupied.get(cell(vx + dir.offsetX, vy + dir.offsetY, vz + dir.offsetZ));
                            boolean hidden = other != null && (template.block(other)
                                .isOpaqueCube()
                                || template.block(other) == block && template.meta(other) == template.meta(i));
                            if (!hidden) renderer.visible |= 1 << side;
                        }
                        if (renderer.visible == 0) continue;
                        tess.setTranslation(vx, vy, vz);
                        int color = block.getRenderColor(template.meta(i));
                        tess.setColorOpaque_F(
                            ((color >> 16) & 255) / 255F,
                            ((color >> 8) & 255) / 255F,
                            (color & 255) / 255F);
                        // Native item renderers can rotate MODELVIEW. Capture stores vertices, not matrix commands.
                        GL11.glPushMatrix();
                        try {
                            renderer.renderBlockAsItem(block, template.meta(i), 1F);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
                } finally {
                    tess.setTranslation(0, 0, 0);
                }
            });
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static long cell(int x, int y, int z) {
        return ((x & 65535L) << 32) | ((y & 65535L) << 16) | (z & 65535L);
    }

    private final class Part {

        final String key;
        final CenterStructure.ModuleTemplate template;
        final CenterStructure.ModulePlacement placement;
        final AxisAlignedBB bounds;
        final java.nio.DoubleBuffer transform;
        final ClientMeshCache<String>.Lease[] meshes;

        @SuppressWarnings("unchecked")
        Part(int slot, int type, int grade) {
            key = slot % 6 + ":" + type + ':' + grade;
            template = CenterStructure.moduleTemplate(slot, type, grade);
            if (template == null) throw new IllegalArgumentException("Missing installed module template " + key);
            placement = CenterStructure.modulePlacement(slot);
            meshes = (ClientMeshCache<String>.Lease[]) new ClientMeshCache<?>.Lease[4];
            int[] box = TEMPLATE_BOUNDS.computeIfAbsent(key, ignored -> {
                int[] result = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE,
                    Integer.MIN_VALUE, Integer.MIN_VALUE };
                for (int i = 0; i < template.count(); i++) {
                    result[0] = Math.min(result[0], template.x(i));
                    result[1] = Math.min(result[1], template.y(i));
                    result[2] = Math.min(result[2], template.z(i));
                    result[3] = Math.max(result[3], template.x(i) + 1);
                    result[4] = Math.max(result[4], template.y(i) + 1);
                    result[5] = Math.max(result[5], template.z(i) + 1);
                }
                return result;
            });
            // The server rotates block coordinates, not cube corners. Rotate about the local cell centre
            // so every rendered cube occupies exactly the former server cell for all four facings.
            int turns = placement.quarterTurns();
            int ax = turns == 1 || turns == 3 ? 0 : turns == 2 ? -1 : 1;
            int az = turns == 1 ? 1 : turns == 3 ? -1 : 0;
            double xx = CriticalStructureAlignment.rotateX(ax, az, facing);
            double xz = CriticalStructureAlignment.rotateZ(ax, az, facing);
            double zx = CriticalStructureAlignment.rotateX(-az, ax, facing);
            double zz = CriticalStructureAlignment.rotateZ(-az, ax, facing);
            double tx = CriticalStructureAlignment.rotateX(placement.offsetX(), placement.offsetZ(), facing) + .5
                - .5 * (xx + zx);
            double tz = CriticalStructureAlignment.rotateZ(placement.offsetX(), placement.offsetZ(), facing) + .5
                - .5 * (xz + zz);
            double ty = placement.offsetY();
            transform = org.lwjgl.BufferUtils.createDoubleBuffer(16);
            transform.put(new double[] { xx, 0, xz, 0, 0, 1, 0, 0, zx, 0, zz, 0, tx, ty, tz, 1 })
                .flip();
            double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
            double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            for (int corner = 0; corner < 8; corner++) {
                double vx = box[(corner & 1) == 0 ? 0 : 3];
                double vy = box[(corner & 2) == 0 ? 1 : 4];
                double vz = box[(corner & 4) == 0 ? 2 : 5];
                double wx = x + tx + xx * vx + zx * vz, wz = z + tz + xz * vx + zz * vz, wy = y + ty + vy;
                minX = Math.min(minX, wx);
                maxX = Math.max(maxX, wx);
                minY = Math.min(minY, wy);
                maxY = Math.max(maxY, wy);
                minZ = Math.min(minZ, wz);
                maxZ = Math.max(maxZ, wz);
            }
            bounds = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        }

        double distanceSquared(double cx, double cy, double cz) {
            double dx = (bounds.minX + bounds.maxX) * .5 - cx;
            double dy = (bounds.minY + bounds.maxY) * .5 - cy;
            double dz = (bounds.minZ + bounds.maxZ) * .5 - cz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    @Override
    public void close() {
        for (Part part : parts)
            if (part != null) for (ClientMeshCache<String>.Lease mesh : part.meshes) if (mesh != null) mesh.close();
    }

    /** Same face suppression used by GT's StructureVBO, applied to our existing sparse module template. */
    private static final class FaceRenderer extends RenderBlocks {

        int visible;

        FaceRenderer() {
            super(Minecraft.getMinecraft().theWorld);
        }

        @Override
        public void renderFaceYNeg(Block b, double x, double y, double z, IIcon i) {
            if ((visible & 1) != 0) super.renderFaceYNeg(b, 0, 0, 0, i);
        }

        @Override
        public void renderFaceYPos(Block b, double x, double y, double z, IIcon i) {
            if ((visible & 2) != 0) super.renderFaceYPos(b, 0, 0, 0, i);
        }

        @Override
        public void renderFaceZNeg(Block b, double x, double y, double z, IIcon i) {
            if ((visible & 4) != 0) super.renderFaceZNeg(b, 0, 0, 0, i);
        }

        @Override
        public void renderFaceZPos(Block b, double x, double y, double z, IIcon i) {
            if ((visible & 8) != 0) super.renderFaceZPos(b, 0, 0, 0, i);
        }

        @Override
        public void renderFaceXNeg(Block b, double x, double y, double z, IIcon i) {
            if ((visible & 16) != 0) super.renderFaceXNeg(b, 0, 0, 0, i);
        }

        @Override
        public void renderFaceXPos(Block b, double x, double y, double z, IIcon i) {
            if ((visible & 32) != 0) super.renderFaceXPos(b, 0, 0, 0, i);
        }
    }
}
