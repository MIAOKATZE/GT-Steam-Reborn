package com.miaokatze.gtsr.common.items;

import net.minecraft.item.Item;

import com.miaokatze.gtsr.register.CreativeTabManager;

public class ReinforcedHubSingularityChip extends Item {

    public ReinforcedHubSingularityChip() {
        super();
        setUnlocalizedName("ReinforcedHubSingularityChip");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setTextureName("gtsr:HubSingularityChip");
        setMaxStackSize(1);
    }
}
