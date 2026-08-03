package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.util.SingularityDropExplosion;

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
        list.add(
            EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.steam_entangled_singularity.danger"));
    }

    @Override
    public boolean onEntityItemUpdate(EntityItem entityItem) {
        if (entityItem.worldObj.isRemote) {
            return false;
        }
        NBTTagCompound tag = entityItem.getEntityData();
        if (tag.getBoolean("gtsrExploded")) {
            return false;
        }
        int ticks = tag.getInteger("gtsrDropTicks") + 1;
        tag.setInteger("gtsrDropTicks", ticks);
        World world = entityItem.worldObj;
        if (ticks % 4 == 0) {
            world.spawnParticle(
                "portal",
                entityItem.posX + (world.rand.nextFloat() - 0.5F) * 0.8D,
                entityItem.posY + world.rand.nextFloat() * 0.5D,
                entityItem.posZ + (world.rand.nextFloat() - 0.5F) * 0.8D,
                (world.rand.nextFloat() - 0.5F) * 0.4D,
                world.rand.nextFloat() * 0.2D,
                (world.rand.nextFloat() - 0.5F) * 0.4D);
        }
        if (ticks >= 200) {
            tag.setBoolean("gtsrExploded", true);
            SingularityDropExplosion.explode(world, entityItem);
        }
        return false;
    }
}
