package com.miaokatze.gtsr.common.dimension.shattered.biome;

import net.minecraftforge.common.BiomeDictionary;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 碎裂浮岛（dim79 群系，dim1 S6a / 04 §1.1 参数表第一列）。
 * <p>
 * 权重 50；top=碎裂草/filler=碎裂泥土；温/雨 0.6/0.4（允许降雨，雷暴由 provider 强制 = S6b）；
 * 天空/雾色暗紫 0x2A1038（WorldProviderShatteredLands 按群系取色消费）；
 * 岛面平坦化（高度扰动由 ChunkProviderShatteredLands 密度公式统一控制，Height 仅元数据）。
 */
public class BiomeShatteredIsles extends GTSRBiomeBase {

    /** 群系权重（04 §1.1：50/30/20 之 50）。 */
    public static final int WEIGHT = 50;
    /** 天空/雾色（04 §1.1：暗紫 0x2A1038）。 */
    public static final int SKY_FOG_COLOR = 0x2A1038;
    /** 群系草色/叶色（灰紫占位，S7a 艺术轮可调）。 */
    public static final int GRASS_COLOR = 0x6B5E7E;

    public BiomeShatteredIsles(int biomeId) {
        // 允许降雨（04 §1.1 三群系中唯一）
        super(biomeId, "Shattered Isles", new Height(0.10F, 0.20F), 0.6F, 0.4F);
        this.topBlock = BlocksGTSR.shatteredGrass;
        this.fillerBlock = BlocksGTSR.shatteredDirt;
        this.theBiomeDecorator.treesPerChunk = 0;
        this.theBiomeDecorator.grassPerChunk = 0;
        this.theBiomeDecorator.flowersPerChunk = 0;
        // plan §S6a：END + DEAD + SPOOKY；不调 addSpawnBiome（维度专属群系）
        BiomeDictionary
            .registerBiomeType(this, BiomeDictionary.Type.END, BiomeDictionary.Type.DEAD, BiomeDictionary.Type.SPOOKY);
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
