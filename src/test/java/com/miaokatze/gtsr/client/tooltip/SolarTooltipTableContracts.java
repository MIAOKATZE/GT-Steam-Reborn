// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.tooltip;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtsr.common.critical.CriticalSolarPolicy;

/** Verifies the visible catalogue against the actual fixed twenty-second consumption policy. */
public final class SolarTooltipTableContracts {

    public static void main(String[] args) {
        verify(
            CriticalSolarPolicy.COOLANTS,
            "coolant",
            new String[] { "40,000", "20,000", "200" },
            new String[] { "1", "1.5", "2.5" });
        verify(
            CriticalSolarPolicy.EXCITERS,
            "exciter",
            new String[] { "30,000", "800", "800", "800", "800", "200" },
            new String[] { "1", "1.5", "2", "3", "4", "8" });
        System.out.println("PASS solar tooltip: 9 policy rows, labels, units, multipliers and actual batch costs");
    }

    private static void verify(List<CriticalSolarPolicy.Entry> entries, String group, String[] flows,
        String[] multipliers) {
        List<List<String>> rows = GTSRSolarTooltipTables.rows(entries, group, EnumChatFormatting.AQUA, key -> key);
        if (rows.size() != flows.length) throw new AssertionError("Medium count changed");
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            List<String> cells = rows.get(i)
                .stream()
                .map(EnumChatFormatting::getTextWithoutFormattingCodes)
                .collect(java.util.stream.Collectors.toList());
            if (!cells.equals(
                java.util.Arrays
                    .asList("gtsr.critical.solar." + group + "." + entry.id, flows[i] + " L/s", "×" + multipliers[i])))
                throw new AssertionError("Changed material order, unit, flow or multiplier: " + cells);
            var cost = CriticalSolarPolicy.costLitres(entry, java.math.BigInteger.ONE);
            var expected = java.math.BigInteger.valueOf(Long.parseLong(flows[i].replace(",", "")) * 20);
            if (!cost.equals(expected)) throw new AssertionError("Display must agree with fixed batch debit");
        }
    }
}
