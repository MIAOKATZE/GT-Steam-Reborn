package com.miaokatze.gtsr.common.weapons;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Server tick owns every state transition and inventory debit. Reloads hold stack identity, never a copied stack. */
public final class WeaponController {

    private final Map<UUID, Session> sessions = new HashMap<UUID, Session>();
    private long tick;
    private static final String KEY = "gtsr.weapon";

    private static final class Session {

        EntityPlayerMP player;
        ItemStack gun, reloadPack;
        int slot, reload, serial, spin, cooldown, dimension;
        long refreshed;
        boolean firing, focusing, reloadDown, switchDown;
        int remoteTicks;
        EntityWeaponProjectile remoteProjectile;
    }

    public static NBTTagCompound data(ItemStack s) {
        if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound());
        if (!s.getTagCompound()
            .hasKey(KEY))
            s.getTagCompound()
                .setTag(KEY, new NBTTagCompound());
        return s.getTagCompound()
            .getCompoundTag(KEY);
    }

    public static int remaining(ItemStack pack) {
        WeaponKind k = PortableWeapons.ammoKind(pack);
        return k == null || pack.stackSize != 1 ? 0 : Math.max(0, k.capacity - pack.getItemDamage());
    }

    public static boolean debit(ItemStack pack, int amount) {
        if (amount < 0 || remaining(pack) < amount) return false;
        pack.setItemDamage(pack.getItemDamage() + amount);
        return true;
    }

    public static int paidUnits(int units, int economy, Random random) {
        int paid = 0;
        float chance = .1f * Math.max(0, Math.min(5, economy));
        for (int i = 0; i < units; i++) if (random.nextFloat() >= chance) paid++;
        return paid;
    }

    private static String packId(ItemStack pack) {
        NBTTagCompound n = data(pack);
        if (!n.hasKey("id")) n.setString(
            "id",
            UUID.randomUUID()
                .toString());
        return n.getString("id");
    }

    private static int selected(ItemStack gun, EntityPlayerMP p, WeaponKind k) {
        NBTTagCompound n = data(gun);
        if (!n.hasKey("ammoType")) {
            int type = 0;
            for (ItemStack item : p.inventory.mainInventory) if (matches(item, k, 1) && units(item, k) > 0) type = 1;
            n.setInteger("ammoType", type);
        }
        boolean ordinary = false, special = false;
        for (ItemStack item : p.inventory.mainInventory) {
            if (matches(item, k, 0) && units(item, k) > 0) ordinary = true;
            if (matches(item, k, 1) && units(item, k) > 0) special = true;
        }
        if (ordinary && special && !n.getBoolean("switchHint")) {
            p.addChatMessage(new net.minecraft.util.ChatComponentTranslation("gtsr.weapon.ammo.switch_hint"));
            n.setBoolean("switchHint", true);
        } else if (!ordinary || !special) n.setBoolean("switchHint", false);
        return n.getInteger("ammoType") == 1 ? 1 : 0;
    }

    private static boolean matches(ItemStack item, WeaponKind k, int type) {
        if (item == null) return false;
        if (k == WeaponKind.SINGULARITY) return item.isItemEqual(
            (type == 1 ? com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSteamEntangledSingularity
                : com.miaokatze.gtsr.common.api.enums.GTSRItemList.SteamEntangledSingularity).get(1));
        return PortableWeapons.ammoKind(item) == k && ((PortableWeapons.AmmoItem) item.getItem()).mimic == (type == 1);
    }

    private static int units(ItemStack item, WeaponKind k) {
        return item == null ? 0 : k == WeaponKind.SINGULARITY ? item.stackSize : remaining(item);
    }

    private static ItemStack pack(EntityPlayerMP p, WeaponKind k) {
        int type = PortableWeapons.kind(p.getHeldItem()) == k ? selected(p.getHeldItem(), p, k) : 0;
        for (ItemStack s : p.inventory.mainInventory) if (matches(s, k, type) && units(s, k) > 0) return s;
        return null;
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        sessions.remove(e.player.getUniqueID());
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        serverTick();
    }

    public void serverTick() {
        tick++;
        WeaponNetwork.Pending pending;
        int count = 0;
        while (count++ < 256 && (pending = WeaponNetwork.INPUT.poll()) != null) {
            EntityPlayerMP p = pending.player;
            WeaponNetwork.Controls c = pending.controls;
            if (p == null || p.isDead
                || p.playerNetServerHandler == null
                || !p.playerNetServerHandler.netManager.isChannelOpen()
                || c.slot < 0
                || c.slot > 8
                || p.inventory.currentItem != c.slot
                || p.openContainer != p.inventoryContainer
                || PortableWeapons.kind(p.getHeldItem()) == null) continue;
            Session s = sessions.get(p.getUniqueID());
            if (s == null) {
                s = new Session();
                s.player = p;
                s.serial = p.getEntityData()
                    .getInteger("gtsr.weaponShotSerial");
                sessions.put(p.getUniqueID(), s);
            }
            ItemStack held = p.getHeldItem();
            if (s.gun != held || s.slot != c.slot || s.dimension != p.dimension) {
                s.gun = held;
                s.slot = c.slot;
                s.dimension = p.dimension;
                s.remoteTicks = 0;
                s.remoteProjectile = null;
                s.reload = 0;
                s.reloadPack = null;
                s.spin = 0;
                s.cooldown = 0;
                s.reloadDown = false;
            }
            WeaponKind heldKind = PortableWeapons.kind(held);
            selected(held, p, heldKind);
            if (c.switchAmmo && !s.switchDown) switchAmmo(s, heldKind);
            s.switchDown = c.switchAmmo;
            if (heldKind == WeaponKind.SINGULARITY && c.firing
                && !s.firing
                && s.remoteProjectile != null
                && !s.remoteProjectile.isDead
                && s.remoteTicks == 0) {
                s.remoteTicks = 30;
                effect(s, 3);
            }
            s.firing = c.firing;
            if (c.firing) p.getEntityData()
                .setLong("gtsr.lastFiring", p.worldObj.getTotalWorldTime());
            s.focusing = c.focusing;
            s.refreshed = tick;
            if (c.reload && !s.reloadDown) startReload(s);
            s.reloadDown = c.reload;
        }
        Iterator<Session> it = sessions.values()
            .iterator();
        while (it.hasNext()) {
            Session s = it.next();
            EntityPlayerMP p = s.player;
            if (p.isDead || p.playerNetServerHandler == null || !p.playerNetServerHandler.netManager.isChannelOpen()) {
                it.remove();
                continue;
            }
            // Cool all carried weapons, so slot changes cannot erase heat or freeze cooling.
            for (ItemStack carried : p.inventory.mainInventory) cool(carried, p.worldObj.getTotalWorldTime());
            if (p.getHeldItem() != s.gun || p.dimension != s.dimension
                || p.inventory.currentItem != s.slot
                || p.openContainer != p.inventoryContainer
                || tick - s.refreshed > 10) {
                s.firing = false;
                s.focusing = false;
                s.reload = 0;
                s.reloadPack = null;
                s.spin = 0;
                s.remoteTicks = 0;
                if (p.getHeldItem() != s.gun || p.dimension != s.dimension) {
                    it.remove();
                    continue;
                }
            }
            update(s);
            sendState(s);
        }
    }

    public static float coolingRate(WeaponKind kind) {
        return kind == WeaponKind.LM12 ? .009f : kind == WeaponKind.T20 ? .012f : 0;
    }

    private static void cool(ItemStack gun, long now) {
        WeaponKind k = PortableWeapons.kind(gun);
        if (k == null || k == WeaponKind.QLZ04 || k == WeaponKind.SINGULARITY) return;
        NBTTagCompound n = data(gun);
        long previous = n.hasKey("heatTick") ? n.getLong("heatTick") : now - 1;
        // Count only tick endpoints in (previous, now] that have reached the 20-tick quiet window.
        // The first eligible endpoint is lastShot+20, including that boundary exactly once.
        long eligibleStart = n.hasKey("lastShot") ? Math.max(previous, n.getLong("lastShot") + 19) : previous;
        long elapsed = Math.max(0, now - eligibleStart);
        n.setLong("heatTick", now);
        float heat = Math.max(0, Math.min(1, n.getFloat("heat")) - Math.min(1000, elapsed) * coolingRate(k));
        n.setFloat("heat", heat);
        if (heat <= .25f) n.setBoolean("hot", false);
    }

    private static void removeEmpty(EntityPlayerMP player, ItemStack item, WeaponKind k) {
        if (item == null || units(item, k) > 0
            || (item.getItem() instanceof PortableWeapons.AmmoItem
                && ((PortableWeapons.AmmoItem) item.getItem()).mimic))
            return;
        for (int i = 0; i < player.inventory.mainInventory.length; i++)
            if (player.inventory.mainInventory[i] == item) player.inventory.mainInventory[i] = null;
    }

    private void switchAmmo(Session s, WeaponKind k) {
        NBTTagCompound n = data(s.gun);
        int old = selected(s.gun, s.player, k), next = 1 - old;
        boolean available = false;
        for (ItemStack item : s.player.inventory.mainInventory)
            if (matches(item, k, next) && units(item, k) > 0) available = true;
        if (!available) return;
        int paid = Math.min(n.getInteger("magazine"), n.getInteger("paidMagazine"));
        if (paid > 0 && (k == WeaponKind.QLZ04 || k == WeaponKind.SINGULARITY)) {
            if (k == WeaponKind.SINGULARITY) {
                ItemStack returned = (old == 1
                    ? com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSteamEntangledSingularity
                    : com.miaokatze.gtsr.common.api.enums.GTSRItemList.SteamEntangledSingularity).get(paid);
                s.player.inventory.addItemStackToInventory(returned);
            } else {
                ItemStack target = null;
                for (ItemStack item : s.player.inventory.mainInventory)
                    if (matches(item, k, old) && packId(item).equals(n.getString("returnPack"))) {
                        target = item;
                        break;
                    }
                if (target == null) {
                    target = new ItemStack(old == 1 ? PortableWeapons.mimicAmmo[k.id] : PortableWeapons.ammo[k.id]);
                    target.setItemDamage(k.capacity);
                    data(target).setString("id", n.getString("returnPack"));
                    target.setItemDamage(Math.max(0, target.getItemDamage() - paid));
                    s.player.inventory.addItemStackToInventory(target);
                } else target.setItemDamage(Math.max(0, target.getItemDamage() - paid));
            }
        }
        n.setInteger("magazine", 0);
        n.setInteger("paidMagazine", 0);
        n.setString("loadedPack", "");
        n.setInteger("ammoType", next);
        s.reload = 0;
        s.reloadPack = null;
        startReload(s);
        s.player.inventoryContainer.detectAndSendChanges();
    }

    private void startReload(Session s) {
        if (s.reload > 0) return;
        WeaponKind k = PortableWeapons.kind(s.gun);
        if (k == null) return;
        if ((k == WeaponKind.QLZ04 || k == WeaponKind.SINGULARITY)
            && data(s.gun).getInteger("magazine") >= (k == WeaponKind.QLZ04 ? 12 : 1)) return;
        ItemStack a = pack(s.player, k);
        if (a == null) return;
        s.reloadPack = a;
        s.reload = k.reloadTicks;
        if (k == WeaponKind.SINGULARITY) effect(s, 5);
    }

    private void finishReload(Session s, WeaponKind k) {
        ItemStack a = s.reloadPack;
        s.reloadPack = null;
        if (a == null || units(a, k) <= 0) return;
        boolean present = false;
        for (ItemStack inventory : s.player.inventory.mainInventory) if (inventory == a) present = true;
        if (!present || !matches(a, k, selected(s.gun, s.player, k))) return;
        NBTTagCompound n = data(s.gun);
        if (k == WeaponKind.QLZ04 || k == WeaponKind.SINGULARITY) {
            int capacity = k == WeaponKind.QLZ04 ? 12 : 1;
            int needed = capacity - Math.max(0, Math.min(capacity, n.getInteger("magazine")));
            int units = Math.min(needed, units(a, k));
            int cost = paidUnits(units, WeaponEnchantments.level(s.gun, WeaponEnchantments.economy), s.player.getRNG());
            if (k == WeaponKind.SINGULARITY) {
                if (a.stackSize < cost) return;
                a.stackSize -= cost;
            } else if (!debit(a, cost)) return;
            n.setInteger("paidMagazine", n.getInteger("paidMagazine") + cost);
            n.setInteger("magazine", Math.min(capacity, n.getInteger("magazine") + units));
            n.setInteger("loadedType", selected(s.gun, s.player, k));
            n.setString("returnPack", k == WeaponKind.SINGULARITY ? "" : packId(a));
            removeEmpty(s.player, a, k);
        } else {
            // Linking a new belt is a reload state change, never a round debit.
            data(a).setBoolean("chain", true);
            n.setString("loadedPack", packId(a));
        }
        effect(s, 2);
        s.player.inventoryContainer.detectAndSendChanges();
    }

    private void update(Session s) {
        WeaponKind k = PortableWeapons.kind(s.gun);
        if (k == null) return;
        if (s.remoteTicks > 0 && --s.remoteTicks == 20) {
            if (s.remoteProjectile != null && !s.remoteProjectile.isDead
                && s.remoteProjectile.worldObj == s.player.worldObj
                && s.player.worldObj.loadedEntityList.contains(s.remoteProjectile)) {
                s.remoteProjectile.detonate();
                effect(s, 4);
            }
            s.remoteProjectile = null;
        }
        if (s.reload > 0) {
            if (--s.reload == 0) finishReload(s, k);
            s.spin = Math.max(0, s.spin - 8);
            return;
        }
        if (k == WeaponKind.SINGULARITY
            && (s.remoteTicks > 0 || (s.remoteProjectile != null && !s.remoteProjectile.isDead))) return;
        if (s.cooldown > 0) s.cooldown--;
        NBTTagCompound n = data(s.gun);
        if (!s.firing || n.getBoolean("hot")) {
            s.spin = Math.max(0, s.spin - 8);
            return;
        }
        ItemStack a = pack(s.player, k);
        if (k == WeaponKind.QLZ04 || k == WeaponKind.SINGULARITY ? n.getInteger("magazine") <= 0
            : a == null || !packId(a).equals(n.getString("loadedPack"))) {
            startReload(s);
            return;
        }
        if (k == WeaponKind.LM12) {
            s.spin = Math.min(160, s.spin + 2);
            if (s.spin < 40) return;
        }
        if (s.player.worldObj.getTotalWorldTime() < n.getLong("nextShot")) return;
        int oldMagazine = n.getInteger("magazine"), oldDamage = a == null ? 0 : a.getItemDamage();
        if (k == WeaponKind.QLZ04 || k == WeaponKind.SINGULARITY)
            n.setInteger("magazine", n.getInteger("magazine") - 1);
        else if (!debit(
            a,
            paidUnits(1, WeaponEnchantments.level(s.gun, WeaponEnchantments.economy), s.player.getRNG()))) return;
        EntityWeaponProjectile projectile = new EntityWeaponProjectile(
            s.player.worldObj,
            s.player,
            k,
            WeaponEnchantments.damage(k, s.gun),
            WeaponEnchantments.penetration(k, s.gun),
            WeaponEnchantments.level(s.gun, WeaponEnchantments.piercing) == 0
                ? WeaponEnchantments.level(s.gun, WeaponEnchantments.incendiary)
                : 0);
        projectile.setCritical(n.getInteger("loadedType") == 1);
        if (!s.player.worldObj.spawnEntityInWorld(projectile)) {
            if (k == WeaponKind.QLZ04 || k == WeaponKind.SINGULARITY) n.setInteger("magazine", oldMagazine);
            else a.setItemDamage(oldDamage);
            projectile.setDead();
            return;
        }
        if (k == WeaponKind.SINGULARITY) {
            s.remoteProjectile = projectile;
            startReload(s);
        }
        if (k == WeaponKind.QLZ04 || k == WeaponKind.SINGULARITY)
            n.setInteger("paidMagazine", Math.max(0, n.getInteger("paidMagazine") - 1));
        else removeEmpty(s.player, a, k);
        s.player.getEntityData()
            .setLong("gtsr.lastWeaponShot", s.player.worldObj.getTotalWorldTime());
        s.serial = s.player.getEntityData()
            .getInteger("gtsr.weaponShotSerial") + 1;
        s.player.getEntityData()
            .setInteger("gtsr.weaponShotSerial", s.serial);
        effect(s, 0);
        s.cooldown = k == WeaponKind.LM12
            ? Math.max(1, Math.min(8, Math.round(1f / (.125f + .875f * Math.min(1, (s.spin - 40) / 120f)))))
            : k.interval;
        n.setLong("nextShot", s.player.worldObj.getTotalWorldTime() + s.cooldown);
        n.setLong("lastShot", s.player.worldObj.getTotalWorldTime());
        n.setInteger("shotInterval", s.cooldown);
        if (k == WeaponKind.LM12 || k == WeaponKind.T20) {
            float heat = Math.min(1, n.getFloat("heat") + (k == WeaponKind.LM12 ? .00625f : .04f));
            n.setFloat("heat", heat);
            if (heat >= 1) n.setBoolean("hot", true);
        }
        s.player.inventoryContainer.detectAndSendChanges();
    }

    private static void effect(Session s, int type) {
        Effect e = new Effect();
        e.entityId = s.player.getEntityId();
        e.kind = PortableWeapons.kind(s.gun).id;
        e.type = type;
        Vec3 muzzle = WeaponPose.muzzle(s.player, PortableWeapons.kind(s.gun));
        Vec3 eject = WeaponPose.eject(s.player, PortableWeapons.kind(s.gun));
        e.x = muzzle.xCoord;
        e.y = muzzle.yCoord;
        e.z = muzzle.zCoord;
        e.ejectX = eject.xCoord;
        e.ejectY = eject.yCoord;
        e.ejectZ = eject.zCoord;
        e.yaw = s.player.rotationYaw;
        e.pitch = s.player.rotationPitch;
        e.shotSerial = s.serial;
        WeaponNetwork.effect(s.player.worldObj, e);
    }

    private static void sendState(Session s) {
        WeaponKind k = PortableWeapons.kind(s.gun);
        if (k == null) return;
        Snapshot snap = new Snapshot();
        NBTTagCompound n = data(s.gun);
        snap.entityId = s.player.getEntityId();
        snap.kind = k.id;
        snap.reloadTicks = s.reload;
        snap.reloadDuration = k.reloadTicks;
        snap.shotSerial = s.serial;
        long now = s.player.worldObj.getTotalWorldTime();
        snap.shotInterval = k == WeaponKind.LM12 && n.hasKey("shotInterval")
            ? Math.max(1, Math.min(8, n.getInteger("shotInterval")))
            : k == WeaponKind.LM12 ? 8 : k.interval;
        snap.shotCooldown = (int) Math.max(0, Math.min(snap.shotInterval, n.getLong("nextShot") - now));
        snap.shotAge = n.hasKey("lastShot") ? (int) Math.max(0, Math.min(1000000, now - n.getLong("lastShot")))
            : 1000000;
        snap.heat = n.getFloat("heat");
        snap.overheated = n.getBoolean("hot");
        snap.spin = s.spin / 160f;
        snap.focusing = s.focusing;
        ItemStack a = pack(s.player, k);
        snap.ammoType = selected(s.gun, s.player, k);
        snap.remoteTicks = s.remoteTicks;
        snap.magazine = k == WeaponKind.QLZ04 || k == WeaponKind.SINGULARITY ? n.getInteger("magazine")
            : a != null && packId(a).equals(n.getString("loadedPack")) ? remaining(a) : 0;
        for (ItemStack ammo : s.player.inventory.mainInventory)
            if (matches(ammo, k, snap.ammoType)) snap.reserve += units(ammo, k);
        WeaponNetwork.state(s.player, snap);
    }
}
