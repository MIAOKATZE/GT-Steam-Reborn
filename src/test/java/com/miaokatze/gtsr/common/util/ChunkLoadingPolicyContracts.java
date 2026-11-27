// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.machine.MTEHeavySteamTurbine;
import com.miaokatze.gtsr.common.machine.MTESingularityMinerNode;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;

import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;

/** Real owner classification and native ticket metadata; sparse IO boundary, no server is launched. */
public final class ChunkLoadingPolicyContracts {

    private static sun.misc.Unsafe unsafe;
    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, ChunkLoadingPolicyContracts.class.getName(), args)) return;
        Field allocator = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        allocator.setAccessible(true);
        unsafe = (sun.misc.Unsafe) allocator.get(null);
        boolean previous = GTValues.enableChunkloaders;
        try {
            automation();
            owners();
        } finally {
            GTValues.enableChunkloaders = previous;
        }
        System.out.println("PASS chunk loading policy checks=" + checks + "; sparse IO only; no Minecraft process");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }

    private static SparseWorld world() throws Exception {
        SparseWorld world = (SparseWorld) unsafe.allocateInstance(SparseWorld.class);
        world.loaded = new HashSet<>();
        world.tiles = new HashMap<>();
        world.providerStub = (IChunkProvider) Proxy.newProxyInstance(
            IChunkProvider.class.getClassLoader(),
            new Class<?>[] { IChunkProvider.class },
            (proxy, method, values) -> {
                if (method.getName()
                    .equals("loadChunk")) {
                    world.loads++;
                    if (world.failLoad) throw new IllegalStateException("fixture IO failure");
                    world.loaded.add(key((Integer) values[0], (Integer) values[1]));
                    return null;
                }
                throw new AssertionError("Unexpected provider access " + method.getName());
            });
        return world;
    }

    private static void automation() throws Exception {
        SparseWorld world = world();
        GTValues.enableChunkloaders = false;
        world.loaded.add(key(0, 0));
        check(HubTeleportUtil.ensureAutomationChunkLoaded(world, 0, 0), "loaded chunks work with loading disabled");
        check(!HubTeleportUtil.ensureAutomationChunkLoaded(world, 16, 0) && world.loads == 0, "disabled does not load");
        GTValues.enableChunkloaders = true;
        check(HubTeleportUtil.ensureAutomationChunkLoaded(world, 16, 0) && world.loads == 1, "first request allowed");
        check(
            !HubTeleportUtil.ensureAutomationChunkLoaded(world, 32, 0) && world.loads == 1,
            "world tick budget shared");
        world.time++;
        check(
            HubTeleportUtil.ensureAutomationChunkLoaded(world, 32, 0) && world.loads == 2,
            "next tick advances queue");
        world.time++;
        world.failLoad = true;
        check(!HubTeleportUtil.ensureAutomationChunkLoaded(world, 48, 0) && world.loads == 3, "failure reported");
        world.time++;
        world.failLoad = false;
        check(
            !HubTeleportUtil.ensureAutomationChunkLoaded(world, 48, 0) && world.loads == 3,
            "failed request backs off");
        check(
            HubTeleportUtil.ensureAutomationChunkLoaded(world, 64, 0) && world.loads == 4,
            "failure does not starve another chunk");
        GTValues.enableChunkloaders = false;
        check(HubTeleportUtil.ensureChunkLoaded(world, 80, 0), "explicit user operation keeps independent loading");
        check(!HubTeleportUtil.ensureAutomationChunkLoaded(null, 0, 0), "missing world never initialized");
    }

    private static BaseMetaTileEntity owner(SparseWorld world, Class<? extends MetaTileEntity> type, int x)
        throws Exception {
        MetaTileEntity meta = (MetaTileEntity) unsafe.allocateInstance(type);
        BaseMetaTileEntity tile = new BaseMetaTileEntity();
        tile.setWorldObj(world);
        tile.xCoord = x;
        tile.yCoord = 64;
        meta.setBaseMetaTileEntity(tile);
        world.tiles.put(x, tile);
        return tile;
    }

    private static void owners() throws Exception {
        GTValues.enableChunkloaders = true;
        SparseWorld world = world();
        BaseMetaTileEntity critical = owner(world, MTECriticalMachine.class, 0);
        BaseMetaTileEntity turbine = owner(world, MTEHeavySteamTurbine.class, 16);
        BaseMetaTileEntity hub = owner(world, MTESteamHubArray.class, 32);
        BaseMetaTileEntity worker = owner(world, MTESingularityMinerNode.class, 48);
        BaseMetaTileEntity foreign = owner(world, MTEHatchInputBus.class, 64);
        check(GtsrChunkLoadingPolicy.isGtsrOwner(critical), "critical owner recognized");
        check(!GtsrChunkLoadingPolicy.permitsPersistentLoading(critical), "critical cannot persistently load");
        check(!GtsrChunkLoadingPolicy.permitsPersistentLoading(turbine), "ordinary machine cannot persistently load");
        check(GtsrChunkLoadingPolicy.permitsPersistentLoading(hub), "hub permitted");
        check(GtsrChunkLoadingPolicy.permitsPersistentLoading(worker), "worker permitted");
        check(!GtsrChunkLoadingPolicy.isGtsrOwner(foreign), "other mod not scoped");
        var ctor = Ticket.class.getDeclaredConstructor(String.class, Type.class, World.class);
        ctor.setAccessible(true);
        List<Ticket> tickets = new java.util.ArrayList<>();
        for (BaseMetaTileEntity tile : new BaseMetaTileEntity[] { critical, turbine, hub, worker, foreign }) {
            Ticket ticket = ctor.newInstance("gregtech", Type.NORMAL, world);
            ticket.getModData()
                .setInteger("OwnerX", tile.xCoord);
            ticket.getModData()
                .setInteger("OwnerY", tile.yCoord);
            ticket.getModData()
                .setInteger("OwnerZ", tile.zCoord);
            tickets.add(ticket);
        }
        List<Ticket> kept = GtsrChunkLoadingPolicy.filterLegacyGtTickets(tickets, world);
        check(
            kept.size() == 3 && kept.containsAll(tickets.subList(2, 5)),
            "retired owners omitted without touching others");
        check(tickets.size() == 5, "input ticket list remains unchanged");
        GTValues.enableChunkloaders = false;
        kept = GtsrChunkLoadingPolicy.filterLegacyGtTickets(tickets, world);
        check(
            kept.size() == 1 && kept.get(0) == tickets.get(4),
            "global disable rejects all restored GTSR owners only");
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static final class SparseWorld extends World {

        Set<Long> loaded;
        Map<Integer, TileEntity> tiles;
        IChunkProvider providerStub;
        long time;
        int loads;
        boolean failLoad;

        private SparseWorld() {
            super(null, "unused", (WorldProvider) null, null, null);
        }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return loaded.contains(key(x >> 4, z >> 4));
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            return tiles.get(x);
        }

        @Override
        public long getTotalWorldTime() {
            return time;
        }

        @Override
        public IChunkProvider getChunkProvider() {
            return providerStub;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return providerStub;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int id) {
            return null;
        }
    }
}
