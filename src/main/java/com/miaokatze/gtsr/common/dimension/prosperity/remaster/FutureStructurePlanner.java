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
            return size() > 512;
        }
    };

    private FutureStructurePlanner() {}

    public static boolean isFuture(String id) {
        return RemasterCatalog.ids()
            .contains(id)
            && RemasterCatalog.descriptor(id, 0)
                .getAsJsonObject("placement")
                .has("futurePlacement");
    }

    public static synchronized List<RemasterSite> cell(long seed, int gx, int gz) {
        String cache = seed + ":" + gx + ":" + gz;
        if (CACHE.containsKey(cache)) return CACHE.get(cache);
        List<RemasterSite> sites = new ArrayList<>();
        long base = GTSRWorldgenHash.cellSeed(seed, gx, gz, 0x4655545552453732L);
        for (int slot = 0; slot < 16; slot++) {
            long h = GTSRWorldgenHash.splitmix64(base + slot * 0x9E3779B97F4A7C15L);
            int roll = (int) Math.floorMod(h, 100);
            if (roll >= 75) continue;
            String category = roll == 0 ? "expansion" : roll < 16 ? "medium" : roll < 43 ? "small" : "ruin";
            for (int attempt = 0; attempt < 6; attempt++) {
                long a = GTSRWorldgenHash.splitmix64(h + attempt * 0x632BE59BD9B4E019L);
                int centerX = gx * 2048 + (slot % 4) * 512 + 128 + (int) Math.floorMod(a >>> 8, 256);
                int centerZ = gz * 2048 + (slot / 4) * 512 + 128 + (int) Math.floorMod(a >>> 32, 256);
                int roster = ProsperityTerrainProfile.chainRosterIndexAt(seed, centerX >> 2, centerZ >> 2);
                List<String> candidates = new ArrayList<>();
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
                        candidates.add(id);
                }
                if (candidates.isEmpty()) continue;
                String id = candidates.get((int) Math.floorMod(a >>> 16, candidates.size()));
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
                    .getAsInt() > 250) continue;
                RemasterSite s = new RemasterSite(id, 0, seed, x, y, z, "natural-prefab");
                boolean occupied = false;
                for (CompactSceneTerrain.Branch b : CompactSceneTerrain.cell(seed, gx, gz))
                    if (s.overlaps(b.centerX - 96, b.centerZ - 96, b.centerX + 96, b.centerZ + 96, 24)) occupied = true;
                if (occupied || !admit(seed, s, floor, ex, ez)) continue;
                sites.add(s);
                break;
            }
        }
        List<RemasterSite> result = Collections.unmodifiableList(sites);
        CACHE.put(cache, result);
        return result;
    }

    private static boolean biome(String biome, int roster) {
        if (biome.startsWith("grassland") || biome.startsWith("rusted_steppe")) return roster == 0;
        if (biome.startsWith("forest") || biome.startsWith("gearwork_forest")) return roster == 1;
        if (biome.startsWith("brass_desert")) return roster == 2;
        if (biome.startsWith("swamp")) return roster == 3;
        return true; // River/lake structures use dry banks rather than their water interiors.
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
        for (int x = ex - 2; x <= ex + 2; x++)
            for (int z = ez - 2; z <= ez + 2; z++) if (ProsperityTerrainProfile.heightAt(seed, x, z) != floor
                || ChunkProviderProsperityRuins.naturalWaterTopAt(seed, x, z) >= floor) return false;
        for (int x = s.minX() - 2; x <= s.maxX() + 2; x += 8) for (int z = s.minZ() - 2; z <= s.maxZ() + 2; z += 8) {
            int height = ProsperityTerrainProfile.heightAt(seed, x, z);
            if (Math.abs(height - floor) > 5 || ChunkProviderProsperityRuins.naturalWaterTopAt(seed, x, z) >= height)
                return false;
        }
        return true;
    }
}
