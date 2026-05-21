package com.miaokatze.gtsr.loader;

import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.airCompressorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.ammoniaPlantRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.atmosphericCentrifugeRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.largeCokeOvenRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.siemensMartinRecipes;
import static gregtech.api.util.GTRecipeBuilder.MINUTES;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraftforge.oredict.OreDictionary;

import bartworks.system.material.WerkstoffLoader;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;

public class GTSRRecipeLoader implements Runnable {

    @Override
    public void run() {
        registerCokeOvenRecipes();
        registerSiemensMartinRecipes();
        registerAmmoniaRecipes();
        registerAirCompressorRecipes();
        registerAtmosphericCentrifugeRecipes();
    }

    private static void registerCokeOvenRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Coal.getGems(1))
            .itemOutputs(
                OreDictionary.getOres("fuelCoke")
                    .get(0)
                    .copy())
            .duration(30 * MINUTES)
            .eut(0)
            .addTo(largeCokeOvenRecipes);
    }

    private static void registerSiemensMartinRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getIngots(1), Materials.Coal.getGems(2))
            .itemOutputs(Materials.Steel.getIngots(1), Materials.DarkAsh.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getDust(1), Materials.Coal.getGems(2))
            .itemOutputs(Materials.Steel.getIngots(1), Materials.DarkAsh.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getIngots(1), Materials.Coal.getDust(2))
            .itemOutputs(Materials.Steel.getIngots(1), Materials.DarkAsh.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getDust(1), Materials.Coal.getDust(2))
            .itemOutputs(Materials.Steel.getIngots(1), Materials.DarkAsh.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.Iron.getIngots(1),
                OreDictionary.getOres("fuelCoke")
                    .get(0)
                    .copy())
            .itemOutputs(Materials.Ash.getDust(1))
            .duration(1200 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.Iron.getDust(1),
                OreDictionary.getOres("fuelCoke")
                    .get(0)
                    .copy())
            .itemOutputs(Materials.Ash.getDust(1))
            .duration(1200 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getIngots(1), Materials.Carbon.getDust(1))
            .itemOutputs(Materials.Steel.getIngots(1), Materials.Ash.getDust(1))
            .duration(800 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getDust(1), Materials.Carbon.getDust(1))
            .itemOutputs(Materials.Steel.getIngots(1), Materials.Ash.getDust(1))
            .duration(800 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);
    }

    private static void registerAmmoniaRecipes() {
        GTValues.RA.stdBuilder()
            .fluidInputs(Materials.Gas.getGas(1000), Materials.Nitrogen.getGas(1000))
            .fluidOutputs(Materials.Ammonia.getGas(1000))
            .itemOutputs(Materials.Ash.getDust(1), Materials.Carbon.getDust(1))
            .outputChances(10000, 1000)
            .duration(64 * SECONDS)
            .eut(0)
            .addTo(ammoniaPlantRecipes);
    }

    private static void registerAirCompressorRecipes() {
        GTValues.RA.stdBuilder()
            .fluidOutputs(Materials.Air.getGas(800))
            .duration(20)
            .eut(-60)
            .addTo(airCompressorRecipes);
    }

    private static void registerAtmosphericCentrifugeRecipes() {
        GTValues.RA.stdBuilder()
            .fluidInputs(Materials.Air.getGas(10000))
            .fluidOutputs(Materials.Nitrogen.getGas(7800), Materials.Oxygen.getGas(2100))
            .duration(1000)
            .eut(-25)
            .addTo(atmosphericCentrifugeRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(Materials.Air.getGas(2000000))
            .fluidOutputs(
                Materials.Nitrogen.getGas(1560000),
                Materials.Oxygen.getGas(420000),
                Materials.Argon.getGas(3860),
                Materials.CarbonDioxide.getGas(600),
                WerkstoffLoader.Neon.getFluidOrGas(40),
                Materials.Methane.getGas(4),
                WerkstoffLoader.Krypton.getFluidOrGas(2),
                Materials.Helium.getGas(1))
            .duration(40000)
            .eut(-250)
            .addTo(atmosphericCentrifugeRecipes);
    }
}
