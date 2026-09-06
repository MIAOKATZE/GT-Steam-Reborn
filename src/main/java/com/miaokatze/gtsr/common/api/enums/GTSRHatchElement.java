package com.miaokatze.gtsr.common.api.enums;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.util.StatCollector;

import com.google.common.collect.ImmutableList;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
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
 * <p>
 * O2-B03①：GTSR machine 具体仓类（冷却仓两型/耐压蒸汽输入仓）不再由本枚举直接引用——
 * api 层零 machine import，类对象经 {@link #registerMachineHatchClasses} 由机器层 Init 期灌入
 * （晚绑定，语义与 GT5U mteClasses() 惰性取用兼容；注册须早于首个结构检测/NEI 查询，
 * MachineLoader.initMachines() 首行满足）。未注册即被取用时以 NPE 快速失败而非静默错检。
 */
public enum GTSRHatchElement implements IHatchElement<MTEMultiBlockBase> {

    SteamInputBus("GTSR.HatchElement.SteamInputBus", (t, te, idx) -> {
        // 对 MTESteamMultiBlockBase 也走 addInputBusToMachineList，而非 mixin 改造后的 addToMachineList。
        // mixin 的 addToMachineList 不处理标准 MTEHatchInputBus，会返回 false 导致结构检测失败。
        return t.addInputBusToMachineList(te, idx);
    }, () -> ImmutableList.of(MTEHatchInputBus.class)) {

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
    }, () -> ImmutableList.of(MTEHatchOutputBus.class)) {

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
    }, () -> ImmutableList.of(MTEHatchInput.class)) {

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
    }, () -> ImmutableList.of(MTEHatchOutput.class)) {

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
    }, () -> ImmutableList.of(steamCoolingHatchClass())) {

        @Override
        public long count(MTEMultiBlockBase t) {
            return 0;
        }

        @Override
        public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
            return ImmutableList.of(STEAM_COOLING_HATCH_CLASS, PRESSURE_STEAM_COOLING_HATCH_CLASS);
        }
    },

    PressureSteamCoolingHatch("GTSR.HatchElement.PressureSteamCoolingHatch", (t, te, idx) -> {
        if (t instanceof MTESteamMultiBlockBase<?>steamBase) {
            return steamBase.addToMachineList(te, idx);
        }
        return false;
    }, () -> ImmutableList.of(pressureSteamCoolingHatchClass())) {

        @Override
        public long count(MTEMultiBlockBase t) {
            return 0;
        }

        @Override
        public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
            return ImmutableList.of(PRESSURE_STEAM_COOLING_HATCH_CLASS);
        }
    },

    PressureSteamInputHatch("GTSR.HatchElement.PressureSteamInputHatch", (t, te, idx) -> {
        if (t instanceof MTESteamMultiBlockBase<?>steamBase) {
            return steamBase.addToMachineList(te, idx);
        }
        return false;
    }, () -> ImmutableList.of(pressureSteamInputHatchClass(), MTEHatchInput.class)) {

        @Override
        public long count(MTEMultiBlockBase t) {
            return 0;
        }

        @Override
        public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
            // v1.10.4：开放 MTEHatchInput（含 ME 输入仓）作为蒸汽输入来源，NEI 同步显示
            return ImmutableList.of(PRESSURE_STEAM_INPUT_HATCH_CLASS, MTEHatchInput.class);
        }
    };

    // O2-B03①：machine 具体仓类静态注册表（Init 期灌入，晚绑定取用）
    private static Class<? extends IMetaTileEntity> STEAM_COOLING_HATCH_CLASS;
    private static Class<? extends IMetaTileEntity> PRESSURE_STEAM_COOLING_HATCH_CLASS;
    private static Class<? extends IMetaTileEntity> PRESSURE_STEAM_INPUT_HATCH_CLASS;

    /**
     * 机器层 Init 期灌入具体仓类（MachineLoader.initMachines() 首行调用；
     * 须早于首个结构检测与 NEI 仓类查询）。
     */
    public static void registerMachineHatchClasses(Class<? extends IMetaTileEntity> steamCoolingHatchClass,
        Class<? extends IMetaTileEntity> pressureSteamCoolingHatchClass,
        Class<? extends IMetaTileEntity> pressureSteamInputHatchClass) {
        GTSRHatchElement.STEAM_COOLING_HATCH_CLASS = steamCoolingHatchClass;
        GTSRHatchElement.PRESSURE_STEAM_COOLING_HATCH_CLASS = pressureSteamCoolingHatchClass;
        GTSRHatchElement.PRESSURE_STEAM_INPUT_HATCH_CLASS = pressureSteamInputHatchClass;
    }

    // 枚举常量初始化器不得直接读后置声明静态字段（JLS 前向引用限制），经静态取值方法中转
    private static Class<? extends IMetaTileEntity> steamCoolingHatchClass() {
        return STEAM_COOLING_HATCH_CLASS;
    }

    private static Class<? extends IMetaTileEntity> pressureSteamCoolingHatchClass() {
        return PRESSURE_STEAM_COOLING_HATCH_CLASS;
    }

    private static Class<? extends IMetaTileEntity> pressureSteamInputHatchClass() {
        return PRESSURE_STEAM_INPUT_HATCH_CLASS;
    }

    private final String translationKey;
    private final Supplier<List<Class<? extends IMetaTileEntity>>> mteClassesSupplier;
    private final IGTHatchAdder<MTEMultiBlockBase> adder;

    GTSRHatchElement(String translationKey, IGTHatchAdder<MTEMultiBlockBase> adder,
        Supplier<List<Class<? extends IMetaTileEntity>>> mteClassesSupplier) {
        this.translationKey = translationKey;
        this.mteClassesSupplier = mteClassesSupplier;
        this.adder = adder;
    }

    @Override
    public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
        return mteClassesSupplier.get();
    }

    @Override
    public IGTHatchAdder<? super MTEMultiBlockBase> adder() {
        return adder;
    }

    @Override
    public String getDisplayName() {
        // [GT-compat] beta 兼容层（beta1/beta2/beta3）：GTUtility.translate 于 beta-3 移除，改用 vanilla StatCollector（三版本通用）
        return StatCollector.translateToLocal(translationKey);
    }

    @Override
    public String getDescriptionLangKey() {
        return translationKey;
    }
}
