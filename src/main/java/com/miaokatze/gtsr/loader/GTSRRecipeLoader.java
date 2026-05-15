package com.miaokatze.gtsr.loader;

import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.largeCokeOvenRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.siemensMartinRecipes;
import static gregtech.api.util.GTRecipeBuilder.MINUTES;

import net.minecraftforge.oredict.OreDictionary;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;

public class GTSRRecipeLoader implements Runnable {

    @Override
    public void run() {
        registerCokeOvenRecipes();
        registerSiemensMartinRecipes();
    }

    private static void registerCokeOvenRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Coal.getGems(1))
            .itemOutputs(
                OreDictionary.getOres("fuelCoke")
                    .get(0)
                    .copy())
            .fluidOutputs(Materials.Creosote.getFluid(500))
            .duration(30 * MINUTES)
            .eut(0)
            .addTo(largeCokeOvenRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Lignite.getGems(1))
            .itemOutputs(
                OreDictionary.getOres("fuelCoke")
                    .get(0)
                    .copy())
            .fluidOutputs(Materials.Creosote.getFluid(400))
            .duration(25 * MINUTES)
            .eut(0)
            .addTo(largeCokeOvenRecipes);
    }

    private static void registerSiemensMartinRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getIngots(1))
            .itemOutputs(Materials.Steel.getIngots(1))
            .duration(6 * MINUTES)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getDust(1))
            .itemOutputs(Materials.Steel.getIngots(1))
            .duration(6 * MINUTES)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.WroughtIron.getIngots(1))
            .itemOutputs(Materials.Steel.getIngots(1))
            .duration(4 * MINUTES)
            .eut(0)
            .addTo(siemensMartinRecipes);
    }
}
