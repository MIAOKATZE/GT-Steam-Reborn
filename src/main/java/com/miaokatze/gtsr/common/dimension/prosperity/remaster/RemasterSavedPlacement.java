package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;

/** Save admission uses the planner's identity rules without terrain viability or chunk access. */
final class RemasterSavedPlacement {

    private static final int[] CELLS = { 2048, 512, 256 };
    private static final Map<String, List<String>> POOLS = new LinkedHashMap<>();
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

    private RemasterSavedPlacement() {}

    private static long hash(long seed, int layer, int gx, int gz) {
        return GTSRWorldgenHash.cellSeed(seed, gx, gz, 0x52374E415455524CL + layer * 173L);
    }

    private static int bound(JsonObject d, String side, int axis) {
        return d.getAsJsonArray(side)
            .get(axis)
            .getAsInt();
    }

    private static String select(String pool, long h) {
        List<String> ids = POOLS.get(pool);
        return ids.get((int) Math.floorMod(h, ids.size()));
    }

    private static String selected(int layer, long h) {
        if (layer == 0) {
            int pick = (int) Math.floorMod(h >>> 8, 5);
            return pick < 2 ? (pick == 0 ? "fallen_foundry" : "subsided_factory") : select("colossus", h >>> 20);
        }
        if (layer == 1) {
            int pick = (int) Math.floorMod(h >>> 8, 4);
            if (pick == 0) {
                String[] medium = { "boiler_shrine", "weaving_mill", "sniper_watch", "mirror_barracks",
                    "resonant_station", "cold_altar", "fungus_cellar" };
                return medium[(int) Math.floorMod(h >>> 24, medium.length)];
            }
            return select(pick == 1 ? "outpost" : pick == 2 ? "colossus" : "special_cache", h >>> 24);
        }
        int pick = (int) Math.floorMod(h >>> 8, 4);
        return pick < 2 ? POOLS.get("echo")
            .get(7 + (int) Math.floorMod(h >>> 24, 20)) : select(pick == 2 ? "machine" : "ruin", h >>> 24);
    }

    private static int variant(String id, long h) {
        return "fiction_expansion_project".equals(id) ? 0 : (int) Math.floorMod(h >>> 12, RemasterCatalog.variants(id));
    }

    private static int layerMask(String id) {
        int mask = "fallen_foundry".equals(id) || "subsided_factory".equals(id) ? 1 : 0;
        if (POOLS.get("colossus")
            .contains(id)) mask |= 3;
        if (POOLS.get("outpost")
            .contains(id)
            || POOLS.get("special_cache")
                .contains(id))
            mask |= 2;
        for (String medium : new String[] { "boiler_shrine", "weaving_mill", "sniper_watch", "mirror_barracks",
            "resonant_station", "cold_altar", "fungus_cellar" }) if (medium.equals(id)) mask |= 2;
        if (POOLS.get("echo")
            .subList(7, 27)
            .contains(id)
            || POOLS.get("machine")
                .contains(id)
            || POOLS.get("ruin")
                .contains(id))
            mask |= 4;
        return mask;
    }

    private static String key(RemasterSite s, int layer, int gx, int gz) {
        return s.seed + ":" + layer + ":" + gx + ":" + gz;
    }

    static boolean city(RemasterSite s) {
        return "city-grid".equals(s.layout) || "city-plot".equals(s.layout);
    }

    /** The group includes seed and layer, so neither another seed nor another layer is frozen. */
    static String group(RemasterSite s) {
        if ("tree-overlay".equals(s.layout)) return null;
        if (city(s)) return cityGroup(s, false);
        if (!"prefab".equals(s.layout)) return null;
        JsonObject d = RemasterCatalog.descriptor(s.prefab, s.variant);
        int mx = s.x + bound(d, "min", 0), mz = s.z + bound(d, "min", 2);
        int layers = layerMask(s.prefab);
        for (int layer = 0; layer < CELLS.length; layer++) {
            if ((layers & 1 << layer) == 0) continue;
            int cell = CELLS[layer], gx = Math.floorDiv(mx, cell), gz = Math.floorDiv(mz, cell);
            long h = hash(s.seed, layer, gx, gz);
            if (Math.floorMod(h, layer == 1 ? 2 : 3) != 0 || layer == 0 && Math.floorMod(h >>> 8, 3) == 0
                || !s.prefab.equals(selected(layer, h))
                || s.variant != variant(s.prefab, h)) continue;
            int width = bound(d, "max", 0) - bound(d, "min", 0) + 1;
            int depth = bound(d, "max", 2) - bound(d, "min", 2) + 1;
            // Single-layer pools remain identifiable when a later prefab revision changes its X/Z bounds.
            if (Integer.bitCount(layers) == 1 && mx + width <= (gx + 1) * cell && mz + depth <= (gz + 1) * cell)
                return key(s, layer, gx, gz);
            int oldInset = 32
                + ("subsided_factory".equals(s.prefab) ? 192 : "fallen_foundry".equals(s.prefab) ? 96 : 24);
            if (matches(mx, mz, cell, gx, gz, width, depth, oldInset, h)) return key(s, layer, gx, gz);
            for (int attempt = 0; attempt < 12; attempt++) {
                long ah = attempt == 0 ? h : GTSRWorldgenHash.splitmix64(h + attempt * 0x9E3779B97F4A7C15L);
                if (matches(mx, mz, cell, gx, gz, width, depth, 40, ah)) return key(s, layer, gx, gz);
            }
        }
        return null;
    }

    private static boolean matches(int mx, int mz, int cell, int gx, int gz, int width, int depth, int inset, long h) {
        if (width + inset * 2 >= cell || depth + inset * 2 >= cell) return false;
        int x = (gx * cell + inset + (int) Math.floorMod(h >>> 16, cell - width - inset * 2)) & ~15;
        int z = (gz * cell + inset + (int) Math.floorMod(h >>> 32, cell - depth - inset * 2)) & ~15;
        return mx == x && mz == z;
    }

    private static String cityGroup(RemasterSite s, boolean canonical) {
        int gx = Math.floorDiv(s.x, CELLS[0]), gz = Math.floorDiv(s.z, CELLS[0]);
        long h = hash(s.seed, 0, gx, gz);
        if (Math.floorMod(h, 3) != 0 || Math.floorMod(h >>> 8, 3) != 0) return null;
        if (!canonical) return ("city-grid".equals(s.layout) && "prosperity_city_full".equals(s.prefab))
            || ("city-plot".equals(s.layout) && POOLS.get("city_variant")
                .contains(s.prefab)) ? key(s, 0, gx, gz) : null;
        int x = (gx * CELLS[0] + 64 + (int) Math.floorMod(h >>> 16, CELLS[0] - 896)) & ~15;
        int z = (gz * CELLS[0] + 64 + (int) Math.floorMod(h >>> 32, CELLS[0] - 512)) & ~15;
        if ("city-grid".equals(s.layout))
            return "prosperity_city_full".equals(s.prefab) && s.variant == 0 && s.x == x && s.z == z ? key(s, 0, gx, gz)
                : null;
        List<String> plots = POOLS.get("city_variant");
        int offset = (int) Math.floorMod(h >>> 20, plots.size());
        for (int i = 0; i < plots.size(); i++) {
            String id = plots.get((i + offset) % plots.size());
            long ph = GTSRWorldgenHash.splitmix64(h + i * 0x9E3779B97F4A7C15L);
            if (!s.prefab.equals(id) || s.variant != variant(id, ph)) continue;
            JsonObject d = RemasterCatalog.descriptor(id, s.variant);
            if (s.x == x + (i % 7) * 96 + 20 - bound(d, "min", 0) && s.z == z + (i / 7) * 96 + 20 - bound(d, "min", 2))
                return key(s, 0, gx, gz);
        }
        return null;
    }

    /** Canonical city roads and their authored plots deliberately share one footprint. */
    static boolean cityParentOverlap(RemasterSite a, RemasterSite b) {
        if (!city(a) || !city(b) || a.layout.equals(b.layout)) return false;
        String group = cityGroup(a, true);
        return group != null && group.equals(cityGroup(b, true));
    }
}
