// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.tooltip;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
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
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.CriticalEnergyMath;
import com.miaokatze.gtsr.common.critical.CriticalEnergyRuntime;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalPlasmaCatalog;
import com.miaokatze.gtsr.common.critical.CriticalSolarPolicy;
import com.miaokatze.gtsr.common.critical.CriticalSolarPolicy.Entry;
import com.miaokatze.gtsr.common.critical.CriticalTooltips;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipChart;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTable;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.enums.VoltageIndex;
import gregtech.common.blocks.ItemMachines;
import gtPlusPlus.core.material.MaterialMisc;
import gtnhintergalactic.config.IGConfig;

/** The boiler's reviewed information page. The actual GT secondary page remains the construction manual. */
public final class GTSRSolarBoilerTooltip {

    private static final String PREFIX = "gtsr.tooltip.boiler.";
    private static final DecimalFormat NUMBER = new DecimalFormat(
        "#,##0.##",
        DecimalFormatSymbols.getInstance(Locale.US));
    private static final double[][][] CURVES = { GTSRSolarReadings.curve(0), GTSRSolarReadings.curve(1),
        GTSRSolarReadings.curve(2) };
    private static final Map<String, String> BODY_KEYS = new HashMap<>();
    private static List<GTSRSolarReadings.Dimension> configuredDimensions;
    private static String[] configSource;
    private static WeakReference<World> lastWorld = new WeakReference<>(null);
    private static List<GTSRSolarReadings.Dimension> worldDimensions;
    private static boolean watchingResources;
    private static NativeTooltipPage cachedPage;
    private static NativeTooltipPage.Tier cachedTier;
    private static int cachedRoute, cachedDimension;
    private static List<GTSRSolarReadings.Dimension> cachedDimensions;

    private GTSRSolarBoilerTooltip() {}

    public static boolean apply(ItemStack stack, List<String> lines) {
        if (!(ItemMachines.getMetaTileEntity(stack) instanceof MTECriticalMachine machine)
            || machine.getPresetKind() != CriticalMachineKind.SOLAR
            || machine.isDisplaySecondaryDescription()) return false;
        if (!watchingResources && Minecraft.getMinecraft()
            .getResourceManager() instanceof IReloadableResourceManager manager) {
            watchingResources = true;
            manager.registerReloadListener(ignored -> {
                cachedPage = null;
                BODY_KEYS.clear();
            });
        }
        if (!StatCollector.canTranslate(PREFIX + "purpose")) return false;
        NativeTooltipPage page = NativeTooltipPage.forItem(stack);
        boolean brief = page.tier() == NativeTooltipPage.Tier.QUICK;
        boolean detail = page.tier() == NativeTooltipPage.Tier.DETAIL;
        if (!brief && Mods.GalacticraftCore.isModLoaded()
            && (!Mods.GTNHIntergalactic.isModLoaded() || IGConfig.dysonSwarm == null
                || IGConfig.dysonSwarm.powerFactors == null))
            return false;
        List<GTSRSolarReadings.Dimension> dims = brief ? Collections.emptyList() : dimensions();
        int route = brief ? 0 : page.variant("boiler.stock", CURVES.length);
        int dim = brief ? 0 : page.variant("boiler.dimension", dims.size());
        try {
            // A stationary NEI hover is rendered every frame. Rebind the immutable content to this frame's event;
            // no recipe scans, formatted 6×3 matrices, registry lookups or list of 45 translations on this path.
            if (cachedPage != null && cachedTier == page.tier()
                && cachedRoute == route
                && cachedDimension == dim
                && cachedDimensions == dims) return cachedPage.apply(stack, lines);
            page.input(tr("terminal"));
            page.line(tr("purpose"));
            if (brief) page.line(tr("mandatory"));
            page.warning(tr(detail ? "warning.detail" : "warning", CriticalEnergyMath.CYCLE_TICKS / 20));
            if (brief) page.divider();
            page.line(tr("unique"));
            if (!brief) {
                page.divider();
                page.line(tr("stock"));
                curve(page, detail);
                page.divider();
                page.line(tr("mixtures"));
                heatmap(page, dims, detail);
                page.divider();
                page.line(tr("example"));
                example(page, detail);
                page.divider();
                pulse(page, detail);
            }
            page.divider();
            page.structure(tr("construction"));
            page.line(GTSRUtils.getAddedByLine());
            cachedPage = page;
            cachedTier = page.tier();
            cachedRoute = route;
            cachedDimension = dim;
            cachedDimensions = dims;
            return page.apply(stack, lines);
        } catch (IllegalArgumentException invalidTextOrChart) {
            // Replacement is transactional. A resource pack with invalid text retains GT's original builder output.
            return false;
        }
    }

    private static String tr(String key, Object... values) {
        String text = values.length == 0 ? StatCollector.translateToLocal(PREFIX + key)
            : StatCollector.translateToLocalFormatted(PREFIX + key, values);
        if (text.contains("{loom}"))
            text = text.replace("{loom}", Integer.toString(GTSRItemList.CriticalLoom.getMeta()));
        if (text.contains("{pulse}"))
            text = text.replace("{pulse}", Integer.toString(GTSRItemList.CriticalTurbine.getMeta()));
        return text;
    }

    private static String n(double x) {
        return NUMBER.format(x);
    }

    private static String multiplier(double x) {
        return "×" + BigDecimal.valueOf(x)
            .stripTrailingZeros()
            .toPlainString();
    }

    /** The reviewed grid rounds to two decimals; a small positive custom factor must never read as zero output. */
    private static String cellMultiplier(double x) {
        BigDecimal value = BigDecimal.valueOf(x);
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        return "×" + (rounded.signum() == 0 ? value : rounded).stripTrailingZeros()
            .toPlainString();
    }

    private static String medium(String group, Entry e) {
        String label = StatCollector.translateToLocal("gtsr.critical.solar." + group + "." + e.id);
        String id = fluid(
            group.equals("coolant") ? CriticalEnergyRuntime.solarCoolant(e) : CriticalEnergyRuntime.solarExciter(e));
        return id.isEmpty() ? "[" + label + "]" : "[fluid:" + id + "|" + label + "]";
    }

    private static String fluid(FluidStack stack) {
        return stack == null || stack.getFluid() == null ? ""
            : stack.getFluid()
                .getName();
    }

    private static String conditions() {
        return tr(
            "conditions",
            n(GTSRSolarReadings.PRIVATE),
            medium("coolant", GTSRSolarReadings.COOLANT),
            multiplier(GTSRSolarReadings.COOLANT.multiplier),
            medium("exciter", GTSRSolarReadings.EXCITER),
            multiplier(GTSRSolarReadings.EXCITER.multiplier));
    }

    private static void curve(NativeTooltipPage page, boolean detail) {
        List<String> tabs = Arrays.asList(tr("salt"), tr("plasma"), tr("water"));
        int route = page.variant("boiler.stock", tabs.size());
        double[][] curve = CURVES[route];
        String id = new String[] { "salt", "plasma", "water" }[route];
        String formula = route == 1 ? "P/V ≤ s/(s+1)"
            : "y ≤ " + n(GTSRSolarReadings.coefficient(route))
                + "x/(x+"
                + n(GTSRSolarReadings.in(GTSRSolarReadings.HALF, GTSRSolarReadings.G))
                + ")";
        double markerX = route == 1 ? 1 : curve[0][curve[0].length - 1];
        double markerY = route == 1 ? 0.5 : curve[1][curve[1].length - 1];
        page.chart(
            "boiler.stock",
            tabs,
            TooltipChart
                .lines(
                    "",
                    tr("x." + id),
                    tr("y." + id),
                    curve[0],
                    Collections.singletonList(formula),
                    new double[][] { curve[1] })
                .range(curve[0][curve[0].length - 1], new double[] { 100, 1, 30 }[route])
                .mark(markerX, markerY));
        if (route != 1) page.line(conditions());
        page.line(tr("curve." + id));
        if (detail) {
            page.line(tr("stock.detail"));
            page.line(tr("plasma.nei"));
        }
    }

    private static List<GTSRSolarReadings.Dimension> dimensions() {
        boolean gc = Mods.GalacticraftCore.isModLoaded();
        String[] source = gc ? IGConfig.dysonSwarm.powerFactors : null;
        if (configuredDimensions == null || configSource != source) {
            configSource = source;
            configuredDimensions = GTSRSolarReadings
                .dimensions(source, gc, key -> IGConfig.dysonSwarm.getPowerFactor(key));
            worldDimensions = null;
        }
        // A modpack can add a dimension without adding a Dyson factor. Show its actual provider resolution as well.
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null || world.provider == null) return configuredDimensions;
        if (worldDimensions != null && world == lastWorld.get()) return worldDimensions;
        lastWorld = new WeakReference<>(world);
        worldDimensions = configuredDimensions;
        if (world.provider.getClass()
            .getName()
            .equals("me.eigenraven.personalspace.world.PersonalWorldProvider")) return configuredDimensions;
        String lookup = String.valueOf(world.provider.dimensionId);
        if (gc) {
            try {
                Class<?> orbit = Class.forName("micdoodle8.mods.galacticraft.api.world.IOrbitDimension");
                if (orbit.isInstance(world.provider)) lookup = String.valueOf(
                    orbit.getMethod("getPlanetToOrbit")
                        .invoke(world.provider));
            } catch (ReflectiveOperationException unavailable) {
                // The known catalog is still valid; no basis to infer this world's provider.
                return configuredDimensions;
            }
        }
        for (var d : configuredDimensions) if (lookup.equals(d.lookup)) return configuredDimensions;
        List<GTSRSolarReadings.Dimension> result = new ArrayList<>(configuredDimensions);
        result.add(new GTSRSolarReadings.Dimension("current", lookup, CriticalEnergyRuntime.dimensionFactor(world)));
        worldDimensions = result;
        return worldDimensions;
    }

    private static String dimensionName(GTSRSolarReadings.Dimension d) {
        if ("current".equals(d.key))
            return tr("current", Minecraft.getMinecraft().theWorld.provider.getDimensionName());
        if (d.key.equals("0") || d.key.equals("PS") || d.key.startsWith("SS_"))
            return StatCollector.canTranslate(PREFIX + "dim." + d.key) ? tr("dim." + d.key) : tr("orbit", d.lookup);
        // Identify by the running Galaxy Registry, so remapped IDs receive the correct celestial body name.
        String body = BODY_KEYS.computeIfAbsent(d.key, GTSRSolarBoilerTooltip::bodyKey);
        if (!body.isEmpty()) {
            if (StatCollector.canTranslate(body)) return StatCollector.translateToLocal(body)
                .split("#", 2)[0].trim();
            if (StatCollector.canTranslate(PREFIX + "body." + body)) return tr("body." + body);
        }
        return tr("dimension.id", d.key);
    }

    private static String bodyKey(String id) {
        if (!Mods.GalacticraftCore.isModLoaded()) return "";
        try {
            Class<?> registry = Class.forName("micdoodle8.mods.galacticraft.api.galaxies.GalaxyRegistry");
            Object body = registry.getMethod("getCelestialBodyFromDimensionID", int.class)
                .invoke(null, Integer.parseInt(id));
            return body == null ? ""
                : (String) body.getClass()
                    .getMethod("getUnlocalizedName")
                    .invoke(body);
        } catch (ReflectiveOperationException | NumberFormatException unavailable) {
            return "";
        }
    }

    private static void heatmap(NativeTooltipPage page, List<GTSRSolarReadings.Dimension> dimensions, boolean detail) {
        int selected = page.variant("boiler.dimension", dimensions.size());
        var d = dimensions.get(selected);
        List<String> tabs = new ArrayList<>();
        double maxFactor = GTSRSolarReadings.PRIVATE;
        for (var dimension : dimensions) {
            tabs.add(dimensionName(dimension));
            maxFactor = Math.max(maxFactor, dimension.factor);
        }
        List<String> headers = new ArrayList<>(), rowFluids = new ArrayList<>(), colFluids = new ArrayList<>();
        List<String> rowFactors = new ArrayList<>(), colFactors = new ArrayList<>();
        List<String> rowRates = new ArrayList<>(), colRates = new ArrayList<>();
        headers.add(tr("exciter"));
        List<Entry> coolants = CriticalSolarPolicy.COOLANTS, exciters = CriticalSolarPolicy.EXCITERS;
        double maxCool = 0, maxEx = 0;
        for (Entry c : coolants) {
            headers.add(medium("coolant", c));
            colFluids.add(fluid(CriticalEnergyRuntime.solarCoolant(c)));
            colFactors.add(multiplier(c.multiplier));
            colRates.add(CriticalTooltips.solarMediumFlow(c) + " L/s");
            maxCool = Math.max(maxCool, c.multiplier);
        }
        List<List<String>> rows = new ArrayList<>();
        double[][] factors = new double[exciters.size()][coolants.size()];
        for (int i = 0; i < exciters.size(); i++) {
            Entry e = exciters.get(i);
            List<String> cells = new ArrayList<>();
            cells.add(medium("exciter", e));
            rowFluids.add(fluid(CriticalEnergyRuntime.solarExciter(e)));
            rowFactors.add(multiplier(e.multiplier));
            rowRates.add(CriticalTooltips.solarMediumFlow(e) + " L/s");
            maxEx = Math.max(maxEx, e.multiplier);
            for (int j = 0; j < coolants.size(); j++) {
                factors[i][j] = GTSRSolarReadings.combined(d.factor, coolants.get(j), e);
                cells.add(cellMultiplier(factors[i][j]));
            }
            rows.add(cells);
        }
        String title = tabs.get(selected) + " " + multiplier(d.factor) + (d.factor == 0 ? " · " + tr("no.output") : "");
        page.chart(
            "boiler.dimension",
            tabs,
            TooltipChart
                .heat(
                    title,
                    TooltipTable.of(title, headers, rows),
                    factors,
                    maxFactor * maxCool * maxEx,
                    rowFluids,
                    colFluids)
                .media(rowFactors, rowRates, colFactors, colRates, detail));
    }

    private static void example(NativeTooltipPage page, boolean detail) {
        page.line(conditions());
        page.line(tr("basis", n(GTSRSolarReadings.in(GTSRSolarReadings.STOCK, GTSRSolarReadings.G))));
        List<String> icons = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        List<double[]> values = new ArrayList<>();
        product(
            rows,
            values,
            icons,
            tr("dense"),
            CriticalEnergyRuntime.steam(5),
            GTSRSolarReadings.STEAM,
            GTSRSolarReadings.STEAM_HEAT);
        product(
            rows,
            values,
            icons,
            tr("hot.salt"),
            MaterialMisc.SOLAR_SALT_HOT.getFluidStack(1),
            GTSRSolarReadings.SALT,
            GTSRSolarReadings.saltHeat(2));
        FluidStack nitrogen = Materials.Nitrogen.getPlasma(1);
        boolean nitrogenAvailable = false;
        if (nitrogen != null) {
            for (var plasma : plasmaRecipes()) {
                if (!nitrogen.isFluidEqual(plasma.output)) continue;
                BigInteger output = plasma
                    .batchLitres(
                        GTSRSolarReadings.STOCK,
                        GTSRSolarReadings.PRIVATE,
                        GTSRSolarReadings.COOLANT,
                        GTSRSolarReadings.EXCITER)
                    .min(GTSRSolarReadings.STOCK);
                product(
                    rows,
                    values,
                    icons,
                    tr("nitrogen"),
                    plasma.output,
                    output,
                    plasma.equivalentEU(output)
                        .divide(BigInteger.valueOf(CriticalSolarPolicy.FULL_TURBINE_MULTIPLIER)));
                nitrogenAvailable = true;
                break;
            }
        }
        TooltipTable table = TooltipTable.of("", Arrays.asList(tr("product"), tr("output"), tr("heat")), rows);
        page.block(
            TooltipChart.columns("", table, values.toArray(new double[0][]), icons)
                .compact(!detail));
        if (!nitrogenAvailable) page.line(tr("missing.nitrogen"));
        if (detail) {
            page.line(tr("heat.detail"));
            page.line(
                tr(
                    "salt.detail",
                    n(GTSRSolarReadings.in(GTSRSolarReadings.WATER, GTSRSolarReadings.G)),
                    n(GTSRSolarReadings.in(GTSRSolarReadings.saltHeat(1), GTSRSolarReadings.T))));
        }
    }

    private static List<CriticalPlasmaCatalog.Entry> plasmaRecipes() {
        try {
            return CriticalPlasmaCatalog.entries();
        } catch (IllegalStateException noEligibleExchanger) {
            // Registration may be incomplete, or a modpack may disable GG's exchanger routes.
            // Water, salt and the rest of the page still have valid data.
            return Collections.emptyList();
        }
    }

    private static void product(List<List<String>> rows, List<double[]> values, List<String> icons, String name,
        FluidStack fluid, BigInteger output, BigInteger heat) {
        double volume = GTSRSolarReadings.in(output, GTSRSolarReadings.G),
            eu = GTSRSolarReadings.in(heat, GTSRSolarReadings.T);
        rows.add(Arrays.asList(name, n(volume), n(eu)));
        values.add(new double[] { volume, eu });
        icons.add(fluid(fluid));
    }

    private static void pulse(NativeTooltipPage page, boolean detail) {
        double energy = GTSRSolarReadings.in(GTSRSolarReadings.PULSE, GTSRSolarReadings.T);
        page.line(detail ? tr("pulse.detail", CriticalEnergyMath.MAX_EFFICIENCY) : tr("pulse"));
        if (detail) {
            page.line(
                tr(
                    "pulse.calc",
                    n(GTSRSolarReadings.in(GTSRSolarReadings.STEAM_HEAT, GTSRSolarReadings.T)),
                    n(CriticalEnergyMath.MAX_EFFICIENCY / 100D),
                    n(energy)));
            double power = GTSRSolarReadings.PULSE.doubleValue() / CriticalEnergyMath.CYCLE_TICKS;
            page.line(
                tr(
                    "pulse.average",
                    n(power / GTSRSolarReadings.T.doubleValue()),
                    n(power / GTValues.V[VoltageIndex.MAX])));
        } else page.line(tr("pulse.max", n(energy)));
    }
}
