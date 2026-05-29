package com.miaokatze.gtsr.common.items;

import net.minecraft.item.Item;

import com.miaokatze.gtsr.register.CreativeTabManager;

public class HubSingularityChip extends Item {

    public HubSingularityChip() {
        super();
        setUnlocalizedName("HubSingularityChip");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setTextureName("gtsr:HubSingularityChip");
        setMaxStackSize(1);
    }
}
