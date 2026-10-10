package com.miaokatze.gtsr.loader.recipes;

import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.get;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;

import java.util.Collection;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import bartworks.system.material.WerkstoffLoader;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** The machine remains LuV; the portable beacon is HV; structure materials retain their normal upstream recipes. */
public final class SpacetimeMachineRecipes {

    private SpacetimeMachineRecipes() {}

    public static void register() {
        registerCraft(machineInputs(), GTSRItemList.SpacetimeCalibration.get(1), 73728, 48000, TierEU.RECIPE_LuV);
        registerCraft(beaconInputs(), GTSRItemList.SpacetimeAnchorBeacon.get(1), 1152, 1200, TierEU.RECIPE_HV);
        verifyRegistration();
    }

    private static ItemStack[] machineInputs() {
        return new ItemStack[] { ItemList.Casing_LuV.get(64), ItemList.Field_Generator_LuV.get(32),
            ItemList.Sensor_LuV.get(32), ItemList.QuantumStar.get(32), get(OrePrefixes.circuit, Materials.LuV, 64),
            get(OrePrefixes.plateDense, WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial(), 64),
            GTUtility.getIntegratedCircuit(24), GTSRItemList.CriticalSteamEntangledSingularity.get(16) };
    }

    private static ItemStack[] beaconInputs() {
        return new ItemStack[] { ItemList.Casing_HV.get(1), ItemList.Field_Generator_HV.get(1),
            ItemList.Sensor_HV.get(1), ItemList.Emitter_HV.get(1), ItemList.Circuit_Nanoprocessor.get(4),
            get(OrePrefixes.plate, Materials.StainlessSteel, 8) };
    }

    private static void registerCraft(ItemStack[] inputs, ItemStack output, int fluidAmount, int ticks, long eut) {
        requireStacks(inputs);
        requireStacks(new ItemStack[] { output });
        FluidStack solder = Materials.SolderingAlloy.getMolten(fluidAmount);
        if (solder == null || solder.getFluid() == null || solder.amount != fluidAmount)
            throw new IllegalStateException("Missing spacetime craft solder");
        Collection<GTRecipe> added = GTValues.RA.stdBuilder()
            .itemInputs(inputs)
            .itemOutputs(output)
            .fluidInputs(solder)
            .duration(ticks)
            .eut(eut)
            .addTo(assemblerRecipes);
        if (added == null || added.size() != 1)
            throw new IllegalStateException("Spacetime assembler craft registration failed: " + output);
    }

    /** Audit the live recipe map, including ingredient identity/counts, rather than a cached registration receipt. */
    public static void verifyRegistration() {
        verifyCraft(machineInputs(), GTSRItemList.SpacetimeCalibration.get(1), 73728, 48000, TierEU.RECIPE_LuV);
        verifyCraft(beaconInputs(), GTSRItemList.SpacetimeAnchorBeacon.get(1), 1152, 1200, TierEU.RECIPE_HV);
    }

    private static void verifyCraft(ItemStack[] inputs, ItemStack output, int fluidAmount, int ticks, long eut) {
        requireStacks(inputs);
        requireStacks(new ItemStack[] { output });
        GTRecipe found = null;
        int matches = 0;
        for (GTRecipe recipe : assemblerRecipes.getAllRecipes()) {
            if (recipe.mOutputs == null) continue;
            for (ItemStack actualOutput : recipe.mOutputs) {
                if (actualOutput != null && GTUtility.areStacksEqual(actualOutput, output)) {
                    found = recipe;
                    matches++;
                    break;
                }
            }
        }
        if (matches != 1 || found == null)
            throw new IllegalStateException("Spacetime craft must have exactly one live recipe: " + output);
        if (found.mOutputs.length != 1 || found.mOutputs[0].stackSize != 1
            || found.mDuration != ticks
            || found.mEUt != eut
            || found.mInputs == null
            || found.mInputs.length != inputs.length
            || found.mFluidInputs == null
            || found.mFluidInputs.length != 1
            || found.mFluidInputs[0] == null
            || found.mFluidInputs[0].amount != fluidAmount
            || !found.mFluidInputs[0].isFluidEqual(Materials.SolderingAlloy.getMolten(fluidAmount)))
            throw new IllegalStateException("Spacetime craft output, fluid, timing or voltage mismatch: " + output);
        boolean[] used = new boolean[found.mInputs.length];
        for (ItemStack input : inputs) {
            ItemStack expected = GTOreDictUnificator.get(true, input);
            boolean matched = false;
            for (int i = 0; i < found.mInputs.length; i++) {
                ItemStack actual = found.mInputs[i];
                if (!used[i] && actual != null
                    && actual.stackSize == input.stackSize
                    && actual.getItemDamage() == expected.getItemDamage()
                    && GTUtility.areStacksEqual(actual, expected)) {
                    used[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) throw new IllegalStateException("Spacetime craft ingredient mismatch: " + input);
        }
    }

    private static void requireStacks(ItemStack[] stacks) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItem() == null || (stack.stackSize <= 0 && !isConfigurationCircuit(stack)))
                throw new IllegalStateException("Missing spacetime craft item");
        }
    }

    private static boolean isConfigurationCircuit(ItemStack stack) {
        // GT uses a zero-sized integrated circuit as a non-consumed recipe selector.
        return stack.stackSize == 0 && stack.getItem() == ItemList.Circuit_Integrated.getItem()
            && stack.getItemDamage() == 24;
    }
}
