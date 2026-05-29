package com.miaokatze.gtsr.common.items;

import net.minecraft.item.Item;

import com.miaokatze.gtsr.register.CreativeTabManager;

public class RareGasSeparationChip extends Item {

    public RareGasSeparationChip() {
        super();
        setUnlocalizedName("RareGasSeparationChip");
        setTextureName("gtsr:RareGasSeparationChip");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
    }
}
