package com.miaokatze.gtsr.mixin.gtnl;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.compat.gtnl.IndependentSteamSupport;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;

@Mixin(value = MTEGTSRMultiBlockBase.class, remap = false)
public abstract class IndependentSteamSourceMixin extends MTEEnhancedMultiBlockBase
    implements IAdditionalSteamSourceHolder {

    @Unique
    private final List<MTEHatch> gtsr$additionalSteamSources = new ArrayList<>();

    protected IndependentSteamSourceMixin(String name) {
        super(name);
    }

    @Override
    public List<MTEHatch> gtsr$getAdditionalSteamSources() {
        return gtsr$additionalSteamSources;
    }

    @Override
    public boolean addInputHatchToMachineList(IGregTechTileEntity tile, int casing) {
        return IndependentSteamSupport.accepts(this) && PipelessSteamSupport.add(this, tile, casing)
            || super.addInputHatchToMachineList(tile, casing);
    }

    @Override
    public ArrayList<FluidStack> getStoredFluids() {
        ArrayList<FluidStack> stored = super.getStoredFluids();
        stored.addAll(PipelessSteamSupport.getStored(this));
        return stored;
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        gtsr$additionalSteamSources.clear();
    }
}
