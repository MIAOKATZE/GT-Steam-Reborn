package com.miaokatze.gtsr.client.weapons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.encounter.GlScope;
import com.miaokatze.gtsr.common.blocks.GTSRSingularityFX;
import com.miaokatze.gtsr.common.fx.GTSRArcFX;
import com.miaokatze.gtsr.common.fx.GTSRBeamFX;
import com.miaokatze.gtsr.common.fx.GTSRGlowFX;
import com.miaokatze.gtsr.common.weapons.EntityWeaponProjectile;
import com.miaokatze.gtsr.common.weapons.EntityWeaponSingularity;
import com.miaokatze.gtsr.common.weapons.PortableWeapons;
import com.miaokatze.gtsr.common.weapons.Snapshot;
import com.miaokatze.gtsr.common.weapons.WeaponKind;
import com.miaokatze.gtsr.common.weapons.WeaponPose;

final class SingularityWeaponFx {

    private static final Map<Integer, Entity> CORES = new HashMap<>();
    private static Object lastWorld;
    private static final Map<Integer, List<EntityFX>> OWNED = new HashMap<>();

    static void stop(int entityId) {
        Entity core = CORES.remove(entityId);
        if (core == null && Minecraft.getMinecraft().theWorld != null) {
            Entity candidate = Minecraft.getMinecraft().theWorld.getEntityByID(entityId);
            if (candidate instanceof EntityWeaponSingularity) core = candidate;
        }
        if (core != null) core.setDead();
        List<EntityFX> effects = OWNED.remove(entityId);
        if (effects != null) for (EntityFX effect : effects) effect.setDead();
    }

    private static <T extends EntityFX> T own(EntityWeaponSingularity core, T effect) {
        List<EntityFX> effects = OWNED.computeIfAbsent(core.getEntityId(), id -> new ArrayList<>());
        effects.removeIf(p -> p.isDead);
        effects.add(effect);
        return effect;
    }

    static void tick(World world) {
        if (lastWorld != world) {
            for (Integer id : new ArrayList<>(CORES.keySet())) stop(id);
            OWNED.clear();
            lastWorld = world;
        }
        for (Integer id : new ArrayList<>(CORES.keySet())) {
            Entity core = CORES.get(id);
            if (core.isDead || ((EntityWeaponSingularity) core).life() >= 160 || !world.loadedEntityList.contains(core))
                stop(id);
        }
        int budget = 24;
        for (Object object : world.loadedEntityList.toArray()) {
            if (!(object instanceof EntityWeaponSingularity) || budget-- <= 0) continue;
            EntityWeaponSingularity core = (EntityWeaponSingularity) object;
            if (core.isDead) continue;
            float r = core.critical() ? .72F : 1F, g = core.critical() ? .3F : 1F;
            if (!CORES.containsKey(core.getEntityId())) {
                CORES.put(core.getEntityId(), core);
                int life = Math.max(1, 160 - core.life());
                own(
                    core,
                    GTSRGlowFX.spawn(world, core.posX, core.posY, core.posZ, core.critical() ? 2F : 1.2F, r, g, 1, life)
                        .setRenderDistanceUnlimited(true));
                for (int i = 0; i < 2; i++) own(
                    core,
                    GTSRBeamFX
                        .add(
                            world,
                            core.posX,
                            core.posY,
                            core.posZ,
                            core.critical() ? 3 : 1.8F,
                            .25F,
                            .04F,
                            .55F,
                            r,
                            g,
                            1,
                            1,
                            life)
                        .setRenderDistanceUnlimited(true));
            }
            if (world.getTotalWorldTime() % 3 == 0) {
                if (core.critical())
                    Minecraft.getMinecraft().effectRenderer.addEffect(own(core, new CriticalDisk(world, core)));
                else own(
                    core,
                    GTSRSingularityFX
                        .spawnOwnedDisk(world, core.posX, core.posY, core.posZ, 2.6, 1, 160, core.life(), .8F));
            }
            if (core.critical() && world.getTotalWorldTime() % 7 == 0) {
                double a = world.rand.nextDouble() * Math.PI * 2;
                own(
                    core,
                    GTSRArcFX
                        .add(
                            world,
                            core.posX,
                            core.posY,
                            core.posZ,
                            core.posX + Math.cos(a) * 3.6,
                            core.posY + world.rand.nextDouble() - .5,
                            core.posZ + Math.sin(a) * 3.6,
                            System.nanoTime(),
                            .06F,
                            7,
                            8,
                            .5F,
                            7)
                        .setRenderDistanceUnlimited(true)).setDarkScale(1);
            }
        }
        // The controller projectile is an orbiting singularity spark, without a white smoke trail.
        int trails = 16;
        for (Object object : world.loadedEntityList.toArray()) {
            if (!(object instanceof EntityWeaponProjectile) || trails-- <= 0) continue;
            EntityWeaponProjectile p = (EntityWeaponProjectile) object;
            if (p.kind() == WeaponKind.SINGULARITY && !p.isDead) Minecraft.getMinecraft().effectRenderer.addEffect(
                new net.minecraft.client.particle.EntityReddustFX(
                    world,
                    p.posX,
                    p.posY,
                    p.posZ,
                    p.critical() ? .7F : 1,
                    p.critical() ? .2F : 1,
                    1));
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
            radius = 1.9 + world.rand.nextDouble() * 2.5;
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

    /** Only four small orbiting cubes per active hand/muzzle, with no accumulating particle entities. */
    static void renderState(World world, float partial) {
        try (GlScope scope = new GlScope()) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glTranslated(
                -net.minecraft.client.renderer.entity.RenderManager.renderPosX,
                -net.minecraft.client.renderer.entity.RenderManager.renderPosY,
                -net.minecraft.client.renderer.entity.RenderManager.renderPosZ);
            for (Object object : world.playerEntities) {
                EntityPlayer p = (EntityPlayer) object;
                if (PortableWeapons.kind(p.getHeldItem()) != WeaponKind.SINGULARITY) continue;
                Minecraft mc = Minecraft.getMinecraft();
                if (p == mc.thePlayer && mc.gameSettings.thirdPersonView == 0) GL11.glDisable(GL11.GL_DEPTH_TEST);
                else GL11.glEnable(GL11.GL_DEPTH_TEST);
                Snapshot snapshot = PortableWeaponClient.snapshot(p);
                float shade = snapshot != null && snapshot.ammoType == 1 ? .55F : 1F;
                float reload = PortableWeaponClient.reloadProgress(p, partial);
                float remote = PortableWeaponClient.remoteProgress(p, partial);
                double time = world.getTotalWorldTime() + partial;
                if (reload > 0) stateCubes(PortableWeaponRenderer.reloadMuzzle(p, partial), time, shade, reload);
                if (remote > 0) stateCubes(
                    PortableWeaponRenderer.viewPoint(p, partial, WeaponPose.modelPoint(p, partial, -.75, .55, -.65)),
                    time,
                    shade,
                    remote);
            }
        }
    }

    private static void stateCubes(Vec3 center, double time, float shade, float progress) {
        double radius = .12 * (1 - progress) + .025;
        for (int cell = 0; cell < 4; cell++) {
            double a = time * .25 + cell * Math.PI / 2;
            double x = center.xCoord + Math.cos(a) * radius;
            double y = center.yCoord + Math.sin(a * 1.5) * radius * .5;
            double z = center.zCoord + Math.sin(a) * radius;
            GL11.glColor4f(shade, shade, shade, .25F);
            GL11.glBegin(GL11.GL_QUADS);
            for (int face = 0; face < 6; face++) {
                int axis = face / 2, sign = face % 2 == 0 ? -1 : 1;
                for (int corner = 0; corner < 4; corner++) {
                    double u = corner == 0 || corner == 3 ? -.025 : .025, v = corner < 2 ? -.025 : .025;
                    GL11.glVertex3d(
                        x + (axis == 0 ? sign * .025 : u),
                        y + (axis == 1 ? sign * .025 : axis == 0 ? u : v),
                        z + (axis == 2 ? sign * .025 : v));
                }
            }
            GL11.glEnd();
        }
    }
}
