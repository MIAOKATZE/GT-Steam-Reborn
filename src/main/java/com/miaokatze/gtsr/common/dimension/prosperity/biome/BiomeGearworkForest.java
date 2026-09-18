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
 * 齿轮森林（dim78 群系 181，plan S2 / 02 §1.1 参数表；S-A1 起自然区方块独立化）。
 * <p>
 * 权重 30；top=铜绿 top/filler=铜绿 base（独立 Block ID，plan §12 修订 2/4；S-A1 前=
 * prosperitySurface meta0/1）；高度 0.18/0.36；常雨（温/雨 0.8/0.9）；
 * 草色 = 铜绿偏青固定 RGB（S-A1 与旧锈草色脱钩，消费端为草丛/叶 tint 邻域均值）。
 * 锈树散布由 ProsperityDecorPlacer 承接（plan §12 修订 5；原版 decorator 仍置零）。
 */
public class BiomeGearworkForest extends GTSRBiomeBase {

    /** 群系权重（02 §1.1：45/30/15/10 之 30）。 */
    public static final int WEIGHT = 30;
    /** topBlock meta（独立方块族 meta 恒 0，plan §12 修订 2）。 */
    public static final int TOP_META = 0;
    /** fillerBlock meta（filler 复用 base，meta 恒 0，plan §12 修订 4）。 */
    public static final int FILLER_META = 0;
    /** 群系草色/叶色（铜绿偏青，S-A1 定色，S7a 艺术轮可调）。 */
    public static final int GRASS_COLOR = 0x4E8A6E;

    public BiomeGearworkForest(int biomeId) {
        // 常雨：温度 0.8（无雪），湿度 0.9
        super(biomeId, "Gearwork Forest", new Height(0.18F, 0.36F), 0.8F, 0.9F);
        this.topBlock = BlocksGTSR.prosperityForestTop;
        this.field_150604_aj = TOP_META;
        this.fillerBlock = BlocksGTSR.prosperityForestBase;
        this.theBiomeDecorator.treesPerChunk = 0;
        this.theBiomeDecorator.grassPerChunk = 0;
        this.theBiomeDecorator.flowersPerChunk = 0;
        // plan §1.2 S2：FOREST + MAGICAL；不调 addSpawnBiome（维度专属群系）
        BiomeDictionary.registerBiomeType(this, BiomeDictionary.Type.FOREST, BiomeDictionary.Type.MAGICAL);
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
