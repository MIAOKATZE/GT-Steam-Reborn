package com.miaokatze.gtsr.common.dimension.prosperity.ruins.city;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSliceSink;

/**
 * 城市渲染切片 Sink（dim1 S4b，plan §3.1 渲染协议的游戏侧落地）。
 * <p>
 * 城市 plot 几何往往横跨多个 chunk（populate 互不相邻且顺序不定），渲染协议 = 每个
 * populate chunk 只把落在<b>自己</b>范围内的写入交给下游。本类即该"只取交集切片"的协议层：
 * 坐标在当前 chunk 内 → 转发；否则<b>静默吸收</b>（返回 true）——这是渲染协议的预期路径而非
 * 越界事故，因此不计入 ChunkClampedSink 的越界告警计数（plan S4b 验收"无越界告警"由此构造
 * 保证：ChunkClampedSink 只应见到零丢弃）。
 * <p>
 * <b>P16-B1 归属变更（行为一字未改）</b>：同一套切片语义现在服务两族结构——城内（本类）与
 * 城外跨 chunk 巨构（{@code RuinedMachinePlacer} 的跨片分支）。协议实现因此上收到
 * {@link ChunkSliceSink}（framework），本类退化为<b>城内命名的那一层薄壳</b>：只保留构造器与
 * 类名，让 {@code ProsperityWorldGenerator.placeCities} 的链序注释与
 * {@code tools/dim1/CityDeterminismCheck} 的切片协议断言仍指向同一个符号。
 * <b>刻意不在这里再写一遍 {@code setBlock}</b>——那就是要被禁止的"第二套切片器"；
 * 归属由"本类不含任何切片判定代码（全文只有构造器）"这一事实自证，
 * 共用面由 {@code tools/dim1/OutpostTemplateCheck} 的成对断言直接调用 {@code ChunkSpans} 钉住。
 * <p>
 * Sink 链序（游戏侧）：变体/街道写入 → {@code CitySliceSink}（切片，实现见 {@link ChunkSliceSink}）
 * → CityBlockResolver（键解析）→ ChunkClampedSink（最终守卫）→ World。
 */
public class CitySliceSink extends ChunkSliceSink {

    public CitySliceSink(BlockSink parent, int chunkX, int chunkZ) {
        super(parent, chunkX, chunkZ);
    }
}
