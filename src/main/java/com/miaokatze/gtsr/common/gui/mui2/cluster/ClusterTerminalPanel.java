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
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
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

/** Native MUI2 widgets. Finite pools retain identity, scroll state and draft state. */
public final class ClusterTerminalPanel {

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
            .pos(32, 62)
            .size(582, 258)
            .controller(controller)
            .initialPage(data.initialPage);
        pages.addPage(topology(session));
        pages.addPage(links(session));
        pages.addPage(boosters(session, view));
        pages.addPage(stats(session, view));
        pages.addPage(performance(session));
        ParentWidget<?> canvas = new ParentWidget<>().name("cluster_canvas")
            .pos(0, 0)
            .size(620, 340)
            .background(TerminalUiStyle.CLUSTER_PANEL)
            .onUpdateListener(widget -> {
                session.clientTick();
                if (manager.isClient()) view.update();
            })
            .child(
                text("cluster_title", () -> tr("gtsr.cluster.mui2.title")).pos(4, 5)
                    .size(204, 12)
                    .color(GtsrGuiPalette.TEXT_TITLE))
            .child(
                new ParentWidget<>().name("cluster_tier_badge")
                    .pos(210, 6)
                    .size(5)
                    .background(
                        new DynamicDrawable(
                            () -> new Rectangle().color(tierColor(session.integer(ClusterTerminalData.KEY_TIER, -1))))))
            .child(
                text("cluster_tier", () -> tier(session.integer(ClusterTerminalData.KEY_TIER, -1))).pos(218, 4)
                    .size(70, 12)
                    .scale(.8f))
            .child(
                text("cluster_status", () -> status(session)).pos(290, 4)
                    .size(180, 12)
                    .scale(.8f)
                    .color(() -> statusColor(session)))
            .child(
                button(
                    "cluster_power",
                    () -> tr(enabled(session) ? "gtsr.gui.cluster.power.on" : "gtsr.gui.cluster.power.off"),
                    () -> session.submit(ClusterTerminalActions.TOGGLE_POWER, -1, new byte[0])).pos(504, 1)
                        .size(56, 20)
                        .addTooltipLine(IKey.lang("gtsr.gui.cluster.power.tip")))
            .child(pages)
            .child(
                text("cluster_receipt", () -> footer(session)).pos(32, 324)
                    .size(578, 14)
                    .scale(.7f)
                    .padding(4, 0)
                    .background(
                        new DynamicDrawable(
                            () -> alert(session) ? TerminalUiStyle.CHIP_ACTIVE : TerminalUiStyle.CHIP_NORMAL))
                    .color(() -> alert(session) ? GtsrGuiPalette.STATE_OFFLINE : GtsrGuiPalette.TEXT_MUTED));
        String[] cardKeys = { ClusterTerminalData.KEY_STEAM, ClusterTerminalData.KEY_LUBE, ClusterTerminalData.KEY_HEAT,
            ClusterTerminalData.KEY_THRU };
        String[] cardLabels = { "gtsr.cluster.mui2.steam", "gtsr.cluster.mui2.lube", "gtsr.cluster.mui2.heat",
            "gtsr.cluster.mui2.throughput" };
        for (int i = 0; i < 4; i++) {
            final int card = i;
            canvas.child(
                new ParentWidget<>().name("cluster_card_" + i)
                    .pos(32 + i * 146, 18)
                    .size(142, 32)
                    .background(new Rectangle().color(0xff26262b))
                    .child(
                        text("cluster_card_label_" + i, () -> tr(cardLabels[card])).pos(3, 3)
                            .size(136, 8)
                            .scale(.6f)
                            .color(GtsrGuiPalette.TEXT_MUTED))
                    .child(
                        text("cluster_card_value_" + i, () -> cardValue(session, card, cardKeys[card])).color(
                            () -> card < 2 && (session.integer(ClusterTerminalData.KEY_SUPPLY, 0) & (1 << card)) != 0
                                ? 0xffff6666
                                : card == 2 ? GtsrGuiPalette.TEXT_ACCENT : GtsrGuiPalette.TEXT_WHITE)
                            .pos(3, 11)
                            .size(136, 12)
                            .scale(.9f))
                    .child(
                        new ParentWidget<>().name("cluster_heat_bar_" + i)
                            .pos(3, 24)
                            .size(136, 7)
                            .background(new ClusterMeter(() -> session.integer(ClusterTerminalData.KEY_HEAT, 0) / 100d))
                            .setEnabledIf(widget -> card == 2))
                    .child(
                        text(
                            "cluster_total_" + i,
                            () -> String.format(
                                tr("gtsr.cluster.gui.card.thru.total"),
                                GtsrNumFormat.grouped(
                                    session.snapshot() == null ? 0L
                                        : session.snapshot()
                                            .getLong(ClusterTerminalData.KEY_TOTAL, 0L)))).pos(3, 24)
                                                .size(136, 8)
                                                .scale(.6f)
                                                .color(GtsrGuiPalette.TEXT_MUTED)
                                                .setEnabledIf(widget -> card == 3)));
        }
        for (int i = 0; i < 5; i++) {
            final int page = i;
            canvas.child(
                new PageButton(i, controller).name("cluster_tab_" + i)
                    .pos(2, 62 + i * 30)
                    .size(28, 28)
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
        return ModularPanel.defaultPanel(ClusterTerminalFactory.PANEL_NAME, 620, 340)
            .background(com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY)
            .child(
                TerminalViewport.wrapExact("cluster_viewport", canvas, 620, 340)
                    .pos(0, 0)
                    .sizeRel(1f));
    }

    private static ParentWidget<?> topology(ClusterSessionSyncHandler session) {
        ParentWidget<?> page = page("cluster_topology");
        page.child(
            text("cluster_topology_summary", () -> topologySummary(session)).pos(0, 0)
                .size(582, 11)
                .scale(.75f));
        String[] pads = { "work", "boost", "logi" };
        for (int pad = 0; pad < 3; pad++) {
            final int column = pad;
            page.child(
                text("cluster_topology_header_" + pad, () -> tr("gtsr.cluster.gui.topo.pad." + pads[column]))
                    .pos(36 + pad * 180, 13)
                    .size(178, 9)
                    .scale(.6f)
                    .color(GtsrGuiPalette.TEXT_MUTED));
        }
        ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> list = new ListWidget<>()
            .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
            .name("cluster_topology_list")
            .pos(0, 24)
            .size(582, 220);
        for (int segment = 19; segment >= 0; segment--) {
            final int seg = segment;
            ParentWidget<?> row = new ParentWidget<>().name("cluster_segment_" + seg)
                .widthRel(1)
                .height(22)
                .setEnabledIf(widget -> seg < Math.max(1, session.integer(ClusterTerminalData.KEY_SEGMENTS, 0) + 1));
            row.child(
                text(
                    "cluster_segment_label_" + seg,
                    () -> seg == 0 ? tr("gtsr.cluster.gui.topo.layer.base")
                        : String.format(tr("gtsr.cluster.gui.topo.layer.ext"), seg)).pos(0, 6)
                            .size(36, 12)
                            .scale(.7f));
            for (int pad = 0; pad < 3; pad++) {
                final int slot = segment * 3 + pad;
                row.child(
                    new ParentWidget<>().name("cluster_topology_slot_" + slot)
                        .pos(36 + pad * 180, 0)
                        .size(178, 22)
                        .background(IDrawable.EMPTY)
                        .child(
                            text("cluster_topology_name_" + slot, () -> type(session.topo(slot, 0))).pos(2, 2)
                                .size(164, 9)
                                .scale(.75f))
                        .child(
                            new ParentWidget<>().name("cluster_topology_tier_" + slot)
                                .pos(170, 3)
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
                                    .pos(2, 11)
                                    .size(174, 8)
                                    .scale(.65f))
                        .child(
                            new ParentWidget<>().name("cluster_topology_bar_" + slot)
                                .pos(1, 18)
                                .size(176, 3)
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
            .pos(0, 246)
            .size(582, 12);
        for (int i = 0; i < 6; i++) {
            final ClusterUnitStatus value = ClusterUnitStatus.values()[i];
            legend.child(
                new ParentWidget<>().pos(i * 96, 2)
                    .size(5)
                    .background(new Rectangle().color(0xff000000 | value.getColorRgb())));
            legend.child(
                text("cluster_legend_" + i, () -> tr(value.getLangKey())).pos(i * 96 + 8, 1)
                    .size(88, 10)
                    .scale(.6f)
                    .color(GtsrGuiPalette.TEXT_MUTED));
        }
        return page.child(legend);
    }

    private static ParentWidget<?> links(ClusterSessionSyncHandler session) {
        ParentWidget<?> page = page("cluster_links");
        page.child(
            new LogisticsMenu(session).pos(0, 0)
                .size(200, 14));
        page.child(
            text("cluster_chain_banner", () -> chainBanner(session)).pos(212, 0)
                .size(370, 14)
                .scale(.7f));
        page.child(
            button("cluster_chain_save", () -> tr("gtsr.cluster.gui.link.chain.save"), session::save).pos(496, 13)
                .size(42, 12));
        page.child(
            button("cluster_chain_revert", () -> tr("gtsr.cluster.mui2.revert"), session::discardDraft).pos(450, 13)
                .size(42, 12));
        page.child(button("cluster_chain_clear", () -> tr("gtsr.cluster.gui.link.chain.clear"), () -> {
            ClusterDraftState draft = session.draft();
            if (draft != null) draft.clear();
        }).pos(540, 13)
            .size(42, 12));
        ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> available = new ListWidget<>()
            .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
            .name("cluster_available_links")
            .pos(0, 28)
            .size(272, 218)
            .background(TerminalUiStyle.CARD);
        for (ChainLink link : LINKS) {
            available.child(
                button(
                    "cluster_append_" + link.ordinal(),
                    () -> tr(link.getLangKey()) + " · "
                        + String.format(Locale.ROOT, "%.2fs", link.getBaseSecondsPrecise())
                        + " "
                        + (lock(session, link.ordinal(), 0) == 0 ? "§a✔" : "§c✖")
                        + "\n§r"
                        + link.getBaseSteamLps()
                        + " L/s · "
                        + tr(mediumKey(link)),
                    () -> {
                        ClusterDraftState draft = session.draft();
                        if (draft != null) draft.append(link.ordinal());
                    }).widthRel(1)
                        .height(34)
                        .background(IDrawable.EMPTY)
                        .padding(5, 3)
                        .overlay(
                            IKey.dynamic(() -> availableLinkLabel(session, link))
                                .alignment(Alignment.TopLeft)
                                .scale(.75f)
                                .shadow(false)
                                .color(GtsrGuiPalette.TEXT_BODY))
                        .hoverBackground(TerminalUiStyle.ROW_HOVER.withColorOverride(0xFFFFFFFF))
                        .tooltipBuilder(tooltip -> {
                            tooltip.addLine(IKey.lang(link.getLangKey()));
                            tooltip.addLine(
                                IKey.lang(
                                    "gtsr.cluster.mui2.link_values",
                                    link.getBaseSecondsPrecise(),
                                    link.getBaseSteamLps(),
                                    lock(session, link.ordinal(), 1)));
                            String medium = link == ChainLink.ORE_WASH || link == ChainLink.SIMPLE_WASH
                                ? "gtsr.cluster.gui.link.need_water"
                                : link == ChainLink.CHEM_BATH ? "gtsr.cluster.gui.link.need_chem"
                                    : link.requiresContinuousPower() ? "gtsr.cluster.gui.link.need_power"
                                        : "gtsr.cluster.gui.link.no_medium";
                            tooltip.addLine(IKey.lang(medium));
                            int kind = lock(session, link.ordinal(), 0);
                            if (kind != 0) tooltip.addLine(IKey.lang("gtsr.cluster.mui2.lock", kind));
                            if (link == ChainLink.CRUSH) {
                                int tier = session.integer(ClusterTerminalData.KEY_TIER, 0);
                                double multiplier = tier >= 2 ? ClusterParams.CRUSH_BYPRODUCT_MULT_HIGH_TIER
                                    : tier == 1 ? ClusterParams.CRUSH_BYPRODUCT_MULT_STEEL
                                        : ClusterParams.CRUSH_BYPRODUCT_MULT_NORMAL;
                                tooltip.addLine(
                                    multiplier >= 1 ? IKey.lang("gtsr.gui.cluster.link.crush.byproduct_none")
                                        : IKey.lang(
                                            "gtsr.gui.cluster.link.crush.byproduct_debuff",
                                            Math.round((1 - multiplier) * 100)));
                            }
                        })
                        .tooltipAutoUpdate(true));
        }
        page.child(available);
        ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> chain = new ListWidget<>()
            .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
            .name("cluster_draft_list")
            .pos(280, 46)
            .size(302, 88)
            .background(TerminalUiStyle.CARD);
        for (int i = 0; i < 16; i++) {
            final int index = i;
            ParentWidget<?> row = new ParentWidget<>().name("cluster_draft_step_" + index)
                .widthRel(1)
                .height(25)
                .setEnabledIf(widget -> session.draft() != null && index < session.draft().ordinals.size());
            row.child(
                new ParentWidget<>().pos(1, 0)
                    .size(296, 13)
                    .background(new Rectangle().color(0x30c87e3b))
                    .setEnabledIf(widget -> session.draft() != null && session.draft().peak == index));
            row.child(
                text("cluster_draft_number_" + index, () -> (index + 1) + ".").pos(2, 3)
                    .size(16, 10)
                    .scale(.75f)
                    .color(GtsrGuiPalette.TEXT_ACCENT));
            row.child(text("cluster_draft_label_" + index, () -> {
                ClusterDraftState draft = session.draft();
                return draft == null || index >= draft.ordinals.size() ? ""
                    : tr(LINKS[draft.ordinals.get(index)].getLangKey());
            }).pos(18, 4)
                .size(44, 9)
                .scale(.65f)
                .color(() -> failedDraftStep(session, index) ? GtsrGuiPalette.STATE_OFFLINE : GtsrGuiPalette.TEXT_BODY)
                .tooltipBuilder(tooltip -> {
                    ClusterDraftState draft = session.draft();
                    if (draft != null && index < draft.ordinals.size()) {
                        int ordinal = draft.ordinals.get(index);
                        tooltip.addLine(IKey.lang(LINKS[ordinal].getLangKey()));
                        tooltip.addLine(IKey.str(flow(draft)));
                    }
                })
                .tooltipAutoUpdate(true));
            row.child(
                text("cluster_draft_time_" + index, () -> draftStepTime(session, index)).pos(64, 4)
                    .size(48, 9)
                    .scale(.6f)
                    .color(GtsrGuiPalette.STATE_ONLINE));
            row.child(
                button(
                    "cluster_move_up_" + index,
                    () -> "◀",
                    () -> {
                        if (session.draft() != null) session.draft()
                            .move(index, -1);
                    }).pos(114, 0)
                        .size(14, 13));
            row.child(
                button(
                    "cluster_move_down_" + index,
                    () -> "▶",
                    () -> {
                        if (session.draft() != null) session.draft()
                            .move(index, 1);
                    }).pos(130, 0)
                        .size(14, 13));
            row.child(
                button(
                    "cluster_remove_" + index,
                    () -> "§c✖",
                    () -> {
                        if (session.draft() != null) session.draft()
                            .remove(index);
                    }).pos(146, 0)
                        .size(16, 13));
            row.child(
                button(
                    "cluster_peak_" + index,
                    () -> session.draft() != null && session.draft().peak == index
                        ? "§6▲" + tr("gtsr.cluster.mui2.peak")
                        : "▲" + tr("gtsr.cluster.mui2.peak"),
                    () -> {
                        if (session.draft() != null) session.draft()
                            .peak(index);
                    }).pos(164, 0)
                        .size(32, 13)
                        .addTooltipLine(IKey.lang("gtsr.terminal.le.chains.peak", index + 1)));
            row.child(
                text("cluster_draft_note_" + index, () -> draftStepNote(session, index)).pos(2, 17)
                    .size(286, 8)
                    .scale(.55f)
                    .color(GtsrGuiPalette.TEXT_ACCENT));
            chain.child(row);
        }
        ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> unitChips = new ListWidget<>()
            .name("cluster_unit_list")
            .pos(280, 28)
            .size(302, 14)
            .scrollDirection(
                new com.cleanroommc.modularui.widget.scroll.HorizontalScrollData().cancelScrollEdge(false));
        for (int segment = 0; segment < 20; segment++) {
            final int slot = segment * 3 + 2;
            unitChips.child(
                button("cluster_unit_" + slot, () -> "#" + slot / 3, () -> session.select(slot)).width(46)
                    .height(14)
                    .setEnabledIf(widget -> session.topo(slot, 0) == 13));
        }
        page.child(unitChips);
        page.child(chain);
        page.child(text("cluster_chain_flow", () -> {
            ClusterDraftState draft = session.draft();
            return draft == null ? tr("gtsr.cluster.gui.link.unit.none")
                : tr("gtsr.cluster.mui2.steps") + " "
                    + draft.ordinals.size()
                    + "/16 · "
                    + tr("gtsr.cluster.mui2.fsm")
                    + " "
                    + (draft.valid() ? "✔" : "✖");
        }).pos(280, 136)
            .size(302, 10)
            .scale(.65f)
            .color(
                () -> session.draft() != null && session.draft()
                    .valid() ? GtsrGuiPalette.STATE_ONLINE : GtsrGuiPalette.STATE_OFFLINE));
        page.child(
            text("cluster_chain_feedback", () -> tr(session.receipt())).pos(0, 247)
                .size(272, 11)
                .scale(.65f));
        page.child(
            text("cluster_chain_formula", () -> tr("gtsr.cluster.gui.link.perf")).pos(280, 168)
                .size(302, 12)
                .scale(.7f)
                .color(GtsrGuiPalette.TEXT_ACCENT)
                .tooltipBuilder(tooltip -> {
                    tooltip.addLine(IKey.str(flow(session.draft())));
                    for (ClusterFormulaText.Line line : ClusterFormulaText
                        .parse(session.string(ClusterTerminalData.KEY_F_FORMULA))) tooltip.addLine(IKey.str(line.text));
                })
                .tooltipAutoUpdate(true));
        page.child(
            text("cluster_available_title", () -> tr("gtsr.cluster.gui.link.avail")).pos(0, 16)
                .size(272, 11)
                .scale(.7f)
                .color(GtsrGuiPalette.TEXT_ACCENT));
        page.child(
            text("cluster_chain_title", () -> tr("gtsr.cluster.gui.link.chain")).pos(280, 16)
                .size(120, 12)
                .scale(.7f)
                .color(GtsrGuiPalette.TEXT_ACCENT));
        page.child(
            new ListWidget<>().name("cluster_flow_diagram")
                .pos(280, 148)
                .size(302, 16)
                .background(TerminalUiStyle.CARD)
                .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
                .child(
                    new PerformanceText(IKey.dynamic(() -> flow(session.draft()))).name("cluster_flow_text")
                        .width(286)
                        .scale(.6f)
                        .color(GtsrGuiPalette.TEXT_BODY)
                        .textAlign(Alignment.TopLeft)));
        ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> formula = new ListWidget<>()
            .name("cluster_formula_list")
            .pos(280, 183)
            .size(302, 63)
            .background(TerminalUiStyle.CARD)
            .scrollDirection(new VerticalScrollData().cancelScrollEdge(false));
        formula.child(
            text(
                "cluster_link_performance",
                () -> tr("gtsr.cluster.gui.link.perf.time") + " "
                    + String.format(Locale.ROOT, "%.2f s", session.integer(ClusterTerminalData.KEY_F_TIME, 0) / 100d)
                    + " · "
                    + tr("gtsr.cluster.gui.link.perf.parallel")
                    + " "
                    + session.integer(ClusterTerminalData.KEY_F_PAR, 0)
                    + " · "
                    + tr("gtsr.cluster.gui.link.perf.thru")
                    + " "
                    + String.format(Locale.ROOT, "%.2f /s", session.integer(ClusterTerminalData.KEY_F_THRU, 0) / 100d))
                        .widthRel(1)
                        .height(12)
                        .scale(.65f));
        for (int i = 0; i < 40; i++) {
            final int line = i;
            formula.child(new PerformanceText(IKey.dynamic(() -> {
                List<ClusterFormulaText.Line> lines = ClusterFormulaText
                    .parse(session.string(ClusterTerminalData.KEY_F_FORMULA));
                return line < lines.size() ? lines.get(line).text : "";
            })).name("cluster_formula_line_" + i)
                .widthRel(1)
                .scale(.6f)
                .color(GtsrGuiPalette.TEXT_BODY)
                .textAlign(Alignment.TopLeft)
                .setEnabledIf(
                    widget -> line < ClusterFormulaText.parse(session.string(ClusterTerminalData.KEY_F_FORMULA))
                        .size()));
        }
        page.child(formula);
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
            return new Menu<>().width(200)
                .coverChildrenHeight()
                .background(TerminalUiStyle.CARD.withColorOverride(0xFFFFFFFF))
                .child(options);
        }
    }

    private static String availableLinkLabel(ClusterSessionSyncHandler session, ChainLink link) {
        int count = 0;
        ClusterDraftState draft = session.draft();
        if (draft != null) for (int ordinal : draft.ordinals) if (ordinal == link.ordinal()) count++;
        String first = tr(link.getLangKey()) + (count == 0 ? "" : "  §a×" + count);
        String second = String.format(Locale.ROOT, "%.2fs", link.getBaseSecondsPrecise()) + " · " + tr(mediumKey(link));
        int kind = lock(session, link.ordinal(), 0);
        if (kind != 0) second += " · §c"
            + (kind == 2 ? String.format(tr("gtsr.gui.cluster.link.locked_module"), tr(link.getLangKey()))
                : tr(kind == 3 ? "gtsr.gui.cluster.link.locked_unformed" : "gtsr.gui.cluster.link.locked_power"));
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
                .size(582, 10)
                .scale(.7f)
                .color(GtsrGuiPalette.TEXT_ACCENT));
        int[] columns = { 0, 150, 182, 238, 326, 370 };
        int[] widths = { 148, 30, 54, 86, 42, 204 };
        String[] headers = { "module", "tier", "fluid", "supply", "gain", "status" };
        for (int i = 0; i < 6; i++) {
            final int col = i;
            page.child(
                text("cluster_booster_header_" + i, () -> tr("gtsr.cluster.gui.boost.col." + headers[col]))
                    .pos(columns[i] + 2, 11)
                    .size(widths[i], 9)
                    .scale(.6f)
                    .color(GtsrGuiPalette.TEXT_ACCENT));
        }
        ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> list = new ListWidget<>()
            .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
            .name("cluster_booster_list")
            .pos(0, 20)
            .size(582, 130);
        for (int i = 0; i < 20; i++) {
            final int index = i;
            ParentWidget<?> row = new ParentWidget<>().name("cluster_booster_row_" + i)
                .widthRel(1)
                .height(15)
                .setEnabledIf(widget -> index < view.boosterRows.size())
                .background(
                    new DynamicDrawable(
                        () -> new Rectangle().color(view.boosterActive(index) ? 0xff26262b : 0xff3a1e1e)))
                .tooltipBuilder(tooltip -> {
                    tooltip.addLine(IKey.str(view.booster(index)));
                    tooltip.addLine(IKey.str(view.boosterFormula(index)));
                })
                .tooltipAutoUpdate(true);
            for (int iCol = 0; iCol < 6; iCol++) {
                final int col = iCol;
                row.child(
                    text("cluster_booster_cell_" + i + "_" + col, () -> view.boosterCell(index, col))
                        .pos(columns[col] + 2, 4)
                        .size(widths[col], 10)
                        .scale(.65f)
                        .color(
                            () -> col == 5
                                ? view.boosterActive(index) ? GtsrGuiPalette.STATE_ONLINE : GtsrGuiPalette.STATE_OFFLINE
                                : view.boosterActive(index) ? GtsrGuiPalette.TEXT_BODY : GtsrGuiPalette.STATE_OFFLINE));
            }
            list.child(row);
        }
        page.child(list);
        page.child(
            text(
                "cluster_booster_hint",
                () -> view.boosterRows.isEmpty() ? tr("gtsr.cluster.gui.boost.empty.hint")
                    : !enabled(session) ? tr("gtsr.cluster.gui.boost.empty.off") : "").pos(0, 152)
                        .size(582, 9)
                        .scale(.6f)
                        .color(GtsrGuiPalette.TEXT_MUTED));
        String[] summaries = { "speed", "parallel", "primary", "secondary", "saver", "mult" };
        for (int i = 0; i < summaries.length; i++) {
            final int index = i;
            page.child(
                new ParentWidget<>().name("cluster_booster_card_" + i)
                    .pos((i % 3) * 195, 154 + (i / 3) * 34)
                    .size(191, 30)
                    .background(new Rectangle().color(0xff26262b))
                    .child(
                        text(
                            "cluster_booster_summary_label_" + i,
                            () -> tr("gtsr.cluster.gui.boost.sum." + summaries[index])).pos(3, 2)
                                .size(185, 8)
                                .scale(.6f)
                                .color(GtsrGuiPalette.TEXT_ACCENT))
                    .child(
                        text("cluster_booster_summary_" + i, () -> "\u00a7l" + view.summary(index)).pos(3, 11)
                            .size(185, 10)
                            .scale(.8f)
                            .color(GtsrGuiPalette.TEXT_WHITE))
                    .child(
                        text("cluster_booster_note_" + i, () -> view.summaryNote(index)).pos(3, 22)
                            .size(185, 8)
                            .scale(.55f)
                            .color(GtsrGuiPalette.TEXT_MUTED)));
        }
        page.child(
            text("cluster_booster_rules", () -> tr("gtsr.cluster.gui.boost.rule.stack")).pos(0, 224)
                .size(582, 9)
                .scale(.6f)
                .color(GtsrGuiPalette.TEXT_MUTED));
        page.child(
            text(
                "cluster_booster_rule_state",
                () -> view.boosterRows.isEmpty() ? tr("gtsr.cluster.gui.boost.rule.none")
                    : view.summary[7] > 0 ? String.format(tr("gtsr.cluster.gui.boost.rule.fail"), view.summary[7])
                        : tr("gtsr.cluster.gui.boost.rule.ok")).pos(0, 234)
                            .size(582, 9)
                            .scale(.6f)
                            .color(
                                () -> view.summary[7] > 0 ? GtsrGuiPalette.STATE_OFFLINE
                                    : GtsrGuiPalette.STATE_ONLINE));
        return page;
    }

    private static ParentWidget<?> stats(ClusterSessionSyncHandler session, View view) {
        ParentWidget<?> page = page("cluster_stats");
        page.child(
            button(
                "cluster_clear_stats",
                () -> tr(session.clearArmed() ? "gtsr.terminal.stats.clear.confirm" : "gtsr.terminal.stats.clear"),
                session::clearStats).pos(512, 0)
                    .size(70, 14)
                    .background(
                        new DynamicDrawable(
                            () -> session.clearArmed() ? TerminalUiStyle.CHIP_ACTIVE : TerminalUiStyle.CHIP_NORMAL)));
        page.child(
            text("cluster_stats_heading", () -> tr("gtsr.terminal.stats.title")).pos(0, 0)
                .size(400, 12)
                .scale(.7f)
                .color(GtsrGuiPalette.TEXT_ACCENT));
        String[] ledgers = { "in", "out", "bonus" };
        for (int ledger = 0; ledger < 3; ledger++) {
            final int col = ledger;
            page.child(
                text("cluster_stats_title_" + col, () -> tr("gtsr.terminal.stats." + ledgers[col])).pos(col * 195, 16)
                    .size(150, 10)
                    .scale(.65f)
                    .color(GtsrGuiPalette.TEXT_LABEL));
            ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> list = new ListWidget<>()
                .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
                .name("cluster_stats_list_" + col)
                .pos(col * 195, 28)
                .size(191, 230);
            for (int rowIndex = 0; rowIndex < 6; rowIndex++) {
                Flow row = Flow.row()
                    .name("cluster_stats_row_" + col + "_" + rowIndex)
                    .widthRel(1)
                    .height(26);
                for (int x = 0; x < 11; x++) {
                    final int entry = rowIndex * 11 + x;
                    if (entry >= 64) break;
                    row.child(
                        new ParentWidget<>().name("cluster_stat_" + col + "_" + entry)
                            .size(17, 26)
                            .child(
                                new ItemDisplayWidget().name("cluster_stat_icon_" + col + "_" + entry)
                                    .size(16)
                                    .background(com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY)
                                    .item(
                                        new ObjectValue.Dynamic<>(
                                            ItemStack.class,
                                            () -> view.stats[col][entry].stack,
                                            ignored -> {})))
                            .child(
                                text(
                                    "cluster_stat_count_" + col + "_" + entry,
                                    () -> view.stats[col][entry].stack == null ? ""
                                        : GtsrNumFormat.compact3(view.stats[col][entry].count)).scale(0.5f)
                                            .pos(0, 17)
                                            .size(17, 9))
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
                .size(582, 12)
                .scale(.7f)
                .color(GtsrGuiPalette.TEXT_ACCENT));
        for (int c = 0; c < 3; c++) {
            final int column = c;
            ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> list = new ListWidget<>()
                .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
                .name("cluster_perf_list_" + c)
                .pos(c * 195, 14)
                .size(191, 244)
                .background(TerminalUiStyle.CARD);
            for (int i = 0; i < 160; i++) {
                final int index = i;
                ButtonWidget<?> row = button("cluster_perf_row_" + c + "_" + i, () -> "", () -> {
                    ClusterPerformanceModel.PerfRow value = session.performance.row(column, index);
                    if (value != null && value.entryKey != null) session.performance.toggle(value.entryKey);
                }).widthRel(1)
                    .coverChildrenHeight()
                    .background(com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY)
                    .hoverBackground(TerminalUiStyle.ROW_HOVER)
                    .setEnabledIf(widget -> session.performance.row(column, index) != null);
                row.child(new PerformanceText(IKey.dynamic(() -> {
                    ClusterPerformanceModel.PerfRow value = session.performance.row(column, index);
                    return value == null ? "" : value.text;
                })).name("cluster_perf_text_" + c + "_" + i)
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .widthRel(1)
                    .scale(0.7f)
                    .onUpdateListener(widget -> {
                        ClusterPerformanceModel.PerfRow value = session.performance.row(column, index);
                        widget.scale(value != null && value.small ? .6f : .7f);
                        widget.padding(value != null && value.small ? 12 : 2, 2, 0, 0);
                    })
                    .textAlign(Alignment.TopLeft)
                    .margin(0, 0, 2, 2));
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
            .size(582, 258);
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
                    .scale(0.7f))
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
            + (card == 2 ? "%" : card == 3 ? " " + tr("gtsr.cluster.gui.card.thru.unit") : " L/s")
            + (shortSupply
                ? " ⚠ " + tr(card == 0 ? "gtsr.cluster.gui.card.steam.short" : "gtsr.cluster.gui.card.lube.short")
                : "");
    }

    private static String topologySummary(ClusterSessionSyncHandler session) {
        int work = 0, boost = 0, logistics = 0;
        for (int i = 0; i < 60; i++) {
            int type = session.topo(i, 0);
            if (type >= 1 && type <= 7) work++;
            else if (type >= 8 && type <= 12) boost++;
            else if (type == 13) logistics++;
        }
        return (session.integer(ClusterTerminalData.KEY_TIER, -1) >= 0 ? "§a✔ " + tr("gtsr.cluster.gui.topo.formed")
            : "✖ " + tier(-1)) + "§r  · "
            + tier(session.integer(ClusterTerminalData.KEY_TIER, -1))
            + " · "
            + String.format(tr("gtsr.cluster.gui.topo.segs"), session.integer(ClusterTerminalData.KEY_SEGMENTS, 0))
            + " · "
            + String.format(tr("gtsr.cluster.gui.topo.modules"), work, boost, logistics)
            + " · "
            + String.format(
                tr("gtsr.cluster.gui.topo.chains"),
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

    private static String status(ClusterSessionSyncHandler session) {
        if (session.integer(ClusterTerminalData.KEY_TIER, -1) < 0) return tr("gtsr.cluster.gui.title.unformed");
        if (session.snapshot() == null || !session.snapshot()
            .getBool(ClusterTerminalData.KEY_ENABLED, false)) return tr("gtsr.cluster.gui.title.standby");
        return tr(
            session.integer(ClusterTerminalData.KEY_HEAT, 0) >= 100 ? "gtsr.cluster.gui.title.run"
                : "gtsr.cluster.gui.title.preheating");
    }

    private static String footer(ClusterSessionSyncHandler session) {
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
                    + " L/矿";
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
                + tr("gtsr.cluster.mui2.liters_per_ore");
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
