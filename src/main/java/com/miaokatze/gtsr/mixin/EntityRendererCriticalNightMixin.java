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
        float strength = CriticalNightClient.strength(mc.theWorld);
        int[] natural = null;
        if (strength < 1F) {
            CriticalNightClient.beginSample(mc.theWorld, false);
            try {
                original.call(partialTicks);
            } finally {
                CriticalNightClient.endSample();
            }
            if (strength == 0F) return;
            natural = lightmapColors.clone();
        }
        CriticalNightClient.beginSample(mc.theWorld, true);
        try {
            original.call(partialTicks);
        } finally {
            CriticalNightClient.endSample();
        }
        CriticalNightClient.blendNightLight(lightmapColors, natural, strength);
        lightmapTexture.updateDynamicTexture();
    }
}
