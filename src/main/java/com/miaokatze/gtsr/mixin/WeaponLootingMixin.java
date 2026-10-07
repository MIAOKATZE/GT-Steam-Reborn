package com.miaokatze.gtsr.mixin;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.miaokatze.gtsr.common.weapons.WeaponDamageSource;

@Mixin(EntityLivingBase.class)
public abstract class WeaponLootingMixin {

    @ModifyVariable(method = "onDeath", at = @At(value = "STORE", ordinal = 0), ordinal = 0, require = 1)
    private int gtsr$ownerlessLaunchLooting(int nativeLooting, DamageSource source) {
        return source instanceof WeaponDamageSource ? ((WeaponDamageSource) source).looting : nativeLooting;
    }

    @Redirect(
        method = "onDeath",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/enchantment/EnchantmentHelper;getLootingModifier(Lnet/minecraft/entity/EntityLivingBase;)I"),
        require = 1)
    private int gtsr$launchLooting(EntityLivingBase attacker, DamageSource source) {
        return source instanceof WeaponDamageSource ? ((WeaponDamageSource) source).looting
            : EnchantmentHelper.getLootingModifier(attacker);
    }
}
