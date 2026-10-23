package com.miaokatze.gtsr.api.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputSlave;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/**
 * GT205 工作流体访问。扣除方法返回实际支付量，部分支付由调用方持久化托管。
 * 原生 ME 使用公开网络代理实扣，避免配方窗口内 drain 仅修改虚拟 extracted。
 * 原生 PatternSlot 是 AE 已投料的持久引用；Slave 归并到真实 master。
 * 未知第三方 dual 必须另行适配，不能假设其数组可写且持久。
 */
public final class GTSRHatchFluidAccess {

    private GTSRHatchFluidAccess() {}

    public static FluidStack probeFluidAmount(MTEHatch hatch, Fluid fluid, int amount) {
        return fluid == null ? null : probeFluidAmount(hatch, new FluidStack(fluid, amount));
    }

    public static FluidStack probeFluidAmount(MTEHatch hatch, FluidStack want) {
        if (hatch == null || want == null || want.amount <= 0) return null;
        FluidTankInfo[] tanks = hatch.getTankInfo(ForgeDirection.UNKNOWN);
        if (tanks == null) return null;
        long available = 0;
        for (FluidTankInfo tank : tanks) {
            if (tank == null || tank.fluid == null || !tank.fluid.isFluidEqual(want)) continue;
            // ME 多个过滤槽会各自报告同一网络存量。
            if (hatch instanceof MTEHatchInputME) available = Math.max(available, tank.fluid.amount);
            else available += Math.max(0, tank.fluid.amount);
        }
        if (available <= 0) return null;
        FluidStack copy = want.copy();
        copy.amount = (int) Math.min(available, want.amount);
        return copy;
    }

    /** 返回实际扣除量；请求的 NBT 始终保留。 */
    public static int drainFluidExact(MTEHatch hatch, FluidStack want) {
        FluidStack request = probeFluidAmount(hatch, want);
        if (request == null) return 0;
        FluidStack real;
        if (hatch instanceof MTEHatchInputME me) {
            // 不修改 extracted，父 endRecipeProcessing 不会再次收费。
            if (me.getClass() != MTEHatchInputME.class) return 0;
            try {
                AENetworkProxy proxy = me.getProxy();
                IAEFluidStack extracted = Platform.poweredExtraction(
                    proxy.getEnergy(),
                    proxy.getStorage()
                        .getFluidInventory(),
                    AEFluidStack.create(request),
                    new MachineSource((IActionHost) me.getBaseMetaTileEntity()));
                real = extracted == null ? null : extracted.getFluidStack();
            } catch (GridAccessException unavailable) {
                return 0;
            }
        } else {
            real = hatch.drain(ForgeDirection.UNKNOWN, request.copy(), true);
        }
        if (real == null || !real.isFluidEqual(want)) return 0;
        return Math.max(0, Math.min(request.amount, real.amount));
    }

    public static boolean hasFluid(MTEHatch hatch, Fluid fluid, int amount) {
        return probeFluidAmount(hatch, fluid, amount) != null;
    }

    /** 每个 ME 网络只计一次；同网多个过滤仓选择可见量最多的一仓。 */
    private static List<MTEHatch> distinctSources(List<? extends MTEHatch> hatches, FluidStack want) {
        IdentityHashMap<Object, MTEHatch> sources = new IdentityHashMap<>();
        for (MTEHatch hatch : GTUtility.validMTEList(hatches)) {
            Object identity = hatch;
            if (hatch instanceof MTEHatchInputME me) {
                if (me.getClass() != MTEHatchInputME.class) continue;
                try {
                    identity = me.getProxy()
                        .getStorage()
                        .getFluidInventory();
                } catch (GridAccessException unavailable) {
                    continue;
                }
            }
            MTEHatch previous = sources.get(identity);
            FluidStack current = probeFluidAmount(hatch, want);
            FluidStack old = previous == null ? null : probeFluidAmount(previous, want);
            if (previous == null || (current != null && (old == null || current.amount > old.amount))) {
                sources.put(identity, hatch);
            }
        }
        return new ArrayList<>(sources.values());
    }

    public static int depleteFluidAcross(List<? extends MTEHatch> hatches, FluidStack want) {
        if (hatches == null || want == null || want.amount <= 0) return 0;
        int remaining = want.amount;
        for (MTEHatch hatch : distinctSources(hatches, want)) {
            if (remaining <= 0) break;
            FluidStack request = want.copy();
            request.amount = remaining;
            remaining -= drainFluidExact(hatch, request);
        }
        return want.amount - remaining;
    }

    public static boolean hasEnoughAcross(List<? extends MTEHatch> hatches, FluidStack want) {
        return want != null && want.amount > 0 && probeFluidAmountAcross(hatches, want) >= want.amount;
    }

    public static long probeFluidAmountAcross(List<? extends MTEHatch> hatches, FluidStack want) {
        if (hatches == null || want == null || want.amount <= 0) return 0;
        long available = 0;
        for (MTEHatch hatch : distinctSources(hatches, want)) {
            FluidStack sim = probeFluidAmount(hatch, want);
            if (sim != null) available += sim.amount;
            if (available >= want.amount) break;
        }
        return Math.min(available, want.amount);
    }

    private static MTEHatchCraftingInputME nativeOwner(IDualInputHatch dual) {
        if (dual != null && dual.getClass() == MTEHatchCraftingInputME.class) {
            return (MTEHatchCraftingInputME) dual;
        }
        if (dual != null && dual.getClass() == MTEHatchCraftingInputSlave.class) {
            MTEHatchCraftingInputME master = ((MTEHatchCraftingInputSlave) dual).getMaster();
            return master != null && master.getClass() == MTEHatchCraftingInputME.class ? master : null;
        }
        return null;
    }

    public static boolean hasUnsupportedDuals(List<? extends IDualInputHatch> duals) {
        if (duals == null) return false;
        for (IDualInputHatch dual : duals) {
            if (dual != null && dual.getClass() != MTEHatchCraftingInputME.class
                && dual.getClass() != MTEHatchCraftingInputSlave.class) return true;
        }
        return false;
    }

    public static boolean hasUnsupportedWorkingFluidSources(List<? extends MTEHatch> hatches,
        List<? extends IDualInputHatch> duals) {
        if (hasUnsupportedDuals(duals)) return true;
        if (hatches != null) {
            for (MTEHatch hatch : hatches) {
                if (hatch instanceof MTEHatchInputME && hatch.getClass() != MTEHatchInputME.class) return true;
            }
        }
        if (duals != null) {
            for (IDualInputHatch dual : duals) {
                if (dual instanceof MTEHatchCraftingInputSlave slave && slave.getMaster() != null
                    && slave.getMaster()
                        .getClass() != MTEHatchCraftingInputME.class)
                    return true;
            }
        }
        return false;
    }

    private static int accessDuals(List<? extends IDualInputHatch> duals, FluidStack want, boolean consume,
        IntConsumer paid) {
        if (duals == null || want == null || want.amount <= 0) return 0;
        Set<IDualInputInventory> inventories = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<FluidStack> stacks = Collections.newSetFromMap(new IdentityHashMap<>());
        int remaining = want.amount;
        for (IDualInputHatch dual : duals) {
            MTEHatchCraftingInputME owner = nativeOwner(dual);
            if (owner == null) continue;
            boolean changed = false;
            for (Iterator<? extends IDualInputInventory> it = dual.inventories(); it.hasNext();) {
                IDualInputInventory inventory = it.next();
                if (!(inventory instanceof MTEHatchCraftingInputME.PatternSlot<?>) || !inventories.add(inventory))
                    continue;
                for (FluidStack stack : inventory.getFluidInputs()) {
                    if (stack == null || !stacks.add(stack) || stack.amount <= 0 || !stack.isFluidEqual(want)) continue;
                    int take = Math.min(remaining, stack.amount);
                    if (consume) {
                        stack.amount -= take;
                        changed |= take > 0;
                        if (take > 0 && paid != null) paid.accept(take);
                    }
                    remaining -= take;
                    if (remaining <= 0) break;
                }
                if (remaining <= 0) break;
            }
            if (changed) owner.getBaseMetaTileEntity()
                .markDirty();
            if (remaining <= 0) break;
        }
        return want.amount - remaining;
    }

    public static int depleteFluidFromDuals(List<? extends IDualInputHatch> duals, FluidStack want) {
        return accessDuals(duals, want, true, null);
    }

    public static int probeFluidAmountFromDuals(List<? extends IDualInputHatch> duals, FluidStack want) {
        return accessDuals(duals, want, false, null);
    }

    public static int probeWorkingFluid(List<? extends MTEHatch> hatches, List<? extends IDualInputHatch> duals,
        FluidStack want) {
        if (want == null || want.amount <= 0) return 0;
        int ordinary = (int) probeFluidAmountAcross(hatches, want);
        FluidStack remaining = want.copy();
        remaining.amount -= ordinary;
        return ordinary + probeFluidAmountFromDuals(duals, remaining);
    }

    public static int drainWorkingFluid(List<? extends MTEHatch> hatches, List<? extends IDualInputHatch> duals,
        FluidStack want) {
        if (want == null || want.amount <= 0) return 0;
        int ordinary = depleteFluidAcross(hatches, want);
        FluidStack remaining = want.copy();
        remaining.amount -= ordinary;
        return ordinary + depleteFluidFromDuals(duals, remaining);
    }

    /** 每笔确定支付立即回写托管；后续源异常不能丢掉已经支付的前缀。 */
    public static void payWorkingFluid(List<? extends MTEHatch> hatches, List<? extends IDualInputHatch> duals,
        FluidStack want, IntConsumer paid) {
        FluidStack remaining = want.copy();
        for (MTEHatch hatch : distinctSources(hatches, want)) {
            if (remaining.amount <= 0) break;
            int taken = drainFluidExact(hatch, remaining);
            if (taken > 0) paid.accept(taken);
            remaining.amount -= taken;
        }
        if (remaining.amount > 0) accessDuals(duals, remaining, true, paid);
    }

    public static void updateDualHatchTextures(List<? extends IDualInputHatch> duals, int textureID) {
        if (duals == null) return;
        for (IDualInputHatch dual : duals) {
            if (dual != null) dual.updateTexture(textureID);
        }
    }
}
