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
 * ═══ 六组断言（全部实跑；阈值 = plan 验收指标，统计功效参数全部从生产常数派生——
 * v1.20.38 纪律 2：不写字面量窗口）═══
 * <ul>
 * <li><b>A 合同面</b>：trunkAt 值域/确定性/换 seed；lakeAt 主干门（带外恒 NO_LAKE、带内存在非
 * 哨兵）；档表族 5 元（RIVER_STYLE[4]=主干宽河档、RELIEF[4]=0.38、DEPTH[4]=0.05、VEG 长度 5）；
 * strengthAt 值域仍 ⊆ [-1,0]；主干带内 s 对 rosterIndex 不变（宽档 max(底,3.5) 的构造不变量）；</li>
 * <li><b>B 主干覆盖占河网 ∈ [11%,16.7%]</b>：河核列（s≥WET_MIN）口径（v1.20.40 P19 §A.2
 * 重钉：WIDTH 0.14→0.08 收窄后下界从 1/8 收口到 11%，上界 1/6 语义不变）；</li>
 * <li><b>C 巨湖存在 + 湖心渐深</b>（v1.20.40 P19 §D 重钉）：主干带内 c_lake&lt;LAKE_WATER_LEVEL
 * 的 4-连通区存在且规模超阈；最大区 h 全部 ≤ SEA_LEVEL + 湖心压力带 minH 锚在
 * SEA_LEVEL−LAKE_CENTER_DEPTH ±2 + 水缘带 maxH−湖心带 minH ≥ 5（中心渐深梯度）；C3 湖形
 * 不规则度 = 最大湖 8 向水径 CV &gt; 0.1（domain-warp 破圆，纯圆 = 0）；</li>
 * <li><b>D 少支流</b>：主干带内"三叉块"（2×2 采样块内 ≥3 个不同最近细胞签名的河核列）密度
 * 显著低于带外对照（对齐门清零横截次级边界的行为读数；"很少支流"≠零分叉，带内近结点
 * 60° 短残支按设计保留，故按密度对比断言——TRUNK_ALIGN_COS javadoc 口径）；</li>
 * <li><b>E sanzu 列存在 + 细长</b>：isSanzuColumn 列数 &gt; 0；最大 4-连通簇的长短轴比
 * （协方差特征值 √(λmax/λmin)）&gt; 5（plan §6-T5 细长断言；形状 = 河道核 × 主干带交集）；</li>
 * <li><b>F 沼泽微池</b>（v1.20.40 P19 §E 新增）：swampLakeAt 第二激活档（rosterIndex==3）在窗内
 * 的 4-连通水域簇数 ≥ 粗格数 1/4、最大簇 π 反解半径 ∈ [0.7,1.4]×INTERVAL×W/(1+W)（U34 探针
 * P4 升格）。</li>
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
    /** 主干覆盖判据带（v1.20.40 P19 §A.2 重钉：WIDTH 0.14→0.08 收窄挤掉份额 −0.37pp——下界从 1/8
     * 收口到 11%（U2/U34/U8 prered 三轮实测 12.132% 稳定在带内），上界 1/6 语义不变）。 */
    static final double COVER_MIN = 0.11D;
    static final double COVER_MAX = 1.0D / 6.0D;
    /** 巨湖扫描窗半径（格）：3×TRUNK_SCALE——湖间隔 LAKE_INTERVAL=1200 下窗内 ≥ 数十个湖细胞。 */
    static final int LAKE_EXTENT = (int) (GTSRVoronoiRiverField.TRUNK_SCALE * 3);
    /** 巨湖粗扫步距（格）：LAKE_INTERVAL/32 ≈ 37（湖理论半径 ≈ 0.11×间隔/2 ≈ 66 格 ⇒ 直径 ≈ 3.6 步）。 */
    static final int LAKE_STRIDE = (int) (GTSRVoronoiRiverField.LAKE_INTERVAL / 32);
    /**
     * 巨湖"水径"目标半径（格）：§5 S5-1 与 §15.2 钉的<b>实测水径 r = 110</b>（水面直径 220 ≥
     * 用户"更大"口径 200，留 10% 余量；§6.1 R3 的派生即用此 r）。<b>注意</b>：S5 尚未落地，
     * {@code LAKE_WATER_LEVEL} 仍是 0.13 ⇒ 今天 C-READ 的均径 ≈ 87.75。本常量是<b>判据侧的目标
     * 锚</b>，不是对当前几何的实测声明；提前钉 14 不产生假红（当前最大区实测 68 样本 ≫ 14），
     * 却能在 S5 缩湖/碎片化时立刻变红——阈从"防单样本假湖"升级成"湖必须成规模"。
     */
    static final int LAKE_R_TARGET = 110;
    /**
     * 巨湖最小连通样本数（v1.20.41 P20 §6.1 R3 重派生：<b>3 → 14</b>）。
     * <p>
     * 派生式 = {@code round(π·r² / LAKE_STRIDE² × 0.5)}，r = {@link #LAKE_R_TARGET} = 110、
     * {@code LAKE_STRIDE = LAKE_INTERVAL/32 = 37} ⇒ {@code round(3.1416×12100/1369×0.5) =
     * round(13.884) = 14}（0.5 = 采样碎片折半：粗扫步距 37 的网格命中一个直径 220 的圆盘，
     * 期望样本数 π·r²/stride² ≈ 27.8，取一半作下界 ⇒ 小于它即"不是一整块湖"）。
     * <p>
     * <b>旧口径保留原文</b>（v1.20.39 T5：湖理论直径 (2×0.11×LAKE_INTERVAL/2)/LAKE_STRIDE ≈ 3.6
     * ⇒ 取 3，只防"单样本假湖"）——覆盖理由：§15.2 巨湖终裁把湖规模抬到 r=110 后，3 样本
     * （≈3×37² ≈ 4107 列 ≈ 半径 36）连"湖滨碎片"都能过，判据失去区分力；C1 的语义从"存在离散
     * 巨湖"升级为"湖达到终裁规模"。C2/C3/B1/E 组的带与派生式<b>一律未动</b>（任务包禁改面）；
     * C3 的 {@code minSize = max(LAKE_MIN_SAMPLES, best/4)} 当前由 best/4 = 68/4 = 17 主导 ⇒
     * 阈 3→14 不改 C3 读数（已在片回执以实测对表）。
     */
    static final int LAKE_MIN_SAMPLES =
        (int) Math.round(Math.PI * LAKE_R_TARGET * LAKE_R_TARGET / (LAKE_STRIDE * LAKE_STRIDE) * 0.5D);
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
        groupFSwampPools();
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
        check("B1 主干覆盖占河网 ∈ [11%,16.7%]（v1.20.40 P19 §A.2 重钉：WIDTH 收窄后下界从 1/8 收口到 11%，上界 1/6 语义不变；归因 U2/U34/U8 prered 实测 12.132%）",
            share >= COVER_MIN && share <= COVER_MAX, "份额=" + f3(100.0D * share) + "%");
    }

    // ══════════════════════ C 巨湖存在 + 水面口径 ══════════════════════

    static void groupC() {
        final int n = 2 * (LAKE_EXTENT / LAKE_STRIDE) + 1;
        final double[] pressure = new double[n * n]; // >0 = 湖水区样本的湖压（LAKE_WATER_LEVEL − c_lake）
        int lakeSamples = 0;
        for (int iz = 0; iz < n; iz++) {
            final int z = -LAKE_EXTENT + iz * LAKE_STRIDE;
            for (int ix = 0; ix < n; ix++) {
                final int x = -LAKE_EXTENT + ix * LAKE_STRIDE;
                final double p = GTSRVoronoiRiverField.LAKE_WATER_LEVEL - GTSRVoronoiRiverField.lakeAt(SEED, x, z);
                if (p > 0.0D) {
                    pressure[iz * n + ix] = p;
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
            if (pressure[i] <= 0.0D || mark[i] != 0) {
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
                if (px > 0 && pressure[p - 1] > 0.0D && mark[p - 1] == 0) {
                    mark[p - 1] = regions;
                    stack[top++] = p - 1;
                }
                if (px < n - 1 && pressure[p + 1] > 0.0D && mark[p + 1] == 0) {
                    mark[p + 1] = regions;
                    stack[top++] = p + 1;
                }
                if (pz > 0 && pressure[p - n] > 0.0D && mark[p - n] == 0) {
                    mark[p - n] = regions;
                    stack[top++] = p - n;
                }
                if (pz < n - 1 && pressure[p + n] > 0.0D && mark[p + n] == 0) {
                    mark[p + n] = regions;
                    stack[top++] = p + n;
                }
            }
            if (size > best) {
                best = size;
                bestId = regions;
            }
        }
        // 湖心渐深口径（v1.20.40 P19 §D 重钉）：最大区内逐样本 heightAt 分压力带——
        // 湖心带（湖压 ≥ 2/3 水位线）minH 锚在 SEA_LEVEL−LAKE_CENTER_DEPTH ±2；
        // 水缘带（湖压 < 1/3 水位线）maxH 与湖心带 minH 的差 = 中心渐深梯度。
        int hChecked = 0;
        int hMin = Integer.MAX_VALUE;
        int hMax = Integer.MIN_VALUE;
        int centerMinH = Integer.MAX_VALUE;
        int rimMaxH = Integer.MIN_VALUE;
        int centerSamples = 0;
        int rimSamples = 0;
        // §30 主代理裁决（P20 S5 家族）：需求 8 的<b>中心岛是干地</b>（岛面 = SEA + LIFT = 72），
        // 所以"最大区 h 全部 ≤ SEA_LEVEL"这条 v1.20.40 时期的子句<b>按设计失效</b>了——被它判红的正是
        // 岛本身（实测 h ∈ [40,72]）。改法不是放宽阈值，而是<b>把"高出水面的列"交给生产自己的出口解释</b>：
        // 每一列都必须被 {@code lakeIslandTopAt}（同一个真值，非判据侧复刻）盖住 ⇒ 高出水面 = 岛；
        // 只要有一列高出水面却不是岛（湖床被抬、或杂类写入），本条照旧转红。
        int aboveSea = 0;
        int aboveSeaByIsland = 0;
        final StringBuilder unexplained = new StringBuilder(320);
        long centroidX = 0;
        long centroidZ = 0;
        for (int iz = 0; iz < n; iz++) {
            final int z = -LAKE_EXTENT + iz * LAKE_STRIDE;
            for (int ix = 0; ix < n; ix++) {
                final int i = iz * n + ix;
                if (mark[i] != bestId) {
                    continue;
                }
                centroidX += -LAKE_EXTENT + ix * LAKE_STRIDE;
                centroidZ += z;
                final int h = ProsperityTerrainProfile.heightAt(SEED, -LAKE_EXTENT + ix * LAKE_STRIDE, z);
                hChecked++;
                hMin = Math.min(hMin, h);
                hMax = Math.max(hMax, h);
                if (h > ProsperityTerrainProfile.SEA_LEVEL) {
                    aboveSea++;
                    // 高出水面的列只允许是岛；判据不另立"什么叫岛"的第二真值——问生产出口本人。
                    // ⚠ 形参口径：本文件的 {@code pressure[]} 存的是 <b>{@code WATER − lakeAt}</b>（反向量，
                    // 越大越靠水缘），而 {@code lakeIslandTopAt} 的第四形参要的是<b>原始 {@code lakeAt}</b>。
                    // 主代理第一版直接喂了 pressure[i] ⇒ 岛域列被判成 NaN（假红一条）。⇒ 这里重取真值，
                    // 不做减法（少一处口径耦合），且只在这种罕见列上付这一次求值。
                    final int sx = -LAKE_EXTENT + ix * LAKE_STRIDE;
                    final double lakeRaw = GTSRVoronoiRiverField.lakeAt(SEED, sx, z);
                    final double isleTop = GTSRVoronoiRiverField.lakeIslandTopAt(SEED, sx, z, lakeRaw);
                    if (!Double.isNaN(isleTop) && h <= (int) Math.ceil(isleTop)) {
                        aboveSeaByIsland++;
                    } else if (unexplained.length() < 300) {
                        // 只报诊断（§30）：未解释列的逐列读数 ⇒ 区分"自然高地压在湖足迹里"与"岛域门
                        // 与生产支路门不同源"两类，前者是既有形态、后者是真缺陷。
                        unexplained.append(" (").append(sx).append(',').append(z)
                            .append(") h=").append(h).append(" lakeAt=").append(f3(lakeRaw))
                            .append(" isle=").append(Double.isNaN(isleTop) ? "NaN" : f3(isleTop))
                            .append(" bed=").append(f3(GTSRVoronoiRiverField.lakeBedAt(SEED, sx, z))).append(';');
                    }
                }
                if (pressure[i] >= GTSRVoronoiRiverField.LAKE_WATER_LEVEL * 2.0D / 3.0D) {
                    centerMinH = Math.min(centerMinH, h);
                    centerSamples++;
                }
                if (pressure[i] < GTSRVoronoiRiverField.LAKE_WATER_LEVEL / 3.0D) {
                    rimMaxH = Math.max(rimMaxH, h);
                    rimSamples++;
                }
            }
        }
        final double depthGrad = hChecked == 0 || centerMinH == Integer.MAX_VALUE || rimMaxH == Integer.MIN_VALUE
            ? -1.0D
            : rimMaxH - (double) centerMinH;
        say(
            "C-READ 巨湖（主干带内 c_lake<LAKE_WATER_LEVEL，粗扫步距 " + LAKE_STRIDE + "）：湖水样本=" + lakeSamples
                + " 连通区=" + regions + " 最大区=" + best + " 样本（≈" + (best * LAKE_STRIDE * LAKE_STRIDE)
                + " 列）；最大区 heightAt ∈ [" + hMin + "," + hMax + "]，湖心带(" + centerSamples + "样本) minH="
                + centerMinH + " 水缘带(" + rimSamples + "样本) maxH=" + rimMaxH + " 渐深差=" + f3(depthGrad)
                + "（水面 SEA_LEVEL=" + ProsperityTerrainProfile.SEA_LEVEL + " 湖心锚="
                + (ProsperityTerrainProfile.SEA_LEVEL - (int) GTSRVoronoiRiverField.LAKE_CENTER_DEPTH) + "）");
        check("C1 巨湖存在：最大 4-连通区 ≥ " + LAKE_MIN_SAMPLES + " 样本（离散巨湖，非边界湖网）",
            best >= LAKE_MIN_SAMPLES && regions > 0, "最大区=" + best + " 区数=" + regions);
        // v1.20.40 P19 §D 重钉（归因 U34 prered：渐深后旧"62-64 带 ≥90%"口径失去意义——
        // 湖滨锚 67/湖心锚 58，最大区 h ∈ [57,67]；LAKE_BED_TARGET 不再参与生成，随退役）。
        check("C2 湖心渐深：最大区内<b>高出水面的列必须全部由生产岛面出口解释</b>（= 它们是中心岛，"
            + "不是湖床被抬）且湖心带 minH ∈ [SEA_LEVEL−"
            + "LAKE_CENTER_DEPTH−2, SEA_LEVEL−LAKE_CENTER_DEPTH+2] 且 水缘带 maxH − 湖心带 minH ≥ 5"
            + "（中心渐深梯度；U34 探针 P2 实测 diff=11）。【<b>子句一原文保留</b>（v1.20.40 P19 §D 时期）："
            + "「最大区 h 全部 ≤ SEA_LEVEL（湖床不高于水面）」——需求 8 的中心岛落地后该字面<b>按设计失效</b>"
            + "（实测最大区 h ∈ [40,72]，72 = 岛面 SEA+LIFT），故改为「岛解释制」：<b>阈值一处未放宽</b>，"
            + "湖床若真被抬出水面仍会红。推导见上方声明段与 plan §30】",
            hChecked > 0 && aboveSea == aboveSeaByIsland
                && centerMinH >= ProsperityTerrainProfile.SEA_LEVEL - (int) GTSRVoronoiRiverField.LAKE_CENTER_DEPTH - 2
                && centerMinH <= ProsperityTerrainProfile.SEA_LEVEL - (int) GTSRVoronoiRiverField.LAKE_CENTER_DEPTH + 2
                && depthGrad >= 5.0D,
            "h∈[" + hMin + "," + hMax + "] 高出水面 " + aboveSea + " 样本（其中岛面出口解释 "
                + aboveSeaByIsland + "）湖心minH=" + centerMinH + " 水缘maxH=" + rimMaxH + " 渐深差="
                + f3(depthGrad) + " 未解释列=" + unexplained);
        groupCLakeShape();
    }

    // ══════════════════════ C3 湖形不规则度（v1.20.40 P19 §D 新增） ══════════════════════

    /**
     * 湖形不规则度（U34 探针 P1 升格）：湖心 domain-warp（LAKE_WARP 幅度 70）后，主要湖
     * （规模 ≥ 最大区 1/4 的连通区，排除碎裂采样片）各自取质心走 8 向细步距水径，变异系数
     * CV 的<b>最大值</b> &gt; 0.1——纯圆 = 0；细步距（粗距 1/4）走查的量化地板 ≈ 步长/均径
     * ≈ 0.01-0.05，0.1 = 抓住"肉眼可辨的不规则"而量化噪声不可达（warp 是全域均匀场 ⇒ 任一
     * 主要湖显著不规则即证 warp 生效；U34 探针湖 CV=0.196）。单取最大湖会漏掉"大而近圆"的
     * 个湖（U9 全量首轮实测最大湖 CV=0.070 而其它主要湖显著更不规则）。
     */
    static void groupCLakeShape() {
        final int n = 2 * (LAKE_EXTENT / LAKE_STRIDE) + 1;
        final int[] mark = new int[n * n];
        int regions = 0;
        int best = 0;
        final int[] stack = new int[n * n];
        final java.util.List<long[]> regSum = new ArrayList<long[]>(); // {sx, sz, size} per region id-1
        for (int i = 0; i < n * n; i++) {
            final int z = -LAKE_EXTENT + (i / n) * LAKE_STRIDE;
            final int x = -LAKE_EXTENT + (i % n) * LAKE_STRIDE;
            if (GTSRVoronoiRiverField.lakeAt(SEED, x, z) >= GTSRVoronoiRiverField.LAKE_WATER_LEVEL
                || mark[i] != 0) {
                continue;
            }
            regions++;
            int size = 0;
            int top = 0;
            stack[top++] = i;
            mark[i] = regions;
            long sx = 0;
            long sz = 0;
            regSum.add(new long[] { 0L, 0L, 0L });
            while (top > 0) {
                final int p = stack[--top];
                size++;
                final int px = p % n;
                final int pz = p / n;
                sx += -LAKE_EXTENT + px * LAKE_STRIDE;
                sz += -LAKE_EXTENT + pz * LAKE_STRIDE;
                final int[] nb = { px > 0 ? p - 1 : -1, px < n - 1 ? p + 1 : -1, pz > 0 ? p - n : -1,
                    pz < n - 1 ? p + n : -1 };
                for (final int q : nb) {
                    if (q < 0 || mark[q] != 0) {
                        continue;
                    }
                    final int qx = -LAKE_EXTENT + (q % n) * LAKE_STRIDE;
                    final int qz = -LAKE_EXTENT + (q / n) * LAKE_STRIDE;
                    if (GTSRVoronoiRiverField.lakeAt(SEED, qx, qz) < GTSRVoronoiRiverField.LAKE_WATER_LEVEL) {
                        mark[q] = regions;
                        stack[top++] = q;
                    }
                }
            }
            regSum.set(regions - 1, new long[] { sx, sz, size });
            best = Math.max(best, size);
        }
        final int RAY_STRIDE = Math.max(1, LAKE_STRIDE / 4);
        final int RAY_LIMIT = 2 * LAKE_EXTENT;
        final int minSize = Math.max(LAKE_MIN_SAMPLES, best / 4);
        double bestCv = 0.0D;
        String bestDesc = "-";
        int qualified = 0;
        for (int id = 1; id <= regions; id++) {
            final long[] s = regSum.get(id - 1);
            final int size = (int) s[2];
            if (size < minSize) {
                continue;
            }
            qualified++;
            final int cx = (int) (s[0] / size);
            final int cz = (int) (s[1] / size);
            final double[] radii = new double[8];
            int rays = 0;
            for (int d = 0; d < 8; d++) {
                final double ang = d * Math.PI / 4.0D;
                final double dx = Math.cos(ang);
                final double dz = Math.sin(ang);
                int r = RAY_STRIDE;
                while (r <= RAY_LIMIT
                    && GTSRVoronoiRiverField.lakeAt(SEED, cx + (int) Math.round(dx * r),
                        cz + (int) Math.round(dz * r)) < GTSRVoronoiRiverField.LAKE_WATER_LEVEL) {
                    r += RAY_STRIDE;
                }
                if (r <= RAY_LIMIT) {
                    radii[rays++] = r;
                }
            }
            if (rays < 6) {
                continue;
            }
            double mean = 0.0D;
            for (int i = 0; i < rays; i++) {
                mean += radii[i];
            }
            mean /= rays;
            double var = 0.0D;
            for (int i = 0; i < rays; i++) {
                var += (radii[i] - mean) * (radii[i] - mean);
            }
            var /= rays;
            final double cv = mean <= 0.0D ? 0.0D : Math.sqrt(var) / mean;
            if (cv > bestCv) {
                bestCv = cv;
                bestDesc = "质心(" + cx + "," + cz + ") 均径=" + f3(mean) + " size=" + size;
            }
        }
        say("C-READ 湖形不规则度：主要湖（≥最大区 1/4）=" + qualified + " 个，最大 CV=" + f3(bestCv) + " @ "
            + bestDesc + "（warp 幅度 " + GTSRVoronoiRiverField.LAKE_WARP + "；U34 探针 CV=0.196）");
        check("C3 湖形不规则度：主要湖 8 向水径 CV 最大值 > 0.1（P19 §D domain-warp 破圆；纯圆 = 0，"
            + "细步距量化地板 ≈ 0.01-0.05）",
            qualified > 0 && bestCv > 0.1D, "qualified=" + qualified + " bestCV=" + f3(bestCv));
    }

    // ══════════════════════ F 沼泽微池（v1.20.40 P19 §E 新增） ══════════════════════

    /**
     * 沼泽微池场存在性（U34 探针 P4 升格）：swampLakeAt 第二激活档（rosterIndex==3）在窗内形成
     * 量级正确的水域簇群。窗 ±5×SWAMP_POOL_INTERVAL（粗格数 (2×5)²=100，U34 实测 101 簇 ≈
     * 1 簇/粗格）；簇数下界取粗格数的 1/4（采样离散与激活阈值余量）；最大簇换算半径（π 反解）
     * ∈ [0.7,1.4]×INTERVAL×W/(1+W)（SWAMP_POOL_WATER_LEVEL=0.055 的水径推导式 ⇒ [8,16]，
     * U34 实测最大簇半径 ≈12）。
     */
    static void groupFSwampPools() {
        final int extent = (int) (GTSRVoronoiRiverField.SWAMP_POOL_INTERVAL * 5);
        final int stride = 4;
        final int n = 2 * (extent / stride) + 1;
        final boolean[] pool = new boolean[n * n];
        int samples = 0;
        for (int iz = 0; iz < n; iz++) {
            final int z = -extent + iz * stride;
            for (int ix = 0; ix < n; ix++) {
                final int x = -extent + ix * stride;
                if (GTSRVoronoiRiverField.swampLakeAt(SEED, x, z, 3) < GTSRVoronoiRiverField.SWAMP_POOL_WATER_LEVEL) {
                    pool[iz * n + ix] = true;
                    samples++;
                }
            }
        }
        final int[] mark = new int[n * n];
        final int[] stack = new int[n * n];
        int regions = 0;
        int best = 0;
        for (int i = 0; i < n * n; i++) {
            if (!pool[i] || mark[i] != 0) {
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
                if (px > 0 && pool[p - 1] && mark[p - 1] == 0) {
                    mark[p - 1] = regions;
                    stack[top++] = p - 1;
                }
                if (px < n - 1 && pool[p + 1] && mark[p + 1] == 0) {
                    mark[p + 1] = regions;
                    stack[top++] = p + 1;
                }
                if (pz > 0 && pool[p - n] && mark[p - n] == 0) {
                    mark[p - n] = regions;
                    stack[top++] = p - n;
                }
                if (pz < n - 1 && pool[p + n] && mark[p + n] == 0) {
                    mark[p + n] = regions;
                    stack[top++] = p + n;
                }
            }
            best = Math.max(best, size);
        }
        final double w = GTSRVoronoiRiverField.SWAMP_POOL_WATER_LEVEL;
        final double radiusMin = 0.7D * GTSRVoronoiRiverField.SWAMP_POOL_INTERVAL * w / (1.0D + w);
        final double radiusMax = 1.4D * GTSRVoronoiRiverField.SWAMP_POOL_INTERVAL * w / (1.0D + w);
        final double bestRadius = best == 0 ? 0.0D : Math.sqrt(best * (double) stride * stride / Math.PI);
        final int cells = (2 * extent / (int) GTSRVoronoiRiverField.SWAMP_POOL_INTERVAL)
            * (2 * extent / (int) GTSRVoronoiRiverField.SWAMP_POOL_INTERVAL);
        say("F-READ 沼泽微池：窗 ±" + extent + " 步距 " + stride + " 水域样本=" + samples + " 簇=" + regions
            + "（粗格 " + cells + "）最大簇=" + best + " 样本（换算半径 " + f3(bestRadius) + "，带 ["
            + f3(radiusMin) + "," + f3(radiusMax) + "]；U34 探针 101 簇 / 最大簇半径 ≈12）");
        check("F1 沼泽微池场存在：4-连通水域簇数 ≥ 粗格数的 1/4（rosterIndex==3 第二激活档在窗内成量级）",
            regions >= cells / 4, "簇=" + regions + " 阈=" + (cells / 4));
        check("F2 微池水径带：最大簇 π 反解半径 ∈ [0.7,1.4]×INTERVAL×W/(1+W)（W=SWAMP_POOL_WATER_LEVEL"
            + " 的水径推导式）",
            bestRadius >= radiusMin && bestRadius <= radiusMax, "最大簇=" + best + " 样本 半径=" + f3(bestRadius));
    }

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
