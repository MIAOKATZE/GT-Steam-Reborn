package com.miaokatze.gtsr.mixin.gtnl;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.StructureError;

@Mixin(value = MTECrustMatterAggregator.class, remap = false)
public abstract class SingularityAggregatorStructureMixin {

    @Inject(method = "checkMachine", at = @At("HEAD"))
    private void gtsr$clearSteamSources(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors,
        CallbackInfo ci) {
        ((IAdditionalSteamSourceHolder) this).gtsr$getAdditionalSteamSources()
            .clear();
    }
}
