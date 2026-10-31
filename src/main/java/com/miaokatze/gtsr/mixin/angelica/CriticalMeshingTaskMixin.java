package com.miaokatze.gtsr.mixin.angelica;

import net.minecraft.block.Block;

import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuildContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat;

/** Covers immediate and deferred encoding; the per-block scope is restored even if conversion throws. */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuilderMeshingTask", remap = false)
public abstract class CriticalMeshingTaskMixin {

    @WrapOperation(
        method = "encodeTessellated",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuildContext;copyRawBuffer([III[I[ILorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;ZZ)V"),
        require = 1)
    private static void gtsr$captureCritical(AngelicaChunkBuildContext context, int[] raw, int offset, int count,
        int[] states, int[] shaderIds, ChunkBuildBuffers buffers, Material material, boolean shaderOverride,
        boolean smoothLighting, Operation<Void> original, @Local(argsOnly = true) Block block) {
        Boolean previous = CriticalAngelicaCompat.beginCapture(block);
        try {
            original.call(
                context,
                raw,
                offset,
                count,
                states,
                shaderIds,
                buffers,
                material,
                shaderOverride,
                smoothLighting);
        } finally {
            CriticalAngelicaCompat.endCapture(previous);
        }
    }
}
