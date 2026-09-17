package com.miaokatze.gtsr.common.dimension.framework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * 新维度抽象定义（dim1 S1 框架）。
 * <p>
 * 一个 def 描述一个待注册维度：def key、英文名、seedSalt、请求 dimId/providerId、
 * 群系权重表（S2/S6a 填充，S1 允许空列表运行——ChunkManager 回退原版 plains）、
 * WorldProvider 类与 ChunkProvider 工厂。
 * <p>
 * 注册解析结果由 {@link DimensionRegistrar} 写回：resolvedDimId/resolvedProviderId = -1
 * 表示该维度被禁用（总开关关闭或 ID 冲突），不注册、其余功能零影响。
 */
public class GTSRDimensionDef {

    /**
     * ChunkProvider 工厂：以（世界，世界种子+seedSalt）构造维度专属 IChunkProvider。
     * S1 传入 GTSRChunkProviderBase；S2/S6a 各自替换为专属 ChunkProvider 子类。
     */
    @FunctionalInterface
    public interface ChunkProviderFactory {

        IChunkProvider create(World world, long seed);
    }

    private final String key;
    private final String englishName;
    private final long seedSalt;
    private final BooleanSupplier masterSwitch;
    private final int requestedDimId;
    private final int requestedProviderId;
    private final Class<? extends WorldProvider> providerClass;
    private final ChunkProviderFactory chunkProviderFactory;

    /** 群系表（S1 为空；S2/S6a 经 {@link #addBiome} 填充，与权重表按下标一一对应）。 */
    private final List<BiomeGenBase> biomeTable = new ArrayList<>();
    private final List<Integer> biomeWeights = new ArrayList<>();

    /** 注册解析结果；-1 = 禁用/未注册（DimensionRegistrar 冲突检测失败或总开关关闭）。 */
    private int resolvedDimId = -1;
    private int resolvedProviderId = -1;

    public GTSRDimensionDef(String key, String englishName, long seedSalt, int requestedDimId, int requestedProviderId,
        BooleanSupplier masterSwitch, Class<? extends WorldProvider> providerClass,
        ChunkProviderFactory chunkProviderFactory) {
        this.key = key;
        this.englishName = englishName;
        this.seedSalt = seedSalt;
        this.masterSwitch = masterSwitch;
        this.requestedDimId = requestedDimId;
        this.requestedProviderId = requestedProviderId;
        this.providerClass = providerClass;
        this.chunkProviderFactory = chunkProviderFactory;
    }

    /** 注册期读取的总开关（如 () -> Config.planDimension.prosperityDimension）。 */
    public boolean isMasterSwitchEnabled() {
        return this.masterSwitch.getAsBoolean();
    }

    public String getKey() {
        return this.key;
    }

    public String getEnglishName() {
        return this.englishName;
    }

    public long getSeedSalt() {
        return this.seedSalt;
    }

    public int getRequestedDimId() {
        return this.requestedDimId;
    }

    public int getRequestedProviderId() {
        return this.requestedProviderId;
    }

    public Class<? extends WorldProvider> getProviderClass() {
        return this.providerClass;
    }

    public ChunkProviderFactory getChunkProviderFactory() {
        return this.chunkProviderFactory;
    }

    /** 群系权重表追加（S2/S6a 注册群系时调用；weight >= 1）。 */
    public void addBiome(BiomeGenBase biome, int weight) {
        this.biomeTable.add(biome);
        this.biomeWeights.add(weight);
    }

    public List<BiomeGenBase> getBiomeTable() {
        return Collections.unmodifiableList(this.biomeTable);
    }

    /** 与 {@link #getBiomeTable()} 下标对应的权重表。 */
    public int[] getBiomeWeights() {
        int[] weights = new int[this.biomeWeights.size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = this.biomeWeights.get(i);
        }
        return weights;
    }

    /** 解析后的实际维度 ID；-1 = 禁用。 */
    public int getResolvedDimId() {
        return this.resolvedDimId;
    }

    /** 解析后的实际 ProviderType ID；-1 = 禁用。 */
    public int getResolvedProviderId() {
        return this.resolvedProviderId;
    }

    /** 该维度是否已成功注册（resolvedDimId != -1）。 */
    public boolean isEnabled() {
        return this.resolvedDimId != -1;
    }

    /** 注册成功后由 DimensionRegistrar 写回（同包可见，外部不可伪造解析结果）。 */
    void markRegistered(int dimId, int providerId) {
        this.resolvedDimId = dimId;
        this.resolvedProviderId = providerId;
    }
}
