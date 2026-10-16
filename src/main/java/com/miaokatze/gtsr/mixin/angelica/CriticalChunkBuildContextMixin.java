package com.miaokatze.gtsr.mixin.angelica;

import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat;

/** Only the draw bucket changes; normals, baked light, material and vertex bytes remain native. */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuildContext", remap = false)
public abstract class CriticalChunkBuildContextMixin {

    @ModifyArg(
        method = "copyRawBuffer",
        at = @At(
            value = "INVOKE",
            target = "Lorg/embeddedt/embeddium/impl/render/chunk/compile/buffers/ChunkModelBuilder;getVertexBuffer(Lorg/embeddedt/embeddium/impl/model/quad/properties/ModelQuadFacing;)Lorg/embeddedt/embeddium/impl/render/chunk/vertex/builder/ChunkMeshBufferBuilder;"),
        index = 0,
        require = 1)
    private ModelQuadFacing gtsr$criticalBucket(ModelQuadFacing facing) {
        return CriticalAngelicaCompat.isCapturing() ? ModelQuadFacing.UNASSIGNED : facing;
    }
}
