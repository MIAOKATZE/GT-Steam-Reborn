package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.layout.ILayoutWidget;
import com.cleanroommc.modularui.api.widget.IVanillaSlot;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.AbstractWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widget.sizer.ResizeNode;
import com.cleanroommc.modularui.widget.sizer.StaticResizer;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.IExpander;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;

import cpw.mods.fml.relauncher.Side;
import gregtech.api.metatileentity.CommonMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import io.netty.buffer.Unpooled;

/** Actual native widgets and native resizers; no synthetic font metrics or copied UI definitions. */
final class TestLabUiSnapshot {

    private TestLabUiSnapshot() {}

    static final class Manager extends PanelSyncManager {

        final Map<String, SyncHandler<?>> handlers = new LinkedHashMap<>();
        final List<String> duplicateIds = new ArrayList<>();
        final EntityPlayer player;

        Manager(EntityPlayer player) {
            super(new ModularSyncManager(false), true);
            this.player = player;
        }

        @Override
        public EntityPlayer getPlayer() {
            return player;
        }

        @Override
        public PanelSyncManager syncValue(String name, int id, SyncHandler<?> handler) {
            String key = makeSyncKey(name, id);
            SyncHandler<?> previous = handlers.put(key, handler);
            if (previous != null && previous != handler) duplicateIds.add(key);
            return super.syncValue(name, id, handler);
        }
    }

    static void inspect(ModularPanel panel, Manager manager, CommonMetaTileEntity machine, Map<String, Object> details,
        List<String> failures) throws Exception {
        details.put("framework", "MUI2");
        details.put("probePhase", "widgetTree");
        List<IWidget> widgets = new ArrayList<>();
        Set<IWidget> seen = Collections.newSetFromMap(new IdentityHashMap<IWidget, Boolean>());
        int failureCount = failures.size();
        collect(panel, widgets, seen, failures);
        details.put("treeNodeCount", widgets.size());
        // collectSyncValues also traverses the tree: reject cycles before any native traversal.
        if (failures.size() != failureCount) {
            details.put("layoutEvidence", "NOT_RUN: repeated/cyclic widget tree");
            return;
        }
        details.put("treeEvidence", "PASS: traversed actual production IWidget children, no repeated/cyclic nodes");
        details.put("probePhase", "nativeSyncCollection");
        WidgetTree.collectSyncValues(manager, panel);
        details.put("duplicateSyncIds", manager.duplicateIds);
        if (!manager.duplicateIds.isEmpty()) failures.add("Duplicate sync IDs: " + manager.duplicateIds);
        List<Map<String, Object>> sync = new ArrayList<>();
        for (Map.Entry<String, SyncHandler<?>> entry : manager.handlers.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", entry.getKey());
            row.put(
                "type",
                entry.getValue()
                    .getClass()
                    .getName());
            sync.add(row);
        }
        details.put("syncHandlers", sync);
        details.put(
            "syncEvidence",
            manager.duplicateIds.isEmpty() ? "PASS: native WidgetTree.collectSyncValues and unique handler IDs"
                : "FAIL: duplicate handler IDs");
        details.put("probePhase", "partialNativeLayout");
        Evidence evidence;
        try {
            evidence = layout(panel, widgets);
        } catch (RuntimeException | LinkageError error) {
            // A client-only layout signature must not erase the actual server-built tree/syncs.
            evidence = new Evidence();
            evidence.unavailable.addAll(widgets);
            evidence.unsupported.add("native layout unavailable: " + error);
            TestLabUiTask.recordError(details, "layoutError", error);
        }
        details.put("probePhase", "widgetSnapshot");
        List<Map<String, Object>> nodes = new ArrayList<>();
        details.put("nodes", nodes);
        snapshot(panel, null, "0", nodes, evidence, true, failures);
        details.put("logicalViewport", "1920x1080");
        details.put("guiWidth", panel.getArea().width);
        details.put("guiHeight", panel.getArea().height);
        details.put(
            "layoutEvidence",
            "PARTIAL_NATIVE_RESIZE: native dimension/layout/preApplyPos methods; bounds require local and ancestor calculation/layout/children flags, no unavailable branch, and absolute-position submission. Client postFullResize lifecycle and font-based visual acceptance NOT_RUN");
        details.put("layoutNotRun", evidence.unsupported);
        details.put("textRendererSimulationRestored", evidence.simulationRestored);
        details.put(
            "overlapEvidence",
            "NOT_RUN: pages, overlays, scrolling and decoration may intentionally share bounds; no generic overlap failure asserted");
        List<Map<String, Object>> states = new ArrayList<>();
        details.put("probePhase", "progressCodecAndFormatter");
        if (machine instanceof MTEMultiBlockBase)
            progressSamples((MTEMultiBlockBase) machine, manager, states, failures);
        details.put("stateSamples", states);
        details.put("probePhase", "safeCallbackPackets");
        safeActions(manager, machine, details, failures);
    }

    private static final class Evidence {

        final List<String> unsupported = new ArrayList<>();
        final Set<IWidget> unavailable = Collections.newSetFromMap(new IdentityHashMap<IWidget, Boolean>());
        final Set<IWidget> absoluteSubmitted = Collections.newSetFromMap(new IdentityHashMap<IWidget, Boolean>());
        final Set<IWidget> relativeSubmitted = Collections.newSetFromMap(new IdentityHashMap<IWidget, Boolean>());
        final Map<IWidget, String> updateUnavailable = new IdentityHashMap<>();
        boolean simulationRestored;

        void unavailable(IWidget widget, String phase, Throwable error) {
            unavailable.add(widget);
            unsupported.add(
                phase + " "
                    + widget.getClass()
                        .getName()
                    + ": "
                    + error);
        }
    }

    @SuppressWarnings("unchecked")
    private static Evidence layout(ModularPanel panel, List<IWidget> widgets) throws Exception {
        Evidence evidence = new Evidence();
        // Bind only data-tree links needed by resizers. Do not initialise a client screen/container.
        StaticResizer screen = new StaticResizer() {

            final Area area = new Area(0, 0, 1920, 1080);

            @Override
            public Area getArea() {
                return area;
            }

            @Override
            public String getDebugDisplayName() {
                return "UI probe fixed 1920x1080 logical viewport";
            }

            @Override
            public String toString() {
                return getDebugDisplayName();
            }
        };
        bind(panel, null, panel, screen, screen);
        for (IWidget widget : widgets) {
            if (widget instanceof PagedWidget) ((PagedWidget<?>) widget).afterInit();
            // Conditions must be evaluated before Flow collapses disabled children.
            try {
                // Resolve only the declaring class's fields. Reflecting ModularPanel fields
                // forces its client-only ModularScreen field signature to link on the server.
                Consumer<IWidget> update = (Consumer<IWidget>) field(Widget.class, "onUpdateListener").get(widget);
                if (update != null) update.accept(widget);
            } catch (RuntimeException | LinkageError error) {
                evidence.updateUnavailable.put(widget, error.toString());
                evidence.unavailable(widget, "update-before-layout", error);
            }
            widget.resizer()
                .initResizing(true);
            ResizeNode parent = widget.resizer()
                .getParent();
            widget.resizer()
                .checkExpanded(parent instanceof IExpander ? ((IExpander) parent).getExpandAxis() : null);
            if (!widget.resizer()
                .isLayout())
                widget.resizer()
                    .setLayoutDone(true);
        }
        // Native dimension/Flow layout, iterated until fixed geometry settles. Font-dependent
        // branches remain unresolved and are explicitly marked, never replaced with guessed sizes.
        Object textRenderer = null;
        Field simulation = null;
        boolean previousSimulation = false;
        try {
            Class<?> rendererType = Class.forName("com.cleanroommc.modularui.drawable.text.TextRenderer");
            textRenderer = rendererType.getField("SHARED")
                .get(null);
            simulation = field(rendererType, "simulate");
            previousSimulation = simulation.getBoolean(textRenderer);
        } catch (ReflectiveOperationException | LinkageError error) {
            simulation = null;
            evidence.unsupported.add("TextRenderer simulation guard unavailable: " + error);
        }
        try {
            for (int pass = 0; pass < 4; pass++) {
                for (IWidget widget : widgets) {
                    if (evidence.unavailable.contains(widget)) continue;
                    try {
                        widget.resizer()
                            .resize(
                                widget.resizer()
                                    .getParent() != null && widget.resizer()
                                        .getParent()
                                        .isLayout());
                    } catch (RuntimeException | LinkageError e) {
                        evidence.unavailable(widget, "resize", e);
                    }
                }
                for (int i = widgets.size() - 1; i >= 0; i--) {
                    IWidget widget = widgets.get(i);
                    if (evidence.unavailable.contains(widget)) continue;
                    try {
                        if (widget instanceof ILayoutWidget) {
                            boolean done = widget.resizer()
                                .layoutChildren();
                            widget.resizer()
                                .postResize();
                            // Native InternalWidgetTree always runs the cross-axis phase.
                            done &= widget.resizer()
                                .postLayoutChildren();
                            widget.resizer()
                                .setLayoutDone(done);
                        }
                        widget.resizer()
                            .postResize();
                        boolean childrenCalculated = true;
                        for (IWidget child : widget.getChildren()) {
                            childrenCalculated &= !evidence.unavailable.contains(child) && child.resizer()
                                .isFullyCalculated(
                                    widget.resizer()
                                        .isLayout());
                        }
                        widget.resizer()
                            .setChildrenResized(childrenCalculated);
                    } catch (RuntimeException | LinkageError e) {
                        evidence.unavailable(widget, "post-layout", e);
                    }
                }
            }
            for (IWidget widget : widgets) {
                if (!widget.resizer()
                    .isXCalculated()
                    || !widget.resizer()
                        .isYCalculated())
                    continue;
                try {
                    widget.resizer()
                        .preApplyPos();
                    evidence.absoluteSubmitted.add(widget);
                } catch (RuntimeException | LinkageError e) {
                    evidence.unavailable(widget, "absolute-position", e);
                }
            }
            for (IWidget widget : widgets) {
                // Panel needs a real screen and vanilla slots need its main-panel slot transform.
                // Their unsubmitted relative coords are not presented as final widget-parent coords.
                if (widget == panel || widget instanceof IVanillaSlot && ((IVanillaSlot) widget).handleAsVanillaSlot())
                    continue;
                if (!evidence.absoluteSubmitted.contains(widget)) continue;
                try {
                    widget.resizer()
                        .applyPos();
                    evidence.relativeSubmitted.add(widget);
                } catch (RuntimeException | LinkageError error) {
                    evidence.unsupported.add(
                        "relative-position " + widget.getClass()
                            .getName() + ": " + error);
                }
            }
            // A position-submission failure can invalidate children after the resize passes finished.
            // Re-propagate it before any snapshot declares an ancestor branch calculated.
            for (int i = widgets.size() - 1; i >= 0; i--) {
                IWidget widget = widgets.get(i);
                boolean childrenCalculated = true;
                for (IWidget child : widget.getChildren()) {
                    childrenCalculated &= !evidence.unavailable.contains(child) && child.resizer()
                        .isFullyCalculated(
                            widget.resizer()
                                .isLayout());
                }
                widget.resizer()
                    .setChildrenResized(childrenCalculated);
            }
        } finally {
            // TextWidget.simulate() does not reset its shared mode when font measurement throws.
            if (simulation != null && textRenderer != null) {
                simulation.setBoolean(textRenderer, previousSimulation);
                evidence.simulationRestored = true;
            }
        }
        return evidence;
    }

    private static void collect(IWidget widget, List<IWidget> all, Set<IWidget> seen, List<String> failures) {
        if (!seen.add(widget)) {
            failures.add(
                "Repeated/cyclic tree node: " + widget.getClass()
                    .getName());
            return;
        }
        all.add(widget);
        for (IWidget child : widget.getChildren()) collect(child, all, seen, failures);
    }

    private static void bind(IWidget widget, IWidget parent, ModularPanel panel, ResizeNode parentResizer,
        ResizeNode screen) throws Exception {
        field(AbstractWidget.class, "parent").set(widget, parent);
        field(AbstractWidget.class, "panel").set(widget, panel);
        widget.resizer()
            .initialize(parentResizer, screen);
        for (IWidget child : widget.getChildren()) bind(child, widget, panel, widget.resizer(), screen);
    }

    private static void snapshot(IWidget widget, IWidget treeParent, String path, List<Map<String, Object>> rows,
        Evidence evidence, boolean ancestorsCalculated, List<String> failures) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        Area area = widget.getArea();
        row.put("path", path);
        row.put(
            "type",
            widget.getClass()
                .getName());
        if (evidence.relativeSubmitted.contains(widget)) {
            row.put("relativeX", area.rx);
            row.put("relativeY", area.ry);
            row.put("relativePositionEvidence", "NATIVE_APPLY_POS");
        } else {
            row.put("resizeParentX", area.rx);
            row.put("resizeParentY", area.ry);
            row.put(
                "relativePositionEvidence",
                "NOT_RUN: screen/vanilla-slot dependent applyPos or unresolved position");
        }
        row.put("absoluteX", area.x);
        row.put("absoluteY", area.y);
        row.put("width", area.width);
        row.put("height", area.height);
        row.put(
            "xCalculated",
            widget.resizer()
                .isXCalculated());
        row.put(
            "yCalculated",
            widget.resizer()
                .isYCalculated());
        row.put(
            "widthCalculated",
            widget.resizer()
                .isWidthCalculated());
        row.put(
            "heightCalculated",
            widget.resizer()
                .isHeightCalculated());
        boolean localCalculated = widget.resizer()
            .isSelfFullyCalculated();
        boolean branchCalculated = widget.resizer()
            .isFullyCalculated() && !evidence.unavailable.contains(widget);
        boolean geometry = localCalculated && branchCalculated
            && ancestorsCalculated
            && evidence.absoluteSubmitted.contains(widget);
        row.put("localXywhCalculated", localCalculated);
        row.put(
            "layoutDone",
            widget.resizer()
                .isLayoutDone());
        row.put(
            "childrenCalculated",
            widget.resizer()
                .areChildrenCalculated());
        row.put("unavailableBranch", evidence.unavailable.contains(widget));
        row.put("ancestorsCalculated", ancestorsCalculated);
        row.put("absolutePositionSubmitted", evidence.absoluteSubmitted.contains(widget));
        row.put(
            "boundsEvidence",
            geometry ? "PARTIAL_NATIVE_GEOMETRY_WITH_RESOLVED_ANCESTORS" : "NOT_RUN_UNRESOLVED_BRANCH_OR_ANCESTOR");
        row.put("clientLayoutFinalization", "NOT_RUN: actual screen/container postFullResize lifecycle");
        if (localCalculated && (area.width < 0 || area.height < 0))
            failures.add(path + " negative native calculated size");
        // AbstractWidget.getParent requires an opened/valid panel, which this server probe
        // deliberately never creates. The actual getChildren traversal already supplies the
        // production tree parent; do not fake widget validity to bypass that lifecycle guard.
        if (geometry && treeParent != null) {
            Area parent = treeParent.getArea();
            row.put(
                "extendsParent",
                area.x < parent.x || area.y < parent.y
                    || area.x + area.width > parent.x + parent.width
                    || area.y + area.height > parent.y + parent.height);
            row.put(
                "extendsParentInterpretation",
                "REVIEW_ONLY: covers/muffler/scroll/pages intentionally extend parents; unresolved ancestor geometry is not final screen overflow");
        }
        if (!evidence.updateUnavailable.containsKey(widget)) {
            row.put("enabled", widget.isEnabled());
            row.put("visibilityEvidence", "REAL_UPDATE_CONDITION_BEFORE_LAYOUT; disabled MUI2 widgets are hidden");
        } else row.put("enabledEvidence", "NOT_RUN: " + evidence.updateUnavailable.get(widget));
        if (widget instanceof TextWidget) {
            try {
                String text = ((TextWidget<?>) widget).getKey()
                    .getFormatted();
                row.put("text", text);
                row.put(
                    "textNonEmpty",
                    text != null && !text.trim()
                        .isEmpty());
                // Empty conditional status/spacing labels are legal; record them for review.
            } catch (RuntimeException | LinkageError e) {
                row.put(
                    "textEvidence",
                    "NOT_RUN: " + e.getClass()
                        .getSimpleName());
            }
        }
        if (widget instanceof ProgressWidget) {
            ProgressWidget progress = (ProgressWidget) widget;
            if (progress.getDoubleValue() != null) {
                double value = progress.getCurrentProgress();
                row.put("progress", value);
                if (!Double.isFinite(value) || value < 0 || value > 1)
                    failures.add(path + " progress outside [0,1]: " + value);
            } else row.put("progressEvidence", "NOT_RUN: linked value requires container binding");
        }
        rows.add(row);
        int index = 0;
        for (IWidget child : widget.getChildren()) snapshot(
            child,
            widget,
            path + "/" + index++,
            rows,
            evidence,
            ancestorsCalculated && branchCalculated && evidence.absoluteSubmitted.contains(widget),
            failures);
    }

    private static void progressSamples(MTEMultiBlockBase machine, Manager manager, List<Map<String, Object>> rows,
        List<String> failures) throws Exception {
        SyncHandler<?> progress = manager.handlers.get("progressTime:0");
        SyncHandler<?> max = manager.handlers.get("maxProgressTime:0");
        if (!(progress instanceof IntSyncValue) || !(max instanceof IntSyncValue)) return;
        int oldProgress = machine.mProgresstime, oldMax = machine.mMaxProgresstime;
        try {
            for (int tick : new int[] { 0, 1, 300, 599, 600 }) {
                machine.mProgresstime = tick;
                machine.mMaxProgresstime = 600;
                ((IntSyncValue) progress).updateCacheFromSource(false);
                ((IntSyncValue) max).updateCacheFromSource(false);
                PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
                try {
                    ((IntSyncValue) progress).write(packet);
                    IntSyncValue decoded = new IntSyncValue(() -> 0);
                    decoded.readOnClient(0, packet);
                    if (decoded.getValue() != tick) failures.add("progress native codec mismatch at " + tick);
                } finally {
                    packet.release();
                }
                String text = machine.generateCurrentRecipeInfoString();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("state", "known_progress_" + tick);
                row.put("progress", tick);
                row.put("duration", 600);
                row.put("productionRecipeText", text);
                rows.add(row);
                if (text == null || text.trim()
                    .isEmpty()) failures.add("Empty production recipe text at " + tick);
            }
        } finally {
            machine.mProgresstime = oldProgress;
            machine.mMaxProgresstime = oldMax;
        }
    }

    private static void safeActions(Manager manager, CommonMetaTileEntity machine, Map<String, Object> details,
        List<String> failures) throws Exception {
        int ignoredRightClicks = 0;
        NBTTagCompound before = new NBTTagCompound();
        machine.saveNBTData(before);
        for (Map.Entry<String, SyncHandler<?>> entry : manager.handlers.entrySet()) {
            if (!entry.getKey()
                .startsWith("critical.action.") || !(entry.getValue() instanceof InteractionSyncHandler)) continue;
            // Critical terminal's production callback explicitly rejects non-left clicks before any
            // mutation or chat packet. Other callbacks are not blindly fired (may open network GUIs).
            PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
            try {
                new MouseData(Side.SERVER, 1, false, false, false).writeToPacket(packet);
                ((InteractionSyncHandler) entry.getValue()).readOnServer(1, packet);
                ignoredRightClicks++;
            } finally {
                packet.release();
            }
        }
        NBTTagCompound after = new NBTTagCompound();
        machine.saveNBTData(after);
        boolean unchanged = before.equals(after);
        details.put("safeCallbacksMachineNbtUnchanged", unchanged);
        if (!unchanged) failures.add("Allowlisted non-left callbacks changed persisted machine NBT");
        details.put("safeCallbackPackets", ignoredRightClicks);
        details.put(
            "safeCallbackEvidence",
            ignoredRightClicks == 0 ? "NOT_RUN: no allowlisted callback"
                : "Native InteractionSyncHandler decodes non-left MouseData; production early-return guards exercised on isolated controller");
    }

    static void inspectLegacy(ModularWindow window, Map<String, Object> details, List<String> failures) {
        details.put("framework", "MUI1");
        details.put("guiWidth", window.getSize().width);
        details.put("guiHeight", window.getSize().height);
        details.put("syncHandlerCount", window.syncedWidgets.size());
        details.put("layoutEvidence", "DECLARED_BOUNDS_ONLY: legacy init/client layout NOT_RUN");
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 0;
        for (com.gtnewhorizons.modularui.api.widget.Widget widget : window.getChildren())
            legacy(widget, "0/" + index++, rows);
        details.put("nodes", rows);
        details.put("treeNodeCount", rows.size());
        if (window.getSize().width <= 0 || window.getSize().height <= 0)
            failures.add("Non-positive legacy window size");
    }

    private static void legacy(com.gtnewhorizons.modularui.api.widget.Widget widget, String path,
        List<Map<String, Object>> rows) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("path", path);
        row.put(
            "type",
            widget.getClass()
                .getName());
        row.put("x", widget.getPos().x);
        row.put("y", widget.getPos().y);
        row.put("width", widget.getSize().width);
        row.put("height", widget.getSize().height);
        row.put("boundsEvidence", "DECLARED_ONLY");
        rows.add(row);
        if (widget instanceof com.gtnewhorizons.modularui.api.widget.IWidgetParent) {
            int i = 0;
            for (com.gtnewhorizons.modularui.api.widget.Widget child : ((com.gtnewhorizons.modularui.api.widget.IWidgetParent) widget)
                .getChildren()) legacy(child, path + "/" + i++, rows);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { /* next superclass */ }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
}
