package com.miaokatze.gtsr.common.dimension.prosperity.entity;

import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;

/**
 * 齿轮鸽（dim78 被动档，L7 / 02 册 §1.4 原设计回归）。
 * <p>
 * <b>为什么继承 {@link EntityAnimal}</b>：{@code Entity.canSpawnHere(EnumCreatureType)} 的实现是
 * {@code type.getCreatureClass().isAssignableFrom(this.getClass())}，而 1.7.10 的
 * {@code EnumCreatureType.creature} 挂的是 {@code EntityAnimal}（不是 {@code EntityCreature}）——
 * 继承错基类会表现为"注册了、刷怪表里也有、但永不落地"，且无头冒烟测不到（wiki
 * {@code dedicated-server-entity-tick-freeze-gate.md} §1：无玩家维度实体被 1200t 冻结）。
 * <p>
 * <b>Phase 1 素材纪律</b>：零新素材 —— 模型与纹理都复用原版（{@code ModelChicken} +
 * {@code textures/entity/chicken.png}，见 {@code GTSRCreatureRenderers}），皮肤归 P10。
 * <p>
 * <b>本片刻意不接的东西</b>（都属"行为生效"面，无头不可测，见任务包铁律）：
 * <ul>
 * <li>繁殖：{@link #createChild} 只满足 {@code EntityAgeable} 的抽象契约，<b>不加</b>
 * {@code EntityAIMate}/{@code EntityAIEatGrass} ⇒ 名册里没有鸽的繁殖通道；</li>
 * <li>掉落 0-1 锈羽（02 册 §1.4）：锈羽物品尚未立项，本片不新增物品；</li>
 * <li>"齿轮咔哒"音效（02 册 §1.4）：音效资产禁止入本片，另派 {@code gtnh-sound-pipeline}。</li>
 * </ul>
 * <b>自定义数据</b>：{@link #DATA_SPAWN_BAND}（DataWatcher 索引 <b>16</b>，vanilla 链上最高只到
 * 12={@code EntityAgeable.IsBaby}，故不撞）= 出生时所在维内群系带下标，经
 * {@link GTSRBiomeAuthority#ordinalAt} 取（<b>不</b>读 Chunk byte 平面），供 P10 的 tint/FX 消费。
 */
public class EntityGearPigeon extends EntityAnimal {

    /** 自定义 DataWatcher 索引（判据 5：必须 ≥16；vanilla 在本链上用到 0/1/6/7/8/9/10/11/12）。 */
    private static final int DATA_SPAWN_BAND = 16;
    /** NBT 键（与 DataWatcher 同源，存档往返）。 */
    private static final String TAG_SPAWN_BAND = "GtsrSpawnBand";

    public EntityGearPigeon(World world) {
        super(world);
        // 02 册 §1.4 的体型档位（比原版鸡略小，读作"机械鸽"）
        this.setSize(0.5F, 0.6F);
        // 1.7.10 风格：AI 在构造期入列；isAIEnabled() 必须显式打开（EntityLiving 默认 false）
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAIWander(this, 1.0D));
        this.tasks.addTask(2, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
        this.tasks.addTask(3, new EntityAILookIdle(this));
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        // 02 册 §1.4：纯氛围生物 ⇒ 血量低于原版鸡（4）的一半、移速与鸡同档
        this.getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .setBaseValue(4.0D);
        this.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
            .setBaseValue(0.25D);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataWatcher.addObject(DATA_SPAWN_BAND, (byte) 0);
    }

    /** 出生时记录所在群系带（自然刷怪路径；身份只问 L1）。 */
    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        final IEntityLivingData out = super.onSpawnWithEgg(data);
        this.dataWatcher.updateObject(DATA_SPAWN_BAND, (byte) currentBandOrdinal(this.worldObj, this.posX, this.posZ));
        return out;
    }

    /** 出生带下标（0..roster-1；{@code -1} = 解析不到名册成员）。 */
    public int getSpawnBandOrdinal() {
        return this.dataWatcher.getWatchableObjectByte(DATA_SPAWN_BAND);
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
     * {@code EntityAgeable} 的抽象契约（繁殖通道本片未接，见类注释；保留实现以便后续切片
     * 只需往 {@link #tasks} 里加 {@code EntityAIMate}）。
     */
    @Override
    public EntityAgeable createChild(EntityAgeable other) {
        return new EntityGearPigeon(other.worldObj);
    }

    /** 群系带下标解析（三档共用口径；走 L1 唯一出口，降级态返回 -1 而不是伪造 0）。 */
    static int currentBandOrdinal(World world, double x, double z) {
        if (world == null || world.provider == null) {
            return -1;
        }
        final GTSRBiomeAuthority.Resolution resolved = GTSRBiomeAuthority.forDimension(world.provider.dimensionId)
            .ordinalAt((int) Math.floor(x), (int) Math.floor(z));
        return resolved == null ? -1 : resolved.ordinal;
    }
}
