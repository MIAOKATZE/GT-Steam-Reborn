package com.miaokatze.gtsr.common.dimension.shattered.biome;

import net.minecraftforge.common.BiomeDictionary;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

/**
 * 琉璃荒漠（dim79 群系 192，重做 S-B3，plan §5 S-B3 + §12 修订 2/3）。
 * <p>
 * 权重 20；top=琉璃沙/filler=琉璃化岩（独立 Block ID，全部 ≥1000F）；温/雨 1.0/0.0 且禁雨
 * （温度 1.0 >= 0.15 不落雪）；天空/雾色琉璃青 0x28323C。
 */
public class BiomeVitreousWaste extends GTSRBiomeBase {

    /** 群系权重（S-B3：40/30/20/10 之 20）。 */
    public static final int WEIGHT = 20;
    /** 天空/雾色（琉璃青）。 */
    public static final int SKY_FOG_COLOR = 0x28323C;
    /** topBlock meta（独立方块族 meta 恒 0，plan §12 修订 2）。 */
    public static final int TOP_META = 0;
    /** fillerBlock meta（filler 复用 base，meta 恒 0）。 */
    public static final int FILLER_META = 0;

    public BiomeVitreousWaste(int biomeId) {
        // 禁雨（琉璃化热荒意象）
        super(biomeId, "Vitreous Waste", new Height(0.10F, 0.15F), 1.0F, 0.0F);
        this.setDisableRain();
        this.topBlock = BlocksGTSR.shatteredGlassTop;
        this.field_150604_aj = TOP_META;
        this.fillerBlock = BlocksGTSR.shatteredGlassBase;
        this.theBiomeDecorator.treesPerChunk = 0;
        this.theBiomeDecorator.grassPerChunk = 0;
        this.theBiomeDecorator.flowersPerChunk = 0;
        // plan §S6a 承袭：END + DEAD + SPOOKY；不调 addSpawnBiome（维度专属群系）
        BiomeDictionary
            .registerBiomeType(this, BiomeDictionary.Type.END, BiomeDictionary.Type.DEAD, BiomeDictionary.Type.SPOOKY);
    }
}
