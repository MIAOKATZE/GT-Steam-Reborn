package com.miaokatze.gtsr.client.encounter;

/** Local-space voxel flow authored around actual model core pivots, independent of frame rate. */
public final class BossBodyRunes {

    private BossBodyRunes() {}

    /** kind: 0 royal thorax, 1 furnace core, 2 hive will_core. Output xyz,size,rgb,alpha. */
    public static double[] sample(int kind, int mask, double tick, int index, boolean awakening) {
        double speed = mask == 0 ? .055 : .11 + (Integer.bitCount(mask) * .025);
        double a = tick * speed + index * 2.399963229728653;
        double pulse = .5 + .5 * Math.sin(tick * .19 + index);
        double radius = kind == 0 ? 1.28 : kind == 1 ? 1.03 : 1.45;
        if (awakening) radius *= .3 + .7 * pulse;
        if ((mask & 1) != 0) radius *= .5 + .5 * pulse;
        if ((mask & 8) != 0) radius *= 1 + .65 * pulse;
        double x = Math.cos(a) * radius, y, z;
        double r, g, b;
        if (kind == 0) {
            // Thorax model bounds y=4.58..8.48, front face z=-.64: chest rather than throne ornament.
            y = 6.53 + Math.sin(a) * radius;
            z = -.75 + Math.sin(a * 2) * .12;
            if ((mask & 16) != 0) {
                y = 6.53 + Math.sin(a) * .75;
                z = -.75 + Math.cos(a * 2) * .55;
            }
            if ((mask & 1) != 0) y -= pulse * .65;
            boolean gravity = (mask & 5) != 0;
            r = gravity ? .73 : 1;
            g = gravity ? .38 : .74;
            b = gravity ? 1 : .22;
            if ((mask & 2) != 0) {
                r = .8;
                g = .46;
                b = 1;
            }
        } else if (kind == 1) {
            y = 9.1 + Math.sin(a) * radius;
            z = 1.02;
            if ((mask & 2) != 0) z += pulse * .5;
            if ((mask & 4) != 0) y -= pulse * .8;
            r = 1;
            g = .36 + .25 * pulse;
            b = .08;
        } else {
            // The separate will_core sits below the hive body at y=1.415,z=-.506.
            y = 1.415 + Math.sin(a * 2) * .42;
            z = -.506 + Math.sin(a) * radius;
            if ((mask & 1) != 0) y += pulse * 2.5;
            if ((mask & 6) != 0) {
                y += pulse;
                z -= pulse * .8;
            }
            r = .72 + .17 * pulse;
            g = .18 + .14 * pulse;
            b = 1;
        }
        double scale = (awakening ? .12 : .075) + (mask != 0 ? .045 : 0) + pulse * .025;
        return new double[] { x, y, z, scale, r, g, b, awakening ? .82 : mask == 0 ? .5 : .76 };
    }
}
