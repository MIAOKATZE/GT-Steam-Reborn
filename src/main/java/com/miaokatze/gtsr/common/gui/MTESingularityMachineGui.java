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
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTESingularityMachineGui<T extends MTESingularityMachineBase> extends MTEMultiBlockBaseGui<T> {

    public MTESingularityMachineGui(T multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue("gtsr.heat", new DoubleSyncValue(() -> multiblock.mHeat));
        syncManager.syncValue("gtsr.maxProgress", new IntSyncValue(() -> multiblock.mMaxProgresstime));
        syncManager.syncValue("gtsr.mode", new IntSyncValue(multiblock::getModeForGui));
        syncManager.syncValue("gtsr.fuelTicks", new IntSyncValue(multiblock::getFuelTicksForGui));
        syncManager.syncValue("gtsr.tier", new IntSyncValue(() -> multiblock.mTier));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        String keyPrefix = multiblock.getGuiKeyPrefix();
        DoubleSyncValue heatSyncer = syncManager.findSyncHandler("gtsr.heat", DoubleSyncValue.class);
        IntSyncValue maxProgressSyncer = syncManager.findSyncHandler("gtsr.maxProgress", IntSyncValue.class);
        IntSyncValue modeSyncer = syncManager.findSyncHandler("gtsr.mode", IntSyncValue.class);
        IntSyncValue fuelTicksSyncer = syncManager.findSyncHandler("gtsr.fuelTicks", IntSyncValue.class);
        IntSyncValue tierSyncer = syncManager.findSyncHandler("gtsr.tier", IntSyncValue.class);

        return super.createTerminalTextWidget(syncManager, parent)
            .childIf(
                !multiblock.isDenseStateManipulator(),
                () -> IKey
                    .dynamic(
                        () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "heat")
                            + EnumChatFormatting.RED
                            + String.format("%.1f%%", heatSyncer.getValue() * 100.0d))
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(IKey.dynamic(() -> {
                String statusKey;
                EnumChatFormatting statusColor;
                if (multiblock.isDenseStateManipulator()) {
                    statusKey = modeSyncer.getValue() == 1 ? "status.decompress" : "status.compress";
                    statusColor = EnumChatFormatting.LIGHT_PURPLE;
                } else if (maxProgressSyncer.getValue() > 0) {
                    statusKey = "status.running";
                    statusColor = EnumChatFormatting.AQUA;
                } else if (heatSyncer.getValue() > 0) {
                    statusKey = "status.accumulating";
                    statusColor = EnumChatFormatting.YELLOW;
                } else {
                    statusKey = "status.idle";
                    statusColor = EnumChatFormatting.WHITE;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "status")
                    + statusColor
                    + StatCollector.translateToLocal(keyPrefix + statusKey);
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .childIf(
                !multiblock.isHideTierInGui(),
                () -> IKey
                    .dynamic(
                        () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "tier")
                            + EnumChatFormatting.GOLD
                            + tierSyncer.getValue()
                            + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .childIf(multiblock.isDenseStateManipulator(), () -> IKey.dynamic(() -> {
                String modeKey = modeSyncer.getValue() == 1 ? "mode.decompress" : "mode.compress";
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "mode")
                    + EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal(keyPrefix + modeKey);
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .childIf(multiblock.isDenseStateManipulator(), () -> IKey.dynamic(() -> {
                String value = fuelTicksSyncer.getValue() > 0 ? String.format("%ds", fuelTicksSyncer.getValue() / 20)
                    : StatCollector.translateToLocal(keyPrefix + "fuel_no_fuel");
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "fuel_time")
                    + EnumChatFormatting.RED
                    + value;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth());
    }
}
