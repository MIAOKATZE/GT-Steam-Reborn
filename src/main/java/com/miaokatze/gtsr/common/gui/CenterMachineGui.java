// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.gui;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public final class CenterMachineGui extends MTEMultiBlockBaseGui<MTEMineralIntegrationCenter> {

    private static final String OPERATION = "gtsr.center.operation";
    private final MTEMineralIntegrationCenter machine;

    public CenterMachineGui(MTEMineralIntegrationCenter machine) {
        super(machine);
        this.machine = machine;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager manager) {
        super.registerSyncValues(manager);
        var status = GenericSyncValue.builder(NBTTagCompound.class)
            .getter(manager.isClient() ? NBTTagCompound::new : machine::operationStatus)
            .serializer(PacketBuffer::writeNBTTagCompoundToBuffer)
            .deserializer(PacketBuffer::readNBTTagCompoundFromBuffer)
            .copyImmutable()
            .build();
        manager.syncValue(OPERATION, status);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager manager, ModularPanel parent) {
        @SuppressWarnings("unchecked")
        var status = (GenericSyncValue<NBTTagCompound, ?>) manager.findSyncHandler(OPERATION, GenericSyncValue.class);
        var list = super.createTerminalTextWidget(manager, parent);
        for (int i = 0; i < CenterOperationText.ROWS; i++) {
            int row = i;
            var line = IKey.dynamic(() -> CenterOperationText.nativeLine(status.getValue(), row)).asWidget()
                .name("center_gt_operation_" + row);
            if (i == 0) line.tooltipBuilder(t -> t.add(StatCollector.translateToLocal("gtsr.ore_center.tooltip.terminal")));
            if (i == 1) {
                var bar = new ProgressWidget().texture(GTGuiTextures.PROGRESSBAR_ARROW_STANDARD, 20)
                    .progress(() -> {
                        NBTTagCompound data = status.getValue();
                        int duration = data.getInteger("Duration");
                        return duration > 0 ? (double) data.getInteger("Elapsed") / duration : 0;
                    }).name("center_gt_batch_progress").right(2).top(0).size(20, 11);
                list.child(new ParentWidget<>().fullWidth().height(12).marginBottom(1)
                    .child(line.left(0).right(24).top(0).height(11)).child(bar));
            } else list.child(line.marginBottom(2).fullWidth());
        }
        return list;
    }
}
