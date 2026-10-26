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
    private int cursor, count, credit, lastChecks;
    private boolean valid, roundClean = true;
    private boolean knownBroken;
    private boolean initialSweep = true;
    private Status status = Status.PENDING;
    private long completedRounds;

    public void reset() {
        cursor = count = credit = lastChecks = 0;
        valid = false;
        roundClean = true;
        knownBroken = false;
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

    /** Revokes the cached result without restarting the sweep at its first voxel. */
    public void invalidate() {
        valid = false;
        roundClean = false;
        knownBroken = true;
        status = Status.BROKEN;
    }

    public boolean tick(int total, IntPredicate matches) {
        return tickSamples(total, index -> matches.test(index) ? Sample.MATCH : Sample.BROKEN);
    }

    /** Missing chunks are unknown, never evidence of a missing block. Recheck the full round after IO. */
    public void pauseUnloaded() {
        if (status != Status.UNLOADED) {
            cursor = credit = 0;
            roundClean = true;
        }
        lastChecks = 0;
        valid = false;
        initialSweep = true;
        status = Status.UNLOADED;
    }

    public boolean tickSamples(int total, IntFunction<Sample> samples) {
        lastChecks = 0;
        if (total <= 0) {
            reset();
            return false;
        }
        if (count != total) {
            reset();
            count = total;
        }
        status = knownBroken ? Status.BROKEN : valid ? Status.VALID : Status.PENDING;
        // Recovery starts on the first ready tick. Only ongoing background checks use the long cadence.
        int budget;
        long deadline = 0L;
        if (initialSweep) {
            budget = MAX_INITIAL_CHECKS_PER_TICK;
            deadline = System.nanoTime() + INITIAL_SCAN_NANOS;
        } else {
            credit += count;
            budget = Math.min(MAX_CHECKS_PER_TICK, credit / ROUND_TICKS);
            credit %= ROUND_TICKS;
        }
        for (int i = 0; i < budget && cursor < count; i++) {
            Sample sample = samples.apply(cursor);
            if (sample == Sample.UNLOADED) {
                pauseUnloaded();
                return false;
            }
            cursor++;
            if (sample == Sample.BROKEN) invalidate();
            lastChecks++;
            if (initialSweep && (lastChecks & 31) == 0 && System.nanoTime() >= deadline) break;
        }
        if (cursor == count) {
            valid = roundClean;
            knownBroken = !roundClean;
            status = valid ? Status.VALID : Status.BROKEN;
            roundClean = true;
            cursor = credit = 0;
            completedRounds++;
            initialSweep = false;
        }
        return valid;
    }

    public Status getStatus() {
        return status;
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
