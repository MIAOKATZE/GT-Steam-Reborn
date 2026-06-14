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
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTESteamHubArrayGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTESteamHubArray hubArray;

    private IntSyncValue mSetTierSync;
    private IntSyncValue mMaxProgresstimeSync;
    private IntSyncValue mStackCountSync;
    private IntSyncValue mPressureUnitCountSync;
    private IntSyncValue mReinforcedUnitCountSync;
    private IntSyncValue mOverpressureUnitCountSync;
    private LongSyncValue mSteamStoredSync;

    public MTESteamHubArrayGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.hubArray = (MTESteamHubArray) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        mSetTierSync = new IntSyncValue(() -> hubArray.mSetTier, val -> hubArray.mSetTier = val);
        mMaxProgresstimeSync = new IntSyncValue(
            () -> hubArray.mMaxProgresstime,
            val -> hubArray.mMaxProgresstime = val);
        mStackCountSync = new IntSyncValue(() -> hubArray.mStackCount, val -> hubArray.mStackCount = val);
        mPressureUnitCountSync = new IntSyncValue(
            () -> hubArray.mPressureUnitCount,
            val -> hubArray.mPressureUnitCount = val);
        mReinforcedUnitCountSync = new IntSyncValue(
            () -> hubArray.mReinforcedUnitCount,
            val -> hubArray.mReinforcedUnitCount = val);
        mOverpressureUnitCountSync = new IntSyncValue(
            () -> hubArray.mOverpressureUnitCount,
            val -> hubArray.mOverpressureUnitCount = val);
        mSteamStoredSync = new LongSyncValue(() -> hubArray.mSteamStored, val -> hubArray.mSteamStored = val);
        syncManager.syncValue("hubSetTier", mSetTierSync);
        syncManager.syncValue("hubMaxProgresstime", mMaxProgresstimeSync);
        syncManager.syncValue("hubStackCount", mStackCountSync);
        syncManager.syncValue("hubPressureUnitCount", mPressureUnitCountSync);
        syncManager.syncValue("hubReinforcedUnitCount", mReinforcedUnitCountSync);
        syncManager.syncValue("hubOverpressureUnitCount", mOverpressureUnitCountSync);
        syncManager.syncValue("hubSteamStored", mSteamStoredSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return super.createTerminalTextWidget(syncManager, parent).child(IKey.dynamic(() -> {
            String tierText;
            if (mSetTierSync.getValue() >= 3) {
                tierText = StatCollector.translateToLocal("gtsr.gui.tier.tungstensteel");
            } else if (mSetTierSync.getValue() == 2) {
                tierText = StatCollector.translateToLocal("gtsr.gui.tier.steel");
            } else {
                tierText = StatCollector.translateToLocal("gtsr.gui.tier.bronze");
            }
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
                if (chip != null && GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(chip, true, true)) {
                    if (mSetTierSync.getValue() >= 3) {
                        chipText = EnumChatFormatting.GREEN
                            + StatCollector.translateToLocal("gtsr.gui.chip.reinforced_installed");
                    } else {
                        chipText = EnumChatFormatting.RED
                            + StatCollector.translateToLocal("gtsr.gui.chip.need_higher_tier");
                    }
                } else if (chip != null && GTSRItemList.HubSingularityChip.isStackEqual(chip, true, true)) {
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
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_hub.storage_units")
                        + " "
                        + EnumChatFormatting.GOLD
                        + (mPressureUnitCountSync.getValue() + mReinforcedUnitCountSync.getValue()
                            + mOverpressureUnitCountSync.getValue())
                        + "/"
                        + (25 * mStackCountSync.getValue())
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_hub.steam_buffer")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + NumberFormatUtil.formatNumber(mSteamStoredSync.getValue())
                        + " L"
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.steam_hub.total_capacity")
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
