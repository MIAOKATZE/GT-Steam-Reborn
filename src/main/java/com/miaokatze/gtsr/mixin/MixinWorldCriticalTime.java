package com.miaokatze.gtsr.mixin;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.critical.CriticalNightClock;

/** Applies night gameplay only to the World instance holding the critical dimension claim. */
@Mixin(World.class)
public abstract class MixinWorldCriticalTime {

    @Inject(method = "getWorldTime", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$localTime(CallbackInfoReturnable<Long> cir) {
        World world = (World) (Object) this;
        if (world.isRemote && com.miaokatze.gtsr.client.critical.CriticalNightClient.isSampling(world)) {
            cir.setReturnValue(com.miaokatze.gtsr.client.critical.CriticalNightClient.sampledTime(world));
            return;
        }
        Long time = CriticalNightClock.time(world);
        if (time != null) cir.setReturnValue(time);
    }

    @Inject(method = "getCelestialAngle", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$localAngle(float partialTicks, CallbackInfoReturnable<Float> cir) {
        World world = (World) (Object) this;
        if (world.isRemote && com.miaokatze.gtsr.client.critical.CriticalNightClient.isSampling(world)) {
            cir.setReturnValue(
                com.miaokatze.gtsr.client.critical.CriticalNightClient.isSamplingMidnight(world) ? .5F
                    : world.provider.calculateCelestialAngle(
                        com.miaokatze.gtsr.client.critical.CriticalNightClient.sampledTime(world),
                        partialTicks));
            return;
        }
        Long time = CriticalNightClock.time(world);
        if (time != null) cir.setReturnValue(
            CriticalNightClock.isNight(world) ? .5F : world.provider.calculateCelestialAngle(time, partialTicks));
    }

    @Inject(method = "isDaytime", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$night(CallbackInfoReturnable<Boolean> cir) {
        if (CriticalNightClock.isNight((World) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "calculateSkylightSubtracted", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$darkSky(float partialTicks, CallbackInfoReturnable<Integer> cir) {
        if (CriticalNightClock.isNight((World) (Object) this)) cir.setReturnValue(12);
    }

    @Inject(method = "getSunBrightnessFactor", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void gtsr$noSun(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (CriticalNightClock.isNight((World) (Object) this)) cir.setReturnValue(0F);
    }

    @Inject(method = "getCurrentMoonPhaseFactor", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$moonlight(CallbackInfoReturnable<Float> cir) {
        if (CriticalNightClock.isNight((World) (Object) this)) cir.setReturnValue(0F);
    }
}
