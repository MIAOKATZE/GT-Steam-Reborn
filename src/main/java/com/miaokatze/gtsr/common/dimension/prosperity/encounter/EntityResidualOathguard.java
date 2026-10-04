package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry;

public class EntityResidualOathguard extends EntityEncounterBase {

    private int ordinal;
    private UUID kingOwner;
    private boolean kingDormant, kingElite;
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
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.23);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
    }

    protected boolean isAIEnabled() {
        return true;
    }

    protected String getLivingSound() {
        return "gtsr:oathguard.idle";
    }

    protected String getHurtSound() {
        return "gtsr:oathguard.hurt";
    }

    protected String getDeathSound() {
        return "gtsr:oathguard.death";
    }

    public int getDeathAnimationDuration() {
        return 260;
    }

    public int getDeathAnimationHoldTicks() {
        return 100;
    }

    public boolean isGoldenDeath() {
        return true;
    }

    public boolean isKingSummon(UUID king) {
        return kingOwner != null && kingOwner.equals(king);
    }

    public boolean isKingSummonDormant() {
        return kingOwner != null && kingDormant;
    }

    public void configureKingSummon(UUID owner, boolean dormant, boolean elite) {
        kingOwner = owner;
        kingDormant = dormant;
        kingElite = elite;
        applyKingBuffs();
    }

    private void applyKingBuffs() {
        addPotionEffect(new PotionEffect(Potion.regeneration.id, Integer.MAX_VALUE, kingElite ? 1 : 0));
        addPotionEffect(new PotionEffect(Potion.resistance.id, Integer.MAX_VALUE, kingElite ? 2 : 1));
        addPotionEffect(new PotionEffect(Potion.field_76434_w.id, Integer.MAX_VALUE, kingElite ? 2 : 1));
        addPotionEffect(new PotionEffect(Potion.damageBoost.id, Integer.MAX_VALUE, kingElite ? 2 : 1));
        if (kingElite) addPotionEffect(new PotionEffect(Potion.moveSpeed.id, Integer.MAX_VALUE, 0));
        setHealth(getMaxHealth());
    }

    public void activateKingSummon(EntityPlayer player) {
        kingDormant = false;
        anger(player);
    }

    public boolean isEntityInvulnerable() {
        return isKingSummonDormant() || super.isEntityInvulnerable();
    }

    public void setOrdinal(int n) {
        ordinal = n;
        registerAnchor();
    }

    private void registerAnchor() {
        if (worldObj.isRemote || kingOwner != null || getEncounterId().isEmpty()) return;
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
        if (isKingSummonDormant()) return false;
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
        if (kingOwner != null) {
            if (ForgottenLakeEncounterData.get(worldObj)
                .kingDead(getEncounterId())) {
                setDead();
                return;
            }
            for (Object o : worldObj.loadedEntityList)
                if (o instanceof EntitySilentKing && ((EntitySilentKing) o).getUniqueID()
                    .equals(kingOwner) && !((EntitySilentKing) o).ownsSummonedGuard(getUniqueID())) {
                        setDead();
                        return;
                    }
            if (kingDormant) {
                setAttackTarget(null);
                getNavigator().clearPathEntity();
                motionX = motionY = motionZ = 0;
                faceHome();
                return;
            }
            if (getAttackTarget() == null || !getAttackTarget().isEntityAlive()) {
                EntityPlayer nearest = worldObj.getClosestVulnerablePlayerToEntity(this, 55);
                if (validPlayer(nearest)) anger(nearest);
            }
        }
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
            if (kingOwner == null && (Math.abs(posY - anchorY) > 4 || getDistanceSq(anchorX, anchorY, anchorZ) > 225)) {
                setPosition(anchorX, anchorY, anchorZ);
                getNavigator().clearPathEntity();
            } else if (ticksExisted % 10 == 0) getNavigator().tryMoveToEntityLiving(t, 1);
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
        if (dead && !worldObj.isRemote && kingOwner != null) {
            for (Object o : worldObj.loadedEntityList)
                if (o instanceof EntitySilentKing && ((EntitySilentKing) o).getUniqueID()
                    .equals(kingOwner)) ((EntitySilentKing) o).summonedGuardDied(getUniqueID());
        }
        if (dead && !worldObj.isRemote && !deathRecorded && kingOwner == null && !getEncounterId().isEmpty()) {
            deathRecorded = true;
            ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(worldObj);
            d.guardDied(getEncounterId(), d.guardIndex(getEncounterId(), getPlatformId(), ordinal));
        }
    }

    @Override
    protected void dropFewItems(boolean recentlyHit, int looting) {
        if (kingOwner == null) entityDropItem(LoreRegistry.oathFragment(), 0);
    }

    public void writeEntityToNBT(NBTTagCompound n) {
        super.writeEntityToNBT(n);
        n.setInteger("ordinal", ordinal);
        if (kingOwner != null) n.setString("kingOwner", kingOwner.toString());
        n.setBoolean("kingDormant", kingDormant);
        n.setBoolean("kingElite", kingElite);
        n.setInteger("healthSchema", 2);
        n.setBoolean("deathRecorded", deathRecorded);
    }

    public void readEntityFromNBT(NBTTagCompound n) {
        super.readEntityFromNBT(n);
        if (getCustomNameTag().equals("（旧日虚影）残誓兵")) setCustomNameTag("(旧日虚影)残誓兵");
        try {
            kingOwner = UUID.fromString(n.getString("kingOwner"));
        } catch (IllegalArgumentException ignored) {
            kingOwner = null;
        }
        kingDormant = n.getBoolean("kingDormant");
        kingElite = n.getBoolean("kingElite");
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
