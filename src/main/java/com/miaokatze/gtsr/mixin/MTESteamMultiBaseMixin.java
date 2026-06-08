package com.miaokatze.gtsr.mixin;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

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
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusOutput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

@Mixin(value = MTESteamMultiBase.class, remap = false)
public abstract class MTESteamMultiBaseMixin {

    // Shadow fields from MTESteamMultiBase (mSteamInputFluids is NOT final in GT5U source)
    @Shadow
    public ArrayList<MTEHatchCustomFluidBase> mSteamInputFluids;

    @Shadow
    public ArrayList<MTEHatchSteamBusOutput> mSteamOutputs;

    // Note: mOutputBusses is defined in MTEMultiBlockBase (grandparent class), NOT in MTESteamMultiBase.
    // We cannot @Shadow it here because Mixin only searches the target class.
    // Instead, we access it via ((MTEMultiBlockBase) gtsr$self()).mOutputBusses

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

    // GTSR Debug: temporary logging method - remove after verification
    @Unique
    private void gtsr$log(String message) {
        System.out.println("[GTSR-Mixin] " + message);
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
     * @author GTSR
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
                // GTSR Debug
                gtsr$log("onRunningTick: superheated steam, consumption=" + aSteamVal + ", speed=4x");
            }
            if (!self.tryConsumeSteam((int) aSteamVal)) {
                // GTSR Debug
                gtsr$log("onRunningTick: steam consumption FAILED, stopping machine");
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

    // region Output Bus Compatibility Fix

    /**
     * @reason Overwrite addOutput(ItemStack) to also output to standard mOutputBusses.
     *         In GT5U 5.09.51.482, MTESteamMultiBase.addOutput(ItemStack) only outputs to
     *         mSteamOutputs and mOutputHatches, completely ignoring mOutputBusses.
     *         This means standard OutputBus hatches (added via atLeast(OutputBus) in structure
     *         definitions) are invisible to the output system.
     *         This fix outputs to mSteamOutputs first (preserving original behavior), then
     *         falls back to mOutputBusses for any remaining items.
     *         We use MTEHatchOutputBus.storePartial() instead of the protected dumpItem()
     *         because Mixin classes cannot access protected methods of the target hierarchy.
     * @author GTSR
     */
    @Overwrite
    public boolean addOutput(ItemStack aStack) {
        if (GTUtility.isStackInvalid(aStack)) return false;
        aStack = GTUtility.copyOrNull(aStack);

        // Step 1: Try mSteamOutputs first (original behavior)
        for (MTEHatchSteamBusOutput tHatch : GTUtility.validMTEList(mSteamOutputs)) {
            if (aStack.stackSize <= 0) break;
            tHatch.storePartial(aStack, false);
        }
        if (aStack.stackSize <= 0) {
            // GTSR Debug
            gtsr$log("addOutput: output to mSteamOutputs (full)");
            return true;
        }
        // GTSR Debug: partial output to mSteamOutputs
        if (!mSteamOutputs.isEmpty()) {
            gtsr$log("addOutput: mSteamOutputs partial, remaining=" + aStack.stackSize);
        }

        // Step 2: Try mOutputBusses (standard output buses - NEW behavior)
        MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
        for (MTEHatchOutputBus tHatch : GTUtility.validMTEList(multiBlockSelf.mOutputBusses)) {
            if (aStack.stackSize <= 0) break;
            tHatch.storePartial(aStack, false);
        }
        if (aStack.stackSize <= 0) {
            // GTSR Debug
            gtsr$log("addOutput: output to mOutputBusses (full)");
            return true;
        }

        // GTSR Debug
        gtsr$log("addOutput: FAILED to fully output item, remaining=" + aStack.stackSize);
        return false;
    }

    // endregion

    // region Hatch Registration Hooks

    /**
     * Inject at HEAD of addToMachineList to handle cooling hatches before the original method.
     * Cooling hatches extend MTEHatchOutput, which is not handled by the original addToMachineList
     * (it only handles MTEHatchCustomFluidBase, MTEHatchSteamBusInput, MTEHatchSteamBusOutput,
     * MTEVoidBus, MTEHatchInput). By intercepting at HEAD, we handle cooling hatches ourselves
     * and let the original method handle the rest.
     */
    @Inject(method = "addToMachineList", at = @At("HEAD"), cancellable = true)
    private void gtsr$onAddToMachineListHead(IGregTechTileEntity aTileEntity, int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        if (aTileEntity == null) return;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return;

        // Handle MTEHatchSteamBusOutput: add to mOutputBusses IN ADDITION to mSteamOutputs.
        // Original addToMachineList puts MTEHatchSteamBusOutput into mSteamOutputs.
        // We also add it to mOutputBusses so that machines checking mOutputBusses.size()
        // (like MTESteamSingularityCompressor) and addOutput() can find it there.
        // We do NOT cancel the original method, so mSteamOutputs still gets the hatch
        // (preserving behavior for GT5U machines that check !mSteamOutputs.isEmpty()).
        if (aMetaTileEntity instanceof MTEHatchSteamBusOutput) {
            MTEHatchSteamBusOutput hatch = (MTEHatchSteamBusOutput) aMetaTileEntity;
            MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
            // Avoid duplicate if already in mOutputBusses (e.g., from a custom adder)
            if (!multiBlockSelf.mOutputBusses.contains(hatch)) {
                multiBlockSelf.mOutputBusses.add(hatch);
                // GTSR Debug
                gtsr$log("SteamBusOutput dual-list: added to mOutputBusses, mSteamOutputs handled by original method");
            }
            // Don't cancel - let original method also add to mSteamOutputs
        }

        // Handle pressure cooling hatch first (more specific subclass of MTESteamCoolingHatch)
        if (aMetaTileEntity instanceof MTEPressureSteamCoolingHatch) {
            MTEPressureSteamCoolingHatch hatch = (MTEPressureSteamCoolingHatch) aMetaTileEntity;
            if (hatch instanceof MTEHatch) {
                ((MTEHatch) hatch).updateTexture(aBaseCasingIndex);
            }
            gtsr$mPressureCoolingHatches.add(hatch);
            // GTSR Debug: log cooling hatch registration
            gtsr$log("Pressure cooling hatch registered");
            cir.setReturnValue(true);
            return;
        }

        // Handle regular cooling hatch (exclude pressure variant already handled above)
        if (aMetaTileEntity instanceof MTESteamCoolingHatch) {
            MTESteamCoolingHatch hatch = (MTESteamCoolingHatch) aMetaTileEntity;
            if (hatch instanceof MTEHatch) {
                ((MTEHatch) hatch).updateTexture(aBaseCasingIndex);
            }
            gtsr$mSteamCoolingHatches.add(hatch);
            // GTSR Debug: log cooling hatch registration
            gtsr$log("Cooling hatch registered");
            cir.setReturnValue(true);
            return;
        }

        // For other hatch types (MTEHatchCustomFluidBase, MTEHatchSteamBusInput,
        // MTEHatchSteamBusOutput, MTEVoidBus, MTEHatchInput),
        // let the original addToMachineList handle them
    }

    /**
     * Inject after clearHatches() to also clear cooling hatch lists.
     * Note: MTESteamMultiBase.clearHatches() calls super.clearHatches() which clears
     * mOutputBusses and mInputBusses, so we only need to clear our custom lists here.
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

    // endregion

    // region Existing Overrides (unchanged logic)

    /**
     * @reason Fix superheated steam detection to work across all input hatches.
     * @author GTSR
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
     * @author GTSR
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
