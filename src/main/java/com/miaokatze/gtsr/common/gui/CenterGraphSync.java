// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph.Node;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph.OutletFilter;
import com.miaokatze.gtsr.common.machine.orecenter.CenterProcess;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;
import com.miaokatze.gtsr.common.network.ServerPacketAuthority;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

final class CenterGraphSync extends SyncHandler<CenterGraphSync> {

    private static final int HEADER = 0, GRAPH = 1, RESULT = 2, EDIT = 3, TERMINAL = 4, TERMINAL_RESULT = 5;
    private static final int MAX_GRAPH_BYTES = 65536, MAX_TEXT = 8192;
    private static final CenterProcess[] PROCESSES = CenterProcess.values();

    enum Action {
        ADD,
        DELETE,
        TOGGLE,
        RENAME,
        PRIORITY,
        RULES,
        REORDER,
        FILTER,
        TARGET,
        APPEND_NODE,
        REMOVE_NODE,
        ASSEMBLY,
        RESET_LEDGER,
        MOVE,
        CIRCUIT,
        FREE_NODE,
        DUPLICATE,
        OUTLET_ALL,
        AUXILIARY
    }

    private static final Action[] ACTIONS = Action.values();

    static final class Snapshot {

        static final Snapshot EMPTY = new Snapshot(-1, 0, false, Collections.emptyList());

        private final long revision;
        private final int capacity;
        private final boolean writable;
        private final List<CenterGraph> graphs;

        private Snapshot(long revision, int capacity, boolean writable, List<CenterGraph> graphs) {
            this.revision = revision;
            this.capacity = capacity;
            this.writable = writable;
            this.graphs = Collections.unmodifiableList(new ArrayList<>(graphs));
        }

        long revision() {
            return revision;
        }

        int capacity() {
            return capacity;
        }

        boolean writable() {
            return writable;
        }

        List<CenterGraph> graphs() {
            return graphs;
        }
    }

    private final MTEMineralIntegrationCenter machine;
    private final PanelSyncManager manager;
    private final EntityPlayer player;
    private final NetHandlerPlayServer connection;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean queued = new AtomicBoolean();

    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile String status = "status.loading";
    private volatile boolean waiting;
    private long sentRevision = -1, incomingRevision;
    private int sentCapacity = -1, incomingCapacity, incomingCount;
    private boolean sentWritable, incomingWritable;
    private int nextCapacityPoll;
    private final List<CenterGraph> incoming = new ArrayList<>();
    private NBTTagCompound sentTerminal;
    private NBTTagList sentLedger;
    private volatile NBTTagCompound terminal = new NBTTagCompound();
    private volatile NBTTagList ledger = new NBTTagList();
    private int nextStatePoll;

    CenterGraphSync(MTEMineralIntegrationCenter machine, PanelSyncManager manager, EntityPlayer player) {
        this.machine = machine;
        this.manager = manager;
        this.player = player;
        connection = ServerPacketAuthority.connection(player);
        manager.addOpenListener(ignored -> generation.incrementAndGet());
    }

    Snapshot snapshot() {
        return snapshot;
    }

    String status() {
        return status;
    }

    NBTTagCompound terminal() {
        return terminal;
    }

    NBTTagList ledger() {
        return ledger;
    }

    void setStatus(String status) {
        this.status = status;
    }

    void edit(Action action, Snapshot base, long graph, int a, int b, String text) {
        if (waiting) return;
        if (!base.writable()) {
            setStatus("status.read_only");
            return;
        }
        if (text.length() > MAX_TEXT || action == Action.ADD && base.graphs()
            .size() >= base.capacity()) {
            setStatus(action == Action.ADD ? "status.capacity" : "status.invalid");
            return;
        }
        waiting = true;
        setStatus("status.saving");
        sync(EDIT, buffer -> {
            buffer.writeByte(action.ordinal());
            buffer.writeLong(base.revision());
            buffer.writeLong(graph);
            buffer.writeInt(a);
            buffer.writeInt(b);
            buffer.writeStringToBuffer(text);
        });
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (init || --nextStatePoll <= 0) {
            nextStatePoll = 10;
            sendTerminal(init);
        }
        long revision = machine.graphRevision();
        boolean writable = machine.canEditGraphs(player);
        if (!init && sentRevision == revision && sentWritable == writable && --nextCapacityPoll > 0) return;
        int capacity = machine.graphCapacity();
        nextCapacityPoll = 10;
        if (!init && sentRevision == revision && sentCapacity == capacity && sentWritable == writable) return;
        sendSnapshot(revision, capacity, writable);
    }

    private void sendTerminal(boolean init) {
        NBTTagCompound state = machine.terminalState();
        NBTTagList rows = machine.terminalLedger();
        boolean stateChanged = init || !state.equals(sentTerminal);
        boolean ledgerChanged = init || !rows.equals(sentLedger);
        if (!stateChanged && !ledgerChanged) return;
        NBTTagCompound message = new NBTTagCompound();
        if (stateChanged) message.setTag("State", state);
        if (ledgerChanged) message.setTag("Ledger", rows);
        try {
            byte[] payload = CompressedStreamTools.compress(message);
            if (payload.length > 1_048_576) throw new IOException("Mineral terminal payload exceeds its bound");
            syncToClient(TERMINAL, buffer -> buffer.writeBytes(payload));
            sentTerminal = state;
            sentLedger = rows;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot serialize mineral terminal", error);
        }
    }

    private void sendSnapshot(long revision, int capacity, boolean writable) {
        List<CenterGraph> graphs = machine.graphSnapshot();
        List<byte[]> payloads = new ArrayList<>(graphs.size());
        try {
            for (CenterGraph graph : graphs) {
                byte[] encoded = CompressedStreamTools.compress(graph.save());
                if (encoded.length > MAX_GRAPH_BYTES)
                    throw new IOException("Graph snapshot exceeds its transport bound");
                payloads.add(encoded);
            }
        } catch (IOException malformed) {
            throw new IllegalStateException("Cannot serialize mineral configuration on the server", malformed);
        }
        sentRevision = revision;
        sentCapacity = capacity;
        sentWritable = writable;
        syncToClient(HEADER, buffer -> {
            buffer.writeLong(revision);
            buffer.writeByte(capacity);
            buffer.writeBoolean(writable);
            buffer.writeByte(payloads.size());
        });
        for (byte[] payload : payloads) {
            syncToClient(GRAPH, buffer -> {
                buffer.writeInt(payload.length);
                buffer.writeBytes(payload);
            });
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id == TERMINAL) {
            int length = buffer.readableBytes();
            if (length < 1 || length > 1_048_576) throw new IOException("Invalid mineral terminal snapshot");
            byte[] bytes = new byte[length];
            buffer.readBytes(bytes);
            NBTTagCompound tag = CompressedStreamTools.func_152457_a(bytes, new NBTSizeTracker(2_097_152L));
            if (tag.hasKey("State")) terminal = tag.getCompoundTag("State");
            if (tag.hasKey("Ledger")) ledger = tag.getTagList("Ledger", 10);
            return;
        }
        if (id == TERMINAL_RESULT) {
            setStatus(buffer.readStringFromBuffer(32));
            waiting = false;
            return;
        }
        if (id == RESULT) {
            int result = buffer.readUnsignedByte();
            setStatus(switch (result) {
                case 0 -> "status.saved";
                case 1 -> "status.stale";
                case 2 -> "status.invalid";
                default -> "status.read_only";
            });
            waiting = false;
            return;
        }
        if (id == HEADER) {
            incomingRevision = buffer.readLong();
            incomingCapacity = buffer.readUnsignedByte();
            incomingWritable = buffer.readBoolean();
            incomingCount = buffer.readUnsignedByte();
            incoming.clear();
            if (incomingCapacity > CenterGraph.MAX_GRAPHS || incomingCount > CenterGraph.MAX_GRAPHS) {
                throw new IOException("Invalid mineral graph header");
            }
        } else if (id == GRAPH) {
            int length = buffer.readInt();
            if (incoming.size() >= incomingCount || length < 1
                || length > MAX_GRAPH_BYTES
                || length != buffer.readableBytes()) throw new IOException("Invalid mineral graph snapshot");
            byte[] data = new byte[length];
            buffer.readBytes(data);
            incoming.add(CenterGraph.load(CompressedStreamTools.func_152457_a(data, new NBTSizeTracker(2_097_152L))));
        } else {
            throw new IOException("Invalid mineral editor message");
        }
        if (incoming.size() == incomingCount) {
            snapshot = new Snapshot(incomingRevision, incomingCapacity, incomingWritable, incoming);
            if ("status.loading".equals(status)) setStatus(incomingWritable ? "status.ready" : "status.read_only");
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id != EDIT || buffer.readableBytes() < 26 || buffer.readableBytes() > 26 + MAX_TEXT * 4 + 4) return;
        int opcode = buffer.readUnsignedByte();
        if (opcode >= ACTIONS.length) return;
        long revision = buffer.readLong(), graph = buffer.readLong();
        int a = buffer.readInt(), b = buffer.readInt();
        String text = buffer.readStringFromBuffer(MAX_TEXT);
        if (text.length() > MAX_TEXT || buffer.isReadable()) return;
        ModularContainer container = manager.getContainer();
        long openedAt = generation.get();
        if (player == null || container == null
            || player.openContainer != container
            || !manager.getModularSyncManager()
                .isOpen()
            || !queued.compareAndSet(false, true)) return;
        Mui2ServerActions.enqueue(() -> {
            try {
                if (!ServerPacketAuthority.isCurrentPlayer(player, connection) || openedAt != generation.get()
                    || !manager.getModularSyncManager()
                        .isOpen()
                    || manager.getPlayer() != player
                    || manager.getContainer() != container
                    || player.openContainer != container
                    || !container.canInteractWith(player)) return;
                Action action = ACTIONS[opcode];
                if (action == Action.ASSEMBLY || action == Action.RESET_LEDGER) {
                    String result = "status.read_only";
                    if (machine.canEditGraphs(player)) {
                        if (action == Action.ASSEMBLY && text.isEmpty())
                            result = machine.applyModuleEdit(player, a, b >> 2, b & 3);
                        else if (action == Action.RESET_LEDGER && machine.resetTerminalLedger(player))
                            result = "status.saved";
                    }
                    sendTerminal(false);
                    detectAndSendChanges(false);
                    String receipt = result;
                    syncToClient(TERMINAL_RESULT, outgoing -> outgoing.writeStringToBuffer(receipt));
                    return;
                }
                int result = apply(action, revision, graph, a, b, text);
                if (result == 0 || result == 1) detectAndSendChanges(false);
                syncToClient(RESULT, outgoing -> outgoing.writeByte(result));
            } finally {
                queued.set(false);
            }
        });
    }

    private int apply(Action action, long revision, long id, int a, int b, String text) {
        if (!machine.canEditGraphs(player)) return 3;
        if (machine.graphRevision() != revision) return 1;
        List<CenterGraph> graphs = new ArrayList<>(machine.graphSnapshot());
        try {
            if (action == Action.ADD) {
                if (graphs.size() >= machine.graphCapacity()) return 2;
                boolean hasFallback = graphs.stream()
                    .anyMatch(g -> g.enabled && g.fallback());
                graphs.add(
                    new CenterGraph(
                        0,
                        "",
                        0,
                        !hasFallback,
                        Collections.emptyList(),
                        Collections.emptyList()));
            } else {
                int index = -1;
                for (int i = 0; i < graphs.size(); i++) {
                    if (graphs.get(i).id == id) {
                        index = i;
                        break;
                    }
                }
                if (index < 0) return 2;
                CenterGraph graph = graphs.get(index);
                switch (action) {
                    case DELETE -> graphs.remove(index);
                    case REORDER -> {
                        if (a < 0 || a >= graphs.size()) return 2;
                        graphs.add(a, graphs.remove(index));
                    }
                    default -> graphs.set(index, replace(graph, action, a, b, text));
                }
            }
            return machine.applyGraphEdit(player, revision, graphs) ? 0 : 2;
        } catch (IllegalArgumentException | IndexOutOfBoundsException invalid) {
            return 2;
        }
    }

    private static CenterGraph replace(CenterGraph graph, Action action, int a, int b, String text) {
        return switch (action) {
            case TOGGLE -> new CenterGraph(
                graph.id,
                graph.name,
                graph.priority,
                !graph.enabled,
                graph.rules(),
                graph.nodes);
            case RENAME -> new CenterGraph(
                graph.id,
                text.trim(),
                graph.priority,
                graph.enabled,
                graph.rules(),
                graph.nodes);
            case PRIORITY -> new CenterGraph(graph.id, graph.name, a, graph.enabled, graph.rules(), graph.nodes);
            case RULES -> new CenterGraph(
                graph.id,
                graph.name,
                graph.priority,
                graph.enabled,
                parseRules(text),
                graph.nodes);
            case FILTER, TARGET, APPEND_NODE, REMOVE_NODE, MOVE, CIRCUIT, FREE_NODE, DUPLICATE, OUTLET_ALL, AUXILIARY -> new CenterGraph(
                graph.id,
                graph.name,
                graph.priority,
                graph.enabled,
                graph.rules(),
                editNodes(graph.nodes, action, a, b, text));
            default -> throw new IllegalArgumentException();
        };
    }

    private static List<Node> editNodes(List<Node> existing, Action action, int index, int selected, String text) {
        if (selected < 0) throw new IllegalArgumentException();
        List<Node> nodes = new ArrayList<>(existing);
        if (action == Action.FREE_NODE) {
            if (nodes.size() >= CenterGraph.MAX_NODES) throw new IllegalArgumentException();
            CenterProcess process = PROCESSES[selected];
            int[] ports = new int[outlets(process)];
            Arrays.fill(ports, direct(process) ? CenterGraph.BUS : CenterGraph.UNCONNECTED);
            nodes.add(new Node(process, ports).withEmptyAuxiliary());
            return nodes;
        }
        if (action == Action.DUPLICATE) {
            for (int source : nodeSelection(text, index, existing.size())) {
                if (nodes.size() >= CenterGraph.MAX_NODES) throw new IllegalArgumentException();
                Node node = existing.get(source);
                int[] ports = new int[outlets(node.process)];
                Arrays.fill(ports, direct(node.process) ? CenterGraph.BUS : CenterGraph.UNCONNECTED);
                Node copy = node.withConfiguration(ports, node.circuit, node.filters());
                if (node.x != CenterGraph.AUTO_POSITION) copy = copy.withPosition(node.x + 24, node.y + 24);
                nodes.add(copy);
            }
            return nodes;
        }
        if (action == Action.REMOVE_NODE) {
            List<Integer> removed = nodeSelection(text, index, existing.size());
            nodes.clear();
            int[] remap = new int[existing.size()];
            int next = 0;
            for (int i = 0; i < existing.size(); i++) remap[i] = removed.contains(i) ? CenterGraph.BUS : next++;
            for (int i = 0; i < existing.size(); i++) {
                if (remap[i] == CenterGraph.BUS) continue;
                Node current = existing.get(i);
                int[] targets = current.targets();
                for (int slot = 0; slot < targets.length; slot++) if (targets[slot] >= 0)
                    targets[slot] = remap[targets[slot]];
                nodes.add(current.withConfiguration(targets, current.circuit, current.filters()));
            }
            return nodes;
        }
        if (action == Action.OUTLET_ALL) {
            for (int i = 0; i < nodes.size(); i++) {
                Node node = nodes.get(i);
                int[] targets = node.targets();
                for (int p = 0; p < targets.length; p++) if (targets[p] == CenterGraph.UNCONNECTED)
                    targets[p] = CenterGraph.BUS;
                nodes.set(i, node.withConfiguration(targets, node.circuit, node.filters()));
            }
            return nodes;
        }
        Node node = nodes.get(index);
        switch (action) {
            case FILTER -> {
                int slot = selected / 3, mode = selected % 3;
                checkPort(node, slot);
                List<OutletFilter> filters = new ArrayList<>(node.filters());
                filters.removeIf(item -> item.slot == slot);
                if (mode != 0) filters.add(new OutletFilter(slot, mode == 1, parseRules(text)));
                nodes.set(index, node.withConfiguration(node.targets(), node.circuit, filters));
            }
            case TARGET -> {
                int slot = selected / 65, target = selected % 65 - 1;
                checkPort(node, slot);
                nodes.set(index, target(node, slot, target));
            }
            case APPEND_NODE -> {
                int slot = selected / PROCESSES.length;
                checkPort(node, slot);
                if (node.target(slot) >= 0 || nodes.size() == CenterGraph.MAX_NODES) {
                    throw new IllegalArgumentException();
                }
                nodes.set(index, target(node, slot, nodes.size()));
                nodes.add(new Node(PROCESSES[selected % PROCESSES.length], new int[0]).withEmptyAuxiliary());
            }
            case MOVE -> {
                String[] rows = text.split(";");
                if (rows.length > CenterGraph.MAX_NODES) throw new IllegalArgumentException();
                for (String row : rows) {
                    String[] coordinates = row.split(",");
                    if (coordinates.length != 3) throw new IllegalArgumentException();
                    int i = Integer.parseInt(coordinates[0]);
                    nodes.set(i, nodes.get(i).withPosition(Integer.parseInt(coordinates[1]),
                        Integer.parseInt(coordinates[2])));
                }
            }
            case CIRCUIT -> nodes.set(index, node.withConfiguration(node.targets(), selected, node.filters()));
            case AUXILIARY -> nodes.set(index, node.withAuxiliary(selected, text));
            default -> throw new IllegalArgumentException();
        }
        return nodes;
    }

    private static List<Integer> nodeSelection(String text, int fallback, int count) {
        if (text.isEmpty()) {
            if (fallback < 0 || fallback >= count) throw new IllegalArgumentException();
            return Collections.singletonList(fallback);
        }
        List<Integer> result = new ArrayList<>();
        for (String number : text.split(",")) {
            int i = Integer.parseInt(number);
            if (i < 0 || i >= count || result.contains(i)) throw new IllegalArgumentException();
            result.add(i);
        }
        return result;
    }

    private static List<String> parseRules(String text) {
        if (text.trim().isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String line : text.split("[;\\r\\n]")) {
            String rule = line.trim();
            if (!rule.isEmpty() && !result.contains(rule)) result.add(rule);
            if (result.size() > CenterGraph.MAX_RULES) throw new IllegalArgumentException();
        }
        return result;
    }

    private static void checkPort(Node node, int slot) {
        if (direct(node.process) || slot >= outlets(node.process)) throw new IllegalArgumentException();
    }

    private static Node target(Node node, int slot, int next) {
        int[] previous = node.targets();
        int[] targets = Arrays.copyOf(previous, Math.max(slot + 1, previous.length));
        Arrays.fill(targets, previous.length, targets.length, CenterGraph.BUS);
        targets[slot] = next;
        return node.withConfiguration(targets, node.circuit, node.filters());
    }

    static boolean direct(CenterProcess process) {
        return switch (process) {
            case SEPARATION, SMELTING, ELECTROLYZER, FLUID_EXTRACTOR, REACTOR, FURNACE, RECYCLER -> true;
            default -> false;
        };
    }

    static int outlets(CenterProcess process) {
        return switch (process) {
            case CRUSHER, WASHER, CHEMICAL_BATH, THERMAL_CENTRIFUGE, SEPARATOR -> 3;
            case SIMPLE_WASHER, CENTRIFUGE -> 2;
            case SIFTER -> 6;
            default -> 1;
        };
    }
}
