package com.miaokatze.gtsr.common.dimension.prosperity.entity;

import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * 渣脊猎手（dim78 敌对档，L7；plan §7.1 U-D 用户确认"三件含敌对"）。
 * <p>
 * <b>为什么继承 {@link EntityMob}</b>：{@code EnumCreatureType.monster} 的判定是
 * {@code IMob.class.isAssignableFrom(...)}，而 {@code EntityMob implements IMob} ⇒ 只有走这条链
 * 才能被 {@code Entity.canSpawnHere} 放行。顺带拿到原版敌对档的既有语义：和平难度自清
 * （{@code EntityMob.onUpdate}）、{@code attackDamage} 属性注册（{@code EntityMob:230}）、
 * 5 点经验值。
 * <p>
 * <b>Phase 1 素材纪律</b>：零新素材 —— {@code ModelSkeleton} +
 * {@code textures/entity/skeleton/skeleton.png}，皮肤归 P10。
 * <p>
 * <b>本片刻意不接</b>：掉落表（不给原版骨头以外的东西，也不新增物品）、远程攻击、
 * 群聚与"结构上封顶 2 只"的行为面（见 {@link GTSRCreatureRoster#structureLinkCap}）。
 * 敌对生物会真的打人 ⇒ 这是本片最需要实机目检的一档。
 */
public class EntitySlagRidgeHunter extends EntityMob {

    /** 自定义 DataWatcher 索引（判据 5：≥16）。 */
    private static final int DATA_SPAWN_BAND = 16;
    private static final String TAG_SPAWN_BAND = "GtsrSpawnBand";

    public EntitySlagRidgeHunter(World world) {
        super(world);
        this.setSize(0.6F, 1.9F);
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(2, new EntityAIAttackOnCollide(this, EntityPlayer.class, 1.0D, false));
        this.tasks.addTask(4, new EntityAIWander(this, 1.0D));
        this.tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(7, new EntityAILookIdle(this));
        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        this.targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        // 低于原版骷髅（20 血 / 4 点近战）：遗迹里的"渣脊看门狗"，不是新 boss
        this.getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .setBaseValue(14.0D);
        this.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
            .setBaseValue(0.26D);
        this.getEntityAttribute(SharedMonsterAttributes.attackDamage)
            .setBaseValue(3.0D);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataWatcher.addObject(DATA_SPAWN_BAND, (byte) 0);
    }

    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        final IEntityLivingData out = super.onSpawnWithEgg(data);
        this.dataWatcher.updateObject(
            DATA_SPAWN_BAND,
            (byte) EntityGearPigeon.currentBandOrdinal(this.worldObj, this.posX, this.posZ));
        return out;
    }

    /** 出生带下标（口径同齿轮鸽）。 */
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
}
