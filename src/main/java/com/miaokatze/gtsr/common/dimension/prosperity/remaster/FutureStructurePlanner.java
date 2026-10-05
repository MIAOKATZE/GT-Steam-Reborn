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

    private static final class Candidate {

        final String id;
        final int x, z;
        final long rank;

        Candidate(String id, int x, int z, long rank) {
            this.id = id;
            this.x = x;
            this.z = z;
            this.rank = rank;
        }

        RemasterSite footprint(long seed) {
            return new RemasterSite(id, 0, seed, x, 0, z, "natural-prefab");
        }
    }

    /** Independent global category lattices: parent cells are only cache/lookup ownership. */
    private static List<Candidate> candidates(long seed, int gx, int gz, int margin) {
        List<Candidate> result = new ArrayList<>();
        for (String category : new String[] { "medium", "small", "ruin" }) {
            int spacing = "medium".equals(category) ? 384 : 160;
            long salt = "medium".equals(category) ? 0x4D454449554D3832L
                : "small".equals(category) ? 0x534D414C4C38324CL : 0x5255494E5338324CL;
            int minX = gx * 2048 - margin, maxX = (gx + 1) * 2048 - 1 + margin;
            int minZ = gz * 2048 - margin, maxZ = (gz + 1) * 2048 - 1 + margin;
            for (int ix = Math.floorDiv(minX, spacing); ix <= Math.floorDiv(maxX, spacing); ix++)
                for (int iz = Math.floorDiv(minZ, spacing); iz <= Math.floorDiv(maxZ, spacing); iz++) {
                    long h = GTSRWorldgenHash.cellSeed(seed, ix, iz, salt);
                    int cx = ix * spacing + spacing / 2 + (int) Math.floorMod(h >>> 8, spacing / 3) - spacing / 6;
                    int cz = iz * spacing + spacing / 2 + (int) Math.floorMod(h >>> 32, spacing / 3) - spacing / 6;
                    if (cx < minX || cx > maxX || cz < minZ || cz > maxZ || Math.floorMod(h, 100) >= 90) continue;
                    int roster = ProsperityTerrainProfile.chainRosterIndexAt(seed, cx >> 2, cz >> 2);
                    List<String> pool = POOLS.get(category + ":" + (roster >= 0 && roster < 4 ? roster : -1));
                    if (pool == null || pool.isEmpty()) continue;
                    String id = pool.get((int) Math.floorMod(h >>> 16, pool.size()));
                    JsonArray size = RemasterCatalog.descriptor(id, 0)
                        .getAsJsonArray("nominal");
                    result.add(
                        new Candidate(
                            id,
                            cx - size.get(0)
                                .getAsInt() / 2,
                            cz - size.get(2)
                                .getAsInt() / 2,
                            h));
                }
        }
        return result;
    }

    public static final class CellSearch {

        private final long seed;
        private final String key;
        private final int gx, gz;
        private final List<Candidate> nearby, owned = new ArrayList<>();
        private final List<RemasterSite> sites = new ArrayList<>();
        private volatile List<RemasterSite> result;
        private int slot;
        private CompactSceneTerrain.CellSearch compact;

        private CellSearch(long seed, int gx, int gz, String target) {
            this.seed = seed;
            this.gx = gx;
            this.gz = gz;
            // Full planning is authoritative for target cursors as well: all overlap competitors remain present.
            key = seed + ":" + gx + ":" + gz;
            result = CACHE.get(key);
            nearby = result == null ? candidates(seed, gx, gz, 256) : Collections.emptyList();
            for (Candidate c : nearby) {
                JsonArray size = RemasterCatalog.descriptor(c.id, 0)
                    .getAsJsonArray("nominal");
                int cx = c.x + size.get(0)
                    .getAsInt() / 2, cz = c.z
                        + size.get(2)
                            .getAsInt() / 2;
                if (Math.floorDiv(cx, 2048) == gx && Math.floorDiv(cz, 2048) == gz) owned.add(c);
            }
        }

        public boolean done() {
            return result != null;
        }

        public List<RemasterSite> result() {
            return result;
        }

        public void step() {
            synchronized (FutureStructurePlanner.class) {
                if (done()) return;
                if (compact == null) compact = CompactSceneTerrain.beginCell(seed, gx, gz);
                if (!compact.done()) {
                    compact.step();
                    return;
                }
                if (slot == owned.size()) {
                    result = Collections.unmodifiableList(sites);
                    CACHE.put(key, result);
                    IN_PROGRESS.values()
                        .removeIf(search -> search == this);
                    return;
                }
                Candidate c = owned.get(slot++);
                RemasterSite footprint = c.footprint(seed);
                // Compare seed-only footprints before terrain admission. This stable priority also works across parent
                // boundaries.
                for (Candidate other : nearby) if (other != c && Long.compareUnsigned(other.rank, c.rank) < 0) {
                    RemasterSite o = other.footprint(seed);
                    if (footprint.overlaps(o.minX(), o.minZ(), o.maxX(), o.maxZ(), 12)) return;
                }
                JsonObject d = RemasterCatalog.descriptor(c.id, 0), placement = d.getAsJsonObject("placement")
                    .getAsJsonObject("futurePlacement");
                JsonArray entry = placement.getAsJsonArray("entrance");
                int ex = c.x + entry.get(0)
                    .getAsInt(), ez = c.z
                        + entry.get(2)
                            .getAsInt();
                int floor = ProsperityTerrainProfile.heightAt(seed, ex, ez), y = floor - entry.get(1)
                    .getAsInt();
                if (y < 8 || y + d.get("yMax")
                    .getAsInt() > 250) return;
                RemasterSite site = new RemasterSite(c.id, 0, seed, c.x, y, c.z, "natural-prefab");
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    for (CompactSceneTerrain.Branch branch : CompactSceneTerrain.cell(seed, gx + dx, gz + dz))
                        if (site.overlaps(
                            branch.centerX - 96,
                            branch.centerZ - 96,
                            branch.centerX + 96,
                            branch.centerZ + 96,
                            24)) return;
                if (admit(seed, site, floor, ex, ez)) sites.add(site);
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
