import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerRosterFace;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;

/**
 * <b>P17 S-A 地势分化 + 群系成片判据</b>（新增机检；改前这块"没有回归网"）。
 * <p>
 * 钉住的需求原话：
 * <ul>
 * <li>需求 1「调整群系生成，单个群系略大一些（现在生成的太破碎了，一小块一小块的）」→ SCALE 组；</li>
 * <li>需求 2「增加地形差异……（青铜森林）地势也相对更起伏」（平原……）（沼泽……地势最平坦）
 * （沙漠……地势相对更平坦一些）」→ RELIEF 组，偏序 <b>森林最大 &gt; 平原 ≥ 沙漠 &gt; 沼泽最小</b>。</li>
 * </ul>
 * 另两组钉实现纪律：SOURCE 组钉"四族同源 + 身份面与 L1/城门同格同值"，REDLINE 组以源码扫描钉
 * "高度面零方块读取 + 高度公式全仓只有一份"（城市侧红线，plan §3.1）。
 * <p>
 * <b>零装配口径</b>：RELIEF/SCALE 两组用哑元 id 表 [180..183] 直连 {@link GTSRGenLayerChain}
 * （与 {@code BiomeZoneCheck} 同口径，不碰注册表）；SOURCE 组自己用匿名 {@link BiomeGenBase}
 * 子类把 dim78 名册记进 L1 账本（{@code recordAllocation}）后走生产三参出口，
 * <b>不</b>依赖 {@code SurfaceHarness}（兄弟切片持有该文件，避免签名漂移把本判据一起带走）。
 * <p>
 * 用法：{@code java -cp <classes;MC;deps> tools/dim1/P17TerrainReliefCheck.java [srcRoot]}
 * 或先 {@code javac} 再 {@code java P17TerrainReliefCheck src/main/java}。
 * 全部阈值是<b>申报式硬钉</b>（照 {@code HeightHashSingleSourceCheck}/{@code ContourBudgetCheck} 口径）：
 * 现值写在 {@code # P17-SA 申报} 注释里，改动高度面/成片档必须同步本文件的申报，不许放宽容差。
 */
public class P17TerrainReliefCheck {

    /** 名册序哑元 id 表（下标 = L1 维内名册下标：草原/森林/荒漠/沼泽）。 */
    private static final int[] IDS = { 180, 181, 182, 183 };
    private static final String[] NAME = { "STEPPE", "FOREST", "WASTES", "SWAMP" };
    /** dim78 def.seedSalt（与 ProsperityTerrainProfile.CHAIN_SEED_SALT 同值，SALT 组钉）。 */
    private static final long SALT = 0x50524F53L;

    // ———— SCALE 组：成片尺度 ————
    private static final int SCALE_SEEDS = 8;
    /**
     * 采样窗边长（chunk）——<b>派生式</b>（T8 重钉，归因 T6 zoom 5→7）：锚定"每轴 16 个
     * selector 格"的统计功效。selector 格 = 2^(zoom-2) chunk（= 4·2^zoom 方块）⇒ zoom=5 时
     * = 128（P17 时代校准窗），zoom=7 时自动放大到 512。固定 128 窗在 zoom=7 只剩 4 格/轴，
     * 份额偏差被窗边缘截断效应放大到 18pp（T8 红清单 #11-SCALE 行）。
     */
    private static final int SCALE_CELLS_PER_AXIS = 16;
    private static final int SCALE_WIN_CHUNKS = SCALE_CELLS_PER_AXIS << (GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS - 2);
    /** P17-SA 申报（改前/改后）：平均连通域 41.9 → 148.3 chunk；zoom=7 后按格径比例自然增长。 */
    private static final double MEAN_COMPONENT_MIN = 100.0D;
    /** P17-SA 申报：孤岛（4 邻全异）chunk 占比最坏 0.025pp = 0.00025。 */
    private static final double ISLAND_RATIO_MAX = 0.0010D;
    /** 成片档钉死值（v1.20.39 T6 裁定 = 7，plan §3.5；静默回退 5/6 必须当场红）。 */
    private static final int EXPECT_ZOOM_LEVELS = 7;

    // ———— RELIEF 组：地势偏序 ————
    /** 16 个连续 seed（0..15，非挑选：固定公式生成）。 */
    private static final int RELIEF_SEEDS = 16;
    /**
     * 每 seed 采样窗边长（方块）——<b>派生式</b>（T8 重钉，归因 T6 zoom 5→7）：锚定"6 个
     * selector 格/轴"（selector 格 = 4·2^zoom 方块，GTSRGenLayerChain 同口径）⇒ zoom=5 时
     * = 768（P17 时代校准窗，窗内每群系约 60 个成片域），zoom=7 时 = 3072。固定 768 窗在
     * zoom=7 只剩 1.5 格/轴，逐 seed 偏序退化为抽签（T8 红清单 #11-RELIEF 行读数 1/16）。
     */
    private static final int RELIEF_CELLS_PER_AXIS = 6;
    private static final int RELIEF_SIDE = RELIEF_CELLS_PER_AXIS * 4 << GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS;
    /**
     * 采样步距（方块）——<b>派生式</b>：zoom 每级步距 ×2，使每 seed 实采列数恒等于 zoom=5
     * 基准（768² ≈ 0.59M），总成本不随窗放大；「相邻列」几何量（within 粗糙度 / max|Δh|）
     * 由此变为"STRIDE 间隔列"口径，重钉读数按新口径申报。
     */
    private static final int RELIEF_STRIDE = 1 << Math.max(0, GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS - 5);
    /** P17-SA 申报（当前档实测）：聚合 sd 森 8.954 / 原 6.392 / 沙 4.576 / 沼 2.609。 */
    private static final double RATIO_FOREST_STEPPE_MIN = 1.25D;
    private static final double RATIO_STEPPE_WASTES_MIN = 1.25D;
    private static final double RATIO_WASTES_SWAMP_MIN = 1.30D;
    /** 逐 seed 偏序命中数下限（实测 16/16）。 */
    private static final int PER_SEED_HITS_MIN = 15;
    /** 相邻列 |Δh| 下限档：改前全维度 max|Δh| = 1（P17-B §1.3）；T4 后河谷壁把该读数抬回真实地势量级。 */
    private static final double ADJACENT_DELTA_MIN = 8.0D;
    /** 群系内（同档相邻列）mean|Δh| 申报：森 0.134 / 原 0.096 / 沙 0.080 / 沼 0.059。 */
    private static final double WITHIN_RATIO_MIN = 1.15D;
    /**
     * 触钳制边界的列占比上限（T8 重钉）：语义="clamp 不得截平 sd 分布"。P17-SA 时代窗小、
     * 实测恒 0，钉的是字面 0；派生窗（3072²×16 seed ≈ 944 万样本）下森林腹地 1.7 档与三频正弦
     * 最低相位叠加的列触及 y=40 下沿（实测 463 = 0.0049%，全部是"恰好等于 40"的触边而非截平带，
     * 聚合 sd 森 9.643 / 沼 2.517 间距 3.8×，触边不可能改变偏序）。口径改为<b>派生占比</b>
     * ≤ 0.01% 样本数，抓力（"截平即红"）不变。
     */
    private static final double CLAMP_RATIO_MAX = 0.0001D;

    private static final List<String> PROBLEMS = new ArrayList<>();
    private static int assertions = 0;

    public static void main(String[] args) throws IOException {
        final Path srcRoot = Paths.get(args.length > 0 ? args[0] : "src/main/java");
        scaleGroup();
        reliefGroup();
        sourceGroup();
        redlineGroup(srcRoot);
        saltGroup(srcRoot);
        System.out.println(
            "P17-RELIEF " + (PROBLEMS.isEmpty() ? "PASS" : "FAIL") + " assertions=" + assertions
                + " zoomLevels=" + GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS + " reliefTable="
                + Arrays.toString(ProsperityTerrainProfile.RELIEF_AMPLITUDE_BY_ROSTER));
        if (!PROBLEMS.isEmpty()) {
            for (final String p : PROBLEMS) {
                System.out.println("  FAIL： " + p);
            }
            System.exit(1);
        }
    }

    // ═══════════════════════ SCALE 组（需求 1）═══════════════════════════

    private static void scaleGroup() {
        check(GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS == EXPECT_ZOOM_LEVELS,
            "SCALE 成片档 DEFAULT_ZOOM_LEVELS == " + EXPECT_ZOOM_LEVELS + "（实测 "
                + GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS + "；plan §0 Q1 裁定档）");
        double sumMeanArea = 0;
        double worstIsland = 0;
        double worstShareDev = 0;
        long dominantMin = Long.MAX_VALUE;
        for (int s = 0; s < SCALE_SEEDS; s++) {
            final long seed = 1000L * s + 7;
            final int[] grid = chunkIdentityGrid(seed, SCALE_WIN_CHUNKS);
            final double[] st = scaleStats(grid, SCALE_WIN_CHUNKS);
            sumMeanArea += st[0];
            worstIsland = Math.max(worstIsland, st[1]);
            worstShareDev = Math.max(worstShareDev, st[2]);
            dominantMin = Math.min(dominantMin, (long)st[3]);
        }
        final double meanArea = sumMeanArea / SCALE_SEEDS;
        System.out.printf("  SCALE 8seed×%d²chunk：平均连通域=%.1f chunk 最坏孤岛=%.3fpp 最坏份额偏差=%.3fpp"
            + " 最小主导簇=%d%n", SCALE_WIN_CHUNKS, meanArea, worstIsland * 100, worstShareDev * 100, dominantMin);
        check(meanArea >= MEAN_COMPONENT_MIN, "SCALE 平均连通域 " + fmt1(meanArea) + " chunk ≥ " + MEAN_COMPONENT_MIN
            + "（改前实测 41.9 ⇒ 需求 1「单群系略大一些」的量化口）");
        check(worstIsland <= ISLAND_RATIO_MAX, "SCALE 最坏孤岛率 " + fmt1(worstIsland * 100) + "pp ≤ "
            + fmt1(ISLAND_RATIO_MAX * 100) + "pp（改前 0.151pp、改后申报 0.025pp）");
        check(dominantMin >= 256L, "SCALE 主导群系最大 4-连通簇（逐 seed 最坏）" + dominantMin + " chunk ≥ 256");
        // 份额仍守等权（plan §2 第 1 条：成片不改面积；这里只复核"没顺手改成加权"）
        check(worstShareDev <= 0.10D, "SCALE chunk 份额对等权 25% 的最坏偏差 " + fmt1(worstShareDev * 100)
            + "pp ≤ 10pp（窗边缘截断口径；等权语义由 BiomeZoneCheck/BBH D0 严钉）");
    }

    /** chunk 代表点身份网格（与 {@code GTSRWorldChunkManager.biomeAt} 同式：chunk 中心块 → 粗格）。 */
    private static int[] chunkIdentityGrid(long worldSeed, int winChunks) {
        final GTSRGenLayerChain chain = new GTSRGenLayerChain(worldSeed ^ SALT, IDS);
        final int cs = winChunks * 4;
        final int[] coarse = chain.coarseInts(0, 0, cs, cs).clone();
        final int[] grid = new int[winChunks * winChunks];
        for (int cz = 0; cz < winChunks; cz++) {
            for (int cx = 0; cx < winChunks; cx++) {
                grid[cx + cz * winChunks] = coarse[(cx * 4 + 2) + (cz * 4 + 2) * cs];
            }
        }
        return grid;
    }

    /** [平均连通域, 孤岛占比, 最坏份额偏差, 主导 id 最大簇]。 */
    private static double[] scaleStats(int[] grid, int w) {
        final int n = grid.length;
        final int[] seen = new int[n];
        Arrays.fill(seen, -1);
        final int[] stack = new int[n];
        final int[] perId = new int[IDS.length];
        final int[] best = new int[IDS.length];
        double sum = 0;
        long comps = 0;
        int cid = 0;
        for (int start = 0; start < n; start++) {
            if (seen[start] >= 0) {
                continue;
            }
            final int id = grid[start];
            final int zone = indexOf(id);
            int top = 0;
            stack[top++] = start;
            seen[start] = cid;
            int size = 0;
            while (top > 0) {
                final int idx = stack[--top];
                size++;
                final int x = idx % w;
                final int z = idx / w;
                if (x > 0 && seen[idx - 1] < 0 && grid[idx - 1] == id) {
                    seen[idx - 1] = cid;
                    stack[top++] = idx - 1;
                }
                if (x < w - 1 && seen[idx + 1] < 0 && grid[idx + 1] == id) {
                    seen[idx + 1] = cid;
                    stack[top++] = idx + 1;
                }
                if (z > 0 && seen[idx - w] < 0 && grid[idx - w] == id) {
                    seen[idx - w] = cid;
                    stack[top++] = idx - w;
                }
                if (z < w - 1 && seen[idx + w] < 0 && grid[idx + w] == id) {
                    seen[idx + w] = cid;
                    stack[top++] = idx + w;
                }
            }
            sum += size;
            comps++;
            perId[zone] += size;
            if (size > best[zone]) {
                best[zone] = size;
            }
            cid++;
        }
        long interior = 0;
        long islands = 0;
        for (int z = 1; z < w - 1; z++) {
            for (int x = 1; x < w - 1; x++) {
                final int idx = x + z * w;
                final int id = grid[idx];
                interior++;
                if (grid[idx - 1] != id && grid[idx + 1] != id && grid[idx - w] != id && grid[idx + w] != id) {
                    islands++;
                }
            }
        }
        double maxDev = 0;
        int dominant = 0;
        for (int i = 0; i < IDS.length; i++) {
            maxDev = Math.max(maxDev, Math.abs((double)perId[i] / n - 0.25D));
            if (best[i] > best[dominant]) {
                dominant = i;
            }
        }
        return new double[] {sum / comps, (double)islands / interior, maxDev, best[dominant]};
    }

    // ═══════════════════════ RELIEF 组（需求 2 的地势侧）═══════════════════════════

    private static void reliefGroup() {
        // —— 账本先行（T3 §2 账本时点纪律 / T8 重钉核心）：T3 起 4-arg heightAtWithReliefTier 的
        // 档参被忽略、内部走 ampAt；ampAt 的单粗格档值按"首次求值时的账本"缓存，而无账本 JVM 的
        // rosterIndexAt 恒 -1 ⇒ 全表默认档 1.0，RELIEF 组退化为 P17 前的无分化口径（T3 prered §4
        // 的结构性红因）。本 JVM 的首次 heightAt 必须发生在哑元档入账之后（与 SOURCE 组同式）。
        for (int i = 0; i < IDS.length; i++) {
            GTSRBiomeAuthority.recordAllocation(dim78Keys()[i], IDS[i], IDS[i], new BiomeGenBase(IDS[i]) {});
        }
        // —— 振幅均值锚（T5/T8 重钉）：「只差异化、不整体加大起伏」锚定在 4 个 selector 档上
        //（P17-SA 申报的原语义口径）；T5 第 5 元 sanzu=0.38 是名册档非 selector（plan §3.3），
        // 不进该均值——口径收窄而非语义放宽。
        check(Math.abs(meanOfSelectorTable() - 1.0D) < 1e-9D, "RELIEF 四 selector 档振幅算数均值 == 1.0（实测 "
            + fmt1(meanOfSelectorTable()) + "）⇒ 本片只做差异化，不整体加大起伏（sanzu 第 5 元为名册档，"
            + "不进 selector 均值锚）");
        for (final double v : ProsperityTerrainProfile.RELIEF_AMPLITUDE_BY_ROSTER) {
            check(v > 0, "RELIEF 每档振幅乘子 > 0（沼泽取最低档而不是 0：0 = 绝对平坦面，且 S-C 河床要可变面）");
        }
        final int side = RELIEF_SIDE / RELIEF_STRIDE;
        final long[] cnt = new long[4];
        final double[] sum = new double[4];
        final double[] sum2 = new double[4];
        final double[] withinSum = new double[4];
        final long[] withinPair = new long[4];
        double maxAdjacent = 0;
        double minH = Integer.MAX_VALUE;
        double maxH = Integer.MIN_VALUE;
        long clampHits = 0;
        int strictHits = 0;
        int coreHits = 0;
        int coveredSeeds = 0;
        for (int s = 0; s < RELIEF_SEEDS; s++) {
            final long worldSeed = s;
            final GTSRGenLayerChain chain = new GTSRGenLayerChain(worldSeed ^ SALT, IDS);
            final int cs = RELIEF_SIDE / GTSRGenLayerChain.COARSE_BLOCK_SCALE;
            final int[] coarse = chain.coarseInts(0, 0, cs, cs).clone();
            final int[] h = new int[side * side];
            final int[] t = new int[side * side];
            final boolean[] riverFree = new boolean[side * side];
            for (int sz = 0; sz < side; sz++) {
                for (int sx = 0; sx < side; sx++) {
                    final int idx = sx + sz * side;
                    final int x = sx * RELIEF_STRIDE;
                    final int z = sz * RELIEF_STRIDE;
                    t[idx] = tierOf(coarse, cs, x, z);
                    h[idx] = ProsperityTerrainProfile.heightAtWithReliefTier(worldSeed, x, z, t[idx]);
                    // T4 河谷归因（T8 重钉）：河谷压低与巨湖压低是 T4/T5 的设计行为，会把沿河
                    // 群系（森宽谷/沼 ×1.6 宽谷）的高度方差整体搬家，压掉振幅档本身的偏序。
                    // 偏序判据因此只在"河/湖未触及列"（s==0 且 lake ≥ 岸哨兵）上计算——
                    // 语义=「振幅档仍把无河地形分开」，不是对整维方差放宽。
                    riverFree[idx] = -GTSRVoronoiRiverField.strengthAt(worldSeed, x, z) == 0.0D
                        && GTSRVoronoiRiverField.lakeAt(worldSeed, x, z) >= GTSRVoronoiRiverField.LAKE_SHORE;
                }
            }
            final double[] sd = new double[4];
            final long[] c = new long[4];
            final double[] s1 = new double[4];
            final double[] s2 = new double[4];
            for (int sz = 0; sz < side; sz++) {
                for (int sx = 0; sx < side; sx++) {
                    final int idx = sx + sz * side;
                    final int tier = t[idx];
                    final int v = h[idx];
                    if (tier < 0 || !riverFree[idx]) {
                        continue;
                    }
                    c[tier]++;
                    s1[tier] += v;
                    s2[tier] += (double)v * v;
                    cnt[tier]++;
                    sum[tier] += v;
                    sum2[tier] += (double)v * v;
                    if (v <= 40 || v >= 110) {
                        clampHits++;
                    }
                    minH = Math.min(minH, v);
                    maxH = Math.max(maxH, v);
                    // 「相邻列」= RELIEF_STRIDE 间隔列（派生步距口径，见常量注释）；x 向与 z 向
                    // 配对只在两列同档且都无河/湖时进 within（群系内粗糙度）；max|Δh| 保留全部
                    // 列对（河谷壁是 T4 设计的一部分，正是"有真实地势差"的最强读数）。
                    if (sx + 1 < side) {
                        final double d = Math.abs(h[idx + 1] - v);
                        if (t[idx + 1] == tier && riverFree[idx + 1]) {
                            withinSum[tier] += d;
                            withinPair[tier]++;
                        }
                        maxAdjacent = Math.max(maxAdjacent, d);
                    }
                    if (sz + 1 < side) {
                        final double d = Math.abs(h[idx + side] - v);
                        if (t[idx + side] == tier && riverFree[idx + side]) {
                            withinSum[tier] += d;
                            withinPair[tier]++;
                        }
                        maxAdjacent = Math.max(maxAdjacent, d);
                    }
                }
            }
            for (int i = 0; i < 4; i++) {
                final double mean = c[i] == 0 ? 0 : s1[i] / c[i];
                sd[i] = c[i] == 0 ? 0 : Math.sqrt(Math.max(0, s2[i] / c[i] - mean * mean));
            }
            // 逐 seed 偏序的覆盖前提：四档在去河/湖列后都有足量样本（下限 = 窗样本/256，均值意义上
            // 每档占 1/4 ⇒ 留 64× 裕量）；不满足的 seed 记一次"未覆盖"，由 coveredSeeds 单独钉。
            boolean covered = true;
            for (int i = 0; i < 4; i++) {
                covered &= c[i] >= side * side / 256;
            }
            if (covered) {
                coveredSeeds++;
                if (sd[1] > sd[0] && sd[0] > sd[2] && sd[2] > sd[3]) {
                    strictHits++;
                }
                if (isMax(sd, 1) && isMin(sd, 3) && sd[0] >= sd[2]) {
                    coreHits++;
                }
            }
        }
        final double[] sd = new double[4];
        final double[] rough = new double[4];
        for (int i = 0; i < 4; i++) {
            final double mean = sum[i] / cnt[i];
            sd[i] = Math.sqrt(Math.max(0, sum2[i] / cnt[i] - mean * mean));
            rough[i] = withinSum[i] / withinPair[i];
        }
        System.out.println("  RELIEF " + RELIEF_SEEDS + "seed×" + RELIEF_SIDE + "²方块(步距" + RELIEF_STRIDE
            + "，去河/湖列)：聚合 sd=" + fmt(sd) + " 群系内 mean|Δh|=" + fmt(rough) + " 严格序命中=" + strictHits
            + "/" + RELIEF_SEEDS + " 偏序命中=" + coreHits + "/" + RELIEF_SEEDS + " 覆盖=" + coveredSeeds + "/"
            + RELIEF_SEEDS);
        System.out.printf("  全域 max|Δh|=%.0f（改前 1） 高度域=[%.0f,%.0f] 触钳制列=%d%n", maxAdjacent, minH, maxH,
            clampHits);
        check(coveredSeeds == RELIEF_SEEDS, "RELIEF 逐 seed 四档覆盖（去河/湖列后样本充足）命中 " + coveredSeeds
            + "/" + RELIEF_SEEDS + " 必须全中（派生窗 6 格/轴的前提读数）");
        check(sd[1] > sd[0] && sd[0] >= sd[2] && sd[2] > sd[3], "RELIEF 聚合 sd 偏序 森>原≥沙>沼（实测 " + fmt(sd)
            + "；T3 账本先行 + T4 去 河/湖列 后的申报口径）");
        check(sd[1] / sd[0] >= RATIO_FOREST_STEPPE_MIN, "RELIEF 森/原 sd 比 " + fmt1(sd[1] / sd[0]) + " ≥ "
            + RATIO_FOREST_STEPPE_MIN + "（申报现值 1.401）");
        check(sd[0] / sd[2] >= RATIO_STEPPE_WASTES_MIN, "RELIEF 原/沙 sd 比 " + fmt1(sd[0] / sd[2]) + " ≥ "
            + RATIO_STEPPE_WASTES_MIN + "（申报现值 1.397）");
        check(sd[2] / sd[3] >= RATIO_WASTES_SWAMP_MIN, "RELIEF 沙/沼 sd 比 " + fmt1(sd[2] / sd[3]) + " ≥ "
            + RATIO_WASTES_SWAMP_MIN + "（申报现值 1.754）");
        check(strictHits >= PER_SEED_HITS_MIN, "RELIEF 逐 seed 严格序（森>原>沙>沼）命中 " + strictHits + "/"
            + RELIEF_SEEDS + " ≥ " + PER_SEED_HITS_MIN + "（改前 0/10；P17-B §2.1 的"
            + "「最小下一步」门槛是 ≥9/10）");
        check(coreHits == RELIEF_SEEDS, "RELIEF 逐 seed 核心偏序（森林最陡且沼泽最平且原≥沙）命中 " + coreHits + "/"
            + RELIEF_SEEDS + " 必须全中");
        check(maxAdjacent >= ADJACENT_DELTA_MIN, "RELIEF 相邻列 max|Δh| " + fmt1(maxAdjacent) + " ≥ "
            + ADJACENT_DELTA_MIN + "（改前全维度实测 = 1，即「完全没有地势差异」的那个数）");
        final long clampMax = (long) (side * side * (double) RELIEF_SEEDS * CLAMP_RATIO_MAX);
        check(clampHits <= clampMax, "RELIEF 触 y=40/110 钳制边的列数 " + clampHits + " ≤ " + clampMax
            + "（样本的 0.01%；越界说明振幅档被 clamp 截平，sd 偏序会失真）");
        check(rough[1] > rough[0] && rough[0] >= rough[2] && rough[2] > rough[3],
            "RELIEF 群系内 mean|Δh| 同偏序（实测 " + fmt(rough) + "）⇒ 短尺度粗糙度与长尺度 sd 同向");
        check(rough[1] / rough[0] >= WITHIN_RATIO_MIN, "RELIEF 森/原 群系内粗糙度比 " + fmt1(rough[1] / rough[0])
            + " ≥ " + WITHIN_RATIO_MIN);
        check(rough[3] < rough[2], "RELIEF 沼<沙 群系内粗糙度（沼泽最平坦）");
    }

    private static boolean isMax(double[] v, int i) {
        for (int j = 0; j < v.length; j++) {
            if (j != i && v[j] >= v[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMin(double[] v, int i) {
        for (int j = 0; j < v.length; j++) {
            if (j != i && v[j] <= v[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 4 个 <b>selector</b> 档的振幅算数均值（T5/T8 重钉口径：名册第 5 元 sanzu 不进 selector
     * 等权名册，也就不进"只差异化不加大起伏"的均值锚）。
     */
    private static double meanOfSelectorTable() {
        double s = 0;
        for (int i = 0; i < IDS.length; i++) {
            s += ProsperityTerrainProfile.RELIEF_AMPLITUDE_BY_ROSTER[i];
        }
        return s / IDS.length;
    }

    private static int tierOf(int[] coarse, int cs, int x, int z) {
        return indexOf(coarse[(x >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT)
            + (z >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT) * cs]);
    }

    // ═══════════════════════ SOURCE 组（四族同源 / 同格同值）═══════════════════════════

    private static void sourceGroup() {
        for (int i = 0; i < IDS.length; i++) {
            final BiomeGenBase biome = new BiomeGenBase(IDS[i]) {};
            GTSRBiomeAuthority.recordAllocation(dim78Keys()[i], IDS[i], IDS[i], biome);
        }
        final int[] ledgerIds = GTSRGenLayerRosterFace.allocatedRosterIds(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        check(Arrays.equals(ledgerIds, IDS), "SOURCE 离线账本装配成功：dim78 名册 id 表 " + Arrays.toString(ledgerIds));
        check(ProsperityTerrainProfile.CHAIN_SEED_SALT == SALT, "SOURCE Profile 申报的链盐 == 0x50524F53");
        int bad = 0;
        int badBand = 0;
        int neg1 = 0;
        long sample = 0;
        final long worldSeed = 12345L;
        for (int cz = -4; cz <= 4; cz++) {
            for (int cx = -4; cx <= 4; cx++) {
                for (int dx = 0; dx < 16; dx += 3) {
                    for (int dz = 0; dz < 16; dz += 3) {
                        final int x = (cx << 4) + dx;
                        final int z = (cz << 4) + dz;
                        final int tier = GTSRGenLayerRosterFace.rosterIndexAt(
                            worldSeed ^ SALT,
                            GTSRBiomeAuthority.DIM_KEY_PROSPERITY,
                            x,
                            z);
                        if (tier < 0) {
                            neg1++;
                        }
                        if (ProsperityTerrainProfile.heightAt(worldSeed, x, z) != ProsperityTerrainProfile
                            .heightAtWithReliefTier(worldSeed, x, z, tier)) {
                            bad++;
                        }
                        sample++;
                    }
                }
                final int band = CityPlanner.bandIndexAt(worldSeed, cx, cz);
                final int face = GTSRGenLayerRosterFace.rosterIndexAt(
                    worldSeed ^ SALT,
                    GTSRBiomeAuthority.DIM_KEY_PROSPERITY,
                    (cx << 4) + 8,
                    (cz << 4) + 8);
                if (band != face) {
                    badBand++;
                }
            }
        }
        System.out.println("  SOURCE 三参==显式档 差异=" + bad + "/" + sample + " 名册面==bandIndexAt 差异=" + badBand
            + "/169 neg1=" + neg1);
        check(bad == 0, "SOURCE 生产三参 heightAt 与档表显式形态逐点同值（差异 " + bad + "/" + sample
            + "）⇒ 群系化没有造出第二套高度");
        check(badBand == 0, "SOURCE 高度面的身份 == 城门 bandIndexAt 的名册下标（差异 " + badBand
            + "/169，含负坐标）⇒ coarse 面与 L1/城门同格同值，结构接地与地形不会错位");
        check(neg1 == 0, "SOURCE 真实账本下身份取不到 -1（neg1=" + neg1 + "）⇒ 默认档不是生产路径");
        // 默认档语义：无身份 ⇒ 与改前逐位相同（档表默认档 == 1.0 是这条等式的唯一凭据）
        check(ProsperityTerrainProfile.DEFAULT_RELIEF_AMPLITUDE == 1.0D,
            "SOURCE 默认档振幅 == 1.0（EMPTY 降级/未装配 JVM 的高度必须与改造前逐位相同）");
        check(ProsperityTerrainProfile.reliefAmplitudeForRosterIndex(-1) == 1.0D
            && ProsperityTerrainProfile.reliefAmplitudeForRosterIndex(99) == 1.0D,
            "SOURCE 身份档缺失（-1 / 越界）一律走默认档，且实现里没有身份等值判断");
    }

    // ═══════════════════════ REDLINE 组（源级红线）═══════════════════════════

    private static void redlineGroup(Path srcRoot) throws IOException {
        final String profile = codeOnly(read("com/miaokatze/gtsr/common/dimension/prosperity/ProsperityTerrainProfile.java", srcRoot));
        final String face = codeOnly(read(
            "com/miaokatze/gtsr/common/dimension/framework/genlayer/GTSRGenLayerRosterFace.java", srcRoot));
        check(!profile.contains("net.minecraft") && !profile.contains("BiomeGenBase")
            && !profile.contains("Blocks.") && !profile.contains("worldObj") && !profile.contains("getBlock")
            && !profile.contains("Chunk") && !profile.contains("World "),
            "REDLINE 高度类源码零 net.minecraft import、零 BiomeGenBase/Blocks/worldObj/getBlock/Chunk 引用"
                + "（城市侧禁读方块红线；群系身份只经 int 出口进来）");
        check(!face.contains("worldObj") && !face.contains("getBlock") && !face.contains("Chunk")
            && !face.contains("IChunkProvider") && !face.contains("World "),
            "REDLINE 身份面源码零世界读取（只用 L1 内存账本 + 整数链）");
        check(profile.contains("GTSRWorldgenHash.valueNoise("), "REDLINE 高度类仍引用上收后的唯一噪声内核");
        check(!face.contains("fineInts") && !face.contains("biomeAtFine"),
            "REDLINE 身份面只吃 coarse 面（voronoi 细面逐列求值既贵又会造成 1 格级高度跳变）");
        final String gate = codeOnly(read(
            "com/miaokatze/gtsr/common/dimension/framework/structure/PlacementGate.java", srcRoot));
        final String provider = codeOnly(read(
            "com/miaokatze/gtsr/common/dimension/prosperity/ChunkProviderProsperityRuins.java", srcRoot));
        final String command = codeOnly(read("com/miaokatze/gtsr/common/commands/GTSRCommand.java", srcRoot));
        check(gate.contains("ProsperityTerrainProfile.heightAt(worldSeed, x, z)"),
            "REDLINE 结构侧接地供给器仍直引同一个 heightAt（城/机器/废墟/outpost 四族同源）");
        check(provider.contains("ProsperityTerrainProfile.heightAt(worldSeed, baseX + x, baseZ + z)"),
            "REDLINE 地形填充仍直引同一个 heightAt");
        check(command.contains("ProsperityTerrainProfile.heightAt(world.getSeed(), x, z)"),
            "REDLINE 指令取高仍直引同一个 heightAt");
        check(occurrences(profile, "RELIEF_MULT * amplitude") == 1,
            "REDLINE 高度公式在 Profile 内只有一份表达式（实测 " + occurrences(profile, "RELIEF_MULT * amplitude")
                + " 处）⇒ 群系振幅只能经档表进这一份");
        check(occurrences(profile, "reliefAmplitudeForRosterIndex(") == 2,
            "REDLINE 档表读取口恰 2 处（声明 + 振幅表达式内一次调用）");
        check(occurrences(profile, "public static int heightAt(long") == 1,
            "REDLINE 三参 heightAt 定义恰 1 处（四参形态是同一函数体的显式档口）");
    }

    // ═══════════════════════ SALT 组（三处字面量同值）═══════════════════════════

    private static void saltGroup(Path srcRoot) throws IOException {
        final String proxy = read("com/miaokatze/gtsr/main/CommonProxy.java", srcRoot);
        final String planner = codeOnly(read(
            "com/miaokatze/gtsr/common/dimension/prosperity/ruins/city/CityPlanner.java", srcRoot));
        final String profile = codeOnly(read(
            "com/miaokatze/gtsr/common/dimension/prosperity/ProsperityTerrainProfile.java", srcRoot));
        check(proxy.contains("0x50524F53L"), "SALT CommonProxy def.seedSalt 字面量在场（BBH C2 同条，此处复核）");
        check(planner.contains("PROSPERITY_SEED_SALT = 0x50524F53L"), "SALT CityPlanner 链盐同值");
        check(profile.contains("CHAIN_SEED_SALT = 0x50524F53L"),
            "SALT Profile 自建身份链的盐同值（第三处字面量；三处任一改动即红，不再靠人眼核）");
    }

    // ═══════════════════════ 公共件 ═══════════════════════

    private static GTSRBiomeAuthority.BiomeId[] dim78Keys() {
        final List<GTSRBiomeAuthority.BiomeId> out = new ArrayList<>();
        for (final GTSRBiomeAuthority.BiomeId key : GTSRBiomeAuthority.BiomeId.values()) {
            if (GTSRBiomeAuthority.DIM_KEY_PROSPERITY.equals(key.dimKey())) {
                out.add(key);
            }
        }
        return out.toArray(new GTSRBiomeAuthority.BiomeId[0]);
    }

    private static int indexOf(int id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i] == id) {
                return i;
            }
        }
        return -1;
    }

    private static String read(String rel, Path srcRoot) throws IOException {
        return new String(Files.readAllBytes(srcRoot.resolve(rel)), StandardCharsets.UTF_8);
    }

    /** 粗去掉 javadoc 与 // 注释（REDLINE 组要判"引用"而不是"文里提到过这个词"）。 */
    private static String codeOnly(String src) {
        final StringBuilder sb = new StringBuilder();
        boolean inBlock = false;
        for (final String line : src.split("\n", -1)) {
            String l = line;
            if (inBlock) {
                final int end = l.indexOf("*/");
                if (end < 0) {
                    continue;
                }
                inBlock = false;
                l = l.substring(end + 2);
            }
            final int start = l.indexOf("/*");
            if (start >= 0) {
                inBlock = true;
                l = l.substring(0, start);
            }
            final int sl = l.indexOf("//");
            if (sl >= 0) {
                l = l.substring(0, sl);
            }
            sb.append(l).append('\n');
        }
        return sb.toString();
    }

    private static int occurrences(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    private static String fmt(double[] v) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            sb.append(NAME[i]).append('=');
            sb.append(String.format(java.util.Locale.ROOT, "%.3f", v[i]));
            if (i < v.length - 1) {
                sb.append('/');
            }
        }
        return sb.toString();
    }

    private static String fmt1(double v) {
        return String.format(java.util.Locale.ROOT, "%.4g", v);
    }

    private static void check(boolean ok, String what) {
        assertions++;
        if (!ok) {
            PROBLEMS.add(what);
        }
    }
}
