// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.tooltip;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToDoubleFunction;

import com.miaokatze.gtsr.common.critical.CriticalEnergyMath;
import com.miaokatze.gtsr.common.critical.CriticalSolarPolicy;
import com.miaokatze.gtsr.common.critical.CriticalSolarPolicy.Entry;

/** Calculations shared by both reading levels. No access to a machine, hatch or world is needed. */
final class GTSRSolarReadings {

    static final BigInteger G = BigInteger.TEN.pow(9), T = BigInteger.TEN.pow(12);
    static final BigInteger HALF = CriticalSolarPolicy.stockHalfSaturationLitres();
    static final BigInteger STOCK = HALF.multiply(BigInteger.valueOf(5));
    static final double PRIVATE = CriticalSolarPolicy.PRIVATE_DIMENSION_FACTOR;
    static final Entry COOLANT = CriticalSolarPolicy.COOLANTS.get(1), EXCITER = CriticalSolarPolicy.EXCITERS.get(1);
    static final double MULTIPLIER = combined(PRIVATE, COOLANT, EXCITER);
    static final BigInteger HALF_HEAT = CriticalSolarPolicy.batchWaterHalfHeat(STOCK, PRIVATE, COOLANT, EXCITER, 5);
    static final BigInteger WATER = CriticalSolarPolicy.batchWaterLitres(HALF_HEAT, 5);
    static final BigInteger STEAM = CriticalSolarPolicy.steamLitres(HALF_HEAT, 5);
    static final BigInteger SALT = hotSalt(STOCK);
    static final BigInteger STEAM_HEAT = HALF_HEAT.divide(BigInteger.valueOf(2));
    static final BigInteger PULSE = HALF_HEAT.multiply(BigInteger.valueOf(CriticalEnergyMath.MAX_EFFICIENCY))
        .divide(BigInteger.valueOf(200));

    private GTSRSolarReadings() {}

    static double combined(double dimension, Entry coolant, Entry exciter) {
        return BigDecimal.valueOf(dimension)
            .multiply(BigDecimal.valueOf(coolant.multiplier))
            .multiply(BigDecimal.valueOf(exciter.multiplier))
            .doubleValue();
    }

    static BigInteger saltHeat(int steamMode) {
        return SALT.multiply(BigInteger.valueOf(CriticalSolarPolicy.HOT_SALT_SUPERHEATED_LITRES))
            .multiply(CriticalEnergyMath.steamHeatPerL(steamMode))
            .divide(BigInteger.valueOf(2));
    }

    static BigInteger hotSalt(BigInteger stock) {
        return CriticalSolarPolicy.batchHotSaltLitres(stock, PRIVATE, COOLANT, EXCITER)
            .min(stock);
    }

    static double in(BigInteger value, BigInteger unit) {
        return new BigDecimal(value).divide(new BigDecimal(unit))
            .doubleValue();
    }

    static double coefficient(int route) {
        BigInteger base = route == 0 ? CriticalSolarPolicy.baseHotSaltLitres()
            : CriticalSolarPolicy.baseDenseSupercriticalLitres();
        return new BigDecimal(base).divide(new BigDecimal(G))
            .multiply(BigDecimal.valueOf(MULTIPLIER))
            .doubleValue();
    }

    /** Salt and water in G L, plasma in multiples of its own H and V. Include the salt stock cap's corner. */
    static double[][] curve(int route) {
        Set<Double> xs = new TreeSet<>();
        double end = route == 1 ? 4 : in(STOCK, G);
        for (int i = 0; i <= 80; i++) xs.add(end * i / 80);
        double half = in(HALF, G);
        if (route == 0) {
            double corner = coefficient(0) - half;
            if (corner > 0 && corner < end) xs.add(corner);
        }
        double[][] points = new double[2][xs.size()];
        int i = 0;
        for (double x : xs) {
            points[0][i] = x;
            points[1][i++] = route == 1 ? x / (1 + x)
                : route == 2 ? coefficient(2) * x / (x + half)
                    : in(
                        hotSalt(
                            BigDecimal.valueOf(x)
                                .multiply(new BigDecimal(G))
                                .toBigInteger()),
                        G);
        }
        return points;
    }

    static final class Dimension {

        final String key, lookup;
        final double factor;

        Dimension(String key, String lookup, double factor) {
            this.key = key;
            this.lookup = lookup;
            // Invalid/nonpositive production factors cannot fund working-fluid production.
            this.factor = Double.isFinite(factor) && factor > 0 ? factor : 0;
        }
    }

    /**
     * Mirrors CriticalEnergyRuntime.dimensionFactor: personal provider, no GC, orbit body, then numeric ID.
     * Orbit station keys in the config have an SS_ prefix; actual orbit providers return the bare body.
     * Keep the audited default order (PS, Overworld, Mars, then config order) without copying any factors.
     */
    static List<Dimension> dimensions(String[] config, boolean galacticraft, ToDoubleFunction<String> factor) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("PS");
        keys.add("0");
        Set<String> configured = new LinkedHashSet<>();
        if (config != null) for (String spec : config) {
            if (spec == null || spec.indexOf(':') <= 0) continue;
            String key = spec.substring(0, spec.indexOf(':'));
            if (key.startsWith("SS_") && key.length() > 3) configured.add(key);
            else if (key.equals("Overworld") || key.startsWith("planet.")) configured.add("SS_" + key);
            else {
                try {
                    if (key.equals(Integer.toString(Integer.parseInt(key)))) configured.add(key);
                } catch (NumberFormatException ignored) {
                    // Other legacy Dyson keys do not name a provider this boiler can look up.
                }
            }
        }
        if (configured.contains("29")) keys.add("29");
        keys.addAll(configured);
        List<Dimension> result = new ArrayList<>();
        for (String key : keys) {
            String lookup = key.startsWith("SS_") ? key.substring(3) : key;
            result.add(
                new Dimension(
                    key,
                    lookup,
                    key.equals("PS") ? PRIVATE : galacticraft ? factor.applyAsDouble(lookup) : 1));
        }
        return result;
    }
}
