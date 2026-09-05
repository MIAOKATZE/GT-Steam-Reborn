package com.miaokatze.gtsr.loader.recipes;

import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.get;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.hasNull;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.log;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.warn;
import static gregtech.api.recipe.RecipeMaps.implosionRecipes;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;

/**
 * 杂项配方（SR-A03 组六，自 GTSRRecipeLoader 门面原样迁出）：聚爆压缩机（2 临界奇点 →
 * 8 普通奇点）/加固砖高炉控制器。方法体逐字未动；
 * 注册顺序与错误隔离由门面 run() 派发表 + safeRegister 单点决定。
 */
public final class MiscRecipes {

    private MiscRecipes() {}

    // 聚爆压缩机配方：2 临界蒸汽纠缠奇点 → 8 普通蒸汽纠缠奇点（duration 20、eut 30，不需要爆炸物）。
    // 使用 2 个 itemInput 使 GT5U implosionRecipes 的 recipeEmitter 走"原样注册"路径，不派生 ITNT/炸药变体；
    // 若只放 1 个输入会强制派生爆炸物变体，且 validateOutputCount 会拒绝 5 个以上的输出。
    public static void registerImplosionRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTSRItemList.CriticalSteamEntangledSingularity.get(1),
                GTSRItemList.CriticalSteamEntangledSingularity.get(1))
            .itemOutputs(GTSRItemList.SteamEntangledSingularity.get(8))
            .duration(20)
            .eut(30)
            .addTo(implosionRecipes);
    }

    /**
     * 注册加固砖高炉控制器的工作台合成配方。
     * <p>
     * 配方：GT5U 砖高炉控制器居中，周围 8 格钢板。
     * 若 GT5U 砖高炉控制器或钢板为 null，则跳过并记录警告。
     */
    public static void registerReinforcedBrickBlastFurnaceRecipe() {
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

}
