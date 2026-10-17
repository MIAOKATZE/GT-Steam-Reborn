package com.miaokatze.gtsr.common.critical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.config.Configuration;

import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.Loader;
import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** GT-style one-owner tickets, isolated under GTSR's own Forge quota and restart callback. */
public final class CriticalChunkTicketManager implements ForgeChunkManager.OrderedLoadingCallback {

    private static final CriticalChunkTicketManager INSTANCE = new CriticalChunkTicketManager();
    private static final Map<TileEntity, Ticket> REGISTERED = new HashMap<>();
    private static Object mod;

    private CriticalChunkTicketManager() {}

    /** Called during mod pre-init; Forge loads these per-mod constraints after pre-init finishes. */
    public static void register() {
        if (mod != null) return;
        mod = Loader.instance()
            .getIndexedModList()
            .get(GTSteamReborn.MODID)
            .getMod();
        configureDefaults(ForgeChunkManager.getConfig());
        com.miaokatze.gtsr.common.machine.base.RemoteWorkerChunkTickets.register(mod, INSTANCE);
    }

    static void configureDefaults(Configuration config) {
        // Configuration.get preserves an existing administrator value, including values below 64.
        config.get(GTSteamReborn.MODID, "maximumChunksPerTicket", 64);
        config.get(GTSteamReborn.MODID, "maximumTicketCount", 200);
    }

    static boolean requestChunkLoad(TileEntity owner, ChunkCoordIntPair chunk) {
        Ticket ticket = ensureTicket(owner);
        if (ticket == null) return false;
        ForgeChunkManager.forceChunk(ticket, chunk);
        return ticket.getChunkList()
            .contains(chunk);
    }

    static Ticket getTicket(TileEntity owner) {
        return REGISTERED.get(owner);
    }

    static Ticket ensureTicket(TileEntity owner) {
        if (!GTValues.enableChunkloaders || mod == null) return null;
        Ticket ticket = REGISTERED.get(owner);
        if (ticket == null) {
            ticket = ForgeChunkManager.requestTicket(mod, owner.getWorldObj(), ForgeChunkManager.Type.NORMAL);
            if (ticket == null) return null;
            ticket.getModData()
                .setInteger("OwnerX", owner.xCoord);
            ticket.getModData()
                .setInteger("OwnerY", owner.yCoord);
            ticket.getModData()
                .setInteger("OwnerZ", owner.zCoord);
            ticket.getModData()
                .setString("OwnerType", "CriticalLoom");
            REGISTERED.put(owner, ticket);
        }
        return ticket;
    }

    static void releaseChunk(TileEntity owner, ChunkCoordIntPair chunk) {
        Ticket ticket = REGISTERED.get(owner);
        if (ticket != null) ForgeChunkManager.unforceChunk(ticket, chunk);
    }

    static void releaseTicket(TileEntity owner) {
        Ticket ticket = REGISTERED.remove(owner);
        // Unlike upstream GT's disabled-config early return, cleanup must always run.
        if (ticket != null) ForgeChunkManager.releaseTicket(ticket);
    }

    static void releaseWorld(World world, boolean forgeTicketsUnloaded) {
        for (TileEntity owner : new ArrayList<>(REGISTERED.keySet())) {
            if (owner.getWorldObj() != world) continue;
            if (forgeTicketsUnloaded) REGISTERED.remove(owner);
            else releaseTicket(owner);
        }
    }

    @Override
    public List<Ticket> ticketsLoaded(List<Ticket> tickets, World world, int maxTicketCount) {
        List<Ticket> kept = new ArrayList<>();
        if (!GTValues.enableChunkloaders || !GTValues.alwaysReloadChunkloaders) return kept;
        for (Ticket ticket : tickets) {
            if (kept.size() >= maxTicketCount) break;
            if (!ticket.getModData()
                .hasKey("OwnerX")
                || !ticket.getModData()
                    .hasKey("OwnerY")
                || !ticket.getModData()
                    .hasKey("OwnerZ"))
                continue;
            int x = ticket.getModData()
                .getInteger("OwnerX");
            int y = ticket.getModData()
                .getInteger("OwnerY");
            int z = ticket.getModData()
                .getInteger("OwnerZ");
            // Match GT's bounded controller restore. Full footprint comes from the current canonical
            // State, never an unvalidated serialized list of arbitrary force-loaded coordinates.
            TileEntity tile = world.getTileEntity(x, y, z);
            if (!(tile instanceof BaseMetaTileEntity base)
                || !(base.getMetaTileEntity() instanceof MTECriticalMachine machine)) continue;
            CriticalMachineState state = machine.getLocalCriticalState();
            if (!state.isLoomJournalOwner() || !state.isChunkLoadingEnabled() || REGISTERED.containsKey(tile)) continue;
            REGISTERED.put(tile, ticket);
            ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(x >> 4, z >> 4));
            kept.add(ticket);
        }
        return kept;
    }

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        for (Ticket ticket : tickets) {
            int x = ticket.getModData()
                .getInteger("OwnerX"),
                y = ticket.getModData()
                    .getInteger("OwnerY"),
                z = ticket.getModData()
                    .getInteger("OwnerZ");
            TileEntity tile = world.getTileEntity(x, y, z);
            if (REGISTERED.get(tile) != ticket || !(tile instanceof BaseMetaTileEntity base)
                || !(base.getMetaTileEntity() instanceof MTECriticalMachine machine)) continue;
            CriticalMachineState state = machine.getLocalCriticalState();
            state.getChunkLoading()
                .ensureAllStructureChunks(world, tile, state.getChunkLoadingFootprint());
        }
    }
}
