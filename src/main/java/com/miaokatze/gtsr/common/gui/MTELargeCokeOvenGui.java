package com.miaokatze.gtsr.common.gui;

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

    public MTELargeCokeOvenGui(MTELargeCokeOven cokeOven) {
        super(cokeOven);
        this.cokeOven = cokeOven;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        // 父 GUI 已同步 progressTime/maxProgressTime 并写回机器，不重复注册批次状态。
        syncManager.syncValue("cokeHeat", new DoubleSyncValue(cokeOven::getHeat, cokeOven::setHeat));
        syncManager.syncValue("cokeTier", new IntSyncValue(cokeOven::getCokeTier, cokeOven::setCokeTier));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        // 炉温数值行已迁移至 GTSRProgressBar 词条系统；状态/配方时长/并行为文本行保留
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        GTSRProgressBarGuiHelper.appendEntryRows(list, syncManager, cokeOven);
        list.child(
            IKey.dynamic(cokeOven::getStatusText)
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(
                IKey.dynamic(cokeOven::getRecipeTimeText)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            .child(
                IKey.dynamic(cokeOven::getParallelText)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth());
        return list;
    }
}
