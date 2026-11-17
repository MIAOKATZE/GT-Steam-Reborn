// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.miaokatze.gtsr.loader.recipes.SampledStoneRecipes;
import com.miaokatze.gtsr.loader.recipes.StoneBreakerRule;

import gregtech.api.enums.GTValues;
import gregtech.common.tileentities.machines.basic.MTERockBreaker;
import gregtech.common.tileentities.machines.basic.MTERockBreaker.RockBreakerRecipe;

@Mixin(value = MTERockBreaker.class, remap = false)
public abstract class MTERockBreakerVoltageMixin {

    @WrapOperation(
        method = "checkRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/basic/MTERockBreaker$RockBreakerRecipe;testInputs(Lnet/minecraft/item/ItemStack;)Z"),
        require = 1)
    private boolean gtsr$canRunSampledRecipe(RockBreakerRecipe recipe, ItemStack input, Operation<Boolean> original) {
        if (!original.call(recipe, input)) return false;
        StoneBreakerRule rule = SampledStoneRecipes.singleRule(recipe);
        if (rule == null) return true;
        MTERockBreaker machine = (MTERockBreaker) (Object) this;
        return input != null && input.stackSize > 0
            && GTValues.V[machine.mTier] >= rule.eut
            && rule.site.allows(
                machine.getBaseMetaTileEntity()
                    .getWorld());
    }

    @ModifyExpressionValue(
        method = "checkRecipe",
        at = @At(value = "FIELD", target = "Lgregtech/api/enums/TierEU;RECIPE_LV:J"),
        require = 1)
    private long gtsr$sampledVoltage(long original, @Local RockBreakerRecipe recipe) {
        StoneBreakerRule rule = SampledStoneRecipes.singleRule(recipe);
        return rule == null ? original : rule.eut;
    }
}
