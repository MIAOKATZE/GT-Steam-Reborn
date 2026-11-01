package com.miaokatze.gtsr.mixin.angelica;

import net.minecraft.client.multiplayer.WorldClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Avoid linking Angelica's optional fluidlogging interface in the ordinary renderer. */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice", remap = false)
public interface MachineVisualWorldSliceAccess {

    @Accessor("world")
    WorldClient gtsr$world();
}
