package com.miaokatze.gtsr.common.util;

import java.util.List;

import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

/**
 * 蒸汽纠缠奇点掉落物爆炸逻辑（普通/临界共用）。
 * <p>
 * 掉落物落地后 5 秒（100 tick）自动爆炸：不破坏方块、不伤害其他掉落物、无任何产物，
 * 实体伤害为 TNT（size 4）的两倍（size 8），爆炸后奇点掉落物自身消失。
 */
public final class SingularityDropExplosion {

    /** 爆炸视觉半径（仅用于粒子散布，不影响方块） */
    private static final float VISUAL_EXPLOSION_SIZE = 4.0F;
    /** 实体伤害威力：TNT 为 4.0，此处 8.0 = 两倍 TNT 伤害 */
    private static final float ENTITY_EXPLOSION_SIZE = 8.0F;
    /** 掉落物爆炸倒计时：5 秒 */
    private static final int EXPLOSION_DELAY_TICKS = 100;

    private SingularityDropExplosion() {}

    /**
     * 掉落物逐 tick 更新：倒计时 5 秒，期间每 4 tick 播一次传送门粒子，
     * 到点爆炸并使掉落物自身消失。由两个奇点物品的 onEntityItemUpdate 委托调用。
     */
    public static void updateDroppedSingularity(EntityItem entityItem) {
        if (entityItem.worldObj.isRemote) {
            return;
        }
        NBTTagCompound tag = entityItem.getEntityData();
        if (tag.getBoolean("gtsrExploded")) {
            return;
        }
        int ticks = tag.getInteger("gtsrDropTicks") + 1;
        tag.setInteger("gtsrDropTicks", ticks);
        World world = entityItem.worldObj;
        if (ticks % 4 == 0) {
            world.spawnParticle(
                "portal",
                entityItem.posX + (world.rand.nextFloat() - 0.5F) * 0.8D,
                entityItem.posY + world.rand.nextFloat() * 0.5D,
                entityItem.posZ + (world.rand.nextFloat() - 0.5F) * 0.8D,
                (world.rand.nextFloat() - 0.5F) * 0.4D,
                world.rand.nextFloat() * 0.2D,
                (world.rand.nextFloat() - 0.5F) * 0.4D);
        }
        if (ticks >= EXPLOSION_DELAY_TICKS) {
            tag.setBoolean("gtsrExploded", true);
            explode(world, entityItem);
            entityItem.setDead();
        }
    }

    public static void explode(World world, EntityItem source) {
        if (world.isRemote) {
            return;
        }
        double x = source.posX;
        double y = source.posY;
        double z = source.posZ;

        // 只计算受影响位置用于粒子散布，不改动任何方块
        Explosion exp = new Explosion(world, null, x, y, z, VISUAL_EXPLOSION_SIZE);
        exp.doExplosionA();
        spawnExplosionParticles(world, exp);
        playExplosionSound(world, x, y, z);
        damageEntities(world, source, exp, x, y, z);
    }

    private static void spawnExplosionParticles(World world, Explosion exp) {
        world.spawnParticle("hugeexplosion", exp.explosionX, exp.explosionY, exp.explosionZ, 1.0D, 0.0D, 0.0D);
        for (Object obj : exp.affectedBlockPositions) {
            ChunkPosition pos = (ChunkPosition) obj;
            double px = pos.chunkPosX + 0.5D;
            double py = pos.chunkPosY + 0.5D;
            double pz = pos.chunkPosZ + 0.5D;
            double dx = px - exp.explosionX;
            double dy = py - exp.explosionY;
            double dz = pz - exp.explosionZ;
            double len = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
            if (len != 0.0D) {
                dx /= len;
                dy /= len;
                dz /= len;
                world.spawnParticle("explode", px, py, pz, dx, dy, dz);
                world.spawnParticle("smoke", px, py, pz, dx, dy, dz);
            }
        }
    }

    private static void playExplosionSound(World world, double x, double y, double z) {
        world.playSoundEffect(
            x,
            y,
            z,
            "random.explode",
            4.0F,
            (1.0F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.2F) * 0.7F);
    }

    /** 实体伤害（跳过掉落物与其他奇点本体）：公式与爆炸威力 8.0 匹配 = 两倍 TNT */
    private static void damageEntities(World world, Entity source, Explosion exp, double x, double y, double z) {
        float range = ENTITY_EXPLOSION_SIZE * 2.0F;
        List<Entity> entities = world.getEntitiesWithinAABB(
            Entity.class,
            AxisAlignedBB.getBoundingBox(
                x - range - 1.0D,
                y - range - 1.0D,
                z - range - 1.0D,
                x + range + 1.0D,
                y + range + 1.0D,
                z + range + 1.0D));
        Vec3 center = Vec3.createVectorHelper(x, y, z);
        DamageSource damageSource = DamageSource.setExplosionSource(exp);
        for (Entity entity : entities) {
            if (entity instanceof EntityItem || entity == source) {
                continue;
            }
            double d9 = entity.getDistance(x, y, z) / (double) range;
            if (d9 > 1.0D) {
                continue;
            }
            double dx = entity.posX - x;
            double dy = entity.posY + (double) entity.getEyeHeight() - y;
            double dz = entity.posZ - z;
            double len = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
            if (len == 0.0D) {
                continue;
            }
            dx /= len;
            dy /= len;
            dz /= len;
            double d12 = (1.0D - d9) * world.getBlockDensity(center, entity.boundingBox);
            entity.attackEntityFrom(
                damageSource,
                (float) ((int) ((d12 * d12 + d12) / 2.0D * 8.0D * (double) ENTITY_EXPLOSION_SIZE + 1.0D)));
            double knockback = d12;
            if (entity instanceof EntityLivingBase) {
                knockback = EnchantmentProtection.func_92092_a(entity, d12);
            }
            entity.motionX += dx * knockback;
            entity.motionY += dy * knockback;
            entity.motionZ += dz * knockback;
        }
    }
}
