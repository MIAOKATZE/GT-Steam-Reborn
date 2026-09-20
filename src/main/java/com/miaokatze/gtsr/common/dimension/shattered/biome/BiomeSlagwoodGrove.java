package com.miaokatze.gtsr.common.dimension.shattered.biome;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

/**
 * 渣木林（dim79 群系 191，重做 S-B3，plan §5 S-B3 + §12 修订 2/3）。
 * <p>
 * 权重 30；top=渣木草皮/filler=渣木渣（独立 Block ID，全部 ≥1000F）；温/雨 0.7/0.6 保留为
 * 生态参数（R4 全维度禁雨：setDisableRain）；
 * 天空/雾色渣绿 0x262B20；枯树（灰烬枯树干）在此群系意象最密——密度由 ShatteredDecorPlacer
 * 统一控制，本类不做 per-biome 装饰参数。
 */
public class BiomeSlagwoodGrove extends GTSRBiomeBase {

    /** 群系权重（S-B3：40/30/20/10 之 30）。 */
    public static final int WEIGHT = 30;
    /** 天空/雾色（渣绿）。 */
    public static final int SKY_FOG_COLOR = 0x262B20;
    /** topBlock meta（独立方块族 meta 恒 0，plan §12 修订 2）。 */
    public static final int TOP_META = 0;
    /** fillerBlock meta（filler 复用 base，meta 恒 0）。 */
    public static final int FILLER_META = 0;

    public BiomeSlagwoodGrove(int biomeId) {
        super(biomeId, "Slagwood Grove", new Height(0.15F, 0.25F), 0.7F, 0.6F);
        this.setDisableRain();
        this.topBlock = BlocksGTSR.shatteredSlagTop;
        this.field_150604_aj = TOP_META;
        this.fillerBlock = BlocksGTSR.shatteredSlagBase;
        this.theBiomeDecorator.treesPerChunk = 0;
        this.theBiomeDecorator.grassPerChunk = 0;
        this.theBiomeDecorator.flowersPerChunk = 0;
        // R1：不向 BiomeDictionary 登记群系类型（维度专属群系不参与主世界类型检索面）；
        // 不调 addSpawnBiome（维度专属群系）
    }
}
