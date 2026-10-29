package com.miaokatze.gtsr.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;
import gregtech.common.tileentities.machines.outputme.base.MTEHatchOutputMEBase;

/** Constructor-only extension point used by the two critical ME output subclasses. */
@Mixin(value = { MTEHatchOutputME.class, MTEHatchOutputBusME.class }, remap = false)
public interface CriticalMEOutputProviderAccess {

    @Mutable
    @Accessor(value = "provider", remap = false)
    void gtsr$setCriticalProvider(MTEHatchOutputMEBase<?> provider);
}
