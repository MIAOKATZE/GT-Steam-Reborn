// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader.recipes;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

public final class RockBreakerCatalysts {

    private RockBreakerCatalysts() {}

    public static boolean isGlowstoneDust(ItemStack input) {
        if (input == null) return false;
        ItemStack dust = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Glowstone, 1);
        return dust != null && GTUtility.areStacksEqual(input, dust);
    }

    public static void registerIndustrialRule() {
        // GT++ supplies its industrial recipes in postInit. Transform only this map, before indexing and NEI.
        RecipeMaps.multiblockRockBreakerRecipes.appendRecipeTransformer(RockBreakerCatalysts::keepIndustrialDust);
    }

    private static void keepIndustrialDust(GTRecipe recipe) {
        ItemStack[] inputs = recipe.mInputs;
        // The GT++ netherrack recipe uses plain item inputs. Leave packs' alternate ingredients,
        // extra products, fluids and other circuit recipes alone (including its Thaumic Bases circuit 6).
        if (recipe instanceof GTRecipe.GTRecipe_WithAlt || inputs.length != 2
            || recipe.mOutputs.length != 1
            || recipe.mFluidInputs.length != 0
            || recipe.mFluidOutputs.length != 0
            || !GTUtility.areStacksEqual(recipe.mOutputs[0], new ItemStack(Blocks.netherrack))
            || recipe.mOutputs[0].stackSize != 1) return;
        for (int i = 0; i < inputs.length; i++) {
            ItemStack dust = inputs[i];
            if (dust == null || dust.stackSize != 1 || !isGlowstoneDust(dust)) continue;
            ItemStack circuit = inputs[1 - i];
            if (circuit == null || circuit.stackSize != 0
                || !GTUtility.areStacksEqual(circuit, GTUtility.getIntegratedCircuit(6))) return;
            ItemStack[] changed = inputs.clone();
            changed[i] = GTUtility.copyAmount(0, dust);
            recipe.setInputs(changed);
            return;
        }
    }
}
