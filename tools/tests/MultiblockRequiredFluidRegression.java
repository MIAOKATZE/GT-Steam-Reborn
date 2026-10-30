package com.miaokatze.gtsr.loader.recipes;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import gregtech.api.util.GTRecipeBuilder;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/** Complete production source, real Forge fluid registry/NBT; item lookups and builder are capture seams. */
public final class MultiblockRequiredFluidRegression {
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        GTSRItemList[] outputs = { GTSRItemList.CriticalSingularityCompressor,
            GTSRItemList.DenseStateManipulator, GTSRItemList.SteamTurbineCycleOverlimitChip };
        int failures = 0;
        MultiblockMachineRecipes.registerMultiblockAssemblerRecipes();
        for (GTSRItemList output : outputs) {
            boolean pass = !GTRecipeBuilder.byOutput.containsKey(output.ordinal());
            System.out.println((pass ? "PASS " : "FAIL ") + "missing required fluid skips " + output);
            if (!pass) failures++;
        }
        FluidRegistry.registerFluid(new Fluid("molten.indalloy140"));
        FluidRegistry.registerFluid(new Fluid("oganesson"));
        GTRecipeBuilder.byOutput.clear();
        MultiblockMachineRecipes.registerMultiblockAssemblerRecipes();
        for (int i = 0; i < outputs.length; i++) {
            FluidStack[] fluids = GTRecipeBuilder.byOutput.get(outputs[i].ordinal());
            boolean pass = fluids != null && fluids.length == 1
                && fluids[0].amount == (i == 2 ? 1000 : 32000)
                && fluids[0].getFluid().getName().equals(i == 2 ? "oganesson" : "molten.indalloy140");
            System.out.println((pass ? "PASS " : "FAIL ") + "valid fluid requirement retained for " + outputs[i]);
            if (!pass) failures++;
        }
        if (failures != 0) throw new AssertionError(failures + " missing-fluid regressions");
    }
}
