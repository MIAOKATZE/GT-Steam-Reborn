package com.miaokatze.gtsr.common.dimension.prosperity.biome;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.common.BiomeDictionary;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 锈蚀草原（dim78 群系 180，plan S2 / 02 §1.1 参数表；S-A1 起自然区方块独立化）。
 * <p>
 * 权重 45；top=锈草甸 top/filler=锈草甸 base（独立 Block ID，plan §12 修订 2/4；S-A1 前=
 * prosperitySurface meta0/1）；高度 0.10/0.28；偶雨（温/雨 0.9/0.2，无雪）；
 * 草色 = 固定锈褐绿 RGB（S-A1 起消费端为草丛/叶 tint 的邻域均值）。
 */
public class BiomeRustedSteppe extends GTSRBiomeBase {

    /** 群系权重（02 §1.1：45/30/15/10 之 45）。 */
    public static final int WEIGHT = 45;
    /** topBlock meta（独立方块族 meta 恒 0，plan §12 修订 2）。 */
    public static final int TOP_META = 0;
    /** fillerBlock meta（filler 复用 base，meta 恒 0，plan §12 修订 4）。 */
    public static final int FILLER_META = 0;
    /** 群系草色/叶色（锈褐绿，S7a 艺术轮可调）。 */
    public static final int GRASS_COLOR = 0x8A7B4A;

    public BiomeRustedSteppe(int biomeId) {
        // 偶雨、无雪：温度 0.9 >= 0.2 不落雪，湿度 0.2 = 偶雨
        super(biomeId, "Rusted Steppe", new Height(0.10F, 0.28F), 0.9F, 0.2F);
        this.topBlock = BlocksGTSR.prosperitySteppeTop;
        this.field_150604_aj = TOP_META;
        this.fillerBlock = BlocksGTSR.prosperitySteppeBase;
        // filler meta 原版 1.7.10 genBiomeTerrain 不写，由 genTerrainBlocks 覆写补齐（见下）
        this.theBiomeDecorator.treesPerChunk = 0;
        this.theBiomeDecorator.grassPerChunk = 0;
        this.theBiomeDecorator.flowersPerChunk = 0;
        // plan §1.2 S2：PLAINS + WASTELAND；不调 addSpawnBiome（维度专属群系）
        BiomeDictionary.registerBiomeType(this, BiomeDictionary.Type.PLAINS, BiomeDictionary.Type.WASTELAND);
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
