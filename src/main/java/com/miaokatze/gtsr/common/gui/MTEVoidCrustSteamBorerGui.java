package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

import gregtech.common.gui.modularui.multiblock.base.MTESteamMultiBlockBaseGui;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTEVoidCrustSteamBorerGui extends MTESteamMultiBlockBaseGui {

    public MTEVoidCrustSteamBorerGui(MTESteamMultiBlockBase<?> multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        com.miaokatze.gtsr.common.machine.MTEVoidCrustSteamBorer machine = (com.miaokatze.gtsr.common.machine.MTEVoidCrustSteamBorer) multiblock;
        syncManager.syncValue("gtsr.lastDimAbbr", new StringSyncValue(() -> machine.lastDimAbbr));
        syncManager.syncValue("gtsr.dropMapValid", new BooleanSyncValue(() -> machine.dropMapValid));
        syncManager.syncValue("gtsr.maxProgress", new IntSyncValue(() -> machine.mMaxProgresstime));
        syncManager.syncValue("gtsr.lastOre", new StringSyncValue(() -> machine.mLastOreName));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        StringSyncValue lastDimAbbrSyncer = syncManager.findSyncHandler("gtsr.lastDimAbbr", StringSyncValue.class);
        BooleanSyncValue dropMapValidSyncer = syncManager.findSyncHandler("gtsr.dropMapValid", BooleanSyncValue.class);
        IntSyncValue maxProgressSyncer = syncManager.findSyncHandler("gtsr.maxProgress", IntSyncValue.class);
        StringSyncValue lastOreSyncer = syncManager.findSyncHandler("gtsr.lastOre", StringSyncValue.class);

        return super.createTerminalTextWidget(syncManager, parent)
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                        + EnumChatFormatting.GOLD
                        + StatCollector.translateToLocal("gtsr.gui.tier.steel"))
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(IKey.dynamic(() -> {
                String dimAbbr = lastDimAbbrSyncer.getValue();
                boolean valid = dropMapValidSyncer.getValue();
                String statusText;
                if ("None".equals(dimAbbr)) {
                    statusText = EnumChatFormatting.RED
                        + StatCollector.translateToLocal("gtsr.gui.void_borer.no_dimension");
                } else if (!valid) {
                    statusText = EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.void_borer.no_ores");
                } else if (maxProgressSyncer.getValue() <= 0) {
                    statusText = EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.void_borer.no_steam");
                } else {
                    statusText = EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.status.running");
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status") + statusText;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.void_borer.dimension")
                        + EnumChatFormatting.GREEN
                        + lastDimAbbrSyncer.getValue())
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(IKey.dynamic(() -> {
                String oreName = lastOreSyncer.getValue();
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.void_borer.mining")
                    + (oreName != null && !oreName.isEmpty() ? EnumChatFormatting.GREEN + oreName
                        : EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.none"));
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.void_borer.steam_cost")
                        + EnumChatFormatting.RED
                        + NumberFormatUtil.formatNumber(
                            com.miaokatze.gtsr.common.machine.MTEVoidCrustSteamBorer.VOID_STEAM_PER_SECOND)
                        + " L/s")
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.work_cycle")
                        + EnumChatFormatting.YELLOW
                        + (com.miaokatze.gtsr.common.machine.MTEVoidCrustSteamBorer.VOID_WORK_TIME_TICKS / 20)
                        + "s")
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth());
    }
}
