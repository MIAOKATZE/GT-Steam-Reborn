package com.miaokatze.gtsr.common.critical;

import net.minecraft.nbt.NBTTagCompound;

/** Shared, dimension-local foundation work. Cells fill in row-major X/Z order. */
public final class CriticalFoundationChunk {

    public static final int CELLS = 16, CELL_DURATION = 400, CURING_DURATION = 72_000 * 20;
    private int completedCells, cellProgress, curingRemaining = CURING_DURATION;
    private boolean funded, fundingUncertain;
    private long lastWorkedTick = Long.MIN_VALUE;

    public int completedCells() {
        return completedCells;
    }

    public int cellProgress() {
        return cellProgress;
    }

    public int curingRemaining() {
        return curingRemaining;
    }

    public boolean funded() {
        return funded;
    }

    public boolean fundingUncertain() {
        return fundingUncertain;
    }

    public boolean poured() {
        return completedCells == CELLS;
    }

    public boolean complete() {
        return poured() && curingRemaining == 0;
    }

    public boolean workedAt(long tick) {
        return lastWorkedTick == tick;
    }

    public void setFunded() {
        funded = true;
    }

    public void lockFunding() {
        fundingUncertain = true;
    }

    /** Perfect overclock: each fourfold voltage step buys four units of baseline work. */
    public static int overclockWork(long baselineVoltage, long suppliedVoltage) {
        if (baselineVoltage <= 0 || suppliedVoltage < baselineVoltage) return 0;
        int work = 1;
        for (long voltage = baselineVoltage; work < CELL_DURATION && voltage <= suppliedVoltage / 4; voltage *= 4)
            work *= 4;
        return work;
    }

    public void pour(int work, long tick) {
        if (!funded || poured() || workedAt(tick) || fundingUncertain) return;
        lastWorkedTick = tick;
        cellProgress = Math.min(CELL_DURATION, cellProgress + Math.max(0, work));
        if (cellProgress == CELL_DURATION) {
            completedCells++;
            cellProgress = 0;
            funded = false;
        }
    }

    public void cure(long tick) {
        if (!poured() || complete() || workedAt(tick) || fundingUncertain) return;
        lastWorkedTick = tick;
        curingRemaining--;
    }

    public void capCuringRemaining(int ticks) {
        if (poured() && !fundingUncertain) curingRemaining = Math.min(curingRemaining, Math.max(0, ticks));
    }

    /** floor(remaining * 20% * n/(16+n)); positive sub-tick fractions stay on the clock. */
    public static int curingReduction(int remaining, long singularities) {
        if (remaining <= 0 || singularities <= 0) return 0;
        java.math.BigInteger count = java.math.BigInteger.valueOf(singularities);
        return java.math.BigInteger.valueOf(remaining)
            .multiply(count)
            .divide(
                count.add(java.math.BigInteger.valueOf(16))
                    .multiply(java.math.BigInteger.valueOf(5)))
            .intValue();
    }

    public void accelerate(long count) {
        if (poured() && !fundingUncertain) curingRemaining -= curingReduction(curingRemaining, count);
    }

    public void read(NBTTagCompound n) {
        completedCells = Math.max(0, Math.min(CELLS, n.getInteger("cells")));
        cellProgress = poured() ? 0 : Math.max(0, Math.min(CELL_DURATION - 1, n.getInteger("progress")));
        funded = !poured() && n.getBoolean("funded");
        fundingUncertain = n.getBoolean("uncertain");
        curingRemaining = Math.max(0, Math.min(CURING_DURATION, n.getInteger("curing")));
        lastWorkedTick = n.hasKey("lastWorkedTick") ? n.getLong("lastWorkedTick") : Long.MIN_VALUE;
    }

    public void write(NBTTagCompound n) {
        n.setInteger("cells", completedCells);
        n.setInteger("progress", cellProgress);
        n.setBoolean("funded", funded);
        n.setBoolean("uncertain", fundingUncertain);
        n.setInteger("curing", curingRemaining);
        n.setLong("lastWorkedTick", lastWorkedTick);
    }
}
