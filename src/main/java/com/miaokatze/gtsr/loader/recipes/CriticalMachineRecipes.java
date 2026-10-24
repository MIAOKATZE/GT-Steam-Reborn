package com.miaokatze.gtsr.loader.recipes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.CriticalChemicalMath;
import com.miaokatze.gtsr.common.critical.CriticalCompiledLayout;
import com.miaokatze.gtsr.common.critical.CriticalConfiguration;
import com.miaokatze.gtsr.common.critical.CriticalConstructionEconomy;
import com.miaokatze.gtsr.common.critical.CriticalEnergyMath;
import com.miaokatze.gtsr.common.critical.CriticalEnergyRuntime;
import com.miaokatze.gtsr.common.critical.CriticalFoundationMaterials;
import com.miaokatze.gtsr.common.critical.CriticalGTRecipes;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalLayoutDefinition;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalStellarPolicy;
import com.miaokatze.gtsr.common.critical.CriticalTier;
import com.miaokatze.gtsr.common.items.IrradiatedDust;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
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
            CriticalRecipeMaps
                .addDisplayRecipe(CriticalMachineKind.SUN, display, "sun", BigInteger.valueOf(original.mEUt));
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
            constructionPages(
                kind,
                0,
                controller,
                CriticalFoundationMaterials.items(),
                new FluidStack[] { CriticalFoundationMaterials.fluid(1_000_000) });
            for (int stage = 1; stage <= 5; stage++) {
                List<CriticalGeometry.Voxel> group = new ArrayList<>();
                for (CriticalGeometry.Voxel voxel : paid) if (economy.stage(voxel) == stage) group.add(voxel);
                constructionPages(
                    kind,
                    stage,
                    controller,
                    economy.costs(group, 0, group.size()),
                    economy.fluidCosts(group, 0, group.size()));
            }
        }
    }

    private static void constructionPages(CriticalMachineKind kind, int stage, ItemStack controller, ItemStack[] costs,
        FluidStack[] fluids) {
        if (costs.length > CriticalRecipeMaps.CONSTRUCTION_PAGE_SIZE)
            throw new IllegalStateException("Construction phase exceeds native grid: " + kind + "/" + stage);
        GTRecipe recipe = GTValues.RA.stdBuilder()
            .itemInputsUnsafe(costs)
            .itemOutputs(controller.copy())
            .fluidInputs(fluids)
            .duration(400)
            .eut(0)
            .build()
            .orElseThrow(() -> new IllegalStateException("Invalid construction catalogue " + kind));
        CriticalRecipeMaps.addConstruction(recipe, kind, stage, 0, 1);
    }

    private static void registerStellarLifecycle() {
        GTRecipe ignition = GTValues.RA.stdBuilder()
            .fluidInputs(Materials.Helium3.getGas(CriticalStellarPolicy.IGNITION_HELIUM3_L))
            .fluidOutputs(Materials.Helium.getPlasma(CriticalStellarPolicy.IGNITION_HELIUM3_L / 2))
            .duration(1)
            .eut(0)
            .build()
            .orElseThrow(() -> new IllegalStateException("Invalid stellar ignition view"));
        CriticalRecipeMaps.addDisplayRecipe(
            CriticalMachineKind.SUN,
            ignition,
            "stellar_ignition",
            BigInteger.valueOf(CriticalStellarPolicy.IGNITION_EU));
        // Two real paid ticks express the half-litre output using native integer fluid stacks.
        GTRecipe sustain = GTValues.RA.stdBuilder()
            .fluidInputs(Materials.Helium3.getGas(2 * CriticalStellarPolicy.SUSTAIN_HELIUM3_L_PER_TICK))
            .fluidOutputs(Materials.Helium.getPlasma(CriticalStellarPolicy.SUSTAIN_HELIUM3_L_PER_TICK))
            .duration(2)
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
        for (int mode = 0; mode < 6; mode++) {
            FluidStack steam = CriticalEnergyRuntime.steam(mode);
            if (steam == null) continue;
            steam.amount = mode < 3 ? 160 : 4;
            add(
                CriticalMachineKind.SOLAR,
                "solar_water",
                new ItemStack[0],
                new ItemStack[0],
                new FluidStack[] { GTModHandler.getDistilledWater(mode < 3 ? 1 : 25) },
                new FluidStack[] { steam },
                BigInteger.ZERO);
        }
        add(
            CriticalMachineKind.SOLAR,
            "solar_salt",
            new ItemStack[0],
            new ItemStack[0],
            new FluidStack[] { MaterialMisc.SOLAR_SALT_COLD.getFluidStack(1) },
            new FluidStack[] { MaterialMisc.SOLAR_SALT_HOT.getFluidStack(1) },
            BigInteger.ZERO);
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
        List<CriticalGTRecipes.ChemicalFuel> fuels = CriticalGTRecipes.chemicalFuels();
        for (CriticalGTRecipes.ChemicalFuel fuel : fuels) {
            FluidStack fluid = fuel.fluid.copy();
            fluid.amount = 1;
            GTRecipe recipe = GTValues.RA.stdBuilder()
                .fluidInputs(fluid)
                .duration(CriticalEnergyMath.CYCLE_TICKS)
                .eut(0)
                .build()
                .orElseThrow(() -> new IllegalStateException("Invalid chemical catalogue fuel"));
            CriticalRecipeMaps
                .addDisplayRecipe(CriticalMachineKind.BATTERY, recipe, "battery", fuel.euPerL, fuel.spectrum);
        }
        registerChemicalMixtures(fuels);
    }

    /** Smallest native heat values give stable, integer-litre examples without inventing fuel tokens. */
    private static void registerChemicalMixtures(List<CriticalGTRecipes.ChemicalFuel> fuels) {
        CriticalGTRecipes.ChemicalFuel[] chosen = new CriticalGTRecipes.ChemicalFuel[3];
        for (CriticalGTRecipes.ChemicalFuel fuel : fuels) {
            CriticalGTRecipes.ChemicalFuel previous = chosen[fuel.spectrum];
            if (previous == null || fuel.euPerL.compareTo(previous.euPerL) < 0
                || fuel.euPerL.equals(previous.euPerL) && fuel.fluid.getFluid()
                    .getName()
                    .compareTo(
                        previous.fluid.getFluid()
                            .getName())
                    < 0)
                chosen[fuel.spectrum] = fuel;
        }
        if (chosen[0] == null || chosen[1] == null || chosen[2] == null) return;
        int[][] weights = { { 1, 1, 0 }, { 1, 1, 1 }, { 1, 1, 4 } };
        for (int window = 0; window < weights.length; window++) {
            BigInteger unit = BigInteger.ONE;
            for (int i = 0; i < 3; i++) {
                if (weights[window][i] == 0) continue;
                BigInteger divisor = chosen[i].euPerL
                    .divide(chosen[i].euPerL.gcd(BigInteger.valueOf(weights[window][i])));
                unit = unit.divide(unit.gcd(divisor))
                    .multiply(divisor);
            }
            BigInteger[] heat = new BigInteger[3];
            List<FluidStack> inputs = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                heat[i] = unit.multiply(BigInteger.valueOf(weights[window][i]));
                if (heat[i].signum() == 0) continue;
                FluidStack input = chosen[i].fluid.copy();
                input.amount = heat[i].divide(chosen[i].euPerL)
                    .intValueExact();
                inputs.add(input);
            }
            BigInteger total = CriticalChemicalMath.sum(heat);
            if (total.compareTo(CriticalChemicalMath.cap(window)) > 0)
                throw new IllegalStateException("Native chemical example exceeds window cap: " + window + "/" + total);
            if (!java.util.Arrays
                .equals(heat, CriticalChemicalMath.match(window, heat, CriticalChemicalMath.cap(window))))
                throw new IllegalStateException("Chemical example must match real paid-heat runtime");
            GTRecipe recipe = GTValues.RA.stdBuilder()
                .fluidInputs(inputs.toArray(new FluidStack[0]))
                .duration(CriticalEnergyMath.CYCLE_TICKS)
                .eut(0)
                .build()
                .orElseThrow(() -> new IllegalStateException("Invalid chemical mixture view"));
            CriticalRecipeMaps.addDisplayRecipe(
                CriticalMachineKind.BATTERY,
                recipe,
                "chemical_window_" + window,
                total.multiply(BigInteger.valueOf(CriticalChemicalMath.multiplier(window))),
                window);
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
