package com.miaokatze.gtsr.client.machinevisual;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.machinevisual.MachineVisualHost;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualNetwork;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualSnapshot;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Host-local handshake, bounded inbound queue and a monotonic freshness lease. */
public final class MachineVisualClient {

    public enum Quality {
        OFF,
        LOW,
        STANDARD,
        HIGH
    }

    private static Quality quality = Quality.STANDARD;
    private static final int HOST_LIMIT = 512, CANDIDATE_LIMIT = 512, HOST_ADMISSIONS_PER_TICK = 4;
    private static final long HOST_LEASE_TICKS = 120, RECENT_VISIBLE_TICKS = 40;
    private static final Map<MachineVisualHost, Entry> ENTRIES = new IdentityHashMap<>();
    private static final Map<MachineVisualHost, Observation> CANDIDATES = new IdentityHashMap<>();
    private static final Map<MachineVisualHost, Entry> RETIRING = new IdentityHashMap<>();
    private static final ConcurrentLinkedQueue<MachineVisualSnapshot> INBOUND = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED = new AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicLong NONCES = new java.util.concurrent.atomic.AtomicLong(
        new java.security.SecureRandom().nextLong());
    private static World world;
    private static long tick, pausedAt, frame, observationSequence;
    public static long framesReceived, framesRejected, subscriptionRequests, renderCalls, renderedCubes;
    /** Completed Tessellator submissions and their returned byte counts; these are not GPU timer measurements. */
    public static long motionSubmissions, motionSubmittedBytes, restorationSubmissions, restorationSubmittedBytes;
    public static int heldMotionMembers, heldMotionGroups, motionMemberLimit, motionGroupLimit;
    public static long budgetDeferredHosts, budgetRotations;
    public static int registeredHosts, waitingHosts, retiringHosts;
    public static long hostAdmissions, hostRetirements, clearanceMemberChecks, clearanceAirChecks,
        clearanceWorldQueries;

    static void recordSubmission(int bytes, boolean restoration) {
        if (restoration) {
            restorationSubmissions++;
            restorationSubmittedBytes += bytes;
        } else {
            motionSubmissions++;
            motionSubmittedBytes += bytes;
        }
    }

    private MachineVisualClient() {}

    public static void register() {
        quality = Quality.valueOf(com.miaokatze.gtsr.config.Config.machineVisualQuality);
        FMLCommonHandler.instance()
            .bus()
            .register(new MachineVisualClient());
        MachineVisualVisibility.register();
    }

    public static void observe(MachineVisualHost host, long hostTick) {
        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        Minecraft mc = Minecraft.getMinecraft();
        if (tile.getWorld() != mc.theWorld) return;
        boolean visible = hostTick == 0;
        Entry entry = ENTRIES.get(host);
        if (entry != null) {
            if (visible) entry.lastVisibleTick = tick;
            return;
        }
        if (!near(mc, tile)) return;
        Observation observation = CANDIDATES.get(host);
        if (observation == null) {
            if (CANDIDATES.size() >= CANDIDATE_LIMIT) {
                if (!visible) return;
                Observation oldest = null;
                for (Observation candidate : CANDIDATES.values()) if (!recentlyVisible(candidate.lastVisibleTick)
                    && (oldest == null || candidate.order < oldest.order)) oldest = candidate;
                if (oldest == null) return;
                CANDIDATES.remove(oldest.host);
            }
            observation = new Observation(host, ++observationSequence);
            CANDIDATES.put(host, observation);
        }
        observation.lastSeenTick = tick;
        if (visible) observation.lastVisibleTick = tick;
    }

    public static void receive(MachineVisualSnapshot snapshot) {
        synchronized (INBOUND) {
            if (QUEUED.get() >= 256) return;
            INBOUND.add(snapshot);
            QUEUED.incrementAndGet();
        }
    }

    public static boolean isFresh(MachineVisualHost host) {
        Entry entry = ENTRIES.get(host);
        return quality != Quality.OFF && entry != null && entry.fresh(now());
    }

    public static boolean isWorking(MachineVisualHost host) {
        Entry entry = ENTRIES.get(host);
        // Last observed authority state is separate from extrapolating progress past the cycle endpoint.
        return isFresh(host) && entry.snapshot.working();
    }

    /** One-tick generators share the authority freshness lease; their decorative clock is never recipe progress. */
    public static boolean isAnimating(MachineVisualHost host) {
        Entry entry = ENTRIES.get(host);
        return isWorking(host) && (entry.snapshot.duration <= 1 || now() <= entry.advanceUntil);
    }

    public static void setQuality(Quality next) {
        quality = next;
    }

    public static Quality quality() {
        return quality;
    }

    static long clientTick() {
        return tick;
    }

    static long renderFrame() {
        return frame;
    }

    static Entry entry(MachineVisualHost host) {
        return ENTRIES.get(host);
    }

    private static long now() {
        return pausedAt != 0 ? pausedAt : System.nanoTime();
    }

    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) frame++;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != world) {
            for (Entry entry : ENTRIES.values()) MachineVisualVisibility.remove(entry);
            ENTRIES.clear();
            CANDIDATES.clear();
            RETIRING.clear();
            MachineVisualVisibility.clear();
            heldMotionMembers = heldMotionGroups = 0;
            registeredHosts = waitingHosts = retiringHosts = 0;
            synchronized (INBOUND) {
                INBOUND.clear();
                QUEUED.set(0);
            }
            world = mc.theWorld;
            pausedAt = 0;
        }
        if (world == null) return;
        long now = System.nanoTime();
        if (mc.isSingleplayer() && mc.isGamePaused()) {
            if (pausedAt == 0) pausedAt = now;
            return;
        }
        if (pausedAt != 0) {
            long pause = now - pausedAt;
            for (Entry entry : ENTRIES.values()) {
                entry.receivedAt += pause;
                entry.lastClock += pause;
                entry.advanceUntil += pause;
            }
            pausedAt = 0;
        }
        tick++;
        retireHosts(mc);
        admitHosts(mc);
        for (int i = 0; i < 64; i++) {
            MachineVisualSnapshot snapshot;
            synchronized (INBOUND) {
                snapshot = INBOUND.poll();
                if (snapshot == null) break;
                QUEUED.decrementAndGet();
            }
            accept(snapshot, now);
        }
        java.util.List<Entry> requests = new java.util.ArrayList<>();
        java.util.List<Entry> motion = new java.util.ArrayList<>();
        for (Entry entry : ENTRIES.values()) {
            if (entry.lastRequest == Long.MIN_VALUE || tick - entry.lastRequest >= 40) requests.add(entry);
            boolean fresh = quality != Quality.OFF && entry.fresh(now);
            entry.previousDecorativeMs = entry.decorativeMs;
            if (fresh && isAnimating(entry.host)) {
                entry.decorativeMs += Math.min(100, Math.max(0, (now - entry.lastClock) / 1000000L));
            } else entry.previousDecorativeMs = entry.decorativeMs = entry.sampledDecorativeMs = 0;
            entry.lastClock = now;
            entry.candidateMask = fresh && entry.snapshot.motionShape() && entry.snapshot.working()
                ? (1 << entry.definition.groups.size()) - 1 & ~entry.restoringMask
                : 0;
            // Already-published groups always see current obstacles, even while a native upload is pending.
            entry.eligibleMask = MachineVisualClearance.safeGroups(entry, entry.mask & entry.candidateMask);
            if (!entry.definition.groups.isEmpty()) motion.add(entry);
            if (fresh) com.miaokatze.gtsr.client.workeffects.WorkEffectParticles.tick(entry.host, tick);
        }
        admitMotion(motion);
        requests.sort(
            java.util.Comparator.comparingLong((Entry entry) -> entry.lastRequest)
                .thenComparingLong(entry -> entry.nonce));
        for (int i = 0; i < Math.min(4, requests.size()); i++) {
            Entry entry = requests.get(i);
            MachineVisualNetwork.request(entry.host, entry.nonce);
            entry.lastRequest = tick;
            subscriptionRequests++;
        }
        registeredHosts = ENTRIES.size();
        waitingHosts = CANDIDATES.size();
        retiringHosts = RETIRING.size();
    }

    private static boolean near(Minecraft mc, IGregTechTileEntity tile) {
        return mc.thePlayer != null
            && mc.thePlayer.getDistanceSq(tile.getXCoord() + .5, tile.getYCoord() + .5, tile.getZCoord() + .5) <= 4096;
    }

    private static boolean recentlyVisible(long observedTick) {
        return observedTick != Long.MIN_VALUE && tick - observedTick <= RECENT_VISIBLE_TICKS;
    }

    private static void retire(Entry entry) {
        MachineVisualVisibility.remove(entry);
        if ((entry.mask | entry.restoringMask) != 0) RETIRING.put(entry.host, entry);
        hostRetirements++;
    }

    private static void retireHosts(Minecraft mc) {
        Iterator<Entry> iterator = ENTRIES.values()
            .iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            IGregTechTileEntity tile = entry.tile;
            if (entry.retired || entry.definition != entry.host.visualDefinition()
                || entry.facing != entry.host.getExtendedFacing()
                || ((net.minecraft.tileentity.TileEntity) tile).isInvalid()
                || tile.getMetaTileEntity() != entry.host
                || entry.world != world
                || !near(mc, tile)
                || !world.blockExists(entry.originX, entry.originY, entry.originZ)
                || world.getTileEntity(entry.originX, entry.originY, entry.originZ) != tile) {
                retire(entry);
                iterator.remove();
            }
        }
        MachineVisualVisibility.prune();
        RETIRING.values()
            .removeIf(entry -> (entry.mask | entry.restoringMask) == 0);
    }

    private static void admitHosts(Minecraft mc) {
        CANDIDATES.values()
            .removeIf(
                candidate -> tick - candidate.lastSeenTick > RECENT_VISIBLE_TICKS
                    || candidate.host.getBaseMetaTileEntity()
                        .getWorld() != world
                    || !near(mc, candidate.host.getBaseMetaTileEntity()));
        java.util.List<Observation> waiting = new java.util.ArrayList<>(CANDIDATES.values());
        waiting.sort(
            java.util.Comparator.comparing((Observation item) -> !recentlyVisible(item.lastVisibleTick))
                .thenComparingLong(item -> item.order));
        int admitted = 0;
        for (Observation candidate : waiting) {
            if (admitted == HOST_ADMISSIONS_PER_TICK) break;
            IGregTechTileEntity tile = candidate.host.getBaseMetaTileEntity();
            boolean restoring = false;
            for (Entry owner : RETIRING.values())
                if (owner.originX == tile.getXCoord() && owner.originY == tile.getYCoord()
                    && owner.originZ == tile.getZCoord()) {
                        restoring = true;
                        break;
                    }
            if (restoring) continue;
            if (((net.minecraft.tileentity.TileEntity) tile).isInvalid() || tile.getMetaTileEntity() != candidate.host
                || !world.blockExists(tile.getXCoord(), tile.getYCoord(), tile.getZCoord())
                || world.getTileEntity(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()) != tile) {
                CANDIDATES.remove(candidate.host);
                continue;
            }
            if (ENTRIES.size() >= HOST_LIMIT) {
                Entry victim = null;
                for (Entry entry : ENTRIES.values()) {
                    boolean lowerPriority = recentlyVisible(candidate.lastVisibleTick)
                        && !recentlyVisible(entry.lastVisibleTick);
                    if (!lowerPriority && tick - entry.registeredAt < HOST_LEASE_TICKS) continue;
                    if (victim == null
                        || recentlyVisible(victim.lastVisibleTick) && !recentlyVisible(entry.lastVisibleTick)
                        || recentlyVisible(victim.lastVisibleTick) == recentlyVisible(entry.lastVisibleTick)
                            && entry.registeredAt < victim.registeredAt)
                        victim = entry;
                }
                if (victim == null) break;
                retire(victim);
                ENTRIES.remove(victim.host);
            }
            Entry entry = new Entry(candidate.host);
            entry.lastVisibleTick = candidate.lastVisibleTick;
            ENTRIES.put(candidate.host, entry);
            CANDIDATES.remove(candidate.host);
            admitted++;
            hostAdmissions++;
        }
    }

    /** Read-only shared animation clock for phase-aligned decorative emitters. */
    public static long decorativeTime(MachineVisualHost host) {
        Entry entry = ENTRIES.get(host);
        return entry == null ? 0 : entry.decorativeMs;
    }

    static long decorativeTime(Entry entry, float partialTick) {
        Minecraft mc = Minecraft.getMinecraft();
        if (pausedAt != 0 || mc.isSingleplayer() && mc.isGamePaused()) return entry.sampledDecorativeMs;
        double fraction = Math.max(0, Math.min(1, partialTick));
        entry.sampledDecorativeMs = entry.previousDecorativeMs
            + (long) ((entry.decorativeMs - entry.previousDecorativeMs) * fraction);
        return entry.sampledDecorativeMs;
    }

    private static void admitMotion(java.util.List<Entry> entries) {
        MachineVisualVisibility.prune();
        entries.sort(java.util.Comparator.comparingLong(entry -> entry.nonce));
        int count = entries.size();
        int[] members = new int[count], groups = new int[count], extraMembers = new int[count],
            extraGroups = new int[count];
        long[] granted = new long[count], leases = new long[count];
        boolean[] active = new boolean[count];
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            members[i] = memberCost(entry, entry.candidateMask);
            groups[i] = Integer.bitCount(entry.candidateMask);
            int extra = entry.candidateMask & ~(entry.mask | entry.restoringMask);
            extraMembers[i] = memberCost(entry, extra);
            extraGroups[i] = Integer.bitCount(extra);
            granted[i] = Math.max(entry.lastMotionGrant, entry.lastCandidateScan);
            leases[i] = entry.motionLeaseUntil;
            active[i] = entry.mask != 0;
        }
        int[] held = MachineVisualVisibility.heldCosts();
        motionMemberLimit = memberLimit(quality.ordinal());
        motionGroupLimit = groupLimit(quality.ordinal());
        boolean[] selected = selectAdmissions(
            motionMemberLimit,
            motionGroupLimit,
            held[0],
            held[1],
            members,
            groups,
            extraMembers,
            extraGroups,
            granted,
            leases,
            active,
            tick);
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            int next = 0;
            if (selected[i]) {
                int extra = entry.candidateMask & ~(entry.mask | entry.restoringMask);
                if (extra != 0) entry.lastCandidateScan = tick;
                // Admission bounds candidate member/group scans before any world query or sweep work.
                next = entry.eligibleMask | MachineVisualClearance.safeGroups(entry, extra);
            }
            if (next != 0 && entry.mask == 0) {
                entry.lastMotionGrant = tick;
                entry.motionLeaseUntil = tick + 120;
            }
            if (entry.candidateMask != 0 && !selected[i]) {
                budgetDeferredHosts++;
                if (entry.mask != 0) budgetRotations++;
            }
            MachineVisualVisibility.publish(entry, next);
        }
        held = MachineVisualVisibility.heldCosts();
        heldMotionMembers = held[0];
        heldMotionGroups = held[1];
    }

    static int memberCost(Entry entry, int mask) {
        int cost = 0;
        for (int i = 0; i < entry.definition.groups.size(); i++)
            if ((mask & 1 << i) != 0) cost += entry.definition.groups.get(i).members.size();
        return cost;
    }

    // Pure policy methods are exercised directly by tools/workvisual/probe_budget.py.
    static int memberLimit(int quality) {
        // STANDARD must fit one maximum-stack Mega turbine (2814 members) as a complete host.
        return quality == 0 ? 0 : quality == 1 ? 128 : 4096;
    }

    static int groupLimit(int quality) {
        return quality == 0 ? 0 : quality == 1 ? 16 : quality == 2 ? 64 : 128;
    }

    static boolean[] selectAdmissions(int memberLimit, int groupLimit, int heldMembers, int heldGroups, int[] members,
        int[] groups, int[] extraMembers, int[] extraGroups, long[] granted, long[] leases, boolean[] active,
        long tick) {
        int size = members.length;
        boolean[] selected = new boolean[size];
        int demandMembers = heldMembers, demandGroups = heldGroups;
        for (int i = 0; i < size; i++) {
            demandMembers += extraMembers[i];
            demandGroups += extraGroups[i];
        }
        boolean pressure = demandMembers > memberLimit || demandGroups > groupLimit;
        boolean debt = heldMembers > memberLimit || heldGroups > groupLimit;
        // Existing reservations are never subtracted here: retirement needs a real static-upload acknowledgement.
        for (int i = 0; i < size; i++) {
            if (!active[i] || members[i] == 0 || debt || pressure && tick >= leases[i]) continue;
            if (heldMembers + extraMembers[i] <= memberLimit && heldGroups + extraGroups[i] <= groupLimit) {
                selected[i] = true;
                heldMembers += extraMembers[i];
                heldGroups += extraGroups[i];
            }
        }
        Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> {
            int compared = Long.compare(granted[a], granted[b]);
            return compared != 0 ? compared : Integer.compare(a, b);
        });
        for (int i : order) {
            if (active[i] || members[i] == 0) continue;
            if (heldMembers + extraMembers[i] <= memberLimit && heldGroups + extraGroups[i] <= groupLimit) {
                selected[i] = true;
                heldMembers += extraMembers[i];
                heldGroups += extraGroups[i];
            }
        }
        return selected;
    }

    static boolean restorationAcknowledged(long expectedEpoch, long observedEpoch, boolean hidden) {
        return !hidden && expectedEpoch == observedEpoch;
    }

    private static void accept(MachineVisualSnapshot frame, long now) {
        framesReceived++;
        if (frame.dimension != world.provider.dimensionId || !world.blockExists(frame.x, frame.y, frame.z)) {
            framesRejected++;
            return;
        }
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(frame.x, frame.y, frame.z);
        if (!(tile instanceof IGregTechTileEntity gt) || !(gt.getMetaTileEntity() instanceof MachineVisualHost host)) {
            framesRejected++;
            return;
        }
        Entry entry = ENTRIES.get(host);
        if (entry == null || entry.nonce != frame.nonce
            || gt.getMetaTileID() != frame.registryId
            || entry.definition.revision != frame.revision
            || host.getExtendedFacing()
                .getIndex() != frame.orientation
            || entry.snapshot != null
                && (frame.session != entry.snapshot.session || frame.generation != entry.snapshot.generation
                    || frame.sequence <= entry.snapshot.sequence)) {
            framesRejected++;
            return;
        }
        entry.snapshot = frame;
        entry.receivedAt = now;
        entry.advanceUntil = now
            + Math.min(1000L, Math.max(1L, (long) frame.duration - frame.progress) * 50L) * 1000000L;
    }

    /** Diagnostic access used by native candidate probes. */
    public static MachineVisualSnapshot snapshot(MachineVisualHost host) {
        Entry entry = ENTRIES.get(host);
        return entry == null ? null : entry.snapshot;
    }

    public static int safeGroupMask(MachineVisualHost host) {
        Entry entry = ENTRIES.get(host);
        return entry == null ? 0 : entry.mask;
    }

    public static int uploadedGroupMask(MachineVisualHost host) {
        Entry entry = ENTRIES.get(host);
        return entry == null ? 0 : MachineVisualVisibility.ready(entry);
    }

    public static Map<String, Object> restorationDiagnostics() {
        return MachineVisualVisibility.diagnostics(ENTRIES.values());
    }

    static final class Entry {

        final MachineVisualHost host;
        final com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition definition;
        final IGregTechTileEntity tile;
        final World world;
        final com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing facing;
        final int originX, originY, originZ;
        final MachineVisualClearance.Plan geometry;
        final long registeredAt = tick;
        final long nonce = NONCES.incrementAndGet();
        MachineVisualSnapshot snapshot;
        long receivedAt, advanceUntil, decorativeMs, previousDecorativeMs, sampledDecorativeMs,
            lastClock = System.nanoTime(), lastRequest = Long.MIN_VALUE, changedAtTick,
            lastRenderFrame = Long.MIN_VALUE;
        volatile int mask;
        int eligibleMask, candidateMask;
        long lastMotionGrant = Long.MIN_VALUE, lastCandidateScan = Long.MIN_VALUE, motionLeaseUntil,
            lastVisibleTick = Long.MIN_VALUE;
        int restoringMask;
        boolean retired;
        final long[] maskEpochs = new long[8];
        final Map<Integer, java.util.Set<Long>> neighbourSections = new java.util.HashMap<>();
        final Map<Integer, Map<Long, Long>> neighbourUploads = new java.util.HashMap<>();

        Entry(MachineVisualHost host) {
            this.host = host;
            definition = host.visualDefinition();
            tile = host.getBaseMetaTileEntity();
            world = tile.getWorld();
            facing = host.getExtendedFacing();
            originX = tile.getXCoord();
            originY = tile.getYCoord();
            originZ = tile.getZCoord();
            geometry = MachineVisualClearance.plan(definition, facing);
        }

        int[] at(int x, int y, int z) {
            return new int[] { originX + x * geometry.x[0] + y * geometry.y[0] + z * geometry.z[0],
                originY + x * geometry.x[1] + y * geometry.y[1] + z * geometry.z[1],
                originZ + x * geometry.x[2] + y * geometry.y[2] + z * geometry.z[2] };
        }

        boolean fresh(long now) {
            return !retired && definition == host.visualDefinition()
                && snapshot != null
                && snapshot.formed()
                && now - receivedAt <= 2000000000L
                && snapshot.orientation == host.getExtendedFacing()
                    .getIndex();
        }
    }

    private static final class Observation {

        final MachineVisualHost host;
        final long order;
        long lastSeenTick = tick, lastVisibleTick = Long.MIN_VALUE;

        Observation(MachineVisualHost host, long order) {
            this.host = host;
            this.order = order;
        }
    }
}
