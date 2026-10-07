package com.miaokatze.gtsr.common.weapons;

/** Per-weapon mode IDs stored in gun NBT and frozen into each projectile. */
public final class WeaponMode {

    public static final int STANDARD = 0, ALTERNATE = 1, DRUM = 2;

    private WeaponMode() {}

    public static int count(WeaponKind kind) {
        return kind == WeaponKind.QLZ04 ? 3 : 2;
    }

    public static int mode(WeaponKind kind, int value) {
        return value >= 0 && value < count(kind) ? value : 0;
    }

    public static int capacity(WeaponKind kind, int mode) {
        return kind == WeaponKind.QLZ04 ? (mode == DRUM ? 8 : 12) : kind == WeaponKind.SINGULARITY ? 1 : kind.capacity;
    }

    public static int interval(WeaponKind kind, int mode) {
        return kind == WeaponKind.QLZ04 && mode == DRUM ? 4 : kind.interval;
    }

    public static int lm12Interval(int spin, int mode) {
        return mode == ALTERNATE
            ? Math.max(2, Math.min(16, Math.round(2f / (.125f + .875f * Math.min(1, (spin - 20) / 60f)))))
            : Math.max(1, Math.min(8, Math.round(1f / (.125f + .875f * Math.min(1, (spin - 40) / 120f)))));
    }
}
