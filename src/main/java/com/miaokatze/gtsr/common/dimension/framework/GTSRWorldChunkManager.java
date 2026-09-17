package com.miaokatze.gtsr.common.dimension.framework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;

/**
 * 自写 BiomeProvider（dim1 S1，不走 GenLayer；plan §1.2/§5.4 口径）。
 * <p>
 * per-chunk 选择纯函数 {@link #biomeAt(int, int)}：对 def 群系权重表按权重展开成数组，
 * 用 splitmix 终结哈希（世界种子掺 chunk 坐标）取模——确定性、与 chunk 生成顺序无关。
 * 群系表为空时（S1 骨架态）回退原版 plains，保证维度可运行。
 * 无 BiomeCache：哈希为 O(1) 纯函数，覆写 cleanupCache 为空操作。
 */
public class GTSRWorldChunkManager extends WorldChunkManager {

    private static final long HASH_CONSTANT_X = 0x9E3779B97F4A7C15L;
    private static final long HASH_CONSTANT_Z = 0xBF58476D1CE4E5B9L;

    private final long seed;
    /** 按权重展开的群系选择表（null = 空群系表，走 fallback）。 */
    private final BiomeGenBase[] weightedBiomes;

    public GTSRWorldChunkManager(long seed, GTSRDimensionDef def) {
        this.seed = seed;
        List<BiomeGenBase> expanded = new ArrayList<>();
        if (def != null) {
            List<BiomeGenBase> table = def.getBiomeTable();
            int[] weights = def.getBiomeWeights();
            for (int i = 0; i < table.size(); i++) {
                int weight = Math.max(1, weights[i]);
                for (int w = 0; w < weight; w++) {
                    expanded.add(table.get(i));
                }
            }
        }
        this.weightedBiomes = expanded.isEmpty() ? null : expanded.toArray(new BiomeGenBase[0]);
    }

    /** per-chunk 群系选择纯函数（确定性哈希驱动；空群系表回退原版 plains）。 */
    public BiomeGenBase biomeAt(int chunkX, int chunkZ) {
        if (this.weightedBiomes == null) {
            return BiomeGenBase.plains;
        }
        return this.weightedBiomes[hash(chunkX, chunkZ) % this.weightedBiomes.length];
    }

    /** splitmix64 终结哈希（非负 31 位结果）。 */
    private int hash(int chunkX, int chunkZ) {
        long h = this.seed ^ (chunkX * HASH_CONSTANT_X) ^ (chunkZ * HASH_CONSTANT_Z);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (int) (h & 0x7FFFFFFFL);
    }

    @Override
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        return biomeAt(x >> 4, z >> 4);
    }

    @Override
    public List<BiomeGenBase> getBiomesToSpawnIn() {
        return Collections.emptyList();
    }

    @Override
    public float[] getRainfall(float[] listToReuse, int x, int z, int width, int length) {
        if (listToReuse == null || listToReuse.length < width * length) {
            listToReuse = new float[width * length];
        }
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < length; dz++) {
                listToReuse[dx + dz * width] = biomeAt((x + dx) >> 4, (z + dz) >> 4).getIntRainfall() / 65536.0F;
            }
        }
        return listToReuse;
    }

    @Override
    public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] listToReuse, int x, int z, int width, int length) {
        if (listToReuse == null || listToReuse.length < width * length) {
            listToReuse = new BiomeGenBase[width * length];
        }
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < length; dz++) {
                listToReuse[dx + dz * width] = biomeAt((x + dx) >> 4, (z + dz) >> 4);
            }
        }
        return listToReuse;
    }

    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] oldList, int x, int z, int width, int depth) {
        return getBiomesForGeneration(oldList, x, z, width, depth);
    }

    @Override
    public BiomeGenBase[] getBiomeGenAt(BiomeGenBase[] listToReuse, int x, int z, int width, int length,
        boolean cacheFlag) {
        return getBiomesForGeneration(listToReuse, x, z, width, length);
    }

    @Override
    public boolean areBiomesViable(int x, int z, int range, List<BiomeGenBase> biomes) {
        int startX = x - range >> 2;
        int startZ = z - range >> 2;
        int spanX = (range * 2 >> 2) + 1;
        int spanZ = (range * 2 >> 2) + 1;
        for (int dx = 0; dx < spanX; dx++) {
            for (int dz = 0; dz < spanZ; dz++) {
                if (!biomes.contains(biomeAt((startX + dx << 2) >> 4, (startZ + dz << 2) >> 4))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ChunkPosition findBiomePosition(int x, int z, int range, List<BiomeGenBase> biomes, Random random) {
        return null;
    }

    @Override
    public void cleanupCache() {}
}
