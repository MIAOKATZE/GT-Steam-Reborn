package com.miaokatze.gtsr.common.critical.recipe;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** A fully validated batch; all stacks are private copies of the recipe/snapshot. */
public final class RecipeMatch {

    public final String id;
    public final ItemStack[] itemInputs, itemOutputs;
    public final FluidStack[] fluidInputs, fluidOutputs;
    public final int durationTicks;
    public final long euPerTick, generatedEu;
    public final String nativeFingerprint;
    public final int nativeDurationTicks, parallel, billingVersion;
    public final long nativeEuPerTick, fusionThreshold;

    public RecipeMatch(String id, ItemStack[] itemInputs, FluidStack[] fluidInputs, ItemStack[] itemOutputs,
        FluidStack[] fluidOutputs, int durationTicks, long euPerTick, long generatedEu) {
        this(
            id,
            itemInputs,
            fluidInputs,
            itemOutputs,
            fluidOutputs,
            durationTicks,
            euPerTick,
            generatedEu,
            "",
            0,
            0,
            0,
            0,
            0);
    }

    public RecipeMatch(String id, ItemStack[] itemInputs, FluidStack[] fluidInputs, ItemStack[] itemOutputs,
        FluidStack[] fluidOutputs, int durationTicks, long euPerTick, long generatedEu, String nativeFingerprint,
        int nativeDurationTicks, long nativeEuPerTick, long fusionThreshold, int parallel, int billingVersion) {
        this.id = id;
        this.itemInputs = itemInputs;
        this.fluidInputs = fluidInputs;
        this.itemOutputs = itemOutputs;
        this.fluidOutputs = fluidOutputs;
        this.durationTicks = durationTicks;
        this.euPerTick = euPerTick;
        this.generatedEu = generatedEu;
        this.nativeFingerprint = nativeFingerprint;
        this.nativeDurationTicks = nativeDurationTicks;
        this.nativeEuPerTick = nativeEuPerTick;
        this.fusionThreshold = fusionThreshold;
        this.parallel = parallel;
        this.billingVersion = billingVersion;
    }
}
