package com.miaokatze.gtsr.common.dimension.prosperity.ruins.city;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.config.Config;

/**
 * 古代城选址规划纯函数（dim1 S4b，plan §3.1 选址与尺度）。
 * <p>
 * cell 网格 = 24 chunk（384 格）；{@code cellSeed = GTSRWorldgenHash.cellSeed(worldSeed,
 * floorDiv(cx,24), floorDiv(cz,24), SALT_CITY)}。存在掷骰 = 混合哈希 % 100 &lt;
 * {@link Config#prosperityCityChance}（默认 45，0 = 全禁用）。中心 chunk = cell 原点 +
 * (8 + hash%8, 8 + hash%8)（cell 中部 8×8 内）；半径 = 4 + hash%4（4-7 chunk，≤8 chunk 钳制）。
 * <p>
 * <b>纯函数</b>：只依赖（worldSeed, cell 坐标）与 Config 常量，零世界读取；同 seed 同 cell
 * 任意次规划逐字节一致（tools/dim1/CityDeterminismCheck 自证）。任何 chunk 都能独立重算
 * 邻域城市——跨 chunk 渲染无共享状态。
 * <p>
 * 检索口径：城影响（半径 7 + plot 外扩 + 变体 footprint 外溢）至多越出本 cell 约 1 chunk，
 * 故 chunk 检索扫描 3×3 cell 邻域（{@link #citiesNear}），渲染/跳过判定统一用
 * {@code |chunk - centerChunk| ≤ radius+1} 窗口（plan §3.4 半径+1 缓冲同口径）。
 */
public final class CityPlanner {

    /** 城市网格周期（chunk；plan §3.1：CITY_CELL = 24）。 */
    public static final int CITY_CELL = 24;

    /** 城市盐（"cItY" 助记；GTSRWorldgenHash.cellSeed 盐隔离不同用途）。 */
    public static final long SALT_CITY = 0xC174L;

    private CityPlanner() {}

    /** 单 cell 规划：无城返回 null。 */
    public static CityPlan planFor(long worldSeed, int cellX, int cellZ) {
        final long cellSeed = GTSRWorldgenHash.cellSeed(worldSeed, cellX, cellZ, SALT_CITY);
        if (mix(cellSeed, 1) % 100L >= Config.prosperityCityChance) {
            return null;
        }
        final int offsetChunkX = 8 + (int) (mix(cellSeed, 2) % 8); // 8..15
        final int offsetChunkZ = 8 + (int) (mix(cellSeed, 3) % 8); // 8..15
        final int radiusChunks = 4 + (int) (mix(cellSeed, 4) % 4); // 4..7
        return new CityPlan(
            worldSeed,
            cellSeed,
            cellX * CITY_CELL + offsetChunkX,
            cellZ * CITY_CELL + offsetChunkZ,
            radiusChunks);
    }

    /**
     * chunk 邻域城市检索（3×3 cell 扫描 + 窗口过滤；无城返回空数组零分配）。
     * 渲染与"城内跳过散布/机器"共用本入口（判定一致）。
     */
    public static CityPlan[] citiesNear(long worldSeed, int chunkX, int chunkZ) {
        final int baseCellX = Math.floorDiv(chunkX, CITY_CELL);
        final int baseCellZ = Math.floorDiv(chunkZ, CITY_CELL);
        CityPlan[] out = null;
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final CityPlan plan = planFor(worldSeed, baseCellX + dx, baseCellZ + dz);
                if (plan != null && plan.chunkInBuffer(chunkX, chunkZ)) {
                    if (out == null) {
                        out = new CityPlan[4];
                    }
                    if (n == out.length) {
                        final CityPlan[] bigger = new CityPlan[n * 2];
                        System.arraycopy(out, 0, bigger, 0, n);
                        out = bigger;
                    }
                    out[n++] = plan;
                }
            }
        }
        if (out == null) {
            return new CityPlan[0];
        }
        final CityPlan[] exact = new CityPlan[n];
        System.arraycopy(out, 0, exact, 0, n);
        return exact;
    }

    /** splitmix64 终结混哈希（非负长整；与 CityVariants.mix 同族常数族，盐分路）。 */
    static long mix(long cellSeed, long salt) {
        long h = cellSeed ^ (salt * 0xD1B54A32D192ED03L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h & 0x7FFFFFFFFFFFFFFFL;
    }
}
