package com.miaokatze.gtsr.common.weapons;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/** Stationary, server-owned gravity node. It deliberately affects teammates and other players. */
public final class EntityWeaponSingularity extends Entity implements IEntityAdditionalSpawnData {

    private boolean critical;
    private UUID owner;
    private int age, blockCursor, blockSweepRemaining, blockSweepCooldown;
    private WeaponShotEnchantments shotEnchantments = new WeaponShotEnchantments();

    public EntityWeaponSingularity(World world) {
        super(world);
        ignoreFrustumCheck = true;
        setSize(.7f, .7f);
    }

    public EntityWeaponSingularity(World world, double x, double y, double z, UUID owner, boolean critical) {
        this(world, x, y, z, owner, critical, new WeaponShotEnchantments());
    }

    public EntityWeaponSingularity(World world, double x, double y, double z, UUID owner, boolean critical,
        WeaponShotEnchantments enchantments) {
        this(world);
        shotEnchantments = enchantments;
        setPosition(x, y, z);
        this.owner = owner;
        this.critical = critical;
    }

    public WeaponShotEnchantments enchantments() {
        return shotEnchantments;
    }

    public int duration() {
        return 160 + 30 * shotEnchantments.duration;
    }

    public double absorptionRadius() {
        return (critical ? 10 : 5) + shotEnchantments.diffusion;
    }

    public float dotDamage() {
        return (critical ? 5 : 2) * (1 + .2f * shotEnchantments.tearing);
    }

    public float explosionDamage(boolean magic) {
        return (magic ? (critical ? 15 : 5) : (critical ? 50 : 20)) * (1 + .2f * shotEnchantments.quenching);
    }

    public static boolean absorbableHardness(float hardness) {
        return hardness >= 0 && hardness <= 50;
    }

    public int blockBudget() {
        return 5 * shotEnchantments.destruction;
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
            if (age >= duration()) setDead();
            return;
        }
        double radius = absorptionRadius();
        absorbBlocks(radius);
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
                hurt((EntityLivingBase) target, dotDamage(), false);
        }
        if (age >= duration()) {
            explode();
            setDead();
        }
    }

    /** Resume a bounded sweep instead of sorting the full sphere every tick. Air never consumes the removal budget. */
    private void absorbBlocks(double radius) {
        int budget = blockBudget();
        if (budget == 0) return;
        if (blockSweepCooldown > 0) {
            blockSweepCooldown--;
            return;
        }
        int reach = (int) Math.ceil(radius), width = 2 * reach + 1, volume = width * width * width;
        if (blockSweepRemaining <= 0) blockSweepRemaining = volume;
        int cx = MathHelper.floor_double(posX), cy = MathHelper.floor_double(posY), cz = MathHelper.floor_double(posZ);
        int removed = 0;
        // Bound terrain reads even in a depleted sphere; resume from this cursor next tick.
        for (int scanned = 0; scanned < 4096 && blockSweepRemaining > 0
            && removed < budget; scanned++, blockSweepRemaining--) {
            int index = blockCursor++ % volume;
            int x = cx + index % width - reach, y = cy + index / width % width - reach,
                z = cz + index / (width * width) - reach;
            double dx = x + .5 - posX, dy = y + .5 - posY, dz = z + .5 - posZ;
            if (y < 0 || y >= worldObj.getHeight()
                || dx * dx + dy * dy + dz * dz > radius * radius
                || !worldObj.blockExists(x, y, z)) continue;
            Block block = worldObj.getBlock(x, y, z);
            if (block.isAir(worldObj, x, y, z) || block.getMaterial()
                .isLiquid() || !absorbableHardness(block.getBlockHardness(worldObj, x, y, z))) continue;
            // Vanilla destruction drops at probability 1 and invokes breakBlock, including container cleanup.
            if (worldObj.func_147480_a(x, y, z, true)) removed++;
        }
        blockCursor %= volume;
        // A depleted sphere is rescanned once per second, rather than every tick.
        if (blockSweepRemaining == 0) blockSweepCooldown = 20;
    }

    private double distanceSquared(Entity target) {
        double dx = Math.max(target.boundingBox.minX, Math.min(posX, target.boundingBox.maxX)) - posX;
        double dy = Math.max(target.boundingBox.minY, Math.min(posY, target.boundingBox.maxY)) - posY;
        double dz = Math.max(target.boundingBox.minZ, Math.min(posZ, target.boundingBox.maxZ)) - posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private void hurt(EntityLivingBase target, float amount, boolean magic) {
        EntityLivingBase shooter = null;
        if (owner != null) for (Object object : worldObj.loadedEntityList)
            if (object instanceof EntityLivingBase && owner.equals(((Entity) object).getUniqueID())) {
                shooter = (EntityLivingBase) object;
                break;
            }
        WeaponDamageSource source = new WeaponDamageSource("gtsr.singularity", this, shooter, shotEnchantments.looting);
        if (magic) source.setMagicDamage()
            .setDamageBypassesArmor();
        int previous = target.hurtResistantTime;
        target.hurtResistantTime = 0;
        try {
            target.attackEntityFrom(source, amount);
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
            hurt(target, explosionDamage(false), false);
            hurt(target, explosionDamage(true), true);
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
        shotEnchantments.write(n);
        n.setInteger("blockCursor", blockCursor);
        n.setBoolean("critical", critical);
        n.setInteger("age", age);
        if (owner != null) n.setString("owner", owner.toString());
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound n) {
        shotEnchantments = WeaponShotEnchantments.read(n);
        blockCursor = Math.max(0, n.getInteger("blockCursor"));
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
        shotEnchantments.write(b);
        b.writeBoolean(critical);
        b.writeInt(age);
    }

    @Override
    public void readSpawnData(ByteBuf b) {
        shotEnchantments = WeaponShotEnchantments.read(b);
        critical = b.readBoolean();
        age = b.readInt();
    }
}
