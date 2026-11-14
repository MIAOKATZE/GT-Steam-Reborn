// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.tooltip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalSolarPolicy;
import com.miaokatze.gtsr.common.critical.CriticalTooltips;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipArt;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltips;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTable;

import gregtech.api.interfaces.ISecondaryDescribable;
import gregtech.common.blocks.ItemMachines;

/** Client-only presentation of the boiler's authoritative fixed medium catalogue. */
public final class GTSRSolarTooltipTables {

    private static final String PREFIX = "gtsr.critical.";

    private GTSRSolarTooltipTables() {}

    public static void apply(ItemStack stack, List<String> lines) {
        var meta = ItemMachines.getMetaTileEntity(stack);
        if (!(meta instanceof MTECriticalMachine machine) || machine.getPresetKind() != CriticalMachineKind.SOLAR)
            return;
        if (meta instanceof ISecondaryDescribable secondary && secondary.isDisplaySecondaryDescription()) return;
        replace(
            stack,
            lines,
            "coolant",
            "solar.coolant",
            CriticalSolarPolicy.COOLANTS,
            EnumChatFormatting.AQUA,
            TooltipArt.STEAM);
        replace(
            stack,
            lines,
            "exciter",
            "solar.excitation",
            CriticalSolarPolicy.EXCITERS,
            EnumChatFormatting.LIGHT_PURPLE,
            TooltipArt.RIFT);
    }

    private static void replace(ItemStack stack, List<String> lines, String group, String titleKey,
        List<CriticalSolarPolicy.Entry> entries, EnumChatFormatting color, TooltipArt art) {
        String title = EnumChatFormatting.GRAY + StatCollector.translateToLocal(PREFIX + "tooltip.native." + titleKey);
        List<String> expected = new ArrayList<>();
        expected.add(title);
        for (var entry : entries) expected.add(CriticalTooltips.solarMediumLine(group, entry, color));
        if (Collections.indexOfSubList(lines, expected) < 0) return;
        try {
            TooltipTable table = TooltipTable
                .of(
                    title,
                    java.util.Arrays.asList(
                        StatCollector.translateToLocal("gtsr.tooltip.table.material"),
                        StatCollector.translateToLocal("gtsr.tooltip.table.flow"),
                        StatCollector.translateToLocal("gtsr.tooltip.table.multiplier")),
                    rows(entries, group, color, StatCollector::translateToLocal))
                .withArt(art);
            // Missing renderer, folded page or a resource-pack rewrite keeps the complete original text.
            NativeTooltips.replace(stack, lines, expected, table);
        } catch (IllegalArgumentException ignored) {
            // A resource pack may supply multiline cells; the original native description remains usable.
        }
    }

    static List<List<String>> rows(List<CriticalSolarPolicy.Entry> entries, String group, EnumChatFormatting color,
        Function<String, String> translate) {
        List<List<String>> rows = new ArrayList<>();
        for (var entry : entries) {
            rows.add(
                java.util.Arrays.asList(
                    color + translate.apply(PREFIX + "solar." + group + "." + entry.id) + EnumChatFormatting.RESET,
                    EnumChatFormatting.WHITE + CriticalTooltips.solarMediumFlow(entry)
                        + " L/s"
                        + EnumChatFormatting.RESET,
                    EnumChatFormatting.LIGHT_PURPLE + "×"
                        + CriticalTooltips.solarMediumMultiplier(entry)
                        + EnumChatFormatting.RESET));
        }
        return rows;
    }
}
