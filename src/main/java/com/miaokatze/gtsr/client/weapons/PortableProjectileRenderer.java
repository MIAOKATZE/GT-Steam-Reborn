package com.miaokatze.gtsr.client.weapons;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.weapons.EntityWeaponProjectile;
import com.miaokatze.gtsr.common.weapons.WeaponKind;

/** Render only: entity trajectories and damage remain server owned. */
final class PortableProjectileRenderer extends Render {

    public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        WeaponKind kind = ((EntityWeaponProjectile) entity).kind();
        String key = kind == WeaponKind.LM12 ? "bullet" : "bullet_he";
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partial, 0, 1, 0);
            GL11.glRotatef(
                entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partial,
                1,
                0,
                0);
            GL11.glScalef(.12F, .12F, .12F);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glColor4f(1, 1, 1, 1);
            PortableWeaponRenderer.bind(key);
            PortableWeaponRenderer.part(key, "all");
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(mode);
            GL11.glPopAttrib();
        }
    }

    protected ResourceLocation getEntityTexture(Entity entity) {
        return new ResourceLocation("gtsr", "textures/weapons/bullet.png");
    }
}
