package com.miaokatze.gtsr.common.critical;

import net.minecraft.nbt.NBTTagCompound;

/** Immutable construction snapshot; functional plugins and tier upgrades have been retired. */
public final class CriticalConfiguration {

    public final CriticalTier tier;
    public final CriticalMachineKind kind;
    public final int parallel, speed, economy;

    public CriticalConfiguration(CriticalTier tier, CriticalMachineKind kind, int parallel, int speed, int economy) {
        this(tier, kind, parallel, speed, economy, true);
    }

    private CriticalConfiguration(CriticalTier tier, CriticalMachineKind kind, int parallel, int speed, int economy,
        boolean newConstruction) {
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
        // New construction uses the eight decorative T3 modules. A paid legacy geometry snapshot
        // must remain byte-for-byte stable on load; its old fields no longer alter processing.
        this.tier = newConstruction ? CriticalTier.T3 : tier;
        this.kind = kind;
        this.parallel = newConstruction ? 8 : parallel;
        this.speed = newConstruction ? 0 : speed;
        this.economy = newConstruction ? 0 : economy;
    }

    public int parallelMultiplier() {
        return 1;
    }

    public int duration(int original) {
        return original;
    }

    public long energy(long original) {
        return original;
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
            c,
            false);
    }
}
