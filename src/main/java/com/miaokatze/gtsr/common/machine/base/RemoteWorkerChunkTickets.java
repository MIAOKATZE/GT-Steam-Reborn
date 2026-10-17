package com.miaokatze.gtsr.common.machine.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.objects.GTChunkManager;

/** Bounded native tickets for real worker owners; the hub anchor is shared by its bound workers. */
public final class RemoteWorkerChunkTickets implements ForgeChunkManager.OrderedLoadingCallback {

    private static final String NODE = "RemoteWorkerNode";
    private static final String HUB = "RemoteWorkerHub";
    private static final Map<TileEntity, List<Ticket>> NODES = new HashMap<>();
    private static final Map<TileEntity, List<Ticket>> HUBS = new HashMap<>();
    private static final Map<TileEntity, TileEntity> BINDINGS = new HashMap<>();
    private static final Set<World> UNLOADED = Collections.newSetFromMap(new WeakHashMap<>());
    private static Object mod;
    private final ForgeChunkManager.OrderedLoadingCallback fallback;

    private RemoteWorkerChunkTickets(ForgeChunkManager.OrderedLoadingCallback fallback) {
        this.fallback = fallback;
    }

    /** Shares GTSR's existing callback/quota without changing any administrator constraint. */
    public static void register(Object ownerMod, ForgeChunkManager.OrderedLoadingCallback fallback) {
        mod = ownerMod;
        RemoteWorkerChunkTickets manager = new RemoteWorkerChunkTickets(fallback);
        ForgeChunkManager.setForcedChunkLoadingCallback(mod, manager);
        MinecraftForge.EVENT_BUS.register(manager);
    }

    private static ChunkCoordIntPair self(TileEntity owner) {
        return new ChunkCoordIntPair(owner.xCoord >> 4, owner.zCoord >> 4);
    }

    private static Ticket request(TileEntity owner, String type, int part) {
        Ticket ticket = ForgeChunkManager.requestTicket(mod, owner.getWorldObj(), ForgeChunkManager.Type.NORMAL);
        if (ticket == null) return null;
        ticket.getModData()
            .setString("OwnerType", type);
        ticket.getModData()
            .setInteger("OwnerX", owner.xCoord);
        ticket.getModData()
            .setInteger("OwnerY", owner.yCoord);
        ticket.getModData()
            .setInteger("OwnerZ", owner.zCoord);
        ticket.getModData()
            .setInteger("WorkerPart", part);
        return ticket;
    }

    private static int capacity(Ticket ticket, int desiredSize) {
        // Forge forceChunk only evicts when depth > 0; zero/negative native depth is unlimited.
        // Still limit this ticket to the current machine footprint, never serialized arbitrary chunks.
        return ticket.getChunkListDepth() > 0 ? ticket.getChunkListDepth() : desiredSize;
    }

    /** Self is always first. Never submit more chunks to a ticket than its actual native depth. */
    public static boolean ensure(TileEntity owner, List<ChunkCoordIntPair> work) {
        if (!GTValues.enableChunkloaders || mod == null || UNLOADED.contains(owner.getWorldObj())) return false;
        // Retire a legacy restored GT ticket while the world is active, before issuing GTSR tickets.
        GTChunkManager.releaseTicket(owner);
        return ensureGroup(owner, work, NODES, NODE);
    }

    private static boolean ensureGroup(TileEntity owner, List<ChunkCoordIntPair> work,
        Map<TileEntity, List<Ticket>> registry, String type) {
        Set<ChunkCoordIntPair> unique = new LinkedHashSet<>();
        unique.add(self(owner));
        unique.addAll(work);
        List<ChunkCoordIntPair> desired = new ArrayList<>(unique);
        List<Ticket> tickets = registry.computeIfAbsent(owner, key -> new ArrayList<>());
        int capacity = 0;
        for (Ticket ticket : tickets) capacity += capacity(ticket, desired.size());
        int previousSize = tickets.size();
        while (capacity < desired.size()) {
            Ticket ticket = request(owner, type, tickets.size());
            if (ticket == null) {
                // Roll back this attempt, preserving only a recovery/self ticket.
                while (tickets.size() > Math.max(1, previousSize)) {
                    ForgeChunkManager.releaseTicket(tickets.remove(tickets.size() - 1));
                }
                if (!tickets.isEmpty()) reconcile(tickets, java.util.Collections.singletonList(self(owner)));
                if (tickets.isEmpty()) registry.remove(owner);
                return false;
            }
            tickets.add(ticket);
            capacity += capacity(ticket, desired.size());
        }
        reconcile(tickets, desired);
        Set<ChunkCoordIntPair> actual = new LinkedHashSet<>();
        for (Ticket ticket : tickets) actual.addAll(ticket.getChunkList());
        if (actual.containsAll(desired) && actual.contains(self(owner))) return true;
        reconcile(tickets, java.util.Collections.singletonList(self(owner)));
        return false;
    }

    private static void reconcile(List<Ticket> tickets, List<ChunkCoordIntPair> desired) {
        int offset = 0, used = 0;
        for (Ticket ticket : tickets) {
            if (offset >= desired.size()) break;
            int end = Math.min(desired.size(), offset + capacity(ticket, desired.size()));
            List<ChunkCoordIntPair> part = desired.subList(offset, end);
            for (ChunkCoordIntPair old : new ArrayList<>(ticket.getChunkList())) {
                if (!part.contains(old)) ForgeChunkManager.unforceChunk(ticket, old);
            }
            for (ChunkCoordIntPair chunk : part) {
                if (!ticket.getChunkList()
                    .contains(chunk)) ForgeChunkManager.forceChunk(ticket, chunk);
            }
            offset = end;
            used++;
        }
        while (tickets.size() > used) ForgeChunkManager.releaseTicket(tickets.remove(tickets.size() - 1));
    }

    public static boolean hasSelf(TileEntity owner) {
        List<Ticket> tickets = NODES.get(owner);
        return tickets != null && !tickets.isEmpty()
            && tickets.get(0)
                .getChunkList()
                .contains(self(owner));
    }

    /** The caller resolves only an already loaded, valid bound hub; this never opens another world. */
    public static void bindHub(TileEntity owner, TileEntity hub) {
        TileEntity previous = BINDINGS.get(owner);
        if (previous != hub) {
            BINDINGS.remove(owner);
            releaseUnusedHub(previous);
        }
        if (hub == null || !GTValues.enableChunkloaders
            || mod == null
            || UNLOADED.contains(owner.getWorldObj())
            || UNLOADED.contains(hub.getWorldObj())) return;
        BINDINGS.put(owner, hub);
        List<ChunkCoordIntPair> footprint = hub instanceof IGregTechTileEntity base
            && base.getMetaTileEntity() instanceof MTESingularityDrillingHub machine
                ? machine.getChunkLoadingFootprint()
                : java.util.Collections.singletonList(self(hub));
        ensureGroup(hub, footprint, HUBS, HUB);
    }

    private static void releaseUnusedHub(TileEntity hub) {
        if (hub == null || BINDINGS.containsValue(hub)) return;
        List<Ticket> tickets = HUBS.remove(hub);
        if (tickets != null && running() && !UNLOADED.contains(hub.getWorldObj())) {
            for (Ticket ticket : tickets) ForgeChunkManager.releaseTicket(ticket);
        }
    }

    public static void release(TileEntity owner) {
        boolean live = running() && !UNLOADED.contains(owner.getWorldObj());
        if (live) GTChunkManager.releaseTicket(owner);
        List<Ticket> tickets = NODES.remove(owner);
        if (tickets != null && live) for (Ticket ticket : tickets) ForgeChunkManager.releaseTicket(ticket);
        releaseUnusedHub(BINDINGS.remove(owner));
    }

    private static boolean running() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null || server.isServerRunning();
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) return;
        UNLOADED.add(event.world);
        // Forge's HIGHEST WorldUnload handler already owns native cleanup. At NORMAL priority,
        // never release or unforce a ticket belonging to the world being unloaded.
        Set<TileEntity> detachedHubs = new LinkedHashSet<>();
        for (TileEntity owner : new ArrayList<>(NODES.keySet())) {
            if (owner.getWorldObj() != event.world) continue;
            NODES.remove(owner);
        }
        for (TileEntity hub : new ArrayList<>(HUBS.keySet())) {
            if (hub.getWorldObj() != event.world) continue;
            HUBS.remove(hub);
        }
        BINDINGS.entrySet()
            .removeIf(entry -> {
                if (entry.getKey()
                    .getWorldObj() != event.world
                    && entry.getValue()
                        .getWorldObj() != event.world)
                    return false;
                detachedHubs.add(entry.getValue());
                return true;
            });
        for (TileEntity hub : detachedHubs) {
            if (hub.getWorldObj() != event.world) releaseUnusedHub(hub);
        }
    }

    @SubscribeEvent
    public void load(WorldEvent.Load event) {
        if (!event.world.isRemote) UNLOADED.remove(event.world);
    }

    private static boolean worker(Ticket ticket) {
        String type = ticket.getModData()
            .getString("OwnerType");
        return NODE.equals(type) || HUB.equals(type);
    }

    @Override
    public List<Ticket> ticketsLoaded(List<Ticket> tickets, World world, int maxTicketCount) {
        if (UNLOADED.contains(world)) return new ArrayList<>();
        List<Ticket> others = new ArrayList<>(), kept = new ArrayList<>();
        for (Ticket ticket : tickets) {
            if (!worker(ticket)) others.add(ticket);
        }
        kept.addAll(fallback.ticketsLoaded(others, world, maxTicketCount));
        if (!GTValues.enableChunkloaders || !GTValues.alwaysReloadChunkloaders) return kept;
        for (Ticket ticket : tickets) {
            if (kept.size() >= maxTicketCount) break;
            if (!NODE.equals(
                ticket.getModData()
                    .getString("OwnerType"))
                || ticket.getModData()
                    .getInteger("WorkerPart") != 0)
                continue;
            int x = ticket.getModData()
                .getInteger("OwnerX"),
                y = ticket.getModData()
                    .getInteger("OwnerY"),
                z = ticket.getModData()
                    .getInteger("OwnerZ");
            if (y < 0 || y >= 256) continue;
            TileEntity tile = world.getTileEntity(x, y, z);
            if (!(tile instanceof IGregTechTileEntity base)
                || !(base.getMetaTileEntity() instanceof MTERemoteWorkerNode node)
                || !node.isBound()
                || NODES.containsKey(tile)) continue;
            List<Ticket> ownerTickets = new ArrayList<>();
            ownerTickets.add(ticket);
            NODES.put(tile, ownerTickets);
            ForgeChunkManager.forceChunk(ticket, self(tile));
            kept.add(ticket);
        }
        return kept;
    }

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        List<Ticket> others = new ArrayList<>();
        for (Ticket ticket : tickets) if (!worker(ticket)) others.add(ticket);
        fallback.ticketsLoaded(others, world);
        // Normal node ticks rebuild the current footprint and a loaded hub; never recurse into dimensions here.
    }
}
