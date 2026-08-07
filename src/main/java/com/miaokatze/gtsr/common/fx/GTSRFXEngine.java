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

import com.miaokatze.gtsr.main.GTSteamReborn;

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

    /**
     * 安全绘制当前批次：批次被破坏（粒子自管后未恢复外层）时 draw 抛 IllegalStateException，
     * 捕获后重置批次状态并返回 false，防止渲染线程崩溃。
     */
    private static boolean safeDraw(Tessellator tessellator) {
        try {
            tessellator.draw();
            return true;
        } catch (IllegalStateException e) {
            try {
                tessellator.startDrawingQuads();
            } catch (IllegalStateException ignored) {
                // 已在绘制状态：直接重置
            }
            return false;
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
        // 纹理线性过滤：消除 32×32/16×16 低分辨率特效纹理放大后的硬像素边缘（"白色像素块"观感）
        // 纹理过滤参数是每个纹理对象独立存储的，需在绑定后设置；每帧幂等设置开销极小
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        Minecraft.getMinecraft().renderEngine.bindTexture(GTSRFXParticle.GLOW_TEXTURE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        Minecraft.getMinecraft().renderEngine.bindTexture(GTSRFXParticle.GLOW_SMALL_TEXTURE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        Minecraft.getMinecraft().renderEngine.bindTexture(GTSRFXParticle.GLOW_SOFT_TEXTURE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        float f1 = ActiveRenderInfo.rotationX;
        float f2 = ActiveRenderInfo.rotationZ;
        float f3 = ActiveRenderInfo.rotationYZ;
        float f4 = ActiveRenderInfo.rotationXY;
        float f5 = ActiveRenderInfo.rotationXZ;
        EntityFX.interpPosX = (float) (entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * (double) frame);
        EntityFX.interpPosY = (float) (entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * (double) frame);
        EntityFX.interpPosZ = (float) (entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * (double) frame);
        Tessellator tessellator = Tessellator.instance;
        long worldTime = entity.worldObj != null ? entity.worldObj.getTotalWorldTime() : 0L;
        boolean doSummary = worldTime % 200L == 0L;
        StringBuilder summary = doSummary ? new StringBuilder() : null;
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
                // 几何自检：NaN/Infinity/越界粒子跳过渲染并记日志（防白色巨块与花屏）
                if (!particle.sanityCheck()) {
                    GTSteamReborn.LOG.warn(
                        "[GTSRFX] 跳过异常粒子（几何自检失败）: {} @ ({},{},{})",
                        particle.getClass()
                            .getSimpleName(),
                        particle.posX,
                        particle.posY,
                        particle.posZ);
                    continue;
                }
                tessellator.startDrawingQuads();
                tessellator.setBrightness(particle.getBrightnessForRender(frame));
                try {
                    particle.renderParticle(tessellator, frame, f1, f5, f2, f3, f4);
                } catch (Throwable t) {
                    // 单粒子渲染异常兜底：清空可能残留的半截批次并重置状态，避免渲染线程崩溃
                    GTSteamReborn.LOG.warn(
                        "[GTSRFX] 粒子渲染异常: {} -> {}",
                        particle.getClass()
                            .getSimpleName(),
                        t.toString());
                    try {
                        tessellator.draw();
                    } catch (IllegalStateException ignored) {
                        // 不在绘制状态：无需清理
                    }
                    try {
                        tessellator.startDrawingQuads();
                    } catch (IllegalStateException ignored) {
                        // 已在绘制状态：直接重置
                    }
                    continue;
                }
                if (!safeDraw(tessellator)) {
                    // 批次契约破坏：粒子自管批次后未恢复外层（TC4 批次偷换模式被违反）
                    GTSteamReborn.LOG.warn(
                        "[GTSRFX] 批次契约破坏：{} 渲染后批次不可绘制",
                        particle.getClass()
                            .getSimpleName());
                }
            }
            int err = GL11.glGetError();
            if (err != GL11.GL_NO_ERROR) {
                GTSteamReborn.LOG
                    .warn("[GTSRFX] GL 错误 layer={} code=0x{}", Integer.valueOf(layer), Integer.toHexString(err));
            }
            GL11.glPopMatrix();
        }
        if (!this.glows.isEmpty()) {
            GL11.glPushMatrix();
            // 关键：beam/arc 自管批次结束后会 glDisable(GL_BLEND)，必须重新启用，
            // 否则辉光无混合直写 framebuffer（纹理 alpha 失效 → 纯白实心圆盘 = "白色光斑"）
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDisable(GL11.GL_CULL_FACE); // billboard quad 防背面剔除（与光束一致）
            GL11.glDisable(GL11.GL_ALPHA_TEST); // 防 alpha 测试残留裁剪渐变边缘（TC4/RenderSpecialItem 同款）
            Minecraft.getMinecraft().renderEngine.bindTexture(GTSRFXParticle.GLOW_SOFT_TEXTURE);
            for (GTSRGlowFX glow : this.glows) {
                // 距离裁剪：超过 64 格（4096 平方）不渲染，对齐电弧/光束可见性
                if (entity.getDistanceSq(glow.getX(), glow.getY(), glow.getZ()) > 4096.0D) {
                    continue;
                }
                if (!glow.sanityCheck()) {
                    GTSteamReborn.LOG.warn(
                        "[GTSRFX] 跳过异常辉光（几何自检失败）: r={} dark={}",
                        Float.valueOf(glow.getRadius()),
                        Float.valueOf(glow.getDarkScale()));
                    continue;
                }
                tessellator.startDrawingQuads();
                tessellator.setBrightness(0x00F000F0);
                glow.render(tessellator, frame);
                if (!safeDraw(tessellator)) {
                    GTSteamReborn.LOG.warn("[GTSRFX] 批次契约破坏：辉光渲染后批次不可绘制");
                }
            }
            GL11.glEnable(GL11.GL_ALPHA_TEST); // 恢复 alpha 测试
            GL11.glEnable(GL11.GL_CULL_FACE); // 恢复世界渲染的背面剔除状态
            GL11.glPopMatrix();
        }
        if (doSummary && summary != null) {
            summary.append("layers=[");
            for (int i = 0; i < 4; i++) {
                summary.append(i)
                    .append(':')
                    .append(this.particles[i].size())
                    .append(' ');
            }
            summary.append("] glows=")
                .append(this.glows.size());
            for (int layer = 0; layer < 4; layer++) {
                for (GTSRFXParticle particle : this.particles[layer]) {
                    if (particle instanceof GTSRBeamFX) {
                        GTSRBeamFX beam = (GTSRBeamFX) particle;
                        summary.append(" beam[len=")
                            .append(beam.getLength())
                            .append(",dark=")
                            .append(beam.getDarkScale())
                            .append(']');
                    } else if (particle instanceof GTSRArcFX) {
                        summary.append(" arc");
                    }
                }
            }
            for (GTSRGlowFX glow : this.glows) {
                summary.append(" glow[r=")
                    .append(glow.getRadius())
                    .append(",dark=")
                    .append(glow.getDarkScale())
                    .append(']');
            }
            // 吸积盘粒子走 vanilla 管道（effectRenderer），不在引擎层列表内，单独统计
            summary.append(" singFX=")
                .append(com.miaokatze.gtsr.common.blocks.GTSRSingularityFX.getActiveCount());
            summary.append(" player=")
                .append(entity.posX)
                .append(',')
                .append(entity.posY)
                .append(',')
                .append(entity.posZ);
            GTSteamReborn.LOG.info("[GTSRFX] 渲染摘要 {}", summary);
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glPopMatrix();
    }

    public static void spawnArc(World world, double sx, double sy, double sz, double ex, double ey, double ez,
        float width, int type, int duration, float multi, int speed, float darkScale, float colorR, float colorG,
        float colorB) {
        GTSRArcFX arc = GTSRArcFX
            .add(world, sx, sy, sz, ex, ey, ez, System.nanoTime(), width, type, duration, multi, speed);
        arc.setDarkScale(darkScale);
        arc.setColor(colorR, colorG, colorB);
    }

    public static void spawnParticle(GTSRFXParticle particle) {
        instance().addEffect(particle);
    }
}
