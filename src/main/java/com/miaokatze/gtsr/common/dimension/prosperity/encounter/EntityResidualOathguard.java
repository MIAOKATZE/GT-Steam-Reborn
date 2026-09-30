package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry;

public class EntityResidualOathguard extends EntityEncounterBase {

    private int ordinal;
    private int swingCooldown, returnTicks, pathFailures;
    private boolean deathRecorded;

    public EntityResidualOathguard(World w) {
        super(w);
        setSize(1.2F, 2.5F);
        setCustomNameTag("(旧日虚影)残誓兵");
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(200);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.18);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
    }

    protected boolean isAIEnabled() {
        return true;
    }

    protected void onDeathUpdate() {
        if (++deathTime >= 200) setDead();
    }

    public void setOrdinal(int n) {
        ordinal = n;
        registerAnchor();
    }

    private void registerAnchor() {
        if (worldObj.isRemote || getEncounterId().isEmpty()) return;
        ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(worldObj);
        d.registerGuardAnchor(
            getEncounterId(),
            d.guardIndex(getEncounterId(), getPlatformId(), ordinal),
            anchorX,
            anchorY,
            anchorZ);
    }

    private boolean validPlayer(EntityPlayer p) {
        return p != null && p.isEntityAlive() && p.worldObj == worldObj && !p.capabilities.isCreativeMode;
    }

    private void anger(EntityPlayer p) {
        setAttackTarget(p);
        if (getEncounterState() != 1) state(1);
        returnTicks = pathFailures = 0;
    }

    public boolean attackEntityFrom(DamageSource s, float a) {
        boolean accepted = super.attackEntityFrom(s, a);
        if (accepted && !worldObj.isRemote && s.getEntity() instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) s.getEntity();
            if (validPlayer(p)) {
                if (isEntityAlive()) anger(p);
                for (Object o : worldObj
                    .getEntitiesWithinAABB(EntityResidualOathguard.class, boundingBox.expand(5, 5, 5))) {
                    EntityResidualOathguard g = (EntityResidualOathguard) o;
                    if (g.isEntityAlive() && g.getEncounterId()
                        .equals(getEncounterId()) && getDistanceSqToEntity(g) <= 25) g.anger(p);
                }
            }
        }
        return accepted;
    }

    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote || !isEntityAlive()) return;
        if (ticksExisted == 1) registerAnchor();
        EntityLivingBase t = getAttackTarget();
        if (!(t instanceof EntityPlayer) || !validPlayer((EntityPlayer) t) || getDistanceSqToEntity(t) > 1600) {
            setAttackTarget(null);
            if (getEncounterState() != 2) {
                state(2);
                returnTicks = pathFailures = 0;
                getNavigator().clearPathEntity();
            }
            heal(1F);
            if (getDistanceSq(anchorX, anchorY, anchorZ) > 1) {
                returnTicks++;
                if (ticksExisted % 10 == 0 && !getNavigator().tryMoveToXYZ(anchorX, anchorY, anchorZ, 1))
                    pathFailures++;
                if (returnTicks >= 100 || pathFailures >= 3) {
                    setPosition(anchorX, anchorY, anchorZ);
                    getNavigator().clearPathEntity();
                    motionX = motionY = motionZ = 0;
                }
            } else {
                getNavigator().clearPathEntity();
                motionX = motionY = motionZ = 0;
                if (getEncounterState() != 0) state(0);
                faceHome();
            }
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
        if (dead || deathRecorded) return;
        super.onDeath(s);
        if (!dead && !worldObj.isRemote && getHealth() <= 0) {
            // Forge cancellation does not restore health. Keep this unique guardian alive and retryable.
            setHealth(1);
            deathTime = 0;
            return;
        }
        if (dead && !worldObj.isRemote && !deathRecorded && !getEncounterId().isEmpty()) {
            deathRecorded = true;
            ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(worldObj);
            d.guardDied(getEncounterId(), d.guardIndex(getEncounterId(), getPlatformId(), ordinal));
        }
    }

    @Override
    protected void dropFewItems(boolean recentlyHit, int looting) {
        entityDropItem(LoreRegistry.oathFragment(), 0);
    }

    public void writeEntityToNBT(NBTTagCompound n) {
        super.writeEntityToNBT(n);
        n.setInteger("ordinal", ordinal);
        n.setInteger("healthSchema", 2);
        n.setBoolean("deathRecorded", deathRecorded);
    }

    public void readEntityFromNBT(NBTTagCompound n) {
        super.readEntityFromNBT(n);
        if (getCustomNameTag().equals("（旧日虚影）残誓兵")) setCustomNameTag("(旧日虚影)残誓兵");
        ordinal = n.getInteger("ordinal");
        deathRecorded = n.getBoolean("deathRecorded");
        if (!n.hasKey("healthSchema")) {
            float oldHealth = getHealth();
            getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(200);
            setHealth(Math.min(200, oldHealth * 5));
        }
        registerAnchor();
    }
}
