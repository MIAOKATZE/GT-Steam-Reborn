import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
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
 * 断言 {@link StructureRegistry} 全名单 = <b>47</b> 名（26 城 + 6 outpost + 5 机型 + 8 废墟 + 2 husk）
 * 且维度归属 PROSPERITY=45 / SHATTERED=2，并逐名打印。
 * <p>
 * <b>39 的口径来源（不是推定）</b>：用户实机日志 {@code plan/log.txt:11452} 打印
 * {@code machines=5 outposts=6 [37 个 dim78 名]}，加 2 名 dim79 husk = 39；本工具的
 * {@link #EXPECTED_NAMES} 与该日志同源（逐名相等面在 {@code RosterIntegrityCheck} 钉死）。
 * <p>
 * <b>P8 的名册增长（39 → 47）是"增员"，与 P0 的"资产未丢"判据是两件事</b>：{@link #BASE_TOTAL_P0}
 * 仍然钉着 39 这个历史基线，并且本工具逐条断言"39 条旧名一条不少、一条不变"（旧名集合 ⊆ 新名集合，
 * 且分桶计数 city/outpost/machine/husk 全部不变）；多出来的 8 条必须恰好等于 {@link RuinShapes#ALL}。
 * 只把 EXPECTED_TOTAL 改成 47 而不钉这两面，名册少一条旧名 + 多一条无关名也能绿——那是假绿。
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

    /** 期望总名数（P8 起 = P0 基线 39 + 废墟族 {@link RuinShapes#ALL} 条）。 */
    static final int EXPECTED_TOTAL = BASE_TOTAL_P0 + RuinShapes.ALL.length;

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
        int husks = 0;
        final List<String> machineNames = new ArrayList<>();
        final List<String> outpostNames = new ArrayList<>();
        final List<String> ruinNames = new ArrayList<>();
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
            } else {
                cities++;
            }
        }

        // ① 分组计数（旧断言把 outpost 混进 cities 桶且总数钉 33 ⇒ stale；P8 同理必须单列 ruin 桶）
        if (names.size() != EXPECTED_TOTAL || machines != 5 || cities != 26 || outposts != 6 || ruins != RuinShapes.ALL.length
            || husks != 2 || CityVariants.ALL.length != 26 || RuinedMachineShapes.ALL.length != 5
            || ProsperityOutpostPlacer.ALL.length != 6) {
            System.out
                .println("ROSTER FAIL: total=" + names.size() + " machines=" + machines + " cities=" + cities
                    + " outposts=" + outposts + " ruins=" + ruins + " husks=" + husks
                    + " expectedTotal=" + EXPECTED_TOTAL
                    + " cityAll=" + CityVariants.ALL.length + " machineAll=" + RuinedMachineShapes.ALL.length
                    + " outpostAll=" + ProsperityOutpostPlacer.ALL.length + " ruinAll=" + RuinShapes.ALL.length);
            System.out.println("NAMES: " + names);
            System.exit(1);
        }
        // ② 名集合与"P0 基线 39 名 + 废墟族"期望全名单相等（不只数量相等；逐名 footprint + 模板 SHA 在 RosterIntegrityCheck）
        final Set<String> expected = new LinkedHashSet<>(Arrays.asList(EXPECTED_NAMES));
        expected.addAll(EXPECTED_RUINS);
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
        //     只钉"总数 == 47 + 集合与派生名单相等"的话，删掉一条旧名再加一条新名也是绿的。
        for (final String legacy : EXPECTED_NAMES) {
            if (!names.contains(legacy)) {
                System.out.println("ROSTER FAIL: P0 baseline name lost @ P8: " + legacy);
                System.exit(1);
            }
        }
        if (names.size() - ruins != BASE_TOTAL_P0) {
            System.out.println("ROSTER FAIL: 非废墟名数 " + (names.size() - ruins) + " != P0 基线 " + BASE_TOTAL_P0);
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
            + " (5 machines + 26 city + 6 outpost + " + RuinShapes.ALL.length + " ruin + 2 husk)");
        System.out.println("MACHINES(5): " + machineNames);
        System.out.println("OUTPOSTS(6): " + outpostNames);
        System.out.println("RUINS(" + RuinShapes.ALL.length + "): " + ruinNames);
        System.out.println("HUSKS(2): " + huskNames);
        System.out.println("DIM78 registry names = " + (names.size() - husks) + " (machines=" + machines
            + " outposts=" + outposts + " ruins=" + ruins + " cities=" + cities
            + ")，P0 基线的 39 名一条不少（plan/log.txt:11452 口径 + P8 增员）");
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
