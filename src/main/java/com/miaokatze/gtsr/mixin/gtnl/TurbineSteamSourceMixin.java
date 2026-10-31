package com.miaokatze.gtsr.mixin.gtnl;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;
import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;
import com.miaokatze.gtsr.common.machine.base.MTESingularityModeMachineBase;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes.SteamType;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;

/** Append a local tank to the turbine's native classification, payment and cooling path. */
@Mixin(value = MTEMegaSteamTurbineArray.class, remap = false)
public abstract class TurbineSteamSourceMixin extends MTESingularityModeMachineBase<MTEMegaSteamTurbineArray>
    implements IAdditionalSteamSourceHolder {

    @Unique
    private final List<MTEHatch> gtsr$additionalSteamSources = new ArrayList<>();

    protected TurbineSteamSourceMixin(String name) {
        super(name);
    }

    @Shadow
    private int getCasingTextureIndex() {
        throw new AssertionError();
    }

    @Override
    public List<MTEHatch> gtsr$getAdditionalSteamSources() {
        return gtsr$additionalSteamSources;
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        gtsr$additionalSteamSources.clear();
    }

    @Override
    public ArrayList<FluidStack> getStoredFluids() {
        ArrayList<FluidStack> fluids = super.getStoredFluids();
        fluids.addAll(PipelessSteamSupport.getStored(this));
        return fluids;
    }

    @Inject(method = "resetStructureRegistration", at = @At("HEAD"))
    private void gtsr$clearSteamSources(CallbackInfo ci) {
        gtsr$additionalSteamSources.clear();
    }

    @Inject(method = "addPressureSteamToMachineList", at = @At("HEAD"), cancellable = true)
    private void gtsr$addSteamSource(IGregTechTileEntity tile, int casing, CallbackInfoReturnable<Boolean> cir) {
        if (PipelessSteamSupport.add(this, tile, casing)) cir.setReturnValue(true);
    }

    @Inject(method = "hasPressureSteamHatch", at = @At("RETURN"), cancellable = true)
    private void gtsr$hasSteamSource(CallbackInfoReturnable<Boolean> cir) {
        if (PipelessSteamSupport.count(this) > 0) cir.setReturnValue(true);
    }

    @Inject(method = "depleteSteamByType", at = @At("RETURN"), cancellable = true)
    private void gtsr$payRemainingSteam(SteamType type, long amount, CallbackInfoReturnable<Boolean> cir,
        @Local(ordinal = 1) long remaining) {
        if (cir.getReturnValue() || remaining <= 0) return;
        for (FluidStack stored : PipelessSteamSupport.getStored(this)) {
            if (SteamTurbineSteamTypes.classifyFluid(stored) != type) continue;
            // drain accepts long demand and splits it into the hatch's int-sized public drain calls.
            remaining -= PipelessSteamSupport.drain(this, stored, remaining, false);
            if (remaining <= 0) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "updateAllHatchTextures", at = @At("TAIL"))
    private void gtsr$updateSteamTextures(CallbackInfo ci) {
        int texture = getCasingTextureIndex();
        for (MTEHatch hatch : gtsr$additionalSteamSources) hatch.updateTexture(texture);
    }
}
