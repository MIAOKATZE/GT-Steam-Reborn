package com.miaokatze.gtsr.mixin;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;

@Mixin(value = MTEHatchCustomFluidBase.class, remap = false)
public abstract class MTEHatchCustomFluidBaseMixin {

    @Inject(method = "isFluidInputAllowed", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtsr$allowAllSteamPipeInput(FluidStack aFluid, CallbackInfoReturnable<Boolean> cir) {
        MTEHatchCustomFluidBase self = (MTEHatchCustomFluidBase) (Object) this;
        Fluid locked = self.mLockedFluid;
        if (locked != null && gtsr$isSteamType(locked.getName())) {
            if (aFluid != null && aFluid.getFluid() != null
                && gtsr$isSteamType(
                    aFluid.getFluid()
                        .getName())) {
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private boolean gtsr$isSteamType(String fluidName) {
        return "steam".equals(fluidName) || "ic2steam".equals(fluidName) || "ic2superheatedsteam".equals(fluidName);
    }
}
