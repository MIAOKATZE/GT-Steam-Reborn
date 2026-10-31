package com.miaokatze.gtsr.mixin.gtnl;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.api.enums.GTSRHatchElement;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

@Mixin(value = GTSRHatchElement.class, remap = false)
public abstract class SharedSteamElementMixin {

    public boolean matchesHatch(IMetaTileEntity tile) {
        return PipelessSteamSupport.matches(tile, ((gregtech.api.interfaces.IHatchElement<?>) this).mteClasses());
    }

    @Inject(method = "mteClasses", at = @At("RETURN"), cancellable = true)
    private void gtsr$addCandidate(CallbackInfoReturnable<List<? extends Class<? extends IMetaTileEntity>>> cir) {
        GTSRHatchElement element = (GTSRHatchElement) (Object) this;
        if (element != GTSRHatchElement.SteamInputHatch && element != GTSRHatchElement.PressureSteamInputHatch) return;
        List<Class<? extends IMetaTileEntity>> classes = new ArrayList<>(cir.getReturnValue());
        classes.add(PipelessSteamSupport.hatchClass());
        cir.setReturnValue(classes);
    }
}
