package com.miaokatze.gtsr.common.gui.mui2.hub;

import java.util.function.BooleanSupplier;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
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
            int height = view.nearby ? 238 : 312;
            Flow content = Flow.col()
                .size(376, height - 24)
                .childPadding(2)
                .child(
                    IKey.lang(title())
                        .asWidget()
                        .color(GtsrGuiPalette.TEXT_BODY)
                        .name("hub_title")
                        .color(GtsrGuiPalette.TEXT_TITLE)
                        .size(376, 18));
            Flow main = Flow.col()
                .size(376, height - 64)
                .childPadding(2);
            if (view.nearby) main.child(nodeCard());
            else {
                main.child(
                    Flow.row()
                        .size(376, 20)
                        .childPadding(4)
                        .child(
                            new TextFieldWidget().name("hub_search")
                                .size(244, 20)
                                .setMaxLength(64)
                                .autoUpdateOnChange(true)
                                .value(new StringValue.Dynamic(() -> view.query, view::query))
                                .addTooltipLine(IKey.lang("gtsr.cache_hub_status.filter")))
                        .child(
                            new ButtonWidget<>().name("hub_sort")
                                .size(128, 20)
                                .background(TerminalUiStyle.BUTTON)
                                .hoverBackground(TerminalUiStyle.HOVER)
                                .overlay(IKey.dynamicKey(() -> IKey.lang("gtsr.mui2.hub.sort." + view.sort)))
                                .onMousePressed(button -> {
                                    if (button == 0) view.nextSort();
                                    return true;
                                })));
                main.child(header());
                VerticalScrollData innerScroll = new VerticalScrollData();
                innerScroll.setCancelScrollEdge(false);
                ListWidget<IWidget, ?> list = new ListWidget<>().name("hub_nodes")
                    .size(376, 114)
                    .scrollDirection(innerScroll)
                    .background(TerminalUiStyle.CARD)
                    .collapseDisabledChild();
                for (int i = 0; i < HubTerminalViewState.PAGE_SIZE; i++) list.child(row(i));
                main.child(list);
                main.child(
                    Flow.row()
                        .size(376, 16)
                        .childPadding(4)
                        .child(
                            action(
                                "gtsr.mui2.hub.previous",
                                "hub_previous",
                                64,
                                () -> view.page > 0,
                                () -> view.page(-1)))
                        .child(
                            IKey.dynamic(
                                () -> (view.page + 1) + " / "
                                    + view.pages()
                                    + "  ("
                                    + view.filtered()
                                    + " / "
                                    + view.total()
                                    + ")")
                                .asWidget()
                                .color(GtsrGuiPalette.TEXT_BODY)
                                .name("hub_page")
                                .size(240, 16))
                        .child(
                            action(
                                "gtsr.mui2.hub.next",
                                "hub_next",
                                64,
                                () -> view.page + 1 < view.pages(),
                                () -> view.page(1))));
                main.child(IKey.dynamic(() -> {
                    HubTerminalViewState.Row row = view.selectedRow();
                    return row == null ? IKey.lang("gtsr.mui2.hub.select")
                        .getFormatted() : row.key.toString();
                })
                    .asWidget()
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .name("hub_selection")
                    .size(376, 14));
            }
            main.child(
                Flow.row()
                    .size(376, 20)
                    .childPadding(4)
                    .child(
                        new CommandField(() -> rename(), () -> {}).name("hub_name")
                            .size(268, 20)
                            .setMaxLength(24)
                            .autoUpdateOnChange(true)
                            .value(new StringValue.Dynamic(view::nameDraft, view::nameDraft))
                            .setEnabledIf(w -> live())
                            .addTooltipLine(IKey.lang("gtsr.hub_status.rename_hint")))
                    .child(action("gtsr.mui2.hub.rename", "hub_rename", 104, this::live, this::rename)));
            if (view.drilling) {
                main.child(
                    Flow.row()
                        .size(376, 18)
                        .childPadding(4)
                        .child(
                            action(
                                "gtsr.mui2.hub.toggle",
                                "hub_toggle",
                                91,
                                this::selected,
                                () -> send(1, !view.selectedRow().worker.allowed)).overlay(
                                    IKey.dynamicKey(
                                        () -> IKey.lang(
                                            selected() && view.selectedRow().worker.allowed ? "gtsr.hub_status.stop"
                                                : "gtsr.hub_status.start"))))
                        .child(
                            action(
                                "gtsr.mui2.hub.recycle",
                                "hub_recycle",
                                91,
                                () -> selected() && view.selectedRow().worker.recyclable,
                                () -> send(2, null)))
                        .child(action("gtsr.mui2.hub.upgrade", "hub_upgrade", 91, this::selected, () -> send(3, null)))
                        .child(
                            action("gtsr.mui2.hub.teleport", "hub_teleport", 91, this::selected, () -> send(5, null))));
                main.child(IKey.dynamicKey(() -> {
                    HubTerminalViewState.Row row = view.selectedRow();
                    return IKey.lang(
                        row == null || !row.worker.allowed ? "gtsr.hub_status.status.stopped"
                            : row.worker.working ? "gtsr.gui.status.running" : "gtsr.gui.status.idle");
                })
                    .asWidget()
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .name("hub_working")
                    .size(376, 18));
            } else {
                main.child(
                    Flow.row()
                        .size(376, 18)
                        .childPadding(4)
                        .child(action("gtsr.cache_hub_status.edit_rate", "hub_rate", 120, this::live, () -> edit(7)))
                        .child(
                            action(
                                "gtsr.cache_hub_status.edit_capacity",
                                "hub_capacity",
                                120,
                                () -> live() && view.selectedRow()
                                    .capacity(),
                                () -> edit(8)))
                        .child(
                            action(
                                "gtsr.mui2.hub.mode",
                                "hub_mode",
                                128,
                                () -> !view.nearby && live() && !view.selectedRow().cache.modeLocked,
                                () -> send(2, !view.selectedRow().cache.out))));
                main.child(
                    Flow.row()
                        .size(376, 18)
                        .childPadding(4)
                        .child(
                            action(
                                "gtsr.mui2.hub.auto",
                                "hub_auto",
                                120,
                                () -> !view.nearby && live(),
                                () -> send(4, !view.selectedRow().cache.auto)).overlay(
                                    IKey.dynamicKey(
                                        () -> IKey.lang(
                                            selected() && view.selectedRow().cache.auto
                                                ? "gtsr.cache_hub_status.auto_on"
                                                : "gtsr.cache_hub_status.auto_off"))))
                        .child(
                            action(
                                "gtsr.mui2.hub.teleport",
                                "hub_teleport",
                                120,
                                () -> !view.nearby && selected(),
                                () -> send(5, null)))
                        .child(IKey.dynamic(() -> {
                            HubTerminalViewState.Row row = view.selectedRow();
                            return row == null ? "—"
                                : IKey
                                    .lang(row.cache.out ? "gtsr.mui2.hub.direction_in" : "gtsr.mui2.hub.direction_out")
                                    .getFormatted();
                        })
                            .asWidget()
                            .color(GtsrGuiPalette.TEXT_BODY)
                            .name("hub_mode_state")
                            .size(128, 18)
                            .scale(0.75f)));
            }
            content.child(
                new PagedWidget<>().name("hub_pages")
                    .size(376, height - 64)
                    .controller(pages)
                    .addPage(main)
                    .addPage(editor(height - 64)));
            content.child(
                IKey.dynamicKey(() -> IKey.lang(session.receiptKey))
                    .asWidget()
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .name("hub_receipt")
                    .color(GtsrGuiPalette.TEXT_ACCENT)
                    .size(376, 16));
            return ModularPanel
                .defaultPanel(view.nearby ? "cache_node_editor" : HubTerminalFactory.PANEL_NAME, 400, height)
                .background(TerminalUiStyle.PANEL)
                .child(
                    TerminalViewport.wrap("hub_viewport", content.pos(0, 0), 376, height - 24)
                        .pos(12, 10)
                        .widthRelOffset(1f, -24)
                        .heightRelOffset(1f, -20))
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

        Flow header() {
            Flow row = Flow.row()
                .size(366, 14)
                .childPadding(2);
            row.child(
                IKey.lang("gtsr.mui2.hub.node")
                    .asWidget()
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .size(148, 14));
            if (view.drilling) {
                row.child(
                    IKey.lang("gtsr.mui2.hub.tier")
                        .asWidget()
                        .color(GtsrGuiPalette.TEXT_BODY)
                        .size(66, 14));
                row.child(
                    IKey.lang("gtsr.mui2.hub.state")
                        .asWidget()
                        .color(GtsrGuiPalette.TEXT_BODY)
                        .size(146, 14));
            } else {
                row.child(
                    IKey.lang("gtsr.cache_hub_status.col.stored")
                        .asWidget()
                        .color(GtsrGuiPalette.TEXT_BODY)
                        .size(68, 14));
                row.child(
                    IKey.lang("gtsr.cache_hub_status.col.cap")
                        .asWidget()
                        .color(GtsrGuiPalette.TEXT_BODY)
                        .size(68, 14));
                row.child(
                    IKey.lang("gtsr.cache_hub_status.col.rate")
                        .asWidget()
                        .color(GtsrGuiPalette.TEXT_BODY)
                        .size(72, 14));
            }
            return row;
        }

        ButtonWidget<?> row(int index) {
            Flow cells = Flow.row()
                .size(366, 22)
                .childPadding(2);
            cells.child(
                Flow.col()
                    .size(148, 22)
                    .child(
                        IKey.dynamic(() -> HubClientText.clip(HubClientText.name(view.row(index)), 142))
                            .asWidget()
                            .color(GtsrGuiPalette.TEXT_BODY)
                            .size(148, 11))
                    .child(IKey.dynamic(() -> {
                        HubTerminalViewState.Row row = view.row(index);
                        return row == null ? ""
                            : HubClientText.clip(
                                row.cache == null ? row.key.toString()
                                    : row.online() ? HubClientText.fluid(row.cache.fluid)
                                        : IKey.lang("gtsr.cache_hub_status.offline")
                                            .getFormatted(),
                                142);
                    })
                        .asWidget()
                        .color(GtsrGuiPalette.TEXT_BODY)
                        .size(148, 11)
                        .color(GtsrGuiPalette.TEXT_ACCENT)));
            if (view.drilling) {
                cells.child(
                    IKey.dynamic(() -> view.row(index) == null ? "" : "Mk" + (view.row(index).worker.tier + 1))
                        .asWidget()
                        .color(GtsrGuiPalette.TEXT_BODY)
                        .size(66, 22));
                cells.child(IKey.dynamicKey(() -> {
                    HubTerminalViewState.Row row = view.row(index);
                    return IKey.lang(
                        row == null || !row.worker.allowed ? "gtsr.hub_status.status.stopped"
                            : row.worker.working ? "gtsr.gui.status.running" : "gtsr.gui.status.idle");
                })
                    .asWidget()
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .size(146, 22));
            } else {
                for (int column = 0; column < 3; column++) {
                    final int c = column;
                    cells.child(
                        IKey.dynamic(
                            () -> view.row(index) == null ? ""
                                : HubClientText.amount(
                                    view.row(index)
                                        .number(c))
                                    + (c == 2 ? "/s" : ""))
                            .asWidget()
                            .color(GtsrGuiPalette.TEXT_BODY)
                            .size(c == 2 ? 72 : 68, 22)
                            .scale(0.85f));
                }
            }
            return new ButtonWidget<>().name("hub_row_" + index)
                .size(366, 22)
                .background(
                    new DynamicDrawable(
                        () -> view.row(index) != null && view.row(index).key.equals(view.selected)
                            ? TerminalUiStyle.HOVER
                            : TerminalUiStyle.CARD))
                .hoverBackground(TerminalUiStyle.HOVER)
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
                    } else {
                        t.addLine(
                            IKey.lang(
                                "gtsr.mui2.hub.worker_flags",
                                row.worker.allowed,
                                row.worker.working,
                                row.worker.retractable,
                                row.worker.recyclable));
                    }
                })
                .tooltipAutoUpdate(true);
        }

        Flow nodeCard() {
            return Flow.col()
                .name("cache_node_card")
                .size(376, 86)
                .childPadding(3)
                .child(
                    IKey.dynamic(() -> view.selectedRow() == null ? "—" : view.selectedRow().key.toString())
                        .asWidget()
                        .color(GtsrGuiPalette.TEXT_BODY)
                        .size(376, 16))
                .child(IKey.dynamic(() -> {
                    HubTerminalViewState.Row row = view.selectedRow();
                    return row == null ? "—" : row.cache.stored + " / " + row.cache.cap + " L";
                })
                    .asWidget()
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .name("cache_node_storage")
                    .size(376, 16))
                .child(IKey.dynamic(() -> {
                    HubTerminalViewState.Row row = view.selectedRow();
                    return row == null ? "—" : row.cache.actualRate + " / " + row.cache.maxRate + " L/s";
                })
                    .asWidget()
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .name("cache_node_rate")
                    .size(376, 16))
                .child(IKey.dynamic(() -> {
                    HubTerminalViewState.Row row = view.selectedRow();
                    return row == null ? "—" : HubClientText.fluid(row.cache.fluid);
                })
                    .asWidget()
                    .color(GtsrGuiPalette.TEXT_BODY)
                    .size(376, 16));
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
                .setEnabledIf(w -> !session.awaiting && enabled.getAsBoolean())
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
