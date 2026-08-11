package com.miaokatze.gtsr.common.blocks;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 失控奇点方块物品
 * 方块本体在世界中透明无贴图（原版全透明贴图），物品栏图标单独使用物品贴图，
 * 否则 ItemBlock 默认渲染方块图标会在创造物品栏中不可见。
 * 注意：ItemBlock.registerIcons 覆写不调 super，Item.itemIcon 永不填充，须自持 IIcon。
 */
public class ItemBlockRunawaySingularity extends ItemBlock {

    private IIcon singularityIcon;

    public ItemBlockRunawaySingularity(Block block) {
        super(block);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerIcons(IIconRegister reg) {
        this.singularityIcon = reg.registerIcon("gtsr:RunawaySingularity");
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return this.singularityIcon != null ? this.singularityIcon : super.getIconFromDamage(damage);
    }
}
