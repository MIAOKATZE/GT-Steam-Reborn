package com.miaokatze.gtsr.mixin.angelica;

import java.util.Collection;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility;

/** Only the filtered outputs actually installed by the region manager may retire a static block. */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager", remap = false)
public abstract class MachineVisualUploadMixin {

    @Inject(method = "uploadMeshes", at = @At("RETURN"), require = 1)
    private void gtsr$machineMasksUploaded(CommandList commands, Collection<?> results, Runnable beforeUpload,
        CallbackInfo callback) {
        for (Object result : results) {
            if (!(result instanceof MachineVisualJobResultAccess access)) continue;
            Object output = access.gtsr$output();
            if (output instanceof ChunkBuildOutput built) {
                MachineVisualVisibility.uploadedSection(
                    output,
                    built.render,
                    built.render.getOriginX(),
                    built.render.getOriginY(),
                    built.render.getOriginZ(),
                    built.meshes.isEmpty());
            } else MachineVisualVisibility.uploaded(output);
        }
    }
}
