package com.miaokatze.gtsr.client.weapons;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.GTSRSingularityFX;
import com.miaokatze.gtsr.common.fx.GTSRBeamFX;
import com.miaokatze.gtsr.common.fx.GTSRFXEngine;
import com.miaokatze.gtsr.common.fx.GTSRGlowFX;
import com.miaokatze.gtsr.common.weapons.EntityWeaponProjectile;
import com.miaokatze.gtsr.common.weapons.EntityWeaponSingularity;
import com.miaokatze.gtsr.common.weapons.PortableWeapons;
import com.miaokatze.gtsr.common.weapons.WeaponKind;
import com.miaokatze.gtsr.common.weapons.WeaponPose;

final class SingularityWeaponFx {

    private static final Map<Integer, Entity> CORES = new HashMap<>();
    private static Object lastWorld;

    static void tick(World world) {
        if (lastWorld != world) {
            CORES.clear();
            lastWorld = world;
        }
        CORES.entrySet()
            .removeIf(e -> e.getValue().isDead);
        int budget = 24;
        for (Object object : world.loadedEntityList.toArray()) {
            if (!(object instanceof EntityWeaponSingularity) || budget-- <= 0) continue;
            EntityWeaponSingularity core = (EntityWeaponSingularity) object;
            if (core.isDead) continue;
            float r = core.critical() ? .72F : 1F, g = core.critical() ? .3F : 1F;
            if (!CORES.containsKey(core.getEntityId())) {
                CORES.put(core.getEntityId(), core);
                int life = Math.max(1, 160 - core.life());
                GTSRGlowFX.spawn(world, core.posX, core.posY, core.posZ, core.critical() ? 1.3F : .8F, r, g, 1, life);
                for (int i = 0; i < 2; i++) GTSRBeamFX.add(
                    world,
                    core.posX,
                    core.posY,
                    core.posZ,
                    core.critical() ? 2 : 1.2F,
                    .25F,
                    .04F,
                    .55F,
                    r,
                    g,
                    1,
                    1,
                    life);
            }
            if (world.getTotalWorldTime() % 3 == 0) {
                if (core.critical()) Minecraft.getMinecraft().effectRenderer.addEffect(new CriticalDisk(world, core));
                else GTSRSingularityFX.spawnDisk(world, core.posX, core.posY, core.posZ, 1.8, 1, 160, core.life(), .8F);
            }
            if (core.critical() && world.getTotalWorldTime() % 7 == 0) {
                double a = world.rand.nextDouble() * Math.PI * 2;
                GTSRFXEngine.spawnArc(
                    world,
                    core.posX,
                    core.posY,
                    core.posZ,
                    core.posX + Math.cos(a) * 2.5,
                    core.posY + world.rand.nextDouble() - .5,
                    core.posZ + Math.sin(a) * 2.5,
                    .06F,
                    7,
                    8,
                    .5F,
                    7,
                    1);
            }
        }
        for (Object object : world.playerEntities) {
            EntityPlayer p = (EntityPlayer) object;
            if (PortableWeapons.kind(p.getHeldItem()) != WeaponKind.SINGULARITY) continue;
            float reload = PortableWeaponClient.reloadProgress(p, 1),
                remote = PortableWeaponClient.remoteProgress(p, 1);
            if (reload > 0) {
                gather(world, PortableWeaponRenderer.reloadMuzzle(p, 1), 3);
            }
            if (remote > 0)
                gather(world, PortableWeaponRenderer.viewPoint(p, 1, WeaponPose.modelPoint(p, 1, -.75, .55, -.65)), 5);
        }
        // The controller projectile is an orbiting singularity spark, without a white smoke trail.
        int trails = 16;
        for (Object object : world.loadedEntityList.toArray()) {
            if (!(object instanceof EntityWeaponProjectile) || trails-- <= 0) continue;
            EntityWeaponProjectile p = (EntityWeaponProjectile) object;
            if (p.kind() == WeaponKind.SINGULARITY && !p.isDead)
                world.spawnParticle("reddust", p.posX, p.posY, p.posZ, p.critical() ? .7 : 1, p.critical() ? .2 : 1, 1);
        }
    }

    /** The existing disk's shrinking orbit with the critical singularity's purple palette. */
    private static final class CriticalDisk extends EntityFX {

        private final EntityWeaponSingularity core;
        private double angle, radius;

        CriticalDisk(World world, EntityWeaponSingularity core) {
            super(world, core.posX, core.posY, core.posZ);
            this.core = core;
            angle = world.rand.nextDouble() * Math.PI * 2;
            radius = 1.3 + world.rand.nextDouble() * 1.7;
            particleMaxAge = Math.min(90, Math.max(1, 160 - core.life()));
            particleScale = .09F;
            setRBGColorF(.72F, .3F, 1);
            setParticleTextureIndex(0);
            position();
            prevPosX = posX;
            prevPosY = posY;
            prevPosZ = posZ;
        }

        public void onUpdate() {
            prevPosX = posX;
            prevPosY = posY;
            prevPosZ = posZ;
            if (core.isDead || particleAge++ >= particleMaxAge) {
                setDead();
                return;
            }
            angle += .08 + .06 / Math.max(.3, radius);
            radius *= radius < 1 ? .94 : .97;
            particleAlpha = Math.min(1, (particleMaxAge - particleAge) / 10F);
            position();
        }

        private void position() {
            setPosition(core.posX + Math.cos(angle) * radius, core.posY, core.posZ + Math.sin(angle) * radius);
        }
    }

    private static void gather(World world, Vec3 center, int count) {
        for (int i = 0; i < count; i++) {
            double a = world.rand.nextDouble() * Math.PI * 2, radius = .07 + world.rand.nextDouble() * .07;
            double dx = Math.cos(a) * radius, dy = (world.rand.nextDouble() - .5) * radius, dz = Math.sin(a) * radius;
            world.spawnParticle(
                "portal",
                center.xCoord + dx,
                center.yCoord + dy,
                center.zCoord + dz,
                -dx * .5,
                -dy * .5,
                -dz * .5);
        }
    }
}
