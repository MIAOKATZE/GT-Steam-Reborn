package com.miaokatze.gtsr.api.compat;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.common.tileentities.machines.IDualInputHatch;

/** 单个工作周期的持久支付托管。未足额及结果不确定时绝不授权生产。 */
public final class GTSRWorkingFluidPayment {

    private FluidStack target;
    private int paid;
    private boolean uncertain;

    public FluidStack getTarget() {
        return target == null ? null : target.copy();
    }

    public boolean isUncertain() {
        return uncertain;
    }

    public boolean pay(List<? extends MTEHatch> hatches, List<? extends IDualInputHatch> duals, FluidStack request,
        Runnable dirty) {
        if (uncertain || request == null || request.amount <= 0) return false;
        if (target == null) {
            target = request.copy();
            dirty.run();
        }
        if (!target.isFluidEqual(request)) return false;
        FluidStack remaining = target.copy();
        remaining.amount -= paid;
        if (remaining.amount <= 0) return true;
        try {
            if (GTSRHatchFluidAccess.probeWorkingFluid(hatches, duals, remaining) < remaining.amount) return false;
            GTSRHatchFluidAccess.payWorkingFluid(hatches, duals, remaining, amount -> {
                paid += amount;
                dirty.run();
            });
        } catch (RuntimeException failure) {
            // 外部库存可能已修改；不能将异常当作零支付并自动重放。
            uncertain = true;
            dirty.run();
            return false;
        }
        return paid == target.amount;
    }

    /** 只能由已建立本周期输出的调用方消费收据；stopMachine 不清除此对象。 */
    public void complete() {
        if (target == null || uncertain || paid != target.amount)
            throw new IllegalStateException("Unpaid working fluid");
        target = null;
        paid = 0;
    }

    public void save(NBTTagCompound nbt, String key) {
        NBTTagCompound state = new NBTTagCompound();
        if (target != null) state.setTag("target", target.writeToNBT(new NBTTagCompound()));
        state.setInteger("paid", paid);
        state.setBoolean("uncertain", uncertain);
        nbt.setTag(key, state);
    }

    public void load(NBTTagCompound nbt, String key) {
        NBTTagCompound state = nbt.getCompoundTag(key);
        target = FluidStack.loadFluidStackFromNBT(state.getCompoundTag("target"));
        paid = state.getInteger("paid");
        uncertain = state.getBoolean("uncertain");
        if (paid < 0 || (target == null && paid != 0) || (target != null && paid > target.amount)) uncertain = true;
    }
}
