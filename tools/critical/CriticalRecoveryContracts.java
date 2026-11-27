package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;
import net.minecraftforge.event.world.WorldEvent;

import com.google.common.collect.*;
import com.google.gson.*;

import gregtech.api.enums.GTValues;

/** Real coverage gates, NBT and Forge ticket retirement. Sparse World; no server restart or chunk IO. */
public final class CriticalRecoveryContracts {

    static sun.misc.Unsafe unsafe;
    static final JsonArray checks = new JsonArray();

    static Field field(Class<?> owner, String key) throws Exception {
        Field f = owner.getDeclaredField(key);
        f.setAccessible(true);
        return f;
    }

    static void check(String name, boolean pass) {
        JsonObject row = new JsonObject();
        row.addProperty("name", name);
        row.addProperty("pass", pass);
        checks.add(row);
        if (!pass) throw new AssertionError(name);
    }

    @SuppressWarnings("unchecked")
    static <T> T read(Class<?> owner, String key) throws Exception {
        return (T) field(owner, key).get(null);
    }

    static SparseWorld world() throws Exception {
        SparseWorld w = (SparseWorld) unsafe.allocateInstance(SparseWorld.class);
        field(World.class, "provider").set(w, unsafe.allocateInstance(WorldProviderSurface.class));
        w.loaded = new HashSet<>();
        w.chunks = (IChunkProvider) Proxy.newProxyInstance(
            IChunkProvider.class.getClassLoader(),
            new Class<?>[] { IChunkProvider.class },
            (p, m, a) -> {
                if (m.getName()
                    .equals("chunkExists")) return w.loaded.contains(new ChunkCoordIntPair((int) a[0], (int) a[1]));
                throw new AssertionError("Unexpected chunk access: " + m.getName());
            });
        Map<World, Multimap<String, Ticket>> tickets = read(ForgeChunkManager.class, "tickets");
        tickets.put(w, ArrayListMultimap.create());
        Map<World, ImmutableSetMultimap<ChunkCoordIntPair, Ticket>> forced = read(
            ForgeChunkManager.class,
            "forcedChunks");
        forced.put(w, ImmutableSetMultimap.of());
        return w;
    }

    static Ticket ticket(SparseWorld w, TileEntity owner, String type) throws Exception {
        Constructor<Ticket> ctor = Ticket.class.getDeclaredConstructor(String.class, Type.class, World.class);
        ctor.setAccessible(true);
        Ticket ticket = ctor.newInstance("gtsr", Type.NORMAL, w);
        ticket.getModData()
            .setString("OwnerType", type);
        ticket.getModData()
            .setInteger("OwnerX", owner.xCoord);
        ticket.getModData()
            .setInteger("OwnerY", owner.yCoord);
        ticket.getModData()
            .setInteger("OwnerZ", owner.zCoord);
        Map<World, Multimap<String, Ticket>> tickets = read(ForgeChunkManager.class, "tickets");
        tickets.get(w)
            .put("gtsr", ticket);
        ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(owner.xCoord >> 4, owner.zCoord >> 4));
        return ticket;
    }

    public static void main(String[] args) throws Exception {
        unsafe = (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
        boolean oldSwitch = GTValues.enableChunkloaders;
        JsonObject report = new JsonObject();
        try {
            check(
                "native ticket field contract is fixed to Forge 10.13.4.1614",
                "10.13.4.1614".equals(net.minecraftforge.common.ForgeVersion.getVersion()));
            report.addProperty("forgeVersion", net.minecraftforge.common.ForgeVersion.getVersion());
            report.addProperty(
                "managerSource",
                CriticalChunkTicketManager.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toString());
            SparseWorld w = world();
            TileEntity owner = new TileEntity();
            owner.setWorldObj(w);
            owner.xCoord = 1;
            owner.yCoord = 64;
            owner.zCoord = 1;
            CriticalMachineState state = new CriticalMachineState();
            state.worldObj = w;
            field(CriticalMachineState.class, "host").set(state, owner);
            List<ChunkCoordIntPair> footprint = Arrays.asList(new ChunkCoordIntPair(0, 0), new ChunkCoordIntPair(7, 7));
            // An explicit bounded footprint isolates the gate from asset registration and layout generation.
            field(CriticalMachineState.class, "loomChunksCache").set(state, footprint);
            w.loaded.addAll(footprint);
            Method ensure = CriticalMachineState.class
                .getDeclaredMethod("ensureConstructionWindow", List.class, int.class, int.class);
            ensure.setAccessible(true);
            GTValues.enableChunkloaders = false;
            check(
                "player-loaded footprint remains ready with GT chunkloaders disabled",
                (Boolean) ensure.invoke(state, Collections.emptyList(), 0, 0));
            Map<World, Multimap<String, Ticket>> tickets = read(ForgeChunkManager.class, "tickets");
            check(
                "coverage gate creates no Forge ticket",
                tickets.get(w)
                    .isEmpty());
            w.loaded.remove(footprint.get(1));
            check(
                "missing physical chunk pauses without attempting to load",
                !(Boolean) ensure.invoke(state, Collections.emptyList(), 0, 0) && w.reads == 0);
            Ticket legacy = ticket(w, owner, "CriticalLoom");
            Ticket emptyLegacy = ticket(w, owner, "CriticalLoom");
            ForgeChunkManager.unforceChunk(emptyLegacy, new ChunkCoordIntPair(owner.xCoord >> 4, owner.zCoord >> 4));
            check(
                "fixture empty legacy ticket is absent from forced-chunk index",
                emptyLegacy.getChunkList()
                    .isEmpty()
                    && !ForgeChunkManager.getPersistentChunksFor(w)
                        .containsValue(emptyLegacy));
            Ticket remote = ticket(w, owner, "RemoteWorkerNode");
            Ticket foreign = ticket(w, owner, "UnrelatedOwner");
            CriticalChunkTicketManager.releaseLegacyTickets(owner);
            check(
                "global disabled still releases only marked legacy owner ticket",
                !tickets.get(w)
                    .containsValue(legacy)
                    && !tickets.get(w)
                        .containsValue(emptyLegacy)
                    && tickets.get(w)
                        .containsValue(remote)
                    && tickets.get(w)
                        .containsValue(foreign));
            CriticalChunkTicketManager manager = (CriticalChunkTicketManager) field(
                CriticalChunkTicketManager.class,
                "INSTANCE").get(null);
            check(
                "ordered restore refuses legacy without tile lookup or forcing",
                manager.ticketsLoaded(Collections.singletonList(legacy), w, 200)
                    .isEmpty() && w.reads == 0);
            NBTTagCompound saved = new NBTTagCompound();
            field(CriticalMachineState.class, "batchId").set(state, "prepaid-batch");
            field(CriticalMachineState.class, "cycleTicks").setInt(state, 37);
            field(CriticalMachineState.class, "cycleDuration").setInt(state, 100);
            state.writeToNBT(saved);
            saved.setBoolean("chunkLoadingEnabled", false);
            CriticalMachineState restored = new CriticalMachineState();
            restored.readFromNBT(saved);
            NBTTagCompound rewritten = new NBTTagCompound();
            restored.writeToNBT(rewritten);
            check(
                "old loader switch is ignored and omitted while paid progress survives",
                !rewritten.hasKey("chunkLoadingEnabled") && restored.getBatchProgress() == 37
                    && restored.getBatchId()
                        .equals("prepaid-batch")
                    && !restored.isStructureValidated());
            // Emulate Forge HIGHEST having already removed its world map before the NORMAL event.
            tickets.remove(w);
            manager.unload(new WorldEvent.Unload(w));
            CriticalChunkTicketManager.releaseLegacyTickets(owner);
            check(
                "world-unload cleanup does not release into removed native maps",
                remote.getChunkList()
                    .size() == 1);
            report.addProperty("status", "PASS");
        } finally {
            GTValues.enableChunkloaders = oldSwitch;
            report.add("checks", checks);
            report.addProperty(
                "scope",
                "Actual production coverage gate, state NBT and preseeded Forge ticket retirement; explicit footprint and sparse World. No real restart, new-ticket request, chunk IO or full host processing claimed.");
            if (args.length > 0) Files.writeString(
                Path.of(args[0]),
                new GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(report));
            System.out.println(report);
        }
    }

    static final class SparseWorld extends World {

        Set<ChunkCoordIntPair> loaded;
        IChunkProvider chunks;
        int reads;

        SparseWorld() {
            super(null, "unused", (WorldProvider) null, null, null);
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            reads++;
            throw new AssertionError("Unexpected tile lookup");
        }

        @Override
        public IChunkProvider getChunkProvider() {
            return chunks;
        }

        @Override
        public long getTotalWorldTime() {
            return 0;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected int func_152379_p() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entity getEntityByID(int id) {
            throw new UnsupportedOperationException();
        }
    }
}
