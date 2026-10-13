package com.miaokatze.gtsr.client.critical;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Approved Solar R4 causal routes. Immutable source data and affine poses are cached, with no world queries. */
final class CriticalSolarFX {

    static final int LOCAL_LIMIT = 800;
    private static final Map<JsonObject, ModelData> MODELS = new IdentityHashMap<>();
    private static final Map<JsonObject, Pose> POSES = new IdentityHashMap<>();
    private static final String[] KEYS = { "concentrators", "thermalRoutes", "heatExchange", "steamOutlets",
        "dataRoutes", "coolingSteam", "driveRing" };
    private static final String[] FAMILIES = { "solar-concentration", "solar-heat-transfer", "solar-thermal",
        "solar-steam", "solar-data", "solar-cooling", "solar-drive" };
    private static final int[] WORK = { 224, 80, 64, 24, 64, 80, 128 }, IDLE = { 64, 8, 16, 4, 24, 8, 48 };
    private static final double[][] COLORS = { { 1, .80, .38 }, { .94, .59, .29 }, { 1, .50, .20 }, { .78, .88, .98 },
        { .36, .66, .91 }, { .65, .82, .94 }, { .31, .64, .95 } };
    private static final double[] ALPHA = { .34, .27, .29, .19, .23, .17, .29 },
        SIZE = { .52, .38, .42, .45, .29, .42, .43 }, LO = { .05, .16, .18, .42, .02, .40, .04 },
        HI = { .86, .86, .86, .96, .98, .97, .96 }, GRAVITY_COLOR = { .43, .64, .94 },
        CONCENTRATION_GLOW = { 1, .93, .62 }, THERMAL_GLOW = { 1, .65, .30 }, STEAM_GLOW = { .66, .85, 1 };
    private static final double[] POINT = new double[3], MOVED = new double[3];

    private CriticalSolarFX() {}

    interface ParticleSink {

        void emit(String family, int index, double[] center, double[] color, double alpha, double[] glowColor,
            double glowAlpha, double size);
    }

    static void clear() {
        MODELS.clear();
        POSES.clear();
    }

    /** Pure production sampler; the standalone regression probe compares this to actual solar_fx.js output. */
    static int sample(JsonObject[] metas, JsonObject[] animations, String[] slugs, double phase, double seconds,
        int lod, Set<String> visible, ParticleSink sink) {
        if (lod == 0) return 0;
        boolean work = phase > 0 && phase < 1;
        int emitted = 0, familyIndex = 0;
        for (int owner = 0; owner < metas.length; owner++) {
            ModelData data = data(metas[owner], animations[owner]);
            for (Gravity gravity : data.gravity) {
                double cycle = seconds * .65 + gravity.height * .07;
                Member member = gravity.members[Math.floorMod((int) Math.floor(cycle), gravity.members.length)];
                JsonObject group = group(animations[owner], member.group);
                if (!visible(visible, slugs[owner], member.group, group)) continue;
                int index = familyIndex++;
                if (index % lod != 0) continue;
                boolean transferredLoom = "v2_base_t3".equals(slugs[owner]) && member.group != null
                    && member.group.startsWith("loom_");
                transform(
                    member.position,
                    group,
                    phase,
                    seconds,
                    owner == 1 || transferredLoom ? "idle" : mode(group, work),
                    MOVED);
                double progress = wrap(cycle);
                MOVED[1] -= .65 + 2.8 * progress;
                double alpha = (work ? .28 : .21) * Math.sin(Math.PI * progress);
                sink.emit(
                    "gravity",
                    index,
                    MOVED,
                    GRAVITY_COLOR,
                    alpha,
                    GRAVITY_COLOR,
                    alpha * (work ? 1.2 : .78),
                    work ? .32 : .26);
                emitted++;
            }
        }
        ModelData solar = data(metas[2], animations[2]);
        for (int family = 0; family < KEYS.length; family++) {
            List<Route> sources = solar.routes.get(family);
            familyIndex = 0;
            int budget = work ? WORK[family] : IDLE[family];
            boolean steam = family == 3 || family == 5;
            double size = SIZE[family] * (work ? 1 : .64),
                fade = work ? Math.max(0, Math.min(1, Math.min((phase - LO[family]) / .05, (HI[family] - phase) / .05)))
                    : .24;
            double[] glow = family == 0 ? CONCENTRATION_GLOW
                : family == 1 || family == 2 ? THERMAL_GLOW : steam ? STEAM_GLOW : COLORS[family];
            for (int rank = 0; rank < sources.size(); rank++) {
                Route source = sources.get(rank);
                int count = budget / sources.size() + (rank < budget % sources.size() ? 1 : 0);
                JsonObject g = group(animations[2], source.group), target = group(animations[2], source.targetGroup);
                // Sampling indices stay global within a family, even when a rigid group is temporarily absent.
                if (!visible(visible, slugs[2], source.group, g)) {
                    familyIndex += count;
                    continue;
                }
                String mode = mode(g, work), targetMode = mode(target, work);
                for (int j = 0; j < source.path.length; j++) {
                    double[] q = source.path[j];
                    JsonObject pose = family == 0 ? (j == 0 ? g : target) : source.followPose ? g : null;
                    if (family == 0 && j == source.path.length - 1 && source.targetPosition != null)
                        q = source.targetPosition;
                    transform(q, pose, phase, seconds, family == 0 && j > 0 ? targetMode : mode, source.posedPath[j]);
                }
                vector(source.normal, g, phase, seconds, mode, source.posedNormal);
                if (steam) {
                    vector(new double[] { 1, 0, 0 }, g, phase, seconds, mode, source.basisX);
                    vector(new double[] { 0, 0, 1 }, g, phase, seconds, mode, source.basisZ);
                }
                for (int i = 0; i < count; i++) {
                    int index = familyIndex++;
                    if (index % lod != 0) continue;
                    double progress = wrap(
                        seconds * (steam ? .34 : family == 6 ? .27 : .19) + i / (double) count
                            + phase * .35
                            + rank * .071);
                    route(source.posedPath, progress, POINT);
                    if (family == 1) {
                        double dx = POINT[0] - 56, dz = POINT[2] - 56, length = Math.hypot(dx, dz);
                        if (length == 0) length = 1;
                        POINT[0] += .64 * dx / length;
                        POINT[2] += .64 * dz / length;
                    }
                    if (family == 6) for (int d = 0; d < 3; d++) POINT[d] += .62 * source.posedNormal[d];
                    if (steam) {
                        double spread = .16 + progress * (family == 5 ? 1 : .60),
                            x = Math.cos(i * 2.399 + seconds) * spread, z = Math.sin(i * 2.399 + seconds) * spread;
                        for (int d = 0; d < 3; d++) POINT[d] += source.basisX[d] * x + source.basisZ[d] * z;
                    }
                    double particleSize = size * (steam ? .8 + progress * .7 : 1);
                    if (steam) particleSize = Math.min(particleSize, Math.max(.08, 179 - POINT[1]));
                    double alpha = ALPHA[family] * fade * Math.sin(Math.PI * progress);
                    sink.emit(
                        FAMILIES[family],
                        index,
                        POINT,
                        COLORS[family],
                        alpha,
                        glow,
                        alpha * (steam ? .55 : work ? 1.2 : .78),
                        particleSize);
                    emitted++;
                }
            }
        }
        if (emitted > LOCAL_LIMIT) throw new IllegalStateException("Solar FX exceeds approved local limit: " + emitted);
        return emitted;
    }

    private static ModelData data(JsonObject meta, JsonObject animations) {
        return MODELS.computeIfAbsent(meta, ignored -> new ModelData(meta));
    }

    private static boolean visible(Set<String> visible, String slug, String name, JsonObject group) {
        return group == null || visible == null || visible.contains(slug + ':' + name);
    }

    private static JsonObject group(JsonObject groups, String name) {
        return name != null && groups.has(name) ? groups.getAsJsonObject(name) : null;
    }

    private static String mode(JsonObject group, boolean work) {
        return group != null && (group.has("workSpin") || group.has("workMotion")) ? work ? "working" : "idle"
            : "build";
    }

    private static void route(double[][] route, double progress, double[] out) {
        double q = progress * (route.length - 1);
        int segment = Math.min(route.length - 2, (int) Math.floor(q));
        for (int d = 0; d < 3; d++)
            out[d] = route[segment][d] + (route[segment + 1][d] - route[segment][d]) * (q - segment);
    }

    private static void vector(double[] value, JsonObject animation, double phase, double seconds, String mode,
        double[] out) {
        Pose pose = pose(animation, phase, seconds, mode);
        if (pose == null) System.arraycopy(value, 0, out, 0, 3);
        else for (int row = 0; row < 3; row++)
            out[row] = value[0] * pose.values[row] + value[1] * pose.values[3 + row] + value[2] * pose.values[6 + row];
    }

    private static void transform(double[] point, JsonObject animation, double phase, double seconds, String mode,
        double[] out) {
        Pose pose = pose(animation, phase, seconds, mode);
        if (pose == null) System.arraycopy(point, 0, out, 0, 3);
        else for (int row = 0; row < 3; row++) out[row] = point[0] * pose.values[row] + point[1] * pose.values[3 + row]
            + point[2] * pose.values[6 + row]
            + pose.values[9 + row];
    }

    private static Pose pose(JsonObject animation, double phase, double seconds, String mode) {
        if (animation == null) return null;
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
        return pose;
    }

    private static double wrap(double x) {
        return ((x % 1) + 1) % 1;
    }

    private static double[] vector(JsonArray v) {
        return new double[] { v.get(0)
            .getAsDouble(),
            v.get(1)
                .getAsDouble(),
            v.get(2)
                .getAsDouble() };
    }

    private static final class Pose {

        final double[] values = new double[12];
        double phase = Double.NaN, seconds = Double.NaN;
        String mode = "";
    }

    private static final class Member {

        final double[] position;
        final String group;

        Member(double[] position, String group) {
            this.position = position;
            this.group = group;
        }
    }

    private static final class Gravity {

        final Member[] members;
        final double height;

        Gravity(List<Member> points) {
            members = points.toArray(new Member[0]);
            double cx = 0, cy = 0, cz = 0;
            for (Member member : members) {
                cx += member.position[0];
                cy += member.position[1];
                cz += member.position[2];
            }
            final double x = cx / members.length, z = cz / members.length, rx = x - 56, rz = z - 56;
            height = cy / members.length;
            Arrays.sort(members, (a, b) -> {
                int angle = Double.compare(angle(a.position, x, z, rx, rz), angle(b.position, x, z, rx, rz));
                return angle != 0 ? angle : Double.compare(radius(a.position, x, z), radius(b.position, x, z));
            });
        }

        private static double angle(double[] p, double cx, double cz, double rx, double rz) {
            double dx = p[0] - cx, dz = p[2] - cz, radial = dx * rx + dz * rz, tangent = dz * rx - dx * rz;
            return Math.atan2(Math.abs(tangent) < 1e-9 ? 0 : tangent, Math.abs(radial) < 1e-9 ? 0 : radial);
        }

        private static double radius(double[] p, double cx, double cz) {
            return (p[0] - cx) * (p[0] - cx) + (p[2] - cz) * (p[2] - cz);
        }
    }

    private static final class Route {

        final double[][] path, posedPath;
        final double[] normal, targetPosition, posedNormal = new double[3], basisX = new double[3],
            basisZ = new double[3];
        final String group, targetGroup;
        final boolean followPose;

        Route(JsonObject source) {
            JsonArray cells = source.has("path") ? source.getAsJsonArray("path") : source.getAsJsonArray("cells");
            path = new double[cells.size()][];
            posedPath = new double[cells.size()][3];
            for (int i = 0; i < cells.size(); i++) path[i] = vector(
                cells.get(source.has("path") ? i : cells.size() - 1 - i)
                    .getAsJsonArray());
            group = source.has("group") ? source.get("group")
                .getAsString() : "";
            targetGroup = source.has("targetGroup") ? source.get("targetGroup")
                .getAsString() : "";
            normal = CriticalAnimation.vector(source, "normal", new double[] { 0, 1, 0 });
            targetPosition = source.has("targetPosition") ? CriticalAnimation.vector(source, "targetPosition", null)
                : null;
            followPose = !source.has("followPose") || source.get("followPose")
                .getAsBoolean();
        }
    }

    private static final class ModelData {

        final List<Gravity> gravity = new ArrayList<>();
        final List<List<Route>> routes = new ArrayList<>();

        ModelData(JsonObject meta) {
            Map<String, List<Member>> clusters = new LinkedHashMap<>();
            if (meta.has("gravitySources")) for (JsonElement entry : meta.getAsJsonArray("gravitySources")) {
                JsonObject source = entry.getAsJsonObject();
                String id = source.get(source.has("clusterId") ? "clusterId" : "id")
                    .getAsString();
                clusters.computeIfAbsent(id, ignored -> new ArrayList<>())
                    .add(
                        new Member(
                            CriticalAnimation.vector(source, "position", new double[3]),
                            source.get("group")
                                .getAsString()));
            }
            else if (meta.has("gravityClusters")) for (JsonElement entry : meta.getAsJsonArray("gravityClusters")) {
                JsonObject cluster = entry.getAsJsonObject();
                List<Member> members = new ArrayList<>();
                for (JsonElement point : cluster.getAsJsonArray("cells")) members.add(
                    new Member(
                        vector(point.getAsJsonArray()),
                        cluster.get("group")
                            .getAsString()));
                gravity.add(new Gravity(members));
            }
            for (List<Member> members : clusters.values()) gravity.add(new Gravity(members));
            JsonObject fx = meta.has("solarFX") ? meta.getAsJsonObject("solarFX") : new JsonObject();
            for (String key : KEYS) {
                List<Route> family = new ArrayList<>();
                if (fx.has(key))
                    for (JsonElement source : fx.getAsJsonArray(key)) family.add(new Route(source.getAsJsonObject()));
                routes.add(family);
            }
        }
    }
}
