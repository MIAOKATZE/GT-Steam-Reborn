package com.miaokatze.gtsr.common.dimension.framework.structure;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 世界生成入口 Sink（dim1 S4a，plan D1 / §1.2 S4a / §2 S4a）：只允许写入当前 populate chunk，
 * 协议层强制 {@code x>>4 == chunkX && z>>4 == chunkZ}（修复 02 代码 15 的越界 spill 瑕疵）。
 * <p>
 * 越界写入<b>丢弃并计数</b>：每实例（=每 chunk）的丢弃进静态累计器，每 256 chunk 汇总日志一次
 * （有丢弃才 WARN——干净运行零告警，即 plan S4a 验收③的 grep 口径）。flag 固定 2，不建 TileEntity。
 * <p>
 * 线程性：populate 为单线程逐 chunk 回调，但多维度/多世界可并发，计数器用 AtomicLong。
 */
public final class ChunkClampedSink implements BlockSink {

    /** 汇总日志窗口（chunk 数，plan：每 256 chunk 汇总一次）。 */
    private static final int LOG_WINDOW_CHUNKS = 256;

    /** 已服务 chunk 数（实例构造数）。 */
    private static final AtomicLong CHUNKS_SERVED = new AtomicLong();
    /** 当前日志窗口内的越界丢弃数。 */
    private static final AtomicLong DROPPED_IN_WINDOW = new AtomicLong();
    /** 历史累计丢弃数（附在汇总日志里，便于观察长期趋势）。 */
    private static final AtomicLong DROPPED_TOTAL = new AtomicLong();

    private final World world;
    private final int chunkX;
    private final int chunkZ;

    public ChunkClampedSink(World world, int chunkX, int chunkZ) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        final long served = CHUNKS_SERVED.incrementAndGet();
        if (served % LOG_WINDOW_CHUNKS == 0) {
            final long dropped = DROPPED_IN_WINDOW.getAndSet(0);
            if (dropped > 0) {
                GTSteamReborn.LOG.warn(
                    "[GTSR] ChunkClampedSink: {} out-of-range writes dropped in the last {} chunks (total dropped: {})",
                    dropped,
                    LOG_WINDOW_CHUNKS,
                    DROPPED_TOTAL.get());
            }
        }
    }

    @Override
    public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
        if ((x >> 4) != this.chunkX || (z >> 4) != this.chunkZ) {
            DROPPED_IN_WINDOW.incrementAndGet();
            DROPPED_TOTAL.incrementAndGet();
            return false;
        }
        if (y < 0 || y > 255 || !(block instanceof Block)) {
            // 句柄不合法属协议误用（世界入口只收 Block 实例），与越界同口径丢弃计数
            DROPPED_IN_WINDOW.incrementAndGet();
            DROPPED_TOTAL.incrementAndGet();
            return false;
        }
        return this.world.setBlock(x, y, z, (Block) block, meta, flags);
    }
}
