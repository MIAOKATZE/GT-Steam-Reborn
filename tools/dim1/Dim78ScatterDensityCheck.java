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

    /** 一行参数 = 扫描表的一行。 */
    public static final class Row {

        public final String label;
        public final int contours;
        public final int blocks;
        public final int attempts;
        public final Boolean vertical;
        public final int windowCap;

        public Row(String label, int contours, int blocks, int attempts, Boolean vertical, int windowCap) {
            this.label = label;
            this.contours = contours;
            this.blocks = blocks;
            this.attempts = attempts;
            this.vertical = vertical;
            this.windowCap = windowCap;
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
        row("DEFAULT档(K8+摘+落块24+窗2)", 8, 24, 64, Boolean.FALSE, 2) };

    private static Row row(String label, int k, int b, int a, Boolean v, int cap) {
        return new Row(label, k, b, a, v, cap);
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
    }

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
                final GTSRChunkProviderBase provP = new ChunkProviderProsperityRuins(SurfaceHarness.mockWorld(), seed);
                final GTSRWorldChunkManager mgrP = new GTSRWorldChunkManager(seed, defP);
                for (int r = 0; r < regions; r++) {
                    // 区原点两轴都按 16 对齐 ⇒ 每区 == 恰好一个 H-2 窗；并按 3/5 个群系带 cell
                    // （ZONE_CELL_CHUNKS=16）跳开，避免整样本塌进同一群系带（P4 实测过的坑）
                    final int cx0 = r * axis * 3 + si * 4096;
                    final int cz0 = r * axis * 5 + si * 924816;
                    final Block[] grid = new Block[side * side * 256];
                    materialize(provP, mgrP, seed, cx0, cz0, grid, scratch, scratchMeta);
                    final GridWorld world = world(grid, cx0, cz0, seed, side);
                    runPreScatterStages(world, seed, cx0, cz0);
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
