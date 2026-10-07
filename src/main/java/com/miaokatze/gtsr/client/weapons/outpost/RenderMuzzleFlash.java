package com.miaokatze.gtsr.client.weapons.outpost;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/** The last world pass owns the translucent flash; vanilla must not draw its fallback white bounding box. */
public final class RenderMuzzleFlash extends Render {

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {}

    @Override
    public void doRenderShadowAndFire(Entity entity, double x, double y, double z, float yaw, float partial) {}

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }
}
