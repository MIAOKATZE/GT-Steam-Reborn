package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/** Finite numerical entrance contract. No voxel loader or entity is used while planning. */
public final class RemasterEntryApron {

    public static final int VERSION = 1, MAX_WRITES = 768;
    private static final int[][] DIR = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private RemasterEntryApron() {}

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static int at(JsonArray a, int i) {
        return a.get(i)
            .getAsInt();
    }

    private static final class Envelope {

        final RemasterSite s;
        final int ex, ez, fy;
        final String sha;
        final Map<String, Integer> cells = new HashMap<>();
        final int[] tops = new int[19 * 19], waters = new int[19 * 19];

        Envelope(RemasterSite site, JsonObject e) {
            s = site;
            JsonArray p = e.getAsJsonArray("entry");
            ex = at(p, 0);
            fy = at(p, 1);
            ez = at(p, 2);
            sha = e.get("sourceSha256")
                .getAsString();
            Arrays.fill(tops, Integer.MIN_VALUE);
            Arrays.fill(waters, Integer.MIN_VALUE);
            for (JsonElement row : e.getAsJsonArray("runs")) {
                JsonArray r = row.getAsJsonArray();
                for (int y = at(r, 2); y < at(r, 2) + at(r, 3); y++) cells.put(key(at(r, 0), y, at(r, 1)), at(r, 4));
            }
        }

        int kind(int x, int y, int z) {
            Integer k = cells.get(key(x, y, z));
            return k == null ? -1 : k;
        }

        int slot(int x, int z) {
            return (x - ex + 9) * 19 + (z - ez + 9);
        }

        int top(int x, int z) {
            int i = slot(x, z);
            if (tops[i] == Integer.MIN_VALUE)
                tops[i] = ProsperityTerrainProfile.heightAt(s.seed, s.x + x, s.z + z) - s.y;
            return tops[i];
        }

        boolean inside(int x, int z) {
            return Math.abs(x - ex) <= 8 && Math.abs(z - ez) <= 8 && writeInside(x, z);
        }

        boolean writeInside(int x, int z) {
            return Math.abs(x - ex) <= 9 && Math.abs(z - ez) <= 9
                && s.x + x >= s.minX()
                && s.x + x <= s.maxX()
                && s.z + z >= s.minZ()
                && s.z + z <= s.maxZ();
        }

        boolean dry(int x, int z) {
            int i = slot(x, z);
            if (waters[i] == Integer.MIN_VALUE)
                waters[i] = ChunkProviderProsperityRuins.naturalWaterTopAt(s.seed, s.x + x, s.z + z);
            return waters[i] < s.y + top(x, z);
        }

        boolean clear(int x, int y, int z) {
            for (int h = 1; h <= 3; h++) if (kind(x, y + h, z) > 0) return false;
            return true;
        }

        boolean platform(int x, int z) {
            return platform(x, fy, z);
        }

        boolean platform(int x, int fy, int z) {
            if (kind(x, fy, z) != 2 || !clear(x, fy, z)) return false;
            // Missing prefab cells retain natural geology; only authored AIR carves it.
            for (int h = 1; h <= 3; h++) if (top(x, z) >= fy + h && kind(x, fy + h, z) != 0) return false;
            return true;
        }

        boolean newFloor(int x, int y, int z) {
            if (!inside(x, z) || !dry(x, z)
                || !clear(x, y, z)
                || kind(x, y, z) != -1
                || top(x, z) > y
                || y - top(x, z) > 24
                || s.y + y < 1
                || s.y + y + 3 > 254) return false;
            for (int yy = top(x, z) + 1; yy < y; yy++) if (kind(x, yy, z) != -1) return false;
            return true;
        }

        boolean flat(int x, int z, int fy) {
            if (!newFloor(x, fy, z)) return false;
            if (fy - top(x, z) <= 2) return true;
            for (int[] d : DIR) {
                int xx = x + d[0], zz = z + d[1];
                if (!writeInside(xx, zz)) return false;
                if (platform(xx, fy, zz) || natural(xx, zz, fy) || kind(xx, fy + 1, zz) > 0) continue;
                if (kind(xx, fy, zz) != -1 || kind(xx, fy + 1, zz) != -1) return false;
            }
            return true;
        }

        boolean natural(int x, int z, int expected) {
            if (!inside(x, z) || top(x, z) != expected
                || !dry(x, z)
                || kind(x, expected, z) != -1
                || !clear(x, expected, z)) return false;
            // Explicit source AIR below the footing means the source excavated this column.
            for (int y = expected - 1; y <= expected + 2; y++) if (kind(x, y, z) != -1) return false;
            return true;
        }

        boolean ring(int x, int z) {
            int t = top(x, z);
            if (!natural(x, z, t)) return false;
            ArrayDeque<int[]> q = new ArrayDeque<>();
            Set<String> seen = new HashSet<>();
            q.add(new int[] { x, z });
            while (!q.isEmpty()) {
                int[] p = q.remove();
                String k = p[0] + ":" + p[1];
                if (!seen.add(k)) continue;
                if (Math.max(Math.abs(p[0] - ex), Math.abs(p[1] - ez)) == 8) return true;
                for (int[] d : DIR)
                    if (natural(p[0] + d[0], p[1] + d[1], t)) q.add(new int[] { p[0] + d[0], p[1] + d[1] });
            }
            return false;
        }
    }

    /** Null rejects a fresh eligible candidate; legacy constructors deliberately never call this. */
    public static RemasterSite admit(RemasterSite s) {
        if (!"prefab".equals(s.layout) && !"ordinary".equals(s.layout)) return s;
        JsonObject d = RemasterCatalog.descriptor(s.prefab, s.variant), p = d.getAsJsonObject("placement");
        if (p == null) return null;
        JsonObject terrain = p.getAsJsonObject("terrain");
        if (terrain != null && terrain.has("buried")
            && "full".equals(
                terrain.get("buried")
                    .getAsString()))
            return s;
        if (!p.has("entryApronEnvelope"))
            return p.has("surfaceEntrance") || p.has("productionSurfaceEntrance") ? null : s;
        JsonObject e = p.getAsJsonObject("entryApronEnvelope");
        if (e.get("version")
            .getAsInt() != VERSION
            || !e.get("sourceSha256")
                .getAsString()
                .equals(
                    d.get("sourceSha256")
                        .getAsString()))
            return null;
        Envelope env = new Envelope(s, e);
        if (!env.platform(env.ex, env.ez)) return null;
        ArrayDeque<List<int[]>> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        List<int[]> first = new ArrayList<>();
        first.add(new int[] { env.ex, env.fy, env.ez, 0 });
        queue.add(first);
        while (!queue.isEmpty()) {
            List<int[]> path = queue.remove();
            int[] end = path.get(path.size() - 1);
            String k = end[0] + ":" + end[1] + ":" + end[2];
            if (!seen.add(k)) continue;
            for (int[] dir : DIR) {
                int nx = end[0] + dir[0], nz = end[2] + dir[1];
                if (env.natural(nx, nz, end[1]) && env.ring(nx, nz)) {
                    RemasterSite r = finish(s, env, path, false, dir);
                    if (r != null) return r;
                }
                // Descending stairs have their high half facing the preceding platform.
                List<int[]> flight = new ArrayList<>(path);
                for (int n = 1; n <= 8 && flight.size() < 17; n++) {
                    int xx = end[0] + dir[0] * n, zz = end[2] + dir[1] * n, yy = end[1] - n + 1;
                    boolean repeated = false;
                    for (int[] a : flight) if (a[0] == xx && a[2] == zz) repeated = true;
                    if (repeated) break;
                    if (!env.newFloor(xx, yy, zz)) break;
                    flight.add(new int[] { xx, yy, zz, 1 });
                    int dx = xx + dir[0], dz = zz + dir[1];
                    if ((env.natural(dx, dz, yy - 1) || env.natural(dx, dz, yy)) && env.ring(dx, dz)) {
                        RemasterSite r = finish(s, env, flight, true, dir);
                        if (r != null) return r;
                    }
                }
                int sourceMeta = dir[0] == 1 ? 1 : dir[0] == -1 ? 0 : dir[1] == 1 ? 3 : 2;
                int landingX = nx + dir[0], landingZ = nz + dir[1];
                if (path.size() <= 15 && end[1] > env.fy - 8
                    && env.inside(nx, nz)
                    && env.inside(landingX, landingZ)
                    && env.kind(nx, end[1], nz) == 10 + sourceMeta
                    && env.clear(nx, end[1], nz)
                    && env.platform(landingX, end[1] - 1, landingZ)) {
                    boolean clearNative = true;
                    for (int h = 1; h <= 3; h++)
                        if (env.top(nx, nz) >= end[1] + h && env.kind(nx, end[1] + h, nz) != 0) clearNative = false;
                    if (clearNative) {
                        List<int[]> next = new ArrayList<>(path);
                        next.add(new int[] { nx, end[1], nz, 1 });
                        next.add(new int[] { landingX, end[1] - 1, landingZ, 0 });
                        queue.add(next);
                    }
                }
                if (path.size() < 17 && env.inside(nx, nz)
                    && (env.platform(nx, end[1], nz) || env.flat(nx, nz, end[1]))) {
                    List<int[]> next = new ArrayList<>(path);
                    next.add(new int[] { nx, end[1], nz, 0 });
                    queue.add(next);
                }
            }
        }
        return null;
    }

    private static RemasterSite finish(RemasterSite s, Envelope e, List<int[]> path, boolean stairs, int[] direction) {
        LinkedHashMap<String, int[]> writes = new LinkedHashMap<>();
        Set<String> floors = new HashSet<>();
        for (int[] a : path) floors.add(a[0] + ":" + a[2]);
        int stairMeta = direction[0] == 1 ? 1 : direction[0] == -1 ? 0 : direction[1] == 1 ? 3 : 2;
        for (int i = 0; i < path.size(); i++) {
            int[] a = path.get(i);
            if (e.kind(a[0], a[1], a[2]) == 2 || e.kind(a[0], a[1], a[2]) >= 10) continue;
            boolean stair = a[3] == 1;
            put(writes, a[0], a[1], a[2], stair ? 1 : 0, stair ? stairMeta : 0);
            for (int y = e.top(a[0], a[2]) + 1; y < a[1]; y++) put(writes, a[0], y, a[2], 0, 0);
            for (int h = 1; h <= 3; h++)
                if (e.kind(a[0], a[1] + h, a[2]) == -1) put(writes, a[0], a[1] + h, a[2], 2, 0);
            if (a[1] - e.top(a[0], a[2]) > 2) {
                for (int[] d : DIR) {
                    int x = a[0] + d[0], z = a[2] + d[1];
                    if (floors.contains(x + ":" + z)) continue;
                    if (stair && (d[0] * direction[0] + d[1] * direction[1]) != 0) continue;
                    if (!e.writeInside(x, z)) return null;
                    if (e.kind(x, a[1], z) == 2 && e.clear(x, a[1], z)) continue;
                    if (e.natural(x, z, a[1])) continue;
                    if (e.top(x, z) > a[1] && e.kind(x, a[1] + 1, z) == -1) continue;
                    if (e.kind(x, a[1] + 1, z) > 0) continue; // Existing wall already guards this side.
                    if (e.kind(x, a[1], z) != -1 || e.kind(x, a[1] + 1, z) != -1) return null;
                    put(writes, x, a[1], z, 0, 0);
                    put(writes, x, a[1] + 1, z, 3, 0);
                }
            }
        }
        if (writes.size() > MAX_WRITES) return null;
        int[] data = new int[writes.size() * 5];
        int n = 0;
        for (int[] row : writes.values()) for (int v : row) data[n++] = v;
        return new RemasterSite(
            s.prefab,
            s.variant,
            s.seed,
            s.x,
            s.y,
            s.z,
            s.layout,
            s.roadVersion,
            VERSION,
            e.sha,
            data);
    }

    private static void put(Map<String, int[]> m, int x, int y, int z, int type, int meta) {
        m.put(key(x, y, z), new int[] { x, y, z, type, meta });
    }

    public static boolean valid(RemasterSite s, RemasterPrefab p) {
        if (s.entryApronVersion == 0) return true;
        if (s.entryApronVersion != VERSION || s.entryApronWrites.length % 5 != 0
            || s.entryApronWrites.length > MAX_WRITES * 5
            || p == null
            || (!"prefab".equals(s.layout) && !"ordinary".equals(s.layout))
            || !s.entryApronSha.equals(
                RemasterCatalog.descriptor(s.prefab, s.variant)
                    .get("sourceSha256")
                    .getAsString()))
            return false;
        JsonObject e = p.metadata.getAsJsonObject("entryApronEnvelope");
        if (e == null || e.get("version")
            .getAsInt() != VERSION
            || !s.entryApronSha.equals(
                e.get("sourceSha256")
                    .getAsString())
            || !"gtsr:ruins_mossroot_paving".equals(
                e.get("floor")
                    .getAsString())
            || !"gtsr:ruins_mossroot_paving_stairs".equals(
                e.get("stairs")
                    .getAsString()))
            return false;
        Envelope env = new Envelope(s, e);
        Set<String> seen = new HashSet<>();
        Set<String> rails = new HashSet<>();
        for (int i = 0; i < s.entryApronWrites.length; i += 5) if (s.entryApronWrites[i + 3] == 3)
            rails.add(key(s.entryApronWrites[i], s.entryApronWrites[i + 1], s.entryApronWrites[i + 2]));
        for (int i = 0; i < s.entryApronWrites.length; i += 5) {
            int x = s.entryApronWrites[i], y = s.entryApronWrites[i + 1], z = s.entryApronWrites[i + 2],
                t = s.entryApronWrites[i + 3];
            if (!env.writeInside(x, z) || env.kind(x, y, z) != -1
                || y + s.y < 1
                || y + s.y > 254
                || t < 0
                || t > 3
                || s.entryApronWrites[i + 4] < 0
                || s.entryApronWrites[i + 4] > (t == 1 ? 3 : 0)
                || !seen.add(key(x, y, z))) return false;
            if (!env.inside(x, z) && t != 3 && (t != 0 || !rails.contains(key(x, y + 1, z)))) return false;
        }
        return true;
    }

    public static int geometry(RemasterSite s, RemasterPrefab p, BlockSink sink, int cx, int cz) {
        if (s.entryApronVersion == 0 || !valid(s, p)) return 0;
        JsonObject e = p.metadata.getAsJsonObject("entryApronEnvelope");
        int count = 0;
        for (int i = 0; i < s.entryApronWrites.length; i += 5) {
            int x = s.x + s.entryApronWrites[i], y = s.y + s.entryApronWrites[i + 1],
                z = s.z + s.entryApronWrites[i + 2], t = s.entryApronWrites[i + 3];
            if ((x >> 4) != cx || (z >> 4) != cz) continue;
            String b = t == 0 ? e.get("floor")
                .getAsString()
                : t == 1 ? e.get("stairs")
                    .getAsString() : t == 2 ? "minecraft:air" : "minecraft:fence";
            if (sink.setBlock(x, y, z, b, s.entryApronWrites[i + 4], 2)) count++;
        }
        return count;
    }
}
