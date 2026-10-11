package com.miaokatze.gtsr.common.critical;

import net.minecraft.nbt.NBTTagCompound;

/** Persistent time checkpoint. Costs use cumulative rounding, so save/reload cannot charge twice. */
public final class CriticalConstructionProgress {

    public static final int STAGE_DURATION = 400;
    private int elapsed;

    public int elapsed() {
        return elapsed;
    }

    public int next(int speed) {
        int step = speed == 3 ? STAGE_DURATION : speed == 2 ? 10 : speed == 1 ? 2 : 1;
        return Math.min(STAGE_DURATION, elapsed + step);
    }

    public static int cumulative(int count, int ticks) {
        return (int) (((long) count * ticks + STAGE_DURATION - 1) / STAGE_DURATION);
    }

    public void advance(int ticks) {
        elapsed = Math.max(0, Math.min(STAGE_DURATION, ticks));
    }

    public void reset() {
        elapsed = 0;
    }

    public void write(NBTTagCompound n) {
        n.setInteger("constructionElapsed", elapsed);
    }

    public void read(NBTTagCompound n) {
        advance(n.getInteger("constructionElapsed"));
    }
}
