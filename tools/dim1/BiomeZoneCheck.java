import java.security.MessageDigest;
import java.util.Arrays;

import com.miaokatze.gtsr.common.dimension.framework.BiomeZoneSelector;

/**
 * S-A2 验收① 群系空间连贯分区自证（一次性自检 main，不进 jar，tools/ 惯例）：
 * 纯 JEP 330 单文件运行，<b>零 Minecraft 依赖</b>（selector 纯函数直接驱动）：
 * {@code java -cp build/classes/java/main tools/dim1/BiomeZoneCheck.java}
 * <p>
 * 断言面（plan §4 S-A2 + §12 修订第 6 条，zone cell=16 chunk、边带 12%）：
 * <ol>
 * <li>双跑逐字节一致：同一 seed 对同一样本窗两次 {@link BiomeZoneSelector#select} 全网格
 * 快照 MessageDigest.isEqual + SHA-256 打印（确定性、与调用顺序无关）；</li>
 * <li>zone 连片度：同 zone 最大 4-连通 chunk 簇 ≥ 阈值（cell=16 口径：主导 zone ≥ 8 cell 当量、
 * 每个 ≥10% 权重 zone ≥ 1 cell 当量），并对照旧 per-chunk 均匀掷骰基线（仅打印）；</li>
 * <li>边带比例：边带 chunk 中实际偏离基准 zone 的比例 ≈12%±（断言区间 [11%,13%]）；</li>
 * <li>权重分布：四群系实测占比对 45/30/15/10 偏差 ≤ 9 个百分点；附带 idStart 段断言
 * （返回下标恒在 [0,biomeCount)，biomeId=idStart+下标 恒在注册段内 → biomeWeight 消费面
 * 不回退 1.0）。</li>
 * </ol>
 */
public class BiomeZoneCheck {

    /** 样本窗边长（chunk）= 24 cell，覆盖 576 cell 使分布偏差 σ≈2pp。 */
    private static final int WINDOW = 24 * BiomeZoneSelector.ZONE_CELL_CHUNKS;
    private static final long[] SEEDS = { 12345L, -987654321L, 0x50524F53L };

    /** dim78 四群系权重（BiomeRustedSteppe/GearworkForest/BrassWastes/FumaroleSwamp.WEIGHT）。 */
    private static final int[] WEIGHTS = { 45, 30, 15, 10 };
    private static final int BIOME_COUNT = WEIGHTS.length;
    /** 与 Config.prosperityBiomeIdStart 默认一致（纯断言用，不引 config 类）。 */
    private static final int ID_START = 180;
    /** dim78 接线盐（CommonProxy S-A2 挂 selector 处同值）。 */
    private static final long SALT = 0x5A4F4E45L;

    private static final double BAND_RATIO_MIN = 0.11;
    private static final double BAND_RATIO_MAX = 0.13;
    private static final double WEIGHT_DEVIATION_MAX = 0.09;
    /** 连片度阈值（cell 当量）：主导 zone ≥ 8 cell，其余 zone ≥ 1 cell。 */
    private static final int DOMINANT_MIN_CELLS = 8;
    private static final int ANY_MIN_CELLS = 1;
    /** 孤岛 chunk（4 邻均异 zone）占比上限：分区方案应远低于旧方案基线。 */
    private static final double ISLAND_RATIO_MAX = 0.08;

    public static void main(String[] args) {
        if (BIOME_COUNT >= 256) {
            fail("biomeCount too large for byte snapshot");
        }
        for (final long seed : SEEDS) {
            checkSeed(seed);
        }
        contractChecks();
        System.out.println(
            "BIOMEZONE PASS: seeds=" + SEEDS.length + " window=" + WINDOW + "x" + WINDOW + " chunks cell="
                + BiomeZoneSelector.ZONE_CELL_CHUNKS + " band=12% — determinism/coherence/band/weights all green");
    }

    private static void checkSeed(long seed) {
        final int half = WINDOW / 2;
        // —— ① 双跑逐字节一致 ——
        final int[] run1 = sampleWindow(seed, half);
        final int[] run2 = sampleWindow(seed, half);
        final byte[] dump1 = toBytes(run1);
        final byte[] dump2 = toBytes(run2);
        if (!MessageDigest.isEqual(dump1, dump2)) {
            fail("double-run byte mismatch seed=" + seed);
        }
        final String sha = sha256Hex(dump1);

        // —— idStart 段断言：下标恒在注册段内（biomeWeight 消费面不回退 1.0） ——
        for (final int zone : run1) {
            if (zone < 0 || zone >= BIOME_COUNT) {
                fail("zone index out of [0," + BIOME_COUNT + "): " + zone + " seed=" + seed);
            }
            final int biomeId = ID_START + zone;
            if (biomeId < ID_START || biomeId >= ID_START + BIOME_COUNT) {
                fail("biomeId out of registration band: " + biomeId + " seed=" + seed);
            }
        }

        // —— ③ 边带比例 ≈12%± ——
        long bandChunks = 0;
        long deviated = 0;
        for (int z = 0; z < WINDOW; z++) {
            for (int x = 0; x < WINDOW; x++) {
                final int cx = x - half;
                final int cz = z - half;
                if (!BiomeZoneSelector.isEdgeBand(cx, cz, BiomeZoneSelector.ZONE_CELL_CHUNKS)) {
                    continue;
                }
                bandChunks++;
                final int base = BiomeZoneSelector.zoneOfCell(
                    seed,
                    Math.floorDiv(cx, BiomeZoneSelector.ZONE_CELL_CHUNKS),
                    Math.floorDiv(cz, BiomeZoneSelector.ZONE_CELL_CHUNKS),
                    BIOME_COUNT,
                    WEIGHTS,
                    SALT);
                if (run1[x + z * WINDOW] != base) {
                    deviated++;
                }
            }
        }
        final double bandRatio = (double) deviated / (double) bandChunks;
        if (bandRatio < BAND_RATIO_MIN || bandRatio > BAND_RATIO_MAX) {
            fail(
                "edge-band ratio " + bandRatio + " outside [" + BAND_RATIO_MIN + "," + BAND_RATIO_MAX + "] seed="
                    + seed);
        }

        // —— ② 连片度（4-连通最大簇）——
        final int[] clusterSizes = largestClusters(run1);
        final int totalWeight = Arrays.stream(WEIGHTS).sum();
        int dominant = 0;
        for (int i = 1; i < BIOME_COUNT; i++) {
            if (WEIGHTS[i] > WEIGHTS[dominant]) {
                dominant = i;
            }
        }
        final int chunkPerCell = BiomeZoneSelector.ZONE_CELL_CHUNKS * BiomeZoneSelector.ZONE_CELL_CHUNKS;
        if (clusterSizes[dominant] < (long) DOMINANT_MIN_CELLS * chunkPerCell) {
            fail(
                "dominant zone " + dominant + " largest cluster " + clusterSizes[dominant] + " chunks < "
                    + DOMINANT_MIN_CELLS
                    + " cells seed=" + seed);
        }
        for (int i = 0; i < BIOME_COUNT; i++) {
            if (WEIGHTS[i] * 10 >= totalWeight && clusterSizes[i] < (long) ANY_MIN_CELLS * chunkPerCell) {
                fail("zone " + i + " largest cluster " + clusterSizes[i] + " < 1 cell seed=" + seed);
            }
        }

        // —— 孤岛占比（盐胡椒度反指标）——
        final double islandRatio = islandRatio(run1);
        if (islandRatio > ISLAND_RATIO_MAX) {
            fail("single-island chunk ratio " + islandRatio + " > " + ISLAND_RATIO_MAX + " seed=" + seed);
        }

        // —— ④ 权重分布偏差 ——
        final long[] counts = new long[BIOME_COUNT];
        for (final int zone : run1) {
            counts[zone]++;
        }
        final StringBuilder shares = new StringBuilder();
        for (int i = 0; i < BIOME_COUNT; i++) {
            final double expected = (double) WEIGHTS[i] / totalWeight;
            final double actual = (double) counts[i] / run1.length;
            final double deviation = Math.abs(actual - expected);
            if (deviation > WEIGHT_DEVIATION_MAX) {
                fail(
                    "zone " + i + " share " + actual + " deviates " + deviation + " from " + expected + " > "
                        + WEIGHT_DEVIATION_MAX + " seed=" + seed);
            }
            shares.append(actual);
            if (i < BIOME_COUNT - 1) {
                shares.append('/');
            }
        }

        System.out.println(
            "BIOMEZONE PASS seed=" + seed + ": sha256=" + sha.substring(0, 16) + "… bandRatio=" + bandRatio
                + " (deviated=" + deviated + "/" + bandChunks + ") clusters=" + Arrays.toString(clusterSizes)
                + " chunks islandRatio=" + islandRatio + " shares=" + shares);

        // —— 对照基线（仅打印，不作断言）：旧 per-chunk 均匀掷骰同窗指标 ——
        printLegacyContrast(seed, half, totalWeight);
    }

    /** 同一样本窗逐 chunk 跑两次 {@link BiomeZoneSelector#select} 的快照。 */
    private static int[] sampleWindow(long seed, int half) {
        final int[] grid = new int[WINDOW * WINDOW];
        for (int z = 0; z < WINDOW; z++) {
            for (int x = 0; x < WINDOW; x++) {
                grid[x + z * WINDOW] = BiomeZoneSelector
                    .select(seed, x - half, z - half, BIOME_COUNT, WEIGHTS, BiomeZoneSelector.ZONE_CELL_CHUNKS, SALT);
            }
        }
        return grid;
    }

    /** 各 zone 最大 4-连通簇（chunk 数；迭代 flood fill，无递归）。 */
    private static int[] largestClusters(int[] grid) {
        final int[] sizes = new int[BIOME_COUNT];
        final boolean[] visited = new boolean[grid.length];
        final int[] stack = new int[grid.length];
        for (int start = 0; start < grid.length; start++) {
            if (visited[start]) {
                continue;
            }
            final int zone = grid[start];
            int top = 0;
            stack[top++] = start;
            visited[start] = true;
            int size = 0;
            while (top > 0) {
                final int idx = stack[--top];
                size++;
                final int x = idx % WINDOW;
                final int z = idx / WINDOW;
                if (x > 0 && !visited[idx - 1] && grid[idx - 1] == zone) {
                    visited[idx - 1] = true;
                    stack[top++] = idx - 1;
                }
                if (x < WINDOW - 1 && !visited[idx + 1] && grid[idx + 1] == zone) {
                    visited[idx + 1] = true;
                    stack[top++] = idx + 1;
                }
                if (z > 0 && !visited[idx - WINDOW] && grid[idx - WINDOW] == zone) {
                    visited[idx - WINDOW] = true;
                    stack[top++] = idx - WINDOW;
                }
                if (z < WINDOW - 1 && !visited[idx + WINDOW] && grid[idx + WINDOW] == zone) {
                    visited[idx + WINDOW] = true;
                    stack[top++] = idx + WINDOW;
                }
            }
            if (size > sizes[zone]) {
                sizes[zone] = size;
            }
        }
        return sizes;
    }

    /** 内部 chunk 中 4 邻均异 zone 的孤岛占比（盐胡椒度反指标）。 */
    private static double islandRatio(int[] grid) {
        long interior = 0;
        long islands = 0;
        for (int z = 1; z < WINDOW - 1; z++) {
            for (int x = 1; x < WINDOW - 1; x++) {
                final int idx = x + z * WINDOW;
                final int zone = grid[idx];
                interior++;
                if (grid[idx - 1] != zone && grid[idx + 1] != zone && grid[idx - WINDOW] != zone
                    && grid[idx + WINDOW] != zone) {
                    islands++;
                }
            }
        }
        return (double) islands / (double) interior;
    }

    /** 旧 per-chunk 均匀掷骰复刻（GTSRWorldChunkManager.hash 同族，仅对照打印，零 MC 依赖）。 */
    private static void printLegacyContrast(long seed, int half, int totalWeight) {
        final int[] grid = new int[WINDOW * WINDOW];
        final int[] cumulative = new int[BIOME_COUNT];
        int acc = 0;
        for (int i = 0; i < BIOME_COUNT; i++) {
            acc += WEIGHTS[i];
            cumulative[i] = acc;
        }
        for (int z = 0; z < WINDOW; z++) {
            for (int x = 0; x < WINDOW; x++) {
                long h = seed ^ ((long) (x - half) * 0x9E3779B97F4A7C15L) ^ ((long) (z - half) * 0xBF58476D1CE4E5B9L);
                h ^= h >>> 33;
                h *= 0xFF51AFD7ED558CCDL;
                h ^= h >>> 33;
                h *= 0xC4CEB9FE1A85EC53L;
                h ^= h >>> 33;
                final int r = (int) (h & 0x7FFFFFFFL) % totalWeight;
                int zone = BIOME_COUNT - 1;
                for (int i = 0; i < BIOME_COUNT; i++) {
                    if (r < cumulative[i]) {
                        zone = i;
                        break;
                    }
                }
                grid[x + z * WINDOW] = zone;
            }
        }
        final int[] clusters = largestClusters(grid);
        int dominant = 0;
        for (int i = 1; i < BIOME_COUNT; i++) {
            if (clusters[i] > clusters[dominant]) {
                dominant = i;
            }
        }
        System.out.println(
            "BIOMEZONE legacy-baseline (per-chunk uniform, contrast only): largestClusterAnyZone="
                + clusters[dominant]
                + " chunks islandRatio=" + islandRatio(grid));
    }

    /** 契约防御：非法入参 fail fast、null 权重表均匀回退。 */
    private static void contractChecks() {
        expectThrows(() -> BiomeZoneSelector.select(1L, 0, 0, 0, WEIGHTS, 16, SALT), "biomeCount=0");
        expectThrows(() -> BiomeZoneSelector.select(1L, 0, 0, 4, WEIGHTS, 0, SALT), "cell=0");
        expectThrows(() -> BiomeZoneSelector.zoneOfCell(1L, 0, 0, 0, WEIGHTS, SALT), "zoneOfCell biomeCount=0");
        expectThrows(() -> BiomeZoneSelector.isEdgeBand(0, 0, 0), "isEdgeBand cell=0");
        final int zone = BiomeZoneSelector.select(42L, -77, 123, 3, null, 8, SALT);
        if (zone < 0 || zone >= 3) {
            fail("null-weights uniform fallback out of range: " + zone);
        }
        System.out.println("BIOMEZONE CONTRACT PASS: invalid-args fail fast, null-weights uniform fallback");
    }

    private static void expectThrows(Runnable call, String label) {
        try {
            call.run();
        } catch (final IllegalArgumentException expected) {
            return;
        } catch (final RuntimeException unexpected) {
            fail(label + " threw unexpected " + unexpected.getClass().getSimpleName());
            return;
        }
        fail(label + " did not throw IllegalArgumentException");
    }

    private static byte[] toBytes(int[] grid) {
        final byte[] out = new byte[grid.length];
        for (int i = 0; i < grid.length; i++) {
            out[i] = (byte) grid[i];
        }
        return out;
    }

    private static String sha256Hex(byte[] data) {
        final StringBuilder sb = new StringBuilder();
        for (final byte b : sha256(data)) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (final java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void fail(String msg) {
        System.out.println("BIOMEZONE FAIL: " + msg);
        System.exit(1);
    }
}
