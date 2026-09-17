package com.miaokatze.gtsr.common.dimension.prosperity.ruins.city;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;

/**
 * 城市渲染切片 Sink（dim1 S4b，plan §3.1 渲染协议的游戏侧落地）。
 * <p>
 * 城市 plot 几何往往横跨多个 chunk（populate 互不相邻且顺序不定），渲染协议 = 每个
 * populate chunk 只把落在<b>自己</b>范围内的写入交给下游。本类即该"只取交集切片"的协议层：
 * 坐标在当前 chunk 内 → 转发；否则<b>静默吸收</b>（返回 true）——这是渲染协议的预期路径而非
 * 越界事故，因此不计入 ChunkClampedSink 的越界告警计数（plan S4b 验收"无越界告警"由此构造
 * 保证：ChunkClampedSink 只应见到零丢弃）。
 * <p>
 * Sink 链序（游戏侧）：变体/街道写入 → {@code CitySliceSink}（切片）→ CityBlockResolver
 * （键解析）→ ChunkClampedSink（最终守卫）→ World。
 */
public final class CitySliceSink implements BlockSink {

    private final BlockSink parent;
    private final int chunkX;
    private final int chunkZ;

    public CitySliceSink(BlockSink parent, int chunkX, int chunkZ) {
        this.parent = parent;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    @Override
    public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
        if ((x >> 4) != this.chunkX || (z >> 4) != this.chunkZ || y < 0 || y > 255) {
            return true; // 其他 chunk populate 的交集切片职责——静默吸收，非越界事故
        }
        return this.parent.setBlock(x, y, z, block, meta, flags);
    }
}
