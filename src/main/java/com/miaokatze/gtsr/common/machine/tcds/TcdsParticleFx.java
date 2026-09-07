package com.miaokatze.gtsr.common.machine.tcds;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCDS 粒子候选位登记簿（纯登记/注销，无渲染；集群 {@code ClusterParticleFx} 平行类，禁止反向依赖）：
 * 控制器工作态边沿登记自身矩阵 'e' 空气候选位（控制器相对偏移，惰性扫描 SHAPE_MAIN 收集），
 * 非工作态/失效配对清理。渲染逻辑分离至 {@link TcdsParticleFxClient} 以保证服务端类加载安全
 * （专用服务器不触达任何客户端类）。
 */
public final class TcdsParticleFx {

    // 包私有：供同包 TcdsParticleFxClient 渲染侧读取（服务端只经 register/clear 触达）

    /** 控制器 → 'e' 位控制器相对偏移列表（仅客户端登记；重复登记以最后一次为准）。 */
    static final Map<MTEThermoChemicalDenseSteamGenerator, List<int[]>> candidates = new ConcurrentHashMap<>();

    private TcdsParticleFx() {}

    /** 控制器侧登记自身 'e' 空气候选位（控制器相对偏移）；重复登记以最后一次为准。 */
    public static void registerCandidates(MTEThermoChemicalDenseSteamGenerator controller,
        List<int[]> controllerRelativeOffsets) {
        if (controller == null) return;
        candidates.put(controller, controllerRelativeOffsets);
    }

    /** 控制器侧注销候选位（工作态撤除边沿/onRemoval 配对）。 */
    public static void clearCandidates(MTEThermoChemicalDenseSteamGenerator controller) {
        if (controller != null) candidates.remove(controller);
    }
}
