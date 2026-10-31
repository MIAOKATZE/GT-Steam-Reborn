package com.miaokatze.gtsr.mixin.gtnl;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.common.machine.base.MTESingularityModeMachineBase;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;

/** Keep the real hatch in the native singularity input table, including its position and limits. */
@Mixin(value = MTESingularityMachineBase.class, remap = false)
public abstract class SingularitySteamSourceMixin extends MTESingularityModeMachineBase<MTESingularityMachineBase>
    implements IAdditionalSteamSourceHolder {

    @Unique
    private final List<MTEHatch> gtsr$additionalSteamSources = new ArrayList<>();

    protected SingularitySteamSourceMixin(String name) {
        super(name);
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

    @Inject(method = "addSteamInputToMachineList", at = @At("HEAD"), cancellable = true)
    private void gtsr$addSteamSource(IGregTechTileEntity tile, int casing, CallbackInfoReturnable<Boolean> cir) {
        if (PipelessSteamSupport.add(this, tile, casing)) cir.setReturnValue(true);
    }

    @Inject(method = "getSteamInputHatches", at = @At("RETURN"))
    private void gtsr$includeSteamSources(CallbackInfoReturnable<List<MTEHatch>> cir) {
        // This is the same collection consumed by probes, grade conversion and EntanglementInputs.
        cir.getReturnValue()
            .addAll(gtsr$additionalSteamSources);
    }

    @Inject(method = "updateAllHatchTextures", at = @At("TAIL"))
    private void gtsr$updateSteamTextures(CallbackInfo ci) {
        int texture = ((MTESingularityMachineBase) (Object) this).getGTSRHatchCasingTextureIndex();
        for (MTEHatch hatch : gtsr$additionalSteamSources) hatch.updateTexture(texture);
    }
}
