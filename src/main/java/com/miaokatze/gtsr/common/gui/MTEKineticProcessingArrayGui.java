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
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTEKineticProcessingArrayGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTEKineticProcessingArray kineticArray;

    private IntSyncValue mMachineTierSync;
    private IntSyncValue mCasingTierSync;
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
        mCasingTierSync = new IntSyncValue(() -> kineticArray.mCasingTier, val -> kineticArray.mCasingTier = val);
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
        syncManager.syncValue("kineticCasingTier", mCasingTierSync);
        syncManager.syncValue("kineticSteamRate", mSteamRateSync);
        syncManager.syncValue("kineticSteamPerAmp", mSteamPerAmpSync);
        syncManager.syncValue("kineticSteamCost", mRealtimeSteamCostSync);
        syncManager.syncValue("kineticMaxParallel", maxParallelSync);
        syncManager.syncValue("kineticParallelCount", mParallelCountSync);
        syncManager.syncValue("kineticMachineName", mMachineNameSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return super.createTerminalTextWidget(syncManager, parent).child(IKey.dynamic(() -> {
            int machineTier = mMachineTierSync.getValue();
            int casingTier = mCasingTierSync.getValue();
            if (machineTier <= 0) {
                return EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.kinetic_array.no_machine");
            }
            return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.kinetic_array.tier")
                + EnumChatFormatting.GREEN
                + GTValues.VN[machineTier > 0 && machineTier < GTValues.VN.length ? machineTier : 0]
                + EnumChatFormatting.WHITE
                + " ("
                + StatCollector.translateToLocal("gtsr.gui.kinetic_array.voltage_cap")
                + " "
                + EnumChatFormatting.YELLOW
                + GTValues.VN[casingTier > 0 && casingTier < GTValues.VN.length ? casingTier : 0]
                + EnumChatFormatting.WHITE
                + ")";
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth()
            .setEnabledIf(w -> multiblock.mMachine))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.kinetic_array.steam_rate")
                        + EnumChatFormatting.AQUA
                        + String.format("%.2f", mSteamRateSync.getValue()))
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth()
                    .setEnabledIf(w -> multiblock.mMachine))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GOLD
                        + StatCollector.translateToLocal("gtsr.gui.kinetic_array.steam_per_amp")
                        + EnumChatFormatting.YELLOW
                        + NumberFormatUtil.formatNumber(mSteamPerAmpSync.getValue())
                        + " L/t")
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth()
                    .setEnabledIf(w -> multiblock.mMachine))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.kinetic_array.hp_steam")
                        + EnumChatFormatting.RED
                        + NumberFormatUtil.formatNumber(mRealtimeSteamCostSync.getValue())
                        + " L/t")
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth()
                    .setEnabledIf(w -> multiblock.mMachine))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.kinetic_array.parallel")
                        + EnumChatFormatting.LIGHT_PURPLE
                        + mParallelCountSync.getValue()
                        + EnumChatFormatting.WHITE
                        + "/"
                        + EnumChatFormatting.YELLOW
                        + maxParallelSync.getValue())
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth()
                    .setEnabledIf(w -> multiblock.mMachine));
    }
}
