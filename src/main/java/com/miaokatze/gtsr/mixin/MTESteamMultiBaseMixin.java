package com.miaokatze.gtsr.mixin;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

@Mixin(value = MTESteamMultiBase.class, remap = false)
public abstract class MTESteamMultiBaseMixin {

    @Shadow
    @Final
    public ArrayList<MTEHatchCustomFluidBase> mSteamInputFluids;

    @Unique
    private final ArrayList<MTESteamCoolingHatch> gtsr$mSteamCoolingHatches = new ArrayList<>();

    @Unique
    private final ArrayList<MTEPressureSteamCoolingHatch> gtsr$mPressureCoolingHatches = new ArrayList<>();

    @Unique
    private int gtsr$accumulatedSteam = 0;

    @Unique
    private MTESteamMultiBase gtsr$self() {
        return (MTESteamMultiBase) (Object) this;
    }

    @Unique
    private boolean gtsr$hasSuperheatedSteamInAnyHatch() {
        for (MTEHatchCustomFluidBase hatch : mSteamInputFluids) {
            if (hatch == null) continue;
            var fluid = hatch.getFluid();
            if (fluid != null && fluid.getFluid() != null
                && "ic2superheatedsteam".equals(
                    fluid.getFluid()
                        .getName())
                && fluid.amount > 0) {
                return true;
            }
        }
        return false;
    }

    // region Steam Consumption & Cooling

    /**
     * @reason Override to add cooling hatch support - pushes cooling products after steam consumption.
     *         Steam consumption formula: aSteamVal = (-lEUt * 10000) / max(1000, mEfficiency)
     *         Superheated: 4x consumption rate, 4x processing speed (via mMaxProgresstime / 4)
     */
    @Overwrite
    public boolean onRunningTick(ItemStack aStack) {
        MTESteamMultiBase self = gtsr$self();
        if (self.lEUt < 0) {
            long aSteamVal = ((-self.lEUt * 10000) / Math.max(1000, self.mEfficiency));
            boolean isSuperheated = gtsr$hasSuperheatedSteamInAnyHatch();
            if (isSuperheated) {
                aSteamVal *= 4;
                if (self.mProgresstime == 0) {
                    self.mMaxProgresstime = Math.max(1, self.mMaxProgresstime / 4);
                }
            }
            if (!self.tryConsumeSteam((int) aSteamVal)) {
                self.stopMachine(ShutDownReasonRegistry.POWER_LOSS);
                return false;
            }
            gtsr$pushCoolingProducts((int) aSteamVal, isSuperheated);
        }
        return true;
    }

    @Unique
    private void gtsr$pushCoolingProducts(int steamConsumed, boolean isSuperheated) {
        if (isSuperheated) {
            for (MTEPressureSteamCoolingHatch hatch : gtsr$mPressureCoolingHatches) {
                if (hatch != null && hatch.isValid()) {
                    hatch.pushCoolingSteam(steamConsumed);
                }
            }
        } else {
            gtsr$accumulatedSteam += steamConsumed;
            int waterAmount = gtsr$accumulatedSteam / 160;
            if (waterAmount > 0) {
                gtsr$accumulatedSteam %= 160;
                for (MTESteamCoolingHatch hatch : gtsr$mSteamCoolingHatches) {
                    if (hatch != null && hatch.isValid()) {
                        hatch.pushCoolingWater(waterAmount);
                    }
                }
            }
        }
    }

    // endregion

    // region Hatch Registration Hooks

    /**
     * Inject after clearHatches() to also clear cooling hatch lists.
     */
    @Inject(method = "clearHatches", at = @At("RETURN"))
    private void gtsr$onClearHatches(CallbackInfo ci) {
        gtsr$mSteamCoolingHatches.clear();
        gtsr$mPressureCoolingHatches.clear();
        gtsr$accumulatedSteam = 0;
    }

    @Inject(method = "saveNBTData", at = @At("RETURN"))
    private void gtsr$onSaveNBTData(NBTTagCompound aNBT, CallbackInfo ci) {
        aNBT.setInteger("gtsr.accumulatedSteam", gtsr$accumulatedSteam);
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"))
    private void gtsr$onLoadNBTData(NBTTagCompound aNBT, CallbackInfo ci) {
        gtsr$accumulatedSteam = aNBT.getInteger("gtsr.accumulatedSteam");
    }

    /**
     * Inject after addToMachineList() to recognize cooling hatches and update their texture.
     * The original method doesn't know about cooling hatches, so we handle them here.
     */
    @Inject(method = "addToMachineList", at = @At("RETURN"), cancellable = true)
    private void gtsr$onAddToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        if (aTileEntity == null) return;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return;

        if (aMetaTileEntity instanceof MTESteamCoolingHatch
            && !(aMetaTileEntity instanceof MTEPressureSteamCoolingHatch)) {
            MTESteamCoolingHatch hatch = (MTESteamCoolingHatch) aMetaTileEntity;
            if (hatch instanceof MTEHatch) {
                ((MTEHatch) hatch).updateTexture(aBaseCasingIndex);
            }
            gtsr$mSteamCoolingHatches.add(hatch);
            cir.setReturnValue(true);
        } else if (aMetaTileEntity instanceof MTEPressureSteamCoolingHatch) {
            MTEPressureSteamCoolingHatch hatch = (MTEPressureSteamCoolingHatch) aMetaTileEntity;
            if (hatch instanceof MTEHatch) {
                ((MTEHatch) hatch).updateTexture(aBaseCasingIndex);
            }
            gtsr$mPressureCoolingHatches.add(hatch);
            cir.setReturnValue(true);
        }
    }

    // endregion

    // region Existing Overrides (unchanged logic)

    /**
     * @reason Fix superheated steam detection to work across all input hatches.
     */
    @Overwrite
    public java.util.ArrayList<net.minecraftforge.fluids.FluidStack> getAllSteamStacks() {
        java.util.ArrayList<net.minecraftforge.fluids.FluidStack> aFluids = new java.util.ArrayList<>();
        net.minecraftforge.fluids.FluidStack aSteam = Materials.Steam.getGas(1);
        net.minecraftforge.fluids.FluidStack aSuperheatedSteam = net.minecraftforge.fluids.FluidRegistry
            .getFluidStack("ic2superheatedsteam", 1);
        for (MTEHatchCustomFluidBase tHatch : mSteamInputFluids) {
            if (tHatch != null) {
                net.minecraftforge.fluids.FluidStack tLiquid = tHatch.getFluid();
                if (tLiquid != null) {
                    if (tLiquid.isFluidEqual(aSteam)) {
                        aFluids.add(tLiquid);
                    } else if (aSuperheatedSteam != null && tLiquid.isFluidEqual(aSuperheatedSteam)) {
                        aFluids.add(tLiquid);
                    }
                }
            }
        }
        return aFluids;
    }

    /**
     * @reason Support both regular steam and superheated steam input hatches.
     *         Superheated steam is consumed at the requested amount (acts as 4x dense steam).
     */
    @Overwrite
    public boolean depleteInput(net.minecraftforge.fluids.FluidStack aLiquid) {
        if (aLiquid == null) return false;
        boolean isSteamRequest = aLiquid.isFluidEqual(Materials.Steam.getGas(1));
        for (MTEHatchCustomFluidBase tHatch : mSteamInputFluids) {
            net.minecraftforge.fluids.FluidStack tLiquid = tHatch.getFluid();
            if (tLiquid != null && tLiquid.isFluidEqual(aLiquid)) {
                tLiquid = tHatch.drain(aLiquid.amount, false);
                if (tLiquid != null && tLiquid.amount >= aLiquid.amount) {
                    tLiquid = tHatch.drain(aLiquid.amount, true);
                    return tLiquid != null && tLiquid.amount >= aLiquid.amount;
                }
            }
            if (isSteamRequest && tLiquid != null
                && "ic2superheatedsteam".equals(
                    tLiquid.getFluid()
                        .getName())) {
                tLiquid = tHatch.drain(aLiquid.amount, false);
                if (tLiquid != null && tLiquid.amount >= aLiquid.amount) {
                    tLiquid = tHatch.drain(aLiquid.amount, true);
                    return tLiquid != null && tLiquid.amount >= aLiquid.amount;
                }
            }
        }
        return false;
    }

    // endregion
}
