package com.miaokatze.gtsr.common.dimension.framework.structure;

/**
 * 全维度统一世界生成哈希源（dim1 S4a，plan §1.2 S4a / 02 §8.3 代码 21 原样）。
 * <p>
 * worldSeed^chunkX^chunkZ 风格派生（确定性种子口径，02 §0.3）：同一种子同一坐标永远派生同一结果，
 * 任何 chunk 都能独立重算——跨 chunk 生成无需共享状态。纯函数，零 Minecraft 依赖（可离线实例化）。
 */
public final class GTSRWorldgenHash {

    private GTSRWorldgenHash() {}

    /** chunk 级哈希（残缺机器/散布的 per-chunk 决策种子）。 */
    public static long chunkSeed(long worldSeed, int cx, int cz) {
        return worldSeed ^ ((long) cx * 341873128712L + (long) cz * 132897987541L);
    }

    /**
     * cell 级哈希（大矿坑 cell=16 / 矿洞 cell=4 / 城市 cell=24 / 通用）：盐隔离同 cell 不同用途，
     * 避免不同生成器在同一 cell 上做出同相关的决策。
     */
    public static long cellSeed(long worldSeed, int gx, int gz, long salt) {
        return worldSeed ^ ((long) gx * 0x9E3779B97F4A7C15L) ^ ((long) gz * 0xC2B2AE3D27D4EB4FL) ^ salt;
    }

    /** block 级哈希（逐方块确定性掷骰，如损伤/缺失判定）。 */
    public static long blockSeed(long worldSeed, int x, int y, int z) {
        return worldSeed ^ ((long) x * 0x27D4EB2F165667C5L) ^ ((long) y << 32) ^ ((long) z * 0x165667B19E3779F9L);
    }
}
