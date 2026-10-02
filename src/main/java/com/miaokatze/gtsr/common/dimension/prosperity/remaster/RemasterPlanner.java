package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/** Pure revision-seven planning. Every footprint is contained in its parent cell with a dry margin. */
public final class RemasterPlanner {

    private static final int[] CELLS = { 128, 32, 16 };
    private static final Map<String, List<String>> POOLS = new LinkedHashMap<>();
    private static final Map<String, List<RemasterSite>> CACHE = new LinkedHashMap<String, List<RemasterSite>>(
        256,
        .75f,
        true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<RemasterSite>> e) {
            return size() > 512;
        }
    };
    static {
        for (JsonElement e : RemasterCatalog.config()
            .getAsJsonArray("structures")) {
            JsonObject s = e.getAsJsonObject();
            POOLS.computeIfAbsent(
                s.get("category")
                    .getAsString(),
                k -> new ArrayList<>())
                .add(
                    s.get("id")
                        .getAsString());
        }
    }

    private RemasterPlanner() {}

    private static long hash(long seed, int layer, int x, int z) {
        return GTSRWorldgenHash.cellSeed(seed, x, z, 0x52374E415455524CL + layer * 173L);
    }

    private static String select(String category, long h) {
        List<String> pool = POOLS.get(category);
        return pool.get((int) Math.floorMod(h, pool.size()));
    }

    private static int value(JsonObject d, String field, int axis) {
        return d.getAsJsonArray(field)
            .get(axis)
            .getAsInt();
    }

    private static RemasterSite anchor(String id, long seed, long h, int minX, int minZ) {
        int variant = "fiction_expansion_project".equals(id) ? 0
            : (int) Math.floorMod(h >>> 12, RemasterCatalog.variants(id));
        JsonObject d = RemasterCatalog.descriptor(id, variant);
        int x = minX - value(d, "min", 0), z = minZ - value(d, "min", 2);
        int y = ProsperityTerrainProfile.heightAt(seed, minX, minZ) + 1;
        y = Math.max(
            1 - d.get("yMin")
                .getAsInt(),
            Math.min(
                254 - d.get("yMax")
                    .getAsInt(),
                y));
        if ("subsided_factory".equals(id)) y = Math.max(y, 180);
        return new RemasterSite(id, variant, seed, x, y, z);
    }

    /** Parent cells include roads and all 28 real 40/60-block authored plots, never the old full-city shell. */
    public static synchronized List<RemasterSite> cell(long seed, int layer, int gx, int gz) {
        String key = seed + ":" + layer + ":" + gx + ":" + gz;
        List<RemasterSite> cached = CACHE.get(key);
        if (cached != null) return cached;
        List<RemasterSite> out = new ArrayList<>();
        long h = hash(seed, layer, gx, gz);
        int cell = CELLS[layer] * 16;
        if (Math.floorMod(h, layer == 0 ? 3 : layer == 1 ? 2 : 3) == 0) {
            int bx = gx * cell + 64, bz = gz * cell + 64;
            if (layer == 0 && Math.floorMod(h >>> 8, 3) == 0) {
                int x = (bx + (int) Math.floorMod(h >>> 16, cell - 896)) & ~15;
                int z = (bz + (int) Math.floorMod(h >>> 32, cell - 512)) & ~15;
                int y = Math.max(60, Math.min(150, ProsperityTerrainProfile.heightAt(seed, x + 336, z + 192) + 1));
                RemasterSite city = new RemasterSite("prosperity_city_full", 0, seed, x, y, z, "city-grid");
                out.add(city);
                List<String> plots = POOLS.get("city_variant");
                // A cyclic offset changes districts while guaranteeing the entire roster in every city.
                int offset = (int) Math.floorMod(h >>> 20, plots.size());
                for (int i = 0; i < plots.size(); i++) {
                    String id = plots.get((i + offset) % plots.size());
                    long ph = GTSRWorldgenHash.splitmix64(h + i * 0x9E3779B97F4A7C15L);
                    RemasterSite p = anchor(id, seed, ph, x + (i % 7) * 96 + 20, z + (i / 7) * 96 + 20);
                    out.add(new RemasterSite(p.prefab, p.variant, seed, p.x, y, p.z, "city-plot"));
                }
            } else {
                String id;
                if (layer == 0) {
                    int pick = (int) Math.floorMod(h >>> 8, 5);
                    id = pick < 2 ? (pick == 0 ? "fallen_foundry" : "subsided_factory") : select("colossus", h >>> 20);
                } else if (layer == 1) {
                    int pick = (int) Math.floorMod(h >>> 8, 4);
                    if (pick == 0) {
                        String[] medium = { "boiler_shrine", "weaving_mill", "sniper_watch", "mirror_barracks",
                            "resonant_station", "cold_altar", "fungus_cellar" };
                        id = medium[(int) Math.floorMod(h >>> 24, medium.length)];
                    } else id = select(pick == 1 ? "outpost" : pick == 2 ? "colossus" : "special_cache", h >>> 24);
                } else {
                    int pick = (int) Math.floorMod(h >>> 8, 4);
                    id = pick < 2 ? POOLS.get("echo")
                        .get(7 + (int) Math.floorMod(h >>> 24, 20)) : select(pick == 2 ? "machine" : "ruin", h >>> 24);
                }
                int v = "fiction_expansion_project".equals(id) ? 0
                    : (int) Math.floorMod(h >>> 12, RemasterCatalog.variants(id));
                JsonObject d = RemasterCatalog.descriptor(id, v);
                int width = value(d, "max", 0) - value(d, "min", 0) + 1;
                int depth = value(d, "max", 2) - value(d, "min", 2) + 1;
                int terrain = "subsided_factory".equals(id) ? 192 : "fallen_foundry".equals(id) ? 96 : 24;
                int inset = 32 + terrain;
                if (width + inset * 2 < cell && depth + inset * 2 < cell) {
                    int x = (gx * cell + inset + (int) Math.floorMod(h >>> 16, cell - width - inset * 2)) & ~15;
                    int z = (gz * cell + inset + (int) Math.floorMod(h >>> 32, cell - depth - inset * 2)) & ~15;
                    out.add(anchor(id, seed, h, x, z));
                }
            }
        }
        boolean treeConflict = false;
        for (RemasterSite s : out) if (treeConflict(s)) {
            treeConflict = true;
            break;
        }
        if (treeConflict) out.clear();
        List<RemasterSite> immutable = Collections.unmodifiableList(out);
        CACHE.put(key, immutable);
        return immutable;
    }

    private static boolean treeConflict(RemasterSite s) {
        double[] anchor = new double[5];
        for (int x = s.minX() - 160; x <= s.maxX() + 288; x += 128)
            for (int z = s.minZ() - 160; z <= s.maxZ() + 288; z += 128)
                if (com.miaokatze.gtsr.common.dimension.prosperity.ruins.MegaTreeAnchors.anchorAt(s.seed, x, z, anchor)
                    && s.overlaps(
                        (int) anchor[0] - 160,
                        (int) anchor[1] - 160,
                        (int) anchor[0] + 160,
                        (int) anchor[1] + 160,
                        16))
                    return true;
        return false;
    }

    private static boolean accepted(long seed, int layer, RemasterSite site) {
        for (int higher = 0; higher < layer; higher++) {
            int size = CELLS[higher];
            for (int x = Math.floorDiv((site.minX() - 32) >> 4, size); x
                <= Math.floorDiv((site.maxX() + 32) >> 4, size); x++)
                for (int z = Math.floorDiv((site.minZ() - 32) >> 4, size); z
                    <= Math.floorDiv((site.maxZ() + 32) >> 4, size); z++)
                    for (RemasterSite parent : cell(seed, higher, x, z))
                        if (parent.overlaps(site.minX(), site.minZ(), site.maxX(), site.maxZ(), 32)) return false;
        }
        return true;
    }

    public static synchronized List<RemasterSite> near(long seed, int cx, int cz) {
        List<RemasterSite> out = new ArrayList<>();
        for (int layer = 0; layer < CELLS.length; layer++) {
            for (RemasterSite s : cell(seed, layer, Math.floorDiv(cx, CELLS[layer]), Math.floorDiv(cz, CELLS[layer])))
                if (s.intersects(cx, cz) && accepted(seed, layer, s)) out.add(s);
        }
        return out;
    }

    /** Bounded read-only locate. No chunk request and no registration. */
    public static RemasterSite nearest(long seed, String id, int bx, int bz, int radius) {
        return nearest(seed, id, bx, bz, radius, s -> true);
    }

    public static RemasterSite nearest(long seed, String id, int bx, int bz, int radius,
        java.util.function.Predicate<RemasterSite> eligible) {
        radius = Math.max(0, Math.min(16, radius));
        RemasterSite best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (int layer = 0; layer < CELLS.length; layer++) {
            int gx = Math.floorDiv(bx >> 4, CELLS[layer]), gz = Math.floorDiv(bz >> 4, CELLS[layer]);
            for (int x = -radius; x <= radius; x++)
                for (int z = -radius; z <= radius; z++) for (RemasterSite s : cell(seed, layer, gx + x, gz + z)) {
                    if (!s.prefab.equals(id) || !accepted(seed, layer, s) || !eligible.test(s)) continue;
                    double dx = s.entryX() - (double) bx, dz = s.entryZ() - (double) bz, d = dx * dx + dz * dz;
                    if (d < distance) {
                        best = s;
                        distance = d;
                    }
                }
        }
        return best;
    }
}
