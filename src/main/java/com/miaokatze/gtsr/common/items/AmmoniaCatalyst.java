package com.miaokatze.gtsr.common.items;

import net.minecraft.item.Item;

import com.miaokatze.gtsr.register.CreativeTabManager;

public class AmmoniaCatalyst extends Item {

    public AmmoniaCatalyst(String unlocalizedName) {
        super();
        setUnlocalizedName(unlocalizedName);
        setTextureName("miscutils:MU-metaitem.01/152");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
    }
}
