package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

import gregtech.common.gui.modularui.multiblock.base.MTESteamMultiBlockBaseGui;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTESteamFluidDrillGui extends MTESteamMultiBlockBaseGui {

    public MTESteamFluidDrillGui(MTESteamMultiBlockBase<?> multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        com.miaokatze.gtsr.common.machine.MTESteamFluidDrill machine = (com.miaokatze.gtsr.common.machine.MTESteamFluidDrill) multiblock;
        syncManager.syncValue("gtsr.tier", new IntSyncValue(() -> machine.mSetTier));
        syncManager.syncValue("gtsr.efficiency", new IntSyncValue(() -> machine.mEfficiency));
        syncManager.syncValue("gtsr.outputMode", new IntSyncValue(() -> machine.mOutputMode));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        IntSyncValue tierSyncer = syncManager.findSyncHandler("gtsr.tier", IntSyncValue.class);
        IntSyncValue efficiencySyncer = syncManager.findSyncHandler("gtsr.efficiency", IntSyncValue.class);
        IntSyncValue outputModeSyncer = syncManager.findSyncHandler("gtsr.outputMode", IntSyncValue.class);

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
                boolean isSuperheated = ((com.miaokatze.gtsr.common.machine.MTESteamFluidDrill) multiblock)
                    .hasSuperheatedSteamInHatch();
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                    + EnumChatFormatting.YELLOW
                    + (isSuperheated ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
                        : StatCollector.translateToLocal("gtsr.gui.steam_type.normal"));
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(IKey.dynamic(() -> {
                com.miaokatze.gtsr.common.machine.MTESteamFluidDrill machine = (com.miaokatze.gtsr.common.machine.MTESteamFluidDrill) multiblock;
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.fluid_drill.output_mode")
                    + EnumChatFormatting.LIGHT_PURPLE
                    + machine.getOutputModeDisplayName();
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.fluid_drill.efficiency")
                        + EnumChatFormatting.GREEN
                        + String.format("%.1f%%", efficiencySyncer.getValue() / 100F))
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(IKey.dynamic(() -> {
                com.miaokatze.gtsr.common.machine.MTESteamFluidDrill machine = (com.miaokatze.gtsr.common.machine.MTESteamFluidDrill) multiblock;
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.fluid_drill.output")
                    + EnumChatFormatting.LIGHT_PURPLE
                    + NumberFormatUtil.formatNumber(machine.calculateFinalWaterOutput())
                    + " L/s";
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth());
    }
}
