package com.miaokatze.gtsr.mixin.gtnl;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.common.machine.MTEKineticProcessingArray;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.compat.gtnl.IndependentSteamSupport;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.StructureError;

@Mixin(value = MTEKineticProcessingArray.class, remap = false)
public abstract class IndependentKineticSteamMixin extends MTEGTSRMultiBlockBase<MTEKineticProcessingArray> {

    @Shadow
    @Final
    private List<MTEHatchPressureSteamInput> mPressureSteamInputs;

    @Shadow
    private int getCasingTextureIndex() {
        return 0;
    }

    protected IndependentKineticSteamMixin(String name) {
        super(name);
    }

    @Inject(method = "checkMachine", at = @At("HEAD"))
    private void gtsr$clearSources(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors,
        CallbackInfo ci) {
        ((IAdditionalSteamSourceHolder) this).gtsr$getAdditionalSteamSources()
            .clear();
    }

    @Inject(method = "addPressureSteamToMachineList", at = @At("HEAD"), cancellable = true)
    private void gtsr$addSource(IGregTechTileEntity tile, int casing, CallbackInfoReturnable<Boolean> cir) {
        if (PipelessSteamSupport.add(this, tile, casing)) cir.setReturnValue(true);
    }

    @Redirect(method = "checkMachine", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
    private boolean gtsr$countSource(List<?> list) {
        return list.isEmpty() && (list != mPressureSteamInputs || PipelessSteamSupport.count(this) == 0);
    }

    @Inject(method = "hasSufficientSuperheatedSteam", at = @At("HEAD"), cancellable = true)
    private void gtsr$probeSteam(long required, CallbackInfoReturnable<Boolean> cir) {
        if (PipelessSteamSupport.count(this) == 0) return;
        FluidStack steam = FluidRegistry.getFluidStack("ic2superheatedsteam", 1);
        cir.setReturnValue(
            required <= 0
                || IndependentSteamSupport.available(this, mInputHatches, mPressureSteamInputs, steam, required)
                    >= required);
    }

    @Inject(method = "depleteSuperheatedSteam", at = @At("HEAD"), cancellable = true)
    private void gtsr$paySteam(long required, CallbackInfoReturnable<Boolean> cir) {
        if (PipelessSteamSupport.count(this) == 0) return;
        if (required <= 0) {
            cir.setReturnValue(true);
            return;
        }
        FluidStack steam = FluidRegistry.getFluidStack("ic2superheatedsteam", 1);
        if (IndependentSteamSupport.available(this, mInputHatches, mPressureSteamInputs, steam, required) < required) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(
            IndependentSteamSupport.drain(this, mInputHatches, mPressureSteamInputs, steam, required) == required);
    }

    @Inject(method = "updateAllHatchTextures", at = @At("RETURN"))
    private void gtsr$updateSourceTextures(CallbackInfo ci) {
        for (var hatch : ((IAdditionalSteamSourceHolder) this).gtsr$getAdditionalSteamSources()) {
            hatch.updateTexture(getCasingTextureIndex());
        }
    }
}
