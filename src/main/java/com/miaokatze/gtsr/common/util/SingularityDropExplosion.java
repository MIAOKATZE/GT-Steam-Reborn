package com.miaokatze.gtsr.common.util;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
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

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

/**
 * 蒸汽纠缠奇点掉落物爆炸逻辑。
 * <p>
 * 普通奇点：掉落 5 秒后爆炸，两倍 TNT 实体伤害（威力 8.0），不破坏方块，无产物，
 * 爆炸后自身消失。
 * <p>
 * 临界奇点：掉落 3 秒后爆炸，50% TNT 方块破坏（威力 2.0）+ 20 倍 TNT 实体伤害
 * （威力 80.0），爆炸后沿爆炸半径随机生成 0-16 个普通蒸汽纠缠奇点（各自带 4-10 秒
 * 随机爆炸倒计时），爆炸后自身消失。两者等待期间均每 4 tick 散发一次传送门粒子。
 */
public final class SingularityDropExplosion {

    /** 普通奇点爆炸视觉粒子半径 */
    private static final float NORMAL_VISUAL_SIZE = 4.0F;
    /** 普通奇点实体伤害威力：TNT 为 4.0，8.0 = 两倍 TNT 伤害 */
    private static final float NORMAL_ENTITY_DAMAGE_SIZE = 8.0F;
    /** 普通奇点爆炸倒计时：5 秒 */
    private static final int NORMAL_DELAY_TICKS = 100;
    /** 临界爆炸产出的普通奇点爆炸倒计时范围：4-10 秒（随机） */
    private static final int SPAWN_DELAY_MIN_TICKS = 80;
    private static final int SPAWN_DELAY_MAX_TICKS = 200;

    /** 临界奇点方块破坏威力：TNT 为 4.0，2.0 = 50% TNT 方块破坏 */
    private static final float CRITICAL_BLOCK_SIZE = 2.0F;
    /** 临界奇点实体伤害威力：80.0 = 20 倍 TNT 伤害 */
    private static final float CRITICAL_ENTITY_DAMAGE_SIZE = 80.0F;
    /** 临界奇点爆炸倒计时：3 秒 */
    private static final int CRITICAL_DELAY_TICKS = 60;

    private SingularityDropExplosion() {}

    /** 普通蒸汽纠缠奇点掉落物逐 tick 更新（由 onEntityItemUpdate 委托） */
    public static void updateNormalSingularity(EntityItem entityItem) {
        // 临界爆炸产出的奇点带有随机 4-10 秒倒计时（NBT gtsrDropDelay），普通掉落默认 5 秒
        NBTTagCompound tag = entityItem.getEntityData();
        int delay = tag.getInteger("gtsrDropDelay");
        updateDroppedSingularity(entityItem, delay > 0 ? delay : NORMAL_DELAY_TICKS, false);
    }

    /** 临界蒸汽纠缠奇点掉落物逐 tick 更新（由 onEntityItemUpdate 委托） */
    public static void updateCriticalSingularity(EntityItem entityItem) {
        updateDroppedSingularity(entityItem, CRITICAL_DELAY_TICKS, true);
    }

    private static void updateDroppedSingularity(EntityItem entityItem, int delayTicks, boolean critical) {
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
        if (ticks >= delayTicks) {
            tag.setBoolean("gtsrExploded", true);
            if (critical) {
                explodeCritical(world, entityItem);
            } else {
                explode(world, entityItem);
            }
            entityItem.setDead();
        }
    }

    /** 普通奇点爆炸：只计算受影响位置用于粒子散布，不改动任何方块 */
    public static void explode(World world, EntityItem source) {
        if (world.isRemote) {
            return;
        }
        double x = source.posX;
        double y = source.posY;
        double z = source.posZ;

        Explosion exp = new Explosion(world, null, x, y, z, NORMAL_VISUAL_SIZE);
        exp.doExplosionA();
        spawnExplosionParticles(world, exp);
        playExplosionSound(world, x, y, z);
        damageEntities(world, source, exp, x, y, z, NORMAL_ENTITY_DAMAGE_SIZE);
    }

    /** 临界奇点爆炸：50% TNT 方块破坏 + 20 倍 TNT 实体伤害 + 0-16 个普通奇点产物 */
    public static void explodeCritical(World world, EntityItem source) {
        if (world.isRemote) {
            return;
        }
        double x = source.posX;
        double y = source.posY;
        double z = source.posZ;

        Explosion exp = new Explosion(world, null, x, y, z, CRITICAL_BLOCK_SIZE);
        exp.doExplosionA();
        destroyBlocks(world, exp);
        spawnExplosionParticles(world, exp);
        playExplosionSound(world, x, y, z);
        damageEntities(world, source, exp, x, y, z, CRITICAL_ENTITY_DAMAGE_SIZE);
        spawnSingularityDrops(world, x, y, z);
    }

    private static void destroyBlocks(World world, Explosion exp) {
        for (int i = exp.affectedBlockPositions.size() - 1; i >= 0; --i) {
            ChunkPosition pos = (ChunkPosition) exp.affectedBlockPositions.get(i);
            Block block = world.getBlock(pos.chunkPosX, pos.chunkPosY, pos.chunkPosZ);
            if (block.getMaterial() != Material.air) {
                if (block.canDropFromExplosion(exp)) {
                    block.dropBlockAsItemWithChance(
                        world,
                        pos.chunkPosX,
                        pos.chunkPosY,
                        pos.chunkPosZ,
                        block.getDamageValue(world, pos.chunkPosX, pos.chunkPosY, pos.chunkPosZ),
                        0.7F,
                        0);
                }
                world.setBlockToAir(pos.chunkPosX, pos.chunkPosY, pos.chunkPosZ);
            }
        }
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

    /** 实体伤害（跳过掉落物与其他奇点本体）：伤害公式与 vanilla TNT 相同，威力由 size 决定 */
    private static void damageEntities(World world, Entity source, Explosion exp, double x, double y, double z,
        float size) {
        float range = size * 2.0F;
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
                (float) ((int) ((d12 * d12 + d12) / 2.0D * 8.0D * (double) size + 1.0D)));
            double knockback = d12;
            if (entity instanceof EntityLivingBase) {
                knockback = EnchantmentProtection.func_92092_a(entity, d12);
            }
            entity.motionX += dx * knockback;
            entity.motionY += dy * knockback;
            entity.motionZ += dz * knockback;
        }
    }

    /** 沿爆炸半径（1-4 格）随机生成 0-16 个普通蒸汽纠缠奇点（各带 4-10 秒随机爆炸倒计时） */
    private static void spawnSingularityDrops(World world, double x, double y, double z) {
        int count = world.rand.nextInt(17);
        for (int i = 0; i < count; ++i) {
            double angle = world.rand.nextDouble() * Math.PI * 2.0D;
            double dist = 1.0D + world.rand.nextDouble() * 3.0D;
            EntityItem drop = new EntityItem(
                world,
                x + Math.cos(angle) * dist,
                y + world.rand.nextDouble() * 2.0D,
                z + Math.sin(angle) * dist,
                GTSRItemList.SteamEntangledSingularity.get(1));
            drop.getEntityData()
                .setInteger(
                    "gtsrDropDelay",
                    SPAWN_DELAY_MIN_TICKS + world.rand.nextInt(SPAWN_DELAY_MAX_TICKS - SPAWN_DELAY_MIN_TICKS + 1));
            drop.motionX = (world.rand.nextFloat() - 0.5F) * 0.4D;
            drop.motionY = world.rand.nextFloat() * 0.3D;
            drop.motionZ = (world.rand.nextFloat() - 0.5F) * 0.4D;
            world.spawnEntityInWorld(drop);
        }
    }
}
