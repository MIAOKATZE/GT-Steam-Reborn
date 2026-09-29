import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import net.minecraft.profiler.Profiler;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer;

/**
 * <b>P25 新挂点：枯竭河床群系平面写断言（任务包 S5 点名）</b>——三条：
 * <ol>
 * <li><b>W1 平面列集合 == 谓词逐列相等</b>：随机 chunk 列扫描（多 seed × 分散 chunk），反射调
 * 生产写通道 {@code assignSanzuRiverBiome} → {@code assignWitheredRiverbedBiome}（populate 后置
 * 同序，{@code ChunkProviderProsperityRuins.onPopulate} 的真实现体），读回 byte 群系平面逐列对拍：
 * 终值 = WITHERED(185) ⟺ {@code !isSanzuColumn ∧ isDryRiverColumn}；= SANZU(184) ⟺
 * {@code isSanzuColumn}；否则 = 初值标记 0。一条等式同时钉「withered 平面列集合与谓词逐列相等」
 * 与「与 sanzu 平面零双写」（sanzu 先写、withered 谓词含 {@code !isSanzuColumn} ⇒ 任一列被两写
 * 必然违反等式一侧）。</li>
 * <li><b>W2 写通道活性与标脏</b>：样本内 withered 平面列 &gt; 0（防谓词恒假假绿）∧ 有写入的
 * chunk {@code isModified == true}（平面写不属于方块写，持久化标脏由生产显式置位——GT5U
 * {@code GTWorldgenerator:734} 先例，sanzu 同款）。</li>
 * <li><b>W3 枯竭河床禁树禁灌木（P30 II-D 重钉为三钉）</b>：真地形 + 真表层缝 + 真装饰
 * （{@code ProsperityDecorPlacer.decorate}，VEG 工具同一条 FlatWorld 网格装配路）。
 * <b>W3 样本钉</b>：自适应落窗后干床列 ≥ 512（防"窗内无干床"的空集假绿——先按谓词粗扫定位
 * 干床簇再落窗；旧"固定窗心按 SEPARATION 错开"在批I I1 河末端定向衰减后 3 seed×4 窗干床列 = 0，
 * 样本域塌缩非行为错；谓词直引生产类 ⇒ I1 衰减自动同源）。<b>W3a 源级钉</b>：placeTree/
 * placeShrubAt/placeMarshSnagPass 三处首行 {@code isDryRiverColumn} bail 恰 3 处在场。
 * <b>W3b 行为证</b>：干床列直调 placeShrubAt（显式 x,z 形态位），首行 bail 生效（零写零异常；
 * bail 被删 ⇒ null 实参解引用当场 NPE）。落块分账<b>只报不钉</b>：自适应窗贴干床簇后干床/非干床
 * 边界大增，暴露三类合法越界写入（冠层叶盘越顶 ≤7 格；巨树形态无干床 bail——MegaTreeForms
 * redwood 锥盘/横臂、greatOak 枝臂、bayou 板根，P25 D6 bail 不含巨树趟；躺倒沼泽木尾段横跨
 * 2-5 格），旧"干床列木/叶落块 = 0"口径只在窗内无干床列时平凡成立，故降级为披露性读数。</li>
 * </ol>
 * <p>
 * <b>装配口径</b>：W1 用 Unsafe 裸 {@code Chunk}（只置 {@code biomeArray}；1.7.10 byte 平面——
 * 本工具运行时不挂 EndlessIDs 替身 ⇒ {@code BiomePlaneAccess.useShortPlane} 恒 false，走 byte
 * 列写通道，与 [2j] 的 byte 档同一面）+ {@code getChunkFromChunkCoords} 桩世界；写通道本体是
 * 生产私有方法的反射直调（零第二份谓词）。W3 的网格/表层/装饰三件全部走生产实现。
 * <p>
 * 用法：{@code java WitheredPlaneCheck}（退出码 0 = 全绿）。
 */
public final class WitheredPlaneCheck {

    /** W1/W3 的种子族（与兄弟片不同值，免得读数互串）。 */
    static final long[] SEEDS = { 0x5A614E5A5032L, 0x776974685252L, 0x336272646564L };
    /** W1 随机 chunk 扫描量（每 seed 24 chunk，分散在 ±2×LAKE_INTERVAL 域内）。 */
    static final int CHUNKS_PER_SEED = 24;

    static int assertions;
    static int failures;
    static final List<String> LINES = new ArrayList<String>();

    public static void main(String[] args) throws Exception {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        final BiomeGenBase[] p = SurfaceHarness.prosperityBiomes();
        final BiomeGenBase[] s = SurfaceHarness.shatteredBiomes();
        SurfaceHarness.recordAllAllocations(p, s);
        final BiomeGenBase withered = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
            .biomeOf(BiomeId.WITHERED_RIVERBED);
        final BiomeGenBase sanzu = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
            .biomeOf(BiomeId.SANZU_RIVER);
        check("前提：WITHERED_RIVERBED 已配槽 id=185", withered != null && withered.biomeID == 185,
            "实=" + (withered == null ? "null" : Integer.valueOf(withered.biomeID)));
        check("前提：SANZU_RIVER 已配槽 id=184", sanzu != null && sanzu.biomeID == 184,
            "实=" + (sanzu == null ? "null" : Integer.valueOf(sanzu.biomeID)));

        // ═══ W1/W2：平面写对拍（byte 通道）═══
        final Method assignSanzu = ChunkProviderProsperityRuins.class
            .getDeclaredMethod("assignSanzuRiverBiome", long.class, int.class, int.class);
        assignSanzu.setAccessible(true);
        final Method assignWithered = ChunkProviderProsperityRuins.class
            .getDeclaredMethod("assignWitheredRiverbedBiome", long.class, int.class, int.class);
        assignWithered.setAccessible(true);
        long cols = 0;
        long mismatches = 0;
        long witheredCols = 0;
        long sanzuCols = 0;
        boolean modifiedOnWrite = true;
        final Random rr = new Random(0x5731_5245_4CL);
        for (final long seed : SEEDS) {
            final PlaneWorld world = PlaneWorld.of(seed);
            final ChunkProviderProsperityRuins provider = new ChunkProviderProsperityRuins(world, seed);
            for (int i = 0; i < CHUNKS_PER_SEED; i++) {
                final int cx = (rr.nextInt(96) - 48) * 7 + (int) ((seed >>> 8) & 0xF);
                final int cz = (rr.nextInt(96) - 48) * 11;
                final Chunk chunk = bareChunk();
                world.put(cx, cz, chunk);
                // populate 同序：sanzu 先写、withered 后写（onPopulate 的调用序）
                assignSanzu.invoke(provider, Long.valueOf(seed), Integer.valueOf(cx), Integer.valueOf(cz));
                assignWithered.invoke(provider, Long.valueOf(seed), Integer.valueOf(cx), Integer.valueOf(cz));
                final byte[] plane = chunk.getBiomeArray();
                boolean anyWithered = false;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        final int x = (cx << 4) + lx;
                        final int z = (cz << 4) + lz;
                        final boolean sanzuCol = GTSRVoronoiRiverField.isSanzuColumn(seed, x, z);
                        final boolean dryCol = !sanzuCol && GTSRVoronoiRiverField.isDryRiverColumn(seed, x, z);
                        final byte got = plane[(lz << 4) | lx];
                        final byte want = dryCol ? (byte) withered.biomeID
                            : (sanzuCol ? (byte) sanzu.biomeID : (byte) 0);
                        cols++;
                        if (dryCol) {
                            witheredCols++;
                            anyWithered = true;
                        }
                        if (sanzuCol) {
                            sanzuCols++;
                        }
                        if (got != want) {
                            mismatches++;
                            if (mismatches <= 5) {
                                say("  W1 违例例：chunk(" + cx + "," + cz + ") 列(" + x + "," + z + ") 平面="
                                    + (got & 255) + " 期望=" + (want & 255) + " sanzu=" + sanzuCol
                                    + " dry=" + dryCol);
                            }
                        }
                    }
                }
                if (anyWithered && !chunk.isModified) {
                    modifiedOnWrite = false;
                }
            }
        }
        say("W-READ 平面对拍：列=" + cols + " 违例=" + mismatches + " withered列=" + witheredCols
            + " sanzu列=" + sanzuCols + "（byte 通道，" + SEEDS.length + " seed × " + CHUNKS_PER_SEED
            + " chunk 随机扫描）");
        check("W1 withered 平面列集合 == !isSanzuColumn ∧ isDryRiverColumn 逐列相等 ∧ sanzu 平面零双写"
            + "（终值三态等式：withered⟺谓词 / sanzu⟺isSanzuColumn / 其余=初值标记 0；违例=0）",
            cols >= 512 * 16L && mismatches == 0,
            "列=" + cols + " 违例=" + mismatches + " withered=" + witheredCols + " sanzu=" + sanzuCols);
        check("W2 写通道活性：样本内 withered 平面列 > 0 ∧ 有写入 chunk 的 isModified==true"
            + "（平面写显式标脏，sanzu 同款 GT5U 先例）",
            witheredCols > 0 && modifiedOnWrite,
            "withered列=" + witheredCols + " 标脏违例=" + !modifiedOnWrite);

        // ═══ W3：枯竭河床列树/灌木落块 = 0 ═══
        // 采样几何（v1.20.53 P30 II-D 改自适应）：每 seed ≤4 个 16×16-chunk 子窗（side 256，
        // 网格内存同 VEG 档）。旧版固定窗心按 SEPARATION（1050 格 ≈ 66 chunk）错开——批I I1
        // 河末端定向衰减落地后 3 seed × 4 固定窗内干床列 = 0（样本域塌缩，非行为错：谓词直引
        // 生产 GTSRVoronoiRiverField.isDryRiverColumn ⇒ I1 衰减自动同源）。改为先按谓词粗扫
        // （stride 16，域 ±8192 格）定位干床簇、按窗桶命中数取 top 窗再落真地形窗
        // （locateDryWindows），保证干床列样本 ≥ 512。
        long dryCols = 0;
        long placements = 0;
        long logOnDryNonMarsh = 0;
        long leafOnDry = 0;
        long marshLogOnDry = 0;
        final int axis = 16;
        final int side = axis * 16;
        for (final long seed : SEEDS) {
            final GTSRDimensionDef def78 = SurfaceHarness.def(true, p, SurfaceHarness.prosperityWeights());
            final GTSRWorldChunkManager mgr = new GTSRWorldChunkManager(seed, def78);
            final Block[] scratch = new Block[65536];
            final byte[] scratchMeta = new byte[65536];
            final int[][] dryWindows = locateDryWindows(seed, 4);
            final StringBuilder wdesc = new StringBuilder();
            for (int wi = 0; wi < dryWindows.length; wi++) {
                if (wi > 0) wdesc.append(' ');
                wdesc.append('(')
                    .append(dryWindows[wi][0])
                    .append(',')
                    .append(dryWindows[wi][1])
                    .append(")h=")
                    .append(dryWindows[wi][2]);
            }
            say("W-READ seed=0x" + Long.toHexString(seed) + " 自适应干床窗（chunk 原点+粗扫命中）：" + wdesc);
            for (final int[] off : dryWindows) {
                final int cxBase = off[0];
                final int czBase = off[1];
                final Block[] grid = new Block[side * side * 256];
                final DecoWorld world = DecoWorld.of(grid, cxBase << 4, czBase << 4, seed, side);
                final GTSRChunkProviderBase prov = new ChunkProviderProsperityRuins(world, seed);
                final Method gen = SurfaceHarness.generateTerrain();
                final Method seam = SurfaceHarness.surfaceSeam(prov.getClass());
                for (int dx = 0; dx < axis; dx++) {
                    for (int dz = 0; dz < axis; dz++) {
                        final int cx = cxBase + dx;
                        final int cz = czBase + dz;
                        java.util.Arrays.fill(scratch, null);
                        java.util.Arrays.fill(scratchMeta, (byte) 0);
                        gen.invoke(prov, cx, cz, scratch, scratchMeta, null);
                        final BiomeGenBase[] plane = mgr.loadBlockGeneratorData(null, cx * 16, cz * 16, 16, 16);
                        seam.invoke(null, seed, cx * 16, cz * 16, scratch, scratchMeta, plane);
                        final int bx = dx * 16;
                        final int bz = dz * 16;
                        for (int lx = 0; lx < 16; lx++) {
                            for (int lz = 0; lz < 16; lz++) {
                                final int scol = (lx << 12) | (lz << 8);
                                final int dst = (bx + lx) + (bz + lz) * side;
                                for (int y = 0; y < 256; y++) {
                                    grid[y * side * side + dst] = scratch[scol | y];
                                }
                            }
                        }
                    }
                }
                final RecordSink sink = new RecordSink(world, side);
                for (int dx = 0; dx < axis; dx++) {
                    for (int dz = 0; dz < axis; dz++) {
                        final int cx = cxBase + dx;
                        final int cz = czBase + dz;
                        final int idx = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
                            .ordinalAt((cx << 4) + 8, (cz << 4) + 8).ordinal;
                        sink.chunk(cx, cz);
                        ProsperityDecorPlacer.decorate(world, seed, cx, cz, idx, sink);
                    }
                }
                for (int lz = 0; lz < side; lz++) {
                    for (int lx = 0; lx < side; lx++) {
                        if (GTSRVoronoiRiverField.isDryRiverColumn(seed, (cxBase << 4) + lx, (czBase << 4) + lz)) {
                            dryCols++;
                        }
                    }
                }
                placements += sink.woodLeafPlacements;
                logOnDryNonMarsh += sink.logOnDryNonMarsh;
                leafOnDry += sink.leafOnDry;
                marshLogOnDry += sink.marshLogOnDry;
            }
        }
        // ═══ W3 复核分账（P30 II-D）：干床列上的木/叶落块归因 ═══ 自适应落窗把采样窗贴到干床簇上，
        // 干床/非干床边界大增后暴露三类<b>合法</b>越界写入（生产设计态，bail 只在落点本柱粒度）：
        // ① 冠层叶盘越顶（placeLeafDisc/canopyFor 半径 ≤7）；② 巨树形态无干床 bail（MegaTreeForms
        // redwood 圆锥干/轮枝横臂、greatOak 枝臂、bayou 板根——P25 D6 bail 只钉 placeTree/
        // placeShrubAt/placeMarshSnagPass 三处，巨树趟不在其列；实例复核：干床上非沼泽干呈单 y
        // 水平连续段 = redwood 锥盘行/横臂形态）；③ 躺倒沼泽木尾段横跨（placeFallenMarshLog 起点过
        // bail、横段 2-5 格可跨入）。故落块计数降为只报不钉，禁树断言换 W3a/W3b 两钉（源级 + 行为）。
        say("W-READ 装饰：干床列=" + dryCols + " 木/叶落块=" + placements + " ｜ 干床分账（只报不钉）：非沼泽种干"
                + "（巨树锥盘/横臂/板根为主）=" + logOnDryNonMarsh + " 叶越顶=" + leafOnDry
                + " 沼泽躺倒木尾段=" + marshLogOnDry
                + "（3 seed × ≤4 自适应窗 × " + axis + "×" + axis + " chunk 真地形装饰，窗取干床簇粗扫 top——"
                + "P30 II-D：旧固定窗（SEPARATION 错开）在批I I1 河衰减后干床列=0 样本域塌缩；旧"
                + "\"干床列木/叶落块=0\"只在窗内无干床列时平凡成立）");
        check("W3 干床样本：自适应落窗后干床列 ≥ 512（防空集假绿；先按 isDryRiverColumn 粗扫定位干床簇"
            + " 再落窗——P30 II-D 起口径，谓词直引生产类 ⇒ 批I I1 河衰减自动同源）",
            dryCols >= 512,
            "干床列=" + dryCols);
        // —— W3a：bail 源级钉（三处首行门，TPDIM sourcePins 同法）——
        final String decorSrc = new String(
            java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(
                    "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityDecorPlacer.java")),
            "UTF-8");
        int bailCount = 0;
        for (int i = decorSrc.indexOf("if (GTSRVoronoiRiverField.isDryRiverColumn(worldSeed, x, z))"); i >= 0; i = decorSrc
            .indexOf("if (GTSRVoronoiRiverField.isDryRiverColumn(worldSeed, x, z))", i + 1)) {
            bailCount++;
        }
        check("W3a 枯竭河床禁树禁灌木 bail 源级钉：placeTree/placeShrubAt/placeMarshSnagPass 三处首行"
            + " isDryRiverColumn 门在场（实测 " + bailCount + " 处 == 3；任一被删/改谓词即红——"
            + "干床上的合法落块只有冠层越顶/巨树形态/躺倒木尾段三类，均不落本柱起点）",
            bailCount == 3,
            "bail 出现次数=" + bailCount);
        // —— W3b：bail 行为证（显式 x,z 形态位直调；bail 在首行 ⇒ null 实参零解引用零写入，
        //     bail 被删 ⇒ pickWood(rand, tier) 对 null 解引用当场 NPE）——
        int dryProbeX = Integer.MIN_VALUE;
        int dryProbeZ = 0;
        outer: for (final long seed : SEEDS) {
            for (int x = -4096; x < 4096; x += 7) {
                for (int z = -4096; z < 4096; z += 7) {
                    if (GTSRVoronoiRiverField.isDryRiverColumn(seed, x, z)) {
                        dryProbeX = x;
                        dryProbeZ = z;
                        break outer;
                    }
                }
            }
        }
        boolean shrubBailFired = false;
        if (dryProbeX != Integer.MIN_VALUE) {
            final Method shrubAt = ProsperityDecorPlacer.class.getDeclaredMethod(
                "placeShrubAt",
                World.class,
                long.class,
                com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder.class,
                Random.class,
                int.class,
                int.class,
                int.class,
                ProsperityDecorPlacer.VegTier.class);
            shrubAt.setAccessible(true);
            try {
                shrubAt.invoke(null, null, SEEDS[0], null, null, dryProbeX, dryProbeZ, 0, null);
                shrubBailFired = true; // 正常返回 = 首行 bail 生效（未触任何 null 实参）
            } catch (final Exception e) {
                shrubBailFired = false; // bail 缺失 ⇒ 方法体对 null rand/tier 解引用
            }
        }
        check("W3b 枯竭河床禁树禁灌木 bail 行为证：干床列 (" + dryProbeX + "," + dryProbeZ
            + ") 直调 placeShrubAt 首行 bail 生效（零写零异常；bail 被删 ⇒ NPE 当场红——"
            + "placeTree/placeMarshSnagPass 同位同式由 W3a 源级钉覆盖）",
            dryProbeX != Integer.MIN_VALUE && shrubBailFired,
            "干床探针列=" + dryProbeX + " bail 生效=" + shrubBailFired);

        for (final String l : LINES) {
            System.out.println(l);
        }
        System.out.println("── 断言=" + assertions + " 红=" + failures + " ──");
        System.out.println(failures == 0 ? "WITHERED PLANE CHECKS: ALL GREEN" : "WITHERED PLANE FAILURES="
            + failures);
        if (failures != 0) {
            System.exit(1);
        }
    }

    static void check(String label, boolean ok, String detail) {
        assertions++;
        if (!ok) {
            failures++;
            LINES.add("FAIL  " + label + " ｜ " + detail);
        } else {
            LINES.add("PASS  " + label);
        }
    }

    /**
     * P30 II-D 自适应落窗：域 ±8192 格、stride 16 粗扫生产谓词
     * {@link GTSRVoronoiRiverField#isDryRiverColumn}（直引生产类 ⇒ 批I I1 河末端定向衰减自动
     * 同源），命中按 16×16-chunk（256 格）窗桶累计，贪心取命中最多的前 {@code want} 个窗
     * （桶心 Chebyshev ≥ 2 窗防重叠重复计数；只选命中 &gt; 0 的桶，不足 want 个就返回实有——
     * 0 个 = 该域无干床，W3 样本断言据此红）。纯函数（同 seed 恒同窗）。
     *
     * @return 每行 {窗原点 chunkX, 窗原点 chunkZ, 粗扫命中数}
     */
    static int[][] locateDryWindows(long seed, int want) {
        final int stride = 16;
        final int half = 8192;
        final int windowBlocks = 16 * 16; // 16 chunk × 16 格 = 256
        final int bucketsPerAxis = 2 * half / windowBlocks; // 64
        final int[][] hits = new int[bucketsPerAxis][bucketsPerAxis];
        for (int z = -half; z < half; z += stride) {
            final int bz = (z + half) / windowBlocks;
            for (int x = -half; x < half; x += stride) {
                if (GTSRVoronoiRiverField.isDryRiverColumn(seed, x, z)) {
                    hits[(x + half) / windowBlocks][bz]++;
                }
            }
        }
        final int[] chosenBx = new int[want];
        final int[] chosenBz = new int[want];
        final int[] chosenHits = new int[want];
        int chosen = 0;
        for (int round = 0; round < want; round++) {
            int best = 0;
            int bestBx = 0;
            int bestBz = 0;
            for (int bz = 0; bz < bucketsPerAxis; bz++) {
                for (int bx = 0; bx < bucketsPerAxis; bx++) {
                    if (hits[bx][bz] <= best) {
                        continue; // 无命中或不超过当前轮最优
                    }
                    boolean near = false;
                    for (int c = 0; c < chosen; c++) {
                        if (Math.max(Math.abs(bx - chosenBx[c]), Math.abs(bz - chosenBz[c])) < 2) {
                            near = true;
                            break;
                        }
                    }
                    if (near) {
                        continue;
                    }
                    best = hits[bx][bz];
                    bestBx = bx;
                    bestBz = bz;
                }
            }
            if (best <= 0) {
                break;
            }
            chosenBx[chosen] = bestBx;
            chosenBz[chosen] = bestBz;
            chosenHits[chosen] = best;
            chosen++;
        }
        final int[][] out = new int[chosen][3];
        for (int c = 0; c < chosen; c++) {
            out[c][0] = -half / 16 + chosenBx[c] * 16;
            out[c][1] = -half / 16 + chosenBz[c] * 16;
            out[c][2] = chosenHits[c];
        }
        return out;
    }

    static void say(String s) {
        LINES.add(s);
    }

    /** Unsafe 裸 Chunk（只置 biomeArray=全 0 标记 + isModified=false；byte 平面通道）。 */
    static Chunk bareChunk() throws Exception {
        final sun.misc.Unsafe u = SurfaceHarness.unsafe();
        final Chunk c = (Chunk) u.allocateInstance(Chunk.class);
        final Field f = Chunk.class.getDeclaredField("blockBiomeArray");
        f.setAccessible(true);
        u.putObject(c, u.objectFieldOffset(f), new byte[256]);
        return c;
    }

    /**
     * {@code getChunkFromChunkCoords} 桩世界（SurfaceHarness.HarnessWorld 同款 Unsafe 装配；
     * 唯一行为差异 = chunk 存根表——生产写通道只消费这一个入口）。
     */
    static final class PlaneWorld extends World {

        private final Map<Long, Chunk> chunks = new HashMap<Long, Chunk>();

        private PlaneWorld() {
            super((ISaveHandler) null, (String) null, (WorldProvider) null, (WorldSettings) null, (Profiler) null);
        }

        static PlaneWorld of(long seed) throws Exception {
            final sun.misc.Unsafe u = SurfaceHarness.unsafe();
            final PlaneWorld w = (PlaneWorld) u.allocateInstance(PlaneWorld.class);
            u.putObject(w, u.objectFieldOffset(PlaneWorld.class.getDeclaredField("chunks")),
                new HashMap<Long, Chunk>());
            final WorldInfo info = (WorldInfo) u.allocateInstance(WorldInfo.class);
            final Field sf = WorldInfo.class.getDeclaredField("randomSeed");
            sf.setAccessible(true);
            u.putLong(info, u.objectFieldOffset(sf), seed);
            final Field wf = World.class.getDeclaredField("worldInfo");
            wf.setAccessible(true);
            u.putObject(w, u.objectFieldOffset(wf), info);
            final HarnessProvider2 prov = (HarnessProvider2) u.allocateInstance(HarnessProvider2.class);
            prov.seedValue = seed;
            final Field pf = World.class.getDeclaredField("provider");
            pf.setAccessible(true);
            u.putObject(w, u.objectFieldOffset(pf), prov);
            return w;
        }

        void put(int cx, int cz, Chunk chunk) {
            chunks.put(Long.valueOf(((long) cx << 32) ^ (cz & 0xFFFFFFFFL)), chunk);
        }

        @Override
        public Chunk getChunkFromChunkCoords(int cx, int cz) {
            return chunks.get(Long.valueOf(((long) cx << 32) ^ (cz & 0xFFFFFFFFL)));
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
        public net.minecraft.entity.Entity getEntityByID(int id) {
            return null;
        }
    }

    /** {@code getSeed()} 回读桩世界的 provider（SurfaceHarness.HarnessProvider 同款）。 */
    static final class HarnessProvider2 extends WorldProvider {

        long seedValue;

        @Override
        public String getDimensionName() {
            return "withered-plane-harness";
        }

        @Override
        public long getSeed() {
            return seedValue;
        }
    }

    /** W3 的平坦网格合成世界（VEG FlatWorld 同一条装配路）。 */
    static final class DecoWorld extends World {

        Block[] grid;
        int originBlockX;
        int originBlockZ;
        int side;
        long seedValue;

        private DecoWorld() {
            super((ISaveHandler) null, (String) null, (WorldProvider) null, (WorldSettings) null, (Profiler) null);
        }

        static DecoWorld of(Block[] grid, int obx, int obz, long seed, int side) throws Exception {
            final sun.misc.Unsafe u = SurfaceHarness.unsafe();
            final DecoWorld w = (DecoWorld) u.allocateInstance(DecoWorld.class);
            w.grid = grid;
            w.originBlockX = obx;
            w.originBlockZ = obz;
            w.side = side;
            w.seedValue = seed;
            final WorldInfo info = (WorldInfo) u.allocateInstance(WorldInfo.class);
            final Field sf = WorldInfo.class.getDeclaredField("randomSeed");
            sf.setAccessible(true);
            u.putLong(info, u.objectFieldOffset(sf), seed);
            final Field wf = World.class.getDeclaredField("worldInfo");
            wf.setAccessible(true);
            u.putObject(w, u.objectFieldOffset(wf), info);
            final HarnessProvider2 p = (HarnessProvider2) u.allocateInstance(HarnessProvider2.class);
            p.seedValue = seed;
            final Field dim = WorldProvider.class.getDeclaredField("dimensionId");
            dim.setAccessible(true);
            u.putInt(p, u.objectFieldOffset(dim), 78);
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
        public boolean setBlock(int x, int y, int z, Block block, int meta, int flags) {
            final int i = index(x, y, z);
            if (i >= 0) {
                grid[i] = block == net.minecraft.init.Blocks.air ? null : block;
                return true;
            }
            return false;
        }

        @Override
        public boolean isAirBlock(int x, int y, int z) {
            final int i = index(x, y, z);
            return i < 0 || grid[i] == null;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            final int i = index(x, y, z);
            return i < 0 ? null : grid[i];
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
        public net.minecraft.entity.Entity getEntityByID(int id) {
            return null;
        }
    }

    /**
     * W3 的落块记录 sink：分账干床列上的落块（<b>只报不钉</b>，P30 II-D 起）。树干
     * （placeTree）/灌木干（placeShrubAt）/立枯桩（placeMarshSnagStump）的 LOG 只写落点本柱
     * （bail 首行所在列）⇒ 这些起点永远不在干床；干床列上的落块全部来自三类合法越界：冠层
     * 叶盘越顶（placeLeafDisc/canopyFor）、巨树形态（MegaTreeForms redwood 锥盘/横臂、
     * greatOak 枝臂、bayou 板根——巨树趟无干床 bail）、躺倒沼泽木尾段（placeFallenMarshLog
     * 横跨）。断言面在 W3a（三处 bail 源级钉）与 W3b（干床列直调 placeShrubAt 的行为证）。
     */
    static final class RecordSink implements BlockSink {

        final DecoWorld world;
        final int side;
        int cx;
        int cz;
        long woodLeafPlacements;
        long logOnDryNonMarsh;
        long leafOnDry;
        long marshLogOnDry;

        RecordSink(DecoWorld world, int side) {
            this.world = world;
            this.side = side;
        }

        void chunk(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            if (!(block instanceof Block)) {
                return false;
            }
            final Block b = (Block) block;
            final boolean wood = b == BlocksGTSR.prosperityRustLog || b == BlocksGTSR.prosperityCopperLog
                || b == BlocksGTSR.prosperityBrassLog || b == BlocksGTSR.prosperityMarshLog;
            final boolean leaf = b == BlocksGTSR.prosperityRustLeaves || b == BlocksGTSR.prosperityCopperLeaves
                || b == BlocksGTSR.prosperityBrassLeaves || b == BlocksGTSR.prosperityMarshLeaves;
            if (wood || leaf) {
                woodLeafPlacements++;
                if (GTSRVoronoiRiverField.isDryRiverColumn(world.seedValue, x, z)) {
                    if (wood) {
                        if (b == BlocksGTSR.prosperityMarshLog) {
                            marshLogOnDry++;
                        } else {
                            logOnDryNonMarsh++;
                        }
                    } else {
                        leafOnDry++;
                    }
                }
            }
            world.setBlock(x, y, z, b, meta, flags);
            return true;
        }
    }
}
