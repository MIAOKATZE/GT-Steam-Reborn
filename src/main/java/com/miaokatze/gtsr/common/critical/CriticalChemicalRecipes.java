package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.miaokatze.gtsr.common.critical.CompoundRecipe.Component;
import com.miaokatze.gtsr.common.critical.CriticalGTRecipes.ChemicalFuel;

import gregtech.api.enums.Materials;
import gregtech.api.util.GTUtility;
import gtPlusPlus.core.fluids.GTPPFluids;

/** Fixed GT205 identities: 120 binary, 136 ternary, 96 quaternary, 80 quinary and 80 senary blends. */
public final class CriticalChemicalRecipes {

    private static Map<GTUtility.FluidId, BigInteger> cachedPrices = ImmutableMap.of();
    private static List<CompoundRecipe> cachedCatalog = ImmutableList.of();
    private static List<CompoundRecipe> cachedLegacyV2Catalog = ImmutableList.of();
    private static List<GTUtility.FluidId> cachedIdentities = ImmutableList.of();
    private static Fluid cachedPromoter, cachedOzone;
    private static volatile Map<String, CompoundRecipe> displayCatalog = ImmutableMap.of();

    private CriticalChemicalRecipes() {}

    public static synchronized List<CompoundRecipe> catalog(List<ChemicalFuel> fuels) {
        FluidStack[] identities = { fluid(Materials.Diesel), fluid(Materials.LightFuel), fluid(Materials.HeavyFuel),
            fluid(Materials.NitroFuel), fluid(Materials.GasolineRegular), fluid(Materials.GasolinePremium),
            fluid(Materials.Ethanol), fluid(Materials.Methanol), fluid(Materials.Hydrogen), fluid(Materials.Methane),
            fluid(Materials.Benzene), fluid(Materials.NefariousGas), stack(GTPPFluids.RP1RocketFuel),
            stack(GTPPFluids.DenseHydrazineFuelMixture), stack(GTPPFluids.CN3H7O3RocketFuel),
            stack(GTPPFluids.H8N4C2O4RocketFuel) };
        ChemicalFuel[] selected = new ChemicalFuel[identities.length];
        Map<GTUtility.FluidId, ChemicalFuel> prices = prices(fuels);
        Map<GTUtility.FluidId, BigInteger> signature = new LinkedHashMap<>();
        for (ChemicalFuel fuel : prices.values()) signature.put(GTUtility.FluidId.create(fuel.fluid), fuel.euPerL);
        Fluid promoterFluid = FluidRegistry.getFluid("combustionpromotor");
        FluidStack ozoneStack = fluid(Materials.Ozone);
        Fluid ozoneFluid = ozoneStack == null ? null : ozoneStack.getFluid();
        List<GTUtility.FluidId> identitySignature = new ArrayList<>();
        for (FluidStack identity : identities)
            identitySignature.add(identity == null ? null : GTUtility.FluidId.create(identity));
        if (!cachedCatalog.isEmpty() && signature.equals(cachedPrices)
            && identitySignature.equals(cachedIdentities)
            && promoterFluid == cachedPromoter
            && ozoneFluid == cachedOzone) return cachedCatalog;
        for (int i = 0; i < identities.length; i++) {
            if (identities[i] == null) continue;
            ChemicalFuel fuel = prices.get(GTUtility.FluidId.create(identities[i]));
            if (fuel != null && fuel.euPerL.signum() > 0) selected[i] = fuel;
        }
        List<CompoundRecipe> result = new ArrayList<>(512);
        List<CompoundRecipe> legacy = new ArrayList<>(256);
        for (int i = 0; i < selected.length; i++) for (int j = i + 1; j < selected.length; j++) {
            int family = (i / 8 == j / 8 ? 0 : 12) + (j >= 12 ? 12 : 0);
            addLegacy(
                result,
                legacy,
                "pair." + i + "." + j,
                tierPeak(2, i * 16 + j),
                12 + family + (i % 4) * 3 + j % 4,
                component(selected[i], 1 + i % 4),
                component(selected[j], 1 + j % 3));
        }
        for (int liquid = 0; liquid < 8; liquid++)
            for (int gas = 0; gas < 4; gas++) for (int rocket = 0; rocket < 4; rocket++) {
                addLegacy(
                    result,
                    legacy,
                    "blend." + liquid + "." + gas + "." + rocket,
                    tierPeak(3, liquid * 16 + gas * 4 + rocket),
                    48 + liquid * 3 + gas * 4 + rocket * 5,
                    component(selected[liquid], 4 + liquid % 3),
                    component(selected[8 + gas], 2 + gas),
                    component(selected[12 + rocket], 1 + rocket));
            }
        ChemicalFuel promoter = auxiliary("combustionpromotor", prices);
        ChemicalFuel ozone = auxiliary(fluid(Materials.Ozone), prices);
        for (int rocket = 0; rocket < 4; rocket++) {
            addLegacy(
                result,
                legacy,
                "promoted." + rocket,
                37 + rocket,
                94 + rocket * 2,
                component(selected[5], 8),
                component(selected[12 + rocket], 2 + rocket),
                component(promoter, 1));
            addLegacy(
                result,
                legacy,
                "ozonated." + rocket,
                34 + rocket,
                88 + rocket * 3,
                component(selected[11], 6),
                component(selected[12 + rocket], 2 + rocket),
                component(ozone, 1));
        }
        for (int liquid = 0; liquid < 8; liquid++)
            for (int gas = 0; gas < 4; gas++) for (int rocket = 0; rocket < 4; rocket++) {
                int variant = gas * 4 + rocket;
                if (rocket < 3) add(
                    result,
                    "quaternary." + liquid + "." + gas + "." + rocket,
                    tierPeak(4, liquid * 12 + gas * 3 + rocket),
                    component(selected[liquid], 4 + liquid % 3),
                    component(selected[8 + gas], 2 + gas),
                    component(selected[12 + rocket], 1 + rocket),
                    component(promoter, 1));
                if (variant >= 10) continue;
                add(
                    result,
                    "quinary." + liquid + "." + gas + "." + rocket,
                    tierPeak(5, liquid * 10 + variant),
                    component(selected[liquid], 4 + liquid % 3),
                    component(selected[8 + gas], 2 + gas),
                    component(selected[12 + rocket], 1 + rocket),
                    component(promoter, 1),
                    component(ozone, 1));
                // The cyclic neighbouring liquid gives eight distinct liquid pairs, never an alias.
                int secondLiquid = (liquid + 1) % 8;
                add(
                    result,
                    "senary." + liquid + "." + gas + "." + rocket,
                    tierPeak(6, liquid * 10 + variant),
                    component(selected[liquid], 4 + liquid % 3),
                    component(selected[secondLiquid], 2 + secondLiquid % 3),
                    component(selected[8 + gas], 2 + gas),
                    component(selected[12 + rocket], 1 + rocket),
                    component(promoter, 1),
                    component(ozone, 1));
            }
        List<CompoundRecipe> immutable = ImmutableList.copyOf(result);
        // Do not freeze the early registration phase's empty catalogue.
        if (!immutable.isEmpty()) {
            cachedPrices = ImmutableMap.copyOf(signature);
            cachedCatalog = immutable;
            cachedLegacyV2Catalog = ImmutableList.copyOf(legacy);
            cachedIdentities = identitySignature;
            cachedPromoter = promoterFluid;
            cachedOzone = ozoneFluid;
            if (immutable.size() >= displayCatalog.size()) {
                Map<String, CompoundRecipe> index = new LinkedHashMap<>();
                for (CompoundRecipe recipe : immutable) index.put(recipe.id, recipe);
                displayCatalog = ImmutableMap.copyOf(index);
            }
        }
        return immutable;
    }

    /** Complexity bands are disjoint; only six-component reactions can reach 100x. */
    private static int tierPeak(int components, int variant) {
        return components == 2 ? 2 + variant % 19 : (components - 2) * 20 + 1 + variant % 20;
    }

    /** Lookup for client labels; callers localize the component FluidStacks using the active language. */
    public static CompoundRecipe byId(String id) {
        if (displayCatalog.isEmpty()) catalog(CriticalGTRecipes.chemicalFuels());
        return displayCatalog.get(id);
    }

    /** Frozen receipt prices can be supplied here without revaluing already-paid fuel. */
    public static Match best(List<ChemicalFuel> fuels, Map<GTUtility.FluidId, BigInteger> litres) {
        return best(fuels, litres, false);
    }

    /** Only for an already-captured schema-2 payment; never publishes legacy values to NEI/byId. */
    public static Match bestLegacyV2(List<ChemicalFuel> fuels, Map<GTUtility.FluidId, BigInteger> litres) {
        return best(fuels, litres, true);
    }

    private static synchronized List<CompoundRecipe> legacyV2Catalog(List<ChemicalFuel> fuels) {
        return catalog(fuels).isEmpty() ? ImmutableList.of() : cachedLegacyV2Catalog;
    }

    private static Match best(List<ChemicalFuel> fuels, Map<GTUtility.FluidId, BigInteger> litres, boolean legacyV2) {
        BigInteger total = BigInteger.ZERO, heat = BigInteger.ZERO;
        Map<GTUtility.FluidId, ChemicalFuel> prices = prices(fuels);
        for (Map.Entry<GTUtility.FluidId, BigInteger> entry : litres.entrySet()) {
            if (entry.getValue()
                .signum() < 0) throw new IllegalArgumentException("Negative input volume");
            total = total.add(entry.getValue());
            ChemicalFuel fuel = prices.get(entry.getKey());
            if (fuel != null) heat = heat.add(
                entry.getValue()
                    .multiply(fuel.euPerL));
        }
        if (total.signum() == 0 || heat.signum() == 0) return null;
        Match best = null;
        for (CompoundRecipe recipe : legacyV2 ? legacyV2Catalog(fuels) : catalog(fuels)) {
            BigInteger[] amounts = new BigInteger[recipe.components.size()];
            int[] ratios = new int[amounts.length];
            for (int i = 0; i < amounts.length; i++) {
                Component part = recipe.components.get(i);
                amounts[i] = litres.getOrDefault(GTUtility.FluidId.create(part.fuel.fluid), BigInteger.ZERO);
                ratios[i] = part.ratio;
            }
            CriticalChemicalMath.Evaluation value = CriticalChemicalMath
                .evaluate(amounts, ratios, total, heat, recipe.peakMultiplier);
            if (!value.eligible) continue;
            if (best == null || value.generatedNumerator.multiply(best.evaluation.generatedDenominator)
                .compareTo(best.evaluation.generatedNumerator.multiply(value.generatedDenominator)) > 0)
                best = new Match(recipe, value, total, heat);
        }
        return best;
    }

    private static Map<GTUtility.FluidId, ChemicalFuel> prices(List<ChemicalFuel> fuels) {
        Map<GTUtility.FluidId, ChemicalFuel> result = new LinkedHashMap<>();
        for (ChemicalFuel fuel : fuels) result.putIfAbsent(GTUtility.FluidId.create(fuel.fluid), fuel);
        return result;
    }

    private static FluidStack fluid(Materials material) {
        if (material == null) return null;
        FluidStack fluid = material.getFluid(1);
        return fluid != null ? fluid : material.getGas(1);
    }

    private static FluidStack stack(Fluid fluid) {
        return fluid == null ? null : new FluidStack(fluid, 1);
    }

    private static ChemicalFuel auxiliary(String name, Map<GTUtility.FluidId, ChemicalFuel> prices) {
        return auxiliary(stack(FluidRegistry.getFluid(name)), prices);
    }

    private static ChemicalFuel auxiliary(FluidStack fluid, Map<GTUtility.FluidId, ChemicalFuel> prices) {
        if (fluid == null) return null;
        return prices.getOrDefault(GTUtility.FluidId.create(fluid), new ChemicalFuel(fluid, BigInteger.ZERO, -1));
    }

    private static Component component(ChemicalFuel fuel, int ratio) {
        return fuel == null ? null : new Component(fuel, ratio);
    }

    private static void addLegacy(List<CompoundRecipe> target, List<CompoundRecipe> legacy, String id, int peak,
        int legacyPeak, Component... components) {
        add(target, id, peak, components);
        add(legacy, id, legacyPeak, components);
    }

    private static void add(List<CompoundRecipe> target, String id, int peak, Component... components) {
        for (Component component : components) if (component == null) return;
        for (int i = 0; i < components.length; i++) for (int j = i + 1; j < components.length; j++)
            if (components[i].fuel.fluid.isFluidEqual(components[j].fuel.fluid)) return;
        target.add(new CompoundRecipe(id, peak, components));
    }

    public static final class Match {

        public final CompoundRecipe recipe;
        public final CriticalChemicalMath.Evaluation evaluation;
        public final BigInteger totalLitres, rawHeat;

        private Match(CompoundRecipe recipe, CriticalChemicalMath.Evaluation evaluation, BigInteger totalLitres,
            BigInteger rawHeat) {
            this.recipe = recipe;
            this.evaluation = evaluation;
            this.totalLitres = totalLitres;
            this.rawHeat = rawHeat;
        }
    }
}
