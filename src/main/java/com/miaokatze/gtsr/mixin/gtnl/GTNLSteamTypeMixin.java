package com.miaokatze.gtsr.mixin.gtnl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.miaokatze.gtsr.compat.gtnl.GTNLSteamType;

import gregtech.api.util.GTUtility;

@Pseudo
@Mixin(targets = "com.science.gtnl.utils.enums.SteamTypes", remap = false)
public interface GTNLSteamTypeMixin extends GTNLSteamType {

    @Accessor("fluid")
    GTUtility.FluidId gtsr$getFluid();

    @Accessor("efficiencyFactor")
    int gtsr$getEfficiencyFactor();
}
