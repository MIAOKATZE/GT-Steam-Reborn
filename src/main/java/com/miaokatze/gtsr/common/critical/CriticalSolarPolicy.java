package com.miaokatze.gtsr.common.critical;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pure, shared boiler catalogue. Power is equivalent EU after a fully warmed (20x) pulse turbine. */
public final class CriticalSolarPolicy {

    public static final int VERSION = 3;
    public static final int BASE_AMPS_MAX = 640;
    public static final int MAX_WATER_AMPS_MAX = 4096;
    public static final int MAX_SALT_AMPS_MAX = 8192;
    public static final long MAX_VOLTAGE = 2_147_483_640L;
    public static final double PRIVATE_DIMENSION_FACTOR = 0.6D;
    public static final int SALT_POWER_MULTIPLIER = 2;
    public static final int HOT_SALT_SUPERHEATED_LITRES = 1000;
    public static final int FULL_TURBINE_MULTIPLIER = 20;
    public static final List<Entry> COOLANTS = Collections.unmodifiableList(
        Arrays.asList(
            new Entry("supercoolant", "超级冷却液", 1D, 1600D),
            new Entry("cryotheum", "极寒之凛冰", 1.5D, 1000D),
            new Entry("tachyon", "富集快子流体", 2.5D, 20D)));
    public static final List<Entry> EXCITERS = Collections.unmodifiableList(
        Arrays.asList(
            new Entry("helium_gas", "氦气", 1D, 1000D),
            new Entry("helium", "氦等离子体", 1.5D, 9D),
            new Entry("iron", "铁等离子体", 2D, 6D),
            new Entry("americium", "镅等离子体", 3D, 3D),
            new Entry("atomic", "熔融原子分离催化剂", 4D, 2D),
            new Entry("spatial", "扩大化空间流体", 8D, 1D)));

    private CriticalSolarPolicy() {}

    public static final class Entry {

        public final String id, label;
        public final double multiplier, litresPerSecond;

        private Entry(String id, String label, double multiplier, double litresPerSecond) {
            this.id = id;
            this.label = label;
            this.multiplier = multiplier;
            this.litresPerSecond = litresPerSecond;
        }
    }

    /** Apply the dimension coefficient exactly once, then the absolute power cap. */
    public static BigInteger ratedWaterEUPerTick(double factor, Entry coolant, Entry exciter) {
        if (!Double.isFinite(factor) || factor <= 0D) return BigInteger.ZERO;
        double amps = Math.min(MAX_WATER_AMPS_MAX, BASE_AMPS_MAX * factor * coolant.multiplier * exciter.multiplier);
        return BigDecimal.valueOf(amps)
            .multiply(BigDecimal.valueOf(MAX_VOLTAGE))
            .toBigInteger();
    }

    public static BigInteger batchBudgetEU(double factor, Entry coolant, Entry exciter, boolean saltMode) {
        return ratedWaterEUPerTick(factor, coolant, exciter)
            .multiply(BigInteger.valueOf(CriticalEnergyMath.CYCLE_TICKS * (saltMode ? SALT_POWER_MULTIPLIER : 1)));
    }

    /** Minimum dust-only work load; both required media still pay their proportional 400-tick cost. */
    public static BigInteger irradiationBudgetEU(BigInteger waterBudget) {
        BigInteger parallel = BigInteger.valueOf(CriticalEnergyMath.IRRADIATION_PARALLEL);
        return waterBudget.add(parallel.subtract(BigInteger.ONE))
            .divide(parallel);
    }

    /** Common multiple ensures all six steam selections carry exactly the same half-EU. */
    public static BigInteger waterHalfHeat(BigInteger equivalentEU) {
        return equivalentEU.divide(BigInteger.TEN)
            .divide(BigInteger.valueOf(15000))
            .multiply(BigInteger.valueOf(15000));
    }

    public static BigInteger steamLitres(BigInteger halfHeat, int mode) {
        return halfHeat.divide(CriticalEnergyMath.steamHeatPerL(Math.floorMod(mode, 6)));
    }

    public static BigInteger waterLitres(BigInteger steamLitres, int mode) {
        BigInteger numerator = steamLitres.multiply(BigInteger.valueOf(Math.floorMod(mode, 6) < 3 ? 1 : 1000));
        return numerator.add(BigInteger.valueOf(159))
            .divide(BigInteger.valueOf(160));
    }

    /** GT205 LHE / thermal boiler: one hot salt -> 1000 SH steam -> 1500 base EU -> 30000 at 20x. */
    public static BigInteger saltLitres(BigInteger equivalentEU) {
        // One shared quantum: water produces multiples of150000EU, salt exactly twice that (10L).
        return equivalentEU.divide(BigInteger.valueOf(300_000))
            .multiply(BigInteger.TEN);
    }

    /** Whole 400-tick prepayment, ceil once; a fractional load can never receive a free modifier. */
    public static BigInteger paidLitres(Entry entry, BigInteger actualEU, BigInteger ratedEU) {
        if (entry.litresPerSecond == 0D || actualEU.signum() <= 0 || ratedEU.signum() <= 0) return BigInteger.ZERO;
        return BigDecimal.valueOf(entry.litresPerSecond * 20D)
            .multiply(new BigDecimal(actualEU))
            .divide(new BigDecimal(ratedEU), 0, RoundingMode.CEILING)
            .toBigIntegerExact();
    }
}
