package com.miaokatze.gtsgu.mixin;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;

@Mixin(value = MTEHatchCustomFluidBase.class, remap = false)
public class MTEHatchCustomFluidBaseMixin {

    @Shadow(remap = false)
    public final Fluid mLockedFluid = null;

    @Inject(method = "isFluidInputAllowed", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtsgu$allowAllSteamPipeInput(FluidStack aFluid, CallbackInfoReturnable<Boolean> cir) {
        if (mLockedFluid != null && gtsgu$isSteamType(mLockedFluid.getName())) {
            if (aFluid != null && aFluid.getFluid() != null
                && gtsgu$isSteamType(
                    aFluid.getFluid()
                        .getName())) {
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private boolean gtsgu$isSteamType(String fluidName) {
        return "steam".equals(fluidName) || "ic2steam".equals(fluidName) || "ic2superheatedsteam".equals(fluidName);
    }
}
