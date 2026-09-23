import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityNaturalBase;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRustLeaves;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRustLog;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperitySurface;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityTuft;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.DensityField;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer.VegTier;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;

/**
 * P17 S-B2「树与花草的群系身份分流」<b>频率机检</b>（plan §1-S-B★ / §2 第 4 条）。
 * 这一层改前<b>全仓零回归网</b>：{@code tools/dim1/*.java} 对装饰层那六个频率常量零引用
 * （P17-C §Q1 实跑 grep 结论），树密度 / 干高 / 花草密度只能实机目测 ⇒ 本片唯一的净增机检。
 * <p>
 * <b>六组断言</b>
 * <ol>
 * <li><b>DECL（档表结构与偏序）</b>：只读 {@code ProsperityDecorPlacer.VEG_TIERS_BY_ROSTER} /
 * {@code DEFAULT_TIER}。树密度 <b>森 &gt; 沼 &gt; 原 &gt; 沙 = 0</b>（荒漠那一行写成
 * {@code treeRolls == 0} 的档值）、干高三区间<b>两两分离</b>（平原 4..6 &lt; 沼泽 7..10 &lt; 森林
 * 11..16 ⇒ "矮/中/大"是可判序而不是比均值）、木种↔群系映射逐员钉、四木种 + 四花 + 两新草全员有消费者、
 * 默认档逐字段 = 改前；</li>
 * <li><b>SOURCE（源级红线）</b>：装饰层去注释源码不含 {@code BiomeId.} / {@code rosterIndex()} /
 * {@code ordinal ==} / {@code getBiomeGenForCoords} / {@code instanceof BiomeGen} /
 * 零 biome import（plan §2 第 8 条：抑制只能写成档值，不能写成身份等值判断）；
 * 编排器每 chunk 只解析一次身份且把 {@code rosterIndex} 传给装饰；
 * {@code tuftForGround} 体内 top 成员比较恰 4 处、全文件 top 成员比较总数恰 4、装饰层
 * {@code isNaturalTop(} 恰 5 处 —— 与 {@code SurfaceGateUnifyCheck} E 组同口径的<b>冗余</b>钉，
 * 不是替代（那条被钉死"多一处少一处都红"）；</li>
 * <li><b>M（真实链实测带）</b>：真实 {@code generateTerrain} + 真实表层缝 + 真实
 * {@code ProsperityDecorPlacer.decorate}（身份走生产同一个 {@code ordinalAt} 出口、同一个 chunk 中心
 * 采样点、同一份城 buffer 窗排除），逐群系钉 树数/chunk · 干高 min/max/mean · 叶/草/花/沙 块数/chunk；
 * 荒漠 {@code trees == 0 且 logs == 0} <b>精确</b>钉（不是"落进一条含 0 的宽带"）；
 * 另钉 sink 越界写入 == 0（冠层半径与干位内收自洽）；</li>
 * <li><b>ORDER（行为级偏序）</b>：同一次实测里再断言一遍 森&gt;沼&gt;原&gt;沙=0 与干高 森&gt;沼&gt;原
 * —— 档表对了但链上被门吃光也算红；</li>
 * <li><b>ANTI（反假绿对照臂）</b>：在<b>同一批荒漠 chunk 坐标</b>上跑三臂 ——
 * 原档（必须 0 树）⇒ 进程内把该行换成「每 chunk 必掷一棵」（必须 &gt; 0 且与 1.0/chunk 同阶）⇒
 * 还原（必须回到 0 树且沙砾/花草计数与第一臂逐位相同）。手法同 {@code SurfaceGateUnifyCheck} G 组、
 * {@code Dim78ScatterDensityCheck} D-PIN 对照臂；不成立即「沙漠零树」绿在一条不可达分支上；</li>
 * <li><b>ROSTER（沙类交付数）</b>：三个沙/砾方块的"盘上存在 + 被真实消费方读到"六面对账
 * （BlocksGTSR 字段 / BlockLoader 注册 / SurfaceGate 源文本<b>零</b>命中 / MUST_NOT_PASS /
 * NATURAL_BLOCKS / SurfaceHarness / 装饰层真实读用）。判据是交付数，不是清单数。</li>
 * </ol>
 * <p>
 * <b>口径边界（申报，不静默）</b>：M/ORDER 组只跑装饰趟（不跑城/outpost/机器/散布），带对的是
 * 「纯装饰链」；与生产整链的实测差 = 结构落块占掉的空气位，方向是生产略低。平坦 {@code Block[]}
 * 网格合成世界的口径逐字承袭 {@code SurfaceGateUnifyCheck.RegionWorld} /
 * {@code Dim78ScatterDensityCheck.GridWorld}（界外 null、y&gt;255 bedrock、离线条款同套 Unsafe 装配）。
 * 实机（真 World、真 setBlock 更新）目视 {@code [未实测]}。
 * <p>
 * 用法：{@code java P17VegetationFrequencyCheck [seeds=4] [regionsPerSeed=2] [axis=16] [antiChunks=120]}
 * <br>退出码 0 = 全绿；1 = 有申报项被破坏。同一份工具源码连跑两次逐位一致（判据 1）。
 */
public final class P17VegetationFrequencyCheck {

    private static final String[] BIO = { "RUSTED_STEPPE", "GEARWORK_FOREST", "BRASS_WASTES", "FUMAROLE_SWAMP" };
    private static final int STEPPE = 0;
    private static final int FOREST = 1;
    private static final int WASTES = 2;
    private static final int SWAMP = 3;

    private static final long[] SEEDS = { 0x503441L, 0x503442L, 0x503443L, 0x503444L, 0x503445L, 0x503446L,
        0x503447L, 0x503448L };

    /**
     * <b>同一批群系实例必须全程复用</b>：{@code GTSRBiomeAuthority.of(biome)} 的 byInstance 表按<b>身份</b>
     * 查，{@code SurfaceHarness.prosperityBiomes()} 每次 {@code new} 一组 ⇒ 记账用一组、def 用另一组
     * 会让所有 {@code ordinalAt} 解析成 -1（实测踩过：全量落到默认档，测出来就是"分流没生效"的假读数）。
     */
    private static BiomeGenBase[] pb;
    /** 见 {@link #pb}（dim79 那一组，只为 recordAllAllocations 的第二参）。 */
    private static BiomeGenBase[] sb;
    /** 见 {@link #pb}。 */
    private static GTSRDimensionDef defP;

    private static final String DECOR_SRC = "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/"
        + "ProsperityDecorPlacer.java";
    private static final String GEN_SRC = "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/"
        + "ProsperityWorldGenerator.java";

    /** 改前基线（同口径实测：{@code plan/tmp/p17-sb2/out/probe-before.log}；申报对照，不当判据）。 */
    private static final double PRE_TREES_PER_CHUNK = 0.059786D;
    /** 见 {@link #PRE_TREES_PER_CHUNK}（草 + 花件数/chunk；改前花恒 0）。 */
    private static final double PRE_FLORA_PER_CHUNK = 3.015228D;
    /** 改前干高带实测（同 log：min 3 / max 5 / mean 3.866..4.538）。 */
    private static final String PRE_TRUNK_BAND = "3..5";

    /**
     * 荒漠界带灌木渗入上界（树/chunk）——任务包口径「≤20 株/609 chunk」的率式：观测 8 株/609 chunk
     * （U6 prered 与 U9 全量同值，全部为界 chunk 密度渐变渗入的干高 1-2 灌木），上界 ≈2.5× 观测。
     * 荒漠的「绝对零」由 M 组<b>腹地精确零</b>断言承担（密度场均匀区密度 ≡0 ⇒ 零树），本上界只
     * 钉界带渐变的量级。
     */
    private static final double WASTES_LEAK = 20.0D / 609.0D;

    // ══ M 组带（v1.20.40 P19 §G 重钉，归因 U6 prered：DecorPlacer 三层掷骰改 DensityField 密度场
    // 驱动 + vegRoster 渗色修复——跨界 chunk 的树期望按 11×11 核连续过渡，区域含边界 chunk 时
    // 森林带被邻档混低（设计效果）、荒漠界带渗入灌木（干高 1-2，腹地均匀区密度 ≡0 保持零树）。
    // 锚 = U9 全量实测 4 seed × 2 区 × 16² chunk（与 U6 prered 同值）；率带 ≈ 实测 ±20%）═══

    /** 每 chunk 树数带（灌木+普通+巨树合计期望）；荒漠一行是界带渐变上界（见 WASTES_SHRUB_LEAK_MAX）。 */
    private static final double[][] BAND_TREES = { { 0.80D, 1.21D }, { 1.82D, 2.74D }, { 0.0D, WASTES_LEAK },
        { 1.31D, 1.98D } };
    /** 干高最小值带（T7 灌木档干 1-2 节 ⇒ 三群系 min 恒 1；荒漠界带灌木在场取 1、无树样本取 0；
     * 无树群系取 {0,0}）。 */
    private static final int[][] BAND_TRUNK_MIN = { { 1, 1 }, { 1, 1 }, { 0, 1 }, { 1, 1 } };
    /** 干高最大值带（巨树档上界；密度场混合使巨树采样随密度微移——v1.20.40 重录 原 24 / 森 37；
     * 荒漠界带灌木上界 2、无树样本取 0）。 */
    private static final int[][] BAND_TRUNK_MAX = { { 24, 24 }, { 37, 37 }, { 0, 2 }, { 26, 26 } };
    /** 干高均值带（三档混合期望）；荒漠带 [0,2] = 无树样本 0 或界带灌木 1-2 的混合期望。 */
    private static final double[][] BAND_TRUNK_MEAN = { { 2.05D, 3.07D }, { 6.30D, 9.45D }, { 0.0D, 2.0D },
        { 3.85D, 5.77D } };
    /** 叶块数/chunk 带（三档冠幅的行为级投影）；荒漠 = 界带灌木小冠上界（实测 0.3186 +20%）。 */
    private static final double[][] BAND_LEAVES = { { 32.5D, 48.7D }, { 103.6D, 155.4D }, { 0.0D, 0.38D },
        { 31D, 46.5D } };
    /** 草块数/chunk 带。 */
    private static final double[][] BAND_TUFTS = { { 4.3D, 7.5D }, { 4.9D, 8.3D }, { 1.3D, 3.0D },
        { 6.0D, 9.4D } };
    /** 花块数/chunk 带（改前恒 0 ⇒ 四档下界一律 &gt; 0）。 */
    private static final double[][] BAND_FLOWERS = { { 2.8D, 5.1D }, { 3.7D, 6.5D }, { 0.55D, 1.5D },
        { 3.5D, 6.1D } };
    /**
     * 沙/砾块数/chunk 带（只允许荒漠非 0）。
     * <p>
     * <b>v1.20.41 P20 S6 需求 5 重钉（荒漠铺沙两档改动）</b>：荒漠行档表的 {@code sandRolls} 4 → 7、
     * 主料 {@code SAND_FINE} → {@code SAND_COARSE}（{@code ProsperityDecorPlacer:224}）⇒ 同口径实测
     * <b>6.893782 → 11.740933 块/chunk</b>（{@code veg-BEFORE-4416.log} / 本片 after 跑，比 1.703
     * ≈ 7/4 = 1.75 的线性外推略低，差值来自空气门饱和：斑数上去后同斑重叠块的 {@code isAirBlock}
     * 让行率升高）。旧带 {@code [4.4, 9.2]}（= 旧实测 6.894 的 ±20% 族带）<b>原文保留在下行的
     * 注释里</b>，新带取同一族口径 = 新实测 11.741 的 ±20% → <b>[9.4, 14.1]</b>。
     * <p>
     * <b>与计划 §6.1 R4-① 的字面带 {@code [3.0, 4.6]} 的单位偏离（申报）</b>：R4-① 的推导链是
     * "现值 2.2 格/chunk × 7/4 = 3.85"，而 2.2 出自账本 §5 的<b>"每 chunk 覆盖到的地面格数"</b>口径；
     * 本判据的 {@code a.sand} 计的是 <b>{@code setBlock} 成功写入的块数</b>（同一格可被多斑重复尝试、
     * 也可一斑多块），改前同口径实测即 6.894 ⇒ 两个口径相差 ≈3.1×，把 R4-① 的字面带直接搬过来会
     * 是一条<b>单位错配</b>的带（改前就该红）。本片按"以本判据自己的改前实测为锚、按族口径等比抬升"
     * 立带，并把单位差记在这里，不当"放宽"（本带比旧带更靠上且宽度同为 ±20%）。
     * 需求侧的"更多粗砂粒"另由下行 {@link #BAND_COARSE_SHARE} 的<b>质</b>档钉住（数量带抓不住质）。
     */
    private static final double[][] BAND_SAND = { { 0.0D, 0.0D }, { 0.0D, 0.0D }, { 9.4D, 14.1D }, { 0.0D, 0.0D } };
    /** ANTI 臂：荒漠档改成必掷后的树数/chunk 带（0.5 下界 = 门没把荒漠整段挡死）。 */
    private static final double[] BAND_ANTI_WASTES = { 0.5D, 1.35D };
    /**
     * 荒漠沙砾斑里<b>粗沙</b>占覆盖物的比例带（需求 5"覆盖更多的粗砂粒"的<b>质</b>判据）。
     * 推导：档表主料换 {@code SAND_COARSE} 后，整斑 1/6 换河床砾、其余 5/6 取主料 ⇒ 理论值
     * 5/6 = 0.833；改前是 细沙 2/3 · 粗沙 1/3 · 砾 1/6 ⇒ 粗沙 0.333。带 = 理论值 ±15%
     * （抖动来自逐斑 1/6 砾骰的有限样本，荒漠样本 ≈14 块/chunk × 772 chunk ⇒ 相对标准误 &lt;0.5pp）。
     * 旧口径的粗沙份额（0.333）原文写在这里，禁止下一轮把它当"新常态"。
     */
    private static final double[] BAND_COARSE_SHARE = { 0.70D, 0.95D };
    /**
     * 风蚀柱数/chunk 带（v1.20.41 P20 S6 需求 5 的新 feature；P20 §5 S6 判据 2 的字面口径
     * {@code 柱高 ≥7 且柱顶 y − 地面 y ≥ 6 的柱数 / chunk ∈ [0.03, 0.12]}）。
     * <p>
     * <b>取带理由（不是照抄字面带，而是先证明它可达成）</b>：档表 1/12 的 chunk 级骰 × 落点场
     * 有候选列的概率。落点场 {@code TerrainVariants.windSpineSiteAt ≥ 0.5} 实测占 roster 2 的
     * <b>0.8397%</b> 列（{@code plan/tmp/p20-s6/probe-TVonly.out}；§21-F 转交的 S4 数是 0.8776%，
     * 两次差值来自哑元 id 表不同）⇒ 每 chunk 期望 2.15 个候选列 ⇒ "本 chunk 有候选" ≈ 88.8%
     * ⇒ 理论柱率 ≈ 0.0833 × 0.888 = <b>0.0740 柱/chunk</b>，恰在 [0.03, 0.12] 内偏上，
     * 且落点 argmax 的空气门/接地门还会再吃掉一点。故<b>沿用 §5 的字面带</b>（不另立新带），
     * 实测若掉出下界，第一顺位是抬 argmax 的候选面（4×4 → 8×8 格点），不是放宽本带。
     */
    private static final double[] BAND_WIND_STUMPS = { 0.03D, 0.12D };
    /**
     * 灌木（干高 ≤2 的短干段）数/chunk 带 —— §6.1 R4-② 的"成簇"档。
     * 定法照 R4-② 原文：以<b>改前实测</b>为"现值"，新带 {@code [现×0.90, 现×1.45]}。
     * 现值 = 改前同口径实测 <b>0.486405 株/chunk</b>（1324 chunk / 644 株，
     * {@code veg-BEFORE-newcheck-4416.log} 的 {@code VEG-S6} 行；与档表灌木密度 1/2 逐位吻合 ⇒
     * 这条"干高 ≤2 短竖段"代理是可信的）⇒ 新带 = <b>[0.437765, 0.705287]</b>。
     * （带外 ×0.4 抑制 + 带内 4..5 株一簇，算式与偏离申报见
     * {@code ProsperityDecorPlacer#SHRUB_CLUSTER_SKIP_DENOM}）。
     */
    private static final double[] BAND_SHRUBS_STEPPE = { 0.437765D, 0.705287D };
    /**
     * 草原灌木的<b>空间聚簇度</b>：每株平均"同丛邻居数"（切比雪夫距离 ≤
     * {@link #SHRUB_NEIGHBOR_RADIUS} 方块内的其它短干株）带 —— 需求 6"灌木<b>群</b>"的字面判据。
     * 这条抓的是<b>空间结构</b>，{@link #BAND_SHRUBS} 抓的是<b>总量</b>：均匀散点会让总量绿而本条红，
     * 反之亦然（防"只改档值不改形态"的假绿）。改前实测 ≈0.1（每 2 chunk 一株的均匀泊松），
     * 成簇后理论 ≈3.5（主株 + 环带 3..4 株互见）。
     */
    private static final double[] BAND_SHRUB_NEIGHBORS = { 1.20D, 6.50D };
    /** 聚簇度半径（方块，切比雪夫距离；= 簇环带外沿 4）。 */
    private static final int SHRUB_NEIGHBOR_RADIUS = 4;
    /** 短干株的干高上限（灌木档形态 = 干 1..2 节，见 {@code ProsperityDecorPlacer#placeShrubAt}）。 */
    private static final int SHRUB_TRUNK_MAX = 2;
    /** 风蚀柱的最小柱高（格；= {@code WIND_STUMP_HEIGHT_MIN}，判据侧按"柱身料竖段 ≥7"独立数，不复用生产常量表达式）。 */
    private static final int WIND_STUMP_MIN_RUN = 7;

    // ═══════════════════════════════════ 记账 ═══════════════════════════════════

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<Block, Integer> KINDS = new IdentityHashMap<>();
    private static long sinkDrops;
    /**
     * v1.20.41 P20 S8 列级归因只报读数：环境变量 {@code VEG_DUMP=<生物群系序号>} 置位时，把该档
     * 荒漠 chunk 里每一格原木/树叶连同<b>装饰前</b>该列地表高度与方块打出来（默认 -1=关，不参与
     * 任何带判定）。用途：比较两个生产快照之间"哪些列从非叶变有叶、它们站在什么地表上"。
     */
    private static int plantDumpIdx = -1;
    private static final int KIND_NONE = 0;
    private static final int KIND_LOG = 1;
    private static final int KIND_LEAF = 2;
    private static final int KIND_TUFT = 3;
    private static final int KIND_FLOWER = 4;
    private static final int KIND_SAND = 5;

    private P17VegetationFrequencyCheck() {}

    public static void main(String[] args) throws Exception {
        quietLogging();
        final String dumpEnv = System.getenv("VEG_DUMP");
        if (dumpEnv != null && !dumpEnv.isEmpty()) {
            plantDumpIdx = Integer.parseInt(dumpEnv.trim());
        }
        bootstrap();
        assertTierTables();
        assertSourceRedlines();
        final int seeds = intArg(args, 0, 4);
        final int regions = intArg(args, 1, 2);
        final int axis = intArg(args, 2, 16);
        final int antiChunks = intArg(args, 3, 120);
        final Map<Integer, Agg> m = measureRegion(seeds, regions, axis);
        System.out.println("VEG seeds=" + seeds + " regionsPerSeed=" + regions + " axis=" + axis
            + " cityWindowExcluded=true sinkCrossChunkDrops=" + sinkDrops);
        for (final Map.Entry<Integer, Agg> e : m.entrySet()) {
            System.out.println(biomeLine(e.getKey(), e.getValue()));
        }
        System.out.println("VEG-TOTAL " + biomeLine(-1, total(m)));
        // v1.20.41 P20 S8 只报读数：荒漠叶/chunk 的逐区拆解（不参与带判定）
        for (final String line : TILE_LINES) {
            System.out.println(line);
        }
        assertBands(m);
        assertSpineShrubAndSand(m);
        assertBehaviouralOrder(m);
        assertDesertSensitivity(antiChunks);
        assertRosterConsumed();
        if (!FAILURES.isEmpty()) {
            for (final String f : FAILURES) {
                System.out.println("  FAIL " + f);
            }
            System.out.println("P17 VEGETATION FREQUENCY FAIL: passed=" + passed + " failed=" + FAILURES.size());
            System.exit(1);
        }
        System.out.println("P17 VEGETATION FREQUENCY PASS: assertions=" + passed
            + " groups=DECL/SOURCE/M/ORDER/ANTI/ROSTER"
            + " 树密度 森>沼>原>沙(腹地零+界带渐变) 与 干高 森>沼>原 双钉（档表 + 真实链实测 + 反假绿臂）");
    }

    private static String biomeLine(int idx, Agg a) {
        return "idx=" + idx + " name=" + (idx >= 0 && idx < BIO.length ? BIO[idx] : "TOTAL") + " chunks=" + a.chunks
            + " trees=" + a.trees + " treesPerChunk=" + fmt(div(a.trees, a.chunks)) + " logs=" + a.logs
            + " trunkMin=" + trunkMin(a) + " trunkMax=" + trunkMax(a) + " trunkMean=" + fmt(trunkMean(a))
            + " leavesPerChunk=" + fmt(div(a.leaves, a.chunks)) + " tuftsPerChunk="
            + fmt(div(a.tufts, a.chunks)) + " flowersPerChunk=" + fmt(div(a.flowers, a.chunks))
            + " sandPerChunk=" + fmt(div(a.sand, a.chunks));
    }

    private static int intArg(String[] args, int i, int def) {
        return args.length > i ? Integer.parseInt(args[i]) : def;
    }

    // ═════════════════════════════════ DECL ═════════════════════════════════

    private static void assertTierTables() {
        final VegTier[] t = ProsperityDecorPlacer.VEG_TIERS_BY_ROSTER;
        // v1.20.39 T5/T8 重钉（plan §3.3）：档表族 4→5——第 5 元 sanzu（名册配槽非 selector）。
        check(t.length == 5, "DECL 档表长度 == 5（T5 起含 sanzu 第 5 元）；实测 " + t.length);
        // v1.20.39 T5/T8 重钉：BiomeId 枚举 9 员（两维各 4 + sanzu）。
        check(BiomeId.values().length == 9, "DECL 前置：BiomeId 枚举 9 员（两维各 4 + sanzu）");
        // T7/T8：树木三档表与档表族同长（sanzu 1/4 灌木 + 1/6 普通 + 1/32 Bayou，plan §3.7 表）。
        check(ProsperityDecorPlacer.TREE_TIERS_BY_ROSTER.length == t.length,
            "DECL 树木三档表长度 == VEG 档表长度（T7 三档同族扩元）；实测 "
                + ProsperityDecorPlacer.TREE_TIERS_BY_ROSTER.length + " vs " + t.length);
        final double dS = density(t[STEPPE]);
        final double dF = density(t[FOREST]);
        final double dW = density(t[WASTES]);
        final double dM = density(t[SWAMP]);
        check(t[WASTES].treeRolls == 0, "DECL 荒漠 treeRolls == 0（「沙漠没有树」写成档值，不是 return / 不是等值判断）");
        check(dW == 0.0D, "DECL 荒漠期望密度 == 0 精确；实测 " + dW);
        check(dF > dM && dM > dS && dS > 0.0D,
            "DECL 树密度偏序 森(" + dF + ") > 沼(" + dM + ") > 原(" + dS + ") > 沙(0)");
        check(dF >= 4.0D * dS, "DECL 森林期望密度 ≥ 平原 4 倍（「更多」要量级可辨）；实测 " + ratio(dF, dS) + "×");
        check(hi(t[STEPPE]) < lo(t[SWAMP]) && hi(t[SWAMP]) < lo(t[FOREST]),
            "DECL 干高三区间分离 原[" + lo(t[STEPPE]) + "," + hi(t[STEPPE]) + "] < 沼[" + lo(t[SWAMP]) + ","
                + hi(t[SWAMP]) + "] < 森[" + lo(t[FOREST]) + "," + hi(t[FOREST]) + "]");
        check(hi(t[FOREST]) > 5, "DECL 森林干上界 > 改前上界 5（「更大」落在可测区间上移）；实测 " + hi(t[FOREST]));
        check(distinctTrunkBands(t) >= 3, "DECL 三个有树的群系干高档互不相同（distinct >= 3）");
        for (int i = 0; i < 4; i++) {
            check(t[i].trunkSpan >= 1, "DECL 档 " + i + " trunkSpan >= 1（nextInt(0) 会抛）");
            check(t[i].treeRolls == 0 || t[i].treeChanceDenom > 0,
                "DECL 档 " + i + " 不变式 treeRolls>0 ⇒ treeChanceDenom>0");
            check(t[i].canopyRadius >= 1 && t[i].canopyRadius <= 7,
                "DECL 档 " + i + " canopyRadius ∈ [1,7]（干位内收 16−2r 必须 > 0）；实测 " + t[i].canopyRadius);
            check(t[i].grassRolls + t[i].flowerRolls > ProsperityDecorPlacer.DEFAULT_TIER.grassRolls,
                "DECL 档 " + i + "（" + BIO[i] + "）草+花 尝试数 > 改前默认档 "
                    + ProsperityDecorPlacer.DEFAULT_TIER.grassRolls + "（「增加更多花草」逐群系都成立）；实测 "
                    + (t[i].grassRolls + t[i].flowerRolls));
        }
        check(t[WASTES].sandRolls > 0, "DECL 荒漠沙砾趟 > 0（沙类方块有真实消费者）");
        // v1.20.41 P20 S6 需求 5 的两处档值钉：量（斑数 4 → 7）与质（主料 SAND_FINE → SAND_COARSE）。
        // 这两条是"回到常量域重解"的可见锚 —— 任何一条被回退，M 组的量带/份额带会同时红，而本条先点名是哪一档。
        check(t[WASTES].sandRolls == 7,
            "DECL 荒漠沙砾斑数 == 7（v1.20.41 需求 5『更粗砂粒』的量档，改前 4；7/4 的线性外推见 BAND_SAND）；实测 "
                + t[WASTES].sandRolls);
        final String decorTier;
        try {
            decorTier = condense(stripComments(read(Paths.get(DECOR_SRC))));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        check(decorTier.contains("WIND_STUMP_ROLLS_BY_ROSTER={0,0,8,0,0}"),
            "DECL 风蚀柱名册档 = 只在荒漠档 8、其余（含身份缺失回退）0（抑制写成档值而非身份等值判断；"
                + "档表按源码文本钉，同一份判据源码要能同时链接改前/改后两套生产 class）");
        check(decorTier.contains(
            "newVegTier(0,0,4,3,1,WOOD_RUST,WOOD_NONE,0,3,1,7,FLOWER_BRASS,GRASS_BRISTLE,SAND_COARSE)"),
            "DECL 荒漠植被档整行字面 = 沙砾斑 7 + 主料 SAND_COARSE（condense 口径，防 spotless 折行打死 needle）");
        check(t[WASTES].sandKind == ProsperityDecorPlacer.SAND_COARSE,
            "DECL 荒漠主料档 == SAND_COARSE（需求 5 的质档，改前 SAND_FINE；只走覆盖物、绝不进 top，"
                + "H-4 与 §1.2 第 5 条）；实测 kind=" + t[WASTES].sandKind);
       for (int i = 0; i < 4; i++) {
            if (i != WASTES) {
                check(t[i].sandRolls == 0, "DECL 非荒漠档 " + i + " sandRolls == 0（铺沙不外溢）");
            }
        }
        check(t[STEPPE].woodPrimary == ProsperityDecorPlacer.WOOD_RUST, "DECL 草原木种 = rust（沿用改前唯一树种）");
        check(t[FOREST].woodPrimary == ProsperityDecorPlacer.WOOD_BRASS
            && t[FOREST].woodSecondary == ProsperityDecorPlacer.WOOD_COPPER && t[FOREST].woodMixDenom > 1,
            "DECL 森林木种 = brass 主 + copper 混生（同群系两副干色两副冠形）");
        check(t[SWAMP].woodPrimary == ProsperityDecorPlacer.WOOD_MARSH
            && t[SWAMP].woodSecondary == ProsperityDecorPlacer.WOOD_NONE, "DECL 沼树木种 = marsh 单档");
        check(t[STEPPE].woodSecondary == ProsperityDecorPlacer.WOOD_NONE, "DECL 草原无混生档（次档关闭 ⇒ 不掷随机）");
        final TreeSet<Integer> woods = new TreeSet<>();
        final TreeSet<Integer> flowers = new TreeSet<>();
        final TreeSet<Integer> grasses = new TreeSet<>();
        for (final VegTier v : t) {
            if (v.treeRolls > 0) {
                woods.add(v.woodPrimary);
                if (v.woodSecondary >= 0) {
                    woods.add(v.woodSecondary);
                }
            }
            flowers.add(v.flowerKind);
            grasses.add(v.grassKind);
        }
        check(woods.equals(new TreeSet<>(Arrays.asList(ProsperityDecorPlacer.WOOD_RUST,
            ProsperityDecorPlacer.WOOD_COPPER, ProsperityDecorPlacer.WOOD_BRASS,
            ProsperityDecorPlacer.WOOD_MARSH))),
            "DECL 四木种全员被某个有树的群系消费（防「注册了但永不被读到」）；实测 " + woods);
        check(flowers.size() == 4, "DECL 四自有花全员被消费（改前花设施数 = 0）；实测 " + flowers);
        check(grasses.size() == 2, "DECL 两新草全员被消费；实测 " + grasses);
        final VegTier d = ProsperityDecorPlacer.DEFAULT_TIER;
        check(d.treeRolls == 1 && d.treeChanceDenom == 16, "DECL 默认档树 = 改前 1 掷 × 1/16");
        check(d.trunkMin == 3 && d.trunkSpan == 3, "DECL 默认档干高 = 改前 " + PRE_TRUNK_BAND);
        check(d.canopyRadius == 2, "DECL 默认档冠半径 = 改前 5×5（r=2）");
        check(d.woodPrimary == ProsperityDecorPlacer.WOOD_RUST
            && d.woodSecondary == ProsperityDecorPlacer.WOOD_NONE, "DECL 默认档木种 = 改前 rust 单档");
        check(d.flowerRolls == 0 && d.sandRolls == 0, "DECL 默认档花/沙 = 改前 0（身份缺失不凭空造设施）");
        check(d.grassRolls == 3, "DECL 默认档草 = 3（改前 2-4 的均值；非逐位相同，RESULT 已申报）");
        check(ProsperityDecorPlacer.tierForRosterIndex(-1) == d && ProsperityDecorPlacer.tierForRosterIndex(99) == d,
            "DECL 越界/身份缺失一律走默认档（回退臂与档表同一处，无第二真值源）");
    }

    private static double density(VegTier t) {
        return t.treeRolls <= 0 || t.treeChanceDenom <= 0 ? 0.0D : (double)t.treeRolls / t.treeChanceDenom;
    }

    private static int lo(VegTier t) {
        return t.trunkMin;
    }

    private static int hi(VegTier t) {
        return t.trunkMin + Math.max(1, t.trunkSpan) - 1;
    }

    private static int distinctTrunkBands(VegTier[] t) {
        final TreeSet<String> s = new TreeSet<>();
        for (final VegTier v : t) {
            if (v.treeRolls > 0) {
                s.add(lo(v) + "-" + hi(v));
            }
        }
        return s.size();
    }

    private static String ratio(double a, double b) {
        return String.format(Locale.ROOT, "%.2f", b <= 0D ? Double.NaN : a / b);
    }

    // ═════════════════════════════════ SOURCE ═════════════════════════════════

    private static void assertSourceRedlines() throws Exception {
        final String decor = stripComments(read(Paths.get(DECOR_SRC)));
        final String gen = stripComments(read(Paths.get(GEN_SRC)));
        check(!decor.contains("BiomeId."), "SOURCE 装饰层零 BiomeId 引用（身份等值判断＝第二真值源）");
        check(!decor.contains("rosterIndex()"), "SOURCE 装饰层零 rosterIndex() 等值判断");
        check(!decor.contains("ordinal ==") && !decor.contains("ordinal=="), "SOURCE 装饰层零 ordinal 等值判断");
        check(!decor.contains("getBiomeGenForCoords") && !decor.contains("instanceof BiomeGen"),
            "SOURCE 装饰层不读群系实例选档");
        check(!decor.contains("net.minecraft.world.biome"), "SOURCE 装饰层零 biome import");
        check(!decor.contains("GTSRBiomeAuthority"), "SOURCE 装饰层不自己解析身份（身份由编排器传入）");
        check(count(condense(gen), "ProsperityDecorPlacer.decorate(") == 1,
            "SOURCE 编排器装饰调用恰 1 处（condense 口径，防 spotless 折行）；实测 "
                + count(condense(gen), "ProsperityDecorPlacer.decorate("));
        // T7/T8 重钉：装饰身份改走 vegRosterIndex（sanzu 平面档接线，plan §3.3/§3.7）——
        // 旧 "chunkX, chunkZ, rosterIndex, sink)" 文本钉随之退役；新钉用 flat（去注释 + 折叠
        // 全部空白）匹配，防 spotlessApply 折行打死 needle（v1.20.38 纪律 2）。
        check(condense(gen).contains(
            "chunkX,chunkZ,vegRosterIndex(worldSeed,chunkX,chunkZ,rosterIndex),sink)"),
            "SOURCE 装饰调用带 vegRosterIndex 实参（T7 平面档接线；condense 口径防折行）");
        check(count(gen, "ordinalAt(") == 1,
            "SOURCE 编排器每 chunk 只解析一次身份（改造前是 biomeWeight 每次各解析一次）；实测 "
                + count(gen, "ordinalAt("));
        check(count(decor, "isNaturalTop(") == 5,
            "SOURCE 装饰层 isNaturalTop( 恰 5 处（1 定义 + 1 委托 + 1 naturalTopAt + 2 碎石内联）；实测 "
                + count(decor, "isNaturalTop("));
        check(count(methodBody(decor, "static Block tuftForGround("), "== BlocksGTSR.prosperity") == 4,
            "SOURCE tuftForGround 体内 top 成员比较恰 4 处（分流没走地表比选）");
        check(count(decor, "== BlocksGTSR.prosperity") == 4,
            "SOURCE 装饰层全文件 top/surface 成员比较总数恰 4（新四趟一律经档表或选择器返回值）；实测 "
                + count(decor, "== BlocksGTSR.prosperity"));
    }

    // ═════════════════════════════ 真实链实测 ═════════════════════════════

    static final class Agg {
        long chunks;
        long trees;
        long logs;
        final int[] trunk = new int[96];
        long leaves;
        long tufts;
        long flowers;
        long sand;
        /** v1.20.41 P20 S6：沙/砾三档各自块数（需求 5"更多<b>粗</b>砂粒"的质判据分子）。 */
        long sandCoarse;
        long sandGravel;
        long sandFine;
        /** v1.20.41 P20 S6：风蚀柱数（柱身料竖段 ≥7 格）、最宽柱高读数、柱顶粗沙覆料数。 */
        long stumps;
        long stumpRunMax;
        long stumpCaps;
        /** v1.20.41 P20 S6：短干株数（干高 ≤{@link #SHRUB_TRUNK_MAX}）与聚簇邻居合计。 */
        long shrubs;
        long shrubNeighbors;

        Agg copy() {
            final Agg b = new Agg();
            b.chunks = chunks;
            b.trees = trees;
            b.logs = logs;
            b.leaves = leaves;
            b.tufts = tufts;
            b.flowers = flowers;
            b.sand = sand;
            b.sandCoarse = sandCoarse;
            b.sandGravel = sandGravel;
            b.sandFine = sandFine;
            b.stumps = stumps;
            b.stumpRunMax = stumpRunMax;
            b.stumpCaps = stumpCaps;
            b.shrubs = shrubs;
            b.shrubNeighbors = shrubNeighbors;
            System.arraycopy(trunk, 0, b.trunk, 0, trunk.length);
            return b;
        }

        long trunkSum() {
            long n = 0;
            for (final int c : trunk) {
                n += c;
            }
            return n;
        }

        /** 干高直方的加权合计（= 干格总数），与 {@link #logs} 必须逐位相等（同一批格的两种数法）。 */
        long trunkBlockSum() {
            long n = 0;
            for (int i = 0; i < trunk.length; i++) {
                n += (long)i * trunk[i];
            }
            return n;
        }

        /** 短干株数（灌木档代理：干高 1..{@link #SHRUB_TRUNK_MAX} 的 LOG 竖段）。 */
        long shrubProxy() {
            long n = 0;
            for (int i = 1; i <= SHRUB_TRUNK_MAX && i < trunk.length; i++) {
                n += trunk[i];
            }
            return n;
        }

        String digest() {
            return "chunks=" + chunks + " trees=" + trees + " logs=" + logs + " leaves=" + leaves + " tufts=" + tufts
                + " flowers=" + flowers + " sand=" + sand + " trunkHist=" + Arrays.toString(trunk)
                // v1.20.41 P20 S6 起 S6 新增的四组计数也进逐位自证摘要：ANTI 的"换档→还原必须逐位回到
                // 第一臂"因此同时证明<b>风蚀柱趟与灌木簇趟</b>是 seed 纯函数（不读共享 rand、不依赖时序）。
                + " sandKinds=" + sandCoarse + "/" + sandGravel + "/" + sandFine + " stumps=" + stumps + "/"
                + stumpRunMax + "/" + stumpCaps + " shrubs=" + shrubs + "/" + shrubNeighbors;
        }
    }

    private static Agg total(Map<Integer, Agg> m) {
        final Agg s = new Agg();
        for (final Agg a : m.values()) {
            s.chunks += a.chunks;
            s.trees += a.trees;
            s.logs += a.logs;
            s.leaves += a.leaves;
            s.tufts += a.tufts;
            s.flowers += a.flowers;
            s.sand += a.sand;
            s.sandCoarse += a.sandCoarse;
            s.sandGravel += a.sandGravel;
            s.sandFine += a.sandFine;
            s.stumps += a.stumps;
            s.stumpRunMax = Math.max(s.stumpRunMax, a.stumpRunMax);
            s.stumpCaps += a.stumpCaps;
            s.shrubs += a.shrubs;
            s.shrubNeighbors += a.shrubNeighbors;
            for (int i = 0; i < s.trunk.length; i++) {
                s.trunk[i] += a.trunk[i];
            }
        }
        return s;
    }

    /**
     * v1.20.41 P20 S8 只报读数（不参与任何带判定）：荒漠 {@code leavesPerChunk} 的<b>逐区</b>拆解。
     * 全量链默认臂 4/2 出现 0.3268(S6 T0) → 0.4581 的位移，而 4/4 臂逐字不变，故必须按
     * (seed, 区序号) 拆开才能指认"是哪一区贡献的"。此处只做差值快照，不改计数口径。
     */
    private static final java.util.List<String> TILE_LINES = new ArrayList<>();

    private static Agg diffAgg(final Agg before, final Agg after) {
        final Agg d = new Agg();
        d.chunks = after.chunks - before.chunks;
        d.trees = after.trees - before.trees;
        d.logs = after.logs - before.logs;
        d.leaves = after.leaves - before.leaves;
        d.tufts = after.tufts - before.tufts;
        d.flowers = after.flowers - before.flowers;
        d.sand = after.sand - before.sand;
        d.shrubs = after.shrubs - before.shrubs;
        d.stumps = after.stumps - before.stumps;
        d.stumpRunMax = Math.max(before.stumpRunMax, after.stumpRunMax);
        for (int i = 0; i < d.trunk.length; i++) {
            d.trunk[i] = after.trunk[i] - before.trunk[i];
        }
        return d;
    }

    /** 区采样（M/ORDER 组）：真实地形 + 真实表层缝 + 真实装饰，城 buffer 窗生产同源排除。 */
    private static Map<Integer, Agg> measureRegion(int seeds, int regions, int axis) throws Exception {
        TILE_LINES.clear();
        final Map<Integer, Agg> out = new TreeMap<>();
        final int side = axis * 16;
        final Block[] scratch = new Block[65536];
        final byte[] scratchMeta = new byte[65536];
        for (int si = 0; si < seeds; si++) {
            final long seed = SEEDS[si % SEEDS.length];
            final GTSRWorldChunkManager mgrP = new GTSRWorldChunkManager(seed, defP);
            final java.lang.reflect.Method gen = SurfaceHarness.generateTerrain();
            for (int r = 0; r < regions; r++) {
                final int cx0 = r * axis * 3 + si * 4096;
                final int cz0 = r * axis * 5 + si * 924816;
                final Block[] grid = new Block[side * side * 256];
                final FlatWorld world = FlatWorld.of(grid, cx0 << 4, cz0 << 4, seed, side);
                final GTSRChunkProviderBase provP = new ChunkProviderProsperityRuins(world, seed);
                final java.lang.reflect.Method seam = SurfaceHarness.surfaceSeam(provP.getClass());
                for (int dx = 0; dx < axis; dx++) {
                    for (int dz = 0; dz < axis; dz++) {
                        final int cx = cx0 + dx;
                        final int cz = cz0 + dz;
                        Arrays.fill(scratch, null);
                        Arrays.fill(scratchMeta, (byte)0);
                        gen.invoke(provP, cx, cz, scratch, scratchMeta, null);
                        final BiomeGenBase[] plane = mgrP.loadBlockGeneratorData(null, cx * 16, cz * 16, 16, 16);
                        seam.invoke(null, seed, cx * 16, cz * 16, scratch, scratchMeta, plane);
                        final int bx = dx * 16;
                        final int bz = dz * 16;
                        for (int lx = 0; lx < 16; lx++) {
                            for (int lz = 0; lz < 16; lz++) {
                                final int col = (lx << 12) | (lz << 8);
                                final int dst = (bx + lx) + (bz + lz) * side;
                                for (int y = 0; y < 256; y++) {
                                    grid[y * side * side + dst] = scratch[col | y];
                                }
                            }
                        }
                    }
                }
                // 注意：bucket() 的语义是"取桶并 +1 chunk"，此处只读快照，绝不能走 bucket()
                final Agg w0 = out.get(Integer.valueOf(WASTES));
                final Agg wastesBefore = w0 == null ? new Agg() : w0.copy();
                decorateRegion(out, world, grid, side, seed, cx0, cz0, axis, si + "/" + r);
                final Agg wastesDelta = diffAgg(wastesBefore, out.get(Integer.valueOf(WASTES)));
                TILE_LINES.add("VEG-TILE si=" + si + " r=" + r + " seed=0x" + Long.toHexString(seed)
                    + " cx0=" + cx0 + " cz0=" + cz0 + " axis=" + axis + " " + biomeLine(WASTES, wastesDelta));
            }
        }
        return out;
    }

    /** 对一个区跑装饰并计数（干高由网格竖直段扫描，与 sink 计数分面）。 */
    private static void decorateRegion(Map<Integer, Agg> out, FlatWorld world, Block[] grid, int side, long seed,
        int cx0, int cz0, int axis, String tile) {
        // v1.20.41 P20 S6：装饰<b>前</b>的每列地表高度 —— 风蚀柱的柱身竖段与灌木短干都按"从原地面
        // 往上数"判定，装饰后再找地表会把柱本身当地表（findSurfaceY 的口径），故必须在此刻快照。
        final int[] topBefore = new int[side * side];
        final int yTop = Math.min(255, MAX_SURFACE_Y_SCAN);
        for (int c = 0; c < topBefore.length; c++) {
            int t = -1;
            for (int y = yTop; y >= 0; y--) {
                if (grid[y * side * side + c] != null) {
                    t = y;
                    break;
                }
            }
            topBefore[c] = t;
        }
        final CountingSink sink = plantDumpIdx < 0 ? new CountingSink(world, out)
            : new CountingSink(world, out, topBefore, grid, side, cx0, cz0, tile);
        for (int dx = 0; dx < axis; dx++) {
            for (int dz = 0; dz < axis; dz++) {
                final int gcx = cx0 + dx;
                final int gcz = cz0 + dz;
                if (CityPlanner.citiesNear(seed, gcx, gcz).length > 0) {
                    continue;
                }
                final int idx = ordinal(gcx, gcz);
                sink.chunk(gcx, gcz, idx, bucket(out, idx));
                ProsperityDecorPlacer.decorate(world, seed, gcx, gcz, idx, sink);
            }
        }
        final List<int[]> shrubCols = new ArrayList<>();
        for (int dx = 0; dx < axis; dx++) {
            for (int dz = 0; dz < axis; dz++) {
                final int gcx = cx0 + dx;
                final int gcz = cz0 + dz;
                if (CityPlanner.citiesNear(seed, gcx, gcz).length > 0) {
                    continue;
                }
                final int idx = ordinal(gcx, gcz);
                final Agg a = out.get(Integer.valueOf(idx));
                final int chunkTrees = countTrunks(grid, side, dx, dz, a);
                if (idx == WASTES && wastesInterior(seed, gcx, gcz)) {
                    wastesInteriorChunks++;
                    wastesInteriorTrees += chunkTrees;
                }
                // S6 新增两组的几何扫描（柱 / 灌木株位）——只在有 agg 桶时进，防"比了个空"
                if (a != null) {
                    scanSpinesAndShrubs(grid, side, dx, dz, topBefore, a, idx, shrubCols);
                }
            }
        }
        countShrubNeighbors(shrubCols, out);
    }

    /** 装饰侧列扫的上界（与 {@code ProsperityDecorPlacer.MAX_SURFACE_Y} 同量级；高度域上沿 108 远在其下）。 */
    private static final int MAX_SURFACE_Y_SCAN = 200;

    /**
     * 一个 chunk 内的两笔几何账（v1.20.41 P20 S6）：
     * <ol>
     * <li><b>风蚀柱</b>：从装饰前地表往上数连续的柱身料（{@code prosperityWastesBase}）竖段，
     * 段高 ≥{@link #WIND_STUMP_MIN_RUN} 记一柱。这条<b>不</b>依赖 sink 的方块分类，是独立的第二数法
     * （防"柱只在 sink 里被记一笔、实际没立起来"）；柱顶的粗沙覆料不计入段高，故
     * "柱顶 y − 地面 y ≥ 6"由段高 ≥7 直接蕴含。</li>
     * <li><b>灌木株位</b>：从装饰前地表往上数连续的干料（{@code KIND_LOG}）竖段，段高 ∈
     * {@code [1, SHRUB_TRUNK_MAX]} 的列记为株位（连同其 biome 桶下标），供
     * {@link #countShrubNeighbors} 算聚簇度。巨树的分叉枝段偶尔也是 1-2 格短段 ⇒ 计进株位；
     * 这类枝段空间上孤立，只会把聚簇度<b>拉低</b>（对"成簇"判据是保守方向，不会造假绿）。</li>
     * </ol>
     */
    private static void scanSpinesAndShrubs(Block[] grid, int side, int cx, int cz, int[] topBefore, Agg a, int idx,
        List<int[]> shrubCols) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                final int col = (cx * 16 + lx) + (cz * 16 + lz) * side;
                final int top = topBefore[col];
                if (top < 0 || top >= 254) {
                    continue;
                }
                int run = 0;
                int y = top + 1;
                while (y < 255 && grid[y * side * side + col] == BlocksGTSR.prosperityWastesBase) {
                    run++;
                    y++;
                }
                if (run >= WIND_STUMP_MIN_RUN) {
                    a.stumps++;
                    a.stumpRunMax = Math.max(a.stumpRunMax, run);
                    if (y < 255 && grid[y * side * side + col] == BlocksGTSR.prosperityCoarseSand) {
                        a.stumpCaps++; // 柱顶那一层粗沙覆料（要从"铺沙份额"的分子里精确扣掉）
                    }
                    continue; // 柱列不再当灌木
                }
                run = 0;
                y = top + 1;
                while (y < 255) {
                    final Block b = grid[y * side * side + col];
                    if (b == null || kind(b) != KIND_LOG) {
                        break;
                    }
                    run++;
                    y++;
                }
                if (run >= 1 && run <= SHRUB_TRUNK_MAX) {
                    a.shrubs++;
                    shrubCols.add(new int[] { cx * 16 + lx, cz * 16 + lz, idx });
                }
            }
        }
    }

    /**
     * 株位的同丛邻居合计（切比雪夫距离 ≤{@link #SHRUB_NEIGHBOR_RADIUS}，<b>不含</b>自身）。
     * 区内膜扫描：株位量级 ≈ 每区 1-2 千，{@code O(n²)} 在判据档可接受（实测每条 <b>秒级</b>）。
     */
    private static void countShrubNeighbors(List<int[]> cols, Map<Integer, Agg> out) {
        final int n = cols.size();
        for (int i = 0; i < n; i++) {
            final int[] p = cols.get(i);
            final Agg a = out.get(Integer.valueOf(p[2]));
            int neighbours = 0;
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                final int[] q = cols.get(j);
                if (Math.abs(p[0] - q[0]) <= SHRUB_NEIGHBOR_RADIUS && Math.abs(p[1] - q[1]) <= SHRUB_NEIGHBOR_RADIUS) {
                    neighbours++;
                }
            }
            // 邻居数记在<b>本株</b>自己的桶上（株位三元组第 3 元 = 采集时那次 ordinal 解析的名册下标，
            // 与 M 组同一次解析，不另起第二条身份取数路）
            if (a != null) {
                a.shrubNeighbors += neighbours;
            }
        }
    }

    /** 数一个 chunk 的干柱（同时累进 {@link Agg}）；返回本 chunk 树数（腹地记账用）。 */
    private static int countTrunks(Block[] grid, int side, int cx, int cz, Agg a) {
        if (a == null) {
            return 0;
        }
        int chunkTrees = 0;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                final int col = (cx * 16 + lx) + (cz * 16 + lz) * side;
                int run = 0;
                for (int y = 0; y < 256; y++) {
                    final Block b = grid[y * side * side + col];
                    if (b != null && kind(b) == KIND_LOG) {
                        run++;
                    } else if (run > 0) {
                        a.trees++;
                        a.logs += run;
                        a.trunk[Math.min(run, 95)]++;
                        run = 0;
                        chunkTrees++;
                    }
                }
                if (run > 0) {
                    a.trees++;
                    a.logs += run;
                    a.trunk[Math.min(run, 95)]++;
                    chunkTrees++;
                }
            }
        }
        return chunkTrees;
    }

    /**
     * 密度场均匀区（荒漠腹地）判据（P19 §G「腹地精确零」的测量口径）：chunk 中心列的三层密度
     * 全部精确为 0 ⇒ 三层掷骰门恒关，该 chunk 不可能落树。坐标与 {@code placeTreePass} 同式
     * （(chunkX&lt;&lt;4)+8）；与 DecorPlacer 同一 DensityField 出口，无第二真值。
     */
    private static boolean wastesInterior(long seed, int chunkX, int chunkZ) {
        final int bx = (chunkX << 4) + 8;
        final int bz = (chunkZ << 4) + 8;
        return DensityField.shrubDensityAt(seed, bx, bz) == 0.0D
            && DensityField.normalDensityAt(seed, bx, bz) == 0.0D
            && DensityField.megaDensityAt(seed, bx, bz) == 0.0D;
    }

    /** 腹地精确零的记账（M 组荒漠断言用）。 */
    private static long wastesInteriorChunks;
    private static long wastesInteriorTrees;

    private static Agg bucket(Map<Integer, Agg> out, int idx) {
        Agg a = out.get(Integer.valueOf(idx));
        if (a == null) {
            out.put(Integer.valueOf(idx), a = new Agg());
            a.chunks = 0;
        }
        a.chunks++;
        return a;
    }

    private static int ordinal(int cx, int cz) {
        return GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
            .ordinalAt((cx << 4) + 8, (cz << 4) + 8).ordinal;
    }

    /**
     * 单 chunk 世界采样（ANTI 臂用）：只取身份 == 目标档且<b>密度场均匀区（腹地）</b>的前 N 个
     * chunk，坐标集在臂间复用。v1.20.40 P19 §G 重钉：界带渐变后非腹地的荒漠 chunk 可含少量
     * 灌木（原档 trees=3 的 U6 现场），「原档零树」前置只在腹地（三层密度 ≡0 ⇒ 掷骰门恒关）
     * 成立——坐标集收腹地后三臂语义复原。
     */
    private static List<int[]> findChunks(int targetIdx, int count) {
        final List<int[]> hits = new ArrayList<>();
        final long seed = ANTI_SEED;
        outer: for (int kx = 0; kx < 220; kx++) {
            for (int kz = 0; kz < 220; kz++) {
                final int cx = 31000 + kx * 7;
                final int cz = -47000 + kz * 5;
                if (CityPlanner.citiesNear(seed, cx, cz).length > 0) {
                    continue;
                }
                if (ordinal(cx, cz) == targetIdx && wastesInterior(seed, cx, cz)) {
                    hits.add(new int[] { cx, cz });
                    if (hits.size() >= count) {
                        break outer;
                    }
                }
            }
        }
        return hits;
    }

    /** ANTI 臂固定 seed（保证三臂跑的是同一坐标集）。 */
    private static final long ANTI_SEED = 0x503444L;
    private static Agg measureSingleChunks(List<int[]> coords) throws Exception {
        final Agg agg = new Agg();
        final Map<Integer, Agg> out = new IdentityHashMap<>();
        out.put(Integer.valueOf(WASTES), agg);
        final GTSRWorldChunkManager mgrP = new GTSRWorldChunkManager(ANTI_SEED, defP);
        final java.lang.reflect.Method gen = SurfaceHarness.generateTerrain();
        final Block[] scratch = new Block[65536];
        final byte[] scratchMeta = new byte[65536];
        final Block[] grid = new Block[16 * 16 * 256];
        for (final int[] c : coords) {
            final int cx = c[0];
            final int cz = c[1];
            Arrays.fill(scratch, null);
            Arrays.fill(scratchMeta, (byte)0);
            final FlatWorld world = FlatWorld.of(grid, cx << 4, cz << 4, ANTI_SEED, 16);
            final GTSRChunkProviderBase provP = new ChunkProviderProsperityRuins(world, ANTI_SEED);
            gen.invoke(provP, cx, cz, scratch, scratchMeta, null);
            final BiomeGenBase[] plane = mgrP.loadBlockGeneratorData(null, cx * 16, cz * 16, 16, 16);
            SurfaceHarness.surfaceSeam(provP.getClass())
                .invoke(null, ANTI_SEED, cx * 16, cz * 16, scratch, scratchMeta, plane);
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    final int col = (lx << 12) | (lz << 8);
                    final int dst = lx + lz * 16;
                    for (int y = 0; y < 256; y++) {
                        grid[y * 256 + dst] = scratch[col | y];
                    }
                }
            }
            agg.chunks++;
            final CountingSink sink = new CountingSink(world, out);
            sink.chunk(cx, cz, WASTES, agg);
            ProsperityDecorPlacer.decorate(world, ANTI_SEED, cx, cz, WASTES, sink);
            countTrunks(grid, 16, 0, 0, agg);
        }
        return agg;
    }

    // ═════════════════════════════════ M / ORDER ═════════════════════════════════

    private static void assertBands(Map<Integer, Agg> m) {
        for (int idx = 0; idx < 4; idx++) {
            final Agg a = m.get(Integer.valueOf(idx));
            check(a != null && a.chunks > 0, "M 前置：档 " + idx + "（" + BIO[idx] + "）样本 chunk 数 > 0");
            if (a == null || a.chunks == 0) {
                continue;
            }
            band("M " + BIO[idx] + " 树数/chunk", div(a.trees, a.chunks), BAND_TREES[idx]);
            band("M " + BIO[idx] + " 干高 min", trunkMin(a), BAND_TRUNK_MIN[idx]);
            band("M " + BIO[idx] + " 干高 max", trunkMax(a), BAND_TRUNK_MAX[idx]);
            band("M " + BIO[idx] + " 干高 mean", trunkMean(a), BAND_TRUNK_MEAN[idx]);
            band("M " + BIO[idx] + " 叶/chunk", div(a.leaves, a.chunks), BAND_LEAVES[idx]);
            band("M " + BIO[idx] + " 草/chunk", div(a.tufts, a.chunks), BAND_TUFTS[idx]);
            band("M " + BIO[idx] + " 花/chunk", div(a.flowers, a.chunks), BAND_FLOWERS[idx]);
            band("M " + BIO[idx] + " 沙砾/chunk", div(a.sand, a.chunks), BAND_SAND[idx]);
            if (idx == WASTES) {
                // v1.20.40 P19 §G 重钉（归因 U6 prered）：密度场界带渐变后荒漠可含少量界带灌木
                //（量级由 WASTES_LEAK 率带钉）；「绝对零」改钉<b>腹地精确零</b>——密度场均匀区
                //（三层密度 ≡0）的 chunk 掷骰门恒关，必须 trees == 0（旧"区域精确零树"口径退役）。
                check(wastesInteriorChunks > 0 && wastesInteriorTrees == 0,
                    "M 荒漠腹地精确零树：密度场均匀区（三层密度 ≡0）chunk=" + wastesInteriorChunks
                        + " 落树=" + wastesInteriorTrees + "（P19 §G 腹地零树 + 界带渐变口径）");
            } else {
                check(a.trees > 0, "M 有树群系 " + BIO[idx] + " trees > 0（实测 " + a.trees + "）");
                check(a.flowers > 0, "M 有树群系 " + BIO[idx] + " flowers > 0（改前全密度 = 0）");
            }
        }
        check(sinkDrops == 0, "M 零越界落块（冠层半径与干位内收自洽，跨界协议未破）；实测 " + sinkDrops);
    }

    // ═════════════════ v1.20.41 P20 S6：风蚀柱 / 灌木成簇 / 粗沙份额 ═════════════════

    /**
     * S6 组（需求 5 的风蚀柱 + 需求 6 的灌木群 + 需求 5 的"粗"砂）：三条各抓一个不同的轴，
     * 任何一条单独绿都不足以说明改动落地了。
     * <ol>
     * <li><b>量</b>：荒漠沙砾块数/chunk（{@link #BAND_SAND}）与<b>质</b>：粗沙份额
     * （{@link #BAND_COARSE_SHARE}）——只钉量的话，把主料留在细沙也算"绿"；</li>
     * <li><b>风蚀柱</b>：柱数/chunk 落带 ＋ <b>其余三群系必须精确 0 柱</b>（档值抑制的跨群系自证，
     * 形状同"铺沙不外溢"那条 DECL）；柱身竖段由网格几何独立数出，不走 sink 分类 ⇒ 第二数法；</li>
     * <li><b>灌木</b>：株数/chunk（总量，{@link #BAND_SHRUBS}）与<b>聚簇度</b>
     * （每株平均同丛邻居数，{@link #BAND_SHRUB_NEIGHBORS}）——需求原话是"灌木<b>群</b>"，
     * 只钉总量的话均匀散点也绿（改前实测聚簇度 = <b>0.000000</b>，见同一份 before 日志）。</li>
     * </ol>
     */
    private static void assertSpineShrubAndSand(Map<Integer, Agg> m) {
        for (int idx = 0; idx < 4; idx++) {
            final Agg a = get(m, idx);
            if (a.chunks == 0) {
                continue;
            }
            if (idx != WASTES) {
                check(a.stumps == 0, "S6 非荒漠档 " + BIO[idx] + " 零风蚀柱（档表 0 = 一条骰都不掷）；实测 " + a.stumps);
            } else {
                band("S6 荒漠 风蚀柱数/chunk", div(a.stumps, a.chunks), BAND_WIND_STUMPS);
                check(a.stumpRunMax >= WIND_STUMP_MIN_RUN,
                    "S6 荒漠柱高最值 ≥ " + WIND_STUMP_MIN_RUN + "（柱真的立起来了，不是 sink 记账假绿）；实测 max run="
                        + a.stumpRunMax);
                // 柱顶粗沙覆料与铺沙同类方块 ⇒ 会从 sink 的 coarse/sand 里混进来；按<b>几何扫出的精确
                // 覆料数</b>同扣分子分母（扣柱数是近似，已弃）⇒ 份额量的是"铺沙趟"自己的质。
                final double total = a.sand - a.stumpCaps;
                final double coarse = a.sandCoarse - a.stumpCaps;
                check(total > 0 && coarse >= 0.0D, "S6 前置：荒漠沙砾样本 > 0 且粗沙份额分子非负（覆料已精确扣除）；"
                    + "实测 sand=" + a.sand + " coarse=" + a.sandCoarse + " caps=" + a.stumpCaps);
                check(a.stumpCaps <= a.stumps, "S6 前置：柱顶覆料数 ≤ 柱数（每柱至多一层覆料）；实测 caps="
                    + a.stumpCaps + " stumps=" + a.stumps);
                band("S6 荒漠 粗沙占铺沙份额", ddiv(coarse, total), BAND_COARSE_SHARE);
                check(a.sandFine == 0L,
                    "S6 荒漠铺沙里细沙不再被放置（档表主料已换粗沙；改前细沙占 2/3）；实测 fine=" + a.sandFine);
            }
            if (idx == STEPPE) {
                band("S6 草原 灌木株数/chunk（短干竖段）", div(a.shrubs, a.chunks), BAND_SHRUBS_STEPPE);
                band("S6 草原 灌木聚簇度（每株同丛邻居均值）", div(a.shrubNeighbors, a.shrubs), BAND_SHRUB_NEIGHBORS);
            }
        }
        final Agg w = get(m, WASTES);
        final Agg s = get(m, STEPPE);
        System.out.println("VEG-S6 wastes sand=" + w.sand + " coarse/gravel/fine=" + w.sandCoarse + "/" + w.sandGravel
            + "/" + w.sandFine + " stumps=" + w.stumps + "(maxRun " + w.stumpRunMax + ") | steppe shrubs=" + s.shrubs
            + " neighbours=" + s.shrubNeighbors + " meanNeighbor=" + fmt(div(s.shrubNeighbors, s.shrubs))
            + " | ORDER 森/原=" + fmt(ddiv(ddiv(get(m, FOREST).trees, get(m, FOREST).chunks),
                ddiv(s.trees, s.chunks))));
    }

    private static void assertBehaviouralOrder(Map<Integer, Agg> m) {
        final double f = div(get(m, FOREST).trees, get(m, FOREST).chunks);
        final double w = div(get(m, SWAMP).trees, get(m, SWAMP).chunks);
        final double s = div(get(m, STEPPE).trees, get(m, STEPPE).chunks);
        final double d = div(get(m, WASTES).trees, get(m, WASTES).chunks);
        // v1.20.40 P19 §G 重钉：严格偏序 森>沼>原>沙 保留；「沙==0 精确项」随界带渐变退役
        //（沙的绝对量级已由 M 组 WASTES_LEAK 率带与腹地精确零钉住，此处只钉偏序）。
        check(f > w && w > s && s > d,
            "ORDER 真实链树密度 森(" + fmt(f) + ") > 沼(" + fmt(w) + ") > 原(" + fmt(s) + ") > 沙(" + fmt(d)
                + "，界带渗入上限见 M 组带)");
        final double tf = trunkMean(get(m, FOREST));
        final double tw = trunkMean(get(m, SWAMP));
        final double ts = trunkMean(get(m, STEPPE));
        check(tf > tw && tw > ts,
            "ORDER 真实链干高均值 森(" + fmt(tf) + ") > 沼(" + fmt(tw) + ") > 原(" + fmt(ts) + ")");
        // 用<b>每 chunk 密度比</b>而不是绝对数比：四群系 chunk 数本身不等权（实测 404/346/495/528），
        // 拿绝对数比会把"面积抽样不等"当成"密度不分流"。
        // T8 重钉（归因 T7 三档）：灌木 1/2 把草原密度抬到 ~1.0/chunk，森林/平原比值从单档时代
        // 的 ~8× 压到 3.2×；需求语义「森林树明显更多、量级可辨」改钉 ≥2.5×（实测 3.19×）。
        check(div(get(m, FOREST).trees, get(m, FOREST).chunks) > 2.5D * div(get(m, STEPPE).trees,
            get(m, STEPPE).chunks),
            "ORDER 森林树密度 > 平原 2.5 倍（T7 三档口径）；实测 "
                + fmt(div(get(m, FOREST).trees, get(m, FOREST).chunks))
                + " vs " + fmt(div(get(m, STEPPE).trees, get(m, STEPPE).chunks)));
        final Agg all = total(m);
        check(div(all.trees, all.chunks) > 3.0D * PRE_TREES_PER_CHUNK,
            "ORDER 全图树密度较改前(" + fmt(PRE_TREES_PER_CHUNK) + ") 升 > 3×；实测 "
                + fmt(div(all.trees, all.chunks)));
        check(div(all.tufts + all.flowers, all.chunks) > 2.0D * PRE_FLORA_PER_CHUNK,
            "ORDER 全图花草密度较改前(" + fmt(PRE_FLORA_PER_CHUNK) + ") 升 > 2×；实测 "
                + fmt(div(all.tufts + all.flowers, all.chunks)));
        for (int idx = 0; idx < 4; idx++) {
            final Agg a = get(m, idx);
            check(a.chunks == 0 || div(a.flowers + a.tufts, a.chunks) > 1.0D,
                "ORDER " + BIO[idx] + " 花草合计 > 1 件/chunk；实测 " + fmt(div(a.flowers + a.tufts, a.chunks)));
        }
    }

    // ═════════════════════════════════ ANTI ═════════════════════════════════

    private static void assertDesertSensitivity(int antiChunks) throws Exception {
        // 先把身份源绑到 ANTI_SEED（GTSRWorldChunkManager 构造期 bind），否则 findChunks 读到的是
        // 上一个 seed 的链 ⇒ "同一坐标集"这条话不成立。
        new GTSRWorldChunkManager(ANTI_SEED, defP);
        final List<int[]> coords = findChunks(WASTES, antiChunks);
        final VegTier original = ProsperityDecorPlacer.VEG_TIERS_BY_ROSTER[WASTES];
        final Agg before = measureSingleChunks(coords);
        check(coords.size() >= 20 && before.chunks == coords.size(),
            "ANTI 前置：荒漠单 chunk 样本 = " + coords.size() + "（测量记录 " + before.chunks + "）");
        check(before.trees == 0 && before.logs == 0,
            "ANTI 前置：原档下同坐标集 trees/logs == 0；实测 " + before.trees + "/" + before.logs);
        check(before.sand > 0 && before.flowers > 0 && before.tufts > 0,
            "ANTI 前置：同坐标集上花/草/沙砾均 > 0（⇒ 零树不是「整趟没跑」，沙 " + before.sand + " 花 " + before.flowers
                + " 草 " + before.tufts + "）");
        ProsperityDecorPlacer.VEG_TIERS_BY_ROSTER[WASTES] = new VegTier(1, 1, original.trunkMin, original.trunkSpan,
            original.canopyRadius, original.woodPrimary, original.woodSecondary, original.woodMixDenom,
            original.grassRolls, original.flowerRolls, original.sandRolls, original.flowerKind, original.grassKind,
            original.sandKind);
        final Agg arm;
        try {
            arm = measureSingleChunks(coords);
        } finally {
            ProsperityDecorPlacer.VEG_TIERS_BY_ROSTER[WASTES] = original;
        }
        check(arm.trees > 0, "ANTI 荒漠档改成必掷 ⇒ 同坐标集 trees > 0（实测 " + arm.trees + "/" + arm.chunks
            + "）；不成立＝「沙漠零树」绿在一条不可达分支上");
        band("ANTI 必掷臂 树数/chunk", div(arm.trees, arm.chunks), BAND_ANTI_WASTES);
        check(arm.logs == arm.trunkBlockSum() && arm.trees == arm.trunkSum() && arm.trees > 0,
            "ANTI 臂：干格总数 == 干高直方加权合计 且 树数 == 直方合计（两种数法同体，防网格扫描与 sink 计数"
                + "各说各话）；实测 logs=" + arm.logs + " blockSum=" + arm.trunkBlockSum() + " trees=" + arm.trees
                + " trunkSum=" + arm.trunkSum());
        check(before.logs == before.trunkBlockSum() && before.trees == before.trunkSum() && before.trees == 0,
            "ANTI 前置臂：同一对合计在\"原档零树\"侧也成立（0 == 0）；实测 logs=" + before.logs
                + " trees=" + before.trees);
        final Agg restored = measureSingleChunks(coords);
        check(restored.trees == 0 && restored.digest()
            .equals(before.digest()), "ANTI 还原自证：换回原档后同坐标集逐位回到第一臂（trees 归 0 且全部计数相同）");
        check(ProsperityDecorPlacer.VEG_TIERS_BY_ROSTER[WASTES] == original, "ANTI 还原自证：档数组引用已复原");
    }

    // ═════════════════════════════════ ROSTER ═════════════════════════════════

    private static void assertRosterConsumed() throws Exception {
        final String loader = read(Paths.get("src/main/java/com/miaokatze/gtsr/loader/BlockLoader.java"));
        final String blocks = read(Paths.get(
            "src/main/java/com/miaokatze/gtsr/common/blocks/BlocksGTSR.java"));
        final String gate = read(Paths.get(
            "src/main/java/com/miaokatze/gtsr/common/dimension/framework/SurfaceGate.java"));
        final String unify = read(Paths.get("tools/dim1/SurfaceGateUnifyCheck.java"));
        final String matrix = read(Paths.get("tools/dim1/SurfaceBiomeMatrixCheck.java"));
        final String harness = read(Paths.get("tools/dim1/SurfaceHarness.java"));
        final String decor = read(Paths.get(DECOR_SRC));
        final String[] sand = { "prosperitySilicaSand", "prosperityCoarseSand", "prosperityRiverGravel" };
        // T2/T8 重钉（plan §3.9 G8）：coarseSand/riverGravel 切 BlockProsperityFalling（注册名不变），
        // silicaSand 维持非重力 NaturalBase——实例化类族按方块分钉，不再一刀切 NaturalBase。
        final java.util.Map<String, String> sandCtor = new java.util.HashMap<>();
        sandCtor.put("prosperitySilicaSand", "BlockProsperityNaturalBase");
        sandCtor.put("prosperityCoarseSand", "BlockProsperityFalling");
        sandCtor.put("prosperityRiverGravel", "BlockProsperityFalling");
        for (final String f : sand) {
            check(blocks.contains("public static Block " + f + ";"), "ROSTER BlocksGTSR 缺字段 " + f);
            check(loader.contains("BlocksGTSR." + f + " = new " + sandCtor.get(f) + "("),
                "ROSTER BlockLoader 缺实例化（类族 " + sandCtor.get(f) + "）" + f);
            check(loader.contains("GameRegistry.registerBlock(BlocksGTSR." + f + ", \"" + capitalize(f) + "\")"),
                "ROSTER BlockLoader 缺注册名 " + f);
            check(loader.contains("gtsr:" + texOf(f)), "ROSTER BlockLoader 注册段缺贴图名 gtsr:" + texOf(f));
            check(!gate.contains(f), "ROSTER SurfaceGate 源文本零命中 " + f + "（DIM78_SIZE 恒 5 纪律）");
            check(unify.contains("\"gtsr:" + f + "\""), "ROSTER MUST_NOT_PASS 缺 " + f + "（静默面之三）");
            check(matrix.contains("\"" + f + "\""), "ROSTER NATURAL_BLOCKS 缺 " + f + "（静默面之一）");
            check(harness.contains("BlocksGTSR." + f + " = new "), "ROSTER SurfaceHarness 缺装配行 " + f
                + "（静默面之二：漏了就没有任何判据红，但新方块永久不被覆盖）");
            check(decor.contains("BlocksGTSR." + f), "ROSTER 装饰层未读 " + f + "（注册了但没人消费）");
        }
        check(count(decor, "sandOf(") >= 4, "ROSTER 装饰层沙料解析调用 >= 4（细/粗/砾三样都有取用路径）；实测 "
            + count(decor, "sandOf("));
        final String en = read(Paths.get("src/main/resources/assets/gtsr/lang/en_US.lang"));
        final String zh = read(Paths.get("src/main/resources/assets/gtsr/lang/zh_CN.lang"));
        for (final String f : sand) {
            final String key = "tile." + capitalize(f) + ".name=";
            check(count(en, key) == 1 && count(zh, key) == 1, "ROSTER 两份 lang 各含恰 1 条 " + key);
        }
    }

    private static String capitalize(String field) {
        return Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    private static String texOf(String field) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            final char c = field.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ═════════════════════════════════ 计数/工具 ═════════════════════════════════

    private static Agg get(Map<Integer, Agg> m, int idx) {
        final Agg a = m.get(Integer.valueOf(idx));
        return a == null ? new Agg() : a;
    }

    private static void band(String label, double v, double[] range) {
        check(v >= range[0] && v <= range[1], label + " = " + fmt(v) + " ∉ 带 [" + range[0] + ", " + range[1] + "]");
    }

    private static void band(String label, long v, int[] range) {
        check(v >= range[0] && v <= range[1], label + " = " + v + " ∉ 带 [" + range[0] + ", " + range[1] + "]");
    }

    private static int trunkMin(Agg a) {
        for (int i = 0; i < a.trunk.length; i++) {
            if (a.trunk[i] > 0) {
                return i;
            }
        }
        return 0;
    }

    private static int trunkMax(Agg a) {
        for (int i = a.trunk.length - 1; i >= 0; i--) {
            if (a.trunk[i] > 0) {
                return i;
            }
        }
        return 0;
    }

    private static double trunkMean(Agg a) {
        long n = 0;
        long sum = 0;
        for (int i = 0; i < a.trunk.length; i++) {
            n += a.trunk[i];
            sum += (long)i * a.trunk[i];
        }
        return n == 0 ? 0D : (double)sum / n;
    }

    /** double 域的比值（{@link #div(long,long)} 之外另立一件：S6 的份额/比值分子分母是扣过覆料的 double）。 */
    private static double ddiv(double a, double b) {
        return b <= 0.0D ? Double.NaN : a / b;
    }

    private static double div(long a, long b) {
        return b == 0 ? 0D : (double)a / b;
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.6f", d);
    }

    private static void check(boolean ok, String msg) {
        if (ok) {
            passed++;
        } else {
            FAILURES.add(msg);
        }
    }

    private static int count(String hay, String needle) {
        int n = 0;
        for (int i = hay.indexOf(needle); i >= 0; i = hay.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    private static String methodBody(String code, String signaturePrefix) {
        final int sig = code.indexOf(signaturePrefix);
        if (sig < 0) {
            return "";
        }
        final int open = code.indexOf('{', sig);
        if (open < 0) {
            return "";
        }
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            if (code.charAt(i) == '{') {
                depth++;
            } else if (code.charAt(i) == '}' && --depth == 0) {
                return code.substring(open, i + 1);
            }
        }
        return "";
    }

    /**
     * flat 口径（v1.20.38 纪律 2）：stripComments 之后把全部空白（含换行）折叠为单个空格——
     * 源级 needle 一律经本方法匹配，防 spotlessApply 折行打死按行文本。
     */
    private static String flat(String text) {
        return stripComments(text).replaceAll("\\s+", " ");
    }

    /**
     * condense 口径（T8）：flat 的加强版——去掉<b>全部</b>空白。spotless 会把
     * {@code ProsperityDecorPlacer.decorate(...)} 折成两行（类名与 .decorate 分行），
     * flat 折叠出的 "X .decorate(" 仍打不死匹配；condense 对任意换行形态稳健。
     */
    private static String condense(String text) {
        return flat(text).replace(" ", "");
    }

    private static String stripComments(String text) {
        final StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                final int end = skipLiteral(text, i);
                sb.append(text, i, end);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                while (i < text.length() && text.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                final int end = text.indexOf("*/", i + 2);
                i = end < 0 ? text.length() : end + 2;
                sb.append(' ');
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static int skipLiteral(String text, int start) {
        final char quote = text.charAt(start);
        int i = start + 1;
        while (i < text.length()) {
            if (text.charAt(i) == '\\') {
                i += 2;
                continue;
            }
            if (text.charAt(i) == quote) {
                return i + 1;
            }
            i++;
        }
        return i;
    }

    private static String read(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static int kind(Block b) {
        final Integer k = KINDS.get(b);
        return k == null ? KIND_NONE : k.intValue();
    }

    /** 写回网格并按身份分类计数（干格由网格竖直段扫描计，这里不重复计）。 */
    static final class CountingSink implements BlockSink {

        private final FlatWorld world;
        private final Map<Integer, Agg> out;
        private int cx;
        private int cz;
        private int idx;
        private Agg agg;
        /** S8 只报读数用：装饰前逐列地表 + 该列网格索引换算（{@code VEG_DUMP=1} 时才打印）。 */
        private int[] topBeforeDump;
        private Block[] gridDump;
        private int sideDump;
        private int baseXDump;
        private int baseZDump;
        private String tileDump = "?";

        CountingSink(FlatWorld world, Map<Integer, Agg> out) {
            this.world = world;
            this.out = out;
        }

        /** S8 重载：附带列级上下文，仅当 {@code VEG_DUMP=1} 时用于打印只报读数。 */
        CountingSink(FlatWorld world, Map<Integer, Agg> out, int[] topBefore, Block[] grid, int side, int cx0,
            int cz0, String tile) {
            this(world, out);
            this.topBeforeDump = topBefore;
            this.gridDump = grid;
            this.sideDump = side;
            this.baseXDump = cx0 << 4;
            this.baseZDump = cz0 << 4;
            this.tileDump = tile;
        }

        void chunk(int cx, int cz, int idx, Agg agg) {
            this.cx = cx;
            this.cz = cz;
            this.idx = idx;
            this.agg = agg;
            if (out.get(Integer.valueOf(idx)) != agg) {
                out.put(Integer.valueOf(idx), agg);
            }
        }

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            if ((x >> 4) != cx || (z >> 4) != cz || y < 0 || y > 255 || !(block instanceof Block)) {
                sinkDrops++; // 跨界协议违例（本层应当恒为 0）
                return false;
            }
            final Block b = (Block)block;
            final int kind = kind(b);
            if (plantDumpIdx >= 0 && gridDump != null && this.idx == plantDumpIdx
                && (kind == KIND_LOG || kind == KIND_LEAF)) {
                final int col = (x - baseXDump) + (z - baseZDump) * sideDump;
                final int gt = (col >= 0 && col < topBeforeDump.length) ? topBeforeDump[col] : -2;
                final String gb = (gt >= 0 && gridDump != null)
                    ? String.valueOf(gridDump[gt * sideDump * sideDump + col].getClass().getSimpleName()) : "-";
                System.out.println("VEG-PLANT t=" + tileDump + " k=" + (kind == KIND_LOG ? "L" : "F") + " cx=" + cx
                    + " cz=" + cz + " x=" + x + " z=" + z + " y=" + y + " gtop=" + gt + " gblk=" + gb);
            }
            switch (kind) {
                case KIND_LEAF: {
                    agg.leaves++;
                    break;
                }
                case KIND_TUFT: {
                    agg.tufts++;
                    break;
                }
                case KIND_FLOWER: {
                    agg.flowers++;
                    break;
                }
                case KIND_SAND: {
                    agg.sand++;
                    // v1.20.41 P20 S6 需求 5 的"质"档：三种沙/砾分开数（需求原话是"更多的<b>粗</b>砂粒"，
                    // 只数合计块数的 BAND_SAND 分不清"细沙变多"与"粗沙变多"这两件事）
                    if (b == BlocksGTSR.prosperityCoarseSand) {
                        agg.sandCoarse++;
                    } else if (b == BlocksGTSR.prosperityRiverGravel) {
                        agg.sandGravel++;
                    } else {
                        agg.sandFine++;
                    }
                    break;
                }
                default: {
                    break;
                }
            }
            world.write(x, y, z, b);
            return true;
        }
    }

    // ═════════════════════════════ 离线装配与世界 ═════════════════════════════

    private static void bootstrap() {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        // 装饰域补齐（SurfaceHarness.blockFamily 不含这五件；缺席 ⇒ 落块测成假 0，
        // 理由同 Dim78ScatterDensityCheck.extraBlocks 的 P4 实测坑）
        if (BlocksGTSR.prosperitySurface == null) {
            BlocksGTSR.prosperitySurface = new BlockProsperitySurface();
        }
        if (BlocksGTSR.prosperityTuftRust == null) {
            BlocksGTSR.prosperityTuftRust = new BlockProsperityTuft("ProsperityTuftRust",
                "gtsr:prosperity_tuft_rust");
        }
        if (BlocksGTSR.prosperityTuftCopper == null) {
            BlocksGTSR.prosperityTuftCopper = new BlockProsperityTuft("ProsperityTuftCopper",
                "gtsr:prosperity_tuft_copper");
        }
        if (BlocksGTSR.prosperityRustLog == null) {
            BlocksGTSR.prosperityRustLog = new BlockProsperityRustLog("ProsperityRustLog",
                "gtsr:prosperity_rust_log_side", "gtsr:prosperity_rust_log_top");
        }
        if (BlocksGTSR.prosperityRustLeaves == null) {
            BlocksGTSR.prosperityRustLeaves = new BlockProsperityRustLeaves("ProsperityRustLeaves",
                "gtsr:prosperity_rust_leaves");
        }
        if (BlocksGTSR.prosperitySilicaSand == null) {
            BlocksGTSR.prosperitySilicaSand = new BlockProsperityNaturalBase("ProsperitySilicaSand",
                "gtsr:prosperity_silica_sand");
        }
        if (BlocksGTSR.prosperityCoarseSand == null) {
            BlocksGTSR.prosperityCoarseSand = new BlockProsperityNaturalBase("ProsperityCoarseSand",
                "gtsr:prosperity_coarse_sand");
        }
        if (BlocksGTSR.prosperityRiverGravel == null) {
            BlocksGTSR.prosperityRiverGravel = new BlockProsperityNaturalBase("ProsperityRiverGravel",
                "gtsr:prosperity_river_gravel");
        }
        final List<String> missing = new ArrayList<>();
        for (final String f : new String[] { "prosperitySilicaSand", "prosperityCoarseSand",
            "prosperityRiverGravel", "prosperityFlowerPatina", "prosperityTuftSedge", "prosperityBrassLog",
            "prosperityCopperLeaves", "prosperityMarshLog" }) {
            try {
                if (((Block)BlocksGTSR.class.getField(f)
                    .get(null)) == null) {
                    missing.add(f);
                }
            } catch (ReflectiveOperationException e) {
                missing.add(f + "(无字段)");
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("S-B2/S-B1 名册方块缺席，测出来必然是假读数：" + missing);
        }
        pb = SurfaceHarness.prosperityBiomes();
        sb = SurfaceHarness.shatteredBiomes();
        defP = SurfaceHarness.def(true, pb, SurfaceHarness.prosperityWeights());
        SurfaceHarness.recordAllAllocations(pb, sb);
        buildKinds();
        if (SurfaceGate.landableTops(SurfaceGate.DIM78).length != 5) {
            throw new IllegalStateException("SurfaceGate dim78 名册不是 5 员 = "
                + SurfaceGate.landableTops(SurfaceGate.DIM78).length + "（S-B2 禁改 top 名册）");
        }
    }

    private static void buildKinds() {
        put(BlocksGTSR.prosperityRustLog, KIND_LOG);
        put(BlocksGTSR.prosperityCopperLog, KIND_LOG);
        put(BlocksGTSR.prosperityBrassLog, KIND_LOG);
        put(BlocksGTSR.prosperityMarshLog, KIND_LOG);
        put(BlocksGTSR.prosperityRustLeaves, KIND_LEAF);
        put(BlocksGTSR.prosperityCopperLeaves, KIND_LEAF);
        put(BlocksGTSR.prosperityBrassLeaves, KIND_LEAF);
        put(BlocksGTSR.prosperityMarshLeaves, KIND_LEAF);
        put(BlocksGTSR.prosperityTuftRust, KIND_TUFT);
        put(BlocksGTSR.prosperityTuftCopper, KIND_TUFT);
        put(BlocksGTSR.prosperityTuftSedge, KIND_TUFT);
        put(BlocksGTSR.prosperityTuftBristle, KIND_TUFT);
        put(BlocksGTSR.prosperityFlowerRust, KIND_FLOWER);
        put(BlocksGTSR.prosperityFlowerPatina, KIND_FLOWER);
        put(BlocksGTSR.prosperityFlowerBrass, KIND_FLOWER);
        put(BlocksGTSR.prosperityFlowerMarsh, KIND_FLOWER);
        put(BlocksGTSR.prosperitySilicaSand, KIND_SAND);
        put(BlocksGTSR.prosperityCoarseSand, KIND_SAND);
        put(BlocksGTSR.prosperityRiverGravel, KIND_SAND);
    }

    private static void put(Block b, int kind) {
        KINDS.put(b, Integer.valueOf(kind));
    }

    /** 平坦网格合成世界（口径逐字承袭 SurfaceGateUnifyCheck.RegionWorld / GridWorld）。 */
    static final class FlatWorld extends World {

        Block[] grid;
        int originBlockX;
        int originBlockZ;
        int side;
        long seedValue;

        private FlatWorld() {
            super((ISaveHandler)null, (String)null, (WorldProvider)null, (WorldSettings)null, (Profiler)null);
        }

        static FlatWorld of(Block[] grid, int originBlockX, int originBlockZ, long seed, int side) throws Exception {
            final sun.misc.Unsafe u = SurfaceHarness.unsafe();
            final FlatWorld w = (FlatWorld)u.allocateInstance(FlatWorld.class);
            w.grid = grid;
            w.originBlockX = originBlockX;
            w.originBlockZ = originBlockZ;
            w.side = side;
            w.seedValue = seed;
            final WorldInfo info = (WorldInfo)u.allocateInstance(WorldInfo.class);
            final Field sf = WorldInfo.class.getDeclaredField("randomSeed");
            sf.setAccessible(true);
            u.putLong(info, u.objectFieldOffset(sf), seed);
            final Field wf = World.class.getDeclaredField("worldInfo");
            wf.setAccessible(true);
            u.putObject(w, u.objectFieldOffset(wf), info);
            final VegProvider p = (VegProvider)u.allocateInstance(VegProvider.class);
            final Field dim = WorldProvider.class.getDeclaredField("dimensionId");
            dim.setAccessible(true);
            u.putInt(p, u.objectFieldOffset(dim), 78);
            p.seedValue = seed;
            final Field pf = World.class.getDeclaredField("provider");
            pf.setAccessible(true);
            u.putObject(w, u.objectFieldOffset(pf), p);
            return w;
        }

        private int index(int x, int y, int z) {
            if (y < 0 || y > 255) {
                return -1;
            }
            final int lx = x - originBlockX;
            final int lz = z - originBlockZ;
            if (lx < 0 || lz < 0 || lx >= side || lz >= side) {
                return -1;
            }
            return (y * side + lz) * side + lx;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            if (y > 255) {
                return Blocks.bedrock; // 镜像 vanilla 界外实心口径（防上界假绿）
            }
            final int i = index(x, y, z);
            return i < 0 ? null : grid[i];
        }

        @Override
        public boolean isAirBlock(int x, int y, int z) {
            if (y > 255) {
                return false;
            }
            final int i = index(x, y, z);
            if (i < 0) {
                return true; // 区外 = 未加载；落点全部钳在本 chunk 内，故不影响计数
            }
            final Block b = grid[i];
            return b == null || b.getMaterial() == Material.air;
        }

        void write(int x, int y, int z, Block block) {
            final int i = index(x, y, z);
            if (i >= 0) {
                grid[i] = block == Blocks.air ? null : block;
            }
        }

        @Override
        public int getActualHeight() {
            return 256;
        }

        @Override
        public long getSeed() {
            return seedValue;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int entityId) {
            return null;
        }
    }

    /** 带种子/维号的 provider（dimensionId = 78 ⇒ L1 身份与 SurfaceGate 维度键都取得到）。 */
    public static final class VegProvider extends WorldProvider {

        long seedValue;

        @Override
        public String getDimensionName() {
            return "gtsr-p17-sb2-veg";
        }

        @Override
        public long getSeed() {
            return seedValue;
        }
    }

    private static void quietLogging() {
        try {
            final Path cfg = Paths.get("temp", "p17-sb2-log4j2.xml");
            Files.createDirectories(cfg.getParent());
            Files.write(cfg, ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Configuration status=\"OFF\"><Loggers><Root level=\"OFF\"/></Loggers></Configuration>\n")
                    .getBytes(StandardCharsets.UTF_8));
            System.setProperty("log4j.configurationFile", cfg.toAbsolutePath().toString());
        } catch (Exception e) {
            System.out.println("NOTE: log4j quiet bootstrap failed (" + e.getClass().getSimpleName() + ")");
        }
    }
}
