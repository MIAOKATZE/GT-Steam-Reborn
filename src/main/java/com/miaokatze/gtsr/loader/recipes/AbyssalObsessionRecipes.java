package com.miaokatze.gtsr.loader.recipes;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialMaterials;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeConstants;
import gregtech.common.tileentities.machines.basic.MTERockBreaker;

/** Independent abyssal route; existing prosperity R6 stages and material IDs remain stable. */
public final class AbyssalObsessionRecipes {

    public static final int ROCK_BREAKER_CIRCUIT = 24;
    private static boolean registered;

    private AbyssalObsessionRecipes() {}

    public static void register() {
        if (registered) throw new IllegalStateException("Abyssal obsession recipes already registered");
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
        // Singleblocks have one fluid input. UniversalChemical converts the filled cell to a second
        // fluid input for the LCR and removes the returned empty cell from that variant.
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator
                    .get(OrePrefixes.cell, ProsperityIndustrialMaterials.get("planned:prosperity_obsession"), 1))
            .itemOutputs(ItemList.Cell_Empty.get(1))
            .fluidInputs(GTSRProsperityAirMaterials.SanzuResidualSteam.getGas(1000))
            .fluidOutputs(GTSRProsperityAirMaterials.AbyssalObsession.getFluid(2000))
            .duration(200)
            .eut(120)
            .addTo(GTRecipeConstants.UniversalChemical);

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
            .addTo(RecipeMaps.multiblockRockBreakerRecipes);
        registered = true;
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
