package com.miaokatze.gtsr.common.util;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;

/** 矿石粉碎产物工具（v1.10.54）：研磨机配方主产物查询 + 加工形态判定，聚合器/采矿节点共用。 */
public final class OreCrushedUtil {

    private OreCrushedUtil() {}

    /**
     * 查询指定物品的研磨机（macerator）配方主产物（机会恒 10000，无需掷随机）；无配方或异常返回 null。
     * 依据 GT5U 5.09.54：粗矿/矿块研磨主产物 = crushed×(2×mOreMultiplier)（红石×10、冰晶石×8 等特殊矿自动正确）。
     * 生产级先例：MTEOreDrillingPlantBase.getOutputByDrops 每挖一块矿查一次（findRecipeQuery().caching(true)）。
     */
    public static ItemStack getCrushedProduct(ItemStack item) {
        if (item == null || item.getItem() == null) return null;
        try {
            GTRecipe recipe = RecipeMaps.maceratorRecipes.findRecipeQuery()
                .caching(true)
                .items(item)
                .find();
            if (recipe != null && recipe.mOutputs.length > 0 && recipe.mOutputs[0] != null) {
                return recipe.mOutputs[0].copy();
            }
        } catch (Throwable ignored) {
            // 查询失败按无配方处理，调用方走回退
        }
        return null;
    }

    /** 已是加工形态（粉碎/粉/宝石等）不再转换（镜像采矿节点 applyCrushedMode 的 skip 前缀表）。 */
    public static boolean isProcessedForm(ItemStack item) {
        if (item == null || item.getItem() == null) return false;
        ItemData data = GTOreDictUnificator.getItemData(item);
        return data != null && data.mPrefix != null && isProcessedForm(data.mPrefix);
    }

    /** 加工形态前缀判定（采矿节点已有前缀时直接调用）。 */
    public static boolean isProcessedForm(OrePrefixes prefix) {
        return prefix == OrePrefixes.crushed || prefix == OrePrefixes.crushedCentrifuged
            || prefix == OrePrefixes.crushedPurified
            || prefix == OrePrefixes.dustImpure
            || prefix == OrePrefixes.dustPure
            || prefix == OrePrefixes.dustRefined
            || prefix == OrePrefixes.dust
            || prefix == OrePrefixes.gem
            || prefix == OrePrefixes.gemChipped
            || prefix == OrePrefixes.gemExquisite
            || prefix == OrePrefixes.gemFlawed
            || prefix == OrePrefixes.gemFlawless;
    }
}
