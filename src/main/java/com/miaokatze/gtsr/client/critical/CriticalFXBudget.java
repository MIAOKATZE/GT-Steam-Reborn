package com.miaokatze.gtsr.client.critical;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** One bounded additive cube stream across all critical anchors in a rendered client frame. */
public final class CriticalFXBudget {

    static final int GLOBAL_LIMIT = 1536;
    private static int remaining = GLOBAL_LIMIT;
    private static boolean registered;

    private CriticalFXBudget() {}

    static void register() {
        if (registered) return;
        registered = true;
        FMLCommonHandler.instance()
            .bus()
            .register(new CriticalFXBudget());
    }

    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        remaining = GLOBAL_LIMIT;
        CriticalRenderMetrics.frameStarts++;
    }

    static int lod(double distanceSquared, int particleSetting) {
        if (particleSetting >= 2 || distanceSquared >= 192D * 192D) return 0;
        int factor = distanceSquared >= 160D * 160D ? 4 : distanceSquared >= 96D * 96D ? 2 : 1;
        return particleSetting == 1 ? Math.min(4, factor * 2) : factor;
    }

    static boolean full() {
        return remaining == 0;
    }

    static CriticalDesignFX.ParticleSink designSink(CriticalDesignFX.ParticleSink draw) {
        return new CriticalDesignFX.ParticleSink() {

            @Override
            public boolean full() {
                return CriticalFXBudget.full();
            }

            @Override
            public void emit(String family, String source, int index, double[] center, double[] color, double alpha,
                double glowAlpha, double size) {
                draw.emit(family, source, index, center, color, alpha, glowAlpha, size);
            }
        };
    }

    static CriticalSolarFX.ParticleSink solarSink(CriticalSolarFX.ParticleSink draw) {
        return new CriticalSolarFX.ParticleSink() {

            @Override
            public boolean full() {
                return CriticalFXBudget.full();
            }

            @Override
            public void emit(String family, int index, double[] center, double[] color, double alpha,
                double[] glowColor, double glowAlpha, double size) {
                draw.emit(family, index, center, color, alpha, glowColor, glowAlpha, size);
            }
        };
    }

    static boolean claim() {
        if (remaining == 0) {
            CriticalRenderMetrics.particleBudgetSkips++;
            return false;
        }
        remaining--;
        CriticalRenderMetrics.particlesDrawn++;
        return true;
    }
}
