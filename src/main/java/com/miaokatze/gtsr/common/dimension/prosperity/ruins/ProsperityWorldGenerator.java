package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkClampedSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityBlockResolver;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlan;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CitySliceSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * 繁荣维度世界生成编排器（dim1 S4a，plan §1.2 S4a / 02 §8.2 代码 20 裁剪版）：
 * 仅 dim78 生效（维度过滤范式同 WorldGenRunawaySingularity.java:28-31，dimId 走 Config 可改口径）。
 * generate 顺序 = 城市段（<b>S4b 挂点，本切片留空调用</b>）→ 城外中型废墟 outpost（1/64，S-A5；
 * 命中则跳过机器）→ 残缺机器（1/prosperityMachineChance × 群系机器权重）→ 地表散布（预算 64 ×
 * 群系散布权重）。矿洞/矿坑/矿脉在用户裁剪范围外（不实现）。
 * <p>
 * 群系权重按 <b>L1 维内名册下标</b>查表（{@link GTSRBiomeAuthority#ordinalAt(int, int)} 的
 * {@code ordinal}，P1 收口：不再读 {@code Chunk} 的 byte biome id、不再做 {@code id - idStart} 减法，
 * 也不 import 群系类做 instanceof）；每 chunk 一个 {@link ChunkClampedSink}（协议层钳制 + 越界计数），
 * 机器与散布共用同一 Sink。构造时向 {@link StructureRegistry} 登记 5 机型变体并输出注册证据日志
 * （plan S4a 验收：runServer 日志 grep 锚点）。
 */
public class ProsperityWorldGenerator implements IWorldGenerator {

    /**
     * 群系机器权重表（02 §1.1 结构权重列·残缺机器）：下标 = L1 维内名册下标
     * （{@code GTSRBiomeAuthority.BiomeId.rosterIndex()}，即注册顺序 锈蚀草原/齿轮森林/黄铜荒漠/起雾沼泽）
     * → 0.7 / 1.2 / 0.9 / 0.8。
     */
    public static final float[] MACHINE_WEIGHTS = { 0.7F, 1.2F, 0.9F, 0.8F };

    /**
     * 群系散布权重表（02 §1.1 结构权重列·散布，下标口径同 {@link #MACHINE_WEIGHTS}）：
     * 锈蚀草原 1.2 / 齿轮森林 1.0 / 黄铜荒漠 1.5 / 起雾沼泽 1.1。
     */
    public static final float[] SCATTER_WEIGHTS = { 1.2F, 1.0F, 1.5F, 1.1F };

    /** 注册证据日志只发一次（幂等构造防御）。 */
    private static volatile boolean evidenceLogged;

    public ProsperityWorldGenerator() {
        RuinedMachinePlacer.registerVariants();
        CityVariants.registerVariants();
        ProsperityOutpostPlacer.registerVariants();
        if (evidenceLogged) {
            return;
        }
        evidenceLogged = true;
        GTSteamReborn.LOG.info(
            "[GTSR] prosperity worldgen registered: dimId={} machines=5 outposts=6 {} scatterBudget=64/chunk"
                + " machineChance=1/{} outpostChance=1/{}",
            Config.prosperityDimId,
            StructureRegistry.names(),
            Config.prosperityMachineChance,
            Config.prosperityOutpostChance);
        GTSteamReborn.LOG.info(
            "[GTSR] prosperity city variants: {} registered (cell={} chance={}%)",
            CityVariants.ALL.length,
            CityPlanner.CITY_CELL,
            Config.prosperityCityChance);
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

        // —— 1. 古代城（S4b）：3×3 cell 检索邻域城市，仅渲染与 C 相交的交集切片（plan §3.1）——
        final CityPlan[] cities = CityPlanner.citiesNear(worldSeed, chunkX, chunkZ);
        placeCities(worldSeed, chunkX, chunkZ, cities, sink);

        // 城市缓冲窗（半径+1 chunk）内跳过散布与残缺机器（plan §3.4：城市本身即"结构密度拉满"，
        // 二者混叠只脏；窗判定与渲染检索同源 CityPlanner.citiesNear，跨 chunk 一致）
        if (cities.length > 0) {
            return;
        }

        // —— 2. 城外中型废墟（S-A5，plan §12 修订 7/8）：1/prosperityOutpostChance 掷骰；同 chunk
        // 互斥掷骰 = 先 outpost，命中则本 chunk 跳过残缺机器（防 footprint 撞格；散布/装饰仍照常）——
        if (!ProsperityOutpostPlacer.placeAll(world, worldSeed, chunkX, chunkZ, sink)) {
            // —— 3. 残缺机器（1/prosperityMachineChance × 群系机器权重，'C' 位=积碳壳，无 TE）——
            RuinedMachinePlacer
                .placeAll(world, worldSeed, chunkX, chunkZ, biomeWeight(world, chunkX, chunkZ, MACHINE_WEIGHTS), sink);
        }

        // —— 4. 地表散布（预算 64 × 群系散布权重；最低优先级，只落自然锈变地表+空气让行）——
        ProsperitySurfaceScatter
            .scatter(world, worldSeed, chunkX, chunkZ, biomeWeight(world, chunkX, chunkZ, SCATTER_WEIGHTS), sink);

        // —— 5. 自然区装饰（S-A1，plan §12 修订 5：草丛 2-4/锈树 1/16/碎石 0-2 堆，频率常量
        // 登记 ProsperityDecorPlacer 类注释；城 buffer 窗由上方 :90-92 return 天然保证）——
        ProsperityDecorPlacer.decorate(world, worldSeed, chunkX, chunkZ, sink);
    }

    /**
     * 古代城渲染（S4b，plan §3.1 渲染协议 + §3.2 街道/地块）：
     * <ul>
     * <li>Sink 链 = CitySliceSink（交集切片：邻 chunk 职责静默吸收，非越界事故）
     * → CityBlockResolver（String 键→Block）→ ChunkClampedSink（最终守卫，构造零丢弃）；</li>
     * <li>街道：锈石铺面 meta5，街中线每 8 格轨枕 meta0，逐列落地
     * y = {@link ProsperityTerrainProfile#heightAt}（高度红线，不读方块）；</li>
     * <li>地块：footprint 与 C 相交者重算同一纯函数放置（损伤档/朝向 plotSeed 派生，
     * 跨 chunk 幂等）。</li>
     * </ul>
     */
    private static void placeCities(long worldSeed, int chunkX, int chunkZ, CityPlan[] cities, BlockSink sink) {
        if (cities.length == 0) {
            return;
        }
        final BlockSink cityChain = new CitySliceSink(new CityBlockResolver(sink), chunkX, chunkZ);
        final StructureBuilder builder = new StructureBuilder(cityChain);
        final CityVariants.GroundFn ground = (x, z) -> ProsperityTerrainProfile.heightAt(worldSeed, x, z);
        for (final CityPlan city : cities) {
            city.forEachStreetColumn(chunkX, chunkZ, (wx, wz, sleeper) -> {
                final int gy = ground.groundY(wx, wz);
                builder.setBlock(wx, gy, wz, CityVariants.K_SURFACE, 5, BlockSink.FLAG_POPULATE);
                if (sleeper) {
                    builder.setBlock(wx, gy + 1, wz, CityVariants.K_DEBRIS, 0, BlockSink.FLAG_POPULATE);
                }
            });
            city.forEachPlotInChunk(
                chunkX,
                chunkZ,
                (variant, originX, originZ, plotSeed) -> CityVariants
                    .placeByName(variant, cityChain, originX, originZ, ground, plotSeed, BlockSink.FLAG_POPULATE));
        }
    }

    /**
     * 群系权重查表（P1 L1 收口，plan §2.1 L1 禁止项）：身份来源唯一——
     * {@link GTSRBiomeAuthority#ordinalAt(int, int)} 给出的<b>维内名册下标</b>；本方法只按下标取权重，
     * 非本维群系 / 降级态（{@code ordinal < 0}）一律回退 1.0F（与改造前"越界回退 1.0"数值口径一致）。
     * <p>
     * 公开是为了离线断言（{@code tools/dim1/BiomeAllocationCheck}）用<b>同一段代码</b>对拍改造前后
     * 的权重数值，不是给生产代码开的后门：生产侧唯一调用者是本类的 {@link #biomeWeight}。
     */
    public static float weightForRosterIndex(int index, float[] table) {
        return index >= 0 && index < table.length ? table[index] : 1.0F;
    }

    /**
     * 群系权重解析（chunk 中心坐标）。
     * <p>
     * <b>P1 改造点</b>：改造前是 {@code world.getBiomeGenForCoords(...)} 读 {@code Chunk} 的 byte
     * biome 平面，再按 {@code biomeID - Config.prosperityBiomeIdStart} 做减法——配槽改为逐群系顺延
     * （允许非连续）后减法必然失真（186 被算成下标 6 ⇒ 恒回退 1.0）。现改为走 L1 唯一出口，
     * 既不再读 byte id，也不再依赖 id 段连续性。采样坐标（chunk 中心块坐标）与判定阈值保持不变，
     * 故正常态（四群系连号）下逐位权重与改造前一致——由 BiomeAllocationCheck 场景 A 对拍钉住。
     */
    private static float biomeWeight(World world, int chunkX, int chunkZ, float[] table) {
        final GTSRBiomeAuthority.Resolution resolution = GTSRBiomeAuthority.forDimension(
            world.provider.dimensionId).ordinalAt((chunkX << 4) + 8, (chunkZ << 4) + 8);
        return weightForRosterIndex(resolution.ordinal, table);
    }
}
