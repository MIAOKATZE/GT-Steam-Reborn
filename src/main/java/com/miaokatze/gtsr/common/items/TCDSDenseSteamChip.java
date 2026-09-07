package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.gtsr.register.CreativeTabManager;

/**
 * 致密蒸汽芯片：装入热化学致密蒸汽发生系统（TCDS）控制器槽位，产出切换为致密变体——
 * 普通档致密蒸汽（÷1000）、过热档致密过热蒸汽（÷2000）。仿 GeothermalOverheatChip。
 */
public class TCDSDenseSteamChip extends Item {

    public TCDSDenseSteamChip(String unlocalizedName) {
        super();
        setUnlocalizedName(unlocalizedName);
        setTextureName("gtsr:TcdsBoostChip");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.chip.tcds_dense_steam"));
        list.add(GTSRUtils.getAddedByLine());
    }
}
