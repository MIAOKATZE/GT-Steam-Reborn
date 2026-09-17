package com.miaokatze.gtsr.common.dimension.framework;

import net.minecraft.world.biome.BiomeGenBase;

/**
 * 新维度群系抽象基类（dim1 S1 骨架，蓝本 GT5U BiomeEverglades.java:31-61）。
 * <p>
 * 构造统一完成：setBiomeName/setHeight/setTemperatureRainfall + 清空 4 个 spawn 列表
 * （裁剪范围无实体，plan §1 范围红线）。getBiomeGrassColor/getBiomeFoliageColor 为子类
 * 覆写点（S2 草色 = 平铺 RGB；默认沿用原版 ColorizerGrass 温湿双线性）。
 * <p>
 * 槽位纪律：super(biomeId) 构造即占 biomeList 槽且不可回退——调用方（S2/S6a 群系持有者）
 * 必须先经 {@link #isBiomeIdFree(int)} 校验，被占则跳过该群系降级运行（plan §1.2 S2/R3）。
 */
public abstract class GTSRBiomeBase extends BiomeGenBase {

    protected GTSRBiomeBase(int biomeId, String biomeName, Height height, float temperature, float rainfall) {
        super(biomeId);
        this.setBiomeName(biomeName);
        this.setHeight(height);
        this.setTemperatureRainfall(temperature, rainfall);
        this.spawnableMonsterList.clear();
        this.spawnableCreatureList.clear();
        this.spawnableWaterCreatureList.clear();
        this.spawnableCaveCreatureList.clear();
    }

    /** biomeList 槽位空闲校验（构造前调用；返回 true 才允许 new 该群系）。 */
    public static boolean isBiomeIdFree(int biomeId) {
        return biomeId >= 0 && biomeId < 256 && BiomeGenBase.getBiome(biomeId) == null;
    }

    /**
     * 草色覆写点：子类按需返回固定 RGB（签名对齐 vanilla getBiomeGrassColor(int, int, int)）。
     * 消费端（草方块 tint）走方块 colorMultiplier 邻域均值（S2 落地，BlockDarkWorldPollutedDirt 样板）。
     */
    // @Override public int getBiomeGrassColor(int x, int y, int z) { return ...; }

    /** 叶色覆写点：同 getBiomeGrassColor。 */
    // @Override public int getBiomeFoliageColor(int x, int y, int z) { return ...; }
}
