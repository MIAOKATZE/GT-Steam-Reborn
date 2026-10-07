package com.miaokatze.gtsr.client.weapons;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.encounter.GlScope;
import com.miaokatze.gtsr.client.weapons.outpost.CurvedAimTrajectoryRenderer;
import com.miaokatze.gtsr.common.weapons.EntityWeaponProjectile;
import com.miaokatze.gtsr.common.weapons.WeaponKind;

/** GT-Outpost ProjectileObjRenderer velocity-facing nose (+Z), MiaoKatze, AGPL-3.0-or-later. */
final class PortableProjectileRenderer extends Render {

    public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityWeaponProjectile projectile = (EntityWeaponProjectile) entity;
        WeaponKind kind = projectile.kind();
        Vec3 visual = visualPoint(projectile, partial);
        double physicalX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partial;
        double physicalY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partial;
        double physicalZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partial;
        x += visual.xCoord - physicalX;
        y += visual.yCoord - physicalY;
        z += visual.zCoord - physicalZ;
        if (kind == WeaponKind.SINGULARITY) {
            try (GlScope scope = new GlScope()) {
                GL11.glTranslated(x, y, z);
                boolean critical = ((EntityWeaponProjectile) entity).critical();
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                GL11.glColor4f(critical ? .7F : 1, critical ? .2F : 1, 1, .9F);
                GL11.glRotatef(entity.ticksExisted * 11, 0, 1, 0);
                for (int ring = 0; ring < 3; ring++) {
                    GL11.glRotatef(60, 1, 0, 0);
                    GL11.glBegin(GL11.GL_LINE_LOOP);
                    for (int i = 0; i < 24; i++) {
                        double a = i * Math.PI * 2 / 24;
                        GL11.glVertex3d(Math.cos(a) * .14, 0, Math.sin(a) * .14);
                    }
                    GL11.glEnd();
                }
            }
            return;
        }
        String key = kind == WeaponKind.LM12 ? "bullet" : "bullet_he";
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(x, y, z);
            Vec3 before = visualPoint(projectile, Math.max(0, partial - .01F));
            Vec3 after = visualPoint(projectile, partial + .01F);
            double vx = after.xCoord - before.xCoord, vy = after.yCoord - before.yCoord,
                vz = after.zCoord - before.zCoord;
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            // Same as GTO's visual velocity channel: mathematical yaw and positive-up pitch.
            float heading = speed > 1e-6 ? (float) Math.toDegrees(Math.atan2(vx, vz)) : -entity.rotationYaw;
            float elevation = speed > 1e-6 ? (float) Math.toDegrees(Math.asin(vy / speed)) : -entity.rotationPitch;
            GL11.glRotatef(heading, 0, 1, 0);
            GL11.glRotatef(-elevation, 1, 0, 0);
            float scale = projectile.fragment() ? .12F : .5F;
            GL11.glScalef(scale, scale, scale);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glColor4f(1, 1, 1, 1);
            PortableWeaponRenderer.bind(key);
            PortableWeaponRenderer.part(key, "all");
        }
    }

    static Vec3 visualPoint(EntityWeaponProjectile projectile, float partial) {
        Vec3 origin = projectile.visualOrigin(), direction = projectile.visualDirection();
        if (origin == null || direction == null)
            return Vec3.createVectorHelper(projectile.posX, projectile.posY, projectile.posZ);
        return CurvedAimTrajectoryRenderer.point(
            origin,
            direction,
            projectile.kind(),
            Math.max(0, projectile.flightAge() - 1 + partial),
            projectile.fragment());
    }

    protected ResourceLocation getEntityTexture(Entity entity) {
        return new ResourceLocation("gtsr", "textures/weapons/bullet.png");
    }
}
