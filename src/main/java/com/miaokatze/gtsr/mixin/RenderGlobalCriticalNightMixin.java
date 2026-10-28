package com.miaokatze.gtsr.mixin;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtsr.common.critical.CriticalNightClock;

/** Skip both vanilla celestial bodies and provider custom sky handlers without replacing either. */
@Mixin(value = RenderGlobal.class, priority = 1100)
public abstract class RenderGlobalCriticalNightMixin {

    @Shadow
    public WorldClient theWorld;

    @Inject(
        method = { "renderSky", "renderClouds", "renderCloudsFancy" },
        at = @At("HEAD"),
        cancellable = true,
        require = 3)
    private void gtsr$moonless(float partialTicks, CallbackInfo ci) {
        if (CriticalNightClock.isNight(theWorld)) ci.cancel();
    }
}
