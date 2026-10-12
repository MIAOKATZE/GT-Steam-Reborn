package com.miaokatze.gtsr.client.critical;

/** Client-thread cumulative counters; the runtime probe samples differences, never inferred FPS. */
public final class CriticalRenderMetrics {

    public static long frameStarts, renderCalls, meshCompiles, displayListDraws, nativeLightQueries, particlesDrawn;
    public static long particleBudgetSkips, particleLodSkips, lastSunParticles, lastSunRingParticles;
    public static long lastSunRingCount, lastSunParticlesPerRing, lastSunLod, lastWorldLightSamples;
    /** Client renderers never request world propagation; material placement owns native light updates. */
    public static final long relightRequests = 0;

    private CriticalRenderMetrics() {}
}
