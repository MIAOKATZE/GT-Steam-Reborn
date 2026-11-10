package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.CombatTracker;
import net.minecraft.util.DamageSource;
import net.minecraft.util.FoodStats;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.miaokatze.gtsr.client.encounter.RoyalSkillVisual;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Isolated contracts against actual patched MC damage, attributes and potion lifecycle. Unsafe bypasses only
 * world/player
 * construction; the attack adapter omits unrelated death/knockback/network work. No client or complete game is started.
 */
public final class BossCombatContracts {

    private static int checks;
    private static Object allocator;
    private static Method allocate;

    private BossCombatContracts() {}

    public static void main(String[] args) throws Exception {
        // Reuse the existing test bootstrap rather than a second copy of FML/MC initialization.
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, EventBootstrap.class.getName(), args)) return;
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafe.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        allocator = singleton.get(null);
        allocate = unsafe.getMethod("allocateInstance", Class.class);
        for (Class<?> type : Arrays.asList(EntityPlayer.class, Potion.class, AttributeModifier.class))
            System.out.println(
                "BOSS ORACLE " + type.getName()
                    + "="
                    + type.getProtectionDomain()
                        .getCodeSource());
        LifeDisintegrationPotion potion = LifeDisintegrationPotion.register(31);
        LifeDisintegrationPotion.registerEvents();
        check(Potion.potionTypes[potion.id] == potion && potion.isBadEffect(), "registered real harmful Potion");
        timing();
        scopeAndDamage(potion);
        lifecycle(potion);
        rejectionAndAbsorption(potion);
        System.out.println("BOSS CONTRACT PASS checks=" + checks + " scope=isolated-patched-MC-no-game");
    }

    private static void timing() {
        equal(14, EntitySilentKing.GRAVITY_WINDUP_TICKS, "gravity windup");
        equal(14, EntitySilentKing.METEOR_WARNING_TICKS, "meteor visual warning");
        equal(28, EntitySilentKing.METEOR_TRACKING_TICKS, "meteor tracking");
        equal(84, EntitySilentKing.METEOR_IMPACT_TICKS, "meteor impact");
        equal(32, RoyalSkillVisual.meteorHeight(14), "visual descent starts after warning");
        equal(24, RoyalSkillVisual.meteorHeight(49), "visual midpoint follows shortened parabola");
        equal(0, RoyalSkillVisual.meteorHeight(84), "visual reaches ground at server impact");
        equal(0, RoyalSkillVisual.meteorHeight(120), "late visual remains grounded");
    }

    private static void scopeAndDamage(LifeDisintegrationPotion potion) throws Exception {
        EntitySilentKing king = (EntitySilentKing) allocate(EntitySilentKing.class);
        check(BossCombatEffects.isBoss(king), "silent king included");
        check(!BossCombatEffects.isBoss(null), "null excluded");
        for (EchoKind kind : EchoKind.values()) {
            EchoAttacker echo = (EchoAttacker) allocate(EchoAttacker.class);
            echo.kind = kind;
            boolean expected = kind == EchoKind.DC02 || kind == EchoKind.DC08;
            check(BossCombatEffects.isBoss(echo) == expected, "exact boss roster " + kind);
            FixturePlayer target = player();
            check(BossCombatEffects.hit(echo, target, DamageSource.generic, 2), "physical accepted " + kind);
            equal(expected ? 94 : 98, target.getHealth(), "physical multiplier " + kind);
            check(target.isPotionActive(potion) == expected, "potion restricted to three bosses " + kind);
        }
        FixturePlayer target = player();
        check(BossCombatEffects.hit(king, target, DamageSource.magic, 3), "magic accepted");
        equal(94, target.getHealth(), "magic multiplier two, not physical three");
        equal(94, target.getMaxHealth(), "snapshot is HP after actual damage");
        equal(
            60,
            target.getActivePotionEffect(potion)
                .getDuration(),
            "real Potion has 60 ticks");
        check(target.lastSource == DamageSource.magic, "damage source identity retained");
    }

    private static void lifecycle(LifeDisintegrationPotion potion) throws Exception {
        FixturePlayer target = player();
        IAttributeInstance health = target.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        AttributeModifier flat = modifier(1, 20, 0), base = modifier(2, .5, 1), total = modifier(3, .25, 2);
        health.applyModifier(flat);
        health.applyModifier(base);
        health.applyModifier(total);
        equal(225, target.getMaxHealth(), "native operation zero/one/two modifiers");
        target.setHealth(190);
        check(hit(target, DamageSource.generic, 10), "first boss hit");
        equal(160, target.getHealth(), "HP snapshot after tripled physical damage");
        equal(160, target.getMaxHealth(), "cap includes existing multiplicative max-health modifiers");
        equal(100, health.getBaseValue(), "base max-health remains intact");
        check(
            health.getModifier(flat.getID()) == flat && health.getModifier(base.getID()) == base
                && health.getModifier(total.getID()) == total,
            "other modifiers preserved by identity");
        target.heal(100);
        equal(160, target.getHealth(), "healing cannot pass locked HP");
        tick(target, 35);
        equal(
            25,
            target.getActivePotionEffect(potion)
                .getDuration(),
            "native Potion ticks elapsed");
        check(hit(target, DamageSource.magic, 5), "repeat hit accepted");
        equal(150, target.getHealth(), "repeat snapshots new current HP");
        equal(150, target.getMaxHealth(), "repeat replaces old cap");
        equal(
            60,
            target.getActivePotionEffect(potion)
                .getDuration(),
            "repeat resets complete 60 ticks");
        check(
            health.func_111122_c()
                .size() == 4,
            "refresh has exactly one extra max-health modifier");
        AttributeModifier later = modifier(4, 40, 0);
        health.applyModifier(later);
        tick(target, 1);
        equal(150, target.getMaxHealth(), "later max-health modifier cannot raise the cap");
        health.removeModifier(total);
        tick(target, 1);
        equal(150, target.getMaxHealth(), "removed max-health modifier retains snapshot");
        tick(target, 57);
        check(target.isPotionActive(potion), "cap remains through tick 59");
        equal(
            1,
            target.getActivePotionEffect(potion)
                .getDuration(),
            "one tick before expiry");
        tick(target, 1);
        check(!target.isPotionActive(potion), "native Potion expires exactly on tick 60");
        equal(240, target.getMaxHealth(), "expiry restores current external modifiers");
        equal(150, target.getHealth(), "expiry grants no free healing");
        check(
            health.func_111122_c()
                .size() == 3,
            "expiry removes only cap modifier");
        check(hit(target, DamageSource.magic, 5), "can apply again after expiry");
        target.removePotionEffect(potion.id);
        equal(240, target.getMaxHealth(), "explicit removal restores maximum immediately");
        equal(140, target.getHealth(), "explicit removal preserves damaged HP");
        check(
            health.func_111122_c()
                .size() == 3,
            "explicit removal leaves no stale cap");
    }

    private static void rejectionAndAbsorption(LifeDisintegrationPotion potion) throws Exception {
        FixturePlayer denied = player();
        denied.reject = true;
        check(!hit(denied, DamageSource.generic, 3), "original rejection return preserved");
        check(!denied.isPotionActive(potion), "rejected hit does not apply potion");
        FixturePlayer absorbed = player();
        absorbed.setAbsorptionAmount(20);
        check(hit(absorbed, DamageSource.generic, 3), "fully absorbed native damage still accepted");
        equal(100, absorbed.getHealth(), "absorption preserves real HP");
        equal(11, absorbed.getAbsorptionAmount(), "native absorption charged tripled damage");
        check(!absorbed.isPotionActive(potion), "accepted without HP decrease does not apply potion");
        absorbed.setAbsorptionAmount(2);
        check(hit(absorbed, DamageSource.magic, 3), "partial absorption accepted");
        equal(96, absorbed.getHealth(), "partial absorption snapshot uses final HP");
        equal(96, absorbed.getMaxHealth(), "partial absorption cap uses final HP");
        tick(absorbed, 10);
        absorbed.setAbsorptionAmount(20);
        hit(absorbed, DamageSource.magic, 1);
        equal(
            50,
            absorbed.getActivePotionEffect(potion)
                .getDuration(),
            "no-HP repeat does not reset duration");
        HurtCancellation cancellation = new HurtCancellation();
        MinecraftForge.EVENT_BUS.register(cancellation);
        try {
            FixturePlayer canceled = player();
            cancellation.target = canceled;
            check(hit(canceled, DamageSource.generic, 3), "native LivingHurt cancellation may retain accepted=true");
            equal(100, canceled.getHealth(), "real Forge cancellation prevents HP damage");
            check(!canceled.isPotionActive(potion), "canceled actual damage does not apply potion");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancellation);
        }
        FixturePlayer dead = player();
        check(hit(dead, DamageSource.generic, 50), "lethal hit accepted");
        equal(0, dead.getHealth(), "native health clamps lethal damage to zero");
        check(!dead.isPotionActive(potion), "lethal hit does not attach stale potion");
        FixturePlayer fractional = player();
        fractional.setHealth(3.5F);
        check(hit(fractional, DamageSource.generic, 1), "sub-one surviving hit accepted");
        equal(.5, fractional.getHealth(), "fractional HP snapshot");
        equal(.5, fractional.getMaxHealth(), "GTNH native max-health permits a fractional cap");
        fractional.heal(10);
        equal(.5, fractional.getHealth(), "real Forge healing event enforces fractional cap");
        tick(fractional, 60);
        fractional.heal(10);
        equal(10.5, fractional.getHealth(), "fractional healing restriction ends with real Potion expiry");
        // A bounded native RangedAttribute fixture also covers packs retaining vanilla's minimum of one.
        Field minimum = RangedAttribute.class.getDeclaredField("minimumValue");
        minimum.setAccessible(true);
        double originalMinimum = minimum.getDouble(SharedMonsterAttributes.maxHealth);
        try {
            minimum.setDouble(SharedMonsterAttributes.maxHealth, 1D);
            FixturePlayer bounded = player();
            bounded.setHealth(3.5F);
            hit(bounded, DamageSource.generic, 1);
            equal(1, bounded.getMaxHealth(), "minimum-one native attribute clamp fixture");
            bounded.heal(10);
            equal(.5, bounded.getHealth(), "real healing event enforces cap below attribute lower bound");
        } finally {
            minimum.setDouble(SharedMonsterAttributes.maxHealth, originalMinimum);
        }
    }

    private static boolean hit(FixturePlayer target, DamageSource source, float amount) throws Exception {
        return BossCombatEffects.hit((EntitySilentKing) allocate(EntitySilentKing.class), target, source, amount);
    }

    private static void tick(FixturePlayer target, int count) {
        for (int i = 0; i < count; i++) target.potionTick();
    }

    private static AttributeModifier modifier(int id, double amount, int operation) {
        return new AttributeModifier(new UUID(0, id), "boss contract " + id, amount, operation);
    }

    private static FixturePlayer player() throws Exception {
        WorldServer world = (WorldServer) allocate(WorldServer.class);
        set(World.class, world, "worldAccesses", new ArrayList<>());
        set(World.class, world, "worldInfo", new WorldInfo(new NBTTagCompound()));
        FixturePlayer player = (FixturePlayer) allocate(FixturePlayer.class);
        player.worldObj = world;
        player.initialize();
        return player;
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object allocate(Class<?> type) throws Exception {
        return allocate.invoke(allocator, type);
    }

    public static final class EventBootstrap {

        public static void main(String[] args) throws Exception {
            // Normal Forge transforms add no-arg event constructors and per-event listener lists before registration.
            Launch.classLoader.registerTransformer("cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer");
            BossCombatContracts.main(args);
        }
    }

    private static void equal(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < .0001, message + " expected=" + expected + " actual=" + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public static final class HurtCancellation {

        private EntityPlayer target;

        @SubscribeEvent
        public void cancel(LivingHurtEvent event) {
            if (event.entityLiving == target) event.setCanceled(true);
        }
    }

    private static final class EchoAttacker extends EntityOldEcho {

        private EchoKind kind;

        private EchoAttacker() {
            super(null);
        }

        @Override
        public EchoKind getKind() {
            return kind;
        }
    }

    private static final class FixturePlayer extends EntityPlayer {

        private boolean reject;
        private DamageSource lastSource;

        private FixturePlayer() {
            super(null, null);
        }

        private void initialize() throws Exception {
            rand = new Random(0);
            entityUniqueID = UUID.randomUUID();
            dataWatcher = new DataWatcher(this);
            dataWatcher.addObject(0, (byte) 0);
            dataWatcher.addObject(1, (short) 300);
            entityInit();
            set(EntityLivingBase.class, this, "activePotionsMap", new HashMap<>());
            set(EntityLivingBase.class, this, "_combatTracker", new CombatTracker(this));
            inventory = new InventoryPlayer(this);
            capabilities = new PlayerCapabilities();
            foodStats = new FoodStats();
            applyEntityAttributes();
            getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(100);
            setHealth(100);
        }

        @Override
        public boolean attackEntityFrom(DamageSource source, float amount) {
            lastSource = source;
            if (reject || ForgeHooks.onLivingAttack(this, source, amount)) return false;
            super.damageEntity(source, amount);
            return true;
        }

        @Override
        public boolean isOnLadder() {
            return false;
        }

        private void potionTick() {
            ++ticksExisted;
            super.updatePotionEffects();
        }

        @Override
        public void addChatMessage(IChatComponent message) {}

        @Override
        public boolean canCommandSenderUseCommand(int level, String name) {
            return false;
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return new ChunkCoordinates(0, 0, 0);
        }

        @Override
        public IChatComponent func_145748_c_() {
            return new ChatComponentText("boss contract player");
        }
    }
}
