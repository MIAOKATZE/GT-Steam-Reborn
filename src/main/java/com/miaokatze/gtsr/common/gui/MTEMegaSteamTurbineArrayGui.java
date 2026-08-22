package com.miaokatze.gtsr.common.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes.SteamType;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTEMegaSteamTurbineArrayGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTEMegaSteamTurbineArray turbineArray;

    private IntSyncValue mCasingTierSync;
    private IntSyncValue mStackCountSync;
    private IntSyncValue mTheoreticalEUtSync;
    private IntSyncValue mSteamConsumptionSync;
    private IntSyncValue mSteamTypeOrdinalSync;
    private IntSyncValue mGearTierSync;
    private IntSyncValue mPipeTierSync;
    private IntSyncValue mSingularityModeSync;
    private IntSyncValue mSingularityModeTicksSync;

    public MTEMegaSteamTurbineArrayGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.turbineArray = (MTEMegaSteamTurbineArray) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        mCasingTierSync = new IntSyncValue(() -> turbineArray.mCasingTier, val -> turbineArray.mCasingTier = val);
        mStackCountSync = new IntSyncValue(() -> turbineArray.mStackCount, val -> turbineArray.mStackCount = val);
        mTheoreticalEUtSync = new IntSyncValue(
            () -> turbineArray.mTheoreticalEUt,
            val -> turbineArray.mTheoreticalEUt = val);
        mSteamConsumptionSync = new IntSyncValue(
            () -> turbineArray.mSteamConsumption,
            val -> turbineArray.mSteamConsumption = val);
        mSteamTypeOrdinalSync = new IntSyncValue(
            () -> turbineArray.mSteamType.ordinal(),
            val -> turbineArray.mSteamType = SteamType.values()[val]);
        mGearTierSync = new IntSyncValue(() -> turbineArray.mGearTier, val -> turbineArray.mGearTier = val);
        mPipeTierSync = new IntSyncValue(() -> turbineArray.mPipeTier, val -> turbineArray.mPipeTier = val);
        mSingularityModeSync = new IntSyncValue(
            () -> turbineArray.mSingularityMode,
            val -> turbineArray.mSingularityMode = val);
        mSingularityModeTicksSync = new IntSyncValue(
            () -> turbineArray.mSingularityModeTicks,
            val -> turbineArray.mSingularityModeTicks = val);

        syncManager.syncValue("turbineCasingTier", mCasingTierSync);
        syncManager.syncValue("turbineStackCount", mStackCountSync);
        syncManager.syncValue("turbineTheoreticalEUt", mTheoreticalEUtSync);
        syncManager.syncValue("turbineSteamConsumption", mSteamConsumptionSync);
        syncManager.syncValue("turbineSteamTypeOrdinal", mSteamTypeOrdinalSync);
        syncManager.syncValue("turbineGearTier", mGearTierSync);
        syncManager.syncValue("turbinePipeTier", mPipeTierSync);
        syncManager.syncValue("turbineSingularityMode", mSingularityModeSync);
        syncManager.syncValue("turbineSingularityModeTicks", mSingularityModeTicksSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> widget = super.createTerminalTextWidget(syncManager, parent);
        // 文本行：蒸汽类型（数值词条由机器注册，appendEntryRows 在其后统一渲染）
        widget.child(IKey.dynamic(() -> {
            SteamType steamType = SteamType.values()[mSteamTypeOrdinalSync.getValue()];
            return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.steam_type")
                + (steamType.requiresHighTier() ? EnumChatFormatting.LIGHT_PURPLE : EnumChatFormatting.YELLOW)
                + StatCollector.translateToLocal(steamType.nameKey)
                + (steamType.requiresHighTier() ? EnumChatFormatting.WHITE + " (Tier 6+)" : "");
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth()
            .setEnabledIf(w -> multiblock.mMachine));
        GTSRProgressBarGuiHelper.appendEntryRows(widget, syncManager, turbineArray);
        return widget.child(IKey.dynamic(() -> {
            int stackCount = mStackCountSync.getValue();
            return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.stacks")
                + EnumChatFormatting.AQUA
                + (1 + stackCount)
                + StatCollector.translateToLocal("gtsr.gui.turbine_array.groups")
                + EnumChatFormatting.WHITE
                + " ("
                + (stackCount == 0 ? StatCollector.translateToLocal("gtsr.gui.turbine_array.baseline")
                    : "+" + stackCount + StatCollector.translateToLocal("gtsr.gui.turbine_array.extra"))
                + ")";
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth()
            .setEnabledIf(w -> multiblock.mMachine))
            .child(IKey.dynamic(() -> {
                if (mSingularityModeSync.getValue() == 0) {
                    return EnumChatFormatting.GOLD
                        + StatCollector.translateToLocal("gtsr.gui.turbine_array.singularity_mode")
                        + EnumChatFormatting.GRAY
                        + StatCollector.translateToLocal("gtsr.gui.turbine_array.singularity_off");
                }
                // 临界模式（2）与蒸汽纠缠模式（1）共用倒计时显示，前缀文案区分
                String prefixKey = mSingularityModeSync.getValue() == 2 ? "gtsr.gui.turbine_array.singularity_critical"
                    : "gtsr.gui.turbine_array.singularity_mode";
                int seconds = mSingularityModeTicksSync.getValue() / 20;
                return EnumChatFormatting.GOLD + StatCollector.translateToLocal(prefixKey)
                    + EnumChatFormatting.LIGHT_PURPLE
                    + seconds
                    + "s";
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth()
                .setEnabledIf(w -> multiblock.mMachine))
            // 循环超限芯片状态：未安装 / 已安装但叠加层不足 / 已激活
            // （控制器槽物品客户端可读，叠加层数经 mStackCountSync 同步）
            .child(IKey.dynamic(() -> {
                ItemStack chip = turbineArray.getControllerSlot();
                boolean hasChip = chip != null
                    && GTSRItemList.SteamTurbineCycleOverlimitChip.isStackEqual(chip, true, true);
                if (!hasChip) {
                    return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.chip_state")
                        + EnumChatFormatting.GRAY
                        + StatCollector.translateToLocal("gtsr.gui.turbine_array.chip_missing");
                }
                if (mStackCountSync.getValue() < MTEMegaSteamTurbineArray.MAX_EXTRA_STACKS) {
                    return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.chip_state")
                        + EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.turbine_array.chip_needs_stacks");
                }
                return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.chip_state")
                    + EnumChatFormatting.GREEN
                    + StatCollector.translateToLocal("gtsr.gui.turbine_array.chip_active");
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth()
                .setEnabledIf(w -> multiblock.mMachine));
    }
}
