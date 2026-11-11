// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.gui;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.machine.MTEHeavySteamTurbine;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/** Reuses the native panel, controls and GTSR's existing synchronized terminal rows. */
public final class MTEHeavySteamTurbineGui extends MTEMultiBlockBaseGui<MTEHeavySteamTurbine> {

    public MTEHeavySteamTurbineGui(MTEHeavySteamTurbine turbine) {
        super(turbine);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        GTSRProgressBarGuiHelper.appendEntryRows(list, syncManager, multiblock);
        return list;
    }
}
