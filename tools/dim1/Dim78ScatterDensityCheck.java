import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

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
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRustLeaves;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRustLog;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperitySurface;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityTuft;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinedCasing;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinDebris;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperitySurfaceScatter;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityWorldGenerator;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;

/**
 * <b>P5 散布密度实测器</b>（plan §5 P5 / §6「ContourBudgetCheck / RegionRepeatCapCheck 常驻复算」/
 * 消解症状 S2 柱阵）。同一份采样代码给出三件事的证据：① K × 竖向件的参数扫描表；
 * ② 改造前 vs 回退档的<b>落块摘要对拍</b>（判据 3）；③ 非散布路径的落点计数（判据 5）。
 * <p>
 * ═══ 测量协议（全部走生产代码，零复制、零手工注册群系）═══
 * <ol>
 * <li><b>真实地形与真实配槽链</b>：反射调真实 {@code GTSRChunkProviderBase.generateTerrain} → 真实
 * {@code GTSRWorldChunkManager.loadBlockGeneratorData} → 真实 {@code applyBiomeSurface} 静态缝；
 * 群系名册走 {@link SurfaceHarness#recordAllAllocations}（正常态 {@code degraded=NONE}、id 180-183），
 * <b>不 new 群系绕过注册</b>（任务包禁止项）；</li>
 * <li><b>真实编排顺序</b>：每 chunk 先 {@link ProsperityOutpostPlacer#placeAll}，未命中才
 * {@link RuinedMachinePlacer#placeAll}（= 生产 {@code ProsperityWorldGenerator.generate} 2→3 段互斥掷骰
 * 的同一段代码顺序，本工具不改其语义）；城 buffer 窗用同源 {@link CityPlanner#citiesNear}
 * 排除（= 生产 :97 的 return）；散布 {@link ProsperitySurfaceScatter#scatter} 在二者之后跑；</li>
 * <li><b>群系散布权重</b>走 {@link ProsperityWorldGenerator#weightForRosterIndex} +
 * {@link GTSRBiomeAuthority#ordinalAt}（生产同一函数）；</li>
 * <li>前序阶段方块经落格 Sink 写回合成世界 ⇒ 散布看得见 outpost/机器的 's' 板面（生产 populate 语义）。</li>
 * </ol>
 * <b>区 == 一整个 H-2 窗</b>：每区 {@code axis×axis} chunk（axis 固定 16）且区原点<b>两轴都按 16 对齐</b>，
 * 这样"窗内同模板发射 chunk 数"不被区边界截断。（P4 样本的 z 轴原点 924817 非 16 的倍数，
 * 不能直接用于窗统计 ⇒ 本工具另设对齐原点，因此基线块的绝对值与 P4 的 100.58 是<b>不同样本</b>，
 * 只能同量级对照，不当作逐位复现。）
 * <p>
 * ═══ 为什么本文件<b>不直接引用</b> P5 新增的 Config 字段 ═══
 * 判据 3 的硬证据是「BASE 树（改造前）的散布落块摘要」与「AFTER 树把四键设回旧值的散布落块摘要」
 * <b>逐位相同</b>；两跑必须吃同一份工具源码，而 BASE 树没有那些字段 ⇒ 参数行一律经反射写入，
 * 字段缺失的树计入 {@code treeFieldsMissing} 并打印（不静默假装生效）。故本工具在 BASE 树上
 * 跑不出参数网格（每行都退化成改造前现状），只用于摘要对拍。
 * <p>
 * ═══ 口径申报（诚实性，plan §6 诚实性公理）═══
 * <ul>
 * <li><b>落块数</b> = Sink 接受的写入次数（烟囱顶部破口改写同一格会 +1）——与 P4 的
 * "scatterLanded=100.58 块/chunk" 同一口径，可直接对读；</li>
 * <li><b>烟囱柱数</b> = 落了 ≥1 个 chimney(meta3) 方块的<b>列数</b>——一柱恰占一列，无歧义；</li>
 * <li><b>件数（轮廓数）</b> = 由写入序列<b>重建</b>：同件判据「meta3 同列连续 y」/「meta1 同层同向相邻」，
 * 其余算新件。两个相邻且共线的独立管道段会被并成一件 ⇒ 件数是<b>下界</b>（实测下界损失 &lt; 1%：
 * 见 {@link Stats#pipeMergeUpperBound}，它同时给出"未被并掉的上界"）。主判据用落块数与柱数。</li>
 * </ul>
 * <b>用法</b>：
 * <pre>
 * java Dim78ScatterDensityCheck grid   [seeds] [regionsPerSeed] [axis=16]  # 参数网格（SCAN 行）
 * java Dim78ScatterDensityCheck digest [seeds] [regionsPerSeed] [axis=16]  # 只跑"改造前现状"行（对拍用）
 * 额外覆盖：-Dgtsr.p5.override=key=value,key=value（在参数行之后再套一层，用于影子/调试）
 * </pre>
 * 退出码：0 = 采样完成；2 = 环境/参数不可用。
 */
public final class Dim78ScatterDensityCheck {

    /** H-2 窗边长（chunk 数）——与 {@code ProsperitySurfaceScatter.WINDOW_CHUNKS} 同一常量值；
     *  这里<b>另写一份并带断言</b>：本工具在 BASE 树上也要能跑，不能引用 AFTER 才有的常量，
     *  故用反射读回并核对（{@link #assertWindowSideLengthMatchesProduction()}）。 */
    static final int WINDOW_CHUNKS = 16;

    /**
     * P7c：结构前序的显式跳过开关（{@code -Dgtsr.skipStructure=1}）。
     * <b>为什么需要</b>：[10] 的判据对象是"散布的回退位 == 改造前逐位相同"，而散布看得见 outpost/机器
     * 已经落进网格的 's' 板面（生产 populate 语义）。P7/P7c 之后结构前序本身是被改判的量
     * （接地改道 + 窗上限/间距），拿"含前序污染的整表"去要求逐位相同，测的就不再是散布那一件事。
     * 置 1 后 SCAN 行在<b>净地形</b>上对拍（比 P5 原口径更严格隔离），结构侧另有
     * {@code PlacementContractCheck} 的 T4/T7 与 {@link Sampler#assertChainAlive()} 负责。
     * CHAIN 行在该档下两侧同为 0（禁用位语义，见 {@code CHAIN-GATE SKIPPED-BY-CONFIG} 同级处理）。
     */
    static final boolean SKIP_STRUCTURE_STAGES =
        "1".equals(System.getProperty("gtsr.skipStructure", "0"));

    /** 一行参数 = 扫描表的一行。 */
    public static final class Row {

        /**
         * P5b：既有六参行的既有语义 = "P5 均匀档扫描表"（{@code GRID} 的 B0/B1/K*、
         * {@code ContourBudgetCheck} 的 B/C/D 行、{@code RegionRepeatCapCheck} 的 pin/relax 行
         * 都由 {@code tools/dim1} 的<b>不可改</b>调用方按六参构造），故六参行一律显式带
         * {@code prosperityScatterClusterMode=0} ⇒ 它们继续测 scatterUniform（P5 代码原样），
         * 成簇档的扫描/判据在 {@code ScatterClusterVarianceCheck} 与新行（七参）里。
         * BASE 树（P5b 前）无该键 ⇒ 反射登记 missing、行为不变（[10]/[13b] 摘要逐位仍绿）。
         */
        static final String LEGACY_UNIFORM_SPEC = "prosperityScatterClusterMode=0";

        public final String label;
        public final int contours;
        public final int blocks;
        public final int attempts;
        public final Boolean vertical;
        public final int windowCap;
        /** P5b 追加键 spec（null = 不带额外键；{@link #LEGACY_UNIFORM_SPEC} 之外的簇参数由七参构造给）。 */
        public final String extraSpec;

        public Row(String label, int contours, int blocks, int attempts, Boolean vertical, int windowCap) {
            this(label, contours, blocks, attempts, vertical, windowCap, LEGACY_UNIFORM_SPEC);
        }

        public Row(String label, int contours, int blocks, int attempts, Boolean vertical, int windowCap,
            String extraSpec) {
            this.label = label;
            this.contours = contours;
            this.blocks = blocks;
            this.attempts = attempts;
            this.vertical = vertical;
            this.windowCap = windowCap;
            this.extraSpec = extraSpec;
        }

        String overrideSpec() {
            final StringBuilder sb = new StringBuilder();
            put(sb, "prosperityScatterContoursPerChunk", contours);
            put(sb, "prosperityScatterBlocksPerChunk", blocks);
            put(sb, "prosperityScatterAttemptsPerChunk", attempts);
            put(sb, "prosperityScatterWindowRepeatCap", windowCap);
            if (vertical != null) {
                put(sb, "prosperityScatterVerticalPieces", vertical.toString());
            }
            if (extraSpec != null && !extraSpec.isEmpty()) {
                sb.append(',').append(extraSpec);
            }
            return sb.toString();
        }

        private static void put(StringBuilder sb, String key, Object value) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(key)
                .append('=')
                .append(value);
        }
    }

    /**
     * 参数网格（列名申报见 {@link #scanRow}）。第 1 行 = <b>现状基线</b>（件数天花板抬到不低于掷点上限
     * ⇒ 不约束 + 竖向件在 + 窗上限关 ⇒ 与改造前逐位同）；第 2 行隔离"只摘竖向件"
     * （plan §5 P5：该动作与 K 取值无关，可独立交付）；3-10 行 = K ∈ {4,6,8,12} × 竖向件 {摘,留}；
     * 11 行 = 竖向件留 + H-2 窗上限 2（U3「柱 ≤2/窗」的兑现形态）；12 行 = 拟定的默认档全量组合。
     */
    public static final Row[] GRID = { row("B0-现状柱阵(件数=掷点上限,竖向件在,窗上限关)", 64, 0, 64, Boolean.TRUE, 0),
        row("B1-只摘竖向件(件数不约束)", 64, 0, 64, Boolean.FALSE, 0), row("K4-摘竖向件", 4, 0, 64, Boolean.FALSE, 0),
        row("K6-摘竖向件", 6, 0, 64, Boolean.FALSE, 0), row("K8-摘竖向件", 8, 0, 64, Boolean.FALSE, 0),
        row("K12-摘竖向件", 12, 0, 64, Boolean.FALSE, 0), row("K4-竖向件在", 4, 0, 64, Boolean.TRUE, 0),
        row("K6-竖向件在", 6, 0, 64, Boolean.TRUE, 0), row("K8-竖向件在", 8, 0, 64, Boolean.TRUE, 0),
        row("K12-竖向件在", 12, 0, 64, Boolean.TRUE, 0), row("K8-竖向件在+H2窗上限2", 8, 0, 64, Boolean.TRUE, 2),
        row("DEFAULT档(K8+摘+落块24+窗2)", 8, 24, 64, Boolean.FALSE, 2),
        // P5b：成簇默认档 = 生产默认键（cell/denom/pieces/radius/falloff 全走 Config 当前默认，
        // 其余四键与上一行同值 ⇒ 与 "DEFAULT档" 行的差只有"成簇开关"这一件事）
        // CLU 行 = 生产默认档的<b>全量显式</b> spec（防同 JVM 行序泄漏，见 ScatterClusterVarianceCheck
        // 的 clusterDefaultSpec 注释）；这六个值与 {@link Config} 的 P5b 段字段默认值严格一致，
        // 改默认必须两处同改（HeightHashSingleSourceCheck 不管工具，靠本注释 + [14e] RED 兜底）。
        clusterRow("CLU-P5b成簇默认档(全显式)", 8, 24, 64, Boolean.FALSE, 2,
            "prosperityScatterClusterMode=1,prosperityScatterClusterCellChunks=4,"
                + "prosperityScatterClusterFieldChanceDenom=3,prosperityScatterClusterPiecesMin=8,"
                + "prosperityScatterClusterPiecesMax=14,prosperityScatterClusterRadiusChunks=2,"
                + "prosperityScatterClusterFalloffPower=1"),
        // P5b 判据 5 退化档：不关开关，只把簇参数设回"每 chunk 一场、每场 K 件、半径 0"
        clusterRow("DEGEN-成簇参数退化到P5-K8", 8, 24, 64, Boolean.FALSE, 2,
            "prosperityScatterClusterMode=1,prosperityScatterClusterCellChunks=1,"
                + "prosperityScatterClusterFieldChanceDenom=1,prosperityScatterClusterPiecesMin=8,"
                + "prosperityScatterClusterPiecesMax=8,prosperityScatterClusterRadiusChunks=0,"
                + "prosperityScatterClusterFalloffPower=1") };

    private static Row row(String label, int k, int b, int a, Boolean v, int cap) {
        return new Row(label, k, b, a, v, cap);
    }

    private static Row clusterRow(String label, int k, int b, int a, Boolean v, int cap, String clusterSpec) {
        return new Row(label, k, b, a, v, cap, clusterSpec);
    }

    /** 现状基线行（digest 模式与 BASE/AFTER 对拍只用这一行）。 */
    public static final String BASELINE_LABEL = GRID[0].label;

    /** 固定 seed 集（P4 同款 8 个，便于跨片对读）。 */
    static final long[] SEEDS = { 0x503441L, 0x503442L, 0x503443L, 0x503444L, 0x503445L, 0x503446L, 0x503447L,
        0x503448L };

    private static final List<String> MISSING_FIELDS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        final String mode = args.length > 0 ? args[0] : "grid";
        final int seeds = intArg(args, 1, 8);
        final int regions = intArg(args, 2, 8);
        final int axis = intArg(args, 3, WINDOW_CHUNKS);
        if (axis != WINDOW_CHUNKS) {
            System.out.println("AXIS_MUST_BE_16（区必须正好等于一个 H-2 窗）实际=" + axis);
            System.exit(2);
        }
        bootstrap();
        assertWindowSideLengthMatchesProduction();
        final List<Row> rows = "digest".equals(mode) ? Collections.singletonList(GRID[0]) : Arrays.asList(GRID);
        final Sampler sampler = new Sampler(seeds, regions, axis, rows);
        sampler.run();
        System.out.println("P5-DENSITY mode=" + mode + " seeds=" + seeds + " regionsPerSeed=" + regions
            + " treeFieldsMissing=" + MISSING_FIELDS.size()
            + (MISSING_FIELDS.isEmpty() ? "" : " " + MISSING_FIELDS));
        // P16-B5b：申报式密度带（同一份疏密面从"CHAIN 行只打印 + 只判非 0"升成"钉新实测 + 旧值留档
        // + 对照臂必须越带"）。放在 CHAIN-GATE <b>之前</b>打印：[13]/[10] 的 run() 用 tail -1 取末行做
        // 显示行，历史上那一直是 CHAIN-GATE 行，不该被本片换掉。
        final int pinBad = sampler.assertDensityPinDeclared();
        // P7c 判据 4：CHAIN 硬门槛（结构命中为 0 必须判红，不再让散布行的绿灯掩盖整条结构链）
        final int chainBad = sampler.assertChainAlive();
        if (chainBad != 0 || pinBad != 0) {
            System.exit(1);
        }
    }

    /**
     * P16-B5b 申报：<b>本轮有意改判</b>的三个疏密分母，以及改判前的对照值。
     * <p>
     * 这三个数字的<b>生产真值出处只有 {@code Config} 字段一处</b>（plan §1 G10 单一真值纪律）；
     * 这里再写一份不是第二真值，而是离线判据惯用的"<b>独立申报</b>"——期望值不许从被测实现里读回来
     * （同 {@code SurfaceGateUnifyCheck} 的 {@code EXPECTED78}、{@code HeightHashSingleSourceCheck}
     * 的口径，也同 {@code ContourBudgetCheck} 的 {@code DECL_MACHINE_DENOM}）。{@link
     * #assertDensityPinDeclared()} 把"申报 == {@code Config} 运行期值"钉成硬判据 ⇒ 谁改了字段没改
     * 申报（或反之）都会红，两边都不是自由量。
     * <p>
     * 旧值 {@code 16/64/48} = 本轮开工前 {@code Config} 的代码默认；plan §3 裁定的基线是
     * <b>实机生效值</b>（用户实例 {@code config/gtsr/gtsr.cfg:50} 把机器那一环覆盖成 24，outpost/ruin
     * 与默认一致）的 1/3 ⇒ 落地 {@code 24→72}、{@code 64→192}、{@code 48→144}。
     * <b>⚠ 实机口径必须与离线口径分开写</b>：本工具与全部离线档读的是<b>代码默认值</b>，不受那个
     * cfg 影响；而用户现有存档/新存档里机器那一环仍被 {@code =24} 覆盖（plan §3 显式的未授权项，
     * 本轮不改该文件）⇒ 实机那一环不会变稀，outpost/ruin 两环改后即生效。
     */
    static final int DECL_MACHINE_DENOM = 72;
    /** 见 {@link #DECL_MACHINE_DENOM}（三键同一条改判，plan §3 的"实机现值 ×3"）。 */
    static final int DECL_OUTPOST_DENOM = 192;
    /** 见 {@link #DECL_MACHINE_DENOM}。 */
    static final int DECL_RUIN_DENOM = 144;
    /** 改判前的代码默认（留档，不静默删；作废理由见 {@link #DECL_MACHINE_DENOM} 的注释）。 */
    static final int LEGACY_MACHINE_DENOM = 16;
    /** 见 {@link #LEGACY_MACHINE_DENOM}。 */
    static final int LEGACY_OUTPOST_DENOM = 64;
    /** 见 {@link #LEGACY_MACHINE_DENOM}。 */
    static final int LEGACY_RUIN_DENOM = 48;

    /** D-PIN 的样本规模：8 seed × 4096 槽 = 32768 格（纯 {@code intentAt}，实测单臂 1.2s）。 */
    static final int PIN_SEEDS = 8;
    /** 见 {@link #PIN_SEEDS}。 */
    static final int PIN_SLOTS_PER_SEED = 4096;
    /**
     * D-PIN 三条落点率带（百分点）——<b>本轮按新疏密实测钉</b>。两臂读数（同一份工具源码、同批 32768
     * 格点，跨 {@code digest 2 2 / digest 8 2 / all 4 1} 三档跑逐字相同 ⇒ 本组与主采样档位无关）：
     * <table border="1">
     * <caption>本工具 D-PIN 行实测；原始台账 plan/tmp/p3-b5b/density-readings.md</caption>
     * <tr><th>环</th><th>新 72/192/144</th><th>旧 16/64/48（对照臂）</th><th>钉的带</th></tr>
     * <tr><td>机器</td><td>1.245pp（408 座）</td><td>5.374pp（1761 座）</td><td>[1.05, 1.45]</td></tr>
     * <tr><td>outpost</td><td>0.528pp（173 座）</td><td>1.208pp（396 座）</td><td>[0.44, 0.62]</td></tr>
     * <tr><td>废墟</td><td>0.812pp（266 座）</td><td>1.767pp（579 座）</td><td>[0.70, 0.94]</td></tr>
     * </table>
     * 带宽取"实测 ±15~18%"（不是把旧值圈进来的宽带来）：新值居带中、旧值全部在带外（旧/新比
     * 4.32×／2.29×／2.18×）⇒ 三条带都还咬得住。为什么带里不是整齐的"1/3"：落点是<b>互斥三环的一条链</b>
     * （outpost → 否则机器 → 否则废墟），上游环变稀会把格子让给下游环，所以三条率各自动 2~4 成而不是
     * 各自的 1/3；再叠上机器那一环的旧基线本身有两个口径（代码默认 16 / 用户 cfg 覆盖成 24，plan §3
     * 裁定的是<b>实机生效值</b>）⇒ 比值随基线选择漂移，把比值当判据等于再造一个假绿源。故本组钉的是
     * <b>实测落点率</b>，分母的 3 倍关系由 {@link #DECL_MACHINE_DENOM} 那一组"申报==运行期真值"的
     * 硬判据单独钉（两边都是死数字，不靠实测）。
     */
    static final double PIN_MACHINE_PP_MIN = 1.05D, PIN_MACHINE_PP_MAX = 1.45D;
    /** 见 {@link #PIN_MACHINE_PP_MIN}。 */
    static final double PIN_OUTPOST_PP_MIN = 0.44D, PIN_OUTPOST_PP_MAX = 0.62D;
    /** 见 {@link #PIN_MACHINE_PP_MIN}。 */
    static final double PIN_RUIN_PP_MIN = 0.70D, PIN_RUIN_PP_MAX = 0.94D;
    /**
     * 巨构占机器族的份额带：候选池 = 5 个小机型 + 2 条跨片巨构（{@code RuinedColossusShapes.ALL}），
     * 抢<b>同一次</b> {@code nextInt(POOL)} ⇒ 理论份额 2/7 = 28.57%；实测新档 28.431%（116/408）、
     * 旧疏密对照臂 28.279% ⇒ 两臂差 0.152pp。带取 [20, 40]（容 footprint 收缩与小机型被门挤位的偏移），
     * 两臂漂移上界 2pp（远小于任何"新增第二条独立概率"会造成量级）。
     */
    static final double PIN_SPAN_SHARE_MIN = 20.0D, PIN_SPAN_SHARE_MAX = 40.0D;
    /** 见 {@link #PIN_SPAN_SHARE_MIN}。 */
    static final double PIN_SPAN_DRIFT_MAX = 2.0D;

    private static int intArg(String[] args, int i, int def) {
        return args.length > i ? Integer.parseInt(args[i]) : def;
    }

    /** 离线装配（Blocks + BlocksGTSR 字段），其它工具复用本类时先调这里。 */
    public static void bootstrap() {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        extraBlocks();
    }

    /**
     * 反证"工具里的窗边长与生产一致"：AFTER 树上反射读 {@code ProsperitySurfaceScatter.WINDOW_CHUNKS}
     * 必须等于 {@link #WINDOW_CHUNKS}（BASE 树读不到该字段则跳过并登记）。
     */
    private static void assertWindowSideLengthMatchesProduction() {
        try {
            final Class<?> c = Class.forName(
                "com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperitySurfaceScatter");
            final int prod = c.getField("WINDOW_CHUNKS")
                .getInt(null);
            if (prod != WINDOW_CHUNKS) {
                throw new IllegalStateException(
                    "生产 WINDOW_CHUNKS=" + prod + " != 工具申报 " + WINDOW_CHUNKS + "（窗统计数径会假绿）");
            }
            System.out.println("# 窗边长对账：生产 WINDOW_CHUNKS=" + prod + " == 工具申报 " + WINDOW_CHUNKS);
        } catch (ReflectiveOperationException e) {
            rememberMissing("ProsperitySurfaceScatter.WINDOW_CHUNKS");
        }
    }

    /** 一行 SCAN 输出（列名固定，供 shell/工具 grep）。 */
    static String scanRow(Row r, Stats s, long eligibleChunks) {
        return String
            .format(
                Locale.ROOT,
                "SCAN label=%s|K=%d|blockCap=%d|attempts=%d|vertical=%s|windowCap=%d|eligibleChunks=%d"
                    + "|coveragePp=%.2f|contourMean=%.3f|contourP95=%d|contourMax=%d|blockMean=%.3f"
                    + "|blockP95=%d|blockMax=%d|chimColPerChunk=%.3f|chimChunkPp=%.2f"
                    + "|windows=%d|maxWinEmitChim=%d|maxWinPiecesChim=%d|maxWinEmitFlat=%d|maxWinPiecesFlat=%d"
                    + "|pipeMergeUpperBound=%d|digest=%s",
                r.label,
                r.contours,
                r.blocks,
                r.attempts,
                String.valueOf(r.vertical),
                r.windowCap,
                eligibleChunks,
                s.coveragePp(),
                s.contourMean(),
                s.contourP95(),
                s.contourMax(),
                s.blockMean(),
                s.blockP95(),
                s.blockMax(),
                s.chimneyMean(),
                s.chimneyChunkPp(),
                s.windows,
                s.maxWinEmitChim,
                s.maxWinPiecesChim,
                s.maxWinEmitFlat,
                s.maxWinPiecesFlat,
                s.pipeMergeUpperBound,
                s.digest());
    }

    // ══════════════════ 采样器（public：ContourBudgetCheck / RegionRepeatCapCheck 复用）══════════════════

    /**
     * 一个样本 = {@code seeds × regionsPerSeed} 个 {@code 16×16} chunk 区（每区恰为一整个 H-2 窗）。
     * 每区只生成一次地形、只跑一次 outpost/机器，然后把网格回卷到"散布前"，逐参数行只重跑散布
     * ⇒ 各行之间的差<b>只有散布</b>这一件事。
     */
    public static final class Sampler {

        public final int seeds;
        public final int regions;
        public final int axis;
        private final int side;
        private final List<Row> rows;
        private final Map<String, Stats> stats = new TreeMap<>();
        /** 非散布路径计数（与参数行无关，只跑一次）——判据 5 的对照面。 */
        public long outpostHitChunks;
        public long outpostLanded;
        public long machineLanded;
        public long preScatterChunks;
        public long cityWindowChunks;
        public long generatedChunks;

        public Sampler(int seeds, int regions, int axis, List<Row> rows) {
            this.seeds = seeds;
            this.regions = regions;
            this.axis = axis;
            this.side = axis * 16;
            this.rows = rows;
            for (final Row r : rows) {
                stats.put(r.label, new Stats(r));
            }
        }

        public Stats stats(String label) {
            return stats.get(label);
        }

        public void run() throws Exception {
            final BiomeGenBase[] pb = SurfaceHarness.prosperityBiomes();
            SurfaceHarness.recordAllAllocations(pb, SurfaceHarness.shatteredBiomes());
            final GTSRDimensionDef defP = SurfaceHarness.def(true, pb, SurfaceHarness.prosperityWeights());
            final Block[] scratch = new Block[65536];
            final byte[] scratchMeta = new byte[65536];
            for (int si = 0; si < seeds; si++) {
                final long seed = SEEDS[si % SEEDS.length];
                final GTSRWorldChunkManager mgrP = new GTSRWorldChunkManager(seed, defP);
                for (int r = 0; r < regions; r++) {
                    // 区原点两轴都按 16 对齐 ⇒ 每区 == 恰好一个 H-2 窗；并按 3/5 个群系带 cell
                    // （ZONE_CELL_CHUNKS=16）跳开，避免整样本塌进同一群系带（P4 实测过的坑）
                    final int cx0 = r * axis * 3 + si * 4096;
                    final int cz0 = r * axis * 5 + si * 924816;
                    final Block[] grid = new Block[side * side * 256];
                    final GridWorld world = world(grid, cx0, cz0, seed, side);
                    // P7c：provider 必须挂在<b>本区世界</b>上（改造前挂 mockWorld）。generateTerrain 取的是
                    // {@code this.worldObj.getSeed()}，挂 mockWorld ⇒ 地形按 SurfaceHarness 的常数种子生成，
                    // 而 P7 起的结构接地走 {@code heightAt(SEEDS[si],...)} ⇒ 两套高度系统性错位、
                    // 就绪门整段拒（实测 digest 8 2 的 CHAIN 只剩 2 座 / 1209 块，改造前口径是 39/20193/19678）。
                    final GTSRChunkProviderBase provP = new ChunkProviderProsperityRuins(world, seed);
                    materialize(provP, mgrP, seed, cx0, cz0, grid, scratch, scratchMeta);
                    if (!SKIP_STRUCTURE_STAGES) {
                        runPreScatterStages(world, seed, cx0, cz0);
                    }
                    final Block[] preScatter = grid.clone();
                    for (final Row row : rows) {
                        applySpec(row.overrideSpec());
                        applySpec(System.getProperty("gtsr.p5.override", ""));
                        System.arraycopy(preScatter, 0, grid, 0, grid.length);
                        final Stats s = stats.get(row.label);
                        final ChainSink sc = new ChainSink(world);
                        for (int cx = 0; cx < axis; cx++) {
                            for (int cz = 0; cz < axis; cz++) {
                                final int gcx = cx0 + cx;
                                final int gcz = cz0 + cz;
                                if (!eligible(gcx, gcz, seed)) {
                                    continue;
                                }
                                sc.chunk(gcx, gcz);
                                ProsperitySurfaceScatter.scatter(
                                    world,
                                    seed,
                                    gcx,
                                    gcz,
                                    weight(gcx, gcz, ProsperityWorldGenerator.SCATTER_WEIGHTS),
                                    sc);
                                s.recordChunk(sc);
                            }
                        }
                    }
                }
            }
            for (final Stats s : stats.values()) {
                s.finish();
            }
            print();
        }

        /** 阶段 1：outpost →（互斥）机器；与参数行无关，只跑一次并把落块分类计清楚。 */
        private void runPreScatterStages(GridWorld world, long seed, int cx0, int cz0) {
            final ChainSink pre = new ChainSink(world);
            final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
            for (int cx = 0; cx < axis; cx++) {
                for (int cz = 0; cz < axis; cz++) {
                    final int gcx = cx0 + cx;
                    final int gcz = cz0 + cz;
                    if (!eligible(gcx, gcz, seed)) {
                        cityWindowChunks++;
                        continue;
                    }
                    preScatterChunks++;
                    generatedChunks++;
                    pre.chunk(gcx, gcz);
                    final long before = pre.accepted;
                    if (ProsperityOutpostPlacer.placeAll(world, seed, gcx, gcz, pre)) {
                        outpostHitChunks++;
                        outpostLanded += pre.accepted - before;
                    } else {
                        final long b2 = pre.accepted;
                        RuinedMachinePlacer.placeAll(
                            world,
                            seed,
                            gcx,
                            gcz,
                            ProsperityWorldGenerator.weightForRosterIndex(
                                authority.ordinalAt((gcx << 4) + 8, (gcz << 4) + 8).ordinal,
                                ProsperityWorldGenerator.MACHINE_WEIGHTS),
                            pre);
                        machineLanded += pre.accepted - b2;
                    }
                }
            }
        }

        private float weight(int cx, int cz, float[] table) {
            final GTSRBiomeAuthority.Resolution res = GTSRBiomeAuthority
                .forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
                .ordinalAt((cx << 4) + 8, (cz << 4) + 8);
            return ProsperityWorldGenerator.weightForRosterIndex(res.ordinal, table);
        }

        private static boolean eligible(int cx, int cz, long seed) {
            return CityPlanner.citiesNear(seed, cx, cz).length == 0;
        }

        /** 真实 generateTerrain + 真实表层缝 → 平坦网格（下标口径同 SurfaceGateUnifyCheck）。 */
        private void materialize(GTSRChunkProviderBase provider, GTSRWorldChunkManager mgr, long seed, int cx0,
            int cz0, Block[] grid, Block[] blocks, byte[] meta) throws Exception {
            final java.lang.reflect.Method gen = SurfaceHarness.generateTerrain();
            final java.lang.reflect.Method seam = SurfaceHarness.surfaceSeam(provider.getClass());
            for (int dx = 0; dx < axis; dx++) {
                for (int dz = 0; dz < axis; dz++) {
                    final int cx = cx0 + dx;
                    final int cz = cz0 + dz;
                    Arrays.fill(blocks, null);
                    Arrays.fill(meta, (byte) 0);
                    gen.invoke(provider, cx, cz, blocks, meta, null);
                    final BiomeGenBase[] plane = mgr.loadBlockGeneratorData(null, cx * 16, cz * 16, 16, 16);
                    seam.invoke(null, seed, cx * 16, cz * 16, blocks, meta, plane);
                    final int bx = dx * 16;
                    final int bz = dz * 16;
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            final int col = (lx << 12) | (lz << 8);
                            final int dstCol = (bx + lx) + (bz + lz) * side;
                            for (int y = 0; y < 256; y++) {
                                grid[y * side * side + dstCol] = blocks[col | y];
                            }
                        }
                    }
                }
            }
        }

        private void print() {
            for (final Row r : rows) {
                System.out.println(scanRow(r, stats.get(r.label), preScatterChunks));
            }
            System.out.println("CHAIN outpostHitChunks=" + outpostHitChunks + " outpostLanded=" + outpostLanded
                + " machineLanded=" + machineLanded + " preScatterChunks=" + preScatterChunks
                + " cityWindowChunks=" + cityWindowChunks + " generatedChunks=" + generatedChunks
                + "（这三项与参数行无关 = 判据 5 非散布路径的对照面）");
        }

        /**
         * <b>CHAIN 硬门槛（P7c 判据 4）</b>：结构命中整段为 0 必须判红。
         * <p>
         * 为什么要这道闸：本工具过去只对散布行做断言，CHAIN 行只打印不判——P7b 实测默认档把城外结构
         * 砍到 {@code 0/0/0} 时工具仍 EXIT=0 静默通过（同族几何下 P5 记的是 39/20193/19678），
         * 这就是"看起来在判"的假绿现场。
         * <p>
         * <b>P16-B5b 改了这道闸的"样本够不够"前提</b>。旧写法是"样本量 ≥ 256 chunk（= 一个 16×16 窗）
         * 时，outpost 的 1/64 与机器的 1/24 独立掷骰让命中 0 座在正常链上不可能出现"——那两个分母
         * 随 U6/疏密改判已作废（现值由 {@code Config.prosperityOutpostChance} /
         * {@code Config.prosperityMachineChance} 给出，本注释按单一真值纪律只回显字段名不写数字），
         * 而"256 chunk"这个<b>数</b>也随分母一起失效：按稀疏的那个环算，256 格样本上"一座都不中"的
         * 概率是 {@code (1-1/N)^256}，N 抬到三位数后它已是"多半会"而不是"不可能"（实测 256 档期望命中
         * 只剩 1.3 座 ⇒ P(0)≈27%，用"0 即缺陷"判它会误红）。故门槛样本量改为<b>由两个分母推导</b>：
         * {@code 4 × max(outpost, machine)}（P(0 座) ≤ e^-4 = 1.8%），推导式而不是新常数 ⇒ 再调疏密
         * 不需要动这里。实测本链用到的样本档 {@code digest 8 2}=3936、{@code digest 4 4}=4096、
         * {@code digest 2 2}=1024（实测 {@code outpostHitChunks=6}）都过得了新门槛，而
         * {@code [10]/[14b]/[15a2]/[16a2]/[19b]} 的摘要对拍一律带 {@code -Dgtsr.skipStructure=1}
         * ⇒ 整闸跳过，没有一档因抬门槛而变红。
         *
         * @return 0 = 通过；1 = 结构层静默失效（调用方据此决定退出码，不自己吞掉）
         */
        /** 反射读 1/N 概率键（缺键按"开着"= 1 处理 ⇒ 门槛不会因为 BASE 树没有该键而放松）。 */
        private static int chanceOf(String key) {
            try {
                final Field f = findField(Class.forName("com.miaokatze.gtsr.config.Config"), key);
                return f == null ? 1 : f.getInt(null);
            } catch (ReflectiveOperationException e) {
                return 1;
            }
        }

        /**
         * CHAIN-GATE 的样本量门槛：<b>由疏密分母推导，不是写死的 256</b>（P16-B5b）。
         * 4 × 两个环里较稀疏的那个分母 ⇒ "一座都不中"的概率 ≤ {@code e^-4 ≈ 1.8%}，
         * 才够得上"0 就是缺陷而不是稀疏"。BASE 树读不到键时 {@code chanceOf} 按 1 处理 ⇒ 门槛退化成 4，
         * 与改造前对该树的实际行为一致（不为旧树新增判红）。
         */
        private int chainSampleFloor() {
            return 4 * Math.max(chanceOf("prosperityOutpostChance"), chanceOf("prosperityMachineChance"));
        }

        /**
         * <b>P16-B5b 申报式密度带（D-PIN 组）</b>：把"城外疏密降到实机现值的 1/3"（plan §0 U2 / §1 G10）
         * 从"CHAIN 行只打印 + 只判非 0"升成<b>钉住新实测、旧值留档并写明作废理由</b>的成对判据。
         * <p>
         * 量的是<b>落点概率面</b>（纯 {@code intentAt} 重放，不需地形；8 seed × 4096 槽 = 32768 格，
         * 两臂合计实测 2.5s），而不是真地形 CHAIN 的块数：{@code machineLanded} 是<b>块数</b>，
         * P16-B1 之后巨构会把自己的片写进邻 chunk（同一座结构在多格里各计若干块），拿块数当概率
         * 证据会串面。三条率按编排器同构的互斥顺序数（outpost → 否则机器 → 否则废墟），加起来就是
         * "城外结构落点率"本身，不是三张独立的表。
         * <p>
         * <b>两面反假绿</b>：同一次运行里再跑一条<b>对照臂</b>——进程内反射把三键注回本轮改判前的
         * {@code 16/64/48}（手法同 {@code BiomeBandHierarchyCheck} 的 rollback 包装，跑完立即还原，
         * 不改工作树），对照臂的三条实测值必须<b>落在带外</b>。任何一条落在带内就判红，
         * 因为那说明这条带对"把概率调回去"不敏感＝恒真判据。
         * <p>
         * 整组在 {@code -Dgtsr.skipStructure=1} 档下<b>完全不打</b>（不是打一行 SKIPPED 就完事）：
         * {@code [10]/[14b]/[15a2]/[16a2]/[19b]} 的 BASE/AFTER 逐字节对拍用同一份工具源码跑旧树，
         * 旧树的 {@code Config} 还是改造前的分母，多打任何一行都会把"散布回退"这条判据打成假红。
         *
         * @return 0 = 通过；非 0 = 红条数
         */
        public int assertDensityPinDeclared() throws ReflectiveOperationException {
            if (SKIP_STRUCTURE_STAGES) {
                return 0;
            }
            if (MISSING_FIELDS.contains("prosperityMachineChance")) {
                return 0; // BASE 树没有本轮的期望值可言（上面已说明为何连一行都不打）
            }
            final int m = chanceOf("prosperityMachineChance");
            final int o = chanceOf("prosperityOutpostChance");
            final int r = chanceOf("prosperityRuinChance");
            int bad = 0;
            // ① 三面一致：申报常量 == 运行期字段（生产真值出处仍只有 Config 字段一处，见 DECL_* 注释）
            bad += pinCheck("D-PIN 机器分母运行期真值 = 申报 " + DECL_MACHINE_DENOM + "（实测 " + m
                + "；改判前代码默认 " + LEGACY_MACHINE_DENOM + "）", m == DECL_MACHINE_DENOM);
            bad += pinCheck("D-PIN outpost 分母运行期真值 = 申报 " + DECL_OUTPOST_DENOM + "（实测 " + o
                + "；改判前 " + LEGACY_OUTPOST_DENOM + "）", o == DECL_OUTPOST_DENOM);
            bad += pinCheck("D-PIN 废墟分母运行期真值 = 申报 " + DECL_RUIN_DENOM + "（实测 " + r
                + "；改判前 " + LEGACY_RUIN_DENOM + "）", r == DECL_RUIN_DENOM);
            // ② 落点率带（新档）+ ③ 对照臂必须越带
            final long[] now = measureIntentRates(m, o, r);
            final long[] legacy = measureIntentRates(LEGACY_MACHINE_DENOM, LEGACY_OUTPOST_DENOM,
                LEGACY_RUIN_DENOM);
            if (now == null || legacy == null) {
                return 0; // 本树没有命中重放口（P7c 之前的 era 快照）⇒ 不打印、不判，保对拍两侧同形
            }
            final double outpostPp = 100.0 * now[1] / now[0];
            final double machinePp = 100.0 * now[2] / now[0];
            final double ruinPp = 100.0 * now[3] / now[0];
            final double spanShare = 100.0 * now[4] / Math.max(1L, now[2]);
            final double lOutpostPp = 100.0 * legacy[1] / legacy[0];
            final double lMachinePp = 100.0 * legacy[2] / legacy[0];
            final double lRuinPp = 100.0 * legacy[3] / legacy[0];
            final double lSpanShare = 100.0 * legacy[4] / Math.max(1L, legacy[2]);
            System.out.println("D-PIN slots=" + now[0] + " 新档落点率pp: outpost=" + fmt2(outpostPp)
                + " machine=" + fmt2(machinePp) + " ruin=" + fmt2(ruinPp) + " 巨构/机器=" + fmt2(spanShare)
                + "（" + now[4] + "/" + now[2] + "）｜对照臂(注回 " + LEGACY_MACHINE_DENOM + "/"
                + LEGACY_OUTPOST_DENOM + "/" + LEGACY_RUIN_DENOM + ")pp: outpost=" + fmt2(lOutpostPp)
                + " machine=" + fmt2(lMachinePp) + " ruin=" + fmt2(lRuinPp)
                + " 巨构/机器=" + fmt2(lSpanShare) + "（" + legacy[4] + "/" + legacy[2] + "）比值 machine="
                + fmt2(lMachinePp / Math.max(1.0E-9D, machinePp)) + " outpost="
                + fmt2(lOutpostPp / Math.max(1.0E-9D, outpostPp)) + " ruin="
                + fmt2(lRuinPp / Math.max(1.0E-9D, ruinPp)) + " 巨构份额漂移="
                + fmt2(Math.abs(spanShare - lSpanShare)));
            bad += pinBand("机器落点率", machinePp, PIN_MACHINE_PP_MIN, PIN_MACHINE_PP_MAX, lMachinePp);
            bad += pinBand("outpost 落点率", outpostPp, PIN_OUTPOST_PP_MIN, PIN_OUTPOST_PP_MAX, lOutpostPp);
            bad += pinBand("废墟落点率", ruinPp, PIN_RUIN_PP_MIN, PIN_RUIN_PP_MAX, lRuinPp);
            // ④ 巨构与 5 个小机型抢<b>同一次</b> nextInt(POOL)、同一 prosperityMachineChance 分母
            //    （B1 申报）⇒ 巨构在机器族里的份额只由候选池比例决定、不随疏密走。两臂必须都落在同一条带内
            //    且彼此漂移不超过 PIN_SPAN_DRIFT_MAX：这条同时是"单 chunk 座数上界仍是 1"的概率面证据
            //    ——若巨构是第二条独立的 1/N，注回旧疏密时它的份额会跟着变，两臂差就会越过漂移上界。
            bad += pinCheck("D-PIN 巨构占机器族份额：新档 " + fmt2(spanShare) + "% 与对照臂 "
                + fmt2(lSpanShare) + "% 都 ∈ [" + PIN_SPAN_SHARE_MIN + ", " + PIN_SPAN_SHARE_MAX
                + "] 且漂移 ≤ " + PIN_SPAN_DRIFT_MAX + "pp（同一 POOL 同一次抽签 ⇒ 巨构不叠加密度）",
                spanShare >= PIN_SPAN_SHARE_MIN && spanShare <= PIN_SPAN_SHARE_MAX
                    && lSpanShare >= PIN_SPAN_SHARE_MIN && lSpanShare <= PIN_SPAN_SHARE_MAX
                    && Math.abs(spanShare - lSpanShare) <= PIN_SPAN_DRIFT_MAX);
            return bad;
        }

        private static int pinCheck(String label, boolean ok) {
            if (!ok) {
                System.out.println("  FAIL " + label);
                return 1;
            }
            return 0;
        }

        /** 带内 + 对照臂带外，两半各算一条（缺一半就退化成"只钉新值"或"只比旧值"）。 */
        private static int pinBand(String label, double actual, double lo, double hi, double legacyArm) {
            final boolean inside = actual >= lo && actual <= hi;
            final boolean outside = legacyArm < lo || legacyArm > hi;
            pinCheck("D-PIN " + label + " = " + fmt2(actual) + "pp ∈ [" + lo + ", " + hi + "]", inside);
            pinCheck("D-PIN " + label + " 的旧疏密对照臂 = " + fmt2(legacyArm)
                + "pp 必须落在带外（否则本带对『把概率调回去』不敏感＝恒真判据）", outside);
            return inside && outside ? 0 : 1;
        }

        private static String fmt2(double v) {
            return String.format(Locale.ROOT, "%.3f", v);
        }

        /**
         * 纯函数落点面计数，返回 {@code [格子数, outpost 命中, 机器命中, 废墟命中, 其中跨片巨构锚点]}。
         * 槽位取互质步进的双轴格点（与 {@code plan/tmp/p3-b5b/work-src/B5bRateProbe.java} 同一份坐标式，
         * 读数可跨工具对读）；城窗格按编排器口径<b>计入分母但不计入命中</b>——本轮没改
         * {@code prosperityCityChance}，城窗抑制面归 {@code CityBiomeGateCheck} 与
         * {@code RuinFamilyCheck} 的"城窗跳过"列判。
         */
        private static long[] measureIntentRates(int machine, int outpost, int ruin)
            throws ReflectiveOperationException {
            final int[] saved = new int[] { chanceOf("prosperityMachineChance"),
                chanceOf("prosperityOutpostChance"), chanceOf("prosperityRuinChance") };
            try {
                applySpec("prosperityMachineChance=" + machine + ",prosperityOutpostChance=" + outpost
                    + ",prosperityRuinChance=" + ruin);
                // 三个 intentAt 一律走反射：本文件的既有约定是"BASE 树（改造前那份生产码）也要能跑
                // 同一份工具源码"——P5/P6 期的 placer 根本没有 intentAt(long,int,int)（P7c 才有命中重放口），
                // 直连会让 [10]/[14]/[15]/[19] 的 BASE 侧工具编译从 2 error 涨到 4 error（实测踩过）。
                final java.lang.reflect.Method outpostIntent = intentAtMethod(ProsperityOutpostPlacer.class);
                final java.lang.reflect.Method machineIntent = intentAtMethod(RuinedMachinePlacer.class);
                final java.lang.reflect.Method ruinIntent = ruinIntentMethod();
                if (outpostIntent == null || machineIntent == null) {
                    return null; // 本树没有命中重放口 ⇒ 三条率无从复算，整组静默跳过（见调用处）
                }
                final BiomeGenBase[] pb = SurfaceHarness.prosperityBiomes();
                final GTSRDimensionDef def = SurfaceHarness.def(true, pb, SurfaceHarness.prosperityWeights());
                long slots = 0;
                long hitsO = 0;
                long hitsM = 0;
                long hitsR = 0;
                long span = 0;
                for (int si = 0; si < PIN_SEEDS; si++) {
                    final long seed = SEEDS[si % SEEDS.length];
                    // 每 seed 现建 manager：GTSRBiomeAuthority.bind 是全局覆盖式，跨 seed 复用最外侧那一份
                    // 会把群系权重钉到别的 seed 上（B5b 首版实测三条率同时偏 ~15%）。
                    new GTSRWorldChunkManager(seed, def);
                    for (int i = 0; i < PIN_SLOTS_PER_SEED; i++) {
                        final int cx = i * 7 - 4096;
                        final int cz = i * 13 + 9217;
                        slots++;
                        if (CityPlanner.citiesNear(seed, cx, cz).length > 0) {
                            continue;
                        }
                        if (callIntent(outpostIntent, seed, cx, cz) != null) {
                            hitsO++;
                            continue;
                        }
                        final Object mi = callIntent(machineIntent, seed, cx, cz);
                        if (mi != null) {
                            hitsM++;
                            // 跨片巨构的申报口径：总 bbox 任一边 > 16 格（OutpostTemplateCheck 的成对断言
                            // 用的就是这条）；这里不引 ChunkSpans——BASE 树没有那个类。
                            if (intentSize(mi, "sizeX") > 16 || intentSize(mi, "sizeZ") > 16) {
                                span++;
                            }
                            continue;
                        }
                        if (callIntent(ruinIntent, seed, cx, cz) != null) {
                            hitsR++;
                        }
                    }
                }
                return new long[] { slots, hitsO, hitsM, hitsR, span };
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("D-PIN 反射调命中重放口失败：" + e, e);
            } finally {
                applySpec("prosperityMachineChance=" + saved[0] + ",prosperityOutpostChance=" + saved[1]
                    + ",prosperityRuinChance=" + saved[2]);
            }
        }

        /** 命中重放口的反射解析（缺方法返回 null，不抛）。 */
        private static java.lang.reflect.Method intentAtMethod(Class<?> placer) {
            try {
                return placer.getMethod("intentAt", long.class, int.class, int.class);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        /** 废墟族的命中重放口（本类对 ruin 包只做反射，理由同 {@code chanceOf}：BASE 树没这个包）。 */
        private static java.lang.reflect.Method ruinIntentMethod() {
            try {
                return intentAtMethod(Class.forName(
                    "com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinPlacer"));
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        private static Object callIntent(java.lang.reflect.Method m, long seed, int cx, int cz)
            throws ReflectiveOperationException {
            return m == null ? null : m.invoke(null, seed, cx, cz);
        }

        /** {@code PlacementGate.Intent.sizeX/sizeZ} 的反射读法（同上：不直连后起类型的成员）。 */
        private static int intentSize(Object intent, String field) {
            try {
                return intent.getClass().getField(field).getInt(intent);
            } catch (ReflectiveOperationException e) {
                return 0;
            }
        }

        public int assertChainAlive() {
            if (SKIP_STRUCTURE_STAGES) {
                System.out.println("CHAIN-GATE SKIPPED-BY-FLAG（本跑用 -Dgtsr.skipStructure=1 显式跳过结构前序，"
                    + "命中为 0 是档位的定义；结构侧判据见 PlacementContractCheck T4/T7 与本工具的默认档跑）");
                return 0;
            }
            final int floor = chainSampleFloor();
            if (generatedChunks < floor) {
                System.out.println("CHAIN-GATE SKIPPED（样本 " + generatedChunks + " chunk < 门槛 " + floor
                    + " = 4 × 两个疏密分母里的稀疏者，不足以把\"命中 0 座\"判成缺陷）");
                return 0;
            }
            // 逐族判：只死一族同样是静默失效（P7c 的 R4 单变量 RED 就是这个形状——机器 roll 整段 return
            // null 时 outpost 侧仍可非 0，只判"合计非 0"会漏）。概率被显式设 0 = 禁用位，不是缺陷。
            // 概率键走反射读（本文件按"零直接引用后起 Config 字段"的既有约定，BASE 树也要能跑）；
            // 键缺失时按"开着"处理，宁严不松。
            final boolean outpostEnabled = chanceOf("prosperityOutpostChance") > 0;
            final boolean machineEnabled = chanceOf("prosperityMachineChance") > 0;
            int bad = 0;
            if (outpostEnabled && outpostHitChunks <= 0) {
                bad++;
            }
            if (machineEnabled && machineLanded <= 0) {
                bad++;
            }
            if (!outpostEnabled && !machineEnabled) {
                System.out.println("CHAIN-GATE SKIPPED-BY-CONFIG（两族概率都被显式设 0 = 禁用位，"
                    + "命中 0 是设计而不是缺陷；结构侧的判据见 PlacementContractCheck T4/T7）");
                return 0;
            }
            System.out.println("CHAIN-GATE outpostEnabled=" + outpostEnabled + " machineEnabled="
                + machineEnabled + " outpostHitChunks=" + outpostHitChunks + " outpostLanded=" + outpostLanded
                + " machineLanded=" + machineLanded + " 样本chunk=" + generatedChunks
                + " verdict=" + (bad == 0 ? "OK" : "FAIL"));
            if (bad != 0) {
                System.out.println("CHAIN-GATE FAIL：概率仍开着却整段没有结构命中/落块 ⇒ 结构层静默失效"
                    + "（窗上限/间距/接地/概率任一把它们全砍光就是这里的现场；P7b 的 cap=2 排名配额即此类）");
            }
            return bad == 0 ? 0 : 1;
        }
    }

    // ══════════════════════════════════ 统计容器 ══════════════════════════════════

    /** 一个参数行的聚合统计。 */
    public static final class Stats {

        private final Row row;
        private final List<Integer> contours = new ArrayList<>();
        private final List<Integer> blocks = new ArrayList<>();
        private final List<Integer> chimneyCols = new ArrayList<>();
        private final Map<Long, int[]> windowEmit = new TreeMap<>();
        private final Map<Long, int[]> windowPieces = new TreeMap<>();
        /** P5b 判据 2：每个 16×16 窗内的总件数（跨件型求和），供"窗间分布"读取。 */
        private final Map<Long, Long> windowTotals = new TreeMap<>();
        /** P5b 判据 2 的观感粒度：4×4 chunk（=64×64 格）子窗总件数（键 = 全局对齐坐标派生）。 */
        private final Map<Long, Long> subwindowTotals = new TreeMap<>();
        private final MessageDigest md;
        long chunksSeen;
        long chunksWithScatter;
        long chimneyChunks;
        long totalPipeBlocks;
        int maxWinEmitChim;
        int maxWinPiecesChim;
        int maxWinEmitFlat;
        int maxWinPiecesFlat;
        int windows;
        /** 件数下界可能并掉的上界：全部管道落块数（真件数 ≤ 重建件数 + 管道块数）。 */
        int pipeMergeUpperBound;
        private String digestHex = "(unfinished)";

        Stats(Row row) {
            this.row = row;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public String label() {
            return row.label;
        }

        /** 从一次 chunk 的写入序列重建件数/落块/烟囱列数/件型直方。 */
        void recordChunk(ChainSink sink) {
            chunksSeen++;
            int pieces = 0;
            int blockCount = 0;
            final int[] kindPieces = new int[4];
            final int[] kindBlocks = new int[4];
            final Map<Integer, Integer> chimCols = new TreeMap<>();
            int px = Integer.MIN_VALUE;
            int py = Integer.MIN_VALUE;
            int pz = Integer.MIN_VALUE;
            int pieceMeta = -1;
            int runDx = 0;
            int runDz = 0;
            boolean runDirKnown = false;
            for (int i = 0; i < sink.n; i++) {
                final int x = sink.xs[i];
                final int y = sink.ys[i];
                final int z = sink.zs[i];
                final int meta = sink.metas[i];
                final boolean continuesChimney = meta == 3 && pieceMeta == 3 && x == px && z == pz && y == py + 1;
                boolean continuesPipe = false;
                if (meta == 1 && pieceMeta == 1 && y == py) {
                    final int ddx = x - px;
                    final int ddz = z - pz;
                    if (runDirKnown) {
                        continuesPipe = ddx == runDx && ddz == runDz;
                    } else if (Math.abs(ddx) + Math.abs(ddz) == 1) {
                        // 段内第二格：就地确立方向，之后只接受同向共线
                        continuesPipe = true;
                        runDx = ddx;
                        runDz = ddz;
                        runDirKnown = true;
                    }
                }
                if (!continuesChimney && !continuesPipe) {
                    pieces++;
                    kindPieces[meta]++;
                    pieceMeta = meta;
                    runDirKnown = false;
                    runDx = 0;
                    runDz = 0;
                }
                kindBlocks[meta]++;
                if (meta == 1) {
                    totalPipeBlocks++;
                }
                if (meta == 3) {
                    chimCols.merge((x << 16) ^ (z & 0xFFFF), 1, Integer::sum);
                }
                blockCount++;
                px = x;
                py = y;
                pz = z;
                final byte[] buf = new byte[16];
                writeInt(buf, 0, x);
                writeInt(buf, 4, y);
                writeInt(buf, 8, z);
                writeInt(buf, 12, meta);
                md.update(buf);
            }
            for (int t = 0; t < 4; t++) {
                if (kindPieces[t] > 0) {
                    windowEmit.computeIfAbsent(windowKey(sink.cx, sink.cz), k -> new int[4])[t]++;
                    windowPieces.computeIfAbsent(windowKey(sink.cx, sink.cz), k -> new int[4])[t] += kindPieces[t];
                }
            }
            if (blockCount > 0) {
                chunksWithScatter++;
            }
            if (!chimCols.isEmpty()) {
                chimneyChunks++;
            }
            contours.add(pieces);
            blocks.add(blockCount);
            chimneyCols.add(chimCols.size());
            if (pieces > 0) {
                windowTotals.merge(windowKey(sink.cx, sink.cz), (long) pieces, Long::sum);
                subwindowTotals.merge(subWindowKey(sink.cx, sink.cz), (long) pieces, Long::sum);
            }
        }

        void finish() {
            windows = windowEmit.size();
            for (final Map.Entry<Long, int[]> e : windowEmit.entrySet()) {
                final int[] emit = e.getValue();
                final int[] pieces = windowPieces.get(e.getKey());
                maxWinEmitChim = Math.max(maxWinEmitChim, emit[3]);
                maxWinPiecesChim = Math.max(maxWinPiecesChim, pieces == null ? 0 : pieces[3]);
                for (int t = 0; t < 3; t++) {
                    maxWinEmitFlat = Math.max(maxWinEmitFlat, emit[t]);
                    maxWinPiecesFlat = Math.max(maxWinPiecesFlat, pieces == null ? 0 : pieces[t]);
                }
            }
            pipeMergeUpperBound = (int) totalPipeBlocks;
            final byte[] h = md.digest();
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                sb.append(String.format(Locale.ROOT, "%02x", h[i]));
            }
            digestHex = sb.toString();
        }

        /** 窗键：chunk 坐标按 16 floorDiv（与 ProsperitySurfaceScatter.windowAllows 同一定义）。 */
        private static long windowKey(int cx, int cz) {
            return Math.floorDiv(cx, WINDOW_CHUNKS) * 1000003L + Math.floorDiv(cz, WINDOW_CHUNKS);
        }

        /** P5b：4×4 chunk 子窗键（64×64 格 = 玩家步行可感知的疏密尺度）。 */
        private static long subWindowKey(int cx, int cz) {
            return Math.floorDiv(cx, 4) * 1000003L + Math.floorDiv(cz, 4);
        }

        /** P5b 判据 2：4×4 chunk 子窗总件数表（缺失键 = 0 件子窗，由调用方补零）。 */
        public Map<Long, Long> subwindowTotalsMap() {
            return subwindowTotals;
        }

        /** 落块摘要（在 {@link #finish()} 里<b>一次算好并缓存</b>——{@code MessageDigest.digest()}
         *  会重置状态，二次调用会拿到空数组的 SHA-256，把 C 组的"摘要相同"测成恒真假绿）。 */
        String digest() {
            return digestHex;
        }

        public double coveragePp() {
            return chunksSeen == 0 ? 0 : 100.0D * chunksWithScatter / chunksSeen;
        }

        public double chimneyChunkPp() {
            return chunksSeen == 0 ? 0 : 100.0D * chimneyChunks / chunksSeen;
        }

        public long chunksSeen() {
            return chunksSeen;
        }

        public long totalBlocks() {
            return sum(blocks);
        }

        public long totalContours() {
            return sum(contours);
        }

        public double contourMean() {
            return chunksSeen == 0 ? 0 : (double) sum(contours) / chunksSeen;
        }

        public double blockMean() {
            return chunksSeen == 0 ? 0 : (double) sum(blocks) / chunksSeen;
        }

        public double chimneyMean() {
            return chunksSeen == 0 ? 0 : (double) sum(chimneyCols) / chunksSeen;
        }

        public int contourMax() {
            return max(contours);
        }

        public int blockMax() {
            return max(blocks);
        }

        public int contourP95() {
            return p95(contours);
        }

        public int blockP95() {
            return p95(blocks);
        }

        // ══════════════════ P5b 判据 1/2 的分布读取口（成簇方差核对）══════════════════

        /** 件数分位数（p∈[0,1]，最近秩法；与 {@link #p95} 同一族估计量）。 */
        public int contourPercentile(double p) {
            final int[] a = new int[contours.size()];
            int i = 0;
            for (final int x : contours) {
                a[i++] = x;
            }
            java.util.Arrays.sort(a);
            return a.length == 0 ? 0 : a[Math.min(a.length - 1, Math.max(0, (int) Math.ceil(a.length * p) - 1))];
        }

        /** 件数 ∈ [lo, hi]（闭区间）的 chunk 占比（百分点）——判据 1 直方图桶。 */
        public double contourBucketPp(int lo, int hi) {
            int n = 0;
            for (final int x : contours) {
                if (x >= lo && x <= hi) {
                    n++;
                }
            }
            return contours.isEmpty() ? 0 : 100.0D * n / contours.size();
        }

        /** 件数变异系数 CV = 样本标准差 / 均值（判据 1 的"方差拉大"主指标）。 */
        public double contourCV() {
            final double mean = contourMean();
            if (mean <= 0) {
                return 0;
            }
            double sq = 0;
            for (final int x : contours) {
                final double d = x - mean;
                sq += d * d;
            }
            return Math.sqrt(sq / Math.max(1, contours.size() - 1)) / mean;
        }

        /** 每窗总件数表（键 = 窗键；未出现的窗 = 0 件，由调用方按样本窗数补零）。 */
        public Map<Long, Long> windowTotalsMap() {
            return windowTotals;
        }

        public int maxWinEmitChim() {
            return maxWinEmitChim;
        }

        public int maxWinPiecesChim() {
            return maxWinPiecesChim;
        }

        public int maxWinEmitFlat() {
            return maxWinEmitFlat;
        }

        public int windows() {
            return windows;
        }

        private static long sum(List<Integer> v) {
            long s = 0;
            for (final int x : v) {
                s += x;
            }
            return s;
        }

        private static int max(List<Integer> v) {
            int m = 0;
            for (final int x : v) {
                m = Math.max(m, x);
            }
            return m;
        }

        private static int p95(List<Integer> v) {
            final int[] a = new int[v.size()];
            int i = 0;
            for (final int x : v) {
                a[i++] = x;
            }
            Arrays.sort(a);
            return a.length == 0 ? 0 : a[Math.min(a.length - 1, (int) Math.ceil(a.length * 0.95D) - 1)];
        }

        private static void writeInt(byte[] buf, int off, int v) {
            buf[off] = (byte) (v >>> 24);
            buf[off + 1] = (byte) (v >>> 16);
            buf[off + 2] = (byte) (v >>> 8);
            buf[off + 3] = (byte) v;
        }
    }

    // ══════════════════════════ 反射写 Config（BASE 树兼容）══════════════════════════

    static void applySpec(String spec) {
        if (spec == null || spec.isEmpty()) {
            return;
        }
        try {
            final Class<?> cfg = Class.forName("com.miaokatze.gtsr.config.Config");
            for (final String kv : spec.split(",")) {
                final int eq = kv.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                final String key = kv.substring(0, eq)
                    .trim();
                final String value = kv.substring(eq + 1)
                    .trim();
                final Field f = findField(cfg, key);
                if (f == null) {
                    rememberMissing(key);
                    continue;
                }
                if (f.getType() == boolean.class) {
                    f.setBoolean(null, Boolean.parseBoolean(value));
                } else {
                    f.setInt(null, Integer.parseInt(value));
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("反射写 Config 失败：" + e, e);
        }
    }

    private static Field findField(Class<?> cfg, String key) {
        try {
            return cfg.getField(key);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static void rememberMissing(String key) {
        if (!MISSING_FIELDS.contains(key)) {
            MISSING_FIELDS.add(key);
            System.out.println("# NOTE 本树无 " + key + "（BASE 树预期如此）");
        }
    }

    // ══════════════════════════════ 合成世界与 Sink ══════════════════════════════

    /** 落格 + 计数 + 记录写入序列的 Sink（钳制规则与生产 ChunkClampedSink 同）。 */
    static final class ChainSink implements BlockSink {

        private static final int CAP = 8192;
        private final GridWorld world;
        final int[] xs = new int[CAP];
        final int[] ys = new int[CAP];
        final int[] zs = new int[CAP];
        final int[] metas = new int[CAP];
        int n;
        long accepted;
        long dropped;
        int cx;
        int cz;

        ChainSink(GridWorld world) {
            this.world = world;
        }

        void chunk(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
            n = 0;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            if ((x >> 4) != cx || (z >> 4) != cz || y < 0 || y > 255 || !(block instanceof Block)) {
                dropped++;
                return false;
            }
            accepted++;
            if (n < CAP) {
                xs[n] = x;
                ys[n] = y;
                zs[n] = z;
                metas[n] = meta;
                n++;
            }
            world.write(x, y, z, (Block) block);
            return true;
        }
    }

    /** 平坦网格合成世界（口径逐字承袭 SurfaceGateUnifyCheck.RegionWorld：界外 null、y&gt;255 bedrock）。 */
    public static final class GridWorld extends World {

        private Block[] grid;
        private int originBlockX;
        private int originBlockZ;
        private int side;
        private long seed;

        private GridWorld() {
            super((ISaveHandler) null, (String) null, (WorldProvider) null, (WorldSettings) null, (Profiler) null);
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
                return true; // 区外 = 未加载；散布/机器落点都钳在 chunk 内，故不影响计数（登记口径限制）
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
            return seed;
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

    /** 带种子/维号的 provider（getSeed 与 provider.dimensionId 口径同生产）。 */
    public static final class GP extends WorldProvider {

        private long seed;

        @Override
        public String getDimensionName() {
            return "gtsr-p5-density-harness";
        }

        @Override
        public long getSeed() {
            return seed;
        }
    }

    static GridWorld world(Block[] grid, int cx0, int cz0, long seed, int side) throws Exception {
        final sun.misc.Unsafe u = SurfaceHarness.unsafe();
        final GridWorld w = (GridWorld) u.allocateInstance(GridWorld.class);
        final WorldInfo info = (WorldInfo) u.allocateInstance(WorldInfo.class);
        final Field sf = WorldInfo.class.getDeclaredField("randomSeed");
        sf.setAccessible(true);
        u.putLong(info, u.objectFieldOffset(sf), seed);
        put(w, World.class, "worldInfo", info);
        final GP rp = (GP) u.allocateInstance(GP.class);
        final Field dim = WorldProvider.class.getDeclaredField("dimensionId");
        dim.setAccessible(true);
        u.putInt(rp, u.objectFieldOffset(dim), 78);
        final Field rs = GP.class.getDeclaredField("seed");
        rs.setAccessible(true);
        u.putLong(rp, u.objectFieldOffset(rs), seed);
        put(w, World.class, "provider", rp);
        put(w, GridWorld.class, "grid", grid);
        setInt(w, GridWorld.class, "originBlockX", cx0 << 4);
        setInt(w, GridWorld.class, "originBlockZ", cz0 << 4);
        setInt(w, GridWorld.class, "side", side);
        setLong(w, GridWorld.class, "seed", seed);
        return w;
    }

    private static void put(Object target, Class<?> owner, String field, Object value) throws Exception {
        final Field f = owner.getDeclaredField(field);
        f.setAccessible(true);
        SurfaceHarness.unsafe()
            .putObject(target, SurfaceHarness.unsafe().objectFieldOffset(f), value);
    }

    private static void setInt(Object target, Class<?> owner, String name, int value) throws Exception {
        final Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        SurfaceHarness.unsafe()
            .putInt(target, SurfaceHarness.unsafe().objectFieldOffset(f), value);
    }

    private static void setLong(Object target, Class<?> owner, String name, long value) throws Exception {
        final Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        SurfaceHarness.unsafe()
            .putLong(target, SurfaceHarness.unsafe().objectFieldOffset(f), value);
    }

    // ══════════════════════ 离线方块装配（P4 同款坑与解法）══════════════════════════

    /**
     * 补齐 {@link SurfaceHarness#blockFamily()} 未覆盖的 {@code BlocksGTSR} 字段
     * （构造参数逐字照抄 {@code SurfaceGateUnifyCheck.extraBlocks}）。缺席后果（P4 实测过的坑）：
     * {@code ruinDebris} 缺 ⇒ 散布写入被"句柄必须是 Block"规则丢光 ⇒ 密度测成假 0；
     * {@code prosperitySurface} 缺 ⇒ 冻结板面参与不了门；casing/tuft 缺 ⇒ 机器与 outpost 落块失真。
     */
    private static void extraBlocks() {
        if (BlocksGTSR.prosperitySurface == null) {
            BlocksGTSR.prosperitySurface = new BlockProsperitySurface();
        }
        if (BlocksGTSR.ruinDebris == null) {
            BlocksGTSR.ruinDebris = new BlockRuinDebris();
        }
        if (BlocksGTSR.ruinedCasing == null) {
            BlocksGTSR.ruinedCasing = new BlockRuinedCasing();
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
        final List<String> missing = new ArrayList<>();
        if (BlocksGTSR.ruinDebris == null) {
            missing.add("ruinDebris");
        }
        if (BlocksGTSR.prosperitySurface == null) {
            missing.add("prosperitySurface");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("散布域方块缺席，测出来必然是假 0：" + missing);
        }
    }

    private Dim78ScatterDensityCheck() {}
}
