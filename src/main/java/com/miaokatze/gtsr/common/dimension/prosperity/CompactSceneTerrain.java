package com.miaokatze.gtsr.common.dimension.prosperity;

import java.util.LinkedHashMap;
import java.util.Map;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/** One shared, world-independent branch anchor for both geology and its compulsory compact scene. */
public final class CompactSceneTerrain {

    public static final int CELL_SIZE = 2048;
    public static final int HALF_X = 46, HALF_Z = 44, TRANSITION = 32;
    public static final int OUTER_X = HALF_X + TRANSITION, OUTER_Z = HALF_Z + TRANSITION;
    private static final long SALT = 0x434F4D5041435438L;
    private static final Map<String, Branch[]> CACHE = new LinkedHashMap<String, Branch[]>(256, .75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Branch[]> e) {
            return size() > 512;
        }
    };

    public static final class Branch {

        public final String prefab;
        public final int roster, centerX, centerZ, surfaceY;

        private Branch(int roster, int x, int z, int y) {
            this.roster = roster;
            prefab = roster == 0 ? "subsided_factory" : "fallen_foundry";
            centerX = x;
            centerZ = z;
            surfaceY = y;
        }

        public int originX() {
            return centerX - 38;
        }

        public int originZ() {
            return centerZ - 36;
        }

        public boolean contains(int x, int z) {
            return Math.abs((long) x - centerX) < OUTER_X && Math.abs((long) z - centerZ) < OUTER_Z;
        }
    }

    private CompactSceneTerrain() {}

    /** Returned array is copied: callers cannot mutate the cached branch set. */
    public static synchronized Branch[] cell(long seed, int gx, int gz) {
        String key = seed + ":" + gx + ":" + gz;
        Branch[] cached = CACHE.get(key);
        if (cached != null) return cached.clone();
        long h = GTSRWorldgenHash.cellSeed(seed, gx, gz, SALT);
        Branch accepted = null;
        if (Math.floorMod(h, 3) == 0) {
            int desiredRoster = (int) (h >>> 16 & 1L);
            for (int attempt = 0; attempt < 64; attempt++) {
                long ah = GTSRWorldgenHash.splitmix64(h + attempt * 0x9E3779B97F4A7C15L);
                int x = gx * CELL_SIZE + 128 + (int) Math.floorMod(ah >>> 8, CELL_SIZE - 256);
                int z = gz * CELL_SIZE + 128 + (int) Math.floorMod(ah >>> 32, CELL_SIZE - 256);
                int roster = ProsperityTerrainProfile.chainRosterIndexAt(seed, x >> 2, z >> 2);
                if (roster != desiredRoster) continue;
                int y = ProsperityTerrainProfile.originalHeightAt(seed, x, z);
                if (y < 24 || y > 200 || !admit(seed, x, z, y, roster)) continue;
                accepted = new Branch(roster, x, z, y);
                break;
            }
        }
        Branch[] result = accepted == null ? new Branch[0] : new Branch[] { accepted };
        CACHE.put(key, result);
        return result.clone();
    }

    private static boolean admit(long seed, int x, int z, int y, int roster) {
        // Every coarse identity cell in the complete branch belongs to its original parent biome.
        for (int zz = (z - OUTER_Z) >> 2; zz <= (z + OUTER_Z) >> 2; zz++)
            for (int xx = (x - OUTER_X) >> 2; xx <= (x + OUTER_X) >> 2; xx++)
                if (ProsperityTerrainProfile.chainRosterIndexAt(seed, xx, zz) != roster) return false;
        // Only gentle dry ground: never bridge a river, lake, cliff, or manufacture a raised massif.
        for (int dz = -OUTER_Z; dz <= OUTER_Z; dz += 8) for (int dx = -OUTER_X; dx <= OUTER_X; dx += 8) {
            int xx = x + dx, zz = z + dz;
            if (GTSRVoronoiRiverField.strengthAt(seed, xx, zz, roster) < 0.0D
                || GTSRVoronoiRiverField.lakeAt(seed, xx, zz) < GTSRVoronoiRiverField.SANZU_BIOME_SHORE_MAX + 0.15D
                || Math.abs(ProsperityTerrainProfile.originalHeightAt(seed, xx, zz) - y) > 12) return false;
        }
        // This tree reservation reads only the lake field; MegaTreeAnchors would recurse through heightAt.
        double[] lake = new double[5];
        for (int dz = -320; dz <= 320; dz += 128) for (int dx = -320; dx <= 320; dx += 128) {
            GTSRVoronoiRiverField.lakeCellCenterAt(seed, x + dx, z + dz, lake);
            if ((int) lake[2] == Integer.MIN_VALUE) continue;
            int ax = (int) Math.round(lake[0]), az = (int) Math.round(lake[1]);
            if (Math.abs((long) ax - x) <= OUTER_X + 176 && Math.abs((long) az - z) <= OUTER_Z + 176
                && GTSRVoronoiRiverField.lakeAt(seed, ax, az) < GTSRVoronoiRiverField.LAKE_ISLAND) return false;
        }
        // Verify every resulting column, including the original edge: sparse height probes alone miss steep ribs.
        int width = OUTER_X * 2 + 3, depth = OUTER_Z * 2 + 3;
        int[] previous = new int[width];
        for (int iz = 0; iz < depth; iz++) {
            int left = 0;
            for (int ix = 0; ix < width; ix++) {
                int dx = ix - OUTER_X - 1, dz = iz - OUTER_Z - 1;
                int raw = ProsperityTerrainProfile.originalHeightAt(seed, x + dx, z + dz);
                if (Math.abs(raw - y) > 12) return false;
                int height = blendedHeight(dx, dz, y, raw);
                if (ix > 0 && Math.abs(height - left) > 2 || iz > 0 && Math.abs(height - previous[ix]) > 2)
                    return false;
                left = height;
                previous[ix] = height;
            }
        }
        return true;
    }

    private static int blendedHeight(int dx, int dz, int surfaceY, int originalHeight) {
        double edge = Math.max(Math.max(0, Math.abs(dx) - HALF_X), Math.max(0, Math.abs(dz) - HALF_Z));
        double t = Math.min(1.0D, edge / TRANSITION);
        double blend = t * t * (3.0D - 2.0D * t);
        return (int) Math.round(surfaceY + (originalHeight - surfaceY) * blend);
    }

    public static int heightAt(long seed, int x, int z, int originalHeight) {
        for (Branch b : cell(seed, Math.floorDiv(x, CELL_SIZE), Math.floorDiv(z, CELL_SIZE))) {
            if (!b.contains(x, z)) continue;
            return blendedHeight(x - b.centerX, z - b.centerZ, b.surfaceY, originalHeight);
        }
        return originalHeight;
    }

    /** Reserve only chunks intersecting these local branches from natural tree/scatter decoration. */
    public static boolean reservedChunk(long seed, int cx, int cz) {
        for (Branch b : cell(seed, Math.floorDiv(cx << 4, CELL_SIZE), Math.floorDiv(cz << 4, CELL_SIZE)))
            if ((cx << 4) <= b.centerX + OUTER_X && (cx << 4) + 15 >= b.centerX - OUTER_X
                && (cz << 4) <= b.centerZ + OUTER_Z
                && (cz << 4) + 15 >= b.centerZ - OUTER_Z) return true;
        return false;
    }
}
