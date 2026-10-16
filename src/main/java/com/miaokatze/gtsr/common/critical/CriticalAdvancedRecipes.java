package com.miaokatze.gtsr.common.critical;

import java.util.Arrays;

import net.minecraft.item.ItemStack;

import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;

/** Explicit adapters for multiblocks whose runtime semantics are not encoded in the recipe map. */
public final class CriticalAdvancedRecipes {

    private CriticalAdvancedRecipes() {}

    public static int qftCatalysts(GTRecipe recipe, ItemStack[] items) {
        ItemStack required = recipe.getMetadata(GTRecipeConstants.QFT_CATALYST);
        if (required == null) return 0;
        long count = 0;
        for (ItemStack item : items)
            if (item != null && item.isItemEqual(required) && ItemStack.areItemStackTagsEqual(item, required))
                count = Math.min(Integer.MAX_VALUE, count + Math.max(0, item.stackSize));
        return (int) count;
    }

    public static GTRecipe qftBase(GTRecipe original) {
        ItemStack required = original.getMetadata(GTRecipeConstants.QFT_CATALYST);
        if (required == null) return null;
        GTRecipe recipe = original.copy();
        recipe.mInputs = Arrays.copyOf(recipe.mInputs, recipe.mInputs.length + 1);
        ItemStack catalyst = required.copy();
        catalyst.stackSize = 0;
        recipe.mInputs[recipe.mInputs.length - 1] = catalyst;
        // Native QFT's unfocused mode distributes 100% across all item and fluid outputs.
        // Optional plasma focusing and fluid conversion are separate original-machine controls.
        int outputCount = recipe.mOutputs.length + recipe.mFluidOutputs.length;
        if (outputCount == 0) return null;
        recipe.mOutputChances = new int[recipe.mOutputs.length];
        recipe.mFluidOutputChances = new int[recipe.mFluidOutputs.length];
        Arrays.fill(recipe.mOutputChances, 10000 / outputCount);
        Arrays.fill(recipe.mFluidOutputChances, 10000 / outputCount);
        return recipe;
    }
}
