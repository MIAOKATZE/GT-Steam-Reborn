package com.miaokatze.gtsr.common.weapons;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import io.netty.buffer.ByteBuf;

/** Immutable enchantment snapshot captured before a shot enters the world. */
public final class WeaponShotEnchantments {

    public final int destruction, diffusion, duration, tearing, quenching, looting;

    public WeaponShotEnchantments() {
        this(0, 0, 0, 0, 0, 0);
    }

    public WeaponShotEnchantments(ItemStack stack) {
        this(
            WeaponEnchantments.level(stack, WeaponEnchantments.destruction),
            WeaponEnchantments.level(stack, WeaponEnchantments.diffusion),
            WeaponEnchantments.level(stack, WeaponEnchantments.duration),
            WeaponEnchantments.level(stack, WeaponEnchantments.tearing),
            WeaponEnchantments.level(stack, WeaponEnchantments.quenching),
            stack == null ? 0 : EnchantmentHelper.getEnchantmentLevel(Enchantment.looting.effectId, stack));
    }

    public WeaponShotEnchantments(int destruction, int diffusion, int duration, int tearing, int quenching,
        int looting) {
        this.destruction = clamp(destruction, 5);
        this.diffusion = clamp(diffusion, 5);
        this.duration = clamp(duration, 5);
        this.tearing = clamp(tearing, 5);
        this.quenching = clamp(quenching, 5);
        this.looting = clamp(looting, Enchantment.looting.getMaxLevel());
    }

    private static int clamp(int level, int maximum) {
        return Math.max(0, Math.min(maximum, level));
    }

    public void write(NBTTagCompound n) {
        n.setInteger("destruction", destruction);
        n.setInteger("diffusion", diffusion);
        n.setInteger("duration", duration);
        n.setInteger("tearing", tearing);
        n.setInteger("quenching", quenching);
        n.setInteger("looting", looting);
    }

    public static WeaponShotEnchantments read(NBTTagCompound n) {
        return new WeaponShotEnchantments(
            n.getInteger("destruction"),
            n.getInteger("diffusion"),
            n.getInteger("duration"),
            n.getInteger("tearing"),
            n.getInteger("quenching"),
            n.getInteger("looting"));
    }

    public void write(ByteBuf b) {
        b.writeByte(destruction)
            .writeByte(diffusion)
            .writeByte(duration)
            .writeByte(tearing)
            .writeByte(quenching)
            .writeByte(looting);
    }

    public static WeaponShotEnchantments read(ByteBuf b) {
        return new WeaponShotEnchantments(
            b.readUnsignedByte(),
            b.readUnsignedByte(),
            b.readUnsignedByte(),
            b.readUnsignedByte(),
            b.readUnsignedByte(),
            b.readUnsignedByte());
    }
}
