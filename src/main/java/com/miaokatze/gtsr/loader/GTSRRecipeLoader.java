package com.miaokatze.gtsr.loader;

import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.airCompressorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.ammoniaPlantRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.atmosphericCentrifugeRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.gearSteamCompressorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.geothermalSteamBoilerRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.largeCokeOvenRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.siemensMartinRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.steamFluidDrillRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.steamSingularityCompressorRecipes;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.INGOTS;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.main.GTSteamReborn;

import bartworks.system.material.WerkstoffLoader;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gtPlusPlus.core.fluids.GTPPFluids;
import gtPlusPlus.core.material.MaterialsAlloy;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;

public class GTSRRecipeLoader implements Runnable {

    private static void log(String msg) {
        GTSteamReborn.LOG.info("[GTSR-Recipe] " + msg);
    }

    private static void warn(String msg) {
        GTSteamReborn.LOG.warn("[GTSR-Recipe] " + msg);
    }

    private static ItemStack get(OrePrefixes prefix, Object mat, long amount) {
        ItemStack stack = GTOreDictUnificator.get(prefix, mat, amount);
        if (stack == null) {
            warn(prefix + " + " + mat + " returned null!");
        }
        return stack;
    }

    private static ItemStack get(ItemList item, int amount) {
        ItemStack stack = item.get(amount);
        if (stack == null) {
            warn("ItemList." + item.name() + " returned null!");
        }
        return stack;
    }

    private static ItemStack get(GTSRItemList item, int amount) {
        ItemStack stack = item.get(amount);
        if (stack == null) {
            warn("GTSRItemList." + item.name() + " returned null!");
        }
        return stack;
    }

    private static boolean hasNull(ItemStack... stacks) {
        for (ItemStack s : stacks) {
            if (s == null) return true;
        }
        return false;
    }

    private static ItemStack[] filterNulls(ItemStack... stacks) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s != null) list.add(s);
        }
        return list.toArray(new ItemStack[0]);
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
        registerMultiblockWorkbenchRecipes();
        registerMultiblockAssemblerRecipes();
        registerHatchRecipes();
        registerSingularityCompressorDisplayRecipe();
        registerGeothermalBoilerDisplayRecipes();
        registerFluidDrillDisplayRecipes();
        registerGearSteamCompressorDisplayRecipes();
    }

    private static void registerCokeOvenRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Coal.getGems(1))
            .itemOutputs(
                OreDictionary.getOres("fuelCoke")
                    .get(0)
                    .copy())
            .fluidOutputs(Materials.Creosote.getFluid(500))
            .duration(1800)
            .eut(0)
            .addTo(largeCokeOvenRecipes);
    }

    private static void registerSiemensMartinRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getIngots(1), Materials.Coal.getGems(2))
            .itemOutputs(Materials.Steel.getIngots(1), Materials.Ash.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getDust(1), Materials.Coal.getGems(2))
            .itemOutputs(Materials.Steel.getIngots(1), Materials.Ash.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getIngots(1), Materials.Coal.getDust(2))
            .itemOutputs(Materials.Steel.getIngots(1), Materials.Ash.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getDust(1), Materials.Coal.getDust(2))
            .itemOutputs(Materials.Steel.getIngots(1), Materials.Ash.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.Iron.getIngots(1),
                OreDictionary.getOres("fuelCoke")
                    .get(0)
                    .copy())
            .itemOutputs(Materials.Steel.getIngots(1), Materials.Ash.getDust(1))
            .outputChances(10000, 7000)
            .duration(1200 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.Iron.getDust(1),
                OreDictionary.getOres("fuelCoke")
                    .get(0)
                    .copy())
            .itemOutputs(Materials.Steel.getIngots(1), Materials.Ash.getDust(1))
            .outputChances(10000, 7000)
            .duration(1200 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getIngots(1), Materials.Carbon.getDust(1))
            .itemOutputs(Materials.Steel.getIngots(1), Materials.Ash.getDust(1))
            .outputChances(10000, 3000)
            .duration(800 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getDust(1), Materials.Carbon.getDust(1))
            .itemOutputs(Materials.Steel.getIngots(1), Materials.Ash.getDust(1))
            .outputChances(10000, 3000)
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

        try {
            FluidStack netherAir = Materials.NetherAir.getFluid(800);
            if (netherAir != null) {
                GTValues.RA.stdBuilder()
                    .fluidOutputs(netherAir)
                    .duration(20)
                    .eut(-60)
                    .addTo(airCompressorRecipes);
            } else {
                warn("Materials.NetherAir.getFluid(800) returned null, skipping Nether Air recipe!");
            }
        } catch (Exception e) {
            warn("Failed to register Nether Air compressor recipe: " + e.getMessage());
        }
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

        try {
            FluidStack netherAirInput = Materials.NetherAir.getFluid(10000);
            if (netherAirInput == null) {
                warn("Materials.NetherAir.getFluid() returned null, skipping Nether Air centrifuge recipes!");
                return;
            }
            GTValues.RA.stdBuilder()
                .fluidInputs(netherAirInput)
                .fluidOutputs(
                    Materials.NitrogenDioxide.getGas(1400),
                    Materials.SulfurDioxide.getGas(3800),
                    Materials.SulfurTrioxide.getGas(2100))
                .duration(1000)
                .eut(-25)
                .addTo(atmosphericCentrifugeRecipes);
        } catch (Exception e) {
            warn("Failed to register Nether Air basic centrifuge recipe: " + e.getMessage());
        }

        try {
            FluidStack netherAirInput2 = Materials.NetherAir.getFluid(100000);
            FluidStack anthracene = (GTPPFluids.Anthracene != null) ? new FluidStack(GTPPFluids.Anthracene, 2500)
                : null;
            if (netherAirInput2 == null || anthracene == null) {
                warn(
                    "Skipping Nether Air rare gas centrifuge recipe - NetherAir=" + (netherAirInput2 != null)
                        + ", Anthracene="
                        + (anthracene != null));
                return;
            }
            GTValues.RA.stdBuilder()
                .fluidInputs(netherAirInput2)
                .fluidOutputs(
                    Materials.NitrogenDioxide.getGas(14000),
                    Materials.SulfurDioxide.getGas(35000),
                    Materials.SulfurTrioxide.getGas(20000),
                    Materials.Chlorine.getGas(2000),
                    WerkstoffLoader.Neon.getFluidOrGas(1200),
                    anthracene,
                    Materials.Radon.getGas(1))
                .duration(20000)
                .eut(-250)
                .addTo(atmosphericCentrifugeRecipes);
        } catch (Exception e) {
            warn("Failed to register Nether Air rare gas centrifuge recipe: " + e.getMessage());
        }
    }

    private static void registerChipRecipes() {
        log("Registering chip recipes...");

        ItemStack t1out = get(GTSRItemList.VeinPyrolyzerChipT1, 1);
        ItemStack t2out = get(GTSRItemList.VeinPyrolyzerChipT2, 1);
        ItemStack t3out = get(GTSRItemList.VeinPyrolyzerChipT3, 1);
        ItemStack geoOut = get(GTSRItemList.GeothermalOverheatChip, 1);
        ItemStack hubOut = get(GTSRItemList.HubSingularityChip, 1);
        ItemStack rareOut = get(GTSRItemList.RareGasSeparationChip, 1);

        if (!hasNull(t1out)) {
            ItemStack[] inputs = filterNulls(
                get(ItemList.OilDrill2, 1),
                get(GTSRItemList.SteamEntangledSingularity, 16),
                get(OrePrefixes.plateDouble, Materials.StainlessSteel, 32),
                get(OrePrefixes.circuit, Materials.HV, 8));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(t1out)
                .fluidInputs(Materials.SolderingAlloy.getMolten(4 * INGOTS))
                .duration(30 * SECONDS)
                .eut(TierEU.RECIPE_HV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped VeinPyrolyzerChipT1 recipe - output is null");
        }

        if (!hasNull(t2out)) {
            ItemStack[] inputs = filterNulls(
                get(ItemList.OilDrill3, 1),
                get(GTSRItemList.SteamEntangledSingularity, 32),
                get(OrePrefixes.plateTriple, Materials.Titanium, 64),
                get(OrePrefixes.circuit, Materials.EV, 16),
                get(GTSRItemList.VeinPyrolyzerChipT1, 1));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(t2out)
                .fluidInputs(Materials.SolderingAlloy.getMolten(6 * INGOTS))
                .duration(45 * SECONDS)
                .eut(TierEU.RECIPE_EV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped VeinPyrolyzerChipT2 recipe - output is null");
        }

        if (!hasNull(t3out)) {
            ItemStack[] inputs = filterNulls(
                get(ItemList.OilDrill4, 1),
                get(GTSRItemList.SteamEntangledSingularity, 64),
                get(OrePrefixes.plateDense, Materials.TungstenSteel, 64),
                get(OrePrefixes.circuit, Materials.IV, 32),
                get(GTSRItemList.VeinPyrolyzerChipT2, 1));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(t3out)
                .fluidInputs(Materials.SolderingAlloy.getMolten(8 * INGOTS))
                .duration(60 * SECONDS)
                .eut(TierEU.RECIPE_IV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped VeinPyrolyzerChipT3 recipe - output is null");
        }

        if (!hasNull(geoOut)) {
            ItemStack[] inputs = filterNulls(
                get(GTSRItemList.SteamEntangledSingularity, 6),
                get(OrePrefixes.plate, Materials.Gold, 4),
                get(OrePrefixes.plate, Materials.Steel, 4),
                get(OrePrefixes.circuit, Materials.LV, 2),
                get(ItemList.Electric_Pump_LV, 8));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(geoOut)
                .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
                .duration(20 * SECONDS)
                .eut(TierEU.RECIPE_LV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped GeothermalOverheatChip recipe - output is null");
        }

        if (!hasNull(hubOut)) {
            ItemStack[] inputs = filterNulls(
                get(GTSRItemList.SteamEntangledSingularity, 8),
                get(OrePrefixes.plate, Materials.Steel, 32),
                get(OrePrefixes.circuit, Materials.LV, 8),
                get(OrePrefixes.screw, Materials.Steel, 64),
                get(ItemList.Sensor_LV, 8),
                get(ItemList.Emitter_LV, 8));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(hubOut)
                .fluidInputs(Materials.SolderingAlloy.getMolten(4 * INGOTS))
                .duration(30 * SECONDS)
                .eut(TierEU.RECIPE_LV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped HubSingularityChip recipe - output is null");
        }

        if (!hasNull(rareOut)) {
            ItemStack[] inputs = filterNulls(
                get(GTSRItemList.SteamEntangledSingularity, 16),
                get(OrePrefixes.plateDouble, Materials.StainlessSteel, 32),
                get(OrePrefixes.circuit, Materials.HV, 8),
                get(OrePrefixes.screw, Materials.StainlessSteel, 64),
                get(ItemList.Electric_Motor_HV, 32));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(rareOut)
                .fluidInputs(Materials.SolderingAlloy.getMolten(6 * INGOTS))
                .duration(40 * SECONDS)
                .eut(TierEU.RECIPE_HV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped RareGasSeparationChip recipe - output is null");
        }

        log("Chip recipes done.");
    }

    private static void registerCatalystRecipes() {
        log("Registering catalyst recipes (tier order: Ni->Pt->U->Os->FeCo->Ru->Quantum)...");

        ItemStack niOut = get(GTSRItemList.AmmoniaCatalystNickel, 1);
        if (!hasNull(niOut)) {
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.dust, Materials.Nickel, 48),
                get(OrePrefixes.dust, Materials.Aluminium, 24),
                get(OrePrefixes.plate, Materials.StainlessSteel, 32));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(niOut)
                .fluidInputs(Materials.Oxygen.getGas(24000))
                .duration(480 * SECONDS)
                .eut(TierEU.RECIPE_HV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystNickel recipe");
        }

        ItemStack ptOut = get(GTSRItemList.AmmoniaCatalystPlatinum, 1);
        if (!hasNull(ptOut)) {
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.dust, Materials.Platinum, 16),
                get(OrePrefixes.dust, Materials.Aluminium, 48),
                get(OrePrefixes.plate, Materials.Titanium, 32));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(ptOut)
                .fluidInputs(Materials.Hydrogen.getGas(16000))
                .duration(640 * SECONDS)
                .eut(TierEU.RECIPE_EV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystPlatinum recipe");
        }

        ItemStack uOut = get(GTSRItemList.AmmoniaCatalystUranium, 1);
        if (!hasNull(uOut)) {
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.dust, Materials.Uranium, 16),
                get(OrePrefixes.dust, Materials.Iron, 32),
                get(OrePrefixes.dust, Materials.Aluminium, 24),
                get(OrePrefixes.plate, Materials.TungstenSteel, 48));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(uOut)
                .fluidInputs(Materials.Nitrogen.getGas(16000))
                .duration(800 * SECONDS)
                .eut(TierEU.RECIPE_IV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystUranium recipe");
        }

        ItemStack osOut = get(GTSRItemList.AmmoniaCatalystOsmium, 1);
        if (!hasNull(osOut)) {
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.dust, Materials.Osmium, 48),
                get(OrePrefixes.dust, Materials.Aluminium, 40),
                get(OrePrefixes.dust, Materials.Silicon, 8),
                get(OrePrefixes.plateDense, Materials.TungstenSteel, 48));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(osOut)
                .fluidInputs(Materials.Nitrogen.getGas(24000))
                .duration(800 * SECONDS)
                .eut(TierEU.RECIPE_IV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystOsmium recipe");
        }

        ItemStack feCoOut = get(GTSRItemList.AmmoniaCatalystFeCo, 1);
        if (!hasNull(feCoOut)) {
            ItemStack luvPlate = GTOreDictUnificator.get(OrePrefixes.plateDense, Materials.Osmiridium, 32);
            if (luvPlate == null) {
                warn("Osmiridium dense plate (32) returned null!");
            }
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.dust, Materials.Iron, 64),
                get(OrePrefixes.dust, Materials.Cobalt, 8),
                get(OrePrefixes.dust, Materials.Aluminium, 16),
                luvPlate);
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(feCoOut)
                .fluidInputs(Materials.Oxygen.getGas(32000))
                .duration(960 * SECONDS)
                .eut(TierEU.RECIPE_LuV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystFeCo recipe");
        }

        ItemStack ruOut = get(GTSRItemList.AmmoniaCatalystRuthenium, 1);
        if (!hasNull(ruOut)) {
            ItemStack rutheniumDust = WerkstoffLoader.Ruthenium.get(OrePrefixes.dust, 16);
            if (rutheniumDust == null) {
                warn("WerkstoffLoader.Ruthenium.get(dust, 16) returned null!");
            }
            ItemStack[] inputs = filterNulls(
                rutheniumDust,
                get(OrePrefixes.dust, Materials.Carbon, 40),
                get(OrePrefixes.dust, Materials.Aluminium, 8),
                get(OrePrefixes.plateDense, Materials.Iridium, 48));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(ruOut)
                .fluidInputs(Materials.Hydrogen.getGas(24000))
                .duration(1200 * SECONDS)
                .eut(TierEU.RECIPE_ZPM)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystRuthenium recipe");
        }

        ItemStack qOut = get(GTSRItemList.AmmoniaCatalystQuantum, 1);
        if (!hasNull(qOut)) {
            ItemStack quantumDust = Materials.Quantium.getDust(64);
            if (quantumDust == null) {
                warn("Materials.Quantium.getDust(64) returned null!");
            }
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.dust, Materials.CosmicNeutronium, 16),
                quantumDust,
                get(OrePrefixes.plateDense, Materials.Osmium, 48));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(qOut)
                .fluidInputs(WerkstoffLoader.Krypton.getFluidOrGas(32000))
                .duration(1440 * SECONDS)
                .eut(TierEU.RECIPE_UV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystQuantum recipe");
        }

        log("Catalyst recipes done.");
    }

    private static void registerCacheNodeRecipes() {
        log("Registering cache node recipes...");

        ItemStack steamCacheResult = GTSRItemList.SteamCacheNode.get(1);
        if (steamCacheResult == null) {
            warn("SteamCacheNode item is null, skipping recipe!");
        } else {
            GTModHandler.addCraftingRecipe(
                steamCacheResult,
                GTModHandler.RecipeBits.BITSD,
                new Object[] { "MPM", "PTP", "MPM", 'M', "plateTripleBronze", 'P', "pipeLargeBronze", 'T',
                    GregtechItemList.GTFluidTank_ULV.get(1) });
        }

        ItemStack reinforcedResult = GTSRItemList.ReinforcedSteamCacheNode.get(1);
        if (reinforcedResult == null) {
            warn("ReinforcedSteamCacheNode item is null, skipping recipe!");
        } else {
            ItemStack steamCacheInput = GTSRItemList.SteamCacheNode.get(1);
            if (steamCacheInput == null) {
                warn("SteamCacheNode input is null, skipping ReinforcedSteamCacheNode recipe!");
            } else {
                GTModHandler.addCraftingRecipe(
                    reinforcedResult,
                    GTModHandler.RecipeBits.BITSD,
                    new Object[] { "MPM", "PTP", "MPM", 'M', "plateTripleSteel", 'P', "pipeLargeSteel", 'T',
                        steamCacheInput });
            }
        }

        ItemStack waterResult = GTSRItemList.WaterCacheNode.get(1);
        if (waterResult == null) {
            warn("WaterCacheNode item is null, skipping recipe!");
        } else {
            ItemStack bcTank = GTModHandler.getModItem("BuildCraft|Factory", "tankBlock", 1);
            if (bcTank == null) {
                warn("BuildCraft tank is null, trying alternate mod ID...");
                bcTank = GTModHandler.getModItem("BuildCraft:Factory", "tankBlock", 1);
            }
            if (bcTank == null) {
                warn("BuildCraft tank still null, skipping WaterCacheNode recipe!");
            } else {
                GTModHandler.addCraftingRecipe(
                    waterResult,
                    GTModHandler.RecipeBits.BITSD,
                    new Object[] { "MPM", "PTP", "MPM", 'M', "plateTripleBronze", 'P', "pipeLargeBronze", 'T',
                        bcTank });
            }
        }

        log("Cache node recipes done.");
    }

    private static void registerNodeRecipes() {
        log("Registering mining/drilling node recipes...");

        ItemStack miningPipe = GTModHandler.getIC2Item("miningPipe", 8);
        if (miningPipe == null) {
            warn("IC2 miningPipe is null! Trying alternate retrieval...");
            miningPipe = GTModHandler.getIC2Item("miningPipe", 1);
        }

        ItemStack minerOut = get(GTSRItemList.SingularityMinerNode, 1);
        if (!hasNull(minerOut) && miningPipe != null) {
            ItemStack[] inputs = filterNulls(
                get(GTSRItemList.SteamEntangledSingularity, 2),
                get(OrePrefixes.frameGt, Materials.Steel, 1),
                get(OrePrefixes.gearGt, Materials.Steel, 8),
                get(OrePrefixes.plateDense, Materials.Steel, 4),
                miningPipe,
                get(ItemList.Electric_Piston_LV, 4),
                get(OrePrefixes.circuit, Materials.LV, 2));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(minerOut)
                .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
                .duration(20 * SECONDS)
                .eut(TierEU.RECIPE_LV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped SingularityMinerNode recipe" + (miningPipe == null ? " - miningPipe is null" : ""));
        }

        ItemStack drillOut = get(GTSRItemList.SingularityDrillingNode, 1);
        if (!hasNull(drillOut) && miningPipe != null) {
            ItemStack[] inputs = filterNulls(
                get(GTSRItemList.SteamEntangledSingularity, 2),
                get(OrePrefixes.frameGt, Materials.Steel, 1),
                get(OrePrefixes.pipeHuge, Materials.Steel, 4),
                get(OrePrefixes.plateDense, Materials.Steel, 4),
                miningPipe,
                get(ItemList.Electric_Pump_LV, 6),
                get(OrePrefixes.circuit, Materials.LV, 2));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(drillOut)
                .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
                .duration(20 * SECONDS)
                .eut(TierEU.RECIPE_LV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped SingularityDrillingNode recipe" + (miningPipe == null ? " - miningPipe is null" : ""));
        }

        log("Node recipes done.");
    }

    private static void registerMultiblockWorkbenchRecipes() {
        log("Registering multiblock workbench recipes...");

        ItemStack copperPlatedBrick = ItemList.Casing_BronzePlatedBricks.get(1);
        ItemStack roseGoldFrame = MaterialsAlloy.TUMBAGA.getFrameBox(1);
        if (roseGoldFrame == null) {
            warn("MaterialsAlloy.TUMBAGA.getFrameBox(1) returned null! Falling back to RoseGold frame.");
            roseGoldFrame = get(OrePrefixes.frameGt, Materials.RoseGold, 1);
        }
        ItemStack efrBlastFurnace = GTModHandler.getModItem("etfuturum", "blast_furnace", 1);
        if (efrBlastFurnace == null) {
            warn("EFR blast_furnace (etfuturum) is null!");
        }
        ItemStack piston = new ItemStack(
            net.minecraft.init.Blocks.piston,
            1,
            net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE);
        ItemStack stickyPiston = new ItemStack(
            net.minecraft.init.Blocks.sticky_piston,
            1,
            net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE);
        ItemStack glass = new ItemStack(
            net.minecraft.init.Blocks.glass,
            1,
            net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE);
        ItemStack brickBlock = new ItemStack(
            net.minecraft.init.Blocks.brick_block,
            1,
            net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE);

        GTModHandler.addCraftingRecipe(
            GTSRItemList.AirCompressor.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "EFE", 'A', copperPlatedBrick, 'B', "pipeHugeBronze", 'C', piston, 'D',
                roseGoldFrame, 'E', "gearGtBronze", 'F', "gearGtBronze" });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.AtmosphericCentrifuge.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "BBB", 'A', copperPlatedBrick, 'B', "pipeHugeBronze", 'C', "gearGtBronze", 'D',
                roseGoldFrame });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.CrustSteamBorer.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "CEC", 'A', copperPlatedBrick, 'B', "gearGtBronze", 'C', "gearGtBronze", 'D',
                roseGoldFrame, 'E', "pipeLargeBronze" });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.GearSteamCompressor.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "CEC", 'A', copperPlatedBrick, 'B', "pipeLargeBronze", 'C', piston, 'D',
                roseGoldFrame, 'E', "pipeHugeBronze" });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.LargeCokeOven.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', copperPlatedBrick, 'B', brickBlock, 'C', efrBlastFurnace, 'D',
                GTModHandler.getModItem("Railcraft", "machine.alpha", 1, 7) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.LargeGeothermalSteamBoiler.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', copperPlatedBrick, 'B', "pipeHugeBronze", 'C', efrBlastFurnace,
                'D', ItemList.Machine_Steel_Boiler_Lava.get(1) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.LargeSteamFurnace.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', copperPlatedBrick, 'B', ItemList.Casing_Firebox_Bronze.get(1), 'C',
                efrBlastFurnace, 'D', roseGoldFrame });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.SteamFluidDrill.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "BEB", 'A', copperPlatedBrick, 'B', "gearGtBronze", 'C',
                get(OrePrefixes.frameGt, Materials.Bronze, 1), 'D', roseGoldFrame, 'E', "pipeMediumBronze" });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.LargeSolarOverpressureArray.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "AAA", "BDB", "BEB", 'A', glass, 'B', copperPlatedBrick, 'D', roseGoldFrame, 'E',
                get(OrePrefixes.block, Materials.Silver, 1) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.SteamHubArray.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "AEB", 'A', copperPlatedBrick, 'B', "pipeHugeBronze", 'C',
                get(OrePrefixes.frameGt, Materials.Bronze, 1), 'D', roseGoldFrame, 'E', "plateTripleBronze" });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.WaterHubArray.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "EEE", 'A', copperPlatedBrick, 'B', "plateTripleBronze", 'C',
                "plateTripleBronze", 'D', roseGoldFrame, 'E', "pipeMediumBronze" });

        log("Multiblock workbench recipes done.");
    }

    private static void registerMultiblockAssemblerRecipes() {
        log("Registering multiblock assembler recipes...");

        GTValues.RA.stdBuilder()
            .itemInputs(
                get(OrePrefixes.frameGt, Materials.Bronze, 4),
                ItemList.Casing_Firebox_Bronze.get(8),
                get(OrePrefixes.gearGt, Materials.Bronze, 4),
                get(OrePrefixes.circuit, Materials.LV, 4),
                get(OrePrefixes.plateTriple, Materials.Bronze, 12))
            .itemOutputs(GTSRItemList.VeinSteamPyrolyzer.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(288))
            .duration(30 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Machine_Multi_LargeChemicalReactor.get(2),
                get(OrePrefixes.frameGt, Materials.Steel, 4),
                ItemList.Casing_Firebox_Steel.get(6),
                get(OrePrefixes.frameGt, Materials.Polytetrafluoroethylene, 8),
                ItemList.Casing_Pipe_Polytetrafluoroethylene.get(8),
                get(OrePrefixes.plateDense, Materials.StainlessSteel, 8),
                get(OrePrefixes.circuit, Materials.HV, 6),
                ItemList.Electric_Pump_HV.get(12))
            .itemOutputs(GTSRItemList.AmmoniaPlant.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(1728))
            .duration(720 * SECONDS)
            .eut(TierEU.RECIPE_HV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                get(OrePrefixes.frameGt, Materials.Steel, 4),
                ItemList.Casing_Firebox_Steel.get(4),
                get(OrePrefixes.plateDense, Materials.Steel, 12),
                ItemList.Casing_Firebricks.get(32),
                get(OrePrefixes.circuit, Materials.LV, 2),
                get(OrePrefixes.pipeLarge, Materials.Steel, 4))
            .itemOutputs(GTSRItemList.SiemensMartinFurnace.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(288))
            .duration(120 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTSRItemList.SteamEntangledSingularity.get(16),
                get(OrePrefixes.frameGt, Materials.Steel, 8),
                get(OrePrefixes.plateTriple, Materials.Steel, 16),
                ItemList.Sensor_LV.get(12),
                ItemList.Electric_Pump_LV.get(8),
                ItemList.Conveyor_Module_LV.get(8))
            .itemOutputs(GTSRItemList.SingularityDrillingHub.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(1152))
            .duration(60 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                get(OrePrefixes.frameGt, Materials.Steel, 8),
                get(OrePrefixes.plateTriple, Materials.Steel, 16),
                get(OrePrefixes.circuit, Materials.LV, 4),
                ItemList.Electric_Piston_LV.get(12),
                get(OrePrefixes.pipeHuge, Materials.Steel, 4),
                get(OrePrefixes.plate, Materials.Obsidian, 32))
            .itemOutputs(GTSRItemList.SteamSingularityCompressor.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(2304))
            .duration(60 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTSRItemList.SteamEntangledSingularity.get(24),
                get(OrePrefixes.frameGt, Materials.Steel, 12),
                get(OrePrefixes.plateTriple, Materials.Steel, 24),
                get(OrePrefixes.circuit, Materials.LV, 8),
                ItemList.Electric_Piston_LV.get(12),
                get(OrePrefixes.rotor, Materials.Steel, 16))
            .itemOutputs(GTSRItemList.MegaSteamTurbineArray.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(1152))
            .duration(45 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        ItemStack paOut = get(GTSRItemList.KineticProcessingArray, 1);
        if (!hasNull(paOut)) {
            ItemStack[] inputs = filterNulls(
                GTSRItemList.SteamEntangledSingularity.get(8),
                get(OrePrefixes.frameGt, Materials.Steel, 4),
                get(OrePrefixes.circuit, Materials.LV, 4),
                ItemList.Electric_Motor_LV.get(4),
                ItemList.Conveyor_Module_LV.get(4),
                ItemList.Electric_Pump_LV.get(4));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(paOut)
                .fluidInputs(Materials.SolderingAlloy.getMolten(16 * INGOTS))
                .duration(60 * SECONDS)
                .eut(TierEU.RECIPE_LV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped KineticProcessingArray recipe - output is null");
        }

        ItemStack voidBorerOut = get(GTSRItemList.SingularityCrustSteamBorer, 1);
        if (!hasNull(voidBorerOut)) {
            ItemStack[] inputs = filterNulls(
                GTSRItemList.SteamEntangledSingularity.get(8),
                GTSRItemList.CrustSteamBorer.get(1),
                get(OrePrefixes.plateTriple, Materials.Steel, 8),
                get(OrePrefixes.frameGt, Materials.Steel, 2),
                get(OrePrefixes.circuit, Materials.LV, 2),
                ItemList.Electric_Piston_LV.get(4));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(voidBorerOut)
                .fluidInputs(Materials.SolderingAlloy.getMolten(4 * INGOTS))
                .duration(30 * SECONDS)
                .eut(TierEU.RECIPE_LV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped SingularityCrustSteamBorer recipe - output is null");
        }

        // --- 强化奇点枢纽升级芯片 ---
        ItemStack reinforcedChipOut = get(GTSRItemList.ReinforcedHubSingularityChip, 1);
        if (!hasNull(reinforcedChipOut)) {
            ItemStack[] inputs = filterNulls(
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                get(OrePrefixes.circuit, Materials.UV, 16),
                get(OrePrefixes.plateDense, Materials.Europium, 32),
                get(OrePrefixes.plateDense, WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial(), 64));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(reinforcedChipOut)
                    .fluidInputs(Materials.Radon.getGas(128000))
                    .duration(960 * SECONDS)
                    .eut(TierEU.RECIPE_LuV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped ReinforcedHubSingularityChip recipe - inputs contain null");
            }
        } else {
            warn("Skipped ReinforcedHubSingularityChip recipe - output is null");
        }

        // --- 超压蒸汽缓存节点 ---
        ItemStack overpressureCacheOut = get(GTSRItemList.OverpressureSteamCacheNode, 1);
        if (!hasNull(overpressureCacheOut)) {
            ItemStack[] inputs = filterNulls(
                GTSRItemList.SteamEntangledSingularity.get(32),
                GTSRItemList.ReinforcedSteamCacheNode.get(1),
                get(OrePrefixes.circuit, Materials.LuV, 4),
                ItemList.Sensor_LuV.get(2),
                get(OrePrefixes.screw, WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial(), 64),
                get(OrePrefixes.plateDense, WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial(), 16));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(overpressureCacheOut)
                    .duration(90 * SECONDS)
                    .eut(TierEU.RECIPE_LuV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped OverpressureSteamCacheNode recipe - inputs contain null");
            }
        } else {
            warn("Skipped OverpressureSteamCacheNode recipe - output is null");
        }

        // --- 超压枢纽存储单元 ---
        ItemStack overpressureHubOut = get(GTSRItemList.OverpressureHubStorageUnit, 1);
        if (!hasNull(overpressureHubOut)) {
            ItemStack[] inputs = filterNulls(
                GTSRItemList.SteamEntangledSingularity.get(16),
                GTSRItemList.ReinforcedHubStorageUnit.get(1),
                ItemList.Super_Tank_IV.get(1),
                get(OrePrefixes.screw, Materials.TungstenSteel, 16),
                get(OrePrefixes.plateDense, Materials.TungstenSteel, 4));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(overpressureHubOut)
                    .duration(80 * SECONDS)
                    .eut(TierEU.RECIPE_IV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped OverpressureHubStorageUnit recipe - inputs contain null");
            }
        } else {
            warn("Skipped OverpressureHubStorageUnit recipe - output is null");
        }

        // --- 超压巨型轮机阵列输入仓 ---
        ItemStack overpressureTurbineOut = get(GTSRItemList.OverpressureTurbineInputHatch, 1);
        if (!hasNull(overpressureTurbineOut)) {
            ItemStack[] inputs = filterNulls(
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.PressureSteamHatch.get(1),
                ItemList.Quantum_Tank_LV.get(1),
                ItemList.Electric_Pump_LuV.get(16),
                get(OrePrefixes.screw, WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial(), 16),
                get(OrePrefixes.plateDense, WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial(), 4));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(overpressureTurbineOut)
                    .fluidInputs(Materials.SolderingAlloy.getMolten(2880))
                    .duration(360 * SECONDS)
                    .eut(TierEU.RECIPE_LuV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped OverpressureTurbineInputHatch recipe - inputs contain null");
            }
        } else {
            warn("Skipped OverpressureTurbineInputHatch recipe - output is null");
        }

        log("Multiblock assembler recipes done.");
    }

    private static void registerHatchRecipes() {
        log("Registering hatch recipes...");

        ItemStack tumbagaPlate = MaterialsAlloy.TUMBAGA.getPlate(1);
        if (tumbagaPlate == null) {
            warn("MaterialsAlloy.TUMBAGA.getPlate(1) returned null! Falling back to RoseGold plate.");
            tumbagaPlate = get(OrePrefixes.plate, Materials.RoseGold, 1);
        }

        ItemStack bcTank = GTModHandler.getModItem("BuildCraft|Factory", "tankBlock", 1);
        if (bcTank == null) {
            bcTank = GTModHandler.getModItem("BuildCraft:Factory", "tankBlock", 1);
        }
        if (bcTank == null) {
            warn("BuildCraft tank is null! Some hatch recipes may fail.");
        }

        ItemStack gtSteamHatch = GregtechItemList.Hatch_Input_Steam.get(1);
        if (gtSteamHatch == null) {
            warn("GregtechItemList.Hatch_Input_Steam is null!");
        }

        GTModHandler.addCraftingRecipe(
            GTSRItemList.SteamInputHatchGeneric.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "plateBronze", 'B', tumbagaPlate, 'C', "plateTin", 'D', bcTank });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.SteamOutputHatchGeneric.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "plateBronze", 'B', "plateTin", 'C', tumbagaPlate, 'D', bcTank });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.SteamOutputHatch.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "plateBronze", 'B', "plateTin", 'C', tumbagaPlate, 'D',
                "pipeHugeBronze" });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.PressureSteamOutputHatch.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "screwSteel", 'B', "plateSteel", 'C', "plateSteel", 'D',
                GTSRItemList.SteamOutputHatch.get(1) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.PressureSteamHatch.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "screwSteel", 'B', "plateSteel", 'C', "plateSteel", 'D',
                gtSteamHatch });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.SteamCoolingHatch.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "plateBronze", 'B', "pipeNonupleCopper", 'C', "pipeNonupleCopper",
                'D', get(OrePrefixes.frameGt, Materials.Bronze, 1) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.PressureSteamCoolingHatch.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "screwSteel", 'B', "plateSteel", 'C', "plateSteel", 'D',
                GTSRItemList.SteamCoolingHatch.get(1) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.SteamHubInputHatch.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "screwBronze", 'B', "pipeHugeBronze", 'C', "pipeHugeBronze", 'D',
                GTSRItemList.SteamInputHatchGeneric.get(1) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.SteamHubOutputHatch.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "screwBronze", 'B', "pipeHugeBronze", 'C', "pipeHugeBronze", 'D',
                GTSRItemList.SteamOutputHatchGeneric.get(1) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.WaterHubInputHatch.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "screwBronze", 'B', "plateBronze", 'C', "pipeLargeBronze", 'D',
                GTSRItemList.SteamInputHatchGeneric.get(1) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.WaterHubOutputHatch.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "screwBronze", 'B', "plateBronze", 'C', "pipeLargeBronze", 'D',
                GTSRItemList.SteamOutputHatchGeneric.get(1) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.HubStorageUnit.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "screwBronze", 'B', "plateTripleBronze", 'C', "plateTripleBronze",
                'D', GregtechItemList.GTFluidTank_ULV.get(1) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.ReinforcedHubStorageUnit.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "screwSteel", 'B', "plateTripleSteel", 'C', "plateTripleBronze",
                'D', GTSRItemList.HubStorageUnit.get(1) });

        log("Hatch recipes done.");
    }

    private static void registerSingularityCompressorDisplayRecipe() {
        GTValues.RA.stdBuilder()
            .fluidInputs(Materials.Steam.getGas(600_000_000))
            .itemOutputs(GTSRItemList.SteamEntangledSingularity.get(1))
            .duration(100000)
            .eut(0)
            .addTo(steamSingularityCompressorRecipes);
        log("Singularity compressor display recipe done.");
    }

    private static void registerGeothermalBoilerDisplayRecipes() {
        // 无芯片配方
        GTValues.RA.stdBuilder()
            .fluidInputs(new FluidStack(net.minecraftforge.fluids.FluidRegistry.getFluid("lava"), 1000))
            .itemOutputs(
                new ItemStack(net.minecraft.init.Blocks.obsidian, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Ash, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Sulfur, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Tantalite, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Aluminiumoxide, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Copper, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Tin, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Silver, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Gold, 1))
            .outputChances(4500, 1500, 1000, 800, 600, 200, 100, 50, 35)
            .duration(0)
            .eut(0)
            .addTo(geothermalSteamBoilerRecipes);

        // 有芯片配方（额外产出金红石粉和白钨矿粉）
        GTValues.RA.stdBuilder()
            .fluidInputs(new FluidStack(net.minecraftforge.fluids.FluidRegistry.getFluid("lava"), 1000))
            .itemOutputs(
                new ItemStack(net.minecraft.init.Blocks.obsidian, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Ash, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Sulfur, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Tantalite, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Aluminiumoxide, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Copper, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Tin, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Silver, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Gold, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Phosphorus, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Rutile, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Scheelite, 1))
            .outputChances(4500, 1500, 1000, 800, 600, 200, 100, 50, 35, 10, 5, 2)
            .duration(0)
            .eut(0)
            .addTo(geothermalSteamBoilerRecipes);
        log("Geothermal boiler display recipes done.");
    }

    private static void registerFluidDrillDisplayRecipes() {
        // Water mode (Bronze: 200~2000 L/s, Steel: 200~8000 L/s)
        GTValues.RA.stdBuilder()
            .fluidOutputs(Materials.Water.getFluid(2000))
            .duration(0)
            .eut(0)
            .addTo(steamFluidDrillRecipes);

        // Distilled Water mode (10% efficiency, requires Steel tier)
        GTValues.RA.stdBuilder()
            .fluidOutputs(GTModHandler.getDistilledWater(200))
            .duration(0)
            .eut(0)
            .addTo(steamFluidDrillRecipes);

        // Brine mode (10% efficiency, requires Steel tier)
        GTValues.RA.stdBuilder()
            .fluidOutputs(Materials.SaltWater.getFluid(200))
            .duration(0)
            .eut(0)
            .addTo(steamFluidDrillRecipes);

        // Lava mode (0.5% efficiency, requires Steel tier)
        GTValues.RA.stdBuilder()
            .fluidOutputs(new FluidStack(net.minecraftforge.fluids.FluidRegistry.getFluid("lava"), 10))
            .duration(0)
            .eut(0)
            .addTo(steamFluidDrillRecipes);

        log("Fluid drill display recipes done.");
    }

    private static void registerGearSteamCompressorDisplayRecipes() {
        // Bronze tier: 6400 L/s steam → 1600 L/s superheated steam + 30 L/s distilled water
        GTValues.RA.stdBuilder()
            .fluidInputs(Materials.Steam.getGas(6400))
            .fluidOutputs(FluidRegistry.getFluidStack("ic2superheatedsteam", 1600), GTModHandler.getDistilledWater(30))
            .duration(0)
            .eut(0)
            .addTo(gearSteamCompressorRecipes);

        log("Gear steam compressor display recipes done.");
    }
}
