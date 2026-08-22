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

    private BlocksGTSR() {}
}
