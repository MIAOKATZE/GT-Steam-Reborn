package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public final class EntityVisualCasing extends Entity {

    private static final double GRAVITY_PER_TICK = .045D, GROUND_FRICTION = .6D, SETTLE_SPEED = .005D;
    private int ageTicks;
    private boolean settled, landSoundPlayed;
    private float spinDegPerTick;
    private static long lastLandSound = -1000;
    private static World soundWorld;

    public EntityVisualCasing(World world, double x, double y, double z, float barrelYawDeg, boolean ejectRight) {
        super(world);
        this.setSize(0.12F, 0.12F);
        this.ignoreFrustumCheck = true;
        // 出膛角（v0.1.15 扇形化）：θ = barrelYawRad + side×(π/2) + δRad，方向=(sin θ, cos θ)
        // （前向=(sin,cos) 同口径）；δ=(rand−0.5)×2×EJECT_SPREAD_DEG 绕竖轴随机偏转。
        // δ=0 时与旧向量数学恒等（三角恒等式；IEEE754 数值差 ≤1 ulp）：side=−1 → sin(yaw−π/2)=−cos yaw ✓、
        // cos(yaw−π/2)=+sin yaw ✓；side=+1 → sin(yaw+π/2)=+cos yaw ✓、cos(yaw+π/2)=−sin yaw ✓
        // ——即旧 (cos yaw·side, −sin yaw·side)，纯方向重参数化零行为漂移。
        double yawRad = Math.toRadians(barrelYawDeg);
        double side = ejectRight ? -1.0D : (this.rand.nextBoolean() ? 1.0D : -1.0D);
        double theta = yawRad + side * (Math.PI / 2.0D)
            + (this.rand.nextDouble() - 0.5D) * 2.0D * Math.toRadians(GatlingFxProfile.EJECT_SPREAD_DEG);
        double ejectX = Math.sin(theta);
        double ejectZ = Math.cos(theta);
        double offset = ejectRight ? 0.02D + this.rand.nextDouble() * 0.03D : 0.12D + this.rand.nextDouble() * 0.08D; // 出壁外移（右抛=贴口
                                                                                                                      // 0.02~0.05；旧=0.12~0.20
                                                                                                                      // 脱离模型体）
        double speed = 0.25D + this.rand.nextDouble() * 0.15D; // 水平 0.25~0.4 格/t
        this.setPosition(x + ejectX * offset, y, z + ejectZ * offset);
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.motionX = ejectX * speed;
        this.motionZ = ejectZ * speed;
        this.motionY = 0.10D + this.rand.nextDouble() * 0.08D; // 竖直上抛 0.10~0.18
        this.rotationYaw = this.rand.nextFloat() * 360.0F;
        this.prevRotationYaw = this.rotationYaw;
        this.spinDegPerTick = (this.rand.nextFloat() - 0.5F) * 30.0F;
    }

    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.prevRotationYaw = this.rotationYaw;
        // v0.1.33 寿命分档（唯一消费点，全仓无其他 LIFETIME_TICKS 读取）：性能模式 20t /
        // 完整视觉 60t（pre-v0.1.31 原值）；逐 tick 读旗标（volatile 布尔，开销可忽略），
        // 模式中途切换对在场个体下一 tick 生效
        int lifetimeTicks = 60;
        if (this.ageTicks++ >= lifetimeTicks || this.worldObj == null) {
            setDead(); // 到龄消散（登出/换维随 world 清理）
            return;
        }
        if (!this.settled) {
            this.motionY -= GRAVITY_PER_TICK;
            this.moveEntity(this.motionX, this.motionY, this.motionZ); // 原版方块碰撞（扫掠）
            if (this.onGround) {
                this.motionY = 0.0D; // 直接静止（不做反弹，E-code-2 D 两可选项取简）
                this.motionX *= GROUND_FRICTION;
                this.motionZ *= GROUND_FRICTION;
                if (Math.abs(this.motionX) < SETTLE_SPEED && Math.abs(this.motionZ) < SETTLE_SPEED) {
                    this.motionX = 0.0D;
                    this.motionZ = 0.0D;
                    this.settled = true;
                    playLandSound(); // E2b：弹壳落地音（settled 首次成立处，每实体一次）
                }
            }
            this.rotationYaw += this.spinDegPerTick;
        }
    }

    private void playLandSound() {
        if (landSoundPlayed || worldObj == null) return;
        landSoundPlayed = true;
        if (soundWorld != worldObj) {
            soundWorld = worldObj;
            lastLandSound = -1000;
        }
        long now = worldObj.getTotalWorldTime();
        if (now - lastLandSound < 2) return;
        lastLandSound = now;
        worldObj.playSound(
            posX,
            posY,
            posZ,
            getEntityData().getBoolean("gtsr.heavyCasing") ? "gtsr:weapons.fx.casing_drop_gx2"
                : "gtsr:weapons.casing_drop",
            .15F + rand.nextFloat() * .1F,
            .9F + rand.nextFloat() * .2F,
            false);
    }

    protected void entityInit() {}

    protected void writeEntityToNBT(NBTTagCompound n) {}

    protected void readEntityFromNBT(NBTTagCompound n) {}
}
