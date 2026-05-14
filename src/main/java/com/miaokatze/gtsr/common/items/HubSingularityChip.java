package com.miaokatze.gtsr.common.items;

import net.minecraft.item.Item;

import gregtech.api.GregTechAPI;

public class HubSingularityChip extends Item {

    public HubSingularityChip() {
        super();
        setUnlocalizedName("HubSingularityChip");
        setCreativeTab(GregTechAPI.TAB_GREGTECH);
        setTextureName("miscutils:MU-metaitem.01/152");
        setMaxStackSize(1);
    }
}
