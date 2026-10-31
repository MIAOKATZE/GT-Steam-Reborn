package com.miaokatze.gtsr.mixin.gtnl;

import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.common.machine.MTEAmmoniaPlant;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.compat.gtnl.IndependentAmmoniaInputElement;
import com.miaokatze.gtsr.compat.gtnl.IndependentSteamSupport;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gregtech.api.enums.HatchElement;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.structure.error.StructureError;

@Mixin(value = MTEAmmoniaPlant.class, remap = false)
public abstract class IndependentAmmoniaSteamMixin extends MTEGTSRMultiBlockBase<MTEAmmoniaPlant> {

    @Shadow
    @Final
    private static int CASING_TEXTURE_ID;

    protected IndependentAmmoniaSteamMixin(String name) {
        super(name);
    }

    @ModifyArg(
        method = "getStructureDefinition",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/util/HatchElementBuilder;atLeast([Lgregtech/api/interfaces/IHatchElement;)Lgregtech/api/util/HatchElementBuilder;"),
        index = 0)
    private IHatchElement<?>[] gtsr$inputCandidates(IHatchElement<?>[] elements) {
        IHatchElement<?>[] candidates = Arrays.copyOf(elements, elements.length);
        for (int i = 0; i < candidates.length; i++) {
            if (candidates[i] == HatchElement.InputHatch) candidates[i] = IndependentAmmoniaInputElement.INSTANCE;
        }
        return candidates;
    }

    @Inject(method = "checkMachine", at = @At("HEAD"))
    private void gtsr$clearSources(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors,
        CallbackInfo ci) {
        ((IAdditionalSteamSourceHolder) this).gtsr$getAdditionalSteamSources()
            .clear();
    }

    @Redirect(method = "checkMachine", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;isEmpty()Z"))
    private boolean gtsr$countSource(java.util.ArrayList<?> list) {
        return list.isEmpty() && (list != mInputHatches || PipelessSteamSupport.count(this) == 0);
    }

    @Redirect(
        method = { "consumeFuelAndSteam", "hasMaintainInputs" },
        at = @At(
            value = "INVOKE",
            target = "Lcom/miaokatze/gtsr/api/compat/GTSRHatchFluidAccess;hasEnoughAcross(Ljava/util/List;Lnet/minecraftforge/fluids/FluidStack;)Z"))
    private boolean gtsr$probeSteam(List<? extends MTEHatch> sources, FluidStack fluid) {
        return GTSRHatchFluidAccess.hasEnoughAcross(IndependentSteamSupport.withAdditional(this, sources), fluid);
    }

    @Redirect(
        method = "consumeFuelAndSteam",
        at = @At(
            value = "INVOKE",
            target = "Lcom/miaokatze/gtsr/api/compat/GTSRHatchFluidAccess;depleteFluidAcross(Ljava/util/List;Lnet/minecraftforge/fluids/FluidStack;)I"))
    private int gtsr$paySteam(List<? extends MTEHatch> sources, FluidStack fluid) {
        return GTSRHatchFluidAccess.depleteFluidAcross(IndependentSteamSupport.withAdditional(this, sources), fluid);
    }

    @Inject(method = "updateAllHatchTextures", at = @At("RETURN"))
    private void gtsr$updateSourceTextures(CallbackInfo ci) {
        for (var hatch : ((IAdditionalSteamSourceHolder) this).gtsr$getAdditionalSteamSources()) {
            hatch.updateTexture(CASING_TEXTURE_ID);
        }
    }
}
