package com.miaokatze.gtsr.client.encounter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;

/** Dynamic code routing for the append-only echo roster; world-unit rigs need no model scale. */
public final class EchoEntityRenderer extends Render {

    private final RigRepository repository;

    public EchoEntityRenderer(RigRepository repository) {
        this.repository = repository;
        shadowSize = .5F;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        EntityOldEcho echo = (EntityOldEcho) entity;
        RigAsset asset = repository.get(echo.getKind().code);
        if (asset == null) return;
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(180 - (echo.getEncounterState() == EntityOldEcho.IDLE ? echo.getHomeYaw() : yaw), 0, 1, 0);
            GL11.glEnable(GL11.GL_NORMALIZE);
            int light = entity.getBrightnessForRender(partial);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 65535, light >>> 16);
            asset.render(echo.getVisualClip(), echo.getVisualTicks(partial), echo.getDeathAlpha(partial));
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (echo.getKind() == EchoKind.DO02 && echo.getCustomNameTag()
            .endsWith("99*")) {
            try (GlScope scope = new GlScope()) {
                func_147906_a(entity, echo.getCommandSenderName(), x, y + echo.height, z, 256);
            }
        } else if (!echo.getKind()
            .isRitual() && mc.objectMouseOver != null && mc.objectMouseOver.entityHit == entity) {
                try (GlScope scope = new GlScope()) {
                    func_147906_a(entity, echo.getCommandSenderName(), x, y, z, 32);
                }
            }
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        String code = ((EntityOldEcho) entity).getKind().code;
        return new ResourceLocation("gtsr", "liminal/" + code + "/textures/model/" + code + ".png");
    }
}
