package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

import gregtech.common.gui.modularui.multiblock.base.MTESteamMultiBlockBaseGui;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTESingularityDrillingHubGui extends MTESteamMultiBlockBaseGui {

    public MTESingularityDrillingHubGui(MTESteamMultiBlockBase<?> multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub machine = (com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub) multiblock;
        syncManager.syncValue("gtsr.maxProgress", new IntSyncValue(() -> machine.mMaxProgresstime));
        syncManager.syncValue("gtsr.boundNodeCount", new IntSyncValue(() -> machine.mBoundNodeCount));
        syncManager.syncValue("gtsr.steamCost", new IntSyncValue(() -> machine.mSteamCost));
        syncManager.syncValue("gtsr.isSuperheated", new BooleanSyncValue(() -> machine.mIsSuperheated));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        IntSyncValue maxProgressSyncer = syncManager.findSyncHandler("gtsr.maxProgress", IntSyncValue.class);
        IntSyncValue boundNodeCountSyncer = syncManager.findSyncHandler("gtsr.boundNodeCount", IntSyncValue.class);
        IntSyncValue steamCostSyncer = syncManager.findSyncHandler("gtsr.steamCost", IntSyncValue.class);

        return super.createTerminalTextWidget(syncManager, parent).child(IKey.dynamic(() -> {
            String status = maxProgressSyncer.getValue() > 0
                ? EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.gui.status.running")
                : EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.status.idle");
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status") + " " + status;
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.singularity_hub.bound_nodes")
                        + " "
                        + EnumChatFormatting.GOLD
                        + boundNodeCountSyncer.getValue())
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                        + " "
                        + EnumChatFormatting.RED
                        + NumberFormatUtil.formatNumber(steamCostSyncer.getValue())
                        + " L/s")
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                        + " "
                        + EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.steam_type.superheated"))
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth());
    }
}
