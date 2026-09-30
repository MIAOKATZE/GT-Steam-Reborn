package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public class EntityResidualOathguard extends EntityEncounterBase {

    private int ordinal;
    private int swingCooldown;

    public EntityResidualOathguard(World w) {
        super(w);
        setSize(1.2F, 2.5F);
        setCustomNameTag("（旧日虚影）残誓兵");
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(40);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.18);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
    }

    protected boolean isAIEnabled() {
        return true;
    }

    protected void onDeathUpdate() {
        if (++deathTime >= 48) setDead();
    }

    public void setOrdinal(int n) {
        ordinal = n;
    }

    public boolean attackEntityFrom(DamageSource s, float a) {
        if (s.getEntity() instanceof EntityPlayer) {
            setAttackTarget((EntityPlayer) s.getEntity());
            if (getEncounterState() != 1) state(1);
        }
        return super.attackEntityFrom(s, a);
    }

    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote || !isEntityAlive()) return;
        EntityLivingBase t = getAttackTarget();
        if (t == null || !t.isEntityAlive() || t.worldObj != worldObj || getDistanceSqToEntity(t) > 1600) {
            setAttackTarget(null);
            state(0);
            getNavigator().clearPathEntity();
            setPosition(anchorX, anchorY, anchorZ);
            motionX = motionY = motionZ = 0;
        } else {
            if (getDistanceSqToEntity(t) < 8 && --swingCooldown <= 0) {
                t.attackEntityFrom(DamageSource.causeMobDamage(this), 5);
                swingCooldown = 25;
                phase(0);
                worldObj.setEntityState(this, (byte) 4);
            } else phase(getVisualPhaseTicks() + 1);
            if (Math.abs(posY - anchorY) > 4 || getDistanceSq(anchorX, anchorY, anchorZ) > 225) {
                setPosition(anchorX, anchorY, anchorZ);
                getNavigator().clearPathEntity();
            } else if (ticksExisted % 10 == 0) getNavigator().tryMoveToEntityLiving(t, .8);
        }
    }

    public void onDeath(DamageSource s) {
        super.onDeath(s);
        if (dead && !worldObj.isRemote && !getEncounterId().isEmpty()) ForgottenLakeEncounterData.get(worldObj)
            .guardDied(getEncounterId(), getPlatformId() * 3 + ordinal);
    }

    public void writeEntityToNBT(NBTTagCompound n) {
        super.writeEntityToNBT(n);
        n.setInteger("ordinal", ordinal);
    }

    public void readEntityFromNBT(NBTTagCompound n) {
        super.readEntityFromNBT(n);
        ordinal = n.getInteger("ordinal");
    }
}
