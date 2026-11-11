// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.llamalad7.mixinextras.sugar.Local;
import com.miaokatze.gtsr.loader.recipes.RockBreakerCatalysts;

import gregtech.common.tileentities.machines.basic.MTERockBreaker;

@Mixin(value = MTERockBreaker.RockBreakerRecipe.Builder.class, remap = false)
public abstract class MTERockBreakerRecipeBuilderMixin {

    @ModifyVariable(method = "inputItem", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private boolean gtsr$keepGlowstone(boolean consumed, @Local(argsOnly = true) ItemStack input) {
        return consumed && !RockBreakerCatalysts.isGlowstoneDust(input);
    }
}
