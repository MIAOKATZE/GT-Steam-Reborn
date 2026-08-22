package com.miaokatze.gtsr.common.machine.base;

import net.minecraftforge.fluids.FluidStack;

/**
 * 枢纽控制器 hatch 视角窄接口（O2-B02）：四个枢纽 I/O hatch 的 mController 消费面单源，
 * 切断 base→machine 反向引用（common.machine.base 不再 import common.machine 具体枢纽类）。
 * receiveFluid/extractFluid 承自 {@link IHubArray}——两侧枢纽本就分别委托
 * receiveSteam/receiveWater 与 extractSteam/extractWater，hatch 改调接口方法语义不变。
 */
public interface IHubController extends IHubArray {

    /** 多方块是否成形。 */
    boolean isFormed();

    /** 当前总容量（芯片倍率族含内）。 */
    long getTotalCapacity();

    /** 当前存储量读数（蒸汽 mSteamStored / 蓄水 mWaterStored 的统一视图）。 */
    long getStoredFluidAmount();

    /** 当前存储流体（空存储返回 null；输出 hatch getFluid 透传用）。 */
    FluidStack getStoredFluidStack();
}
