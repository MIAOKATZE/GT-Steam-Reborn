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
import net.minecraft.entity.item.EntityItem;
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
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
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
import com.miaokatze.gtsr.common.weapons.WeaponDamageSource;
import com.miaokatze.gtsr.common.weapons.WeaponEnchantments;
import com.miaokatze.gtsr.common.weapons.WeaponKind;
import com.miaokatze.gtsr.common.weapons.WeaponMode;
import com.miaokatze.gtsr.common.weapons.WeaponNetwork;
import com.miaokatze.gtsr.common.weapons.WeaponPose;
import com.miaokatze.gtsr.common.weapons.WeaponShotEnchantments;
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
    private Effect capturedShot, capturedImpact, capturedGesture;
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
            singularityEnchantments();
            nativeFrozenLooting();
            destructionDrops();
            for (WeaponKind kind : new WeaponKind[] { WeaponKind.LM12, WeaponKind.T20, WeaponKind.QLZ04 })
                inventory(kind);
            movingAim();
            nearAim();
            cooling();
            muzzleAndWall();
            damage();
            collision(WeaponKind.T20);
            collision(WeaponKind.QLZ04);
            modeBehavior();
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
        require(capturedState.remoteTicks == 40, "107 remote action continues for forty ticks after trigger");
        int nodes = 0;
        for (Object object : world.loadedEntityList)
            if (object instanceof EntityWeaponSingularity && !((Entity) object).isDead) nodes++;
        for (int i = 0; i < 39; i++) control(false, false, false);
        require(capturedState.remoteTicks == 1, "107 remote action still active at tick 49");
        control(false, false, false);
        require(capturedState.remoteTicks == 0, "107 remote action finishes at tick 50");
        int after = 0;
        for (Object object : world.loadedEntityList)
            if (object instanceof EntityWeaponSingularity && !((Entity) object).isDead) after++;
        require(after == nodes && nodes == 1, "107 one remote action creates exactly one node");
        // 12 held ticks + one release + 50 action ticks elapsed since the shot.
        for (int i = 0; i < 36; i++) control(false, false, false);
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

    private static final class LootingCow extends EntityCow {

        int observedLooting = -1;

        LootingCow(WorldServer world) {
            super(world);
        }

        @Override
        protected void dropFewItems(boolean playerKill, int looting) {
            observedLooting = looting;
            super.dropFewItems(playerKill, looting);
        }
    }

    private LootingCow lootingCow() {
        LootingCow cow = new LootingCow(world);
        cow.setPosition(x + 2, 240, z);
        cow.setHealth(1);
        world.spawnEntityInWorld(cow);
        owned.add(cow);
        return cow;
    }

    private void nativeFrozenLooting() {
        List<Entity> beforeDrops = new ArrayList<Entity>(world.loadedEntityList);
        ItemStack previous = player.inventory.getCurrentItem();
        boolean inserted = !world.loadedEntityList.contains(player);
        if (inserted) world.loadedEntityList.add(player);
        try {
            for (WeaponKind kind : WeaponKind.values()) {
                ItemStack gun = new ItemStack(PortableWeapons.weapons[kind.id]);
                require(
                    Enchantment.looting.canApply(gun) && Enchantment.looting.canApplyAtEnchantingTable(gun),
                    "native woven looting applicability and table " + kind);
                require(
                    gun.getItem()
                        .isBookEnchantable(gun, new ItemStack(Items.enchanted_book)),
                    "native weapon book boundary " + kind);
                gun.addEnchantment(Enchantment.looting, 3);
                EntityWeaponProjectile shot = new EntityWeaponProjectile(world, player, kind, 100, 0, 0);
                shot.freezeEnchantments(gun);
                player.inventory.mainInventory[player.inventory.currentItem] = null;
                LootingCow target = lootingCow();
                EntityWeaponProjectile.applyDamage(target, 100, 0, 0, shot, player);
                require(
                    target.observedLooting == 3,
                    "native projectile death uses launch looting after weapon switch " + kind);
            }
            for (int deathPhase = 0; deathPhase < 3; deathPhase++) {
                LootingCow target = lootingCow();
                // DOT, physical burst, then magic burst after physical survives at 1 health.
                target.setHealth(deathPhase == 1 ? 3 : 1);
                EntityWeaponSingularity core = new EntityWeaponSingularity(
                    world,
                    target.posX,
                    target.posY + target.height * .5,
                    target.posZ,
                    player.getUniqueID(),
                    false,
                    new WeaponShotEnchantments(0, 0, 0, 0, 0, 3));
                NBTTagCompound saved = new NBTTagCompound();
                core.writeToNBT(saved);
                saved.setInteger("age", deathPhase == 0 ? 19 : 159);
                core.readFromNBT(saved);
                // Final tick also deals DOT; offset its two damage for the magic-only death fixture.
                if (deathPhase == 2) target.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                    .setBaseValue(100);
                if (deathPhase == 2) target.setHealth(23);
                core.onUpdate();
                require(
                    target.observedLooting == 3,
                    "native singularity DOT/physical/magic death frozen looting phase " + deathPhase);
                owned.add(core);
                for (Entity entity : new ArrayList<Entity>(owned))
                    if (entity instanceof LootingCow) world.removeEntity(entity);
            }
            ItemStack sword = new ItemStack(Items.diamond_sword);
            sword.addEnchantment(Enchantment.looting, 1);
            player.inventory.mainInventory[player.inventory.currentItem] = sword;
            LootingCow nativeTarget = lootingCow();
            nativeTarget.attackEntityFrom(DamageSource.causePlayerDamage(player), 100);
            require(nativeTarget.observedLooting == 1, "ordinary native damage still reads currently held looting");
            LootingCow frozenZero = lootingCow();
            frozenZero.attackEntityFrom(new WeaponDamageSource("gtsr.portable", player, player, 0), 100);
            require(frozenZero.observedLooting == 0, "unenchanted launch ignores later held looting sword");
            LootingCow ownerlessProjectile = lootingCow();
            ownerlessProjectile.attackEntityFrom(
                new WeaponDamageSource("gtsr.portable", new EntityWeaponProjectile(world), null, 3),
                100);
            require(
                ownerlessProjectile.observedLooting == 3,
                "ownerless projectile native death retains frozen looting");
            LootingCow ownerlessNodeTarget = lootingCow();
            EntityWeaponSingularity ownerlessNode = new EntityWeaponSingularity(
                world,
                ownerlessNodeTarget.posX,
                ownerlessNodeTarget.posY + ownerlessNodeTarget.height * .5,
                ownerlessNodeTarget.posZ,
                UUID.randomUUID(),
                false,
                new WeaponShotEnchantments(0, 0, 0, 0, 0, 3));
            NBTTagCompound ownerlessSaved = new NBTTagCompound();
            ownerlessNode.writeToNBT(ownerlessSaved);
            ownerlessSaved.setInteger("age", 19);
            ownerlessNode.readFromNBT(ownerlessSaved);
            ownerlessNode.onUpdate();
            owned.add(ownerlessNode);
            require(
                ownerlessNodeTarget.observedLooting == 3,
                "unloaded owner singularity DOT native death retains frozen looting");
            LootingCow ordinaryOwnerless = lootingCow();
            ordinaryOwnerless.attackEntityFrom(DamageSource.magic, 100);
            require(ordinaryOwnerless.observedLooting == 0, "ordinary ownerless native death keeps zero looting");
            require(!Enchantment.looting.canApply(new ItemStack(Items.stick)), "looting ordinary item rule unchanged");
        } finally {
            player.inventory.mainInventory[player.inventory.currentItem] = previous;
            if (inserted) world.loadedEntityList.remove(player);
            for (Object object : world.loadedEntityList)
                if (object instanceof EntityItem && !beforeDrops.contains(object)) owned.add((Entity) object);
        }
    }

    private void destructionDrops() {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = 250;
        List<Entity> before = new ArrayList<Entity>(world.loadedEntityList);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            require(world.isAirBlock(bx + dx, by, bz + dz), "destruction fixture uses only air");
        try {
            for (int level : new int[] { 1, 5 }) {
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) world.setBlock(bx + dx, by, bz + dz, Blocks.stone);
                EntityWeaponSingularity core = new EntityWeaponSingularity(
                    world,
                    bx + .5,
                    by + .5,
                    bz + .5,
                    player.getUniqueID(),
                    false,
                    new WeaponShotEnchantments(level, 0, 0, 0, 0, 0));
                core.onUpdate();
                int removed = 0;
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    if (world.isAirBlock(bx + dx, by, bz + dz)) removed++;
                    world.setBlockToAir(bx + dx, by, bz + dz);
                }
                require(removed == 5 * level, "native destruction actual per-tick removal budget level " + level);
                core.setDead();
            }
            world.setBlock(bx, by, bz, Blocks.obsidian);
            world.setBlock(bx + 1, by, bz, Blocks.bedrock);
            world.setBlock(bx - 1, by, bz, Blocks.chest);
            ((TileEntityChest) world.getTileEntity(bx - 1, by, bz))
                .setInventorySlotContents(0, new ItemStack(Items.diamond, 7));
            EntityWeaponSingularity core = new EntityWeaponSingularity(
                world,
                bx + .5,
                by + .5,
                bz + .5,
                player.getUniqueID(),
                false,
                new WeaponShotEnchantments(1, 0, 0, 0, 0, 0));
            core.onUpdate();
            require(
                world.isAirBlock(bx, by, bz) && world.isAirBlock(bx - 1, by, bz)
                    && world.getBlock(bx + 1, by, bz) == Blocks.bedrock,
                "native absorption includes obsidian and excludes bedrock");
            int stones = 0, obsidian = 0, diamonds = 0, chests = 0;
            for (Object object : world.loadedEntityList) if (object instanceof EntityItem && !before.contains(object)) {
                EntityItem drop = (EntityItem) object;
                ItemStack stack = drop.getEntityItem();
                if (stack.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.cobblestone))
                    stones += stack.stackSize;
                if (stack.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.obsidian))
                    obsidian += stack.stackSize;
                if (stack.getItem() == Items.diamond) diamonds += stack.stackSize;
                if (stack.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.chest))
                    chests += stack.stackSize;
            }
            require(
                stones == 30 && obsidian == 1 && diamonds == 7 && chests == 1,
                "native 100-percent block drops and full container contents");
        } finally {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) world.setBlockToAir(bx + dx, by, bz + dz);
            for (Object object : new ArrayList<Object>(world.loadedEntityList))
                if (object instanceof EntityItem && !before.contains(object)) {
                    ((Entity) object).setDead();
                    world.removeEntity((Entity) object);
                }
        }
    }

    private void singularityEnchantments() {
        WeaponEnchantments[] additions = { WeaponEnchantments.destruction, WeaponEnchantments.diffusion,
            WeaponEnchantments.duration, WeaponEnchantments.tearing, WeaponEnchantments.quenching };
        for (WeaponEnchantments enchantment : additions) {
            require(
                Enchantment.enchantmentsList[enchantment.effectId] == enchantment && enchantment.getMaxLevel() == 5,
                "107 dedicated enchantment registered at cap V");
            for (WeaponKind kind : WeaponKind.values()) require(
                enchantment.canApply(new ItemStack(PortableWeapons.weapons[kind.id]))
                    == (kind == WeaponKind.SINGULARITY),
                "107 dedicated enchantment weapon matrix");
        }
        require(EntityWeaponSingularity.absorbableHardness(50), "destruction hardness 50 included");
        require(!EntityWeaponSingularity.absorbableHardness(50.01f), "destruction hardness over 50 excluded");
        require(!EntityWeaponSingularity.absorbableHardness(-1), "destruction negative hardness excluded");
        for (int level : new int[] { 1, 5 }) for (boolean critical : new boolean[] { false, true }) {
            ItemStack gun = new ItemStack(PortableWeapons.weapons[WeaponKind.SINGULARITY.id]);
            for (WeaponEnchantments enchantment : additions) gun.addEnchantment(enchantment, level);
            gun.addEnchantment(Enchantment.looting, 3);
            EntityWeaponProjectile projectile = new EntityWeaponProjectile(
                world,
                player,
                WeaponKind.SINGULARITY,
                0,
                0,
                0);
            projectile.freezeEnchantments(gun);
            // Mutating the launch stack after capture must not change either persisted entity.
            gun.setTagCompound(null);
            require(
                projectile.enchantments().duration == level && projectile.enchantments().looting == 3,
                "shot enchantments frozen before weapon replacement");
            NBTTagCompound saved = new NBTTagCompound();
            projectile.writeToNBT(saved);
            EntityWeaponProjectile restoredProjectile = new EntityWeaponProjectile(world);
            restoredProjectile.readFromNBT(saved);
            require(
                restoredProjectile.enchantments().quenching == level && restoredProjectile.enchantments().looting == 3,
                "projectile frozen enchantments NBT");
            ByteBuf bytes = Unpooled.buffer();
            projectile.writeSpawnData(bytes);
            restoredProjectile.readSpawnData(bytes);
            bytes.release();
            require(restoredProjectile.enchantments().destruction == level, "projectile frozen enchantments spawn");
            EntityCow center = cow(0, 0);
            center.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                .setBaseValue(1000);
            center.setHealth(1000);
            EntityWeaponSingularity node = new EntityWeaponSingularity(
                world,
                center.posX,
                center.posY + center.height * .5,
                center.posZ,
                player.getUniqueID(),
                critical,
                projectile.enchantments());
            owned.add(node);
            require(
                node.duration() == 160 + 30 * level && node.blockBudget() == 5 * level,
                "107 duration and actual block budget level " + level);
            close((float) node.absorptionRadius(), (critical ? 10 : 5) + level, "107 diffusion radius");
            for (int i = 0; i < node.duration(); i++) node.onUpdate();
            close(
                center.getHealth(),
                1000 - (node.duration() / 20) * (critical ? 5 : 2) * (1 + .2f * level)
                    - ((critical ? 50 : 20) + (critical ? 15 : 5)) * (1 + .2f * level),
                "107 level I/V native DOT plus scaled physical and magic burst");
            require(node.isDead, "enchanted core dynamic lifetime ends exactly");
            node.writeToNBT(saved);
            EntityWeaponSingularity restored = new EntityWeaponSingularity(world);
            restored.readFromNBT(saved);
            require(
                restored.duration() == node.duration() && restored.enchantments().looting == 3,
                "node frozen enchants NBT");
            bytes = Unpooled.buffer();
            node.writeSpawnData(bytes);
            restored.readSpawnData(bytes);
            bytes.release();
            require(
                restored.duration() == node.duration() && restored.enchantments().tearing == level,
                "node frozen enchants spawn");
            center.setDead();
            world.removeEntity(center);
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
        ItemStack pack = k == WeaponKind.SINGULARITY ? GTSRItemList.SteamEntangledSingularity.get(4)
            : new ItemStack(PortableWeapons.ammo[k.id]);
        player.inventory.mainInventory[1] = pack;
        return pack;
    }

    private int tick(boolean fire, boolean reload) {
        // Bounded fixture clock advances production cadence/heat logic, then finally restores the save clock.
        world.getWorldInfo()
            .incrementTotalWorldTime(world.getTotalWorldTime() + 1);
        WeaponNetwork.Controls sent = new WeaponNetwork.Controls();
        sent.slot = 0;
        sent.firing = fire;
        sent.reload = reload;
        sent.yaw = player.rotationYaw;
        sent.pitch = player.rotationPitch;
        ByteBuf controlsBytes = Unpooled.buffer();
        try {
            sent.toBytes(controlsBytes);
            WeaponNetwork.Controls received = new WeaponNetwork.Controls();
            received.fromBytes(controlsBytes);
            require(
                controlsBytes.readableBytes() == 0 && received.yaw == sent.yaw && received.pitch == sent.pitch,
                "actual Controls aim roundtrip");
            // Deliberately stale server aim: only the decoded production input restores the shot direction.
            player.rotationYaw = sent.yaw + 90;
            player.rotationPitch = 0;
            WeaponNetwork.enqueueControls(player, received);
        } finally {
            controlsBytes.release();
        }
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
                    if (held == WeaponKind.LM12 || held == WeaponKind.T20) {
                        double yaw = Math.toRadians(player.rotationYaw), pitch = Math.toRadians(player.rotationPitch);
                        Vec3 eye = WeaponPose.eye(player, 1);
                        Vec3 target = eye.addVector(
                            -Math.sin(yaw) * Math.cos(pitch) * 100,
                            -Math.sin(pitch) * 100,
                            Math.cos(yaw) * Math.cos(pitch) * 100);
                        // The fixture world can retain legitimate targets from other production scenarios.
                        net.minecraft.util.MovingObjectPosition block = world.func_147447_a(
                            Vec3.createVectorHelper(eye.xCoord, eye.yCoord, eye.zCoord),
                            Vec3.createVectorHelper(target.xCoord, target.yCoord, target.zCoord),
                            false,
                            true,
                            false);
                        Vec3 end = target;
                        if (block != null) target = block.hitVec;
                        double closest = eye.squareDistanceTo(target);
                        for (Object candidate : world.loadedEntityList) {
                            if (!(candidate instanceof net.minecraft.entity.EntityLivingBase)) continue;
                            Entity living = (Entity) candidate;
                            if (living == player || living.isDead || !living.canBeCollidedWith()) continue;
                            net.minecraft.util.MovingObjectPosition intercept = living.boundingBox.expand(.1, .1, .1)
                                .calculateIntercept(eye, end);
                            if (intercept != null && eye.squareDistanceTo(intercept.hitVec) < closest) {
                                target = intercept.hitVec;
                                closest = eye.squareDistanceTo(target);
                            }
                        }
                        Vec3 actual = Vec3.createVectorHelper(entity.motionX, entity.motionY, entity.motionZ)
                            .normalize();
                        Vec3 direction = Vec3
                            .createVectorHelper(
                                target.xCoord - expected.xCoord,
                                target.yCoord - expected.yCoord,
                                target.zCoord - expected.zCoord)
                            .normalize();
                        require(
                            actual.dotProduct(direction)
                                / Math.sqrt(actual.dotProduct(actual) * direction.dotProduct(direction)) > 1 - 1.0E-10,
                            "decoded moving aim converges from physical muzzle to eye ray; target=" + target
                                + " muzzle="
                                + expected
                                + " actual="
                                + actual
                                + " expected="
                                + direction);
                    }
                    owned.add(entity);
                    entity.setDead();
                    world.removeEntity(entity);
                }
            }
        return shots;
    }

    private void movingAim() {
        double px = player.posX, py = player.posY, pz = player.posZ;
        for (WeaponKind kind : new WeaponKind[] { WeaponKind.LM12, WeaponKind.T20 }) {
            setup(kind);
            tick(false, true);
            for (int i = 0; i < kind.reloadTicks; i++) tick(false, false);
            int shots = 0;
            for (int i = 0; i < 96; i++) {
                player.setPosition(px + Math.sin(i * .15) * 2, py + Math.cos(i * .13), pz + i * .02);
                player.rotationYaw = -170 + (i * 19 % 340);
                player.rotationPitch = -70 + (i * 11 % 140);
                shots += tick(true, false);
                require(
                    Math.abs(player.rotationYaw - (-170 + (i * 19 % 340))) < 1.0E-5,
                    "wire aim replaces stale server yaw during continuous fire");
                require(
                    Math.abs(player.rotationPitch - (-70 + (i * 11 % 140))) < 1.0E-5,
                    "wire aim replaces stale server pitch during continuous fire");
            }
            require(shots >= 8, "moving continuous fire produces multiple new directions " + kind);
            tick(false, false);
        }
        player.setPosition(px, py, pz);
        player.rotationYaw = player.rotationPitch = 0;
    }

    private void nearAim() {
        player.rotationYaw = player.rotationPitch = 0;
        Vec3 eye = WeaponPose.eye(player, 1);
        int bx = (int) Math.floor(eye.xCoord), by = (int) Math.floor(eye.yCoord), bz = (int) Math.floor(eye.zCoord) + 8;
        require(world.getBlock(bx, by, bz) == Blocks.air, "near aim fixture empty air");
        try {
            require(world.setBlock(bx, by, bz, Blocks.stone, 0, 2), "near aim target installed");
            for (WeaponKind kind : new WeaponKind[] { WeaponKind.LM12, WeaponKind.T20 }) {
                EntityWeaponProjectile projectile = new EntityWeaponProjectile(
                    world,
                    player,
                    kind,
                    kind.damage,
                    kind.armorPenetration,
                    0);
                owned.add(projectile);
                double travel = (bz - projectile.posZ) / projectile.motionZ;
                require(
                    Math.abs(projectile.posX + projectile.motionX * travel - eye.xCoord) < 1.0E-8
                        && Math.abs(projectile.posY + projectile.motionY * travel - eye.yCoord) < 1.0E-8,
                    "near block crosshair intersects actual waist-muzzle ray " + kind);
            }
        } finally {
            world.setBlock(bx, by, bz, Blocks.air, 0, 2);
        }
        EntityCow target = new EntityCow(world);
        target.setPosition(eye.xCoord, eye.yCoord - .7, eye.zCoord + 6);
        owned.add(target);
        require(world.spawnEntityInWorld(target), "near entity target installed");
        try {
            double front = target.boundingBox.minZ - .1;
            for (WeaponKind kind : new WeaponKind[] { WeaponKind.LM12, WeaponKind.T20 }) {
                EntityWeaponProjectile projectile = new EntityWeaponProjectile(
                    world,
                    player,
                    kind,
                    kind.damage,
                    kind.armorPenetration,
                    0);
                owned.add(projectile);
                double travel = (front - projectile.posZ) / projectile.motionZ;
                require(
                    Math.abs(projectile.posX + projectile.motionX * travel - eye.xCoord) < 1.0E-8
                        && Math.abs(projectile.posY + projectile.motionY * travel - eye.yCoord) < 1.0E-8,
                    "near entity crosshair intersects actual waist-muzzle ray " + kind);
            }
        } finally {
            target.setDead();
            world.removeEntity(target);
        }
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
                        if (effect.type == 1) capturedImpact = effect;
                        if (effect.type == 3) capturedGesture = effect;
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
                    && result.shotSerial == state.shotSerial
                    && result.remoteAmmoType == state.remoteAmmoType
                    && result.chargeTicks == state.chargeTicks
                    && result.chargeDuration == state.chargeDuration,
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
                    && out.shotSerial == effect.shotSerial
                    && out.ammoType == effect.ammoType,
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

    private int modeStep(boolean fire, boolean reload, boolean switchMode) {
        int serial = player.getEntityData()
            .getInteger("gtsr.weaponShotSerial");
        world.getWorldInfo()
            .incrementTotalWorldTime(world.getTotalWorldTime() + 1);
        capturedState = null;
        capturedShot = null;
        WeaponNetwork.enqueueControls(player, 0, fire, false, reload, false, switchMode);
        controller.serverTick();
        require(capturedState != null, "mode fixture actual server Snapshot");
        return player.getEntityData()
            .getInteger("gtsr.weaponShotSerial") - serial;
    }

    private void clearModeEntities() {
        for (Entity entity : owned) {
            entity.setDead();
            world.removeEntity(entity);
        }
        for (Object object : new ArrayList<Object>(world.loadedEntityList)) {
            if (object instanceof EntityWeaponProjectile || object instanceof EntityWeaponSingularity) {
                Entity entity = (Entity) object;
                owned.add(entity);
                entity.setDead();
                world.removeEntity(entity);
            }
        }
    }

    private EntityWeaponProjectile liveModeProjectile() {
        for (Object object : world.loadedEntityList) {
            if (object instanceof EntityWeaponProjectile && !((Entity) object).isDead) {
                EntityWeaponProjectile shot = (EntityWeaponProjectile) object;
                owned.add(shot);
                return shot;
            }
        }
        throw new IllegalStateException("mode fixture has no live projectile");
    }

    private EntityWeaponProjectile modeTrajectory(WeaponKind kind, int mode, float damage, double px, double py,
        double pz, double vx, double vy, double vz) {
        EntityWeaponProjectile shot = new EntityWeaponProjectile(world, player, kind, damage, 0, 0);
        shot.setMode(mode);
        shot.freezeEnchantments(player.getHeldItem());
        shot.setPosition(px, py, pz);
        shot.motionX = vx;
        shot.motionY = vy;
        shot.motionZ = vz;
        owned.add(shot);
        require(world.spawnEntityInWorld(shot), "mode fixture real projectile spawn");
        return shot;
    }

    private void modeBehavior() {
        int beforeChecks = checks;
        clearModeEntities();
        ItemStack belt = setup(WeaponKind.LM12);
        reload(WeaponKind.LM12);
        NBTTagCompound gunData = WeaponController.data(player.getHeldItem());
        modeStep(false, false, true);
        require(capturedState.mode == 1, "LM C selects suppression on actual server");
        gunData.setBoolean("hot", true);
        gunData.setFloat("heat", 1);
        for (int i = 0; i < 9; i++) require(modeStep(true, false, false) == 0, "suppression waits below spin20");
        require(modeStep(true, false, false) == 1, "suppression shoots at spin20 despite hot NBT");
        require(
            capturedState.chargeTicks == 20 && capturedState.chargeDuration == 80,
            "LM suppression pre-spin supplies cyan charge progress against full-spin80");
        for (int i = 0; i < 85; i++) modeStep(true, false, false);
        require(
            capturedState.shotInterval == 2 && !capturedState.overheated && belt.getItemDamage() > 1,
            "suppression reaches interval2 and keeps firing while visually heated");
        close(capturedState.spin, 1, "LM suppression visual spin saturates at full-speed80");
        modeStep(false, false, true);
        gunData.setBoolean("hot", true);
        gunData.setFloat("heat", 1);
        require(modeStep(true, false, false) == 0 && capturedState.overheated, "standard LM hot stops firing");
        clearModeEntities();
        ItemStack pack = setup(WeaponKind.QLZ04);
        reload(WeaponKind.QLZ04);
        gunData = WeaponController.data(player.getHeldItem());
        for (int mode : new int[] { 1, 2, 0 }) {
            int paid = gunData.getInteger("paidMagazine");
            int prior = pack.getItemDamage();
            modeStep(false, false, true);
            require(
                capturedState.mode == mode && capturedState.magazine == 0 && capturedState.reloadTicks == 39,
                "QLZ C clears magazine and begins reload mode=" + mode);
            require(pack.getItemDamage() == prior - paid, "QLZ mode switch refunds only paid rounds mode=" + mode);
            for (int i = 0; i < 39; i++) modeStep(false, false, false);
            int capacity = mode == 2 ? 8 : 12;
            require(
                capturedState.magazine == capacity && pack.getItemDamage() == capacity,
                "QLZ actual reload capacity mode=" + mode);
            int first = -1, second = -1;
            gunData.setBoolean("hot", true);
            for (int i = 0; i < 20; i++) {
                if (modeStep(true, false, false) > 0) {
                    if (first < 0) first = i;
                    else if (second < 0) second = i;
                }
            }
            require(
                second - first == (mode == 2 ? 4 : 12) && !capturedState.overheated,
                "QLZ actual mode cadence and heat immunity mode=" + mode);
            clearModeEntities();
            // Restore a fully paid magazine so each next iteration has an exact independent refund expectation.
            gunData.setInteger("magazine", capacity);
            gunData.setInteger("paidMagazine", capacity);
            pack.setItemDamage(capacity);
            modeStep(false, false, false);
        }
        pack.setItemDamage(0);
        modeStep(false, false, true);
        require(pack.getItemDamage() == 0, "QLZ full ordinary pack discards refund overflow");
        for (int i = 0; i < 39; i++) modeStep(false, false, false);
        gunData.setInteger("paidMagazine", 0);
        int before = pack.getItemDamage();
        modeStep(false, false, true);
        require(
            pack.getItemDamage() == before && capturedState.magazine == 0,
            "QLZ unpaid economy magazine cannot generate refunded resources");
        clearModeEntities();
        modeProjectileBehavior();
        unstableBehavior();
        clearModeEntities();
        report(
            null,
            "MODES checks=" + (checks - beforeChecks)
                + " controls-serverTick heat cadence paid-refund segment-proximity fragments charge instant-blast");
    }

    private void modeProjectileBehavior() {
        setup(WeaponKind.T20);
        // A diagonal miss lies inside an expanded cube but outside the exact 1.5-radius rounded target bounds.
        EntityCow corner = cow(0, 3);
        EntityWeaponProjectile miss = modeTrajectory(
            WeaponKind.T20,
            1,
            8,
            x - 3,
            corner.boundingBox.maxY + 1.2,
            corner.boundingBox.maxZ + 1.2,
            6,
            0,
            0);
        miss.onUpdate();
        require(!miss.isDead && corner.getHealth() == 100, "T20 diagonal proximity outside sphere does not trigger");
        clearModeEntities();
        EntityCow center = cow(0, 3), splash = cow(2, 3);
        EntityWeaponProjectile airburst = modeTrajectory(WeaponKind.T20, 1, 8, x, 230.6, z - 2, 0, 0, 10);
        airburst.onUpdate();
        require(airburst.isDead, "T20 fast segment crossing entire proximity sphere detonates");
        close(center.getHealth(), 92, "T20 airburst physical damage8");
        close(splash.getHealth(), 92, "T20 airburst radius2.5 actual off-axis splash");
        clearModeEntities();
        center = cow(0, 3);
        EntityCow near = cow(.8, 3), far = cow(1.8, 3);
        EntityWeaponProjectile standard = modeTrajectory(WeaponKind.T20, 0, 10, x, 230.6, z, 0, 0, 5);
        standard.onUpdate();
        close(center.getHealth(), 90, "T20 standard physical damage10");
        close(near.getHealth(), 90, "T20 standard radius1 actual splash");
        close(far.getHealth(), 100, "T20 standard radius1 excludes distant entity");
        clearModeEntities();
        setup(WeaponKind.QLZ04);
        player.getHeldItem()
            .addEnchantment(Enchantment.looting, 3);
        EntityCow ahead = cow(0, 3.8);
        EntityWeaponProjectile grenade = modeTrajectory(WeaponKind.QLZ04, 1, 12, x, 230.6, z, 0, 0, .2);
        grenade.onUpdate();
        require(grenade.isDead, "QLZ shotgun detects entity in next forward4 before direct collision");
        int fragments = 0;
        for (Object object : new ArrayList<Object>(world.loadedEntityList)) {
            if (!(object instanceof EntityWeaponProjectile) || ((Entity) object).isDead) continue;
            EntityWeaponProjectile fragment = (EntityWeaponProjectile) object;
            require(fragment.fragment(), "QLZ split children carry fragment marker");
            require(fragment.enchantments().looting == 3, "QLZ fragments retain frozen looting");
            Vec3 direction = fragment.visualDirection();
            require(
                direction.zCoord >= Math.cos(Math.PI / 6) - 1e-9,
                "QLZ all fragments inside full60degree forward cone");
            NBTTagCompound saved = new NBTTagCompound();
            fragment.writeToNBT(saved);
            close(saved.getFloat("damage"), 2, "QLZ each physical fragment damage2");
            EntityWeaponProjectile restored = new EntityWeaponProjectile(world);
            restored.readFromNBT(saved);
            require(
                restored.fragment() && restored.enchantments().looting == 3,
                "QLZ fragment save cannot split again and preserves looting");
            if (fragments == 0) {
                // Short-lived physical fragments use the same ten-tick window as the burst visual.
                restored.setPosition(x, 240, z);
                restored.motionX = 0;
                restored.motionY = 0;
                restored.motionZ = 3;
                owned.add(restored);
                require(world.spawnEntityInWorld(restored), "spawn fragment lifetime fixture");
                for (int age = 0; age < 10; age++) world.updateEntityWithOptionalForce(restored, true);
                require(
                    !restored.isDead && Math.abs(restored.posZ - z - 30) < 1e-9,
                    "QLZ physical fragment flies ten ticks with maximum thirty-block reach");
                require(restored.ticksExisted == 10, "fragment age advances through real World entity tick path");
                world.updateEntityWithOptionalForce(restored, true);
                require(
                    restored.isDead && restored.ticksExisted == 11 && Math.abs(restored.posZ - z - 30) < 1e-9,
                    "QLZ physical fragment expires before eleventh movement tick");
                world.removeEntity(restored);
            }
            owned.add(fragment);
            fragments++;
        }
        require(
            fragments == 30 && ahead.getHealth() == 100,
            "QLZ emits exactly30 projectiles without parent AoE damage");
        clearModeEntities();
        int bx = (int) Math.floor(x), by = 230, bz = (int) Math.floor(z) + 2;
        Block prior = world.getBlock(bx, by, bz);
        int metadata = world.getBlockMetadata(bx, by, bz);
        require(prior == Blocks.air, "mode wall fixture starts in air");
        try {
            require(world.setBlock(bx, by, bz, Blocks.stone, 0, 2), "mode wall fixture install");
            cow(0, 3.8);
            EntityWeaponProjectile blocked = modeTrajectory(WeaponKind.QLZ04, 1, 12, x, 230.6, z, 0, 0, .2);
            blocked.onUpdate();
            require(!blocked.isDead, "QLZ lookahead cannot airburst through nearer wall");
            clearModeEntities();
            EntityCow behind = cow(0, 3.8);
            EntityWeaponProjectile shell = modeTrajectory(WeaponKind.T20, 1, 8, x, 230.6, z - 1, 0, 0, 10);
            shell.onUpdate();
            require(
                shell.isDead && shell.posZ < bz,
                "T20 nearest wall/proximity impact is resolved within flown segment");
            require(
                world.getBlock(bx, by, bz) == Blocks.stone && behind.getHealth() <= 100,
                "mode projectile leaves terrain intact");
        } finally {
            world.setBlock(bx, by, bz, prior, metadata, 2);
            clearModeEntities();
        }
    }

    private void unstableTrajectory() {
        setup(WeaponKind.SINGULARITY);
        float pitch = player.rotationPitch;
        player.rotationPitch = -45;
        EntityWeaponProjectile normal = new EntityWeaponProjectile(world, player, WeaponKind.SINGULARITY, 0, 0, 0);
        EntityWeaponProjectile unstable = new EntityWeaponProjectile(world, player, WeaponKind.SINGULARITY, 0, 0, 0);
        player.rotationPitch = pitch;
        Vec3 origin = unstable.visualOrigin(), direction = unstable.visualDirection();
        unstable.setMode(WeaponMode.ALTERNATE);
        require(
            unstable.visualOrigin()
                .squareDistanceTo(origin) < 1e-12
                && unstable.visualDirection()
                    .squareDistanceTo(direction) < 1e-12,
            "107 setting unstable mode preserves frozen launch origin and direction");
        double speed = Math.sqrt(
            unstable.motionX * unstable.motionX + unstable.motionY * unstable.motionY
                + unstable.motionZ * unstable.motionZ);
        require(Math.abs(speed - .9) < 1e-6, "107 unstable actual constructor launch speed doubles to .9");
        require(
            Math.abs(WeaponMode.gravity(WeaponKind.SINGULARITY, 1) - .0063) < 1e-12,
            "107 unstable gravity doubles to preserve equal-angle flight duration");
        // An isolated horizontal landing plane at the launch height removes eye-height range offsets.
        normal.setPosition(x, 240, z);
        unstable.setPosition(x, 240, z);
        owned.add(normal);
        owned.add(unstable);
        require(
            world.spawnEntityInWorld(normal) && world.spawnEntityInWorld(unstable),
            "spawn paired actual ballistic entities");
        double initialVertical = unstable.motionY;
        world.updateEntityWithOptionalForce(normal, true);
        world.updateEntityWithOptionalForce(unstable, true);
        require(
            Math.abs(initialVertical - unstable.motionY - .0063) < 1e-12,
            "107 actual unstable update applies mode gravity");
        NBTTagCompound saved = new NBTTagCompound();
        unstable.writeToNBT(saved);
        EntityWeaponProjectile restored = new EntityWeaponProjectile(world);
        restored.readFromNBT(saved);
        require(
            restored.mode() == 1 && restored.ticksExisted == 1
                && Math.abs(restored.motionY - unstable.motionY) < 1e-12
                && restored.visualOrigin()
                    .squareDistanceTo(origin) < 1e-12,
            "107 NBT restores frozen unstable mode trajectory without resetting evolved motion");
        ByteBuf bytes = Unpooled.buffer();
        try {
            unstable.writeSpawnData(bytes);
            restored.readSpawnData(bytes);
            require(
                restored.mode() == 1 && restored.ticksExisted == 1
                    && bytes.readableBytes() == 0
                    && restored.visualDirection()
                        .squareDistanceTo(direction) < 1e-12,
                "107 spawn decoder preserves frozen unstable mode age and direction");
        } finally {
            bytes.release();
        }
        int flight = 1;
        while (normal.posY >= 240 && flight < 350) {
            world.updateEntityWithOptionalForce(normal, true);
            world.updateEntityWithOptionalForce(unstable, true);
            flight++;
            require(!normal.isDead && !unstable.isDead, "107 paired ballistic test stays collision-free");
        }
        double normalRange = Math.hypot(normal.posX - x, normal.posZ - z);
        double unstableRange = Math.hypot(unstable.posX - x, unstable.posZ - z);
        require(
            flight < 350 && unstable.posY < 240 && normal.ticksExisted == unstable.ticksExisted,
            "107 both modes return to horizontal launch plane on same tick");
        require(
            Math.abs(unstableRange / normalRange - 2) < 1e-6,
            "107 actual unstable horizontal landing range is twice standard");
        clearModeEntities();
    }

    private void unstableBehavior() {
        unstableTrajectory();
        for (boolean critical : new boolean[] { false, true }) {
            setup(WeaponKind.SINGULARITY);
            ItemStack gun = player.getHeldItem();
            gun.addEnchantment(WeaponEnchantments.quenching, 2);
            gun.addEnchantment(Enchantment.looting, 3);
            player.inventory.mainInventory[1] = (critical ? GTSRItemList.CriticalSteamEntangledSingularity
                : GTSRItemList.SteamEntangledSingularity).get(4);
            modeStep(false, false, true);
            modeStep(false, true, false);
            for (int i = 0; i < 99; i++) modeStep(false, false, false);
            require(
                capturedState.magazine == 1 && capturedState.mode == 1,
                "107 unstable loaded via production reload");
            for (int i = 0; i < 99; i++) require(modeStep(true, false, false) == 0, "107 partial hold cannot shoot");
            require(
                modeStep(false, false, false) == 0 && capturedState.magazine == 1,
                "107 early release cancels without consuming loaded ammunition");
            for (int i = 0; i < 110; i++)
                require(modeStep(true, false, false) == 0, "107 full hold never automatically fires");
            require(capturedState.chargeTicks == 100, "107 server charge saturates at100ticks");
            int cancelledSerial = player.getEntityData()
                .getInteger("gtsr.weaponShotSerial");
            WeaponNetwork.Controls cancel = new WeaponNetwork.Controls();
            cancel.slot = 0;
            cancel.yaw = player.rotationYaw;
            cancel.pitch = player.rotationPitch;
            cancel.cancelCharge = true;
            ByteBuf cancelBytes = Unpooled.buffer();
            try {
                cancel.toBytes(cancelBytes);
                WeaponNetwork.Controls decoded = new WeaponNetwork.Controls();
                decoded.fromBytes(cancelBytes);
                require(
                    decoded.cancelCharge && cancelBytes.readableBytes() == 0,
                    "107 GUI cancel flag survives actual Controls wire decoder");
                WeaponNetwork.enqueueControls(player, decoded);
                controller.serverTick();
            } finally {
                cancelBytes.release();
            }
            require(
                capturedState.chargeTicks == 0 && capturedState.magazine == 1
                    && player.getEntityData()
                        .getInteger("gtsr.weaponShotSerial") == cancelledSerial,
                "107 inventory GUI full-charge lost focus cancels instead of treating it as release-to-fire");
            for (int i = 0; i < 50; i++) modeStep(true, false, false);
            WeaponNetwork.enqueueControls(player, 0, true, false, false, false, false, true);
            controller.serverTick();
            for (int i = 0; i < 5; i++) require(
                modeStep(true, false, false) == 0 && capturedState.chargeTicks == 0,
                "107 explicit cancel while trigger held blocks recharge until actual release");
            modeStep(false, false, false);
            for (int i = 0; i < 100; i++) require(
                modeStep(true, false, false) == 0,
                "107 charge can restart after GUI cancel and actual trigger release");
            require(
                modeStep(false, false, false) == 1 && capturedState.reloadTicks == 100,
                "107 full release creates one projectile and auto reloads");
            EntityWeaponProjectile shot = liveModeProjectile();
            require(
                shot.mode() == 1 && shot.critical() == critical && shot.enchantments().looting == 3,
                "107 shot freezes mode critical and looting");
            EntityCow inner = cow(critical ? 11 : 5, 0), outside = cow(critical ? 13 : 7, 0);
            inner.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                .setBaseValue(300);
            inner.setHealth(300);
            shot.setPosition(x, 230.6, z);
            WeaponController.data(gun)
                .setInteger("ammoType", critical ? 0 : 1);
            capturedGesture = null;
            require(
                modeStep(true, false, false) == 0 && !shot.isDead && capturedState.remoteTicks == 49,
                "107 unstable click begins same fifty-tick gesture and waits before blast");
            require(capturedState.remoteAmmoType == (critical ? 1 : 0), "107 remote color freezes launched ammo");
            require(
                capturedState.ammoType != capturedState.remoteAmmoType && capturedGesture != null
                    && capturedGesture.ammoType == capturedState.remoteAmmoType,
                "107 selected ammo change cannot recolor old projectile remote gesture");
            packetRoundtrip(capturedState, capturedGesture);
            for (int i = 0; i < 8; i++) modeStep(true, false, false);
            require(!shot.isDead && capturedState.remoteTicks == 41, "107 unstable remote waits nine ticks");
            close(inner.getHealth(), 300, "107 unstable remote applies no early blast damage");
            modeStep(true, false, false);
            require(shot.isDead && capturedState.remoteTicks == 40, "107 unstable remote blasts on tenth tick");
            require(
                capturedState.remoteAmmoType == (critical ? 1 : 0),
                "107 post-blast gesture retains frozen ammo color");
            WeaponController.data(gun)
                .setInteger("ammoType", critical ? 1 : 0);
            close(
                inner.getHealth(),
                300 - (critical ? 130 : 50) * 1.4f,
                "107 actual physical+magic blast doubled then quenching scales");
            close(outside.getHealth(), 100, "107 doubled final blast radius excludes outside target");
            for (Object object : world.loadedEntityList) require(
                !(object instanceof EntityWeaponSingularity) || ((Entity) object).isDead,
                "107 unstable final explosion spawns no persistent pull node");
            clearModeEntities();
            EntityCow collisionTarget = cow(0, 3);
            collisionTarget.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                .setBaseValue(300);
            collisionTarget.setHealth(300);
            EntityWeaponProjectile collisionShot = modeTrajectory(WeaponKind.SINGULARITY, 1, 0, x, 230.6, z, 0, 0, 5);
            collisionShot.setCritical(critical);
            collisionShot.onUpdate();
            require(collisionShot.isDead, "107 unstable entity impact instantly consumes projectile");
            close(
                collisionTarget.getHealth(),
                300 - (critical ? 130 : 50) * 1.4f,
                "107 unstable entity impact immediately applies final blast without eight-second pull");
            for (Object object : world.loadedEntityList) require(
                !(object instanceof EntityWeaponSingularity) || ((Entity) object).isDead,
                "107 unstable collision also leaves no sustained node");
            clearModeEntities();
            for (int i = 0; i < 99; i++) modeStep(false, false, false);
            for (int i = 0; i < 50; i++) modeStep(true, false, false);
            require(capturedState.chargeTicks == 50, "107 second charge progresses independently");
            player.inventory.currentItem = 2;
            controller.serverTick();
            player.inventory.currentItem = 0;
            require(
                modeStep(false, false, false) == 0 && capturedState.chargeTicks == 0 && capturedState.magazine == 1,
                "107 equipment change cancels charge without spending round");
            for (int i = 0; i < 30; i++) modeStep(true, false, false);
            modeStep(true, false, true);
            require(
                capturedState.mode == 0 && capturedState.chargeTicks == 0,
                "107 C cancels unfinished charge on server");
            clearModeEntities();
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
