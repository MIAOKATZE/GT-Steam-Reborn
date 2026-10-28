package com.miaokatze.gtsr.mixin;

import net.minecraft.world.WorldProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.critical.CriticalNightClock;

@Mixin(WorldProvider.class)
public abstract class WorldProviderCriticalNightClientMixin {

    @Inject(method = "getSunBrightness", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void gtsr$noSun(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (CriticalNightClock.isNight(((WorldProvider) (Object) this).worldObj)) cir.setReturnValue(0F);
    }
}
