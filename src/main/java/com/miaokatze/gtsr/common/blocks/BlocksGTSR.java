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

    // ==== P17-SB1：dim78 木/花/草名册（12 个，全部非表层 top；注册在 BlockLoader 自然族段之后，
    // plan §0-Q3 新增自有 log/leaf 族 3 档 + §2-3 自有花；meta 恒 0，独立 Block ID 同 §12 修订 2；
    // 世界生成消费方＝S-B2 身份档分流，本片只注册与出资产，不接任何生成逻辑）====

    /** 铜绿树干（GearworkForest 身份档树；rust 档既有 ProsperityRustLog 不在此列）。 */
    public static Block prosperityCopperLog;

    /** 铜绿树冠（side/top 双图标；草色 tint 同锈叶口径；S-B2 冠层消费）。 */
    public static Block prosperityCopperLeaves;

    /** 黄铜树干（BrassWastes 身份档树）。 */
    public static Block prosperityBrassLog;

    /** 黄铜树冠（side/top 双图标）。 */
    public static Block prosperityBrassLeaves;

    /** 泥炭沼树干（FumaroleSwamp 身份档树）。 */
    public static Block prosperityMarshLog;

    /** 沼地树冠（side/top 双图标）。 */
    public static Block prosperityMarshLeaves;

    /** 锈花（十字装饰；S-B2 Steppe 花密度消费；dim78 原花设施数=0）。 */
    public static Block prosperityFlowerRust;

    /** 铜绿霜花（十字装饰；S-B2 Forest 花）。 */
    public static Block prosperityFlowerPatina;

    /** 黄铜金盏（十字装饰；S-B2 Wastes 花）。 */
    public static Block prosperityFlowerBrass;

    /** 沼地兰（十字装饰；S-B2 Swamp 花）。 */
    public static Block prosperityFlowerMarsh;

    /** 苔薹草（十字装饰，密矮；S-B2 Swamp/Forest 草密度消费）。 */
    public static Block prosperityTuftSedge;

    /** 刚毛草（十字装饰，疏高带穗；S-B2 Steppe/Wastes 草密度消费）。 */
    public static Block prosperityTuftBristle;

    // ==== P17-S-B2：dim78 沙/砂砾族（3 个，全部非表层 top；B 档走 BlockProsperityNaturalBase 吃贴图名
    // ⇒ 零新 Java 类。用途限定：荒漠装饰趟铺沙 + S-C 河床料；不进 SurfaceGate 名册，DIM78_SIZE 恒 5）====

    /** 细硅沙（荒漠铺沙主料；S-B2 沙砾趟消费，S-C 河床边岸可用）。 */
    public static Block prosperitySilicaSand;

    /** 粗粒沙（荒漠铺沙第二样，风成砾粒；S-B2 沙砾趟按 1/3 概率替换细沙）。 */
    public static Block prosperityCoarseSand;

    /** 河床砂砾（S-C 河床主料；S-B2 先在荒漠砾石斑上真实消费一次，避免"注册了但没人读"）。 */
    public static Block prosperityRiverGravel;

    private BlocksGTSR() {}
}
