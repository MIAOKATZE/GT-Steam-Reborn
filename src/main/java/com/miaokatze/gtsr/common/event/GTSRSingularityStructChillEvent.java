package com.miaokatze.gtsr.common.event;

/**
 * 奇点机器事件·结构失温 → 停机：
 * cryotheum 超扣（减少量 > 当前失稳）→ 失稳 clamp 0 + disableWorking() 强制停机（软锤恢复）。
 */
public class GTSRSingularityStructChillEvent extends GTSRMachineEvent {

    public GTSRSingularityStructChillEvent(String machineKey, int x, int y, int z, int dim) {
        super(machineKey, "gtsr.event.singularity.struct_chill", "gtsr.event.result.shutdown", x, y, z, dim);
    }
}
