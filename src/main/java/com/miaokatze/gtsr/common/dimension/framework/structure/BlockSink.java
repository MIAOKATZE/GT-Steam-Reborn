package com.miaokatze.gtsr.common.dimension.framework.structure;

/**
 * 结构方块写入协议（dim1 S4a，plan D1 / §1.2 S4a）：三种入口（ChunkClampedSink 世界生成钳制 /
 * DirectWorldSink 指令直写 / RasterSink 离线栅格化）与 StructureBuilder 的统一面向。
 * <p>
 * 协议要点：
 * <ul>
 * <li>{@code block} 为<b>不透明方块句柄</b>：游戏内是 {@code net.minecraft.block.Block} 实例
 * （放置方经解析后传入），离线预览（RasterSink + tools/dim1 驱动）传纯 String 键（如
 * {@code "gtsr:RuinedCasing"}）——本接口因此零 Minecraft 依赖，RasterSink 可在无游戏运行时实例化；</li>
 * <li>实现方决定钳制/直写/栅格化语义；返回 {@code true} 表示该写入被接受，{@code false} 表示被丢弃
 * （如 ChunkClampedSink 越界），调用方无需回滚（丢弃即协议语义）；</li>
 * <li><b>P7（plan §2.1 L5「禁止失败计成功」）返回值的契约强度</b>：返回值是"这一次写入到底落没落"
 * 的<b>唯一真值来源</b>——放置方（{@code RuinedMachinePlacer}/{@code ProsperityOutpostPlacer} 与
 * P8 起的废墟族）必须把它经 {@link PlacementGate.CountingSink} 汇总成"真实落块数"，再交给
 * {@link PlacementGate.Permit#commit(int)} 决定本次放置是否算成功；<b>禁止</b>在 sink 全拒的场景下
 * 仍向上返回"已生成"。实现方也不得把"已接受"与"已落盘"混为一谈：{@code false} 必须意味着
 * 这一格没有变成方块。</li>
 * <li>meta 恒在 0..15 域内由调用方保证；flags 采用原版 setBlock 语义（populate 期 2，直写 3）。</li>
 * </ul>
 */
public interface BlockSink {

    /** populate 期 flag（不触发邻块更新，光照由 populate 收尾集中处理）。 */
    int FLAG_POPULATE = 2;

    /** 直写 flag（触发邻块更新，/gtsr structure 通道用）。 */
    int FLAG_DIRECT = 3;

    /**
     * 写入一个方块。
     *
     * @param x     世界坐标 x
     * @param y     世界坐标 y（0..255）
     * @param z     世界坐标 z
     * @param block 不透明方块句柄（游戏内 Block 实例 / 离线 String 键）
     * @param meta  方块 meta（0..15）
     * @param flags setBlock flag（{@link #FLAG_POPULATE} / {@link #FLAG_DIRECT}）
     * @return true = 接受；false = 丢弃（越界/坐标非法/句柄不合法）
     */
    boolean setBlock(int x, int y, int z, Object block, int meta, int flags);
}
