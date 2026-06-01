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
        .maxIO(1, 1, 0, 0)
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
        .maxIO(0, 0, 1, 8)
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
}
