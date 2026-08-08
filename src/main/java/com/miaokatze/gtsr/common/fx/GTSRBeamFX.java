package com.miaokatze.gtsr.common.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 可复用光片组件（原探照灯式竖光片粒子）：1 片光片从中心点水平向外延伸，绕偏竖直随机旋转轴持续扫动。
 * 沿长度方向 4 段 alpha 递减（中心亮、末端淡），外加一片柔和丁达尔副片。
 * 自管批次：renderParticle 开头 tess.draw() 冲刷外层批次，结尾恢复外层批次。
 *
 * 11 参工厂参数：length 光片长度、width 片宽、rotPerTick 每 tick 旋转弧度、tiltBias 旋转轴竖直偏置
 * （0~1，1=纯竖直、0=纯水平）、colorR/G/B 片色、alphaScale 整体透明度、maxAge 寿命上限。
 * 旋转轴为归一化单位向量：tiltY = tiltBias + (1-tiltBias)×rand（竖直分量），水平分量按单位圆补全（自动单位化）。
 * 近场衰减：玩家距光片中心 3 格内 alpha 线性淡出，防止 additive 白板贴脸填满全屏。
 * 示例（奇点用法）：GTSRBeamFX.add(world, x, y, z, length, width, 0.02618F, 0.55F, 0.85F, 0.92F, 1.0F, 1.0F, 10000);
 */
@SideOnly(Side.CLIENT)
public class GTSRBeamFX extends GTSRFXParticle {

    /** 沿长度方向的逐段 alpha（中心亮 → 末端淡） */
    private static final float[] SEG_ALPHA = { 0.45F, 0.32F, 0.2F, 0.1F };
    private static final int SEG_COUNT = 4;

    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private float length;
    private final float width;
    /** 每 tick 旋转增量：默认 0.02618 rad ≈ 1.5°/tick = 30°/s */
    private float rotPerTick = 0.02618F;
    /** 旋转轴（归一化单位向量，偏竖直随机）：旋转角 = baseYaw + yaw + rot 绕该轴旋转 */
    private final float tiltX;
    private final float tiltY;
    private final float tiltZ;
    /** 初始方位（0~2π 全随机），yaw 仅承担小幅微漂移 */
    private final float baseYaw;
    private float yaw;
    private float pitch;
    private float rot;
    private float darkScale = 1.0F;
    /** 近场衰减系数：玩家贴近光片中心时线性淡出（renderParticle 每帧更新） */
    private float nearFade = 1.0F;
    private float colorR;
    private float colorG;
    private float colorB;
    private final float alphaScale;
    private final int maxAge;

    private GTSRBeamFX(World world, double x, double y, double z, float length, float width, float rotPerTick,
        float tiltBias, float colorR, float colorG, float colorB, float alphaScale, int maxAge, long seed) {
        super(world, x, y, z);
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        this.length = length;
        this.width = width;
        this.rotPerTick = rotPerTick;
        this.colorR = colorR;
        this.colorG = colorG;
        this.colorB = colorB;
        this.alphaScale = alphaScale;
        this.maxAge = maxAge;
        this.rand.setSeed(seed);
        this.baseYaw = this.rand.nextFloat() * 2.0F * (float) Math.PI;
        this.yaw = (this.rand.nextFloat() - 0.5F) * 0.6F;
        this.pitch = (this.rand.nextFloat() - 0.5F) * 0.6F;
        this.rot = this.rand.nextFloat() * 2.0F * (float) Math.PI;
        // 旋转轴：竖直分量由 tiltBias 偏置随机，水平分量按单位圆补全（结果自动单位化）
        this.tiltY = tiltBias + (1.0F - tiltBias) * this.rand.nextFloat();
        float h = (float) Math.sqrt(Math.max(0.0F, 1.0F - this.tiltY * this.tiltY));
        float a = this.rand.nextFloat() * 2.0F * (float) Math.PI;
        this.tiltX = h * (float) Math.cos((double) a);
        this.tiltZ = h * (float) Math.sin((double) a);
    }

    /** 简配入口：默认 rotPerTick 0.02618、tiltBias 0.55、色 0.85/0.92/1.0、alphaScale 1.0、maxAge 10000 */
    public static GTSRBeamFX add(World world, double x, double y, double z, float length, float width) {
        return add(world, x, y, z, length, width, 0.02618F, 0.55F, 0.85F, 0.92F, 1.0F, 1.0F, 10000);
    }

    /**
     * 全参工厂：rotPerTick 每 tick 旋转弧度、tiltBias 旋转轴竖直偏置（0~1，1=纯竖直、0=纯水平）、
     * colorR/G/B 片色、alphaScale 整体透明度、maxAge 寿命上限。
     */
    public static GTSRBeamFX add(World world, double x, double y, double z, float length, float width, float rotPerTick,
        float tiltBias, float colorR, float colorG, float colorB, float alphaScale, int maxAge) {
        GTSRBeamFX fx = new GTSRBeamFX(
            world,
            x,
            y,
            z,
            length,
            width,
            rotPerTick,
            tiltBias,
            colorR,
            colorG,
            colorB,
            alphaScale,
            maxAge,
            System.nanoTime());
        GTSRFXEngine.instance()
            .addEffect(fx);
        return fx;
    }

    /** 每 tick 更新长度与暗化系数（消散时随 activeFactor 收缩、变暗） */
    public void updateParams(float length, float darkScale) {
        this.length = length;
        this.darkScale = darkScale;
    }

    /** 颜色实时同步（奇点 NBT 同步到达/变化后立即生效，不再固定创建时颜色） */
    public void updateColor(float colorR, float colorG, float colorB) {
        this.colorR = colorR;
        this.colorG = colorG;
        this.colorB = colorB;
    }

    /** 当前长度（供引擎渲染摘要日志） */
    public float getLength() {
        return this.length;
    }

    /** 当前暗化系数（供引擎渲染摘要日志） */
    public float getDarkScale() {
        return this.darkScale;
    }

    @Override
    public boolean sanityCheck() {
        // 光片几何自检：长度/暗化系数/旋转轴有限且在合理范围（长度上限 100 格）
        return Float.isFinite(this.length) && this.length >= 0.0F
            && this.length <= 100.0F
            && Float.isFinite(this.darkScale)
            && this.darkScale >= 0.0F
            && this.darkScale <= 2.0F
            && Float.isFinite(this.tiltX)
            && Float.isFinite(this.tiltY)
            && Float.isFinite(this.tiltZ)
            && super.sanityCheck();
    }

    @Override
    public void onUpdate() {
        this.particleAge++;
        this.rot += this.rotPerTick;
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
        int visibleDistance = 10000; // getDistanceSq 是距离平方：10000 = 100 格
        if (!Minecraft.getMinecraft().gameSettings.fancyGraphics) {
            visibleDistance = 2500; // 2500 = 50 格
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
        // 绕偏竖直随机轴旋转（旋转角 = 初始方位 + 微漂移 + 持续旋转）
        GL11.glRotatef(
            (float) Math.toDegrees((double) (this.baseYaw + this.yaw + this.rot)),
            this.tiltX,
            this.tiltY,
            this.tiltZ);
        // 近场衰减：玩家距光片中心 3 格内 alpha 线性淡出，防止 additive 白板贴脸填满全屏
        float dist = (float) renderentity.getDistance(this.centerX, this.centerY, this.centerZ);
        this.nearFade = Math.min(1.0F, dist / 6.0F);
        float halfHeight = Math.min(3.0F, this.length * 0.5F);
        this.renderSheet(tess, this.width, 1.0F, halfHeight); // 核心光片
        this.renderSheet(tess, this.width * 2.4F, 0.25F, halfHeight); // 丁达尔副片
        // 完整恢复 layer 2 层状态：重开混合 + normal 混合 + 重绑 items 层纹理，
        // 防止 additive/blendFunc/纹理残留污染同层后续粒子（vanilla item 粒子等）的共享批次
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.locationItemsTexture);
        GL11.glPopMatrix();
        tess.startDrawingQuads(); // 恢复外层批次
    }

    /**
     * 画一片锥形光片：XZ 平面内 x 从 0 到 length、y 从 -h/2 到 +h/2 的 quad（z=0），
     * 用 sheetWidth 作为根部片厚在 z 方向对称偏移成两面；厚度沿长度线性收窄（末端 20%），呈光锥状。
     * 沿长度分 4 段逐段 alpha 递减。
     */
    private void renderSheet(Tessellator tess, float sheetWidth, float alphaScale, float halfHeight) {
        tess.startDrawingQuads();
        tess.setBrightness(0x00F000F0);
        float segLen = this.length / (float) SEG_COUNT;
        float halfThick0 = sheetWidth * 0.5F;
        for (int i = 0; i < SEG_COUNT; i++) {
            float x0 = (float) i * segLen;
            float x1 = x0 + segLen;
            // 锥形收窄系数：根部 1.0 → 末端 0.2（用段索引避免 length=0 除零）
            float t0 = 1.0F - 0.8F * ((float) i / (float) SEG_COUNT);
            float t1 = 1.0F - 0.8F * ((float) (i + 1) / (float) SEG_COUNT);
            float h0 = halfThick0 * t0;
            float h1 = halfThick0 * t1;
            float a = SEG_ALPHA[i] * alphaScale * this.darkScale * this.nearFade;
            if (a <= 0.0F) {
                continue;
            }
            tess.setColorRGBA_F(this.colorR, this.colorG, this.colorB, a * this.alphaScale);
            // 取纹理右半（u 0.5→1.0）：纹理中心最亮贴中心端，向末端单调变淡
            float u0 = 0.5F + (float) i / (float) SEG_COUNT;
            float u1 = 0.5F + (float) (i + 1) / (float) SEG_COUNT;
            // z = -h 面（x0 端厚 h0、x1 端厚 h1）
            tess.addVertexWithUV((double) x0, (double) (-halfHeight), (double) (-h0), (double) u0, 1.0D);
            tess.addVertexWithUV((double) x1, (double) (-halfHeight), (double) (-h1), (double) u1, 1.0D);
            tess.addVertexWithUV((double) x1, (double) halfHeight, (double) (-h1), (double) u1, 0.0D);
            tess.addVertexWithUV((double) x0, (double) halfHeight, (double) (-h0), (double) u0, 0.0D);
            // z = +h 面
            tess.addVertexWithUV((double) x0, (double) (-halfHeight), (double) h0, (double) u0, 1.0D);
            tess.addVertexWithUV((double) x1, (double) (-halfHeight), (double) h1, (double) u1, 1.0D);
            tess.addVertexWithUV((double) x1, (double) halfHeight, (double) h1, (double) u1, 0.0D);
            tess.addVertexWithUV((double) x0, (double) halfHeight, (double) h0, (double) u0, 0.0D);
        }
        tess.draw();
    }
}
