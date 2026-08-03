package com.miaokatze.gtsr.common.util;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

public final class SingularityDropExplosion {

    private static final float BLOCK_EXPLOSION_SIZE = 2.0F;
    private static final float ENTITY_EXPLOSION_SIZE = 80.0F;

    private SingularityDropExplosion() {}

    public static void explode(World world, EntityItem source) {
        if (world.isRemote) {
            return;
        }
        double x = source.posX;
        double y = source.posY;
        double z = source.posZ;

        Explosion exp = new Explosion(world, null, x, y, z, BLOCK_EXPLOSION_SIZE);
        exp.doExplosionA();
        destroyBlocks(world, exp);
        spawnExplosionParticles(world, exp);
        playExplosionSound(world, x, y, z);
        damageEntities(world, source, exp, x, y, z);
        spawnSingularityDrops(world, source, x, y, z);
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
                (float) ((int) ((d12 * d12 + d12) / 2.0D * 8.0D * (double) range + 1.0D)));
            double knockback = d12;
            if (entity instanceof EntityLivingBase) {
                knockback = EnchantmentProtection.func_92092_a(entity, d12);
            }
            entity.motionX += dx * knockback;
            entity.motionY += dy * knockback;
            entity.motionZ += dz * knockback;
        }
    }

    private static void spawnSingularityDrops(World world, EntityItem source, double x, double y, double z) {
        int count = world.rand.nextInt(17);
        for (int i = 0; i < count; ++i) {
            double angle = world.rand.nextDouble() * Math.PI * 2.0D;
            double dist = 1.0D + world.rand.nextDouble() * 7.0D;
            EntityItem drop = new EntityItem(
                world,
                x + Math.cos(angle) * dist,
                y + world.rand.nextDouble() * 2.0D,
                z + Math.sin(angle) * dist,
                source.getEntityItem()
                    .copy());
            drop.motionX = (world.rand.nextFloat() - 0.5F) * 0.4D;
            drop.motionY = world.rand.nextFloat() * 0.3D;
            drop.motionZ = (world.rand.nextFloat() - 0.5F) * 0.4D;
            world.spawnEntityInWorld(drop);
        }
    }
}
