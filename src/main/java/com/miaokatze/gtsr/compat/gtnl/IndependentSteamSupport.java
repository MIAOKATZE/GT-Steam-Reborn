package com.miaokatze.gtsr.compat.gtnl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.common.machine.MTEAmmoniaPlant;
import com.miaokatze.gtsr.common.machine.MTEGearSteamCompressor;
import com.miaokatze.gtsr.common.machine.MTEKineticProcessingArray;
import com.miaokatze.gtsr.common.machine.MTESiemensMartinFurnace;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

import appeng.me.GridAccessException;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/** Payments for the independent controllers retain their existing fluid and cost rules. */
public final class IndependentSteamSupport {

    private IndependentSteamSupport() {}

    public static boolean accepts(Object machine) {
        return machine instanceof MTEGearSteamCompressor || machine instanceof MTEKineticProcessingArray
            || machine instanceof MTESiemensMartinFurnace
            || machine instanceof MTEAmmoniaPlant
            || machine instanceof MTESteamMineralLogisticsCluster;
    }

    public static List<MTEHatch> withAdditional(Object machine, List<? extends MTEHatch> ordinary) {
        List<MTEHatch> sources = new ArrayList<>(ordinary);
        sources.addAll(((IAdditionalSteamSourceHolder) machine).gtsr$getAdditionalSteamSources());
        return sources;
    }

    public static long available(Object machine, List<? extends MTEHatch> ordinary, List<? extends MTEHatch> pressure,
        FluidStack fluid, long required) {
        if (fluid == null || required <= 0) return 0;
        FluidStack request = fluid.copy();
        request.amount = (int) Math.min(required, Integer.MAX_VALUE);
        // Keep a long total while counting each native ME network only once.
        IdentityHashMap<Object, Integer> amounts = new IdentityHashMap<>();
        for (MTEHatch hatch : GTUtility.validMTEList(ordinary)) {
            Object source = hatch;
            if (hatch instanceof MTEHatchInputME me) {
                if (me.getClass() != MTEHatchInputME.class) continue;
                try {
                    source = me.getProxy()
                        .getStorage()
                        .getFluidInventory();
                } catch (GridAccessException unavailable) {
                    continue;
                }
            }
            FluidStack simulated = GTSRHatchFluidAccess.probeFluidAmount(hatch, request);
            if (simulated != null) amounts.merge(source, simulated.amount, Math::max);
        }
        long amount = 0;
        for (int stored : amounts.values()) amount += stored;
        for (MTEHatch hatch : GTUtility.validMTEList(pressure)) {
            FluidStack simulated = hatch.drain(request.amount, false);
            if (simulated != null && simulated.isFluidEqual(fluid)) amount += simulated.amount;
        }
        return amount + PipelessSteamSupport.available(machine, request);
    }

    public static long drain(Object machine, List<? extends MTEHatch> ordinary, List<? extends MTEHatch> pressure,
        FluidStack fluid, long required) {
        long remaining = required;
        while (remaining > 0) {
            FluidStack request = fluid.copy();
            request.amount = (int) Math.min(remaining, Integer.MAX_VALUE);
            int paid = GTSRHatchFluidAccess.depleteFluidAcross(ordinary, request);
            remaining -= paid;
            if (paid < request.amount) break;
        }
        for (MTEHatch hatch : GTUtility.validMTEList(pressure)) {
            if (remaining <= 0) break;
            FluidStack simulated = hatch.drain((int) Math.min(remaining, Integer.MAX_VALUE), false);
            if (simulated == null || !simulated.isFluidEqual(fluid)) continue;
            FluidStack paid = hatch.drain(simulated.amount, true);
            if (paid != null && paid.isFluidEqual(fluid)) remaining -= paid.amount;
        }
        if (remaining > 0) remaining -= PipelessSteamSupport.drain(machine, fluid, remaining, false);
        return required - remaining;
    }

    public static List<FluidStack> steamKinds(Object machine, List<FluidStack> stored,
        List<? extends MTEHatch> pressure) {
        List<FluidStack> all = new ArrayList<>(stored);
        all.addAll(PipelessSteamSupport.getStored(machine));
        for (MTEHatch hatch : GTUtility.validMTEList(pressure)) {
            FluidStack fluid = hatch.drain(Integer.MAX_VALUE, false);
            if (fluid != null) all.add(fluid);
        }
        Set<Fluid> seen = new HashSet<>();
        all.removeIf(fluid -> fluid == null || !GTModHandler.isAnySteam(fluid) || !seen.add(fluid.getFluid()));
        return all;
    }
}
