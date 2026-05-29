package com.miaokatze.gtsr.common.items;

import net.minecraft.item.Item;

import com.miaokatze.gtsr.register.CreativeTabManager;

public class AmmoniaCatalyst extends Item {

    public AmmoniaCatalyst(String unlocalizedName) {
        super();
        setUnlocalizedName(unlocalizedName);
        setTextureName("gtsr:" + unlocalizedName);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
    }
}
