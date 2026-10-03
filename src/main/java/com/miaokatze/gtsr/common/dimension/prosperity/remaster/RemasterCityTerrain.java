package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;

import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/** Pure street surfaces; saved version-zero cities retain their released constant plane. */
public final class RemasterCityTerrain {

    private static final int WIDTH = 768, DEPTH = 384;
    private static final int[] DX = { 1, -1, 0, 0 }, DZ = { 0, 0, 1, -1 };
    // Eight immutable three-array fields occupy about 28 MiB; no World, NBT or entity is retained.
    private static final Map<String, Surface> SURFACES = new LinkedHashMap<String, Surface>(16, .75F, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Surface> entry) {
            return size() > 8;
        }
    };

    private RemasterCityTerrain() {}

    public static boolean contains(RemasterSite s, int x, int z) {
        int u = x - s.x, v = z - s.z;
        return u >= 0 && v >= 0
            && u < WIDTH
            && v < DEPTH
            && (u % 96 < 8 || v % 96 < 8 || u >= 680 && u < 760 && v >= 104 && v < 280);
    }

    /** Absolute-coordinate access to the street footprint; zero denotes a column outside that footprint. */
    public static final class Surface {

        private final int originX, originZ;
        private final int[][] natural = new int[WIDTH][DEPTH];
        private final int[][] top = new int[WIDTH][DEPTH];
        private final int[][] meta = new int[WIDTH][DEPTH];
        private Boolean viable;

        private Surface(RemasterSite s) {
            originX = s.x;
            originZ = s.z;
        }

        public int heightAt(int x, int z) {
            int u = x - originX, v = z - originZ;
            return u < 0 || v < 0 || u >= WIDTH || v >= DEPTH ? 0 : top[u][v];
        }

        public int naturalAt(int x, int z) {
            int u = x - originX, v = z - originZ;
            return u < 0 || v < 0 || u >= WIDTH || v >= DEPTH ? 0 : natural[u][v];
        }

        public int stairAt(int x, int z) {
            int u = x - originX, v = z - originZ;
            return u < 0 || v < 0 || u >= WIDTH || v >= DEPTH || top[u][v] == 0 ? -1 : meta[u][v];
        }
    }

    private static final class Level {

        private final int key, height;

        private Level(int key, int height) {
            this.key = key;
            this.height = height;
        }
    }

    /** Minimum natural-height majorant with one block of rise per two street edges. */
    public static synchronized Surface surface(RemasterSite s) {
        String key = s.id() + ":" + s.y + ":" + s.roadVersion;
        Surface cached = SURFACES.get(key);
        if (cached != null) return cached;
        Surface field = new Surface(s);
        int[][] level = new int[WIDTH][DEPTH];
        PriorityQueue<Level> queue = new PriorityQueue<>((a, b) -> Integer.compare(b.height, a.height));
        for (int u = 0; u < WIDTH; u++) for (int v = 0; v < DEPTH; v++) {
            if (!contains(s, s.x + u, s.z + v)) continue;
            field.natural[u][v] = ProsperityTerrainProfile.heightAt(s.seed, s.x + u, s.z + v);
            int water = ChunkProviderProsperityRuins.naturalWaterTopAt(s.seed, s.x + u, s.z + v);
            level[u][v] = Math.max(field.natural[u][v], water < 0 ? field.natural[u][v] : water + 1) * 2;
            queue.add(new Level(u * DEPTH + v, level[u][v]));
        }
        while (!queue.isEmpty()) {
            Level current = queue.remove();
            int u = current.key / DEPTH, v = current.key % DEPTH;
            if (level[u][v] != current.height) continue;
            for (int d = 0; d < 4; d++) {
                int x = u + DX[d], z = v + DZ[d];
                if (x < 0 || z < 0
                    || x >= WIDTH
                    || z >= DEPTH
                    || field.natural[x][z] == 0
                    || level[x][z] >= current.height - 1) continue;
                level[x][z] = current.height - 1;
                queue.add(new Level(x * DEPTH + z, level[x][z]));
            }
        }
        for (int u = 0; u < WIDTH; u++)
            for (int v = 0; v < DEPTH; v++) if (field.natural[u][v] > 0) field.top[u][v] = (level[u][v] + 1) / 2;
        for (int u = 0; u < WIDTH; u++) for (int v = 0; v < DEPTH; v++) {
            if (field.top[u][v] == 0) continue;
            int uphill = -1, downhill = 0;
            for (int d = 0; d < 4; d++) {
                int x = u + DX[d], z = v + DZ[d];
                if (x < 0 || z < 0 || x >= WIDTH || z >= DEPTH || field.top[x][z] == 0) continue;
                if (field.top[x][z] > field.top[u][v] && uphill < 0) uphill = d;
                if (field.top[x][z] < field.top[u][v]) downhill |= 1 << d;
            }
            // Different descent mouths let a narrow crest connect both sides through its level terrace.
            field.meta[u][v] = downhill == 0 ? uphill : descent(downhill, s.x + u, s.z + v);
        }
        SURFACES.put(key, field);
        return field;
    }

    private static int descent(int mask, int x, int z) {
        int pick = Math.floorMod(x + z, Integer.bitCount(mask));
        for (int d = 0; d < 4; d++) if ((mask & (1 << d)) != 0 && pick-- == 0) return d ^ 1;
        return -1;
    }

    public static int grade(RemasterSite s, int x, int z, int[][] natural, int originX, int originZ) {
        return s.roadVersion > 0 ? surface(s).heightAt(x, z) : s.y - 1;
    }

    public static int stair(RemasterSite s, int x, int z, int top, int[][] natural, int originX, int originZ) {
        return s.roadVersion > 0 ? surface(s).stairAt(x, z) : -1;
    }

    /** Each adjacent pair needs a reversible half-step route inside its own street or plaza. */
    public static boolean viable(RemasterSite s) {
        if (s.roadVersion == 0) return s.y - 1 >= 1 && s.y - 1 <= 254;
        Surface field = surface(s);
        synchronized (field) {
            if (field.viable == null) field.viable = connected(field);
            return field.viable;
        }
    }

    private static boolean connected(Surface field) {
        int[][] top = field.top, meta = field.meta;
        int[] seen = new int[WIDTH * DEPTH], queue = new int[WIDTH * DEPTH];
        int mark = 0;
        for (int u = 0; u < WIDTH; u++) for (int v = 0; v < DEPTH; v++) {
            if (top[u][v] == 0) continue;
            if (top[u][v] < 1 || top[u][v] > 254) return false;
            for (int d = 0; d < 4; d += 2) {
                int nu = u + DX[d], nv = v + DZ[d];
                if (nu >= WIDTH || nv >= DEPTH || top[nu][nv] == 0 || step(top, meta, u, v, nu, nv, d)) continue;
                int head = 0, tail = 0, target = nu * DEPTH + nv;
                queue[tail++] = u * DEPTH + v;
                seen[u * DEPTH + v] = ++mark;
                boolean reached = false;
                while (head < tail && !reached) {
                    int key = queue[head++], x = key / DEPTH, z = key % DEPTH;
                    for (int direction = 0; direction < 4; direction++) {
                        int nx = x + DX[direction], nz = z + DZ[direction];
                        if (nx < 0 || nz < 0
                            || nx >= WIDTH
                            || nz >= DEPTH
                            || top[nx][nz] == 0
                            || !corridor(u, v, nu, nv, nx, nz)
                            || !step(top, meta, x, z, nx, nz, direction)) continue;
                        int next = nx * DEPTH + nz;
                        if (seen[next] == mark) continue;
                        seen[next] = mark;
                        if (next == target) {
                            reached = true;
                            break;
                        }
                        queue[tail++] = next;
                    }
                }
                if (!reached) return false;
            }
        }
        return true;
    }

    // Real vanilla MC .6-wide/.5-step pair tests: equal heights, or the high stair facing uphill.
    private static boolean step(int[][] top, int[][] meta, int u, int v, int nu, int nv, int direction) {
        int delta = top[nu][nv] - top[u][v];
        if (delta == 0) return true;
        int uphill = delta > 0 ? direction : direction ^ 1;
        return Math.abs(delta) == 1 && (delta > 0 ? meta[nu][nv] : meta[u][v]) == uphill;
    }

    private static boolean corridor(int u, int v, int nu, int nv, int x, int z) {
        return Math.abs(x - u) <= 7 && Math.abs(z - v) <= 7
            || u % 96 < 8 && nu % 96 < 8 && u / 96 == nu / 96 && x / 96 == u / 96 && x % 96 < 8
            || v % 96 < 8 && nv % 96 < 8 && v / 96 == nv / 96 && z / 96 == v / 96 && z % 96 < 8
            || u >= 680 && nu >= 680
                && u < 760
                && nu < 760
                && v >= 104
                && nv >= 104
                && v < 280
                && nv < 280
                && x >= 680
                && x < 760
                && z >= 104
                && z < 280;
    }
}
