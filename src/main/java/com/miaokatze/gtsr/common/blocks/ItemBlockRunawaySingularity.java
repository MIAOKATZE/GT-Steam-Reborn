package com.miaokatze.gtsr.common.blocks;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/**
 * 失控奇点方块物品
 * 方块本体在世界中透明无贴图（TRANSPARENT 图标），物品栏图标单独使用物品贴图，
 * 否则 ItemBlock 默认渲染方块图标会在创造物品栏中不可见。
 */
public class ItemBlockRunawaySingularity extends ItemBlock {

    public ItemBlockRunawaySingularity(Block block) {
        super(block);
        this.setTextureName("gtsr:RunawaySingularity");
    }

    @Override
    public IIcon getIconIndex(ItemStack stack) {
        return this.itemIcon;
    }
}
