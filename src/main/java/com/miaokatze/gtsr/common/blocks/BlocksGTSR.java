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

    // ==== dim1 S6a：破碎之地方块族（BlockLoader.initBlocks 注册期写入）====

    /** 碎裂草（碎裂浮岛 top；群系草色 tint 消费）。 */
    public static Block shatteredGrass;

    /** 碎裂泥土（碎裂浮岛 filler）。 */
    public static Block shatteredDirt;

    /** 裂隙石（深裂谷 top/filler 通体）。 */
    public static Block riftStone;

    /** 碎裂黑石（奇点荒原 top/filler + 裂隙层 y<20 基底 + 荒原黑石斑）。 */
    public static Block shatteredBlackstone;

    private BlocksGTSR() {}
}
