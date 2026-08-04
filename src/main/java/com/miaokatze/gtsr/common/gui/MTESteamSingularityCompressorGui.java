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
        syncManager.syncValue("gtsr.mode", new IntSyncValue(() -> multiblock.mMode));
        syncManager.syncValue("gtsr.fuelTicks", new IntSyncValue(() -> multiblock.mFuelTicks));
        syncManager.syncValue("gtsr.tier", new IntSyncValue(() -> multiblock.mTier));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        DoubleSyncValue heatSyncer = syncManager.findSyncHandler("gtsr.heat", DoubleSyncValue.class);
        IntSyncValue maxProgressSyncer = syncManager.findSyncHandler("gtsr.maxProgress", IntSyncValue.class);
        IntSyncValue modeSyncer = syncManager.findSyncHandler("gtsr.mode", IntSyncValue.class);
        IntSyncValue fuelTicksSyncer = syncManager.findSyncHandler("gtsr.fuelTicks", IntSyncValue.class);
        IntSyncValue tierSyncer = syncManager.findSyncHandler("gtsr.tier", IntSyncValue.class);

        return super.createTerminalTextWidget(syncManager, parent).child(IKey.dynamic(() -> {
            // 仅奇点纠缠模式显示热量；压缩/解压模式隐藏（改为显示奇点维持时间）
            if (modeSyncer.getValue() > 0) return "";
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.singularity_compressor.heat")
                + EnumChatFormatting.RED
                + String.format("%.1f%%", heatSyncer.getValue() * 100.0d);
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth())
            .child(IKey.dynamic(() -> {
                String statusKey;
                EnumChatFormatting statusColor;
                if (modeSyncer.getValue() == 2) {
                    statusKey = "gtsr.gui.singularity_compressor.status.decompress";
                    statusColor = EnumChatFormatting.LIGHT_PURPLE;
                } else if (modeSyncer.getValue() == 1) {
                    statusKey = "gtsr.gui.singularity_compressor.status.compress";
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
                String modeKey;
                int mode = modeSyncer.getValue();
                if (mode == 2) {
                    modeKey = "gtsr.gui.singularity_compressor.mode.decompress";
                } else if (mode == 1) {
                    modeKey = "gtsr.gui.singularity_compressor.mode.compress";
                } else {
                    modeKey = "gtsr.gui.singularity_compressor.mode.accumulate";
                }
                return EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.gui.singularity_compressor.mode")
                    + EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal(modeKey);
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(IKey.dynamic(() -> {
                if (modeSyncer.getValue() <= 0) return "";
                String value = fuelTicksSyncer.getValue() > 0 ? String.format("%ds", fuelTicksSyncer.getValue() / 20)
                    : StatCollector.translateToLocal("gtsr.gui.singularity_compressor.fuel_no_fuel");
                return EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.gui.singularity_compressor.fuel_time")
                    + EnumChatFormatting.RED
                    + value;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth());
    }
}
