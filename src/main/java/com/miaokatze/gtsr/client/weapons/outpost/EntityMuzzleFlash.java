package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * E2a：蜂群 machine_gun 炮口闪光<b>纯客户端视觉实体</b>（同 D6 {@link EntityVisualCasing} 口径：
 * 构造于客户端 world、不注册 EntityRegistry id、无网络包——实体永不跨端，
 * {@link RenderMuzzleFlash} 经 RenderingRegistry 按类绑定渲染）。
 *
 * <p>
 * <b>行为</b>：spawn 时一次定形全部随机量（寿命 {@link GatlingFxProfile#FLASH_LIFE_MIN}~MAX=2~4t、
 * 随机 roll 0~360°（星芒贴图取向打散）、随机缩放 jitter 0.85~1.2、光环边长 0.9~1.2 格），
 * onUpdate 仅 age++ 到龄 {@code setDead()}——无位移/无碰撞/无重力（闪光原地定格，观感靠
 * alpha 快速衰减）；不写 NBT（客户端实体不落盘）。spawn 侧（PacketS2CFireEvent 消费链）
 * 由后续 E2b 切片接线，本切片交付实体/渲染器/注册。
 *
 * <p>
 * <b>字段可见性</b>：随机定形量为 {@code public final}——渲染器（同包）按实体实例读参绘制，
 * 与 TurretModelSpecs.Group 公开字段同风格。
 */
@SideOnly(Side.CLIENT)
public class EntityMuzzleFlash extends Entity {

    /** 已存活 tick（消散倒计时基准；渲染器读做 alpha 相位，仅 onUpdate 递增） */
    public int ageTicks = 0;

    /** 寿命（tick；spawn 时 2~4 随机） */
    public final int lifeTicks;

    /** 炮管朝向（deg，世界数学角；预留给 E2b 定向扩展，billboard 渲染不消费） */
    public final float yawDeg;

    /** 随机 roll（deg 0~360；星芒贴图取向抖动） */
    public final float rollDeg;

    /** 随机缩放抖动（0.85~1.2；核心与光环同乘） */
    public final float scaleJitter;

    /** 光环（halo）面片边长（格；spawn 时 0.9~1.2 随机） */
    public final float haloScale;

    /** 反序列化占位构造（客户端实体无 NBT 路径，防御性保留，同 EntityVisualCasing） */
    public EntityMuzzleFlash(World world) {
        this(world, 0.0D, 0.0D, 0.0D, 0.0F);
    }

    /**
     * 视觉生成入口（E2b 消费）。
     *
     * @param x,     y, z 炮口世界坐标（MuzzleHelper 炮口链产出）
     * @param yawDeg 炮管朝向（deg，世界数学角 atan2(dx,dz)、+Z=0，同 aimYaw 口径）
     */
    public EntityMuzzleFlash(World world, double x, double y, double z, float yawDeg) {
        super(world);
        this.setSize(0.6F, 0.6F);
        this.ignoreFrustumCheck = true;
        this.setPosition(x, y, z);
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.yawDeg = yawDeg;
        this.rollDeg = this.rand.nextFloat() * 360.0F;
        this.lifeTicks = GatlingFxProfile.FLASH_LIFE_MIN
            + this.rand.nextInt(GatlingFxProfile.FLASH_LIFE_MAX - GatlingFxProfile.FLASH_LIFE_MIN + 1);
        this.scaleJitter = GatlingFxProfile.FLASH_SCALE_JITTER_MIN + this.rand.nextFloat()
            * (GatlingFxProfile.FLASH_SCALE_JITTER_MAX - GatlingFxProfile.FLASH_SCALE_JITTER_MIN);
        this.haloScale = GatlingFxProfile.FLASH_HALO_MIN
            + this.rand.nextFloat() * (GatlingFxProfile.FLASH_HALO_MAX - GatlingFxProfile.FLASH_HALO_MIN);
    }

    /** Loaded ammunition/FX have no render-distance cap; the engine retains normal frustum culling. */
    @Override
    public boolean isInRangeToRenderDist(double distance) {
        return true; // Loaded ammunition and FX remain eligible regardless of camera distance.
    }

    /** 到龄消散（无位移积分：闪光原地定格，观察者动效=alpha 衰减）；登出/换维随 world 清理 */
    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        if (this.worldObj == null || this.ageTicks++ >= this.lifeTicks) {
            setDead();
        }
    }

    /** 客户端实体不落盘：空实现 */
    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {}

    /** 客户端实体不落盘：空实现 */
    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {}

    @Override
    protected void entityInit() {}
}
