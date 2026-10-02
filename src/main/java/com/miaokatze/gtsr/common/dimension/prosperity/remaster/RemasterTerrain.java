package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/** Chunk-owned solid geology precedes the authored air. No enclosing building is synthesized. */
public final class RemasterTerrain {

    private RemasterTerrain() {}

    public static int margin(RemasterSite s) {
        if ("tree-overlay".equals(s.layout) || "city-plot".equals(s.layout)) return 0;
        if ("subsided_factory".equals(s.prefab)) return 192;
        if ("fallen_foundry".equals(s.prefab)) return 96;
        return "city-grid".equals(s.layout) ? 24 : 24;
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
        if ("tree-overlay".equals(s.layout) || "city-plot".equals(s.layout)) return;
        int[] box = footprint(s);
        int margin = margin(s);
        int minY = "city-grid".equals(s.layout) ? 0
            : RemasterCatalog.descriptor(s.prefab, s.variant)
                .get("yMin")
                .getAsInt();
        int bottom = Math.max(1, s.y + minY - 4);
        for (int x = cx << 4; x < (cx << 4) + 16; x++) for (int z = cz << 4; z < (cz << 4) + 16; z++) {
            int dx = Math.max(box[0] - x, Math.max(0, x - box[2]));
            int dz = Math.max(box[1] - z, Math.max(0, z - box[3]));
            int d = Math.max(dx, dz);
            if (d > margin) continue;
            int natural = ProsperityTerrainProfile.heightAt(s.seed, x, z);
            // Smooth broad massif, terrace at the authored surface and continuous accessible outer slopes.
            double t = 1.0 - d / (double) margin;
            t = t * t * (3 - 2 * t);
            int top = (int) Math.round(natural + (s.y - 1 - natural) * t);
            if (d == 0) top = s.y - 1;
            top = Math.max(1, Math.min(253, top));
            for (int y = Math.max(1, Math.min(bottom, natural)); y <= top; y++)
                sink.setBlock(x, y, z, "minecraft:stone", 0, 2);
            if (top < natural) for (int y = top + 1; y <= Math.min(254, natural + 3); y++)
                sink.setBlock(x, y, z, "minecraft:air", 0, 2);
            sink.setBlock(x, top, z, "minecraft:grass", 0, 2);
        }
    }
}
