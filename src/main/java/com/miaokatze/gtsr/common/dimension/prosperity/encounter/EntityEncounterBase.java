package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.entity.EntityCreature;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public abstract class EntityEncounterBase extends EntityCreature
    implements com.miaokatze.gtsr.common.dimension.prosperity.entity.ProsperityDeathVisual {

    protected double anchorX, anchorY, anchorZ;
    private int deathExperience;
    private boolean deathExperienceReleased;

    public EntityEncounterBase(World w) {
        super(w);
        func_110163_bv();
    }

    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(20, 0);
        dataWatcher.addObject(21, "");
        dataWatcher.addObject(22, 0);
        dataWatcher.addObject(23, -1);
        dataWatcher.addObject(24, 90F);
        dataWatcher.addObject(31, 0);
        dataWatcher.addObject(19, 0);
    }

    /** Watched corpse age, also available to renderers when local deathTime lags. */
    public int getDeathAnimationTicks() {
        return Math.max(deathTime, dataWatcher.getWatchableObjectInt(31));
    }

    public int getDeathAnimationDuration() {
        return 100;
    }

    public int getDeathAnimationHoldTicks() {
        return 12;
    }

    public boolean isGoldenDeath() {
        return false;
    }

    public float getDeathAlpha(float partial) {
        if (getHealth() > 0) return 1;
        float age = getDeathAnimationTicks() + partial - getDeathAnimationHoldTicks();
        return Math
            .max(0, Math.min(1, 1 - age / Math.max(1, getDeathAnimationDuration() - getDeathAnimationHoldTicks())));
    }

    @Override
    public void onDeath(net.minecraft.util.DamageSource source) {
        int pending = !worldObj.isRemote && recentlyHit > 0 ? getExperiencePoints(attackingPlayer) : 0;
        super.onDeath(source);
        if (!worldObj.isRemote && dead && !deathExperienceReleased) deathExperience = pending;
    }

    @Override
    protected void onDeathUpdate() {
        deathTime++;
        syncDeathAnimation();
        finishDeathAnimation();
    }

    protected void finishDeathAnimation() {
        if (deathTime < getDeathAnimationDuration()) return;
        if (!worldObj.isRemote && !deathExperienceReleased) {
            deathExperienceReleased = true;
            if (worldObj.getGameRules()
                .getGameRuleBooleanValue("doMobLoot")) {
                int xp = deathExperience;
                deathExperience = 0;
                while (xp > 0) {
                    int split = net.minecraft.entity.item.EntityXPOrb.getXPSplit(xp);
                    xp -= split;
                    worldObj.spawnEntityInWorld(
                        new net.minecraft.entity.item.EntityXPOrb(worldObj, posX, posY, posZ, split));
                }
            }
        }
        setDead();
    }

    protected void syncDeathAnimation() {
        if (!worldObj.isRemote) dataWatcher.updateObject(31, deathTime);
        motionX = motionY = motionZ = 0;
        getNavigator().clearPathEntity();
    }

    public int getSpawnerTier() {
        return dataWatcher.getWatchableObjectInt(19);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!worldObj.isRemote) {
            dataWatcher.updateObject(19, Math.max(0, Math.min(3, getEntityData().getInteger("gtsr.spawnerTier"))));
            if (getHealth() <= 0) syncDeathAnimation();
        }
    }

    public int getEncounterState() {
        return dataWatcher.getWatchableObjectInt(20);
    }

    public String getEncounterId() {
        return dataWatcher.getWatchableObjectString(21);
    }

    public int getVisualPhaseTicks() {
        return dataWatcher.getWatchableObjectInt(22);
    }

    public int getPlatformId() {
        return dataWatcher.getWatchableObjectInt(23);
    }

    public float getHomeYaw() {
        return dataWatcher.getWatchableObjectFloat(24);
    }

    public void setHomeYaw(float yaw) {
        dataWatcher.updateObject(24, yaw);
        faceHome();
    }

    protected void faceHome() {
        rotationYaw = rotationYawHead = renderYawOffset = getHomeYaw();
    }

    protected float legacyHomeYaw() {
        if (getPlatformId() < 0) return 90F;
        try {
            String[] parts = getEncounterId().split(":");
            double dx = Integer.parseInt(parts[1]) + .5 - anchorX;
            double dz = Integer.parseInt(parts[2]) + .5 - anchorZ;
            return (float) (Math.atan2(-dx, dz) * 180 / Math.PI);
        } catch (RuntimeException ignored) {
            return 90F;
        }
    }

    protected void state(int s) {
        dataWatcher.updateObject(20, s);
        dataWatcher.updateObject(22, 0);
    }

    protected void phase(int t) {
        dataWatcher.updateObject(22, t);
    }

    public void initialize(String id, int platform, double x, double y, double z) {
        dataWatcher.updateObject(21, id);
        dataWatcher.updateObject(23, platform);
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        setPosition(x, y, z);
    }

    protected boolean canDespawn() {
        return false;
    }

    public void writeEntityToNBT(NBTTagCompound n) {
        super.writeEntityToNBT(n);
        n.setString("encounter", getEncounterId());
        n.setInteger("platform", getPlatformId());
        n.setInteger("state", getEncounterState());
        n.setInteger("phase", getVisualPhaseTicks());
        n.setDouble("ax", anchorX);
        n.setDouble("ay", anchorY);
        n.setDouble("az", anchorZ);
        n.setFloat("homeYaw", getHomeYaw());
        n.setInteger("corpseTicks", deathTime);
        n.setInteger("encounterDeathXP", deathExperience);
        n.setBoolean("encounterDeathXPReleased", deathExperienceReleased);
    }

    public void readEntityFromNBT(NBTTagCompound n) {
        super.readEntityFromNBT(n);
        initialize(
            n.getString("encounter"),
            n.getInteger("platform"),
            n.getDouble("ax"),
            n.getDouble("ay"),
            n.getDouble("az"));
        state(n.getInteger("state"));
        phase(n.getInteger("phase"));
        setHomeYaw(n.hasKey("homeYaw") ? n.getFloat("homeYaw") : legacyHomeYaw());
        deathTime = n.getInteger("corpseTicks");
        deathExperience = Math.max(0, Math.min(1000, n.getInteger("encounterDeathXP")));
        deathExperienceReleased = n.getBoolean("encounterDeathXPReleased");
    }
}
