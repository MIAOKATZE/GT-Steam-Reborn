package com.miaokatze.gtsr.mixin;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtsr.common.weapons.WeaponEnchantments;

@Mixin(CreativeTabs.class)
public abstract class WeaponCreativeBooksMixin {

    @Inject(method = "displayAllReleventItems", at = @At("TAIL"), require = 1)
    private void gtsr$weaponBooks(List<ItemStack> items, CallbackInfo ci) {
        if ((Object) this != CreativeTabs.tabTools) return;
        for (Enchantment enchantment : new Enchantment[] { WeaponEnchantments.piercing, WeaponEnchantments.incendiary,
            WeaponEnchantments.hollowPoint, WeaponEnchantments.economy, WeaponEnchantments.destruction,
            WeaponEnchantments.diffusion, WeaponEnchantments.duration, WeaponEnchantments.tearing,
            WeaponEnchantments.quenching, Enchantment.looting }) {
            if (enchantment == null) continue;
            boolean present = false;
            for (ItemStack item : items) {
                if (item.getItem() != Items.enchanted_book) continue;
                net.minecraft.nbt.NBTTagList entries = Items.enchanted_book.func_92110_g(item);
                for (int i = 0; i < entries.tagCount(); i++) {
                    net.minecraft.nbt.NBTTagCompound entry = entries.getCompoundTagAt(i);
                    if (entry.getShort("id") == enchantment.effectId
                        && entry.getShort("lvl") == enchantment.getMaxLevel()) present = true;
                }
            }
            if (!present) items.add(
                Items.enchanted_book
                    .getEnchantedItemStack(new EnchantmentData(enchantment, enchantment.getMaxLevel())));
        }
    }
}
