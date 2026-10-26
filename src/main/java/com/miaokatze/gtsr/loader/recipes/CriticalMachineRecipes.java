package com.miaokatze.gtsr.loader.recipes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.CompoundRecipe;
import com.miaokatze.gtsr.common.critical.CriticalChemicalRecipes;
import com.miaokatze.gtsr.common.critical.CriticalCompiledLayout;
import com.miaokatze.gtsr.common.critical.CriticalConfiguration;
import com.miaokatze.gtsr.common.critical.CriticalConstructionEconomy;
import com.miaokatze.gtsr.common.critical.CriticalEnergyMath;
import com.miaokatze.gtsr.common.critical.CriticalEnergyRuntime;
import com.miaokatze.gtsr.common.critical.CriticalGTRecipes;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalLayoutDefinition;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalSolarPolicy;
import com.miaokatze.gtsr.common.critical.CriticalStellarPolicy;
import com.miaokatze.gtsr.common.critical.CriticalTier;
import com.miaokatze.gtsr.common.items.IrradiatedDust;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.GTUtility;
import gtPlusPlus.core.material.MaterialMisc;

/** Physical manufacturing recipes plus real work-recipe views. */
public final class CriticalMachineRecipes {

    private static boolean registered;

    private CriticalMachineRecipes() {}

    public static void register() {
        if (registered) return;
        CriticalManufacturingRecipes.register();
        registerConstruction();
        registerSolar();
        registerTurbine();
        registerChemicalFuels();
        registerStellarLifecycle();
        for (GTRecipe original : RecipeMaps.fusionRecipes.getAllRecipes()) {
            if (!supportedFusion(original)) continue;
            GTRecipe display = original.copy();
            display.mDuration = CriticalStellarPolicy.duration(original.mDuration);
            CriticalRecipeMaps.addFusionRecipe(display, original.mEUt, original.mDuration);
        }
        registered = true;
    }

    /** Mirror the runtime's public MkV gate and its closed native fluid-only metadata contract. */
    private static boolean supportedFusion(GTRecipe recipe) {
        if (!recipe.mEnabled || recipe.mFakeRecipe
            || recipe.mDuration <= 0
            || !CriticalStellarPolicy.nativeCapability(
                recipe.getMetadataOrDefault(GTRecipeConstants.FUSION_THRESHOLD, -1L),
                recipe.mEUt,
                GTValues.V[10])
            || recipe.mInputs.length != 0
            || recipe.mOutputs.length != 0
            || recipe.mSpecialItems != null
            || recipe.mNeedsEmptyOutput) return false;
        for (java.util.Map.Entry<gregtech.api.recipe.RecipeMetadataKey<?>, Object> entry : recipe.getMetadataStorage()
            .getEntries()) if (entry.getKey() != GTRecipeConstants.FUSION_THRESHOLD) return false;
        return true;
    }

    /** Freeze the exact canonical paid bill once at loadComplete, never in the NEI render loop. */
    private static void registerConstruction() {
        for (CriticalMachineKind kind : CriticalMachineKind.releasedValues()) {
            CriticalConfiguration config = new CriticalConfiguration(CriticalTier.T3, kind, 8, 0, 0);
            CriticalLayoutDefinition definition = CriticalLayoutDefinition.select(2, 31, false, config.tier, kind);
            CriticalCompiledLayout layout = CriticalCompiledLayout.get(definition, config);
            List<CriticalGeometry.Voxel> paid = new ArrayList<>();
            for (CriticalGeometry.Voxel voxel : layout.voxels())
                if (!CriticalLayoutDefinition.isLoom(voxel)) paid.add(voxel);
            CriticalConstructionEconomy economy = CriticalConstructionEconomy
                .freeze(config, paid, definition.cacheKey());
            ItemStack controller = switch (kind) {
                case SOLAR -> GTSRItemList.CriticalSolar.get(1);
                case TURBINE -> GTSRItemList.CriticalTurbine.get(1);
                case SUN -> GTSRItemList.CriticalSun.get(1);
                case BATTERY -> GTSRItemList.CriticalBattery.get(1);
                default -> throw new IllegalArgumentException("Unreleased construction " + kind);
            };
            for (int group = 0; group < 3; group++) {
                ItemStack[][] items = new ItemStack[3][];
                FluidStack[][] fluids = new FluidStack[3][];
                int[] steps = new int[] { 1, 1, 1 };
                List<ItemStack> allItems = new ArrayList<>();
                List<FluidStack> allFluids = new ArrayList<>();
                for (int row = 0; row < 3; row++) {
                    if (group == 0) {
                        items[row] = economy.itemsForDisplayRow(group, row, paid);
                        fluids[row] = economy.fluidsForDisplayRow(group, row, paid);
                    } else {
                        CriticalConstructionEconomy.DisplayRow profile = economy.displayRowProfile(group, row, paid);
                        steps[row] = profile.steps;
                        items[row] = profile.perStepItems();
                        fluids[row] = profile.perStepFluids();
                    }
                    java.util.Collections.addAll(allItems, items[row]);
                    java.util.Collections.addAll(allFluids, fluids[row]);
                }
                GTRecipe recipe = GTValues.RA.stdBuilder()
                    .itemInputsUnsafe(allItems.toArray(new ItemStack[0]))
                    .itemOutputs(controller.copy())
                    .fluidInputs(allFluids.toArray(new FluidStack[0]))
                    .duration(400)
                    .eut(0)
                    .build()
                    .orElseThrow(() -> new IllegalStateException("Invalid construction catalogue " + kind));
                CriticalRecipeMaps.addConstruction(recipe, kind, group, items, fluids, steps);
            }
        }
    }

    private static void registerStellarLifecycle() {
        GTRecipe ignition = GTValues.RA.stdBuilder()
            .fluidInputs(Materials.Helium3.getGas(CriticalStellarPolicy.IGNITION_HELIUM3_L))
            .fluidOutputs(Materials.Helium.getPlasma(CriticalStellarPolicy.IGNITION_HELIUM3_L / 2))
            .duration(CriticalStellarPolicy.DURATION_TICKS)
            .eut(0)
            .build()
            .orElseThrow(() -> new IllegalStateException("Invalid stellar ignition view"));
        CriticalRecipeMaps.addDisplayRecipe(
            CriticalMachineKind.SUN,
            ignition,
            "stellar_ignition",
            BigInteger.valueOf(CriticalStellarPolicy.IGNITION_EU));
        // A complete twenty-second phase preserves the real continuous helium/plasma conversion.
        GTRecipe sustain = GTValues.RA.stdBuilder()
            .fluidInputs(
                Materials.Helium3
                    .getGas(CriticalStellarPolicy.DURATION_TICKS * CriticalStellarPolicy.SUSTAIN_HELIUM3_L_PER_TICK))
            .fluidOutputs(
                Materials.Helium.getPlasma(
                    CriticalStellarPolicy.DURATION_TICKS / 2 * CriticalStellarPolicy.SUSTAIN_HELIUM3_L_PER_TICK))
            .duration(CriticalStellarPolicy.DURATION_TICKS)
            .eut(0)
            .build()
            .orElseThrow(() -> new IllegalStateException("Invalid stellar sustain view"));
        CriticalRecipeMaps.addDisplayRecipe(
            CriticalMachineKind.SUN,
            sustain,
            "stellar_sustain",
            BigInteger.valueOf(CriticalStellarPolicy.SUSTAIN_EUT));
    }

    private static void registerSolar() {
        BigInteger waterSampleEU = BigInteger.valueOf(150000);
        for (int mode = 0; mode < 6; mode++) {
            FluidStack steam = CriticalEnergyRuntime.steam(mode);
            if (steam == null) continue;
            // A small common energy sample fits native int fluid stacks in all six forms.
            steam.amount = CriticalSolarPolicy.steamLitres(CriticalSolarPolicy.waterHalfHeat(waterSampleEU), mode)
                .intValueExact();
            add(
                CriticalMachineKind.SOLAR,
                "solar_water",
                new ItemStack[0],
                new ItemStack[0],
                new FluidStack[] { GTModHandler.getDistilledWater(
                    CriticalSolarPolicy.waterLitres(BigInteger.valueOf(steam.amount), mode)
                        .intValueExact()) },
                new FluidStack[] { steam },
                waterSampleEU);
        }
        BigInteger saltSampleEU = waterSampleEU.multiply(BigInteger.valueOf(CriticalSolarPolicy.SALT_POWER_MULTIPLIER));
        int saltLitres = CriticalSolarPolicy.saltLitres(saltSampleEU)
            .intValueExact();
        add(
            CriticalMachineKind.SOLAR,
            "solar_salt",
            new ItemStack[0],
            new ItemStack[0],
            new FluidStack[] { MaterialMisc.SOLAR_SALT_COLD.getFluidStack(saltLitres) },
            new FluidStack[] { MaterialMisc.SOLAR_SALT_HOT.getFluidStack(saltLitres) },
            saltSampleEU);
        for (int i = 0; i < 2; i++) {
            ItemStack source = IrradiatedDust.source(i == 0);
            if (source == null) continue;
            add(
                CriticalMachineKind.SOLAR,
                "solar_dust",
                new ItemStack[] { source },
                new ItemStack[] {
                    (i == 0 ? GTSRItemList.IrradiatedMoonDust : GTSRItemList.IrradiatedStoneDust).get(1) },
                new FluidStack[0],
                new FluidStack[0],
                BigInteger.ZERO);
        }
    }

    private static void registerTurbine() {
        for (int mode = 0; mode < 6; mode++) {
            FluidStack steam = CriticalEnergyRuntime.steam(mode);
            if (steam == null) continue;
            steam.amount = mode < 3 ? 160 : 4;
            BigInteger baseEu = CriticalEnergyMath.steamHeatPerL(mode)
                .multiply(BigInteger.valueOf(steam.amount))
                .divide(BigInteger.valueOf(2));
            add(
                CriticalMachineKind.TURBINE,
                "turbine",
                new ItemStack[0],
                new ItemStack[0],
                new FluidStack[] { steam },
                new FluidStack[] { GTModHandler.getDistilledWater(mode < 3 ? 1 : 25) },
                baseEu);
        }
    }

    private static void registerChemicalFuels() {
        registerChemicalMixtures(CriticalGTRecipes.chemicalFuels());
    }

    private static void registerChemicalMixtures(List<CriticalGTRecipes.ChemicalFuel> fuels) {
        List<CompoundRecipe> catalog = new ArrayList<>(CriticalChemicalRecipes.catalog(fuels));
        catalog.sort(
            java.util.Comparator.comparingInt((CompoundRecipe entry) -> entry.peakMultiplier)
                .thenComparing(entry -> entry.id));
        for (CompoundRecipe compound : catalog) {
            List<FluidStack> inputs = new ArrayList<>();
            BigInteger inputHeat = BigInteger.ZERO;
            for (CompoundRecipe.Component component : compound.components) {
                FluidStack input = component.fuel.fluid.copy();
                input.amount = component.ratio;
                inputs.add(input);
                inputHeat = inputHeat.add(component.fuel.euPerL.multiply(BigInteger.valueOf(component.ratio)));
            }
            ItemStack circuit = GTUtility.getIntegratedCircuit(compound.selectorCircuit);
            circuit.stackSize = 0;
            GTRecipe recipe = GTValues.RA.stdBuilder()
                .itemInputs(circuit)
                .fluidInputs(inputs.toArray(new FluidStack[0]))
                .duration(CriticalEnergyMath.CYCLE_TICKS)
                .eut(0)
                .build()
                .orElseThrow(() -> new IllegalStateException("Invalid chemical compound view: " + compound.id));
            CriticalRecipeMaps.addChemicalRecipe(
                recipe,
                compound,
                inputHeat,
                inputHeat.multiply(BigInteger.valueOf(compound.peakMultiplier)));
        }
    }

    private static void add(CriticalMachineKind kind, String operation, ItemStack[] items, ItemStack[] outputs,
        FluidStack[] fluids, FluidStack[] fluidOutputs, BigInteger baseEu) {
        GTRecipe recipe = GTValues.RA.stdBuilder()
            .itemInputs(items)
            .itemOutputs(outputs)
            .fluidInputs(fluids)
            .fluidOutputs(fluidOutputs)
            .duration(CriticalEnergyMath.CYCLE_TICKS)
            .eut(0)
            .build()
            .orElseThrow(() -> new IllegalStateException("Invalid critical runtime view: " + operation));
        CriticalRecipeMaps.addDisplayRecipe(kind, recipe, operation, baseEu);
    }
}
