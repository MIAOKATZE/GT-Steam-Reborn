package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.machine.tcds.MTEThermoChemicalDenseSteamGenerator;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * TCDS GUI（LGB 同款 ModularUI 终端模式）：芯片告警行 + 全部数值行统一走
 * GTSRProgressBar 词条系统（热量 → 输出档位 → 流量（设定+理论最大热量）→ 蒸汽输出 → 燃料段（单燃料一行 /
 * 双燃料两行）→ 空气 → 蒸馏水，配色仿 LSOA 纪律：标签 WHITE、产量类 GREEN、消耗类 GOLD、状态提示 AQUA；
 * 燃料行零值自动隐藏实现单/双燃料互斥）。
 * 芯片槽为 GT5U 基类 GUI 自带控制器槽（mInventory[1]）；本类保留芯片告警文本行与字段同步链。
 */
public class MTEThermoChemicalDenseSteamGeneratorGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTEThermoChemicalDenseSteamGenerator generator;

    private DoubleSyncValue mHeatSync;
    private IntSyncValue mOutputSync;
    private IntSyncValue mFuelKindSync;
    private StringSyncValue mFuelNameSync;
    private IntSyncValue mSuperTierSync;
    private IntSyncValue mOutputTierSync;
    private StringSyncValue mLiquidFuelNameSync;
    private IntSyncValue mFuelConsumptionSync;
    private IntSyncValue mGasConsumptionSync;
    private IntSyncValue mLiquidConsumptionSync;
    private IntSyncValue mAirConsumptionSync;
    private IntSyncValue mWaterConsumptionSync;

    public MTEThermoChemicalDenseSteamGeneratorGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.generator = (MTEThermoChemicalDenseSteamGenerator) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        // setter 回写客户端机器字段：数值行/燃料行直接复用服务端同源显示口径（客户端槽位物品本地可读）
        mHeatSync = new DoubleSyncValue(() -> generator.mHeat, val -> generator.mHeat = val);
        mOutputSync = new IntSyncValue(
            () -> generator.mCurrentOutputEquivalent,
            val -> generator.mCurrentOutputEquivalent = val);
        mFuelKindSync = new IntSyncValue(() -> generator.mCurrentFuelKind, val -> generator.mCurrentFuelKind = val);
        mFuelNameSync = new StringSyncValue(
            () -> generator.mCurrentFuelFluidName,
            val -> generator.mCurrentFuelFluidName = val);
        mSuperTierSync = new IntSyncValue(
            () -> generator.mSuperheatedTier ? 1 : 0,
            val -> generator.mSuperheatedTier = val != 0);
        mOutputTierSync = new IntSyncValue(() -> generator.mOutputTier, val -> generator.mOutputTier = val);
        mLiquidFuelNameSync = new StringSyncValue(
            () -> generator.mCurrentFuelLiquidName,
            val -> generator.mCurrentFuelLiquidName = val);
        mFuelConsumptionSync = new IntSyncValue(
            () -> generator.mCurrentFuelConsumption,
            val -> generator.mCurrentFuelConsumption = val);
        mGasConsumptionSync = new IntSyncValue(
            () -> generator.mCurrentGasConsumption,
            val -> generator.mCurrentGasConsumption = val);
        mLiquidConsumptionSync = new IntSyncValue(
            () -> generator.mCurrentLiquidConsumption,
            val -> generator.mCurrentLiquidConsumption = val);
        mAirConsumptionSync = new IntSyncValue(
            () -> generator.mCurrentAirConsumption,
            val -> generator.mCurrentAirConsumption = val);
        mWaterConsumptionSync = new IntSyncValue(
            () -> generator.mCurrentWaterConsumption,
            val -> generator.mCurrentWaterConsumption = val);
        syncManager.syncValue("tcdsHeat", mHeatSync);
        syncManager.syncValue("tcdsOutput", mOutputSync);
        syncManager.syncValue("tcdsFuelKind", mFuelKindSync);
        syncManager.syncValue("tcdsFuelName", mFuelNameSync);
        syncManager.syncValue("tcdsSuperTier", mSuperTierSync);
        syncManager.syncValue("tcdsOutputTier", mOutputTierSync);
        syncManager.syncValue("tcdsLiquidFuelName", mLiquidFuelNameSync);
        syncManager.syncValue("tcdsFuelConsumption", mFuelConsumptionSync);
        syncManager.syncValue("tcdsGasConsumption", mGasConsumptionSync);
        syncManager.syncValue("tcdsLiquidConsumption", mLiquidConsumptionSync);
        syncManager.syncValue("tcdsAirConsumption", mAirConsumptionSync);
        syncManager.syncValue("tcdsWaterConsumption", mWaterConsumptionSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        // 告警行：控制器槽错装其他物品（芯片无效）
        list.child(IKey.dynamic(() -> {
            boolean invalid = generator.hasInvalidChipSlotItem();
            return invalid ? EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.tcds.chip_warn") : " ";
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth());
        // 数值行统一走词条系统：热量 / 输出档位 / 流量（设定+理论最大热量）/ 蒸汽输出 / 燃料段（单燃料一行 /
        // 双燃料两行，零值行自动隐藏实现模式互斥）/ 空气消耗 / 蒸馏水消耗（行序与配色见机器端 registerProgressEntries）
        GTSRProgressBarGuiHelper.appendEntryRows(list, syncManager, generator);
        return list;
    }
}
