package com.miaokatze.gtsr.common.weapons;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/** Stationary, server-owned gravity node. It deliberately affects teammates and other players. */
public final class EntityWeaponSingularity extends Entity implements IEntityAdditionalSpawnData {

    private boolean critical;
    private UUID owner;
    private int age;

    public EntityWeaponSingularity(World world) {
        super(world);
        ignoreFrustumCheck = true;
        setSize(.7f, .7f);
    }

    public EntityWeaponSingularity(World world, double x, double y, double z, UUID owner, boolean critical) {
        this(world);
        setPosition(x, y, z);
        this.owner = owner;
        this.critical = critical;
    }

    public boolean critical() {
        return critical;
    }

    public int life() {
        return age;
    }

    @Override
    public boolean isInRangeToRenderDist(double distance) {
        return true;
    }

    @Override
    protected void entityInit() {}

    public static boolean eligible(Entity target, UUID owner) {
        return target != null && !target.isDead
            && !(target instanceof EntityWeaponSingularity)
            && (owner == null || !owner.equals(target.getUniqueID()));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        age++;
        if (worldObj.isRemote) {
            if (age >= 160) setDead();
            return;
        }
        double radius = critical ? 10 : 5;
        for (Object object : worldObj
            .getEntitiesWithinAABBExcludingEntity(this, boundingBox.expand(radius, radius, radius))) {
            Entity target = (Entity) object;
            if (!eligible(target, owner)) continue;
            double dx = posX - target.posX, dy = posY - (target.posY + target.height * .5), dz = posZ - target.posZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distanceSquared(target) > radius * radius) continue;
            if (distance >= .1) {
                double pull = .075 + .085 * (1 - distance / radius);
                target.motionX += dx / distance * pull;
                target.motionY += dy / distance * pull;
                target.motionZ += dz / distance * pull;
                target.velocityChanged = true;
            }
            if (age % 20 == 0 && target instanceof EntityLivingBase)
                hurt((EntityLivingBase) target, critical ? 5 : 2, false);
        }
        if (age >= 160) {
            explode();
            setDead();
        }
    }

    private double distanceSquared(Entity target) {
        double dx = Math.max(target.boundingBox.minX, Math.min(posX, target.boundingBox.maxX)) - posX;
        double dy = Math.max(target.boundingBox.minY, Math.min(posY, target.boundingBox.maxY)) - posY;
        double dz = Math.max(target.boundingBox.minZ, Math.min(posZ, target.boundingBox.maxZ)) - posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private void hurt(EntityLivingBase target, float amount, boolean magic) {
        int previous = target.hurtResistantTime;
        target.hurtResistantTime = 0;
        try {
            target.attackEntityFrom(magic ? DamageSource.magic : new DamageSource("gtsr.singularity"), amount);
        } finally {
            target.hurtResistantTime = previous;
        }
    }

    private void explode() {
        double radius = critical ? 6 : 3;
        for (Object object : worldObj
            .getEntitiesWithinAABB(EntityLivingBase.class, boundingBox.expand(radius, radius, radius))) {
            EntityLivingBase target = (EntityLivingBase) object;
            if (!eligible(target, owner) || distanceSquared(target) > radius * radius) continue;
            hurt(target, critical ? 50 : 20, false);
            hurt(target, critical ? 15 : 5, true);
        }
        Effect effect = new Effect();
        effect.kind = WeaponKind.SINGULARITY.id;
        effect.type = 1;
        effect.entityId = getEntityId();
        effect.x = posX;
        effect.y = posY;
        effect.z = posZ;
        effect.shotSerial = critical ? 1 : 0;
        WeaponNetwork.effect(worldObj, effect);
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound n) {
        n.setBoolean("critical", critical);
        n.setInteger("age", age);
        if (owner != null) n.setString("owner", owner.toString());
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound n) {
        critical = n.getBoolean("critical");
        age = n.getInteger("age");
        try {
            owner = n.hasKey("owner") ? UUID.fromString(n.getString("owner")) : null;
        } catch (IllegalArgumentException e) {
            owner = null;
        }
    }

    @Override
    public void writeSpawnData(ByteBuf b) {
        b.writeBoolean(critical);
        b.writeInt(age);
    }

    @Override
    public void readSpawnData(ByteBuf b) {
        critical = b.readBoolean();
        age = b.readInt();
    }
}
