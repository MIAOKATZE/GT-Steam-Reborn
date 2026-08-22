package com.miaokatze.gtsr.common.gui;

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
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.MTELargeGeothermalSteamBoiler;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTELargeGeothermalSteamBoilerGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTELargeGeothermalSteamBoiler boiler;

    private DoubleSyncValue mHeatSync;
    private DoubleSyncValue mCalcificationSync;
    private IntSyncValue mCurrentSteamOutputSync;
    private IntSyncValue mSetTierSync;

    public MTELargeGeothermalSteamBoilerGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.boiler = (MTELargeGeothermalSteamBoiler) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        mHeatSync = new DoubleSyncValue(() -> boiler.mHeat, val -> boiler.mHeat = val);
        mCalcificationSync = new DoubleSyncValue(() -> boiler.mCalcification, val -> boiler.mCalcification = val);
        mCurrentSteamOutputSync = new IntSyncValue(
            () -> boiler.mCurrentSteamOutput,
            val -> boiler.mCurrentSteamOutput = val);
        mSetTierSync = new IntSyncValue(() -> boiler.mSetTier, val -> boiler.mSetTier = val);
        syncManager.syncValue("geoHeat", mHeatSync);
        syncManager.syncValue("geoCalcification", mCalcificationSync);
        syncManager.syncValue("geoSteamOutput", mCurrentSteamOutputSync);
        syncManager.syncValue("geoSetTier", mSetTierSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        // 文本行：芯片等级警告（热量/结垢/蒸汽输出数值行已迁移至 GTSRProgressBar 词条系统）
        list.child(IKey.dynamic(() -> {
            boolean hasInvalidChip = hasInvalidChip();
            return hasInvalidChip
                ? EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.chip_tier2_warn")
                : " ";
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth());
        GTSRProgressBarGuiHelper.appendEntryRows(list, syncManager, boiler);
        return list;
    }

    @Override
    public ModularPanel build(com.cleanroommc.modularui.factory.PosGuiData guiData, PanelSyncManager syncManager,
        com.cleanroommc.modularui.screen.UISettings uiSettings) {
        return super.build(guiData, syncManager, uiSettings);
    }

    private boolean hasInvalidChip() {
        if (mSetTierSync.getValue() == 2) return false;
        ItemStack stack = boiler.getControllerSlot();
        return stack != null && GTSRItemList.GeothermalOverheatChip.isStackEqual(stack, true, true);
    }
}
