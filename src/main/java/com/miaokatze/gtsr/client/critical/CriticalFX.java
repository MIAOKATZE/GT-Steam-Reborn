package com.miaokatze.gtsr.client.critical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;

/** Bounded visual particles follow the design's actual ports and animated group transforms. */
final class CriticalFX {

    private static final Map<String, List<Source>> SOURCES = new HashMap<>();
    private static final Map<String, List<double[]>> SURFACES = new HashMap<>();
    private static final String[] SLUGS = { "solar", "turbine", "processing", "entangler", "sun", "dimension",
        "battery", "assembly", "accelerator" };
    private static final double[] SIZES = { .52, .62, .72, .5, .62, .52, .72, .68, .8 };
    private static final float[][] COLORS = { { .58F, .84F, 1 }, { .18F, .70F, 1 }, { .6F, 1, .8F }, { .8F, .68F, 1 },
        { 1, .78F, .42F }, { .7F, .78F, 1 }, { .6F, 1, .9F }, { 1, .85F, .55F }, { .12F, .48F, 1 } };
    private static final double[][][] FACES = {
        { { -.5, -.5, .5 }, { -.5, -.5, -.5 }, { .5, -.5, -.5 }, { .5, -.5, .5 } },
        { { -.5, .5, -.5 }, { -.5, .5, .5 }, { .5, .5, .5 }, { .5, .5, -.5 } },
        { { .5, .5, -.5 }, { .5, -.5, -.5 }, { -.5, -.5, -.5 }, { -.5, .5, -.5 } },
        { { -.5, .5, .5 }, { -.5, -.5, .5 }, { .5, -.5, .5 }, { .5, .5, .5 } },
        { { -.5, .5, -.5 }, { -.5, -.5, -.5 }, { -.5, -.5, .5 }, { -.5, .5, .5 } },
        { { .5, .5, .5 }, { .5, -.5, .5 }, { .5, -.5, -.5 }, { .5, .5, -.5 } } };

    private CriticalFX() {}

    static void render(String slug, CriticalGeometry.Model model, double phase, double seconds) {
        int profile = 0;
        while (profile < SLUGS.length && !SLUGS[profile].equals(slug)) profile++;
        if (profile == SLUGS.length) return;
        List<Source> sources = SOURCES.computeIfAbsent(slug, ignored -> parse(model));
        if (sources.isEmpty()) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setBrightness(0x00f000f0);
        for (int layer = 0; layer < 2; layer++) {
            int budget = layer == 0 ? 192 : phase > 0 && phase < 1 ? 1024 : 0;
            if (layer == 1 && slug.equals("sun")) {
                renderSun(model, phase, seconds, t);
                continue;
            }
            for (int si = 0; si < sources.size(); si++) {
                Source source = sources.get(si);
                if (layer == 1 && (phase <= source.lo || phase >= source.hi)) continue;
                int count = budget / sources.size() + (si < budget % sources.size() ? 1 : 0);
                for (int i = 0; i < count; i++) {
                    double local = (phase - source.lo) / (source.hi - source.lo);
                    double travel = wrap(
                        layer == 0 ? seconds * .045 + i / (double) Math.max(1, count)
                            : local * (1.9 + (profile + 1) * .06) + i / (double) Math.max(1, count));
                    if (slug.equals("entangler") && si % 2 == 1) travel = 1 - travel;
                    double q = travel * (source.route.length - 1);
                    int segment = Math.min(source.route.length - 2, (int) q);
                    double theta = i * 2.399963 + si * .73 + seconds * (.23 + (profile + 1) * .015);
                    double[] a = source.route[segment], b = source.route[segment + 1], pos = new double[3];
                    for (int d = 0; d < 3; d++) pos[d] = a[d] + (b[d] - a[d]) * (q - segment);
                    double width = (layer == 0 ? .34 : .5 + 1.7 * Math.sin(Math.PI * phase))
                        * Math.sin(Math.PI * travel);
                    pos[0] += Math.cos(theta) * width;
                    pos[2] += Math.sin(theta) * width;
                    if (source.id.startsWith("pressure_band_")) {
                        double angle = travel * Math.PI * 2 + seconds * .16 + si * .3,
                            radius = layer == 0 ? 13 : 15 + 4 * wrap(local * 2);
                        pos = new double[] { source.position[0] + Math.cos(angle) * radius,
                            source.position[1] + .45 * Math.sin(angle * 3),
                            source.position[2] + Math.sin(angle) * radius };
                    } else if (source.id.startsWith("processing_front_tool_")) {
                        double spread = layer == 0 ? .18 : .65 + travel * 4.8;
                        pos[0] += Math.cos(theta) * spread;
                        pos[1] += Math.sin(theta) * spread * .5;
                    } else if (source.id.equals("pulse_exit")) {
                        double spread = layer == 0 ? .22 : .8 + travel * 4.4;
                        pos[0] += Math.cos(theta) * spread;
                        pos[1] += Math.sin(theta) * spread;
                    } else if (source.id.startsWith("focus_field_")) {
                        if (i % 2 == 1) pos[0] = 80 - pos[0];
                        pos[2] -= Math.sin(Math.PI * travel) * width;
                    } else if (source.id.equals("stellar_corona")) {
                        JsonObject star = model.animations.getAsJsonObject("star");
                        double[] center = CriticalAnimation.vector(star, "pivot", new double[] { 40, 106, 40 });
                        List<double[]> surface = surface(model, "star", null);
                        if (!surface.isEmpty()) {
                            double[] point = surface.get(Math.min(surface.size() - 1, (int) (travel * surface.size())));
                            double[] direction = normalized(sub(point, center));
                            for (int d = 0; d < 3; d++) pos[d] = point[d] + direction[d] * .65;
                        }
                    }
                    if (source.animation != null) {
                        double[] moved = CriticalAnimation
                            .transform(pos, source.animation, phase, seconds, phase > 0 ? "build" : "idle");
                        double weight = source.fixedRoute ? Math.pow(1 - travel, 3) : 1;
                        for (int d = 0; d < 3; d++) pos[d] += (moved[d] - pos[d]) * weight;
                    }
                    double fade = layer == 0 ? 1
                        : Math.max(0, Math.min(1, Math.min((phase - source.lo) / .045, (source.hi - phase) / .055)));
                    double alpha = (layer == 0 ? .24 : .22 * fade) * (.8 + .2 * Math.sin(seconds * .8 + theta))
                        * Math.pow(Math.sin(Math.PI * travel), 2);
                    float[] color = COLORS[profile];
                    if (source.id.equals("stellar_corona")) color = new float[] { 1, .30F, .035F };
                    t.setColorRGBA_F(color[0], color[1], color[2], (float) alpha);
                    double size = SIZES[profile] * (layer == 0 ? .95 : 1.25 + .4 * Math.sin(Math.PI * travel));
                    for (double[][] face : FACES) for (double[] vertex : face) t.addVertex(
                        pos[0] + .5 + vertex[0] * size,
                        pos[1] + .5 + vertex[1] * size,
                        pos[2] + .5 + vertex[2] * size);
                }
            }
        }
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(true);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Four crossed confinement planes and transfers use actual ring voxels, cached only once. */
    private static void renderSun(CriticalGeometry.Model model, double phase, double seconds, Tessellator t) {
        if (!(phase > 0 && phase < 1)) return;
        JsonObject star = model.animations.getAsJsonObject("star");
        double[] core = CriticalAnimation
            .transform(CriticalAnimation.vector(star, "pivot", new double[3]), star, phase, seconds, "build");
        float[][] colors = { { 1, .32F, .055F }, { 1, .52F, .09F }, { 1, .71F, .18F }, { 1, .85F, .38F } };
        double envelope = Math
            .pow(Math.sin(Math.PI * Math.max(0, Math.min(1, Math.min(phase / .12, (1 - phase) / .12))) / 2), 2);
        for (int ring = 0; ring < 4; ring++) {
            String name = "confinement_" + ring;
            JsonObject group = model.animations.getAsJsonObject(name);
            double[] normal = normalized(
                CriticalAnimation.vector(group, "localPlaneNormal", CriticalAnimation.axis(group)));
            List<double[]> points = surface(model, name, normal);
            if (points.isEmpty()) continue;
            for (int layer = 0; layer < 2; layer++) {
                int total = layer == 0 ? 675 : 349, count = total / 4 + (ring < total % 4 ? 1 : 0);
                for (int i = 0; i < count; i++) {
                    double tail = i / (double) count, turn = wrap(phase * (1.5 + ring * .19) + ring * .25 - tail * .72);
                    double[] point = points.get(Math.min(points.size() - 1, (int) (turn * points.size()))),
                        anchor = new double[3];
                    for (int d = 0; d < 3; d++) anchor[d] = point[d] + normal[d] * (1.25 + (i % 2) * .55);
                    anchor = CriticalAnimation.transform(anchor, group, phase, seconds, "build");
                    double[] pos = anchor;
                    double size, alpha;
                    if (layer == 0) {
                        size = .85 + .20 * (1 - tail);
                        alpha = .16 * envelope * (.55 + .45 * (1 - tail));
                    } else {
                        double travel = wrap(phase * 3.2 + i / (double) count),
                            blend = Math.max(0, Math.min(1, (phase - .35) / .2));
                        blend = blend * blend * (3 - 2 * blend);
                        double amount = travel * (1 - blend) + (1 - travel) * blend;
                        double[] direction = normalized(sub(anchor, core));
                        pos = new double[3];
                        for (int d = 0; d < 3; d++) {
                            double surface = core[d] + direction[d] * 7.5;
                            pos[d] = anchor[d] + (surface - anchor[d]) * amount;
                        }
                        size = .62 + .26 * Math.sin(Math.PI * travel);
                        alpha = .13 * envelope * Math.pow(Math.sin(Math.PI * travel), 2);
                    }
                    cube(t, pos, colors[ring], alpha, size);
                }
            }
        }
    }

    private static List<double[]> surface(CriticalGeometry.Model model, String group, double[] normal) {
        return SURFACES.computeIfAbsent(group, ignored -> {
            List<double[]> result = new ArrayList<>();
            double[] pivot = CriticalAnimation.vector(model.animations.getAsJsonObject(group), "pivot", new double[3]);
            for (CriticalGeometry.Voxel v : model.voxels) if (v.group.equals(group)) {
                double[] point = { v.x, v.y, v.z }, delta = sub(point, pivot);
                if (!group.equals("star") || dot(delta, delta) > 44) result.add(point);
            }
            if (normal != null) {
                double[] ref = Math.abs(normal[1]) < .9 ? new double[] { 0, 1, 0 } : new double[] { 1, 0, 0 };
                double[] u = normalized(cross(normal, ref)), v = cross(normal, u);
                result.sort((a, b) -> {
                    long aa = Math
                        .round(wrap(Math.atan2(dot(sub(a, pivot), v), dot(sub(a, pivot), u)) / (Math.PI * 2)) * 1e8);
                    long bb = Math
                        .round(wrap(Math.atan2(dot(sub(b, pivot), v), dot(sub(b, pivot), u)) / (Math.PI * 2)) * 1e8);
                    int compare = Long.compare(aa, bb);
                    for (int d = 0; compare == 0 && d < 3; d++) compare = Double.compare(a[d], b[d]);
                    return compare;
                });
            }
            return result;
        });
    }

    private static void cube(Tessellator t, double[] pos, float[] color, double alpha, double size) {
        t.setColorRGBA_F(color[0], color[1], color[2], (float) alpha);
        for (double[][] face : FACES) for (double[] vertex : face)
            t.addVertex(pos[0] + .5 + vertex[0] * size, pos[1] + .5 + vertex[1] * size, pos[2] + .5 + vertex[2] * size);
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
    }

    private static double[] normalized(double[] a) {
        double len = Math.sqrt(dot(a, a));
        if (len == 0) len = 1;
        return new double[] { a[0] / len, a[1] / len, a[2] / len };
    }

    private static List<Source> parse(CriticalGeometry.Model model) {
        List<Source> result = new ArrayList<>();
        if (!model.meta.has("partsFX")) return result;
        for (JsonElement element : model.meta.getAsJsonArray("partsFX")) {
            JsonObject source = element.getAsJsonObject();
            if (!source.has("path")) continue;
            JsonArray path = source.getAsJsonArray("path");
            if (path.size() < 2) continue;
            double[][] route = new double[path.size()][];
            for (int i = 0; i < path.size(); i++) {
                JsonArray v = path.get(i)
                    .getAsJsonArray();
                route[i] = new double[] { v.get(0)
                    .getAsDouble(),
                    v.get(1)
                        .getAsDouble(),
                    v.get(2)
                        .getAsDouble() };
            }
            JsonArray active = source.has("activePhase") ? source.getAsJsonArray("activePhase") : null;
            String group = source.has("group") ? source.get("group")
                .getAsString() : "";
            result.add(
                new Source(
                    source.has("id") ? source.get("id")
                        .getAsString() : "",
                    route,
                    CriticalAnimation.vector(source, "position", route[0]),
                    active == null ? .03
                        : active.get(0)
                            .getAsDouble(),
                    active == null ? .94
                        : active.get(1)
                            .getAsDouble(),
                    model.animations.has(group) ? model.animations.getAsJsonObject(group) : null,
                    source.has("bindingMode") && source.get("bindingMode")
                        .getAsString()
                        .equals("movingEmitterFixedRoute")));
        }
        return result;
    }

    private static double wrap(double value) {
        return value - Math.floor(value);
    }

    private static final class Source {

        final String id;
        final double[][] route;
        final double[] position;
        final double lo, hi;
        final JsonObject animation;
        final boolean fixedRoute;

        Source(String id, double[][] route, double[] position, double lo, double hi, JsonObject animation,
            boolean fixedRoute) {
            this.id = id;
            this.route = route;
            this.position = position;
            this.lo = lo;
            this.hi = hi;
            this.animation = animation;
            this.fixedRoute = fixedRoute;
        }
    }
}
