package com.miaokatze.gtsr.common.gui.mui2;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.NetHandlerPlayServer;

import com.miaokatze.gtsr.common.network.ServerPacketAuthority;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.machine.tcds.MTEThermoChemicalDenseSteamGenerator;
import com.miaokatze.gtsr.common.terminal.TcdsTerminalData;
import com.miaokatze.gtsr.common.terminal.TerminalServerSessions;
import com.miaokatze.gtsr.common.util.GtsrNumFormat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** One persistent widget tree and one snapshot consumer per opened TCDS session. */
public final class TcdsTerminalPanel {

    public static final String SYNC_KEY = "tcds_session";

    private TcdsTerminalPanel() {}

    public static ModularPanel build(PosGuiData data, MTEThermoChemicalDenseSteamGenerator machine,
        PanelSyncManager manager) {
        Session session = new Session(data, machine);
        manager.syncValue(SYNC_KEY, session);
        TextFieldWidget input = new TextFieldWidget().name("tcds_flow_input")
            .size(140, 14)
            .setMaxLength(11)
            .autoUpdateOnChange(true)
            .value(new StringValue.Dynamic(session::draft, value -> {
                session.draft = value;
                session.edited = true;
            }))
            .addTooltipLine(IKey.lang("gtsr.tcds_terminal.mui2.input_hint"));
        ParentWidget<?> content = new ParentWidget<>().name("tcds_content")
            .size(384, 232)
            .pos(0, 0);
        content.child(
            text(IKey.lang("gtsr.tcds_terminal.title"), 0, 3, 384, 12, 1f, GtsrGuiPalette.TEXT_TITLE)
                .name("tcds_title"));
        content.child(
            row(
                "gtsr.tcds_terminal.flow_label",
                session,
                s -> NumberFormatUtil.formatNumber(s.flow) + " L/t",
                32,
                1f,
                GtsrGuiPalette.TEXT_ACCENT));
        content
            .child(row("gtsr.tcds_terminal.heat_cap_label", session, s -> s.heatCap, 48, 1f, GtsrGuiPalette.TEXT_BODY));
        content.child(text(IKey.lang("gtsr.tcds_terminal.input_label"), 0, 64, 210, 10, 1f, GtsrGuiPalette.TEXT_LABEL));
        content.child(
            text(
                IKey.dynamic(
                    () -> IKey.lang("gtsr.tcds_terminal.mui2.heat")
                        .getFormatted() + " "
                        + (session.snapshot == null ? "—" : number(session.snapshot.heat) + "%")),
                224,
                64,
                160,
                10,
                0.7f,
                GtsrGuiPalette.TEXT_MUTED).name("gtsr.tcds_terminal.mui2.heat"));
        content.child(
            new ParentWidget<>().name("tcds_input_row")
                .pos(0, 74)
                .size(384, 14)
                .child(input.pos(0, 0))
                .child(
                    TerminalUiStyle.button("gtsr.tcds_terminal.confirm")
                        .name("tcds_apply")
                        .pos(144, 0)
                        .size(64, 14)
                        .background(
                            new DynamicDrawable(
                                () -> session.awaiting ? TerminalUiStyle.DISABLED : TerminalUiStyle.BUTTON))
                        .hoverBackground(
                            new DynamicDrawable(
                                () -> session.awaiting ? TerminalUiStyle.DISABLED : TerminalUiStyle.HOVER))
                        .overlay(
                            IKey.lang("gtsr.tcds_terminal.confirm")
                                .color(() -> session.awaiting ? GtsrGuiPalette.TEXT_MUTED : GtsrGuiPalette.TEXT_BODY))
                        .onMousePressed(button -> {
                            if (button == 0) session.submit(input.getText());
                            return true;
                        })
                        .addTooltipLine(IKey.lang("gtsr.tcds_terminal.confirm_hint")))
                .child(
                    TerminalUiStyle.button("gtsr.tcds_terminal.formula_help")
                        .name("tcds_formula_help")
                        .pos(224, 0)
                        .size(152, 14)
                        .tooltipBuilder(tooltip -> {
                            for (String key : new String[] { "flow", "efficiency", "output", "cap", "consumption",
                                "shortage", "buffer", "chip" })
                                tooltip.addLine(IKey.lang("gtsr.tcds_terminal.formula_" + key));
                        })));
        Flow formula = Flow.col()
            .pos(0, 96)
            .size(384, 132)
            .childPadding(0)
            .collapseDisabledChild();
        formula.child(
            line(
                header("gtsr.tcds_terminal.group_flow", 0)
                    .addTooltipLine(IKey.lang("gtsr.tcds_terminal.formula_cap"))));
        formula.child(
            line(
                row(
                    "gtsr.tcds_terminal.kv_actual_flow",
                    session,
                    TcdsTerminalPanel::actualFlow,
                    0,
                    0.7f,
                    GtsrGuiPalette.TEXT_BODY).addTooltipLine(IKey.lang("gtsr.tcds_terminal.formula_flow"))));
        formula.child(
            line(
                text(
                    IKey.dynamic(
                        () -> IKey.lang("gtsr.tcds_terminal.formula_efficiency")
                            .getFormatted() + " → "
                            + (session.snapshot == null ? "—"
                                : String.format(Locale.ROOT, "%.3f", session.snapshot.efficiency))),
                    12,
                    0,
                    372,
                    11,
                    0.6f,
                    GtsrGuiPalette.TEXT_MUTED).name("gtsr.tcds_terminal.mui2.efficiency")
                        .addTooltipLine(IKey.lang("gtsr.tcds_terminal.formula_efficiency"))));
        formula.child(
            line(
                header("gtsr.tcds_terminal.group_output", 0)
                    .addTooltipLine(IKey.lang("gtsr.tcds_terminal.formula_output"))
                    .addTooltipLine(
                        IKey.dynamic(() -> session.snapshot == null ? "—" : outputFlow(session.snapshot)))));
        formula.child(
            line(
                row(
                    "gtsr.tcds_terminal.kv_output_total",
                    session,
                    TcdsTerminalPanel::outputFlow,
                    0,
                    0.7f,
                    GtsrGuiPalette.TEXT_ACCENT)));
        formula.child(line(derivation(session, true, 0)).setEnabledIf(w -> familyActive(session.snapshot, true)));
        formula.child(line(derivation(session, false, 0)).setEnabledIf(w -> familyActive(session.snapshot, false)));
        formula.child(
            line(
                header("gtsr.tcds_terminal.group_consumption", 0)
                    .addTooltipLine(IKey.lang("gtsr.tcds_terminal.formula_consumption"))));
        formula.child(
            line(
                row(
                    "gtsr.tcds_terminal.kv_fuel",
                    session,
                    TcdsTerminalPanel::fuelFlow,
                    0,
                    0.7f,
                    GtsrGuiPalette.TEXT_BODY)));
        formula.child(
            line(
                row(
                    "gtsr.tcds_terminal.kv_air",
                    session,
                    s -> GtsrNumFormat.grouped(s.airConsumption),
                    0,
                    0.7f,
                    GtsrGuiPalette.TEXT_BODY)));
        formula.child(
            line(
                row(
                    "gtsr.tcds_terminal.kv_water",
                    session,
                    s -> GtsrNumFormat.grouped(s.waterConsumption),
                    0,
                    0.7f,
                    GtsrGuiPalette.TEXT_BODY)));
        formula.child(
            new ParentWidget<>().name("tcds_status_row")
                .size(384, 11)
                .child(text(IKey.lang("gtsr.tcds_terminal.kv_status"), 0, 0, 138, 11, 0.7f, GtsrGuiPalette.TEXT_LABEL))
                .child(
                    text(
                        IKey.dynamicKey(
                            () -> IKey.lang(
                                running(session) ? "gtsr.tcds_terminal.status_ok"
                                    : "gtsr.tcds_terminal.status_stopped")),
                        142,
                        0,
                        150,
                        11,
                        0.7f,
                        GtsrGuiPalette.TEXT_BODY).name("tcds_status")
                            .color(() -> running(session) ? GtsrGuiPalette.STATE_ONLINE : GtsrGuiPalette.STATE_IDLE))
                .child(
                    text(
                        IKey.dynamic(
                            () -> session.snapshot == null ? "—"
                                : "· " + GtsrNumFormat.grouped(session.snapshot.output / 1000)),
                        292,
                        0,
                        92,
                        11,
                        0.7f,
                        GtsrGuiPalette.TEXT_ACCENT).name("tcds_chip_output"))
                .addTooltipLine(IKey.lang("gtsr.tcds_terminal.formula_shortage"))
                .addTooltipLine(IKey.lang("gtsr.tcds_terminal.formula_buffer"))
                .addTooltipLine(IKey.lang("gtsr.tcds_terminal.formula_chip"))
                .addTooltipLine(
                    IKey.dynamic(
                        () -> session.snapshot == null ? "—"
                            : session.snapshot.output + " ÷ 1000 = " + session.snapshot.output / 1000)));
        content.child(formula);
        content.child(text(IKey.dynamicKey(() -> {
            session.consumeResult();
            return IKey.lang(session.receiptKey);
        }), 224, 88, 160, 8, 0.6f, GtsrGuiPalette.TEXT_ACCENT).name("tcds_receipt")
            .addTooltipLine(IKey.dynamicKey(() -> IKey.lang(session.receiptKey)))
            .tooltipAutoUpdate(true));
        // Both sides retain the same tree; narrow canvases expose the original canvas through native scrollbars.
        return ModularPanel.defaultPanel(TcdsTerminalFactory.PANEL_NAME, 400, 240)
            .background(TerminalUiStyle.HUB_PANEL)
            .child(
                TerminalViewport.wrapExact("tcds_body_viewport", content, 384, 232)
                    .pos(8, 0)
                    .widthRelOffset(1f, -16)
                    .heightRelOffset(1f, -8));
    }

    private static boolean running(Session session) {
        return session.snapshot != null
            && (session.snapshot.airConsumption > 0 || session.snapshot.waterConsumption > 0);
    }

    private static String fuelFlow(TcdsTerminalData.Snapshot snapshot) {
        if (snapshot.fuelKind == 3) return "gas " + GtsrNumFormat.grouped(snapshot.gasConsumption)
            + " + liquid "
            + GtsrNumFormat.grouped(snapshot.liquidConsumption);
        if (snapshot.fuelKind == 1 || snapshot.fuelKind == 2)
            return (snapshot.fuelKind == 1 ? "gas " : "liquid ") + GtsrNumFormat.grouped(snapshot.fuelConsumption);
        return "0";
    }

    private static ParentWidget<?> line(IWidget child) {
        return new ParentWidget<>().size(384, 11)
            .child(child);
    }

    private static boolean familyActive(TcdsTerminalData.Snapshot snapshot, boolean gas) {
        return snapshot != null && (snapshot.fuelKind == 3 || snapshot.fuelKind == (gas ? 1 : 2));
    }

    private static String actualFlow(TcdsTerminalData.Snapshot snapshot) {
        if (snapshot.fuelKind == 0) return "0 L/t";
        return fuelFlow(snapshot) + " = "
            + GtsrNumFormat.grouped(familyFlow(snapshot, true) + familyFlow(snapshot, false))
            + " L/t";
    }

    private static String outputFlow(TcdsTerminalData.Snapshot snapshot) {
        if (snapshot.fuelKind == 0) return "0 L/t";
        String terms = familyActive(snapshot, true) ? "gas " + GtsrNumFormat.grouped(familyTerm(snapshot, true)) : "";
        if (familyActive(snapshot, false))
            terms += (terms.isEmpty() ? "" : " + ") + "liquid " + GtsrNumFormat.grouped(familyTerm(snapshot, false));
        return terms + " = " + GtsrNumFormat.grouped(snapshot.output) + " L/t";
    }

    private static TextWidget<?> header(String key, int y) {
        return text(
            IKey.dynamic(
                () -> "§6§l" + IKey.lang(key)
                    .getFormatted()),
            0,
            y,
            384,
            11,
            0.7f,
            GtsrGuiPalette.TEXT_ACCENT);
    }

    private static TextWidget<?> derivation(Session session, boolean gas, int y) {
        String key = "gtsr.tcds_terminal.derive_" + (gas ? "gas" : "liquid");
        return text(
            IKey.dynamic(
                () -> IKey.lang(key)
                    .getFormatted() + " = "
                    + (session.snapshot == null ? "—"
                        : GtsrNumFormat.grouped(familyTerm(session.snapshot, gas)) + " L/t")),
            12,
            y,
            372,
            11,
            0.6f,
            GtsrGuiPalette.TEXT_MUTED).name(key)
                .addTooltipLine(IKey.dynamic(() -> familyEquation(session.snapshot, gas)))
                .tooltipAutoUpdate(true);
    }

    private static TextWidget<?> text(IKey key, int x, int y, int width, int height, float scale, int color) {
        return IKey.dynamic(() -> clip(key.getFormatted(), (int) (width / scale)))
            .asWidget()
            .pos(x, y)
            .size(width, height)
            .scale(scale)
            .textAlign(Alignment.CenterLeft)
            .color(color)
            .shadow(true);
    }

    private static String clip(String text, int width) {
        return NetworkUtils.isDedicatedClient() ? clientClip(text, width) : text;
    }

    @SideOnly(Side.CLIENT)
    private static String clientClip(String text, int width) {
        if (Minecraft.getMinecraft().fontRenderer.getStringWidth(text) <= width) return text;
        return Minecraft.getMinecraft().fontRenderer.trimStringToWidth(text, Math.max(0, width - 9)) + "...";
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

    private static ParentWidget<?> row(String key, Session session, Function<TcdsTerminalData.Snapshot, String> value,
        int y, float scale, int valueColor) {
        return new ParentWidget<>().name(key)
            .pos(0, y)
            .size(384, scale == 1f ? 12 : 11)
            .child(text(IKey.lang(key), 0, 0, 138, scale == 1f ? 12 : 11, scale, GtsrGuiPalette.TEXT_LABEL))
            .child(
                text(
                    IKey.dynamic(() -> session.snapshot == null ? "—" : value.apply(session.snapshot)),
                    142,
                    0,
                    242,
                    scale == 1f ? 12 : 11,
                    scale,
                    valueColor));
    }

    /** Wire ids: 0 snapshot, 1 apply(seq,int), 2 result(seq,accepted,int). All keys are explicit. */
    public static final class Session extends SyncHandler<Session> {

        private final PosGuiData data;
        private final MTEThermoChemicalDenseSteamGenerator machine;
        private final TileEntity tile;
        private final NetHandlerPlayServer connection;
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
            this.connection = ServerPacketAuthority.connection(data.getPlayer());
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
                && ServerPacketAuthority.isCurrentPlayer(data.getPlayer(), connection)
                && data.getPlayer().openContainer == getSyncManager().getContainer()
                && (com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.remote(data)
                    || data.getPlayer().worldObj == tile.getWorldObj())
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
