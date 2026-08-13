package com.miaokatze.gtsr.loader;

import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.airCompressorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.ammoniaPlantRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.atmosphericCentrifugeRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.criticalSingularityCompressorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.denseStateManipulatorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.gearSteamCompressorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.geothermalSteamBoilerRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.largeCokeOvenRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.siemensMartinRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.steamFluidDrillRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.steamSingularityEntanglerRecipes;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.recipe.RecipeMaps.implosionRecipes;
import static gregtech.api.util.GTRecipeBuilder.INGOTS;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.main.GTSteamReborn;

import bartworks.system.material.WerkstoffLoader;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
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

    // 【Bug2 加固】单个注册方法异常时记 ERROR（含方法名）后继续执行后续方法，
    // 杜绝单点异常穿透 run() 被 CommonProxy.postInit 捕获后导致后续全部配方静默团灭。
    private static void safeRegister(String name, Runnable r) {
        try {
            r.run();
            log("Registered " + name + " recipes successfully.");
        } catch (Exception e) {
            GTSteamReborn.LOG.error("[GTSR-Recipe] Failed to register " + name + " recipes: " + e.getMessage());
        }
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
        // 【Bug2 加固】全部注册调用经 safeRegister 独立 try-catch，单方法失败不再拖垮其余配方
        safeRegister("CokeOven", GTSRRecipeLoader::registerCokeOvenRecipes);
        safeRegister("SiemensMartin", GTSRRecipeLoader::registerSiemensMartinRecipes);
        safeRegister("Ammonia", GTSRRecipeLoader::registerAmmoniaRecipes);
        safeRegister("AirCompressor", GTSRRecipeLoader::registerAirCompressorRecipes);
        safeRegister("AtmosphericCentrifuge", GTSRRecipeLoader::registerAtmosphericCentrifugeRecipes);
        safeRegister("Chip", GTSRRecipeLoader::registerChipRecipes);
        safeRegister("Catalyst", GTSRRecipeLoader::registerCatalystRecipes);
        safeRegister("CacheNode", GTSRRecipeLoader::registerCacheNodeRecipes);
        safeRegister("TinyPlanetBlock", GTSRRecipeLoader::registerTinyPlanetRecipe); // 新增：Botania Tiny Planet
                                                                                     // Block（魔力环绕器）工作台配方
                                                                                     // （Botania 未加载时自动跳过）
        safeRegister("HubTerminal", GTSRRecipeLoader::registerHubTerminalRecipe); // 枢纽终端工作台配方：中心蒸汽纠缠奇点 + 8 钢板环绕
        safeRegister("NodeNBTClear", GTSRRecipeLoader::registerNodeNBTClearRecipes); // 节点清 NBT 无序配方：1 节点(无视 NBT) → 1
                                                                                     // 干净节点
        safeRegister("Node", GTSRRecipeLoader::registerNodeRecipes);
        safeRegister("MultiblockWorkbench", GTSRRecipeLoader::registerMultiblockWorkbenchRecipes);
        safeRegister("MultiblockAssembler", GTSRRecipeLoader::registerMultiblockAssemblerRecipes);
        safeRegister("Hatch", GTSRRecipeLoader::registerHatchRecipes);
        safeRegister("DistilledWaterHatch", GTSRRecipeLoader::registerDistilledWaterHatchRecipe); // 蒸馏水仓：继承蓄水仓配方 +
                                                                                                  // 3组蒸汽纠缠奇点
        safeRegister("GeothermalBoilerDisplay", GTSRRecipeLoader::registerGeothermalBoilerDisplayRecipes);
        safeRegister("FluidDrillDisplay", GTSRRecipeLoader::registerFluidDrillDisplayRecipes);
        safeRegister("GearSteamCompressorDisplay", GTSRRecipeLoader::registerGearSteamCompressorDisplayRecipes);
        safeRegister(
            "SteamSingularityEntanglerDisplay",
            GTSRRecipeLoader::registerSteamSingularityEntanglerDisplayRecipes);
        safeRegister(
            "CriticalSingularityCompressorDisplay",
            GTSRRecipeLoader::registerCriticalSingularityCompressorDisplayRecipes);
        safeRegister("Implosion", GTSRRecipeLoader::registerImplosionRecipes); // 聚爆压缩机：2 临界奇点 → 8 普通奇点
        safeRegister("DenseStateManipulatorDisplay", GTSRRecipeLoader::registerDenseStateManipulatorDisplayRecipes);
        safeRegister("ReinforcedBrickBlastFurnace", GTSRRecipeLoader::registerReinforcedBrickBlastFurnaceRecipe);
        safeRegister("LegacyConversion", GTSRRecipeLoader::registerLegacyConversionRecipes);
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
            .itemInputs(Materials.Iron.getIngots(2), Materials.Coal.getGems(2))
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getDust(2), Materials.Coal.getGems(2))
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getIngots(2), Materials.Coal.getDust(2))
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getDust(2), Materials.Coal.getDust(2))
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.Iron.getIngots(2),
                OreDictionary.getOres("fuelCoke")
                    .get(0)
                    .copy())
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .outputChances(10000, 7000)
            .duration(1200 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.Iron.getDust(2),
                OreDictionary.getOres("fuelCoke")
                    .get(0)
                    .copy())
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .outputChances(10000, 7000)
            .duration(1200 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getIngots(2), Materials.Carbon.getDust(1))
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .outputChances(10000, 3000)
            .duration(800 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getDust(2), Materials.Carbon.getDust(1))
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
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
            ItemStack hubBoard = new ItemStack(GameRegistry.findItem("gregtech", "gt.metaitem.01"), 1, 19305);
            ItemStack hubCircuit = get(OrePrefixes.circuit, Materials.LV, 2); // 任意 LV 电路（OreDict，模板 dreamcraft
                                                                              // CircuitLV 的替代）
            ItemStack hubSingularity = GTSRItemList.SteamEntangledSingularity.get(1);
            if (!hasNull(hubBoard, hubCircuit, hubSingularity)) {
                GameRegistry
                    .addShapedRecipe(hubOut, "AAA", "BCB", "AAA", 'A', hubBoard, 'B', hubCircuit, 'C', hubSingularity);
            } else {
                warn("Skipped HubSingularityChip crafting recipe - inputs contain null");
            }
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

    /**
     * 注册 Botania Tiny Planet 工作台合成配方。
     * <p>
     * 配方形状（用户需求：LV 电路板居中，8 个蒸汽纠缠奇点绕一圈）：
     * 
     * <pre>
     *   S S S
     *   S C S
     *   S S S
     * </pre>
     * 
     * 其中：
     * - C = LV 电路板（任意种类，通过 OrePrefixes.circuit.get(Materials.LV) 匹配 OreDict）
     * - S = 蒸汽纠缠奇点（GTSRItemList.SteamEntangledSingularity）
     * 产物：Botania:tinyPlanetBlock（魔力环绕器）。原版配方为行星项链（tinyPlanet）+ 8 石头 = 魔力环绕器；
     * 本配方直接产出方块，方便检索。
     * <p>
     * 安全处理：Botania 是 GTNH 必装模组，但出于代码健壮性仍用 Loader.isModLoaded 判断。
     * 若 Botania 未加载、产物或材料为 null，则跳过配方注册并记录警告日志。
     */
    private static void registerTinyPlanetRecipe() {
        log("Registering Tiny Planet crafting recipe...");

        // 1. 运行时检测 Botania 是否加载（与 MTEVoidCrustSteamBorer.isPluginLoaded 风格一致）
        if (!Loader.isModLoaded("Botania")) {
            log("Botania not loaded, skipping Tiny Planet recipe.");
            return;
        }

        // 2. 获取产物：Botania:tinyPlanetBlock（魔力环绕器方块，注册名为小写驼峰式；原版配方为行星项链 tinyPlanet + 8 石头 = 魔力环绕器）
        ItemStack tinyPlanetBlock = GTModHandler.getModItem("Botania", "tinyPlanetBlock", 1);
        if (tinyPlanetBlock == null) {
            warn("Botania:tinyPlanetBlock item is null, skipping Tiny Planet recipe!");
            return;
        }

        // 3. 获取中心材料：LV 电路板（OrePrefixes.circuit + Materials.LV 匹配任意种类 LV 电路板）
        ItemStack lvCircuit = get(OrePrefixes.circuit, Materials.LV, 1);
        if (lvCircuit == null) {
            warn("LV circuit is null, skipping Tiny Planet recipe!");
            return;
        }

        // 4. 获取环绕材料：蒸汽纠缠奇点
        ItemStack singularity = GTSRItemList.SteamEntangledSingularity.get(1);
        if (singularity == null) {
            warn("SteamEntangledSingularity is null, skipping Tiny Planet recipe!");
            return;
        }

        // 5. 注册工作台配方：SSS / SCS / SSS（C 居中，S 绕一圈）
        GTModHandler.addCraftingRecipe(
            tinyPlanetBlock,
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "SSS", "SCS", "SSS", 'S', singularity, 'C', lvCircuit });

        log("Tiny Planet recipe registered.");
    }

    /**
     * 注册枢纽终端工作台合成配方。
     * <p>
     * 配方形状（中心 1 个蒸汽纠缠奇点，8 个钢板环绕一圈）：
     *
     * <pre>
     *   S S S
     *   S C S
     *   S S S
     * </pre>
     *
     * 其中：
     * - C = 蒸汽纠缠奇点（GTSRItemList.SteamEntangledSingularity）
     * - S = 钢板（OrePrefixes.plate + Materials.Steel，匹配矿物词典）
     * 产物：枢纽终端（GTSRItemList.HubTerminal）
     * <p>
     * 安全处理：沿用本类 hasNull/warn 防御模式，任一材料为 null 时跳过注册并记录警告。
     */
    private static void registerHubTerminalRecipe() {
        log("Registering Hub Terminal crafting recipe...");

        ItemStack terminalOut = get(GTSRItemList.HubTerminal, 1);
        ItemStack steelPlate = get(OrePrefixes.plate, Materials.Steel, 1);
        ItemStack singularity = get(GTSRItemList.SteamEntangledSingularity, 1);

        if (hasNull(terminalOut, steelPlate, singularity)) {
            warn("Skipped HubTerminal recipe - output or inputs are null");
            return;
        }

        GTModHandler.addCraftingRecipe(
            terminalOut,
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "SSS", "SCS", "SSS", 'S', steelPlate, 'C', singularity });

        log("Hub Terminal recipe registered.");
    }

    /**
     * 节点「洗白」无序配方：1 个节点(无视 NBT) → 1 个干净节点，帮助玩家清除节点上的 NBT 数据
     * （绑定信息/自定义名/奇点消耗标记等）。vanilla 无序配方匹配不校验 NBT，输出为全新干净栈。
     * 覆盖全部 6 种节点：采矿/钻井节点 + 4 种缓存节点。
     * 注意：会同时清掉 gtsr.singularity_consumed 标记，属预期行为（允许玩家重新绑定，计划已确认）。
     */
    private static void registerNodeNBTClearRecipes() {
        log("Registering node NBT-clearing shapeless recipes...");

        GTSRItemList[] nodeItems = { GTSRItemList.SingularityMinerNode, GTSRItemList.SingularityDrillingNode,
            GTSRItemList.SteamCacheNode, GTSRItemList.ReinforcedSteamCacheNode, GTSRItemList.OverpressureSteamCacheNode,
            GTSRItemList.WaterCacheNode };

        for (GTSRItemList node : nodeItems) {
            ItemStack clean = get(node, 1);
            if (hasNull(clean)) {
                warn("Skipped NBT-clear recipe for " + node.name() + " - stack is null");
                continue;
            }
            GTModHandler.addShapelessCraftingRecipe(clean, new Object[] { clean });
        }

        log("Node NBT-clearing recipes registered: " + nodeItems.length);
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

        // 点2修复：RC 焦炉在 GTNH 2.9.0 已禁用，改用 GT5U 新添加的 CokeOvenController（焦炉控制器）
        GTModHandler.addCraftingRecipe(
            GTSRItemList.LargeCokeOven.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', copperPlatedBrick, 'B', brickBlock, 'C', efrBlastFurnace, 'D',
                ItemList.CokeOvenController.get(1) });

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
            new Object[] { "ABA", "CDC", "EBE", 'A', copperPlatedBrick, 'B', "pipeHugeBronze", 'C',
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
            .itemOutputs(GTSRItemList.SteamSingularityEntangler.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(2304))
            .duration(60 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        // --- 临界蒸汽纠缠奇点稳定装置 (GTUDK 配方：替换原 LV 配方，IV 级) ---
        ItemStack cscOut = get(GTSRItemList.CriticalSingularityCompressor, 1);
        if (!hasNull(cscOut)) {
            ItemStack[] cscInputs = filterNulls(
                new ItemStack(GameRegistry.findItem("gregtech", "gt.blockcasings8"), 64, 6),
                new ItemStack(GameRegistry.findItem("gregtech", "bw.frames"), 64, 88),
                new ItemStack(GameRegistry.findItem("gregtech", "gt.blockframes"), 32, 70),
                get(OrePrefixes.circuit, Materials.UV, 16),
                new ItemStack(GameRegistry.findItem("gregtech", "gt.blockcasings4"), 32, 7),
                new ItemStack(GameRegistry.findItem("gregtech", "gt.blockcasings4"), 32, 6),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64));
            if (!hasNull(cscInputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(cscInputs)
                    .itemOutputs(cscOut)
                    .fluidInputs(FluidRegistry.getFluidStack("molten.indalloy140", 32000))
                    .duration(4800)
                    .eut(32768)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped CriticalSingularityCompressor recipe - inputs contain null");
            }
        } else {
            warn("Skipped CriticalSingularityCompressor recipe - output is null");
        }

        // --- 致密态操纵装置 (GTUDK 配方：替换原 LV 配方，IV 级) ---
        ItemStack dsmOut = get(GTSRItemList.DenseStateManipulator, 1);
        if (!hasNull(dsmOut)) {
            ItemStack[] dsmInputs = filterNulls(
                new ItemStack(GameRegistry.findItem("gregtech", "gt.blockcasings8"), 64, 6),
                new ItemStack(GameRegistry.findItem("gregtech", "bw.frames"), 64, 88),
                new ItemStack(GameRegistry.findItem("bartworks", "BW_TieredGlass"), 64, 3),
                get(OrePrefixes.circuit, Materials.UV, 16),
                new ItemStack(GameRegistry.findItem("gregtech", "gt.blockcasings4"), 64, 7),
                new ItemStack(GameRegistry.findItem("gregtech", "gt.metaitem.01"), 16, 32645),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64));
            if (!hasNull(dsmInputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(dsmInputs)
                    .itemOutputs(dsmOut)
                    .fluidInputs(FluidRegistry.getFluidStack("molten.indalloy140", 32000))
                    .duration(4800)
                    .eut(32768)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped DenseStateManipulator recipe - inputs contain null");
            }
        } else {
            warn("Skipped DenseStateManipulator recipe - output is null");
        }

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

        ItemStack voidBorerOut = get(GTSRItemList.CrustMatterAggregator, 1);
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
            warn("Skipped CrustMatterAggregator recipe - output is null");
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

        // --- 蒸汽轮机循环超限芯片（GTUDK 模板：7 项物料，IV 级 12000t/131072EU/t）---
        ItemStack turbineChipOut = get(GTSRItemList.SteamTurbineCycleOverlimitChip, 1);
        if (!hasNull(turbineChipOut)) {
            // 注意：不用 filterNulls —— 任一物料缺失（如 GoodGenerator 未安装）应整体跳过，而不是降配注册
            ItemStack[] inputs = { get(OrePrefixes.circuit, Materials.UV, 64), // 模板 gt.metaitem.01:22070（UV
                                                                               // 电路）→ 任意 UV 电路（OreDict）
                new ItemStack(GameRegistry.findItem("GoodGenerator", "compactFusionCoil"), 32, 2),
                GTSRItemList.CriticalSteamEntangledSingularity.get(64),
                new ItemStack(GameRegistry.findItem("gregtech", "gt.metaitem.01"), 32, 32677), // 力场发生器 VIII（GT5U lang
                                                                                               // 已确认）
                new ItemStack(GameRegistry.findItem("gregtech", "gt.metaitem.01"), 64, 32616) }; // 电动泵 ZPM（GT5U lang
                                                                                                 // 已确认）
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(turbineChipOut)
                    .fluidInputs(FluidRegistry.getFluidStack("oganesson", 1000))
                    .duration(12000)
                    .eut(131072)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped SteamTurbineCycleOverlimitChip recipe - inputs contain null");
            }
        } else {
            warn("Skipped SteamTurbineCycleOverlimitChip recipe - output is null");
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

        // 蒸汽输入仓/输出仓合成表的 'D' 位置原为 BC 储罐（BuildCraft tankBlock），
        // 现替换为 MC 铁桶（Items.bucket），降低对 BuildCraft 的硬依赖。
        ItemStack ironBucket = new ItemStack(Items.bucket);

        ItemStack gtSteamHatch = GregtechItemList.Hatch_Input_Steam.get(1);
        if (gtSteamHatch == null) {
            warn("GregtechItemList.Hatch_Input_Steam is null!");
        }

        GTModHandler.addCraftingRecipe(
            GTSRItemList.SteamInputHatchGeneric.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "plateBronze", 'B', tumbagaPlate, 'C', "plateTin", 'D',
                ironBucket });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.SteamOutputHatchGeneric.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', "plateBronze", 'B', "plateTin", 'C', tumbagaPlate, 'D',
                ironBucket });

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

        // 巨型空气输入仓：装配机配方 = 64×HV输入仓 + 1×蒸汽纠缠奇点（v1.8.5 起不再用流体，
        // 彻底规避 Air/NetherAir 流体/单元在运行时返回 null 导致注册异常的历史问题，见 v1.8.4 Bug2）
        ItemStack megaAirHatchOut = GTSRItemList.MegaAirInputHatch.get(1);
        ItemStack megaAirHatchSingularity = GTSRItemList.SteamEntangledSingularity.get(1);
        if (megaAirHatchOut != null && megaAirHatchSingularity != null) {
            GTValues.RA.stdBuilder()
                .itemInputs(ItemList.Hatch_Input_HV.get(64), megaAirHatchSingularity)
                .circuit(17)
                .itemOutputs(megaAirHatchOut)
                .duration(15 * SECONDS)
                .eut(TierEU.RECIPE_HV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped MegaAirInputHatch assembler recipe - output or singularity is null");
        }

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

    /**
     * 蒸馏水仓配方：蓄水仓（Reservoir Hatch）+ 16 个蒸汽纠缠奇点，
     * 电功率 24 EU/t，耗时 1600 tick。
     */
    private static void registerDistilledWaterHatchRecipe() {
        log("Registering Distilled Water Hatch recipe...");

        ItemStack distilledOut = get(GTSRItemList.DistilledWaterHatch, 1);
        if (hasNull(distilledOut)) {
            warn("Skipped DistilledWaterHatch recipe - output is null");
            return;
        }

        ItemStack[] inputs = filterNulls(
            GregtechItemList.Hatch_Reservoir.get(1),
            get(GTSRItemList.SteamEntangledSingularity, 16));
        if (hasNull(inputs)) {
            warn("Skipped DistilledWaterHatch recipe - inputs contain null");
            return;
        }

        GTValues.RA.stdBuilder()
            .itemInputs(inputs)
            .itemOutputs(distilledOut)
            .duration(1600)
            .eut(24)
            .addTo(assemblerRecipes);
        log("Distilled Water Hatch recipe registered.");
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
        // 水模式：青铜 200~2000 L/s，钢 200~8000 L/s
        GTValues.RA.stdBuilder()
            .fluidOutputs(Materials.Water.getFluid(2000))
            .duration(20)
            .eut(0)
            .addTo(steamFluidDrillRecipes);

        // 蒸馏水模式：水模式的20%（需钢级）
        GTValues.RA.stdBuilder()
            .fluidOutputs(GTModHandler.getDistilledWater(400))
            .duration(20)
            .eut(0)
            .addTo(steamFluidDrillRecipes);

        // 盐水模式：水模式的10%（需钢级）
        GTValues.RA.stdBuilder()
            .fluidOutputs(Materials.SaltWater.getFluid(200))
            .duration(20)
            .eut(0)
            .addTo(steamFluidDrillRecipes);

        // 岩浆模式：其他维度0.5%，下界5%（需钢级）
        GTValues.RA.stdBuilder()
            .fluidOutputs(new FluidStack(net.minecraftforge.fluids.FluidRegistry.getFluid("lava"), 10))
            .duration(20)
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

    // 蒸汽奇点纠缠装置展示配方：3 条（蒸汽/过热蒸汽/超临界蒸汽 → 蒸汽纠缠奇点）。
    // 仅用于 NEI 展示，实际产出由机器 checkProcessing() 计算；流体不写量。
    private static void registerSteamSingularityEntanglerDisplayRecipes() {
        ItemStack output = GTSRItemList.SteamEntangledSingularity.get(1);
        if (output == null) {
            warn("Skipped SteamSingularityEntangler display recipes - output is null");
            return;
        }
        for (String fluidName : new String[] { "steam", "ic2superheatedsteam", "supercriticalsteam" }) {
            FluidStack input = FluidRegistry.getFluidStack(fluidName, 1);
            if (input == null) {
                warn("Skipped SSE display recipe for fluid " + fluidName + " - fluid missing");
                continue;
            }
            GTValues.RA.stdBuilder()
                .fluidInputs(input)
                .itemOutputs(output)
                .duration(20)
                .eut(0)
                .addTo(steamSingularityEntanglerRecipes);
        }
        log("Steam singularity entangler display recipes done.");
    }

    // 临界纠缠奇点稳定装置展示配方：3 条（致密态蒸汽/过热/超临界 → 临界蒸汽纠缠奇点）。
    private static void registerCriticalSingularityCompressorDisplayRecipes() {
        ItemStack output = GTSRItemList.CriticalSteamEntangledSingularity.get(1);
        if (output == null) {
            warn("Skipped CriticalSingularityCompressor display recipes - output is null");
            return;
        }
        for (String fluidName : new String[] { "densesteam", "densesuperheatedsteam", "densesupercriticalsteam" }) {
            FluidStack input = FluidRegistry.getFluidStack(fluidName, 1);
            if (input == null) {
                warn("Skipped CSC display recipe for fluid " + fluidName + " - fluid missing");
                continue;
            }
            GTValues.RA.stdBuilder()
                .fluidInputs(input)
                .itemOutputs(output)
                .duration(20)
                .eut(0)
                .addTo(criticalSingularityCompressorRecipes);
        }
        log("Critical singularity compressor display recipes done.");
    }

    // 聚爆压缩机配方：2 临界蒸汽纠缠奇点 → 8 普通蒸汽纠缠奇点（duration 20、eut 30，不需要爆炸物）。
    // 使用 2 个 itemInput 使 GT5U implosionRecipes 的 recipeEmitter 走"原样注册"路径，不派生 ITNT/炸药变体；
    // 若只放 1 个输入会强制派生爆炸物变体，且 validateOutputCount 会拒绝 5 个以上的输出。
    private static void registerImplosionRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTSRItemList.CriticalSteamEntangledSingularity.get(1),
                GTSRItemList.CriticalSteamEntangledSingularity.get(1))
            .itemOutputs(GTSRItemList.SteamEntangledSingularity.get(8))
            .duration(20)
            .eut(30)
            .addTo(implosionRecipes);
    }

    // 致密态操纵装置展示配方：6 条（3 压缩 + 3 解压）。
    // 压缩：1000L 普通蒸汽 → 1L 致密态蒸汽；解压：1L 致密态蒸汽 → 1000L 普通蒸汽。
    private static void registerDenseStateManipulatorDisplayRecipes() {
        String[] normal = { "steam", "ic2superheatedsteam", "supercriticalsteam" };
        String[] dense = { "densesteam", "densesuperheatedsteam", "densesupercriticalsteam" };
        for (int i = 0; i < normal.length; i++) {
            FluidStack normalIn = FluidRegistry.getFluidStack(normal[i], 1000);
            FluidStack denseOut = FluidRegistry.getFluidStack(dense[i], 1);
            if (normalIn != null && denseOut != null) {
                GTValues.RA.stdBuilder()
                    .fluidInputs(normalIn)
                    .fluidOutputs(denseOut)
                    .duration(20)
                    .eut(0)
                    .addTo(denseStateManipulatorRecipes);
            } else {
                warn("Skipped DSM compress display recipe for grade " + i);
            }
            FluidStack denseIn = FluidRegistry.getFluidStack(dense[i], 1);
            FluidStack normalOut = FluidRegistry.getFluidStack(normal[i], 1000);
            if (denseIn != null && normalOut != null) {
                GTValues.RA.stdBuilder()
                    .fluidInputs(denseIn)
                    .fluidOutputs(normalOut)
                    .duration(20)
                    .eut(0)
                    .addTo(denseStateManipulatorRecipes);
            } else {
                warn("Skipped DSM decompress display recipe for grade " + i);
            }
        }
        log("Dense state manipulator display recipes done.");
    }

    /**
     * 注册加固砖高炉控制器的工作台合成配方。
     * <p>
     * 配方：GT5U 砖高炉控制器居中，周围 8 格钢板。
     * 若 GT5U 砖高炉控制器或钢板为 null，则跳过并记录警告。
     */
    private static void registerReinforcedBrickBlastFurnaceRecipe() {
        log("Registering Reinforced Brick Blast Furnace crafting recipe...");

        ItemStack brickBlastFurnace = get(ItemList.Machine_Bricked_BlastFurnace, 1);
        ItemStack steelPlate = get(OrePrefixes.plate, Materials.Steel, 1);
        ItemStack output = get(GTSRItemList.ReinforcedBrickBlastFurnace, 1);

        if (hasNull(output, brickBlastFurnace, steelPlate)) {
            warn("Skipped ReinforcedBrickBlastFurnace recipe - output or inputs are null");
            return;
        }

        GTModHandler.addCraftingRecipe(
            output,
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "SSS", "SBS", "SSS", 'S', steelPlate, 'B', brickBlastFurnace });

        log("Reinforced Brick Blast Furnace recipe registered.");
    }

    // 【Meta 迁移】旧控制器 → 新控制器 工作台无序转换配方（仅三台更改了结构的 [OLD] 机器：
    // 超压太阳能锅炉阵列 / 蒸汽奇点压缩机 / 奇点地壳钻探机；其余 OLD 机器采用 MTELegacyConverter
    // 映射（放置即自动转换），无需转换配方）
    private static void registerLegacyConversionRecipes() {
        log("Registering old -> new controller conversion recipes...");

        MetaTileEntityID[] structuralChanged = { MetaTileEntityID.LARGE_SOLAR_OVERPRESSURE_ARRAY,
            MetaTileEntityID.STEAM_SINGULARITY_ENTANGLER, MetaTileEntityID.SINGULARITY_CRUST_STEAM_BORER, };
        for (MetaTileEntityID id : structuralChanged) {
            GTModHandler.addShapelessCraftingRecipe(
                new ItemStack(GregTechAPI.sBlockMachines, 1, id.ID),
                new Object[] { new ItemStack(GregTechAPI.sBlockMachines, 1, id.OLD_ID) });
        }

        log("Old -> new conversion recipes registered: " + structuralChanged.length);
    }
}
