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
import com.miaokatze.gtsr.common.machine.MTESteamSingularityCompressor;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTESteamSingularityCompressorGui extends MTEMultiBlockBaseGui<MTESteamSingularityCompressor> {

    public MTESteamSingularityCompressorGui(MTESteamSingularityCompressor multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue("gtsr.heat", new DoubleSyncValue(() -> multiblock.mHeat));
        syncManager.syncValue("gtsr.maxProgress", new IntSyncValue(() -> multiblock.mMaxProgresstime));
        syncManager.syncValue("gtsr.denseMode", new IntSyncValue(() -> multiblock.mDenseMode ? 1 : 0));
        syncManager.syncValue("gtsr.denseTicks", new IntSyncValue(() -> multiblock.mDenseTicks));
        syncManager.syncValue("gtsr.tier", new IntSyncValue(() -> multiblock.mTier));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        DoubleSyncValue heatSyncer = syncManager.findSyncHandler("gtsr.heat", DoubleSyncValue.class);
        IntSyncValue maxProgressSyncer = syncManager.findSyncHandler("gtsr.maxProgress", IntSyncValue.class);
        IntSyncValue denseModeSyncer = syncManager.findSyncHandler("gtsr.denseMode", IntSyncValue.class);
        IntSyncValue denseTicksSyncer = syncManager.findSyncHandler("gtsr.denseTicks", IntSyncValue.class);
        IntSyncValue tierSyncer = syncManager.findSyncHandler("gtsr.tier", IntSyncValue.class);

        return super.createTerminalTextWidget(syncManager, parent)
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.singularity_compressor.heat")
                        + EnumChatFormatting.RED
                        + String.format("%.1f%%", heatSyncer.getValue() * 100.0d))
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(IKey.dynamic(() -> {
                String statusKey;
                EnumChatFormatting statusColor;
                if (denseModeSyncer.getValue() > 0) {
                    statusKey = "gtsr.gui.singularity_compressor.status.dense";
                    statusColor = EnumChatFormatting.LIGHT_PURPLE;
                } else if (maxProgressSyncer.getValue() > 0) {
                    statusKey = "gtsr.gui.status.running";
                    statusColor = EnumChatFormatting.AQUA;
                } else if (heatSyncer.getValue() > 0) {
                    statusKey = "gtsr.gui.singularity_compressor.status.accumulating";
                    statusColor = EnumChatFormatting.YELLOW;
                } else {
                    statusKey = "gtsr.gui.status.idle";
                    statusColor = EnumChatFormatting.WHITE;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                    + " "
                    + statusColor
                    + StatCollector.translateToLocal(statusKey);
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.singularity_compressor.tier")
                        + EnumChatFormatting.GOLD
                        + tierSyncer.getValue()
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(IKey.dynamic(() -> {
                String modeKey = denseModeSyncer.getValue() > 0 ? "gtsr.gui.singularity_compressor.mode.dense"
                    : "gtsr.gui.singularity_compressor.mode.accumulate";
                return EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.gui.singularity_compressor.mode")
                    + " "
                    + EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal(modeKey);
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(IKey.dynamic(() -> {
                if (denseModeSyncer.getValue() <= 0) return "";
                return EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.gui.singularity_compressor.dense_time")
                    + " "
                    + EnumChatFormatting.RED
                    + String.format("%ds", denseTicksSyncer.getValue() / 20);
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth());
    }
}
