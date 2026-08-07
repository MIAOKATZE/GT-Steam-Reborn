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
        if (mode == 0) {
            // 吸积盘：中心平面内随机轨道
            double r = spawnR * (0.45D + 0.55D * this.rand.nextDouble());
            double theta = this.rand.nextDouble() * 2.0D * Math.PI;
            this.posX = cx + Math.cos(theta) * r;
            this.posZ = cz + Math.sin(theta) * r;
            this.posY = cy + (this.rand.nextDouble() - 0.5D) * 0.4D;
            this.radius = r;
            this.angle = theta;
            this.orbitSpeed = (float) ((0.06D + this.rand.nextDouble() * 0.09D)
                * (this.rand.nextBoolean() ? 1.0D : -1.0D));
            this.particleScale = 0.12F + this.rand.nextFloat() * 0.1F;
            this.particleMaxAge = 50 + this.rand.nextInt(40);
        } else {
            // 外扩：中心随机球面喷出
            double dirX = this.rand.nextDouble() * 2.0D - 1.0D;
            double dirY = (this.rand.nextDouble() * 2.0D - 1.0D) * 0.5D;
            double dirZ = this.rand.nextDouble() * 2.0D - 1.0D;
            double dirLen = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (dirLen < 1.0E-4D) {
                dirLen = 1.0E-4D;
            }
            double speed = 0.15D + this.rand.nextDouble() * 0.2D;
            this.motionX = dirX / dirLen * speed;
            this.motionY = dirY / dirLen * speed;
            this.motionZ = dirZ / dirLen * speed;
            this.particleScale = 0.12F + this.rand.nextFloat() * 0.1F;
            this.particleMaxAge = 30 + this.rand.nextInt(20);
        }
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.setRBGColorF(0.95F, 0.97F, 1.0F);
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
        this.setRBGColorF(0.95F, 0.97F, 1.0F);
    }

    public static void spawnDisk(World world, double cx, double cy, double cz, double range) {
        GTSRSingularityFX fx = new GTSRSingularityFX(world, cx, cy, cz, range, 0);
        Minecraft.getMinecraft().effectRenderer.addEffect(fx);
    }

    public static void spawnBurst(World world, double cx, double cy, double cz) {
        for (int i = 0; i < 12; i++) {
            GTSRSingularityFX fx = new GTSRSingularityFX(world, cx, cy, cz, 0.0D, 1);
            Minecraft.getMinecraft().effectRenderer.addEffect(fx);
        }
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
            this.angle += this.orbitSpeed;
            this.radius *= 0.992F;
            this.posX = this.centerX + Math.cos(this.angle) * this.radius;
            this.posZ = this.centerZ + Math.sin(this.angle) * this.radius;
            this.posY = this.centerY + Math.sin((double) this.particleAge * 0.1D) * 0.15D;
            if (this.radius < 0.5D) {
                this.alpha -= 0.06F;
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
        float t = Math.min(1.0F, (float) this.particleAge / 60.0F);
        this.particleRed = 0.95F - 0.2F * t;
        this.particleGreen = 0.97F - 0.12F * t;
        this.particleBlue = 1.0F;
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
