package com.miaokatze.gtsr.loader;

import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.criticalSingularityCompressorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.denseStateManipulatorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.gearSteamCompressorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.geothermalSteamBoilerRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.steamFluidDrillRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.steamSingularityEntanglerRecipes;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.filterNulls;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.get;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.hasNull;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.log;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.warn;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.recipe.RecipeMaps.implosionRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.loader.recipes.MultiblockMachineRecipes;
import com.miaokatze.gtsr.loader.recipes.NodeAndHubRecipes;
import com.miaokatze.gtsr.loader.recipes.ProcessingMachineRecipes;
import com.miaokatze.gtsr.main.GTSteamReborn;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gtPlusPlus.core.material.MaterialsAlloy;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;

public class GTSRRecipeLoader implements Runnable {

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

    @Override
    public void run() {
        // 【Bug2 加固】全部注册调用经 safeRegister 独立 try-catch，单方法失败不再拖垮其余配方
        safeRegister("CokeOven", ProcessingMachineRecipes::registerCokeOvenRecipes);
        safeRegister("SiemensMartin", ProcessingMachineRecipes::registerSiemensMartinRecipes);
        safeRegister("Ammonia", ProcessingMachineRecipes::registerAmmoniaRecipes);
        safeRegister("AirCompressor", ProcessingMachineRecipes::registerAirCompressorRecipes);
        safeRegister("AtmosphericCentrifuge", ProcessingMachineRecipes::registerAtmosphericCentrifugeRecipes);
        safeRegister("Chip", ProcessingMachineRecipes::registerChipRecipes);
        safeRegister("Catalyst", ProcessingMachineRecipes::registerCatalystRecipes);
        safeRegister("CacheNode", NodeAndHubRecipes::registerCacheNodeRecipes);
        safeRegister("TinyPlanetBlock", NodeAndHubRecipes::registerTinyPlanetRecipe); // 新增：Botania Tiny Planet
                                                                                      // Block（魔力环绕器）工作台配方
                                                                                      // （Botania 未加载时自动跳过）
        safeRegister("HubTerminal", NodeAndHubRecipes::registerHubTerminalRecipe); // 枢纽终端工作台配方：中心蒸汽纠缠奇点 + 8 钢板环绕
        safeRegister("NodeNBTClear", NodeAndHubRecipes::registerNodeNBTClearRecipes); // 节点清 NBT 无序配方：1 节点(无视 NBT) → 1
                                                                                      // 干净节点
        safeRegister("Node", NodeAndHubRecipes::registerNodeRecipes);
        safeRegister("MultiblockWorkbench", MultiblockMachineRecipes::registerMultiblockWorkbenchRecipes);
        safeRegister("MultiblockAssembler", MultiblockMachineRecipes::registerMultiblockAssemblerRecipes);
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
