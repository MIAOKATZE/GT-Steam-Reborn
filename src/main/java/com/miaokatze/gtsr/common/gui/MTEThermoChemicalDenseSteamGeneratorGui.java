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
 * TCDS GUI（LGB 同款 ModularUI 终端模式）：热量条/状态行走 GTSRProgressBar 词条系统，
 * 芯片槽为 GT5U 基类 GUI 自带控制器槽（mInventory[1]）。附加行：芯片槽告警、燃料名称与热值、
 * 普通/致密模式。
 */
public class MTEThermoChemicalDenseSteamGeneratorGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTEThermoChemicalDenseSteamGenerator generator;

    private DoubleSyncValue mHeatSync;
    private IntSyncValue mOutputSync;
    private IntSyncValue mFuelKindSync;
    private IntSyncValue mFuelValueSync;
    private StringSyncValue mFuelNameSync;
    private IntSyncValue mSuperTierSync;

    public MTEThermoChemicalDenseSteamGeneratorGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.generator = (MTEThermoChemicalDenseSteamGenerator) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        // setter 回写客户端机器字段：模式/燃料行直接复用服务端同源显示口径（客户端槽位物品本地可读）
        mHeatSync = new DoubleSyncValue(() -> generator.mHeat, val -> generator.mHeat = val);
        mOutputSync = new IntSyncValue(
            () -> generator.mCurrentOutputEquivalent,
            val -> generator.mCurrentOutputEquivalent = val);
        mFuelKindSync = new IntSyncValue(() -> generator.mCurrentFuelKind, val -> generator.mCurrentFuelKind = val);
        mFuelValueSync = new IntSyncValue(() -> generator.mCurrentFuelValue, val -> generator.mCurrentFuelValue = val);
        mFuelNameSync = new StringSyncValue(
            () -> generator.mCurrentFuelFluidName,
            val -> generator.mCurrentFuelFluidName = val);
        mSuperTierSync = new IntSyncValue(
            () -> generator.mSuperheatedTier ? 1 : 0,
            val -> generator.mSuperheatedTier = val != 0);
        syncManager.syncValue("tcdsHeat", mHeatSync);
        syncManager.syncValue("tcdsOutput", mOutputSync);
        syncManager.syncValue("tcdsFuelKind", mFuelKindSync);
        syncManager.syncValue("tcdsFuelValue", mFuelValueSync);
        syncManager.syncValue("tcdsFuelName", mFuelNameSync);
        syncManager.syncValue("tcdsSuperTier", mSuperTierSync);
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
        // 燃料行：无燃料 / 具体燃料流体名·热值
        list.child(
            IKey.dynamic(
                () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.tcds.fuel")
                    + EnumChatFormatting.AQUA
                    + generator.fuelDisplayText()
                    + EnumChatFormatting.RESET)
                .asWidget()
                .marginBottom(2)
                .fullWidth());
        // 模式行：普通/致密 × 蒸汽/过热蒸汽（芯片判定读客户端同步槽位）
        list.child(
            IKey.dynamic(
                () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.tcds.mode")
                    + EnumChatFormatting.LIGHT_PURPLE
                    + generator.outputModeSuffix()
                    + EnumChatFormatting.RESET)
                .asWidget()
                .marginBottom(2)
                .fullWidth());
        // 热量% / 当量产出速率（GTSRProgressBar 词条系统）
        GTSRProgressBarGuiHelper.appendEntryRows(list, syncManager, generator);
        return list;
    }
}
