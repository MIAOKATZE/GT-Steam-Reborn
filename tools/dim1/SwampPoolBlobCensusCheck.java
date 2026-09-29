import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.TerrainVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * <b>v1.20.53 P31 批0 新增（record-only）；v1.20.54 P31 批II 起转硬门并挂载 surface_checks.sh [3u9]</b>。
 * 使命：P31 潭规模减半轮（RET-P §6「仓内无任何潭 blob 面积实测记录」的补证项）——
 * 逐列按<b>潭来源场</b>分类后做同源 4-连通 flood-fill，输出每源最大/中位连通域面积（区块）与总数
 * （批0 pre 基线 plan/tmp/p31-census-pre.md；批I-B 调档轨迹 plan/tmp/p31-ib-report.md §2），
 * 并对分源最大连通域执行<b>分源带硬门</b>（plan/p31-plan.md §5-2）。
 * <p>
 * ═══ 采样口径（= P17TerrainReliefCheck VARIANT 组同口径）═══
 * 16 seed（worldSeed = 0..15，非挑选）× 1024² 窗（原点 (0,0)）× 步距 4 ⇒ 每 seed 256×256 列、
 * 全探针 1,048,576 列；roster≠3 列不入任何源（三档门/微池场都是 roster 3 专属）。<b>纯函数求值、
 * 零世界读取</b>（仅离线哑元账本装配身份面，P29CDigest/P30GrottoProbe 同式）。
 * <p>
 * ═══ 分源分类（单一真值优先，私有门面按判据侧只读镜像先例）═══
 * <ul>
 * <li><b>STEEP 陡深潭</b>（λ71 场 blob，TV:602 块语义）：{@code swampTierAt==DEEP ∧ tyG==0}
 * ——分型域（TV:628-633 镜像：λ157 负瓣 {@code s01((−n157−0.45)/0.20)}）外，深门纯走 λ71；</li>
 * <li><b>GENTLE 缓坡深潭</b>（λ353，TV:640-651）：{@code swampTierAt==DEEP ∧ tyG>0}——域内
 * {@code g_eff=(1−tyG)·g陡+tyG·g缓}（TV:1399-1403），域内 DEEP 列按域归属记缓坡源（C1-C3 的
 * 联合作用面）；辅助源 <b>GENTLE_CORE</b> = GENTLE ∧ 缓床 blob 满门（λ353 门 {@code ≥0.5}，
 * TV:641/:647 镜像）——剔除「域内但实际由 λ71 撑起」的列，检验最大 blob 是否真 λ353 驱动；</li>
 * <li><b>MARSH 水沼地</b>（λ61 场 blob，TV:664-674 现值 λ61）：{@code swampTierAt==MARSH}
 * （三档优先序 DEEP&gt;MARSH&gt;POOL 的生产分流，TV:1418-1421，经 swampTierAt 单一出口消费）；</li>
 * <li><b>RVF 微池</b>（{@link GTSRVoronoiRiverField#swampPoolWaterAt}，RVF:2530-2620）：
 * {@code swampLakeAt < 0.055}；按水网域门（RVF:2508-2510 公开常量）分记
 * <b>MICRO_IN</b>（λ151 门值&gt;0，域内水径 14-26 格连片）与 <b>MICRO_OUT</b>（域外，r 8-16 格），
 * 另派生 <b>MICRO_ALL</b> = IN∪OUT 合并填充（连片口径上限读数）。</li>
 * </ul>
 * 三档源（STEEP/GENTLE/MARSH）与微池源是两套独立水体系统，同列可多源并存（各自 flood-fill）。
 * <p>
 * ═══ 面积口径与已知局限 ═══
 * 连通域面积（区块）= 域内采样格数 × 4²/256 = <b>格数/16</b>（步距 4 ⇒ 每格代表 4×4 方块）。
 * <b>步距 4 混叠</b>（plan §9-7 申报）：&lt;4 格窄连廊会断连、跨格邻接可能并域 ⇒ 读数贴
 * 12 区块决策阈时须加密步距 2 复测一次再裁决。
 * <p>
 * ═══ sanity 门 ═══
 * 五主源（STEEP/GENTLE/MARSH/MICRO_IN/MICRO_OUT）各须 ≥1 连通域且最大域 ≥1 区块——违者
 * EXIT=1（探针失明或生产面结构性变化，先修探针再谈读数）。GENTLE_CORE/MICRO_ALL 为派生
 * 辅助源不做 sanity。
 * <p>
 * ═══ 硬门带（v1.20.54 P31 批II；带 = 主代理终裁 plan §2.2-1/2/5，<b>非原计划的 ≤12 平带</b>）═══
 * <ul>
 * <li><b>STEEP ≤ 16.5</b>：λDEEP(S_SWAMP_DEEP_BED) 71→39 后 52.81→16.31（已缩 69% 超用户「减半」
 * 意图；达 12 需 λ≈33.4 梯外大步、DEEP 份额 2.399 贴 2.0 预警线 ⇒ 终裁停梯带重估）；</li>
 * <li><b>GENTLE ≤ 12</b>：深潭源维持原 ≤12 硬门不放松（λGENTLE 353→250 后 17.75→2.19 ✓）；</li>
 * <li><b>MARSH ≤ 56</b>：半淹景观带豁免深潭硬门——MARSH 深仅 0-1 格属沼泽湿地景观非用户抱怨的
 * 「水潭」，λMARSH 61→39 后 89.25→50.56（已缩 43%；继续压有 M 带沼行越带 + ORDER 链叠加扰动
 * 风险 ⇒ 终裁停梯，实机目检不满意可二期再压）；</li>
 * <li><b>MICRO_ALL == 6.125（同值）</b>：E 分支未触发 ⇒ RVF 微池零改动 ⇒ 微池连片读数必须与
 * 批0 pre <b>逐位同值</b>（6.125 = 98 采样格，IEEE 精确可比较）——任何漂移 = 误触
 * swampLakeAt/水网域门路径（E1/E2 之类的改动），防意外扩疆。</li>
 * </ul>
 * 负对照自证（RED→GREEN 纪律，同 [3u8] 缺源即红窗先例）：{@code --negctl6} 档把四门临时钉到
 * ≤6 区块 ⇒ 现读数（STEEP 16.31/MARSH 50.56/MICRO_ALL 6.125）必红（GENTLE 2.19≤6 仍绿属预期，
 * 自证对象 = 门机制可红非逐源全红）⇒ 证明门非永绿假门；<b>该档仅供手工自证</b>（输出留
 * temp/p31-ii/），挂载段只跑正式带。
 */
public class SwampPoolBlobCensusCheck {

    /** 名册序哑元 id 表（P17TerrainReliefCheck/P29CDigest 同款离线装配）。 */
    private static final int[] IDS = { 180, 181, 182, 183 };

    // —— 分型域门面（= 生产常量 TerrainVariants 私有 S_SWAMP_POOLTYPE 族只读镜像；与
    //    temp/p30-pre/probe/ProbeDigestUnion.java 镜像腿逐字同源同值）——
    private static final long S_SWAMP_POOLTYPE = 0x6811C3D1L;
    private static final double SWAMP_POOLTYPE_SCALE = 157.0D;
    private static final double POOLTYPE_GATE_LO = 0.45D;
    private static final double POOLTYPE_GATE_SPAN = 0.20D;
    // —— 缓床 blob 门面（= 生产常量 TerrainVariants 私有 S_SWAMP_DEEP_GENTLE 族只读镜像，
    //    TV:640/:641/:647 + 门 LO 共享 SWAMP_DEEP_GATE_LO=0.68，TV:1405-1408 求值镜像；
    //    v1.20.54 P31 批I-B C2 起波长镜像 353→250 随生产同值）——
    private static final long S_SWAMP_DEEP_GENTLE = 0x6811C407L;
    private static final double SWAMP_DEEP_GENTLE_BED = 250.0D;
    private static final double DEEP_GATE_LO_SHARED = 0.68D;
    private static final double GENTLE_GATE_SPAN = 0.30D;

    /** 采样口径：16 seed（worldSeed = 0..15）× 1024² 原点 (0,0) × 步距 4（P17 VARIANT 组同口径）。 */
    private static final int SEEDS = 16;
    private static final int SIDE = 1024;
    private static final int STRIDE = 4;
    /** 每格代表 4×4 方块 ⇒ 区块换算 = 格数 × 16/256 = 格数/16。 */
    private static final double CELLS_PER_CHUNK = 256.0D / (STRIDE * STRIDE);

    // —— 硬门带（v1.20.54 P31 批II；= 主代理终裁 plan/p31-plan.md §2.2-1/2/5，非 ≤12 平带：
    //    终态实测 STEEP 16.3125 / GENTLE 2.1875 / MARSH 50.5625 / MICRO_ALL 6.125，见类注释）——
    /** STEEP 陡深潭带（λ39 停梯后带重估：52.81→16.31 已缩 69%，达 12 需梯外大步）。 */
    private static final double MAX_STEEP_CHUNKS = 16.5D;
    /** GENTLE 缓坡深潭带（深潭源维持原 ≤12 硬门不放松；λ353→250 后 2.19 ✓）。 */
    private static final double MAX_GENTLE_CHUNKS = 12.0D;
    /** MARSH 水沼地带（半淹景观带豁免深潭硬门：深仅 0-1 格，λ61→39 后 89.25→50.56）。 */
    private static final double MAX_MARSH_CHUNKS = 56.0D;
    /** MICRO_ALL 同值锚（= 批0 pre 读数 98 格/16 逐位同值；E 分支未触 ⇒ 微池零漂移）。 */
    private static final double MICRO_ALL_PIN_CHUNKS = 6.125D;
    /** 负对照档 {@code --negctl6}：四门临时钉 ≤6 ⇒ 必红自证（门非永绿假门）。 */
    private static final double NEGCTL_BAND_CHUNKS = 6.0D;

    /** 源下标：五主源 + 两派生辅助源。 */
    private static final int SRC_STEEP = 0;
    private static final int SRC_GENTLE = 1;
    private static final int SRC_GENTLE_CORE = 2;
    private static final int SRC_MARSH = 3;
    private static final int SRC_MICRO_IN = 4;
    private static final int SRC_MICRO_OUT = 5;
    private static final int SRC_MICRO_ALL = 6;
    private static final int SRC_N = 7;
    private static final String[] SRC_NAME = { "STEEP", "GENTLE", "GENTLE_CORE", "MARSH", "MICRO_IN",
        "MICRO_OUT", "MICRO_ALL" };
    /** sanity 门覆盖的主源（派生源不做 sanity）。 */
    private static final int[] SANITY_SRCS = { SRC_STEEP, SRC_GENTLE, SRC_MARSH, SRC_MICRO_IN, SRC_MICRO_OUT };

    public static void main(String[] args) {
        boolean negctl = false;
        for (final String a : args) {
            if ("--negctl6".equals(a)) {
                negctl = true;
            }
        }
        initLedger();
        final int g = SIDE / STRIDE;
        final List<List<Long>> compsBySrc = new ArrayList<>(SRC_N); // 每源全量连通域格数（跨 seed 合并）
        for (int i = 0; i < SRC_N; i++) {
            compsBySrc.add(new ArrayList<Long>());
        }
        final long[] colsBySrc = new long[SRC_N];
        long sampled = 0;
        long swampCols = 0;
        for (int s = 0; s < SEEDS; s++) {
            final long worldSeed = s;
            final boolean[][] hit = new boolean[SRC_N][g * g];
            for (int sz = 0; sz < g; sz++) {
                for (int sx = 0; sx < g; sx++) {
                    final int x = sx * STRIDE;
                    final int z = sz * STRIDE;
                    sampled++;
                    final int roster = ProsperityTerrainProfile.chainRosterIndexAt(worldSeed, x >> 2, z >> 2);
                    if (roster != 3) {
                        continue;
                    }
                    swampCols++;
                    final int tier = TerrainVariants.swampTierAt(worldSeed, x, z, roster);
                    if (tier == TerrainVariants.SWAMP_TIER_DEEP) {
                        final boolean inGentleDomain = tygMirror(worldSeed, x, z) > 0.0D;
                        final int idx = sx + sz * g;
                        if (inGentleDomain) {
                            hit[SRC_GENTLE][idx] = true;
                            if (gentleBlobMirror(worldSeed, x, z) >= 0.5D) {
                                hit[SRC_GENTLE_CORE][idx] = true;
                            }
                        } else {
                            hit[SRC_STEEP][idx] = true;
                        }
                    } else if (tier == TerrainVariants.SWAMP_TIER_MARSH) {
                        hit[SRC_MARSH][sx + sz * g] = true;
                    }
                    if (GTSRVoronoiRiverField.swampPoolWaterAt(worldSeed, x, z, roster)) {
                        final boolean netOn = GTSRWorldgenHash
                            .valueNoise(
                                worldSeed ^ GTSRVoronoiRiverField.SALT_SWAMP_NET,
                                x / GTSRVoronoiRiverField.SWAMP_NET_SCALE,
                                z / GTSRVoronoiRiverField.SWAMP_NET_SCALE)
                            > GTSRVoronoiRiverField.SWAMP_NET_GATE_LO;
                        final int idx = sx + sz * g;
                        hit[netOn ? SRC_MICRO_IN : SRC_MICRO_OUT][idx] = true;
                        hit[SRC_MICRO_ALL][idx] = true;
                    }
                }
            }
            for (int src = 0; src < SRC_N; src++) {
                for (final boolean cell : hit[src]) {
                    if (cell) {
                        colsBySrc[src]++;
                    }
                }
                floodFill(hit[src], g, compsBySrc.get(src));
            }
        }
        int sanityFails = 0;
        final StringBuilder sanityLine = new StringBuilder("CENSUS-SANITY");
        final double[] maxChunksBySrc = new double[SRC_N];
        for (int src = 0; src < SRC_N; src++) {
            final List<Long> comps = compsBySrc.get(src);
            long maxCells = 0;
            for (final long c : comps) {
                if (c > maxCells) {
                    maxCells = c;
                }
            }
            maxChunksBySrc[src] = chunks(maxCells);
            System.out.println(
                "CENSUS src=" + SRC_NAME[src] + " cols=" + colsBySrc[src] + " comps=" + comps.size()
                    + " maxCells=" + maxCells + " maxChunks=" + chunks(maxCells)
                    + " medChunks=" + medianChunks(comps) + " ge15Chunks=" + (chunks(maxCells) >= 15.0D));
            if (Arrays.binarySearch(SANITY_SRCS, src) >= 0) {
                final boolean ok = !comps.isEmpty() && maxCells >= CELLS_PER_CHUNK;
                sanityLine.append(' ').append(SRC_NAME[src]).append('=').append(ok ? "OK" : "FAIL");
                if (!ok) {
                    sanityFails++;
                }
            }
        }
        System.out.println(sanityLine);
        // ── 分源硬门（v1.20.54 P31 批II；带 = 主代理终裁 §2.2，非 ≤12 平带；MICRO_ALL 同值门）──
        final double bSteep = negctl ? NEGCTL_BAND_CHUNKS : MAX_STEEP_CHUNKS;
        final double bGentle = negctl ? NEGCTL_BAND_CHUNKS : MAX_GENTLE_CHUNKS;
        final double bMarsh = negctl ? NEGCTL_BAND_CHUNKS : MAX_MARSH_CHUNKS;
        int gateFails = 0;
        final StringBuilder gateLine = new StringBuilder("CENSUS-GATE");
        gateLine.append(" STEEP max=").append(maxChunksBySrc[SRC_STEEP]).append(" (<=").append(bSteep).append(')')
            .append(pass(maxChunksBySrc[SRC_STEEP] <= bSteep));
        gateLine.append(" | GENTLE max=").append(maxChunksBySrc[SRC_GENTLE]).append(" (<=").append(bGentle).append(')')
            .append(pass(maxChunksBySrc[SRC_GENTLE] <= bGentle));
        gateLine.append(" | MARSH max=").append(maxChunksBySrc[SRC_MARSH]).append(" (<=").append(bMarsh).append(')')
            .append(pass(maxChunksBySrc[SRC_MARSH] <= bMarsh));
        // MICRO_ALL 同值门：正式档 = 与批0 pre 锚逐位同值（E 分支未触 ⇒ 微池零漂移）；negctl 档 = ≤6 必红
        final boolean microOk = negctl ? maxChunksBySrc[SRC_MICRO_ALL] <= NEGCTL_BAND_CHUNKS
            : maxChunksBySrc[SRC_MICRO_ALL] == MICRO_ALL_PIN_CHUNKS;
        gateLine.append(" | MICRO_ALL max=").append(maxChunksBySrc[SRC_MICRO_ALL])
            .append(negctl ? " (<=" + NEGCTL_BAND_CHUNKS + ")" : " (==" + MICRO_ALL_PIN_CHUNKS + " 同值)").append(pass(microOk));
        if (maxChunksBySrc[SRC_STEEP] > bSteep) gateFails++;
        if (maxChunksBySrc[SRC_GENTLE] > bGentle) gateFails++;
        if (maxChunksBySrc[SRC_MARSH] > bMarsh) gateFails++;
        if (!microOk) gateFails++;
        System.out.println(gateLine);
        System.out.println(
            "SWAMP POOL CENSUS: "
                + (sanityFails == 0 && gateFails == 0
                    ? "ALL SANITY+GATE SOURCES OK (hard gate" + (negctl ? " NEGCTL6 负对照档" : "")
                        + ", caliber=" + SEEDS + "x" + SIDE + "^2 stride " + STRIDE + ", sampled=" + sampled
                        + " swampCols=" + swampCols + ")"
                    : sanityFails + " SANITY + " + gateFails + " GATE source(s) FAILED"
                        + (negctl ? " (NEGCTL6 负对照档：必红 = 门灵敏度自证通过)" : "")));
        if (sanityFails != 0 || gateFails != 0) {
            System.exit(1);
        }
    }

    private static String pass(boolean ok) {
        return ok ? " PASS" : " FAIL";
    }

    /** 同源 4-连通 flood-fill（BFS，就地清零已访问格；域格数计入 out）。 */
    private static void floodFill(boolean[] hit, int g, List<Long> out) {
        final int[] queue = new int[hit.length];
        for (int start = 0; start < hit.length; start++) {
            if (!hit[start]) {
                continue;
            }
            long size = 0;
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            hit[start] = false;
            while (head < tail) {
                final int cur = queue[head++];
                final int cx = cur % g;
                final int cz = cur / g;
                size++;
                if (cx > 0 && hit[cur - 1]) {
                    hit[cur - 1] = false;
                    queue[tail++] = cur - 1;
                }
                if (cx < g - 1 && hit[cur + 1]) {
                    hit[cur + 1] = false;
                    queue[tail++] = cur + 1;
                }
                if (cz > 0 && hit[cur - g]) {
                    hit[cur - g] = false;
                    queue[tail++] = cur - g;
                }
                if (cz < g - 1 && hit[cur + g]) {
                    hit[cur + g] = false;
                    queue[tail++] = cur + g;
                }
            }
            out.add(Long.valueOf(size));
        }
    }

    /** 格数 → 区块（每格 4×4 方块）。 */
    private static double chunks(long cells) {
        return cells / CELLS_PER_CHUNK;
    }

    /** 连通域面积（区块）中位数（跨 seed 合并全体域；空集 = NA）。 */
    private static String medianChunks(List<Long> comps) {
        if (comps.isEmpty()) {
            return "NA";
        }
        final long[] arr = new long[comps.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = comps.get(i).longValue();
        }
        Arrays.sort(arr);
        final double mid = arr.length % 2 == 1 ? chunks(arr[arr.length / 2])
            : (chunks(arr[arr.length / 2 - 1]) + chunks(arr[arr.length / 2])) / 2.0D;
        return String.format("%.2f", mid);
    }

    /** 缓坡潭域门 tyG 镜像（= TV.swampGates SWG_TYG 槽求值式，TV:1386-1388）。 */
    private static double tygMirror(long worldSeed, int x, int z) {
        final double tn = GTSRWorldgenHash
            .valueNoise(worldSeed ^ S_SWAMP_POOLTYPE, x / SWAMP_POOLTYPE_SCALE, z / SWAMP_POOLTYPE_SCALE);
        return gate01((-tn - POOLTYPE_GATE_LO) / POOLTYPE_GATE_SPAN);
    }

    /** 缓床 λ353 门值镜像（= TV.swampGates gentleGate 求值式，TV:1399-1402；LO 共享 0.68）。 */
    private static double gentleBlobMirror(long worldSeed, int x, int z) {
        final double n = GTSRWorldgenHash.valueNoise(
            worldSeed ^ S_SWAMP_DEEP_GENTLE,
            x / SWAMP_DEEP_GENTLE_BED,
            z / SWAMP_DEEP_GENTLE_BED);
        return gate01((n - DEEP_GATE_LO_SHARED) / GENTLE_GATE_SPAN);
    }

    /** smoothstep 带通（TerrainVariants.s01 同式镜像；带外精确 0.0/1.0）。 */
    private static double gate01(double t) {
        final double c = Math.max(0.0D, Math.min(1.0D, t));
        return c * c * (3.0D - 2.0D * c);
    }

    /** 离线账本装配（P29CDigest/P30GrottoProbe 同式；无账本 ⇒ chainRosterIndexAt 恒 −1 无 roster 3）。 */
    private static void initLedger() {
        final List<GTSRBiomeAuthority.BiomeId> keys = new ArrayList<>();
        for (final GTSRBiomeAuthority.BiomeId key : GTSRBiomeAuthority.BiomeId.values()) {
            if (GTSRBiomeAuthority.DIM_KEY_PROSPERITY.equals(key.dimKey())) {
                keys.add(key);
            }
        }
        for (int i = 0; i < IDS.length && i < keys.size(); i++) {
            GTSRBiomeAuthority.recordAllocation(keys.get(i), IDS[i], IDS[i], new BiomeGenBase(IDS[i]) {});
        }
    }
}
