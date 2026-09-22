package com.miaokatze.gtsr.common.dimension.prosperity.river;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerRosterFace;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * dim78 河流水系的<b>落块器</b>（v1.20.39 T4 重写；v1.20.40 P19 §C 改接分段池水位）：在
 * {@code onPopulate} 窗口里按 {@link GTSRVoronoiRiverField} 纯函数河流场做<b>水面回填 / 河床料
 * 与河滩料铺放 / 落差墙处理</b>。河谷压低已并入 {@link ProsperityTerrainProfile#heightAt}
 * （本类不切地形，只回填）。
 *
 * <p>
 * ═══ 每列断面（H = {@code heightAt}，即含两段式河谷压低后的地表实体顶；P = 本段池水位
 * {@link GTSRVoronoiRiverField#poolLevelAt}，同 segKey 段内恒定）═══
 * <ul>
 * <li><b>置水列</b>（{@code wetAt} 过且 H &lt; P−1）：{@code y=H+1..P−1} 置
 * {@link #waterMaterial}（v1.20.40 P19 §I 起为 {@code BlocksGTSR.abyssalFluid} meta 0 静态源；
 * {@code FLAG_POPULATE}）——水面恒 = 段池水位 P，不再是全局 68；段与段之间 P 差 ≥
 * {@link GTSRVoronoiRiverField#POOL_DROP} 的交界两侧各按本段水面回填，竖直落差面由上游池
 * 末列的水柱天然贴出（<b>落差墙/瀑布</b>，P19 §C）；湿段末端面由 heightCore 的床端面修正
 * （P19 §B）收成缓坡，回填门随床自动关水；</li>
 * <li><b>河核列</b>（s ≥ WET_MIN）地表料：水下（H &lt; P−1）= {@code gtsr:prosperityRiverGravel}
 * 床料；水上且 s ∈ [WET_MIN, WET_MIN+{@link GTSRVoronoiRiverField#SHORE_FLAT_BAND}] =
 * <b>滩料</b>（草原/森林=硅沙、荒漠=粗沙、沼泽/sanzu=河砾，P19 §A.3 河滩带）；带外露头列
 * = 床料（干砾浅滩，浅滩重释的派生干出）。RiverGravel 是 {@code BlockFalling}，只写一格
 * 且写在固体顶（fallInstantly 置位期不产生悬浮结算）；</li>
 * <li><b>落差墙列</b>（四邻河核列池水位差 &gt;= POOL_DROP，即 segKey 不同的段界陡坎）：
 * 保留落差不拉平，且<b>不铺重力床/滩料</b>——防 fallInstantly 结算把重力料掉进落差面堵住
 * 瀑面（v1.20.39 plan §9 风险表的既定处理，P19 起判据由 H 高差改为 P 池差）。</li>
 * </ul>
 *
 * <p>
 * ═══ 写入协议（全部既存纪律，零新发明）═══
 * <ul>
 * <li><b>只写本 chunk</b>：写入经 {@link BlockSink}（生产传 {@code ChunkClampedSink} 钳制）；
 * 邻列量（s/池水位/落差检测）一律由 {@link GTSRVoronoiRiverField}/{@code heightAt} <b>纯函数
 * 重算</b>（18×18 含一列邻格环），零跨 chunk 方块读；</li>
 * <li><b>{@link BlockSink#FLAG_POPULATE}（=2）</b>：只送客户端、不触发邻块更新（与原版
 * {@code WorldGenLakes} 事后灌水同形；水是静态源，provider 阶段不排流体 tick——P17 S-C2
 * 的照明/流体实测结论原样沿用）；</li>
 * <li><b>不消费 populate 的 {@code Random}</b>：全部判定走纯函数 ⇒ 既有 rand 流一个数不动。</li>
 * </ul>
 * <p>
 * 巨湖水面回填（{@code fillSanzuLakes}，SEA_LEVEL=68 口径）在 provider 侧，属 P19 批2 巨湖
 * 重构，本类不触碰。
 */
public final class GTSRRiverPlacer {

    /** 河道统计日志窗口（沿用每 256 chunk 一行口径）。 */
    private static final int LOG_WINDOW_CHUNKS = 256;

    private static final AtomicLong CHUNKS_SERVED = new AtomicLong();
    private static final AtomicLong CORE_COLUMNS = new AtomicLong();
    private static final AtomicLong WATER_COLUMNS = new AtomicLong();
    private static final AtomicLong WATER_CELLS = new AtomicLong();
    private static final AtomicLong BED_PLACED = new AtomicLong();
    private static final AtomicLong FLAT_PLACED = new AtomicLong();
    private static final AtomicLong FALL_COLUMNS = new AtomicLong();
    private static final AtomicLong WRITES = new AtomicLong();

    /** 河床料缺失锚点是否已打过（一次性；正常生产路径不可达，见 {@link #bedMaterial()}）。 */
    private static boolean bedMissingLogged;

    /** 水体方块缺失锚点是否已打过（一次性；正常生产路径不可达，见 {@link #waterMaterial()}）。 */
    private static boolean waterMissingLogged;

    private GTSRRiverPlacer() {}

    /**
     * 本 chunk 的河流水系（唯一生产入口，{@code ChunkProviderProsperityRuins.onPopulate} 调用）。
     *
     * @param worldSeed {@code world.getSeed()}——必须与 {@code heightAt} 的其它调用点同一个值
     *                  （不含 def.seedSalt；身份面的盐在 {@link #tierGrid} 内按 S-A 同式掺入）
     */
    public static void place(World world, long worldSeed, int chunkX, int chunkZ, BlockSink sink) {
        final int baseX = chunkX << 4;
        final int baseZ = chunkZ << 4;
        // —— 1. 身份档（coarse 面 1:4，6×6 格 + 冗余圈：河核列/床/置水判定全部要按列取档）
        final int[] tiers = tierGrid(worldSeed, baseX, baseZ);
        // —— 2. 纯函数重算 18×18（16×16 + 一列邻格环，落差检测与统计都只在本 chunk 内写）
        final double[] s = new double[18 * 18];
        final boolean[] wet = new boolean[18 * 18];
        final int[] h = new int[18 * 18];
        final int[] pool = new int[18 * 18];
        for (int lz = 0; lz < 18; lz++) {
            for (int lx = 0; lx < 18; lx++) {
                final int i = lz * 18 + lx;
                final int x = baseX - 1 + lx;
                final int z = baseZ - 1 + lz;
                final int tier = tierAt(tiers, x, z, baseX, baseZ);
                // P19 U8 求值重排（各列读数与旧顺序逐位相同，纯函数无序依赖）：先算河流场三件
                // （s/wet/pool——它们经河流场列级 memo 互相共解一次 warp+evalBorder），最后算
                // heightAt（其内部 heightCore 复用同一批 memo；heightAt 可能触发的 endFaceBed
                // 段界射线在本列读数全部取完之后，不再冲掉本列的 memo 现场让邻量重算）。
                // T3 审查点落地：不再走 4 参 heightAtWithReliefTier（rosterIndex 形参已被忽略），
                // 直接用 3 参 heightAt——与 generateTerrain/PlacementGate 同一出口
                s[i] = -GTSRVoronoiRiverField.strengthAt(worldSeed, x, z, tier);
                wet[i] = GTSRVoronoiRiverField.wetAt(worldSeed, x, z, tier);
                // P19 §C：本段池水位（同 segKey 段内恒定 ⇒ 水面跨 chunk 一致；落差墙判据）
                pool[i] = GTSRVoronoiRiverField.poolLevelAt(worldSeed, x, z, tier);
                h[i] = ProsperityTerrainProfile.heightAt(worldSeed, x, z);
            }
        }
        final Block bed = bedMaterial();
        final Block water = waterMaterial();
        int coreColumns = 0;
        int waterColumns = 0;
        int waterCells = 0;
        int bedPlaced = 0;
        int flatPlaced = 0;
        int fallColumns = 0;
        int writes = 0;
        // —— 3. 逐列落块：只走本 chunk 的 16×16（邻格环只参与判定，一律不写）
        for (int lz = 1; lz < 17; lz++) {
            for (int lx = 1; lx < 17; lx++) {
                final int i = lz * 18 + lx;
                if (s[i] < GTSRVoronoiRiverField.WET_MIN) {
                    continue; // 谷坡列（0 < s < WET_MIN）：河谷已由 heightAt 压低，不铺不灌
                }
                coreColumns++;
                final int x = baseX + lx - 1;
                final int z = baseZ + lz - 1;
                final int p = pool[i];
                final boolean submerged = h[i] < p - 1; // 水下（水面 = p，最高水格 p−1）
                // 落差墙列：四邻河核列池水位差 ≥ POOL_DROP 即段界陡坎（保留落差，不拉平）
                boolean dropColumn = false;
                for (int d = 0; d < 4 && !dropColumn; d++) {
                    final int ni = i + (d == 0 ? -1 : d == 1 ? 1 : d == 2 ? -18 : 18);
                    if (s[ni] >= GTSRVoronoiRiverField.WET_MIN
                        && Math.abs(p - pool[ni]) >= GTSRVoronoiRiverField.POOL_DROP) {
                        dropColumn = true;
                    }
                }
                if (dropColumn) {
                    // 落差列不铺重力床/滩料（防 fallInstantly 结算堵瀑面）；水照回填
                    // （上游池列水柱到 p_hi−1，贴着下游 p_lo−1 水面 = 落差竖直面）
                    fallColumns++;
                } else if (h[i] > GTSRWorldgenHash.bedrockTopHash(worldSeed, x, z)) {
                    // 地表料：水下 = 床料；水上且在河滩带（s ∈ [WET_MIN, WET_MIN+SHORE_FLAT_BAND]）
                    // = 滩料（群系档表）；滩带外露头列 = 床料（干砾浅滩）
                    final Block surface = submerged ? bed
                        : (s[i] <= GTSRVoronoiRiverField.WET_MIN + GTSRVoronoiRiverField.SHORE_FLAT_BAND
                            ? flatMaterial(tierAt(tiers, x, z, baseX, baseZ), bed)
                            : bed);
                    writes += accept(sink, x, h[i], z, surface, 0);
                    if (surface == bed) {
                        bedPlaced++;
                    } else {
                        flatPlaced++;
                    }
                }
                // 水面回填：置水资格列 且 地表在本段水面之下（水面 = 池水位 p → 最高水格 p−1）
                if (wet[i] && h[i] < p - 1) {
                    waterColumns++;
                    for (int y = h[i] + 1; y <= p - 1; y++) {
                        writes += accept(sink, x, y, z, water, 0);
                        waterCells++;
                    }
                }
            }
        }
        // —— 4. 观测：每 256 chunk 一行（无河道也是读数，不静默）
        CORE_COLUMNS.addAndGet(coreColumns);
        WATER_COLUMNS.addAndGet(waterColumns);
        WATER_CELLS.addAndGet(waterCells);
        BED_PLACED.addAndGet(bedPlaced);
        FLAT_PLACED.addAndGet(flatPlaced);
        FALL_COLUMNS.addAndGet(fallColumns);
        WRITES.addAndGet(writes);
        final long served = CHUNKS_SERVED.incrementAndGet();
        if (served % LOG_WINDOW_CHUNKS == 0) {
            GTSteamReborn.LOG.info(
                "[GTSR] dim78 river over {} chunks: coreCols={} waterCols={} waterCells={} bedCols={}"
                    + " flatCols={} fallCols={} writes={}",
                served,
                CORE_COLUMNS.get(),
                WATER_COLUMNS.get(),
                WATER_CELLS.get(),
                BED_PLACED.get(),
                FLAT_PLACED.get(),
                FALL_COLUMNS.get(),
                WRITES.get());
        }
    }

    /**
     * 河滩料（P19 plan §A.3 群系档表）：草原/森林 = 硅沙、荒漠 = 粗沙、沼泽/sanzu = 河砾；
     * 越界/缺席（离线 -1）回退硅沙（常态河口径）。字段缺失（BlockLoader 未跑）时回退床料
     * {@code fallback}（其自身再退 stone，见 {@link #bedMaterial()}）——断面仍闭合。
     */
    private static Block flatMaterial(int rosterIndex, Block fallback) {
        final Block flat;
        switch (rosterIndex) {
            case 2: {
                flat = BlocksGTSR.prosperityCoarseSand;
                break;
            }
            case 3:
            case 4: {
                flat = BlocksGTSR.prosperityRiverGravel;
                break;
            }
            default: {
                flat = BlocksGTSR.prosperitySilicaSand;
                break;
            }
        }
        return flat != null ? flat : fallback;
    }

    /** 一次写入；返回 1/0 只为统计，不参与任何生成判定（丢弃语义见 {@code BlockSink} 契约）。 */
    private static int accept(BlockSink sink, int x, int y, int z, Block block, int meta) {
        return sink.setBlock(x, y, z, block, meta, BlockSink.FLAG_POPULATE) ? 1 : 0;
    }

    /**
     * 河床料（在册 {@code gtsr:prosperityRiverGravel}，T2 起 BlockFalling 派生）。
     * <b>{@code BlockLoader} 未跑时该静态字段为 null</b>——生产路径由 preInit 顺序保证非 null，
     * 真为 null 时回退 {@code Blocks.stone} 并打<b>一次性</b> WARN（断面仍闭合，只是床料退化）。
     */
    private static Block bedMaterial() {
        final Block bed = BlocksGTSR.prosperityRiverGravel;
        if (bed != null) {
            return bed;
        }
        if (!bedMissingLogged) {
            bedMissingLogged = true;
            GTSteamReborn.LOG.warn(
                "[GTSR] dim78 river bed material MISSING (BlocksGTSR.prosperityRiverGravel == null"
                    + " before BlockLoader) -> 河床退化为 stone，水断面不变（一次性告警）");
        }
        return Blocks.stone;
    }

    /**
     * <b>维度水体方块</b>（v1.20.40 P19 plan §I 换块：{@code BlocksGTSR.abyssalFluid}，
     * 材料名 {@code abyssal_obsession}/深渊执念，meta 0 = 静态源）——river 河面/巨湖
     * （provider {@code fillSanzuLakes}）/沼泽微池（{@code fillSwampPools}）全部水体同块，
     * 视觉色由 {@code BlockAbyssalFluid.colorMultiplier} 按群系分档（{@code BiomeSanzuRiver}
     * 更深一档）；流动 tick 由 BlockFluidClassic 天然行为承担。{@code BlockLoader} 未跑
     * （理论不可达：worldgen 晚于 preInit）时回退 {@code Blocks.water} 并打<b>一次性</b> WARN
     * （断面仍闭合，只是水体退化为原版水）。
     */
    public static Block waterMaterial() {
        final Block fluid = BlocksGTSR.abyssalFluid;
        if (fluid != null) {
            return fluid;
        }
        if (!waterMissingLogged) {
            waterMissingLogged = true;
            GTSteamReborn.LOG.warn(
                "[GTSR] dim78 water material MISSING (BlocksGTSR.abyssalFluid == null"
                    + " before BlockLoader) -> 水体退化为原版水，水断面不变（一次性告警）");
        }
        return Blocks.water;
    }

    // ————————————————————————— 身份格网 —————————————————————————

    /** coarse 面（1:4）的格网起点：覆盖 [baseX-4, baseX+20) ⇒ 含一列邻格环所需的边圈。 */
    private static int cellOrigin(int base) {
        return (base - 4) >> 2;
    }

    /**
     * 本 chunk 及其一列邻格环的 L1 名册下标（coarse 身份面，与 {@code heightAt} 内部的取数口
     * <b>同一个</b> {@link GTSRGenLayerRosterFace}，盐沿用 {@link ProsperityTerrainProfile#CHAIN_SEED_SALT}
     * 的<b>常数引用</b>——不抄字面量，不新增第二真值）。缺失身份 =
     * {@link GTSRGenLayerRosterFace#NO_IDENTITY}，由档表各自回退默认档（无身份等值判断）。
     * <b>public 供同片消费面复用</b>（v1.20.40 P19 §E：provider {@code fillSwampPools} 的
     * 微池 roster 门走同一格网，不建第二份身份取数）。
     */
    public static int[] tierGrid(long worldSeed, int baseX, int baseZ) {
        final long chainSeed = worldSeed ^ ProsperityTerrainProfile.CHAIN_SEED_SALT;
        final int cx0 = cellOrigin(baseX);
        final int cz0 = cellOrigin(baseZ);
        final int[] tiers = new int[7 * 7];
        for (int cz = 0; cz < 7; cz++) {
            for (int cx = 0; cx < 7; cx++) {
                tiers[cz * 7 + cx] = GTSRGenLayerRosterFace
                    .rosterIndexAt(chainSeed, GTSRBiomeAuthority.DIM_KEY_PROSPERITY, (cx0 + cx) << 2, (cz0 + cz) << 2);
            }
        }
        return tiers;
    }

    /** 方块坐标 → 档（列坐标可落在邻格环，故格网比 6×6 多留一圈冗余；public 同 {@link #tierGrid}）。 */
    public static int tierAt(int[] tiers, int x, int z, int baseX, int baseZ) {
        final int cx = ((x >> 2) - cellOrigin(baseX));
        final int cz = ((z >> 2) - cellOrigin(baseZ));
        if (cx < 0 || cz < 0 || cx >= 7 || cz >= 7) {
            // 理论不可达（列域 [base-4, base+20)）；真到了这里按身份不可得处理，不伪造档
            return GTSRGenLayerRosterFace.NO_IDENTITY;
        }
        return tiers[cz * 7 + cx];
    }
}
