package com.miaokatze.gtsr.common.gui.mui2.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;
import com.miaokatze.gtsr.common.gui.mui2.TerminalViewport;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;
import com.miaokatze.gtsr.common.terminal.AggregatorTerminalData;
import com.miaokatze.gtsr.common.terminal.AggregatorTerminalData.OreEntry;

/** Fixed inventory topology plus seven reusable ore rows; sorting never rebuilds a real slot. */
public final class AggregatorConfigPanel {

    private static final String PREFIX = "gtsr.aggregator_config.";
    private static final int VISIBLE_ROWS = 7;
    private static final String[] MODES = { "raw", "crushed", "purified" };
    private static final String[] CATEGORIES = { "all", "unfiltered", "filtered" };
    private static final String[] ROMAN = { "III", "V", "VII", "IX", "XI", "XIII", "XV" };

    private AggregatorConfigPanel() {}

    /** Production builder with the real machine handlers; factory owns the permission predicate. */
    public static ModularPanel build(PosGuiData data, MTECrustMatterAggregator machine, PanelSyncManager manager,
        UISettings settings) {
        settings.customContainer(AggregatorConfigModularContainer::new);
        AggregatorConfigSession session = new AggregatorConfigSession(data, machine);
        manager.syncValue(AggregatorConfigSession.KEY, session);
        manager.registerSlotGroup(new SlotGroup("aggregator_dimensions", 5).setAllowSorting(false));
        View view = new View(session);
        ModularPanel window = ModularPanel.defaultPanel(AggregatorConfigFactory.PANEL_NAME, 475, 350)
            .background(IDrawable.EMPTY);
        ParentWidget<?> panel = new ParentWidget<>().name("aggregator_canvas")
            .pos(0, 0)
            .size(475, 350)
            .background(TerminalUiStyle.AGGREGATOR_PANEL);
        panel.child(
            IKey.lang(PREFIX + "title")
                .asWidget()
                .name("aggregator_title")
                .pos(8, 6)
                .size(450, 14)
                .color(GtsrGuiPalette.TEXT_TITLE));
        panel.child(
            action("aggregator_ore_mode", view::modeLabel, session, 1).pos(8, 22)
                .size(104, 18)
                .addTooltipLine(IKey.lang(PREFIX + "ore_mode.tip")));
        panel.child(
            action("aggregator_fortune", view::fortuneLabel, session, 2).pos(116, 22)
                .size(104, 18)
                .addTooltipLine(IKey.lang(PREFIX + "fortune.tip"))
                .addTooltipLine(IKey.lang(PREFIX + "fortune.gate")));
        panel.child(
            text("aggregator_steam", view::steamLabel).pos(224, 22)
                .size(246, 10)
                .color(GtsrGuiPalette.TEXT_WHITE)
                .addTooltipLine(IKey.lang(PREFIX + "steam_formula"))
                .addTooltipLine(IKey.dynamic(view::steamDetail)));
        panel.child(
            text("aggregator_steam_formula", view::steamFormula).pos(224, 32)
                .size(246, 10)
                .scale(.8f)
                .addTooltipLine(IKey.dynamic(view::steamDetail)));
        panel.child(
            IKey.lang(PREFIX + "slots_title")
                .asWidget()
                .pos(18, 44)
                .size(92, 14)
                .color(GtsrGuiPalette.TEXT_BODY));
        panel.child(
            action("aggregator_refresh", () -> tr("refresh"), session, 4).pos(50, 44)
                .size(26, 16)
                .addTooltipLine(IKey.lang(PREFIX + "refresh.tip")));
        panel.child(
            text("aggregator_dimension_increase", () -> "\u00a7l" + view.increase(false)).pos(80, 45)
                .size(96, 12)
                .color(GtsrGuiPalette.TEXT_WHITE));
        panel.child(
            Flow.row()
                .pos(18, 58)
                .size(158, 110)
                .background(TerminalUiStyle.CARD));
        InvWrapper plugins = new InvWrapper(machine.getPluginSlotInventory());
        for (int n = 0; n < 25; n++) {
            ModularSlot slot = n
                == 0 ? new PureInventorySlot(machine.inventoryHandler, machine.getControllerSlotIndex()).limit(1)
                    .filter(MTECrustMatterAggregator::isDimensionDisplayItem)
                    : new PureInventorySlot(plugins, n - 1).limit(1)
                        .filter(MTECrustMatterAggregator::isDimensionDisplayItem);
            slot.slotGroup("aggregator_dimensions");
            if (n > 0) slot.changeListener(
                (stack, amount, client, init) -> {
                    if (!client && !init) machine.getPluginSlotInventory()
                        .markDirty();
                });
            manager.itemSlot("aggregator_dimension", n, slot);
            ItemSlot widget = new ItemSlot().syncHandler("aggregator_dimension", n)
                .name("aggregator_slot_" + n)
                .background(TerminalUiStyle.SLOT_FRAME)
                .pos(24 + n % 5 * 32, 63 + n / 5 * 20);
            if (n == 0) widget.addTooltipLine(IKey.lang(PREFIX + "slot1_hint"));
            panel.child(widget);
        }
        InventorySlots.registerPlayer(manager, data.getPlayer());
        panel.child(
            SlotGroupWidget.playerInventory((index, slot) -> slot.background(TerminalUiStyle.SLOT_FRAME))
                .name("aggregator_player_inventory")
                .pos(303, 264));
        panel.child(
            text("aggregator_dimension_cost", () -> tr("dim_text." + (view.directional() ? "directional" : "filtered")))
                .pos(18, 172)
                .size(158, 10)
                .scale(.85f)
                .addTooltipLine(
                    IKey.dynamic(() -> tr("dim_text." + (view.directional() ? "directional" : "filtered")))));
        panel.child(
            action("aggregator_directional", () -> tr("directional." + (view.directional() ? "on" : "off")), session, 5)
                .pos(18, 184)
                .size(90, 18)
                .addTooltipLine(IKey.lang(PREFIX + "directional.tip")));
        panel.child(
            new ListWidget<>().name("aggregator_mode_hint")
                .pos(18, 204)
                .size(158, 60)
                .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
                .child(
                    text(
                        "aggregator_mode_hint_title",
                        () -> tr("mode_hint." + (view.directional() ? "directional" : "filtered") + ".title"))
                            .width(148)
                            .height(12)
                            .color(GtsrGuiPalette.TEXT_ACCENT))
                .child(
                    // These legacy literals contain ordinary percent signs, not formatter arguments.
                    IKey.dynamic(() -> tr("mode_hint." + (view.directional() ? "directional" : "filtered")))
                        .asWidget()
                        .width(148)
                        .textAlign(Alignment.TopLeft)
                        .color(GtsrGuiPalette.TEXT_BODY)));
        panel.child(
            text("aggregator_uu_cost", view::uuLabel).pos(18, 266)
                .size(270, 12)
                .scale(.85f)
                .color(0xffe080e8)
                .addTooltipLine(IKey.lang(PREFIX + "uu_formula")));
        panel.child(
            new ListWidget<>().name("aggregator_help")
                .pos(18, 280)
                .size(280, 44)
                .scrollDirection(new VerticalScrollData().cancelScrollEdge(false))
                .child(
                    IKey.lang(PREFIX + "help.slots")
                        .asWidget()
                        .width(266)
                        .scale(.9f)
                        .color(GtsrGuiPalette.TEXT_LABEL))
                .child(
                    IKey.lang(() -> PREFIX + "help." + (view.directional() ? "directional" : "filter"))
                        .asWidget()
                        .width(266)
                        .scale(.9f)
                        .color(GtsrGuiPalette.TEXT_LABEL)));
        panel.child(
            IKey.lang(session::receiptKey)
                .asWidget()
                .name("aggregator_receipt")
                .pos(18, 332)
                .size(440, 12)
                .color(GtsrGuiPalette.TEXT_ACCENT));
        addBrowser(panel, view, session);
        return window.child(
            TerminalViewport.wrapExact("aggregator_viewport", panel, 475, 350)
                .pos(0, 0)
                .sizeRel(1f));
    }

    private static void addBrowser(ParentWidget<?> panel, View view, AggregatorConfigSession session) {
        panel.child(
            text("aggregator_browser_title", () -> tr("browser_title")).pos(180, 45)
                .size(54, 12));
        panel.child(
            text("aggregator_weight_increase", () -> "\u00a7l" + view.increase(true)).pos(234, 45)
                .size(236, 12)
                .color(GtsrGuiPalette.TEXT_WHITE));
        panel.child(
            new ParentWidget<>().name("aggregator_browser_background")
                .pos(180, 58)
                .size(290, 208)
                .background(TerminalUiStyle.CARD));
        TextFieldWidget search = new TextFieldWidget().name("aggregator_search_input")
            .pos(184, 62)
            .size(138, 16)
            .setMaxLength(128)
            .autoUpdateOnChange(true)
            .value(new StringValue.Dynamic(() -> view.draft, s -> view.draft = s))
            .addTooltipLine(IKey.lang(PREFIX + "search_hint"));
        panel.child(search);
        panel.child(localButton("aggregator_search", () -> tr("search"), () -> {
            view.search = search.getText();
            view.offset = 0;
        }).pos(326, 62)
            .size(44, 16));
        panel.child(localButton("aggregator_category", () -> tr("category." + CATEGORIES[view.category]), () -> {
            view.category = (view.category + 1) % 3;
            view.offset = 0;
        }).pos(374, 62)
            .size(44, 16)
            .addTooltipLine(IKey.lang(PREFIX + "category.tip")));
        panel.child(
            action("aggregator_clear", () -> tr("clear_config"), session, 7).pos(422, 62)
                .size(44, 16)
                .addTooltipLine(IKey.lang(PREFIX + "clear_config.tip")));
        for (int n = 0; n < 3; n++) {
            final int key = n;
            String label = new String[] { "name", "weight", "dim" }[n];
            panel.child(
                localButton(
                    "aggregator_sort_" + label,
                    () -> "\u00a76\u00a7l" + tr("col." + label)
                        + (view.sort == key ? view.ascending ? " ▲" : " ▼" : ""),
                    () -> {
                        if (view.sort == key) view.ascending = !view.ascending;
                        else {
                            view.sort = key;
                            view.ascending = true;
                        }
                        view.offset = 0;
                    }).pos(184 + new int[] { 20, 124, 172 }[n], 80)
                        .size(n == 0 ? 100 : n == 1 ? 44 : 56, 14)
                        .background(IDrawable.EMPTY)
                        .hoverBackground(TerminalUiStyle.ROW_HOVER));
        }
        panel.child(
            IKey.lang(PREFIX + "col.action")
                .asWidget()
                .name("aggregator_action_header")
                .pos(416, 82)
                .size(44, 12)
                .color(GtsrGuiPalette.TEXT_ACCENT));
        Flow rows = Flow.column()
            .size(274, 154);
        for (int n = 0; n < VISIBLE_ROWS; n++) {
            final int row = n;
            ParentWidget<?> line = new ParentWidget<>().name("aggregator_ore_row_" + n)
                .size(274, 22)
                .hoverBackground(TerminalUiStyle.ROW_HOVER)
                .setEnabledIf(widget -> view.entry(row) != null);
            line.child(
                new ItemDisplayWidget()
                    .item(
                        new ObjectValue.Dynamic<>(
                            ItemStack.class,
                            () -> view.entry(row) == null ? null : view.entry(row).displayStack,
                            null))
                    .pos(0, 3)
                    .size(16));
            line.child(
                text("aggregator_ore_name_" + n, () -> shorten(view.name(view.entry(row)), 15)).pos(20, 6)
                    .size(100, 12)
                    .color(GtsrGuiPalette.TEXT_WHITE)
                    .addTooltipLine(IKey.dynamic(() -> view.name(view.entry(row)))));
            line.child(
                text(
                    "aggregator_ore_weight_" + n,
                    () -> view.entry(row) == null ? "" : formatWeight(view.entry(row).weight)).pos(124, 6)
                        .size(44, 12)
                        .color(GtsrGuiPalette.TEXT_WHITE)
                        .addTooltipLine(
                            IKey.dynamic(() -> view.entry(row) == null ? "" : Float.toString(view.entry(row).weight))));
            line.child(
                text(
                    "aggregator_ore_dims_" + n,
                    () -> view.entry(row) == null ? "" : shorten(String.join("+", view.entry(row).dimAbbrs), 9))
                        .pos(172, 6)
                        .size(56, 12)
                        .color(GtsrGuiPalette.TEXT_WHITE)
                        .addTooltipLine(
                            IKey.dynamic(
                                () -> view.entry(row) == null ? "" : String.join("+", view.entry(row).dimAbbrs))));
            line.child(localButton("aggregator_ore_action_" + n, () -> view.rowAction(row), () -> {
                OreEntry entry = view.entry(row);
                if (entry != null) session.submit(view.directional() ? 6 : 3, entry);
            }).pos(232, 4)
                .size(44, 14)
                .background(
                    new DynamicDrawable(
                        () -> view.entry(row) != null
                            && (view.directional() ? view.entry(row).aimed : view.entry(row).filtered)
                                ? TerminalUiStyle.CHIP_ACTIVE
                                : TerminalUiStyle.CHIP_NORMAL))
                .addTooltipLine(
                    IKey.lang(() -> PREFIX + (view.directional() ? "directional_ore_hint" : "filtered_hint"))));
            rows.child(line);
        }
        panel.child(
            new ButtonWidget<>().name("aggregator_ore_list")
                .pos(180, 100)
                .size(290, 156)
                .background(IDrawable.EMPTY)
                .child(rows.pos(4, 1))
                .onMousePressed(button -> true)
                .onMouseScrolled((direction, amount) -> {
                    int previous = view.offset;
                    view.scroll((int) -Math.copySign(Math.max(1, amount), direction.modifier));
                    return view.offset != previous;
                }));
        panel.child(
            new SliderWidget().name("aggregator_ore_scrollbar")
                .pos(459, 101)
                .size(9, 153)
                .background(TerminalUiStyle.SCROLLBAR_TRACK)
                .sliderTexture(TerminalUiStyle.SCROLLBAR_THUMB)
                .setAxis(GuiAxis.Y)
                .bounds(0, 1)
                .sliderWidth(8)
                .sliderHeight(18)
                .value(
                    new DoubleValue.Dynamic(
                        () -> view.maxOffset() == 0 ? 0 : (double) view.offset / view.maxOffset(),
                        v -> view.offset = (int) Math.round(v * view.maxOffset()))));
        panel.child(
            text(
                "aggregator_ore_count",
                () -> view.entries()
                    .isEmpty()
                        ? tr("empty")
                        : (view.offset + 1) + "–"
                            + Math.min(
                                view.offset + VISIBLE_ROWS,
                                view.entries()
                                    .size())
                            + " / "
                            + view.entries()
                                .size()).pos(180, 256)
                                    .size(290, 10)
                                    .scale(.8f));
    }

    private static TextWidget<?> text(String name, Supplier<String> value) {
        return IKey.dynamic(value)
            .asWidget()
            .name(name)
            .color(GtsrGuiPalette.TEXT_BODY);
    }

    private static ButtonWidget<?> action(String name, Supplier<String> label, AggregatorConfigSession session,
        int code) {
        return localButton(name, label, () -> {
            // Legacy raw mode keeps Fortune visible, with no actionable hover or submission.
            if (code != 2 || fortuneAvailable(session)) session.submit(code, null);
        }).background(
            new DynamicDrawable(
                () -> code == 2 && !fortuneAvailable(session) ? TerminalUiStyle.DISABLED : TerminalUiStyle.BUTTON))
            .hoverBackground(
                new DynamicDrawable(
                    () -> code == 2 && !fortuneAvailable(session) ? TerminalUiStyle.DISABLED : TerminalUiStyle.HOVER));
    }

    private static boolean fortuneAvailable(AggregatorConfigSession session) {
        return session.snapshot() != null && session.snapshot().oreMode != 0;
    }

    private static ButtonWidget<?> localButton(String name, Supplier<String> label, Runnable action) {
        return new ButtonWidget<>().name(name)
            .background(TerminalUiStyle.BUTTON)
            .hoverBackground(TerminalUiStyle.HOVER)
            .child(
                text(name + "_label", label).sizeRel(1f)
                    .scale(1f)
                    .textAlign(Alignment.Center))
            .onMousePressed(button -> {
                if (button == 0) action.run();
                return true;
            });
    }

    private static String tr(String suffix) {
        return StatCollector.translateToLocal(PREFIX + suffix);
    }

    private static String formatWeight(float weight) {
        return weight == Math.floor(weight) ? String.format(Locale.ROOT, "%.0f", weight)
            : String.format(Locale.ROOT, "%.1f", weight);
    }

    private static String shorten(String value, int length) {
        return value.length() > length ? value.substring(0, length - 1) + "…" : value;
    }

    private static final class View {

        private final AggregatorConfigSession session;
        private String draft = "", search = "", cachedSearch;
        private int category, sort, offset, cachedCategory = -1, cachedSort = -1;
        private boolean ascending = true, cachedAscending;
        private AggregatorTerminalData.Snapshot cachedSnapshot;
        private List<OreEntry> visible = Collections.emptyList();

        private View(AggregatorConfigSession session) {
            this.session = session;
        }

        private boolean directional() {
            return session.snapshot() != null && session.snapshot().directionalMode;
        }

        private List<OreEntry> entries() {
            AggregatorTerminalData.Snapshot snapshot = session.snapshot();
            if (cachedSnapshot != snapshot || !search.equals(cachedSearch)
                || category != cachedCategory
                || sort != cachedSort
                || ascending != cachedAscending) {
                List<OreEntry> result = new ArrayList<>();
                if (snapshot != null) for (OreEntry ore : snapshot.ores) {
                    if (category == 1 && ore.filtered || category == 2 && !ore.filtered) continue;
                    if (!name(ore).toLowerCase(Locale.ROOT)
                        .contains(search.toLowerCase(Locale.ROOT))) continue;
                    result.add(ore);
                }
                result.sort((a, b) -> {
                    int compared = sort == 1 ? Float.compare(a.weight, b.weight)
                        : sort == 2 ? String.join("+", a.dimAbbrs)
                            .compareToIgnoreCase(String.join("+", b.dimAbbrs)) : name(a).compareToIgnoreCase(name(b));
                    return ascending ? compared : -compared;
                });
                visible = result;
                cachedSnapshot = snapshot;
                cachedSearch = search;
                cachedCategory = category;
                cachedSort = sort;
                cachedAscending = ascending;
            }
            offset = Math.max(0, Math.min(offset, Math.max(0, visible.size() - VISIBLE_ROWS)));
            return visible;
        }

        private int maxOffset() {
            return Math.max(0, entries().size() - VISIBLE_ROWS);
        }

        private void scroll(int delta) {
            offset = Math.max(0, Math.min(maxOffset(), offset + delta));
        }

        private OreEntry entry(int row) {
            List<OreEntry> entries = entries();
            return offset + row < entries.size() ? entries.get(offset + row) : null;
        }

        private String name(OreEntry ore) {
            return ore == null ? "" : ore.displayStack == null ? ore.uniqueId : ore.displayStack.getDisplayName();
        }

        private String rowAction(int row) {
            OreEntry ore = entry(row);
            return ore == null ? ""
                : tr(
                    directional() ? ore.aimed ? "directional_ore.off" : "directional_ore"
                        : ore.filtered ? "unfilter" : "filter");
        }

        private String modeLabel() {
            int mode = session.snapshot() == null ? 0 : Math.max(0, Math.min(2, session.snapshot().oreMode));
            return tr("ore_mode." + MODES[mode]) + " +"
                + Math.round(MTECrustMatterAggregator.ORE_MODE_STEAM_BONUS[mode] * 100)
                + "%";
        }

        private String fortuneLabel() {
            int fortune = session.snapshot() == null ? 3 : session.snapshot().fortune;
            int index = Math.max(0, Math.min(6, (fortune - 3) / 2));
            return (fortuneAvailable(session) ? "" : "§7") + String.format(
                tr("fortune_level"),
                ROMAN[index],
                Math.round(MTECrustMatterAggregator.FORTUNE_STEAM_BONUS[index] * 100));
        }

        private String increase(boolean weight) {
            if (session.snapshot() == null) return "—";
            return "+" + String.format(
                Locale.ROOT,
                "%.0f",
                weight ? session.snapshot().weightIncrease : session.snapshot().dimIncrease) + "%";
        }

        private String steamLabel() {
            AggregatorTerminalData.Snapshot s = session.snapshot();
            return s == null ? "—"
                : "§l" + tr("steam_cost")
                    + String.format(
                        Locale.ROOT,
                        "%,.0f L/s §6§l×%.2f",
                        (s.denseState ? MTECrustMatterAggregator.DENSE_STEAM_PER_SECOND
                            : MTECrustMatterAggregator.NORMAL_STEAM_PER_SECOND) * s.steamMult,
                        s.steamMult);
        }

        private String steamFormula() {
            AggregatorTerminalData.Snapshot s = session.snapshot();
            if (s == null) return "—";
            long base = s.denseState ? MTECrustMatterAggregator.DENSE_STEAM_PER_SECOND
                : MTECrustMatterAggregator.NORMAL_STEAM_PER_SECOND;
            return base + " × "
                + String.format(Locale.ROOT, "%.2f", s.steamMult)
                + " = "
                + String.format(Locale.ROOT, "%,.0f L/s", base * s.steamMult);
        }

        private String steamDetail() {
            AggregatorTerminalData.Snapshot s = session.snapshot();
            if (s == null) return "—";
            double mode = 1 + MTECrustMatterAggregator.ORE_MODE_STEAM_BONUS[Math.max(0, Math.min(2, s.oreMode))]
                + MTECrustMatterAggregator.FORTUNE_STEAM_BONUS[Math.max(0, Math.min(6, (s.fortune - 3) / 2))];
            return s.directionalMode
                ? tr("steam_cost.detail_dir")
                    + String.format(Locale.ROOT, "%.2f × %.2f × %.2f", mode, 1 + s.dimIncrease / 100, s.uuMult / mode)
                : tr("steam_cost.detail") + String.format(Locale.ROOT, "%.2f × %.2f", mode, s.steamMult / mode);
        }

        private String uuLabel() {
            return tr("uu_cost") + (!directional() ? tr("uu_cost.off")
                : String
                    .format(Locale.ROOT, "%,.0f L/s (×%.2f)", session.snapshot().uuMult, session.snapshot().uuMult));
        }
    }
}
