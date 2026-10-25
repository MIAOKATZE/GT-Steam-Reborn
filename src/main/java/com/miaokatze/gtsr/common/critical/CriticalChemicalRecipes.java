package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
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

/** Fixed GT205 identities: 128 distinct blends in each circuit group from two to nine fluids. */
public final class CriticalChemicalRecipes {

    private static Map<GTUtility.FluidId, BigInteger> cachedPrices = ImmutableMap.of();
    private static List<CompoundRecipe> cachedCatalog = ImmutableList.of();
    private static List<CompoundRecipe> cachedLegacyV4Catalog = ImmutableList.of();
    private static List<CompoundRecipe> cachedLegacyV3Catalog = ImmutableList.of();
    private static List<CompoundRecipe> cachedLegacyV2Catalog = ImmutableList.of();
    private static List<GTUtility.FluidId> cachedIdentities = ImmutableList.of();
    private static Fluid cachedPromoter, cachedOzone;
    private static volatile Map<String, CompoundRecipe> displayCatalog = ImmutableMap.of();
    private static volatile Map<String, CompoundRecipe> legacyDisplayCatalog = ImmutableMap.of();

    private CriticalChemicalRecipes() {}

    public static synchronized List<CompoundRecipe> catalog(List<ChemicalFuel> fuels) {
        FluidStack[] identities = { fluid(Materials.Diesel), fluid(Materials.LightFuel), fluid(Materials.HeavyFuel),
            fluid(Materials.NitroFuel), fluid(Materials.GasolineRegular), fluid(Materials.GasolinePremium),
            fluid(Materials.Ethanol), fluid(Materials.Methanol), fluid(Materials.Hydrogen), fluid(Materials.Methane),
            fluid(Materials.Benzene), fluid(Materials.NefariousGas), stack(GTPPFluids.RP1RocketFuel),
            stack(GTPPFluids.DenseHydrazineFuelMixture), stack(GTPPFluids.CN3H7O3RocketFuel),
            stack(GTPPFluids.H8N4C2O4RocketFuel), fluid(Materials.Ethylene), fluid(Materials.Propene),
            fluid(Materials.Butene), fluid(Materials.Butadiene), fluid(Materials.Ethane), fluid(Materials.Propane),
            fluid(Materials.Butane), fluid(Materials.Toluene) };
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
        if (signature.equals(cachedPrices) && identitySignature.equals(cachedIdentities)
            && promoterFluid == cachedPromoter
            && ozoneFluid == cachedOzone) return cachedCatalog;
        for (int i = 0; i < identities.length; i++) {
            if (identities[i] == null) continue;
            ChemicalFuel fuel = prices.get(GTUtility.FluidId.create(identities[i]));
            if (fuel != null && fuel.euPerL.signum() > 0) selected[i] = fuel;
        }
        List<CompoundRecipe> result = new ArrayList<>(512);
        List<CompoundRecipe> legacy = new ArrayList<>(256);
        for (int i = 0; i < 16; i++) for (int j = i + 1; j < 16; j++) {
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
        List<CompoundRecipe> legacyV3 = ImmutableList.copyOf(result);
        List<CompoundRecipe> legacyV4 = ImmutableList.copyOf(buildLegacyV4Catalog(selected, promoter));
        result = buildCircuitCatalog(selected, promoter, ozone);
        List<CompoundRecipe> immutable = ImmutableList.copyOf(result);
        cachedLegacyV2Catalog = ImmutableList.copyOf(legacy);
        cachedLegacyV3Catalog = legacyV3;
        cachedLegacyV4Catalog = legacyV4;
        if (legacyV3.size() + legacyV4.size() >= legacyDisplayCatalog.size()) {
            Map<String, CompoundRecipe> labels = new LinkedHashMap<>();
            for (CompoundRecipe recipe : legacyV3) labels.put(recipe.id, recipe);
            for (CompoundRecipe recipe : legacyV4) labels.put(recipe.id, recipe);
            legacyDisplayCatalog = ImmutableMap.copyOf(labels);
        }
        // All four pricing catalogues belong to this exact signature, including empty outcomes.
        // Native identity/price registration changes invalidate an early empty registration result.
        cachedPrices = ImmutableMap.copyOf(signature);
        cachedCatalog = immutable;
        cachedIdentities = identitySignature;
        cachedPromoter = promoterFluid;
        cachedOzone = ozoneFluid;
        if (immutable.size() >= displayCatalog.size()) {
            Map<String, CompoundRecipe> index = new LinkedHashMap<>();
            for (CompoundRecipe recipe : immutable) index.put(recipe.id, recipe);
            displayCatalog = ImmutableMap.copyOf(index);
        }
        return immutable;
    }

    /** Frozen schema-3 complexity bands. */
    private static int tierPeak(int components, int variant) {
        return components == 2 ? 2 + variant % 19 : (components - 2) * 20 + 1 + variant % 20;
    }

    /** Lookup for client labels; callers localize the component FluidStacks using the active language. */
    public static CompoundRecipe byId(String id) {
        if (displayCatalog.isEmpty()) catalog(CriticalGTRecipes.chemicalFuels());
        CompoundRecipe current = displayCatalog.get(id);
        return current != null ? current : legacyDisplayCatalog.get(id);
    }

    /** Compatibility bridge for schema-3 receipts; new captures must supply a circuit. */
    public static Match best(List<ChemicalFuel> fuels, Map<GTUtility.FluidId, BigInteger> litres) {
        return bestLegacyV3(fuels, litres);
    }

    /** A selected group never falls back to a less complex reaction. Exact ties keep catalogue order. */
    public static Match best(List<ChemicalFuel> fuels, Map<GTUtility.FluidId, BigInteger> litres, int circuit) {
        if (circuit < 2 || circuit > 9) return null;
        return select(fuels, litres, catalog(fuels), circuit, null);
    }

    /** Validate an already-frozen identity without selecting a replacement reaction. */
    public static Match matchExact(List<ChemicalFuel> fuels, Map<GTUtility.FluidId, BigInteger> litres, int circuit,
        String id) {
        if (circuit < 2 || circuit > 9 || id == null || id.isEmpty()) return null;
        return select(fuels, litres, catalog(fuels), circuit, id);
    }

    /** Frozen schema-4 identities, ratios, selectors and reward bands; never reinterpret an old ID. */
    public static Match matchExactLegacyV4(List<ChemicalFuel> fuels, Map<GTUtility.FluidId, BigInteger> litres,
        int circuit, String id) {
        if (circuit < 2 || circuit > 9 || id == null || id.isEmpty()) return null;
        return select(fuels, litres, catalogLegacyV4(fuels), circuit, id);
    }

    public static synchronized List<CompoundRecipe> catalogLegacyV4(List<ChemicalFuel> fuels) {
        catalog(fuels);
        return cachedLegacyV4Catalog;
    }

    /** Only for already-captured schema-2 payments; its prices never enter the current NEI catalogue. */
    public static Match bestLegacyV2(List<ChemicalFuel> fuels, Map<GTUtility.FluidId, BigInteger> litres) {
        return select(fuels, litres, catalogLegacyV2(fuels), 0, null);
    }

    /** Frozen 512-entry, two-to-six-fluid schema-3 catalogue and complexity rewards. */
    public static Match bestLegacyV3(List<ChemicalFuel> fuels, Map<GTUtility.FluidId, BigInteger> litres) {
        return select(fuels, litres, catalogLegacyV3(fuels), 0, null);
    }

    public static synchronized List<CompoundRecipe> catalogLegacyV2(List<ChemicalFuel> fuels) {
        catalog(fuels);
        return cachedLegacyV2Catalog;
    }

    public static synchronized List<CompoundRecipe> catalogLegacyV3(List<ChemicalFuel> fuels) {
        catalog(fuels);
        return cachedLegacyV3Catalog;
    }

    private static Match select(List<ChemicalFuel> fuels, Map<GTUtility.FluidId, BigInteger> litres,
        List<CompoundRecipe> recipes, int circuit, String exactId) {
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
        for (CompoundRecipe recipe : recipes) {
            if (circuit != 0 && recipe.selectorCircuit != circuit) continue;
            if (exactId != null && !recipe.id.equals(exactId)) continue;
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

    private static List<CompoundRecipe> buildLegacyV4Catalog(ChemicalFuel[] selected, ChemicalFuel promoter) {
        // Each reaction has just two ordinary feedstocks. Additional slots require more industrial products:
        // regular gasoline -> premium gasoline -> RP-1 -> dense hydrazine -> CN3H7O3 -> H8N4C2O4 -> promoter.
        // The four rocket blends require chemical-plant casing tiers 3 through 6; they are not each
        // other's ingredients. The final promoter adds a separate MV synthesis branch, not a voltage tier.
        ChemicalFuel[] base = { selected[0], selected[1], selected[2], selected[3], selected[6], selected[7],
            selected[8], selected[9], selected[10], selected[16], selected[17], selected[18], selected[19],
            selected[20], selected[21], selected[22], selected[23] };
        ChemicalFuel[] chain = { selected[4], selected[5], selected[12], selected[13], selected[14], selected[15],
            promoter };
        List<CompoundRecipe> result = new ArrayList<>(1024);
        for (int size = 2; size <= 9; size++) {
            int variant = 0;
            for (int first = 0; first < base.length; first++)
                for (int second = first + 1; second < base.length; second++) {
                    if (variant >= 128) continue;
                    Component[] parts = new Component[size];
                    parts[0] = component(base[first], 4 + first % 3);
                    parts[1] = component(base[second], 2 + second % 3);
                    for (int i = 2; i < size; i++) parts[i] = component(chain[i - 2], i < 5 ? 3 : 1);
                    add(result, "compound." + size + "." + first + "." + second, circuitPeak(size, variant), parts);
                    variant++;
                }
        }
        return result;
    }

    /**
     * One acquisition gate per group, two only for the final promoted reaction. Remaining materials are
     * genuine subsets of all eligible feedstocks, including lower-tier industrial products; a higher
     * circuit never accumulates a fixed tail of every preceding gate.
     */
    private static List<CompoundRecipe> buildCircuitCatalog(ChemicalFuel[] selected, ChemicalFuel promoter,
        ChemicalFuel ozone) {
        ChemicalFuel[] materials = new ChemicalFuel[26];
        System.arraycopy(selected, 0, materials, 0, selected.length);
        materials[24] = promoter;
        materials[25] = ozone;
        int[] base = { 0, 1, 2, 3, 6, 7, 8, 9, 10, 16, 17, 18, 19, 20, 21, 22, 23 };
        int[] gates = { 4, 5, 12, 13, 14, 15 };
        List<CompoundRecipe> result = new ArrayList<>(1024);
        for (int size = 2; size <= 9; size++) {
            int gateCount = size == 2 ? 0 : size == 9 ? 2 : 1;
            List<Integer> pool = new ArrayList<>();
            for (int index : base) pool.add(index);
            for (int i = 0; i < Math.min(size - 3, gates.length - 1); i++) pool.add(gates[i]);
            if (size >= 4) pool.add(25);
            int variableCount = size - gateCount;
            long combinations = choose(pool.size(), variableCount);
            for (int variant = 0; variant < 128; variant++) {
                // Equal rank strata cover the entire combinatorial space, including its far end.
                // A fixed in-stratum offset avoids both a lexicographic prefix and order/random-seed drift.
                long lower = combinations * variant / 128;
                long upper = combinations * (variant + 1) / 128;
                long rank = lower + (variant == 0 ? 0 : (variant * 37L + size * 17L) % (upper - lower));
                int[] subset = unrank(pool.size(), variableCount, rank);
                int[] identities = new int[size];
                int cursor = 0;
                if (gateCount > 0) identities[cursor++] = gates[Math.min(size - 3, gates.length - 1)];
                if (gateCount > 1) identities[cursor++] = 24;
                for (int index : subset) identities[cursor++] = pool.get(index);
                Component[] parts = new Component[size];
                for (int i = 0; i < identities.length; i++) {
                    int identity = identities[i];
                    parts[i] = component(materials[identity], identity >= 24 ? 1 : 2 + identity % 4);
                }
                // Stable identities encode the actual set, never a presentation slot or sample ordinal.
                Arrays.sort(identities);
                StringBuilder id = new StringBuilder("reaction.").append(size);
                for (int identity : identities) id.append('.')
                    .append(identity);
                add(result, id.toString(), circuitPeak(size, variant), parts);
            }
        }
        return result;
    }

    private static long choose(int n, int k) {
        if (k < 0 || k > n) return 0;
        long result = 1;
        for (int i = 1; i <= k; i++) result = result * (n - k + i) / i;
        return result;
    }

    /** Lexicographic combinadic unranking; ranks themselves are sampled across the full range. */
    private static int[] unrank(int poolSize, int count, long rank) {
        int[] result = new int[count];
        int first = 0;
        for (int slot = 0; slot < count; slot++) {
            for (int candidate = first; candidate < poolSize; candidate++) {
                long block = choose(poolSize - candidate - 1, count - slot - 1);
                if (rank < block) {
                    result[slot] = candidate;
                    first = candidate + 1;
                    break;
                }
                rank -= block;
            }
        }
        return result;
    }

    private static int circuitPeak(int size, int variant) {
        int[] minimum = { 2, 13, 25, 37, 49, 61, 73, 87 };
        int[] maximum = { 12, 24, 36, 48, 60, 72, 86, 100 };
        return minimum[size - 2] + variant % (maximum[size - 2] - minimum[size - 2] + 1);
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
