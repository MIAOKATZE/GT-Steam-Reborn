package com.miaokatze.gtsr.common.dimension.prosperity;

import java.util.LinkedHashMap;
import java.util.Map;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/** One shared, world-independent branch anchor for both geology and its compulsory compact scene. */
public final class CompactSceneTerrain {

    public static final int CELL_SIZE = 2048;
    // Both authored footprints fit with a natural apron; preserve the original surface anchor.
    public static final int HALF_X = 60, HALF_Z = 60, TRANSITION = 36;
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
            prefab = roster == 0 ? "subsided_factory" : roster == 1 ? "fallen_foundry" : "fiction_expansion_project";
            centerX = x;
            centerZ = z;
            surfaceY = y;
        }

        public int originX() {
            return centerX - (roster == 2 ? 78 : 60);
        }

        public int originZ() {
            return centerZ - (roster == 2 ? 78 : 60);
        }

        public boolean contains(int x, int z) {
            return Math.abs((long) x - centerX) < outer(roster) && Math.abs((long) z - centerZ) < outer(roster);
        }
    }

    private CompactSceneTerrain() {}

    private static final Map<String, CellSearch> IN_PROGRESS = new LinkedHashMap<String, CellSearch>() {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CellSearch> e) {
            return size() > 128;
        }
    };

    /** Returned array is copied: callers cannot mutate the cached branch set. */
    public static synchronized Branch[] cell(long seed, int gx, int gz) {
        CellSearch search = beginCell(seed, gx, gz);
        while (!search.done()) search.step();
        return search.result();
    }

    /** Same industrial placement, advanced one candidate or admission row at a time. */
    public static synchronized CellSearch beginCell(long seed, int gx, int gz) {
        String key = seed + ":" + gx + ":" + gz;
        CellSearch search = IN_PROGRESS.get(key);
        if (search == null) {
            search = new CellSearch(seed, gx, gz, key);
            if (!search.done()) IN_PROGRESS.put(key, search);
        }
        return search;
    }

    public static final class CellSearch {

        private final long seed;
        private long hash;
        private final int gx, gz;
        private int roster, phase;
        private final java.util.List<Branch> branches = new java.util.ArrayList<>();
        private final String key;
        private Branch[] result;
        private int attempt, stage, row, x, z, y;
        private int[] previous;
        private final double[] lake = new double[5];

        private CellSearch(long seed, int gx, int gz, String key) {
            this.seed = seed;
            this.gx = gx;
            this.gz = gz;
            this.key = key;
            hash = GTSRWorldgenHash.cellSeed(seed, gx, gz, SALT);
            roster = (int) (hash >>> 16 & 1L);
            result = CACHE.get(key);
        }

        public boolean done() {
            return result != null;
        }

        public Branch[] result() {
            return result == null ? null : result.clone();
        }

        private void finish(Branch branch) {
            if (branch != null) branches.add(branch);
            if (phase++ == 0) {
                hash = GTSRWorldgenHash.cellSeed(seed, gx, gz, SALT ^ 0x46494354494F4E32L);
                roster = 2;
                attempt = 0;
                reject();
                return;
            }
            result = branches.toArray(new Branch[branches.size()]);
            CACHE.put(key, result);
            if (IN_PROGRESS.get(key) == this) IN_PROGRESS.remove(key);
        }

        private void reject() {
            stage = 0;
            row = 0;
            previous = null;
        }

        public void step() {
            synchronized (CompactSceneTerrain.class) {
                if (done()) return;
                if (stage == 0) {
                    if (phase == 1 && Math.floorMod(hash, 2) != 0 || attempt == 384) {
                        finish(null);
                        return;
                    }
                    long h = GTSRWorldgenHash.splitmix64(hash + attempt++ * 0x9E3779B97F4A7C15L);
                    x = gx * CELL_SIZE + 128 + (int) Math.floorMod(h >>> 8, CELL_SIZE - 256);
                    z = gz * CELL_SIZE + 128 + (int) Math.floorMod(h >>> 32, CELL_SIZE - 256);
                    if (ProsperityTerrainProfile.chainRosterIndexAt(seed, x >> 2, z >> 2) != roster) return;
                    y = ProsperityTerrainProfile.originalHeightAt(seed, x, z);
                    if (y < 24 || y > (roster == 2 ? 140 : 200)) return;
                    for (Branch existing : branches)
                        if (Math.abs(x - existing.centerX) < outer(roster) + outer(existing.roster) + 24
                            && Math.abs(z - existing.centerZ) < outer(roster) + outer(existing.roster) + 24) return;
                    stage = 1;
                    row = (z - outer(roster)) >> 2;
                    return;
                }
                if (stage == 1) {
                    for (int xx = (x - outer(roster)) >> 2; xx <= (x + outer(roster)) >> 2; xx++)
                        if (ProsperityTerrainProfile.chainRosterIndexAt(seed, xx, row) != roster) {
                            reject();
                            return;
                        }
                    if (++row > (z + outer(roster)) >> 2) {
                        stage = 2;
                        row = -outer(roster);
                    }
                    return;
                }
                if (stage == 2) {
                    for (int dx = -outer(roster); dx <= outer(roster); dx += 8) {
                        int xx = x + dx, zz = z + row;
                        if (GTSRVoronoiRiverField.strengthAt(seed, xx, zz, roster) < 0.0D
                            || GTSRVoronoiRiverField.lakeAt(seed, xx, zz)
                                < GTSRVoronoiRiverField.SANZU_BIOME_SHORE_MAX + 0.15D
                            || Math.abs(ProsperityTerrainProfile.originalHeightAt(seed, xx, zz) - y) > 12) {
                            reject();
                            return;
                        }
                    }
                    row += 8;
                    if (row > outer(roster)) {
                        stage = 3;
                        row = -320;
                    }
                    return;
                }
                if (stage == 3) {
                    for (int dx = -320; dx <= 320; dx += 128) {
                        GTSRVoronoiRiverField.lakeCellCenterAt(seed, x + dx, z + row, lake);
                        if ((int) lake[2] == Integer.MIN_VALUE) continue;
                        int ax = (int) Math.round(lake[0]), az = (int) Math.round(lake[1]);
                        if (Math.abs((long) ax - x) <= outer(roster) + 176
                            && Math.abs((long) az - z) <= outer(roster) + 176
                            && GTSRVoronoiRiverField.lakeAt(seed, ax, az) < GTSRVoronoiRiverField.LAKE_ISLAND) {
                            reject();
                            return;
                        }
                    }
                    row += 128;
                    if (row > 320) {
                        stage = 4;
                        row = 0;
                        previous = new int[outer(roster) * 2 + 3];
                    }
                    return;
                }
                int left = 0;
                for (int ix = 0; ix < previous.length; ix++) {
                    int dx = ix - outer(roster) - 1, dz = row - outer(roster) - 1;
                    int raw = ProsperityTerrainProfile.originalHeightAt(seed, x + dx, z + dz);
                    if (Math.abs(raw - y) > 12) {
                        reject();
                        return;
                    }
                    int height = blendedHeight(seed, x + dx, z + dz, dx, dz, y, raw, roster);
                    if (ix > 0 && Math.abs(height - left) > 2 || row > 0 && Math.abs(height - previous[ix]) > 2) {
                        reject();
                        return;
                    }
                    left = height;
                    previous[ix] = height;
                }
                if (++row == outer(roster) * 2 + 3) finish(new Branch(roster, x, z, y));
            }
        }
    }

    private static int outer(int roster) {
        return (roster == 2 ? 78 : HALF_X) + TRANSITION;
    }

    private static int blendedHeight(long seed, int x, int z, int dx, int dz, int surfaceY, int originalHeight,
        int roster) {
        int half = roster == 2 ? 78 : HALF_X;
        double edge = Math.max(Math.max(0, Math.abs(dx) - half), Math.max(0, Math.abs(dz) - half));
        double t = Math.min(1.0D, edge / TRANSITION);
        double blend = t * t * (3.0D - 2.0D * t);
        // Retain the authored 120-square foundation and courtyard, but give its apron broad,
        // asymmetric knolls and shallow dry hollows. The compact support vanishes smoothly at
        // both boundaries, so the native terrain outside the branch is byte-for-byte unchanged.
        double relief = Math.sin(Math.PI * t) * Math.sin(Math.PI * t)
            * (Math.sin(dx / 19.0D + dz / 31.0D) * (roster == 1 ? 4.0D : 2.6D)
                + Math.cos(dz / 17.0D - dx / 37.0D) * 1.4D);
        // South-facing entry routes stay broad and quiet; forest relief grows along the flanks.
        int routeX = roster == 0 ? -42 : 0;
        double entry = dz > half && Math.abs(dx - routeX) < 18 ? Math.abs(dx - routeX) / 18.0D : 1.0D;
        double height = surfaceY + (originalHeight - surfaceY) * blend + relief * entry;
        if (roster == 2) {
            // The ring, foundations, explicit AIR and approaches keep their exact authored level.
            // Between them, low dunes and dry swales taper over a twelve-block clearance band.
            double clearance = CompactSceneDecor.fictionReliefWeight(dx + 78, dz + 78);
            double rim = Math.max(0D, Math.min(1D, (outer(roster) - Math.max(Math.abs(dx), Math.abs(dz))) / 16D));
            long phase = GTSRWorldgenHash
                .splitmix64(GTSRWorldgenHash.cellSeed(seed, x - dx, z - dz, SALT ^ 0x46494354494F4EL));
            double angle = (phase >>> 11) * 0x1.0p-53 * Math.PI * 2D;
            double dunes = 2.8D * Math.sin(dx / 21D + dz / 34D + angle) + 1.6D * Math.cos(dz / 18D - dx / 29D + angle);
            double route = Math.min(1D, Math.abs(dx) / 24D);
            route = route * route * (3D - 2D * route);
            double approach = Math.max(0D, Math.min(1D, (dz - 60D) / 18D));
            approach = approach * approach * (3D - 2D * approach);
            height += dunes * clearance * rim * (1D - approach * (1D - route));
        }
        if (roster < 2 && Math.abs(dx) < HALF_X
            && Math.abs(dz) < HALF_Z
            && CompactSceneDecor.landscapeColumn(roster, dx + 60, dz + 60, 0)) {
            // Broad, soil-covered factory knoll; foundry shoulders rise between the authored bays.
            double rim = Math.min(1D, Math.min(HALF_X - Math.abs(dx), HALF_Z - Math.abs(dz)) / 12D);
            double mound = roster == 0
                ? 7D * Math.max(0D, 1D - Math.pow((dx - 12D) / 43D, 2) - Math.pow((dz + 23D) / 37D, 2))
                : Math.min(2D, Math.max(0D, 1.4D + 1.1D * Math.sin(dx / 13D) + .8D * Math.cos(dz / 17D)));
            height += mound * rim;
        }
        return (int) Math.round(Math.max(originalHeight - 12, Math.min(originalHeight + 12, height)));
    }

    public static int heightAt(long seed, int x, int z, int originalHeight) {
        for (Branch b : cell(seed, Math.floorDiv(x, CELL_SIZE), Math.floorDiv(z, CELL_SIZE))) {
            if (!b.contains(x, z)) continue;
            return blendedHeight(seed, x, z, x - b.centerX, z - b.centerZ, b.surfaceY, originalHeight, b.roster);
        }
        return originalHeight;
    }

    /** A conservative column mask also protects explicitly authored AIR and the entry sightline. */
    public static boolean decorColumn(Branch b, int x, int z, int radius) {
        int dx = x - b.centerX, dz = z - b.centerZ;
        if (!b.contains(x - radius, z - radius) || !b.contains(x + radius, z + radius)) return false;
        if (Math.abs(dx) <= outer(b.roster) - TRANSITION + radius + 2
            && Math.abs(dz) <= outer(b.roster) - TRANSITION + radius + 2) return false;
        int routeX = b.roster == 0 ? -42 : 0;
        return !(dz > HALF_Z && Math.abs(dx - routeX) <= 18 + radius);
    }

    /** Reserve only chunks intersecting these local branches from natural tree/scatter decoration. */
    public static boolean reservedChunk(long seed, int cx, int cz) {
        for (Branch b : cell(seed, Math.floorDiv(cx << 4, CELL_SIZE), Math.floorDiv(cz << 4, CELL_SIZE)))
            if ((cx << 4) <= b.centerX + outer(b.roster) && (cx << 4) + 15 >= b.centerX - outer(b.roster)
                && (cz << 4) <= b.centerZ + outer(b.roster)
                && (cz << 4) + 15 >= b.centerZ - outer(b.roster)) return true;
        return false;
    }
}
