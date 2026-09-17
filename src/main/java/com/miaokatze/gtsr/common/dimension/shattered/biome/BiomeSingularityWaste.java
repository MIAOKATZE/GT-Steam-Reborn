package com.miaokatze.gtsr.common.dimension.shattered.biome;

import net.minecraftforge.common.BiomeDictionary;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

/**
 * 奇点荒原（dim79 群系，dim1 S6a / 04 §1.1 参数表第三列）。
 * <p>
 * 权重 20；top=filler=碎裂黑石（另由 ChunkProviderShatteredLands 撒噪声黑石斑咬入石体）；
 * 温/雨 0.2/0.0 且禁雨（温度 0.2 >= 0.15 不落雪）；天空/雾色暗紫偏灰 0x1A1424。
 */
public class BiomeSingularityWaste extends GTSRBiomeBase {

    /** 群系权重（04 §1.1：50/30/20 之 20）。 */
    public static final int WEIGHT = 20;
    /** 天空/雾色（04 §1.1：暗紫偏灰 0x1A1424）。 */
    public static final int SKY_FOG_COLOR = 0x1A1424;

    public BiomeSingularityWaste(int biomeId) {
        // 禁雨（04 §1.1：仅碎裂浮岛允许降雨）
        super(biomeId, "Singularity Wastes", new Height(0.05F, 0.10F), 0.2F, 0.0F);
        this.setDisableRain();
        this.topBlock = BlocksGTSR.shatteredBlackstone;
        this.fillerBlock = BlocksGTSR.shatteredBlackstone;
        this.theBiomeDecorator.treesPerChunk = 0;
        this.theBiomeDecorator.grassPerChunk = 0;
        this.theBiomeDecorator.flowersPerChunk = 0;
        // plan §S6a：END + DEAD + SPOOKY；不调 addSpawnBiome（维度专属群系）
        BiomeDictionary
            .registerBiomeType(this, BiomeDictionary.Type.END, BiomeDictionary.Type.DEAD, BiomeDictionary.Type.SPOOKY);
    }
}
