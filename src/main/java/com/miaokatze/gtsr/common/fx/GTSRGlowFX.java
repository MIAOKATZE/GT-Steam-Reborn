package com.miaokatze.gtsr.common.fx;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 世界辉光特效：多层同心 additive billboard 圆，极微弱波动 + 寿命渐出。
 * 参考 Thaumcraft 4.2.3.5 TileNodeRenderer 辉光 quad 与 RenderSpecialItem 三角锥 glow，自包含实现。
 */
@SideOnly(Side.CLIENT)
public class GTSRGlowFX {

    private final World world;
    private final double x;
    private final double y;
    private final double z;
    private float radius;
    private final float colorR;
    private final float colorG;
    private final float colorB;
    private final float baseAlpha;
    private final int maxAge;
    private int age;
    private boolean dead;
    private float darkScale = 1.0F;
    private float shrinkPerTick = 0.0F;
    /** 颤动（慢速随机波动）：当前强度与目标强度，每 30~59 tick 换一次目标 */
    private float breathe = 1.0F;
    private float breatheTarget = 1.0F;
    private int breatheChangeIn;

    public GTSRGlowFX(World world, double x, double y, double z, float radius, float colorR, float colorG, float colorB,
        int durationTicks) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.colorR = colorR;
        this.colorG = colorG;
        this.colorB = colorB;
        this.baseAlpha = 0.9F;
        this.maxAge = Math.max(1, durationTicks);
    }

    public static GTSRGlowFX spawn(World world, double x, double y, double z, float radius, float colorR, float colorG,
        float colorB, int durationTicks) {
        GTSRGlowFX glow = new GTSRGlowFX(world, x, y, z, radius, colorR, colorG, colorB, durationTicks);
        GTSRFXEngine.instance()
            .addGlow(glow);
        return glow;
    }

    /** 每 tick 更新半径与暗化系数（消散时随 activeFactor 收缩、变暗） */
    public void updateParams(float radius, float darkScale) {
        this.radius = radius;
        this.darkScale = darkScale;
    }

    /** 设置每 tick 半径收缩量（>0 时开启收缩，用于消散过渡辉光） */
    public void setShrinkPerTick(float shrinkPerTick) {
        this.shrinkPerTick = shrinkPerTick;
    }

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
            this.dead = true;
        }
    }

    public boolean isDead() {
        return this.dead;
    }

    public void setDead() {
        this.dead = true;
    }

    public void render(Tessellator tess, float partialTicks) {
        // 颤动强度字段（onUpdate 缓慢随机波动），随寿命渐出
        float fade = Math.max(0.0F, 1.0F - (float) this.age / (float) this.maxAge);
        // 近场衰减：贴脸（<3 格）时淡出，避免 additive 辉光近距全屏泛白
        float dist = 100.0F;
        if (net.minecraft.client.Minecraft.getMinecraft().thePlayer != null) {
            dist = (float) net.minecraft.client.Minecraft.getMinecraft().thePlayer.getDistance(this.x, this.y, this.z);
        }
        float nearFade = Math.min(1.0F, dist / 3.0F);
        float alpha = this.baseAlpha * this.breathe * fade * this.darkScale * nearFade;
        float arX = ActiveRenderInfo.rotationX;
        float arZ = ActiveRenderInfo.rotationZ;
        float arYZ = ActiveRenderInfo.rotationYZ;
        float arXY = ActiveRenderInfo.rotationXY;
        float arXZ = ActiveRenderInfo.rotationXZ;
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
