package com.miaokatze.gtsr.common.event;

/**
 * 奇点机器事件·维度撕裂 → 短时维度破碎（仅 CESS）：
 * 维度撕裂 >100% 时触发维度撕裂爆炸链（spawn 紫色 RUNAWAY `50 20 10 24000 0 purple 100` → 广播 → explodeMultiblock）。
 */
public class GTSRSingularityDimTearEvent extends GTSRMachineEvent {

    public GTSRSingularityDimTearEvent(String machineKey, int x, int y, int z, int dim) {
        super(machineKey, "gtsr.event.singularity.dim_tear", "gtsr.event.result.dim_shatter", x, y, z, dim);
    }
}
