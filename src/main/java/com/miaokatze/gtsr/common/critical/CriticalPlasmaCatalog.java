package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

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
            List<Entry> resolved = new ArrayList<>();
            for (Materials material : new Materials[] { Materials.Nitrogen, Materials.Sulfur, Materials.Calcium,
                Materials.Nickel, Materials.Zinc, Materials.Niobium, Materials.Silver, Materials.Tin, Materials.Bismuth,
                Materials.Radon }) {
                FluidStack plasma = material.getPlasma(1);
                Entry found = null;
                for (GTRecipe recipe : GoodGeneratorRecipeMaps.extremeHeatExchangerFuels.getAllRecipes()) {
                    if (recipe.mEnabled && !recipe.mFakeRecipe
                        && recipe instanceof ExtremeHeatExchangerRecipe heat
                        && plasma != null
                        && plasma.isFluidEqual(recipe.mFluidInputs[0])) {
                        if (found != null)
                            throw new IllegalStateException("Duplicate GG plasma exchanger: " + material);
                        found = new Entry(material.mName, heat);
                    }
                }
                // Fusion plasma alone is insufficient: GG omits candidates without a recoverable cold fluid.
                if (found != null && isRotorFuel(plasma)) resolved.add(found);
            }
            if (resolved.isEmpty()) throw new IllegalStateException(
                "No eligible native GG plasma exchangers; recipe registration must be complete");
            entries = java.util.Collections.unmodifiableList(resolved);
        }
        return entries;
    }

    private static boolean isRotorFuel(FluidStack plasma) {
        for (GTRecipe fuel : RecipeMaps.plasmaFuels.getAllRecipes()) {
            if (fuel.mEnabled && !fuel.mFakeRecipe
                && fuel.mSpecialValue > 0
                && fuel.mInputs.length > 0
                && plasma.isFluidEqual(GTUtility.getFluidForFilledItem(fuel.mInputs[0], true))) return true;
        }
        return false;
    }

    public static final class Entry {

        public final String id;
        public final FluidStack input, output;
        /** One native second consumes hotLitres and really emits denseSteamLitres, including tick rounding. */
        public final BigInteger hotLitres, denseSteamLitres, halfSaturation;

        private Entry(String id, ExtremeHeatExchangerRecipe recipe) {
            this.id = id;
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
