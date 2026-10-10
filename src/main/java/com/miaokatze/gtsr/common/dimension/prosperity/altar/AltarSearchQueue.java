package com.miaokatze.gtsr.common.dimension.prosperity.altar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded server-thread classification cache. Discovery never loads or generates terrain. */
final class AltarSearchQueue {

    static final int RADIUS = 7, MAX_RADIUS = 64, MAX_CANDIDATES = 512, MAX_REGIONS = 64;
    static final int UNKNOWN = -1, REJECTED = 0, PLANNED = 1, ACTUAL = 2;
    private static final long RETRY_TICKS = 40, CACHE_TICKS = 1200;

    interface Candidates {

        int[] inRegion(int x, int z);
    }

    interface Backend {

        /** Without disk permission only inspect loaded chunks and persistent rejections. */
        int classify(int x, int z, boolean allowDisk);
    }

    private final Candidates source;
    private final Map<Long, Search> regions = new LinkedHashMap<>(16, .75F, true);
    private long ticks;
    private long lastSearch;

    AltarSearchQueue(Candidates source) {
        this.source = source;
    }

    void beginTick() {
        ticks++;
        for (Search search : regions.values()) search.origins.clear();
    }

    void keepAlive(double x, double z) {
        Search search = regions.get(key(region(x), region(z)));
        if (search != null) search.origins.add(new double[] { x, z });
    }

    void request(double x, double z) {
        int rx = region(x), rz = region(z);
        long key = key(rx, rz);
        Search search = regions.get(key);
        if (search == null) {
            if (regions.size() >= MAX_REGIONS) {
                Iterator<Search> iterator = regions.values()
                    .iterator();
                boolean removed = false;
                while (iterator.hasNext()) if (iterator.next().origins.isEmpty()) {
                    iterator.remove();
                    removed = true;
                    break;
                }
                if (!removed) return;
            }
            search = new Search();
            search.regionX = rx;
            search.regionZ = rz;
            for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int[] p = source.inRegion(rx + dx, rz + dz);
                // Keep the full structure footprint inside vanilla's world limits.
                if (p != null && Math.abs((long) p[0]) < 1874998 && Math.abs((long) p[1]) < 1874998)
                    search.candidates.add(new Candidate(p[0], p[1]));
            }
            regions.put(key, search);
        }
        search.origins.add(new double[] { x, z });
    }

    int[] nearest(double x, double z, Backend backend) {
        Search search = regions.get(key(region(x), region(z)));
        if (search == null) return null;
        Candidate nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Candidate candidate : search.candidates) {
            if (candidate.status != PLANNED && candidate.status != ACTUAL) continue;
            int live = backend.classify(candidate.x, candidate.z, false);
            if (live != UNKNOWN) record(candidate, live);
            if (candidate.status != PLANNED || ticks - candidate.checked >= CACHE_TICKS) continue;
            double current = distance(candidate, x, z);
            if (current < distance) {
                nearest = candidate;
                distance = current;
            }
        }
        return nearest == null ? null : new int[] { nearest.x * 16 + 7, nearest.z * 16 + 7 };
    }

    /** At most one disk probe per server tick, shared by every player and region. */
    void tick(Backend backend, boolean mayProbe) {
        if (!mayProbe) return;
        List<Map.Entry<Long, Search>> requested = new ArrayList<>();
        for (Map.Entry<Long, Search> entry : regions.entrySet())
            if (!entry.getValue().origins.isEmpty()) requested.add(entry);
        if (requested.isEmpty()) return;
        int start = 0;
        for (int i = 0; i < requested.size(); i++) if (requested.get(i)
            .getKey() == lastSearch) {
                start = (i + 1) % requested.size();
                break;
            }
        for (int i = 0; i < requested.size(); i++) {
            Map.Entry<Long, Search> entry = requested.get((start + i) % requested.size());
            expand(entry.getValue());
            Candidate next = null;
            double distance = Double.POSITIVE_INFINITY;
            for (Candidate candidate : entry.getValue().candidates) {
                long age = ticks - candidate.checked;
                if (candidate.status == REJECTED || (candidate.checked != Long.MIN_VALUE
                    && age < (candidate.status == UNKNOWN ? RETRY_TICKS : CACHE_TICKS))) continue;
                for (double[] origin : entry.getValue().origins) {
                    double current = distance(candidate, origin[0], origin[1]);
                    if (current < distance) {
                        next = candidate;
                        distance = current;
                    }
                }
            }
            if (next == null) continue;
            record(next, backend.classify(next.x, next.z, true));
            lastSearch = entry.getKey();
            return;
        }
    }

    private void expand(Search search) {
        if (search.radius >= MAX_RADIUS) return;
        for (Candidate candidate : search.candidates)
            if (candidate.checked == Long.MIN_VALUE || candidate.status == PLANNED || candidate.status == ACTUAL)
                return;
        search.candidates.removeIf(candidate -> candidate.status == REJECTED);
        // Every completed ring advances once; transient UNKNOWN entries retain their retry budget.
        if (search.candidates.size() + (search.radius + 1) * 8 > MAX_CANDIDATES) return;
        int radius = ++search.radius;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
            int[] p = source.inRegion(search.regionX + dx, search.regionZ + dz);
            if (p != null && Math.abs((long) p[0]) < 1874998 && Math.abs((long) p[1]) < 1874998)
                search.candidates.add(new Candidate(p[0], p[1]));
        }
    }

    private void record(Candidate candidate, int status) {
        candidate.status = status;
        candidate.checked = ticks;
    }

    private static double distance(Candidate candidate, double x, double z) {
        double dx = candidate.x * 16.0 + 7 - x, dz = candidate.z * 16.0 + 7 - z;
        return dx * dx + dz * dz;
    }

    private static int region(double coordinate) {
        return Math.floorDiv((int) Math.floor(coordinate), 512);
    }

    private static long key(int x, int z) {
        return (long) x << 32 | z & 0xffffffffL;
    }

    private static final class Candidate {

        final int x, z;
        int status = UNKNOWN;
        long checked = Long.MIN_VALUE;

        Candidate(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    private static final class Search {

        final List<Candidate> candidates = new ArrayList<>();
        final List<double[]> origins = new ArrayList<>();
        int regionX, regionZ, radius = RADIUS;
    }
}
