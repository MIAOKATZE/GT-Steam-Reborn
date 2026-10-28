package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment.MainAxis;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.gui.mui2.TerminalViewport;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/** Ordinary right-click uses the GT multiblock UI with a brief loom/journal summary. */
public final class MTECriticalMachineGui extends MTEMultiBlockBaseGui<MTECriticalMachine> {

    private final CriticalMachineState state;
    private final boolean loomController;
    private IPanelHandler constructionPanel;

    public MTECriticalMachineGui(MTECriticalMachine machine) {
        super(machine);
        state = machine.getCriticalState();
        loomController = machine.isKindSelector();
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
            .child(createStructureUpdateButton(manager))
            .child(
                new ButtonWidget<>().name("critical_native_construction")
                    .setEnabledIf(w -> loomController)
                    .size(18)
                    .overlay(IKey.str("C"))
                    .tooltipBuilder(t -> t.add(IKey.lang("gtsr.critical.tab.construction")))
                    .onMousePressed(button -> {
                        if (button != 0) return false;
                        constructionPanel.openPanel();
                        return true;
                    }));
    }

    @Override
    protected void registerSyncValues(PanelSyncManager manager) {
        super.registerSyncValues(manager);
        manager.syncValue("critical.native.loom", new BooleanSyncValue(state::isLoomStructureValid));
        manager.syncValue("critical.native.status", new IntSyncValue(state::getStatusCode));
        manager.syncValue("critical.native.runtime", new StringSyncValue(state::getRuntimeStatusKey));
        manager.syncValue(
            "critical.native.blocker",
            new StringSyncValue(() -> "runtime.blocked".equals(state.getRuntimeBlockerKey()) ? state.getStatus() : ""));
        constructionPanel = manager.syncedPanel("critical.native.construction", true, (childManager, handler) -> {
            CriticalConstructionView view = new CriticalConstructionView(state, childManager);
            ParentWidget<?> content = new ParentWidget<>().size(475, 230)
                .child(
                    IKey.lang("gtsr.critical.tab.construction")
                        .color(CriticalGuiStyle.TEXT_TITLE)
                        .asWidget()
                        .pos(8, 7)
                        .size(435, 12))
                .child(
                    view.create()
                        .pos(6, 22));
            return new ModularPanel("critical_native_construction").size(475, 230)
                .background(CriticalGuiStyle.PANEL)
                .child(
                    TerminalViewport.wrapExact("critical_native_construction_viewport", content, 475, 230)
                        .sizeRel(1f))
                .child(ButtonWidget.panelCloseButton());
        });
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager manager, ModularPanel panel) {
        BooleanSyncValue loom = manager.findSyncHandler("critical.native.loom", BooleanSyncValue.class);
        IntSyncValue status = manager.findSyncHandler("critical.native.status", IntSyncValue.class);
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(manager, panel);
        if (loomController) {
            line(
                list,
                () -> (loom.getValue() ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                    + tr(loom.getValue() ? "loom.formed" : "loom.unformed"));
            line(list, () -> EnumChatFormatting.YELLOW + tr("status." + status.getValue()));
            line(list, () -> EnumChatFormatting.GRAY + tr("ui.native.loom_hint"));
        } else {
            StringSyncValue runtime = manager.findSyncHandler("critical.native.runtime", StringSyncValue.class);
            StringSyncValue blocker = manager.findSyncHandler("critical.native.blocker", StringSyncValue.class);
            line(list, () -> EnumChatFormatting.YELLOW + tr("ui.running_status") + ": " + tr(runtime.getValue()));
            line(
                list,
                () -> blocker.getValue()
                    .isEmpty() ? "" : EnumChatFormatting.YELLOW + blocker.getValue());
            line(list, () -> EnumChatFormatting.GRAY + tr("ui.native.main_hint"));
        }
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
