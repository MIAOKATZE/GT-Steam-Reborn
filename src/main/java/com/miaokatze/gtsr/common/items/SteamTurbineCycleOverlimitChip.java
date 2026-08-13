package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.gtsr.register.CreativeTabManager;

public class SteamTurbineCycleOverlimitChip extends Item {

    public SteamTurbineCycleOverlimitChip() {
        super();
        setUnlocalizedName("SteamTurbineCycleOverlimitChip");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setTextureName("gtsr:SteamTurbineCycleOverlimitChip");
        setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(
            EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("gtsr.tooltip.chip.steam_turbine_cycle_overlimit.1")
                + StatCollector.translateToLocal("gtsr.tooltip.chip.steam_turbine_cycle_overlimit.2")
                + StatCollector.translateToLocal("gtsr.tooltip.chip.steam_turbine_cycle_overlimit.3"));
        list.add(GTSRUtils.getAddedByLine());
    }
}
