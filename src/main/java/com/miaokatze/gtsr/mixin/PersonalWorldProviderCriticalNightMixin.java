package com.miaokatze.gtsr.mixin;

import net.minecraft.world.WorldProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.critical.CriticalNightClock;

/** PersonalSpace has its own fixed-day implementations which bypass WorldProvider's bodies. */
@Pseudo
@Mixin(targets = "me.eigenraven.personalspace.world.PersonalWorldProvider", remap = false)
public abstract class PersonalWorldProviderCriticalNightMixin {

    @Inject(method = "isDaytime", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$night(CallbackInfoReturnable<Boolean> cir) {
        if (CriticalNightClock.isNight(((WorldProvider) (Object) this).worldObj)) cir.setReturnValue(false);
    }

    @Inject(
        method = { "getSunBrightness", "getSunBrightnessFactor" },
        at = @At("HEAD"),
        cancellable = true,
        require = 2)
    private void gtsr$noSun(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (CriticalNightClock.isNight(((WorldProvider) (Object) this).worldObj)) cir.setReturnValue(0F);
    }

    @Inject(method = { "calculateCelestialAngle", "func_76563_a" }, at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$midnight(long time, float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (CriticalNightClock.isNight(((WorldProvider) (Object) this).worldObj)) cir.setReturnValue(.5F);
    }
}
