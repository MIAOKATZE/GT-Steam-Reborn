package com.miaokatze.gtsr.common.gui;

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
import com.miaokatze.gtsr.common.machine.MTEGearSteamCompressor;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTEGearSteamCompressorGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTEGearSteamCompressor compressor;

    private IntSyncValue mCasingTierSync;
    private LongSyncValue mSteamConsumedLastTickSync;
    private LongSyncValue mSuperheatedOutputLastTickSync;
    private LongSyncValue mWaterOutputLastTickSync;

    public MTEGearSteamCompressorGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.compressor = (MTEGearSteamCompressor) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        mCasingTierSync = new IntSyncValue(() -> compressor.mCasingTier, val -> compressor.mCasingTier = val);
        mSteamConsumedLastTickSync = new LongSyncValue(
            () -> compressor.mSteamConsumedLastTick,
            val -> compressor.mSteamConsumedLastTick = val);
        mSuperheatedOutputLastTickSync = new LongSyncValue(
            () -> compressor.mSuperheatedOutputLastTick,
            val -> compressor.mSuperheatedOutputLastTick = val);
        mWaterOutputLastTickSync = new LongSyncValue(
            () -> compressor.mWaterOutputLastTick,
            val -> compressor.mWaterOutputLastTick = val);
        syncManager.syncValue("compressorCasingTier", mCasingTierSync);
        syncManager.syncValue("compressorSteamConsumed", mSteamConsumedLastTickSync);
        syncManager.syncValue("compressorSHOutput", mSuperheatedOutputLastTickSync);
        syncManager.syncValue("compressorWaterOutput", mWaterOutputLastTickSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return super.createTerminalTextWidget(syncManager, parent)
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.gear_compressor.tier")
                        + EnumChatFormatting.GREEN
                        + (mCasingTierSync.getValue() == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                            : mCasingTierSync.getValue() == 1 ? StatCollector.translateToLocal("gtsr.gui.tier.bronze")
                                : StatCollector.translateToLocal("gtsr.gui.tier.none")))
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth()
                    .setEnabledIf(w -> multiblock.mMachine))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.gear_compressor.steam_in")
                        + EnumChatFormatting.RED
                        + NumberFormatUtil.formatNumber(mSteamConsumedLastTickSync.getValue())
                        + " L/s")
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth()
                    .setEnabledIf(w -> multiblock.mMachine))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GOLD
                        + StatCollector.translateToLocal("gtsr.gui.gear_compressor.sh_steam_out")
                        + EnumChatFormatting.AQUA
                        + NumberFormatUtil.formatNumber(mSuperheatedOutputLastTickSync.getValue())
                        + " L/s")
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth()
                    .setEnabledIf(w -> multiblock.mMachine))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.gear_compressor.water_out")
                        + EnumChatFormatting.BLUE
                        + NumberFormatUtil.formatNumber(mWaterOutputLastTickSync.getValue())
                        + " L/s")
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth()
                    .setEnabledIf(w -> multiblock.mMachine));
    }
}
