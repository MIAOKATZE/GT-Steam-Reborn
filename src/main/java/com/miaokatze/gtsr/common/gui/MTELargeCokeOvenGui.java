package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.machine.MTELargeCokeOven;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTELargeCokeOvenGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTELargeCokeOven cokeOven;

    private DoubleSyncValue mHeatSync;
    private IntSyncValue mMaxProgresstimeSync;
    private IntSyncValue mProgresstimeSync;
    private IntSyncValue mTierSync;
    private IntSyncValue mOriginalRecipeTimeSync;

    private static final int HEAT_SPEEDUP_PER_PERCENT = 1;
    private static final int MIN_RECIPE_TIME_SECONDS = 10;

    public MTELargeCokeOvenGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.cokeOven = (MTELargeCokeOven) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        mHeatSync = new DoubleSyncValue(() -> cokeOven.mHeat, val -> cokeOven.mHeat = val);
        mMaxProgresstimeSync = new IntSyncValue(
            () -> cokeOven.mMaxProgresstime,
            val -> cokeOven.mMaxProgresstime = val);
        mProgresstimeSync = new IntSyncValue(() -> cokeOven.mProgresstime, val -> cokeOven.mProgresstime = val);
        mTierSync = new IntSyncValue(() -> cokeOven.mTier, val -> cokeOven.mTier = val);
        mOriginalRecipeTimeSync = new IntSyncValue(
            () -> cokeOven.mOriginalRecipeTime,
            val -> cokeOven.mOriginalRecipeTime = val);
        syncManager.syncValue("cokeHeat", mHeatSync);
        syncManager.syncValue("cokeMaxProgresstime", mMaxProgresstimeSync);
        syncManager.syncValue("cokeProgresstime", mProgresstimeSync);
        syncManager.syncValue("cokeTier", mTierSync);
        syncManager.syncValue("cokeOriginalRecipeTime", mOriginalRecipeTimeSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return super.createTerminalTextWidget(syncManager, parent)
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.temperature")
                        + EnumChatFormatting.RED
                        + String.format("%.1f%%", mHeatSync.getValue() * 100.0d)
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(IKey.dynamic(() -> {
                String statusKey;
                EnumChatFormatting statusColor;
                if (mMaxProgresstimeSync.getValue() > 0) {
                    statusKey = "gtsr.gui.status.running";
                    statusColor = EnumChatFormatting.AQUA;
                } else if (mHeatSync.getValue() > 0) {
                    statusKey = "gtsr.gui.coke_oven.status.cooling";
                    statusColor = EnumChatFormatting.BLUE;
                } else {
                    statusKey = "gtsr.gui.status.idle";
                    statusColor = EnumChatFormatting.WHITE;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                    + " "
                    + statusColor
                    + StatCollector.translateToLocal(statusKey)
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(IKey.dynamic(() -> {
                if (mOriginalRecipeTimeSync.getValue() > 0) {
                    int originalSeconds = mOriginalRecipeTimeSync.getValue() / 20;
                    int reducedSeconds = (int) (mHeatSync.getValue() * 100.0d * HEAT_SPEEDUP_PER_PERCENT);
                    int theoreticalSeconds = Math.max(MIN_RECIPE_TIME_SECONDS, originalSeconds - reducedSeconds);
                    return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.recipe_time")
                        + EnumChatFormatting.GOLD
                        + theoreticalSeconds
                        + "s"
                        + EnumChatFormatting.RESET;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.recipe_time")
                    + EnumChatFormatting.WHITE
                    + "-"
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.parallel")
                        + " "
                        + EnumChatFormatting.GOLD
                        + cokeOven.getMaxParallelRecipes()
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth());
    }
}
