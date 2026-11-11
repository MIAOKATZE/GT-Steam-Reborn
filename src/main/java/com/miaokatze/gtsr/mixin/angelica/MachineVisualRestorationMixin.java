// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin.angelica;

import net.minecraft.world.World;

import org.embeddedt.embeddium.impl.render.chunk.ChunkUpdateType;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltRenderSectionData;
import org.embeddedt.embeddium.impl.util.PositionUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility;
import com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility.RestorationRequest;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

/** Mandatory cleanup enters the existing native queue; ordinary world updates keep their original policy. */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager", remap = false)
public abstract class MachineVisualRestorationMixin {

    @Shadow
    @Final
    private Long2ReferenceMap<RenderSection> sectionByPosition;
    @Shadow
    @Final
    protected ReferenceSet<RenderSection> sectionsRequestingUpdate;

    @Shadow
    protected abstract void invalidateCachedSectionData(RenderSection section);

    @Shadow
    public abstract boolean isInShadowPass();

    private RenderSection gtsr$removedSection;

    @Inject(method = "updateChunks", at = @At("HEAD"), require = 1)
    private void gtsr$mandatoryRestorations(boolean updateImmediately, CallbackInfo callback) {
        if (isInShadowPass() || !(this instanceof MachineVisualSectionWorldAccess access)) return;
        CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer == null || renderer.getRenderSectionManager() != (Object) this) return;
        World world = access.gtsr$sectionWorld();
        for (RestorationRequest request : MachineVisualVisibility.pollRestorations(world, this)) {
            RenderSection current = sectionByPosition
                .get(PositionUtil.packSection(request.sectionX, request.sectionY, request.sectionZ));
            if (request.expectedSection() instanceof RenderSection prior && prior.isDisposed()) {
                MachineVisualVisibility.sectionDisposed(world, this, prior);
                continue;
            }
            if (current == null) {
                MachineVisualVisibility.restorationSectionAbsent(request, world, this);
                continue;
            }
            if (!MachineVisualVisibility.bindRestoration(request, world, this, current)) continue;
            invalidateCachedSectionData(current);
            // Deliberately bypass allowImportantRebuilds only for these two owned cleanup sections.
            // Thus alwaysDeferChunkUpdates cannot strand hidden originals; no global option changes.
            current.requestUpdate(ChunkUpdateType.IMPORTANT_REBUILD);
            sectionsRequestingUpdate.add(current);
            MachineVisualVisibility.restorationPromoted(request);
        }
    }

    @Inject(method = "updateSectionInfo", at = @At("RETURN"), require = 1)
    private void gtsr$sectionPresent(RenderSection section, BuiltRenderSectionData data,
        CallbackInfoReturnable<Boolean> callback) {
        if (this instanceof MachineVisualSectionWorldAccess access) MachineVisualVisibility.sectionPresent(
            access.gtsr$sectionWorld(),
            this,
            section,
            section.getOriginX(),
            section.getOriginY(),
            section.getOriginZ());
    }

    @Inject(method = "onSectionRemoved", at = @At("HEAD"), require = 1)
    private void gtsr$beforeSectionRemoved(int x, int y, int z, CallbackInfo callback) {
        gtsr$removedSection = sectionByPosition.get(PositionUtil.packSection(x, y, z));
    }

    @Inject(method = "onSectionRemoved", at = @At("RETURN"), require = 1)
    private void gtsr$afterSectionRemoved(int x, int y, int z, CallbackInfo callback) {
        RenderSection removed = gtsr$removedSection;
        gtsr$removedSection = null;
        if (removed != null && removed.isDisposed() && this instanceof MachineVisualSectionWorldAccess access)
            MachineVisualVisibility.sectionDisposed(access.gtsr$sectionWorld(), this, removed);
    }

    @Inject(method = "destroy", at = @At("RETURN"), require = 1)
    private void gtsr$managerDisposed(CallbackInfo callback) {
        if (this instanceof MachineVisualSectionWorldAccess access)
            MachineVisualVisibility.managerDisposed(access.gtsr$sectionWorld(), this);
    }
}
