package com.miaokatze.gtsr.api.compat;

import java.util.List;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IDualInputHatch;

/**
 * 统一仓室流体访问层（v1.10.6）。
 * <p>
 * 全项目多方块机器的流体取流/探测统一走本类方法，铁律：
 * <ul>
 * <li>探测一律走 {@code getTankInfo(ForgeDirection.UNKNOWN)}——双版本均安全模拟：ME 输入仓
 * （MTEHatchInputME）内部以 extractItems(SIMULATE) 返回网络可得量，普通仓返回标准存量；
 * 不可用 3 参 drain(false) 模拟：beta-1 的 MTEHatchInputME.drain 忽略 doDrain，模拟调用也会
 * 真实提取 ME 网络流体（beta-2 已补 SIMULATE 分支，但需双版本兼容）</li>
 * <li>实扣仍用 3 参 {@code drain(ForgeDirection.UNKNOWN, FluidStack, true)}（实扣在双版本语义一致，
 * 兼容普通仓/ME 输入仓/样板仓）</li>
 * <li>实扣量 = 需求与可得量的较小值，禁止传 {@code Integer.MAX_VALUE} 实扣（ME 网络全量提取；
 * 唯一例外：奇点机 drainGrade 的"输入仓有多少消耗多少"设计语义，见 MTESingularityMachineBase）</li>
 * <li>探测/实扣一律传副本（ME 输入仓实现会改写请求对象）</li>
 * </ul>
 * 设计依据：ME 输入仓（MTEHatchInputME）本地罐恒空，2 参 drain 恒 null，仅 3 参 UNKNOWN drain
 * 在配方窗口内走 slot.extracted 虚拟引用、窗口外走网络提取；ProgrammableHatches 限制仓的
 * restrict 仅在窗口内 slot 扣减路径生效，窗口外 drain 实扣完全绕过——因此实扣前先以
 * getTankInfo 探测可得量，使限制仓的配置在窗口内自然生效。
 */
public final class GTSRHatchFluidAccess {

    /** 私有构造器，工具类不可实例化 */
    private GTSRHatchFluidAccess() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }

    /**
     * 探测仓中指定流体的实际可得量（模拟，不消耗）。
     *
     * @param hatch  仓室
     * @param fluid  流体
     * @param amount 探测请求量（实扣上限；应传需求上限，非 MAX_VALUE）
     * @return 实际可得量对应的 FluidStack 副本（amount 为可得量，≤ 请求量），无则 null
     */
    public static FluidStack probeFluidAmount(MTEHatch hatch, Fluid fluid, int amount) {
        if (hatch == null || fluid == null || amount <= 0) return null;
        FluidTankInfo[] tanks = hatch.getTankInfo(ForgeDirection.UNKNOWN);
        if (tanks == null) return null;
        long available = 0;
        for (FluidTankInfo tank : tanks) {
            if (tank != null && tank.fluid != null && tank.fluid.getFluid() == fluid) available += tank.fluid.amount;
        }
        if (available <= 0) return null;
        return new FluidStack(fluid, (int) Math.min(available, amount));
    }

    /**
     * 探测仓中指定流体的实际可得量（模拟，不消耗），按 FluidStack 匹配。
     *
     * @param hatch 仓室
     * @param want  探测请求（amount 为探测上限）
     * @return 实际可得量对应的 FluidStack 副本，无则 null
     */
    public static FluidStack probeFluidAmount(MTEHatch hatch, FluidStack want) {
        if (hatch == null || want == null || want.amount <= 0) return null;
        FluidTankInfo[] tanks = hatch.getTankInfo(ForgeDirection.UNKNOWN);
        if (tanks == null) return null;
        long available = 0;
        for (FluidTankInfo tank : tanks) {
            if (tank != null && tank.fluid != null && tank.fluid.isFluidEqual(want)) available += tank.fluid.amount;
        }
        if (available <= 0) return null;
        FluidStack copy = want.copy();
        copy.amount = (int) Math.min(available, want.amount);
        return copy;
    }

    /**
     * 从仓中按需量取流：模拟→实扣两段式。
     *
     * @param hatch 仓室
     * @param want  需求（amount 为需求量）
     * @return 实际提取量，0 表示未取到
     */
    public static int drainFluidExact(MTEHatch hatch, FluidStack want) {
        if (hatch == null || want == null || want.amount <= 0) return 0;
        FluidStack sim = probeFluidAmount(hatch, want);
        if (sim == null || sim.amount <= 0) return 0;
        int toDrain = Math.min(sim.amount, want.amount);
        FluidStack real = hatch.drain(ForgeDirection.UNKNOWN, new FluidStack(want.getFluid(), toDrain), true);
        return real == null ? 0 : real.amount;
    }

    /**
     * 判断仓中是否有指定流体（amount &gt; 0）。
     *
     * @param hatch  仓室
     * @param fluid  流体
     * @param amount 探测请求量
     * @return 有则 true
     */
    public static boolean hasFluid(MTEHatch hatch, Fluid fluid, int amount) {
        return probeFluidAmount(hatch, fluid, amount) != null;
    }

    /**
     * 从仓列表跨仓按需取流（合计需求）。模拟→实扣两段式，每仓按实际可得量扣减，
     * 直至满足需求或耗尽所有仓。
     *
     * @param hatches 仓室列表（已过滤 null）
     * @param want    需求（amount 为需求总量）
     * @return 实际提取总量
     */
    public static int depleteFluidAcross(List<? extends MTEHatch> hatches, FluidStack want) {
        if (hatches == null || want == null || want.amount <= 0) return 0;
        int remaining = want.amount;
        for (MTEHatch hatch : GTUtility.validMTEList(hatches)) {
            if (remaining <= 0) break;
            int drained = drainFluidExact(hatch, new FluidStack(want.getFluid(), remaining));
            remaining -= drained;
        }
        return want.amount - remaining;
    }

    /**
     * 判断仓列表合计是否满足指定流体需求（仅模拟探测）。
     *
     * @param hatches 仓室列表
     * @param want    需求（amount 为需求总量）
     * @return 满足则 true
     */
    public static boolean hasEnoughAcross(List<? extends MTEHatch> hatches, FluidStack want) {
        if (hatches == null || want == null || want.amount <= 0) return true;
        long available = 0;
        for (MTEHatch hatch : GTUtility.validMTEList(hatches)) {
            FluidStack sim = probeFluidAmount(hatch, want);
            if (sim != null) available += sim.amount;
            if (available >= want.amount) return true;
        }
        return false;
    }

    /**
     * 从样板仓（mDualInputHatches）按需扣减流体（引用扣减）。
     * <p>
     * 样板仓流体消费语义：getAllFluids 返回 pattern 库存的持久引用，直接扣减引用后
     * 由样板仓自身完成网络结算（与奇点燃料 getAllItems 引用扣减同构）。
     *
     * @param duals 样板仓列表
     * @param want  需求（amount 为需求总量）
     * @return 实际扣减总量
     */
    public static int depleteFluidFromDuals(List<? extends IDualInputHatch> duals, FluidStack want) {
        if (duals == null || want == null || want.amount <= 0) return 0;
        int remaining = want.amount;
        for (IDualInputHatch dual : duals) {
            if (dual == null || remaining <= 0) break;
            for (FluidStack fs : dual.getAllFluids()) {
                if (fs == null || fs.amount <= 0 || !fs.isFluidEqual(want)) continue;
                int take = Math.min(remaining, fs.amount);
                fs.amount -= take;
                remaining -= take;
                if (remaining <= 0) break;
            }
        }
        return want.amount - remaining;
    }

    /**
     * v1.10.8：统一刷新样板仓（mDualInputHatches）底材纹理（10 处同构循环抽取）。
     *
     * @param duals     样板仓列表
     * @param textureID 当前机器 tier 对应的外壳贴图索引
     */
    public static void updateDualHatchTextures(List<? extends IDualInputHatch> duals, int textureID) {
        if (duals == null) return;
        for (IDualInputHatch dual : duals) {
            if (dual != null) dual.updateTexture(textureID);
        }
    }
}
