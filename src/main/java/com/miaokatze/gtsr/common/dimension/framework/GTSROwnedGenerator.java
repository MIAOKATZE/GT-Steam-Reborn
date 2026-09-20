package com.miaokatze.gtsr.common.dimension.framework;

/**
 * R1 维度干涉收口的「自有生成器」标记接口（R1 防线 1，plan R1 四层防线第 1 层）。
 * <p>
 * 背景（已核实）：{@code ChunkProviderServer.populate}（build/rfg/minecraft-src
 * ChunkProviderServer.java:303-318）外层对<b>一切维度</b>调用
 * {@code GameRegistry.generateWorld}，GTSR provider 无法从内部阻止——第三方
 * {@code IWorldGenerator}（Forestry 蜂巢、TC4 节点/矿、BC 油井等）由此进入 dim78/79。
 * GTSR 自有的两个生成器（{@code ProsperityWorldGenerator} / {@code WorldGenShatteredRuins}）
 * 实现本接口后，{@code GameRegistryMixin} 在 GTSR 维度上只放行本接口实例、跳过其余 generator。
 * <p>
 * 空接口（纯标记）：不携带任何方法与状态。fail-open：{@code GameRegistryMixin} 未应用
 * （如 mixins.gtsr.json 摘除本条目）时，{@code generateWorld} 行为回落现状，本接口退化为
 * 无消费者的无害标记。
 */
public interface GTSROwnedGenerator {
    // 纯标记接口，无成员
}
