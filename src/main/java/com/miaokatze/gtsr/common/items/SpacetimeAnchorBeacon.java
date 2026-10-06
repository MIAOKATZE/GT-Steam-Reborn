package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.prosperity.travel.AnchorIntent;
import com.miaokatze.gtsr.common.dimension.prosperity.travel.SpacetimeTravel;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;

public final class SpacetimeAnchorBeacon extends Item implements IElectricItem {

    public static final int USE_DURATION = 72000;
    public static final int RECALL_TICKS = 60;

    /** The mark is written exclusively by SpacetimeTravel.mark on the server. */
    public static boolean isBound(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("gtsrAnchor", 10);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack, int pass) {
        return isBound(stack) || super.hasEffect(stack, pass);
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.bow;
    }

    public SpacetimeAnchorBeacon() {
        setUnlocalizedName("SpacetimeAnchorBeacon");
        setTextureName("gtsr:spacetime_anchor_beacon");
        setCreativeTab(GregTechAPI.TAB_GREGTECH);
        setMaxStackSize(1);
        setMaxDamage(0);
    }

    @Override
    public boolean canProvideEnergy(ItemStack s) {
        return false;
    }

    @Override
    public Item getChargedItem(ItemStack s) {
        return this;
    }

    @Override
    public Item getEmptyItem(ItemStack s) {
        return this;
    }

    @Override
    public double getMaxCharge(ItemStack s) {
        return 102400000;
    }

    @Override
    public int getTier(ItemStack s) {
        return 6;
    }

    @Override
    public double getTransferLimit(ItemStack s) {
        return 32768;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack s) {
        return USE_DURATION;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        if (!w.isRemote && p instanceof EntityPlayerMP) {
            AnchorIntent.used((EntityPlayerMP) p, s);
            if (p.isSneaking() && SpacetimeTravel.eligible((EntityPlayerMP) p)) p.setItemInUse(s, USE_DURATION);
        }
        if (w.isRemote) {
            clientIntent();
            if (p.isSneaking()) p.setItemInUse(s, USE_DURATION);
        }
        return s;
    }

    @SideOnly(Side.CLIENT)
    private void clientIntent() {
        com.miaokatze.gtsr.client.travel.SpacetimeClient.anchorRightClick();
    }

    @Override
    public void onUsingTick(ItemStack s, EntityPlayer p, int remaining) {
        if (p.getHeldItem() != s || !p.isSneaking()) {
            p.stopUsingItem();
            return;
        }
        if (p.worldObj.isRemote) {
            // End only the local pose; recall and charging remain server-owned.
            if (USE_DURATION - remaining >= RECALL_TICKS) p.stopUsingItem();
            return;
        }
        if (!(p instanceof EntityPlayerMP) || !SpacetimeTravel.eligible((EntityPlayerMP) p)) {
            p.stopUsingItem();
            return;
        }
        if (USE_DURATION - remaining >= RECALL_TICKS) {
            p.stopUsingItem();
            SpacetimeTravel.recall((EntityPlayerMP) p, s);
        }
    }

    @Override
    public void addInformation(ItemStack s, EntityPlayer p, List list, boolean advanced) {
        list.add(StatCollector.translateToLocal("gtsr.anchor.tooltip.mark"));
        list.add(StatCollector.translateToLocal("gtsr.anchor.tooltip.recall"));
        list.add(
            StatCollector.translateToLocalFormatted(
                "gtsr.anchor.tooltip.energy",
                (long) ElectricItem.manager.getCharge(s),
                102400000L));
        list.add(StatCollector.translateToLocal("gtsr.anchor.tooltip.cost"));
    }
}
