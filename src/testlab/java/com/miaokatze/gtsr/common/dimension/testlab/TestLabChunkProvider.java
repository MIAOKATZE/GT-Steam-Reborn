package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import com.miaokatze.gtsr.common.dimension.framework.BiomePlaneAccess;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;

/** No terrain events, ores, decorators, or survival structures. */
public final class TestLabChunkProvider extends GTSRChunkProviderBase {

    public TestLabChunkProvider(World world, long seed) {
        super(world, seed);
    }

    @Override
    public Chunk provideChunk(int x, int z) {
        Block[] blocks = new Block[65536];
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int index = bx << 12 | bz << 8;
                blocks[index] = Blocks.bedrock;
                blocks[index | 63] = Blocks.stone;
                blocks[index | 64] = Blocks.quartz_block;
            }
        }
        Chunk chunk = new Chunk(worldObj, blocks, new byte[65536], x, z);
        BiomeGenBase[] biomes = new BiomeGenBase[256];
        java.util.Arrays.fill(biomes, BiomeGenBase.plains);
        if (!BiomePlaneAccess.write(chunk, biomes))
            throw new IllegalStateException("Cannot initialize real lab chunk biome plane at " + x + "," + z);
        chunk.generateSkylightMap();
        return chunk;
    }

    @Override
    public List getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return Collections.emptyList();
    }
}
