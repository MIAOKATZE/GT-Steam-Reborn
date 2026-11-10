package com.miaokatze.gtsr.mixin.angelica;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.rendering.celeritas.DeferredSectionMesh;
import com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility;

/** The scheduler releases successful, failed, obsolete and orphaned deferred jobs through this one terminal seam. */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.DeferredMeshScheduler", remap = false)
public abstract class MachineVisualDeferredReleaseMixin {

    @Inject(
        method = "release(Lcom/gtnewhorizons/angelica/rendering/celeritas/DeferredSectionMesh;)V",
        at = @At("HEAD"),
        require = 1)
    private void gtsr$releaseMachineMasks(DeferredSectionMesh mesh, CallbackInfo callback) {
        // Before native buffer release: its failure must not retain an unpublished receipt.
        // A successful finalizeSection already moved this task's receipt to its ChunkBuildOutput.
        MachineVisualVisibility.finishBuild(mesh.task, null);
    }
}
