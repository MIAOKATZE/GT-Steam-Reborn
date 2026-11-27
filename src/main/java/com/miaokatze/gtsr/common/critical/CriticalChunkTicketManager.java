// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.WorldEvent;

import com.google.common.collect.Multimap;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/** Legacy critical-ticket retirement and the shared GTSR remote-worker registration entry point. */
public final class CriticalChunkTicketManager implements ForgeChunkManager.OrderedLoadingCallback {

    private static final Field NATIVE_TICKETS = nativeTicketsField();
    private static final CriticalChunkTicketManager INSTANCE = new CriticalChunkTicketManager();
    private static final Set<World> UNLOADED = Collections.newSetFromMap(new WeakHashMap<>());
    private static Object mod;

    private CriticalChunkTicketManager() {}

    /** The remote-worker route and its existing administrator quotas remain authoritative. */
    public static void register() {
        if (mod != null) return;
        mod = Loader.instance()
            .getIndexedModList()
            .get(GTSteamReborn.MODID)
            .getMod();
        configureDefaults(ForgeChunkManager.getConfig());
        com.miaokatze.gtsr.common.machine.base.RemoteWorkerChunkTickets.register(mod, INSTANCE);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    static void configureDefaults(Configuration config) {
        // Configuration.get preserves existing administrator values, including zero.
        config.get(GTSteamReborn.MODID, "maximumChunksPerTicket", 64);
        config.get(GTSteamReborn.MODID, "maximumTicketCount", 200);
    }

    static boolean isLegacyCriticalTicket(Ticket ticket) {
        // Every ticket issued by the removed critical loader wrote this explicit marker.
        // An arbitrary GTSR ticket with matching XYZ is not sufficient evidence of ownership.
        return GTSteamReborn.MODID.equals(ticket.getModId()) && !ticket.isPlayerTicket()
            && "CriticalLoom".equals(
                ticket.getModData()
                    .getString("OwnerType"));
    }

    private static Field nativeTicketsField() {
        try {
            // Forge 10.13.4.1614 is already loaded before GTSR's late mixins can run.
            // Cache its exact field once; a missing field must fail explicitly, never report clean.
            return ReflectionHelper.findField(ForgeChunkManager.class, "tickets");
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                "Forge 1614 native ticket registry unavailable; legacy cleanup cannot run",
                failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<World, Multimap<String, Ticket>> nativeTickets() {
        try {
            return (Map<World, Multimap<String, Ticket>>) NATIVE_TICKETS.get(null);
        } catch (IllegalAccessException | ClassCastException failure) {
            throw new IllegalStateException(
                "Cannot read Forge 1614 native ticket registry; legacy cleanup aborted",
                failure);
        }
    }

    /** Copy only GTSR's native entries: empty tickets have no forced-chunk index entry. */
    private static Set<Ticket> nativeLegacyTickets(World world) {
        Multimap<String, Ticket> tickets = nativeTickets().get(world);
        if (tickets == null) return Collections.emptySet();
        Set<Ticket> legacy = new LinkedHashSet<>();
        for (Ticket ticket : tickets.get(GTSteamReborn.MODID)) if (isLegacyCriticalTicket(ticket)) legacy.add(ticket);
        return legacy;
    }

    /** Only this loaded owner's legacy critical ticket may be released; never a remote-worker ticket. */
    public static void releaseLegacyTickets(TileEntity owner) {
        if (owner == null || !live(owner.getWorldObj())) return;
        for (Ticket ticket : nativeLegacyTickets(owner.getWorldObj())) {
            if (!isLegacyCriticalTicket(ticket) || !ticket.getModData()
                .hasKey("OwnerX")
                || !ticket.getModData()
                    .hasKey("OwnerY")
                || !ticket.getModData()
                    .hasKey("OwnerZ"))
                continue;
            if (ticket.getModData()
                .getInteger("OwnerX") == owner.xCoord
                && ticket.getModData()
                    .getInteger("OwnerY") == owner.yCoord
                && ticket.getModData()
                    .getInteger("OwnerZ") == owner.zCoord)
                ForgeChunkManager.releaseTicket(ticket);
        }
    }

    private static boolean live(World world) {
        MinecraftServer server = MinecraftServer.getServer();
        return world != null && !world.isRemote
            && !UNLOADED.contains(world)
            && (server == null || server.isServerRunning());
    }

    /** Retire orphaned legacy tickets too, without looking up or loading their owner chunks. */
    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !live(event.world) || event.world.getTotalWorldTime() % 20 != 0)
            return;
        for (Ticket ticket : nativeLegacyTickets(event.world))
            if (isLegacyCriticalTicket(ticket)) ForgeChunkManager.releaseTicket(ticket);
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (!event.world.isRemote) UNLOADED.add(event.world);
        // Forge's HIGHEST handler has already removed its native world ticket map.
        // Do not release/unforce any native ticket from this NORMAL-priority callback.
    }

    @SubscribeEvent
    public void load(WorldEvent.Load event) {
        if (!event.world.isRemote) UNLOADED.remove(event.world);
    }

    @Override
    public List<Ticket> ticketsLoaded(List<Ticket> tickets, World world, int maxTicketCount) {
        // Forge discards every omitted ticket itself. Never inspect a tile or force an owner chunk.
        // RemoteWorkerChunkTickets filters its own ticket types before invoking this fallback.
        return Collections.emptyList();
    }

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        if (!live(world)) return;
        // Defensive retirement if invoked directly with already registered legacy tickets.
        for (Ticket ticket : tickets) if (isLegacyCriticalTicket(ticket)) ForgeChunkManager.releaseTicket(ticket);
    }
}
