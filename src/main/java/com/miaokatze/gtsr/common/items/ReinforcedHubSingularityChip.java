package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.register.CreativeTabManager;

public class ReinforcedHubSingularityChip extends Item {

    public ReinforcedHubSingularityChip() {
        super();
        setUnlocalizedName("ReinforcedHubSingularityChip");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setTextureName("gtsr:ReinforcedHubSingularityChip");
        setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(
            EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.chip.reinforced_hub_singularity"));
        list.add(EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("gtsr.tooltip.added_by"));
    }
}
