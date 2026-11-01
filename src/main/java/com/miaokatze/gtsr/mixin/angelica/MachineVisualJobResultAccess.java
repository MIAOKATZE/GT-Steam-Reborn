package com.miaokatze.gtsr.mixin.angelica;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the erased output field without importing the optional Java-record backport superclass. */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult$Success", remap = false)
public interface MachineVisualJobResultAccess {

    @Accessor("output")
    Object gtsr$output();
}
