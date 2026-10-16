package com.miaokatze.gtsr.common.gui.mui2;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.scroll.HorizontalScrollData;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.machine.tcds.MTEThermoChemicalDenseSteamGenerator;
import com.miaokatze.gtsr.common.terminal.TcdsTerminalData;
import com.miaokatze.gtsr.common.terminal.TerminalServerSessions;

/** One persistent widget tree and one snapshot consumer per opened TCDS session. */
public final class TcdsTerminalPanel {

    public static final String SYNC_KEY = "tcds_session";

    private TcdsTerminalPanel() {}

    public static ModularPanel build(PosGuiData data, MTEThermoChemicalDenseSteamGenerator machine,
        PanelSyncManager manager) {
        Session session = new Session(data, machine);
        manager.syncValue(SYNC_KEY, session);
        TextFieldWidget input = new TextFieldWidget().name("tcds_flow_input")
            .size(150, 20)
            .setMaxLength(11)
            .autoUpdateOnChange(true)
            .value(new StringValue.Dynamic(session::draft, value -> {
                session.draft = value;
                session.edited = true;
            }))
            .addTooltipLine(IKey.lang("gtsr.tcds_terminal.mui2.input_hint"));
        Flow content = Flow.col()
            .name("tcds_content")
            .size(376, 244)
            .childPadding(2);
        content.child(row("gtsr.tcds_terminal.flow_label", session, s -> s.flow + " L/t"));
        content.child(row("gtsr.tcds_terminal.heat_cap_label", session, s -> s.heatCap));
        content.child(
            Flow.row()
                .name("tcds_input_row")
                .size(376, 22)
                .childPadding(8)
                .child(input)
                .child(
                    TerminalUiStyle.button("gtsr.tcds_terminal.confirm")
                        .name("tcds_apply")
                        .size(85, 20)
                        .background(
                            new DynamicDrawable(
                                () -> session.awaiting ? TerminalUiStyle.DISABLED : TerminalUiStyle.BUTTON))
                        .hoverBackground(
                            new DynamicDrawable(
                                () -> session.awaiting ? TerminalUiStyle.DISABLED : TerminalUiStyle.HOVER))
                        .onMousePressed(button -> {
                            if (button == 0) session.submit(input.getText());
                            return true;
                        })
                        .addTooltipLine(IKey.lang("gtsr.tcds_terminal.confirm_hint")))
                .child(
                    TerminalUiStyle.button("gtsr.tcds_terminal.formula_help")
                        .name("tcds_formula_help")
                        .size(116, 20)
                        .tooltipBuilder(tooltip -> {
                            for (String key : new String[] { "flow", "efficiency", "output", "cap", "consumption",
                                "shortage", "buffer", "chip" })
                                tooltip.addLine(IKey.lang("gtsr.tcds_terminal.formula_" + key));
                        })));
        content.child(row("gtsr.tcds_terminal.mui2.heat", session, s -> number(s.heat) + "%"));
        content.child(row("gtsr.tcds_terminal.mui2.efficiency", session, s -> number(s.efficiency * 100) + "%"));
        content.child(row("gtsr.tcds_terminal.kv_actual_flow", session, s -> s.fuelConsumption + " L/t"));
        content.child(row("gtsr.tcds_terminal.kv_output_total", session, s -> s.output + " L/t"));
        content.child(row("gtsr.tcds_terminal.kv_fuel", session, s -> s.fuelConsumption + " L/t"));
        content.child(
            row(
                "gtsr.tcds_terminal.derive_gas",
                session,
                s -> familyFlow(s, true) + " L/t → " + familyTerm(s, true) + " L/t")
                    .addTooltipLine(IKey.dynamic(() -> familyEquation(session.snapshot, true))));
        content.child(
            row(
                "gtsr.tcds_terminal.derive_liquid",
                session,
                s -> familyFlow(s, false) + " L/t → " + familyTerm(s, false) + " L/t")
                    .addTooltipLine(IKey.dynamic(() -> familyEquation(session.snapshot, false))));
        content.child(row("gtsr.tcds_terminal.kv_air", session, s -> s.airConsumption + " L/t"));
        content.child(row("gtsr.tcds_terminal.kv_water", session, s -> s.waterConsumption + " L/t"));
        content.child(
            Flow.row()
                .name("tcds_status_row")
                .size(376, 14)
                .childPadding(6)
                .child(
                    IKey.dynamicKey(
                        () -> IKey.lang(
                            session.snapshot != null
                                && (session.snapshot.airConsumption > 0 || session.snapshot.waterConsumption > 0)
                                    ? "gtsr.tcds_terminal.status_ok"
                                    : "gtsr.tcds_terminal.status_stopped"))
                        .asWidget()
                        .name("tcds_status")
                        .size(154, 14)
                        .color(GtsrGuiPalette.TEXT_BODY))
                .child(
                    IKey.dynamic(
                        () -> session.snapshot == null ? "—"
                            : session.snapshot.output + " ÷ 1000 = " + session.snapshot.output / 1000)
                        .asWidget()
                        .name("tcds_chip_output")
                        .size(216, 14)
                        .color(GtsrGuiPalette.TEXT_BODY)));
        content.child(IKey.dynamicKey(() -> {
            session.consumeResult();
            return IKey.lang(session.receiptKey);
        })
            .asWidget()
            .name("tcds_receipt")
            .size(376, 16)
            .color(GtsrGuiPalette.TEXT_ACCENT));
        // Keep the entire widget tree on both sides. Only the client viewport changes size.
        // The horizontal axis also preserves access below the normal 427px minimum canvas width.
        HorizontalScrollData horizontal = new HorizontalScrollData(false, 4);
        horizontal.setScrollSize(376);
        horizontal.setCancelScrollEdge(false);
        ListWidget<?, ?> body = new ListWidget<>().name("tcds_body_viewport")
            .pos(12, 30)
            .widthRelOffset(1f, -16)
            .heightRelOffset(1f, -42)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .scrollDirection(new VerticalScrollData(false, 4).cancelScrollEdge(false))
            .child(content);
        body.getScrollArea()
            .setScrollDataX(horizontal);
        body.onUpdateListener(widget -> {
            horizontal.clamp(body.getScrollArea());
            body.getScrollData()
                .clamp(body.getScrollArea());
        });
        return ModularPanel.defaultPanel(TcdsTerminalFactory.PANEL_NAME, 400, 286)
            .background(TerminalUiStyle.PANEL)
            .child(
                IKey.lang("gtsr.tcds_terminal.title")
                    .asWidget()
                    .name("tcds_title")
                    .pos(12, 10)
                    .size(376, 16)
                    .color(GtsrGuiPalette.TEXT_TITLE))
            .child(body);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int familyFlow(TcdsTerminalData.Snapshot snapshot, boolean gas) {
        if (snapshot.fuelKind == 3) return gas ? snapshot.gasConsumption : snapshot.liquidConsumption;
        return snapshot.fuelKind == (gas ? 1 : 2) ? snapshot.fuelConsumption : 0;
    }

    private static long familyTerm(TcdsTerminalData.Snapshot snapshot, boolean gas) {
        return Math.round(
            familyFlow(snapshot, gas) * (double) (gas ? snapshot.gasHeatValue : snapshot.liquidHeatValue)
                * snapshot.efficiency
                * 2.0d
                * snapshot.heat
                / 100.0d);
    }

    private static String familyEquation(TcdsTerminalData.Snapshot snapshot, boolean gas) {
        if (snapshot == null) return "—";
        return familyFlow(snapshot, gas) + " × "
            + (gas ? snapshot.gasHeatValue : snapshot.liquidHeatValue)
            + " × "
            + String.format(Locale.ROOT, "%.3f", snapshot.efficiency)
            + " × 2 × "
            + number(snapshot.heat)
            + "% = "
            + familyTerm(snapshot, gas)
            + " L/t";
    }

    private static Flow row(String key, Session session, Function<TcdsTerminalData.Snapshot, String> value) {
        return Flow.row()
            .name(key)
            .size(376, 14)
            .childPadding(6)
            .child(
                IKey.lang(key)
                    .asWidget()
                    .size(154, 14)
                    .color(GtsrGuiPalette.TEXT_LABEL))
            .child(
                IKey.dynamic(() -> session.snapshot == null ? "—" : value.apply(session.snapshot))
                    .asWidget()
                    .size(216, 14)
                    .color(GtsrGuiPalette.TEXT_BODY));
    }

    /** Wire ids: 0 snapshot, 1 apply(seq,int), 2 result(seq,accepted,int). All keys are explicit. */
    public static final class Session extends SyncHandler<Session> {

        private final PosGuiData data;
        private final MTEThermoChemicalDenseSteamGenerator machine;
        private final TileEntity tile;
        private final AtomicBoolean pending = new AtomicBoolean();
        private volatile TcdsTerminalData.Snapshot snapshot;
        private volatile String receiptKey = "gtsr.tcds_terminal.mui2.ready";
        private String draft = "";
        private boolean edited;
        private boolean awaiting;
        private String submittedDraft;
        private volatile Result result;
        private int sequence, appliedSequence;
        private long lastPoll = Long.MIN_VALUE;
        private byte[] lastSnapshot;

        Session(PosGuiData data, MTEThermoChemicalDenseSteamGenerator machine) {
            this.data = data;
            this.machine = machine;
            this.tile = data.getTileEntity();
            allowC2S();
        }

        private String draft() {
            consumeResult();
            return !edited && snapshot != null ? Integer.toString(snapshot.flow) : draft;
        }

        private void consumeResult() {
            Result reply = result;
            if (reply == null) return;
            result = null;
            if (reply.sequence != sequence) return;
            awaiting = false;
            receiptKey = reply.accepted ? "gtsr.tcds_terminal.mui2.applied" : "gtsr.tcds_terminal.mui2.rejected";
            if (reply.accepted && draft.equals(submittedDraft)) {
                draft = Integer.toString(reply.flow);
                edited = true;
            }
        }

        private void submit(String text) {
            consumeResult();
            if (awaiting) return;
            final int flow;
            try {
                flow = Integer.parseInt(text.trim());
            } catch (NumberFormatException error) {
                receiptKey = "gtsr.tcds_terminal.mui2.invalid";
                return;
            }
            int request = ++sequence;
            draft = text;
            submittedDraft = text;
            awaiting = true;
            edited = true;
            receiptKey = "gtsr.tcds_terminal.mui2.pending";
            sync(1, buffer -> {
                buffer.writeInt(request);
                buffer.writeInt(flow);
            });
        }

        private boolean active() {
            return isValid() && !getSyncManager().isClient()
                && data.getPlayer().openContainer == getSyncManager().getContainer()
                && data.getPlayer().worldObj == tile.getWorldObj()
                && data.getTileEntity() == tile
                && TcdsTerminalFactory.machine(data) == machine
                && TcdsTerminalFactory.canUse(data);
        }

        @Override
        public void detectAndSendChanges(boolean init) {
            long now = TerminalServerSessions.currentServerTick();
            if (!init && lastPoll != Long.MIN_VALUE && now - lastPoll < 10) return;
            lastPoll = now;
            if (!active()) return;
            byte[] payload = TcdsTerminalData.assembleSnapshot(machine);
            if (!init && Arrays.equals(payload, lastSnapshot)) return;
            lastSnapshot = payload;
            syncToClient(0, buffer -> {
                buffer.writeInt(payload.length);
                buffer.writeBytes(payload);
            });
        }

        @Override
        public void readOnServer(int id, PacketBuffer buffer) throws IOException {
            if (id != 1 || buffer.readableBytes() != 8) return;
            int request = buffer.readInt();
            int flow = buffer.readInt();
            if (!pending.compareAndSet(false, true)) return;
            Mui2ServerActions.enqueue(() -> {
                try {
                    if (!active()) return;
                    boolean accepted = request > appliedSequence;
                    if (accepted) {
                        appliedSequence = request;
                        machine.setFlow(flow);
                    }
                    int actual = machine.getFlow();
                    syncToClient(2, reply -> {
                        reply.writeInt(request);
                        reply.writeBoolean(accepted);
                        reply.writeInt(actual);
                    });
                    lastSnapshot = null;
                    lastPoll = Long.MIN_VALUE;
                } finally {
                    pending.set(false);
                }
            });
        }

        @Override
        public void readOnClient(int id, PacketBuffer buffer) throws IOException {
            if (id == 0) {
                if (buffer.readableBytes() < 4) return;
                int length = buffer.readInt();
                if (length < 0 || length > 4096 || buffer.readableBytes() != length) return;
                byte[] payload = new byte[length];
                buffer.readBytes(payload);
                TcdsTerminalData.Snapshot next = TcdsTerminalData.readSnapshot(payload);
                if (next != null) snapshot = next;
            } else if (id == 2 && buffer.readableBytes() == 9) {
                int request = buffer.readInt();
                boolean accepted = buffer.readBoolean();
                int actual = buffer.readInt();
                result = new Result(request, accepted, actual);
            }
        }

        private static final class Result {

            final int sequence, flow;
            final boolean accepted;

            Result(int sequence, boolean accepted, int flow) {
                this.sequence = sequence;
                this.accepted = accepted;
                this.flow = flow;
            }
        }
    }
}
