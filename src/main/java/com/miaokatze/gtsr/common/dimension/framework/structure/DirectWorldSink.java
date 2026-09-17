package com.miaokatze.gtsr.common.dimension.framework.structure;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * 指令直写入口 Sink（dim1 S4a，plan D1 / §1.2 S4a，S5 /gtsr structure 消费）：
 * 全量写入、flag 3、写前强制 chunk load（跨 chunk 结构一次成型，不依赖玩家加载状态）、允许 TileEntity。
 * <p>
 * 与 {@link ChunkClampedSink} 的区别：不做 chunk 钳制（放置方自行负责 footprint 合法性）；
 * chunk 强制加载使越 chunk 写入安全（原版 World.setBlock 对未加载 chunk 会静默失败）。
 */
public final class DirectWorldSink implements BlockSink {

    private final World world;

    public DirectWorldSink(World world) {
        this.world = world;
    }

    @Override
    public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
        if (y < 0 || y > 255 || !(block instanceof Block)) {
            return false;
        }
        final Chunk chunk = this.world.getChunkFromChunkCoords(x >> 4, z >> 4);
        if (chunk == null) {
            return false;
        }
        return this.world.setBlock(x, y, z, (Block) block, meta, flags);
    }
}
