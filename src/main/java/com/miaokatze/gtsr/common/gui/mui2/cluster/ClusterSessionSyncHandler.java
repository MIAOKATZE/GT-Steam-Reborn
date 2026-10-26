package com.miaokatze.gtsr.common.gui.mui2.cluster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.machine.cluster.ChainLink;
import com.miaokatze.gtsr.common.machine.cluster.ClusterTopology;
import com.miaokatze.gtsr.common.machine.cluster.LogisticsChain;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicLogisticsUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTEClusterUnitBase;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalActions;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalData;
import com.miaokatze.gtsr.common.terminal.TerminalNet;
import com.miaokatze.gtsr.common.terminal.TerminalServerSessions;

import io.netty.buffer.Unpooled;

/** One consumer of the original delta stream per formal container. */
public final class ClusterSessionSyncHandler extends SyncHandler<ClusterSessionSyncHandler> {

    public static final String SYNC_KEY = "cluster_session";
    private final ClusterTerminalFactory.ClusterGuiData data;
    private final MTESteamMineralLogisticsCluster machine;
    private final TileEntity tile;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final ConcurrentLinkedQueue<Runnable> incoming = new ConcurrentLinkedQueue<>();
    private final MTEClusterUnitBase[] members = new MTEClusterUnitBase[60];
    private byte[] memberTopology;
    private int memberSegments = -1;
    private final Map<Integer, ClusterDraftState> drafts = new HashMap<>();
    private long lastPoll = Long.MIN_VALUE;
    private long serverGeneration;
    private long sentGeneration;
    private long generation;
    private boolean serverPrimed;
    private int commandSequence, processedSequence;
    private int lastResultStatus;
    private boolean awaiting;
    private ClusterDraftState submittedDraft;
    private int submittedRevision, submittedPeak, submittedSlot;
    private long submittedGeneration;
    private List<Integer> submittedChain;
    private int acceptedVersion = -1;
    private boolean saveAccepted;
    private long rejectUntil;
    private boolean clearArmed;
    private long clearDeadline;
    private String receipt = "gtsr.cluster.mui2.ready";
    private ClusterSnapshot snapshot;
    public final ClusterPerformanceModel performance = new ClusterPerformanceModel();
    private byte[] topology = new byte[0];
    private byte[] run = new byte[0];
    private int currentSelectedSlot = -1;
    private List<Integer> authorityChain = new ArrayList<>();
    private int authorityPeak;

    public ClusterSessionSyncHandler(ClusterTerminalFactory.ClusterGuiData data,
        MTESteamMineralLogisticsCluster machine) {
        this.data = data;
        this.machine = machine;
        this.tile = data.getTileEntity();
        allowC2S();
    }

    private boolean active() {
        return isValid() && !getSyncManager().isClient()
            && data.getPlayer().openContainer == getSyncManager().getContainer()
            && (com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.remote(data)
                || data.getPlayer().worldObj == tile.getWorldObj())
            && data.getTileEntity() == tile
            && ClusterTerminalFactory.machine(data) == machine
            && ClusterTerminalFactory.canUse(data);
    }

    private void refreshMembers() {
        MTEClusterUnitBase[] next = new MTEClusterUnitBase[60];
        for (ClusterTopology.SlotSnapshot slot : machine.getTopology()
            .getSlots()) {
            int id = slot.segment * 3 + slot.pad;
            if (id >= 0 && id < 60) next[id] = slot.unit;
        }
        byte[] structure = machine.buildTopologySnapshot();
        int segments = machine.getTopology()
            .getSegmentCount();
        if (serverGeneration == 0 || !Arrays.equals(next, members)
            || !Arrays.equals(structure, memberTopology)
            || segments != memberSegments) {
            System.arraycopy(next, 0, members, 0, 60);
            memberTopology = structure;
            memberSegments = segments;
            serverGeneration++;
        }
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        long now = TerminalServerSessions.currentServerTick();
        if (!init && lastPoll != Long.MIN_VALUE && now - lastPoll < 10) return;
        if (!active()) return;
        if (!serverPrimed) {
            ClusterTerminalData.beginSession(
                data.getPlayer()
                    .getUniqueID(),
                this);
            serverPrimed = true;
        }
        lastPoll = now;
        refreshMembers();
        sendSnapshot();
    }

    private int sendSnapshot() {
        byte[] payload = ClusterTerminalData.assembleSnapshot(
            machine,
            data.getPlayer()
                .getUniqueID());
        if (ClusterSnapshot.mask(payload) == 0 && sentGeneration == serverGeneration) return -1;
        int version = TerminalNet.nextSnapshotVersion();
        sentGeneration = serverGeneration;
        syncToClient(0, buffer -> {
            buffer.writeLong(data.epoch);
            buffer.writeInt(version);
            buffer.writeLong(serverGeneration);
            buffer.writeInt(payload.length);
            buffer.writeBytes(payload);
        });
        return version;
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id != 1 || buffer.readableBytes() < 29) return;
        long epoch = buffer.readLong();
        int seq = buffer.readInt();
        int ordinal = buffer.readUnsignedByte();
        long expectedGeneration = buffer.readLong();
        int slot = buffer.readInt();
        int length = buffer.readInt();
        if (epoch != data.epoch || seq < 1 || length < 0 || length > 72 || buffer.readableBytes() != length) return;
        byte[] payload = new byte[length];
        buffer.readBytes(payload);
        if (!pending.compareAndSet(false, true)) return;
        Mui2ServerActions.enqueue(() -> {
            try {
                if (!active()) return;
                if (seq <= processedSequence) {
                    result(seq, seq == processedSequence ? lastResultStatus : 2, -1);
                    return;
                }
                processedSequence = seq;
                refreshMembers();
                int status = execute(ordinal, slot, expectedGeneration, payload);
                lastResultStatus = status;
                // Read/modify/send all occur at server tick END, never the Netty callback.
                int version = sendSnapshot();
                result(seq, status, version);
            } finally {
                pending.set(false);
            }
        });
    }

    private int execute(int ordinal, int slot, long expectedGeneration, byte[] payload) {
        ClusterTerminalActions[] actions = ClusterTerminalActions.values();
        if (ordinal < 0 || ordinal >= actions.length) return 1;
        ClusterTerminalActions action = actions[ordinal];
        if (action == ClusterTerminalActions.TOGGLE_POWER || action == ClusterTerminalActions.CLEAR_STATS) {
            if (payload.length != 0) return 1;
        } else if (action == ClusterTerminalActions.SELECT_LOGISTICS || action == ClusterTerminalActions.SAVE_CHAIN) {
            if (slot < 0 || slot >= 60
                || expectedGeneration != serverGeneration
                || !(members[slot] instanceof MTEBasicLogisticsUnit unit)
                || !machine.getTopology()
                    .getLogisticsUnits()
                    .contains(unit))
                return 3;
            if (action == ClusterTerminalActions.SELECT_LOGISTICS) {
                if (payload.length != 0) return 1;
                PacketBuffer select = new PacketBuffer(Unpooled.buffer(4));
                select.writeInt(
                    machine.getTopology()
                        .getLogisticsUnits()
                        .indexOf(unit));
                payload = new byte[4];
                select.readBytes(payload);
            } else {
                if (machine.getSelectedLogisticsUnit() != unit || machine.getStructureTierIndex() < 0) return 3;
                if (payload.length < 12) return 1;
                PacketBuffer chain = new PacketBuffer(Unpooled.wrappedBuffer(payload));
                int length = chain.readInt();
                if (length < 1 || length > 16 || chain.readableBytes() != length * 4 + 4) return 1;
                int[] ordinals = new int[length];
                for (int i = 0; i < length; i++) {
                    ordinals[i] = chain.readInt();
                    if (ordinals[i] < 0 || ordinals[i] >= ChainLink.values().length) return 1;
                }
                if (!LogisticsChain.fromOrdinalArray(ordinals)
                    .isValidStructure()) return 1;
            }
        } else return 1; // All frozen placeholder actions remain unavailable.
        ClusterTerminalData.executeAction(machine, ordinal, payload);
        return 0;
    }

    private void result(int sequence, int status, int version) {
        syncToClient(2, buffer -> {
            buffer.writeLong(data.epoch);
            buffer.writeInt(sequence);
            buffer.writeByte(status);
            buffer.writeInt(version);
        });
    }

    @Override
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id == 0 && buffer.readableBytes() >= 24) {
            long epoch = buffer.readLong();
            int version = buffer.readInt();
            long nextGeneration = buffer.readLong();
            int length = buffer.readInt();
            if (epoch != data.epoch || length < 0
                || length > TerminalNet.MAX_PAYLOAD_BYTES
                || buffer.readableBytes() != length) return;
            byte[] payload = new byte[length];
            buffer.readBytes(payload);
            incoming.offer(() -> applySnapshot(version, nextGeneration, payload));
        } else if (id == 2 && buffer.readableBytes() == 17) {
            long epoch = buffer.readLong();
            int seq = buffer.readInt();
            int status = buffer.readUnsignedByte();
            int version = buffer.readInt();
            if (epoch == data.epoch) incoming.offer(() -> applyResult(seq, status, version));
        }
    }

    /** Called by the actual client widget update; packet reads never mutate UI state. */
    public void clientTick() {
        if (!isValid() || !getSyncManager().isClient()) return;
        Runnable task;
        while ((task = incoming.poll()) != null) task.run();
        if (clearArmed && now() >= clearDeadline) clearArmed = false;
        performance.update(snapshot);
    }

    private void applySnapshot(int version, long nextGeneration, byte[] payload) {
        ClusterSnapshot next = ClusterSnapshot.merge(snapshot, version, payload);
        if (next == snapshot) return;
        snapshot = next;
        performance.update(snapshot);
        generation = nextGeneration;
        if ((next.changedMask & (1 << 10)) != 0) topology = next.getBytes(ClusterTerminalData.KEY_TOPO);
        if ((next.changedMask & (1 << 11)) != 0) run = next.getBytes(ClusterTerminalData.KEY_RUN);
        currentSelectedSlot = -1;
        int selected = integer(ClusterTerminalData.KEY_SEL_LOGI, -1);
        for (int slot = 2; slot < 60; slot += 3)
            if (topo(slot, 0) == 13 && topo(slot, 4) == selected) currentSelectedSlot = slot;
        authorityChain = parseChain();
        authorityPeak = parsePeak();
        for (ClusterDraftState existing : drafts.values())
            if (existing.dirty && existing.generation != generation) existing.invalidTarget = true;
        ClusterDraftState draft = draft();
        if (draft != null) draft.follow(chain(), peak(), generation);
        confirmSaved();
    }

    private void applyResult(int seq, int status, int version) {
        if (seq != commandSequence) return;
        awaiting = false;
        receipt = status == 0 ? "gtsr.cluster.mui2.applied"
            : status == 3 ? "gtsr.cluster.mui2.target_changed" : "gtsr.cluster.mui2.rejected";
        if (status != 0) rejectUntil = now() + 1500;
        if (submittedDraft != null) {
            saveAccepted = status == 0;
            acceptedVersion = version;
            confirmSaved();
        }
    }

    private void confirmSaved() {
        if (!saveAccepted || submittedDraft == null
            || snapshot == null
            || generation != submittedGeneration
            || snapshot.version < acceptedVersion
            || selectedSlot() != submittedSlot) return;
        if (chain().equals(submittedChain) && peak() == submittedPeak) {
            if (submittedDraft.revision == submittedRevision) submittedDraft.dirty = false;
            submittedDraft = null;
            saveAccepted = false;
        }
    }

    public boolean submit(ClusterTerminalActions action, int slot, byte[] payload) {
        if (awaiting || snapshot == null || !isValid()) return false;
        awaiting = true;
        receipt = "gtsr.cluster.mui2.pending";
        int seq = ++commandSequence;
        sync(1, buffer -> {
            buffer.writeLong(data.epoch);
            buffer.writeInt(seq);
            buffer.writeByte(action.ordinal());
            buffer.writeLong(generation);
            buffer.writeInt(slot);
            buffer.writeInt(payload.length);
            buffer.writeBytes(payload);
        });
        return true;
    }

    public void select(int slot) {
        submit(ClusterTerminalActions.SELECT_LOGISTICS, slot, new byte[0]);
    }

    public void discardDraft() {
        int slot = selectedSlot();
        if (slot >= 0) drafts.remove(slot);
        submittedDraft = null;
        saveAccepted = false;
    }

    public void save() {
        ClusterDraftState draft = draft();
        if (draft == null || !draft.valid()) {
            rejectUntil = now() + 1500;
            return;
        }
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        buffer.writeInt(draft.ordinals.size());
        for (int value : draft.ordinals) buffer.writeInt(value);
        buffer.writeInt(draft.peak);
        byte[] payload = new byte[buffer.readableBytes()];
        buffer.readBytes(payload);
        if (submit(ClusterTerminalActions.SAVE_CHAIN, selectedSlot(), payload)) {
            submittedDraft = draft;
            submittedRevision = draft.revision;
            submittedPeak = draft.peak;
            submittedSlot = selectedSlot();
            submittedChain = new ArrayList<>(draft.ordinals);
            saveAccepted = false;
            submittedGeneration = generation;
        }
    }

    public void clearStats() {
        long now = now();
        if (clearArmed && now < clearDeadline) {
            if (submit(ClusterTerminalActions.CLEAR_STATS, -1, new byte[0])) clearArmed = false;
        } else {
            clearArmed = true;
            clearDeadline = now + 3000;
        }
    }

    public boolean clearArmed() {
        return clearArmed && now() < clearDeadline;
    }

    public String receipt() {
        if (now() < rejectUntil) return "gtsr.cluster.mui2.rejected";
        ClusterDraftState draft = draft();
        if (draft != null && draft.invalidTarget) return "gtsr.cluster.mui2.target_changed";
        if (!awaiting && draft != null && draft.dirty) return "gtsr.cluster.gui.link.chain.unsaved";
        return receipt;
    }

    private static long now() {
        return System.nanoTime() / 1000000L;
    }

    public ClusterSnapshot snapshot() {
        return snapshot;
    }

    public int integer(String key, int fallback) {
        return snapshot == null ? fallback : snapshot.getInt(key, fallback);
    }

    public String string(String key) {
        return snapshot == null ? "" : snapshot.getStr(key, "");
    }

    public int topo(int slot, int offset) {
        int index = slot * 5 + offset;
        return topology != null && slot >= 0 && offset >= 0 && offset < 5 && index < topology.length
            ? topology[index] & 255
            : 255;
    }

    public int run(int slot) {
        return run != null && slot >= 0 && slot < run.length ? run[slot] & 255 : 255;
    }

    public int selectedSlot() {
        return currentSelectedSlot;
    }

    public ClusterDraftState draft() {
        int slot = selectedSlot();
        if (slot < 0) return null;
        ClusterDraftState draft = drafts.get(slot);
        if (draft == null) {
            draft = new ClusterDraftState();
            draft.follow(chain(), peak(), generation);
            drafts.put(slot, draft);
        }
        return draft;
    }

    private List<Integer> chain() {
        return authorityChain;
    }

    private int peak() {
        return authorityPeak;
    }

    private List<Integer> parseChain() {
        List<Integer> out = new ArrayList<>();
        for (String part : string(ClusterTerminalData.KEY_LE_CHAIN).split(",")) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value >= 0 && value < ChainLink.values().length) out.add(value);
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    private int parsePeak() {
        int selected = integer(ClusterTerminalData.KEY_SEL_LOGI, -1);
        for (String row : string(ClusterTerminalData.KEY_LE_CHAINS).split(",")) {
            String[] fields = row.split(":");
            try {
                if (fields.length >= 3 && Integer.parseInt(fields[0]) == selected)
                    return Math.max(0, Integer.parseInt(fields[2].split("\\.")[0]));
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    @Override
    public void dispose() {
        if (isValid() && !getSyncManager().isClient() && serverPrimed) Mui2ServerActions.enqueue(
            () -> ClusterTerminalData.releaseSession(
                data.getPlayer()
                    .getUniqueID(),
                this));
        incoming.clear();
        drafts.clear();
        super.dispose();
    }
}
