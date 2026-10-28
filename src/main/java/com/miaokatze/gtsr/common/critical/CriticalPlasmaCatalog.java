package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import goodgenerator.api.recipe.ExtremeHeatExchangerRecipe;
import goodgenerator.api.recipe.GoodGeneratorRecipeMaps;
import gregtech.api.enums.Materials;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** Boiler working fluids, priced from the registered GG exchanger's actual full-load ticks. */
public final class CriticalPlasmaCatalog {

    private static List<Entry> entries;

    private CriticalPlasmaCatalog() {}

    public static List<Entry> entries() {
        if (entries == null) {
            Set<Fluid> fusionOutputs = new HashSet<>();
            for (GTRecipe recipe : RecipeMaps.fusionRecipes.getAllRecipes()) {
                if (!recipe.mEnabled || recipe.mFakeRecipe) continue;
                for (FluidStack output : recipe.mFluidOutputs)
                    if (output != null && output.amount > 0) fusionOutputs.add(output.getFluid());
            }
            Set<Fluid> rotorFuels = new HashSet<>();
            for (GTRecipe fuel : RecipeMaps.plasmaFuels.getAllRecipes()) {
                if (!fuel.mEnabled || fuel.mFakeRecipe || fuel.mSpecialValue <= 0 || fuel.mInputs.length == 0) continue;
                FluidStack fluid = GTUtility.getFluidForFilledItem(fuel.mInputs[0], true);
                if (fluid != null) rotorFuels.add(fluid.getFluid());
            }
            List<Entry> resolved = new ArrayList<>();
            Set<Fluid> seen = new HashSet<>();
            for (GTRecipe recipe : GoodGeneratorRecipeMaps.extremeHeatExchangerFuels.getAllRecipes()) {
                if (!recipe.mEnabled || recipe.mFakeRecipe || recipe.mFluidInputs.length == 0) continue;
                FluidStack plasma = recipe.mFluidInputs[0];
                if (!fusionOutputs.contains(plasma.getFluid()) || !rotorFuels.contains(plasma.getFluid())
                    || plasma.isFluidEqual(Materials.Helium.getPlasma(1))
                    || plasma.isFluidEqual(Materials.Iron.getPlasma(1))
                    || plasma.isFluidEqual(Materials.Americium.getPlasma(1))) continue;
                if (!seen.add(plasma.getFluid())) throw new IllegalStateException(
                    "Duplicate GG plasma exchanger: " + plasma.getFluid()
                        .getName());
                if (!(recipe instanceof ExtremeHeatExchangerRecipe)) throw new IllegalStateException(
                    "Unsupported GG plasma exchanger: " + plasma.getFluid()
                        .getName());
                resolved.add(new Entry((ExtremeHeatExchangerRecipe) recipe));
            }
            if (resolved.isEmpty()) throw new IllegalStateException(
                "No eligible native GG plasma exchangers; recipe registration must be complete");
            resolved.sort(Comparator.comparing(entry -> entry.id));
            entries = java.util.Collections.unmodifiableList(resolved);
        }
        return entries;
    }

    /** Fluid registry ids cover GT++ materials; old paid telemetry used GT Materials names. */
    public static String displayName(String id) {
        Fluid fluid = FluidRegistry.getFluid(id);
        if (fluid != null) return new FluidStack(fluid, 1).getLocalizedName();
        Materials legacy = Materials.get(id);
        if (legacy != Materials._NULL) return legacy.getLocalizedName();
        // Display-only history may outlive a removed fluid; never let it interrupt the machine terminal.
        return id;
    }

    public static final class Entry {

        public final String id;
        public final FluidStack input, output;
        /** One native second consumes hotLitres and really emits denseSteamLitres, including tick rounding. */
        public final BigInteger hotLitres, denseSteamLitres, halfSaturation;

        private Entry(ExtremeHeatExchangerRecipe recipe) {
            id = recipe.mFluidInputs[0].getFluid()
                .getName();
            if (recipe.mFluidInputs.length != 2 || recipe.mFluidOutputs.length != 3
                || recipe.getCooledFluid() == null
                || recipe.getNormalSteam() == null)
                throw new IllegalStateException("Unsupported GG plasma exchanger: " + id);
            input = new FluidStack(recipe.getCooledFluid(), 1);
            output = recipe.mFluidInputs[0].copy();
            output.amount = 1;
            hotLitres = BigInteger.valueOf(recipe.getMaxHotFluidConsume());
            // GT205: transformed plasma -> DSC; water per tick = EUt / 1 / 160, then steam = water*160/1000.
            if (recipe.getHeatedSteam() != Materials.DenseSupercriticalSteam.getGas(1)
                .getFluid()
                || !"ic2superheatedsteam".equals(
                    recipe.getNormalSteam()
                        .getName())
                || recipe.mSpecialValue != 1
                || hotLitres.signum() <= 0
                || recipe.getEUt() <= 0) throw new IllegalStateException("Unsupported GG plasma exchanger: " + id);
            denseSteamLitres = BigInteger.valueOf(recipe.getEUt() / 160L * 160L / 1000L * 20L);
            if (denseSteamLitres.signum() <= 0) throw new IllegalStateException("Empty GG plasma heat: " + id);
            halfSaturation = CriticalSolarPolicy.plasmaHalfSaturation(hotLitres, denseSteamLitres);
        }

        public BigInteger batchLitres(BigInteger stock, double factor, CriticalSolarPolicy.Entry coolant,
            CriticalSolarPolicy.Entry exciter) {
            return CriticalSolarPolicy.batchPlasmaLitres(stock, factor, coolant, exciter, hotLitres, denseSteamLitres);
        }

        /** Same full-turbine accounting unit as the unchanged coolant/exciter pricing. */
        public BigInteger equivalentEU(BigInteger litres) {
            return litres.multiply(denseSteamLitres)
                .multiply(BigInteger.valueOf(50_000))
                .divide(hotLitres);
        }

        public BigInteger ratedEquivalentEU(double factor, CriticalSolarPolicy.Entry coolant,
            CriticalSolarPolicy.Entry exciter) {
            return CriticalSolarPolicy.ratedBatchEquivalentEU(factor, coolant, exciter, false)
                .multiply(BigInteger.valueOf(5))
                .divide(BigInteger.valueOf(2));
        }
    }
}
