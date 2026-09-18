package com.miaokatze.gtsr.common.dimension.shattered;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
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
 * 表层三段式替换（<b>P2 起表层链上收框架</b>；任务包口径 "表层 top1/filler1-2/base"）：框架内核
 * 逐列自顶向下——首个"corestone + 上方空气"裸露面落 biome.topBlock（1 格，<b>不写 meta</b>＝
 * 与 dim78 的差异①），其下 filler 段 1-2 格（确定性列哈希深度）落 biome.fillerBlock（filler
 * 复用同群系 base 方块），<b>其余主体保持 shatteredCorestone</b>（差异②：dim78 §12 修订 4 的
 * "全主体换 base"仅针对 dim78）且 filler 段写完即 break（差异③）。本类对该链的贡献只剩一份
 * 声明式 {@link #spec()}；事件段、降级门（缺席列/空表一律不铺）、下标口径均在
 * {@code GTSRChunkProviderBase}。若 review 裁决改全主体换 base，只需把 spec 的 wholeBody 钩子
 * 换上并去掉 break——单点改动，不再涉及扫描循环。
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
     * <p>
     * <b>P3</b>：{@link #bedrockTop(long,int,int)} 现取自框架一份（改造前与
     * {@code ChunkProviderProsperityRuins} 的同名私有件逐字符相同）；哈希算法在
     * {@code GTSRWorldgenHash.bedrockTopHash}（本片只搬位置，不改任何数值）。
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

    /**
     * 表层三段式替换（P2 起为框架 {@code GTSRChunkProviderBase.applyBiomeSurface} 的
     * <b>dim79 声明式规格 + 公开静态缝</b>）：扫描主体 {@code BlocksGTSR.shatteredCorestone}、
     * top <b>不写</b> meta（差异①，与 dim78 相反）、filler 段 <b>不写</b> meta
     * （{@link GTSRChunkProviderBase#NO_META_WRITE}）、主体<b>不</b>整段换 base（差异②，
     * 保留 corestone）、filler 段写完即 break（差异③）并带悬空主体格防御跳过。
     * <p>
     * 事件段、降级门（缺席列/空表一律不铺）、{@code biomes[x + z*16]} 下标口径均在框架；
     * 本静态缝零 World 依赖，供 {@code tools/dim1/ReplaceSurfaceRuntimeCheck} /
     * {@code SurfaceByteParityDump} / {@code SurfaceDegradationCheck} /
     * {@code SurfaceTranspositionCheck} 以合成 biomes 数组离线驱动同一运行时代码。
     */
    public static void applyBiomeSurface(long worldSeed, int baseX, int baseZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes) {
        GTSRChunkProviderBase.applyBiomeSurface(worldSeed, baseX, baseZ, blocks, metadata, biomes, spec());
    }

    /** 本 provider 的表层规格（{@code bodyBlock} 惰性读，避免早于 BlockLoader 定值）。 */
    private static GTSRChunkProviderBase.SurfaceSpec spec() {
        return new GTSRChunkProviderBase.SurfaceSpec(
            GTSRBiomeAuthority.DIM_KEY_SHATTERED,
            () -> BlocksGTSR.shatteredCorestone,
            false,
            biome -> GTSRChunkProviderBase.NO_META_WRITE,
            null,
            true,
            true);
    }

    @Override
    protected GTSRChunkProviderBase.SurfaceSpec surfaceSpec() {
        return spec();
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
}
