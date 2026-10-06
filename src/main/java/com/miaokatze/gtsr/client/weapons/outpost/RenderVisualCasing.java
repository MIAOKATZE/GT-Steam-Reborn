package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.encounter.GlScope;

public final class RenderVisualCasing extends Render {

    private static final ResourceLocation TEXTURE = new ResourceLocation("gtsr:textures/weapons/casing.png");
    private IModelCustom model;
    private boolean attempted;

    public void doRender(Entity e, double x, double y, double z, float yaw, float partial) {
        if (!attempted) {
            attempted = true;
            try {
                model = AdvancedModelLoader.loadModel(new ResourceLocation("gtsr:models/weapons/casing.obj"));
            } catch (RuntimeException missing) {
                return;
            }
        }
        if (model == null) return;
        try (GlScope scope = new GlScope()) {
            GL11.glTranslatef((float) x, (float) y, (float) z);
            float ry = e.prevRotationYaw + MathHelper.wrapAngleTo180_float(e.rotationYaw - e.prevRotationYaw) * partial;
            GL11.glRotatef(ry, 0, 1, 0);
            GL11.glScalef(.5F, .5F, .5F);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            net.minecraft.client.Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TEXTURE);
            model.renderAll();
        }
    }

    protected ResourceLocation getEntityTexture(Entity e) {
        return TEXTURE;
    }
}
