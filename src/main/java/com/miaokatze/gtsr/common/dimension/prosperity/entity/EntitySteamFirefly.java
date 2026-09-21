package com.miaokatze.gtsr.common.dimension.prosperity.entity;

import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * 汽雾萤（dim78 环境档，L7 / 02 册 §1.4 原设计回归）。
 * <p>
 * <b>为什么继承 {@link EntityAmbientCreature}</b>：{@code EnumCreatureType.ambient} 在 1.7.10 挂的
 * 就是 {@code EntityAmbientCreature}（蝙蝠那条链），换 {@code EntityCreature} 会被
 * {@code Entity.canSpawnHere} 判不允许落地。该基类 {@code isPeacefulCreature=true} ⇒
 * 和平难度不消失、不参与战斗。
 * <p>
 * <b>素材纪律（D2 后）</b>：零新素材 —— 模型是我方复刻件 {@code GTSRModelSteamFirefly}
 * （原版 {@code ModelBat} 的 {@code render()} 第一句无条件 {@code (EntityBat)} ⇒ 上屏即崩，C-01），
 * 几何/UV/画布与原版逐位一致，故贴图仍复用 {@code textures/entity/bat.png}；渲染器按原版蝙蝠的
 * 0.35 缩放档做比例修正（不缩会按模型原尺寸画出一只"门板大的萤火虫"）。自有皮肤归 D1，
 * <b>画布钉死 64×64</b>（{@code CreatureSpawnAuthorityCheck} I2 组三方对钉）。
 * <p>
 * <b>本片刻意不接</b>：夜间发微光的 client 侧粒子（02 册 §1.4 的 {@code FumeFireflyFX}，
 * 同 {@code GTSRSingularityFX} 挂法）与音效——都是"行为/表现生效"面，无头测不到，另派。
 * <p>
 * <b>自定义数据</b>：{@link #DATA_SPAWN_BAND}=16（同齿轮鸽口径）与 {@link #DATA_FLICKER_PHASE}=17
 * （明暗相位 0..255，供 P10 的发光/tint 消费，<b>本片只写不消费</b>）。
 */
public class EntitySteamFirefly extends EntityAmbientCreature {

    /** 自定义 DataWatcher 索引（判据 5：≥16；本链 vanilla 最高用到 11={@code EntityLiving}）。 */
    private static final int DATA_SPAWN_BAND = 16;
    /** 明暗相位（第二个自定义槽，同样 ≥16；本片只负责写入与同步，不负责渲染消费）。 */
    private static final int DATA_FLICKER_PHASE = 17;
    private static final String TAG_SPAWN_BAND = "GtsrSpawnBand";

    /** 换目标点的间隔 tick（≈2s，与旧 {@code EntityAIHover} 同一口径）。 */
    private static final int REPATH_INTERVAL_TICKS = 40;
    /** 布朗运动的水平方框边长（格）：±4 格 ⇒ 02 册 §1.4 的"就近 8 格"。 */
    private static final double WANDER_RANGE = 8.0D;
    /** 布朗运动的竖向幅度（格）：±1.5 格（原 {@code EntityAIHover} 的 3.0 见方）。 */
    private static final double WANDER_LIFT = 3.0D;
    /** 到位半径平方（格²）：比原版蝙蝠的 {@code < 4.0F}（{@code EntityBat.java:167}）同形。 */
    private static final double REPATH_DISTANCE_SQ = 4.0D;
    /** 横向目标速度的渐近系数（原版蝙蝠实测同值 0.1，{@code EntityBat.java:175-177}）。 */
    private static final double STEER_RATE = 0.1D;
    /**
     * 把申报的 {@code movementSpeed} 折成"悬停巡航目标速度"的比例。
     * <p>
     * 不能直接拿属性值当目标速度：空中每 tick 有 {@code motionX *= f2}（{@code f2 = 0.91}，
     * {@code EntityLivingBase.java:1706-1708}），"朝目标速度 v 渐近 + 每 tick 打 0.91"的平衡解是
     * {@code 0.5v} ⇒ 0.30 会跑成 ≈3 格/秒。折 0.2 ⇒ 平衡 ≈0.6 格/秒。
     */
    private static final double HOVER_SPEED_SCALE = 0.2D;
    /** 高度误差 → 竖向速度增量 的比例增益（悬停控制律的一阶项）。 */
    private static final double LIFT_GAIN = 0.004D;
    /**
     * 逐 tick 抵消重力的前馈量：原版在积分完位置之后做 {@code motionY -= 0.08D}
     * （{@code EntityLivingBase.java:1703}），同 tick 内加回等量即可悬停。
     */
    private static final double GRAVITY_TRIM = 0.08D;
    /** 竖向速度夹位（格/tick）：防比例项在 1.5 格高差上冲出失稳的爬升。 */
    private static final double MAX_LIFT_SPEED = 0.12D;

    /** 当前游荡目标点（出生时未定，首帧即掷）。 */
    private double wanderX;
    private double wanderY;
    private double wanderZ;
    private boolean hasWanderTarget;
    private int repathCounter = REPATH_INTERVAL_TICKS;

    public EntitySteamFirefly(World world) {
        super(world);
        this.setSize(0.3F, 0.3F);
        // D2 的 C-05/C-15 一并收口：位移不再走 tasks，而由 updateAITasks 直投 motion（蝙蝠范式），
        // 于是原先"每 40t 喂一次 moveHelper"的 EntityAIHover 整个消失，长期持有 LOOK 互斥位、
        // 把 LookIdle 永久挤掉的那条死锁也一起消失（清单 C-15）。
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAILookIdle(this));
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .setBaseValue(2.0D);
        // 移速属性是悬停速度的唯一真值源（updateAITasks 直接读它，不再另立常量）
        this.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
            .setBaseValue(0.30D);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataWatcher.addObject(DATA_SPAWN_BAND, (byte) 0);
        this.dataWatcher.addObject(DATA_FLICKER_PHASE, (byte) 0);
    }

    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        final IEntityLivingData out = super.onSpawnWithEgg(data);
        this.dataWatcher.updateObject(
            DATA_SPAWN_BAND,
            (byte) EntityGearPigeon.currentBandOrdinal(this.worldObj, this.posX, this.posZ));
        // 相位按实体 id 散开（不引 World 读取，保证客户端/服务端一致）
        this.dataWatcher.updateObject(DATA_FLICKER_PHASE, (byte) (this.getEntityId() & 0xFF));
        return out;
    }

    /** 出生带下标（口径同齿轮鸽）。 */
    public int getSpawnBandOrdinal() {
        return this.dataWatcher.getWatchableObjectByte(DATA_SPAWN_BAND);
    }

    /** 明暗相位 0..255（P10 的 FX 消费位；本片只申报与同步）。 */
    public int getFlickerPhase() {
        return this.dataWatcher.getWatchableObjectByte(DATA_FLICKER_PHASE) & 0xFF;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setByte(TAG_SPAWN_BAND, (byte) this.getSpawnBandOrdinal());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey(TAG_SPAWN_BAND)) {
            this.dataWatcher.updateObject(DATA_SPAWN_BAND, tag.getByte(TAG_SPAWN_BAND));
        }
    }

    /**
     * 就近布朗运动的<b>位移来源</b>（D2 修 C-05）。
     * <p>
     * 旧写法把目标点喂给 {@code getMoveHelper().setMoveTo(...)} 了事，而那是一条<b>零位移</b>通道：
     * {@code EntityMoveHelper.java:49-74} 全文只写 {@code setMoveForward(0)}（:51）、
     * {@code rotationYaw}（:65）、{@code setAIMoveSpeed}（:66）与"脚下贴脸时跳一下"（:68-71），
     * 一次都不碰 {@code motion*}/{@code pos*}；真正把 {@code moveForward} 变成位移的
     * {@code navigator}（{@code EntityLiving.java:616-617}）本档也没接 ⇒ 两路全空，萤只会原地转头。
     * <b>旧类注释"走 getMoveHelper 的同一套移动通道"这一前提已被证伪</b>（清单 C-05），本文件按
     * 下面这条改：横向沿用原版蝙蝠的"朝符号方向的目标速度渐近"（{@code EntityBat.java:175-177}），
     * 竖向改成"高度误差比例 + 逐 tick 精确抵消重力"的悬停控制——蝙蝠那套"目标速度 0.7 + 0.6 阻尼"
     * 的平衡解仍是缓慢下沉（它靠悬挂落地兜住），而萤火虫只有 2 点血、一摔就死，不兜这个底。
     * 不引 {@code PathNavigateFlying} 的理由同清单：0.3 格见方的虫做 A* 代价与风险都不划算，
     * 且竖向 ±1.5 格的目标点只有直投 motion 才表达得出来。
     * <p>
     * <b>本方法只在服务端跑</b>（{@code EntityLivingBase.java:1978-1986} 的 {@code isClientWorld()}
     * 实为 {@code !worldObj.isRemote}，见 {@code :2224-2227}）⇒ 位置/速度仍按
     * {@code registerModEntity} 申报的 {@code sendsVelocityUpdates=true} 下发客户端，与原版蝙蝠一致。
     * <b>轨迹手感（会不会穿墙、贴顶、卡方块、绕圈）离线不可证，见回执的"仍需实机"清单。</b>
     */
    @Override
    protected void updateAITasks() {
        super.updateAITasks();
        this.repickWanderTargetIfNeeded();
        final double d0 = this.wanderX - this.posX;
        final double d1 = this.wanderY - this.posY;
        final double d2 = this.wanderZ - this.posZ;
        final double hover = this.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
            .getAttributeValue() * HOVER_SPEED_SCALE;
        this.motionX += (Math.signum(d0) * hover - this.motionX) * STEER_RATE;
        this.motionZ += (Math.signum(d2) * hover - this.motionZ) * STEER_RATE;
        this.motionY += d1 * LIFT_GAIN + GRAVITY_TRIM;
        if (this.motionY > MAX_LIFT_SPEED) {
            this.motionY = MAX_LIFT_SPEED;
        } else if (this.motionY < -MAX_LIFT_SPEED) {
            this.motionY = -MAX_LIFT_SPEED;
        }
        // 模型朝向跟着实际速度走（原版蝙蝠 EntityBat.java:178-179 同形）。与蝙蝠不同：这里不置
        // moveForward——它是"走路推力"的输入，本档的位移已全部由上面三行承担，置了反而会叠加成两倍速。
        final float wanted = (float) (Math.atan2(this.motionZ, this.motionX) * 180.0D / Math.PI) - 90.0F;
        this.rotationYaw += MathHelper.wrapAngleTo180_float(wanted - this.rotationYaw);
    }

    /**
     * 掷目标点：每 {@code REPATH_INTERVAL_TICKS} tick 一次，或已到位（水平距离² 小于
     * {@code REPATH_DISTANCE_SQ}）时提前换。掷法表达式与旧 {@code EntityAIHover} 逐字符相同，
     * 故"就近 8 格 / 竖向 ±1.5 格"两处口径一字未变。
     */
    private void repickWanderTargetIfNeeded() {
        final double dx = this.wanderX - this.posX;
        final double dz = this.wanderZ - this.posZ;
        final boolean arrived = this.hasWanderTarget && dx * dx + dz * dz < REPATH_DISTANCE_SQ;
        if (this.hasWanderTarget && ++this.repathCounter < REPATH_INTERVAL_TICKS && !arrived) {
            return;
        }
        this.repathCounter = 0;
        this.hasWanderTarget = true;
        this.wanderX = this.posX + (this.rand.nextDouble() - 0.5D) * WANDER_RANGE;
        this.wanderY = this.posY + (this.rand.nextDouble() - 0.5D) * WANDER_LIFT;
        this.wanderZ = this.posZ + (this.rand.nextDouble() - 0.5D) * WANDER_RANGE;
    }

    /**
     * 不落足走路，也不积累坠落：两条都是原版蝙蝠的既有覆写
     * （{@code EntityBat.java:194-197} 与 {@code :202,208} 的空实现）。
     * <p>
     * 为什么必须一起接：{@code EntityLiving.updateFallState} 会把 {@code fallDistance} 累到落地时
     * 一次结算，而本档 {@code maxHealth = 2.0} ⇒ 清单 C-05 附注里"悬空点一摔就死"就是这条路径。
     * 会飞的虫不吃坠落伤害是原版飞行档口径，不是本仓新设。
     */
    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    protected void fall(float distance) {}

    @Override
    protected void updateFallState(double distanceFallenThisTick, boolean isOnGround) {}
}
