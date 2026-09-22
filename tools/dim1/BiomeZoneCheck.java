import java.security.MessageDigest;

import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;

/**
 * 群系空间连贯分区自证（tools/ 惯例的一次性自检 main；<b>B2 起改钉 GenLayer 链</b>）。
 * <p>
 * S-A2/P6 时代的对象是 {@code BiomeZoneSelector}（cell 权重掷骰 + 边带 12% 碎斑），B2 已整体退役；
 * 身份面唯一出口是 {@link GTSRGenLayerChain}（等权轮盘 + Zoom×DEFAULT_ZOOM_LEVELS + Smooth 粗层；
 * P17 S-A 起该档 = 5）。本检查改钉
 * <b>chunk 代表点采样口径</b>下的链分区质量——即 {@code GTSRWorldChunkManager.biomeAt} 与
 * {@code CityPlanner.bandIndexAt} 实际消费的那一面（每 chunk 一次 {@code biomeAtCoarse((cx<<4)+8,
 * (cz<<4)+8)}）；链自身的粗格性质（确定性/均分/连通域/直线边界/voronoi 抖动/构造期契约）由
 * {@code com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerSelfTest}（surface_checks [2k]）
 * 包内自测覆盖，此处不重复。
 * <p>
 * 零装配（不触 BiomeGenBase 注册表/L1 账本）：id 用 [0,254] 内哑元（对齐注册段 idStart=180），
 * 纯 int 语义，{@code java -cp build/classes/java/main;<MC+deps> tools/dim1/BiomeZoneCheck.java}。
 * <p>
 * 断言面（B2 链口径）：
 * <ol>
 * <li>双跑逐字节一致：同 seed 两次全窗快照 MessageDigest.isEqual + SHA-256 打印（确定性）；</li>
 * <li>连片度：同 id 最大 4-连通 chunk 簇 ≥ 阈值（zoom 成片性），孤岛 chunk 占比 ≤ 8%；</li>
 * <li>等权分布：四 id 实测占比对 25% 偏差 ≤ 8pp（zoom 中心多数票有轻微收敛偏置，阈值留裕量）；</li>
 * <li>段断言：输出 id 恒 ∈ 入参集合（biomeWeight 消费面不回退 1.0 的同款 id 段守卫）。</li>
 * </ol>
 */
public class BiomeZoneCheck {

    /** 样本窗边长（chunk）；384² = 147456 chunk 采样，等权份额 σ≈0.11pp。 */
    private static final int WINDOW = 384;
    private static final long[] SEEDS = { 12345L, -987654321L, 0x50524F53L };

    /** 四群系哑元 id（等权轮盘；与注册段 idStart=180 对齐，纯 int 不注册）。 */
    private static final int[] BIOME_IDS = { 180, 181, 182, 183 };
    private static final int BIOME_COUNT = BIOME_IDS.length;

    private static final double SHARE_DEVIATION_MAX = 0.08D;
    /** 连片度阈值（chunk）：特征片 ≥ 数百 chunk 量级，留足下限（P17 S-A zoom 4→5 后实测 1480–2832）。 */
    private static final long DOMINANT_MIN_CHUNKS = 256L;
    /** 孤岛 chunk（4 邻均异 id）占比上限：成片分区的盐胡椒反指标。 */
    private static final double ISLAND_RATIO_MAX = 0.08D;

    public static void main(String[] args) {
        if (BIOME_COUNT >= 256) {
            fail("biomeCount too large for byte snapshot");
        }
        for (final long seed : SEEDS) {
            checkSeed(seed);
        }
        contractChecks();
        System.out.println(
            "BIOMEZONE PASS: seeds=" + SEEDS.length + " window=" + WINDOW + "x" + WINDOW + " chunks"
                + " zoomLevels=" + GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS
                + " — determinism/coherence/equal-shares/id-band all green (B2 chain face)");
    }

    private static void checkSeed(long seed) {
        final int half = WINDOW / 2;
        // —— ① 双跑逐字节一致（chunk 代表点口径与 biomeAt/bandIndexAt 同式）——
        final int[] run1 = sampleWindow(seed, half);
        final int[] run2 = sampleWindow(seed, half);
        final byte[] dump1 = toBytes(run1);
        final byte[] dump2 = toBytes(run2);
        if (!MessageDigest.isEqual(dump1, dump2)) {
            fail("double-run byte mismatch seed=" + seed);
        }
        final String sha = sha256Hex(dump1);

        // —— ④ id 段断言：链输出恒 ∈ 入参集合（防御钳制不触发时的显式复核）——
        for (final int id : run1) {
            if (indexOf(id) < 0) {
                fail("chain id " + id + " outside input set " + java.util.Arrays.toString(BIOME_IDS) + " seed=" + seed);
            }
        }

        // —— ② 连片度（4-连通最大簇）+ 孤岛占比 ——
        final long[] clusterSizes = largestClusters(run1);
        int dominant = 0;
        for (int i = 1; i < BIOME_COUNT; i++) {
            if (clusterSizes[i] > clusterSizes[dominant]) {
                dominant = i;
            }
        }
        if (clusterSizes[dominant] < DOMINANT_MIN_CHUNKS) {
            fail("dominant id " + BIOME_IDS[dominant] + " largest cluster " + clusterSizes[dominant]
                + " chunks < " + DOMINANT_MIN_CHUNKS + " seed=" + seed);
        }
        for (int i = 0; i < BIOME_COUNT; i++) {
            if (clusterSizes[i] <= 0L) {
                fail("id " + BIOME_IDS[i] + " never appears seed=" + seed);
            }
        }
        final double islandRatio = islandRatio(run1);
        if (islandRatio > ISLAND_RATIO_MAX) {
            fail("single-island chunk ratio " + islandRatio + " > " + ISLAND_RATIO_MAX + " seed=" + seed);
        }

        // —— ③ 等权分布偏差（25% 基准）——
        final long[] counts = new long[BIOME_COUNT];
        for (final int id : run1) {
            counts[indexOf(id)]++;
        }
        final StringBuilder shares = new StringBuilder();
        for (int i = 0; i < BIOME_COUNT; i++) {
            final double actual = (double) counts[i] / run1.length;
            final double deviation = Math.abs(actual - 1.0D / BIOME_COUNT);
            if (deviation > SHARE_DEVIATION_MAX) {
                fail("id " + BIOME_IDS[i] + " share " + actual + " deviates " + deviation + " from "
                    + (1.0D / BIOME_COUNT) + " > " + SHARE_DEVIATION_MAX + " seed=" + seed);
            }
            shares.append(String.format("%.3f", actual));
            if (i < BIOME_COUNT - 1) {
                shares.append('/');
            }
        }

        System.out.println(
            "BIOMEZONE PASS seed=" + seed + ": sha256=" + sha.substring(0, 16) + "… clusters="
                + java.util.Arrays.toString(clusterSizes) + " chunks islandRatio=" + islandRatio
                + " shares=" + shares);
    }

    /**
     * 同一样本窗逐 chunk 的身份快照——采样式与生产两口（{@code GTSRWorldChunkManager.biomeAt} /
     * {@code CityPlanner.bandIndexAt}）逐字同款：chunk 中心块 {@code (cx<<4)+8, (cz<<4)+8} 喂
     * {@link GTSRGenLayerChain#biomeAtCoarse(int, int)}。
     */
    private static int[] sampleWindow(long seed, int half) {
        final GTSRGenLayerChain chain = new GTSRGenLayerChain(seed, BIOME_IDS);
        final int[] grid = new int[WINDOW * WINDOW];
        for (int z = 0; z < WINDOW; z++) {
            for (int x = 0; x < WINDOW; x++) {
                grid[x + z * WINDOW] = chain.biomeAtCoarse(((x - half) << 4) + 8, ((z - half) << 4) + 8);
            }
        }
        return grid;
    }

    /** 各 id 最大 4-连通簇（chunk 数；迭代 flood fill，无递归）。 */
    private static long[] largestClusters(int[] grid) {
        final long[] sizes = new long[BIOME_COUNT];
        final boolean[] visited = new boolean[grid.length];
        final int[] stack = new int[grid.length];
        for (int start = 0; start < grid.length; start++) {
            if (visited[start]) {
                continue;
            }
            final int zone = indexOf(grid[start]);
            int top = 0;
            stack[top++] = start;
            visited[start] = true;
            long size = 0;
            while (top > 0) {
                final int idx = stack[--top];
                size++;
                final int x = idx % WINDOW;
                final int z = idx / WINDOW;
                if (x > 0 && !visited[idx - 1] && zone == indexOf(grid[idx - 1])) {
                    visited[idx - 1] = true;
                    stack[top++] = idx - 1;
                }
                if (x < WINDOW - 1 && !visited[idx + 1] && zone == indexOf(grid[idx + 1])) {
                    visited[idx + 1] = true;
                    stack[top++] = idx + 1;
                }
                if (z > 0 && !visited[idx - WINDOW] && zone == indexOf(grid[idx - WINDOW])) {
                    visited[idx - WINDOW] = true;
                    stack[top++] = idx - WINDOW;
                }
                if (z < WINDOW - 1 && !visited[idx + WINDOW] && zone == indexOf(grid[idx + WINDOW])) {
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

    /** 内部 chunk 中 4 邻均异 id 的孤岛占比（盐胡椒度反指标）。 */
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
        return interior == 0 ? 0 : (double) islands / (double) interior;
    }

    /**
     * 契约防御（链构造期 fail-fast 的透传复核；主断言面在 GTSRGenLayerSelfTest，此处只钉
     * "哑元 id 段可构造 + 非法入参必抛"这一层，防本工具自身悄悄改用非法档）。
     */
    private static void contractChecks() {
        expectThrows(() -> new GTSRGenLayerChain(1L, new int[0]), "empty biomeIds");
        expectThrows(() -> new GTSRGenLayerChain(1L, new int[] { 256 }), "id 256");
        expectThrows(() -> new GTSRGenLayerChain(1L, BIOME_IDS, -1), "zoomLevels -1");
        System.out.println("BIOMEZONE CONTRACT PASS: chain constructor fail-fast passthrough");
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

    private static int indexOf(int id) {
        for (int i = 0; i < BIOME_IDS.length; i++) {
            if (BIOME_IDS[i] == id) {
                return i;
            }
        }
        return -1;
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
