package com.miaokatze.gtsr.common.dimension.prosperity.altar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-thread scheduler; the backend is also the seam for deterministic budget/cancellation checks. */
final class AltarSearchQueue {

    // Seven surrounding 512-block regions keep each coordinate axis within 4096 blocks
    // of any player in the shared origin region (including its opposite edge).
    static final int RADIUS = 7, MAX_CANDIDATES = 32, MAX_REGIONS = 64, MAX_FAILURES = 512;

    interface Candidates {

        int[] inRegion(int x, int z);
    }

    interface Backend {

        boolean hasTarget();

        boolean loaded(int x, int z);

        void load(int x, int z);

        boolean populated(int x, int z);

        boolean confirm(int x, int z);

        void release(int x, int z);
    }

    private final Candidates source;
    private final Map<Long, Search> regions = new LinkedHashMap<>(16, .75F, true);
    private final Map<Long, Boolean> failures = new LinkedHashMap<>(16, .75F, true);
    private Search active;
    private int[] candidate;
    private final boolean[] owned = new boolean[4];
    private int step;

    AltarSearchQueue(Candidates source) {
        this.source = source;
    }

    void beginTick() {
        for (Search search : regions.values()) search.requested = false;
    }

    void keepAlive(int regionX, int regionZ) {
        Search search = regions.get(key(regionX, regionZ));
        if (search != null) search.requested = true;
    }

    void request(int regionX, int regionZ) {
        long key = key(regionX, regionZ);
        Search search = regions.get(key);
        if (search == null) {
            // Never evict an active or currently requested search to admit more players.
            if (regions.size() >= MAX_REGIONS) {
                Iterator<Search> iterator = regions.values()
                    .iterator();
                boolean removed = false;
                while (iterator.hasNext()) {
                    Search old = iterator.next();
                    if (old != active && !old.requested) {
                        iterator.remove();
                        removed = true;
                        break;
                    }
                }
                if (!removed) return;
            }
            List<int[]> candidates = new ArrayList<>();
            for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int[] position = source.inRegion(regionX + dx, regionZ + dz);
                if (position != null && Math.abs((long) position[0]) < 1874998
                    && Math.abs((long) position[1]) < 1874998) candidates.add(position);
            }
            final long centerX = (long) regionX * 32 + 16, centerZ = (long) regionZ * 32 + 16;
            candidates.sort(Comparator.comparingLong(position -> distance(position, centerX, centerZ)));
            if (candidates.size() > MAX_CANDIDATES) candidates = new ArrayList<>(candidates.subList(0, MAX_CANDIDATES));
            search = new Search(candidates);
            regions.put(key, search);
        }
        search.requested = true;
    }

    /** Cancel immediately each tick; perform at most one chunk load when the shared budget permits. */
    void tick(Backend backend, boolean mayLoad) {
        if (active != null && (!active.requested || backend.hasTarget())) cleanup(backend);
        if (!mayLoad || backend.hasTarget()) return;
        if (active == null) {
            for (Search search : regions.values()) if (search.requested && search.cursor < search.candidates.size()) {
                active = search;
                break;
            }
        }
        if (active == null) return;
        if (candidate == null) {
            while (active.cursor < active.candidates.size()) {
                int[] next = active.candidates.get(active.cursor);
                if (!failures.containsKey(key(next[0], next[1]))) {
                    candidate = next;
                    break;
                }
                active.cursor++;
            }
            if (candidate == null) {
                active = null;
                return;
            }
        }
        int x = candidate[0] + (step & 1), z = candidate[1] + (step >> 1);
        owned[step] = !backend.loaded(x, z);
        // A loaded, already populated candidate can be recovered without touching neighbours.
        if (step == 0 && !owned[0] && backend.confirm(x, z)) {
            active.cursor++;
            cleanup(backend);
            return;
        }
        try {
            backend.load(x, z);
            step++;
            if (step == 4 || backend.populated(candidate[0], candidate[1])) {
                if (!backend.confirm(candidate[0], candidate[1])) rememberFailure(candidate);
                active.cursor++;
                cleanup(backend);
            }
        } catch (RuntimeException failure) {
            rememberFailure(candidate);
            active.cursor++;
            cleanup(backend);
            throw failure;
        }
    }

    private void cleanup(Backend backend) {
        if (candidate != null) for (int i = 0; i < 4; i++) {
            if (owned[i]) backend.release(candidate[0] + (i & 1), candidate[1] + (i >> 1));
            owned[i] = false;
        }
        candidate = null;
        step = 0;
        active = null;
    }

    private void rememberFailure(int[] position) {
        failures.put(key(position[0], position[1]), true);
        if (failures.size() > MAX_FAILURES) {
            Iterator<Long> iterator = failures.keySet()
                .iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static long key(int x, int z) {
        return (long) x << 32 | z & 0xffffffffL;
    }

    private static long distance(int[] position, long x, long z) {
        long dx = position[0] - x, dz = position[1] - z;
        return dx * dx + dz * dz;
    }

    private static final class Search {

        final List<int[]> candidates;
        int cursor;
        boolean requested;

        Search(List<int[]> candidates) {
            this.candidates = candidates;
        }
    }
}
