package com.miaokatze.gtsr.common.weapons;

import java.io.File;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.Loader;

/** IDs are persisted on first allocation; a later collision fails instead of reinterpreting saved NBT. */
public final class WeaponEnchantments extends Enchantment {

    public static WeaponEnchantments piercing, incendiary, hollowPoint, economy;
    private final int category;

    private WeaponEnchantments(int id, int category, String name) {
        super(id, 5, EnumEnchantmentType.weapon);
        this.category = category;
        setName("gtsr." + name);
    }

    public static void init() {
        if (piercing != null) return;
        Configuration cfg = new Configuration(
            new File(
                Loader.instance()
                    .getConfigDir(),
                "gtsr-portable-enchantments.cfg"));
        cfg.load();
        int[] ids = new int[4];
        String[] names = { "piercing", "incendiary", "hollowPoint", "economy" };
        for (int i = 0; i < 4; i++) {
            int free = -1;
            for (int j = Enchantment.enchantmentsList.length - 1; j >= 0; j--) {
                boolean used = false;
                for (int k = 0; k < i; k++) if (ids[k] == j) used = true;
                if (!used && Enchantment.enchantmentsList[j] == null) {
                    free = j;
                    break;
                }
            }
            ids[i] = cfg.get("ids", names[i], free)
                .getInt();
            if (ids[i] < 0 || ids[i] >= Enchantment.enchantmentsList.length
                || Enchantment.enchantmentsList[ids[i]] != null)
                throw new IllegalStateException("GTSR portable enchantment ID collision: " + names[i] + "=" + ids[i]);
            for (int k = 0; k < i; k++)
                if (ids[k] == ids[i]) throw new IllegalStateException("Duplicate GTSR enchantment ID");
        }
        cfg.save();
        piercing = new WeaponEnchantments(ids[0], 0, "piercing");
        incendiary = new WeaponEnchantments(ids[1], 1, "incendiary");
        hollowPoint = new WeaponEnchantments(ids[2], 2, "hollowPoint");
        economy = new WeaponEnchantments(ids[3], 3, "economy");
        Enchantment.addToBookList(piercing);
        Enchantment.addToBookList(incendiary);
        Enchantment.addToBookList(hollowPoint);
        Enchantment.addToBookList(economy);
    }

    @Override
    public int getMaxLevel() {
        return category == 3 ? 5 : 3;
    }

    @Override
    public int getMinEnchantability(int level) {
        return 8 + level * 8;
    }

    @Override
    public int getMaxEnchantability(int level) {
        return getMinEnchantability(level) + 20;
    }

    @Override
    public boolean canApply(ItemStack s) {
        return PortableWeapons.kind(s) != null && (PortableWeapons.kind(s) != WeaponKind.SINGULARITY || category == 3);
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack s) {
        return canApply(s);
    }

    @Override
    public boolean canApplyTogether(Enchantment other) {
        if (other == this) return false;
        return !(other instanceof WeaponEnchantments) || category == 3 || ((WeaponEnchantments) other).category == 3;
    }

    public static int level(ItemStack s, WeaponEnchantments e) {
        return e == null || (PortableWeapons.kind(s) == WeaponKind.SINGULARITY && e.category != 3) ? 0
            : Math.max(0, Math.min(e.getMaxLevel(), EnchantmentHelper.getEnchantmentLevel(e.effectId, s)));
    }

    public static float damage(WeaponKind k, ItemStack s) {
        int ap = level(s, piercing), fire = ap == 0 ? level(s, incendiary) : 0,
            hp = ap == 0 && fire == 0 ? level(s, hollowPoint) : 0;
        return k.damage * (1 + .05f * (ap + fire) + .25f * hp);
    }

    public static float penetration(WeaponKind k, ItemStack s) {
        int ap = level(s, piercing), hp = ap == 0 && level(s, incendiary) == 0 ? level(s, hollowPoint) : 0;
        return k.armorPenetration * (1 + .2f * ap - .1f * hp);
    }
}
