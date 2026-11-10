package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.BaseAttributeMap;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** A removable, synchronized potion whose cap follows the most recent damaging boss hit. */
public final class LifeDisintegrationPotion extends Potion {

    public static final int DURATION_TICKS = 60;
    public static final UUID MODIFIER_ID = UUID.fromString("86c9eae5-f2ec-4b16-92ba-1728831d77ea");
    private static final String CAP_KEY = "gtsrLifeDisintegrationCap";
    private static LifeDisintegrationPotion instance;
    private static boolean listening;

    private LifeDisintegrationPotion(int id) {
        super(id, true, 0x8D243B);
        setPotionName("potion.gtsr.lifeDisintegration");
        setIconIndex(2, 2);
        setEffectiveness(1D);
    }

    /** Stable, configurable IDs must agree on both sides; never overwrite or silently choose another slot. */
    public static LifeDisintegrationPotion register(int id) {
        if (instance != null) {
            if (instance.id != id) throw new IllegalStateException("Life disintegration potion already registered");
            return instance;
        }
        if (id < 0 || id > 255 || id >= Potion.potionTypes.length) {
            throw new IllegalStateException(
                "Invalid gtsr potion.lifeDisintegrationId=" + id
                    + "; select an available ID in 0..255 supported by the potion table, identical on server and client");
        }
        if (Potion.potionTypes[id] != null) {
            throw new IllegalStateException(
                "GTSR life disintegration potion ID " + id
                    + " is occupied by "
                    + Potion.potionTypes[id].getName()
                    + "; change potion.lifeDisintegrationId in config/gtsr/gtsr.cfg on server and client");
        }
        instance = new LifeDisintegrationPotion(id);
        return instance;
    }

    public static LifeDisintegrationPotion instance() {
        return instance;
    }

    public static void registerEvents() {
        if (listening) return;
        listening = true;
        Events events = new Events();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
    }

    /** Called after damage has completed. Replacing the old effect always resets all sixty ticks. */
    public void lock(EntityPlayer player) {
        if (player.worldObj.isRemote || !player.isEntityAlive()) return;
        float health = player.getHealth();
        if (!(health > 0) || !Float.isFinite(health)) return;
        player.removePotionEffect(id);
        player.getEntityData()
            .setFloat(CAP_KEY, health);
        player.addPotionEffect(new PotionEffect(id, DURATION_TICKS, 0));
        // A rejected potion must not leave persistent state or an attribute modifier behind.
        if (!player.isPotionActive(id)) clear(player);
    }

    @Override
    public void applyAttributesModifiersToEntity(EntityLivingBase entity, BaseAttributeMap attributes, int amplifier) {
        if (!(entity instanceof EntityPlayer)) return;
        if (!entity.getEntityData()
            .hasKey(CAP_KEY))
            entity.getEntityData()
                .setFloat(CAP_KEY, entity.getHealth());
        synchronize(entity);
    }

    @Override
    public void removeAttributesModifiersFromEntity(EntityLivingBase entity, BaseAttributeMap attributes,
        int amplifier) {
        clear(entity);
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Override
    public void performEffect(EntityLivingBase entity, int amplifier) {
        if (!entity.worldObj.isRemote && entity instanceof EntityPlayer) synchronize(entity);
    }

    /** Recompute against every existing modifier, before the attribute's final range clamp. */
    public void synchronize(EntityLivingBase entity) {
        if (entity.worldObj.isRemote) return;
        NBTTagCompound data = entity.getEntityData();
        if (!data.hasKey(CAP_KEY)) data.setFloat(CAP_KEY, entity.getHealth());
        float cap = data.getFloat(CAP_KEY);
        if (!(cap > 0) || !Float.isFinite(cap)) {
            entity.removePotionEffect(id);
            clear(entity);
            return;
        }
        IAttributeInstance health = entity.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        double raw = health.getBaseValue();
        for (Object value : health.func_111122_c()) {
            AttributeModifier modifier = (AttributeModifier) value;
            if (!MODIFIER_ID.equals(modifier.getID()) && modifier.getOperation() == 0) raw += modifier.getAmount();
        }
        double withBaseMultipliers = raw;
        for (Object value : health.func_111122_c()) {
            AttributeModifier modifier = (AttributeModifier) value;
            if (!MODIFIER_ID.equals(modifier.getID()) && modifier.getOperation() == 1)
                withBaseMultipliers += raw * modifier.getAmount();
        }
        raw = withBaseMultipliers;
        for (Object value : health.func_111122_c()) {
            AttributeModifier modifier = (AttributeModifier) value;
            if (!MODIFIER_ID.equals(modifier.getID()) && modifier.getOperation() == 2) raw *= 1D + modifier.getAmount();
        }
        // A smaller external maximum is never raised to the stored cap.
        double amount = raw > cap && Double.isFinite(raw) ? cap / raw - 1D : 0D;
        AttributeModifier previous = health.getModifier(MODIFIER_ID);
        if (previous == null || Double.compare(previous.getAmount(), amount) != 0) {
            if (previous != null) health.removeModifier(previous);
            health.applyModifier(new AttributeModifier(MODIFIER_ID, "GTSR life disintegration", amount, 2));
        }
        // Some packs clamp maxHealth to at least 1; this must never heal a surviving half-HP player.
        float limit = Math.min(cap, entity.getMaxHealth());
        if (entity.getHealth() > limit) entity.setHealth(limit);
    }

    private static void clear(EntityLivingBase entity) {
        IAttributeInstance health = entity.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        AttributeModifier modifier = health.getModifier(MODIFIER_ID);
        if (modifier != null) health.removeModifier(modifier);
        entity.getEntityData()
            .removeTag(CAP_KEY);
    }

    public static final class Events {

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void heal(LivingHealEvent event) {
            LifeDisintegrationPotion potion = instance;
            EntityLivingBase entity = event.entityLiving;
            if (potion == null || entity.worldObj.isRemote
                || !(entity instanceof EntityPlayer)
                || !entity.isPotionActive(potion.id)) return;
            potion.synchronize(entity);
            float available = entity.getEntityData()
                .getFloat(CAP_KEY) - entity.getHealth();
            if (!(available > 0)) event.setCanceled(true);
            else event.amount = Math.min(event.amount, available);
        }

        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.player.worldObj.isRemote || instance == null) return;
            if (event.player.isPotionActive(instance.id)) instance.synchronize(event.player);
            else if (event.player.getEntityData()
                .hasKey(CAP_KEY)
                || event.player.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                    .getModifier(MODIFIER_ID) != null)
                clear(event.player);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void death(LivingDeathEvent event) {
            if (instance != null && !event.entityLiving.worldObj.isRemote && event.entityLiving instanceof EntityPlayer)
                event.entityLiving.removePotionEffect(instance.id);
        }

        @SubscribeEvent
        public void clone(PlayerEvent.Clone event) {
            if (event.wasDeath && !event.entityPlayer.worldObj.isRemote && instance != null) {
                event.entityPlayer.removePotionEffect(instance.id);
                clear(event.entityPlayer);
            }
        }
    }
}
