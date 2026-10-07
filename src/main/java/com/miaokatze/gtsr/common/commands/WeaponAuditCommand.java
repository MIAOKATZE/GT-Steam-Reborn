package com.miaokatze.gtsr.common.commands;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.weapons.Effect;
import com.miaokatze.gtsr.common.weapons.EntityWeaponProjectile;
import com.miaokatze.gtsr.common.weapons.EntityWeaponSingularity;
import com.miaokatze.gtsr.common.weapons.PortableWeapons;
import com.miaokatze.gtsr.common.weapons.Snapshot;
import com.miaokatze.gtsr.common.weapons.WeaponController;
import com.miaokatze.gtsr.common.weapons.WeaponEnchantments;
import com.miaokatze.gtsr.common.weapons.WeaponKind;
import com.miaokatze.gtsr.common.weapons.WeaponNetwork;
import com.miaokatze.gtsr.common.weapons.WeaponPose;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
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
    private Channel auditNetwork;
    private Snapshot capturedState;
    private Effect capturedShot;
    private EventBus auditTickBus;

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
            auditTickBus = new EventBus();
            auditTickBus.register(controller);
            captureNetwork();
            registrationAndEnchantments();
            newWeaponMechanics();
            for (WeaponKind kind : new WeaponKind[] { WeaponKind.LM12, WeaponKind.T20, WeaponKind.QLZ04 })
                inventory(kind);
            cooling();
            muzzleAndWall();
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
            if (auditNetwork != null && auditNetwork.pipeline()
                .get("gtsrWeaponAuditObserver") != null) auditNetwork.pipeline()
                    .remove("gtsrWeaponAuditObserver");
            auditNetwork = null;
            if (auditTickBus != null && controller != null) auditTickBus.unregister(controller);
            auditTickBus = null;
            if (advancedTime) world.getWorldInfo()
                .incrementTotalWorldTime(originalTime);
            advancedTime = false;
            player = null;
            controller = null;
        }
    }

    private void control(boolean fire, boolean reload, boolean switchAmmo) {
        world.getWorldInfo()
            .incrementTotalWorldTime(world.getTotalWorldTime() + 1);
        WeaponNetwork.enqueueControls(player, 0, fire, false, reload, switchAmmo);
        controller.serverTick();
    }

    private void switchingMechanics() {
        ItemStack ordinary = setup(WeaponKind.QLZ04);
        ItemStack mimic = new ItemStack(PortableWeapons.mimicAmmo[2]);
        // Initialize the ordinary selection before adding another type, then prove selection stays sticky.
        control(false, false, false);
        player.inventory.mainInventory[2] = mimic;
        reload(WeaponKind.QLZ04);
        ItemStack gun = player.getHeldItem();
        require(
            ordinary.getItemDamage() == 12 && WeaponController.data(gun)
                .getInteger("ammoType") == 0,
            "QLZ sticky ordinary reload");
        ordinary.setItemDamage(0);
        control(false, false, true);
        require(
            ordinary.getItemDamage() == 0 && WeaponController.data(gun)
                .getInteger("magazine") == 0,
            "QLZ full-pack refund overflow discarded");
        for (int i = 0; i < 39; i++) control(false, false, false);
        require(
            mimic.getItemDamage() == 12 && WeaponController.data(gun)
                .getInteger("magazine") == 12,
            "QLZ V switches to mimic and reloads twelve");
        control(false, false, true);
        require(mimic.getItemDamage() == 0, "QLZ paid magazine returned to mimic");
        ordinary = setup(WeaponKind.QLZ04);
        gun = player.getHeldItem();
        control(false, false, false);
        player.inventory.mainInventory[2] = new ItemStack(PortableWeapons.mimicAmmo[2]);
        WeaponController.data(gun)
            .setInteger("magazine", 12);
        WeaponController.data(gun)
            .setInteger("paidMagazine", 0);
        ordinary.setItemDamage(20);
        control(false, false, true);
        require(ordinary.getItemDamage() == 20, "QLZ economy free rounds cannot be refunded as new ammunition");
    }

    private void singularityMechanics() {
        for (int i = 0; i < player.inventory.mainInventory.length; i++) player.inventory.mainInventory[i] = null;
        player.inventory.currentItem = 0;
        ItemStack gun = new ItemStack(PortableWeapons.weapons[3]);
        player.inventory.mainInventory[0] = gun;
        player.inventory.mainInventory[1] = GTSRItemList.SteamEntangledSingularity.get(4);
        control(false, true, false);
        for (int i = 0; i < 98; i++) control(false, false, false);
        require(
            WeaponController.data(gun)
                .getInteger("magazine") == 0,
            "107 no load at tick 99");
        control(false, false, false);
        require(
            WeaponController.data(gun)
                .getInteger("magazine") == 1 && player.inventory.mainInventory[1].stackSize == 3,
            "107 load at tick 100");
        player.inventory.mainInventory[2] = GTSRItemList.CriticalSteamEntangledSingularity.get(4);
        control(false, false, false);
        require(
            WeaponController.data(gun)
                .getInteger("ammoType") == 0,
            "107 type sticks when higher ammo arrives");
        control(false, false, true);
        require(
            player.inventory.mainInventory[1].stackSize == 4 && WeaponController.data(gun)
                .getInteger("magazine") == 0,
            "107 switch refunds paid round and starts reload");
        for (int i = 0; i < 99; i++) control(false, false, false);
        require(
            WeaponController.data(gun)
                .getInteger("loadedType") == 1,
            "107 switched critical reload");
        control(true, false, false);
        EntityWeaponProjectile projectile = null;
        for (Object object : world.loadedEntityList)
            if (object instanceof EntityWeaponProjectile && !((Entity) object).isDead)
                projectile = (EntityWeaponProjectile) object;
        require(projectile != null && projectile.critical(), "107 critical projectile spawned");
        owned.add(projectile);
        require(capturedState.reloadTicks == 100, "107 shot automatically starts full 100 tick reload");
        for (int i = 0; i < 12; i++) control(true, false, false);
        require(!projectile.isDead, "107 held trigger does not remotely detonate");
        control(false, false, false);
        control(true, false, false);
        for (int i = 0; i < 8; i++) control(true, false, false);
        require(!projectile.isDead, "107 remote gesture waits 9 ticks");
        control(true, false, false);
        require(projectile.isDead, "107 remote detonation at tick 10 during automatic reload");
        require(capturedState.remoteTicks == 20, "107 remote action continues for twenty ticks after trigger");
        int nodes = 0;
        for (Object object : world.loadedEntityList)
            if (object instanceof EntityWeaponSingularity && !((Entity) object).isDead) nodes++;
        for (int i = 0; i < 19; i++) control(false, false, false);
        require(capturedState.remoteTicks == 1, "107 remote action still active at tick 29");
        control(false, false, false);
        require(capturedState.remoteTicks == 0, "107 remote action finishes at tick 30");
        int after = 0;
        for (Object object : world.loadedEntityList)
            if (object instanceof EntityWeaponSingularity && !((Entity) object).isDead) after++;
        require(after == nodes && nodes == 1, "107 one remote action creates exactly one node");
        // 12 held ticks + one release + 30 action ticks elapsed since the shot.
        for (int i = 0; i < 56; i++) control(false, false, false);
        require(
            WeaponController.data(gun)
                .getInteger("magazine") == 0,
            "107 automatic reload incomplete at shot plus 99");
        control(false, false, false);
        require(
            WeaponController.data(gun)
                .getInteger("magazine") == 1,
            "107 automatic reload finishes without firing at shot plus 100");
        player.inventory.mainInventory[1] = null;
        player.inventory.mainInventory[2] = null;
        control(true, false, false);
        require(
            capturedState.magazine == 0 && capturedState.reloadTicks == 0,
            "107 empty inventory cannot begin automatic reload");
        for (int i = 0; i < 105; i++) control(false, false, false);
        require(capturedState.magazine == 0, "107 empty inventory cannot fabricate ammunition");
        // control() advances only the controller: the empty-inventory shot has not ticked away.
        // Remove this sub-fixture's projectile before later audits change the player pose/weapon.
        for (Object object : new ArrayList<Object>(world.loadedEntityList))
            if (object instanceof EntityWeaponProjectile) {
                Entity projectileEntity = (Entity) object;
                owned.add(projectileEntity);
                projectileEntity.setDead();
                world.removeEntity(projectileEntity);
            }
        for (Object object : new ArrayList<Object>(world.loadedEntityList))
            if (object instanceof EntityWeaponSingularity) {
                owned.add((Entity) object);
                ((Entity) object).setDead();
                world.removeEntity((Entity) object);
            }
        for (boolean critical : new boolean[] { false, true }) {
            EntityCow center = cow(0, 0), outside = cow(critical ? 11 : 6, 0);
            center.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                .setBaseValue(300);
            center.setHealth(300);
            EntityWeaponSingularity node = new EntityWeaponSingularity(
                world,
                center.posX,
                center.posY + center.height * .5,
                center.posZ,
                player.getUniqueID(),
                critical);
            owned.add(node);
            world.spawnEntityInWorld(node);
            for (int i = 0; i < 159; i++) node.onUpdate();
            close(center.getHealth(), 300 - 7 * (critical ? 5 : 2), "107 seven DOT ticks before end");
            close(outside.getHealth(), 100, "107 outside gravity radius");
            require(!node.isDead, "107 node lasts through tick 159");
            node.onUpdate();
            require(node.isDead, "107 node ends at tick 160");
            close(
                center.getHealth(),
                300 - 8 * (critical ? 5 : 2) - (critical ? 50 : 20) - (critical ? 15 : 5),
                "107 DOT plus physical and magic burst");
            center.setDead();
            outside.setDead();
            world.removeEntity(center);
            world.removeEntity(outside);
            NBTTagCompound saved = new NBTTagCompound();
            node.writeToNBT(saved);
            EntityWeaponSingularity restored = new EntityWeaponSingularity(world);
            restored.readFromNBT(saved);
            require(
                restored.critical() == critical && restored.life() == 160,
                "107 node NBT preserves type and lifetime");
            ByteBuf bytes = Unpooled.buffer();
            node.writeSpawnData(bytes);
            restored.readSpawnData(bytes);
            bytes.release();
            require(restored.critical() == critical, "107 node spawn type preserved");
        }
    }

    private void newWeaponMechanics() {
        ItemStack gun = new ItemStack(PortableWeapons.weapons[WeaponKind.SINGULARITY.id]);
        require(PortableWeapons.kind(gun) == WeaponKind.SINGULARITY, "107 registered");
        require(WeaponKind.SINGULARITY.reloadTicks == 100, "107 five-second reload");
        close((float) WeaponKind.SINGULARITY.projectileSpeed, .45f, "107 old grenade speed at 30 percent");
        close((float) WeaponKind.SINGULARITY.gravity, .00315f, "107 preserves old grenade spatial arc");
        for (WeaponEnchantments ench : new WeaponEnchantments[] { WeaponEnchantments.piercing,
            WeaponEnchantments.incendiary, WeaponEnchantments.hollowPoint }) {
            require(!ench.canApply(gun), "107 rejects combat enchantment");
            gun.addEnchantment(ench, 3);
            require(WeaponEnchantments.level(gun, ench) == 0, "107 ignores injected combat enchantment");
        }
        require(WeaponEnchantments.economy.canApply(gun), "107 accepts economy");
        for (WeaponKind kind : new WeaponKind[] { WeaponKind.LM12, WeaponKind.T20, WeaponKind.QLZ04 }) {
            ItemStack mimic = new ItemStack(PortableWeapons.mimicAmmo[kind.id]);
            require(((PortableWeapons.AmmoItem) mimic.getItem()).mimic, "mimic glint policy " + kind);
            mimic.setItemDamage(kind.capacity);
            require(WeaponController.remaining(mimic) == 0, "empty mimic retained " + kind);
            player.inventory.mainInventory[4] = mimic;
            player.getEntityData()
                .setLong("gtsr.lastFiring", world.getTotalWorldTime() - 100);
            player.getEntityData()
                .setLong("gtsr.lastWeaponShot", world.getTotalWorldTime() - 100);
            PortableWeapons.AmmoItem item = (PortableWeapons.AmmoItem) mimic.getItem();
            item.onUpdate(mimic, world, player, 4, false);
            world.getWorldInfo()
                .incrementTotalWorldTime(world.getTotalWorldTime() + 40);
            item.onUpdate(mimic, world, player, 4, false);
            require(
                WeaponController.remaining(mimic) == (kind == WeaponKind.LM12 ? 10 : kind == WeaponKind.T20 ? 3 : 1),
                "mimic two-second regeneration " + kind);
            require(player.inventory.mainInventory[4] == mimic, "empty mimic is retained " + kind);
            mimic.setItemDamage(kind.capacity);
            player.getEntityData()
                .setLong("gtsr.lastFiring", world.getTotalWorldTime());
            world.getWorldInfo()
                .incrementTotalWorldTime(world.getTotalWorldTime() + 99);
            item.onUpdate(mimic, world, player, 4, false);
            require(WeaponController.remaining(mimic) == 0, "mimic cannot regenerate before quiet tick 100 " + kind);
            player.inventory.mainInventory[4] = null;
        }
        require(
            !com.miaokatze.gtsr.common.weapons.EntityWeaponSingularity.eligible(player, player.getUniqueID()),
            "107 excludes shooter by UUID");
        require(
            !com.miaokatze.gtsr.common.weapons.EntityWeaponSingularity
                .eligible(new com.miaokatze.gtsr.common.weapons.EntityWeaponSingularity(world), null),
            "107 excludes gravity nodes");
        switchingMechanics();
        singularityMechanics();
    }

    private void registrationAndEnchantments() {
        int[] capacities = { 500, 160, 60 };
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
        for (WeaponKind k : new WeaponKind[] { WeaponKind.LM12, WeaponKind.T20, WeaponKind.QLZ04 }) {
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
        capturedState = null;
        capturedShot = null;
        auditTickBus.post(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        require(capturedState != null, "actual server Snapshot observed");
        require(capturedState.entityId == player.getEntityId(), "Snapshot belongs to local fixture");
        WeaponKind held = PortableWeapons.kind(player.getHeldItem());
        if (held != null) {
            int interval = capturedState.shotInterval;
            require(
                held == WeaponKind.LM12 ? interval >= 1 && interval <= 8
                    : interval == (held == WeaponKind.T20 ? 8 : 12),
                "actual Snapshot interval");
            require(
                capturedState.shotCooldown >= 0 && capturedState.shotCooldown <= interval,
                "actual Snapshot cooldown bounds");
            if (capturedShot != null) {
                require(
                    capturedState.shotAge == 0 && capturedState.shotCooldown == interval,
                    "successful shot Snapshot age and cooldown");
                packetRoundtrip(capturedState, capturedShot);
            }
        }
        int shots = 0;
        for (Object object : new ArrayList<Object>(world.loadedEntityList))
            if (object instanceof EntityWeaponProjectile) {
                Entity entity = (Entity) object;
                // Only this command's projectile origin; normal saves are rejected before setup.
                if (!entity.isDead
                    && entity.getDistanceSq(player.posX, player.posY + player.getEyeHeight(), player.posZ) < 9) {
                    shots++;
                    Vec3 expected = WeaponPose.muzzle(player, held);
                    require(
                        entity.getDistanceSq(expected.xCoord, expected.yCoord, expected.zCoord) < 1.0E-12,
                        "server projectile starts at physical muzzle");
                    owned.add(entity);
                    entity.setDead();
                    world.removeEntity(entity);
                }
            }
        return shots;
    }

    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass()
            .getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }

    private void captureNetwork() throws Exception {
        Field net = WeaponNetwork.class.getDeclaredField("NET");
        net.setAccessible(true);
        Object wrapper = net.get(null);
        auditNetwork = (Channel) ((Map<?, ?>) field(wrapper, "channels")).get(Side.SERVER);
        auditNetwork.pipeline()
            .addLast("gtsrWeaponAuditObserver", new ChannelOutboundHandlerAdapter() {

                @Override
                public void write(ChannelHandlerContext context, Object message, ChannelPromise promise)
                    throws Exception {
                    if (message instanceof WeaponNetwork.StatePacket) capturedState = (Snapshot) field(message, "s");
                    if (message instanceof WeaponNetwork.EffectPacket) {
                        Effect effect = (Effect) field(message, "e");
                        if (effect.type == 0) capturedShot = effect;
                    }
                    context.write(message, promise);
                }
            });
    }

    private void packetRoundtrip(Snapshot state, Effect effect) {
        try {
            WeaponNetwork.StatePacket packet = new WeaponNetwork.StatePacket();
            Field s = packet.getClass()
                .getDeclaredField("s");
            s.setAccessible(true);
            s.set(packet, state);
            ByteBuf bytes = Unpooled.buffer();
            packet.toBytes(bytes);
            WeaponNetwork.StatePacket read = new WeaponNetwork.StatePacket();
            read.fromBytes(bytes);
            Snapshot result = (Snapshot) s.get(read);
            require(
                bytes.readableBytes() == 0 && result.shotInterval == state.shotInterval
                    && result.shotCooldown == state.shotCooldown
                    && result.shotAge == state.shotAge
                    && result.shotSerial == state.shotSerial,
                "actual Snapshot added fields roundtrip");
            bytes.release();
            WeaponNetwork.EffectPacket p = new WeaponNetwork.EffectPacket();
            Field e = p.getClass()
                .getDeclaredField("e");
            e.setAccessible(true);
            e.set(p, effect);
            bytes = Unpooled.buffer();
            p.toBytes(bytes);
            WeaponNetwork.EffectPacket r = new WeaponNetwork.EffectPacket();
            r.fromBytes(bytes);
            Effect out = (Effect) e.get(r);
            require(
                bytes.readableBytes() == 0 && out.x == effect.x
                    && out.y == effect.y
                    && out.z == effect.z
                    && out.ejectX == effect.ejectX
                    && out.ejectY == effect.ejectY
                    && out.ejectZ == effect.ejectZ
                    && out.shotSerial == effect.shotSerial,
                "actual Effect muzzle/eject roundtrip");
            bytes.release();
            WeaponKind kind = WeaponKind.fromId(effect.kind);
            Vec3 muzzle = WeaponPose.muzzle(player, kind), eject = WeaponPose.eject(player, kind);
            require(
                muzzle.squareDistanceTo(Vec3.createVectorHelper(effect.x, effect.y, effect.z)) < 1.0E-12,
                "actual Effect uses physical muzzle");
            require(
                eject.squareDistanceTo(Vec3.createVectorHelper(effect.ejectX, effect.ejectY, effect.ejectZ)) < 1.0E-12,
                "actual Effect uses physical eject port");
        } catch (Exception error) {
            throw new IllegalStateException("packet reflection fixture", error);
        }
    }

    private void cooling() {
        for (WeaponKind kind : new WeaponKind[] { WeaponKind.LM12, WeaponKind.T20 }) {
            setup(kind);
            reload(kind);
            NBTTagCompound n = WeaponController.data(player.getHeldItem());
            float gain = kind == WeaponKind.LM12 ? .00625f : .04f, rate = kind == WeaponKind.LM12 ? .009f : .012f;
            int successful = 0;
            for (int i = 0; i < 500 && successful < 10; i++) {
                int fired = tick(true, false);
                successful += fired;
                if (fired > 0)
                    close(n.getFloat("heat"), successful * gain, "continuous successful shots never cool " + kind);
            }
            require(successful == 10, "generate actual firing heat for quiet window " + kind);
            float heat = n.getFloat("heat");
            long lastShot = n.getLong("lastShot");
            for (int i = 0; i < 19; i++) tick(false, false);
            require(world.getTotalWorldTime() == lastShot + 19, "fixture reaches real nineteen tick boundary");
            close(n.getFloat("heat"), heat, "nineteen quiet ticks do not cool " + kind);
            tick(false, false);
            close(n.getFloat("heat"), heat - rate, "twentieth quiet tick cools once " + kind);
            for (int i = 0; i < 10; i++) tick(false, false);
            close(n.getFloat("heat"), Math.max(0, heat - 11 * rate), "subsequent ten ticks triple cooling " + kind);
            setup(kind);
            reload(kind);
            n = WeaponController.data(player.getHeldItem());
            for (int i = 0; i < 1000 && !n.getBoolean("hot"); i++) tick(true, false);
            require(n.getBoolean("hot"), "actual successful shots reach overheat " + kind);
            heat = n.getFloat("heat");
            lastShot = n.getLong("lastShot");
            for (int i = 0; i < 19; i++)
                require(tick(true, false) == 0, "overheat stops shots without trigger release " + kind);
            require(world.getTotalWorldTime() == lastShot + 19, "overheat quiet window starts at last successful shot");
            close(n.getFloat("heat"), heat, "overheated nineteen ticks no cooling " + kind);
            require(tick(true, false) == 0, "twentieth tick still locked " + kind);
            close(n.getFloat("heat"), heat - rate, "overheated twentieth tick begins cooling " + kind);
            int resumed = 0;
            for (int i = 0; i < 200 && resumed == 0; i++) resumed += tick(true, false);
            require(resumed == 1 && !n.getBoolean("hot"), "actual cooling unlocks and resumes held trigger " + kind);
        }
    }

    private void muzzleAndWall() {
        double originalZ = player.posZ;
        player.setPosition(x, 230, world.getSpawnPoint().posZ + .9);
        int bx = (int) Math.floor(x), by = 231, bz = world.getSpawnPoint().posZ + 1;
        require(
            world.getChunkProvider()
                .chunkExists(bx >> 4, bz >> 4),
            "wall fixture chunk already loaded");
        Block prior = world.getBlock(bx, by, bz);
        int metadata = world.getBlockMetadata(bx, by, bz);
        require(prior == Blocks.air, "owned wall fixture is empty air");
        try {
            require(world.setBlock(bx, by, bz, Blocks.stone, 0, 2), "install bounded fixture wall");
            for (WeaponKind kind : new WeaponKind[] { WeaponKind.LM12, WeaponKind.T20, WeaponKind.QLZ04 }) {
                EntityWeaponProjectile p = new EntityWeaponProjectile(
                    world,
                    player,
                    kind,
                    kind.damage,
                    kind.armorPenetration,
                    0);
                owned.add(p);
                Vec3 muzzle = WeaponPose.muzzle(player, kind);
                require(
                    p.getDistanceSq(muzzle.xCoord, muzzle.yCoord, muzzle.zCoord) < 1.0E-12,
                    "constructor begins at real muzzle " + kind);
                require(muzzle.zCoord > bz + 1, "muzzle extends beyond obstruction " + kind);
                require(world.spawnEntityInWorld(p), "spawn original trajectory wall projectile");
                p.onUpdate();
                require(p.isDead, "first tick eye-to-muzzle wall blocks beyond-wall spawn " + kind);
                require(world.getBlock(bx, by, bz) == Blocks.stone, "wall impact does not destroy obstruction " + kind);
            }
        } finally {
            world.setBlock(bx, by, bz, prior, metadata, 2);
            player.setPosition(x, 230, originalZ);
        }
        require(
            world.getBlock(bx, by, bz) == prior && world.getBlockMetadata(bx, by, bz) == metadata,
            "fixture wall restored");
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
        require(pack.getItemDamage() == (k == WeaponKind.QLZ04 ? 12 : 0), "reload charges only QLZ magazine " + k);
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
        int duration = k == WeaponKind.LM12 ? 70 : 30;
        for (int i = 1; i <= duration; i++) if (tick(true, false) > 0) firingTicks.add(i);
        require(!firingTicks.isEmpty(), "real firing produces entity " + k);
        if (k == WeaponKind.LM12)
            require(firingTicks.get(0) == 20, "LM12 doubled acceleration reaches spin gate at tick 20");
        else for (int i = 1; i < firingTicks.size(); i++) require(
            firingTicks.get(i) - firingTicks.get(i - 1) == (k == WeaponKind.T20 ? 8 : 12),
            "real shot interval " + k);
        require(
            restored.getItemDamage() == before + (k == WeaponKind.QLZ04 ? 0 : firingTicks.size()),
            "shot inventory settlement " + k);
        if (k == WeaponKind.QLZ04) {
            int magazine = WeaponController.data(gun)
                .getInteger("magazine");
            require(magazine == 12 - firingTicks.size(), "magazine firing debit");
            reload(k);
            require(restored.getItemDamage() == 12 + firingTicks.size(), "manual reload charges only missing rounds");
            ItemStack partial = setup(k);
            partial.setItemDamage(57);
            reload(k);
            require(
                partial.getItemDamage() == 60 && WeaponController.data(player.getHeldItem())
                    .getInteger("magazine") == 3,
                "partial three rounds no first fee");
            ItemStack all = setup(k);
            int total = 0;
            for (int cycle = 0; cycle < 5; cycle++) {
                reload(k);
                int rounds = WeaponController.data(player.getHeldItem())
                    .getInteger("magazine");
                for (int i = 0; i < 12; i++) tick(false, false);
                for (int i = 0; i < 1 + (rounds - 1) * 12; i++) total += tick(true, false);
                tick(false, false);
            }
            require(
                total == 60 && all.getItemDamage() == 60,
                "full 60-round pack fires exactly 60 without double debit");
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
            require(
                fresh.getItemDamage() == 0 && WeaponController.data(fresh)
                    .getBoolean("chain"),
                "new pack links without debit");
            ItemStack nearlyEmpty = new ItemStack(PortableWeapons.ammo[k.id]);
            nearlyEmpty.setItemDamage(k.capacity - 1);
            player.inventory.mainInventory[1] = nearlyEmpty;
            reload(k);
            require(
                nearlyEmpty.getItemDamage() == k.capacity - 1 && WeaponController.data(nearlyEmpty)
                    .getBoolean("chain"),
                "one unchained unit links free");
            int single = 0;
            for (int i = 0; i < 50; i++) single += tick(true, false);
            require(single == 1 && nearlyEmpty.getItemDamage() == k.capacity, "single new round is usable");
            require(player.inventory.mainInventory[1] == null, "ordinary empty pack destroyed immediately " + k);
            ItemStack full = setup(k);
            reload(k);
            int total = 0;
            for (int i = 0; i < 4000 && WeaponController.remaining(full) > 0; i++) total += tick(true, false);
            require(
                total == (k.capacity) && full.getItemDamage() == k.capacity,
                "complete belt supplies every round " + k);
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
        for (WeaponKind k : new WeaponKind[] { WeaponKind.LM12, WeaponKind.T20, WeaponKind.QLZ04 }) {
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
        require(ammo.getItemDamage() == 0, "forged out of range slot rejected");
        player.inventory.currentItem = 1;
        WeaponNetwork.enqueueControls(player, 0, true, false, true);
        controller.serverTick();
        require(ammo.getItemDamage() == 0, "forged held slot rejected");
        player.inventory.currentItem = 0;
        channel.close();
        WeaponNetwork.enqueueControls(player, 0, true, false, true);
        controller.serverTick();
        require(ammo.getItemDamage() == 0, "disconnected queued input rejected");
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
