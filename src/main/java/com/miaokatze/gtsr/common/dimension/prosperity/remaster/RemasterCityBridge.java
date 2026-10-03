package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/** Immutable, owner-chunk bridge construction; never changes an authored plot anchor. */
public final class RemasterCityBridge {

    private static final int W = 768, D = 384, LIMIT = 56;
    private static final int[] DX = { 1, -1, 0, 0 }, DZ = { 0, 0, 1, -1 };
    private static final Map<String, Plan> CACHE = new LinkedHashMap<String, Plan>(8, .75f, true) {

        protected boolean removeEldestEntry(Map.Entry<String, Plan> e) {
            return size() > 4;
        }
    };

    private RemasterCityBridge() {}

    public static final class Connection {

        public final RemasterSite plot;
        /** Absolute x, floor y, z, stair metadata; entrance first, street last. */
        public final List<int[]> points;

        private Connection(RemasterSite p, List<int[]> path) {
            plot = p;
            List<int[]> copy = new ArrayList<>();
            for (int[] v : path) copy.add(v.clone());
            points = Collections.unmodifiableList(copy);
        }
    }

    private static final class Column {

        int floor, stair, guardHeight = 1;
        boolean walk, pier, source, deckOnly;

        Column(int h, int s, boolean w) {
            floor = h;
            stair = s;
            walk = w;
        }
    }

    private static final class Plan {

        final RemasterSite parent;
        final Map<Integer, Column> columns = new TreeMap<>();
        final List<Connection> paths = new ArrayList<>();
        final int[] nativeFloor = new int[W * D];
        final int[] protectedMin = new int[W * D], protectedMax = new int[W * D];
        boolean valid;
        int[] witness;
        String failure = "";

        Plan(RemasterSite s) {
            parent = s;
        }
    }

    public static boolean walkable(RemasterSite s, int x, int z) {
        int u = x - s.x, v = z - s.z;
        Plan p = plan(s);
        Column c = p.columns.get(u * D + v);
        return u >= 0 && v >= 0 && u < W && v < D && c != null && c.walk;
    }

    public static int floorAt(RemasterSite s, int x, int z) {
        Column c = plan(s).columns.get((x - s.x) * D + z - s.z);
        return c == null ? 0 : c.floor;
    }

    public static List<Connection> connections(RemasterSite s) {
        return Collections.unmodifiableList(plan(s).paths);
    }

    public static boolean viable(RemasterSite s) {
        return s.roadVersion == 0 || plan(s).valid;
    }

    public static String failure(RemasterSite s) {
        return plan(s).failure;
    }

    /** Absolute x, player/entity feet y, z. No new platform is introduced. */
    public static int[] witnessPosition(RemasterSite s) {
        if (s.roadVersion == 0) return new int[] { s.x + 724, s.y, s.z + 196 };
        int[] point = plan(s).witness;
        return point == null ? null : point.clone();
    }

    private static synchronized Plan plan(RemasterSite s) {
        String key = s.id() + ":" + s.y + ":" + s.roadVersion;
        Plan p = CACHE.get(key);
        if (p != null) return p;
        p = build(s);
        CACHE.put(key, p);
        return p;
    }

    private static boolean road(RemasterSite s, int x, int z) {
        return RemasterCityTerrain.contains(s, x, z);
    }

    private static boolean interior(RemasterSite s, int x, int z) {
        if (!road(s, x, z)) return false;
        for (int d = 0; d < 4; d++) if (!road(s, x + DX[d], z + DZ[d])) return false;
        return true;
    }

    private static int nativeFloor(RemasterSite s, int x, int z) {
        int n = ProsperityTerrainProfile.heightAt(s.seed, x, z);
        int water = ChunkProviderProsperityRuins.naturalWaterTopAt(s.seed, x, z);
        return Math.max(n, water < 0 ? n : water + 1);
    }

    private static Plan build(RemasterSite s) {
        Plan p = new Plan(s);
        if (s.roadVersion == 0) {
            p.failure = "version zero uses legacy owner";
            return p;
        }
        RemasterCityTerrain.Surface f = RemasterCityTerrain.surface(s);
        List<RemasterSite> plots = RemasterPlanner.savedCityPlots(s);
        if (plots.size() != 28) {
            p.failure = "missing source plots " + plots.size();
            return p;
        }
        for (RemasterSite plot : plots) {
            RemasterPrefab prefab = plot.plan();
            for (int cx = Math.floorDiv(prefab.min[0], 16); cx <= Math.floorDiv(prefab.max[0], 16); cx++)
                for (int cz = Math.floorDiv(prefab.min[2], 16); cz <= Math.floorDiv(prefab.max[2], 16); cz++)
                    for (RemasterPrefab.Run r : prefab.slice(cx, cz)) for (int i = 0; i < r.length; i++) {
                        int u = plot.x + r.x + i - s.x, v = plot.z + r.z - s.z, y = plot.y + r.y;
                        if (u < 0 || v < 0 || u >= W || v >= D) continue;
                        int k = u * D + v;
                        p.protectedMin[k] = p.protectedMin[k] == 0 ? y : Math.min(p.protectedMin[k], y);
                        p.protectedMax[k] = Math.max(p.protectedMax[k], y);
                    }
        }
        for (int u = 0; u < W; u++) for (int v = 0; v < D; v++) {
            int x = s.x + u, z = s.z + v;
            if (!road(s, x, z)) continue;
            int h = f.heightAt(x, z);
            if (h < 1 || h > 252) {
                p.failure = "bridge world-height";
                return p;
            }
            Column c = new Column(h, f.stairAt(x, z), interior(s, x, z));
            boolean vertical = u % 96 < 8, horizontal = v % 96 < 8;
            c.pier = vertical && v % 16 < 2 && (u % 96 < 2 || u % 96 >= 6)
                || horizontal && u % 16 < 2 && (v % 96 < 2 || v % 96 >= 6)
                || u % 16 >= 2 && u % 16 < 4 && v % 16 >= 2 && v % 16 < 4;
            if (h - f.naturalAt(x, z) > 3) {
                int axis = vertical && !horizontal ? 2 : 0;
                for (int d = axis; d < axis + 2; d++) {
                    int nx = x + DX[d], nz = z + DZ[d];
                    if (!road(s, nx, nz) || f.heightAt(nx, nz) - f.naturalAt(nx, nz) <= 3)
                        c.pier |= vertical ? u % 96 < 2 || u % 96 >= 6 : v % 96 < 2 || v % 96 >= 6;
                }
            }
            p.columns.put(u * D + v, c);
        }
        for (RemasterSite plot : plots) {
            List<int[]> path = search(p, plot, plots);
            if (path == null) {
                p.failure = "no bounded entrance route: " + plot.prefab + " " + p.failure;
                return p;
            }
            if (!install(p, plot, path)) {
                p.failure = "connector overlap: " + plot.prefab;
                return p;
            }
            p.paths.add(new Connection(plot, path));
        }
        repairStairMouths(p);
        for (Connection path : p.paths) for (int[] point : path.points) {
            Column c = boundedColumn(p, point[0] - s.x, point[2] - s.z);
            if (c != null) point[3] = c.stair;
        }
        List<Integer> displaced = new ArrayList<>();
        for (Map.Entry<Integer, Column> e : p.columns.entrySet()) {
            int key = e.getKey(), x = s.x + key / D, z = s.z + key % D;
            Column c = e.getValue();
            if (!c.walk) for (int direction = 0; direction < 4; direction++) {
                int u = x - s.x + DX[direction], v = z - s.z + DZ[direction];
                if (u < 0 || v < 0 || u >= W || v >= D) continue;
                Column neighbor = p.columns.get(u * D + v);
                if (neighbor != null && neighbor.walk)
                    c.guardHeight = Math.max(c.guardHeight, neighbor.floor - c.floor + 1);
            }
            if (c.floor + c.guardHeight > 254) {
                p.failure = "rail world height";
                return p;
            }
            int n = ProsperityTerrainProfile.heightAt(s.seed, x, z);
            if (!c.source && overlap(p, key, c.floor - 1, c.floor)) {
                p.failure = "deck intersects authored volume at " + x + "," + z;
                return p;
            }
            if (overlap(p, key, n, c.floor - 2)) {
                c.deckOnly = true;
                if (c.pier) {
                    c.pier = false;
                    displaced.add(key);
                }
            }
        }
        for (int blocked : displaced) if (!relocate(p, blocked)) {
            p.failure = "no protected-volume-safe pier near " + (s.x + blocked / D) + "," + (s.z + blocked % D);
            return p;
        }
        p.witness = witness(p);
        if (p.witness == null) {
            p.failure = "no existing five-by-five full plaza witness footing";
            return p;
        }
        p.valid = true;
        return p;
    }

    private static Column boundedColumn(Plan p, int u, int v) {
        return u >= 0 && v >= 0 && u < W && v < D ? p.columns.get(u * D + v) : null;
    }

    /** Repair non-source stair mouths after all entrance connectors have been installed. */
    private static void repairStairMouths(Plan p) {
        for (Map.Entry<Integer, Column> e : p.columns.entrySet()) {
            Column c = e.getValue();
            if (!c.walk || c.source || c.stair < 0) continue;
            int u = e.getKey() / D, v = e.getKey() % D, low = c.stair ^ 1;
            Column mouth = boundedColumn(p, u + DX[low], v + DZ[low]);
            if (mouth != null && mouth.walk) continue;
            int mask = 0;
            for (int d = 0; d < 4; d++) {
                Column neighbor = boundedColumn(p, u + DX[d], v + DZ[d]);
                if (neighbor != null && neighbor.walk && neighbor.floor == c.floor - 1) mask |= 1 << d;
            }
            if (mask == 0) continue;
            int pick = Math.floorMod(p.parent.x + u + p.parent.z + v, Integer.bitCount(mask));
            for (int d = 0; d < 4; d++) if ((mask & (1 << d)) != 0 && pick-- == 0) {
                c.stair = d ^ 1;
                break;
            }
        }
        for (Map.Entry<Integer, Column> e : p.columns.entrySet()) {
            Column c = e.getValue();
            if (!c.walk || c.source) continue;
            int u = e.getKey() / D, v = e.getKey() % D, choose = -1;
            boolean isolated = true;
            for (int d = 0; d < 4; d++) {
                int nu = u + DX[d], nv = v + DZ[d];
                Column neighbor = boundedColumn(p, nu, nv);
                if (neighbor == null || !neighbor.walk) continue;
                if (neighbor.floor <= c.floor || neighbor.floor == c.floor + 1 && neighbor.stair == d) {
                    isolated = false;
                    break;
                }
                if (neighbor.floor == c.floor + 1 && !neighbor.source
                    && choose < 0
                    && safeOldMouth(p, nu, nv, neighbor, d ^ 1)) choose = d;
            }
            if (isolated && choose >= 0) boundedColumn(p, u + DX[choose], v + DZ[choose]).stair = choose;
        }
    }

    /** Preserve another legal exit for the low mouth of a stair being redirected. */
    private static boolean safeOldMouth(Plan p, int u, int v, Column high, int newLowDirection) {
        if (high.stair < 0) return true;
        int old = high.stair ^ 1;
        if (old == newLowDirection) return true;
        int lu = u + DX[old], lv = v + DZ[old];
        Column mouth = boundedColumn(p, lu, lv);
        if (mouth == null || !mouth.walk) return true;
        for (int d = 0; d < 4; d++) {
            int nu = lu + DX[d], nv = lv + DZ[d];
            if (nu == u && nv == v) continue;
            Column exit = boundedColumn(p, nu, nv);
            if (exit == null || !exit.walk) continue;
            int delta = exit.floor - mouth.floor;
            if (delta == 0 || delta == 1 && exit.stair == d || delta == -1 && mouth.stair == (d ^ 1)) return true;
        }
        return false;
    }

    private static int[] witness(Plan p) {
        List<int[]> candidates = new ArrayList<>();
        for (int u = 682; u <= 757; u++) for (int v = 106; v <= 277; v++) candidates.add(new int[] { u, v });
        candidates.sort(
            Comparator.comparingInt((int[] a) -> (a[0] - 724) * (a[0] - 724) + (a[1] - 196) * (a[1] - 196))
                .thenComparingInt(a -> a[0])
                .thenComparingInt(a -> a[1]));
        for (int[] candidate : candidates) {
            Column center = p.columns.get(candidate[0] * D + candidate[1]);
            if (center == null || center.floor > 247) continue;
            boolean valid = true;
            for (int du = -2; du <= 2 && valid; du++) for (int dv = -2; dv <= 2; dv++) {
                int key = (candidate[0] + du) * D + candidate[1] + dv;
                Column c = p.columns.get(key);
                if (c == null || !c.walk || c.floor != center.floor || c.stair >= 0 || p.protectedMin[key] != 0) {
                    valid = false;
                    break;
                }
            }
            if (valid) return new int[] { p.parent.x + candidate[0], center.floor + 1, p.parent.z + candidate[1] };
        }
        return null;
    }

    private static boolean overlap(Plan p, int key, int min, int max) {
        return min <= max && p.protectedMin[key] != 0 && min <= p.protectedMax[key] && max >= p.protectedMin[key];
    }

    private static boolean relocate(Plan p, int origin) {
        int ox = origin / D, oz = origin % D;
        for (int radius = 0; radius <= 8; radius++)
            for (int du = -radius; du <= radius; du++) for (int dv = -radius; dv <= radius; dv++) {
                if (Math.abs(du) + Math.abs(dv) != radius) continue;
                int u = ox + du, v = oz + dv;
                boolean valid = true;
                for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) {
                    int xx = u + dx, zz = v + dz;
                    if (xx < 0 || zz < 0 || xx >= W || zz >= D) {
                        valid = false;
                        continue;
                    }
                    int key = xx * D + zz;
                    Column c = p.columns.get(key);
                    if (c == null || c.source || !road(p.parent, p.parent.x + xx, p.parent.z + zz)) {
                        valid = false;
                        continue;
                    }
                    int n = ProsperityTerrainProfile.heightAt(p.parent.seed, p.parent.x + xx, p.parent.z + zz);
                    if (overlap(p, key, n, c.floor - 2)) valid = false;
                }
                if (valid) {
                    for (int dx = 0; dx < 2; dx++)
                        for (int dz = 0; dz < 2; dz++) p.columns.get((u + dx) * D + v + dz).pier = true;
                    return true;
                }
            }
        return false;
    }

    private static final class Node {

        int x, z, h, direction, cost, estimate;
        boolean changed;
        Node previous;

        Node(int x, int z, int h, int d, boolean c, int n, Node parent, int estimate) {
            this.x = x;
            this.z = z;
            this.h = h;
            direction = d;
            changed = c;
            cost = n;
            previous = parent;
            this.estimate = estimate;
        }

        long key(int ox, int oz) {
            return (((((long) (x - ox) * 100 + (z - oz)) * 256 + h) * 5 + direction + 1) * 2) + (changed ? 1 : 0);
        }
    }

    private static int lowerBound(RemasterSite s, int x, int z) {
        int u = Math.floorMod(x - s.x, 96), v = Math.floorMod(z - s.z, 96);
        return Math.max(0, Math.min(Math.min(u - 7, 96 - u), Math.min(v - 7, 96 - v)));
    }

    private static int lowerBound(List<int[]> gates, int x, int z, int h) {
        int best = 1000;
        for (int[] gate : gates)
            best = Math.min(best, Math.max(Math.abs(x - gate[0]) + Math.abs(z - gate[2]), 2 * Math.abs(h - gate[1])));
        return best;
    }

    /** Cardinal, two-edge stairs; turns happen only on a level landing. */
    private static List<int[]> search(Plan p, RemasterSite plot, List<RemasterSite> plots) {
        RemasterSite city = p.parent;
        int ox = city.x + Math.floorDiv(plot.x - city.x, 96) * 96;
        int oz = city.z + Math.floorDiv(plot.z - city.z, 96) * 96;
        int ex = plot.entryX(), ez = plot.entryZ(), eh = plot.entryY() - 1;
        List<int[]> gates = new ArrayList<>();
        RemasterCityTerrain.Surface surface = RemasterCityTerrain.surface(city);
        for (int t = 8; t < 88; t++) for (int[] g : new int[][] { { ox + t, oz + 7 }, { ox + t, oz + 96 },
            { ox + 7, oz + t }, { ox + 96, oz + t } })
            if (road(city, g[0], g[1])) gates.add(new int[] { g[0], surface.heightAt(g[0], g[1]), g[1] });
        PriorityQueue<Node> open = new PriorityQueue<>(
            Comparator.comparingInt((Node n) -> n.estimate)
                .thenComparingInt(n -> n.cost)
                .thenComparingInt(n -> n.h));
        Map<Long, Integer> seen = new HashMap<>();
        Node start = new Node(ex, ez, eh, -1, false, 0, null, lowerBound(gates, ex, ez, eh));
        open.add(start);
        seen.put(start.key(ox, oz), 0);
        int expanded = 0, reached = 0, badShape = 0;
        while (!open.isEmpty() && expanded++ < 250000) {
            Node a = open.remove();
            if (seen.get(a.key(ox, oz)) != a.cost) continue;
            if (road(city, a.x, a.z) && a.cost > 0 && !a.changed) {
                Column road = p.columns.get((a.x - city.x) * D + a.z - city.z);
                if (road != null && road.floor == a.h) {
                    reached++;
                    int ix = a.x + DX[a.direction], iz = a.z + DZ[a.direction];
                    Column inward = p.columns.get((ix - city.x) * D + iz - city.z);
                    if (!interior(city, ix, iz) || inward == null || a.cost + 1 > LIMIT) continue;
                    int delta = inward.floor - a.h;
                    if (Math.abs(delta) > 1 || delta != 0
                        && (delta > 0 ? inward.stair : road.stair) != (delta > 0 ? a.direction : a.direction ^ 1))
                        continue;
                    List<int[]> result = new ArrayList<>();
                    for (Node n = a; n != null; n = n.previous) result.add(new int[] { n.x, n.h, n.z, -1 });
                    Collections.reverse(result);
                    for (int i = 1; i < result.size(); i++) {
                        int[] lo = result.get(i - 1), hi = result.get(i);
                        int d = direction(lo[0], lo[2], hi[0], hi[2]);
                        if (hi[1] > lo[1]) hi[3] = d;
                        if (hi[1] < lo[1]) lo[3] = d ^ 1;
                    }
                    result.get(result.size() - 1)[3] = road.stair;
                    result.add(new int[] { ix, inward.floor, iz, inward.stair });
                    if (shape(result)) return result;
                    badShape++;
                }
            }
            if (a.cost >= LIMIT) continue;
            for (int d = 0; d < 4; d++) {
                if (a.changed && d != a.direction) continue;
                int x = a.x + DX[d], z = a.z + DZ[d];
                if (x < ox + 1 || z < oz + 1 || x > ox + 97 || z > oz + 97 || x >= city.x + W || z >= city.z + D)
                    continue;
                for (int delta = -1; delta <= 1; delta++) {
                    if (delta != 0 && (a.changed || a.direction != -1 && a.direction != d)) continue;
                    if (a.changed && delta != 0) continue;
                    if (delta != 0 && a.previous != null && a.previous.direction != -1 && a.previous.direction != d)
                        continue;
                    if (d != a.direction && a.direction != -1
                        && a.previous != null
                        && a.previous.previous != null
                        && a.previous.previous.h != a.h) continue;
                    int h = a.h + delta;
                    // A height transition uses the higher column's stair; authored full paving cannot be rewritten.
                    if (delta < 0 && source(plot, a.x, a.h, a.z) != null || delta > 0 && source(plot, x, h, z) != null)
                        continue;
                    if (h < 1 || h > 252 || !clear(p, plot, plots, x, z, h, d)) continue;
                    if (road(city, x, z) && RemasterCityTerrain.surface(city)
                        .heightAt(x, z) != h) continue;
                    int cost = a.cost + 1, heuristic = lowerBound(gates, x, z, h);
                    if (cost + heuristic > LIMIT) continue;
                    Node n = new Node(x, z, h, d, delta != 0, cost, a, cost + heuristic);
                    long key = n.key(ox, oz);
                    Integer old = seen.get(key);
                    if (old != null && old <= cost) continue;
                    seen.put(key, cost);
                    open.add(n);
                }
            }
        }
        p.failure = "expanded=" + expanded + " reached=" + reached + " badShape=" + badShape;
        return null;
    }

    private static boolean shape(List<int[]> path) {
        Map<Long, Integer> floor = new HashMap<>();
        for (int i = 0; i < path.size(); i++) {
            int[] a = path.get(i);
            int d = i + 1 < path.size() ? direction(a[0], a[2], path.get(i + 1)[0], path.get(i + 1)[2])
                : direction(path.get(i - 1)[0], path.get(i - 1)[2], a[0], a[2]);
            for (int k = -2; k <= 2; k++) {
                int x = a[0] + (d < 2 ? 0 : k), z = a[2] + (d < 2 ? k : 0);
                long key = (long) x << 32 ^ (z & 0xffffffffL);
                Integer old = floor.put(key, a[1]);
                if (old != null && old != a[1]) return false;
            }
        }
        return true;
    }

    private static int direction(int x, int z, int nx, int nz) {
        return nx > x ? 0 : nx < x ? 1 : nz > z ? 2 : 3;
    }

    private static boolean clear(Plan p, RemasterSite plot, List<RemasterSite> plots, int x, int z, int h, int d) {
        RemasterSite city = p.parent;
        int sideX = d < 2 ? 0 : 1, sideZ = d < 2 ? 1 : 0;
        for (int k = -2; k <= 2; k++) {
            int xx = x + sideX * k, zz = z + sideZ * k;
            if (xx < city.x || zz < city.z || xx >= city.x + W || zz >= city.z + D) return false;
            for (RemasterSite other : plots)
                if (xx >= other.minX() && xx <= other.maxX() && zz >= other.minZ() && zz <= other.maxZ()) {
                    if (other != plot || zz > plot.entryZ() + 2) return false;
                    boolean authored = p.protectedMin[(xx - city.x) * D + zz - city.z] != 0;
                    if (k != 0 && authored) continue;
                    // Authored entrance floor can be reused, but no non-air source is replaced.
                    for (int y = h + 1; y <= h + 2; y++) if (source(plot, xx, y, zz) != null) return false;
                    String floor = source(plot, xx, h, zz);
                    if (authored
                        && (floor == null || !floor.startsWith("minecraft:stone") && !floor.contains("paving")))
                        return false;
                }
            if (!road(city, xx, zz)) {
                if (source(plot, xx, h, zz) != null) continue;
                int index = (xx - city.x) * D + zz - city.z;
                if (p.nativeFloor[index] == 0) p.nativeFloor[index] = nativeFloor(city, xx, zz);
                if (h < p.nativeFloor[index]) {
                    int water = ChunkProviderProsperityRuins.naturalWaterTopAt(city.seed, xx, zz);
                    // Only this explicitly owned dry approach may remove at most two native head blocks.
                    if (water >= h || p.nativeFloor[index] - h > 2) return false;
                }
            }
        }
        return true;
    }

    /** Non-air authored material at an exact column, using the prefab's immutable slices. */
    public static String source(RemasterSite plot, int x, int y, int z) {
        String key = authoredAt(plot, x, y, z);
        return key == null || key.startsWith("minecraft:air") ? null : key;
    }

    /** Includes explicit authored AIR, unlike the collision-oriented source() query. */
    public static String authoredAt(RemasterSite plot, int x, int y, int z) {
        RemasterPrefab f = plot.plan();
        int lx = x - plot.x, ly = y - plot.y, lz = z - plot.z;
        if (lx < f.min[0] || lx > f.max[0] || lz < f.min[2] || lz > f.max[2] || ly < f.min[1] || ly > f.max[1])
            return null;
        for (RemasterPrefab.Run r : f.slice(Math.floorDiv(lx, 16), Math.floorDiv(lz, 16)))
            if (r.y == ly && r.z == lz && lx >= r.x && lx < r.x + r.length) {
                return f.palette[r.paletteIndex];
            }
        return null;
    }

    private static boolean install(Plan p, RemasterSite plot, List<int[]> path) {
        for (int i = 0; i < path.size(); i++) {
            int[] a = path.get(i);
            int d = i + 1 < path.size() ? direction(a[0], a[2], path.get(i + 1)[0], path.get(i + 1)[2])
                : direction(path.get(i - 1)[0], path.get(i - 1)[2], a[0], a[2]);
            int sx = d < 2 ? 0 : 1, sz = d < 2 ? 1 : 0;
            for (int k = -2; k <= 2; k++) {
                int x = a[0] + sx * k, z = a[2] + sz * k, key = (x - p.parent.x) * D + z - p.parent.z;
                if (k != 0 && p.protectedMin[key] != 0
                    && x >= plot.minX()
                    && x <= plot.maxX()
                    && z >= plot.minZ()
                    && z <= plot.maxZ()) continue;
                boolean walk = Math.abs(k) <= 1;
                Column old = p.columns.get(key);
                if (road(p.parent, x, z)) {
                    if (walk && old != null) old.walk = true;
                    continue;
                }
                if (old != null && old.floor != a[1]) return false;
                Column c = new Column(a[1], walk ? a[3] : -1, walk);
                c.source = source(plot, x, a[1], z) != null;
                c.pier = Math.abs(k) == 2 && (i % 8 < 2 || i == 0 || i == path.size() - 1);
                if (old != null) {
                    c.walk |= old.walk;
                    c.pier |= old.pier;
                    c.source |= old.source;
                    if (old.walk) c.stair = old.stair;
                }
                p.columns.put(key, c);
            }
        }
        return true;
    }

    /** Emits only current bridges; callers keep version-zero geometry on the legacy path. */
    public static int geometry(RemasterSite s, BlockSink sink, int cx, int cz) {
        Plan p = plan(s);
        if (!p.valid) return 0;
        int writes = 0;
        for (Map.Entry<Integer, Column> e : p.columns.entrySet()) {
            int x = s.x + e.getKey() / D, z = s.z + e.getKey() % D;
            if ((x >> 4) != cx || (z >> 4) != cz) continue;
            Column c = e.getValue();
            int n = ProsperityTerrainProfile.heightAt(s.seed, x, z), h = c.floor;
            int start = !c.deckOnly && h - n <= 3 || c.pier ? n : h - 1;
            if (!c.source) for (int y = start; y < h; y++) {
                int key = e.getKey();
                if (p.protectedMin[key] != 0 && y >= p.protectedMin[key] && y <= p.protectedMax[key]) continue;
                if (sink.setBlock(x, y, z, "minecraft:stonebrick", 0, 2)) writes++;
            }
            if (!c.source && sink.setBlock(
                x,
                h,
                z,
                c.walk && c.stair >= 0 ? "minecraft:stone_brick_stairs" : "minecraft:stonebrick",
                c.walk && c.stair >= 0 ? c.stair : 0,
                2)) writes++;
            if (!c.source && !c.walk) for (int y = h + 1; y <= h + c.guardHeight; y++)
                if (!overlap(p, e.getKey(), y, y) && sink.setBlock(x, y, z, "minecraft:fence", 0, 2)) writes++;
            int head = 2;
            if (p.witness != null && Math.abs(x - p.witness[0]) <= 2 && Math.abs(z - p.witness[2]) <= 2) head = 7;
            if (c.walk) for (int y = h + 1; y <= h + head; y++)
                if (!overlap(p, e.getKey(), y, y) && sink.setBlock(x, y, z, "minecraft:air", 0, 2)) writes++;
        }
        return writes;
    }
}
