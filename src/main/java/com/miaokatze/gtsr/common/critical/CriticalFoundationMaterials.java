package com.miaokatze.gtsr.common.critical;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.FluidState;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.fluid.GTFluidFactory;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

/** The loom's foundation bill and its concrete mixer recipe. */
public final class CriticalFoundationMaterials {

    public static final String FLUID_NAME = "gtsr.ultra_high_performance_concrete";
    private static Fluid concrete;
    private static boolean registered;

    private CriticalFoundationMaterials() {}

    public static void registerRecipes() {
        if (registered) return;
        FluidStack wetConcrete = Materials.Concrete.getMolten(9000);
        if (wetConcrete == null) throw new IllegalStateException("Missing wet concrete");
        concrete = GTFluidFactory.builder(FLUID_NAME)
            .withDefaultLocalName("Ultra High Performance Concrete")
            .withStateAndTemperature(FluidState.LIQUID, 300)
            .withColorRGBA(new short[] { 150, 157, 166, 255 })
            .withIconsFrom(wetConcrete.getFluid())
            .buildAndRegister()
            .asFluid();
        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTModHandler.getIC2Item("carbonFiber", 32),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.SiliconDioxide, 64))
            .fluidInputs(wetConcrete)
            .fluidOutputs(fluid(16000))
            .duration(300 * 20)
            .eut(TierEU.RECIPE_IV)
            .addTo(RecipeMaps.mixerRecipes);
        registered = true;
    }

    public static FluidStack fluid(int amount) {
        if (concrete == null) throw new IllegalStateException("Foundation concrete not registered");
        return new FluidStack(concrete, amount);
    }

    public static int concreteAmount(int scheduleVersion) {
        return scheduleVersion >= CriticalFoundationChunk.VERSION ? 50_000 : 1_000_000;
    }

    public static ItemStack[] items() {
        return new ItemStack[] {
            GTUtility.copyAmountUnsafe(512, GTOreDictUnificator.get(OrePrefixes.stickLong, Materials.Neutronium, 1)),
            ItemList.Hull_MAX.get(32) };
    }

    /** New cells have their own bill; historical journals retain their original bill. */
    public static ItemStack[] items(boolean modern) {
        return items(modern ? CriticalFoundationChunk.VERSION : 0);
    }

    public static ItemStack[] items(int scheduleVersion) {
        if (scheduleVersion < 5) return items();
        if (scheduleVersion >= CriticalFoundationChunk.VERSION) return new ItemStack[] {
            GTUtility.copyAmountUnsafe(32, GTOreDictUnificator.get(OrePrefixes.stickLong, Materials.Neutronium, 1)),
            ItemList.Hull_MAX.get(2), GTUtility
                .copyAmountUnsafe(400, GTOreDictUnificator.get(OrePrefixes.wireFine, Materials.TungstenSteel, 1)) };
        return new ItemStack[] { items()[0], items()[1],
            GTUtility.copyAmountUnsafe(
                scheduleVersion >= 6 ? 6_400 : 8_000,
                GTOreDictUnificator.get(OrePrefixes.wireFine, Materials.TungstenSteel, 1)) };
    }
}
