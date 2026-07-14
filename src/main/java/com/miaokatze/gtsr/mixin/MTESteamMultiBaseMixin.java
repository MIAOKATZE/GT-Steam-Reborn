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
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEHatchVoidBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusInput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusOutput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

@Mixin(value = MTESteamMultiBlockBase.class, remap = false)
public abstract class MTESteamMultiBaseMixin {

    // Shadow fields from MTESteamMultiBlockBase (mSteamInputFluids is NOT final in GT5U source)
    @Shadow
    public ArrayList<MTEHatchCustomFluidBase> mSteamInputFluids;

    @Shadow
    public ArrayList<MTEHatchOutputBus> mSteamOutputs;

    @Shadow
    public ArrayList<MTEHatchSteamBusInput> mSteamInputs;

    // Note: mInputHatches, mOutputHatches, mInputBusses, mOutputBusses are defined in
    // MTEMultiBlockBase (parent class), NOT in MTESteamMultiBlockBase itself.
    // Mixin @Shadow only searches the target class, not inherited fields.
    // We access them via ((MTEMultiBlockBase) gtsr$self()).mInputHatches etc.

    @Unique
    private final ArrayList<MTESteamCoolingHatch> gtsr$mSteamCoolingHatches = new ArrayList<>();

    @Unique
    private final ArrayList<MTEPressureSteamCoolingHatch> gtsr$mPressureCoolingHatches = new ArrayList<>();

    @Unique
    private int gtsr$accumulatedSteam = 0;

    @Unique
    private MTESteamMultiBlockBase gtsr$self() {
        return (MTESteamMultiBlockBase) (Object) this;
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
        MTESteamMultiBlockBase self = gtsr$self();
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

    // region Output Bus Compatibility Fix

    /**
     * Inject at HEAD of addOutputPartial(ItemStack) to replace the inherited implementation.
     *
     * In GT5U 2.9.0+, MTESteamMultiBlockBase inherits addOutputPartial from MTEMultiBlockBase,
     * which uses ItemEjectionHelper with getOutputBusses(). MTESteamMultiBlockBase overrides
     * getOutputBusses() to return only mSteamOutputs, ignoring mOutputBusses entirely.
     * This means standard OutputBus hatches (added via atLeast(OutputBus) in structure
     * definitions) are invisible to the output system.
     *
     * Since addOutputPartial is inherited (not declared in MTESteamMultiBlockBase), we cannot
     * use @Overwrite. Instead, we use @Inject at HEAD with cancellation to completely replace
     * the inherited behavior.
     *
     * This fix outputs to mSteamOutputs first (preserving original behavior), then
     * falls back to mOutputBusses for any remaining items.
     * We use MTEHatchOutputBus.storePartial() instead of the protected dumpItem()
     * because Mixin classes cannot access protected methods of the target hierarchy.
     *
     * @author GTSR
     */
    @Inject(method = "addOutputPartial", at = @At("HEAD"), cancellable = true)
    private void gtsr$onAddOutputPartialHead(ItemStack aStack, CallbackInfo ci) {
        if (GTUtility.isStackInvalid(aStack)) {
            ci.cancel();
            return;
        }
        aStack = GTUtility.copyOrNull(aStack);

        // Step 1: Try mSteamOutputs first (original behavior)
        for (MTEHatchOutputBus tHatch : GTUtility.validMTEList(mSteamOutputs)) {
            if (aStack.stackSize <= 0) break;
            tHatch.storePartial(aStack, false);
        }
        if (aStack.stackSize <= 0) {
            ci.cancel();
            return;
        }

        // Step 2: Try mOutputBusses (standard output buses - NEW behavior)
        // 去重：跳过 MTEHatchSteamBusOutput（已在 Step 1 的 mSteamOutputs 中处理），
        // 防止 atLeast(OutputBus) 把蒸汽输出总线也加到 mOutputBusses 导致重复输出
        MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
        for (MTEHatchOutputBus tHatch : GTUtility.validMTEList(multiBlockSelf.mOutputBusses)) {
            if (aStack.stackSize <= 0) break;
            if (tHatch instanceof MTEHatchSteamBusOutput) continue;
            tHatch.storePartial(aStack, false);
        }
        ci.cancel();
    }

    // endregion

    // region Hatch Registration Hooks

    /**
     * @reason Overwrite addToMachineList to restore GTSR's hatch registration behavior.
     *         New GT5U 2.9.0+ addSteamInputFluidHatch only accepts hatches locked to
     *         Materials.Steam.mGas (excluding superheated steam), and limits to 1 input
     *         fluid hatch. It also doesn't handle MTEHatchOutput (fluid output) or
     *         GTSR's cooling hatches. This overwrite restores the old behavior:
     *         - All MTEHatchCustomFluidBase → mSteamInputFluids (no fluid lock check)
     *         - MTEHatchSteamBusInput → mSteamInputs + mInputBusses (dual registration)
     *         - MTEHatchSteamBusOutput/MTEHatchVoidBus → mSteamOutputs + mOutputBusses (dual registration)
     *         - MTEHatchInput → mInputHatches
     *         - MTEHatchOutput → mOutputHatches
     *         - GTSR cooling hatches → custom lists
     * @author GTSR
     */
    @Overwrite
    public boolean addToMachineList(final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;

        // Handle pressure cooling hatch first (more specific subclass of MTESteamCoolingHatch)
        // MTEPressureSteamCoolingHatch 继承自 MTEHatch（经 MTESteamCoolingHatch → MTEHatchOutput → MTEHatch），
        // 因此 hatch.updateTexture 可直接调用，无需冗余的 instanceof 检查（Java 21+ 会拒绝编译冗余模式匹配）。
        if (aMetaTileEntity instanceof MTEPressureSteamCoolingHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            gtsr$mPressureCoolingHatches.add(hatch);
            return true;
        }

        // Handle regular cooling hatch (exclude pressure variant already handled above)
        if (aMetaTileEntity instanceof MTESteamCoolingHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            gtsr$mSteamCoolingHatches.add(hatch);
            return true;
        }

        // All MTEHatchCustomFluidBase → mSteamInputFluids (no fluid lock check, no limit)
        // This includes MTEHatchPressureSteamInput (ic2superheatedsteam) which the new
        // addSteamInputFluidHatch would reject because mLockedFluid != Materials.Steam.mGas
        if (aMetaTileEntity instanceof MTEHatchCustomFluidBase fluidHatch) {
            return gtsr$self().addToMachineListInternal(mSteamInputFluids, fluidHatch, aBaseCasingIndex);
        }

        // MTEHatchSteamBusInput → mSteamInputs only
        // 取消双注册到 mInputBusses：避免 GTNL 等模组的 getAllStoredInputs 同时遍历 mInputBusses 和 mSteamInputs 导致输入翻倍。
        // GTSR 机器的输入物品获取通过 gtsr$onGetAllStoredInputsTail 注入补充 mSteamInputs 遍历来保证。
        if (aMetaTileEntity instanceof MTEHatchSteamBusInput steamBus) {
            gtsr$self().resetRecipeMapForHatch(aTileEntity, gtsr$self().getRecipeMap());
            return gtsr$self().addToMachineListInternal(mSteamInputs, steamBus, aBaseCasingIndex);
        }

        // MTEHatchSteamBusOutput / MTEHatchVoidBus → mSteamOutputs only
        // 取消双注册到 mOutputBusses：避免 GTNL 等模组的 getOutputBusses/addOutput 同时遍历 mOutputBusses 和 mSteamOutputs 导致输出重复。
        // GTSR 机器的物品输出通过 gtsr$onGetOutputBussesTail 注入补充 mOutputBusses 遍历，以及 addOutputPartial Mixin 来保证。
        if (aMetaTileEntity instanceof MTEHatchSteamBusOutput || aMetaTileEntity instanceof MTEHatchVoidBus) {
            return gtsr$self()
                .addToMachineListInternal(mSteamOutputs, (MTEHatchOutputBus) aMetaTileEntity, aBaseCasingIndex);
        }

        // MTEHatchInput → mInputHatches (standard fluid input)
        // Note: MTEHatchInputBus, MTEHatchOutputBus, MTEHatchOutput are intentionally NOT
        // handled here — they should return false so that subsequent chain elements
        // (e.g. atLeast(InputBus/OutputBus) or casing blocks) can process them.
        // This matches vanilla MTESteamMultiBlockBase behavior and avoids inflating
        // their NEI priority above casing blocks in GT++ native steam machines.
        if (aMetaTileEntity instanceof MTEHatchInput inputHatch) {
            MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
            return gtsr$self().addToMachineListInternal(multiBlockSelf.mInputHatches, inputHatch, aBaseCasingIndex);
        }

        return false;
    }

    // region GT5U Native Adder Compatibility

    /**
     * Inject into addSteamInputFluidHatch to also accept pressure steam hatches.
     *
     * GT5U's addSteamInputFluidHatch only accepts hatches locked to Materials.Steam.mGas,
     * rejecting MTEHatchPressureSteamInput (locked to ic2superheatedsteam).
     * We inject at TAIL: if the original method returned false but the hatch is a
     * MTEHatchCustomFluidBase, we add it to mSteamInputFluids ourselves.
     *
     * @author GTSR
     */
    @Inject(method = "addSteamInputFluidHatch", at = @At("TAIL"), cancellable = true)
    private void gtsr$onAddSteamInputFluidHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        // If original already succeeded, nothing to do
        if (cir.getReturnValueZ()) return;

        if (aTileEntity == null) return;
        final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return;

        // Accept any MTEHatchCustomFluidBase (including pressure steam) that the original rejected
        if (aMetaTileEntity instanceof MTEHatchCustomFluidBase fluidHatch) {
            cir.setReturnValue(gtsr$self().addToMachineListInternal(mSteamInputFluids, fluidHatch, aBaseCasingIndex));
        }
    }

    // gtsr$onAddSteamBusInput 已删除：取消双注册到 mInputBusses，避免 GTNL 等模组的 getAllStoredInputs 输入翻倍。
    // 蒸汽输入总线只加到 mSteamInputs（GT5U 原生行为），GTSR 机器通过 gtsr$onGetAllStoredInputsTail 注入补充遍历。

    /**
     * Inject into addSteamBusOutput to accept MTEHatchOutput (including cooling hatches) when original fails.
     *
     * 取消双注册到 mOutputBusses：避免 GTNL 等模组的 getOutputBusses/addOutput 输出重复。
     * 蒸汽输出总线只加到 mSteamOutputs（GT5U 原生行为），GTSR 机器通过 gtsr$onGetOutputBussesTail
     * 注入补充合并 mOutputBusses 遍历，以及 addOutputPartial Mixin 来保证物品输出。
     *
     * GT5U's addSteamBusOutput only handles MTEHatchSteamBusOutput and MTEHatchVoidBus.
     * Cooling hatches (MTESteamCoolingHatch extends MTEHatchOutput) are not recognized,
     * so they have nowhere to be placed in GT5U native steam machines.
     * We intercept at TAIL: if original failed but the hatch is an MTEHatchOutput,
     * we register it to mOutputHatches and cooling hatch lists.
     *
     * @author GTSR
     */
    @Inject(method = "addSteamBusOutput", at = @At("TAIL"), cancellable = true)
    private void gtsr$onAddSteamBusOutput(IGregTechTileEntity aTileEntity, int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        if (aTileEntity == null) return;
        final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return;

        // 原方法成功（MTEHatchSteamBusOutput/MTEHatchVoidBus 已加到 mSteamOutputs），不再双注册到 mOutputBusses
        if (cir.getReturnValueZ()) {
            return;
        }

        // Original failed - check for MTEHatchOutput (cooling hatches, fluid output hatches)
        // Handle pressure cooling hatch first (more specific subclass)
        // 同上，MTEPressureSteamCoolingHatch 继承自 MTEHatch，hatch.updateTexture 可直接调用。
        if (aMetaTileEntity instanceof MTEPressureSteamCoolingHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            gtsr$mPressureCoolingHatches.add(hatch);
            // Also add to mOutputHatches so the structure accepts it
            MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
            gtsr$self().addToMachineListInternal(multiBlockSelf.mOutputHatches, hatch, aBaseCasingIndex);
            cir.setReturnValue(true);
            return;
        }

        // Handle regular cooling hatch
        if (aMetaTileEntity instanceof MTESteamCoolingHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            gtsr$mSteamCoolingHatches.add(hatch);
            // Also add to mOutputHatches so the structure accepts it
            MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
            gtsr$self().addToMachineListInternal(multiBlockSelf.mOutputHatches, hatch, aBaseCasingIndex);
            cir.setReturnValue(true);
            return;
        }

        // Handle generic MTEHatchOutput (fluid output hatches)
        if (aMetaTileEntity instanceof MTEHatchOutput outputHatch) {
            MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
            cir.setReturnValue(
                gtsr$self().addToMachineListInternal(multiBlockSelf.mOutputHatches, outputHatch, aBaseCasingIndex));
        }
    }

    // endregion

    /**
     * Inject after clearHatches() to also clear cooling hatch lists.
     * Note: MTESteamMultiBlockBase.clearHatches() calls super.clearHatches() which clears
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

    // region Input/Output Aggregation (取消双注册后的合并读取层)

    /**
     * 注入 getAllStoredInputs 的 RETURN，补充遍历 mSteamInputs。
     *
     * 取消双注册后，蒸汽输入总线（MTEHatchSteamBusInput）只在 mSteamInputs 中，不在 mInputBusses 中。
     * GT5U 原生的 getAllStoredInputs（定义在 MTEMultiBlockBase）只遍历 mInputBusses，找不到蒸汽输入总线中的物品。
     * 本注入在原方法执行后，补充遍历 mSteamInputs，确保 GTSR 机器能获取所有输入物品。
     *
     * 去重：由于取消了双注册，mInputBusses 和 mSteamInputs 没有重复对象。但为了保险（防止其他路径
     * 把蒸汽输入总线加到 mInputBusses），仍然用 IdentityHashMap 去重。
     *
     * 注意：GTNL 等模组自己覆写了 getAllStoredInputs，本注入对它们无效（mixin 优先级低于子类覆写）。
     *
     * @author GTSR
     */
    @Inject(method = "getAllStoredInputs", at = @At("RETURN"))
    private void gtsr$onGetAllStoredInputsTail(CallbackInfoReturnable<ArrayList<net.minecraft.item.ItemStack>> cir) {
        ArrayList<net.minecraft.item.ItemStack> result = cir.getReturnValue();
        if (result == null) {
            result = new ArrayList<>();
            cir.setReturnValue(result);
        }

        // 去重集合：记录 mInputBusses 中已处理的 hatch
        java.util.Set<gregtech.api.metatileentity.implementations.MTEHatchInputBus> seen = java.util.Collections
            .newSetFromMap(new java.util.IdentityHashMap<>());
        MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
        for (gregtech.api.metatileentity.implementations.MTEHatchInputBus bus : GTUtility
            .validMTEList(multiBlockSelf.mInputBusses)) {
            seen.add(bus);
        }

        // 补充遍历 mSteamInputs（蒸汽输入总线）
        for (MTEHatchSteamBusInput tHatch : GTUtility.validMTEList(mSteamInputs)) {
            if (seen.contains(tHatch)) continue;
            tHatch.mRecipeMap = gtsr$self().getRecipeMap();
            IGregTechTileEntity tileEntity = tHatch.getBaseMetaTileEntity();
            for (int i = tileEntity.getSizeInventory() - 1; i >= 0; i--) {
                net.minecraft.item.ItemStack itemStack = tileEntity.getStackInSlot(i);
                if (itemStack != null) {
                    result.add(itemStack);
                }
            }
        }
    }

    /**
     * 注入 getOutputBusses 的 RETURN，补充合并 mOutputBusses。
     *
     * 取消双注册后，蒸汽输出总线只在 mSteamOutputs 中。GT5U 原生的 getOutputBusses（MTESteamMultiBlockBase 覆写）
     * 只返回 mSteamOutputs，不包含 mOutputBusses 中的标准输出总线。
     * 本注入在原方法执行后，补充合并 mOutputBusses（去重），确保所有输出总线都能被 ItemEjectionHelper 使用。
     *
     * 去重：用 IdentityHashMap 去重，防止同一 hatch 被加入两次。
     * 排除未锁定的 VoidBus（与 MTEMultiBlockBase.getOutputBusses 行为一致）。
     *
     * 注意：GTNL 等模组自己覆写了 getOutputBusses，本注入对它们无效（mixin 优先级低于子类覆写）。
     *
     * @author GTSR
     */
    @Inject(method = "getOutputBusses", at = @At("RETURN"))
    private void gtsr$onGetOutputBussesTail(
        CallbackInfoReturnable<java.util.List<gregtech.api.interfaces.IOutputBus>> cir) {
        java.util.List<gregtech.api.interfaces.IOutputBus> original = cir.getReturnValue();
        java.util.List<gregtech.api.interfaces.IOutputBus> result = new java.util.ArrayList<>(original);

        // 去重集合：记录原返回列表中已包含的 hatch
        java.util.Set<MTEHatchOutputBus> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (gregtech.api.interfaces.IOutputBus bus : original) {
            if (bus instanceof MTEHatchOutputBus outputBus) {
                seen.add(outputBus);
            }
        }

        // 补充合并 mOutputBusses（去重，排除未锁定的 VoidBus）
        MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
        for (MTEHatchOutputBus outputBus : GTUtility.validMTEList(multiBlockSelf.mOutputBusses)) {
            if (outputBus instanceof MTEHatchVoidBus voidBus && !voidBus.isLocked()) {
                continue;
            }
            if (seen.add(outputBus)) {
                result.add(outputBus);
            }
        }

        cir.setReturnValue(result);
    }

    // endregion

    // region Existing Overrides (unchanged logic)

    /**
     * @reason Fix superheated steam detection to work across all input hatches.
     *         In GT5U 2.9.0+, the base getAllSteamStacks() uses getStoredFluids() which
     *         only checks for regular steam, missing superheated steam. We override to
     *         iterate mSteamInputFluids directly and detect both steam types.
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
