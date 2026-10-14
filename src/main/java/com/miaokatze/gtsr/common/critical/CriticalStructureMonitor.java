package com.miaokatze.gtsr.common.critical;

import java.util.function.IntPredicate;

/** Server-thread incremental validation. A failed round cannot restore validity midway through repairs. */
public final class CriticalStructureMonitor {

    public static final int ROUND_TICKS = 6000;
    public static final int MAX_CHECKS_PER_TICK = 128;
    private int cursor, count, credit, lastChecks;
    private boolean valid, roundClean = true;
    private long completedRounds;

    public void reset() {
        cursor = count = credit = lastChecks = 0;
        valid = false;
        roundClean = true;
    }

    /** Trust only a just-finished paid construction; the next ordinary sweep starts at zero. */
    void primeConstruction(int total) {
        reset();
        completedRounds = 0;
        count = total;
        valid = total > 0;
    }

    /** Revokes the cached result without restarting the sweep at its first voxel. */
    public void invalidate() {
        valid = false;
        roundClean = false;
    }

    public boolean tick(int total, IntPredicate matches) {
        lastChecks = 0;
        if (total <= 0) {
            reset();
            return false;
        }
        if (count != total) {
            reset();
            count = total;
        }
        // Distribute every position over exactly 6000 ticks, including structures smaller than 6000.
        credit += count;
        int budget = Math.min(MAX_CHECKS_PER_TICK, credit / ROUND_TICKS);
        credit %= ROUND_TICKS;
        for (int i = 0; i < budget && cursor < count; i++) {
            if (!matches.test(cursor++)) invalidate();
            lastChecks++;
        }
        if (cursor == count) {
            valid = roundClean;
            roundClean = true;
            cursor = credit = 0;
            completedRounds++;
        }
        return valid;
    }

    public int getLastChecks() {
        return lastChecks;
    }

    public long getCompletedRounds() {
        return completedRounds;
    }
}
