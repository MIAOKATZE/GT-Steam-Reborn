package com.miaokatze.gtsr.common.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

/**
 * 枢纽绑定流静态助手（O2-12/O2-13）：收敛「从玩家主物品栏消耗蒸汽纠缠奇点」的逐字双份
 * （原 MTESteamHubArray 与 MTESingularityDrillingHub 各一份，diff 为空亲证），
 * 以及 gtsr.hubPos 绑定标签核心字段（x/y/z/dim/type/output）的单点构造
 * （原 6 文件 12 处写块镜像）。绑定流主体（onRightclick 模板 / bindOne / bindWhole / 成本钩子）
 * 随 MTEHubArrayBase 基类模板上提，本类只承载无状态段落，供基类、钻井枢纽与节点族共用。
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

    /**
     * 构造 gtsr.hubPos 绑定标签的核心字段（x/y/z/dim/type/output）。
     * 注意 output 极性双轨（O2-13 防漂移）：机器侧绑定流按语义直存（getLockedItemOutput/翻转值）；
     * 节点侧 saveNBTData/setItemNBT 反转存储（loadNBTData 反转读取）——RemoteWorkerNode 例外直存。
     * 调用处以显式布尔表达极性，禁止在助手内做任何翻转。
     */
    public static NBTTagCompound createHubPosTag(int x, int y, int z, int dim, String type, boolean output) {
        NBTTagCompound hubTag = new NBTTagCompound();
        hubTag.setInteger("x", x);
        hubTag.setInteger("y", y);
        hubTag.setInteger("z", z);
        hubTag.setInteger("dim", dim);
        hubTag.setString("type", type);
        hubTag.setBoolean("output", output);
        return hubTag;
    }
}
