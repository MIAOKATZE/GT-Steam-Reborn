package com.miaokatze.gtsr.mixin.gtnl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

@Mixin(
    targets = "com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray$MegaSteamTurbineArrayHatchElement$1",
    remap = false)
public abstract class TurbineHatchCountMixin {

    @Inject(
        method = "count(Lcom/miaokatze/gtsr/common/machine/MTEMegaSteamTurbineArray;)J",
        at = @At("RETURN"),
        cancellable = true)
    private void gtsr$countSteamSources(MTEMegaSteamTurbineArray machine, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(cir.getReturnValue() + PipelessSteamSupport.count(machine));
    }
}
