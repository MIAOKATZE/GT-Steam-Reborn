// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.machinevisual;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/** Reuses native mixed-light results only within one synchronous render invocation. */
final class MachineVisualRenderAccess implements IBlockAccess {

    private static final int LIMIT = 32768;
    private final Long2IntOpenHashMap[] lighting = new Long2IntOpenHashMap[16];
    private IBlockAccess source;
    private int entries;

    boolean begin(IBlockAccess next) {
        if (source != null) return false;
        source = next;
        entries = 0;
        for (Long2IntOpenHashMap cache : lighting) if (cache != null) cache.clear();
        return true;
    }

    void end() {
        source = null;
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minimum) {
        // Outside vanilla coordinate/light ranges, preserve the delegate's exact behavior without a packed key.
        if (x < -33554432 || x > 33554431
            || z < -33554432
            || z > 33554431
            || y < -2048
            || y > 2047
            || minimum < 0
            || minimum > 15) return source.getLightBrightnessForSkyBlocks(x, y, z, minimum);
        Long2IntOpenHashMap cache = lighting[minimum];
        if (cache == null) {
            cache = new Long2IntOpenHashMap();
            cache.defaultReturnValue(-1);
            lighting[minimum] = cache;
        }
        long key = ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | (long) y & 0xfffL;
        int result = cache.get(key);
        if (result != -1) return result;
        result = source.getLightBrightnessForSkyBlocks(x, y, z, minimum);
        if (result != -1 && entries < LIMIT) {
            cache.put(key, result);
            entries++;
        }
        return result;
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return source.getBlock(x, y, z);
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return source.getTileEntity(x, y, z);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return source.getBlockMetadata(x, y, z);
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
        return source.isBlockProvidingPowerTo(x, y, z, side);
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        return source.isAirBlock(x, y, z);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return source.getBiomeGenForCoords(x, z);
    }

    @Override
    public int getHeight() {
        return source.getHeight();
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return source.extendedLevelsInChunkCache();
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean fallback) {
        return source.isSideSolid(x, y, z, side, fallback);
    }
}
