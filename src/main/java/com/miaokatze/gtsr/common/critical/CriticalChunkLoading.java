package com.miaokatze.gtsr.common.critical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.google.common.collect.ImmutableSetMultimap;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** A formed loom owns one GTSR Forge ticket for its complete selected structure footprint. */
public final class CriticalChunkLoading {

    public enum Status {
        READY,
        WAITING_FOR_CHUNKS,
        BACKOFF,
        BUDGET_EXCEEDED,
        REQUEST_FAILED,
        DISABLED,
        INVALID_HOST
    }

    private static final String MOD_ID = "gtsr";
    private static final int RETRY_TICKS = 40;
    private static final int MONITOR_TICKS = 20;
    private static final Set<CriticalChunkLoading> LOADERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean registered;

    private TileEntity owner;
    private Ticket nativeTicket;
    private final Set<ChunkCoordIntPair> current = new LinkedHashSet<>();
    private final Set<ChunkCoordIntPair> needed = new LinkedHashSet<>();
    private ChunkCoordIntPair activeChunk;
    private ChunkCoordIntPair pendingLoad;
    private long lastLoadTick = Long.MIN_VALUE;
    private long lastReconcileTick = Long.MIN_VALUE;
    private long retryAt;
    private boolean enabled = true;
    private Status status = Status.INVALID_HOST;

    public int budget() {
        int depth = ForgeChunkManager.getMaxChunkDepthFor(MOD_ID);
        // A zero administrator quota disables this loader; do not exploit Forge's maxDepth=0
        // eviction implementation to turn a restrictive configuration into unlimited coverage.
        return Math.max(0, depth);
    }

    /** Compatibility entry point; callers now pass the complete footprint, never a sliding window. */
    public boolean ensure(World world, TileEntity host, List<ChunkCoordIntPair> desired) {
        return ensureAllStructureChunks(world, host, desired);
    }

    /**
     * Establish durable coverage before reading or writing the structure. Player-loaded chunks do not
     * count as durable coverage. Only another live Forge forced ticket can replace this loom's claim.
     * Forge/GT quotas and the server's chunkloader switches remain authoritative.
     */
    public boolean ensureAllStructureChunks(World world, TileEntity host, List<ChunkCoordIntPair> desired) {
        if (!validHost(world, host) || desired == null || desired.contains(null)) {
            release();
            return false;
        }
        if (owner != host) {
            release();
            owner = host;
        }
        registerMonitor();
        LOADERS.add(this);
        activeChunk = new ChunkCoordIntPair(host.xCoord >> 4, host.zCoord >> 4);
        Set<ChunkCoordIntPair> next = new LinkedHashSet<>();
        next.add(activeChunk);
        next.addAll(desired);
        if (!needed.equals(next)) {
            needed.clear();
            needed.addAll(next);
            retryAt = 0;
            lastReconcileTick = Long.MIN_VALUE;
        }
        return maintain(world);
    }

    private boolean maintain(World world) {
        if (!enabled || !GTValues.enableChunkloaders) {
            releaseTicket();
            status = Status.DISABLED;
            return false;
        }
        long now = world.getTotalWorldTime();
        if (now < retryAt) return false;

        ImmutableSetMultimap<ChunkCoordIntPair, Ticket> forcedSnapshot = ForgeChunkManager
            .getPersistentChunksFor(world);
        // Locate native restart-restored tickets even when their controller chunk is externally owned.
        Ticket ticket = CriticalChunkTicketManager.getTicket(owner);
        if (ticket == null) ticket = findTicket(forcedSnapshot, owner);
        nativeTicket = ticket;
        Set<ChunkCoordIntPair> selfNeeded = new LinkedHashSet<>();
        for (ChunkCoordIntPair chunk : needed) {
            if (!externallyForced(forcedSnapshot, chunk, owner)) selfNeeded.add(chunk);
        }
        int ticketDepth = ticket == null ? 0 : ticket.getChunkListDepth();
        int limit = ticketDepth > 0 ? Math.min(budget(), ticketDepth) : budget();
        if (limit <= 0 || selfNeeded.size() > limit) return fail(world, Status.BUDGET_EXCEEDED);
        if (ticket == null) {
            ticket = CriticalChunkTicketManager.ensureTicket(owner);
            nativeTicket = ticket;
            if (ticket == null) return fail(world, Status.REQUEST_FAILED);
        }

        boolean covered = ticket == null ? selfNeeded.isEmpty()
            : ticket.getChunkList()
                .containsAll(selfNeeded);
        boolean changed = ticket != null && !new LinkedHashSet<>(ticket.getChunkList()).equals(selfNeeded);
        if (!covered || changed || lastReconcileTick == Long.MIN_VALUE || now - lastReconcileTick >= MONITOR_TICKS) {
            if (ticket != null) {
                // Never release any other owner's ticket, even where it overlaps this footprint.
                for (ChunkCoordIntPair chunk : new ArrayList<>(ticket.getChunkList())) {
                    if (!selfNeeded.contains(chunk)) CriticalChunkTicketManager.releaseChunk(owner, chunk);
                }
            }
            for (ChunkCoordIntPair chunk : selfNeeded) {
                if ((ticket == null || !ticket.getChunkList()
                    .contains(chunk)) && !CriticalChunkTicketManager.requestChunkLoad(owner, chunk))
                    return fail(world, Status.REQUEST_FAILED);
                // Retain/adopt the actual Forge ticket, including native restart-restored tickets.
                ticket = CriticalChunkTicketManager.getTicket(owner);
                nativeTicket = ticket;
                if (ticket == null) return fail(world, Status.REQUEST_FAILED);
                for (ChunkCoordIntPair forced : new ArrayList<>(ticket.getChunkList())) {
                    if (!selfNeeded.contains(forced)) CriticalChunkTicketManager.releaseChunk(owner, forced);
                }
                if (!ticket.getChunkList()
                    .contains(chunk) && !CriticalChunkTicketManager.requestChunkLoad(owner, chunk))
                    return fail(world, Status.REQUEST_FAILED);
            }
            if (ticket != null) {
                for (ChunkCoordIntPair chunk : new ArrayList<>(ticket.getChunkList())) {
                    if (!selfNeeded.contains(chunk)) CriticalChunkTicketManager.releaseChunk(owner, chunk);
                }
            }
            // Validate actual coverage even if Forge has evicted a chunk at its depth limit.
            if (!selfNeeded.isEmpty() && (ticket == null || !ticket.getChunkList()
                .containsAll(selfNeeded))) return fail(world, Status.REQUEST_FAILED);
            // Keep the single empty owner ticket when all chunks are externally covered. It stores
            // the restart callback's ownerXYZ without duplicating a single foreign forced chunk.
            current.clear();
            if (ticket != null) current.addAll(ticket.getChunkList());
            lastReconcileTick = now;
        }
        for (ChunkCoordIntPair chunk : needed) {
            if (!world.getChunkProvider()
                .chunkExists(chunk.chunkXPos, chunk.chunkZPos)) {
                // Only load verified, authorized footprint chunks; at most one native IO request/tick.
                if (pendingLoad == null && lastLoadTick != now && world instanceof WorldServer server) {
                    ChunkCoordIntPair requested = chunk;
                    pendingLoad = requested;
                    lastLoadTick = now;
                    server.theChunkProviderServer.loadChunk(
                        requested.chunkXPos,
                        requested.chunkZPos,
                        () -> { if (pendingLoad == requested) pendingLoad = null; });
                }
                status = Status.WAITING_FOR_CHUNKS;
                return false;
            }
        }
        status = Status.READY;
        return true;
    }

    private static boolean validHost(World world, TileEntity host) {
        return world != null && !world.isRemote
            && world.provider != null
            && host instanceof BaseMetaTileEntity
            && !host.isInvalid()
            && host.getWorldObj() == world
            && world.getChunkProvider()
                .chunkExists(host.xCoord >> 4, host.zCoord >> 4)
            && world.getTileEntity(host.xCoord, host.yCoord, host.zCoord) == host;
    }

    private static boolean ownedBy(Ticket ticket, TileEntity host) {
        NBTTagCompound data = ticket.getModData();
        return MOD_ID.equals(ticket.getModId()) && !ticket.isPlayerTicket()
            && data.hasKey("OwnerX")
            && data.hasKey("OwnerY")
            && data.hasKey("OwnerZ")
            && data.getInteger("OwnerX") == host.xCoord
            && data.getInteger("OwnerY") == host.yCoord
            && data.getInteger("OwnerZ") == host.zCoord;
    }

    private static Ticket findTicket(World world, TileEntity host) {
        return findTicket(ForgeChunkManager.getPersistentChunksFor(world), host);
    }

    private static Ticket findTicket(ImmutableSetMultimap<ChunkCoordIntPair, Ticket> forced, TileEntity host) {
        for (Ticket ticket : forced.values()) {
            if (ownedBy(ticket, host)) return ticket;
        }
        return null;
    }

    private static boolean externallyForced(ImmutableSetMultimap<ChunkCoordIntPair, Ticket> forced,
        ChunkCoordIntPair chunk, TileEntity host) {
        for (Ticket ticket : forced.get(chunk)) {
            if (!ownedBy(ticket, host)) return true;
        }
        return false;
    }

    private boolean fail(World world, Status failure) {
        releaseTicket();
        retryAt = world.getTotalWorldTime() + RETRY_TICKS;
        status = failure;
        return false;
    }

    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        retryAt = 0;
        lastReconcileTick = Long.MIN_VALUE;
        if (!enabled) {
            releaseTicket();
            status = Status.DISABLED;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Broken loom, dismantling, invalidation and removal stop both supervision and this owner's ticket. */
    public void release() {
        releaseTicket();
        LOADERS.remove(this);
        owner = null;
        needed.clear();
        activeChunk = null;
        retryAt = 0;
        lastReconcileTick = Long.MIN_VALUE;
        status = Status.INVALID_HOST;
    }

    /** Also adopts and removes a native restart-restored ticket before the first ensure call. */
    public void release(TileEntity host) {
        if (host instanceof BaseMetaTileEntity && host != owner && host.getWorldObj() != null) {
            Ticket restored = findTicket(host.getWorldObj(), host);
            releaseNativeTicket(host, restored);
        }
        release();
    }

    private void releaseTicket() {
        if (owner != null) {
            Ticket existing = nativeTicket != null ? nativeTicket
                : owner.getWorldObj() == null ? null : findTicket(owner.getWorldObj(), owner);
            releaseNativeTicket(owner, existing);
        }
        nativeTicket = null;
        current.clear();
        pendingLoad = null;
    }

    private static void releaseNativeTicket(TileEntity host, Ticket existing) {
        CriticalChunkTicketManager.releaseTicket(host);
        if (existing == null) return;
        ForgeChunkManager.releaseTicket(existing);
    }

    /** GT's native reload callback must always restore the loom controller, not an arbitrary work chunk. */
    public ChunkCoordIntPair getActiveChunk() {
        return activeChunk;
    }

    public Set<ChunkCoordIntPair> currentSet() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(current));
    }

    public Status getStatus() {
        return status;
    }

    public boolean ready() {
        return status == Status.READY;
    }

    private static void registerMonitor() {
        if (registered) return;
        Supervisor supervisor = new Supervisor();
        FMLCommonHandler.instance()
            .bus()
            .register(supervisor);
        MinecraftForge.EVENT_BUS.register(supervisor);
        registered = true;
    }

    /** Continues supervising externally covered controllers even without a tile's own tick callback. */
    public static final class Supervisor {

        @SubscribeEvent
        public void tick(TickEvent.WorldTickEvent event) {
            if (event.phase != TickEvent.Phase.START || event.world.isRemote) return;
            for (CriticalChunkLoading loader : new ArrayList<>(LOADERS)) {
                if (loader.owner == null || loader.owner.getWorldObj() != event.world) continue;
                if (!validHost(event.world, loader.owner)) loader.release();
                // Controller handoff is checked before world ticking can unload it. Remaining
                // coverage is reconciled at low frequency, or immediately by the tile's gate.
                else if (event.world.getTotalWorldTime() % MONITOR_TICKS == 0
                    || ForgeChunkManager.getPersistentChunksFor(event.world)
                        .get(loader.activeChunk)
                        .isEmpty())
                    loader.maintain(event.world);
            }
        }

        @SubscribeEvent
        public void unload(WorldEvent.Unload event) {
            for (CriticalChunkLoading loader : new ArrayList<>(LOADERS)) {
                if (loader.owner != null && loader.owner.getWorldObj() == event.world) loader.release();
            }
            CriticalChunkTicketManager.releaseWorld(event.world);
        }
    }
}
