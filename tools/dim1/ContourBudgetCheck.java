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
 *  A3 单一真值 + U6 不可配（判据 4 的 <b>P16 成对重写</b>）：旧立论"代码默认 16 + cfg 可覆盖"已随 U6 失效
 *    （三个 getInt 块被删、真值改 72、注释里的"默认 16"消失），旧写法还有一处用
 *    {@code indexOf("prosperityMachineChance = 16")} 取注释锚点——锚点 -1 之后整段行为未定义。
 *    新契约每条都成对钉「旧形状消失」+「新形状在场」：① 六个疏密键<b>不得</b>再是
 *    {@code configuration.get*} 的键名（负判据按"带引号才算键名"做前缀排除，字段名自身不算违规），
 *    同时保留可配的七键<b>必须</b>仍在 cfg（否则"整段删空"也能凑绿）；② 六键字段仍是
 *    {@code public static int} 且<b>不得</b> {@code final}——final 会打断 11 处离线测试档注入（含本类
 *    自己复位 {@code prosperityScatterClusterFieldChanceDenom}），后果是"密度面再没有机检"；
 *    ③ 机器分母真值 = 申报值 72，声明文本初值与运行期字段值必须相等，注释/日志<b>不复写</b>具体分母数字
 *    （锚点走声明符号，不走字面值）；④ 消费方只引用 {@code Config.xxx}，全仓 {@code 1/24}·{@code 1/72}·
 *    {@code machineChance=NN} 复写计数 = 0（旧申报"≤1"那处 SurfaceGate.java Javadoc 已按 P5 纪律改写，
 *    界值按<b>当前实测</b>重算后收紧）。与疏密无关的 {@code 1/16}、旧叙述 {@code 1/64}/{@code 1/48}
 *    <b>只登记不判红</b>（属 B1/B2/B4 正在写的文件），见运行时 {@code # A3 未决} 行。
 *  A1b 声明级（P5b）：成簇七键（mode/cell/denom/piecesMin/piecesMax/radius/falloff）默认值
 *    == 申报值（1/4/3/8/14/2/1），放宽任一键立刻红，不依赖采样。
 *  B 行为级（P5b 重标）：真实编排链上 8 seed × 8 个 16×16 窗实测，<b>成簇默认档</b>的件数均值
 *    ∈[1.0,1.4]、落块均值 ∈[1.3,1.9]、有内容 chunk 占比 ∈[20,32]pp、0 件占比 ≥70pp、CV ≥2、
 *    max ≥5；同一份样本里的"现状行"必须显著更密（反假绿）。旧带 [8,11]/[10,14] 是 P5 均匀档
 *    产物，按 plan §7.2 重测后作废（新实测与样本量见 p5b 证据文档判据 1/4）。
 *  C 可回退：把四键设回旧值 ⇒ 柱/chunk 必须回到冒烟 E6 的 8.8-10.2 带、落块回到 95-105 带，
 *    且其落块摘要与"现状行"逐位相同（同 JVM 内两行的 digest 相等）。
 *    BASE 树（改造前快照编译）与 AFTER 回退档的<b>跨进程</b>摘要对拍在
 *    {@code tools/dim1/surface_checks.sh} 的 [10] 步，不在本类里。
 *  D 灵敏度自检（P5b 换成咬合成簇真变量的破坏）：进程内把"场中心命中分母"放宽成 1（每格必中）
 *    再跑一遍，件数/落块均值必须<b>超出</b> B 组申报带且 0 件占比跌出成簇带
 *    （若本工具对放宽不敏感，D 组自己判红）。跨进程影子树版在 surface_checks.sh [10d]/[14e]。
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
    // P5b（plan §7.2）：成簇默认档的第二份真值申报（人为放宽任一键，A 组立刻红，不依赖采样）。
    static final int DECL_CLUSTER_MODE = 1;
    static final int DECL_CLUSTER_CELL = 4;
    static final int DECL_CLUSTER_DENOM = 3;
    static final int DECL_CLUSTER_PIECES_MIN = 8;
    static final int DECL_CLUSTER_PIECES_MAX = 14;
    static final int DECL_CLUSTER_RADIUS = 2;
    static final int DECL_CLUSTER_FALLOFF = 1;
    /**
     * P16 / U6：机器分母的代码真值（= 实机生效值 24 的三分之一，plan §3 落地表）。旧申报 16 作废，
     * 且它现在<b>只</b>能出现在这一处 + Config 的字段声明，其余任何复写都由 A3 判红。
     */
    static final int DECL_MACHINE_DENOM = 72;

    /** 六个疏密键（U6 从 gtsr.cfg 摘除，字段保留可写）。 */
    static final String[] SIX_DENSITY_KEYS = { "prosperityMachineChance", "prosperityOutpostChance",
        "prosperityRuinChance", "prosperityCityChance", "prosperityScatterClusterFieldChanceDenom",
        "prosperityStructureBudgetPerChunk" };

    /**
     * A3 负判据的正向对照：plan §U6「保留可配（非疏密类）」清单。缺了这份对照，"把整段
     * synchronizeConfiguration 删空"也能让六键的负判据全绿——那是最省事的凑绿路径。
     */
    static final String[] KEEP_CONFIGURABLE_KEYS = { "planDimension.prosperityDimension",
        "planDimension.shatteredDimension", "prosperityRuinsEnabled", "prosperityRuinWindowRepeatCap",
        "prosperityRuinMicroModulation", "prosperityBiomeMacroBandChunks", "prosperityCityBiomeGate" };

    /** cfg 注册键数地板（当前实测 44 = 旧 50 摘掉六键；只防"删空"，不防正常新增）。 */
    static final int CFG_KEY_FLOOR = 40;

    /** 机器分母在 P16 之前的历史值：16/64 旧代码默认、24 实机覆盖、48 旧废墟档。 */
    static final int[] LEGACY_MACHINE_DENOMS = { 16, 24, 48, 64 };

    private static final String CONFIG_REL = "com/miaokatze/gtsr/config/Config.java";

    /** 机器链闭包（定义 1 + 消费 2）；新增消费方须同批在此申报并写明理由。 */
    private static final List<String> MACHINE_HOLDER_BASELINE = Arrays.asList(
        "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java",
        "com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java", CONFIG_REL);

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

    /**
     * P5b 成簇默认档件数均值带（实测 1.271，8 seed × 8 窗 = 16384 chunk，见
     * plan/维度计划/调查取证/Phase1按片报告/p5b-clustered-scatter-20260919.md 判据 1）。旧带 [8.0,11.0] 按 P5
     * K=8 均匀档标定，成簇模型下作废（plan §7.2 明文"必须重测重标"）。新带装不下均匀档 9.19、
     * 也装不下"denom 放宽到 1"的失控档（D 组实测），带宽吸收 seed 抖动但不足以容纳退回均匀档。
     */
    private static final double BAND_CONTOUR_MEAN_MIN = 1.0D, BAND_CONTOUR_MEAN_MAX = 1.4D;
    /** 成簇默认档落块均值带（实测 1.584；旧 [10,14] 是 P5 均匀档产物）。 */
    private static final double BAND_BLOCK_MEAN_MIN = 1.3D, BAND_BLOCK_MEAN_MAX = 1.9D;
    /** 有内容 chunk 占比带（成簇 = 少数富 chunk；上界钉住不许退回均匀档那种几乎全覆盖）。 */
    private static final double BAND_COVERAGE_MIN = 20.0D, BAND_COVERAGE_MAX = 32.0D;
    /** 判据 1 的行为带（与 ScatterClusterVarianceCheck 同一判据的第二处独立钉）。 */
    private static final double BAND_ZERO_PP_MIN = 70.0D, BAND_CV_MIN = 2.0D;
    private static final int BAND_CONTOUR_MAX_MIN = 5;
    /** 现状行的柱带：冒烟 E6 实测 8.8-10.2 柱/chunk（离线口径＝含 chimney 落块的列数）。 */
    private static final double BAND_BASELINE_CHIMNEY_MIN = 8.0D, BAND_BASELINE_CHIMNEY_MAX = 11.0D;
    /** 现状行的落块带：P4 权威样本 100.58 块/chunk（不同样本原点，±10% 容差）。 */
    private static final double BAND_BASELINE_BLOCK_MIN = 90.0D, BAND_BASELINE_BLOCK_MAX = 110.0D;

    private static final Pattern MACHINE_DENOM_LITERAL = Pattern.compile("/\\s*(?:1[0-9]|[2-9][0-9])\\b");

    /**
     * A3 的"具体机器分母复写"形态：旧漂移 {@code 1/24}、当前真值 {@code 1/72} 的复写，
     * 以及注册日志里把分母写成字面量（{@code machineChance=72} / {@code machineChance=1/72}）。
     * 日志占位形式 {@code machineChance=1/{}} 不命中——那是"回显字段值"的正确写法。
     */
    private static final Pattern MACHINE_DENOM_DOC = Pattern
        .compile("1\\s*/\\s*(?:24|72)\\b|machineChance\\s*=\\s*(?:1\\s*/\\s*)?\\d{2,}");

    /** 注释里复写分母的两种口径形态（P5 单一真值纪律）。 */
    private static final Pattern DENOM_IN_COMMENT = Pattern.compile("1\\s*/\\s*\\d+|默认\\s*[^，。；)】]{0,3}\\d+");

    /** 未决登记面（不判红）：与疏密裁决相邻但归口别的切片的旧密度叙述。 */
    private static final Pattern PENDING_STALE_DENOM = Pattern.compile("1\\s*/\\s*(?:16|48|64)\\b");

    /** 六键的字段声明形状（组 1=修饰符串、2=键名、3=初值、4=行尾注释；容忍 CRLF）。 */
    private static final Pattern INT_FIELD_DECL = Pattern.compile(
        "(?m)^[\\t ]*((?:\\w+\\s+)+?)int\\s+(\\w+)\\s*=\\s*(-?\\d+)\\s*;[\\t ]*(?://([^\\r\\n]*))?[\\t ]*\\r?$");

    /** cfg 注册点：{@code configuration.getXxx("键名", …)} 的首个字符串实参才是"键名"。 */
    private static final Pattern CFG_KEY_NAME = Pattern
        .compile("configuration\\s*\\.\\s*get\\w*\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** U6 口径注释的合法表述集合（行尾注释里至少要有一种）。 */
    private static final Pattern NOT_EXPORTED = Pattern.compile("不导出|不注册|不得自定义|不允许自定义");

    /** 机器分母注释块的锚点：<b>声明符号</b>本身，不是"字段 = 字面值"。 */
    private static final Pattern MACHINE_DECL_ANCHOR = Pattern
        .compile("public\\s+static\\s+int\\s+prosperityMachineChance\\s*=");

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
        final int defClusterMode = Config.prosperityScatterClusterMode;
        final int defClusterCell = Config.prosperityScatterClusterCellChunks;
        final int defClusterDenom = Config.prosperityScatterClusterFieldChanceDenom;
        final int defClusterPMin = Config.prosperityScatterClusterPiecesMin;
        final int defClusterPMax = Config.prosperityScatterClusterPiecesMax;
        final int defClusterRadius = Config.prosperityScatterClusterRadiusChunks;
        final int defClusterFalloff = Config.prosperityScatterClusterFalloffPower;

        checkGroupA(defAttempts, defContours, defBlocks, defVertical, defWindowCap, defWeights);
        checkGroupA1b(defClusterMode, defClusterCell, defClusterDenom, defClusterPMin, defClusterPMax,
            defClusterRadius, defClusterFalloff);
        checkGroupA2();
        checkGroupA3();

        // —— B/C/D：一次采样跑四行（默认档、现状行、回退档、放宽档），行内差只有散布 ——
        final List<Dim78ScatterDensityCheck.Row> rows = Arrays.asList(
            // P5b：DEFAULT 行 = 生产成簇默认档全显式 spec（六参行自带 mode=0 = P5 均匀档，那已不是
            // 生产默认档；"写默认档就要把默认档写全"，防同 JVM 行序泄漏——Dim78 工具同款注释）。
            new Dim78ScatterDensityCheck.Row("DEFAULT-生产默认档", defContours, defBlocks, defAttempts,
                Boolean.valueOf(defVertical), defWindowCap, clusterSpec(defClusterMode, defClusterCell,
                    defClusterDenom, defClusterPMin, defClusterPMax, defClusterRadius, defClusterFalloff)),
            new Dim78ScatterDensityCheck.Row("BASELINE-现状柱阵", DECL_ATTEMPTS, 0, DECL_ATTEMPTS, Boolean.TRUE, 0),
            // 回退档故意把件数写成 Config 上界 1024（不是 64）：若件数天花板真的"高于掷点上限即不约束"，
            // 它的落块摘要必须与现状行逐位相同——这样 C 组的摘要相等才是有内容的断言，而不是同一行跑两遍。
            new Dim78ScatterDensityCheck.Row("ROLLBACK-四键设回旧值", 1024, 0, DECL_ATTEMPTS, Boolean.TRUE, 0),
            // P5b 灵敏度行：只把"场中心概率"放宽成每格必中（denom=1，其余=默认）⇒ 件数/落块均值
            // 必须冲出申报带；旧的"K 放宽到 64"在成簇档只是抬天花板、不构成放宽（那是退化档的
            // 形状，由 ScatterClusterVarianceCheck 的 DEGEN 行负责），故换成咬合真变量的这一行。
            new Dim78ScatterDensityCheck.Row("RELAX-场中心放宽成每格必中(denom=1)", defContours, defBlocks,
                defAttempts, Boolean.valueOf(defVertical), defWindowCap,
                clusterSpec(defClusterMode, defClusterCell, 1, defClusterPMin, defClusterPMax, defClusterRadius,
                    defClusterFalloff)));
        final Dim78ScatterDensityCheck.Sampler sampler =
            new Dim78ScatterDensityCheck.Sampler(seeds, regions, 16, rows);
        sampler.run();
        final Dim78ScatterDensityCheck.Stats def = sampler.stats("DEFAULT-生产默认档");
        final Dim78ScatterDensityCheck.Stats base = sampler.stats("BASELINE-现状柱阵");
        final Dim78ScatterDensityCheck.Stats back = sampler.stats("ROLLBACK-四键设回旧值");
        final Dim78ScatterDensityCheck.Stats relax = sampler.stats("RELAX-场中心放宽成每格必中(denom=1)");

        checkGroupB(def, base, sampler);
        checkGroupC(base, back);
        checkGroupD(def, relax);

        // 复位（本进程后续若还有别的用器不吃这些静态字段，但保持"写过的要还原"纪律）
        Config.prosperityScatterAttemptsPerChunk = defAttempts;
        Config.prosperityScatterContoursPerChunk = defContours;
        Config.prosperityScatterBlocksPerChunk = defBlocks;
        Config.prosperityScatterVerticalPieces = defVertical;
        Config.prosperityScatterWindowRepeatCap = defWindowCap;
        Config.prosperityScatterClusterMode = defClusterMode;
        Config.prosperityScatterClusterCellChunks = defClusterCell;
        Config.prosperityScatterClusterFieldChanceDenom = defClusterDenom;
        Config.prosperityScatterClusterPiecesMin = defClusterPMin;
        Config.prosperityScatterClusterPiecesMax = defClusterPMax;
        Config.prosperityScatterClusterRadiusChunks = defClusterRadius;
        Config.prosperityScatterClusterFalloffPower = defClusterFalloff;

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
        check(Config.prosperityMachineChance == DECL_MACHINE_DENOM, "A3 机器分母的运行期真值 = " + DECL_MACHINE_DENOM
            + "（P16/U6：该键已摘出 cfg，此值即代码唯一真值，不再有「实机覆盖」一说；实测 "
            + Config.prosperityMachineChance + "）");
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
            "prosperityScatterWeightChimney", "prosperityScatterWindowRepeatCap",
            // P5b：成簇七键也必须真实引用（散布层若把簇参数重新内联，本组红）
            "prosperityScatterClusterMode", "prosperityScatterClusterCellChunks",
            "prosperityScatterClusterFieldChanceDenom", "prosperityScatterClusterPiecesMin",
            "prosperityScatterClusterPiecesMax", "prosperityScatterClusterRadiusChunks",
            "prosperityScatterClusterFalloffPower" };
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

    /**
     * A3 单一真值 + U6 不可配（判据 4 的 P16 成对重写）。
     * <p>
     * 旧立论是"代码默认 16 + cfg 可覆盖"，于是把三件事当成真：键在 Config 代码里出现 3 次（声明 /
     * synchronizeConfiguration 赋值左值 / getInt 默认值实参）、源码含 {@code = 16}、紧邻注释写"默认 16"。
     * U6 摘除六个 getInt 块后这些形状全部消失，且取注释的锚点 {@code indexOf("…= 16")} 返回 -1，之后
     * {@code commentBlockAbove} 的行为未定义——那是"静默失效"而不是"判红"，比判红更糟。本组按新契约成对重写：
     * 每条都同时钉「旧形状不再在场」与「新形状确实在场」，任一半场被删都会红。
     */
    private static void checkGroupA3() throws Exception {
        final java.nio.file.Path root = java.nio.file.Paths.get("src/main/java");
        final List<java.nio.file.Path> files = new ArrayList<>();
        java.nio.file.Files.walk(root)
            .filter(p -> p.toString()
                .endsWith(".java"))
            .forEach(files::add);
        Collections.sort(files);

        // ── (1) 机器链闭包：定义 1（Config）+ 消费 2；消费方只引用 Config.xxx ──
        final List<String> holders = new ArrayList<>();
        final List<String> stale = new ArrayList<>();
        final List<String> notViaConfig = new ArrayList<>();
        final List<String> privateCopy = new ArrayList<>();
        for (final java.nio.file.Path f : files) {
            final String code = stripComments(read(f));
            final String rel = relativize(root, f);
            if (code.contains("prosperityMachineChance")) {
                holders.add(rel);
                if (!CONFIG_REL.equals(rel)) {
                    final java.util.regex.Matcher m = MACHINE_DENOM_LITERAL.matcher(code);
                    while (m.find()) {
                        stale.add(rel + ':' + m.group());
                    }
                    if (!code.contains("Config.prosperityMachineChance")) {
                        notViaConfig.add(rel);
                    }
                }
            }
            // 六键的任何"第二处 int 声明"（在 Config.java 之外）都是真值分叉——旧 1/24 漂移就是这么来的。
            if (!CONFIG_REL.equals(rel)) {
                for (final String k : SIX_DENSITY_KEYS) {
                    if (count(code, "\\bint\\s+" + Pattern.quote(k) + "\\s*=") > 0) {
                        privateCopy.add(rel + '#' + k);
                    }
                }
            }
        }
        check(holders.contains(CONFIG_REL), "A3 新形状在场：机器分母的唯一真值出处存在：" + CONFIG_REL);
        Collections.sort(holders);
        check(holders.equals(MACHINE_HOLDER_BASELINE),
            "A3 机器链闭包 = 定义 1 + 消费 2（新增消费方须同批把名写进 MACHINE_HOLDER_BASELINE 并申报理由），实测 "
                + holders);
        check(notViaConfig.isEmpty(),
            "A3 新形状在场：机器分母消费方逐字引用 Config.prosperityMachineChance（未走 Config 的持有者=" + notViaConfig
                + "；私有常量或字面量都会让真值再次分叉）");
        check(privateCopy.isEmpty(), "A3 旧形状消失：六键在 Config.java 之外没有任何第二处 int 声明（消费方私有副本＝"
            + "真值分叉，正是旧 1/24 漂移的成因），命中=" + privateCopy);
        check(stale.isEmpty(), "A3 旧形状消失：消费方去注释后无 /NN 形态的机器分母字面量（实测 " + stale.size() + " 处 "
            + stale + "）");

        // ── Config.java 的三份读数：原文 / 去注释保字符串 / 去注释去字符串 ──
        final String cfgRaw = read(root.resolve(CONFIG_REL));
        final String cfgCode = stripComments(cfgRaw);
        final String cfgNoComment = stripCommentsKeepingStrings(cfgRaw);
        final List<String> registered = registeredConfigKeys(cfgNoComment);

        // ── (2) 旧形状消失：六键不再是 cfg 的键名（负判据按"带引号才算键名"做前缀排除）──
        final List<String> stillRegistered = new ArrayList<>();
        final List<String> stillQuoted = new ArrayList<>();
        final List<String> stillAssigned = new ArrayList<>();
        final List<String> notSingleTruth = new ArrayList<>();
        for (final String k : SIX_DENSITY_KEYS) {
            if (registered.contains(k)) {
                stillRegistered.add(k);
            }
            final int quoted = count(cfgNoComment, Pattern.quote("\"" + k + "\""));
            if (quoted != 0) {
                stillQuoted.add(k + " x" + quoted);
            }
            final int assign = count(cfgCode, Pattern.quote(k) + "\\s*=\\s*configuration\\s*\\.");
            if (assign != 0) {
                stillAssigned.add(k + " x" + assign);
            }
            final int occ = count(cfgCode, Pattern.quote(k));
            if (occ != 1) {
                notSingleTruth.add(k + " x" + occ);
            }
        }
        check(stillRegistered.isEmpty(), "A3 旧形状消失：六键都不在 configuration.get* 的键名实参位（当前注册 "
            + registered.size() + " 键，命中=" + stillRegistered + "）");
        check(stillQuoted.isEmpty(),
            "A3 旧形状消失（前缀排除式：只有带引号的 \"KEY\" 才是 cfg 键名，字段名自身不算违规）：命中=" + stillQuoted);
        check(stillAssigned.isEmpty(),
            "A3 旧形状消失：六键都不是 synchronizeConfiguration 的赋值左值（旧契约每键 1 处，实测 " + cfgCode.length()
                + " 字符代码里为 0），命中=" + stillAssigned);
        check(notSingleTruth.isEmpty(), "A3 新形状在场：六键在 Config 代码里各出现恰好 1 次（=字段声明本身；旧契约是 3 次"
            + "：声明 + 赋值左值 + getInt 默认值实参），命中=" + notSingleTruth);

        // ── (3) 反假绿：非疏密键仍可配 + 抽键完整性（否则"整段删空"也能让 (2) 全绿）──
        final List<String> lostKeys = new ArrayList<>();
        for (final String k : KEEP_CONFIGURABLE_KEYS) {
            if (!registered.contains(k)) {
                lostKeys.add(k);
            }
        }
        check(lostKeys.isEmpty(), "A3 反假绿：plan §U6「保留可配」的 " + KEEP_CONFIGURABLE_KEYS.length
            + " 个非疏密键仍在 cfg 里，丢失=" + lostKeys);
        check(registered.size() >= CFG_KEY_FLOOR, "A3 反假绿：cfg 注册键数 ≥ 地板 " + CFG_KEY_FLOOR + "（实测 "
            + registered.size() + "，只防整段删空，不防正常新增）");
        final int getCalls = count(cfgNoComment, "configuration\\s*\\.\\s*get\\w*\\s*\\(");
        check(getCalls == registered.size(), "A3 抽键完整性：configuration.get*( 调用数 == 抽出的键名数（" + getCalls + " vs "
            + registered.size() + "；不等即有调用的首个实参不是字面量，(2) 的负判据会漏看）");

        // ── (4) 字段形状：public static int 且【不得】final ──
        // 理由必须写清：final 会打断离线测试档注入——实测 11 处赋值跨 4 个工具（PlacementContractCheck
        // :239/253/1426/1466/1576/1617/1634/1639、RuinFamilyCheck:912、本类的复位段、
        // ScatterClusterVarianceCheck:240）。加 final 最直接的反噬是<b>本工具自己编译不过</b>（本类就要
        // 复位 prosperityScatterClusterFieldChanceDenom），密度面从此再没有机检——所以这条比"值对不对"更硬。
        final java.util.regex.Matcher fm = INT_FIELD_DECL.matcher(cfgRaw);
        final java.util.Map<String, String> mods = new java.util.LinkedHashMap<>();
        final java.util.Map<String, String> inits = new java.util.LinkedHashMap<>();
        final java.util.Map<String, String> trails = new java.util.LinkedHashMap<>();
        final List<String> dupDecl = new ArrayList<>();
        while (fm.find()) {
            final String name = fm.group(2);
            if (!isDensityKey(name)) {
                continue;
            }
            if (mods.containsKey(name)) {
                dupDecl.add(name);
            }
            mods.put(name, fm.group(1)
                .trim());
            inits.put(name, fm.group(3));
            trails.put(name, fm.group(4) == null ? "" : fm.group(4)
                .trim());
        }
        final List<String> badShape = new ArrayList<>();
        for (final String k : SIX_DENSITY_KEYS) {
            final String m = mods.get(k);
            if (m == null) {
                badShape.add(k + "=<声明不再是「修饰符 int 键名 = 数字;」的一行形状>");
            } else if (!"public static".equals(m)) {
                badShape.add(k + "=[" + m + "]");
            }
        }
        check(badShape.isEmpty(), "A3 新形状在场：六键仍是 public static int 且无 final（final 打断 11 处离线档注入，"
            + "且首个编译失败的就是本工具自己 ⇒ 密度面失去机检），违规=" + badShape);
        check(dupDecl.isEmpty(), "A3 旧形状消失：六键在 Config.java 里没有第二处声明（双声明=双真值），命中=" + dupDecl);
        final List<String> badTrail = new ArrayList<>();
        for (final String k : SIX_DENSITY_KEYS) {
            final String t = trails.get(k);
            if (t == null || !t.contains("gtsr.cfg") || !NOT_EXPORTED.matcher(t)
                .find()) {
                badTrail.add(k + "=[" + t + "]");
            }
        }
        check(badTrail.isEmpty(), "A3 反假绿：六键的声明行都自带「不导出到 gtsr.cfg + 不可自定义」口径注释（这是读者唯一"
            + "能看到的「这个键为什么在 cfg 里找不到」的线索，删注释=把不可配变成看似缺陷），违规=" + badTrail);

        // ── (5) 机器分母真值：声明文本、运行期字段、历史值负判据三面一致 ──
        final String declared = inits.get("prosperityMachineChance");
        final int declValue = parseIntOr(declared, Integer.MIN_VALUE);
        check(declValue == DECL_MACHINE_DENOM, "A3 机器分母的声明真值 = " + DECL_MACHINE_DENOM + "（P16 落地值，plan §3"
            + " 实机生效值 24 的三分之一；实测声明文本里是 \"" + declared + "\"）");
        final List<String> legacyHit = new ArrayList<>();
        for (final int v : LEGACY_MACHINE_DENOMS) {
            if (declValue == v) {
                legacyHit.add(String.valueOf(v));
            }
        }
        check(legacyHit.isEmpty(), "A3 旧形状消失：机器分母未退回 P16 之前的历史值 " + Arrays.toString(LEGACY_MACHINE_DENOMS)
            + "（16=旧代码默认、24=旧实机覆盖、48/64=旧档），实测 " + declValue);
        check(!cfgRaw.contains("prosperityMachineChance = 24"),
            "A3 旧形状消失：Config 源码里没有 `prosperityMachineChance = 24`（把代码追成用户存档的观测态）");
        check(declValue == Config.prosperityMachineChance, "A3 单一真值跨面一致：机器分母的声明文本初值 == 运行期字段值（"
            + declValue + " vs " + Config.prosperityMachineChance + "；不等即静态块或回填在偷偷改写这个字段）");

        // ── (6) 注释口径：锚点走声明符号（旧写法拿字面值当锚点，默认值一变就 -1 静默失效）──
        final java.util.regex.Matcher anchor = MACHINE_DECL_ANCHOR.matcher(cfgRaw);
        final boolean anchored = anchor.find();
        check(anchored, "A3 判据自身可证：机器分母的注释锚点（声明符号 public static int prosperityMachineChance =）"
            + "命中；旧锚 indexOf(\"prosperityMachineChance = 16\") 在 -1 之后行为未定义");
        if (anchored) {
            final String block = commentBlockAbove(cfgRaw, anchor.start());
            check(!block.isEmpty(), "A3 新形状在场：机器分母仍有紧邻注释块交代沿革与纪律（实测 " + block.length() + " 字符）");
            check(block.contains("单一真值"), "A3 新形状在场：紧邻注释写明 P5「单一真值声明」（旧判据要它写「默认 16」，"
                + "那本身就是复写）——实测块长 " + block.length() + " 字符");
            final List<String> copied = new ArrayList<>();
            final java.util.regex.Matcher cm = DENOM_IN_COMMENT.matcher(block);
            while (cm.find()) {
                copied.add(cm.group());
            }
            check(copied.isEmpty(), "A3 旧形状消失：注释不复写具体分母数字（1/N 与「默认 N」两种形态，P5 单一真值纪律），"
                + "命中=" + copied);
            check(!block.contains(String.valueOf(DECL_MACHINE_DENOM)),
                "A3 旧形状消失：注释里不出现真值数字 " + DECL_MACHINE_DENOM + " 的复写（唯一真值=字段声明处）");
        }

        // ── (7) 全仓具体分母复写计数：界值按当前实测从旧的 ≤1 收紧为 0 ──
        // 旧申报的那 1 处是 framework/SurfaceGate.java 的 Javadoc，主代理已按 P5 纪律改写为不含数字，
        // 实测（plan/tmp/p3-b5a/work/probe-readings.txt）1/24 与 1/72 均为 0 处 ⇒ 0 是"当前可达且不靠
        // 动冻结文件"的最紧界；与疏密无关的 1/16、旧叙述 1/64 与 1/48 见下面的"未决"行，不在本条面内。
        int docResidual = 0;
        final List<String> docHits = new ArrayList<>();
        for (final java.nio.file.Path f : files) {
            final java.util.regex.Matcher m = MACHINE_DENOM_DOC.matcher(read(f));
            while (m.find()) {
                docResidual++;
                if (docHits.size() < 6) {
                    docHits.add(relativize(root, f) + ':' + m.group());
                }
            }
        }
        check(docResidual == 0, "A3 旧形状消失：src/main/java 里 1/24、1/72、machineChance=NN 三种复写计数 = 0"
            + "（旧申报上界 ≤1 按当前实测收紧为 0），实测 " + docResidual + " 处 " + docHits);
        check(!read(root.resolve(
            "com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java")).contains("1/24"),
            "A3 消费方注释口径：RuinedMachinePlacer 的写死分母注释已改成只写公式与出处（查的是原文，注释也算复写）");

        // ── 未决登记（只打印，不判红）：旧密度叙述站点归 B1/B2/B4，等形态定稿后按新实测改述 ──
        final List<String> pending = new ArrayList<>();
        for (final java.nio.file.Path f : files) {
            final java.util.regex.Matcher m = PENDING_STALE_DENOM.matcher(read(f));
            while (m.find()) {
                pending.add(relativize(root, f) + ':' + m.group());
            }
        }
        System.out.println("# A3 未决(登记不判红，归 B1/B2/B4 按新实测改述)=" + pending.size() + " " + pending);
        System.out.println("# A3 持有者=" + holders + " cfg注册键=" + registered.size() + " 抽键完整性=" + getCalls
            + " 机器分母复写=" + docResidual + " 声明真值=" + declared + " 运行期真值=" + Config.prosperityMachineChance
            + " 六键声明=" + mods);
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
        check(def.coveragePp() >= BAND_COVERAGE_MIN && def.coveragePp() <= BAND_COVERAGE_MAX,
            "B 有散布内容的 chunk 占比 " + fmt(def.coveragePp()) + "pp ∈ [" + BAND_COVERAGE_MIN + ", "
                + BAND_COVERAGE_MAX + "]pp（成簇档 = 少数富 chunk；上界钉住不得退回处处有件）");
        // P5b 判据 1 行为带（第二处独立钉，主钉在 ScatterClusterVarianceCheck）
        check(def.contourBucketPp(0, 0) >= BAND_ZERO_PP_MIN,
            "B 成簇 0 件 chunk 占比 " + fmt(def.contourBucketPp(0, 0)) + "pp ≥ " + BAND_ZERO_PP_MIN + "pp");
        check(def.contourCV() >= BAND_CV_MIN,
            "B 成簇件数 CV " + fmt(def.contourCV()) + " ≥ " + BAND_CV_MIN);
        check(def.contourMax() >= BAND_CONTOUR_MAX_MIN,
            "B 成簇件数 max " + def.contourMax() + " ≥ " + BAND_CONTOUR_MAX_MIN);
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
            "D 灵敏度：场中心放宽成每格必中(denom=1)后件数均值 " + fmt(relax.contourMean())
                + " 必须越过申报带上界 " + BAND_CONTOUR_MEAN_MAX + "（否则 B 组是恒绿）");
        check(relax.blockMean() > BAND_BLOCK_MEAN_MAX,
            "D 灵敏度：denom=1 后落块均值 " + fmt(relax.blockMean()) + " 必须越过申报带上界 "
                + BAND_BLOCK_MEAN_MAX);
        check(relax.contourBucketPp(0, 0) < BAND_ZERO_PP_MIN,
            "D 灵敏度：denom=1 后 0 件占比 " + fmt(relax.contourBucketPp(0, 0)) + "pp 必须跌出 ≥"
                + BAND_ZERO_PP_MIN + "pp 的成簇带");
        check(!relax.digest()
            .equals(def.digest()), "D 灵敏度：放宽档与默认档的落块摘要不同（同则说明成簇参数根本没生效）");
    }

    /** 成簇七键的显式 spec（DEFAULT/RELAX 行共用；"写默认档就要把默认档写全"）。 */
    static String clusterSpec(int mode, int cell, int denom, int pmin, int pmax, int radius, int falloff) {
        return "prosperityScatterClusterMode=" + mode + ",prosperityScatterClusterCellChunks=" + cell
            + ",prosperityScatterClusterFieldChanceDenom=" + denom + ",prosperityScatterClusterPiecesMin=" + pmin
            + ",prosperityScatterClusterPiecesMax=" + pmax + ",prosperityScatterClusterRadiusChunks=" + radius
            + ",prosperityScatterClusterFalloffPower=" + falloff;
    }

    /** A1b 组：成簇七键的声明级钉（与 Config 字段严格一致；放宽任一键本组立刻红）。 */
    private static void checkGroupA1b(int mode, int cell, int denom, int pmin, int pmax, int radius,
        int falloff) {
        check(mode == DECL_CLUSTER_MODE, "A1b 成簇开关默认 = " + DECL_CLUSTER_MODE + "（实测 " + mode + "）");
        check(cell == DECL_CLUSTER_CELL, "A1b 场格边长 = " + DECL_CLUSTER_CELL + "（实测 " + cell + "）");
        check(denom == DECL_CLUSTER_DENOM,
            "A1b 场中心命中分母 = " + DECL_CLUSTER_DENOM + "（实测 " + denom + "；放宽即红）");
        check(pmin == DECL_CLUSTER_PIECES_MIN && pmax == DECL_CLUSTER_PIECES_MAX,
            "A1b 单场件数配额 = [" + DECL_CLUSTER_PIECES_MIN + ", " + DECL_CLUSTER_PIECES_MAX + "]（实测 [" + pmin
                + ", " + pmax + "]）");
        check(radius == DECL_CLUSTER_RADIUS, "A1b 径向衰减半径 = " + DECL_CLUSTER_RADIUS + "（实测 " + radius + "）");
        check(falloff == DECL_CLUSTER_FALLOFF, "A1b 衰减幂次 = " + DECL_CLUSTER_FALLOFF + "（实测 " + falloff + "）");
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

    private static String read(java.nio.file.Path p) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(p), "UTF-8");
    }

    private static String relativize(java.nio.file.Path root, java.nio.file.Path f) {
        return root.relativize(f)
            .toString()
            .replace('\\', '/');
    }

    private static boolean isDensityKey(String name) {
        for (final String k : SIX_DENSITY_KEYS) {
            if (k.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 抽出 Config 源码里的 cfg 注册键名。参数必须是<b>已去注释但保留字符串字面量</b>的文本
     * （{@link #stripCommentsKeepingStrings}）：注释里重提"某键不导出"不算注册，字符串里的键名才算。
     */
    static List<String> registeredConfigKeys(String srcWithoutComments) {
        final List<String> keys = new ArrayList<>();
        final java.util.regex.Matcher m = CFG_KEY_NAME.matcher(srcWithoutComments);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    /** 取某一下标之上紧邻的连续 `//` 注释块（用于"注释口径"的对账；下标必须由调用方证非 -1）。 */
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
        return scanComments(text, false);
    }

    /**
     * 只去注释、<b>保留</b>字符串字面量。A3 的"某键是否还是 cfg 键名"必须用这一份：把字符串也抹掉就
     * 看不见键名字面量，而不过滤注释又会把"某键不导出"的重提注释算成违规（负判据把修复自身判红的旧坑）。
     */
    static String stripCommentsKeepingStrings(String text) {
        return scanComments(text, true);
    }

    private static String scanComments(String text, boolean keepStrings) {
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
                sb.append(keepStrings ? text.substring(i, Math.min(j + 1, text.length())) : "\"\"");
                i = j + 1;
            } else if (c == '\'') {
                int j = i + 1;
                while (j < text.length() && text.charAt(j) != '\'') {
                    j += text.charAt(j) == '\\' ? 2 : 1;
                }
                sb.append(keepStrings ? text.substring(i, Math.min(j + 1, text.length())) : "''");
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
