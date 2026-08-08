package com.miaokatze.gtsr.common.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 世界辉光特效：多层同心 additive billboard 圆，极微弱波动 + 寿命渐出。
 * 参考 Thaumcraft 4.2.3.5 TileNodeRenderer 辉光 quad 与 RenderSpecialItem 三角锥 glow，自包含实现。
 * 走 vanilla 粒子管道（EffectRenderer）：渲染时机 = 方块 pass 0 后、pass 1 前，深度测试开启——
 * 普通方块遮挡、染色玻璃/水等半透明面不遮挡（与吸积盘粒子同层级）。
 */
@SideOnly(Side.CLIENT)
public class GTSRGlowFX extends EntityFX {

    /** layer 0 层纹理（vanilla EffectRenderer 层首绑定）：自管批次结束后恢复用 */
    private static final ResourceLocation PARTICLES_TEXTURE = new ResourceLocation("textures/particle/particles.png");

    private final World world;
    private final double x;
    private final double y;
    private final double z;
    private float radius;
    private float colorR;
    private float colorG;
    private float colorB;
    private final float baseAlpha;
    private final int maxAge;
    private int age;
    private float darkScale = 1.0F;
    private float shrinkPerTick = 0.0F;
    /** 颤动（慢速随机波动）：当前强度与目标强度，每 30~59 tick 换一次目标 */
    private float breathe = 1.0F;
    private float breatheTarget = 1.0F;
    private int breatheChangeIn;

    public GTSRGlowFX(World world, double x, double y, double z, float radius, float colorR, float colorG, float colorB,
        int durationTicks) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.colorR = colorR;
        this.colorG = colorG;
        this.colorB = colorB;
        this.baseAlpha = 0.55F; // TC4 节点式多层光晕强度（0.9 过曝成白块、0.32 太淡）
        this.maxAge = Math.max(1, durationTicks);
        this.particleMaxAge = this.maxAge; // 粒子管道按 EntityFX 生命周期管理
    }

    public static GTSRGlowFX spawn(World world, double x, double y, double z, float radius, float colorR, float colorG,
        float colorB, int durationTicks) {
        GTSRGlowFX glow = new GTSRGlowFX(world, x, y, z, radius, colorR, colorG, colorB, durationTicks);
        Minecraft.getMinecraft().effectRenderer.addEffect(glow);
        return glow;
    }

    /** 每 tick 更新半径与暗化系数（消散时随 activeFactor 收缩、变暗） */
    public void updateParams(float radius, float darkScale) {
        this.radius = radius;
        this.darkScale = darkScale;
    }

    /** 颜色实时同步（奇点 NBT 同步到达/变化后立即生效，不再固定创建时颜色） */
    public void updateColor(float colorR, float colorG, float colorB) {
        this.colorR = colorR;
        this.colorG = colorG;
        this.colorB = colorB;
    }

    /** 设置每 tick 半径收缩量（>0 时开启收缩，用于消散过渡辉光） */
    public void setShrinkPerTick(float shrinkPerTick) {
        this.shrinkPerTick = shrinkPerTick;
    }

    /** 渲染前几何自检：半径/暗化系数有限且在合理范围（半径上限 100 格） */
    public boolean sanityCheck() {
        return Float.isFinite(this.radius) && this.radius >= 0.0F
            && this.radius <= 100.0F
            && Float.isFinite(this.darkScale)
            && this.darkScale >= 0.0F
            && this.darkScale <= 2.0F;
    }

    /** 当前半径（供调试） */
    public float getRadius() {
        return this.radius;
    }

    /** 当前暗化系数（供调试） */
    public float getDarkScale() {
        return this.darkScale;
    }

    @Override
    public void onUpdate() {
        this.age++;
        if (this.shrinkPerTick > 0.0F) {
            this.radius = Math.max(0.0F, this.radius - this.shrinkPerTick);
        }
        // 颤动：每 30~59 tick 换目标（0.9~1.1，强弱差别 20%），每 tick 向目标逼近 8% → 慢速、烈度低
        this.breatheChangeIn--;
        if (this.breatheChangeIn <= 0) {
            this.breatheChangeIn = 30 + this.world.rand.nextInt(30);
            this.breatheTarget = 0.9F + this.world.rand.nextFloat() * 0.2F;
        }
        this.breathe += (this.breatheTarget - this.breathe) * 0.08F;
        if (this.age >= this.maxAge) {
            this.setDead(); // 同步 EntityFX.isDead，粒子管道据此移除
        }
    }

    public boolean isDead() {
        return super.isDead;
    }

    @Override
    public void renderParticle(Tessellator tess, float p, float rx, float rz, float ry, float rxz, float ryz) {
        // 距离裁剪：超过 64 格（4096 平方）不渲染（对齐电弧/光束可见性；粒子管道无渲染侧裁剪）
        if (Minecraft.getMinecraft().thePlayer != null
            && Minecraft.getMinecraft().thePlayer.getDistanceSq(this.x, this.y, this.z) > 4096.0D) {
            return;
        }
        if (!sanityCheck()) {
            return;
        }
        // 颤动强度字段（onUpdate 缓慢随机波动），随寿命渐出
        float fade = Math.max(0.0F, 1.0F - (float) this.age / (float) this.maxAge);
        // 近场衰减：贴脸（<3 格）时淡出，避免 additive 辉光近距全屏泛白
        float dist = 100.0F;
        if (Minecraft.getMinecraft().thePlayer != null) {
            dist = (float) Minecraft.getMinecraft().thePlayer.getDistance(this.x, this.y, this.z);
        }
        float nearFade = Math.min(1.0F, dist / 3.0F);
        float alpha = this.baseAlpha * this.breathe * fade * this.darkScale * nearFade;
        // 完全自管批次：先冲刷共享批次，独立 draw；结束后完整恢复 layer 0 层状态。
        // 粒子管道每层粒子共享一个批次、层末一次 draw，绘制时状态 = 层内最后一次设置的状态——
        // 若不恢复纹理/混合，glow_soft 会污染同层吸积盘与 vanilla 粒子的批次（"光效外圈透明效果被引用到其他效果"）。
        tess.draw();
        Minecraft.getMinecraft().renderEngine.bindTexture(GTSRFXParticle.GLOW_SOFT_TEXTURE);
        GL11.glDepthMask(false);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        tess.startDrawingQuads();
        tess.setBrightness(0x00F000F0);
        // vanilla EntityFX.renderParticle 正确 billboard 公式（参数 rotationX/rotationXZ/rotationZ/rotationYZ/rotationXY）
        float arX = ActiveRenderInfo.rotationX;
        float arXZ = ActiveRenderInfo.rotationXZ;
        float arZ = ActiveRenderInfo.rotationZ;
        float arYZ = ActiveRenderInfo.rotationYZ;
        float arXY = ActiveRenderInfo.rotationXY;
        float ix = (float) (this.x - (double) EntityFX.interpPosX);
        float iy = (float) (this.y - (double) EntityFX.interpPosY);
        float iz = (float) (this.z - (double) EntityFX.interpPosZ);
        for (int i = 0; i < 3; i++) {
            float s = this.radius * (1.0F - (float) i * 0.25F);
            float a = alpha * (1.0F - (float) i * 0.3F);
            if (a <= 0.0F) {
                continue;
            }
            tess.setColorRGBA_F(this.colorR, this.colorG, this.colorB, a);
            tess.addVertexWithUV(
                (double) (ix - arX * s - arYZ * s),
                (double) (iy - arXZ * s),
                (double) (iz - arZ * s - arXY * s),
                0.0D,
                1.0D);
            tess.addVertexWithUV(
                (double) (ix - arX * s + arYZ * s),
                (double) (iy + arXZ * s),
                (double) (iz - arZ * s + arXY * s),
                1.0D,
                1.0D);
            tess.addVertexWithUV(
                (double) (ix + arX * s + arYZ * s),
                (double) (iy + arXZ * s),
                (double) (iz + arZ * s + arXY * s),
                1.0D,
                0.0D);
            tess.addVertexWithUV(
                (double) (ix + arX * s - arYZ * s),
                (double) (iy - arXZ * s),
                (double) (iz + arZ * s - arXY * s),
                0.0D,
                0.0D);
        }
        tess.draw();
        // 恢复 layer 0 层状态（层纹理 particles.png、normal 混合、GL_BLEND 保持开启），并恢复外层批次
        Minecraft.getMinecraft().renderEngine.bindTexture(PARTICLES_TEXTURE);
        GL11.glDepthMask(true);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_BLEND);
        tess.startDrawingQuads();
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }
}
