package com.miaokatze.gtsr.common.dimension.testlab;

import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.DimensionManager;

import com.miaokatze.gtsr.common.dimension.framework.DimensionRegistrar;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.config.Config;

/** A fixed, persistent world, independent of the two survival dimensions. */
public final class TestLabDimension {

    private static GTSRDimensionDef definition;

    private TestLabDimension() {}

    public static void register() {
        if (!Config.testLabEnabled) return;
        definition = new GTSRDimensionDef(
            "testlab",
            "GTSR Machine Test Laboratory",
            0x544553544c41424cL,
            Config.testLabDimId,
            Config.testLabProviderId,
            () -> Config.testLabEnabled,
            TestLabWorldProvider.class,
            TestLabChunkProvider::new);
        definition.addBiome(BiomeGenBase.plains, 1);
        DimensionRegistrar.preInitDimensions(definition);
    }

    public static boolean enabled() {
        return definition != null && definition.isEnabled();
    }

    public static int id() {
        return enabled() ? definition.getResolvedDimId() : -1;
    }

    public static WorldServer world() {
        if (!enabled()) throw new IllegalStateException("测试维度已关闭或 ID 冲突；检查 testlab 配置及日志");
        WorldServer world = DimensionManager.getWorld(id());
        if (world == null) {
            DimensionManager.initDimension(id());
            world = DimensionManager.getWorld(id());
        }
        if (world == null || !(world.provider instanceof TestLabWorldProvider))
            throw new IllegalStateException("测试维度未成功加载");
        return world;
    }
}
