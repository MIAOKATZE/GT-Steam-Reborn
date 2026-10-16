package com.miaokatze.gtsr.common.items;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Materials;

/** A distinct irradiation product that borrows its original dust's atlas and colour. */
public final class IrradiatedDust extends Item {

    private final boolean moon;

    public IrradiatedDust(boolean moon) {
        this.moon = moon;
        setUnlocalizedName(moon ? "IrradiatedMoonDust" : "IrradiatedStoneDust");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(64);
    }

    public static ItemStack source(boolean moon) {
        return moon ? GameRegistry.findItemStack("dreamcraft", "MoonStoneDust", 1) : Materials.Stone.getDust(1);
    }

    public ItemStack source() {
        return source(moon);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) { /* Uses the already registered source atlas icon. */ }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        ItemStack original = source();
        return original == null ? super.getIconFromDamage(damage) : original.getIconIndex();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        ItemStack original = source();
        return original == null ? 0xFFFFFF
            : original.getItem()
                .getColorFromItemStack(original, pass);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack, int pass) {
        return true;
    }
}
