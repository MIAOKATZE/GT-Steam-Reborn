// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.tooltip;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipChart;

/**
 * Share exact inputs across the curves, axis ticks and the probe; in particular don't interpolate across the TCDS
 * knees.
 */
final class GTSRReviewedCurve {

    private GTSRReviewedCurve() {}

    static TooltipChart chart(JsonObject block) {
        double max = n(block, "xMax"), probe = n(block, "probe");
        TreeSet<Double> points = new TreeSet<>();
        double[] xTicks = numbers(block.getAsJsonArray("xTicks"));
        for (double x : xTicks) points.add(x);
        for (int i = 0; i <= 180; i++) points.add(max * i / 180);
        points.add(probe);
        JsonArray series = block.getAsJsonArray("series");
        for (JsonElement e : series) {
            JsonObject s = e.getAsJsonObject();
            for (String key : new String[] { "half", "riseEnd", "plateauEnd" }) if (s.has(key)) points.add(n(s, key));
        }
        double[] xs = points.stream()
            .mapToDouble(Double::doubleValue)
            .toArray();
        double[][] ys = new double[series.size()][xs.length];
        int[] colors = new int[series.size()];
        List<String> legends = new ArrayList<>(), values = new ArrayList<>();
        for (int i = 0; i < series.size(); i++) {
            JsonObject s = series.get(i)
                .getAsJsonObject();
            legends.add(
                s.get("name")
                    .getAsString());
            colors[i] = Integer.decode(
                s.get("color")
                    .getAsString());
            for (int j = 0; j < xs.length; j++) ys[i][j] = value(s, xs[j]);
            values.add(
                BigDecimal.valueOf(value(s, probe))
                    .round(new MathContext(4))
                    .stripTrailingZeros()
                    .toPlainString());
        }
        return TooltipChart.lines(
            block.get("title")
                .getAsString(),
            block.get("xLabel")
                .getAsString(),
            block.get("yLabel")
                .getAsString(),
            xs,
            legends,
            ys)
            .range(max, n(block, "yMax"))
            .plot(xTicks, numbers(block.getAsJsonArray("yTicks")), probe, values, colors);
    }

    private static double value(JsonObject s, double x) {
        return switch (s.get("model")
            .getAsString()) {
            case "saturation" -> n(s, "limit") * x / (x + n(s, "half"));
            case "plateauPower" -> {
                double rise = n(s, "riseEnd"), end = n(s, "plateauEnd");
                yield x <= rise ? x : x <= end ? rise : rise * Math.pow(x / end, n(s, "exponent"));
            }
            default -> throw new IllegalArgumentException("Unknown GTSR curve: " + s);
        };
    }

    private static double n(JsonObject object, String key) {
        return object.get(key)
            .getAsDouble();
    }

    private static double[] numbers(JsonArray array) {
        double[] out = new double[array.size()];
        for (int i = 0; i < out.length; i++) out[i] = array.get(i)
            .getAsDouble();
        return out;
    }
}
