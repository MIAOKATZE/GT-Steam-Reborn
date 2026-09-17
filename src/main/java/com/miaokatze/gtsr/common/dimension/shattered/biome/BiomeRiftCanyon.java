package com.miaokatze.gtsr.common.dimension.shattered.biome;

import net.minecraftforge.common.BiomeDictionary;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

/**
 * 深裂谷（dim79 群系，dim1 S6a / 04 §1.1 参数表第二列）。
 * <p>
 * 权重 30；top=filler=裂隙石通体（峡谷竖面直接裸露）；温/雨 0.8/0.2 且禁雨；
 * 天空/雾色暗紫偏红 0x38102A；地形与另两群系共享同一 chunk provider（04 §1.1 刻意决策：
 * 破碎感来自噪声本身，差异只体现在表面替换与观感）。
 */
public class BiomeRiftCanyon extends GTSRBiomeBase {

    /** 群系权重（04 §1.1：50/30/20 之 30）。 */
    public static final int WEIGHT = 30;
    /** 天空/雾色（04 §1.1：暗紫偏红 0x38102A）。 */
    public static final int SKY_FOG_COLOR = 0x38102A;

    public BiomeRiftCanyon(int biomeId) {
        // 禁雨（04 §1.1：仅碎裂浮岛允许降雨）
        super(biomeId, "Rift Canyon", new Height(0.30F, 0.60F), 0.8F, 0.2F);
        this.setDisableRain();
        this.topBlock = BlocksGTSR.riftStone;
        this.fillerBlock = BlocksGTSR.riftStone;
        this.theBiomeDecorator.treesPerChunk = 0;
        this.theBiomeDecorator.grassPerChunk = 0;
        this.theBiomeDecorator.flowersPerChunk = 0;
        // plan §S6a：END + DEAD + SPOOKY；不调 addSpawnBiome（维度专属群系）
        BiomeDictionary
            .registerBiomeType(this, BiomeDictionary.Type.END, BiomeDictionary.Type.DEAD, BiomeDictionary.Type.SPOOKY);
    }
}
