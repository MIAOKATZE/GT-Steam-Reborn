package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;

import gregtech.common.gui.modularui.multiblock.base.MTESteamMultiBlockBaseGui;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTEAtmosphericCentrifugeGui extends MTESteamMultiBlockBaseGui {

    public MTEAtmosphericCentrifugeGui(MTESteamMultiBlockBase<?> multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(
            "gtsr.tier",
            new IntSyncValue(() -> ((com.miaokatze.gtsr.common.machine.MTEAtmosphericCentrifuge) multiblock).mSetTier));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        IntSyncValue tierSyncer = syncManager.findSyncHandler("gtsr.tier", IntSyncValue.class);

        return super.createTerminalTextWidget(syncManager, parent).child(IKey.dynamic(() -> {
            String tierText = tierSyncer.getValue() == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText;
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth())
            .child(IKey.dynamic(() -> {
                boolean isSuperheated = ((com.miaokatze.gtsr.common.machine.MTEAtmosphericCentrifuge) multiblock)
                    .hasSuperheatedSteamInHatch();
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                    + EnumChatFormatting.YELLOW
                    + (isSuperheated ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
                        : StatCollector.translateToLocal("gtsr.gui.steam_type.normal"));
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.parallel")
                        + EnumChatFormatting.GOLD
                        + ((com.miaokatze.gtsr.common.machine.MTEAtmosphericCentrifuge) multiblock)
                            .getMaxParallelRecipes())
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(IKey.dynamic(() -> {
                com.miaokatze.gtsr.common.machine.MTEAtmosphericCentrifuge machine = (com.miaokatze.gtsr.common.machine.MTEAtmosphericCentrifuge) multiblock;
                boolean hasChip = machine.hasRareGasChip();
                int tier = tierSyncer.getValue();
                if (hasChip && tier < 2) {
                    return EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.atmospheric_centrifuge.chip")
                        + EnumChatFormatting.RED
                        + StatCollector.translateToLocal("gtsr.gui.atmospheric_centrifuge.need_steel");
                }
                return EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.gui.atmospheric_centrifuge.chip")
                    + (hasChip ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.installed")
                        : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.not_installed"));
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth());
    }
}
