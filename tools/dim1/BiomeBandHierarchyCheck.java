import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.BiomeZoneSelector;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlan;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.config.Config;

/**
 * H-1 群系身份分层自检（<b>B2 起链身份语义</b>；P6 时代的 macro/micro 带机制已退役，本检查随之改判）。
 * <p>
 * 离线运行（classpath 配方见 {@code plan/维度计划/调查取证/dim78-修复与整合/v12030-hotfix-replaceruntime-report.md} §3，
 * 与 {@code tools/dim1/surface_checks.sh} 同一套；任务包禁止 gradlew 全量构建）：
 * <pre>
 *   java ... BiomeBandHierarchyCheck assert [srcRoot] [seeds] [windowChunks]
 *   java ... BiomeBandHierarchyCheck table  [seeds] [windowChunks]
 *   java ... BiomeBandHierarchyCheck rollback &lt;MainClass&gt; [passthrough args...]   # [10]/[11] 的 AFTER 侧包装
 * </pre>
 * <p>
 * <b>采样口径（真实链，禁止手工构造群系）</b>：份额/连贯性一律走
 * {@link CityPlanner#bandIndexAt}（= 城门与渲染共用的 H-1 纯出口；B2 起本地重建等权
 * {@link GTSRGenLayerChain} 粗层，种子 {@code seed ^ def.seedSalt}、代表点 = chunk 中心块），
 * 身份对账走真实 {@link SurfaceHarness}（真实配槽账本 {@code recordAllAllocations} + 真实 def 表 +
 * {@link GTSRWorldChunkManager} 构造内的 L1 {@code bind}）。链自身的粗格性质（确定性/均分/连通域/
 * 直线边界/voronoi 抖动/构造期契约）由 {@code GTSRGenLayerSelfTest}（快链 [2k]）覆盖；chunk 代表点
 * 口径的空间统计由 {@code BiomeZoneCheck}（[3]）覆盖；本检查钉的是<b>接线与消费面</b>。
 * {@link BiomeZoneSelector} 在本检查只以 micro 强度层身份出场（0.7/1.0/1.3 档）。
 * <p>
 * <b>断言组（B2 改判后）</b>：
 * <ul>
 * <li><b>A 分层职责</b>：A0 三键默认档 + micro 档表（0.7/1.0/1.3、cell=16）仍在；
 * A1 城门纯出口 == 本地同参链粗层（种子礼仪/等权 id 表/chunk 中心代表点三要素，含负坐标 floorDiv 路径）；
 * A2 身份面连贯性（孤岛率 ≤ 8%、主导簇成片）；
 * A3 micro 层只给强度（同 micro cell 内恒定、三档均值 1.0、与身份互信息 ≈ 0 ⇒ 两层独立）；
 * A4 dim79 manager 接线 == 本地同参链（0x53484C53 盐）；A5 空表降级不伪造身份/强度（biomeAt null、
 * micro 1.0），真实配槽后 L1 账本仍 NONE。</li>
 * <li><b>B 恒等式与回退位（链时代等价面）</b>：B1 macro 键惰性——{@code prosperityBiomeMacroBandChunks}
 * 设回 {16,15,0,-64,999} 任一档，身份面逐位不变（带机制退役后该键不再进入任何身份路径）；
 * B1b 灵敏度（不同 seed 摘要必须不同，防两边恒真的假绿）。</li>
 * <li><b>C 单一真值 / 源级红线</b>：真实链 manager 的 {@code biomeAt}、本地重建链与城门纯出口
 * {@code bandIndexAt} 在 ≥16384 chunk 上三元逐位同一（⇒ 门与渲染不可能各算一套身份）；
 * CommonProxy 的 def seedSalt 字面量 == CityPlanner 的 {@code PROSPERITY_SEED_SALT}（0x50524F53L）；
 * 城门与 manager 禁 {@code getBiomeGenForCoords}；planner/manager 零 {@code BiomeZoneSelector} 引用；
 * manager 身份路径必经 {@code genChain.biomeAtCoarse}；渲染与抑制只有一处 {@code citiesNear}
 * 调用；Config 三个 P6 键"字段声明/同步块键名/同步块赋值"三处一致（plan §2.4 判据 4）。</li>
 * <li><b>D 等权守恒</b>：chunk 尺度份额对 25% 的偏差落在申报容差内（容差 = max(3σ, 下限 pp)，
 * σ 由实测样本数推得）；四个 macro 档位的份额/连贯性实测<b>逐位相同</b>（macro 键惰性的行为面证据）。</li>
 * <li><b>E 确定性</b>：同 seed 双跑的身份网格摘要与城布局摘要逐位相同（打印 SHA-256）。</li>
 * </ul>
 * <p>
 * <b>列名申报（{@code table} 模式；T1/T2/T4 见 {@code CityBiomeGateCheck}）</b>：
 * <pre>
 * # COLUMNS T3 macroCellChunks|seedCount|sampleChunks|share0Pp|share1Pp|share2Pp|share3Pp
 *            |shareDevMaxPp|shareTolPp|islandRatioMaxPp|maxClusterChunks|selected
 * </pre>
 * 份额下标 0..3 = dim78 名册序（0=锈蚀草原 / 1=齿轮森林 / 2=黄铜废土 / 3=喷气沼泽），基准 25%
 * （B2 起等权链；45-30-15-10 权重面随 selector 退役）。macroCellChunks 列只反映 Config 键当前值
 * ——四个档位的行应当<b>完全一致</b>（惰性键；D2 钉住）。
 */
public class BiomeBandHierarchyCheck {

    /** dim78 名册规模（B2 起身份 = 等权链，无权重面；名册序与 02 册 §1.1 同序）。 */
    private static final int BIOME_COUNT = 4;

    /** dim78 def.seedSalt（= CommonProxy 构造 def 处字面量 = CityPlanner.PROSPERITY_SEED_SALT）。 */
    private static final long SEED_SALT_P = 0x50524F53L;
    /** dim79 def.seedSalt（CommonProxy S-B3；两维同世界种子时链输出域分离）。 */
    private static final long SEED_SALT_S = 0x53484C53L;

    /** 固定 seed 集：与 P4/P5 的 {@code Dim78ScatterDensityCheck.SEEDS} 同一组，便于跨片对读。 */
    private static final long[] SEEDS = { 0x503441L, 0x503442L, 0x503443L, 0x503444L, 0x503445L, 0x503446L,
        0x503447L, 0x503448L };

    /** 每 seed 一个连续窗的边长（chunk）；8 × 256² = 524288 chunk 样本（≥ 任务包要求的 8×16384）。 */
    private static final int DEFAULT_WINDOW = 256;

    /** 份额容差：3σ（≈99.7%）+ 下限（zoom 中心多数票的收敛偏置申报值，等权基准 25pp）。 */
    private static final double SIGMA_TOL = 3.0D;
    private static final double CHUNK_TOL_FLOOR_PP = 5.0D;

    /** 连贯性阈值（与 {@code tools/dim1/BiomeZoneCheck} 同口径）。 */
    private static final double ISLAND_RATIO_MAX = 0.08D;

    /** 主导簇下限（chunk；zoom=4 特征片量级，留足裕量防碎斑）。 */
    private static final int DOMINANT_CLUSTER_MIN = 64;

    /** 惰性键扫描档（B2 语义：四档身份面必须逐位一致；Config 键本身仍钉默认 64）。 */
    static final int[] MACRO_SWEEP = { 16, 32, 64, 128 };

    private static int assertions;
    private static final List<String> FAILURES = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        final String mode = args.length > 0 ? args[0] : "assert";
        if ("rollback".equals(mode)) {
            if (args.length < 2) {
                System.out.println("用法：java BiomeBandHierarchyCheck rollback <MainClass> [passthrough args...]");
                System.exit(2);
            }
            // 判据「非本片路径零漂移」的 AFTER 侧通用包装：先把 P6 三个键设回改造前口径，再委托给
            // 指定工具自己的 main（两棵树跑同一份 dump/measure 代码）。BASE 树（P6 开工前快照）没有这
            // 三个键，反射写不进就登记并跳过。B2 起键已惰性（身份/城门不再读 macro 档），包装保留
            // 是为了 [10] 的 P5 摘要对拍与任何仍读键的消费方。
            // <b>必须在 Dim78ScatterDensityCheck.bootstrap() 之前委托</b>：bootstrap 会二次创建方块
            // 实例，而 SurfaceByteParityDump 的 per-chunk sha 覆盖"方块标签 + meta"，二次实例化会让
            // 两跑的标签编号不同 ⇒ 连 dim79 都跟着变（实测 774 行假漂移）。目标工具自己会做初始化。
            // 标记走 stderr：目标工具的 stdout 是要逐字节对拍的正文（实测混进一行就让 [10b] 白报差异）。
            System.err.println("# P6-ROLLBACK target=" + args[1] + " missing-fields=" + rollbackP6Keys());
            invokeMain(args[1], args, 2);
            return;
        }
        if ("table".equals(mode)) {
            // 只出表 3（份额/连贯性实测），不跑断言：全是纯函数计算，故不需要方块装配
            printTable(intArg(args, 1, SEEDS.length), intArg(args, 2, DEFAULT_WINDOW));
            return;
        }
        if (!"assert".equals(mode)) {
            // 未知 mode 一律拒跑：实测过 shell 被并发改写导致命令行退化成 `<bin> 8 256`，
            // 于是 mode="8"、srcRoot="8"，断言跑在一棵不存在的路径上（假红/假绿都可能）。
            System.out.println("BIOME BAND HIERARCHY FAIL: 未知 mode=" + mode
                + "（可用：assert [srcRoot] [seeds] [window] | table [seeds] [window] | rollback <Main> ...）");
            System.exit(2);
        }
        Dim78ScatterDensityCheck.bootstrap();
        SurfaceHarness.recordAllAllocations(SurfaceHarness.prosperityBiomes(), SurfaceHarness.shatteredBiomes());
        assertAll(args.length > 1 ? args[1] : "src/main/java");
        printTable(intArg(args, 2, SEEDS.length), intArg(args, 3, DEFAULT_WINDOW));
        finish();
    }

    /** 委托目标工具的 main（同 classpath，参数从 args[2] 起透传）。 */
    private static void invokeMain(String className, String[] args, int from) throws Exception {
        final String[] pass = new String[Math.max(0, args.length - from)];
        System.arraycopy(args, from, pass, 0, pass.length);
        final Class<?> target = Class.forName(className);
        try {
            target.getMethod("main", String[].class).invoke(null, (Object)pass);
        } catch (final java.lang.reflect.InvocationTargetException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            }
            throw new IllegalStateException(cause);
        }
    }

    /** 把 P6 的三个键设回改造前口径（BASE 树无这些字段 ⇒ 登记后跳过）。 */
    private static String rollbackP6Keys() {
        final StringBuilder missing = new StringBuilder();
        for (final String[] kv : new String[][] { { "prosperityBiomeMacroBandChunks", "16" },
            { "shatteredBiomeMacroBandChunks", "16" }, { "prosperityCityBiomeGate", "0" } }) {
            try {
                java.lang.reflect.Field f = Config.class.getField(kv[0]);
                f.setInt(null, Integer.parseInt(kv[1]));
            } catch (final ReflectiveOperationException e) {
                if (missing.length() > 0) {
                    missing.append(',');
                }
                missing.append(kv[0]);
            }
        }
        return missing.length() == 0 ? "none" : missing.toString();
    }

    private static int intArg(String[] args, int i, int def) {
        return args.length > i ? Integer.parseInt(args[i]) : def;
    }

    // ══════════════════════════════════ 断言 ══════════════════════════════════

    private static void assertAll(String srcRoot) throws Exception {
        groupA();
        groupB();
        groupC(srcRoot);
        groupD();
        groupE();
    }

    /** A 组：分层职责（链身份 + micro 强度两层）。 */
    private static void groupA() {
        final int macro = Config.prosperityBiomeMacroBandChunks;
        check(macro == 64, "A0 dim78 macro 键默认必须 == 64（Config 面钉；B2 起该键已惰性、不进身份/城门），实测 "
            + macro);
        check(BiomeZoneSelector.MICRO_CELL_CHUNKS == 16,
            "A0 micro cell 必须 == 16，实测 " + BiomeZoneSelector.MICRO_CELL_CHUNKS);
        check(Config.shatteredBiomeMacroBandChunks == 16,
            "A0 dim79 macro 键默认必须 == 16（同上，惰性面钉），实测 " + Config.shatteredBiomeMacroBandChunks);
        check(Config.prosperityCityBiomeGate == 1,
            "A0 城门默认档必须 == 1（锚点群系身份，U2 锁定），实测 " + Config.prosperityCityBiomeGate);
        check(BiomeZoneSelector.MICRO_STRENGTHS.length == 3
            && BiomeZoneSelector.MICRO_STRENGTHS[0] == 0.7F && BiomeZoneSelector.MICRO_STRENGTHS[2] == 1.3F,
            "A0 micro 强度档表必须是 0.7/1.0/1.3（U2 锁定）");

        // —— A1：城门纯出口 == 本地同参链粗层（三要素：种子礼仪 / 等权 id 表 / chunk 中心代表点）——
        final BiomeGenBase[] pb = SurfaceHarness.prosperityBiomes();
        final int[] chainIds = new int[pb.length];
        for (int i = 0; i < pb.length; i++) {
            chainIds[i] = pb[i].biomeID;
        }
        int a1Chunks = 0;
        int a1Bad = 0;
        for (int si = 0; si < SEEDS.length; si++) {
            final long seed = SEEDS[si];
            final GTSRGenLayerChain expect = new GTSRGenLayerChain(seed ^ SEED_SALT_P, chainIds);
            for (int dx = 0; dx < 64; dx++) {
                for (int dz = 0; dz < 64; dz++) {
                    // 负坐标与跨象限：floorDiv/(cx<<4)+8 的负路径必须同样成立
                    final int cx = si * 4096 + dx - 96;
                    final int cz = si * 924816 + dz - 128;
                    a1Chunks++;
                    if (CityPlanner.bandIndexAt(seed, cx, cz) != rosterIndexOfId(
                        expect.biomeAtCoarse((cx << 4) + 8, (cz << 4) + 8),
                        pb)) {
                        a1Bad++;
                    }
                }
            }
        }
        check(a1Bad == 0 && a1Chunks >= 16384, "A1 城门纯出口 bandIndexAt == 本地同参链粗层（seed^0x50524F53、"
            + "等权 id 表、chunk 中心代表点；含负坐标 floorDiv 路径）（" + a1Chunks + " chunk，差异 " + a1Bad
            + "）");

        // —— A2：身份面连贯性（连续窗；孤岛率 + 主导簇成片）——
        final int win = 128;
        double islandWorst = 0;
        long islandWorstSeed = 0;
        int clusterMin = Integer.MAX_VALUE;
        for (int si = 0; si < SEEDS.length; si++) {
            final long seed = SEEDS[si];
            final int x0 = si * 4096;
            final int z0 = si * 924816;
            final int[] grid = bandGrid(seed, x0, z0, win);
            final double island = islandRatio(grid, win);
            if (island > islandWorst) {
                islandWorst = island;
                islandWorstSeed = seed;
            }
            clusterMin = Math.min(clusterMin, largestClusters(grid, win)[dominantZone(grid)]);
        }
        check(islandWorst <= ISLAND_RATIO_MAX, "A2 身份面孤岛率（盐胡椒反指标）最坏 seed=" + islandWorstSeed
            + " = " + pct(islandWorst * 100.0D) + "% ≤ " + pct(ISLAND_RATIO_MAX * 100.0D) + "%");
        check(clusterMin >= DOMINANT_CLUSTER_MIN, "A2 主导群系最大 4-连通簇最坏 = " + clusterMin
            + " chunk ≥ " + DOMINANT_CLUSTER_MIN + "（链成片性塌到碎斑时该值会掉到个位数）");

        // —— A3：micro 层只给强度，不给身份 ——
        int tierCells = 0;
        double strengthSum = 0;
        final int[] tierHits = new int[BiomeZoneSelector.MICRO_STRENGTHS.length];
        final int[] identHits = new int[BIOME_COUNT];
        final int[][] joint = new int[BIOME_COUNT][tierHits.length];
        int intraCellDrift = 0;
        for (int si = 0; si < SEEDS.length; si++) {
            final long seed = SEEDS[si];
            for (int dx = 0; dx < 64; dx++) {
                for (int dz = 0; dz < 64; dz++) {
                    final int cx = si * 4096 + dx * 4;
                    final int cz = si * 924816 + dz * 4;
                    final int tier = BiomeZoneSelector.microStrengthTier(seed, cx, cz, SEED_SALT_P);
                    final int ident = CityPlanner.bandIndexAt(seed, cx, cz);
                    tierHits[tier]++;
                    identHits[ident]++;
                    joint[ident][tier]++;
                    strengthSum += BiomeZoneSelector.MICRO_STRENGTHS[tier];
                    tierCells++;
                    // 同 micro cell 内的另一坐标必须同档（强度层的空间粒度 == micro cell）
                    final int cx2 = cx + 3;
                    final int cz2 = cz + 5;
                    final int mc = BiomeZoneSelector.MICRO_CELL_CHUNKS;
                    if (Math.floorDiv(cx, mc) == Math.floorDiv(cx2, mc)
                        && Math.floorDiv(cz, mc) == Math.floorDiv(cz2, mc)
                        && BiomeZoneSelector.microStrengthTier(seed, cx2, cz2, SEED_SALT_P) != tier) {
                        intraCellDrift++;
                    }
                }
            }
        }
        check(intraCellDrift == 0, "A3 micro 强度档在同 micro cell 内必须恒定（漂移 " + intraCellDrift + "）");
        final double meanStrength = strengthSum / tierCells;
        check(Math.abs(meanStrength - 1.0D) < 0.02D,
            "A3 micro 三档均值必须 ≈1.0（采用后不改变 P5 钉住的总量口径），实测 " + fmt(meanStrength, 4));
        for (final int hits : tierHits) {
            check(hits * 4L >= tierCells, "A3 micro 三档份额均 ≥25%±（实测 " + Arrays.toString(tierHits) + " / "
                + tierCells + "）");
        }
        final double mi = mutualInformation(joint, identHits, tierHits, tierCells);
        check(mi < 0.01D,
            "A3 micro 强度档与链身份必须独立掷骰（互信息 " + fmt(mi, 5) + " nat < 0.01）");

        // —— A4：dim79 接线（manager == 本地同参链，0x53484C53 盐）——
        final BiomeGenBase[] sb = SurfaceHarness.shatteredBiomes();
        final int[] sChainIds = new int[sb.length];
        for (int i = 0; i < sb.length; i++) {
            sChainIds[i] = sb[i].biomeID;
        }
        final GTSRDimensionDef sDef = SurfaceHarness.def(false, sb, SurfaceHarness.shatteredWeights());
        final GTSRWorldChunkManager sMgr = new GTSRWorldChunkManager(SEEDS[0], sDef);
        final GTSRGenLayerChain sExpect = new GTSRGenLayerChain(SEEDS[0] ^ SEED_SALT_S, sChainIds);
        int sBad = 0;
        for (int dx = 0; dx < 64; dx++) {
            for (int dz = 0; dz < 64; dz++) {
                final int cx = dx - 30;
                final int cz = dz + 77;
                if (sMgr.biomeAt(cx, cz) != biomeOfId(sExpect.biomeAtCoarse((cx << 4) + 8, (cz << 4) + 8), sb)) {
                    sBad++;
                }
            }
        }
        check(sBad == 0,
            "A4 dim79 manager 身份面 == 本地同参链粗层（seed^0x53484C53；4096 chunk，差异 " + sBad + "）");

        // —— A5：空表降级不伪造身份/强度；真实配槽不污染 L1 账本 ——
        final GTSRWorldChunkManager emptyMgr = new GTSRWorldChunkManager(SEEDS[0], SurfaceHarness.emptyDef(true));
        check(emptyMgr.biomeAt(3, 5) == null, "A5 空表降级：biomeAt 仍为 null（P1 起不伪造 plains）");
        check(emptyMgr.microStrengthAt(3, 5) == 1.0F,
            "A5 空表降级（链缺席）：micro 强度返回中性 1.0，不伪造强度（B2 起门槛随链身份面判）");
        final GTSRDimensionDef singleDef = SurfaceHarness.def(true, new BiomeGenBase[] { pb[0] },
            new int[] { 1 });
        final GTSRWorldChunkManager singleMgr = new GTSRWorldChunkManager(SEEDS[0], singleDef);
        check(singleMgr.biomeAt(7, -9) == pb[0],
            "A5 单成员表：链身份恒为该成员（等权轮盘的退化档不伪造别的身份）");
        // 三态本身（NONE/SHORT/EMPTY 的推导）不属本片改动面，由 P1 的 BiomeAllocationCheck(121) 常驻钉住；
        // 这里只钉"分层接线没有污染账本"：真实配槽后 degraded 必须仍是 NONE。
        SurfaceHarness.recordAllAllocations(SurfaceHarness.prosperityBiomes(), SurfaceHarness.shatteredBiomes());
        check(GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY).degraded()
            == GTSRBiomeAuthority.Degraded.NONE,
            "A5 身份面接线不得污染 L1 账本（真实配槽后 degraded 仍为 NONE）");
    }

    /** B 组：恒等式与回退位（链时代等价面：macro 键惰性）。 */
    private static void groupB() {
        // B1：macro 键设回任意历史档/非法档，身份面必须逐位不变（带机制退役的行为面证据；
        // 非空泛——若任何路径重新消费该键，16/64 两档的网格摘要必然不同）
        final int saved = Config.prosperityBiomeMacroBandChunks;
        int bad = 0;
        int checked = 0;
        try {
            final String reference = digestGrid(SEEDS[0], 24);
            for (final int requested : new int[] { 16, 15, 0, -64, 999 }) {
                Config.prosperityBiomeMacroBandChunks = requested;
                if (!reference.equals(digestGrid(SEEDS[0], 24))) {
                    bad++;
                }
                checked++;
            }
        } finally {
            Config.prosperityBiomeMacroBandChunks = saved;
        }
        check(bad == 0 && checked == 5, "B1 macroCell ∈ {16,15,0,-64,999} 任一档，身份网格摘要逐位不变（"
            + checked + " 档，漂移 " + bad + "）⇒ 带机制退役后 Config 单键不再影响身份面");
        // B1b 灵敏度：不同 seed 的摘要必须不同（全同 ⇒ B1 是「两边都恒真」的假绿）
        check(!digestGrid(SEEDS[0], 24).equals(digestGrid(SEEDS[1], 24)),
            "B1b 灵敏度：不同 seed 的身份网格摘要必须不同（否则 B1 是恒真假绿）");
    }

    /** C 组：单一真值与源级红线。 */
    private static void groupC(String srcRoot) throws IOException {
        // —— C1：真实链 manager、本地重建链与城门纯出口三元逐位同一 ——
        final BiomeGenBase[] pb = SurfaceHarness.prosperityBiomes();
        SurfaceHarness.recordAllAllocations(pb, SurfaceHarness.shatteredBiomes());
        final int[] defWeights = SurfaceHarness.prosperityWeights();
        final GTSRDimensionDef def = SurfaceHarness.def(true, pb, defWeights);
        // 本地重建的同参链（seed ^ def.seedSalt、def 表 id 等权数组、chunk 中心代表点）独立复算期望值
        // ——钉住接线三要素（种子礼仪 / 等权数组 / 中心代表点）。
        final int[] chainIds = new int[pb.length];
        for (int i = 0; i < pb.length; i++) {
            chainIds[i] = pb[i].biomeID;
        }
        int c1Chunks = 0;
        int c1Bad = 0;
        int c1PlannerBad = 0;
        for (int si = 0; si < SEEDS.length; si++) {
            final long seed = SEEDS[si];
            final GTSRWorldChunkManager mgr = new GTSRWorldChunkManager(seed, def);
            final GTSRGenLayerChain expect = new GTSRGenLayerChain(seed ^ SEED_SALT_P, chainIds);
            for (int dx = 0; dx < 64; dx++) {
                for (int dz = 0; dz < 64; dz++) {
                    final int cx = si * 4096 + dx;
                    final int cz = si * 924816 + dz;
                    c1Chunks++;
                    final int expectId = expect.biomeAtCoarse((cx << 4) + 8, (cz << 4) + 8);
                    if (mgr.biomeAt(cx, cz) != biomeOfId(expectId, pb)) {
                        c1Bad++;
                    }
                    // B2 收口钉：城门纯函数与 manager 吃的是同一个身份面（逐位同一）
                    if (CityPlanner.bandIndexAt(seed, cx, cz) != rosterIndexOfBiome(mgr.biomeAt(cx, cz), pb)) {
                        c1PlannerBad++;
                    }
                }
            }
        }
        check(c1Bad == 0 && c1Chunks >= 16384, "C1 manager 身份面 == 本地重建同参链粗层（seed^salt、等权 id 表、"
            + "chunk 中心代表点）逐位同一（" + c1Chunks + " chunk，差异 " + c1Bad + "）⇒ B1 接线三要素钉住");
        check(c1PlannerBad == 0, "C1 城门纯出口 bandIndexAt == manager biomeAt 的名册下标（差异 " + c1PlannerBad
            + "/" + c1Chunks + "）⇒ B2 收口：城门与渲染/生成吃同一链身份面，无第二身份源");

        // —— C2：源级红线 ——
        final String proxy = read("com/miaokatze/gtsr/main/CommonProxy.java", srcRoot);
        final String planner = read(
            "com/miaokatze/gtsr/common/dimension/prosperity/ruins/city/CityPlanner.java", srcRoot);
        final String wcm = read("com/miaokatze/gtsr/common/dimension/framework/GTSRWorldChunkManager.java", srcRoot);
        final String scatter = read(
            "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperitySurfaceScatter.java", srcRoot);
        final String gw = read(
            "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java", srcRoot);
        final String cfg = read("com/miaokatze/gtsr/config/Config.java", srcRoot);
        final String plannerCode = codeOnly(planner);
        final String wcmCode = codeOnly(wcm);
        check(proxy.contains("0x" + Long.toHexString(SEED_SALT_P).toUpperCase(Locale.ROOT) + "L"),
            "C2 CommonProxy dim78 def.seedSalt 字面量必须 == 0x"
                + Long.toHexString(SEED_SALT_P).toUpperCase(Locale.ROOT) + "L（B2 起链种子礼仪的唯一盐源）");
        check(proxy.contains("0x" + Long.toHexString(SEED_SALT_S).toUpperCase(Locale.ROOT) + "L"),
            "C2 dim79 def.seedSalt 字面量必须在场（两维链输出域分离）");
        check(plannerCode.contains("PROSPERITY_SEED_SALT = 0x"
            + Long.toHexString(SEED_SALT_P).toUpperCase(Locale.ROOT) + "L"),
            "C2 CityPlanner.PROSPERITY_SEED_SALT 必须与 CommonProxy def 接线盐同值（城门本地重建链的种子"
                + "礼仪与 manager 逐位同源；两处不同值即为漂移）");
        check(!plannerCode.contains("BiomeZoneSelector"),
            "C2 城门零 BiomeZoneSelector 引用（B2 退役红线：身份只有链一条出口）");
        check(wcmCode.replace("BiomeZoneSelector.microStrength", "").indexOf("BiomeZoneSelector") < 0,
            "C2 manager 对 selector 的引用仅限 micro 强度层（microStrength/microStrengthTier），"
                + "身份路径零 selector 引用");
        check(!plannerCode.contains("getBiomeGenForCoords"),
            "C2 城门禁 getBiomeGenForCoords（plan §2.1 L6 红线：城中心最远跨 8 chunk 会触发邻 chunk 生成）");
        check(!wcmCode.contains("getBiomeGenForCoords"), "C2 GTSRWorldChunkManager 不得读世界 biome 平面");
        check(wcmCode.contains("genChain.biomeAtCoarse("),
            "C2 manager 的身份路径必须经链粗层 genChain.biomeAtCoarse（不得旁路自算）");
        check(plannerCode.contains("new GTSRGenLayerChain("),
            "C2 城门身份必须本地重建同参 GTSRGenLayerChain（而不是自己实现身份算法）");
        check(!codeOnly(scatter).contains("BiomeZoneSelector."),
            "C2 消费方（散布层）不得自己算身份——身份只在 H-1 出");
        check(!wcmCode.contains("bandRosterIndex") && !wcmCode.contains("bandIdentity("),
            "C2 manager 不得残留过渡期带出口（bandRosterIndex/bandIdentity 已随 B2 退役）");
        check(occurrences(codeOnly(gw), "CityPlanner.citiesNear(") == 1, "C2 渲染与抑制必须共用同一处 citiesNear"
            + " 调用（实测代码内 " + occurrences(codeOnly(gw), "CityPlanner.citiesNear(") + " 处）");
        for (final String key : new String[] { "prosperityBiomeMacroBandChunks", "shatteredBiomeMacroBandChunks",
            "prosperityCityBiomeGate" }) {
            final int decl = occurrences(cfg, "public static int " + key + " =");
            final int getk = occurrences(cfg, "\"" + key + "\"");
            final int use = occurrences(cfg, key + " = configuration.getInt");
            check(decl == 1 && getk == 1 && use == 1, "C2 Config 键 " + key
                + " 三处一致（字段声明 " + decl + " / 同步块键名 " + getk + " / 同步块赋值 " + use + "）");
        }
    }

    /** D 组：等权守恒（表 3 的断言面）。 */
    private static void groupD() {
        // D0：chunk 尺度份额对 25% 的守恒（等权链；容差 = max(3σ, 下限)）
        final int win = 128;
        final Measure m0 = measure(SEEDS.length, win, Config.prosperityBiomeMacroBandChunks);
        final double chunkTol = Math.max(CHUNK_TOL_FLOOR_PP, tolPp(m0.chunks));
        double chunkDev = 0;
        for (int i = 0; i < BIOME_COUNT; i++) {
            chunkDev = Math.max(chunkDev, Math.abs(100.0D * m0.chunkCounts[i] / m0.chunks - 100.0D / BIOME_COUNT));
        }
        check(m0.chunks >= 100_000, "D0 样本量门槛（实测 " + m0.chunks + " chunk ≥ 100000）");
        check(chunkDev <= chunkTol, "D0 chunk 尺度份额对等权基准 25pp 的最大偏差 " + pct(chunkDev) + "pp ≤ 容差 "
            + pct(chunkTol) + "pp（σ 由实测样本数推得 + 收敛偏置下限）⇒ 等权守恒");
        System.out.println("  # D0 chunk 份额(名册序 0..3) = "
            + (100.0D * m0.chunkCounts[0] / m0.chunks) + "/" + (100.0D * m0.chunkCounts[1] / m0.chunks) + "/"
            + (100.0D * m0.chunkCounts[2] / m0.chunks) + "/" + (100.0D * m0.chunkCounts[3] / m0.chunks)
            + " 基准 25pp，最大偏差 " + pct(chunkDev) + "pp，容差 " + pct(chunkTol) + "pp");
        for (int i = 0; i < BIOME_COUNT; i++) {
            check(m0.chunkCounts[i] > 0, "D0 四群系身份均须出现（实测 "
                + Arrays.toString(m0.chunkCounts) + " / " + m0.chunks + "）");
        }
        check(m0.islandWorst <= ISLAND_RATIO_MAX * 100.0D,
            "D 连贯性：孤岛率最坏 " + pct(m0.islandWorst / 100.0D) + "% ≤ " + pct(ISLAND_RATIO_MAX * 100.0D) + "%");

        // D2：macro 键惰性的行为面证据——四个档位的份额/连贯性实测逐位相同
        Measure reference = null;
        int drift = 0;
        for (final int macro : MACRO_SWEEP) {
            final Measure m = measure(4, 64, macro);
            if (reference == null) {
                reference = m;
            } else if (!Arrays.equals(m.chunkCounts, reference.chunkCounts)
                || m.islandWorst != reference.islandWorst
                || m.clusterDominantMin != reference.clusterDominantMin) {
                drift++;
            }
        }
        check(drift == 0, "D2 macro ∈ " + Arrays.toString(MACRO_SWEEP) + " 四档的身份份额/连贯性实测逐位相同（"
            + "漂移档数 " + drift + "）⇒ macro 键已不进入身份路径（B1 行为面 + B1 源级的互相印证）");
    }

    /** E 组：确定性双跑（身份网格 + 城布局）。 */
    private static void groupE() {
        final int win = 64;
        for (int si = 0; si < 3; si++) {
            final long seed = SEEDS[si];
            final int x0 = si * 4096;
            final int z0 = si * 924816;
            final String b1 = sha(bandGrid(seed, x0, z0, win));
            final String b2 = sha(bandGrid(seed, x0, z0, win));
            check(b1.equals(b2), "E1 身份网格双跑摘要不同 seed=" + seed);
            final String c1 = cityDigest(seed, x0, z0, win);
            final String c2 = cityDigest(seed, x0, z0, win);
            check(c1.equals(c2), "E2 城布局（城门生效后 cellSeed/锚点/半径序列）双跑摘要不同 seed=" + seed);
            System.out.println("  # determinism seed=" + seed + " identityGridSHA=" + b1.substring(0, 16)
                + " cityLayoutSHA=" + c1.substring(0, 16));
        }
    }

    // ══════════════════════════════════ 表 3 ══════════════════════════════════

    private static void printTable(int seeds, int win) {
        System.out.println(
            "# COLUMNS T3 macroCellChunks|seedCount|sampleChunks|share0Pp|share1Pp|share2Pp|share3Pp"
                + "|shareDevMaxPp|shareTolPp|islandRatioMaxPp|maxClusterChunks|selected");
        for (final int macro : MACRO_SWEEP) {
            final Measure m = measure(seeds, win, macro);
            double chunkDev = 0;
            final StringBuilder sb = new StringBuilder("T3 ");
            sb.append(macro).append('|')
                .append(seeds)
                .append('|')
                .append(m.chunks);
            for (int i = 0; i < BIOME_COUNT; i++) {
                final double share = 100.0D * m.chunkCounts[i] / m.chunks;
                sb.append('|').append(pct(share));
                chunkDev = Math.max(chunkDev, Math.abs(share - 100.0D / BIOME_COUNT));
            }
            sb.append('|').append(pct(chunkDev)).append('|')
                .append(pct(Math.max(CHUNK_TOL_FLOOR_PP, tolPp(m.chunks)))).append('|').append(pct(m.islandWorst))
                .append('|').append(m.clusterDominantMin).append('|')
                .append(macro == Config.prosperityBiomeMacroBandChunks ? "SELECTED" : "-");
            System.out.println(sb);
        }
    }

    /** 一次测量（chunk 尺度份额 + 连贯性；macro 只写进 Config 键，不进入采样路径——D2 钉惰性）。 */
    private static final class Measure {
        final long[] chunkCounts = new long[BIOME_COUNT];
        long chunks;
        double islandWorst;
        int clusterDominantMin = Integer.MAX_VALUE;
    }

    private static Measure measure(int seeds, int win, int macro) {
        final Measure m = new Measure();
        final int saved = Config.prosperityBiomeMacroBandChunks;
        try {
            Config.prosperityBiomeMacroBandChunks = macro;
            for (int si = 0; si < seeds; si++) {
                final long seed = SEEDS[si % SEEDS.length];
                final int x0 = si * 4096;
                final int z0 = si * 924816;
                final int[] grid = bandGrid(seed, x0, z0, win);
                for (final int z : grid) {
                    m.chunkCounts[z]++;
                }
                m.chunks += (long)win * win;
                m.islandWorst = Math.max(m.islandWorst, islandRatio(grid, win) * 100.0D);
                m.clusterDominantMin = Math.min(m.clusterDominantMin, largestClusters(grid, win)[dominantZone(grid)]);
            }
            return m;
        } finally {
            Config.prosperityBiomeMacroBandChunks = saved;
        }
    }

    /** 二项容差（百分点）：3σ，σ = sqrt(p(1-p)/n)，等权 p = 1/N 的最不利者即 1/N。 */
    private static double tolPp(long n) {
        if (n <= 0) {
            return 100.0D;
        }
        final double p = 1.0D / BIOME_COUNT;
        return Math.sqrt(p * (1 - p) / n) * 100.0D * SIGMA_TOL;
    }

    // ══════════════════════════════════ 工具方法 ══════════════════════════════════

    /** 名册序下标（按 biome 实例身份对账；null ⇒ -1，与 biomeAt 的空表口径同源）。 */
    private static int rosterIndexOfBiome(BiomeGenBase biome, BiomeGenBase[] roster) {
        if (biome == null) {
            return -1;
        }
        for (int i = 0; i < roster.length; i++) {
            if (roster[i] == biome) {
                return i;
            }
        }
        return -1;
    }

    /** 名册序下标（按 biome id 对账；不在名册 ⇒ -1，防御路径不伪造身份）。 */
    private static int rosterIndexOfId(int id, BiomeGenBase[] roster) {
        for (int i = 0; i < roster.length; i++) {
            if (roster[i].biomeID == id) {
                return i;
            }
        }
        return -1;
    }

    /** 按 id 取名册成员实例（不在名册 ⇒ null）。 */
    private static BiomeGenBase biomeOfId(int id, BiomeGenBase[] roster) {
        for (int i = 0; i < roster.length; i++) {
            if (roster[i].biomeID == id) {
                return roster[i];
            }
        }
        return null;
    }

    /** 身份网格摘要（SHA-256；B1/B1b 的比对口径）。 */
    private static String digestGrid(long seed, int half) {
        final MessageDigest md = sha256();
        for (int dx = 0; dx < 2 * half; dx++) {
            for (int dz = 0; dz < 2 * half; dz++) {
                putInt(md, CityPlanner.bandIndexAt(seed, dx - half, dz - half));
            }
        }
        return hex(md.digest());
    }

    /** 连续窗的身份网格（走 H-1 纯出口，与生产行为同一函数）。 */
    private static int[] bandGrid(long seed, int cx0, int cz0, int win) {
        final int[] grid = new int[win * win];
        for (int dz = 0; dz < win; dz++) {
            for (int dx = 0; dx < win; dx++) {
                grid[dx + dz * win] = CityPlanner.bandIndexAt(seed, cx0 + dx, cz0 + dz);
            }
        }
        return grid;
    }

    /** 去掉注释行（{@code //}、{@code *} 开头）与块注释，只留代码体——
     * 防止"文档里提到某 API"被误判成调用（正例：本片的 L6 红线在 javadoc 里点名了被禁的读面 API）。
     */
    private static String codeOnly(String src) {
        final StringBuilder sb = new StringBuilder(src.length());
        final java.io.BufferedReader reader =
            new java.io.BufferedReader(new java.io.StringReader(src));
        boolean inBlock = false;
        try {
            String raw;
            while ((raw = reader.readLine()) != null) {
                final String t = raw.trim();
                if (inBlock) {
                    if (t.contains("*/")) {
                        inBlock = false;
                    }
                    continue;
                }
                if (t.startsWith("/*") && !t.contains("*/")) {
                    inBlock = true;
                    continue;
                }
                if (t.startsWith("//") || t.startsWith("*") || t.isEmpty()) {
                    continue;
                }
                sb.append(raw).append(0x0A);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return sb.toString();
    }

    /** 主导 zone（网格内计数最多者；等权链下不再依赖权重表）。 */
    private static int dominantZone(int[] grid) {
        final long[] counts = new long[BIOME_COUNT];
        for (final int z : grid) {
            counts[z]++;
        }
        int best = 0;
        for (int i = 1; i < BIOME_COUNT; i++) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        return best;
    }

    private static double islandRatio(int[] grid, int win) {
        long interior = 0;
        long islands = 0;
        for (int z = 1; z < win - 1; z++) {
            for (int x = 1; x < win - 1; x++) {
                final int idx = x + z * win;
                final int zone = grid[idx];
                interior++;
                if (grid[idx - 1] != zone && grid[idx + 1] != zone && grid[idx - win] != zone
                    && grid[idx + win] != zone) {
                    islands++;
                }
            }
        }
        return interior == 0 ? 0 : (double)islands / interior;
    }

    private static int[] largestClusters(int[] grid, int win) {
        final int[] sizes = new int[BIOME_COUNT];
        final boolean[] seen = new boolean[grid.length];
        final int[] stack = new int[grid.length];
        for (int start = 0; start < grid.length; start++) {
            if (seen[start]) {
                continue;
            }
            final int zone = grid[start];
            int top = 0;
            stack[top++] = start;
            seen[start] = true;
            int size = 0;
            while (top > 0) {
                final int idx = stack[--top];
                size++;
                final int x = idx % win;
                final int z = idx / win;
                if (x > 0 && !seen[idx - 1] && grid[idx - 1] == zone) {
                    seen[idx - 1] = true;
                    stack[top++] = idx - 1;
                }
                if (x < win - 1 && !seen[idx + 1] && grid[idx + 1] == zone) {
                    seen[idx + 1] = true;
                    stack[top++] = idx + 1;
                }
                if (z > 0 && !seen[idx - win] && grid[idx - win] == zone) {
                    seen[idx - win] = true;
                    stack[top++] = idx - win;
                }
                if (z < win - 1 && !seen[idx + win] && grid[idx + win] == zone) {
                    seen[idx + win] = true;
                    stack[top++] = idx + win;
                }
            }
            sizes[zone] = Math.max(sizes[zone], size);
        }
        return sizes;
    }

    /** 城门生效后的城布局摘要（cellSeed + 锚点 chunk + 半径 + 门判定；不含 plot 细节）。 */
    private static String cityDigest(long seed, int cx0, int cz0, int win) {
        final MessageDigest md = sha256();
        final int c0 = Math.floorDiv(cx0, CityPlanner.CITY_CELL) - 1;
        final int c1 = Math.floorDiv(cx0 + win, CityPlanner.CITY_CELL) + 1;
        final int z0 = Math.floorDiv(cz0, CityPlanner.CITY_CELL) - 1;
        final int z1 = Math.floorDiv(cz0 + win, CityPlanner.CITY_CELL) + 1;
        for (int cx = c0; cx <= c1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                final CityPlan p = CityPlanner.planFor(seed, cx, cz);
                if (p == null) {
                    continue;
                }
                md.update((byte)(CityPlanner.cityGateAllows(seed, p) ? 1 : 0));
                putLong(md, p.getCellSeed());
                putInt(md, p.getCenterChunkX());
                putInt(md, p.getCenterChunkZ());
                putInt(md, p.getRadiusChunks());
            }
        }
        return hex(md.digest());
    }

    /** 列联表互信息（nat）——"micro 强度档与链身份独立"的量化口径。 */
    private static double mutualInformation(int[][] joint, int[] rowTotals, int[] colTotals, long total) {
        double mi = 0;
        for (int i = 0; i < rowTotals.length; i++) {
            for (int j = 0; j < colTotals.length; j++) {
                if (joint[i][j] == 0) {
                    continue;
                }
                final double pij = (double)joint[i][j] / total;
                final double pi = (double)rowTotals[i] / total;
                final double pj = (double)colTotals[j] / total;
                mi += pij * Math.log(pij / (pi * pj));
            }
        }
        return Math.max(0, mi);
    }

    private static String sha(int[] grid) {
        final MessageDigest md = sha256();
        for (final int v : grid) {
            md.update((byte)v);
        }
        return hex(md.digest());
    }

    private static void putInt(MessageDigest md, int v) {
        md.update((byte)(v >>> 24));
        md.update((byte)(v >>> 16));
        md.update((byte)(v >>> 8));
        md.update((byte)v);
    }

    private static void putLong(MessageDigest md, long v) {
        for (int i = 56; i >= 0; i -= 8) {
            md.update((byte)(v >>> i));
        }
    }

    private static String hex(byte[] data) {
        final StringBuilder sb = new StringBuilder();
        for (final byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(String rel, String root) throws IOException {
        return new String(Files.readAllBytes(Paths.get(root, rel)), StandardCharsets.UTF_8);
    }

    private static int occurrences(String haystack, String needle) {
        int n = 0;
        int from = 0;
        while (true) {
            final int i = haystack.indexOf(needle, from);
            if (i < 0) {
                return n;
            }
            n++;
            from = i + needle.length();
        }
    }

    private static String pct(double v) {
        return fmt(v, 3);
    }

    private static String fmt(double v, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", v);
    }

    private static void check(boolean ok, String label) {
        assertions++;
        if (!ok) {
            FAILURES.add(label);
            System.out.println("  FAIL " + label);
        }
    }

    private static void finish() {
        if (!FAILURES.isEmpty()) {
            System.out.println("BIOME BAND HIERARCHY FAIL: " + FAILURES.size() + " assertion(s) failed, passed="
                + (assertions - FAILURES.size()));
            System.exit(1);
        }
        System.out.println("BIOME BAND HIERARCHY PASS: assertions=" + assertions
            + " — 分层职责/键惰性/单一真值/等权守恒/确定性 all green (B2 chain face)");
    }
}
