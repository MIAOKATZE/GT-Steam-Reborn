package com.miaokatze.gtsr.common.dimension.prosperity.biome;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 黄铜荒漠（dim78 群系 182，plan S2 / 02 §1.1 参数表；S-A1 起自然区方块独立化）。
 * <p>
 * 权重 15；top=黄铜丘沙 top/filler=黄铜砂岩 base（独立 Block ID，plan §12 修订 2/4；S-A1 前=
 * prosperitySurface meta2/5）；高度 0.14/0.32；无雨（温/雨 2.0/0.0 + setDisableRain，
 * 原版 desert 同款口径）；草色 = 黄铜色固定 RGB。
 */
public class BiomeBrassWastes extends GTSRBiomeBase {

    /** 群系权重（02 §1.1：45/30/15/10 之 15）。 */
    public static final int WEIGHT = 15;
    /** topBlock meta（独立方块族 meta 恒 0，plan §12 修订 2）。 */
    public static final int TOP_META = 0;
    /** fillerBlock meta（filler 复用 base，meta 恒 0，plan §12 修订 4）。 */
    public static final int FILLER_META = 0;
    /** 群系草色/叶色（黄铜色，S7a 艺术轮可调）。 */
    public static final int GRASS_COLOR = 0xB08D3E;

    public BiomeBrassWastes(int biomeId) {
        // 无雨：温 2.0 / 雨 0.0 + 禁雨（原版 desert 口径），无雪（温 >= 0.2）
        super(biomeId, "Brass Wastes", new Height(0.14F, 0.32F), 2.0F, 0.0F);
        this.topBlock = BlocksGTSR.prosperityWastesTop;
        this.field_150604_aj = TOP_META;
        this.fillerBlock = BlocksGTSR.prosperityWastesBase;
        this.setDisableRain();
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
