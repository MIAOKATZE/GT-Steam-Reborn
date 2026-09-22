import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * <b>v1.20.39 T4 机检：dim78 Voronoi 河流强度场形态（plan §3.1/§3.2 校准回路的判据面）</b>。
 * 纯模型驱动（{@link GTSRVoronoiRiverField}/{@link ProsperityTerrainProfile} 均零世界读取，
 * 无需离线装配账本：按档测量传显式 rosterIndex；heightAt 链在未装配 JVM 走默认档，河谷
 * 压低链仍在）。P17RiverNetworkCheck（旧轴向等距线模型）已随旧模型删除，本判据接替其位。
 *
 * <p>
 * ═══ 七组断言（全部实跑；阈值 = plan §8 验收指标，统计功效参数全部从生产常数派生——
 * v1.20.38 纪律 2：不写字面量窗口）═══
 * <ul>
 * <li><b>A 合同面</b>：strengthAt ∈ [-1,0] 且确定性（双跑逐位）、换 seed 必换场、档表 5 元
 * （T5/T8：4 selector + sanzu 主干宽河档）、默认档=常态河、trunkAt/lakeAt 已激活且确定性
 * （T5/T8 反转原 T4 占位钉）、
 * VALLEY_LEVEL==WET_MIN（平底域==置水域不变量）、沼泽档 widthScale==1.6、床档表值域；</li>
 * <li><b>B 河宽分布</b>：常态（草原/森林档）水道半宽 = 河道中心走到 s&lt;WET_MIN 的距离，
 * 中位数 ∈ [5,8] 格（plan 目标带）；</li>
 * <li><b>C 谷坡半宽</b>：水缘到谷缘（s==0）的距离 ∈ [20,40] 格；</li>
 * <li><b>D 蜿蜒度</b>：中心线追踪（步长/步数从 LARGE_BEND_SCALE 派生，覆盖 ≥3 个大弯波长）
 * 曲折率 = 路长/端距 &gt; 1.2；</li>
 * <li><b>E 支流连通</b>：Voronoi 三叉点（2×2 列块内 ≥3 个不同最近细胞签名）非零且成量
 * ——边界网络天然连通的分叉证据；</li>
 * <li><b>F 荒漠断流</b>：荒漠档河核列 wetAt 占比 ∈ [0.2,0.4]（约 30%±10 河段有水，串珠断流）；</li>
 * <li><b>G 沼泽河宽 ×1.6</b>：沼泽档/常态档水道半宽比 ∈ [1.3,1.9]（×1.6 断言带）；外加
 * heightAt 河谷集成：河核列 heightAt 与 bedAt 偏差 ≤1.5（压低链真接进了高度）。</li>
 * </ul>
 *
 * <p>
 * <b>用法</b>：{@code java RiverMorphologyCheck}（退出码 0 = 全绿）。采样种子固定 ⇒ 双跑逐位一致。
 */
public final class RiverMorphologyCheck {

    /** 本判据世界种子（与兄弟片不同值，免得读数互串）。 */
    static final long SEED = 0x7269_7665_4C42L;

    // ── 统计功效参数（派生式：全部从 GTSRVoronoiRiverField 生产常数导出，无字面量窗口）──
    /** 采样窗半径（格）：≥3 个 Voronoi 细胞 ⇒ 窗内必有 ≥9 条边界。 */
    static final int SCAN_EXTENT = (int) (GTSRVoronoiRiverField.SEPARATION * 3);
    /** 采样步距（格）：SEPARATION/128 = 8 < 常态水道全宽（~2×5.8）⇒ 河道不会被步距跳空。 */
    static final int SCAN_STRIDE = (int) (GTSRVoronoiRiverField.SEPARATION / 128);
    /** 河道中心样本数下限：max(32, SEPARATION/32) —— 中位数判据的最小样本量。 */
    static final int N_CENTERS = Math.max(32, (int) (GTSRVoronoiRiverField.SEPARATION / 32));
    /** 半宽走查上限（格）：SEPARATION/7 = 150 > 4×谷半宽上限 ⇒ 谷缘必在限内。 */
    static final int WALK_LIMIT = (int) (GTSRVoronoiRiverField.SEPARATION / 7);
    /** 中心线追踪步长（格）与步数：总路长 = 3×LARGE_BEND_SCALE（覆盖 ≥3 个大弯波长）。 */
    static final int TRACE_STEP = (int) (GTSRVoronoiRiverField.SMALL_BEND_SCALE / 13);
    static final int TRACE_STEPS = (int) (GTSRVoronoiRiverField.LARGE_BEND_SCALE * 3) / TRACE_STEP;
    /** 追踪段数：SEPARATION/SMALL_BEND_SCALE = 13 段。 */
    static final int N_TRACES = (int) (GTSRVoronoiRiverField.SEPARATION / GTSRVoronoiRiverField.SMALL_BEND_SCALE);
    /** 三叉点扫描步距（格）：SEPARATION/64 = 16 < 三叉点邻域尺度。 */
    static final int FORK_STRIDE = (int) (GTSRVoronoiRiverField.SEPARATION / 64);

    /** 名册下标（0 锈蚀草原 / 1 齿轮森林 / 2 黄铜荒漠 / 3 喷气沼泽）。 */
    static final String[] ROSTER = { "草原", "森林", "荒漠", "沼泽" };

    static int assertions;
    static int failures;
    static final List<String> LINES = new ArrayList<String>();

    public static void main(String[] args) {
        groupA();
        final List<int[]> centers = findCenters();
        say(
            "S-READ 河道中心样本=" + centers.size() + "（扫描窗 ±" + SCAN_EXTENT + " 步距 " + SCAN_STRIDE
                + "）");
        groupB(centers);
        groupC(centers);
        groupD(centers);
        groupE();
        groupF();
        groupG(centers);
        report();
    }

    // ══════════════════════ A 合同面 ══════════════════════

    static void groupA() {
        double min = 0.0D;
        double max = -1.0D;
        boolean deterministic = true;
        boolean seedSensitive = false;
        // 采样域取 ±SCAN_EXTENT/2（±64 的小窗对两个 seed 都可能整窗无河 ⇒ 假"零差异"）
        final int lim = SCAN_EXTENT / 2;
        for (int zi = -lim; zi <= lim; zi += SCAN_STRIDE * 4) {
            for (int xi = -lim; xi <= lim; xi += SCAN_STRIDE * 4) {
                final double v = GTSRVoronoiRiverField.strengthAt(SEED, xi, zi, 0);
                min = Math.min(min, v);
                max = Math.max(max, v);
                if (v != GTSRVoronoiRiverField.strengthAt(SEED, xi, zi, 0)) {
                    deterministic = false;
                }
                if (v != GTSRVoronoiRiverField.strengthAt(SEED ^ 0x5AL, xi, zi, 0)) {
                    seedSensitive = true;
                }
            }
        }
        check("A1 strengthAt 值域 ⊆ [-1,0]（0=无影响、-1=河道中心；全域采样）",
            min >= -1.0D && min <= 0.0D && max <= 0.0D, "min=" + f3(min) + " max=" + f3(max));
        check("A2 确定性：同 seed 同坐标双跑逐位一致（含 ThreadLocal disk 缓存命中/未命中两态）",
            deterministic, "双跑出现漂移");
        check("A3 换 seed 必换场（不是常数场）", seedSensitive, "换 seed 后采样域内零差异");
        // v1.20.39 T5/T8 重钉（plan §3.3）：A4 由"T4 骨架占位反向钉（恒 0）"反转为
        // "主干/巨湖场已按设计激活且确定性"——激活本身是 T5 的被验对象，占位钉随之退役；
        // 激活统计量（覆盖份额/巨湖/少支流/细长）由 SanzuTrunkCoverageCheck 专判，此处只钉合同面。
        boolean trunkShapeOk = true;
        boolean trunkDeterministic = true;
        boolean trunkAlive = false;
        // 采样域派生自主干尺度（T5/T8）：主干带波长约 TRUNK_SCALE，±TRUNK_SCALE/2 窗 + SEPARATION/16
        // 步距保证窗内至少有一条主干带脊线（±256 小窗实测 alive=false——带外整窗无 trunk 是几何事实）。
        final int trunkLim = (int) (GTSRVoronoiRiverField.TRUNK_SCALE / 2);
        final int trunkStride = (int) (GTSRVoronoiRiverField.SEPARATION / 16);
        for (int zi = -trunkLim; zi <= trunkLim && trunkShapeOk; zi += trunkStride) {
            for (int xi = -trunkLim; xi <= trunkLim; xi += trunkStride) {
                final double tv = GTSRVoronoiRiverField.trunkAt(SEED, xi, zi);
                final double lv = GTSRVoronoiRiverField.lakeAt(SEED, xi, zi);
                if (tv != GTSRVoronoiRiverField.trunkAt(SEED, xi, zi)
                    || lv != GTSRVoronoiRiverField.lakeAt(SEED, xi, zi)) {
                    trunkDeterministic = false;
                }
                if (tv < 0.0D || tv > 1.0D - GTSRVoronoiRiverField.SANZU_TRUNK_EDGE || lv < 0.0D || lv > 1.0D) {
                    trunkShapeOk = false;
                }
                if (tv > 0.0D) {
                    trunkAlive = true;
                }
            }
        }
        check("A4 trunkAt/lakeAt 已激活（T5 接线）且确定性：trunk ∈ [0,1-EDGE]、lake ∈ [0,1]、采样窗非恒零",
            trunkShapeOk && trunkDeterministic && trunkAlive,
            "shapeOk=" + trunkShapeOk + " det=" + trunkDeterministic + " alive=" + trunkAlive);
        final GTSRVoronoiRiverField.RiverStyle[] styles = GTSRVoronoiRiverField.RIVER_STYLE_BY_ROSTER;
        // v1.20.39 T5/T8 重钉（plan §5 档表族 4→5 约定）：第 5 元 = sanzu 主干宽河档
        //（widthScale == TRUNK_WIDTH_SCALE=3.5，无浅滩/无断流门——主干带内全水域）。
        check("A5 档表 5 元（前 4 元草原/森林=常态+浅滩、荒漠=断流、沼泽=沼地河；[4]=sanzu 主干宽河档）",
            styles.length == 5 && styles[0].shoals && styles[1].shoals && styles[2].wetGated
                && !styles[3].shoals && !styles[3].wetGated
                && styles[4].widthScale == GTSRVoronoiRiverField.TRUNK_WIDTH_SCALE
                && !styles[4].shoals && !styles[4].wetGated,
            "length=" + styles.length + " sanzuWidthScale=" + (styles.length > 4 ? styles[4].widthScale : -1));
        check("A6 越界/缺席名册一律默认档（常态河）且沼泽 widthScale == 1.6×常态",
            GTSRVoronoiRiverField.styleForRosterIndex(-1) == GTSRVoronoiRiverField.DEFAULT_STYLE
                && GTSRVoronoiRiverField.styleForRosterIndex(99) == GTSRVoronoiRiverField.DEFAULT_STYLE
                && styles[3].widthScale == 1.6D * styles[0].widthScale,
            "widthScale=" + styles[3].widthScale + "/" + styles[0].widthScale);
        check("A7 床档表值域：常态床目标=64.5（水面 68 ⇒ 水深 2-5 源头）、沼泽床 ∈ [66.5,67.5]（水面近地）",
            styles[0].bedTarget == GTSRVoronoiRiverField.BED_TARGET
                && GTSRVoronoiRiverField.BED_TARGET == 64.5D
                && styles[3].bedTarget + styles[3].bedNoiseAmp >= 66.5D
                && styles[3].bedTarget - styles[3].bedNoiseAmp <= 67.5D,
            "normal=" + styles[0].bedTarget + " swamp=" + styles[3].bedTarget + "±" + styles[3].bedNoiseAmp);
        check("A8 VALLEY_LEVEL == WET_MIN（平底域精确等于置水域：水下平床、水外起坡——高度链不变量）",
            GTSRVoronoiRiverField.VALLEY_LEVEL == GTSRVoronoiRiverField.WET_MIN,
            "valley=" + GTSRVoronoiRiverField.VALLEY_LEVEL + " wetMin=" + GTSRVoronoiRiverField.WET_MIN);
    }

    // ══════════════════════ 河道中心采样 ══════════════════════

    /** s 场局部极大（≥ 四邻）即河道中心候选；按扫描序去重（相互隔开 ≥ WALK_LIMIT/2）。 */
    static List<int[]> findCenters() {
        final List<int[]> out = new ArrayList<int[]>();
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT && out.size() < N_CENTERS; z += SCAN_STRIDE) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT && out.size() < N_CENTERS; x += SCAN_STRIDE) {
                final double v = s(x, z);
                if (v < 0.8D) {
                    continue; // 只在河核强度带内找中心
                }
                if (v < s(x + SCAN_STRIDE, z) || v < s(x - SCAN_STRIDE, z) || v < s(x, z + SCAN_STRIDE)
                    || v < s(x, z - SCAN_STRIDE)) {
                    continue;
                }
                boolean dup = false;
                for (final int[] c : out) {
                    if (Math.abs(c[0] - x) <= WALK_LIMIT / 2 && Math.abs(c[1] - z) <= WALK_LIMIT / 2) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    out.add(new int[] { x, z });
                }
            }
        }
        return out;
    }

    static double s(int x, int z) {
        return -GTSRVoronoiRiverField.strengthAt(SEED, x, z, 0);
    }

    /**
     * 从 (x,z) 沿 (dx,dz) 走到 s ≤ 阈值的步数（-1 = 到 {@link #WALK_LIMIT} 仍在域内，即走到了
     * "顺河"方向——河沿走向连续数百格，属几何事实不是异常）。谷缘判定用 ≤（s 恒 ≥ 0，
     * 严格 < 会永真走不出谷）。
     */
    static int walkOut(int x, int z, int dx, int dz, double threshold) {
        for (int r = 1; r <= WALK_LIMIT; r++) {
            if (s(x + dx * r, z + dz * r) <= threshold) {
                return r - 1; // 阈值前的最后一格仍在域内（中心到域缘的半宽）
            }
        }
        return -1;
    }

    /**
     * 某档的水道半宽：四轴方向的<b>有界</b>方向取最小（顺河方向无界、跳过；四向全无界 = 三叉口，
     * 样本剔除记 -1）。斜向河的轴向往返会量到 w·√2 量级 ⇒ 读数是 [w, w√2] 的分布，判据取中位。
     */
    static int waterHalfWidth(int x, int z, int rosterIndex) {
        int best = Integer.MAX_VALUE;
        for (int d = 0; d < 4; d++) {
            final int dx = d == 0 ? 1 : d == 1 ? -1 : 0;
            final int dz = d == 2 ? 1 : d == 3 ? -1 : 0;
            int r = 1;
            while (r <= WALK_LIMIT
                && -GTSRVoronoiRiverField.strengthAt(SEED, x + dx * r, z + dz * r, rosterIndex)
                    >= GTSRVoronoiRiverField.WET_MIN) {
                r++;
            }
            if (r <= WALK_LIMIT) {
                best = Math.min(best, r - 1);
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    /** 某档的谷缘半宽（走到 s==0 的有界方向最小；全无界记 -1）。 */
    static int valleyHalfWidth(int x, int z) {
        int best = Integer.MAX_VALUE;
        for (int d = 0; d < 4; d++) {
            final int dx = d == 0 ? 1 : d == 1 ? -1 : 0;
            final int dz = d == 2 ? 1 : d == 3 ? -1 : 0;
            final int w = walkOut(x, z, dx, dz, 0.0D);
            if (w >= 0) {
                best = Math.min(best, w);
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    // ══════════════════════ B 河宽分布 ══════════════════════

    static void groupB(List<int[]> centers) {
        final List<Integer> widths = new ArrayList<Integer>();
        for (final int[] c : centers) {
            final int w = waterHalfWidth(c[0], c[1], 0);
            if (w > 0) {
                widths.add(Integer.valueOf(w));
            }
        }
        final double median = median(widths);
        say("B-READ 常态水道半宽 样本=" + widths.size() + " p10/中位/p90=" + pct(widths, 10) + "/"
            + f3(median) + "/" + pct(widths, 90));
        check("B1 常态河半宽中位数 ∈ [5,8] 格（plan §3.1 目标带；校准回路 WIDTH 的验收读数）",
            widths.size() >= N_CENTERS / 2 && median >= 5.0D && median <= 8.0D,
            "中位=" + f3(median) + " n=" + widths.size());
    }

    // ══════════════════════ C 谷坡半宽 ══════════════════════

    static void groupC(List<int[]> centers) {
        final List<Integer> slopes = new ArrayList<Integer>();
        for (final int[] c : centers) {
            final int valley = valleyHalfWidth(c[0], c[1]);
            final int water = waterHalfWidth(c[0], c[1], 0);
            if (valley > 0 && water > 0) {
                slopes.add(Integer.valueOf(valley - water));
            }
        }
        final double median = median(slopes);
        say("C-READ 谷坡半宽（水缘→谷缘 s==0）样本=" + slopes.size() + " p10/中位/p90=" + pct(slopes, 10)
            + "/" + f3(median) + "/" + pct(slopes, 90));
        check("C1 谷坡半宽中位数 ∈ [20,40] 格（plan §3.1 校准目标；valleyLevel 校准回路的验收读数）",
            slopes.size() >= N_CENTERS / 2 && median >= 20.0D && median <= 40.0D,
            "中位=" + f3(median) + " n=" + slopes.size());
    }

    // ══════════════════════ D 蜿蜒度 ══════════════════════

    static void groupD(List<int[]> centers) {
        double sum = 0.0D;
        int n = 0;
        double worst = Double.MAX_VALUE;
        for (int k = 0; k < centers.size() && n < N_TRACES; k += Math.max(1, centers.size() / N_TRACES)) {
            final int[] c = centers.get(k);
            // 初始朝向 = 16 向试探中 s 最大者（沿河起歩，不横切）
            double hx = 1.0D;
            double hz = 0.0D;
            double bestInit = -1.0D;
            for (int a = 0; a < 16; a++) {
                final double ang = a * Math.PI / 8.0D;
                final double nx = Math.cos(ang);
                final double nz = Math.sin(ang);
                final double v = s((int) Math.round(c[0] + nx * TRACE_STEP),
                    (int) Math.round(c[1] + nz * TRACE_STEP));
                if (v > bestInit) {
                    bestInit = v;
                    hx = nx;
                    hz = nz;
                }
            }
            int x = c[0];
            int z = c[1];
            double path = 0.0D;
            for (int t = 0; t < TRACE_STEPS; t++) {
                int bx = x;
                int bz = z;
                int bhx = 1;
                int bhz = 0;
                double bestScore = -Double.MAX_VALUE;
                for (int a = 0; a < 16; a++) {
                    final double ang = a * Math.PI / 8.0D;
                    final double nx = Math.cos(ang);
                    final double nz = Math.sin(ang);
                    // 小前向偏置：防 180° 折返，但允许在叉口急转（大偏置会把追踪锁死在直线上）
                    final int tx = (int) Math.round(x + nx * TRACE_STEP);
                    final int tz = (int) Math.round(z + nz * TRACE_STEP);
                    final double score = s(tx, tz) + 0.05D * (nx * hx + nz * hz);
                    if (score > bestScore) {
                        bestScore = score;
                        bx = tx;
                        bz = tz;
                        bhx = tx - x;
                        bhz = tz - z;
                    }
                }
                final double step = Math.hypot(bx - x, bz - z);
                if (step <= 0.0D) {
                    break;
                }
                final double nh = Math.hypot(bhx, bhz);
                path += step;
                x = bx;
                z = bz;
                hx = bhx / nh;
                hz = bhz / nh;
            }
            final double straight = Math.hypot(x - c[0], z - c[1]);
            if (path > 0.0D && straight > TRACE_STEP) {
                final double sinuosity = path / straight;
                sum += sinuosity;
                worst = Math.min(worst, sinuosity);
                n++;
            }
        }
        final double mean = n == 0 ? 0.0D : sum / n;
        say("D-READ 蜿蜒度（中心线追踪 长=" + (TRACE_STEPS * TRACE_STEP) + " 格/段）样本=" + n + " 均="
            + f3(mean) + " 最直段=" + f3(worst == Double.MAX_VALUE ? 0 : worst));
        check("D1 中心线曲折率均值 > 1.2（两级 Disk jitter 蜿蜒的验收读数）",
            n >= N_TRACES / 2 && mean > 1.2D, "均=" + f3(mean) + " n=" + n);
    }

    // ══════════════════════ E 支流连通（三叉点） ══════════════════════

    static void groupE() {
        int forks = 0;
        int checked = 0;
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT; z += FORK_STRIDE) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT; x += FORK_STRIDE) {
                checked++;
                final long h00 = cellHash(x, z);
                final long h10 = cellHash(x + FORK_STRIDE, z);
                final long h01 = cellHash(x, z + FORK_STRIDE);
                final long h11 = cellHash(x + FORK_STRIDE, z + FORK_STRIDE);
                final List<Long> distinct = new ArrayList<Long>();
                for (final long h : new long[] { h00, h10, h01, h11 }) {
                    if (!distinct.contains(Long.valueOf(h))) {
                        distinct.add(Long.valueOf(h));
                    }
                }
                if (distinct.size() >= 3) {
                    forks++;
                }
            }
        }
        say("E-READ 三叉点（2×2 列块 ≥3 个最近细胞）=" + forks + " / 扫描块 " + checked + "（步距 "
            + FORK_STRIDE + " ⇒ 实测顶点密度 " + f3(forks / (double) checked / (FORK_STRIDE * FORK_STRIDE))
            + " 块⁻²）");
        // 理论顶点密度 ≈ 每细胞 2 顶点 / SEPARATION²（Voronoi 恒等式）；断言带 = 理论值的 [1/4, 4] 倍
        final double theory = 2.0D / (GTSRVoronoiRiverField.SEPARATION * GTSRVoronoiRiverField.SEPARATION);
        final double measured = forks / (double) checked / (FORK_STRIDE * FORK_STRIDE);
        check("E1 支流分叉非零且密度与 Voronoi 理论同阶（实测 ∈ 理论 2/SEPARATION² 的 [1/4,4] 倍"
            + "——河网连通的分叉证据，不是孤立平行线）",
            forks > 0 && measured >= theory / 4.0D && measured <= theory * 4.0D,
            "实测=" + f3(measured) + " 理论=" + f3(theory) + " forks=" + forks);
    }

    static long cellHash(int x, int z) {
        return GTSRVoronoiRiverField.nearestCellHash(SEED, x, z);
    }

    // ══════════════════════ F 荒漠断流 ══════════════════════

    static void groupF() {
        long core = 0;
        long wet = 0;
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT; z += SCAN_STRIDE) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT; x += SCAN_STRIDE) {
                if (-GTSRVoronoiRiverField.strengthAt(SEED, x, z, 2) < GTSRVoronoiRiverField.WET_MIN) {
                    continue;
                }
                core++;
                if (GTSRVoronoiRiverField.wetAt(SEED, x, z, 2)) {
                    wet++;
                }
            }
        }
        final double share = core == 0 ? -1.0D : wet / (double) core;
        say("F-READ 荒漠档河核列=" + core + " 过水列=" + wet + " 占比=" + f3(100.0D * share) + "%"
            + "（WET_EDGE=" + GTSRVoronoiRiverField.WET_EDGE + " 连续噪声闸）");
        check("F1 荒漠断流：过水占比 ∈ [20%,40%]（约 30%±10 ⇒ 串珠断流，不是全干也不是全满）",
            core >= N_CENTERS && share >= 0.2D && share <= 0.4D, "占比=" + f3(100.0D * share) + "% n=" + core);
    }

    // ══════════════════════ G 沼泽河宽 + heightAt 集成 ══════════════════════

    static void groupG(List<int[]> centers) {
        final List<Integer> normal = new ArrayList<Integer>();
        final List<Integer> swamp = new ArrayList<Integer>();
        for (final int[] c : centers) {
            final int wn = waterHalfWidth(c[0], c[1], 0);
            final int ws = waterHalfWidth(c[0], c[1], 3);
            if (wn > 0) {
                normal.add(Integer.valueOf(wn));
            }
            if (ws > 0) {
                swamp.add(Integer.valueOf(ws));
            }
        }
        final double ratio = median(swamp) / Math.max(1.0D, median(normal));
        say("G-READ 水道半宽 常态中位=" + f3(median(normal)) + " 沼泽中位=" + f3(median(swamp)) + " 比值="
            + f3(ratio) + "（档表 widthScale=1.6）");
        check("G1 沼泽河宽 ≈ 常态 ×1.6（档表乘子的行为读数；断言带 [1.3,1.9]）",
            !swamp.isEmpty() && ratio >= 1.3D && ratio <= 1.9D, "比值=" + f3(ratio));
        // heightAt 集成：河核列地表 = round(bed)（高地支）或低地原样 h0 ≤ bed+1（防抬升支）
        // ⇒ 上界 h1 ≤ bed+1 恒成立；|h1-bed| ≤1.5 的平底占比应是绝大多数（低地支是少数）。
        int n = 0;
        int over = 0;
        int flat = 0;
        double worstOver = 0.0D;
        for (final int[] c : centers) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    final int x = c[0] + dx;
                    final int z = c[1] + dz;
                    if (s(x, z) < GTSRVoronoiRiverField.WET_MIN) {
                        continue;
                    }
                    final int h = ProsperityTerrainProfile.heightAt(SEED, x, z);
                    final double bed = GTSRVoronoiRiverField.bedAt(SEED, x, z, -1);
                    n++;
                    final double dev = h - bed;
                    if (dev > 1.5D) {
                        over++;
                        worstOver = Math.max(worstOver, dev);
                    }
                    if (Math.abs(dev) <= 1.5D) {
                        flat++;
                    }
                }
            }
        }
        final double flatShare = n == 0 ? 0.0D : flat / (double) n;
        say("G-READ heightAt 河谷集成：河核列 n=" + n + " 超上界=" + over + "（最坏 +" + f3(worstOver)
            + "）平底占比=" + f3(100.0D * flatShare) + "%（bedAt 按默认档——未装配 JVM 与 heightCore"
            + " 同走 -1，同一条链；低地支 |dev|>1.5 属设计内：h0 ≤ bed+1 不动，防抬升）");
        check("G2a 河谷压低真并入 heightAt：河核列 h1 ≤ bed+1 恒成立（两支共同上界，无抬升无漏压）",
            n > 0 && over == 0, "over=" + over + "/" + n + " worst=+" + f3(worstOver));
        check("G2b 平底支占绝大多数（≥80% 河核列 h1 = round(bed) ±1.5；其余是低地原样支）",
            flatShare >= 0.8D, "flat=" + f3(100.0D * flatShare) + "%");
        int rangeBad = 0;
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT; z += SCAN_STRIDE * 2) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT; x += SCAN_STRIDE * 2) {
                final int h = ProsperityTerrainProfile.heightAt(SEED, x, z);
                if (h < 40 || h > 110) {
                    rangeBad++;
                }
            }
        }
        check("G3 heightAt 值域 [40,110] 不变（压低链不破钳制契约）", rangeBad == 0, "越界=" + rangeBad);
    }

    // ══════════════════════ 统计小件 ══════════════════════

    static double median(List<Integer> v) {
        if (v.isEmpty()) {
            return -1.0D;
        }
        final int[] a = new int[v.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = v.get(i).intValue();
        }
        Arrays.sort(a);
        return a.length % 2 == 1 ? a[a.length / 2] : (a[a.length / 2 - 1] + a[a.length / 2]) / 2.0D;
    }

    static double pct(List<Integer> v, int p) {
        if (v.isEmpty()) {
            return -1.0D;
        }
        final int[] a = new int[v.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = v.get(i).intValue();
        }
        Arrays.sort(a);
        return a[Math.min(a.length - 1, Math.max(0, (int) Math.round(a.length * p / 100.0D - 0.5D)))];
    }

    static String f3(double v) {
        return String.format("%.3f", Double.valueOf(v));
    }

    static void say(String line) {
        LINES.add(line);
        System.out.println(line);
    }

    static void check(String label, boolean ok, String detail) {
        assertions++;
        if (!ok) {
            failures++;
        }
        say((ok ? "PASS " : "FAIL ") + label + (detail.isEmpty() ? "" : " ｜ " + detail));
    }

    static void report() {
        say("═══ RiverMorphologyCheck assertions=" + assertions + " failures=" + failures + " ═══");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
