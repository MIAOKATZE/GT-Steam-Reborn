package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSROwnedGenerator;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkClampedSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityBlockResolver;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlan;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CitySliceSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinPlacer;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * 繁荣维度世界生成编排器（dim1 S4a，plan §1.2 S4a / 02 §8.2 代码 20 裁剪版）：
 * 仅 dim78 生效（维度过滤范式同 WorldGenRunawaySingularity.java:28-31，dimId 走 Config 可改口径）。
 * generate 顺序 = 城市段（<b>S4b 挂点，本切片留空调用</b>）→ 城外中型废墟 outpost（1/64，S-A5；
 * 命中则跳过机器）→ 残缺机器（1/prosperityMachineChance × 群系机器权重）→ 地表散布（<b>P5 起：
 * 每 chunk 件数 K × 群系散布权重，另有落块/掷点两道上限，全部取自 Config</b>；见
 * {@link ProsperitySurfaceScatter} 类注释）。矿洞/矿坑/矿脉在用户裁剪范围外（不实现）。
 * <p>
 * 群系权重按 <b>L1 维内名册下标</b>查表（{@link GTSRBiomeAuthority#ordinalAt(int, int)} 的
 * {@code ordinal}，P1 收口：不再读 {@code Chunk} 的 byte biome id、不再做 {@code id - idStart} 减法，
 * 也不 import 群系类做 instanceof）；每 chunk 一个 {@link ChunkClampedSink}（协议层钳制 + 越界计数），
 * 机器与散布共用同一 Sink。构造时向 {@link StructureRegistry} 登记 5 机型变体并输出注册证据日志
 * （plan S4a 验收：runServer 日志 grep 锚点）。
 */
public class ProsperityWorldGenerator implements IWorldGenerator, GTSROwnedGenerator {

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
        // P8：废墟族（破坏结构）。开关关闭时<b>一条 roster 都不登记</b>——名册/展示页/机检三条链
        // 因此都读到"没有 ruin 族"的形态，与"注册了但掷骰不中"是两种可区分的状态。
        if (Config.prosperityRuinsEnabled) {
            RuinPlacer.registerVariants();
        }
        if (evidenceLogged) {
            return;
        }
        evidenceLogged = true;
        // P5（plan §2.4 判据 4 单一真值）：注册证据行的散布口径改从 Config 取值，不再硬编码 64——
        // 用户包真值行（plan/log.txt:11452 "scatterBudget=64/chunk"）就是被硬编码骗过去的。
        // 竖向件关闭时权重表不含烟囱项（权重和 85），故一并回显，便于实机一眼分辨"柱阵回没回来"。
        GTSteamReborn.LOG.info(
            "[GTSR] prosperity worldgen registered: dimId={} machines={} (spanning={}) outposts=6 {}"
                + " scatterK={}/chunk"
                + " scatterBlocks={}/chunk scatterAttempts={}/chunk scatterVertical={} scatterWindowCap={}"
                + " scatterWeights={}/{}/{}/{} scatterClusterMode={} clusterCell={}ch clusterDenom={}"
                + " clusterPieces={}..{} clusterRadius={}ch clusterFalloff={} machineChance=1/{} outpostChance=1/{}"
                + " structureBudget={}/chunk structureWindowCap={} structureFamilyGap={}"
                + " ruins={} ruinEnabled={} ruinChance=1/{} ruinWindowCap={}",
            Config.prosperityDimId,
            // P16-B1：机型数不再写死在格式串里（"5" 曾是第二份真值；候选池并上跨片巨构后它会变）
            RuinedMachinePlacer.registeredCount(),
            RuinedMachinePlacer.spanningCount(),
            StructureRegistry.names(),
            Config.prosperityScatterContoursPerChunk,
            Config.prosperityScatterBlocksPerChunk,
            Config.prosperityScatterAttemptsPerChunk,
            Config.prosperityScatterVerticalPieces,
            Config.prosperityScatterWindowRepeatCap,
            Config.prosperityScatterWeightSleeper,
            Config.prosperityScatterWeightPipe,
            Config.prosperityScatterWeightRivetPlate,
            Config.prosperityScatterWeightChimney,
            // P5b（plan §7.2）：成簇档回显——场中心选择（cell/denom）与场内撒落（pieces/radius/falloff）
            Config.prosperityScatterClusterMode,
            Config.prosperityScatterClusterCellChunks,
            Config.prosperityScatterClusterFieldChanceDenom,
            Config.prosperityScatterClusterPiecesMin,
            Config.prosperityScatterClusterPiecesMax,
            Config.prosperityScatterClusterRadiusChunks,
            Config.prosperityScatterClusterFalloffPower,
            Config.prosperityMachineChance,
            Config.prosperityOutpostChance,
            Config.prosperityStructureBudgetPerChunk,
            Config.prosperityStructureWindowRepeatCap,
            // P7c：贴脸由同族间距档负责（0 = 关闭 = 回退位），一并回显便于实机一眼分辨治疗是否生效
            Config.prosperityStructureFamilyGapChunks,
            // P8：废墟族（破坏结构）四元组——注册数/开关/分母/本族窗上限，全部取 Config 与 roster 真值
            RuinPlacer.registeredCount(),
            Config.prosperityRuinsEnabled,
            Config.prosperityRuinChance,
            Config.prosperityRuinWindowRepeatCap);
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
        // P17 S-B2：本 chunk 的 L1 名册下标<b>只解析一次</b>，机器权重 / 散布权重 / 植被档三族共用
        // （改造前是 biomeWeight 每次调用各解析一次，且装饰根本没有身份 ⇒ 旧 :182 的"零分流"根因）。
        final int rosterIndex = biomeRosterIndex(world, chunkX, chunkZ);

        // —— 1. 古代城（S4b）：3×3 cell 检索邻域城市，仅渲染与 C 相交的交集切片（plan §3.1）——
        final CityPlan[] cities = CityPlanner.citiesNear(worldSeed, chunkX, chunkZ);
        placeCities(worldSeed, chunkX, chunkZ, cities, sink);

        // 城市缓冲窗（半径+1 chunk）内跳过散布与残缺机器（plan §3.4：城市本身即"结构密度拉满"，
        // 二者混叠只脏；窗判定与渲染检索同源 CityPlanner.citiesNear，跨 chunk 一致）
        if (cities.length > 0) {
            return;
        }

        // —— 2. 城外中型废墟（S-A5，plan §12 修订 7/8）：1/prosperityOutpostChance 掷骰；同 chunk
        // 互斥掷骰 = 先 outpost，命中则本 chunk 跳过残缺机器（防 footprint 撞格；散布/装饰仍照常）。
        // P7（plan §5 P7 / §2.2 H-3）：互斥与预算不再靠"outpost 说它成功了"这句话——本 chunk 建一个
        // PlacementGate.ChunkGate 交给两个 placer 共用，预算<b>只在真实落块后</b>由 Permit.commit 扣减，
        // 所以 outpost 若门通过却一块没落进世界，机器照常有机会（改造前那种"假成功吞掉互斥位"已闭合）。——
        final PlacementGate.ChunkGate structureGate = PlacementGate
            .beginChunk(SurfaceGate.DIM78, worldSeed, chunkX, chunkZ);
        boolean structureLanded = ProsperityOutpostPlacer
            .placeAll(world, worldSeed, chunkX, chunkZ, sink, structureGate);
        if (!structureLanded) {
            // —— 3. 残缺机器（1/prosperityMachineChance × 群系机器权重，'C' 位=积碳壳，无 TE）——
            structureLanded = RuinedMachinePlacer.placeAll(
                world,
                worldSeed,
                chunkX,
                chunkZ,
                weightForRosterIndex(rosterIndex, MACHINE_WEIGHTS),
                sink,
                structureGate);
        }
        // —— 4. 城外废墟族（P8，plan §5 P8）：互斥链的第三环。前两环的掷骰与概率值一个字未改，
        // 本环只在它们都没真实落块时才问门，过的是同一份每 chunk 预算（默认 1）与同一份同族间距档
        // ⇒ 废墟是"在既有预算内挤位"，不是叠加密度（实测对照见 tools/dim1/RuinFamilyCheck DENSITY 行）。
        if (!structureLanded) {
            RuinPlacer.placeAll(world, worldSeed, chunkX, chunkZ, sink, structureGate);
        }

        // —— 5. 地表散布（P5：每 chunk 件数 K × 群系散布权重 + 落块/掷点上限，全部 Config 取值；
        // 最低优先级，只落自然锈变地表+空气让行。掷骰顺序与上方 2→3→4 的互斥关系一字未改）——
        ProsperitySurfaceScatter
            .scatter(world, worldSeed, chunkX, chunkZ, weightForRosterIndex(rosterIndex, SCATTER_WEIGHTS), sink);

        // —— 6. 自然区装饰（S-A1，plan §12 修订 5；<b>P17 S-B2 起带身份</b>：树趟 + 植被趟各按
        // ProsperityDecorPlacer.VEG_TIERS_BY_ROSTER[名册下标] 取档 ⇒ 青铜森林树最多最大、平原矮树、
        // 沼泽中等、荒漠零树，并新增花/新草/沙砾三件；城 buffer 窗由上方 citiesNear return 天然保证——
        // <b>T7 起树趟扩三档（灌木/普通/巨树），植被身份走 vegRosterIndex</b>（sanzu 平面档当趟生效，
        // 见 {@link #vegRosterIndex}；机器/散布/结构仍用 GenLayer 面 rosterIndex，不受影响）——
        ProsperityDecorPlacer
            .decorate(world, worldSeed, chunkX, chunkZ, vegRosterIndex(worldSeed, chunkX, chunkZ, rosterIndex), sink);
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
     * <p>
     * <b>P7 接地口径</b>：这里的 {@code ground} 与 {@link ProsperityOutpostPlacer#placeAll}、
     * {@link RuinedMachinePlacer#placeAll} 内的逐列落地表达式<b>同为</b>
     * {@link ProsperityTerrainProfile#heightAt(long, int, int)}（plan §2.1 L2「heightAt 唯一制式」）。
     * 改造前机器那一族用的是中心列 {@code findSurfaceY} 列扫出的<b>整台同一平面</b>，与本处的逐列
     * 高度不同源（审计 A-4）；P7 起三族一致，逐点一致性由
     * {@code tools/dim1/PlacementContractCheck grounding} 在真实链上钉住。城内地块仍不归
     * {@link PlacementGate} 管（其密度由城窗 H-1/L6 决定，不是每 chunk 掷骰）。
     */
    private static void placeCities(long worldSeed, int chunkX, int chunkZ, CityPlan[] cities, BlockSink sink) {
        if (cities.length == 0) {
            return;
        }
        final BlockSink cityChain = new CitySliceSink(new CityBlockResolver(sink), chunkX, chunkZ);
        final StructureBuilder builder = new StructureBuilder(cityChain);
        // P7：城的接地与两族城外结构取同一个供给器（PlacementGate.groundFn），"同一列同一个 y"
        // 由构造保证；离线断言 tools/dim1/PlacementContractCheck grounding 逐点复核。
        final CityVariants.GroundFn ground = PlacementGate.groundFn(worldSeed);
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
     * 的权重数值，不是给生产代码开的后门：生产侧唯一调用者是本类的 {@code generate}（机器与散布两族）。
     */
    public static float weightForRosterIndex(int index, float[] table) {
        return index >= 0 && index < table.length ? table[index] : 1.0F;
    }

    /**
     * 群系身份解析（chunk 中心坐标）。
     * <p>
     * <b>P1 改造点</b>：改造前是 {@code world.getBiomeGenForCoords(...)} 读 {@code Chunk} 的 byte
     * biome 平面，再按 {@code biomeID - Config.prosperityBiomeIdStart} 做减法——配槽改为逐群系顺延
     * （允许非连续）后减法必然失真（186 被算成下标 6 ⇒ 恒回退 1.0）。现改为走 L1 唯一出口，
     * 既不再读 byte id，也不再依赖 id 段连续性。采样坐标（chunk 中心块坐标）与判定阈值保持不变，
     * 故正常态（四群系连号）下逐位权重与改造前一致——由 BiomeAllocationCheck 场景 A 对拍钉住。
     * <p>
     * <b>P17 S-B2 改判</b>：原 {@code biomeWeight(World,int,int,float[])} 每次调用各解析一次身份
     * （机器 + 散布 = 每 chunk 两次），而装饰那一趟<b>拿不到</b>身份 ⇒ 分流无从发生。
     * 现把解析上提为 {@code generate} 里的<b>一次</b> {@link #biomeRosterIndex}，三族共用同一个
     * {@code ordinal}（权重侧仍走 {@link #weightForRosterIndex}，数值逐位不变；植被侧走
     * {@code ProsperityDecorPlacer.tierForRosterIndex}）。身份出口仍只有 {@code ordinalAt} 一条。
     */
    private static int biomeRosterIndex(World world, int chunkX, int chunkZ) {
        return GTSRBiomeAuthority.forDimension(world.provider.dimensionId)
            .ordinalAt((chunkX << 4) + 8, (chunkZ << 4) + 8).ordinal;
    }

    /**
     * <b>植被档身份（T7，plan §3.3 / §3.8）</b>：GenLayer 面下标之上叠加 sanzu 平面档——
     * sanzu 不进 selector（T5 裁决），其群系平面在 populate 后置由
     * {@code ChunkProviderProsperityRuins.assignSanzuRiverBiome} 按
     * {@link GTSRVoronoiRiverField#isSanzuColumn} 逐列写入；decorate 晚于该写入 ⇒ 这里用<b>同一谓词</b>
     * 在 chunk 中心确定性重算（纯函数、跨 chunk 一致，与河流回填同族），命中且 sanzu 已配槽
     * （账本点名得到实例，无槽降级不伪造）⇒ 植被档取 sanzu 名册下标
     * （{@link BiomeId#SANZU_RIVER}，档值=档表第 5 行，身份仍只有 {@code ordinalAt} 一条出口）。
     * <p>
     * 只影响 decorate 的档位选择：机器/散布/结构继续消费 GenLayer 面 {@code rosterIndex}
     * （四元素权重表的下标语义与 T5 前逐位一致），本方法不是第二身份真值源。
     */
    private static int vegRosterIndex(long worldSeed, int chunkX, int chunkZ, int chainIndex) {
        if (GTSRVoronoiRiverField.isSanzuColumn(worldSeed, (chunkX << 4) + 8, (chunkZ << 4) + 8)
            && GTSRBiomeAuthority.forDimKey(SurfaceGate.DIM78)
                .biomeOf(BiomeId.SANZU_RIVER) != null) {
            return BiomeId.SANZU_RIVER.rosterIndex();
        }
        return chainIndex;
    }
}
