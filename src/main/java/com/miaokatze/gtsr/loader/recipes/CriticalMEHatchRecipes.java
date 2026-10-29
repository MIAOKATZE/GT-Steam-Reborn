package com.miaokatze.gtsr.loader.recipes;

import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.get;

import java.util.Collection;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTRecipe;
import gtPlusPlus.core.material.MaterialsAlloy;
import gtnhintergalactic.recipe.IGRecipeMaps;

/** UHV upgrades of native ME hatches, manufactured by the Space Elevator's assembly module. */
public final class CriticalMEHatchRecipes {

    private CriticalMEHatchRecipes() {}

    public static void register() {
        register(
            GTSRItemList.CriticalInputHatchME,
            ItemList.Hatch_Input_ME_Advanced,
            ItemList.Electric_Pump_UHV,
            ItemList.Sensor_UHV);
        register(
            GTSRItemList.CriticalInputBusME,
            ItemList.Hatch_Input_Bus_ME_Advanced,
            ItemList.Conveyor_Module_UHV,
            ItemList.Sensor_UHV);
        register(
            GTSRItemList.CriticalOutputHatchME,
            ItemList.Hatch_Output_ME,
            ItemList.Electric_Pump_UHV,
            ItemList.Emitter_UHV);
        register(
            GTSRItemList.CriticalOutputBusME,
            ItemList.Hatch_Output_Bus_ME,
            ItemList.Conveyor_Module_UHV,
            ItemList.Emitter_UHV);
    }

    private static void register(GTSRItemList output, ItemList original, ItemList transport, ItemList control) {
        Collection<GTRecipe> added = GTValues.RA.stdBuilder()
            .itemInputs(
                original.get(1),
                ItemList.Hull_MAX.get(1),
                transport.get(2),
                control.get(2),
                new Object[] { OrePrefixes.circuit.get(Materials.UHV), 4 },
                get(OrePrefixes.plate, Materials.CosmicNeutronium, 8),
                get(OrePrefixes.cableGt02, Materials.Bedrockium, 4),
                GTSRItemList.CriticalSteamEntangledSingularity.get(16))
            .itemOutputs(output.get(1))
            .fluidInputs(
                MaterialsAlloy.INDALLOY_140.getFluidStack(2304),
                Materials.Lubricant.getFluid(1000),
                Materials.Polybenzimidazole.getMolten(1152))
            .metadata(IGRecipeMaps.MODULE_TIER, 1)
            .duration(60 * 20)
            .eut(TierEU.RECIPE_UHV)
            .addTo(IGRecipeMaps.spaceAssemblerRecipes);
        if (added.size() != 1) throw new IllegalStateException("Invalid critical ME space assembly recipe: " + output);
    }
}
