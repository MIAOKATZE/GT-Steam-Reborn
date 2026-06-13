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

import gregtech.common.gui.modularui.multiblock.base.MTESteamMultiBlockBaseGui;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTESteamSingularityCompressorGui extends MTESteamMultiBlockBaseGui {

    public MTESteamSingularityCompressorGui(MTESteamMultiBlockBase<?> multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        com.miaokatze.gtsr.common.machine.MTESteamSingularityCompressor machine = (com.miaokatze.gtsr.common.machine.MTESteamSingularityCompressor) multiblock;
        syncManager.syncValue("gtsr.heat", new DoubleSyncValue(() -> machine.mHeat));
        syncManager.syncValue("gtsr.maxProgress", new IntSyncValue(() -> machine.mMaxProgresstime));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        DoubleSyncValue heatSyncer = syncManager.findSyncHandler("gtsr.heat", DoubleSyncValue.class);
        IntSyncValue maxProgressSyncer = syncManager.findSyncHandler("gtsr.maxProgress", IntSyncValue.class);

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
                if (maxProgressSyncer.getValue() > 0) {
                    statusKey = "gtsr.gui.status.running";
                    statusColor = EnumChatFormatting.AQUA;
                } else if (heatSyncer.getValue() > 0) {
                    statusKey = "gtsr.gui.singularity_compressor.status.accumulating";
                    statusColor = EnumChatFormatting.YELLOW;
                } else {
                    statusKey = "gtsr.gui.status.idle";
                    statusColor = EnumChatFormatting.GRAY;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                    + " "
                    + statusColor
                    + StatCollector.translateToLocal(statusKey);
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(IKey.dynamic(() -> {
                boolean isSuperheated = ((com.miaokatze.gtsr.common.machine.MTESteamSingularityCompressor) multiblock)
                    .hasSuperheatedSteamInHatch();
                String steamType = isSuperheated ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
                    : StatCollector.translateToLocal("gtsr.gui.steam_type.normal");
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                    + " "
                    + EnumChatFormatting.YELLOW
                    + steamType;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth());
    }
}
