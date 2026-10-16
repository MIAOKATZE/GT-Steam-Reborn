package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment.MainAxis;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
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
    public ModularPanel build(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        return super.build(data, manager, settings).background(CriticalGuiStyle.CARD);
    }

    @Override
    protected ParentWidget<?> createTerminalParentWidget(ModularPanel panel, PanelSyncManager manager) {
        return super.createTerminalParentWidget(panel, manager).background(CriticalGuiStyle.CARD);
    }

    @Override
    protected IWidget createInventoryRow(ModularPanel panel, PanelSyncManager manager) {
        return Flow.row()
            .fullWidth()
            .height(76)
            .childIf(
                multiblock.doesBindPlayerInventory(),
                () -> SlotGroupWidget.playerInventory(
                    (index, slot) -> slot.background(CriticalGuiStyle.SLOT)
                        .hoverBackground(CriticalGuiStyle.SLOT))
                    .marginLeft(4))
            .child(createButtonColumn(panel, manager));
    }

    @Override
    protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager manager) {
        // The loom has no controller storage slot: all material supply belongs to real hatches/buses.
        return Flow.column()
            .width(18)
            .leftRel(1, -2, 1)
            .mainAxisAlignment(MainAxis.END)
            .reverseLayout(true)
            .child(createPowerSwitchButton().name("critical_native_power"))
            .child(createStructureUpdateButton(manager));
    }

    @Override
    protected void registerSyncValues(PanelSyncManager manager) {
        super.registerSyncValues(manager);
        manager.syncValue("critical.native.loom", new BooleanSyncValue(state::isLoomStructureValid));
        manager.syncValue("critical.native.status", new IntSyncValue(state::getStatusCode));
        manager.syncValue("critical.native.reason", new IntSyncValue(state::getReasonCode));
        manager.syncValue("critical.native.detail", new StringSyncValue(state::getStatus));
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
        manager.syncValue("critical.native.batch", new IntSyncValue(state::getBatchProgress));
        manager.syncValue("critical.native.duration", new IntSyncValue(state::getBatchDuration));
        manager.syncValue("critical.native.energy", new LongSyncValue(state::getEnergyStored));
        manager.syncValue("critical.native.capacity", new LongSyncValue(state::getEnergyCapacity));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager manager, ModularPanel panel) {
        BooleanSyncValue loom = manager.findSyncHandler("critical.native.loom", BooleanSyncValue.class);
        IntSyncValue status = manager.findSyncHandler("critical.native.status", IntSyncValue.class);
        StringSyncValue detail = manager.findSyncHandler("critical.native.detail", StringSyncValue.class);
        IntSyncValue kind = manager.findSyncHandler("critical.native.kind", IntSyncValue.class);
        IntSyncValue tier = manager.findSyncHandler("critical.native.tier", IntSyncValue.class);
        IntSyncValue stage = manager.findSyncHandler("critical.native.stage", IntSyncValue.class);
        IntSyncValue placed = manager.findSyncHandler("critical.native.placed", IntSyncValue.class);
        IntSyncValue total = manager.findSyncHandler("critical.native.total", IntSyncValue.class);
        IntSyncValue round = manager.findSyncHandler("critical.native.round", IntSyncValue.class);
        IntSyncValue rounds = manager.findSyncHandler("critical.native.rounds", IntSyncValue.class);
        IntSyncValue batch = manager.findSyncHandler("critical.native.batch", IntSyncValue.class);
        IntSyncValue duration = manager.findSyncHandler("critical.native.duration", IntSyncValue.class);
        LongSyncValue energy = manager.findSyncHandler("critical.native.energy", LongSyncValue.class);
        LongSyncValue capacity = manager.findSyncHandler("critical.native.capacity", LongSyncValue.class);
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
        line(list, () -> tr("status." + status.getValue()) + " · " + detail.getValue());
        line(
            list,
            () -> tr("construction") + " " + stage.getValue() + " · " + placed.getValue() + " / " + total.getValue());
        progress(list, () -> ratio(placed.getValue(), total.getValue()));
        line(list, () -> tr("batch") + " " + batch.getValue() + " / " + duration.getValue() + " t");
        progress(list, () -> ratio(batch.getValue(), duration.getValue()));
        line(list, () -> "EU " + energy.getValue() + " / " + capacity.getValue());
        progress(list, () -> ratio(energy.getValue(), capacity.getValue()));
        line(
            list,
            () -> tr("construction.round") + " "
                + (rounds.getValue() == 0 ? 0 : Math.min(rounds.getValue(), round.getValue() + 1))
                + " / "
                + rounds.getValue());
        line(list, () -> EnumChatFormatting.GRAY + tr("management_hint"));
        return list;
    }

    private static double ratio(long value, long maximum) {
        return maximum <= 0 ? 0 : Math.max(0, Math.min(1, (double) value / maximum));
    }

    private static void progress(ListWidget<IWidget, ?> list, java.util.function.DoubleSupplier value) {
        list.child(
            new ParentWidget<>().background(new CriticalProgressBar(value))
                .fullWidth()
                .height(7)
                .marginBottom(4));
    }

    private static void line(ListWidget<IWidget, ?> list, java.util.function.Supplier<String> text) {
        list.child(
            CriticalGuiStyle.text(IKey.dynamic(text))
                .color(CriticalGuiStyle.TEXT_BODY)
                .fullWidth()
                .marginBottom(2));
    }

    private static String tr(String suffix) {
        return StatCollector.translateToLocal("gtsr.critical." + suffix);
    }
}
