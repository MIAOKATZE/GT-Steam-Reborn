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
import com.miaokatze.gtsr.common.machine.MTEAmmoniaPlant;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTEAmmoniaPlantGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTEAmmoniaPlant ammoniaPlant;

    private IntSyncValue mHeatLevelSync;
    private LongSyncValue mRealtimeSteamCostSync;
    private LongSyncValue mRealtimeSteamOutputSync;
    private IntSyncValue mParallelCountSync;
    private IntSyncValue mCatalystTypeSync;
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
        mCatalystTypeSync = new IntSyncValue(ammoniaPlant::getCatalystType, ammoniaPlant::syncCatalystType);
        mMaxProgresstimeSync = new IntSyncValue(
            () -> ammoniaPlant.mMaxProgresstime,
            val -> ammoniaPlant.mMaxProgresstime = val);
        syncManager.syncValue("ammoniaHeatLevel", mHeatLevelSync);
        syncManager.syncValue("ammoniaSteamCost", mRealtimeSteamCostSync);
        syncManager.syncValue("ammoniaSteamOutput", mRealtimeSteamOutputSync);
        syncManager.syncValue("ammoniaParallelCount", mParallelCountSync);
        syncManager.syncValue("ammoniaCatalystType", mCatalystTypeSync);
        syncManager.syncValue("ammoniaMaxProgresstime", mMaxProgresstimeSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        // 热量/蒸汽消耗/高压蒸汽/并行数值行已迁移至 GTSRProgressBar 词条系统；催化剂与状态为文本行保留
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        GTSRProgressBarGuiHelper.appendEntryRows(list, syncManager, ammoniaPlant);
        list.child(IKey.dynamic(() -> {
            int catalystType = mCatalystTypeSync.getValue();
            String catalystName = catalystType > 0
                ? StatCollector.translateToLocal("gtsr.gui.ammonia_plant.catalyst." + catalystType)
                : StatCollector.translateToLocal("gtsr.gui.not_installed");
            EnumChatFormatting catalystColor = catalystType > 0 ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
            return EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.catalyst")
                + " "
                + catalystColor
                + catalystName
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
                    .fullWidth());
        return list;
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
        if (heatLevel <= 0) return EnumChatFormatting.WHITE;
        if (heatLevel < 10000) return EnumChatFormatting.GOLD;
        if (maxProgress > 0) return EnumChatFormatting.GREEN;
        return EnumChatFormatting.YELLOW;
    }
}
