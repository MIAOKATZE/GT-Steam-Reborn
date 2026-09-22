package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.HashMap;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerRosterFace;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * 树密度连续场（P19 U6，plan §G「树密度平滑+渗色修复」）：把每群系三层树期望
 * （灌木 1/{@code shrubDenom}、普通 {@code treeRolls/treeChanceDenom}、巨树 1/{@code megaDenom}，
 * 即 {@link ProsperityDecorPlacer#TREE_TIERS_BY_ROSTER} / {@link ProsperityDecorPlacer#VEG_TIERS_BY_ROSTER}
 * 当前的 5 元数值）作为<b>粗格标量场</b>，经 11×11 smoothstep 紧支核（半径 5 粗格 = 20 方块，
 * 与 {@code ProsperityTerrainProfile.ampAt} 同构）加权成连续场。两个消费面：
 * <ul>
 * <li><b>树趟掷骰</b>（{@link ProsperityDecorPlacer} 三层门）：群系边界整 chunk 硬切
 * （用户 image5「树界过硬」）的根因是"chunk 中心单点取档"——密度场使跨界 chunk 的树期望
 * 连续过渡（40-60 格渐变带，同 ampAt 口径）；</li>
 * <li><b>植被档 argmax</b>（{@link ProsperityWorldGenerator#vegRosterIndex} 渗色修复 K1）：
 * 河擦角 chunk 的档由密度场主导选出，不再"中心列是 sanzu ⇒ 整块按错档装饰"。</li>
 * </ul>
 * <p>
 * <b>纯函数</b>：输出只依赖 (worldSeed, x, z) 与静态档表；粗格身份经
 * {@link GTSRGenLayerRosterFace} 一条短命链求值，线程私有缓存（与 ampAt 同纪律：GenLayer 链与
 * vanilla IntCache 均非线程安全，缓存以 ThreadLocal 结构上杜绝跨线程共享；上限整清，值可重算）。
 * <p>
 * <b>盐纪律（申报一处有意偏离任务包字面）</b>：粗格身份查询必须复用
 * {@link ProsperityTerrainProfile#CHAIN_SEED_SALT}——{@code GTSRGenLayerRosterFace} 明言盐值不同
 * 得到的是<b>与身份面无关的排列</b>，密度场若换盐则"树界"不再跟随实机可见的群系界（本片目标本身）。
 * 任务包「新盐族隔离」由此落实为：<b>零共享派生态</b>——核数组/ThreadLocal 缓存全部自含、不引
 * Profile 私有件、不进 ampAt/seedSalt 的任何盐空间；密度场自身不含独立噪声（它是身份的纯折叠，
 * 任何独立混合项都会破坏与群系界的对齐），故无新盐可加。
 * <p>
 * <b>档值零缓存（判据友好关键）</b>：粗格缓存只存<b>身份</b>（int），三层数值每次由档表<b>现读</b>
 * ——离线判据的 ANTI 换档臂（{@code P17VegetationFrequencyCheck} 荒漠行进程内换档）与批4 重钉
 * 立即可见，不存在 ampAt「账本时点假设」的密度版。
 * <p>
 * <b>均匀区构造性退化</b>：核内全同身份 ⇒ 直接返回该身份的档表行值<b>逐位</b>（跳过 Σw 归一的
 * ulp 级误差）⇒ 群系腹地的密度 == 档表原值，均匀区期望与改造前逐位一致；群系腹地之外的值域
 * 是在场行值的凸组合（核权重归一，Σw = 1）⇒ 平滑只过渡、不越界。
 * <p>
 * <b>掷骰门与档表概率的精确重合</b>：门分母 {@link #DICE_GATE} = 96000 = 96 × 1000，96 = 档表
 * 全部分母 {8,2,3,6} ∪ {2,4} ∪ {24,8,16,32} 的最小公倍数 ⇒ 每个均匀区行值映射成<b>精确整数门</b>
 * （{@code Math.round} 吸收 1/3 类双精度 ulp 噪声：0.333…×96000 = 31999.99… → 32000），
 * 门概率 == 档表概率（有理数意义逐位）；混合区门概率 = 凸组合的最近整数近似（连续，无跳变）。
 * <p>
 * <b>成本</b>：身份缓存未命中时才调 {@link GTSRVoronoiRiverField#isSanzuColumn}（含 heightAt 整链，
 * 贵）；每粗格一次、跨 chunk 内核共享（同 ampAt 的摊销形状），命中后每次 fold 仅 69 次查表乘加。
 */
public final class DensityField {

    /** 三层枚举：灌木档（chunk 级 1/N 亮点下层）。 */
    public static final int LAYER_SHRUB = 0;
    /** 三层枚举：普通档（{@code treeRolls × 1/treeChanceDenom}）。 */
    public static final int LAYER_NORMAL = 1;
    /** 三层枚举：巨树档（独立盐 chunk 级 1/N 亮点）。 */
    public static final int LAYER_MEGA = 2;

    /**
     * 密度掷骰门分母 = 96000 = 96 × 1000。96 是档表全部分母
     * （普通 {8,2,3,6}、灌木 {2,4}、巨树 {24,8,16,32}）的最小公倍数 ⇒ 均匀区每行
     * {@link #gateOf} 都是精确整数、门概率与档表概率逐位重合（见类注释）。
     */
    public static final int DICE_GATE = 96000;

    /** smoothstep 核半径（<b>粗格</b>单位；与 ampAt 同为 5 ⇒ 核直径 11 粗格 = 44 方块）。 */
    private static final int KERNEL_RADIUS = 5;

    /** 每线程每 seed 的粗格身份缓存上限（同 {@code ProsperityTerrainProfile.AMP_CELL_CACHE_CAP} 口径）。 */
    private static final int CELL_CACHE_CAP = 16384;

    /**
     * dim78 名册的最大下标（静态一次由 {@link BiomeId} 枚举派生，不手写 4）：argmax 桶数组的界，
     * 未来名册扩员（新 selector 成员/新平面档）时桶自动随枚举加宽，不构成第二真值。
     */
    private static final int MAX_ROSTER_INDEX;
    static {
        int max = -1;
        for (final BiomeId id : BiomeId.values()) {
            if (SurfaceGate.DIM78.equals(id.dimKey()) && id.rosterIndex() > max) {
                max = id.rosterIndex();
            }
        }
        MAX_ROSTER_INDEX = max;
    }

    /** 核参与格点（i²+j² &lt; r²）——本类<b>自含一份</b>，与 Profile 私有核数组零共享。 */
    private static final int[] KERNEL_DX;
    /** 见 {@link #KERNEL_DX}。 */
    private static final int[] KERNEL_DZ;
    /** 归一核权（Σw = 1；全同身份场由均匀短路兜底逐位，混合场为行值凸组合）。 */
    private static final double[] KERNEL_W;

    static {
        final int r = KERNEL_RADIUS;
        final int rr = r * r;
        int n = 0;
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                if (i * i + j * j < rr) {
                    n++;
                }
            }
        }
        KERNEL_DX = new int[n];
        KERNEL_DZ = new int[n];
        KERNEL_W = new double[n];
        double wSum = 0.0D;
        int k = 0;
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                final int d2 = i * i + j * j;
                if (d2 >= rr) {
                    continue;
                }
                // w = s(1 − d/r)、s(t) = t²(3−2t)（与 ampAt 同式）
                final double t = 1.0D - Math.sqrt(d2) / r;
                final double w = t * t * (3.0D - 2.0D * t);
                KERNEL_DX[k] = i;
                KERNEL_DZ[k] = j;
                KERNEL_W[k] = w;
                wSum += w;
                k++;
            }
        }
        for (k = 0; k < n; k++) {
            KERNEL_W[k] /= wSum;
        }
    }

    /**
     * 粗格<b>身份</b>缓存（线程私有；外层 key = worldSeed，内层 = (cellX, cellZ) 打包 long）。
     * 只缓存 {@link #effectiveRosterAt} 的 int 结果，<b>不</b>缓存任何档值（见类注释「档值零缓存」）。
     */
    private static final ThreadLocal<HashMap<Long, HashMap<Long, Integer>>> CELL_ROSTER_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    private DensityField() {}

    /** (x,z) 列的灌木档密度连续场（chunk 级期望概率 ∈ [0, 1/2]）。 */
    public static double shrubDensityAt(long worldSeed, int x, int z) {
        return fold(worldSeed, x, z, LAYER_SHRUB);
    }

    /** (x,z) 列的普通档密度连续场（chunk 级期望树数 ∈ [0, 1]）。 */
    public static double normalDensityAt(long worldSeed, int x, int z) {
        return fold(worldSeed, x, z, LAYER_NORMAL);
    }

    /** (x,z) 列的巨树档密度连续场（chunk 级期望概率 ∈ [0, 1/8]）。 */
    public static double megaDensityAt(long worldSeed, int x, int z) {
        return fold(worldSeed, x, z, LAYER_MEGA);
    }

    /**
     * 密度值 → 掷骰门阈值：{@code rand.nextInt(DICE_GATE) < gateOf(d)} 的命中概率 = d
     * （{@code DICE_GATE} 的整数近似，均匀区精确，见类注释）。负值钳 0、>1 钳满门。
     */
    public static int gateOf(double density) {
        final int g = (int) Math.round(density * (double) DICE_GATE);
        return g <= 0 ? 0 : Math.min(g, DICE_GATE);
    }

    /**
     * 植被档的<b>密度场主导</b>选取（{@code ProsperityWorldGenerator.vegRosterIndex} 渗色修复，
     * P19 §G/K1）：内核各粗格按<b>有效身份</b>（sanzu 列覆写，见 {@link #effectiveRosterAt}）投票，
     * 每身份票重 = Σ核权，档值取 {@code 票重 × normalRowOf(身份)} 的<b>普通档密度</b>，取 argmax。
     * <p>
     * 平票（含全部行值为 0 的全荒漠区——普通档行值 0 不可作 argmax 主键）按<b>先到者得</b>：
     * 按 {@code -1(默认) < 0 < 1 < 2 < 3 < 4} 升序扫描 + 严格大于替换 ⇒ 平票取名册序小者
     * （任务包口径）；全荒漠区唯一在场身份是荒漠 ⇒ 仍选荒漠（不退化成草原档）。
     * 胜者为 {@code -1}（身份不可得，离线/EMPTY 降级态）⇒ 返回 {@code chainIndex}
     * （与改造前一致：身份缺失走默认档，由 {@code tierForRosterIndex(-1)} 兜底）。
     * 生产态 {@code -1} 不可达（实测 524288 chunk 上 0 次）。
     * <p>
     * 均匀区（核内全同有效身份）短路返回该身份本身 ⇒ 群系腹地的植被档与改造前逐位一致。
     */
    public static int dominantVegRosterAt(long worldSeed, int x, int z, int chainIndex) {
        final int cellX = x >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int cellZ = z >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        // 桶下标 = 身份 + 1：0 ↔ 默认(-1)、1..MAX_ROSTER_INDEX+1 ↔ 名册 0..（升序扫描 = 名册序小者先到）
        final double[] votes = new double[MAX_ROSTER_INDEX + 2];
        final int[] window = rosterWindow(worldSeed, cellX, cellZ);
        int first = 0;
        boolean uniform = true;
        for (int k = 0; k < KERNEL_DX.length; k++) {
            final int r = window[k];
            if (k == 0) {
                first = r;
            } else if (r != first) {
                uniform = false;
            }
            votes[r + 1] += KERNEL_W[k];
        }
        if (uniform) {
            return first < 0 ? chainIndex : first;
        }
        int best = -2;
        double bestVal = -1.0D;
        for (int b = 0; b < votes.length; b++) {
            if (votes[b] <= 0.0D) {
                continue;
            }
            final double v = votes[b] * normalRowOf(b - 1);
            if (v > bestVal) {
                bestVal = v;
                best = b - 1;
            }
        }
        return best <= 0 ? chainIndex : best;
    }

    /**
     * 内核折叠：11×11 smoothstep 紧支核对粗格有效身份的三层档值加权。全同身份 ⇒ 直接返回该行值
     * （逐位，跳过归一化的 ulp 级误差）；混合 ⇒ 凸组合。
     */
    private static double fold(long worldSeed, int x, int z, int layer) {
        final int cellX = x >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int cellZ = z >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int[] window = rosterWindow(worldSeed, cellX, cellZ);
        double acc = 0.0D;
        int first = 0;
        boolean uniform = true;
        for (int k = 0; k < KERNEL_DX.length; k++) {
            final int r = window[k];
            if (k == 0) {
                first = r;
            } else if (r != first) {
                uniform = false;
            }
            acc += KERNEL_W[k] * rowDensity(r, layer);
        }
        return uniform ? rowDensity(first, layer) : acc;
    }

    /**
     * 核窗身份的<b>列间 memo</b>（P19 U8 性能批）：核中心同粗格的所有列，窗内 69 个有效身份
     * 逐位相同（身份只依赖 (seed, 粗格)）——按 (seed, 中心粗格) 缓存上一次窗口的 int[]，命中
     * 即免 69 次 {@link #effectiveRosterAt} 查表。身份是纯函数 ⇒ 命中/未命中逐位同值；
     * <b>"档值零缓存"纪律不变</b>：本表只缓<b>身份</b>，三层档值仍由 {@link #rowDensity} 每次
     * 现读档表（离线判据 ANTI 换档臂立即可见）；账本时点假设与 {@link #effectiveRosterAt} 同款。
     */
    private static int[] rosterWindow(long worldSeed, int cellX, int cellZ) {
        final long[] winSeed = WINDOW_SEED.get();
        final int[] winCell = WINDOW_CELL.get();
        final int[] winRoster = WINDOW_ROSTER.get();
        if (winSeed[0] == worldSeed && winCell[0] == cellX && winCell[1] == cellZ) {
            return winRoster;
        }
        for (int k = 0; k < KERNEL_DX.length; k++) {
            winRoster[k] = effectiveRosterAt(worldSeed, cellX + KERNEL_DX[k], cellZ + KERNEL_DZ[k]);
        }
        winSeed[0] = worldSeed;
        winCell[0] = cellX;
        winCell[1] = cellZ;
        return winRoster;
    }

    /** 核窗 memo：当前 (seed, cellX, cellZ) 与窗内有效身份（惰性建表，长度 = 核点数 69）。 */
    private static final ThreadLocal<long[]> WINDOW_SEED = ThreadLocal.withInitial(() -> new long[1]);
    private static final ThreadLocal<int[]> WINDOW_CELL = ThreadLocal.withInitial(() -> new int[2]);
    private static final ThreadLocal<int[]> WINDOW_ROSTER = ThreadLocal.withInitial(() -> new int[KERNEL_DX.length]);

    /**
     * 粗格 (cellX, cellZ) 的<b>有效身份</b>（缓存）：链身份面名册下标（{@code -1} = 身份不可得）
     * 之上叠加 sanzu 平面档覆写——粗格左上列命中 {@link GTSRVoronoiRiverField#isSanzuColumn}
     * （与 {@code ChunkProviderProsperityRuins.assignSanzuRiverBiome} 的逐列写入同谓词同口径，
     * 纯函数跨 chunk 一致）且 sanzu 已配槽（无槽降级不伪造，与改造前 vegRosterIndex 同一守卫）
     * ⇒ 取 {@link BiomeId#SANZU_RIVER} 名册下标。查询 seed 组合与 ampAt/manager/城门逐字同式
     * （{@code worldSeed ^ CHAIN_SEED_SALT}，盐纪律见类注释）。
     */
    private static int effectiveRosterAt(long worldSeed, int cellX, int cellZ) {
        final HashMap<Long, HashMap<Long, Integer>> bySeed = CELL_ROSTER_CACHE.get();
        HashMap<Long, Integer> cells = bySeed.get(worldSeed);
        if (cells == null) {
            cells = new HashMap<>();
            bySeed.put(worldSeed, cells);
        }
        final Long key = Long.valueOf(packCell(cellX, cellZ));
        Integer cached = cells.get(key);
        if (cached == null) {
            if (cells.size() >= CELL_CACHE_CAP) {
                cells.clear();
            }
            final int blockX = cellX << GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
            final int blockZ = cellZ << GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
            // P19 U8：链身份取数并入 Profile.chainRosterIndexAt 的单一份共享 memo（取数式与本处
            // 旧内联版逐字同源——同 seed 组合/同盐/同 coarse 面，消同格短命链的跨类重复求值）。
            int roster = ProsperityTerrainProfile.chainRosterIndexAt(worldSeed, cellX, cellZ);
            if (GTSRBiomeAuthority.forDimKey(SurfaceGate.DIM78)
                .biomeOf(BiomeId.SANZU_RIVER) != null
                && GTSRVoronoiRiverField.isSanzuColumn(worldSeed, blockX, blockZ)) {
                roster = BiomeId.SANZU_RIVER.rosterIndex();
            }
            cached = Integer.valueOf(roster);
            cells.put(key, cached);
        }
        return cached.intValue();
    }

    /**
     * 身份 → 三层档值（<b>现读</b>档表，零缓存）：普通档 = {@code treeRolls/treeChanceDenom}
     * （chunk 级期望树数；{@code rolls=0} 或 {@code denom=0} ⇒ 0），灌木/巨树 = {@code 1/N}
     * （0 = 关）。取数路径按表分家且全部<b>按名册下标现读</b>：普通档走
     * {@link ProsperityDecorPlacer#tierForRosterIndex}（VEG 表当前槽位）、灌木/巨树走
     * {@link ProsperityDecorPlacer#treeTiersForRosterIndex}（TREE 表当前槽位）——
     * <b>不</b>经 {@code TreeTierSet.normal} 的类初始化期冻结引用（离线判据的 ANTI 换档臂
     * {@code VEG_TIERS_BY_ROSTER[荒漠] = 必掷行} 必须立刻对本场可见，冻结引用会把它静默吃掉）。
     * 身份 {@code -1} 走默认档（{@code DEFAULT_TIER} 普通 1/16、{@code DEFAULT_TREE_TIERS}
     * 灌木/巨树 0——与生产降级口径一致）。
     */
    private static double rowDensity(int roster, int layer) {
        switch (layer) {
            case LAYER_SHRUB: {
                final ProsperityDecorPlacer.TreeTierSet tiers = roster >= 0
                    ? ProsperityDecorPlacer.treeTiersForRosterIndex(roster)
                    : ProsperityDecorPlacer.DEFAULT_TREE_TIERS;
                return tiers.shrubDenom > 0 ? 1.0D / tiers.shrubDenom : 0.0D;
            }
            case LAYER_MEGA: {
                final ProsperityDecorPlacer.TreeTierSet tiers = roster >= 0
                    ? ProsperityDecorPlacer.treeTiersForRosterIndex(roster)
                    : ProsperityDecorPlacer.DEFAULT_TREE_TIERS;
                return tiers.megaDenom > 0 ? 1.0D / tiers.megaDenom : 0.0D;
            }
            default: {
                final ProsperityDecorPlacer.VegTier tier = roster >= 0
                    ? ProsperityDecorPlacer.tierForRosterIndex(roster)
                    : ProsperityDecorPlacer.DEFAULT_TIER;
                return tier.treeRolls > 0 && tier.treeChanceDenom > 0 ? (double) tier.treeRolls / tier.treeChanceDenom
                    : 0.0D;
            }
        }
    }

    /** 普通档行值（argmax 主键；身份 {@code -1} = 默认档 1/16）。 */
    private static double normalRowOf(int roster) {
        return rowDensity(roster, LAYER_NORMAL);
    }

    /** (cellX, cellZ) → long 打包（低 32 位 cellZ；算术语义下负坐标两侧一致，同 Profile 口径）。 */
    private static long packCell(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }
}
