package com.miaokatze.gtsr.common.gui;

import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalTier;

import gregtech.api.enums.GTValues;

/** Shared, bounded construction page for the management terminal and the native GT read-only dialog. */
final class CriticalConstructionView {

    private final GenericSyncValue<CriticalBusinessSnapshot, ?> business;
    private final boolean[] expanded = new boolean[10];
    private static final String[] FAMILIES = { "controller", "equipment", "S", "H", "E", "O", "F", "M", "C", "G" };

    CriticalConstructionView(CriticalMachineState state, PanelSyncManager manager) {
        this(CriticalBusinessSnapshot.register(manager, state, null));
    }

    CriticalConstructionView(GenericSyncValue<CriticalBusinessSnapshot, ?> business) {
        this.business = business;
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
                .background(new CriticalConstructionPreview(this::sample))
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
        line(information, () -> tr("ui.controller_state") + ": " + tr("status." + sample().status()));
        line(
            information,
            () -> tr("ui.journal_owner") + ": " + tr(sample().loomOwner() ? "ui.journal.loom" : "ui.journal.main"));
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
        line(
            information,
            () -> business.getValue()
                .detail());
        line(information, this::nextAction, CriticalGuiStyle.TEXT_ACCENT);
        line(information, () -> tr("view.base_power") + " " + constructionVoltage() + " EU/t");
        line(
            information,
            () -> sample().phase() == 1 ? tr(sample().cellFunded() ? "construction.funded" : "construction.unfunded")
                : "");
        heading(information, () -> tr("construction.next_cost"));
        line(
            information,
            () -> business.getValue()
                .nextItems());
        line(
            information,
            () -> business.getValue()
                .nextFluids());
        line(
            information,
            () -> business.getValue()
                .nextItems()
                .isEmpty()
                && business.getValue()
                    .nextFluids()
                    .isEmpty() ? tr("view.no_pending_cost") : "");
        heading(information, () -> tr("construction.missing"));
        line(
            information,
            () -> business.getValue()
                .missingItems(),
            CriticalGuiStyle.STATE_OFFLINE);
        line(
            information,
            () -> business.getValue()
                .missingFluids(),
            CriticalGuiStyle.STATE_OFFLINE);
        line(
            information,
            () -> business.getValue()
                .missingItems()
                .isEmpty()
                && business.getValue()
                    .missingFluids()
                    .isEmpty() ? tr("construction.supplied") : "",
            CriticalGuiStyle.STATE_ONLINE);
        heading(information, () -> tr("ui.economy.layers"));
        line(information, () -> tr(business.getValue().economyVersion == 2 ? "ui.economy.rc2" : "ui.economy.legacy"));
        line(information, () -> tr("ui.economy.network_unpaid"), CriticalGuiStyle.TEXT_MUTED);
        for (int family = 0; family < FAMILIES.length; family++) {
            final int index = family;
            information.child(
                new ButtonWidget<>().name("critical_economy_family_" + FAMILIES[index])
                    .height(16)
                    .widthRel(1f)
                    .background(CriticalGuiStyle.BUTTON)
                    .hoverBackground(CriticalGuiStyle.HOVER)
                    .overlay(
                        IKey.dynamic(() -> (expanded[index] ? "− " : "+ ") + tr("ui.economy.family." + FAMILIES[index]))
                            .color(CriticalGuiStyle.TEXT_ACCENT))
                    .onMousePressed(button -> {
                        if (button != 0) return false;
                        expanded[index] = !expanded[index];
                        return true;
                    }));
            information.child(
                CriticalGuiStyle.text(IKey.dynamic(() -> familyLines(FAMILIES[index])))
                    .color(CriticalGuiStyle.TEXT_BODY)
                    .fullWidth()
                    .marginBottom(4)
                    .setEnabledIf(w -> expanded[index]));
        }
        page.child(information);
        return page;
    }

    private CriticalConstructionSnapshot sample() {
        return business.getValue().construction;
    }

    private String nextAction() {
        CriticalConstructionSnapshot s = sample();
        return tr("ui.next_action") + ": " + tr("ui.recover." + (!s.enabled() ? 7 : s.reason()));
    }

    private String familyLines(String family) {
        StringBuilder text = new StringBuilder();
        CriticalBusinessSnapshot sample = business.getValue();
        for (int i = 0; i < sample.identityCount(); i++) {
            if (!family.equals(sample.identity(i, 0))) continue;
            if (text.length() > 0) text.append("\n\n");
            text.append(identityLine(i));
        }
        return text.length() == 0 ? tr("view.no_pending_cost") : text.toString();
    }

    private String identityLine(int index) {
        CriticalBusinessSnapshot s = business.getValue();
        if (index >= s.identityCount()) return "";
        return s.identity(index, 2) + " ["
            + s.identity(index, 1)
            + "]\n"
            + tr("ui.economy.demand")
            + " "
            + s.identity(index, 3)
            + " | "
            + tr("ui.economy.installed")
            + " "
            + s.identity(index, 4)
            + "\n"
            + tr("ui.economy.escrow")
            + " "
            + s.identity(index, 5)
            + " | "
            + tr("ui.economy.missing")
            + " "
            + s.identity(index, 6)
            + "\n"
            + tr("ui.economy.next")
            + " "
            + s.identity(index, 7);
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
