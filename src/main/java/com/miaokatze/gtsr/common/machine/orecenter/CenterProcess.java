package com.miaokatze.gtsr.common.machine.orecenter;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;

public enum CenterProcess {

    CRUSHER(CenterModule.CRUSHER, "gt.recipe.macerator", false, true),
    HAMMER(CenterModule.CRUSHER, "gt.recipe.forgehammer", false, true),
    WASHER(CenterModule.WASHER, "gt.recipe.orewasher", false, true),
    SIMPLE_WASHER(CenterModule.WASHER, "gt.recipe.simplewasher", false, true),
    CHEMICAL_BATH(CenterModule.WASHER, "gt.recipe.chemicalbath", false, true),
    SIFTER(CenterModule.SIFTER, "gt.recipe.sifter", false, true),
    CENTRIFUGE(CenterModule.CENTRIFUGE, "gt.recipe.multicentrifuge", false, true),
    SEPARATION(CenterModule.CENTRIFUGE, "gt.recipe.multicentrifuge", true, false),
    SMELTING(CenterModule.SMELTING, "gt.recipe.furnace", true, false),
    THERMAL_CENTRIFUGE(CenterModule.THERMAL_CENTRIFUGE, "gt.recipe.thermalcentrifuge", false, true),
    SEPARATOR(CenterModule.SEPARATOR, "gt.recipe.electromagneticseparator", false, true),
    ELECTROLYZER(CenterModule.ELECTROLYZER, "gt.recipe.multielectro", true, false),
    EXTRACTOR(CenterModule.EXTRACTOR, "gt.recipe.extractor", false, false),
    FLUID_EXTRACTOR(CenterModule.EXTRACTOR, "gt.recipe.fluidextractor", true, false),
    REACTOR(CenterModule.REACTOR, "gt.recipe.largechemicalreactor", true, false),
    FURNACE(CenterModule.FURNACE, "gt.recipe.blastfurnace", true, false),
    DRYER(CenterModule.DRYER, "gt.recipe.multidehydrator", false, false),
    RECYCLER(CenterModule.FURNACE, "ic.recipe.recycler", true, false);

    public final CenterModule module;
    final String recipeMap;
    final boolean direct;
    final boolean mineralYield;

    CenterProcess(CenterModule module, String recipeMap, boolean direct, boolean mineralYield) {
        this.module = module;
        this.recipeMap = recipeMap;
        this.direct = direct;
        this.mineralYield = mineralYield;
    }

    RecipeMap<?> recipes() {
        if (this == HAMMER) return RecipeMaps.hammerRecipes;
        return RecipeMap.ALL_RECIPE_MAPS.get(recipeMap);
    }

    public boolean installed(int[] grades) {
        return grades[module.ordinal()] > 0 && (this != RECYCLER || grades[CenterModule.REACTOR.ordinal()] > 0);
    }

    public int auxiliarySlots() {
        return this == REACTOR ? 3 : this == FURNACE ? 2 : 0;
    }

    public boolean hasCircuitSlot() {
        return this == REACTOR || this == FURNACE || this == ELECTROLYZER;
    }
}
