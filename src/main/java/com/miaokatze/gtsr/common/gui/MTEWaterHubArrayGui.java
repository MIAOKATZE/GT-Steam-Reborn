package com.miaokatze.gtsr.common.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTEWaterHubArrayGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTEWaterHubArray hubArray;

    private IntSyncValue mSetTierSync;
    private IntSyncValue mMaxProgresstimeSync;
    private IntSyncValue mStackCountSync;
    private IntSyncValue mHubUnitCountSync;
    private IntSyncValue mReinforcedHubUnitCountSync;
    private LongSyncValue mWaterStoredSync;

    public MTEWaterHubArrayGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.hubArray = (MTEWaterHubArray) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        mSetTierSync = new IntSyncValue(() -> hubArray.mSetTier, val -> hubArray.mSetTier = val);
        mMaxProgresstimeSync = new IntSyncValue(
            () -> hubArray.mMaxProgresstime,
            val -> hubArray.mMaxProgresstime = val);
        mStackCountSync = new IntSyncValue(() -> hubArray.mStackCount, val -> hubArray.mStackCount = val);
        mHubUnitCountSync = new IntSyncValue(() -> hubArray.mHubUnitCount, val -> hubArray.mHubUnitCount = val);
        mReinforcedHubUnitCountSync = new IntSyncValue(
            () -> hubArray.mReinforcedHubUnitCount,
            val -> hubArray.mReinforcedHubUnitCount = val);
        mWaterStoredSync = new LongSyncValue(() -> hubArray.mWaterStored, val -> hubArray.mWaterStored = val);
        syncManager.syncValue("hubSetTier", mSetTierSync);
        syncManager.syncValue("hubMaxProgresstime", mMaxProgresstimeSync);
        syncManager.syncValue("hubStackCount", mStackCountSync);
        syncManager.syncValue("hubHubUnitCount", mHubUnitCountSync);
        syncManager.syncValue("hubReinforcedHubUnitCount", mReinforcedHubUnitCountSync);
        syncManager.syncValue("hubWaterStored", mWaterStoredSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return super.createTerminalTextWidget(syncManager, parent).child(IKey.dynamic(() -> {
            String tierText = mSetTierSync.getValue() == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText
                + EnumChatFormatting.RESET;
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth())
            .child(IKey.dynamic(() -> {
                ItemStack chip = hubArray.getControllerSlot();
                String chipText;
                if (chip != null && GTSRItemList.HubSingularityChip.isStackEqual(chip, true, true)) {
                    chipText = EnumChatFormatting.GREEN
                        + StatCollector.translateToLocal("gtsr.gui.chip.singularity_installed");
                } else {
                    chipText = EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.chip.none");
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.chip")
                    + " "
                    + chipText
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(IKey.dynamic(() -> {
                String status = mMaxProgresstimeSync.getValue() > 0
                    ? EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.gui.status.running")
                    : EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.status.idle");
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                    + " "
                    + status
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.water_hub.storage_units")
                        + " "
                        + EnumChatFormatting.GOLD
                        + (mHubUnitCountSync.getValue() + mReinforcedHubUnitCountSync.getValue())
                        + "/"
                        + (9 * mStackCountSync.getValue())
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.water_hub.water_buffer")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + NumberFormatUtil.formatNumber(mWaterStoredSync.getValue())
                        + " L"
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.water_hub.total_capacity")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + NumberFormatUtil.formatNumber(hubArray.getTotalCapacity())
                        + " L"
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth());
    }
}
