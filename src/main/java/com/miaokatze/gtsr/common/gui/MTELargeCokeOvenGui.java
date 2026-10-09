package com.miaokatze.gtsr.common.gui;

import java.util.Locale;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.machine.MTELargeCokeOven;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTELargeCokeOvenGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTELargeCokeOven cokeOven;

    private DoubleSyncValue mHeatSync;
    private IntSyncValue mMaxProgresstimeSync;
    private IntSyncValue mTierSync;

    public MTELargeCokeOvenGui(MTEEnhancedMultiBlockBase<?> multiblock) {
        super(multiblock);
        this.cokeOven = (MTELargeCokeOven) multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        mHeatSync = new DoubleSyncValue(() -> cokeOven.mHeat, val -> cokeOven.mHeat = val);
        mMaxProgresstimeSync = syncManager.findSyncHandler("maxProgressTime", IntSyncValue.class);
        mTierSync = new IntSyncValue(() -> cokeOven.mTier, val -> cokeOven.mTier = val);
        syncManager.syncValue("cokeHeat", mHeatSync);
        syncManager.syncValue("cokeTier", mTierSync);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        // 炉温数值行已迁移至 GTSRProgressBar 词条系统；状态/配方时长/并行为文本行保留
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        GTSRProgressBarGuiHelper.appendEntryRows(list, syncManager, cokeOven);
        list.child(IKey.dynamic(() -> {
            String statusKey;
            EnumChatFormatting statusColor;
            if (mMaxProgresstimeSync.getValue() > 0) {
                statusKey = "gtsr.gui.status.running";
                statusColor = EnumChatFormatting.AQUA;
            } else if (mHeatSync.getValue() > 0) {
                statusKey = "gtsr.gui.coke_oven.status.cooling";
                statusColor = EnumChatFormatting.BLUE;
            } else {
                statusKey = "gtsr.gui.status.idle";
                statusColor = EnumChatFormatting.WHITE;
            }
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                + " "
                + statusColor
                + StatCollector.translateToLocal(statusKey)
                + EnumChatFormatting.RESET;
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth())
            .child(IKey.dynamic(() -> {
                // 父类同步的是本批已经应用全部倍率的最终 tick 数；不随运行中升温再次缩减。
                if (mMaxProgresstimeSync.getValue() > 0) {
                    return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.recipe_time")
                        + EnumChatFormatting.GOLD
                        + String.format(Locale.ROOT, "%.2f", mMaxProgresstimeSync.getValue() / 20.0d)
                        + "s"
                        + EnumChatFormatting.RESET;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.recipe_time")
                    + EnumChatFormatting.WHITE
                    + "-"
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.parallel")
                        + " "
                        + EnumChatFormatting.GOLD
                        + cokeOven.getMaxParallelRecipes()
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth());
        return list;
    }
}
