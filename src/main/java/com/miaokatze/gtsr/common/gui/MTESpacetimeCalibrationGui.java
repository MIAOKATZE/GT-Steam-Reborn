package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.machine.MTESpacetimeCalibration;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/** Utility status uses server-owned sync values, independently of the recipe progress timer. */
public final class MTESpacetimeCalibrationGui extends MTEMultiBlockBaseGui<MTESpacetimeCalibration> {

    private static final String PREFIX = "gtsr.spacetime.gui.";
    private static final String[] STATES = { "idle", "preheating", "calibrated", "structure", "maintenance", "power",
        "disabled", "blocked" };
    private IntSyncValue state, progress;
    private LongSyncValue cost;

    public MTESpacetimeCalibrationGui(MTESpacetimeCalibration multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        state = new IntSyncValue(multiblock::getStateForGui);
        progress = new IntSyncValue(multiblock::getPreheatTicksForGui);
        cost = new LongSyncValue(multiblock::getEnergyCostForGui);
        syncManager.syncValue("spacetime.state", state);
        syncManager.syncValue("spacetime.progress", progress);
        syncManager.syncValue("spacetime.cost", cost);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        list.child(
            IKey.dynamic(() -> line("status", statusText()))
                .asWidget()
                .marginBottom(2)
                .fullWidth());
        list.child(
            IKey.dynamic(
                () -> line(
                    "preheat",
                    String.format("%.1f / 30 s (%d%%)", progress.getValue() / 20.0, progress.getValue() / 6)))
                .asWidget()
                .marginBottom(2)
                .fullWidth());
        list.child(
            IKey.dynamic(() -> line("cost", cost.getValue() + " EU/t"))
                .asWidget()
                .marginBottom(2)
                .fullWidth());
        return list;
    }

    private String statusText() {
        int value = Math.max(0, Math.min(STATES.length - 1, state.getValue()));
        EnumChatFormatting color = value == 2 ? EnumChatFormatting.GREEN
            : value == 1 ? EnumChatFormatting.AQUA : value >= 3 ? EnumChatFormatting.RED : EnumChatFormatting.WHITE;
        return color + StatCollector.translateToLocal(PREFIX + STATES[value]);
    }

    private static String line(String key, String value) {
        return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(PREFIX + key)
            + " "
            + EnumChatFormatting.WHITE
            + value;
    }
}
