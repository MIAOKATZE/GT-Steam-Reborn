package com.miaokatze.gtsr.common.dimension.prosperity.biome;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * <b>枯竭河床</b>（dim78 第 6 群系，v1.20.48 P25，用户裁定）——干涸河床带：枯竭龟裂的滩地。
 * <p>
 * <b>形状天然来自 {@code GTSRVoronoiRiverField.strengthAt} 场 × D6 谓词</b>
 * （{@code isDryRiverColumn}）：本群系不做任何独立形状计算，落位即"河床场判干"的列集合
 * ——写平面（populate 后置指派，{@code ChunkProviderProsperityRuins.onPopulate} →
 * {@code BiomePlaneAccess}）与机器侧枯竭气息收集（{@code ProsperityAirLookup} case
 * WITHERED_RIVERBED）消费的是<b>同一份谓词</b>（无第二真值：写什么列、空气压缩机就认什么列）。
 * <p>
 * <b>注册与常规四群系不同轨</b>（与 {@link BiomeSanzuRiver} 同款，v1.20.39 T5 先例）：经
 * {@link ProsperityBiomes} 既有扫描配槽机制占 biomeList 槽（首选槽
 * {@code prosperityBiomeIdStart + 5}，默认 185；被占顺延，上界
 * {@link GTSRBiomeBase#HARD_ID_MAX}=254），但<b>不挂 def 群系表、不进 GenLayer 链 selector
 * 等权名册</b>（4 家不动，plan §3.3/§5）——链身份面（{@code GTSRBiomeAuthority.ordinalAt}
 * 的 Source 采样）结构性解析不到本群系，身份只由上述谓词与平面列承载。
 * <p>
 * 构造纪律与四群系/sanzu 同构（参照 {@link BiomeSanzuRiver}）：R4 全维度禁雨
 * {@code setDisableRain}（温/雨 0.7/0.8 保留为生态参数，取 sanzu 同档）；R1 不向
 * BiomeDictionary 登记类型、不调 addSpawnBiome；装饰零趟（trees/grass/flowers 全 0）；
 * spawnable 列表三清由 {@link GTSRBiomeBase} 构造统一完成（本类不再重复）。top=河床砾
 * （滩料族，与 sanzu 湖滩同族）/ filler=石化石（wholeBody 自 G4 起全群系统一
 * prosperityStone）。草/叶色 = 枯竭土黄固定 RGB（选型：与第七气 withered_breath 的
 * ARGB 0x008a7a52 同源，形成"气色即地色"的可读锚点，并与 sanzu 河谷水汽青灰
 * 0x4F7370 拉开区分）。
 */
public class BiomeWitheredRiverbed extends GTSRBiomeBase {

    /** topBlock meta（独立方块族 meta 恒 0，plan §12 修订 2）。 */
    public static final int TOP_META = 0;
    /** fillerBlock meta（filler 复用 prosperityStone，meta 恒 0）。 */
    public static final int FILLER_META = 0;
    /** 群系草色/叶色（枯竭土黄，P25 定色，S7a 艺术轮可调；选型见类注释）。 */
    public static final int GRASS_COLOR = 0x8A7A52;

    public BiomeWitheredRiverbed(int biomeId) {
        // R4 全维度禁雨：温 0.7（无雪）、湿 0.8（生态参数，取 sanzu 同档），降水由 setDisableRain 关死。
        super(biomeId, "Withered Riverbed", new Height(-0.05F, 0.10F), 0.7F, 0.8F);
        this.setDisableRain();
        this.topBlock = BlocksGTSR.prosperityRiverGravel;
        this.field_150604_aj = TOP_META;
        this.fillerBlock = BlocksGTSR.prosperityStone;
        this.theBiomeDecorator.treesPerChunk = 0;
        this.theBiomeDecorator.grassPerChunk = 0;
        this.theBiomeDecorator.flowersPerChunk = 0;
        // R1：不向 BiomeDictionary 登记群系类型（维度专属群系不参与主世界类型检索面）；
        // 不调 addSpawnBiome（维度专属群系）；spawnable 三清由 GTSRBiomeBase 构造统一完成
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
