package com.miaokatze.gtsr.common.gui;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalTier;

import gregtech.api.enums.GTValues;

/** Shared, bounded construction page for the management terminal and the native GT read-only dialog. */
final class CriticalConstructionView {

    private final GenericSyncValue<CriticalConstructionSnapshot, ?> snapshot;
    private final GenericListSyncHandler<ItemStack> nextItems, missingItems;
    private final GenericListSyncHandler<FluidStack> nextFluids, missingFluids;
    private final StringSyncValue detail;

    CriticalConstructionView(CriticalMachineState state, PanelSyncManager manager) {
        snapshot = CriticalConstructionSnapshot.register(manager, state);
        nextItems = items(state::getConstructionNextItemCostsForGui);
        missingItems = items(state::getConstructionMissingItemsForGui);
        nextFluids = fluids(state::getConstructionNextFluidCostsForGui);
        missingFluids = fluids(state::getConstructionMissingFluidsForGui);
        detail = new StringSyncValue(state::getStatus);
        manager.syncValue("critical.constructionView.nextItems", nextItems);
        manager.syncValue("critical.constructionView.missingItems", missingItems);
        manager.syncValue("critical.constructionView.nextFluids", nextFluids);
        manager.syncValue("critical.constructionView.missingFluids", missingFluids);
        manager.syncValue("critical.constructionView.detail", detail);
    }

    ParentWidget<?> create() {
        ParentWidget<?> page = new ParentWidget<>().name("critical_page_construction")
            .size(463, 202);
        ParentWidget<?> drawing = new ParentWidget<>().size(286, 202)
            .background(CriticalGuiStyle.CARD);
        drawing.child(
            IKey.dynamic(this::phaseName)
                .color(CriticalGuiStyle.TEXT_ACCENT)
                .asWidget()
                .pos(8, 5)
                .size(270, 12));
        drawing.child(
            new ParentWidget<>().name("critical_construction_preview")
                .pos(6, 22)
                .size(274, 144)
                .background(new CriticalConstructionPreview(snapshot::getValue))
                .tooltipBuilder(t -> {
                    t.add(IKey.lang(key("view.grid_hint")));
                    t.add(IKey.lang(key("view.ghost_hint")));
                }));
        drawing.child(
            IKey.dynamic(() -> tr(sample().showsFoundation() ? "view.grid_legend" : "view.ghost_legend"))
                .color(CriticalGuiStyle.TEXT_MUTED)
                .scale(0.8f)
                .asWidget()
                .pos(8, 172)
                .size(270, 10));
        drawing.child(
            new ParentWidget<>().pos(8, 189)
                .size(270, 6)
                .background(new CriticalProgressBar(() -> sample().progress())));
        page.child(drawing);

        ListWidget<IWidget, ?> information = new ListWidget<>().name("critical_construction_live_details")
            .pos(292, 0)
            .size(171, 202)
            .padding(7)
            .background(CriticalGuiStyle.CARD)
            .scrollDirection(new VerticalScrollData().cancelScrollEdge(false));
        heading(information, () -> tr("view.progress") + " " + percent(sample().progress()));
        line(information, () -> {
            CriticalConstructionSnapshot s = sample();
            if (s.showsFoundation()) return tr("view.cells") + " " + s.completedCells() + " / " + s.totalCells();
            return tr(s.status() == 3 ? "status.3" : "view.blocks") + " " + s.placed() + " / " + s.total();
        });
        line(information, () -> {
            CriticalConstructionSnapshot s = sample();
            if (s.status() == 3) return "";
            if (s.showsFoundation() && s.phase() == 1)
                return tr("view.cell_time") + " " + seconds(s.cellTicks()) + " / " + seconds(s.cellDuration());
            if (s.showsFoundation() && s.phase() == 2)
                return tr("view.curing_remaining") + " " + clock(s.curingRemaining());
            return tr("construction.elapsed") + " "
                + clock(s.constructionTicks())
                + " / "
                + clock(s.constructionDuration());
        });
        line(information, () -> tr("view.chunks") + " " + sample().chunkCount());
        line(information, this::activeLocation);
        line(
            information,
            () -> !sample().enabled() ? tr("reason.7")
                : sample().reason() == 0 ? "" : tr("reason." + sample().reason()),
            CriticalGuiStyle.TEXT_ACCENT);
        line(information, () -> detail.getValue());
        line(information, () -> tr("view.base_power") + " " + constructionVoltage() + " EU/t");
        line(
            information,
            () -> sample().phase() == 1 ? tr(sample().cellFunded() ? "construction.funded" : "construction.unfunded")
                : "");
        heading(information, () -> tr("construction.next_cost"));
        line(information, () -> itemLines(nextItems));
        line(information, () -> fluidLines(nextFluids));
        line(
            information,
            () -> nextItems.getValue()
                .isEmpty()
                && nextFluids.getValue()
                    .isEmpty() ? tr("view.no_pending_cost") : "");
        heading(information, () -> tr("construction.missing"));
        line(information, () -> itemLines(missingItems), CriticalGuiStyle.STATE_OFFLINE);
        line(information, () -> fluidLines(missingFluids), CriticalGuiStyle.STATE_OFFLINE);
        line(
            information,
            () -> missingItems.getValue()
                .isEmpty()
                && missingFluids.getValue()
                    .isEmpty() ? tr("construction.supplied") : "",
            CriticalGuiStyle.STATE_ONLINE);
        page.child(information);
        return page;
    }

    private CriticalConstructionSnapshot sample() {
        return snapshot.getValue();
    }

    private String phaseName() {
        CriticalConstructionSnapshot s = sample();
        if (s.status() == 0) return tr("view.phase.planned");
        if (s.status() == 3) return tr("status.3");
        if (s.status() == 2) return tr("view.phase.complete");
        if (s.showsFoundation() && s.phase() == 1) return tr("view.phase.pouring");
        if (s.showsFoundation() && s.phase() == 2) return tr("view.phase.curing");
        return tr(s.stage() == 0 ? "view.phase.loom" : s.stage() <= 3 ? "view.phase.base" : "view.phase.body");
    }

    private String activeLocation() {
        CriticalConstructionSnapshot s = sample();
        int chunk = s.activeChunk(), cell = s.activeCell();
        if (!s.showsFoundation() || s.phase() != 1 || chunk < 0 || chunk >= s.chunkCount() || cell < 0) return "";
        return tr("view.active_chunk") + " ["
            + s.chunkX(chunk)
            + ", "
            + s.chunkZ(chunk)
            + "]\n"
            + tr("view.active_cell")
            + " "
            + (cell % 4 + 1)
            + ", "
            + (cell / 4 + 1);
    }

    private long constructionVoltage() {
        CriticalConstructionSnapshot s = sample();
        return s.loomOwner() || s.phase() == 1 || s.phase() == 2 ? GTValues.V[9]
            : CriticalTier.byId(s.tier())
                .constructionVoltage();
    }

    private static void heading(ListWidget<IWidget, ?> list, Supplier<String> text) {
        line(list, text, CriticalGuiStyle.TEXT_ACCENT);
    }

    private static void line(ListWidget<IWidget, ?> list, Supplier<String> text) {
        line(list, text, CriticalGuiStyle.TEXT_BODY);
    }

    private static void line(ListWidget<IWidget, ?> list, Supplier<String> text, int color) {
        list.child(
            CriticalGuiStyle.text(IKey.dynamic(text))
                .color(color)
                .fullWidth()
                .marginBottom(4)
                .setEnabledIf(
                    w -> !text.get()
                        .isEmpty()));
    }

    private static GenericListSyncHandler<ItemStack> items(Supplier<ItemStack[]> getter) {
        return new GenericListSyncHandler<>(() -> Arrays.asList(getter.get()), null, packet -> {
            ItemStack stack = NetworkUtils.readItemStack(packet);
            int count = packet.readInt();
            if (stack != null) stack.stackSize = count;
            return stack;
        }, (packet, stack) -> {
            NetworkUtils.writeItemStack(packet, stack);
            packet.writeInt(stack == null ? 0 : stack.stackSize);
        }, ItemStack::areItemStacksEqual, stack -> stack == null ? null : stack.copy());
    }

    private static GenericListSyncHandler<FluidStack> fluids(Supplier<FluidStack[]> getter) {
        return new GenericListSyncHandler<>(
            () -> Arrays.asList(getter.get()),
            null,
            packet -> ByteBufAdapters.FLUID_STACK.deserialize(packet),
            (packet, stack) -> ByteBufAdapters.FLUID_STACK.serialize(packet, stack),
            FluidStack::isFluidStackIdentical,
            FluidStack::copy);
    }

    private static String itemLines(GenericListSyncHandler<ItemStack> items) {
        StringBuilder text = new StringBuilder();
        for (ItemStack stack : items.getValue()) {
            if (stack == null) continue;
            if (text.length() > 0) text.append('\n');
            text.append(stack.getDisplayName())
                .append(" × ")
                .append(stack.stackSize);
        }
        return text.toString();
    }

    private static String fluidLines(GenericListSyncHandler<FluidStack> fluids) {
        StringBuilder text = new StringBuilder();
        for (FluidStack stack : fluids.getValue()) {
            if (stack == null) continue;
            if (text.length() > 0) text.append('\n');
            text.append(stack.getLocalizedName())
                .append(" × ")
                .append(stack.amount)
                .append(" mB");
        }
        return text.toString();
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100);
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f s", ticks / 20D);
    }

    private static String clock(int ticks) {
        long seconds = Math.max(0, ticks) / 20;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private static String key(String suffix) {
        return "gtsr.critical." + suffix;
    }

    private static String tr(String suffix) {
        return StatCollector.translateToLocal(key(suffix));
    }
}
