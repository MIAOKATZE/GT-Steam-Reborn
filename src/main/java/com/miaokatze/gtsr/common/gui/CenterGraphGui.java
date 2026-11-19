// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.gui;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
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
import com.miaokatze.gtsr.common.gui.mui2.TerminalViewport;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph.Node;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph.OutletFilter;
import com.miaokatze.gtsr.common.machine.orecenter.CenterProcess;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public final class CenterGraphGui extends MTEMultiBlockBaseGui<MTEMineralIntegrationCenter> {

    private static final String PANEL = "gtsr_ore_center_graph";
    private static final String PREFIX = "gtsr.ore_center.graph.";
    private static final int WIDTH = 592, HEIGHT = 321;
    private static final int CANVAS_WIDTH = 379, CANVAS_HEIGHT = 232;
    private static final int TAB_WIDTH = 64, TAB_HEIGHT = 16, TAB_COLUMNS = 9;
    private static final int UNRESTRICTED = 0, ALLOW = 1, DENY = 2;
    private static final int TEXT_LIMIT = 8192;
    private static final CenterProcess[] PROCESSES = CenterProcess.values();

    private final MTEMineralIntegrationCenter machine;

    public CenterGraphGui(MTEMineralIntegrationCenter machine) {
        super(machine);
        this.machine = machine;
    }

    @Override
    public ModularPanel build(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        ModularPanel panel = new ModularPanel(PANEL).sizeRel(1f)
            .background(CriticalGuiStyle.PANEL);
        CenterGraphSync sync = new CenterGraphSync(machine, manager, data.getPlayer());
        manager.syncValue("ore_center.graph", sync.allowC2S());
        Editor editor = new Editor(sync);
        var machinePanel = manager
            .syncedPanel("ore_center.base", true, (childManager, handler) -> super.build(data, childManager, settings));
        ParentWidget<?> content = editor.content();
        content.child(
            button("machine", () -> tr("machine"), machinePanel::openPanel).pos(464, 6)
                .size(120, 16));
        return panel.child(
            TerminalViewport.wrapExact(PANEL + "_viewport", content, WIDTH, HEIGHT)
                .sizeRel(1f));
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
        private final Tabs tabs = new Tabs(this);
        private final Canvas canvas = new Canvas(this);

        private Editor(CenterGraphSync sync) {
            this.sync = sync;
        }

        private ParentWidget<?> content() {
            ParentWidget<?> contents = new ParentWidget<>().size(WIDTH, HEIGHT);
            contents.child(label(() -> tr("title"), 8, 7, 420, 13));
            contents.child(
                tabs.name("center_graph_tabs")
                    .pos(8, 26)
                    .size(TAB_WIDTH * TAB_COLUMNS, 2 * TAB_HEIGHT)
                    .tooltipBuilder(t -> t.add(IKey.lang(PREFIX + "tabs.help"))));
            contents.child(
                button("add", () -> "+ " + tr("graph"), () -> edit(CenterGraphSync.Action.ADD, 0, 0, "")).pos(8, 63)
                    .size(71, 16));
            contents.child(
                button("enabled", this::enabledText, () -> edit(CenterGraphSync.Action.TOGGLE, 0, 0, "")).pos(82, 63)
                    .size(72, 16));
            contents.child(label(() -> tr("priority"), 157, 64, 54, 14));
            contents.child(
                priority.pos(212, 64)
                    .size(39, 14));
            contents.child(button("save_priority", () -> tr("save"), () -> {
                try {
                    edit(
                        CenterGraphSync.Action.PRIORITY,
                        Integer.parseInt(
                            priority.getText()
                                .trim()),
                        0,
                        "");
                } catch (NumberFormatException invalid) {
                    sync.setStatus("priority.invalid");
                }
            }).pos(254, 63)
                .size(40, 16));
            contents.child(
                name.pos(302, 64)
                    .size(103, 14));
            contents.child(
                button(
                    "save_name",
                    () -> tr("rename"),
                    () -> edit(
                        CenterGraphSync.Action.RENAME,
                        0,
                        0,
                        name.getText()
                            .trim())).pos(408, 63)
                                .size(55, 16));
            contents.child(
                button("delete", () -> tr("delete"), () -> edit(CenterGraphSync.Action.DELETE, 0, 0, "")).pos(467, 63)
                    .size(65, 16));
            contents.child(
                label(
                    () -> snapshot.graphs()
                        .size() + " / "
                        + snapshot.capacity(),
                    535,
                    64,
                    48,
                    14));

            contents.child(
                canvas.name("center_graph_canvas")
                    .pos(8, 83)
                    .size(CANVAS_WIDTH, CANVAS_HEIGHT)
                    .tooltipBuilder(t -> t.add(IKey.lang(PREFIX + "canvas.help"))));
            ParentWidget<?> inspector = new ParentWidget<>().background(CriticalGuiStyle.CARD)
                .pos(391, 83)
                .size(193, CANVAS_HEIGHT);
            contents.child(inspector);
            inspector.child(label(() -> tr("rules"), 5, 4, 183, 11));
            inspector.child(
                rules.pos(6, 16)
                    .size(181, 15)
                    .tooltipBuilder(t -> t.add(IKey.lang(PREFIX + "rules.help"))));
            inspector.child(
                button(
                    "save_rules",
                    () -> tr("save_rules"),
                    () -> edit(CenterGraphSync.Action.RULES, 0, 0, rules.getText())).pos(6, 34)
                        .size(181, 15));
            inspector.child(label(() -> tr("outlet"), 5, 54, 183, 11));
            inspector.child(
                button(
                    "select_node",
                    this::nodeText,
                    () -> { if (graph != null) selectNode((nodeIndex + 1) % graph.nodes.size()); }).pos(6, 68)
                        .size(181, 15));
            inspector.child(button("port", this::portText, () -> {
                Node current = node();
                if (current == null) return;
                port = (port + 1) % CenterGraphSync.outlets(current.process);
                readPort();
            }).pos(6, 86)
                .size(87, 15));
            inspector.child(
                button("filter_mode", this::modeText, () -> filterMode = (filterMode + 1) % 3).pos(99, 86)
                    .size(88, 15));
            inspector.child(
                filter.pos(6, 104)
                    .size(181, 15)
                    .tooltipBuilder(t -> t.add(IKey.lang(PREFIX + "filter.help"))));
            inspector.child(
                button(
                    "save_filter",
                    () -> tr("save_filter"),
                    () -> { edit(CenterGraphSync.Action.FILTER, nodeIndex, port * 3 + filterMode, filter.getText()); })
                        .pos(6, 122)
                        .size(181, 15));
            inspector.child(
                button("target", this::targetText, this::nextTarget).pos(6, 140)
                    .size(129, 15));
            inspector.child(
                button(
                    "save_target",
                    () -> tr("save"),
                    () -> edit(CenterGraphSync.Action.TARGET, nodeIndex, port * 65 + target + 1, "")).pos(138, 140)
                        .size(49, 15));
            inspector.child(label(() -> tr("process"), 5, 160, 183, 11));
            inspector.child(
                button(
                    "process_previous",
                    () -> "<",
                    () -> processIndex = Math.floorMod(processIndex - 1, PROCESSES.length)).pos(6, 173)
                        .size(16, 15));
            inspector.child(label(() -> processName(PROCESSES[processIndex]), 24, 173, 145, 15));
            inspector.child(
                button(
                    "process_next",
                    () -> ">",
                    () -> processIndex = (processIndex + 1) % PROCESSES.length).pos(171, 173)
                        .size(16, 15));
            inspector.child(
                button(
                    "add_process",
                    () -> tr("add_process"),
                    () -> edit(
                        CenterGraphSync.Action.APPEND_NODE,
                        nodeIndex,
                        port * PROCESSES.length + processIndex,
                        "")).pos(6, 191)
                            .size(88, 15));
            inspector.child(
                button(
                    "remove_process",
                    () -> tr("remove_process"),
                    () -> edit(CenterGraphSync.Action.REMOVE_NODE, nodeIndex, 0, "")).pos(99, 191)
                        .size(88, 15));
            inspector.child(label(() -> tr(sync.status()), 5, 211, 183, 15));
            return contents;
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
            nodeIndex = graph == null ? 0 : Math.min(nodeIndex, graph.nodes.size() - 1);
            Node current = node();
            if (current == null || port >= CenterGraphSync.outlets(current.process)) port = 0;
            readPort();
        }

        private void selectGraph(long id) {
            if (id == graphId) return;
            graphId = id;
            nodeIndex = port = 0;
            canvas.firstRow = 0;
            refresh();
        }

        private void selectNode(int index) {
            nodeIndex = index;
            port = 0;
            readPort();
        }

        private Node node() {
            return graph == null ? null : graph.nodes.get(nodeIndex);
        }

        private void readPort() {
            Node node = node();
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
            if (graph == null || CenterGraphSync.direct(node().process)) return;
            if (target == CenterGraph.BUS && nodeIndex + 1 < graph.nodes.size()) target = nodeIndex + 1;
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
            return target == CenterGraph.BUS ? tr("output")
                : "→ " + (target + 1) + "  " + processName(graph.nodes.get(target).process);
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
                int x = i % TAB_COLUMNS * TAB_WIDTH, y = i / TAB_COLUMNS * TAB_HEIGHT;
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
            if (x < 0 || y < 0 || x >= TAB_COLUMNS * TAB_WIDTH || y >= 2 * TAB_HEIGHT) return -1;
            int i = y / TAB_HEIGHT * TAB_COLUMNS + x / TAB_WIDTH;
            return i < editor.snapshot.graphs()
                .size() ? i : -1;
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

        private static final int NODE_WIDTH = 148, ROW_HEIGHT = 41, VISIBLE_ROWS = 5;
        private static final int NODE_X = 111, BUS_X = 277, CARD_Y = 22;
        private static final int GRID = 0xFF344149, LINE = 0xFF739A90, BACKGROUND = 0xFF222B30;

        private final Editor editor;
        private final IDrawable text;
        private int firstRow;
        private String line = "";
        private int ink = CriticalGuiStyle.TEXT_BODY;

        Canvas(Editor editor) {
            this.editor = editor;
            text = CriticalGuiStyle.fitted(() -> line, () -> ink, Alignment.CenterLeft);
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            GuiDraw.drawRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT, BACKGROUND);
            for (int x = 0; x < CANVAS_WIDTH; x += 12) GuiDraw.drawRect(x, 0, 1, CANVAS_HEIGHT, GRID);
            for (int y = 0; y < CANVAS_HEIGHT; y += 12) GuiDraw.drawRect(0, y, CANVAS_WIDTH, 1, GRID);
            CenterGraph graph = editor.graph;
            if (graph == null) {
                caption(context, tr("empty"), 114, 103, 176, 24, CriticalGuiStyle.TEXT_MUTED);
                return;
            }
            firstRow = Math.min(firstRow, Math.max(0, graph.nodes.size() - VISIBLE_ROWS));
            int end = Math.min(firstRow + VISIBLE_ROWS, graph.nodes.size());
            int outputY = CANVAS_HEIGHT / 2;
            box(context, theme.getTheme(), tr("input"), 5, outputY - 8, 93);
            box(context, theme.getTheme(), tr("output"), BUS_X, outputY - 8, CANVAS_WIDTH - BUS_X - 5);
            caption(
                context,
                graph.fallback() ? tr("fallback")
                    : tr(
                        "rule_count",
                        graph.rules()
                            .size()),
                5,
                outputY + 12,
                94,
                15,
                CriticalGuiStyle.TEXT_ACCENT);
            if (firstRow == 0) drawRoute(98, outputY, NODE_X, CARD_Y + 8);
            for (int i = firstRow; i < end; i++) {
                Node node = graph.nodes.get(i);
                int y = CARD_Y + (i - firstRow) * ROW_HEIGHT;
                int itemSlots = CenterGraphSync.outlets(node.process);
                for (int slot = 0; slot < itemSlots; slot++) {
                    int target = node.target(slot);
                    if (target == CenterGraph.BUS) drawRoute(NODE_X + NODE_WIDTH, y + 22 + slot * 2, BUS_X, outputY);
                    else if (target < end) {
                        int joint = NODE_X + NODE_WIDTH + 5 + slot;
                        int endY = CARD_Y + (target - firstRow) * ROW_HEIGHT + 20;
                        int startY = y + 23 + slot * 2;
                        GuiDraw.drawRect(NODE_X + NODE_WIDTH, startY, joint - NODE_X - NODE_WIDTH, 1, LINE);
                        GuiDraw.drawRect(joint, startY, 1, endY - startY + 1, LINE);
                        GuiDraw.drawRect(NODE_X + NODE_WIDTH, endY, joint - NODE_X - NODE_WIDTH + 1, 1, LINE);
                    }
                }
            }
            for (int i = firstRow; i < end; i++) {
                Node node = graph.nodes.get(i);
                int y = CARD_Y + (i - firstRow) * ROW_HEIGHT;
                boolean selected = i == editor.nodeIndex;
                GuiDraw.drawRect(NODE_X - 1, y - 1, NODE_WIDTH + 2, 35, selected ? CriticalGuiStyle.TEXT_ACCENT : GRID);
                CriticalGuiStyle.CARD.draw(context, NODE_X, y, NODE_WIDTH, 33, theme.getTheme());
                caption(
                    context,
                    (i + 1) + "  " + processName(node.process),
                    NODE_X + 2,
                    y + 1,
                    NODE_WIDTH - 4,
                    16,
                    CriticalGuiStyle.TEXT_TITLE);
                caption(
                    context,
                    CenterGraphSync.direct(node.process) ? tr("direct")
                        : tr("port_count", CenterGraphSync.outlets(node.process)),
                    NODE_X + 2,
                    y + 17,
                    NODE_WIDTH - 4,
                    13,
                    CriticalGuiStyle.TEXT_MUTED);
            }
            caption(
                context,
                (firstRow + 1) + "–" + end + " / " + graph.nodes.size(),
                5,
                3,
                112,
                13,
                CriticalGuiStyle.TEXT_MUTED);
        }

        private void box(ModularGuiContext context, WidgetTheme theme, String value, int x, int y, int width) {
            CriticalGuiStyle.CARD.draw(context, x, y, width, 17, theme);
            caption(context, value, x, y, width, 17, CriticalGuiStyle.TEXT_TITLE);
        }

        private void caption(ModularGuiContext context, String value, int x, int y, int w, int h, int color) {
            line = value;
            ink = color;
            text.draw(context, x, y, w, h, null);
        }

        private void drawRoute(int x, int y, int endX, int endY) {
            int elbow = (x + endX) / 2;
            GuiDraw.drawRect(x, y, elbow - x, 1, LINE);
            GuiDraw.drawRect(elbow, Math.min(y, endY), 1, Math.abs(y - endY) + 1, LINE);
            GuiDraw.drawRect(elbow, endY, endX - elbow, 1, LINE);
        }

        @Override
        public Result onMousePressed(int mouseButton) {
            if (editor.graph == null || mouseButton != 0) return Result.IGNORE;
            int x = getContext().getMouseX() - NODE_X;
            int y = getContext().getMouseY() - CARD_Y;
            if (x < 0 || x >= NODE_WIDTH || y < 0 || y % ROW_HEIGHT >= 33) return Result.IGNORE;
            int index = firstRow + y / ROW_HEIGHT;
            if (index >= editor.graph.nodes.size()) return Result.IGNORE;
            editor.selectNode(index);
            return Result.SUCCESS;
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            if (editor.graph == null || editor.graph.nodes.size() <= VISIBLE_ROWS) return false;
            int delta = direction == UpOrDown.UP ? -1 : 1;
            firstRow = Math.max(0, Math.min(editor.graph.nodes.size() - VISIBLE_ROWS, firstRow + delta));
            return true;
        }
    }

    private static ButtonWidget<?> button(String key, Supplier<String> caption, Runnable action) {
        return new ButtonWidget<>().name("center_graph_" + key)
            .background(CriticalGuiStyle.BUTTON)
            .hoverBackground(CriticalGuiStyle.HOVER)
            .overlay(CriticalGuiStyle.fitted(caption, () -> CriticalGuiStyle.TEXT_CONTROL, Alignment.CENTER))
            .onMousePressed(button -> {
                if (button != 0) return false;
                action.run();
                return true;
            });
    }

    private static TextFieldWidget field(String key, int maxLength) {
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

    private static String processName(CenterProcess process) {
        return tr(
            "process." + process.name()
                .toLowerCase(java.util.Locale.ROOT));
    }

    private static String outletName(int port) {
        return port == 0 ? tr("primary") : tr("secondary", port);
    }

    private static String tr(String key, Object... args) {
        return args.length == 0 ? StatCollector.translateToLocal(PREFIX + key)
            : StatCollector.translateToLocalFormatted(PREFIX + key, args);
    }
}
