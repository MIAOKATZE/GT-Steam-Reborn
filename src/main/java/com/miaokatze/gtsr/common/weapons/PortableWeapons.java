package com.miaokatze.gtsr.common.weapons;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

public final class PortableWeapons {

    public static final Item[] weapons = new Item[3], ammo = new Item[3];
    private static boolean initialized;

    public static void registerItems() {
        if (weapons[0] != null) return;
        String[] w = { "PortableLM12", "PortableT20", "PortableQLZ04" },
            a = { "Ammo762Pack", "Ammo20Pack", "Ammo35Pack" };
        for (WeaponKind k : WeaponKind.values()) {
            weapons[k.id] = new WeaponItem(k).setUnlocalizedName("gtsr." + w[k.id])
                .setTextureName("gtsr:SteamEntangledSingularity");
            ammo[k.id] = new AmmoItem(k).setUnlocalizedName("gtsr." + a[k.id])
                .setTextureName("gtsr:SteamEntangledSingularity");
            GameRegistry.registerItem(weapons[k.id], w[k.id]);
            GameRegistry.registerItem(ammo[k.id], a[k.id]);
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.valueOf(w[k.id])
                .set(weapons[k.id]);
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.valueOf(a[k.id])
                .set(ammo[k.id]);
        }
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        WeaponEnchantments.init();
        WeaponNetwork.init();
        EntityRegistry.registerModEntity(
            EntityWeaponProjectile.class,
            "PortableWeaponProjectile",
            44,
            Loader.instance()
                .getIndexedModList()
                .get(GTSteamReborn.MODID)
                .getMod(),
            96,
            1,
            true);
        FMLCommonHandler.instance()
            .bus()
            .register(new WeaponController());
    }

    public static WeaponKind kind(ItemStack s) {
        return s != null && s.getItem() instanceof WeaponItem ? ((WeaponItem) s.getItem()).kind : null;
    }

    public static WeaponKind ammoKind(ItemStack s) {
        return s != null && s.getItem() instanceof AmmoItem ? ((AmmoItem) s.getItem()).kind : null;
    }

    public static final class WeaponItem extends Item {

        public final WeaponKind kind;

        WeaponItem(WeaponKind k) {
            kind = k;
            setMaxStackSize(1);
            setCreativeTab(gregtech.api.GregTechAPI.TAB_GREGTECH);
        }

        @Override
        public EnumAction getItemUseAction(ItemStack s) {
            return EnumAction.none;
        }

        @Override
        public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
            lines.add(StatCollector.translateToLocal("gtsr.weapon.tooltip." + kind.modelKey));
            lines.add(
                StatCollector.translateToLocalFormatted(
                    "gtsr.weapon.tooltip.damage",
                    WeaponEnchantments.damage(kind, stack),
                    WeaponEnchantments.penetration(kind, stack)));
            lines.add(
                StatCollector.translateToLocal(
                    kind == WeaponKind.QLZ04 ? "gtsr.weapon.tooltip.magazine" : "gtsr.weapon.tooltip.chain"));
            lines.add(StatCollector.translateToLocal("gtsr.weapon.tooltip.controls"));
        }

        @Override
        public int getMaxItemUseDuration(ItemStack s) {
            return 72000;
        }

        @Override
        public boolean onLeftClickEntity(ItemStack s, EntityPlayer player, net.minecraft.entity.Entity entity) {
            return true;
        }

        @Override
        public int getItemEnchantability() {
            return 16;
        }

        @Override
        public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
            p.setItemInUse(s, 72000);
            return s;
        }
    }

    public static final class AmmoItem extends Item {

        public final WeaponKind kind;

        @Override
        public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
            lines.add(StatCollector.translateToLocal("gtsr.weapon.tooltip.caliber." + kind.modelKey));
            lines.add(
                StatCollector.translateToLocalFormatted(
                    "gtsr.weapon.tooltip.ammo",
                    WeaponController.remaining(stack),
                    kind.capacity));
        }

        AmmoItem(WeaponKind k) {
            kind = k;
            setCreativeTab(gregtech.api.GregTechAPI.TAB_GREGTECH);
            setMaxStackSize(1);
            setMaxDamage(k.capacity);
        }
    }
}
