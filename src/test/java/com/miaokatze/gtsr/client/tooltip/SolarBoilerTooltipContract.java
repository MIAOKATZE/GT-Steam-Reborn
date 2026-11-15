// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.tooltip;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.miaokatze.gtsr.common.critical.CriticalSolarPolicy;

import gtnhintergalactic.config.IGConfig;

/** Checks the visible scenarios against GT's shipping configuration and exact inventory policy, without a world. */
final class SolarBoilerTooltipContract {

    static void verify() throws ReflectiveOperationException {
        // Read the actual dependency's default list, not a hard-coded replica of its 45 table entries.
        String[] defaults = IGConfig.DysonSwarm.class.getField("powerFactors")
            .getAnnotation(Config.DefaultStringList.class)
            .value();
        IGConfig.DysonSwarm config = new IGConfig.DysonSwarm();
        config.powerFactors = defaults;
        List<GTSRSolarReadings.Dimension> dims = GTSRSolarReadings.dimensions(defaults, true, config::getPowerFactor);
        check(dims.size() == 45, "shipping sources");
        check(dims.get(0).key.equals("PS") && dims.get(1).key.equals("0") && dims.get(2).key.equals("29"), "tab order");
        int cells = 0, stations = 0;
        double max = 0;
        for (var d : dims) {
            if (d.key.startsWith("SS_")) {
                stations++;
                check(config.getPowerFactor(d.key) > 0 && d.factor == 0, "station uses provider lookup, not SS_ key");
            } else if (d.key.equals("PS")) {
                check(d.factor == 0.6 && config.getPowerFactor("PS") == 0.01, "personal override");
            } else check(config.getPowerFactor(d.key) == d.factor, "numeric dimension");
            for (var c : CriticalSolarPolicy.COOLANTS) for (var e : CriticalSolarPolicy.EXCITERS) {
                double v = GTSRSolarReadings.combined(d.factor, c, e);
                check(Math.abs(v - d.factor * c.multiplier * e.multiplier) < 1e-9, "matrix cell");
                max = Math.max(max, v);
                cells++;
            }
        }
        check(cells == 810 && stations == 7 && Math.abs(max - 67.4) < 1e-9, "whole heat scale");
        IGConfig.DysonSwarm custom = new IGConfig.DysonSwarm();
        custom.powerFactorDefault = 0.25;
        custom.powerFactors = new String[] { "SS_planet.mars:19", "planet.mars:0.75", "777:3", "777:4", "UW_Void:99" };
        List<GTSRSolarReadings.Dimension> modded = GTSRSolarReadings
            .dimensions(custom.powerFactors, true, custom::getPowerFactor);
        check(
            modded.size() == 4 && modded.get(1).factor == 0.25
                && modded.get(2).factor == 0.75
                && modded.get(3).factor == 4,
            "default, orbit body, custom dimension and duplicate schedules");
        check(
            GTSRSolarReadings.dimensions(custom.powerFactors, false, key -> { throw new AssertionError(key); })
                .stream()
                .allMatch(d -> d.factor == (d.key.equals("PS") ? 0.6 : 1)),
            "no Galacticraft");

        double[][] salt = GTSRSolarReadings.curve(0), plasma = GTSRSolarReadings.curve(1),
            water = GTSRSolarReadings.curve(2);
        check(
            sample(salt, 8) == 8 && sample(salt, 500) == 90
                && sample(plasma, 1) == .5
                && sample(plasma, 4) == .8
                && sample(water, 500) == 27,
            "curve markers and stock cap");
        check(
            GTSRSolarReadings.WATER.equals(new BigInteger("168750000000"))
                && GTSRSolarReadings.STEAM.equals(new BigInteger("27000000000"))
                && GTSRSolarReadings.STEAM_HEAT.equals(new BigInteger("67500000000000"))
                && GTSRSolarReadings.SALT.equals(new BigInteger("90000000000"))
                && GTSRSolarReadings.saltHeat(2)
                    .equals(new BigInteger("225000000000000"))
                && GTSRSolarReadings.saltHeat(1)
                    .equals(new BigInteger("135000000000000"))
                && GTSRSolarReadings.PULSE.equals(new BigInteger("1350000000000000")),
            "fixed example and maximum pulse output");
        // The audited GT/GG nitrogen exchanger: same 500 G L stock as steam and salt, not the separate NEI 5H example.
        BigInteger hot = BigInteger.valueOf(2500), dense = BigInteger.valueOf(290300);
        BigInteger out = CriticalSolarPolicy.batchPlasmaLitres(
            GTSRSolarReadings.STOCK,
            GTSRSolarReadings.PRIVATE,
            GTSRSolarReadings.COOLANT,
            GTSRSolarReadings.EXCITER,
            hot,
            dense);
        check(out.equals(BigInteger.valueOf(697074010L)), "nitrogen batch");
        System.out.println(
            "PASS boiler: 45 source-backed dimensions, 810 cells, modded orbit/defaults, 3 curves and fixed production");
    }

    private static double sample(double[][] points, double stock) {
        int index = Arrays.binarySearch(points[0], stock);
        if (index < 0) throw new AssertionError("Missing curve sample at " + stock);
        return points[1][index];
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
