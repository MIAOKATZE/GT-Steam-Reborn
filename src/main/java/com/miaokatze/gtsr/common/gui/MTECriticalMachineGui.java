package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment.MainAxis;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/** Ordinary right-click uses the GT multiblock UI with a brief loom/journal summary. */
public final class MTECriticalMachineGui extends MTEMultiBlockBaseGui<MTECriticalMachine> {

    private final CriticalMachineState state;

    public MTECriticalMachineGui(MTECriticalMachine machine) {
        super(machine);
        state = machine.getCriticalState();
    }

    @Override
    protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager manager) {
        // GT205 has no separate controller-slot flag. Keep its button layout without this unused storage slot.
        return Flow.column()
            .width(18)
            .leftRel(1, -2, 1)
            .mainAxisAlignment(MainAxis.END)
            .reverseLayout(true)
            .child(createPowerSwitchButton())
            .child(createStructureUpdateButton(manager));
    }

    @Override
    protected void registerSyncValues(PanelSyncManager manager) {
        super.registerSyncValues(manager);
        manager.syncValue("critical.native.loom", new BooleanSyncValue(state::isLoomStructureValid));
        manager.syncValue("critical.native.status", new IntSyncValue(state::getStatusCode));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager manager, ModularPanel panel) {
        BooleanSyncValue loom = manager.findSyncHandler("critical.native.loom", BooleanSyncValue.class);
        IntSyncValue status = manager.findSyncHandler("critical.native.status", IntSyncValue.class);
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(manager, panel);
        line(
            list,
            () -> (loom.getValue() ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                + tr(loom.getValue() ? "loom.formed" : "loom.unformed"));
        line(list, () -> EnumChatFormatting.YELLOW + tr("status." + status.getValue()));
        line(list, () -> EnumChatFormatting.GRAY + tr("management_hint"));
        return list;
    }

    private static void line(ListWidget<IWidget, ?> list, java.util.function.Supplier<String> text) {
        list.child(
            IKey.dynamic(text)
                .asWidget()
                .fullWidth()
                .marginBottom(2));
    }

    private static String tr(String suffix) {
        return StatCollector.translateToLocal("gtsr.critical." + suffix);
    }
}
