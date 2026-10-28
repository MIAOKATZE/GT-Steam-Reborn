package com.miaokatze.gtsr.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.miaokatze.gtsr.client.critical.CriticalNightClient;
import com.miaokatze.gtsr.common.critical.CriticalNightClock;

/** Wraps the final lightmap method body, including cancellation returns inserted by other mixins. */
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

    @WrapMethod(method = "updateLightmap")
    private void gtsr$zeroSkyLight(float partialTicks, Operation<Void> original) {
        original.call(partialTicks);
        if (CriticalNightClock.isNight(mc.theWorld)) {
            CriticalNightClient.darkenSkyLight(lightmapColors);
            lightmapTexture.updateDynamicTexture();
        }
    }
}
