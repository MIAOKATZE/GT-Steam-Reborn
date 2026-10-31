package com.miaokatze.gtsr.compat.gtnl;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;

/** Accesses only the paid local tank; network refilling remains the hatch's responsibility. */
public final class PipelessSteamSupport {

    private PipelessSteamSupport() {}

    @SuppressWarnings("unchecked")
    public static Class<? extends IMetaTileEntity> hatchClass() {
        try {
            return (Class<? extends IMetaTileEntity>) Class.forName(
                "com.science.gtnl.common.machine.hatch.WirelessSteamEnergyHatch",
                false,
                PipelessSteamSupport.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("GTNL pipeless compatibility loaded without its hatch", missing);
        }
    }

    public static boolean add(Object controller, IGregTechTileEntity tile, int casing) {
        if (!(controller instanceof IAdditionalSteamSourceHolder holder) || tile == null) return false;
        if (!(tile.getMetaTileEntity() instanceof MTEHatch hatch) || !hatchClass().isInstance(hatch)
            || hatch.mTier != 0) return false;
        hatch.updateTexture(casing);
        if (!holder.gtsr$getAdditionalSteamSources()
            .contains(hatch))
            holder.gtsr$getAdditionalSteamSources()
                .add(hatch);
        return true;
    }

    public static boolean matches(IMetaTileEntity tile, List<? extends Class<? extends IMetaTileEntity>> classes) {
        if (hatchClass().isInstance(tile) && ((MTEHatch) tile).mTier != 0) return false;
        return classes.stream()
            .anyMatch(type -> type.isInstance(tile));
    }

    public static int count(Object controller) {
        return controller instanceof IAdditionalSteamSourceHolder holder ? holder.gtsr$getAdditionalSteamSources()
            .size() : 0;
    }

    public static List<FluidStack> getStored(Object controller) {
        List<FluidStack> result = new ArrayList<>();
        if (controller instanceof IAdditionalSteamSourceHolder holder) {
            for (MTEHatch hatch : holder.gtsr$getAdditionalSteamSources()) {
                if (!hatch.isValid()) continue;
                FluidStack stack = hatch.drain(Integer.MAX_VALUE, false);
                if (stack != null && stack.amount > 0) result.add(stack.copy());
            }
        }
        return result;
    }

    public static long available(Object controller, FluidStack request) {
        return drain(controller, request, Long.MAX_VALUE, true);
    }

    public static long drain(Object controller, FluidStack request, long amount, boolean simulate) {
        if (!(controller instanceof IAdditionalSteamSourceHolder holder) || request == null || amount <= 0) return 0;
        long remaining = amount;
        for (MTEHatch hatch : holder.gtsr$getAdditionalSteamSources()) {
            if (!hatch.isValid() || remaining <= 0) continue;
            int limit = (int) Math.min(remaining, Integer.MAX_VALUE);
            FluidStack probe = hatch.drain(limit, false);
            if (probe == null || !probe.isFluidEqual(request) || probe.amount <= 0) continue;
            FluidStack drained = simulate ? probe : hatch.drain(Math.min(limit, probe.amount), true);
            if (drained != null && drained.isFluidEqual(request)) remaining -= drained.amount;
        }
        return amount - remaining;
    }
}
