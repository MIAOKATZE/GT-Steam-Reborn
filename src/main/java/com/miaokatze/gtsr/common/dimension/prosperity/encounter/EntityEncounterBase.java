package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.entity.EntityCreature;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public abstract class EntityEncounterBase extends EntityCreature {

    protected double anchorX, anchorY, anchorZ;

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
    }
}
