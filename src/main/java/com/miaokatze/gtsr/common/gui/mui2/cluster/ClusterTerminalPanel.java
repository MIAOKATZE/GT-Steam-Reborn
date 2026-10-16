package com.miaokatze.gtsr.common.gui.mui2.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
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
            .background(TerminalUiStyle.PANEL)
            .onUpdateListener(widget -> {
                session.clientTick();
                if (manager.isClient()) view.update();
            })
            .child(
                text(
                    "cluster_title",
                    () -> tr("gtsr.cluster.mui2.title") + " · "
                        + tier(session.integer(ClusterTerminalData.KEY_TIER, -1))
                        + " · "
                        + status(session)).pos(12, 5)
                            .size(486, 16))
            .child(
                button(
                    "cluster_power",
                    () -> tr("gtsr.cluster.mui2.power"),
                    () -> session.submit(ClusterTerminalActions.TOGGLE_POWER, -1, new byte[0])).pos(506, 2)
                        .size(104, 20))
            .child(pages)
            .child(
                text("cluster_receipt", () -> footer(session)).pos(32, 324)
                    .size(578, 14));
        String[] cardKeys = { ClusterTerminalData.KEY_STEAM, ClusterTerminalData.KEY_LUBE, ClusterTerminalData.KEY_HEAT,
            ClusterTerminalData.KEY_THRU };
        String[] cardLabels = { "gtsr.cluster.mui2.steam", "gtsr.cluster.mui2.lube", "gtsr.cluster.mui2.heat",
            "gtsr.cluster.mui2.throughput" };
        for (int i = 0; i < 4; i++) {
            final int card = i;
            canvas.child(
                new ParentWidget<>().name("cluster_card_" + i)
                    .pos(32 + i * 146, 24)
                    .size(142, 32)
                    .background(TerminalUiStyle.CARD)
                    .child(
                        text("cluster_card_label_" + i, () -> tr(cardLabels[card])).pos(4, 2)
                            .size(134, 12))
                    .child(
                        text(
                            "cluster_card_value_" + i,
                            () -> GtsrNumFormat.grouped(session.integer(cardKeys[card], 0))
                                + (card == 2 ? "%" : card == 3 ? "/s" : " L/s"))
                                    .color(
                                        () -> card < 2
                                            && (session.integer(ClusterTerminalData.KEY_SUPPLY, 0) & (1 << card)) != 0
                                                ? 0xffff6666
                                                : GtsrGuiPalette.TEXT_BODY)
                                    .pos(4, 15)
                                    .size(134, 14)));
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
                                ? TerminalUiStyle.HOVER
                                : TerminalUiStyle.BUTTON))
                    .hoverBackground(TerminalUiStyle.HOVER)
                    .overlay(
                        IKey.lang("gtsr.cluster.gui.tab." + TABS[page])
                            .scale(0.7f))
                    .addTooltipLine(IKey.lang("gtsr.cluster.gui.tab." + TABS[page])));
        }
        return ModularPanel.defaultPanel(ClusterTerminalFactory.PANEL_NAME, 620, 340)
            .background(TerminalUiStyle.PANEL)
            .child(
                TerminalViewport.wrap("cluster_viewport", canvas, 620, 340)
                    .pos(0, 0)
                    .sizeRel(1f));
    }

    private static ParentWidget<?> topology(ClusterSessionSyncHandler session) {
        ParentWidget<?> page = page("cluster_topology");
        page.child(
            text(
                "cluster_topology_summary",
                () -> tier(session.integer(ClusterTerminalData.KEY_TIER, -1)) + " · "
                    + tr("gtsr.cluster.mui2.segments")
                    + " "
                    + session.integer(ClusterTerminalData.KEY_SEGMENTS, 0)
                    + " · "
                    + tr("gtsr.cluster.mui2.break")
                    + " "
                    + session.integer(ClusterTerminalData.KEY_BREAK, -1)).pos(0, 0)
                        .size(582, 18));
        ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> list = new ListWidget<>()
            .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
            .name("cluster_topology_list")
            .pos(0, 20)
            .size(582, 220);
        for (int segment = 19; segment >= 0; segment--) {
            final int seg = segment;
            Flow row = Flow.row()
                .name("cluster_segment_" + seg)
                .widthRel(1)
                .height(32)
                .childPadding(2)
                .setEnabledIf(widget -> seg < Math.max(1, session.integer(ClusterTerminalData.KEY_SEGMENTS, 0) + 1));
            row.child(text("cluster_segment_label_" + seg, () -> "#" + seg).size(28, 30));
            for (int pad = 0; pad < 3; pad++) {
                int slot = segment * 3 + pad;
                row.child(
                    new ParentWidget<>().name("cluster_topology_slot_" + slot)
                        .width(178)
                        .height(30)
                        .background(TerminalUiStyle.CARD)
                        .child(
                            text("cluster_topology_name_" + slot, () -> type(session.topo(slot, 0))).pos(3, 1)
                                .size(172, 13))
                        .child(
                            text("cluster_topology_state_" + slot, () -> state(session, slot))
                                .color(
                                    () -> session.topo(slot, 3) > 0 && session.topo(slot, 3) != 255 ? 0xffff7777
                                        : GtsrGuiPalette.TEXT_BODY)
                                .pos(3, 14)
                                .size(172, 13))
                        .tooltipBuilder(tooltip -> {
                            tooltip.addLine(IKey.str(type(session.topo(slot, 0))));
                            tooltip.addLine(IKey.str(tier(session.topo(slot, 1))));
                            tooltip.addLine(IKey.str(state(session, slot)));
                            tooltip.addLine(IKey.lang("gtsr.cluster.mui2.slot", slot));
                        })
                        .tooltipAutoUpdate(true));
            }
            list.child(row);
        }
        page.child(list);
        page.child(
            text("cluster_topology_legend", () -> tr("gtsr.cluster.mui2.topology_legend")).pos(0, 242)
                .size(582, 15));
        return page;
    }

    private static ParentWidget<?> links(ClusterSessionSyncHandler session) {
        ParentWidget<?> page = page("cluster_links");
        ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> units = new ListWidget<>()
            .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
            .name("cluster_unit_list")
            .pos(0, 0)
            .size(130, 76);
        for (int segment = 0; segment < 20; segment++) {
            int slot = segment * 3 + 2;
            units.child(
                button(
                    "cluster_unit_" + slot,
                    () -> (session.selectedSlot() == slot ? "▶ " : "") + tr("gtsr.cluster.mui2.logistics")
                        + " #"
                        + (slot / 3),
                    () -> session.select(slot)).widthRel(1)
                        .height(19)
                        .setEnabledIf(widget -> session.topo(slot, 0) == 13));
        }
        page.child(units);
        page.child(
            text(
                "cluster_chain_banner",
                () -> tr("gtsr.cluster.mui2.execution") + " "
                    + session.integer(ClusterTerminalData.KEY_LE_EXEC, 0)
                    + " · "
                    + tr("gtsr.cluster.mui2.failure")
                    + " "
                    + session.integer(ClusterTerminalData.KEY_LE_FAIL, -1)).pos(136, 0)
                        .size(446, 18));
        page.child(
            button("cluster_chain_save", () -> tr("gtsr.cluster.gui.link.chain.save"), session::save).pos(422, 19)
                .size(78, 20));
        page.child(
            button("cluster_chain_revert", () -> tr("gtsr.cluster.mui2.revert"), session::discardDraft).pos(340, 19)
                .size(78, 20));
        page.child(button("cluster_chain_clear", () -> tr("gtsr.cluster.gui.link.chain.clear"), () -> {
            ClusterDraftState draft = session.draft();
            if (draft != null) draft.clear();
        }).pos(504, 19)
            .size(78, 20));
        ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> available = new ListWidget<>()
            .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
            .name("cluster_available_links")
            .pos(0, 82)
            .size(210, 176);
        for (ChainLink link : LINKS) {
            available.child(
                button(
                    "cluster_append_" + link.ordinal(),
                    () -> tr(link.getLangKey()) + " · "
                        + String.format(Locale.ROOT, "%.2fs", link.getBaseSecondsPrecise())
                        + " "
                        + (lock(session, link.ordinal(), 0) == 0 ? "✔" : "✖"),
                    () -> {
                        ClusterDraftState draft = session.draft();
                        if (draft != null) draft.append(link.ordinal());
                    }).widthRel(1)
                        .height(28)
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
            .pos(216, 44)
            .size(366, 170);
        for (int i = 0; i < 16; i++) {
            final int index = i;
            Flow row = Flow.row()
                .name("cluster_draft_step_" + index)
                .widthRel(1)
                .height(24)
                .childPadding(2)
                .setEnabledIf(widget -> session.draft() != null && index < session.draft().ordinals.size());
            row.child(text("cluster_draft_label_" + index, () -> {
                ClusterDraftState draft = session.draft();
                return draft == null || index >= draft.ordinals.size() ? ""
                    : (index + 1) + ". " + tr(LINKS[draft.ordinals.get(index)].getLangKey());
            }).width(208)
                .height(24)
                .tooltipBuilder(tooltip -> {
                    ClusterDraftState draft = session.draft();
                    if (draft != null && index < draft.ordinals.size()) {
                        int ordinal = draft.ordinals.get(index);
                        int tier = Math.max(0, Math.min(3, session.integer(ClusterTerminalData.KEY_TIER, 0)));
                        double seconds = LINKS[ordinal].getBaseSecondsPrecise() * ClusterParams.TIER_TIME_FACTOR[tier]
                            / Math.max(1, lock(session, ordinal, 1));
                        tooltip.addLine(IKey.lang(LINKS[ordinal].getLangKey()));
                        tooltip.addLine(IKey.lang("gtsr.cluster.mui2.actual_seconds", seconds));
                        tooltip.addLine(IKey.str(flow(draft)));
                    }
                })
                .tooltipAutoUpdate(true));
            row.child(
                button(
                    "cluster_peak_" + index,
                    () -> session.draft() != null && session.draft().peak == index ? "★" : "☆",
                    () -> {
                        if (session.draft() != null) session.draft()
                            .peak(index);
                    }).size(28, 22));
            row.child(
                button(
                    "cluster_move_up_" + index,
                    () -> "↑",
                    () -> {
                        if (session.draft() != null) session.draft()
                            .move(index, -1);
                    }).size(28, 22));
            row.child(
                button(
                    "cluster_move_down_" + index,
                    () -> "↓",
                    () -> {
                        if (session.draft() != null) session.draft()
                            .move(index, 1);
                    }).size(28, 22));
            row.child(
                button(
                    "cluster_remove_" + index,
                    () -> "×",
                    () -> {
                        if (session.draft() != null) session.draft()
                            .remove(index);
                    }).size(28, 22));
            chain.child(row);
        }
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
        }).pos(216, 218)
            .size(366, 18));
        page.child(
            text("cluster_chain_feedback", () -> tr(session.receipt())).pos(136, 58)
                .size(74, 20));
        page.child(
            text("cluster_chain_formula", () -> tr("gtsr.cluster.mui2.formula")).pos(216, 238)
                .size(366, 18)
                .tooltipBuilder(tooltip -> {
                    tooltip.addLine(IKey.str(flow(session.draft())));
                    for (ClusterFormulaText.Line line : ClusterFormulaText
                        .parse(session.string(ClusterTerminalData.KEY_F_FORMULA))) tooltip.addLine(IKey.str(line.text));
                })
                .tooltipAutoUpdate(true));
        return page;
    }

    private static ParentWidget<?> boosters(ClusterSessionSyncHandler session, View view) {
        ParentWidget<?> page = page("cluster_boosters");
        ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> list = new ListWidget<>()
            .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
            .name("cluster_booster_list")
            .pos(0, 0)
            .size(582, 156);
        for (int i = 0; i < 20; i++) {
            final int index = i;
            list.child(
                text("cluster_booster_row_" + i, () -> view.booster(index)).widthRel(1)
                    .height(30)
                    .setEnabledIf(widget -> index < view.boosterRows.size())
                    .tooltipBuilder(tooltip -> {
                        tooltip.addLine(IKey.str(view.booster(index)));
                        tooltip.addLine(IKey.str(view.boosterFormula(index)));
                    })
                    .tooltipAutoUpdate(true));
        }
        page.child(list);
        String[] summaries = { "speed", "parallel", "primary", "secondary", "saver", "mult" };
        for (int i = 0; i < summaries.length; i++) {
            final int index = i;
            page.child(
                new ParentWidget<>().name("cluster_booster_card_" + i)
                    .pos((i % 3) * 194, 160 + (i / 3) * 36)
                    .size(190, 32)
                    .background(TerminalUiStyle.CARD)
                    .child(
                        text(
                            "cluster_booster_summary_" + i,
                            () -> tr("gtsr.cluster.gui.boost.sum." + summaries[index]) + " " + view.summary(index))
                                .pos(3, 2)
                                .size(184, 28)));
        }
        page.child(
            text("cluster_booster_rules", () -> tr("gtsr.cluster.gui.boost.rule.stack")).pos(0, 234)
                .size(582, 22));
        return page;
    }

    private static ParentWidget<?> stats(ClusterSessionSyncHandler session, View view) {
        ParentWidget<?> page = page("cluster_stats");
        page.child(
            button(
                "cluster_clear_stats",
                () -> tr(session.clearArmed() ? "gtsr.terminal.stats.clear.confirm" : "gtsr.terminal.stats.clear"),
                session::clearStats).pos(450, 0)
                    .size(132, 20));
        String[] ledgers = { "in", "out", "bonus" };
        for (int ledger = 0; ledger < 3; ledger++) {
            final int col = ledger;
            page.child(
                text("cluster_stats_title_" + col, () -> tr("gtsr.terminal.stats." + ledgers[col])).pos(col * 194, 2)
                    .size(150, 18));
            ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> list = new ListWidget<>()
                .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
                .name("cluster_stats_list_" + col)
                .pos(col * 194, 24)
                .size(190, 234);
            for (int rowIndex = 0; rowIndex < 8; rowIndex++) {
                Flow row = Flow.row()
                    .name("cluster_stats_row_" + col + "_" + rowIndex)
                    .widthRel(1)
                    .height(28);
                for (int x = 0; x < 8; x++) {
                    final int entry = rowIndex * 8 + x;
                    row.child(
                        new ParentWidget<>().name("cluster_stat_" + col + "_" + entry)
                            .size(22, 28)
                            .child(
                                new ItemDisplayWidget().name("cluster_stat_icon_" + col + "_" + entry)
                                    .size(18)
                                    .item(
                                        new ObjectValue.Dynamic<>(
                                            ItemStack.class,
                                            () -> view.stats[col][entry].stack,
                                            ignored -> {})))
                            .child(
                                text(
                                    "cluster_stat_count_" + col + "_" + entry,
                                    () -> view.stats[col][entry].stack == null ? ""
                                        : GtsrNumFormat.compact3(view.stats[col][entry].count)).scale(0.6f)
                                            .pos(0, 18)
                                            .size(22, 10))
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
        for (int c = 0; c < 3; c++) {
            final int column = c;
            ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> list = new ListWidget<>()
                .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
                .name("cluster_perf_list_" + c)
                .pos(c * 194, 0)
                .size(190, 258);
            for (int i = 0; i < 160; i++) {
                final int index = i;
                ButtonWidget<?> row = button("cluster_perf_row_" + c + "_" + i, () -> "", () -> {
                    ClusterPerformanceModel.PerfRow value = session.performance.row(column, index);
                    if (value != null && value.entryKey != null) session.performance.toggle(value.entryKey);
                }).widthRel(1)
                    .coverChildrenHeight()
                    .background(com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY)
                    .setEnabledIf(widget -> session.performance.row(column, index) != null);
                row.child(new PerformanceText(IKey.dynamic(() -> {
                    ClusterPerformanceModel.PerfRow value = session.performance.row(column, index);
                    return value == null ? "" : value.text;
                })).name("cluster_perf_text_" + c + "_" + i)
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .widthRel(1)
                    .scale(0.75f)
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
        return new TextWidget<>(IKey.dynamic(value)).name(name)
            .color(GtsrGuiPalette.TEXT_BODY)
            .scale(0.85f);
    }

    private static ButtonWidget<?> button(String name, Supplier<String> label, Runnable action) {
        return new ButtonWidget<>().name(name)
            .background(TerminalUiStyle.BUTTON)
            .hoverBackground(TerminalUiStyle.HOVER)
            .overlay(
                IKey.dynamic(label)
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
        if (err > 0 && err != 255) return tr("gtsr.cluster.mui2.error") + " " + err;
        int run = session.run(slot);
        ClusterUnitStatus[] statuses = ClusterUnitStatus.values();
        return run < statuses.length ? tr(statuses[run].getLangKey()) : "--";
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
