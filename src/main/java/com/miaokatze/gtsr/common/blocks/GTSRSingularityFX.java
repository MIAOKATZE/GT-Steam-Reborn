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

    /** 活跃粒子计数（诊断日志用：吸积盘走 vanilla 管道，不在引擎统计内） */
    private static int activeCount;

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
    private float colorR = 1.0F;
    private float colorG = 1.0F;
    private float colorB = 1.0F;
    private boolean arrived; // mode 2 吸收粒子：到达中心后吸附坠入
    private double spawnR; // mode 0 吸积盘出生半径（消散钳制基准）
    private int durationTicks; // mode 0 消散感知：奇点持续 tick 数（-1 表示永不消散）
    private int spawnElapsed; // mode 0 消散感知：粒子出生时已流逝的 tick 数

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
        this.spawnR = r;
        this.angle = theta;
        this.orbitSpeed = (float) ((0.06D + this.rand.nextDouble() * 0.09D) * (this.rand.nextBoolean() ? 1.0D : -1.0D));
        this.initRSq = r * r;
        this.shrinkFactor = 0.988F + (this.rand.nextFloat() * 2.0F - 1.0F) * 0.0015F;
        this.particleScale = 0.12F + this.rand.nextFloat() * 0.1F;
        this.particleMaxAge = 150 + this.rand.nextInt(70);
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.setRBGColorF(1.0F * this.colorR, 1.0F * this.colorG, 1.0F * this.colorB);
        GTSRSingularityFX.activeCount++;
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
        this.setRBGColorF(1.0F * this.colorR, 1.0F * this.colorG, 1.0F * this.colorB);
        GTSRSingularityFX.activeCount++;
    }

    /**
     * 设置消散变黑系数（1.0 为纯白，小于 1.0 时粒子整体变暗）。
     */
    public void setDarkScale(float darkScale) {
        this.darkScale = darkScale;
    }

    /** 当前活跃粒子数（诊断日志用） */
    public static int getActiveCount() {
        return GTSRSingularityFX.activeCount;
    }

    @Override
    public void setDead() {
        if (!this.isDead) {
            GTSRSingularityFX.activeCount--;
        }
        super.setDead();
    }

    public static void spawnDisk(World world, double cx, double cy, double cz, double spawnR, float darkScale,
        int durationTicks, int spawnElapsed, float colorR, float colorG, float colorB) {
        GTSRSingularityFX fx = new GTSRSingularityFX(world, cx, cy, cz, spawnR, 0);
        fx.setDarkScale(darkScale);
        fx.colorR = colorR;
        fx.colorG = colorG;
        fx.colorB = colorB;
        fx.durationTicks = durationTicks;
        fx.spawnElapsed = spawnElapsed;
        Minecraft.getMinecraft().effectRenderer.addEffect(fx);
    }

    public static void spawnAbsorb(World world, double fx, double fy, double fz, double tx, double ty, double tz,
        float colorR, float colorG, float colorB) {
        int count = 1 + world.rand.nextInt(3);
        for (int i = 0; i < count; i++) {
            GTSRSingularityFX particle = new GTSRSingularityFX(world, fx, fy, fz, tx, ty, tz, 2);
            particle.colorR = colorR;
            particle.colorG = colorG;
            particle.colorB = colorB;
            Minecraft.getMinecraft().effectRenderer.addEffect(particle);
        }
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        if (this.mode == 0) {
            // 当前奇点衰减系数 af（与 TileRunawaySingularity.getActiveFactor 同公式，不引用 TE）：
            // 持续期前 80% 保持 1.0，之后线性衰减到 0
            double elapsed = (double) (this.spawnElapsed + this.particleAge);
            double af;
            if (this.durationTicks == -1) {
                af = 1.0D;
            } else if (elapsed >= (double) this.durationTicks) {
                af = 0.0D;
            } else if (elapsed < (double) this.durationTicks * 0.8D) {
                af = 1.0D;
            } else {
                af = Math.max(
                    0.0D,
                    Math.min(1.0D, ((double) this.durationTicks - elapsed) / ((double) this.durationTicks * 0.2D)));
            }
            // 轨道角速度随半径收缩加速（角动量守恒近似 ω∝1/r^1.2），增量封顶防止 r→0 时爆炸
            float angInc = this.orbitSpeed
                * (float) Math.pow((double) (this.initRSq / (this.radius * this.radius)), 0.6D);
            float maxInc = Math.abs(this.orbitSpeed) * 2.5F;
            this.angle += Math.max(-maxInc, Math.min(maxInc, angInc));
            // 收缩：外圈慢速（迟滞感），内圈加速（坠入感，且防粒子在中心堆积成白色亮团）
            float shrink = this.shrinkFactor;
            if (this.radius < 2.0D) {
                shrink = 0.97F;
            }
            if (this.radius < 1.0D) {
                shrink = 0.94F;
            }
            // 收缩的同时受消散系数钳制：奇点消散时盘面随 effRange 一起收拢
            this.radius = Math.min(this.radius * shrink, this.spawnR * af * 0.9D);
            this.posX = this.centerX + Math.cos(this.angle) * this.radius;
            this.posZ = this.centerZ + Math.sin(this.angle) * this.radius;
            this.posY = this.centerY + Math.sin((double) this.particleAge * 0.1D) * 0.15D;
            // 内圈快速湮灭（radius<1.2 开始）+ 寿命后期渐隐（消散加速叠加，af=0 时每 tick 衰减 0.27 快速收尾）
            if (this.radius < 1.2D || (double) this.particleAge > (double) this.particleMaxAge * 0.9D) {
                this.alpha -= 0.15F + (float) ((1.0D - af) * 0.12D);
                if (this.alpha <= 0.0F) {
                    this.setDead();
                    return;
                }
            }
            // 中心湮灭兜底：半径收缩到极小直接消失（防极端堆积成白色亮团）
            if (this.radius < 0.4D) {
                this.setDead();
                return;
            }
        } else {
            if (!this.arrived) {
                double dx = this.centerX - this.posX;
                double dy = this.centerY - this.posY;
                double dz = this.centerZ - this.posZ;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist <= 0.12D) { // 到达中心：吸附并坠入
                    this.arrived = true;
                    this.posX = this.centerX;
                    this.posY = this.centerY;
                    this.posZ = this.centerZ;
                    this.motionX = 0.0D;
                    this.motionY = 0.0D;
                    this.motionZ = 0.0D;
                }
            }
            if (this.arrived) {
                this.particleScale *= 0.7F;
                this.alpha -= 0.25F;
                if (this.alpha <= 0.0F) {
                    this.setDead();
                    return;
                }
            } else {
                this.posX += this.motionX;
                this.posZ += this.motionZ;
                this.posY += this.motionY + Math.sin((double) this.particleAge * 0.2D) * 0.01D;
            }
        }
        // 纯白 × 颜色 × darkScale，不随时间变色
        this.particleRed = this.colorR * this.darkScale;
        this.particleGreen = this.colorG * this.darkScale;
        this.particleBlue = this.colorB * this.darkScale;
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
        // 白色渐变圆帧族（帧 0-5：中心实边缘透明；帧 6-7 是实心白色方块，不可用）：随 age 由小渐大
        int part = (int) ((float) this.particleAge / (float) this.particleMaxAge * 7.0F);
        part = Math.max(0, Math.min(5, part));
        float u0 = (float) (part % 16) / 16.0F;
        float u1 = u0 + 0.0624375F;
        float v0 = (float) (part / 16) / 16.0F;
        float v1 = v0 + 0.0624375F;
        float x = (float) (this.prevPosX + (this.posX - this.prevPosX) * (double) p - (double) interpPosX);
        float y = (float) (this.prevPosY + (this.posY - this.prevPosY) * (double) p - (double) interpPosY);
        float z = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * (double) p - (double) interpPosZ);
        float s = this.particleScale;
        tess.setColorRGBA_F(this.particleRed, this.particleGreen, this.particleBlue, this.alpha);
        // vanilla EntityFX.renderParticle 正确 billboard 公式（参数 rx=rotX, rz=rotXZ, ry=rotZ, rxz=rotYZ, ryz=rotXY）
        tess.addVertexWithUV(
            (double) (x - rx * s - rxz * s),
            (double) (y - rz * s),
            (double) (z - ry * s - ryz * s),
            (double) u0,
            (double) v1);
        tess.addVertexWithUV(
            (double) (x - rx * s + rxz * s),
            (double) (y + rz * s),
            (double) (z - ry * s + ryz * s),
            (double) u1,
            (double) v1);
        tess.addVertexWithUV(
            (double) (x + rx * s + rxz * s),
            (double) (y + rz * s),
            (double) (z + ry * s + ryz * s),
            (double) u1,
            (double) v0);
        tess.addVertexWithUV(
            (double) (x + rx * s - rxz * s),
            (double) (y - rz * s),
            (double) (z + ry * s - ryz * s),
            (double) u0,
            (double) v0);
    }
}
