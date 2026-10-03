package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/** Pure revision-seven planning. Every footprint is contained in its parent cell with a dry margin. */
public final class RemasterPlanner {

    private static final int[] CELLS = { 128, 32, 16 };
    private static final Map<String, List<String>> POOLS = new LinkedHashMap<>();
    private static final Map<String, JsonObject> PLACEMENT = new LinkedHashMap<>();
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

    private static JsonObject placement(String id, int variant) {
        synchronized (PLACEMENT) {
            String key = id + ":" + variant;
            JsonObject cached = PLACEMENT.get(key);
            if (cached != null) return cached;
            JsonObject d = RemasterCatalog.descriptor(id, variant);
            JsonObject source = d.has("placement") ? d.getAsJsonObject("placement") : new JsonObject();
            JsonObject result = new JsonObject();
            for (String field : new String[] { "terrain", "surfaceEntrance", "productionSurfaceEntrance", "rooms",
                "entryApronEnvelope" }) if (source.has(field)) result.add(field, source.get(field));
            PLACEMENT.put(key, result);
            return result;
        }
    }

    private static RemasterSite anchor(String id, long seed, long h, int minX, int minZ) {
        int variant = RemasterRollout.standardVariant(id);
        JsonObject d = RemasterCatalog.descriptor(id, variant);
        int x = minX - value(d, "min", 0), z = minZ - value(d, "min", 2);
        JsonObject metadata = placement(id, variant);
        JsonObject entrance = metadata.getAsJsonObject("surfaceEntrance");
        if (entrance == null) entrance = metadata.getAsJsonObject("productionSurfaceEntrance");
        int ex = entrance != null && entrance.has("x") ? entrance.get("x")
            .getAsInt() : value(d, "min", 0) + 4;
        int ez = entrance != null && entrance.has("z") ? entrance.get("z")
            .getAsInt() : value(d, "min", 2) + 4;
        int top = entrance != null && entrance.has("topY") ? entrance.get("topY")
            .getAsInt() : 0;
        int y = ProsperityTerrainProfile.heightAt(seed, x + ex, z + ez) + (entrance == null ? 1 : 0) - top;
        // Never move a surface entrance upwards to conceal an underground/world-height violation.
        if (y + d.get("yMin")
            .getAsInt() < 5 || y
                + d.get("yMax")
                    .getAsInt()
                > 254)
            return null;
        if (ChunkProviderProsperityRuins.naturalWaterTopAt(seed, x + ex, z + ez) >= y + top) return null;
        JsonObject terrain = metadata.getAsJsonObject("terrain");
        boolean buried = terrain != null && terrain.has("buried")
            && "full".equals(
                terrain.get("buried")
                    .getAsString());
        if (buried) {
            // Check every building roof against the real column field; routes/shafts are intentionally open.
            int cover = terrain.has("cover") ? terrain.get("cover")
                .getAsInt() : 8;
            for (JsonElement room : metadata.getAsJsonArray("rooms")) {
                JsonObject r = room.getAsJsonObject();
                int rx = r.get("x")
                    .getAsInt(),
                    rz = r.get("z")
                        .getAsInt();
                int rw = r.get("w")
                    .getAsInt(),
                    rd = r.get("d")
                        .getAsInt();
                int roof = y + r.get("y")
                    .getAsInt()
                    + r.get("h")
                        .getAsInt();
                if (roof >= y - 2) continue; // Authored surface entrance structures.
                for (int u : new int[] { 0, rw / 2, rw - 1 }) for (int v : new int[] { 0, rd / 2, rd - 1 })
                    if (ProsperityTerrainProfile.heightAt(seed, x + rx + u, z + rz + v) < roof + cover) return null;
            }
        }
        return new RemasterSite(id, variant, seed, x, y, z);
    }

    /** Recover the planned plots of a saved parent without applying a new city's eligibility filter. */
    public static List<RemasterSite> savedCityPlots(RemasterSite parent) {
        if (!RemasterRollout.allowsGeneration(parent) || !"city-grid".equals(parent.layout))
            return Collections.emptyList();
        long h = hash(parent.seed, 0, Math.floorDiv(parent.x, CELLS[0] * 16), Math.floorDiv(parent.z, CELLS[0] * 16));
        List<String> plots = POOLS.get("city_variant");
        List<RemasterSite> out = new ArrayList<>();
        int offset = (int) Math.floorMod(h >>> 20, plots.size());
        for (int i = 0; i < plots.size(); i++) {
            String id = plots.get((i + offset) % plots.size());
            long ph = GTSRWorldgenHash.splitmix64(h + i * 0x9E3779B97F4A7C15L);
            if (parent.roadVersion == 0) {
                int variant = (int) Math.floorMod(ph >>> 12, RemasterCatalog.variants(id));
                JsonObject d = RemasterCatalog.descriptor(id, variant);
                out.add(
                    new RemasterSite(
                        id,
                        variant,
                        parent.seed,
                        parent.x + (i % 7) * 96 + 20 - value(d, "min", 0),
                        parent.y,
                        parent.z + (i / 7) * 96 + 20 - value(d, "min", 2),
                        "city-plot"));
            } else {
                RemasterSite p = anchor(
                    id,
                    parent.seed,
                    ph,
                    parent.x + (i % 7) * 96 + 20,
                    parent.z + (i / 7) * 96 + 20);
                if (p != null) out.add(new RemasterSite(p.prefab, p.variant, p.seed, p.x, p.y, p.z, "city-plot"));
            }
        }
        return out;
    }

    /** Only the two large standard bosses enter cells; the court belongs to the natural-tree pass. */
    public static synchronized List<RemasterSite> cell(long seed, int layer, int gx, int gz) {
        String key = seed + ":" + layer + ":" + gx + ":" + gz;
        List<RemasterSite> cached = CACHE.get(key);
        if (cached != null) return cached;
        List<RemasterSite> out = new ArrayList<>();
        // The natural-tree pass exclusively owns the third scene. No cities or smaller layers are admitted.
        if (layer != 0) return Collections.emptyList();
        long h = hash(seed, layer, gx, gz);
        int cell = CELLS[0] * 16;
        if (Math.floorMod(h, 3) == 0) {
            String id = (h >>> 8 & 1L) == 0 ? "fallen_foundry" : "subsided_factory";
            JsonObject d = RemasterCatalog.descriptor(id, RemasterRollout.standardVariant(id));
            int width = value(d, "max", 0) - value(d, "min", 0) + 1;
            int depth = value(d, "max", 2) - value(d, "min", 2) + 1;
            int inset = 40;
            if (width + inset * 2 < cell && depth + inset * 2 < cell) {
                for (int attempt = 0; attempt < 12; attempt++) {
                    long ah = GTSRWorldgenHash.splitmix64(h + attempt * 0x9E3779B97F4A7C15L);
                    int x = (gx * cell + inset + (int) Math.floorMod(ah >>> 16, cell - width - inset * 2)) & ~15;
                    int z = (gz * cell + inset + (int) Math.floorMod(ah >>> 32, cell - depth - inset * 2)) & ~15;
                    RemasterSite candidate = anchor(id, seed, h, x, z);
                    if (candidate != null) candidate = RemasterEntryApron.admit(candidate);
                    if (candidate != null) {
                        out.add(candidate);
                        break;
                    }
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
        if (!RemasterRollout.isActive(id) || "forgotten_lake_court".equals(id)) return null;
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
