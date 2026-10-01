package com.miaokatze.gtsr.common.dimension.prosperity.architecture;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry, in block units; also consumed by the offline mesh review. */
public final class RuinsGeometry {

    private RuinsGeometry() {}

    private static void box(List<double[]> a, double x, double y, double z, double X, double Y, double Z) {
        a.add(new double[] { x, y, z, X, Y, Z });
    }

    /** Axis: sides 0/1 vertical, 2/3 north/south, 4/5 west/east. */
    private static void axisBox(List<double[]> a, int side, double lo, double hi, double start, double end) {
        if (side < 2) box(a, lo, start, lo, hi, end, hi);
        else if (side < 4) box(a, lo, lo, start, hi, hi, end);
        else box(a, start, lo, lo, end, hi, hi);
    }

    public static List<double[]> parts(int shape, int meta, int connections, boolean supported, boolean collision) {
        List<double[]> a = new ArrayList<>();
        int side = meta >= 0 && meta < 6 ? meta : 1;
        if (shape == 0 || shape == 1) {
            box(a, .3125, .3125, .3125, .6875, .6875, .6875);
            int mask = connections;
            if (mask == 0) mask = side < 2 ? 3 : side < 4 ? 12 : 48;
            for (int s = 0; s < 6; s++) if ((mask & (1 << s)) != 0) {
                axisBox(a, s, .34375, .65625, (s & 1) == 0 ? 0 : .5, (s & 1) == 0 ? .5 : 1);
                axisBox(a, s, .28125, .71875, (s & 1) == 0 ? .125 : .8125, (s & 1) == 0 ? .1875 : .875);
            }
            if (shape == 1) {
                // Handwheel is perpendicular to pipe axis and projects above the pipe body.
                int wheel = side < 2 ? 3 : 1;
                double[][] ring = { { .3125, .1875, .6875, .25 }, { .3125, .75, .6875, .8125 },
                    { .1875, .3125, .25, .6875 }, { .75, .3125, .8125, .6875 }, { .25, .25, .375, .3125 },
                    { .625, .25, .75, .3125 }, { .25, .6875, .375, .75 }, { .625, .6875, .75, .75 } };
                for (double[] q : ring) {
                    double[] b = { q[0], .8125, q[1], q[2], .875, q[3] };
                    if (wheel == 3) box(a, b[0], b[2], b[1], b[3], b[5], b[4]);
                    else a.add(b);
                }
                if (wheel == 3) {
                    box(a, .46875, .1875, .8125, .53125, .8125, .875);
                    box(a, .1875, .46875, .8125, .8125, .53125, .875);
                    box(a, .4375, .4375, .65625, .5625, .5625, .875);
                } else {
                    box(a, .46875, .8125, .1875, .53125, .875, .8125);
                    box(a, .1875, .8125, .46875, .8125, .875, .53125);
                    box(a, .4375, .65625, .4375, .5625, .875, .5625);
                }
            }
        } else if (shape == 2) {
            // Canonical north-mounted gauge; transform around center to attachment face.
            box(a, .40625, .40625, .8125, .59375, .59375, 1);
            box(a, .25, .25, .6875, .75, .75, .875);
            box(a, .3125, .3125, .65625, .6875, .6875, .6875);
            for (double[] b : a) rotateAttachment(b, side);
        } else if (shape == 3) {
            double y = (meta & 1) == 1 ? .875 : 0;
            if (collision) box(a, 0, y, 0, 1, y + .125, 1);
            else {
                box(a, 0, y, 0, 1, y + .125, .0625);
                box(a, 0, y, .9375, 1, y + .125, 1);
                box(a, 0, y, 0, .0625, y + .125, 1);
                box(a, .9375, y, 0, 1, y + .125, 1);
                for (int k = 1; k < 8; k++) {
                    double p = k * .125;
                    box(a, p, y, .0625, p + .03125, y + .09375, .9375);
                    box(a, .0625, y, p, .9375, y + .0625, p + .03125);
                }
            }
        } else if (shape == 4) {
            for (int k = 0; k < 4; k++) {
                double y = k * .25;
                if ((k & 1) == 0) {
                    box(a, .40625, y, .46875, .4375, y + .21875, .53125);
                    box(a, .5625, y, .46875, .59375, y + .21875, .53125);
                    box(a, .40625, y, .46875, .59375, y + .03125, .53125);
                    box(a, .40625, y + .1875, .46875, .59375, y + .21875, .53125);
                } else {
                    box(a, .46875, y, .40625, .53125, y + .21875, .4375);
                    box(a, .46875, y, .5625, .53125, y + .21875, .59375);
                    box(a, .46875, y, .40625, .53125, y + .03125, .59375);
                    box(a, .46875, y + .1875, .40625, .53125, y + .21875, .59375);
                }
            }
            if ((connections & 1) != 0) box(a, .375, 0, .375, .625, .0625, .625);
            if ((connections & 2) != 0) box(a, .375, .9375, .375, .625, 1, .625);
        } else if (shape == 5) {
            box(a, .4375, 0, .4375, .5625, collision ? 1.5 : 1.0625, .5625);
            box(a, .40625, 1, .40625, .59375, 1.0625, .59375);
            if (supported) box(a, .3125, 0, .3125, .6875, .0625, .6875);
            for (int s = 2; s < 6; s++) if ((connections & (1 << s)) != 0) {
                double x = s == 4 ? 0 : .46875, X = s == 5 ? 1 : .53125, z = s == 2 ? 0 : .46875,
                    Z = s == 3 ? 1 : .53125;
                if (collision) box(a, x, 0, z, X, 1.5, Z);
                else {
                    box(a, x, .4375, z, X, .5, Z);
                    box(a, x, .9375, z, X, 1, Z);
                    if (s < 4) box(a, .484375, .5, s == 2 ? .1875 : .78125, .515625, .9375, s == 2 ? .21875 : .8125);
                    else box(a, s == 4 ? .1875 : .78125, .5, .484375, s == 4 ? .21875 : .8125, .9375, .515625);
                }
            }
        }
        return a;
    }

    private static void rotateAttachment(double[] b, int side) {
        if (side == 2) return;
        double[] e = { 2, 2, 2, -1, -1, -1 };
        for (int k = 0; k < 8; k++) {
            double x = b[(k & 1) == 0 ? 0 : 3], y = b[(k & 2) == 0 ? 1 : 4], z = b[(k & 4) == 0 ? 2 : 5], X = x, Y = y,
                Z = z;
            if (side == 3) Z = 1 - z;
            else if (side == 4) {
                X = z;
                Z = x;
            } else if (side == 5) {
                X = 1 - z;
                Z = x;
            } else if (side == 0) {
                Y = z;
                Z = y;
            } else if (side == 1) {
                Y = 1 - z;
                Z = y;
            }
            e[0] = Math.min(e[0], X);
            e[1] = Math.min(e[1], Y);
            e[2] = Math.min(e[2], Z);
            e[3] = Math.max(e[3], X);
            e[4] = Math.max(e[4], Y);
            e[5] = Math.max(e[5], Z);
        }
        System.arraycopy(e, 0, b, 0, 6);
    }

    public static double[] envelope(List<double[]> parts) {
        double[] e = { 1, 1, 1, 0, 0, 0 };
        for (double[] b : parts) for (int i = 0; i < 3; i++) {
            e[i] = Math.min(e[i], b[i]);
            e[i + 3] = Math.max(e[i + 3], b[i + 3]);
        }
        return e;
    }
}
