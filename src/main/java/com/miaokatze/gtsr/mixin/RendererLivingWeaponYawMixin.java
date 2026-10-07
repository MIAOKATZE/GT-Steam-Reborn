package com.miaokatze.gtsr.mixin;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.miaokatze.gtsr.common.weapons.PortableWeapons;
import com.miaokatze.gtsr.common.weapons.WeaponPose;

/** Render-only body basis; vanilla tick/head network state is never written by a render frame. */
@Mixin(RendererLivingEntity.class)
public abstract class RendererLivingWeaponYawMixin {

    @Shadow
    private float interpolateRotation(float previous, float current, float partial) {
        throw new AssertionError("Mixin shadow");
    }

    @Redirect(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;interpolateRotation(FFF)F",
            ordinal = 0),
        require = 1)
    private float gtsr$weaponBodyYaw(RendererLivingEntity renderer, float previous, float current, float partial,
        EntityLivingBase entity, double x, double y, double z, float entityYaw, float renderPartial) {
        if (entity instanceof EntityPlayer && PortableWeapons.kind(((EntityPlayer) entity).getHeldItem()) != null)
            return WeaponPose.yaw((EntityPlayer) entity, partial);
        return interpolateRotation(previous, current, partial);
    }
}
