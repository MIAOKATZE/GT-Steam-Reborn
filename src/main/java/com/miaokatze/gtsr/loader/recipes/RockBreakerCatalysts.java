// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader.recipes;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
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
