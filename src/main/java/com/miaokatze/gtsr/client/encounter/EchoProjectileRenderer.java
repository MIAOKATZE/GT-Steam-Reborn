package com.miaokatze.gtsr.client.encounter;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoCombatProjectile;

/** Solid voxel core with translucent rotating satellites; no frame-owned particle emissions. */
public final class EchoProjectileRenderer extends Render {

    private static final float[][] COLORS = { { .95F, .48F, .16F }, { .7F, .3F, .95F }, { 1F, .7F, .2F },
        { .7F, .25F, .95F } };

    public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        if (((EchoCombatProjectile) entity).hasExploded()) return;
        float[] c = COLORS[Math.max(0, Math.min(3, ((EchoCombatProjectile) entity).getPalette()))];
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(x, y, z);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            double age = entity.ticksExisted + partial;
            GL11.glRotated(age * 9, 1, 1, 0);
            GL11.glColor4f(c[0], c[1], c[2], 1);
            CubeRuneParticle.box(0, 0, 0, .15);
            GL11.glDepthMask(false);
            GL11.glColor4f(c[0], c[1], c[2], .4F);
            CubeRuneParticle.box(0, 0, 0, .23);
            for (int i = 0; i < 4; i++) {
                double a = age * .1 + i * Math.PI / 2;
                CubeRuneParticle.box(Math.cos(a) * .33, Math.sin(a * 2) * .1, Math.sin(a) * .33, .055);
            }
        }
    }

    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }
}
