package com.miaokatze.gtsr.mixin.gtnl;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

@Mixin(
    targets = { "com.miaokatze.gtsr.common.machine.MTEGearSteamCompressor$GearSteamCompressorHatchElement",
        "com.miaokatze.gtsr.common.machine.MTEKineticProcessingArray$KineticProcessingArrayHatchElement",
        "com.miaokatze.gtsr.common.machine.MTESiemensMartinFurnace$SiemensMartinHatchElement",
        "com.miaokatze.gtsr.common.machine.cluster.ClusterStructureDef$ControllerInputHatchElement" },
    remap = false)
public abstract class IndependentHatchCandidatesMixin {

    public boolean matchesHatch(IMetaTileEntity tile) {
        return PipelessSteamSupport.matches(tile, ((gregtech.api.interfaces.IHatchElement<?>) this).mteClasses());
    }

    @Inject(method = "mteClasses", at = @At("RETURN"), cancellable = true)
    private void gtsr$appendCandidate(CallbackInfoReturnable<List<Class<? extends IMetaTileEntity>>> cir) {
        if (!"PressureSteamInput".equals(((Enum<?>) (Object) this).name())) return;
        List<Class<? extends IMetaTileEntity>> classes = new ArrayList<>(cir.getReturnValue());
        classes.add(PipelessSteamSupport.hatchClass());
        cir.setReturnValue(classes);
    }
}
