package com.miaokatze.gtsr.common.gui;

import java.text.NumberFormat;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.MTELargeGeothermalSteamBoiler;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTELargeGeothermalSteamBoilerGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTELargeGeothermalSteamBoiler boiler;

    private DoubleSyncValue mHeatSync;
    private IntSyncValue mCurrentSteamOutputSync;
    private IntSyncValue mSetTierSync;

    private static final NumberFormat numberFormat = NumberFormat.getNumberInstance();

    static {
        numberFormat.setMinimumFractionDigits(3);
        numberFormat.setMaximumFractionDigits(3);
    }

    public MTELargeGeothermalSteamBoilerGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.boiler = (MTELargeGeothermalSteamBoiler) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        mHeatSync = new DoubleSyncValue(() -> boiler.mHeat, val -> boiler.mHeat = val);
        mCurrentSteamOutputSync = new IntSyncValue(
            () -> boiler.mCurrentSteamOutput,
            val -> boiler.mCurrentSteamOutput = val);
        mSetTierSync = new IntSyncValue(() -> boiler.mSetTier, val -> boiler.mSetTier = val);
        syncManager.syncValue("geoHeat", mHeatSync);
        syncManager.syncValue("geoSteamOutput", mCurrentSteamOutputSync);
        syncManager.syncValue("geoSetTier", mSetTierSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return super.createTerminalTextWidget(syncManager, parent).child(IKey.dynamic(() -> {
            boolean hasInvalidChip = hasInvalidChip();
            return hasInvalidChip
                ? EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.chip_tier2_warn")
                : " ";
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.heat")
                        + EnumChatFormatting.GOLD
                        + numberFormat.format(mHeatSync.getValue() * 100)
                        + "% "
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE
                        + StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.steam_output")
                        + EnumChatFormatting.AQUA
                        + NumberFormatUtil.formatNumber(mCurrentSteamOutputSync.getValue())
                        + " L/s "
                        + EnumChatFormatting.WHITE
                        + (hasOverheatChip() ? StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.superheated")
                            : StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.steam"))
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth());
    }

    private boolean hasOverheatChip() {
        if (mSetTierSync.getValue() != 2) return false;
        ItemStack stack = boiler.getControllerSlot();
        return stack != null && GTSRItemList.GeothermalOverheatChip.isStackEqual(stack, true, true);
    }

    private boolean hasInvalidChip() {
        if (mSetTierSync.getValue() == 2) return false;
        ItemStack stack = boiler.getControllerSlot();
        return stack != null && GTSRItemList.GeothermalOverheatChip.isStackEqual(stack, true, true);
    }
}
