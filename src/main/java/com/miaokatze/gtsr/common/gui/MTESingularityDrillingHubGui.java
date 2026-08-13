package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.api.progress.IGTSRProgressProvider;

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
        syncManager.syncValue("gtsr.isSuperheated", new BooleanSyncValue(() -> machine.mIsSuperheated));
        syncManager.syncValue("gtsr.isActivelyRunning", new BooleanSyncValue(() -> machine.mIsActivelyRunning));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        BooleanSyncValue isActivelyRunningSyncer = syncManager
            .findSyncHandler("gtsr.isActivelyRunning", BooleanSyncValue.class);

        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        list.child(IKey.dynamic(() -> {
            String status = Boolean.TRUE.equals(isActivelyRunningSyncer.getValue())
                ? EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.gui.status.running")
                : EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.status.idle");
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status") + " " + status;
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth());
        GTSRProgressBarGuiHelper.appendEntryRows(list, syncManager, (IGTSRProgressProvider) multiblock);
        list.child(
            IKey.dynamic(
                () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                    + " "
                    + EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.gui.steam_type.superheated"))
                .asWidget()
                .marginBottom(2)
                .fullWidth());
        return list;
    }
}
