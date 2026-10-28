package com.miaokatze.gtsr.mixin;

import net.minecraft.world.WorldProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.critical.CriticalNightClock;

/** Provider callers must observe the same night as World callers. */
@Mixin(WorldProvider.class)
public abstract class MixinWorldProviderCriticalNight {

    @Inject(method = "isDaytime", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void gtsr$night(CallbackInfoReturnable<Boolean> cir) {
        if (CriticalNightClock.isNight(((WorldProvider) (Object) this).worldObj)) cir.setReturnValue(false);
    }

    @Inject(method = "getSunBrightnessFactor", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void gtsr$noSun(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (CriticalNightClock.isNight(((WorldProvider) (Object) this).worldObj)) cir.setReturnValue(0F);
    }

    @Inject(method = "calculateCelestialAngle", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$midnight(long time, float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (CriticalNightClock.isNight(((WorldProvider) (Object) this).worldObj)) cir.setReturnValue(.5F);
    }

    @Inject(method = "getCurrentMoonPhaseFactor", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void gtsr$moonlight(CallbackInfoReturnable<Float> cir) {
        if (CriticalNightClock.isNight(((WorldProvider) (Object) this).worldObj)) cir.setReturnValue(0F);
    }

    @Inject(method = "getMoonPhase", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$newMoon(long time, CallbackInfoReturnable<Integer> cir) {
        if (CriticalNightClock.isNight(((WorldProvider) (Object) this).worldObj)) cir.setReturnValue(4);
    }

    @Inject(method = "getWorldTime", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void gtsr$time(CallbackInfoReturnable<Long> cir) {
        net.minecraft.world.World world = ((WorldProvider) (Object) this).worldObj;
        if (world != null && world.isRemote
            && com.miaokatze.gtsr.client.critical.CriticalNightClient.isSampling(world)) {
            cir.setReturnValue(com.miaokatze.gtsr.client.critical.CriticalNightClient.sampledTime(world));
            return;
        }
        Long time = CriticalNightClock.time(((WorldProvider) (Object) this).worldObj);
        if (time != null) cir.setReturnValue(time);
    }
}
