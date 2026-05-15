package com.miaokatze.gtsr.api.recipe;

import com.gtnewhorizons.modularui.common.widget.ProgressBar;

import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;

public class GTSRRecipeMaps {

    public static final RecipeMap<RecipeMapBackend> largeCokeOvenRecipes = RecipeMapBuilder
        .of("gtsr.recipe.largecokeoven")
        .maxIO(1, 1, 0, 1)
        .minInputs(1, 0)
        .progressBar(GTUITextures.PROGRESSBAR_SIFT, ProgressBar.Direction.DOWN)
        .build();

    public static final RecipeMap<RecipeMapBackend> siemensMartinRecipes = RecipeMapBuilder
        .of("gtsr.recipe.siemensmartin")
        .maxIO(2, 1, 0, 0)
        .minInputs(1, 0)
        .progressBar(GTUITextures.PROGRESSBAR_ARROW, ProgressBar.Direction.RIGHT)
        .build();
}
