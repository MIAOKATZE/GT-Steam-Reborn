package com.miaokatze.gtsr.common.dimension.shattered.biome;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

/**
 * 灰烬草原（dim79 群系 190，重做 S-B3，plan §5 S-B3 + §12 修订 2/3）。
 * <p>
 * 权重 40；top=灰烬草毡/filler=灰烬壤（独立 Block ID，全部 ≥1000F）；温/雨 0.7/0.5 保留为
 * 生态参数（R4 全维度禁雨：setDisableRain；强制雷暴已随 R4 移除）；
 * 天空/雾色灰烬灰 0x2E2A28（WorldProviderShatteredLands
 * 按群系取色消费）；地形与 decor 消费同全维度统一管线（ShatteredTerrainProfile + ShatteredDecorPlacer）。
 */
public class BiomeAshenPrairie extends GTSRBiomeBase {

    /** 群系权重（S-B3：40/30/20/10 之 40）。 */
    public static final int WEIGHT = 40;
    /** 天空/雾色（灰烬灰）。 */
    public static final int SKY_FOG_COLOR = 0x2E2A28;
    /** topBlock meta（独立方块族 meta 恒 0，plan §12 修订 2）。 */
    public static final int TOP_META = 0;
    /** fillerBlock meta（filler 复用 base，meta 恒 0）。 */
    public static final int FILLER_META = 0;

    public BiomeAshenPrairie(int biomeId) {
        // R4 全维度禁雨：温/雨 0.7/0.5 保留为生态参数，降水由 setDisableRain 关死
        super(biomeId, "Ashen Prairie", new Height(0.10F, 0.20F), 0.7F, 0.5F);
        this.setDisableRain();
        this.topBlock = BlocksGTSR.shatteredAshTop;
        this.field_150604_aj = TOP_META;
        this.fillerBlock = BlocksGTSR.shatteredAshBase;
        this.theBiomeDecorator.treesPerChunk = 0;
        this.theBiomeDecorator.grassPerChunk = 0;
        this.theBiomeDecorator.flowersPerChunk = 0;
        // R1：不向 BiomeDictionary 登记群系类型（维度专属群系不参与主世界类型检索面）；
        // 不调 addSpawnBiome（维度专属群系）
    }
}
