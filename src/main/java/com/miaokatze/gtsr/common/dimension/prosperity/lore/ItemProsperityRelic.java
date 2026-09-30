package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.register.CreativeTabManager;

/** Tangible witnesses to the lost age. They never bypass encounter seals. */
public class ItemProsperityRelic extends Item {

    private final String id;

    public ItemProsperityRelic(String id) {
        this.id = id;
        setUnlocalizedName("prosperity." + id);
        setTextureName("gtsr:lore/" + id);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(id.equals("old_crown") ? 1 : 64);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List text, boolean advanced) {
        text.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("lore.relic." + id));
    }
}
