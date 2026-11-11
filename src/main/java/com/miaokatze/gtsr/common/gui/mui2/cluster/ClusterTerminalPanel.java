// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.gui.mui2.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.menu.AbstractMenuButton;
import com.cleanroommc.modularui.widgets.menu.Menu;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;
import com.miaokatze.gtsr.common.gui.mui2.TerminalViewport;
import com.miaokatze.gtsr.common.machine.cluster.ChainLink;
import com.miaokatze.gtsr.common.machine.cluster.ClusterChainFSM;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.ClusterUnitStatus;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalActions;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalData;
import com.miaokatze.gtsr.common.util.GtsrNumFormat;
import com.miaokatze.miaokatzecore.ui.mui2.ScrollEdges;

/** Native MUI2 widgets. Finite pools retain identity, scroll state and draft state. */
public final class ClusterTerminalPanel {

    public static final int WIDTH = 496;
    public static final int HEIGHT = 330;
    private static final int PAGE_WIDTH = 480;
    private static final int PAGE_HEIGHT = 206;
    private static final String[] TABS = { "topology", "links", "boosters", "stats", "perf" };
    private static final ChainLink[] LINKS = ChainLink.values();

    private ClusterTerminalPanel() {}

    public static ModularPanel build(ClusterTerminalFactory.ClusterGuiData data,
        MTESteamMineralLogisticsCluster machine, PanelSyncManager manager) {
        ClusterSessionSyncHandler session = new ClusterSessionSyncHandler(data, machine);
        manager.syncValue(ClusterSessionSyncHandler.SYNC_KEY, session);
        View view = new View(session);
        PagedWidget.Controller controller = new PagedWidget.Controller();
        PagedWidget<?> pages = new PagedWidget<>().name("cluster_pages")
            .pos(8, 100)
            .size(PAGE_WIDTH, PAGE_HEIGHT)
            .controller(controller)
            .initialPage(data.initialPage);
        pages.addPage(topology(session));
        pages.addPage(links(session, controller));
        pages.addPage(boosters(session, view));
        pages.addPage(stats(session, view));
        pages.addPage(performance(session));
        ParentWidget<?> canvas = new ParentWidget<>().name("cluster_canvas")
            .pos(0, 0)
            .size(WIDTH, HEIGHT)
            .background(TerminalUiStyle.CLUSTER_PANEL)
            .onUpdateListener(widget -> {
                session.clientTick();
                if (manager.isClient()) view.update();
            })
            .child(
                text("cluster_title", () -> tr("gtsr.cluster.mui2.title")).pos(10, 7)
                    .size(290, 13)
                    .color(GtsrGuiPalette.TEXT_TITLE))
            .child(
                new ParentWidget<>().name("cluster_tier_badge")
                    .pos(306, 11)
                    .size(5)
                    .background(
                        new DynamicDrawable(
                            () -> new Rectangle().color(tierColor(session.integer(ClusterTerminalData.KEY_TIER, -1))))))
            .child(
                text("cluster_tier", () -> tier(session.integer(ClusterTerminalData.KEY_TIER, -1))).pos(316, 7)
                    .size(90, 13)
                    .scale(.8f))
            .child(
                text("cluster_status", () -> status(session)).pos(10, 23)
                    .size(394, 10)
                    .scale(.75f)
                    .color(() -> statusColor(session)))
            .child(
                button(
                    "cluster_power",
                    () -> tr(enabled(session) ? "gtsr.gui.cluster.power.on" : "gtsr.gui.cluster.power.off"),
                    () -> session.submit(ClusterTerminalActions.TOGGLE_POWER, -1, new byte[0])).pos(414, 7)
                        .size(72, 22)
                        .addTooltipLine(IKey.lang("gtsr.gui.cluster.power.tip")))
            .child(pages)
            .child(
                text("cluster_receipt", () -> footer(session)).pos(8, 310)
                    .size(PAGE_WIDTH, 14)
                    .scale(.75f)
                    .padding(4, 0)
                    .background(
                        new DynamicDrawable(
                            () -> alert(session) ? TerminalUiStyle.CHIP_ACTIVE : TerminalUiStyle.CHIP_NORMAL))
                    .color(() -> receiptColor(session))
                    .tooltipBuilder(tooltip -> tooltip.addLine(IKey.str(tr(session.receipt()))))
                    .tooltipAutoUpdate(true));
        String[] cardKeys = { ClusterTerminalData.KEY_STEAM, ClusterTerminalData.KEY_LUBE, ClusterTerminalData.KEY_HEAT,
            ClusterTerminalData.KEY_THRU };
        String[] cardLabels = { "gtsr.cluster.mui2.steam", "gtsr.cluster.mui2.lube", "gtsr.cluster.mui2.heat",
            "gtsr.cluster.mui2.throughput" };
        for (int i = 0; i < 4; i++) {
            final int card = i;
            canvas.child(
                new ParentWidget<>().name("cluster_card_" + i)
                    .pos(8 + i * 121, 38)
                    .size(117, 32)
                    .background(new Rectangle().color(0xff26262b))
                    .child(
                        text("cluster_card_label_" + i, () -> tr(cardLabels[card])).pos(5, 3)
                            .size(107, 9)
                            .scale(.7f)
                            .color(GtsrGuiPalette.TEXT_MUTED))
                    .child(
                        text("cluster_card_value_" + i, () -> cardValue(session, card, cardKeys[card])).color(
                            () -> card < 2 && (session.integer(ClusterTerminalData.KEY_SUPPLY, 0) & (1 << card)) != 0
                                ? 0xffff6666
                                : card == 2 ? GtsrGuiPalette.TEXT_ACCENT : GtsrGuiPalette.TEXT_WHITE)
                            .pos(5, 13)
                            .size(107, 12)
                            .scale(.85f))
                    .child(
                        new ParentWidget<>().name("cluster_heat_bar_" + i)
                            .pos(5, 27)
                            .size(107, 3)
                            .background(new ClusterMeter(() -> session.integer(ClusterTerminalData.KEY_HEAT, 0) / 100d))
                            .setEnabledIf(widget -> card == 2))
                    .tooltipBuilder(tooltip -> {
                        tooltip
                            .addLine(IKey.str(tr(cardLabels[card]) + ": " + cardValue(session, card, cardKeys[card])));
                        if (card == 3) tooltip.addLine(
                            IKey.lang(
                                "gtsr.cluster.gui.card.thru.total",
                                GtsrNumFormat.grouped(
                                    session.snapshot() == null ? 0L
                                        : session.snapshot()
                                            .getLong(ClusterTerminalData.KEY_TOTAL, 0L))));
                        if (card < 2 && (session.integer(ClusterTerminalData.KEY_SUPPLY, 0) & (1 << card)) != 0)
                            tooltip.addLine(
                                IKey.lang(
                                    card == 0 ? "gtsr.cluster.gui.card.steam.short"
                                        : "gtsr.cluster.gui.card.lube.short"));
                    })
                    .tooltipAutoUpdate(true));
        }
        for (int i = 0; i < 5; i++) {
            final int page = i;
            canvas.child(
                new PageButton(i, controller).name("cluster_tab_" + i)
                    .pos(8 + i * 97, 75)
                    .size(92, 20)
                    .background(
                        new DynamicDrawable(
                            () -> controller.isInitialised() && controller.getActivePageIndex() == page
                                ? TerminalUiStyle.TAB_ACTIVE
                                : TerminalUiStyle.TAB_INACTIVE))
                    .hoverBackground(TerminalUiStyle.TAB_ACTIVE)
                    .overlay(
                        IKey.lang("gtsr.cluster.gui.tab." + TABS[page])
                            .color(
                                () -> controller.isInitialised() && controller.getActivePageIndex() == page
                                    ? GtsrGuiPalette.TEXT_ACCENT
                                    : GtsrGuiPalette.TEXT_MUTED)
                            .scale(0.8f))
                    .addTooltipLine(IKey.lang("gtsr.cluster.gui.tab." + TABS[page])));
        }
        return ModularPanel.defaultPanel(ClusterTerminalFactory.PANEL_NAME, WIDTH, HEIGHT)
            .background(com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY)
            .child(
                TerminalViewport.wrapExact("cluster_viewport", canvas, WIDTH, HEIGHT)
                    .pos(0, 0)
                    .sizeRel(1f));
    }

    private static ParentWidget<?> topology(ClusterSessionSyncHandler session) {
        ParentWidget<?> page = page("cluster_topology");
        page.child(
            text("cluster_topology_summary", () -> topologySummary(session)).pos(0, 0)
                .size(PAGE_WIDTH, 14)
                .scale(.75f));
        String[] pads = { "work", "boost", "logi" };
        for (int pad = 0; pad < 3; pad++) {
            final int column = pad;
            page.child(
                text("cluster_topology_header_" + pad, () -> tr("gtsr.cluster.gui.topo.pad." + pads[column]))
                    .pos(34 + pad * 146, 17)
                    .size(142, 11)
                    .scale(.75f)
                    .color(GtsrGuiPalette.TEXT_MUTED));
        }
        ClusterList list = list("cluster_topology_list", 0, 31, PAGE_WIDTH, 153);
        for (int segment = 19; segment >= 0; segment--) {
            final int seg = segment;
            ParentWidget<?> row = new ParentWidget<>().name("cluster_segment_" + seg)
                .widthRel(1)
                .height(28)
                .setEnabledIf(widget -> seg < Math.max(1, session.integer(ClusterTerminalData.KEY_SEGMENTS, 0) + 1));
            row.child(
                text(
                    "cluster_segment_label_" + seg,
                    () -> seg == 0 ? tr("gtsr.cluster.gui.topo.layer.base")
                        : String.format(tr("gtsr.cluster.gui.topo.layer.ext"), seg)).pos(0, 6)
                            .size(32, 14)
                            .scale(.75f));
            for (int pad = 0; pad < 3; pad++) {
                final int slot = segment * 3 + pad;
                row.child(
                    new ParentWidget<>().name("cluster_topology_slot_" + slot)
                        .pos(34 + pad * 146, 0)
                        .size(142, 28)
                        .background(IDrawable.EMPTY)
                        .child(
                            text("cluster_topology_name_" + slot, () -> type(session.topo(slot, 0))).pos(2, 2)
                                .size(128, 11)
                                .scale(.8f))
                        .child(
                            new ParentWidget<>().name("cluster_topology_tier_" + slot)
                                .pos(134, 4)
                                .size(4)
                                .background(
                                    new DynamicDrawable(() -> new Rectangle().color(tierColor(session.topo(slot, 1)))))
                                .setEnabledIf(widget -> session.topo(slot, 0) != 0 && session.topo(slot, 0) != 255))
                        .child(
                            text(
                                "cluster_topology_state_" + slot,
                                () -> session.topo(slot, 0) == 0 ? "" : state(session, slot))
                                    .color(
                                        () -> session.topo(slot, 3) > 0 && session.topo(slot, 3) != 255
                                            ? GtsrGuiPalette.STATE_OFFLINE
                                            : runColor(session, slot))
                                    .pos(2, 14)
                                    .size(136, 10)
                                    .scale(.75f))
                        .child(
                            new ParentWidget<>().name("cluster_topology_bar_" + slot)
                                .pos(1, 25)
                                .size(140, 2)
                                .background(new DynamicDrawable(() -> new Rectangle().color(runColor(session, slot)))))
                        .tooltipBuilder(tooltip -> {
                            tooltip.addLine(IKey.str(type(session.topo(slot, 0))));
                            tooltip.addLine(IKey.str(tier(session.topo(slot, 1))));
                            tooltip.addLine(IKey.str(state(session, slot)));
                            int link = session.topo(slot, 4);
                            if (link >= 0 && link < LINKS.length) tooltip.addLine(
                                IKey.str(
                                    String.format(tr("gtsr.cluster.gui.topo.linked"), tr(LINKS[link].getLangKey()))));
                            tooltip.addLine(IKey.lang("gtsr.cluster.mui2.slot", slot));
                        })
                        .tooltipAutoUpdate(true));
            }
            list.child(row);
        }
        page.child(list);
        ParentWidget<?> legend = new ParentWidget<>().name("cluster_topology_legend")
            .pos(0, 190)
            .size(PAGE_WIDTH, 16);
        for (int i = 0; i < 6; i++) {
            final ClusterUnitStatus value = ClusterUnitStatus.values()[i];
            legend.child(
                new ParentWidget<>().pos(i * 80, 5)
                    .size(5)
                    .background(new Rectangle().color(0xff000000 | value.getColorRgb())));
            legend.child(
                text("cluster_legend_" + i, () -> tr(value.getLangKey())).pos(i * 80 + 8, 1)
                    .size(70, 14)
                    .scale(.7f)
                    .color(GtsrGuiPalette.TEXT_MUTED));
        }
        return page.child(legend);
    }

    private static ParentWidget<?> links(ClusterSessionSyncHandler session, PagedWidget.Controller controller) {
        ParentWidget<?> page = page("cluster_links");
        page.child(
            new LogisticsMenu(session).pos(0, 0)
                .size(260, 20));
        page.child(
            button("cluster_batch_toggle", () -> batchLabel(session), session::toggleBatchMode).pos(268, 0)
                .size(212, 20)
                .tooltipBuilder(tooltip -> {
                    tooltip.addLine(IKey.lang("gtsr.cluster.mui2.batch.tip"));
                    tooltip.addLine(IKey.str(batchSummary(session)));
                })
                .tooltipAutoUpdate(true));
        page.child(
            text("cluster_chain_banner", () -> chainBanner(session)).pos(0, 24)
                .size(PAGE_WIDTH, 12)
                .scale(.75f));
        page.child(
            text("cluster_available_title", () -> tr("gtsr.cluster.gui.link.avail")).pos(0, 39)
                .size(174, 12)
                .scale(.8f)
                .color(GtsrGuiPalette.TEXT_ACCENT));
        page.child(
            text("cluster_chain_title", () -> tr("gtsr.cluster.gui.link.chain")).pos(184, 39)
                .size(130, 12)
                .scale(.8f)
                .color(GtsrGuiPalette.TEXT_ACCENT));
        page.child(text("cluster_chain_flow", () -> {
            ClusterDraftState draft = session.draft();
            return draft == null ? "—"
                : draft.ordinals.size() + "/16 · " + tr("gtsr.cluster.mui2.fsm") + " " + (draft.valid() ? "✔" : "✖");
        }).pos(316, 39)
            .size(164, 12)
            .scale(.75f)
            .textAlign(Alignment.CenterRight)
            .color(
                () -> session.draft() != null && session.draft()
                    .valid() ? GtsrGuiPalette.STATE_ONLINE : GtsrGuiPalette.STATE_OFFLINE));
        ClusterList available = list("cluster_available_links", 0, 54, 174, 126);
        for (ChainLink link : LINKS) {
            available.child(button("cluster_append_" + link.ordinal(), () -> availableLinkLabel(session, link), () -> {
                ClusterDraftState draft = session.draft();
                if (draft != null) draft.append(link.ordinal());
            }).widthRel(1)
                .height(32)
                .padding(5, 4)
                .overlay(
                    IKey.dynamic(() -> availableLinkLabel(session, link))
                        .alignment(Alignment.TopLeft)
                        .scale(.8f)
                        .shadow(false)
                        .color(GtsrGuiPalette.TEXT_BODY))
                .tooltipBuilder(tooltip -> {
                    tooltip.addLine(IKey.lang("gtsr.cluster.mui2.add_step"));
                    tooltip.addLine(IKey.lang(link.getLangKey()));
                    tooltip.addLine(
                        IKey.lang(
                            "gtsr.cluster.mui2.link_values",
                            link.getBaseSecondsPrecise(),
                            link.getBaseSteamLps(),
                            lock(session, link.ordinal(), 1)));
                    tooltip.addLine(IKey.lang(mediumKey(link)));
                    int kind = lock(session, link.ordinal(), 0);
                    if (kind != 0) tooltip.addLine(IKey.lang("gtsr.cluster.mui2.lock", kind));
                    if (link == ChainLink.CRUSH) tooltip.addLine(IKey.str(crushNote(session)));
                })
                .tooltipAutoUpdate(true));
        }
        page.child(available);
        ClusterList chain = list("cluster_draft_list", 184, 54, 296, 100);
        chain.child(
            text(
                "cluster_draft_empty",
                () -> tr(
                    session.draft() == null ? "gtsr.cluster.gui.link.unit.none" : "gtsr.cluster.gui.link.chain.empty"))
                        .widthRel(1)
                        .height(34)
                        .padding(5)
                        .scale(.8f)
                        .color(GtsrGuiPalette.TEXT_MUTED)
                        .setEnabledIf(widget -> session.draft() == null || session.draft().ordinals.isEmpty()));
        for (int i = 0; i < 16; i++) {
            final int index = i;
            ParentWidget<?> row = new ParentWidget<>().name("cluster_draft_step_" + index)
                .widthRel(1)
                .height(34)
                .background(
                    new DynamicDrawable(
                        () -> session.draft() != null && session.draft().peak == index
                            ? new Rectangle().color(0x30c87e3b)
                            : IDrawable.EMPTY))
                .setEnabledIf(widget -> session.draft() != null && index < session.draft().ordinals.size());
            row.child(
                text("cluster_draft_number_" + index, () -> (index + 1) + ".").pos(4, 3)
                    .size(17, 12)
                    .scale(.8f)
                    .color(GtsrGuiPalette.TEXT_ACCENT));
            row.child(text("cluster_draft_label_" + index, () -> {
                ClusterDraftState draft = session.draft();
                return draft == null || index >= draft.ordinals.size() ? ""
                    : tr(LINKS[draft.ordinals.get(index)].getLangKey());
            }).pos(23, 2)
                .size(100, 20)
                .scale(.8f)
                .color(() -> failedDraftStep(session, index) ? GtsrGuiPalette.STATE_OFFLINE : GtsrGuiPalette.TEXT_BODY)
                .tooltipBuilder(tooltip -> {
                    ClusterDraftState draft = session.draft();
                    if (draft != null && index < draft.ordinals.size()) {
                        tooltip.addLine(IKey.lang(LINKS[draft.ordinals.get(index)].getLangKey()));
                        tooltip.addLine(IKey.str(draftStepTime(session, index)));
                        String note = draftStepNote(session, index);
                        if (!note.isEmpty()) tooltip.addLine(IKey.str(note));
                    }
                })
                .tooltipAutoUpdate(true));
            row.child(
                text("cluster_draft_time_" + index, () -> draftStepTime(session, index)).pos(126, 3)
                    .size(45, 12)
                    .scale(.75f)
                    .color(GtsrGuiPalette.STATE_ONLINE));
            row.child(
                button(
                    "cluster_move_up_" + index,
                    () -> "↑",
                    () -> {
                        if (session.draft() != null) session.draft()
                            .move(index, -1);
                    }).pos(174, 6)
                        .size(20, 20)
                        .addTooltipLine(IKey.lang("gtsr.cluster.mui2.move_up")));
            row.child(
                button(
                    "cluster_move_down_" + index,
                    () -> "↓",
                    () -> {
                        if (session.draft() != null) session.draft()
                            .move(index, 1);
                    }).pos(196, 6)
                        .size(20, 20)
                        .addTooltipLine(IKey.lang("gtsr.cluster.mui2.move_down")));
            row.child(
                button(
                    "cluster_remove_" + index,
                    () -> "§c×",
                    () -> {
                        if (session.draft() != null) session.draft()
                            .remove(index);
                    }).pos(218, 6)
                        .size(20, 20)
                        .addTooltipLine(IKey.lang("gtsr.cluster.mui2.remove_step")));
            row.child(
                button(
                    "cluster_peak_" + index,
                    () -> session.draft() != null && session.draft().peak == index
                        ? "§6◆ " + tr("gtsr.cluster.mui2.peak")
                        : "◇ " + tr("gtsr.cluster.mui2.peak"),
                    () -> {
                        if (session.draft() != null) session.draft()
                            .peak(index);
                    }).pos(240, 6)
                        .size(42, 20)
                        .addTooltipLine(IKey.lang("gtsr.terminal.le.chains.peak", index + 1)));
            row.child(
                text("cluster_draft_note_" + index, () -> draftStepNote(session, index)).pos(23, 24)
                    .size(148, 8)
                    .scale(.65f)
                    .color(GtsrGuiPalette.TEXT_ACCENT));
            chain.child(row);
        }
        page.child(chain);
        page.child(
            list("cluster_flow_diagram", 184, 158, 296, 22).padding(6, 6, 2, 2)
                .child(
                    new PerformanceText(IKey.dynamic(() -> flow(session.draft()))).name("cluster_flow_text")
                        .widthRel(1)
                        .padding(0, 0, 3, 3)
                        .scale(.75f)
                        .color(GtsrGuiPalette.TEXT_BODY)
                        .textAlign(Alignment.TopLeft)));
        page.child(
            button("cluster_link_performance", () -> tr("gtsr.cluster.gui.link.perf"), () -> controller.setPage(4))
                .pos(0, 186)
                .size(174, 20)
                .tooltipBuilder(tooltip -> {
                    tooltip.addLine(
                        IKey.lang(
                            "gtsr.cluster.mui2.performance_preview",
                            String
                                .format(Locale.ROOT, "%.2f", session.integer(ClusterTerminalData.KEY_F_TIME, 0) / 100d),
                            session.integer(ClusterTerminalData.KEY_F_PAR, 0),
                            String.format(
                                Locale.ROOT,
                                "%.2f",
                                session.integer(ClusterTerminalData.KEY_F_THRU, 0) / 100d)));
                    for (ClusterFormulaText.Line line : ClusterFormulaText
                        .parse(session.string(ClusterTerminalData.KEY_F_FORMULA))) tooltip.addLine(IKey.str(line.text));
                })
                .tooltipAutoUpdate(true));
        page.child(
            button("cluster_chain_revert", () -> tr("gtsr.cluster.mui2.revert"), session::discardDraft).pos(184, 186)
                .size(94, 20));
        page.child(
            button(
                "cluster_chain_clear",
                () -> tr("gtsr.cluster.gui.link.chain.clear"),
                () -> {
                    if (session.draft() != null) session.draft()
                        .clear();
                }).pos(282, 186)
                    .size(94, 20));
        page.child(
            button("cluster_chain_save", () -> tr("gtsr.cluster.gui.link.chain.save"), session::save).pos(380, 186)
                .size(100, 20)
                .addTooltipLine(IKey.lang("gtsr.cluster.mui2.save_tip")));
        return page;
    }

    private static final class LogisticsMenu extends AbstractMenuButton<LogisticsMenu> {

        private final ClusterSessionSyncHandler session;

        LogisticsMenu(ClusterSessionSyncHandler session) {
            super("cluster_logistics_dropdown");
            this.session = session;
            openOnHover = false;
            background(TerminalUiStyle.CHIP_NORMAL.withColorOverride(0xFFFFFFFF));
            hoverBackground(TerminalUiStyle.CHIP_ACTIVE.withColorOverride(0xFFFFFFFF));
            padding(5, 0);
            overlay(
                IKey.dynamic(
                    () -> session.topo(session.selectedSlot(), 0) == 13 ? tr("gtsr.cluster.mui2.logistics") + " #"
                        + session.selectedSlot() / 3
                        + " · "
                        + unitState(session, session.selectedSlot())
                        + " ▾" : tr("gtsr.cluster.gui.link.unit.none") + " ▾")
                    .alignment(Alignment.CenterLeft)
                    .scale(.75f)
                    .shadow(false)
                    .color(GtsrGuiPalette.TEXT_BODY));
        }

        @Override
        protected Menu<?> createMenu() {
            ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> options = new ListWidget<>().widthRel(1)
                .maxSize(140);
            for (int segment = 0; segment < 20; segment++) {
                final int slot = segment * 3 + 2;
                options.child(
                    button(
                        "cluster_dropdown_unit_" + slot,
                        () -> tr("gtsr.cluster.mui2.logistics") + " #" + slot / 3 + " · " + unitState(session, slot),
                        () -> {
                            session.select(slot);
                            closeMenu(false);
                        }).widthRel(1)
                            .height(14)
                            .setEnabledIf(widget -> session.topo(slot, 0) == 13));
            }
            options.child(
                text("cluster_dropdown_empty", () -> tr("gtsr.cluster.gui.link.unit.none")).widthRel(1)
                    .height(14)
                    .scale(.75f)
                    .setEnabledIf(widget -> {
                        for (int segment = 0; segment < 20; segment++)
                            if (session.topo(segment * 3 + 2, 0) == 13) return false;
                        return true;
                    }));
            return new Menu<>().width(260)
                .coverChildrenHeight()
                .background(TerminalUiStyle.CARD.withColorOverride(0xFFFFFFFF))
                .child(options);
        }
    }

    private static String availableLinkLabel(ClusterSessionSyncHandler session, ChainLink link) {
        int count = 0;
        ClusterDraftState draft = session.draft();
        if (draft != null) for (int ordinal : draft.ordinals) if (ordinal == link.ordinal()) count++;
        String first = "+ " + tr(link.getLangKey()) + (count == 0 ? "" : "  §a×" + count);
        String second = String.format(Locale.ROOT, "%.2fs", link.getBaseSecondsPrecise()) + " · "
            + link.getBaseSteamLps()
            + " L/s";
        int kind = lock(session, link.ordinal(), 0);
        first += kind == 0 ? " §a✔" : " §c✖";
        return first + "\n§r" + second;
    }

    private static String draftStepTime(ClusterSessionSyncHandler session, int index) {
        ClusterDraftState draft = session.draft();
        if (draft == null || index >= draft.ordinals.size()) return "";
        int ordinal = draft.ordinals.get(index);
        int tier = Math.max(0, Math.min(3, session.integer(ClusterTerminalData.KEY_TIER, 0)));
        double seconds = LINKS[ordinal].getBaseSecondsPrecise() * ClusterParams.TIER_TIME_FACTOR[tier]
            / Math.max(1, lock(session, ordinal, 1));
        return String.format(Locale.ROOT, "%.2fs", seconds);
    }

    private static String draftStepNote(ClusterSessionSyncHandler session, int index) {
        ClusterDraftState draft = session.draft();
        if (draft == null || index >= draft.ordinals.size() || LINKS[draft.ordinals.get(index)] != ChainLink.CRUSH)
            return "";
        return crushNote(session);
    }

    private static String crushNote(ClusterSessionSyncHandler session) {
        int tier = session.integer(ClusterTerminalData.KEY_TIER, 0);
        double multiplier = tier >= 2 ? ClusterParams.CRUSH_BYPRODUCT_MULT_HIGH_TIER
            : tier == 1 ? ClusterParams.CRUSH_BYPRODUCT_MULT_STEEL : ClusterParams.CRUSH_BYPRODUCT_MULT_NORMAL;
        return multiplier >= 1 ? tr("gtsr.gui.cluster.link.crush.byproduct_none")
            : String.format(tr("gtsr.gui.cluster.link.crush.byproduct_debuff"), Math.round((1 - multiplier) * 100));
    }

    private static ParentWidget<?> boosters(ClusterSessionSyncHandler session, View view) {
        ParentWidget<?> page = page("cluster_boosters");
        page.child(
            text("cluster_booster_title", () -> tr("gtsr.cluster.gui.boost.title")).pos(0, 0)
                .size(PAGE_WIDTH, 12)
                .scale(.8f)
                .color(GtsrGuiPalette.TEXT_ACCENT)
                .addTooltipLine(IKey.lang("gtsr.cluster.gui.boost.rule.stack")));
        int[] columns = { 4, 140, 4, 158, 214, 292 };
        int[] widths = { 134, 68, 150, 310, 72, 176 };
        String[] headers = { "module", "tier", "fluid", "supply", "gain", "status" };
        for (int col : new int[] { 0, 1, 4, 5 }) {
            final int column = col;
            page.child(
                text("cluster_booster_header_" + col, () -> tr("gtsr.cluster.gui.boost.col." + headers[column]))
                    .pos(columns[col], 13)
                    .size(widths[col], 10)
                    .scale(.7f)
                    .color(GtsrGuiPalette.TEXT_MUTED));
        }
        ClusterList list = list("cluster_booster_list", 0, 25, PAGE_WIDTH, 100);
        list.child(
            text("cluster_booster_empty", () -> tr("gtsr.cluster.gui.boost.empty.hint")).widthRel(1)
                .height(42)
                .padding(5)
                .scale(.8f)
                .color(GtsrGuiPalette.TEXT_MUTED)
                .setEnabledIf(widget -> view.boosterRows.isEmpty()));
        for (int i = 0; i < 20; i++) {
            final int index = i;
            ParentWidget<?> row = new ParentWidget<>().name("cluster_booster_row_" + i)
                .widthRel(1)
                .height(32)
                .setEnabledIf(widget -> index < view.boosterRows.size())
                .background(
                    new DynamicDrawable(
                        () -> new Rectangle().color(view.boosterActive(index) ? 0xff26262b : 0xff3a1e1e)))
                .tooltipBuilder(tooltip -> {
                    tooltip.addLine(IKey.str(view.booster(index)));
                    tooltip.addLine(IKey.str(view.boosterFormula(index)));
                })
                .tooltipAutoUpdate(true);
            for (int col = 0; col < 6; col++) {
                final int column = col;
                row.child(
                    text("cluster_booster_cell_" + i + "_" + col, () -> view.boosterCell(index, column))
                        .pos(columns[col], col == 2 || col == 3 ? 18 : 3)
                        .size(widths[col], 11)
                        .scale(col == 2 || col == 3 ? .7f : .8f)
                        .color(
                            () -> column == 5
                                ? view.boosterActive(index) ? GtsrGuiPalette.STATE_ONLINE : GtsrGuiPalette.STATE_OFFLINE
                                : GtsrGuiPalette.TEXT_BODY));
            }
            list.child(row);
        }
        page.child(list);
        page.child(
            text(
                "cluster_booster_rule_state",
                () -> view.boosterRows.isEmpty() ? tr("gtsr.cluster.gui.boost.rule.none")
                    : !enabled(session) ? tr("gtsr.cluster.gui.boost.empty.off")
                        : view.summary[7] > 0 ? String.format(tr("gtsr.cluster.gui.boost.rule.fail"), view.summary[7])
                            : tr("gtsr.cluster.gui.boost.rule.ok")).pos(0, 127)
                                .size(PAGE_WIDTH, 11)
                                .scale(.75f)
                                .color(
                                    () -> view.summary[7] > 0 ? GtsrGuiPalette.STATE_OFFLINE
                                        : GtsrGuiPalette.TEXT_MUTED)
                                .addTooltipLine(IKey.lang("gtsr.cluster.gui.boost.rule.stack")));
        String[] summaries = { "speed", "parallel", "primary", "secondary", "saver", "mult" };
        for (int i = 0; i < summaries.length; i++) {
            final int index = i;
            page.child(
                new ParentWidget<>().name("cluster_booster_card_" + i)
                    .pos((i % 3) * 162, 140 + (i / 3) * 34)
                    .size(156, 30)
                    .background(new Rectangle().color(0xff26262b))
                    .child(
                        text(
                            "cluster_booster_summary_label_" + i,
                            () -> tr("gtsr.cluster.gui.boost.sum." + summaries[index])).pos(5, 3)
                                .size(146, 10)
                                .scale(.7f)
                                .color(GtsrGuiPalette.TEXT_ACCENT))
                    .child(
                        text("cluster_booster_summary_" + i, () -> view.summary(index)).pos(5, 15)
                            .size(146, 13)
                            .scale(.95f)
                            .color(GtsrGuiPalette.TEXT_WHITE))
                    .tooltipBuilder(tooltip -> {
                        tooltip.addLine(IKey.str(view.summaryNote(index)));
                        tooltip.addLine(IKey.lang("gtsr.cluster.gui.boost.rule.stack"));
                    })
                    .tooltipAutoUpdate(true));
        }
        return page;
    }

    private static ParentWidget<?> stats(ClusterSessionSyncHandler session, View view) {
        ParentWidget<?> page = page("cluster_stats");
        page.child(
            button(
                "cluster_clear_stats",
                () -> tr(session.clearArmed() ? "gtsr.terminal.stats.clear.confirm" : "gtsr.terminal.stats.clear"),
                session::clearStats).pos(364, 0)
                    .size(116, 20)
                    .background(
                        new DynamicDrawable(
                            () -> session.clearArmed() ? TerminalUiStyle.CHIP_ACTIVE
                                : TerminalUiStyle.BUTTON.withColorOverride(0xFFFFFFFF))));
        page.child(
            text("cluster_stats_heading", () -> tr("gtsr.terminal.stats.title")).pos(0, 0)
                .size(354, 20)
                .scale(.8f)
                .color(GtsrGuiPalette.TEXT_ACCENT));
        String[] ledgers = { "in", "out", "bonus" };
        for (int ledger = 0; ledger < 3; ledger++) {
            final int col = ledger;
            page.child(
                text("cluster_stats_title_" + col, () -> tr("gtsr.terminal.stats." + ledgers[col])).pos(col * 162, 24)
                    .size(156, 12)
                    .scale(.75f)
                    .color(GtsrGuiPalette.TEXT_LABEL));
            ClusterList list = list("cluster_stats_list_" + col, col * 162, 38, 156, 168);
            list.child(
                text("cluster_stats_empty_" + col, () -> tr("gtsr.cluster.mui2.stats_empty")).widthRel(1)
                    .height(32)
                    .padding(4)
                    .scale(.8f)
                    .color(GtsrGuiPalette.TEXT_MUTED)
                    .setEnabledIf(widget -> view.stats[col][0].stack == null));
            for (int rowIndex = 0; rowIndex < 10; rowIndex++) {
                final int first = rowIndex * 7;
                Flow row = Flow.row()
                    .name("cluster_stats_row_" + col + "_" + rowIndex)
                    .widthRel(1)
                    .height(28)
                    .mainAxisAlignment(Alignment.MainAxis.START)
                    .crossAxisAlignment(Alignment.CrossAxis.START)
                    .setEnabledIf(widget -> first < 64 && view.stats[col][first].stack != null);
                for (int x = 0; x < 7; x++) {
                    final int entry = first + x;
                    if (entry >= 64) break;
                    row.child(
                        new ParentWidget<>().name("cluster_stat_" + col + "_" + entry)
                            .size(20, 28)
                            .child(
                                new ItemDisplayWidget().name("cluster_stat_icon_" + col + "_" + entry)
                                    .pos(2, 2)
                                    .size(16)
                                    .background(IDrawable.EMPTY)
                                    .item(
                                        new ObjectValue.Dynamic<>(
                                            ItemStack.class,
                                            () -> view.stats[col][entry].stack,
                                            ignored -> {})))
                            .child(
                                text(
                                    "cluster_stat_count_" + col + "_" + entry,
                                    () -> view.stats[col][entry].stack == null ? ""
                                        : GtsrNumFormat.compact3(view.stats[col][entry].count)).scale(.65f)
                                            .pos(0, 19)
                                            .size(20, 8)
                                            .textAlign(Alignment.TopCenter))
                            .tooltipBuilder(tooltip -> {
                                StatCell cell = view.stats[col][entry];
                                if (cell.stack != null) {
                                    tooltip.addLine(IKey.str(cell.stack.getDisplayName()));
                                    tooltip.addLine(IKey.str("×" + GtsrNumFormat.grouped(cell.count)));
                                }
                            })
                            .tooltipAutoUpdate(true));
                }
                list.child(row);
            }
            page.child(list);
        }
        return page;
    }

    private static ParentWidget<?> performance(ClusterSessionSyncHandler session) {
        ParentWidget<?> page = page("cluster_performance");
        page.child(
            text("cluster_perf_title", () -> tr("gtsr.terminal.perf.title")).pos(0, 0)
                .size(PAGE_WIDTH, 14)
                .scale(.8f)
                .color(GtsrGuiPalette.TEXT_ACCENT));
        for (int c = 0; c < 3; c++) {
            final int column = c;
            ClusterList list = list("cluster_perf_list_" + c, c * 162, 18, 156, 188);
            for (int i = 0; i < 160; i++) {
                final int index = i;
                ButtonWidget<?> row = button("cluster_perf_row_" + c + "_" + i, () -> "", () -> {
                    ClusterPerformanceModel.PerfRow value = session.performance.row(column, index);
                    if (value != null && value.entryKey != null) session.performance.toggle(value.entryKey);
                }).widthRel(1)
                    .coverChildrenHeight(18)
                    .padding(4, 4, 0, 0)
                    .background(IDrawable.EMPTY)
                    .hoverBackground(TerminalUiStyle.ROW_HOVER)
                    .setEnabledIf(widget -> session.performance.row(column, index) != null);
                row.child(new PerformanceText(IKey.dynamic(() -> {
                    ClusterPerformanceModel.PerfRow value = session.performance.row(column, index);
                    return value == null ? "" : value.text;
                })).name("cluster_perf_text_" + c + "_" + i)
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .widthRel(1)
                    .scale(.8f)
                    .padding(0, 0, 4, 4)
                    .onUpdateListener(widget -> {
                        ClusterPerformanceModel.PerfRow value = session.performance.row(column, index);
                        widget.scale(value != null && value.small ? .7f : .8f);
                    })
                    .textAlign(Alignment.TopLeft));
                row.tooltipBuilder(tooltip -> {
                    ClusterPerformanceModel.PerfRow value = session.performance.row(column, index);
                    if (value != null && value.tipKey != null)
                        for (String line : ClusterPerformanceModel.tipLines(value)) tooltip.addLine(IKey.str(line));
                })
                    .tooltipAutoUpdate(true);
                list.child(row);
            }
            page.child(list);
        }
        return page;
    }

    private static ParentWidget<?> page(String name) {
        return new ParentWidget<>().name(name)
            .size(PAGE_WIDTH, PAGE_HEIGHT);
    }

    private static ClusterList list(String name, int x, int y, int width, int height) {
        VerticalScrollData scroll = new VerticalScrollData(false, 4);
        scroll.setCancelScrollEdge(false);
        scroll.texture(TerminalUiStyle.SCROLLBAR_THUMB);
        return new ClusterList().name(name)
            .pos(x, y)
            .size(width, height)
            .padding(2)
            .background(TerminalUiStyle.CARD.withColorOverride(0xFFFFFFFF))
            .scrollDirection(scroll)
            .showScrollShadows(false);
    }

    /** Core's decoration draws after the native viewport; native MUI2 retains clipping and hit testing. */
    private static final class ClusterList extends ListWidget<IWidget, ClusterList> {

        private static final ScrollEdges.Style EDGES = new ScrollEdges.Style(
            7,
            5,
            0x80202025,
            0xa0202025,
            0x60202025,
            0xff62656b,
            0xff17191d);

        @Override
        public void postDraw(ModularGuiContext context, boolean transformed) {
            super.postDraw(context, transformed);
            if (!transformed) ScrollEdges.draw(0, 0, getArea().width, getArea().height, EDGES);
        }
    }

    /** Client fonts determine wrapped height; dedicated-server tree inspection never asks for a font. */
    private static final class PerformanceText extends TextWidget<PerformanceText> {

        PerformanceText(IKey key) {
            super(key);
        }

        @Override
        public int getDefaultHeight() {
            return com.cleanroommc.modularui.network.NetworkUtils.isDedicatedClient() ? super.getDefaultHeight() : 14;
        }
    }

    private static TextWidget<?> text(String name, Supplier<String> value) {
        return new TextWidget<>(
            IKey.dynamic(value)
                .shadow(false)).name(name)
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .scale(0.85f);
    }

    private static ButtonWidget<?> button(String name, Supplier<String> label, Runnable action) {
        return new ButtonWidget<>().name(name)
            .background(TerminalUiStyle.BUTTON.withColorOverride(0xFFFFFFFF))
            .hoverBackground(TerminalUiStyle.HOVER.withColorOverride(0xFFFFFFFF))
            .overlay(
                IKey.dynamic(label)
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .shadow(false)
                    .scale(0.8f))
            .onMousePressed(mouse -> {
                if (mouse == 0) action.run();
                return true;
            });
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static String tier(int tier) {
        return tier >= 0 && tier < ClusterParams.TIER_COUNT ? tr(
            ClusterParams.ClusterTier.get(tier)
                .getLangKey())
            : tr("gtsr.cluster.gui.title.unformed");
    }

    private static String type(int type) {
        if (type == 0 || type == 255) return tr(type == 0 ? "gtsr.cluster.mui2.empty" : "gtsr.cluster.mui2.unknown");
        if (type == 13) return tr("gtsr.cluster.mui2.logistics");
        if (type >= 8 && type <= 12) return tr(ClusterParams.BoosterType.values()[type - 8].getLangKey());
        int[] ordinal = { 0, 0, 3, 5, 6, 7, 8, 9 };
        return type >= 1 && type <= 7 ? tr(LINKS[ordinal[type]].getLangKey()) : "#" + type;
    }

    private static String state(ClusterSessionSyncHandler session, int slot) {
        int err = session.topo(slot, 3);
        if (err > 0 && err != 255) return tr(switch (err) {
            case ClusterTerminalData.ERR_MODULE_CONFLICT -> "gtsr.cluster.gui.topo.err.conflict";
            case ClusterTerminalData.ERR_TIER_MISMATCH -> "gtsr.cluster.gui.topo.err.tier";
            case ClusterTerminalData.ERR_NOT_CONNECTED -> "gtsr.cluster.gui.topo.err.unlinked";
            default -> "gtsr.cluster.gui.topo.err.generic";
        });
        int run = session.run(slot);
        ClusterUnitStatus[] statuses = ClusterUnitStatus.values();
        return run < statuses.length ? tr(statuses[run].getLangKey()) : "--";
    }

    private static int tierColor(int tier) {
        int[] colors = { 0xffc87e3b, 0xffc2c8d0, 0xff8ea2c8, 0xff6e7f8c };
        return tier >= 0 && tier < colors.length ? colors[tier] : 0xff6e6e6e;
    }

    private static boolean enabled(ClusterSessionSyncHandler session) {
        return session.snapshot() != null && session.snapshot()
            .getBool(ClusterTerminalData.KEY_ENABLED, false);
    }

    private static boolean alert(ClusterSessionSyncHandler session) {
        return session.integer(ClusterTerminalData.KEY_SUPPLY, 0) != 0
            || session.integer(ClusterTerminalData.KEY_BREAK, -1) >= 1;
    }

    private static int receiptColor(ClusterSessionSyncHandler session) {
        String receipt = session.receipt();
        if (receipt.equals("gtsr.cluster.mui2.pending")) return GtsrGuiPalette.TEXT_ACCENT;
        return alert(session) || receipt.equals("gtsr.cluster.mui2.rejected")
            || receipt.equals("gtsr.cluster.mui2.target_changed") ? GtsrGuiPalette.STATE_OFFLINE
                : GtsrGuiPalette.TEXT_MUTED;
    }

    private static int statusColor(ClusterSessionSyncHandler session) {
        return session.integer(ClusterTerminalData.KEY_TIER, -1) < 0 || !enabled(session) ? GtsrGuiPalette.TEXT_MUTED
            : session.integer(ClusterTerminalData.KEY_HEAT, 0) >= 100 ? GtsrGuiPalette.STATE_ONLINE
                : GtsrGuiPalette.TEXT_ACCENT;
    }

    private static int runColor(ClusterSessionSyncHandler session, int slot) {
        int run = session.run(slot);
        return session.topo(slot, 0) != 0 && run >= 0 && run < ClusterUnitStatus.values().length
            ? 0xff000000 | ClusterUnitStatus.values()[run].getColorRgb()
            : 0xff3a3a3f;
    }

    private static String cardValue(ClusterSessionSyncHandler session, int card, String key) {
        boolean shortSupply = card < 2 && (session.integer(ClusterTerminalData.KEY_SUPPLY, 0) & (1 << card)) != 0;
        return (shortSupply ? "§c⚠ " : "") + GtsrNumFormat.grouped(session.integer(key, 0))
            + (card == 2 ? "%" : card == 3 ? " " + tr("gtsr.cluster.gui.card.thru.unit") : " L/s");
    }

    private static String topologySummary(ClusterSessionSyncHandler session) {
        int work = 0, boost = 0, logistics = 0;
        for (int i = 0; i < 60; i++) {
            int type = session.topo(i, 0);
            if (type >= 1 && type <= 7) work++;
            else if (type >= 8 && type <= 12) boost++;
            else if (type == 13) logistics++;
        }
        return String.format(
            tr("gtsr.cluster.mui2.topology_summary"),
            session.integer(ClusterTerminalData.KEY_SEGMENTS, 0),
            work,
            boost,
            logistics,
            Integer.bitCount(session.integer(ClusterTerminalData.KEY_LE_AVAIL, 0)));
    }

    private static String mediumKey(ChainLink link) {
        return link == ChainLink.ORE_WASH || link == ChainLink.SIMPLE_WASH ? "gtsr.cluster.gui.link.need_water"
            : link == ChainLink.CHEM_BATH ? "gtsr.cluster.gui.link.need_chem"
                : link.requiresContinuousPower() ? "gtsr.cluster.gui.link.need_power"
                    : "gtsr.cluster.gui.link.no_medium";
    }

    private static boolean failedDraftStep(ClusterSessionSyncHandler session, int index) {
        ClusterDraftState draft = session.draft();
        return draft != null && index < draft.ordinals.size()
            && session.integer(ClusterTerminalData.KEY_LE_FAIL, 0) == draft.ordinals.get(index) + 1;
    }

    private static String chainBanner(ClusterSessionSyncHandler session) {
        int exec = session.integer(ClusterTerminalData.KEY_LE_EXEC, 0);
        int fail = session.integer(ClusterTerminalData.KEY_LE_FAIL, 0);
        String failed = fail > 0 && fail <= LINKS.length ? tr(LINKS[fail - 1].getLangKey()) : "--";
        return (exec >= 1 ? "§a✔ " + tr("gtsr.cluster.gui.link.banner.struct_ok")
            : "§c✖ " + tr("gtsr.cluster.gui.link.banner.struct_bad")) + "§r | "
            + (exec == 2 ? "§a✔ " + tr("gtsr.cluster.gui.link.banner.exec_ok")
                : "§c✖ " + String.format(tr("gtsr.cluster.gui.link.banner.exec_bad"), failed));
    }

    private static String unitState(ClusterSessionSyncHandler session, int slot) {
        for (String entry : session.string(ClusterTerminalData.KEY_LE_UNITS)
            .split(",")) {
            String[] fields = entry.split(":");
            try {
                if (fields.length < 2 || Integer.parseInt(fields[0]) != slot / 3) continue;
                int flags = Integer.parseInt(fields[1]);
                return ((flags & 1) != 0 ? "§a" + tr("gtsr.cluster.gui.link.unit.linked")
                    : "§c" + tr("gtsr.cluster.gui.link.unit.unlinked")) + "§r · "
                    + ((flags & 4) != 0 ? "§a" + tr("gtsr.cluster.gui.link.unit.authorized")
                        : "§c" + tr("gtsr.cluster.gui.link.unit.unauthorized"));
            } catch (NumberFormatException ignored) {}
        }
        return "--";
    }

    private static int selectedUnitField(ClusterSessionSyncHandler session, int field) {
        return session.snapshot() == null ? 0
            : session.snapshot()
                .selectedUnitField(field, 0);
    }

    private static String batchLabel(ClusterSessionSyncHandler session) {
        if (session.selectedSlot() < 0) return tr("gtsr.cluster.mui2.batch.unselected");
        return tr(
            (selectedUnitField(session, 1) & 16) != 0 ? "gtsr.cluster.mui2.batch.on" : "gtsr.cluster.mui2.batch.off");
    }

    private static String batchSummary(ClusterSessionSyncHandler session) {
        int parallel = selectedUnitField(session, 2), size = selectedUnitField(session, 3);
        return parallel < 1 ? tr("gtsr.cluster.mui2.batch.no_sample")
            : String.format(
                tr("gtsr.cluster.mui2.batch.last").replace("\\n", "\n"),
                GtsrNumFormat.grouped(size),
                GtsrNumFormat.grouped(parallel),
                (double) size / parallel,
                selectedUnitField(session, 4));
    }

    private static String status(ClusterSessionSyncHandler session) {
        if (session.integer(ClusterTerminalData.KEY_TIER, -1) < 0) return tr("gtsr.cluster.gui.title.unformed");
        if (session.snapshot() == null || !session.snapshot()
            .getBool(ClusterTerminalData.KEY_ENABLED, false)) return tr("gtsr.cluster.gui.title.standby");
        return tr(
            session.integer(ClusterTerminalData.KEY_HEAT, 0) >= 100 ? "gtsr.cluster.gui.title.run"
                : "gtsr.cluster.gui.title.preheating");
    }

    private static String footer(ClusterSessionSyncHandler session) {
        String receipt = session.receipt();
        if (!receipt.equals("gtsr.cluster.mui2.ready") && !receipt.equals("gtsr.cluster.mui2.applied"))
            return tr(receipt);
        int supply = session.integer(ClusterTerminalData.KEY_SUPPLY, 0);
        if ((supply & 1) != 0) return "⚠ " + tr("gtsr.cluster.gui.foot.steam_short");
        if ((supply & 2) != 0) return "⚠ " + tr("gtsr.cluster.gui.foot.lube_short");
        int broken = session.integer(ClusterTerminalData.KEY_BREAK, -1);
        if (broken >= 1) return String.format(tr("gtsr.cluster.gui.topo.error.ext"), broken);
        return tr(session.receipt());
    }

    private static int lock(ClusterSessionSyncHandler session, int ordinal, int field) {
        String[] entries = session.string(ClusterTerminalData.KEY_LE_LOCK)
            .split(",");
        if (ordinal < entries.length) {
            String[] values = entries[ordinal].split(":");
            try {
                if (field < values.length) return Integer.parseInt(values[field]);
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private static String flow(ClusterDraftState draft) {
        if (draft == null) return "";
        ClusterChainFSM.Form form = ClusterChainFSM.start();
        StringBuilder line = new StringBuilder(tr(ClusterChainFSM.formLangKey(form)));
        for (int ordinal : draft.ordinals) {
            form = ClusterChainFSM.next(form, LINKS[ordinal]);
            line.append(" →(")
                .append(tr(LINKS[ordinal].getLangKey()))
                .append(")→ ")
                .append(tr(ClusterChainFSM.formLangKey(form)));
        }
        return line.toString();
    }

    private static final class StatCell {

        ItemStack stack;
        long count;
        int id = -1, meta;
    }

    private static final class View {

        final ClusterSessionSyncHandler session;
        final StatCell[][] stats = new StatCell[3][64];
        final List<int[]> boosterRows = new ArrayList<>();
        final List<int[]> boosterLive = new ArrayList<>();
        final List<int[]> boosterCost = new ArrayList<>();
        private ClusterSnapshot previous;
        private int[] summary = new int[8];

        View(ClusterSessionSyncHandler session) {
            this.session = session;
            for (int c = 0; c < 3; c++) for (int i = 0; i < 64; i++) stats[c][i] = new StatCell();
        }

        void update() {
            ClusterSnapshot next = session.snapshot();
            if (next == null || next == previous) return;
            previous = next;
            if ((next.changedMask & (1 << 25)) != 0)
                parseRows(session.string(ClusterTerminalData.KEY_BO_STRUCT), boosterRows);
            if ((next.changedMask & (1 << 26)) != 0)
                parseRows(session.string(ClusterTerminalData.KEY_BO_LIVE), boosterLive);
            if ((next.changedMask & (1 << 28)) != 0)
                parseRows(session.string(ClusterTerminalData.KEY_BO_COST), boosterCost);
            if ((next.changedMask & (1 << 27)) != 0)
                summary = ints(session.string(ClusterTerminalData.KEY_BO_SUM), ",", 8);
            if ((next.changedMask & (1 << 30)) == 0) return;
            String[] segments = session.string(ClusterTerminalData.KEY_STATS)
                .split("\\|", -1);
            for (int c = 0; c < 3; c++) {
                String[] entries = c < segments.length ? segments[c].split(",") : new String[0];
                for (int i = 0; i < 64; i++) {
                    StatCell cell = stats[c][i];
                    try {
                        if (i >= entries.length) {
                            cell.stack = null;
                            cell.id = -1;
                            continue;
                        }
                        String[] values = entries[i].split(":");
                        int id = Integer.parseInt(values[0]), meta = Integer.parseInt(values[1]);
                        long count = Long.parseLong(values[2]);
                        Item item = Item.getItemById(id);
                        if (item == null) {
                            cell.stack = null;
                            cell.id = -1;
                            continue;
                        }
                        if (cell.stack == null || cell.id != id || cell.meta != meta)
                            cell.stack = new ItemStack(item, 1, meta);
                        cell.id = id;
                        cell.meta = meta;
                        cell.count = count;
                    } catch (RuntimeException ignored) {
                        cell.stack = null;
                        cell.id = -1;
                    }
                }
            }
        }

        String summary(int index) {
            int raw = summary[index];
            if (index == 1) return "+" + raw;
            if (index == 4) return "-" + Math.min(raw, 48) + "%";
            if (index == 5) return "×" + String.format(Locale.ROOT, "%.2f", raw / 100d);
            return "+" + raw + "%";
        }

        boolean boosterActive(int index) {
            return index < boosterLive.size() && boosterLive.get(index).length > 1 && boosterLive.get(index)[1] != 0;
        }

        String boosterCell(int index, int column) {
            if (index >= boosterRows.size()) return "";
            int[] row = boosterRows.get(index);
            if (row.length < 4 || row[0] < 0 || row[0] >= ClusterParams.BoosterType.values().length) return "--";
            ClusterParams.BoosterType type = ClusterParams.BoosterType.values()[row[0]];
            if (column == 0) return tr(type.getLangKey()) + " @" + row[2];
            if (column == 1) return tier(row[1]);
            if (column == 2) return tr(type.getFluidLangKey());
            if (column == 3) {
                int amount = index < boosterLive.size() && boosterLive.get(index).length > 0 ? boosterLive.get(index)[0]
                    : 0;
                int cost = index < boosterCost.size() && boosterCost.get(index).length > 0 ? boosterCost.get(index)[0]
                    : 0;
                return GtsrNumFormat.grouped(amount) + " L · "
                    + String.format(Locale.ROOT, "%.1f", cost / 10d)
                    + " "
                    + tr("gtsr.cluster.mui2.liters_per_round");
            }
            if (column == 4) return row[1] >= 0 && row[1] < 4 && (row[3] & 3) == 3
                ? "+" + type.getBoosterValue(row[1]) + (type == ClusterParams.BoosterType.PARALLEL ? "" : "%")
                : "--";
            return (row[3] & 1) == 0 ? tr("gtsr.cluster.gui.boost.state.unlinked")
                : (row[3] & 2) == 0 ? tr("gtsr.cluster.gui.boost.state.unformed")
                    : boosterActive(index) ? "✔ " + tr("gtsr.cluster.gui.boost.active")
                        : String.format(tr("gtsr.cluster.gui.boost.fail"), tr(type.getFluidLangKey()));
        }

        String summaryNote(int index) {
            if (index == 5) return String.format(tr("gtsr.cluster.gui.boost.sum.count"), summary[6], summary[7]);
            if (index == 4) return summary[4] > 48 ? tr("gtsr.cluster.gui.boost.saver_capped") : "≤ 48%";
            return tr(index == 2 ? "gtsr.cluster.gui.boost.note.primary" : "gtsr.cluster.gui.boost.note.additive");
        }

        String booster(int index) {
            if (index >= boosterRows.size()) return "";
            int[] row = boosterRows.get(index);
            if (row.length < 4 || row[0] < 0 || row[0] >= ClusterParams.BoosterType.values().length) return "--";
            int[] live = index < boosterLive.size() ? boosterLive.get(index) : new int[0];
            int[] cost = index < boosterCost.size() ? boosterCost.get(index) : new int[0];
            ClusterParams.BoosterType type = ClusterParams.BoosterType.values()[row[0]];
            String status = (row[3] & 1) == 0 ? tr("gtsr.cluster.gui.boost.state.unlinked")
                : (row[3] & 2) == 0 ? tr("gtsr.cluster.gui.boost.state.unformed")
                    : live.length > 1 && live[1] != 0 ? "✔ " + tr("gtsr.cluster.gui.boost.active")
                        : String.format(tr("gtsr.cluster.gui.boost.fail"), tr(type.getFluidLangKey()));
            String gain = row[1] >= 0 && row[1] < 4 && (row[3] & 3) == 3
                ? "+" + type.getBoosterValue(row[1]) + (type == ClusterParams.BoosterType.PARALLEL ? "" : "%")
                : "--";
            return "#" + row[2]
                + " "
                + tr(type.getLangKey())
                + " · "
                + tier(row[1])
                + " · "
                + tr(type.getFluidLangKey())
                + " · "
                + gain
                + "\n"
                + status
                + " · "
                + (live.length > 0 ? live[0] : 0)
                + " L · "
                + String.format(Locale.ROOT, "%.1f", cost.length > 0 ? cost[0] / 10d : 0)
                + " "
                + tr("gtsr.cluster.mui2.liters_per_round");
        }

        String boosterFormula(int index) {
            return index < boosterCost.size() ? ClusterPerformanceModel.costFormulaText(boosterCost.get(index)) : "";
        }

        private static void parseRows(String raw, List<int[]> destination) {
            destination.clear();
            for (String row : raw.split(",")) if (!row.isEmpty()) destination.add(ints(row, ":", -1));
        }

        private static int[] ints(String raw, String separator, int size) {
            String[] fields = raw.split(separator);
            int[] out = new int[size < 0 ? fields.length : size];
            for (int i = 0; i < out.length && i < fields.length; i++) try {
                out[i] = Integer.parseInt(fields[i]);
            } catch (NumberFormatException ignored) {}
            return out;
        }
    }
}
