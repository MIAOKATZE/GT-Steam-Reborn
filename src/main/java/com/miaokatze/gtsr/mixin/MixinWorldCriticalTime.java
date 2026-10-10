package com.miaokatze.gtsr.mixin;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.critical.CriticalNightClock;

/** Overrides only dimensions which have actually initialized a critical local clock. */
@Mixin(World.class)
public abstract class MixinWorldCriticalTime {

    @Inject(method = "getWorldTime", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$localTime(CallbackInfoReturnable<Long> cir) {
        World world = (World) (Object) this;
        Long time = CriticalNightClock.time(world);
        if (time != null) cir.setReturnValue(time);
    }

    @Inject(method = "getCelestialAngle", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$localAngle(float partialTicks, CallbackInfoReturnable<Float> cir) {
        World world = (World) (Object) this;
        Long time = CriticalNightClock.time(world);
        if (time != null) cir.setReturnValue(
            CriticalNightClock.isNight(world) ? .5F : world.provider.calculateCelestialAngle(time, partialTicks));
    }

    @Inject(method = "isDaytime", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$night(CallbackInfoReturnable<Boolean> cir) {
        if (CriticalNightClock.isNight((World) (Object) this)) cir.setReturnValue(false);
    }
}
