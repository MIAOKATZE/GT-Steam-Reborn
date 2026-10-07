package com.miaokatze.gtsr.common.weapons;

public enum WeaponKind {

    LM12(0, "lm12", 2, 5, 1, 500, 15, 0, 50),
    T20(1, "t20", 10, 8, 8, 160, 12, .005, 50),
    QLZ04(2, "qlz04", 12, 10, 12, 60, 3, .035, 40),
    SINGULARITY(3, "singularity", 0, 0, 20, 1, .45, .00315, 100);

    public final int id, interval, capacity, reloadTicks;
    public final String modelKey;
    public final float damage, armorPenetration;
    public final double projectileSpeed, gravity;

    WeaponKind(int id, String key, float damage, float penetration, int interval, int capacity, double speed,
        double gravity, int reload) {
        this.id = id;
        this.modelKey = key;
        this.damage = damage;
        armorPenetration = penetration;
        this.interval = interval;
        this.capacity = capacity;
        projectileSpeed = speed;
        this.gravity = gravity;
        reloadTicks = reload;
    }

    public static WeaponKind fromId(int id) {
        return id >= 0 && id < values().length ? values()[id] : null;
    }
}
