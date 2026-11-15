// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.tooltip;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;
import com.miaokatze.gtsr.common.machine.turbine.MegaSteamTurbinePolicy;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes.SteamType;
import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipChart;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTable;

import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.ISecondaryDescribable;
import gregtech.common.blocks.ItemMachines;

/** Client presentation of the reviewed MegaExample; all example bonuses use the runtime policy. */
public final class GTSRMegaTurbineTooltip {

    private static final String PREFIX = "gtsr.tooltip.mega_native.";
    private static final String CHART_ID = "gtsr.mega.upgrades";
    static final int EXAMPLE_CASING = 6; // LuV, at which all six steam families are usable.
    static final SteamType[] FUELS = { SteamType.STEAM, SteamType.SH_STEAM, SteamType.SC_STEAM, SteamType.DENSE_STEAM,
        SteamType.DENSE_SH_STEAM, SteamType.DENSE_SC_STEAM };
    private static final String[] STEP_KEYS = { "base", "rings", "tungsten", "entangled", "critical", "chip" };
    private static Presentation presentation;
    private static boolean reloadListenerRegistered;

    private GTSRMegaTurbineTooltip() {}

    public static boolean apply(ItemStack stack, List<String> lines) {
        var meta = ItemMachines.getMetaTileEntity(stack);
        if (!(meta instanceof MTEMegaSteamTurbineArray)) return false;
        if (meta instanceof ISecondaryDescribable secondary && secondary.isDisplaySecondaryDescription()) return false;
        Presentation view = presentation();
        NativeTooltipPage page = NativeTooltipPage.forItem(stack);
        boolean detail = page.tier() == NativeTooltipPage.Tier.DETAIL;
        page.line("§f" + view.text("purpose"));
        page.divider();
        page.line("§f" + view.text("formula"));
        if (detail) {
            page.line("§7" + view.text("baseline"));
            page.block(view.effects);
            page.line("§7" + view.text("conditions"));
        }
        page.divider();
        page.line("§7" + view.text("supply"));
        page.line("§7" + view.text("rating"));
        page.line("§7" + view.text("glass"));
        page.warning("§c" + view.text("risk"));
        page.divider();
        if (page.tier() != NativeTooltipPage.Tier.QUICK) {
            page.line("§7" + view.text("sample"));
            page.chart(CHART_ID, view.fuels, view.charts[detail ? 1 : 0][page.variant(CHART_ID, FUELS.length)]);
            page.divider();
            page.line("§7" + view.text("control"));
        }
        page.structure("§7" + view.text("structure"));
        page.line(GTSRUtils.getAddedByLine());
        return page.apply(stack, lines);
    }

    /** Only immutable presentation data is retained; never a hovered stack, NBT or event list. */
    private static synchronized Presentation presentation() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!reloadListenerRegistered && minecraft.getResourceManager() instanceof IReloadableResourceManager manager) {
            manager.registerReloadListener(ignored -> invalidatePresentation());
            reloadListenerRegistered = true;
        }
        String language = minecraft.gameSettings == null ? "" : minecraft.gameSettings.language;
        if (presentation == null || !presentation.language.equals(language)) presentation = new Presentation(language);
        return presentation;
    }

    static synchronized void invalidatePresentation() {
        presentation = null;
    }

    private static final class Presentation {

        final String language;
        final TooltipChart[][] charts = new TooltipChart[2][FUELS.length];
        final TooltipTable effects = effects();
        final List<String> fuels;
        final Map<String, String> messages = new HashMap<>();

        Presentation(String language) {
            this.language = language;
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < FUELS.length; i++) {
                labels.add(StatCollector.translateToLocal(FUELS[i].nameKey));
                charts[0][i] = chart(i, false);
                charts[1][i] = chart(i, true);
            }
            fuels = Collections.unmodifiableList(labels);
            for (String key : new String[] { "purpose", "formula", "baseline", "conditions", "supply", "rating",
                "glass", "risk", "sample", "control", "structure" })
                messages.put(key, GTSRMegaTurbineTooltip.text(key));
        }

        String text(String key) {
            return messages.get(key);
        }
    }

    static TooltipChart chart(int selected, boolean detail) {
        List<List<String>> measurements = new ArrayList<>();
        double maximum = 0;
        for (SteamType fuel : FUELS) {
            List<Stage> stages = stages(fuel);
            measurements.addAll(rows(stages, detail));
            for (Stage stage : stages) maximum = Math.max(maximum, stage.total);
        }
        List<Stage> stages = stages(FUELS[selected]);
        TooltipTable table = leftAligned(
            TooltipTable.of("", stageHeaders(detail), rows(stages, detail))
                .withMeasurementRows(measurements));
        double[] totals = new double[stages.size()];
        double[] previous = detail ? null : new double[stages.size()];
        for (int i = 0; i < stages.size(); i++) {
            totals[i] = stages.get(i).total;
            if (previous != null) previous[i] = i == 0 ? totals[i] : stages.get(i - 1).total;
        }
        return TooltipChart.bars("", table, totals, previous, maximum);
    }

    private static List<String> stageHeaders(boolean detail) {
        List<String> headers = new ArrayList<>(
            Arrays.asList(
                text("column.upgrade"),
                text("column.efficiency"),
                text("column.savings"),
                text("column.power")));
        if (!detail) headers.add(text("column.gain"));
        return headers;
    }

    static List<List<String>> rows(List<Stage> stages, boolean detail) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            Stage stage = stages.get(i);
            List<String> row = new ArrayList<>(
                Arrays.asList(
                    "§f" + text("step." + STEP_KEYS[i]),
                    "§7" + stage.efficiency / 100 + "%",
                    "§a" + stage.saving + "%",
                    "§b" + reading(stage.total, detail, false)));
            if (!detail) row.add(i == 0 ? "§8—" : "§e" + reading(stage.total - stages.get(i - 1).total, false, true));
            rows.add(row);
        }
        return rows;
    }

    static List<Stage> stages(SteamType fuel) {
        List<Stage> result = new ArrayList<>();
        for (int step = 0; step < STEP_KEYS.length; step++) {
            int rings = step >= 1 ? 4 : 0;
            int material = step >= 2 ? 3 : 1;
            int singularity = step >= 4 ? 2 : step >= 3 ? 1 : 0;
            int efficiency = MegaSteamTurbinePolicy
                .efficiencyLimit(fuel.maxEfficiency, EXAMPLE_CASING, rings, material, singularity);
            int saving = MegaSteamTurbinePolicy.savingsPercent(10, rings, material, singularity);
            float factor = SteamTurbineSteamTypes.effectiveSteamEffFactor(fuel, step == 5);
            long base = (long) (GTValues.V[EXAMPLE_CASING] * MTEMegaSteamTurbineArray.BASE_POWER_MULTIPLIER
                * 10L
                * (2 + rings)
                * MegaSteamTurbinePolicy.powerMultiplier(singularity)
                * factor);
            long total = base * efficiency / 10000;
            result.add(new Stage(efficiency, saving, total));
        }
        return result;
    }

    static String reading(long power, boolean detail, boolean gain) {
        DecimalFormat format = new DecimalFormat("#,##0.########", DecimalFormatSymbols.getInstance(Locale.US));
        String sign = gain && power > 0 ? "+" : "";
        return sign + format.format(power)
            + (detail ? " (" + sign + format.format((double) power / GTValues.V[EXAMPLE_CASING]) + " A LuV)" : "");
    }

    static TooltipTable effects() {
        List<List<String>> rows = new ArrayList<>();
        for (String key : new String[] { "casing", "ring", "load", "titanium", "tungsten", "steam", "heated", "dense",
            "entangled", "critical", "chip_heated", "chip_critical" }) {
            rows.add(
                Arrays.asList(
                    text("effect." + key),
                    text("effect." + key + ".power"),
                    text("effect." + key + ".efficiency"),
                    text("effect." + key + ".savings")));
        }
        return leftAligned(
            TooltipTable.of(
                "",
                Arrays.asList(
                    text("column.effect"),
                    text("column.power_term"),
                    text("column.cap_pp"),
                    text("column.saving_pp")),
                rows));
    }

    private static TooltipTable leftAligned(TooltipTable table) {
        for (int column = 0; column < table.headers()
            .size(); column++) table = table.align(column, TooltipTable.Alignment.LEFT);
        return table;
    }

    private static String text(String key) {
        return StatCollector.translateToLocal(PREFIX + key);
    }

    static final class Stage {

        final int efficiency, saving;
        final long total;

        Stage(int efficiency, int saving, long total) {
            this.efficiency = efficiency;
            this.saving = saving;
            this.total = total;
        }
    }
}
