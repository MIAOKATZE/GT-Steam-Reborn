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
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // 叠加发光（亮色相加、alpha 控强度）
        GL11.glDepthMask(false);
        Tessellator tess = Tessellator.instance;
        drawFlashBatch(tess, mc, false, pt); // 批 1：核心小亮心（星芒）
        drawFlashBatch(tess, mc, true, pt); // 批 2：光环大面片柔光
    }

    private static void drawFlashBatch(Tessellator tess, Minecraft mc, boolean glow, float pt) {
        boolean open = false;
        for (int i = 0, n = FLASHES.size(); i < n; i++) {
            EntityMuzzleFlash f = FLASHES.get(i);
            float ageF = f.ageTicks + pt;
            float alpha = GatlingFxProfile.clamp01(1.0F - ageF / Math.max(1, f.lifeTicks));
            double ex = interpPos(f.prevPosX, f.posX, pt);
            double ey = interpPos(f.prevPosY, f.posY, pt);
            double ez = interpPos(f.prevPosZ, f.posZ, pt);
            if (!open) {
                tess.startDrawingQuads();
                open = true;
            }
            double size = (glow ? f.haloScale : GatlingFxProfile.FLASH_CORE_SCALE) * f.scaleJitter * .045;
            for (int cell = 0; cell < 3; cell++) {
                double q = cell - 1;
                addCube(
                    tess,
                    ex + q * size * 2.2,
                    ey + (cell % 2) * size * 1.8,
                    ez + q * size * 1.2,
                    size,
                    1F,
                    glow ? .62F : .94F,
                    glow ? .18F : .75F,
                    alpha * (glow ? .20F : .65F));
            }
        }
        if (open) {
            tess.draw();
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
