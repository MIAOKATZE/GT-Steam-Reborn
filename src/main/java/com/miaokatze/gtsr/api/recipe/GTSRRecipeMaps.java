package com.miaokatze.gtsr.api.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularui.common.widget.ProgressBar;

import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.nei.formatter.DefaultSpecialValueFormatter;
import gregtech.nei.formatter.INEISpecialInfoFormatter;

public class GTSRRecipeMaps {

    private static final INEISpecialInfoFormatter AIR_COMPRESSOR_FORMATTER = recipeInfo -> {
        List<String> result = new ArrayList<>(DefaultSpecialValueFormatter.INSTANCE.format(recipeInfo));
        if (recipeInfo.recipe.mFluidOutputs != null) {
            for (FluidStack output : recipeInfo.recipe.mFluidOutputs) {
                if (output != null && output.getFluid() != null
                    && "netherair".equals(
                        output.getFluid()
                            .getName())) {
                    result.add(
                        EnumChatFormatting.LIGHT_PURPLE
                            + StatCollector.translateToLocal("gtsr.tooltip.air_compressor.nether_recipe"));
                    break;
                }
            }
        }
        return result;
    };

    public static final RecipeMap<RecipeMapBackend> largeCokeOvenRecipes = RecipeMapBuilder
        .of("gtsr.recipe.large_coke_oven")
        .maxIO(1, 1, 0, 1)
        .minInputs(1, 0)
        .progressBar(GTUITextures.PROGRESSBAR_ARROW, ProgressBar.Direction.RIGHT)
        .build();

    public static final RecipeMap<RecipeMapBackend> siemensMartinRecipes = RecipeMapBuilder
        .of("gtsr.recipe.siemens_martin_furnace")
        .maxIO(2, 2, 0, 0)
        .minInputs(1, 0)
        .progressBar(GTUITextures.PROGRESSBAR_ARROW, ProgressBar.Direction.RIGHT)
        .build();

    public static final RecipeMap<RecipeMapBackend> ammoniaPlantRecipes = RecipeMapBuilder
        .of("gtsr.recipe.ammonia_plant")
        .maxIO(0, 2, 2, 1)
        .minInputs(0, 2)
        .progressBar(GTUITextures.PROGRESSBAR_ARROW, ProgressBar.Direction.RIGHT)
        .build();

    public static final RecipeMap<RecipeMapBackend> airCompressorRecipes = RecipeMapBuilder
        .of("gtsr.recipe.air_compressor")
        .maxIO(0, 0, 1, 1)
        .minInputs(0, 0)
        .progressBar(GTUITextures.PROGRESSBAR_ARROW, ProgressBar.Direction.RIGHT)
        .neiSpecialInfoFormatter(AIR_COMPRESSOR_FORMATTER)
        .build();

    public static final RecipeMap<RecipeMapBackend> atmosphericCentrifugeRecipes = RecipeMapBuilder
        .of("gtsr.recipe.atmospheric_centrifuge")
        .maxIO(0, 0, 1, 9)
        .minInputs(0, 1)
        .progressBar(GTUITextures.PROGRESSBAR_ARROW, ProgressBar.Direction.RIGHT)
        .frontend(FluidGridFrontend::new)
        .build();

    public static final RecipeMap<RecipeMapBackend> steamSingularityCompressorRecipes = RecipeMapBuilder
        .of("gtsr.recipe.steam_singularity_compressor")
        .maxIO(0, 1, 1, 0)
        .minInputs(0, 1)
        .progressBar(GTUITextures.PROGRESSBAR_ARROW, ProgressBar.Direction.RIGHT)
        .build();

    private static final INEISpecialInfoFormatter GEOTHERMAL_CHIP_FORMATTER = recipeInfo -> {
        List<String> result = new ArrayList<>(DefaultSpecialValueFormatter.INSTANCE.format(recipeInfo));
        if (recipeInfo.recipe.mOutputs != null && recipeInfo.recipe.mOutputs.length > 8) {
            result.add(
                EnumChatFormatting.DARK_AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.chip_required"));
        }
        return result;
    };

    public static final RecipeMap<RecipeMapBackend> geothermalSteamBoilerRecipes = RecipeMapBuilder
        .of("gtsr.recipe.geothermal_steam_boiler")
        .maxIO(0, 12, 1, 0)
        .minInputs(0, 1)
        .progressBar(GTUITextures.PROGRESSBAR_ARROW, ProgressBar.Direction.RIGHT)
        .neiSpecialInfoFormatter(GEOTHERMAL_CHIP_FORMATTER)
        .build();

    private static final INEISpecialInfoFormatter STEAM_FLUID_DRILL_FORMATTER = recipeInfo -> {
        List<String> result = new ArrayList<>();
        if (recipeInfo.recipe.mFluidOutputs != null && recipeInfo.recipe.mFluidOutputs.length > 0) {
            FluidStack output = recipeInfo.recipe.mFluidOutputs[0];
            if (output != null && output.getFluid() != null) {
                String fluidName = output.getFluid()
                    .getName();
                if ("saltwater".equals(fluidName) || "ic2distilledwater".equals(fluidName)) {
                    result.add(
                        EnumChatFormatting.GOLD
                            + StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.nei_efficiency_10"));
                } else if ("lava".equals(fluidName)) {
                    result.add(
                        EnumChatFormatting.GOLD
                            + StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.nei_efficiency_05"));
                }
            }
        }
        return result;
    };

    public static final RecipeMap<RecipeMapBackend> steamFluidDrillRecipes = RecipeMapBuilder
        .of("gtsr.recipe.steam_fluid_drill")
        .maxIO(0, 1, 1, 0)
        .minInputs(0, 0)
        .progressBar(GTUITextures.PROGRESSBAR_ARROW, ProgressBar.Direction.RIGHT)
        .neiSpecialInfoFormatter(STEAM_FLUID_DRILL_FORMATTER)
        .build();

    public static final RecipeMap<RecipeMapBackend> gearSteamCompressorRecipes = RecipeMapBuilder
        .of("gtsr.recipe.gear_steam_compressor")
        .maxIO(0, 0, 1, 2)
        .minInputs(0, 0)
        .progressBar(GTUITextures.PROGRESSBAR_ARROW, ProgressBar.Direction.RIGHT)
        .build();
}
