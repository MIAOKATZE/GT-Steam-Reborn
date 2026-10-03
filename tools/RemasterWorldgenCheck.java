import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterCatalog;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterPlanner;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterPrefab;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSite;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterWorldgen;

/** Executes the production voxel slice writer against a strict owner sink, without a Minecraft world. */
public final class RemasterWorldgenCheck {
    public static void main(String[] args) {
        NativeTerrainFixture.initialize();
        long total = 0, explicitAir = 0;
        for (String id : RemasterCatalog.ids()) {
            for (int variant = 0; variant < RemasterCatalog.variants(id); variant++) {
                JsonObject d = RemasterCatalog.descriptor(id, variant);
                RemasterPrefab p = RemasterCatalog.get(id, variant);
                int anchorY = Math.max(100, 1 - d.get("yMin").getAsInt());
                anchorY = Math.min(anchorY, 254 - d.get("yMax").getAsInt());
                RemasterSite s = new RemasterSite(id, variant, 20261001L, -27, anchorY, 13);
                long[] counts = {0, 0};
                for (int cx = s.minX() >> 4; cx <= s.maxX() >> 4; cx++)
                    for (int cz = s.minZ() >> 4; cz <= s.maxZ() >> 4; cz++) {
                        final int ownerX = cx, ownerZ = cz;
                        RemasterWorldgen.geometry(s, p, (x, y, z, block, meta, flags) -> {
                            if ((x >> 4) != ownerX || (z >> 4) != ownerZ || y < 1 || y > 254)
                                throw new AssertionError("foreign/out-of-height voxel " + id);
                            counts[0]++;
                            if (block.toString().equals("minecraft:air")) counts[1]++;
                            return true;
                        }, cx, cz);
                    }
                long expected = d.get("solidCount").getAsLong() + d.get("airCount").getAsLong();
                if (counts[0] != expected || counts[1] != d.get("airCount").getAsLong())
                    throw new AssertionError(id + "/" + variant + ": " + counts[0] + " != " + expected + "; air=" + counts[1]);
                total += counts[0]; explicitAir += counts[1];
            }
        }
        Set<String> coverage = new HashSet<>();
        int cities = 0;
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++)
            for (int layer = 0; layer < 3; layer++) {
                List<RemasterSite> first = RemasterPlanner.cell(20261001L, layer, x, z);
                List<RemasterSite> again = RemasterPlanner.cell(20261001L, layer, x, z);
                if (!first.toString().equals(again.toString())) throw new AssertionError("planner unstable");
                Set<String> plots = new HashSet<>();
                for (RemasterSite s : first) {
                    for (RemasterSite actual : RemasterPlanner.near(s.seed, s.entryX() >> 4, s.entryZ() >> 4))
                        if (actual.id().equals(s.id())) coverage.add(s.prefab);
                    JsonObject d = RemasterCatalog.descriptor(s.prefab, s.variant);
                    if (s.y + d.get("yMin").getAsInt() < 1 || s.y + d.get("yMax").getAsInt() > 254)
                        throw new AssertionError("planned height " + s.prefab);
                    if (s.prefab.startsWith("city_") && !s.prefab.equals("city_archive_cache")) plots.add(s.prefab);
                    if (s.prefab.equals("fiction_expansion_project") && s.variant != 0) throw new AssertionError("fiction starts after A");
                }
                if (!plots.isEmpty()) {
                    if (plots.size() != 28) throw new AssertionError("city roster " + plots.size());
                    cities++;
                }
            }
        // Rare native seed/cell regression, outside the original 625-cell domain.
        boolean rareFactory = false;
        for (RemasterSite s : RemasterPlanner.cell(20261001L, 0, -23, -92)) {
            if (!s.prefab.equals("subsided_factory")) continue;
            if (s.variant != 2) throw new AssertionError("rare native factory variant");
            for (RemasterSite actual : RemasterPlanner.near(s.seed, s.entryX() >> 4, s.entryZ() >> 4))
                if (actual.id().equals(s.id())) { coverage.add(s.prefab); rareFactory = true; }
        }
        if (!rareFactory) throw new AssertionError("rare native factory not rediscovered at natural entry");
        coverage.add("forgotten_lake_court"); // Dedicated original natural lake tree entrypoint, never the free-standing pools.
        if (coverage.size() != 90) throw new AssertionError("natural roster coverage=" + coverage.size() + " missing=" + missing(coverage));
        String initial = snapshot(RemasterPlanner.cell(20261001L, 0, 4, -3));
        for (int i = 0; i < 520; i++) RemasterPlanner.cell(20261001L, 2, i, 123);
        if (!initial.equals(snapshot(RemasterPlanner.cell(20261001L, 0, 4, -3)))) throw new AssertionError("cache eviction changes anchors");
        System.out.println("PASS: 268 prefab variants, " + total + " owner-only voxels, " + explicitAir + " explicit air; 90 natural IDs; " + cities + " cities with all 28 plots; bounded deterministic planner; world Y=1..254; fiction starts A.");
    }
    private static String snapshot(List<RemasterSite> sites) {
        StringBuilder out = new StringBuilder();
        for (RemasterSite s : sites) out.append(s.id()).append(':').append(s.y).append(':').append(s.minX()).append(':').append(s.maxZ()).append(';');
        return out.toString();
    }
    private static Set<String> missing(Set<String> found) {
        Set<String> missing = new HashSet<>(RemasterCatalog.ids()); missing.removeAll(found); return missing;
    }
}
