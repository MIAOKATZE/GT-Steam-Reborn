package com.miaokatze.gtsr.common.dimension.prosperity.altar;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

/** Bounded, shared discovery of real altars in previously unexplored overworld terrain. */
public final class SpacetimeAltarNavigationSearch {

    private static final Map<WorldServer, State> SEARCHES = new WeakHashMap<>();
    private static long ticks;

    private SpacetimeAltarNavigationSearch() {}

    public static void beginTick() {
        for (State state : SEARCHES.values()) state.search.beginTick();
    }

    public static void keepAlive(World world, double x, double z) {
        State state = SEARCHES.get(world);
        if (state != null && eligible(world)) state.search.keepAlive(region(x), region(z));
    }

    public static void request(World world, double x, double z) {
        if (!eligible(world)) return;
        WorldServer server = (WorldServer) world;
        State state = SEARCHES.get(server);
        if (state == null) {
            final long seed = world.getSeed();
            state = new State(
                new AltarSearchQueue((rx, rz) -> SpacetimeAltarWorldGenerator.candidateInRegion(seed, rx, rz)));
            SEARCHES.put(server, state);
        }
        state.search.request(region(x), region(z));
    }

    public static void tick() {
        boolean mayLoad = ++ticks % 20 == 0;
        for (Map.Entry<WorldServer, State> entry : SEARCHES.entrySet()) {
            WorldServer world = entry.getKey();
            try {
                entry.getValue().search.tick(new WorldBackend(world, entry.getValue()), mayLoad && eligible(world));
            } catch (RuntimeException failure) {
                com.miaokatze.gtsr.main.GTSteamReborn.LOG.warn("[GTSR] Beacon altar search candidate failed", failure);
            }
            // A server has only one overworld; retain a global budget even during world replacement.
            if (eligible(world)) mayLoad = false;
        }
    }

    private static boolean eligible(World world) {
        return world instanceof WorldServer && !world.isRemote
            && world.provider.dimensionId == 0
            && world.getWorldInfo()
                .isMapFeaturesEnabled()
            && world.getChunkProvider() instanceof ChunkProviderServer;
    }

    private static int region(double position) {
        return Math.floorDiv((int) Math.floor(position), 512);
    }

    private static final class WorldBackend implements AltarSearchQueue.Backend {

        private final WorldServer world;
        private final ChunkProviderServer provider;
        private final State state;

        WorldBackend(WorldServer world, State state) {
            this.world = world;
            this.state = state;
            provider = (ChunkProviderServer) world.getChunkProvider();
        }

        @Override
        public boolean hasTarget() {
            return SpacetimeAltarIndex.nearest(world, 0, 0) != null;
        }

        @Override
        public boolean loaded(int x, int z) {
            return provider.chunkExists(x, z);
        }

        @Override
        public void load(int x, int z) {
            // Explicit load works with GTNH's loadChunkOnProvideRequest=false. Natural 2x2 population
            // runs all registered generators, including the unchanged altar terrain/placement checks.
            Set<Chunk> before = new HashSet<>(provider.loadedChunks);
            try {
                provider.loadChunk(x, z);
            } finally {
                // Other registered generators may load beyond our four explicit chunks. Include
                // only chunks acquired during this server-thread operation in eventual cleanup.
                for (Chunk chunk : provider.loadedChunks) if (!before.contains(chunk))
                    state.acquired.add(new ChunkCoordIntPair(chunk.xPosition, chunk.zPosition));
            }
        }

        @Override
        public boolean populated(int x, int z) {
            return loaded(x, z) && provider.provideChunk(x, z).isTerrainPopulated;
        }

        @Override
        public boolean confirm(int x, int z) {
            String id = "altar:" + x + ":" + z;
            SpacetimeAltarIndex index = SpacetimeAltarIndex.get(world);
            SpacetimeAltarIndex.Entry entry = index.find(id);
            if (entry != null)
                return entry.valid && world.getBlock(entry.x, entry.y, entry.z) == SpacetimeAltarBlocks.core;
            // Recover only an actual matching saved core; never re-run generation on populated terrain.
            if (!loaded(x, z)) return false;
            int coreX = x * 16 + 7, coreZ = z * 16 + 7;
            for (int y = 63; y <= 238; y++) {
                if (world.getBlock(coreX, y, coreZ) != SpacetimeAltarBlocks.core) continue;
                TileEntity tile = world.getTileEntity(coreX, y, coreZ);
                if (tile instanceof TileSpacetimeAltar && id.equals(((TileSpacetimeAltar) tile).instanceId())) {
                    index.add(id, coreX, y, coreZ);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void release(int x, int z) {
            releaseIfUnwatched(x, z);
            for (ChunkCoordIntPair chunk : state.acquired) releaseIfUnwatched(chunk.chunkXPos, chunk.chunkZPos);
            state.acquired.clear();
        }

        private void releaseIfUnwatched(int x, int z) {
            if (loaded(x, z) && !world.getPlayerManager()
                .func_152621_a(x, z)) provider.unloadChunksIfNotNearSpawn(x, z);
        }
    }

    private static final class State {

        final AltarSearchQueue search;
        final Set<ChunkCoordIntPair> acquired = new HashSet<>();

        State(AltarSearchQueue search) {
            this.search = search;
        }
    }
}
