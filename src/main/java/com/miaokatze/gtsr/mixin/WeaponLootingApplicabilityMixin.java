package com.miaokatze.gtsr.mixin;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.weapons.PortableWeapons;

@Mixin(Enchantment.class)
public abstract class WeaponLootingApplicabilityMixin {

    @Inject(method = "canApply", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$portableLooting(ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if ((Object) this == Enchantment.looting && PortableWeapons.kind(stack) != null) result.setReturnValue(true);
    }
}
