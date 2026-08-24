package com.miaokatze.gtsr.common.machine.cluster;

/**
 * 链批执行宿主契约（plan §3.6 / §4.2）：{@link ClusterChainExecutor} 与集群总控之间的最小接口面，
 * 由总控（{@code MTESteamMineralLogisticsCluster}，批 2 E5 接线）实现，执行器只消费、不感知总控内部。
 * <p>
 * 职责边界（单一职责，不新建架构层）：
 * <ul>
 * <li>{@link #heatFraction()}——瞬时热量 0..1，供批执行前的低温吞批门控（§3.6.4）；</li>
 * <li>{@link #isThermalSupplyOkLatched()}——最近一次 20t 双流体原子结算的锁存结果
 * （蒸汽+润滑两项均足=true；断供锁存驱动预热衰减与 GUI 红标，见 {@link ClusterSteamEconomy}）；</li>
 * <li>{@link #addRealBatchThroughput(int)}——真实成功批吞吐累计：同一秒段内多条物流链的成功批矿数
 * 求和，宿主按滑动窗口/与 20t 结算对齐的窗口换算矿石/s（§3.6.6-4），取代「最后一条链理论吞吐」。</li>
 * </ul>
 * 线程模型：仅服务器主线程调用。
 */
public interface ClusterBatchHost {

    /**
     * @return 瞬时热量分率 0..1（预热进度；&lt;1.0 时批执行走低温吞批路径）
     */
    double heatFraction();

    /**
     * @return 最近一次 20t 结算锁存的双流体供应是否正常（蒸汽且润滑均足额；true=正常）
     */
    boolean isThermalSupplyOkLatched();

    /**
     * 真实成功批吞吐累计：每条物流链每成功一批调用一次，入参为本批实际处理矿数。
     *
     * @param items 本批实际处理矿数（恒 &gt; 0）
     */
    void addRealBatchThroughput(int items);
}
