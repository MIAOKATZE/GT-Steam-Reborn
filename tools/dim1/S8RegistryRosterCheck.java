import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSpans;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedColossusShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachineShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinTemplate;
import com.miaokatze.gtsr.common.dimension.shattered.WorldGenShatteredRuins;

/**
 * dim1 S8 总冒烟离线断言①（一次性自检 main，不进 jar，tools/ 惯例）：依次调用
 * {@link RuinedMachinePlacer#registerVariants()}（5 机型）+
 * {@link CityVariants#registerVariants()}（26 城变体）+
 * {@link ProsperityOutpostPlacer#registerVariants()}（6 城外中型废墟）+
 * {@link RuinPlacer#registerVariants()}（<b>P8：8 条城外废墟族破坏结构</b>）+
 * {@link WorldGenShatteredRuins#registerVariants()}（2 dim79 husk），
 * 断言 {@link StructureRegistry} 全名单 = <b>51</b> 名（28 城（26 基础 + <b>2 城内巨构
 * （P16-B3）</b>）+ 6 outpost + 5 机型 + 8 废墟 + <b>2 城外跨 chunk 巨构（P16-B1）</b> + 2 husk；
 * 总数仍由 {@code BASE_TOTAL_P0 + 各增员表} 派生，本工具不写 51 这个字面）
 * 且维度归属 PROSPERITY=49 / SHATTERED=2，并逐名打印。
 * <p>
 * <b>39 的口径来源（不是推定）</b>：用户实机日志 {@code plan/log.txt:11452} 打印
 * {@code machines=5 outposts=6 [37 个 dim78 名]}，加 2 名 dim79 husk = 39；本工具的
 * {@link #EXPECTED_NAMES} 与该日志同源（逐名相等面在 {@code RosterIntegrityCheck} 钉死）。
 * <p>
 * <b>P8 的名册增长（39 → 47）与 P16-B1 的再增长（47 → 49）都是"增员"，与 P0 的"资产未丢"判据是两件事</b>：
 * {@link #BASE_TOTAL_P0}
 * 仍然钉着 39 这个历史基线，并且本工具逐条断言"39 条旧名一条不少、一条不变"（旧名集合 ⊆ 新名集合，
 * 且分桶计数 city/outpost/machine/husk 全部不变）；P8 多出来的 8 条必须恰好等于 {@link RuinShapes#ALL}，
 * P16-B1 多出来的 {@code RuinedColossusShapes.ALL.length} 条必须恰好等于
 * {@link RuinedColossusShapes#ALL}（族标注仍是 {@code machine}，见下面的 colossus 专项断言）。
 * 只把 EXPECTED_TOTAL 改成新数而不钉这两面，名册少一条旧名 + 多一条无关名也能绿——那是假绿。
 * <p>
 * <b>classpath 口径（本行曾被本文件 javadoc 写错，2026-09-18 实测更正）</b>：
 * 纯 {@code build/classes/java/main} 跑不通——{@code WorldGenShatteredRuins implements
 * IWorldGenerator}，源文件模式下 <em>编译期</em> 即需 FML 接口类可解析，实测报
 * {@code 找不到cpw.mods.fml.common.IWorldGenerator的类文件}。只需 forge universal jar
 * （满足接口链接，不触发任何 MC 类初始化），<b>不需要</b> patchedMc/guava/log4j：
 * <pre>
 * FORGE_JAR=~/.gradle/caches/modules-2/files-2.1/net.minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/\
 * 25fd97f72beca728112256938e03e8105b1b78cc/forge-1.7.10-10.13.4.1614-1.7.10-universal.jar
 * MSYS2_ARG_CONV_EXCL='*' java -cp "build\classes\java\main;$FORGE_JAR" \
 *   tools/dim1/S8RegistryRosterCheck.java
 * </pre>
 * 与 {@code StructureViewerExport} 的既有配方同源（该工具 javadoc :25-31 已实测此坑）。
 */
public class S8RegistryRosterCheck {

    /** P0 基线名数（26 城 + 6 outpost + 5 机型 + 2 husk）；P8 增员前的历史值，永久保留作对照。 */
    static final int BASE_TOTAL_P0 = 39;

    /**
     * 期望总名数（P8 起 = P0 基线 39 + 废墟族 {@link RuinShapes#ALL} 条；
     * <b>P16-B1 再 + 城外跨 chunk 巨构 {@link RuinedColossusShapes#ALL} 条</b>；
     * <b>P16-B3 再 + 城内巨构（city 一路，名册边长判据派生，见 {@link #cityMegaCount()}）</b>——
     * 增长数一律由各张模板表派生，本工具不写第二份数字；总式形状（P0 基线 + 逐族增员相加）
     * 一字未动，P16-B3 只往末尾追加了 city 一路的加数。
     */
    static final int EXPECTED_TOTAL = BASE_TOTAL_P0 + RuinShapes.ALL.length
        + RuinedColossusShapes.ALL.length + cityMegaCount();

    /**
     * P0 基线的 39 名全名单（与实机日志 plan/log.txt:11452 同源；有序，便于 diff 定位）。
     * <b>P8 不改本表</b>——它是"旧名册一条不少"的对照面；增员名单是它加上 {@link RuinShapes#ALL}。
     */
    static final String[] EXPECTED_NAMES = { "boiler_frame", "boiler_house", "broken_bridge", "broken_pillars",
        "canal_gate", "chimney_base", "chimney_stack", "clock_tower", "cooling_tower", "crane_ruin", "dome_hall",
        "engine_room", "fallen_arch", "forge_hall", "fountain_basin", "gas_holder", "gear_mill_table", "gear_tower",
        "husk_small", "husk_tall", "machine_plinth", "manor_ruin", "market_colonnade", "outpost_brick_kiln",
        "outpost_broken_aqueduct", "outpost_collapsed_truss", "outpost_gear_slag_mound", "outpost_toppled_boiler",
        "outpost_watch_post", "pressure_tank_row", "pump_base", "pump_house", "rail_platform", "slag_heap",
        "steam_gallery", "tram_depot", "viaduct", "watch_tower", "water_tower" };

    /** 6 个城外中型废墟名（A5 批次新增进断言的名）。 */
    static final Set<String> EXPECTED_OUTPOSTS = new LinkedHashSet<>(
        Arrays.asList("outpost_watch_post", "outpost_broken_aqueduct", "outpost_toppled_boiler", "outpost_brick_kiln",
            "outpost_collapsed_truss", "outpost_gear_slag_mound"));

    /**
     * 8 个城外废墟族（破坏结构）名（<b>P8 批次</b>）。与 {@link RuinShapes#ALL} 同源派生，
     * 不手抄第二份名单——手抄就是"名册有两处真值"。
     */
    static final Set<String> EXPECTED_RUINS = ruinsFromSources();

    /**
     * P16-B1 的跨片巨构名（<b>新机型分桶</b>，族仍是 {@code machine}）。同样从形状表派生，不手抄名单。
     * 单列一桶的理由与 P8 给 ruin 单列一致：增员必须能指名道姓地对账，混进 city 桶就是假绿。
     */
    static final Set<String> EXPECTED_COLOSSI = colossiFromSources();

    /** P0 基线的城内变体数（巨构在此数上<b>加</b>，本路是 P16-B3 唯一允许动数的桶）。 */
    static final int CITY_P0_COUNT = 26;

    /**
     * P16-B3 的<b>城内巨构</b>名（申报边长 &gt;16 的城变体，形状表 = {@code CityMegaVariants}
     * 汇入的 {@link CityVariants#ALL} 尾部）。判据口径与 {@link #EXPECTED_COLOSSI} 同形——
     * 从名册按边长派生、不手抄名字；它们落在 city 桶里，所以 city 桶计数 =
     * {@code CITY_P0_COUNT + CITY_MEGA.size()}，②b 的"39 旧名一条不少"减项同步补这一路。
     */
    static final Set<String> CITY_MEGA = cityMegaFromSources();

    private static Set<String> cityMegaFromSources() {
        final Set<String> s = new LinkedHashSet<>();
        for (final CityVariants.Variant v : CityVariants.ALL) {
            if (Math.max(v.sizeX, v.sizeZ) > ChunkSpans.CHUNK_BLOCKS) {
                s.add(v.name);
            }
        }
        if (s.isEmpty() || s.size() > 2) {
            throw new IllegalStateException(
                "[GTSR] B3 验收口径要求城内巨构 1-2 个，名册边长判据现读出 " + s.size() + " 个: " + s);
        }
        return s;
    }

    /** 城内巨构数（名册边长判据现算；{@link #EXPECTED_TOTAL} 在 CITY_MEGA 字段初始化之前取数，故走方法不走字段）。 */
    static int cityMegaCount() {
        int n = 0;
        for (final CityVariants.Variant v : CityVariants.ALL) {
            if (Math.max(v.sizeX, v.sizeZ) > ChunkSpans.CHUNK_BLOCKS) {
                n++;
            }
        }
        return n;
    }

    private static Set<String> colossiFromSources() {
        final Set<String> s = new LinkedHashSet<>();
        for (final RuinedColossusShapes.Colossus c : RuinedColossusShapes.ALL) {
            s.add(c.name);
        }
        return s;
    }

    private static Set<String> ruinsFromSources() {
        final Set<String> s = new LinkedHashSet<>();
        for (final RuinTemplate t : RuinShapes.ALL) {
            s.add(t.name);
        }
        return s;
    }

    public static void main(String[] args) {
        RuinedMachinePlacer.registerVariants();
        CityVariants.registerVariants();
        ProsperityOutpostPlacer.registerVariants();
        RuinPlacer.registerVariants();
        WorldGenShatteredRuins.registerVariants();

        final List<String> names = StructureRegistry.names();
        int machines = 0;
        int cities = 0;
        int outposts = 0;
        int ruins = 0;
        int colossi = 0;
        int husks = 0;
        final List<String> machineNames = new ArrayList<>();
        final List<String> outpostNames = new ArrayList<>();
        final List<String> ruinNames = new ArrayList<>();
        final List<String> colossusNames = new ArrayList<>();
        final List<String> huskNames = new ArrayList<>();
        for (final StructureRegistry.Entry e : StructureRegistry.all()) {
            if (e.dimension == StructureRegistry.Dimension.SHATTERED) {
                husks++;
                huskNames.add(e.name);
            } else if (contains(RuinedMachineShapes.ALL, e.name)) {
                machines++;
                machineNames.add(e.name);
            } else if (EXPECTED_OUTPOSTS.contains(e.name)) {
                outposts++;
                outpostNames.add(e.name);
            } else if (EXPECTED_RUINS.contains(e.name)) {
                ruins++;
                ruinNames.add(e.name);
            } else if (EXPECTED_COLOSSI.contains(e.name)) {
                colossi++;
                colossusNames.add(e.name);
            } else {
                cities++;
            }
        }

        // ① 分组计数（旧断言把 outpost 混进 cities 桶且总数钉 33 ⇒ stale；P8 同理必须单列 ruin 桶；
        //    P16-B3 只动 city 一路：city = 26 基础 + CITY_MEGA 派生数，总式形状一字不动）
        final int expectedCity = CITY_P0_COUNT + CITY_MEGA.size();
        if (names.size() != EXPECTED_TOTAL || machines != 5 || cities != expectedCity || outposts != 6
            || ruins != RuinShapes.ALL.length || colossi != RuinedColossusShapes.ALL.length || husks != 2
            || CityVariants.ALL.length != expectedCity || RuinedMachineShapes.ALL.length != 5
            || ProsperityOutpostPlacer.ALL.length != 6) {
            System.out
                .println("ROSTER FAIL: total=" + names.size() + " machines=" + machines + " cities=" + cities
                    + " outposts=" + outposts + " ruins=" + ruins + " colossi=" + colossi + " husks=" + husks
                    + " expectedTotal=" + EXPECTED_TOTAL + " cityAll=" + CityVariants.ALL.length
                    + " machineAll=" + RuinedMachineShapes.ALL.length
                    + " outpostAll=" + ProsperityOutpostPlacer.ALL.length + " ruinAll=" + RuinShapes.ALL.length
                    + " colossusAll=" + RuinedColossusShapes.ALL.length);
            System.out.println("NAMES: " + names);
            System.exit(1);
        }
        // ② 名集合与"P0 基线 39 名 + 废墟族"期望全名单相等（不只数量相等；逐名 footprint + 模板 SHA 在 RosterIntegrityCheck）
        final Set<String> expected = new LinkedHashSet<>(Arrays.asList(EXPECTED_NAMES));
        expected.addAll(EXPECTED_RUINS);
        expected.addAll(EXPECTED_COLOSSI);
        expected.addAll(CITY_MEGA);
        final Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(new LinkedHashSet<>(names));
        final Set<String> extra = new TreeSet<>(names);
        extra.removeAll(expected);
        if (!missing.isEmpty() || !extra.isEmpty()) {
            System.out.println("ROSTER FAIL: name set mismatch missing=" + missing + " extra=" + extra);
            System.out.println("NAMES: " + names);
            System.exit(1);
        }
        // ②b P8 反假绿：P0 基线的 39 名必须<b>一条不少</b>地仍在册（名册增长不等于名册替换）。
        //     只钉"总数 == 期望值 + 集合与派生名单相等"的话，删掉一条旧名再加一条新名也是绿的。
        for (final String legacy : EXPECTED_NAMES) {
            if (!names.contains(legacy)) {
                System.out.println("ROSTER FAIL: P0 baseline name lost @ P8: " + legacy);
                System.exit(1);
            }
        }
        // 同一条"旧名一条不少"的判据在 P16-B1 后要减去<b>两</b>批增员（废墟 + 跨片巨构），
        // P16-B3 再减去<b>城内巨构</b>这一路（city 一路的加数，总式形状不动）；
        // 少减一项等于给"删一条旧名 + 加两条新名"留了蒙过总数对账的路（重复计数陷阱）。
        if (names.size() - ruins - colossi - CITY_MEGA.size() != BASE_TOTAL_P0) {
            System.out.println(
                "ROSTER FAIL: 非(废墟+跨片巨构+城内巨构)名数 " + (names.size() - ruins - colossi - CITY_MEGA.size())
                    + " != P0 基线 " + BASE_TOTAL_P0);
            System.exit(1);
        }
        // ③ 6 outpost 与 8 废墟必须逐个在册且归 PROSPERITY
        for (final String n : EXPECTED_OUTPOSTS) {
            final StructureRegistry.Entry e = StructureRegistry.get(n);
            if (e == null || e.dimension != StructureRegistry.Dimension.PROSPERITY) {
                System.out.println("ROSTER FAIL: outpost missing or mis-dimensioned: " + n);
                System.exit(1);
            }
        }
        for (final String n : EXPECTED_COLOSSI) {
            final StructureRegistry.Entry e = StructureRegistry.get(n);
            if (e == null || e.dimension != StructureRegistry.Dimension.PROSPERITY) {
                System.out.println("ROSTER FAIL: colossus missing or mis-dimensioned: " + n);
                System.exit(1);
            }
            // P16-B1 的互斥链申报钉在这里：巨构是<b>机器族的新机型</b>，不是第四环 ⇒ 族标注必须是
            // machine（哪天改成独立族，这条指名变红，逼着重新申报"插在哪一环"）。
            // footprint 登记的是<b>总 bbox</b>（>16 是申报不是钳制；成对断言在 OutpostTemplateCheck），
            // 所以这里反过来钉"至少有一边超出单 chunk"——超不出去就说明它根本没跨 chunk。
            if (!"machine".equals(e.family) || e.footprintX <= 16 && e.footprintZ <= 16) {
                System.out.println(
                    "ROSTER FAIL: colossus family/footprint drift: " + n + " family=" + e.family
                        + " footprint=" + e.footprintX + "x" + e.footprintZ);
                System.exit(1);
            }
            if (e.placementDenominator != 0 || e.windowRepeatCap != 0) {
                System.out.println("ROSTER FAIL: colossus 把数字搬进了 roster（应 0 = 跟随 Config）: " + n);
                System.exit(1);
            }
            if (e.allowsDamagedVariant) {
                System.out.println("ROSTER FAIL: colossus 不该 opt-in P8 损毁算子: " + n);
                System.exit(1);
            }
        }
        for (final String n : CITY_MEGA) {
            final StructureRegistry.Entry e = StructureRegistry.get(n);
            if (e == null || e.dimension != StructureRegistry.Dimension.PROSPERITY) {
                System.out.println("ROSTER FAIL: city mega missing or mis-dimensioned: " + n);
                System.exit(1);
            }
            // 城内巨构三面对账：footprint = 申报总 bbox（>16，"申报不是钳制"与 colossus 同语义，
            // 超不出去就说明它根本没跨 chunk）；族标注必须仍是 unscoped（城内地块走 CityPlanner
            // 选型链、<b>不进</b> PlacementGate 互斥三环——与 B4 密度链解耦，哪天漂进机器族桶即红）。
            if (e.footprintX <= ChunkSpans.CHUNK_BLOCKS && e.footprintZ <= ChunkSpans.CHUNK_BLOCKS) {
                System.out.println(
                    "ROSTER FAIL: city mega footprint 未超单 chunk（跨片申报名不副实）: " + n + " footprint="
                        + e.footprintX + "x" + e.footprintZ);
                System.exit(1);
            }
            if (!PlacementGate.FAMILY_UNSCOPED.equals(e.family)) {
                System.out.println("ROSTER FAIL: city mega 族标注漂移（城链不归 PlacementGate 管）: " + n + " family="
                    + e.family);
                System.exit(1);
            }
        }
        for (final String n : EXPECTED_RUINS) {
            final StructureRegistry.Entry e = StructureRegistry.get(n);
            if (e == null || e.dimension != StructureRegistry.Dimension.PROSPERITY) {
                System.out.println("ROSTER FAIL: ruin missing or mis-dimensioned: " + n);
                System.exit(1);
            }
            if (!"ruin".equals(e.family) || !e.allowsDamagedVariant) {
                System.out.println("ROSTER FAIL: ruin family/damage opt-in drift: " + n + " " + e.family
                    + " damaged=" + e.allowsDamagedVariant);
                System.exit(1);
            }
        }
        for (final String n : names) {
            if (!n.equals(n.trim()) || n.isEmpty()) {
                System.out.println("ROSTER FAIL: bad name token '" + n + "'");
                System.exit(1);
            }
        }
        System.out.println("ROSTER PASS: StructureRegistry names() = " + EXPECTED_TOTAL
            + " (5 machines + " + (CITY_P0_COUNT + CITY_MEGA.size()) + " city(含 " + CITY_MEGA.size()
            + " 城内巨构) + 6 outpost + " + RuinShapes.ALL.length + " ruin + "
            + RuinedColossusShapes.ALL.length + " colossus + 2 husk)");
        System.out.println("MACHINES(5): " + machineNames);
        System.out.println("OUTPOSTS(6): " + outpostNames);
        System.out.println("RUINS(" + RuinShapes.ALL.length + "): " + ruinNames);
        System.out.println("COLOSSI(" + RuinedColossusShapes.ALL.length + "): " + colossusNames);
        System.out.println("CITYMEGA(" + CITY_MEGA.size() + "): " + CITY_MEGA);
        System.out.println("HUSKS(2): " + huskNames);
        System.out.println("DIM78 registry names = " + (names.size() - husks) + " (machines=" + machines
            + " outposts=" + outposts + " ruins=" + ruins + " colossi=" + colossi + " cities=" + cities
            + ")，P0 基线的 39 名一条不少（plan/log.txt:11452 口径 + P8/P16-B1 增员）");
        System.out.println("NAMES(" + EXPECTED_TOTAL + "): " + names);
        System.out.println("ROSTER DONE");
    }

    private static boolean contains(RuinedMachineShapes.Shape[] shapes, String name) {
        for (final RuinedMachineShapes.Shape s : shapes) {
            if (s.name.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
