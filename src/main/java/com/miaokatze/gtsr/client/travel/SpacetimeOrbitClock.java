package com.miaokatze.gtsr.client.travel;

/** Integrates angular speed instead of multiplying absolute time by a changing speed. */
public final class SpacetimeOrbitClock {

    private final double receivedAt, preheat, phaseAtReceipt;

    public SpacetimeOrbitClock(double tick, int preheatTicks, SpacetimeOrbitClock previous) {
        receivedAt = tick;
        preheat = Math.max(0, Math.min(600, preheatTicks));
        phaseAtReceipt = previous == null ? 0 : previous.phase(tick);
    }

    public double phase(double tick) {
        double elapsed = Math.max(0, tick - receivedAt);
        double warming = Math.min(elapsed, 600 - preheat);
        double integrated = warming * (1 + 9 * preheat / 600) + 9 * warming * warming / 1200;
        return phaseAtReceipt + .012 * (integrated + (elapsed - warming) * 10);
    }

    public double speed(double tick) {
        return 1 + 9 * Math.min(600, preheat + Math.max(0, tick - receivedAt)) / 600;
    }
}
