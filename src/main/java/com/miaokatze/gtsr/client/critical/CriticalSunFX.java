package com.miaokatze.gtsr.client.critical;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.renderer.Tessellator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;

/** V2 sun: cached physical routes, one sparse inner stream per ring, no world light or particle entities. */
final class CriticalSunFX {

    static final int RING_IDLE = 48, RING_WORK = 72, LOCAL_LIMIT = 1024;
    private static final Map<CriticalGeometry.Model, ModelData> MODELS = new IdentityHashMap<>();
    private static final Map<JsonObject, Pose> POSES = new IdentityHashMap<>();
    private static final double[] POINT = new double[3], MOVED = new double[3];
    private static final float[] COLD = { .42F, .61F, .88F }, WARM = { .88F, .60F, .34F };
    private static int emitted;

    private CriticalSunFX() {}

    static void clear() {
        MODELS.clear();
        POSES.clear();
    }

    static void render(CriticalGeometry.Model sun, CriticalGeometry.Model loom, CriticalGeometry.Model base,
        double phase, double seconds, int lod, Set<String> visible, String sunSlug, String loomSlug, String baseSlug,
        Tessellator tessellator) {
        ModelData data = MODELS.computeIfAbsent(sun, ModelData::new);
        boolean work = phase > 0 && phase < 1;
        emitted = 0;
        int ringCount = (work ? RING_WORK : RING_IDLE) / lod;
        int visibleRings = 0;
        long ringDrawnBefore = CriticalRenderMetrics.particlesDrawn;
        for (Ring ring : data.rings) {
            if (visible != null && !visible.contains(sunSlug + ':' + ring.group)) continue;
            visibleRings++;
            for (int i = 0; i < ringCount; i++) {
                double fraction = wrap(seconds * ring.speed + i / (double) ringCount);
                double angle = fraction * Math.PI * 2, bin = fraction * 64;
                int first = (int) bin;
                double radius = ring.radii[first] + (ring.radii[(first + 1) % 64] - ring.radii[first]) * (bin - first);
                for (int d = 0; d < 3; d++)
                    POINT[d] = ring.pivot[d] + (ring.u[d] * Math.cos(angle) + ring.v[d] * Math.sin(angle)) * radius;
                transform(POINT, ring.animation, phase, seconds, MOVED);
                emit(tessellator, MOVED, COLD, work ? .39 : .26, .43);
            }
        }
        CriticalRenderMetrics.lastSunRingCount = visibleRings;
        CriticalRenderMetrics.lastSunParticlesPerRing = ringCount;
        CriticalRenderMetrics.lastSunRingParticles = CriticalRenderMetrics.particlesDrawn - ringDrawnBefore;
        if (visible == null || visible.contains(sunSlug + ":star")) {
            int count = (work ? 96 : 64) / lod;
            for (int i = 0; i < count; i++) {
                double y = 1 - 2 * (i + .5) / count, a = i * 2.399963 + seconds * .08;
                double horizontal = Math.sqrt(1 - y * y), radius = 10.2 + .3 * Math.sin(seconds * .8 + i);
                POINT[0] = data.core[0] + horizontal * Math.cos(a) * radius;
                POINT[1] = data.core[1] + y * radius;
                POINT[2] = data.core[2] + horizontal * Math.sin(a) * radius;
                transform(POINT, data.star, phase, seconds, MOVED);
                emit(tessellator, MOVED, WARM, work ? .27 : .16, .35);
            }
        }
        routes(data.tipRoutes, work ? 8 : 3, lod, phase, seconds, tessellator);
        routes(data.plateRoutes, work ? 3 : 1, lod, phase, seconds, tessellator);
        gravity(sun, sunSlug, phase, seconds, lod, visible, tessellator);
        gravity(loom, loomSlug, phase, seconds, lod, visible, tessellator);
        gravity(base, baseSlug, phase, seconds, lod, visible, tessellator);
        CriticalRenderMetrics.lastSunParticles = emitted;
        CriticalRenderMetrics.lastSunLod = lod;
    }

    private static void routes(List<double[][]> routes, int amount, int lod, double phase, double seconds,
        Tessellator tessellator) {
        int indexInFamily = 0;
        for (double[][] route : routes) for (int i = 0; i < amount; i++) {
            if (indexInFamily++ % lod != 0) continue;
            double progress = wrap(seconds * .19 + i / (double) amount), q = progress * (route.length - 1);
            int index = Math.min(route.length - 2, (int) q);
            for (int d = 0; d < 3; d++)
                POINT[d] = route[index][d] + (route[index + 1][d] - route[index][d]) * (q - index);
            POINT[1] += .58;
            emit(tessellator, POINT, COLD, .25 * Math.sin(Math.PI * progress), .30);
        }
    }

    private static void gravity(CriticalGeometry.Model model, String slug, double phase, double seconds, int lod,
        Set<String> visible, Tessellator tessellator) {
        ModelData data = MODELS.computeIfAbsent(model, ModelData::new);
        for (int i = 0; i < data.gravity.size(); i++) {
            if (i % lod != 0) continue;
            Emitter emitter = data.gravity.get(i);
            if (emitter.animation != null && visible != null && !visible.contains(slug + ':' + emitter.group)) continue;
            double cycle = seconds * .65 + emitter.height * .07;
            double[] source = emitter.members[Math.floorMod((int) Math.floor(cycle), emitter.members.length)];
            transform(source, emitter.animation, phase, seconds, MOVED, slug.endsWith("loom") ? "idle" : "build");
            double progress = wrap(cycle);
            MOVED[1] -= .65 + 2.8 * progress;
            emit(tessellator, MOVED, COLD, .20 * Math.sin(Math.PI * progress), .23);
        }
    }

    private static void emit(Tessellator tessellator, double[] point, float[] color, double alpha, double size) {
        if (emitted >= LOCAL_LIMIT || !CriticalFXBudget.claim()) return;
        emitted++;
        CriticalFX.cube(tessellator, point, color, alpha, size);
    }

    private static void transform(double[] point, JsonObject animation, double phase, double seconds, double[] out) {
        transform(point, animation, phase, seconds, out, "build");
    }

    private static void transform(double[] point, JsonObject animation, double phase, double seconds, double[] out,
        String mode) {
        if (animation == null) {
            System.arraycopy(point, 0, out, 0, 3);
            return;
        }
        Pose pose = POSES.computeIfAbsent(animation, ignored -> new Pose());
        if (pose.phase != phase || pose.seconds != seconds || !mode.equals(pose.mode)) {
            double[] origin = CriticalAnimation.transform(new double[3], animation, phase, seconds, mode);
            for (int column = 0; column < 3; column++) {
                double[] unit = new double[3];
                unit[column] = 1;
                double[] moved = CriticalAnimation.transform(unit, animation, phase, seconds, mode);
                for (int row = 0; row < 3; row++) pose.values[column * 3 + row] = moved[row] - origin[row];
            }
            System.arraycopy(origin, 0, pose.values, 9, 3);
            pose.phase = phase;
            pose.seconds = seconds;
            pose.mode = mode;
        }
        for (int row = 0; row < 3; row++) out[row] = point[0] * pose.values[row] + point[1] * pose.values[3 + row]
            + point[2] * pose.values[6 + row]
            + pose.values[9 + row];
    }

    private static double wrap(double value) {
        return value - Math.floor(value);
    }

    private static double emitterAngle(double[] point, double cx, double cz, double rx, double rz) {
        double tangent = (point[0] - cx) * -rz + (point[2] - cz) * rx;
        double radial = (point[0] - cx) * rx + (point[2] - cz) * rz;
        // Signed zero must not put equivalent rotated carriers on opposite atan2 branch cuts.
        if (Math.abs(tangent) < 1e-9) tangent = 0;
        if (Math.abs(radial) < 1e-9) radial = 0;
        return Math.atan2(tangent, radial);
    }

    private static final class Pose {

        final double[] values = new double[12];
        double phase = Double.NaN, seconds = Double.NaN;
        String mode = "";
    }

    private static final class Emitter {

        final double[][] members;
        final double height;
        final String group;
        final JsonObject animation;

        Emitter(double[][] members, String group, JsonObject animation) {
            this.members = members;
            this.height = members[0][1];
            this.group = group;
            this.animation = animation;
        }
    }

    private static final class ModelData {

        final List<Ring> rings = new ArrayList<>();
        final List<double[][]> tipRoutes = new ArrayList<>(), plateRoutes = new ArrayList<>();
        final List<Emitter> gravity = new ArrayList<>();
        final JsonObject star;
        final double[] core;

        ModelData(CriticalGeometry.Model model) {
            star = model.animations.has("star") ? model.animations.getAsJsonObject("star") : null;
            core = star == null ? new double[3] : CriticalAnimation.vector(star, "pivot", new double[3]);
            for (int ring = 0; ring < 4; ring++) if (model.animations.has("confinement_" + ring))
                rings.add(new Ring(model, "confinement_" + ring, ring % 2 == 0 ? .055 : -.055));
            parseRoutes(model.meta, "partsFX", "path", tipRoutes);
            parseRoutes(model.meta, "receivingPlaneCircuitSources", "projectorPath", plateRoutes);
            parseRoutes(model.meta, "receivingPlaneCircuitSources", "chamberPath", plateRoutes);
            if (model.meta.has("gravityClusters"))
                for (JsonElement entry : model.meta.getAsJsonArray("gravityClusters")) {
                    JsonObject cluster = entry.getAsJsonObject();
                    JsonArray cells = cluster.getAsJsonArray("cells");
                    double[][] members = new double[cells.size()][];
                    double centerX = 0, centerZ = 0;
                    for (int i = 0; i < members.length; i++) {
                        members[i] = vector(
                            cells.get(i)
                                .getAsJsonArray());
                        centerX += members[i][0];
                        centerZ += members[i][2];
                    }
                    final double cx = centerX / members.length, cz = centerZ / members.length;
                    final double rx = cx - 56, rz = cz - 56;
                    // Intrinsic radial/tangent order rotates with the carrier; equal-height symmetric clusters share
                    // phase.
                    java.util.Arrays.sort(
                        members,
                        java.util.Comparator.comparingDouble((double[] p) -> emitterAngle(p, cx, cz, rx, rz))
                            .thenComparingDouble(p -> (p[0] - cx) * (p[0] - cx) + (p[2] - cz) * (p[2] - cz)));
                    String group = cluster.get("group")
                        .getAsString();
                    gravity.add(
                        new Emitter(
                            members,
                            group,
                            model.animations.has(group) ? model.animations.getAsJsonObject(group) : null));
                }
        }
    }

    private static void parseRoutes(JsonObject meta, String key, String path, List<double[][]> result) {
        if (!meta.has(key)) return;
        for (JsonElement entry : meta.getAsJsonArray(key)) {
            JsonObject object = entry.getAsJsonObject();
            if (!object.has(path)) continue;
            JsonArray points = object.getAsJsonArray(path);
            if (points.size() < 2) continue;
            double[][] route = new double[points.size()][];
            for (int i = 0; i < route.length; i++) route[i] = vector(
                points.get(i)
                    .getAsJsonArray());
            result.add(route);
        }
    }

    private static double[] vector(JsonArray array) {
        return new double[] { array.get(0)
            .getAsDouble(),
            array.get(1)
                .getAsDouble(),
            array.get(2)
                .getAsDouble() };
    }

    private static final class Ring {

        final String group;
        final JsonObject animation;
        final double[] pivot, u, v, radii = new double[64];
        final double speed;

        Ring(CriticalGeometry.Model model, String group, double speed) {
            this.group = group;
            this.speed = speed;
            animation = model.animations.getAsJsonObject(group);
            pivot = CriticalAnimation.vector(animation, "pivot", new double[3]);
            double[] normal = CriticalAnimation
                .vector(animation, "localPlaneNormal", CriticalAnimation.axis(animation));
            normalize(normal);
            double[] reference = Math.abs(normal[1]) > .9 ? new double[] { 1, 0, 0 } : new double[] { 0, 1, 0 };
            u = cross(normal, reference);
            normalize(u);
            v = cross(normal, u);
            java.util.Arrays.fill(radii, Double.POSITIVE_INFINITY);
            double minimum = Double.POSITIVE_INFINITY;
            for (CriticalGeometry.Voxel voxel : model.voxels) if (voxel.group.equals(group)) {
                double[] delta = { voxel.x - pivot[0], voxel.y - pivot[1], voxel.z - pivot[2] };
                double x = dot(delta, u), y = dot(delta, v), radius = Math.sqrt(x * x + y * y);
                int bin = Math.min(63, (int) (wrap(Math.atan2(y, x) / (Math.PI * 2)) * 64));
                radii[bin] = Math.min(radii[bin], radius);
                minimum = Math.min(minimum, radius);
            }
            for (int i = 0; i < radii.length; i++)
                radii[i] = Math.max(1, (Double.isInfinite(radii[i]) ? minimum : radii[i]) - .70);
        }
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
    }

    private static void normalize(double[] vector) {
        double length = Math.sqrt(dot(vector, vector));
        if (length == 0) return;
        for (int i = 0; i < 3; i++) vector[i] /= length;
    }
}
