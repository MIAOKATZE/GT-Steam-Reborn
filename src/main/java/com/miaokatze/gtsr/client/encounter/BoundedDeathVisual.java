package com.miaokatze.gtsr.client.encounter;

/** A single volume-sized disintegration, inspired by Outpost's stable three-cube clusters. */
public final class BoundedDeathVisual {

    private BoundedDeathVisual() {}

    public static int count(double width, double height) {
        double volume = Math.max(0, width * width * height);
        return volume < .3 ? 6
            : volume < 1 ? 9 : volume < 3 ? 12 : volume < 10 ? 18 : volume < 64 ? 24 : volume < 512 ? 36 : 48;
    }

    public static int duration(boolean golden) {
        return golden ? 52 : 36;
    }

    private static double unit(long seed, int index, int channel) {
        long value = seed + 0x9e3779b97f4a7c15L * (1 + index * 7L + channel);
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53;
    }

    /** Births occupy only twelve ticks; no corpse emits another endless stream. */
    public static double[] sample(long seed, int index, double age, double width, double height, boolean golden) {
        int cluster = index / 3;
        double birth = unit(seed, cluster, 0) * 12, elapsed = age - birth, life = golden ? 40 : 24;
        if (elapsed < 0 || elapsed >= life) return new double[] { 0, 0, 0, 0, 0 };
        double progress = elapsed / life, angle = unit(seed, cluster, 1) * Math.PI * 2;
        double size = .03 + unit(seed, index, 3) * Math.min(.085, .025 + width * .012);
        double radius = width * (.15 + unit(seed, cluster, 2) * .29) + progress * (.25 + Math.min(1, width * .12));
        return new double[] { Math.cos(angle) * radius + (index % 3 - 1) * size * 1.5,
            unit(seed, cluster, 4) * height + progress * (.3 + Math.min(1, height * .1)), Math.sin(angle) * radius,
            size * (1 - .25 * progress), .34 * Math.min(1, elapsed / 5) * (1 - progress) };
    }
}
