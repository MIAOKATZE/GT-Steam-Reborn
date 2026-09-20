package com.miaokatze.gtsr.common.dimension.framework.genlayer;

import java.util.Arrays;

/**
 * GenLayer 链离线自测（切片 A；包内 main，不接入构建主流程，不进任何运行期路径）。
 * <p>
 * 对多 seed × 正/负象限采样粗层，断言四件事（tools/dim1 断言脚本风格的 fail-fast main）：
 * <ol>
 * <li><b>确定性</b>：同 seed 重建链两次全窗逐位一致；单点小窗采样与整窗采样逐位一致
 * （粗/细两面都查——B1 三面同解依赖的纯函数语义）；</li>
 * <li><b>均分</b>：各 id 实测占比对 1/N 偏差 ≤ 阈值（zoom 的中心多数票对边际分布有轻微
 * 收敛偏置，阈值留裕量）；</li>
 * <li><b>成片/非噪点</b>：主导 id 最大 4-连通域 ≥ 阈值（粗格），孤岛格（4 邻全异）占比 ≤ 阈值；</li>
 * <li><b>边界非直线</b>：相邻行"变道位置集合"完全相同的比例 ≤ 阈值（条纹/直线边界的特征是
 * 变道位置恒定；voronoi 有机边界应显著低于）；附带细层 voronoi 抖动率（细层 ≠ 平铺粗层的
 * 比例，证明 voronoi 真实生效而非恒等）。</li>
 * </ol>
 * 运行需要 MC 类路径（链复用 vanilla GenLayer），例：
 * {@code java -cp build/classes/java/main;<minecraft+deps> com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerSelfTest}
 * 纯 int 语义，不触碰 BiomeGenBase 注册表，id 用 [0,254] 内哑元（对齐 BiomeZoneCheck 的 ID_START=180）。
 */
public final class GTSRGenLayerSelfTest {

    /** 粗层采样窗边长（粗格；256² = 512×512 方块 ⇒ 16×16 个 selector 格，占比 σ≈2.7pp）。 */
    private static final int COARSE_WINDOW = 256;
    /** 细层采样窗边长（方块，1:1；覆盖 16×16 粗格）。 */
    private static final int FINE_WINDOW = 64;
    /** 单点一致性抽查步长（粗格；跨步覆盖不同 (x,z) 奇偶性）。 */
    private static final int POINT_STRIDE = 7;

    private static final long[] SEEDS = { 12345L, -987654321L, 0x50524F53L };
    /** 两个象限原点（粗格）：0 与负半轴，覆盖负坐标 >> 2 与 floorDiv 路径。 */
    private static final int[][] OFFSETS = { { 0, 0 }, { -128, -96 } };

    /** 四群系哑元 id（等权轮盘；与 tools/dim1/BiomeZoneCheck 的 ID_START 段对齐，纯 int 不注册）。 */
    private static final int[] BIOME_IDS = { 180, 181, 182, 183 };

    private static final double UNIFORM_TOL = 0.12;
    private static final int DOMINANT_COMPONENT_MIN = 64;
    private static final double ISLAND_RATIO_MAX = 0.10;
    private static final double STRAIGHT_ROW_MAX = 0.10;
    /**
     * voronoi 抖动率界：细层 ≠ 平铺粗层的比例。vanilla 抖动 ±1.8/4 格下实测 1.4%~3.9%
     * （只有 4×4 格边界附近的方块会被邻角抢走），下界取 0.5% 只为证明 voronoi 真实生效
     * （恒等映射会精确得 0），上界 50% 防退化成噪点。
     */
    private static final double VORONOI_JITTER_MIN = 0.005;
    private static final double VORONOI_JITTER_MAX = 0.50;

    private GTSRGenLayerSelfTest() {}

    public static void main(String[] args) {
        constructorValidation();
        for (final long seed : SEEDS) {
            for (final int[] offset : OFFSETS) {
                checkRegion(seed, offset[0], offset[1]);
            }
        }
        zoomVariantSmoke();
        System.out.println(
            "GENLAYER PASS: seeds=" + SEEDS.length
                + " offsets="
                + OFFSETS.length
                + " coarseWindow="
                + COARSE_WINDOW
                + "x"
                + COARSE_WINDOW
                + " zoomLevels="
                + GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS
                + " ids="
                + Arrays.toString(BIOME_IDS)
                + " — determinism/uniformity/coherence/non-straight-boundary all green");
    }

    private static void checkRegion(long seed, int ox, int oz) {
        final GTSRGenLayerChain chain = new GTSRGenLayerChain(seed, BIOME_IDS);
        final GTSRGenLayerChain rebuild = new GTSRGenLayerChain(seed, BIOME_IDS);
        final int[] coarse = chain.coarseInts(ox, oz, COARSE_WINDOW, COARSE_WINDOW);
        // 快照取逻辑窗长 W*H：IntCache 底数组可能更长，尾部是未写的陈旧格
        final int[] snapshot = Arrays.copyOf(coarse, COARSE_WINDOW * COARSE_WINDOW);
        final int[] coarseAgain = rebuild.coarseInts(ox, oz, COARSE_WINDOW, COARSE_WINDOW);
        if (!regionEquals(snapshot, coarseAgain, COARSE_WINDOW * COARSE_WINDOW)) {
            fail(seed, ox, oz, "same-seed rebuild mismatch (chain not deterministic)");
        }
        // 单点小窗 vs 整窗（粗面；chunk 代表点口径 blockX = coarseX << 2）
        for (int z = 0; z < COARSE_WINDOW; z += POINT_STRIDE) {
            for (int x = 0; x < COARSE_WINDOW; x += POINT_STRIDE) {
                final int point = chain.biomeAtCoarse((ox + x) << 2, (oz + z) << 2);
                if (point != snapshot[x + z * COARSE_WINDOW]) {
                    fail(seed, ox, oz, "coarse single-point != window at coarse(" + (ox + x) + "," + (oz + z) + ")");
                }
            }
        }
        statsAndAsserts(seed, ox, oz, snapshot);
        fineFaceChecks(seed, ox, oz, chain, snapshot);
    }

    private static void statsAndAsserts(long seed, int ox, int oz, int[] coarse) {
        final int n = BIOME_IDS.length;
        final int total = coarse.length;
        final int[] counts = new int[n];
        for (final int id : coarse) {
            counts[indexOf(id)]++;
        }
        // 均分
        final StringBuilder shares = new StringBuilder();
        for (int i = 0; i < n; i++) {
            final double share = (double) counts[i] / total;
            shares.append(String.format("%s=%.1f%%", shortId(BIOME_IDS[i]), share * 100));
            if (Math.abs(share - 1.0 / n) > UNIFORM_TOL) {
                fail(seed, ox, oz, "uniformity broken: id " + BIOME_IDS[i] + " share " + share);
            }
            if (i < n - 1) {
                shares.append(' ');
            }
        }
        // 成片：主导 id 最大 4-连通域 + 孤岛率
        int dominant = 0;
        for (int i = 1; i < n; i++) {
            if (counts[i] > counts[dominant]) {
                dominant = i;
            }
        }
        final int largest = largestComponent(coarse, BIOME_IDS[dominant]);
        if (largest < DOMINANT_COMPONENT_MIN) {
            fail(seed, ox, oz, "dominant id " + BIOME_IDS[dominant] + " largest component " + largest);
        }
        final double islands = islandRatio(coarse);
        if (islands > ISLAND_RATIO_MAX) {
            fail(seed, ox, oz, "island ratio " + islands + " > " + ISLAND_RATIO_MAX);
        }
        // 边界非直线
        final double straight = straightRowRatio(coarse);
        if (straight > STRAIGHT_ROW_MAX) {
            fail(seed, ox, oz, "straight-boundary row ratio " + straight + " > " + STRAIGHT_ROW_MAX);
        }
        System.out.printf(
            "seed=%d off=(%d,%d) %s largestComp(%d)=%d cells(=%.0f blocks) islands=%.1f%% straightRows=%.1f%%%n",
            seed,
            ox,
            oz,
            shares,
            BIOME_IDS[dominant],
            largest,
            largest * 4.0,
            islands * 100,
            straight * 100);
    }

    private static void fineFaceChecks(long seed, int ox, int oz, GTSRGenLayerChain chain, int[] coarseSnapshot) {
        final int bx0 = ox << 2;
        final int bz0 = oz << 2;
        final int[] fine = chain.fineInts(bx0, bz0, FINE_WINDOW, FINE_WINDOW);
        // 细面确定性 + 单点一致性（小步距抽查）；快照同样取逻辑窗长
        final int[] fineCopy = Arrays.copyOf(fine, FINE_WINDOW * FINE_WINDOW);
        final GTSRGenLayerChain rebuild = new GTSRGenLayerChain(seed, BIOME_IDS);
        final int[] fineAgain = rebuild.fineInts(bx0, bz0, FINE_WINDOW, FINE_WINDOW);
        if (!regionEquals(fineCopy, fineAgain, FINE_WINDOW * FINE_WINDOW)) {
            fail(seed, ox, oz, "fine face rebuild mismatch");
        }
        for (int z = 0; z < FINE_WINDOW; z += POINT_STRIDE) {
            for (int x = 0; x < FINE_WINDOW; x += POINT_STRIDE) {
                if (chain.biomeAtFine(bx0 + x, bz0 + z) != fineCopy[x + z * FINE_WINDOW]) {
                    fail(seed, ox, oz, "fine single-point != window at block(" + (bx0 + x) + "," + (bz0 + z) + ")");
                }
            }
        }
        // 细层 id 全部 ∈ 入参集合（防御钳制路径正常时不触发；这里显式复核）
        int jitter = 0;
        for (int z = 0; z < FINE_WINDOW; z++) {
            for (int x = 0; x < FINE_WINDOW; x++) {
                final int id = fineCopy[x + z * FINE_WINDOW];
                if (indexOf(id) < 0) {
                    fail(seed, ox, oz, "fine id " + id + " outside input set");
                }
                final int tiled = coarseSnapshot[(x >> 2) + (z >> 2) * COARSE_WINDOW];
                if (id != tiled) {
                    jitter++;
                }
            }
        }
        final double ratio = (double) jitter / fineCopy.length;
        if (ratio < VORONOI_JITTER_MIN || ratio > VORONOI_JITTER_MAX) {
            fail(
                seed,
                ox,
                oz,
                "voronoi jitter ratio " + ratio + " outside [" + VORONOI_JITTER_MIN + "," + VORONOI_JITTER_MAX + "]");
        }
        System.out.printf("seed=%d off=(%d,%d) fine: ids-valid, voronoiJitter=%.1f%%%n", seed, ox, oz, ratio * 100);
    }

    /** 构造期 fail fast 契约：空表/越界 id/越界 zoomLevels 必须抛 IllegalArgumentException。 */
    private static void constructorValidation() {
        expectThrow("null biomeIds", () -> new GTSRGenLayerChain(1L, null));
        expectThrow("empty biomeIds", () -> new GTSRGenLayerChain(1L, new int[0]));
        expectThrow("id 255 (vanilla sentinel)", () -> new GTSRGenLayerChain(1L, new int[] { 10, 255 }));
        expectThrow("id 256 (byte aliasing)", () -> new GTSRGenLayerChain(1L, new int[] { 256 }));
        expectThrow("negative id", () -> new GTSRGenLayerChain(1L, new int[] { -1 }));
        expectThrow("zoomLevels -1", () -> new GTSRGenLayerChain(1L, BIOME_IDS, -1));
        expectThrow("zoomLevels 9", () -> new GTSRGenLayerChain(1L, BIOME_IDS, 9));
    }

    /** zoom 档位变体冒烟：0/1/6 可构造、确定性、输出 id 合法。 */
    private static void zoomVariantSmoke() {
        for (final int zoom : new int[] { 0, 1, 6 }) {
            final GTSRGenLayerChain a = new GTSRGenLayerChain(42L, BIOME_IDS, zoom);
            final GTSRGenLayerChain b = new GTSRGenLayerChain(42L, BIOME_IDS, zoom);
            final int[] wa = a.coarseInts(-8, -8, 64, 64);
            final int[] sa = Arrays.copyOf(wa, 64 * 64);
            final int[] wb = b.coarseInts(-8, -8, 64, 64);
            if (!regionEquals(sa, wb, 64 * 64)) {
                fail(42L, -8, -8, "zoom=" + zoom + " rebuild mismatch");
            }
            for (final int id : sa) {
                if (indexOf(id) < 0) {
                    fail(42L, -8, -8, "zoom=" + zoom + " id " + id + " outside set");
                }
            }
            if (a.zoomLevels() != zoom) {
                fail(42L, -8, -8, "zoomLevels() mismatch");
            }
        }
    }

    /** 指定 id 的最大 4-连通域大小（BFS，粗格网格）。 */
    private static int largestComponent(int[] grid, int id) {
        final int w = COARSE_WINDOW;
        final boolean[] seen = new boolean[grid.length];
        final int[] queue = new int[grid.length];
        int best = 0;
        for (int start = 0; start < grid.length; start++) {
            if (seen[start] || grid[start] != id) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            int size = 0;
            while (head < tail) {
                final int cell = queue[head++];
                size++;
                final int cx = cell % w;
                final int cz = cell / w;
                if (cx > 0 && !seen[cell - 1] && grid[cell - 1] == id) {
                    seen[cell - 1] = true;
                    queue[tail++] = cell - 1;
                }
                if (cx < w - 1 && !seen[cell + 1] && grid[cell + 1] == id) {
                    seen[cell + 1] = true;
                    queue[tail++] = cell + 1;
                }
                if (cz > 0 && !seen[cell - w] && grid[cell - w] == id) {
                    seen[cell - w] = true;
                    queue[tail++] = cell - w;
                }
                if (cz < w - 1 && !seen[cell + w] && grid[cell + w] == id) {
                    seen[cell + w] = true;
                    queue[tail++] = cell + w;
                }
            }
            if (size > best) {
                best = size;
            }
        }
        return best;
    }

    /** 孤岛率：4 邻均与本格不同 id 的格子占比（盐胡椒特征）。 */
    private static double islandRatio(int[] grid) {
        final int w = COARSE_WINDOW;
        int islands = 0;
        for (int z = 0; z < w; z++) {
            for (int x = 0; x < w; x++) {
                final int id = grid[x + z * w];
                final boolean loneWest = x == 0 || grid[x - 1 + z * w] != id;
                final boolean loneEast = x == w - 1 || grid[x + 1 + z * w] != id;
                final boolean loneNorth = z == 0 || grid[x + (z - 1) * w] != id;
                final boolean loneSouth = z == w - 1 || grid[x + (z + 1) * w] != id;
                if (loneWest && loneEast && loneNorth && loneSouth) {
                    islands++;
                }
            }
        }
        return (double) islands / grid.length;
    }

    /**
     * 边界直线度：相邻两行的"变道位置集合"（行内 id 发生变化的 x 下标集）完全相同的行对占比。
     * 直线条纹图该值 → 1；有机边界应显著低。双方都无变道的行对不计入分母。
     */
    private static double straightRowRatio(int[] grid) {
        final int w = COARSE_WINDOW;
        boolean[] prev = null;
        int straight = 0;
        int compared = 0;
        for (int z = 0; z < w; z++) {
            final boolean[] changes = new boolean[w];
            int changeCount = 0;
            for (int x = 1; x < w; x++) {
                if (grid[x + z * w] != grid[x - 1 + z * w]) {
                    changes[x] = true;
                    changeCount++;
                }
            }
            if (prev != null && (changeCount > 0 || anyTrue(prev))) {
                compared++;
                if (Arrays.equals(changes, prev)) {
                    straight++;
                }
            }
            prev = changes;
        }
        return compared == 0 ? 0.0 : (double) straight / compared;
    }

    private static boolean anyTrue(boolean[] flags) {
        for (final boolean flag : flags) {
            if (flag) {
                return true;
            }
        }
        return false;
    }

    /**
     * 前 {@code length} 格逐位相等（Java 8 无 Arrays.equals 区间重载；两数组均可能来自
     * IntCache、长于逻辑窗，只比逻辑区）。
     */
    private static boolean regionEquals(int[] a, int[] b, int length) {
        for (int i = 0; i < length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(int id) {
        for (int i = 0; i < BIOME_IDS.length; i++) {
            if (BIOME_IDS[i] == id) {
                return i;
            }
        }
        return -1;
    }

    private static String shortId(int id) {
        return "id" + id;
    }

    private static void expectThrow(String what, Runnable construction) {
        try {
            construction.run();
        } catch (final IllegalArgumentException expected) {
            return;
        }
        throw new IllegalStateException("constructor validation missing for: " + what);
    }

    private static void fail(long seed, int ox, int oz, String message) {
        throw new AssertionError("GENLAYER FAIL seed=" + seed + " off=(" + ox + "," + oz + "): " + message);
    }
}
