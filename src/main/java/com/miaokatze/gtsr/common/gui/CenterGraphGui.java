// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.gui;

import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Supplier;
import java.util.function.IntSupplier;
import java.util.function.IntConsumer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.Stencil;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtsr.client.gui.CenterTerminalClient;
import com.miaokatze.gtsr.common.gui.mui2.TerminalViewport;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph.Node;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph.OutletFilter;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraphPreview;
import com.miaokatze.gtsr.common.machine.orecenter.CenterProcess;
import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;

import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.miaokatze.gtsr.common.machine.orecenter.CenterPresets;
import com.miaokatze.gtsr.common.machine.orecenter.CenterPresets.Preset;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTUtility;

public final class CenterGraphGui {

    private static final String PANEL = "gtsr_ore_center_graph";
    private static final String PREFIX = "gtsr.ore_center.graph.";
    private static final int TAB_WIDTH = 64, TAB_HEIGHT = 15;
    private static final int UNRESTRICTED = 0, ALLOW = 1, DENY = 2;
    private static final int TEXT_LIMIT = 8192;
    private static final CenterProcess[] PROCESSES = {
        CenterProcess.CRUSHER, CenterProcess.WASHER, CenterProcess.SIFTER, CenterProcess.CENTRIFUGE,
        CenterProcess.ELECTROLYZER, CenterProcess.DRYER, CenterProcess.FURNACE, CenterProcess.REACTOR,
        CenterProcess.SEPARATOR, CenterProcess.EXTRACTOR, CenterProcess.THERMAL_CENTRIFUGE, CenterProcess.SMELTING,
        CenterProcess.SEPARATION, CenterProcess.HAMMER, CenterProcess.SIMPLE_WASHER, CenterProcess.CHEMICAL_BATH,
        CenterProcess.FLUID_EXTRACTOR, CenterProcess.RECYCLER
    };
    private static final java.util.regex.Pattern MINERAL = java.util.regex.Pattern.compile(
        "(?:ore|variantOre|rawOre|crushedPurified|crushed|dustImpure|dustPure)([A-Z][A-Za-z0-9_]*)");

    private final MTEMineralIntegrationCenter machine;

    public CenterGraphGui(MTEMineralIntegrationCenter machine) {
        this.machine = machine;
    }

    public ModularPanel build(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        ModularPanel panel = new ModularPanel(PANEL).sizeRel(1f)
            .background(TerminalUiStyle.CLUSTER_PANEL);
        CenterGraphSync sync = new CenterGraphSync(machine, manager, data.getPlayer());
        manager.syncValue("ore_center.graph", sync.allowC2S());
        Editor editor = new Editor(sync);
        var presets = manager.syncedPanel("ore_center.generator", true,
            (child, handler) -> editor.presetPanel(handler::closePanel));
        editor.openPresets = presets::openPanel;
        CenterTerminalPages pages = new CenterTerminalPages(sync,
            id -> editor.graphId == id ? editor.canvas.sample : null);
        int[] selectedPage = { 0 };
        ParentWidget<?> header = new ParentWidget<>().name("center_terminal_header")
            .background(TerminalUiStyle.CHIP_NORMAL)
            .left(4).right(4).top(3).height(20);
        header.child(new BrandMark().pos(3, 3).size(11, 14));
        header.child(new ParentWidget<>().background(compact(() -> "GTSR", () -> 0xFFE3BA74, Alignment.CenterLeft))
            .pos(14, 0).size(70, 8));
        header.child(label(() -> tr("terminal.brand"), 14, 7, 73, 13));
        String[] ids = { "assembly", "routes", "stats", "calculation" };
        for (int i = 0; i < ids.length; i++) {
            int index = i;
            header.child(button("page_" + ids[i], () -> tr("page." + ids[index]), () -> {
                selectedPage[0] = index;
                pages.disarm();
                panel.scheduleResize();
            }).pos(89 + i * 48, 0).size(47, 20)
                .background(new DynamicDrawable(
                    () -> index == selectedPage[0] ? TerminalUiStyle.TAB_ACTIVE : TerminalUiStyle.TAB_INACTIVE))
                .overlay(CriticalGuiStyle.fitted(() -> tr("page." + ids[index]),
                    () -> index == selectedPage[0] ? 0xFFFFD197 : 0xFFC0C9C8, Alignment.CENTER)));
        }
        header.child(button("header_presets", () -> tr("preset.open"), () -> {
            selectedPage[0] = 1;
            pages.disarm();
            panel.scheduleResize();
            editor.openPresets.run();
        }).right(29).top(3).size(66, 13));
        for (int i = 0; i < 4; i++) {
            int tier = i;
            header.child(new ParentWidget<>().name("tier_" + i).right(98 + (3 - i) * 26).top(3).size(24, 13)
                .background(new DynamicDrawable(() -> sync.terminal().hasKey("Tier")
                    && sync.terminal().getInteger("Tier") == tier ? TerminalUiStyle.CHIP_ACTIVE : TerminalUiStyle.CHIP_NORMAL))
                .child(CriticalGuiStyle.fitted(() -> tr("terminal.tier." + tier),
                    () -> 0xFFC0C9C8, Alignment.CENTER).asWidget().sizeRel(1f)));
        }
        header.child(button("exit", () -> tr("exit"), () -> panel.getScreen().close()).right(1).top(3).size(26, 13));
        panel.child(header);
        List<ParentWidget<?>> bodies = Arrays.asList(pages.assembly(), editor.content(() -> {
            selectedPage[0] = 3;
            panel.scheduleResize();
        }), pages.statistics(), pages.calculation());
        for (int i = 0; i < bodies.size(); i++) {
            int index = i;
            panel.child(bodies.get(i).left(4).right(4).top(24).bottom(15).setEnabledIf(w -> selectedPage[0] == index));
        }
        ParentWidget<?> footer = CenterTerminalPages.card("center_terminal_receipt")
            .left(4).right(4).bottom(5).height(10);
        footer.child(CriticalGuiStyle.label(() -> tr("terminal.receipt"), () -> 0xFFCFAA75, Alignment.CenterLeft)
            .pos(2, 0).size(36, 10));
        footer.child(CenterTerminalPages.label(() -> tr(sync.status())).left(39).right(3).top(0).height(10));
        panel.child(footer);
        return panel;
    }

    private static final class BrandMark extends Widget<BrandMark> {

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            for (int row = 0; row < 11; row++) {
                int x = (10 - row) / 3;
                for (int stroke = 0; stroke < 3; stroke++)
                    GuiDraw.drawRect(x + stroke * 3, row, 2, 1, row % 2 == 0 ? 0xFFEBC17D : 0xFFC89958);
            }
        }
    }

    private static final class Editor {

        private final CenterGraphSync sync;
        private CenterGraphSync.Snapshot snapshot = CenterGraphSync.Snapshot.EMPTY;
        private long graphId = -1;
        private int nodeIndex, port, filterMode, target;
        private NBTTagCompound moduleSnapshot;
        private final int[] unlocked = new int[CenterModule.values().length];
        private CenterGraph graph;
        private final TextFieldWidget name = field("name", 32);
        private final TextFieldWidget priority = field("priority", 11);
        private final TextFieldWidget rules = field("rules", TEXT_LIMIT);
        private final TextFieldWidget filter = field("filter", TEXT_LIMIT);
        private final TextFieldWidget circuit = field("circuit", 2);
        private final TextFieldWidget sourceSearch = field("source_find", 64);
        private final TextFieldWidget processSearch = field("find_process", 32);
        private final SourceItems sourceItems = new SourceItems(this);
        private ItemStack carried;
        private boolean showSources, chooseOreTerm, showRules, checking, confirmDelete, showShortcuts;
        private String inspectedRule;
        private Runnable openPresets;
        private Preset preset = CenterPresets.all().get(0);
        private final Tabs tabs = new Tabs(this);
        private final Canvas canvas = new Canvas(this);

        private Editor(CenterGraphSync sync) {
            this.sync = sync;
        }

        private ParentWidget<?> content(Runnable capacity) {
            ParentWidget<?> contents = new ParentWidget<>().name("center_routes");
            ParentWidget<?> sourceBar = CenterTerminalPages.card("center_graph_toolbar_source")
                .left(0).right(0).top(0).height(13);
            sourceBar.child(button("source", () -> tr(showSources ? "source.return" : "source"), () -> {
                if (showSources) {
                    showSources = false;
                    carried = null;
                } else openSources(chooseOreTerm);
            }).right(2).top(0).size(68, 13));
            contents.child(sourceBar);
            ParentWidget<?> documents = CenterTerminalPages.card("center_graph_documents")
                .left(0).right(0).top(14).height(17);
            documents.child(tabs.name("center_graph_tabs").left(2).right(41).top(1).height(TAB_HEIGHT)
                .tooltipBuilder(t -> t.add(IKey.lang(PREFIX + "tabs.help"))));
            documents.child(CenterTerminalPages.label(() -> tr("terminal.quota", snapshot.graphs().size(), snapshot.capacity()))
                .right(1).top(1).size(37, 14));
            contents.child(documents);

            ParentWidget<?> toolbar = CenterTerminalPages.card("center_graph_actions").left(0).right(0).top(32).height(16);
            toolbar.child(button("enabled", this::enabledText, () -> edit(CenterGraphSync.Action.TOGGLE, 0, 0, ""))
                .pos(2, 1).size(47, 14));
            toolbar.child(label(() -> tr("priority"), 51, 1, 30, 14));
            toolbar.child(priority.pos(82, 2).size(37, 12)
                .tooltipBuilder(t -> t.add(tr("priority.help"))));
            toolbar.child(button("save_priority", () -> tr("priority.apply"), this::savePriority).pos(121, 1).size(25, 14));
            toolbar.child(button("add", () -> "+ " + tr("graph"), () -> edit(CenterGraphSync.Action.ADD, 0, 0, ""))
                .pos(148, 1).size(42, 14));
            toolbar.child(button("generator_presets", () -> tr("preset.open"), () -> openPresets.run())
                .pos(192, 1).size(52, 14));
            toolbar.child(name.pos(246, 2).size(72, 12));
            toolbar.child(button("save_name", () -> tr("rename"),
                () -> edit(CenterGraphSync.Action.RENAME, 0, 0, name.getText().trim()))
                .pos(320, 1).size(27, 14));
            List<Widget<?>> docks = new java.util.ArrayList<>();
            ParentWidget<?> more = new GraphActions(toolbar, top -> docks.forEach(dock -> dock.top(top)))
                .pos(0, 16).size(318, 16);
            more.child(button("add_mineral", () -> tr("rule.item"), () -> openSources(false))
                .pos(2, 1).size(44, 14));
            more.child(button("add_ore_term", () -> tr("rule.add"), () -> {
                openSources(true);
                inspectedRule = null;
                showRules = true;
                showSources = false;
            }).pos(48, 1).size(44, 14));
            more.child(button("validate", () -> tr("validation"), this::toggleValidation)
                .pos(94, 1).size(47, 14)
                .background(new DynamicDrawable(() -> checking ? TerminalUiStyle.CHIP_ACTIVE : TerminalUiStyle.BUTTON))
                .tooltipBuilder(t -> t.add(tr("validation.help"))));
            more.child(button("rules_toggle", () -> tr("terminal.entry", graph == null ? 0 : graph.rules().size()), () -> {
                boolean opening = !showRules || inspectedRule != null;
                inspectedRule = null;
                showRules = opening;
                showSources = false;
            }).pos(143, 1).size(54, 14));
            more.child(button("capacity", () -> tr("terminal.capacity"), capacity).pos(199, 1).size(32, 14));
            more.child(button("delete", () -> tr(confirmDelete ? "delete.confirm" : "delete"), () -> {
                if (graph == null) return;
                if (confirmDelete) edit(CenterGraphSync.Action.DELETE, 0, 0, "");
                confirmDelete = !confirmDelete;
            }).pos(233, 1).size(52, 14));
            more.child(button("cancel_delete", () -> tr("cancel"), () -> confirmDelete = false)
                .pos(287, 1).size(30, 14).setEnabledIf(w -> confirmDelete));
            toolbar.child(more);
            contents.child(toolbar);

            contents.child(canvas.name("center_graph_canvas").left(93).right(146).top(65).bottom(18));
            docks.add(canvas);
            ParentWidget<?> tools = CenterTerminalPages.card("center_process_toolbox").left(0).top(65).bottom(18).width(91);
            tools.child(label(() -> tr("terminal.processes"), 3, 1, 28, 13));
            tools.child(label(() -> tr("terminal.process_count", processCount(), PROCESSES.length), 36, 1, 52, 13));
            tools.child(processSearch.pos(4, 16).size(83, 13)
                .tooltipBuilder(t -> t.add(tr("terminal.find_process"))));
            tools.child(new ProcessList(this).left(3).right(3).top(32).bottom(2));
            contents.child(tools);
            docks.add(tools);

            ParentWidget<?> inspector = CenterTerminalPages.card("center_graph_properties").right(0).top(65)
                .bottom(18).width(144).setEnabledIf(w -> !showSources && !showRules);
            inspector.child(label(() -> tr("terminal.properties"), 3, 3, 128, 15)
                .setEnabledIf(w -> node() == null));
            inspector.child(label(() -> tr("terminal.pick_process"), 3, 21, 129, 15)
                .setEnabledIf(w -> node() == null));
            ParentWidget<?> nodeDetails = new ParentWidget<>().sizeRel(1f).setEnabledIf(w -> node() != null);
            nodeDetails.child(label(this::nodeText, 2, 3, 104, 15));
            nodeDetails.child(button("focus_node", () -> tr("terminal.locate"), () -> canvas.focus(nodeIndex))
                .right(3).top(3).size(29, 13));
            nodeDetails.child(button("copy_node", () -> tr("terminal.copy"),
                () -> edit(CenterGraphSync.Action.DUPLICATE, nodeIndex, 0, ""))
                .pos(4, 19).size(42, 14));
            nodeDetails.child(button("remove_process", () -> tr("remove_process"),
                () -> edit(CenterGraphSync.Action.REMOVE_NODE, nodeIndex, 0, ""))
                .right(3).top(19).size(56, 14));
            nodeDetails.child(new OutletList(this).left(4).right(4).top(39).height(126));

            nodeDetails.child(button("filter_mode", this::modeText, () -> filterMode = (filterMode + 1) % 3)
                .left(4).top(167).size(52, 13));
            nodeDetails.child(button("save_filter", () -> tr("save_filter"),
                () -> edit(CenterGraphSync.Action.FILTER, nodeIndex, port * 3 + filterMode, filter.getText()))
                .right(4).top(167).size(63, 13));
            nodeDetails.child(filter.left(4).right(4).top(183).height(14)
                .tooltipBuilder(t -> t.add(IKey.lang(PREFIX + "filter.help"))));
            nodeDetails.child(button("target", this::targetText, this::nextTarget).left(4).top(201).size(93, 13));
            nodeDetails.child(button("save_target", () -> tr("save"), () -> edit(CenterGraphSync.Action.TARGET,
                nodeIndex, port * 65 + Math.max(-1, target) + 1, "")).right(4).top(201).size(32, 13));
            nodeDetails.child(label(() -> "NC", 5, 221, 24, 14).setEnabledIf(w -> hasCircuit()));
            nodeDetails.child(circuit.pos(31, 221).size(25, 13).setEnabledIf(w -> hasCircuit())
                .tooltipBuilder(t -> t.add(tr("circuit.help"))));
            nodeDetails.child(button("save_circuit", () -> tr("save"), () -> {
                try {
                    edit(CenterGraphSync.Action.CIRCUIT, nodeIndex, Integer.parseInt(circuit.getText().trim()), "");
                } catch (NumberFormatException invalid) {
                    sync.setStatus("status.invalid");
                }
            }).right(4).top(221).size(35, 13).setEnabledIf(w -> hasCircuit()));
            inspector.child(nodeDetails);
            contents.child(inspector);
            docks.add(inspector);

            ParentWidget<?> entries = CenterTerminalPages.card("center_graph_entries").right(0).top(65)
                .bottom(18).width(144).setEnabledIf(w -> showRules && !showSources);
            entries.child(label(() -> tr("rules"), 3, 3, 133, 15).setEnabledIf(w -> inspectedRule == null));
            entries.child(label(() -> tr("rule.matches"), 3, 3, 133, 15).setEnabledIf(w -> inspectedRule != null));
            entries.child(rules.left(4).right(4).top(21).height(16).setEnabledIf(w -> inspectedRule == null)
                .tooltipBuilder(t -> t.add(IKey.lang(PREFIX + "rules.help"))));
            entries.child(button("save_rules", () -> tr("save_rules"), () -> {
                edit(CenterGraphSync.Action.RULES, 0, 0, rules.getText());
                showRules = false;
            }).left(4).right(4).top(41).height(15).setEnabledIf(w -> inspectedRule == null));
            entries.child(new MatchingMinerals(this).name("center_graph_matching").left(4).right(4).top(22).bottom(3)
                .setEnabledIf(w -> inspectedRule != null));
            contents.child(entries);
            docks.add(entries);

            ParentWidget<?> sourcePanel = CenterTerminalPages.card("center_graph_source").right(0).top(65).bottom(18)
                .width(144).setEnabledIf(w -> showSources);
            sourcePanel.child(label(() -> tr("source.pack"), 3, 3, 133, 14));
            sourcePanel.child(sourceSearch.left(4).right(4).top(85).height(14)
                .tooltipBuilder(t -> t.add("NEI")));
            sourcePanel.child(sourceItems.left(4).right(4).top(19).bottom(17));
            sourcePanel.child(CenterTerminalPages.label(() -> carried == null ? tr("rule.drop") : carried.getDisplayName())
                .left(2).right(2).bottom(2).height(13));
            contents.child(sourcePanel);
            docks.add(sourcePanel);

            ParentWidget<?> status = CenterTerminalPages.card("center_graph_status")
                .left(0).right(0).bottom(1).height(15);
            status.child(CenterTerminalPages.label(() -> canvas.sample == null
                ? tr("terminal.sample") : canvas.sample.getDisplayName()).left(1).right(390).top(0).height(15));
            status.child(CriticalGuiStyle.label(() -> tr(graph != null && graph.runnable()
                ? "terminal.connected" : "terminal.open_outlets"), () -> 0xFF9BC6AB, Alignment.CENTER)
                .right(338).top(0).size(51, 15));
            status.child(button("wire_outputs", () -> tr("wire_outputs"),
                () -> edit(CenterGraphSync.Action.OUTLET_ALL, 0, 0, ""))
                .right(287).top(0).size(49, 15));
            status.child(button("undo", () -> tr("terminal.undo"), () -> edit(CenterGraphSync.Action.UNDO, 0, 0, ""))
                .right(257).top(0).size(28, 15).tooltipBuilder(t -> t.add("Ctrl+Z")));
            status.child(button("redo", () -> tr("terminal.redo"), () -> edit(CenterGraphSync.Action.REDO, 0, 0, ""))
                .right(227).top(0).size(28, 15).tooltipBuilder(t -> t.add("Ctrl+Y / Ctrl+Shift+Z")));
            status.child(button("box_select", () -> tr("terminal.box_select"), () -> canvas.boxMode = !canvas.boxMode)
                .right(182).top(0).size(42, 15)
                .background(new DynamicDrawable(() -> canvas.boxMode ? TerminalUiStyle.CHIP_ACTIVE : TerminalUiStyle.BUTTON)));
            status.child(button("arrange", () -> tr("terminal.arrange"), () -> {
                edit(CenterGraphSync.Action.ARRANGE, 0, 0, "");
                canvas.fitAfterSync = true;
            }).right(140).top(0).size(40, 15));
            status.child(button("fit", () -> tr("terminal.fit"), canvas::fit)
                .right(111).top(0).size(27, 15));
            status.child(button("zoom_out", () -> "−", () -> canvas.zoomBy(-0.12))
                .right(95).top(0).size(14, 15));
            status.child(button("zoom_reset", () -> (int) Math.round(canvas.zoom * 100) + "%", canvas::resetZoom)
                .right(63).top(0).size(30, 15));
            status.child(button("zoom_in", () -> "+", () -> canvas.zoomBy(0.12))
                .right(47).top(0).size(14, 15));
            status.child(button("shortcuts", () -> tr("terminal.shortcuts"), () -> showShortcuts = !showShortcuts)
                .right(1).top(0).size(44, 15).tooltipBuilder(t -> t.add(tr("canvas.help"))));
            contents.child(status);

            ParentWidget<?> shortcuts = CenterTerminalPages.card("center_graph_shortcuts")
                .right(2).bottom(18).size(188, 90).setEnabledIf(w -> showShortcuts);
            shortcuts.child(label(() -> tr("terminal.shortcuts"), 4, 1, 175, 13));
            for (int i = 0; i < 6; i++) {
                int row = i + 1;
                shortcuts.child(label(() -> tr("shortcut." + row), 4, row * 12 + 2, 178, 11));
            }
            contents.child(shortcuts);
            return contents;
        }

        private ModularPanel presetPanel(Runnable close) {
            var panel = new ModularPanel("gtsr_center_presets").size(387, 224)
                .background(TerminalUiStyle.CLUSTER_PANEL);
            panel.child(label(() -> tr("preset.title"), 5, 3, 195, 16));
            panel.child(button("preset_close", () -> tr("exit"), close).right(4).top(3).size(37, 15));
            ListWidget<IWidget, ?> menu = new ListWidget<>();
            menu.name("center_preset_groups").left(4).top(23).bottom(4).width(132);
            String group = "";
            for (Preset item : CenterPresets.all()) {
                if (!item.group.equals(group)) {
                    group = item.group;
                    String label = item.group;
                    menu.child(CenterTerminalPages.label(() -> label).fullWidth().height(13));
                }
                menu.child(button("preset_" + item.id, () -> item.name, () -> {
                    preset = item;
                    panel.scheduleResize();
                }).fullWidth().height(16).background(new DynamicDrawable(() -> preset == item
                    ? TerminalUiStyle.CHIP_ACTIVE : TerminalUiStyle.CHIP_NORMAL)));
            }
            panel.child(menu);
            var detail = CenterTerminalPages.card("center_preset_details").left(140).right(4).top(23).bottom(4);
            detail.child(CenterTerminalPages.label(() -> preset.name).left(4).right(4).top(3).height(14));
            detail.child(IKey.dynamic(() -> preset.description).color(0xFFC9D3D1).asWidget()
                .left(4).right(4).top(20).height(35));
            detail.child(CenterTerminalPages.label(() -> tr("preset.nodes", preset.nodeCount()))
                .left(4).right(4).top(57).height(13));
            ListWidget<IWidget, ?> expressions = new ListWidget<>();
            expressions.name("center_preset_expressions").left(4).right(4).top(73).bottom(21);
            int rows = CenterPresets.all().stream().mapToInt(p -> p.expressions.size()).max().orElse(0);
            for (int i = 0; i < rows; i++) {
                int row = i;
                var expression = new ParentWidget<>().fullWidth().height(16)
                    .setEnabledIf(w -> row < preset.expressions.size());
                expression.child(CenterTerminalPages.label(() -> row < preset.expressions.size()
                    ? preset.expressions.get(row).label() : "").left(0).right(61).top(0).height(15));
                expression.child(button("preset_expression_" + i, () -> tr(expressionFilter()
                    ? "preset.use_outlet" : "preset.use_rule"), () -> useExpression(row, close))
                    .right(0).top(0).size(59, 15).setEnabledIf(w -> graph != null && snapshot.writable())
                    .tooltipBuilder(t -> {
                        if (row < preset.expressions.size()) t.add(preset.expressions.get(row).rule());
                    }));
                expressions.child(expression);
            }
            detail.child(expressions);
            detail.child(button("preset_apply", () -> tr(preset.importable() ? "preset.apply" : "preset.rules_only"),
                () -> {
                    if (!preset.importable() || !snapshot.writable()) return;
                    if (snapshot.graphs().size() >= snapshot.capacity()) {
                        sync.setStatus("status.capacity");
                        return;
                    }
                    edit(CenterGraphSync.Action.PRESET, 0, 0, preset.id);
                    close.run();
                }).left(4).right(4).bottom(3).height(15));
            return panel.child(detail);
        }

        private boolean expressionFilter() {
            return preset.id.equals("special-electrolyzer") || preset.id.equals("special-centrifuge")
                || preset.id.equals("special-dehydrator");
        }

        private void useExpression(int index, Runnable close) {
            if (graph == null || index >= preset.expressions.size() || !snapshot.writable()) return;
            String expression = preset.expressions.get(index).rule();
            if (expressionFilter()) {
                if (node() == null || CenterGraphSync.direct(node().process)) {
                    sync.setStatus("preset.select_outlet");
                    return;
                }
                Set<String> values = new LinkedHashSet<>();
                for (OutletFilter outlet : node().filters())
                    if (outlet.slot == port && outlet.allow) values.addAll(outlet.rules());
                values.add(expression);
                edit(CenterGraphSync.Action.FILTER, nodeIndex, port * 3 + ALLOW, String.join(";", values));
            } else {
                Set<String> values = new LinkedHashSet<>(graph.rules());
                values.add(expression);
                edit(CenterGraphSync.Action.RULES, 0, 0, String.join(";", values));
            }
            close.run();
        }

        private void savePriority() {
            try {
                edit(CenterGraphSync.Action.PRIORITY, Integer.parseInt(priority.getText().trim()), 0, "");
            } catch (NumberFormatException invalid) {
                sync.setStatus("priority.invalid");
            }
        }

        private void openSources(boolean oreTerm) {
            chooseOreTerm = oreTerm;
            showSources = true;
            showRules = false;
        }

        private void toggleValidation() {
            checking = !checking;
            if (!checking) canvas.sample = null;
        }

        private boolean processReady(CenterProcess process) {
            NBTTagCompound state = sync.terminal();
            if (moduleSnapshot != state) {
                moduleSnapshot = state;
                Arrays.fill(unlocked, 0);
                int[] grades = state.getIntArray("Grades"), modules = state.getIntArray("Modules");
                int sectors = state.getInteger("Sectors");
                for (int i = 0; i < Math.min(grades.length, modules.length); i++) {
                    if ((sectors & (1 << i / 6)) == 0 || grades[i] <= 0) continue;
                    unlocked[modules[i]] = Math.max(unlocked[modules[i]], grades[i]);
                }
            }
            return process.installed(unlocked);
        }

        private int processCount() {
            int count = 0;
            for (CenterProcess process : PROCESSES) if (processReady(process)) count++;
            return count;
        }

        private boolean hasCircuit() {
            Node current = node();
            return current != null && (current.process == CenterProcess.REACTOR
                || current.process == CenterProcess.ELECTROLYZER || current.process == CenterProcess.FURNACE);
        }

        private void update() {
            long imported = sync.takeImportedGraph();
            CenterGraphSync.Snapshot next = sync.snapshot();
            if (imported > 0) {
                snapshot = next;
                selectGraph(imported);
                return;
            }
            if (next == snapshot) return;
            boolean sameRevision = next.revision() == snapshot.revision();
            snapshot = next;
            if (sameRevision) return;
            refresh();
        }

        private void refresh() {
            long previousId = graphId;
            graph = null;
            for (CenterGraph candidate : snapshot.graphs()) {
                if (candidate.id == graphId) {
                    graph = candidate;
                    break;
                }
            }
            if (graph == null && !snapshot.graphs()
                .isEmpty())
                graph = snapshot.graphs()
                    .get(0);
            graphId = graph == null ? -1 : graph.id;
            if (previousId != graphId) {
                canvas.reset();
                inspectedRule = null;
                confirmDelete = false;
            }
            setField(name, graph == null ? "" : graph.name.isEmpty() ? tr("graph") + " " + graph.id : graph.name);
            setField(priority, graph == null ? "" : Integer.toString(graph.priority));
            setField(rules, graph == null ? "" : String.join(";", graph.rules()));
            nodeIndex = graph == null || graph.nodes.isEmpty() ? 0 : Math.min(nodeIndex, graph.nodes.size() - 1);
            Node current = node();
            if (current == null || port >= CenterGraphSync.outlets(current)) port = 0;
            readPort();
        }

        private void selectGraph(long id) {
            if (id == graphId) return;
            graphId = id;
            nodeIndex = port = 0;
            canvas.reset();
            inspectedRule = null;
            confirmDelete = false;
            refresh();
        }

        private void selectNode(int index) {
            nodeIndex = index;
            port = 0;
            readPort();
        }

        private Node node() {
            return graph == null || graph.nodes.isEmpty() ? null : graph.nodes.get(nodeIndex);
        }

        private void readPort() {
            Node node = node();
            setField(circuit, node == null ? "" : Integer.toString(node.circuit));
            target = node == null ? CenterGraph.BUS : node.target(port);
            filterMode = UNRESTRICTED;
            setField(filter, "");
            if (node == null) return;
            for (OutletFilter outlet : node.filters()) {
                if (outlet.slot != port) continue;
                filterMode = outlet.allow ? ALLOW : DENY;
                setField(filter, String.join(";", outlet.rules()));
                break;
            }
        }

        private void nextTarget() {
            if (node() == null || CenterGraphSync.direct(node().process)) return;
            if (target <= CenterGraph.BUS && nodeIndex + 1 < graph.nodes.size()) target = nodeIndex + 1;
            else if (target != CenterGraph.BUS && target + 1 < graph.nodes.size()) target++;
            else target = CenterGraph.BUS;
        }

        private void edit(CenterGraphSync.Action action, int a, int b, String value) {
            if (action != CenterGraphSync.Action.ADD && action != CenterGraphSync.Action.PRESET && graph == null) return;
            sync.edit(action, snapshot, graphId, a, b, value);
        }

        private String enabledText() {
            return tr(graph != null && graph.enabled ? "enabled" : "disabled");
        }

        private String nodeText() {
            return node() == null ? tr("select_node") : (nodeIndex + 1) + "  " + processName(node().process);
        }

        private String portText() {
            Node current = node();
            if (current != null && CenterGraphSync.direct(current.process)) return tr("direct");
            return outletName(current, port);
        }

        private String modeText() {
            return tr(filterMode == ALLOW ? "allow" : filterMode == DENY ? "deny" : "unrestricted");
        }

        private String targetText() {
            return target == CenterGraph.UNCONNECTED ? tr("unconnected") : target == CenterGraph.BUS ? tr("output")
                : "→ " + (target + 1) + "  " + processName(graph.nodes.get(target).process);
        }

        private void acceptSample(int destination, int port) {
            if (graph == null || carried == null) return;
            String[] tags = CenterGraph.tags(carried);
            String rule = null;
            if (destination < 0) {
                for (String tag : tags) {
                    java.util.regex.Matcher matcher = MINERAL.matcher(tag);
                    if (!matcher.matches()) continue;
                    if (chooseOreTerm) rule = tag;
                    else rule = "^(?:ore|variantOre|rawOre|crushedPurified|crushed|dustImpure|dustPure)"
                        + matcher.group(1) + "$";
                    break;
                }
                if (rule == null) {
                    sync.setStatus("status.invalid");
                    return;
                }
                Set<String> entries = new LinkedHashSet<>(graph.rules());
                if (entries.add(rule)) edit(CenterGraphSync.Action.RULES, 0, 0, String.join(";", entries));
                if (checking) canvas.sample = carried.copy();
            } else if (chooseOreTerm && tags.length > 0) {
                rule = tags[0];
            } else {
                GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(carried.getItem());
                if (id != null) rule = "item:" + id + "@" + carried.getItemDamage();
            }
            if (destination >= 0 && rule != null) {
                selectNode(destination);
                this.port = port;
                readPort();
                Set<String> entries = new LinkedHashSet<>(Arrays.asList(filter.getText().split(";")));
                entries.remove("");
                if (entries.add(rule))
                    edit(CenterGraphSync.Action.FILTER, destination, port * 3 + Math.max(1, filterMode),
                        String.join(";", entries));
            }
            carried = null;
        }
    }

    private static ItemStack processIcon(CenterProcess process) {
        return CenterModuleItems.stack(process.module, 1);
    }

    private static final class ProcessList extends Widget<ProcessList> implements Interactable {

        private final Editor editor;
        private final List<CenterProcess> rows = new java.util.ArrayList<>();
        private final ItemStack[] icons = new ItemStack[PROCESSES.length];
        private int first;
        private String name;
        private final IDrawable text = CriticalGuiStyle.fitted(() -> name, () -> 0xFFF0E8D8, Alignment.CenterLeft);

        ProcessList(Editor editor) {
            this.editor = editor;
            tooltipBuilder(t -> {
                int index = first + getContext().getMouseY() / 16;
                if (index >= 0 && index < rows.size()) {
                    CenterProcess process = rows.get(index);
                    t.add(processName(process));
                    if (!editor.processReady(process)) t.add(tr("terminal.module_required"));
                    else if (!editor.canvas.acceptsLink(process)) t.add(tr("link.incompatible"));
                }
            });
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            rows.clear();
            String query = editor.processSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
            for (CenterProcess process : PROCESSES)
                if (query.isEmpty() || processName(process).toLowerCase(java.util.Locale.ROOT).contains(query))
                    rows.add(process);
            int count = getArea().height / 16;
            first = Math.min(first, Math.max(0, rows.size() - count));
            for (int i = first; i < Math.min(rows.size(), first + count); i++) {
                CenterProcess process = rows.get(i);
                int y = (i - first) * 16;
                boolean hover = context.getMouseY() / 16 == i - first
                    && context.getMouseX() >= 0 && context.getMouseX() < getArea().width;
                boolean available = editor.processReady(process) && editor.canvas.acceptsLink(process);
                (hover && available ? TerminalUiStyle.HOVER
                    : available ? TerminalUiStyle.BUTTON : TerminalUiStyle.CHIP_NORMAL)
                    .draw(context, 0, y, getArea().width, 15, theme.getTheme());
                ItemStack icon = icons[process.ordinal()];
                if (icon == null) icons[process.ordinal()] = icon = processIcon(process);
                GuiDraw.drawItem(icon, 5, y + 3, 10, 10, context.getCurrentDrawingZ());
                name = processName(process);
                text.draw(context, 15, y, getArea().width - 25, 15, null);
                name = available ? "+" : "·";
                text.draw(context, getArea().width - 9, y, 8, 15, null);
            }
            markTooltipDirty();
        }

        @Override
        public Result onMousePressed(int mouseButton) {
            int index = first + getContext().getMouseY() / 16;
            if (mouseButton != 0 || index < 0 || index >= rows.size()) return Result.IGNORE;
            CenterProcess process = rows.get(index);
            if (!editor.processReady(process) || !editor.canvas.acceptsLink(process)) return Result.IGNORE;
            if (editor.canvas.source >= 0) {
                editor.edit(CenterGraphSync.Action.APPEND_NODE, editor.canvas.source,
                    editor.canvas.sourcePort * PROCESSES.length + process.ordinal(), "");
                editor.canvas.clearLink();
            } else editor.edit(CenterGraphSync.Action.FREE_NODE, 0, process.ordinal(), "");
            return Result.SUCCESS;
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            first = Math.max(0, first + (direction == UpOrDown.UP ? -2 : 2));
            return true;
        }
    }

    private static final class OutletList extends Widget<OutletList> implements Interactable {

        private final Editor editor;
        private String caption;
        private int color;
        private final IDrawable text = CriticalGuiStyle.fitted(() -> caption, () -> color, Alignment.CenterLeft);
        private int first;

        OutletList(Editor editor) {
            this.editor = editor;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            Node node = editor.node();
            if (node == null) return;
            int outlets = CenterGraphSync.outlets(node), width = getArea().width;
            int visible = getArea().height / 29;
            first = Math.min(first, Math.max(0, outlets - visible));
            for (int i = first; i < Math.min(first + visible, outlets); i++) {
                int y = (i - first) * 29;
                TerminalUiStyle.CHIP_NORMAL.draw(context, 0, y, width, 27, theme.getTheme());
                if (editor.port == i) GuiDraw.drawRect(0, y, 1, 27, 0xFFE1AF71);
                caption = CenterGraphSync.direct(node.process) ? tr("direct") : outletName(node, i);
                color = i == 0 ? 0xFFE1AF71 : 0xFFA6C9B6;
                text.draw(context, 1, y, width - 4, 13, null);
                int target = node.target(i);
                caption = target >= 0 ? processName(editor.graph.nodes.get(target).process)
                    : tr(target == CenterGraph.BUS ? "output" : "unconnected");
                color = 0xFFC8C2B2;
                text.draw(context, 1, y + 13, width - 26, 12, null);
                if (!CenterGraphSync.direct(node.process)) {
                    caption = tr("terminal.link");
                    text.draw(context, width - 26, y + 13, 26, 12, null);
                }
            }
        }

        @Override
        public Result onMousePressed(int mouseButton) {
            Node node = editor.node();
            int port = first + getContext().getMouseY() / 29;
            if (mouseButton != 0 || node == null || port < 0 || port >= CenterGraphSync.outlets(node))
                return Result.IGNORE;
            editor.port = port;
            editor.readPort();
            if (editor.snapshot.writable() && !CenterGraphSync.direct(node.process)
                && getContext().getMouseX() > getArea().width - 28) {
                editor.canvas.armLink(editor.nodeIndex, port, null);
            }
            return Result.SUCCESS;
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            first = Math.max(0, first + (direction == UpOrDown.UP ? -1 : 1));
            return true;
        }
    }

    private static final class GraphActions extends ParentWidget<GraphActions> {

        private final ParentWidget<?> toolbar;
        private final IntConsumer moveDocks;
        private Boolean wide;

        GraphActions(ParentWidget<?> toolbar, IntConsumer moveDocks) {
            this.toolbar = toolbar;
            this.moveDocks = moveDocks;
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            boolean next = toolbar.getArea().width >= 670;
            if (wide != null && next == wide) return;
            wide = next;
            pos(next ? 349 : 0, next ? 0 : 16);
            toolbar.height(next ? 16 : 32);
            moveDocks.accept(next ? 49 : 65);
            toolbar.scheduleResize();
        }
    }

    private static final class MatchingMinerals extends Widget<MatchingMinerals> implements Interactable {

        private final Editor editor;
        private final List<ItemStack> matches = new java.util.ArrayList<>();
        private CenterGraph.PreviewInputs search;
        private String rule;
        private int first;

        MatchingMinerals(Editor editor) {
            this.editor = editor;
            tooltipBuilder(t -> {
                int index = first + getContext().getMouseY() / 16;
                if (index < matches.size()) {
                    for (String line : matches.get(index).getTooltip(CenterTerminalClient.player(), false)) t.add(line);
                }
            });
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            if (!java.util.Objects.equals(rule, editor.inspectedRule)) {
                rule = editor.inspectedRule;
                matches.clear();
                search = rule == null ? null : new CenterGraph.PreviewInputs(rule);
                first = 0;
            }
            if (search != null && search.advance(matches)) search = null;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int rows = getArea().height / 16;
            for (int i = 0; i < rows && first + i < matches.size(); i++) {
                ItemStack mineral = matches.get(first + i);
                TerminalUiStyle.CHIP_NORMAL.draw(context, 0, i * 16, getArea().width, 15, theme.getTheme());
                GuiDraw.drawItem(mineral, 2, i * 16 + 1, 13, 13, context.getCurrentDrawingZ());
                CriticalGuiStyle.fitted(mineral::getDisplayName, () -> CriticalGuiStyle.TEXT_TITLE, Alignment.CenterLeft)
                    .draw(context, 17, i * 16, getArea().width - 19, 15, theme.getTheme());
            }
            if (matches.isEmpty()) CriticalGuiStyle.fitted(() -> tr(search == null ? "rule.no_matches" : "status.loading"),
                () -> CriticalGuiStyle.TEXT_MUTED, Alignment.Center)
                .draw(context, 0, 0, getArea().width, 16, theme.getTheme());
            markTooltipDirty();
        }

        @Override
        public Result onMousePressed(int button) {
            int index = first + getContext().getMouseY() / 16;
            if (button != 0 || index >= matches.size()) return Result.IGNORE;
            editor.checking = true;
            editor.canvas.sample = matches.get(index).copy();
            return Result.SUCCESS;
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            first = Math.max(0, Math.min(Math.max(0, matches.size() - getArea().height / 16),
                first + (direction == UpOrDown.UP ? -1 : 1)));
            return true;
        }
    }

    private static final class SourceItems extends Widget<SourceItems> implements Interactable {

        private final Editor editor;
        private List<ItemStack> filtered = Collections.emptyList(), source;
        private String query = "";
        private int row;

        SourceItems(Editor editor) {
            this.editor = editor;
            tooltipBuilder(t -> {
                ItemStack item = hit(getContext().getMouseX(), getContext().getMouseY());
                if (item != null)
                    for (String line : item.getTooltip(CenterTerminalClient.player(), false)) t.add(line);
            });
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            EntityPlayer player = CenterTerminalClient.player();
            if (player == null) return;
            List<ItemStack> catalog = CenterTerminalClient.catalog();
            String search = editor.sourceSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
            if (source != catalog || !search.equals(query)) {
                source = catalog;
                query = search;
                row = 0;
                if (query.isEmpty()) filtered = catalog;
                else {
                    filtered = new java.util.ArrayList<>();
                    for (ItemStack item : catalog) if (item != null && item.getDisplayName()
                        .toLowerCase(java.util.Locale.ROOT).contains(query)) filtered.add(item);
                }
            }
            for (int i = 0; i < 36; i++) {
                int inventory = i < 27 ? i + 9 : i - 27;
                slot(context, theme, player.inventory.getStackInSlot(inventory),
                    i % 9 * 15, i / 9 * 15);
            }
            int rows = rows();
            row = Math.min(row, Math.max(0, (filtered.size() + 8) / 9 - rows));
            for (int i = 0; i < rows * 9 && row * 9 + i < filtered.size(); i++)
                slot(context, theme, filtered.get(row * 9 + i), i % 9 * 15, 85 + i / 9 * 15);
            markTooltipDirty();
        }

        private int rows() { return Math.max(0, (getArea().height - 85) / 15); }

        private void slot(ModularGuiContext context, WidgetThemeEntry<?> theme, ItemStack item, int x, int y) {
            TerminalUiStyle.SLOT_FRAME.draw(context, x, y, 14, 14, theme.getTheme());
            if (item != null) GuiDraw.drawItem(item, x + 1, y + 1, 12, 12, context.getCurrentDrawingZ());
        }

        private ItemStack hit(int x, int y) {
            EntityPlayer player = CenterTerminalClient.player();
            if (x < 0 || y < 0 || x >= 135 || player == null) return null;
            if (y < 60) {
                int i = x / 15 + y / 15 * 9;
                return player.inventory.getStackInSlot(i < 27 ? i + 9 : i - 27);
            }
            if (y < 85 || y >= 85 + rows() * 15) return null;
            int i = row * 9 + x / 15 + (y - 85) / 15 * 9;
            return i < filtered.size() ? filtered.get(i) : null;
        }

        @Override
        public Result onMousePressed(int button) {
            ItemStack item = hit(getContext().getMouseX(), getContext().getMouseY());
            if ((button != 0 && button != 1) || item == null) return Result.IGNORE;
            editor.carried = item.copy();
            editor.carried.stackSize = button == 1 ? 1 : Math.max(1, item.stackSize);
            return Result.SUCCESS;
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            row = Math.max(0, row + (direction == UpOrDown.UP ? -1 : 1));
            return true;
        }
    }

    private static final class Tabs extends Widget<Tabs> implements Interactable {

        private final Editor editor;
        private final IDrawable title;
        private long pressedId = -1;
        private String text = "";
        private int color;

        private Tabs(Editor editor) {
            this.editor = editor;
            title = CriticalGuiStyle.fitted(() -> text, () -> color, Alignment.CENTER);
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            editor.update();
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            List<CenterGraph> graphs = editor.snapshot.graphs();
            int hovered = indexAt(context.getMouseX(), context.getMouseY());
            for (int i = 0; i < graphs.size(); i++) {
                CenterGraph graph = graphs.get(i);
                int columns = columns();
                if (i >= columns) break;
                int x = i % columns * TAB_WIDTH, y = i / columns * TAB_HEIGHT;
                boolean selected = graph.id == editor.graphId;
                IDrawable skin = selected ? CriticalGuiStyle.SELECTED_TAB
                    : i == hovered ? CriticalGuiStyle.HOVER : CriticalGuiStyle.TAB;
                skin.draw(context, x, y, TAB_WIDTH - 2, TAB_HEIGHT - 2, theme.getTheme());
                GuiDraw.drawRect(
                    x + 3,
                    y + 4,
                    2,
                    6,
                    graph.enabled ? CriticalGuiStyle.STATE_ONLINE : CriticalGuiStyle.STATE_OFFLINE);
                text = graph.name.isEmpty() ? tr("graph") + " " + (i + 1) : graph.name;
                color = graph.enabled ? CriticalGuiStyle.TEXT_TITLE : CriticalGuiStyle.TEXT_MUTED;
                title.draw(context, x + 5, y, TAB_WIDTH - 9, TAB_HEIGHT - 2, null);
                if (pressedId > 0 && i == hovered && graphs.get(i).id != pressedId) {
                    GuiDraw.drawRect(x, y + TAB_HEIGHT - 2, TAB_WIDTH - 2, 2, CriticalGuiStyle.TEXT_ACCENT);
                }
            }
        }

        private int indexAt(int x, int y) {
            int columns = columns();
            if (x < 0 || y < 0 || x >= columns * TAB_WIDTH || y >= TAB_HEIGHT) return -1;
            int i = y / TAB_HEIGHT * columns + x / TAB_WIDTH;
            return i < editor.snapshot.graphs()
                .size() ? i : -1;
        }

        private int columns() {
            return Math.max(1, getArea().width / TAB_WIDTH);
        }

        @Override
        public Result onMousePressed(int mouseButton) {
            if (mouseButton != 0) return Result.IGNORE;
            pressedId = -1;
            int index = indexAt(getContext().getMouseX(), getContext().getMouseY());
            if (index < 0) return Result.IGNORE;
            pressedId = editor.snapshot.graphs()
                .get(index).id;
            editor.selectGraph(pressedId);
            return Result.SUCCESS;
        }

        @Override
        public void onMouseLeaveArea() {
            super.onMouseLeaveArea();
            pressedId = -1;
        }

        @Override
        public boolean onMouseRelease(int mouseButton) {
            if (mouseButton != 0 || pressedId < 0) return false;
            long sourceId = pressedId;
            pressedId = -1;
            int index = indexAt(getContext().getMouseX(), getContext().getMouseY());
            if (index < 0 || sourceId == editor.snapshot.graphs()
                .get(index).id) return true;
            editor.sync.edit(CenterGraphSync.Action.REORDER, editor.snapshot, sourceId, index, 0, "");
            return true;
        }
    }


    private static final class Canvas extends Widget<Canvas> implements Interactable {

        private static final int NODE_W = 92, HEAD = 14, ROW = 13, BUS_W = 69, BUS_H = 26, TILE_H = 16;
        private static final int BUS_Y = 48, RULE_X = -94, RULE_STEP = 19, RULE_ROWS = 8;
        private static final int PROCESS_X = 122, COLUMN_STEP = 146, CARD_GAP = 26;
        private static final int SAMPLE_X = -43, SAMPLE_Y = -24;
        private static final int GRID = 0xFF323C42, LINE = 0xFF95C4AA, BACKGROUND = 0xFF20262C;
        private static final int OUT = -2, IN = -3, MODULE_GHOST = -4, RULE_GHOST = -5;
        private final Editor editor;
        private final CenterCircuitWires circuits = new CenterCircuitWires();
        private final IDrawable text;
        private final IDrawable productText;
        private final Set<Integer> marked = new LinkedHashSet<>();
        private int[][] nodes = new int[0][];
        private String copied = "";
        private long copiedGraph = -1;
        private int offX, offY, pressX, pressY, dx, dy, source = -1, sourcePort;
        private int dragBus;
        private CenterGraphPreview.Product linkedProduct;
        private final boolean[] linkReceivers = new boolean[PROCESSES.length];
        private boolean moving, panning, boxMode, selecting, fitPending = true, fitAfterSync;
        private CenterGraph laidOut;
        private int inputX, inputY, outputX, outputY, minX, minY, maxX, maxY;
        private ItemStack[] ruleIcons = new ItemStack[0];
        private double zoom = 1;
        private final ItemStack[] icons = new ItemStack[PROCESSES.length];
        private ItemStack sample;
        private ItemStack tracedSample;
        private CenterGraph tracedGraph;
        private CenterGraphPreview trace;
        private String line = "";
        private int ink = CriticalGuiStyle.TEXT_BODY;

        Canvas(Editor editor) {
            this.editor = editor;
            text = compact(() -> line, () -> ink, Alignment.CenterLeft);
            productText = compact(() -> line, () -> ink, Alignment.CenterLeft, .43f);
            tooltipBuilder(t -> {
                if (editor.graph == null || editor.graph != laidOut) return;
                int x = mouseX(), y = mouseY(), index = hit(x, y);
                if (index == MODULE_GHOST || index == RULE_GHOST || index == IN || index == OUT) {
                    t.add(tr(switch (index) {
                        case MODULE_GHOST -> "ghost.process.help";
                        case RULE_GHOST -> "ghost.rule.help";
                        case IN -> "ghost.input.help";
                        default -> "ghost.output.help";
                    }));
                    if (index == IN || index == OUT) t.add(tr("bus.move"));
                    return;
                }
                int rule = ruleAt(x, y);
                if (rule >= 0) {
                    t.add(ruleIcons[rule] == null ? editor.graph.rules().get(rule) : ruleIcons[rule].getDisplayName());
                    return;
                }
                if (trace == null || index < 0 || y < nodes[index][1] + portOffset(editor.graph.nodes.get(index))) return;
                int port = (y - nodes[index][1] - portOffset(editor.graph.nodes.get(index))) / ROW;
                for (CenterGraphPreview.Product product : trace.products(index)) {
                    if (product.port != port) continue;
                    String name = product.item == null
                        ? product.fluid.getLocalizedName() + " " + product.fluid.amount + " L"
                        : product.item.getDisplayName() + " × " + product.item.stackSize;
                    t.add(name + (product.chance < 10000
                        ? " · " + String.format(java.util.Locale.ROOT, "%.2f%%", product.chance / 100.0) : ""));
                }
            });
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            editor.update();
            if (sample != tracedSample || tracedGraph != editor.graph) {
                tracedGraph = editor.graph;
                tracedSample = sample;
                trace = tracedGraph == null || sample == null ? null
                    : new CenterGraphPreview(tracedGraph, sample, editor.sync.terminal());
            }
            if (trace != null) trace.advance();
        }

        void reset() {
            marked.clear();
            fit();
            clearLink();
            sample = null;
            dragBus = 0;
            moving = panning = selecting = boxMode = fitAfterSync = false;
        }

        private void fit() { fitPending = true; }

        private void fitLayout() {
            zoom = Math.min(1, Math.min((getArea().width - 12.0) / (maxX - minX),
                (getArea().height - 12.0) / (maxY - minY)));
            zoom = Math.max(.04, zoom);
            offX = (boardWidth() - minX - maxX) / 2;
            offY = (boardHeight() - minY - maxY) / 2;
            fitPending = false;
        }

        private int boardWidth() { return (int) Math.ceil(getArea().width / zoom); }
        private int boardHeight() { return (int) Math.ceil(getArea().height / zoom); }
        private int mouseX() { return (int) Math.floor(getContext().getMouseX() / zoom); }
        private int mouseY() { return (int) Math.floor(getContext().getMouseY() / zoom); }

        private void resetZoom() { zoomBy(1 - zoom); }

        private void zoomBy(double amount) {
            zoomAt(amount, getArea().width / 2, getArea().height / 2);
        }

        private void zoomAt(double amount, int x, int y) {
            double next = Math.max(.04, Math.min(1.65, zoom + amount));
            offX += Math.round(x / next - x / zoom);
            offY += Math.round(y / next - y / zoom);
            zoom = next;
        }

        private void focus(int node) {
            if (node < 0 || node >= nodes.length) return;
            offX = boardWidth() / 2 - nodes[node][2] - NODE_W / 2;
            offY = boardHeight() / 2 - nodes[node][3] - HEAD / 2;
        }

        private void positions(CenterGraph graph) {
            if (laidOut != graph) {
                laidOut = graph;
                if (fitAfterSync) {
                    fit();
                    fitAfterSync = false;
                }
                int count = graph.nodes.size();
                int[] depth = new int[count], bottom = new int[count];
                int[] terminals = new int[count];
                int terminalCount = 0;
                nodes = new int[count][4];
                int ruleCount = graph.rules().size();
                ruleIcons = new ItemStack[ruleCount];
                inputX = graph.inputPosition == null ? 0 : graph.inputPosition.x;
                inputY = graph.inputPosition == null ? BUS_Y : graph.inputPosition.y;
                minX = inputX + RULE_X - (Math.max(0, ruleCount - 1) / RULE_ROWS) * 82;
                minY = inputY + SAMPLE_Y;
                int right = count == 0 ? inputX + PROCESS_X + NODE_W
                    : Math.max(PROCESS_X + NODE_W, inputX + BUS_W);
                maxY = inputY + Math.max(1, Math.min(ruleCount, RULE_ROWS)) * RULE_STEP;
                for (int i = 0; i < ruleCount; i++) ruleIcons[i] = mineralIcon(graph.rules().get(i));
                for (int i = 0; i < count; i++) {
                    Node node = graph.nodes.get(i);
                    int layer = depth[i], h = cardHeight(node);
                    int x = node.x == CenterGraph.AUTO_POSITION ? PROCESS_X + layer * COLUMN_STEP : node.x;
                    int y = node.y == CenterGraph.AUTO_POSITION ? BUS_Y + bottom[layer] : node.y;
                    bottom[layer] += h + CARD_GAP;
                    nodes[i][2] = x;
                    nodes[i][3] = y;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    right = Math.max(right, x + NODE_W);
                    maxY = Math.max(maxY, y + h);
                    boolean terminal = false;
                    for (int target : node.targets()) {
                        if (target >= 0) depth[target] = Math.max(depth[target], layer + 1);
                        else if (target == CenterGraph.BUS) terminal = true;
                    }
                    if (terminal) terminals[terminalCount++] = y;
                }
                Arrays.sort(terminals, 0, terminalCount);
                outputX = graph.outputPosition == null ? right + 68 : graph.outputPosition.x;
                outputY = graph.outputPosition != null ? graph.outputPosition.y
                    : terminalCount == 0 ? inputY : terminals[terminalCount / 2];
                minX = Math.min(minX, outputX);
                minY = Math.min(minY, outputY);
                maxX = Math.max(right, outputX + BUS_W);
                maxY = Math.max(maxY, Math.max(inputY, outputY) + BUS_H);
            }
            if (fitPending) fitLayout();
            for (int i = 0; i < nodes.length; i++) {
                boolean dragged = moving && marked.contains(i);
                nodes[i][0] = nodes[i][2] + offX + (dragged ? dx : 0);
                nodes[i][1] = nodes[i][3] + offY + (dragged ? dy : 0);
            }
        }

        private static ItemStack mineralIcon(String rule) {
            String prefix = "^(?:ore|variantOre|rawOre|crushedPurified|crushed|dustImpure|dustPure)";
            if (!rule.startsWith(prefix) || !rule.endsWith("$")) return null;
            String material = rule.substring(prefix.length(), rule.length() - 1);
            return material.matches("[A-Z][A-Za-z0-9_]*") ? oreStack("rawOre" + material) : null;
        }

        private static ItemStack oreStack(String name) {
            if (!OreDictionary.doesOreNameExist(name)) return null;
            for (ItemStack stack : OreDictionary.getOres(name, false)) if (stack != null) return stack;
            return null;
        }

        private int busX(int bus) { return offX + (bus == IN ? inputX : outputX) + (dragBus == bus ? dx : 0); }
        private int busY(int bus) { return offY + (bus == IN ? inputY : outputY) + (dragBus == bus ? dy : 0); }
        private int ruleX(int index) { return busX(IN) + RULE_X - index / RULE_ROWS * 82; }
        private int ruleY(int index) { return busY(IN) + index % RULE_ROWS * RULE_STEP; }
        private int ruleWidth(int index) { return ruleIcons[index] == null ? BUS_W : TILE_H; }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            editor.update();
            int width = getArea().width, height = getArea().height;
            if (width < 16 || height < 16) return;
            GuiDraw.drawRect(0, 0, width, height, BACKGROUND);
            CenterGraph graph = editor.graph;
            if (graph == null) {
                caption(context, tr("empty"), Math.max(2, width / 2 - 82), height / 2, 164, 18,
                    CriticalGuiStyle.TEXT_MUTED);
                return;
            }
            positions(graph);
            Stencil.applyAtZero(getArea(), context);
            GlStateManager.pushMatrix();
            GlStateManager.scale(zoom, zoom, 1);
            try {
                drawBoard(context, theme.getTheme(), graph);
            } finally {
                GlStateManager.popMatrix();
                Stencil.remove();
            }
        }

        private void drawBoard(ModularGuiContext context, WidgetTheme theme, CenterGraph graph) {
            int width = boardWidth(), height = boardHeight();
            int entry = trace == null || tracedGraph != graph ? 0 : trace.entryNode();
            int stride = 10 * Math.max(1, (int) Math.ceil(.65 / zoom));
            for (int x = Math.floorMod(offX, stride); x < width; x += stride) rect(x, 0, 1, height, GRID);
            for (int y = Math.floorMod(offY, stride); y < height; y += stride) rect(0, y, width, 1, GRID);
            drawCircuits(graph, width, height, stride);
            if (source >= 0 && source < nodes.length) wire(nodes[source][0] + NODE_W, portY(source, sourcePort),
                mouseX(), mouseY(), 0xFFDFC381);
            int sampleX = busX(IN) + SAMPLE_X, sampleY = busY(IN) + SAMPLE_Y;
            if (sample != null && visible(sampleX, sampleY, 18, 18)) {
                rect(sampleX, sampleY, 18, 18, 0xFFA8C6AD);
                rect(sampleX + 1, sampleY + 1, 16, 16, 0xFF32383F);
                GuiDraw.drawItem(sample, sampleX + 2, sampleY + 2, 14, 14, context.getCurrentDrawingZ());
            }
            for (int i = 0; i < ruleIcons.length; i++) {
                int x = ruleX(i), y = ruleY(i);
                ItemStack icon = ruleIcons[i];
                if (icon == null) box(context, theme, graph.rules().get(i), x, y);
                else if (visible(x, y, TILE_H, TILE_H)) {
                    TerminalUiStyle.SLOT_FRAME.draw(context, x, y, TILE_H, TILE_H, theme);
                    GuiDraw.drawItem(icon, x + 2, y + 2, 12, 12, context.getCurrentDrawingZ());
                }
                rect(x + ruleWidth(i) - 2, y + 6, 4, 4, LINE);
            }
            int hover = hit(mouseX(), mouseY());
            if (ruleIcons.length == 0)
                ghost(context, tr("ghost.rule"), ruleX(0), ruleY(0), BUS_W, hover == RULE_GHOST, null);
            if (graph.nodes.isEmpty())
                ghost(context, tr("ghost.process"), busX(IN) + PROCESS_X, busY(IN), NODE_W, hover == MODULE_GHOST,
                    CenterProcess.CRUSHER);
            for (int i = 0; i < nodes.length; i++) {
                Node node = graph.nodes.get(i);
                int x = nodes[i][0], y = nodes[i][1];
                int slots = CenterGraphSync.outlets(node), h = cardHeight(node);
                if (x <= -NODE_W || x >= width || y <= -h || y >= height) continue;
                boolean selected = i == editor.nodeIndex || marked.contains(i);
                boolean reached = trace != null && !trace.products(i).isEmpty();
                rect(x - 1, y - 1, NODE_W + 2, h + 2,
                    selected ? CriticalGuiStyle.TEXT_ACCENT : reached ? 0xFFA4D3A5 : 0xFF667874);
                rect(x, y, NODE_W, h, 0xFF2D3238);
                rect(x + 2, y + 2, NODE_W - 4, HEAD - 2, 0xFF454B53);
                if (x >= 0 && x + NODE_W <= width && y >= 0 && y + HEAD <= height) {
                    ItemStack icon = icons[node.process.ordinal()];
                    if (icon == null) icons[node.process.ordinal()] = icon = processIcon(node.process);
                    GuiDraw.drawItem(icon, x + 5, y + 3, 9, 9, context.getCurrentDrawingZ());
                    caption(context, processName(node.process), x + 13, y + 1, NODE_W - (i == entry ? 29 : 15), HEAD,
                        CriticalGuiStyle.TEXT_TITLE);
                    if (i == entry) caption(context, tr("terminal.inlet"), x + NODE_W - 18, y + 1, 18, HEAD, 0xFFA6C9B6);
                    drawAuxiliaries(context, theme, node, x, y);
                }
                rect(x - 2, y + 8, 4, 4, LINE);
                for (int port = 0; port < slots; port++) {
                    int row = y + portOffset(node) + port * ROW;
                    rect(x + 2, row, NODE_W - 4, ROW - 1, 0xFF40464F);
                    boolean direct = CenterGraphSync.direct(node.process);
                    int color = node.target(port) == CenterGraph.UNCONNECTED ? 0xFFDC9360
                        : port == 0 && !direct ? 0xFFE4AF6D : 0xFF9DCAB9;
                    String label = direct ? tr("direct") : outletName(node, port);
                    if (x >= 0 && x + NODE_W <= width && row >= 0 && row + ROW <= height) {
                        caption(context, label, x + 2, row, reached ? 43 : NODE_W - 23, ROW, color);
                        if (reached) drawProducts(context, i, port, x + 40, row);
                        String restriction = direct ? tr("direct") : tr("unrestricted");
                        for (OutletFilter f : node.filters()) if (f.slot == port)
                            restriction = tr(f.allow ? "allow" : "deny");
                        caption(context, restriction, x + NODE_W - 18, row, 17, ROW, color);
                    }
                    rect(x + NODE_W - 2, row + ROW / 2 - 1, 5, 4, color);
                }
                if (x >= 0 && x + NODE_W <= width && y >= 0 && y + h <= height) {
                    int footer = y + h - 10;
                    if (reached) {
                        CenterGraphPreview.Product first = trace.products(i).get(0);
                        caption(context, tr(trace.conditionalRecipe(i) ? "terminal.recipe_conditional" : "terminal.recipe", first.item == null
                            ? first.fluid.getLocalizedName() : first.item.getDisplayName()), x + 2, footer,
                            NODE_W - 3, 10, 0xFFDCCAA9);
                    } else caption(context, tr("terminal.no_sample"), x + 2, footer, NODE_W - 3, 10, 0xFFC1C7BF);
                }
                if (canReceiveLink(i)) {
                    int top = receiverY(i), receiverHeight = receiveAtHeader() ? HEAD - 2 : 11;
                    if (!receiveAtHeader()) {
                        rect(x + 2, top, NODE_W - 4, receiverHeight, 0xFF293F36);
                        caption(context, outletName(graph.nodes.get(source), sourcePort), x + 5, top, NODE_W - 10,
                            receiverHeight, 0xFFB9E6B8);
                    }
                    int green = 0xFF9ADD9F;
                    rect(x + 2, top, NODE_W - 4, 1, green);
                    rect(x + 2, top + receiverHeight - 1, NODE_W - 4, 1, green);
                    rect(x + 2, top, 1, receiverHeight, green);
                    rect(x + NODE_W - 3, top, 1, receiverHeight, green);
                }
                if (source < 0 && hover == i && mouseY() < y + HEAD && !moving)
                    caption(context, "✥", Math.min(width - 8, mouseX() + 5),
                        Math.min(height - 10, mouseY() + 5), 8, 10, 0xFFF3D3A2);
            }
            int inX = busX(IN), inY = busY(IN), outX = busX(OUT), outY = busY(OUT);
            bus(context, theme, tr("input"), nodes.length == 0 ? tr("bus.entry") : "→ " + processName(graph.nodes.get(entry).process),
                inX, inY);
            bus(context, theme, tr("output"), tr("bus.receive"), outX, outY);
            rect(inX - 2, inY + BUS_H / 2 - 2, 4, 4, LINE);
            rect(inX + BUS_W - 2, inY + BUS_H / 2 - 2, 4, 4, LINE);
            rect(outX - 2, outY + BUS_H / 2 - 2, 4, 4, 0xFFE2AD68);
            if (dragBus != 0 || editor.snapshot.writable() && onBusBorder(hover, mouseX(), mouseY()))
                caption(context, "✥", Math.min(width - 8, mouseX() + 5), Math.min(height - 10, mouseY() + 5),
                    8, 10, 0xFFF3D3A2);
            if (selecting) {
                int x = Math.min(pressX, pressX + dx), y = Math.min(pressY, pressY + dy);
                int w = Math.abs(dx) + 1, h = Math.abs(dy) + 1;
                rect(x, y, w, h, 0x336ACAA9);
                rect(x, y, w, 1, LINE);
                rect(x, y + h - 1, w, 1, LINE);
                rect(x, y, 1, h, LINE);
                rect(x + w - 1, y, 1, h, LINE);
            }
            markTooltipDirty();
        }

        private void drawProducts(ModularGuiContext context, int index, int port, int x, int y) {
            int shown = 0;
            for (CenterGraphPreview.Product product : trace.products(index)) {
                if (product.port != port) continue;
                if (shown >= 1) break;
                int at = x + shown++ * 16;
                ItemStack output = product.item;
                if (output != null) GuiDraw.drawItem(output, at, y + 2, 9, 9, context.getCurrentDrawingZ());
                if (output != null) {
                    line = "×" + output.stackSize + " " + chance(product.chance);
                    ink = 0xFFE2D3BC;
                    productText.draw(context, at + 9, y, NODE_W - 66, ROW, null);
                } else {
                    line = product.fluid.amount + " L " + chance(product.chance);
                    ink = 0xFF8DC7E0;
                    productText.draw(context, at, y, NODE_W - 61, ROW, null);
                }
            }
        }

        private static int cardHeight(Node node) {
            return portOffset(node) + CenterGraphSync.outlets(node) * ROW + 11;
        }

        private static int portOffset(Node node) {
            return HEAD + (node.process.auxiliarySlots() > 0 ? 28 : 0) + (node.process.hasCircuitSlot() ? 12 : 0);
        }

        private void drawAuxiliaries(ModularGuiContext context, WidgetTheme theme, Node node, int x, int y) {
            int count = node.process.auxiliarySlots();
            for (int i = 0; i < count * 2; i++) {
                int sx = x + 32 + i % count * 17, sy = y + HEAD + i / count * 14;
                TerminalUiStyle.SLOT_FRAME.draw(context, sx, sy, 14, 13, theme);
                String key = node.auxiliary(i);
                ItemStack stack = i < count ? itemKey(key) : null;
                if (stack != null) GuiDraw.drawItem(stack, sx + 1, sy + 1, 12, 12, context.getCurrentDrawingZ());
                else if (!key.isEmpty()) caption(context, "L", sx, sy, 13, 13, 0xFF9DD7EB);
            }
            if (count > 0) {
                caption(context, tr("terminal.items"), x + 2, y + HEAD, 31, 13, 0xFFC8C2B2);
                caption(context, tr("terminal.fluids"), x + 2, y + HEAD + 14, 31, 13, 0xFF9DD7EB);
            }
            if (node.process.hasCircuitSlot()) {
                int line = y + portOffset(node) - 12;
                caption(context, "NC", x + 2, line, 25, 12, 0xFFC8C2B2);
                TerminalUiStyle.SLOT_FRAME.draw(context, x + 32, line, 14, 12, theme);
                if (node.circuit > 0) caption(context, Integer.toString(node.circuit), x + 33, line, 15, 12, 0xFFE2CA9E);
            }
        }

        private static ItemStack itemKey(String key) {
            int colon = key.indexOf(':'), meta = key.lastIndexOf('@');
            if (colon < 1 || meta <= colon + 1) return null;
            Item item = GameRegistry.findItem(key.substring(0, colon), key.substring(colon + 1, meta));
            return item == null ? null : new ItemStack(item, 1, Integer.parseInt(key.substring(meta + 1)));
        }

        private int auxiliaryAt(int index, int x, int y) {
            Node node = editor.graph.nodes.get(index);
            int count = node.process.auxiliarySlots();
            int col = x - nodes[index][0] - 32, row = y - nodes[index][1] - HEAD;
            if (col < 0 || row < 0) return -1;
            if (node.process.hasCircuitSlot() && col < 14 && row >= countRows(count) && row < countRows(count) + 12)
                return count * 2;
            if (count == 0 || row >= 28 || col / 17 >= count || col % 17 >= 14) return -1;
            return row / 14 * count + col / 17;
        }

        private static int countRows(int count) { return count > 0 ? 28 : 0; }

        private void applyAuxiliary(int index, int slot) {
            Node node = editor.graph.nodes.get(index);
            int count = node.process.auxiliarySlots();
            ItemStack stack = editor.carried;
            if (slot == count * 2) {
                if (stack == null || ItemList.Circuit_Integrated.isStackEqual(stack, true, true)) {
                    int number = stack == null ? 0 : stack.getItemDamage();
                    if (number >= 0 && number <= 24) editor.edit(CenterGraphSync.Action.CIRCUIT, index, number, "");
                }
            } else {
                String key = "";
                if (stack != null && slot < count) {
                    GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(stack.getItem());
                    if (id == null) return;
                    key = id + "@" + stack.getItemDamage();
                } else if (stack != null) {
                    FluidStack fluid = GTUtility.getFluidForFilledItem(stack, true);
                    if (fluid == null) return;
                    key = fluid.getFluid().getName();
                }
                editor.edit(CenterGraphSync.Action.AUXILIARY, index, slot, key);
            }
            editor.carried = null;
        }

        private int hit(int x, int y) {
            if (inside(x, y, busX(OUT), busY(OUT), BUS_W, BUS_H)) return OUT;
            if (inside(x, y, busX(IN), busY(IN), BUS_W, BUS_H)) return IN;
            for (int i = nodes.length - 1; i >= 0; i--) {
                if (inside(x, y, nodes[i][0] - 2, nodes[i][1], NODE_W + 5, cardHeight(laidOut.nodes.get(i)))) return i;
            }
            if (nodes.length == 0 && inside(x, y, busX(IN) + PROCESS_X, busY(IN), NODE_W, 23)) return MODULE_GHOST;
            if (ruleIcons.length == 0 && inside(x, y, ruleX(0), ruleY(0), BUS_W, TILE_H)) return RULE_GHOST;
            return -1;
        }

        private boolean onBusBorder(int bus, int x, int y) {
            if (bus != IN && bus != OUT) return false;
            int border = Math.min(8, (int) Math.ceil(3 / zoom));
            return !inside(x, y, busX(bus) + border, busY(bus) + border, BUS_W - 2 * border, BUS_H - 2 * border);
        }

        private static boolean inside(int x, int y, int left, int top, int width, int height) {
            return x >= left && x < left + width && y >= top && y < top + height;
        }

        private boolean visible(int x, int y, int w, int h) {
            return x + w > 0 && y + h > 0 && x < boardWidth() && y < boardHeight();
        }

        private int portY(int node, int port) { return nodes[node][1] + portOffset(laidOut.nodes.get(node)) + port * ROW + ROW / 2; }

        private void bus(ModularGuiContext context, WidgetTheme theme, String title, String subtitle, int x, int y) {
            if (!visible(x, y, BUS_W, BUS_H)) return;
            TerminalUiStyle.CHIP_NORMAL.draw(context, x, y, BUS_W, BUS_H, theme);
            caption(context, title, x + 4, y + 1, BUS_W - 8, 13, CriticalGuiStyle.TEXT_TITLE);
            caption(context, subtitle, x + 4, y + 13, BUS_W - 8, 12, LINE);
        }

        private void box(ModularGuiContext context, WidgetTheme theme, String title, int x, int y) {
            if (!visible(x, y, BUS_W, TILE_H)) return;
            TerminalUiStyle.CHIP_NORMAL.draw(context, x, y, BUS_W, TILE_H, theme);
            caption(context, title, x + 4, y, BUS_W - 8, TILE_H, CriticalGuiStyle.TEXT_TITLE);
        }

        private void ghost(ModularGuiContext context, String title, int x, int y, int w, boolean hovered, CenterProcess process) {
            int height = process == null ? TILE_H : 23;
            if (!visible(x, y, w, height)) return;
            rect(x, y, w, height, 0xFF28333B);
            int color = hovered ? 0xFFA2C6B2 : 0xFF637B7E;
            for (int at = 0; at < w; at += 5) {
                rect(x + at, y, Math.min(3, w - at), 1, color);
                rect(x + at, y + height - 1, Math.min(3, w - at), 1, color);
            }
            for (int at = 0; at < height; at += 5) {
                rect(x, y + at, 1, Math.min(3, height - at), color);
                rect(x + w - 1, y + at, 1, Math.min(3, height - at), color);
            }
            int labelInset = 5;
            if (process != null) {
                ItemStack icon = icons[process.ordinal()];
                if (icon == null) icons[process.ordinal()] = icon = processIcon(process);
                GuiDraw.drawItem(icon, x + 5, y + 3, 10, 10, context.getCurrentDrawingZ());
                labelInset = 18;
                rect(x + 5, y + 16, w - 10, 1, 0xFF56676D);
            }
            caption(context, title, x + labelInset, y, w - labelInset - 4, TILE_H,
                hovered ? CriticalGuiStyle.TEXT_TITLE : CriticalGuiStyle.TEXT_MUTED);
        }

        private void caption(ModularGuiContext context, String value, int x, int y, int w, int h, int color) {
            line = value;
            ink = color;
            text.draw(context, x, y, w, h, null);
        }

        private void rect(int x, int y, int w, int h, int color) {
            int left = Math.max(0, x), top = Math.max(0, y);
            int right = Math.min(boardWidth(), x + w), bottom = Math.min(boardHeight(), y + h);
            if (left < right && top < bottom) GuiDraw.drawRect(left, top, right - left, bottom - top, color);
        }

        private void drawCircuits(CenterGraph graph, int width, int height, int gridStride) {
            List<int[]> boxes = new java.util.ArrayList<>();
            List<int[]> edges = new java.util.ArrayList<>();
            int inputX = busX(IN), inputY = busY(IN) + BUS_H / 2;
            int outX = busX(OUT), outY = busY(OUT) + BUS_H / 2;
            boxes.add(new int[] { inputX, inputY - BUS_H / 2, inputX + BUS_W, inputY + BUS_H / 2 });
            boxes.add(new int[] { outX, outY - BUS_H / 2, outX + BUS_W, outY + BUS_H / 2 });
            if (nodes.length > 0) edges.add(new int[] { inputX + BUS_W, inputY, nodes[0][0], nodes[0][1] + 9, LINE, 0 });
            Set<Integer> extraEntries = new LinkedHashSet<>();
            for (var entry : graph.entryRoutes()) if (entry.node != 0 && extraEntries.add(entry.node))
                edges.add(new int[] { inputX + BUS_W, inputY, nodes[entry.node][0], nodes[entry.node][1] + 9, LINE, 0 });
            if (sample != null) {
                int x = inputX + SAMPLE_X, y = busY(IN) + SAMPLE_Y;
                boxes.add(new int[] { x, y, x + 18, y + 18 });
                edges.add(new int[] { x + 18, y + 9, inputX, inputY, 0xFFE0AD71, 0 });
            }
            for (int i = 0; i < ruleIcons.length; i++) {
                int x = ruleX(i), y = ruleY(i), w = ruleWidth(i);
                boxes.add(new int[] { x, y, x + w, y + TILE_H });
                edges.add(new int[] { x + w, y + TILE_H / 2, inputX, inputY, LINE, 0 });
            }
            for (int i = 0; i < nodes.length; i++) {
                Node node = graph.nodes.get(i);
                int ports = CenterGraphSync.outlets(node);
                int x = nodes[i][0], y = nodes[i][1];
                boxes.add(new int[] { x, y, x + NODE_W, y + cardHeight(node) });
                for (int port = 0; port < ports; port++) {
                    int target = node.target(port);
                    if (target == CenterGraph.UNCONNECTED) continue;
                    int toX = target < 0 ? outX : nodes[target][0];
                    int toY = target < 0 ? outY : nodes[target][1] + 9;
                    edges.add(new int[] { x + NODE_W, portY(i, port), toX, toY,
                        port == 0 ? 0xFFE2AD68 : LINE, port == 0 ? 0 : 1 });
                }
            }
            circuits.draw(width, height, offX, offY, gridStride, BACKGROUND, GRID, edges, boxes);
        }

        private void wire(int x, int y, int endX, int endY, int color) {
            int middle = (x + endX) / 2;
            rect(Math.min(x, middle), y, Math.abs(middle - x) + 1, 1, color);
            rect(middle, Math.min(y, endY), 1, Math.abs(endY - y) + 1, color);
            rect(Math.min(middle, endX), endY, Math.abs(endX - middle) + 1, 1, color);
        }

        private int ruleAt(int x, int y) {
            for (int i = 0; i < ruleIcons.length; i++) {
                if (inside(x, y, ruleX(i), ruleY(i), ruleWidth(i), TILE_H)) return i;
            }
            return -1;
        }

        private boolean selectInput(int x, int y) {
            int index = ruleAt(x, y);
            if (index < 0) return false;
            ItemStack icon = ruleIcons[index];
            if (icon != null && editor.checking) sample = icon.copy();
            else {
                editor.inspectedRule = laidOut.rules().get(index);
                editor.showRules = true;
                editor.showSources = false;
            }
            return true;
        }

        private static String chance(int chance) {
            if (chance % 100 == 0) return (chance / 100) + "%";
            return String.format(java.util.Locale.ROOT, "%.2f%%", chance / 100.0);
        }

        private void armLink(int node, int port, CenterGraphPreview.Product product) {
            if (source == node && sourcePort == port && linkedProduct == product) {
                clearLink();
                return;
            }
            source = node;
            sourcePort = port;
            linkedProduct = product;
            if (product != null) for (CenterProcess process : PROCESSES)
                linkReceivers[process.ordinal()] = product.canEnter(process);
        }

        private void clearLink() {
            source = -1;
            linkedProduct = null;
        }

        private boolean acceptsLink(CenterProcess process) {
            return linkedProduct == null || linkReceivers[process.ordinal()];
        }

        private boolean canReceiveLink(int node) {
            if (source < 0 || node <= source) return false;
            CenterProcess process = laidOut.nodes.get(node).process;
            return acceptsLink(process) && (linkedProduct == null || editor.processReady(process));
        }

        private boolean receiveAtHeader() { return linkedProduct == null || sourcePort == 0; }

        private int receiverY(int node) {
            return nodes[node][1] + (receiveAtHeader() ? 2 : cardHeight(laidOut.nodes.get(node)) - 11);
        }

        private CenterGraphPreview.Product shownProduct(int node, int port) {
            if (trace != null) for (CenterGraphPreview.Product product : trace.products(node))
                if (product.port == port) return product;
            return null;
        }

        @Override
        public Result onMousePressed(int button) {
            if (editor.graph == null) return Result.IGNORE;
            positions(editor.graph);
            pressX = mouseX();
            pressY = mouseY();
            dx = dy = 0;
            if (button == 2) {
                panning = true;
                return Result.SUCCESS;
            }
            int hit = hit(pressX, pressY);
            int auxiliary = hit < 0 ? -1 : auxiliaryAt(hit, pressX, pressY);
            if (button == 1 && auxiliary >= 0) {
                editor.carried = null;
                applyAuxiliary(hit, auxiliary);
                return Result.SUCCESS;
            }
            if (button != 0) return Result.IGNORE;
            if (editor.carried != null) {
                acceptDrop(hit, pressX, pressY);
                return Result.SUCCESS;
            }
            if (source >= 0 && hit != source) {
                if (hit == OUT || canReceiveLink(hit) && inside(pressX, pressY, nodes[hit][0] + 2,
                    receiverY(hit), NODE_W - 4, receiveAtHeader() ? HEAD - 2 : 11)) {
                    editor.edit(CenterGraphSync.Action.TARGET, source, sourcePort * 65 + (hit == OUT ? 0 : hit + 1), "");
                    clearLink();
                    return Result.SUCCESS;
                }
                if (hit >= 0) return Result.SUCCESS;
            }
            if (editor.snapshot.writable() && onBusBorder(hit, pressX, pressY)) {
                dragBus = hit;
                clearLink();
                return Result.SUCCESS;
            }
            if (hit < 0) {
                if (hit == RULE_GHOST) {
                    editor.openSources(false);
                    return Result.SUCCESS;
                }
                if (hit == IN) {
                    editor.inspectedRule = null;
                    editor.showRules = true;
                    editor.showSources = false;
                    return Result.SUCCESS;
                }
                if (hit == MODULE_GHOST) {
                    getContext().focus(editor.processSearch);
                    return Result.SUCCESS;
                }
                if (selectInput(pressX, pressY)) return Result.SUCCESS;
                if (hit == -1) {
                    selecting = boxMode || CenterTerminalClient.control() || CenterTerminalClient.shift();
                    panning = !selecting;
                }
                clearLink();
                if (!selecting || !CenterTerminalClient.control() && !CenterTerminalClient.shift()) marked.clear();
                return Result.SUCCESS;
            }
            if (auxiliary >= 0) {
                clearLink();
                editor.selectNode(hit);
                editor.showSources = true;
                editor.showRules = false;
                return Result.SUCCESS;
            }
            int outputs = nodes[hit][1] + portOffset(editor.graph.nodes.get(hit));
            int port = (pressY - outputs) / ROW;
            editor.selectNode(hit);
            if (pressY >= outputs && port < CenterGraphSync.outlets(editor.node())) {
                editor.port = port;
                editor.readPort();
                if (editor.snapshot.writable() && !CenterGraphSync.direct(editor.node().process)) {
                    int localX = pressX - nodes[hit][0];
                    boolean clickedProduct = localX >= 36 && localX < NODE_W - 12;
                    CenterGraphPreview.Product product = clickedProduct || port == 0 ? shownProduct(hit, port) : null;
                    armLink(hit, port, product);
                }
                return Result.SUCCESS;
            }
            clearLink();
            if (CenterTerminalClient.control()) {
                if (!marked.add(hit)) marked.remove(hit);
            } else if (!marked.contains(hit)) {
                marked.clear();
                marked.add(hit);
            }
            moving = !marked.isEmpty();
            return Result.SUCCESS;
        }

        @Override
        public void onMouseDrag(int button, long time) {
            int x = mouseX(), y = mouseY();
            if (moving || selecting || dragBus != 0) {
                dx = x - pressX;
                dy = y - pressY;
            } else if (panning) {
                offX += x - pressX;
                offY += y - pressY;
                pressX = x;
                pressY = y;
            }
        }

        @Override
        public boolean onMouseRelease(int button) {
            if (editor.graph != null && editor.carried != null && button == 0) {
                positions(editor.graph);
                int x = mouseX(), y = mouseY();
                if (inside(x, y, 0, 0, boardWidth(), boardHeight())) acceptDrop(hit(x, y), x, y);
            }
            if (selecting) {
                int left = Math.min(pressX, pressX + dx), right = Math.max(pressX, pressX + dx);
                int top = Math.min(pressY, pressY + dy), bottom = Math.max(pressY, pressY + dy);
                for (int i = 0; i < nodes.length; i++) {
                    int x = nodes[i][0], y = nodes[i][1];
                    if (left < x + NODE_W && right > x && top < y + cardHeight(laidOut.nodes.get(i)) && bottom > y)
                        marked.add(i);
                }
            }
            if (moving) moveMarked(dx, dy);
            if (dragBus != 0 && (dx != 0 || dy != 0)) {
                editor.edit(CenterGraphSync.Action.MOVE_BUS, busX(dragBus) - offX, busY(dragBus) - offY,
                    dragBus == IN ? "input" : "output");
            }
            dragBus = 0;
            moving = panning = selecting = false;
            return true;
        }

        private void moveMarked(int x, int y) {
            if (marked.isEmpty() || x == 0 && y == 0) return;
            StringJoiner edits = new StringJoiner(";");
            for (int node : marked) if (node < nodes.length)
                edits.add(node + "," + (nodes[node][2] + x) + "," + (nodes[node][3] + y));
            if (edits.length() > 0) editor.edit(CenterGraphSync.Action.MOVE, editor.nodeIndex, 0, edits.toString());
        }

        private void acceptDrop(int target, int x, int y) {
            if (target >= 0) {
                Node node = editor.graph.nodes.get(target);
                int row = y - nodes[target][1] - portOffset(node);
                int auxiliary = auxiliaryAt(target, x, y);
                if (auxiliary >= 0) {
                    applyAuxiliary(target, auxiliary);
                    return;
                }
                if (row >= 0 && row / ROW < CenterGraphSync.outlets(node)
                    && !CenterGraphSync.direct(node.process)) editor.acceptSample(target, row / ROW);
            } else if (target == IN || target == RULE_GHOST || ruleAt(x, y) >= 0) editor.acceptSample(-1, 0);
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            int delta = direction == UpOrDown.UP ? 36 : -36;
            if (CenterTerminalClient.shift()) offX += delta;
            else zoomAt(direction == UpOrDown.UP ? .10 : -.10, getContext().getMouseX(), getContext().getMouseY());
            return true;
        }

        @Override
        public Result onKeyPressed(char type, int key) {
            if (key == Keyboard.KEY_ESCAPE && editor.showShortcuts) {
                editor.showShortcuts = false;
                return Result.SUCCESS;
            }
            if (type == '?') {
                editor.showShortcuts = !editor.showShortcuts;
                return Result.SUCCESS;
            }
            if (key == Keyboard.KEY_ESCAPE && (source >= 0 || dragBus != 0 || selecting || boxMode || !marked.isEmpty())) {
                clearLink();
                dragBus = 0;
                selecting = boxMode = false;
                marked.clear();
                return Result.SUCCESS;
            }
            if (key == Keyboard.KEY_HOME || key == Keyboard.KEY_F) {
                fit();
                return Result.SUCCESS;
            }
            if (editor.graph == null) return Result.IGNORE;
            if (key == Keyboard.KEY_0 || key == Keyboard.KEY_NUMPAD0) {
                resetZoom();
                return Result.SUCCESS;
            }
            if (key == Keyboard.KEY_EQUALS || key == Keyboard.KEY_ADD || key == Keyboard.KEY_MINUS || key == Keyboard.KEY_SUBTRACT) {
                zoomBy(key == Keyboard.KEY_MINUS || key == Keyboard.KEY_SUBTRACT ? -.12 : .12);
                return Result.SUCCESS;
            }
            int x = key == Keyboard.KEY_LEFT ? -1 : key == Keyboard.KEY_RIGHT ? 1 : 0;
            int y = key == Keyboard.KEY_UP ? -1 : key == Keyboard.KEY_DOWN ? 1 : 0;
            if (x != 0 || y != 0) {
                int step = CenterTerminalClient.shift() || marked.isEmpty() ? 24 : 1;
                if (marked.isEmpty()) {
                    offX -= x * step;
                    offY -= y * step;
                } else moveMarked(x * step, y * step);
                return Result.SUCCESS;
            }
            if ((key == Keyboard.KEY_DELETE || key == Keyboard.KEY_BACK) && !editor.graph.nodes.isEmpty()) {
                StringJoiner ids = new StringJoiner(",");
                for (int index : marked) ids.add(Integer.toString(index));
                editor.edit(CenterGraphSync.Action.REMOVE_NODE, editor.nodeIndex, 0, ids.toString());
                marked.clear();
                return Result.SUCCESS;
            }
            if (CenterTerminalClient.control() && (key == Keyboard.KEY_Z || key == Keyboard.KEY_Y)) {
                editor.edit(key == Keyboard.KEY_Y || CenterTerminalClient.shift()
                    ? CenterGraphSync.Action.REDO : CenterGraphSync.Action.UNDO, 0, 0, "");
                marked.clear();
                return Result.SUCCESS;
            }
            if (CenterTerminalClient.control() && key == Keyboard.KEY_A) {
                marked.clear();
                for (int i = 0; i < editor.graph.nodes.size(); i++) marked.add(i);
                return Result.SUCCESS;
            }
            if (CenterTerminalClient.control() && key == Keyboard.KEY_D) {
                StringJoiner ids = new StringJoiner(",");
                for (int index : marked) ids.add(Integer.toString(index));
                editor.edit(CenterGraphSync.Action.DUPLICATE, editor.nodeIndex, 0, ids.toString());
                return Result.SUCCESS;
            }
            if (CenterTerminalClient.control() && key == Keyboard.KEY_C && !editor.graph.nodes.isEmpty()) {
                StringJoiner ids = new StringJoiner(",");
                for (int index : marked) ids.add(Integer.toString(index));
                copied = marked.isEmpty() ? Integer.toString(editor.nodeIndex) : ids.toString();
                copiedGraph = editor.graph.id;
                return Result.SUCCESS;
            }
            if (CenterTerminalClient.control() && key == Keyboard.KEY_V && copiedGraph == editor.graph.id) {
                editor.edit(CenterGraphSync.Action.DUPLICATE, editor.nodeIndex, 0, copied);
                return Result.SUCCESS;
            }
            return Result.IGNORE;
        }
    }

    static ButtonWidget<?> button(String key, Supplier<String> caption, Runnable action) {
        return new ButtonWidget<>().name("center_graph_" + key)
            .background(TerminalUiStyle.BUTTON)
            .hoverBackground(TerminalUiStyle.HOVER)
            .overlay(compact(caption, () -> CriticalGuiStyle.TEXT_CONTROL, Alignment.CENTER))
            .onMousePressed(button -> {
                if (button != 0) return false;
                action.run();
                return true;
            });
    }

    static IDrawable compact(Supplier<String> text, IntSupplier color, Alignment alignment) {
        return compact(text, color, alignment, .64f);
    }

    private static IDrawable compact(Supplier<String> text, IntSupplier color, Alignment alignment, float minScale) {
        return new IDrawable() {

            private final TextRenderer renderer = new TextRenderer();

            @Override
            public void draw(com.cleanroommc.modularui.screen.viewport.GuiContext context, int x, int y,
                int width, int height, WidgetTheme theme) {
                String value = text.get();
                int available = Math.max(1, width - 3);
                int measured = TextRenderer.getFontRenderer().getStringWidth(value);
                float scale = Math.min(.82f, (float) available / Math.max(1, measured));
                if (scale < minScale) {
                    scale = minScale;
                    value = TextRenderer.getFontRenderer().trimStringToWidth(value, (int) (available / scale));
                }
                renderer.setColor(color.getAsInt());
                renderer.setShadow(false);
                renderer.setScale(scale);
                renderer.setAlignment(alignment, available, height);
                renderer.setHardWrapOnBorder(false);
                renderer.setPos(x + 1, y);
                renderer.draw(value);
            }
        };
    }

    static TextFieldWidget field(String key, int maxLength) {
        return new TextFieldWidget().name("center_graph_" + key)
            .background(CriticalGuiStyle.CARD)
            .setMaxLength(maxLength);
    }

    private static void setField(TextFieldWidget field, String value) {
        field.getStringValue().setStringValue(value);
        field.setText(value);
        field.markTooltipDirty();
    }

    private static ParentWidget<?> label(Supplier<String> text, int x, int y, int w, int h) {
        return CriticalGuiStyle.label(text, () -> CriticalGuiStyle.TEXT_TITLE, Alignment.CenterLeft)
            .pos(x, y)
            .size(w, h);
    }

    static String processName(CenterProcess process) {
        return tr(
            "process." + process.name()
                .toLowerCase(java.util.Locale.ROOT));
    }

    private static String outletName(Node node, int port) {
        if (node != null && port >= node.process.outletCount())
            return tr("preset.branch", port - node.process.outletCount() + 1);
        return port == 0 ? tr("primary") : tr("secondary", port);
    }

    static String tr(String key, Object... args) {
        return args.length == 0 ? StatCollector.translateToLocal(PREFIX + key)
            : StatCollector.translateToLocalFormatted(PREFIX + key, args);
    }
}
