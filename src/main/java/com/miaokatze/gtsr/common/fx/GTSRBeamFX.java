package com.miaokatze.gtsr.common.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 探照灯式竖光片粒子：1 片竖直光片从奇点中心水平向外延伸，绕竖轴持续扫动（30°/s）。
 * 沿长度方向 4 段 alpha 递减（中心亮、末端淡），外加一片柔和丁达尔副片。
 * 自管批次：renderParticle 开头 tess.draw() 冲刷外层批次，结尾恢复外层批次。
 */
@SideOnly(Side.CLIENT)
public class GTSRBeamFX extends GTSRFXParticle {

    /** 每 tick 旋转增量：0.02618 rad ≈ 1.5°/tick = 30°/s */
    private static final float ROT_PER_TICK = 0.02618F;
    /** 沿长度方向的逐段 alpha（中心亮 → 末端淡） */
    private static final float[] SEG_ALPHA = { 0.9F, 0.65F, 0.4F, 0.2F };
    private static final int SEG_COUNT = 4;

    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private float length;
    private final float width;
    private float yaw;
    private float pitch;
    private float rot;
    private float darkScale = 1.0F;
    private final int maxAge = 10000;

    private GTSRBeamFX(World world, double x, double y, double z, float length, float width, long seed) {
        super(world, x, y, z);
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        this.length = length;
        this.width = width;
        this.rand.setSeed(seed);
        this.yaw = (this.rand.nextFloat() - 0.5F) * 0.6F;
        this.pitch = (this.rand.nextFloat() - 0.5F) * 0.6F;
        this.rot = this.rand.nextFloat() * 2.0F * (float) Math.PI;
    }

    public static GTSRBeamFX add(World world, double x, double y, double z, float length, float width) {
        GTSRBeamFX fx = new GTSRBeamFX(world, x, y, z, length, width, System.nanoTime());
        GTSRFXEngine.instance()
            .addEffect(fx);
        return fx;
    }

    /** 每 tick 更新长度与暗化系数（消散时随 activeFactor 收缩、变暗） */
    public void updateParams(float length, float darkScale) {
        this.length = length;
        this.darkScale = darkScale;
    }

    @Override
    public void onUpdate() {
        this.particleAge++;
        this.rot += ROT_PER_TICK;
        // 少量 yaw/pitch 微漂移增加生机（clamp ±0.3）
        this.yaw += (this.rand.nextFloat() - 0.5F) * 0.02F;
        this.pitch += (this.rand.nextFloat() - 0.5F) * 0.02F;
        if (this.yaw > 0.3F) {
            this.yaw = 0.3F;
        }
        if (this.yaw < -0.3F) {
            this.yaw = -0.3F;
        }
        if (this.pitch > 0.3F) {
            this.pitch = 0.3F;
        }
        if (this.pitch < -0.3F) {
            this.pitch = -0.3F;
        }
    }

    @Override
    public void renderParticle(Tessellator tess, float partialframe, float cosyaw, float cospitch, float sinyaw,
        float sinsinpitch, float cossinpitch) {
        EntityClientPlayerMP renderentity = Minecraft.getMinecraft().thePlayer;
        int visibleDistance = 100;
        if (!Minecraft.getMinecraft().gameSettings.fancyGraphics) {
            visibleDistance = 50;
        }
        if (renderentity == null
            || renderentity.getDistanceSq(this.posX, this.posY, this.posZ) > (double) visibleDistance) {
            return;
        }
        tess.draw(); // 刷新外层批次，本粒子自管批次（TC4 FXBeamWand 兼容模式）
        GL11.glPushMatrix();
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        Minecraft.getMinecraft().renderEngine.bindTexture(GTSRFXParticle.GLOW_TEXTURE);
        GL11.glTranslated(
            this.centerX - (double) interpPosX,
            this.centerY - (double) interpPosY,
            this.centerZ - (double) interpPosZ);
        GL11.glRotatef((float) Math.toDegrees((double) (this.yaw + this.rot)), 0.0F, 1.0F, 0.0F);
        float halfHeight = Math.min(3.0F, this.length * 0.5F);
        this.renderSheet(tess, this.width, 1.0F, halfHeight); // 核心光片
        this.renderSheet(tess, this.width * 2.4F, 0.25F, halfHeight); // 丁达尔副片
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
        tess.startDrawingQuads(); // 恢复外层批次
    }

    /**
     * 画一片竖直光片：XZ 平面内 x 从 0 到 length、y 从 -h/2 到 +h/2 的 quad（z=0），
     * 用 sheetWidth 作为片厚在 z 方向对称偏移成两面，构成有厚度的光片（片宽 = sheetWidth）。
     * 沿长度分 4 段逐段 alpha 递减。
     */
    private void renderSheet(Tessellator tess, float sheetWidth, float alphaScale, float halfHeight) {
        tess.startDrawingQuads();
        tess.setBrightness(0x00F000F0);
        float segLen = this.length / (float) SEG_COUNT;
        float halfThick = sheetWidth * 0.5F;
        for (int i = 0; i < SEG_COUNT; i++) {
            float x0 = (float) i * segLen;
            float x1 = x0 + segLen;
            float a = SEG_ALPHA[i] * alphaScale * this.darkScale;
            if (a <= 0.0F) {
                continue;
            }
            tess.setColorRGBA_F(0.85F, 0.92F, 1.0F, a);
            // 取纹理右半（u 0.5→1.0）：纹理中心最亮贴中心端，向末端单调变淡
            float u0 = 0.5F + (float) i / (float) SEG_COUNT;
            float u1 = 0.5F + (float) (i + 1) / (float) SEG_COUNT;
            // z = -halfThick 面
            tess.addVertexWithUV((double) x0, (double) (-halfHeight), (double) (-halfThick), (double) u0, 1.0D);
            tess.addVertexWithUV((double) x1, (double) (-halfHeight), (double) (-halfThick), (double) u1, 1.0D);
            tess.addVertexWithUV((double) x1, (double) halfHeight, (double) (-halfThick), (double) u1, 0.0D);
            tess.addVertexWithUV((double) x0, (double) halfHeight, (double) (-halfThick), (double) u0, 0.0D);
            // z = +halfThick 面
            tess.addVertexWithUV((double) x0, (double) (-halfHeight), (double) halfThick, (double) u0, 1.0D);
            tess.addVertexWithUV((double) x1, (double) (-halfHeight), (double) halfThick, (double) u1, 1.0D);
            tess.addVertexWithUV((double) x1, (double) halfHeight, (double) halfThick, (double) u1, 0.0D);
            tess.addVertexWithUV((double) x0, (double) halfHeight, (double) halfThick, (double) u0, 0.0D);
        }
        tess.draw();
    }
}
