// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin.nei;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtsr.client.nei.StructurePreviewMaterials;
import com.miaokatze.gtsr.common.structure.StructurePreviewWorld;
import com.miaokatze.miaokatzecore.client.preview.PreviewGeometryCache;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongSet;

/** Optional, domain-scoped substitution. Camera, raytrace, overlays and all TESR callbacks stay upstream. */
@Pseudo
@Mixin(targets = "blockrenderer6343.client.renderer.WorldSceneRenderer", remap = false)
public abstract class CriticalPreviewSceneMixin {

    @Shadow
    private RenderBlocks bufferBuilder;
    @Shadow
    private boolean renderAllFaces;
    @Shadow
    private Consumer<Object> onRender;
    @Shadow
    public LongSet renderedBlocks;
    @Unique
    private World gtsr$previewWorld;
    @Unique
    private PreviewGeometryCache gtsr$previewCache;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void gtsr$rememberWorld(@Coerce World world, CallbackInfo callback) {
        gtsr$previewWorld = world;
    }

    @Unique
    private PreviewGeometryCache gtsr$cache() {
        if (!(gtsr$previewWorld instanceof StructurePreviewWorld preview) || !preview.gtsr$isStructurePreview())
            return null;
        if (gtsr$previewCache == null) gtsr$previewCache = new PreviewGeometryCache(
            gtsr$previewWorld, preview::gtsr$previewRevision, StructurePreviewMaterials::isCacheable);
        return gtsr$previewCache;
    }

    @Inject(method = { "resetRenderedBlocks", "setRenderAllFaces" }, at = @At("RETURN"), require = 2)
    private void gtsr$selectionChanged(CallbackInfo callback) {
        if (gtsr$previewCache != null) gtsr$previewCache.invalidate();
    }

    @Inject(method = "renderBlocks", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$appendCachedGeometry(Tessellator tessellator, LongCollection positions, boolean faded,
        CallbackInfo callback) {
        if (faded || positions != renderedBlocks
            || positions.isEmpty()
            || !PreviewGeometryCache.supports(tessellator)) return;
        PreviewGeometryCache cache = gtsr$cache();
        if (cache == null || !cache.prepare(positions)) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        int ambientOcclusion = minecraft.gameSettings.ambientOcclusion;
        minecraft.gameSettings.ambientOcclusion = 0;
        tessellator.startDrawingQuads();
        try {
            tessellator.setBrightness(0x00f000f0);
            cache.append(tessellator, bufferBuilder, renderAllFaces);
            if (onRender != null) onRender.accept(this);
        } finally {
            minecraft.gameSettings.ambientOcclusion = ambientOcclusion;
            tessellator.draw();
            tessellator.setTranslation(0, 0, 0);
        }
        callback.cancel();
    }

    // BlockRenderer 1.4.25 traverses tileMap directly, including native pass and selection checks.
}
