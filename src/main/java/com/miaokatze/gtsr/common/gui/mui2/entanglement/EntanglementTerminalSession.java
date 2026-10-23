package com.miaokatze.gtsr.common.gui.mui2.entanglement;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.common.terminal.TerminalServerSessions;
import com.miaokatze.gtsr.common.terminal.entanglement.EntanglementInputs;
import com.miaokatze.gtsr.common.terminal.entanglement.EntanglementMath;
import com.miaokatze.gtsr.common.terminal.entanglement.EntanglementSnapshot;

/** One explicit handler, fixed six editing slots, bounded packets and server-thread mutations. */
public final class EntanglementTerminalSession extends SyncHandler<EntanglementTerminalSession> {

    private final PosGuiData data;
    private final TileEntity tile;
    private final MTESingularityMachineBase machine;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final Map<String, String> drafts = new LinkedHashMap<>();
    private volatile EntanglementSnapshot incoming;
    private volatile Reply reply;
    private EntanglementSnapshot snapshot;
    private boolean defaults, conflict, awaiting;
    private int sequence, appliedSequence, page;
    private String receipt = "ready";
    private byte[] lastSnapshot;
    private long lastPoll = Long.MIN_VALUE, lastAction = Long.MIN_VALUE;

    EntanglementTerminalSession(PosGuiData data, MTESingularityMachineBase machine) {
        this.data = data;
        this.tile = data.getTileEntity();
        this.machine = machine;
        allowC2S();
    }

    public EntanglementSnapshot snapshot() {
        refresh();
        return snapshot;
    }

    private void refresh() {
        Reply response = reply;
        if (response != null) {
            reply = null;
            if (response.sequence == sequence) {
                awaiting = false;
                if (response.accepted) {
                    drafts.clear();
                    defaults = false;
                    conflict = false;
                    receipt = "applied";
                } else {
                    conflict = true;
                    receipt = "rejected";
                }
                incoming = response.snapshot;
            }
        }
        EntanglementSnapshot next = incoming;
        if (next == null || next == snapshot) return;
        if (snapshot != null && dirty()) {
            boolean sameRows = snapshot.rows.size() == next.rows.size();
            for (int i = 0; sameRows && i < snapshot.rows.size(); i++) sameRows = snapshot.rows.get(i)
                .key()
                .equals(
                    next.rows.get(i)
                        .key());
            if (!sameRows || snapshot.revision != next.revision) {
                conflict = true;
                receipt = "conflict";
            }
        }
        snapshot = next;
    }

    public EntanglementSnapshot.Row row(int slot) {
        EntanglementSnapshot current = snapshot();
        return current == null || slot >= current.rows.size() ? null : current.rows.get(slot);
    }

    public String draft(int slot) {
        EntanglementSnapshot.Row row = row(slot);
        if (row == null) return "";
        return drafts.getOrDefault(row.key(), defaults || row.limit < 0 ? "" : Long.toString(row.limit));
    }

    public void edit(int slot, String value) {
        EntanglementSnapshot.Row row = row(slot);
        if (row == null || conflict || awaiting) return;
        drafts.put(row.key(), value);
        receipt = "draft";
    }

    public static Long parse(String text) {
        String value = text.trim();
        if (value.isEmpty()) return -1L;
        if (!value.matches("[0-9]{1,19}")) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private Map<String, Long> parsed() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> draft : drafts.entrySet()) {
            Long value = parse(draft.getValue());
            if (value == null) return null;
            result.put(draft.getKey(), value);
        }
        return result;
    }

    public EntanglementMath.Forecast forecast() {
        EntanglementSnapshot current = snapshot();
        Map<String, Long> changes = parsed();
        return current == null ? null : current.forecast(changes == null ? new LinkedHashMap<>() : changes, defaults);
    }

    public long[] requested() {
        EntanglementSnapshot current = snapshot();
        Map<String, Long> changes = parsed();
        return current == null ? new long[6]
            : current.requested(changes == null ? new LinkedHashMap<>() : changes, defaults);
    }

    public long planned(int slot) {
        EntanglementSnapshot.Row row = row(slot);
        EntanglementMath.Forecast forecast = forecast();
        if (row == null || forecast == null || !snapshot.formed || !snapshot.allowed) return 0;
        if (row.kind < 3 && row.kind != forecast.grade) return 0;
        Long limit = parse(draft(slot));
        return limit == null ? 0 : row.requested(limit);
    }

    public boolean dirty() {
        return defaults || !drafts.isEmpty();
    }

    public boolean canApply() {
        refresh();
        return snapshot != null && !conflict && !awaiting && parsed() != null;
    }

    public String receipt() {
        refresh();
        if (parsed() == null) return "invalid";
        return receipt;
    }

    public void discard() {
        drafts.clear();
        defaults = false;
        conflict = false;
        receipt = "ready";
        snapshot = incoming;
    }

    public void defaults() {
        if (awaiting) return;
        discard();
        defaults = true;
        receipt = "default_draft";
    }

    public void apply() {
        if (!canApply()) return;
        Map<String, Long> changes = parsed();
        EntanglementSnapshot submitted = snapshot;
        boolean reset = defaults;
        int request = ++sequence;
        awaiting = true;
        receipt = "pending";
        sync(1, buffer -> {
            buffer.writeInt(request);
            buffer.writeInt(submitted.revision);
            buffer.writeInt(submitted.page);
            buffer.writeBoolean(reset);
            buffer.writeByte(submitted.rows.size());
            for (EntanglementSnapshot.Row row : submitted.rows) {
                buffer.writeInt(row.x);
                buffer.writeInt(row.y);
                buffer.writeInt(row.z);
                buffer.writeByte(row.kind);
                buffer.writeLong(changes.getOrDefault(row.key(), reset ? -1 : row.limit));
            }
        });
    }

    public void page(int direction) {
        refresh();
        if (snapshot == null || awaiting) return;
        if (dirty()) {
            receipt = "save_first";
            return;
        }
        int next = Math.max(0, Math.min(snapshot.pages() - 1, snapshot.page + direction));
        if (next != snapshot.page) sync(3, buffer -> buffer.writeInt(next));
    }

    private boolean active() {
        return isValid() && !getSyncManager().isClient()
            && data.getPlayer().openContainer == getSyncManager().getContainer()
            && (com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.remote(data)
                || data.getPlayer().worldObj == tile.getWorldObj())
            && data.getTileEntity() == tile
            && EntanglementTerminalFactory.machine(data) == machine
            && EntanglementTerminalFactory.canUse(data);
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        long now = TerminalServerSessions.currentServerTick();
        if (!init && lastPoll != Long.MIN_VALUE && now - lastPoll < 10) return;
        lastPoll = now;
        if (!active()) return;
        byte[] payload = machine.entanglementSnapshot(page)
            .encode();
        if (!init && Arrays.equals(payload, lastSnapshot)) return;
        lastSnapshot = payload;
        syncToClient(0, buffer -> writeSnapshot(buffer, payload));
    }

    private static void writeSnapshot(PacketBuffer buffer, byte[] payload) {
        buffer.writeInt(payload.length);
        buffer.writeBytes(payload);
    }

    private static EntanglementSnapshot readSnapshot(PacketBuffer buffer) throws IOException {
        if (buffer.readableBytes() < 4) throw new IOException("Missing snapshot size");
        int length = buffer.readInt();
        if (length < 0 || length > EntanglementSnapshot.MAX_BYTES || buffer.readableBytes() != length)
            throw new IOException("Invalid snapshot size");
        byte[] payload = new byte[length];
        buffer.readBytes(payload);
        return EntanglementSnapshot.decode(payload);
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id == 3 && buffer.readableBytes() == 4) {
            int requestedPage = buffer.readInt();
            if (!pending.compareAndSet(false, true)) return;
            Mui2ServerActions.enqueue(() -> {
                try {
                    if (!active()) return;
                    page = machine.entanglementSnapshot(requestedPage).page;
                    lastPoll = Long.MIN_VALUE;
                } finally {
                    pending.set(false);
                }
            });
            return;
        }
        if (id != 1 || buffer.readableBytes() < 14 || buffer.readableBytes() > 140) return;
        int request = buffer.readInt(), revision = buffer.readInt(), requestedPage = buffer.readInt();
        boolean reset = buffer.readBoolean();
        int count = buffer.readUnsignedByte();
        if (count > EntanglementSnapshot.PAGE_SIZE || buffer.readableBytes() != count * 21) return;
        Map<String, Long> changes = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            int x = buffer.readInt(), y = buffer.readInt(), z = buffer.readInt(), kind = buffer.readUnsignedByte();
            long limit = buffer.readLong();
            if (kind >= EntanglementInputs.KINDS || limit < -1
                || changes.put(EntanglementInputs.key(x, y, z, kind), limit) != null) return;
        }
        if (!pending.compareAndSet(false, true)) return;
        Mui2ServerActions.enqueue(() -> {
            try {
                if (!active()) return;
                long now = TerminalServerSessions.currentServerTick();
                EntanglementSnapshot current = machine.entanglementSnapshot(page);
                boolean accepted = request > appliedSequence && requestedPage == current.page
                    && revision == current.revision
                    && (lastAction == Long.MIN_VALUE || now - lastAction >= 5)
                    && current.rows.size() == changes.size();
                for (EntanglementSnapshot.Row row : current.rows) accepted &= changes.containsKey(row.key());
                if (accepted) {
                    appliedSequence = request;
                    lastAction = now;
                    accepted = machine.applyEntanglementLimits(changes, revision, reset);
                }
                final boolean result = accepted;
                byte[] payload = machine.entanglementSnapshot(page)
                    .encode();
                syncToClient(2, response -> {
                    response.writeInt(request);
                    response.writeBoolean(result);
                    writeSnapshot(response, payload);
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
        if (id == 0) incoming = readSnapshot(buffer);
        else if (id == 2 && buffer.readableBytes() >= 9) {
            int request = buffer.readInt();
            boolean accepted = buffer.readBoolean();
            reply = new Reply(request, accepted, readSnapshot(buffer));
        }
    }

    private static final class Reply {

        final int sequence;
        final boolean accepted;
        final EntanglementSnapshot snapshot;

        Reply(int sequence, boolean accepted, EntanglementSnapshot snapshot) {
            this.sequence = sequence;
            this.accepted = accepted;
            this.snapshot = snapshot;
        }
    }
}
