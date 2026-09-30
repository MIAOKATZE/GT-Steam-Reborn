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
    }
}
