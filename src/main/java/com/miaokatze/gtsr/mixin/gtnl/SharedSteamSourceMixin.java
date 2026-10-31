package com.miaokatze.gtsr.mixin.gtnl;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

@Mixin(value = MTESteamMultiBlockBase.class, remap = false)
public abstract class SharedSteamSourceMixin implements IAdditionalSteamSourceHolder {

    @Unique
    private final List<MTEHatch> gtsr$additionalSteamSources = new ArrayList<>();

    @Override
    public List<MTEHatch> gtsr$getAdditionalSteamSources() {
        return gtsr$additionalSteamSources;
    }

    @Override
    public boolean gtsr$addAdditionalSteamSource(IGregTechTileEntity tile, int casing) {
        return gtsr$isSupportedController() && PipelessSteamSupport.add(this, tile, casing);
    }

    @Unique
    private boolean gtsr$isSupportedController() {
        Object self = this;
        return self instanceof com.miaokatze.gtsr.common.machine.MTEAirCompressor
            || self instanceof com.miaokatze.gtsr.common.machine.MTEAtmosphericCentrifuge
            || self instanceof com.miaokatze.gtsr.common.machine.MTELargeSteamFurnace
            || self instanceof com.miaokatze.gtsr.common.machine.MTEVeinSteamPyrolyzer
            || self instanceof com.miaokatze.gtsr.common.machine.MTECrustSteamBorer
            || self instanceof com.miaokatze.gtsr.common.machine.MTESteamFluidDrill
            || self instanceof com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
    }

    @Inject(method = "clearHatches", at = @At("HEAD"))
    private void gtsr$clearPipeless(CallbackInfo ci) {
        gtsr$additionalSteamSources.clear();
    }
}
