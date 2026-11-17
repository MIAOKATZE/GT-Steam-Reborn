// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader.recipes;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.SingleRecipeCheck;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;

/** Attach the outside-Nether rule only to the original paid, circuit-6 netherrack recipe. */
public final class RockBreakerCatalysts {

    private RockBreakerCatalysts() {}

    public static StoneBreakerRule paidRule(ItemStack input, boolean consumed, int program, ItemStack output, int eut) {
        if (!consumed || program != 6
            || input == null
            || input.stackSize != 1
            || output == null
            || output.stackSize != 1
            || !GTUtility.areStacksEqual(output, new ItemStack(Blocks.netherrack))) return null;
        return isGlowstoneDust(input) ? new StoneBreakerRule(StoneSite.OUTSIDE_NETHER, eut, input, program) : null;
    }

    public static boolean isGlowstoneDust(ItemStack input) {
        if (input == null) return false;
        ItemStack dust = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Glowstone, 1);
        return dust != null && GTUtility.areStacksEqual(input, dust);
    }

    public static void registerIndustrialRule() {
        RecipeMaps.multiblockRockBreakerRecipes.appendBuilderTransformer(RockBreakerCatalysts::markPaid);
    }

    public static boolean isNetherrackLock(SingleRecipeCheck lock) {
        return lock != null && lock.getRecipeMap() == RecipeMaps.multiblockRockBreakerRecipes
            && isNetherrackVariant(lock.getRecipe());
    }

    private static boolean isNetherrackVariant(GTRecipe recipe) {
        StoneBreakerRule rule = recipe.getMetadata(StoneBreakerRule.KEY);
        if (rule == null || (rule.site != StoneSite.OUTSIDE_NETHER && rule.site != StoneSite.NETHER)
            || recipe.mInputs.length != 2
            || recipe.mOutputs.length != 1
            || !GTUtility.areStacksEqual(recipe.mOutputs[0], new ItemStack(Blocks.netherrack))) return false;
        for (int i = 0; i < 2; i++) {
            if (isGlowstoneDust(recipe.mInputs[i])
                && GTUtility.areStacksEqual(recipe.mInputs[1 - i], GTUtility.getIntegratedCircuit(6))) return true;
        }
        return false;
    }

    public static SingleRecipeCheck restoreNetherrackLock(SingleRecipeCheck saved, World world) {
        // GT restores the recipe and itemCost separately. Rebuild both from the same local variant,
        // including for pre-v2.1.51 locks whose saved glowstone cost was zero in every dimension.
        for (GTRecipe recipe : saved.getRecipeMap()
            .getAllRecipes()) {
            if (isNetherrackVariant(recipe) && recipe.getMetadata(StoneBreakerRule.KEY).site.allows(world)) {
                return SingleRecipeCheck.builder(saved.getRecipeMap())
                    .setRecipe(recipe)
                    .setBefore(recipe.mInputs, GTValues.emptyFluidStackArray)
                    .setAfter(GTValues.emptyItemStackArray, GTValues.emptyFluidStackArray)
                    .build();
            }
        }
        // If a pack removed the local variant, normal dimension validation rejects the other one.
        return saved;
    }

    private static void markPaid(GTRecipeBuilder builder) {
        ItemStack[] inputs = builder.getItemInputsBasic();
        ItemStack[] outputs = builder.getItemOutputs();
        if (inputs == null || inputs.length != 2
            || outputs == null
            || outputs.length != 1
            || builder.getItemInputsOreDict() != null
            || builder.getMetadata(StoneBreakerRule.KEY) != null
            || (builder.getFluidInputs() != null && builder.getFluidInputs().length != 0)
            || (builder.getFluidOutputs() != null && builder.getFluidOutputs().length != 0)) return;
        for (int i = 0; i < 2; i++) {
            ItemStack circuit = inputs[1 - i];
            if (circuit == null || circuit.stackSize != 0
                || !GTUtility.areStacksEqual(circuit, GTUtility.getIntegratedCircuit(6))) continue;
            StoneBreakerRule rule = paidRule(inputs[i], true, 6, outputs[0], builder.getEUt());
            if (rule != null) builder.metadata(StoneBreakerRule.KEY, rule)
                .ignoreCollision();
        }
    }
}
