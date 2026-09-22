package com.miaokatze.gtsr.common.dimension.framework.genlayer;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.GenLayerSmooth;
import net.minecraft.world.gen.layer.GenLayerVoronoiZoom;
import net.minecraft.world.gen.layer.GenLayerZoom;
import net.minecraft.world.gen.layer.IntCache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

/**
 * dim78/dim79 GenLayer 群系分布链的组合器/门面（切片 A：纯新增核心，B1/B2 才接线消费）。
 * <p>
 * 链结构（复用 vanilla 三个纯 id 操作层，蓝本 {@code GenLayer.initializeAllBiomeGenerators}
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
 * selectModeOrRandom 只从输入里挑；voronoi 的 {@code & 255} 对 ≤254 的 id 是恒等），故直接组合复用，
 * 不自写等价类。
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
 * <b>线程模型</b>：与 vanilla WorldChunkManager 的 GenLayer 相同——{@code getInts} 会改写层内
 * {@code chunkSeed} 实例态，<b>非线程安全</b>；每维（每 WorldChunkManager）各持一条链即可，
 * 跨线程并发查询由 B1 接线方规避。
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
     * <b>P17 S-A 由 4 递进到 5</b>（需求："单个群系略大一些（现在生成的太破碎了，一小块一小块的）"）。
     * vanilla 骨架该位是 {@code b0 = 4}（{@code GenLayer.java:54}）——本仓不跟 vanilla 取值，跟的是
     * "成片尺度"这条观感判据：取证 {@code plan/tmp/p17-a/A-biome-scale.md} §1.3 实测平均连通域
     * 41.9 → 149.1 chunk（×3.6）、孤岛率 0.170 → 0.025pp、chunk 份额对等权基准的最大偏差
     * 0.725 → 1.967pp（容差 5.0pp，仍绿）。取 6 会把份额偏差推到 4.700pp（余量 0.3pp）且四窗红，
     * 故按裁定档停在 5。
     * <p>
     * <b>这一常量的消费面</b>（改它=同时改这些，不存在"只改一处调用点"的余地）：manager 身份面
     * {@code GTSRWorldChunkManager} 构链、城门 {@code CityPlanner.bandIndexAt} 本地重建链、
     * 高度面 {@code ProsperityTerrainProfile.heightAt} 经 {@link GTSRGenLayerRosterFace} 重建的同一条链、
     * 以及全部离线判据（{@code GTSRGenLayerSelfTest} / {@code BiomeZoneCheck} /
     * {@code BiomeBandHierarchyCheck}）——同一个常量 ⇒ "三元逐位同一"结构上不可能漂移（BBH A1/C1）。
     */
    public static final int DEFAULT_ZOOM_LEVELS = 5;

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
     *                   {@link #DEFAULT_ZOOM_LEVELS} = 5 ⇒ selector 格 = 32×32 粗格 = 128 方块
     *                   = <b>8×8 chunk</b>，即"单个群系"的典型线性尺度）
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
        final GenLayer zoomed = GenLayerZoom.magnify(ZOOM_SALT, selector, zoomLevels);
        final GenLayer smoothed = new GenLayerSmooth(SMOOTH_SALT, zoomed);
        this.coarseRoot = smoothed;
        this.fineRoot = new GenLayerVoronoiZoom(VORONOI_SALT, smoothed);
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
     * 返回数组是 IntCache 瞬态存储（vanilla WorldChunkManager 同款约定）：下一次本链任何采样
     * 调用都可能复用并覆写它——需要跨调用保留请自行拷贝。
     *
     * @param x 粗层 X（1 单位 = 4 方块；方块坐标换算 {@code blockX >> 2}）
     * @param z 粗层 Z（同上）
     * @return 行主序 {@code out[i + j * width]}；id ∈ biomeIds（防御钳制后保证）
     */
    public int[] coarseInts(int x, int z, int width, int height) {
        IntCache.resetIntCache();
        return sanitize(this.coarseRoot.getInts(x, z, width, height), width * height, "coarseInts");
    }

    /**
     * 粗层单点（方块坐标入参，内部 {@code >> 2}；chunk 代表点 = <b>chunk 中心块</b>
     * {@code ((chunkX << 4) + 8, (chunkZ << 4) + 8)}，B1 定稿口径——见类注释坐标系契约）。
     * 实现为 1×1 小窗 getInts，不求值整图。
     */
    public int biomeAtCoarse(int blockX, int blockZ) {
        IntCache.resetIntCache();
        final int[] out = this.coarseRoot.getInts(blockX >> COARSE_BLOCK_SHIFT, blockZ >> COARSE_BLOCK_SHIFT, 1, 1);
        return sanitizeOne(out[0], "biomeAtCoarse");
    }

    /**
     * 细层窗口采样（1:1 方块级，voronoi 有机边界；vanilla {@code biomeIndexLayer} 同口径）。
     * 返回数组同样是 IntCache 瞬态存储（见 {@link #coarseInts}）。
     */
    public int[] fineInts(int blockX, int blockZ, int width, int height) {
        IntCache.resetIntCache();
        return sanitize(this.fineRoot.getInts(blockX, blockZ, width, height), width * height, "fineInts");
    }

    /**
     * 细层单点（方块坐标 1:1）。实现为 1×1 小窗 getInts，不求值整图。
     */
    public int biomeAtFine(int blockX, int blockZ) {
        IntCache.resetIntCache();
        final int[] out = this.fineRoot.getInts(blockX, blockZ, 1, 1);
        return sanitizeOne(out[0], "biomeAtFine");
    }

    /**
     * 输出防御钳制：越界 id 替换为 {@code biomeIds[0]} 并每链只 log 一次
     * （zoom/smooth/voronoi 只复制父层值，此路径理论不可达——纯防御，不抛异常）。
     * <p>
     * 只走 {@code length} 个<b>逻辑</b>格：IntCache 返回的底数组长度是 intCacheSize
     * （可能大于请求尺寸），层只写前 {@code width*height} 格——尾部是陈旧垃圾，
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
}
