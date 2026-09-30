package com.miaokatze.gtsr.client.encounter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntityResidualOathguard;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing;

/** The imported rig uses world units; dormant throne is exactly spawn frame zero. */
public final class EncounterEntityRenderer extends Render {

    private final RigRepository repository;
    private final String code;

    public EncounterEntityRenderer(RigRepository repository, String code) {
        this.repository = repository;
        this.code = code;
        shadowSize = code.equals("dc-10") ? 2.0F : 0.4F;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
        RigAsset asset = repository.get(code);
        if (asset == null) return;
        String clip = "idle";
        double time = entity.ticksExisted + partial;
        if (entity instanceof EntitySilentKing) {
            EntitySilentKing king = (EntitySilentKing) entity;
            int state = king.getEncounterState();
            time = king.getVisualPhaseTicks() + partial;
            if (state == 0) {
                clip = "spawn";
                time = 0;
            } else if (state == 1) {
                clip = "spawn";
                time *= 104.0 / EntitySilentKing.AWAKENING_TICKS;
            } else if (state == 4) {
                clip = "death";
                time = king.deathTime + partial;
            } else if (state == 2) {
                int skill = king.getSkillId();
                if (skill != 0) {
                    clip = skill == 2 || skill == 4 ? "sound_vacuum" : "silent_edict";
                    time = king.getSkillTicks() + partial;
                }

            }
        } else if (entity instanceof EntityResidualOathguard) {
            EntityResidualOathguard guard = (EntityResidualOathguard) entity;
            if (guard.getHealth() <= 0) {
                clip = "death";
                time = Math.min(48, guard.deathTime + partial);
            } else if (guard.getEncounterState() == 1 && guard.getVisualPhaseTicks() < 25) {
                clip = "attack";
                time = (guard.getVisualPhaseTicks() + partial) * 1.2;
            } else
                if ((guard.getEncounterState() == 1 || guard.getEncounterState() == 2) && guard.limbSwingAmount > 0.02F)
                    clip = "walk";
        }
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(x, y, z);
            float facing = entity instanceof EntitySilentKing ? ((EntitySilentKing) entity).getHomeYaw()
                : entity instanceof EntityResidualOathguard
                    && ((EntityResidualOathguard) entity).getEncounterState() == 0
                        ? ((EntityResidualOathguard) entity).getHomeYaw()
                        : yaw;
            GL11.glRotatef(180 - facing, 0, 1, 0);
            GL11.glEnable(GL11.GL_NORMALIZE);
            int light = entity.getBrightnessForRender(partial);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 65535, light >>> 16);
            asset.render(clip, time);
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit == entity) {
            try (GlScope scope = new GlScope()) {
                func_147906_a(entity, entity.getCommandSenderName(), x, y, z, 32);
            }
        }
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return new ResourceLocation("gtsr", "liminal/" + code + "/textures/model/" + code + ".png");
    }
}
