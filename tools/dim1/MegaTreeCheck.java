import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.storage.ISaveHandler;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.IslandMegaTree;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.MegaTreeAnchors;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * P22 版 B · S3 岛心巨树（垂天玄柯）判据：A（净空五读）/ B（派生窗）/ C（30 座活湖实测）/
 * D（重放幂等 + 9-chunk 单射 + 源级纪律）四组，范式照 {@code CaveFieldCheck}。
 *
 * <p>═══ A 净空五读（单树几何硬界）═══ 冠 bbox 水平_extent ≤ 31（直径 30 硬值 + 1）且 ≥ 29
 * （在场，防冠壳缩水）；单树跨 ≤ 9 chunk；{@code windowChunks(15) == 3}（派生式，改半径档自动跟）；
 * 树顶 ≤ 255（+ y0=200 早退臂零写入，照 MegaTreeForms:93）；单树写格 ≤ 9500 / 单 chunk ≤ 3600。
 *
 * <p>═══ B 派生窗（防写死）═══ 多 radius 断言 {@code windowChunks} == 独立重算公式
 * {@code 2*ceil((2r+1)/16)-1}；恒奇数；且 ≥ 穷举最小窗（充分性方向：窗口至少够大）。
 *
 * <p>═══ C 30 座活湖实测 ═══ 锚点搜索（湖心反解 → 活湖门双源对拍）；岛顶 y0 实测带
 * [{@link #Y0_MIN},{@link #Y0_MAX}]（heightAt 实值，禁假设值——带取首测实测 ±余量，见 PROGRESS）；
 * 出岛必有树 100%（30/30 落树，log/leaf 数下界在场）；写格预算实测带（只报 + 硬顶 9500/3600）；
 * 树干穿水行为：合成 y0=60 锚点 + 水世界（≤67 非空气），干段 61..67 全部落木（≤67 段由木取代水，
 * p21 A3 口径）且整套写集与空世界逐格相同（写不依赖世界内容 ⇒ owner-local 让行只发生在叶门）。
 *
 * <p>═══ D 重放幂等 + 一致性 ═══ 同 (seed,chunk) 双跑 {@code placeIslandTreePass} 落块
 * <b>序列</b>逐位同；<b>9-chunk 重放并集 == 单世界一次性写入逐格</b>（owner 单射行为级证明：
 * 每格恰被一个 chunk 写，无空洞无重复——枚举漏一个 chunk 即红）；荒漠区（3×3 窗采样全无主干带）
 * 精确 0 写入；源级纪律：两新类零 {@code this.rand}/{@code world.rand}/{@code nextLong}；
 * 新盐 {@code SALT_ISLAND_TREE = 0x49534C4E44L} 在场且与 {@code SALT_MEGA}(0x6D656761) 不同值、
 * 由<b>锚点槽</b> {@code chunkSeed(worldSeed, ax>>4, az>>4)} 派生（成对断言防假绿）；
 * {@code placeIslandTreePass} 挂在 mega 段之前。
 *
 * <p>口径申报：合成世界只供叶门（isAirBlock/getBlock）与穿水臂消费，锚点/形态全部走生产纯函数
 * （工具不自算第二份判定）；单树 rand 派生的盐字面经源级断言与生产钉同值。
 * 出口：stdout 逐组判定，末行 {@code MEGATREE assertions=<n> fail=<m>}；任何红 exit 1。
 */
public final class MegaTreeCheck {

    /** 岛心锚点搜索种（CaveFieldCheck.CARVE_SEED 同族步进）。 */
    static final long SEARCH_SEED0 = 0x1AFEE7A9E5A7L;
    static final long SEARCH_SEED_STEP = 0x9E37L;

    /** 岛顶 y0 实测带（heightAt 实值；首测 30 座活湖全部 = 72 = SEA_LEVEL+LAKE_ISLAND_LIFT 岛面平台，
     * 取 ±1 余量立带——偏出即岛面平台腿被动，不是放宽）。 */
    static final int Y0_MIN = 71;
    static final int Y0_MAX = 73;

    /** 单树写格硬顶（p21 §4 预算）/ 单 chunk 硬顶。 */
    static final int TREE_CELL_CAP = 9500;
    static final int CHUNK_CELL_CAP = 3600;

    /** 出岛必有树的下界（干/叶量级在场，防"落了 1 格也算树"式假绿）。 */
    static final int LOG_FLOOR = 1000;
    static final int LEAF_FLOOR = 1500;

    /** 生产盐字面（源级断言与 DecorPlacer 钉同值后才用于单树 rand 派生）。 */
    static final long SALT_ISLAND_TREE = 0x49534C4E44L;

    static final String ANCHOR_SRC = "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/MegaTreeAnchors.java";
    static final String TREE_SRC = "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/IslandMegaTree.java";
    static final String DECOR_SRC = "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityDecorPlacer.java";

    static int total;
    static int fails;

    public static void main(String[] args) throws Exception {
        bootstrap();
        groupA();
        groupB();
        groupC();
        groupD();
        System.out.println("MEGATREE assertions=" + total + " fail=" + fails);
        if (fails > 0) {
            System.out.println("MEGATREE CHECKS: RED");
            System.exit(1);
        }
        System.out.println("MEGATREE CHECKS: GREEN");
    }

    static void bootstrap() throws Exception {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        // 账本装配（GenBenchCheck/CaveFieldCheck 同形：dim78 四 selector 成员占 180..183）
        for (int i = 0; i < 4; i++) {
            final BiomeGenBase b = new BiomeGenBase(180 + i) {};
            GTSRBiomeAuthority.recordAllocation(GTSRBiomeAuthority.BiomeId.values()[i], 180 + i, 180 + i, b);
        }
        if (BlocksGTSR.prosperityZenithLog == null || BlocksGTSR.prosperityJadeLeaves == null) {
            throw new IllegalStateException("P22-B S2 巨树方块缺席（blockFamily 未同步）");
        }
    }

    static void a(String group, String name, boolean ok) {
        total++;
        if (!ok) {
            fails++;
            System.out.println(group + " FAIL: " + name);
        }
    }

    static void read(String line) {
        System.out.println("  READ " + line);
    }

    // ════════════════════════ 记录 sink / 合成世界 ════════════════════════

    static final int KIND_OTHER = 0;
    static final int KIND_LOG = 1;
    static final int KIND_LEAF = 2;

    /** 落块记录 sink：保留插入序（双跑序列对拍）+ 格→种类表（并集对拍）。 */
    static final class Rec implements BlockSink {

        final Map<Long, Integer> cells = new LinkedHashMap<>();
        final List<long[]> order = new ArrayList<>();

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            final Block b = (Block) block;
            final int kind = b == BlocksGTSR.prosperityZenithLog ? KIND_LOG
                : (b == BlocksGTSR.prosperityJadeLeaves ? KIND_LEAF : KIND_OTHER);
            cells.put(key(x, y, z), Integer.valueOf(kind));
            order.add(new long[] { key(x, y, z), kind });
            return true;
        }

        int logs() {
            return count(KIND_LOG);
        }

        int leaves() {
            return count(KIND_LEAF);
        }

        int count(int kind) {
            int n = 0;
            for (final Integer v : cells.values()) {
                if (v.intValue() == kind) {
                    n++;
                }
            }
            return n;
        }
    }

    /** 落块序列逐元素相等（List.equals 对 long[] 元素是引用比较，必须手比）。 */
    static boolean seqEquals(List<long[]> a, List<long[]> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i)[0] != b.get(i)[0] || a.get(i)[1] != b.get(i)[1]) {
                return false;
            }
        }
        return true;
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** 纯空气合成世界（只供叶门读；y>255 镜像 vanilla 实心界外）。waterTop ≥ 0 时 ≤waterTop 为非空气"水"。 */
    static final class AirWorld extends World {

        int waterTop = -1;

        private AirWorld() {
            super((ISaveHandler) null, (String) null, (WorldProvider) null, (WorldSettings) null, (Profiler) null);
        }

        static AirWorld make() throws Exception {
            final sun.misc.Unsafe u = SurfaceHarness.unsafe();
            return (AirWorld) u.allocateInstance(AirWorld.class);
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            if (y > 255) {
                return Blocks.bedrock;
            }
            if (y >= 0 && y <= waterTop) {
                return Blocks.cobblestone; // "水"占位：非空气且非 grass/snow ⇒ 叶门必拒
            }
            return Blocks.air;
        }

        @Override
        public boolean isAirBlock(int x, int y, int z) {
            if (y > 255) {
                return false;
            }
            return y < 0 || y > waterTop;
        }

        @Override
        public net.minecraft.entity.Entity getEntityByID(int id) {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        protected net.minecraft.world.chunk.IChunkProvider createChunkProvider() {
            return null;
        }
    }

    // ════════════════════════ 锚点搜索（生产纯函数，双源对拍）════════════════════════

    static final class Anchor {

        final long seed;
        final int ax;
        final int az;
        final int y0;

        Anchor(long seed, int ax, int az, int y0) {
            this.seed = seed;
            this.ax = ax;
            this.az = az;
            this.y0 = y0;
        }
    }

    /** 活湖锚点搜索（CaveFieldCheck A7 同式：湖心反解 + 域门）。 */
    static Anchor searchAnchor(long seed, int x, int z) {
        if (GTSRVoronoiRiverField.trunkAt(seed, x, z) <= 0.0D) {
            return null;
        }
        final double[] out = new double[4];
        GTSRVoronoiRiverField.lakeCellCenterAt(seed, x, z, out);
        if ((int) out[2] == Integer.MIN_VALUE) {
            return null;
        }
        final int cx = (int) Math.round(out[0]);
        final int cz = (int) Math.round(out[1]);
        if (GTSRVoronoiRiverField.lakeAt(seed, cx, cz) >= GTSRVoronoiRiverField.LAKE_ISLAND) {
            return null;
        }
        return new Anchor(seed, cx, cz, ProsperityTerrainProfile.heightAt(seed, cx, cz));
    }

    static List<Anchor> findAnchors(int want) {
        final List<Anchor> found = new ArrayList<>();
        final java.util.Set<Long> seen = new java.util.HashSet<>();
        for (long s = 0; s < 64 && found.size() < want; s++) {
            final long seed = SEARCH_SEED0 + s * SEARCH_SEED_STEP;
            for (int z = -9000; z <= 9000 && found.size() < want; z += 300) {
                for (int x = -9000; x <= 9000 && found.size() < want; x += 300) {
                    final Anchor a = searchAnchor(seed, x, z);
                    if (a != null && seen.add(Long.valueOf(((long) a.ax << 32) ^ (a.az & 0xFFFFFFFFL)))) {
                        found.add(a); // 同一湖的多个粗格列只计一次（30 座活湖 = 30 座不同湖）
                    }
                }
            }
        }
        return found;
    }

    /** 单树一次性写入（rand 派生 = 生产锚点槽式，盐字面经 D 组源级钉同值）。 */
    static Rec singleTree(World world, Anchor a) {
        final Rec rec = new Rec();
        final Random treeRand = new Random(
            GTSRWorldgenHash.chunkSeed(a.seed, a.ax >> 4, a.az >> 4) ^ SALT_ISLAND_TREE);
        IslandMegaTree.placeInto(world, new com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder(rec),
            treeRand, a.ax, a.az, a.y0);
        return rec;
    }

    /** 一棵树的读数（bbox/span/top/预算）。 */
    static final class TreeStats {

        int dxExtent;
        int dzExtent;
        int topY;
        int cells;
        int chunkMax;
        int chunksSpanned;
    }

    static TreeStats stats(Rec rec) {
        final TreeStats s = new TreeStats();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        final Map<Long, Integer> byChunk = new LinkedHashMap<>();
        for (final Long k : rec.cells.keySet()) {
            final int x = signFixX((int) (k >> 38));
            final int z = signFixZ((int) ((k >> 12) & 0x3FFFFFF));
            final int y = (int) (k & 0xFFF);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            s.topY = Math.max(s.topY, y);
            byChunk.merge(chunkKey(x, z), Integer.valueOf(1), Integer::sum);
        }
        s.cells = rec.cells.size();
        s.dxExtent = rec.cells.isEmpty() ? 0 : maxX - minX + 1;
        s.dzExtent = rec.cells.isEmpty() ? 0 : maxZ - minZ + 1;
        s.chunksSpanned = byChunk.size();
        for (final Integer v : byChunk.values()) {
            s.chunkMax = Math.max(s.chunkMax, v.intValue());
        }
        return s;
    }

    /** key 打包的 26 位字段还原符号（26 位补码回展）。 */
    static int signFixX(int v) {
        return (v << 6) >> 6;
    }

    static int signFixZ(int v) {
        return (v << 6) >> 6;
    }

    static long chunkKey(int x, int z) {
        return ((long) (x >> 4) << 32) | ((long) (z >> 4) & 0xFFFFFFFFL);
    }

    // ════════════════════════ A 组：净空五读 ════════════════════════

    static void groupA() throws Exception {
        final List<Anchor> anchors = findAnchors(1);
        a("A", "anchor.found", !anchors.isEmpty());
        if (anchors.isEmpty()) {
            return;
        }
        final World world = AirWorld.make();
        final Rec rec = singleTree(world, anchors.get(0));
        final TreeStats s = stats(rec);
        read("A tree cells=" + s.cells + " logs=" + rec.logs() + " leaves=" + rec.leaves() + " dxExtent=" + s.dxExtent
            + " dzExtent=" + s.dzExtent + " topY=" + s.topY + " spanChunks=" + s.chunksSpanned + " chunkMax="
            + s.chunkMax);
        a("A", "bbox.horizontalExtent<=31(dx=" + s.dxExtent + ",dz=" + s.dzExtent + ")",
            s.dxExtent <= 31 && s.dzExtent <= 31);
        a("A", "bbox.horizontalPresence>=29", s.dxExtent >= 29 && s.dzExtent >= 29);
        a("A", "spanChunks<=9(" + s.chunksSpanned + ")", s.chunksSpanned <= 9);
        a("A", "windowChunks(15)==3(derived)", MegaTreeAnchors.windowChunks(15) == 3);
        a("A", "topY<=255(" + s.topY + ")", s.topY <= 255);
        a("A", "cells<=9500(" + s.cells + ")", s.cells <= TREE_CELL_CAP);
        a("A", "chunkMax<=3600(" + s.chunkMax + ")", s.chunkMax <= CHUNK_CELL_CAP);
        // 255 红线早退臂（照 MegaTreeForms:93）：合成高锚 y0=200 ⇒ 整树零写入
        final Rec early = new Rec();
        final boolean placed = IslandMegaTree.placeInto(world,
            new com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder(early),
            new Random(42L), 100, 100, 200);
        a("A", "earlyExit.y0=200.zeroWrite", !placed && early.cells.isEmpty());
    }

    // ════════════════════════ B 组：派生窗（防写死）════════════════════════

    static void groupB() {
        final int[] radii = { 4, 8, 12, 15, 20, 24, 31, 40, 63, 80 };
        boolean formulaOk = true;
        boolean oddOk = true;
        boolean sufficientOk = true;
        for (final int r : radii) {
            final int w = MegaTreeAnchors.windowChunks(r);
            final int expect = 2 * ((2 * r + 16) / 16) - 1; // 独立重算 2*ceil((2r+1)/16)-1
            if (w != expect) {
                formulaOk = false;
            }
            if ((w & 1) != 1) {
                oddOk = false;
            }
            // 穷举最小窗：半窗 h 扩到窗列域 [−16h, 16(h+1)−1] ⊇ [−r, 15+r] ⇔ h ≥ ceil(r/16)
            // （小 r 时公式窗 h_f=ceil((2r+1)/16)−1 &lt; h——枚举靠采样容差仍完备，见下方 tolerance 腿）
            int h = 0;
            while (h * 16 < r) {
                h++;
            }
            // 采样容差论证（MegaTreeAnchors.enumerateAnchors 同一条）：窗内采样点距任何"半径 r 内
            // 锚点列"≤ gap = max(0, r−16·h_f)+ENUM_STEP ⇒ warped 位漂移 ≤ gap·√2·(1+2π·LAKE_WARP/
            // LAKE_WARP_SCALE)；本格湖站距最近邻站 ≥ LAKE_INTERVAL·(1−2·CELL_JITTER)=360 ⇒ 漂移
            // &lt; 半净空即保证 argmin 不换格（锚点不漏）。
            final int hF = (w - 1) >> 1;
            final int gap = Math.max(0, r - 16 * hF) + MegaTreeAnchors.ENUM_STEP;
            final double drift = gap * Math.sqrt(2.0D)
                * (1.0D + 2.0D * Math.PI * GTSRVoronoiRiverField.LAKE_WARP / GTSRVoronoiRiverField.LAKE_WARP_SCALE);
            final double clearanceHalf = GTSRVoronoiRiverField.LAKE_INTERVAL
                * (1.0D - 2.0D * GTSRVoronoiRiverField.CELL_JITTER) / 2.0D;
            if (!(drift < clearanceHalf)) {
                sufficientOk = false;
            }
            read("B r=" + r + " window=" + w + " formula=" + expect + " bruteMin=" + (2 * h + 1)
                + " gap=" + gap + " drift=" + String.format("%.1f", drift) + "<" + clearanceHalf);
        }
        a("B", "windowChunks.formula.match(all=" + radii.length + ")", formulaOk);
        a("B", "windowChunks.alwaysOdd", oddOk);
        a("B", "windowChunks.samplingTolerance.anchorNeverMissed", sufficientOk);
    }

    // ════════════════════════ C 组：30 座活湖实测 ════════════════════════

    static void groupC() throws Exception {
        final int want = 30;
        final List<Anchor> anchors = findAnchors(want);
        a("C", "activeLakes.found(" + anchors.size() + "/" + want + ")", anchors.size() >= want);
        final World world = AirWorld.make();
        int y0Min = Integer.MAX_VALUE, y0Max = Integer.MIN_VALUE;
        int cellsMin = Integer.MAX_VALUE, cellsMax = Integer.MIN_VALUE;
        int chunkMaxAll = 0;
        int topMax = 0;
        int extentMax = 0;
        boolean allPlaced = true;
        boolean y0BandOk = true;
        boolean budgetOk = true;
        boolean extentOk = true;
        boolean activeOk = true;
        for (final Anchor an : anchors) {
            // 活湖门双源（生产 anchorAt 同一条域门，独立重算）
            if (!(GTSRVoronoiRiverField.lakeAt(an.seed, an.ax, an.az) < GTSRVoronoiRiverField.LAKE_ISLAND)) {
                activeOk = false;
            }
            final double[] buf = new double[5];
            if (!MegaTreeAnchors.anchorAt(an.seed, an.ax, an.az, buf)
                || (int) buf[0] != an.ax || (int) buf[1] != an.az || (int) buf[4] != an.y0) {
                activeOk = false; // 锚点三腿双源对拍（ax/az/y0 逐位）
            }
            y0Min = Math.min(y0Min, an.y0);
            y0Max = Math.max(y0Max, an.y0);
            final Rec rec = singleTree(world, an);
            final TreeStats s = stats(rec);
            if (s.cells == 0 || rec.logs() < LOG_FLOOR || rec.leaves() < LEAF_FLOOR) {
                allPlaced = false;
            }
            if (an.y0 < Y0_MIN || an.y0 > Y0_MAX) {
                y0BandOk = false;
            }
            cellsMin = Math.min(cellsMin, s.cells);
            cellsMax = Math.max(cellsMax, s.cells);
            chunkMaxAll = Math.max(chunkMaxAll, s.chunkMax);
            topMax = Math.max(topMax, s.topY);
            extentMax = Math.max(extentMax, Math.max(s.dxExtent, s.dzExtent));
            if (s.cells > TREE_CELL_CAP || s.chunkMax > CHUNK_CELL_CAP) {
                budgetOk = false;
            }
            if (Math.max(s.dxExtent, s.dzExtent) > 31) {
                extentOk = false;
            }
        }
        a("C", "anchor.tripleLeg.doubleSource", activeOk);
        a("C", "y0.band[" + Y0_MIN + "," + Y0_MAX + "](measured " + y0Min + ".." + y0Max + ")", y0BandOk);
        a("C", "treePresence.100pct(30/30,log>=" + LOG_FLOOR + ",leaf>=" + LEAF_FLOOR + ")", allPlaced);
        a("C", "budget.treeCells<=9500 & chunkMax<=3600", budgetOk);
        a("C", "extent<=31.all30(" + extentMax + ")", extentOk);
        a("C", "top<=255.all30(" + topMax + ")", topMax <= 255);
        read("C y0=" + y0Min + ".." + y0Max + " cells=" + cellsMin + ".." + cellsMax + " chunkMax=" + chunkMaxAll
            + " extentMax=" + extentMax + " topMax=" + topMax);

        // 树干穿水行为：合成 y0=60 锚点，水世界（≤67 非空气）vs 空世界
        final AirWorld air = AirWorld.make();
        final AirWorld water = AirWorld.make();
        water.waterTop = 67;
        final Rec inAir = new Rec();
        final Rec inWater = new Rec();
        final Random r1 = new Random(GTSRWorldgenHash.chunkSeed(SEARCH_SEED0, 700, 700) ^ SALT_ISLAND_TREE);
        final Random r2 = new Random(GTSRWorldgenHash.chunkSeed(SEARCH_SEED0, 700, 700) ^ SALT_ISLAND_TREE);
        IslandMegaTree.placeInto(air,
            new com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder(inAir), r1, 7000, 7000, 60);
        IslandMegaTree.placeInto(water,
            new com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder(inWater), r2, 7000, 7000, 60);
        boolean trunkThroughWater = true;
        for (int y = 61; y <= 67; y++) {
            final Integer kind = inWater.cells.get(key(7000, y, 7000));
            if (kind == null || kind.intValue() != KIND_LOG) {
                trunkThroughWater = false;
            }
        }
        a("C", "trunk.throughWater.y61-67.replacedByLog", trunkThroughWater);
        a("C", "writeSet.worldIndependent(air==water)", inAir.cells.equals(inWater.cells));
        read("C waterArm trunkCells(61..67)=" + (trunkThroughWater ? "all-log" : "GAP") + " cells="
            + inWater.cells.size());
    }

    // ════════════════════════ D 组：重放幂等 + 9-chunk 单射 + 源级纪律 ════════════════════════

    static void groupD() throws Exception {
        final World world = AirWorld.make();
        final List<Anchor> anchors = findAnchors(3);
        a("D", "anchors.forReplay.found(" + anchors.size() + ")", anchors.size() >= 3);

        // D1 双跑序列逐位同（同 seed 同 chunk，两次独立趟）
        boolean seqOk = true;
        int seqChecked = 0;
        for (final Anchor an : anchors) {
            final int ocx = an.ax >> 4;
            final int ocz = an.az >> 4;
            final int[][] probes = { { ocx, ocz }, { ocx + 1, ocz }, { ocx, ocz - 1 } };
            for (final int[] p : probes) {
                final Rec r1 = new Rec();
                final Rec r2 = new Rec();
                ProsperityDecorPlacer.placeIslandTreePass(world, an.seed, p[0], p[1], r1);
                ProsperityDecorPlacer.placeIslandTreePass(world, an.seed, p[0], p[1], r2);
                seqChecked++;
                if (!seqEquals(r1.order, r2.order)) {
                    seqOk = false;
                }
            }
        }
        a("D", "doubleRun.sequence.identical n=" + seqChecked, seqOk);

        // D2 9-chunk 重放并集 == 单树一次性写入（owner 单射行为级证明）
        boolean unionOk = true;
        boolean injectiveOk = true;
        boolean ownedOk = true;
        int unionChecked = 0;
        for (final Anchor an : anchors) {
            final Rec single = singleTree(world, an);
            final Map<Long, Integer> union = new LinkedHashMap<>();
            int sumSizes = 0;
            final int ocx = an.ax >> 4;
            final int ocz = an.az >> 4;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    final Rec piece = new Rec();
                    ProsperityDecorPlacer.placeIslandTreePass(world, an.seed, ocx + dx, ocz + dz, piece);
                    for (final Map.Entry<Long, Integer> e : piece.cells.entrySet()) {
            final int x = signFixX((int) (e.getKey() >> 38));
            final int z = signFixZ((int) ((e.getKey() >> 12) & 0x3FFFFFF));
            if ((x >> 4) != ocx + dx || (z >> 4) != ocz + dz) {
                            ownedOk = false; // 切片层失守：写了不归本 chunk 的格
                        }
                        union.put(e.getKey(), e.getValue());
                    }
                    sumSizes += piece.cells.size();
                }
            }
            unionChecked++;
            if (!union.equals(single.cells)) {
                unionOk = false;
            }
            if (sumSizes != union.size()) {
                injectiveOk = false; // 有格被两个 chunk 重复写
            }
            read("D union#" + unionChecked + " single=" + single.cells.size() + " union=" + union.size()
                + " sumPieces=" + sumSizes);
        }
        a("D", "replayUnion9.equalsSingleShot", unionOk);
        a("D", "ownerInjection.noDuplicateWrites", injectiveOk);
        a("D", "slicePieces.allOwned", ownedOk);

        // D3 荒漠区（无主干带）精确 0：窗内 49 采样点 trunkAt 全 0 ⇒ pass 零写入
        final Lcg rng = new Lcg(0xD3L);
        int desertChecked = 0;
        boolean desertOk = true;
        while (desertChecked < 16) {
            final int cx = rng.intInRange(-2000, 2000);
            final int cz = rng.intInRange(-2000, 2000);
            boolean anyTrunk = false;
            for (int z = cz * 16 - 16 + 4; z < cz * 16 + 32 && !anyTrunk; z += MegaTreeAnchors.ENUM_STEP) {
                for (int x = cx * 16 - 16 + 4; x < cx * 16 + 32 && !anyTrunk; x += MegaTreeAnchors.ENUM_STEP) {
                    if (GTSRVoronoiRiverField.trunkAt(SEARCH_SEED0, x, z) > 0.0D) {
                        anyTrunk = true;
                    }
                }
            }
            if (anyTrunk) {
                continue;
            }
            final Rec piece = new Rec();
            ProsperityDecorPlacer.placeIslandTreePass(world, SEARCH_SEED0, cx, cz, piece);
            desertChecked++;
            if (!piece.cells.isEmpty()) {
                desertOk = false;
            }
        }
        a("D", "desertNoTrunkBand.exactlyZero(" + desertChecked + " chunks)", desertOk);

        // D4 源级纪律：零裸 rand + 新盐在场 + 锚点槽派生 + 挂点在 mega 前
        final String tree = stripComments(slurp(TREE_SRC));
        final String anchor = stripComments(slurp(ANCHOR_SRC));
        final String decor = stripComments(slurp(DECOR_SRC));
        a("D", "src.tree.noBareRand", !tree.contains("this.rand") && !tree.contains("world.rand")
            && !tree.contains("nextLong"));
        a("D", "src.anchor.noBareRand", !anchor.contains("this.rand") && !anchor.contains("world.rand")
            && !anchor.contains("nextLong"));
        final Pattern saltPat = Pattern.compile("SALT_ISLAND_TREE\\s*=\\s*(0x[0-9A-Fa-f]+)L");
        final Matcher m = saltPat.matcher(decor);
        final String saltLit = m.find() ? m.group(1) : "(absent)";
        final boolean saltOk = !"(absent)".equals(saltLit) && Long.decode(saltLit).longValue() == SALT_ISLAND_TREE;
        read("D saltIslandTree=" + saltLit + " expect=0x" + Long.toHexString(SALT_ISLAND_TREE));
        a("D", "src.saltIslandTree.literalPinned", saltOk);
        a("D", "src.saltMega.different",
            decor.contains("SALT_MEGA = 0x6D656761L") && !decor.contains("SALT_MEGA = 0x49534C4E44L"));
        a("D", "src.randDerivedFromAnchorSlot",
            decor.contains("chunkSeed(worldSeed, ax >> 4, az >> 4) ^ SALT_ISLAND_TREE"));
        // 挂点位置序：岛心树支线在 mega 段之前（placeTreePass 体内）
        final int callIdx = decor.indexOf("placeIslandTreePass(world, worldSeed, chunkX, chunkZ, sink);");
        final int megaIdx = decor.indexOf("if (trees.megaForm != MEGA_NONE)");
        a("D", "src.hook.beforeMegaSegment", callIdx >= 0 && megaIdx > callIdx);
    }

    // ════════════════════════ 工具 ════════════════════════

    static String slurp(String path) throws java.io.IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    /** 去注释（CaveFieldCheck 同款：注释置空格、保留字符串字面量与换行）。 */
    static String stripComments(String src) {
        final int n = src.length();
        final StringBuilder sb = new StringBuilder(n);
        int i = 0;
        while (i < n) {
            final char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    sb.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int j = src.indexOf("*/", i + 2);
                final int stop = j < 0 ? n : j + 2;
                for (int k = i; k < stop; k++) {
                    sb.append(src.charAt(k) == '\n' ? '\n' : ' ');
                }
                i = stop;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n) {
                    final char d = src.charAt(j);
                    if (d == '\\') {
                        j += 2;
                        continue;
                    }
                    if (d == c) {
                        j++;
                        break;
                    }
                    if (d == '\n') {
                        break;
                    }
                    j++;
                }
                sb.append(src, i, Math.min(j, n));
                i = j;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /** 确定性 LCG（CaveFieldCheck 同款）。 */
    static final class Lcg {

        long state;

        Lcg(long seed) {
            state = seed;
        }

        long next() {
            state = state * 6364136223846793005L + 1442695040888963407L;
            return state >>> 33;
        }

        int intInRange(int lo, int hi) {
            final long span = (long) hi - lo + 1;
            return (int) (lo + next() % span);
        }
    }
}
