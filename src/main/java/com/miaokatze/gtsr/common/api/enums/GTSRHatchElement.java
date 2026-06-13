package com.miaokatze.gtsr.common.api.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
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
 * the adder dispatches to {@code MTESteamMultiBlockBase::addToMachineList} (which the GTSR Mixin
 * overwrites to handle all hatch types correctly, including dual registration for steam buses).
 * For other {@code MTEMultiBlockBase} machines, the adder falls back to the standard
 * {@code addInputBusToMachineList}/{@code addOutputBusToMachineList} methods.
 */
public enum GTSRHatchElement implements IHatchElement<MTEMultiBlockBase> {

    SteamInputBus("GTSR.HatchElement.SteamInputBus", (t, te, idx) -> {
        if (t instanceof MTESteamMultiBlockBase<?>steamBase) {
            return steamBase.addToMachineList(te, idx);
        }
        return t.addInputBusToMachineList(te, idx);
    }, MTEHatchInputBus.class) {

        @Override
        public long count(MTEMultiBlockBase t) {
            return t.mInputBusses.size();
        }
    },

    SteamOutputBus("GTSR.HatchElement.SteamOutputBus", (t, te, idx) -> {
        if (t instanceof MTESteamMultiBlockBase<?>steamBase) {
            return steamBase.addToMachineList(te, idx);
        }
        return t.addOutputBusToMachineList(te, idx);
    }, MTEHatchOutputBus.class) {

        @Override
        public long count(MTEMultiBlockBase t) {
            return t.mOutputBusses.size();
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
