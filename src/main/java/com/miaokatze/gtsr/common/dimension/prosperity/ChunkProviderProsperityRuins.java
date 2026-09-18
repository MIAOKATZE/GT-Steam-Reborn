package com.miaokatze.gtsr.common.dimension.prosperity;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeBrassWastes;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeFumaroleSwamp;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeGearworkForest;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeRustedSteppe;

/**
 * 繁荣维度地形生成器（dim1 S4b，plan §1.2 :59-65 / 02 §2 参数表；S-A1 起自然区按群系独立化）。
 * <p>
 * 地形模型 = {@link ProsperityTerrainProfile#heightAt} 高度场（全维度统一缓丘 + 低频幅度调制，
 * 模板异变简化口径，见 Profile 类注释）：每列 y=0..bedrockDepth 基岩（深度 1-4，02 §2.1
 * "基岩层 y0-4"口径）、其上 stone 填至 heightAt，以上留空气。无海平面流体（gtsr.brine 在
 * 用户裁剪范围外，02 §0.2）；无洞穴/矿洞（02 §4/§5 裁剪，plan §1 范围红线）。
 * <p>
 * 表面与主体替换（S-A1，plan §12 修订 4）：generateTerrain 保持 stone 主体（框架 provideChunk
 * 在 generateTerrain 之后才加载 biomes 数组，主体替换无法前移）；replaceBlocksForBiome 逐列
 * 自顶向下——首个"stone + 上方空气"裸露面落 top（biome.topBlock/field_150604_aj），其下
 * filler 1-2 格深度落 biome.fillerBlock（filler 复用 base，meta 恒 0），<b>其余 stone 主体整段
 * 替换为群系 base 方块</b>（{@link #baseBlockOf}）。填充与替换两阶段口径的净效果 =
 * 基岩之上为群系 base 实心体 + 表层 top。不调用 biome.genTerrainBlocks（其 parabolic/62 带
 * 逻辑服务于原版噪声管线的 meta 现状）。
 * <p>
 * <b>高度红线</b>：地形与古代城（IWorldGenerator 通道）共用 {@link ProsperityTerrainProfile
 * #heightAt(World.getSeed(), x, z)} 同一纯函数——本类不做任何跨 chunk 方块读取；基岩带与
 * heightAt 本切片零改动。
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
     * 群系表面与主体替换（保留 S1 框架 ReplaceBiomeBlocks 事件契约；S-A1 plan §12 修订 4；
     * v1.20.30 终验修复：裸露面判定走框架 {@code isAirOrEmpty}——原始数组未写入槽位是
     * <b>null</b> 而非 {@code Blocks.air}，此前的 {@code != Blocks.air} 恒真导致整链 no-op）：
     * 逐列自顶向下——首个"stone + 上方空气"裸露面落 top（meta 走 biome.field_150604_aj），
     * 其下 filler 1-2 格（深度确定性）落 biome.fillerBlock（meta 经 {@link #fillerMetaOf} 直写），
     * 其余 stone 主体整段替换为 {@link #baseBlockOf} 群系 base 方块。generateTerrain 之后才加载
     * biomes 数组（GTSRChunkProviderBase.provideChunk 顺序），主体替换无法前移——本类单次
     * 全列扫描同时完成 top/filler/base 三层落位。扫描/落位核心在
     * {@link #applyBiomeSurface}（public static，供 tools/dim1/ReplaceSurfaceRuntimeCheck
     * 以合成 biomes 数组离线驱动同一运行时代码路径）。
     */
    @Override
    protected void replaceBlocksForBiome(int chunkX, int chunkZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes) {
        // ReplaceBiomeBlocks 事件契约保留（两 provider 同位：不调 super——
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
        applyBiomeSurface(this.worldObj.getSeed(), chunkX * 16, chunkZ * 16, blocks, metadata, biomes);
    }

    /**
     * 表层/主体替换核心（世界种子 + chunk 原点世界坐标入参的纯数组变换，零 World 依赖，
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
                final Block baseBlock = baseBlockOf(biome);
                final int fillerMeta = fillerMetaOf(biome);
                final int depth = 1 + (int) ((mix(worldSeed, baseX + x, baseZ + z)) & 1); // filler 深度 1-2
                boolean topPlaced = false;
                int fillerLeft = 0;
                for (int y = 254; y > 0; y--) {
                    final int idx = column | y;
                    if (blocks[idx] != Blocks.stone) {
                        continue;
                    }
                    if (!topPlaced) {
                        if (!isAirOrEmpty(blocks[idx + 1])) {
                            continue; // 尚未到达裸露面（上方仍被 stone 覆盖）
                        }
                        blocks[idx] = biome.topBlock;
                        metadata[idx] = (byte) biome.field_150604_aj;
                        topPlaced = true;
                        fillerLeft = depth;
                        continue;
                    }
                    // 裸露面以下：顶部 depth 格落 filler，其余主体 stone → 群系 base
                    if (fillerLeft > 0) {
                        blocks[idx] = biome.fillerBlock;
                        metadata[idx] = (byte) fillerMeta;
                        fillerLeft--;
                    } else {
                        blocks[idx] = baseBlock;
                    }
                }
            }
        }
    }

    /**
     * 群系主体 base 方块映射（S-A1，plan §12 修订 4：自然区主体 stone 按群系替换为 base 方块）。
     * instanceof 显式四群系——避免改 S1 GTSRBiomeBase 契约（同 {@link #fillerMetaOf} 口径）；
     * 未知群系回退 Blocks.stone（S1 平坦模板群系 / 群系降级运行保持石质主体）。
     */
    private static Block baseBlockOf(BiomeGenBase biome) {
        if (biome instanceof BiomeRustedSteppe) {
            return BlocksGTSR.prosperitySteppeBase;
        }
        if (biome instanceof BiomeGearworkForest) {
            return BlocksGTSR.prosperityForestBase;
        }
        if (biome instanceof BiomeBrassWastes) {
            return BlocksGTSR.prosperityWastesBase;
        }
        if (biome instanceof BiomeFumaroleSwamp) {
            return BlocksGTSR.prosperitySwampBase;
        }
        return Blocks.stone;
    }

    /**
     * filler meta 群系映射（S-A1 起独立方块族 meta 恒 0，plan §12 修订 2/4；表结构与群系
     * 常量的对应关系由 tools/dim1/SurfaceBiomeMatrixCheck 离线断言钉住）。
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
