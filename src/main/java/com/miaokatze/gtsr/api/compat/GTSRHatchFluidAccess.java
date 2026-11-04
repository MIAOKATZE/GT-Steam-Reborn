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

import com.miaokatze.gtsr.common.critical.CriticalEnergyPorts.LongFluidSource;
import com.miaokatze.gtsr.common.machine.hatch.critical.MTEHatchCriticalInputME;

import appeng.api.config.Actionable;
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
 * GT265 工作流体访问。扣除方法返回实际支付量，部分支付由调用方持久化托管。
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
        long available = hatch instanceof MTEHatchInputME me ? probeMeFluidAmount(me, want, want.amount)
            : probeOrdinaryFluidAmount(hatch, want, want.amount);
        if (available <= 0) return null;
        FluidStack result = want.copy();
        result.amount = (int) available;
        return result;
    }

    private static long probeMeFluidAmount(MTEHatchInputME me, FluidStack identity, long amount) {
        if (me.getClass() == MTEHatchCriticalInputME.class) {
            return accessCriticalFluid((MTEHatchCriticalInputME) me, identity, amount, false);
        }
        if (me.getClass() != MTEHatchInputME.class || !me.isAllowedToWork()) return 0;
        boolean configured = false;
        for (FluidStack fluid : me.getStoredFluids()) if (fluid != null && fluid.isFluidEqual(identity)) {
            configured = true;
            break;
        }
        if (!configured) return 0;
        IAEFluidStack request = AEFluidStack.create(new FluidStack(identity, 1));
        request.setStackSize(amount);
        try {
            IAEFluidStack available = me.getProxy()
                .getStorage()
                .getFluidInventory()
                .extractItems(
                    request,
                    Actionable.SIMULATE,
                    new MachineSource((IActionHost) me.getBaseMetaTileEntity()));
            return available == null ? 0 : Math.min(amount, available.getStackSize());
        } catch (GridAccessException unavailable) {
            return 0;
        }
    }

    private static long accessCriticalFluid(MTEHatchCriticalInputME hatch, FluidStack identity, long amount,
        boolean consume) {
        LongFluidSource best = null;
        long available = 0;
        for (int slot = 0; slot < MTEHatchInputME.SLOT_COUNT; slot++) {
            LongFluidSource source = hatch.getLongInputSource(slot);
            if (source == null || !source.prototype()
                .isFluidEqual(identity)) continue;
            long offered = source.extract(Math.min(amount, hatch.getLongSlotLimit(slot)), false);
            if (offered > available) {
                best = source;
                available = offered;
            }
        }
        return consume && best != null ? best.extract(available, true) : available;
    }

    private static long probeOrdinaryFluidAmount(MTEHatch hatch, FluidStack identity, long amount) {
        FluidTankInfo[] tanks = hatch.getTankInfo(ForgeDirection.UNKNOWN);
        if (tanks == null) return 0;
        long available = 0;
        for (FluidTankInfo tank : tanks) {
            if (tank == null || tank.fluid == null || !tank.fluid.isFluidEqual(identity)) continue;
            available += Math.min(amount - available, Math.max(0, tank.fluid.amount));
            if (available == amount) break;
        }
        return available;
    }

    /** 返回实际扣除量；请求的 NBT 始终保留。 */
    public static int drainFluidExact(MTEHatch hatch, FluidStack want) {
        FluidStack request = probeFluidAmount(hatch, want);
        if (request == null) return 0;
        FluidStack real;
        if (hatch instanceof MTEHatchInputME me) {
            // 不修改 extracted，父 endRecipeProcessing 不会再次收费。
            if (me.getClass() == MTEHatchCriticalInputME.class)
                return (int) accessCriticalFluid((MTEHatchCriticalInputME) me, want, request.amount, true);
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
        return distinctSources(hatches, want, want.amount);
    }

    private static List<MTEHatch> distinctSources(List<? extends MTEHatch> hatches, FluidStack want, long amount) {
        IdentityHashMap<Object, MTEHatch> sources = new IdentityHashMap<>();
        for (MTEHatch hatch : GTUtility.validMTEList(hatches)) {
            Object identity = hatch;
            if (hatch instanceof MTEHatchInputME me) {
                if (me.getClass() != MTEHatchInputME.class && me.getClass() != MTEHatchCriticalInputME.class) continue;
                try {
                    identity = me.getProxy()
                        .getStorage()
                        .getFluidInventory();
                } catch (GridAccessException unavailable) {
                    continue;
                }
            }
            MTEHatch previous = sources.get(identity);
            long current = hatch instanceof MTEHatchInputME me ? probeMeFluidAmount(me, want, amount)
                : probeOrdinaryFluidAmount(hatch, want, amount);
            long old = previous == null ? 0
                : previous instanceof MTEHatchInputME me ? probeMeFluidAmount(me, want, amount)
                    : probeOrdinaryFluidAmount(previous, want, amount);
            if (previous == null || current > old) {
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

    /** Long 请求保留完整 AE 数量；普通仓每个真实罐只探测一次，同一 ME 网络只计一次。 */
    public static long probeFluidAmountAcross(List<? extends MTEHatch> hatches, FluidStack identity, long amount) {
        if (hatches == null || identity == null || amount <= 0) return 0;
        long remaining = amount;
        for (MTEHatch hatch : distinctSources(hatches, identity, amount)) {
            remaining -= hatch instanceof MTEHatchInputME me ? probeMeFluidAmount(me, identity, remaining)
                : probeOrdinaryFluidAmount(hatch, identity, remaining);
            if (remaining == 0) break;
        }
        return amount - remaining;
    }

    /** 返回实际 long 支付量；AE 单次有界提取，普通仓按 int 块实扣后才继续下一块。 */
    public static long depleteFluidAcross(List<? extends MTEHatch> hatches, FluidStack identity, long amount) {
        if (hatches == null || identity == null || amount <= 0) return 0;
        long remaining = amount;
        for (MTEHatch hatch : distinctSources(hatches, identity, amount)) {
            if (hatch instanceof MTEHatchInputME me) {
                if (me.getClass() == MTEHatchCriticalInputME.class) {
                    remaining -= accessCriticalFluid((MTEHatchCriticalInputME) me, identity, remaining, true);
                    if (remaining == 0) break;
                    continue;
                }
                long available = probeMeFluidAmount(me, identity, remaining);
                if (available == 0) continue;
                IAEFluidStack request = AEFluidStack.create(new FluidStack(identity, 1));
                request.setStackSize(available);
                try {
                    AENetworkProxy proxy = me.getProxy();
                    IAEFluidStack extracted = Platform.poweredExtraction(
                        proxy.getEnergy(),
                        proxy.getStorage()
                            .getFluidInventory(),
                        request,
                        new MachineSource((IActionHost) me.getBaseMetaTileEntity()));
                    if (extracted != null) remaining -= Math.min(available, extracted.getStackSize());
                } catch (GridAccessException unavailable) {
                    continue;
                }
            } else {
                while (remaining > 0) {
                    int taken = drainFluidExact(
                        hatch,
                        new FluidStack(identity, (int) Math.min(remaining, Integer.MAX_VALUE)));
                    if (taken == 0) break;
                    remaining -= taken;
                }
            }
            if (remaining == 0) break;
        }
        return amount - remaining;
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

    public static boolean isWorkingFluidDualInput(IDualInputHatch dual) {
        MTEHatchCraftingInputME owner = nativeOwner(dual);
        return owner != null && owner.supportsFluids();
    }

    public static boolean hasWorkingFluidDualInput(List<? extends IDualInputHatch> duals) {
        for (IDualInputHatch dual : duals) if (isWorkingFluidDualInput(dual)) return true;
        return false;
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
                if (hatch instanceof MTEHatchInputME && hatch.getClass() != MTEHatchInputME.class
                    && hatch.getClass() != MTEHatchCriticalInputME.class) return true;
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

    private static long accessDuals(List<? extends IDualInputHatch> duals, FluidStack want, long amount,
        boolean consume, IntConsumer paid) {
        if (duals == null || want == null || amount <= 0) return 0;
        Set<IDualInputInventory> inventories = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<FluidStack> stacks = Collections.newSetFromMap(new IdentityHashMap<>());
        long remaining = amount;
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
                    int take = (int) Math.min(remaining, stack.amount);
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
        return amount - remaining;
    }

    /** Type discovery only: quantities must be probed against the real paid pattern inventories. */
    public static List<FluidStack> getDualFluidIdentities(List<? extends IDualInputHatch> duals) {
        List<FluidStack> fluids = new ArrayList<>();
        for (IDualInputHatch dual : duals) {
            if (nativeOwner(dual) == null) continue;
            for (Iterator<? extends IDualInputInventory> it = dual.inventories(); it.hasNext();) {
                IDualInputInventory inventory = it.next();
                if (!(inventory instanceof MTEHatchCraftingInputME.PatternSlot<?>)) continue;
                for (FluidStack fluid : inventory.getFluidInputs()) {
                    if (fluid == null || fluid.amount <= 0) continue;
                    boolean duplicate = false;
                    for (FluidStack known : fluids) if (known.isFluidEqual(fluid)) {
                        duplicate = true;
                        break;
                    }
                    if (!duplicate) fluids.add(new FluidStack(fluid, 1));
                }
            }
        }
        return fluids;
    }

    public static int depleteFluidFromDuals(List<? extends IDualInputHatch> duals, FluidStack want) {
        return want == null ? 0 : (int) accessDuals(duals, want, want.amount, true, null);
    }

    public static int probeFluidAmountFromDuals(List<? extends IDualInputHatch> duals, FluidStack want) {
        return want == null ? 0 : (int) accessDuals(duals, want, want.amount, false, null);
    }

    public static long depleteFluidFromDuals(List<? extends IDualInputHatch> duals, FluidStack identity, long amount) {
        return accessDuals(duals, identity, amount, true, null);
    }

    public static long probeFluidAmountFromDuals(List<? extends IDualInputHatch> duals, FluidStack identity,
        long amount) {
        return accessDuals(duals, identity, amount, false, null);
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
        if (remaining.amount > 0) accessDuals(duals, remaining, remaining.amount, true, paid);
    }

    public static void updateDualHatchTextures(List<? extends IDualInputHatch> duals, int textureID) {
        if (duals == null) return;
        for (IDualInputHatch dual : duals) {
            if (dual != null) dual.updateTexture(textureID);
        }
    }
}
