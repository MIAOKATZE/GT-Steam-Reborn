package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;

/** Bounded pure planning, with versioned salt and four deterministic fallback anchors per cell. */
public final class RuinsSitePlanner {

    private static final int[] CELLS = { 96, 24, 8 };
    private static final int[][] MEDIUM = { { 4, 5, 6 }, { 3, 6 }, { 2, 4 }, { 2, 3, 6 } };
    private static final int[][] SMALL = { { 7, 9, 12, 15, 17, 20, 21, 26 }, { 9, 14, 17, 18, 20, 22, 25, 26 },
        { 7, 11, 13, 14, 15, 16, 21, 23, 24, 26 }, { 8, 10, 16, 19, 22, 24, 25 } };
    private static final Map<String, RuinSite> CACHE = new LinkedHashMap<String, RuinSite>(256, .75F, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, RuinSite> e) {
            return size() > 2048;
        }
    };

    private RuinsSitePlanner() {}

    public static List<RuinSite> legacyNear(long seed, int cx, int cz) {
        return RuinsLegacySitePlanner.near(seed, cx, cz);
    }

    public static synchronized List<RuinSite> near(long seed, int cx, int cz) {
        List<RuinSite> result = new ArrayList<>();
        for (int layer = 0; layer < 3; layer++) {
            RuinSite s = candidate(seed, layer, Math.floorDiv(cx, CELLS[layer]), Math.floorDiv(cz, CELLS[layer]));
            if (s != null && s.intersects(cx, cz) && accepted(seed, layer, s)) result.add(s);
        }
        return result;
    }

    private static boolean accepted(long seed, int layer, RuinSite s) {
        for (int higher = 0; higher < layer; higher++) {
            int cell = CELLS[higher];
            int x0 = Math.floorDiv((s.x - 24) >> 4, cell), x1 = Math.floorDiv((s.x + s.width + 24) >> 4, cell);
            int z0 = Math.floorDiv((s.z - 24) >> 4, cell), z1 = Math.floorDiv((s.z + s.depth + 24) >> 4, cell);
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                RuinSite other = candidate(seed, higher, x, z);
                if (other != null && s.overlaps(other, 24)) return false;
            }
        }
        // Insets of two chunks make same-layer neighbors separated by at least 64 blocks.
        return true;
    }

    /** Nearest accepted natural anchor in a fully bounded square; maxCandidates counts cells. */
    public static synchronized RuinSite findNearest(long seed, int kind, int blockX, int blockZ, int maxRadiusCells,
        int maxCandidates) {
        return findNearest(seed, kind, blockX, blockZ, maxRadiusCells, maxCandidates, site -> true);
    }

    public static synchronized RuinSite findNearest(long seed, int kind, int blockX, int blockZ, int maxRadiusCells,
        int maxCandidates, java.util.function.Predicate<RuinSite> eligible) {
        if (kind < 0 || kind >= RuinSite.NAMES.length || maxCandidates < 1) return null;
        int layer = kind < 2 ? 0 : kind < 7 ? 1 : 2, cell = CELLS[layer];
        int gx = Math.floorDiv(blockX >> 4, cell), gz = Math.floorDiv(blockZ >> 4, cell);
        int radius = Math.max(0, Math.min(32, maxRadiusCells));
        RuinSite best = null;
        double distance = Double.POSITIVE_INFINITY;
        int count = 0;
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                if (++count > maxCandidates) return best;
                RuinSite s = candidate(seed, layer, gx + dx, gz + dz);
                if (s == null || s.kind != kind || !accepted(seed, layer, s) || !eligible.test(s)) continue;
                double x = (double) s.entryX() - blockX, z = (double) s.entryZ() - blockZ, d = x * x + z * z;
                if (d < distance) {
                    distance = d;
                    best = s;
                }
            }
            // Unvisited cells lie outside this ring. Their rectangles bound every possible entry.
            long span = cell * 16L;
            double bound = Math.min(
                Math.min(blockX - (gx - ring) * span, (gx + ring + 1) * span - blockX),
                Math.min(blockZ - (gz - ring) * span, (gz + ring + 1) * span - blockZ));
            if (best != null && distance <= bound * bound) break;
        }
        return best;
    }

    private static RuinSite candidate(long seed, int layer, int cx, int cz) {
        String key = seed + ":" + layer + ":" + cx + ":" + cz;
        if (CACHE.containsKey(key)) return CACHE.get(key);
        long hash = GTSRWorldgenHash.cellSeed(seed, cx, cz, 0x5033384543484FL + layer * 703L);
        RuinSite result = null;
        if (Math.floorMod(hash, layer == 0 ? 2 : 3) == 0) {
            int width = RuinSite.widthFor(layer == 0 ? 0 : layer == 1 ? 2 : 7, 2),
                depth = RuinSite.depthFor(layer == 0 ? 0 : layer == 1 ? 2 : 7, 2);
            int cell = CELLS[layer], wc = (width + 15) >> 4, dc = (depth + 15) >> 4;
            for (int attempt = 0; attempt < 4; attempt++) {
                long h = GTSRWorldgenHash.splitmix64(hash + attempt * 0x9E3779B97F4A7C15L);
                int x = (cx * cell + 2 + (int) Math.floorMod(h >>> 8, cell - wc - 3)) * 16;
                int z = (cz * cell + 2 + (int) Math.floorMod(h >>> 24, cell - dc - 3)) * 16;
                int biome = CityPlanner.bandIndexAt(seed, x >> 4, z >> 4);
                if (biome < 0 || biome >= SMALL.length) continue;
                int[] pool = layer == 1 ? MEDIUM[biome] : SMALL[biome];
                int kind = layer == 0 ? (biome == 0 || biome == 2 ? 0 : 1)
                    : pool[(int) Math.floorMod(hash >>> 40, pool.length)];
                // Cheap coarse rejection precedes perimeter and interior wet sampling.
                int min = 255, max = 0;
                boolean ok = true;
                for (int xx = 0; xx < width && ok; xx += 32) for (int zz = 0; zz < depth && ok; zz += 32) {
                    int height = ProsperityTerrainProfile.heightAt(seed, x + xx, z + zz);
                    min = Math.min(min, height);
                    max = Math.max(max, height);
                    if (!PlacementGate.dryPointAt(seed, x + xx, z + zz)) ok = false;
                }
                if (!ok || max - min > (layer == 0 ? 42 : layer == 1 ? 30 : 18)) continue;
                if (!PlacementGate.dryFootprintStrict(seed, x - 2, z - 2, x + width + 1, z + depth + 1)) continue;
                // City exclusion samples every chunk across the actual new footprint, including its margin.
                for (int xx = -16; xx < width + 16 && ok; xx += 16) for (int zz = -16; zz < depth + 16 && ok; zz += 16)
                    if (CityPlanner.citiesNear(seed, (x + xx) >> 4, (z + zz) >> 4).length > 0) ok = false;
                if (!ok) continue;
                // Coarse gates never authorize an unsampled wet column in a critical room or route.
                for (int xx = 0; xx < width && ok; xx++) for (int zz = 0; zz < depth && ok; zz++)
                    if (!PlacementGate.dryPointAt(seed, x + xx, z + zz)) ok = false;
                if (!ok) continue;
                int y = ProsperityTerrainProfile.heightAt(seed, x + width / 2, z + depth / 2) + 1;
                if (y <= 35 || y >= 180) continue;
                RuinSite proposed = new RuinSite(seed, kind, x, y, z, biome);
                if ((kind == 8 || kind == 10 || kind == 19 || kind == 24) && shoreDistance(proposed) > 40) continue;
                // Hillside themes prefer relief; shore themes choose the closest dry bank of fallback anchors.
                if (result == null || terrainScore(proposed) > terrainScore(result)) result = proposed;
                if (kind != 8 && kind != 10 && kind != 19 && kind != 24 && kind != 14 && kind != 4) break;
            }
        }
        CACHE.put(key, result);
        return result;
    }

    private static int terrainScore(RuinSite s) {
        if (s.kind == 14 || s.kind == 4) {
            int a = ProsperityTerrainProfile.heightAt(s.seed, s.x + 3, s.z + 3);
            int b = ProsperityTerrainProfile.heightAt(s.seed, s.x + s.width - 4, s.z + s.depth - 4);
            return Math.abs(a - b);
        }
        return 48 - shoreDistance(s);
    }

    /** Actual placed-water predicate, not the wider no-structure shore protection band. */
    public static boolean waterAt(long seed, int x, int z) {
        return com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins.naturalWaterTopAt(seed, x, z)
            >= 0;
    }

    public static int shoreDistance(RuinSite s) {
        for (int d = 1; d <= 40; d++)
            for (int side = 0; side < 4; side++) for (int p = 0; p < (side < 2 ? s.depth : s.width); p += 4) {
                int x = side < 2 ? s.x + (side == 0 ? -d : s.width - 1 + d) : s.x + p;
                int z = side >= 2 ? s.z + (side == 2 ? -d : s.depth - 1 + d) : s.z + p;
                if (waterAt(s.seed, x, z)) return d;
            }
        return 41;
    }
}
