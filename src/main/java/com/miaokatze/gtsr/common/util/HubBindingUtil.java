package com.miaokatze.gtsr.common.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

/**
 * 枢纽绑定流静态助手（O2-12）：收敛「从玩家主物品栏消耗蒸汽纠缠奇点」的逐字双份
 * （原 MTESteamHubArray 与 MTESingularityDrillingHub 各一份，diff 为空亲证）。
 * 绑定流主体（onRightclick 模板 / bindOne / bindWhole / 成本钩子）随 MTEHubArrayBase 基类模板上提，
 * 本类只承载无状态的背包消耗段，供基类与钻井枢纽共用。
 */
public final class HubBindingUtil {

    private HubBindingUtil() {}

    /**
     * 从玩家主物品栏消耗指定数量个蒸汽纠缠奇点（背包总量不足时不消耗并返回 false）。
     *
     * @return 是否成功消耗
     */
    public static boolean consumeSteamEntangledSingularities(EntityPlayer player, int amount) {
        int found = 0;
        for (ItemStack invStack : player.inventory.mainInventory) {
            if (invStack != null && GTSRItemList.SteamEntangledSingularity.isStackEqual(invStack, true, true)) {
                found += invStack.stackSize;
            }
        }
        if (found < amount) return false;
        int remaining = amount;
        for (int i = 0; i < player.inventory.mainInventory.length && remaining > 0; i++) {
            ItemStack invStack = player.inventory.mainInventory[i];
            if (invStack != null && GTSRItemList.SteamEntangledSingularity.isStackEqual(invStack, true, true)) {
                int toConsume = Math.min(remaining, invStack.stackSize);
                invStack.stackSize -= toConsume;
                remaining -= toConsume;
                if (invStack.stackSize <= 0) {
                    player.inventory.mainInventory[i] = null;
                }
            }
        }
        player.inventoryContainer.detectAndSendChanges();
        return true;
    }
}
