import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Random;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSpans;
import com.miaokatze.gtsr.common.dimension.framework.structure.RasterSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer.Outpost;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedColossusShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachineShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinTemplate;

/**
 * S9 3D 查看器导出驱动（一次性导出 main，不进 jar，tools/ 惯例）：把 dim78 城变体
 * {@link CityVariants#ALL}（含 P16-B3 的 2 个城内巨构）+ 城外中型废墟
 * {@link ProsperityOutpostPlacer#ALL} + 城内/dim78 残缺机器 {@link RuinedMachineShapes#ALL} +
 * dim78 城外废墟族 {@code RuinShapes.ALL} + <b>P16-C1 第六条腿</b>城外跨 chunk 巨构
 * {@link RuinedColossusShapes#ALL} +
 * dim79 破碎残骸 {@code WorldGenShatteredRuins.HUSK_SMALL/HUSK_TALL}（反射取形状——
 * HuskRasterPreview 同款单一权威，&lt;clinit&gt; 只构造纯 Java 的 HuskShape 不触发 MC 类初始化）
 * 导出为体素 JSON，落 {@code plan/维度计划/设计册与实施计划/review/dim1/data/structures-dim7879.json}
 * （+ 同内容的 {@code structures-dim7879.js} 包装 {@code var STRUCTURES_DATA=...}，
 * file:// 直开 index.html 时绕开 fetch CORS）。
 * <p>
 * <b>═══ P16-C1 计数纪律：本工具内不再有任何名册数字 ═══</b>
 * 本轮（P16）两次返工的同一个根因就是"工具里抄了一份名册计数常量"：{@code :247} 的
 * {@code != 26}、机型腿的 {@code != 5}、outpost 腿的 {@code != 6}、废墟腿的
 * {@code != RUIN_LABELS.length}——名册一扩项，这些常量要么把导出器打断（本次就是），
 * 要么更糟：静默少导几条。现在全部换成两条<b>派生</b>判据：
 * <ol>
 * <li>腿内：逐条循环计数必须等于该腿权威数组的长度（少一条就红，且红在导出器里）；</li>
 * <li>腿间：{@link #expectedVariants()} = 六条腿的权威数组长度之和（城/outpost/机型/废墟/巨构
 * 全部现算，husk 取反射字段名表长度），{@link #main} 拿它核对 {@link #emitted}；</li>
 * <li>中文名表（{@code *_LABELS}）降级为<b>纯装饰</b>：命中就译，未命中回退 id（旧行为），
 * 且新增 {@link #assertLabelRowsResolve()} 反向钉"标签表里不得有名册中不存在的 id"
 * ——即"名册扩项永不打断导出，标签写错名字必定打断"。</li>
 * </ol>
 * 名册真值本身仍不在这里：{@code asset_baseline_check.sh} 的 {@code [3/4]} 拿
 * {@code S8RegistryRosterCheck} 的 NAMES 与本数据件的 id 集合做双向对账（MISSING/EXTRA 判 FAIL），
 * 那一条才是"数据件 == 名册"的门；本工具的派生判据只保证"导出器自己不漏腿、不少条"。
 * <p>
 * <b>═══ P16-C1 跨 chunk 巨构的导出几何 ═══</b>
 * {@link RuinedColossusShapes} 的两条巨构申报总 bbox 是 24×12×16（跨 2×1 chunk）与
 * 20×12×20（跨 2×2 chunk），运行期由 {@link ChunkSpans} 切成"每个 populate chunk 只画自己那一份"。
 * 导出侧要的是<b>整座</b>（区5 因此能转出"一座横跨 2×2 的巨构"的真实形态，而不是锚点片），
 * 所以本腿按总 bbox 逐格读母体字符盘，并拿 {@link ChunkSpans} 自己算出的分片实心数之和
 * （{@code Colossus#solidChars()}，即生产那套几何的读数）回核对冲——
 * <b>导出侧不写第二份 clip/intersect</b>（两者不等即 fail fast，说明要么漏格要么多格）。
 * 每条目额外带一个 {@code spans} 对象（{@code chunksX/chunksZ/slices/sliceSolid/exportedSolid/
 * machineCells/solidCells}，全部取自 {@link ChunkSpans} 与 {@link RuinedColossusShapes} 的派生量），
 * 页内与 reviewer 因此能直接读到
 * "跨几个 chunk、几片、实心多少格、机械件占比"，不必再跑 {@code RuinedColossusShapes.describe()}。
 * 导出的形态是<b>母体蓝图</b>（= {@code /gtsr structure} 摆放的那份、也是 {@code RosterIntegrityCheck}
 * 逐名 SHA 钉的那份）；世界里真正落的是 P16-B2 由 {@code RuinedColossusShapes.morphAt} 选出的
 * 四档派生残骸 + 半埋深度，那属于运行期选型，与机型/废墟腿同一口径（{@code tier=0}、无 seed）。
 * <p>
 * <b>P7（plan §5 P7 / 任务包"开局基线"第 6 条：P0 把这条数据件缺口推给本片）</b>：改造前本工具只有
 * <b>三条腿</b>（city/outpost/husk = 34），名册是 39 名 ⇒ {@code asset_baseline_check.sh} 的
 * {@code STRUCT-JSON MISSING} 常驻列出缺的 5 个机型（{@code boiler_frame}/{@code chimney_base}/
 * {@code gear_mill_table}/{@code pump_base}/{@code steam_gallery}）。缺口的根因不是 JSON 少了几行，
 * 而是这里的 {@link #buildJson()} groups 数组与 {@link #main} 的 variants 计数<b>写死成三条腿</b>
 * ——所以修法是<b>加腿</b>并把计数换成派生量，不是手编产物（手编会让"数据件 == 生成器输出"这条不变量彻底失效）。
 * 机型腿与 husk 腿同一口径：<b>直接读形状串剪影</b>（{@link RuinedMachineShapes.Shape#charAt}），
 * 不走 {@code RuinedMachinePlacer.place}——那条路径要经 {@code resolveBlock} 取 {@code BlocksGTSR}
 * 的 Block 实例与 {@code Blocks.air}，会触发 MC 类初始化，正好破掉本工具"零 MC 执行依赖"的前提；
 * 因此机型条目的 {@code tier=0 / missingRate=0 / 无 seed}（与 husk 同），损伤掷骰属于运行期行为、
 * 不属于模板。巨构腿同理（读 {@link RuinedColossusShapes.Colossus#shape} 的字符盘）。
 * <p>
 * <b>纯 JEP 330 单文件运行，零 Minecraft 执行依赖</b>（城/outpost/machine/ruin/colossus 腿零 MC；husk 腿反射已编译类，
 * 类定义需 FML 接口在 classpath 才能链接——Java 26 实测无 forge jar 时 NoClassDefFoundError:
 * IWorldGenerator，HuskRasterPreview 的旧"无 jar 可跑"口径在当前 JDK 已失效，故运行命令补 forge
 * universal jar，仅满足接口链接、不触发任何 MC 类初始化）：
 * <pre>
 * FORGE_JAR=~/.gradle/caches/modules-2/files-2.1/net.minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/\
 * 25fd97f72beca728112256938e03e8105b1b78cc/forge-1.7.10-10.13.4.1614-1.7.10-universal.jar
 * java -cp "build/classes/java/main;$FORGE_JAR" tools/dim1/StructureViewerExport.java [outDir]
 * </pre>
 * <p>
 * 口径：
 * <ul>
 * <li>每变体 = id/中文名/group/类别/尺寸/损伤档/种子/方块矩阵；矩阵 layers[y][z] 行字符串，
 * 每格 = 记号族 char（'.' = 空），char → 颜色 RGB/语义名/meta 经调色板图例（palette）查表；</li>
 * <li>损伤档口径：城/outpost 取<b>默认档 1</b>（缺失率 MISSING_RATES[1]=20%），tier 字段保留；
 * 城 plotSeed 选 damageTier==1 且 rotationOf==0 的最小种子（未旋转，本地坐标 1:1）——
 * P16-B3 的两个城内巨构走的也是这一条（它们是 {@link CityVariants#ALL} 的成员，不加特殊分支）；
 * outpost rot=0 显式传参、Random 种子 = SALT_PLACE（OutpostTemplateCheck 同盐）；
 * machine/husk/ruin/colossus 形状即残缺剪影、无损伤/无形态掷骰（tier=0）；</li>
 * <li>调色板 = RasterSink.defaultPalette() + GT5U 福利键包装（CityRasterPreview.gtAccentPalette
 * 同款复制）：'X' 镀铜砖块 = sBlockCasings1 meta10，'Z' 固体钢机械外壳 = sBlockCasings2 meta0；
 * P16-C1 起再补巨构记号补集（{@link #colossusExtraChars()} 从 {@link RuinedColossusShapes#usedChars()}
 * 现算，键与 meta 一律回读 {@link RuinedColossusShapes#blockKeyOf(char)} / {@link #metaOf} 的<b>生产</b>
 * 真值，工具内不写第二个 meta 表）；
 * 'C' 一字两义（city/outpost/ruin/colossus = 圆石；husk = 核心位积碳壳 meta1），图例条目带 groups 域区分；
 * P7 起 'C' 再加一条 machine 域条目（机型核心位同为积碳壳 meta1，{@code RuinedMachineShapes.metaOf}）；</li>
 * <li>图例表 {@link #legend()} 是<b>唯一一份</b>（旧 {@code PALETTE_GROUPS} 平行表已删——它只服务断言面，
 * 却需要人肉与图例逐行对齐，正是"两处真值"）；页内图例与 {@link #assertGridKnownChars} 读同一份；</li>
 * <li>确定性：无时间戳/无随机遍历，双跑（进程内两次独立构建 + 跨进程两次运行）SHA-256 一致，
 * 不一致即非零退出。</li>
 * </ul>
 */
public class StructureViewerExport {

    /** 城/outpost 记号族（CityVariants.blockKeyOf 全集；图例固定顺序 = 本序）。 */
    private static final String CITY_CHARS = "#@%dprcsSCGBXZ";

    /** 中损档（tier=1）下标（CityVariants.MISSING_RATES[1]）。 */
    private static final int DEFAULT_TIER = 1;

    /** outpost 放置细节种子（OutpostTemplateCheck 同款 SALT_PLACE）。 */
    private static final long PLACE_SEED = 0x5A4C4EL;

    /** 城 26 变体中文名（与页内既有分组口径一致；缺失回退 = id 本身）。 */
    private static final String[][] CITY_LABELS = { { "watch_tower", "望塔残躯" }, { "gear_tower", "齿轮塔" },
        { "chimney_stack", "烟囱束" }, { "water_tower", "水塔" }, { "cooling_tower", "冷却塔" },
        { "clock_tower", "钟塔" }, { "boiler_house", "锅炉房" }, { "pump_house", "泵房" },
        { "forge_hall", "锻造厅" }, { "engine_room", "蒸汽机房" }, { "gas_holder", "储气罐" },
        { "pressure_tank_row", "压力罐排" }, { "broken_bridge", "断桥" }, { "viaduct", "高架渠" },
        { "rail_platform", "轨台" }, { "crane_ruin", "起重机残骸" }, { "canal_gate", "渠闸" },
        { "dome_hall", "穹顶大厅" }, { "market_colonnade", "市场柱廊" }, { "manor_ruin", "庄园残迹" },
        { "fountain_basin", "干涸喷池" }, { "tram_depot", "轨车车库" }, { "fallen_arch", "倒塌拱门" },
        { "machine_plinth", "机座残墩" }, { "slag_heap", "渣堆" }, { "broken_pillars", "断柱群" },
        // P16-B3 城内巨构两条（P16-C1 补译名；缺译只回退 id，不再打断导出）
        { "great_forge", "巨型锻焊大厅（城内巨构·跨 2×2）" },
        { "titan_gearworks", "巨型齿轮机构塔（城内巨构·跨 2×2）" } };

    /** outpost 6 变体中文名（A4A5 报告 §3 意象）。 */
    private static final String[][] OUTPOST_LABELS = { { "outpost_watch_post", "瞭望台残躯" },
        { "outpost_broken_aqueduct", "断拱渠段" }, { "outpost_toppled_boiler", "翻倒锅炉座" },
        { "outpost_brick_kiln", "砖窑残躯" }, { "outpost_collapsed_truss", "塌桁桥段" },
        { "outpost_gear_slag_mound", "露齿轮渣山" } };

    /** husk 2 变体中文名（WorldGenShatteredRuins javadoc 意象）。 */
    private static final String[][] HUSK_LABELS = { { "husk_small", "矮残骸（残墙断口+双残柱）" },
        { "husk_tall", "烟囱残梗" } };

    /**
     * husk 腿的权威数组等价物：反射取的 {@code WorldGenShatteredRuins} 静态字段名表。
     * P16-C1 起 husk 的条数从这里派生（原来在 EXPORT 行与 {@link #appendHuskVariants} 里各写死一个 2）。
     */
    private static final String[] HUSK_FIELDS = { "HUSK_SMALL", "HUSK_TALL" };

    /** 残缺机器 5 机型中文名（RuinedMachineShapes javadoc ①-⑤ 意象原话）。 */
    private static final String[][] MACHINE_LABELS = { { "boiler_frame", "锅炉框架（立式方筒+东墙炉门+塌顶）" },
        { "pump_base", "泵座（矮台基座+四角柱脚+泵心积碳）" }, { "gear_mill_table", "齿轮碾磨台（碎瓷齿环包积碳磨芯）" },
        { "steam_gallery", "蒸汽管廊（墩柱+双排管槽+顶梁中跨坍塌）" }, { "chimney_base", "烟囱基座（中空烟道+顶部锯齿破口）" } };

    /**
     * P8 城外废墟族 8 条破坏结构的中文名。谱系（母体 + 算子序列）<b>不在这里手写</b>：
     * {@link #appendRuinVariants} 从 {@link RuinTemplate#mother()} / {@link RuinTemplate#ops()}
     * 拼进 label 字段——手写第二份谱系就是"名册谱系两处真值"，与 plan §2.1 横切 roster 行冲突。
     */
    private static final String[][] RUIN_LABELS = { { "ruin_aqueduct_span", "断跨渠段残段" },
        { "ruin_truss_fan", "塌桁扇形残骸" }, { "ruin_kiln_stump", "砖窑缺角残冠" },
        { "ruin_boiler_lean", "倾覆锅炉残座" }, { "ruin_watch_buried", "半埋瞭望残躯" },
        { "ruin_chimney_fan", "烟囱塌落扇" }, { "ruin_gallery_span", "断跨管廊残段" },
        { "ruin_pump_chip", "缺角泵座残墩" } };

    /**
     * P16-B1 城外跨 chunk 巨构 2 条的中文名（P16-C1 第六条腿）。<b>纯装饰表</b>：
     * 只按 id 查译名，长度不参与任何断言（旧 {@code RUIN_LABELS.length} 那种"标签表长度 == 名册长度"
     * 就是本轮要拆掉的地雷——名册扩项时它先把导出器打断，反而让 {@code [3/4]} 的真对账看不见）。
     * 意象出处 = {@link RuinedColossusShapes} 类注释与 {@code buildHubArray/buildBoilerHall} 的 javadoc。
     */
    private static final String[][] COLOSSUS_LABELS = { { "colossus_hub_array", "枢纽阵列残架（跨 2×1 chunk 母体蓝图）" },
        { "colossus_boiler_hall", "锅炉大厅残壳（跨 2×2 chunk 母体蓝图）" } };

    /**
     * 巨构七枚专用记号的中文语义（P16-C1）。键集只用于"查到就译"，实际入图例的字符由
     * {@link #colossusExtraChars()} 从 {@link RuinedColossusShapes#usedChars()} 现算；
     * 注册标识与 meta 同样回读 {@link RuinedColossusShapes#blockKeyOf(char)} / {@link #metaOf(char)}，
     * 本工具不写第二张键表（写死 meta 就是下一个"两处真值"）。
     */
    private static final String[][] COLOSSUS_CHAR_NAMES = { { "n", "青铜管道", "GregTechAPI.sBlockCasings2 meta12" },
        { "N", "钢管道", "GregTechAPI.sBlockCasings2 meta13" }, { "e", "青铜齿轮箱", "sBlockCasings2 meta2" },
        { "E", "钢齿轮箱", "sBlockCasings2 meta3" }, { "f", "青铜燃烧室", "sBlockCasings3 meta13" },
        { "F", "钢燃烧室", "sBlockCasings3 meta14" }, { "v", "防爆玻璃", "sBlockGlass1 meta10" } };

    private final RasterSink.Palette palette;
    /** 本次构建实际写出的变体数（P7 起 EXPORT 行由它打印，不再写死总数——写死就是本次缺口的根因）。 */
    private int emitted;

    public static void main(String[] args) throws Exception {
        final File outDir = new File(
            args.length > 0 ? args[0]
                : "plan/维度计划/设计册与实施计划/review/dim1/data");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("cannot create output dir: " + outDir.getAbsolutePath());
        }
        final StructureViewerExport exporter = new StructureViewerExport();
        // 进程内双跑对拍：两次独立构建逐字节一致（确定性断言面之一）
        final String first = exporter.buildJson();
        final String second = exporter.buildJson();
        if (!MessageDigest.isEqual(first.getBytes(), second.getBytes())) {
            fail("in-process double build mismatch");
        }
        final File jsonOut = new File(outDir, "structures-dim7879.json");
        final File jsOut = new File(outDir, "structures-dim7879.js");
        Files.write(jsonOut.toPath(), first.getBytes("UTF-8"));
        Files.write(jsOut.toPath(), ("var STRUCTURES_DATA = " + first + ";\n").getBytes("UTF-8"));
        // 回读校验 + 摘要（跨进程双跑 = 外部对两次运行打印的 SHA256 比对）
        final String jsonSha = sha256(Files.readAllBytes(jsonOut.toPath()));
        final String jsSha = sha256(Files.readAllBytes(jsOut.toPath()));
        if (!MessageDigest.isEqual(first.getBytes("UTF-8"), Files.readAllBytes(jsonOut.toPath()))) {
            fail("json read-back mismatch");
        }
        // P16-C1 派生总量核对：本次实际写出数必须等于"六条腿权威数组之和"（本工具内零名册数字，
        // 腿内另有逐条计数判据；真"数据件 == 名册"的门在 asset_baseline_check.sh 的 [3/4]）。
        final int expected = expectedVariants();
        if (exporter.emitted != expected) {
            fail("emitted " + exporter.emitted + " != derived total " + expected + "（某条腿漏条或多了条目）");
        }
        // 装饰性译名表的反向钉：表里不得有名册中不存在的 id（改名/删条目会在这里红）。
        assertLabelRowsResolve();
        System.out.println("EXPORT variants=" + exporter.emitted + " (city=" + CityVariants.ALL.length
            + " outpost=" + ProsperityOutpostPlacer.ALL.length + " machine=" + RuinedMachineShapes.ALL.length
            + " ruin=" + RuinShapes.ALL.length + " colossus=" + RuinedColossusShapes.ALL.length
            + " husk=" + HUSK_FIELDS.length + " derived-total=" + expected + ") bytes="
            + first.getBytes("UTF-8").length);
        System.out.println("EXPORT spans colossus=" + colossusSpanRow());
        System.out.println("SHA256 json=" + jsonSha);
        System.out.println("SHA256 js=" + jsSha);
        System.out.println("EXPORT DONE -> " + jsonOut.getAbsolutePath());
    }

    /**
     * 派生期望总数 = 六条腿权威数组长度之和（城/outpost/机型/废墟/巨构各取自己的 {@code ALL.length}，
     * husk 取反射字段名表长度）。<b>这是本文件唯一一处"总量"式判据，且它不含任何字面量计数</b>：
     * 名册扩项时这里自动跟着长，只有"某条腿整类漏了新项"才会红——而那正是本轮两次返工的形态。
     */
    static int expectedVariants() {
        return CityVariants.ALL.length + ProsperityOutpostPlacer.ALL.length + RuinedMachineShapes.ALL.length
            + RuinShapes.ALL.length + RuinedColossusShapes.ALL.length + HUSK_FIELDS.length;
    }

    /** 巨构腿的跨片读数行（回执与 reviewer 直接读的"跨几 chunk / 几片 / 实心 / 机械件"）。 */
    static String colossusSpanRow() {
        final StringBuilder b = new StringBuilder();
        for (final RuinedColossusShapes.Colossus c : RuinedColossusShapes.ALL) {
            if (b.length() > 0) {
                b.append(" | ");
            }
            b.append(c.name)
                .append(" bbox=")
                .append(c.sizeX())
                .append('x')
                .append(c.sizeY())
                .append('x')
                .append(c.sizeZ())
                .append(" chunks=")
                .append(c.coveredChunksX())
                .append('x')
                .append(c.coveredChunksZ())
                .append(" slices=")
                .append(c.slices.length)
                .append(" sliceSolidSum=")
                .append(c.solidChars())
                .append(" machine=")
                .append(c.motherMachine)
                .append('/')
                .append(c.motherSolid);
        }
        return b.toString();
    }

    /**
     * 译名表反向钉：每张表的 id 必须能在自己那条腿的权威名册里找到。
     * 名册扩项不触发这里（那是装饰表，缺译回退 id），<b>改名或删了名册成员而忘了同步</b>才触发。
     */
    static void assertLabelRowsResolve() {
        final String[][][] tables = { CITY_LABELS, OUTPOST_LABELS, MACHINE_LABELS, RUIN_LABELS, COLOSSUS_LABELS };
        final String[] sources = { names(CityVariants.ALL.length, index -> CityVariants.ALL[index].name),
            names(ProsperityOutpostPlacer.ALL.length, index -> ProsperityOutpostPlacer.ALL[index].name),
            names(RuinedMachineShapes.ALL.length, index -> RuinedMachineShapes.ALL[index].name),
            names(RuinShapes.ALL.length, index -> RuinShapes.ALL[index].name),
            names(RuinedColossusShapes.ALL.length, index -> RuinedColossusShapes.ALL[index].name) };
        for (int t = 0; t < tables.length; t++) {
            for (final String[] row : tables[t]) {
                if (!(";" + sources[t] + ";").contains(";" + row[0] + ";")) {
                    fail("label table row '" + row[0] + "' 不在腿 " + t + " 的名册里（改名/删条目未同步）");
                }
            }
        }
    }

    /** 把某条腿的名字拼成一段分号串（只为 {@link #assertLabelRowsResolve()} 的存在性检查；不持真值）。 */
    private static String names(int n, java.util.function.IntFunction<String> nameAt) {
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            b.append(nameAt.apply(i)).append(';');
        }
        return b.toString();
    }

    /**
     * 巨构腿专用记号（P16-C1）：{@link RuinedColossusShapes#usedChars()} 里 {@b 不属于}
     * {@link #CITY_CHARS} 的那几个字符（现为 {@code nNeEfFv} 七枚，全由生产侧记号表定义）。
     * 从生产侧现算 ⇒ 将来巨构再引新记号，图例自动多一条（键/meta 也自动跟着走），不需要改本工具。
     */
    static String colossusExtraChars() {
        final StringBuilder b = new StringBuilder();
        for (final char c : RuinedColossusShapes.usedChars().toCharArray()) {
            if (c != '.' && CITY_CHARS.indexOf(c) < 0) {
                b.append(c);
            }
        }
        return b.toString();
    }

    StructureViewerExport() {
        // 调色板 = defaultPalette + GT5U 福利键（CityRasterPreview.gtAccentPalette 同款包装）。
        // P16-C1 起补集由 colossusAccents() 现算（键与 meta 都取自 RuinedColossusShapes 的生产记号表），
        // 所以这里既没有第二张 (char → 键:meta) 表，也没有写死任何 meta 数字。
        final RasterSink.Palette base = RasterSink.defaultPalette();
        this.palette = (block, meta) -> {
            final String key = block + ":" + meta;
            if ("gt5u:CasingBronzePlated:10".equals(key)) {
                return 0xFFB87333; // 镀铜砖块：铜橙
            }
            if ("gt5u:CasingSolidSteel:0".equals(key)) {
                return 0xFF8C9BA5; // 固体钢机械外壳：冷钢蓝灰
            }
            final Integer accent = COLOSSUS_ACCENTS.get(key);
            if (accent != null) {
                return accent.intValue();
            }
            return base.applyAsInt(block, meta);
        };
    }

    /**
     * 巨构专用记号 → 展示色（P16-C1）。键 = {@code blockKeyOf(c) + ":" + metaOf(c)} 由生产侧派生；
     * 表里没有的字符（将来巨构再引新记号）自动回落 {@link RasterSink#defaultPalette()} 的稳定散列色，
     * 不 fail ——装饰面永远不该把导出打断。
     */
    private static final java.util.Map<String, Integer> COLOSSUS_ACCENTS = colossusAccents();

    private static java.util.Map<String, Integer> colossusAccents() {
        final java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        final String rgbFor = "nNeEfFv";
        final int[] rgb = { 0xFF9C6B3F, 0xFF7E8790, 0xFFBE9060, 0xFF9AA5AE, 0xFF8E5A32, 0xFF6E7A85, 0xFF9FD4E0 };
        final String extra = colossusExtraChars();
        for (int i = 0; i < extra.length(); i++) {
            final char c = extra.charAt(i);
            final int at = rgbFor.indexOf(c);
            out.put(
                RuinedColossusShapes.blockKeyOf(c) + ":" + RuinedColossusShapes.metaOf(c),
                Integer.valueOf(at >= 0 ? rgb[at] : 0xFFB08A5F));
        }
        return out;
    }

    // ═══ JSON 构建（StringBuilder 手写，顺序固定 → 字节确定） ═══

    String buildJson() throws Exception {
        this.emitted = 0; // 每次构建从 0 计数（进程内双跑必须逐字节相同）
        final StringBuilder b = new StringBuilder(1 << 18);
        b.append("{\n");
        b.append("  \"generator\": \"tools/dim1/StructureViewerExport.java\",\n");
        b.append("  \"format\": \"gtsr-voxel-1\",\n");
        b.append("  \"axis\": \"x→东/右, z→南/下, y→上；layers[y] 为水平切片（y=0 基座层），每行 = z 行（sizeX 个字符），'.'=空\",\n");
        b.append("  \"tierDefault\": ").append(DEFAULT_TIER).append(",\n");
        b.append("  \"tiers\": [5, 20, 40],\n");
        b.append("  \"gt5uNote\": \"X=GregTechAPI.sBlockCasings1 meta10（镀铜砖块）· Z=GregTechAPI.sBlockCasings2 meta0（固体钢机械外壳），来源 dim78-fix-slice-A4A5-report §1\",\n");
        b.append("  \"palette\": [\n");
        appendPalette(b);
        b.append("\n  ],\n");
        b.append("  \"groups\": [\n");
        // P7：groups 由三条腿补成四条腿，计数一律从各腿的权威数组取（原来写死三条 = 机型腿缺失的根因）。
        b.append("    {\"id\":\"city\",\"name\":\"繁荣废都·城变体（dim78）\",\"source\":\"CityVariants.ALL\",\"count\":")
            .append(CityVariants.ALL.length).append("},\n");
        b.append("    {\"id\":\"outpost\",\"name\":\"城外中型废墟（dim78 outposts）\",\"source\":\"ProsperityOutpostPlacer.ALL\",\"count\":")
            .append(ProsperityOutpostPlacer.ALL.length).append("},\n");
        b.append(
            "    {\"id\":\"machine\",\"name\":\"残缺蒸汽机器（dim78 machines，模板剪影·损伤掷骰在运行期）\",\"source\":\"RuinedMachineShapes.ALL\",\"count\":")
            .append(RuinedMachineShapes.ALL.length).append("},\n");
        // P8 第五条腿：城外废墟族（破坏结构）。与 machine/husk 腿同口径——直接读剪影、不做运行期掷骰；
        // 但 ruin 的剪影本身已经是 RuinDamageOps 的"破败化"产物，谱系（母体 + 算子序列）随 label 一起出，
        // 这样展示页能一眼看出"这条烂墙是哪条结构变的"。
        b.append(
            "    {\"id\":\"ruin\",\"name\":\"城外废墟·破坏结构（dim78 ruins，P8 由既有结构破败化派生；无仓室/控制器/TE）\",\"source\":\"RuinShapes.ALL（RuinDamageOps 派生）\",\"count\":")
            .append(RuinShapes.ALL.length).append("},\n");
        // P16-C1 第六条腿：城外跨 chunk 巨构。导出的是<b>母体蓝图</b>（名册里逐名 SHA 钉的那份、
        // /gtsr structure 摆放的那份）；世界里真正落下的是 P16-B2 的四档派生残骸 + 半埋深度，
        // 那是运行期 morphAt 的选型 ⇒ 与 machine/ruin/husk 同口径 tier=0、无 seed。
        b.append(
            "    {\"id\":\"colossus\",\"name\":\"城外跨 chunk 巨构（dim78 colossus，母体蓝图；世界落 B2 派生残骸档）\",\"source\":\"RuinedColossusShapes.ALL（几何经 ChunkSpans）\",\"count\":")
            .append(RuinedColossusShapes.ALL.length).append("},\n");
        b.append(
            "    {\"id\":\"husk\",\"name\":\"破碎残骸骨架（dim79）\",\"source\":\"WorldGenShatteredRuins（反射形状）\",\"count\":")
            .append(HUSK_FIELDS.length).append("}\n");
        b.append("  ],\n");
        b.append("  \"variants\": [\n");
        appendCityVariants(b);
        appendOutpostVariants(b);
        appendMachineVariants(b);
        appendRuinVariants(b);
        appendColossusVariants(b);
        appendHuskVariants(b);
        b.append("\n  ]\n");
        b.append("}\n");
        return b.toString();
    }

    /**
     * 调色板图例：char → 颜色 → 语义名 → meta 备注；'C' 一字多义以 groups 域区分。
     * <p>
     * <b>P16-C1 单一图例表</b>：本方法与其消费方 {@link #paletteLegendFor(String)}（断言面）读同一份
     * {@link #legend()}。旧实现另有一张 {@code PALETTE_GROUPS} 平行表、注释写着"必须与图例逐行一致"
     * 却没有任何东西保证它一致——那正是本轮要拆的"两处真值"，已随本次删除。
     */
    private void appendPalette(StringBuilder b) {
        final String[][] legend = legend();
        for (int i = 0; i < legend.length; i++) {
            final String[] e = legend[i];
            final int argb = this.palette.applyAsInt(e[1], Integer.parseInt(e[2]));
            b.append("    {\"char\":\"").append(e[0]).append("\",\"color\":\"").append(hex(argb))
                .append("\",\"block\":\"").append(e[1]).append("\",\"meta\":").append(e[2])
                .append(",\"name\":\"").append(e[3]).append("\",\"groups\":\"").append(e[4]).append("\"");
            if (!e[5].isEmpty()) {
                b.append(",\"note\":\"").append(e[5]).append("\"");
            }
            b.append("}").append(i < legend.length - 1 ? "," : "").append("\n");
        }
    }

    /**
     * 唯一一份图例（P16-C1）：前 16 行是既有的记号族（'C' 的 husk/machine 两条按 groups 域分义，
     * groups 域自 P16-C1 起补上 {@code colossus}——巨构的共用记号含义与
     * {@link CityVariants#blockKeyOf(char)} 逐字一致，故不新立条目），尾部若干行是巨构专用记号，
     * 由 {@link #colossusExtraChars()} 从生产侧现算、键与 meta 全部回读
     * {@link RuinedColossusShapes#blockKeyOf(char)} / {@link RuinedColossusShapes#metaOf(char)}。
     * 顺序固定 ⇒ 双跑逐字节一致。
     */
    static String[][] legend() {
        final String[][] shared = { { "#", "gtsr:RuinedCasing", "0", "锈壳（外壳族 meta0）",
            "city,outpost,machine,ruin,husk,colossus",
            "city/outpost/machine/ruin 共用；husk 语境 = 积碳外层（WorldGenShatteredRuins 口径）" },
            { "@", "gtsr:RuinedCasing", "1", "积碳壳（外壳族 meta1）", "city,outpost,machine,ruin,colossus",
                "烟黑；P8 废墟族也是它（机型 'C' 核心位归一后的落点）" },
            { "%", "gtsr:RuinedCasing", "2", "碎瓷壳（外壳族 meta2）", "city,outpost,machine,ruin,colossus",
                "青白；P8 废墟族里兼作 GT5U 'X' 锈变落点" },
            { "d", "gtsr:RuinDebris", "0", "轨枕残木", "city,outpost,ruin,colossus", "" },
            { "p", "gtsr:RuinDebris", "1", "管道残段", "city,outpost,ruin,colossus", "" },
            { "r", "gtsr:RuinDebris", "2", "铆接板", "city,outpost,ruin,colossus", "" },
            { "c", "gtsr:RuinDebris", "3", "烟囱残段", "city,outpost,ruin,colossus", "" },
            { "s", "gtsr:ProsperitySurface", "5", "锈石铺面", "city,outpost,ruin,colossus",
                "城内冻结地表 meta5；废墟族里被锈蚀算子降级为碎瓷壳/沙砾" },
            { "S", "minecraft:stone", "0", "原版石", "city,outpost,ruin,colossus", "" },
            { "C", "minecraft:cobblestone", "0", "圆石", "city,outpost,ruin,colossus",
                "husk/machine 的 'C' 另有条目（核心位积碳壳）" },
            { "G", "minecraft:gravel", "0", "沙砾", "city,outpost,ruin,colossus",
                "P8 废墟族的塌落扇形/半埋覆压散料" },
            { "B", "minecraft:iron_bars", "0", "铁栏杆", "city,outpost,ruin,colossus", "" },
            { "X", "gt5u:CasingBronzePlated", "10", "镀铜砖块 Bronze Plated Bricks", "city,outpost,colossus",
                "GT5U 福利键 GregTechAPI.sBlockCasings1 meta10；P8 废墟族不用（GT 机器注册体，见 RuinFamilyCheck TE 组）" },
            { "Z", "gt5u:CasingSolidSteel", "0", "固体钢机械外壳 Solid Steel Machine Casing", "city,outpost,colossus",
                "GT5U 福利键 GregTechAPI.sBlockCasings2 meta0；P8 废墟族不用（同上）" },
            { "C", "gtsr:RuinedCasing", "1", "husk 核心位（积碳壳 meta1）", "husk", "仅 dim79 husk；无 TE 无箱子" },
            { "C", "gtsr:RuinedCasing", "1", "machine 核心位（积碳壳 meta1）", "machine",
                "仅 dim78 机型 'C' 位；RuinedMachineShapes.metaOf 同款，无 TE 无控制器无修复无战利品" } };
        final String extra = colossusExtraChars();
        final String[][] out = java.util.Arrays.copyOf(shared, shared.length + extra.length());
        for (int i = 0; i < extra.length(); i++) {
            final String c = String.valueOf(extra.charAt(i));
            String name = "巨构记号 " + c;
            String gtNote = "P16-B1 巨构专用记号；键与 meta 由 RuinedColossusShapes.blockKeyOf/metaOf 现算";
            for (final String[] row : COLOSSUS_CHAR_NAMES) {
                if (row[0].equals(c)) {
                    name = row[1];
                    gtNote = "GT5U 福利键 " + row[2] + "（无 TE 的静态外壳；RuinFamilyCheck COLOSSUS 组逐格钉 meta 红线）";
                    break;
                }
            }
            out[shared.length + i] = new String[] { c, RuinedColossusShapes.blockKeyOf(c.charAt(0)),
                String.valueOf(RuinedColossusShapes.metaOf(c.charAt(0))), name, "colossus", gtNote };
        }
        return out;
    }

    private void appendCityVariants(StringBuilder b) throws Exception {
        // P16-C1：原来的 `if (CityVariants.ALL.length != 26) fail` 已删——那是"名册数字抄进工具"的
        // 本体，B3 一加城内巨构就把整个导出器打断（本轮残红的直接成因）。换成腿内逐条计数：
        // 少一条就红，名册扩项则自动跟着长。P16-B3 的两个城内巨构因此不需要任何特殊分支。
        int done = 0;
        for (final CityVariants.Variant v : CityVariants.ALL) {
            // 未旋转 + 中损档种子：damageTier==1 且 rotationOf==0 的最小非负种子
            long plotSeed = -1;
            for (long s = 0; s < 1000000; s++) {
                if (CityVariants.damageTier(s) == DEFAULT_TIER && CityVariants.rotationOf(s) == 0) {
                    plotSeed = s;
                    break;
                }
            }
            if (plotSeed < 0) {
                fail("no seed with tier=1 rot=0 found");
            }
            final char[][][] grid = emptyGrid(v.sizeX, v.sizeY, v.sizeZ);
            final BlockSink sink = recordingSink(grid, v.sizeX, v.sizeY, v.sizeZ, reverseMapCity());
            final BlockSink previewSink = (x, y, z, block, meta, flags) -> String.valueOf(block).endsWith("air")
                ? true
                : sink.setBlock(x, y, z, block, meta, flags);
            CityVariants.place(v, previewSink, 0, 0, (x, z) -> 0, plotSeed, 0);
            appendVariant(b, v.name, labelOf(CITY_LABELS, v.name), "city", v.category, v.sizeX, v.sizeY, v.sizeZ,
                DEFAULT_TIER, CityVariants.MISSING_RATES[DEFAULT_TIER], plotSeed, grid, ",",
                citySpanExtra(v, grid));
            done++;
        }
        if (done != CityVariants.ALL.length) {
            fail("city 腿只导出 " + done + " 条，权威数组 " + CityVariants.ALL.length + " 条（漏条）");
        }
    }

    /**
     * 城内巨构（P16-B3，申报边长 &gt;16 的那两条）额外带一份跨片读数；其余基础变体不带该字段
     * （既有 26 条的字节面一字不改，只有新条目多键）。几何同样取自 {@link ChunkSpans}：
     * 把<b>已导出的那座矩阵</b>（y=0 在底）翻成 {@code ChunkSpans} 要的层序（layers[0] 顶层）后交给它切，
     * 导出侧不写第二份 clip/intersect；{@code Variant.layers} 是包内私有，本工具本来也不该绕过去读。
     */
    private static String citySpanExtra(CityVariants.Variant v, char[][][] grid) {
        if (Math.max(v.sizeX, v.sizeZ) <= ChunkSpans.CHUNK_BLOCKS) {
            return "";
        }
        final ChunkSpans.Slice[] slices = ChunkSpans.slice(toLayers(grid), v.sizeX, v.sizeY, v.sizeZ);
        int solid = 0;
        for (final ChunkSpans.Slice s : slices) {
            solid += s.solidChars;
        }
        return ",\"spans\":{\"chunksX\":" + ChunkSpans.chunksAcross(v.sizeX) + ",\"chunksZ\":"
            + ChunkSpans.chunksAcross(v.sizeZ) + ",\"slices\":" + slices.length + ",\"sliceSolid\":" + solid + "}";
    }

    /** 导出矩阵 {@code [y][z][x]}（y=0 = 基座层）→ {@link ChunkSpans} 的层序（layers[0] = 顶层）。 */
    private static String[][] toLayers(char[][][] grid) {
        final int sizeY = grid.length;
        final int sizeZ = grid[0].length;
        final String[][] layers = new String[sizeY][sizeZ];
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                layers[sizeY - 1 - y][z] = new String(grid[y][z]);
            }
        }
        return layers;
    }

    private void appendOutpostVariants(StringBuilder b) {
        int done = 0;
        for (final Outpost o : ProsperityOutpostPlacer.ALL) {
            final char[][][] grid = emptyGrid(o.sizeX, o.sizeY, o.sizeZ);
            final BlockSink sink = recordingSink(grid, o.sizeX, o.sizeY, o.sizeZ, reverseMapCity());
            final BlockSink previewSink = (x, y, z, block, meta, flags) -> String.valueOf(block).endsWith("air")
                ? true
                : sink.setBlock(x, y, z, block, meta, flags);
            ProsperityOutpostPlacer.place(
                new StructureBuilder(previewSink),
                o,
                0,
                0,
                0,
                CityVariants.MISSING_RATES[DEFAULT_TIER],
                new Random(PLACE_SEED),
                (x, z) -> 0,
                BlockSink.FLAG_POPULATE);
            appendVariant(b, o.name, labelOf(OUTPOST_LABELS, o.name), "outpost", "outpost", o.sizeX, o.sizeY, o.sizeZ,
                DEFAULT_TIER, CityVariants.MISSING_RATES[DEFAULT_TIER], PLACE_SEED, grid, ",", "");
            done++;
        }
        // P16-C1：原 `!= 6` 硬钉换成腿内计数（同 city 腿的理由）
        if (done != ProsperityOutpostPlacer.ALL.length) {
            fail("outpost 腿只导出 " + done + " 条，权威数组 " + ProsperityOutpostPlacer.ALL.length + " 条（漏条）");
        }
    }

    /**
     * 城外废墟族 8 条破坏结构（P8 第五条腿）。与 machine/husk 腿同口径：<b>直接读派生后的剪影</b>，
     * 不走 {@code RuinPlacer.place}（那条路径要经 {@code CityBlockResolver} 取 Block 实例 ⇒ 触发 MC
     * 类初始化，破掉本工具"零 MC 执行依赖"的前提）。
     * <p>
     * 与 machine/husk 的差别只在 {@code tier}：废墟的<b>结构损毁已经烧在模板里</b>
     * （{@link RuinDamageOps} 的六个算子），运行期另有 {@code MISSING_RATES} 逐块侵蚀——
     * 后者属于运行期行为，按既有口径不进模板，故 {@code tier=0 / missingRate=0 / 无 seed}。
     * 谱系（母体 + 算子序列）从 {@link RuinTemplate#mother()} / {@link RuinTemplate#ops()} 拼进
     * label，展示页因此能一眼看出"这条烂墙是哪条结构变的"。
     */
    private void appendRuinVariants(StringBuilder b) {
        // P16-C1：原 `RuinShapes.ALL.length != RUIN_LABELS.length` 硬钉删除——那是"标签表长度当名册长度"
        // 的形态，废墟族一扩项就先把导出器打断。标签表降级为装饰（缺译回退 id），漏条由下面的腿内计数抓。
        int done = 0;
        for (final RuinTemplate ruin : RuinShapes.ALL) {
            final char[][][] grid = emptyGrid(ruin.sizeX, ruin.sizeY, ruin.sizeZ);
            for (int y = 0; y < ruin.sizeY; y++) {
                for (int dz = 0; dz < ruin.sizeZ; dz++) {
                    for (int dx = 0; dx < ruin.sizeX; dx++) {
                        final char c = ruin.charAt(y, dx, dz);
                        if (c == ' ') {
                            continue; // 不触碰（同 RuinPlacer.place）
                        }
                        if (c != '#' && c != '@' && c != '%' && c != 'd' && c != 'p' && c != 'r' && c != 'c'
                            && c != 's' && c != 'S' && c != 'C' && c != 'G' && c != 'B' && c != '.') {
                            fail("ruin char '" + c + "' outside existing symbol family @" + ruin.name);
                        }
                        if (RuinShapes.isForbiddenChar(c)) {
                            fail("ruin char '" + c + "' is a forbidden machine-body key @" + ruin.name);
                        }
                        if (c != '.' && CityVariants.blockKeyOf(c) == null) {
                            fail("ruin char '" + c + "' has no block key @" + ruin.name);
                        }
                        grid[y][dz][dx] = c;
                    }
                }
            }
            final String label = labelOf(RUIN_LABELS, ruin.name) + "（母体 " + ruin.mother() + " · 算子 "
                + String.join("+", ruin.ops()) + "）";
            appendVariant(
                b,
                ruin.name,
                label,
                "ruin",
                "ruin",
                ruin.sizeX,
                ruin.sizeY,
                ruin.sizeZ,
                0,
                0,
                -1,
                grid,
                ",",
                "");
            done++;
        }
        if (done != RuinShapes.ALL.length) {
            fail("ruin 腿只导出 " + done + " 条，权威数组 " + RuinShapes.ALL.length + " 条（漏条）");
        }
    }

    /**
     * 城外跨 chunk 巨构（<b>P16-C1 第六条腿</b>）。两条 {@link RuinedColossusShapes.Colossus} 的总 bbox
     * 超出单 chunk（24×12×16 跨 2×1 / 20×12×20 跨 2×2），本腿<b>按总 bbox 逐格导整座</b>——区5 因此
     * 转得出"一座横跨 2×2 的巨构"的真实形态，而不是只有锚点那 16×16 片。
     * <p>
     * 三条纪律：
     * <ol>
     * <li><b>零第二套几何</b>：跨片读数全部来自 {@link ChunkSpans}（{@code Colossus#slices} 就是生产
     * {@code ChunkSliceSink} 用的那一份切片结果），导出侧不写 clip/intersect；</li>
     * <li><b>完整性的machine证</b>：本腿逐格数出的实心格数必须等于 {@link RuinedColossusShapes.Colossus#solidChars()}
     * （= 各分片 {@code solidChars} 之和，即生产那套几何的读数）。两者不等 ⇒ 要么漏格（只导了锚点片）
     * 要么多格，直接 fail fast，不静默出一座"缺一角"的巨构；</li>
     * <li><b>记号可解析</b>：每个非 {@code '.'} 字符都要在 {@link RuinedColossusShapes#blockKeyOf(char)}
     * 有键（禁字符面由 {@code RuinFamilyCheck} 的 COLOSSUS 组与生产侧 {@code CityBlockResolver} 钉，
     * 本工具只保证"导出的东西都有材质"）。</li>
     * </ol>
     * 形态口径：导的是<b>母体蓝图</b>（名册逐名 SHA 钉的那份、{@code /gtsr structure} 摆放的那份）；
     * 世界里落下的是 P16-B2 由 {@code RuinedColossusShapes.morphAt} 选出的四档派生残骸 + 半埋深度，
     * 属运行期选型 ⇒ 与 machine/ruin/husk 同 {@code tier=0 / missingRate=0 / 无 seed}。
     */
    private void appendColossusVariants(StringBuilder b) {
        int done = 0;
        for (final RuinedColossusShapes.Colossus c : RuinedColossusShapes.ALL) {
            final char[][][] grid = emptyGrid(c.sizeX(), c.sizeY(), c.sizeZ());
            int solid = 0;
            for (int y = 0; y < c.sizeY(); y++) {
                for (int dz = 0; dz < c.sizeZ(); dz++) {
                    for (int dx = 0; dx < c.sizeX(); dx++) {
                        final char ch = c.shape.charAt(y, dx, dz);
                        if (ch == ' ') {
                            continue; // 不触碰（同 RuinedMachinePlacer 的跨片分支）
                        }
                        if (ch != '.' && RuinedColossusShapes.blockKeyOf(ch) == null) {
                            fail("colossus char '" + ch + "' has no block key @" + c.name);
                        }
                        grid[y][dz][dx] = ch;
                        if (ChunkSpans.isSolid(ch)) {
                            solid++;
                        }
                    }
                }
            }
            // 完整性对账：导出的整座实心数 == ChunkSpans 分片实心数之和（生产那套几何的读数）
            if (solid != c.solidChars()) {
                fail(c.name + " 导出实心 " + solid + " != ChunkSpans 分片实心和 " + c.solidChars()
                    + "（=> 只导了锚点片或漏/多格）");
            }
            final int slices = c.slices.length;
            final int chunksX = c.coveredChunksX();
            final int chunksZ = c.coveredChunksZ();
            if (chunksX * chunksZ < 2 || slices < 2) {
                fail(c.name + " 申报跨 chunk 却只有 " + chunksX + "x" + chunksZ + " / " + slices + " 片");
            }
            final String spans = ",\"spans\":{\"chunksX\":" + chunksX + ",\"chunksZ\":" + chunksZ
                + ",\"slices\":" + slices + ",\"sliceSolid\":" + c.solidChars() + ",\"exportedSolid\":" + solid
                + ",\"machineCells\":" + c.motherMachine + ",\"solidCells\":" + c.motherSolid
                + ",\"blueprint\":\"母体蓝图；世界落 B2 派生残骸档（morphAt 四档 + 半埋 0.." + c.maxBury + "）\"}";
            appendVariant(
                b,
                c.name,
                labelOf(COLOSSUS_LABELS, c.name),
                "colossus",
                "colossus",
                c.sizeX(),
                c.sizeY(),
                c.sizeZ(),
                0,
                0,
                -1,
                grid,
                ",",
                spans);
            done++;
        }
        if (done != RuinedColossusShapes.ALL.length) {
            fail("colossus 腿只导出 " + done + " 条，权威数组 " + RuinedColossusShapes.ALL.length + " 条（漏条）");
        }
    }

    /** husk 形状反射（HuskRasterPreview 同款单一权威；'#'=积碳外层 meta0、'C'=核心位积碳壳 meta1）。 */
    private void appendHuskVariants(StringBuilder b) throws Exception {
        final Class<?> gen = Class.forName("com.miaokatze.gtsr.common.dimension.shattered.WorldGenShatteredRuins");
        final Class<?> huskCls = Class.forName("com.miaokatze.gtsr.common.dimension.shattered.WorldGenShatteredRuins$HuskShape");
        final Method charAt = huskCls.getMethod("charAt", int.class, int.class, int.class);
        int done = 0;
        for (final String fieldName : HUSK_FIELDS) {
            final Object husk = gen.getField(fieldName).get(null);
            final String name = (String) huskCls.getField("name").get(husk);
            final int sizeX = huskCls.getField("sizeX").getInt(husk);
            final int sizeY = huskCls.getField("sizeY").getInt(husk);
            final int sizeZ = huskCls.getField("sizeZ").getInt(husk);
            final char[][][] grid = emptyGrid(sizeX, sizeY, sizeZ);
            for (int y = 0; y < sizeY; y++) {
                for (int dz = 0; dz < sizeZ; dz++) {
                    for (int dx = 0; dx < sizeX; dx++) {
                        final char c = (char) charAt.invoke(husk, y, dx, dz);
                        if (c == ' ') {
                            continue; // 不触碰（同 placeHusk）
                        }
                        if (c != '#' && c != 'C') {
                            fail("husk char '" + c + "' not in {# C}");
                        }
                        grid[y][dz][dx] = c;
                    }
                }
            }
            // tier=0 仅占位：husk 形状即残缺剪影，无损伤掷骰（missingRate=0、无种子）
            appendVariant(b, name, labelOf(HUSK_LABELS, name), "husk", "husk", sizeX, sizeY, sizeZ, 0, 0, -1, grid,
                done == 0 ? "," : "", "");
            done++;
        }
        if (done != HUSK_FIELDS.length) {
            fail("husk 腿只导出 " + done + " 条，反射字段名表 " + HUSK_FIELDS.length + " 条（漏条）");
        }
    }

    /**
     * 残缺机器 5 机型（P7 第四条腿）。与 husk 腿同口径：<b>直接读形状串剪影</b>，不走放置器路径
     * （放置器要解析 {@code BlocksGTSR}/{@code Blocks.air} 的 Block 实例 ⇒ 会触发 MC 类初始化，
     * 破掉本工具"零 MC 执行依赖"的前提）。'C' 核心位在此域就是积碳壳 meta1（与 '@' 同方块，
     * 但模板语义不同，故图例单列一条 machine 域的 'C'）。
     */
    private void appendMachineVariants(StringBuilder b) {
        // P16-C1：原 `!= 5` 硬钉换成腿内计数（同 city 腿的理由）
        int done = 0;
        for (final RuinedMachineShapes.Shape shape : RuinedMachineShapes.ALL) {
            final char[][][] grid = emptyGrid(shape.sizeX, shape.sizeY, shape.sizeZ);
            for (int y = 0; y < shape.sizeY; y++) {
                for (int dz = 0; dz < shape.sizeZ; dz++) {
                    for (int dx = 0; dx < shape.sizeX; dx++) {
                        final char c = shape.charAt(y, dx, dz);
                        if (c == ' ') {
                            continue; // 不触碰（同 RuinedMachinePlacer.place）
                        }
                        if (c != '#' && c != '@' && c != '%' && c != 'C' && c != '.') {
                            fail("machine char '" + c + "' not in {# @ % C .}");
                        }
                        if (c != '.' && RuinedMachineShapes.blockKeyOf(c) == null) {
                            fail("machine char '" + c + "' has no block key");
                        }
                        grid[y][dz][dx] = c;
                    }
                }
            }
            // tier=0 仅占位：机型形状即残骸剪影，损伤度 20-95 是运行期掷骰（不进模板导出）
            appendVariant(
                b,
                shape.name,
                labelOf(MACHINE_LABELS, shape.name),
                "machine",
                "machine",
                shape.sizeX,
                shape.sizeY,
                shape.sizeZ,
                0,
                0,
                -1,
                grid,
                ",",
                "");
            done++;
        }
        if (done != RuinedMachineShapes.ALL.length) {
            fail("machine 腿只导出 " + done + " 条，权威数组 " + RuinedMachineShapes.ALL.length + " 条（漏条）");
        }
    }

    /**
     * 单变体 JSON 对象（含 layers 三维矩阵；seed&lt;0 时省略字段）。
     *
     * @param extra 该条目专属的附加 JSON 片段（P16-C1 起给跨 chunk 条目带 {@code spans}；其余腿传空串，
     *              既有 47 条的字节面因此一字不变）
     */
    private void appendVariant(StringBuilder b, String id, String label, String group, String category, int sizeX,
        int sizeY, int sizeZ, int tier, int missingRate, long seed, char[][][] grid, String trailing, String extra) {
        assertGridKnownChars(group, grid);
        this.emitted++;
        b.append("    {\"id\":\"").append(id).append("\",\"label\":\"").append(label).append("\",\"group\":\"")
            .append(group).append("\",\"category\":\"").append(category).append("\",\"sizeX\":").append(sizeX)
            .append(",\"sizeY\":").append(sizeY).append(",\"sizeZ\":").append(sizeZ).append(",\"tier\":").append(tier)
            .append(",\"missingRate\":").append(missingRate);
        if (seed >= 0) {
            b.append(",\"seed\":").append(seed);
        }
        b.append(extra);
        b.append(",\"layers\":[\n");
        for (int y = 0; y < sizeY; y++) {
            b.append("      [");
            for (int z = 0; z < sizeZ; z++) {
                b.append("\"").append(grid[y][z]).append("\"");
                if (z < sizeZ - 1) {
                    b.append(",");
                }
            }
            b.append("]").append(y < sizeY - 1 ? "," : "").append("\n");
        }
        b.append("    ]}").append(trailing).append("\n");
    }

    // ═══ 记录 Sink（放置流 → 本地坐标网格；越界即 fail fast，不静默裁剪） ═══

    private static char[][][] emptyGrid(int sizeX, int sizeY, int sizeZ) {
        final char[][][] grid = new char[sizeY][sizeZ][sizeX];
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    grid[y][z][x] = '.';
                }
            }
        }
        return grid;
    }

    /** block:meta → 记号 char（city 记号族顺序注册，'@' 先于 'C' 占 RuinedCasing:1）。 */
    private static java.util.Map<String, Character> reverseMapCity() {
        final java.util.Map<String, Character> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < CITY_CHARS.length(); i++) {
            final char c = CITY_CHARS.charAt(i);
            map.putIfAbsent(CityVariants.blockKeyOf(c) + ":" + CityVariants.metaOf(c), Character.valueOf(c));
        }
        return map;
    }

    /** 记录 Sink：rot=0 + origin(0,0) + ground 0 ⇒ wx=dx、wy=1+y、wz=dz；越界 = 契约破坏 fail fast。 */
    private BlockSink recordingSink(char[][][] grid, int sizeX, int sizeY, int sizeZ,
        java.util.Map<String, Character> reverse) {
        return (x, y, z, block, meta, flags) -> {
            final int lx = x;
            final int ly = y - 1; // ground=0 ⇒ wy = 1 + 模板 y
            final int lz = z;
            if (lx < 0 || lx >= sizeX || lz < 0 || lz >= sizeZ || ly < 0 || ly >= sizeY) {
                return fail("write out of template bounds: " + lx + "," + ly + "," + lz + " size " + sizeX + "x"
                    + sizeY + "x" + sizeZ);
            }
            final Character c = reverse.get(block + ":" + meta);
            if (c == null) {
                return fail("no palette char for " + block + ":" + meta);
            }
            grid[ly][lz][lx] = c.charValue();
            return true;
        };
    }

    /** 断言：矩阵中每个非 '.' char 在该 group 的调色板中有条目。 */
    private void assertGridKnownChars(String group, char[][][] grid) {
        final ArrayList<String> keys = new ArrayList<>();
        for (final String[] e : paletteLegendFor(group)) {
            keys.add(e[0]); // 单一图例行：e[0]=char e[1]=block e[2]=meta e[3]=name e[4]=groups
        }
        for (final char[][] layer : grid) {
            for (final char[] row : layer) {
                for (final char c : row) {
                    if (c != '.' && !keys.contains(String.valueOf(c))) {
                        fail("char '" + c + "' missing from palette for group " + group);
                    }
                }
            }
        }
    }

    /** group 可用 char 集合（与图例的 groups 域同一份表——P16-C1 起不再有第二张表）。 */
    private static ArrayList<String[]> paletteLegendFor(String group) {
        final ArrayList<String[]> out = new ArrayList<>();
        for (final String[] e : legend()) {
            for (final String g : e[4].split(",")) {
                if (g.equals(group)) {
                    out.add(e);
                    break;
                }
            }
        }
        return out;
    }

    private static String labelOf(String[][] table, String id) {
        for (final String[] e : table) {
            if (e[0].equals(id)) {
                return e[1];
            }
        }
        return id;
    }

    private static String hex(int argb) {
        return String.format("#%02X%02X%02X", argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF);
    }

    private static String sha256(byte[] data) throws Exception {
        final StringBuilder sb = new StringBuilder();
        for (final byte by : MessageDigest.getInstance("SHA-256").digest(data)) {
            sb.append(String.format("%02x", by));
        }
        return sb.toString();
    }

    /** fail fast：断言失败即非零退出（IOException 场景同口径抛出）。 */
    private static boolean fail(String message) {
        throw new IllegalStateException("STRUCTUREVIEWEREXPORT FAIL: " + message);
    }
}
