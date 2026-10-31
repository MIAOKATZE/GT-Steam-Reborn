package com.miaokatze.gtsr.mixin.gtnl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Mixin(
    targets = { "com.miaokatze.gtsr.common.api.enums.GTSRHatchElement$3",
        "com.miaokatze.gtsr.common.api.enums.GTSRHatchElement$7" },
    remap = false)
public abstract class SharedSteamElementCountMixin {

    @Inject(
        method = "count(Lgregtech/api/metatileentity/implementations/MTEMultiBlockBase;)J",
        at = @At("RETURN"),
        cancellable = true)
    private void gtsr$countPipeless(MTEMultiBlockBase controller, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(cir.getReturnValueJ() + PipelessSteamSupport.count(controller));
    }
}
