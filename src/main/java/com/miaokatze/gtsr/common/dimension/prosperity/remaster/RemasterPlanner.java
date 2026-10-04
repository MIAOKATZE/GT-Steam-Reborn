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
        for (CompactSceneTerrain.Branch branch : CompactSceneTerrain.cell(seed, gx, gz))
            sites.add(new RemasterSite(branch.prefab, 0, seed, branch.originX(), branch.surfaceY, branch.originZ()));
        sites.addAll(FutureStructurePlanner.cell(seed, gx, gz));
        return Collections.unmodifiableList(sites);
    }

    public static List<RemasterSite> near(long seed, int cx, int cz) {
        List<RemasterSite> sites = new ArrayList<>();
        for (RemasterSite site : cell(
            seed,
            0,
            Math.floorDiv(cx << 4, CompactSceneTerrain.CELL_SIZE),
            Math.floorDiv(cz << 4, CompactSceneTerrain.CELL_SIZE)))
            if (site.overlaps(cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15, 8)) sites.add(site);
        return sites;
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
        private int dx, dz;
        private boolean done;
        private RemasterSite best;
        private double distance = Double.POSITIVE_INFINITY;
        private FutureStructurePlanner.CellSearch future;
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
            dx = dz = -this.radius;
            done = !RemasterRollout.isActive(id) || "forgotten_lake_court".equals(id);
        }

        public boolean done() {
            return done;
        }

        public RemasterSite result() {
            return best;
        }

        public void step() {
            if (done) return;
            if (future == null) {
                sites = new ArrayList<>();
                for (CompactSceneTerrain.Branch branch : CompactSceneTerrain.cell(seed, gx + dx, gz + dz)) sites
                    .add(new RemasterSite(branch.prefab, 0, seed, branch.originX(), branch.surfaceY, branch.originZ()));
                future = FutureStructurePlanner.beginCell(seed, gx + dx, gz + dz);
                return;
            }
            if (!future.done()) {
                future.step();
                return;
            }
            sites.addAll(future.result());
            for (RemasterSite site : sites) {
                if (!site.prefab.equals(id) || !eligible.test(site)) continue;
                double x = site.entryX() - (double) bx, z = site.entryZ() - (double) bz, d = x * x + z * z;
                if (d <= radiusSquared && d < distance) {
                    best = site;
                    distance = d;
                }
            }
            future = null;
            sites = null;
            if (++dz > radius) {
                dz = -radius;
                if (++dx > radius) done = true;
            }
        }
    }
}
