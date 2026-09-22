package com.miaokatze.gtsr.common.dimension.prosperity.biome;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 遗忘之川（dim78 第 5 群系，v1.20.39 T5 / plan §3.3）——细长稀有的主干宽河带：主干带
 * （{@code GTSRVoronoiRiverField.trunkAt}，波长约 5000 格、占河网 1/6-1/8）内的 Voronoi 主边界
 * 宽河（width×3.5）+ 巨湖（第二 Voronoi，湖床 62-64/水面 68），与普通河同网连通但少支流
 * （带内对齐门清零横截次级边界）。
 * <p>
 * <b>注册与常规四群系不同轨</b>：经 {@link ProsperityBiomes} 既有扫描配槽机制占 biomeList 槽
 * （首选槽 {@code prosperityBiomeIdStart + 4}，默认 184；被占顺延，上界
 * {@link GTSRBiomeBase#HARD_ID_MAX}=254），但<b>不挂 def 群系表、不进 GenLayer 链 selector
 * 等权名册</b>（4 家不动，plan §3.3/§5）——本群系的平面列由 populate 后置指派写入
 * （{@code ChunkProviderProsperityRuins.onPopulate}，唯一写入通道 {@code BiomePlaneAccess}；
 * 列条件 trunk&gt;0 且 s≥0.7 且 h1≤68 与 {@code GTSRVoronoiRiverField.isSanzuColumn} 同一谓词），
 * 细长形状天然来自"河道核 × 主干带"的交集。空气压缩机在本群系收集三途余汽
 * （{@code ProsperityAirLookup} case SANZU_RIVER）。
 * <p>
 * 构造纪律与四群系同构（参照 {@link BiomeFumaroleSwamp}）：R4 全维度禁雨
 * {@code setDisableRain}（温/雨 0.7/0.8 保留为生态参数）；R1 不向 BiomeDictionary 登记类型、
 * 不调 addSpawnBiome；装饰零趟（VEG 档走名册档表，T7 完整化）。top=河床砾 / filler=石化石
 * （河床语义；wholeBody 自 G4 起全群系统一 prosperityStone，见
 * {@code ChunkProviderProsperityRuins.baseBlockOf}）。草/叶色 = 河谷水汽青灰固定 RGB。
 */
public class BiomeSanzuRiver extends GTSRBiomeBase {

    /** topBlock meta（独立方块族 meta 恒 0，plan §12 修订 2）。 */
    public static final int TOP_META = 0;
    /** fillerBlock meta（filler 复用 prosperityStone，meta 恒 0）。 */
    public static final int FILLER_META = 0;
    /** 群系草色/叶色（河谷水汽青灰，T5 定色，S7a 艺术轮可调）。 */
    public static final int GRASS_COLOR = 0x4F7370;

    public BiomeSanzuRiver(int biomeId) {
        // R4 全维度禁雨：温 0.7（无雪）、湿 0.8（水汽生态参数），降水由 setDisableRain 关死
        super(biomeId, "Sanzu River", new Height(-0.05F, 0.10F), 0.7F, 0.8F);
        this.setDisableRain();
        this.topBlock = BlocksGTSR.prosperityRiverGravel;
        this.field_150604_aj = TOP_META;
        this.fillerBlock = BlocksGTSR.prosperityStone;
        this.theBiomeDecorator.treesPerChunk = 0;
        this.theBiomeDecorator.grassPerChunk = 0;
        this.theBiomeDecorator.flowersPerChunk = 0;
        // R1：不向 BiomeDictionary 登记群系类型（维度专属群系不参与主世界类型检索面）；
        // 不调 addSpawnBiome（维度专属群系）
    }

    /** 补齐 fillerBlock meta（原版只写 top meta，见 {@link ProsperityBiomes#applyFillerMeta}；meta 0 短路）。 */
    @Override
    public void genTerrainBlocks(World world, Random random, Block[] blocks, byte[] metadata, int x, int z,
        double stoneNoise) {
        super.genTerrainBlocks(world, random, blocks, metadata, x, z, stoneNoise);
        ProsperityBiomes.applyFillerMeta(blocks, metadata, this.fillerBlock, FILLER_META);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getBiomeGrassColor(int x, int y, int z) {
        return GRASS_COLOR;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getBiomeFoliageColor(int x, int y, int z) {
        return GRASS_COLOR;
    }
}
