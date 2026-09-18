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
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlan;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.config.Config;

/**
 * P6 · H-1 群系带分层自检（plan §2.2 H-1 + §5 P6 目标 1 + §7.1 已锁定 U2「macro 64 chunk + micro 16」）。
 * <p>
 * 离线运行（classpath 配方见 {@code plan/investigation/v12030-hotfix-replaceruntime-report.md} §3，
 * 与 {@code tools/dim1/surface_checks.sh} 同一套；任务包禁止 gradlew 全量构建）：
 * <pre>
 *   java ... BiomeBandHierarchyCheck assert [srcRoot] [seeds] [windowChunks]
 *   java ... BiomeBandHierarchyCheck table  [seeds] [windowChunks]
 * </pre>
 * <p>
 * <b>采样口径（真实链，禁止手工构造群系）</b>：份额/连贯性一律走
 * {@link CityPlanner#bandIndexAt}（= 城门与渲染共用的 H-1 纯出口），身份对账走真实
 * {@link SurfaceHarness}（真实配槽账本 {@code recordAllAllocations} + 真实 def 权重表 +
 * {@link GTSRWorldChunkManager} 构造内的 L1 {@code bind}）。样本 = 每 seed 一个<b>连续</b>
 * {@code windowChunks}² 窗（默认 8 × 128² = 131072 chunk ≥ 任务包「≥8 seed × ≥16384 chunk」）；
 * 连续窗是为让"最大簇/孤岛率"这类连通性统计有意义（离散 16×16 区只能做逐块份额）。
 * <p>
 * <b>断言组</b>：
 * <ul>
 * <li><b>A 分层职责</b>：A0 三键默认档（dim78 macro=64 / dim79 macro=16 / micro=16）；
 * A1 非边带 chunk 的身份 == 其 macro 带基准掷骰（穷举 0 例外 ⇒ 身份由带决定，不是逐 chunk 盐胡椒）；
 * A2 连贯性（孤岛率 ≤ {@code BiomeZoneCheck} 同口径 8% 且带尺度最大簇不得比 16 口径更碎）；
 * A3 micro 层只给强度（同 micro cell 内恒定、三档均值 1.0、与身份互信息 ≈ 0 ⇒ 两层独立）；
 * A4 dim79 带尺度仍 16 且身份网格与改造前单层口径逐位相同（判据「dim79 零改动」）；
 * A5 未挂 selector / 空表降级 ⇒ 身份出口 −1、{@code biomeAt} null、强度中性 1.0（不伪造身份，L1 三态不变）。</li>
 * <li><b>B 恒等式与回退位</b>：{@code bandIndex(cell=16s)} == {@code select(坐标按 s 折算, cell=16)}
 * == {@code select(原坐标, cell=16s)}（s=1..8 穷举含负坐标）；{@code macroCell ∈ {16,15,0,-64}} 一律
 * 逐位回退改造前口径。</li>
 * <li><b>C 单一真值 / 源级红线</b>：真实链 manager 与城门纯出口在 ≥16384 chunk 上逐位同一（⇒ 门与渲染
 * 不可能各算一套带）；CommonProxy 接线盐字面量 == {@link BiomeZoneSelector#ZONE_SALT_PROSPERITY}；
 * 城门与 manager 禁 {@code getBiomeGenForCoords}；消费方（散布层）不得自己算带；manager 身份路径必经
 * {@code bandIdentity}；渲染与抑制只有一处 {@code citiesNear} 调用；Config 三个新键"字段声明/同步块键名/
 * 同步块赋值"三处一致（plan §2.4 判据 4）。</li>
 * <li><b>D 权重守恒</b>：带尺度与 chunk 尺度份额对 45-30-15-10 的偏差均落在申报容差内
 * （容差 = max(3σ, 下限 pp)，σ 由实测样本数推得，下限是"边带 12% 改掷带来的系统性偏移"申报值）。</li>
 * <li><b>E 确定性</b>：同 seed 双跑的带划分网格摘要与城布局摘要逐位相同（打印 SHA-256）。</li>
 * </ul>
 * <p>
 * <b>列名申报（{@code table} 模式 = 判据 1 的表 3；表 1/2/4 见 {@code CityBiomeGateCheck}）</b>：
 * <pre>
 * # COLUMNS T3 macroCellChunks|seedCount|sampleChunks|nBands|bandShare0Pp|bandShare1Pp|bandShare2Pp
 *            |bandShare3Pp|bandDevMaxPp|bandTolPp|chunkShare0Pp|chunkShare1Pp|chunkShare2Pp|chunkShare3Pp
 *            |chunkDevMaxPp|chunkTolPp|islandRatioMaxPp|maxClusterChunks|selected
 * </pre>
 * 份额下标 0..3 = dim78 名册序（0=锈蚀草原 / 1=齿轮森林 / 2=黄铜废土 / 3=喷气沼泽）。
 */
public class BiomeBandHierarchyCheck {

    /** dim78 名册序权重表（02 册 §1.1 设计口径 45-30-15-10；与四个群系类各自的 WEIGHT 常数同源）。 */
    private static final int[] WEIGHTS = { 45, 30, 15, 10 };
    private static final int BIOME_COUNT = WEIGHTS.length;
    private static final int TOTAL_WEIGHT = 100;

    /** dim78 接线盐（与 {@link BiomeZoneSelector#ZONE_SALT_PROSPERITY} 同源；C2 另做源级对账）。 */
    private static final long SALT_P = BiomeZoneSelector.ZONE_SALT_PROSPERITY;
    /** dim79 接线盐（CommonProxy S-B3 = dim78 盐 +1 域分离）。 */
    private static final long SALT_S = SALT_P + 1;
    /** dim78 def.seedSalt（micro 强度层的域分离输入，与 GTSRWorldChunkManager 同口径）。 */
    private static final long SEED_SALT_P = 0x50524F53L;

    /** 固定 seed 集：与 P4/P5 的 {@code Dim78ScatterDensityCheck.SEEDS} 同一组，便于跨片对读。 */
    private static final long[] SEEDS = { 0x503441L, 0x503442L, 0x503443L, 0x503444L, 0x503445L, 0x503446L,
        0x503447L, 0x503448L };

    /** 每 seed 一个连续窗的边长（chunk）；8 × 256² = 524288 chunk 样本（≥ 任务包要求的 8×16384）。 */
    private static final int DEFAULT_WINDOW = 256;

    /** 带基准掷骰抽样边长（每 seed n×n 个带索引；8 × 20² = 3200 带，够把 3σ 压到 1pp 级）。 */
    private static final int BAND_SAMPLE_PER_SEED = 20;

    /** 份额容差：3σ（≈99.7%）+ 系统性偏移下限（边带 12% 改掷带来的非噪声偏差，申报值）。 */
    private static final double SIGMA_TOL = 3.0D;
    private static final double BAND_TOL_FLOOR_PP = 5.0D;
    private static final double CHUNK_TOL_FLOOR_PP = 2.5D;

    /** 连贯性阈值（与 {@code tools/dim1/BiomeZoneCheck} 同口径）。 */
    private static final double ISLAND_RATIO_MAX = 0.08D;

    /** macro 扫描档（表 1/2/3 共用；U2 锁定档 64 在其中）。 */
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
            // 判据「非本片路径零漂移」的 AFTER 侧通用包装：先把本片三个新键设回改造前口径，再委托给
            // 指定工具自己的 main（两棵树跑同一份 dump/measure 代码）。BASE 树（P6 开工前快照）没有这
            // 三个键，反射写不进就登记并跳过。
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

    /** 把本片的三个新键设回改造前口径（BASE 树无这些字段 ⇒ 登记后跳过）。 */
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

    /** A 组：分层职责。 */
    private static void groupA() {
        final int macro = Config.prosperityBiomeMacroBandChunks;
        check(macro == 64, "A0 dim78 macro 带尺度默认必须 == 64（U2 锁定档），实测 " + macro);
        check(BiomeZoneSelector.MICRO_CELL_CHUNKS == 16,
            "A0 micro cell 必须 == 16，实测 " + BiomeZoneSelector.MICRO_CELL_CHUNKS);
        check(Config.shatteredBiomeMacroBandChunks == 16,
            "A0 dim79 macro 带尺度默认必须 == 16（本片对 dim79 零改动），实测 "
                + Config.shatteredBiomeMacroBandChunks);
        check(Config.prosperityCityBiomeGate == 1,
            "A0 城门默认档必须 == 1（锚点带，U2 锁定），实测 " + Config.prosperityCityBiomeGate);
        check(BiomeZoneSelector.ZONE_CELL_CHUNKS == BiomeZoneSelector.MICRO_CELL_CHUNKS,
            "A0 兼容别名 ZONE_CELL_CHUNKS 必须与 MICRO_CELL_CHUNKS 同值（既有接线/工具的 16 口径不能漂）");
        check(BiomeZoneSelector.MICRO_STRENGTHS.length == 3
            && BiomeZoneSelector.MICRO_STRENGTHS[0] == 0.7F && BiomeZoneSelector.MICRO_STRENGTHS[2] == 1.3F,
            "A0 micro 强度档表必须是 0.7/1.0/1.3（U2 锁定）");

        // —— A1：非边带 chunk 的身份 == 带原点基准掷骰；边带偏离率复算 ——
        int a1Chunks = 0;
        int a1Bad = 0;
        int edgeChunks = 0;
        int edgeDeviated = 0;
        for (int si = 0; si < SEEDS.length; si++) {
            final long seed = SEEDS[si];
            for (int dx = 0; dx < 64; dx++) {
                for (int dz = 0; dz < 64; dz++) {
                    final int cx = si * 4096 + dx;
                    final int cz = si * 924816 + dz;
                    final int base = BiomeZoneSelector.zoneOfCell(
                        seed,
                        Math.floorDiv(cx, macro),
                        Math.floorDiv(cz, macro),
                        BIOME_COUNT,
                        WEIGHTS,
                        SALT_P);
                    final int actual = CityPlanner.bandIndexAt(seed, cx, cz);
                    if (BiomeZoneSelector.isEdgeBand(cx, cz, macro)) {
                        edgeChunks++;
                        if (actual != base) {
                            edgeDeviated++;
                        }
                        continue;
                    }
                    a1Chunks++;
                    if (actual != base) {
                        a1Bad++;
                    }
                }
            }
        }
        check(a1Bad == 0 && a1Chunks > 3000, "A1 非边带 chunk 身份必须等于其 macro 带基准掷骰（穷举 " + a1Chunks
            + " chunk，例外 " + a1Bad + "）");
        final double edgeRate = (double)edgeDeviated / edgeChunks;
        System.out.println("  # A1b 带尺度边带改掷率 = " + pct(edgeRate * 100.0D) + "% ("
            + edgeDeviated + "/" + edgeChunks + " 边带 chunk)；非边带身份全等带基准 " + a1Chunks + " chunk");
        check(edgeRate > 0.09D && edgeRate < 0.135D, "A1b 边带 12% 改掷率在带尺度上仍成立（实测 "
            + pct(edgeRate * 100.0D) + "% ∈ (9%,13.5%)，BiomeZoneCheck 在 16 口径钉 [11%,13%]；"
            + "带尺度邻带全同基准时保持基准，故理论上限略低于 12%）");

        // —— A2：连贯性（连续窗；与 16 口径同窗对比）——
        final int win = 256;
        double islandWorst = 0;
        long islandWorstSeed = 0;
        int clusterMin = Integer.MAX_VALUE;
        double islandWorstBaseline = 0;
        for (int si = 0; si < SEEDS.length; si++) {
            final long seed = SEEDS[si];
            final int x0 = si * 4096;
            final int z0 = si * 924816;
            final int[] grid = bandGridAt(seed, x0, z0, win, macro);
            final double island = islandRatio(grid, win);
            if (island > islandWorst) {
                islandWorst = island;
                islandWorstSeed = seed;
            }
            clusterMin = Math.min(clusterMin, largestClusters(grid, win)[dominantZone()]);
            islandWorstBaseline = Math.max(islandWorstBaseline, islandRatio(bandGridAt(seed, x0, z0, win, 16), win));
        }
        check(islandWorst <= ISLAND_RATIO_MAX, "A2 macro 带孤岛率（盐胡椒反指标）最坏 seed=" + islandWorstSeed
            + " = " + pct(islandWorst * 100.0D) + "% ≤ " + pct(ISLAND_RATIO_MAX * 100.0D) + "%");
        check(islandWorst <= islandWorstBaseline + 1e-9, "A2 带尺度孤岛率必须 ≤ 16 口径实测最坏值（"
            + pct(islandWorst * 100.0D) + "% vs " + pct(islandWorstBaseline * 100.0D) + "%）⇒ 分层不得把"
            + " chunk 尺度打碎");
        final int bandCore = macro - 2 * bandChunks(macro);
        check(clusterMin >= bandCore * bandCore, "A2 主导带最大 4-连通簇最坏 seed = " + clusterMin
            + " chunk ≥ 一带内核面积 " + bandCore + "²=" + (bandCore * bandCore)
            + "（带减去两侧边带后的必然连片核；碎成噪点时该值会塌到个位数）");

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
            "A3 micro 强度档与 macro 身份必须独立掷骰（互信息 " + fmt(mi, 5) + " nat < 0.01）");

        // —— A4：dim79 零改动 ——
        final BiomeGenBase[] sb = SurfaceHarness.shatteredBiomes();
        final int[] sw = SurfaceHarness.shatteredWeights();
        final GTSRDimensionDef sDef = SurfaceHarness.def(false, sb, sw);
        final GTSRWorldChunkManager sMgr = new GTSRWorldChunkManager(SEEDS[0], sDef);
        check(sMgr.macroBandChunks() == BiomeZoneSelector.MICRO_CELL_CHUNKS,
            "A4 dim79 manager 解析出的带尺度必须 == 16，实测 " + sMgr.macroBandChunks());
        int sBad = 0;
        for (int dx = 0; dx < 64; dx++) {
            for (int dz = 0; dz < 64; dz++) {
                final int cx = dx - 30;
                final int cz = dz + 77;
                final int legacy = BiomeZoneSelector.select(SEEDS[0], cx, cz, BIOME_COUNT, sw, 16, SALT_S);
                if (sMgr.bandRosterIndex(cx, cz) != legacy || sMgr.biomeAt(cx, cz) != null && sw.length == 0) {
                    sBad++;
                }
            }
        }
        check(sBad == 0,
            "A4 dim79 身份网格与改造前单层 select(...,cell=16,...) 逐位相同（4096 chunk，差异 " + sBad + "）");

        // —— A5：未挂 selector / 空表降级不伪造身份 ——
        final GTSRDimensionDef noSel = new GTSRDimensionDef(
            "anon-p6",
            "P6 anon",
            SEED_SALT_P,
            -1,
            -1,
            () -> true,
            null,
            null);
        noSel.addBiome(SurfaceHarness.prosperityBiomes()[0], WEIGHTS[0]);
        final GTSRWorldChunkManager anonMgr = new GTSRWorldChunkManager(SEEDS[0], noSel);
        check(anonMgr.bandRosterIndex(3, 5) == -1, "A5 未挂 selector 的 def：身份出口必须返回 -1（不伪造）");
        check(anonMgr.microStrengthAt(3, 5) == 1.0F, "A5 未挂 selector 的 def：micro 强度必须返回中性 1.0");
        final GTSRWorldChunkManager emptyMgr = new GTSRWorldChunkManager(SEEDS[0], SurfaceHarness.emptyDef(true));
        check(emptyMgr.bandRosterIndex(3, 5) == -1, "A5 空表降级：身份出口必须 -1");
        check(emptyMgr.biomeAt(3, 5) == null, "A5 空表降级：biomeAt 仍为 null（P1 起不伪造 plains）");
        check(emptyMgr.microStrengthAt(3, 5) == 1.0F, "A5 空表降级：micro 强度返回中性 1.0，不伪造强度");
        // 三态本身（NONE/SHORT/EMPTY 的推导）不属本片改动面，由 P1 的 BiomeAllocationCheck(121) 常驻钉住；
        // 这里只钉"分层接线没有污染账本"：真实配槽后 degraded 必须仍是 NONE。
        SurfaceHarness.recordAllAllocations(SurfaceHarness.prosperityBiomes(), SurfaceHarness.shatteredBiomes());
        check(GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY).degraded()
            == GTSRBiomeAuthority.Degraded.NONE,
            "A5 带尺度接线不得污染 L1 账本（真实配槽后 degraded 仍为 NONE）");
    }

    /** B 组：macro/micro 恒等式与回退位。 */
    private static void groupB() {
        int checked = 0;
        int edgeOnlyDiff = 0;
        int badDiff = 0;
        for (int s = 1; s <= 8; s++) {
            final int macro = s * BiomeZoneSelector.MICRO_CELL_CHUNKS;
            for (int seedIdx = 0; seedIdx < 3; seedIdx++) {
                final long seed = SEEDS[seedIdx] + seedIdx * 7L;
                for (int dx = -70; dx <= 70; dx += 3) {
                    for (int dz = -70; dz <= 70; dz += 3) {
                        final int folded = BiomeZoneSelector
                            .bandIndex(seed, dx, dz, BIOME_COUNT, WEIGHTS, macro, SALT_P);
                        final int scaled = BiomeZoneSelector.select(
                            seed,
                            Math.floorDiv(dx, s),
                            Math.floorDiv(dz, s),
                            BIOME_COUNT,
                            WEIGHTS,
                            BiomeZoneSelector.MICRO_CELL_CHUNKS,
                            SALT_P);
                        final int nativeScale = BiomeZoneSelector
                            .select(seed, dx, dz, BIOME_COUNT, WEIGHTS, macro, SALT_P);
                        if (folded != scaled) {
                            fail("B1a 折算实现与「坐标缩放后走单层 select」不等（s=" + s + " chunk=" + dx + "," + dz
                                + "）——bandIndex 的实现被改道");
                            return;
                        }
                        if (folded != nativeScale) {
                            // 许可差异只有两类：① 边带内 12% 改掷的随机数输入是折算后的坐标；
                            // ② 边带宽度按 s 量化（round(16s×0.12) 与 s×round(16×0.12) 可差 1 格，
                            //    只影响边界那一圈 chunk 在两种口径下"算不算边带"的分类）
                            final boolean edgeNative = BiomeZoneSelector.isEdgeBand(dx, dz, macro);
                            final boolean edgeFolded = BiomeZoneSelector.isEdgeBand(
                                Math.floorDiv(dx, s),
                                Math.floorDiv(dz, s),
                                BiomeZoneSelector.MICRO_CELL_CHUNKS);
                            if (edgeNative || edgeFolded) {
                                edgeOnlyDiff++;
                            } else {
                                badDiff++;
                            }
                        }
                        // 带基准（不含边带改掷）必须逐位相同
                        final int baseFolded = BiomeZoneSelector.zoneOfCell(
                            seed,
                            Math.floorDiv(dx, macro),
                            Math.floorDiv(dz, macro),
                            BIOME_COUNT,
                            WEIGHTS,
                            SALT_P);
                        final boolean edgeAny = BiomeZoneSelector.isEdgeBand(dx, dz, macro)
                            || BiomeZoneSelector.isEdgeBand(
                                Math.floorDiv(dx, s),
                                Math.floorDiv(dz, s),
                                BiomeZoneSelector.MICRO_CELL_CHUNKS);
                        if (!edgeAny && folded != baseFolded) {
                            badDiff++;
                        }
                        checked++;
                    }
                }
            }
        }
        check(badDiff == 0 && checked > 30000, "B1b 带基准身份在折算口径与原生 macro 口径之间逐位相同（穷举 "
            + checked + " 点，非许可差异 " + badDiff + " 点；许可的边带差异 " + edgeOnlyDiff
            + " 点，仅限边带宽度按 s 量化或 12% 改掷输入不同）");
        check(edgeOnlyDiff > 0, "B1c 灵敏度：边带改掷必须真实存在（否则 B1b 可能是「两边都恒真」的假绿）");

        int rb = 0;
        for (int dx = -40; dx <= 40; dx += 7) {
            for (int dz = -40; dz <= 40; dz += 7) {
                final int legacy = BiomeZoneSelector
                    .select(12345L, dx, dz, BIOME_COUNT, WEIGHTS, BiomeZoneSelector.ZONE_CELL_CHUNKS, SALT_P);
                for (final int requested : new int[] { 16, 15, 0, -64 }) {
                    if (BiomeZoneSelector.bandIndex(12345L, dx, dz, BIOME_COUNT, WEIGHTS, requested, SALT_P)
                        != legacy) {
                        fail("B2 回退位不成立 macroCell=" + requested + " chunk=" + dx + "," + dz);
                        return;
                    }
                    rb++;
                }
            }
        }
        check(rb >= 576,
            "B2 macroCell ∈ {16,15,0,-64} 一律逐位回退改造前单层口径（" + rb + " 点）⇒ Config 单值可回退");
        check(BiomeZoneSelector.normalizeMacroCell(100) == 96 && BiomeZoneSelector.normalizeMacroCell(64) == 64
            && BiomeZoneSelector.normalizeMacroCell(1) == 16,
            "B3 normalizeMacroCell：非整数倍向下取整（100→96）、64 保持、小于 micro 回退 16");
    }

    /** C 组：单一真值与源级红线。 */
    private static void groupC(String srcRoot) throws IOException {
        // —— C1：真实链 manager（真实配槽账本 + 真实 def 权重表 + L1 绑定）与城门纯出口逐位同一 ——
        final BiomeGenBase[] pb = SurfaceHarness.prosperityBiomes();
        SurfaceHarness.recordAllAllocations(pb, SurfaceHarness.shatteredBiomes());
        final int[] defWeights = SurfaceHarness.prosperityWeights();
        final GTSRDimensionDef def = SurfaceHarness.def(true, pb, defWeights);
        int c1Chunks = 0;
        int c1Bad = 0;
        for (int si = 0; si < SEEDS.length; si++) {
            final long seed = SEEDS[si];
            final GTSRWorldChunkManager mgr = new GTSRWorldChunkManager(seed, def);
            check(
                mgr.macroBandChunks() == BiomeZoneSelector
                    .normalizeMacroCell(Config.prosperityBiomeMacroBandChunks),
                "C1 manager 必须把 Config 的 macro 带尺度接进来（seed=" + seed + "）");
            for (int dx = 0; dx < 64; dx++) {
                for (int dz = 0; dz < 64; dz++) {
                    final int cx = si * 4096 + dx;
                    final int cz = si * 924816 + dz;
                    c1Chunks++;
                    final int viaManager = mgr.bandRosterIndex(cx, cz);
                    final int viaPure = CityPlanner.bandIndexAt(seed, cx, cz);
                    if (viaManager != viaPure || mgr.biomeAt(cx, cz) != pb[viaPure]) {
                        c1Bad++;
                    }
                }
            }
        }
        check(c1Bad == 0 && c1Chunks >= 16384, "C1 真实链身份（manager/def 权重表/L1 绑定源）与城门纯函数逐位同一（"
            + c1Chunks + " chunk，差异 " + c1Bad + "）⇒ 门与渲染同一入口");

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
        final String hexP = Long.toHexString(SALT_P).toUpperCase(Locale.ROOT);
        final String plannerCode = codeOnly(planner);
        final String wcmCode = codeOnly(wcm);
        check(proxy.contains("0x" + hexP + "L"),
            "C2 CommonProxy dim78 selector 盐字面量必须 == BiomeZoneSelector.ZONE_SALT_PROSPERITY（0x" + hexP
                + "L）；两处不同值即为漂移");
        check(proxy.contains("0x" + Long.toHexString(SALT_S).toUpperCase(Locale.ROOT) + "L"),
            "C2 dim79 selector 盐仍是 dim78 盐 +1（本片不改 dim79 接线）");
        check(!plannerCode.contains("getBiomeGenForCoords"),
            "C2 城门禁 getBiomeGenForCoords（plan §2.1 L6 红线：城中心最远跨 8 chunk 会触发邻 chunk 生成）");
        check(!wcmCode.contains("getBiomeGenForCoords"), "C2 GTSRWorldChunkManager 不得读世界 biome 平面");
        check(!codeOnly(scatter).contains("BiomeZoneSelector.select(")
            && !codeOnly(scatter).contains("zoneOfCell(") && !codeOnly(scatter).contains("bandIndex("),
            "C2 消费方（散布层）不得自己算带——身份只在 H-1 出");
        check(codeOnly(wcm).contains("bandIdentity("),
            "C2 manager 的身份路径必须经 H-1 的 bandIdentity 出口（不得旁路直调算法体）");
        check(codeOnly(planner).contains("BiomeZoneSelector.bandIndex("),
            "C2 城门身份必须走 H-1 bandIndex 出口（而不是自己实现带算法）");
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

    /** D 组：权重守恒（表 3 的断言面）。 */
    private static void groupD() {
        // D0：带基准掷骰本身的份额守恒（一带一票、iid；与 macro 无关，是最硬的一条）
        final long[] iid = bandRollSample(SEEDS.length, BAND_SAMPLE_PER_SEED);
        final long draws = (long)SEEDS.length * BAND_SAMPLE_PER_SEED * BAND_SAMPLE_PER_SEED;
        final double iidTol = Math.max(BAND_TOL_FLOOR_PP, tolPp(draws));
        double iidDev = 0;
        for (int i = 0; i < BIOME_COUNT; i++) {
            iidDev = Math.max(iidDev, Math.abs(100.0D * iid[i] / draws - WEIGHTS[i]));
        }
        check(iidDev <= iidTol, "D0 带基准掷骰份额对 45-30-15-10 的最大偏差 " + pct(iidDev) + "pp ≤ 容差 "
            + pct(iidTol) + "pp（" + draws + " 次带抽样，3σ 或申报下限）⇒ macro 化后带尺度守恒");
        System.out.println("  # D0 带基准掷骰份额(名册序 0..3) = "
            + (100.0D * iid[0] / draws) + "/" + (100.0D * iid[1] / draws) + "/" + (100.0D * iid[2] / draws) + "/"
            + (100.0D * iid[3] / draws) + " 设计 45/30/15/10，最大偏差 " + pct(iidDev) + "pp，容差 " + pct(iidTol)
            + "pp");
        check(iid[0] > 0 && iid[1] > 0 && iid[2] > 0 && iid[3] > 0,
            "D0 四群系带基准均须出现（实测 " + Arrays.toString(iid) + " / " + draws + "）");

        for (final int macro : MACRO_SWEEP) {
            final Measure m = measure(SEEDS.length, DEFAULT_WINDOW, macro);
            final double bandTol = Math.max(BAND_TOL_FLOOR_PP, tolPp(m.bands));
            final double chunkTol = Math.max(CHUNK_TOL_FLOOR_PP, tolPp(m.bands));
            double bandDev = 0;
            double chunkDev = 0;
            for (int i = 0; i < BIOME_COUNT; i++) {
                bandDev = Math.max(bandDev, Math.abs(100.0D * m.bandCounts[i] / m.bands - WEIGHTS[i]));
                chunkDev = Math.max(chunkDev, Math.abs(100.0D * m.chunkCounts[i] / m.chunks - WEIGHTS[i]));
            }
            if (m.bands >= 64) {
                check(bandDev <= bandTol, "D macro=" + macro + " 连续窗带尺度份额偏差 " + pct(bandDev) + "pp ≤ 容差 "
                    + pct(bandTol) + "pp（窗内带数 " + m.bands + "）");
                // chunk 尺度：带内 chunk 完全相关 ⇒ 有效样本数取"窗内实际带数"，不是 chunk 数
                check(chunkDev <= chunkTol, "D macro=" + macro + " chunk 尺度份额偏差 " + pct(chunkDev)
                    + "pp ≤ 容差 " + pct(chunkTol) + "pp（连续窗 " + m.chunks + " chunk / 有效样本 " + m.bands
                    + " 带；按 chunk 数算 σ 是假严格，按带数算才是该 estimand 的正确方差）");
            } else {
                System.out.println("  # 申报（不断言）macro=" + macro + " 连续窗内仅 " + m.bands
                    + " 带（<64）：带尺度偏差 " + pct(bandDev) + "pp、chunk 尺度偏差 " + pct(chunkDev)
                    + "pp —— 该档抽样不足，仅申报；份额守恒由 D0 与更小 macro 档断言");
            }
            check(m.islandWorst <= ISLAND_RATIO_MAX * 100.0D, "D macro=" + macro + " 连贯性：孤岛率最坏 "
                + pct(m.islandWorst / 100.0D) + "% ≤ " + pct(ISLAND_RATIO_MAX * 100.0D) + "%");
        }
    }

    /** 带基准掷骰份额样本（每 seed 一个 n×n 的带索引网格；与 macro 无关，故只跑一次）。 */
    private static long[] bandRollSample(int seeds, int n) {
        final long[] counts = new long[BIOME_COUNT];
        for (int si = 0; si < seeds; si++) {
            final long seed = SEEDS[si % SEEDS.length];
            final int b0 = (int)(seed % 1000L) + si * 10_000;
            for (int bx = 0; bx < n; bx++) {
                for (int bz = 0; bz < n; bz++) {
                    counts[BiomeZoneSelector.zoneOfCell(
                        seed,
                        b0 + bx,
                        b0 + 4096 * si + bz,
                        BIOME_COUNT,
                        WEIGHTS,
                        SALT_P)]++;
                }
            }
        }
        return counts;
    }

    /** E 组：确定性双跑（带划分 + 城布局）。 */
    private static void groupE() {
        final int win = 64;
        for (int si = 0; si < 3; si++) {
            final long seed = SEEDS[si];
            final int x0 = si * 4096;
            final int z0 = si * 924816;
            final String b1 = sha(bandGrid(seed, x0, z0, win));
            final String b2 = sha(bandGrid(seed, x0, z0, win));
            check(b1.equals(b2), "E1 带划分双跑摘要不同 seed=" + seed);
            final String c1 = cityDigest(seed, x0, z0, win);
            final String c2 = cityDigest(seed, x0, z0, win);
            check(c1.equals(c2), "E2 城布局（城门生效后 cellSeed/锚点/半径序列）双跑摘要不同 seed=" + seed);
            System.out.println("  # determinism seed=" + seed + " bandGridSHA=" + b1.substring(0, 16)
                + " cityLayoutSHA=" + c1.substring(0, 16));
        }
    }

    // ══════════════════════════════════ 表 3 ══════════════════════════════════

    private static void printTable(int seeds, int win) {
        System.out.println(
            "# COLUMNS T3 macroCellChunks|seedCount|sampleChunks|nBands|bandShare0Pp|bandShare1Pp|bandShare2Pp"
                + "|bandShare3Pp|bandDevMaxPp|bandTolPp|chunkShare0Pp|chunkShare1Pp|chunkShare2Pp|chunkShare3Pp"
                + "|chunkDevMaxPp|chunkTolPp|islandRatioMaxPp|maxClusterChunks|selected");
        for (final int macro : MACRO_SWEEP) {
            final Measure m = measure(seeds, win, macro);
            double bandDev = 0;
            double chunkDev = 0;
            final StringBuilder sb = new StringBuilder("T3 ");
            sb.append(macro).append('|')
                .append(seeds)
                .append('|')
                .append(m.chunks)
                .append('|')
                .append(m.bands);
            for (int i = 0; i < BIOME_COUNT; i++) {
                final double share = 100.0D * m.bandCounts[i] / m.bands;
                sb.append('|').append(pct(share));
                bandDev = Math.max(bandDev, Math.abs(share - WEIGHTS[i]));
            }
            sb.append('|').append(pct(bandDev)).append('|')
                .append(pct(Math.max(BAND_TOL_FLOOR_PP, tolPp(m.bands))));
            for (int i = 0; i < BIOME_COUNT; i++) {
                final double share = 100.0D * m.chunkCounts[i] / m.chunks;
                sb.append('|').append(pct(share));
                chunkDev = Math.max(chunkDev, Math.abs(share - WEIGHTS[i]));
            }
            sb.append('|').append(pct(chunkDev)).append('|')
                .append(pct(Math.max(CHUNK_TOL_FLOOR_PP, tolPp(m.chunks)))).append('|').append(pct(m.islandWorst))
                .append('|').append(m.clusterDominantMin).append('|')
                .append(macro == Config.prosperityBiomeMacroBandChunks ? "SELECTED" : "-");
            System.out.println(sb);
        }
    }

    /** 一次测量（带尺度/ chunk 尺度份额 + 连贯性）。 */
    private static final class Measure {
        final long[] bandCounts = new long[BIOME_COUNT];
        final long[] chunkCounts = new long[BIOME_COUNT];
        long bands;
        long chunks;
        double islandWorst;
        int clusterDominantMin = Integer.MAX_VALUE;
    }

    private static Measure measure(int seeds, int win, int macro) {
        final Measure m = new Measure();
        final int perSeed = (int)Math.ceil(win / (double)macro);
        for (int si = 0; si < seeds; si++) {
            final long seed = SEEDS[si % SEEDS.length];
            final int x0 = si * 4096;
            final int z0 = si * 924816;
            final int[] grid = bandGridAt(seed, x0, z0, win, macro);
            for (final int z : grid) {
                m.chunkCounts[z]++;
            }
            m.chunks += (long)win * win;
            m.islandWorst = Math.max(m.islandWorst, islandRatio(grid, win) * 100.0D);
            m.clusterDominantMin = Math.min(m.clusterDominantMin, largestClusters(grid, win)[dominantZone()]);
            for (int bx = 0; bx < perSeed; bx++) {
                for (int bz = 0; bz < perSeed; bz++) {
                    final int cx = x0 + bx * macro;
                    final int cz = z0 + bz * macro;
                    m.bandCounts[BiomeZoneSelector.zoneOfCell(
                        seed,
                        Math.floorDiv(cx, macro),
                        Math.floorDiv(cz, macro),
                        BIOME_COUNT,
                        WEIGHTS,
                        SALT_P)]++;
                    m.bands++;
                }
            }
        }
        return m;
    }

    /** 二项容差（百分点）：3σ，σ = sqrt(p(1-p)/n)，p 取权重表中各项的最不利者。 */
    private static double tolPp(long n) {
        if (n <= 0) {
            return 100.0D;
        }
        double worst = 0;
        for (final int w : WEIGHTS) {
            final double p = w / (double)TOTAL_WEIGHT;
            worst = Math.max(worst, Math.sqrt(p * (1 - p) / n) * 100.0D);
        }
        return worst * SIGMA_TOL;
    }

    // ══════════════════════════════════ 工具方法 ══════════════════════════════════

    private static int dominantZone() {
        int best = 0;
        for (int i = 1; i < BIOME_COUNT; i++) {
            if (WEIGHTS[i] > WEIGHTS[best]) {
                best = i;
            }
        }
        return best;
    }

    /** 连续窗的身份网格（默认档：读 Config 当前带尺度，即生产消费方所见）。 */
    private static int[] bandGrid(long seed, int cx0, int cz0, int win) {
        return bandGridAt(seed, cx0, cz0, win, Config.prosperityBiomeMacroBandChunks);
    }

    /**
     * 指定带尺度的连续窗身份网格。
     * <p>
     * 走 {@link CityPlanner#bandIndexAt}（H-1 纯出口，城门/渲染共用），只把
     * {@link Config#prosperityBiomeMacroBandChunks} 临时设为待测档并在 finally 里复位——
     * <b>不自己实现带算法</b>，故本表与生产行为同一函数。
     */
    private static int[] bandGridAt(long seed, int cx0, int cz0, int win, int macro) {
        final int saved = Config.prosperityBiomeMacroBandChunks;
        try {
            Config.prosperityBiomeMacroBandChunks = macro;
            final int[] grid = new int[win * win];
            for (int dz = 0; dz < win; dz++) {
                for (int dx = 0; dx < win; dx++) {
                    grid[dx + dz * win] = CityPlanner.bandIndexAt(seed, cx0 + dx, cz0 + dz);
                }
            }
            return grid;
        } finally {
            Config.prosperityBiomeMacroBandChunks = saved;
        }
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

    /** 边带宽度（chunk）= round(cell × 0.12)，至少 1（与 BiomeZoneSelector 内部同式，供核面积计算）。 */
    private static int bandChunks(int cell) {
        return Math.max(1, Math.round(cell * BiomeZoneSelector.EDGE_BAND_FRACTION));
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

    /** 列联表互信息（nat）——"micro 强度档与 macro 身份独立"的量化口径。 */
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

    private static void fail(String msg) {
        FAILURES.add(msg);
        System.out.println("  FAIL " + msg);
    }

    private static void finish() {
        if (!FAILURES.isEmpty()) {
            System.out.println("BIOME BAND HIERARCHY FAIL: " + FAILURES.size() + " assertion(s) failed, passed="
                + (assertions - FAILURES.size()));
            System.exit(1);
        }
        System.out.println("BIOME BAND HIERARCHY PASS: assertions=" + assertions
            + " — 分层职责/恒等式回退位/单一真值/权重守恒/确定性 all green");
    }
}
