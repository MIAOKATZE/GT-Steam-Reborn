package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** A traded journal never carries another player's chapter permissions. */
public final class ItemProsperityJournal extends ItemProsperityRelic {

    public ItemProsperityJournal() {
        super("history");
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player instanceof EntityPlayerMP && stack.getItem() == LoreRegistry.journal) {
            LoreNetwork.send((EntityPlayerMP) player, true);
        }
        return stack;
    }
}
