package com.miaokatze.gtsr.mixin;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtsr.client.weapons.PortableWeaponPlayerPose;

/** Client-list only. Native angles run first for every player, preventing pose leakage. */
@Mixin(ModelBiped.class)
public abstract class ModelBipedWeaponPoseMixin {

    @Inject(method = "setRotationAngles", at = @At("RETURN"), require = 1)
    private void gtsr$weaponPose(float limb, float amount, float age, float yaw, float pitch, float scale,
        Entity entity, CallbackInfo ci) {
        PortableWeaponPlayerPose.apply((ModelBiped) (Object) this, entity);
    }
}
