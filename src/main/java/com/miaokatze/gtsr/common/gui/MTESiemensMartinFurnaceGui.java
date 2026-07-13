package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
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
    private BooleanSyncValue mAirSupplyOKSync;

    // 蒸汽消耗常量（与机器类一致，单位 L/s）
    private static final int SUPERHEATED_STEAM_COST = 300;
    private static final int SUPERHEATED_STEAM_COST_OVERHEAT = 3_000;
    private static final int SUPERHEATED_STEAM_COST_MAX = 1_500;
    private static final double MAX_OVERHEAT = 2.0d;
    private static final double MAX_RECIPE_TIME_REDUCTION = 0.5d;
    private static final double RECIPE_TIME_REDUCTION_PER_PERCENT = 0.005d;
    // 并行：100%炉温 = 64，200%炉温 = 128
    private static final int BASE_PARALLEL = 64;
    private static final int MAX_PARALLEL = 128;
    // 空气消耗
    private static final int AIR_COST_PER_SECOND = 1_000;

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
        mAirSupplyOKSync = new BooleanSyncValue(() -> furnace.mAirSupplyOK, val -> furnace.mAirSupplyOK = val);
        syncManager.syncValue("siemensTemperature", mFurnaceTemperatureSync);
        syncManager.syncValue("siemensMaxProgresstime", mMaxProgresstimeSync);
        syncManager.syncValue("siemensAirSupplyOK", mAirSupplyOKSync);
    }

    /**
     * 计算当前并行数（与机器类逻辑一致）。
     */
    private int getCurrentParallel() {
        double temp = mFurnaceTemperatureSync.getValue();
        if (temp < 1.0d) return 0;
        if (temp <= 1.0d) return BASE_PARALLEL;
        double tempRatio = Math.min(1.0d, (temp - 1.0d) / (MAX_OVERHEAT - 1.0d));
        return (int) (BASE_PARALLEL + (MAX_PARALLEL - BASE_PARALLEL) * tempRatio);
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
            // 并行数：动态显示（100%炉温64，200%炉温128）
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.parallel")
                        + " "
                        + EnumChatFormatting.GOLD
                        + getCurrentParallel()
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            // 空气状态：运行配方时显示消耗，预热时显示空闲，不足时红色警告
            .child(IKey.dynamic(() -> {
                if (mMaxProgresstimeSync.getValue() > 0) {
                    boolean airOK = mAirSupplyOKSync.getValue();
                    String airKey = airOK ? "gtsr.gui.siemens_martin.air_ok" : "gtsr.gui.siemens_martin.air_low";
                    EnumChatFormatting airColor = airOK ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
                    return EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.siemens_martin.air_status")
                        + airColor
                        + StatCollector.translateToLocal(airKey)
                        + EnumChatFormatting.GRAY
                        + " ("
                        + NumberFormatUtil.formatNumber(AIR_COST_PER_SECOND)
                        + " L/s)"
                        + EnumChatFormatting.RESET;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.siemens_martin.air_status")
                    + EnumChatFormatting.WHITE
                    + StatCollector.translateToLocal("gtsr.gui.siemens_martin.air_preheat_idle")
                    + EnumChatFormatting.RESET;
            })
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
