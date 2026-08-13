package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.machine.MTEKineticProcessingArray;

import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.util.GTUtility;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTEKineticProcessingArrayGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTEKineticProcessingArray kineticArray;

    private IntSyncValue mMachineTierSync;
    private LongSyncValue mMaxRecipeVoltageSync;
    private LongSyncValue mBoostRemainingSecondsSync;
    private DoubleSyncValue mSteamRateSync;
    private LongSyncValue mSteamPerAmpSync;
    private LongSyncValue mRealtimeSteamCostSync;
    private IntSyncValue maxParallelSync;
    private IntSyncValue mParallelCountSync;
    private StringSyncValue mMachineNameSync;

    public MTEKineticProcessingArrayGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.kineticArray = (MTEKineticProcessingArray) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        mMachineTierSync = new IntSyncValue(() -> kineticArray.mMachineTier, val -> kineticArray.mMachineTier = val);
        mMaxRecipeVoltageSync = new LongSyncValue(
            () -> kineticArray.mMaxRecipeVoltage,
            val -> kineticArray.mMaxRecipeVoltage = val);
        mBoostRemainingSecondsSync = new LongSyncValue(
            () -> kineticArray.mBoostRemainingSeconds,
            val -> kineticArray.mBoostRemainingSeconds = val);
        mSteamRateSync = new DoubleSyncValue(() -> kineticArray.mSteamRate, val -> kineticArray.mSteamRate = val);
        mSteamPerAmpSync = new LongSyncValue(() -> kineticArray.mSteamPerAmp, val -> kineticArray.mSteamPerAmp = val);
        mRealtimeSteamCostSync = new LongSyncValue(
            () -> kineticArray.mRealtimeSteamCost,
            val -> kineticArray.mRealtimeSteamCost = val);
        maxParallelSync = new IntSyncValue(() -> kineticArray.maxParallel, val -> kineticArray.maxParallel = val);
        mParallelCountSync = new IntSyncValue(
            () -> kineticArray.mParallelCount,
            val -> kineticArray.mParallelCount = val);
        mMachineNameSync = new StringSyncValue(() -> kineticArray.mMachineName, val -> kineticArray.mMachineName = val);
        syncManager.syncValue("kineticMachineTier", mMachineTierSync);
        syncManager.syncValue("kineticMaxRecipeVoltage", mMaxRecipeVoltageSync);
        syncManager.syncValue("kineticBoostRemainingSeconds", mBoostRemainingSecondsSync);
        syncManager.syncValue("kineticSteamRate", mSteamRateSync);
        syncManager.syncValue("kineticSteamPerAmp", mSteamPerAmpSync);
        syncManager.syncValue("kineticSteamCost", mRealtimeSteamCostSync);
        syncManager.syncValue("kineticMaxParallel", maxParallelSync);
        syncManager.syncValue("kineticParallelCount", mParallelCountSync);
        syncManager.syncValue("kineticMachineName", mMachineNameSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        // 蒸汽速率/每安培/高压蒸汽/并行数值行已迁移至 GTSRProgressBar 词条系统；电压与增幅模式为文本行保留
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        list.child(IKey.dynamic(() -> {
            long maxRecipeVoltage = mMaxRecipeVoltageSync.getValue();
            String voltageLabel = EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("gtsr.gui.kinetic_array.max_recipe_voltage")
                + EnumChatFormatting.WHITE;
            if (mMachineTierSync.getValue() <= 0 || maxRecipeVoltage <= 0) {
                return voltageLabel + " NULL";
            }
            return voltageLabel + EnumChatFormatting.YELLOW
                + NumberFormatUtil.formatNumber(maxRecipeVoltage)
                + EnumChatFormatting.WHITE
                + " ("
                + EnumChatFormatting.GREEN
                + getVoltageTierName(maxRecipeVoltage)
                + EnumChatFormatting.WHITE
                + ")";
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth()
            .setEnabledIf(w -> multiblock.mMachine))
            .child(IKey.dynamic(() -> {
                long boostRemainingSeconds = mBoostRemainingSecondsSync.getValue();
                if (boostRemainingSeconds <= 0) {
                    return EnumChatFormatting.GOLD
                        + StatCollector.translateToLocal("gtsr.gui.kinetic_array.boost_mode_disabled");
                }
                return EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                    "gtsr.gui.kinetic_array.boost_mode_remaining",
                    NumberFormatUtil.formatNumber(boostRemainingSeconds));
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth()
                .setEnabledIf(w -> multiblock.mMachine));
        GTSRProgressBarGuiHelper.appendEntryRows(list, syncManager, kineticArray);
        return list;
    }

    private static String getVoltageTierName(long voltage) {
        int tier = GTUtility.getTierExtended(voltage);
        return GTValues.VN[tier >= 0 && tier < GTValues.VN.length ? tier : 0];
    }
}
