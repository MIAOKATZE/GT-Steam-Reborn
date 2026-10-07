package com.miaokatze.gtsr.common.weapons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
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
    private boolean critical, fragment;
    private int mode;
    private Vec3 visualOrigin, visualDirection;

    public int mode() {
        return mode;
    }

    public boolean fragment() {
        return fragment;
    }

    public int flightAge() {
        return ticksExisted;
    }

    public Vec3 visualOrigin() {
        return visualOrigin == null ? Vec3.createVectorHelper(posX, posY, posZ) : visualOrigin;
    }

    public Vec3 visualDirection() {
        return visualDirection == null ? Vec3.createVectorHelper(motionX, motionY, motionZ)
            .normalize() : visualDirection;
    }

    public void setMode(int value) {
        mode = WeaponMode.mode(weapon, value);
        if (!fragment && visualDirection != null && ticksExisted == 0) {
            double speed = WeaponMode.projectileSpeed(weapon, mode);
            motionX = visualDirection.xCoord * speed;
            motionY = visualDirection.yCoord * speed;
            motionZ = visualDirection.zCoord * speed;
        }
    }

    private WeaponShotEnchantments shotEnchantments = new WeaponShotEnchantments();

    public void freezeEnchantments(ItemStack stack) {
        shotEnchantments = new WeaponShotEnchantments(stack);
    }

    public WeaponShotEnchantments enchantments() {
        return shotEnchantments;
    }

    public boolean critical() {
        return critical;
    }

    public void setCritical(boolean value) {
        critical = value;
    }

    public void detonate() {
        if (isDead || worldObj.isRemote || weapon != WeaponKind.SINGULARITY) return;
        EntityWeaponSingularity node = new EntityWeaponSingularity(
            worldObj,
            posX,
            posY,
            posZ,
            shooterId,
            critical,
            shotEnchantments,
            mode == WeaponMode.ALTERNATE);
        if (mode == WeaponMode.ALTERNATE) node.explodeInstantly();
        else worldObj.spawnEntityInWorld(node);
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
        WeaponPose.Physical pose = owner instanceof EntityPlayer ? WeaponPose.physical((EntityPlayer) owner, kind, 1)
            : null;
        Vec3 look = pose == null ? owner.getLookVec() : pose.forward;
        Vec3 localMuzzle = WeaponPose.localMuzzle(kind);
        Vec3 muzzle = pose != null ? pose.modelPoint(localMuzzle.xCoord, localMuzzle.yCoord, localMuzzle.zCoord)
            : Vec3.createVectorHelper(
                owner.posX + look.xCoord * .5,
                owner.posY + owner.getEyeHeight() + look.yCoord * .5,
                owner.posZ + look.zCoord * .5);
        setPosition(muzzle.xCoord, muzzle.yCoord, muzzle.zCoord);
        initialOrigin = owner instanceof EntityPlayer ? WeaponPose.eye((EntityPlayer) owner, 1)
            : Vec3.createVectorHelper(owner.posX, owner.posY - owner.yOffset + owner.getEyeHeight(), owner.posZ);
        // Straight weapons leave the real waist muzzle and converge with the crosshair ray.
        // Grenades and singularity sparks retain their pitched ballistic launch.
        if (pose == null && (kind == WeaponKind.LM12 || kind == WeaponKind.T20))
            look = launchDirection(world, owner, kind, initialOrigin, muzzle, look);
        initialMuzzle = Vec3.createVectorHelper(muzzle.xCoord, muzzle.yCoord, muzzle.zCoord);
        initialObstructionPending = true;
        visualOrigin = Vec3.createVectorHelper(posX, posY, posZ);
        visualDirection = look;
        double speed = WeaponMode.projectileSpeed(kind, mode);
        motionX = look.xCoord * speed;
        motionY = look.yCoord * speed;
        motionZ = look.zCoord * speed;
        rotationYaw = (float) Math.toDegrees(Math.atan2(-look.xCoord, look.zCoord));
        rotationPitch = (float) -Math.toDegrees(Math.asin(look.yCoord));
    }

    /** Straight-weapon convergence for non-player owners; players use their rigid physical pose. */
    public static Vec3 launchDirection(World world, EntityLivingBase owner, WeaponKind kind, Vec3 eye, Vec3 muzzle,
        Vec3 look) {
        if (kind != WeaponKind.LM12 && kind != WeaponKind.T20) return look;
        Vec3 target = aimTarget(world, owner, eye, look);
        double dx = target.xCoord - muzzle.xCoord, dy = target.yCoord - muzzle.yCoord,
            dz = target.zCoord - muzzle.zCoord;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return length < 1.0E-8 ? look : Vec3.createVectorHelper(dx / length, dy / length, dz / length);
    }

    /** Eye ray selection is independent of the gun transform, preventing recursive pose evaluation. */
    public static Vec3 aimTarget(World world, EntityLivingBase owner, Vec3 eye, Vec3 look) {
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
        return target;
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
        if (ticksExisted > (fragment ? 10 : weapon == WeaponKind.SINGULARITY ? 400 : 100)) {
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
            boolean airburst = !fragment && weapon == WeaponKind.T20 && mode == WeaponMode.ALTERNATE;
            boolean shotgun = !fragment && weapon == WeaponKind.QLZ04 && mode == WeaponMode.ALTERNATE;
            Vec3 forward = Vec3.createVectorHelper(motionX, motionY, motionZ)
                .normalize();
            Vec3 probeEnd = shotgun ? start.addVector(forward.xCoord * 4, forward.yCoord * 4, forward.zCoord * 4) : end;
            double reach = airburst ? 1.5 : .1;
            double nearest = hit == null ? start.squareDistanceTo(probeEnd) : start.squareDistanceTo(hit.hitVec);
            if (shotgun) {
                MovingObjectPosition obstruction = worldObj.func_147447_a(
                    Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord),
                    Vec3.createVectorHelper(probeEnd.xCoord, probeEnd.yCoord, probeEnd.zCoord),
                    false,
                    true,
                    false);
                if (obstruction != null) nearest = Math.min(nearest, start.squareDistanceTo(obstruction.hitVec));
            }
            List<?> candidates = worldObj.getEntitiesWithinAABBExcludingEntity(
                this,
                boundingBox
                    .addCoord(
                        probeEnd.xCoord - start.xCoord,
                        probeEnd.yCoord - start.yCoord,
                        probeEnd.zCoord - start.zCoord)
                    .expand(reach + .2, reach + .2, reach + .2));
            for (Object object : candidates) {
                Entity e = (Entity) object;
                if (e.isDead || e == shooter
                    || (weapon != WeaponKind.SINGULARITY && !(e instanceof EntityLivingBase))
                    || !e.canBeCollidedWith()) continue;
                Vec3 contact;
                if (airburst) contact = proximityIntercept(e.boundingBox, start, probeEnd, 1.5);
                else {
                    MovingObjectPosition intercept = e.boundingBox.expand(reach, reach, reach)
                        .calculateIntercept(start, probeEnd);
                    contact = e.boundingBox.expand(reach, reach, reach)
                        .isVecInside(start) ? start : intercept == null ? null : intercept.hitVec;
                }
                double distance = contact == null ? Double.MAX_VALUE : start.squareDistanceTo(contact);
                if (distance <= nearest) {
                    nearest = distance;
                    hit = new MovingObjectPosition(e);
                    hit.hitVec = contact;
                }
            }
            if (hit != null) {
                if (shotgun && hit.entityHit != null) hit.hitVec = start;
                impact(hit);
                setDead();
                return;
            }
        }
        setPosition(end.xCoord, end.yCoord, end.zCoord);
        motionY -= fragment ? 0 : WeaponMode.gravity(weapon, mode);
    }

    private void impact(MovingObjectPosition hit) {
        Vec3 at = hit.hitVec == null ? Vec3.createVectorHelper(posX, posY, posZ) : hit.hitVec;
        if (weapon == WeaponKind.SINGULARITY) {
            at = singularityImpactPoint(hit, at);
            setPosition(at.xCoord, at.yCoord, at.zCoord);
            detonate();
            return;
        }
        if (!fragment && weapon == WeaponKind.QLZ04 && mode == WeaponMode.ALTERNATE) {
            splitFragments(at);
        } else if (!fragment && (weapon == WeaponKind.QLZ04 || weapon == WeaponKind.T20)) {
            double radius = weapon == WeaponKind.T20 ? (mode == WeaponMode.ALTERNATE ? 2.5 : 1) : 2;
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
        if (weapon != WeaponKind.LM12 && !fragment) {
            Effect effect = new Effect();
            effect.entityId = shooter == null ? -1 : shooter.getEntityId();
            effect.kind = weapon.id;
            effect.type = 1;
            effect.mode = mode;
            effect.ammoType = critical ? 1 : 0;
            effect.x = at.xCoord;
            effect.y = at.yCoord;
            effect.z = at.zCoord;
            WeaponNetwork.effect(worldObj, effect);
        }
    }

    /** First contact with the exact rounded AABB, including fast segments that enter and leave in one tick. */
    public static Vec3 proximityIntercept(AxisAlignedBB box, Vec3 start, Vec3 end, double radius) {
        double[] origin = { start.xCoord, start.yCoord, start.zCoord };
        double[] delta = { end.xCoord - start.xCoord, end.yCoord - start.yCoord, end.zCoord - start.zCoord };
        double[] low = { box.minX, box.minY, box.minZ }, high = { box.maxX, box.maxY, box.maxZ };
        List<Double> boundaries = new ArrayList<Double>();
        boundaries.add(0d);
        boundaries.add(1d);
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1e-12) continue;
            double enter = (low[axis] - origin[axis]) / delta[axis];
            double leave = (high[axis] - origin[axis]) / delta[axis];
            if (enter > 0 && enter < 1) boundaries.add(enter);
            if (leave > 0 && leave < 1) boundaries.add(leave);
        }
        Collections.sort(boundaries);
        for (int i = 0; i + 1 < boundaries.size(); i++) {
            double from = boundaries.get(i), to = boundaries.get(i + 1), middle = (from + to) * .5;
            double a = 0, b = 0, c = -radius * radius;
            for (int axis = 0; axis < 3; axis++) {
                double point = origin[axis] + delta[axis] * middle;
                double bound = point < low[axis] ? low[axis] : point > high[axis] ? high[axis] : point;
                if (point == bound) continue;
                double offset = origin[axis] - bound;
                a += delta[axis] * delta[axis];
                b += 2 * offset * delta[axis];
                c += offset * offset;
            }
            double contact = from;
            if (a * from * from + b * from + c > 1e-10) {
                if (a < 1e-12) continue;
                double discriminant = b * b - 4 * a * c;
                if (discriminant < 0) continue;
                contact = (-b - Math.sqrt(discriminant)) / (2 * a);
                if (contact < from - 1e-10 || contact > to + 1e-10) continue;
                contact = Math.max(from, Math.min(to, contact));
            }
            return start.addVector(delta[0] * contact, delta[1] * contact, delta[2] * contact);
        }
        return null;
    }

    private void splitFragments(Vec3 at) {
        Vec3 forward = Vec3.createVectorHelper(motionX, motionY, motionZ)
            .normalize();
        Vec3 side = Math.abs(forward.yCoord) > .99 ? Vec3.createVectorHelper(1, 0, 0)
            : Vec3.createVectorHelper(-forward.zCoord, 0, forward.xCoord)
                .normalize();
        Vec3 up = forward.crossProduct(side)
            .normalize();
        for (int i = 0; i < 30; i++) {
            // Uniform solid-angle sampling inside the 30-degree half-angle cone.
            double cos = 1 - rand.nextDouble() * (1 - Math.cos(Math.PI / 6));
            double sin = Math.sqrt(1 - cos * cos), angle = rand.nextDouble() * Math.PI * 2;
            Vec3 direction = Vec3.createVectorHelper(
                forward.xCoord * cos + sin * (side.xCoord * Math.cos(angle) + up.xCoord * Math.sin(angle)),
                forward.yCoord * cos + sin * (side.yCoord * Math.cos(angle) + up.yCoord * Math.sin(angle)),
                forward.zCoord * cos + sin * (side.zCoord * Math.cos(angle) + up.zCoord * Math.sin(angle)));
            EntityWeaponProjectile child = new EntityWeaponProjectile(worldObj);
            child.weapon = weapon;
            child.mode = mode;
            child.fragment = true;
            child.shooter = shooter;
            child.shooterId = shooterId;
            child.shotEnchantments = shotEnchantments;
            child.critical = critical;
            child.damage = 2;
            child.penetration = penetration;
            child.fire = fire;
            child.visualOrigin = Vec3.createVectorHelper(at.xCoord, at.yCoord, at.zCoord);
            child.visualDirection = direction;
            child.setPosition(at.xCoord, at.yCoord, at.zCoord);
            child.motionX = direction.xCoord * 3;
            child.motionY = direction.yCoord * 3;
            child.motionZ = direction.zCoord * 3;
            worldObj.spawnEntityInWorld(child);
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
        DamageSource source = new WeaponDamageSource(
            "gtsr.portable",
            projectile,
            shooter,
            projectile instanceof EntityWeaponProjectile
                ? ((EntityWeaponProjectile) projectile).shotEnchantments.looting
                : 0).setProjectile()
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
        shotEnchantments.write(n);
        n.setInteger("kind", weapon.id);
        n.setBoolean("critical", critical);
        n.setInteger("mode", mode);
        n.setBoolean("fragment", fragment);
        Vec3 origin = visualOrigin(), direction = visualDirection();
        n.setDouble("visualX", origin.xCoord);
        n.setDouble("visualY", origin.yCoord);
        n.setDouble("visualZ", origin.zCoord);
        n.setDouble("directionX", direction.xCoord);
        n.setDouble("directionY", direction.yCoord);
        n.setDouble("directionZ", direction.zCoord);
        n.setInteger("flightAge", ticksExisted);
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
        shotEnchantments = WeaponShotEnchantments.read(n);
        WeaponKind k = WeaponKind.fromId(n.getInteger("kind"));
        weapon = k == null ? WeaponKind.LM12 : k;
        critical = n.getBoolean("critical");
        mode = WeaponMode.mode(weapon, n.getInteger("mode"));
        fragment = n.getBoolean("fragment");
        visualOrigin = n.hasKey("visualX")
            ? Vec3.createVectorHelper(n.getDouble("visualX"), n.getDouble("visualY"), n.getDouble("visualZ"))
            : null;
        visualDirection = n.hasKey("directionX")
            ? Vec3.createVectorHelper(n.getDouble("directionX"), n.getDouble("directionY"), n.getDouble("directionZ"))
            : null;
        ticksExisted = Math.max(0, n.getInteger("flightAge"));
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
        shotEnchantments.write(b);
        b.writeByte(weapon.id);
        b.writeBoolean(critical);
        b.writeByte(mode);
        b.writeBoolean(fragment);
        Vec3 origin = visualOrigin(), direction = visualDirection();
        b.writeDouble(origin.xCoord);
        b.writeDouble(origin.yCoord);
        b.writeDouble(origin.zCoord);
        b.writeDouble(direction.xCoord);
        b.writeDouble(direction.yCoord);
        b.writeDouble(direction.zCoord);
        b.writeInt(ticksExisted);
    }

    @Override
    public void readSpawnData(ByteBuf b) {
        shotEnchantments = WeaponShotEnchantments.read(b);
        WeaponKind k = WeaponKind.fromId(b.readUnsignedByte());
        critical = b.readBoolean();
        weapon = k == null ? WeaponKind.LM12 : k;
        mode = WeaponMode.mode(weapon, b.readUnsignedByte());
        fragment = b.readBoolean();
        visualOrigin = Vec3.createVectorHelper(b.readDouble(), b.readDouble(), b.readDouble());
        visualDirection = Vec3.createVectorHelper(b.readDouble(), b.readDouble(), b.readDouble());
        ticksExisted = b.readInt();
    }
}
