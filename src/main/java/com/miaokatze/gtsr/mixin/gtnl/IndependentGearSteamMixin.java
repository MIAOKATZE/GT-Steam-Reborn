package com.miaokatze.gtsr.mixin.gtnl;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.common.machine.MTEGearSteamCompressor;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.compat.gtnl.IndependentSteamSupport;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.StructureError;

@Mixin(value = MTEGearSteamCompressor.class, remap = false)
public abstract class IndependentGearSteamMixin extends MTEGTSRMultiBlockBase<MTEGearSteamCompressor> {

    @Shadow
    @Final
    private List<MTEHatchPressureSteamInput> mPressureSteamInputs;

    @Shadow
    private boolean hasEnoughSteam(long required) {
        return false;
    }

    @Shadow
    private int getCasingTextureIndex() {
        return 0;
    }

    protected IndependentGearSteamMixin(String name) {
        super(name);
    }

    @Inject(method = "checkMachine", at = @At("HEAD"))
    private void gtsr$clearSources(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors,
        CallbackInfo ci) {
        ((IAdditionalSteamSourceHolder) this).gtsr$getAdditionalSteamSources()
            .clear();
    }

    @Inject(method = "addPressureSteamToMachineList", at = @At("HEAD"), cancellable = true)
    private void gtsr$addSource(IGregTechTileEntity tile, int casing, CallbackInfoReturnable<Boolean> cir) {
        if (PipelessSteamSupport.add(this, tile, casing)) cir.setReturnValue(true);
    }

    @Inject(method = "hasPressureSteamHatch", at = @At("RETURN"), cancellable = true)
    private void gtsr$countSource(CallbackInfoReturnable<Boolean> cir) {
        if (PipelessSteamSupport.count(this) > 0) cir.setReturnValue(true);
    }

    @Inject(method = "hasEnoughSteam", at = @At("HEAD"), cancellable = true)
    private void gtsr$probeSteam(long required, CallbackInfoReturnable<Boolean> cir) {
        if (PipelessSteamSupport.count(this) == 0) return;
        long available = 0;
        for (FluidStack fluid : IndependentSteamSupport.steamKinds(this, getStoredFluids(), mPressureSteamInputs)) {
            available += IndependentSteamSupport.available(this, mInputHatches, mPressureSteamInputs, fluid, required);
            if (available >= required) break;
        }
        cir.setReturnValue(available >= required);
    }

    @Inject(method = "depleteSteam", at = @At("HEAD"), cancellable = true)
    private void gtsr$paySteam(long required, CallbackInfoReturnable<Boolean> cir) {
        if (PipelessSteamSupport.count(this) == 0) return;
        if (required <= 0) {
            cir.setReturnValue(true);
            return;
        }
        if (!hasEnoughSteam(required)) {
            cir.setReturnValue(false);
            return;
        }
        long remaining = required;
        for (FluidStack fluid : IndependentSteamSupport.steamKinds(this, getStoredFluids(), mPressureSteamInputs)) {
            long amount = Math.min(
                remaining,
                IndependentSteamSupport.available(this, mInputHatches, mPressureSteamInputs, fluid, remaining));
            remaining -= IndependentSteamSupport.drain(this, mInputHatches, mPressureSteamInputs, fluid, amount);
            if (remaining <= 0) break;
        }
        cir.setReturnValue(remaining <= 0);
    }

    @Inject(method = "updateAllHatchTextures", at = @At("RETURN"))
    private void gtsr$updateSourceTextures(CallbackInfo ci) {
        for (var hatch : ((IAdditionalSteamSourceHolder) this).gtsr$getAdditionalSteamSources()) {
            hatch.updateTexture(getCasingTextureIndex());
        }
    }
}
