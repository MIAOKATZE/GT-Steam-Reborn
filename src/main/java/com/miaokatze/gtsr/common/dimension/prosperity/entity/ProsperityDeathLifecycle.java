package com.miaokatze.gtsr.common.dimension.prosperity.entity;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.nbt.NBTTagCompound;

/** Finite corpse animation with a persisted, once-only experience reward. */
public final class ProsperityDeathLifecycle {

    public static final int WATCHER = 31;
    public static final int DURATION = 100;
    public static final int HOLD = 12;
    private boolean captured;
    private boolean rewarded;
    private int pendingExperience;

    public void capture(int experience) {
        if (!captured) {
            captured = true;
            pendingExperience = Math.max(0, experience);
        }
    }

    public int ticks(EntityLiving entity) {
        return Math.max(
            entity.deathTime,
            entity.getDataWatcher()
                .getWatchableObjectInt(WATCHER));
    }

    public float alpha(EntityLiving entity, float partial) {
        if (entity.getHealth() > 0) return 1;
        return Math.max(0, Math.min(1, 1 - (ticks(entity) + partial - HOLD) / (DURATION - HOLD)));
    }

    public void tick(EntityLiving entity) {
        entity.deathTime = Math.min(DURATION, entity.deathTime + 1);
        entity.motionX = entity.motionY = entity.motionZ = 0;
        entity.getNavigator()
            .clearPathEntity();
        if (!entity.worldObj.isRemote) {
            entity.getDataWatcher()
                .updateObject(WATCHER, entity.deathTime);
        }
        if (entity.deathTime < DURATION) return;
        if (!entity.worldObj.isRemote && !rewarded) {
            rewarded = true;
            if (entity.worldObj.getGameRules()
                .getGameRuleBooleanValue("doMobLoot")) {
                int remaining = pendingExperience;
                pendingExperience = 0;
                while (remaining > 0) {
                    int amount = EntityXPOrb.getXPSplit(remaining);
                    remaining -= amount;
                    entity.worldObj.spawnEntityInWorld(
                        new EntityXPOrb(entity.worldObj, entity.posX, entity.posY, entity.posZ, amount));
                }
            }
        }
        entity.setDead();
    }

    public void write(EntityLiving entity, NBTTagCompound tag) {
        tag.setInteger("gtsrCorpseTicks", entity.deathTime);
        tag.setInteger("gtsrCorpseXp", pendingExperience);
        tag.setBoolean("gtsrCorpseCaptured", captured);
        tag.setBoolean("gtsrCorpseRewarded", rewarded);
    }

    public void read(EntityLiving entity, NBTTagCompound tag) {
        entity.deathTime = Math.max(0, Math.min(DURATION, tag.getInteger("gtsrCorpseTicks")));
        pendingExperience = Math.max(0, tag.getInteger("gtsrCorpseXp"));
        captured = tag.getBoolean("gtsrCorpseCaptured");
        rewarded = tag.getBoolean("gtsrCorpseRewarded");
        entity.getDataWatcher()
            .updateObject(WATCHER, entity.deathTime);
    }
}
