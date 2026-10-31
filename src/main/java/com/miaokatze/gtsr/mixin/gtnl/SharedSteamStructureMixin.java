package com.miaokatze.gtsr.mixin.gtnl;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.common.machine.MTEAirCompressor;
import com.miaokatze.gtsr.common.machine.MTEAtmosphericCentrifuge;
import com.miaokatze.gtsr.common.machine.MTECrustSteamBorer;
import com.miaokatze.gtsr.common.machine.MTELargeSteamFurnace;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.common.machine.MTESteamFluidDrill;
import com.miaokatze.gtsr.common.machine.MTEVeinSteamPyrolyzer;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

@Mixin(
    value = { MTEAirCompressor.class, MTEAtmosphericCentrifuge.class, MTELargeSteamFurnace.class,
        MTEVeinSteamPyrolyzer.class, MTECrustSteamBorer.class, MTESteamFluidDrill.class,
        MTESingularityDrillingHub.class },
    remap = false)
public abstract class SharedSteamStructureMixin {

    @Invoker("getCasingTextureID")
    public abstract int gtsr$getCasingTextureID();

    @Inject(method = "updateAllHatchTextures", at = @At("RETURN"))
    private void gtsr$updateSteamTextures(CallbackInfo ci) {
        for (var hatch : ((IAdditionalSteamSourceHolder) this).gtsr$getAdditionalSteamSources()) {
            hatch.updateTexture(gtsr$getCasingTextureID());
        }
    }

    @WrapOperation(
        method = "checkMachine",
        at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;isEmpty()Z"),
        require = 0)
    private boolean gtsr$hasSteam(ArrayList<?> list, Operation<Boolean> original) {
        boolean empty = original.call(list);
        return list == ((MTESteamMultiBlockBase<?>) (Object) this).mSteamInputFluids
            ? empty && PipelessSteamSupport.count(this) == 0
            : empty;
    }

    @WrapOperation(
        method = "checkMachine",
        at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;size()I"),
        require = 0)
    private int gtsr$countSteam(ArrayList<?> list, Operation<Integer> original) {
        int size = original.call(list);
        return list == ((MTESteamMultiBlockBase<?>) (Object) this).mSteamInputFluids
            ? size + PipelessSteamSupport.count(this)
            : size;
    }
}
