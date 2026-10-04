package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** A tracked, swept projectile. Detonation never edits blocks or damages friendly echoes. */
public final class EchoCombatProjectile extends Entity {

    private EntityOldEcho owner;
    private float damage;
    private double blastRadius, gravity;
    private int life = 100;

    public EchoCombatProjectile(World world) {
        super(world);
        setSize(.35F, .35F);
    }

    public static void fire(EntityOldEcho owner, double x, double y, double z, double tx, double ty, double tz,
        double speed, float damage, double radius, double gravity, int palette) {
        if (owner.worldObj.isRemote) return;
        EchoCombatProjectile bolt = new EchoCombatProjectile(owner.worldObj);
        bolt.owner = owner;
        bolt.damage = damage;
        bolt.blastRadius = Math.max(.5, Math.min(6, radius));
        bolt.gravity = Math.max(0, Math.min(.1, gravity));
        bolt.dataWatcher.updateObject(20, palette);
        bolt.setPosition(x, y, z);
        double dx = tx - x, dy = ty - y, dz = tz - z, len = Math.max(.01, Math.sqrt(dx * dx + dy * dy + dz * dz));
        bolt.motionX = dx / len * speed;
        bolt.motionY = dy / len * speed;
        bolt.motionZ = dz / len * speed;
        if (owner.worldObj.getChunkProvider()
            .chunkExists(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4))
            owner.worldObj.spawnEntityInWorld(bolt);
    }

    protected void entityInit() {
        dataWatcher.addObject(20, 1);
        dataWatcher.addObject(21, 0);
    }

    public boolean hasExploded() {
        return dataWatcher.getWatchableObjectInt(21) != 0;
    }

    public int getPalette() {
        return dataWatcher.getWatchableObjectInt(20);
    }

    public boolean canBeCollidedWith() {
        return false;
    }

    public void onUpdate() {
        super.onUpdate();
        if (hasExploded()) {
            if (!worldObj.isRemote && --life <= 0) setDead();
            return;
        }
        if (worldObj.isRemote) {
            setPosition(posX + motionX, posY + motionY, posZ + motionZ);
            return;
        }
        if (--life <= 0 || owner == null || !owner.isEntityAlive()) {
            setDead();
            return;
        }
        double nx = posX + motionX, ny = posY + motionY, nz = posZ + motionZ;
        if (!worldObj.getChunkProvider()
            .chunkExists(((int) Math.floor(nx)) >> 4, ((int) Math.floor(nz)) >> 4)) {
            setDead();
            return;
        }
        Vec3 from = Vec3.createVectorHelper(posX, posY, posZ), to = Vec3.createVectorHelper(nx, ny, nz);
        MovingObjectPosition block = worldObj.rayTraceBlocks(from, to);
        double nearest = block == null ? Double.POSITIVE_INFINITY : from.squareDistanceTo(block.hitVec);
        Vec3 impact = block == null ? null : block.hitVec;
        for (Object object : worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) object;
            if (!valid(p)) continue;
            net.minecraft.util.AxisAlignedBB box = p.boundingBox.expand(.2, .2, .2);
            MovingObjectPosition hit = box.calculateIntercept(from, to);
            if (box.isVecInside(from)) {
                nearest = 0;
                impact = from;
            } else if (hit != null && from.squareDistanceTo(hit.hitVec) < nearest) {
                nearest = from.squareDistanceTo(hit.hitVec);
                impact = hit.hitVec;
            }
        }
        if (impact != null) {
            setPosition(impact.xCoord, impact.yCoord, impact.zCoord);
            detonate();
            return;
        }
        setPosition(nx, ny, nz);
        motionY -= gravity;
    }

    private boolean valid(EntityPlayer p) {
        return p.isEntityAlive() && !p.capabilities.isCreativeMode;
    }

    private void detonate() {
        CombatEffects.send(
            this,
            32,
            1,
            16,
            getPalette(),
            new CombatGeometry(CombatGeometry.CIRCLE, posX, posY, posZ, 0, 0, blastRadius, 0));
        worldObj.playSoundEffect(posX, posY, posZ, "gtsr:echo.projectile.explode", 1, .8F);
        for (Object object : worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) object;
            if (!valid(p) || p.getDistanceSq(posX, posY, posZ) > blastRadius * blastRadius) continue;
            Vec3 head = Vec3.createVectorHelper(p.posX, p.posY + p.getEyeHeight(), p.posZ);
            // Nudge the blast point out of its impact surface before testing line of sight.
            Vec3 origin = Vec3.createVectorHelper(posX - motionX * .1, posY - motionY * .1, posZ - motionZ * .1);
            if (worldObj.rayTraceBlocks(origin, head) != null) continue;
            if (p.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, owner), damage)) {
                double dx = p.posX - posX, dz = p.posZ - posZ, len = Math.max(.1, Math.sqrt(dx * dx + dz * dz));
                p.addVelocity(dx / len * .25, .12, dz / len * .25);
                p.velocityChanged = true;
            }
        }
        dataWatcher.updateObject(21, 1);
        motionX = motionY = motionZ = 0;
        life = 16;
    }

    protected void writeEntityToNBT(NBTTagCompound n) {
        n.setInteger("palette", getPalette());
    }

    protected void readEntityFromNBT(NBTTagCompound n) {
        dataWatcher.updateObject(20, n.getInteger("palette"));
        life = 0;
    }
}
