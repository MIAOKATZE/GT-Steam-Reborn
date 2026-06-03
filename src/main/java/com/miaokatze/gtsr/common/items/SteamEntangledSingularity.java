package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import gregtech.api.GregTechAPI;

public class SteamEntangledSingularity extends Item {

    public SteamEntangledSingularity() {
        super();
        setUnlocalizedName("SteamEntangledSingularity");
        setCreativeTab(GregTechAPI.TAB_GREGTECH);
        setTextureName("gtsr:SteamEntangledSingularity");
        setMaxStackSize(64);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.singularity.desc"));
        list.add(EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("gtsr.tooltip.added_by"));
    }
}
