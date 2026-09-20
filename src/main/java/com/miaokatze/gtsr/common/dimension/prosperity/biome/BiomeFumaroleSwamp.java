package com.miaokatze.gtsr.common.dimension.prosperity.biome;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 起雾沼泽（dim78 群系 183，plan S2 / 02 §1.1 参数表；02 文档旧称"汽雾沼泽"；S-A1 起自然区方块独立化）。
 * <p>
 * 权重 10；top=泥炭沼表土 top/filler=泥炭沼泥 base（独立 Block ID，plan §12 修订 2/4；S-A1 前=
 * prosperitySurface meta3/4）；高度 -0.08/0.16；无雨（R4 全维度禁雨：温/雨 0.8/0.9 保留为生态参数）；
 * 草色 = 压暗雾浊橄榄固定 RGB（S-A1 与森林旧值脱钩）。终日雾属 Provider 视觉（后续切片），不在群系参数内。
 */
public class BiomeFumaroleSwamp extends GTSRBiomeBase {

    /** 群系权重（02 §1.1：45/30/15/10 之 10）。 */
    public static final int WEIGHT = 10;
    /** topBlock meta（独立方块族 meta 恒 0，plan §12 修订 2）。 */
    public static final int TOP_META = 0;
    /** fillerBlock meta（filler 复用 base，meta 恒 0，plan §12 修订 4）。 */
    public static final int FILLER_META = 0;
    /** 群系草色/叶色（压暗雾浊橄榄，S-A1 定色，S7a 艺术轮可调）。 */
    public static final int GRASS_COLOR = 0x4A5732;

    public BiomeFumaroleSwamp(int biomeId) {
        // R4 全维度禁雨：温 0.8（无雪），湿 0.9 保留为生态参数，降水由 setDisableRain 关死
        super(biomeId, "Fumarole Swamp", new Height(-0.08F, 0.16F), 0.8F, 0.9F);
        this.setDisableRain();
        this.topBlock = BlocksGTSR.prosperitySwampTop;
        this.field_150604_aj = TOP_META;
        this.fillerBlock = BlocksGTSR.prosperitySwampBase;
        this.theBiomeDecorator.treesPerChunk = 0;
        this.theBiomeDecorator.grassPerChunk = 0;
        this.theBiomeDecorator.flowersPerChunk = 0;
        // R1：不向 BiomeDictionary 登记群系类型（维度专属群系不参与主世界类型检索面）；
        // 不调 addSpawnBiome（维度专属群系）
    }

    /** 补齐 fillerBlock meta（原版只写 top meta，见 {@link ProsperityBiomes#applyFillerMeta}）。 */
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
