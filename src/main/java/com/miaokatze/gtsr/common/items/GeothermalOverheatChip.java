package com.miaokatze.gtsr.common.items;

import net.minecraft.item.Item;

import com.miaokatze.gtsr.register.CreativeTabManager;

public class GeothermalOverheatChip extends Item {

    public GeothermalOverheatChip(String unlocalizedName) {
        super();
        setUnlocalizedName(unlocalizedName);
        setTextureName("gtsr:GeothermalOverheatChip");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
    }
}
