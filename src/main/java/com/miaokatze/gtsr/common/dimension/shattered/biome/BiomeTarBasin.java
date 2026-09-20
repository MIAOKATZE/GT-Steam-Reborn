package com.miaokatze.gtsr.common.dimension.shattered.biome;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

/**
 * 焦油洼地（dim79 群系 193，重做 S-B3，plan §5 S-B3 + §12 修订 2/3）。
 * <p>
 * 权重 10；top=焦油壳/filler=焦油砾（独立 Block ID，全部 ≥1000F）；温/雨 0.8/0.9 保留为
 * 生态参数（R4 全维度禁雨：setDisableRain，湿热洼地意象）；
 * 天空/雾色焦油褐黑 0x160F0B。无自然流体（维度不放水/焦油液，任务口径）。
 */
public class BiomeTarBasin extends GTSRBiomeBase {

    /** 群系权重（S-B3：40/30/20/10 之 10）。 */
    public static final int WEIGHT = 10;
    /** 天空/雾色（焦油褐黑）。 */
    public static final int SKY_FOG_COLOR = 0x160F0B;
    /** topBlock meta（独立方块族 meta 恒 0，plan §12 修订 2）。 */
    public static final int TOP_META = 0;
    /** fillerBlock meta（filler 复用 base，meta 恒 0）。 */
    public static final int FILLER_META = 0;

    public BiomeTarBasin(int biomeId) {
        super(biomeId, "Tar Basin", new Height(0.05F, 0.10F), 0.8F, 0.9F);
        this.setDisableRain();
        this.topBlock = BlocksGTSR.shatteredTarTop;
        this.field_150604_aj = TOP_META;
        this.fillerBlock = BlocksGTSR.shatteredTarBase;
        this.theBiomeDecorator.treesPerChunk = 0;
        this.theBiomeDecorator.grassPerChunk = 0;
        this.theBiomeDecorator.flowersPerChunk = 0;
        // R1：不向 BiomeDictionary 登记群系类型（维度专属群系不参与主世界类型检索面）；
        // 不调 addSpawnBiome（维度专属群系）
    }
}
