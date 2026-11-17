// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.blocks;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/** One scan only: native World.getBlock bounds and lazy chunk lookup, without retaining world data across ticks. */
final class SingularityBlockLookup {

    private static final ClassValue<Boolean> NATIVE_BLOCK_LOOKUP = new ClassValue<Boolean>() {

        @Override
        protected Boolean computeValue(Class<?> worldType) {
            for (String name : new String[] { "getBlock", "func_147439_a" }) {
                try {
                    return worldType.getMethod(name, int.class, int.class, int.class)
                        .getDeclaringClass() == World.class;
                } catch (NoSuchMethodException ignored) {
                    // Development and reobfuscated runtimes use different names.
                } catch (SecurityException denied) {
                    return false;
                }
            }
            return false;
        }
    };

    private final World world;
    private final int minChunkX, minChunkZ, depth;
    private final Chunk[] chunks;
    private final boolean nativeLookup;

    SingularityBlockLookup(World world, int centerX, int centerZ, int radius) {
        this.world = world;
        nativeLookup = radius >= 0 && radius <= 48 && NATIVE_BLOCK_LOOKUP.get(world.getClass());
        if (!nativeLookup) {
            minChunkX = minChunkZ = depth = 0;
            chunks = null;
            return;
        }
        minChunkX = (centerX - radius) >> 4;
        minChunkZ = (centerZ - radius) >> 4;
        int width = ((centerX + radius) >> 4) - minChunkX + 1;
        depth = ((centerZ + radius) >> 4) - minChunkZ + 1;
        chunks = new Chunk[width * depth];
    }

    Block getBlock(int x, int y, int z) {
        if (!nativeLookup) return world.getBlock(x, y, z);
        if (x < -30000000 || x >= 30000000 || z < -30000000 || z >= 30000000 || y < 0 || y >= 256) return Blocks.air;
        int index = ((x >> 4) - minChunkX) * depth + (z >> 4) - minChunkZ;
        Chunk chunk = chunks[index];
        if (chunk == null) chunks[index] = chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
        // Vanilla Chunk.getBlock returns air for a null section. Preserve overrides on specialized chunks.
        if (chunk.getClass() == Chunk.class && chunk.getBlockStorageArray()[y >> 4] == null) return Blocks.air;
        return chunk.getBlock(x & 15, y, z & 15);
    }
}
