package com.miaokatze.gtsr.mixin.gtnl;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gregtech.api.metatileentity.implementations.MTEHatch;

@Mixin(value = MTESteamMineralLogisticsCluster.class, remap = false)
public abstract class IndependentClusterSteamMixin {

    @Shadow
    @Final
    private List<MTEHatchPressureSteamInput> pressureSteamHatches;

    @Shadow
    private int getCasingTextureID() {
        return 0;
    }

    @Inject(method = "getClusterFluidInputHatches", at = @At("RETURN"))
    private void gtsr$includeSources(CallbackInfoReturnable<List<MTEHatch>> cir) {
        cir.getReturnValue()
            .addAll(((IAdditionalSteamSourceHolder) this).gtsr$getAdditionalSteamSources());
    }

    @Redirect(method = "checkMachine", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int gtsr$countSources(List<?> list) {
        return list.size() + (list == pressureSteamHatches ? PipelessSteamSupport.count(this) : 0);
    }

    @Inject(method = "updateAllHatchTextures", at = @At("RETURN"))
    private void gtsr$updateSourceTextures(CallbackInfo ci) {
        for (var hatch : ((IAdditionalSteamSourceHolder) this).gtsr$getAdditionalSteamSources()) {
            hatch.updateTexture(getCasingTextureID());
        }
    }
}
