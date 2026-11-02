package com.miaokatze.gtsr.common.gui;

import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalTier;

import gregtech.api.enums.GTValues;

/** Shared, bounded construction page for the management terminal and the native GT read-only dialog. */
final class CriticalConstructionView {

    private final GenericSyncValue<CriticalBusinessSnapshot, ?> business;
    private final boolean[] expanded = new boolean[14];
    private static final String[] FAMILIES = { "controller", "equipment", "S", "H", "E", "O", "F", "M", "C", "G" };

    CriticalConstructionView(CriticalMachineState state, PanelSyncManager manager) {
        this(CriticalBusinessSnapshot.register(manager, state, null));
    }

    CriticalConstructionView(GenericSyncValue<CriticalBusinessSnapshot, ?> business) {
        this.business = business;
        // The next debit and the actionable deficit are primary information, not hidden diagnostics.
        expanded[11] = expanded[12] = true;
    }

    ParentWidget<?> create() {
        ParentWidget<?> page = new ParentWidget<>().name("critical_page_construction")
            .size(463, 202);
        ParentWidget<?> drawing = new ParentWidget<>().size(286, 202)
            .background(CriticalGuiStyle.CARD);
        drawing.child(
            IKey.dynamic(() -> tr("view.progress") + " · " + phaseName())
                .color(CriticalGuiStyle.TEXT_ACCENT)
                .asWidget()
                .pos(8, 5)
                .size(270, 12)
                .tooltipBuilder(t -> t.add(phaseName()))
                .tooltipAutoUpdate(true));
        drawing.child(
            IKey.dynamic(() -> percent(sample().progress()))
                .color(CriticalGuiStyle.TEXT_TITLE)
                .scale(1.45f)
                .asWidget()
                .pos(8, 22)
                .size(122, 20));
        drawing.child(
            new ListWidget<>().name("critical_construction_stage_counts")
                .pos(139, 23)
                .size(139, 18)
                .scrollDirection(CriticalGuiStyle.scroll())
                .child(
                    CriticalGuiStyle.text(IKey.dynamic(this::stageCounts))
                        .color(CriticalGuiStyle.TEXT_BODY)
                        .fullWidth()));
        drawing.child(
            new ParentWidget<>().name("critical_construction_preview")
                .pos(6, 46)
                .size(274, 108)
                .background(new CriticalConstructionPreview(business::getValue))
                .tooltipBuilder(
                    t -> t.add(IKey.lang(key(sample().showsFoundation() ? "view.grid_hint" : "view.ghost_hint"))))
                .tooltipAutoUpdate(true));
        drawing.child(
            IKey.dynamic(this::stepTime)
                .color(CriticalGuiStyle.TEXT_BODY)
                .asWidget()
                .pos(8, 157)
                .size(270, 12));
        drawing.child(
            IKey.dynamic(() -> tr(sample().showsFoundation() ? "view.grid_legend" : "view.ghost_legend"))
                .color(CriticalGuiStyle.TEXT_MUTED)
                .asWidget()
                .pos(8, 171)
                .size(270, 10));
        drawing.child(
            new ParentWidget<>().pos(8, 181)
                .size(270, 2)
                .background(new CriticalProgressBar(() -> sample().progress())));
        page.child(drawing);

        ListWidget<IWidget, ?> status = new ListWidget<>().name("critical_construction_priority")
            .pos(292, 0)
            .size(171, 67)
            .padding(7)
            .background(CriticalGuiStyle.CARD)
            .scrollDirection(CriticalGuiStyle.scroll());
        line(status, () -> tr("status." + sample().status()), CriticalGuiStyle.TEXT_TITLE);
        line(
            status,
            () -> sample().status() != 2 && !sample().enabled() ? tr("reason.7")
                : sample().reason() == 0 ? business.getValue()
                    .detail() : tr("reason." + sample().reason()),
            CriticalGuiStyle.TEXT_ACCENT);
        line(status, this::nextAction, CriticalGuiStyle.TEXT_MUTED);
        line(
            status,
            () -> business.getValue()
                .supplyTerraceNotice(),
            CriticalGuiStyle.TEXT_MUTED);
        page.child(status);

        ListWidget<IWidget, ?> information = new ListWidget<>().name("critical_construction_live_details")
            .pos(292, 72)
            .size(171, 130)
            .padding(7)
            .background(CriticalGuiStyle.CARD)
            .scrollDirection(CriticalGuiStyle.scroll());
        disclosure(information, 10, "ui.details.construction");
        ListWidget<IWidget, ?> timing = detailGroup(information, 10);
        line(
            timing,
            () -> tr("ui.journal_owner") + ": " + tr(sample().loomOwner() ? "ui.journal.loom" : "ui.journal.main"));
        line(timing, () -> tr("view.chunks") + " " + sample().chunkCount());
        line(timing, this::activeLocation);
        line(timing, () -> tr("view.base_power") + " " + constructionVoltage() + " EU/t");
        line(
            timing,
            () -> sample().phase() == 1 ? tr(sample().cellFunded() ? "construction.funded" : "construction.unfunded")
                : "");
        disclosure(information, 11, "construction.next_cost");
        ListWidget<IWidget, ?> costs = detailGroup(information, 11);
        line(
            costs,
            () -> business.getValue()
                .nextItems());
        line(
            costs,
            () -> business.getValue()
                .nextFluids());
        line(
            costs,
            () -> business.getValue()
                .nextItems()
                .isEmpty()
                && business.getValue()
                    .nextFluids()
                    .isEmpty() ? tr("view.no_pending_cost") : "");
        disclosure(information, 12, "construction.missing");
        ListWidget<IWidget, ?> missing = detailGroup(information, 12);
        line(
            missing,
            () -> business.getValue()
                .missingItems(),
            CriticalGuiStyle.STATE_OFFLINE);
        line(
            missing,
            () -> business.getValue()
                .missingFluids(),
            CriticalGuiStyle.STATE_OFFLINE);
        line(
            missing,
            () -> business.getValue()
                .missingItems()
                .isEmpty()
                && business.getValue()
                    .missingFluids()
                    .isEmpty() ? tr("construction.supplied") : "",
            CriticalGuiStyle.STATE_ONLINE);
        disclosure(information, 13, "ui.economy.layers");
        ListWidget<IWidget, ?> ledger = detailGroup(information, 13);
        line(ledger, () -> tr(business.getValue().economyVersion == 2 ? "ui.economy.rc2" : "ui.economy.legacy"));
        line(ledger, () -> tr("ui.economy.network_unpaid"), CriticalGuiStyle.TEXT_MUTED);
        for (int family = 0; family < FAMILIES.length; family++) {
            final int index = family;
            ledger.child(
                new ButtonWidget<>().name("critical_economy_family_" + FAMILIES[index])
                    .height(16)
                    .widthRel(1f)
                    .background(CriticalGuiStyle.BUTTON)
                    .hoverBackground(CriticalGuiStyle.HOVER)
                    .overlay(
                        IKey.dynamic(() -> (expanded[index] ? "− " : "+ ") + tr("ui.economy.family." + FAMILIES[index]))
                            .scale(0.8f)
                            .color(CriticalGuiStyle.TEXT_ACCENT))
                    .onMousePressed(button -> {
                        if (button != 0) return false;
                        expanded[index] = !expanded[index];
                        return true;
                    }));
            ledger.child(
                CriticalGuiStyle.text(IKey.dynamic(() -> familyLines(FAMILIES[index])))
                    .color(CriticalGuiStyle.TEXT_BODY)
                    .fullWidth()
                    .marginBottom(4)
                    .setEnabledIf(w -> expanded[index]));
        }
        page.child(information);
        return page;
    }

    /** Handheld layout; the native GT read-only dialog continues to use create(). */
    ParentWidget<?> createTerminalOverview() {
        ParentWidget<?> page = new ParentWidget<>().name("critical_page_construction")
            .size(463, 252);
        ParentWidget<?> drawing = new ParentWidget<>().name("critical_construction_drawing")
            .size(286, 202)
            .background(CriticalGuiStyle.CARD);
        drawing.child(
            CriticalGuiStyle.label(this::phaseName, () -> CriticalGuiStyle.TEXT_ACCENT, Alignment.CenterLeft)
                .name("critical_construction_phase")
                .pos(5, 3)
                .size(276, 16)
                .tooltipBuilder(t -> t.add(phaseName()))
                .tooltipAutoUpdate(true));
        drawing.child(
            IKey.dynamic(() -> percent(sample().progress()))
                .scale(1.25f)
                .color(CriticalGuiStyle.TEXT_TITLE)
                .asWidget()
                .name("critical_construction_percent")
                .pos(9, 24)
                .size(108, 18));
        drawing.child(
            CriticalGuiStyle.label(this::stageCounts, () -> CriticalGuiStyle.TEXT_BODY, Alignment.CenterRight)
                .name("critical_construction_stage_counts")
                .pos(120, 24)
                .size(158, 18)
                .tooltipBuilder(t -> t.add(stageCounts()))
                .tooltipAutoUpdate(true));
        drawing.child(
            new ParentWidget<>().name("critical_construction_preview")
                .pos(6, 48)
                .size(274, 120)
                .background(new CriticalConstructionPreview(business::getValue))
                .tooltipBuilder(t -> t.add(tr(sample().showsFoundation() ? "view.grid_hint" : "view.ghost_hint")))
                .tooltipAutoUpdate(true));
        drawing.child(
            CriticalGuiStyle.label(this::stepTime, () -> CriticalGuiStyle.TEXT_BODY, Alignment.CenterLeft)
                .name("critical_construction_time")
                .pos(5, 174)
                .size(276, 14)
                .tooltipBuilder(t -> t.add(stepTime()))
                .tooltipAutoUpdate(true));
        drawing.child(
            new ParentWidget<>().name("critical_construction_progress_bar")
                .pos(9, 192)
                .size(268, 4)
                .background(new CriticalProgressBar(() -> sample().progress())));
        page.child(drawing);

        ListWidget<IWidget, ?> status = new ListWidget<>().name("critical_construction_priority")
            .pos(292, 0)
            .size(171, 202)
            .padding(8)
            .background(CriticalGuiStyle.CARD)
            .scrollDirection(CriticalGuiStyle.scroll())
            .overlay(CriticalGuiStyle.SCROLL_EDGES);
        terminalLine(status, () -> tr("status." + sample().status()), CriticalGuiStyle.TEXT_TITLE);
        terminalLine(
            status,
            () -> sample().status() != 2 && !sample().enabled() ? tr("reason.7")
                : sample().reason() == 0 ? business.getValue()
                    .detail() : tr("reason." + sample().reason()),
            CriticalGuiStyle.TEXT_ACCENT);
        terminalLine(status, this::nextAction, CriticalGuiStyle.TEXT_BODY);
        terminalLine(
            status,
            () -> tr("ui.journal_owner") + ": " + tr(sample().loomOwner() ? "ui.journal.loom" : "ui.journal.main"),
            CriticalGuiStyle.TEXT_MUTED);
        terminalLine(
            status,
            () -> tr("view.base_power") + " " + constructionVoltage() + " EU/t",
            CriticalGuiStyle.TEXT_MUTED);
        terminalLine(status, this::activeLocation, CriticalGuiStyle.TEXT_MUTED);
        terminalLine(
            status,
            () -> sample().phase() == 1 ? tr(sample().cellFunded() ? "construction.funded" : "construction.unfunded")
                : "",
            CriticalGuiStyle.TEXT_MUTED);
        terminalLine(
            status,
            () -> business.getValue()
                .supplyTerraceNotice(),
            CriticalGuiStyle.TEXT_ACCENT);
        page.child(status);

        ParentWidget<?> materials = new ParentWidget<>().name("critical_construction_material_summary")
            .pos(0, 208)
            .size(463, 44)
            .background(CriticalGuiStyle.CARD);
        materials.child(
            CriticalGuiStyle
                .label(
                    () -> tr(hasDeficit() ? "construction.missing" : "construction.next_cost"),
                    () -> hasDeficit() ? CriticalGuiStyle.STATE_OFFLINE : CriticalGuiStyle.TEXT_MUTED,
                    Alignment.CenterLeft)
                .pos(5, 1)
                .size(450, 13));
        ListWidget<IWidget, ?> materialSummary = new ListWidget<>().name("critical_construction_cost_summary")
            .pos(9, 16)
            .size(445, 23)
            .scrollDirection(CriticalGuiStyle.scroll());
        terminalLine(materialSummary, () -> materialSummary(), CriticalGuiStyle.TEXT_BODY);
        materials.child(materialSummary);
        page.child(materials);
        return page;
    }

    ParentWidget<?> createTerminalMaterials() {
        ParentWidget<?> page = new ParentWidget<>().name("critical_page_materials")
            .size(463, 252);
        for (int side = 0; side < 2; side++) {
            final boolean missing = side == 1;
            ParentWidget<?> card = new ParentWidget<>().name("critical_material_card_" + side)
                .pos(side * 235, 0)
                .size(228, 96)
                .background(CriticalGuiStyle.CARD);
            card.child(
                CriticalGuiStyle
                    .label(
                        () -> tr(missing ? "construction.missing" : "construction.next_cost"),
                        () -> missing ? CriticalGuiStyle.STATE_OFFLINE : CriticalGuiStyle.TEXT_ACCENT,
                        Alignment.CenterLeft)
                    .pos(5, 3)
                    .size(216, 14));
            ListWidget<IWidget, ?> costs = new ListWidget<>()
                .name(missing ? "critical_material_deficit" : "critical_material_next")
                .pos(7, 21)
                .size(214, 68)
                .padding(3)
                .scrollDirection(CriticalGuiStyle.scroll())
                .overlay(CriticalGuiStyle.SCROLL_EDGES);
            terminalLine(
                costs,
                () -> missing ? business.getValue()
                    .missingItems()
                    : business.getValue()
                        .nextItems(),
                CriticalGuiStyle.TEXT_BODY);
            terminalLine(
                costs,
                () -> missing ? business.getValue()
                    .missingFluids()
                    : business.getValue()
                        .nextFluids(),
                CriticalGuiStyle.TEXT_BODY);
            terminalLine(
                costs,
                () -> missing ? hasDeficit() ? "" : tr("construction.supplied")
                    : business.getValue()
                        .nextItems()
                        .isEmpty()
                        && business.getValue()
                            .nextFluids()
                            .isEmpty() ? tr("view.no_pending_cost") : "",
                CriticalGuiStyle.TEXT_MUTED);
            card.child(costs);
            page.child(card);
        }
        ParentWidget<?> ledger = new ParentWidget<>().name("critical_material_ledger_card")
            .pos(0, 102)
            .size(463, 150)
            .background(CriticalGuiStyle.CARD);
        ledger.child(
            CriticalGuiStyle
                .label(() -> tr("ui.economy.layers"), () -> CriticalGuiStyle.TEXT_ACCENT, Alignment.CenterLeft)
                .pos(5, 3)
                .size(450, 14)
                .tooltipBuilder(t -> {
                    t.add(tr(business.getValue().economyVersion == 2 ? "ui.economy.rc2" : "ui.economy.legacy"));
                    t.add(tr("ui.economy.network_unpaid"));
                })
                .tooltipAutoUpdate(true));
        ListWidget<IWidget, ?> families = new ListWidget<>().name("critical_economy_families")
            .pos(7, 22)
            .size(449, 121)
            .padding(3)
            .scrollDirection(CriticalGuiStyle.scroll())
            .overlay(CriticalGuiStyle.SCROLL_EDGES);
        for (int family = 0; family < FAMILIES.length; family++) {
            final int index = family;
            families.child(
                new ButtonWidget<>().name("critical_economy_family_" + FAMILIES[index])
                    .height(16)
                    .fullWidth()
                    .marginBottom(3)
                    .background(CriticalGuiStyle.BUTTON)
                    .hoverBackground(CriticalGuiStyle.HOVER)
                    .overlay(
                        CriticalGuiStyle.fitted(
                            () -> (expanded[index] ? "− " : "+ ") + tr("ui.economy.family." + FAMILIES[index]),
                            () -> CriticalGuiStyle.TEXT_BODY,
                            Alignment.CenterLeft))
                    .onMousePressed(button -> {
                        if (button != 0) return false;
                        expanded[index] = !expanded[index];
                        return true;
                    }));
            families.child(
                CriticalGuiStyle.compactText(IKey.dynamic(() -> familyLines(FAMILIES[index])))
                    .color(CriticalGuiStyle.TEXT_BODY)
                    .fullWidth()
                    .marginBottom(5)
                    .setEnabledIf(w -> expanded[index]));
        }
        ledger.child(families);
        page.child(ledger);
        return page;
    }

    private boolean hasDeficit() {
        return !business.getValue()
            .missingItems()
            .isEmpty()
            || !business.getValue()
                .missingFluids()
                .isEmpty();
    }

    private String materialSummary() {
        CriticalBusinessSnapshot sample = business.getValue();
        String items = hasDeficit() ? sample.missingItems() : sample.nextItems();
        String fluids = hasDeficit() ? sample.missingFluids() : sample.nextFluids();
        return items.isEmpty() && fluids.isEmpty() ? tr("view.no_pending_cost")
            : items + (items.isEmpty() || fluids.isEmpty() ? "" : "\n") + fluids;
    }

    private static void terminalLine(ListWidget<IWidget, ?> list, Supplier<String> text, int color) {
        list.child(
            CriticalGuiStyle.compactText(IKey.dynamic(text))
                .color(color)
                .fullWidth()
                .marginBottom(5)
                .setEnabledIf(
                    w -> !text.get()
                        .isEmpty()));
    }

    private String stageCounts() {
        CriticalConstructionSnapshot s = sample();
        if (s.showsFoundation() && s.foundationScheduleVersion() == 6)
            return tr("view.chunks") + " " + s.completedChunks() + " / " + s.chunkCount();
        return s.showsFoundation() ? tr("view.cells") + " " + s.completedCells() + " / " + s.totalCells()
            : tr("view.blocks") + " " + s.placed() + " / " + s.total();
    }

    private void disclosure(ListWidget<IWidget, ?> list, int index, String label) {
        list.child(
            new ButtonWidget<>().name("critical_details_" + index)
                .height(19)
                .fullWidth()
                .marginBottom(3)
                .background(CriticalGuiStyle.BUTTON)
                .hoverBackground(CriticalGuiStyle.HOVER)
                .overlay(
                    IKey.dynamic(() -> (expanded[index] ? "− " : "+ ") + tr(label))
                        .color(CriticalGuiStyle.TEXT_ACCENT))
                .tooltipBuilder(t -> t.add(IKey.lang(key(label))))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    expanded[index] = !expanded[index];
                    return true;
                }));
    }

    private ListWidget<IWidget, ?> detailGroup(ListWidget<IWidget, ?> list, int index) {
        ListWidget<IWidget, ?> group = new ListWidget<>().fullWidth()
            .coverChildrenHeight()
            .scrollDirection(CriticalGuiStyle.scroll())
            .setEnabledIf(w -> expanded[index]);
        list.child(group);
        return group;
    }

    private CriticalConstructionSnapshot sample() {
        return business.getValue().construction;
    }

    private String stepTime() {
        CriticalConstructionSnapshot s = sample();
        if (s.status() != 1) return "";
        if (s.showsFoundation() && s.phase() == 1)
            return tr("view.cell_time") + " " + seconds(s.cellTicks()) + " / " + seconds(s.cellDuration());
        if (s.showsFoundation() && s.phase() == 2)
            return tr("view.curing_remaining") + " " + clock(s.curingRemaining());
        if (s.showsFoundation() && s.phase() == 4)
            return tr("view.heating_remaining") + " " + clock(s.heatingRemaining());
        return tr("construction.elapsed") + " "
            + seconds(s.constructionTicks())
            + " / "
            + seconds(s.constructionDuration());
    }

    private String nextAction() {
        CriticalConstructionSnapshot s = sample();
        return tr("ui.next_action") + ": " + tr(s.recoveryKey());
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
        if (s.showsFoundation() && s.phase() == 4) return tr("view.phase.heating");
        if (s.threePartConstruction()) {
            String phase = s.stage() <= 3 ? "view.phase.base"
                : s.equipmentStage() ? "view.phase.equipment" : "view.phase.body";
            int part = (s.stage() - 1) % 3 + 1;
            return tr(phase) + " " + Math.max(1, Math.min(3, part)) + "/3";
        }
        return tr(
            s.stage() == 0 ? "view.phase.loom"
                : s.stage() <= 3 ? "view.phase.base" : s.stage() == 5 ? "view.phase.equipment" : "view.phase.body");
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
        return s.loomOwner() || s.phase() == 1 || s.phase() == 2 || s.phase() == 4 ? GTValues.V[9]
            : CriticalTier.byId(s.tier())
                .constructionVoltage();
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
