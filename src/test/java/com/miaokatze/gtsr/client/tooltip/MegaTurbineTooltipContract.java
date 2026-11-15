// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.tooltip;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipChart;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTable;

/** Fixed reviewed six-fuel examples, including the exact visible fields and chart alignment. */
public final class MegaTurbineTooltipContract {

    public static void main(String[] args) {
        long[][] expected = { { 2293760, 8454144, 9240576, 22413312, 65863680, 65863680 },
            { 5898240, 20840448, 22413312, 52690944, 151388160, 227082240 },
            { 7208960, 24772608, 26345472, 60555264, 171048960, 427622400 },
            { 2949120, 10420224, 11206656, 26345472, 75694080, 75694080 },
            { 7208960, 24772608, 26345472, 60555264, 171048960, 256573440 },
            { 8519680, 28704768, 30277632, 68419584, 190709760, 476774400 } };
        int[] savings = { 20, 28, 33, 38, 43, 43 };
        for (int fuel = 0; fuel < expected.length; fuel++) {
            List<GTSRMegaTurbineTooltip.Stage> stages = GTSRMegaTurbineTooltip
                .stages(GTSRMegaTurbineTooltip.FUELS[fuel]);
            for (int i = 0; i < stages.size(); i++) {
                require(stages.get(i).total == expected[fuel][i], "reviewed exact EU/t, fuel=" + fuel + ", stage=" + i);
                require(stages.get(i).saving == savings[i], "singularity replaces rather than stacks savings");
            }
            for (boolean detail : new boolean[] { false, true }) {
                TooltipChart chart = GTSRMegaTurbineTooltip.chart(fuel, detail);
                require(chart.kind == TooltipChart.Kind.BARS && chart.maximum == 476774400, "shared bar scale");
                require(
                    chart.table.rows()
                        .size() == 6
                        && chart.table.headers()
                            .size() == (detail ? 4 : 5),
                    "all visible fields");
                require(
                    chart.table.measurementRows()
                        .size() >= 36,
                    "widths include all fuel variants");
                for (int column = 0; column < chart.table.headers()
                    .size(); column++)
                    require(chart.table.alignment(column) == TooltipTable.Alignment.LEFT, "reviewed left alignment");
                for (int i = 0; i < 6; i++) {
                    require(chart.value(0, i) == expected[fuel][i], "bars show total, never delta");
                    require(
                        chart.previous(i) == (detail || i == 0 ? expected[fuel][i] : expected[fuel][i - 1]),
                        "cyan baseline and common gain segment");
                    List<String> row = chart.table.rows()
                        .get(i);
                    require(plain(row.get(2)).equals(savings[i] + "%"), "savings visible next to efficiency and power");
                    require(
                        row.get(3)
                            .contains("A LuV") == detail,
                        "amps only on detailed total power");
                    if (!detail) require(
                        !row.get(4)
                            .contains("A LuV"),
                        "gain retains EU/t units");
                }
            }
        }
        require(
            GTSRMegaTurbineTooltip.reading(2293760, true, false)
                .equals("2,293,760 (70 A LuV)"),
            "detailed power units");
        require(
            GTSRMegaTurbineTooltip.reading(6160384, false, true)
                .equals("+6,160,384"),
            "signed increment");
        require(
            GTSRMegaTurbineTooltip.effects()
                .rows()
                .size() == 12,
            "complete component/operation effects table");
        System.out.println(
            "PASS mega tooltip: six fuels × six cumulative stages, common/detail fields, savings, shared scale and widths");
    }

    private static String plain(String text) {
        return EnumChatFormatting.getTextWithoutFormattingCodes(text);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
