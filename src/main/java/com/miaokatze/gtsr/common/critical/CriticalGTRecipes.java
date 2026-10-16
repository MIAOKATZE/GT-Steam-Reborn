package com.miaokatze.gtsr.common.critical;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.critical.recipe.RecipeMatch;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;

/**
 * Native GT matching/consumption on snapshots, followed by exact real-hatch funding in the journal.
 * The input delta preserves ore alternatives, non-consumed catalysts and native chance consumption.
 * Output rolls are made once, only after a worst-case capacity reservation has selected the batch.
 */
public final class CriticalGTRecipes {

    private CriticalGTRecipes() {}

    // ParallelHelper always reads IVoidable's output-count limits, even when its own void
    // protection is disabled. Capacity is reserved jointly through CriticalEnergyPorts below;
    // this view merely prevents the helper from truncating a native recipe's output list.
    private static final gregtech.api.interfaces.tileentity.IVoidable OUTPUT_VIEW = new gregtech.api.interfaces.tileentity.IVoidable() {

        @Override
        public boolean supportsVoidProtection() {
            return false;
        }

        @Override
        public gregtech.api.enums.VoidingMode getVoidingMode() {
            return gregtech.api.enums.VoidingMode.VOID_NONE;
        }

        @Override
        public void setVoidingMode(gregtech.api.enums.VoidingMode mode) {}

        @Override
        public List<gregtech.api.interfaces.IOutputBus> getOutputBusses() {
            return java.util.Collections.emptyList();
        }

        @Override
        public List<gregtech.api.interfaces.IOutputHatch> getOutputHatches() {
            return java.util.Collections.emptyList();
        }

        @Override
        public boolean canDumpItemToME(List<gregtech.api.util.GTUtility.ItemId> outputs) {
            return false;
        }

        @Override
        public boolean canDumpFluidToME(List<gregtech.api.util.GTUtility.FluidId> outputs) {
            return false;
        }
    };

    public static RecipeMatch match(CriticalMachineState state, MTECriticalMachine host) {
        if (host == null) return null;
        String mode = CriticalProcessingModes.selected(state);
        ItemStack[] items = host.criticalRecipeItemsSnapshot();
        FluidStack[] fluids = concat(host.criticalPhysicalFluidsSnapshot(), host.criticalMEFluidsSnapshot());
        long voltage = host.criticalRecipeVoltage(), power = host.criticalRecipePower();
        if (CriticalProcessingModes.ASSEMBLY_LINE.equals(mode)) {
            for (GTRecipe.RecipeAssemblyLine original : host.criticalAssemblyRecipes()) {
                // A recipe is available only through the real data-access hatch's researched recipe list.
                GTRecipe recipe = new GTRecipe.GTRecipe_WithAlt(
                    false,
                    copies(original.mInputs),
                    new ItemStack[] { original.mOutput.copy() },
                    null,
                    null,
                    null,
                    null,
                    null,
                    copies(original.mFluidInputs),
                    new FluidStack[0],
                    original.mDuration,
                    original.mEUt,
                    0,
                    original.mOreDictAlt,
                    null);
                RecipeMatch result = prepare(state, recipe, items, fluids, voltage, power, mode);
                if (result != null) return result;
            }
            return null;
        }
        RecipeMap<?> map = CriticalProcessingModes.map(mode);
        if (map == null) return null;
        long recipeVoltage = voltage > Long.MAX_VALUE / Math.max(1, map.getAmperage()) ? Long.MAX_VALUE
            : voltage * Math.max(1, map.getAmperage());
        java.util.Iterator<GTRecipe> candidates = map.findRecipeQuery()
            .items(items)
            .fluids(fluids)
            .voltage(voltage)
            .filter(recipe -> recipe.mEnabled && !recipe.mFakeRecipe && recipe.mEUt >= 0)
            .findAll()
            .iterator();
        while (candidates.hasNext()) {
            GTRecipe recipe = candidates.next();
            int maxParallel = Integer.MAX_VALUE;
            if (map == gregtech.api.recipe.RecipeMaps.quantumForceTransformerRecipes) {
                maxParallel = CriticalAdvancedRecipes.qftCatalysts(recipe, items);
                if (maxParallel == 0) continue;
                recipe = CriticalAdvancedRecipes.qftBase(recipe);
                if (recipe == null) continue;
            }
            RecipeMatch result = prepare(state, recipe, items, fluids, recipeVoltage, power, mode, maxParallel);
            if (result != null) return result;
        }
        return null;
    }

    public static RecipeMatch prepare(CriticalMachineState state, GTRecipe recipe, ItemStack[] items,
        FluidStack[] fluids, long voltage, long power, String id) {
        return prepare(state, recipe, items, fluids, voltage, power, id, Integer.MAX_VALUE);
    }

    private static RecipeMatch prepare(CriticalMachineState state, GTRecipe recipe, ItemStack[] items,
        FluidStack[] fluids, long voltage, long power, String id, int maxParallel) {
        if (!recipe.mEnabled || recipe.mFakeRecipe || recipe.mEUt < 0 || recipe.mEUt > voltage || power < 0)
            return null;
        long undiscountedPower = power > Long.MAX_VALUE / 100L ? Long.MAX_VALUE : power * 100L;
        ParallelHelper limit = helper(
            recipe,
            copies(items),
            copies(fluids),
            maxParallel,
            undiscountedPower,
            false,
            false);
        int maximum = limit.getCurrentParallel();
        int low = 0, high = maximum;
        while (low < high) {
            int middle = low + (int) (((long) high - low + 1L) / 2L);
            if (fits(state, recipe, middle)) low = middle;
            else high = middle - 1;
        }
        if (low == 0) return null;
        ItemStack[] remainingItems = copies(items);
        FluidStack[] remainingFluids = copies(fluids);
        ParallelHelper execution = helper(recipe, remainingItems, remainingFluids, low, undiscountedPower, true, true);
        int actual = execution.getCurrentParallel();
        if (actual <= 0) return null;
        long rawEu = (long) recipe.mEUt * actual;
        long eut = rawEu / 100L + (rawEu % 100L == 0 ? 0 : 1);
        return new RecipeMatch(
            id,
            itemDelta(items, remainingItems),
            fluidDelta(fluids, remainingFluids),
            copies(execution.getItemOutputs()),
            copies(execution.getFluidOutputs()),
            400,
            eut,
            0);
    }

    private static ParallelHelper helper(GTRecipe recipe, ItemStack[] items, FluidStack[] fluids, int maximum,
        long power, boolean consume, boolean output) {
        return new ParallelHelper().setMachine(OUTPUT_VIEW, false, false)
            .setRecipe(recipe)
            .setItemInputs(items)
            .setFluidInputs(fluids)
            .setMaxParallel(maximum)
            .setAvailableEUt(power)
            .setEUtModifier(1D)
            .setCalculator(
                OverclockCalculator.ofNoOverclock(recipe)
                    .setEUt(Long.MAX_VALUE)
                    .setMaxTierSkips(0))
            .setConsumption(consume)
            .setOutputCalculation(output)
            .build();
    }

    private static boolean fits(CriticalMachineState state, GTRecipe recipe, int parallel) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack original : recipe.mOutputs) if (original != null) {
            long count = (long) original.stackSize * parallel;
            while (count > 0) {
                ItemStack stack = original.copy();
                stack.stackSize = (int) Math.min(count, Integer.MAX_VALUE);
                items.add(stack);
                count -= stack.stackSize;
            }
        }
        List<CriticalEnergyPorts.FluidAmount> fluids = new ArrayList<>();
        for (FluidStack original : recipe.mFluidOutputs) if (original != null) {
            long count = (long) original.amount * parallel;
            if (count > 0)
                fluids.add(new CriticalEnergyPorts.FluidAmount(original, java.math.BigInteger.valueOf(count)));
        }
        return state.canDeliverScaledBatch(items.toArray(new ItemStack[0]), fluids);
    }

    private static ItemStack[] itemDelta(ItemStack[] before, ItemStack[] after) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < before.length; i++) if (before[i] != null) {
            int amount = before[i].stackSize - (after[i] == null ? 0 : after[i].stackSize);
            if (amount > 0) {
                ItemStack cost = before[i].copy();
                cost.stackSize = amount;
                result.add(cost);
            }
        }
        return result.toArray(new ItemStack[0]);
    }

    private static FluidStack[] fluidDelta(FluidStack[] before, FluidStack[] after) {
        List<FluidStack> result = new ArrayList<>();
        for (int i = 0; i < before.length; i++) if (before[i] != null) {
            int amount = before[i].amount - (after[i] == null ? 0 : after[i].amount);
            if (amount > 0) {
                FluidStack cost = before[i].copy();
                cost.amount = amount;
                result.add(cost);
            }
        }
        return result.toArray(new FluidStack[0]);
    }

    private static ItemStack[] copies(ItemStack[] source) {
        if (source == null) return new ItemStack[0];
        return Arrays.stream(source)
            .map(stack -> stack == null ? null : stack.copy())
            .toArray(ItemStack[]::new);
    }

    private static FluidStack[] copies(FluidStack[] source) {
        if (source == null) return new FluidStack[0];
        return Arrays.stream(source)
            .map(stack -> stack == null ? null : stack.copy())
            .toArray(FluidStack[]::new);
    }

    private static FluidStack[] concat(FluidStack[] first, FluidStack[] second) {
        FluidStack[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
