package com.miaokatze.gtsr.common.dimension.framework.structure;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨 chunk 分片几何（<b>P16-B1</b>）。
 * <p>
 * ═══ 为什么这一层在 framework 而不是城包里 ═══
 * 城内结构（{@code prosperity/ruins/city/CitySliceSink} + {@code CityPlan.forEachPlotInChunk}）早就
 * 支持"一座结构横跨多个 chunk、每个 populate chunk 只画与自己相交的那一份"。城外残骸族此前被
 * {@code ≤16×16×12} 的单 chunk 钳制锁死（{@code RuinedMachinePlacer.roll} 的
 * {@code freeX = 16 - sizeX} 收缩、{@code RuinShapes} 静态契约、{@code OutpostTemplateCheck} 判据①），
 * 于是"巨型残骸"在城外放不下。本片把<b>同一套</b>协议泛化给城外：几何半边落在本类
 * （切分 / 相交 / 局部裁剪 / 并集），写入半边落在 {@link ChunkSliceSink}
 * （{@code CitySliceSink} 改为它的子类，城内一条路不换实现）。<b>全仓只有这一套切片器</b>——
 * 城外巨构不复制第二份 clip/intersect 代码，这一点由 {@code OutpostTemplateCheck} 的成对断言
 * 直接调用本类来钉（断言与生产共用同一份几何，不会出现"断言自己算一套"）。
 * <p>
 * ═══ 成对断言（判据 G7）用的三个量 ═══
 * <ol>
 * <li>{@link Slice#withinSliceLimits()}：<b>每个分片</b> {@code ≤16×16×12}（X/Z 由 chunk 栅格天然 bounded，
 * 高度 {@code sizeY} 是全模板共享的那一档 ⇒ 这条真正把"高"限死）；</li>
 * <li>{@link #unionBounds(Slice[])}：<b>各分片实心格的并集</b>必须恰好等于<b>显式申报的总 bbox</b>
 * （申报值见 {@code RuinedColossusShapes.Colossus#declaredChunksX()} 与各模板的 sizeX/Y/Z）；</li>
 * <li>{@link #distinctChunkOffsets(Slice[], boolean)}：申报的跨 chunk 数 == 实得分片数，
 * 且巨构至少跨 2 个 chunk（否则"跨 chunk"这条判据可以在一张空表上绿）。</li>
 * </ol>
 * 零 Minecraft 依赖（纯 String 字符盘 + int），故 {@code tools/dim1} 的离线驱动可直接实例化。
 */
public final class ChunkSpans {

    /** chunk 边长（块）——与 {@code ChunkClampedSink}/{@code ChunkSliceSink} 的 {@code x>>4} 同一口径。 */
    public static final int CHUNK_BLOCKS = 16;

    /**
     * 单个分片允许的最大高度（块）。旧口径里"footprint ≤16×16、高 ≤12"是<b>整座结构</b>的上界
     * （{@code RuinShapes} 静态契约 / {@code OutpostTemplateCheck} 判据①）；本片把它读成
     * <b>分片</b>的上界——总 bbox 的高与分片的高在实现上是同一个数（层序不分段），
     * 于是"分片 ≤12"既保住了旧判据的数值，又不再限制总 bbox 的 X/Z 跨度。
     */
    public static final int MAX_SLICE_HEIGHT = 12;

    private ChunkSpans() {}

    /**
     * 一个分片：模板局部坐标下、落在某个 chunk 栅格单元里的那一份。
     * <p>
     * 只有<b>含实心格</b>（既非 {@code ' '} 不触碰、也非 {@code '.'} 清空）的单元才会被产出，
     * 因此"申报了 N 个 chunk 却只有一片有东西"这种形态会直接体现在分片数上（判据 3）。
     */
    public static final class Slice {

        /** 相对模板原点所在 chunk 的偏移（0 起，按 chunk 栅格计）。 */
        public final int chunkDx;
        public final int chunkDz;
        /** 该片在模板局部坐标下的起点与尺寸（宽/深天然 ≤{@link #CHUNK_BLOCKS}）。 */
        public final int minX;
        public final int minZ;
        public final int sizeX;
        public final int sizeZ;
        /** 该片高度 = 模板总高（分片只切 X/Z，不切 Y）。 */
        public final int sizeY;
        /** 本片<b>实心格</b>的包围盒（模板局部坐标；{@code solidChars==0} 时未定义，不会有这种片）。 */
        public final int usedMinX;
        public final int usedMaxX;
        public final int usedMinY;
        public final int usedMaxY;
        public final int usedMinZ;
        public final int usedMaxZ;
        public final int solidChars;

        Slice(int chunkDx, int chunkDz, int minX, int minZ, int sizeX, int sizeZ, int sizeY, int usedMinX, int usedMaxX,
            int usedMinY, int usedMaxY, int usedMinZ, int usedMaxZ, int solidChars) {
            this.chunkDx = chunkDx;
            this.chunkDz = chunkDz;
            this.minX = minX;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.sizeY = sizeY;
            this.usedMinX = usedMinX;
            this.usedMaxX = usedMaxX;
            this.usedMinY = usedMinY;
            this.usedMaxY = usedMaxY;
            this.usedMinZ = usedMinZ;
            this.usedMaxZ = usedMaxZ;
            this.solidChars = solidChars;
        }

        /** 判据 G7 第一条：这一片本身是否 {@code ≤16×16×12}。 */
        public boolean withinSliceLimits() {
            return this.sizeX >= 1 && this.sizeX <= CHUNK_BLOCKS
                && this.sizeZ >= 1
                && this.sizeZ <= CHUNK_BLOCKS
                && this.sizeY >= 1
                && this.sizeY <= MAX_SLICE_HEIGHT;
        }

        /** 本片的实心格是否落在自己的框内（自洽性；越界即数据坏，不该存在）。 */
        public boolean usedInsideSelf() {
            return this.usedMinX >= this.minX && this.usedMaxX < this.minX + this.sizeX
                && this.usedMinZ >= this.minZ
                && this.usedMaxZ < this.minZ + this.sizeZ
                && this.usedMinY >= 0
                && this.usedMaxY < this.sizeY;
        }
    }

    /** 块坐标 → chunk 坐标（{@code >>4} 对负坐标同样是 floor 语义，与两个 sink 的判定一致）。 */
    public static int chunkOf(int block) {
        return block >> 4;
    }

    /** 跨度 size（块，≥1）在 chunk 栅格上最多占几个 chunk。 */
    public static int chunksAcross(int size) {
        return (size + CHUNK_BLOCKS - 1) / CHUNK_BLOCKS;
    }

    /**
     * 落在 {@code chunk}（含 X 或 Z 轴）一侧、可能覆盖到本 chunk 的<b>锚点</b>回扫格数。
     * <p>
     * 锚点结构的世界原点由 {@link #anchorFreeChunks()} 那扇抖动窗掷出，故本 chunk 会被
     * "原点所在 chunk ≤ 自己且 ≥ 自己 - 回扫格数"的锚点覆盖；两侧对称取值即可（多扫几格只会多做几次
     * 纯函数重放，不漏扫才是硬要求）。
     */
    public static int reachBack(int size) {
        return chunksAcross(size + anchorFreeBlocks());
    }

    /**
     * 跨片结构的原点抖动窗（块）：{@code CHUNK_BLOCKS - 1}，即"原点可以落在锚点 chunk 内的任意一列"。
     * 取满一个 chunk 而不是收缩到 0——收缩到 0 就是旧锁死的等价形态（总 bbox 与 chunk 栅格同相 ⇒
     * 全世界的巨构都对齐到 chunk 角，是看得出来的机器味）。
     */
    public static int anchorFreeBlocks() {
        return CHUNK_BLOCKS - 1;
    }

    /** 总 bbox 是否与 chunk (chunkX,chunkZ) 相交。 */
    public static boolean intersects(int originX, int sizeX, int originZ, int sizeZ, int chunkX, int chunkZ) {
        return overlaps(originX, sizeX, chunkX) && overlaps(originZ, sizeZ, chunkZ);
    }

    private static boolean overlaps(int origin, int size, int chunk) {
        final int base = chunk << 4;
        return origin <= base + CHUNK_BLOCKS - 1 && origin + size - 1 >= base;
    }

    /**
     * 局部坐标下本 chunk 要画的 {@code dx}（或 {@code dz}）下界；与 {@link #localMax(int, int, int)}
     * 配对使用，{@code min > max} 即不相交（调用方跳过）。
     */
    public static int localMin(int origin, int size, int chunk) {
        return clamp(0, size - 1, (chunk << 4) - origin);
    }

    /** 局部坐标上界（含）。 */
    public static int localMax(int origin, int size, int chunk) {
        return clamp(0, size - 1, ((chunk << 4) + CHUNK_BLOCKS - 1) - origin);
    }

    private static int clamp(int lo, int hi, int v) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /**
     * 把模板字符盘按 chunk 栅格切成实心分片（{@code layers[0]} = 顶层，与
     * {@code RuinedMachineShapes}/{@code CityVariants}/{@code RuinShapes} 同一层序）。
     * 单 chunk 结构在这里就是"恰好一片"——成对断言因此对旧条目同样成立，不需要另开一条判据。
     */
    public static Slice[] slice(String[][] layers, int sizeX, int sizeY, int sizeZ) {
        final List<Slice> out = new ArrayList<>();
        for (int cx = 0; cx < chunksAcross(sizeX); cx++) {
            for (int cz = 0; cz < chunksAcross(sizeZ); cz++) {
                final int minX = cx * CHUNK_BLOCKS;
                final int minZ = cz * CHUNK_BLOCKS;
                final int w = Math.min(CHUNK_BLOCKS, sizeX - minX);
                final int d = Math.min(CHUNK_BLOCKS, sizeZ - minZ);
                int solid = 0;
                // 跑动极值：max 侧从最小值起、min 侧从最大值起（起反了就永远取不到点，
                // 后果由 RuinedColossusShapes 的静态契约与本片首轮自检实测抓到——见 B1 回执失败迭代）
                int umx = Integer.MIN_VALUE;
                int umxn = Integer.MAX_VALUE;
                int umy = Integer.MIN_VALUE;
                int umyn = Integer.MAX_VALUE;
                int umz = Integer.MIN_VALUE;
                int umzn = Integer.MAX_VALUE;
                for (int ly = 0; ly < sizeY; ly++) {
                    final int y = sizeY - 1 - ly;
                    final String[] rows = layers[ly];
                    for (int dz = 0; dz < d; dz++) {
                        final String row = rows[minZ + dz];
                        for (int dx = 0; dx < w; dx++) {
                            if (!isSolid(row.charAt(minX + dx))) {
                                continue;
                            }
                            solid++;
                            final int lx = minX + dx;
                            final int lz = minZ + dz;
                            umxn = Math.min(umxn, lx);
                            umx = Math.max(umx, lx);
                            umyn = Math.min(umyn, y);
                            umy = Math.max(umy, y);
                            umzn = Math.min(umzn, lz);
                            umz = Math.max(umz, lz);
                        }
                    }
                }
                if (solid > 0) {
                    out.add(new Slice(cx, cz, minX, minZ, w, d, sizeY, umxn, umx, umyn, umy, umzn, umz, solid));
                }
            }
        }
        return out.toArray(new Slice[0]);
    }

    /** 实心格：既不是"不触碰"（{@code ' '}）也不是"清空气"（{@code '.'}）。 */
    public static boolean isSolid(char c) {
        return c != ' ' && c != '.';
    }

    /**
     * 各分片实心格的并集包围盒（模板局部坐标，闭区间）；{@code {minX,maxX,minY,maxY,minZ,maxZ}}，
     * 无实心格时返回 {@code null}。判据 G7 第二条拿它跟<b>显式申报的总 bbox</b>
     * （{@code 0 .. sizeX-1} 等）逐轴比相等 ⇒ "申报了 24 宽但两头都是空格"这类谎报必红。
     */
    public static int[] unionBounds(Slice[] slices) {
        if (slices.length == 0) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (final Slice s : slices) {
            minX = Math.min(minX, s.usedMinX);
            maxX = Math.max(maxX, s.usedMaxX);
            minY = Math.min(minY, s.usedMinY);
            maxY = Math.max(maxY, s.usedMaxY);
            minZ = Math.min(minZ, s.usedMinZ);
            maxZ = Math.max(maxZ, s.usedMaxZ);
        }
        return new int[] { minX, maxX, minY, maxY, minZ, maxZ };
    }

    /** 并集是否恰好铺满申报的总 bbox（六面都吃到，且不出框）。 */
    public static boolean unionEqualsBox(Slice[] slices, int sizeX, int sizeY, int sizeZ) {
        final int[] u = unionBounds(slices);
        return u != null && u[0] == 0
            && u[1] == sizeX - 1
            && u[2] == 0
            && u[3] == sizeY - 1
            && u[4] == 0
            && u[5] == sizeZ - 1;
    }

    /** 分片覆盖到的不同 chunk 偏移数（{@code xAxis=true} 数 X 轴）；判据"至少跨 2 chunk"用。 */
    public static int distinctChunkOffsets(Slice[] slices, boolean xAxis) {
        final List<Integer> seen = new ArrayList<>();
        for (final Slice s : slices) {
            final int v = xAxis ? s.chunkDx : s.chunkDz;
            if (!seen.contains(v)) {
                seen.add(v);
            }
        }
        return seen.size();
    }
}
