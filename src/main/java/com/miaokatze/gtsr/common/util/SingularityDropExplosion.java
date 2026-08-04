package com.miaokatze.gtsr.common.util;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S27PacketExplosion;
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
 * 爆炸后自身消失；等待期每 4 tick 冒一次白色云朵粒子。
 * <p>
 * 临界奇点：掉落 3 秒后爆炸，50% TNT 方块破坏（威力 2.0）+ 20 倍 TNT 实体伤害
 * （威力 80.0），爆炸后沿爆炸半径随机生成 0-16 个普通蒸汽纠缠奇点（各自带 4-10 秒
 * 随机爆炸倒计时），爆炸后自身消失；等待期每 2 tick 冒两颗黑色大烟雾粒子（更多、
 * 扩散更大），爆炸动画半径 8.0（普通 4.0 的两倍）。
 * <p>
 * 等待期粒子在客户端由 EntityItem.age 驱动；爆炸动画通过复刻 patched
 * WorldServer.newExplosion 的 S27PacketExplosion 广播在客户端渲染 TNT 爆炸粒子动画。
 */
public final class SingularityDropExplosion {

    /** 普通奇点爆炸视觉粒子半径 */
    private static final float NORMAL_VISUAL_SIZE = 4.0F;
    /** 普通奇点实体伤害威力：TNT 为 4.0，8.0 = 两倍 TNT 伤害 */
    private static final float NORMAL_ENTITY_DAMAGE_SIZE = 8.0F;
    /** 普通奇点爆炸倒计时：5 秒 */
    private static final int NORMAL_DELAY_TICKS = 100;
    /** 普通奇点等待期白色粒子间隔（tick） */
    private static final int NORMAL_PARTICLE_INTERVAL = 4;
    /** 临界爆炸产出的普通奇点爆炸倒计时范围：4-10 秒（随机） */
    private static final int SPAWN_DELAY_MIN_TICKS = 80;
    private static final int SPAWN_DELAY_MAX_TICKS = 200;

    /** 临界奇点方块破坏威力：TNT 为 4.0，2.0 = 50% TNT 方块破坏 */
    private static final float CRITICAL_BLOCK_SIZE = 2.0F;
    /** 临界奇点实体伤害威力：80.0 = 20 倍 TNT 伤害 */
    private static final float CRITICAL_ENTITY_DAMAGE_SIZE = 80.0F;
    /** 临界奇点爆炸视觉粒子半径（普通 4.0 的两倍，动画更大） */
    private static final float CRITICAL_VISUAL_SIZE = 8.0F;
    /** 临界奇点爆炸倒计时：3 秒 */
    private static final int CRITICAL_DELAY_TICKS = 60;
    /** 临界奇点等待期黑色粒子间隔（tick，比普通更密集） */
    private static final int CRITICAL_PARTICLE_INTERVAL = 2;

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
        World world = entityItem.worldObj;
        if (world.isRemote) {
            // 客户端：只负责等待期粒子（服务端负责倒计时与爆炸），以 EntityItem.age 计时
            if (entityItem.age % (critical ? CRITICAL_PARTICLE_INTERVAL : NORMAL_PARTICLE_INTERVAL) == 0) {
                spawnWaitParticles(world, entityItem, critical);
            }
            return;
        }
        NBTTagCompound tag = entityItem.getEntityData();
        if (tag.getBoolean("gtsrExploded")) {
            return;
        }
        int ticks = tag.getInteger("gtsrDropTicks") + 1;
        tag.setInteger("gtsrDropTicks", ticks);
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

    /** 等待期粒子：普通奇点白色云朵粒子；临界奇点黑色大烟雾粒子（每 2 tick 两颗、扩散更大） */
    private static void spawnWaitParticles(World world, EntityItem item, boolean critical) {
        if (critical) {
            for (int i = 0; i < 2; ++i) {
                world.spawnParticle(
                    "largesmoke",
                    item.posX + (world.rand.nextFloat() - 0.5F) * 1.2D,
                    item.posY + world.rand.nextFloat() * 0.8D,
                    item.posZ + (world.rand.nextFloat() - 0.5F) * 1.2D,
                    (world.rand.nextFloat() - 0.5F) * 0.8D,
                    world.rand.nextFloat() * 0.4D,
                    (world.rand.nextFloat() - 0.5F) * 0.8D);
            }
        } else {
            world.spawnParticle(
                "cloud",
                item.posX + (world.rand.nextFloat() - 0.5F) * 0.8D,
                item.posY + world.rand.nextFloat() * 0.5D,
                item.posZ + (world.rand.nextFloat() - 0.5F) * 0.8D,
                (world.rand.nextFloat() - 0.5F) * 0.4D,
                world.rand.nextFloat() * 0.2D,
                (world.rand.nextFloat() - 0.5F) * 0.4D);
        }
    }

    /** 普通奇点爆炸：不改动任何方块；通过 S27PacketExplosion 向客户端广播 TNT 爆炸粒子动画 */
    public static void explode(World world, EntityItem source) {
        if (world.isRemote) {
            return;
        }
        double x = source.posX;
        double y = source.posY;
        double z = source.posZ;

        Explosion exp = new Explosion(world, null, x, y, z, NORMAL_VISUAL_SIZE);
        exp.doExplosionA();
        exp.isSmoking = false;
        exp.doExplosionB(false);
        broadcastExplosion(world, exp);
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

        Explosion blockExp = new Explosion(world, null, x, y, z, CRITICAL_BLOCK_SIZE);
        blockExp.doExplosionA();
        destroyBlocks(world, blockExp);

        Explosion visualExp = new Explosion(world, null, x, y, z, CRITICAL_VISUAL_SIZE);
        visualExp.doExplosionA();
        visualExp.isSmoking = false;
        visualExp.doExplosionB(false);
        broadcastExplosion(world, visualExp);

        damageEntities(world, source, visualExp, x, y, z, CRITICAL_ENTITY_DAMAGE_SIZE);
        spawnSingularityDrops(world, x, y, z);
    }

    /**
     * 向 64 格内玩家广播 S27PacketExplosion。
     * <p>
     * GTNH patched MC 的 Explosion.doExplosionB 不再发包（v1.10.1 因此看不到爆炸动画），
     * 发包逻辑在 WorldServer.newExplosion 覆写里；这里复刻该逻辑让客户端渲染
     * TNT 爆炸粒子动画（hugeexplosion + 逐位 explode/smoke + 音效 + 玩家击退）。
     */
    private static void broadcastExplosion(World world, Explosion exp) {
        for (EntityPlayer player : world.playerEntities) {
            if (player.getDistanceSq(exp.explosionX, exp.explosionY, exp.explosionZ) < 4096.0D) {
                ((EntityPlayerMP) player).playerNetServerHandler.sendPacket(
                    new S27PacketExplosion(
                        exp.explosionX,
                        exp.explosionY,
                        exp.explosionZ,
                        exp.explosionSize,
                        exp.affectedBlockPositions,
                        exp.func_77277_b()
                            .get(player)));
            }
        }
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
