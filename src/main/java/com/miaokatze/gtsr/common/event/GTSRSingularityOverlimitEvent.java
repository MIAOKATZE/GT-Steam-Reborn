package com.miaokatze.gtsr.common.event;

/**
 * 奇点机器事件·装置超限 → 奇点失控：
 * SSE 超限 >500% / CESS 超限 >=1000% 时触发装置超限爆炸链（spawn RUNAWAY → 广播 → explodeMultiblock）。
 */
public class GTSRSingularityOverlimitEvent extends GTSRMachineEvent {

    public GTSRSingularityOverlimitEvent(String machineKey, int x, int y, int z, int dim) {
        super(machineKey, "gtsr.event.singularity.overlimit", "gtsr.event.result.rogue_singularity", x, y, z, dim);
    }
}
