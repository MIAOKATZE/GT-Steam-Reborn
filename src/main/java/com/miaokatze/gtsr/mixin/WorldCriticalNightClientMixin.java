package com.miaokatze.gtsr.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.critical.CriticalNightClock;

/** These World methods are stripped on dedicated servers. Water/lava fog stays in EntityRenderer. */
@Mixin(World.class)
public abstract class WorldCriticalNightClientMixin {

    // Keep getSavedLightValue untouched: lighting propagation needs the original stored sky values.
    @Inject(method = "getSkyBlockTypeBrightness", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$skyLight(EnumSkyBlock type, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        if (type == EnumSkyBlock.Sky && CriticalNightClock.isNight((World) (Object) this)) cir.setReturnValue(0);
    }

    @Inject(method = "getSunBrightness", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$noSun(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (CriticalNightClock.isNight((World) (Object) this)) cir.setReturnValue(0F);
    }

    @Inject(method = "getMoonPhase", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$newMoon(CallbackInfoReturnable<Integer> cir) {
        if (CriticalNightClock.isNight((World) (Object) this)) cir.setReturnValue(4);
    }

    @Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$blackSky(Entity entity, float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (CriticalNightClock.isNight((World) (Object) this)) cir.setReturnValue(Vec3.createVectorHelper(0, 0, 0));
    }

    @Inject(method = "getFogColor", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$blackFog(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (CriticalNightClock.isNight((World) (Object) this)) cir.setReturnValue(Vec3.createVectorHelper(0, 0, 0));
    }
}
