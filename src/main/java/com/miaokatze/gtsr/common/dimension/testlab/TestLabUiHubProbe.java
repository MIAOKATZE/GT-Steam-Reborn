package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtsr.common.gui.mui2.hub.HubTerminalSession;
import com.miaokatze.gtsr.common.gui.mui2.hub.HubTerminalViewState;
import com.miaokatze.gtsr.common.terminal.CacheHubTerminalData;
import com.miaokatze.gtsr.common.terminal.SingularityHubTerminalData;

import io.netty.buffer.Unpooled;

/** DTO fixture through production codecs/session/update listener; no fabricated route or world-node mutation. */
final class TestLabUiHubProbe {

    private TestLabUiHubProbe() {}

    static void inspect(ModularPanel panel, TestLabUiSnapshot.Manager manager, Map<String, Object> details,
        List<String> failures) throws Exception {
        if (!(manager.handlers.get(HubTerminalSession.SYNC_KEY + ":0") instanceof HubTerminalSession session)) {
            failures.add("Missing production hub_session:0 handler");
            return;
        }
        HubTerminalViewState view = session.view;
        List<IWidget> before = widgets(panel);
        Map<String, SyncHandler<?>> beforeHandlers = new LinkedHashMap<>(manager.handlers);
        List<Map<String, Object>> samples = new ArrayList<>();
        details.put("terminalStateSamples", samples);
        details.put(
            "terminalStateSource",
            "TEST_DTO_FIXTURE: production Data.write -> Session.readOnClient -> actual panel onUpdate; authoritative world assembly NOT_RUN");
        int beforeFailures = failures.size();
        long revision = view.revision;
        // Each new production panel starts with an empty view. Do not overwrite a live session.
        if (view.total() != 0 || revision != 0)
            throw new IllegalStateException("Hub probe expected isolated fresh view");
        try {
            int[] counts = view.nearby ? new int[] { 1, 0, 1 } : new int[] { 0, 1, 32, 33, 256, 255, 1, 0, 256, 0 };
            for (int i = 0; i < counts.length; i++) {
                int count = counts[i];
                byte[] payload = payload(count, view.drilling, i);
                deliver(session, ++revision, payload);
                update(before);
                int decoded = view.drilling ? SingularityHubTerminalData.readList(payload)
                    .size()
                    : CacheHubTerminalData.readList(payload)
                        .size();
                int enabled = 0;
                for (IWidget widget : before) {
                    if (widget.getName() != null && widget.getName()
                        .startsWith("hub_row_") && widget.isEnabled()) enabled++;
                }
                String rawDisplay = text(before, view.nearby ? "cache_node_storage" : "hub_page");
                String display = rawDisplay == null ? null : rawDisplay.replaceAll("(?i)§[0-9A-FK-OR]", "");
                String expected = view.nearby ? count == 0 ? "—" : (123L + i) + " / 20000000000 L"
                    : "1 / " + Math.max(1, (count + 31) / 32) + "  (" + count + " / " + count + ")";
                boolean stable = before.equals(widgets(panel)) && beforeHandlers.equals(manager.handlers);
                boolean state = decoded == count && view.total() == count
                    && view.filtered() == count
                    && (view.nearby || enabled == Math.min(32, count))
                    && expected.equals(display);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("state", "list_state_" + i);
                row.put("sourceDtoCount", count);
                row.put("decodedCount", decoded);
                row.put("productionViewCount", view.total());
                row.put("enabledProductionRows", enabled);
                row.put("productionText", rawDisplay);
                row.put("productionPlainText", display);
                row.put("payloadBytes", payload.length);
                row.put("samePanelWidgetsAndSyncHandlers", stable);
                row.put("decodedViewDisplayEqual", state);
                samples.add(row);
                if (!stable || !state) failures.add("Hub production DTO/session/view/widget mismatch state " + i);
            }
            int lastTotal = view.total();
            deliver(session, revision - 1, payload(1, view.drilling, 0));
            update(before);
            details.put("terminalOldRevisionIgnored", view.total() == lastTotal && view.revision == revision);
            if (view.total() != lastTotal || view.revision != revision)
                failures.add("Hub old revision changed current view");
        } finally {
            // Restore the fresh, empty isolated view after all packets have been consumed.
            deliver(session, revision + 1, payload(0, view.drilling, 0));
            update(before);
            view.revision = 0;
            details.put("terminalViewRestored", view.total() == 0 && view.selected == null);
            if (view.total() != 0 || view.selected != null) failures.add("Hub probe view restoration failed");
        }
        details.put(
            "terminalStateEvidence",
            failures.size() == beforeFailures
                ? "PASS: same production panel DTO/codec/session/update/text and row-visibility checks; old revision ignored"
                : "FAIL: hub production state checks");
        details.put(
            "terminalActionEvidence",
            "NOT_RUN: factory route, real permissions, C2S actions and network transport");
    }

    private static byte[] payload(int count, boolean drilling, int state) {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writeVarIntToBuffer(count);
            for (int i = 0; i < count; i++) {
                if (drilling) SingularityHubTerminalData.write(
                    packet,
                    new SingularityHubTerminalData.HubNodeInfo(
                        i,
                        64,
                        i + 1,
                        0,
                        i % 2 == 0,
                        1,
                        state % 2 == 0,
                        true,
                        false,
                        true,
                        "Probe node " + i));
                else CacheHubTerminalData.write(
                    packet,
                    new CacheHubTerminalData.CacheNodeInfo(
                        i,
                        64,
                        i + 1,
                        0,
                        "steam",
                        "Probe node " + i,
                        "steam",
                        123L + state,
                        20000000000L,
                        100,
                        100,
                        false,
                        false,
                        false,
                        1000,
                        2000,
                        20000000000L));
            }
            byte[] result = new byte[packet.readableBytes()];
            packet.readBytes(result);
            return result;
        } finally {
            packet.release();
        }
    }

    private static void deliver(HubTerminalSession session, long revision, byte[] payload) throws Exception {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writeLong(revision);
            packet.writeInt(payload.length);
            packet.writeBytes(payload);
            session.readOnClient(0, packet);
        } finally {
            packet.release();
        }
    }

    @SuppressWarnings("unchecked")
    private static void update(List<IWidget> widgets) {
        for (IWidget widget : widgets) {
            if (!(widget instanceof Widget<?>actual)) continue;
            Consumer<IWidget> listener = (Consumer<IWidget>) (Consumer<?>) actual.getOnUpdateListener();
            if (listener != null) listener.accept(widget);
        }
    }

    private static String text(List<IWidget> widgets, String name) {
        for (IWidget widget : widgets) {
            if (name.equals(widget.getName()) && widget instanceof TextWidget<?>text) return text.getKey()
                .getFormatted();
        }
        return null;
    }

    private static List<IWidget> widgets(IWidget root) {
        List<IWidget> widgets = new ArrayList<>();
        widgets.add(root);
        for (IWidget child : root.getChildren()) widgets.addAll(widgets(child));
        return widgets;
    }
}
