package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * E2a：软烟团<b>纯客户端视觉实体</b>（同 {@link EntityVisualCasing} 口径：构造于客户端
 * world、不注册 EntityRegistry id、无网络包——实体永不跨端，{@link RenderSmokePuff}
 * 经 RenderingRegistry 按类绑定渲染）。通用参数化：曳光烟/长条轨迹段（v0.1.14
 * {@link #spawnStreak}）、枪口烟与停射余烟共用
 * 本实体，尺寸/透明度/寿命/颜色全部构造入参（调参只经 {@link GatlingFxProfile}）。
 *
 * <p>
 * <b>行为</b>：onUpdate 仅线性积分 motion（无碰撞、无重力、不走 moveEntity 扫掠）+
 * age++ 到龄消散；不写 NBT。<b>性能预算</b>：静态 {@link AtomicInteger} 活体计数 +
 * {@link GatlingFxProfile#SMOKE_CAP}=600 上限，静态 {@link #spawn} 入口满员返回 false
 * （调用方静默放弃，无需处理）；计数随 {@code setDead()} 回收（幂等：仅首次置位时递减）。
 * 换维/卸载时未走 setDead 的残留计数由 {@link #resetCounter()} 兜底（E2b 换维清理处调用）。
 */
@SideOnly(Side.CLIENT)
public class SmokePuffEntity extends Entity {

    /** 全 mod 软烟团活体计数（SMOKE_CAP 预算；仅客户端主线程 spawn/setDead 访问，原子兜底） */
    private static final AtomicInteger LIVE_COUNT = new AtomicInteger();
    // Direct-mapped spatial cache is bounded independently of historic shots; collisions only miss merges.
    private static final SmokePuffEntity[] STREAK_CACHE = new SmokePuffEntity[4096 * 4];
    private static World streakWorld;
    private static int streakSlot;
    private double trailAnchorX, trailAnchorY, trailAnchorZ;

    private static int streakBucket(int x, int y, int z) {
        return (x * 73856093 ^ y * 19349663 ^ z * 83492791) & 4095;
    }

    /** 已存活 tick（消散倒计时基准；渲染器读做尺寸/alpha 相位，仅 onUpdate 递增） */
    public int ageTicks = 0;

    /** 初/末尺寸（格；渲染按寿命线性插值） */
    public final float scale0;
    public final float scale1;

    /** 初始 alpha（渲染随寿命线性衰减到 0） */
    public final float alpha0;

    /** 寿命（tick） */
    public final int lifeTicks;

    /** 烟色（0..1；曳光烟偏暖灰、余烟偏冷灰，E2b 决定） */
    public final float r;
    public final float g;
    public final float b;

    /**
     * v0.1.14 长条 billboard 模式单位轴（弹道方向；零向量=圆斑 billboard 模式）。
     * 渲染端沿本轴把 quad 拉伸为全长 {@link #streakLength}、面向相机卷绕的圆柱式
     * billboard（轴=弹道方向、宽=寿命插值尺寸），见 {@link RenderSmokePuff}
     */
    public final float axisX;
    public final float axisY;
    public final float axisZ;

    /** 长条模式全长（格；0=圆斑模式。长条段生成于段中点，几何在世界系静止、仅随 motion 漂移） */
    public final float streakLength;

    /**
     * 末期膨胀（swell）倍率（1.0=关闭，默认关闭）：>1 时渲染端以
     * {@link GatlingFxProfile#puffScaleWithSwell} 替代线性放大（仅圆斑模式语义，守卫在
     * {@link RenderSmokePuff} 侧）。枪口烟经 {@link #spawn} 膨胀重载逐粒注入；
     * {@link #spawnStreak} 轨迹段与停射余烟的既有入口不触碰本字段=维持原线性放大行为。
     * 经 setter 注入而非构造器入参（全部构造器签名保持兼容）。
     */
    private float endSwellFactor = 1.0F;

    /**
     * 白烟图档位（false=既有 {@code smoke_soft.png} 路径，逐字节原行为）：火箭/导弹的浓重白烟轨迹
     * 与尾喷口尾烟需要真正偏白的图档，而 {@code smoke_soft.png} 自身最亮通道封顶 ≈0.745，
     * 靠 {@code glColor4f} 提亮顶不出白（见 {@code EffectPalette#TRAIL_WHITE} 注记）。
     * 贴图路径单一真值在 {@link com.miaokatze.gto.client.effect.EffectPalette#WHITE_SMOKE_TEXTURE}，
     * 本位只是逐实例开关（与 {@link #endSwellFactor} 同式：setter 注入，构造器签名保持兼容）。
     */
    private boolean whitePuff = false;

    /** 反序列化占位构造（客户端实体无 NBT 路径，防御性保留）——不计数，勿直接 spawnEntityInWorld */
    public SmokePuffEntity(World world) {
        this(world, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.1F, 0.1F, 0.5F, 60, 0.8F, 0.8F, 0.8F);
    }

    /**
     * 圆斑模式实体构造（调用方走 {@link #spawn} 以纳入 SMOKE_CAP 预算）。
     *
     * @param x,        y, z 出生世界坐标
     * @param motionX,  motionY, motionZ 初速度（格/tick；上飘=Profile.SMOKE_RISE 类正值，漂移调用方随机）
     * @param scale0,   scale1 初/末尺寸（格）
     * @param alpha0    初始不透明度（0..1）
     * @param lifeTicks 寿命（tick）
     * @param r,        g, b 烟色（0..1）
     */
    public SmokePuffEntity(World world, double x, double y, double z, double motionX, double motionY, double motionZ,
        float scale0, float scale1, float alpha0, int lifeTicks, float r, float g, float b) {
        this(
            world,
            x,
            y,
            z,
            motionX,
            motionY,
            motionZ,
            scale0,
            scale1,
            alpha0,
            lifeTicks,
            r,
            g,
            b,
            0.0F,
            0.0F,
            0.0F,
            0.0F);
    }

    /**
     * 长条模式实体构造（v0.1.14；调用方走 {@link #spawnStreak} 以纳入 SMOKE_CAP 预算）：
     * axis* 需为单位向量、streakLength>0，出生点=弹道段中点；其余语义同圆斑构造。
     */
    public SmokePuffEntity(World world, double x, double y, double z, double motionX, double motionY, double motionZ,
        float scale0, float scale1, float alpha0, int lifeTicks, float r, float g, float b, float axisX, float axisY,
        float axisZ, float streakLength) {
        super(world);
        this.setSize(0.2F, 0.2F);
        this.ignoreFrustumCheck = true;
        this.setPosition(x, y, z);
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.scale0 = scale0;
        this.scale1 = scale1;
        this.alpha0 = alpha0;
        this.lifeTicks = Math.max(1, lifeTicks);
        this.r = r;
        this.g = g;
        this.b = b;
        this.axisX = axisX;
        this.axisY = axisY;
        this.axisZ = axisZ;
        this.streakLength = streakLength;
    }

    /** 启用末期膨胀（圆斑模式专用语义；1.0=关闭）：逐实例注入，构造器签名保持不变 */
    public SmokePuffEntity setEndSwellFactor(float factor) {
        this.endSwellFactor = factor;
        return this;
    }

    /** 当前末期膨胀倍率（渲染端读；1.0=未激活=原线性放大） */
    public float getEndSwellFactor() {
        return this.endSwellFactor;
    }

    /** 切到白烟图档（逐实例注入，链式）：仅改渲染期绑定的贴图，不改任何几何/颜色/预算语义 */
    public SmokePuffEntity setWhitePuff(boolean white) {
        this.whitePuff = white;
        return this;
    }

    /** 是否走白烟图档（渲染端读；false=既有暖灰图路径） */
    public boolean isWhitePuff() {
        return this.whitePuff;
    }

    /**
     * 圆斑静态生成入口（E2b/停射余烟消费）：满员（{@link GatlingFxProfile#SMOKE_CAP}）返回
     * false 静默放弃，调用方无需处理；world 为 null 同样放弃。末期膨胀（swell）默认关闭
     * （委托 {@link #spawn} 膨胀重载传 1.0）。
     */
    public static boolean spawn(World world, double x, double y, double z, double motionX, double motionY,
        double motionZ, float scale0, float scale1, float alpha0, int lifeTicks, float r, float g, float b) {
        return spawn(world, x, y, z, motionX, motionY, motionZ, scale0, scale1, alpha0, lifeTicks, r, g, b, 1.0F);
    }

    /**
     * 圆斑生成入口·末期膨胀变体（v0.1.14 枪口烟消费）：末参 swell 因子经
     * {@link #setEndSwellFactor} 逐粒注入（传 1.0=与 14 参 {@link #spawn} 完全同行为）；
     * 停射余烟（GatlingClientFxManager.flushSmokeQueues）继续走 14 参入口，默认关闭不受连带。
     */
    public static boolean spawn(World world, double x, double y, double z, double motionX, double motionY,
        double motionZ, float scale0, float scale1, float alpha0, int lifeTicks, float r, float g, float b,
        float endSwellFactor) {
        return spawn(
            world,
            x,
            y,
            z,
            motionX,
            motionY,
            motionZ,
            scale0,
            scale1,
            alpha0,
            lifeTicks,
            r,
            g,
            b,
            endSwellFactor,
            false);
    }

    /**
     * 圆斑生成入口·白烟图档变体：末参 {@code whitePuff} 经 {@link #setWhitePuff} 逐粒注入
     * （传 false=与 15 参 {@link #spawn} 完全同行为）。火箭/导弹的浓重白烟走本入口取白档。
     */
    public static boolean spawn(World world, double x, double y, double z, double motionX, double motionY,
        double motionZ, float scale0, float scale1, float alpha0, int lifeTicks, float r, float g, float b,
        float endSwellFactor, boolean whitePuff) {
        SmokePuffEntity puff = new SmokePuffEntity(
            world,
            x,
            y,
            z,
            motionX,
            motionY,
            motionZ,
            scale0,
            scale1,
            alpha0,
            lifeTicks,
            r,
            g,
            b);
        puff.setEndSwellFactor(endSwellFactor);
        puff.setWhitePuff(whitePuff);
        return spawnIntoWorld(world, puff);
    }

    /**
     * 长条段静态生成入口（v0.1.14 弹道轨迹消费）：沿 axis*（单位向量）拉伸全长 streakLength
     * 的长条 billboard 烟段，与 {@link #spawn} 共享 SMOKE_CAP 预算与满员静默放弃语义。
     */
    public static boolean spawnStreak(World world, double x, double y, double z, float axisX, float axisY, float axisZ,
        float streakLength, double motionX, double motionY, double motionZ, float width0, float width1, float alpha0,
        int lifeTicks, float r, float g, float b) {
        return spawnStreak(
            world,
            x,
            y,
            z,
            axisX,
            axisY,
            axisZ,
            streakLength,
            motionX,
            motionY,
            motionZ,
            width0,
            width1,
            alpha0,
            lifeTicks,
            r,
            g,
            b,
            false);
    }

    /**
     * 长条段生成入口·白烟图档变体（{@code whitePuff=false} 时与 18 参入口逐字同行为）：
     * 弹道档位表的 HEAVY_WHITE 档经本入口取白档，TRACER/EM_ARC 档继续取暖灰图或不发烟。
     */
    public static boolean spawnStreak(World world, double x, double y, double z, float axisX, float axisY, float axisZ,
        float streakLength, double motionX, double motionY, double motionZ, float width0, float width1, float alpha0,
        int lifeTicks, float r, float g, float b, boolean whitePuff) {
        if (world == null) return false;
        if (streakWorld != world) {
            java.util.Arrays.fill(STREAK_CACHE, null);
            streakWorld = world;
        }
        int cx = (int) Math.floor(x / 4), cy = (int) Math.floor(y / 4), cz = (int) Math.floor(z / 4);
        for (int ix = -1; ix <= 1; ix++)
            for (int iy = -1; iy <= 1; iy++) for (int iz = -1; iz <= 1; iz++) for (int slot = 0; slot < 4; slot++) {
                SmokePuffEntity active = STREAK_CACHE[streakBucket(cx + ix, cy + iy, cz + iz) * 4 + slot];
                if (active == null || active.isDead
                    || active.worldObj != world
                    || active.ageTicks > active.lifeTicks * .6
                    || active.whitePuff != whitePuff
                    || active.scale0 != width0
                    || active.lifeTicks != lifeTicks) continue;
                if (VisualOverlapPolicy.sameTrail(
                    x - active.trailAnchorX,
                    y - active.trailAnchorY,
                    z - active.trailAnchorZ,
                    axisX,
                    axisY,
                    axisZ,
                    active.axisX,
                    active.axisY,
                    active.axisZ,
                    Math.min(streakLength, active.streakLength))) {
                    active.ageTicks = Math.min(active.ageTicks, active.lifeTicks / 4);
                    // A renewed segment stays on its firing lane instead of drifting away indefinitely.
                    active.posX += (x - active.posX) * .1;
                    active.posY += (y - active.posY) * .1;
                    active.posZ += (z - active.posZ) * .1;
                    return true;
                }
            }
        SmokePuffEntity puff = new SmokePuffEntity(
            world,
            x,
            y,
            z,
            motionX,
            motionY,
            motionZ,
            width0,
            width1,
            alpha0,
            lifeTicks,
            r,
            g,
            b,
            axisX,
            axisY,
            axisZ,
            streakLength);
        puff.setWhitePuff(whitePuff);
        if (!spawnIntoWorld(world, puff)) return false;
        puff.trailAnchorX = x;
        puff.trailAnchorY = y;
        puff.trailAnchorZ = z;
        int bucket = streakBucket(cx, cy, cz) * 4;
        int slot = (++streakSlot) & 3;
        for (int i = 0; i < 4; i++) if (STREAK_CACHE[bucket + i] == null || STREAK_CACHE[bucket + i].isDead) {
            slot = i;
            break;
        }
        STREAK_CACHE[bucket + slot] = puff;
        return true;
    }

    /** 预算门 + 落世界（spawn/spawnStreak 共用）：满员或 world null 静默放弃（性能预算优先） */
    private static boolean spawnIntoWorld(World world, SmokePuffEntity puff) {
        if (world == null || LIVE_COUNT.get() >= GatlingFxProfile.SMOKE_CAP) {
            return false; // 预算满员/无世界：静默放弃
        }
        if (!world.spawnEntityInWorld(puff)) {
            return false;
        }
        LIVE_COUNT.incrementAndGet();
        return true;
    }

    /** 当前活体计数（监控/自证用） */
    public static int liveCount() {
        return LIVE_COUNT.get();
    }

    /** 换维/卸载兜底：强制归零计数（残留烟团随 world 清理，无需逐个 setDead） */
    public static void resetCounter() {
        LIVE_COUNT.set(0);
        java.util.Arrays.fill(STREAK_CACHE, null);
        streakWorld = null;
    }

    /** Loaded ammunition/FX have no render-distance cap; the engine retains normal frustum culling. */
    @Override
    public boolean isInRangeToRenderDist(double distance) {
        return true; // Loaded ammunition and FX remain eligible regardless of camera distance.
    }

    /** 线性积分 + 到龄消散（无碰撞、无重力） */
    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        if (this.worldObj == null || this.ageTicks++ >= this.lifeTicks) {
            setDead();
            return;
        }
        this.posX += this.motionX;
        this.posY += this.motionY;
        this.posZ += this.motionZ;
    }

    /** 活体计数回收（setDead 可被多次调用：仅首次置位时递减） */
    @Override
    public void setDead() {
        boolean wasAlive = !this.isDead;
        super.setDead();
        if (wasAlive) {
            LIVE_COUNT.decrementAndGet();
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
