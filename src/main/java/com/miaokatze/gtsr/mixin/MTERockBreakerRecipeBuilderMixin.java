// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.miaokatze.gtsr.loader.recipes.RockBreakerCatalysts;
import com.miaokatze.gtsr.loader.recipes.SampledStoneRecipes;
import com.miaokatze.gtsr.loader.recipes.StoneBreakerRule;

import gregtech.api.enums.TierEU;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.common.tileentities.machines.basic.MTERockBreaker.RockBreakerRecipe;

@Mixin(value = RockBreakerRecipe.Builder.class, remap = false)
public abstract class MTERockBreakerRecipeBuilderMixin {

    @Shadow
    private ItemStack inputItem;
    @Shadow
    private boolean inputConsumed;
    @Shadow
    private int circuit;
    @Shadow
    private ItemStack outputItem;

    @Unique
    private StoneBreakerRule gtsr$stoneRule() {
        if ((Object) this instanceof SampledStoneRecipes.SampledBuilder builder) return builder.rule;
        return RockBreakerCatalysts.paidRule(inputItem, inputConsumed, circuit, outputItem, (int) TierEU.RECIPE_LV);
    }

    @ModifyExpressionValue(
        method = "addFakeRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/util/GTRecipeBuilder;eut(J)Lgregtech/api/util/GTRecipeBuilder;"),
        require = 1)
    private GTRecipeBuilder gtsr$displaySiteAndVoltage(GTRecipeBuilder display) {
        StoneBreakerRule rule = gtsr$stoneRule();
        return rule == null ? display
            : display.eut(rule.eut)
                .metadata(StoneBreakerRule.KEY, rule);
    }

    @Inject(method = "build", at = @At("RETURN"), require = 1)
    private void gtsr$bindSiteAndVoltage(CallbackInfoReturnable<RockBreakerRecipe> result) {
        StoneBreakerRule rule = gtsr$stoneRule();
        if (rule != null) SampledStoneRecipes.bindSingle(result.getReturnValue(), rule);
    }
}
