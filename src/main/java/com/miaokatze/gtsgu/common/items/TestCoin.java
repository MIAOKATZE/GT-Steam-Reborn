package com.miaokatze.gtsgu.common.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/**
 * 测试硬币物品
 * <p>
 * 这是一个基础的 Minecraft 物品，用于验证模组的物品注册流程、创造模式标签页集成以及配方系统。
 * 支持右键操作：显示测试信息
 */
public class TestCoin extends Item {

    /**
     * 构造函数：初始化测试硬币的基础属性
     */
    public TestCoin() {
        super();
        setUnlocalizedName("TestCoin_GTSGU");
        setTextureName("gtsgu:TestCoin_GTSGU");
        setCreativeTab(CreativeTabs.tabMisc);
        setMaxStackSize(64);
    }

    /**
     * 处理右击事件
     */
    @Override
    public ItemStack onItemRightClick(ItemStack aStack, World aWorld, EntityPlayer aPlayer) {
        if (aWorld.isRemote) {
            return aStack;
        }

        aPlayer.addChatMessage(new ChatComponentText("§a[GTSGU] 测试硬币工作正常！"));
        return aStack;
    }
}
