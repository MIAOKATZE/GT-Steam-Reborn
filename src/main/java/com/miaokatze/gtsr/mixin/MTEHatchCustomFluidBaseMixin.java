package com.miaokatze.gtsr.mixin;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;

@Mixin(value = MTEHatchCustomFluidBase.class, remap = false)
public abstract class MTEHatchCustomFluidBaseMixin {

    @Shadow(remap = false)
    public Fluid mLockedFluid;

    @Unique
    private boolean gtsr$autoInput = false;

    @Inject(method = "isFluidInputAllowed", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtsr$allowAllSteamPipeInput(FluidStack aFluid, CallbackInfoReturnable<Boolean> cir) {
        if (mLockedFluid != null && gtsr$isSteamType(mLockedFluid.getName())) {
            if (aFluid != null && aFluid.getFluid() != null
                && gtsr$isSteamType(
                    aFluid.getFluid()
                        .getName())) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"), remap = false)
    private void gtsr$saveNBTData(NBTTagCompound aNBT, CallbackInfo ci) {
        aNBT.setBoolean("gtsr$autoInput", gtsr$autoInput);
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"), remap = false)
    private void gtsr$loadNBTData(NBTTagCompound aNBT, CallbackInfo ci) {
        gtsr$autoInput = aNBT.getBoolean("gtsr$autoInput");
    }

    @Unique
    public boolean gtsr$isAutoInput() {
        return gtsr$autoInput;
    }

    @Unique
    public void gtsr$setAutoInput(boolean value) {
        gtsr$autoInput = value;
    }

    @Unique
    private boolean gtsr$isSteamType(String fluidName) {
        return "steam".equals(fluidName) || "ic2steam".equals(fluidName) || "ic2superheatedsteam".equals(fluidName);
    }
}
