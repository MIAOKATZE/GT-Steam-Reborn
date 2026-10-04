package com.miaokatze.gtsr.client.encounter;

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
            FineRuneRenderer.unlit();
            double age = entity.ticksExisted + partial;
            try (GlScope core = new GlScope()) {
                GL11.glRotated(age * 7, 1, 1, 0);
                FineRuneRenderer.voxel(0, 0, 0, .16, c[0], c[1], c[2], .88F, true);
            }
            FineRuneRenderer.orbit(.28, age * 5, 4, c[0], c[1], c[2], .68F, .03);
        }
    }

    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }
}
