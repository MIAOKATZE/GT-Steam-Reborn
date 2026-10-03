package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/** Limited dimension-stone footings; natural geology remains owned by the terrain provider. */
public final class RemasterTerrain {

    private RemasterTerrain() {}

    public static int margin(RemasterSite s) {
        if ("tree-overlay".equals(s.layout) || "city-plot".equals(s.layout)) return 0;
        return 8;
    }

    public static int[] footprint(RemasterSite s) {
        if ("city-grid".equals(s.layout)) return new int[] { s.x, s.z, s.x + 767, s.z + 383 };
        JsonObject d = RemasterCatalog.descriptor(s.prefab, s.variant);
        return new int[] { s.x + d.getAsJsonArray("min")
            .get(0)
            .getAsInt(), s.z
                + d.getAsJsonArray("min")
                    .get(2)
                    .getAsInt(),
            s.x + d.getAsJsonArray("max")
                .get(0)
                .getAsInt(),
            s.z + d.getAsJsonArray("max")
                .get(2)
                .getAsInt() };
    }

    public static void build(RemasterSite s, BlockSink sink, int cx, int cz) {
        // The dimension provider owns geology. A buried prefab must fit existing rock;
        // constructing a massif here would conceal a bad anchor and destroy nearby terrain.
        if ("tree-overlay".equals(s.layout) || "city-grid".equals(s.layout)) return;
        JsonObject metadata = s.plan().metadata;
        if (!metadata.has("rooms")) return;
        for (JsonElement e : metadata.getAsJsonArray("rooms")) {
            JsonObject room = e.getAsJsonObject();
            int floor = s.y + room.get("y")
                .getAsInt() + 1;
            int x0 = s.x + room.get("x")
                .getAsInt(), z0 = s.z
                    + room.get("z")
                        .getAsInt();
            int x1 = x0 + room.get("w")
                .getAsInt() - 1, z1 = z0
                    + room.get("d")
                        .getAsInt()
                    - 1;
            // Narrow bearing piers, never a filled room footprint or manufactured hillside.
            for (int x = Math.max(x0, cx << 4); x <= Math.min(x1, (cx << 4) + 15); x++)
                for (int z = Math.max(z0, cz << 4); z <= Math.min(z1, (cz << 4) + 15); z++) {
                    if ((x - x0) % 8 != 0 && x != x1) continue;
                    if ((z - z0) % 8 != 0 && z != z1) continue;
                    int natural = ProsperityTerrainProfile.heightAt(s.seed, x, z);
                    int gap = floor - natural - 1;
                    if (gap <= 0 || gap > 8 || floor > 254) continue;
                    for (int y = natural + 1; y < floor; y++) sink.setBlock(x, y, z, "gtsr:ProsperityStone", 0, 2);
                }
        }
    }
}
