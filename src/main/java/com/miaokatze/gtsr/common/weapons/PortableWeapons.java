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

    public static final Item[] weapons = new Item[4], ammo = new Item[3], mimicAmmo = new Item[3];
    private static boolean initialized;

    public static void registerItems() {
        if (weapons[0] != null) return;
        String[] w = { "PortableLM12", "PortableT20", "PortableQLZ04", "PortableSingularity" },
            a = { "Ammo762Pack", "Ammo20Pack", "Ammo35Pack" };
        for (WeaponKind k : WeaponKind.values()) {
            weapons[k.id] = new WeaponItem(k).setUnlocalizedName("gtsr." + w[k.id])
                .setTextureName("gtsr:SteamEntangledSingularity");
            if (k == WeaponKind.SINGULARITY) {
                GameRegistry.registerItem(weapons[k.id], w[k.id]);
                com.miaokatze.gtsr.common.api.enums.GTSRItemList.PortableSingularity.set(weapons[k.id]);
                continue;
            }
            ammo[k.id] = new AmmoItem(k, false).setUnlocalizedName("gtsr." + a[k.id])
                .setTextureName("gtsr:SteamEntangledSingularity");
            GameRegistry.registerItem(weapons[k.id], w[k.id]);
            GameRegistry.registerItem(ammo[k.id], a[k.id]);
            String mimicName = "Mimic" + a[k.id];
            mimicAmmo[k.id] = new AmmoItem(k, true).setUnlocalizedName("gtsr." + mimicName)
                .setTextureName("gtsr:SteamEntangledSingularity");
            GameRegistry.registerItem(mimicAmmo[k.id], mimicName);
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.valueOf(mimicName)
                .set(mimicAmmo[k.id]);
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
        EntityRegistry.registerModEntity(
            EntityWeaponSingularity.class,
            "PortableWeaponSingularity",
            45,
            Loader.instance()
                .getIndexedModList()
                .get(GTSteamReborn.MODID)
                .getMod(),
            96,
            1,
            false);
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
            if (kind == WeaponKind.SINGULARITY) {
                lines.add(StatCollector.translateToLocal("gtsr.weapon.tooltip.singularity.stats"));
                lines.add(StatCollector.translateToLocal("gtsr.weapon.tooltip.singularity.controls"));
                return;
            }
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
            if (mimic) lines.add(StatCollector.translateToLocal("gtsr.weapon.tooltip.mimic"));
        }

        public final boolean mimic;

        @Override
        public boolean hasEffect(ItemStack stack, int pass) {
            return mimic;
        }

        @Override
        public void onUpdate(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean held) {
            if (world.isRemote || !(entity instanceof EntityPlayer)) return;
            EntityPlayer player = (EntityPlayer) entity;
            long now = world.getTotalWorldTime();
            if (!mimic) {
                if (WeaponController.remaining(stack) == 0 && slot >= 0
                    && slot < player.inventory.mainInventory.length
                    && player.inventory.mainInventory[slot] == stack) player.inventory.mainInventory[slot] = null;
                return;
            }
            net.minecraft.nbt.NBTTagCompound n = WeaponController.data(stack);
            String owner = player.getUniqueID()
                .toString();
            if (!owner.equals(n.getString("regenOwner")) || n.getLong("regenTick") > now) {
                n.setString("regenOwner", owner);
                n.setLong("regenTick", now);
            }
            if (player.getEntityData()
                .getLong("gtsr.lastFiring") > now)
                player.getEntityData()
                    .setLong("gtsr.lastFiring", now);
            if (player.getEntityData()
                .getLong("gtsr.lastWeaponShot") > now)
                player.getEntityData()
                    .setLong("gtsr.lastWeaponShot", now);
            long quiet = Math.max(
                player.getEntityData()
                    .getLong("gtsr.lastFiring"),
                player.getEntityData()
                    .getLong("gtsr.lastWeaponShot"));
            if (now - quiet < 100 || stack.getItemDamage() <= 0) {
                n.setLong("regenTick", now);
                return;
            }
            long previous = n.hasKey("regenTick") ? n.getLong("regenTick") : now;
            if (!n.hasKey("regenTick")) n.setLong("regenTick", now);
            if (now - previous >= 40) {
                int rounds = kind == WeaponKind.LM12 ? 10 : kind == WeaponKind.T20 ? 3 : 1;
                stack.setItemDamage(Math.max(0, stack.getItemDamage() - rounds));
                n.setLong("regenTick", now);
                player.inventory.markDirty();
            }
        }

        AmmoItem(WeaponKind k, boolean mimic) {
            kind = k;
            this.mimic = mimic;
            setCreativeTab(gregtech.api.GregTechAPI.TAB_GREGTECH);
            setMaxStackSize(1);
            setMaxDamage(k.capacity);
        }
    }
}
