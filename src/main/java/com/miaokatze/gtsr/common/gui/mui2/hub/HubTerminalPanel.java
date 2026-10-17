package com.miaokatze.gtsr.common.gui.mui2.hub;

import java.util.Locale;
import java.util.function.BooleanSupplier;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;
import com.miaokatze.gtsr.common.gui.mui2.TerminalViewport;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/** Native, persistent controls: a bounded row pool, local drafts and one explicitly registered session. */
public final class HubTerminalPanel {

    private HubTerminalPanel() {}

    /** Headless probes may call this production builder directly; route/permission testing uses the factory. */
    public static ModularPanel build(HubTerminalFactory factory, PosGuiData data, IMetaTileEntity machine,
        PanelSyncManager manager) {
        HubTerminalSession session = new HubTerminalSession(factory, data, machine);
        manager.syncValue(HubTerminalSession.SYNC_KEY, session);
        return new View(factory, session).build();
    }

    private static final class View {

        final HubTerminalFactory factory;
        final HubTerminalSession session;
        final HubTerminalViewState view;
        final PagedWidget.Controller pages = new PagedWidget.Controller();
        TextFieldWidget numberField;
        String language = "";

        View(HubTerminalFactory factory, HubTerminalSession session) {
            this.factory = factory;
            this.session = session;
            this.view = session.view;
            this.view.searchText = row -> HubClientText.name(row) + " "
                + row.type()
                + (row.cache == null ? "" : " " + HubClientText.fluid(row.cache.fluid) + " " + row.cache.fluid);
        }

        ModularPanel build() {
            return terminal();
        }

        /** Restore the native handheld hub canvas and its anchored single-node editor. */
        ModularPanel terminal() {
            ParentWidget<?> content = new ParentWidget<>().size(384, 234)
                .pos(0, 0);
            content.child(
                label(IKey.lang(title()), 0, 3, 206, 12, 1f, GtsrGuiPalette.TEXT_TITLE).name("hub_title")
                    .addTooltipLine(IKey.lang(title())));
            ParentWidget<?> main = new ParentWidget<>().size(384, 234);
            if (!view.nearby) {
                main.child(
                    new TextFieldWidget().name("hub_search")
                        .pos(242, 3)
                        .size(110, 12)
                        .setMaxLength(64)
                        .autoUpdateOnChange(true)
                        .value(new StringValue.Dynamic(() -> view.query, view::query))
                        .addTooltipLine(IKey.lang("gtsr.cache_hub_status.filter")));
                main.child(
                    new ButtonWidget<>().name("hub_sort")
                        .pos(356, 3)
                        .size(28, 12)
                        .background(TerminalUiStyle.BUTTON)
                        .hoverBackground(TerminalUiStyle.HOVER)
                        .overlay(
                            IKey.str("#")
                                .color(GtsrGuiPalette.TEXT_BODY))
                        .addTooltipLine(IKey.dynamicKey(() -> IKey.lang("gtsr.mui2.hub.sort." + view.sort)))
                        .tooltipAutoUpdate(true)
                        .onMousePressed(button -> {
                            if (button == 0) view.nextSort();
                            return true;
                        }));
            }
            main.child(header().pos(0, 15));
            if (view.nearby) main.child(nodeCard().pos(0, 24));
            else {
                VerticalScrollData scroll = new VerticalScrollData(false, 4);
                scroll.texture(TerminalUiStyle.SCROLLBAR_THUMB);
                scroll.setCancelScrollEdge(false);
                ListWidget<IWidget, ?> list = new ListWidget<>().name("hub_nodes")
                    .pos(0, 24)
                    .size(384, 160)
                    .scrollDirection(scroll)
                    .crossAxisAlignment(Alignment.CrossAxis.START)
                    .background(TerminalUiStyle.CARD)
                    .collapseDisabledChild();
                for (int i = 0; i < HubTerminalViewState.PAGE_SIZE; i++) list.child(row(i));
                main.child(list);
                main.child(
                    label(IKey.lang("gtsr.hub_status.empty"), 6, 30, 360, 14, 1f, GtsrGuiPalette.TEXT_MUTED)
                        .setEnabledIf(w -> view.filtered() == 0));
                main.child(
                    compact("gtsr.mui2.hub.previous", "hub_previous", 40, () -> view.page > 0, () -> view.page(-1))
                        .pos(0, 186)
                        .overlay(
                            IKey.str("<")
                                .color(
                                    () -> !session.awaiting && view.page > 0 ? GtsrGuiPalette.TEXT_BODY
                                        : GtsrGuiPalette.TEXT_MUTED)));
                main.child(
                    label(
                        IKey.dynamic(
                            () -> (view.page + 1) + " / "
                                + view.pages()
                                + "  ("
                                + view.filtered()
                                + " / "
                                + view.total()
                                + ")"),
                        44,
                        186,
                        96,
                        14,
                        0.75f,
                        GtsrGuiPalette.TEXT_LABEL).name("hub_page"));
                main.child(
                    compact(
                        "gtsr.mui2.hub.next",
                        "hub_next",
                        40,
                        () -> view.page + 1 < view.pages(),
                        () -> view.page(1)).pos(144, 186)
                            .overlay(
                                IKey.str(">")
                                    .color(
                                        () -> !session.awaiting && view.page + 1 < view.pages()
                                            ? GtsrGuiPalette.TEXT_BODY
                                            : GtsrGuiPalette.TEXT_MUTED)));
            }
            main.child(
                label(
                    IKey.dynamic(
                        () -> selected() ? view.selectedRow().key.toString()
                            : IKey.lang("gtsr.mui2.hub.select")
                                .getFormatted()),
                    view.nearby ? 0 : 190,
                    186,
                    view.nearby ? 280 : view.drilling ? 194 : 92,
                    14,
                    0.7f,
                    GtsrGuiPalette.TEXT_MUTED).name("hub_selection")
                        .addTooltipLine(
                            IKey.dynamic(
                                () -> selected() ? view.selectedRow().key.toString()
                                    : IKey.lang("gtsr.mui2.hub.select")
                                        .getFormatted()))
                        .tooltipAutoUpdate(true));
            main.child(
                new ParentWidget<>().pos(0, 204)
                    .size(140, 14)
                    .background(
                        new Rectangle().color(0xFF101216),
                        new Rectangle().color(0xFF62666A)
                            .hollow())
                    .child(label(IKey.dynamic(view::nameDraft), 3, 0, 134, 14, 1f, GtsrGuiPalette.TEXT_MUTED))
                    .setEnabledIf(w -> !live())
                    .addTooltipLine(IKey.lang("gtsr.hub_status.rename_hint")));
            main.child(
                new CommandField(this::rename, () -> {}).name("hub_name")
                    .pos(0, 204)
                    .size(140, 14)
                    .setMaxLength(24)
                    .autoUpdateOnChange(true)
                    .value(new StringValue.Dynamic(view::nameDraft, view::nameDraft))
                    .setEnabledIf(w -> live())
                    .addTooltipLine(IKey.lang("gtsr.hub_status.rename_hint")));
            main.child(compact("gtsr.mui2.hub.rename", "hub_rename", 64, this::live, this::rename).pos(144, 204));
            if (view.drilling) {
                main.child(
                    compact("gtsr.mui2.hub.teleport", "hub_teleport", 64, this::selected, () -> send(5, null))
                        .pos(0, 219));
                main.child(
                    compact(
                        "gtsr.mui2.hub.toggle",
                        "hub_toggle",
                        78,
                        this::selected,
                        () -> send(1, !view.selectedRow().worker.allowed)).pos(66, 219)
                            .overlay(
                                caption(
                                    IKey.dynamicKey(
                                        () -> IKey.lang(
                                            selected() && view.selectedRow().worker.allowed ? "gtsr.hub_status.stop"
                                                : "gtsr.hub_status.start")),
                                    78).color(
                                        () -> !session.awaiting && selected() ? GtsrGuiPalette.TEXT_BODY
                                            : GtsrGuiPalette.TEXT_MUTED)));
                main.child(
                    compact(
                        "gtsr.mui2.hub.recycle",
                        "hub_recycle",
                        78,
                        () -> selected() && view.selectedRow().worker.recyclable,
                        () -> send(2, null)).pos(146, 219));
                main.child(
                    compact("gtsr.mui2.hub.upgrade", "hub_upgrade", 78, this::selected, () -> send(3, null))
                        .pos(226, 219));
                main.child(
                    label(
                        IKey.dynamicKey(() -> IKey.lang(workerStatus(view.selectedRow()))),
                        308,
                        219,
                        76,
                        14,
                        0.7f,
                        GtsrGuiPalette.TEXT_BODY).name("hub_working")
                            .color(() -> workerColor(view.selectedRow())));
            } else {
                main.child(
                    compact(
                        "gtsr.mui2.hub.teleport",
                        "hub_teleport",
                        56,
                        () -> !view.nearby && selected(),
                        () -> send(5, null)).pos(0, 219));
                main.child(
                    compact("gtsr.cache_hub_status.edit_rate", "hub_rate", 48, this::live, () -> edit(7)).pos(58, 219));
                main.child(
                    compact(
                        "gtsr.cache_hub_status.edit_capacity",
                        "hub_capacity",
                        48,
                        () -> live() && view.selectedRow()
                            .capacity(),
                        () -> edit(8)).pos(108, 219));
                main.child(
                    compact(
                        "gtsr.mui2.hub.mode",
                        "hub_mode",
                        136,
                        () -> !view.nearby && live() && !view.selectedRow().cache.modeLocked,
                        () -> send(2, !view.selectedRow().cache.out)).pos(158, 219)
                            .overlay(
                                caption(
                                    IKey.dynamicKey(
                                        () -> IKey.lang(
                                            selected() && view.selectedRow().cache.out
                                                ? "gtsr.cache_hub_status.mode_short_input"
                                                : "gtsr.cache_hub_status.mode_short_output")),
                                    136).color(
                                        () -> !session.awaiting && !view.nearby
                                            && live()
                                            && !view.selectedRow().cache.modeLocked ? GtsrGuiPalette.TEXT_BODY
                                                : GtsrGuiPalette.TEXT_MUTED)));
                main.child(
                    compact(
                        "gtsr.mui2.hub.auto",
                        "hub_auto",
                        68,
                        () -> !view.nearby && live(),
                        () -> send(4, !view.selectedRow().cache.auto)).pos(296, 219)
                            .overlay(
                                caption(
                                    IKey.dynamicKey(
                                        () -> IKey.lang(
                                            selected() && view.selectedRow().cache.auto
                                                ? "gtsr.cache_hub_status.auto_on"
                                                : "gtsr.cache_hub_status.auto_off")),
                                    68).color(
                                        () -> !session.awaiting && !view.nearby && live() ? GtsrGuiPalette.TEXT_BODY
                                            : GtsrGuiPalette.TEXT_MUTED)));
                main.child(
                    label(
                        IKey.dynamicKey(
                            () -> IKey.lang(
                                selected() && view.selectedRow().cache.out ? "gtsr.mui2.hub.direction_in"
                                    : "gtsr.mui2.hub.direction_out")),
                        286,
                        186,
                        98,
                        14,
                        0.7f,
                        GtsrGuiPalette.TEXT_ACCENT).name("hub_mode_state"));
            }
            content.child(
                new PagedWidget<>().name("hub_pages")
                    .size(384, 234)
                    .controller(pages)
                    .addPage(main)
                    .addPage(
                        editor(196).pos(4, 28)
                            .background(TerminalUiStyle.CARD)));
            content.child(
                label(
                    IKey.dynamicKey(() -> IKey.lang(session.receiptKey)),
                    216,
                    204,
                    168,
                    14,
                    0.65f,
                    GtsrGuiPalette.TEXT_ACCENT).name("hub_receipt")
                        .addTooltipLine(IKey.dynamicKey(() -> IKey.lang(session.receiptKey)))
                        .tooltipAutoUpdate(true));
            return ModularPanel
                .defaultPanel(view.nearby ? "cache_node_editor" : HubTerminalFactory.PANEL_NAME, 400, 240)
                .background(TerminalUiStyle.HUB_PANEL)
                .child(
                    TerminalViewport.wrapExact("hub_viewport", content, 384, 234)
                        .pos(8, 0)
                        .widthRelOffset(1f, -16)
                        .heightRelOffset(1f, -6))
                .onUpdateListener(panel -> {
                    session.consume(view);
                    String nextLanguage = HubClientText.language();
                    if (!language.equals(nextLanguage)) {
                        language = nextLanguage;
                        view.refresh();
                    }
                    if (pages.isInitialised() && view.editing == null && pages.getActivePageIndex() == 1)
                        pages.setPage(0);
                });
        }

        ButtonWidget<?> compact(String key, String name, int width, BooleanSupplier enabled, Runnable callback) {
            return action(key, name, width, enabled, callback).height(14)
                .background(
                    new DynamicDrawable(
                        () -> !session.awaiting && enabled.getAsBoolean() ? TerminalUiStyle.BUTTON
                            : TerminalUiStyle.DISABLED))
                .hoverBackground(
                    new DynamicDrawable(
                        () -> !session.awaiting && enabled.getAsBoolean() ? TerminalUiStyle.HOVER
                            : TerminalUiStyle.DISABLED))
                .overlay(
                    caption(IKey.lang(key), width).color(
                        () -> !session.awaiting && enabled.getAsBoolean() ? GtsrGuiPalette.TEXT_BODY
                            : GtsrGuiPalette.TEXT_MUTED))
                .addTooltipLine(IKey.lang(key));
        }

        IKey caption(IKey key, int width) {
            return IKey.dynamic(() -> HubClientText.clip(key.getFormatted(), width - 4))
                .color(GtsrGuiPalette.TEXT_BODY)
                .shadow(true);
        }

        TextWidget<?> label(IKey key, int x, int y, int width, int height, float scale, int color) {
            return IKey.dynamic(() -> HubClientText.clip(key.getFormatted(), (int) (width / scale)))
                .asWidget()
                .pos(x, y)
                .size(width, height)
                .scale(scale)
                .color(color)
                .shadow(true)
                .textAlign(Alignment.CenterLeft);
        }

        String workerStatus(HubTerminalViewState.Row row) {
            return row == null || !row.worker.allowed ? "gtsr.hub_status.status.stopped"
                : row.worker.working ? "gtsr.gui.status.running" : "gtsr.gui.status.idle";
        }

        int workerColor(HubTerminalViewState.Row row) {
            return row == null || !row.worker.allowed ? GtsrGuiPalette.STATE_OFFLINE
                : row.worker.working ? GtsrGuiPalette.STATE_ONLINE : GtsrGuiPalette.STATE_IDLE;
        }

        String title() {
            switch (factory.type) {
                case STEAM_HUB:
                    return "gtsr.cache_hub_status.title.steam";
                case WATER_HUB:
                    return "gtsr.cache_hub_status.title.water";
                case CACHE_NODE:
                    return "gtsr.cache_hub_status.node_editor";
                default:
                    return "gtsr.hub_status.title";
            }
        }

        boolean selected() {
            return view.selectedRow() != null;
        }

        boolean live() {
            return selected() && view.selectedRow()
                .online();
        }

        ParentWidget<?> header() {
            ParentWidget<?> header = new ParentWidget<>().size(376, 9);
            if (!view.drilling) {
                String[] keys = { "stored", "cap", "rate" };
                int[] x = { 162, 238, 314 };
                for (int i = 0; i < keys.length; i++) header.child(
                    label(
                        IKey.lang("gtsr.cache_hub_status.col." + keys[i]),
                        x[i],
                        0,
                        i == 2 ? 60 : 70,
                        9,
                        1f,
                        GtsrGuiPalette.TEXT_LABEL).textAlign(Alignment.CenterRight));
            }
            return header;
        }

        ButtonWidget<?> row(int index) {
            ParentWidget<?> cells = new ParentWidget<>().size(376, 20);
            ItemDrawable icon = new ItemDrawable();
            cells.child(new Decoration().background(new DynamicDrawable(() -> {
                HubTerminalViewState.Row row = view.row(index);
                ItemStack stack = row == null ? null
                    : row.worker == null ? HubClientText.icon(row.type())
                        : (row.worker.isMiner ? GTSRItemList.SingularityMinerNode
                            : GTSRItemList.SingularityDrillingNode).get(1);
                return icon.setItem(stack);
            }))
                .pos(4, 2)
                .size(16, 16));
            cells.child(
                new Decoration().background(new Rectangle().color(GtsrGuiPalette.TEXT_ACCENT))
                    .size(2, 20)
                    .setEnabledIf(w -> view.row(index) != null && view.row(index).key.equals(view.selected)));
            if (view.drilling) {
                cells.child(
                    label(
                        IKey.dynamic(() -> HubClientText.name(view.row(index))),
                        24,
                        0,
                        170,
                        10,
                        1f,
                        GtsrGuiPalette.TEXT_BODY));
                cells.child(
                    label(
                        IKey.dynamic(() -> view.row(index) == null ? "" : "Mk" + (view.row(index).worker.tier + 1)),
                        198,
                        0,
                        40,
                        10,
                        1f,
                        GtsrGuiPalette.TEXT_ACCENT));
                cells.child(
                    label(
                        IKey.dynamicKey(() -> IKey.lang(workerStatus(view.row(index)))),
                        242,
                        0,
                        128,
                        10,
                        1f,
                        GtsrGuiPalette.TEXT_BODY).color(() -> workerColor(view.row(index))));
                cells.child(
                    label(
                        IKey.dynamic(() -> view.row(index) == null ? "" : view.row(index).key.toString()),
                        24,
                        10,
                        346,
                        10,
                        1f,
                        GtsrGuiPalette.TEXT_MUTED));
            } else {
                cells.child(
                    label(
                        IKey.dynamic(() -> view.row(index) == null ? "" : view.row(index).cache.out ? "<" : ">"),
                        23,
                        0,
                        10,
                        20,
                        1f,
                        GtsrGuiPalette.TEXT_ACCENT).color(
                            () -> view.row(index) != null && view.row(index).cache.out ? GtsrGuiPalette.STATE_ONLINE
                                : GtsrGuiPalette.TEXT_ACCENT));
                cells.child(label(IKey.dynamic(() -> {
                    HubTerminalViewState.Row row = view.row(index);
                    if (row == null) return "";
                    String suffix = row.online() ? ""
                        : " §c" + IKey.lang("gtsr.cache_hub_status.offline")
                            .getFormatted();
                    return HubClientText.clip(HubClientText.name(row), 122 - (row.online() ? 0 : 54)) + suffix;
                }), 34, 0, 122, 10, 1f, GtsrGuiPalette.TEXT_BODY));
                cells.child(
                    label(
                        IKey.dynamic(
                            () -> view.row(index) == null ? "" : HubClientText.fluid(view.row(index).cache.fluid)),
                        34,
                        10,
                        122,
                        10,
                        1f,
                        GtsrGuiPalette.TEXT_ACCENT));
                int[] x = { 162, 238, 314 };
                for (int column = 0; column < 3; column++) {
                    final int c = column;
                    cells.child(
                        label(
                            IKey.dynamic(
                                () -> view.row(index) == null ? ""
                                    : amount(
                                        view.row(index)
                                            .number(c))
                                        + (c == 2 ? "/s" : "")),
                            x[c],
                            0,
                            c == 2 ? 60 : 70,
                            20,
                            1f,
                            c == 0 ? GtsrGuiPalette.TEXT_BODY
                                : c == 1 ? GtsrGuiPalette.TEXT_MUTED : GtsrGuiPalette.TEXT_ACCENT)
                                    .textAlign(Alignment.CenterRight));
                }
            }
            return new ButtonWidget<>().name("hub_row_" + index)
                .size(376, 20)
                .background(
                    new DynamicDrawable(
                        () -> view.row(index) != null && view.row(index).key.equals(view.selected)
                            ? new Rectangle().color(0xAA805020)
                            : null))
                .hoverBackground(TerminalUiStyle.ROW_HOVER)
                .child(cells)
                .setEnabledIf(w -> view.row(index) != null)
                .onMousePressed(button -> {
                    if (button == 0) view.select(index);
                    return true;
                })
                .tooltipBuilder(t -> {
                    HubTerminalViewState.Row row = view.row(index);
                    if (row == null) return;
                    t.addLine(IKey.str(HubClientText.name(row)));
                    t.addLine(IKey.str(row.key.toString()));
                    if (row.cache != null) {
                        t.addLine(IKey.str(row.cache.stored + " / " + row.cache.cap + " L"));
                        t.addLine(IKey.str(row.cache.actualRate + " / " + row.cache.maxRate + " L/s"));
                        t.addLine(
                            IKey.lang("gtsr.mui2.hub.limits", row.cache.maxCap, row.cache.rate, row.cache.capPct));
                    } else t.addLine(
                        IKey.lang(
                            "gtsr.mui2.hub.worker_flags",
                            row.worker.allowed,
                            row.worker.working,
                            row.worker.retractable,
                            row.worker.recyclable));
                })
                .tooltipAutoUpdate(true);
        }

        String amount(long value) {
            if (value < 1000) return value + "L";
            if (value >= 1_000_000_000L) return String.format(Locale.ROOT, "%.2fG", value / 1_000_000_000d);
            if (value >= 1_000_000L) return String.format(Locale.ROOT, "%.2fM", value / 1_000_000d);
            return String.format(Locale.ROOT, "%.2fK", value / 1_000d);
        }

        ParentWidget<?> nodeCard() {
            return new ParentWidget<>().name("cache_node_card")
                .size(384, 160)
                .background(TerminalUiStyle.CARD)
                .child(row(0).pos(0, 0))
                .child(
                    label(
                        IKey.lang("gtsr.cache_hub_status.col.stored"),
                        12,
                        42,
                        164,
                        14,
                        1f,
                        GtsrGuiPalette.TEXT_LABEL))
                .child(
                    label(
                        IKey.dynamic(
                            () -> selected()
                                ? view.selectedRow().cache.stored + " / " + view.selectedRow().cache.cap + " L"
                                : "—"),
                        12,
                        58,
                        356,
                        16,
                        1f,
                        GtsrGuiPalette.TEXT_BODY).name("cache_node_storage"))
                .child(
                    label(IKey.lang("gtsr.cache_hub_status.col.rate"), 12, 86, 164, 14, 1f, GtsrGuiPalette.TEXT_LABEL))
                .child(
                    label(
                        IKey.dynamic(
                            () -> selected()
                                ? view.selectedRow().cache.actualRate + " / "
                                    + view.selectedRow().cache.maxRate
                                    + " L/s"
                                : "—"),
                        12,
                        102,
                        356,
                        16,
                        1f,
                        GtsrGuiPalette.TEXT_ACCENT).name("cache_node_rate"))
                .child(
                    label(
                        IKey.dynamic(() -> selected() ? HubClientText.fluid(view.selectedRow().cache.fluid) : "—"),
                        12,
                        136,
                        356,
                        14,
                        1f,
                        GtsrGuiPalette.TEXT_MUTED));
        }

        Flow editor(int height) {
            Runnable cancel = () -> {
                view.cancelEdit();
                pages.setPage(0);
            };
            numberField = new CommandField(this::confirm, cancel).name("hub_numeric_input")
                .size(360, 20)
                .setMaxLength(19)
                .autoUpdateOnChange(true)
                .value(new StringValue.Dynamic(() -> view.numericDraft, value -> view.numericDraft = value));
            return Flow.col()
                .name("hub_numeric_editor")
                .size(376, height)
                .padding(8)
                .childPadding(8)
                .child(
                    IKey.dynamicKey(
                        () -> IKey.lang(
                            view.editingAction == 7 ? "gtsr.cache_hub_status.edit_rate"
                                : "gtsr.cache_hub_status.edit_capacity"))
                        .asWidget()
                        .color(GtsrGuiPalette.TEXT_BODY)
                        .size(360, 20))
                .child(IKey.dynamic(() -> {
                    HubTerminalViewState.Row row = view.selectedRow();
                    return row == null ? "—"
                        : (view.editingAction == 7 ? "0 .. " + row.cache.maxRate + " L/s"
                            : "1 .. " + row.cache.maxCap + " L");
                })
                    .asWidget()
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .name("hub_numeric_range")
                    .size(360, 20))
                .child(numberField)
                .child(
                    Flow.row()
                        .size(360, 20)
                        .childPadding(8)
                        .child(
                            action(
                                "gtsr.cache_hub_status.confirm",
                                "hub_numeric_apply",
                                176,
                                this::live,
                                this::confirm))
                        .child(action("gtsr.cache_hub_status.cancel", "hub_numeric_cancel", 176, () -> true, cancel)));
        }

        void edit(int action) {
            view.edit(action);
            if (view.editing == null) return;
            pages.setPage(1);
            numberField.setText(view.numericDraft);
            numberField.getContext()
                .focus(numberField);
        }

        void confirm() {
            if (session.awaiting) return;
            try {
                byte[] payload = view.numericPayload(numberField.getText());
                session.submit(view.editingAction, payload);
                view.cancelEdit();
                pages.setPage(0);
            } catch (IllegalArgumentException error) {
                session.receiptKey = "gtsr.cache_hub_status.invalid_value";
            }
        }

        void rename() {
            if (!live()) return;
            session.submit(view.drilling ? 4 : 3, view.selectedPayload(null, view.nameDraft()));
        }

        void send(int action, Boolean target) {
            if (!selected()) return;
            session.submit(action, view.selectedPayload(target, null));
        }

        ButtonWidget<?> action(String key, String name, int width, BooleanSupplier enabled, Runnable callback) {
            ButtonWidget<?> button = TerminalUiStyle.button(key)
                .name(name)
                .size(width, 18)
                .background(
                    new DynamicDrawable(
                        () -> !session.awaiting && enabled.getAsBoolean() ? TerminalUiStyle.BUTTON
                            : TerminalUiStyle.DISABLED))
                .hoverBackground(
                    new DynamicDrawable(
                        () -> !session.awaiting && enabled.getAsBoolean() ? TerminalUiStyle.HOVER
                            : TerminalUiStyle.DISABLED))
                .overlay(
                    caption(IKey.lang(key), width).color(
                        () -> !session.awaiting && enabled.getAsBoolean() ? GtsrGuiPalette.TEXT_BODY
                            : GtsrGuiPalette.TEXT_MUTED))
                .onMousePressed(mouse -> {
                    if (mouse == 0 && !session.awaiting && enabled.getAsBoolean()) callback.run();
                    return true;
                });
            String hint = switch (name) {
                case "hub_teleport" -> "gtsr.hub_status.teleport";
                case "hub_upgrade" -> "gtsr.hub_status.upgrade";
                case "hub_recycle" -> "gtsr.hub_status.recycle";
                case "hub_rate" -> "gtsr.cache_hub_status.rate_tip";
                case "hub_capacity" -> "gtsr.cache_hub_status.cap_tip";
                case "hub_auto" -> "gtsr.cache_hub_status.auto_tip";
                case "hub_rename" -> "gtsr.hub_status.rename_hint";
                default -> null;
            };
            if (hint != null) button.addTooltipLine(IKey.lang(hint));
            if (name.equals("hub_mode")) button.addTooltipLine(
                IKey.dynamicKey(
                    () -> IKey.lang(
                        selected() && view.selectedRow().cache.out ? "gtsr.cache_hub_status.mode_tip_input"
                            : "gtsr.cache_hub_status.mode_tip_output")));
            return button;
        }
    }

    /** Passive row ornament: native rendering and hit transforms, without stealing the parent row hover. */
    private static final class Decoration extends Widget<Decoration> {

        @Override
        public boolean canHover() {
            return false;
        }
    }

    /** Native text input semantics with explicit Enter/escape draft handling; never edits a server value. */
    private static final class CommandField extends TextFieldWidget {

        private final Runnable enter, escape;

        CommandField(Runnable enter, Runnable escape) {
            this.enter = enter;
            this.escape = escape;
        }

        @Override
        public Result onKeyPressed(char character, int keyCode) {
            if (isFocused() && (keyCode == 28 || keyCode == 156)) {
                enter.run();
                getContext().removeFocus();
                return Result.SUCCESS;
            }
            if (isFocused() && keyCode == 1) {
                escape.run();
                getContext().removeFocus();
                return Result.SUCCESS;
            }
            return super.onKeyPressed(character, keyCode);
        }
    }
}
