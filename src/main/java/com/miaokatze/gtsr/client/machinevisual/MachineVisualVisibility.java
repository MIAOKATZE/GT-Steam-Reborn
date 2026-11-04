package com.miaokatze.gtsr.client.machinevisual;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Exact-cell masks commit only with the chunk output that actually reached the renderer. */
public final class MachineVisualVisibility {

    private static final Map<String, CellEntry> CELLS = new ConcurrentHashMap<>();
    private static final Map<Object, BuildReceipt> BUILDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, BuildReceipt> OUTPUTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, SectionState> SECTIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Dependency, RestorationRequest> RESTORATIONS = new java.util.LinkedHashMap<>();
    private static long restorationPumpTick = Long.MIN_VALUE;
    private static int restorationActions;
    private static final ThreadLocal<BuildContext> BUILD = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PROXY = ThreadLocal.withInitial(() -> false);
    private static volatile int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    private static volatile int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
    public static long committedOutputs, retiredProxyCubes;
    /** Section cleanup actions, not decorative draw submissions or guessed timeout releases. */
    public static long restorationRequestsEnqueued, mandatoryRestorations, rendererRetirementAcks, absentRendererAcks;

    private MachineVisualVisibility() {}

    static void register() {
        MinecraftForge.EVENT_BUS.register(new MachineVisualVisibility());
    }

    public static void beginBuild(Object task, World world) {
        if (CELLS.isEmpty()) return;
        BUILD.set(new BuildContext(task, world));
    }

    public static void endBuild() {
        BUILD.remove();
    }

    /** Default absent cells to static for this exact rebuild, including members removed before tessellation. */
    public static void beginSection(Object task, World world, int x, int y, int z) {
        if (CELLS.isEmpty() || world == null
            || x + 16 < minX - 1
            || x > maxX + 1
            || y + 16 < minY - 1
            || y > maxY + 1
            || z + 16 < minZ - 1
            || z > maxZ + 1) return;
        BuildReceipt receipt = new BuildReceipt();
        receipt.world = world;
        for (CellEntry cell : CELLS.values()) {
            if (cell.owner.world == world && (cell.x >> 4) == (x >> 4)
                && (cell.y >> 4) == (y >> 4)
                && (cell.z >> 4) == (z >> 4)) {
                long epoch = cell.owner.maskEpochs[Integer.numberOfTrailingZeros(cell.groupBit)];
                receipt.cells.put(cell, new CellDecision(false, epoch));
            }
        }
        BUILDS.put(task, receipt);
    }

    /** Reject unrelated world geometry before constructing position keys or allocating a build scope. */
    public static boolean mayOwn(int x, int y, int z) {
        return !CELLS.isEmpty() && x >= minX - 1
            && x <= maxX + 1
            && y >= minY - 1
            && y <= maxY + 1
            && z >= minZ - 1
            && z <= maxZ + 1;
    }

    /** The output owns exactly the suppression decisions used while tessellating it. */
    public static void finishBuild(Object task, Object output) {
        BuildReceipt decisions = BUILDS.remove(task);
        if (output != null && decisions != null) OUTPUTS.put(output, decisions);
    }

    /** Called after uploadMeshes (Angelica) or completed display-list compilation (vanilla). */
    public static void uploaded(Object output) {
        BuildReceipt decisions = OUTPUTS.remove(output);
        if (decisions == null) return;
        committedOutputs++;
        for (Map.Entry<CellEntry, CellDecision> decision : decisions.cells.entrySet()) {
            CellEntry built = decision.getKey();
            CellEntry current = CELLS.get(built.key);
            if (current == null || current.owner.world != built.owner.world) continue;
            current.uploadedHidden = decision.getValue().hidden;
            current.uploadedEpoch = decision.getValue().epoch;
            current.uploaded = true;
            current.rendererAbsent = false;
        }
        for (Map.Entry<Dependency, Long> item : decisions.neighbours.entrySet()) {
            Dependency dependency = item.getKey();
            dependency.owner.neighbourUploads.computeIfAbsent(dependency.bit, ignored -> new java.util.HashMap<>())
                .put(dependency.section, item.getValue());
        }
        releaseRestoredGroups();
    }

    /** Empty native outputs have no block callback; their known section object still proves static restoration. */
    public static void uploadedSection(Object output, Object renderSection, int x, int y, int z, boolean empty) {
        SectionState known = SECTIONS.get(renderSection);
        if (known != null && known.retired) {
            OUTPUTS.remove(output);
            return;
        }
        BuildReceipt receipt = OUTPUTS.get(output);
        if (receipt != null && receipt.world != null && known == null) {
            known = new SectionState(receipt.world, null, x >> 4, y >> 4, z >> 4);
            SECTIONS.put(renderSection, known);
        }
        if (receipt == null && empty) {
            if (known != null) {
                beginSection(output, known.world, x, y, z);
                finishBuild(output, output);
                receipt = OUTPUTS.get(output);
            }
        }
        if (receipt != null) for (CellEntry built : receipt.cells.keySet()) {
            CellEntry current = CELLS.get(built.key);
            if (current != null && current.owner.world == built.owner.world) current.renderSection = renderSection;
        }
        uploaded(output);
    }

    /** Called by the concrete renderer after metadata installation; binds its actual manager and world. */
    public static void sectionPresent(World world, Object manager, Object section, int x, int y, int z) {
        SectionState state = SECTIONS.get(section);
        if (state == null) {
            if (CELLS.isEmpty() || x + 16 < minX - 1
                || x > maxX + 1
                || y + 16 < minY - 1
                || y > maxY + 1
                || z + 16 < minZ - 1
                || z > maxZ + 1) return;
            SECTIONS.put(section, new SectionState(world, manager, x >> 4, y >> 4, z >> 4));
        } else if (state.world == world && !state.retired) state.bindManager(manager);
    }

    /** Actual native region removal/deletion is an absence acknowledgement, never a synthetic static upload. */
    public static void sectionDisposed(World world, Object manager, Object section) {
        SectionState state = SECTIONS.get(section);
        if (state == null || state.world != world || state.manager() != null && state.manager() != manager) return;
        if (!state.retired) rendererRetirementAcks++;
        state.bindManager(manager);
        state.retired = true;
        for (RestorationRequest request : RESTORATIONS.values())
            if (request.world == world && request.expectedSection == section) {
                request.queued = true;
                request.state = "RENDERER_RETIRED_ACK";
            }
        for (CellEntry cell : CELLS.values()) if (cell.owner.world == world && cell.renderSection == section) {
            acknowledgeAbsence(cell);
        }
        releaseRestoredGroups();
        pruneRestorations();
    }

    /** The manager's native destroy returned after region buffers and the chunk renderer were deleted. */
    public static void managerDisposed(World world, Object manager) {
        java.util.List<Object> sections = new java.util.ArrayList<>();
        synchronized (SECTIONS) {
            for (Map.Entry<Object, SectionState> item : SECTIONS.entrySet())
                if (item.getValue().world == world && item.getValue()
                    .manager() == manager) sections.add(item.getKey());
        }
        for (Object section : sections) sectionDisposed(world, manager, section);
    }

    private static void acknowledgeAbsence(CellEntry cell) {
        cell.uploadedHidden = false;
        cell.uploaded = true;
        cell.uploadedEpoch = cell.owner.maskEpochs[Integer.numberOfTrailingZeros(cell.groupBit)];
        cell.rendererAbsent = true;
    }

    /** At most two exact restoration sections may be promoted in one advancing client tick. */
    public static java.util.List<RestorationRequest> pollRestorations(World world, Object manager) {
        pruneRestorations();
        long tick = MachineVisualClient.clientTick();
        if (restorationPumpTick != tick) {
            restorationPumpTick = tick;
            restorationActions = 0;
        }
        java.util.List<RestorationRequest> selected = new java.util.ArrayList<>();
        if (restorationActions >= 2) return selected;
        for (RestorationRequest request : RESTORATIONS.values()) {
            if (request.world != world || request.queued) continue;
            if (request.expectedManager != null && request.expectedManager != manager) {
                request.state = "WAITING_OWNING_MANAGER";
                continue;
            }
            selected.add(request);
            if (++restorationActions >= 2) break;
        }
        // A stale native identity cannot monopolize the bounded cleanup allowance.
        for (RestorationRequest request : selected) {
            RESTORATIONS.remove(request.key);
            RESTORATIONS.put(request.key, request);
        }
        return selected;
    }

    public static boolean bindRestoration(RestorationRequest request, World world, Object manager, Object section) {
        if (!currentRestoration(request) || request.world != world
            || request.expectedManager != null && request.expectedManager != manager
            || request.expectedSection != null && request.expectedSection != section) {
            request.state = "WAITING_EXACT_SECTION_RETIREMENT";
            return false;
        }
        SectionState state = SECTIONS.get(section);
        if (state == null) SECTIONS
            .put(section, new SectionState(world, manager, request.sectionX, request.sectionY, request.sectionZ));
        else if (state.world != world || state.retired) return false;
        request.expectedManager = manager;
        request.expectedSection = section;
        return true;
    }

    public static void restorationPromoted(RestorationRequest request) {
        request.queued = true;
        request.state = "NATIVE_IMPORTANT_QUEUED";
        mandatoryRestorations++;
    }

    /** A missing current registry entry proves absence only for cells never bound to a live GPU section. */
    public static void restorationSectionAbsent(RestorationRequest request, World world, Object manager) {
        if (!currentRestoration(request) || request.world != world || request.expectedSection != null) return;
        boolean acknowledged = false;
        for (CellEntry cell : CELLS.values()) {
            if (cell.owner != request.owner || cell.groupBit != request.bit
                || (cell.x >> 4) != request.sectionX
                || (cell.y >> 4) != request.sectionY
                || (cell.z >> 4) != request.sectionZ) continue;
            if (cell.renderSection != null) {
                request.state = "WAITING_EXACT_SECTION_RETIREMENT";
                return;
            }
        }
        for (CellEntry cell : CELLS.values()) if (cell.owner == request.owner && cell.groupBit == request.bit
            && (cell.x >> 4) == request.sectionX
            && (cell.y >> 4) == request.sectionY
            && (cell.z >> 4) == request.sectionZ) {
                acknowledgeAbsence(cell);
                acknowledged = true;
            }
        if (acknowledged) absentRendererAcks++;
        releaseRestoredGroups();
        pruneRestorations();
    }

    private static boolean currentRestoration(RestorationRequest request) {
        int index = Integer.numberOfTrailingZeros(request.bit);
        return (request.owner.restoringMask & request.bit) != 0 && request.owner.maskEpochs[index] == request.epoch;
    }

    private static void pruneRestorations() {
        RESTORATIONS.values()
            .removeIf(request -> !currentRestoration(request));
    }

    /** Detached diagnostic values only. No native queue promotion, world writes or lease changes. */
    static Map<String, Object> diagnostics(Iterable<MachineVisualClient.Entry> registered) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("maxMandatorySectionsPerClientTick", 2);
        result.put("requestsEnqueued", restorationRequestsEnqueued);
        result.put("mandatoryRequestsPromoted", mandatoryRestorations);
        result.put("rendererRetirementAcks", rendererRetirementAcks);
        result.put("unboundAbsentRendererAcks", absentRendererAcks);
        result.put("pendingRequests", RESTORATIONS.size());
        java.util.List<Map<String, Object>> requests = new java.util.ArrayList<>();
        for (RestorationRequest request : RESTORATIONS.values()) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("nonce", request.owner.nonce);
            row.put("groupBit", request.bit);
            row.put("epoch", request.epoch);
            row.put("section", java.util.Arrays.asList(request.sectionX, request.sectionY, request.sectionZ));
            row.put("expectedSectionIdentity", System.identityHashCode(request.expectedSection));
            row.put("expectedManagerIdentity", System.identityHashCode(request.expectedManager));
            row.put("queued", request.queued);
            row.put("state", request.state);
            requests.add(Collections.unmodifiableMap(row));
        }
        result.put("requests", Collections.unmodifiableList(requests));
        Map<MachineVisualClient.Entry, Boolean> owners = new IdentityHashMap<>();
        for (MachineVisualClient.Entry entry : registered) owners.put(entry, true);
        for (CellEntry cell : CELLS.values()) owners.put(cell.owner, true);
        java.util.List<Map<String, Object>> entries = new java.util.ArrayList<>();
        for (MachineVisualClient.Entry entry : owners.keySet()) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("definition", entry.definition.id);
            row.put("nonce", entry.nonce);
            row.put("origin", java.util.Arrays.asList(entry.originX, entry.originY, entry.originZ));
            row.put("worldIdentity", System.identityHashCode(entry.world));
            row.put("currentWorld", entry.world == Minecraft.getMinecraft().theWorld);
            row.put("mask", entry.mask);
            row.put("restoringMask", entry.restoringMask);
            row.put("retired", entry.retired);
            java.util.List<Map<String, Object>> cells = new java.util.ArrayList<>();
            for (int group = 0; group < entry.definition.groups.size(); group++) {
                for (MachineVisualDefinition.Cell member : entry.definition.groups.get(group).members) {
                    int[] position = entry.at(member.x, member.y, member.z);
                    CellEntry cell = CELLS.get(key(position[0], position[1], position[2]));
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("position", java.util.Arrays.asList(position[0], position[1], position[2]));
                    item.put("groupBit", 1 << group);
                    item.put("expectedEpoch", entry.maskEpochs[group]);
                    item.put("registered", cell != null && cell.owner == entry);
                    if (cell != null && cell.owner == entry) {
                        item.put("uploadedEpoch", cell.uploadedEpoch);
                        item.put("uploaded", cell.uploaded);
                        item.put("uploadedHidden", cell.uploadedHidden);
                        item.put("rendererAbsentAck", cell.rendererAbsent);
                        item.put("sectionIdentity", System.identityHashCode(cell.renderSection));
                        SectionState state = SECTIONS.get(cell.renderSection);
                        item.put("sectionKnown", state != null);
                        if (state != null) {
                            item.put("managerIdentity", state.managerIdentity);
                            item.put("sectionRetiredAck", state.retired);
                        }
                    }
                    cells.add(Collections.unmodifiableMap(item));
                }
            }
            row.put("cells", Collections.unmodifiableList(cells));
            entries.add(Collections.unmodifiableMap(row));
        }
        result.put("entries", Collections.unmodifiableList(entries));
        return Collections.unmodifiableMap(result);
    }

    public static boolean hidden(IBlockAccess access, Block block, int x, int y, int z) {
        if (!mayOwn(x, y, z) || PROXY.get()) return false;
        BuildContext context = BUILD.get();
        if (context == null || access == null) return false;
        CellEntry cell = CELLS.get(key(x, y, z));
        if (cell == null || context.world != cell.owner.world) return false;
        MachineVisualClient.Entry entry = cell.owner;
        boolean hide = cell.block == block && access.getBlockMetadata(x, y, z) == cell.metadata
            && !entry.retired
            && (entry.mask & cell.groupBit) != 0;
        synchronized (BUILDS) {
            BuildReceipt receipt = BUILDS.computeIfAbsent(context.task, ignored -> new BuildReceipt());
            receipt.world = context.world;
            receipt.cells
                .put(cell, new CellDecision(hide, entry.maskEpochs[Integer.numberOfTrailingZeros(cell.groupBit)]));
        }
        return hide;
    }

    /** Fixed neighbour faces are dependencies, never acknowledgements for source-cell suppression. */
    public static boolean neighbourHidden(IBlockAccess access, int x, int y, int z, int side) {
        if (!mayOwn(x, y, z) || PROXY.get()) return false;
        BuildContext context = BUILD.get();
        if (context == null) return false;
        CellEntry cell = CELLS.get(key(x, y, z));
        if (cell == null || context.world != cell.owner.world) return false;
        boolean hide = !cell.owner.retired && (cell.owner.mask & cell.groupBit) != 0
            && access.getBlock(x, y, z) == cell.block
            && access.getBlockMetadata(x, y, z) == cell.metadata;
        net.minecraftforge.common.util.ForgeDirection normal = net.minecraftforge.common.util.ForgeDirection
            .getOrientation(side);
        Dependency dependency = new Dependency(
            cell.owner,
            cell.groupBit,
            section(x - normal.offsetX, y - normal.offsetY, z - normal.offsetZ));
        long epoch = cell.owner.maskEpochs[Integer.numberOfTrailingZeros(cell.groupBit)];
        long observation = hide ? epoch : -epoch - 1;
        synchronized (BUILDS) {
            Map<Dependency, Long> observations = BUILDS
                .computeIfAbsent(context.task, ignored -> new BuildReceipt()).neighbours;
            Long previous = observations.get(dependency);
            observations.put(dependency, previous == null || previous == observation ? observation : Long.MIN_VALUE);
        }
        return hide;
    }

    static void publish(MachineVisualClient.Entry entry, int mask) {
        int old = entry.mask;
        if (old == mask) return;
        entry.mask = mask;
        entry.restoringMask |= old & ~mask;
        MachineVisualDefinition definition = entry.definition;
        for (int i = 0; i < definition.groups.size(); i++) {
            int bit = 1 << i;
            if (((mask ^ old) & bit) == 0) continue;
            entry.maskEpochs[i]++;
            entry.neighbourUploads.remove(bit);
            if ((mask & bit) != 0) planNeighbourSections(entry, i);
            for (MachineVisualDefinition.Cell member : definition.groups.get(i).members) {
                int[] p = entry.at(member.x, member.y, member.z);
                String key = key(p[0], p[1], p[2]);
                CellEntry oldCell = CELLS.get(key);
                if ((mask & bit) != 0 && (oldCell == null || oldCell.owner != entry)) {
                    CellEntry next = new CellEntry(
                        entry,
                        bit,
                        p[0],
                        p[1],
                        p[2],
                        entry.world.getBlock(p[0], p[1], p[2]),
                        entry.world.getBlockMetadata(p[0], p[1], p[2]));
                    if (oldCell != null && oldCell.owner.world == entry.world) {
                        next.uploaded = oldCell.uploaded;
                        next.uploadedHidden = oldCell.uploadedHidden;
                    }
                    minX = Math.min(minX, p[0]);
                    minY = Math.min(minY, p[1]);
                    minZ = Math.min(minZ, p[2]);
                    maxX = Math.max(maxX, p[0]);
                    maxY = Math.max(maxY, p[1]);
                    maxZ = Math.max(maxZ, p[2]);
                    CELLS.put(key, next);
                }
                entry.world.markBlockRangeForRenderUpdate(p[0] - 1, p[1] - 1, p[2] - 1, p[0] + 1, p[1] + 1, p[2] + 1);
            }
            if ((old & bit) != 0 && (mask & bit) == 0) enqueueRestoration(entry, i);
        }
        releaseRestoredGroups();
    }

    private static void enqueueRestoration(MachineVisualClient.Entry entry, int group) {
        int bit = 1 << group;
        for (MachineVisualDefinition.Cell member : entry.definition.groups.get(group).members) {
            int[] p = entry.at(member.x, member.y, member.z);
            CellEntry cell = CELLS.get(key(p[0], p[1], p[2]));
            if (cell == null || cell.owner != entry) continue;
            SectionState known = cell.renderSection == null ? null : SECTIONS.get(cell.renderSection);
            if (known != null && known.world == entry.world && known.retired) {
                acknowledgeAbsence(cell);
                continue;
            }
            Dependency key = new Dependency(entry, bit, section(p[0], p[1], p[2]));
            RestorationRequest prior = RESTORATIONS.get(key);
            if (prior != null && prior.epoch == entry.maskEpochs[group]) continue;
            RESTORATIONS.put(
                key,
                new RestorationRequest(
                    key,
                    entry.maskEpochs[group],
                    p[0] >> 4,
                    p[1] >> 4,
                    p[2] >> 4,
                    cell.renderSection,
                    known == null ? null : known.manager()));
            restorationRequestsEnqueued++;
        }
    }

    /** Counts both active and restoring reservations, including controllers no longer in the client host map. */
    static int[] heldCosts() {
        Map<MachineVisualClient.Entry, Integer> owners = heldOwners();
        int members = 0, groups = 0;
        for (Map.Entry<MachineVisualClient.Entry, Integer> item : owners.entrySet()) {
            members += MachineVisualClient.memberCost(item.getKey(), item.getValue());
            groups += Integer.bitCount(item.getValue());
        }
        return new int[] { members, groups };
    }

    private static Map<MachineVisualClient.Entry, Integer> heldOwners() {
        Map<MachineVisualClient.Entry, Integer> owners = new IdentityHashMap<>();
        for (CellEntry cell : CELLS.values()) {
            int mask = cell.owner.mask | cell.owner.restoringMask;
            if (mask != 0) owners.put(cell.owner, mask);
        }
        return owners;
    }

    private static void releaseRestoredGroups() {
        for (MachineVisualClient.Entry entry : heldOwners().keySet()) {
            int restored = entry.restoringMask;
            for (int i = 0; i < entry.definition.groups.size(); i++) {
                int bit = 1 << i;
                if ((restored & bit) == 0) continue;
                for (MachineVisualDefinition.Cell member : entry.definition.groups.get(i).members) {
                    int[] p = entry.at(member.x, member.y, member.z);
                    CellEntry cell = CELLS.get(key(p[0], p[1], p[2]));
                    if (cell == null || cell.owner != entry
                        || !cell.uploaded
                        || !MachineVisualClient
                            .restorationAcknowledged(entry.maskEpochs[i], cell.uploadedEpoch, cell.uploadedHidden)) {
                        restored &= ~bit;
                        break;
                    }
                }
            }
            entry.restoringMask &= ~restored;
        }
        pruneRestorations();
    }

    static int ready(MachineVisualClient.Entry entry) {
        int ready = entry.mask;
        for (int i = 0; i < entry.definition.groups.size(); i++) {
            int bit = 1 << i;
            if ((ready & bit) == 0) continue;
            for (MachineVisualDefinition.Cell member : entry.definition.groups.get(i).members) {
                int[] p = entry.at(member.x, member.y, member.z);
                if (!uploadedHidden(entry, p[0], p[1], p[2])) {
                    ready &= ~bit;
                    break;
                }
            }
            Map<Long, Long> uploaded = entry.neighbourUploads.get(bit);
            for (long neighbour : entry.neighbourSections.getOrDefault(bit, Collections.emptySet())) {
                if (uploaded == null || !Long.valueOf(entry.maskEpochs[i])
                    .equals(uploaded.get(neighbour))) {
                    ready &= ~bit;
                    break;
                }
            }
        }
        return ready;
    }

    static boolean uploadedHidden(MachineVisualClient.Entry entry, int x, int y, int z) {
        CellEntry cell = CELLS.get(key(x, y, z));
        return cell != null && cell.owner == entry && cell.uploaded && cell.uploadedHidden;
    }

    static void remove(MachineVisualClient.Entry entry) {
        entry.retired = true;
        publish(entry, 0);
        // Keep ownership and rest proxies until the replacement static chunk output is committed.
        prune();
    }

    static void clear() {
        CELLS.clear();
        BUILDS.clear();
        OUTPUTS.clear();
        SECTIONS.clear();
        RESTORATIONS.clear();
        restorationPumpTick = Long.MIN_VALUE;
        restorationActions = 0;
        BUILD.remove();
        minX = minY = minZ = Integer.MAX_VALUE;
        maxX = maxY = maxZ = Integer.MIN_VALUE;
    }

    static void beginProxy() {
        PROXY.set(true);
    }

    static void endProxy() {
        PROXY.remove();
    }

    static void prune() {
        releaseRestoredGroups();
        World world = Minecraft.getMinecraft().theWorld;
        CELLS.entrySet()
            .removeIf(item -> {
                CellEntry cell = item.getValue();
                return cell.owner.world != world
                    || ((cell.owner.mask | cell.owner.restoringMask) & cell.groupBit) == 0 && cell.uploaded
                        && !cell.uploadedHidden;
            });
    }

    /** Retired controllers have no TESR callback; fill only their still-hidden original world cells. */
    @SubscribeEvent
    public void restoreRetired(RenderWorldLastEvent event) {
        if (CELLS.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.renderViewEntity == null) return;
        prune();
        java.util.List<CellEntry> targets = null;
        for (CellEntry cell : CELLS.values()) {
            if ((!cell.owner.retired && cell.owner.lastRenderFrame == MachineVisualClient.renderFrame())
                || !cell.uploadedHidden
                || cell.owner.world != mc.theWorld) continue;
            if (mc.theWorld.getBlock(cell.x, cell.y, cell.z) != cell.block
                || mc.theWorld.getBlockMetadata(cell.x, cell.y, cell.z) != cell.metadata) continue;
            if (targets == null) targets = new java.util.ArrayList<>();
            targets.add(cell);
        }
        if (targets == null) return;
        double cameraX = mc.renderViewEntity.lastTickPosX
            + (mc.renderViewEntity.posX - mc.renderViewEntity.lastTickPosX) * event.partialTicks;
        double cameraY = mc.renderViewEntity.lastTickPosY
            + (mc.renderViewEntity.posY - mc.renderViewEntity.lastTickPosY) * event.partialTicks;
        double cameraZ = mc.renderViewEntity.lastTickPosZ
            + (mc.renderViewEntity.posZ - mc.renderViewEntity.lastTickPosZ) * event.partialTicks;
        mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        beginProxy();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1, 1, 1, 1);
            RenderBlocks renderer = new RenderBlocks(mc.theWorld);
            renderer.renderAllFaces = true;
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.setTranslation(-cameraX, -cameraY, -cameraZ);
            try {
                for (CellEntry cell : targets) {
                    renderer.renderBlockByRenderType(cell.block, cell.x, cell.y, cell.z);
                    retiredProxyCubes++;
                }
                MachineVisualClient.recordSubmission(tess.draw(), true);
            } finally {
                tess.setTranslation(0, 0, 0);
            }
        } finally {
            endProxy();
            GL11.glPopAttrib();
        }
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static long section(int x, int y, int z) {
        return ((long) (x >> 4) & 0x3ffffffL) << 38 | ((long) (z >> 4) & 0x3ffffffL) << 12 | (y >> 4) & 0xfffL;
    }

    private static void planNeighbourSections(MachineVisualClient.Entry entry, int groupIndex) {
        java.util.Set<String> moving = new java.util.HashSet<>();
        for (MachineVisualDefinition.Group group : entry.definition.groups)
            for (MachineVisualDefinition.Cell member : group.members) {
                int[] p = entry.at(member.x, member.y, member.z);
                moving.add(key(p[0], p[1], p[2]));
            }
        java.util.Set<Long> required = new java.util.HashSet<>();
        for (MachineVisualDefinition.Cell member : entry.definition.groups.get(groupIndex).members) {
            int[] p = entry.at(member.x, member.y, member.z);
            for (net.minecraftforge.common.util.ForgeDirection direction : net.minecraftforge.common.util.ForgeDirection.VALID_DIRECTIONS) {
                int x = p[0] + direction.offsetX, y = p[1] + direction.offsetY, z = p[2] + direction.offsetZ;
                if (moving.contains(key(x, y, z)) || !entry.world.blockExists(x, y, z)
                    || entry.world.isAirBlock(x, y, z)) continue;
                Block neighbour = entry.world.getBlock(x, y, z);
                if (!neighbour.shouldSideBeRendered(
                    entry.world,
                    p[0],
                    p[1],
                    p[2],
                    direction.getOpposite()
                        .ordinal()))
                    required.add(section(x, y, z));
            }
        }
        entry.neighbourSections.put(1 << groupIndex, required);
    }

    private static final class BuildReceipt {

        World world;
        final Map<CellEntry, CellDecision> cells = new IdentityHashMap<>();
        final Map<Dependency, Long> neighbours = new java.util.HashMap<>();
    }

    private static final class CellDecision {

        final boolean hidden;
        final long epoch;

        CellDecision(boolean hidden, long epoch) {
            this.hidden = hidden;
            this.epoch = epoch;
        }
    }

    private static final class Dependency {

        final MachineVisualClient.Entry owner;
        final int bit;
        final long section;

        Dependency(MachineVisualClient.Entry owner, int bit, long section) {
            this.owner = owner;
            this.bit = bit;
            this.section = section;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(owner) * 31 + bit * 17 + Long.hashCode(section);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Dependency d && owner == d.owner && bit == d.bit && section == d.section;
        }
    }

    private static final class CellEntry {

        final MachineVisualClient.Entry owner;
        final String key;
        final int groupBit, x, y, z, metadata;
        final Block block;
        boolean uploaded, uploadedHidden;
        boolean rendererAbsent;
        Object renderSection;
        long uploadedEpoch = Long.MIN_VALUE;

        CellEntry(MachineVisualClient.Entry owner, int groupBit, int x, int y, int z, Block block, int metadata) {
            this.owner = owner;
            this.groupBit = groupBit;
            this.x = x;
            this.y = y;
            this.z = z;
            this.key = key(x, y, z);
            this.block = block;
            this.metadata = metadata;
        }
    }

    private static final class SectionState {

        final World world;
        final int x, y, z;
        java.lang.ref.WeakReference<Object> manager;
        int managerIdentity;
        boolean retired;

        SectionState(World world, Object manager, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            bindManager(manager);
        }

        void bindManager(Object value) {
            manager = new java.lang.ref.WeakReference<>(value);
            managerIdentity = System.identityHashCode(value);
        }

        Object manager() {
            return manager.get();
        }
    }

    public static final class RestorationRequest {

        private final Dependency key;
        private final MachineVisualClient.Entry owner;
        private final World world;
        private final int bit;
        private final long epoch;
        public final int sectionX, sectionY, sectionZ;
        private Object expectedSection, expectedManager;
        private boolean queued;
        private String state = "PENDING_NATIVE_QUEUE";

        RestorationRequest(Dependency key, long epoch, int x, int y, int z, Object section, Object manager) {
            this.key = key;
            owner = key.owner;
            world = owner.world;
            bit = key.bit;
            this.epoch = epoch;
            sectionX = x;
            sectionY = y;
            sectionZ = z;
            expectedSection = section;
            expectedManager = manager;
        }

        public Object expectedSection() {
            return expectedSection;
        }

        public Object expectedManager() {
            return expectedManager;
        }
    }

    private static final class BuildContext {

        final Object task;
        final World world;

        BuildContext(Object task, World world) {
            this.task = task;
            this.world = world;
        }
    }
}
