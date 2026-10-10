package com.miaokatze.gtsr.common.dimension.prosperity.altar;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

/** Discover deterministic planned targets without loading or generating any chunks. */
public final class SpacetimeAltarNavigationSearch {

    private static final Map<WorldServer, AltarSearchQueue> SEARCHES = new WeakHashMap<>();

    private SpacetimeAltarNavigationSearch() {}

    public static void beginTick() {
        for (AltarSearchQueue search : SEARCHES.values()) search.beginTick();
    }

    public static void keepAlive(World world, double x, double z) {
        AltarSearchQueue search = SEARCHES.get(world);
        if (search != null && eligible(world)) search.keepAlive(x, z);
    }

    public static void request(World world, double x, double z) {
        if (!eligible(world)) return;
        WorldServer server = (WorldServer) world;
        AltarSearchQueue search = SEARCHES.get(server);
        if (search == null) {
            final long seed = world.getSeed();
            search = new AltarSearchQueue((rx, rz) -> SpacetimeAltarWorldGenerator.candidateInRegion(seed, rx, rz));
            SEARCHES.put(server, search);
        }
        search.request(x, z);
    }

    /** Real cores and confirmed unpopulated candidates compete by the player's actual block distance. */
    public static int[] nearest(World world, double x, double z) {
        request(world, x, z);
        int[] planned = null;
        AltarSearchQueue search = SEARCHES.get(world);
        if (eligible(world) && search != null) planned = search.nearest(x, z, new WorldBackend((WorldServer) world));
        // Run after live candidate validation so a recovered or removed core is reflected immediately.
        SpacetimeAltarIndex.Entry actual = SpacetimeAltarIndex.nearest(world, x, z);
        if (actual == null) return planned;
        if (planned == null || distance(actual.x, actual.z, x, z) <= distance(planned[0], planned[1], x, z))
            return new int[] { actual.x, actual.z };
        return planned;
    }

    public static void tick() {
        boolean mayProbe = true;
        for (Map.Entry<WorldServer, AltarSearchQueue> entry : SEARCHES.entrySet()) {
            WorldServer world = entry.getKey();
            if (!eligible(world)) continue;
            try {
                entry.getValue()
                    .tick(new WorldBackend(world), mayProbe);
            } catch (RuntimeException failure) {
                com.miaokatze.gtsr.main.GTSteamReborn.LOG.warn("[GTSR] Beacon altar candidate probe deferred", failure);
            }
            // Keep a global budget even during overworld replacement.
            mayProbe = false;
        }
    }

    private static double distance(int tx, int tz, double x, double z) {
        double dx = tx - x, dz = tz - z;
        return dx * dx + dz * dz;
    }

    private static boolean eligible(World world) {
        return world instanceof WorldServer && !world.isRemote
            && world.provider.dimensionId == 0
            && world.getWorldInfo()
                .isMapFeaturesEnabled()
            && world.getChunkProvider() instanceof ChunkProviderServer;
    }

    private static final class WorldBackend implements AltarSearchQueue.Backend {

        private final WorldServer world;
        private final ChunkProviderServer provider;
        private final SpacetimeAltarIndex index;

        WorldBackend(WorldServer world) {
            this.world = world;
            provider = (ChunkProviderServer) world.getChunkProvider();
            index = SpacetimeAltarIndex.get(world);
        }

        @Override
        public int classify(int x, int z, boolean allowDisk) {
            String id = "altar:" + x + ":" + z;
            if (index.rejected(id)) return AltarSearchQueue.REJECTED;
            if (provider.chunkExists(x, z)) {
                Chunk chunk = provider.provideChunk(x, z);
                int coreX = x * 16 + 7, coreZ = z * 16 + 7;
                for (Object object : chunk.chunkTileEntityMap.values()) {
                    if (!(object instanceof TileSpacetimeAltar)) continue;
                    TileEntity tile = (TileEntity) object;
                    if (tile.xCoord != coreX || tile.zCoord != coreZ
                        || !id.equals(((TileSpacetimeAltar) tile).instanceId())
                        || chunk.getBlock(7, tile.yCoord, 7) != SpacetimeAltarBlocks.core) continue;
                    index.add(id, coreX, tile.yCoord, coreZ);
                    return AltarSearchQueue.ACTUAL;
                }
                if (!chunk.isTerrainPopulated) return AltarSearchQueue.PLANNED;
                index.reject(id);
                return AltarSearchQueue.REJECTED;
            }
            if (!allowDisk) return AltarSearchQueue.UNKNOWN;
            SavedAltarCandidateProbe.Result result = SavedAltarCandidateProbe.inspect(provider, x, z);
            if (result.status == AltarSearchQueue.ACTUAL) index.add(id, x * 16 + 7, result.y, z * 16 + 7);
            if (result.status == AltarSearchQueue.REJECTED) index.reject(id);
            return result.status;
        }
    }
}
