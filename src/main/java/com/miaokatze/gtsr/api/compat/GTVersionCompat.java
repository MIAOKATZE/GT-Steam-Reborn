// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.api.compat;

import net.minecraft.block.Block;

import bartworks.common.loaders.ItemRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;

/**
 * GTNH 2.9.0–2.9.2 shared public API facade, checked against GT5U .265/.275/.277.
 * These calls use the same modern symbols on all three versions; no runtime probing or fallback is needed.
 * Keep existing public signatures for callers. Environment reporting belongs to bootstrap.
 */
public final class GTVersionCompat {

    private GTVersionCompat() {}

    public static ITexture getCasingTextureOrNull(Object tile) {
        return tile instanceof MTEHatch hatch ? hatch.getCasingTexture() : null;
    }

    public static Block getTieredBorosilicateGlassBlock() {
        Block block = ItemRegistry.bw_realglas;
        if (block == null) throw new IllegalStateException("BartWorks tiered borosilicate glass is unavailable");
        return block;
    }

    public static Block getReinforcedGlassBlock() {
        return GregTechAPI.sBlockGlass1;
    }

    public static int getReinforcedGlassMeta() {
        return 10;
    }

    public static IIconContainer customAlphaCompat(String domain, String path) {
        return Textures.BlockIcons.customAlpha(domain, path);
    }

    /** Legacy GT++ map names now resolve directly to the shared RecipeMaps fields; unknown names fail explicitly. */
    public static RecipeMap<?> gppRecipeMap(String fieldName) {
        return switch (fieldName) {
            case "rocketFuels" -> RecipeMaps.rocketFuels;
            case "quantumForceTransformerRecipes" -> RecipeMaps.quantumForceTransformerRecipes;
            case "electrolyzerNonCellRecipes" -> RecipeMaps.electrolyzerNonCellRecipes;
            case "centrifugeNonCellRecipes" -> RecipeMaps.centrifugeNonCellRecipes;
            case "mixerNonCellRecipes" -> RecipeMaps.mixerNonCellRecipes;
            case "chemicalDehydratorRecipes" -> RecipeMaps.chemicalDehydratorRecipes;
            case "chemicalDehydratorNonCellRecipes" -> RecipeMaps.chemicalDehydratorNonCellRecipes;
            case "multiblockRockBreakerRecipes" -> RecipeMaps.multiblockRockBreakerRecipes;
            case "simpleWasherRecipes" -> RecipeMaps.simpleWasherRecipes;
            default -> throw new IllegalArgumentException("Unknown GregTech recipe map: " + fieldName);
        };
    }

    public static RecipeMap<?> requireGppRecipeMap(String fieldName) {
        return gppRecipeMap(fieldName);
    }
}
