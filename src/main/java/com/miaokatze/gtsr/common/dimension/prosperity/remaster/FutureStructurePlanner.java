package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.CompactSceneTerrain;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/** Sparse authored buildings admitted on dry natural terrain, without a landscape floor. */
public final class FutureStructurePlanner {

    private static final Map<String, List<RemasterSite>> CACHE = new LinkedHashMap<String, List<RemasterSite>>(
        256,
        .75f,
        true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<RemasterSite>> e) {
            return size() > 1024;
        }
    };

    // Seed-only partial cells can be shared across simultaneous lookup cursors. World predicates stay outside.
    private static final Map<String, CellSearch> IN_PROGRESS = new LinkedHashMap<String, CellSearch>() {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CellSearch> e) {
            return size() > 128;
        }
    };

    private static final Map<String, List<String>> POOLS = new LinkedHashMap<>();
    static {
        for (String category : new String[] { "expansion", "medium", "small", "ruin" })
            for (int roster = -1; roster < 4; roster++) {
                List<String> pool = new ArrayList<>();
                for (String id : RemasterCatalog.ids()) if (isFuture(id)) {
                    JsonObject p = RemasterCatalog.descriptor(id, 0)
                        .getAsJsonObject("placement")
                        .getAsJsonObject("futurePlacement");
                    if (category.equals(
                        p.get("category")
                            .getAsString())
                        && biome(
                            p.get("biome")
                                .getAsString(),
                            roster))
                        pool.add(id);
                }
                POOLS.put(category + ":" + roster, Collections.unmodifiableList(pool));
            }
    }

    private FutureStructurePlanner() {}

    public static boolean isFuture(String id) {
        return RemasterCatalog.ids()
            .contains(id)
            && RemasterCatalog.descriptor(id, 0)
                .getAsJsonObject("placement")
                .has("futurePlacement");
    }

    public static synchronized List<RemasterSite> cell(long seed, int gx, int gz) {
        CellSearch search = beginCell(seed, gx, gz);
        while (!search.done()) search.step();
        return search.result();
    }

    /** Pure seed placement cursor; each step admits at most one candidate attempt. */
    public static synchronized CellSearch beginCell(long seed, int gx, int gz) {
        String key = seed + ":" + gx + ":" + gz;
        CellSearch search = IN_PROGRESS.get(key);
        if (search != null) return search;
        search = new CellSearch(seed, gx, gz, null);
        if (!search.done()) IN_PROGRESS.put(key, search);
        return search;
    }

    /** Target-only cursor retains all earlier attempts in any slot that can select the target. */
    public static synchronized CellSearch beginCell(long seed, int gx, int gz, String target) {
        String fullKey = seed + ":" + gx + ":" + gz;
        if (CACHE.containsKey(fullKey)) return new CellSearch(seed, gx, gz, null);
        String key = fullKey + ":" + target;
        CellSearch search = IN_PROGRESS.get(key);
        if (search == null) {
            search = new CellSearch(seed, gx, gz, target);
            if (!search.done()) IN_PROGRESS.put(key, search);
        }
        return search;
    }

    public static final class CellSearch {

        private final long seed, base;
        private final int gx, gz;
        private final String key, target, targetCategory;
        private final List<RemasterSite> sites = new ArrayList<>();
        private volatile List<RemasterSite> result;
        private int slot, attempt;
        private CompactSceneTerrain.CellSearch compact;

        private CellSearch(long seed, int gx, int gz, String target) {
            this.target = target;
            targetCategory = target == null ? null
                : RemasterCatalog.descriptor(target, 0)
                    .getAsJsonObject("placement")
                    .getAsJsonObject("futurePlacement")
                    .get("category")
                    .getAsString();
            this.seed = seed;
            this.gx = gx;
            this.gz = gz;
            key = seed + ":" + gx + ":" + gz + (target == null ? "" : ":" + target);
            base = GTSRWorldgenHash.cellSeed(seed, gx, gz, 0x4655545552453732L);
            result = CACHE.get(key);
        }

        public boolean done() {
            return result != null;
        }

        public List<RemasterSite> result() {
            return result;
        }

        private String choice(long h, int currentSlot, int currentAttempt, String category) {
            long a = GTSRWorldgenHash.splitmix64(h + currentAttempt * 0x632BE59BD9B4E019L);
            int x = gx * 2048 + (currentSlot % 4) * 512 + 128 + (int) Math.floorMod(a >>> 8, 256);
            int z = gz * 2048 + (currentSlot / 4) * 512 + 128 + (int) Math.floorMod(a >>> 32, 256);
            int roster = ProsperityTerrainProfile.chainRosterIndexAt(seed, x >> 2, z >> 2);
            List<String> candidates = POOLS.get(category + ":" + (roster >= 0 && roster < 4 ? roster : -1));
            return candidates == null || candidates.isEmpty() ? null
                : candidates.get((int) Math.floorMod(a >>> 16, candidates.size()));
        }

        public void step() {
            synchronized (FutureStructurePlanner.class) {
                if (done()) return;
                if (slot == 16) {
                    result = Collections.unmodifiableList(sites);
                    CACHE.put(key, result);
                    if (IN_PROGRESS.get(key) == this) IN_PROGRESS.remove(key);
                    return;
                }
                int currentSlot = slot, currentAttempt = attempt;
                long h = GTSRWorldgenHash.splitmix64(base + slot * 0x9E3779B97F4A7C15L);
                int roll = (int) Math.floorMod(h, 100);
                if (roll >= 75) {
                    slot++;
                    attempt = 0;
                    return;
                }
                String category = roll == 0 ? "expansion" : roll < 16 ? "medium" : roll < 43 ? "small" : "ruin";
                if (target != null && !category.equals(targetCategory)) {
                    slot++;
                    attempt = 0;
                    return;
                }
                long a = GTSRWorldgenHash.splitmix64(h + currentAttempt * 0x632BE59BD9B4E019L);
                int centerX = gx * 2048 + (currentSlot % 4) * 512 + 128 + (int) Math.floorMod(a >>> 8, 256);
                int centerZ = gz * 2048 + (currentSlot / 4) * 512 + 128 + (int) Math.floorMod(a >>> 32, 256);
                if (target != null && currentAttempt == 0) {
                    boolean possible = false;
                    for (int i = 0; i < 6; i++) if (target.equals(choice(h, currentSlot, i, category))) {
                        possible = true;
                        break;
                    }
                    if (!possible) {
                        slot = currentSlot + 1;
                        attempt = 0;
                        return;
                    }
                }
                String id = choice(h, currentSlot, currentAttempt, category);
                if (id != null && target != null) {
                    if (compact == null) compact = CompactSceneTerrain.beginCell(seed, gx, gz);
                    if (!compact.done()) {
                        compact.step();
                        return;
                    }
                }
                if (++attempt == 6) {
                    slot++;
                    attempt = 0;
                }
                if (id == null) return;
                JsonObject d = RemasterCatalog.descriptor(id, 0), p = d.getAsJsonObject("placement")
                    .getAsJsonObject("futurePlacement");
                JsonArray size = d.getAsJsonArray("nominal"), entry = p.getAsJsonArray("entrance");
                int x = centerX - size.get(0)
                    .getAsInt() / 2, z = centerZ
                        - size.get(2)
                            .getAsInt() / 2;
                int ex = x + entry.get(0)
                    .getAsInt(), ez = z
                        + entry.get(2)
                            .getAsInt();
                int floor = ProsperityTerrainProfile.heightAt(seed, ex, ez), y = floor - entry.get(1)
                    .getAsInt();
                if (y < 8 || y + d.get("yMax")
                    .getAsInt() > 250) return;
                RemasterSite s = new RemasterSite(id, 0, seed, x, y, z, "natural-prefab");
                boolean occupied = false;
                for (CompactSceneTerrain.Branch b : CompactSceneTerrain.cell(seed, gx, gz))
                    if (s.overlaps(b.centerX - 96, b.centerZ - 96, b.centerX + 96, b.centerZ + 96, 24)) occupied = true;
                if (occupied || !admit(seed, s, floor, ex, ez)) return;
                sites.add(s);
                slot = currentSlot + 1;
                attempt = 0;
            }
        }
    }

    private static boolean biome(String biome, int roster) {
        if (biome.startsWith("grassland") || biome.startsWith("rusted_steppe")) return roster == 0;
        if (biome.startsWith("forest") || biome.startsWith("gearwork_forest")) return roster == 1;
        if (biome.startsWith("brass_desert")) return roster == 2;
        if (biome.startsWith("swamp")) return roster == 3;
        return true; // River/lake structures use dry banks rather than their water interiors.
    }

    private static boolean waterAtLeast(long seed, int x, int z, int threshold) {
        int height = ProsperityTerrainProfile.heightAt(seed, x, z);
        if (com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField.islandPillarAt(seed, x, z))
            return -1 >= threshold;
        int direct = com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField
            .sanzuShoreWaterAt(seed, x, z) && height < ProsperityTerrainProfile.SEA_LEVEL
                ? ProsperityTerrainProfile.SEA_LEVEL - 1
                : -1;
        if (direct >= threshold) return true;
        int roster = ProsperityTerrainProfile.chainRosterIndexAt(seed, x >> 2, z >> 2);
        // Only roster three creates a nominal pool top; fixed neighbors never create one here.
        if (roster != 3) return false;
        long nominal = (long) com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField
            .poolLevelAt(seed, x, z, roster) - 1;
        // Relaxation only lowers nominal tops, and the public water query emits them only above the bed.
        if (nominal < threshold || nominal < height + 1L) return false;
        return ChunkProviderProsperityRuins.naturalWaterTopAt(seed, x, z) >= threshold;
    }

    private static boolean admit(long seed, RemasterSite s, int floor, int ex, int ez) {
        double[] lake = new double[5];
        for (int dx = -320; dx <= 320; dx += 128) for (int dz = -320; dz <= 320; dz += 128) {
            com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField
                .lakeCellCenterAt(seed, ex + dx, ez + dz, lake);
            if ((int) lake[2] == Integer.MIN_VALUE) continue;
            int ax = (int) Math.round(lake[0]), az = (int) Math.round(lake[1]);
            if (s.overlaps(ax - 176, az - 176, ax + 176, az + 176, 16)
                && com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField.lakeAt(seed, ax, az)
                    < com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField.LAKE_ISLAND)
                return false;
        }
        for (int x = ex - 2; x <= ex + 2; x++) for (int z = ez - 2; z <= ez + 2; z++)
            if (Math.abs(ProsperityTerrainProfile.heightAt(seed, x, z) - floor) > 1 || waterAtLeast(seed, x, z, floor))
                return false;
        JsonObject descriptor = RemasterCatalog.descriptor(s.prefab, s.variant);
        int minX = s.x + descriptor.getAsJsonArray("min")
            .get(0)
            .getAsInt();
        int maxX = s.x + descriptor.getAsJsonArray("max")
            .get(0)
            .getAsInt();
        int minZ = s.z + descriptor.getAsJsonArray("min")
            .get(2)
            .getAsInt();
        int maxZ = s.z + descriptor.getAsJsonArray("max")
            .get(2)
            .getAsInt();
        // Preserve authored footprint sampling; apron ownership must not shift the eight-block probe grid.
        for (int x = minX - 2; x <= maxX + 2; x += 8) for (int z = minZ - 2; z <= maxZ + 2; z += 8) {
            int height = ProsperityTerrainProfile.heightAt(seed, x, z);
            if (Math.abs(height - floor) > 5 || waterAtLeast(seed, x, z, height)) return false;
        }
        return true;
    }
}
