package com.miaokatze.gtsr.common.event;

/**
 * 奇点机器事件·结构崩解 → 奇点失控：
 * 结构失稳 >100% 时触发结构崩解爆炸链（spawn RUNAWAY → 广播 → explodeMultiblock）。
 */
public class GTSRSingularityStructCollapseEvent extends GTSRMachineEvent {

    public GTSRSingularityStructCollapseEvent(String machineKey, int x, int y, int z, int dim) {
        super(
            machineKey,
            "gtsr.event.singularity.struct_collapse",
            "gtsr.event.result.rogue_singularity",
            x,
            y,
            z,
            dim);
    }
}
