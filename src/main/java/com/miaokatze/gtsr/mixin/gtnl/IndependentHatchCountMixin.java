package com.miaokatze.gtsr.mixin.gtnl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

@Mixin(
    targets = { "com.miaokatze.gtsr.common.machine.MTEGearSteamCompressor$GearSteamCompressorHatchElement$1",
        "com.miaokatze.gtsr.common.machine.MTEKineticProcessingArray$KineticProcessingArrayHatchElement$1",
        "com.miaokatze.gtsr.common.machine.MTESiemensMartinFurnace$SiemensMartinHatchElement$4",
        "com.miaokatze.gtsr.common.machine.cluster.ClusterStructureDef$ControllerInputHatchElement$3" },
    remap = false)
public abstract class IndependentHatchCountMixin {

    @Inject(method = "count(Ljava/lang/Object;)J", at = @At("RETURN"), cancellable = true)
    private void gtsr$countSources(Object controller, CallbackInfoReturnable<Long> cir) {
        if ("PressureSteamInput".equals(((Enum<?>) (Object) this).name())) {
            cir.setReturnValue(cir.getReturnValue() + PipelessSteamSupport.count(controller));
        }
    }
}
