// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.gui;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBounds;
import com.miaokatze.miaokatzecore.api.miaovision.SceneSnapshot;
import com.miaokatze.miaokatzecore.client.miaovision.MiaoVisionRenderer;
import com.miaokatze.miaokatzecore.client.miaovision.SceneCamera;
import com.miaokatze.miaokatzecore.client.miaovision.SceneModelResolver;
import com.miaokatze.miaokatzecore.client.miaovision.SceneViewport;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/** Uses the authored construction pieces as the room geometry of a Core MiaoVision scene. */
public final class CenterAssemblyScene implements AutoCloseable {

    private final MiaoVisionRenderer renderer = new MiaoVisionRenderer();
    private final SceneSnapshot empty = new SceneSnapshot("gtsr-ore-center", 0, 0, Collections.emptyList());
    private final FloatBuffer model = BufferUtils.createFloatBuffer(16), projection = BufferUtils.createFloatBuffer(16),
        projected = BufferUtils.createFloatBuffer(3);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(4);
    public final int[] x = new int[36], y = new int[36];
    private int[] shownModules, shownGrades;
    private int tier = -1, mask = -1;
    private int geometry, atlasId;
    private SceneBounds bounds;
    private double yawOffset;
    private double pitch = 28;
    private double zoom = 1.30;
    private boolean drew;

    public boolean draw(ModularGuiContext context, int width, int height, int tier, int mask, int[] modules, int[] grades) {
        Minecraft mc = Minecraft.getMinecraft();
        double sx = (double) mc.displayWidth / context.getScreenArea().width;
        double sy = (double) mc.displayHeight / context.getScreenArea().height;
        int left = (int) Math.floor(context.transformX(0, 0) * sx);
        int top = (int) Math.floor(context.transformY(0, 0) * sy);
        int right = (int) Math.ceil(context.transformX(width, 0) * sx);
        int bottom = (int) Math.ceil(context.transformY(0, height) * sy);
        if (left < 0 || top < 0 || right > mc.displayWidth || bottom > mc.displayHeight || right <= left || bottom <= top)
            return false;

        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        int currentAtlas = mc.getTextureManager().getTexture(TextureMap.locationBlocksTexture).getGlTextureId();
        if (tier != this.tier || mask != this.mask || currentAtlas != atlasId
            || !Arrays.equals(modules, shownModules) || !Arrays.equals(grades, shownGrades)) {
            clearGeometry();
            this.tier = tier;
            this.mask = mask;
            atlasId = currentAtlas;
            shownModules = modules.clone();
            shownGrades = grades.clone();
        }
        if (geometry == 0) {
            // Build the block access once per actual configuration, without using or scanning live chunks.
            BlocksView view = new BlocksView();
            CenterStructure.visitAssemblyPreview(tier, mask, shownModules, shownGrades, view::put);
            bounds = view.bounds();
            renderNative(mc, left, top, right, bottom, sx, sy, mask, view);
        } else {
            renderNative(mc, left, top, right, bottom, sx, sy, mask, null);
        }
        return drew;
    }

    private void renderNative(Minecraft mc, int left, int top, int right, int bottom,
        double sx, double sy, int mask, BlocksView build) {
        int sector = Integer.bitCount(mask) == 1 ? Integer.numberOfTrailingZeros(mask) : -1;
        double yaw = yawOffset + (sector < 0 ? 21 : 78 - 60 * sector);
        drew = false;
        renderer.render(empty, bounds, new SceneViewport(left, mc.displayHeight - bottom, right - left, bottom - top),
            new SceneCamera(yaw, pitch, zoom), new SceneModelResolver() {

                @Override
                public ItemStack resolve(String id) {
                    return null;
                }

                @Override
                public void beforeBodies(SceneSnapshot snapshot) {
                    mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
                    if (geometry == 0 && build != null) bake(build);
                    if (geometry == 0) return;
                    GL11.glCallList(geometry);
                    model.clear();
                    projection.clear();
                    viewport.clear();
                    GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, model);
                    GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
                    GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
                    CenterStructure.visitAssemblyPads((slot, px, py, pz) -> {
                        projected.clear();
                        if (GLU.gluProject(px + .5f, py + 1.5f, pz + .5f, model, projection, viewport, projected)) {
                            x[slot] = (int) Math.round((projected.get(0) - left) / sx);
                            y[slot] = (int) Math.round((mc.displayHeight - projected.get(1) - top) / sy);
                        }
                    });
                    drew = true;
                }
            }, null);
    }

    private void bake(BlocksView view) {
        int list = GL11.glGenLists(1);
        if (list == 0) return;
        boolean complete = false;
        Map<Block, float[]> originalBounds = new IdentityHashMap<>();
        GL11.glNewList(list, GL11.GL_COMPILE);
        try {
            RenderBlocks blocks = new RenderBlocks(view);
            Tessellator mesh = Tessellator.instance;
            for (int pass = 0; pass < 2; pass++) {
                mesh.startDrawingQuads();
                mesh.setTranslation(0, 0, 0);
                for (Cell cell : view.cells.values()) {
                    if (cell.block.getRenderBlockPass() != pass || view.enclosed(cell)) continue;
                    originalBounds.computeIfAbsent(cell.block, b -> new float[] { (float) b.getBlockBoundsMinX(),
                        (float) b.getBlockBoundsMinY(), (float) b.getBlockBoundsMinZ(), (float) b.getBlockBoundsMaxX(),
                        (float) b.getBlockBoundsMaxY(), (float) b.getBlockBoundsMaxZ() });
                    cell.block.setBlockBoundsBasedOnState(view, cell.x, cell.y, cell.z);
                    blocks.setRenderBoundsFromBlock(cell.block);
                    blocks.renderStandardBlock(cell.block, cell.x, cell.y, cell.z);
                }
                mesh.draw();
            }
            complete = true;
        } finally {
            GL11.glEndList();
            originalBounds.forEach((block, a) -> block.setBlockBounds(a[0], a[1], a[2], a[3], a[4], a[5]));
            if (complete) geometry = list;
            else GL11.glDeleteLists(list, 1);
        }
    }

    public void rotate(int dx, int dy) {
        yawOffset += dx;
        pitch = Math.max(8, Math.min(82, pitch + dy * .65));
    }

    public void zoom(boolean in) {
        zoom = Math.max(.68, Math.min(2.4, zoom * (in ? 1.10 : 1 / 1.10)));
    }

    public String renderFailure() {
        return renderer.opacityFailure();
    }

    private void clearGeometry() {
        if (geometry != 0) GL11.glDeleteLists(geometry, 1);
        geometry = 0;
    }

    @Override
    public void close() {
        clearGeometry();
        renderer.close();
    }

    private static final class Cell {
        final int x, y, z, meta;
        final Block block;

        Cell(int x, int y, int z, Block block, int meta) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.meta = meta;
        }
    }

    private static final class BlocksView implements IBlockAccess {
        final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();
        int minX, minY, minZ, maxX, maxY, maxZ;

        void put(int x, int y, int z, Block block, int meta) {
            cells.put(key(x, y, z), new Cell(x, y, z, block, meta));
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        SceneBounds bounds() {
            return new SceneBounds(minX - 1, minY - 1, minZ - 1, maxX + 2, maxY + 2, maxZ + 2);
        }

        boolean enclosed(Cell cell) {
            for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
                if (!getBlock(cell.x + direction.offsetX, cell.y + direction.offsetY,
                    cell.z + direction.offsetZ).isOpaqueCube()) return false;
            return true;
        }

        private static long key(int x, int y, int z) {
            return ((long) x & 0x1fffff) << 42 | ((long) z & 0x1fffff) << 21 | (y & 0x1fffff);
        }

        public Block getBlock(int x, int y, int z) {
            Cell cell = cells.get(key(x, y, z));
            return cell == null ? Blocks.air : cell.block;
        }

        public int getBlockMetadata(int x, int y, int z) {
            Cell cell = cells.get(key(x, y, z));
            return cell == null ? 0 : cell.meta;
        }

        public TileEntity getTileEntity(int x, int y, int z) { return null; }
        public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minimum) { return 0xf000f0; }
        public int isBlockProvidingPowerTo(int x, int y, int z, int side) { return 0; }
        public boolean isAirBlock(int x, int y, int z) { return getBlock(x, y, z) == Blocks.air; }
        public BiomeGenBase getBiomeGenForCoords(int x, int z) { return BiomeGenBase.plains; }
        public int getHeight() { return 256; }
        public boolean extendedLevelsInChunkCache() { return false; }
        public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean fallback) {
            return getBlock(x, y, z).isSideSolid(this, x, y, z, side);
        }
    }
}
