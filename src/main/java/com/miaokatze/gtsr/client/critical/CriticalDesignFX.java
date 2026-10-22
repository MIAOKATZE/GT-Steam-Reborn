package com.miaokatze.gtsr.client.critical;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Approved R5 design_fx.js sampler. No GL, world queries, particle entities or private animation clock. */
final class CriticalDesignFX {

    static final int LOCAL_LIMIT = 800, IDLE_LIMIT = 200, GRAVITY_LIMIT = 64;
    private static final Map<JsonObject, Data> MODELS = new IdentityHashMap<>();
    private static final Map<JsonObject, List<NativeRing>> SUN_RINGS = new IdentityHashMap<>();
    private static final double[] GRAVITY_COLOR = { .43, .64, .94 }, SHARED_COLOR = { .43, .58, .86 };

    interface ParticleSink {

        /** A bounded renderer may stop once later samples cannot produce any vertices. */
        default boolean full() {
            return false;
        }

        void emit(String family, String source, int index, double[] center, double[] color, double alpha,
            double glowAlpha, double size);
    }

    private CriticalDesignFX() {}

    static String profile(JsonObject meta, String slug) {
        if (meta.has("designFX")) return "design";
        return meta.has("solarFX") || slug.endsWith("_solar") || "solar".equals(slug) ? "solar" : "sun";
    }

    static void clear() {
        MODELS.clear();
        SUN_RINGS.clear();
    }

    /** Accepted native Sun stream, with its original 1024 local budget, separate from seven design budgets. */
    static int sampleSun(JsonObject[] metas, JsonObject[] groups, String[] slugs,
        Map<String, List<double[]>> ringPoints, double phase, double seconds, String mode, int lod, Set<String> visible,
        ParticleSink sink) {
        if (lod <= 0) return 0;
        boolean work = "working".equals(mode);
        Counter out = new Counter(lod, work, seconds, sink);
        for (int owner = 0; owner < 3; owner++) {
            if (sink.full()) return out.emitted;
            Data data = data(metas[owner]);
            for (int ci = 0; ci < data.clusters.size(); ci++) {
                if (sink.full()) return out.emitted;
                if (!active(ci, data.clusters.size(), seconds)) continue;
                Cluster c = data.clusters.get(ci);
                double cycle = seconds * .65 + c.meanHeight * .07, t = wrap(cycle);
                Member m = c.sorted[Math.floorMod((int) Math.floor(cycle), c.sorted.length)];
                JsonObject g = group(groups[owner], m.group);
                if (!visible(visible, slugs[owner], m.group, g)) continue;
                String poseMode = owner == 1 || owner == 0 && m.group.startsWith("loom_") ? "idle" : "build";
                double[] q = pose(m.position, g, phase, seconds, poseMode);
                q[1] -= bottomGap(m.position, g, phase, seconds, poseMode, q) + 2.8 * t;
                out.direct("gravity", c.id, q, SHARED_COLOR, .20 * Math.sin(Math.PI * t), .65, .23);
            }
        }
        List<NativeRing> rings = SUN_RINGS.computeIfAbsent(metas[2], ignored -> {
            List<NativeRing> parsed = new ArrayList<>();
            for (int k = 0; k < 4; k++) {
                String name = "confinement_" + k;
                if (groups[2].has(name)) parsed.add(
                    new NativeRing(
                        name,
                        groups[2].getAsJsonObject(name),
                        ringPoints.get(name),
                        k % 2 == 0 ? .055 : -.055));
            }
            return parsed;
        });
        for (NativeRing r : rings) {
            if (sink.full()) return out.emitted;
            if (!visible(visible, slugs[2], r.name, r.animation)) continue;
            int n = work ? 72 : 48;
            for (int i = 0; i < n; i++) {
                if (sink.full()) return out.emitted;
                double t = wrap(i / (double) n + seconds * r.speed + phase * .35), a = t * Math.PI * 2, b = t * 64;
                int j = (int) Math.floor(b);
                double radius = r.radii[j] + (r.radii[(j + 1) % 64] - r.radii[j]) * (b - j);
                double[] q = new double[3];
                for (int d = 0; d < 3; d++) q[d] = r.pivot[d] + (r.u[d] * Math.cos(a) + r.v[d] * Math.sin(a)) * radius;
                out.direct(
                    "ring",
                    r.name,
                    pose(q, r.animation, phase, seconds, "build"),
                    new double[] { .48, .64, .90 },
                    work ? .54 : .36,
                    .65,
                    .31);
            }
        }
        JsonObject star = group(groups[2], "star");
        if (visible(visible, slugs[2], "star", star)) {
            double[] pivot = star == null ? new double[3] : CriticalAnimation.vector(star, "pivot", new double[3]);
            int n = work ? 96 : 64;
            for (int i = 0; i < n; i++) {
                if (sink.full()) return out.emitted;
                double y = 1 - 2 * (i + .5) / n, a = i * 2.399963 + seconds * .10, r = Math.sqrt(1 - y * y),
                    radius = 9.85 + .12 * Math.sin(seconds * 1.4 + i);
                double[] q = { pivot[0] + r * Math.cos(a) * radius, pivot[1] + y * radius,
                    pivot[2] + r * Math.sin(a) * radius };
                out.direct(
                    "core",
                    "star:sphere",
                    pose(q, star, phase, seconds, "build"),
                    new double[] { .93, .66, .40 },
                    work ? .31 : .22,
                    .65,
                    .29);
            }
        }
        Data main = data(metas[2]);
        for (NativeRoute r : main.tipRoutes) {
            if (sink.full()) return out.emitted;
            JsonObject g = group(groups[2], r.group);
            if (!visible(visible, slugs[2], r.group, g)) continue;
            int n = work ? 8 : 3;
            for (int i = 0; i < n; i++) {
                if (sink.full()) return out.emitted;
                double t = wrap(seconds * .19 + i / (double) n + phase * .4);
                double[] q = path(r.path, phase >= .72 ? 1 - t : t);
                out.direct(
                    "tip",
                    r.id,
                    pose(q, g, phase, seconds, "build"),
                    new double[] { .59, .66, .91 },
                    work ? .36 : .19,
                    .65,
                    .30);
            }
        }
        for (NativeRoute r : main.plateRoutes) {
            if (sink.full()) return out.emitted;
            int n = work ? 3 : 1;
            for (int i = 0; i < n; i++) {
                if (sink.full()) return out.emitted;
                double[] q = path(r.path, wrap(seconds * .27 + i / (double) n + phase * .2));
                q[1] += .58;
                out.direct("plate", r.id, q, new double[] { .42, .61, .88 }, work ? .32 : .19, .65, .24);
            }
        }
        if (out.nominal > 1024) throw new IllegalStateException("Native Sun FX exceeds 1024: " + out.nominal);
        return out.emitted;
    }

    /** metas/groups/slugs are ordered base, loom, machine. Caller supplies the installation's current context. */
    static int sample(JsonObject[] metas, JsonObject[] groups, String[] slugs, double phase, double seconds,
        String mode, int lod, Set<String> visible, ParticleSink sink) {
        if (lod <= 0) return 0;
        boolean working = "working".equals(mode);
        Counter out = new Counter(lod, working, seconds, sink);
        for (int owner = 0; owner < 2; owner++) {
            if (sink.full()) return out.emitted;
            Data shared = data(metas[owner]);
            for (int ci = 0; ci < shared.clusters.size(); ci++) {
                if (sink.full()) return out.emitted;
                Cluster cluster = shared.clusters.get(ci);
                if (!active(ci, shared.clusters.size(), seconds)) continue;
                double cycle = seconds * .65 + cluster.meanHeight * .07;
                Member member = cluster.sorted[Math.floorMod((int) Math.floor(cycle), cluster.sorted.length)];
                JsonObject animation = group(groups[owner], member.group);
                if (!visible(visible, slugs[owner], member.group, animation)) continue;
                String poseMode = owner == 1 || member.group.startsWith("loom_") ? "idle" : "build";
                double t = wrap(cycle);
                double[] posed = pose(member.position, animation, phase, seconds, poseMode);
                posed[1] -= bottomGap(member.position, animation, phase, seconds, poseMode, posed) + 2.8 * t;
                out.direct("gravity", cluster.id, posed, SHARED_COLOR, .20 * Math.sin(Math.PI * t), .65, .23);
            }
        }
        Data machine = data(metas[2]);
        for (int si = 0; si < machine.sources.size(); si++) {
            if (sink.full()) return out.emitted;
            Source s = machine.sources.get(si);
            int count = working ? s.work : s.idle;
            if ("gravity".equals(s.type)) continue;
            JsonObject animation = group(groups[2], s.group);
            if (!visible(visible, slugs[2], s.group, animation)) continue;
            if ("ring".equals(s.type)) {
                for (int i = 0; i < count; i++) {
                    if (sink.full()) return out.emitted;
                    double t = wrap(i / (double) count + seconds * s.direction * (working ? .075 : .035) + si * .125);
                    double angle = t * Math.PI * 2;
                    double[] point = new double[3];
                    for (int d = 0; d < 3; d++)
                        point[d] = s.center[d] + (s.u[d] * Math.cos(angle) + s.v[d] * Math.sin(angle)) * s.radius;
                    out.emit(s, i, pose(point, animation, phase, seconds, mode), s.color, 1, s.glow, s.size);
                }
            } else if ("plume".equals(s.type)) {
                double gate = working
                    ? Math.max(0, Math.min(1, Math.min((phase - s.lo) / s.ramp, (s.hi - phase) / s.ramp)))
                    : 0;
                double pulse = s.pulseFrequency == 0 ? 1
                    : Math.max(
                        0,
                        Math.min(
                            1,
                            .7 + .3
                                * Math.sin(seconds * s.pulseFrequency + (s.pulsePhase == null ? si : s.pulsePhase))));
                int amount = (int) Math.floor(count * gate * pulse);
                for (int i = 0; i < amount; i++) {
                    if (sink.full()) return out.emitted;
                    double t = wrap(i / (double) Math.max(1, amount) + seconds * s.speed + si * .125);
                    double angle = i * 2.399963 + seconds * .8 + si;
                    double radius = (.16 + Math.tan(s.spread) * s.length * t) * Math.sqrt((i % 11 + .5) / 11);
                    double[] point = new double[3];
                    for (int d = 0; d < 3; d++) point[d] = s.center[d] + s.normal[d] * s.length * t
                        + (s.u[d] * Math.cos(angle) + s.v[d] * Math.sin(angle)) * radius;
                    point = pose(point, animation, phase, seconds, mode);
                    if (point[1] >= 179.8) continue;
                    double size = Math.min(s.size * (.7 + t * 1.2), Math.max(.05, 179.8 - point[1]));
                    out.emit(
                        s,
                        i,
                        point,
                        i % 9 == 0 ? new double[] { .86, .84, .78 } : s.color,
                        gate * .60 * (.6 + .4 * Math.sin(Math.PI * t)),
                        s.glow * Math.pow(1 - t, 3),
                        size);
                }
            } else {
                for (int i = 0; i < count; i++) {
                    if (sink.full()) return out.emitted;
                    double t = wrap(i / (double) Math.max(1, count) + seconds * (working ? .18 : .06) + si * .125);
                    double[] point = path(s.path, t);
                    if ("scan".equals(s.type))
                        for (int d = 0; d < 3; d++) point[d] += s.u[d] * Math.sin(t * Math.PI * 2) * s.scanRadius;
                    out.emit(s, i, pose(point, animation, phase, seconds, mode), s.color, 1, s.glow, s.size);
                }
            }
        }
        for (int ci = 0; ci < machine.clusters.size(); ci++) {
            if (sink.full()) return out.emitted;
            Cluster c = machine.clusters.get(ci);
            if (!active(ci, machine.clusters.size(), seconds)) continue;
            double cycle = seconds * .65 + c.members[0].position[1] * .07;
            Member member = c.members[Math.floorMod((int) Math.floor(cycle), c.members.length)];
            JsonObject animation = group(groups[2], member.group);
            if (!visible(visible, slugs[2], member.group, animation)) continue;
            double[] emitter = pose(member.position, animation, phase, seconds, mode);
            double gap = bottomGap(member.position, animation, phase, seconds, mode, emitter);
            emitter[1] -= gap + 2.8 * wrap(cycle);
            // World-down trail is already posed: never transform it a second time with the carrier.
            out.direct(
                "design-gravity",
                c.id,
                emitter,
                GRAVITY_COLOR,
                (working ? .36 : .20) * (.85 + .15 * Math.sin(seconds * .8)),
                .65,
                .25);
        }
        int limit = working ? LOCAL_LIMIT : IDLE_LIMIT;
        if (out.nominal > limit)
            throw new IllegalStateException("R5 FX declared budget exceeds " + limit + ": " + out.nominal);
        return out.emitted;
    }

    static boolean active(int index, int total, double seconds) {
        return total <= GRAVITY_LIMIT || Math.floorMod(index - (int) Math.floor(seconds * .65), total) < GRAVITY_LIMIT;
    }

    static double bottomGap(double[] point, JsonObject animation, double phase, double seconds, String mode,
        double[] posed) {
        if (animation == null) return .65;
        double extent = 0;
        for (int d = 0; d < 3; d++) {
            double[] unit = point.clone();
            unit[d]++;
            extent += Math.abs(pose(unit, animation, phase, seconds, mode)[1] - posed[1]);
        }
        return .15 + .5 * extent;
    }

    static double[] pose(double[] point, JsonObject animation, double phase, double seconds, String mode) {
        return animation == null ? point.clone() : CriticalAnimation.transform(point, animation, phase, seconds, mode);
    }

    private static Data data(JsonObject meta) {
        return MODELS.computeIfAbsent(meta, Data::new);
    }

    private static JsonObject group(JsonObject groups, String name) {
        return groups.has(name) ? groups.getAsJsonObject(name) : null;
    }

    private static boolean visible(Set<String> visible, String slug, String name, JsonObject animation) {
        return animation == null || visible == null || visible.contains(slug + ':' + name);
    }

    private static double number(JsonObject o, String key, double fallback) {
        return CriticalAnimation.number(o, key, fallback);
    }

    private static String string(JsonObject o, String key, String fallback) {
        return o.has(key) ? o.get(key)
            .getAsString() : fallback;
    }

    private static double wrap(double x) {
        return ((x % 1) + 1) % 1;
    }

    private static double[] vector(JsonArray a) {
        return new double[] { a.get(0)
            .getAsDouble(),
            a.get(1)
                .getAsDouble(),
            a.get(2)
                .getAsDouble() };
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
    }

    private static double[] unit(double[] a) {
        double n = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        for (int d = 0; d < 3; d++) a[d] /= n == 0 ? 1 : n;
        return a;
    }

    private static double[] path(double[][] route, double t) {
        double q = t * (route.length - 1);
        int i = Math.min(route.length - 2, (int) Math.floor(q));
        double[] out = new double[3];
        for (int d = 0; d < 3; d++) out[d] = route[i][d] + (route[i + 1][d] - route[i][d]) * (q - i);
        return out;
    }

    private static final class Counter {

        final int lod;
        final boolean working;
        final double seconds;
        final ParticleSink sink;
        final Map<String, Integer> families = new LinkedHashMap<>();
        int nominal, emitted;

        Counter(int lod, boolean working, double seconds, ParticleSink sink) {
            this.lod = lod;
            this.working = working;
            this.seconds = seconds;
            this.sink = sink;
        }

        void emit(Source s, int i, double[] point, double[] color, double gain, double glow, double size) {
            direct(
                s.family,
                s.id,
                point,
                color,
                (working ? .36 : .20) * (.85 + .15 * Math.sin(seconds * .8 + i)) * gain,
                glow,
                size);
        }

        void direct(String family, String source, double[] point, double[] color, double alpha, double glow,
            double size) {
            int index = families.getOrDefault(family, 0);
            families.put(family, index + 1);
            nominal++;
            if (index % lod != 0) return;
            sink.emit(family, source, index, point, color, alpha, alpha * glow, size);
            emitted++;
        }
    }

    private static final class Source {

        final String id, type, family, group;
        final int idle, work;
        final double[] center, normal, u, v, color;
        final double[][] path;
        final double radius, scanRadius, direction, length, size, glow, spread, speed, lo, hi, ramp, pulseFrequency;
        final Double pulsePhase;

        Source(JsonObject s) {
            id = string(s, "id", "");
            type = string(s, "type", "");
            family = string(s, "family", type);
            group = string(s, "carrierGroup", string(s, "group", ""));
            JsonElement budget = s.get("budget");
            idle = budget == null ? 0
                : budget.isJsonPrimitive() ? budget.getAsInt() : (int) number(budget.getAsJsonObject(), "idle", 0);
            work = budget == null ? 0
                : budget.isJsonPrimitive() ? budget.getAsInt() : (int) number(budget.getAsJsonObject(), "work", 0);
            center = CriticalAnimation.vector(s, "center", new double[] { 56, 100, 56 });
            normal = unit(axis(s, "ring".equals(type) && s.has("planeNormal") ? "planeNormal" : "axis"));
            u = Math.abs(normal[1]) > .9 ? new double[] { 1, 0, 0 } : unit(cross(normal, new double[] { 0, 1, 0 }));
            v = unit(cross(normal, u));
            color = CriticalAnimation.vector(s, "color", new double[] { .4, .7, 1 });
            radius = Math.max(.4, number(s, "radius", 12));
            scanRadius = number(s, "radius", 1);
            direction = number(s, "direction", 1);
            length = number(s, "length", 8);
            size = number(s, "size", "plume".equals(type) ? .6 : .25);
            glow = number(s, "glowGain", "plume".equals(type) ? .064 : .65);
            spread = number(s, "spread", .28);
            speed = number(s, "speed", .45);
            JsonArray window = s.has("activeWindow") ? s.getAsJsonArray("activeWindow") : null;
            lo = window == null ? 0
                : window.get(0)
                    .getAsDouble();
            hi = window == null ? 1
                : window.get(1)
                    .getAsDouble();
            ramp = number(s, "ramp", .06);
            JsonElement pulse = s.get("pulse");
            pulseFrequency = pulse == null ? 0
                : pulse.isJsonPrimitive() ? pulse.getAsDouble() : number(pulse.getAsJsonObject(), "frequency", 2);
            pulsePhase = pulse != null && pulse.isJsonPrimitive() ? Double.valueOf(0)
                : pulse != null && pulse.getAsJsonObject()
                    .has("phase") ? Double.valueOf(
                        pulse.getAsJsonObject()
                            .get("phase")
                            .getAsDouble())
                        : null;
            if (s.has("path")) {
                JsonArray r = s.getAsJsonArray("path");
                path = new double[r.size()][];
                for (int i = 0; i < path.length; i++) path[i] = vector(
                    r.get(i)
                        .getAsJsonArray());
            } else {
                path = new double[2][3];
                for (int d = 0; d < 3; d++) {
                    path[0][d] = center[d] - normal[d] * length / 2;
                    path[1][d] = center[d] + normal[d] * length / 2;
                }
            }
        }

        private static double[] axis(JsonObject s, String key) {
            if (!s.has(key)) return new double[] { 0, 1, 0 };
            JsonElement value = s.get(key);
            if (value.isJsonArray()) return vector(value.getAsJsonArray());
            String a = value.getAsString();
            return new double[] { "x".equals(a) ? 1 : 0, "y".equals(a) ? 1 : 0, "z".equals(a) ? 1 : 0 };
        }
    }

    private static final class Member {

        final double[] position;
        final String group;

        Member(double[] p, String group) {
            this.position = p;
            this.group = group;
        }
    }

    private static final class Cluster {

        final String id;
        final Member[] members, sorted;
        final double meanHeight;

        Cluster(String id, List<Member> points) {
            this.id = id;
            members = points.toArray(new Member[0]);
            sorted = members.clone();
            double cx = 0, cy = 0, cz = 0;
            for (Member m : members) {
                cx += m.position[0];
                cy += m.position[1];
                cz += m.position[2];
            }
            final double x = cx / members.length, z = cz / members.length, rx = x - 56, rz = z - 56;
            meanHeight = cy / members.length;
            java.util.Arrays.sort(sorted, (a, b) -> {
                double aa = angle(a.position, x, z, rx, rz), bb = angle(b.position, x, z, rx, rz);
                return aa != bb ? Double.compare(aa, bb)
                    : Double.compare(radius(a.position, x, z), radius(b.position, x, z));
            });
        }

        private static double radius(double[] p, double x, double z) {
            return (p[0] - x) * (p[0] - x) + (p[2] - z) * (p[2] - z);
        }

        private static double angle(double[] p, double x, double z, double rx, double rz) {
            double a = (p[2] - z) * rx - (p[0] - x) * rz, b = (p[0] - x) * rx + (p[2] - z) * rz;
            return Math.atan2(Math.abs(a) < 1e-9 ? 0 : a, Math.abs(b) < 1e-9 ? 0 : b);
        }
    }

    private static final class Data {

        final List<Source> sources = new ArrayList<>();
        final List<Cluster> clusters = new ArrayList<>();
        final List<NativeRoute> tipRoutes = new ArrayList<>(), plateRoutes = new ArrayList<>();

        Data(JsonObject meta) {
            routes(meta, "partsFX", "path", tipRoutes);
            if (meta.has("receivingPlaneCircuitSources"))
                for (JsonElement e : meta.getAsJsonArray("receivingPlaneCircuitSources")) {
                    JsonObject r = e.getAsJsonObject();
                    if (r.has("projectorPath")) plateRoutes.add(new NativeRoute(r, "projectorPath"));
                    if (r.has("chamberPath")) plateRoutes.add(new NativeRoute(r, "chamberPath"));
                }
            if (meta.has("designFX"))
                for (JsonElement s : meta.getAsJsonArray("designFX")) sources.add(new Source(s.getAsJsonObject()));
            if (meta.has("gravityClusters") && meta.getAsJsonArray("gravityClusters")
                .size() > 0) {
                for (JsonElement e : meta.getAsJsonArray("gravityClusters")) {
                    JsonObject c = e.getAsJsonObject();
                    List<Member> members = new ArrayList<>();
                    String g = string(c, "carrierGroup", string(c, "group", ""));
                    for (JsonElement p : c.getAsJsonArray("cells"))
                        members.add(new Member(vector(p.getAsJsonArray()), g));
                    if (!members.isEmpty()) clusters.add(new Cluster(string(c, "id", ""), members));
                }
            } else if (meta.has("gravitySources")) {
                Map<String, List<Member>> byId = new LinkedHashMap<>();
                for (JsonElement e : meta.getAsJsonArray("gravitySources")) {
                    JsonObject s = e.getAsJsonObject();
                    String id = string(s, "clusterId", string(s, "id", ""));
                    byId.computeIfAbsent(id, k -> new ArrayList<>())
                        .add(
                            new Member(
                                CriticalAnimation.vector(s, "position", new double[3]),
                                string(s, "carrierGroup", string(s, "group", ""))));
                }
                for (Map.Entry<String, List<Member>> e : byId.entrySet())
                    clusters.add(new Cluster(e.getKey(), e.getValue()));
            }
        }
    }

    private static void routes(JsonObject meta, String key, String path, List<NativeRoute> out) {
        if (meta.has(key)) for (JsonElement e : meta.getAsJsonArray(key)) {
            JsonObject r = e.getAsJsonObject();
            if (r.has(path) && r.getAsJsonArray(path)
                .size() > 1) out.add(new NativeRoute(r, path));
        }
    }

    private static final class NativeRoute {

        final String id, group;
        final double[][] path;

        NativeRoute(JsonObject s, String key) {
            id = string(s, "id", "");
            group = string(s, "carrierGroup", string(s, "group", ""));
            JsonArray points = s.getAsJsonArray(key);
            path = new double[points.size()][];
            for (int i = 0; i < path.length; i++) path[i] = vector(
                points.get(i)
                    .getAsJsonArray());
        }
    }

    private static final class NativeRing {

        final String name;
        final JsonObject animation;
        final double[] pivot, u, v, radii = new double[64];
        final double speed;

        NativeRing(String name, JsonObject animation, List<double[]> points, double speed) {
            this.name = name;
            this.animation = animation;
            this.speed = speed;
            pivot = CriticalAnimation.vector(animation, "pivot", new double[3]);
            double[] normal = unit(
                CriticalAnimation.vector(animation, "localPlaneNormal", CriticalAnimation.axis(animation)));
            u = Math.abs(normal[1]) > .9 ? new double[] { 1, 0, 0 } : unit(cross(normal, new double[] { 0, 1, 0 }));
            v = unit(cross(normal, u));
            java.util.Arrays.fill(radii, Double.POSITIVE_INFINITY);
            for (double[] p : points) {
                double x = 0, y = 0;
                for (int d = 0; d < 3; d++) {
                    x += (p[d] - pivot[d]) * u[d];
                    y += (p[d] - pivot[d]) * v[d];
                }
                int bin = (int) Math.floor(wrap(Math.atan2(y, x) / (Math.PI * 2)) * 64);
                radii[bin] = Math.min(radii[bin], Math.hypot(x, y));
            }
            double min = Double.POSITIVE_INFINITY;
            for (double r : radii) min = Math.min(min, r);
            for (int i = 0; i < 64; i++) radii[i] = (Double.isInfinite(radii[i]) ? min : radii[i]) - .7;
        }
    }
}
