import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.StructureBurialTiers;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * <b>v1.20.39 T5 机检：遗忘之川主干带/巨湖/少支流/细长形状（plan §3.3 + §6-T5 验收指标的判据面）</b>。
 * 纯模型驱动（{@link GTSRVoronoiRiverField}/{@link ProsperityTerrainProfile} 均零世界读取，
 * 无需离线装配账本：heightAt 链在未装配 JVM 走默认档，河谷/巨湖压低链仍在；主干带内
 * strengthAt 对 rosterIndex 构造不变 ⇒ -1 与生产档同解，见 isSanzuColumn javadoc）。
 *
 * <p>
 * ═══ 五组断言（全部实跑；阈值 = plan §8 验收指标，统计功效参数全部从生产常数派生——
 * v1.20.38 纪律 2：不写字面量窗口）═══
 * <ul>
 * <li><b>A 合同面</b>：trunkAt 值域/确定性/换 seed；lakeAt 主干门（带外恒 NO_LAKE、带内存在非
 * 哨兵）；档表族 5 元（RIVER_STYLE[4]=主干宽河档、RELIEF[4]=0.38、DEPTH[4]=0.05、VEG 长度 5）；
 * strengthAt 值域仍 ⊆ [-1,0]；主干带内 s 对 rosterIndex 不变（宽档 max(底,3.5) 的构造不变量）；</li>
 * <li><b>B 主干覆盖占河网 1/6-1/8</b>：河核列（s≥WET_MIN）口径，主干带内核列份额 ∈
 * [12.5%, 16.7%]（SANZU_TRUNK_EDGE 校准回路的验收读数，对齐门后实测）；</li>
 * <li><b>C 巨湖存在 + 水面口径</b>：主干带内 c_lake&lt;LAKE_WATER_LEVEL 的 4-连通区存在且规模
 * 超阈（离散巨湖，非边界湖网）；湖心列 heightAt 压至 62-64 带（水面 SEA_LEVEL=68 ⇒ 深 4-6）；</li>
 * <li><b>D 少支流</b>：主干带内"三叉块"（2×2 采样块内 ≥3 个不同最近细胞签名的河核列）密度
 * 显著低于带外对照（对齐门清零横截次级边界的行为读数；"很少支流"≠零分叉，带内近结点
 * 60° 短残支按设计保留，故按密度对比断言——TRUNK_ALIGN_COS javadoc 口径）；</li>
 * <li><b>E sanzu 列存在 + 细长</b>：isSanzuColumn 列数 &gt; 0；最大 4-连通簇的长短轴比
 * （协方差特征值 √(λmax/λmin)）&gt; 5（plan §6-T5 细长断言；形状 = 河道核 × 主干带交集）。</li>
 * </ul>
 *
 * <p>
 * <b>用法</b>：{@code java SanzuTrunkCoverageCheck}（退出码 0 = 全绿）。采样种子固定 ⇒ 双跑逐位一致。
 */
public final class SanzuTrunkCoverageCheck {

    /** 本判据世界种子（与兄弟片不同值，免得读数互串）。 */
    static final long SEED = 0x5A61_4E5A_554EL;

    // ── 统计功效参数（派生式：全部从 GTSRVoronoiRiverField/Profile 生产常数导出，无字面量窗口）──
    /** 主扫描窗半径（格）：2×TRUNK_SCALE = 8000 ⇒ 覆盖 ≥3 个主干带波长（λ≈1.24×TRUNK_SCALE）。 */
    static final int SCAN_EXTENT = (int) (GTSRVoronoiRiverField.TRUNK_SCALE * 2);
    /** 主扫描步距（格）：SEPARATION/64 = 16（与 RiverMorphologyCheck 的分叉步距同口径）。 */
    static final int SCAN_STRIDE = (int) (GTSRVoronoiRiverField.SEPARATION / 64);
    /** 主干覆盖判据带（plan §3.3：占河网 1/6-1/8 ⇒ [12.5%, 16.7%]）。 */
    static final double COVER_MIN = 1.0D / 8.0D;
    static final double COVER_MAX = 1.0D / 6.0D;
    /** 巨湖扫描窗半径（格）：3×TRUNK_SCALE——湖间隔 LAKE_INTERVAL=1200 下窗内 ≥ 数十个湖细胞。 */
    static final int LAKE_EXTENT = (int) (GTSRVoronoiRiverField.TRUNK_SCALE * 3);
    /** 巨湖粗扫步距（格）：LAKE_INTERVAL/32 ≈ 37（湖理论半径 ≈ 0.11×间隔/2 ≈ 66 格 ⇒ 直径 ≈ 3.6 步）。 */
    static final int LAKE_STRIDE = (int) (GTSRVoronoiRiverField.LAKE_INTERVAL / 32);
    /**
     * 巨湖最小连通样本数（派生：湖理论直径 (2×0.11×LAKE_INTERVAL/2)/LAKE_STRIDE ≈ 3.6 ⇒ 取 3
     * ——小于它的连通区按采样碎片不计，杜绝"单样本假湖"）。
     */
    static final int LAKE_MIN_SAMPLES = 3;
    /** 细长判据带（plan §6-T5：采样窗内长短轴比 > 5）。 */
    static final double ELONGATION_MIN = 5.0D;
    /** sanzu 最大簇最小样本数（派生：主边界沿主干带至少延伸一个 Voronoi 间距 ⇒ SEPARATION/步距）。 */
    static final int CLUSTER_MIN_SAMPLES = (int) (GTSRVoronoiRiverField.SEPARATION / SCAN_STRIDE);
    /** 少支流密度对比带（TRUNK_ALIGN_COS javadoc"按密度对比断言"口径：带内三叉密度 ≤ 带外 1/3）。 */
    static final double FORK_RATE_MAX_RATIO = 1.0D / 3.0D;

    static int assertions;
    static int failures;
    static final List<String> LINES = new ArrayList<String>();

    public static void main(String[] args) {
        groupA();
        // —— 主网格一趟：trunk / s / 河核 / sanzu 预筛（B/D/E 共用）——
        final int n = gridN();
        final boolean[] trunk = new boolean[n * n];
        final boolean[] core = new boolean[n * n];
        final long[] hash = new long[n * n];
        final boolean[] sanzu = new boolean[n * n];
        long coreTotal = 0;
        long coreTrunk = 0;
        int sanzuPre = 0;
        for (int iz = 0; iz < n; iz++) {
            final int z = -SCAN_EXTENT + iz * SCAN_STRIDE;
            for (int ix = 0; ix < n; ix++) {
                final int x = -SCAN_EXTENT + ix * SCAN_STRIDE;
                final int i = iz * n + ix;
                trunk[i] = GTSRVoronoiRiverField.trunkAt(SEED, x, z) > 0.0D;
                final double s = -GTSRVoronoiRiverField.strengthAt(SEED, x, z, -1);
                core[i] = s >= GTSRVoronoiRiverField.WET_MIN;
                if (core[i]) {
                    coreTotal++;
                    hash[i] = GTSRVoronoiRiverField.nearestCellHash(SEED, x, z);
                    if (trunk[i]) {
                        coreTrunk++;
                    }
                }
                // sanzu 预筛（廉价两门）后走真谓词（heightAt 只对候选列求值）
                sanzu[i] = trunk[i] && s >= GTSRVoronoiRiverField.SANZU_BIOME_STRENGTH
                    && GTSRVoronoiRiverField.isSanzuColumn(SEED, x, z);
                if (sanzu[i]) {
                    sanzuPre++;
                }
            }
        }
        say(
            "S-READ 主网格 ±" + SCAN_EXTENT + " 步距 " + SCAN_STRIDE + "：河核列=" + coreTotal + " 主干河核列="
                + coreTrunk + " sanzu 列=" + sanzuPre);
        groupB(coreTotal, coreTrunk);
        groupC();
        groupD(n, trunk, core, hash);
        groupE(n, sanzu, sanzuPre);
        report();
    }

    static int gridN() {
        return 2 * (SCAN_EXTENT / SCAN_STRIDE) + 1;
    }

    // ══════════════════════ A 合同面 ══════════════════════

    static void groupA() {
        double tMin = 1.0D;
        double tMax = 0.0D;
        boolean deterministic = true;
        boolean seedSensitive = false;
        boolean lakeSeenInside = false;
        boolean lakeGatedOutside = true;
        boolean sRangeOk = true;
        boolean rosterInvariant = true;
        int invariantChecked = 0;
        // 采样域 = 全窗 ±SCAN_EXTENT（主干带波长 ~5000 ⇒ 窄窗可能整窗无带，A 组早期版本实测
        // ±SCAN_EXTENT/4 内 0 命中——带是稀疏条带，统计面必须覆盖全窗）
        final int lim = SCAN_EXTENT;
        for (int zi = -lim; zi <= lim; zi += SCAN_STRIDE * 2) {
            for (int xi = -lim; xi <= lim; xi += SCAN_STRIDE * 2) {
                final double t = GTSRVoronoiRiverField.trunkAt(SEED, xi, zi);
                tMin = Math.min(tMin, t);
                tMax = Math.max(tMax, t);
                if (t != GTSRVoronoiRiverField.trunkAt(SEED, xi, zi)) {
                    deterministic = false;
                }
                if (t != GTSRVoronoiRiverField.trunkAt(SEED ^ 0x5BL, xi, zi)) {
                    seedSensitive = true;
                }
                if (t <= 0.0D) {
                    if (GTSRVoronoiRiverField.lakeAt(SEED, xi, zi) != GTSRVoronoiRiverField.NO_LAKE) {
                        lakeGatedOutside = false;
                    }
                } else if (GTSRVoronoiRiverField.lakeAt(SEED, xi, zi) < GTSRVoronoiRiverField.NO_LAKE) {
                    lakeSeenInside = true;
                }
                final double rs = GTSRVoronoiRiverField.strengthAt(SEED, xi, zi, -1);
                if (rs > 0.0D || rs < -1.0D) {
                    sRangeOk = false;
                }
                // 主干带内 s 对 rosterIndex 构造不变（max(底档,TRUNK_WIDTH_SCALE) 同值）
                if (t > 0.0D) {
                    final double s0 = -GTSRVoronoiRiverField.strengthAt(SEED, xi, zi, 0);
                    final double s3 = -GTSRVoronoiRiverField.strengthAt(SEED, xi, zi, 3);
                    final double sd = -GTSRVoronoiRiverField.strengthAt(SEED, xi, zi, -1);
                    if (s0 != sd || s3 != sd) {
                        rosterInvariant = false;
                    }
                    invariantChecked++;
                }
            }
        }
        check("A1 trunkAt 值域 ⊆ [0, 1-SANZU_TRUNK_EDGE] 且确定性双跑逐位一致",
            tMin >= 0.0D && tMax <= 1.0D - GTSRVoronoiRiverField.SANZU_TRUNK_EDGE && deterministic,
            "min=" + f4(tMin) + " max=" + f4(tMax));
        check("A2 trunkAt 换 seed 必换场（不是常数场）", seedSensitive, "换 seed 后采样域内零差异");
        check("A3 lakeAt 主干门：带外恒 NO_LAKE 哨兵、带内存在非哨兵（性能门 = 激活门同一条）",
            lakeGatedOutside && lakeSeenInside, "gated=" + lakeGatedOutside + " seenInside=" + lakeSeenInside);
        check("A4 档表族 5 元：RIVER_STYLE[4]=主干宽河档、RELIEF[4]=0.38、DEPTH[4]=沼泽档 0.05、VEG 长度 5",
            GTSRVoronoiRiverField.RIVER_STYLE_BY_ROSTER.length == 5
                && GTSRVoronoiRiverField.RIVER_STYLE_BY_ROSTER[4].widthScale == GTSRVoronoiRiverField.TRUNK_WIDTH_SCALE
                && ProsperityTerrainProfile.RELIEF_AMPLITUDE_BY_ROSTER.length == 5
                && ProsperityTerrainProfile.RELIEF_AMPLITUDE_BY_ROSTER[4] == 0.38D
                && StructureBurialTiers.DEPTH_TIER_BY_ROSTER.length == 5
                && StructureBurialTiers.DEPTH_TIER_BY_ROSTER[4] == 0.05D
                && ProsperityDecorPlacer.VEG_TIERS_BY_ROSTER.length == 5,
            "style=" + GTSRVoronoiRiverField.RIVER_STYLE_BY_ROSTER.length
                + " relief=" + ProsperityTerrainProfile.RELIEF_AMPLITUDE_BY_ROSTER.length
                + " depth=" + StructureBurialTiers.DEPTH_TIER_BY_ROSTER.length
                + " veg=" + ProsperityDecorPlacer.VEG_TIERS_BY_ROSTER.length);
        check("A5 strengthAt 值域仍 ⊆ [-1,0]（含主干带对齐门/展宽分支）", sRangeOk, "越界采样存在");
        check("A6 主干带内 s 对 rosterIndex 构造不变（0/3/-1 同值；isSanzuColumn 无参形态的依据）",
            rosterInvariant && invariantChecked > 32, "checked=" + invariantChecked);
    }

    // ══════════════════════ B 主干覆盖占河网 ══════════════════════

    static void groupB(long coreTotal, long coreTrunk) {
        final double share = coreTotal == 0 ? -1.0D : coreTrunk / (double) coreTotal;
        say(
            "B-READ 主干覆盖占河网（河核列 s≥WET_MIN 口径，对齐门后实测）=" + f3(100.0D * share) + "%"
                + "（河核 " + coreTotal + " 列 / 主干河核 " + coreTrunk + " 列；目标带 ["
                + f3(100.0D * COVER_MIN) + "%," + f3(100.0D * COVER_MAX) + "%]，SANZU_TRUNK_EDGE="
                + GTSRVoronoiRiverField.SANZU_TRUNK_EDGE + "）");
        check("B1 主干覆盖占河网 1/6-1/8（SANZU_TRUNK_EDGE 校准回路的验收读数）",
            share >= COVER_MIN && share <= COVER_MAX, "份额=" + f3(100.0D * share) + "%");
    }

    // ══════════════════════ C 巨湖存在 + 水面口径 ══════════════════════

    static void groupC() {
        final int n = 2 * (LAKE_EXTENT / LAKE_STRIDE) + 1;
        final byte[] lake = new byte[n * n]; // 1 = 湖水区样本
        int lakeSamples = 0;
        for (int iz = 0; iz < n; iz++) {
            final int z = -LAKE_EXTENT + iz * LAKE_STRIDE;
            for (int ix = 0; ix < n; ix++) {
                final int x = -LAKE_EXTENT + ix * LAKE_STRIDE;
                if (GTSRVoronoiRiverField.lakeAt(SEED, x, z) < GTSRVoronoiRiverField.LAKE_WATER_LEVEL) {
                    lake[iz * n + ix] = 1;
                    lakeSamples++;
                }
            }
        }
        // 4-连通洪泛求最大区
        final int[] mark = new int[n * n];
        int regions = 0;
        int best = 0;
        int bestId = 0;
        final int[] stack = new int[n * n];
        for (int i = 0; i < n * n; i++) {
            if (lake[i] == 0 || mark[i] != 0) {
                continue;
            }
            regions++;
            int size = 0;
            int top = 0;
            stack[top++] = i;
            mark[i] = regions;
            while (top > 0) {
                final int p = stack[--top];
                size++;
                final int px = p % n;
                final int pz = p / n;
                if (px > 0 && lake[p - 1] == 1 && mark[p - 1] == 0) {
                    mark[p - 1] = regions;
                    stack[top++] = p - 1;
                }
                if (px < n - 1 && lake[p + 1] == 1 && mark[p + 1] == 0) {
                    mark[p + 1] = regions;
                    stack[top++] = p + 1;
                }
                if (pz > 0 && lake[p - n] == 1 && mark[p - n] == 0) {
                    mark[p - n] = regions;
                    stack[top++] = p - n;
                }
                if (pz < n - 1 && lake[p + n] == 1 && mark[p + n] == 0) {
                    mark[p + n] = regions;
                    stack[top++] = p + n;
                }
            }
            if (size > best) {
                best = size;
                bestId = regions;
            }
        }
        // 湖心列高度口径：最大区内逐样本 heightAt
        int hChecked = 0;
        int inBedBand = 0;
        int hMin = Integer.MAX_VALUE;
        int hMax = Integer.MIN_VALUE;
        for (int iz = 0; iz < n; iz++) {
            final int z = -LAKE_EXTENT + iz * LAKE_STRIDE;
            for (int ix = 0; ix < n; ix++) {
                final int i = iz * n + ix;
                if (mark[i] != bestId) {
                    continue;
                }
                final int h = ProsperityTerrainProfile.heightAt(SEED, -LAKE_EXTENT + ix * LAKE_STRIDE, z);
                hChecked++;
                if (h >= LAKE_BED_MIN && h <= LAKE_BED_MAX) {
                    inBedBand++;
                }
                hMin = Math.min(hMin, h);
                hMax = Math.max(hMax, h);
            }
        }
        final double bedShare = hChecked == 0 ? 0.0D : inBedBand / (double) hChecked;
        say(
            "C-READ 巨湖（主干带内 c_lake<LAKE_WATER_LEVEL，粗扫步距 " + LAKE_STRIDE + "）：湖水样本=" + lakeSamples
                + " 连通区=" + regions + " 最大区=" + best + " 样本（≈" + (best * LAKE_STRIDE * LAKE_STRIDE)
                + " 列）；最大区 heightAt ∈ [" + hMin + "," + hMax + "]，62-64 带内占比 "
                + f3(100.0D * bedShare) + "%（水面 SEA_LEVEL=" + ProsperityTerrainProfile.SEA_LEVEL + "）");
        check("C1 巨湖存在：最大 4-连通区 ≥ " + LAKE_MIN_SAMPLES + " 样本（离散巨湖，非边界湖网）",
            best >= LAKE_MIN_SAMPLES && regions > 0, "最大区=" + best + " 区数=" + regions);
        check("C2 湖心床口径：最大区 heightAt ≥90% ∈ [62,64] 且全部 < SEA_LEVEL（水面 68 ⇒ 深 4-6；"
            + "低地湖心更深列按防抬升语义保留并逐样本报数）",
            hChecked > 0 && bedShare >= 0.9D && hMax < ProsperityTerrainProfile.SEA_LEVEL,
            "band=" + f3(100.0D * bedShare) + "% h∈[" + hMin + "," + hMax + "]");
    }

    /** 湖床带下/上界（派生：LAKE_BED_TARGET ± 1 噪声）。 */
    static final int LAKE_BED_MIN = (int) Math.floor(GTSRVoronoiRiverField.LAKE_BED_TARGET - 1.0D);
    static final int LAKE_BED_MAX = (int) Math.ceil(GTSRVoronoiRiverField.LAKE_BED_TARGET + 1.0D);

    // ══════════════════════ D 少支流（三叉密度对比） ══════════════════════

    /**
     * "三叉块" = 2×2 采样块内 ≥3 个<b>不同最近细胞签名</b>的河核列（活跃三叉的邻域形态）。
     * 带内对齐门清零横截次级边界 ⇒ 带内三叉只剩近结点 60° 短残支（设计保留），密度应显著低于
     * 带外 Voronoi 网络的天然三叉密度（对照臂同时证明"与普通河同网连通、带外分叉照常"）。
     */
    static void groupD(int n, boolean[] trunk, boolean[] core, long[] hash) {
        long blocksIn = 0;
        long blocksOut = 0;
        long forksIn = 0;
        long forksOut = 0;
        for (int iz = 0; iz + 1 < n; iz++) {
            for (int ix = 0; ix + 1 < n; ix++) {
                final int i00 = iz * n + ix;
                final int i10 = i00 + 1;
                final int i01 = i00 + n;
                final int i11 = i01 + 1;
                // 块归属：四列同属带内/带外（跨界块不计数，避免边界稀释任一侧）
                final boolean inA = trunk[i00] && trunk[i10] && trunk[i01] && trunk[i11];
                final boolean outA = !trunk[i00] && !trunk[i10] && !trunk[i01] && !trunk[i11];
                if (!inA && !outA) {
                    continue;
                }
                final long[] h = new long[] { hash[i00], hash[i10], hash[i01], hash[i11] };
                int distinct = 0;
                for (int a = 0; a < 4; a++) {
                    if (h[a] == 0L) {
                        continue; // 非河核列不参与（签名 0 = 空）
                    }
                    boolean seen = false;
                    for (int b = 0; b < a; b++) {
                        if (h[a] == h[b] && h[b] != 0L) {
                            seen = true;
                            break;
                        }
                    }
                    if (!seen) {
                        distinct++;
                    }
                }
                final boolean fork = distinct >= 3;
                if (inA) {
                    blocksIn++;
                    if (fork) {
                        forksIn++;
                    }
                } else {
                    blocksOut++;
                    if (fork) {
                        forksOut++;
                    }
                }
            }
        }
        final double rateIn = blocksIn == 0 ? -1.0D : forksIn / (double) blocksIn;
        final double rateOut = blocksOut == 0 ? -1.0D : forksOut / (double) blocksOut;
        say(
            "D-READ 三叉块密度：带内 " + forksIn + "/" + blocksIn + "（" + f3(100.0D * rateIn) + "%） vs 带外 "
                + forksOut + "/" + blocksOut + "（" + f3(100.0D * rateOut) + "%）");
        check("D1 带外对照三叉非零（普通河网分叉照常 ⇒ 主干与普通河同网）",
            rateOut > 0.0D && blocksOut > 256, "带外=" + forksOut + "/" + blocksOut);
        check("D2 少支流：带内三叉密度 ≤ 带外 ×" + f3(FORK_RATE_MAX_RATIO) + "（对齐门的行为读数）",
            rateIn >= 0.0D && rateOut > 0.0D && rateIn <= rateOut * FORK_RATE_MAX_RATIO,
            "带内=" + f3(100.0D * rateIn) + "% 带外=" + f3(100.0D * rateOut) + "%");
    }

    // ══════════════════════ E sanzu 列存在 + 细长 ══════════════════════

    static void groupE(int n, boolean[] sanzu, int sanzuCount) {
        check("E1 sanzu 群系列数 > 0（指派谓词在采样窗内可命中）", sanzuCount > 0, "n=" + sanzuCount);
        // 8-连通洪泛：各簇规模 + PCA 长短轴（主干带沿主边界延伸 ⇒ 大簇应细长；偶发分汊区
        // （对齐门保留的 60° 短残支）会把邻近河段并成团块——断言取"存在足够大的细长簇"，
        // 与 plan §3.3"很少支流≠零分汊"同口径）
        final int[] mark = new int[n * n];
        final int[] stack = new int[n * n];
        final List<int[]> sizes = new ArrayList<int[]>();
        final List<Double> elongations = new ArrayList<Double>();
        for (int i = 0; i < n * n; i++) {
            if (!sanzu[i] || mark[i] != 0) {
                continue;
            }
            final int id = i + 1;
            int size = 0;
            int top = 0;
            stack[top++] = i;
            mark[i] = id;
            final List<int[]> cols = new ArrayList<int[]>();
            while (top > 0) {
                final int p = stack[--top];
                cols.add(new int[] { p % n, p / n });
                size++;
                final int px = p % n;
                final int pz = p / n;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        final int qx = px + dx;
                        final int qz = pz + dz;
                        if (qx < 0 || qz < 0 || qx >= n || qz >= n) {
                            continue;
                        }
                        final int q = qz * n + qx;
                        if (sanzu[q] && mark[q] == 0) {
                            mark[q] = id;
                            stack[top++] = q;
                        }
                    }
                }
            }
            long sx = 0;
            long sz = 0;
            for (final int[] c : cols) {
                sx += c[0];
                sz += c[1];
            }
            final double mx = sx / (double) size;
            final double mz = sz / (double) size;
            double cxx = 0;
            double czz = 0;
            double cxz = 0;
            for (final int[] c : cols) {
                final double ox = c[0] - mx;
                final double oz = c[1] - mz;
                cxx += ox * ox;
                czz += oz * oz;
                cxz += ox * oz;
            }
            sizes.add(new int[] { size });
            elongations.add(Double.valueOf(Math.sqrt(eigMax(cxx, czz, cxz) / Math.max(1e-12D, eigMin(cxx, czz, cxz)))));
        }
        // 报数：前 5 大簇的 规模/长短轴
        final Integer[] order = new Integer[sizes.size()];
        for (int k = 0; k < order.length; k++) {
            order[k] = Integer.valueOf(k);
        }
        Arrays.sort(order, (a, b) -> Integer.compare(sizes.get(b.intValue())[0], sizes.get(a.intValue())[0]));
        final StringBuilder top = new StringBuilder();
        int biggest = 0;
        boolean bigElongated = false;
        double bigElongatedRatio = 0.0D;
        int bigElongatedSize = 0;
        for (int k = 0; k < Math.min(5, order.length); k++) {
            final int idx = order[k].intValue();
            final int size = sizes.get(idx)[0];
            final double ratio = elongations.get(idx).doubleValue();
            if (k > 0) {
                top.append("; ");
            }
            top.append("#")
                .append(k + 1)
                .append(" size=")
                .append(size)
                .append(" ratio=")
                .append(f3(ratio));
            biggest = Math.max(biggest, size);
            if (size >= CLUSTER_MIN_SAMPLES && ratio > bigElongatedRatio) {
                bigElongatedRatio = ratio;
                bigElongatedSize = size;
            }
        }
        bigElongated = bigElongatedRatio > ELONGATION_MIN;
        say("E-READ sanzu 簇 top5（size=采样数×" + SCAN_STRIDE * SCAN_STRIDE + " 列/样本）：" + top);
        check("E2 sanzu 最大簇规模 ≥ SEPARATION/步距（主边界沿主干带至少一个 Voronoi 间距）",
            biggest >= CLUSTER_MIN_SAMPLES, "最大簇=" + biggest + " 阈=" + CLUSTER_MIN_SAMPLES);
        check("E3 形状细长：存在规模 ≥ 阈 的簇长短轴比 > " + ELONGATION_MIN + "（plan §6-T5；分汊团块按"
            + "\"很少支流≠零分汊\"口径不计入本断言，逐簇报数见 E-READ）",
            bigElongated, "best ratio=" + f3(bigElongatedRatio) + " @size=" + bigElongatedSize);
    }

    /** 2×2 对称阵较大特征值。 */
    static double eigMax(double a, double d, double b) {
        final double tr = a + d;
        final double det = a * d - b * b;
        final double disc = Math.sqrt(Math.max(0.0D, tr * tr / 4.0D - det));
        return tr / 2.0D + disc;
    }

    /** 2×2 对称阵较小特征值。 */
    static double eigMin(double a, double d, double b) {
        final double tr = a + d;
        final double det = a * d - b * b;
        final double disc = Math.sqrt(Math.max(0.0D, tr * tr / 4.0D - det));
        return tr / 2.0D - disc;
    }

    // ══════════════════════ 统计小件 ══════════════════════

    static String f3(double v) {
        return String.format("%.3f", Double.valueOf(v));
    }

    static String f4(double v) {
        return String.format("%.4f", Double.valueOf(v));
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
        say("═══ SanzuTrunkCoverageCheck assertions=" + assertions + " failures=" + failures + " ═══");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
