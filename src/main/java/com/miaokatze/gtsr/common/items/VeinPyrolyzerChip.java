package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

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

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(
            EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.chip.vein_pyrolyzer")
                + EnumChatFormatting.AQUA
                + " +"
                + rangeBonus);
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
