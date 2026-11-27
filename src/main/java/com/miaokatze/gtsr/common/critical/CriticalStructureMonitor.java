// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.critical;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/** Server-thread incremental validation. A failed round cannot restore validity midway through repairs. */
public final class CriticalStructureMonitor {

    public enum Sample {
        MATCH,
        BROKEN,
        UNLOADED
    }

    public enum Status {
        PENDING,
        VALID,
        BROKEN,
        UNLOADED
    }

    public static final int ROUND_TICKS = 6000;
    public static final int MAX_CHECKS_PER_TICK = 128;
    public static final int MAX_INITIAL_CHECKS_PER_TICK = 1024;
    public static final long INITIAL_SCAN_NANOS = 2_000_000L;
    private int cursor, count, lastChecks;
    private long credit;
    private boolean valid, roundClean = true;
    private boolean knownBroken;
    private boolean roundUnloaded;
    private boolean initialSweep = true;
    private Status status = Status.PENDING;
    private long completedRounds;

    public void reset() {
        cursor = count = lastChecks = 0;
        credit = 0;
        valid = false;
        roundClean = true;
        knownBroken = false;
        roundUnloaded = false;
        initialSweep = true;
        status = Status.PENDING;
    }

    /** Trust only a just-finished paid construction; the next ordinary sweep starts at zero. */
    void primeConstruction(int total) {
        reset();
        completedRounds = 0;
        count = total;
        valid = total > 0;
        initialSweep = false;
        status = valid ? Status.VALID : Status.PENDING;
    }

    /** Resume a previously verified shape and immediately begin its background recheck. */
    public void restoreValidation(int total) {
        primeConstruction(total);
        requestFastSweep();
    }

    /** Revokes the cached result without restarting the sweep at its first voxel. */
    public void invalidate() {
        valid = false;
        roundClean = false;
        knownBroken = true;
        status = Status.BROKEN;
    }

    /** A diagnostic sweep keeps the current verdict until a real mismatch or a complete round replaces it. */
    public void requestFastSweep() {
        cursor = lastChecks = 0;
        credit = 0;
        roundClean = true;
        roundUnloaded = false;
        initialSweep = true;
    }

    public boolean tick(int total, IntPredicate matches) {
        return tickSamples(total, index -> matches.test(index) ? Sample.MATCH : Sample.BROKEN);
    }

    /** Missing chunks are unknown, never evidence of a missing block. Recheck the full round after IO. */
    public void pauseUnloaded() {
        if (status != Status.UNLOADED) {
            cursor = 0;
            credit = 0;
            roundClean = true;
            roundUnloaded = false;
        }
        lastChecks = 0;
        valid = false;
        initialSweep = true;
        status = knownBroken ? Status.BROKEN : Status.UNLOADED;
    }

    public boolean tickSamples(int total, IntFunction<Sample> samples) {
        return tickSamples(total, 1, samples);
    }

    /** A multiplexing caller reports how often the monitor receives a server-thread slice. */
    public boolean tickSamples(int total, int elapsedTicks, IntFunction<Sample> samples) {
        return tickSamples(total, elapsedTicks, Integer.MAX_VALUE, samples);
    }

    /** Multiplexed structures may share one per-tick sampling limit without discarding earned checks. */
    public boolean tickSamples(int total, int elapsedTicks, int maxChecks, IntFunction<Sample> samples) {
        lastChecks = 0;
        if (total <= 0) {
            reset();
            return false;
        }
        if (count != total) {
            reset();
            count = total;
        }
        status = knownBroken ? Status.BROKEN : roundUnloaded ? Status.UNLOADED : valid ? Status.VALID : Status.PENDING;
        // Recovery starts on the first ready tick. Only ongoing background checks use the long cadence.
        int budget;
        long deadline = 0L;
        if (initialSweep) {
            budget = Math.min(MAX_INITIAL_CHECKS_PER_TICK, Math.max(0, maxChecks));
            deadline = System.nanoTime() + INITIAL_SCAN_NANOS;
        } else {
            // At most one round of debt survives a delayed caller; arithmetic also supports huge shapes.
            credit = Math
                .min((long) count * ROUND_TICKS, credit + (long) count * Math.min(64, Math.max(1, elapsedTicks)));
            budget = (int) Math.min(Math.min(MAX_CHECKS_PER_TICK, Math.max(0, maxChecks)), credit / ROUND_TICKS);
            credit -= (long) budget * ROUND_TICKS;
        }
        for (int i = 0; i < budget && cursor < count; i++) {
            Sample sample = samples.apply(cursor);
            cursor++;
            lastChecks++;
            if (sample == Sample.UNLOADED) {
                // Keep visiting loaded cells: an early unknown chunk must not hide later real damage.
                roundUnloaded = true;
                valid = false;
                initialSweep = true;
                status = knownBroken ? Status.BROKEN : Status.UNLOADED;
            }
            if (sample == Sample.BROKEN) invalidate();
            if (initialSweep && (lastChecks & 31) == 0 && System.nanoTime() >= deadline) break;
        }
        if (cursor == count) {
            valid = roundClean && !roundUnloaded;
            knownBroken = !roundClean || roundUnloaded && knownBroken;
            status = knownBroken ? Status.BROKEN : roundUnloaded ? Status.UNLOADED : Status.VALID;
            roundClean = true;
            cursor = 0;
            credit = 0;
            completedRounds++;
            initialSweep = roundUnloaded;
            roundUnloaded = false;
        }
        return valid;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isFastSweep() {
        return initialSweep;
    }

    public boolean hasKnownDamage() {
        return knownBroken;
    }

    public int getLastChecks() {
        return lastChecks;
    }

    public long getCompletedRounds() {
        return completedRounds;
    }
}
