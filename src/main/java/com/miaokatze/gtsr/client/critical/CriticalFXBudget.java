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
