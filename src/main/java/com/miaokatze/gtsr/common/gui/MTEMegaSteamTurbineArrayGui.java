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
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTEMegaSteamTurbineArrayGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTEMegaSteamTurbineArray turbineArray;

    private IntSyncValue mCasingTierSync;
    private IntSyncValue mStackCountSync;
    private IntSyncValue mTheoreticalEUtSync;
    private IntSyncValue mSteamConsumptionSync;
    private IntSyncValue mSteamTypeOrdinalSync;
    private IntSyncValue mEfficiencySync;
    private IntSyncValue mEUtSync;
    private IntSyncValue mGearTierSync;
    private IntSyncValue mPipeTierSync;
    private IntSyncValue mPowerParameterSync;
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
            val -> turbineArray.mSteamType = MTEMegaSteamTurbineArray.SteamType.values()[val]);
        mEfficiencySync = new IntSyncValue(() -> turbineArray.mEfficiency, val -> turbineArray.mEfficiency = val);
        mEUtSync = new IntSyncValue(() -> turbineArray.mEUt, val -> turbineArray.mEUt = val);
        mGearTierSync = new IntSyncValue(() -> turbineArray.mGearTier, val -> turbineArray.mGearTier = val);
        mPipeTierSync = new IntSyncValue(() -> turbineArray.mPipeTier, val -> turbineArray.mPipeTier = val);
        mPowerParameterSync = new IntSyncValue(
            () -> turbineArray.mPowerParameter,
            val -> turbineArray.mPowerParameter = val);
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
        syncManager.syncValue("turbineEfficiency", mEfficiencySync);
        syncManager.syncValue("turbineEUt", mEUtSync);
        syncManager.syncValue("turbineGearTier", mGearTierSync);
        syncManager.syncValue("turbinePipeTier", mPipeTierSync);
        syncManager.syncValue("turbinePowerParameter", mPowerParameterSync);
        syncManager.syncValue("turbineSingularityMode", mSingularityModeSync);
        syncManager.syncValue("turbineSingularityModeTicks", mSingularityModeTicksSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return super.createTerminalTextWidget(syncManager, parent).child(IKey.dynamic(() -> {
            MTEMegaSteamTurbineArray.SteamType steamType = MTEMegaSteamTurbineArray.SteamType
                .values()[mSteamTypeOrdinalSync.getValue()];
            return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.steam_type")
                + (steamType.requiresHighTier() ? EnumChatFormatting.LIGHT_PURPLE : EnumChatFormatting.YELLOW)
                + StatCollector.translateToLocal(steamType.nameKey)
                + (steamType.requiresHighTier() ? EnumChatFormatting.WHITE + " (Tier 6+)" : "");
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth()
            .setEnabledIf(w -> multiblock.mMachine))
            .child(IKey.dynamic(() -> {
                MTEMegaSteamTurbineArray.SteamType steamType = MTEMegaSteamTurbineArray.SteamType
                    .values()[mSteamTypeOrdinalSync.getValue()];
                // 客户端按同步的奇点模式等级换算功率倍率：2→×5、1→×2、0→×1
                int powerMult = mSingularityModeSync.getValue() == 2 ? 5 : mSingularityModeSync.getValue() == 1 ? 2 : 1;
                return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.eu_t")
                    + EnumChatFormatting.AQUA
                    + NumberFormatUtil.formatNumber(
                        (long) (turbineArray.getVoltage() * mPowerParameterSync.getValue()
                            * turbineArray.getGroupCount()
                            * powerMult
                            * (turbineArray.getMaxEfficiencyLimit(steamType) / 10000.0)
                            * turbineArray.getEffectiveSteamEffFactor(steamType)))
                    + " EU/t";
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth()
                .setEnabledIf(w -> multiblock.mMachine))
            .child(IKey.dynamic(() -> {
                MTEMegaSteamTurbineArray.SteamType steamType = MTEMegaSteamTurbineArray.SteamType
                    .values()[mSteamTypeOrdinalSync.getValue()];
                return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.steam")
                    + EnumChatFormatting.AQUA
                    + NumberFormatUtil.formatNumber(turbineArray.calcSteamConsumption(steamType))
                    + " L/t";
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth()
                .setEnabledIf(w -> multiblock.mMachine))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.savings")
                        + EnumChatFormatting.GREEN
                        + String.format("%.0f%%", turbineArray.getSteamSavings() * 100))
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth()
                    .setEnabledIf(w -> multiblock.mMachine))
            .child(IKey.dynamic(() -> {
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
                MTEMegaSteamTurbineArray.SteamType steamType = MTEMegaSteamTurbineArray.SteamType
                    .values()[mSteamTypeOrdinalSync.getValue()];
                int efficiency = mEfficiencySync.getValue();
                int maxEff = turbineArray.getMaxEfficiencyLimit(steamType);
                return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.efficiency")
                    + (efficiency >= maxEff ? EnumChatFormatting.LIGHT_PURPLE
                        : efficiency >= 10000 ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW)
                    + String.format("%.1f%%", efficiency / 100.0)
                    + (efficiency >= maxEff ? StatCollector.translateToLocal("gtsr.gui.turbine_array.max") : "");
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth()
                .setEnabledIf(w -> multiblock.mMachine))
            .child(IKey.dynamic(() -> {
                MTEMegaSteamTurbineArray.SteamType steamType = MTEMegaSteamTurbineArray.SteamType
                    .values()[mSteamTypeOrdinalSync.getValue()];
                return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.max_efficiency")
                    + EnumChatFormatting.LIGHT_PURPLE
                    + String.format("%.1f%%", turbineArray.getMaxEfficiencyLimit(steamType) / 100.0);
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth()
                .setEnabledIf(w -> multiblock.mMachine))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.output")
                        + EnumChatFormatting.GREEN
                        + NumberFormatUtil
                            .formatNumber(Math.abs((long) mEUtSync.getValue() * mEfficiencySync.getValue() / 10000))
                        + " EU/t")
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth()
                    .setEnabledIf(w -> multiblock.mMachine))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.power_param")
                        + EnumChatFormatting.AQUA
                        + (mPowerParameterSync.getValue() * 10)
                        + "%")
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
