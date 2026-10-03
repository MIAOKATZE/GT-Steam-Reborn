import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Production terrain/planner invariants, using pure fields rather than an emulated Minecraft terrain. */
public final class TerrainPlacementCheck {
    public static void main(String[] args) {
        NativeTerrainFixture.initialize();
        long[] piers = { 0 };
        for (String id : RemasterCatalog.ids()) {
            RemasterSite s = new RemasterSite(id, 0, 20261001L, -27, 90, 13);
            for (int cx = s.minX() >> 4; cx <= s.maxX() >> 4; cx++)
                for (int cz = s.minZ() >> 4; cz <= s.maxZ() >> 4; cz++) {
                    final int ownerX = cx, ownerZ = cz;
                    RemasterTerrain.build(s, (x, y, z, block, meta, flags) -> {
                        int natural = ProsperityTerrainProfile.heightAt(s.seed, x, z);
                        if ((x >> 4) != ownerX || (z >> 4) != ownerZ || y <= natural || y > natural + 8
                            || y < 1 || y > 254 || meta != 0 || flags != 2)
                            throw new AssertionError("unbounded/foreign pier " + id);
                        if (!"gtsr:ProsperityStone".equals(block.toString()))
                            throw new AssertionError("non-dimension foundation " + block);
                        piers[0]++; return true;
                    }, cx, cz);
                }
        }
        long legacy = legacyPlane();
        long bridge = 0;
        // Same normal-field search domain as the dedicated city bridge checks. Do not pin a historical candidate.
        for (long seed : new long[] { 20261001L, 20261002L, 20261003L }) {
            RemasterSite selected = null;
            outer: for (int gx = -12; gx <= 12; gx++) for (int gz = -12; gz <= 12; gz++)
                for (RemasterSite s : RemasterPlanner.cell(seed, 0, gx, gz))
                    if ("city-grid".equals(s.layout) && s.roadVersion == 1 && RemasterCityBridge.viable(s)) {
                        selected = s;
                        break outer;
                    }
            if (selected == null) throw new AssertionError("no viable normal6 production city in [-12,12], seed=" + seed);
            bridge += bridgeGeometry(selected);
        }
        int candidates = 0;
        for (int gx = -3; gx <= 3; gx++) for (int gz = -3; gz <= 3; gz++) for (int layer = 0; layer < 3; layer++)
            for (RemasterSite s : RemasterPlanner.cell(20261001L, layer, gx, gz)) {
                JsonObject d = RemasterCatalog.descriptor(s.prefab, s.variant);
                if (s.y + d.get("yMin").getAsInt() < 5 || s.y + d.get("yMax").getAsInt() > 254)
                    throw new AssertionError("bedrock/world-height intersection " + s.prefab);
                if (!"city-grid".equals(s.layout) && ChunkProviderProsperityRuins.naturalWaterTopAt(s.seed, s.entryX(), s.entryZ()) >= s.entryY())
                    throw new AssertionError("flooded arrival " + s.prefab);
                candidates++;
            }
        NativeTerrainFixture.assertNormal();
        if (piers[0] == 0 || legacy == 0 || bridge == 0 || candidates == 0) throw new AssertionError("empty test fixture");
        System.out.println("PASS: " + piers[0] + " bounded owner-only dimension-stone piers; " + legacy
            + " legacy plane cells; " + bridge + " bounded bridge writes in 3 normal6 cities; " + candidates
            + " real-field placement anchors; authored volumes and natural-water AIR protected (pure geometry, no MC movement claim).");
    }

    private static long legacyPlane() {
        RemasterSite city = new RemasterSite("prosperity_city_full", 0, 20261001L, -512, 150, -256, "city-grid", 0);
        Set<Integer> expected = new HashSet<>(), actual = new HashSet<>();
        for (int u = 0; u < 768; u++) for (int v = 0; v < 384; v++)
            if (cityRoad(u, v)) expected.add(u * 384 + v);
        for (int cx = city.minX() >> 4; cx <= city.maxX() >> 4; cx++)
            for (int cz = city.minZ() >> 4; cz <= city.maxZ() >> 4; cz++) {
                final int ownerX = cx, ownerZ = cz;
                int[] count = { 0 };
                int returned = RemasterWorldgen.geometry(city, null, (x, y, z, block, meta, flags) -> {
                    if ((x >> 4) != ownerX || (z >> 4) != ownerZ || !cityRoad(x - city.x, z - city.z)
                        || y != city.y - 1 || !"minecraft:stonebrick".equals(block.toString()) || meta != 0 || flags != 2)
                        throw new AssertionError("saved roadVersion0 changed owner/material/savedY-1 plane");
                    if (!actual.add((x - city.x) * 384 + z - city.z)) throw new AssertionError("duplicate legacy cell");
                    count[0]++;
                    return true;
                }, cx, cz);
                if (returned != count[0]) throw new AssertionError("legacy write accounting");
            }
        if (!actual.equals(expected)) throw new AssertionError("legacy plane footprint incomplete");
        return actual.size();
    }

    private static long bridgeGeometry(RemasterSite city) {
        List<RemasterSite> plots = RemasterPlanner.savedCityPlots(city);
        if (plots.size() != 28) throw new AssertionError("missing original city plots");
        // Independently derive the protected source column spans from immutable prefab runs, including authored AIR.
        int[] sourceMin = new int[768 * 384], sourceMax = new int[768 * 384];
        for (RemasterSite plot : plots) {
            RemasterPrefab prefab = plot.plan();
            for (int cx = Math.floorDiv(prefab.min[0], 16); cx <= Math.floorDiv(prefab.max[0], 16); cx++)
                for (int cz = Math.floorDiv(prefab.min[2], 16); cz <= Math.floorDiv(prefab.max[2], 16); cz++)
                    for (RemasterPrefab.Run run : prefab.slice(cx, cz)) for (int i = 0; i < run.length; i++) {
                        int u = plot.x + run.x + i - city.x, v = plot.z + run.z - city.z;
                        if (u < 0 || v < 0 || u >= 768 || v >= 384) throw new AssertionError("original plot outside city");
                        int key = u * 384 + v, y = plot.y + run.y;
                        sourceMin[key] = sourceMin[key] == 0 ? y : Math.min(sourceMin[key], y);
                        sourceMax[key] = Math.max(sourceMax[key], y);
                    }
        }
        int[] witness = RemasterCityBridge.witnessPosition(city);
        long[] writes = { 0 }, supports = { 0 }, rails = { 0 }, air = { 0 };
        int[] natural = new int[768 * 384], water = new int[768 * 384], floors = new int[768 * 384];
        for (int u = 0; u < 768; u++) for (int v = 0; v < 384; v++) {
            int key = u * 384 + v, x = city.x + u, z = city.z + v;
            natural[key] = ProsperityTerrainProfile.heightAt(city.seed, x, z);
            water[key] = ChunkProviderProsperityRuins.naturalWaterTopAt(city.seed, x, z);
            floors[key] = RemasterCityBridge.floorAt(city, x, z);
        }
        for (int cx = city.minX() >> 4; cx <= city.maxX() >> 4; cx++)
            for (int cz = city.minZ() >> 4; cz <= city.maxZ() >> 4; cz++) {
                final int ownerX = cx, ownerZ = cz;
                long before = writes[0];
                int returned = RemasterWorldgen.geometry(city, null, (x, y, z, block, meta, flags) -> {
                    int u = x - city.x, v = z - city.z;
                    if ((x >> 4) != ownerX || (z >> 4) != ownerZ || u < 0 || v < 0 || u >= 768 || v >= 384
                        || y < 1 || y > 254 || flags != 2) throw new AssertionError("bridge owner/bounds/world-height");
                    int key = u * 384 + v, floor = floors[key];
                    if (floor == 0 || y < Math.min(natural[key], floor - 1) || y > floor + 7)
                        throw new AssertionError("bridge write outside finite column");
                    if (floor <= water[key]) throw new AssertionError("bridge deck submerged in natural water");
                    if (sourceMin[key] != 0 && y >= sourceMin[key] && y <= sourceMax[key])
                        throw new AssertionError("bridge modifies original plot source volume at " + x + "," + y + "," + z);
                    String id = block.toString();
                    boolean walk = RemasterCityBridge.walkable(city, x, z);
                    if ("minecraft:stonebrick".equals(id) && meta == 0 && y <= floor) {
                        if (y < floor) supports[0]++;
                    } else if ("minecraft:stone_brick_stairs".equals(id) && meta >= 0 && meta < 4 && walk && y == floor) {
                        // Real stair collision and route connectivity belong to CityRoadCollisionCheck.
                    } else if ("minecraft:fence".equals(id) && meta == 0 && !walk && y > floor) {
                        rails[0]++;
                    } else if ("minecraft:air".equals(id) && meta == 0 && walk && y > floor) {
                        int head = witness != null && Math.abs(x - witness[0]) <= 2 && Math.abs(z - witness[2]) <= 2 ? 7 : 2;
                        if (y > floor + head || y <= water[key] || natural[key] - floor > 2)
                            throw new AssertionError("AIR removes natural water or exceeds bounded owned head clearance");
                        air[0]++;
                    } else throw new AssertionError("bridge material/metadata/vertical role outside contract: " + id);
                    writes[0]++;
                    return true;
                }, cx, cz);
                if (returned != writes[0] - before) throw new AssertionError("bridge write accounting");
            }
        if (supports[0] == 0 || rails[0] == 0 || air[0] == 0) throw new AssertionError("empty bridge role coverage");
        System.out.println("BRIDGE_FIELD seed=" + city.seed + " origin=" + city.x + "," + city.y + "," + city.z
            + " writes=" + writes[0] + " supports=" + supports[0] + " rails=" + rails[0] + " AIR=" + air[0]);
        return writes[0];
    }

    private static boolean cityRoad(int u, int v) {
        return u >= 0 && v >= 0 && u < 768 && v < 384
            && (u % 96 < 8 || v % 96 < 8 || u >= 680 && u < 760 && v >= 104 && v < 280);
    }
}
