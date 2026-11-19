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

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
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
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTUtility;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public final class CenterGraphGui extends MTEMultiBlockBaseGui<MTEMineralIntegrationCenter> {

    private static final String PANEL = "gtsr_ore_center_graph";
    private static final String PREFIX = "gtsr.ore_center.graph.";
    private static final int TAB_WIDTH = 64, TAB_HEIGHT = 15;
    private static final int UNRESTRICTED = 0, ALLOW = 1, DENY = 2;
    private static final int TEXT_LIMIT = 8192;
    private static final CenterProcess[] PROCESSES = CenterProcess.values();
    private static final java.util.regex.Pattern MINERAL = java.util.regex.Pattern.compile(
        "(?:ore|variantOre|rawOre|crushedPurified|crushed|dustImpure|dustPure)([A-Z][A-Za-z0-9_]*)");

    private final MTEMineralIntegrationCenter machine;

    public CenterGraphGui(MTEMineralIntegrationCenter machine) {
        super(machine);
        this.machine = machine;
    }

    @Override
    public ModularPanel build(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        ModularPanel panel = new ModularPanel(PANEL).sizeRel(1f)
            .background(TerminalUiStyle.CLUSTER_PANEL);
        CenterGraphSync sync = new CenterGraphSync(machine, manager, data.getPlayer());
        manager.syncValue("ore_center.graph", sync.allowC2S());
        Editor editor = new Editor(sync);
        CenterTerminalPages pages = new CenterTerminalPages(sync,
            id -> editor.graphId == id ? editor.canvas.sample : null);
        var machinePanel = manager
            .syncedPanel("ore_center.base", true, (childManager, handler) -> super.build(data, childManager, settings));
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
        for (int i = 0; i < 4; i++) {
            int tier = i;
            header.child(button("tier_" + i, () -> tr("terminal.tier." + tier), machinePanel::openPanel)
                .right(30 + (3 - i) * 20).top(3).size(19, 13)
                .background(new DynamicDrawable(() -> sync.terminal().hasKey("Tier")
                    && sync.terminal().getInteger("Tier") == tier ? TerminalUiStyle.CHIP_ACTIVE : TerminalUiStyle.CHIP_NORMAL))
                .tooltipBuilder(t -> {
                    t.add(tr("terminal.tier." + tier));
                    t.add(tr("machine"));
                }));
        }
        header.child(button("exit", () -> tr("exit"), () -> panel.getScreen().close()).right(1).top(3).size(26, 13));
        panel.child(header);
        List<ParentWidget<?>> bodies = Arrays.asList(pages.assembly(), editor.content(), pages.statistics(), pages.calculation());
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
        private int nodeIndex, port, filterMode, target, processIndex;
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
        private boolean showSources, chooseOreTerm, showRules;
        private final Tabs tabs = new Tabs(this);
        private final Canvas canvas = new Canvas(this);

        private Editor(CenterGraphSync sync) {
            this.sync = sync;
        }

        private ParentWidget<?> content() {
            ParentWidget<?> contents = new ParentWidget<>().name("center_routes");
            ParentWidget<?> sourceBar = CenterTerminalPages.card("center_graph_toolbar_source")
                .left(0).right(0).top(0).height(13);
            sourceBar.child(button("source", () -> tr(showSources ? "source.return" : "source"), () -> {
                showSources = !showSources;
                showRules = false;
                carried = null;
                contents.scheduleResize();
            }).right(2).top(0).size(68, 13));
            contents.child(sourceBar);
            ParentWidget<?> toolbar = CenterTerminalPages.card("center_graph_documents")
                .left(0).right(0).top(14).height(17);
            toolbar.child(tabs.name("center_graph_tabs").left(2).right(406).top(1).height(TAB_HEIGHT)
                .tooltipBuilder(t -> t.add(IKey.lang(PREFIX + "tabs.help"))));
            toolbar.child(button("add", () -> "+ " + tr("graph"), () -> edit(CenterGraphSync.Action.ADD, 0, 0, ""))
                .right(358).top(1).size(46, 14));
            toolbar.child(name.right(278).top(2).size(77, 12));
            toolbar.child(button("save_name", () -> tr("rename"),
                () -> edit(CenterGraphSync.Action.RENAME, 0, 0, name.getText().trim()))
                .right(248).top(1).size(27, 14));
            toolbar.child(button("add_mineral", () -> tr("rule.item"), () -> {
                chooseOreTerm = false;
                showSources = true;
                showRules = false;
            }).right(206).top(1).size(40, 14));
            toolbar.child(button("add_ore_term", () -> tr("rule.add"), () -> {
                chooseOreTerm = true;
                showSources = true;
                showRules = false;
            }).right(164).top(1).size(40, 14));
            toolbar.child(button("rules_toggle", () -> tr("terminal.entry",
                graph == null ? 0 : graph.rules().size()), () -> {
                    showRules = !showRules;
                    showSources = false;
                }).right(116).top(1).size(46, 14));
            toolbar.child(button("enabled", this::enabledText, () -> edit(CenterGraphSync.Action.TOGGLE, 0, 0, ""))
                .right(80).top(1).size(34, 14));
            toolbar.child(button("delete", () -> tr("delete"), () -> edit(CenterGraphSync.Action.DELETE, 0, 0, ""))
                .right(39).top(1).size(39, 14));
            toolbar.child(CenterTerminalPages.label(() -> snapshot.graphs().size() + "/" + snapshot.capacity())
                .right(1).top(1).size(34, 14));
            contents.child(toolbar);

            contents.child(canvas.name("center_graph_canvas").left(93).right(146).top(32).bottom(18));
            ParentWidget<?> tools = CenterTerminalPages.card("center_process_toolbox").left(0).top(32).bottom(18).width(91);
            tools.child(label(() -> tr("terminal.processes"), 3, 1, 81, 13));
            tools.child(processSearch.pos(4, 16).size(83, 13)
                .tooltipBuilder(t -> t.add(tr("terminal.find_process"))));
            tools.child(new ProcessList(this).left(3).right(3).top(32).bottom(2));
            contents.child(tools);

            ParentWidget<?> inspector = CenterTerminalPages.card("center_graph_properties").right(0).top(32)
                .bottom(18).width(144).setEnabledIf(w -> !showSources && !showRules);
            inspector.child(label(this::nodeText, 2, 3, 104, 15));
            inspector.child(button("focus_node", () -> tr("terminal.locate"), () -> canvas.focus(nodeIndex))
                .right(3).top(3).size(29, 13));
            inspector.child(button("copy_node", () -> tr("terminal.copy"),
                () -> edit(CenterGraphSync.Action.DUPLICATE, nodeIndex, 0, ""))
                .pos(4, 19).size(42, 14));
            inspector.child(button("remove_process", () -> tr("remove_process"),
                () -> edit(CenterGraphSync.Action.REMOVE_NODE, nodeIndex, 0, ""))
                .right(3).top(19).size(56, 14));
            inspector.child(new OutletList(this).left(4).right(4).top(39).height(126));

            inspector.child(button("filter_mode", this::modeText, () -> filterMode = (filterMode + 1) % 3)
                .left(4).top(167).size(52, 13));
            inspector.child(button("save_filter", () -> tr("save_filter"),
                () -> edit(CenterGraphSync.Action.FILTER, nodeIndex, port * 3 + filterMode, filter.getText()))
                .right(4).top(167).size(63, 13));
            inspector.child(filter.left(4).right(4).top(183).height(14)
                .tooltipBuilder(t -> t.add(IKey.lang(PREFIX + "filter.help"))));
            inspector.child(button("target", this::targetText, this::nextTarget).left(4).top(201).size(93, 13));
            inspector.child(button("save_target", () -> tr("save"), () -> edit(CenterGraphSync.Action.TARGET,
                nodeIndex, port * 65 + Math.max(-1, target) + 1, "")).right(4).top(201).size(32, 13));
            inspector.child(label(() -> "NC", 5, 221, 24, 14).setEnabledIf(w -> hasCircuit()));
            inspector.child(circuit.pos(31, 221).size(25, 13).setEnabledIf(w -> hasCircuit())
                .tooltipBuilder(t -> t.add(tr("circuit.help"))));
            inspector.child(button("save_circuit", () -> tr("save"), () -> {
                try {
                    edit(CenterGraphSync.Action.CIRCUIT, nodeIndex, Integer.parseInt(circuit.getText().trim()), "");
                } catch (NumberFormatException invalid) {
                    sync.setStatus("status.invalid");
                }
            }).right(4).top(221).size(35, 13).setEnabledIf(w -> hasCircuit()));
            contents.child(inspector);

            ParentWidget<?> entries = CenterTerminalPages.card("center_graph_entries").right(0).top(32)
                .bottom(18).width(144).setEnabledIf(w -> showRules && !showSources);
            entries.child(label(() -> tr("rules"), 3, 3, 133, 15));
            entries.child(rules.left(4).right(4).top(21).height(16)
                .tooltipBuilder(t -> t.add(IKey.lang(PREFIX + "rules.help"))));
            entries.child(button("save_rules", () -> tr("save_rules"), () -> {
                edit(CenterGraphSync.Action.RULES, 0, 0, rules.getText());
                showRules = false;
            }).left(4).right(4).top(41).height(15));
            entries.child(label(() -> tr("priority"), 3, 66, 133, 14));
            entries.child(priority.left(4).top(83).size(78, 16));
            entries.child(button("save_priority", () -> tr("save"), this::savePriority)
                .right(4).top(83).size(52, 16));
            contents.child(entries);

            ParentWidget<?> sourcePanel = CenterTerminalPages.card("center_graph_source").right(0).top(32).bottom(18)
                .width(144).setEnabledIf(w -> showSources);
            sourcePanel.child(label(() -> tr("source.pack"), 3, 3, 133, 14));
            sourcePanel.child(sourceSearch.left(4).right(4).top(85).height(14)
                .tooltipBuilder(t -> t.add("NEI")));
            sourcePanel.child(sourceItems.left(4).right(4).top(19).bottom(17));
            sourcePanel.child(CenterTerminalPages.label(() -> carried == null ? tr("rule.drop") : carried.getDisplayName())
                .left(2).right(2).bottom(2).height(13));
            contents.child(sourcePanel);

            ParentWidget<?> status = CenterTerminalPages.card("center_graph_status")
                .left(0).right(0).bottom(1).height(15);
            status.child(CenterTerminalPages.label(() -> canvas.sample == null
                ? tr("terminal.sample") : canvas.sample.getDisplayName()).left(1).right(245).top(0).height(15));
            status.child(CriticalGuiStyle.label(() -> tr(graph != null && graph.runnable()
                ? "terminal.connected" : "terminal.open_outlets"), () -> 0xFF9BC6AB, Alignment.CENTER)
                .right(191).top(0).size(51, 15));
            status.child(button("wire_outputs", () -> tr("wire_outputs"),
                () -> edit(CenterGraphSync.Action.OUTLET_ALL, 0, 0, ""))
                .right(140).top(0).size(49, 15));
            status.child(button("fit", () -> tr("terminal.fit"), canvas::fit)
                .right(111).top(0).size(27, 15));
            status.child(button("zoom_out", () -> "−", () -> canvas.zoomBy(-0.12))
                .right(95).top(0).size(14, 15));
            status.child(button("zoom_reset", () -> (int) Math.round(canvas.zoom * 100) + "%", canvas::fit)
                .right(63).top(0).size(30, 15));
            status.child(button("zoom_in", () -> "+", () -> canvas.zoomBy(0.12))
                .right(47).top(0).size(14, 15));
            status.child(button("shortcuts", () -> tr("terminal.shortcuts"), () -> {})
                .right(1).top(0).size(44, 15).tooltipBuilder(t -> t.add(tr("canvas.help"))));
            contents.child(status);
            return contents;
        }

        private void savePriority() {
            try {
                edit(CenterGraphSync.Action.PRIORITY, Integer.parseInt(priority.getText().trim()), 0, "");
            } catch (NumberFormatException invalid) {
                sync.setStatus("priority.invalid");
            }
        }

        private boolean hasCircuit() {
            Node current = node();
            return current != null && (current.process == CenterProcess.REACTOR
                || current.process == CenterProcess.ELECTROLYZER || current.process == CenterProcess.FURNACE);
        }

        private void update() {
            CenterGraphSync.Snapshot next = sync.snapshot();
            if (next == snapshot) return;
            boolean sameRevision = next.revision() == snapshot.revision();
            snapshot = next;
            if (sameRevision) return;
            refresh();
        }

        private void refresh() {
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
            setField(name, graph == null ? "" : graph.name);
            setField(priority, graph == null ? "" : Integer.toString(graph.priority));
            setField(rules, graph == null ? "" : String.join(";", graph.rules()));
            nodeIndex = graph == null || graph.nodes.isEmpty() ? 0 : Math.min(nodeIndex, graph.nodes.size() - 1);
            Node current = node();
            if (current == null || port >= CenterGraphSync.outlets(current.process)) port = 0;
            readPort();
        }

        private void selectGraph(long id) {
            if (id == graphId) return;
            graphId = id;
            nodeIndex = port = 0;
            canvas.reset();
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
            if (action != CenterGraphSync.Action.ADD && graph == null) return;
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
            return outletName(port);
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
                canvas.sample = carried.copy();
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
        ItemList icon = switch (process) {
            case CRUSHER, HAMMER -> ItemList.Machine_LV_Macerator;
            case WASHER, SIMPLE_WASHER, CHEMICAL_BATH -> ItemList.Machine_LV_OreWasher;
            case SIFTER -> ItemList.Machine_LV_Sifter;
            case CENTRIFUGE, SEPARATION -> ItemList.Machine_LV_Centrifuge;
            case ELECTROLYZER -> ItemList.Machine_LV_Electrolyzer;
            case THERMAL_CENTRIFUGE -> ItemList.Machine_LV_ThermalCentrifuge;
            case SEPARATOR -> ItemList.Machine_LV_ElectromagneticSeparator;
            case EXTRACTOR -> ItemList.Machine_LV_Extractor;
            case FLUID_EXTRACTOR -> ItemList.Machine_LV_FluidExtractor;
            case REACTOR -> ItemList.Machine_Multi_LargeChemicalReactor;
            case FURNACE -> ItemList.Machine_Multi_BlastFurnace;
            case SMELTING -> ItemList.Machine_LV_E_Furnace;
            default -> null;
        };
        return icon == null || !icon.hasBeenSet() ? CenterModuleItems.stack(process.module, 1) : icon.get(1);
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
                if (index >= 0 && index < rows.size()) t.add(processName(rows.get(index)));
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
                (hover ? TerminalUiStyle.HOVER : TerminalUiStyle.BUTTON)
                    .draw(context, 0, y, getArea().width, 15, theme.getTheme());
                ItemStack icon = icons[process.ordinal()];
                if (icon == null) icons[process.ordinal()] = icon = processIcon(process);
                GuiDraw.drawItem(icon, 5, y + 3, 10, 10, context.getCurrentDrawingZ());
                name = processName(process);
                text.draw(context, 15, y, getArea().width - 18, 15, null);
            }
            markTooltipDirty();
        }

        @Override
        public Result onMousePressed(int mouseButton) {
            int index = first + getContext().getMouseY() / 16;
            if (mouseButton != 0 || index < 0 || index >= rows.size()) return Result.IGNORE;
            editor.edit(CenterGraphSync.Action.FREE_NODE, 0, rows.get(index).ordinal(), "");
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
            int outlets = CenterGraphSync.outlets(node.process), width = getArea().width;
            int visible = getArea().height / 29;
            first = Math.min(first, Math.max(0, outlets - visible));
            for (int i = first; i < Math.min(first + visible, outlets); i++) {
                int y = (i - first) * 29;
                TerminalUiStyle.CHIP_NORMAL.draw(context, 0, y, width, 27, theme.getTheme());
                if (editor.port == i) GuiDraw.drawRect(0, y, 1, 27, 0xFFE1AF71);
                caption = CenterGraphSync.direct(node.process) ? tr("direct") : outletName(i);
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
            if (mouseButton != 0 || node == null || port < 0 || port >= CenterGraphSync.outlets(node.process))
                return Result.IGNORE;
            editor.port = port;
            editor.readPort();
            if (!CenterGraphSync.direct(node.process) && getContext().getMouseX() > getArea().width - 28) {
                editor.canvas.source = editor.nodeIndex;
                editor.canvas.sourcePort = port;
            }
            return Result.SUCCESS;
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            first = Math.max(0, first + (direction == UpOrDown.UP ? -1 : 1));
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

        private static final int NODE_W = 92, HEAD = 14, ROW = 13, BUS_W = 69;
        private static final int GRID = 0xFF323C42, LINE = 0xFF95C4AA, BACKGROUND = 0xFF20262C;
        private static final int OUT = -2, IN = -3;
        private final Editor editor;
        private final IDrawable text;
        private final IDrawable productText;
        private final Set<Integer> marked = new LinkedHashSet<>();
        private int[][] nodes = new int[0][];
        private String copied = "";
        private long copiedGraph = -1;
        private int offX, offY, pressX, pressY, dx, dy, source = -1, sourcePort;
        private boolean moving, panning;
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
                if (editor.graph == null || trace == null) return;
                int y = getContext().getMouseY(), index = hit(getContext().getMouseX(), y);
                if (index < 0 || y < nodes[index][1] + portOffset(editor.graph.nodes.get(index))) return;
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
            source = -1;
            sample = null;
            moving = panning = false;
        }

        private void fit() {
            offX = offY = 0;
            if (editor.graph == null || editor.graph.nodes.size() < 2) zoom = 1;
            else zoom = Math.max(.45, Math.min(1, (getArea().width - 163) / 338.0));
        }

        private void zoomBy(double amount) {
            zoom = Math.max(.42, Math.min(1.65, zoom + amount));
        }

        private void focus(int node) {
            if (node < 0 || node >= nodes.length) return;
            offX += getArea().width / 2 - nodes[node][0] - NODE_W / 2;
            offY += getArea().height / 2 - nodes[node][1] - HEAD / 2;
        }

        private void positions(CenterGraph graph) {
            int count = graph.nodes.size();
            int[] depth = new int[count], rows = new int[count];
            if (nodes.length != count) nodes = new int[count][4];
            int top = Math.max(24, getArea().height / 2 - 91);
            for (int i = 0; i < count; i++) {
                Node node = graph.nodes.get(i);
                int layer = depth[i], lan = rows[layer]++;
                int x = node.x == CenterGraph.AUTO_POSITION ? 155 + layer * 116 : node.x;
                int y = node.y == CenterGraph.AUTO_POSITION ? top + lan * 91 : node.y;
                nodes[i][0] = (int) Math.round(x * zoom) + offX + (moving && marked.contains(i) ? dx : 0);
                nodes[i][1] = (int) Math.round(y * zoom) + offY + (moving && marked.contains(i) ? dy : 0);
                nodes[i][2] = x;
                nodes[i][3] = y;
                for (int target : node.targets()) if (target >= 0) depth[target] = Math.max(depth[target], layer + 1);
            }
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            editor.update();
            int width = getArea().width, height = getArea().height;
            rect(0, 0, width, height, BACKGROUND);
            for (int x = Math.floorMod(offX, 10); x < width; x += 10) rect(x, 0, 1, height, GRID);
            for (int y = Math.floorMod(offY, 10); y < height; y += 10) rect(0, y, width, 1, GRID);
            CenterGraph graph = editor.graph;
            if (graph == null) {
                caption(context, tr("empty"), Math.max(2, width / 2 - 82), height / 2, 164, 18,
                    CriticalGuiStyle.TEXT_MUTED);
                return;
            }
            positions(graph);
            int busY = height / 2, outX = width - BUS_W - 5;
            int inputX = Math.min(100, Math.max(27, width / 7));
            if (nodes.length > 0) wire(inputX + BUS_W, busY, nodes[0][0], nodes[0][1] + 9, LINE);
            for (int i = 0; i < nodes.length; i++) {
                Node node = graph.nodes.get(i);
                for (int port = 0; port < CenterGraphSync.outlets(node.process); port++) {
                    int target = node.target(port);
                    if (target == CenterGraph.UNCONNECTED) continue;
                    int x = target < 0 ? outX : nodes[target][0];
                    int y = target < 0 ? busY : nodes[target][1] + 9;
                    boolean main = port == 0;
                    wire(nodes[i][0] + NODE_W, portY(i, port), x, y, main ? 0xFFE2AD68 : LINE, !main);
                }
            }
            if (source >= 0 && source < nodes.length) wire(nodes[source][0] + NODE_W, portY(source, sourcePort),
                context.getMouseX(), context.getMouseY(), 0xFFDFC381);
            box(context, theme.getTheme(), tr("input"), inputX, busY - 8);
            if (sample != null && busY >= 29) {
                int sampleX = Math.max(7, inputX - 55);
                rect(sampleX - 1, busY - 18, 19, 19, 0xFFA8C6AD);
                rect(sampleX, busY - 17, 17, 17, 0xFF32383F);
                GuiDraw.drawItem(sample, sampleX + 1, busY - 16, 15, 15, context.getCurrentDrawingZ());
                wire(sampleX + 18, busY - 9, inputX, busY, 0xFFE0AD71);
            }
            box(context, theme.getTheme(), tr("output"), outX, busY - 8);
            List<String> rules = graph.rules();
            if (rules.isEmpty()) rules = Collections.singletonList(tr("fallback"));
            for (int i = 0; i < Math.min(8, rules.size()); i++) {
                int y = busY - 11 + i * 17;
                if (sample != null) y += 17;
                if (y < 0) continue;
                int at = Math.max(1, inputX - BUS_W - 9);
                box(context, theme.getTheme(), rules.get(i), at, y);
                wire(at + BUS_W, y + 8, inputX, busY, LINE);
            }
            int hover = hit(context.getMouseX(), context.getMouseY());
            if (graph.nodes.isEmpty()) {
                box(context, theme.getTheme(), tr("add_process"), Math.max(2, width / 2 - 43), busY - 7);
            }
            for (int i = 0; i < nodes.length; i++) {
                Node node = graph.nodes.get(i);
                int x = nodes[i][0], y = nodes[i][1];
                int slots = CenterGraphSync.outlets(node.process), h = portOffset(node) + slots * ROW + 11;
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
                    caption(context, processName(node.process), x + 13, y + 1, NODE_W - (i == 0 ? 29 : 15), HEAD,
                        CriticalGuiStyle.TEXT_TITLE);
                    if (i == 0) caption(context, tr("terminal.inlet"), x + NODE_W - 18, y + 1, 18, HEAD, 0xFFA6C9B6);
                    drawAuxiliaries(context, theme.getTheme(), node, x, y);
                }
                rect(x - 2, y + 8, 4, 4, LINE);
                for (int port = 0; port < slots; port++) {
                    int row = y + portOffset(node) + port * ROW;
                    rect(x + 2, row, NODE_W - 4, ROW - 1, 0xFF40464F);
                    boolean direct = CenterGraphSync.direct(node.process);
                    int color = node.target(port) == CenterGraph.UNCONNECTED ? 0xFFDC9360
                        : port == 0 && !direct ? 0xFFE4AF6D : 0xFF9DCAB9;
                    String label = direct ? tr("direct") : outletName(port);
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
                        caption(context, tr("terminal.recipe", first.item == null
                            ? first.fluid.getLocalizedName() : first.item.getDisplayName()), x + 2, footer,
                            NODE_W - 3, 10, 0xFFDCCAA9);
                    } else caption(context, tr("terminal.no_sample"), x + 2, footer, NODE_W - 3, 10, 0xFFC1C7BF);
                }
                if (hover == i && context.getMouseY() < y + HEAD && !moving)
                    caption(context, "✥", Math.min(width - 8, context.getMouseX() + 5),
                        Math.min(height - 10, context.getMouseY() + 5), 8, 10, 0xFFF3D3A2);
            }
            if (trace != null) markTooltipDirty();
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
            for (int i = nodes.length - 1; i >= 0; i--) {
                int maxY = nodes[i][1] + portOffset(editor.graph.nodes.get(i)) + CenterGraphSync.outlets(editor.graph.nodes.get(i).process) * ROW + 11;
                if (x >= nodes[i][0] && x <= nodes[i][0] + NODE_W + 3 && y >= nodes[i][1] && y < maxY) return i;
            }
            if (Math.abs(y - getArea().height / 2) <= 11) {
                if (x >= getArea().width - BUS_W - 5) return OUT;
                if (x <= Math.max(27, getArea().width / 7) + BUS_W) return IN;
            }
            return -1;
        }

        private int portY(int node, int port) { return nodes[node][1] + portOffset(editor.graph.nodes.get(node)) + port * ROW + ROW / 2; }

        private void box(ModularGuiContext context, WidgetTheme theme, String title, int x, int y) {
            if (x < 0 || y < 0 || x + BUS_W > getArea().width || y + 16 > getArea().height) return;
            TerminalUiStyle.CHIP_NORMAL.draw(context, x, y, BUS_W, 16, theme);
            caption(context, title, x + 4, y, BUS_W - 8, 16, CriticalGuiStyle.TEXT_TITLE);
        }

        private void caption(ModularGuiContext context, String value, int x, int y, int w, int h, int color) {
            line = value;
            ink = color;
            text.draw(context, x, y, w, h, null);
        }

        private void rect(int x, int y, int w, int h, int color) {
            int left = Math.max(0, x), top = Math.max(0, y);
            int right = Math.min(getArea().width, x + w), bottom = Math.min(getArea().height, y + h);
            if (left < right && top < bottom) GuiDraw.drawRect(left, top, right - left, bottom - top, color);
        }

        private void wire(int x, int y, int endX, int endY, int color) {
            wire(x, y, endX, endY, color, false);
        }

        private void wire(int x, int y, int endX, int endY, int color, boolean dotted) {
            int middle = (x + endX) / 2;
            line(x, y, middle, y, color, dotted);
            line(middle, y, middle, endY, color, dotted);
            line(middle, endY, endX, endY, color, dotted);
        }

        private void line(int x, int y, int endX, int endY, int color, boolean dotted) {
            int left = Math.min(x, endX), top = Math.min(y, endY);
            int length = Math.abs(endX - x) + Math.abs(endY - y) + 1;
            if (!dotted) {
                rect(left, top, Math.abs(endX - x) + 1, Math.abs(endY - y) + 1, color);
                return;
            }
            boolean horizontal = y == endY;
            int cross = horizontal ? top : left;
            if (cross < 0 || cross >= (horizontal ? getArea().height : getArea().width)) return;
            int start = horizontal ? left : top;
            int bound = horizontal ? getArea().width : getArea().height;
            int first = Math.max(0, -start) / 5 * 5;
            int last = Math.min(length, bound - start);
            for (int at = first; at < last; at += 5) {
                int dash = Math.min(3, last - at);
                if (horizontal) rect(left + at, top, dash, 1, color);
                else rect(left, top + at, 1, dash, color);
            }
        }

        private boolean selectInput(int x, int y) {
            int busX = Math.min(100, Math.max(27, getArea().width / 7));
            int left = Math.max(1, busX - BUS_W - 9);
            int top = getArea().height / 2 - 11 + (sample == null ? 0 : 17);
            List<String> rules = editor.graph.rules();
            if (x < left || x >= left + BUS_W || y < top) return false;
            int index = (y - top) / 17;
            if (index >= Math.min(8, rules.size())) return false;
            String rule = rules.get(index);
            if (OreDictionary.doesOreNameExist(rule)) {
                for (ItemStack candidate : OreDictionary.getOres(rule, false)) {
                    if (candidate != null) {
                        sample = candidate.copy();
                        return true;
                    }
                }
            }
            editor.showRules = true;
            editor.showSources = false;
            return true;
        }

        private static String chance(int chance) {
            if (chance % 100 == 0) return (chance / 100) + "%";
            return String.format(java.util.Locale.ROOT, "%.2f%%", chance / 100.0);
        }

        @Override
        public Result onMousePressed(int button) {
            if (editor.graph == null) return Result.IGNORE;
            pressX = getContext().getMouseX();
            pressY = getContext().getMouseY();
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
                acceptDrop(hit, pressY);
                return Result.SUCCESS;
            }
            if (source >= 0 && (hit >= 0 || hit == OUT) && hit != source) {
                editor.edit(CenterGraphSync.Action.TARGET, source, sourcePort * 65 + (hit == OUT ? 0 : hit + 1), "");
                source = -1;
                return Result.SUCCESS;
            }
            if (hit < 0) {
                if (selectInput(pressX, pressY)) return Result.SUCCESS;
                if (hit == -1) panning = true;
                source = -1;
                marked.clear();
                return Result.SUCCESS;
            }
            source = -1;
            if (auxiliary >= 0) {
                editor.selectNode(hit);
                editor.showSources = true;
                editor.showRules = false;
                return Result.SUCCESS;
            }
            int outputs = nodes[hit][1] + portOffset(editor.graph.nodes.get(hit));
            int port = (pressY - outputs) / ROW;
            editor.selectNode(hit);
            if (pressY >= outputs && port < CenterGraphSync.outlets(editor.node().process)) {
                editor.port = port;
                editor.readPort();
                if (pressX >= nodes[hit][0] + NODE_W - 7 && !CenterGraphSync.direct(editor.node().process)) {
                    source = hit;
                    sourcePort = port;
                }
                return Result.SUCCESS;
            }
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
            int x = getContext().getMouseX(), y = getContext().getMouseY();
            if (moving) {
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
                int x = getContext().getMouseX(), y = getContext().getMouseY();
                if (x >= 0 && y >= 0 && x < getArea().width && y < getArea().height) acceptDrop(hit(x, y), y);
            }
            if (moving && (dx != 0 || dy != 0)) {
                StringJoiner edits = new StringJoiner(";");
                for (int node : marked) if (node < nodes.length)
                    edits.add(node + "," + (nodes[node][2] + (int) Math.round(dx / zoom)) + "," + (nodes[node][3] + (int) Math.round(dy / zoom)));
                editor.edit(CenterGraphSync.Action.MOVE, editor.nodeIndex, 0, edits.toString());
            }
            moving = panning = false;
            return true;
        }

        private void acceptDrop(int target, int y) {
            if (target >= 0) {
                Node node = editor.graph.nodes.get(target);
                int row = y - nodes[target][1] - portOffset(node);
                int auxiliary = auxiliaryAt(target, getContext().getMouseX(), y);
                if (auxiliary >= 0) {
                    applyAuxiliary(target, auxiliary);
                    return;
                }
                if (row >= 0 && row / ROW < CenterGraphSync.outlets(node.process)
                    && !CenterGraphSync.direct(node.process)) editor.acceptSample(target, row / ROW);
            } else if (getContext().getMouseX() <= Math.max(27, getArea().width / 7) + BUS_W)
                editor.acceptSample(-1, 0);
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            int delta = direction == UpOrDown.UP ? 36 : -36;
            if (CenterTerminalClient.shift()) offX += delta;
            else zoomBy(direction == UpOrDown.UP ? .10 : -.10);
            return true;
        }

        @Override
        public Result onKeyPressed(char type, int key) {
            if (key == Keyboard.KEY_ESCAPE && source >= 0) {
                source = -1;
                return Result.SUCCESS;
            }
            if (key == Keyboard.KEY_HOME) {
                fit();
                return Result.SUCCESS;
            }
            if (editor.graph == null) return Result.IGNORE;
            if (key == Keyboard.KEY_DELETE) {
                StringJoiner ids = new StringJoiner(",");
                for (int index : marked) ids.add(Integer.toString(index));
                editor.edit(CenterGraphSync.Action.REMOVE_NODE, editor.nodeIndex, 0, ids.toString());
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

    private static String outletName(int port) {
        return port == 0 ? tr("primary") : tr("secondary", port);
    }

    static String tr(String key, Object... args) {
        return args.length == 0 ? StatCollector.translateToLocal(PREFIX + key)
            : StatCollector.translateToLocalFormatted(PREFIX + key, args);
    }
}
