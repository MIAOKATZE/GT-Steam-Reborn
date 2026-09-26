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
 * <li><b>W3 枯竭河床列树/灌木落块 = 0</b>：真地形 + 真表层缝 + 真装饰
 * （{@code ProsperityDecorPlacer.decorate}，VEG 工具同一条 FlatWorld 网格装配路），记录每格
 * LOG/LEAF 落块的列，断言 {@code isDryRiverColumn} 列上木/叶落块数为 0（placeTree/placeShrubAt
 * 首行 bail 的行为证）。样本要求：扫描窗内干床列 ≥ 512（防"窗内无干床"的空集假绿）。</li>
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
        // 采样几何：每 seed 4 个 16×16-chunk 子窗（side 256，网格内存同 VEG 档），窗心按 SEPARATION
        // （1050 格 ≈ 66 chunk）错开 ⇒ 4 窗合计恒覆盖 ≥1 条 Voronoi 边（干河床沿边分布）。
        long dryCols = 0;
        long placements = 0;
        long placementsOnDry = 0;
        final int axis = 16;
        final int side = axis * 16;
        final int[][] windowOffsets = { { 0, 0 }, { 96, 0 }, { 0, 96 }, { 96, 96 } };
        for (final long seed : SEEDS) {
            final GTSRDimensionDef def78 = SurfaceHarness.def(true, p, SurfaceHarness.prosperityWeights());
            final GTSRWorldChunkManager mgr = new GTSRWorldChunkManager(seed, def78);
            final Block[] scratch = new Block[65536];
            final byte[] scratchMeta = new byte[65536];
            for (final int[] off : windowOffsets) {
                final int cxBase = (int) ((seed >>> 4) & 3) * 997 + off[0];
                final int czBase = (int) ((seed >>> 6) & 3) * 1231 + off[1];
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
                placementsOnDry += sink.woodLeafOnDry;
            }
        }
        say("W-READ 装饰：干床列=" + dryCols + " 木/叶落块=" + placements + " 其中干床列上=" + placementsOnDry
            + "（3 seed × 4 窗 × " + axis + "×" + axis + " chunk 真地形装饰，窗心错开 ≥ SEPARATION）");
        check("W3 枯竭河床列树/灌木落块 = 0（placeTree/placeShrubAt 首行 isDryRiverColumn bail 的行为证；"
            + "干床列样本 ≥ 512 防空集假绿）",
            dryCols >= 512 && placementsOnDry == 0,
            "干床列=" + dryCols + " 干床落块=" + placementsOnDry + " 总落块=" + placements);

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

    /** W3 的落块记录 sink：只记木/叶落块及其列是否在干床上。 */
    static final class RecordSink implements BlockSink {

        final DecoWorld world;
        final int side;
        int cx;
        int cz;
        long woodLeafPlacements;
        long woodLeafOnDry;

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
                    woodLeafOnDry++;
                }
            }
            world.setBlock(x, y, z, b, meta, flags);
            return true;
        }
    }
}
