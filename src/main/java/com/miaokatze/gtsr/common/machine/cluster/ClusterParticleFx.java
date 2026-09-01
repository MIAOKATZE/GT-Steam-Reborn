package com.miaokatze.gtsr.common.machine.cluster;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 集群粒子候选位登记簿（纯登记/注销，无渲染）：模块自身矩阵 'e' 空气候选位（单元基类
 * {@link MTEClusterUnitBase} 客户端 tick 经 {@link #registerAirCandidates} 注册，空列表跳过）与
 * 集群 'e' 精准候选位（按字节同步的延伸段数经 {@link ClusterStructureDef#clusterAirFxOffsets(int)}
 * 推导后经 {@link #registerClusterAirCandidates} 整体替换）。渲染逻辑已分离至
 * {@link ClusterParticleFxClient} 以保证服务端类加载安全。
 */
public final class ClusterParticleFx {

    // 包私有：供同包 ClusterParticleFxClient 渲染侧读取（服务端只经 register/clear 触达）

    /** 模块空气候选位：unit → 控制器相对偏移列表（客户端实例注册）。 */
    static final Map<MTEClusterUnitBase, List<int[]>> unitAirCandidates = new ConcurrentHashMap<>();

    /** 集群 'e' 精准候选位：cluster → 控制器相对偏移列表（仅客户端注册；重复注册以最后一次为准）。 */
    static final Map<MTESteamMineralLogisticsCluster, List<int[]>> clusterAirCandidates = new ConcurrentHashMap<>();

    private ClusterParticleFx() {}

    /** 模块侧（客户端 tick）注册自身 'e' 空气候选位；重复注册以最后一次为准。 */
    public static void registerAirCandidates(MTEClusterUnitBase unit, List<int[]> controllerRelativeOffsets) {
        if (unit == null) return;
        unitAirCandidates.put(unit, controllerRelativeOffsets);
    }

    /** 模块侧注销候选位（onRemoval 等）。 */
    public static void clearAirCandidates(MTEClusterUnitBase unit) {
        if (unit != null) unitAirCandidates.remove(unit);
    }

    /** 主控侧注册集群 'e' 精准候选位（控制器相对偏移，按延伸段数整体替换）。 */
    public static void registerClusterAirCandidates(MTESteamMineralLogisticsCluster cluster,
        List<int[]> controllerRelativeOffsets) {
        if (cluster == null) return;
        clusterAirCandidates.put(cluster, controllerRelativeOffsets);
    }

    /** 主控侧注销集群 'e' 精准候选位（checkMachine 复位段/断裂清理/onRemoval 配对）。 */
    public static void clearClusterAirCandidates(MTESteamMineralLogisticsCluster cluster) {
        if (cluster != null) clusterAirCandidates.remove(cluster);
    }
}
