package com.miaokatze.gtsr.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.api.compat.ICoolingHatchHolder;
import com.miaokatze.gtsr.api.compat.SteamCoolingSupport;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;
import com.miaokatze.gtsr.config.Config;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputDebug;
import gregtech.api.metatileentity.implementations.MTEHatchMultiInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEHatchVoidBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.tileentities.machines.MTEHatchInputME;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusInput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusOutput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

@Mixin(value = MTESteamMultiBlockBase.class, remap = false)
public abstract class MTESteamMultiBaseMixin implements ICoolingHatchHolder {

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

    // region ICoolingHatchHolder 接口实现
    // 暴露 @Unique private 字段供 GTNL 专用 mixin 通过接口访问
    // （GTNL 子类 mixin 无法直接访问父类 mixin 的 @Unique private 字段）

    @Override
    @Unique
    public ArrayList<MTESteamCoolingHatch> gtsr$getCoolingHatches() {
        return gtsr$mSteamCoolingHatches;
    }

    @Override
    @Unique
    public ArrayList<MTEPressureSteamCoolingHatch> gtsr$getPressureHatches() {
        return gtsr$mPressureCoolingHatches;
    }

    @Override
    @Unique
    public int gtsr$getAccumulatedSteam() {
        return gtsr$accumulatedSteam;
    }

    @Override
    @Unique
    public void gtsr$setAccumulatedSteam(int value) {
        gtsr$accumulatedSteam = value;
    }

    // endregion

    /**
     * 运行时守卫：判断当前实例是否为 GTNL（com.science.gtnl.* 包）机器。
     * this.getClass() 返回实际运行时类（如 GTNL 子类），而非 Mixin 注入的目标类。
     * GTNL 机器开启增强时仅对冷却舱室相关逻辑放行，其他回退到 GT5U 原生行为。
     */
    @Unique
    private boolean gtsr$isGTNLMachine() {
        return this.getClass()
            .getName()
            .startsWith("com.science.gtnl.");
    }

    /**
     * 判断元机器实体是否为 GTSR 冷却舱室（普通或压力）。
     * 先判断压力舱室，因其继承自普通舱室。
     *
     * @param aMetaTileEntity 元机器实体
     * @return 是冷却舱室返回 true，否则 false
     */
    @Unique
    private boolean gtsr$isCoolingHatch(IMetaTileEntity aMetaTileEntity) {
        return aMetaTileEntity instanceof MTEPressureSteamCoolingHatch
            || aMetaTileEntity instanceof MTESteamCoolingHatch;
    }

    // region Steam Consumption & Cooling

    /**
     * @reason Inject at HEAD to add cooling hatch support - pushes cooling products after steam consumption.
     *         Steam consumption formula: aSteamVal = (-lEUt * 10000) / max(1000, mEfficiency)
     *         Superheated: 4x consumption rate, 4x processing speed (via mMaxProgresstime / 4)
     * @author GTSR
     *         注：转为 @Inject(HEAD, cancellable=true) + GTNL 守卫，避免影响 GTNL 子类机器（GTNL 走原生）。
     */
    @Inject(method = "onRunningTick(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void gtsr$onRunningTickHead(ItemStack aStack, CallbackInfoReturnable<Boolean> cir) {
        // GTNL 守卫：GTNL 机器走 GT5U 原生行为，跳过 GTSR 逻辑
        // （GTNL 覆写 onRunningTick 不调 super，本注入对 GTNL 机器不触发；守卫保留为双重保险）
        if (gtsr$isGTNLMachine()) return;

        MTESteamMultiBlockBase self = gtsr$self();
        if (self.lEUt < 0) {
            long aSteamVal = ((-self.lEUt * 10000) / Math.max(1000, self.mEfficiency));
            boolean isSuperheated = SteamCoolingSupport.hasSuperheatedSteam(self);
            if (isSuperheated) {
                aSteamVal *= 4;
                if (self.mProgresstime == 0) {
                    self.mMaxProgresstime = Math.max(1, self.mMaxProgresstime / 4);
                }
            }
            if (!self.tryConsumeSteam((int) aSteamVal)) {
                self.stopMachine(ShutDownReasonRegistry.POWER_LOSS);
                cir.setReturnValue(false);
                return;
            }
            SteamCoolingSupport.pushCoolingProducts((ICoolingHatchHolder) this, (int) aSteamVal, isSuperheated);
        }
        cir.setReturnValue(true);
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
        // GTNL 守卫：GTNL 机器走 GT5U 原生 addOutputPartial 行为（不 cancel），跳过 GTSR 双总线输出逻辑
        if (gtsr$isGTNLMachine()) return;

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
     * @reason Inject at HEAD to restore GTSR's hatch registration behavior.
     *         New GT5U 2.9.0+ addSteamInputFluidHatch only accepts hatches locked to
     *         Materials.Steam.mGas (excluding superheated steam), and limits to 1 input
     *         fluid hatch. It also doesn't handle MTEHatchOutput (fluid output) or
     *         GTSR's cooling hatches. This inject restores the old behavior:
     *         - All MTEHatchCustomFluidBase → mSteamInputFluids (no fluid lock check)
     *         - MTEHatchSteamBusInput → mSteamInputs + mInputBusses (dual registration)
     *         - MTEHatchSteamBusOutput/MTEHatchVoidBus → mSteamOutputs + mOutputBusses (dual registration)
     *         - MTEHatchInput → mInputHatches
     *         - MTEHatchOutput → mOutputHatches
     *         - GTSR cooling hatches → custom lists
     * @author GTSR
     *         注：转为 @Inject(HEAD, cancellable=true) + GTNL 守卫，避免影响 GTNL 子类机器（GTNL 走原生）。
     *         使用方法描述符避免与父类 MTEMultiBlockBase 的单参重载 addToMachineList(IGregTechTileEntity) 歧义。
     */
    @Inject(
        method = "addToMachineList(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;I)Z",
        at = @At("HEAD"),
        cancellable = true)
    private void gtsr$addToMachineListHead(final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        // GTNL 守卫：GTNL 机器仅在开启增强且为冷却舱室时走 GTSR 注册，其他情况走原生
        // （避免输入翻倍回归：非冷却舱室走 GTNL 原生 addToMachineList）
        if (gtsr$isGTNLMachine()) {
            if (!Config.gtnlEnhancement) return;
            final IMetaTileEntity metaTileEntity = (aTileEntity == null) ? null : aTileEntity.getMetaTileEntity();
            if (!gtsr$isCoolingHatch(metaTileEntity)) return;
            // 开启增强 + 冷却舱室：走下方 GTSR 冷却舱室注册逻辑
        }

        if (aTileEntity == null) {
            cir.setReturnValue(false);
            return;
        }
        final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) {
            cir.setReturnValue(false);
            return;
        }

        // Handle pressure cooling hatch first (more specific subclass of MTESteamCoolingHatch)
        // MTEPressureSteamCoolingHatch 继承自 MTEHatch（经 MTESteamCoolingHatch → MTEHatchOutput → MTEHatch），
        // 因此 hatch.updateTexture 可直接调用，无需冗余的 instanceof 检查（Java 21+ 会拒绝编译冗余模式匹配）。
        if (aMetaTileEntity instanceof MTEPressureSteamCoolingHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            gtsr$mPressureCoolingHatches.add(hatch);
            cir.setReturnValue(true);
            return;
        }

        // Handle regular cooling hatch (exclude pressure variant already handled above)
        if (aMetaTileEntity instanceof MTESteamCoolingHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            gtsr$mSteamCoolingHatches.add(hatch);
            cir.setReturnValue(true);
            return;
        }

        // All MTEHatchCustomFluidBase → mSteamInputFluids (no fluid lock check, no limit)
        // This includes MTEHatchPressureSteamInput (ic2superheatedsteam) which the new
        // addSteamInputFluidHatch would reject because mLockedFluid != Materials.Steam.mGas
        if (aMetaTileEntity instanceof MTEHatchCustomFluidBase fluidHatch) {
            cir.setReturnValue(gtsr$self().addToMachineListInternal(mSteamInputFluids, fluidHatch, aBaseCasingIndex));
            return;
        }

        // MTEHatchSteamBusInput → mSteamInputs only
        // 取消双注册到 mInputBusses：避免 GTNL 等模组的 getAllStoredInputs 同时遍历 mInputBusses 和 mSteamInputs 导致输入翻倍。
        // GTSR 机器的输入物品获取通过 gtsr$onGetAllStoredInputsTail 注入补充 mSteamInputs 遍历来保证。
        if (aMetaTileEntity instanceof MTEHatchSteamBusInput steamBus) {
            gtsr$self().resetRecipeMapForHatch(aTileEntity, gtsr$self().getRecipeMap());
            cir.setReturnValue(gtsr$self().addToMachineListInternal(mSteamInputs, steamBus, aBaseCasingIndex));
            return;
        }

        // MTEHatchSteamBusOutput / MTEHatchVoidBus → mSteamOutputs only
        // 取消双注册到 mOutputBusses：避免 GTNL 等模组的 getOutputBusses/addOutput 同时遍历 mOutputBusses 和 mSteamOutputs 导致输出重复。
        // GTSR 机器的物品输出通过 gtsr$onGetOutputBussesTail 注入补充 mOutputBusses 遍历，以及 addOutputPartial Mixin 来保证。
        if (aMetaTileEntity instanceof MTEHatchSteamBusOutput || aMetaTileEntity instanceof MTEHatchVoidBus) {
            cir.setReturnValue(
                gtsr$self()
                    .addToMachineListInternal(mSteamOutputs, (MTEHatchOutputBus) aMetaTileEntity, aBaseCasingIndex));
            return;
        }

        // MTEHatchInput → mInputHatches (standard fluid input)
        // Note: MTEHatchInputBus, MTEHatchOutputBus, MTEHatchOutput are intentionally NOT
        // handled here — they should return false so that subsequent chain elements
        // (e.g. atLeast(InputBus/OutputBus) or casing blocks) can process them.
        // This matches vanilla MTESteamMultiBlockBase behavior and avoids inflating
        // their NEI priority above casing blocks in GT++ native steam machines.
        if (aMetaTileEntity instanceof MTEHatchInput inputHatch) {
            MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
            cir.setReturnValue(
                gtsr$self().addToMachineListInternal(multiBlockSelf.mInputHatches, inputHatch, aBaseCasingIndex));
            return;
        }

        cir.setReturnValue(false);
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
        // GTNL 守卫：GTNL 机器走 GT5U 原生 addSteamInputFluidHatch 行为（不 cancel），跳过 GTSR 扩展接受逻辑
        if (gtsr$isGTNLMachine()) return;

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
        // GTNL 守卫：GTNL 机器仅在开启增强且为冷却舱室时走 GTSR 注册，其他情况走原生
        if (gtsr$isGTNLMachine()) {
            if (!Config.gtnlEnhancement) return;
            if (cir.getReturnValueZ()) return; // 原方法已成功，不再处理
            final IMetaTileEntity metaTileEntity = (aTileEntity == null) ? null : aTileEntity.getMetaTileEntity();
            if (!gtsr$isCoolingHatch(metaTileEntity)) return; // 非冷却舱室：走原生
            // 开启增强 + 原方法失败 + 冷却舱室：走下方 GTSR 冷却舱室注册逻辑
        }

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
        // 移除 GTNL 守卫：GTNL 机器继承父类注入的 ICoolingHatchHolder 字段，清理无副作用
        // GTNL clearHatches 调 super，mixin 触发，让 GTNL 机器也清理冷却舱室列表
        SteamCoolingSupport.clearHatches((ICoolingHatchHolder) this);
    }

    @Inject(method = "saveNBTData", at = @At("RETURN"))
    private void gtsr$onSaveNBTData(NBTTagCompound aNBT, CallbackInfo ci) {
        // 移除 GTNL 守卫：GTNL NBT 调 super，mixin 触发，让 GTNL 机器也持久化 accumulatedSteam
        SteamCoolingSupport.saveNBT((ICoolingHatchHolder) this, aNBT);
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"))
    private void gtsr$onLoadNBTData(NBTTagCompound aNBT, CallbackInfo ci) {
        // 移除 GTNL 守卫：GTNL NBT 调 super，mixin 触发，让 GTNL 机器也加载 accumulatedSteam
        SteamCoolingSupport.loadNBT((ICoolingHatchHolder) this, aNBT);
    }

    // endregion

    // region Input/Output Aggregation (取消双注册后的合并读取层，含 GTNL 守卫)

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
     * cancellable=true 必填：方法体内（result == null 分支）调用 cir.setReturnValue(result) 覆写返回值，
     * 未声明 cancellable 会抛出 CancellationException: The call getAllStoredInputs is not cancellable.
     *
     * @author GTSR
     */
    @Inject(method = "getAllStoredInputs", at = @At("RETURN"), cancellable = true)
    private void gtsr$onGetAllStoredInputsTail(CallbackInfoReturnable<ArrayList<net.minecraft.item.ItemStack>> cir) {
        // GTNL 守卫：GTNL 机器若覆写 getAllStoredInputs 则本注入无效；若未覆写则避免 GTSR 补充 mSteamInputs
        // 遍历导致输入翻倍，统一跳过让 GTNL 走 GT5U 原生行为
        if (gtsr$isGTNLMachine()) return;

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
     * 注入 getStoredInputsForColor 的 RETURN，补充遍历 mInputBusses（标准输入总线）。
     *
     * MTESteamMultiBlockBase 覆写了 getStoredInputsForColor，但只遍历 mSteamInputs（蒸汽输入总线），
     * 完全忽略 mInputBusses。GT5U 标准配方流程 doCheckRecipe 经 getStoredInputsForColor 收集输入物品，
     * 导致结构上允许放置的标准输入总线（GTSRHatchElement.SteamInputBus → addInputBusToMachineList）
     * 中的物品永远不参与配方匹配（大型蒸汽熔炉早期 bug：只识别蒸汽输入总线中的物品）。
     * 本注入与原方法结果合并，对齐 GT5U 原生 getStoredInputsForColor 语义：
     * - 跳过 MTEHatchCraftingInputME（样板仓走 doCheckRecipe 的 mDualInputHatches 分支）
     * - 按 Optional color 过滤总线颜色
     * - 同步 mRecipeMap
     * - ME 输入总线按 ItemId 去重
     * - 防御性跳过 MTEHatchSteamBusInput（基类已遍历，防双注册重复）
     * 消耗侧无需处理：GTRecipe.consumeInput 直接扣减本方法返回的槽位 ItemStack 引用。
     * 对 GT++ 原生蒸汽机器无副作用（其 mInputBusses 为空）。
     *
     * cancellable=true 必填：result == null 分支调用 cir.setReturnValue(result) 覆写返回值。
     *
     * @author GTSR
     */
    @Inject(method = "getStoredInputsForColor", at = @At("RETURN"), cancellable = true)
    private void gtsr$onGetStoredInputsForColorTail(java.util.Optional<java.lang.Byte> color,
        CallbackInfoReturnable<ArrayList<net.minecraft.item.ItemStack>> cir) {
        // GTNL 守卫：GTNL 机器走自身覆写/原生行为，避免 GTSR 补充遍历导致输入翻倍
        if (gtsr$isGTNLMachine()) return;

        ArrayList<net.minecraft.item.ItemStack> result = cir.getReturnValue();
        if (result == null) {
            result = new ArrayList<>();
            cir.setReturnValue(result);
        }

        MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
        java.util.Map<gregtech.api.util.GTUtility.ItemId, net.minecraft.item.ItemStack> inputsFromME = new java.util.HashMap<>();
        for (gregtech.api.metatileentity.implementations.MTEHatchInputBus tHatch : GTUtility
            .validMTEList(multiBlockSelf.mInputBusses)) {
            if (tHatch instanceof gregtech.common.tileentities.machines.MTEHatchCraftingInputME) {
                continue;
            }
            // 防御性去重：蒸汽输入总线只由基类从 mSteamInputs 遍历，此处跳过防止其他路径双注册导致重复
            if (tHatch instanceof MTEHatchSteamBusInput) {
                continue;
            }
            byte busColor = tHatch.getColor();
            if (color.isPresent() && busColor != -1 && busColor != color.get()) continue;
            tHatch.mRecipeMap = gtsr$self().getRecipeMap();
            IGregTechTileEntity tileEntity = tHatch.getBaseMetaTileEntity();
            boolean isMEBus = tHatch instanceof gregtech.common.tileentities.machines.MTEHatchInputBusME;
            for (int i = tileEntity.getSizeInventory() - 1; i >= 0; i--) {
                net.minecraft.item.ItemStack itemStack = tileEntity.getStackInSlot(i);
                if (itemStack != null) {
                    if (isMEBus) {
                        // 防止不同 ME 总线中的相同物品被重复识别（与 GT5U 原生行为一致）
                        inputsFromME.put(gregtech.api.util.GTUtility.ItemId.createNoCopy(itemStack), itemStack);
                    } else {
                        result.add(itemStack);
                    }
                }
            }
        }
        if (!inputsFromME.isEmpty()) {
            result.addAll(inputsFromME.values());
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
     * cancellable=true 必填：方法体内调用 cir.setReturnValue(result) 覆写返回值，
     * 未声明 cancellable 会导致运行时抛出 CancellationException: The call getOutputBusses is not cancellable.
     *
     * @author GTSR
     */
    @Inject(method = "getOutputBusses", at = @At("RETURN"), cancellable = true)
    private void gtsr$onGetOutputBussesTail(
        CallbackInfoReturnable<java.util.List<gregtech.api.interfaces.IOutputBus>> cir) {
        // GTNL 守卫：GTNL 机器若覆写 getOutputBusses 则本注入无效；若未覆写则避免 GTSR 合并 mOutputBusses
        // 导致输出重复，统一跳过让 GTNL 走 GT5U 原生行为
        if (gtsr$isGTNLMachine()) return;

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

    // region Recipe Fluid Collection (ME Hatch Visibility)

    /**
     * @reason Inject at HEAD to restore ME input hatch visibility in recipe fluid collection.
     *         GT5U native getStoredFluidsForColor has a special branch for MTEHatchInputME
     *         (via meHatch.getStoredFluids(), deduplicated by fluid type), but GT++'s
     *         MTESteamMultiBlockBase override replaces it with getFillableStack() (local tank),
     *         which is always null for ME hatches. This makes all MTESteamMultiBlockBase machines
     *         unable to see ME input hatch fluids during recipe collection.
     *         This override mirrors the GT5U native implementation while preserving the
     *         mSteamInputFluids (GT++ custom fluid hatches) iteration.
     * @author GTSR
     *         注：转为 @Inject(HEAD, cancellable=true) + GTNL 守卫，避免影响 GTNL 子类机器（GTNL 走原生）。
     *         使用方法描述符区分 getStoredInputsForColor(ItemStack) 相关重载。
     */
    @Inject(
        method = "getStoredFluidsForColor(Ljava/util/Optional;)Ljava/util/ArrayList;",
        at = @At("HEAD"),
        cancellable = true)
    private void gtsr$getStoredFluidsForColorHead(java.util.Optional<java.lang.Byte> color,
        CallbackInfoReturnable<java.util.ArrayList<net.minecraftforge.fluids.FluidStack>> cir) {
        // GTNL 守卫：GTNL 机器走 GT5U 原生行为，跳过 GTSR 逻辑
        if (gtsr$isGTNLMachine()) return;

        ArrayList<net.minecraftforge.fluids.FluidStack> rList = new ArrayList<>();
        // GT++ 原语义：mSteamInputFluids（自定义流体仓，本地罐）
        for (MTEHatchCustomFluidBase tHatch : GTUtility.validMTEList(mSteamInputFluids)) {
            byte hatchColor = tHatch.getBaseMetaTileEntity()
                .getColorization();
            if (color.isPresent() && hatchColor != -1 && hatchColor != color.get()) continue;
            if (tHatch.getFillableStack() != null) {
                rList.add(tHatch.getFillableStack());
            }
        }
        // 对齐 GT5U 原生 mInputHatches 语义（MTEMultiBlockBase:1852-1892）：
        // MTEHatchInputME → getStoredFluids() 特判并按流体类型去重；其余 → getFillableStack()
        Map<Fluid, net.minecraftforge.fluids.FluidStack> inputsFromME = new HashMap<>();
        MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
        for (MTEHatchInput tHatch : GTUtility.validMTEList(multiBlockSelf.mInputHatches)) {
            byte hatchColor = tHatch.getColor();
            if (color.isPresent() && hatchColor != -1 && hatchColor != color.get()) continue;
            if (tHatch instanceof MTEHatchMultiInput multiInputHatch) {
                for (net.minecraftforge.fluids.FluidStack tFluid : multiInputHatch.getStoredFluid()) {
                    if (tFluid != null) {
                        rList.add(tFluid);
                    }
                }
            } else if (tHatch instanceof MTEHatchInputME meHatch) {
                for (net.minecraftforge.fluids.FluidStack fluidStack : meHatch.getStoredFluids()) {
                    if (fluidStack != null) {
                        // 防止不同 ME 仓中的相同流体被重复识别（与 GT5U 原生行为一致）
                        inputsFromME.put(fluidStack.getFluid(), fluidStack);
                    }
                }
            } else if (tHatch instanceof MTEHatchInputDebug debugHatch) {
                for (net.minecraftforge.fluids.FluidStack fluid : debugHatch.getFluidList()) {
                    if (fluid != null) {
                        net.minecraftforge.fluids.FluidStack stack = fluid.copy();
                        stack.amount = Integer.MAX_VALUE;
                        rList.add(stack);
                    }
                }
            } else {
                net.minecraftforge.fluids.FluidStack fillableStack = tHatch.getFillableStack();
                if (fillableStack != null) {
                    rList.add(fillableStack);
                }
            }
        }
        if (!inputsFromME.isEmpty()) {
            rList.addAll(inputsFromME.values());
        }
        cir.setReturnValue(rList);
    }

    // endregion

    // region Steam Stack & Deplete Input (HEAD Inject + GTNL 守卫)

    /**
     * @reason Inject at HEAD to fix superheated steam detection to work across all input hatches.
     *         In GT5U 2.9.0+, the base getAllSteamStacks() uses getStoredFluids() which
     *         only checks for regular steam, missing superheated steam. We override to
     *         iterate mSteamInputFluids directly and detect both steam types.
     * @author GTSR
     *         注：转为 @Inject(HEAD, cancellable=true) + GTNL 守卫，避免影响 GTNL 子类机器（GTNL 走原生）。
     */
    @Inject(method = "getAllSteamStacks()Ljava/util/ArrayList;", at = @At("HEAD"), cancellable = true)
    private void gtsr$getAllSteamStacksHead(
        CallbackInfoReturnable<java.util.ArrayList<net.minecraftforge.fluids.FluidStack>> cir) {
        // GTNL 守卫：GTNL 机器走 GT5U 原生 getAllSteamStacks 行为，跳过 GTSR 逻辑
        if (gtsr$isGTNLMachine()) return;

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
        // v1.10.4：ME 输入仓/普通输入仓（mInputHatches）蒸汽来源支持。
        // 3 参 drain(UNKNOWN,...) 模拟对普通仓走本地罐、对 ME 输入仓走虚拟引用/网络提取。
        // v1.10.6：统一走 GTSRHatchFluidAccess，探测量 cap 到 1M（原 MAX_VALUE 会把 ME 网络
        // 全量上报为"仓内蒸汽"，仅用于判空/显示）。
        MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
        int probeAmount = 1_000_000;
        for (MTEHatchInput hatch : GTUtility.validMTEList(multiBlockSelf.mInputHatches)) {
            net.minecraftforge.fluids.FluidStack result = GTSRHatchFluidAccess
                .probeFluidAmount(hatch, aSteam.getFluid(), probeAmount);
            if (result != null && result.amount > 0 && result.isFluidEqual(aSteam)) {
                aFluids.add(result);
            }
            if (aSuperheatedSteam != null) {
                net.minecraftforge.fluids.FluidStack result2 = GTSRHatchFluidAccess
                    .probeFluidAmount(hatch, aSuperheatedSteam.getFluid(), probeAmount);
                if (result2 != null && result2.amount > 0 && result2.isFluidEqual(aSuperheatedSteam)) {
                    aFluids.add(result2);
                }
            }
        }
        cir.setReturnValue(aFluids);
    }

    /**
     * @reason Inject at HEAD to support both regular steam and superheated steam input hatches.
     *         Superheated steam is consumed at the requested amount (acts as 4x dense steam).
     * @author GTSR
     *         注：转为 @Inject(HEAD, cancellable=true) + GTNL 守卫，避免影响 GTNL 子类机器（GTNL 走原生）。
     *         使用方法描述符避免与 depleteInput(ItemStack) 重载歧义。
     */
    @Inject(method = "depleteInput(Lnet/minecraftforge/fluids/FluidStack;)Z", at = @At("HEAD"), cancellable = true)
    private void gtsr$depleteInputHead(net.minecraftforge.fluids.FluidStack aLiquid,
        CallbackInfoReturnable<Boolean> cir) {
        // GTNL 守卫：GTNL 机器走 GT5U 原生 depleteInput(FluidStack) 行为，跳过 GTSR 逻辑
        if (gtsr$isGTNLMachine()) return;

        if (aLiquid == null) {
            cir.setReturnValue(false);
            return;
        }
        boolean isSteamRequest = aLiquid.isFluidEqual(Materials.Steam.getGas(1));
        for (MTEHatchCustomFluidBase tHatch : mSteamInputFluids) {
            net.minecraftforge.fluids.FluidStack tLiquid = tHatch.getFluid();
            if (tLiquid != null && tLiquid.isFluidEqual(aLiquid)) {
                tLiquid = tHatch.drain(aLiquid.amount, false);
                if (tLiquid != null && tLiquid.amount >= aLiquid.amount) {
                    tLiquid = tHatch.drain(aLiquid.amount, true);
                    cir.setReturnValue(tLiquid != null && tLiquid.amount >= aLiquid.amount);
                    return;
                }
            }
            if (isSteamRequest && tLiquid != null
                && "ic2superheatedsteam".equals(
                    tLiquid.getFluid()
                        .getName())) {
                tLiquid = tHatch.drain(aLiquid.amount, false);
                if (tLiquid != null && tLiquid.amount >= aLiquid.amount) {
                    tLiquid = tHatch.drain(aLiquid.amount, true);
                    cir.setReturnValue(tLiquid != null && tLiquid.amount >= aLiquid.amount);
                    return;
                }
            }
        }
        // v1.10.4：ME 输入仓/普通输入仓（mInputHatches）蒸汽来源支持。
        // 逐仓 3 参 drain(UNKNOWN,...) 模拟→实扣（传副本：ME 输入仓实现会改写请求对象）。
        // ME 输入仓在配方窗口内走虚拟引用、窗口外走网络提取（需仓槽配置对应流体）。
        // v1.10.6：统一走 GTSRHatchFluidAccess（先探测完整性，满足才实扣——与旧语义一致，
        // 不足时不部分扣减）。蒸汽请求时补超热直接扣减分支（与本地罐路径语义对齐，
        // 支持纯 ME 超热供汽：仓槽仅配置 ic2superheatedsteam 时普通蒸汽探测会失败）。
        MTEMultiBlockBase multiBlockSelf = (MTEMultiBlockBase) (Object) this;
        net.minecraftforge.fluids.FluidStack aSuperheatedSteam = null;
        if (isSteamRequest) {
            aSuperheatedSteam = net.minecraftforge.fluids.FluidRegistry.getFluidStack("ic2superheatedsteam", 1);
        }
        for (MTEHatchInput hatch : GTUtility.validMTEList(multiBlockSelf.mInputHatches)) {
            net.minecraftforge.fluids.FluidStack sim = GTSRHatchFluidAccess.probeFluidAmount(hatch, aLiquid);
            if (sim != null && sim.amount >= aLiquid.amount) {
                int drained = GTSRHatchFluidAccess.drainFluidExact(hatch, aLiquid);
                cir.setReturnValue(drained >= aLiquid.amount);
                return;
            }
            if (aSuperheatedSteam != null) {
                net.minecraftforge.fluids.FluidStack simSuper = GTSRHatchFluidAccess
                    .probeFluidAmount(hatch, aSuperheatedSteam);
                if (simSuper != null && simSuper.amount >= aLiquid.amount) {
                    int drained = GTSRHatchFluidAccess.drainFluidExact(hatch, aSuperheatedSteam);
                    cir.setReturnValue(drained >= aLiquid.amount);
                    return;
                }
            }
        }
        cir.setReturnValue(false);
    }

    // endregion
}
