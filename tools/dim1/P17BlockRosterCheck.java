import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

import com.miaokatze.gtsr.common.blocks.BlockProsperityFalling;
import com.miaokatze.gtsr.common.blocks.BlockProsperityStone;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityCanopyLeaves;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityNaturalBase;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRustLeaves;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRustLog;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityTuft;

/**
 * P17-SB1「新增方块的注册与资产面」自证断言（plan §1-S-B 交付判据：不接受"清单里加了名字"）。
 * <p>
 * 名册现量：<b>17 方块 / 22 张 32 档贴图 / 2 份 lang × 17 tile 键 + 1 item 键</b>
 * （P17-SB1 的 12/18 + P17-S-B2 的沙砾 3/3 + T2/T8 的 prosperityStone + P19-U1 的 abyssalFluid 流体与桶物品）。
 * 钉六组事实，全部离线、零随机、读数幂等（连跑两次逐位一致）：
 * <ol>
 * <li><b>注册链</b>：{@code BlocksGTSR} 12 字段声明 + {@code BlockLoader} 逐块
 * {@code new <类>(...)}/{@code GameRegistry.registerBlock(BlocksGTSR.<f>, "<注册名>")} 文本
 * （单 ID 无 ItemBlock、无数字 ID 形参＝"ID 自动序号 + meta 恒 0"申报的源级钉）；</li>
 * <li><b>运行时实例</b>：{@code SurfaceHarness.blockFamily()} 真实装配后 12 个字段逐非 null，
 * 类族（log→BlockProsperityRustLog / 冠→BlockProsperityCanopyLeaves（含 instanceof 锈叶，tint 继承）/
 * 花草→BlockProsperityTuft）、材质（wood/leaves/plants）、unlocalizedName == "tile."+注册名；</li>
 * <li><b>贴图可解析</b>：18 个 {@code gtsr:<path>} 图标名在<b>仓库资源根</b>（相对路径文件）与
 * <b>classpath</b>（{@code getResource("assets/gtsr/textures/blocks/<path>.png")}，运行档需把
 * {@code src/main/resources} 挂上 classpath，见 surface_checks.sh 本步）两侧都能取到字节；</li>
 * <li><b>像素纪律</b>：逐张 BufferedImage 32×32 正方形；实测色数（RGBA 去重，透明底计入）
 * == {@code design32.json} 档案 measured == 展示页标注（色数由 check_feedback 钉，此处只钉档案）；</li>
 * <li><b>lang 齐备无多余</b>：两份 .lang 对 12 个注册名各含恰好一条 {@code tile.<注册名>.name=}，
 * 且不存在 {@code tile.<注册名>.<数字>.name=}（meta 恒 0 申报的反向钉）与重复行；</li>
 * <li><b>纪律 1 反向钉</b>：12 个新方块在 {@code SurfaceGateUnifyCheck.MUST_NOT_PASS} 全员在列，
 * 且 {@code framework/SurfaceGate.java} 源文本不含任何新名（DIM78_SIZE 恒 5 的名册面证据）。</li>
 * </ol>
 * 退出码 0=全绿；1=任一断言红。运行（快档挂点见 {@code tools/dim1/surface_checks.sh} 的
 * {@code [3sb1]} 步）：
 * {@code java -cp "src/main/resources;tools-classes;..." P17BlockRosterCheck}。
 */
public final class P17BlockRosterCheck {

    /** 申报名册：BlocksGTSR 字段 → {注册名, 类族标记(log/leaf/flower|grass), 图标贴图名...}。 */
    private static final Map<String, String[]> ROSTER = new LinkedHashMap<>();
    static {
        ROSTER.put("prosperityCopperLog", new String[] { "ProsperityCopperLog", "log",
            "prosperity_copper_log_side", "prosperity_copper_log_top" });
        ROSTER.put("prosperityCopperLeaves", new String[] { "ProsperityCopperLeaves", "leaf",
            "prosperity_copper_leaves_side", "prosperity_copper_leaves_top" });
        ROSTER.put("prosperityBrassLog", new String[] { "ProsperityBrassLog", "log",
            "prosperity_brass_log_side", "prosperity_brass_log_top" });
        ROSTER.put("prosperityBrassLeaves", new String[] { "ProsperityBrassLeaves", "leaf",
            "prosperity_brass_leaves_side", "prosperity_brass_leaves_top" });
        ROSTER.put("prosperityMarshLog", new String[] { "ProsperityMarshLog", "log",
            "prosperity_marsh_log_side", "prosperity_marsh_log_top" });
        ROSTER.put("prosperityMarshLeaves", new String[] { "ProsperityMarshLeaves", "leaf",
            "prosperity_marsh_leaves_side", "prosperity_marsh_leaves_top" });
        ROSTER.put("prosperityFlowerRust", new String[] { "ProsperityFlowerRust", "plant",
            "prosperity_flower_rust" });
        ROSTER.put("prosperityFlowerPatina", new String[] { "ProsperityFlowerPatina", "plant",
            "prosperity_flower_patina" });
        ROSTER.put("prosperityFlowerBrass", new String[] { "ProsperityFlowerBrass", "plant",
            "prosperity_flower_brass" });
        ROSTER.put("prosperityFlowerMarsh", new String[] { "ProsperityFlowerMarsh", "plant",
            "prosperity_flower_marsh" });
        ROSTER.put("prosperityTuftSedge", new String[] { "ProsperityTuftSedge", "plant",
            "prosperity_tuft_sedge" });
        ROSTER.put("prosperityTuftBristle", new String[] { "ProsperityTuftBristle", "plant",
            "prosperity_tuft_bristle" });
        // P17-S-B2 沙/砂砾 3 件（B 档 BlockProsperityNaturalBase 吃贴图名 ⇒ 零新 Java 类；全部非 top）。
        // v1.20.39 T2/T8 重钉（plan §3.9 G8）：coarseSand/riverGravel 切 BlockProsperityFalling
        //（注册名/贴图不变，材质仍 ground），类族标记 base→falling。
        ROSTER.put("prosperitySilicaSand", new String[] { "ProsperitySilicaSand", "base",
            "prosperity_silica_sand" });
        ROSTER.put("prosperityCoarseSand", new String[] { "ProsperityCoarseSand", "falling",
            "prosperity_coarse_sand" });
        ROSTER.put("prosperityRiverGravel", new String[] { "ProsperityRiverGravel", "falling",
            "prosperity_river_gravel" });
        // v1.20.39 T2/T8 名册同步（plan §3.7 G4）：prosperityStone 地底石化主体石入册。
        ROSTER.put("prosperityStone", new String[] { "ProsperityStone", "stone",
            "prosperity_stone" });
        // v1.20.40 P19-U1（plan §I）名册同步 16→17：深渊执念流体方块入册——注册走 initAbyssalFluid
        //（CommonProxy.init 段，晚于 GT preInit 的 FluidRegistry 装配），离线 harness 无 GT 材料 ⇒
        // 流体实例必为 null，运行时实例/32×32 贴图两档不适用（见 "fluid" 家族分支的专属钉）；
        // 流体贴图由 GT 材料管线自动生成（autogen+tint），不在 blocks/ 32×32 名册内。
        ROSTER.put("abyssalFluid", new String[] { "AbyssalFluid", "fluid" });
    }

    private static final String BLOCKS = "src/main/java/com/miaokatze/gtsr/common/blocks/BlocksGTSR.java";
    private static final String LOADER = "src/main/java/com/miaokatze/gtsr/loader/BlockLoader.java";
    private static final String SURFACE_GATE = "src/main/java/com/miaokatze/gtsr/common/dimension"
        + "/framework/SurfaceGate.java";
    private static final String UNIFY = "tools/dim1/SurfaceGateUnifyCheck.java";
    private static final String MATRIX = "tools/dim1/SurfaceBiomeMatrixCheck.java";
    private static final String HARNESS = "tools/dim1/SurfaceHarness.java";
    private static final String DESIGN32 = "tools/artgen/dim7879/design32.json";
    private static final String[] LANGS = { "src/main/resources/assets/gtsr/lang/en_US.lang",
        "src/main/resources/assets/gtsr/lang/zh_CN.lang" };
    private static final String TEX_ROOT = "src/main/resources/assets/gtsr/textures/blocks/";
    private static final String TEX_CP = "assets/gtsr/textures/blocks/";

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();

    private P17BlockRosterCheck() {}

    public static void main(String[] args) throws Exception {
        quietLogging();
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();

        final String blocks = read(BLOCKS);
        final String loader = read(LOADER);
        final String gate = read(SURFACE_GATE);
        final String unify = read(UNIFY);
        final String matrix = read(MATRIX);
        final String harness = read(HARNESS);
        final JsonObject design = new JsonParser().parse(read(DESIGN32)).getAsJsonObject()
            .get("entries").getAsJsonObject();

        // —— 1+2. 注册链文本钉 + 运行时实例钉（v1.20.40 P19 口径 17 方块 = 原 16 + abyssalFluid） ——
        for (final Map.Entry<String, String[]> e : ROSTER.entrySet()) {
            final String field = e.getKey();
            final String reg = e.getValue()[0];
            final String family = e.getValue()[1];
            final boolean fluid = "fluid".equals(family);
            check(blocks.contains("public static Block " + field + ";"),
                "BlocksGTSR 缺字段声明 " + field);
            check(loader.contains("BlocksGTSR." + field + " = new ")
                && loader.contains("GameRegistry.registerBlock(BlocksGTSR." + field + ", \"" + reg + "\");"),
                "BlockLoader 缺实例化或注册 " + field);
            if (!fluid) {
                check(loader.contains("\"gtsr:" + e.getValue()[2] + "\""),
                    "BlockLoader 注册段缺贴图名 gtsr:" + e.getValue()[2]);
            }
            // 单 ID 无 ItemBlock / 无数字 ID（meta 恒 0 + FML 自动序号申报的源级反向钉）
            check(!loader.contains("BlocksGTSR." + field + ", ItemBlock"),
                field + " 不得走 ItemBlock meta 族注册");
            final Block b = fieldOf(field);
            if (fluid) {
                // 流体家族专属钉（P19-U1）：离线 harness 无 GT 材料流体 ⇒ 实例恒 null（失败开放
                // 注册路径的设计态），改钉源级三件——实例化类族 + FluidRegistry 取流体 + 时序门
                //（initAbyssalFluid 的失败开放告警）。
                check(b == null, "离线 harness 下 " + field + " 应为 null（FluidRegistry 依赖，若非 null 说明"
                    + "装配面变了，本分支的源级钉要跟着复核）");
                check(loader.contains("new BlockAbyssalFluid(fluid)"), field + " 实例化类族应为 BlockAbyssalFluid");
                // needle 不带 FluidRegistry 前缀：源里 FluidRegistry 与 .getFluid 被 spotless 折行
                check(loader.contains("getFluid(\"abyssal_obsession\")"),
                    field + " 注册段缺材料流体名 abyssal_obsession");
                check(loader.contains("abyssal fluid material missing from FluidRegistry"),
                    field + " 缺失败开放告警（材料链降级零崩溃面）");
            } else {
                check(b != null, "SurfaceHarness.blockFamily 未装配 " + field + "（静默面同步缺失）");
            }
            if (!fluid && b != null) {
                check(harness.contains("BlocksGTSR." + field + " = new "),
                    "SurfaceHarness 文本缺 " + field + " 装配行");
                if ("log".equals(family)) {
                    check(b instanceof BlockProsperityRustLog, field + " 类族应为 BlockProsperityRustLog");
                    check(material(b) == Material.wood, field + " 材质应为 wood");
                } else if ("leaf".equals(family)) {
                    check(b instanceof BlockProsperityCanopyLeaves && b instanceof BlockProsperityRustLeaves,
                        field + " 类族应为 BlockProsperityCanopyLeaves（继承锈叶 tint）");
                    check(material(b) == Material.leaves, field + " 材质应为 leaves");
                } else if ("base".equals(family)) {
                    check(b instanceof BlockProsperityNaturalBase, field + " 类族应为 BlockProsperityNaturalBase");
                    check(material(b) == Material.ground, field + " 材质应为 ground");
                } else if ("falling".equals(family)) {
                    // T2/T8：G8 重力沙砾（BlockFalling 派生），注册名与材质观感不变。
                    check(b instanceof BlockProsperityFalling, field + " 类族应为 BlockProsperityFalling");
                    check(material(b) == Material.ground, field + " 材质应为 ground");
                } else if ("stone".equals(family)) {
                    // T2/T8：G4 地底石化主体石（Material.rock，pickaxe 档）。
                    check(b instanceof BlockProsperityStone, field + " 类族应为 BlockProsperityStone");
                    check(material(b) == Material.rock, field + " 材质应为 rock");
                } else {
                    check(b instanceof BlockProsperityTuft, field + " 类族应为 BlockProsperityTuft");
                    check(material(b) == Material.plants, field + " 材质应为 plants");
                }
                check(("tile." + reg).equals(b.getUnlocalizedName()),
                    field + " unlocalizedName=" + b.getUnlocalizedName() + " != tile." + reg);
            }
            // —— 6. 纪律 1 反向钉（表面名册面：流体走专属反向钉） ——
            if (fluid) {
                // P19-U1：流体不是地表 top——消费面 = placer/provider 水体材料，不进 SurfaceGate
                // 的 top 名册，也不进两份 surface 判据工具的表面名册（三处零命中是正向纪律）。
                check(!unify.contains("\"gtsr:" + field + "\""),
                    "SurfaceGateUnifyCheck 名册面不得出现流体 " + field);
                check(!matrix.contains("\"" + field + "\""),
                    "SurfaceBiomeMatrixCheck NATURAL_BLOCKS 不得出现流体 " + field);
            } else {
                check(unify.contains("\"gtsr:" + field + "\""), "MUST_NOT_PASS 缺 " + field);
                check(matrix.contains("\"" + field + "\""), "NATURAL_BLOCKS 缺 " + field);
            }
            check(!gate.contains(reg) && !gate.contains(field),
                "SurfaceGate 源文本不得出现新方块 " + field + "（DIM78_SIZE 恒 5 纪律）");
        }

        // —— 1b. v1.20.40 P19-U1 桶物品面（ItemAbyssalBucket；GTSRItemList 枚举注册 + 贴图 + 两份 lang） ——
        final String itemLoader = read("src/main/java/com/miaokatze/gtsr/loader/ItemLoader.java");
        check(itemLoader.contains("new ItemAbyssalBucket(BlocksGTSR.abyssalFluid)")
            && itemLoader.contains("\"AbyssalObsessionBucket\""),
            "ItemLoader 缺 AbyssalObsessionBucket 注册（ItemAbyssalBucket(BlocksGTSR.abyssalFluid)）");
        check(itemLoader.contains("registerFluidContainer"),
            "ItemLoader 缺桶容器互认（FluidContainerRegistry 1000mB ↔ 原版空桶）");
        final File bucketTex = new File("src/main/resources/assets/gtsr/textures/items/AbyssalObsessionBucket.png");
        check(bucketTex.isFile(), "缺桶物品贴图 assets/gtsr/textures/items/AbyssalObsessionBucket.png");

        // —— 3+4. 贴图解析 + 像素纪律（classpath 与仓库资源根双通道；T8 起 22 张） ——
        int texCount = 0;
        for (final Map.Entry<String, String[]> e : ROSTER.entrySet()) {
            for (int i = 2; i < e.getValue().length; i++) {
                final String tex = e.getValue()[i];
                texCount++;
                final File f = new File(TEX_ROOT + tex + ".png");
                check(f.isFile(), "仓库资源根缺贴图 " + TEX_ROOT + tex + ".png");
                check(P17BlockRosterCheck.class.getClassLoader().getResource(TEX_CP + tex + ".png") != null,
                    "classpath 不可解析贴图 " + TEX_CP + tex + ".png"
                        + "（本步 classpath 必须含 src/main/resources；缺＝挂点退化，判红不静默）");
                if (f.isFile()) {
                    final BufferedImage im = ImageIO.read(f);
                    check(im != null, "ImageIO 解码失败 " + tex);
                    if (im != null) {
                        check(im.getWidth() == 32 && im.getHeight() == 32 && im.getWidth() == im.getHeight(),
                            tex + " 非 32×32 正方形：" + im.getWidth() + "x" + im.getHeight());
                        final java.util.Set<Integer> hist = new java.util.HashSet<>();
                        for (int y = 0; y < im.getHeight(); y++) {
                            for (int x = 0; x < im.getWidth(); x++) {
                                hist.add(im.getRGB(x, y));
                            }
                        }
                        final JsonEntry de = jsonEntry(design, tex);
                        check(de != null, "design32 档案缺条目 " + tex);
                        if (de != null) {
                            check(hist.size() == de.distinctRgba,
                                tex + " 实测色数 " + hist.size() + " != 档案 measured " + de.distinctRgba);
                        }
                    }
                }
            }
        }
        check(texCount == 22, "贴图名册数 != 22（T8 加 prosperity_stone）：" + texCount);

        // —— 5. lang 齐备无多余（两份 ×12，重复行与 meta 键反向钉） ——
        for (final String lang : LANGS) {
            final List<String> lines = Files.readAllLines(Paths.get(lang), StandardCharsets.UTF_8);
            for (final Map.Entry<String, String[]> e : ROSTER.entrySet()) {
                final String exact = "tile." + e.getValue()[0] + ".name=";
                int exactHits = 0;
                int anyHits = 0;
                for (final String ln : lines) {
                    if (!ln.startsWith("tile." + e.getValue()[0])) {
                        continue;
                    }
                    anyHits++;
                    if (ln.startsWith(exact)) {
                        exactHits++;
                    }
                }
                check(exactHits == 1, lang + " 的 tile." + e.getValue()[0] + ".name= 出现 " + exactHits + " 次（须恰 1）");
                check(anyHits == 1, lang + " 的 tile." + e.getValue()[0] + "* 出现 " + anyHits + " 次（meta 形/多余键＝红）");
            }
        }
        // P19-U1：桶物品 lang 键（两份各恰 1 条；item 域，不在 tile 名册循环内）
        for (final String lang : LANGS) {
            final List<String> lines = Files.readAllLines(Paths.get(lang), StandardCharsets.UTF_8);
            int bucketHits = 0;
            for (final String ln : lines) {
                if (ln.startsWith("item.AbyssalObsessionBucket.name=")) {
                    bucketHits++;
                }
            }
            check(bucketHits == 1, lang + " 的 item.AbyssalObsessionBucket.name= 出现 " + bucketHits + " 次（须恰 1）");
        }

        if (!FAILURES.isEmpty()) {
            for (final String f : FAILURES) {
                System.out.println("  FAIL " + f);
            }
            System.out.println("P17 BLOCK ROSTER FAIL: passed=" + passed + " failed=" + FAILURES.size());
            System.exit(1);
        }
        System.out.println("P17 BLOCK ROSTER PASS: blocks=17 textures=22 langs=2x17(+bucket item)"
            + " family=log/leaf(canopy-tint 继承)/plant(tuft)/base(砂)/falling(砾, G8)/stone(石化, G4)"
            + "/fluid(abyssal, P19-U1 离线实例 null 态源级钉)"
            + " 注册链+运行时+字节+lang+纪律1 全钉 assertions=" + passed);
    }

    private static final class JsonEntry {
        int distinctRgba;
    }

    private static JsonEntry jsonEntry(JsonObject design, String key) {
        final JsonElement el = design.get(key);
        if (el == null) {
            return null;
        }
        final JsonEntry out = new JsonEntry();
        out.distinctRgba = el.getAsJsonObject().get("measured").getAsJsonObject().get("distinct_rgba")
            .getAsInt();
        return out;
    }

    private static Material material(Block b) {
        try {
            final java.lang.reflect.Field f = Block.class.getDeclaredField("blockMaterial");
            f.setAccessible(true);
            return (Material)f.get(b);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Block fieldOf(String field) {
        try {
            return (Block)BlocksGTSR.class.getField(field).get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void quietLogging() {
        try {
            final java.nio.file.Path cfg = Paths.get("temp", "p17-sb1-log4j2.xml");
            Files.createDirectories(cfg.getParent());
            Files.write(cfg, ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Configuration status=\"OFF\"><Loggers><Root level=\"OFF\"/></Loggers></Configuration>\n")
                    .getBytes(StandardCharsets.UTF_8));
            System.setProperty("log4j.configurationFile", cfg.toAbsolutePath().toString());
        } catch (Exception e) {
            System.out.println("NOTE: log4j quiet bootstrap failed (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static void check(boolean ok, String msg) {
        if (ok) {
            passed++;
        } else {
            FAILURES.add(msg);
        }
    }
}
