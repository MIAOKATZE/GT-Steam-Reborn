package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.compat.GTVersionCompat;
import com.miaokatze.gtsr.common.critical.recipe.RecipeMatch;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;

/**
 * Native GT matching/consumption on snapshots, followed by exact real-hatch funding in the journal.
 * The input delta preserves ore alternatives, non-consumed catalysts and native chance consumption.
 * Output rolls are made once, only after a worst-case capacity reservation has selected the batch.
 */
public final class CriticalGTRecipes {

    private CriticalGTRecipes() {}

    public static final class ChemicalFuel {

        public final FluidStack fluid;
        public final BigInteger euPerL;
        /** First native map wins: diesel=0, gas=1, rocket=2. */
        public final int spectrum;

        private ChemicalFuel(FluidStack fluid, BigInteger euPerL, int spectrum) {
            this.fluid = fluid.copy();
            this.fluid.amount = 1;
            this.euPerL = euPerL;
            this.spectrum = spectrum;
        }
    }

    /** Same maps, fuel-container decoding and rocket bonus as GT205's UCFE, without its promoter. */
    public static List<ChemicalFuel> chemicalFuels() {
        Map<GTUtility.FluidId, ChemicalFuel> fuels = new LinkedHashMap<>();
        addChemicalFuels(fuels, RecipeMaps.dieselFuels, 1, 0);
        addChemicalFuels(fuels, RecipeMaps.gasTurbineFuels, 1, 1);
        addChemicalFuels(fuels, GTVersionCompat.requireGppRecipeMap("rocketFuels"), 3, 2);
        return new ArrayList<>(fuels.values());
    }

    private static void addChemicalFuels(Map<GTUtility.FluidId, ChemicalFuel> fuels, RecipeMap<?> map, int bonus,
        int spectrum) {
        for (GTRecipe recipe : map.getAllRecipes()) {
            if (!recipe.mEnabled || recipe.mFakeRecipe || recipe.mSpecialValue <= 0) continue;
            FluidStack fluid = recipe.mInputs != null && recipe.mInputs.length > 0
                ? GTUtility.getFluidForFilledItem(recipe.mInputs[0], true)
                : recipe.mFluidInputs != null && recipe.mFluidInputs.length > 0 ? recipe.mFluidInputs[0] : null;
            if (fluid != null) fuels.putIfAbsent(
                GTUtility.FluidId.create(fluid),
                new ChemicalFuel(
                    fluid,
                    BigInteger.valueOf(recipe.mSpecialValue)
                        .multiply(BigInteger.valueOf(bonus)),
                    spectrum));
        }
    }

    /** RC2 MkV capability with independent stellar pricing, real power and worst-case output gates. */
    public static RecipeMatch matchFusion(CriticalMachineState state, MTECriticalMachine host) {
        if (host == null || !CriticalStellarRuntime.ready(state.getStellarRuntimeData())) return null;
        ItemStack[] items = host.criticalRecipeItemsSnapshot();
        FluidStack[] fluids = concat(host.criticalPhysicalFluidsSnapshot(), host.criticalMEFluidsSnapshot());
        for (GTRecipe recipe : RecipeMaps.fusionRecipes.getAllRecipes()) {
            long threshold = recipe.getMetadataOrDefault(gregtech.api.util.GTRecipeConstants.FUSION_THRESHOLD, -1L);
            if (!recipe.mEnabled || recipe.mFakeRecipe
                || recipe.mDuration <= 0
                || !CriticalStellarPolicy.nativeCapability(threshold, recipe.mEUt, gregtech.api.enums.GTValues.V[10]))
                continue;
            if (!knownFusionContract(recipe)) continue;
            int powerParallel = CriticalStellarPolicy.powerLimitedParallel(
                CriticalStellarPolicy.processingPowerBudget(
                    host.criticalRecipePower(),
                    MTECriticalMachine.criticalEnergyTickCap(),
                    state.getStellarRuntimeData()
                        .getBoolean("holdPaid")),
                CriticalStellarPolicy.MAX_PARALLEL);
            if (powerParallel <= 0) continue;
            ParallelHelper limit = helper(
                recipe,
                copies(items),
                copies(fluids),
                powerParallel,
                Long.MAX_VALUE,
                false,
                false);
            int low = 0, high = limit.getCurrentParallel();
            while (low < high) {
                int middle = low + (high - low + 1) / 2;
                if (fits(state, recipe, middle)) low = middle;
                else high = middle - 1;
            }
            if (low == 0) continue;
            ItemStack[] remainingItems = copies(items);
            FluidStack[] remainingFluids = copies(fluids);
            ParallelHelper execution = helper(recipe, remainingItems, remainingFluids, low, Long.MAX_VALUE, true, true);
            int parallel = execution.getCurrentParallel();
            if (parallel <= 0) continue;
            return new RecipeMatch(
                "native_fusion",
                itemDelta(items, remainingItems),
                fluidDelta(fluids, remainingFluids),
                copies(execution.getItemOutputs()),
                copies(execution.getFluidOutputs()),
                CriticalStellarPolicy.duration(recipe.mDuration),
                CriticalStellarPolicy.runEuPerTick(parallel),
                0,
                fusionFingerprint(recipe, threshold),
                recipe.mDuration,
                recipe.mEUt,
                threshold,
                parallel,
                CriticalStellarPolicy.VERSION);
        }
        return null;
    }

    private static String fusionFingerprint(GTRecipe recipe, long threshold) {
        net.minecraft.nbt.NBTTagCompound data = new net.minecraft.nbt.NBTTagCompound();
        data.setInteger("duration", recipe.mDuration);
        data.setInteger("eut", recipe.mEUt);
        data.setLong("threshold", threshold);
        data.setBoolean("nbtSensitive", recipe.isNBTSensitive);
        if (recipe.mFluidInputChances != null) data.setIntArray("inputChances", recipe.mFluidInputChances);
        if (recipe.mFluidOutputChances != null) data.setIntArray("outputChances", recipe.mFluidOutputChances);
        net.minecraft.nbt.NBTTagList inputs = new net.minecraft.nbt.NBTTagList();
        for (FluidStack fluid : recipe.mFluidInputs)
            if (fluid != null) inputs.appendTag(fluid.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
        data.setTag("inputs", inputs);
        net.minecraft.nbt.NBTTagList outputs = new net.minecraft.nbt.NBTTagList();
        for (FluidStack fluid : recipe.mFluidOutputs)
            if (fluid != null) outputs.appendTag(fluid.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
        data.setTag("outputs", outputs);
        // Canonical GT205 fusion recipes are fluid-only; reject future item/extra-metadata extensions separately.
        return java.util.UUID.nameUUIDFromBytes(canonicalNbt(data).getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .toString();
    }

    private static String canonicalNbt(net.minecraft.nbt.NBTBase tag) {
        if (tag instanceof net.minecraft.nbt.NBTTagCompound compound) {
            StringBuilder result = new StringBuilder("{");
            for (String key : new java.util.TreeSet<>(compound.func_150296_c())) result.append(key.length())
                .append(':')
                .append(key)
                .append(canonicalNbt(compound.getTag(key)));
            return result.append('}')
                .toString();
        }
        if (tag instanceof net.minecraft.nbt.NBTTagList original) {
            net.minecraft.nbt.NBTTagList list = (net.minecraft.nbt.NBTTagList) original.copy();
            StringBuilder result = new StringBuilder("[");
            while (list.tagCount() > 0) result.append(canonicalNbt(list.removeTag(0)));
            return result.append(']')
                .toString();
        }
        if (tag instanceof net.minecraft.nbt.NBTTagByteArray bytes)
            return "B" + java.util.Arrays.toString(bytes.func_150292_c());
        if (tag instanceof net.minecraft.nbt.NBTTagIntArray ints)
            return "I" + java.util.Arrays.toString(ints.func_150302_c());
        String value = tag.toString();
        return tag.getId() + ":" + value.length() + ":" + value;
    }

    private static boolean knownFusionContract(GTRecipe recipe) {
        if (recipe.mInputs.length != 0 || recipe.mOutputs.length != 0
            || recipe.mSpecialItems != null
            || recipe.mNeedsEmptyOutput) return false;
        for (java.util.Map.Entry<gregtech.api.recipe.RecipeMetadataKey<?>, Object> entry : recipe.getMetadataStorage()
            .getEntries()) if (entry.getKey() != gregtech.api.util.GTRecipeConstants.FUSION_THRESHOLD) return false;
        return true;
    }

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
            if (map == GTVersionCompat.requireGppRecipeMap("quantumForceTransformerRecipes")) {
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
