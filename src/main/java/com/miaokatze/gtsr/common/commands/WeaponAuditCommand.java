package com.miaokatze.gtsr.common.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.miaokatze.gtsr.common.weapons.EntityWeaponProjectile;
import com.miaokatze.gtsr.common.weapons.PortableWeapons;
import com.miaokatze.gtsr.common.weapons.WeaponController;
import com.miaokatze.gtsr.common.weapons.WeaponEnchantments;
import com.miaokatze.gtsr.common.weapons.WeaponKind;
import com.miaokatze.gtsr.common.weapons.WeaponNetwork;
import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

/** Opt-in isolated-server fixture; never operates on a connected player's inventory or terrain. */
public final class WeaponAuditCommand extends CommandBase {

    private int checks;
    private final List<Entity> owned = new ArrayList<Entity>();
    private WorldServer world;
    private EntityPlayerMP player;
    private WeaponController controller;
    private EmbeddedChannel channel;
    private long originalTime;
    private boolean advancedTime;
    private double x, z;

    @Override
    public String getCommandName() {
        return "gtsrweaponaudit";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtsrweaponaudit";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        checks = 0;
        try {
            world = DimensionManager.getWorld(0);
            require(world != null, "overworld loaded");
            // Permission alone does not authorize a fixture in a real user save.
            require(
                "Smoke_weapons_v87".equals(
                    world.getWorldInfo()
                        .getWorldName()),
                "isolated world guard");
            originalTime = world.getTotalWorldTime();
            advancedTime = true;
            x = world.getSpawnPoint().posX + .5;
            z = world.getSpawnPoint().posZ + .5;
            require(
                world.getChunkProvider()
                    .chunkExists(((int) x) >> 4, ((int) z) >> 4),
                "spawn chunk already loaded");
            player = new EntityPlayerMP(
                MinecraftServer.getServer(),
                world,
                new GameProfile(UUID.randomUUID(), "WeaponAudit"),
                new ItemInWorldManager(world));
            NetworkManager manager = new NetworkManager(false);
            channel = new EmbeddedChannel(manager);
            player.playerNetServerHandler = new NetHandlerPlayServer(MinecraftServer.getServer(), manager, player);
            require(manager.isChannelOpen(), "in-memory fixture channel is active");
            player.setPosition(x, 230, z);
            player.rotationYaw = 0;
            player.rotationPitch = 0;
            controller = new WeaponController();
            registrationAndEnchantments();
            for (WeaponKind kind : WeaponKind.values()) inventory(kind);
            damage();
            collision(WeaponKind.T20);
            collision(WeaponKind.QLZ04);
            disconnect();
            report(
                sender,
                "PASS checks=" + checks
                    + " fixture=local-disconnected-player production-tick inventory damage collision; online-player/client-visual=NOT_TESTED");
        } catch (Throwable error) {
            report(sender, "FAIL checks=" + checks + " " + error);
            error.printStackTrace();
        } finally {
            for (Entity entity : owned) {
                entity.setDead();
                if (world != null) world.removeEntity(entity);
            }
            owned.clear();
            if (player != null) player.setDead();
            if (channel != null) channel.close();
            if (advancedTime) world.getWorldInfo()
                .incrementTotalWorldTime(originalTime);
            advancedTime = false;
            player = null;
            controller = null;
        }
    }

    private void registrationAndEnchantments() {
        int[] capacities = { 200, 100, 30 };
        float[] baseDamage = { 2, 10, 12 }, basePenetration = { 5, 8, 10 };
        WeaponEnchantments[] ench = { WeaponEnchantments.piercing, WeaponEnchantments.incendiary,
            WeaponEnchantments.hollowPoint, WeaponEnchantments.economy };
        for (int i = 0; i < ench.length; i++) {
            require(
                ench[i] != null && Enchantment.enchantmentsList[ench[i].effectId] == ench[i],
                "registered enchantment " + i);
            boolean inBooks = false;
            for (Enchantment book : Enchantment.enchantmentsBookList) if (book == ench[i]) inBooks = true;
            require(inBooks, "registered enchantment in vanilla book candidate list " + i);
            require(ench[i].getMaxLevel() == (i == 3 ? 5 : 3), "enchant cap " + i);
            for (int j = 0; j < i; j++) require(ench[i].effectId != ench[j].effectId, "unique enchant ID");
            for (int j = 0; j < ench.length; j++) require(
                ench[i].canApplyTogether(ench[j]) == (i != j && (i == 3 || j == 3)),
                "mutual exclusion " + i + "/" + j);
        }
        for (WeaponKind k : WeaponKind.values()) {
            ItemStack gun = new ItemStack(PortableWeapons.weapons[k.id]);
            ItemStack ammo = new ItemStack(PortableWeapons.ammo[k.id]);
            require(PortableWeapons.kind(gun) == k && PortableWeapons.ammoKind(ammo) == k, "real item registry " + k);
            require(
                ammo.getMaxDamage() == capacities[k.id] && WeaponController.remaining(ammo) == capacities[k.id],
                "pack capacity " + k);
            close(WeaponEnchantments.damage(k, gun), baseDamage[k.id], "base damage");
            close(WeaponEnchantments.penetration(k, gun), basePenetration[k.id], "base penetration");
            for (int e = 0; e < 3; e++) for (int level = 1; level <= 3; level++) {
                ItemStack enchanted = gun.copy();
                enchanted.addEnchantment(ench[e], level);
                close(
                    WeaponEnchantments.damage(k, enchanted),
                    baseDamage[k.id] * (1 + (e == 2 ? .25f : .05f) * level),
                    "ench damage");
                close(
                    WeaponEnchantments.penetration(k, enchanted),
                    basePenetration[k.id] * (1 + (e == 0 ? .2f : e == 2 ? -.1f : 0) * level),
                    "ench penetration");
            }
        }
        // Scripted RNG distinguishes an independent roll for every unit and exact 10% boundaries.
        final float[] rolls = { .05f, .15f, .25f, .35f, .45f, .55f, .65f, .75f, .85f, .95f };
        for (int level = 0; level <= 5; level++) {
            Random random = new Random(0) {

                private int cursor;

                @Override
                public float nextFloat() {
                    return rolls[cursor++ % 10];
                }
            };
            require(WeaponController.paidUnits(10, level, random) == 10 - level, "economy independent rolls " + level);
        }
        report(
            null,
            "IDS piercing=" + ench[0].effectId
                + " incendiary="
                + ench[1].effectId
                + " hollowPoint="
                + ench[2].effectId
                + " economy="
                + ench[3].effectId);
    }

    private ItemStack setup(WeaponKind k) {
        for (int i = 0; i < player.inventory.mainInventory.length; i++) player.inventory.mainInventory[i] = null;
        player.inventory.currentItem = 0;
        player.inventory.mainInventory[0] = new ItemStack(PortableWeapons.weapons[k.id]);
        ItemStack pack = new ItemStack(PortableWeapons.ammo[k.id]);
        player.inventory.mainInventory[1] = pack;
        return pack;
    }

    private int tick(boolean fire, boolean reload) {
        // Bounded fixture clock advances production cadence/heat logic, then finally restores the save clock.
        world.getWorldInfo()
            .incrementTotalWorldTime(world.getTotalWorldTime() + 1);
        WeaponNetwork.enqueueControls(player, 0, fire, false, reload);
        controller.serverTick();
        int shots = 0;
        for (Object object : new ArrayList<Object>(world.loadedEntityList))
            if (object instanceof EntityWeaponProjectile) {
                Entity entity = (Entity) object;
                // Only this command's projectile origin; normal saves are rejected before setup.
                if (!entity.isDead
                    && entity.getDistanceSq(player.posX, player.posY + player.getEyeHeight(), player.posZ) < 4) {
                    shots++;
                    owned.add(entity);
                    entity.setDead();
                    world.removeEntity(entity);
                }
            }
        return shots;
    }

    private void reload(WeaponKind k) {
        for (int i = 0; i < k.reloadTicks; i++) tick(false, i == 0);
    }

    private void inventory(WeaponKind k) {
        ItemStack pack = setup(k), gun = player.getHeldItem();
        tick(false, true);
        player.inventory.currentItem = 2;
        controller.serverTick();
        player.inventory.currentItem = 0;
        for (int i = 0; i < k.reloadTicks + 1; i++) tick(false, false);
        require(pack.getItemDamage() == 0, "cancel reload charges nothing " + k);
        reload(k);
        require(pack.getItemDamage() == (k == WeaponKind.QLZ04 ? 8 : 1), "first reload actual debit " + k);
        int before = pack.getItemDamage();
        reload(k);
        require(pack.getItemDamage() == before, "same pack reload no duplicate fee " + k);
        NBTTagCompound saved = new NBTTagCompound();
        pack.writeToNBT(saved);
        ItemStack restored = ItemStack.loadItemStackFromNBT(saved);
        require(
            restored.getItemDamage() == before && ItemStack.areItemStackTagsEqual(restored, pack),
            "ammo durability NBT roundtrip " + k);
        player.inventory.mainInventory[1] = restored;
        reload(k);
        require(restored.getItemDamage() == before, "saved pack reload no duplicate fee " + k);
        List<Integer> firingTicks = new ArrayList<Integer>();
        int duration = k == WeaponKind.LM12 ? 70 : 18;
        for (int i = 1; i <= duration; i++) if (tick(true, false) > 0) firingTicks.add(i);
        require(!firingTicks.isEmpty(), "real firing produces entity " + k);
        if (k == WeaponKind.LM12) require(firingTicks.get(0) == 40, "LM12 40 tick spin gate");
        else for (int i = 1; i < firingTicks.size(); i++) require(
            firingTicks.get(i) - firingTicks.get(i - 1) == (k == WeaponKind.T20 ? 4 : 5),
            "real shot interval " + k);
        require(
            restored.getItemDamage() == before + (k == WeaponKind.QLZ04 ? 0 : firingTicks.size()),
            "shot inventory settlement " + k);
        if (k == WeaponKind.QLZ04) {
            int magazine = WeaponController.data(gun)
                .getInteger("magazine");
            require(magazine == 8 - firingTicks.size(), "magazine firing debit");
            reload(k);
            require(restored.getItemDamage() == 8 + firingTicks.size(), "manual reload charges only missing rounds");
            ItemStack partial = setup(k);
            partial.setItemDamage(27);
            reload(k);
            require(
                partial.getItemDamage() == 30 && WeaponController.data(player.getHeldItem())
                    .getInteger("magazine") == 3,
                "partial three rounds no first fee");
            ItemStack all = setup(k);
            int total = 0;
            for (int cycle = 0; cycle < 4; cycle++) {
                reload(k);
                int rounds = WeaponController.data(player.getHeldItem())
                    .getInteger("magazine");
                for (int i = 0; i < 5; i++) tick(false, false);
                for (int i = 0; i < 1 + (rounds - 1) * 5; i++) total += tick(true, false);
                tick(false, false);
            }
            require(
                total == 30 && all.getItemDamage() == 30,
                "full 30-round pack fires exactly 30 without double debit");
        } else {
            ItemStack anotherGun = new ItemStack(PortableWeapons.weapons[k.id]);
            player.inventory.mainInventory[0] = anotherGun;
            reload(k);
            require(
                restored.getItemDamage() == before + firingTicks.size(),
                "switch gun preserves existing pack chain");
            ItemStack fresh = new ItemStack(PortableWeapons.ammo[k.id]);
            player.inventory.mainInventory[1] = fresh;
            reload(k);
            require(fresh.getItemDamage() == 1, "new pack pays one initialization fee");
            ItemStack nearlyEmpty = new ItemStack(PortableWeapons.ammo[k.id]);
            nearlyEmpty.setItemDamage(k.capacity - 1);
            player.inventory.mainInventory[1] = nearlyEmpty;
            reload(k);
            require(
                nearlyEmpty.getItemDamage() == k.capacity - 1 && !WeaponController.data(nearlyEmpty)
                    .getBoolean("chain"),
                "one unchained unit cannot initialize");
        }
    }

    private EntityCow cow(double dx, double dz) {
        EntityCow c = new EntityCow(world);
        c.getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .setBaseValue(100);
        c.setHealth(100);
        c.setPosition(x + dx, 230, z + dz);
        owned.add(c);
        require(world.spawnEntityInWorld(c), "spawn target");
        return c;
    }

    private void damage() {
        EntityCow target = cow(5, 0);
        EntityWeaponProjectile projectile = new EntityWeaponProjectile(world, player, WeaponKind.LM12, 2, 5, 0);
        target.hurtResistantTime = 19;
        require(EntityWeaponProjectile.applyDamage(target, 2, 5, 0, projectile, player), "first independent damage");
        require(EntityWeaponProjectile.applyDamage(target, 2, 5, 0, projectile, player), "second independent damage");
        close(target.getHealth(), 96, "hurt resistant time does not swallow shot");
        require(target.hurtResistantTime == 19, "prior hurt resistance restored");
        for (int level = 1; level <= 3; level++) {
            target.extinguish();
            target.setHealth(100);
            require(
                EntityWeaponProjectile.applyDamage(target, 2, 5, level, projectile, player),
                "incendiary damage accepted");
            NBTTagCompound burning = new NBTTagCompound();
            target.writeToNBT(burning);
            require(burning.getShort("Fire") == level * 100, "incendiary five seconds per level " + level);
        }
        EntityZombie armored = new EntityZombie(world);
        owned.add(armored);
        armored.getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .setBaseValue(100);
        armored.setHealth(100);
        armored.setCurrentItemOrArmor(1, new ItemStack(Items.diamond_boots));
        armored.setCurrentItemOrArmor(2, new ItemStack(Items.diamond_leggings));
        armored.setCurrentItemOrArmor(3, new ItemStack(Items.diamond_chestplate));
        armored.setCurrentItemOrArmor(4, new ItemStack(Items.diamond_helmet));
        require(armored.getTotalArmorValue() >= 20, "real armor equipment");
        for (WeaponKind k : WeaponKind.values()) {
            armored.setHealth(100);
            require(
                EntityWeaponProjectile.applyDamage(armored, k.damage, k.armorPenetration, 0, projectile, player),
                "armored damage accepted");
            float expected = 100
                - k.damage * (25 - Math.min(20, Math.max(0, armored.getTotalArmorValue() - k.armorPenetration))) / 25f;
            close(armored.getHealth(), expected, "real armor point penetration " + k);
        }
    }

    private void collision(WeaponKind kind) {
        EntityCow center = cow(0, 3), near = cow(1.5, 3), far = cow(3, 3);
        int bx = (int) x, bz = (int) z + 3;
        Object block = world.getBlock(bx, 230, bz);
        int metadata = world.getBlockMetadata(bx, 230, bz);
        EntityWeaponProjectile p = new EntityWeaponProjectile(
            world,
            player,
            kind,
            kind.damage,
            kind.armorPenetration,
            0);
        p.setPosition(x, 230.6, z + .5);
        p.motionX = 0;
        p.motionY = 0;
        p.motionZ = 4;
        owned.add(p);
        require(world.spawnEntityInWorld(p), "spawn projectile");
        net.minecraft.util.Vec3 start = net.minecraft.util.Vec3.createVectorHelper(p.posX, p.posY, p.posZ);
        net.minecraft.util.Vec3 end = start.addVector(p.motionX, p.motionY, p.motionZ);
        List<?> candidates = world.getEntitiesWithinAABBExcludingEntity(
            p,
            p.boundingBox.addCoord(p.motionX, p.motionY, p.motionZ)
                .expand(.3, .3, .3));
        report(
            null,
            "COLLISION kind=" + kind
                + " start="
                + start
                + " end="
                + end
                + " targetBox="
                + center.boundingBox
                + " targetCollidable="
                + center.canBeCollidedWith()
                + " candidateContainsTarget="
                + candidates.contains(center)
                + " intercept="
                + center.boundingBox.expand(.1, .1, .1)
                    .calculateIntercept(start, end));
        require(
            center.canBeCollidedWith() && candidates.contains(center),
            "fixture target present in actual world collision query");
        require(
            center.boundingBox.expand(.1, .1, .1)
                .calculateIntercept(start, end) != null,
            "fixture segment intersects target bounds");
        p.onUpdate();
        require(p.isDead, "continuous segment finds real target");
        close(center.getHealth(), 100 - kind.damage, "direct hit");
        close(near.getHealth(), kind == WeaponKind.QLZ04 ? 88 : 100, "radius two / T20 no AOE");
        close(far.getHealth(), 100, "outside radius unaffected");
        require(
            world.getBlock(bx, 230, bz) == block && world.getBlockMetadata(bx, 230, bz) == metadata,
            "impact does not change terrain");
        for (Entity e : new Entity[] { center, near, far, p }) {
            e.setDead();
            world.removeEntity(e);
        }
    }

    private void disconnect() {
        ItemStack ammo = setup(WeaponKind.T20);
        reload(WeaponKind.T20);
        WeaponNetwork.enqueueControls(player, 9, true, false, true);
        controller.serverTick();
        require(ammo.getItemDamage() == 1, "forged out of range slot rejected");
        player.inventory.currentItem = 1;
        WeaponNetwork.enqueueControls(player, 0, true, false, true);
        controller.serverTick();
        require(ammo.getItemDamage() == 1, "forged held slot rejected");
        player.inventory.currentItem = 0;
        channel.close();
        WeaponNetwork.enqueueControls(player, 0, true, false, true);
        controller.serverTick();
        require(ammo.getItemDamage() == 1, "disconnected queued input rejected");
    }

    private void require(boolean pass, String label) {
        if (!pass) throw new IllegalStateException(label);
        checks++;
    }

    private void close(float actual, float expected, String label) {
        require(Math.abs(actual - expected) < .001f, label + " expected=" + expected + " actual=" + actual);
    }

    private static void report(ICommandSender sender, String result) {
        String text = "[GTSR-WEAPON-AUDIT] " + result;
        System.out.println(text);
        if (sender != null) sender.addChatMessage(new ChatComponentText(text));
    }
}
