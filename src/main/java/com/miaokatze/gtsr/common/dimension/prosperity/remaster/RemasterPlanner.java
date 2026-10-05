package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.miaokatze.gtsr.common.dimension.prosperity.CompactSceneTerrain;

/** Compact industrial scenes share their compulsory terrain branch; the native tree owns its own placement. */
public final class RemasterPlanner {

    private RemasterPlanner() {}

    public static List<RemasterSite> cell(long seed, int layer, int gx, int gz) {
        if (layer != 0) return Collections.emptyList();
        List<RemasterSite> sites = new ArrayList<>();
        for (CompactSceneTerrain.Branch branch : CompactSceneTerrain.cell(seed, gx, gz)) sites.add(
            new RemasterSite(
                branch.prefab,
                0,
                seed,
                branch.originX(),
                branch.surfaceY,
                branch.originZ(),
                branch.roster == 2 ? "natural-prefab" : "compact-prefab"));
        sites.addAll(FutureStructurePlanner.cell(seed, gx, gz));
        return Collections.unmodifiableList(sites);
    }

    public static List<RemasterSite> near(long seed, int cx, int cz) {
        int bx = cx << 4, bz = cz << 4, size = CompactSceneTerrain.CELL_SIZE;
        int gx = Math.floorDiv(bx, size), gz = Math.floorDiv(bz, size);
        int lx = Math.floorMod(bx, size), lz = Math.floorMod(bz, size);
        // Global small/medium lattices can straddle a parent-cell edge; populate both halves.
        int minDx = lx < 192 ? -1 : 0, maxDx = lx + 15 >= size - 192 ? 1 : 0;
        int minDz = lz < 192 ? -1 : 0, maxDz = lz + 15 >= size - 192 ? 1 : 0;
        java.util.Map<String, RemasterSite> sites = new java.util.LinkedHashMap<>();
        for (int dx = minDx; dx <= maxDx; dx++)
            for (int dz = minDz; dz <= maxDz; dz++) for (RemasterSite site : cell(seed, 0, gx + dx, gz + dz))
                if (site.overlaps(bx, bz, bx + 15, bz + 15, 8)) sites.put(site.id(), site);
        return new ArrayList<>(sites.values());
    }

    /** Radius is measured in 2048-block parent cells (12 means 24576 blocks); never loads chunks. */
    public static RemasterSite nearest(long seed, String id, int bx, int bz, int radius) {
        return nearest(seed, id, bx, bz, radius, site -> true);
    }

    public static RemasterSite nearest(long seed, String id, int bx, int bz, int radius,
        java.util.function.Predicate<RemasterSite> eligible) {
        NearestSearch search = beginNearest(seed, id, bx, bz, radius, eligible);
        while (!search.done()) search.step();
        return search.result();
    }

    public static NearestSearch beginNearest(long seed, String id, int bx, int bz, int radius,
        java.util.function.Predicate<RemasterSite> eligible) {
        return new NearestSearch(seed, id, bx, bz, radius, eligible);
    }

    /** Preserves the original dx/dz/site order, circle and strict distance tie handling. */
    public static final class NearestSearch {

        private final long seed, radiusSquared;
        private final String id;
        private final int bx, bz, gx, gz, radius;
        private final java.util.function.Predicate<RemasterSite> eligible;
        private final List<Cell> pending = new ArrayList<>();
        private final boolean industrial;
        private int index, visitedCells, bestRank = Integer.MAX_VALUE;
        private Cell current;

        private static final class Cell {

            final int x, z, rank;
            final double lower;

            Cell(int x, int z, int rank, double lower) {
                this.x = x;
                this.z = z;
                this.rank = rank;
                this.lower = lower;
            }
        }

        private boolean done;
        private RemasterSite best;
        private double distance = Double.POSITIVE_INFINITY;
        private FutureStructurePlanner.CellSearch future;
        private CompactSceneTerrain.CellSearch compact;
        private List<RemasterSite> sites;

        private NearestSearch(long seed, String id, int bx, int bz, int radius,
            java.util.function.Predicate<RemasterSite> eligible) {
            this.seed = seed;
            this.id = id;
            this.bx = bx;
            this.bz = bz;
            this.eligible = eligible;
            this.radius = Math.max(0, Math.min(16, radius));
            long blocks = (long) this.radius * CompactSceneTerrain.CELL_SIZE;
            radiusSquared = blocks * blocks;
            gx = Math.floorDiv(bx, CompactSceneTerrain.CELL_SIZE);
            gz = Math.floorDiv(bz, CompactSceneTerrain.CELL_SIZE);
            industrial = "fallen_foundry".equals(id) || "subsided_factory".equals(id)
                || "fiction_expansion_project".equals(id);
            if (RemasterRollout.isActive(id) && !"forgotten_lake_court".equals(id)) {
                com.google.gson.JsonObject descriptor = RemasterCatalog.descriptor(id, 0);
                com.google.gson.JsonObject entry = RemasterCatalog.entrance(id, 0);
                int ex = entry != null && entry.has("x") ? entry.get("x")
                    .getAsInt()
                    : descriptor.getAsJsonArray("min")
                        .get(0)
                        .getAsInt() + 4;
                int ez = entry != null && entry.has("z") ? entry.get("z")
                    .getAsInt()
                    : descriptor.getAsJsonArray("min")
                        .get(2)
                        .getAsInt() + 4;
                int offsetX = ex - ("fiction_expansion_project".equals(id) ? 78
                    : industrial ? 60
                        : descriptor.getAsJsonArray("nominal")
                            .get(0)
                            .getAsInt() / 2);
                int offsetZ = ez - ("fiction_expansion_project".equals(id) ? 78
                    : industrial ? 60
                        : descriptor.getAsJsonArray("nominal")
                            .get(2)
                            .getAsInt() / 2);
                int rank = 0;
                int centerMin = industrial ? 128 : 0, centerMax = industrial ? 1919 : 2047;
                for (int dx = -this.radius; dx <= this.radius; dx++)
                    for (int dz = -this.radius; dz <= this.radius; dz++, rank++) {
                        long x = (long) (gx + dx) * CompactSceneTerrain.CELL_SIZE;
                        long z = (long) (gz + dz) * CompactSceneTerrain.CELL_SIZE;
                        double px = Math
                            .max(0L, Math.max(x + centerMin + offsetX - bx, bx - (x + centerMax + offsetX)));
                        double pz = Math
                            .max(0L, Math.max(z + centerMin + offsetZ - bz, bz - (z + centerMax + offsetZ)));
                        double lower = px * px + pz * pz;
                        if (lower <= radiusSquared) pending.add(new Cell(gx + dx, gz + dz, rank, lower));
                    }
                pending.sort((a, b) -> {
                    int c = Double.compare(a.lower, b.lower);
                    return c == 0 ? Integer.compare(a.rank, b.rank) : c;
                });
            }
            done = !RemasterRollout.isActive(id) || "forgotten_lake_court".equals(id);
        }

        public boolean done() {
            return done;
        }

        public RemasterSite result() {
            return best;
        }

        public int visitedCells() {
            return visitedCells;
        }

        public void step() {
            if (done) return;
            if (current == null) {
                if (index == pending.size() || pending.get(index).lower > distance) {
                    done = true;
                    return;
                }
                current = pending.get(index++);
                visitedCells++;
                sites = new ArrayList<>();
                if (industrial) {
                    compact = CompactSceneTerrain.beginCell(seed, current.x, current.z);
                } else {
                    future = FutureStructurePlanner.beginCell(seed, current.x, current.z, id);
                }
                return;
            }
            if (compact != null) {
                if (!compact.done()) {
                    compact.step();
                    return;
                }
                for (CompactSceneTerrain.Branch branch : compact.result()) if (id.equals(branch.prefab)) sites.add(
                    new RemasterSite(
                        branch.prefab,
                        0,
                        seed,
                        branch.originX(),
                        branch.surfaceY,
                        branch.originZ(),
                        branch.roster == 2 ? "natural-prefab" : "compact-prefab"));
                compact = null;
            }
            if (future != null) {
                if (!future.done()) {
                    future.step();
                    return;
                }
                sites.addAll(future.result());
            }
            for (RemasterSite site : sites) {
                if (!site.prefab.equals(id) || !eligible.test(site)) continue;
                double x = site.entryX() - (double) bx, z = site.entryZ() - (double) bz, d = x * x + z * z;
                // Equal-distance cells use the old dx/dz rank; within a cell slot order remains unchanged.
                if (d <= radiusSquared && (d < distance || d == distance && current.rank < bestRank)) {
                    best = site;
                    distance = d;
                    bestRank = current.rank;
                }
            }
            current = null;
            future = null;
            sites = null;
        }
    }
}
