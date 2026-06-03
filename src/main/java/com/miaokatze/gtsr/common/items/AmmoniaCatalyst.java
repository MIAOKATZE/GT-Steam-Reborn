package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.register.CreativeTabManager;

public class AmmoniaCatalyst extends Item {

    public AmmoniaCatalyst(String unlocalizedName) {
        super();
        setUnlocalizedName(unlocalizedName);
        setTextureName("gtsr:" + unlocalizedName);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        String name = getUnlocalizedName();
        if (name.contains("Quantum")) {
            list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.catalyst.ammonia.quantum"));
        } else if (name.contains("Ruthenium")) {
            list.add(
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.catalyst.ammonia.ruthenium"));
        } else if (name.contains("FeCo")) {
            list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.catalyst.ammonia.feco"));
        } else if (name.contains("Osmium")) {
            list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.catalyst.ammonia.osmium"));
        } else if (name.contains("Platinum")) {
            list.add(
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.catalyst.ammonia.platinum"));
        } else if (name.contains("Uranium")) {
            list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.catalyst.ammonia.uranium"));
        } else if (name.contains("Nickel")) {
            list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.catalyst.ammonia.nickel"));
        }
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.catalyst.ammonia"));
        list.add(
            EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.tooltip.added_by")
                + " "
                + EnumChatFormatting.AQUA
                + "GT"
                + EnumChatFormatting.GREEN
                + "-"
                + EnumChatFormatting.GOLD
                + "Steam"
                + EnumChatFormatting.RED
                + "-"
                + EnumChatFormatting.BLUE
                + "Reborn");
    }
}
