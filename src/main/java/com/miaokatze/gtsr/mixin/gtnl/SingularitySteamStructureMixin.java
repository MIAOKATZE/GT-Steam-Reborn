package com.miaokatze.gtsr.mixin.gtnl;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.common.machine.MTECriticalSingularityCompressor;
import com.miaokatze.gtsr.common.machine.MTEDenseStateManipulator;
import com.miaokatze.gtsr.common.machine.MTESteamSingularityEntangler;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.StructureError;

@Mixin(
    value = { MTESteamSingularityEntangler.class, MTECriticalSingularityCompressor.class,
        MTEDenseStateManipulator.class },
    remap = false)
public abstract class SingularitySteamStructureMixin {

    @Inject(method = "checkMachine", at = @At("HEAD"))
    private void gtsr$clearSteamSources(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors,
        CallbackInfo ci) {
        ((IAdditionalSteamSourceHolder) this).gtsr$getAdditionalSteamSources()
            .clear();
    }

    @WrapOperation(
        method = "checkMachine",
        at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 0))
    private boolean gtsr$hasSteamSource(List<?> pressureInputs, Operation<Boolean> original) {
        // Only the pressure-source test changes; output and fuel-bus requirements stay native.
        return original.call(pressureInputs) && PipelessSteamSupport.count(this) == 0;
    }
}
