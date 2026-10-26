package com.miaokatze.gtsr.common.critical;

import net.minecraft.nbt.NBTTagCompound;

/** Shared, dimension-local foundation work. Cells fill in row-major X/Z order. */
public final class CriticalFoundationChunk {

    public static final int VERSION = 7;
    public static final int CELLS = 16, CELL_DURATION = 40, LEGACY_CELL_DURATION = 400, CURING_DURATION = 86_400 * 20;
    public static final int LEGACY_CURING_DURATION = 72_000 * 20, HEATING_DURATION = 1_800 * 20;
    public static final int HEATING_STEAM_PER_TICK = 50_000;
    private int completedCells, cellProgress, curingRemaining = CURING_DURATION;
    private int heatingRemaining = HEATING_DURATION, heatingPaidWork;
    private boolean funded, fundingUncertain, modern = true;
    private int scheduleVersion = VERSION;
    private long lastWorkedTick = Long.MIN_VALUE;

    public int completedCells() {
        return scheduleVersion == 6 && !poured() ? cellProgress * CELLS / LEGACY_CELL_DURATION : completedCells;
    }

    public int scheduleVersion() {
        return scheduleVersion;
    }

    public int cellProgress() {
        return cellProgress;
    }

    /** Already-paid whole chunks and historical cells retain their original work clock. */
    public int cellDuration() {
        return scheduleVersion >= VERSION ? CELL_DURATION : LEGACY_CELL_DURATION;
    }

    public int curingRemaining() {
        return curingRemaining;
    }

    public int heatingRemaining() {
        return heatingRemaining;
    }

    public boolean modern() {
        return modern;
    }

    /** Only a newly created journal of an old job uses the historical price and clock. */
    public void useLegacySchedule() {
        if (completedCells != 0 || cellProgress != 0 || funded) return;
        modern = false;
        scheduleVersion = 0;
        heatingRemaining = 0;
        curingRemaining = LEGACY_CURING_DURATION;
    }

    public boolean heatingFunded() {
        return heatingPaidWork > 0;
    }

    public int heatingPaidWork() {
        return heatingPaidWork;
    }

    public void fundHeating() {
        fundHeating(1);
    }

    public void fundHeating(int work) {
        if (heatingPaidWork == 0) heatingPaidWork = Math.min(heatingRemaining, Math.max(0, work));
    }

    public void heat(long tick) {
        heat(1, tick);
    }

    public void heat(int work, long tick) {
        if (!poured() || heatingRemaining == 0 || !heatingFunded() || workedAt(tick) || fundingUncertain) return;
        int advance = Math.min(heatingRemaining, Math.min(heatingPaidWork, Math.max(0, work)));
        if (advance == 0) return;
        lastWorkedTick = tick;
        heatingRemaining -= advance;
        heatingPaidWork -= advance;
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
        return poured() && heatingRemaining == 0 && curingRemaining == 0;
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
        for (long voltage = baselineVoltage; work < LEGACY_CELL_DURATION
            && voltage <= suppliedVoltage / 4; voltage *= 4) work *= 4;
        return work;
    }

    public void pour(int work, long tick) {
        if (!funded || poured() || workedAt(tick) || fundingUncertain) return;
        lastWorkedTick = tick;
        cellProgress = Math.min(cellDuration(), cellProgress + Math.max(0, work));
        if (cellProgress == cellDuration()) {
            completedCells = scheduleVersion == 6 ? CELLS : completedCells + 1;
            cellProgress = 0;
            funded = false;
        }
    }

    public void cure(long tick) {
        cure(1, tick);
    }

    public void cure(int work, long tick) {
        if (!poured() || heatingRemaining > 0 || complete() || workedAt(tick) || fundingUncertain || work <= 0) return;
        lastWorkedTick = tick;
        curingRemaining = Math.max(0, curingRemaining - work);
    }

    public void capCuringRemaining(int ticks) {
        if (poured() && heatingRemaining == 0 && !fundingUncertain)
            curingRemaining = Math.min(curingRemaining, Math.max(0, ticks));
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
        if (poured() && heatingRemaining == 0 && !fundingUncertain)
            curingRemaining -= curingReduction(curingRemaining, count);
    }

    public void read(NBTTagCompound n) {
        scheduleVersion = n.hasKey("foundationVersion") ? n.getInteger("foundationVersion") : 0;
        modern = scheduleVersion >= 5 && scheduleVersion <= VERSION;
        completedCells = Math.max(0, Math.min(CELLS, n.getInteger("cells")));
        cellProgress = poured() ? 0 : Math.max(0, Math.min(cellDuration() - 1, n.getInteger("progress")));
        funded = !poured() && n.getBoolean("funded");
        fundingUncertain = n.getBoolean("uncertain");
        curingRemaining = Math.max(0, Math.min(CURING_DURATION, n.getInteger("curing")));
        heatingRemaining = modern ? Math.max(0, Math.min(HEATING_DURATION, n.getInteger("heating"))) : 0;
        heatingPaidWork = modern && heatingRemaining > 0 && n.getBoolean("heatingFunded")
            ? n.hasKey("heatingPaidWork") ? n.getInteger("heatingPaidWork") : 1
            : 0;
        if (heatingPaidWork < 0 || heatingPaidWork > heatingRemaining) {
            fundingUncertain = true;
            heatingPaidWork = 0;
        }
        if (n.hasKey("foundationVersion") && !modern) fundingUncertain = true;
        if (scheduleVersion == 6 && completedCells != 0 && completedCells != CELLS) fundingUncertain = true;
        if (modern
            && (!n.hasKey("heating") || n.getInteger("heating") < 0 || n.getInteger("heating") > HEATING_DURATION))
            fundingUncertain = true;
        lastWorkedTick = n.hasKey("lastWorkedTick") ? n.getLong("lastWorkedTick") : Long.MIN_VALUE;
        // One historical paid cell already covers the new complete chunk bill and its 400 work.
        // Uncertain payments retain their original units and lock; no payer or refund is guessed.
        if (scheduleVersion == 5 && !fundingUncertain) {
            scheduleVersion = 6;
            if (completedCells > 0) {
                completedCells = CELLS;
                cellProgress = 0;
                funded = false;
            }
        }
        // An untouched chunk has no old payment to preserve. Paid v6 work remains a whole-chunk
        // receipt with its exact 400-work clock, including after another save/reload.
        if (scheduleVersion == 6 && !fundingUncertain && !funded && completedCells == 0 && cellProgress == 0)
            scheduleVersion = VERSION;
    }

    public void write(NBTTagCompound n) {
        if (scheduleVersion != 0) n.setInteger("foundationVersion", scheduleVersion);
        else n.removeTag("foundationVersion");
        n.setInteger("cells", completedCells);
        n.setInteger("progress", cellProgress);
        n.setBoolean("funded", funded);
        n.setBoolean("uncertain", fundingUncertain);
        n.setInteger("curing", curingRemaining);
        n.setInteger("heating", heatingRemaining);
        n.setBoolean("heatingFunded", heatingFunded());
        n.setInteger("heatingPaidWork", heatingPaidWork);
        n.setLong("lastWorkedTick", lastWorkedTick);
    }
}
