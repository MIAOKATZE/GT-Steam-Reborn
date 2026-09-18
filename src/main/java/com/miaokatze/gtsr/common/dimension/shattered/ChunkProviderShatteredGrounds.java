package com.miaokatze.gtsr.common.dimension.shattered;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkClampedSink;

/**
 * 破碎之地地形生成器（dim79 重做 S-B1，plan §5 S-B1）：完整正常地形——<b>无浮岛、无裂隙层、
 * 无悬空</b>（旧 ChunkProviderShatteredLands 密度公式/裂隙层/黑石斑随葬，类已删除）。
 * <p>
 * 地形模型 = {@link ShatteredTerrainProfile#heightAt} 高度场（基准 64、双频起伏 + 低频幅度
 * 调制、钳制 36..96，零 MC 纯函数）：每列 y=0..bedrockTop 基岩（深度 1-4，列哈希）、其上
 * <b>shatteredCorestone 实心填充至 heightAt（含）</b>，以上留空气。无海平面流体、无洞穴
 * （旧裂隙层机制不保留）。
 * <p>
 * 表层三段式替换（任务包口径 "表层 top1/filler1-2/base"）：replaceBlocksForBiome 逐列自顶
 * 向下——首个"corestone + 上方空气"裸露面落 biome.topBlock（1 格），其下 filler 段 1-2 格
 * （确定性列哈希深度）落 biome.fillerBlock（filler 复用同群系 base 方块，meta 恒 0），
 * <b>其余主体保持 shatteredCorestone</b>（"地形主体石"口径：任务包明确 corestone 实心至
 * heightAt——dim78 §12 修订 4 的"全主体换 base"仅针对 dim78，dim79 base 段即 filler 段
 * 1-2 格；若 review 裁决改全主体换 base，仅需本方法 filler 段之后不再 break、续写 base，
 * 单点改动）。
 * <p>
 * 装饰挂接：{@link #onPopulate} 委托 {@link ShatteredDecorPlacer}（石刺簇/灰烬枯树/灰烬
 * 棘丛，确定性 per-chunk 哈希，ChunkClampedSink 钳制本 chunk）。结构生成不经本类
 * （IWorldGenerator = WorldGenShatteredRuins 责任，约定承袭 S6a）。
 */
public class ChunkProviderShatteredGrounds extends GTSRChunkProviderBase {

    public ChunkProviderShatteredGrounds(World world, long seed) {
        super(world, seed);
    }

    /**
     * 高度场地形填充：Block[] 下标 x&lt;&lt;12 | z&lt;&lt;8 | y（S1 框架约定）。
     * 每列：基岩（y=0 恒有，1..3 按列哈希递减概率，整体深度 1-4）+
     * shatteredCorestone 至 heightAt（含）。
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
                final int height = ShatteredTerrainProfile.heightAt(worldSeed, baseX + x, baseZ + z);
                final int bedrockTop = bedrockTop(worldSeed, baseX + x, baseZ + z);
                for (int y = 0; y <= bedrockTop; y++) {
                    blocks[column | y] = Blocks.bedrock;
                }
                for (int y = bedrockTop + 1; y <= height; y++) {
                    blocks[column | y] = BlocksGTSR.shatteredCorestone;
                }
            }
        }
    }

    /** 列基岩顶 y（0..3）：确定性列哈希（1 + hash%4），跨 chunk 无缝（ChunkProviderProsperityRuins 同款）。 */
    private static int bedrockTop(long worldSeed, int x, int z) {
        long h = worldSeed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        return (int) (h & 3);
    }

    /**
     * 表层三段式替换（保留框架 ReplaceBiomeBlocks 事件契约；不调 super——super 实现会按
     * biome.genTerrainBlocks 原版管线再做一遍表面，与本实现二选一；v1.20.30 终验修复：
     * 裸露面判定走框架 {@code isAirOrEmpty}——原始数组未写入槽位是 <b>null</b> 而非
     * {@code Blocks.air}，此前的 {@code != Blocks.air} 恒真导致整链 no-op）：逐列自顶向下，首个
     * "corestone + 上方空气"裸露面落 top，其下 filler 段 1-2 格（确定性深度）落 base
     * （filler 复用 base），主体保持 corestone；filler 段写完即 break（下方无替换需求）。
     * 扫描/落位核心在 {@link #applyBiomeSurface}（public static，供
     * tools/dim1/ReplaceSurfaceRuntimeCheck 以合成 biomes 数组离线驱动同一运行时代码路径）。
     */
    @Override
    protected void replaceBlocksForBiome(int chunkX, int chunkZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes) {
        // ReplaceBiomeBlocks 事件契约保留（兼容第三方 terraingen 钩子）
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
        applyBiomeSurface(this.worldObj.getSeed(), chunkX * 16, chunkZ * 16, blocks, metadata, biomes);
    }

    /**
     * 表层替换核心（世界种子 + chunk 原点世界坐标入参的纯数组变换，零 World 依赖，
     * 与 {@link #replaceBlocksForBiome} 事件段同一实现体——运行时路径级离线断言的静态缝）。
     */
    public static void applyBiomeSurface(long worldSeed, int baseX, int baseZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final BiomeGenBase biome = biomes[z + x * 16];
                if (biome == null) {
                    continue;
                }
                final int column = x << 12 | z << 8;
                final int depth = 1 + (int) (mix(worldSeed, baseX + x, baseZ + z) & 1); // filler 深度 1-2
                boolean topPlaced = false;
                int fillerLeft = depth;
                for (int y = 254; y > 0; y--) {
                    final int idx = column | y;
                    if (blocks[idx] != BlocksGTSR.shatteredCorestone) {
                        continue;
                    }
                    if (!topPlaced) {
                        if (!isAirOrEmpty(blocks[idx + 1])) {
                            continue; // 尚未到达裸露面（上方仍被 corestone 覆盖）
                        }
                        blocks[idx] = biome.topBlock;
                        topPlaced = true;
                        continue;
                    }
                    // 裸露面以下：filler 段（1-2 格）落 base，写完主体保持 corestone
                    if (isAirOrEmpty(blocks[idx + 1])) {
                        continue; // 悬空 corestone（不应出现，防御跳过）
                    }
                    blocks[idx] = biome.fillerBlock;
                    fillerLeft--;
                    if (fillerLeft <= 0) {
                        break;
                    }
                }
            }
        }
    }

    /** populate 装饰挂接（S-B 装饰子项，plan §12 修订 9）：石刺簇/灰烬枯树/灰烬棘丛确定性散布。 */
    @Override
    protected void onPopulate(Random random, int chunkX, int chunkZ) {
        ShatteredDecorPlacer.decorate(
            this.worldObj,
            this.worldObj.getSeed(),
            chunkX,
            chunkZ,
            new ChunkClampedSink(this.worldObj, chunkX, chunkZ));
    }

    private static long mix(long worldSeed, int x, int z) {
        long h = worldSeed ^ (x * 0x27D4EB2F165667C5L) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }
}
