package com.miaokatze.gtsr.common.dimension.framework.genlayer;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.world.gen.layer.GenLayer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

/**
 * dim78/dim79 GenLayer 群系分布链的组合器/门面（切片 A：纯新增核心，B1/B2 才接线消费）。
 * <p>
 * 链结构（保持 vanilla 三个纯 id 操作层的采样规则，蓝本 {@code GenLayer.initializeAllBiomeGenerators}
 * {@code GenLayer.java:32-100} 的 b0=4 zoom + Smooth + VoronoiZoom 骨架；<b>骨架同形、zoom 次数
 * 本仓自成档位</b>，见 {@link #DEFAULT_ZOOM_LEVELS}）：
 * 
 * <pre>
 *   粗层（1:4 比例尺，身份面）：
 *     GTSRGenLayerSelector（等权轮盘，1:(16·2^zoomLevels) 方块格）
 *       → GenLayerZoom.magnify(1000L, selector, zoomLevels)   // 角点直拷/边二选一/中心多数（GenLayerZoom.java:29-47）
 *       → GenLayerSmooth(1000L, …)                             // 对向邻相等采纳，消棋盘噪点（GenLayerSmooth.java:26-63）
 *   细层（1:1 方块级，出图面）：
 *     GenLayerVoronoiZoom(10L, 粗层)                           // 4×4 块种子化距离取角值（GenLayerVoronoiZoom.java:39-48）
 * </pre>
 * 
 * vanilla 三层均为 public 构造、只复制/平滑父层 int id（zoom/smooth 的 selectRandom/
 * selectModeOrRandom 只从输入里挑；voronoi 的 {@code & 255} 对 ≤254 的 id 是恒等）。
 * 本链保留其种子/选值规则，但私有采样层不使用共享 IntCache。
 * <p>
 * <b>坐标系契约（B1 接线依据）</b>：
 * <ul>
 * <li>粗层 1 比例尺单位 = 4 方块（vanilla {@code WorldChunkManager.areBiomesViable:206-208} 同口径，
 * 即 {@code blockX >> 2}，算术右移对负坐标即向下取整）；</li>
 * <li>{@link #coarseInts(int, int, int, int)} 的 x/z/w/h 均为粗层单位；</li>
 * <li>{@link #biomeAtCoarse(int, int)} 收<b>方块坐标</b>，内部 {@code >> 2}——chunk 代表点采样即
 * {@code biomeAtCoarse((chunkX << 4) + 8, (chunkZ << 4) + 8)}（<b>chunk 中心块</b>，等价粗层格
 * {@code (chunkX << 2) + 2, (chunkZ << 2) + 2}——chunk 覆盖的 4×4 粗格的中心格；B1 定稿口径，
 * 取代 A3 契约草案的西北角 {@code (chunkX << 4)}：中心格比西北角格更能代表整 chunk 的主导群系，
 * 且不受 chunk 边界处粗层格切分的影响）；</li>
 * <li>{@link #fineInts(int, int, int, int)} / {@link #biomeAtFine(int, int)} 收<b>方块坐标</b>
 * （1:1，voronoi 有机边界；vanilla {@code biomeIndexLayer} 同口径 WorldChunkManager.java:82）。</li>
 * </ul>
 * 纯函数语义：所有层逐格 {@code initChunkSeed(x,z)}，同 (worldSeed, x, z) 恒同值，与查询窗口无关
 * （B1 三面同解依赖）。
 * <p>
 * <b>种子礼仪</b>：构造器内对粗/细两个链根各调一次 {@code initWorldGenSeed(worldSeed)}（沿
 * parent 链传播到 selector；幂等，vanilla GenLayer.java:97-98 同款双调）。{@code worldSeed}
 * 入参应已含维度域分离——B1 接线建议传 {@code worldSeed ^ def.getSeedSalt()}，避免两维同种子
 * 同群系表时链输出逐位相同。
 * <p>
 * <b>线程模型</b>：原生层会改写 {@code chunkSeed}，IntCache 也由整个 JVM 共享。
 * 本门面在链实例监视器内完成整次查询，并在解锁前拷出窗口。各采样层仅使用私有数组，
 * 不分配或重置共享 IntCache；原生/其他模组的池重置不会污染本链，本链也不会回收其活跃数组。
 * <p>
 * <b>输出 id 防御钳制</b>：zoom/smooth/voronoi 理论上只复制不发明新 id；每条公共出口仍校验
 * 输出 id ∈ 入参 biomeIds 集合，越界值替换为 {@code biomeIds[0]} 并 <b>log 一次</b>
 * （每链实例一次，不抛异常、不打断世界生成）。
 */
public final class GTSRGenLayerChain {

    private static final Logger LOG = LogManager.getLogger("GTSR.GenLayerChain");

    /**
     * 粗层 selector 格的 zoom 次数。
     * <p>
     * <b>历史档位</b>：P17 S-A 由 4 递进到 5（需求："单个群系略大一些（现在生成的太破碎了，一小块
     * 一块的）"；取证 {@code plan/tmp/p17-a/A-biome-scale.md} §1.3：平均连通域 41.9 → 149.1 chunk、
     * 孤岛率 0.170 → 0.025、份额偏差 0.725 → 1.967pp——当时"取 6 推到 4.700pp"的读数是 S-A 代的
     * 固定窗口径，已被 S-G 派生窗（m=1024 selector 格）取代，不再构成本档约束）。
     * vanilla 骨架该位是 {@code b0 = 4}（{@code GenLayer.java:54}）——本仓不跟 vanilla 取值，跟的是
     * "成片尺度"观感判据。
     * <p>
     * <b>P18（v1.20.39 G7）由 5 递进到 7</b>（需求："单一群系延伸 ≥10 倍"——5→7 = 线性 ×4、
     * 面积 ×16，满足面积口径）。红线（plan §3.5/§9）：派生窗（S-G 口径，selector 格数不随档缩水）
     * 下等权份额偏差 &lt;5pp；初跑读数见 {@code plan/tmp/p18-t6-prered.md}。判据侧写死窗/写死档的
     * 名单（BiomeZoneCheck 384 窗、P17TerrainReliefCheck EXPECT_ZOOM_LEVELS=5 等）同文件红清单，
     * 重钉归 T8。
     * <p>
     * <b>这一常量的消费面</b>（改它=同时改这些，不存在"只改一处调用点"的余地）：manager 身份面
     * {@code GTSRWorldChunkManager} 构链、城门 {@code CityPlanner.bandIndexAt} 本地重建链、
     * 高度面 {@code ProsperityTerrainProfile.heightAt} 经 {@link GTSRGenLayerRosterFace} 重建的同一条链、
     * 以及全部离线判据（{@code GTSRGenLayerSelfTest} / {@code BiomeZoneCheck} /
     * {@code BiomeBandHierarchyCheck}）——同一个常量 ⇒ "三元逐位同一"结构上不可能漂移（BBH A1/C1）。
     */
    public static final int DEFAULT_ZOOM_LEVELS = 7;

    /** 粗层比例尺：1 粗层格 = 4 方块（voronoi 的 4× 放大倍数，GenLayerVoronoiZoom.java:21-24）。 */
    public static final int COARSE_BLOCK_SCALE = 4;

    /**
     * 方块坐标 → 粗层格坐标的算术右移位数 = log2({@link #COARSE_BLOCK_SCALE}) = 2
     * （<b>不是</b> scale 本身——自测首跑曾把 {@code >> 4} 当 {@code >> 2} 用，
     * 被"单点 vs 整窗"逐位对拍当场抓出）。算术右移对负方块坐标即向下取整，与
     * vanilla {@code WorldChunkManager} 的 {@code x >> 2} 同口径。
     */
    public static final int COARSE_BLOCK_SHIFT = Integer.numberOfTrailingZeros(COARSE_BLOCK_SCALE);

    /** zoomLevels 合法域上界（防接线手滑把世界生成放大到不可收拾）。 */
    public static final int MAX_ZOOM_LEVELS = 8;

    /** 链盐（取值对齐 vanilla GenLayer.java:74/94/96 的 1000L/1000L/10L 家族）。 */
    private static final long ZOOM_SALT = 1000L;
    private static final long SMOOTH_SALT = 1000L;
    private static final long VORONOI_SALT = 10L;

    /** 入参群系 id 表快照（构造期防御拷贝，此后只读）。 */
    private final int[] biomeIds;
    private final int zoomLevels;
    /** 粗层链根（zoom+smooth 之后的 1:4 身份面）。 */
    private final GenLayer coarseRoot;
    /** 细层链根（voronoi 之后的 1:1 出图面；其 parent 即粗层根）。 */
    private final GenLayer fineRoot;
    /** 输出 id 合法集合查表（[0,255] 索引，构造期一次性填充）。 */
    private final boolean[] allowedIds;
    /** 防御钳制日志闸（每链实例只 log 一次）。 */
    private final AtomicBoolean invalidLogged = new AtomicBoolean(false);

    /**
     * @param worldSeed 世界种子（建议已掺维度域分离盐，见类注释）
     * @param biomeIds  候选群系 id 表（≥1 项、每项 ∈ [0, 254]；等权轮盘）
     */
    public GTSRGenLayerChain(long worldSeed, int[] biomeIds) {
        this(worldSeed, biomeIds, DEFAULT_ZOOM_LEVELS);
    }

    /**
     * @param zoomLevels 粗层相对 1:4 比例尺的 zoom 次数（0 = selector 直接当 1:4 面；
     *                   vanilla 口径 4 ⇒ selector 格 = 16×16 粗格 = 64 方块；本仓默认
     *                   {@link #DEFAULT_ZOOM_LEVELS} = 7 ⇒ selector 格 = 128×128 粗格 = 512 方块
     *                   = <b>32×32 chunk</b>，即"单个群系"的典型线性尺度；面积口径为 zoom5 的
     *                   16 倍，满足"单一群系延伸 ≥10 倍"）
     * @throws IllegalArgumentException biomeIds 空/越界，或 zoomLevels ∉ [0, {@link #MAX_ZOOM_LEVELS}]
     */
    public GTSRGenLayerChain(long worldSeed, int[] biomeIds, int zoomLevels) {
        if (biomeIds == null || biomeIds.length == 0) {
            throw new IllegalArgumentException("biomeIds must not be null or empty");
        }
        for (int i = 0; i < biomeIds.length; i++) {
            final int id = biomeIds[i];
            if (id < 0 || id > GTSRBiomeBase.HARD_ID_MAX) {
                throw new IllegalArgumentException(
                    "biomeIds[" + i + "]=" + id + " out of [0," + GTSRBiomeBase.HARD_ID_MAX + "]");
            }
        }
        if (zoomLevels < 0 || zoomLevels > MAX_ZOOM_LEVELS) {
            throw new IllegalArgumentException("zoomLevels must be in [0," + MAX_ZOOM_LEVELS + "]: " + zoomLevels);
        }
        this.biomeIds = biomeIds.clone();
        this.zoomLevels = zoomLevels;
        this.allowedIds = new boolean[256];
        for (final int id : this.biomeIds) {
            this.allowedIds[id] = true;
        }

        final GTSRGenLayerSelector selector = new GTSRGenLayerSelector(
            GTSRGenLayerSelector.SELECTOR_BASE_SEED,
            this.biomeIds);
        GenLayer zoomed = selector;
        for (int level = 0; level < zoomLevels; level++) {
            zoomed = new OwnedZoom(ZOOM_SALT + level, zoomed);
        }
        final GenLayer smoothed = new OwnedSmooth(SMOOTH_SALT, zoomed);
        this.coarseRoot = smoothed;
        this.fineRoot = new OwnedVoronoi(VORONOI_SALT, smoothed);
        // 标准 GenLayer 初始化礼仪：对两链根各调一次 initWorldGenSeed（沿 parent 传播到 selector；
        // 幂等——vanilla initializeAllBiomeGenerators:97-98 同款双调）
        this.coarseRoot.initWorldGenSeed(worldSeed);
        this.fineRoot.initWorldGenSeed(worldSeed);
    }

    /** 本链 zoom 次数（诊断/自检用）。 */
    public int zoomLevels() {
        return this.zoomLevels;
    }

    /** 本链候选群系 id 表快照（诊断/自检用；每次调用防御拷贝）。 */
    public int[] biomeIds() {
        return this.biomeIds.clone();
    }

    /**
     * 粗层窗口采样（1:4 身份面）。
     * <p>
     * The complete sampling transaction holds this chain's monitor to serialize mutable GenLayer
     * seed state and private scratch. Return an owned logical-window snapshot before unlocking.
     * Neither this chain nor its selector touches Minecraft's process-global IntCache.
     *
     * @param x 粗层 X（1 单位 = 4 方块；方块坐标换算 {@code blockX >> 2}）
     * @param z 粗层 Z（同上）
     * @return 行主序 {@code out[i + j * width]}；id ∈ biomeIds（防御钳制后保证）
     */
    public int[] coarseInts(int x, int z, int width, int height) {
        synchronized (this) {
            return Arrays.copyOf(
                sanitize(this.coarseRoot.getInts(x, z, width, height), width * height, "coarseInts"),
                width * height);
        }
    }

    /**
     * 粗层单点（方块坐标入参，内部 {@code >> 2}；chunk 代表点 = <b>chunk 中心块</b>
     * {@code ((chunkX << 4) + 8, (chunkZ << 4) + 8)}，B1 定稿口径——见类注释坐标系契约）。
     * 实现为 1×1 小窗 getInts，不求值整图。
     */
    public int biomeAtCoarse(int blockX, int blockZ) {
        synchronized (this) {
            final int[] out = this.coarseRoot.getInts(blockX >> COARSE_BLOCK_SHIFT, blockZ >> COARSE_BLOCK_SHIFT, 1, 1);
            return sanitizeOne(out[0], "biomeAtCoarse");
        }
    }

    /**
     * 细层窗口采样（1:1 方块级，voronoi 有机边界；vanilla {@code biomeIndexLayer} 同口径）。
     * 返回数组是独立窗口快照（见 {@link #coarseInts}）。
     */
    public int[] fineInts(int blockX, int blockZ, int width, int height) {
        synchronized (this) {
            return Arrays.copyOf(
                sanitize(this.fineRoot.getInts(blockX, blockZ, width, height), width * height, "fineInts"),
                width * height);
        }
    }

    /**
     * 细层单点（方块坐标 1:1）。实现为 1×1 小窗 getInts，不求值整图。
     */
    public int biomeAtFine(int blockX, int blockZ) {
        synchronized (this) {
            final int[] out = this.fineRoot.getInts(blockX, blockZ, 1, 1);
            return sanitizeOne(out[0], "biomeAtFine");
        }
    }

    /**
     * 输出防御钳制：越界 id 替换为 {@code biomeIds[0]} 并每链只 log 一次
     * （zoom/smooth/voronoi 只复制父层值，此路径理论不可达——纯防御，不抛异常）。
     * <p>
     * 只走 {@code length} 个<b>逻辑</b>格：私有 scratch 可能大于请求尺寸，
     * 层只写前 {@code width*height} 格——尾部是陈旧垃圾，
     * 不能进钳制扫描（首跑曾把未写尾部当越界 id 误报）。
     */
    private int[] sanitize(int[] ids, int length, String where) {
        for (int i = 0; i < length; i++) {
            ids[i] = sanitizeOne(ids[i], where);
        }
        return ids;
    }

    private int sanitizeOne(int id, String where) {
        if (id >= 0 && id <= 255 && this.allowedIds[id]) {
            return id;
        }
        if (this.invalidLogged.compareAndSet(false, true)) {
            LOG.warn(
                "GenLayer chain produced biome id {} outside input set {} at {} — clamped to {} (once per chain)",
                id,
                Arrays.toString(this.biomeIds),
                where,
                this.biomeIds[0]);
        }
        return this.biomeIds[0];
    }

    /**
     * GTSR-only sampling stages. These implement the documented zoom / smooth / nearest-jittered-
     * corner rules, while retaining Minecraft's GenLayer seed and selection methods. They do not
     * extend or invoke the pooled vanilla stages, or modify other mods' layers. Native parity is
     * checked by tools/dim1/GenLayerIsolationCheck.java; do not change RNG call order or tie rules.
     */
    private abstract static class OwnedLayer extends GenLayer {

        // At most 16 KiB per stage; exceptionally large queries do not enlarge retained scratch.
        private static final int MAX_RETAINED_CELLS = 4096;
        private int[] retained = new int[0];

        OwnedLayer(long salt, GenLayer parent) {
            super(salt);
            this.parent = parent;
        }

        final int[] output(int size) {
            if (size > MAX_RETAINED_CELLS) return new int[size];
            if (retained.length < size) retained = new int[size];
            return retained;
        }
    }

    private static final class OwnedZoom extends OwnedLayer {

        OwnedZoom(long salt, GenLayer parent) {
            super(salt, parent);
        }

        @Override
        public int[] getInts(int x, int z, int width, int height) {
            final int offsetX = x & 1, offsetZ = z & 1;
            final int parentWidth = ((width + offsetX + 1) >> 1) + 1;
            final int parentHeight = ((height + offsetZ + 1) >> 1) + 1;
            final int[] input = parent.getInts(x >> 1, z >> 1, parentWidth, parentHeight);
            final int[] out = output(width * height);
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    final int localX = column + offsetX, localZ = row + offsetZ;
                    final int cell = (localZ >> 1) * parentWidth + (localX >> 1);
                    final int northwest = input[cell];
                    final int target = row * width + column;
                    if (((localX | localZ) & 1) == 0) {
                        out[target] = northwest;
                        continue;
                    }
                    // A tile draws south, then east, then the centre, in that order. Earlier draws
                    // still have to be consumed when only its eastern or centre cell is requested.
                    initChunkSeed(((x >> 1) + (localX >> 1)) << 1, ((z >> 1) + (localZ >> 1)) << 1);
                    final int southwest = input[cell + parentWidth];
                    final int south = selectRandom(northwest, southwest);
                    if ((localX & 1) == 0) {
                        out[target] = south;
                        continue;
                    }
                    final int northeast = input[cell + 1];
                    final int east = selectRandom(northwest, northeast);
                    out[target] = (localZ & 1) == 0 ? east
                        : selectModeOrRandom(northwest, northeast, southwest, input[cell + parentWidth + 1]);
                }
            }
            return out;
        }
    }

    private static final class OwnedSmooth extends OwnedLayer {

        OwnedSmooth(long salt, GenLayer parent) {
            super(salt, parent);
        }

        @Override
        public int[] getInts(int x, int z, int width, int height) {
            final int stride = width + 2;
            final int[] input = parent.getInts(x - 1, z - 1, stride, height + 2);
            final int[] out = output(width * height);
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    final int center = (row + 1) * stride + column + 1;
                    final boolean horizontal = input[center - 1] == input[center + 1];
                    final boolean vertical = input[center - stride] == input[center + stride];
                    int value = input[center];
                    if (horizontal && vertical) {
                        initChunkSeed(x + column, z + row);
                        value = nextInt(2) == 0 ? input[center - 1] : input[center - stride];
                    } else if (horizontal) {
                        value = input[center - 1];
                    } else if (vertical) {
                        value = input[center - stride];
                    }
                    out[row * width + column] = value;
                }
            }
            return out;
        }
    }

    private static final class OwnedVoronoi extends OwnedLayer {

        // Two jitter coordinates per parent corner. Large calls, as above, are not retained.
        private double[] retainedJitter = new double[0];

        OwnedVoronoi(long salt, GenLayer parent) {
            super(salt, parent);
        }

        @Override
        public int[] getInts(int x, int z, int width, int height) {
            final int shiftedX = x - 2, shiftedZ = z - 2;
            final int offsetX = shiftedX & 3, offsetZ = shiftedZ & 3;
            // Include the complete requested rectangle, including odd-sized unaligned windows.
            final int parentWidth = ((width + offsetX + 3) >> 2) + 1;
            final int parentHeight = ((height + offsetZ + 3) >> 2) + 1;
            final int[] input = parent.getInts(shiftedX >> 2, shiftedZ >> 2, parentWidth, parentHeight);
            final int jitterSize = 2 * parentWidth * parentHeight;
            final double[] jitter;
            if (jitterSize > 8192) {
                jitter = new double[jitterSize];
            } else {
                if (retainedJitter.length < jitterSize) retainedJitter = new double[jitterSize];
                jitter = retainedJitter;
            }
            for (int row = 0; row < parentHeight; row++) {
                for (int column = 0; column < parentWidth; column++) {
                    initChunkSeed(((shiftedX >> 2) + column) << 2, ((shiftedZ >> 2) + row) << 2);
                    final int at = 2 * (row * parentWidth + column);
                    jitter[at] = (nextInt(1024) / 1024.0D - 0.5D) * 3.6D;
                    jitter[at + 1] = (nextInt(1024) / 1024.0D - 0.5D) * 3.6D;
                }
            }
            final int[] out = output(width * height);
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    final int localX = column + offsetX, localZ = row + offsetZ;
                    final int northwest = (localZ >> 2) * parentWidth + (localX >> 2);
                    final int northeast = northwest + 1, southwest = northwest + parentWidth;
                    final int southeast = southwest + 1;
                    final int dx = localX & 3, dz = localZ & 3;
                    final double nw = distance(jitter, northwest, dx, dz, false, false);
                    final double ne = distance(jitter, northeast, dx, dz, true, false);
                    final double sw = distance(jitter, southwest, dx, dz, false, true);
                    final double se = distance(jitter, southeast, dx, dz, true, true);
                    // Equal distances deliberately fall through to the southeast corner.
                    final int nearest = nw < ne && nw < sw && nw < se ? northwest
                        : ne < nw && ne < sw && ne < se ? northeast
                            : sw < nw && sw < ne && sw < se ? southwest : southeast;
                    out[row * width + column] = input[nearest];
                }
            }
            return out;
        }

        private static double distance(double[] jitter, int corner, int x, int z, boolean east, boolean south) {
            final double dx = x - (jitter[2 * corner] + (east ? 4.0D : 0.0D));
            final double dz = z - (jitter[2 * corner + 1] + (south ? 4.0D : 0.0D));
            return dz * dz + dx * dx;
        }
    }
}
