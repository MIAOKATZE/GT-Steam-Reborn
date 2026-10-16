package com.miaokatze.gtsr.client.critical;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.renderer.Tessellator;

import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;

/** Original native Sun family budgets; all physical sources consume the current installation pose. */
final class CriticalSunFX {

    static final int RING_IDLE = 48, RING_WORK = 72, LOCAL_LIMIT = 1024;
    private static final Map<CriticalGeometry.Model, Map<String, List<double[]>>> RINGS = new IdentityHashMap<>();

    private CriticalSunFX() {}

    static void clear() {
        RINGS.clear();
    }

    static void render(CriticalGeometry.Model sun, CriticalGeometry.Model loom, CriticalGeometry.Model base,
        double phase, double seconds, int lod, Set<String> visible, String sunSlug, String loomSlug, String baseSlug,
        Tessellator tessellator) {
        render(
            sun,
            loom,
            base,
            phase,
            seconds,
            lod,
            visible,
            sunSlug,
            loomSlug,
            baseSlug,
            phase > 0 && phase < 1 ? "working" : "idle",
            tessellator);
    }

    static void render(CriticalGeometry.Model sun, CriticalGeometry.Model loom, CriticalGeometry.Model base,
        double phase, double seconds, int lod, Set<String> visible, String sunSlug, String loomSlug, String baseSlug,
        String mode, Tessellator tessellator) {
        Map<String, List<double[]>> points = RINGS.computeIfAbsent(sun, model -> {
            Map<String, List<double[]>> parsed = new LinkedHashMap<>();
            for (int k = 0; k < 4; k++) parsed.put("confinement_" + k, new ArrayList<>());
            for (CriticalGeometry.Voxel voxel : model.voxels) {
                List<double[]> ring = parsed.get(voxel.group);
                if (ring != null) ring.add(new double[] { voxel.x, voxel.y, voxel.z });
            }
            return parsed;
        });
        int[] rings = { 0 };
        long before = CriticalRenderMetrics.particlesDrawn;
        CriticalDesignFX.sampleSun(
            new JsonObject[] { base.meta, loom.meta, sun.meta },
            new JsonObject[] { base.animations, loom.animations, sun.animations },
            new String[] { baseSlug, loomSlug, sunSlug },
            points,
            phase,
            seconds,
            mode,
            lod,
            visible,
            (family, source, index, center, color, alpha, glowAlpha, size) -> {
                if (!CriticalFXBudget.claim()) return;
                if ("ring".equals(family)) rings[0]++;
                CriticalFX.solarCube(tessellator, center, color, alpha * .62, size);
                CriticalFX.solarCube(tessellator, center, color, glowAlpha * .95, size * .56);
                CriticalFX.solarCube(tessellator, center, color, glowAlpha * .16, size * 2);
            });
        int ringCount = 0;
        for (int k = 0; k < 4; k++) if (sun.animations.has("confinement_" + k)
            && (visible == null || visible.contains(sunSlug + ":confinement_" + k))) ringCount++;
        CriticalRenderMetrics.lastSunParticles = CriticalRenderMetrics.particlesDrawn - before;
        CriticalRenderMetrics.lastSunRingParticles = rings[0];
        CriticalRenderMetrics.lastSunRingCount = ringCount;
        CriticalRenderMetrics.lastSunParticlesPerRing = ("working".equals(mode) ? RING_WORK : RING_IDLE) / lod;
        CriticalRenderMetrics.lastSunLod = lod;
    }
}
