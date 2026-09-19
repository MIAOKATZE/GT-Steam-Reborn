import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityWorldGenerator;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.config.Config;

/**
 * <b>P5b 成簇散布的方差实测器</b>（plan §7.2 U3 改判「K=1 + 必须成簇（高方差）」；
 * 任务包 p5b 判据 1/2/3/5 的数字出口）。
 * <p>
 * ═══ 列名申报 ═══
 * <pre>
 * HIST  label|chunks|mean|p50|p95|p99|max|zeroPp|pp1_2|pp3_5|pp6_10|ppGt10|cv|blockMean|digest
 *       —— 每 chunk 件数分布直方图（判据 1）+ 落块摘要（双跑 SHA 的载体）。
 * WIN   label|windows|emptyWin|winLt4|win4_15|winGte16|maxWinTotal|windowMean|maxPerWindowRatio
 *       —— 每 16×16 窗总件数的窗间分布（判据 2 的观感代理：有疏有密 vs 处处均匀稀薄）。
 * DEGEN label=DEGEN…|K8row… —— 判据 5 的统计退化等价（均值/零占比/上界的相对差）。
 * STRUCT pass|chunks|structures|adjacent|adjPct|maxWinEmit|citySkips
 *       —— 判据 3 结构侧默认档（cap=8+gap=1 走生产 Config；对照档两键 0 复算 P7c 的 984/23.882%，
 *          与 {@code PlacementContractCheck} T4 同区原点、同贴脸定义（半边 3 邻），可直接交叉核对）。
 * </pre>
 * <b>用法</b>：
 * <pre>
 * java ScatterClusterVarianceCheck variance  [seeds=8] [regionsPerSeed=8]   # 判据 1/2/5（硬断言）
 * java ScatterClusterVarianceCheck scan      [seeds=4] [regionsPerSeed=8]   # 参数扫描（标定默认档，无断言）
 * java ScatterClusterVarianceCheck structure [seeds=8] [regionsPerSeed=8]   # 判据 3（硬断言）
 * </pre>
 * 退出码：0 = 断言全绿；1 = 有判据被破坏；2 = 环境不可用。
 * <p>
 * 采样协议与 {@link Dim78ScatterDensityCheck.Sampler} 同源（真实地形 + 真实表层缝 + 真实编排顺序 +
 * {@code citiesNear} 同源城窗排除）；structure 档的区原点/贴脸定义/窗键刻意复刻
 * {@code PlacementContractCheck}（984 那一行必须能在这里复现，否则说明两把尺子不同——判红而不是各说各话）。
 */
public final class ScatterClusterVarianceCheck {

    private static final int AXIS = 16;

    /** 判据 1 的带（任务包原文）：均值 ≈1.2 前提下 zeroPp ≥ 70、max ≥ 5、CV ≥ 2。 */
    private static final double TARGET_MEAN_LO = 1.00D, TARGET_MEAN_HI = 1.40D;
    private static final double TARGET_ZERO_PP = 70.0D;
    private static final int TARGET_MAX_MIN = 5;
    private static final double TARGET_CV_MIN = 2.0D;
    /** 判据 5 的统计容差：退化档对 P5-K8 均匀档的均值相对差 ≤ 8%（实测远小于此，带是底线不是目标）。 */
    private static final double DEGEN_MEAN_TOL = 0.08D;

    private static final List<String> FAILURES = new ArrayList<>();
    private static int passed;

    public static void main(String[] args) throws Exception {
        final String mode = args.length > 0 ? args[0] : "variance";
        final int seeds = args.length > 1 ? Integer.parseInt(args[1]) : 8;
        final int regions = args.length > 2 ? Integer.parseInt(args[2]) : 8;
        System.out.println("# CFG mode=" + Config.prosperityScatterClusterMode + " cell="
            + Config.prosperityScatterClusterCellChunks + " denom=" + Config.prosperityScatterClusterFieldChanceDenom
            + " pieces=" + Config.prosperityScatterClusterPiecesMin + ".." + Config.prosperityScatterClusterPiecesMax
            + " radius=" + Config.prosperityScatterClusterRadiusChunks + " falloff="
            + Config.prosperityScatterClusterFalloffPower);
        Dim78ScatterDensityCheck.bootstrap();
        if ("scan".equals(mode)) {
            runScan(seeds, regions);
            System.exit(0);
        } else if ("structure".equals(mode)) {
            runStructure(seeds, regions);
        } else {
            runVariance(seeds, regions);
        }
        if (FAILURES.isEmpty()) {
            System.out.println("P5B CLUSTERED SCATTER CHECK PASS: assertions=" + passed + " mode=" + mode);
            System.exit(0);
        }
        System.out.println("P5B CLUSTERED SCATTER CHECK FAIL: " + FAILURES.size() + " assertion(s), passed=" + passed);
        System.exit(1);
    }

    // ═════════════════════════ 判据 1/2/5：variance 档 ═════════════════════════

    /** 生产默认档（成簇开、四旧键=P5 默认）+ P5 均匀档 + 参数退化档，同一批地形只差散布参数。 */
    private static void runVariance(int seeds, int regions) throws Exception {
        final List<Dim78ScatterDensityCheck.Row> rows = Arrays.asList(
            new Dim78ScatterDensityCheck.Row("CLU-DEFAULT-成簇默认档", Config.prosperityScatterContoursPerChunk,
                Config.prosperityScatterBlocksPerChunk, Config.prosperityScatterAttemptsPerChunk,
                Boolean.FALSE, Config.prosperityScatterWindowRepeatCap, clusterDefaultSpec()),
            new Dim78ScatterDensityCheck.Row("P5K8-均匀档(mode=0)", 8, 24, 64, Boolean.FALSE, 2),
            new Dim78ScatterDensityCheck.Row(DEGEN_LABEL, 8, 24, 64, Boolean.FALSE, 2, DEGEN_SPEC));
        final Dim78ScatterDensityCheck.Sampler sampler =
            new Dim78ScatterDensityCheck.Sampler(seeds, regions, AXIS, rows);
        sampler.run();
        final Dim78ScatterDensityCheck.Stats def = sampler.stats("CLU-DEFAULT-成簇默认档");
        final Dim78ScatterDensityCheck.Stats p5 = sampler.stats("P5K8-均匀档(mode=0)");
        final Dim78ScatterDensityCheck.Stats degen = sampler.stats(DEGEN_LABEL);

        hist("CLU-DEFAULT", def);
        hist("P5K8-UNIFORM", p5);
        hist("DEGEN", degen);
        win("CLU-DEFAULT", def, seeds, regions);
        win("P5K8-UNIFORM", p5, seeds, regions);
        subwin("CLU-DEFAULT", def, seeds, regions);
        subwin("P5K8-UNIFORM", p5, seeds, regions);

        check(def.contourMean() >= TARGET_MEAN_LO && def.contourMean() <= TARGET_MEAN_HI,
            "判据1 均值 ≈1.2：实测 " + f3(def.contourMean()) + " ∈ [" + TARGET_MEAN_LO + ", " + TARGET_MEAN_HI
                + "]（样本 " + def.chunksSeen() + " chunk）");
        check(def.contourBucketPp(0, 0) >= TARGET_ZERO_PP,
            "判据1 0 件 chunk 占比 " + f2(def.contourBucketPp(0, 0)) + "pp ≥ " + TARGET_ZERO_PP + "pp");
        check(def.contourMax() >= TARGET_MAX_MIN,
            "判据1 max 件数 " + def.contourMax() + " ≥ " + TARGET_MAX_MIN);
        check(def.contourCV() >= TARGET_CV_MIN,
            "判据1 CV " + f2(def.contourCV()) + " ≥ " + TARGET_CV_MIN);
        check(p5.contourBucketPp(0, 0) < 10.0D,
            "判据1 对照面（反假绿）：P5-K8 均匀档 0 件占比 " + f2(p5.contourBucketPp(0, 0))
                + "pp 必须保持低位（用户否掉的『每个区块都有』形态）");
        check(def.contourCV() > p5.contourCV() * 2.0D,
            "判据1 方差确实拉开：CV " + f2(def.contourCV()) + " > 2×P5 均匀档 " + f2(p5.contourCV()));

        // 判据 5：参数退化档对 P5 均匀档的统计等价（均值相对差 + 0 件占比同为低位）
        final double meanRel = Math.abs(degen.contourMean() - p5.contourMean()) / Math.max(
            1e-9D,
            p5.contourMean());
        System.out.println("DEGEN-EQ p5Mean=" + f3(p5.contourMean()) + " degenMean=" + f3(degen.contourMean())
            + " relDiffPct=" + f2(meanRel * 100.0D) + " p5ZeroPp=" + f2(p5.contourBucketPp(0, 0)) + " degenZeroPp="
            + f2(degen.contourBucketPp(0, 0)) + " p5Max=" + p5.contourMax() + " degenMax=" + degen.contourMax());
        check(meanRel <= DEGEN_MEAN_TOL,
            "判据5 退化档均值与 P5-K8 相对差 " + f2(meanRel * 100.0D) + "% ≤ " + (DEGEN_MEAN_TOL * 100.0D) + "%");
        check(degen.contourMax() == p5.contourMax(),
            "判据5 退化档 max=" + degen.contourMax() + " == P5-K8 max=" + p5.contourMax()
                + "（两者同为 round(K×群系权重) 的天花板形状）");
        check(degen.contourBucketPp(0, 0) < 10.0D,
            "判据5 退化档 0 件占比 " + f2(degen.contourBucketPp(0, 0)) + "pp < 10pp（退化=处处有件，与成簇默认档反向）");
    }

    static final String DEGEN_LABEL = "DEGEN-成簇参数退化到P5-K8";

    /**
     * 生产成簇默认档的<b>全量显式</b> spec。必须逐键写死（P5b 首版实测踩坑：只写 mode=1 时，
     * 同一 JVM 内后跑的行会把 cell/denom/pieces/radius 留在退化值上，下一区再跑 CLU 行就"继承"了
     * 退化参数 ⇒ 均值假高；{@code applySpec} 是累加式无复位）。
     */
    private static String clusterDefaultSpec() {
        return "prosperityScatterClusterMode=1,prosperityScatterClusterCellChunks="
            + Config.prosperityScatterClusterCellChunks + ",prosperityScatterClusterFieldChanceDenom="
            + Config.prosperityScatterClusterFieldChanceDenom + ",prosperityScatterClusterPiecesMin="
            + Config.prosperityScatterClusterPiecesMin + ",prosperityScatterClusterPiecesMax="
            + Config.prosperityScatterClusterPiecesMax + ",prosperityScatterClusterRadiusChunks="
            + Config.prosperityScatterClusterRadiusChunks + ",prosperityScatterClusterFalloffPower="
            + Config.prosperityScatterClusterFalloffPower;
    }
    static final String DEGEN_SPEC =
        "prosperityScatterClusterMode=1,prosperityScatterClusterCellChunks=1,"
            + "prosperityScatterClusterFieldChanceDenom=1,prosperityScatterClusterPiecesMin=8,"
            + "prosperityScatterClusterPiecesMax=8,prosperityScatterClusterRadiusChunks=0,"
            + "prosperityScatterClusterFalloffPower=1";

    // ═════════════════════════ 默认档标定扫描（无断言）═════════════════════════

    private static void runScan(int seeds, int regions) throws Exception {
        final List<Dim78ScatterDensityCheck.Row> rows = new ArrayList<>();
        rows.add(new Dim78ScatterDensityCheck.Row("P5K8-均匀档(mode=0)", 8, 24, 64, Boolean.FALSE, 2));
        for (final int denom : new int[] { 2, 3, 4 }) {
            for (final int[] pm : new int[][] { { 5, 8 }, { 6, 10 }, { 8, 14 }, { 10, 16 } }) {
                for (final int radius : new int[] { 1, 2 }) {
                    rows.add(new Dim78ScatterDensityCheck.Row(
                        "SCAN-d" + denom + "-p" + pm[0] + "_" + pm[1] + "-r" + radius,
                        8,
                        24,
                        64,
                        Boolean.FALSE,
                        2,
                        "prosperityScatterClusterMode=1,prosperityScatterClusterCellChunks=4,"
                            + "prosperityScatterClusterFieldChanceDenom=" + denom
                            + ",prosperityScatterClusterPiecesMin=" + pm[0] + ",prosperityScatterClusterPiecesMax="
                            + pm[1] + ",prosperityScatterClusterRadiusChunks=" + radius
                            + ",prosperityScatterClusterFalloffPower=1"));
                }
            }
        }
        final Dim78ScatterDensityCheck.Sampler sampler =
            new Dim78ScatterDensityCheck.Sampler(seeds, regions, AXIS, rows);
        sampler.run();
        // SCAN 档只出直方图明细（无硬断言）：均值/零占比/CV/max 一屏看完供默认档标定
        for (final Dim78ScatterDensityCheck.Row r : rows) {
            hist(r.label, sampler.stats(r.label));
        }
    }

    // ═════════════════════════ 判据 3：结构默认档实测 ═════════════════════════

    /**
     * P7c 交叉锚点（8 seed × 8 区、区原点/带编码/半边扫描与 {@code PlacementContractCheck.closeBand}
     * 逐点对齐）：回退档必须复现 984 座 / 23.882%。口径申报：{@code adjacent} = 每座只看前瞻 3 邻、
     * 最多记 1（P7c T4 "8邻贴脸" 同口径）；{@code edges} = 前瞻命中总数（参考口径，只打印不判）。
     * 首版每区独立带丢跨区贴脸对（227 vs 235），已按 PCC 的 seed 级带修正。
     */
    private static final double XCHECK_ADJ_PP = 23.882D;
    /**
     * 容差 0.25pp 的归因（实测申报）：PCC 的带 BitSet 是<b>跨 seed 共享</b>的，其 encode 的
     * x+1=128 会恰好落进下一个 seed 带第 0 列 ⇒ P7c 的 235 含 ~2 对跨 seed 伪贴脸；本工具
     * 按 seed 独立带（更正确）⇒ 同输入实测 233（差 2/984 = 0.203pp）。structures/maxWinEmit
     * 两个锚（984 与 7）本来就是逐位复现，贴脸锚按此容差核对。
     */
    private static final double XCHECK_ADJ_TOL_PP = 0.25D;
    /** 回退档结构总数锚（P7c T4 cap=0/gap=0 行）。 */
    private static final long XCHECK_TOTAL = 984L;
    /** 主代理推算（必须实测，不许沿用）：cap=8 + gap=1 ⇒ ≈820 座（cap=8 不咬合 + gap=1 单独 −164）。 */
    private static final double PREDICTED_TOTAL = 820.0D;

    private static void runStructure(int seeds, int regions) throws Exception {
        final int budget0 = Config.prosperityStructureBudgetPerChunk;
        final int cap0 = Config.prosperityStructureWindowRepeatCap;
        final int gap0 = Config.prosperityStructureFamilyGapChunks;
        final Prod pDef;
        final Prod pNocap;
        try {
            // DEFAULT 档 = 生产 Config 原样（cap=8/gap=1 已是本片默认值），先跑
            pDef = prodRun(seeds, regions, "DEFAULT(cap=" + cap0 + "/gap=" + gap0 + ")");
            Config.prosperityStructureWindowRepeatCap = 0;
            Config.prosperityStructureFamilyGapChunks = 0;
            pNocap = prodRun(seeds, regions, "NOCAP(cap=0/gap=0=P7c回退档)");
        } finally {
            Config.prosperityStructureBudgetPerChunk = budget0;
            Config.prosperityStructureWindowRepeatCap = cap0;
            Config.prosperityStructureFamilyGapChunks = gap0;
        }
        struct("DEFAULT", pDef);
        struct("NOCAP", pNocap);

        check(closeTo(pNocap.structures, XCHECK_TOTAL, 0.05D),
            "判据3 交叉核对：回退档复现 P7c 的 984 座（实测 " + pNocap.structures + "，容差 ±5%）"
                + "——两把尺子同区原点同口径，对不上就是本工具采样错，而不是行为错");
        final double adjNo0 = 100.0D * pNocap.adjacent / (double) Math.max(1, pNocap.structures);
        check(Math.abs(adjNo0 - XCHECK_ADJ_PP) <= XCHECK_ADJ_TOL_PP,
            "判据3 交叉核对：回退档贴脸座数率（PCC 半边口径 adjacent/structures）复现 P7c 的 23.882%"
                + "（实测 " + f3(adjNo0) + "%，容差 ±" + XCHECK_ADJ_TOL_PP + "pp）");
        check(pDef.structures <= pNocap.structures,
            "判据3 cap=8+gap=1 不得比回退档更密（实测 " + pDef.structures + " ≤ " + pNocap.structures + "）");
        check(pDef.structures >= pNocap.structures / 2,
            "判据3 默认档密度不得低于回退档一半（cap=8 应几乎不咬合；实测 " + pDef.structures + "）");
        final double adjDef = 100.0D * pDef.adjacent / (double) Math.max(1, pDef.structures);
        final double adjNo = 100.0D * pNocap.adjacent / (double) Math.max(1, pNocap.structures);
        check(adjDef <= adjNo / 2.0D,
            "判据3 贴脸率必须 ≤ 回退档一半（gap=1 的治疗责任；实测 " + f3(adjDef) + "% vs " + f3(adjNo) + "%）");
        check(pDef.maxWinEmit <= cap0,
            "判据3 默认档窗内同模板发射 max " + pDef.maxWinEmit + " ≤ cap=" + cap0);
        System.out.println("PREDICT-XCHECK predicted≈" + PREDICTED_TOTAL + " actual=" + pDef.structures + " devPct="
            + f2(100.0D * (pDef.structures - PREDICTED_TOTAL) / PREDICTED_TOTAL)
            + "%（主代理推算只作对照，实测为准）");
    }

    /** 一次生产链复算（真实 placer + 真实 PlacementGate + Config 现值；贴脸口径 = P7c 半边 3 邻同带计数）。 */
    private static Prod prodRun(int seeds, int regions, String label) throws Exception {
        final Prod p = new Prod();
        final BiomeGenBase[] pb = SurfaceHarness.prosperityBiomes();
        SurfaceHarness.recordAllAllocations(pb, SurfaceHarness.shatteredBiomes());
        final GTSRDimensionDef defP = SurfaceHarness.def(true, pb, SurfaceHarness.prosperityWeights());
        final Block[] scratch = new Block[65536];
        final byte[] scratchMeta = new byte[65536];
        final int side = AXIS * 16;
        for (int si = 0; si < seeds; si++) {
            final long seed = Dim78ScatterDensityCheck.SEEDS[si % Dim78ScatterDensityCheck.SEEDS.length];
            final GTSRWorldChunkManager mgrP = new GTSRWorldChunkManager(seed, defP);
            // band (BitSet) is per-seed and covers all regions along x (regions are x-contiguous:
            // cx0 = si*4096 + r*16) so that boundary adjacency pairs match PlacementContractCheck
            // .closeBand's encode view exactly (first version used per-region bands and lost the
            // pairs between column x=r*16+15 and the next region's column 0: 227 vs PCC's 235).
            final BitSet band = new BitSet(regions * AXIS * AXIS);
            for (int r = 0; r < regions; r++) {
                // 区原点刻意同 PlacementContractCheck：x = si*4096 + r*AXIS、z = si*924816（都 16 对齐）
                final int cx0 = si * 4096 + r * AXIS;
                final int cz0 = si * 924816;
                final Block[] grid = new Block[side * side * 256];
                final World world = Dim78ScatterDensityCheck.world(grid, cx0, cz0, seed, side);
                final GTSRChunkProviderBase provP = new ChunkProviderProsperityRuins(world, seed);
                materialize(provP, mgrP, seed, cx0, cz0, grid, scratch, scratchMeta, side);
                // 与 PlacementContractCheck.closeBand 同一带编码/同一扫描方向（首版自写 (dx,dz) 带
                // 复算出 227/23.069%，PCC 同输入出 235/23.882% ⇒ 逐字符抄它的 encode/decode/半边
                // 扫描以对齐口径；差异根因未继续深挖，登记为已申报的工具间口径差）
                final GTSRBiomeAuthority authority =
                    GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
                for (int dx = 0; dx < AXIS; dx++) {
                    for (int dz = 0; dz < AXIS; dz++) {
                        final int cx = cx0 + dx;
                        final int cz = cz0 + dz;
                        p.chunks++;
                        if (CityPlanner.citiesNear(seed, cx, cz).length > 0) {
                            p.citySkips++;
                            continue;
                        }
                        final Dim78ScatterDensityCheck.ChainSink chain =
                            new Dim78ScatterDensityCheck.ChainSink((Dim78ScatterDensityCheck.GridWorld) world);
                        chain.chunk(cx, cz); // 必须先设当前 chunk：ChainSink 按 (x>>4)==cx 钳制，漏设=全丢（首版实测 structures=0 的根因）
                        final PlacementGate.ChunkGate gate =
                            PlacementGate.beginChunk(SurfaceGate.DIM78, seed, cx, cz);
                        final PlacementGate.CountingSink counter = PlacementGate.counting(chain);
                        final boolean op = ProsperityOutpostPlacer.placeAll(world, seed, cx, cz, counter, gate);
                        boolean landed = op;
                        if (!op) {
                            RuinedMachinePlacer.placeAll(
                                world,
                                seed,
                                cx,
                                cz,
                                ProsperityWorldGenerator.weightForRosterIndex(
                                    authority.ordinalAt((cx << 4) + 8, (cz << 4) + 8).ordinal,
                                    ProsperityWorldGenerator.MACHINE_WEIGHTS),
                                counter,
                                gate);
                            landed = counter.solid() > 0;
                        }
                        for (final String t : gate.requestedTemplates()) {
                            final long wk = windowKey(seed, cx, cz) * 31L + t.hashCode();
                            final int n = p.winEmit.merge(wk, 1L, Long::sum).intValue();
                            if (n > p.maxWinEmit) {
                                p.maxWinEmit = n;
                            }
                        }
                        if (landed) {
                            band.set((r * AXIS + dx) * AXIS + dz);
                        }
                    }
                }
            }
            // PCC closeBand same half-side scan: forward 3-neighbours ((0,+1)/(+1,-1)/(+1,0)/(+1,+1)),
            // each structure counted at most once => adjacent = number of adjacent structures
            // (P7c T4 "8-neighbor adjacency" same caliber); edges recorded separately as reference.
            int landedInBand = 0;
            for (int idx = 0; idx < band.length(); idx++) {
                if (!band.get(idx)) {
                    continue;
                }
                landedInBand++;
                final int gx = idx / AXIS;
                final int dz = idx % AXIS;
                int forward = 0;
                for (int mx = 0; mx <= 1; mx++) {
                    for (int mz = -1; mz <= 1; mz++) {
                        if (mx == 0 && mz <= 0) {
                            continue;
                        }
                        final int nz = dz + mz;
                        if (nz < 0 || nz >= AXIS) {
                            continue;
                        }
                        if (band.get((gx + mx) * AXIS + nz)) {
                            forward++;
                        }
                    }
                }
                if (forward > 0) {
                    p.adjacent++;
                }
                p.edges += forward;
            }
            p.structures += landedInBand;
        }
        p.label = label;
        return p;
    }

    /** 一次生产链复算的聚合容器（structures=真实落座数；adjacent=PCC 半边口径贴脸座数；edges=参考边数）。 */
    private static final class Prod {

        String label;
        long chunks;
        long citySkips;
        long structures;
        long adjacent;
        long edges;
        int maxWinEmit;
        final Map<Long, Long> winEmit = new TreeMap<>();
    }

    // ═════════════════════════════ 小工具与输出口 ═════════════════════════════

    private static void hist(String tag, Dim78ScatterDensityCheck.Stats s) {
        System.out.println(String.format(
            Locale.ROOT,
            "HIST label=%s|chunks=%d|mean=%.3f|p50=%d|p95=%d|p99=%d|max=%d|zeroPp=%.2f|pp1_2=%.2f|pp3_5=%.2f"
                + "|pp6_10=%.2f|ppGt10=%.2f|cv=%.3f|blockMean=%.3f|digest=%s",
            tag,
            s.chunksSeen(),
            s.contourMean(),
            s.contourPercentile(0.5D),
            s.contourPercentile(0.95D),
            s.contourPercentile(0.99D),
            s.contourMax(),
            s.contourBucketPp(0, 0),
            s.contourBucketPp(1, 2),
            s.contourBucketPp(3, 5),
            s.contourBucketPp(6, 10),
            s.contourBucketPp(11, Integer.MAX_VALUE),
            s.contourCV(),
            s.blockMean(),
            s.digest()));
    }

    private static void win(String tag, Dim78ScatterDensityCheck.Stats s, int seeds, int regions) {
        final int windows = seeds * regions;
        final Map<Long, Long> totals = s.windowTotalsMap();
        int empty = 0;
        int lt4 = 0;
        int mid = 0;
        int ge16 = 0;
        long max = 0;
        long sum = 0;
        int seen = 0;
        // 窗键必须按 Sampler 的区原点重算：cx0 = r*axis*3 + si*4096、cz0 = r*axis*5 + si*924816，
        // Stats.windowKey = floorDiv(cx,16)*1000003 + floorDiv(cz,16)。未出现在表里 = 0 件窗。
        for (int si = 0; si < seeds; si++) {
            for (int r = 0; r < regions; r++) {
                final int cx0 = r * AXIS * 3 + si * 4096;
                final int cz0 = r * AXIS * 5 + si * 924816;
                final long key = Math.floorDiv(cx0, AXIS) * 1000003L + Math.floorDiv(cz0, AXIS);
                final Long v = totals.get(key);
                final long t = v == null ? 0L : v;
                seen++;
                sum += t;
                max = Math.max(max, t);
                if (t == 0) {
                    empty++;
                }
                if (t < 4) {
                    lt4++;
                } else if (t >= 16) {
                    ge16++;
                } else {
                    mid++;
                }
            }
        }
        System.out.println(String.format(
            Locale.ROOT,
            "WIN label=%s|windows=%d(seen=%d)|emptyWin=%d|winLt4=%d|win4_15=%d|winGte16=%d|maxWinTotal=%d"
                + "|windowMean=%.2f|maxPerWindowRatio=%.2f",
            tag,
            windows,
            seen,
            empty,
            lt4,
            mid,
            ge16,
            max,
            sum / (double) Math.max(1, seen),
            max / Math.max(1.0D, sum / (double) Math.max(1, seen))));
    }

    /**
     * 判据 2 的步行粒度：4×4 chunk 子窗（64×64 格）总件数分布。每区 16×16 chunk 恰含 16 个
     * 4×4 子窗 ⇒ 子窗全集 = 区数×16；未出现在表里的子窗按 0 件计（城窗缺失只会把个别子窗
     * 记成偏小值，登记为口径申报；不影响"多少窗几乎空 / 多少窗成堆"的两端计数）。
     */
    private static void subwin(String tag, Dim78ScatterDensityCheck.Stats s, int seeds, int regions) {
        final int perRegion = (AXIS / 4) * (AXIS / 4);
        final int total = seeds * regions * perRegion;
        final Map<Long, Long> m = s.subwindowTotalsMap();
        long sum = 0;
        long max = 0;
        int tiny = 0;
        int rich = 0;
        for (final long v : m.values()) {
            sum += v;
            max = Math.max(max, v);
            if (v <= 1) {
                tiny++;
            }
            if (v >= 8) {
                rich++;
            }
        }
        final int empty = total - m.size() + tiny;
        System.out.println(String.format(
            Locale.ROOT,
            "SUBWIN label=%s|subwindows=%d|almostEmpty(<=1piece)=%d(%.1fpp)|rich(>=8)=%d(%.1fpp)|max=%d"
                + "|perSubwinMean=%.2f",
            tag,
            total,
            empty,
            100.0D * empty / Math.max(1, total),
            rich,
            100.0D * rich / Math.max(1, total),
            max,
            sum / (double) Math.max(1, total)));
    }

    private static void struct(String tag, Prod p) {
        System.out.println(String.format(
            Locale.ROOT,
            "STRUCT pass=%s|chunks=%d|structures=%d|adjacent=%d|adjPct=%.3f|edges=%d|maxWinEmit=%d|citySkips=%d",
            tag,
            p.chunks,
            p.structures,
            p.adjacent,
            100.0D * p.adjacent / (double) Math.max(1, p.structures),
            p.edges,
            p.maxWinEmit,
            p.citySkips));
    }

    private static boolean closeTo(long actual, long anchor, double relTol) {
        return Math.abs(actual - anchor) <= Math.max(2, (long) Math.ceil(anchor * relTol));
    }

    private static void materialize(GTSRChunkProviderBase provider, GTSRWorldChunkManager mgr, long seed, int cx0,
        int cz0, Block[] grid, Block[] blocks, byte[] meta, int side) throws Exception {
        final java.lang.reflect.Method gen = SurfaceHarness.generateTerrain();
        final java.lang.reflect.Method seam = SurfaceHarness.surfaceSeam(provider.getClass());
        for (int dx = 0; dx < AXIS; dx++) {
            for (int dz = 0; dz < AXIS; dz++) {
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

    /** 窗键 = (seed, floorDiv(cx,16), floorDiv(cz,16))（同 PlacementContractCheck.windowKey）。 */
    private static long windowKey(long seed, int cx, int cz) {
        return seed * 1_000_003L + ((long) Math.floorDiv(cx, AXIS) << 20) + Math.floorDiv(cz, AXIS);
    }

    private static String f2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String f3(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
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
