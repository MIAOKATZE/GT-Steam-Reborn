// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.gui;

import static com.miaokatze.gtsr.common.gui.CenterGraphGui.tr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.LongFunction;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.oredict.OreDictionary;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtsr.client.gui.CenterTerminalClient;
import com.miaokatze.gtsr.client.gui.CenterAssemblyScene;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraphPreview;
import com.miaokatze.gtsr.common.machine.orecenter.CenterPlanner;
import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;
import com.miaokatze.gtsr.common.items.CenterModuleItems;

final class CenterTerminalPages {

    private static final CenterModule[] MODULES = CenterModule.values();
    private static final String[] SLOT_CODES = { "A1", "P1", "P2", "L1", "L2", "L3" };
    private static final String[] GRADES = { "I", "II", "III" };
    private final CenterGraphSync sync;
    private final LongFunction<ItemStack> sampleForGraph;
    private int slot, grade = 1, scope = -1;
    private boolean allPacks;
    private CenterModule selected = CenterModule.SPEED;
    private boolean showDirectory = true, showDetails = true, focused;
    private int statsColumn = -1, formula, route, outlet;
    private boolean resetArmed, trialRequested;
    private CenterPlanner.Session trialJob;
    private CenterPlanner.Batch trialBatch;
    private CenterGraph trialGraph;
    private String trialStatus;
    private final TextFieldWidget batchSize = CenterGraphGui.field("calc_batch_size", 7);
    private final TextFieldWidget search = CenterGraphGui.field("ledger_search", 64);

    CenterTerminalPages(CenterGraphSync sync, LongFunction<ItemStack> sampleForGraph) {
        this.sync = sync;
        this.sampleForGraph = sampleForGraph;
        batchSize.value(new com.cleanroommc.modularui.value.StringValue("64"));
    }

    private NBTTagCompound state() {
        return sync.terminal();
    }

    private int mask() {
        return state().getInteger("Sectors");
    }

    private int tier() {
        return state().hasKey("Tier") ? state().getInteger("Tier") : -1;
    }

    private int value(String key, int index) {
        int[] values = state().getIntArray(key);
        return index < values.length ? values[index] : 0;
    }

    private int installedGrade() {
        return value("Grades", slot);
    }

    private int count(CenterModule module, int grade) {
        return value("Packs", module.ordinal() * 3 + grade - 1);
    }

    private int kind() {
        return slot % 6 == 0 ? 0 : slot % 6 <= 2 ? 1 : 2;
    }

    private void select(int index) {
        slot = index;
        int currentGrade = installedGrade();
        if (currentGrade > 0) {
            selected = MODULES[value("Modules", slot)];
            grade = currentGrade;
        } else {
            if (selected.kind != kind()) {
                for (CenterModule module : MODULES) if (module.kind == kind()) {
                    selected = module;
                    break;
                }
            }
            grade = 1;
        }
    }

    private boolean removing() {
        return installedGrade() == grade && value("Modules", slot) == selected.ordinal();
    }

    private void apply() {
        int argument = ((removing() ? -1 : selected.ordinal()) << 2) | grade;
        sync.edit(CenterGraphSync.Action.ASSEMBLY, sync.snapshot(), -1, slot, argument, "");
    }

    ParentWidget<?> assembly() {
        ParentWidget<?> page = new ParentWidget<>().name("center_assembly");
        ParentWidget<?> bar = card("center_assembly_workbar").left(0).right(0).top(0).height(13);
        bar.child(button("directory", () -> tr("assembly.directory"), () -> {
            showDirectory = !showDirectory;
            page.scheduleResize();
        }).pos(3, 0).size(38, 13));
        bar.child(button("inspector", () -> tr("assembly.inspector"), () -> {
            showDetails = !showDetails;
            page.scheduleResize();
        }).pos(43, 0).size(38, 13));
        bar.child(CriticalGuiStyle.label(() -> tr("assembly.readout", tr(state().getBoolean("Active") ? "assembly.running"
            : state().getBoolean("Formed") ? "assembly.idle" : "assembly.unformed"),
            Integer.bitCount(mask()), installed(), sync.snapshot().capacity()),
            () -> 0xFFEDE6D8, Alignment.CenterRight).left(84).right(2).top(0).height(13));
        page.child(bar);
        ParentWidget<?> resources = card("center_assembly_resources").left(0).right(0).top(14).height(35);
        for (int i = 0; i < 6; i++) {
            int row = i + 2;
            var readout = CriticalGuiStyle.fitted(() -> CenterOperationText.line(state(), row),
                () -> 0xFFD9E6E2, Alignment.CenterLeft).asWidget();
            readout.widthRel(.48f).top(2 + (i / 2) * 11).height(11);
            if ((i & 1) == 0) readout.left(4);
            else readout.right(3);
            resources.child(readout);
        }
        page.child(resources);

        ParentWidget<?> directory = card("center_assembly_directory").left(0).top(51).bottom(0).width(113)
            .setEnabledIf(w -> showDirectory);
        directory.child(label(() -> tr("assembly.pads")).pos(4, 3).size(103, 13));
        for (int filter = -1; filter < 2; filter++) {
            int id = filter;
            directory.child(button("pad_filter_" + filter, () -> tr("terminal.pad_filter." + id), () -> scope = id)
                .pos(4 + (filter + 1) * 35, 19).size(33, 13));
        }
        directory.child(new PadDirectory().left(4).right(4).top(35).bottom(4));
        page.child(directory);

        ParentWidget<?> center = card("center_assembly_map").left(() -> showDirectory ? 115 : 0,
            com.cleanroommc.modularui.widget.sizer.Unit.Measure.PIXEL)
            .right(() -> showDetails ? 182 : 0, com.cleanroommc.modularui.widget.sizer.Unit.Measure.PIXEL)
            .top(51).bottom(0);
        ParentWidget<?> mapHead = new ParentWidget<>().background((IDrawable)
            (ctx, x, y, width, height, theme) -> GuiDraw.drawRect(x, y, width, height, 0xFF27373C))
            .left(0).right(0).top(1).height(14);
        mapHead.child(label(() -> tr("terminal.assembly_view")).pos(3, 0).size(85, 14));
        mapHead.child(CriticalGuiStyle.label(() -> tr("terminal.assembly_rings"),
            () -> 0xFFCCCCBE, Alignment.CenterRight).left(89).right(3).top(0).height(14));
        center.child(mapHead);
        center.child(new Miniature().name("center_assembly_scene").left(1).right(1).top(15).bottom(16)
            .tooltipBuilder(t -> t.add(tr("terminal.scene_controls"))));
        ParentWidget<?> mapFoot = card("center_assembly_sector_bar").left(1).right(1).bottom(1).height(15);
        mapFoot.child(label(() -> tr("terminal.sector")).pos(1, 0).size(22, 15));
        for (int s = 0; s < 6; s++) {
            int sector = s;
            mapFoot.child(button("sector_" + s, () -> "0" + (sector + 1), () -> select(sector * 6 + slot % 6))
                .left(23 + s * 17).top(2).size(15, 12)
                .background(new com.cleanroommc.modularui.drawable.DynamicDrawable(() -> sector == slot / 6
                    ? TerminalUiStyle.CHIP_ACTIVE : TerminalUiStyle.CHIP_NORMAL)));
        }
        mapFoot.child(button("focus_sector", () -> tr(focused ? "assembly.whole" : "assembly.focus"),
            () -> focused = !focused).right(3).top(2).size(49, 12)
            .setEnabledIf(w -> Integer.bitCount(mask()) > 1));
        center.child(mapFoot);
        page.child(center);

        ParentWidget<?> detail = card("center_assembly_inspector").right(0).top(51).bottom(0).width(180)
            .setEnabledIf(w -> showDetails);
        detail.child(label(() -> tr("assembly.pad")).pos(4, 3).size(96, 13));
        detail.child(CriticalGuiStyle.label(() -> "S0" + (slot / 6 + 1) + " / " + SLOT_CODES[slot % 6],
            () -> 0xFFE3B87B, Alignment.CenterRight).right(4).top(3).size(71, 13));
        ParentWidget<?> selection = card("center_assembly_selection").left(4).right(4).top(19).height(29);
        selection.child(new SelectedPack().pos(4, 4).size(22, 22));
        selection.child(label(() -> pack(selected, grade).getDisplayName()).left(28).right(3).top(2).height(13));
        selection.child(label(() -> installedGrade() != 0 ? tr("terminal.installed_grade", GRADES[installedGrade() - 1])
            : tr((mask() & 1 << (slot / 6)) == 0 ? "assembly.planned" : "assembly.vacant"))
            .left(28).right(3).top(15).height(12));
        detail.child(selection);
        detail.child(label(() -> tr("assembly.pack")).pos(4, 50).size(95, 12));
        for (int g = 1; g <= 3; g++) {
            int level = g;
            detail.child(button("pack_grade_" + g, () -> tr("terminal.pack_grade", GRADES[level - 1]), () -> grade = level)
                .right(4 + (3 - g) * 24).top(50).size(22, 12)
                .background(new com.cleanroommc.modularui.drawable.DynamicDrawable(() -> grade == level
                    ? TerminalUiStyle.CHIP_ACTIVE : TerminalUiStyle.CHIP_NORMAL)));
        }
        detail.child(new PackPicker().pos(4, 65).size(171, 75));
        detail.child(label(() -> tr("assembly.bus_count", count(selected, grade))).pos(4, 142).size(170, 13));
        detail.child(button("apply_module", () -> tr((mask() & 1 << (slot / 6)) == 0
            ? "terminal.wait_sector" : removing() ? "assembly.remove" : "assembly.install"), this::apply)
            .left(4).right(4).top(157).height(14));
        detail.child(label(() -> tr("assembly.bus")).pos(4, 177).size(103, 14));
        detail.child(button("stock_all", () -> tr(allPacks ? "terminal.compatible_packs" : "terminal.all_packs"),
            () -> allPacks = !allPacks).right(4).top(177).size(53, 14));
        detail.child(new PackStock().left(4).right(4).top(193).bottom(4));
        return page.child(detail);
    }

    private int installed() {
        int n = 0;
        for (int grade : state().getIntArray("Grades")) if (grade != 0) n++;
        return n;
    }

    ParentWidget<?> statistics() {
        ParentWidget<?> page = card("center_statistics");
        page.child(label(() -> tr("stats.title")).pos(7, 5).size(80, 15));
        for (int i = -1; i < 3; i++) {
            int column = i;
            page.child(button("stats_column_" + i, () -> tr("stats.column." + column), () -> statsColumn = column)
                .right(176 + (2 - i) * 36).top(3).size(34, 13));
        }
        page.child(search.right(55).top(3).size(116, 13)
            .tooltipBuilder(t -> t.add(tr("stats.search"))));
        page.child(button("stats_reset", () -> tr(resetArmed ? "stats.confirm" : "stats.reset"), () -> {
            if (resetArmed) sync.edit(CenterGraphSync.Action.RESET_LEDGER, sync.snapshot(), -1, 0, 0, "");
            resetArmed = !resetArmed;
        }).right(5).top(3).size(47, 13));
        page.child(label(() -> tr("terminal.ledger_hint")).left(3).right(3).top(18).height(10));
        page.child(new Ledger().left(2).right(2).top(29).bottom(17));
        page.child(label(() -> tr("terminal.ledger_detail")).left(3).right(3).bottom(3).height(12));
        return page;
    }

    void disarm() {
        resetArmed = false;
    }

    ParentWidget<?> calculation() {
        ParentWidget<?> page = new ParentWidget<>().name("center_calculation");
        ParentWidget<?> tools = card("center_calculation_tools").left(0).right(0).top(0).height(22);
        tools.child(label(() -> tr("calc.route")).pos(2, 3).size(47, 15));
        tools.child(button("calc_route", () -> {
            CenterGraph graph = calculationGraph();
            return graph == null || graph.name.isEmpty() ? tr("graph") + " " + (route + 1) : graph.name;
        }, () -> {
            route = (route + 1) % Math.max(1, sync.snapshot().graphs().size());
            outlet = 0;
            resetTrial();
        }).pos(50, 4).size(83, 14));
        tools.child(label(() -> tr("terminal.batch_size")).right(92).top(3).size(55, 15));
        tools.child(batchSize.right(47).top(4).size(43, 13));
        tools.child(button("calc_run", () -> tr("terminal.trial"), this::runTrial).right(4).top(4).size(40, 13));
        page.child(tools);
        page.child(label(() -> tr("terminal.path_readout", path().size())).left(3).right(3).top(24).height(13));
        page.child(new PathTable().left(2).widthRel(.468f).top(39).bottom(17));
        page.child(new CalculationTable().name("center_calculation_results").leftRelOffset(.468f, 5).right(2).top(39).bottom(17));
        page.child(label(() -> tr(trialStatus == null ? "terminal.calc_live" : trialStatus))
            .left(3).right(3).bottom(2).height(12));
        return page;
    }

    private void resetTrial() {
        trialRequested = false;
        trialJob = null;
        trialBatch = null;
        trialGraph = null;
        trialStatus = null;
    }

    private void runTrial() {
        resetTrial();
        trialRequested = true;
        trialGraph = calculationGraph();
        int quantity;
        try {
            quantity = Integer.parseInt(batchSize.getText().trim());
        } catch (NumberFormatException invalid) {
            quantity = 0;
        }
        if (quantity < 1 || quantity > 4096) {
            trialStatus = "terminal.trial_size";
            return;
        }
        ItemStack sample = null;
        if (trialGraph != null) {
            sample = sampleForGraph.apply(trialGraph.id);
            if (sample == null) for (String rule : trialGraph.rules()) {
                if (!OreDictionary.doesOreNameExist(rule)) continue;
                List<ItemStack> entries = OreDictionary.getOres(rule, false);
                if (!entries.isEmpty()) {
                    sample = entries.get(0);
                    break;
                }
            }
        }
        if (sample == null) {
            trialStatus = "terminal.trial_sample";
            return;
        }
        trialJob = CenterGraphPreview.trial(trialGraph, sample, quantity, state());
        trialStatus = trialJob == null ? "terminal.trial_missing" : "terminal.trial_working";
    }

    private List<CenterGraph.Node> path() {
        CenterGraph graph = calculationGraph();
        if (graph == null || graph.nodes.isEmpty()) return Collections.emptyList();
        List<CenterGraph.Node> path = new ArrayList<>();
        int i = 0;
        while (i >= 0) {
            CenterGraph.Node node = graph.nodes.get(i);
            path.add(node);
            i = node.target(Math.min(outlet, CenterGraphSync.outlets(node) - 1));
        }
        return path;
    }

    private CenterGraph calculationGraph() {
        List<CenterGraph> graphs = sync.snapshot().graphs();
        return graphs.isEmpty() ? null : graphs.get(Math.min(route, graphs.size() - 1));
    }

    private String amount(int row) {
        NBTTagCompound state = state();
        if (trialRequested && trialBatch == null && row != 3) return "—";
        long time = trialRequested ? trialBatch == null ? 0 : trialBatch.ticks : state.getInteger("Duration");
        long energy = trialRequested ? trialBatch == null ? 0 : trialBatch.energy : state.getLong("Energy");
        long steam = trialRequested ? trialBatch == null ? 0 : trialBatch.steam : state.getLong("Steam");
        long density = Math.max(1, state.getInteger("SteamDensity"));
        return switch (row) {
            case 0 -> (trialRequested ? Long.toString(time) : state.getInteger("Elapsed") + " / " + time) + " t";
            case 1 -> (steam + density - 1) / density + " L";
            case 2 -> energy + " EU";
            case 3 -> state.getLong("Power") + " EU/t";
            default -> String.format(Locale.ROOT, "%.3f", 1 + .06 * Math.max(0, Integer.bitCount(mask()) - 1)
                + (trialRequested ? 0 : .025 * Math.max(0,
                    sync.snapshot().graphs().stream().filter(graph -> graph.enabled).count() - 1)));
        };
    }

    private abstract static class Surface<S extends Surface<S>> extends Widget<S> implements Interactable {

        private String caption;
        private int color;
        private final IDrawable text = CenterGraphGui.compact(() -> caption, () -> color, Alignment.CenterLeft);

        void text(ModularGuiContext context, String value, int x, int y, int width, int height, boolean active) {
            caption = value;
            color = active ? CriticalGuiStyle.TEXT_ACCENT : CriticalGuiStyle.TEXT_BODY;
            if (width > 0) text.draw(context, x, y, width, height, null);
        }

        void item(ModularGuiContext context, ItemStack stack, int x, int y, int size) {
            if (stack != null) GuiDraw.drawItem(stack, x, y, size, size, context.getCurrentDrawingZ());
        }
    }

    private final class PadDirectory extends Surface<PadDirectory> {

        private int first;
        private final List<Integer> items = new ArrayList<>(42);

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            items.clear();
            for (int sector = 0; sector < 6; sector++) {
                items.add(-sector - 1);
                if (sector != slot / 6) continue;
                for (int i = sector * 6; i < (sector + 1) * 6; i++) {
                    int grade = value("Grades", i);
                    if (scope == -1 || scope == 0 && grade > 0 || scope == 1 && grade == 0) items.add(i);
                }
            }
            int visible = getArea().height / 13;
            first = Math.min(first, Math.max(0, items.size() - visible));
            for (int i = first; i < Math.min(items.size(), first + visible); i++) {
                int pad = items.get(i), y = (i - first) * 13;
                if (pad < 0) {
                    int sector = -pad - 1, levels = 0;
                    for (int place = sector * 6; place < (sector + 1) * 6; place++)
                        if (value("Grades", place) != 0) levels++;
                    (sector == slot / 6 ? TerminalUiStyle.CHIP_ACTIVE : TerminalUiStyle.CHIP_NORMAL)
                        .draw(context, 0, y, getArea().width, 12, theme.getTheme());
                    text(context, tr("terminal.sector") + " 0" + (sector + 1), 1, y, 69, 12, sector == slot / 6);
                    text(context, (mask() & 1 << sector) == 0 ? tr("assembly.planned") : levels + " / 6",
                        getArea().width - 27, y, 25, 12, false);
                } else {
                    int level = value("Grades", pad);
                    if (pad == slot) TerminalUiStyle.CHIP_ACTIVE.draw(context, 0, y, getArea().width, 12, theme.getTheme());
                    text(context, SLOT_CODES[pad % 6], 3, y, 23, 12, pad == slot);
                    String name = level == 0 ? tr("assembly.vacant")
                        : tr("assembly.short." + MODULES[value("Modules", pad)].key);
                    text(context, name, 24, y, getArea().width - 40, 12, pad == slot);
                    text(context, level == 0 ? "–" : GRADES[level - 1], getArea().width - 17, y, 17, 12, false);
                }
            }
        }

        @Override
        public Result onMousePressed(int button) {
            int row = first + getContext().getMouseY() / 13;
            if (button != 0 || row < 0 || row >= items.size()) return Result.IGNORE;
            int entry = items.get(row);
            select(entry < 0 ? (-entry - 1) * 6 + slot % 6 : entry);
            return Result.SUCCESS;
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            first = Math.max(0, first + (direction == UpOrDown.UP ? -1 : 1));
            return true;
        }
    }

    private final class SelectedPack extends Surface<SelectedPack> {

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            TerminalUiStyle.SLOT_FRAME.draw(context, 0, 0, 22, 22, theme.getTheme());
            item(context, pack(selected, grade), 2, 2, 18);
        }
    }

    private final class Miniature extends Surface<Miniature> {

        private CenterAssemblyScene scene;
        private int drawnMask, mouseX, mouseY;
        private boolean rotating, rendered;

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int width = getArea().width, height = getArea().height;
            if (width <= 0 || height <= 0) return;
            GuiDraw.drawRect(0, 0, width, height, 0xFF1B292E);
            for (int i = 0; i < width; i += 10) GuiDraw.drawRect(i, 0, 1, height, 0xFF25363B);
            for (int i = 0; i < height; i += 10) GuiDraw.drawRect(0, i, width, 1, 0xFF25363B);
            if (scene == null) scene = new CenterAssemblyScene();
            int mask = mask(), local = 1 << (slot / 6);
            drawnMask = focused && (mask & local) != 0 ? local : mask;
            rendered = scene.draw(context, width, height, Math.max(0, tier()), drawnMask,
                state().getIntArray("Modules"), state().getIntArray("Grades"));
            if (!rendered) {
                text(context, tr("terminal.scene_unavailable"), 7, height / 2 - 9, width - 14, 18, false);
                return;
            }
            for (int i = 0; i < 36; i++) {
                if ((drawnMask & 1 << (i / 6)) == 0 || i / 6 != slot / 6) continue;
                int x = scene.x[i], y = scene.y[i];
                if (x < 9 || y < 6 || x >= width - 9 || y >= height - 6) continue;
                (i == slot ? TerminalUiStyle.CHIP_ACTIVE : TerminalUiStyle.CHIP_NORMAL)
                    .draw(context, x - 9, y - 5, 18, 11, theme.getTheme());
                text(context, SLOT_CODES[i % 6], x - 7, y - 5, 16, 11, i == slot);
            }
        }

        @Override
        public Result onMousePressed(int button) {
            if (!rendered) return Result.IGNORE;
            int mx = getContext().getMouseX(), my = getContext().getMouseY();
            if (button == 1) {
                mouseX = mx;
                mouseY = my;
                rotating = true;
                return Result.SUCCESS;
            }
            if (button != 0) return Result.IGNORE;
            int closest = -1, distance = 196;
            for (int i = 0; i < 36; i++) {
                if ((drawnMask & 1 << (i / 6)) == 0) continue;
                int dx = mx - scene.x[i], dy = my - scene.y[i], square = dx * dx + dy * dy;
                if (square >= distance) continue;
                closest = i;
                distance = square;
            }
            if (closest < 0) return Result.IGNORE;
            select(closest);
            return Result.SUCCESS;
        }

        @Override
        public void onMouseDrag(int button, long time) {
            if (!rotating || button != 1 || scene == null) return;
            int x = getContext().getMouseX(), y = getContext().getMouseY();
            scene.rotate(x - mouseX, y - mouseY);
            mouseX = x;
            mouseY = y;
        }

        @Override
        public boolean onMouseRelease(int button) {
            if (button != 1 || !rotating) return false;
            rotating = false;
            return true;
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            if (!rendered || scene == null) return false;
            scene.zoom(direction == UpOrDown.UP);
            return true;
        }

        @Override
        public void dispose() {
            if (scene != null) scene.close();
            scene = null;
            super.dispose();
        }
    }

    private final class PackPicker extends Surface<PackPicker> {

        PackPicker() {
            tooltipBuilder(t -> {
                CenterModule module = at(getContext().getMouseX(), getContext().getMouseY());
                if (module != null) {
                    ItemStack stack = pack(module, grade);
                    for (String line : stack.getTooltip(CenterTerminalClient.player(), false)) t.add(line);
                }
            });
        }

        private CenterModule at(int x, int y) {
            if (x < 0 || y < 0 || x >= 170 || y >= 75) return null;
            int index = x / 34 + y / 25 * 5;
            for (CenterModule module : MODULES) if (module.kind == kind() && index-- == 0) return module;
            return null;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int index = 0;
            for (CenterModule module : MODULES) {
                if (module.kind != kind()) continue;
                int x = (index % 5) * 34, y = (index++ / 5) * 25;
                (module == selected ? TerminalUiStyle.CHIP_ACTIVE : TerminalUiStyle.SLOT_FRAME)
                    .draw(context, x + 1, y + 1, 32, 23, theme.getTheme());
                item(context, pack(module, grade), x + 9, y + 2, 14);
                text(context, Integer.toString(count(module, grade)), x + 23, y + 1, 10, 11, false);
                text(context, tr("assembly.short." + module.key), x + 1, y + 16, 32, 8, module == selected);
            }
            markTooltipDirty();
        }

        @Override
        public Result onMousePressed(int button) {
            CenterModule module = at(getContext().getMouseX(), getContext().getMouseY());
            if (button != 0 || module == null) return Result.IGNORE;
            selected = module;
            return Result.SUCCESS;
        }
    }

    private final class PackStock extends Surface<PackStock> {

        private int first;

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int w = getArea().width;
            TerminalUiStyle.CHIP_NORMAL.draw(context, 0, 0, w, 12, theme.getTheme());
            text(context, tr("terminal.stock_module"), 1, 0, w - 65, 12, false);
            text(context, tr("terminal.stock_input"), w - 63, 0, 31, 12, false);
            text(context, tr("terminal.stock_return"), w - 32, 0, 31, 12, false);
            int row = 0, index = 0;
            for (CenterModule module : MODULES) {
                if (!allPacks && module.kind != kind()) continue;
                if (index++ < first || 13 + (row + 1) * 13 > getArea().height) continue;
                int y = 13 + row++ * 13;
                if ((row & 1) == 0) GuiDraw.drawRect(0, y, w, 12, 0xFF30363D);
                GuiDraw.drawRect(0, y + 12, w, 1, 0xFF484E54);
                text(context, tr("assembly.short." + module.key), 1, y, w - 68, 12, module == selected);
                text(context, Integer.toString(count(module, grade)), w - 59, y, 25, 12, false);
                int returned = value("Returns", module.ordinal() * 3 + grade - 1);
                text(context, Integer.toString(returned), w - 28, y, 27, 12, false);
            }
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            int count = 0;
            for (CenterModule module : MODULES) if (allPacks || module.kind == kind()) count++;
            int max = Math.max(0, count - (getArea().height - 13) / 13);
            first = Math.max(0, Math.min(max, first + (direction == UpOrDown.UP ? -1 : 1)));
            return true;
        }
    }

    private static final class StatRow {
        final ItemStack stack;
        final long[] amounts;

        StatRow(ItemStack stack, long[] amounts) {
            this.stack = stack;
            this.amounts = amounts;
        }
    }

    private final class Ledger extends Surface<Ledger> {

        private List<StatRow> rows = Collections.emptyList();
        private NBTTagList seen;
        private String query;
        private int first, filtered = -2;

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            String text = search.getText().toLowerCase(Locale.ROOT).trim();
            NBTTagList ledger = sync.ledger();
            if (seen != ledger || filtered != statsColumn || !text.equals(query)) {
                if (filtered != statsColumn || !text.equals(query)) first = 0;
                seen = ledger;
                filtered = statsColumn;
                query = text;
                rows = new ArrayList<>();
                for (int i = 0; i < ledger.tagCount(); i++) {
                    NBTTagCompound nbt = ledger.getCompoundTagAt(i);
                    ItemStack stack = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("Item"));
                    if (stack == null || !stack.getDisplayName().toLowerCase(Locale.ROOT).contains(query)
                        && !String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()))
                            .toLowerCase(Locale.ROOT).contains(query)) continue;
                    long[] values = { nbt.getLong("C0"), nbt.getLong("C1"), nbt.getLong("C2") };
                    if (filtered < 0 || values[filtered] > 0) rows.add(new StatRow(stack, values));
                }
                if (filtered >= 0) rows.sort(Comparator.comparingLong((StatRow row) -> row.amounts[filtered]).reversed());
            }
            int width = getArea().width, col = width / 6;
            TerminalUiStyle.CHIP_NORMAL.draw(context, 0, 0, width, 17, theme.getTheme());
            text(context, tr("stats.item"), 6, 0, 3 * col, 17, true);
            for (int i = 0; i < 3; i++) text(context, tr("stats.column." + i), width - (3 - i) * col,
                0, col, 17, i == filtered);
            int visible = Math.max(0, (getArea().height - 18) / 20);
            first = Math.min(first, Math.max(0, rows.size() - visible));
            for (int i = first; i < Math.min(first + visible, rows.size()); i++) {
                StatRow row = rows.get(i);
                int y = 18 + (i - first) * 20;
                if ((i & 1) != 0) GuiDraw.drawRect(0, y, width, 20, 0xFF394046);
                item(context, row.stack, 4, y + 2, 16);
                text(context, row.stack.getDisplayName(), 24, y, width - 3 * col - 25, 20, false);
                for (int j = 0; j < 3; j++) text(context, String.format(Locale.ROOT, "%,d", row.amounts[j]),
                    width - (3 - j) * col, y, col, 20, filtered == j);
            }
            if (rows.isEmpty()) text(context, tr("stats.empty"), 6, 20, width - 12, 20, false);
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            first = Math.max(0, first + (direction == UpOrDown.UP ? -1 : 1));
            return true;
        }
    }

    private final class PathTable extends Surface<PathTable> {

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int width = getArea().width;
            TerminalUiStyle.CARD.draw(context, 0, 0, width, getArea().height, theme.getTheme());
            TerminalUiStyle.CHIP_NORMAL.draw(context, 1, 1, width - 2, 15, theme.getTheme());
            text(context, tr("calc.path"), 3, 1, width - 7, 15, true);
            GuiDraw.drawRect(2, 16, width - 4, 13, 0xFF30363D);
            text(context, tr("terminal.processes"), 3, 16, width / 3, 13, false);
            text(context, tr("terminal.path_outlet"), width / 3, 16, width * 2 / 3 - 6, 13, false);
            List<CenterGraph.Node> path = path();
            for (int i = 0; i < Math.min(path.size(), (getArea().height - 30) / 21); i++) {
                CenterGraph.Node node = path.get(i);
                int port = Math.min(outlet, CenterGraphSync.outlets(node) - 1), y = 29 + i * 21;
                if ((i & 1) != 0) GuiDraw.drawRect(2, y, width - 4, 21, 0xFF2F343B);
                text(context, (i + 1) + "  " + CenterGraphGui.processName(node.process), 3, y, width / 3, 20, false);
                text(context, tr("calc.outlet", port + 1) + " → " + (node.target(port) < 0
                    ? tr(node.target(port) == CenterGraph.BUS ? "output" : "unconnected")
                    : CenterGraphGui.processName(calculationGraph().nodes.get(node.target(port)).process)),
                    width / 3, y, width * 2 / 3 - 7, 20, true);
            }
        }

        @Override
        public Result onMousePressed(int button) {
            if (button != 0 || getContext().getMouseY() < 29) return Result.IGNORE;
            outlet = (outlet + 1) % 6;
            return Result.SUCCESS;
        }
    }

    private final class CalculationTable extends Surface<CalculationTable> {

        @Override
        public void onUpdate() {
            super.onUpdate();
            if (trialRequested && calculationGraph() != trialGraph) resetTrial();
            if (trialJob != null && trialJob.advance(4, 200_000)) {
                trialBatch = trialJob.batch();
                trialJob = null;
                trialStatus = trialBatch == null ? "terminal.trial_missing" : "terminal.trial_ready";
            }
        }

        private int formulaRow;
        private final IDrawable right = CriticalGuiStyle.fitted(() -> amount(formulaRow), () -> 0xFFE7DDC8,
            Alignment.CenterRight);

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int w = getArea().width;
            TerminalUiStyle.CARD.draw(context, 0, 0, w, getArea().height, theme.getTheme());
            TerminalUiStyle.CHIP_NORMAL.draw(context, 1, 1, w - 2, 15, theme.getTheme());
            text(context, tr("calc.result"), 3, 1, w - 6, 15, true);
            int y = 16;
            for (int row = 0; row < 5; row++) {
                if (row == 0 || row == 1 || row == 4) {
                    GuiDraw.drawRect(2, y, w - 4, 11, 0xFF333940);
                    text(context, tr("terminal.formula_group." + row), 3, y, w - 6, 11, false);
                    y += 11;
                }
                GuiDraw.drawRect(2, y, w - 4, 14, row == formula ? 0xFF474E58 : 0xFF2F343B);
                text(context, tr("calc.row." + row), 3, y, w / 2, 14, row == formula);
                formulaRow = row;
                right.draw(context, w / 2, y, w / 2 - 3, 14, null);
                y += 15;
            }
            int detail = y + 20;
            if (detail + 55 >= getArea().height) return;
            GuiDraw.drawRect(2, detail, w - 4, 1, 0xFF434B53);
            text(context, tr("calc.row." + formula) + " · " + tr("calc.detail"), 3, detail + 3, w - 6, 14, true);
            text(context, tr("calc.formula." + formula), 3, detail + 18, w - 7, 15, false);
            if (trialBatch != null) {
                int consumed = trialBatch.consumed.stream().mapToInt(item -> item.stackSize).sum();
                text(context, tr("terminal.trial_consumed", consumed), 3, detail + 34, w - 7, 15, false);
            }
        }

        @Override
        public Result onMousePressed(int button) {
            if (button != 0) return Result.IGNORE;
            int y = getContext().getMouseY(), start = 16;
            for (int row = 0; row < 5; row++) {
                if (row == 0 || row == 1 || row == 4) start += 11;
                if (y >= start && y < start + 14) {
                    formula = row;
                    return Result.SUCCESS;
                }
                start += 15;
            }
            return Result.IGNORE;
        }
    }

    private static ItemStack pack(CenterModule module, int grade) {
        return CenterModuleItems.stack(module, grade);
    }

    static ParentWidget<?> card(String name) {
        return new ParentWidget<>().name(name).background(TerminalUiStyle.CARD);
    }

    private static ButtonWidget<?> button(String key, java.util.function.Supplier<String> label, Runnable action) {
        return CenterGraphGui.button(key, label, action);
    }

    static ParentWidget<?> label(java.util.function.Supplier<String> text) {
        return CriticalGuiStyle.label(text, () -> CriticalGuiStyle.TEXT_TITLE, Alignment.CenterLeft);
    }
}
