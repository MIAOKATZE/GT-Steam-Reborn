package com.miaokatze.gtsr.client.critical;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import com.google.gson.JsonObject;

/** Installation-local, monotonically integrated R5 mechanism clocks. Sampling never advances time. */
final class CriticalContinuousMotion {

    private static final double TAU = Math.PI * 2;
    static final Sample ZERO = new Sample(0, 0, 0, 0, 0, 0);

    private CriticalContinuousMotion() {}

    /** Monotonic wall-clock adapter, separate from world time and deterministic in native probes. */
    static class FrameClock {

        private double last = Double.NaN, seconds;
        private long frame = Long.MIN_VALUE;

        double update(long frameId, double now, boolean paused) {
            if (frameId == frame) return seconds;
            frame = frameId;
            // Long absences suspend this anchor; loading or returning to view never catches up in one jump.
            if (!Double.isNaN(last) && !paused && now - last <= 1) seconds += Math.max(0, now - last);
            last = now;
            return seconds;
        }
    }

    static final class Context {

        private final Map<String, Clock> clocks = new HashMap<>();
        private final Map<JsonObject, AffinePose> poses = new IdentityHashMap<>();
        private double seconds;
        private long frames;

        /** Call exactly once before drawing this installation; all readers see the resulting snapshot. */
        void beginFrame(double now, Iterable<JsonObject> groups, String mode) {
            now = Math.max(seconds, Math.max(0, now));
            for (JsonObject group : groups) register(group);
            double dt = now - seconds;
            for (Clock clock : clocks.values()) {
                clock.integrate(dt);
                clock.target = speed(clock.meta, mode);
                clock.snapshot = CriticalContinuousMotion.sample(clock.meta, clock.theta, clock.velocity);
            }
            seconds = now;
            poses.clear();
            frames++;
        }

        double[] affine(JsonObject group, double phase, double at, String mode) {
            AffinePose cached = poses.get(group);
            if (cached != null && cached.phase == phase && cached.seconds == at && cached.mode.equals(mode))
                return cached.matrix;
            double[] matrix = CriticalAnimation.affineUncached(group, phase, at, mode);
            poses.put(group, new AffinePose(phase, at, mode, matrix));
            return matrix;
        }

        private Clock register(JsonObject group) {
            if (!group.has("continuousMotion")) return null;
            JsonObject meta = group.getAsJsonObject("continuousMotion");
            String id = meta.get("clockId")
                .getAsString();
            Clock clock = clocks.get(id);
            if (clock == null) {
                clock = new Clock(meta, seconds);
                clocks.put(id, clock);
            } else if (!clock.meta.equals(meta)) throw new IllegalArgumentException("Conflicting motion clock " + id);
            return clock;
        }

        Sample sample(JsonObject group) {
            if (!group.has("continuousMotion")) return ZERO;
            Clock clock = clocks.get(
                group.getAsJsonObject("continuousMotion")
                    .get("clockId")
                    .getAsString());
            // Groups must be registered by beginFrame, never by a voxel/FX reader.
            if (clock == null) throw new IllegalStateException("Motion group outside frame snapshot");
            return clock.snapshot;
        }

        double seconds() {
            return seconds;
        }

        long frames() {
            return frames;
        }

        int clockCount() {
            return clocks.size();
        }
    }

    private static final class AffinePose {

        final double phase, seconds;
        final String mode;
        final double[] matrix;

        AffinePose(double phase, double seconds, String mode, double[] matrix) {
            this.phase = phase;
            this.seconds = seconds;
            this.mode = mode;
            this.matrix = matrix;
        }
    }

    static Sample idleSample(JsonObject group, double seconds) {
        if (!group.has("continuousMotion")) return ZERO;
        JsonObject meta = group.getAsJsonObject("continuousMotion");
        double velocity = speed(meta, "idle");
        return sample(meta, CriticalAnimation.number(meta, "phase", 0) + velocity * Math.max(0, seconds), velocity);
    }

    private static double speed(JsonObject meta, String mode) {
        return TAU
            / CriticalAnimation.number(meta, "working".equals(mode) ? "workPeriodSeconds" : "idlePeriodSeconds", 60)
            * CriticalAnimation.number(meta, "direction", 1);
    }

    private static Sample sample(JsonObject meta, double theta, double velocity) {
        boolean rotate = "rotateY".equals(
            meta.get("kind")
                .getAsString());
        double offset = rotate ? 0 : CriticalAnimation.number(meta, "amplitude", 0) * Math.sin(theta);
        String axis = meta.has("axis") ? meta.get("axis")
            .getAsString() : "y";
        return new Sample(
            theta,
            velocity,
            rotate ? theta : 0,
            "x".equals(axis) ? offset : 0,
            "y".equals(axis) ? offset : 0,
            "z".equals(axis) ? offset : 0);
    }

    static final class Sample {

        final double theta, velocity, angle, x, y, z;

        Sample(double theta, double velocity, double angle, double x, double y, double z) {
            this.theta = theta;
            this.velocity = velocity;
            this.angle = angle;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class Clock {

        final JsonObject meta;
        double theta, velocity, target;
        Sample snapshot;

        Clock(JsonObject meta, double seconds) {
            this.meta = meta;
            velocity = target = speed(meta, "idle");
            theta = CriticalAnimation.number(meta, "phase", 0) + velocity * seconds;
            snapshot = sample(meta, theta, velocity);
        }

        void integrate(double dt) {
            double tau = Math.max(.001, CriticalAnimation.number(meta, "responseSeconds", .7));
            double decay = Math.exp(-dt / tau), delta = velocity - target;
            theta += target * dt + delta * tau * (1 - decay);
            velocity = target + delta * decay;
        }
    }
}
