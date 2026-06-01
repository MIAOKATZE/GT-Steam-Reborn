package com.miaokatze.gtsr.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.basic.MTERockBreaker;

@Mixin(value = MTERockBreaker.RockBreakerRecipe.Builder.class, remap = false)
public class MTERockBreakerRecipeBuilderMixin {

    @Shadow(remap = false)
    private ItemStack inputItem;

    @Shadow(remap = false)
    private boolean inputConsumed;

    @Overwrite(remap = false)
    public MTERockBreaker.RockBreakerRecipe.Builder inputItem(ItemStack aInputItem, boolean consumed) {
        if (consumed && aInputItem != null) {
            ItemStack glowstoneDust = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Glowstone, 1);
            if (glowstoneDust != null && GTUtility.areStacksEqual(aInputItem, glowstoneDust)) {
                consumed = false;
            }
        }
        this.inputItem = aInputItem;
        this.inputConsumed = consumed;
        return (MTERockBreaker.RockBreakerRecipe.Builder) (Object) this;
    }
}
