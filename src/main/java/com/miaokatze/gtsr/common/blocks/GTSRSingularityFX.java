package com.miaokatze.gtsr.common.blocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.fx.GTSRFXParticle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 奇点粒子：吸积/消散/向心粒子（extends EntityFX，vanilla 传统粒子管道，不依赖引擎渲染）。
 * 继承 GTSRFXParticle 以兼容既有引擎入口调用；静态入口走 Minecraft.effectRenderer。自包含实现。
 */
@SideOnly(Side.CLIENT)
public class GTSRSingularityFX extends GTSRFXParticle {

    private int mode;
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private double radius;
    private double angle;
    private float alpha = 1.0F;
    private float orbitSpeed;
    private double initRSq;
    private float shrinkFactor = 0.98F;
    private float darkScale = 1.0F;

    public GTSRSingularityFX(World world, double cx, double cy, double cz, double spawnR) {
        this(world, cx, cy, cz, spawnR, 0);
    }

    public GTSRSingularityFX(World world, double cx, double cy, double cz, double spawnR, int mode) {
        super(world, cx, cy, cz);
        this.mode = mode;
        this.centerX = cx;
        this.centerY = cy;
        this.centerZ = cz;
        this.particleGravity = 0.0F;
        // 吸积盘：中心平面内随机轨道
        double r = spawnR * (0.45D + 0.55D * this.rand.nextDouble());
        double theta = this.rand.nextDouble() * 2.0D * Math.PI;
        this.posX = cx + Math.cos(theta) * r;
        this.posZ = cz + Math.sin(theta) * r;
        this.posY = cy + (this.rand.nextDouble() - 0.5D) * 0.4D;
        this.radius = r;
        this.angle = theta;
        this.orbitSpeed = (float) ((0.06D + this.rand.nextDouble() * 0.09D) * (this.rand.nextBoolean() ? 1.0D : -1.0D));
        this.initRSq = r * r;
        this.shrinkFactor = 0.98F + (this.rand.nextFloat() * 2.0F - 1.0F) * 0.003F;
        this.particleScale = 0.12F + this.rand.nextFloat() * 0.1F;
        this.particleMaxAge = 50 + this.rand.nextInt(40);
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.setRBGColorF(1.0F, 1.0F, 1.0F);
    }

    /**
     * 向心粒子：出生点 -> 目标点（吸积）。
     */
    private GTSRSingularityFX(World world, double fromX, double fromY, double fromZ, double toX, double toY, double toZ,
        int mode) {
        super(world, fromX, fromY, fromZ);
        this.mode = mode;
        this.centerX = toX;
        this.centerY = toY;
        this.centerZ = toZ;
        this.particleGravity = 0.0F;
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4D) {
            len = 1.0E-4D;
        }
        double speed = 0.12D + this.rand.nextDouble() * 0.08D;
        this.motionX = dx / len * speed;
        this.motionY = dy / len * speed;
        this.motionZ = dz / len * speed;
        this.particleScale = 0.12F + this.rand.nextFloat() * 0.1F;
        this.particleMaxAge = 25 + this.rand.nextInt(15);
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.setRBGColorF(1.0F, 1.0F, 1.0F);
    }

    /**
     * 设置消散变黑系数（1.0 为纯白，小于 1.0 时粒子整体变暗）。
     */
    public void setDarkScale(float darkScale) {
        this.darkScale = darkScale;
    }

    public static void spawnDisk(World world, double cx, double cy, double cz, double range, float darkScale) {
        GTSRSingularityFX fx = new GTSRSingularityFX(world, cx, cy, cz, range, 0);
        fx.setDarkScale(darkScale);
        Minecraft.getMinecraft().effectRenderer.addEffect(fx);
    }

    public static void spawnAbsorb(World world, double fx, double fy, double fz, double tx, double ty, double tz) {
        int count = 1 + world.rand.nextInt(3);
        for (int i = 0; i < count; i++) {
            GTSRSingularityFX particle = new GTSRSingularityFX(world, fx, fy, fz, tx, ty, tz, 2);
            Minecraft.getMinecraft().effectRenderer.addEffect(particle);
        }
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        if (this.mode == 0) {
            // 轨道角速度随半径收缩加速（角动量守恒近似 ω∝1/r²），增量封顶防止 r→0 时爆炸
            float angInc = this.orbitSpeed * (float) (this.initRSq / (this.radius * this.radius));
            float maxInc = Math.abs(this.orbitSpeed) * 6.0F;
            this.angle += Math.max(-maxInc, Math.min(maxInc, angInc));
            this.radius *= this.shrinkFactor;
            this.posX = this.centerX + Math.cos(this.angle) * this.radius;
            this.posZ = this.centerZ + Math.sin(this.angle) * this.radius;
            this.posY = this.centerY + Math.sin((double) this.particleAge * 0.1D) * 0.15D;
            // 明显收敛到中心附近或寿命后期开始渐隐消失
            if (this.radius < 1.0D || (double) this.particleAge > (double) this.particleMaxAge * 0.7D) {
                this.alpha -= 0.08F;
                if (this.alpha <= 0.0F) {
                    this.setDead();
                    return;
                }
            }
        } else {
            this.posX += this.motionX;
            this.posZ += this.motionZ;
            this.posY += this.motionY + Math.sin((double) this.particleAge * 0.2D) * 0.01D;
        }
        // 纯白 × darkScale，不随时间变色
        this.particleRed = 1.0F * this.darkScale;
        this.particleGreen = 1.0F * this.darkScale;
        this.particleBlue = 1.0F * this.darkScale;
        this.particleAge++;
        if (this.particleAge >= this.particleMaxAge) {
            this.setDead();
        }
    }

    @Override
    public int getFXLayer() {
        return 0;
    }

    @Override
    public void renderParticle(Tessellator tess, float p, float rx, float rz, float ry, float rxz, float ryz) {
        int part = 16 + (int) (this.particleAge / 6.0F) % 4;
        float u0 = (float) (part % 16) / 16.0F;
        float u1 = u0 + 0.0624375F;
        float v0 = (float) (part / 16) / 16.0F;
        float v1 = v0 + 0.0624375F;
        float x = (float) (this.prevPosX + (this.posX - this.prevPosX) * (double) p - (double) interpPosX);
        float y = (float) (this.prevPosY + (this.posY - this.prevPosY) * (double) p - (double) interpPosY);
        float z = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * (double) p - (double) interpPosZ);
        float s = this.particleScale;
        tess.setColorRGBA_F(this.particleRed, this.particleGreen, this.particleBlue, this.alpha);
        tess.addVertexWithUV(
            (double) (x - rx * s - ry * s),
            (double) (y - rxz * s),
            (double) (z - rz * s - ryz * s),
            (double) u0,
            (double) v1);
        tess.addVertexWithUV(
            (double) (x - rx * s + ry * s),
            (double) (y + rxz * s),
            (double) (z - rz * s + ryz * s),
            (double) u1,
            (double) v1);
        tess.addVertexWithUV(
            (double) (x + rx * s + ry * s),
            (double) (y + rxz * s),
            (double) (z + rz * s + ryz * s),
            (double) u1,
            (double) v0);
        tess.addVertexWithUV(
            (double) (x + rx * s - ry * s),
            (double) (y - rxz * s),
            (double) (z + rz * s - ryz * s),
            (double) u0,
            (double) v0);
    }
}
