package com.miaokatze.gtsr.client.weapons;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.encounter.GlScope;
import com.miaokatze.gtsr.common.weapons.EntityWeaponProjectile;
import com.miaokatze.gtsr.common.weapons.WeaponKind;

/** GT-Outpost ProjectileObjRenderer velocity-facing nose (+Z), MiaoKatze, AGPL-3.0-or-later. */
final class PortableProjectileRenderer extends Render {

    public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        WeaponKind kind = ((EntityWeaponProjectile) entity).kind();
        String key = kind == WeaponKind.LM12 ? "bullet" : "bullet_he";
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(x, y, z);
            double speed = Math.sqrt(
                entity.motionX * entity.motionX + entity.motionY * entity.motionY + entity.motionZ * entity.motionZ);
            // Same as GTO's visual velocity channel: mathematical yaw and positive-up pitch.
            float heading = speed > 1e-6 ? (float) Math.toDegrees(Math.atan2(entity.motionX, entity.motionZ))
                : -entity.rotationYaw;
            float elevation = speed > 1e-6 ? (float) Math.toDegrees(Math.asin(entity.motionY / speed))
                : -entity.rotationPitch;
            GL11.glRotatef(heading, 0, 1, 0);
            GL11.glRotatef(-elevation, 1, 0, 0);
            GL11.glScalef(.5F, .5F, .5F);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glColor4f(1, 1, 1, 1);
            PortableWeaponRenderer.bind(key);
            PortableWeaponRenderer.part(key, "all");
        }
    }

    protected ResourceLocation getEntityTexture(Entity entity) {
        return new ResourceLocation("gtsr", "textures/weapons/bullet.png");
    }
}
