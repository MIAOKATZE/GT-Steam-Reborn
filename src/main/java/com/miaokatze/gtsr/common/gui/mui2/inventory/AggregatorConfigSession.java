package com.miaokatze.gtsr.common.gui.mui2.inventory;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.NetHandlerPlayServer;

import com.miaokatze.gtsr.common.network.ServerPacketAuthority;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;
import com.miaokatze.gtsr.common.terminal.AggregatorTerminalData;
import com.miaokatze.gtsr.common.terminal.AggregatorTerminalData.OreEntry;
import com.miaokatze.gtsr.common.terminal.TerminalNet;
import com.miaokatze.gtsr.common.terminal.TerminalServerSessions;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** One explicit consumer per live container. Slot changes never imply a forceRefreshPool action. */
public final class AggregatorConfigSession extends SyncHandler<AggregatorConfigSession> {

    public static final String KEY = "aggregator_session";
    private static final int MAX_SNAPSHOT_BYTES = 1024 * 1024;
    private final PosGuiData data;
    private final MTECrustMatterAggregator machine;
    private final TileEntity tile;
    private final NetHandlerPlayServer connection;
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile AggregatorTerminalData.Snapshot snapshot;
    private volatile String receipt = "gtsr.aggregator_config.mui2.ready";
    private volatile int completedSequence;
    private int sequence, appliedSequence, clientRevision;
    private boolean awaiting;
    private long lastPoll = Long.MIN_VALUE;
    private byte[] lastSnapshot;

    public AggregatorConfigSession(PosGuiData data, MTECrustMatterAggregator machine) {
        this.data = data;
        this.connection = ServerPacketAuthority.connection(data.getPlayer());
        this.machine = machine;
        this.tile = data.getTileEntity();
        allowC2S();
    }

    public AggregatorTerminalData.Snapshot snapshot() {
        return snapshot;
    }

    public String receiptKey() {
        if (completedSequence == sequence) awaiting = false;
        return receipt;
    }

    public void submit(int action, OreEntry ore) {
        if (!getSyncManager().isClient()) return;
        receiptKey();
        if (awaiting || action < 1 || action > 7) return;
        ByteBuf payload = Unpooled.buffer();
        try {
            if (action == 3 || action == 6) {
                if (ore == null || ore.uniqueId == null || ore.uniqueId.length() > 256) return;
                ByteBufUtils.writeUTF8String(payload, ore.uniqueId);
                payload.writeInt(ore.meta);
            }
            byte[] bytes = new byte[payload.readableBytes()];
            payload.readBytes(bytes);
            int request = ++sequence;
            awaiting = true;
            receipt = "gtsr.aggregator_config.mui2.pending";
            syncToServer(1, buffer -> {
                buffer.writeInt(request);
                buffer.writeByte(action);
                buffer.writeInt(bytes.length);
                buffer.writeBytes(bytes);
            });
        } finally {
            payload.release();
        }
    }

    private boolean active() {
        return isValid() && !getSyncManager().isClient()
            && ServerPacketAuthority.isCurrentPlayer(data.getPlayer(), connection)
            && data.getPlayer().openContainer == getSyncManager().getContainer()
            && (com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.remote(data)
                || data.getPlayer().worldObj == tile.getWorldObj())
            && data.getTileEntity() == tile
            && AggregatorConfigFactory.machine(data) == machine
            && AggregatorConfigFactory.canUse(data);
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        long now = TerminalServerSessions.currentServerTick();
        if (!init && lastPoll != Long.MIN_VALUE && now - lastPoll < 20) return;
        lastPoll = now;
        if (!active()) return;
        byte[] bytes = AggregatorTerminalData.assembleSnapshot(machine);
        if (!init && Arrays.equals(bytes, lastSnapshot)) return;
        lastSnapshot = bytes;
        int revision = TerminalNet.nextSnapshotVersion();
        syncToClient(0, buffer -> {
            buffer.writeInt(revision);
            buffer.writeInt(bytes.length);
            buffer.writeBytes(bytes);
        });
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id != 1 || buffer.readableBytes() < 9) return;
        int request = buffer.readInt();
        int action = buffer.readUnsignedByte();
        int length = buffer.readInt();
        if (request < 1 || action < 1 || action > 7 || length < 0 || length > 1024 || length != buffer.readableBytes())
            return;
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        if (!validPayload(action, bytes) || !pending.compareAndSet(false, true)) return;
        Mui2ServerActions.enqueue(() -> {
            try {
                if (!active()) return;
                boolean accepted = request > appliedSequence;
                if (accepted) {
                    appliedSequence = request;
                    AggregatorTerminalData.executeAction(machine, data.getPlayer(), action, bytes);
                    lastSnapshot = null;
                    lastPoll = Long.MIN_VALUE;
                }
                syncToClient(2, reply -> {
                    reply.writeInt(request);
                    reply.writeBoolean(accepted);
                });
            } finally {
                pending.set(false);
            }
        });
    }

    private static boolean validPayload(int action, byte[] bytes) {
        if (action != 3 && action != 6) return bytes.length == 0;
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        try {
            String name = ByteBufUtils.readUTF8String(buf);
            if (name.isEmpty() || name.length() > 256 || name.indexOf(':') < 1 || buf.readableBytes() != 4)
                return false;
            buf.readInt();
            return true;
        } catch (RuntimeException malformed) {
            return false;
        } finally {
            buf.release();
        }
    }

    @Override
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id == 0 && buffer.readableBytes() >= 8) {
            int revision = buffer.readInt();
            int length = buffer.readInt();
            if (length < 1 || length > MAX_SNAPSHOT_BYTES || length != buffer.readableBytes()) return;
            byte[] bytes = new byte[length];
            buffer.readBytes(bytes);
            AggregatorTerminalData.Snapshot decoded = AggregatorTerminalData.readSnapshot(bytes);
            if (decoded != null && revision > clientRevision) {
                clientRevision = revision;
                snapshot = decoded;
            }
        } else if (id == 2 && buffer.readableBytes() == 5) {
            int request = buffer.readInt();
            boolean accepted = buffer.readBoolean();
            if (request != sequence) return;
            receipt = accepted ? "gtsr.aggregator_config.mui2.applied" : "gtsr.aggregator_config.mui2.rejected";
            completedSequence = request;
        }
    }
}
