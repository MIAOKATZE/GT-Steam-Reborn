package com.miaokatze.gtsr.mixin.angelica;

import net.minecraft.client.multiplayer.WorldClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact native manager world, independent of a later Minecraft.theWorld replacement. */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderSectionManager", remap = false)
public interface MachineVisualSectionWorldAccess {

    @Accessor("world")
    WorldClient gtsr$sectionWorld();
}
