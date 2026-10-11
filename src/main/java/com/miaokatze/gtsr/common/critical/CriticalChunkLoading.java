package com.miaokatze.gtsr.common.critical;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.objects.GTChunkManager;

/** One host-owned GT ticket, with a bounded sliding window for construction and scanning. */
public final class CriticalChunkLoading {

    public enum Status {
        READY,
        WAITING_FOR_CHUNKS,
        BACKOFF,
        BUDGET_EXCEEDED,
        REQUEST_FAILED,
        INVALID_HOST
    }

    private static final String MOD_ID = "gregtech";
    private static final int RETRY_TICKS = 40;

    private TileEntity owner;
    private final Set<ChunkCoordIntPair> current = new LinkedHashSet<>();
    private ChunkCoordIntPair activeChunk;
    private ChunkCoordIntPair pendingLoad;
    private long lastLoadTick = Long.MIN_VALUE;
    private long retryAt;
    private Status status = Status.INVALID_HOST;

    public int budget() {
        return Math.max(0, ForgeChunkManager.getMaxChunkDepthFor(MOD_ID));
    }

    /**
     * Call before spending resources or advancing progress. The caller must split work into windows
     * within {@link #budget()}; this helper always includes the controller and never enlarges quotas.
     * Missing chunks use Forge's native chunk loader after ticket verification: at most one pending
     * request and one new request per host per tick. Existing disk chunks use native asynchronous IO.
     */
    public boolean ensure(World world, TileEntity host, List<ChunkCoordIntPair> desired) {
        if (!validHost(world, host) || desired == null || desired.contains(null)) {
            release();
            status = Status.INVALID_HOST;
            return false;
        }
        if (owner != host) {
            release();
            owner = host;
        }
        long now = world.getTotalWorldTime();
        if (now < retryAt) {
            status = Status.BACKOFF;
            return false;
        }

        ChunkCoordIntPair controller = new ChunkCoordIntPair(host.xCoord >> 4, host.zCoord >> 4);
        Set<ChunkCoordIntPair> next = new LinkedHashSet<>();
        next.add(controller);
        next.addAll(desired);
        if (next.size() > budget()) return fail(world, Status.BUDGET_EXCEEDED);

        // Adopt the native restart callback's ticket too: its forced chunks need not be in current.
        Ticket ticket = findTicket(world, host, controller);
        if (ticket != null && ticket.getChunkListDepth() > 0 && next.size() > ticket.getChunkListDepth())
            return fail(world, Status.BUDGET_EXCEEDED);
        Set<ChunkCoordIntPair> previous = new LinkedHashSet<>(current);
        if (ticket != null) previous.addAll(ticket.getChunkList());
        for (ChunkCoordIntPair chunk : previous) {
            if (!next.contains(chunk)) GTChunkManager.releaseChunk(host, chunk);
        }
        for (ChunkCoordIntPair chunk : next) {
            if ((ticket == null || !ticket.getChunkList()
                .contains(chunk)) && !GTChunkManager.requestChunkLoad(host, chunk))
                return fail(world, Status.REQUEST_FAILED);
        }

        // GT's wrapper returns true even if Forge silently evicts chunks at the native depth limit.
        // Verify the real single ticket instead of accepting that boolean as proof of coverage.
        ticket = findTicket(world, host, controller);
        if (ticket == null || ticket.getChunkListDepth() > 0 && next.size() > ticket.getChunkListDepth()
            || !ticket.getChunkList()
                .containsAll(next))
            return fail(world, Status.REQUEST_FAILED);
        current.clear();
        current.addAll(next);
        activeChunk = controller;
        for (ChunkCoordIntPair chunk : next) {
            if (!chunk.equals(controller)) activeChunk = chunk;
        }
        for (ChunkCoordIntPair chunk : next) {
            if (!world.getChunkProvider()
                .chunkExists(chunk.chunkXPos, chunk.chunkZPos)) {
                // RC-2 may suppress implicit generation during tile ticks. A forced ticket alone
                // does not establish a loaded chunk; explicitly request only its verified window.
                if (pendingLoad == null && lastLoadTick != now && world instanceof WorldServer server) {
                    ChunkCoordIntPair requested = new ChunkCoordIntPair(chunk.chunkXPos, chunk.chunkZPos);
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

    private static Ticket findTicket(World world, TileEntity host, ChunkCoordIntPair controller) {
        for (Ticket ticket : ForgeChunkManager.getPersistentChunksFor(world)
            .get(controller)) {
            NBTTagCompound data = ticket.getModData();
            if (MOD_ID.equals(ticket.getModId()) && !ticket.isPlayerTicket()
                && data.hasKey("OwnerX")
                && data.hasKey("OwnerY")
                && data.hasKey("OwnerZ")
                && data.getInteger("OwnerX") == host.xCoord
                && data.getInteger("OwnerY") == host.yCoord
                && data.getInteger("OwnerZ") == host.zCoord) return ticket;
        }
        return null;
    }

    private boolean fail(World world, Status failure) {
        releaseTicket();
        retryAt = world.getTotalWorldTime() + RETRY_TICKS;
        status = failure;
        return false;
    }

    /** Stop, completion, broken loom and removal must release; ordinary chunk unload follows GT. */
    public void release() {
        releaseTicket();
        owner = null;
        retryAt = 0;
        status = Status.INVALID_HOST;
    }

    /** Also removes a restart-restored native ticket before this instance has called ensure. */
    public void release(TileEntity host) {
        if (host instanceof BaseMetaTileEntity && host != owner) GTChunkManager.releaseTicket(host);
        release();
    }

    private void releaseTicket() {
        if (owner != null) GTChunkManager.releaseTicket(owner);
        current.clear();
        activeChunk = null;
        pendingLoad = null;
    }

    /** Expose through the host's IChunkLoader; GT's reload setting controls restart behavior. */
    public ChunkCoordIntPair getActiveChunk() {
        return activeChunk;
    }

    public Set<ChunkCoordIntPair> currentSet() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(current));
    }

    public Status getStatus() {
        return status;
    }
}
