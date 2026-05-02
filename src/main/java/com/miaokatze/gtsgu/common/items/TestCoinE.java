package com.miaokatze.gtsgu.common.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.miaokatze.gtsgu.common.api.enums.GTSGUItemList;

import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;

/**
 * 电子测试硬币物品
 * <p>
 * 实现 IElectricItem 接口，支持充电。保留作为框架参考。
 */
public class TestCoinE extends Item implements IElectricItem {

    private static final long MAX_CHARGE = 1000000L;
    private static final int TIER = 5; // HV
    private static final long COST_PER_USE = 100000L;

    public TestCoinE() {
        super();
        setUnlocalizedName("TestCoin_GTSGU_E");
        setTextureName("gtsgu:TestCoin_GTSGU");
        setCreativeTab(CreativeTabs.tabMisc);
        setMaxStackSize(1);
        setMaxDamage((int) MAX_CHARGE);
    }

    @Override
    public boolean canProvideEnergy(ItemStack aStack) {
        return true;
    }

    @Override
    public double getMaxCharge(ItemStack aStack) {
        return MAX_CHARGE;
    }

    @Override
    public int getTier(ItemStack aStack) {
        return TIER;
    }

    @Override
    public double getTransferLimit(ItemStack aStack) {
        return 8192L; // HV limit
    }

    @Override
    public Item getChargedItem(ItemStack aStack) {
        return this;
    }

    @Override
    public Item getEmptyItem(ItemStack aStack) {
        return this;
    }

    /**
     * 处理右击事件
     */
    @Override
    public ItemStack onItemRightClick(ItemStack aStack, World aWorld, EntityPlayer aPlayer) {
        // 保留作为框架参考，暂时不实现功能
        if (!aWorld.isRemote && aPlayer.isSneaking()) {
            aPlayer.addChatMessage(new net.minecraft.util.ChatComponentText("§aTestCoinE 框架已就绪！"));
        }
        return aStack;
    }
}
