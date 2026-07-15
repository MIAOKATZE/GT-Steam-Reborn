package com.miaokatze.gtsr.common.api.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusInput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusOutput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

/**
 * Custom HatchElement variants without GT5U's steam bus blacklist.
 * <p>
 * In GT5U 2.9.0+, {@code HatchElement.InputBus} and {@code HatchElement.OutputBus} added
 * {@code mteBlacklist()} that excludes {@code MTEHatchSteamBusInput} and
 * {@code MTEHatchSteamBusOutput} respectively. This prevents steam buses from being
 * recognized by steam multiblock machines that use {@code atLeast(InputBus)} or
 * {@code atLeast(OutputBus)}.
 * <p>
 * This enum provides blacklist-free alternatives. For {@code MTESteamMultiBlockBase} machines,
 * the adder dispatches to {@code addInputBusToMachineList}/{@code addOutputBusToMachineList}
 * (standard bus registration), NOT to the mixin-overridden {@code addToMachineList}.
 * <p>
 * 原因：GTSR 的 MTESteamMultiBaseMixin 改造了 addToMachineList，但该覆写只处理蒸汽专用仓类型
 * （冷却仓、蒸汽流体输入仓、蒸汽总线、MTEHatchInput），对标准 MTEHatchInputBus/MTEHatchOutputBus
 * 返回 false，导致结构检测失败。改用父类 MTEMultiBlockBase 的 addInputBusToMachineList/
 * addOutputBusToMachineList 可将标准总线正确注册到 mInputBusses/mOutputBusses。
 * 蒸汽总线通过 MTESteamMultiBlockBase 原生的 addSteamBusInput/addSteamBusOutput 或 GTSR Mixin
 * 的 addToMachineList 覆写单独处理。
 * <p>
 * For other {@code MTEMultiBlockBase} machines, the adder falls back to the standard
 * {@code addInputBusToMachineList}/{@code addOutputBusToMachineList} methods.
 */
public enum GTSRHatchElement implements IHatchElement<MTEMultiBlockBase> {

    SteamInputBus("GTSR.HatchElement.SteamInputBus", (t, te, idx) -> {
        // 对 MTESteamMultiBlockBase 也走 addInputBusToMachineList，而非 mixin 改造后的 addToMachineList。
        // mixin 的 addToMachineList 不处理标准 MTEHatchInputBus，会返回 false 导致结构检测失败。
        return t.addInputBusToMachineList(te, idx);
    }, MTEHatchInputBus.class) {

        @Override
        public long count(MTEMultiBlockBase t) {
            return t.mInputBusses.size();
        }

        @Override
        public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
            return ImmutableList.of(MTEHatchSteamBusInput.class);
        }
    },

    SteamOutputBus("GTSR.HatchElement.SteamOutputBus", (t, te, idx) -> {
        // 同上，对 MTESteamMultiBlockBase 走 addOutputBusToMachineList 注册到 mOutputBusses。
        return t.addOutputBusToMachineList(te, idx);
    }, MTEHatchOutputBus.class) {

        @Override
        public long count(MTEMultiBlockBase t) {
            return t.mOutputBusses.size();
        }

        @Override
        public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
            return ImmutableList.of(MTEHatchSteamBusOutput.class);
        }
    },

    SteamInputHatch("GTSR.HatchElement.SteamInputHatch", (t, te, idx) -> {
        if (t instanceof MTESteamMultiBlockBase<?>steamBase) {
            return steamBase.addToMachineList(te, idx);
        }
        return t.addInputHatchToMachineList(te, idx);
    }, MTEHatchInput.class) {

        @Override
        public long count(MTEMultiBlockBase t) {
            return t.mInputHatches.size();
        }
    },

    SteamOutputHatch("GTSR.HatchElement.SteamOutputHatch", (t, te, idx) -> {
        if (t instanceof MTESteamMultiBlockBase<?>steamBase) {
            return steamBase.addToMachineList(te, idx);
        }
        return t.addOutputHatchToMachineList(te, idx);
    }, MTEHatchOutput.class) {

        @Override
        public long count(MTEMultiBlockBase t) {
            return t.mOutputHatches.size();
        }
    },

    SteamCoolingHatch("GTSR.HatchElement.SteamCoolingHatch", (t, te, idx) -> {
        if (t instanceof MTESteamMultiBlockBase<?>steamBase) {
            return steamBase.addToMachineList(te, idx);
        }
        return false;
    }, MTESteamCoolingHatch.class) {

        @Override
        public long count(MTEMultiBlockBase t) {
            return 0;
        }

        @Override
        public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
            return ImmutableList.of(MTESteamCoolingHatch.class, MTEPressureSteamCoolingHatch.class);
        }
    },

    PressureSteamCoolingHatch("GTSR.HatchElement.PressureSteamCoolingHatch", (t, te, idx) -> {
        if (t instanceof MTESteamMultiBlockBase<?>steamBase) {
            return steamBase.addToMachineList(te, idx);
        }
        return false;
    }, MTEPressureSteamCoolingHatch.class) {

        @Override
        public long count(MTEMultiBlockBase t) {
            return 0;
        }

        @Override
        public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
            return ImmutableList.of(MTEPressureSteamCoolingHatch.class);
        }
    },

    PressureSteamInputHatch("GTSR.HatchElement.PressureSteamInputHatch", (t, te, idx) -> {
        if (t instanceof MTESteamMultiBlockBase<?>steamBase) {
            return steamBase.addToMachineList(te, idx);
        }
        return false;
    }, MTEHatchPressureSteamInput.class) {

        @Override
        public long count(MTEMultiBlockBase t) {
            return 0;
        }

        @Override
        public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
            return ImmutableList.of(MTEHatchPressureSteamInput.class);
        }
    };

    private final String translationKey;
    private final List<Class<? extends IMetaTileEntity>> mteClasses;
    private final IGTHatchAdder<MTEMultiBlockBase> adder;

    @SafeVarargs
    GTSRHatchElement(String translationKey, IGTHatchAdder<MTEMultiBlockBase> adder,
        Class<? extends IMetaTileEntity>... mteClasses) {
        this.translationKey = translationKey;
        this.mteClasses = Collections.unmodifiableList(Arrays.asList(mteClasses));
        this.adder = adder;
    }

    @Override
    public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
        return mteClasses;
    }

    @Override
    public IGTHatchAdder<? super MTEMultiBlockBase> adder() {
        return adder;
    }

    @Override
    public String getDisplayName() {
        return GTUtility.translate(translationKey);
    }

    @Override
    public String getDescriptionLangKey() {
        return translationKey;
    }
}
