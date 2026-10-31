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
    targets = "com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray$MegaSteamTurbineArrayHatchElement",
    remap = false)
public abstract class TurbineHatchElementMixin {

    public boolean matchesHatch(IMetaTileEntity tile) {
        return PipelessSteamSupport.matches(tile, ((gregtech.api.interfaces.IHatchElement<?>) this).mteClasses());
    }

    @Inject(method = "mteClasses", at = @At("RETURN"), cancellable = true)
    private void gtsr$steamCandidates(CallbackInfoReturnable<List<? extends Class<? extends IMetaTileEntity>>> cir) {
        if (!((Enum<?>) (Object) this).name()
            .equals("PressureSteamInput")) return;
        List<Class<? extends IMetaTileEntity>> classes = new ArrayList<>(cir.getReturnValue());
        classes.add(PipelessSteamSupport.hatchClass());
        cir.setReturnValue(classes);
    }
}
