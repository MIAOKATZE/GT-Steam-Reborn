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
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.machine.MTESiemensMartinFurnace;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTESiemensMartinFurnaceGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTESiemensMartinFurnace furnace;

    private DoubleSyncValue mFurnaceTemperatureSync;
    private IntSyncValue mMaxProgresstimeSync;

    private static final int SUPERHEATED_STEAM_COST = 1_200;
    private static final int SUPERHEATED_STEAM_COST_OVERHEAT = 12_000;
    private static final int SUPERHEATED_STEAM_COST_MAX = 6_000;
    private static final double MAX_OVERHEAT = 2.0d;
    private static final double MAX_RECIPE_TIME_REDUCTION = 0.5d;
    private static final double RECIPE_TIME_REDUCTION_PER_PERCENT = 0.005d;
    private static final int MAX_PARALLEL = 64;

    public MTESiemensMartinFurnaceGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.furnace = (MTESiemensMartinFurnace) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        mFurnaceTemperatureSync = new DoubleSyncValue(
            () -> furnace.mFurnaceTemperature,
            val -> furnace.mFurnaceTemperature = val);
        mMaxProgresstimeSync = new IntSyncValue(() -> furnace.mMaxProgresstime, val -> furnace.mMaxProgresstime = val);
        syncManager.syncValue("siemensTemperature", mFurnaceTemperatureSync);
        syncManager.syncValue("siemensMaxProgresstime", mMaxProgresstimeSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return super.createTerminalTextWidget(syncManager, parent)
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.siemens_martin.temperature")
                        + (mFurnaceTemperatureSync.getValue() > 1.0d ? EnumChatFormatting.LIGHT_PURPLE
                            : EnumChatFormatting.RED)
                        + String.format("%.1f%%", mFurnaceTemperatureSync.getValue() * 100.0d)
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
                } else if (mFurnaceTemperatureSync.getValue() > 0 && mFurnaceTemperatureSync.getValue() < 1.0d) {
                    statusKey = "gtsr.gui.siemens_martin.status.heating";
                    statusColor = EnumChatFormatting.YELLOW;
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
                int steamCostLps;
                if (mMaxProgresstimeSync.getValue() > 0) {
                    if (mFurnaceTemperatureSync.getValue() >= MAX_OVERHEAT) {
                        steamCostLps = SUPERHEATED_STEAM_COST_MAX;
                    } else if (mFurnaceTemperatureSync.getValue() >= 1.0d) {
                        steamCostLps = SUPERHEATED_STEAM_COST_OVERHEAT;
                    } else {
                        steamCostLps = SUPERHEATED_STEAM_COST * 20;
                    }
                } else {
                    steamCostLps = SUPERHEATED_STEAM_COST * 20;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.siemens_martin.steam_cost")
                    + EnumChatFormatting.RED
                    + NumberFormatUtil.formatNumber(steamCostLps)
                    + " L/s"
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal(
                        "gtsr.gui.parallel") + " " + EnumChatFormatting.GOLD + MAX_PARALLEL + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(IKey.dynamic(() -> {
                if (mFurnaceTemperatureSync.getValue() > 1.0d) {
                    double overheatPercent = (mFurnaceTemperatureSync.getValue() - 1.0d) * 100.0d;
                    double reduction = Math
                        .min(MAX_RECIPE_TIME_REDUCTION, overheatPercent * RECIPE_TIME_REDUCTION_PER_PERCENT);
                    return EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.siemens_martin.overheat_reduction")
                        + EnumChatFormatting.GOLD
                        + String.format("%.1f%%", reduction * 100.0d)
                        + EnumChatFormatting.RESET;
                }
                return EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.gui.siemens_martin.overheat_reduction")
                    + EnumChatFormatting.WHITE
                    + "0%"
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(IKey.dynamic(() -> {
                String recipeInfo = mMaxProgresstimeSync.getValue() > 0
                    ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.siemens_martin.recipe.active")
                    : EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.siemens_martin.recipe.none");
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(
                    "gtsr.gui.siemens_martin.current_recipe") + " " + recipeInfo + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth());
    }
}
