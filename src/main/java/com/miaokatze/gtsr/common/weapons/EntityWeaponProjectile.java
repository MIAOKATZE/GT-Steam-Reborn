package com.miaokatze.gtsr.common.weapons;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/** Server authoritative continuous segment collision; visual client integration uses the same no-drag step. */
public final class EntityWeaponProjectile extends Entity implements IEntityAdditionalSpawnData {

    private WeaponKind weapon = WeaponKind.LM12;
    private EntityLivingBase shooter;
    private UUID shooterId;
    private float damage, penetration;
    private int fire;
    private boolean critical;

    public boolean critical() {
        return critical;
    }

    public void setCritical(boolean value) {
        critical = value;
    }

    public void detonate() {
        if (isDead || worldObj.isRemote || weapon != WeaponKind.SINGULARITY) return;
        worldObj.spawnEntityInWorld(new EntityWeaponSingularity(worldObj, posX, posY, posZ, shooterId, critical));
        setDead();
    }

    private Vec3 initialOrigin, initialMuzzle;
    private boolean initialObstructionPending;

    public EntityWeaponProjectile(World world) {
        super(world);
        ignoreFrustumCheck = true;
        setSize(.12f, .12f);
    }

    public EntityWeaponProjectile(World world, EntityLivingBase owner, WeaponKind kind, float damage, float penetration,
        int fire) {
        this(world);
        this.shooter = owner;
        shooterId = owner.getUniqueID();
        weapon = kind;
        this.damage = damage;
        this.penetration = penetration;
        this.fire = fire;
        Vec3 look = owner instanceof EntityPlayer ? WeaponPose.forward((EntityPlayer) owner, 1) : owner.getLookVec();
        Vec3 muzzle = owner instanceof EntityPlayer ? WeaponPose.muzzle((EntityPlayer) owner, kind)
            : Vec3.createVectorHelper(
                owner.posX + look.xCoord * .5,
                owner.posY + owner.getEyeHeight() + look.yCoord * .5,
                owner.posZ + look.zCoord * .5);
        setPosition(muzzle.xCoord, muzzle.yCoord, muzzle.zCoord);
        initialOrigin = owner instanceof EntityPlayer ? WeaponPose.eye((EntityPlayer) owner, 1)
            : Vec3.createVectorHelper(owner.posX, owner.posY - owner.yOffset + owner.getEyeHeight(), owner.posZ);
        // Straight weapons leave the real waist muzzle and converge with the crosshair ray.
        // Grenades and singularity sparks retain their pitched ballistic launch.
        if (kind == WeaponKind.LM12 || kind == WeaponKind.T20)
            look = launchDirection(world, owner, kind, initialOrigin, muzzle, look);
        initialMuzzle = Vec3.createVectorHelper(muzzle.xCoord, muzzle.yCoord, muzzle.zCoord);
        initialObstructionPending = true;
        motionX = look.xCoord * kind.projectileSpeed;
        motionY = look.yCoord * kind.projectileSpeed;
        motionZ = look.zCoord * kind.projectileSpeed;
        rotationYaw = (float) Math.toDegrees(Math.atan2(-look.xCoord, look.zCoord));
        rotationPitch = (float) -Math.toDegrees(Math.asin(look.yCoord));
    }

    /** Shared by authoritative launch and client trajectory prediction for the same sampled pose. */
    public static Vec3 launchDirection(World world, EntityLivingBase owner, WeaponKind kind, Vec3 eye, Vec3 muzzle,
        Vec3 look) {
        if (kind != WeaponKind.LM12 && kind != WeaponKind.T20) return look;
        Vec3 end = eye.addVector(look.xCoord * 100, look.yCoord * 100, look.zCoord * 100);
        MovingObjectPosition block = world.func_147447_a(
            Vec3.createVectorHelper(eye.xCoord, eye.yCoord, eye.zCoord),
            Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord),
            false,
            true,
            false);
        Vec3 target = block == null ? end : block.hitVec;
        double nearest = eye.squareDistanceTo(target);
        for (Object object : world.getEntitiesWithinAABBExcludingEntity(
            owner,
            owner.boundingBox.addCoord(look.xCoord * 100, look.yCoord * 100, look.zCoord * 100)
                .expand(1, 1, 1))) {
            Entity entity = (Entity) object;
            if (entity.isDead || !(entity instanceof EntityLivingBase) || !entity.canBeCollidedWith()) continue;
            MovingObjectPosition hit = entity.boundingBox.expand(.1, .1, .1)
                .calculateIntercept(eye, end);
            if (hit != null && eye.squareDistanceTo(hit.hitVec) < nearest) {
                target = hit.hitVec;
                nearest = eye.squareDistanceTo(target);
            }
        }
        double dx = target.xCoord - muzzle.xCoord, dy = target.yCoord - muzzle.yCoord,
            dz = target.zCoord - muzzle.zCoord;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return length < 1.0E-8 ? look : Vec3.createVectorHelper(dx / length, dy / length, dz / length);
    }

    public WeaponKind kind() {
        return weapon;
    }

    @Override
    public boolean isInRangeToRenderDist(double distance) {
        return true;
    }

    @Override
    protected void entityInit() {}

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (ticksExisted > (weapon == WeaponKind.SINGULARITY ? 400 : 100)) {
            setDead();
            return;
        }
        Vec3 start = Vec3.createVectorHelper(posX, posY, posZ), end = start.addVector(motionX, motionY, motionZ);
        if (!worldObj.isRemote) {
            if (initialObstructionPending) {
                initialObstructionPending = false;
                // A fixture may deliberately replace the complete projectile trajectory. Only its original
                // constructor trajectory has an eye-to-muzzle obstruction segment.
                if (initialMuzzle != null && initialMuzzle.squareDistanceTo(start) < 1.0E-12) {
                    MovingObjectPosition obstruction = worldObj.func_147447_a(
                        Vec3.createVectorHelper(initialOrigin.xCoord, initialOrigin.yCoord, initialOrigin.zCoord),
                        Vec3.createVectorHelper(initialMuzzle.xCoord, initialMuzzle.yCoord, initialMuzzle.zCoord),
                        false,
                        true,
                        false);
                    if (obstruction != null) {
                        impact(obstruction);
                        setDead();
                        return;
                    }
                }
            }
            if (shooter == null && shooterId != null) for (Object object : worldObj.loadedEntityList)
                if (object instanceof EntityLivingBase && ((Entity) object).getUniqueID()
                    .equals(shooterId)) {
                        shooter = (EntityLivingBase) object;
                        break;
                    }
            // Minecraft's block DDA mutates its input Vec3; entity intersections need the original segment.
            MovingObjectPosition hit = worldObj.func_147447_a(
                Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord),
                Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord),
                false,
                true,
                false);
            double nearest = hit == null ? start.squareDistanceTo(end) : start.squareDistanceTo(hit.hitVec);
            List<?> candidates = worldObj.getEntitiesWithinAABBExcludingEntity(
                this,
                boundingBox.addCoord(motionX, motionY, motionZ)
                    .expand(.3, .3, .3));
            for (Object object : candidates) {
                Entity e = (Entity) object;
                if (e == shooter || (weapon != WeaponKind.SINGULARITY && !(e instanceof EntityLivingBase))
                    || !e.canBeCollidedWith()) continue;
                MovingObjectPosition intercept = e.boundingBox.expand(.1, .1, .1)
                    .calculateIntercept(start, end);
                double distance = e.boundingBox.isVecInside(start) ? 0
                    : intercept == null ? Double.MAX_VALUE : start.squareDistanceTo(intercept.hitVec);
                if (distance <= nearest) {
                    nearest = distance;
                    hit = new MovingObjectPosition(e);
                    hit.hitVec = distance == 0 ? start : intercept.hitVec;
                }
            }
            if (hit != null) {
                impact(hit);
                setDead();
                return;
            }
        }
        setPosition(end.xCoord, end.yCoord, end.zCoord);
        motionY -= weapon.gravity;
    }

    private void impact(MovingObjectPosition hit) {
        Vec3 at = hit.hitVec == null ? Vec3.createVectorHelper(posX, posY, posZ) : hit.hitVec;
        if (weapon == WeaponKind.SINGULARITY) {
            at = singularityImpactPoint(hit, at);
            setPosition(at.xCoord, at.yCoord, at.zCoord);
            detonate();
            return;
        }
        if (weapon == WeaponKind.QLZ04 || weapon == WeaponKind.T20) {
            double radius = weapon == WeaponKind.T20 ? .25 : 2;
            List<?> nearby = worldObj.getEntitiesWithinAABB(
                EntityLivingBase.class,
                AxisAlignedBB.getBoundingBox(
                    at.xCoord - radius,
                    at.yCoord - radius,
                    at.zCoord - radius,
                    at.xCoord + radius,
                    at.yCoord + radius,
                    at.zCoord + radius));
            for (Object o : nearby) {
                EntityLivingBase e = (EntityLivingBase) o;
                double dx = Math.max(e.boundingBox.minX, Math.min(at.xCoord, e.boundingBox.maxX)) - at.xCoord;
                double dy = Math.max(e.boundingBox.minY, Math.min(at.yCoord, e.boundingBox.maxY)) - at.yCoord;
                double dz = Math.max(e.boundingBox.minZ, Math.min(at.zCoord, e.boundingBox.maxZ)) - at.zCoord;
                if (dx * dx + dy * dy + dz * dz <= radius * radius || e == hit.entityHit)
                    applyDamage(e, damage, penetration, fire, this, shooter);
            }
        } else if (hit.entityHit instanceof EntityLivingBase)
            applyDamage((EntityLivingBase) hit.entityHit, damage, penetration, fire, this, shooter);
        if (weapon != WeaponKind.LM12) {
            Effect effect = new Effect();
            effect.entityId = shooter == null ? -1 : shooter.getEntityId();
            effect.kind = weapon.id;
            effect.type = 1;
            effect.x = at.xCoord;
            effect.y = at.yCoord;
            effect.z = at.zCoord;
            WeaponNetwork.effect(worldObj, effect);
        }
    }

    /** Keep the node outside the struck surface. Airborne remote detonation does not use this path. */
    public static Vec3 singularityImpactPoint(MovingObjectPosition hit, Vec3 at) {
        double x = 0, y = 0, z = 0;
        if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            switch (hit.sideHit) {
                case 0:
                    y = -1;
                    break;
                case 1:
                    y = 1;
                    break;
                case 2:
                    z = -1;
                    break;
                case 3:
                    z = 1;
                    break;
                case 4:
                    x = -1;
                    break;
                case 5:
                    x = 1;
                    break;
                default:
                    y = 1;
            }
        } else if (hit.entityHit != null) {
            x = at.xCoord - hit.entityHit.posX;
            y = at.yCoord - (hit.entityHit.posY + hit.entityHit.height * .5);
            z = at.zCoord - hit.entityHit.posZ;
            double length = Math.sqrt(x * x + y * y + z * z);
            if (length > 1e-6) {
                x /= length;
                y /= length;
                z /= length;
            } else {
                x = 0;
                y = 1;
                z = 0;
            }
        }
        return at.addVector(x * .4, y * .4, z * .4);
    }

    /**
     * Public production seam for dedicated-server damage audits. Armor points are partially ignored; potion/protection
     * and true invulnerability remain native.
     */
    public static boolean applyDamage(EntityLivingBase target, float damage, float penetration, int fire,
        Entity projectile, EntityLivingBase shooter) {
        if (target == null || target.isDead || target.isEntityInvulnerable()) return false;
        if (target instanceof EntityPlayer && shooter instanceof EntityPlayer && target != shooter) {
            if (!((EntityPlayer) shooter).canAttackPlayer((EntityPlayer) target)) return false;
            if (shooter instanceof EntityPlayerMP && !((EntityPlayerMP) shooter).mcServer.isPVPEnabled()) return false;
        }
        int prior = target.hurtResistantTime;
        target.hurtResistantTime = 0;
        float armor = Math.max(0, target.getTotalArmorValue() - Math.max(0, penetration));
        DamageSource source = new EntityDamageSourceIndirect("gtsr.portable", projectile, shooter).setProjectile()
            .setDamageBypassesArmor();
        boolean accepted;
        try {
            accepted = target.attackEntityFrom(source, Math.max(0, damage) * (25 - Math.min(20, armor)) / 25f);
        } finally {
            target.hurtResistantTime = prior;
        }
        if (accepted && fire > 0) target.setFire(Math.min(3, fire) * 5);
        return accepted;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound n) {
        n.setInteger("kind", weapon.id);
        n.setBoolean("critical", critical);
        n.setFloat("damage", damage);
        n.setFloat("penetration", penetration);
        n.setInteger("fire", fire);
        n.setBoolean("initialObstructionPending", initialObstructionPending);
        if (initialObstructionPending && initialOrigin != null && initialMuzzle != null) {
            n.setDouble("originX", initialOrigin.xCoord);
            n.setDouble("originY", initialOrigin.yCoord);
            n.setDouble("originZ", initialOrigin.zCoord);
            n.setDouble("muzzleX", initialMuzzle.xCoord);
            n.setDouble("muzzleY", initialMuzzle.yCoord);
            n.setDouble("muzzleZ", initialMuzzle.zCoord);
        }
        if (shooterId != null) n.setString("shooter", shooterId.toString());
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound n) {
        WeaponKind k = WeaponKind.fromId(n.getInteger("kind"));
        weapon = k == null ? WeaponKind.LM12 : k;
        critical = n.getBoolean("critical");
        damage = n.getFloat("damage");
        penetration = n.getFloat("penetration");
        fire = n.getInteger("fire");
        initialObstructionPending = n.getBoolean("initialObstructionPending");
        if (initialObstructionPending) {
            initialOrigin = Vec3
                .createVectorHelper(n.getDouble("originX"), n.getDouble("originY"), n.getDouble("originZ"));
            initialMuzzle = Vec3
                .createVectorHelper(n.getDouble("muzzleX"), n.getDouble("muzzleY"), n.getDouble("muzzleZ"));
        }
        try {
            if (n.hasKey("shooter")) shooterId = UUID.fromString(n.getString("shooter"));
        } catch (IllegalArgumentException ignored) {
            shooterId = null;
        }
    }

    @Override
    public void writeSpawnData(ByteBuf b) {
        b.writeByte(weapon.id);
        b.writeBoolean(critical);
    }

    @Override
    public void readSpawnData(ByteBuf b) {
        WeaponKind k = WeaponKind.fromId(b.readUnsignedByte());
        critical = b.readBoolean();
        weapon = k == null ? WeaponKind.LM12 : k;
    }
}
