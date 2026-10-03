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
        return Collections.unmodifiableList(sites);
    }

    public static List<RemasterSite> near(long seed, int cx, int cz) {
        List<RemasterSite> sites = new ArrayList<>();
        for (RemasterSite site : cell(
            seed,
            0,
            Math.floorDiv(cx << 4, CompactSceneTerrain.CELL_SIZE),
            Math.floorDiv(cz << 4, CompactSceneTerrain.CELL_SIZE))) if (site.intersects(cx, cz)) sites.add(site);
        return sites;
    }

    /** Radius is measured in 2048-block parent cells (12 means 24576 blocks); never loads chunks. */
    public static RemasterSite nearest(long seed, String id, int bx, int bz, int radius) {
        return nearest(seed, id, bx, bz, radius, site -> true);
    }

    public static RemasterSite nearest(long seed, String id, int bx, int bz, int radius,
        java.util.function.Predicate<RemasterSite> eligible) {
        if (!RemasterRollout.isActive(id) || "forgotten_lake_court".equals(id)) return null;
        radius = Math.max(0, Math.min(16, radius));
        long radiusBlocks = (long) radius * CompactSceneTerrain.CELL_SIZE;
        int gx = Math.floorDiv(bx, CompactSceneTerrain.CELL_SIZE),
            gz = Math.floorDiv(bz, CompactSceneTerrain.CELL_SIZE);
        RemasterSite best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) for (RemasterSite site : cell(seed, 0, gx + dx, gz + dz)) {
                if (!site.prefab.equals(id) || !eligible.test(site)) continue;
                double x = site.entryX() - (double) bx, z = site.entryZ() - (double) bz, d = x * x + z * z;
                if (d <= radiusBlocks * radiusBlocks && d < distance) {
                    best = site;
                    distance = d;
                }
            }
        return best;
    }
}
