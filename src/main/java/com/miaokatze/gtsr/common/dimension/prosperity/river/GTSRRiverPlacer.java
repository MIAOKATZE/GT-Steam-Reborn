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
 * dim78 河流水系的<b>落块器</b>（v1.20.39 T4 重写）：在 {@code onPopulate} 窗口里按
 * {@link GTSRVoronoiRiverField} 纯函数河流场做<b>水面回填 / 河床料铺放 / 瀑布落差处理</b>。
 * 河谷压低已并入 {@link ProsperityTerrainProfile#heightAt}（本类不切地形，只回填）——
 * 与 P17 S-C 旧模型"落块器下切河床"的分工不同：旧 {@code GTSRRiverNetwork}（轴向等距线、
 * SPACING/MEANDER/CUT_DEPTH/holdsWater/整条掷骰 wet）已整类删除。
 *
 * <p>
 * ═══ 每列断面（H = {@code heightAt}，即含河谷压低后的地表实体顶）═══
 * <ul>
 * <li><b>置水列</b>（{@code wetAt} 过且 H &lt; SEA_LEVEL=68）：{@code y=H+1..67} 置
 * {@code Blocks.water}（meta 0，静态源），水面口径 68 = 最高水格 67 的上一格；</li>
 * <li><b>河核列</b>（s ≥ {@link GTSRVoronoiRiverField#WET_MIN}，含置水/干谷/浅滩）：
 * 地表 {@code y=H} 换 {@code gtsr:prosperityRiverGravel} 河床料——RiverGravel 自 T2 起
 * 是 {@code BlockFalling} 重力方块，<b>只写一格且写在固体顶</b>（下方恒有支撑），
 * populate 窗口 fallInstantly 置位期不产生悬浮结算；</li>
 * <li><b>瀑布落差列</b>（相邻河核列 H 高差 &gt; {@link #WATERFALL_DROP}）：保留落差不拉平
 * （床函数本来就不平滑），且<b>两侧都不铺河床料</b>——防 fallInstantly 结算把重力床料
 * 掉进落差面堵住瀑面（plan §9 风险表的既定处理）。落差竖直面的水由深列的回填天然覆盖
 * （深列水柱 H+1..67 恰好贴着高列的岩壁），无额外写面。</li>
 * </ul>
 *
 * <p>
 * ═══ 写入协议（全部既存纪律，零新发明）═══
 * <ul>
 * <li><b>只写本 chunk</b>：写入经 {@link BlockSink}（生产传 {@code ChunkClampedSink} 钳制）；
 * 邻列量（s/床/落差检测）一律由 {@link GTSRVoronoiRiverField}/{@code heightAt} <b>纯函数重算</b>
 * （18×18 含一列邻格环），零跨 chunk 方块读；</li>
 * <li><b>{@link BlockSink#FLAG_POPULATE}（=2）</b>：只送客户端、不触发邻块更新（与原版
 * {@code WorldGenLakes} 事后灌水同形；水是静态源 {@code setTickRandomly(false)}，
 * provider 阶段不排流体 tick——P17 S-C2 的照明/流体实测结论原样沿用）；</li>
 * <li><b>不消费 populate 的 {@code Random}</b>：全部判定走纯函数 ⇒ 既有 rand 流一个数不动。</li>
 * </ul>
 */
public final class GTSRRiverPlacer {

    /** 相邻河核列地表高差超过该值判为瀑布落差列对（格）。 */
    public static final int WATERFALL_DROP = 3;

    /** 河道统计日志窗口（沿用每 256 chunk 一行口径）。 */
    private static final int LOG_WINDOW_CHUNKS = 256;

    private static final AtomicLong CHUNKS_SERVED = new AtomicLong();
    private static final AtomicLong CORE_COLUMNS = new AtomicLong();
    private static final AtomicLong WATER_COLUMNS = new AtomicLong();
    private static final AtomicLong WATER_CELLS = new AtomicLong();
    private static final AtomicLong BED_PLACED = new AtomicLong();
    private static final AtomicLong FALL_COLUMNS = new AtomicLong();
    private static final AtomicLong WRITES = new AtomicLong();

    /** 河床料缺失锚点是否已打过（一次性；正常生产路径不可达，见 {@link #bedMaterial()}）。 */
    private static boolean bedMissingLogged;

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
        for (int lz = 0; lz < 18; lz++) {
            for (int lx = 0; lx < 18; lx++) {
                final int i = lz * 18 + lx;
                final int x = baseX - 1 + lx;
                final int z = baseZ - 1 + lz;
                final int tier = tierAt(tiers, x, z, baseX, baseZ);
                // T3 审查点落地：不再走 4 参 heightAtWithReliefTier（rosterIndex 形参已被忽略），
                // 直接用 3 参 heightAt——与 generateTerrain/PlacementGate 同一出口
                h[i] = ProsperityTerrainProfile.heightAt(worldSeed, x, z);
                s[i] = -GTSRVoronoiRiverField.strengthAt(worldSeed, x, z, tier);
                wet[i] = GTSRVoronoiRiverField.wetAt(worldSeed, x, z, tier);
            }
        }
        final Block bed = bedMaterial();
        int coreColumns = 0;
        int waterColumns = 0;
        int waterCells = 0;
        int bedPlaced = 0;
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
                // 瀑布落差列：四邻河核列地表高差超过 WATERFALL_DROP 即判落差（保留落差不拉平）
                boolean dropColumn = false;
                for (int d = 0; d < 4 && !dropColumn; d++) {
                    final int ni = i + (d == 0 ? -1 : d == 1 ? 1 : d == 2 ? -18 : 18);
                    if (s[ni] >= GTSRVoronoiRiverField.WET_MIN && Math.abs(h[i] - h[ni]) > WATERFALL_DROP) {
                        dropColumn = true;
                    }
                }
                if (dropColumn) {
                    // 落差列不铺重力河床料（防 fallInstantly 结算堵瀑面）；水照回填（深列水柱贴岩壁即瀑面）
                    fallColumns++;
                } else if (h[i] > GTSRWorldgenHash.bedrockTopHash(worldSeed, x, z)) {
                    // 床料写在地表固体顶（下方恒有支撑；单格替换，自下而上纪律天然满足）
                    writes += accept(sink, x, h[i], z, bed, 0);
                    bedPlaced++;
                }
                // 水面回填：置水资格列 且 地表在海面之下（水面 68 口径 → 最高水格 67）
                if (wet[i] && h[i] < ProsperityTerrainProfile.SEA_LEVEL) {
                    waterColumns++;
                    for (int y = h[i] + 1; y <= ProsperityTerrainProfile.SEA_LEVEL - 1; y++) {
                        writes += accept(sink, x, y, z, Blocks.water, 0);
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
        FALL_COLUMNS.addAndGet(fallColumns);
        WRITES.addAndGet(writes);
        final long served = CHUNKS_SERVED.incrementAndGet();
        if (served % LOG_WINDOW_CHUNKS == 0) {
            GTSteamReborn.LOG.info(
                "[GTSR] dim78 river over {} chunks: coreCols={} waterCols={} waterCells={} bedCols={}"
                    + " fallCols={} writes={}",
                served,
                CORE_COLUMNS.get(),
                WATER_COLUMNS.get(),
                WATER_CELLS.get(),
                BED_PLACED.get(),
                FALL_COLUMNS.get(),
                WRITES.get());
        }
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
     */
    private static int[] tierGrid(long worldSeed, int baseX, int baseZ) {
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

    /** 方块坐标 → 档（列坐标可落在邻格环，故格网比 6×6 多留一圈冗余）。 */
    private static int tierAt(int[] tiers, int x, int z, int baseX, int baseZ) {
        final int cx = ((x >> 2) - cellOrigin(baseX));
        final int cz = ((z >> 2) - cellOrigin(baseZ));
        if (cx < 0 || cz < 0 || cx >= 7 || cz >= 7) {
            // 理论不可达（列域 [base-4, base+20)）；真到了这里按身份不可得处理，不伪造档
            return GTSRGenLayerRosterFace.NO_IDENTITY;
        }
        return tiers[cz * 7 + cx];
    }
}
