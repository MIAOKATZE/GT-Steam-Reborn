package com.miaokatze.gtsr.client.critical;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Direct port of preview/viewer.js sample and VOXEL_TRANSFORM, in voxel coordinates. */
final class CriticalAnimation {

    private static CriticalContinuousMotion.Context current;

    static void beginFrame(CriticalContinuousMotion.Context context) {
        current = context;
    }

    static void endFrame() {
        current = null;
    }

    static CriticalContinuousMotion.Context currentContext() {
        return current;
    }

    private CriticalAnimation() {}

    static double[] transform(double[] position, JsonObject g, double phase, double seconds, String mode) {
        if (current == null) return transformUncached(position, g, phase, seconds, mode);
        double[] m = current.affine(g, phase, seconds, mode);
        return new double[] { m[0] * position[0] + m[4] * position[1] + m[8] * position[2] + m[12],
            m[1] * position[0] + m[5] * position[1] + m[9] * position[2] + m[13],
            m[2] * position[0] + m[6] * position[1] + m[10] * position[2] + m[14] };
    }

    static double[] affineUncached(JsonObject g, double phase, double seconds, String mode) {
        double[] origin = transformUncached(new double[3], g, phase, seconds, mode), result = new double[16];
        for (int column = 0; column < 3; column++) {
            double[] unit = new double[3];
            unit[column] = 1;
            double[] moved = transformUncached(unit, g, phase, seconds, mode);
            for (int row = 0; row < 3; row++) result[column * 4 + row] = moved[row] - origin[row];
        }
        System.arraycopy(origin, 0, result, 12, 3);
        result[15] = 1;
        return result;
    }

    private static double[] transformUncached(double[] position, JsonObject g, double phase, double seconds,
        String mode) {
        double[] pivot = vector(g, "pivot", new double[3]);
        Pose pose = sample(g, phase, mode);
        CriticalContinuousMotion.Sample continuous = current == null ? CriticalContinuousMotion.idleSample(g, seconds)
            : current.sample(g);
        double[] q = sub(position, pivot);
        for (int i = 0; i < 3; i++) q[i] *= pose.scale;
        if (g.has("ambientRotation")) {
            JsonObject a = g.getAsJsonObject("ambientRotation");
            q = rotate(q, axis(a), seconds * Math.PI * 2 / number(a, "periodSeconds", 60) * number(a, "direction", 1));
        }
        q = rotate(
            q,
            axis(g),
            g.has("cycleRotation") && !g.get("cycleRotation")
                .getAsBoolean() ? 0 : pose.angle);
        // Independent of the cycle pose: piston offsets and scale must survive the crown's work spin.
        if (g.has("workSpin") && "working".equals(mode)) {
            JsonObject spin = g.getAsJsonObject("workSpin");
            double ramp = Math.min(.49, Math.max(.001, number(spin, "ramp", .2)));
            q = rotate(q, axis(g), spinAngle(spin, phase, ramp, 0));
        }
        if (g.has("ambientTilt")) {
            JsonObject a = g.getAsJsonObject("ambientTilt");
            double[] tp = vector(a, "pivot", pivot);
            q = sub(
                add(
                    rotate(
                        sub(add(q, pivot), tp),
                        axis(a),
                        number(a, "amplitudeDeg", 0) * Math.PI
                            / 180
                            * Math.sin(seconds * Math.PI * 2 / number(a, "periodSeconds", 12) + number(a, "phase", 0))),
                    tp),
                pivot);
        }
        JsonObject idle = g.has("idleMotion") ? g.getAsJsonObject("idleMotion") : new JsonObject();
        double wave = Math.sin(seconds * Math.PI * 2 / number(idle, "periodSeconds", 12) + number(idle, "phase", 0));
        if (idle.has("modes")) {
            boolean enabled = false;
            for (com.google.gson.JsonElement value : idle.getAsJsonArray("modes"))
                if (mode.equals(value.getAsString())) enabled = true;
            if (!enabled) wave = 0;
        }
        q = rotate(q, axis(idle), number(idle, "angleDeg", 0) * Math.PI / 180 * wave);
        q = rotate(q, new double[] { 0, 1, 0 }, continuous.angle);
        double[] idleOffset = vector(idle, "offset", new double[3]);
        double[] workOffset = new double[3];
        if (g.has("workMotion")) {
            JsonObject work = g.getAsJsonObject("workMotion");
            double[] range = work.has("activePhase") ? new double[] { work.getAsJsonArray("activePhase")
                .get(0)
                .getAsDouble(),
                work.getAsJsonArray("activePhase")
                    .get(1)
                    .getAsDouble() }
                : new double[] { .28, .83 };
            double fade = Math.max(.001, number(work, "fadePhase", .05));
            double gain = "working".equals(mode) ? smooth((phase - range[0]) / fade) * smooth((range[1] - phase) / fade)
                : 0;
            double workWave = Math
                .sin(seconds * Math.PI * 2 / number(work, "periodSeconds", 3) + number(work, "phase", 0)) * gain;
            q = rotate(q, axis(work), number(work, "angleDeg", 0) * Math.PI / 180 * workWave);
            workOffset = vector(work, "offset", new double[3]);
            for (int i = 0; i < 3; i++) workOffset[i] *= workWave;
        }
        if (g.has("cycleOffsetSpace") && "local-before-continuous-rotation".equals(
            g.get("cycleOffsetSpace")
                .getAsString()))
            pose.offset = rotate(pose.offset, new double[] { 0, 1, 0 }, continuous.angle);
        for (int i = 0; i < 3; i++) q[i] += pivot[i] + pose.offset[i] + idleOffset[i] * wave + workOffset[i];
        q[0] += continuous.x;
        q[1] += continuous.y;
        q[2] += continuous.z;
        if (g.has("ambientOrbit")) {
            JsonObject a = g.getAsJsonObject("ambientOrbit");
            double[] center = vector(a, "pivot", pivot);
            double angle = seconds * Math.PI
                * 2
                / number(a, "periodSeconds", 60)
                * number(a, "direction", 1)
                * number(a, "angleDeg", 360)
                / 360
                + (a.has("cycleAngle") && a.get("cycleAngle")
                    .getAsBoolean() ? sample(g, phase, "build").angle : 0);
            q = add(rotate(sub(q, center), axis(a), angle), center);
        }
        q[1] += levitationBob(g, seconds);
        return q;
    }

    static double levitationBob(JsonObject g, double seconds) {
        if (!g.has("levitationBob")) return 0;
        JsonObject bob = g.getAsJsonObject("levitationBob");
        return number(bob, "amplitude", 0)
            * Math.sin(seconds * Math.PI * 2 / number(bob, "periodSeconds", 12) + number(bob, "phase", 0));
    }

    private static Pose sample(JsonObject g, double phase, String mode) {
        Pose result = new Pose();
        if ("idle".equals(mode)) return result;
        if (g.has("continuousSpin")) {
            JsonObject spin = g.getAsJsonObject("continuousSpin");
            result.angle = spinAngle(spin, phase, number(spin, "ramp", .22), 1);
            return result;
        }
        JsonObject cycle = g.has("cycle") ? g.getAsJsonObject("cycle") : null;
        if (g.has("cyclesByMode") && g.getAsJsonObject("cyclesByMode")
            .has(mode))
            cycle = g.getAsJsonObject("cyclesByMode")
                .getAsJsonObject(mode);
        if (cycle == null || !cycle.has("keyframes")) return result;
        JsonArray frames = cycle.getAsJsonArray("keyframes");
        if (frames.size() == 0) return result;
        JsonObject left = frames.get(0)
            .getAsJsonObject(),
            right = frames.get(frames.size() - 1)
                .getAsJsonObject();
        for (int i = 1; i < frames.size(); i++) {
            JsonObject l = frames.get(i - 1)
                .getAsJsonObject(),
                rr = frames.get(i)
                    .getAsJsonObject();
            if (phase >= number(l, "phase", 0) && phase <= number(rr, "phase", 1)) {
                left = l;
                right = rr;
                break;
            }
        }
        double t = Math.max(
            0,
            Math.min(
                1,
                (phase - number(left, "phase", 0))
                    / Math.max(1e-9, number(right, "phase", 1) - number(left, "phase", 0))));
        String ease = left.has("easing") ? left.get("easing")
            .getAsString() : "smoothstep";
        if ("hold".equals(ease)) t = phase >= number(right, "phase", 1) ? 1 : 0;
        else if (!"linear".equals(ease)) t = t * t * (3 - 2 * t);
        result.angle = lerp(number(left, "angle", 0), number(right, "angle", 0), t);
        result.scale = lerp(number(left, "scale", 1), number(right, "scale", 1), t);
        double[] a = vector(left, "offset", new double[3]), b = vector(right, "offset", new double[3]);
        for (int i = 0; i < 3; i++) result.offset[i] = lerp(a[i], b[i], t);
        return result;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private static double spinAngle(JsonObject spin, double phase, double r, double defaultTurns) {
        double x = Math.max(0, Math.min(1, phase)), u = x / r, integral;
        if (x < r) integral = r * (u * u * u - .5 * u * u * u * u);
        else if (x <= 1 - r) integral = r * .5 + x - r;
        else {
            u = (x - (1 - r)) / r;
            integral = 1 - 1.5 * r + r * (u - u * u * u + .5 * u * u * u * u);
        }
        return Math.PI * 2 * number(spin, "turns", defaultTurns) * number(spin, "direction", 1) * integral / (1 - r);
    }

    static double number(JsonObject object, String key, double fallback) {
        return object.has(key) ? object.get(key)
            .getAsDouble() : fallback;
    }

    static double[] vector(JsonObject object, String key, double[] fallback) {
        if (!object.has(key)) return fallback;
        JsonArray array = object.getAsJsonArray(key);
        return new double[] { array.get(0)
            .getAsDouble(),
            array.get(1)
                .getAsDouble(),
            array.get(2)
                .getAsDouble() };
    }

    static double[] axis(JsonObject g) {
        if (!g.has("axis")) return new double[] { 0, 1, 0 };
        if (g.get("axis")
            .isJsonArray()) return vector(g, "axis", new double[] { 0, 1, 0 });
        return switch (g.get("axis")
            .getAsString()) {
            case "x" -> new double[] { 1, 0, 0 };
            case "z" -> new double[] { 0, 0, 1 };
            default -> new double[] { 0, 1, 0 };
        };
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double[] add(double[] a, double[] b) {
        return new double[] { a[0] + b[0], a[1] + b[1], a[2] + b[2] };
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
    }

    private static double[] rotate(double[] v, double[] axis, double angle) {
        double n = Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
        if (n == 0 || angle == 0) return v;
        double x = axis[0] / n, y = axis[1] / n, z = axis[2] / n, c = Math.cos(angle), s = Math.sin(angle),
            d = v[0] * x + v[1] * y + v[2] * z;
        return new double[] { v[0] * c + (y * v[2] - z * v[1]) * s + x * d * (1 - c),
            v[1] * c + (z * v[0] - x * v[2]) * s + y * d * (1 - c),
            v[2] * c + (x * v[1] - y * v[0]) * s + z * d * (1 - c) };
    }

    private static final class Pose {

        double angle, scale = 1;
        double[] offset = new double[3];
    }
}
