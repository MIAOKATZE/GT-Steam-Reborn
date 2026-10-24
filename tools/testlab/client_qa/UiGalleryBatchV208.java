package audit;

import com.google.gson.JsonObject;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

/** Narrow v2.0.8 visual routes. Calls production builders; never contains product classes or grants. */
final class UiGalleryBatchV208 {
    private static final String G = UiGalleryBatch.G;

    /** Failure telemetry only; cross-thread server fields are observations, never a business authority. */
    static JsonObject waitObservation(UiGalleryBatch batch) {
        JsonObject out = new JsonObject();
        out.addProperty("stage", batch.stage); out.addProperty("request", batch.request);
        out.addProperty("expectedWindowId", batch.windowId);
        out.addProperty("caseElapsedMillis", (System.nanoTime() - batch.caseStartNanos) / 1e6);
        try {
            Object gui = UiGalleryBatch.field(batch.q.mc, "currentScreen|field_71462_r");
            out.addProperty("actualGuiClass", gui == null ? "null" : gui.getClass().getName());
        } catch (Throwable error) { out.addProperty("guiObservationError", error.toString()); }
        for (String side : new String[] { "client", "server" }) {
            try {
                Object player = side.equals("client") ? batch.player() : batch.q.serverPlayer;
                Object container = UiGalleryBatch.field(player, "openContainer|field_71070_bA");
                JsonObject state = new JsonObject();
                state.addProperty("containerClass", container.getClass().getName());
                state.addProperty("windowId", batch.q.number(UiGalleryBatch.field(container, "windowId|field_75152_c")));
                Object data = UiGalleryBatch.call(container, "getGuiData");
                state.addProperty("dataClass", data.getClass().getName());
                if (UiGalleryBatch.type(G + "common.terminal.RemoteTerminalGuiData").isInstance(data)) {
                    state.addProperty("remote", Boolean.TRUE.equals(UiGalleryBatch.field(data, "remote")));
                    state.addProperty("grantPresent", UiGalleryBatch.field(data, "grant") != null);
                    state.addProperty("tilePresent", UiGalleryBatch.call(data, "getTileEntity") != null);
                }
                Object psm = UiGalleryBatch.call(UiGalleryBatch.call(container, "getSyncManager"), "getMainPSM");
                state.addProperty("psmInitialized", Boolean.TRUE.equals(UiGalleryBatch.call(psm, "isInitialised")));
                try {
                    Object session = UiGalleryBatch.call(psm, "findSyncHandler", "machine_history");
                    state.addProperty("historyPending", Boolean.TRUE.equals(UiGalleryBatch.call(
                        UiGalleryBatch.field(session, "pending"), "get")));
                    state.addProperty("historyLastAction", ((Number)UiGalleryBatch.field(session, "lastAction")).longValue());
                    state.addProperty("historyActiveObserved", Boolean.TRUE.equals(UiGalleryBatch.call(session, "active")));
                    state.addProperty("historyDataValidObserved", Boolean.TRUE.equals(UiGalleryBatch.call(
                        UiGalleryBatch.field(session, "data"), "valid", player)));
                } catch (Throwable noSession) { state.addProperty("historyObservation", noSession.toString()); }
                out.add(side, state);
            } catch (Throwable error) { out.addProperty(side + "ObservationError", error.toString()); }
        }
        if (batch.q.wire != null) {
            if (batch.q.wire.lastMuiOpen != null) out.add("lastActualMuiOpen", batch.q.wire.lastMuiOpen);
            JsonObject packets = new JsonObject();
            for (Map.Entry<String, java.util.concurrent.atomic.AtomicLong> entry : batch.q.wire.values.entrySet())
                if (entry.getKey().startsWith("phase" + batch.q.phase + ":") && entry.getKey().contains(":modularui2:"))
                    packets.addProperty(entry.getKey(), entry.getValue().get());
            out.add("currentPhaseMuiPackets", packets);
        }
        try {
            out.addProperty("serverActionQueueSizeObserved", ((java.util.Queue<?>)UiGalleryBatch.field(
                UiGalleryBatch.type(G + "common.gui.mui2.Mui2ServerActions"), "PENDING")).size());
        } catch (Throwable error) { out.addProperty("queueObservationError", error.toString()); }
        return out;
    }

    static JsonObject historyNameObservation(UiGalleryBatch batch, Object session) {
        JsonObject out = new JsonObject();
        try {
            List<?> entries = (List<?>)UiGalleryBatch.field(UiGalleryBatch.field(session, "data"), "entries");
            String raw = String.valueOf(UiGalleryBatch.field(entries.get(0), "name"));
            String name = String.valueOf(UiGalleryBatch.call(session, "name", 0));
            Object key = UiGalleryBatch.call(UiGalleryBatch.type("com.cleanroommc.modularui.api.drawable.IKey"), "lang", raw);
            out.addProperty("rawEntryName", raw); out.addProperty("sessionName", name);
            out.addProperty("langFormatted", String.valueOf(UiGalleryBatch.call(key, "getFormatted")));
            out.addProperty("clientClipped340", String.valueOf(UiGalleryBatch.call(UiGalleryBatch.type(
                G + "common.gui.mui2.hub.HubClientText"), "clip", name, 340)));
            Object button = batch.q.find(batch.q.panel, "machine_history_row_0");
            com.google.gson.JsonArray children = new com.google.gson.JsonArray();
            historyTextChildren(batch, button, children);
            out.add("actualTextChildren", children);
        } catch (Throwable error) { out.addProperty("observationError", error.toString()); }
        return out;
    }

    static void historyTextChildren(UiGalleryBatch batch, Object widget, com.google.gson.JsonArray output) throws Exception {
        if (UiGalleryBatch.type("com.cleanroommc.modularui.widgets.TextWidget").isInstance(widget)) {
            JsonObject child = new JsonObject();
            child.addProperty("class", widget.getClass().getName());
            child.add("area", batch.rectangle(UiGalleryBatch.call(widget, "getArea")));
            child.addProperty("keyFormatted", String.valueOf(UiGalleryBatch.call(UiGalleryBatch.call(widget, "getKey"), "getFormatted")));
            child.addProperty("lastDrawnText", String.valueOf(UiGalleryBatch.field(widget, "lastText")));
            child.add("padding", batch.paddingState(UiGalleryBatch.call(UiGalleryBatch.call(widget, "getArea"), "getPadding")));
            output.add(child);
        }
        for (Object child : (List<?>)UiGalleryBatch.call(widget, "getChildren")) historyTextChildren(batch, child, output);
    }

    static boolean isNei(JsonObject row) {
        return "nei".equals(UiGalleryBatch.str(row, "visual", ""));
    }

    static String criticalIdentity(Object psm) throws Exception {
        Object value = null;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>)UiGalleryBatch.field(psm, "syncHandlers")).entrySet()) {
            if (String.valueOf(entry.getKey()).equals("critical.businessSnapshot")) {
                value = UiGalleryBatch.call(entry.getValue(), "getValue"); break;
            }
        }
        if (value == null) return "";
        Object configuration = UiGalleryBatch.call(value, "configuration");
        return String.valueOf(UiGalleryBatch.field(configuration, "kind")) + ":"
            + UiGalleryBatch.call(UiGalleryBatch.field(value, "construction"), "tier");
    }

    static void annotateCapture(UiGalleryBatch batch, JsonObject event) throws Exception {
        if (UiGalleryBatch.flag(batch.row, "actualRemote")) {
            event.add("actualRemoteServer", batch.v208RemoteServerProof);
            event.add("actualRemoteClient", batch.v208RemoteClientProof);
            event.addProperty("remoteScope", "Actual history-row C2S / remote factory / nullable-client-tile builder; same dimension near target; physical cross-dimension travel NOT_RUN");
        }
        if (isNei(batch.row)) {
            com.google.gson.JsonArray handlers = new com.google.gson.JsonArray();
            for (Object handler : (List<?>)UiGalleryBatch.call(batch.q.gui, "getCurrentRecipeHandlers")) {
                JsonObject item = new JsonObject();
                item.addProperty("class", handler.getClass().getName());
                item.addProperty("handlerId", String.valueOf(UiGalleryBatch.call(handler, "getHandlerId")));
                item.addProperty("recipes", batch.q.number(UiGalleryBatch.call(handler, "numRecipes")));
                handlers.add(item);
            }
            event.add("actualNeiHandlers", handlers);
            event.addProperty("rendererMode", "PUBLIC_NEI_RECIPE_SCREEN");
        }
        if (UiGalleryBatch.flag(batch.row, "actualHistory")) {
            Object psm = UiGalleryBatch.call(UiGalleryBatch.call(batch.q.screen, "getSyncManager"), "getMainPSM");
            Object session = UiGalleryBatch.call(psm, "findSyncHandler", "machine_history");
            event.add("actualHistoryStatuses", new com.google.gson.Gson().toJsonTree(UiGalleryBatch.field(session, "statuses")));
            event.addProperty("actualHistoryEntries", ((List<?>)UiGalleryBatch.field(
                UiGalleryBatch.field(session, "data"), "entries")).size());
            event.add("historyNameObservation", historyNameObservation(batch, session));
        }
    }

    static void prepareLiveHistoryTarget(UiGalleryBatch batch) throws Exception {
        batch.v208LiveTarget = batch.auxiliary("CRITICAL_SUN", "v208.live-history-sun");
        batch.currentFixture = batch.v208LiveTarget;
        Object[] target = batch.v208LiveTarget;
        UiGalleryBatch.call(batch.q.serverPlayer, "setPositionAndUpdate|func_70634_a",
            ((Integer)target[1]) + .5, ((Integer)target[2]) + 1d, ((Integer)target[3]) + 2.5);
    }

    /** First pass opens a legal local container; GuiManager replaces it with history on the next tick. */
    static boolean bootstrapHistory(UiGalleryBatch batch) throws Exception {
        if (batch.v208HistoryLocalOpened) {
            // GuiManager.open closes the old server container itself. Sending S2E here makes
            // MUI2's client closeAll response race and close the newly opened history container.
            return false;
        }
        Object[] target = batch.v208LiveTarget;
        if (target == null) throw new AssertionError("Owned live Sun target was not placed");
        Object previous = UiGalleryBatch.field(batch.q.serverPlayer, "openContainer|field_71070_bA");
        batch.q.check("v208.history.live-local-open", Boolean.TRUE.equals(UiGalleryBatch.call(
            UiGalleryBatch.type(G + "common.gui.CriticalMachineTerminalFactory"), "open", batch.q.serverPlayer, target[4])),
            "Real local Critical factory opens the owned live Sun with a held original HubTerminal");
        Object container = UiGalleryBatch.field(batch.q.serverPlayer, "openContainer|field_71070_bA");
        Object data = UiGalleryBatch.call(container, "getGuiData");
        batch.q.check("v208.history.live-local-container", container != previous
            && UiGalleryBatch.call(data, "getTileEntity") == target[4],
            "Actual local container and real target, before automatic history recording is inspected");
        List<?> records = (List<?>)UiGalleryBatch.call(UiGalleryBatch.type(
            G + "common.terminal.MachineTerminalHistory"), "read", batch.q.serverPlayer);
        batch.v208RemoteIndex = -1;
        for (int index = 0; index < records.size(); index++) {
            Object entry = records.get(index);
            if (batch.q.number(UiGalleryBatch.field(entry, "x")) == (Integer)target[1]
                && batch.q.number(UiGalleryBatch.field(entry, "y")) == (Integer)target[2]
                && batch.q.number(UiGalleryBatch.field(entry, "z")) == (Integer)target[3]) {
                batch.q.check("v208.history.live-ready", "ready".equals(String.valueOf(UiGalleryBatch.call(
                    UiGalleryBatch.type(G + "common.terminal.MachineTerminalHistory"), "status", batch.q.serverPlayer, entry))),
                    "Naturally recorded owned target remains loaded, same identity and authorized");
                batch.v208RemoteIndex = index;
                break;
            }
        }
        batch.q.check("v208.history.live-index", batch.v208RemoteIndex >= 0 && batch.v208RemoteIndex < 6,
            "Live Sun original history index=" + batch.v208RemoteIndex);
        JsonObject proof = new JsonObject();
        proof.addProperty("localWindowId", batch.q.number(UiGalleryBatch.field(container, "windowId|field_75152_c")));
        proof.addProperty("historyIndex", batch.v208RemoteIndex);
        proof.addProperty("scope", "Real locally opened live owned Sun; native factory records history; no synthesized entry or Grant");
        batch.q.receipt.add("v208LiveHistoryBootstrap", proof);
        batch.v208HistoryLocalOpened = true;
        return true;
    }

    static void startRemote(UiGalleryBatch batch) throws Exception {
        if (batch.v208LiveTarget == null || !batch.screenReady(false)
            || !"machine_terminal_history".equals(String.valueOf(UiGalleryBatch.call(batch.q.panel, "getName"))))
            throw new AssertionError("Remote route must continue the captured real history panel");
        Object psm = UiGalleryBatch.call(UiGalleryBatch.call(batch.q.screen, "getSyncManager"), "getMainPSM");
        Object session = UiGalleryBatch.call(psm, "findSyncHandler", "machine_history");
        int index = batch.v208RemoteIndex;
        String[] statuses = (String[])UiGalleryBatch.field(session, "statuses");
        if (index < 0 || index >= statuses.length || !"ready".equals(statuses[index])) {
            batch.stage = 21; return;
        }
        int visible = -1;
        for (int row = 0; row < 6; row++)
            if (batch.q.number(UiGalleryBatch.call(session, "index", row)) == index) { visible = row; break; }
        if (visible < 0) throw new AssertionError("Live original index is outside the captured history page");
        Object button = batch.q.find(batch.q.panel, "machine_history_row_" + visible);
        if (button == null) throw new AssertionError("Actual history row button missing");
        batch.lastNavigationProof = batch.hitEvidence(button);
        batch.q.check("v208.remote.actual-history-row-hit",
            UiGalleryBatch.flag(batch.lastNavigationProof, "targetInHoveredChain"), String.valueOf(batch.lastNavigationProof));
        batch.q.receipt.add("v208RemoteHistoryHit", batch.lastNavigationProof);
        batch.q.receipt.add("v208RemoteBeforeClick", waitObservation(batch));
        batch.q.click(button); // Real production Session.request sends its normal C2S index; no direct remote open.
        batch.syncStartNanos = System.nanoTime();
        batch.dispatch(7); batch.stage = 20;
    }

    static boolean observeRemoteServer(UiGalleryBatch batch) throws Exception {
        Object container = UiGalleryBatch.field(batch.q.serverPlayer, "openContainer|field_71070_bA");
        if (!UiGalleryBatch.type("com.cleanroommc.modularui.screen.ModularContainer").isInstance(container)) return false;
        Object data = UiGalleryBatch.call(container, "getGuiData");
        if (!UiGalleryBatch.type(G + "common.terminal.RemoteTerminalGuiData").isInstance(data)
            || !Boolean.TRUE.equals(UiGalleryBatch.field(data, "remote"))) return false;
        Object grant = UiGalleryBatch.field(data, "grant");
        batch.q.check("v208.remote.real-server-grant", grant != null
            && Boolean.TRUE.equals(UiGalleryBatch.call(data, "canUseRemote", batch.q.serverPlayer))
            && UiGalleryBatch.call(data, "getTileEntity") == batch.v208LiveTarget[4],
            "Server-issued production grant resolves the same live owned target; helper never creates a Grant");
        Object psm = UiGalleryBatch.call(UiGalleryBatch.call(container, "getSyncManager"), "getMainPSM");
        batch.q.check("v208.remote.server-psm", Boolean.TRUE.equals(UiGalleryBatch.call(psm, "isInitialised")),
            "Actual remote server PSM initialized");
        batch.windowId = batch.q.number(UiGalleryBatch.field(container, "windowId|field_75152_c"));
        batch.expectedValues = batch.primitives(psm);
        batch.expectedCriticalIdentity = criticalIdentity(psm);
        JsonObject proof = new JsonObject();
        proof.addProperty("windowId", batch.windowId); proof.addProperty("remote", true);
        proof.addProperty("dataClass", data.getClass().getName()); proof.addProperty("grantPresent", true);
        proof.addProperty("grantValid", true); proof.addProperty("targetSameObject", true);
        proof.addProperty("psmInitialized", true); proof.addProperty("historyIndex", batch.v208RemoteIndex);
        proof.addProperty("playerDimension", batch.q.number(UiGalleryBatch.field(batch.q.serverPlayer, "dimension|field_71093_bK")));
        proof.addProperty("targetDimension", batch.q.number(UiGalleryBatch.field(data, "targetDimension")));
        batch.v208RemoteServerProof = proof;
        return true;
    }

    static void verifyRemoteClient(UiGalleryBatch batch) throws Exception {
        Object container = UiGalleryBatch.field(batch.player(), "openContainer|field_71070_bA");
        Object data = UiGalleryBatch.call(container, "getGuiData");
        JsonObject packet = batch.q.wire.lastMuiOpen;
        batch.q.check("v208.remote.actual-factory-packet", packet != null
            && "gtsr:remote_machine_terminal".equals(packet.get("factory").getAsString())
            && packet.get("windowId").getAsInt() == batch.windowId,
            "Actual S2C factory header and matching remote window: " + packet);
        batch.q.check("v208.remote.client-null-target", Boolean.TRUE.equals(UiGalleryBatch.field(data, "remote"))
            && UiGalleryBatch.call(data, "getTileEntity") == null && UiGalleryBatch.field(data, "grant") == null,
            "Real remote client GuiData deliberately has no TileEntity and no serialized grant");
        Object psm = UiGalleryBatch.call(UiGalleryBatch.call(batch.q.screen, "getSyncManager"), "getMainPSM");
        batch.q.check("v208.remote.matched-client-psm-window",
            Boolean.TRUE.equals(UiGalleryBatch.call(psm, "isInitialised"))
            && batch.q.number(UiGalleryBatch.field(container, "windowId|field_75152_c")) == batch.windowId,
            "Remote client window/PSM matches the actual server-issued remote container");
        JsonObject proof = new JsonObject();
        proof.add("actualOpenGuiPacket", packet); proof.addProperty("windowId", batch.windowId);
        proof.addProperty("remote", true); proof.addProperty("getTileEntityNull", true);
        proof.addProperty("grantNull", true); proof.addProperty("psmInitialized", true);
        proof.addProperty("dataClass", data.getClass().getName());
        proof.addProperty("scope", "Same-dimension nearby actual remote route; no physical cross-dimension travel claim");
        batch.v208RemoteClientProof = proof;
    }

    static void localClick(UiGalleryBatch batch, String name) throws Exception {
        if (!name.matches("critical_details_1[0-3]") && !name.equals("critical_runtime_disclosure")
            && !name.matches("critical_economy_family_(controller|equipment|S|H|E|O|F|M|C|G)"))
            throw new IllegalArgumentException("v208 local display-navigation whitelist only: " + name);
        Object button = batch.q.find(batch.q.panel, name);
        if (button == null) throw new AssertionError("Local display control missing " + name);
        // Reveal through each real scroll ancestor before the actual ModularScreen hit-test.
        for (Object parent = UiGalleryBatch.call(button, "getParent"); parent != null;
             parent = UiGalleryBatch.call(parent, "getParent")) {
            if (!UiGalleryBatch.type("com.cleanroommc.modularui.widget.AbstractScrollWidget").isInstance(parent)) continue;
            Object scroll = UiGalleryBatch.call(parent, "getScrollArea");
            Object area = UiGalleryBatch.call(parent, "getArea");
            for (String axis : new String[] { "X", "Y" }) {
                Object data = UiGalleryBatch.call(scroll, "getScroll" + axis);
                if (data == null) continue;
                int coordinate = axis.equals("X") ? 0 : 1;
                int[] target = batch.q.screenPoint(button), viewport = batch.q.screenPoint(parent);
                int viewportSize = batch.q.number(UiGalleryBatch.field(area, coordinate == 0 ? "width" : "height"));
                int visible = batch.q.number(UiGalleryBatch.call(data, "getFullVisibleSize", scroll));
                int start = viewport[coordinate] - viewportSize / 2;
                int size = batch.q.number(UiGalleryBatch.field(UiGalleryBatch.call(button, "getArea"),
                    coordinate == 0 ? "width" : "height"));
                int low = target[coordinate] - size / 2, high = target[coordinate] + size / 2;
                int delta = low < start ? low - start : high > start + visible ? high - start - visible : 0;
                if (delta != 0) UiGalleryBatch.call(data, "scrollTo", scroll,
                    batch.q.number(UiGalleryBatch.call(data, "getScroll")) + delta);
                UiGalleryBatch.call(batch.q.screen, "onFrameUpdate");
            }
        }
        batch.lastNavigationProof = batch.hitEvidence(button);
        batch.q.check("v208.actual-local-hit." + batch.row.get("name").getAsString() + "." + name,
            UiGalleryBatch.flag(batch.lastNavigationProof, "targetInHoveredChain"), String.valueOf(batch.lastNavigationProof));
        batch.localClickProofs.add(batch.lastNavigationProof);
        batch.q.click(button);
    }

    static boolean open(UiGalleryBatch batch, String visual) throws Exception {
        if (visual.equals("nei")) {
            openNei(batch);
            return true;
        }
        if (visual.equals("critical_tooltip_fontproof")) {
            openTooltipFontProof(batch);
            return true;
        }
        return false;
    }

    private static void openNei(UiGalleryBatch batch) throws Exception {
        String kind = UiGalleryBatch.str(batch.row, "neiKind", "LOOM");
        Object maps = UiGalleryBatch.type(G + "api.recipe.CriticalRecipeMaps");
        Object map = kind.equals("LOOM") ? UiGalleryBatch.field(maps, "construction")
            : kind.equals("IRRADIATION") ? UiGalleryBatch.field(maps, "stellarIrradiation")
            : UiGalleryBatch.call(maps, "forKind", Enum.valueOf((Class)UiGalleryBatch.type(
                G + "common.critical.CriticalMachineKind"), kind));
        String id = String.valueOf(UiGalleryBatch.field(map, "unlocalizedName"));
        batch.q.check("v208.nei.public-entry." + kind,
            Boolean.TRUE.equals(UiGalleryBatch.call(UiGalleryBatch.type("codechicken.nei.recipe.GuiCraftingRecipe"),
                "openRecipeGui", id, new Object[0])), "Actual public GuiCraftingRecipe.openRecipeGui(" + id + ")");
        JsonObject proof = new JsonObject();
        proof.addProperty("route", "GuiCraftingRecipe.openRecipeGui");
        proof.addProperty("outputIdFromProductionRecipeMap", id);
        proof.addProperty("scope", "Real registered NEI handler and recipe screen; no fabricated recipe drawing");
        batch.q.receipt.add("v208NeiEntry." + kind, proof);
    }

    static void openHistory(UiGalleryBatch batch) throws Exception {
        Object history = UiGalleryBatch.type(G + "common.terminal.MachineTerminalHistory");
        List<?> records = (List<?>)UiGalleryBatch.call(history, "read", batch.q.serverPlayer);
        batch.q.check("v208.history.real-recorded-machines", records.size() >= 2,
            "Same owned HubTerminal records from real loom/Sun open routes, entries=" + records.size());
        UiGalleryBatch.call(UiGalleryBatch.type(G + "common.terminal.MachineHistoryFactory"), "open", batch.q.serverPlayer);
        batch.q.receipt.addProperty("v208HistoryScope",
            "Real server factory with held owned HubTerminal and naturally recorded fixtures; no history-row request or remote grant");
        batch.q.receipt.addProperty("v208HistoryRecordedEntries", records.size());
    }

    private static void openTooltipFontProof(UiGalleryBatch batch) throws Exception {
        String kind = UiGalleryBatch.str(batch.row, "tooltipKind", "SUN");
        boolean shifted = UiGalleryBatch.flag(batch.row, "tooltipShift");
        String itemEntry = kind.equals("LOOM") ? "CriticalLoom"
            : "Critical" + kind.substring(0, 1) + kind.substring(1).toLowerCase(java.util.Locale.ROOT);
        Object stack = UiGalleryBatch.call(UiGalleryBatch.field(UiGalleryBatch.type(
            G + "common.api.enums.GTSRItemList"), itemEntry), "get", 1L, new Object[0]);
        Object item = UiGalleryBatch.call(stack, "getItem|func_77973_b");
        List<String> lines = new java.util.ArrayList<String>();
        lines.add(String.valueOf(UiGalleryBatch.call(stack, "getDisplayName|func_82833_r")));
        // Read the real registered item's GT description before Forge's keyboard-selected tooltip event.
        UiGalleryBatch.call(item, "addInformation|func_77624_a", stack, batch.player(), lines, false);
        int originalLineCount = lines.size();
        boolean replaced = Boolean.TRUE.equals(UiGalleryBatch.call(UiGalleryBatch.type(
            G + "client.tooltip.GTSRMachineTooltips"), "rewrite", stack, lines, shifted));
        batch.q.check("v208.tooltip.production-layering." + kind + "." + (shifted ? "details" : "default"), replaced,
            "Actual GTSRItemList." + itemEntry + " -> ItemMachines.addInformation -> GTSRMachineTooltips.rewrite");
        UiGalleryBatch.call(UiGalleryBatch.type(G + "client.tooltip.GTSRTooltipHandler"), "normalizeBrand", lines,
            UiGalleryBatch.call(UiGalleryBatch.type(G + "common.util.GTSRUtils"), "getAddedByLine"));
        Object keyType = UiGalleryBatch.type("com.cleanroommc.modularui.api.drawable.IKey");
        Object panel = batch.construct("com.cleanroommc.modularui.screen.ModularPanel", "v208_tooltip_fontproof");
        UiGalleryBatch.call(panel, "size", 612, 338);
        Object backgrounds = Array.newInstance(UiGalleryBatch.type("com.cleanroommc.modularui.api.drawable.IDrawable"), 1);
        Array.set(backgrounds, 0, UiGalleryBatch.field(UiGalleryBatch.type(G + "common.gui.mui2.TerminalUiStyle"), "HUB_PANEL"));
        UiGalleryBatch.call(panel, "background", backgrounds);
        Object title = UiGalleryBatch.call(UiGalleryBatch.call(keyType, "str", "FONT RENDER PROOF | " + kind
            + (shifted ? " | production details page" : " | production overview page")), "asWidget");
        UiGalleryBatch.call(title, "pos", 10, 8); UiGalleryBatch.call(title, "size", 592, 12);
        UiGalleryBatch.call(title, "color", 0xFFF0E8D8);
        UiGalleryBatch.call(panel, "child", title);
        Object list = batch.construct("com.cleanroommc.modularui.widgets.ListWidget");
        UiGalleryBatch.call(list, "name", "v208_tooltip_fontproof_text");
        UiGalleryBatch.call(list, "pos", 10, 26); UiGalleryBatch.call(list, "size", 592, 300);
        UiGalleryBatch.call(list, "scrollDirection", UiGalleryBatch.call(
            UiGalleryBatch.type(G + "common.gui.CriticalGuiStyle"), "scroll"));
        StringBuilder joined = new StringBuilder();
        for (String line : lines) { if (joined.length() > 0) joined.append('\n'); joined.append(line); }
        Object text = UiGalleryBatch.call(UiGalleryBatch.type(G + "common.gui.CriticalGuiStyle"), "text",
            UiGalleryBatch.call(keyType, "str", joined.toString()));
        UiGalleryBatch.call(text, "color", 0xFFD8D4C8);
        UiGalleryBatch.call(text, "scale", .75f); UiGalleryBatch.call(text, "fullWidth");
        UiGalleryBatch.call(list, "child", text); UiGalleryBatch.call(panel, "child", list);
        UiGalleryBatch.call(UiGalleryBatch.type("com.cleanroommc.modularui.factory.ClientGUI"), "open",
            batch.construct("com.cleanroommc.modularui.screen.ModularScreen", "gtsr", panel));
        JsonObject proof = new JsonObject();
        proof.addProperty("productionMethod", "ItemMachines.addInformation -> GTSRMachineTooltips.rewrite(details="
            + shifted + ") -> GTSRTooltipHandler.normalizeBrand");
        proof.addProperty("registeredItem", "GTSRItemList." + itemEntry);
        proof.addProperty("itemMeta", ((Number)UiGalleryBatch.call(stack, "getItemDamage|func_77960_j")).intValue());
        proof.addProperty("productionLayerReplaced", replaced);
        proof.addProperty("originalItemLineCount", originalLineCount);
        proof.addProperty("completeProductionPage", true);
        proof.addProperty("viewport", "Real scrollable ListWidget; complete wrapped production page, no truncation");
        proof.addProperty("scope", "Real registered item / production layering / client font renderer; page selected by explicit API argument, no item-hover or physical Shift keyboard claim");
        proof.addProperty("physicalKeyboard", "NOT_RUN");
        proof.addProperty("lineCount", lines.size());
        proof.add("lines", new com.google.gson.Gson().toJsonTree(lines));
        batch.q.receipt.add("v208TooltipFontProof." + (shifted ? "shift" : "default"), proof);
    }
}
