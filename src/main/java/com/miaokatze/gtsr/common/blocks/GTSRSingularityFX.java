package com.miaokatze.gtsr.common.blocks;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.fx.GTSRFXParticle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 吸积环轨道粒子（自包含，参考 TC4 FXWisp 螺旋）
 */
@SideOnly(Side.CLIENT)
public class GTSRSingularityFX extends GTSRFXParticle {

    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final double axisX;
    private final double axisY;
    private final double axisZ;
    private final double basisX;
    private final double basisY;
    private final double basisZ;
    private final double crossX;
    private final double crossY;
    private final double crossZ;
    private final double orbitSpeed;
    private final double shrink;
    private final double wispPhase;
    private double radius;
    private double angle;
    private float alpha = 1.0F;
    private boolean dissipating;

    public GTSRSingularityFX(World world, double cx, double cy, double cz, double spawnR) {
        super(world, cx, cy, cz);
        this.centerX = cx;
        this.centerY = cy;
        this.centerZ = cz;

        // 球壳内随机出生点
        double r = Math.max(spawnR, 0.001D) * (0.4D + 0.6D * this.rand.nextDouble());
        double theta = this.rand.nextDouble() * 2.0D * Math.PI;
        double phi = Math.acos(2.0D * this.rand.nextDouble() - 1.0D);
        this.posX = cx + Math.sin(phi) * Math.cos(theta) * r;
        this.posY = cy + Math.sin(phi) * Math.sin(theta) * r;
        this.posZ = cz + Math.cos(phi) * r;
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        // 随机倾斜轨道平面：法线轴 + 平面内正交基
        double aTheta = this.rand.nextDouble() * 2.0D * Math.PI;
        double aPhi = Math.acos(2.0D * this.rand.nextDouble() - 1.0D);
        this.axisX = Math.sin(aPhi) * Math.cos(aTheta);
        this.axisY = Math.sin(aPhi) * Math.sin(aTheta);
        this.axisZ = Math.cos(aPhi);
        double rx, ry, rz;
        if (Math.abs(this.axisX) > 0.9D) {
            rx = 0.0D;
            ry = 1.0D;
            rz = 0.0D;
        } else {
            rx = 1.0D;
            ry = 0.0D;
            rz = 0.0D;
        }
        double bX = this.axisY * rz - this.axisZ * ry;
        double bY = this.axisZ * rx - this.axisX * rz;
        double bZ = this.axisX * ry - this.axisY * rx;
        double bLen = Math.sqrt(bX * bX + bY * bY + bZ * bZ);
        this.basisX = bX / bLen;
        this.basisY = bY / bLen;
        this.basisZ = bZ / bLen;
        this.crossX = this.axisY * this.basisZ - this.axisZ * this.basisY;
        this.crossY = this.axisZ * this.basisX - this.axisX * this.basisZ;
        this.crossZ = this.axisX * this.basisY - this.axisY * this.basisX;

        this.radius = r;
        this.angle = this.rand.nextDouble() * 2.0D * Math.PI;
        this.orbitSpeed = (0.05D + this.rand.nextDouble() * 0.07D) * (this.rand.nextBoolean() ? 1.0D : -1.0D);
        this.shrink = 0.985D + this.rand.nextDouble() * 0.010D;
        this.wispPhase = this.rand.nextDouble() * 2.0D * Math.PI;
        this.particleScale = 0.1F + this.rand.nextFloat() * 0.1F;
        this.particleMaxAge = 40 + this.rand.nextInt(40);
        this.setRBGColorF(1.0F, 1.0F, 1.0F);
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.angle += this.orbitSpeed;
        this.radius *= this.shrink;
        double cosA = Math.cos(this.angle);
        double sinA = Math.sin(this.angle);
        double wisp = Math.sin((double) this.particleAge * 0.15D + this.wispPhase) * 0.12D * this.radius;
        this.posX = this.centerX + this.radius * (cosA * this.basisX + sinA * this.crossX) + wisp * this.axisX;
        this.posY = this.centerY + this.radius * (cosA * this.basisY + sinA * this.crossY) + wisp * this.axisY;
        this.posZ = this.centerZ + this.radius * (cosA * this.basisZ + sinA * this.crossZ) + wisp * this.axisZ;

        if (this.radius < 0.6D) {
            this.dissipating = true;
        }
        if (this.dissipating) {
            this.alpha -= 0.06F;
            this.particleAlpha = Math.max(0.0F, this.alpha);
            if (this.alpha <= 0.0F) {
                this.setDead();
                return;
            }
        }

        float t = Math.min(1.0F, this.particleAge / 60.0F);
        this.setRBGColorF(1.0F, 1.0F - t, 1.0F);

        this.particleAge++;
        if (this.particleAge >= this.particleMaxAge) {
            this.setDead();
        }
    }

    @Override
    public void renderParticle(Tessellator tess, float p, float rx, float rz, float ry, float rxz, float ryz) {
        this.renderGlowTextureQuad(tess, p, rx, rz, ry, rxz, ryz);
    }
}
