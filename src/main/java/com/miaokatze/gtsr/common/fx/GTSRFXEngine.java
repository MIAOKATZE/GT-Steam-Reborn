package com.miaokatze.gtsr.common.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 独立粒子引擎：4 层渲染（0/2 additive、1/3 normal）+ 世界辉光特效列表。
 * 移植自 Thaumcraft 4.2.3.5 ParticleEngine，自包含实现。
 */
@SideOnly(Side.CLIENT)
public class GTSRFXEngine {

    private static final GTSRFXEngine INSTANCE = new GTSRFXEngine();
    private static final int MAX_PER_LAYER = 2000;

    private final List<GTSRFXParticle>[] particles = new List[4];
    private final List<GTSRGlowFX> glows = new ArrayList<GTSRGlowFX>();

    private GTSRFXEngine() {
        for (int i = 0; i < 4; i++) {
            this.particles[i] = new ArrayList<GTSRFXParticle>();
        }
    }

    public static GTSRFXEngine instance() {
        return INSTANCE;
    }

    public void addEffect(GTSRFXParticle particle) {
        int layer = particle.getFXLayer();
        if (layer < 0 || layer > 3) {
            layer = 2;
        }
        List<GTSRFXParticle> parts = this.particles[layer];
        if (parts.size() >= MAX_PER_LAYER) {
            parts.remove(0);
        }
        parts.add(particle);
    }

    public void addGlow(GTSRGlowFX glow) {
        this.glows.add(glow);
    }

    public int particleCount(int layer) {
        return layer >= 0 && layer < 4 ? this.particles[layer].size() : 0;
    }

    public int totalParticles() {
        int total = 0;
        for (List<GTSRFXParticle> parts : this.particles) {
            total += parts.size();
        }
        return total;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.side == Side.SERVER || event.phase != Phase.START) {
            return;
        }
        for (List<GTSRFXParticle> parts : this.particles) {
            Iterator<GTSRFXParticle> iterator = parts.iterator();
            while (iterator.hasNext()) {
                GTSRFXParticle particle = iterator.next();
                particle.onUpdate();
                if (particle.isDead) {
                    iterator.remove();
                }
            }
        }
        Iterator<GTSRGlowFX> glowIterator = this.glows.iterator();
        while (glowIterator.hasNext()) {
            GTSRGlowFX glow = glowIterator.next();
            glow.onUpdate();
            if (glow.isDead()) {
                glowIterator.remove();
            }
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (this.totalParticles() == 0 && this.glows.isEmpty()) {
            return;
        }
        EntityClientPlayerMP entity = Minecraft.getMinecraft().thePlayer;
        if (entity == null) {
            return;
        }
        float frame = event.partialTicks;
        GL11.glPushMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.003921569F);
        float f1 = ActiveRenderInfo.rotationX;
        float f2 = ActiveRenderInfo.rotationZ;
        float f3 = ActiveRenderInfo.rotationYZ;
        float f4 = ActiveRenderInfo.rotationXY;
        float f5 = ActiveRenderInfo.rotationXZ;
        EntityFX.interpPosX = (float) (entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * (double) frame);
        EntityFX.interpPosY = (float) (entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * (double) frame);
        EntityFX.interpPosZ = (float) (entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * (double) frame);
        Tessellator tessellator = Tessellator.instance;
        for (int layer = 0; layer < 4; layer++) {
            List<GTSRFXParticle> parts = this.particles[layer];
            if (parts.isEmpty()) {
                continue;
            }
            GL11.glPushMatrix();
            if (layer == 0 || layer == 2) {
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            } else {
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
            for (GTSRFXParticle particle : parts) {
                tessellator.startDrawingQuads();
                tessellator.setBrightness(particle.getBrightnessForRender(frame));
                particle.renderParticle(tessellator, frame, f1, f5, f2, f3, f4);
                tessellator.draw();
            }
            GL11.glPopMatrix();
        }
        if (!this.glows.isEmpty()) {
            GL11.glPushMatrix();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            Minecraft.getMinecraft().renderEngine.bindTexture(GTSRFXParticle.GLOW_TEXTURE);
            for (GTSRGlowFX glow : this.glows) {
                // 距离裁剪：超过 64 格（4096 平方）不渲染，对齐电弧/光束可见性
                if (entity.getDistanceSq(glow.getX(), glow.getY(), glow.getZ()) > 4096.0D) {
                    continue;
                }
                tessellator.startDrawingQuads();
                tessellator.setBrightness(0x00F000F0);
                glow.render(tessellator, frame);
                tessellator.draw();
            }
            GL11.glPopMatrix();
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glPopMatrix();
    }

    public static void spawnArc(World world, double sx, double sy, double sz, double ex, double ey, double ez,
        float width, int type, int duration, float multi, int speed, float darkScale) {
        GTSRArcFX.add(world, sx, sy, sz, ex, ey, ez, System.nanoTime(), width, type, duration, multi, speed)
            .setDarkScale(darkScale);
    }

    public static void spawnParticle(GTSRFXParticle particle) {
        instance().addEffect(particle);
    }
}
