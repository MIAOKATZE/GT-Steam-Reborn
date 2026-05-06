package com.miaokatze.gtsr.register;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 创造模式物品栏管理器
 * 负责管理模组专属的创造模式标签页，包括图标设置、名称显示以及物品列表的维护。
 */
public class CreativeTabManager {

    /**
     * 模组专属的创造模式标签页实例
     */
    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs("gtsr") {

        /**
         * 获取标签页的图标物品
         * 使用测试硬币作为默认图标
         */
        @Override
        public Item getTabIconItem() {
            if (com.miaokatze.gtsr.common.api.enums.GTSRItemList.SteamCacheNode.hasBeenSet()) {
                return com.miaokatze.gtsr.common.api.enums.GTSRItemList.SteamCacheNode.getItem();
            }
            return net.minecraft.init.Items.diamond;
        }

        /**
         * 获取标签页的显示名称（未本地化）
         */
        @Override
        public String getTranslatedTabLabel() {
            return "GT Steam Reborn";
        }

        /**
         * 向标签页中添加所有相关物品
         * 注意：Minecraft 1.7.10 中该方法名拼写为 displayAllReleventItems
         */
        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void displayAllReleventItems(List list) {
            for (ItemStack item : getItemsToAdd()) {
                if (item != null) {
                    list.add(item);
                }
            }
        }
    };

    private static final List<ItemStack> itemsToAdd = new ArrayList<>();

    public static void addItemToTab(ItemStack itemStack) {
        if (itemStack != null) {
            itemsToAdd.add(itemStack);
        }
    }

    public static void initCreativeTab() {
        GTSteamReborn.LOG.info("正在初始化创造模式物品栏，当前包含 " + itemsToAdd.size() + " 个物品");
    }

    public static List<ItemStack> getItemsToAdd() {
        return itemsToAdd;
    }
}
