package com.miaokatze.gtsr.common.dimension.prosperity;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
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
 * 表面与主体替换（<b>P2 起表层链上收框架</b>，S-A1 plan §12 修订 4）：generateTerrain 保持
 * stone 主体（框架 provideChunk 在 generateTerrain 之后才加载 biomes 数组，主体替换无法前移）；
 * 框架内核逐列自顶向下——首个"stone + 上方空气"裸露面落 top（meta 走 biome.field_150604_aj），
 * 其下 filler 1-2 格落 biome.fillerBlock（meta 经 {@link #fillerMetaOf} 直写），其余 stone 主体
 * 整段替换为群系 base 方块（{@link #baseBlockOf}）。本类对该链的贡献只剩一份声明式
 * {@link #spec()}（扫描主体＝stone、写 top/filler meta、整段换主体、不提前 break）与两个材质
 * 钩子；事件段、降级门、下标口径均在 {@code GTSRChunkProviderBase}。不调
 * {@code biome.genTerrainBlocks}（其 parabolic/62 带逻辑服务于原版噪声管线的 meta 现状）。
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
     * <p>
     * <b>P3</b>：本方法内的 {@link #bedrockTop(long,int,int)} 与 {@code ChunkProviderShatteredGrounds}
     * 的同名私有件<b>逐字符相同</b>，已并为框架件一份；哈希算法本身在
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

    /**
     * 表层/主体替换核心（P2 起为框架 {@code GTSRChunkProviderBase.applyBiomeSurface} 的
     * <b>dim78 声明式规格 + 公开静态缝</b>）：
     * <ul>
     * <li>规格：扫描主体 {@code Blocks.stone}、top 写 {@code biome.field_150604_aj}（差异①）、
     * filler meta 查 {@link #fillerMetaOf}、filler 段以下<b>整段</b>换 {@link #baseBlockOf}
     * （差异②）、<b>不</b>提前 break（差异③，必须扫到 y=1 才能整段换主体）、不跳过悬空格；</li>
     * <li>零 World 依赖的纯数组变换由框架保证，本静态缝供
     * {@code tools/dim1/ReplaceSurfaceRuntimeCheck} / {@code SurfaceByteParityDump} /
     * {@code SurfaceDegradationCheck} / {@code SurfaceTranspositionCheck} 离线驱动同一运行时代码；</li>
     * <li>降级态（缺席群系 / 空表）是否铺表层由框架门统一决定（plan §5 P2 判据 B）：
     * 缺席列一格不写，绝不出现 plains/grass/dirt。</li>
     * </ul>
     */
    public static void applyBiomeSurface(long worldSeed, int baseX, int baseZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes) {
        GTSRChunkProviderBase.applyBiomeSurface(worldSeed, baseX, baseZ, blocks, metadata, biomes, spec());
    }

    /** 本 provider 的表层规格（每次新建：{@code bodyBlock} 惰性读，避免早于 BlockLoader 定值）。 */
    private static GTSRChunkProviderBase.SurfaceSpec spec() {
        return new GTSRChunkProviderBase.SurfaceSpec(
            GTSRBiomeAuthority.DIM_KEY_PROSPERITY,
            () -> Blocks.stone,
            true,
            ChunkProviderProsperityRuins::fillerMetaOf,
            ChunkProviderProsperityRuins::baseBlockOf,
            false,
            false);
    }

    @Override
    protected GTSRChunkProviderBase.SurfaceSpec surfaceSpec() {
        return spec();
    }

    /**
     * L1 群系身份（两跳：实例账本 → 实际 id 反查，与 {@code ShatteredBiomes.identityOf} 同形状；
     * plan §2.1 L1「身份的唯一出口」）。返回 {@code null} = 本维名册点名不到（外来群系 /
     * 未注册实例）。第二跳仅命中账本记录的<b>实际 id</b>（{@link GTSRBiomeAuthority#actualIdOf}），
     * 不做 {@code biomeID - idStart} 减法，供离线合成实例与注册后新建实例同样可命名。
     */
    public static BiomeId identityOf(BiomeGenBase biome) {
        if (biome == null) {
            return null;
        }
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        final GTSRBiomeAuthority.Resolution byInstance = authority.of(biome);
        if (byInstance.resolved()) {
            return byInstance.biomeId;
        }
        for (final BiomeId key : BiomeId.values()) {
            if (key.dimKey()
                .equals(GTSRBiomeAuthority.DIM_KEY_PROSPERITY) && authority.actualIdOf(key) == biome.biomeID) {
                return key;
            }
        }
        return null;
    }

    /**
     * 群系主体 base 方块映射（S-A1，plan §12 修订 4：自然区主体 stone 按群系替换为 base 方块）。
     * <b>P2b 起身份读面收口 L1</b>：经 {@link #identityOf} 解析为 {@link BiomeId} 后 switch——
     * 不再 {@code instanceof} 判类（plan §2.1 L1 禁止项），也不读 Chunk 保存的 byte 平面。
     * 名册点名不到（S1 平坦模板群系 / 外来群系 / 缺席实例）回退 {@link Blocks#stone}，与
     * 改造前 instanceof 未命中口径逐格一致（行为矩阵由
     * {@code tools/dim1/SurfaceBiomeMatrixCheck} 行为钉断言，逐字节回归由
     * {@code SurfaceByteParityDump} 对拍断言）。
     */
    private static Block baseBlockOf(BiomeGenBase biome) {
        final BiomeId key = identityOf(biome);
        if (key == null) {
            return Blocks.stone;
        }
        switch (key) {
            case RUSTED_STEPPE: {
                return BlocksGTSR.prosperitySteppeBase;
            }
            case GEARWORK_FOREST: {
                return BlocksGTSR.prosperityForestBase;
            }
            case BRASS_WASTES: {
                return BlocksGTSR.prosperityWastesBase;
            }
            case FUMAROLE_SWAMP: {
                return BlocksGTSR.prosperitySwampBase;
            }
            default: {
                return Blocks.stone;
            }
        }
    }

    /**
     * filler meta 群系映射（S-A1 起独立方块族 meta 恒 0，plan §12 修订 2/4）。
     * <b>P2b 起身份读面收口 L1</b>：同 {@link #baseBlockOf} 经 {@link #identityOf} 后 switch，
     * meta <b>数值仍逐字取自各群系类的 {@code FILLER_META} 常量</b>（群系侧单一真值，本方法
     * 只做"身份 → 常量"的选取，不复制数值）；名册点名不到回退 meta 0，与改造前 instanceof
     * 未命中口径一致。表与群系常量的对应关系由 {@code tools/dim1/SurfaceBiomeMatrixCheck}
     * 的<b>行为钉</b>（真实表层链输出逐格对账）钉住，不再是源码文本钉。
     */
    private static int fillerMetaOf(BiomeGenBase biome) {
        final BiomeId key = identityOf(biome);
        if (key == null) {
            return 0;
        }
        switch (key) {
            case RUSTED_STEPPE: {
                return BiomeRustedSteppe.FILLER_META;
            }
            case GEARWORK_FOREST: {
                return BiomeGearworkForest.FILLER_META;
            }
            case BRASS_WASTES: {
                return BiomeBrassWastes.FILLER_META;
            }
            case FUMAROLE_SWAMP: {
                return BiomeFumaroleSwamp.FILLER_META;
            }
            default: {
                return 0;
            }
        }
    }
}
