package com.miaokatze.gtsr.loader.recipes;

import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.filterNulls;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.get;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.hasNull;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.log;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.warn;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTModHandler;
import gtPlusPlus.core.material.MaterialsAlloy;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;

/**
 * 仓室配方（SR-A03 组四，自 GTSRRecipeLoader 门面原样迁出）：14 个输入/输出/冷却/枢纽仓与
 * 巨型空气输入仓工作台+组装机配方、蒸馏水仓配方。方法体逐字未动；注册顺序与错误隔离
 * 由门面 run() 派发表 + safeRegister 单点决定。
 */
public final class HatchRecipes {

    private HatchRecipes() {}

    public static void registerHatchRecipes() {
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
    public static void registerDistilledWaterHatchRecipe() {
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

}
