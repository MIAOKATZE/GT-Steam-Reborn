import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Random;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.RasterSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer.Outpost;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachineShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/**
 * S9 3D 查看器导出驱动（一次性导出 main，不进 jar，tools/ 惯例）：把 dim78 城变体
 * {@link CityVariants#ALL}（26）+ 城外中型废墟 {@link ProsperityOutpostPlacer#ALL}（6）+
 * dim78 残缺机器 {@link RuinedMachineShapes#ALL}（5，<b>P7 补上，见下</b>）+
 * dim79 破碎残骸 {@code WorldGenShatteredRuins.HUSK_SMALL/HUSK_TALL}（2，反射取形状——
 * HuskRasterPreview 同款单一权威，&lt;clinit&gt; 只构造纯 Java 的 HuskShape 不触发 MC 类初始化）
 * 导出为体素 JSON，落 {@code plan/新维度计划/review/dim1/data/structures-dim7879.json}
 * （+ 同内容的 {@code structures-dim7879.js} 包装 {@code var STRUCTURES_DATA=...}，
 * file:// 直开 index.html 时绕开 fetch CORS）。
 * <p>
 * <b>P7（plan §5 P7 / 任务包"开局基线"第 6 条：P0 把这条数据件缺口推给本片）</b>：改造前本工具只有
 * <b>三条腿</b>（city/outpost/husk = 34），名册是 39 名 ⇒ {@code asset_baseline_check.sh} 的
 * {@code STRUCT-JSON MISSING} 常驻列出缺的 5 个机型（{@code boiler_frame}/{@code chimney_base}/
 * {@code gear_mill_table}/{@code pump_base}/{@code steam_gallery}）。缺口的根因不是 JSON 少了几行，
 * 而是这里的 {@link #buildJson()} groups 数组与 {@link #main} 的 variants 计数<b>写死成三条腿</b>
 * ——所以修法是<b>加第四条腿</b>，不是手编产物（手编会让"数据件 == 生成器输出"这条不变量彻底失效）。
 * 机型腿与 husk 腿同一口径：<b>直接读形状串剪影</b>（{@link RuinedMachineShapes.Shape#charAt}），
 * 不走 {@code RuinedMachinePlacer.place}——那条路径要经 {@code resolveBlock} 取 {@code BlocksGTSR}
 * 的 Block 实例与 {@code Blocks.air}，会触发 MC 类初始化，正好破掉本工具"零 MC 执行依赖"的前提；
 * 因此机型条目的 {@code tier=0 / missingRate=0 / 无 seed}（与 husk 同），损伤掷骰属于运行期行为、
 * 不属于模板。
 * <p>
 * <b>纯 JEP 330 单文件运行，零 Minecraft 执行依赖</b>（城/outpost/machine 腿零 MC；husk 腿反射已编译类，
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
 * 城 plotSeed 选 damageTier==1 且 rotationOf==0 的最小种子（未旋转，本地坐标 1:1）；
 * outpost rot=0 显式传参、Random 种子 = SALT_PLACE（OutpostTemplateCheck 同盐）；
 * machine/husk 形状即残缺剪影、无损伤掷骰（tier=0）；</li>
 * <li>调色板 = RasterSink.defaultPalette() + GT5U 福利两键包装（CityRasterPreview.gtAccentPalette
 * 同款复制）：'X' 镀铜砖块 = sBlockCasings1 meta10，'Z' 固体钢机械外壳 = sBlockCasings2 meta0；
 * 'C' 一字两义（city/outpost = 圆石；husk = 核心位积碳壳 meta1），图例条目带 groups 域区分；
 * P7 起 'C' 再加一条 machine 域条目（机型核心位同为积碳壳 meta1，{@code RuinedMachineShapes.metaOf}）；</li>
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
        { "machine_plinth", "机座残墩" }, { "slag_heap", "渣堆" }, { "broken_pillars", "断柱群" } };

    /** outpost 6 变体中文名（A4A5 报告 §3 意象）。 */
    private static final String[][] OUTPOST_LABELS = { { "outpost_watch_post", "瞭望台残躯" },
        { "outpost_broken_aqueduct", "断拱渠段" }, { "outpost_toppled_boiler", "翻倒锅炉座" },
        { "outpost_brick_kiln", "砖窑残躯" }, { "outpost_collapsed_truss", "塌桁桥段" },
        { "outpost_gear_slag_mound", "露齿轮渣山" } };

    /** husk 2 变体中文名（WorldGenShatteredRuins javadoc 意象）。 */
    private static final String[][] HUSK_LABELS = { { "husk_small", "矮残骸（残墙断口+双残柱）" },
        { "husk_tall", "烟囱残梗" } };

    /** 残缺机器 5 机型中文名（RuinedMachineShapes javadoc ①-⑤ 意象原话）。 */
    private static final String[][] MACHINE_LABELS = { { "boiler_frame", "锅炉框架（立式方筒+东墙炉门+塌顶）" },
        { "pump_base", "泵座（矮台基座+四角柱脚+泵心积碳）" }, { "gear_mill_table", "齿轮碾磨台（碎瓷齿环包积碳磨芯）" },
        { "steam_gallery", "蒸汽管廊（墩柱+双排管槽+顶梁中跨坍塌）" }, { "chimney_base", "烟囱基座（中空烟道+顶部锯齿破口）" } };

    private final RasterSink.Palette palette;
    /** 本次构建实际写出的变体数（P7 起 EXPORT 行由它打印，不再写死总数——写死就是本次缺口的根因）。 */
    private int emitted;

    public static void main(String[] args) throws Exception {
        final File outDir = new File(
            args.length > 0 ? args[0]
                : "plan/新维度计划/review/dim1/data");
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
        System.out.println("EXPORT variants=" + exporter.emitted
            + " (city=" + CityVariants.ALL.length + " outpost=" + ProsperityOutpostPlacer.ALL.length
            + " machine=" + RuinedMachineShapes.ALL.length + " husk=2) bytes=" + first.getBytes("UTF-8").length);
        System.out.println("SHA256 json=" + jsonSha);
        System.out.println("SHA256 js=" + jsSha);
        System.out.println("EXPORT DONE -> " + jsonOut.getAbsolutePath());
    }

    StructureViewerExport() {
        // 调色板 = defaultPalette + GT5U 福利两键（CityRasterPreview.gtAccentPalette 同款包装）
        final RasterSink.Palette base = RasterSink.defaultPalette();
        this.palette = (block, meta) -> {
            final String key = block + ":" + meta;
            if ("gt5u:CasingBronzePlated:10".equals(key)) {
                return 0xFFB87333; // 镀铜砖块：铜橙
            }
            if ("gt5u:CasingSolidSteel:0".equals(key)) {
                return 0xFF8C9BA5; // 固体钢机械外壳：冷钢蓝灰
            }
            return base.applyAsInt(block, meta);
        };
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
        b.append(
            "    {\"id\":\"husk\",\"name\":\"破碎残骸骨架（dim79）\",\"source\":\"WorldGenShatteredRuins（反射形状）\",\"count\":")
            .append(HUSK_LABELS.length).append("}\n");
        b.append("  ],\n");
        b.append("  \"variants\": [\n");
        appendCityVariants(b);
        appendOutpostVariants(b);
        appendMachineVariants(b);
        appendHuskVariants(b);
        b.append("\n  ]\n");
        b.append("}\n");
        return b.toString();
    }

    /** 调色板图例：char → 颜色 → 语义名 → meta 备注；'C' 一字多义以 groups 域区分。 */
    private void appendPalette(StringBuilder b) {
        final String[][] legend = {
            { "#", "gtsr:RuinedCasing", "0", "锈壳（外壳族 meta0）", "city,outpost,machine,husk", "husk 语境 = 积碳外层（WorldGenShatteredRuins 口径）" },
            { "@", "gtsr:RuinedCasing", "1", "积碳壳（外壳族 meta1）", "city,outpost,machine", "烟黑" },
            { "%", "gtsr:RuinedCasing", "2", "碎瓷壳（外壳族 meta2）", "city,outpost,machine", "青白" },
            { "d", "gtsr:RuinDebris", "0", "轨枕残木", "city,outpost", "" },
            { "p", "gtsr:RuinDebris", "1", "管道残段", "city,outpost", "" },
            { "r", "gtsr:RuinDebris", "2", "铆接板", "city,outpost", "" },
            { "c", "gtsr:RuinDebris", "3", "烟囱残段", "city,outpost", "" },
            { "s", "gtsr:ProsperitySurface", "5", "锈石铺面", "city,outpost", "城内冻结地表 meta5" },
            { "S", "minecraft:stone", "0", "原版石", "city,outpost", "" },
            { "C", "minecraft:cobblestone", "0", "圆石", "city,outpost", "husk/machine 的 'C' 另有条目（核心位积碳壳）" },
            { "G", "minecraft:gravel", "0", "沙砾", "city,outpost", "" },
            { "B", "minecraft:iron_bars", "0", "铁栏杆", "city,outpost", "" },
            { "X", "gt5u:CasingBronzePlated", "10", "镀铜砖块 Bronze Plated Bricks", "city,outpost",
                "GT5U 福利键 GregTechAPI.sBlockCasings1 meta10" },
            { "Z", "gt5u:CasingSolidSteel", "0", "固体钢机械外壳 Solid Steel Machine Casing", "city,outpost",
                "GT5U 福利键 GregTechAPI.sBlockCasings2 meta0" },
            { "C", "gtsr:RuinedCasing", "1", "husk 核心位（积碳壳 meta1）", "husk", "仅 dim79 husk；无 TE 无箱子" },
            { "C", "gtsr:RuinedCasing", "1", "machine 核心位（积碳壳 meta1）", "machine",
                "仅 dim78 机型 'C' 位；RuinedMachineShapes.metaOf 同款，无 TE 无控制器无修复无战利品" } };
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

    private void appendCityVariants(StringBuilder b) throws Exception {
        if (CityVariants.ALL.length != 26) {
            fail("expected 26 city variants, got " + CityVariants.ALL.length);
        }
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
                DEFAULT_TIER, CityVariants.MISSING_RATES[DEFAULT_TIER], plotSeed, grid, ",");
        }
    }

    private void appendOutpostVariants(StringBuilder b) {
        if (ProsperityOutpostPlacer.ALL.length != 6) {
            fail("expected 6 outposts, got " + ProsperityOutpostPlacer.ALL.length);
        }
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
                DEFAULT_TIER, CityVariants.MISSING_RATES[DEFAULT_TIER], PLACE_SEED, grid, ",");
        }
    }

    /** husk 形状反射（HuskRasterPreview 同款单一权威；'#'=积碳外层 meta0、'C'=核心位积碳壳 meta1）。 */
    private void appendHuskVariants(StringBuilder b) throws Exception {
        final Class<?> gen = Class.forName("com.miaokatze.gtsr.common.dimension.shattered.WorldGenShatteredRuins");
        final Class<?> huskCls = Class.forName("com.miaokatze.gtsr.common.dimension.shattered.WorldGenShatteredRuins$HuskShape");
        final Method charAt = huskCls.getMethod("charAt", int.class, int.class, int.class);
        int done = 0;
        for (final String fieldName : new String[] { "HUSK_SMALL", "HUSK_TALL" }) {
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
                done == 0 ? "," : "");
            done++;
        }
        if (done != 2) {
            fail("expected 2 husks, got " + done);
        }
    }

    /**
     * 残缺机器 5 机型（P7 第四条腿）。与 husk 腿同口径：<b>直接读形状串剪影</b>，不走放置器路径
     * （放置器要解析 {@code BlocksGTSR}/{@code Blocks.air} 的 Block 实例 ⇒ 会触发 MC 类初始化，
     * 破掉本工具"零 MC 执行依赖"的前提）。'C' 核心位在此域就是积碳壳 meta1（与 '@' 同方块，
     * 但模板语义不同，故图例单列一条 machine 域的 'C'）。
     */
    private void appendMachineVariants(StringBuilder b) {
        if (RuinedMachineShapes.ALL.length != 5) {
            fail("expected 5 machine shapes, got " + RuinedMachineShapes.ALL.length);
        }
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
                ",");
            done++;
        }
        if (done != RuinedMachineShapes.ALL.length) {
            fail("expected " + RuinedMachineShapes.ALL.length + " machines, got " + done);
        }
    }

    /** 单变体 JSON 对象（含 layers 三维矩阵；seed<0 时省略字段）。 */
    private void appendVariant(StringBuilder b, String id, String label, String group, String category, int sizeX,
        int sizeY, int sizeZ, int tier, int missingRate, long seed, char[][][] grid, String trailing) {
        assertGridKnownChars(group, grid);
        this.emitted++;
        b.append("    {\"id\":\"").append(id).append("\",\"label\":\"").append(label).append("\",\"group\":\"")
            .append(group).append("\",\"category\":\"").append(category).append("\",\"sizeX\":").append(sizeX)
            .append(",\"sizeY\":").append(sizeY).append(",\"sizeZ\":").append(sizeZ).append(",\"tier\":").append(tier)
            .append(",\"missingRate\":").append(missingRate);
        if (seed >= 0) {
            b.append(",\"seed\":").append(seed);
        }
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
            keys.add(e[1]); // e[0]=groups 域，e[1]=char
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

    /** group 可用 char 集合（与 appendPalette 的 groups 域一致）。 */
    private static ArrayList<String[]> paletteLegendFor(String group) {
        final ArrayList<String[]> out = new ArrayList<>();
        for (final String[] e : PALETTE_GROUPS) {
            for (final String g : e[0].split(",")) {
                if (g.equals(group)) {
                    out.add(e);
                    break;
                }
            }
        }
        return out;
    }

    /** 与 appendPalette 平行的 (groups, char) 表（仅用于断言面；必须与图例的 groups 域逐行一致）。 */
    private static final String[][] PALETTE_GROUPS = { { "city,outpost,machine,husk", "#" },
        { "city,outpost,machine", "@" }, { "city,outpost,machine", "%" }, { "city,outpost", "d" },
        { "city,outpost", "p" }, { "city,outpost", "r" }, { "city,outpost", "c" }, { "city,outpost", "s" },
        { "city,outpost", "S" }, { "city,outpost", "C" }, { "city,outpost", "G" }, { "city,outpost", "B" },
        { "city,outpost", "X" }, { "city,outpost", "Z" }, { "husk", "C" }, { "machine", "C" } };

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
