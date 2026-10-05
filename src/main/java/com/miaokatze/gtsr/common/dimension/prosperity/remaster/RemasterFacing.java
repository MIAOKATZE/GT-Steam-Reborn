package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Authored direction wins; otherwise face the closest authored approach at the same level. */
public final class RemasterFacing {

    private RemasterFacing() {}

    public static float yaw(RemasterSite site, JsonObject node) {
        if (node.has("yaw")) return node.get("yaw")
            .getAsFloat();
        if (node.has("facing") && node.get("facing")
            .isJsonPrimitive()) {
            String facing = node.get("facing")
                .getAsString();
            if ("north".equals(facing)) return 180F;
            if ("south".equals(facing)) return 0F;
            if ("west".equals(facing)) return 90F;
            if ("east".equals(facing)) return -90F;
        }
        double x = value(node, "x"), y = value(node, "y"), z = value(node, "z");
        double tx = site.entryX() - site.x, tz = site.entryZ() - site.z, best = Double.POSITIVE_INFINITY;
        JsonObject metadata = site.plan().metadata;
        if (metadata.has("routes")) for (JsonElement raw : metadata.getAsJsonArray("routes")) {
            JsonArray route = raw.isJsonArray() ? raw.getAsJsonArray()
                : raw.isJsonObject() && raw.getAsJsonObject()
                    .has("points") ? raw.getAsJsonObject()
                        .getAsJsonArray("points") : null;
            if (route == null) continue;
            for (int i = 1; i < route.size(); i++) {
                if (!route.get(i - 1)
                    .isJsonArray()
                    || !route.get(i)
                        .isJsonArray())
                    continue;
                JsonArray a = route.get(i - 1)
                    .getAsJsonArray(),
                    b = route.get(i)
                        .getAsJsonArray();
                if (a.size() < 3 || b.size() < 3) continue;
                double ax = a.get(0)
                    .getAsDouble(),
                    az = a.get(2)
                        .getAsDouble();
                double dx = b.get(0)
                    .getAsDouble() - ax,
                    dz = b.get(2)
                        .getAsDouble() - az;
                double t = dx * dx + dz * dz == 0 ? 0
                    : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz)));
                double px = ax + dx * t, pz = az + dz * t;
                double py = a.get(1)
                    .getAsDouble()
                    + (b.get(1)
                        .getAsDouble()
                        - a.get(1)
                            .getAsDouble())
                        * t;
                double distance = (px - x) * (px - x) + (pz - z) * (pz - z) + 4 * (py - y) * (py - y);
                if (distance < best) {
                    best = distance;
                    tx = px;
                    tz = pz;
                }
            }
        }
        return (float) (Math.atan2(-(tx - x), tz - z) * 180 / Math.PI);
    }

    public static int nodeMeta(RemasterSite site, JsonObject node, int authoredMeta) {
        if (node.has("facing") && !node.has("yaw")
            && node.get("facing")
                .isJsonPrimitive()
            && node.get("facing")
                .getAsJsonPrimitive()
                .isNumber())
            return node.get("facing")
                .getAsInt();
        // Node chests use the vanilla facing metadata consumed by the shared chest renderer.
        int direction = Math.floorMod((int) Math.floor(yaw(site, node) / 90D + .5D), 4);
        return new int[] { 3, 4, 2, 5 }[direction];
    }

    private static double value(JsonObject node, String key) {
        return node.has(key) ? node.get(key)
            .getAsDouble() : 0;
    }
}
