package com.miaokatze.gtsr.common.dimension.prosperity.entity;

import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * 汽雾萤（dim78 环境档，L7 / 02 册 §1.4 原设计回归）。
 * <p>
 * <b>为什么继承 {@link EntityAmbientCreature}</b>：{@code EnumCreatureType.ambient} 在 1.7.10 挂的
 * 就是 {@code EntityAmbientCreature}（蝙蝠那条链），换 {@code EntityCreature} 会被
 * {@code Entity.canSpawnHere} 判不允许落地。该基类 {@code isPeacefulCreature=true} ⇒
 * 和平难度不消失、不参与战斗。
 * <p>
 * <b>Phase 1 素材纪律</b>：零新素材 —— {@code ModelBat} + {@code textures/entity/bat.png}，
 * 渲染器并按原版蝙蝠的 0.35 缩放档做比例修正（原版 {@code RenderBat} 就是这么缩的，
 * 不缩会按模型原尺寸画出一只"门板大的萤火虫"）。皮肤与发光 FX 归 P10。
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

    public EntitySteamFirefly(World world) {
        super(world);
        this.setSize(0.3F, 0.3F);
        this.tasks.addTask(0, new EntityAISwimming(this));
        // 悬停游荡（02 册 §1.4"就近 8 格布朗运动"）：不能用原版 EntityAIWander —— 它的构造入参是
        // EntityCreature，而 ambient 档要求继承 EntityAmbientCreature（挂在 EntityLiving 链上）。
        // 故本档自带 EntityAIHover（下方），走 getMoveHelper 的同一套移动通道。
        this.tasks.addTask(1, new EntityAIHover(this));
        this.tasks.addTask(2, new EntityAILookIdle(this));
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
     * 就近 8 格布朗运动（02 册 §1.4 的"悬停游荡"最小承载）。
     * <p>
     * 只做"每 ~2s 换一个 8 格内的目标点 + 交 {@code getMoveHelper()} 走"，<b>不</b>引寻路
     * （ambient 链上的 {@code PathNavigate} 对飞行不做竖向判定，接了反而更怪）。真正的
     * "贴顶悬停 / 夜间成群"是行为面，本片不声称成立。
     */
    static final class EntityAIHover extends EntityAIBase {

        private static final int REPATH_INTERVAL_TICKS = 40;
        private static final double RANGE = 8.0D;

        private final EntitySteamFirefly fly;
        private int tickCounter;

        EntityAIHover(EntitySteamFirefly fly) {
            this.fly = fly;
            // 与 LookIdle/Swimming 互斥位对齐（同原版 EntityAIWander 的 MOVE|LOOK 位）
            this.setMutexBits(3);
        }

        @Override
        public boolean shouldExecute() {
            return true;
        }

        @Override
        public void startExecuting() {
            this.tickCounter = 0;
        }

        @Override
        public boolean continueExecuting() {
            return true;
        }

        @Override
        public void updateTask() {
            if (++this.tickCounter < REPATH_INTERVAL_TICKS) {
                return;
            }
            this.tickCounter = 0;
            final World world = this.fly.worldObj;
            final double x = this.fly.posX + (world.rand.nextDouble() - 0.5D) * RANGE;
            final double y = this.fly.posY + (world.rand.nextDouble() - 0.5D) * 3.0D;
            final double z = this.fly.posZ + (world.rand.nextDouble() - 0.5D) * RANGE;
            this.fly.getMoveHelper()
                .setMoveTo(x, y, z, 0.6D);
        }
    }
}
