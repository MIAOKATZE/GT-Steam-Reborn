package com.miaokatze.gtsr.common.event;

/**
 * 奇点机器事件·维度固化 → 停机（仅 CESS）：
 * UU 物质超扣（减少量 > 当前撕裂）→ 撕裂 clamp 0 + disableWorking() 强制停机（软锤恢复）。
 */
public class GTSRSingularityDimSolidifyEvent extends GTSRMachineEvent {

    public GTSRSingularityDimSolidifyEvent(String machineKey, int x, int y, int z, int dim) {
        super(machineKey, "gtsr.event.singularity.dim_solidify", "gtsr.event.result.shutdown", x, y, z, dim);
    }
}
