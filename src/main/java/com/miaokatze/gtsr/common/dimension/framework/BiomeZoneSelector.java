package com.miaokatze.gtsr.common.dimension.framework;

/**
 * 群系空间连贯分区选择器（dim78 修复 S-A2，plan §4 S-A2 + §12 修订第 6 条）。
 * <p>
 * 两级确定性掷骰（全 splitmix 终结哈希，<b>零 Minecraft import</b>，离线可复算）：
 * <ol>
 * <li>cell 级基准 zone：zone cell = {@link #ZONE_CELL_CHUNKS} chunk（用户拍板 48→16，群系区不要
 * 太大），每 cell 按权重表掷出基准群系——同 cell 整片同群系，形成空间连贯分区，摆脱 per-chunk
 * 均匀掷骰的盐胡椒分布；</li>
 * <li>边带过渡碎斑：距 cell 边界不足 band（band = cell×{@link #EDGE_BAND_FRACTION} 四舍五入，
 * cell=16 时为 2 chunk）的 chunk，以 {@link #EDGE_BAND_FRACTION} 概率改掷相邻 cell（取基准
 * zone 与本 cell 不同的邻 cell）的 zone，其余保持基准——边界处生成约 12% 的过渡碎斑。</li>
 * </ol>
 * 输出契约：返回值 ∈ [0, biomeCount)（def 群系权重表下标），消费侧仍整 chunk 单群系
 * （{@link GTSRWorldChunkManager#biomeAt} 口径不变）。纯函数：同 seed 同坐标结果恒定，
 * 与 chunk 生成顺序无关。
 */
public final class BiomeZoneSelector {

    /** zone cell 尺寸（chunk）：plan §12 修订第 6 条，48→16（用户：不要把群系做得太大）。 */
    public static final int ZONE_CELL_CHUNKS = 16;

    /**
     * 边带比例：既是边带宽度系数（band = cell×此值 四舍五入），也是边带内 chunk 改掷邻 cell
     * 的概率（plan §12 修订第 6 条保留 12%）。
     */
    public static final float EDGE_BAND_FRACTION = 0.12F;

    /** 边带掷骰命中概率的分母（12% → roll%1000 < 120）。 */
    private static final int BAND_ROLL_SCALE = 1000;

    /** cell 级域分离盐（与 chunk 级掷骰、其他 cellSeed 用途解耦）。 */
    private static final long CELL_DOMAIN = 0x5A4F4E455A4F4E45L;
    /** chunk 级域分离盐。 */
    private static final long CHUNK_DOMAIN = 0x2B1E4E4F5A4F4E45L;

    private static final long CELL_CONSTANT_X = 0x9E3779B97F4A7C15L;
    private static final long CELL_CONSTANT_Z = 0xC2B2AE3D27D4EB4FL;
    private static final long CHUNK_CONSTANT_X = 0xBF58476D1CE4E5B9L;
    private static final long CHUNK_CONSTANT_Z = 0x94D049BB133111EBL;

    private BiomeZoneSelector() {}

    /**
     * 群系 zone 选择（plan §4 S-A2 定夺签名）：cell 级权重掷骰 + 边带 12% chunk 级邻 cell 掷骰。
     *
     * @param seed       世界种子
     * @param chunkX     chunk 坐标 X
     * @param chunkZ     chunk 坐标 Z
     * @param biomeCount 群系表长度（&gt;0）
     * @param weights    权重表（null 或长度不足按下标补 1；单权重 &lt;1 按 1 计，与展开表口径一致）
     * @param cell       zone cell 尺寸（chunk，&gt;0；dim78 接线传 {@link #ZONE_CELL_CHUNKS}）
     * @param salt       用途域分离盐（dim78 接线传 0x5A4F4E45L）
     * @return 群系权重表下标 ∈ [0, biomeCount)
     * @throws IllegalArgumentException biomeCount 或 cell 非正（接线期编程错误，fail fast）
     */
    public static int select(long seed, int chunkX, int chunkZ, int biomeCount, int[] weights, int cell, long salt) {
        if (biomeCount <= 0) {
            throw new IllegalArgumentException("biomeCount must be positive: " + biomeCount);
        }
        if (cell <= 0) {
            throw new IllegalArgumentException("cell must be positive: " + cell);
        }
        final int cellX = Math.floorDiv(chunkX, cell);
        final int cellZ = Math.floorDiv(chunkZ, cell);
        final int base = zoneOfCell(seed, cellX, cellZ, biomeCount, weights, salt);
        if (!isEdgeBand(chunkX, chunkZ, cell)) {
            return base;
        }
        final long roll = chunkRoll(seed, chunkX, chunkZ, salt);
        if (Math.floorMod(roll, BAND_ROLL_SCALE) >= Math.round(BAND_ROLL_SCALE * EDGE_BAND_FRACTION)) {
            return base;
        }
        // 12% 命中：改掷邻 cell——优先取本 chunk 所在边带方向、且基准 zone 不同的邻 cell；
        // 均与基准同 zone 时回退全部 4 邻方向（miss 仅剩"4 邻全同 zone"，约 2%，保证测得
        // 边带偏离率 ≈12%）；4 邻仍无不同 zone 则保持基准（视觉上无差异可过渡）。
        final int band = bandChunks(cell);
        final int localX = Math.floorMod(chunkX, cell);
        final int localZ = Math.floorMod(chunkZ, cell);
        int directionMask = 0;
        if (localX < band) {
            directionMask |= 1; // 西邻
        }
        if (localX >= cell - band) {
            directionMask |= 2; // 东邻
        }
        if (localZ < band) {
            directionMask |= 4; // 北邻
        }
        if (localZ >= cell - band) {
            directionMask |= 8; // 南邻
        }
        int picked = -1;
        int seen = 0;
        for (int pass = 0; pass < 2 && picked == -1; pass++) {
            final int mask = pass == 0 ? directionMask : 0b1111;
            for (int dir = 0; dir < 4; dir++) {
                if ((mask & (1 << dir)) == 0) {
                    continue;
                }
                final int nx = cellX + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                final int nz = cellZ + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                final int zone = zoneOfCell(seed, nx, nz, biomeCount, weights, salt);
                if (zone == base) {
                    continue;
                }
                seen++;
                if (Math.floorMod(roll >>> 20, seen) == 0) {
                    picked = zone;
                }
            }
        }
        return picked == -1 ? base : picked;
    }

    /**
     * cell 级基准 zone（公开给离线自检工具复算边带偏离）：权重掷骰，全 splitmix。
     *
     * @return 群系权重表下标 ∈ [0, biomeCount)
     */
    public static int zoneOfCell(long seed, int cellX, int cellZ, int biomeCount, int[] weights, long salt) {
        if (biomeCount <= 0) {
            throw new IllegalArgumentException("biomeCount must be positive: " + biomeCount);
        }
        final long h = seed ^ ((long) cellX * CELL_CONSTANT_X) ^ ((long) cellZ * CELL_CONSTANT_Z) ^ salt ^ CELL_DOMAIN;
        return pickWeighted(mix(h), biomeCount, weights);
    }

    /**
     * chunk 是否落在 cell 边带（距任一 cell 边界不足 band chunk；公开给离线自检工具同口径复算）。
     */
    public static boolean isEdgeBand(int chunkX, int chunkZ, int cell) {
        if (cell <= 0) {
            throw new IllegalArgumentException("cell must be positive: " + cell);
        }
        final int band = bandChunks(cell);
        final int localX = Math.floorMod(chunkX, cell);
        final int localZ = Math.floorMod(chunkZ, cell);
        return localX < band || localX >= cell - band || localZ < band || localZ >= cell - band;
    }

    /** 边带宽度（chunk）= cell×边带比例 四舍五入，至少 1（cell=16 → 2）。 */
    private static int bandChunks(int cell) {
        return Math.max(1, Math.round(cell * EDGE_BAND_FRACTION));
    }

    /** chunk 级边带掷骰源（与 cell 级常量/域分离，独立同分布）。 */
    private static long chunkRoll(long seed, int chunkX, int chunkZ, long salt) {
        final long h = seed ^ ((long) chunkX * CHUNK_CONSTANT_X)
            ^ ((long) chunkZ * CHUNK_CONSTANT_Z)
            ^ salt
            ^ CHUNK_DOMAIN;
        return mix(h);
    }

    /** 权重掷骰：roll 落入累计权重区间选下标；权重 null/缺位按 1、&lt;1 按 1（与展开表口径一致）。 */
    private static int pickWeighted(long roll, int biomeCount, int[] weights) {
        long total = 0;
        for (int i = 0; i < biomeCount; i++) {
            total += weightAt(weights, i);
        }
        long threshold = Math.floorMod(roll, total);
        long acc = 0;
        for (int i = 0; i < biomeCount; i++) {
            acc += weightAt(weights, i);
            if (threshold < acc) {
                return i;
            }
        }
        return biomeCount - 1;
    }

    private static int weightAt(int[] weights, int index) {
        if (weights == null || index >= weights.length) {
            return 1;
        }
        return Math.max(1, weights[index]);
    }

    /** splitmix64 终结哈希（与 GTSRWorldChunkManager.hash 同族，纯 long 位运算，跨平台确定）。 */
    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }
}
