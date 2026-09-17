package com.miaokatze.gtsr.common.dimension.prosperity;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeBrassWastes;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeFumaroleSwamp;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeGearworkForest;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeRustedSteppe;

/**
 * 繁荣维度地形生成器（dim1 S4b，plan §1.2 :59-65 / 02 §2 参数表）。
 * <p>
 * 地形模型 = {@link ProsperityTerrainProfile#heightAt} 高度场（全维度统一缓丘 + 低频幅度调制，
 * 模板异变简化口径，见 Profile 类注释）：每列 y=0..bedrockDepth 基岩（深度 1-4，02 §2.1
 * "基岩层 y0-4"口径）、其上 stone 填至 heightAt，以上留空气。无海平面流体（gtsr.brine 在
 * 用户裁剪范围外，02 §0.2）；无洞穴/矿洞（02 §4/§5 裁剪，plan §1 范围红线）。
 * <p>
 * 表面替换（top/filler 走群系）：自研逐列替换——自顶向下找首个"stone + 上方空气"裸露面，
 * top 按 biome.topBlock/field_150604_aj 落地，filler 按 1-2 格深度落地并<b>直接写 metadata</b>
 * （1.7.10 genBiomeTerrain 只写 top meta 的现状，filler meta 补写方式 =
 * {@code ProsperityBiomes.applyFillerMeta} 同款直写 byte[]，仅改为替换点同步写）。
 * 不调用 biome.genTerrainBlocks（其 parabolic/62 带逻辑服务于原版噪声管线的 meta 现状）。
 * <p>
 * <b>高度红线</b>：地形与古代城（IWorldGenerator 通道）共用 {@link ProsperityTerrainProfile
 * #heightAt(World.getSeed(), x, z)} 同一纯函数——本类不做任何跨 chunk 方块读取。
 */
public class ChunkProviderProsperityRuins extends GTSRChunkProviderBase {

    public ChunkProviderProsperityRuins(World world, long seed) {
        super(world, seed);
    }

    /**
     * 高度场地形填充：Block[] 下标 x&lt;&lt;12 | z&lt;&lt;8 | y（S1 框架约定）。
     * 每列：基岩（y=0 恒有，1..3 按列哈希递减概率，整体深度 1-4 对齐 02 §2.1 基岩带）+
     * stone 至 heightAt（含）。
     */
    @Override
    protected void generateTerrain(int chunkX, int chunkZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomesForGeneration) {
        final long worldSeed = this.worldObj.getSeed();
        final int baseX = chunkX * 16;
        final int baseZ = chunkZ * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int column = x << 12 | z << 8;
                final int height = ProsperityTerrainProfile.heightAt(worldSeed, baseX + x, baseZ + z);
                final int bedrockTop = bedrockTop(worldSeed, baseX + x, baseZ + z);
                for (int y = 0; y <= bedrockTop; y++) {
                    blocks[column | y] = Blocks.bedrock;
                }
                for (int y = bedrockTop + 1; y <= height; y++) {
                    blocks[column | y] = Blocks.stone;
                }
            }
        }
    }

    /** 列基岩顶 y（0..3）：确定性列哈希（1 + hash%4），跨 chunk 无缝。 */
    private static int bedrockTop(long worldSeed, int x, int z) {
        long h = worldSeed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        return (int) (h & 3);
    }

    /**
     * 群系表面替换（保留 S1 框架 ReplaceBiomeBlocks 事件契约）：逐列自顶向下找首个
     * "stone + 上方空气"，top 1 格 + filler 1-2 格（深度确定性），top meta 走
     * biome.field_150604_aj、filler meta 经 {@link #fillerMetaOf} 群系映射直写。
     */
    @Override
    protected void replaceBlocksForBiome(int chunkX, int chunkZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes) {
        // ReplaceBiomeBlocks 事件契约保留（ChunkProviderShatteredLands 同位：不调 super——
        // S1 super 实现会按 biome.genTerrainBlocks 原版管线再做一遍表面，与本实现二选一）
        final net.minecraftforge.event.terraingen.ChunkProviderEvent.ReplaceBiomeBlocks event = new net.minecraftforge.event.terraingen.ChunkProviderEvent.ReplaceBiomeBlocks(
            this,
            chunkX,
            chunkZ,
            blocks,
            metadata,
            biomes,
            null);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        if (event.getResult() == cpw.mods.fml.common.eventhandler.Event.Result.DENY) {
            return;
        }
        final long worldSeed = this.worldObj.getSeed();
        final int baseX = chunkX * 16;
        final int baseZ = chunkZ * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final BiomeGenBase biome = biomes[z + x * 16];
                if (biome == null) {
                    continue;
                }
                final int column = x << 12 | z << 8;
                final int fillerMeta = fillerMetaOf(biome);
                final int depth = 1 + (int) ((mix(worldSeed, baseX + x, baseZ + z)) & 1); // filler 深度 1-2
                for (int y = 254; y > 0; y--) {
                    final int idx = column | y;
                    if (blocks[idx] != Blocks.stone || blocks[idx + 1] != Blocks.air) {
                        continue;
                    }
                    blocks[idx] = biome.topBlock;
                    metadata[idx] = (byte) biome.field_150604_aj;
                    for (int d = 1; d <= depth && y - d > 0; d++) {
                        final int fidx = idx - d;
                        if (blocks[fidx] == Blocks.stone) {
                            blocks[fidx] = biome.fillerBlock;
                            metadata[fidx] = (byte) fillerMeta;
                        }
                    }
                    break; // 每列只处理最高裸露面
                }
            }
        }
    }

    /**
     * filler meta 群系映射（02 §1.1 表：锈土 1 / 锈土 1 / 锈石 5 / 锈黏 4）。
     * instanceof 显式四群系——避免改 S1 GTSRBiomeBase 契约（本切片硬边界）；
     * 未知群系回退 meta0（S1 平坦模板群系，filler 本就无 meta 语义）。
     */
    private static int fillerMetaOf(BiomeGenBase biome) {
        if (biome instanceof BiomeRustedSteppe) {
            return BiomeRustedSteppe.FILLER_META;
        }
        if (biome instanceof BiomeGearworkForest) {
            return BiomeGearworkForest.FILLER_META;
        }
        if (biome instanceof BiomeBrassWastes) {
            return BiomeBrassWastes.FILLER_META;
        }
        if (biome instanceof BiomeFumaroleSwamp) {
            return BiomeFumaroleSwamp.FILLER_META;
        }
        return 0;
    }

    private static long mix(long worldSeed, int x, int z) {
        long h = worldSeed ^ (x * 0x27D4EB2F165667C5L) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }
}
