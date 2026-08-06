package com.miaokatze.gtsr.common.blocks;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 奇点客户端粒子：聚拢 -> 环绕 -> 消散 三阶段光效
 */
@SideOnly(Side.CLIENT)
public class GTSRSingularityFX extends EntityFX {

    private int phase;
    private double targetX, targetY, targetZ;
    private float alpha = 1.0F;
    private double orbitRadius;
    private double orbitAngle;
    private double orbitSpeed;
    private int orbitTicks;
    private final double centerX, centerY, centerZ;

    public GTSRSingularityFX(World world, double cx, double cy, double cz, double range) {
        super(world, cx, cy, cz);
        this.centerX = cx;
        this.centerY = cy;
        this.centerZ = cz;

        // 拒绝采样：球内随机出生点
        double r = Math.max(range, 0.001D);
        double x = 0, y = 0, z = 0;
        for (int i = 0; i < 16; i++) {
            x = cx + (this.rand.nextDouble() * 2.0D - 1.0D) * r;
            y = cy + (this.rand.nextDouble() * 2.0D - 1.0D) * r;
            z = cz + (this.rand.nextDouble() * 2.0D - 1.0D) * r;
            double dx = x - cx, dy = y - cy, dz = z - cz;
            if (dx * dx + dy * dy + dz * dz <= r * r) {
                break;
            }
        }
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;

        this.phase = 0;
        this.motionX = (cx - this.posX) * 0.06D;
        this.motionY = (cy - this.posY) * 0.06D;
        this.motionZ = (cz - this.posZ) * 0.06D;
        this.particleGravity = 0.0F;
        this.particleScale = 0.15F + this.rand.nextFloat() * 0.15F;
        this.particleMaxAge = 60 + this.rand.nextInt(40);
        this.setRBGColorF(1.0F, 1.0F, 1.0F);
    }

    @Override
    public void onUpdate() {
        if (this.phase == 0) {
            // 聚拢：按方向直接设定速度，比例收敛
            double dx = this.centerX - this.posX;
            double dy = this.centerY - this.posY;
            double dz = this.centerZ - this.posZ;
            this.motionX = dx * 0.12D;
            this.motionY = dy * 0.12D;
            this.motionZ = dz * 0.12D;
            this.posX += this.motionX;
            this.posY += this.motionY;
            this.posZ += this.motionZ;

            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 1.2D) {
                this.phase = 1;
                this.orbitRadius = dist;
                this.orbitAngle = Math.atan2(dz, dx);
                this.orbitSpeed = (0.15D + this.rand.nextDouble() * 0.25D) * (this.rand.nextBoolean() ? 1.0D : -1.0D);
            }
        } else if (this.phase == 1) {
            // 环绕：水平圆 + 轻微 y 摆动，半径缓慢收缩
            this.orbitTicks++;
            this.orbitAngle += this.orbitSpeed;
            this.orbitRadius *= 0.995D;
            this.posX = this.centerX + Math.cos(this.orbitAngle) * this.orbitRadius;
            this.posZ = this.centerZ + Math.sin(this.orbitAngle) * this.orbitRadius;
            this.posY = this.centerY + Math.sin(this.orbitTicks * 0.1D) * 0.3D * this.orbitRadius;
            if (this.orbitTicks >= 30) {
                this.phase = 2;
            }
        } else {
            // 消散：透明度递减
            this.alpha -= 0.05F;
            if (this.alpha <= 0.0F) {
                this.setDead();
            }
        }

        // 颜色渐变：白 -> 品红
        float t = Math.min(1.0F, this.particleAge / 60.0F);
        this.setRBGColorF(1.0F, 1.0F - t, 1.0F);

        this.particleAge++;
        if (this.particleAge >= this.particleMaxAge) {
            this.setDead();
        }
    }

    @Override
    public void renderParticle(Tessellator tess, float p, float rx, float rz, float ry, float rxz, float ryz) {
        float x = (float) (this.prevPosX + (this.posX - this.prevPosX) * p - interpPosX);
        float y = (float) (this.prevPosY + (this.posY - this.prevPosY) * p - interpPosY);
        float z = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * p - interpPosZ);
        float s = this.particleScale * (0.6F + 0.6F * Math.min(1.0F, this.particleAge / 10.0F));

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(false);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        tess.setBrightness(0x00F000F0); // 全亮光效，不受环境光照影响
        tess.setColorRGBA_F(this.particleRed, this.particleGreen, this.particleBlue, this.alpha);
        tess.addVertexWithUV(x - rx * s - ry * s, y - rxz * s, z - rz * s - ryz * s, 0, 1);
        tess.addVertexWithUV(x - rx * s + ry * s, y + rxz * s, z - rz * s + ryz * s, 1, 1);
        tess.addVertexWithUV(x + rx * s + ry * s, y + rxz * s, z + rz * s + ryz * s, 1, 0);
        tess.addVertexWithUV(x + rx * s - ry * s, y - rxz * s, z + rz * s - ryz * s, 0, 0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(true);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public int getFXLayer() {
        return 0;
    }
}
