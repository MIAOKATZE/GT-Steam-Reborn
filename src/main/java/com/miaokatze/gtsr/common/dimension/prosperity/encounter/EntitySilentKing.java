package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/** A fixed throne encounter, never a natural spawn. */
public class EntitySilentKing extends EntityEncounterBase {

    public static final int DORMANT = 0, AWAKENING = 1, ACTIVE = 2, RECOVERING = 3, DEFEATED = 4;
    public static final int ALERT_RADIUS = 55;
    private int combatTicks;
    private double echoX, echoY, echoZ;

    public EntitySilentKing(World w) {
        super(w);
        setSize(6F, 12F);
        setCustomNameTag("旧日王座");
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(1000);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);
    }

    public boolean isEntityInvulnerable() {
        return getEncounterState() != ACTIVE || super.isEntityInvulnerable();
    }

    protected void onDeathUpdate() {
        if (++deathTime >= 120) setDead();
    }

    public boolean attackEntityFrom(DamageSource s, float a) {
        return getEncounterState() == ACTIVE && super.attackEntityFrom(s, a);
    }

    public void moveEntity(double x, double y, double z) {}

    public boolean canBePushed() {
        return false;
    }

    private List<EntityPlayer> players() {
        List<EntityPlayer> out = new ArrayList<>();
        for (Object o : worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(55, 24, 55))) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.isEntityAlive() && p.worldObj == worldObj
                && !p.capabilities.isCreativeMode
                && getDistanceSqToEntity(p) <= ALERT_RADIUS * ALERT_RADIUS) out.add(p);
        }
        return out;
    }

    public void onLivingUpdate() {
        super.onLivingUpdate();
        motionX = motionY = motionZ = 0;
        if (worldObj.isRemote) return;
        setPosition(anchorX, anchorY, anchorZ);
        if (getEncounterId().isEmpty()) return;
        ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(worldObj);
        if (d.kingDead(getEncounterId())) {
            if (getEncounterState() != DEFEATED) setDead();
            return;
        }
        int s = getEncounterState();
        if (s == DORMANT) {
            if (d.allGuardsDead(getEncounterId())) {
                state(AWAKENING);
                setHealth(1);
                setCustomNameTag("（旧日虚影）缄王");
            }
            return;
        }
        if (s == AWAKENING) {
            int t = getVisualPhaseTicks() + 1;
            phase(t);
            setHealth(Math.max(1, 1000F * t / 104F));
            if (t >= 104) {
                setHealth(1000);
                state(ACTIVE);
            }
            return;
        }
        List<EntityPlayer> ps = players();
        if (ps.isEmpty()) {
            if (s != RECOVERING) state(RECOVERING);
            setHealth(Math.min(1000, getHealth() + 20));
            combatTicks = 0;
            return;
        }
        if (s == RECOVERING) state(ACTIVE);
        phase(getVisualPhaseTicks() + 1);
        combatTicks++;
        int beat = combatTicks % 160;
        if (beat == 1) {
            EntityPlayer p = ps.get(0);
            echoX = p.posX;
            echoY = p.posY;
            echoZ = p.posZ;
        }
        if (beat >= 1 && beat <= 35 && beat % 5 == 0) {
            worldObj.playSoundEffect(echoX, echoY, echoZ, "note.harp", .8F, 1.5F);
            for (int i = 0; i < 16; i++) {
                double r = i * Math.PI / 8;
                if (worldObj instanceof net.minecraft.world.WorldServer)
                    ((net.minecraft.world.WorldServer) worldObj).func_147487_a(
                        "reddust",
                        echoX + 3 * Math.cos(r),
                        echoY + .2,
                        echoZ + 3 * Math.sin(r),
                        1,
                        0,
                        0,
                        0,
                        0);
            }
        }
        if (beat == 36) for (EntityPlayer p : ps)
            if (p.getDistanceSq(echoX, echoY, echoZ) < 16) p.attackEntityFrom(DamageSource.causeMobDamage(this), 10);
        if (beat >= 90 && beat < 120 && beat % 5 == 0) {
            worldObj.playSoundEffect(posX, posY, posZ, "random.fizz", 1, .6F);
            if (worldObj instanceof net.minecraft.world.WorldServer) for (int i = 0; i < 32; i++) {
                double r = i * Math.PI / 16;
                ((net.minecraft.world.WorldServer) worldObj).func_147487_a(
                    "reddust",
                    posX + 24 * Math.cos(r),
                    posY + .2,
                    posZ + 24 * Math.sin(r),
                    1,
                    0,
                    0,
                    0,
                    0);
            }
        }
        if (beat == 120) {
            worldObj.playSoundEffect(posX, posY, posZ, "random.explode", 1, .6F);
            for (EntityPlayer p : ps) if (getDistanceSqToEntity(p) < 24 * 24 && Math.abs(p.posY - posY) < 10) {
                p.attackEntityFrom(DamageSource.causeMobDamage(this), 8);
                p.addVelocity((p.posX - posX) * .03, .15, (p.posZ - posZ) * .03);
            }
        }
    }

    public void onDeath(DamageSource s) {
        super.onDeath(s);
        if (dead && !worldObj.isRemote) {
            ForgottenLakeEncounterData.get(worldObj)
                .kingDied(getEncounterId());
            state(DEFEATED);
        }
    }

    public void writeEntityToNBT(NBTTagCompound n) {
        super.writeEntityToNBT(n);
        n.setInteger("combat", combatTicks);
        n.setDouble("ex", echoX);
        n.setDouble("ey", echoY);
        n.setDouble("ez", echoZ);
    }

    public void readEntityFromNBT(NBTTagCompound n) {
        super.readEntityFromNBT(n);
        combatTicks = n.getInteger("combat");
        echoX = n.getDouble("ex");
        echoY = n.getDouble("ey");
        echoZ = n.getDouble("ez");
    }
}
