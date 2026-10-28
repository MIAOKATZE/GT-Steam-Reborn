package com.miaokatze.gtsr.common.critical;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pure boiler catalogue: native steam heat and exact working-fluid inventory saturation. */
public final class CriticalSolarPolicy {

    public static final int VERSION = 9;
    private static final BigInteger STOCK_HALF_SATURATION = BigInteger.valueOf(100_000_000_000L);
    private static final long BASE_DENSE_SUPERCRITICAL_LITRES = 12_000_000_000L;
    private static final long BASE_HOT_SALT_LITRES = 40_000_000_000L;
    /** Legacy heat-budget API and dust-only basis; normal production uses the direct volume helpers. */
    public static final long BASE_WATER_HEAT_EUT = 70_000_000_000L;
    /** Legacy policy5 API value; unused by policy6 production or inventory arithmetic. */
    public static final long MAX_WATER_HEAT_EUT = 440_000_000_000L;
    public static final long REFERENCE_WATER_HEAT_EUT = 94_500_000_000L;
    public static final int WATER_HEAT_QUANTUM_EU = 7500;
    private static final BigInteger HALF_HEAT_QUANTUM = BigInteger.valueOf(2L * WATER_HEAT_QUANTUM_EU);
    // Legacy display/API constants; these do not limit policy6 native heat.
    public static final int BASE_AMPS_MAX = 640;
    public static final int MAX_WATER_AMPS_MAX = 4096;
    public static final int MAX_SALT_AMPS_MAX = 8192;
    public static final long MAX_VOLTAGE = 2_147_483_640L;
    public static final double PRIVATE_DIMENSION_FACTOR = 0.6D;
    public static final int SALT_POWER_MULTIPLIER = 2;
    public static final int HOT_SALT_SUPERHEATED_LITRES = 1000;
    public static final int FULL_TURBINE_MULTIPLIER = 20;
    public static final int REFERENCE_AMPS_MAX = 864;
    public static final List<Entry> COOLANTS = Collections.unmodifiableList(
        Arrays.asList(
            new Entry("supercoolant", "超级冷却液", 1D, 199_468_339_200L, 1000D, 40000D),
            new Entry("cryotheum", "极寒之凛冰", 1.5D, 365_691_955_200L, 1000D, 20000D),
            new Entry("tachyon", "富集快子流体", 2.5D, 664_894_464_000L, 20D, 200D)));
    public static final List<Entry> EXCITERS = Collections.unmodifiableList(
        Arrays.asList(
            // Helium gas substitutes for the native mandatory2400L/s air maintenance benchmark.
            new Entry("helium_gas", "氦气", 1D, 664_894_464_000L, 2400D, 30000D),
            new Entry("helium", "氦等离子体", 1.5D, 166_223_616_000L, 20D, 800D),
            new Entry("iron", "铁等离子体", 2D, 166_223_616_000L, 20D, 800D),
            new Entry("americium", "镅等离子体", 3D, 166_223_616_000L, 20D, 800D),
            new Entry("atomic", "熔融原子分离催化剂", 4D, 166_223_616_000L, 20D, 800D),
            new Entry("spatial", "扩大化空间流体", 8D, 664_894_464_000L, 20D, 200D)));

    private CriticalSolarPolicy() {}

    public static BigInteger stockHalfSaturationLitres() {
        return STOCK_HALF_SATURATION;
    }

    public static BigInteger baseDenseSupercriticalLitres() {
        return BigInteger.valueOf(BASE_DENSE_SUPERCRITICAL_LITRES);
    }

    public static BigInteger baseHotSaltLitres() {
        return BigInteger.valueOf(BASE_HOT_SALT_LITRES);
    }

    /** Policy7: V = 12G * stock / (stock + 100G) * dimension * coolant * exciter, in groups of3L. */
    public static BigInteger batchDenseSupercriticalLitres(BigInteger stock, double factor, Entry coolant,
        Entry exciter) {
        return stockYield(stock, factor, coolant, exciter, BASE_DENSE_SUPERCRITICAL_LITRES, 3);
    }

    /** Policy7: Q = 40G * stock / (stock + 100G) * dimension * coolant * exciter. */
    public static BigInteger batchHotSaltLitres(BigInteger stock, double factor, Entry coolant, Entry exciter) {
        return stockYield(stock, factor, coolant, exciter, BASE_HOT_SALT_LITRES, 1);
    }

    /** The 100G water reference carries the same heat as H litres of this cold working fluid. */
    public static BigInteger plasmaHalfSaturation(BigInteger hotLitres, BigInteger denseSteamLitres) {
        return ceilDivide(
            BigInteger.valueOf(40_000_000_000L)
                .multiply(hotLitres),
            denseSteamLitres);
    }

    /** P = (30G/c) * M * S/(S+H), rounded once to whole plasma litres; no separate output cap. */
    public static BigInteger batchPlasmaLitres(BigInteger stock, double factor, Entry coolant, Entry exciter,
        BigInteger hotLitres, BigInteger denseSteamLitres) {
        if (stock.signum() <= 0 || !Double.isFinite(factor) || factor <= 0D) return BigInteger.ZERO;
        return BigDecimal.valueOf(30_000_000_000L)
            .multiply(new BigDecimal(hotLitres))
            .multiply(new BigDecimal(stock))
            .multiply(BigDecimal.valueOf(factor))
            .multiply(BigDecimal.valueOf(coolant.multiplier))
            .multiply(BigDecimal.valueOf(exciter.multiplier))
            .divide(
                new BigDecimal(denseSteamLitres.multiply(stock.add(plasmaHalfSaturation(hotLitres, denseSteamLitres)))),
                0,
                RoundingMode.DOWN)
            .toBigIntegerExact();
    }

    /** Policy7 asymptotic batch budget, only for the displayed load; no global output cap. */
    public static BigInteger ratedBatchEquivalentEU(double factor, Entry coolant, Entry exciter, boolean saltMode) {
        if (!Double.isFinite(factor) || factor <= 0D) return BigInteger.ZERO;
        return BigDecimal.valueOf(saltMode ? 1_200_000_000_000_000L : 600_000_000_000_000L)
            .multiply(BigDecimal.valueOf(factor))
            .multiply(BigDecimal.valueOf(coolant.multiplier))
            .multiply(BigDecimal.valueOf(exciter.multiplier))
            .toBigInteger();
    }

    private static BigInteger stockYield(BigInteger stock, double factor, Entry coolant, Entry exciter, long baseLitres,
        int quantum) {
        if (stock.signum() <= 0 || !Double.isFinite(factor) || factor <= 0D) return BigInteger.ZERO;
        // Keep the complete decimal dimension factor until the final, common volume quantisation.
        return BigDecimal.valueOf(baseLitres)
            .multiply(new BigDecimal(stock))
            .multiply(BigDecimal.valueOf(factor))
            .multiply(BigDecimal.valueOf(coolant.multiplier))
            .multiply(BigDecimal.valueOf(exciter.multiplier))
            .divide(
                new BigDecimal(
                    stock.add(STOCK_HALF_SATURATION)
                        .multiply(BigInteger.valueOf(quantum))),
                0,
                RoundingMode.DOWN)
            .toBigIntegerExact()
            .multiply(BigInteger.valueOf(quantum));
    }

    public static final class Entry {

        public final String id, label;
        public final double multiplier;
        public final long referenceEUPerTick;
        public final double nativeLitresPerSecond;
        /** Integer litres per second at the shared native-heat reference. */
        public final double litresPerSecond;

        private Entry(String id, String label, double multiplier, long referenceEUPerTick, double nativeLitresPerSecond,
            double litresPerSecond) {
            this.id = id;
            this.label = label;
            this.multiplier = multiplier;
            this.referenceEUPerTick = referenceEUPerTick;
            this.nativeLitresPerSecond = nativeLitresPerSecond;
            this.litresPerSecond = litresPerSecond;
        }
    }

    /** Native heat power: apply dimension and medium multipliers once, with no production cap. */
    public static BigInteger ratedWaterHeatEUPerTick(double factor, Entry coolant, Entry exciter) {
        if (!Double.isFinite(factor) || factor <= 0D) return BigInteger.ZERO;
        return BigDecimal.valueOf(BASE_WATER_HEAT_EUT)
            .multiply(BigDecimal.valueOf(factor))
            .multiply(BigDecimal.valueOf(coolant.multiplier))
            .multiply(BigDecimal.valueOf(exciter.multiplier))
            .toBigInteger();
    }

    /** Compatibility accounting unit; this design value never measures stored steam heat. */
    public static BigInteger ratedWaterEUPerTick(double factor, Entry coolant, Entry exciter) {
        return ratedWaterHeatEUPerTick(factor, coolant, exciter).multiply(BigInteger.valueOf(FULL_TURBINE_MULTIPLIER));
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

    /** Unrounded native half-EU ceiling. Inventory saturation must run before volume quantisation. */
    public static BigInteger rawWaterHalfHeat(BigInteger equivalentEU) {
        return equivalentEU.divide(BigInteger.TEN);
    }

    /** Common multiple ensures all six steam selections carry exactly the same half-EU. */
    public static BigInteger waterHalfHeat(BigInteger equivalentEU) {
        return rawWaterHalfHeat(equivalentEU).divide(HALF_HEAT_QUANTUM)
            .multiply(HALF_HEAT_QUANTUM);
    }

    /** One ordinary-water litre carries 160 half-EU; K = 5 times native heat EU/t for 400 ticks. */
    public static BigInteger waterHalfSaturationStock(BigInteger maximumHalfHeat) {
        return ceilDivide(maximumHalfHeat, BigInteger.valueOf(160));
    }

    /** Same inventory curve for every steam: E = 400 P S / (S + 5 P), rounded to 7500 native EU. */
    public static BigInteger saturatedWaterHalfHeat(BigInteger stock, BigInteger maximumHalfHeat) {
        if (stock.signum() <= 0 || maximumHalfHeat.signum() <= 0) return BigInteger.ZERO;
        BigInteger halfSaturation = waterHalfSaturationStock(maximumHalfHeat);
        return maximumHalfHeat.multiply(stock)
            .divide(
                stock.add(halfSaturation)
                    .multiply(HALF_HEAT_QUANTUM))
            .multiply(HALF_HEAT_QUANTUM);
    }

    /** Cold salt is converted one-for-one; its maximum batch volume is also its half-saturation stock. */
    public static BigInteger saturatedSaltLitres(BigInteger stock, BigInteger maximumLitres) {
        if (stock.signum() <= 0 || maximumLitres.signum() <= 0) return BigInteger.ZERO;
        return maximumLitres.multiply(stock)
            .divide(stock.add(maximumLitres));
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        return numerator.add(denominator.subtract(BigInteger.ONE))
            .divide(denominator);
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
        return equivalentEU.divide(BigInteger.valueOf(30_000));
    }

    /** Compatibility signature; the old per-entry rated budget no longer discounts higher power. */
    public static BigInteger paidLitres(Entry entry, BigInteger actualEU, BigInteger ratedEU) {
        return costLitres(entry, actualEU);
    }

    /** Scale to actual LHE-equivalent power, ceil to whole litres/second, then prepay all20seconds. */
    public static BigInteger costLitres(Entry entry, BigInteger actualEU) {
        if (entry.litresPerSecond == 0D || actualEU.signum() <= 0) return BigInteger.ZERO;
        BigInteger benchmarkEU = BigInteger.valueOf(REFERENCE_WATER_HEAT_EUT)
            .multiply(BigInteger.valueOf((long) CriticalEnergyMath.CYCLE_TICKS * FULL_TURBINE_MULTIPLIER));
        return ceilDivide(actualEU.multiply(BigInteger.valueOf((long) entry.litresPerSecond)), benchmarkEU)
            .multiply(BigInteger.valueOf(CriticalEnergyMath.CYCLE_TICKS / 20));
    }
}
