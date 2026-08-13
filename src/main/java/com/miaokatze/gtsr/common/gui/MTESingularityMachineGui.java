package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTESingularityMachineGui<T extends MTESingularityMachineBase> extends MTEMultiBlockBaseGui<T> {

    public MTESingularityMachineGui(T multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue("gtsr.maxProgress", new IntSyncValue(() -> multiblock.mMaxProgresstime));
        syncManager.syncValue("gtsr.mode", new IntSyncValue(multiblock::getModeForGui));
        syncManager.syncValue("gtsr.fuelTicks", new IntSyncValue(multiblock::getFuelTicksForGui));
        syncManager.syncValue("gtsr.tier", new IntSyncValue(() -> multiblock.mTier));
    }

    /**
     * 热量机制是否在 GUI 终端展示（热量行 + 「热量累积中」状态行）。
     * 默认致密态操控机不显示；地壳物质聚合器已删除热量机制，覆写为 false。
     */
    protected boolean isHeatGuiShown() {
        return !multiblock.isDenseStateManipulator();
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        String keyPrefix = multiblock.getGuiKeyPrefix();
        IntSyncValue maxProgressSyncer = syncManager.findSyncHandler("gtsr.maxProgress", IntSyncValue.class);
        IntSyncValue modeSyncer = syncManager.findSyncHandler("gtsr.mode", IntSyncValue.class);
        IntSyncValue tierSyncer = syncManager.findSyncHandler("gtsr.tier", IntSyncValue.class);

        ListWidget<IWidget, ?> widget = super.createTerminalTextWidget(syncManager, parent);
        // 数值词条（热量等）由机器注册，统一在此渲染；文本行保留在其后
        GTSRProgressBarGuiHelper.appendEntryRows(widget, syncManager, multiblock);
        return widget.child(IKey.dynamic(() -> {
            String statusKey;
            EnumChatFormatting statusColor;
            if (multiblock.isDenseStateManipulator()) {
                // DSM：工作中按压缩/解压模式显示，否则待机中
                if (maxProgressSyncer.getValue() > 0) {
                    statusKey = modeSyncer.getValue() == 1 ? "status.decompress" : "status.compress";
                    statusColor = EnumChatFormatting.LIGHT_PURPLE;
                } else {
                    statusKey = "status.idle";
                    statusColor = EnumChatFormatting.WHITE;
                }
            } else if (maxProgressSyncer.getValue() > 0) {
                statusKey = "status.running";
                statusColor = EnumChatFormatting.AQUA;
            } else {
                statusKey = "status.idle";
                statusColor = EnumChatFormatting.WHITE;
            }
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "status")
                + statusColor
                + StatCollector.translateToLocal(keyPrefix + statusKey);
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth())
            .childIf(
                !multiblock.isHideTierInGui(),
                () -> IKey
                    .dynamic(
                        () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "tier")
                            + EnumChatFormatting.GOLD
                            + tierSyncer.getValue()
                            + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .childIf(multiblock.isDenseStateManipulator(), () -> IKey.dynamic(() -> {
                String modeKey = modeSyncer.getValue() == 1 ? "mode.decompress" : "mode.compress";
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "mode")
                    + EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal(keyPrefix + modeKey);
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .childIf(multiblock.isDenseStateManipulator(), () -> IKey.dynamic(() -> {
                // v1.10.59：燃料行 → 奇点模式行（关/普通/临界 + 剩余秒数）
                int mode = multiblock.getSingularityModeForGui();
                int ticks = multiblock.getSingularityTicksForGui();
                String value = mode == 0 ? StatCollector.translateToLocal(keyPrefix + "singularity_off")
                    : (mode == 2 ? StatCollector.translateToLocal(keyPrefix + "singularity_critical")
                        : StatCollector.translateToLocal(keyPrefix + "singularity_steam"))
                        + String.format(" %ds", ticks / 20);
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "singularity_mode")
                    + EnumChatFormatting.RED
                    + value;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth());
    }
}
