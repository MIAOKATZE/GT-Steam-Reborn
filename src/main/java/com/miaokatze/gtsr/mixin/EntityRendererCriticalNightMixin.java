package com.miaokatze.gtsr.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtsr.client.critical.CriticalNightClient;
import com.miaokatze.gtsr.common.critical.CriticalNightClock;

/** Runs after Angelica's custom lightmap hook as well as the normal lightmap body. */
@Mixin(value = EntityRenderer.class, priority = 900)
public abstract class EntityRendererCriticalNightMixin {

    @Shadow
    private Minecraft mc;
    @Shadow
    @Final
    private int[] lightmapColors;
    @Shadow
    @Final
    private DynamicTexture lightmapTexture;

    @Inject(method = "updateLightmap", at = @At("RETURN"), require = 1)
    private void gtsr$zeroSkyLight(float partialTicks, CallbackInfo ci) {
        if (CriticalNightClock.isNight(mc.theWorld)) {
            CriticalNightClient.darkenSkyLight(lightmapColors);
            lightmapTexture.updateDynamicTexture();
        }
    }
}
