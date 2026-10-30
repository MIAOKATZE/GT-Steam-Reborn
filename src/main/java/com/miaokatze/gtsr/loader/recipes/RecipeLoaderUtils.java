package com.miaokatze.gtsr.loader.recipes;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.main.GTSteamReborn;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

/**
 * 配方注册公共工具（SR-A03：自 GTSRRecipeLoader 门面上提，[GTSR-Recipe] 日志前缀与
 * 物料 null 防御单源化）。log/warn 供门面 safeRegister 与 recipes/ 各域类共用；
 * get/hasNull 统一检查必需物料；不得先丢弃缺失输入，否则会注册降价配方。
 */
public final class RecipeLoaderUtils {

    private RecipeLoaderUtils() {}

    public static void log(String msg) {
        GTSteamReborn.LOG.info("[GTSR-Recipe] " + msg);
    }

    public static void warn(String msg) {
        GTSteamReborn.LOG.warn("[GTSR-Recipe] " + msg);
    }

    public static ItemStack get(OrePrefixes prefix, Object mat, long amount) {
        ItemStack stack = GTOreDictUnificator.get(prefix, mat, amount);
        if (stack == null) {
            warn(prefix + " + " + mat + " returned null!");
        }
        return stack;
    }

    public static ItemStack get(ItemList item, int amount) {
        ItemStack stack = item.get(amount);
        if (stack == null) {
            warn("ItemList." + item.name() + " returned null!");
        }
        return stack;
    }

    public static ItemStack get(GTSRItemList item, int amount) {
        ItemStack stack = item.get(amount);
        if (stack == null) {
            warn("GTSRItemList." + item.name() + " returned null!");
        }
        return stack;
    }

    public static boolean hasNull(ItemStack... stacks) {
        if (stacks == null) return true;
        for (ItemStack s : stacks) {
            // A missing registry item can still be wrapped in a non-null ItemStack.
            // Zero-size integrated circuits are legitimate non-consumed selectors.
            if (s == null || s.getItem() == null) return true;
        }
        return false;
    }

    /** Legacy helper retained for binary compatibility; never use for mandatory recipe inputs. */
    @Deprecated
    public static ItemStack[] filterNulls(ItemStack... stacks) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s != null) list.add(s);
        }
        return list.toArray(new ItemStack[0]);
    }
}
