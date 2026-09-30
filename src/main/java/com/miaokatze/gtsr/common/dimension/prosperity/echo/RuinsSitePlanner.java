package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;

/** Pure, bounded anchor planning. Rejected wet/steep/city sites cannot leave a ghost suppression window. */
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

    public static synchronized List<RuinSite> near(long seed, int cx, int cz) {
        List<RuinSite> result = new ArrayList<>();
        for (int layer = 0; layer < 3; layer++) {
            int cell = CELLS[layer], gx = Math.floorDiv(cx, cell), gz = Math.floorDiv(cz, cell);
            // Anchors are inset by >= two chunks; footprints never cross the cell boundary.
            RuinSite s = candidate(seed, layer, gx, gz);
            if (s == null || !s.intersects(cx, cz)) continue;
            boolean collision = false;
            for (int higher = 0; higher < layer && !collision; higher++) {
                int hcell = CELLS[higher];
                int lowX = Math.floorDiv((s.x >> 4) - 10, hcell),
                    highX = Math.floorDiv(((s.x + s.width) >> 4) + 10, hcell);
                int lowZ = Math.floorDiv((s.z >> 4) - 10, hcell),
                    highZ = Math.floorDiv(((s.z + s.depth) >> 4) + 10, hcell);
                for (int x = lowX; x <= highX && !collision; x++) for (int z = lowZ; z <= highZ && !collision; z++) {
                    RuinSite other = candidate(seed, higher, x, z);
                    collision = other != null && s.overlaps(other, 24);
                }
            }
            if (!collision) result.add(s);
        }
        return result;
    }

    private static RuinSite candidate(long seed, int layer, int cx, int cz) {
        String key = seed + ":" + layer + ":" + cx + ":" + cz;
        if (CACHE.containsKey(key)) return CACHE.get(key);
        long hash = GTSRWorldgenHash.cellSeed(seed, cx, cz, 0x4543484F + layer * 703L);
        RuinSite result = null;
        // One potential site per cell; do not consume the shared populate Random.
        if (Math.floorMod(hash, layer == 0 ? 2 : 3) == 0) {
            int cell = CELLS[layer];
            int side = layer == 0 ? 6 : layer == 1 ? 3 : 2;
            int x = (cx * cell + 2 + (int) Math.floorMod(hash >>> 8, cell - side - 3)) * 16;
            int z = (cz * cell + 2 + (int) Math.floorMod(hash >>> 24, cell - side - 3)) * 16;
            int biome = CityPlanner.bandIndexAt(seed, x >> 4, z >> 4);
            if (biome < 0 || biome >= SMALL.length) {
                CACHE.put(key, null);
                return null;
            }
            int[] pool = layer == 1 ? MEDIUM[biome] : layer == 2 ? SMALL[biome] : null;
            int kind = layer == 0 ? (biome == 0 || biome == 2 ? 0 : 1)
                : pool[(int) Math.floorMod(hash >>> 40, pool.length)];
            int width = layer == 0 ? 96 : layer == 1 ? 48 : 32, depth = layer == 0 ? 96 : layer == 1 ? 48 : 16;
            // Wet-themed variants occupy dry banks, never overwrite water bodies.
            boolean ok = PlacementGate.dryFootprintStrict(seed, x - 3, z - 3, x + width + 2, z + depth + 2);
            int min = 255, max = 0;
            for (int xx = 0; xx <= width && ok; xx += 16) for (int zz = 0; zz <= depth && ok; zz += 16) {
                int h = ProsperityTerrainProfile.heightAt(seed, x + xx, z + zz);
                min = Math.min(min, h);
                max = Math.max(max, h);
                if (CityPlanner.citiesNear(seed, (x + xx) >> 4, (z + zz) >> 4).length > 0) ok = false;
            }
            if (ok && max - min <= (layer == 0 ? 18 : layer == 1 ? 14 : 10)) {
                int y = ProsperityTerrainProfile.heightAt(seed, x + width / 2, z + depth / 2) + 1;
                if (y > 30 && y < 200) result = new RuinSite(seed, kind, x, y, z, biome);
            }
        }
        CACHE.put(key, result);
        return result;
    }
}
