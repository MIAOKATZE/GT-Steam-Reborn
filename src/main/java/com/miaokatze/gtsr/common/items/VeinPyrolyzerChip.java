package com.miaokatze.gtsr.common.items;

import net.minecraft.item.Item;

import com.miaokatze.gtsr.register.CreativeTabManager;

public class VeinPyrolyzerChip extends Item {

    private final int rangeBonus;

    public VeinPyrolyzerChip(String unlocalizedName, int rangeBonus) {
        super();
        setUnlocalizedName(unlocalizedName);
        setTextureName("gtsr:" + unlocalizedName);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
        this.rangeBonus = rangeBonus;
    }

    public int getRangeBonus() {
        return rangeBonus;
    }
}
