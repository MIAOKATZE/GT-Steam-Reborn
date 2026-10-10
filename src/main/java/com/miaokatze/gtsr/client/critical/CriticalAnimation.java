package com.miaokatze.gtsr.client.critical;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Direct port of preview/viewer.js sample and VOXEL_TRANSFORM, in voxel coordinates. */
final class CriticalAnimation {

    private CriticalAnimation() {}

    static double[] transform(double[] position, JsonObject g, double phase, double seconds, String mode) {
        double[] pivot = vector(g, "pivot", new double[3]);
        Pose pose = sample(g, phase, mode);
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
        q = rotate(q, axis(idle), number(idle, "angleDeg", 0) * Math.PI / 180 * wave);
        double[] idleOffset = vector(idle, "offset", new double[3]);
        for (int i = 0; i < 3; i++) q[i] += pivot[i] + pose.offset[i] + idleOffset[i] * wave;
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
                    .getAsBoolean() ? pose.angle : 0);
            q = add(rotate(sub(q, center), axis(a), angle), center);
        }
        return q;
    }

    private static Pose sample(JsonObject g, double phase, String mode) {
        Pose result = new Pose();
        if ("idle".equals(mode)) return result;
        if (g.has("continuousSpin")) {
            JsonObject spin = g.getAsJsonObject("continuousSpin");
            double x = Math.max(0, Math.min(1, phase)), r = number(spin, "ramp", .22), u = x / r, integral;
            if (x < r) integral = r * (u * u * u - .5 * u * u * u * u);
            else if (x <= 1 - r) integral = r * .5 + x - r;
            else {
                u = (x - (1 - r)) / r;
                integral = 1 - 1.5 * r + r * (u - u * u * u + .5 * u * u * u * u);
            }
            result.angle = Math.PI * 2 * number(spin, "turns", 1) * number(spin, "direction", 1) * integral / (1 - r);
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
