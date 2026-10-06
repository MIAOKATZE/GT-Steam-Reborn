package com.miaokatze.gtsr.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.miaokatze.gtsr.common.dimension.prosperity.air.ProsperityAirIntake;

import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchFluidGenerator;

/** Preserve GT's interval, air-side requirement, fluid identity, tank cap and dirty notification. */
@Mixin(value = MTEHatchFluidGenerator.class, remap = false)
public abstract class MTEHatchFluidGeneratorMixin {

    @ModifyArg(
        method = "addFluidToHatch",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/fluids/FluidStack;<init>(Lnet/minecraftforge/fluids/Fluid;I)V"),
        index = 1,
        remap = false)
    private int gtsr$unstableAirAmount(int amount) {
        return ProsperityAirIntake.generationAmount((MTEHatchFluidGenerator) (Object) this, amount);
    }
}
