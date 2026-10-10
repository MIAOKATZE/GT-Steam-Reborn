package com.miaokatze.gtsr.common.critical;

import net.minecraft.nbt.NBTTagCompound;

/** Immutable job snapshot. Plugin arithmetic acts on the original recipe values. */
public final class CriticalConfiguration {

    public final CriticalTier tier;
    public final CriticalMachineKind kind;
    public final int parallel, speed, economy;

    public CriticalConfiguration(CriticalTier tier, CriticalMachineKind kind, int parallel, int speed, int economy) {
        if (parallel < 0 || speed < 0
            || economy < 0
            || parallel > 8
            || speed > 8
            || economy > 8
            || parallel + speed + economy > 8
            || tier == null
            || kind == null) {
            throw new IllegalArgumentException("At most eight plugins");
        }
        this.tier = tier;
        this.kind = kind;
        this.parallel = parallel;
        this.speed = speed;
        this.economy = economy;
    }

    public int parallelMultiplier() {
        return 1 << parallel;
    }

    public int duration(int original) {
        return (int) Math.max(1L, ((long) original + (1 << speed) - 1) >> speed);
    }

    public long energy(long original) {
        return Math.max(1, original / 10 * (10 - economy) + (original % 10 * (10 - economy) + 9) / 10);
    }

    public NBTTagCompound write() {
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("tier", tier.ordinal());
        n.setInteger("kind", kind.ordinal());
        n.setInteger("parallel", parallel);
        n.setInteger("speed", speed);
        n.setInteger("economy", economy);
        return n;
    }

    public static CriticalConfiguration read(NBTTagCompound n) {
        int a = Math.max(0, Math.min(8, n.getInteger("parallel")));
        int b = Math.max(0, Math.min(8 - a, n.getInteger("speed")));
        int c = Math.max(0, Math.min(8 - a - b, n.getInteger("economy")));
        return new CriticalConfiguration(
            CriticalTier.byId(n.getInteger("tier")),
            CriticalMachineKind.byId(n.getInteger("kind")),
            a,
            b,
            c);
    }
}
