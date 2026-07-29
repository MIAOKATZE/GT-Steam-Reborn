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
import com.miaokatze.gtsr.common.machine.MTEReinforcedBrickBlastFurnace;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 加固砖高炉的自定义 GUI。
 * <p>
 * 使用 ModularUI 2 的 {@code createTerminalTextWidget} 显示炉温、运行状态、并行数与运行速度，
 * 通过 {@code registerSyncValues} 同步炉温与进度时间，保证客户端实时更新。
 * 参考实现：{@link MTELargeCokeOvenGui}、{@link MTESiemensMartinFurnaceGui}。
 */
public class MTEReinforcedBrickBlastFurnaceGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTEReinforcedBrickBlastFurnace furnace;

    private DoubleSyncValue mFurnaceTemperatureSync;
    private IntSyncValue mMaxProgresstimeSync;

    public MTEReinforcedBrickBlastFurnaceGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.furnace = (MTEReinforcedBrickBlastFurnace) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        // 同步炉温（0.0~1.0）与进度时间，客户端 GUI 据此显示
        mFurnaceTemperatureSync = new DoubleSyncValue(
            () -> furnace.mFurnaceTemperature,
            val -> furnace.mFurnaceTemperature = val);
        mMaxProgresstimeSync = new IntSyncValue(() -> furnace.mMaxProgresstime, val -> furnace.mMaxProgresstime = val);
        syncManager.syncValue("rbbfTemperature", mFurnaceTemperatureSync);
        syncManager.syncValue("rbbfMaxProgresstime", mMaxProgresstimeSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return super.createTerminalTextWidget(syncManager, parent)
            // 炉温显示：黄色标签 + 红色百分比
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.reinforced_brick_blast_furnace.temperature")
                        + " "
                        + EnumChatFormatting.RED
                        + String.format("%.1f%%", mFurnaceTemperatureSync.getValue() * 100.0d)
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            // 运行状态显示：运行中(青)/升温中(黄)/待机中(灰)
            .child(IKey.dynamic(() -> {
                String statusKey;
                EnumChatFormatting statusColor;
                if (mMaxProgresstimeSync.getValue() > 0) {
                    statusKey = "gtsr.gui.status.running";
                    statusColor = EnumChatFormatting.AQUA;
                } else if (mFurnaceTemperatureSync.getValue() > 0.0d && mFurnaceTemperatureSync.getValue() < 1.0d) {
                    statusKey = "gtsr.gui.reinforced_brick_blast_furnace.status.heating";
                    statusColor = EnumChatFormatting.YELLOW;
                } else {
                    statusKey = "gtsr.gui.status.idle";
                    statusColor = EnumChatFormatting.GRAY;
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
            // 并行数显示：黄色标签 + 金色数字
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.reinforced_brick_blast_furnace.parallel")
                        + " "
                        + EnumChatFormatting.GOLD
                        + furnace.getMaxParallelRecipes()
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            // 运行速度显示：黄色标签 + 金色倍率（1.00x ~ 1.50x）
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.reinforced_brick_blast_furnace.speed")
                        + " "
                        + EnumChatFormatting.GOLD
                        + String.format("%.2fx", 1.0d + 0.5d * mFurnaceTemperatureSync.getValue())
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth());
    }
}
