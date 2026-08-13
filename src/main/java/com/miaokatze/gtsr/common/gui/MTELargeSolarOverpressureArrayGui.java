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
import com.miaokatze.gtsr.common.machine.MTELargeSolarOverpressureArray;

import gregtech.api.enums.ItemList;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTELargeSolarOverpressureArrayGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTELargeSolarOverpressureArray solarArray;

    private DoubleSyncValue mHeatSync;
    private DoubleSyncValue mCalcificationSync;
    private DoubleSyncValue mSunRatioSync;
    private IntSyncValue mCurrentSteamOutputSync;

    private static final NumberFormat numberFormat = NumberFormat.getNumberInstance();

    static {
        numberFormat.setMinimumFractionDigits(3);
        numberFormat.setMaximumFractionDigits(3);
    }

    public MTELargeSolarOverpressureArrayGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.solarArray = (MTELargeSolarOverpressureArray) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        mHeatSync = new DoubleSyncValue(() -> solarArray.mHeat, val -> solarArray.mHeat = val);
        mCalcificationSync = new DoubleSyncValue(
            () -> solarArray.mCalcification,
            val -> solarArray.mCalcification = val);
        mCurrentSteamOutputSync = new IntSyncValue(
            () -> solarArray.mCurrentSteamOutput,
            val -> solarArray.mCurrentSteamOutput = val);
        mSunRatioSync = new DoubleSyncValue(() -> solarArray.mSunRatio, val -> solarArray.mSunRatio = val);
        syncManager.syncValue("solarHeat", mHeatSync);
        syncManager.syncValue("solarCalcification", mCalcificationSync);
        syncManager.syncValue("solarSteamOutput", mCurrentSteamOutputSync);
        syncManager.syncValue("solarSunRatio", mSunRatioSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        // 热量/阳光比例/结垢/蒸汽输出数值行已迁移至 GTSRProgressBar 词条系统；太阳能增幅行为文本行保留
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        GTSRProgressBarGuiHelper.appendEntryRows(list, syncManager, solarArray);
        list.child(IKey.dynamic(() -> {
            float booster = calculateSolarBooster();
            double overpressureExtra = Math.max(0.0d, mHeatSync.getValue() - 1.0d);
            // 额外增幅口径（v1.10.51）：两段式 = 太阳能额外(booster-1) + 超压额外(heat-1)，
            // 不显示基础 100%；无锅炉时保留灰字提示、无超压时超压段显示 +0%
            String hint = booster <= 1.0f
                ? " " + EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.solar_array.boost_hint")
                : "";
            return EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.solar_array.solar_booster")
                + EnumChatFormatting.GREEN
                + "+"
                + numberFormat.format((booster - 1.0f) * 100)
                + "%"
                + EnumChatFormatting.WHITE
                + " + "
                + EnumChatFormatting.LIGHT_PURPLE
                + "+"
                + numberFormat.format(overpressureExtra * 100)
                + "%"
                + hint
                + EnumChatFormatting.RESET;
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth());
        return list;
    }

    @Override
    public ModularPanel build(com.cleanroommc.modularui.factory.PosGuiData guiData, PanelSyncManager syncManager,
        com.cleanroommc.modularui.screen.UISettings uiSettings) {
        return super.build(guiData, syncManager, uiSettings);
    }

    private float calculateSolarBooster() {
        // 与机器端一致：高级太阳能锅炉每满组 64 台 +2.0x，简单太阳能锅炉每满组 64 台 +1.0x，最高 3.0x
        float booster = 1.0f;
        ItemStack stack = solarArray.getControllerSlot();
        if (stack != null) {
            if (ItemList.Machine_HP_Solar.isStackEqual(stack, false, false)) {
                booster += 2.0f * Math.min(stack.stackSize, 64) / 64.0f;
            } else if (ItemList.Machine_Bronze_Boiler_Solar.isStackEqual(stack, false, false)) {
                booster += 1.0f * Math.min(stack.stackSize, 64) / 64.0f;
            }
        }
        return Math.min(booster, 3.0f);
    }
}
