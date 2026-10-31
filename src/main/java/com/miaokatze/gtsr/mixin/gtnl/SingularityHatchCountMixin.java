package com.miaokatze.gtsr.mixin.gtnl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

@Mixin(
    targets = "com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase$SingularityHatchElement$1",
    remap = false)
public abstract class SingularityHatchCountMixin {

    @Inject(
        method = "count(Lcom/miaokatze/gtsr/common/machine/base/MTESingularityMachineBase;)J",
        at = @At("RETURN"),
        cancellable = true)
    private void gtsr$countSteamSources(MTESingularityMachineBase machine, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(cir.getReturnValue() + PipelessSteamSupport.count(machine));
    }
}
