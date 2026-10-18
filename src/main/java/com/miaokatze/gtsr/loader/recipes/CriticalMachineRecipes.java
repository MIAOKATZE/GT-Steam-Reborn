package com.miaokatze.gtsr.loader.recipes;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.CriticalEnergyMath;
import com.miaokatze.gtsr.common.critical.CriticalEnergyRuntime;
import com.miaokatze.gtsr.common.critical.CriticalGTRecipes;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.items.IrradiatedDust;

import gregtech.api.enums.GTValues;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gtPlusPlus.core.material.MaterialMisc;

/** Real work-recipe views only. Critical controllers, casings and loom materials have no crafting recipes. */
public final class CriticalMachineRecipes {

    private static boolean registered;

    private CriticalMachineRecipes() {}

    public static void register() {
        if (registered) return;
        registerSolar();
        registerTurbine();
        registerChemicalFuels();
        for (GTRecipe original : RecipeMaps.fusionRecipes.getAllRecipes()) {
            if (!original.mEnabled || original.mFakeRecipe || original.mEUt < 0) continue;
            CriticalRecipeMaps
                .addDisplayRecipe(CriticalMachineKind.SUN, original.copy(), "sun", BigInteger.valueOf(original.mEUt));
        }
        registered = true;
    }

    private static void registerSolar() {
        for (int mode = 0; mode < 6; mode++) {
            FluidStack steam = CriticalEnergyRuntime.steam(mode);
            if (steam == null) continue;
            steam.amount = mode < 3 ? 160 : 4;
            add(
                CriticalMachineKind.SOLAR,
                "solar_fluid",
                new ItemStack[0],
                new ItemStack[0],
                new FluidStack[] { GTModHandler.getDistilledWater(mode < 3 ? 1 : 25) },
                new FluidStack[] { steam },
                BigInteger.ZERO);
        }
        add(
            CriticalMachineKind.SOLAR,
            "solar_fluid",
            new ItemStack[0],
            new ItemStack[0],
            new FluidStack[] { MaterialMisc.SOLAR_SALT_COLD.getFluidStack(1) },
            new FluidStack[] { MaterialMisc.SOLAR_SALT_HOT.getFluidStack(1) },
            BigInteger.ZERO);
        for (int i = 0; i < 2; i++) {
            ItemStack source = IrradiatedDust.source(i == 0);
            if (source == null) continue;
            add(
                CriticalMachineKind.SOLAR,
                "solar_dust",
                new ItemStack[] { source },
                new ItemStack[] {
                    (i == 0 ? GTSRItemList.IrradiatedMoonDust : GTSRItemList.IrradiatedStoneDust).get(1) },
                new FluidStack[0],
                new FluidStack[0],
                BigInteger.ZERO);
        }
    }

    private static void registerTurbine() {
        for (int mode = 0; mode < 6; mode++) {
            FluidStack steam = CriticalEnergyRuntime.steam(mode);
            if (steam == null) continue;
            steam.amount = mode < 3 ? 160 : 4;
            BigInteger baseEu = CriticalEnergyMath.steamHeatPerL(mode)
                .multiply(BigInteger.valueOf(steam.amount))
                .divide(BigInteger.valueOf(2));
            add(
                CriticalMachineKind.TURBINE,
                "turbine",
                new ItemStack[0],
                new ItemStack[0],
                new FluidStack[] { steam },
                new FluidStack[] { GTModHandler.getDistilledWater(mode < 3 ? 1 : 25) },
                baseEu);
        }
    }

    private static void registerChemicalFuels() {
        for (CriticalGTRecipes.ChemicalFuel fuel : CriticalGTRecipes.chemicalFuels()) {
            FluidStack fluid = fuel.fluid.copy();
            fluid.amount = 1;
            add(
                CriticalMachineKind.BATTERY,
                "battery",
                new ItemStack[0],
                new ItemStack[0],
                new FluidStack[] { fluid },
                new FluidStack[0],
                fuel.euPerL);
        }
    }

    private static void add(CriticalMachineKind kind, String operation, ItemStack[] items, ItemStack[] outputs,
        FluidStack[] fluids, FluidStack[] fluidOutputs, BigInteger baseEu) {
        GTRecipe recipe = GTValues.RA.stdBuilder()
            .itemInputs(items)
            .itemOutputs(outputs)
            .fluidInputs(fluids)
            .fluidOutputs(fluidOutputs)
            .duration(CriticalEnergyMath.CYCLE_TICKS)
            .eut(0)
            .build()
            .orElseThrow(() -> new IllegalStateException("Invalid critical runtime view: " + operation));
        CriticalRecipeMaps.addDisplayRecipe(kind, recipe, operation, baseEu);
    }
}
