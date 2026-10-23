package com.miaokatze.gtsr.common.gui.mui2.worker;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.FakePlayer;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.gui.mui2.Mui2TerminalRoutes;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;
import com.miaokatze.gtsr.common.terminal.TerminalServerSessions;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.Unpooled;

/** Upgrade commands have session-local sequence identity and execute only on the server END queue. */
public final class WorkerUiSession extends SyncHandler<WorkerUiSession> {

    public static final String SYNC_KEY = "worker_session";
    private final PosGuiData data;
    private final MTERemoteWorkerNode machine;
    private final TileEntity tile;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final AtomicReference<Incoming> incoming = new AtomicReference<>();
    private final AtomicReference<Reply> reply = new AtomicReference<>();
    public WorkerUiSnapshot snapshot;
    public boolean awaiting;
    public String receiptKey = "gtsr.mui2.hub.ready";
    private long lastPoll = Long.MIN_VALUE, revision, clientRevision;
    private byte[] lastSnapshot;
    private int sequence, handledSequence;
    private boolean handledResult;

    public WorkerUiSession(PosGuiData data, MTERemoteWorkerNode machine) {
        this.data = data;
        this.machine = machine;
        this.tile = data.getTileEntity();
        allowC2S();
    }

    public boolean canUse() {
        if (com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.remote(data))
            return com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.canUseRemote(data.getPlayer(), data);
        return tile != null && !tile.isInvalid()
            && data.getPlayer() != null
            && !data.getPlayer().isDead
            && !(data.getPlayer() instanceof FakePlayer)
            && data.getPlayer().worldObj == tile.getWorldObj()
            && data.getTileEntity() == tile
            && tile instanceof IGregTechTileEntity base
            && base.getMetaTileEntity() == machine
            && base.canAccessData()
            && base.isUseableByPlayer(data.getPlayer());
    }

    private boolean active() {
        return isValid() && !getSyncManager().isClient()
            && Mui2TerminalRoutes.remoteWorkerNodes
            && canUse()
            && data.getPlayer().openContainer == getSyncManager().getContainer();
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        long now = TerminalServerSessions.currentServerTick();
        if (!init && lastPoll != Long.MIN_VALUE && now - lastPoll < 10) return;
        lastPoll = now;
        if (!active()) return;
        WorkerUiSnapshot current = machine.getWorkerUiSnapshot();
        if (current == null) return;
        PacketBuffer encoded = new PacketBuffer(Unpooled.buffer());
        current.write(encoded);
        byte[] bytes = new byte[encoded.readableBytes()];
        encoded.readBytes(bytes);
        if (!init && Arrays.equals(bytes, lastSnapshot)) return;
        lastSnapshot = bytes;
        long rev = ++revision;
        syncToClient(0, out -> {
            out.writeLong(rev);
            out.writeInt(bytes.length);
            out.writeBytes(bytes);
        });
    }

    public void upgrade() {
        if (awaiting) return;
        awaiting = true;
        receiptKey = "gtsr.mui2.hub.pending";
        int request = ++sequence;
        sync(1, out -> out.writeInt(request));
    }

    public void consume() {
        Incoming update = incoming.getAndSet(null);
        if (update != null && update.revision > clientRevision) {
            clientRevision = update.revision;
            snapshot = update.snapshot;
        }
        Reply result = reply.getAndSet(null);
        if (result != null && result.sequence == sequence) {
            awaiting = false;
            receiptKey = result.accepted ? "gtsr.mui2.worker.upgraded" : "gtsr.mui2.hub.rejected";
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer in) throws IOException {
        if (id != 1 || in.readableBytes() != 4) return;
        int seq = in.readInt();
        if (seq <= 0 || !pending.compareAndSet(false, true)) return;
        Mui2ServerActions.enqueue(() -> {
            try {
                if (!active()) return;
                boolean accepted;
                if (seq == handledSequence) accepted = handledResult;
                else if (seq < handledSequence) accepted = false;
                else {
                    handledSequence = seq;
                    accepted = machine.tryUpgrade(data.getPlayer());
                    handledResult = accepted;
                    lastPoll = Long.MIN_VALUE;
                    lastSnapshot = null;
                }
                final boolean result = accepted;
                syncToClient(2, out -> {
                    out.writeInt(seq);
                    out.writeBoolean(result);
                });
            } finally {
                pending.set(false);
            }
        });
    }

    @Override
    public void readOnClient(int id, PacketBuffer in) throws IOException {
        if (id == 0) {
            if (in.readableBytes() < 12) return;
            long rev = in.readLong();
            int length = in.readInt();
            if (length < 0 || length > 4096 || length != in.readableBytes()) return;
            try {
                WorkerUiSnapshot value = WorkerUiSnapshot.read(in);
                if (in.isReadable()) return;
                Incoming next = new Incoming(rev, value);
                incoming.accumulateAndGet(
                    next,
                    (old, update) -> old == null || update.revision > old.revision ? update : old);
            } catch (RuntimeException error) {
                // Malformed/truncated display data never partially updates the live panel.
            }
        } else if (id == 2 && in.readableBytes() == 5) {
            Reply next = new Reply(in.readInt(), in.readBoolean());
            reply
                .accumulateAndGet(next, (old, update) -> old == null || update.sequence >= old.sequence ? update : old);
        }
    }

    @Override
    public void dispose() {
        incoming.set(null);
        reply.set(null);
        super.dispose();
    }

    private static final class Incoming {

        final long revision;
        final WorkerUiSnapshot snapshot;

        Incoming(long revision, WorkerUiSnapshot snapshot) {
            this.revision = revision;
            this.snapshot = snapshot;
        }
    }

    private static final class Reply {

        final int sequence;
        final boolean accepted;

        Reply(int sequence, boolean accepted) {
            this.sequence = sequence;
            this.accepted = accepted;
        }
    }
}
