// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.common.machinevisual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;
import com.miaokatze.gtsr.common.network.ServerPacketAuthority;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Chunk-observation index for loaded owners. No fake world block, client request or owner chunk loading. */
public final class VirtualStructureNetwork {

    private static final Map<World, Index> WORLDS = new IdentityHashMap<>();
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private static final int MAX_CHUNKS = 1024;

    private VirtualStructureNetwork() {}

    public static void register() {
        VirtualStructureNetwork listener = new VirtualStructureNetwork();
        MinecraftForge.EVENT_BUS.register(listener);
        FMLCommonHandler.instance()
            .bus()
            .register(listener);
    }

    public static void observe(MTEMineralIntegrationCenter center) {
        IGregTechTileEntity gt = center.getBaseMetaTileEntity();
        if (!(gt instanceof TileEntity tile) || !(tile.getWorldObj() instanceof WorldServer world)) return;
        Index index = WORLDS.computeIfAbsent(world, ignored -> new Index(world));
        Source source = index.sources.get(position(tile.xCoord, tile.yCoord, tile.zCoord));
        long revision = center.moduleVisualRevision();
        int facing = gt.getFrontFacing()
            .ordinal();
        if (source != null && source.owner == tile && source.revision == revision && source.facing == facing) return;
        NBTTagCompound visual = new NBTTagCompound();
        visual.setIntArray("types", center.installedModuleTypes());
        visual.setIntArray("grades", center.installedModuleGrades());
        visual.setInteger("facing", facing);
        visual.setLong("revision", revision);
        // The footprint is bounded and fixed; installing another module never needs a player/world scan.
        source = index.ensure(
            tile,
            VirtualStructureFrame.CENTER,
            tile.xCoord - 176,
            tile.zCoord - 176,
            tile.xCoord + 176,
            tile.zCoord + 176);
        source.revision = revision;
        source.facing = facing;
        source.update(visual);
    }

    public static void remove(IGregTechTileEntity owner) {
        if (owner instanceof TileEntity tile) remove(tile);
    }

    public static void remove(TileEntity owner) {
        if (owner == null || owner.getWorldObj() == null) return;
        Index index = WORLDS.get(owner.getWorldObj());
        if (index == null) return;
        Source source = index.sources.get(position(owner.xCoord, owner.yCoord, owner.zCoord));
        if (source != null && source.owner == owner) index.remove(source);
    }

    static long position(int x, int y, int z) {
        return ((x & 0x3ffffffL) << 38) | ((z & 0x3ffffffL) << 12) | (y & 0xfffL);
    }

    static long chunk(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    @SubscribeEvent
    public void watch(ChunkWatchEvent.Watch event) {
        Index index = WORLDS.get(event.player.worldObj);
        if (index == null) return;
        long key = chunk(event.chunk.chunkXPos, event.chunk.chunkZPos);
        Set<Source> sources = index.chunks.get(key);
        if (sources != null) for (Source source : sources) source.watch(event.player, key);
    }

    @SubscribeEvent
    public void unwatch(ChunkWatchEvent.UnWatch event) {
        Index index = WORLDS.get(event.player.worldObj);
        if (index == null) return;
        long key = chunk(event.chunk.chunkXPos, event.chunk.chunkZPos);
        Set<Source> sources = index.chunks.get(key);
        if (sources != null) for (Source source : sources) source.unwatch(event.player, key);
    }

    @SubscribeEvent
    public void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.world.isRemote) return;
        Index index = WORLDS.get(event.world);
        if (index == null) return;
        Set<Source> sources = index.chunks.get(chunk(event.getChunk().xPosition, event.getChunk().zPosition));
        if (sources != null) for (Source source : new ArrayList<>(sources)) {
            if (source.owner.xCoord >> 4 == event.getChunk().xPosition
                && source.owner.zCoord >> 4 == event.getChunk().zPosition) index.remove(source);
        }
    }

    @SubscribeEvent
    public void worldUnloaded(WorldEvent.Unload event) {
        Index index = WORLDS.remove(event.world);
        if (index != null) for (Source source : new ArrayList<>(index.sources.values())) index.remove(source);
    }

    @SubscribeEvent
    public void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP player)) return;
        // One logout, over registered visual sources only; no world/TE enumeration or tick polling.
        for (Index index : WORLDS.values()) for (Source source : index.sources.values()) source.watchers.remove(player);
    }

    @SubscribeEvent
    public void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP player) for (Index index : WORLDS.values())
            for (Source source : index.sources.values()) source.watchers.remove(player);
    }

    private static final class Index {

        final WorldServer world;
        final Map<Long, Source> sources = new HashMap<>();
        final Map<Long, Set<Source>> chunks = new HashMap<>();

        Index(WorldServer world) {
            this.world = world;
        }

        Source ensure(TileEntity tile, int kind, int minX, int minZ, int maxX, int maxZ) {
            long key = position(tile.xCoord, tile.yCoord, tile.zCoord);
            Source existing = sources.get(key);
            if (existing != null && existing.owner == tile
                && existing.kind == kind
                && existing.minX == minX >> 4
                && existing.minZ == minZ >> 4
                && existing.maxX == maxX >> 4
                && existing.maxZ == maxZ >> 4) return existing;
            if (existing != null) remove(existing);
            int x0 = minX >> 4, z0 = minZ >> 4, x1 = maxX >> 4, z1 = maxZ >> 4;
            if ((long) (x1 - x0 + 1) * (z1 - z0 + 1) > MAX_CHUNKS)
                throw new IllegalArgumentException("Virtual structure footprint exceeds index limit");
            Source source = new Source(this, tile, kind, x0, z0, x1, z1);
            sources.put(key, source);
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) chunks.computeIfAbsent(chunk(x, z), ignored -> new HashSet<>())
                    .add(source);
            // Only initial registration seeds existing observers. Subsequent updates use chunk events and watchers.
            for (Object object : world.playerEntities) if (object instanceof EntityPlayerMP player) {
                for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (world.getPlayerManager()
                    .isPlayerWatchingChunk(player, x, z)) source.watch(player, chunk(x, z));
            }
            return source;
        }

        void remove(Source source) {
            sources.remove(position(source.owner.xCoord, source.owner.yCoord, source.owner.zCoord));
            for (int x = source.minX; x <= source.maxX; x++) for (int z = source.minZ; z <= source.maxZ; z++) {
                long key = chunk(x, z);
                Set<Source> bucket = chunks.get(key);
                if (bucket != null) {
                    bucket.remove(source);
                    if (bucket.isEmpty()) chunks.remove(key);
                }
            }
            VirtualStructureFrame removed = source.frame(VirtualStructureFrame.REMOVED, new NBTTagCompound());
            for (EntityPlayerMP player : source.watchers.keySet()) source.send(player, removed);
            source.watchers.clear();
        }
    }

    private static final class Source {

        final Index index;
        final TileEntity owner;
        final int kind, minX, minZ, maxX, maxZ;
        final long generation = GENERATIONS.incrementAndGet();
        final Map<EntityPlayerMP, Set<Long>> watchers = new IdentityHashMap<>();
        long sequence, revision = Long.MIN_VALUE;
        int facing = -1;
        NBTTagCompound visual;

        Source(Index index, TileEntity owner, int kind, int minX, int minZ, int maxX, int maxZ) {
            this.index = index;
            this.owner = owner;
            this.kind = kind;
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        void update(NBTTagCompound next) {
            if (next.equals(visual)) return;
            visual = (NBTTagCompound) next.copy();
            VirtualStructureFrame frame = frame(kind, visual);
            java.util.Iterator<EntityPlayerMP> players = watchers.keySet()
                .iterator();
            while (players.hasNext()) {
                EntityPlayerMP player = players.next();
                if (!ServerPacketAuthority.isCurrentPlayer(player) || player.worldObj != index.world) players.remove();
                else send(player, frame);
            }
        }

        void watch(EntityPlayerMP player, long chunk) {
            if (!ServerPacketAuthority.isCurrentPlayer(player) || player.worldObj != index.world) return;
            boolean first = !watchers.containsKey(player);
            watchers.computeIfAbsent(player, ignored -> new HashSet<>())
                .add(chunk);
            if (first && visual != null) send(player, frame(kind, visual));
        }

        void unwatch(EntityPlayerMP player, long chunk) {
            Set<Long> watched = watchers.get(player);
            if (watched == null) return;
            watched.remove(chunk);
            if (watched.isEmpty()) {
                watchers.remove(player);
                send(player, frame(VirtualStructureFrame.REMOVED, new NBTTagCompound()));
            }
        }

        VirtualStructureFrame frame(int type, NBTTagCompound data) {
            return new VirtualStructureFrame(
                index.world.provider.dimensionId,
                owner.xCoord,
                owner.yCoord,
                owner.zCoord,
                type,
                generation,
                ++sequence,
                data);
        }

        void send(EntityPlayerMP player, VirtualStructureFrame frame) {
            if (ServerPacketAuthority.isCurrentPlayer(player) && player.worldObj == index.world)
                MachineVisualNetwork.sendVirtual(player, frame);
        }
    }

    public static final class Handler implements IMessageHandler<VirtualStructureFrame, IMessage> {

        @Override
        public IMessage onMessage(VirtualStructureFrame message, MessageContext context) {
            GTSteamReborn.proxy.receiveVirtualStructure(message, context.getClientHandler());
            return null;
        }
    }
}
