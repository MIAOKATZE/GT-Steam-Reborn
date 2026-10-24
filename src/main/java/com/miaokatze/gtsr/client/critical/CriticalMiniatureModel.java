package com.miaokatze.gtsr.client.critical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.miaokatze.gtsr.common.critical.CriticalMachineKind;

/** Authored miniature mechanisms; no canonical voxels, world, TileEntity or GL is loaded. */
final class CriticalMiniatureModel {

    static final int MAX_PARTS = 28, MAX_FACES = 2000;
    static final double YAW = -26, PITCH = 22;

    enum Surface {

        STEEL("v2_steel", .34F, .42F, .47F, false),
        ARMOR("v2_titanium", .78F, .84F, .84F, false),
        COPPER("v2_copper", 1, .58F, .27F, false),
        DARK("v2_graphite", .22F, .29F, .33F, false),
        COLLECTOR("v2_solar_flux_collector", .30F, .62F, .77F, false),
        CYAN("v2_cyan", .16F, .91F, 1, true),
        GOLD("v2_amber", 1, .65F, .14F, true),
        VIOLET("v2_cyan", .78F, .42F, 1, true),
        STAR("v2_stellar_lens", 1, .87F, .38F, true);

        final String atlas;
        final float r, g, b;
        final boolean emission;

        Surface(String atlas, float r, float g, float b, boolean e) {
            this.atlas = atlas;
            this.r = r;
            this.g = g;
            this.b = b;
            emission = e;
        }
    }

    enum Motion {
        STILL,
        ROTATE_X,
        ROTATE_Y,
        ROCK_Z,
        PISTON_Y,
        FLOW_Y,
        BREATHE
    }

    static final class Part {

        final String name;
        final int stage;
        final Surface surface;
        final Motion motion;
        final double x, y, z, speed, amplitude, phase;
        final List<float[]> quads = new ArrayList<>();

        Part(String n, int s, Surface c, Motion m, double x, double y, double z, double rate, double amp,
            double phase) {
            name = n;
            stage = s;
            surface = c;
            motion = m;
            this.x = x;
            this.y = y;
            this.z = z;
            speed = rate;
            amplitude = amp;
            this.phase = phase;
        }
    }

    final List<Part> parts;
    final int faces;
    final double[] projectedLow = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
    final double[] projectedHigh = { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY };

    private CriticalMiniatureModel(List<Part> data) {
        parts = Collections.unmodifiableList(data);
        int count = 0;
        for (Part p : parts) {
            count += p.quads.size();
            for (int pose = 0; pose < 24; pose++) for (float[] q : p.quads) for (int v = 0; v < 4; v++) {
                double[] a = transform(p, q[v * 3], q[v * 3 + 1], q[v * 3 + 2], pose * 2.731);
                double yaw = Math.toRadians(YAW), pitch = Math.toRadians(PITCH);
                double sx = Math.cos(yaw) * a[0] + Math.sin(yaw) * a[2],
                    depth = -Math.sin(yaw) * a[0] + Math.cos(yaw) * a[2];
                double sy = Math.cos(pitch) * a[1] - Math.sin(pitch) * depth;
                projectedLow[0] = Math.min(projectedLow[0], sx);
                projectedLow[1] = Math.min(projectedLow[1], sy);
                projectedHigh[0] = Math.max(projectedHigh[0], sx);
                projectedHigh[1] = Math.max(projectedHigh[1], sy);
            }
        }
        faces = count;
        if (count > MAX_FACES || parts.size() > MAX_PARTS) throw new IllegalStateException("Miniature budget exceeded");
    }

    static CriticalMiniatureModel create(CriticalMachineKind kind) {
        Builder b = new Builder();
        b.platform();
        switch (kind) {
            case SUN -> b.sun();
            case TURBINE -> b.turbine();
            case BATTERY -> b.battery();
            case SOLAR -> b.solar();
            default -> throw new IllegalArgumentException("No miniature study for " + kind);
        }
        return new CriticalMiniatureModel(b.parts);
    }

    static double[] transform(Part p, double x, double y, double z, double seconds) {
        double a = seconds * p.speed + p.phase, sin = Math.sin(a), cos = Math.cos(a);
        switch (p.motion) {
            case ROTATE_X -> {
                double before = y;
                y = cos * y - sin * z;
                z = sin * before + cos * z;
            }
            case ROTATE_Y -> {
                double before = x;
                x = cos * x + sin * z;
                z = -sin * before + cos * z;
            }
            case ROCK_Z -> {
                double tilt = sin * p.amplitude, c = Math.cos(tilt), v = Math.sin(tilt), before = x;
                x = c * x - v * y;
                y = v * before + c * y;
            }
            case PISTON_Y -> y += sin * p.amplitude;
            case FLOW_Y -> y += ((a / (Math.PI * 2)) % 1 + 1) % 1 * p.amplitude;
            case BREATHE -> {
                double scale = 1 + sin * p.amplitude;
                x *= scale;
                y *= scale;
                z *= scale;
            }
            default -> {}
        }
        return new double[] { x + p.x, y + p.y, z + p.z };
    }

    private static final class Builder {

        final List<Part> parts = new ArrayList<>();

        Part part(String n, int s, Surface c) {
            return part(n, s, c, Motion.STILL, 0, 0, 0, 0, 0, 0);
        }

        Part part(String n, int s, Surface c, Motion m, double x, double y, double z, double speed, double amp,
            double phase) {
            Part p = new Part(n, s, c, m, x, y, z, speed, amp, phase);
            parts.add(p);
            return p;
        }

        void platform() {
            Part deck = part("foundation", 1, Surface.DARK);
            box(deck, -10.8, 0, -4.5, 10.8, .9, 4.5);
            Part trim = part("base_busbars", 1, Surface.COPPER);
            box(trim, -10.5, .9, -4.35, 10.5, 1.25, -3.7);
            box(trim, -10.5, .9, 3.7, 10.5, 1.25, 4.35);
            Part support = part("load_bearing_frame", 2, Surface.ARMOR);
            for (double x : new double[] { -8.6, 8.6 })
                for (double z : new double[] { -3.1, 3.1 }) box(support, x - .65, .9, z - .65, x + .65, 2.25, z + .65);
            Part ports = part("decorative_interface_housings", 3, Surface.STEEL);
            for (double x : new double[] { -7.5, -2.5, 2.5, 7.5 }) box(ports, x - .65, 1.2, -4.2, x + .65, 2.1, -3.1);
        }

        void sun() {
            Part support = part("confinement_supports", 2, Surface.STEEL);
            for (double x : new double[] { -4.7, 4.7 })
                for (double z : new double[] { -2.2, 2.2 }) box(support, x - .35, 1.2, z - .35, x + .35, 6.6, z + .35);
            Part saddle = part("chamber_load_stack", 4, Surface.ARMOR);
            cylinder(saddle, 0, 1.25, 0, 3.3, 1.3, 12, 1);
            for (int i = 0; i < 3; i++) {
                Part r = part(
                    "confinement_" + i,
                    4,
                    Surface.COPPER,
                    Motion.ROTATE_Y,
                    0,
                    6,
                    0,
                    i == 1 ? -.23 : .19 + i * .035,
                    0,
                    i * Math.PI / 3);
                ring(r, 4.55, .38, .48, 20, 2);
                box(r, -5.1, -.55, -.42, -4.2, .55, .42);
                box(r, 4.2, -.55, -.42, 5.1, .55, .42);
            }
            Part star = part("star", 4, Surface.STAR, Motion.BREATHE, 0, 6, 0, 1.9, .09, 0);
            sphere(star, 2.25, 12, 6);
            Part inner = part("coil_stage_0", 4, Surface.GOLD, Motion.ROTATE_Y, 0, 6, 0, -.4, 0, 0);
            ring(inner, 2.8, .14, .2, 16, 1);
            Part piston = part("chamber_piston_0", 4, Surface.ARMOR, Motion.PISTON_Y, 0, 2.4, 2.8, 2.5, .35, 0);
            box(piston, -1, 0, -.5, 1, 1.3, .5);
        }

        void turbine() {
            Part housing = part("turbine_bearing_housings", 4, Surface.ARMOR);
            for (double x : new double[] { -7.6, 7.6 }) {
                box(housing, x - .8, 1.2, -2, x + .8, 5, 2);
                cylinder(housing, x - .9, 5.1, 0, 1.35, 1.8, 12, 0);
            }
            Part shaft = part("chamber_drive_0", 4, Surface.STEEL, Motion.ROTATE_X, 0, 5.1, 0, 1.3, 0, 0);
            cylinder(shaft, -9, 0, 0, .65, 18, 12, 0);
            for (int i = 0; i < 3; i++) {
                Part r = part(
                    "rotor_" + i,
                    4,
                    Surface.COPPER,
                    Motion.ROTATE_X,
                    -4.2 + i * 4.2,
                    5.1,
                    0,
                    1.3 + i * .18,
                    0,
                    i * .45);
                ring(r, 2.8, .27, .5, 16, 0);
                cylinder(r, -.4, 0, 0, .95, .8, 8, 0);
                for (int blade = 0; blade < 6; blade++) radialBlade(r, blade * Math.PI / 3);
            }
            Part steam = part("steam_manifold", 4, Surface.COPPER);
            for (double z : new double[] { -2.8, 2.8 }) {
                cylinder(steam, -8.4, 2, z, .46, 6.8, 8, 1);
                cylinder(steam, 8.4, 2, z, .46, 6.8, 8, 1);
                cylinder(steam, -8.4, 8.8, z, .46, 16.8, 8, 0);
            }
            Part piston = part("chamber_piston_0", 4, Surface.ARMOR, Motion.PISTON_Y, 0, 2.2, 3.1, 3.2, .55, 0);
            box(piston, -2.2, 0, -.4, 2.2, .7, .4);
            Part pulse = part("admission_pulse", 4, Surface.CYAN, Motion.FLOW_Y, -8.4, 2.1, 2.8, 2.5, 5.7, 0);
            cylinder(pulse, 0, 0, 0, .58, .45, 8, 1);
        }

        void battery() {
            Part tanks = part("electrode_banks", 4, Surface.STEEL), caps = part("bank_isolators", 4, Surface.ARMOR);
            Part conduit = part("bank_transfer_manifold", 4, Surface.COPPER);
            cylinder(conduit, -7.8, 2, 2.9, .48, 15.6, 8, 0);
            Surface[] spectra = { Surface.CYAN, Surface.GOLD, Surface.VIOLET };
            for (int i = 0; i < 3; i++) {
                double x = -6.3 + i * 6.3;
                cylinder(tanks, x, 2.2, 0, 2.05, 5.7, 12, 1);
                cylinder(caps, x, 1.3, 0, 2.35, .9, 12, 1);
                cylinder(caps, x, 7.9, 0, 2.35, .85, 12, 1);
                Part window = part("charge_core_" + i, 4, spectra[i]);
                box(window, x - 1.15, 2.8, 1.8, x + 1.15, 7.2, 2.06);
                Part hoop = part(
                    "bank_half_hoop_" + i,
                    4,
                    Surface.COPPER,
                    Motion.ROTATE_Y,
                    x,
                    5.2,
                    0,
                    i == 1 ? -.55 : .43,
                    0,
                    i * 1.8);
                ring(hoop, 2.4, .24, .42, 14, 1);
                box(hoop, 2.15, -.52, -.4, 2.85, .52, .4);
                Part pulse = part("electrode_" + i, 4, spectra[i], Motion.FLOW_Y, x, 2.55, 2.22, 2.15, 3.8, i * 2.1);
                box(pulse, -1.32, 0, -.1, 1.32, .34, .1);
                cylinder(conduit, x, 2, 2.9, .42, 1, 8, 1);
            }
        }

        void solar() {
            Part columns = part("array_columns", 4, Surface.ARMOR), receiver = part("receiver", 4, Surface.COPPER);
            box(receiver, -8, 3.1, -2.6, 8, 4, -1.65);
            Surface[] channels = { Surface.GOLD, Surface.CYAN, Surface.GOLD };
            for (int i = 0; i < 3; i++) {
                double x = -6.5 + i * 6.5;
                box(columns, x - .72, 1.3, -.7, x + .72, 6.3, .7);
                Part collector = part("petal_" + i, 4, Surface.COLLECTOR, Motion.ROCK_Z, x, 6.7, 0, 1.15, .18, i * 1.9);
                box(collector, -2.6, -.25, -2, 2.6, .25, 2);
                Part rim = part("heat_louver_" + i, 4, Surface.COPPER, Motion.ROCK_Z, x, 6.7, 0, 1.15, .18, i * 1.9);
                box(rim, -2.8, -.42, -2.2, 2.8, -.2, -1.7);
                box(rim, -2.8, -.42, 1.7, 2.8, -.2, 2.2);
                Part channel = part("solar_drive_ring_" + i, 4, channels[i]);
                box(channel, x - .27, 2.2, .8, x + .27, 6.3, 1.15);
                Part flow = part("solar_valve_" + i, 4, Surface.CYAN, Motion.FLOW_Y, x, 2.1, 1.3, 2.4, 3.6, i * 2.1);
                box(flow, -.55, 0, -.12, .55, .35, .12);
            }
            Part piston = part("solar_piston", 4, Surface.STEEL, Motion.PISTON_Y, 0, 2, 2.8, 2, .4, 0);
            box(piston, -1.1, 0, -.45, 1.1, 1.1, .45);
        }

        static void radialBlade(Part p, double angle) {
            int start = p.quads.size();
            box(p, -.22, .8, -.38, .22, 2.65, .38);
            for (int i = start; i < p.quads.size(); i++) {
                float[] q = p.quads.get(i);
                for (int v = 0; v < 4; v++) {
                    float y = q[v * 3 + 1], z = q[v * 3 + 2];
                    q[v * 3 + 1] = (float) (Math.cos(angle) * y - Math.sin(angle) * z);
                    q[v * 3 + 2] = (float) (Math.sin(angle) * y + Math.cos(angle) * z);
                }
            }
        }

        static void box(Part p, double x0, double y0, double z0, double x1, double y1, double z1) {
            quad(p, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
            quad(p, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
            quad(p, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
            quad(p, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1);
            quad(p, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1);
            quad(p, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        }

        static void cylinder(Part p, double x, double y, double z, double r, double length, int segments, int axis) {
            int start = p.quads.size();
            for (int i = 0; i < segments; i++) {
                double a = i * Math.PI * 2 / segments, b = (i + 1) * Math.PI * 2 / segments;
                double[] a0 = cp(x, y, z, r, 0, a, axis), a1 = cp(x, y, z, r, length, a, axis),
                    b0 = cp(x, y, z, r, 0, b, axis), b1 = cp(x, y, z, r, length, b, axis);
                quad(p, a0, b0, b1, a1);
                double[] c0 = cp(x, y, z, 0, 0, 0, axis), c1 = cp(x, y, z, 0, length, 0, axis);
                quad(p, c0, b0, a0, c0);
                quad(p, c1, a1, b1, c1);
            }
            if (axis == 0) reverseFrom(p, start);
        }

        static double[] cp(double x, double y, double z, double radius, double distance, double angle, int axis) {
            double u = Math.cos(angle) * radius, v = Math.sin(angle) * radius;
            return axis == 0 ? new double[] { x + distance, y + u, z + v }
                : new double[] { x + u, y + distance, z + v };
        }

        static void ring(Part p, double radius, double width, double depth, int segments, int axis) {
            int start = p.quads.size();
            for (int i = 0; i < segments; i++) {
                double a = i * Math.PI * 2 / segments, b = (i + 1) * Math.PI * 2 / segments;
                double[] ao = rp(radius + width, -depth / 2, a, axis), bo = rp(radius + width, -depth / 2, b, axis);
                double[] ai = rp(radius - width, -depth / 2, a, axis), bi = rp(radius - width, -depth / 2, b, axis);
                double[] at = rp(radius + width, depth / 2, a, axis), bt = rp(radius + width, depth / 2, b, axis);
                double[] it = rp(radius - width, depth / 2, a, axis), jt = rp(radius - width, depth / 2, b, axis);
                quad(p, ao, bo, bt, at);
                quad(p, bi, ai, it, jt);
                quad(p, ai, bi, bo, ao);
                quad(p, at, bt, jt, it);
            }
            if (axis != 1) reverseFrom(p, start);
        }

        static void reverseFrom(Part p, int start) {
            for (int i = start; i < p.quads.size(); i++) {
                float[] q = p.quads.get(i);
                for (int a = 0; a < 3; a++) {
                    float v = q[3 + a];
                    q[3 + a] = q[9 + a];
                    q[9 + a] = v;
                }
            }
        }

        static double[] rp(double radius, double depth, double angle, int axis) {
            double u = Math.cos(angle) * radius, v = Math.sin(angle) * radius;
            return axis == 0 ? new double[] { depth, u, v }
                : axis == 1 ? new double[] { u, depth, v } : new double[] { u, v, depth };
        }

        static void sphere(Part p, double r, int segments, int bands) {
            for (int band = 0; band < bands; band++) for (int side = 0; side < segments; side++) {
                double a = band * Math.PI / bands - Math.PI / 2, b = (band + 1) * Math.PI / bands - Math.PI / 2;
                double c = side * Math.PI * 2 / segments, d = (side + 1) * Math.PI * 2 / segments;
                quad(p, sp(r, a, c), sp(r, a, d), sp(r, b, d), sp(r, b, c));
            }
        }

        static double[] sp(double r, double a, double b) {
            return new double[] { r * Math.cos(a) * Math.cos(b), r * Math.sin(a), r * Math.cos(a) * Math.sin(b) };
        }

        static void quad(Part p, double[] a, double[] b, double[] c, double[] d) {
            quad(p, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2]);
        }

        static void quad(Part p, double... coordinates) {
            float[] q = new float[12];
            for (int i = 0; i < 12; i++) q[i] = (float) coordinates[i];
            p.quads.add(q);
        }

    }

}
