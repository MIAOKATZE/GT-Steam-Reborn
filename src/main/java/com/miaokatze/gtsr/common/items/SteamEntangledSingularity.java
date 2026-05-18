package com.miaokatze.gtsr.common.items;

import net.minecraft.item.Item;

import gregtech.api.GregTechAPI;

public class SteamEntangledSingularity extends Item {

    public SteamEntangledSingularity() {
        super();
        setUnlocalizedName("SteamEntangledSingularity");
        setCreativeTab(GregTechAPI.TAB_GREGTECH);
        setTextureName("gtsr:SteamEntangledSingularity");
        setMaxStackSize(64);
    }
}
