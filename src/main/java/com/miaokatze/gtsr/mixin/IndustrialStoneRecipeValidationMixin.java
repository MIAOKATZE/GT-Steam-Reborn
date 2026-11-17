// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import java.util.stream.Stream;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.miaokatze.gtsr.loader.recipes.StoneBreakerRule;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IVoidable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;

@Mixin(value = ProcessingLogic.class, remap = false)
public abstract class IndustrialStoneRecipeValidationMixin {

    @Shadow
    protected RecipeMap<?> lastRecipeMap;
    @Shadow
    protected IVoidable machine;
    @Shadow
    protected long availableVoltage;
    @Shadow
    protected ItemStack[] inputItems;

    @Unique
    private World gtsr$stoneMakerWorld() {
        if (machine instanceof IMetaTileEntity mte && mte.getBaseMetaTileEntity() != null)
            return mte.getBaseMetaTileEntity()
                .getWorld();
        return null;
    }

    // Dual-input pattern caches select the first input match. Exclude the other world's
    // glowstone recipe before it can enter that cache.
    @ModifyReturnValue(method = "findRecipeMatches", at = @At("RETURN"), require = 1)
    private Stream<GTRecipe> gtsr$matchSite(Stream<GTRecipe> recipes, @Local(argsOnly = true) RecipeMap<?> map) {
        if (map != RecipeMaps.multiblockRockBreakerRecipes) return recipes;
        World world = gtsr$stoneMakerWorld();
        return recipes.filter(recipe -> {
            StoneBreakerRule rule = recipe.getMetadata(StoneBreakerRule.KEY);
            return rule == null || rule.site.allows(world);
        });
    }

    @ModifyExpressionValue(
        method = "validateAndCalculateRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/logic/ProcessingLogic;validateRecipe(Lgregtech/api/util/GTRecipe;)Lgregtech/api/recipe/check/CheckRecipeResult;"),
        require = 1)
    private CheckRecipeResult gtsr$validateSampledStone(CheckRecipeResult nativeResult,
        @Local(argsOnly = true) GTRecipe recipe) {
        if (lastRecipeMap != RecipeMaps.multiblockRockBreakerRecipes || !nativeResult.wasSuccessful())
            return nativeResult;
        StoneBreakerRule rule = recipe.getMetadata(StoneBreakerRule.KEY);
        if (rule == null) return nativeResult;
        if (availableVoltage < rule.eut) return CheckRecipeResultRegistry.insufficientPower(rule.eut);
        // A locked, zero-cost recipe still requires a physical sample and its selected circuit.
        return rule.site.allows(gtsr$stoneMakerWorld()) && rule.acceptsInputs(inputItems) ? nativeResult
            : CheckRecipeResultRegistry.NO_RECIPE;
    }
}
