package com.miaokatze.gtsr.common.critical;

/** Converts a complete frozen construction row or foundation phase to paid work per effective tick. */
final class CriticalConstructionDebugPolicy {

    static final int FAST_PHASE_TICKS = 30;

    private CriticalConstructionDebugPolicy() {}

    static int placementBudget(int positions) {
        return (int) Math.min(8192L, Math.max(128L, 2L * ((positions + 29L) / FAST_PHASE_TICKS)));
    }

    static int work(int baseline, int speed, int duration) {
        if (baseline <= 0 || duration <= 0) return 0;
        if (speed == 3) return (int) Math.max(1L, (long) duration / FAST_PHASE_TICKS);
        return (int) Math.min(Integer.MAX_VALUE, (long) baseline * (speed == 2 ? 10 : speed == 1 ? 2 : 1));
    }
}
