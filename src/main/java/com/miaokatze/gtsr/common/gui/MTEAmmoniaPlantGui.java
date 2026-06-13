package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.machine.MTEAmmoniaPlant;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTEAmmoniaPlantGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTEAmmoniaPlant ammoniaPlant;

    private IntSyncValue mHeatLevelSync;
    private LongSyncValue mRealtimeSteamCostSync;
    private LongSyncValue mRealtimeSteamOutputSync;
    private IntSyncValue mParallelCountSync;
    private IntSyncValue mMaxProgresstimeSync;

    public MTEAmmoniaPlantGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.ammoniaPlant = (MTEAmmoniaPlant) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        mHeatLevelSync = new IntSyncValue(() -> ammoniaPlant.mHeatLevel, val -> ammoniaPlant.mHeatLevel = val);
        mRealtimeSteamCostSync = new LongSyncValue(
            () -> ammoniaPlant.mRealtimeSteamCost,
            val -> ammoniaPlant.mRealtimeSteamCost = val);
        mRealtimeSteamOutputSync = new LongSyncValue(
            () -> ammoniaPlant.mRealtimeSteamOutput,
            val -> ammoniaPlant.mRealtimeSteamOutput = val);
        mParallelCountSync = new IntSyncValue(
            () -> ammoniaPlant.mParallelCount,
            val -> ammoniaPlant.mParallelCount = val);
        mMaxProgresstimeSync = new IntSyncValue(
            () -> ammoniaPlant.mMaxProgresstime,
            val -> ammoniaPlant.mMaxProgresstime = val);
        syncManager.syncValue("ammoniaHeatLevel", mHeatLevelSync);
        syncManager.syncValue("ammoniaSteamCost", mRealtimeSteamCostSync);
        syncManager.syncValue("ammoniaSteamOutput", mRealtimeSteamOutputSync);
        syncManager.syncValue("ammoniaParallelCount", mParallelCountSync);
        syncManager.syncValue("ammoniaMaxProgresstime", mMaxProgresstimeSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return super.createTerminalTextWidget(syncManager, parent).child(IKey.dynamic(() -> {
            double heatPct = mHeatLevelSync.getValue() / 100.0;
            EnumChatFormatting heatColor;
            if (heatPct <= 0) heatColor = EnumChatFormatting.GRAY;
            else if (heatPct < 50) heatColor = EnumChatFormatting.RED;
            else if (heatPct < 80) heatColor = EnumChatFormatting.GOLD;
            else if (heatPct < 100) heatColor = EnumChatFormatting.GREEN;
            else heatColor = EnumChatFormatting.YELLOW;
            return EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.heat")
                + " "
                + heatColor
                + String.format("%.1f%%", heatPct)
                + " "
                + EnumChatFormatting.RESET;
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.status")
                        + " "
                        + getStatusColor()
                        + getStatusText()
                        + " "
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.steam")
                        + " "
                        + EnumChatFormatting.AQUA
                        + NumberFormatUtil.formatNumber(mRealtimeSteamCostSync.getValue())
                        + " L/s "
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.hp_steam")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + NumberFormatUtil.formatNumber(mRealtimeSteamOutputSync.getValue())
                        + " L/s "
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.parallel")
                        + " "
                        + EnumChatFormatting.GOLD
                        + mParallelCountSync.getValue()
                        + " "
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth());
    }

    private String getStatusText() {
        int heatLevel = mHeatLevelSync.getValue();
        int maxProgress = mMaxProgresstimeSync.getValue();
        if (heatLevel <= 0) return StatCollector.translateToLocal("gtsr.gui.ammonia_plant.cold");
        if (heatLevel < 10000) return StatCollector.translateToLocal("gtsr.gui.ammonia_plant.preheating");
        if (maxProgress > 0) return StatCollector.translateToLocal("gtsr.gui.ammonia_plant.running");
        return StatCollector.translateToLocal("gtsr.gui.ammonia_plant.standby");
    }

    private EnumChatFormatting getStatusColor() {
        int heatLevel = mHeatLevelSync.getValue();
        int maxProgress = mMaxProgresstimeSync.getValue();
        if (heatLevel <= 0) return EnumChatFormatting.GRAY;
        if (heatLevel < 10000) return EnumChatFormatting.GOLD;
        if (maxProgress > 0) return EnumChatFormatting.GREEN;
        return EnumChatFormatting.YELLOW;
    }
}
