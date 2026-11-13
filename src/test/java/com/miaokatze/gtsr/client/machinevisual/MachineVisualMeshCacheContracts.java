// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.machinevisual;

import java.util.HashSet;
import java.util.Set;

/** Exercises native-list ownership and resampling policy without creating a GL context. */
public final class MachineVisualMeshCacheContracts {

    private static int checks;

    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }

    private static final class Driver implements MachineVisualMeshCache.Driver {

        int compiles, draws, deletes;
        boolean unavailable, failDelete;
        final Set<Integer> live = new HashSet<>();

        public int compile(Runnable commands) {
            if (unavailable) throw new IllegalStateException("no native allocation");
            commands.run();
            int id = ++compiles;
            live.add(id);
            return id;
        }

        public void draw(int list) {
            check(live.contains(list), "only live published lists replay");
            draws++;
        }

        public void delete(int list) {
            check(live.remove(list), "each list released once");
            deletes++;
            if (failDelete) throw new IllegalStateException("driver cleanup failure");
        }
    }

    public static void main(String[] args) {
        Driver driver = new Driver();
        MachineVisualMeshCache cache = new MachineVisualMeshCache(driver);
        int[] samples = { 0 };
        Runnable sample = () -> samples[0]++;
        cache.begin(0, 1);
        check(!cache.draw(0, 0, sample), "first admission samples native GT geometry");
        for (int frame = 0; frame < 400; frame++) {
            cache.begin(0, 1);
            check(cache.draw(0, 0, sample), "steady motion replays across frames and ticks");
        }
        check(samples[0] == 1 && driver.compiles == 1, "steady animation never periodically rebakes");
        check(!cache.draw(1, 0, sample) && samples[0] == 2, "rigid groups retain distinct meshes");
        check(!cache.draw(0, 1, sample), "native visibility revision replaces changed group");
        check(cache.draw(1, 0, sample), "another group's upload never recompiles unchanged geometry");
        cache.begin(1, 1);
        check(!cache.draw(0, 1, sample), "second render pass has an independent native capture");
        int compiled = driver.compiles;
        cache.begin(0, 1);
        check(
            cache.draw(0, 1, sample) && driver.compiles == compiled,
            "alternating shader passes retain both captures");
        cache.invalidate(0);
        check(driver.live.size() == 1 && cache.draw(1, 0, sample), "restoration invalidates this group in all passes");
        cache.begin(0, 0);
        check(driver.live.isEmpty(), "AO changes invalidate native lighting");
        cache.draw(0, 2, sample);
        cache.clear();
        cache.clear();
        check(driver.live.isEmpty(), "reload/retirement/world exit cleanup is idempotent");

        Driver unavailable = new Driver();
        unavailable.unavailable = true;
        MachineVisualMeshCache fallback = new MachineVisualMeshCache(unavailable);
        int before = samples[0];
        fallback.draw(0, 0, sample);
        fallback.draw(0, 0, sample);
        check(samples[0] == before + 2 && unavailable.draws == 0, "unsupported allocation keeps immediate rendering");

        Driver failed = new Driver();
        MachineVisualMeshCache broken = new MachineVisualMeshCache(failed);
        try {
            broken.draw(0, 0, () -> { throw new IllegalArgumentException("native draw failure"); });
            throw new AssertionError("native failure swallowed");
        } catch (IllegalArgumentException expected) {
            check(failed.live.isEmpty(), "failed native batch is never published or replayed");
        }
        broken.draw(0, 0, sample);
        broken.draw(1, 0, sample);
        failed.failDelete = true;
        try {
            broken.clear();
            throw new AssertionError("cleanup failure swallowed");
        } catch (IllegalStateException expected) {
            check(expected.getSuppressed().length == 1 && failed.live.isEmpty(), "cleanup attempts all owned lists");
        }
        broken.clear();
        System.out.println("PASS MachineVisualMeshCache contracts: " + checks);
        MachineVisualEntranceContracts.main(args);
    }
}
