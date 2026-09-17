package com.miaokatze.gtsr.common.dimension.framework;

import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * 新维度 WorldProvider 模板（dim1 S1，蓝本 GT5U WorldProviderMod.java:16-32）。
 * <p>
 * registerWorldChunkManager/createChunkGenerator 为 final 模板：ChunkManager 由
 * {@link GTSRWorldChunkManager}（def 群系权重表驱动，不走 GenLayer）构造，ChunkGenerator
 * 由 def 工厂以（世界种子 + def.seedSalt）构造。getFogColor/getSkyColor 为子类覆写点
 * （S2 微暖橙雾 / S6a 暗紫系按群系取色）；calculateCelestialAngle 提供固定太阳角钩子；
 * canRespawnHere=false（死亡回主世界，plan §1.2 口径）。
 */
public abstract class GTSRWorldProviderBase extends WorldProvider {

    /** 运行期经 DimensionRegistrar 回查的 def；维度 ID 未在注册表时为 null（防御性降级运行）。 */
    protected GTSRDimensionDef def;

    @Override
    protected final void registerWorldChunkManager() {
        this.def = DimensionRegistrar.defForDimension(this.dimensionId);
        this.worldChunkMgr = new GTSRWorldChunkManager(this.worldObj.getSeed(), this.def);
        this.isHellWorld = false;
        this.hasNoSky = false;
    }

    @Override
    public final IChunkProvider createChunkGenerator() {
        long seed = this.worldObj.getSeed() + (this.def != null ? this.def.getSeedSalt() : 0L);
        if (this.def != null) {
            return this.def.getChunkProviderFactory()
                .create(this.worldObj, seed);
        }
        // def 缺失（维度未在本会话注册却仍被创建）——防御性回退到基础地形
        return new GTSRChunkProviderBase(this.worldObj, seed);
    }

    /** 客户端雾色覆写点（S2/S6a 定真实配色）。 */
    @Override
    public abstract Vec3 getFogColor(float partialTicks, float renderPass);

    /** 客户端天色覆写点（S2/S6a 定真实配色；S6a 可按当前群系三变体取色）。 */
    @Override
    public abstract Vec3 getSkyColor(Entity cameraEntity, float partialTicks);

    /**
     * 固定太阳角钩子：子类返回非 null 则昼夜恒定于该角度（S6a 无太阳 = 0.75F），
     * 返回 null 沿用原版时间推进计算。
     */
    protected Float fixedCelestialAngle() {
        return null;
    }

    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks) {
        Float fixed = fixedCelestialAngle();
        return fixed != null ? fixed.floatValue() : super.calculateCelestialAngle(worldTime, partialTicks);
    }

    @Override
    public boolean canRespawnHere() {
        return false;
    }

    @Override
    public boolean isSurfaceWorld() {
        return true;
    }

    @Override
    public String getDimensionName() {
        return this.def != null ? this.def.getEnglishName() : "GTSR Dimension";
    }

    /**
     * S1 注册占位实现：中性雾/天色，仅保证维度注册链完整可运行。
     * S2/S6a 落地 WorldProviderProsperityRuins / WorldProviderShatteredLands 后，
     * def 的 providerClass 即替换为专属实现，本占位类随之退役。
     */
    public static class Skeleton extends GTSRWorldProviderBase {

        @Override
        public Vec3 getFogColor(float partialTicks, float renderPass) {
            return Vec3.createVectorHelper(0.2D, 0.2D, 0.25D);
        }

        @Override
        public Vec3 getSkyColor(Entity cameraEntity, float partialTicks) {
            return Vec3.createVectorHelper(0.2D, 0.2D, 0.3D);
        }
    }
}
