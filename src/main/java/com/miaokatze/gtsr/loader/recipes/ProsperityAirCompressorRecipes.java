package com.miaokatze.gtsr.loader.recipes;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;

/** Display recipes: actual extraction is dispatched by ProsperityAirLookup in the compressor. */
public final class ProsperityAirCompressorRecipes {

    private ProsperityAirCompressorRecipes() {}

    public static void register() {
        Materials[] feeds = { GTSRProsperityAirMaterials.WastesSigh, GTSRProsperityAirMaterials.ThickGrease,
            GTSRProsperityAirMaterials.MetalGrit, GTSRProsperityAirMaterials.UmbralMire,
            GTSRProsperityAirMaterials.SanzuResidualSteam, GTSRProsperityAirMaterials.WitheredBreath };
        for (Materials feed : feeds) {
            if (feed == null || feed.mGas == null) continue;
            GTValues.RA.stdBuilder()
                .fluidOutputs(feed.getGas(800))
                .duration(20)
                .eut(-60)
                .fake()
                .ignoreCollision()
                .addTo(GTSRRecipeMaps.airCompressorRecipes);
        }
    }

    public static String sourceKey(FluidStack stack) {
        if (stack == null) return null;
        Fluid fluid = stack.getFluid();
        if (fluid == null) return null;
        switch (fluid.getName()) {
            case "wastesigh":
                return "gtsr.air_compressor.source.rusted_steppe";
            case "thickgrease":
                return "gtsr.air_compressor.source.gearwork_forest";
            case "metalgrit":
                return "gtsr.air_compressor.source.brass_wastes";
            case "umbralmire":
                return "gtsr.air_compressor.source.fumarole_swamp";
            case "sanzu_residual_steam":
                return "gtsr.air_compressor.source.sanzu_river";
            case "withered_breath":
                return "gtsr.air_compressor.source.withered_riverbed";
            default:
                return null;
        }
    }
}
