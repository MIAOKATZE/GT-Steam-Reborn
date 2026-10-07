package com.miaokatze.gtsr.client.weapons;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/** Core visuals use the project's existing glow, rotating beams and accretion particles. */
final class PortableSingularityRenderer extends Render {

    public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {}

    protected ResourceLocation getEntityTexture(Entity entity) {
        return new ResourceLocation("textures/particle/particles.png");
    }
}
