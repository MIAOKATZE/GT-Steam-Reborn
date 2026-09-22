import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerRosterFace;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSpans;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer.Outpost;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedColossusShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedColossusShapes.Colossus;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedColossusShapes.Morph;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedColossusShapes.Wreck;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachineShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinTemplate;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.StructureBurialTiers;

/**
 * <b>P17 S-D 判据：结构半埋的群系差异（沙漠更多是半埋）</b>。
 * <p>
 * 需求原话：「结构生成上也有些差异，比如沙漠更多是半埋的结构等等。」改前实测形状（P17-C）是
 * 「三族三套且全不分流」：巨构的运行期埋深分母是<b>模板常量</b> {@code maxBury}、废墟的半埋<b>烘进
 * 字符盘</b>、机器与 outpost <b>没有埋深参数</b>，群系身份只进到掷骰权重。本片把埋深系数改成
 * <b>按 L1 名册下标的档表</b>（{@link StructureBurialTiers}）并四族同源。本判据钉六件事：
 * <ol>
 * <li><b>A TABLE</b>：档表本体——沙漠档唯一最大、其余严格递减、默认档恒 0（身份缺失走默认档，
 * 不写身份等值判断）；</li>
 * <li><b>B CLAMP</b>：「埋深被推过 {@code sizeY/2} 是 clinit 抛而不是判据红」这条<b>无回归网高危点</b>
 * 的自证——穷举档表 × ceiling × 锚点，证明任何档取出的埋深都在「该形状已被验证过的那几档」之内；
 * 巨构那侧再钉 {@code contractValidatedThrough == maxBury}（契约恰好只覆盖到可达档 ⇒ 上界外扩一档就是
 * {@code ExceptionInInitializerError}，没有判据会替它红，所以本片只在界内重排分布）；</li>
 * <li><b>C TIER</b>：沙漠的埋深分布与露出比<b>显著</b>低于其他三族（逐盘 + 逐族两级读数，含
 * 「沙漠零埋深占比 = 0 / 最浅档仍 &gt; 0」这条「不是稍微」的证据）；</li>
 * <li><b>D WORLD</b>：走<b>生产</b>放置链的四族实测——悬浮 = 0、写进地形 = 0、每座必真落块、
 * 沙漠落块显著低于同锚点 burial=0 的基线（P16 那三口径沿用）；</li>
 * <li><b>E SOURCE</b>：源级红线（档表只有一份、四族各读同一出口、身份一律按<b>锚点原点</b>反查、
 * 巨构文件里那个「模板常量当分母」已移走、以及「不需要在 WorldGenerator 层传参」的实证）；</li>
 * <li><b>F DET</b>：双跑逐位一致 + 跨 seed 必变（反恒真）。</li>
 * </ol>
 * 用法：{@code java P17StructureBiomeVarianceCheck [srcRoot]}——{@code srcRoot} 缺省
 * {@code src/main/java}（E 组要读源文件）。零 Minecraft 世界依赖（接地走
 * {@link PlacementGate#groundFn(long)} 那条纯高度面），JEP330 离线可跑。
 */
public final class P17StructureBiomeVarianceCheck {

    /** 采样 seed（档内掷骰与地形接地的输入）。 */
    private static final long SEED = 0x5DA05DL;
    /** 另一把 seed（F 组「跨 seed 必变」的对照）。 */
    private static final long SEED_B = 0x0B1E117L;
    /** 身份缺失档（走档表默认档）；真值只在框架那一个常数里。 */
    private static final int NO_ID = GTSRGenLayerRosterFace.NO_IDENTITY;
    /** 埋深采样窗：每 chunk 的 16×16 原点抖动位（与 P16-B2 那扇抖动窗同形状）。 */
    private static final int ANCHORS = 16;
    /** 名册下标采样域（含 -1 与越界值，用来钉「越界一律默认档」）。 */
    private static final int[] ROSTER_PROBES = { NO_ID, 0, 1, 2, 3, -5, 4, 9, 99 };
    /** 沙漠档「露出比显著低于其他档」的比值上界。 */
    private static final double DESERT_EXPOSED_RATIO_MAX = 0.80D;
    /**
     * 沙漠档「埋深均值显著高于其他档」的倍数下界。
     * <p>
     * 这条线是<b>从档表本体推出来的</b>，不是照着读数画的：沙漠档均值恒等于验证档 {@code C}，其余档的
     * 均值上界是 {@code (floor + C)/2}（巨构族才有抖动窗；另三族是确定性下限，均值 = {@code floor}，
     * 比值只会更大），而档表里最大的非沙漠系数 0.22 在任何 ceiling 上量化出的 {@code floor ≤ C/3}
     * ⇒ 比值下界 {@code C / ((C/3 + C)/2) = 1.5}。实测各族落在 1.75-5.0（见 C 组读数行）。
     */
    private static final double DESERT_DEPTH_MEAN_MIN = 1.50D;
    /** 沙漠档露出比相对"其他三档里埋得最深那一档"的上界比值（C6，比 C4 更严）。 */
    private static final double DESERT_VS_NEAREST_MAX = 0.80D;
    /** WORLD 组：沙漠档落块相对「完全不埋」基线的上界比值（每族）。 */
    private static final double DESERT_WORLD_BLOCKS_MAX = 0.85D;
    /** 四族名（读数行的序）。 */
    private static final String[] FAMILIES = { "colossus", "machine", "outpost", "ruin" };
    /** 档表所在包（E 组读源文件用）。 */
    private static final String RUIN_PKG = "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/";
    /** 世界渲染的锚点采样（块坐标；跨两扇 chunk，覆盖「结构压邻槽」的形状）。 */
    private static final int[] WORLD_ANCHORS = { 3, 5, 12, 40, 44, 19, 25, 3, 57, 61, 33, 26, 4, 44, 50, 12 };

    private static int assertions;
    private static int failures;

    private P17StructureBiomeVarianceCheck() {}

    public static void main(String[] args) {
        final String srcRoot = args.length > 0 ? args[0] : "src/main/java";
        final List<Spec> specs = specs();
        System.out.println("# P17 S-D 结构群系差异判据：字符盘 " + specs.size() + " 张（四族） 档表 "
            + Arrays.toString(StructureBurialTiers.DEPTH_TIER_BY_ROSTER) + " 默认档 "
            + StructureBurialTiers.DEFAULT_DEPTH_TIER + " 露出比闸 "
            + StructureBurialTiers.CEILING_MIN_EXPOSED_PCT + "%");
        groupTable();
        groupClamp(specs);
        groupTier(specs);
        groupWorld(specs);
        groupSource(srcRoot);
        groupDeterminism(specs);
        System.out.println("P17-STRUCTURE-BIOME " + (failures == 0 ? "PASS" : "FAIL") + ": assertions=" + assertions
            + " failures=" + failures + " specs=" + specs.size());
        if (failures != 0) {
            System.exit(1);
        }
    }

    // ════════════════════════════ A 组：档表本体 ════════════════════════════

    private static void groupTable() {
        final double[] t = StructureBurialTiers.DEPTH_TIER_BY_ROSTER;
        final int rosterSize = dim78RosterSize();
        check(t.length == rosterSize,
            "A1 档表长度 == dim78 维内名册成员数（下标口径与 MACHINE_WEIGHTS/ordinalAt 同一张序）：" + t.length
                + " vs " + rosterSize);
        boolean inRange = true;
        for (final double v : t) {
            if (!(v >= 0.0D && v <= 1.0D)) {
                inRange = false;
            }
        }
        check(inRange, "A2 档表全部落在 [0,1]（埋深下限占验证档的比例，不是层数）：" + Arrays.toString(t));
        final int maxIdx = argMax(t);
        check(countEquals(t, t[maxIdx]) == 1,
            "A3 档表有唯一最大档（需求点名的沙漠档）：下标 " + maxIdx + " 值 " + t[maxIdx]);
        check(maxIdx == 2,
            "A3b 唯一最大档的下标 == 2 == 黄铜荒漠的 rosterIndex（由 GTSRBiomeAuthority.BiomeId 定序，不是猜的）");
        check(t[0] > t[1] && t[1] > t[3],
            "A4 非沙漠三档严格递减（plan §1-S-D 的「沙漠档最大、其余递减」）：" + t[0] + " > " + t[1] + " > " + t[3]);
        check(StructureBurialTiers.DEFAULT_DEPTH_TIER == 0.0D,
            "A5 默认档 == 0 ⇒ 巨构那侧窗口回到改造前那段 [0,maxBury]、另三族那侧恒 0");
        check(StructureBurialTiers.depthTierForRosterIndex(NO_ID) == StructureBurialTiers.DEFAULT_DEPTH_TIER
            && StructureBurialTiers.depthTierForRosterIndex(-5) == StructureBurialTiers.DEFAULT_DEPTH_TIER
            && StructureBurialTiers.depthTierForRosterIndex(99) == StructureBurialTiers.DEFAULT_DEPTH_TIER,
            "A6 身份缺失/越界下标一律回退默认档（没有「某群系 ⇒ 抑制」那种等值分支）");
        check(t[maxIdx] == 1.0D,
            "A7 最大档 == 1.0 ⇒ 它的截断量化值必然越界一次（ceiling+1）⇒ 夹紧被真实行使，B 组不是恒真式");
        System.out.println("# A 档表本体 verdict=" + verdict());
    }

    // ════════════════════ B 组：夹紧自证（clinit 高危点） ════════════════════

    private static void groupClamp(List<Spec> specs) {
        int probes = 0;
        boolean ok = true;
        for (int ceiling = -3; ceiling <= 40; ceiling++) {
            for (final int roster : ROSTER_PROBES) {
                final int floor = StructureBurialTiers.floorDepthFor(ceiling, roster);
                probes++;
                if (floor < 0 || floor > Math.max(0, ceiling)) {
                    ok = false;
                }
            }
        }
        check(ok, "B1 夹紧穷举 " + probes + " 组（ceiling ∈ [-3,40] × 名册下标 ∈ " + Arrays.toString(ROSTER_PROBES)
            + "）：埋深下限恒 ∈ [0, max(0,ceiling)]，一次越界都没有");
        final int desert = argMax(StructureBurialTiers.DEPTH_TIER_BY_ROSTER);
        check(StructureBurialTiers.floorDepthFor(3, desert) == 3,
            "B2 沙漠档在 ceiling=3 上被夹回 3（截断原值是 4 = 越界）⇒ 夹紧真的在挡东西");
        check(StructureBurialTiers.floorDepthFor(1, desert) == 1,
            "B2b 同一档在 ceiling=1 上也被夹（截断原值 2）⇒ 最浅的验证档同样越不出去");
        boolean ceilOk = true;
        boolean colossusCeilOk = true;
        boolean depthOk = true;
        int renders = 0;
        for (final Spec s : specs) {
            // 另三族的验证档由本片的 ceilingFor 实算 ⇒ 必须同时满足"埋掉不到一半"与"露出比 ≥ 闸值"；
            // 巨构族的验证档是它自己的静态契约给的（那条闸值是"至少还剩六分之一质量"，比本片的闸更宽），
            // 故那一族只校"埋掉不到一半 + 露出比 ≥ 六分之一"，不得拿本族闸去判外族（那是改口径）。
            if (s.ceiling * 2 >= s.sizeY) {
                ceilOk = false;
            }
            if ("colossus".equals(s.family)) {
                if (s.exposedRatio(s.ceiling) * 6.0D < 1.0D) {
                    colossusCeilOk = false;
                }
            } else if (s.ceiling > 0 && s.exposedRatio(s.ceiling) * 100.0D
                < StructureBurialTiers.CEILING_MIN_EXPOSED_PCT) {
                ceilOk = false;
            }
            for (final int roster : ROSTER_PROBES) {
                for (int az = 0; az < ANCHORS; az++) {
                    for (int ax = 0; ax < ANCHORS; ax++) {
                        final int d = depthFor(s, ax, az, SEED, roster);
                        renders++;
                        if (d < 0 || d > s.ceiling) {
                            depthOk = false;
                        }
                    }
                }
            }
        }
        check(ceilOk, "B3 另三族逐盘自证：每张盘的 ceiling 都满足 ceiling < sizeY/2 且埋到该档后露出比 ≥ "
            + StructureBurialTiers.CEILING_MIN_EXPOSED_PCT + "%（这是「档表越不出去」的前提那一半）");
        check(colossusCeilOk,
            "B3b 巨构族逐盘自证：maxBury 档的实心格露出比 ≥ 1/6（该族静态契约自己的闸值，不用本片的 34%，"
                + "免得把外族判据改成迎合本片）");
        check(depthOk, "B4 四族 × " + specs.size() + " 张盘 × " + ROSTER_PROBES.length + " 个名册下标 × " + ANCHORS
            + "² 锚点 = " + renders + " 次生产埋深取数，全部落在 [0, 本盘验证档] 之内");
        for (final Colossus c : RuinedColossusShapes.ALL) {
            final int validated = RuinedColossusShapes.contractValidatedThrough(c);
            check(validated >= c.maxBury && c.maxBury >= 1 && c.maxBury < c.sizeY() / 2,
                "B5 " + c.name + "：契约验证档 " + validated + " ≥ 档表可达档 maxBury=" + c.maxBury
                    + "（且 maxBury 自身在 1..sizeY/2-1 内）⇒ 任何群系档都把埋深留在契约判定域内");
            check(validated >= c.maxBury,
                "B5b " + c.name + "：契约还能接受到第 " + validated + " 档 ⇒ 本族窗口上界取 maxBury=" + c.maxBury
                    + " 是「留了 " + (validated - c.maxBury) + " 档余量」的保守选择，档表从不外扩到那几档"
                    + "（外扩的后果是 clinit 抛，没有任何判据会替它红）");
        }
        String blocked = null;
        for (final RuinTemplate r : RuinShapes.ALL) {
            if (RuinPlacer.buryCeilingOf(r) < (r.sizeY - 1) / 2) {
                blocked = r.name + "(" + RuinPlacer.buryCeilingOf(r) + " < 几何界 " + (r.sizeY - 1) / 2 + ")";
            }
        }
        check(blocked != null,
            "B6 存在「被露出比闸挡掉更深档」的盘：" + blocked + " ⇒ CEILING_MIN_EXPOSED_PCT 是活闸，不是摆设");
        System.out.println("# B 夹紧/clinit 自证 verdict=" + verdict());
    }

    // ══════════════ C 组：沙漠埋深分布与露出比（逐盘 + 逐族） ══════════════

    private static void groupTier(List<Spec> specs) {
        final int desert = argMax(StructureBurialTiers.DEPTH_TIER_BY_ROSTER);
        final Map<String, Agg> agg = new LinkedHashMap<>();
        boolean desertAlwaysDeepest = true;
        boolean desertNeverZeroDepth = true;
        boolean othersHaveZeroDepth = true;
        boolean orderMonotone = true;
        int live = 0;
        for (final Spec s : specs) {
            System.out.println(tierRow(s));
            if (s.ceiling < 1) {
                continue;
            }
            live++;
            final Agg d = sample(s, desert);
            final Agg steppe = sample(s, 0);
            final Agg forest = sample(s, 1);
            final Agg swamp = sample(s, 3);
            final Agg none = sample(s, NO_ID);
            if (d.meanDepth < s.ceiling - 1.0E-9) {
                desertAlwaysDeepest = false;
            }
            if (d.zeroShare > 0.0D) {
                desertNeverZeroDepth = false;
            }
            if (swamp.zeroShare <= 0.0D) {
                othersHaveZeroDepth = false;
            }
            if (!(d.meanDepth >= steppe.meanDepth && steppe.meanDepth >= forest.meanDepth
                && forest.meanDepth >= swamp.meanDepth)) {
                orderMonotone = false;
            }
            put(agg, s.family, desert, d);
            put(agg, s.family, 0, steppe);
            put(agg, s.family, 1, forest);
            put(agg, s.family, 3, swamp);
            put(agg, s.family, NO_ID, none);
        }
        check(desertAlwaysDeepest,
            "C1 每张可埋盘（" + live + "/" + specs.size() + " 张，ceiling ≥ 1）上沙漠档的实测埋深均值恰为该盘验证档");
        check(desertNeverZeroDepth, "C2 沙漠档实测「零埋深」（整座骑在地表上）占比 = 0");
        check(othersHaveZeroDepth,
            "C2b 最浅的非沙漠档仍能掷到零埋深（占比 > 0）⇒ C2 不是「到处都埋」造成的恒真，差异真是分流出来的");
        check(orderMonotone, "C3 逐盘埋深均值按档表序单调不减（沙漠 ≥ 草原 ≥ 森林 ≥ 沼泽），无一次翻面");
        final StringBuilder row = new StringBuilder();
        boolean exposedOk = true;
        boolean nearestOk = true;
        boolean depthOk = true;
        for (final String family : FAMILIES) {
            final Agg d = agg.get(family + "@" + desert);
            if (d == null) {
                continue;
            }
            final int boards = Math.max(1, d.n);
            final double desertDepth = d.meanDepth / boards;
            final double desertExposed = d.meanExposed / boards;
            double worstExposed = 0.0D;
            double nearestExposed = Double.MAX_VALUE;
            double bestDepth = 0.0D;
            for (final int other : new int[] { 0, 1, 3, NO_ID }) {
                final Agg a = agg.get(family + "@" + other);
                if (a != null) {
                    final double e = a.meanExposed / Math.max(1, a.n);
                    worstExposed = Math.max(worstExposed, e);
                    nearestExposed = Math.min(nearestExposed, e);
                    bestDepth = Math.max(bestDepth, a.meanDepth / Math.max(1, a.n));
                }
            }
            exposedOk &= desertExposed <= worstExposed * DESERT_EXPOSED_RATIO_MAX;
            // 更强的那条：与"其他三族里埋得最深的那一档"比也要显著低 —— 只跟最浅档比是软判据。
            nearestOk &= desertExposed <= nearestExposed * DESERT_VS_NEAREST_MAX;
            depthOk &= bestDepth <= 1.0E-9D || desertDepth >= bestDepth * DESERT_DEPTH_MEAN_MIN;
            row.append(" [").append(family).append(" 盘数=").append(boards).append("] 沙漠埋深均值=")
                .append(fmt(desertDepth, 3)).append(" 露出比=").append(pct(desertExposed))
                .append(" vs 其他档最浅露出比=").append(pct(worstExposed)).append(" 比值=")
                .append(fmt(desertExposed / Math.max(1.0E-9D, worstExposed), 3))
                .append(" vs 其他档最深露出比=").append(pct(nearestExposed)).append(" 最近比值=")
                .append(fmt(desertExposed / Math.max(1.0E-9D, nearestExposed), 3)).append(" 埋深倍数=")
                .append(bestDepth <= 1.0E-9D ? "n/a(其他档全不埋)" : fmt(desertDepth / bestDepth, 2)).append(';');
        }
        System.out.println("AGG 沙漠档 vs 其他档（逐族汇总）:" + row);
        check(exposedOk, "C4 四族逐族实测：沙漠档实心格露出比 ≤ 其他档最浅者的 "
            + (int) (DESERT_EXPOSED_RATIO_MAX * 100) + "%（这条比值就是「显著」而不是「稍微」的量化线）：" + row);
        check(depthOk, "C5 四族逐族实测：沙漠档埋深均值 ≥ 其他档最大值的 " + DESERT_DEPTH_MEAN_MIN + " 倍");
        check(nearestOk, "C6 反「只跟最浅档比」的软判据：沙漠档露出比 ≤ <b>其他三档里埋得最深那一档</b>的 "
            + (int) (DESERT_VS_NEAREST_MAX * 100) + "%（四族逐族实测，见上一行「最近比值」列）");
        System.out.println("# C verdict=" + verdict());
    }

    /** 逐盘读数行：五档的窗口 [floor..top]、埋深均值、露出比、零埋深占比。 */
    private static String tierRow(Spec s) {
        final StringBuilder one = new StringBuilder("  TIER " + pad(s.family, 8) + ' ' + pad(s.name, 30) + " sizeY="
            + s.sizeY + " 验证档=" + s.ceiling + " ｜");
        for (final int roster : new int[] { NO_ID, 0, 1, 2, 3 }) {
            final Agg a = sample(s, roster);
            one.append(roster == NO_ID ? " 无身份[" : " 档" + roster + '[').append(a.floor).append("..").append(a.top)
                .append("] 均值=").append(fmt(a.meanDepth, 2)).append(" 露出=").append(pct(a.meanExposed))
                .append(" 零埋=").append(pct(a.zeroShare)).append(';');
        }
        return one.toString();
    }

    // ═══════════ D 组：生产放置链的世界读数（P16 三口径 + 改前基线） ═══════════

    private static void groupWorld(List<Spec> specs) {
        final CityVariants.GroundFn ground = PlacementGate.groundFn(SEED);
        final int desert = argMax(StructureBurialTiers.DEPTH_TIER_BY_ROSTER);
        final Map<String, long[]> world = new LinkedHashMap<>();
        final Map<String, long[]> baseline = new HashMap<>();
        for (final Spec s : specs) {
            for (final int roster : new int[] { NO_ID, 0, 1, 2, 3 }) {
                long[] acc = world.get(s.family + "@" + roster);
                if (acc == null) {
                    world.put(s.family + "@" + roster, acc = new long[4]);
                }
                for (int a = 0; a < WORLD_ANCHORS.length; a += 2) {
                    final int ax = WORLD_ANCHORS[a];
                    final int az = WORLD_ANCHORS[a + 1];
                    final Vox vox = render(s, ax, az, SEED, roster, ground);
                    acc[0]++;
                    acc[1] += vox.floating(ground);
                    acc[2] += vox.belowGround(ground);
                    acc[3] += vox.solids();
                    if (roster == NO_ID) {
                        long[] b = baseline.get(s.family);
                        if (b == null) {
                            baseline.put(s.family, b = new long[3]);
                        }
                        final Vox base = renderWithBury(s, ax, az, SEED, ground, 0);
                        b[0]++;
                        b[1] += base.solids();
                        b[2] += base.floating(ground);
                    }
                }
            }
        }
        long renders = 0;
        long carveTotal = 0;
        for (final long[] v : world.values()) {
            renders += v[0];
            carveTotal += v[2];
        }
        check(carveTotal == 0, "D1 世界读数：四族 × 五档 × " + renders
            + " 次生产渲染，写进地形（地表高度场以内的格，含空气清空位）= 0"
            + " ⇒ 埋深不靠挖洞实现（G8③ 那条「不穿出空洞」的入口照旧封死）");
        // 悬浮：巨构族必须恰为 0（本族 wreck 有 settle 的逐列连续性质，与 RuinFamilyCheck M3② 同一口径）；
        // 另三族的字符盘本来就有同列空洞（改造前即如此，不属本片引入），故判据写成"半埋不新增悬浮"。
        boolean colossusClean = true;
        for (final Map.Entry<String, long[]> e : world.entrySet()) {
            if (e.getKey().startsWith("colossus") && e.getValue()[1] != 0) {
                colossusClean = false;
            }
        }
        check(colossusClean, "D2 巨构族世界读数：五档 × 全部锚点，悬浮块 = 0（半埋从底部切层，settle 的"
            + "逐列连续性质不受影响；与 RuinFamilyCheck M3② 同一口径）");
        boolean floatNotWorse = true;
        final StringBuilder frow = new StringBuilder();
        for (final String family : FAMILIES) {
            final long[] b = baseline.get(family);
            if (b == null) {
                continue;
            }
            frow.append(' ').append(family).append(" 基线悬浮=").append(b[2]).append(" 各档悬浮=");
            for (final int roster : new int[] { NO_ID, 0, 1, 2, 3 }) {
                final long[] v = world.get(family + "@" + roster);
                if (v == null) {
                    continue;
                }
                frow.append(roster).append(':').append(v[1]).append(' ');
                if (v[1] > b[2]) {
                    floatNotWorse = false;
                }
            }
        }
        check(floatNotWorse, "D2b 半埋不新增悬浮：四族每族的各档悬浮块数都 ≤ 同锚点 burial=0 基线。实测" + frow);
        boolean landedAll = true;
        for (final Map.Entry<String, long[]> e : world.entrySet()) {
            if (e.getValue()[3] <= 0) {
                landedAll = false;
            }
        }
        check(landedAll, "D3 每一次渲染都真落了块（沙漠档恒取最深验证档也没把任何一座埋成鬼影）");
        boolean shrinkOk = true;
        final StringBuilder row = new StringBuilder();
        for (final String family : FAMILIES) {
            final long[] b = baseline.get(family);
            final long[] d = world.get(family + "@" + desert);
            if (b == null || d == null || b[1] == 0) {
                continue;
            }
            final double ratio = (double) d[3] / (double) b[1];
            shrinkOk &= ratio <= DESERT_WORLD_BLOCKS_MAX;
            row.append(' ').append(family).append('=').append(pct(ratio)).append('(').append(d[3]).append('/')
                .append(b[1]).append(')');
        }
        check(shrinkOk, "D4 沙漠档落块 ≤ 改前基线（同 seed 同锚点 burial=0）的 "
            + (int) (DESERT_WORLD_BLOCKS_MAX * 100) + "%，四族实测比值:" + row
            + " ⇒ 差异落在世界里，不只是字符盘算式里");
        System.out.println("WORLD 渲染组=" + renders + " 写进地形=" + carveTotal);
        for (final Map.Entry<String, long[]> e : world.entrySet()) {
            System.out.println("  WORLD " + pad(e.getKey(), 18) + " 渲染=" + e.getValue()[0] + " 落块="
                + e.getValue()[3] + " 每座均块=" + fmt((double) e.getValue()[3] / Math.max(1L, e.getValue()[0]), 1)
                + " 悬浮=" + e.getValue()[1] + " 覆地=" + e.getValue()[2]);
        }
        for (final Map.Entry<String, long[]> e : baseline.entrySet()) {
            System.out.println("  BASELINE burial=0 " + pad(e.getKey(), 10) + " 渲染=" + e.getValue()[0] + " 落块="
                + e.getValue()[1] + " 悬浮=" + e.getValue()[2]);
        }
        System.out.println("# D verdict=" + verdict());
    }

    // ════════════════════════ E 组：源级红线（同源与坐标口径） ════════════════════════

    private static void groupSource(String srcRoot) {
        final String tiers = read(srcRoot, RUIN_PKG + "StructureBurialTiers.java");
        final String stripped = stripComments(tiers);
        check(!tiers.isEmpty(), "E0 能读到档表源文件（读不到 = 判据自己变瞎）");
        check(!stripped.contains("net.minecraft") && !stripped.contains("Blocks.") && !stripped.contains("getBlock")
            && !stripped.contains("World") && !stripped.contains("BiomeGenBase"),
            "E1 档表零方块/零 World 读取：身份只经 GTSRBiomeAuthority.ordinalAt 那一条纯函数出口"
                + "（与 S-A 高度红线同一证法）");
        check(!stripped.contains("rosterIndex ==") && !stripped.contains("ordinal ==")
            && !stripped.contains("BiomeId.") && !stripped.contains("BRASS_WASTES"),
            "E2 档表里没有身份等值判断 ⇒ 抑制只能写成「该群系档 = 0」，不留第二真值源（plan §2 第 8 条）");
        final List<String> holders = grepFiles(srcRoot, "DEPTH_TIER_BY_ROSTER");
        check(holders.size() == 1 && holders.get(0).endsWith("StructureBurialTiers.java"),
            "E3 埋深档表全仓只声明一处（实测持有者 " + holders.size() + " 个）：" + holders);
        final String[] families = {
            "com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedColossusShapes.java",
            "com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java",
            "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/RuinPlacer.java",
            "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java" };
        final StringBuilder missing = new StringBuilder();
        for (final String f : families) {
            if (!read(srcRoot, f).contains("StructureBurialTiers")) {
                missing.append(' ').append(f);
            }
        }
        check(missing.length() == 0,
            "E4 四族同源：colossus/ruin/machine/outpost 四个放置文件都经同一个 StructureBurialTiers 取档，缺者:"
                + (missing.length() == 0 ? "无" : missing));
        final String colossus = stripComments(
            read(srcRoot, "com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedColossusShapes.java"));
        check(!colossus.contains("c.maxBury + 1"),
            "E5 巨构文件里「模板常量当分母」那一式已移走（去注释后不再有 roll(..., c.maxBury + 1)）"
                + " ⇒ 埋深系数已改为档表，maxBury 只剩「验证档上界」这一个角色");
        check(colossus.contains("StructureBurialTiers.depthAt"),
            "E5b 巨构的埋深改由档表出口给出（同一实现体，不是抄一份算式）");
        check(read(srcRoot, RUIN_PKG + "RuinDamageOps.java")
            .contains("static void halfBury(char[][][] g, int sizeX, int sizeY, int sizeZ, long salt)"),
            "E6 废墟族「烘进字符盘」那半条链的签名与实现体未动 ⇒ 派生模板字节一字未改，逐名模板 SHA 不必重钉");
        final List<String> users = grepFiles(srcRoot, "StructureBurialTiers\\.");
        check(!users.contains("com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java")
            && !users.contains("com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityDecorPlacer.java"),
            "E7 编排器与装饰层零引用 ⇒ 本片不需要在 WorldGenerator 层传身份（兄弟片 S-B2 的文件一条未碰）。"
                + "实测引用者 " + users.size() + " 个文件：" + users);
        final String machinePlacer = stripComments(
            read(srcRoot, "com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java"));
        check(machinePlacer.contains("rosterIndexAtOrigin(originX, originZ)")
            && !machinePlacer.contains("rosterIndexAtOrigin((cx"),
            "E8 机器族身份一律按「锚点原点」反查（邻槽补片带的就是这两个值；按 chunk 号反查会造鬼影埋深）");
        check(stripped.contains("ordinalAt(originBlockX, originBlockZ)") && !stripped.contains("<< 4) + 8"),
            "E9 反查实现体的入参就是原点块坐标本身，没有 chunk 中心采样式（(cx<<4)+8 是权重档口径，不是形态口径）");
        check(!read(srcRoot, "com/miaokatze/gtsr/common/dimension/framework/SurfaceGate.java")
            .contains("StructureBurialTiers"), "E10 framework/SurfaceGate.java 禁碰面自证：零引用");
        System.out.println("# E verdict=" + verdict());
    }

    // ════════════════════════════ F 组：确定性 ════════════════════════════

    private static void groupDeterminism(List<Spec> specs) {
        final String a1 = readings(specs, SEED);
        final String a2 = readings(specs, SEED);
        check(a1.equals(a2), "F1 同 seed 双跑：全部读数表逐位一致 SHA=" + head(sha256(a1)));
        final String b = readings(specs, SEED_B);
        check(!a1.equals(b), "F2 跨 seed 必变（反「档表实为常数」的恒真假绿）SHA=" + head(sha256(b)));
        final int distinct = distinctTierSeparation(specs);
        check(distinct > 1,
            "F3 沙漠档与默认档的埋深和逐盘确有差异（不是「整表一个数」）：可分盘的沙漠/默认档埋深和互异数 = " + distinct);
        System.out.println("DET double-run SHA=" + sha256(a1) + " equal=" + a1.equals(a2));
    }

    /** 有多少张盘的「沙漠档埋深和」与「默认档埋深和」不同（反恒真的分维度）。 */
    private static int distinctTierSeparation(List<Spec> specs) {
        final Set<Integer> pairs = new HashSet<>();
        final int desert = argMax(StructureBurialTiers.DEPTH_TIER_BY_ROSTER);
        int diff = 0;
        for (final Spec s : specs) {
            final int a = sumDepth(s, SEED, desert);
            final int b = sumDepth(s, SEED, NO_ID);
            pairs.add(Integer.valueOf(a * 1000 + b));
            if (a != b) {
                diff++;
            }
        }
        return diff > 0 ? pairs.size() : 1;
    }

    private static int sumDepth(Spec s, long seed, int roster) {
        int sum = 0;
        for (int az = 0; az < 8; az++) {
            for (int ax = 0; ax < 8; ax++) {
                sum += depthFor(s, ax, az, seed, roster);
            }
        }
        return sum;
    }

    private static String readings(List<Spec> specs, long seed) {
        final StringBuilder sb = new StringBuilder();
        for (final Spec s : specs) {
            for (final int roster : ROSTER_PROBES) {
                int sum = 0;
                int xor = 0;
                for (int az = 0; az < 8; az++) {
                    for (int ax = 0; ax < 8; ax++) {
                        final int d = depthFor(s, ax, az, seed, roster);
                        sum += d;
                        xor ^= d;
                    }
                }
                sb.append(s.name).append('|').append(roster).append('|').append(sum).append('|').append(xor)
                    .append('\n');
            }
        }
        return sb.toString();
    }

    // ════════════════════════════ 采样与渲染 ════════════════════════════

    /** 一张可埋字符盘 + 它的验证档 + 逐层实心计数（露出比分母）。 */
    private static final class Spec {

        final String family;
        final String name;
        final int ceiling;
        final int sizeY;
        final int[] layerSolid;
        final int solid;
        /** 生产入口要的那个对象（Wreck / Shape / Outpost / RuinTemplate）。 */
        final Object source;
        /** 巨构族专用：所属母体（{@code morphAt} 的入参）；另三族为 null。 */
        final Colossus owner;

        Spec(String family, String name, int ceiling, Object source, Colossus owner) {
            this.family = family;
            this.name = name;
            this.ceiling = ceiling;
            this.source = source;
            this.owner = owner;
            this.layerSolid = layers(family, source);
            int total = 0;
            for (final int v : this.layerSolid) {
                total += v;
            }
            this.sizeY = this.layerSolid.length;
            this.solid = Math.max(1, total);
        }

        /** 埋 {@code depth} 层后还露在外面的实心格比例（设计露出比，未含运行期缺失掷骰）。 */
        double exposedRatio(int depth) {
            int above = 0;
            for (int y = Math.max(0, depth); y < sizeY; y++) {
                above += layerSolid[y];
            }
            return above * 1.0D / solid;
        }

        private static int[] layers(String family, Object src) {
            if ("colossus".equals(family)) {
                return ((Wreck) src).layerSolid.clone();
            }
            final int sx = dim(family, src, 0);
            final int sy = dim(family, src, 1);
            final int sz = dim(family, src, 2);
            final int[] out = new int[sy];
            for (int y = 0; y < sy; y++) {
                int n = 0;
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        if (ChunkSpans.isSolid(charAt(family, src, y, x, z))) {
                            n++;
                        }
                    }
                }
                out[y] = n;
            }
            return out;
        }
    }

    private static int dim(String family, Object src, int which) {
        if ("machine".equals(family)) {
            final RuinedMachineShapes.Shape s = (RuinedMachineShapes.Shape) src;
            return which == 0 ? s.sizeX : which == 1 ? s.sizeY : s.sizeZ;
        }
        if ("outpost".equals(family)) {
            final Outpost o = (Outpost) src;
            return which == 0 ? o.sizeX : which == 1 ? o.sizeY : o.sizeZ;
        }
        final RuinTemplate r = (RuinTemplate) src;
        return which == 0 ? r.sizeX : which == 1 ? r.sizeY : r.sizeZ;
    }

    private static char charAt(String family, Object src, int y, int x, int z) {
        if ("machine".equals(family)) {
            return ((RuinedMachineShapes.Shape) src).charAt(y, x, z);
        }
        if ("outpost".equals(family)) {
            return ((Outpost) src).charAt(y, x, z);
        }
        return ((RuinTemplate) src).charAt(y, x, z);
    }

    private static List<Spec> specs() {
        final List<Spec> out = new ArrayList<>();
        for (final Colossus c : RuinedColossusShapes.ALL) {
            for (final Wreck w : c.wrecks) {
                out.add(new Spec("colossus", w.label, c.maxBury, w, c));
            }
        }
        for (final RuinedMachineShapes.Shape s : RuinedMachineShapes.ALL) {
            out.add(new Spec("machine", s.name, RuinedMachinePlacer.buryCeilingOf(s), s, null));
        }
        for (final Outpost o : ProsperityOutpostPlacer.ALL) {
            out.add(new Spec("outpost", o.name, ProsperityOutpostPlacer.buryCeilingOf(o), o, null));
        }
        for (final RuinTemplate r : RuinShapes.ALL) {
            out.add(new Spec("ruin", r.name, RuinPlacer.buryCeilingOf(r), r, null));
        }
        return out;
    }

    /** 一次埋深取数：一律走各族的<b>生产</b>出口（判据不自算第二份窗口算式）。 */
    private static int depthFor(Spec s, int ax, int az, long seed, int roster) {
        if ("colossus".equals(s.family)) {
            return RuinedColossusShapes.morphAt(seed, ax, az, s.owner, roster).bury;
        }
        if ("machine".equals(s.family)) {
            return RuinedMachinePlacer.buryingAt((RuinedMachineShapes.Shape) s.source, ax, az, roster);
        }
        if ("outpost".equals(s.family)) {
            return ProsperityOutpostPlacer.buryingAt((Outpost) s.source, ax, az, roster);
        }
        return RuinPlacer.buryingAt((RuinTemplate) s.source, ax, az, roster);
    }

    private static Vox render(Spec s, int ax, int az, long seed, int roster, CityVariants.GroundFn ground) {
        return renderWithBury(s, ax, az, seed, ground, depthFor(s, ax, az, seed, roster));
    }

    /** 按给定埋深经生产链落进体素集（四族各自的 public 放置入口，实现体与 populate 同一条）。 */
    private static Vox renderWithBury(Spec s, int ax, int az, long seed, CityVariants.GroundFn ground, int burial) {
        final Vox vox = new Vox();
        final BlockSink rec = (x, y, z, block, meta, flags) -> {
            vox.put(x, y, z, block);
            return true;
        };
        final StructureBuilder builder = new StructureBuilder(rec);
        final int rnd = (int) (seed ^ (ax * 31L + az * 7L));
        if ("colossus".equals(s.family)) {
            final Wreck w = (Wreck) s.source;
            final Colossus c = s.owner;
            final Morph morph = new Morph(c, w, burial);
            for (int cx = ChunkSpans.chunkOf(ax); cx <= ChunkSpans.chunkOf(ax + c.sizeX() - 1); cx++) {
                for (int cz = ChunkSpans.chunkOf(az); cz <= ChunkSpans.chunkOf(az + c.sizeZ() - 1); cz++) {
                    RuinedMachinePlacer.placeSlice(
                        builder,
                        new Random(rnd + cx * 977L + cz),
                        c.shape,
                        ax,
                        az,
                        cx,
                        cz,
                        ground,
                        BlockSink.FLAG_POPULATE,
                        morph);
                }
            }
            return vox;
        }
        if ("machine".equals(s.family)) {
            final RuinedMachineShapes.Shape shape = (RuinedMachineShapes.Shape) s.source;
            for (int cx = ChunkSpans.chunkOf(ax); cx <= ChunkSpans.chunkOf(ax + shape.sizeX - 1); cx++) {
                for (int cz = ChunkSpans.chunkOf(az); cz <= ChunkSpans.chunkOf(az + shape.sizeZ - 1); cz++) {
                    RuinedMachinePlacer.placeSlice(
                        builder,
                        new Random(rnd + cx * 53L + cz),
                        shape,
                        ax,
                        az,
                        cx,
                        cz,
                        ground,
                        BlockSink.FLAG_POPULATE,
                        null,
                        Math.max(0, burial));
                }
            }
            return vox;
        }
        if ("outpost".equals(s.family)) {
            ProsperityOutpostPlacer.place(
                builder,
                (Outpost) s.source,
                ax,
                az,
                Math.floorMod(rnd, 4),
                25,
                new Random(rnd ^ 0x5EEDL),
                ground,
                BlockSink.FLAG_POPULATE,
                Math.max(0, burial));
            return vox;
        }
        RuinPlacer.place(
            builder,
            (RuinTemplate) s.source,
            ax,
            az,
            Math.floorMod(rnd, 4),
            25,
            new Random(rnd ^ 0x5EEDL),
            ground,
            BlockSink.FLAG_POPULATE,
            Math.max(0, burial));
        return vox;
    }

    /** 落块体素集（只数被 sink 接受的非空气写入；口径同 {@code RuinFamilyCheck} 的 VoxelSet）。 */
    private static final class Vox {

        private final Map<Long, Boolean> at = new HashMap<>();
        private final List<long[]> list = new ArrayList<>();

        private static long key(int x, int y, int z) {
            return (x + 1048576L) << 42 | (y + 1048576L) << 21 | (z + 1048576L);
        }

        void put(int x, int y, int z, Object block) {
            at.put(key(x, y, z), Boolean.valueOf(!PlacementGate.isAirHandle(block)));
            list.add(new long[] { x, y, z });
        }

        boolean solidAt(int x, int y, int z) {
            final Boolean v = at.get(key(x, y, z));
            return v != null && v.booleanValue();
        }

        int solids() {
            int n = 0;
            for (final Boolean v : at.values()) {
                if (v.booleanValue()) {
                    n++;
                }
            }
            return n;
        }

        /** 悬浮：下方既不是该列地表顶、也不是本次落块。 */
        int floating(CityVariants.GroundFn ground) {
            int n = 0;
            for (final long[] p : list) {
                final int x = (int) p[0];
                final int y = (int) p[1];
                final int z = (int) p[2];
                if (!solidAt(x, y, z)) {
                    continue;
                }
                if (y - 1 > ground.groundY(x, z) && !solidAt(x, y - 1, z)) {
                    n++;
                }
            }
            return n;
        }

        /** 写进地形：任何一次写入（含空气清空位）落在该列地表顶或其以下。 */
        int belowGround(CityVariants.GroundFn ground) {
            int n = 0;
            for (final long[] p : list) {
                if (p[1] <= ground.groundY((int) p[0], (int) p[2])) {
                    n++;
                }
            }
            return n;
        }
    }

    // ════════════════════════════ 汇总小工具 ════════════════════════════

    private static final class Agg {

        double meanDepth;
        double meanExposed;
        double zeroShare;
        int floor;
        int top;
        int n;
    }

    private static Agg sample(Spec s, int roster) {
        final Agg a = new Agg();
        double depth = 0;
        double exposed = 0;
        int zero = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int az = 0; az < ANCHORS; az++) {
            for (int ax = 0; ax < ANCHORS; ax++) {
                final int d = depthFor(s, ax, az, SEED, roster);
                depth += d;
                exposed += s.exposedRatio(d);
                if (d == 0) {
                    zero++;
                }
                min = Math.min(min, d);
                max = Math.max(max, d);
            }
        }
        final int n = ANCHORS * ANCHORS;
        a.meanDepth = depth / n;
        a.meanExposed = exposed / n;
        a.zeroShare = zero / (double) n;
        a.floor = min;
        a.top = max;
        a.n = 1;
        return a;
    }

    /** 把一个盘的读数累加进"该族该档"的汇总（C4/C5 判的是族级均值，不是某一张盘）。 */
    private static void put(Map<String, Agg> map, String family, int roster, Agg a) {
        final String key = family + "@" + roster;
        Agg acc = map.get(key);
        if (acc == null) {
            map.put(key, acc = new Agg());
        }
        acc.meanDepth += a.meanDepth;
        acc.meanExposed += a.meanExposed;
        acc.zeroShare += a.zeroShare;
        acc.floor = Math.min(acc.floor == 0 && acc.n == 0 ? Integer.MAX_VALUE : acc.floor, a.floor);
        acc.top = Math.max(acc.top, a.top);
        acc.n++;
    }

    private static int dim78RosterSize() {
        int n = 0;
        for (final GTSRBiomeAuthority.BiomeId id : GTSRBiomeAuthority.BiomeId.values()) {
            if (GTSRBiomeAuthority.DIM_KEY_PROSPERITY.equals(id.dimKey())) {
                n++;
            }
        }
        return n;
    }

    private static int argMax(double[] t) {
        int best = 0;
        for (int i = 1; i < t.length; i++) {
            if (t[i] > t[best]) {
                best = i;
            }
        }
        return best;
    }

    private static int countEquals(double[] t, double v) {
        int n = 0;
        for (final double d : t) {
            if (d == v) {
                n++;
            }
        }
        return n;
    }

    private static String verdict() {
        return failures == 0 ? "green" : "RED(" + failures + ")";
    }

    private static void check(boolean cond, String label) {
        assertions++;
        if (!cond) {
            failures++;
            System.out.println("FAIL: " + label);
        }
    }

    private static String fmt(double v, int digits) {
        return String.format("%." + digits + "f", Double.valueOf(v));
    }

    private static String pct(double v) {
        return fmt(v * 100.0D, 1) + '%';
    }

    private static String pad(String s, int n) {
        final StringBuilder b = new StringBuilder(s);
        while (b.length() < n) {
            b.append(' ');
        }
        return b.toString();
    }

    private static String head(String sha) {
        return sha.length() <= 16 ? sha : sha.substring(0, 16);
    }

    private static String read(String root, String relPath) {
        try {
            return new String(Files.readAllBytes(Paths.get(root, relPath.split("/"))), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> grepFiles(String root, String needle) {
        final List<String> out = new ArrayList<>();
        final Pattern pat = Pattern.compile(needle);
        try {
            final List<Path> all = new ArrayList<>();
            Files.walk(Paths.get(root)).filter(p -> p.toString().endsWith(".java")).forEach(all::add);
            for (final Path p : all) {
                final String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                if (pat.matcher(text).find()) {
                    out.add(p.toString().replace('\\', '/').substring(root.length() + 1));
                }
            }
        } catch (Exception e) {
            System.out.println("WARN: grepFiles 失败 " + e);
        }
        Collections.sort(out);
        return out;
    }

    /** 去注释（块注释 + 行注释），源级断言只看代码体。 */
    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
    }

    private static String sha256(String s) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder();
            for (final byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "sha-unavailable";
        }
    }
}
