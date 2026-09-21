package com.miaokatze.gtsr.common.dimension.framework.structure;

/**
 * 跨 chunk「交集切片」写入协议（<b>P16-B1 从 {@code prosperity/ruins/city/CitySliceSink} 抽出的共同层</b>）。
 * <p>
 * 协议内容一字未改，只是换了属主：坐标在当前 populate chunk 内 → 转发给下游；否则<b>静默吸收</b>
 * （返回 true）——这是"每个 chunk 只画与自己相交的那一份"这条渲染协议的预期路径，<b>不是</b>越界事故，
 * 所以不进 {@link ChunkClampedSink} 的越界丢弃计数（城链"零越界告警"的构造论据与此同源）。
 * <p>
 * 为什么必须有一层共同实现：城外此前放不下跨 chunk 结构（{@code ≤16×16×12} 单 chunk 锁死 +
 * {@link ChunkClampedSink} 的强制钳制），城内一直走 {@code CitySliceSink}。任务包口径是
 * <b>泛化复用，禁止复制粘贴出第二套切片器</b>，故本协议上收 framework：
 * <ul>
 * <li>城内：{@code city/CitySliceSink extends ChunkSliceSink}（名字与调用点全部保留，
 * {@code CityDeterminismCheck} 的切片协议断言仍打在同一个类名上）；</li>
 * <li>城外巨构：{@code RuinedMachinePlacer} 的跨片分支直接 new 本类；</li>
 * <li>几何半边（切分 / 相交 / 局部裁剪 / 并集申报）在 {@link ChunkSpans}，成对断言与生产共用它。</li>
 * </ul>
 * 链序（游戏侧，城与城外同形）：放置写入 → 本类（切片）→ {@code CityBlockResolver}（键解析）
 * → {@code CountingSink}（落块真值）→ {@link ChunkClampedSink}（最终守卫）→ World。
 * <p>
 * 返回 {@code true} 的语义边界（{@link BlockSink} 契约第 3 条）：这里"接受"指的是
 * <b>"这一格由别的 chunk 负责，本 chunk 不报错也不回滚"</b>，不代表世界上已经有了这块——
 * 那块由邻 chunk 自己的 populate 写。因此本类只能包在 {@code CountingSink} <b>外侧</b>
 * （切片先过滤、计数只数真落进本 chunk 的块），否则会把邻槽的写入算进本槽的落块真值。
 */
public class ChunkSliceSink implements BlockSink {

    private final BlockSink parent;
    private final int chunkX;
    private final int chunkZ;

    public ChunkSliceSink(BlockSink parent, int chunkX, int chunkZ) {
        this.parent = parent;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    /** 本切片服务的 chunk（断言与子类读用）。 */
    public final int chunkX() {
        return this.chunkX;
    }

    public final int chunkZ() {
        return this.chunkZ;
    }

    protected final BlockSink parent() {
        return this.parent;
    }

    /** 这一格是否归本 chunk 画（{@code >>4} 与 {@link ChunkClampedSink} 同一 floor 语义）。 */
    public final boolean owns(int x, int z) {
        return (x >> 4) == this.chunkX && (z >> 4) == this.chunkZ;
    }

    @Override
    public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
        if (!owns(x, z) || y < 0 || y > 255) {
            return true; // 其他 chunk populate 的交集切片职责——静默吸收，非越界事故
        }
        return this.parent.setBlock(x, y, z, block, meta, flags);
    }
}
