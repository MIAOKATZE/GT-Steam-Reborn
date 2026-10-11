package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalTier;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/** Ordinary right-click keeps GT controls and adds server-owned loom/journal telemetry. */
public final class MTECriticalMachineGui extends MTEMultiBlockBaseGui<MTECriticalMachine> {

    private final CriticalMachineState state;

    public MTECriticalMachineGui(MTECriticalMachine machine) {
        super(machine);
        state = machine.getCriticalState();
    }

    @Override
    protected void registerSyncValues(PanelSyncManager manager) {
        super.registerSyncValues(manager);
        manager.syncValue("critical.native.loom", new BooleanSyncValue(state::isLoomStructureValid));
        manager.syncValue("critical.native.status", new IntSyncValue(state::getStatusCode));
        manager.syncValue("critical.native.reason", new IntSyncValue(state::getReasonCode));
        manager.syncValue(
            "critical.native.kind",
            new IntSyncValue(
                () -> state.getKind()
                    .ordinal()));
        manager.syncValue(
            "critical.native.tier",
            new IntSyncValue(
                () -> state.getTier()
                    .ordinal()));
        manager.syncValue("critical.native.stage", new IntSyncValue(state::getStage));
        manager.syncValue("critical.native.placed", new IntSyncValue(state::getConstructionPlaced));
        manager.syncValue("critical.native.total", new IntSyncValue(state::getJobTotal));
        manager.syncValue("critical.native.round", new IntSyncValue(state::getConstructionRoundIndex));
        manager.syncValue("critical.native.rounds", new IntSyncValue(state::getConstructionRoundTotal));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager manager, ModularPanel panel) {
        BooleanSyncValue loom = manager.findSyncHandler("critical.native.loom", BooleanSyncValue.class);
        IntSyncValue status = manager.findSyncHandler("critical.native.status", IntSyncValue.class);
        IntSyncValue reason = manager.findSyncHandler("critical.native.reason", IntSyncValue.class);
        IntSyncValue kind = manager.findSyncHandler("critical.native.kind", IntSyncValue.class);
        IntSyncValue tier = manager.findSyncHandler("critical.native.tier", IntSyncValue.class);
        IntSyncValue stage = manager.findSyncHandler("critical.native.stage", IntSyncValue.class);
        IntSyncValue placed = manager.findSyncHandler("critical.native.placed", IntSyncValue.class);
        IntSyncValue total = manager.findSyncHandler("critical.native.total", IntSyncValue.class);
        IntSyncValue round = manager.findSyncHandler("critical.native.round", IntSyncValue.class);
        IntSyncValue rounds = manager.findSyncHandler("critical.native.rounds", IntSyncValue.class);
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(manager, panel);
        line(
            list,
            () -> (loom.getValue() ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                + tr(loom.getValue() ? "loom.formed" : "loom.unformed"));
        line(
            list,
            () -> EnumChatFormatting.YELLOW + tr("machine." + CriticalMachineKind.byId(kind.getValue()).key)
                + " · "
                + CriticalTier.byId(tier.getValue())
                    .name());
        line(list, () -> tr("status." + status.getValue()) + " · " + tr("reason." + reason.getValue()));
        line(
            list,
            () -> tr("construction") + " " + stage.getValue() + " · " + placed.getValue() + " / " + total.getValue());
        line(
            list,
            () -> tr("construction.round") + " "
                + (rounds.getValue() == 0 ? 0 : Math.min(rounds.getValue(), round.getValue() + 1))
                + " / "
                + rounds.getValue());
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
