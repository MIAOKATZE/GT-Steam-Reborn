package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.TerrainVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRRiverPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;
import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 沼泽<b>地面浅洞</b>（grotto，v1.20.53 P30 II-C 新增）：populate 后置 carve 的地表浅洞族——
 * 从地表延伸进入（洞缘 m≤0.55 开放圈直接开口见天）、洞底水潭（潭区独立噪声斑状塌口 ≈30%
 * 露天，{@link #GROTTO_OPEN_THR} 探针校准）、洞内草更多（{@link #GROTTO_GRASS_ROLLS} 趟）。
 * <p>
 * ═══ 纯函数纪律（风格照 {@code GTSRRiverPlacer}/{@code ChunkProviderProsperityRuins.fillSwampPools}）═══
 * 全部场与谓词是 {@code (seed, x, z)} 的确定函数——<b>任一 chunk 可独立重算</b>（CPR onPopulate
 * 同一条纪律）：跨 chunk 的邻列量（潭的 8 邻钳制）只算坐标、不读块，经 {@link #grottoCarveTopAt}
 * 列级重算 ⇒ 生成次序无关、接缝两侧恒同值。{@link #carve} 只写本 chunk（{@link BlockSink} /
 * 生产 {@code ChunkClampedSink} 钳制），全部写入走 {@link BlockSink#FLAG_POPULATE}，
 * <b>不消费 populate 的 {@code Random}</b>（既有 rand 取数序一字不动）。
 * <p>
 * ═══ 列几何（m = {@link #grottoMAt} 门值 ∈ [0,1]，site 列 = {@link #grottoSiteAt}）═══
 * <ul>
 * <li>h0 = {@link ProsperityTerrainProfile#heightAt}（GROTTO <b>不进</b> heightAt——地形场零改动，
 * GenBench 不计入；h0 及其下主体照旧）；</li>
 * <li>depth = {@link #GROTTO_DEPTH_BASE} + {@link #GROTTO_DEPTH_SPAN}·m（site 列 m≥0.5 ⇒ 5.5..8 格），
 * floorY = h0 − round(depth)；</li>
 * <li>顶厚 t = round({@link #GROTTO_ROOF_T}·s01((m−{@link #GROTTO_RIM_M})/({@link #GROTTO_ROOF_FULL_M}
 * −{@link #GROTTO_RIM_M})))：m≤0.55 ⇒ t=0（<b>洞缘开放圈</b>——地表直接开口进洞），
 * m≥0.68 ⇒ 全厚（完整顶盖）；</li>
 * <li>open = (t==0) ∨ (wet ∧ openNoise≥{@link #GROTTO_OPEN_THR})，wet = m≥{@link #GROTTO_WATER_M}
 * （潭区）；yTop = open ? h0 : h0−t；<b>腔 = [floorY+1, yTop] 置空气</b>；</li>
 * <li>潭（wet 列）：wTop = min(8 邻 {@link #grottoCarveTopAt} − 1, floorY+
 * {@link #GROTTO_WATER_DEPTH_MAX})——N8 只降不升钳制（SwampFieldGrid 的"8 邻固体顶 ≥ 水顶"
 * 同族口径；浅洞域外邻的阻挡面 = 其 h0，列级一趟即收敛、不需不动点）+ 潭深帽 2；
 * wTop ≥ floorY+1 ⇒ 置水 [floorY+1, wTop]（{@link GTSRRiverPlacer#waterMaterial()} 单一出口）；
 * <b>潭顶塌口</b> = 潭区独立噪声（{@link #grottoOpenNoiseAt}，λ21）≥ {@link #GROTTO_OPEN_THR}
 * 的斑状 open 列——塌口列 yTop=h0 见天，潭水经塌口接天光（目标占比 ≈30%±3%，
 * 校准读数 {@code temp/p30-probe/grotto-post.out}）。</li>
 * </ul>
 * <p>
 * <b>洞底统一 {@code prosperitySwampTop}</b>（{@code ChunkClampedSink} 无读接口 ⇒ 不做"floorY
 * 须为地形主体"的判读，<b>无条件覆写</b>——简化口径申报：floorY 原为 wholeBody 的
 * prosperityStone/stone 主体，统一写成 PDP {@code tuftForGround} 认的 4 top 之一 ⇒ 洞草可落、
 * 洞底是沼泽壤土而非裸岩）。草趟只落"洞腔列 ∧ 无水"（floorY+1 &gt; wTop）的 floorY+1
 * （cross 植物直写，PDP {@code placeCrossPlant} 的 FLAG_POPULATE 口径；不查 isAirBlock——
 * 腔内本趟刚置空，构造保证）。
 */
public final class GrottoCarver {

    /** 观察日志窗口（chunk 数，与 CPR 巨湖/微池行同款量级，单独一条 grotto 行）。 */
    private static final int LOG_WINDOW_CHUNKS = 1024;

    private static final AtomicLong CHUNKS_SERVED = new AtomicLong();
    private static final AtomicLong CAVITY_CELLS = new AtomicLong();
    private static final AtomicLong WATER_CELLS = new AtomicLong();
    private static final AtomicLong GRASS_PLACED = new AtomicLong();

    /**
     * 浅洞选址盐（沼泽分型等差族延拓：{@code 0x6811C3D1}+0x12 族之上，= 沼泽缓坡床纹
     * {@code 0x6811C407} + 0x12；同族步长 0x12，无碰撞，grep 自检见
     * {@code temp/p30-iic/diff-note.md}）。
     */
    public static final long S_SWAMP_GROTTO = 0x6811C419L;
    /** m 场波长（格）：53（53%16=5 非 chunk 对齐，无 16 格周期伪影）。 */
    public static final double GROTTO_SCALE = 53.0D;
    /** m 场门带下沿（valueNoise 域）：s01((n−LO)/SPAN)，门 ⟺ n≥0.71。 */
    public static final double GROTTO_GATE_LO = 0.62D;
    /** m 场门带带宽。 */
    public static final double GROTTO_GATE_SPAN = 0.18D;
    /** 选址边缘门阈（{@link TerrainVariants#swampInteriorGateAt} ≥ 本值才可开洞——比微池腿 0.5 严一档）。 */
    public static final double GROTTO_EDGE_GATE_MIN = 0.6D;
    /** 洞深基（格）：depth = 3 + 5·m。 */
    public static final double GROTTO_DEPTH_BASE = 3.0D;
    /** 洞深幅（格）：site 列 m∈[0.5,1] ⇒ depth 5.5..8。 */
    public static final double GROTTO_DEPTH_SPAN = 5.0D;
    /** 洞缘开放圈上阈：m ≤ 本值 ⇒ 顶厚 t=0（地表直接开口）。 */
    public static final double GROTTO_RIM_M = 0.55D;
    /** 顶全厚上阈：m ≥ 本值 ⇒ t = GROTTO_ROOF_T 全厚（s01 饱和 1.0）。 */
    public static final double GROTTO_ROOF_FULL_M = 0.68D;
    /** 顶全厚（格）：round(3.5·s01(..)) ⇒ 全厚列 t=4。 */
    public static final double GROTTO_ROOF_T = 3.5D;
    /** 潭区阈：m ≥ 本值 = wet（洞底置水候选列）。 */
    public static final double GROTTO_WATER_M = 0.78D;
    /** 潭顶塌口独立盐（= S_SWAMP_GROTTO + 0x12，等差族内独立一格）。 */
    public static final long S_SWAMP_GROTTO_OPEN = 0x6811C42BL;
    /** 塌口噪声波长（格）：λ21（21%16=5 非 chunk 对齐）。 */
    public static final double GROTTO_OPEN_SCALE = 21.0D;
    /**
     * 塌口阈：openNoise ≥ 本值 = 潭区塌口见天。<b>校准终值 0.21</b>（P30 II-C 探针 post：
     * 设计标称 0.42 实测占比 0.175 超带（目标 0.30±0.03）；声明校准域 {0.30..0.55} 内最高
     * 0.244（thr=0.30）仍低于带下沿 ⇒ 域外扩展扫描选 0.21 ⇒ 占比 0.306 ∈ 带，
     * EXTENDED 申报）；过程与读数见 {@code temp/p30-probe/grotto-post.out} 与
     * {@code temp/p30-iic/diff-note.md}。
     */
    public static final double GROTTO_OPEN_THR = 0.21D;
    /** 潭深帽（格）：wTop ≤ floorY+本值（N8 钳制之外的深度上限）。 */
    public static final int GROTTO_WATER_DEPTH_MAX = 2;
    /** 洞草趟数/chunk（每趟 hash 掷一次本 chunk 列，命中有效列才落草）。 */
    public static final int GROTTO_GRASS_ROLLS = 24;
    /** 洞草掷骰盐（= S_SWAMP_GROTTO + 0x12×2，等差族内独立一格）。 */
    public static final long S_SWAMP_GROTTO_GRASS = 0x6811C43DL;

    private GrottoCarver() {}

    /**
     * <b>浅洞 m 场门值</b> ∈ [0,1]：{@code s01((valueNoise(seed^S_SWAMP_GROTTO, x/53, z/53) −
     * 0.62)/0.18)}——深腹地 1.0、域外精确 0.0（{@link #s01} 带外常值）。选址/洞深/顶厚/潭区
     * 全部由这一个门值分级（单场分档，无第二张表）。<b>纯函数</b>。
     */
    public static double grottoMAt(long worldSeed, int x, int z) {
        return s01(
            (GTSRWorldgenHash.valueNoise(worldSeed ^ S_SWAMP_GROTTO, x / GROTTO_SCALE, z / GROTTO_SCALE)
                - GROTTO_GATE_LO) / GROTTO_GATE_SPAN);
    }

    /**
     * <b>潭顶塌口独立噪声</b> ∈ [−1,1)：{@code valueNoise(seed^S_SWAMP_GROTTO_OPEN, x/21, z/21)}
     * ——与 m 场不同盐不同波长 ⇒ 塌口斑与洞形独立（潭区内约 30% 列塌口见天，占比由
     * {@link #GROTTO_OPEN_THR} 校准）。<b>纯函数</b>。
     */
    public static double grottoOpenNoiseAt(long worldSeed, int x, int z) {
        return GTSRWorldgenHash
            .valueNoise(worldSeed ^ S_SWAMP_GROTTO_OPEN, x / GROTTO_OPEN_SCALE, z / GROTTO_OPEN_SCALE);
    }

    /**
     * <b>浅洞选址谓词</b>：m ≥ 0.5 且以下六门全真（引用来源单一真值，无复刻）：
     * <ol>
     * <li>m ≥ 0.5（{@link #grottoMAt}，最便宜先行短路——域外列零后续求值）；</li>
     * <li>roster == 3（{@link ProsperityTerrainProfile#chainRosterIndexAt} coarse 1:4 身份面
     * ——只在喷气沼泽开洞）；</li>
     * <li>{@link TerrainVariants#swampTierAt} == NONE（避三档水体：不在已有表面池/深水池/水沼地上开洞）；</li>
     * <li>!{@link GTSRVoronoiRiverField#swampPoolWaterAt}（避微池水网）；</li>
     * <li>{@link TerrainVariants#swampInteriorGateAt} ≥ {@link #GROTTO_EDGE_GATE_MIN}
     * （避群系边缘——P30 II-AB 连续边缘门，与微池腿 0.5 同门严一档）；</li>
     * <li>{@link TerrainVariants#lakePlaneTerrainAllowedAt} &gt; 0.5（避 sanzu 湖平面压力域
     * ——湖滩列不开洞）∧ !{@link GTSRVoronoiRiverField#isDryRiverColumn}（避枯竭河床）。</li>
     * </ol>
     * <b>纯函数</b>、零 {@code net.minecraft} 依赖；probe（P30GrottoProbe post）与生产 carve 共用本出口。
     */
    public static boolean grottoSiteAt(long worldSeed, int x, int z) {
        if (grottoMAt(worldSeed, x, z) < 0.5D) {
            return false;
        }
        return siteGates(worldSeed, x, z);
    }

    /** 选址六门（m 门之外；独立成法供 carve 内联 m 后复用，避免二次 m 求值）。 */
    private static boolean siteGates(long worldSeed, int x, int z) {
        final int roster = ProsperityTerrainProfile.chainRosterIndexAt(worldSeed, x >> 2, z >> 2);
        if (roster != 3) {
            return false;
        }
        if (TerrainVariants.swampTierAt(worldSeed, x, z, roster) != TerrainVariants.SWAMP_TIER_NONE) {
            return false;
        }
        if (GTSRVoronoiRiverField.swampPoolWaterAt(worldSeed, x, z, roster)) {
            return false;
        }
        if (TerrainVariants.swampInteriorGateAt(worldSeed, x, z) < GROTTO_EDGE_GATE_MIN) {
            return false;
        }
        if (TerrainVariants.lakePlaneTerrainAllowedAt(worldSeed, x, z) <= 0.5D) {
            return false;
        }
        return !GTSRVoronoiRiverField.isDryRiverColumn(worldSeed, x, z);
    }

    /** 潭区谓词：m ≥ {@link #GROTTO_WATER_M}（洞底置水候选列；t 在该域恒全厚 ⇒ 塌口只由独立噪声定）。 */
    public static boolean grottoWetAt(long worldSeed, int x, int z) {
        return grottoMAt(worldSeed, x, z) >= GROTTO_WATER_M;
    }

    /**
     * <b>塌口/开放圈谓词</b>：t==0（m≤{@link #GROTTO_RIM_M} 洞缘开放圈）∨（wet ∧
     * openNoise ≥ {@link #GROTTO_OPEN_THR}）。open 列 yTop=h0（腔直通地表），留顶列 yTop=h0−t。
     */
    public static boolean grottoOpenAt(long worldSeed, int x, int z) {
        final double m = grottoMAt(worldSeed, x, z);
        if (roofThicknessAt(m) == 0) {
            return true;
        }
        return m >= GROTTO_WATER_M && grottoOpenNoiseAt(worldSeed, x, z) >= GROTTO_OPEN_THR;
    }

    /** 洞底 y：{@code heightAt − round(3 + 5·m)}（site 列专用；floorY 处统一写 prosperitySwampTop）。 */
    public static int grottoFloorYAt(long worldSeed, int x, int z) {
        final double m = grottoMAt(worldSeed, x, z);
        return ProsperityTerrainProfile.heightAt(worldSeed, x, z)
            - (int) Math.round(GROTTO_DEPTH_BASE + GROTTO_DEPTH_SPAN * m);
    }

    /**
     * <b>post-carve 最高实心 y</b>（列级纯函数重算，供潭的 8 邻钳制与未来消费面）：
     * 非 site 列 = {@link ProsperityTerrainProfile#heightAt}（未挖）；open 列 = floorY
     * （洞底即最高实心）；留顶列 = h0 − t（顶盖顶）。跨 chunk 邻列只算坐标不读块
     * （"任一 chunk 可独立重算"纪律）。
     */
    public static int grottoCarveTopAt(long worldSeed, int x, int z) {
        final double m = grottoMAt(worldSeed, x, z);
        if (m < 0.5D || !siteGates(worldSeed, x, z)) {
            return ProsperityTerrainProfile.heightAt(worldSeed, x, z);
        }
        if (roofThicknessAt(m) == 0 || (m >= GROTTO_WATER_M && grottoOpenNoiseAt(worldSeed, x, z) >= GROTTO_OPEN_THR)) {
            return grottoFloorYAt(worldSeed, x, z);
        }
        return ProsperityTerrainProfile.heightAt(worldSeed, x, z) - roofThicknessAt(m);
    }

    /** 顶厚（格）：round(GROTTO_ROOF_T·s01((m−RIM)/(FULL−RIM)))——m≤RIM ⇒ 0，m≥FULL ⇒ 全厚 4。 */
    private static int roofThicknessAt(double m) {
        return (int) Math.round(GROTTO_ROOF_T * s01((m - GROTTO_RIM_M) / (GROTTO_ROOF_FULL_M - GROTTO_RIM_M)));
    }

    /**
     * <b>本 chunk 的浅洞 carve</b>（唯一生产入口，CPR {@code onPopulate} 后置链末段调用——表层
     * 替换与河流/湖/微池置水全部落定之后再开口，洞腔空气/潭水/洞草不与既有水体通道互相干扰）。
     * 16×16 逐列：m 门先行短路（域外 chunk 近零成本——一次 valueNoise/列），site 列才求六门；
     * 腔置空气 → floorY 写 {@code prosperitySwampTop} → 潭置水（{@link GTSRRiverPlacer#waterMaterial()}，
     * N8 钳制 + 深度帽）→ 草趟（{@link #GROTTO_GRASS_ROLLS} 次 hash 掷列，有效列 =
     * 洞腔列 ∧ 无水，落 {@code prosperityTuftCopper}）。<b>不消费 populate 的 Random</b>；
     * {@code world} 形参按 {@code GTSRRiverPlacer.place} 同款保留（写入全经 sink）。
     */
    public static void carve(World world, long worldSeed, int chunkX, int chunkZ, BlockSink sink) {
        final int baseX = chunkX << 4;
        final int baseZ = chunkZ << 4;
        final Block air = Blocks.air;
        final Block water = GTSRRiverPlacer.waterMaterial();
        // 列级一趟缓存（16×16；第二趟草趟直接读，不重求谓词）
        final boolean[] site = new boolean[16 * 16];
        final int[] floorYs = new int[16 * 16];
        final int[] wTops = new int[16 * 16];
        int cavityCells = 0;
        int waterCells = 0;
        int grassPlaced = 0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                final int x = baseX + lx;
                final int z = baseZ + lz;
                final double m = grottoMAt(worldSeed, x, z);
                if (m < 0.5D || !siteGates(worldSeed, x, z)) {
                    continue;
                }
                final int h0 = ProsperityTerrainProfile.heightAt(worldSeed, x, z);
                final int floorY = h0 - (int) Math.round(GROTTO_DEPTH_BASE + GROTTO_DEPTH_SPAN * m);
                final int t = roofThicknessAt(m);
                final boolean wet = m >= GROTTO_WATER_M;
                final boolean open = t == 0 || (wet && grottoOpenNoiseAt(worldSeed, x, z) >= GROTTO_OPEN_THR);
                final int yTop = open ? h0 : h0 - t;
                final int i = lz * 16 + lx;
                site[i] = true;
                floorYs[i] = floorY;
                // 腔置空气（site 列腔恒非空：depth≥6、t≤4 ⇒ 高度 ≥2）
                for (int y = floorY + 1; y <= yTop; y++) {
                    sink.setBlock(x, y, z, air, 0, BlockSink.FLAG_POPULATE);
                    cavityCells++;
                }
                // 洞底统一沼泽 top（无条件覆写口径见类注释）
                sink.setBlock(x, floorY, z, BlocksGTSR.prosperitySwampTop, 0, BlockSink.FLAG_POPULATE);
                // 潭：N8 钳制（8 邻 post-carve 顶 −1）∩ 深度帽，wTop ≥ floorY+1 才置水
                wTops[i] = Integer.MIN_VALUE;
                if (wet) {
                    int wTop = floorY + GROTTO_WATER_DEPTH_MAX;
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dz == 0) {
                                continue;
                            }
                            final int barrier = grottoCarveTopAt(worldSeed, x + dx, z + dz) - 1;
                            if (barrier < wTop) {
                                wTop = barrier;
                            }
                        }
                    }
                    if (wTop >= floorY + 1) {
                        wTops[i] = wTop;
                        for (int y = floorY + 1; y <= wTop; y++) {
                            if (sink.setBlock(x, y, z, water, 0, BlockSink.FLAG_POPULATE)) {
                                waterCells++;
                            }
                        }
                    }
                }
            }
        }
        // 草趟：每趟 hash 掷本 chunk 一列（chunkSeed 掺草盐 + 趟次乘子），有效 = 洞腔列 ∧ 无水
        final Block tuft = BlocksGTSR.prosperityTuftCopper;
        for (int roll = 0; roll < GROTTO_GRASS_ROLLS; roll++) {
            final long key = GTSRWorldgenHash.chunkSeed(worldSeed ^ S_SWAMP_GROTTO_GRASS, chunkX, chunkZ)
                ^ (roll * GTSRWorldgenHash.CELL_MUL_X);
            final int lx = (int) (roll01(key) * 16.0D);
            final int lz = (int) (roll01(key ^ GTSRWorldgenHash.CELL_MUL_Z) * 16.0D);
            final int i = lz * 16 + lx;
            if (!site[i] || wTops[i] >= floorYs[i] + 1 || tuft == null) {
                continue; // 非洞腔列 / 草不落水（floorY+1 ≤ wTop = 有水）/ 离线未装配
            }
            if (sink.setBlock(baseX + lx, floorYs[i] + 1, baseZ + lz, tuft, 0, BlockSink.FLAG_POPULATE)) {
                grassPlaced++;
            }
        }
        CAVITY_CELLS.addAndGet(cavityCells);
        WATER_CELLS.addAndGet(waterCells);
        GRASS_PLACED.addAndGet(grassPlaced);
        if (CHUNKS_SERVED.incrementAndGet() % LOG_WINDOW_CHUNKS == 0) {
            GTSteamReborn.LOG.info(
                "[GTSR] dim78 swamp grotto over {} chunks: cavityCells={} waterCells={} grassPlaced={}"
                    + " (pure-predicate surface grotto, P30 II-C)",
                CHUNKS_SERVED.get(),
                CAVITY_CELLS.get(),
                WATER_CELLS.get(),
                GRASS_PLACED.get());
        }
    }

    /** smoothstep 带通（clamp[0,1] 后 3t²−2t³；带外精确 0.0/1.0——TerrainVariants/RVF 同名私有帮手同口径）。 */
    private static double s01(double t) {
        final double c = Math.max(0.0D, Math.min(1.0D, t));
        return c * c * (3.0D - 2.0D * c);
    }

    /** splitmix64 → [0,1)（高 53 位归一；RVF hash01 同口径，确定性纯函数）。 */
    private static double roll01(long key) {
        return (GTSRWorldgenHash.splitmix64(key) >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR;
    }
}
