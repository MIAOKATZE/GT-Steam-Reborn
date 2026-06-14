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

public class MTECrustSteamBorerGui extends MTESteamMultiBlockBaseGui {

    public MTECrustSteamBorerGui(MTESteamMultiBlockBase<?> multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        com.miaokatze.gtsr.common.machine.MTECrustSteamBorer machine = (com.miaokatze.gtsr.common.machine.MTECrustSteamBorer) multiblock;
        syncManager.syncValue("gtsr.tier", new IntSyncValue(() -> machine.mSetTier));
        syncManager.syncValue("gtsr.canMine", new BooleanSyncValue(() -> machine.canMineInCurrentDim));
        syncManager.syncValue("gtsr.maxProgress", new IntSyncValue(() -> machine.mMaxProgresstime));
        syncManager.syncValue("gtsr.dimId", new IntSyncValue(() -> machine.mCurrentDimId));
        syncManager.syncValue("gtsr.lastOre", new StringSyncValue(() -> machine.mLastOreName));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        IntSyncValue tierSyncer = syncManager.findSyncHandler("gtsr.tier", IntSyncValue.class);
        BooleanSyncValue canMineSyncer = syncManager.findSyncHandler("gtsr.canMine", BooleanSyncValue.class);
        IntSyncValue maxProgressSyncer = syncManager.findSyncHandler("gtsr.maxProgress", IntSyncValue.class);
        IntSyncValue dimIdSyncer = syncManager.findSyncHandler("gtsr.dimId", IntSyncValue.class);
        StringSyncValue lastOreSyncer = syncManager.findSyncHandler("gtsr.lastOre", StringSyncValue.class);

        return super.createTerminalTextWidget(syncManager, parent).child(IKey.dynamic(() -> {
            int tier = tierSyncer.getValue();
            String tierText = tier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                : tier == 1 ? StatCollector.translateToLocal("gtsr.gui.tier.bronze") : "None";
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText;
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth())
            .child(IKey.dynamic(() -> {
                String statusText;
                if (!canMineSyncer.getValue()) {
                    statusText = EnumChatFormatting.RED
                        + StatCollector.translateToLocal("gtsr.gui.crust_borer.invalid_dim");
                } else if (maxProgressSyncer.getValue() <= 0) {
                    statusText = EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.crust_borer.no_steam");
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
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.dimension")
                        + (canMineSyncer.getValue() ? EnumChatFormatting.GREEN + String.valueOf(dimIdSyncer.getValue())
                            : EnumChatFormatting.RED
                                + StatCollector.translateToLocal("gtsr.gui.crust_borer.invalid_dim")))
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(IKey.dynamic(() -> {
                String oreName = lastOreSyncer.getValue();
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.mining")
                    + (oreName != null && !oreName.isEmpty() ? EnumChatFormatting.GREEN + oreName
                        : EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.none"));
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.steam_cost")
                        + EnumChatFormatting.RED
                        + NumberFormatUtil
                            .formatNumber(com.miaokatze.gtsr.common.machine.MTECrustSteamBorer.STEAM_PER_SECOND)
                        + " L/s")
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.work_cycle")
                        + EnumChatFormatting.YELLOW
                        + (com.miaokatze.gtsr.common.machine.MTECrustSteamBorer.WORK_TIME_TICKS / 20)
                        + "s")
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth());
    }
}
