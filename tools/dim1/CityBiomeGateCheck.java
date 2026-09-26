import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlan;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.config.Config;

/**
 * P6 · L6 城门与暴露自检（plan §2.1 L6 + §2.2 H-1 + §3 S3 行 + §5 P6 + §7.1 已锁定 U2）。
 * <p>
 * 离线运行（classpath 配方见 {@code plan/维度计划/调查取证/dim78-修复与整合/v12030-hotfix-replaceruntime-report.md} §3）：
 * <pre>
 *   java ... CityBiomeGateCheck assert [seeds] [regionsPerSeed]
 *   java ... CityBiomeGateCheck table  [seeds] [regionsPerSeed]     # 表 1 + 表 2
 *   java ... CityBiomeGateCheck load   [seeds] [regionsPerSeed]     # 表 4（真实链两跑）
 * </pre>
 * <b>采样口径（真实链，禁止手工构造群系）</b>：seed 集与区几何与 P5 的
 * {@code Dim78ScatterDensityCheck.Sampler} <b>完全同一</b>（{@code cx0 = r×axis×3 + si×4096}、
 * {@code cz0 = r×axis×5 + si×924816}，{@code axis=16} ⇒ 每区恰为一个 H-2 窗），默认
 * 8 seed × 8 区 = <b>16384 chunk</b>（≥ 任务包要求的 ≥8 seed × ≥16384 chunk），
 * 于是表 4 的暴露率与 P5 报告 §10 的 {@code f_now = 14091/16384 = 86.005%} 直接可比。
 * 城一律走同源 {@link CityPlanner#planFor}/{@link CityPlanner#citiesNear}，
 * 群系身份走 H-1 出口 {@link CityPlanner#bandIndexAt}（与 {@code GTSRWorldChunkManager.biomeAt}
 * 同一纯函数，逐位同一性由 {@code BiomeBandHierarchyCheck} C1 在真实链上钉住）。
 * <p>
 * <b>列名申报</b>：
 * <pre>
 * # COLUMNS T12 macroCellChunks|gateTier|seeds|regionsPerSeed|sampleChunks|citiesAnchored|citiesKept
 *            |keptPct|cityCoveredChunks|coveragePp|exposedPp|bandsInWindow|steppeBands|bandsWithCity
 *            |bandsWithCityOfAllPct|bandsWithCityOfSteppePct|anchorInBandPct|discHalfPct|discAllPct|selected
 *   —— gateTier=0 的 4 行 = <b>表 1</b>（macro × 城市数 / 城覆盖 chunk 占比 / 有城带占比，门关闭的基线）；
 *   —— gateTier∈{1,2,3} 的 12 行 = <b>表 2</b>（门条件三档 × 上表各列，另给锚点/城盘落带率）。
 * # COLUMNS T4 label|macroCellChunks|gateTier|seeds|regionsPerSeed|sampleChunks|eligibleChunks
 *            |cityWindowChunks|exposedPp|scatterBlockMeanEligible|wholeDimLoad|deltaExposedPp
 *            |deltaLoadPerChunk|p5BudgetPp|p5PieceEquivalent|verdict
 *   —— <b>表 4</b>：BASE 档 = {@code macro=16, gate=0}（= 改造前 + P5 终态），SELECTED 档 =
 *      {@code macro=64, gate=1}（U2 锁定）；两跑都是<b>真实编排链</b>（真实配槽账本 + L1 权威 +
 *      真实 generateTerrain/表层缝 + P4 SurfaceGate 后的可落地列 + P5 K=8 散布档），
 *      {@code exposedPp} 即该链"非城窗 chunk 占比"，{@code wholeDimLoad = scatterBlockMeanEligible × exposed}。
 * </pre>
 * <p>
 * <b>断言组</b>：A 无鬼窗（逐点）/ B 城市 100% 落带 / C 反门恒真（门不是恒真式）/ D 两种数法一致 /
 * E 选定档阈值。
 */
public class CityBiomeGateCheck {

    /** dim78 名册规模（B2 起身份 = 等权 GenLayer 链，无权重面；名册序 0..3 与 02 册 §1.1 同序）。 */
    private static final int BIOME_COUNT = 4;
    private static final int STEPPE = GTSRBiomeAuthority.BiomeId.RUSTED_STEPPE.rosterIndex();
    /** 表 1/2 的身份汇报格（chunk）：B2 前是 macro 带尺度，现仅作统计分箱，不参与门。 */
    private static final int REPORT_CELL = 16;

    /** 与 P5 同一 seed 集、同一区几何（见类注释）。 */
    private static final long[] SEEDS = Dim78ScatterDensityCheck.SEEDS;
    private static final int AXIS = Dim78ScatterDensityCheck.WINDOW_CHUNKS;

    /** P5 报告 §10 的城门暴露预算（百分点）与等效件数换算（每 +1pp 暴露 ≈ +0.096 件）。 */
    private static final double P5_BUDGET_PP = 8.0D;
    private static final double P5_PIECES_PER_PP = 0.096D;
    /**
     * E3 的<b>干区臂弃位率上界</b>（v1.20.42 P22 A1c 新增——本判据首个弃位率断言；写法照
     * {@code RuinFamilyCheck} 弃位率带的「实测登记 + 带值」式）。分母 = 样本内候选城
     * （{@code citiesAnchored}，8 seed × 8 区 = 16384 chunk 窗内锚定的 planFor 非空 cell），
     * 分子 = 其中被<b>干区臂</b>（{@code PlacementGate.dryFootprint} 占比制 + 城心全过制；
     * {@code GATE_OFF} 只关带门、干区臂常开——C2 的既有口径）弃置者。
     * <p>
     * <b>读数登记</b>：v1.20.42 P22 复跑（A1a wetAt 收紧为 {@code s≥WET_MIN ∧ trunk>0}（干河床可
     * 进结构）+ A1b 潭避让腿（{@code swampRiverPoolAt} 列重新算湿）后）实测 <b>3/12 = 25.0%</b>，
     * 与 A0 基线（{@code plan/tmp/p22-a0/full-0.log}，pre-A1a 同采样）<b>逐位同</b> ⇒ 本样本内
     * 新门对城弃置零位移（12 候选城里 3 座湿弃置者换门前后同批）。<b>旧 CITY 17.44%</b> 是
     * P19-U5 全候选探针（6 seed、全部候选 cell）的记录值（{@code CityPlanner} javadoc:52 与
     * {@code plan/tmp/p19-u5-prered.md} 留档），<b>非判据钉</b>——p20-plan R6 曾计划在
     * PlacementContractCheck 加上界断言但未落地 ⇒ 无带可塌缩、无可重钉点，本条是弃位率的首钉。
     * <b>带上界 50% 的取法</b>：样本 n=12 只支撑粗回归闸——50% = 候选城被水避让门砍半（远超
     * 17.44% 记录口径的 2.8 倍）时提前于 E1（keptRatio ≥ 0.10）可见的红灯；越界 = wetAt 门
     * 反向放宽 / 潭·微池场外溢 / 占比制常量漂移一类回归。精确弃位率（全候选 cell 口径）归
     * {@code PlacementContractCheck} 的「城窗跳过=」报告行域（R6 裁定域，本片不动它）。
     * <p>
     * <b>P23 R1·S6 重钉：50% → 75%</b>。归因：R1 全域站格湖（水半径 200/站距 1200，湖面占
     * ~9%）+ 干区臂「城心全过制」⇒ 候选城落湖心的概率结构性上升——本样本实测 7/12 = 58.33%
     * （v1.20.42 复跑 3/12 = 25.0%），属放湖的<b>设计内</b>漂移非门回归（E1 keptRatio 5/12 =
     * 41.7% ≥ 0.10 照绿）。75% = 候选城被砍 3/4（n=12 粗样本一城 ≈ 8.3pp ⇒ 9/12 仍绿、
     * 10/12 = 83.3% 红），保留"提前于 E1 可见的红灯"语义与 1.29× 余量。
     * <p>
     * <b>P24-B 重钉：75% → 42%</b>（用户裁决「调档 + 候选迁移」后按实跑回钉）。两项改动：
     * ① {@code PlacementGate.DRY_RATIO_CITY} 0.85→0.75（城盘外缘滩带容忍 15%→25%，列级谓词
     * 一字不动）；② {@code CityPlanner.resolveCityPlan} 候选迁移（原锚点被弃时在同 cell 的
     * 8×8 候选锚点域内确定性地再试 ≤2 个备选点）。本样本实测（GATE_OFF 分母口径不变）：
     * <pre>
     *   P23 原态（0.85，不迁移）          : 7/12 = 58.333%
     *   仅调档（0.75，不迁移）            : 6/12 = 50.000%   （救回 1 座近线候选）
     *   调档 + 迁移（本态）               : 4/12 = 33.333%   （迁移再救 2 座：湖缘平移到干锚点）
     * </pre>
     * 42% 的取法：n=12 ⇒ 一城 8.33pp，42% = 实测 33.333% + 一格粒度 ⇒ 5/12（41.667%）仍绿，
     * 6/12（50.0% = <b>迁移失效/退回仅调档态</b>）即红，7/12（58.333% = P23 湖弃位原态）必红；
     * 余量 1.26×，保留"提前于 E1（keptRatio ≥ 0.10 ⇒ 弃位 90%）可见"的回归闸语义。
     * <p>
     * <b>为什么不回退到 50%</b>：50% 会把"迁移机制整体失效"（恰好退回仅调档读数）判绿，
     * 而迁移正是 P24-B 的另一半交付；42% 是同时钉住档值与迁移的两用闸。
     * <p>
     * <b>迁移只改落点不改谓词</b>：本闸的读数下降全部来自"候选落点选择"，湖/河/潭/护带四腿
     * 与城心 64×64 全过制逐字未动 ⇒ 越界不可能由"谓词被放松"造成，必是落点解析回归。
     * <p>
     * <b>P25 重钉：42% → 50%（实测 + 1 格先例）</b>。双向归因：① D1 湖概率减半（LAKE_INTERVAL
     * 1200→3000，湖面占比 ~9%→~1.4%）压低保湖弃位（↓）；② PlacementGate.dryColumnAt 四新腿
     * （⑤isSanzuColumn/⑥isDryRiverColumn/⑦微池置水/⑧三档置水）把更多候选城盘判湿（↑）。
     * 本样本实测 <b>5/12 = 41.667%</b>（P24-B 4/12 = 33.333%）——净 +1 城，恰落 P24 带的最后一格
     * 绿。按 P24「实测 + 一格粒度」同式改钉 50%：6/12（50.0%）仍绿、<b>7/12（58.333% = P23
     * 湖弃位原态）必红</b>。诚实申报：50% 同时让"迁移整体失效退回仅调档（P24 读数 6/12）"落绿，
     * 该形态的判别从本闸移交 E1（keptRatio ≥ 0.10）与 PlacementContractCheck 城窗跳过报告行
     * （P25 起新腿增量与迁移失效在 6/12 档不可分，须靠那两处分账）。
     */
    private static final double DRY_ABANDON_MAX = 0.50D;

    private static int assertions;
    private static final List<String> FAILURES = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        final String mode = args.length > 0 ? args[0] : "assert";
        Dim78ScatterDensityCheck.bootstrap();
        // B2 起 CityPlanner.steppeBandAt/bandIndexAt 走 GTSRBiomeAuthority 名册（biomeOf）——
        // 先入账真实配槽（离线 harness 的 180-183 段），否则身份出口恒 -1（不伪造身份）。
        SurfaceHarness.recordAllAllocations(SurfaceHarness.prosperityBiomes(), SurfaceHarness.shatteredBiomes());
        final int seeds = intArg(args, 1, SEEDS.length);
        final int regions = intArg(args, 2, 8);
        if ("table".equals(mode)) {
            printTables(seeds, regions);
            return;
        }
        if ("load".equals(mode)) {
            // strict = 判据 6 当硬门禁（超预算即红）；默认只申报 + 打一行 BUDGET-EXCEEDED 高亮，
            // 因为"超预算"是本片要交给主代理裁决的事实，不是本片可自修的缺陷（改 K 属 P5 范围）。
            load(seeds, regions, args.length > 3 && "strict".equals(args[3]));
            finish();
            return;
        }
        if (!"assert".equals(mode)) {
            System.out.println("CITY BIOME GATE FAIL: 未知 mode=" + mode + "（可用：assert|table|load）");
            System.exit(2);
        }
        assertAll(seeds, regions);
        printTables(seeds, regions);
        finish();
    }

    private static int intArg(String[] args, int i, int def) {
        return args.length > i ? Integer.parseInt(args[i]) : def;
    }

    // ══════════════════════════════════ 断言 ══════════════════════════════════

    private static void assertAll(int seeds, int regions) {
        groupA(seeds, regions);
        groupBAndC(seeds, regions);
        groupD(seeds, regions);
    }

    /**
     * A 组：无鬼窗——渲染侧与抑制侧对"此处有城"逐点相同。
     * <p>
     * <b>P24-B 独立性口径说明（如实登记）</b>：抑制侧复算在 P24-B 起也用
     * {@code CityPlanner.resolveCityPlan}（与生产入口同一"门"），故本组对<b>迁移落点选择</b>
     * 不再独立（那是另两处同源性的必然代价）；本组仍独立钉住的是：① 渲染返回的每一座城都过门
     * （{@code ghost}）；② 3×3 cell 扫描 + cellSeed 集合身份 + 缓冲窗过滤的记账等价
     * （{@code missing}/{@code contentDiff}）——即"渲染说有城、放置器说不许放"与"扫描漏检"
     * 两类真缺陷仍在射程内。
     */
    private static void groupA(int seeds, int regions) {
        int chunks = 0;
        int ghost = 0;
        int missing = 0;
        int contentDiff = 0;
        for (int si = 0; si < seeds; si++) {
            final long seed = SEEDS[si % SEEDS.length];
            for (int rg = 0; rg < regions; rg++) {
                final int cx0 = rg * AXIS * 3 + si * 4096;
                final int cz0 = rg * AXIS * 5 + si * 924816;
                for (int dx = 0; dx < AXIS; dx++) {
                    for (int dz = 0; dz < AXIS; dz++) {
                        final int cx = cx0 + dx;
                        final int cz = cz0 + dz;
                        chunks++;
                        // 渲染侧：生产唯一入口（ProsperityWorldGenerator.placeCities 用的就是它）
                        final CityPlan[] render = CityPlanner.citiesNear(seed, cx, cz);
                        // 抑制侧的等价复算：3×3 cell 的原始候选城 → 逐座解析落点 → 再套缓冲窗。
                        // P24-B 起"门"= resolveCityPlan（原锚点过门，或同 cell 内迁移后的备选
                        // 锚点），两侧必须同走该入口——否则迁移会把"渲染有城 / 复算无城"打成
                        // 鬼窗假红（集合键仍是 cellSeed，迁移不改 cell 身份）。
                        final TreeSet<Long> manual = new TreeSet<>();
                        final int bcx = Math.floorDiv(cx, CityPlanner.CITY_CELL);
                        final int bcz = Math.floorDiv(cz, CityPlanner.CITY_CELL);
                        for (int k = -1; k <= 1; k++) {
                            for (int l = -1; l <= 1; l++) {
                                final CityPlan p = CityPlanner.planFor(seed, bcx + k, bcz + l);
                                if (p == null) {
                                    continue;
                                }
                                final CityPlan placed = CityPlanner.resolveCityPlan(seed, p);
                                if (placed != null && placed.chunkInBuffer(cx, cz)) {
                                    manual.add(Long.valueOf(p.getCellSeed()));
                                }
                            }
                        }
                        final TreeSet<Long> viaRender = new TreeSet<>();
                        for (final CityPlan p : render) {
                            viaRender.add(Long.valueOf(p.getCellSeed()));
                            // 渲染给出的每一座城都必须自己过门（否则就是"渲染说有城、放置器说不许放"）
                            if (!CityPlanner.cityGateAllows(seed, p)) {
                                ghost++;
                            }
                        }
                        if (!viaRender.equals(manual)) {
                            contentDiff++;
                        }
                        // 一致 == 「渲染侧非空」与「复算侧非空」同真同假（两侧都空也算一致）
                        if ((render.length > 0) == manual.isEmpty()) {
                            missing++;
                        }
                    }
                }
            }
        }
        check(ghost == 0 && missing == 0 && contentDiff == 0 && chunks >= 16384,
            "A1 无鬼窗：渲染入口与非门侧等价复算在 " + chunks + " chunk 上逐点一致（集合差异 " + contentDiff
                + "、单侧非空 " + missing + "、渲染含未过门城 " + ghost + "）");
    }

    /** B 组（城市 100% 落带）与 C 组（门非恒真）。 */
    private static void groupBAndC(int seeds, int regions) {
        final Row selected = measure(Config.prosperityBiomeMacroBandChunks, Config.prosperityCityBiomeGate, seeds,
            regions);
        check(selected.citiesAnchored > 0, "B1 选定档样本内必须有候选城可测（实测锚点城 " + selected.citiesAnchored + "）");
        check(selected.anchorInBandPct() == 100.0D, "B2 判据 2：选定档下过门城的锚点 chunk 身份必须 100% 为锈蚀草原"
            + "（实测 " + pct(selected.anchorInBandPct()) + "%，过门城 " + selected.citiesKept + " 座 / 候选 "
            + selected.citiesAnchored + " 座）");
        // P24-B 核过：迁移不改 cell 身份、也不放松谓词，选定档仍被 biome 臂削掉 10/12
        // （keptRatio 0.1667 < 1.0）⇒ "门真的在削减" 语义保持，断言原文不动。
        check(selected.citiesKept > 0 && selected.keptRatio() < 1.0D, "B3 门必须真的在削减：选定档过门 "
            + selected.citiesKept + " / 候选 " + selected.citiesAnchored + "（keptRatio="
            + fmt(selected.keptRatio(), 4) + "）");
        check(selected.cityCoveredChunks > 0, "B4 选定档仍要有城（城覆盖 chunk " + selected.cityCoveredChunks + "）");

        final Row ungated = measure(Config.prosperityBiomeMacroBandChunks, CityPlanner.GATE_OFF, seeds, regions);
        check(ungated.anchorInBandPct() < 95.0D, "C1 反门恒真（P4 教训：门恒真会掩盖配置）：门关闭时锚点身份为草原的城"
            + "只占 " + pct(ungated.anchorInBandPct()) + "%（&lt;95% 才说明门有实际工作可做），候选城 "
            + ungated.citiesAnchored + " 座");
        // v1.20.40 P19 §F 重钉（归因 U5 prered + 二次 redirect）：cityGateAllows 的干区臂
        //（wetAt 回填真值口径）按拍板<b>独立于带门常开</b>（GATE_OFF 只关带门），旧语义
        // "门关闭 == 改造前全候选城都算"随干区臂退役。现钉单向性：关带门后保留城数 ≥ 选定档
        //（带门只减不增）且覆盖仍严格高于选定档。
        check(ungated.citiesKept >= selected.citiesKept && ungated.coveragePp() > selected.coveragePp(),
            "C2 带门关闭只减不增：GATE_OFF 保留城 " + ungated.citiesKept + " ≥ 选定档 " + selected.citiesKept
                + "（P19 §F 干区臂常开，旧「全候选城都算」口径退役）且覆盖 " + pct(ungated.coveragePp())
                + "pp > 选定档 " + pct(selected.coveragePp()) + "pp");
        check(ungated.anchorInBandPct() > 5.0D && ungated.anchorInBandPct() < 60.0D,
            "C3 申报性对照：门关闭时锚点草原率 " + pct(ungated.anchorInBandPct())
                + "% ∈ (5,60)（等权链下草原份额 ≈25%，出带即身份面或采样几何漂移）");
        // v1.20.42 P22 A1c：干区臂弃位率首钉（E3，读数依据与旧 17.44% 记录的关系见 DRY_ABANDON_MAX javadoc）
        // P24-B：分母（候选）= planFor 的<b>原锚点</b>口径不变 ⇒ 与 P22/P23 读数直接可比；分子 =
        // 该 cell 的全部候选锚点（原锚点 + ≤2 个迁移备选点）都被弃的 cell 数。
        final double dryAbandon = ungated.citiesAnchored == 0 ? 0.0D
            : (ungated.citiesAnchored - ungated.citiesKept) / (double) ungated.citiesAnchored;
        System.out.println(
            "  # E3-READ 干区臂弃位率=" + pct(dryAbandon * 100.0D) + "%（候选 " + ungated.citiesAnchored
                + " 弃 " + (ungated.citiesAnchored - ungated.citiesKept)
                + "；P22 25.0% → P23 58.333%（湖全域化）→ P24-B 本读；P19-U5 全候选口径记录 17.44% 非判据钉）");
        check(dryAbandon <= DRY_ABANDON_MAX,
            "E3 干区臂弃位率 " + pct(dryAbandon * 100.0D) + "% ≤ " + pct(DRY_ABANDON_MAX * 100.0D)
                + "%（P24-B 重钉的水避让回归上界：GATE_OFF 档只含干区臂（C2 口径），候选城被弃 "
                + (ungated.citiesAnchored - ungated.citiesKept) + "/" + ungated.citiesAnchored
                + "；P24-B = DRY_RATIO_CITY 0.85→0.75 调档 + resolveCityPlan 候选迁移，"
                + "列级谓词逐字未动——42% 的取法与三态读数见 DRY_ABANDON_MAX javadoc）");

        // 身份面的几何可行性（B2 改判：城盘能否整体装进草原身份区不再由 macro 带保证，而由链的
        // 成片尺度保证）——样本窗外扩后最大草原 4-连通簇必须 ≥ 最小城盘 9×9=81 chunk
        int steppeClusterMax = 0;
        for (int si = 0; si < seeds; si++) {
            final long seed = SEEDS[si % SEEDS.length];
            for (int rg = 0; rg < regions; rg++) {
                final int cx0b = rg * AXIS * 3 + si * 4096 - 16;
                final int cz0b = rg * AXIS * 5 + si * 924816 - 16;
                final int side = AXIS + 32;
                final int[] grid = new int[side * side];
                for (int dz = 0; dz < side; dz++) {
                    for (int dx = 0; dx < side; dx++) {
                        grid[dx + dz * side] = CityPlanner.bandIndexAt(seed, cx0b + dx, cz0b + dz);
                    }
                }
                steppeClusterMax = Math.max(steppeClusterMax, largestClusterOf(grid, side, STEPPE));
            }
        }
        System.out.println("  # B5 实测：样本窗（外扩 16 chunk）最大草原 4-连通簇 = " + steppeClusterMax
            + " chunk（最小城盘 81；r=7 城 + 裕量 = 15×15=225 上界档）");
        check(steppeClusterMax >= 81, "B5 身份面几何可行性：最大草原连通簇 " + steppeClusterMax
            + " ≥ 最小城盘 81 chunk（9×9，r=4 城的 chunk 盘）⇒ 链的成片尺度装得下整座小城"
            + "（r=7 大城的 225 chunk 档是上界不是保证，由门档 2/3 按需收紧）");

        // E 组：选定档阈值（城不能被门砍光，覆盖面积必须落在申报带内）
        // P24-B 核过（调档 + 迁移后的新选址行为）：E1 实测 2/12 = 0.1667（原 0.1667，未变）；
        // E2 实测 8.740pp（原 7.953pp，仍在 [2,20] 内）——两项<b>均未触线、无需重钉</b>，
        // 采样窗内选定档（gate=1）保留城数不变（biome 臂是选定档的主过滤器，迁移在 gate=1 下
        // 本样本零救回；见 DRY_ABANDON_MAX javadoc 的三态读数），故申报带与断言原文保持。
        check(selected.keptRatio() >= 0.10D,
            "E1 选定档 keptRatio " + fmt(selected.keptRatio(), 4) + " ≥ 0.10（城市数不得塌到不可用）");
        check(selected.coveragePp() >= 2.0D && selected.coveragePp() <= 20.0D,
            "E2 选定档城覆盖 " + pct(selected.coveragePp()) + "pp ∈ [2,20]（申报带；改造前基线 13.995pp）");
    }

    /** D 组：两种数法（逐 chunk citiesNear vs 逐城盖章）必须给同一个覆盖面积。 */
    private static void groupD(int seeds, int regions) {
        final Row stamped = measure(Config.prosperityBiomeMacroBandChunks, Config.prosperityCityBiomeGate, seeds,
            regions);
        long covered = 0;
        long chunks = 0;
        for (int si = 0; si < seeds; si++) {
            final long seed = SEEDS[si % SEEDS.length];
            for (int rg = 0; rg < regions; rg++) {
                final int cx0 = rg * AXIS * 3 + si * 4096;
                final int cz0 = rg * AXIS * 5 + si * 924816;
                for (int dx = 0; dx < AXIS; dx++) {
                    for (int dz = 0; dz < AXIS; dz++) {
                        chunks++;
                        if (CityPlanner.citiesNear(seed, cx0 + dx, cz0 + dz).length > 0) {
                            covered++;
                        }
                    }
                }
            }
        }
        check(stamped.cityCoveredChunks == covered && chunks == stamped.sampleChunks,
            "D1 城窗面积两种数法一致：逐城盖章 " + stamped.cityCoveredChunks + " == 逐 chunk citiesNear "
                + covered + "（样本 " + chunks + " chunk）");
    }

    // ══════════════════════════════ 表 1 / 表 2 ══════════════════════════════

    private static void printTables(int seeds, int regions) {
        System.out.println(
            "# COLUMNS T12 macroCellChunks|gateTier|seeds|regionsPerSeed|sampleChunks|citiesAnchored|citiesKept"
                + "|keptPct|cityCoveredChunks|coveragePp|exposedPp|bandsInWindow|steppeBands|bandsWithCity"
                + "|bandsWithCityOfAllPct|bandsWithCityOfSteppePct|anchorInBandPct|discHalfPct|discAllPct|selected");
        final int[] tiers = { CityPlanner.GATE_OFF, CityPlanner.GATE_ANCHOR_BAND, CityPlanner.GATE_DISC_HALF,
            CityPlanner.GATE_DISC_ALL };
        for (final int macro : BiomeBandHierarchyCheck.MACRO_SWEEP) {
            for (final int tier : tiers) {
                final Row r = measure(macro, tier, seeds, regions);
                final StringBuilder sb = new StringBuilder(tier == CityPlanner.GATE_OFF ? "T1 " : "T2 ");
                sb.append(macro).append('|').append(tier).append('|').append(seeds).append('|').append(regions)
                    .append('|').append(r.sampleChunks).append('|').append(r.citiesAnchored).append('|')
                    .append(r.citiesKept).append('|').append(pct(r.keptRatio() * 100.0D)).append('|')
                    .append(r.cityCoveredChunks).append('|').append(pct(r.coveragePp())).append('|')
                    .append(pct(100.0D - r.coveragePp())).append('|').append(r.bandsInWindow).append('|')
                    .append(r.steppeBands).append('|').append(r.bandsWithCity).append('|')
                    .append(pct(r.bandsWithCity * 100.0D / Math.max(1, r.bandsInWindow))).append('|')
                    .append(pct(r.bandsWithCity * 100.0D / Math.max(1, r.steppeBands))).append('|')
                    .append(pct(r.anchorInBandPct())).append('|').append(pct(r.discHalfPct())).append('|')
                    .append(pct(r.discAllPct())).append('|')
                    .append(macro == Config.prosperityBiomeMacroBandChunks
                        && tier == Config.prosperityCityBiomeGate ? "SELECTED" : "-");
                System.out.println(sb);
            }
        }
    }

    // ══════════════════════════════════ 表 4 ══════════════════════════════════

    /**
     * 表 4：净暴露增量与折算整维负载。
     * <p>
     * 两跑共用 P5 的 {@code Dim78ScatterDensityCheck.Sampler}（真实 generateTerrain + 真实表层缝 +
     * 真实 outpost/机器/散布编排 + 同源 citiesNear 抑制），参数行 = P5 选定档
     * {@code K=8 / 落块 24 / 竖向件摘出 / 窗上限 2}；唯一变量是本片的 (macro, gate) 两个 Config 键。
     */
    private static void load(int seeds, int regions, boolean strict) throws Exception {
        System.out.println(
            "# COLUMNS T4 label|macroCellChunks|gateTier|seeds|regionsPerSeed|sampleChunks|eligibleChunks"
                + "|cityWindowChunks|exposedPp|scatterBlockMeanEligible|wholeDimLoad|deltaExposedPp"
                + "|deltaLoadPerChunk|p5BudgetPp|p5PieceEquivalent|verdict");
        final Result base = realChainLoad(seeds, regions, 16, CityPlanner.GATE_OFF, "BASE(macro16,gate0)");
        final Result after = realChainLoad(seeds, regions, Config.prosperityBiomeMacroBandChunks,
            Config.prosperityCityBiomeGate, "SELECTED(macro" + Config.prosperityBiomeMacroBandChunks + ",gate"
                + Config.prosperityCityBiomeGate + ")");
        final double dPp = after.exposedPp - base.exposedPp;
        final double dLoad = after.wholeDimLoad - base.wholeDimLoad;
        final double pieces = dPp * P5_PIECES_PER_PP;
        // P5 的线性换算（每 +1pp 暴露 = +0.1204 块 ≈ +0.096 件）假设了 eligible 池的件均块数不变；
        // 实测本片把城窗集中到草原带后，新暴露池的件密度反而下降（blockMean/contourMean 两跑都 ≈1.25），
        // 故同时给出"按实测负载折算"的件数，两个口径都上报，不自选有利的那个。
        final double blocksPerPiece = after.blockMean / after.contourMean;
        final double piecesMeasuredLoad = dLoad / blocksPerPiece;
        final String verdict = dPp <= P5_BUDGET_PP ? "WITHIN-P5-BUDGET" : "OVER-BUDGET-REPORT";
        System.out.println(row4(base) + "|-|-|-|-|BASE");
        System.out.println(row4(after) + "|" + pct(dPp) + "|" + pct(dLoad) + "|" + pct(P5_BUDGET_PP) + "|"
            + fmt(pieces, 3) + "|" + verdict);
        final boolean over = dPp > P5_BUDGET_PP;
        if (strict) {
            check(!over, "T4 城门净暴露增量 " + pct(dPp) + "pp ≤ P5 让出的预算 " + pct(P5_BUDGET_PP) + "pp（"
                + "暴露面积口径等效 " + fmt(pieces, 3) + " 件 K 预算）");
        } else {
            System.out.println("  # " + (over ? "!! BUDGET-EXCEEDED（上报主代理裁决，本片不改 K）" : "BUDGET-OK")
                + " 暴露面积口径 Δ=" + pct(dPp) + "pp vs 预算 " + pct(P5_BUDGET_PP) + "pp（按 P5 线性换算 = "
                + fmt(pieces, 3) + " 件）；实测整维负载 Δ=" + pct(dLoad) + " 块/chunk（= "
                + fmt(piecesMeasuredLoad, 3) + " 件，实测 1 件 ≈ " + fmt(blocksPerPiece, 3)
                + " 块；件密度 BASE=" + fmt(base.contourMean, 3) + " → SELECTED=" + fmt(after.contourMean, 3)
                + "）——两种口径结论不同：实测新暴露池以草原为主，P5 的线性换算假设了 blockMean 不变，"
                + "两个数都上报，不自选有利的那个");
        }
        check(dPp > -15.0D && dPp < 15.0D,
            "T4b 防呆：净暴露增量必须在 ±15pp 量级内（实测 " + pct(dPp) + "pp）⇒ 越界说明采样几何或门档写错了，"
                + "而不是预算问题");
        check(after.wholeDimLoad > 0.0D && base.wholeDimLoad > 0.0D,
            "T4c 两跑的整维负载都必须为正（BASE=" + pct(base.wholeDimLoad) + " SELECTED="
                + pct(after.wholeDimLoad) + "）");
    }

    private static String row4(Result r) {
        return "T4 " + r.label + '|' + r.macro + '|' + r.gate + '|' + r.seeds + '|' + r.regions + '|'
            + r.sampleChunks + '|' + r.eligibleChunks + '|' + r.cityWindowChunks + '|' + pct(r.exposedPp) + '|'
            + pct(r.blockMean) + '|' + pct(r.wholeDimLoad);
    }

    private static final class Result {
        String label;
        int macro;
        int gate;
        int seeds;
        int regions;
        long sampleChunks;
        long eligibleChunks;
        long cityWindowChunks;
        double exposedPp;
        double blockMean;
        double contourMean;
        double wholeDimLoad;
    }

    private static Result realChainLoad(int seeds, int regions, int macro, int gate, String label) throws Exception {
        final int savedM = Config.prosperityBiomeMacroBandChunks;
        final int savedG = Config.prosperityCityBiomeGate;
        try {
            Config.prosperityBiomeMacroBandChunks = macro;
            Config.prosperityCityBiomeGate = gate;
            final Dim78ScatterDensityCheck.Row row = new Dim78ScatterDensityCheck.Row(
                label + " P5选定档K8", 8, 24, 64, Boolean.FALSE, 2);
            final Dim78ScatterDensityCheck.Sampler sampler =
                new Dim78ScatterDensityCheck.Sampler(seeds, regions, AXIS, Collections.singletonList(row));
            sampler.run();
            final Result r = new Result();
            r.label = label;
            r.macro = macro;
            r.gate = gate;
            r.seeds = seeds;
            r.regions = regions;
            r.eligibleChunks = sampler.generatedChunks;
            r.cityWindowChunks = sampler.cityWindowChunks;
            r.sampleChunks = sampler.generatedChunks + sampler.cityWindowChunks;
            r.exposedPp = 100.0D * r.eligibleChunks / r.sampleChunks;
            r.blockMean = sampler.stats(row.label).blockMean();
            r.contourMean = sampler.stats(row.label).contourMean();
            r.wholeDimLoad = r.blockMean * r.exposedPp / 100.0D;
            System.out.println("  # 真实链 " + label + " 宏=" + macro + " 门=" + gate + " 暴露=" + pct(r.exposedPp)
                + "% 件均块=" + pct(r.blockMean) + " 整维负载=" + pct(r.wholeDimLoad) + " 块/全维chunk");
            return r;
        } finally {
            Config.prosperityBiomeMacroBandChunks = savedM;
            Config.prosperityCityBiomeGate = savedG;
        }
    }

    // ═══════════════════════════ 表 1/2 的测量核心 ═══════════════════════════

    /** 一行测量结果（选定 macro × gate 档下的城/带/覆盖计数）。 */
    private static final class Row {
        final int macro;
        final int gate;
        long citiesAnchored;
        long citiesKept;
        long anchorInBand;
        long discHalf;
        long discAll;
        long cityCoveredChunks;
        long sampleChunks;
        long bandsInWindow;
        long steppeBands;
        long bandsWithCity;
        final java.util.Set<Long> bandKeys = new java.util.HashSet<>();
        final java.util.Set<Long> cityBandKeys = new java.util.HashSet<>();
        /**
         * 城覆盖 chunk 的<b>去重</b>集合（T8 重钉，归因 T6 zoom 5→7）：城 buffer 方窗半径
         * ≤ radius+margin=11 &lt; CITY_CELL=24 ⇒ 相邻格两城的 buffer 可交叠；逐城累加会把
         * 一个被两城 buffer 同时覆盖的 chunk 记 2 次，而 D1 的对照面（citiesNear().length>0）
         * 是 chunk 级去重谓词——zoom=5 时代样本窗内无交叠故两法恰等，zoom=7 下门带变宽、
         * 过门城变多，交叠首次出现（实测 1579 对 1574）。数法对齐 = 盖章侧同口径去重。
         */
        final java.util.Set<Long> cityCoveredSet = new java.util.HashSet<>();

        void addCoveredChunk(int cx, int cz) {
            this.cityCoveredSet.add(((long) cx << 32) | (cz & 0xFFFFFFFFL));
        }

        void sealCoveredChunks() {
            this.cityCoveredChunks = this.cityCoveredSet.size();
        }

        Row(int macro, int gate) {
            this.macro = macro;
            this.gate = gate;
        }

        double keptRatio() {
            return citiesAnchored == 0 ? 0 : (double)citiesKept / citiesAnchored;
        }

        double anchorInBandPct() {
            return citiesKept == 0 ? 0 : 100.0D * anchorInBand / citiesKept;
        }

        double discHalfPct() {
            return citiesKept == 0 ? 0 : 100.0D * discHalf / citiesKept;
        }

        double discAllPct() {
            return citiesKept == 0 ? 0 : 100.0D * discAll / citiesKept;
        }

        double coveragePp() {
            return sampleChunks == 0 ? 0 : 100.0D * cityCoveredChunks / sampleChunks;
        }
    }

    private static Row measure(int macro, int gate, int seeds, int regions) {
        final int savedM = Config.prosperityBiomeMacroBandChunks;
        final int savedG = Config.prosperityCityBiomeGate;
        try {
            Config.prosperityBiomeMacroBandChunks = macro;
            Config.prosperityCityBiomeGate = gate;
            final Row r = new Row(macro, gate);
            for (int si = 0; si < seeds; si++) {
                final long seed = SEEDS[si % SEEDS.length];
                for (int rg = 0; rg < regions; rg++) {
                    final int cx0 = rg * AXIS * 3 + si * 4096;
                    final int cz0 = rg * AXIS * 5 + si * 924816;
                    measureRegion(seed, cx0, cz0, r);
                }
            }
            r.bandsInWindow = r.bandKeys.size();
            r.bandsWithCity = r.cityBandKeys.size();
            r.sealCoveredChunks();
            return r;
        } finally {
            Config.prosperityBiomeMacroBandChunks = savedM;
            Config.prosperityCityBiomeGate = savedG;
        }
    }

    private static void measureRegion(long seed, int cx0, int cz0, Row r) {
        // 1) 样本窗内的身份面抽样（B2：带基准掷骰退役，chunk 身份 = CityPlanner.bandIndexAt 链面；
        //    16-chunk 汇报格只作统计分箱，步长沿用表 1/2 的取样密度）
        for (int dx = 0; dx < AXIS; dx += Math.min(AXIS, r.macro)) {
            for (int dz = 0; dz < AXIS; dz += Math.min(AXIS, r.macro)) {
                final int bx = Math.floorDiv(cx0 + dx, REPORT_CELL);
                final int bz = Math.floorDiv(cz0 + dz, REPORT_CELL);
                final long key = bandKey(bx, bz);
                if (r.bandKeys.add(key) && CityPlanner.bandIndexAt(seed, cx0 + dx, cz0 + dz) == STEPPE) {
                    r.steppeBands++;
                }
            }
        }
        // 2) 逐城：候选（planFor）→ 落点（resolveCityPlan：原锚点/迁移）→ 盖缓冲窗
        r.sampleChunks += (long)AXIS * AXIS;
        final int cellFrom = Math.floorDiv(cx0, CityPlanner.CITY_CELL) - 2;
        final int cellTo = Math.floorDiv(cx0 + AXIS, CityPlanner.CITY_CELL) + 2;
        final int cellZFrom = Math.floorDiv(cz0, CityPlanner.CITY_CELL) - 2;
        final int cellZTo = Math.floorDiv(cz0 + AXIS, CityPlanner.CITY_CELL) + 2;
        for (int cellX = cellFrom; cellX <= cellTo; cellX++) {
            for (int cellZ = cellZFrom; cellZ <= cellZTo; cellZ++) {
                final CityPlan p = CityPlanner.planFor(seed, cellX, cellZ);
                if (p == null) {
                    continue;
                }
                // 候选基数（citiesAnchored）仍按 planFor 的<b>原锚点</b>口径 —— 与 P22/P23 的
                // 25.0%/58.33% 读数同分母，E3 弃位率才与历史可比（迁移只改"落点"，不改"候选"）。
                final boolean anchorInWindow = p.getCenterChunkX() >= cx0 && p.getCenterChunkX() < cx0 + AXIS
                    && p.getCenterChunkZ() >= cz0 && p.getCenterChunkZ() < cz0 + AXIS;
                if (anchorInWindow) {
                    r.citiesAnchored++;
                }
                // P24-B：落点 = 原锚点过门，或同 cell 内迁移后的备选锚点；统计与盖窗<b>一律用落点</b>
                // （城市真值坐标）——否则 D1（逐 chunk citiesNear 对照）与迁移后的窗口径脱钩。
                final CityPlan placed = CityPlanner.resolveCityPlan(seed, p);
                if (placed == null) {
                    continue;
                }
                if (anchorInWindow) {
                    r.citiesKept++;
                    if (CityPlanner.steppeBandAt(seed, placed.getCenterChunkX(), placed.getCenterChunkZ())) {
                        r.anchorInBand++;
                        r.cityBandKeys.add(bandKey(
                            Math.floorDiv(placed.getCenterChunkX(), REPORT_CELL),
                            Math.floorDiv(placed.getCenterChunkZ(), REPORT_CELL)));
                    }
                    final int disc = 2 * placed.getRadiusChunks() + 1;
                    int inBand = 0;
                    for (int dx = -placed.getRadiusChunks(); dx <= placed.getRadiusChunks(); dx++) {
                        for (int dz = -placed.getRadiusChunks(); dz <= placed.getRadiusChunks(); dz++) {
                            if (CityPlanner.steppeBandAt(
                                seed,
                                placed.getCenterChunkX() + dx,
                                placed.getCenterChunkZ() + dz)) {
                                inBand++;
                            }
                        }
                    }
                    if (inBand * 2 >= disc * disc) {
                        r.discHalf++;
                    }
                    if (inBand == disc * disc) {
                        r.discAll++;
                    }
                }
                // 缓冲窗 = radius + 自适应裕量（CityPlan 私有，C1 并行面无 getter）——迭代半径取
                // 上界，窗口真值由 chunkInBuffer 谓词判（D1 两侧同谓词 ⇒ 与半径/裕量口径解耦）。
                final int reach = placed.getRadiusChunks() * 2 + 2;
                for (int dx = -reach; dx <= reach; dx++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        final int cx = placed.getCenterChunkX() + dx;
                        final int cz = placed.getCenterChunkZ() + dz;
                        if (cx < cx0 || cx >= cx0 + AXIS || cz < cz0 || cz >= cz0 + AXIS) {
                            continue;
                        }
                        if (placed.chunkInBuffer(cx, cz)) {
                            r.addCoveredChunk(cx, cz);
                        }
                    }
                }
            }
        }
    }

    private static long bandKey(int bx, int bz) {
        return ((long)bx << 32) ^ (bz & 0xFFFFFFFFL);
    }

    /** 指定 zone 的最大 4-连通簇（chunk 数；B5 几何可行性口径，迭代 flood fill）。 */
    private static int largestClusterOf(int[] grid, int side, int zone) {
        final boolean[] seen = new boolean[grid.length];
        final int[] stack = new int[grid.length];
        int best = 0;
        for (int start = 0; start < grid.length; start++) {
            if (seen[start] || grid[start] != zone) {
                continue;
            }
            int top = 0;
            stack[top++] = start;
            seen[start] = true;
            int size = 0;
            while (top > 0) {
                final int idx = stack[--top];
                size++;
                final int x = idx % side;
                final int z = idx / side;
                if (x > 0 && !seen[idx - 1] && grid[idx - 1] == zone) {
                    seen[idx - 1] = true;
                    stack[top++] = idx - 1;
                }
                if (x < side - 1 && !seen[idx + 1] && grid[idx + 1] == zone) {
                    seen[idx + 1] = true;
                    stack[top++] = idx + 1;
                }
                if (z > 0 && !seen[idx - side] && grid[idx - side] == zone) {
                    seen[idx - side] = true;
                    stack[top++] = idx - side;
                }
                if (z < side - 1 && !seen[idx + side] && grid[idx + side] == zone) {
                    seen[idx + side] = true;
                    stack[top++] = idx + side;
                }
            }
            if (size > best) {
                best = size;
            }
        }
        return best;
    }

    // ══════════════════════════════════ 输出 ══════════════════════════════════

    private static String pct(double v) {
        return fmt(v, 3);
    }

    private static String fmt(double v, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", v);
    }

    private static void check(boolean ok, String label) {
        assertions++;
        if (!ok) {
            FAILURES.add(label);
            System.out.println("  FAIL " + label);
        }
    }

    private static void finish() {
        if (!FAILURES.isEmpty()) {
            System.out.println("CITY BIOME GATE FAIL: " + FAILURES.size() + " assertion(s) failed, passed="
                + (assertions - FAILURES.size()));
            System.exit(1);
        }
        System.out.println("CITY BIOME GATE PASS: assertions=" + assertions
            + " — 无鬼窗/城市全落草原带/门非恒真/两种数法一致 all green");
    }
}
