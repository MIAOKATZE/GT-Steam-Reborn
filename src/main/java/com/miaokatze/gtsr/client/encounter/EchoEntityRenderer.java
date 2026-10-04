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
            String clip = echo.getVisualClip();
            double tick = echo.getVisualTicks(partial);
            boolean industrial = echo.getKind() == EchoKind.DC02 || echo.getKind() == EchoKind.DC08;
            if (industrial && echo.getIndustrialBossStage() == 2 && echo.getHealth() > 0) {
                int skill = echo.getSkillId();
                if (skill > 0) {
                    boolean foundry = echo.getKind() == EchoKind.DC02;
                    double attack = echo.getSkillTicks() + partial;
                    clip = BossRigMotion.clip(foundry, skill);
                    tick = BossRigMotion.clock(foundry, skill, attack);
                    double[] pose = BossRigMotion.pose(foundry, skill, attack);
                    GL11.glRotated(pose[0], 0, 1, 0);
                    GL11.glRotated(pose[1], 1, 0, 0);
                    GL11.glTranslated(0, pose[2], 0);
                } else if (echo.getPhaseLockTicks() > 0) {
                    clip = echo.getKind() == EchoKind.DC02 ? "gravity_salvo" : "command_pulse";
                    tick = 60 - echo.getPhaseLockTicks() + partial;
                }
            }
            asset.render(clip, tick, echo.getDeathAlpha(partial) * CubeRuneParticle.spawnAlpha(echo, partial));
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
