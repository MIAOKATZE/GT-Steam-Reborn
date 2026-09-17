package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkClampedSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * 繁荣维度世界生成编排器（dim1 S4a，plan §1.2 S4a / 02 §8.2 代码 20 裁剪版）：
 * 仅 dim78 生效（维度过滤范式同 WorldGenRunawaySingularity.java:28-31，dimId 走 Config 可改口径）。
 * generate 顺序 = 城市段（<b>S4b 挂点，本切片留空调用</b>）→ 残缺机器（1/prosperityMachineChance ×
 * 群系机器权重）→ 地表散布（预算 32 × 群系散布权重）。矿洞/矿坑/矿脉在用户裁剪范围外（不实现）。
 * <p>
 * 群系权重按 biomeId int 查表（相对 {@link Config#prosperityBiomeIdStart}， ProsperityAirLookup 同款
 * 不 import 群系类）；每 chunk 一个 {@link ChunkClampedSink}（协议层钳制 + 越界计数），
 * 机器与散布共用同一 Sink。构造时向 {@link StructureRegistry} 登记 5 机型变体并输出注册证据日志
 * （plan S4a 验收：runServer 日志 grep 锚点）。
 */
public class ProsperityWorldGenerator implements IWorldGenerator {

    /**
     * 群系机器权重表（02 §1.1 结构权重列·残缺机器）：下标 = biomeId - prosperityBiomeIdStart
     * → 锈蚀草原 0.7 / 齿轮森林 1.2 / 黄铜荒漠 0.9 / 起雾沼泽 0.8。
     */
    public static final float[] MACHINE_WEIGHTS = { 0.7F, 1.2F, 0.9F, 0.8F };

    /**
     * 群系散布权重表（02 §1.1 结构权重列·散布）：锈蚀草原 1.2 / 齿轮森林 1.0 / 黄铜荒漠 1.5 / 起雾沼泽 1.1。
     */
    public static final float[] SCATTER_WEIGHTS = { 1.2F, 1.0F, 1.5F, 1.1F };

    /** 注册证据日志只发一次（幂等构造防御）。 */
    private static volatile boolean evidenceLogged;

    public ProsperityWorldGenerator() {
        RuinedMachinePlacer.registerVariants();
        if (evidenceLogged) {
            return;
        }
        evidenceLogged = true;
        GTSteamReborn.LOG.info(
            "[GTSR] prosperity worldgen registered: dimId={} machines=5 {} scatterBudget=32/chunk machineChance=1/{}",
            Config.prosperityDimId,
            StructureRegistry.names(),
            Config.prosperityMachineChance);
    }

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator,
        IChunkProvider chunkProvider) {
        // 维度过滤：仅繁荣维度（Config 可改口径；维度被禁用时该 id 不存在，此守卫为双保险）
        if (world.provider.dimensionId != Config.prosperityDimId) {
            return;
        }
        if (!Config.planDimension.prosperityDimension) {
            return;
        }
        final long worldSeed = world.getSeed();
        // 每 chunk 一个钳制 Sink：越界写入协议层丢弃并计数（每 256 chunk 汇总日志，02 代码 15 越界瑕疵修复）
        final BlockSink sink = new ChunkClampedSink(world, chunkX, chunkZ);

        // —— 1. 古代城（S4b 挂点：CityPlanner 渲染交集切片；本切片留空调用，plan §2 S4a）——
        placeCities(world, worldSeed, chunkX, chunkZ, sink);

        // —— 2. 残缺机器（1/prosperityMachineChance × 群系机器权重，'C' 位=积碳壳，无 TE）——
        RuinedMachinePlacer
            .placeAll(world, worldSeed, chunkX, chunkZ, biomeWeight(world, chunkX, chunkZ, MACHINE_WEIGHTS), sink);

        // —— 3. 地表散布（预算 32 × 群系散布权重；最低优先级，只落空气/锈变地表）——
        ProsperitySurfaceScatter
            .scatter(world, worldSeed, chunkX, chunkZ, biomeWeight(world, chunkX, chunkZ, SCATTER_WEIGHTS), sink);
    }

    /**
     * 古代城调用点（<b>S4b 挂点</b>，本切片留空）：S4b 落地 CityPlanner 纯函数渲染——
     * chunk C 检索 footprint 与 C 相交的城市地块，只把落在 C 内的方块交给 sink（plan §3.1）。
     */
    @SuppressWarnings("unused")
    private void placeCities(World world, long worldSeed, int chunkX, int chunkZ, BlockSink sink) {}

    /** 群系权重查表（biomeId int 相对偏移；非本维度群系/未知群系回退 1.0）。 */
    private static float biomeWeight(World world, int chunkX, int chunkZ, float[] table) {
        final BiomeGenBase biome = world.getBiomeGenForCoords((chunkX << 4) + 8, (chunkZ << 4) + 8);
        if (biome == null) {
            return 1.0F;
        }
        final int index = biome.biomeID - Config.prosperityBiomeIdStart;
        return index >= 0 && index < table.length ? table[index] : 1.0F;
    }
}
