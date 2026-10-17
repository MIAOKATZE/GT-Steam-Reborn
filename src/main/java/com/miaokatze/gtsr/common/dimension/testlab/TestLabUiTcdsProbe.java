package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtsr.common.gui.mui2.TcdsTerminalPanel;
import com.miaokatze.gtsr.common.machine.tcds.MTEThermoChemicalDenseSteamGenerator;
import com.miaokatze.gtsr.common.terminal.TcdsTerminalData;

import io.netty.buffer.Unpooled;

/** Same production panel, source -> codec -> production session -> actual dynamic text. Never opens a GUI. */
final class TestLabUiTcdsProbe {

    private TestLabUiTcdsProbe() {}

    static void inspect(ModularPanel panel, TestLabUiSnapshot.Manager manager,
        MTEThermoChemicalDenseSteamGenerator machine, Map<String, Object> details, List<String> failures)
        throws Exception {
        SyncHandler<?> handler = manager.handlers.get(TcdsTerminalPanel.SYNC_KEY + ":0");
        if (!(handler instanceof TcdsTerminalPanel.Session session)) {
            failures.add("Missing production tcds_session:0 handler");
            return;
        }
        String[] sourceNames = { "mFlow", "mCurrentOutputEquivalent", "mCurrentFuelKind", "mCurrentFuelConsumption",
            "mCurrentGasConsumption", "mCurrentLiquidConsumption", "mCurrentAirConsumption", "mCurrentWaterConsumption",
            "mCurrentGasHeatValue", "mCurrentLiquidHeatValue", "mHeat" };
        Field[] sourceFields = new Field[sourceNames.length];
        Object[] original = new Object[sourceNames.length];
        for (int i = 0; i < sourceFields.length; i++) {
            sourceFields[i] = machine.getClass()
                .getField(sourceNames[i]);
            original[i] = sourceFields[i].get(machine);
        }
        Field cache = TcdsTerminalPanel.Session.class.getDeclaredField("snapshot");
        cache.setAccessible(true);
        Object originalCache = cache.get(handler);
        List<IWidget> before = widgets(panel);
        Map<String, SyncHandler<?>> beforeHandlers = new LinkedHashMap<>(manager.handlers);
        TextWidget<?> flow = textValue(before, "gtsr.tcds_terminal.flow_label");
        if (flow == null) failures.add("Missing actual TCDS flow dynamic text widget");
        List<Map<String, Object>> samples = new ArrayList<>();
        details.put("terminalStateSamples", samples);
        int beforeFailures = failures.size();
        int[][] states = { { 1, 0, 0 }, { 100, 0, 0 }, { 500, 1, 300 }, { 1000, 2, 500 }, { 2000, 3, 700 },
            { 3000, 1, 0 }, { 4000, 2, 0 }, { 8000, 3, 900 }, { Integer.MAX_VALUE, 3, 1000 }, { 100, 0, 0 } };
        try {
            for (int index = 0; index < states.length; index++) {
                int[] state = states[index];
                machine.mFlow = state[0];
                machine.mCurrentFuelKind = state[1];
                machine.mCurrentOutputEquivalent = state[2];
                machine.mCurrentFuelConsumption = state[1] == 1 || state[1] == 2 ? 17 + index : 0;
                machine.mCurrentGasConsumption = state[1] == 3 ? 19 + index : 0;
                machine.mCurrentLiquidConsumption = state[1] == 3 ? 23 + index : 0;
                machine.mCurrentAirConsumption = state[2] > 0 ? 29 + index : 0;
                machine.mCurrentWaterConsumption = state[2] > 0 ? 31 + index : 0;
                machine.mCurrentGasHeatValue = state[1] == 1 || state[1] == 3 ? 800 : 0;
                machine.mCurrentLiquidHeatValue = state[1] == 2 || state[1] == 3 ? 1200 : 0;
                machine.mHeat = index == 9 ? 0 : index * 25.0;
                byte[] payload = TcdsTerminalData.assembleSnapshot(machine);
                TcdsTerminalData.Snapshot decoded = TcdsTerminalData.readSnapshot(payload);
                if (decoded == null) throw new IllegalStateException("Production TCDS snapshot codec rejected source");
                PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
                try {
                    packet.writeInt(payload.length);
                    packet.writeBytes(payload);
                    // The base @SideOnly(CLIENT) method is stripped on dedicated; this concrete
                    // common-side production override owns the decoder being exercised.
                    session.readOnClient(0, packet);
                } finally {
                    packet.release();
                }
                TcdsTerminalData.Snapshot actual = (TcdsTerminalData.Snapshot) cache.get(handler);
                boolean equal = snapshotsEqual(decoded, actual);
                boolean sourceEqual = decoded.flow == machine.getFlow()
                    && decoded.output == machine.mCurrentOutputEquivalent
                    && decoded.fuelKind == machine.mCurrentFuelKind
                    && decoded.fuelConsumption == machine.mCurrentFuelConsumption
                    && decoded.gasConsumption == machine.mCurrentGasConsumption
                    && decoded.liquidConsumption == machine.mCurrentLiquidConsumption
                    && decoded.airConsumption == machine.mCurrentAirConsumption
                    && decoded.waterConsumption == machine.mCurrentWaterConsumption
                    && decoded.gasHeatValue == machine.mCurrentGasHeatValue
                    && decoded.liquidHeatValue == machine.mCurrentLiquidHeatValue
                    && Double.compare(decoded.heat, machine.mHeat) == 0
                    && Double.compare(
                        decoded.efficiency,
                        MTEThermoChemicalDenseSteamGenerator.flowEfficiency(machine.getFlow())) == 0
                    && decoded.heatCap.equals(machine.heatCapDisplay());
                String rawDisplay = flow == null ? null
                    : flow.getKey()
                        .getFormatted();
                String display = rawDisplay == null ? null : rawDisplay.replaceAll("(?i)§[0-9A-FK-OR]", "");
                boolean displayed = flow != null && display.equals(machine.getFlow() + " L/t");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("state", index == 9 ? "recovered_stopped" : "flow_fuel_state_" + index);
                row.put("flow", machine.getFlow());
                row.put("fuelKind", machine.mCurrentFuelKind);
                row.put("payloadBytes", payload.length);
                row.put("sourceCodecEqual", sourceEqual);
                row.put("decodedProductionSessionEqual", equal);
                row.put("productionFlowText", rawDisplay);
                row.put("productionFlowPlainText", display);
                row.put("displayEqual", displayed);
                row.put("samePanelAndWidgets", before.equals(widgets(panel)));
                row.put("sameSyncHandlers", beforeHandlers.equals(manager.handlers));
                samples.add(row);
                if (!sourceEqual || !equal || !displayed)
                    failures.add("TCDS source/session/display mismatch state " + index);
                if (!before.equals(widgets(panel)) || !beforeHandlers.equals(manager.handlers))
                    failures.add("TCDS panel widget/sync identity changed state " + index);
            }
            // A truncated production packet must keep the last complete snapshot on the same panel.
            Object last = cache.get(handler);
            byte[] payload = TcdsTerminalData.assembleSnapshot(machine);
            PacketBuffer malformed = new PacketBuffer(Unpooled.buffer());
            try {
                malformed.writeInt(payload.length);
                malformed.writeBytes(Arrays.copyOf(payload, payload.length - 1));
                session.readOnClient(0, malformed);
            } finally {
                malformed.release();
            }
            details.put("terminalTruncatedPacketPreservesCache", cache.get(handler) == last);
            if (cache.get(handler) != last) failures.add("Truncated TCDS snapshot replaced production session cache");
        } finally {
            for (int i = 0; i < sourceFields.length; i++) sourceFields[i].set(machine, original[i]);
            cache.set(handler, originalCache);
            boolean restored = cache.get(handler) == originalCache;
            for (int i = 0; i < sourceFields.length; i++) restored &= original[i].equals(sourceFields[i].get(machine));
            details.put("terminalSourceAndSessionRestored", restored);
            if (!restored) failures.add("TCDS isolated fixture source/session restoration failed");
        }
        details.put(
            "terminalStateEvidence",
            failures.size() == beforeFailures
                ? "PASS: ten same-panel source/codec/production-session/dynamic-text states and truncated packet rejection"
                : "FAIL: TCDS same-panel state checks");
        details.put(
            "terminalActionEvidence",
            "NOT_RUN: no C2S action, real route, permission or actual network transport exercised");
    }

    private static boolean snapshotsEqual(TcdsTerminalData.Snapshot left, TcdsTerminalData.Snapshot right)
        throws IllegalAccessException {
        if (right == null) return false;
        for (Field field : TcdsTerminalData.Snapshot.class.getFields()) {
            if (!field.get(left)
                .equals(field.get(right))) return false;
        }
        return true;
    }

    private static TextWidget<?> textValue(List<IWidget> widgets, String rowName) {
        for (IWidget widget : widgets) {
            if (rowName.equals(widget.getName()) && widget.getChildren()
                .size() == 2
                && widget.getChildren()
                    .get(1) instanceof TextWidget<?>text)
                return text;
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
