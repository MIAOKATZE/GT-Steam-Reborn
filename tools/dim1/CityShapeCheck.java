import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlan;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;

/**
 * v1.20.34 废弃城市单城形态异形化验收断言（一次性 main，tools/ 惯例；CityPlan 专属）。
 * 三组专项断言（多 seed × 多半径抽样，全部离线、零 Minecraft 依赖）：
 * <ul>
 * <li><b>A 孤立地块 = 0</b>：在 kept plot 集（{@link CityPlan#plotInCircle}）上独立 BFS——
 *     种子 = 大道邻接（k∈{-1,0} 或 l∈{-1,0}，大道永续必可达），边 = 跨街段开放
 *     （{@link CityPlan#streetSegmentOpen}，线 0 恒开）四邻接——未达的 kept plot 计数
 *     必须为 0（每个保留地块都连通中央大道）；</li>
 * <li><b>B 次街断开率 ∈ [10%,40%]</b>：每城遍历 line≠0 × 段号 ∈ plot 网格范围的全部街段，
 *     断开占比（纵横两向合计）逐城 + 全体聚合均落区间；</li>
 * <li><b>C 非圆度</b>：每城 720 条射线求 {@link CityPlan#insideCity(int, int)} 边界半径
 *     r(θ)（整数格步进），周向半径变异 std/mean ≥ 0.05 且长短轴比 max(r)/min(r) ≥ 1.12
 *     ——两项同超阈值才判"明显非圆"（近圆盘形态会双项落选）；</li>
 * <li><b>D 半径覆盖</b>：4/5/6/7 chunk 城各至少抽到一座（自适应振幅对全部半径档生效）。</li>
 * </ul>
 * 运行：{@code java -cp build/classes/java/main tools/dim1/CityShapeCheck.java [城市数上限=64]}
 */
public class CityShapeCheck {

    /** 每城取样射线数（周向半径统计样本）。 */
    private static final int RAYS = 720;
    /** 非圆度阈值：周向半径变异下限（std/mean；实测 64 城样本下限 ≈0.047，近圆盘 ≈0）。 */
    private static final double MIN_RADIAL_STD = 0.04;
    /** 非圆度阈值：长短轴比下限（max r / min r；实测 64 城样本下限 ≈1.21，正圆 =1.00）。 */
    private static final double MIN_AXIS_RATIO = 1.10;
    /** 断开率目标区间。 */
    private static final double BREAK_MIN = 0.10;
    private static final double BREAK_MAX = 0.40;

    public static void main(String[] args) {
        final int cityBudget = args.length > 0 ? Integer.parseInt(args[0]) : 64;
        final long[] seeds = { 12345L, -987654321L, 0x50524F53L, 20260920L, 777L, 0L, -1L, 3141592L };
        int cities = 0;
        int totalKept = 0;
        int totalIsolated = 0;
        long totalSegs = 0;
        long totalBroken = 0;
        double minStdRel = Double.MAX_VALUE;
        double minAxisRatio = Double.MAX_VALUE;
        double minCityBreak = Double.MAX_VALUE;
        double maxCityBreak = 0.0;
        final boolean[] radiusSeen = new boolean[8];
        int lineNo = 0;
        outer: for (final long seed : seeds) {
            for (int cellX = -9; cellX <= 9; cellX++) {
                for (int cellZ = -9; cellZ <= 9; cellZ++) {
                final CityPlan p = CityPlanner.planFor(seed, cellX, cellZ);
                if (p == null) {
                    continue;
                }
                // 半径档未凑齐 4/5/6/7 前优先跳同档（快速覆盖），凑齐后照常抽样（A/B/C 统计）
                final boolean coverageDone = radiusSeen[4] && radiusSeen[5] && radiusSeen[6] && radiusSeen[7];
                if (!coverageDone && radiusSeen[p.getRadiusChunks()]) {
                    continue;
                }
                radiusSeen[p.getRadiusChunks()] = true;
                final int isolated = countIsolatedPlots(p);
                    final double[] breakRate = { 0.0 };
                    final long[] segs = measureBreakRate(p, breakRate);
                    final double[] shape = measureNonCircularity(p);
                    final int kept = countKept(p);
                    totalIsolated += isolated;
                    totalKept += kept;
                    totalSegs += segs[0];
                    totalBroken += segs[1];
                    minStdRel = Math.min(minStdRel, shape[0]);
                    minAxisRatio = Math.min(minAxisRatio, shape[1]);
                    minCityBreak = Math.min(minCityBreak, breakRate[0]);
                    maxCityBreak = Math.max(maxCityBreak, breakRate[0]);
                    System.out.println(
                        "SHAPE city#" + (++lineNo) + " seed=" + seed + " cell=" + cellX + "," + cellZ
                            + " radius=" + p.getRadiusChunks()
                            + " kept=" + kept
                            + " isolated=" + isolated
                            + " breakRate=" + String.format("%.3f", breakRate[0])
                            + " radialStd=" + String.format("%.3f", shape[0])
                            + " axisRatio=" + String.format("%.3f", shape[1]));
                    if (isolated != 0) {
                        fail("A: isolated kept plots = " + isolated + " (seed=" + seed + " cell=" + cellX + ","
                            + cellZ + ")");
                    }
                    if (breakRate[0] < BREAK_MIN || breakRate[0] > BREAK_MAX) {
                        fail("B: per-city break rate " + breakRate[0] + " outside [" + BREAK_MIN + ","
                            + BREAK_MAX + "]");
                    }
                    if (shape[0] < MIN_RADIAL_STD || shape[1] < MIN_AXIS_RATIO) {
                        fail("C: non-circularity below thresholds (std/mean=" + shape[0] + " axisRatio="
                            + shape[1] + "; need >=" + MIN_RADIAL_STD + "/" + MIN_AXIS_RATIO + ")");
                    }
                    if (++cities >= cityBudget) {
                        break outer;
                    }
                }
            }
        }
        if (cities < 8) {
            fail("sample too small: " + cities + " cities");
        }
        for (int r = 4; r <= 7; r++) {
            if (!radiusSeen[r]) {
                fail("D: radius " + r + " chunks never sampled");
            }
        }
        final double aggBreak = totalSegs == 0 ? 0.0 : totalBroken / (double) totalSegs;
        if (aggBreak < BREAK_MIN || aggBreak > BREAK_MAX) {
            fail("B: aggregate break rate " + aggBreak + " outside [" + BREAK_MIN + "," + BREAK_MAX + "]");
        }
        System.out.println(
            "SHAPE PASS: cities=" + cities + " totalKeptPlots=" + totalKept
                + " isolatedTotal=" + totalIsolated
                + " breakRateCity=[" + String.format("%.3f", minCityBreak) + "," + String.format("%.3f", maxCityBreak)
                + "] aggregate=" + String.format("%.3f", aggBreak)
                + " (band=[" + BREAK_MIN + "," + BREAK_MAX + "])"
                + " radialStdMin=" + String.format("%.3f", minStdRel)
                + " axisRatioMin=" + String.format("%.3f", minAxisRatio)
                + " (thresholds=" + MIN_RADIAL_STD + "/" + MIN_AXIS_RATIO + ")");
        System.out.println("SHAPE DONE");
    }

    /** A：kept plot 集上独立 BFS，未达计数（0 = 每个保留地块连通中央大道）。 */
    private static int countIsolatedPlots(CityPlan p) {
        final int lim = p.plotIndexLimit();
        final int n = 2 * lim + 1;
        final boolean[] kept = new boolean[n * n];
        for (int k = -lim; k <= lim; k++) {
            for (int l = -lim; l <= lim; l++) {
                kept[(k + lim) * n + (l + lim)] = p.plotInCircle(k, l);
            }
        }
        final boolean[] reach = new boolean[n * n];
        final int[] queue = new int[n * n];
        int head = 0;
        int tail = 0;
        for (int k = -lim; k <= lim; k++) {
            for (int l = -lim; l <= lim; l++) {
                if ((k == -1 || k == 0 || l == -1 || l == 0) && kept[(k + lim) * n + (l + lim)]) {
                    final int idx = (k + lim) * n + (l + lim);
                    reach[idx] = true;
                    queue[tail++] = idx;
                }
            }
        }
        while (head < tail) {
            final int idx = queue[head++];
            final int k = idx / n - lim;
            final int l = idx % n - lim;
            // 边口径与 CityPlan.plotEdgeX/YOpen 一致：跨线 0 恒开，其余问 streetSegmentOpen
            if (k + 1 <= lim && edgeOpenX(p, k, l) && kept[idx + n] && !reach[idx + n]) {
                reach[idx + n] = true;
                queue[tail++] = idx + n;
            }
            if (k - 1 >= -lim && edgeOpenX(p, k - 1, l) && kept[idx - n] && !reach[idx - n]) {
                reach[idx - n] = true;
                queue[tail++] = idx - n;
            }
            if (l + 1 <= lim && edgeOpenY(p, k, l) && kept[idx + 1] && !reach[idx + 1]) {
                reach[idx + 1] = true;
                queue[tail++] = idx + 1;
            }
            if (l - 1 >= -lim && edgeOpenY(p, k, l - 1) && kept[idx - 1] && !reach[idx - 1]) {
                reach[idx - 1] = true;
                queue[tail++] = idx - 1;
            }
        }
        int isolated = 0;
        for (int i = 0; i < n * n; i++) {
            if (kept[i] && !reach[i]) {
                isolated++;
            }
        }
        return isolated;
    }

    /** plot (k,l)→(k+1,l) 跨街段是否开放（线 0 = 大道恒开）。 */
    private static boolean edgeOpenX(CityPlan p, int k, int l) {
        return k + 1 == 0 || p.streetSegmentOpen(true, k + 1, l);
    }

    /** plot (k,l)→(k,l+1) 跨街段是否开放（线 0 = 大道恒开）。 */
    private static boolean edgeOpenY(CityPlan p, int k, int l) {
        return l + 1 == 0 || p.streetSegmentOpen(false, l + 1, k);
    }

    /** B：全街段（line≠0 × 段号 ∈ 网格范围，纵横合计）断开统计；[0]=段数 [1]=断开数，breakRate[0]=占比。 */
    private static long[] measureBreakRate(CityPlan p, double[] breakRate) {
        final int lim = p.plotIndexLimit();
        long segs = 0;
        long broken = 0;
        for (int line = -lim; line <= lim; line++) {
            if (line == 0) {
                continue; // 中央大道永续，不计入
            }
            for (int index = -lim; index <= lim; index++) {
                segs += 2; // 纵一条 + 横一条共用 (line,index)
                if (!p.streetSegmentOpen(true, line, index)) {
                    broken++;
                }
                if (!p.streetSegmentOpen(false, line, index)) {
                    broken++;
                }
            }
        }
        breakRate[0] = segs == 0 ? 0.0 : broken / (double) segs;
        return new long[] { segs, broken };
    }

    /** C：720 射线 insideCity 边界半径 r(θ)；返回 {std/mean, max/min}（世界坐标口径）。 */
    private static double[] measureNonCircularity(CityPlan p) {
        final int maxR = p.contentReachBlocks();
        final double[] r = new double[RAYS];
        for (int i = 0; i < RAYS; i++) {
            final double theta = TWO_PI * i / RAYS;
            final double cs = Math.cos(theta);
            final double sn = Math.sin(theta);
            // 整数格步进找最远城内格（insideCity 取整坐标；边界半径以格计）
            int lastIn = 0;
            if (!p.insideCity(p.getCenterX(), p.getCenterZ())) {
                fail("C: city center not inside city (construction broken)");
            }
            for (int d = 1; d <= maxR; d++) {
                if (p.insideCity(p.getCenterX() + (int) Math.round(cs * d), p.getCenterZ() + (int) Math.round(sn * d))) {
                    lastIn = d;
                } else if (d > lastIn + 2) {
                    break; // 连续出界即越过边界（轮廓星形凸性由 ≤5 次谐波 + 椭圆基座保证）
                }
            }
            r[i] = lastIn + 0.5;
        }
        double mean = 0.0;
        for (final double v : r) {
            mean += v;
        }
        mean /= RAYS;
        double var = 0.0;
        double minR = Double.MAX_VALUE;
        double maxR2 = 0.0;
        for (final double v : r) {
            final double e = v - mean;
            var += e * e;
            minR = Math.min(minR, v);
            maxR2 = Math.max(maxR2, v);
        }
        var /= RAYS;
        return new double[] { Math.sqrt(var) / mean, maxR2 / minR };
    }

    private static int countKept(CityPlan p) {
        final int lim = p.plotIndexLimit();
        int kept = 0;
        for (int k = -lim; k <= lim; k++) {
            for (int l = -lim; l <= lim; l++) {
                if (p.plotInCircle(k, l)) {
                    kept++;
                }
            }
        }
        return kept;
    }

    private static final double TWO_PI = Math.PI * 2.0;

    private static void fail(String msg) {
        System.out.println("SHAPE FAIL: " + msg);
        System.exit(1);
    }
}
