// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader.recipes;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.compat.GTVersionCompat;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.fluid.ProsperityFluidContainers;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialMaterials;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.common.tileentities.machines.basic.MTERockBreaker;

/** Independent abyssal route; existing prosperity R6 stages and material IDs remain stable. */
public final class AbyssalObsessionRecipes {

    public static final int ROCK_BREAKER_CIRCUIT = 24;
    private static boolean registered;

    private AbyssalObsessionRecipes() {}

    public static void register() {
        if (registered) throw new IllegalStateException("Abyssal obsession recipes already registered");
        RecipeMap<?> rockBreakerRecipes = GTVersionCompat.gppRecipeMap("multiblockRockBreakerRecipes");
        if (rockBreakerRecipes == null)
            throw new IllegalStateException("Missing required GT recipe map: multiblockRockBreakerRecipes");
        GTValues.RA.stdBuilder()
            .itemInputs(new ItemStack(BlocksGTSR.prosperityStone))
            .itemOutputs(
                GTOreDictUnificator
                    .get(OrePrefixes.dust, ProsperityIndustrialMaterials.get("planned:prosperity_dust"), 1))
            .duration(100)
            .eut(30)
            .addTo(RecipeMaps.maceratorRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator
                    .get(OrePrefixes.dust, ProsperityIndustrialMaterials.get("planned:prosperity_dust"), 1))
            .fluidInputs(GTModHandler.getDistilledWater(1000))
            .fluidOutputs(
                ProsperityIndustrialMaterials.get("planned:prosperity_reminiscence")
                    .getFluid(1000))
            .duration(200)
            .eut(30)
            .addTo(RecipeMaps.mixerRecipes);
        GTValues.RA.stdBuilder()
            .fluidInputs(
                ProsperityIndustrialMaterials.get("planned:prosperity_reminiscence")
                    .getFluid(1000))
            .fluidOutputs(
                ProsperityIndustrialMaterials.get("planned:prosperity_obsession")
                    .getFluid(1000))
            .duration(100)
            .eut(120)
            .addTo(RecipeMaps.fluidHeaterRecipes);
        registerAbyssalReaction();

        registerSingleRockBreaker(Blocks.lava);
        registerSingleRockBreaker(Blocks.flowing_lava);
        // The GT multiblock retains its built-in water/lava structure. A zero-size item input
        // requires the filled bucket to be present in its bus without consuming its contents.
        GTValues.RA.stdBuilder()
            .itemInputs(GTSRItemList.AbyssalObsessionBucket.get(0))
            .circuit(ROCK_BREAKER_CIRCUIT)
            .itemOutputs(new ItemStack(BlocksGTSR.prosperityStone))
            .duration(100)
            .eut(30)
            .addTo(rockBreakerRecipes);
        registered = true;
    }

    /** One reaction bill, with the singleblock's second fluid supplied in a native NBT cell. */
    static void registerAbyssalReaction() {
        FluidStack obsession = ProsperityIndustrialMaterials.get("planned:prosperity_obsession")
            .getFluid(1000);
        FluidStack steam = GTSRProsperityAirMaterials.SanzuResidualSteam.getGas(1000);
        GTRecipeBuilder bill = GTValues.RA.stdBuilder()
            .fluidOutputs(GTSRProsperityAirMaterials.AbyssalObsession.getFluid(2000))
            .duration(200)
            .eut(120);
        bill.copy()
            .itemInputs(ProsperityFluidContainers.filledCell(obsession))
            .itemOutputs(ProsperityFluidContainers.emptyCell(1))
            .fluidInputs(steam)
            .nbtSensitive()
            .addTo(RecipeMaps.chemicalReactorRecipes);
        // UniversalChemical only removes the legacy Cell_Empty, so the all-fluid LCR variant
        // is explicit: no physical container entered this machine and none can be returned.
        bill.copy()
            .fluidInputs(obsession, steam)
            .addTo(RecipeMaps.multiblockChemicalReactorRecipes);
    }

    private static void registerSingleRockBreaker(Block lava) {
        MTERockBreaker.addRockBreakerRecipe(
            builder -> builder.sideBlocks(BlocksGTSR.abyssalFluid, lava)
                .circuit(ROCK_BREAKER_CIRCUIT)
                .recipeDescription("gtsr.recipe.rockbreaker.abyssal.side")
                .outputItem(new ItemStack(BlocksGTSR.prosperityStone))
                .duration(100));
    }
}
