package com.miaokatze.gtsr.common.gui.mui2.hub;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.common.machine.base.MTEHubArrayBase;
import com.miaokatze.gtsr.common.terminal.CacheHubTerminalData;
import com.miaokatze.gtsr.common.terminal.CacheNodeTerminalData;
import com.miaokatze.gtsr.common.terminal.SingularityHubTerminalData;
import com.miaokatze.gtsr.common.terminal.TerminalServerSessions;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import cpw.mods.fml.common.network.ByteBufUtils;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.Unpooled;

/** One bounded, explicitly keyed consumer. Packet callbacks publish data; UI/world work has its own thread. */
public final class HubTerminalSession extends SyncHandler<HubTerminalSession> {

    public static final String SYNC_KEY = "hub_session";
    public static final int MAX_SNAPSHOT_BYTES = 131072;
    public static final int MAX_ACTION_BYTES = 256;
    public static final int PROCESSED = 1, REJECTED = 2;
    private final HubTerminalFactory factory;
    private final PosGuiData data;
    private final IMetaTileEntity machine;
    private final TileEntity tile;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final AtomicReference<Snapshot> incoming = new AtomicReference<>();
    private final AtomicReference<Reply> incomingReply = new AtomicReference<>();
    public final HubTerminalViewState view;
    private long lastPoll = Long.MIN_VALUE, revision;
    private byte[] lastSnapshot;
    private int handledSequence, handledStatus;
    private int clientSequence;
    public boolean awaiting;
    public String receiptKey = "gtsr.mui2.hub.ready";

    public HubTerminalSession(HubTerminalFactory factory, PosGuiData data, IMetaTileEntity machine) {
        this.factory = factory;
        this.data = data;
        this.machine = machine;
        this.tile = data.getTileEntity();
        this.view = new HubTerminalViewState(
            factory.type == TerminalUiType.SINGULARITY_HUB,
            factory.type == TerminalUiType.CACHE_NODE);
        allowC2S();
    }

    public boolean active() {
        return isValid() && !getSyncManager().isClient()
            && tile != null
            && !tile.isInvalid()
            && data.getPlayer().openContainer == getSyncManager().getContainer()
            && data.getTileEntity() == tile
            && factory.machine(data) == machine
            && factory.canInteractWith(data.getPlayer(), data);
    }

    private byte[] assemble() {
        switch (factory.type) {
            case CACHE_NODE:
                return CacheNodeTerminalData.assembleSnapshot(data.getPlayer(), (IGregTechTileEntity) tile);
            case SINGULARITY_HUB:
                return SingularityHubTerminalData.assembleSnapshot((MTESingularityDrillingHub) machine);
            default:
                return CacheHubTerminalData.assembleSnapshot(factory.type, machine);
        }
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        long now = TerminalServerSessions.currentServerTick();
        if (!init && lastPoll != Long.MIN_VALUE && now - lastPoll < 10) return;
        lastPoll = now;
        if (!active()) return;
        byte[] payload = assemble();
        if (payload == null || payload.length > MAX_SNAPSHOT_BYTES || (!init && Arrays.equals(lastSnapshot, payload)))
            return;
        lastSnapshot = payload;
        long nextRevision = ++revision;
        syncToClient(0, out -> {
            out.writeLong(nextRevision);
            out.writeInt(payload.length);
            out.writeBytes(payload);
        });
    }

    public void submit(int action, byte[] payload) {
        if (awaiting || payload == null || payload.length > MAX_ACTION_BYTES) return;
        awaiting = true;
        receiptKey = "gtsr.mui2.hub.pending";
        int seq = ++clientSequence;
        sync(1, out -> {
            out.writeInt(seq);
            out.writeInt(action);
            out.writeInt(payload.length);
            out.writeBytes(payload);
        });
    }

    /** Called once per client UI update, never by the network callback. */
    public void consume(HubTerminalViewState view) {
        Snapshot next = incoming.getAndSet(null);
        if (next != null) {
            if (next.revision > view.revision) {
                view.replace(next.cache, next.drilling, next.revision);
            }
        }
        Reply reply = incomingReply.getAndSet(null);
        if (reply != null) {
            if (reply.sequence == clientSequence) {
                awaiting = false;
                // Most existing backend commands are void and may report a business failure in chat.
                receiptKey = reply.status == PROCESSED ? "gtsr.mui2.hub.processed" : "gtsr.mui2.hub.rejected";
            }
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id != 1 || buffer.readableBytes() < 12) return;
        int seq = buffer.readInt(), action = buffer.readInt(), length = buffer.readInt();
        if (seq <= 0 || length < 16 || length > MAX_ACTION_BYTES || buffer.readableBytes() != length) return;
        byte[] payload = new byte[length];
        buffer.readBytes(payload);
        if (!validCommand(factory.type, action, payload) || !pending.compareAndSet(false, true)) return;
        Mui2ServerActions.enqueue(() -> {
            try {
                if (!active()) return;
                int status;
                if (seq == handledSequence) {
                    status = handledStatus;
                } else if (seq < handledSequence) {
                    status = REJECTED;
                } else {
                    handledSequence = seq;
                    status = execute(action, payload);
                    handledStatus = status;
                    lastPoll = Long.MIN_VALUE;
                    lastSnapshot = null;
                }
                final int result = status;
                syncToClient(2, out -> {
                    out.writeInt(seq);
                    out.writeInt(result);
                });
            } finally {
                pending.set(false);
            }
        });
    }

    private int execute(int action, byte[] payload) {
        if (factory.type == TerminalUiType.CACHE_NODE) {
            if (!CacheNodeTerminalData.canUse(data.getPlayer(), (IGregTechTileEntity) tile)) return REJECTED;
            PacketBuffer check = new PacketBuffer(Unpooled.wrappedBuffer(payload));
            if (check.readInt() != data.getX() || check.readInt() != data.getY()
                || check.readInt() != data.getZ()
                || check.readInt() != data.getPlayer().dimension) return REJECTED;
            CacheNodeTerminalData.executeAction(data.getPlayer(), (IGregTechTileEntity) tile, action, payload);
        } else if (factory.type == TerminalUiType.SINGULARITY_HUB) {
            // Deliberately preserve the legacy 64-block ordinary-action range. Teleport has its own 8-block check.
            SingularityHubTerminalData
                .executeAction((MTESingularityDrillingHub) machine, data.getPlayer(), action, payload);
        } else {
            if (!((MTEHubArrayBase) machine).canUseStatusAction(data.getPlayer())) return REJECTED;
            CacheHubTerminalData.executeAction(factory.type, machine, data.getPlayer(), action, payload);
        }
        return PROCESSED;
    }

    /** Exact framing before enqueueing. Business guards remain in the authoritative Data/machine methods. */
    public static boolean validCommand(TerminalUiType type, int action, byte[] payload) {
        if (payload == null || payload.length < 16 || payload.length > MAX_ACTION_BYTES) return false;
        boolean drilling = type == TerminalUiType.SINGULARITY_HUB;
        boolean node = type == TerminalUiType.CACHE_NODE;
        if (node && action != 3 && action != 7 && action != 8) return false;
        if (action < 1 || action > (drilling ? 5 : 8)) return false;
        int rename = drilling ? 4 : 3;
        if (action == rename) {
            try {
                PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(payload));
                buffer.skipBytes(16);
                ByteBufUtils.readUTF8String(buffer);
                return !buffer.isReadable();
            } catch (RuntimeException error) {
                return false;
            }
        }
        if ((!drilling && (action == 2 || action == 4)) || (drilling && action == 1)) return payload.length == 17;
        if (!drilling && (action == 7 || action == 8)) return payload.length == 24;
        return payload.length == 16;
    }

    @Override
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id == 0) {
            if (buffer.readableBytes() < 12) return;
            long rev = buffer.readLong();
            int length = buffer.readInt();
            if (length < 0 || length > MAX_SNAPSHOT_BYTES || buffer.readableBytes() != length) return;
            byte[] payload = new byte[length];
            buffer.readBytes(payload);
            List<CacheHubTerminalData.CacheNodeInfo> cache = null;
            List<SingularityHubTerminalData.HubNodeInfo> drilling = null;
            if (factory.type == TerminalUiType.SINGULARITY_HUB) drilling = SingularityHubTerminalData.readList(payload);
            else cache = CacheHubTerminalData.readList(payload);
            if (cache != null || drilling != null) {
                final Snapshot next = new Snapshot(rev, cache, drilling);
                incoming.accumulateAndGet(
                    next,
                    (old, update) -> old == null || update.revision > old.revision ? update : old);
            }
        } else if (id == 2 && buffer.readableBytes() == 8) {
            Reply next = new Reply(buffer.readInt(), buffer.readInt());
            incomingReply
                .accumulateAndGet(next, (old, update) -> old == null || update.sequence >= old.sequence ? update : old);
        }
    }

    @Override
    public void dispose() {
        incoming.set(null);
        incomingReply.set(null);
        super.dispose();
    }

    private static final class Snapshot {

        final long revision;
        final List<CacheHubTerminalData.CacheNodeInfo> cache;
        final List<SingularityHubTerminalData.HubNodeInfo> drilling;

        Snapshot(long revision, List<CacheHubTerminalData.CacheNodeInfo> cache,
            List<SingularityHubTerminalData.HubNodeInfo> drilling) {
            this.revision = revision;
            this.cache = cache;
            this.drilling = drilling;
        }
    }

    private static final class Reply {

        final int sequence, status;

        Reply(int sequence, int status) {
            this.sequence = sequence;
            this.status = status;
        }
    }
}
