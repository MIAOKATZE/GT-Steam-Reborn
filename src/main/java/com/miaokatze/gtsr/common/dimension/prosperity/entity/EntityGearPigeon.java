package com.miaokatze.gtsr.common.dimension.prosperity.entity;

import net.minecraft.block.Block;
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
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;

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
 * <p>
 * ═══ D2 修的 C-03：为什么必须覆写 {@link #getCanSpawnHere()} ═══
 * {@code EntityAnimal.java:322-327} 的原文是
 * {@code this.worldObj.getBlock(i, j - 1, k) == Blocks.grass && getFullBlockLightValue(i, j, k) > 8 && super…}，
 * 而 dim78 四个群系的 topBlock 全是自研块（{@code BiomeRustedSteppe.java:37} 等），
 * 全仓 {@code Blocks.grass} 零命中（唯一出现是 {@code GTSRChunkProviderBase.java:319} 的一句注释）
 * ⇒ 那一行硬门在本维<b>恒 false</b>，名册里基权最高（12）、乘子最厚（草原 3 / 齿轮林 5）的一档
 * 于是永不自然刷出。{@code world/SpawnerAnimals.java:238-295 performWorldGenSpawning}（唯一能绕过
 * 该门的生成期通道）我方 provider 从未调用 ⇒ 没有第二条路。
 * <p>
 * <b>修法口径</b>：把"草"换成<b>本维地表声明真值</b>（{@link SurfaceGate#landableTops} 白名单，
 * 与装饰/结构/散布/机器四层同一个门），<b>不</b>去动 {@code Blocks.grass} 的注册、<b>不</b>往主世界塞草，
 * 光照门（{@code > 8}）原值保留 ⇒ 主世界行为零改动（本类只在 dim78 名册里，dim79 恒零见名册注释）。
 * 原版那三段落地安全谓词（{@code EntityLiving.java:742}）之所以要<b>显式复算</b>而不是
 * {@code super.getCanSpawnHere()}：Java 无法跳过一层 super，而 {@code EntityAnimal} 那层正是缺陷本身。
 * 复算的正确性由 {@code CreatureSpawnAuthorityCheck} 的 I4 组双向钉（我方文件含这三段 + 上游
 * {@code EntityLiving} 那一行仍是这三段），上游一改即红，不会静默漂移。
 */
public class EntityGearPigeon extends EntityAnimal {

    /** 自定义 DataWatcher 索引（判据 5：必须 ≥16；vanilla 在本链上用到 0/1/6/7/8/9/10/11/12）。 */
    private static final int DATA_SPAWN_BAND = 16;
    /** NBT 键（与 DataWatcher 同源，存档往返）。 */
    private static final String TAG_SPAWN_BAND = "GtsrSpawnBand";
    /**
     * 本维键（与 {@code SurfaceGate} 的 dim78 声明同一真值；名册的 dim79 恒零保证本类不在碎地被构造，
     * 见 {@code GTSRCreatureRoster#bandMultiplier}）。
     */
    private static final String DIM_KEY = SurfaceGate.DIM78;
    /** 原版 {@code EntityAnimal} 的光照门槛（{@code EntityAnimal.java:327} 的 {@code > 8}，原值保留）。 */
    private static final int MIN_SPAWN_LIGHT = 8;

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

    /**
     * 本维地表门（<b>离线可复算入口</b>；{@code CreatureSpawnAuthorityCheck} I4 组按名反射调它，
     * 逐条钉"名册白名单成员全部放行、原版草一律拒绝"）。
     * <p>
     * 三参形式（显式集合 + 维度键）是 {@code SurfaceGateUnifyCheck} E 组为生产调用点申报的唯一形态，
     * 本仓不得再出现第二套地表比较表达式。
     */
    public static boolean canSpawnOnGround(Block ground) {
        return SurfaceGate.isNaturalTop(DIM_KEY, SurfaceGate.landableTops(DIM_KEY), ground);
    }

    /**
     * 落地判定（D2 修 C-03）。三段构成与原版同形、只是将第一段的"草"换成本维地表真值：
     * <ol>
     * <li>脚下方块 ∈ {@link SurfaceGate#landableTops}(dim78)（替代 {@code == Blocks.grass}）；</li>
     * <li>该格光照 &gt; 8（原值，见 {@link #MIN_SPAWN_LIGHT}）；</li>
     * <li>{@code EntityLiving.java:742} 的三段落地安全谓词逐条复算（无实体碰撞 / 无方块碰撞盒 /
     * 不在液体里）——不能靠 {@code super}，因为中间那层 {@code EntityAnimal} 就是缺陷本身。</li>
     * </ol>
     */
    @Override
    public boolean getCanSpawnHere() {
        final int i = MathHelper.floor_double(this.posX);
        final int j = MathHelper.floor_double(this.boundingBox.minY);
        final int k = MathHelper.floor_double(this.posZ);
        return canSpawnOnGround(this.worldObj.getBlock(i, j - 1, k))
            && this.worldObj.getFullBlockLightValue(i, j, k) > MIN_SPAWN_LIGHT
            && this.worldObj.checkNoEntityCollision(this.boundingBox)
            && this.worldObj.getCollidingBoundingBoxes(this, this.boundingBox)
                .isEmpty()
            && !this.worldObj.isAnyLiquid(this.boundingBox);
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
