package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.encounter.GlScope;

public final class FxEntityWorldPass {

    private static final List<SmokePuffEntity> SMOKES = new ArrayList<>();
    private static final List<EntityMuzzleFlash> FLASHES = new ArrayList<>();

    public static void render(World world, float partial) {
        SMOKES.clear();
        FLASHES.clear();
        for (Object o : world.loadedEntityList) {
            if (o instanceof SmokePuffEntity && !((Entity) o).isDead) SMOKES.add((SmokePuffEntity) o);
            else if (o instanceof EntityMuzzleFlash && !((Entity) o).isDead) FLASHES.add((EntityMuzzleFlash) o);
        }
        try (GlScope scope = new GlScope()) {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            double x = net.minecraft.client.renderer.entity.RenderManager.renderPosX,
                y = net.minecraft.client.renderer.entity.RenderManager.renderPosY,
                z = net.minecraft.client.renderer.entity.RenderManager.renderPosZ;
            GL11.glTranslated(-x, -y, -z);
            drawSmokeLayer(Minecraft.getMinecraft(), partial, x, y, z);
            drawFlashLayer(Minecraft.getMinecraft(), partial);
        }
    }

    private static void drawSmokeLayer(Minecraft mc, float pt, double camX, double camY, double camZ) {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA); // 烟≠发光，alpha 混合
        GL11.glDepthMask(false);
        Tessellator tess = Tessellator.instance;
        drawSmokeBatch(tess, mc, false, pt, camX, camY, camZ); // 批 A：通用柔边烟
        drawSmokeBatch(tess, mc, true, pt, camX, camY, camZ); // 批 B：白烟（EffectPalette 路径真值）
    }

    private static void drawSmokeBatch(Tessellator tess, Minecraft mc, boolean white, float pt, double camX,
        double camY, double camZ) {
        boolean open = false;
        for (int i = 0, n = SMOKES.size(); i < n; i++) {
            SmokePuffEntity p = SMOKES.get(i);
            if (p.isWhitePuff() != white) {
                continue;
            }
            // 寿命相位（含 partialTicks 平滑）：尺寸线性放大、alpha 线性→0；末期膨胀（swell）
            // 仅圆斑模式且实例激活时替换线性插值（原 doRender 同判据同式）
            float phase = GatlingFxProfile.clamp01((p.ageTicks + pt) / p.lifeTicks);
            boolean swellActive = p.streakLength <= 0.0F && p.getEndSwellFactor() > 1.0F;
            float scale = swellActive ? GatlingFxProfile.puffScaleWithSwell(phase, p.scale0, p.scale1)
                : GatlingFxProfile.lerp(p.scale0, p.scale1, phase);
            float alpha = p.alpha0 * (1.0F - phase * phase * (3F - 2F * phase));
            if (alpha <= 0.0F) {
                continue; // 已衰减尽：跳过（原 doRender 早退同判据）
            }
            double ex = interpPos(p.prevPosX, p.posX, pt);
            double ey = interpPos(p.prevPosY, p.posY, pt);
            double ez = interpPos(p.prevPosZ, p.posZ, pt);
            if (!open) {
                tess.startDrawingQuads();
                open = true;
            }
            // Round33: a puff is an exposed cube cluster, including engine and muzzle smoke.
            int count = p.streakLength > 0
                ? Math.min(12, Math.max(2, (int) Math.ceil(p.streakLength / Math.max(.04, scale))))
                : 3;
            for (int cell = 0; cell < count; cell++) {
                double q = p.streakLength > 0 ? (cell / (double) (count - 1) - .5) * p.streakLength
                    : (cell - 1) * scale * .40;
                double cx = ex + (p.streakLength > 0 ? p.axisX * q : q),
                    cy = ey + (p.streakLength > 0 ? p.axisY * q : (cell % 2) * scale * .25),
                    cz = ez + (p.streakLength > 0 ? p.axisZ * q : (cell - 1) * scale * .25);
                addCube(tess, cx, cy, cz, scale * .19, p.r, p.g, p.b, alpha);
            }
        }
        if (open) {
            tess.draw();
        }
    }

    private static void drawFlashLayer(Minecraft mc, float pt) {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        // A cube contributed several additive faces at once, washing its yellow core out to white.
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        for (EntityMuzzleFlash f : FLASHES) {
            float phase = GatlingFxProfile.clamp01((f.ageTicks + pt) / Math.max(1, f.lifeTicks));
            boolean singularity = f.getEntityData()
                .getBoolean("gtsr.singularityFlash");
            boolean critical = singularity && f.getEntityData()
                .getBoolean("gtsr.criticalFlash");
            float red = critical ? .72F : 1, green = critical ? .3F : singularity ? 1 : .74F;
            float blue = singularity ? 1 : .06F;
            float alpha = singularity ? .4F : .36F * (1 - phase);
            if (alpha <= 0) continue;
            GL11.glPushMatrix();
            GL11.glTranslated(
                interpPos(f.prevPosX, f.posX, pt),
                interpPos(f.prevPosY, f.posY, pt),
                interpPos(f.prevPosZ, f.posZ, pt));
            GL11.glRotatef(-net.minecraft.client.renderer.entity.RenderManager.instance.playerViewY, 0, 1, 0);
            GL11.glRotatef(net.minecraft.client.renderer.entity.RenderManager.instance.playerViewX, 1, 0, 0);
            double radius = .055 * f.scaleJitter;
            for (int cell = 0; cell < 3; cell++) {
                double x = (cell - 1) * radius * .9, y = (cell % 2) * radius * .5;
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                GL11.glColor4f(red, green, blue, alpha);
                GL11.glVertex3d(x, y, 0);
                GL11.glColor4f(red, green, blue, singularity ? .4F : 0);
                for (int edge = 0; edge <= 16; edge++) {
                    double a = edge * Math.PI / 8;
                    GL11.glVertex3d(x + Math.cos(a) * radius, y + Math.sin(a) * radius, 0);
                }
                GL11.glEnd();
            }
            GL11.glPopMatrix();
        }
    }

    private static void addCube(Tessellator tess, double x, double y, double z, double half, float red, float green,
        float blue, float alpha) {
        for (int face = 0; face < 6; face++) {
            int axis = face / 2, sign = face % 2 == 0 ? -1 : 1;
            float shade = axis == 1 ? (sign > 0 ? 1F : .72F) : axis == 0 ? .86F : .94F;
            tess.setColorRGBA_F(red * shade, green * shade, blue * shade, alpha);
            for (int corner = 0; corner < 4; corner++) {
                double u = corner == 0 || corner == 3 ? -half : half, v = corner < 2 ? -half : half;
                tess.addVertex(
                    x + (axis == 0 ? sign * half : u),
                    y + (axis == 1 ? sign * half : axis == 0 ? u : v),
                    z + (axis == 2 ? sign * half : v));
            }
        }
    }

    private static double interpPos(double prev, double cur, float pt) {
        return prev + (cur - prev) * pt;
    }
}
