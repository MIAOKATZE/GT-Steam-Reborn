// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.gui;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public final class CenterMachineGui extends MTEMultiBlockBaseGui<MTEMineralIntegrationCenter> {

    private final MTEMineralIntegrationCenter machine;

    public CenterMachineGui(MTEMineralIntegrationCenter machine) {
        super(machine);
        this.machine = machine;
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager manager, ModularPanel parent) {
        var status = GenericSyncValue.builder(NBTTagCompound.class)
            .getter(manager.isClient() ? NBTTagCompound::new : machine::operationStatus)
            .serializer(PacketBuffer::writeNBTTagCompoundToBuffer)
            .deserializer(PacketBuffer::readNBTTagCompoundFromBuffer)
            .copyImmutable()
            .build();
        manager.syncValue("gtsr.center.operation", status);
        var list = super.createTerminalTextWidget(manager, parent);
        for (int i = 0; i < CenterOperationText.ROWS; i++) {
            int row = i;
            list.child(IKey.dynamic(() -> CenterOperationText.line(status.getValue(), row)).color(0xFFD0DBD9).asWidget()
                .name("center_gt_operation_" + row).fullWidth());
        }
        return list;
    }
}
