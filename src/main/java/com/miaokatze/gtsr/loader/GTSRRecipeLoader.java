package com.miaokatze.gtsr.loader;

import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.airCompressorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.ammoniaPlantRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.atmosphericCentrifugeRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.largeCokeOvenRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.siemensMartinRecipes;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.INGOTS;
import static gregtech.api.util.GTRecipeBuilder.MINUTES;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.main.GTSteamReborn;

import bartworks.system.material.Werkstoff;
import bartworks.system.material.WerkstoffLoader;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gtPlusPlus.core.material.MaterialsAlloy;

public class GTSRRecipeLoader implements Runnable {

    private static void log(String msg) {
        GTSteamReborn.LOG.info("[GTSR-Recipe] " + msg);
    }

    private static void warn(String msg) {
        GTSteamReborn.LOG.warn("[GTSR-Recipe] " + msg);
    }

    private static ItemStack safeGet(OrePrefixes prefix, Materials mat, long amount) {
        ItemStack stack = GTOreDictUnificator.get(prefix, mat, amount);
        if (stack == null) {
            warn(prefix + " + " + mat + " returned null!");
        }
        return stack;
    }

    private static ItemStack safeGet(OrePrefixes prefix, Werkstoff werkstoff, long amount) {
        ItemStack stack = GTOreDictUnificator.get(prefix, werkstoff, amount);
        if (stack == null) {
            warn(prefix + " + Werkstoff:" + werkstoff + " returned null!");
        }
        return stack;
    }

    private static ItemStack safeGet(OrePrefixes prefix, gtPlusPlus.core.material.Material mat, long amount) {
        ItemStack stack = GTOreDictUnificator.get(prefix, mat, amount);
        if (stack == null) {
            warn(prefix + " + GT++Material:" + mat + " returned null!");
        }
        return stack;
    }

    private static ItemStack safeGet(ItemList item, int amount) {
        ItemStack stack = item.get(amount);
        if (stack == null) {
            warn("ItemList." + item.name() + " returned null!");
        }
        return stack;
    }

    private static ItemStack safeGet(GTSRItemList item, int amount) {
        ItemStack stack = item.get(amount);
        if (stack == null) {
            warn("GTSRItemList." + item.name() + " returned null!");
        }
        return stack;
    }

    @Override
    public void run() {
        registerCokeOvenRecipes();
        registerSiemensMartinRecipes();
        registerAmmoniaRecipes();
        registerAirCompressorRecipes();
        registerAtmosphericCentrifugeRecipes();
        registerChipRecipes();
        registerCatalystRecipes();
        registerCacheNodeRecipes();
        registerNodeRecipes();
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

    private static void registerChipRecipes() {
        log("Registering chip recipes...");

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(ItemList.OilDrill1, 1),
                safeGet(GTSRItemList.SteamEntangledSingularity, 16),
                safeGet(OrePrefixes.plateDouble, Materials.StainlessSteel, 32),
                safeGet(OrePrefixes.circuit, Materials.HV, 8))
            .itemOutputs(safeGet(GTSRItemList.VeinPyrolyzerChipT1, 1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(4 * INGOTS))
            .duration(30 * SECONDS)
            .eut(TierEU.RECIPE_HV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(ItemList.OilDrill2, 1),
                safeGet(GTSRItemList.SteamEntangledSingularity, 32),
                safeGet(OrePrefixes.plateTriple, Materials.Titanium, 64),
                safeGet(OrePrefixes.circuit, Materials.EV, 16),
                safeGet(GTSRItemList.VeinPyrolyzerChipT1, 1))
            .itemOutputs(safeGet(GTSRItemList.VeinPyrolyzerChipT2, 1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(6 * INGOTS))
            .duration(45 * SECONDS)
            .eut(TierEU.RECIPE_EV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(ItemList.OilDrill3, 1),
                safeGet(GTSRItemList.SteamEntangledSingularity, 64),
                safeGet(OrePrefixes.plateDense, Materials.TungstenSteel, 64),
                safeGet(OrePrefixes.circuit, Materials.IV, 32),
                safeGet(GTSRItemList.VeinPyrolyzerChipT2, 1))
            .itemOutputs(safeGet(GTSRItemList.VeinPyrolyzerChipT3, 1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(8 * INGOTS))
            .duration(60 * SECONDS)
            .eut(TierEU.RECIPE_IV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(GTSRItemList.SteamEntangledSingularity, 6),
                safeGet(OrePrefixes.plate, Materials.Gold, 4),
                safeGet(OrePrefixes.plate, Materials.Steel, 4),
                safeGet(OrePrefixes.circuit, Materials.LV, 2),
                safeGet(ItemList.Electric_Pump_LV, 8))
            .itemOutputs(safeGet(GTSRItemList.GeothermalOverheatChip, 1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
            .duration(20 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(GTSRItemList.SteamEntangledSingularity, 8),
                safeGet(OrePrefixes.plate, Materials.Steel, 32),
                safeGet(OrePrefixes.circuit, Materials.LV, 8),
                safeGet(OrePrefixes.screw, Materials.Steel, 64),
                safeGet(ItemList.Sensor_LV, 8),
                safeGet(ItemList.Emitter_LV, 8))
            .itemOutputs(safeGet(GTSRItemList.HubSingularityChip, 1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(4 * INGOTS))
            .duration(30 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(GTSRItemList.SteamEntangledSingularity, 16),
                safeGet(OrePrefixes.plateDouble, Materials.StainlessSteel, 32),
                safeGet(OrePrefixes.circuit, Materials.HV, 8),
                safeGet(OrePrefixes.screw, Materials.StainlessSteel, 64),
                safeGet(ItemList.Electric_Motor_HV, 32))
            .itemOutputs(safeGet(GTSRItemList.RareGasSeparationChip, 1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(6 * INGOTS))
            .duration(40 * SECONDS)
            .eut(TierEU.RECIPE_HV)
            .addTo(assemblerRecipes);

        log("Chip recipes done.");
    }

    private static void registerCatalystRecipes() {
        log("Registering catalyst recipes (tier order: Ni->Pt->U->Os->FeCo->Ru->Quantum)...");

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(OrePrefixes.dust, Materials.Nickel, 48),
                safeGet(OrePrefixes.dust, Materials.Aluminium, 24),
                safeGet(OrePrefixes.plate, Materials.StainlessSteel, 32))
            .itemOutputs(safeGet(GTSRItemList.AmmoniaCatalystNickel, 1))
            .fluidInputs(Materials.Oxygen.getGas(24000))
            .duration(480 * SECONDS)
            .eut(TierEU.RECIPE_HV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(OrePrefixes.dust, Materials.Platinum, 16),
                safeGet(OrePrefixes.dust, Materials.Aluminium, 48),
                safeGet(OrePrefixes.plate, Materials.Titanium, 32))
            .itemOutputs(safeGet(GTSRItemList.AmmoniaCatalystPlatinum, 1))
            .fluidInputs(Materials.Hydrogen.getGas(16000))
            .duration(640 * SECONDS)
            .eut(TierEU.RECIPE_EV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(OrePrefixes.dust, Materials.Uranium, 16),
                safeGet(OrePrefixes.dust, Materials.Iron, 32),
                safeGet(OrePrefixes.dust, Materials.Aluminium, 24),
                safeGet(OrePrefixes.plate, Materials.TungstenSteel, 48))
            .itemOutputs(safeGet(GTSRItemList.AmmoniaCatalystUranium, 1))
            .fluidInputs(Materials.Nitrogen.getGas(16000))
            .duration(800 * SECONDS)
            .eut(TierEU.RECIPE_IV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(OrePrefixes.dust, Materials.Osmium, 48),
                safeGet(OrePrefixes.dust, Materials.Aluminium, 40),
                safeGet(OrePrefixes.dust, Materials.Silicon, 8),
                safeGet(OrePrefixes.plateDense, Materials.TungstenSteel, 48))
            .itemOutputs(safeGet(GTSRItemList.AmmoniaCatalystOsmium, 1))
            .fluidInputs(Materials.Nitrogen.getGas(24000))
            .duration(800 * SECONDS)
            .eut(TierEU.RECIPE_IV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(OrePrefixes.dust, Materials.Iron, 64),
                safeGet(OrePrefixes.dust, Materials.Cobalt, 8),
                safeGet(OrePrefixes.dust, Materials.Aluminium, 16),
                safeGet(OrePrefixes.plateDense, Materials.Iridium, 48))
            .itemOutputs(safeGet(GTSRItemList.AmmoniaCatalystFeCo, 1))
            .fluidInputs(Materials.Oxygen.getGas(32000))
            .duration(960 * SECONDS)
            .eut(TierEU.RECIPE_UV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(OrePrefixes.dust, WerkstoffLoader.Ruthenium, 16),
                safeGet(OrePrefixes.dust, Materials.Carbon, 40),
                safeGet(OrePrefixes.dust, Materials.Aluminium, 8))
            .itemOutputs(safeGet(GTSRItemList.AmmoniaCatalystRuthenium, 1))
            .fluidInputs(Materials.Hydrogen.getGas(24000))
            .duration(960 * SECONDS)
            .eut(TierEU.RECIPE_LuV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(OrePrefixes.dust, Materials.CosmicNeutronium, 16),
                safeGet(OrePrefixes.dust, MaterialsAlloy.QUANTUM, 64),
                safeGet(OrePrefixes.plateDense, Materials.Osmium, 48))
            .itemOutputs(safeGet(GTSRItemList.AmmoniaCatalystQuantum, 1))
            .fluidInputs(WerkstoffLoader.Krypton.getFluidOrGas(32000))
            .duration(1440 * SECONDS)
            .eut(TierEU.RECIPE_UV)
            .addTo(assemblerRecipes);

        log("Catalyst recipes done.");
    }

    private static void registerCacheNodeRecipes() {
        log("Registering cache node recipes...");

        ItemStack steamCacheResult = GTSRItemList.SteamCacheNode.get(1);
        if (steamCacheResult == null) {
            warn("SteamCacheNode item is null, skipping recipe!");
            return;
        }
        GTModHandler.addCraftingRecipe(
            steamCacheResult,
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "MPM", "PTP", "MPM", 'M', "plateTripleBronze", 'P', "pipeLargeBronze", 'T',
                ItemList.Super_Tank_LV });

        ItemStack reinforcedResult = GTSRItemList.ReinforcedSteamCacheNode.get(1);
        if (reinforcedResult == null) {
            warn("ReinforcedSteamCacheNode item is null, skipping recipe!");
            return;
        }
        GTModHandler.addCraftingRecipe(
            reinforcedResult,
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "MPM", "PTP", "MPM", 'M', "plateTripleSteel", 'P', "pipeLargeSteel", 'T',
                GTSRItemList.SteamCacheNode });

        ItemStack waterResult = GTSRItemList.WaterCacheNode.get(1);
        if (waterResult == null) {
            warn("WaterCacheNode item is null, skipping recipe!");
            return;
        }
        ItemStack bcTank = GTModHandler.getModItem("BuildCraft|Factory", "tankBlock", 1);
        if (bcTank == null) {
            warn("BuildCraft tank is null, trying alternate mod ID...");
            bcTank = GTModHandler.getModItem("BuildCraft:Factory", "tankBlock", 1);
        }
        if (bcTank == null) {
            warn("BuildCraft tank still null, skipping WaterCacheNode recipe!");
            return;
        }
        GTModHandler.addCraftingRecipe(
            waterResult,
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "MPM", "PTP", "MPM", 'M', "plateTripleBronze", 'P', "pipeMediumBronze", 'T', bcTank });

        log("Cache node recipes done.");
    }

    private static void registerNodeRecipes() {
        log("Registering mining/drilling node recipes...");

        ItemStack miningPipe = GTModHandler.getIC2Item("miningPipe", 8);
        if (miningPipe == null) {
            warn("IC2 miningPipe is null! Trying alternate retrieval...");
            miningPipe = GTModHandler.getIC2Item("miningPipe", 1);
        }

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(GTSRItemList.SteamEntangledSingularity, 2),
                safeGet(OrePrefixes.frameGt, Materials.Steel, 1),
                safeGet(OrePrefixes.gearGt, Materials.Steel, 8),
                safeGet(OrePrefixes.plateDense, Materials.Steel, 4),
                miningPipe,
                safeGet(ItemList.Electric_Piston_LV, 4),
                safeGet(OrePrefixes.circuit, Materials.LV, 2))
            .itemOutputs(safeGet(GTSRItemList.SingularityMinerNode, 1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
            .duration(20 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                safeGet(GTSRItemList.SteamEntangledSingularity, 2),
                safeGet(OrePrefixes.frameGt, Materials.Steel, 1),
                safeGet(OrePrefixes.pipeHuge, Materials.Steel, 4),
                safeGet(OrePrefixes.plateDense, Materials.Steel, 4),
                miningPipe,
                safeGet(ItemList.Electric_Pump_LV, 6),
                safeGet(OrePrefixes.circuit, Materials.LV, 2))
            .itemOutputs(safeGet(GTSRItemList.SingularityDrillingNode, 1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
            .duration(20 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        log("Node recipes done.");
    }
}
