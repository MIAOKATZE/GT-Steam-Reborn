import java.io.*;
import java.util.*;
import com.google.gson.*;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;

/** Real planner anchors and production geometry over generateTerrain's actual stone height field. */
public final class StairNaturalProfileCapture {
    public static void main(String[] args) throws Exception {
        NativeTerrainFixture.initialize();
        JsonArray authored = new JsonParser().parse(new FileReader("temp/stair-walk-fixtures.json")).getAsJsonArray();
        Map<Integer, RemasterSite> sites = new LinkedHashMap<>();
        long seed = 20261001L;
        outer: for (int gx = -12; gx <= 12; gx++) for (int gz = -12; gz <= 12; gz++)
            for (RemasterSite site : RemasterPlanner.cell(seed, 0, gx, gz))
                if (site.prefab.equals("city_watch_tower") && site.variant > 0) {
                    sites.putIfAbsent(site.variant, site);
                    if (sites.size() == 2) break outer;
                }
        if (sites.size() != 2) throw new AssertionError("missing normal-six-biome watch variants in cells[-12,12]^2: admitted=" + sites.keySet());
        for (RemasterSite site : sites.values()) {
            if (site.y != ProsperityTerrainProfile.heightAt(seed, site.entryX(), site.entryZ()))
                throw new AssertionError("normal watch anchor is not the actual natural entry floor");
            if (RemasterSite.read(site.save()).y != site.y)
                throw new AssertionError("saved normal watch Y changed");
        }
        JsonArray output = new JsonArray(), fields = new JsonArray();
        for (JsonElement element : authored) {
            JsonObject route = element.getAsJsonObject();
            if (!route.has("kind") || !route.get("id").getAsString().equals("city_watch_tower")) continue;
            int variant = route.get("variant").getAsInt();
            RemasterSite site = sites.get(variant);
            if (site == null) continue;
            Set<String> extent = new HashSet<>();
            Map<String, String> cells = new HashMap<>();
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = minX, maxZ = maxX;
            JsonArray terrain = new JsonArray();
            for (JsonElement point : route.getAsJsonArray("points")) {
                JsonArray p = point.getAsJsonArray();
                int px = p.get(0).getAsInt(), py = p.get(1).getAsInt(), pz = p.get(2).getAsInt();
                JsonArray field = new JsonArray();
                field.add(new JsonPrimitive(px)); field.add(new JsonPrimitive(pz));
                field.add(new JsonPrimitive(ProsperityTerrainProfile.heightAt(seed, site.x + px, site.z + pz) - site.y));
                terrain.add(field);
                for (int x = px - 3; x <= px + 3; x++) for (int z = pz - 3; z <= pz + 3; z++) {
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    int top = ProsperityTerrainProfile.heightAt(seed, site.x + x, site.z + z) - site.y;
                    for (int y = py - 7; y <= py + 7; y++) {
                        String k = x + "," + y + "," + z;
                        extent.add(k);
                        if (y <= top) cells.put(k, "minecraft:stone#0");
                    }
                }
            }
            BlockSink sink = (x,y,z,block,meta,flags) -> {
                String k = (x - site.x) + "," + (y - site.y) + "," + (z - site.z);
                if (!extent.contains(k)) return true;
                String id = block.toString();
                if (id.startsWith("minecraft:air")) cells.remove(k); else cells.put(k,id + "#" + meta);
                return true;
            };
            for (int cx = (site.x + minX) >> 4; cx <= (site.x + maxX) >> 4; cx++)
                for (int cz = (site.z + minZ) >> 4; cz <= (site.z + maxZ) >> 4; cz++) {
                    RemasterTerrain.build(site, sink, cx, cz);
                    RemasterWorldgen.geometry(site, site.plan(), sink, cx, cz);
                }
            JsonObject row = new JsonParser().parse(route.toString()).getAsJsonObject();
            JsonArray geometry = new JsonArray();
            for (Map.Entry<String,String> e : cells.entrySet()) {
                JsonArray cell = new JsonArray();
                for (String n : e.getKey().split(",")) cell.add(new JsonPrimitive(Integer.parseInt(n)));
                cell.add(new JsonPrimitive(e.getValue())); geometry.add(cell);
            }
            row.add("cells", geometry);
            row.addProperty("reason", "actual seed/planner anchor + generateTerrain stone height field + production owner geometry/explicit air/foundation");
            output.add(row);
            JsonObject evidence = new JsonObject();
            evidence.addProperty("variant",variant); evidence.addProperty("seed",seed);
            evidence.addProperty("siteX",site.x); evidence.addProperty("siteY",site.y); evidence.addProperty("siteZ",site.z);
            evidence.addProperty("layout",site.layout); evidence.addProperty("route",route.get("route").getAsString());
            evidence.add("localNaturalTopByWaypoint", terrain); fields.add(evidence);
        }
        if (output.size() != 12) throw new AssertionError("all 2 aliases and 4 original loot endpoints per variant required: " + output.size());
        try (Writer out = new FileWriter("temp/stair-natural-fixtures.json")) { new Gson().toJson(output,out); }
        try (Writer out = new FileWriter("temp/stair-natural-fields.json")) { new GsonBuilder().setPrettyPrinting().create().toJson(fields,out); }
        System.out.println("captured " + output.size() + " production natural watch routes at actual seed/planner anchors");
    }
}
