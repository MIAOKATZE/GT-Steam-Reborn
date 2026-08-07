package com.miaokatze.gtsr.common.fx;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 世界辉光特效：多层同心 additive billboard 圆，呼吸 + 寿命渐出。
 * 参考 Thaumcraft 4.2.3.5 TileNodeRenderer 辉光 quad 与 RenderSpecialItem 三角锥 glow，自包含实现。
 */
@SideOnly(Side.CLIENT)
public class GTSRGlowFX {

    private final World world;
    private final double x;
    private final double y;
    private final double z;
    private final float radius;
    private final float colorR;
    private final float colorG;
    private final float colorB;
    private final float baseAlpha;
    private final float phase;
    private final int maxAge;
    private int age;
    private boolean dead;

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
        this.phase = world.rand.nextFloat() * (float) Math.PI * 2.0F;
        this.maxAge = Math.max(1, durationTicks);
    }

    public static void spawn(World world, double x, double y, double z, float radius, float colorR, float colorG,
        float colorB, int durationTicks) {
        GTSRFXEngine.instance()
            .addGlow(new GTSRGlowFX(world, x, y, z, radius, colorR, colorG, colorB, durationTicks));
    }

    public void onUpdate() {
        this.age++;
        if (this.age >= this.maxAge) {
            this.dead = true;
        }
    }

    public boolean isDead() {
        return this.dead;
    }

    public void render(Tessellator tess, float partialTicks) {
        // 呼吸：世界时间正弦，随寿命渐出
        float breathe = 0.88F
            + 0.12F * (float) Math.sin((double) this.world.getWorldTime() * 0.1D + (double) this.phase);
        float fade = Math.max(0.0F, 1.0F - (float) this.age / (float) this.maxAge);
        float alpha = this.baseAlpha * breathe * fade;
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
