import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityWorldGenerator;
import com.miaokatze.gtsr.config.Config;

/**
 * <b>P5 判据 2/3 的 H-3 钉：每 chunk 轮廓（件）数与落块双上限</b>
 * （plan §5 P5「成功判据：{@code ContourBudgetCheck}（K ≤ 阈值）」/ §2.2 H-3 /
 * §7.1 已锁定 U3「K 与落块双上限 + 竖向件摘出」/ §2.4 判据 5「新判据一律先 RED 后 GREEN」）。
 * <p>
 * ═══ 列名申报（本工具的第二份真值；改期望必须连同 plan 与 Config 注释一起改）═══
 * <pre>
 *  A 声明级：Config 的六个散布键 + 四件型权重 == 申报值（竖向件默认关、K=8、落块=24、掷点=64、
 *    窗上限=2）。<b>人为放宽（例如 K 8→64、窗上限 2→999）本组立刻红</b>，不依赖任何采样。
 *  A2 源级（防"又改回硬编码"）：ProsperitySurfaceScatter.java 去注释后
 *    ① 不得出现 {@code BUDGET_PER_CHUNK} 或 {@code = { 30, 25, 30, 15 }} 形状的私有常量表；
 *    ② 必须逐字引用五个 Config 键（各 ≥1 次）；
 *    ③ {@code findSurfaceY} 定义数必须仍为 0（P3 合并件不得被复活）；
 *    ④ 不得出现 {@code >>> 33}（P3 手搓哈希红线，窗排序哈希必须走 GTSRWorldgenHash）。
 *  A3 单一真值（判据 4）：机器概率分母的具体数字在 src/main/java 只剩 Config 的"定义+注释"两处，
 *    其余文件出现 "/24" "/16" 形态的机器分母字面量必须为 0；全仓 Javadoc 残留上界申报 1 处
 *    （framework/SurfaceGate.java 的口径注释，非本片允许路径——修成 0 也绿，只打印位置）。
 *  B 行为级：真实编排链（outpost→互斥机器→散布）上 8 seed × 8 个 16×16 窗实测，默认档的
 *    件数均值/上界、落块均值、有内容 chunk 占比落在申报带内；同一份样本里的"现状行"
 *    必须显著更密（反假绿：带若宽到能同时容纳现状，就失去意义）。
 *  C 可回退：把四键设回旧值 ⇒ 柱/chunk 必须回到冒烟 E6 的 8.8-10.2 带、落块回到 95-105 带，
 *    且其落块摘要与"现状行"逐位相同（同 JVM 内两行的 digest 相等）。
 *    BASE 树（改造前快照编译）与 AFTER 回退档的<b>跨进程</b>摘要对拍在
 *    {@code tools/dim1/surface_checks.sh} 的 [10] 步，不在本类里。
 *  D 灵敏度自检：在进程内把 K 放宽到 64 再跑一遍，件数/落块均值必须<b>超出</b> B 组申报带
 *    （若本工具对放宽不敏感，D 组自己判红）。
 * </pre>
 * <b>用法</b>：{@code java ContourBudgetCheck [seeds] [regionsPerSeed]}（默认 8 × 8 = 16384 chunk）
 * 退出码：0 = 全绿；1 = 有申报项被破坏；2 = 环境不可用。
 */
public final class ContourBudgetCheck {

    // ═════════════════════════ A 组：声明级申报（本工具自带的第二份真值）═════════════════════════

    static final int DECL_ATTEMPTS = 64;
    static final int DECL_CONTOURS = 8;
    static final int DECL_BLOCKS = 24;
    static final boolean DECL_VERTICAL = false;
    static final int DECL_WINDOW_CAP = 2;
    static final int[] DECL_WEIGHTS = { 30, 25, 30, 15 };

    /** H-3 硬上界：K 折算到最大群系权重（荒漠 1.5）后的件数天花板。 */
    static int contourHardCeiling() {
        return Math.round(DECL_CONTOURS * maxScatterWeight());
    }

    /** 落块天花板（同上折算）。 */
    static int blockHardCeiling() {
        return Math.round(DECL_BLOCKS * maxScatterWeight());
    }

    private static float maxScatterWeight() {
        float m = 0;
        for (final float w : ProsperityWorldGenerator.SCATTER_WEIGHTS) {
            m = Math.max(m, w);
        }
        return m;
    }

    // ═════════════════════════════════ B/C 组申报带 ═════════════════════════════════

    /** 默认档件数均值带（实测 9.5 上下；带宽 ±1.0 吸收 seed 方差，但装不下"现状 64.9"）。 */
    private static final double BAND_CONTOUR_MEAN_MIN = 8.0D, BAND_CONTOUR_MEAN_MAX = 11.0D;
    /** 默认档落块均值带（实测 11.8；装不下现状 99.1，也装不下"再砍一半"）。 */
    private static final double BAND_BLOCK_MEAN_MIN = 10.0D, BAND_BLOCK_MEAN_MAX = 14.0D;
    /** 有内容 chunk 占比下界（摘竖向件 + K=8 后仍要"看得见痕迹"）。 */
    private static final double BAND_COVERAGE_MIN = 95.0D;
    /** 现状行的柱带：冒烟 E6 实测 8.8-10.2 柱/chunk（离线口径＝含 chimney 落块的列数）。 */
    private static final double BAND_BASELINE_CHIMNEY_MIN = 8.0D, BAND_BASELINE_CHIMNEY_MAX = 11.0D;
    /** 现状行的落块带：P4 权威样本 100.58 块/chunk（不同样本原点，±10% 容差）。 */
    private static final double BAND_BASELINE_BLOCK_MIN = 90.0D, BAND_BASELINE_BLOCK_MAX = 110.0D;

    private static final Pattern MACHINE_DENOM_LITERAL = Pattern.compile("/\\s*(?:1[0-9]|[2-9][0-9])\\b");

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        final int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 8;
        final int regions = args.length > 1 ? Integer.parseInt(args[1]) : 8;
        Dim78ScatterDensityCheck.bootstrap();

        // —— 先记"生产默认值"，再做任何写操作（B/C/D 组都依赖这个快照）——
        final int defAttempts = Config.prosperityScatterAttemptsPerChunk;
        final int defContours = Config.prosperityScatterContoursPerChunk;
        final int defBlocks = Config.prosperityScatterBlocksPerChunk;
        final boolean defVertical = Config.prosperityScatterVerticalPieces;
        final int defWindowCap = Config.prosperityScatterWindowRepeatCap;
        final int[] defWeights = { Config.prosperityScatterWeightSleeper, Config.prosperityScatterWeightPipe,
            Config.prosperityScatterWeightRivetPlate, Config.prosperityScatterWeightChimney };

        checkGroupA(defAttempts, defContours, defBlocks, defVertical, defWindowCap, defWeights);
        checkGroupA2();
        checkGroupA3();

        // —— B/C/D：一次采样跑四行（默认档、现状行、回退档、放宽档），行内差只有散布 ——
        final List<Dim78ScatterDensityCheck.Row> rows = Arrays.asList(
            new Dim78ScatterDensityCheck.Row("DEFAULT-生产默认档", defContours, defBlocks, defAttempts,
                Boolean.valueOf(defVertical), defWindowCap),
            new Dim78ScatterDensityCheck.Row("BASELINE-现状柱阵", DECL_ATTEMPTS, 0, DECL_ATTEMPTS, Boolean.TRUE, 0),
            // 回退档故意把件数写成 Config 上界 1024（不是 64）：若件数天花板真的"高于掷点上限即不约束"，
            // 它的落块摘要必须与现状行逐位相同——这样 C 组的摘要相等才是有内容的断言，而不是同一行跑两遍。
            new Dim78ScatterDensityCheck.Row("ROLLBACK-四键设回旧值", 1024, 0, DECL_ATTEMPTS, Boolean.TRUE, 0),
            new Dim78ScatterDensityCheck.Row("RELAX-K放宽到64(灵敏度)", 64, 0, DECL_ATTEMPTS, Boolean.valueOf(
                defVertical), defWindowCap));
        final Dim78ScatterDensityCheck.Sampler sampler =
            new Dim78ScatterDensityCheck.Sampler(seeds, regions, 16, rows);
        sampler.run();
        final Dim78ScatterDensityCheck.Stats def = sampler.stats("DEFAULT-生产默认档");
        final Dim78ScatterDensityCheck.Stats base = sampler.stats("BASELINE-现状柱阵");
        final Dim78ScatterDensityCheck.Stats back = sampler.stats("ROLLBACK-四键设回旧值");
        final Dim78ScatterDensityCheck.Stats relax = sampler.stats("RELAX-K放宽到64(灵敏度)");

        checkGroupB(def, base, sampler);
        checkGroupC(base, back);
        checkGroupD(def, relax);

        // 复位（本进程后续若还有别的用器不吃这些静态字段，但保持"写过的要还原"纪律）
        Config.prosperityScatterAttemptsPerChunk = defAttempts;
        Config.prosperityScatterContoursPerChunk = defContours;
        Config.prosperityScatterBlocksPerChunk = defBlocks;
        Config.prosperityScatterVerticalPieces = defVertical;
        Config.prosperityScatterWindowRepeatCap = defWindowCap;

        report(def, base, back, relax, sampler);
        if (FAILURES.isEmpty()) {
            System.out.println("CONTOUR BUDGET CHECK PASS: assertions=" + passed);
            System.exit(0);
        }
        System.out.println("CONTOUR BUDGET CHECK FAIL: " + FAILURES.size() + " assertion(s) failed, passed=" + passed);
        System.exit(1);
    }

    // ══════════════════════════════════ A 组 ══════════════════════════════════

    private static void checkGroupA(int attempts, int contours, int blocks, boolean vertical, int windowCap,
        int[] weights) {
        check(attempts == DECL_ATTEMPTS, "A 掷点上限 = " + DECL_ATTEMPTS + "（实测 " + attempts + "）");
        check(contours == DECL_CONTOURS,
            "A H-3 件数 K = " + DECL_CONTOURS + "（实测 " + contours + "；放宽即红）");
        check(blocks == DECL_BLOCKS, "A H-3 落块上限 = " + DECL_BLOCKS + "（实测 " + blocks + "）");
        check(vertical == DECL_VERTICAL,
            "A 竖向件默认摘出（prosperityScatterVerticalPieces=" + DECL_VERTICAL + "，实测 " + vertical + "）");
        check(windowCap == DECL_WINDOW_CAP,
            "A H-2 窗重复上限 = " + DECL_WINDOW_CAP + "（实测 " + windowCap + "；放宽即红）");
        check(Arrays.equals(weights, DECL_WEIGHTS),
            "A 件型权重表 = " + Arrays.toString(DECL_WEIGHTS) + "（实测 " + Arrays.toString(weights) + "）");
        check(Config.prosperityMachineChance == 16,
            "A3 机器分母代码默认 = 16（设计基线，实机 24 属外部覆盖；实测 " + Config.prosperityMachineChance + "）");
    }

    /** A2 源级：散布层的预算/权重必须真的只剩 Config 一个出处。 */
    private static void checkGroupA2() throws Exception {
        final String path = "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/"
            + "ProsperitySurfaceScatter.java";
        final String code = stripComments(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
            "UTF-8"));
        check(!code.contains("BUDGET_PER_CHUNK"), "A2 私有 BUDGET_PER_CHUNK 常量已消失（改由 Config 取值）");
        check(!code.contains("private static final int[] WEIGHTS"),
            "A2 私有 WEIGHTS 常量表已消失（改由 Config 取值）");
        check(!Pattern.compile("=\\s*\\{\\s*30\\s*,\\s*25\\s*,\\s*30\\s*,\\s*15")
            .matcher(code)
            .find(), "A2 权重表未被重新内联");
        final String[] keys = { "prosperityScatterAttemptsPerChunk", "prosperityScatterContoursPerChunk",
            "prosperityScatterBlocksPerChunk", "prosperityScatterVerticalPieces",
            "prosperityScatterWeightSleeper", "prosperityScatterWeightPipe", "prosperityScatterWeightRivetPlate",
            "prosperityScatterWeightChimney", "prosperityScatterWindowRepeatCap" };
        for (final String k : keys) {
            check(code.contains("Config." + k), "A2 散布层引用 Config." + k);
        }
        check(count(code, "int\\s+findSurfaceY\\s*\\(") == 0,
            "A2 P3 合并列扫未被复活（findSurfaceY 定义数=0，实测 "
                + count(code, "int\\s+findSurfaceY\\s*\\(") + "）");
        check(!code.contains(">>> 33"), "A2 无手搓哈希（P3 红线：窗排序必须走 GTSRWorldgenHash）");
        // spotless 会把成员调用折行（GTSRWorldgenHash 与 .splitmix64( 被拆到两行），字面量
        // contains 因此少数 ⇒ 计数改走"空白不敏感"的同一语义（P7c 实测：格式化后本条曾假红）。
        final int smix = count(code, "GTSRWorldgenHash\\s*\\.\\s*splitmix64\\s*\\(");
        check(smix > 0, "A2 窗排序哈希走框架唯一件（空白不敏感计数，实测 " + smix + " 处）");
    }

    /** A3 判据 4：机器概率口径的三处漂移收敛为单一真值。 */
    private static void checkGroupA3() throws Exception {
        final List<String> holders = new ArrayList<>();
        final List<String> stale = new ArrayList<>();
        final java.nio.file.Path root = java.nio.file.Paths.get("src/main/java");
        final List<java.nio.file.Path> files = new ArrayList<>();
        java.nio.file.Files.walk(root)
            .filter(p -> p.toString()
                .endsWith(".java"))
            .forEach(files::add);
        Collections.sort(files);
        for (final java.nio.file.Path f : files) {
            final String code = stripComments(new String(java.nio.file.Files.readAllBytes(f), "UTF-8"));
            if (code.contains("prosperityMachineChance")) {
                final String rel = root.relativize(f)
                    .toString()
                    .replace('\\', '/');
                holders.add(rel);
                // 定义处（Config.java 的字段初始化与 getInt 默认参数）允许，其它处出现 "/数字" 形态的
                // 分母字面量即为口径漂移残留
                if (!"com/miaokatze/gtsr/config/Config.java".equals(rel)) {
                    final java.util.regex.Matcher m = MACHINE_DENOM_LITERAL.matcher(code);
                    while (m.find()) {
                        stale.add(rel + ':' + m.group());
                    }
                }
            }
        }
        check(holders.contains("com/miaokatze/gtsr/config/Config.java"),
            "A3 机器分母唯一真值出处存在：Config.java");
        Collections.sort(holders);
        check(holders.equals(Arrays.asList("com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java",
            "com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java",
            "com/miaokatze/gtsr/config/Config.java")),
            "A3 机器分母只被 3 个文件引用（定义 1 + 消费 2），实测 " + holders);
        final String cfgStripped = stripComments(new String(
            java.nio.file.Files.readAllBytes(root.resolve("com/miaokatze/gtsr/config/Config.java")), "UTF-8"));
        check(count(cfgStripped, "prosperityMachineChance") == 3,
            "A3 Config 内该键在代码里出现 3 次（字段声明 + synchronizeConfiguration 赋值左值 + getInt 默认值实参；"
                + "键名字符串经 stripComments 已消），实测 " + count(cfgStripped, "prosperityMachineChance"));
        check(cfgStripped.contains("prosperityMachineChance = 16"),
            "A3 字段默认值 = 申报值 16（代码默认保持 16，用户 cfg 的 24 属外部覆盖）");
        final String cfgRaw = new String(
            java.nio.file.Files.readAllBytes(root.resolve("com/miaokatze/gtsr/config/Config.java")), "UTF-8");
        final int cfgIdx = cfgRaw.indexOf("prosperityMachineChance = 16");
        final String cfgBlock = commentBlockAbove(cfgRaw, cfgIdx);
        check(cfgBlock.contains("默认 16"),
            "A3 机器分母的紧邻注释写 '默认 16'，与实际默认值一致（注释块 " + cfgBlock.length() + " 字符）");
        check(!cfgRaw.contains("prosperityMachineChance = 24"),
            "A3 机器分母默认值未被改成实机观测态 24（用户存档 cfg 属外部覆盖，不改代码）");
        check(stale.isEmpty(), "A3 消费方无机器分母字面量残留（实测 " + stale.size() + " 处 " + stale + "）");
        // 改造前那处漂移的具体形态是注释里写死 "1/24"。P5 后 src/main/java 里该字面量必须 ≤ 1 处，
        // 且只能是已登记的越界残留（framework/SurfaceGate.java 的 Javadoc，非本片允许路径）。
        int docResidual = 0;
        final List<String> docHits = new ArrayList<>();
        for (final java.nio.file.Path f : files) {
            final String raw = new String(java.nio.file.Files.readAllBytes(f), "UTF-8");
            int idx = raw.indexOf("1/24");
            while (idx >= 0) {
                docResidual++;
                docHits.add(root.relativize(f)
                    .toString()
                    .replace('\\', '/'));
                idx = raw.indexOf("1/24", idx + 1);
            }
        }
        check(docResidual <= 1, "A3 判据4 具体分母字面量残留 ≤ 申报上界 1（实测 " + docResidual + " 处 " + docHits
            + "；那 1 处是 framework/SurfaceGate.java 的 Javadoc，不在本片允许路径内，已上报主代理）");
        check(!stripComments(new String(java.nio.file.Files.readAllBytes(
            root.resolve("com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java")),
            "UTF-8")).contains("1/24"), "A3 RuinedMachinePlacer 的写死分母注释已改成只写公式与出处");
        System.out.println("# A3 持有者=" + holders + " 具体分母字面量残留=" + docResidual + " " + docHits);
    }

    // ══════════════════════════════════ B 组 ══════════════════════════════════

    private static void checkGroupB(Dim78ScatterDensityCheck.Stats def, Dim78ScatterDensityCheck.Stats base,
        Dim78ScatterDensityCheck.Sampler sampler) {
        check(def.chunksSeen() >= 4096L,
            "B 样本量 ≥ 4096 chunk（默认档实测 " + def.chunksSeen() + "）");
        band("B 默认档件数均值", def.contourMean(), BAND_CONTOUR_MEAN_MIN, BAND_CONTOUR_MEAN_MAX);
        band("B 默认档落块均值", def.blockMean(), BAND_BLOCK_MEAN_MIN, BAND_BLOCK_MEAN_MAX);
        check(def.contourMax() <= contourHardCeiling(),
            "B H-3 件数硬上界：单 chunk 最大件数 " + def.contourMax() + " ≤ round(K×w_max)=" + contourHardCeiling());
        check(def.blockMax() <= blockHardCeiling() + 4,
            "B H-3 落块硬上界：单 chunk 最大落块 " + def.blockMax() + " ≤ " + blockHardCeiling() + "+4（单件越界）");
        check(def.contourP95() <= contourHardCeiling(),
            "B 件数 p95 " + def.contourP95() + " ≤ 硬上界 " + contourHardCeiling());
        check(def.coveragePp() >= BAND_COVERAGE_MIN,
            "B 有散布内容的 chunk 占比 " + fmt(def.coveragePp()) + "pp ≥ " + BAND_COVERAGE_MIN
                + "pp（摘竖向件后不得把痕迹削成光地）");
        // 判据 1 的正向证据：默认档柱阵归零
        check(def.chimneyMean() == 0.0D,
            "B 判据1 柱阵归零：默认档烟囱柱/chunk = " + fmt(def.chimneyMean()) + "（必须严格 0）");
        check(def.chimneyChunkPp() == 0.0D,
            "B 判据1 柱阵归零：默认档含柱 chunk 占比 = " + fmt(def.chimneyChunkPp()) + "pp（必须严格 0）");
        check(base.chimneyMean() >= 8.0D,
            "B 反假绿：同一样本现状行仍有 " + fmt(base.chimneyMean()) + " 柱/chunk（对照面成立）");
        check(def.blockMean() * 6.0D < base.blockMean(),
            "B 反假绿：默认档落块必须比现状疏 6 倍以上（实测 " + fmt(def.blockMean()) + " vs " + fmt(
                base.blockMean()) + "）");
        check(sampler.preScatterChunks >= 4096L && sampler.generatedChunks == sampler.preScatterChunks,
            "B 编排链前提：所有非城窗 chunk 都跑了 outpost/机器（" + sampler.generatedChunks + "/"
                + sampler.preScatterChunks + "）");
    }

    // ══════════════════════════════════ C 组（判据 3）══════════════════════════════════

    private static void checkGroupC(Dim78ScatterDensityCheck.Stats base, Dim78ScatterDensityCheck.Stats back) {
        band("C 判据3 回退档复现柱：烟囱柱/chunk", back.chimneyMean(), BAND_BASELINE_CHIMNEY_MIN,
            BAND_BASELINE_CHIMNEY_MAX);
        band("C 判据3 回退档复现落块/chunk", back.blockMean(), BAND_BASELINE_BLOCK_MIN, BAND_BASELINE_BLOCK_MAX);
        check(back.chimneyChunkPp() >= 99.0D,
            "C 判据3 回退档含柱 chunk 占比 " + fmt(back.chimneyChunkPp()) + "pp（现状是几乎每 chunk 都有柱）");
        check(back.digest()
            .equals(base.digest()),
            "C 判据3 回退档（件数写成上界 1024）与现状行（件数 64）的落块摘要逐位相同（" + base.digest()
                + " vs " + back.digest() + "）⇒ 件数天花板在高于掷点上限时确实不产生任何影响，"
                + "四键设回旧值即还原改造前散布层");
    }

    // ══════════════════════════════════ D 组（灵敏度）══════════════════════════════════

    private static void checkGroupD(Dim78ScatterDensityCheck.Stats def, Dim78ScatterDensityCheck.Stats relax) {
        check(relax.contourMean() > BAND_CONTOUR_MEAN_MAX,
            "D 灵敏度：K 放宽到 64 后件数均值 " + fmt(relax.contourMean()) + " 必须越过申报带上界 "
                + BAND_CONTOUR_MEAN_MAX + "（否则 B 组是恒绿）");
        check(relax.blockMean() > BAND_BLOCK_MEAN_MAX,
            "D 灵敏度：K 放宽后落块均值 " + fmt(relax.blockMean()) + " 必须越过申报带上界 " + BAND_BLOCK_MEAN_MAX);
        check(!relax.digest()
            .equals(def.digest()), "D 灵敏度：放宽档与默认档的落块摘要不同（同则说明上限根本没生效）");
    }

    private static void report(Dim78ScatterDensityCheck.Stats def, Dim78ScatterDensityCheck.Stats base,
        Dim78ScatterDensityCheck.Stats back, Dim78ScatterDensityCheck.Stats relax,
        Dim78ScatterDensityCheck.Sampler s) {
        System.out.println("# 非散布路径（判据 5 的对照面，与参数行无关）outpostHitChunks=" + s.outpostHitChunks
            + " outpostLanded=" + s.outpostLanded + " machineLanded=" + s.machineLanded
            + " eligibleChunks=" + s.preScatterChunks + " cityWindowChunks=" + s.cityWindowChunks);
        System.out.println("# 机器落块/chunk=" + fmt((double) s.machineLanded / s.preScatterChunks)
            + "（P4 权威样本 5.28，用于交叉核对样本口径）");
        final double drop = base.blockMean() <= 0 ? 0 : 100.0D * (1.0D - def.blockMean() / base.blockMean());
        System.out.println("P5-CONTOUR digestDefault=" + def.digest() + " digestRollback=" + back.digest()
            + " digestBaseline=" + base.digest() + " blockDropPct=" + fmt(drop));
    }

    // ══════════════════════════════════ 小工具 ══════════════════════════════════

    /** 取某一下标之上紧邻的连续 `//` 注释块（用于"注释与实际默认值一致"的对账）。 */
    private static String commentBlockAbove(String src, int idx) {
        final int lineStart = src.lastIndexOf('\n', idx) + 1;
        final StringBuilder sb = new StringBuilder();
        int cur = src.lastIndexOf('\n', Math.max(0, lineStart - 2)) + 1;
        while (cur > 0) {
            final int next = src.lastIndexOf('\n', Math.max(0, cur - 2)) + 1;
            final String line = src.substring(next, cur)
                .trim();
            if (line.startsWith("//")) {
                sb.insert(0, line)
                    .insert(0, '\n');
                cur = next;
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private static void band(String label, double actual, double lo, double hi) {
        check(actual >= lo && actual <= hi, label + " = " + fmt(actual) + " ∈ [" + lo + ", " + hi + "]");
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static int count(String code, String regex) {
        final java.util.regex.Matcher m = Pattern.compile(regex)
            .matcher(code);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** 去注释（块注释/行注释/字符串字面量），与 P4 工具同一口径。 */
    static String stripComments(String text) {
        final StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                final int e = text.indexOf("*/", i + 2);
                i = e < 0 ? text.length() : e + 2;
                sb.append('\n');
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                final int e = text.indexOf('\n', i);
                i = e < 0 ? text.length() : e;
            } else if (c == '"') {
                int j = i + 1;
                while (j < text.length() && text.charAt(j) != '"') {
                    j += text.charAt(j) == '\\' ? 2 : 1;
                }
                sb.append("\"\"");
                i = j + 1;
            } else if (c == '\'') {
                int j = i + 1;
                while (j < text.length() && text.charAt(j) != '\'') {
                    j += text.charAt(j) == '\\' ? 2 : 1;
                }
                sb.append("''");
                i = j + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static void check(boolean ok, String msg) {
        if (ok) {
            passed++;
            return;
        }
        FAILURES.add(msg);
        System.out.println("  FAIL " + msg);
    }
}
