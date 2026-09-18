package com.miaokatze.gtsr.common.blocks;

import net.minecraft.block.Block;

/**
 * 方块实例自持持有者：由 loader 注册期写入一次，machine / machine.base / blocks 三方只读，
 * 消除 machine(.base) 与 blocks 包对 loader 包的反向依赖边（包依赖环 #3/#5/#8）。
 * Block 引用只读且注册期即定型，无时序变化。
 */
public final class BlocksGTSR {

    /** 失控奇点方块（BlockLoader.initBlocks 注册期写入）。 */
    public static Block runawaySingularity;

    // ==== dim1 S2：繁荣维度 meta 族方块（BlockLoader.initBlocks 注册期写入）====

    /** 锈变地表 6 meta（0 锈草/1 锈土/2 黄铜沙/3 锈泥炭/4 锈黏/5 锈石；群系 top/filler 挂接）。 */
    public static Block prosperitySurface;

    /** 残骸散点 4 meta（0 轨枕/1 管道/2 铆接板/3 烟囱残段；S4a 结构消费）。 */
    public static Block ruinDebris;

    /** 残缺外壳 3 meta（0 锈蚀/1 积碳/2 碎瓷；S4a/S4b 结构消费）。 */
    public static Block ruinedCasing;

    // ==== dim79 重做（S-B）：破碎之地高硬度方块族（BlockLoader.initBlocks 注册期写入；全部独立
    // Block ID，plan §12 修订 2；hardness ≥1000F / blast 1200 / harvest pickaxe 3，plan §12 修订 3，
    // 分层值登记 dim79-redo-slice-B-report.md。旧 shatteredGrass/shatteredDirt/riftStone/
    // shatteredBlackstone 四方块删除，注册名不保留——无 retrogen，存量 chunk≈0 已裁决）====

    /** 碎核岩（地形主体石：generateTerrain 实心填充至 heightAt，表层之下主体）。 */
    public static Block shatteredCorestone;

    /** 独碑岩（遗迹平台/地标结构块；WorldGenShatteredRuins 平台消费）。 */
    public static Block shatteredMonolith;

    /** 灰烬草毡 top（BiomeAshenPrairie 表层）。 */
    public static Block shatteredAshTop;

    /** 灰烬壤 base（BiomeAshenPrairie filler 段，filler 复用 base）。 */
    public static Block shatteredAshBase;

    /** 渣木草皮 top（BiomeSlagwoodGrove 表层）。 */
    public static Block shatteredSlagTop;

    /** 渣木渣 base（BiomeSlagwoodGrove filler 段，filler 复用 base）。 */
    public static Block shatteredSlagBase;

    /** 琉璃沙 top（BiomeVitreousWaste 表层）。 */
    public static Block shatteredGlassTop;

    /** 琉璃化岩 base（BiomeVitreousWaste filler 段，filler 复用 base）。 */
    public static Block shatteredGlassBase;

    /** 焦油壳 top（BiomeTarBasin 表层）。 */
    public static Block shatteredTarTop;

    /** 焦油砾 base（BiomeTarBasin filler 段，filler 复用 base）。 */
    public static Block shatteredTarBase;

    /** 石刺簇（十字装饰；ShatteredDecorPlacer 消费）。 */
    public static Block shatteredSpikeCluster;

    /** 灰烬枯树干（枯树造型由 ShatteredDecorPlacer 拼装）。 */
    public static Block shatteredAshLog;

    /** 灰烬棘丛（十字装饰；ShatteredDecorPlacer 消费）。 */
    public static Block shatteredAshThorn;

    // ==== dim78 S-A1：四群系自然区方块族（BlockLoader.initBlocks 注册期写入；全部独立 Block ID，
    // plan §12 修订 2；meta 恒 0。既有 prosperitySurface meta0-5 城内语义冻结不动）====

    /** 锈草甸 top（BiomeRustedSteppe 自然区表层，S-A1 前=prosperitySurface meta0）。 */
    public static Block prosperitySteppeTop;

    /** 锈草甸 base（BiomeRustedSteppe 主体/filler，S-A1 前=stone 主体 + prosperitySurface meta1）。 */
    public static Block prosperitySteppeBase;

    /** 铜绿 top（BiomeGearworkForest 自然区表层，S-A1 前=prosperitySurface meta0）。 */
    public static Block prosperityForestTop;

    /** 铜绿 base（BiomeGearworkForest 主体/filler，S-A1 前=stone 主体 + prosperitySurface meta1）。 */
    public static Block prosperityForestBase;

    /** 黄铜丘沙 top（BiomeBrassWastes 自然区表层，S-A1 前=prosperitySurface meta2）。 */
    public static Block prosperityWastesTop;

    /** 黄铜砂岩 base（BiomeBrassWastes 主体/filler，S-A1 前=stone 主体 + prosperitySurface meta5）。 */
    public static Block prosperityWastesBase;

    /** 泥炭沼表土 top（BiomeFumaroleSwamp 自然区表层，S-A1 前=prosperitySurface meta3）。 */
    public static Block prosperitySwampTop;

    /** 泥炭沼泥 base（BiomeFumaroleSwamp 主体/filler，S-A1 前=stone 主体 + prosperitySurface meta4）。 */
    public static Block prosperitySwampBase;

    /** 锈草丛（十字装饰；Steppe/Wastes 草丛，ProsperityDecorPlacer 消费）。 */
    public static Block prosperityTuftRust;

    /** 铜绿草丛（十字装饰；Forest/Swamp 草丛，ProsperityDecorPlacer 消费）。 */
    public static Block prosperityTuftCopper;

    /** 锈树干（树造型由 ProsperityDecorPlacer 拼装）。 */
    public static Block prosperityRustLog;

    /** 锈树叶（冠层由 ProsperityDecorPlacer 拼装；草色 tint 按邻域群系）。 */
    public static Block prosperityRustLeaves;

    private BlocksGTSR() {}
}
