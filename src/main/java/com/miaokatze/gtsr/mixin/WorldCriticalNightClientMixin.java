package com.miaokatze.gtsr.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.client.critical.CriticalNightClient;
import com.miaokatze.gtsr.common.critical.CriticalNightClock;

/** These World methods are stripped on dedicated servers. Water/lava fog stays in EntityRenderer. */
@Mixin(World.class)
public abstract class WorldCriticalNightClientMixin {

    @Inject(method = "getSunBrightness", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$noSun(float partialTicks, CallbackInfoReturnable<Float> cir) {
        World world = (World) (Object) this;
        if (CriticalNightClient.isSamplingMidnight(world)) cir.setReturnValue(world.getSunBrightnessBody(partialTicks));
        else if (CriticalNightClock.isNight(world)) cir.setReturnValue(0F);
    }

    @Inject(method = "getMoonPhase", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$newMoon(CallbackInfoReturnable<Integer> cir) {
        if (CriticalNightClock.isNight((World) (Object) this)) cir.setReturnValue(4);
    }

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 1)
    private void gtsr$blackSky(Entity entity, float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        World world = (World) (Object) this;
        float strength = CriticalNightClient.strength(world);
        if (strength > 0) cir.setReturnValue(CriticalNightClient.skyColor(world, entity, partialTicks, strength));
    }

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true, require = 1)
    private void gtsr$blackFog(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        World world = (World) (Object) this;
        float strength = CriticalNightClient.strength(world);
        if (strength > 0) cir.setReturnValue(CriticalNightClient.fogColor(world, partialTicks, strength));
    }
}
