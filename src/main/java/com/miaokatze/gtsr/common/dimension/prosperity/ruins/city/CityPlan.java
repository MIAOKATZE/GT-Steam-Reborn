package com.miaokatze.gtsr.common.dimension.prosperity.ruins.city;

import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;

/**
 * 古代城地块/街道纯函数描述（dim1 S4b，plan §3.2 街道与地块 + §3.1 渲染协议）。
 * <p>
 * 坐标系：街道网世界坐标轴向对齐——主街间距 16 格、街宽 3 格（锈石铺面 meta5，街中线每
 * 8 格嵌一枚轨枕 meta0）；过城市中心的两条主街加宽至 5 格（"中央大道"）。街网围出 13×13
 * 地块：plot (k,l) 占 u∈[k*16+2, k*16+14]（u = wx - centerX）。plotSeed = cellSeed ^
 * (plotIndex * 0x9E37)（plotIndex = k*1000003+l，k/l 组合编码，plan 公式派生）。
 * <p>
 * 分区（plot 中心距中心 r = max(|k|,|l|)，chunk 口径）：r&lt;2 核心区（civic 池）/
 * 2≤r&lt;5 工业环（halls+infra 池）/ r≥5 边缘废墟环（towers+rubble 池，空地率↑）。
 * 地块 roll：空地 25%（核心/工业）或 45%（边缘，"城市消融进荒野"），空地落 rubble 小件。
 * <p>
 * <b>城界（dim78-fix S-A3 异形边界）</b>：城市不再是欧氏圆——边界为 Fourier 径向扰动轮廓
 * r(θ) = radiusBlocks × (1 + Σ_{k=1..3} a_k·cos(kθ + φ_k))（θ = atan2(dv,du)，du/dv 为
 * 至中心偏移；a_k/φ_k 由 cellSeed 经 {@link CityPlanner#mix} 派生，构造期定值）。振幅硬约束
 * Σ|a_k|·radiusBlocks ≤ {@link #MAX_EDGE_PERTURBATION} 格（构造期等比钳制），故最大延伸
 * radiusBlocks+12 ≤ (radius+1)*16 恰在缓冲窗内、reach（radius*16+32）内余 16+ 格。
 * {@link #insideCity(int, int)} 为<b>全城唯一边界判定</b>：plot 圆界（{@link #plotInCircle}）
 * 与街道裁剪（{@link #forEachStreetColumn}）一律委托之——全仓禁止旁路欧氏圆独立实现。
 * <p>
 * <b>渲染协议（plan §3.1）</b>：populate chunk C 时只遍历 footprint 与 C 相交的地块，重算
 * 同一纯函数；{@link PlotVisitor}/{@link StreetVisitor} 回调面向纯几何（本类零 Minecraft
 * 依赖），写入钳制由调用方（ChunkSliceSink+ChunkClampedSink）协议层完成——锚点一次性副作用
 * 不存在（城内无 TE/箱子，纯方块，天然幂等）。
 * <p>
 * <b>高度红线</b>：一切落地 y 由消费方经 {@code ProsperityTerrainProfile.heightAt}（同源
 * 纯函数）逐列给出，本类不携带高度、不读方块。
 */
public final class CityPlan {

    /** 街间距（格；plan §3.2）。 */
    public static final int STREET_SPACING = 16;
    /** 普通街宽（格）。 */
    public static final int STREET_WIDTH = 3;
    /** 中央大道宽（格）。 */
    public static final int AVENUE_WIDTH = 5;
    /** 地块进深（16 间距 - 3 街宽 = 13，plan §3.2）。 */
    public static final int PLOT_DEPTH = STREET_SPACING - STREET_WIDTH;

    private static final long PLOT_SALT_TIER = 0x71E2L; // district roll 分路盐

    /**
     * 振幅硬约束（格）：Σ|a_k|·radiusBlocks ≤ 12（&lt;16 硬上限，SanityCheck reach =
     * radius*16+32 余 16+ 格；dim78-fix S-A3）。
     */
    public static final double MAX_EDGE_PERTURBATION = 12.0;
    /** 单谐波振幅候选域下界（radiusBlocks 比例）。 */
    private static final double EDGE_AMP_MIN = 0.012;
    /**
     * 单谐波振幅候选域上界（radiusBlocks 比例）：Σ 候选 ≤ 0.108，7 chunk 城（112 格）未钳
     * 扰动至多 ≈ 12.1 格 → 构造期等比钳制兜底；4 chunk 城天然在约束内。
     */
    private static final double EDGE_AMP_MAX = 0.036;
    /** a_k 派生盐基（mix(cellSeed, 盐基+k)，k=1..3；与 CityPlanner.planFor 的 1..4 盐分路）。 */
    private static final long SALT_EDGE_AMPLITUDE = 0xA1B0L;
    /** φ_k 派生盐基（mix(cellSeed, 盐基+k)，k=1..3）。 */
    private static final long SALT_EDGE_PHASE = 0xC0D0L;
    /** 2π（φ_k 相位域）。 */
    private static final double TWO_PI = Math.PI * 2.0;

    private final long worldSeed;
    private final long cellSeed;
    private final int centerChunkX;
    private final int centerChunkZ;
    private final int radiusChunks;
    private final int centerX;
    private final int centerZ;
    private final int radiusBlocks;
    /** k 次谐波振幅（radiusBlocks 比例；构造期钳制后定值）。 */
    private final double amp1;
    private final double amp2;
    private final double amp3;
    /** k 次谐波相位（弧度 [0,2π)；构造期定值）。 */
    private final double phi1;
    private final double phi2;
    private final double phi3;

    CityPlan(long worldSeed, long cellSeed, int centerChunkX, int centerChunkZ, int radiusChunks) {
        this.worldSeed = worldSeed;
        this.cellSeed = cellSeed;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.radiusChunks = radiusChunks;
        this.centerX = centerChunkX * 16 + 8;
        this.centerZ = centerChunkZ * 16 + 8;
        this.radiusBlocks = radiusChunks * 16;
        // —— 城界 Fourier 参数派生（S-A3）：a_k/φ_k 全由 cellSeed + 盐基+k 决定，纯函数 ——
        double a1 = EDGE_AMP_MIN
            + (EDGE_AMP_MAX - EDGE_AMP_MIN) * frac(CityPlanner.mix(cellSeed, SALT_EDGE_AMPLITUDE + 1));
        double a2 = EDGE_AMP_MIN
            + (EDGE_AMP_MAX - EDGE_AMP_MIN) * frac(CityPlanner.mix(cellSeed, SALT_EDGE_AMPLITUDE + 2));
        double a3 = EDGE_AMP_MIN
            + (EDGE_AMP_MAX - EDGE_AMP_MIN) * frac(CityPlanner.mix(cellSeed, SALT_EDGE_AMPLITUDE + 3));
        this.phi1 = TWO_PI * frac(CityPlanner.mix(cellSeed, SALT_EDGE_PHASE + 1));
        this.phi2 = TWO_PI * frac(CityPlanner.mix(cellSeed, SALT_EDGE_PHASE + 2));
        this.phi3 = TWO_PI * frac(CityPlanner.mix(cellSeed, SALT_EDGE_PHASE + 3));
        // 振幅硬约束钳制（构造期；Σ|a_k|·radiusBlocks ≤ 12 格，超限整族等比缩，保持频谱特征）
        final double sumAbs = a1 + a2 + a3; // 候选振幅均非负
        final double maxSum = MAX_EDGE_PERTURBATION / this.radiusBlocks;
        final double scale = sumAbs > maxSum ? maxSum / sumAbs : 1.0;
        this.amp1 = a1 * scale;
        this.amp2 = a2 * scale;
        this.amp3 = a3 * scale;
    }

    /** 长整哈希 → [0,1) 双精度（丢低 11 位取 53 位精度的标准映射）。 */
    private static double frac(long v) {
        return (v >>> 11) * 0x1.0p-53;
    }

    public long getWorldSeed() {
        return this.worldSeed;
    }

    public long getCellSeed() {
        return this.cellSeed;
    }

    public int getCenterChunkX() {
        return this.centerChunkX;
    }

    public int getCenterChunkZ() {
        return this.centerChunkZ;
    }

    public int getRadiusChunks() {
        return this.radiusChunks;
    }

    public int getCenterX() {
        return this.centerX;
    }

    public int getCenterZ() {
        return this.centerZ;
    }

    /** chunk 是否在城市缓冲窗（半径+1 chunk，plan §3.4；渲染与"城内跳过"同口径）。 */
    public boolean chunkInBuffer(int chunkX, int chunkZ) {
        return Math.abs(chunkX - this.centerChunkX) <= this.radiusChunks + 1
            && Math.abs(chunkZ - this.centerChunkZ) <= this.radiusChunks + 1;
    }

    // ═══ 城界（单一判定；dim78-fix S-A3 Fourier 径向扰动）═══

    /**
     * 世界坐标 (wx,wz) 是否落在城界内——<b>全城唯一边界判定</b>（S-A3 红线：plot 圆界与
     * 街道裁剪一律委托本函数，全仓禁止旁路欧氏圆独立实现）。边界 = Fourier 径向扰动轮廓：
     * <p>
     * {@code r(θ) = radiusBlocks × (1 + Σ_{k=1..3} a_k·cos(k·θ + φ_k))}，θ = atan2(dv,du)
     * （du/dv = (wx,wz) 至中心偏移）；a_k/φ_k 由 cellSeed 派生（构造期定值），振幅满足硬约束
     * Σ|a_k|·radiusBlocks ≤ {@value #MAX_EDGE_PERTURBATION} 格（构造期等比钳制），保证最大
     * 延伸 radiusBlocks+12 在缓冲窗（radius+1 chunk）内、SanityCheck reach（radius*16+32）
     * 内余 16+ 格，3×3 cell 检索不漏检。
     */
    public boolean insideCity(int wx, int wz) {
        final double du = wx - this.centerX;
        final double dv = wz - this.centerZ;
        final double distSq = du * du + dv * dv;
        // 快速外退：扰动上界 12 格，超出 (radiusBlocks+12)² 必在城外，跳过三角函数
        final double rOuter = this.radiusBlocks + MAX_EDGE_PERTURBATION;
        if (distSq >= rOuter * rOuter) {
            return false;
        }
        final double theta = Math.atan2(dv, du);
        final double rBoundary = this.radiusBlocks * (1.0 + this.amp1 * Math.cos(theta + this.phi1)
            + this.amp2 * Math.cos(2.0 * theta + this.phi2)
            + this.amp3 * Math.cos(3.0 * theta + this.phi3));
        return distSq <= rBoundary * rBoundary;
    }

    /** 钳制后总扰动幅值 Σ|a_k|·radiusBlocks（格；≤ {@value #MAX_EDGE_PERTURBATION}，自证用）。 */
    public double edgePerturbationBlocks() {
        return (Math.abs(this.amp1) + Math.abs(this.amp2) + Math.abs(this.amp3)) * this.radiusBlocks;
    }

    // ═══ 街道（纯几何）═══

    /** 普通街带：u ≡ -1/0/+1 (mod 16)。 */
    private static boolean streetBand(int u) {
        final int m = Math.floorMod(u, STREET_SPACING);
        return m <= 1 || m >= STREET_SPACING - 1;
    }

    /** (wx,wz) 是否街道格（中央大道宽 5，普通街宽 3）。 */
    public boolean onStreet(int wx, int wz) {
        final int u = wx - this.centerX;
        final int v = wz - this.centerZ;
        final boolean sx = Math.abs(u) <= AVENUE_WIDTH / 2 || streetBand(u);
        final boolean sz = Math.abs(v) <= AVENUE_WIDTH / 2 || streetBand(v);
        return sx || sz;
    }

    /** 街中线（轨枕嵌位线）：u ≡ 0 (mod 16) 或中央大道中线。 */
    private static boolean streetCenterLine(int u) {
        return Math.floorMod(u, STREET_SPACING) == 0;
    }

    // ═══ 地块（纯决策）═══

    /** plotIndex 组合编码（k/l → 单值；plan 公式 plotSeed = cellSeed ^ (plotIndex*0x9E37)）。 */
    private static long plotIndex(int k, int l) {
        return k * 1000003L + l;
    }

    /** plotSeed（plan §3.2 公式原样，plotIndex 组合编码为派生口径）。 */
    public long plotSeed(int k, int l) {
        return this.cellSeed ^ (plotIndex(k, l) * 0x9E37L);
    }

    /** plot (k,l) 中心是否落在城界内（委托 {@link #insideCity(int, int)} 单一判定；S-A3 异形边界，与街道裁剪同口径）。 */
    public boolean plotInCircle(int k, int l) {
        return insideCity(this.centerX + k * STREET_SPACING + 8, this.centerZ + l * STREET_SPACING + 8);
    }

    /** plot (k,l) 的世界原点（最小角；u = k*16+2）。 */
    public int plotOriginX(int k) {
        return this.centerX + k * STREET_SPACING + 2;
    }

    public int plotOriginZ(int l) {
        return this.centerZ + l * STREET_SPACING + 2;
    }

    /** 分区（plan §3.2：0=核心 / 1=工业环 / 2=边缘废墟环；r = max(|k|,|l|) chunk 口径）。 */
    public int districtOf(int k, int l) {
        final int r = Math.max(Math.abs(k), Math.abs(l));
        return r < 2 ? 0 : r < 5 ? 1 : 2;
    }

    /** plot 是否空地（核心/工业 25% / 边缘 45%，plotSeed 哈希）。 */
    public boolean plotEmpty(int k, int l) {
        final int threshold = districtOf(k, l) == 2 ? 45 : 25;
        return CityPlanner.mix(plotSeed(k, l), PLOT_SALT_TIER) % 100L < threshold;
    }

    /**
     * plot 建筑变体选型（分区池 + plotSeed 哈希；空地由 {@link #plotEmpty} 先行判定）。
     * 池构成照 plan §3.2 分区：核心 civic / 工业环 halls+infra / 边缘 towers+rubble。
     */
    public String plotVariant(int k, int l) {
        final long plotSeed = plotSeed(k, l);
        final String[] pool;
        switch (districtOf(k, l)) {
            case 0:
                pool = CORE_POOL;
                break;
            case 1:
                pool = INDUSTRIAL_POOL;
                break;
            default:
                pool = EDGE_POOL;
        }
        return pool[(int) (CityPlanner.mix(plotSeed, 0x51CE) % pool.length)];
    }

    // 变体池（注册名 = CityVariants.ALL 基础名）
    private static final String[] CORE_POOL = { "dome_hall", "clock_tower", "market_colonnade", "manor_ruin",
        "tram_depot", "fountain_basin" };
    private static final String[] INDUSTRIAL_POOL = { "boiler_house", "pump_house", "forge_hall", "engine_room",
        "gas_holder", "pressure_tank_row", "chimney_stack", "cooling_tower", "gear_tower", "viaduct", "crane_ruin",
        "rail_platform", "canal_gate", "broken_bridge" };
    private static final String[] EDGE_POOL = { "watch_tower", "water_tower", "chimney_stack", "gear_tower",
        "cooling_tower", "clock_tower", "fallen_arch", "broken_pillars", "machine_plinth", "slag_heap" };
    private static final String[] RUBBLE_POOL = { "broken_pillars", "slag_heap", "fallen_arch", "machine_plinth" };

    /** 空地 rubble 小件选型（边缘环密度件）。 */
    public String plotRubble(int k, int l) {
        return RUBBLE_POOL[(int) (CityPlanner.mix(plotSeed(k, l), 0xB088L) % RUBBLE_POOL.length)];
    }

    // ═══ 渲染遍历（纯几何回调；写入由消费方 Sink 协议钳制）═══

    /** 街道列回调（每列一次；sleeper=街中线轨枕嵌位）。 */
    public interface StreetVisitor {

        void column(int wx, int wz, boolean sleeper);
    }

    /**
     * 遍历 chunk 内的街道格（城界内；确定性列序）。
     */
    public void forEachStreetColumn(int chunkX, int chunkZ, StreetVisitor visitor) {
        final int baseX = chunkX * 16;
        final int baseZ = chunkZ * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int wx = baseX + x;
                final int wz = baseZ + z;
                final int du = wx - this.centerX;
                final int dv = wz - this.centerZ;
                if (!insideCity(wx, wz)) {
                    continue; // 城界外（街道只在城内；与 plotInCircle 同口径 = insideCity 单一判定）
                }
                if (!onStreet(wx, wz)) {
                    continue;
                }
                final boolean onXLine = streetCenterLine(du) || Math.abs(du) <= AVENUE_WIDTH / 2;
                final boolean onZLine = streetCenterLine(dv) || Math.abs(dv) <= AVENUE_WIDTH / 2;
                // 轨枕嵌位：沿街中线、每 8 格一枚（沿街轴 = 中线，行进轴 = mod 8）
                final boolean sleeper = onXLine && Math.floorMod(dv, 8) == 0 || onZLine && Math.floorMod(du, 8) == 0;
                visitor.column(wx, wz, sleeper);
            }
        }
    }

    /** 地块渲染回调（仅 footprint 与 chunk 相交的地块；plan §3.1 渲染协议）。 */
    public interface PlotVisitor {

        void plot(String variantName, int originX, int originZ, long plotSeed);
    }

    /**
     * 遍历 footprint 与 chunk 相交的地块并回调变体放置参数。
     * plot 网格 = plot 中心 (k*16+8, l*16+8) 落在城界内（{@link #plotInCircle} 委托
     * {@link #insideCity(int, int)} 单一判定，与街道裁剪同口径）；bbox 相交 = plot 矩形
     * （原点扩 footprint 余量 16）与 chunk 矩形相交。
     */
    public void forEachPlotInChunk(int chunkX, int chunkZ, PlotVisitor visitor) {
        final int kLim = this.radiusBlocks / STREET_SPACING + 1;
        final int kMin = -kLim;
        final int kMax = kLim;
        final int minX = chunkX * 16 - 16; // 变体 footprint 外溢余量（最大 16 长）
        final int minZ = chunkZ * 16 - 16;
        final int maxX = minX + 47; // 16 + 16 + 15
        final int maxZ = minZ + 47;
        for (int k = kMin; k <= kMax; k++) {
            final int px = plotOriginX(k);
            if (px < minX - PLOT_DEPTH - 16 || px > maxX + 16) {
                continue; // 快速横切（原点沿 x 单调，间距 16）
            }
            for (int l = kMin; l <= kMax; l++) {
                if (!plotInCircle(k, l)) {
                    continue; // 城界外（plot 中心经 insideCity 单一判定）
                }
                final int pz = plotOriginZ(l);
                // plot bbox [px, px+PLOT_DEPTH-1]×[pz, pz+PLOT_DEPTH-1] 外扩 footprint 余量后与 chunk 相交
                if (px + PLOT_DEPTH - 1 + 16 < minX || px - 16 > maxX
                    || pz + PLOT_DEPTH - 1 + 16 < minZ
                    || pz - 16 > maxZ) {
                    continue;
                }
                final boolean empty = plotEmpty(k, l);
                final String variant = empty ? plotRubble(k, l) : plotVariant(k, l);
                // plot 内居中锚点（旋转后 footprint 尺寸参与；place 内不再平移）
                final long plotSeed = plotSeed(k, l);
                final CityVariants.Variant v = CityVariants.byName(variant);
                final int[] rs = v != null
                    ? StructureBuilder.rotateSize(v.sizeX, v.sizeZ, CityVariants.rotationOf(plotSeed))
                    : new int[] { PLOT_DEPTH, PLOT_DEPTH };
                final int anchorX = px + (PLOT_DEPTH - rs[0]) / 2;
                final int anchorZ = pz + (PLOT_DEPTH - rs[1]) / 2;
                visitor.plot(variant, anchorX, anchorZ, plotSeed);
            }
        }
    }

    /**
     * 规划描述规范化串（确定性自证用：同 seed 同 cell 两次规划逐字节一致 → 本串相等）。
     * 覆盖中心/半径/全部 plot 决策（变体/朝向/损伤档）与街道参数。
     */
    public String describe() {
        final StringBuilder sb = new StringBuilder(1 << 16);
        sb.append("city(center=")
            .append(this.centerX)
            .append(',')
            .append(this.centerZ)
            .append("; centerChunk=")
            .append(this.centerChunkX)
            .append(',')
            .append(this.centerChunkZ)
            .append("; radius=")
            .append(this.radiusChunks)
            .append(")\n");
        final int lim = this.radiusBlocks / STREET_SPACING + 1;
        for (int k = -lim; k <= lim; k++) {
            for (int l = -lim; l <= lim; l++) {
                if (!plotInCircle(k, l)) {
                    continue;
                }
                final long plotSeed = plotSeed(k, l);
                sb.append("plot(")
                    .append(k)
                    .append(',')
                    .append(l)
                    .append(") district=")
                    .append(districtOf(k, l))
                    .append(" empty=")
                    .append(plotEmpty(k, l))
                    .append(" variant=")
                    .append(plotEmpty(k, l) ? plotRubble(k, l) : plotVariant(k, l))
                    .append(" rot=")
                    .append(CityVariants.rotationOf(plotSeed))
                    .append(" tier=")
                    .append(CityVariants.damageTier(plotSeed))
                    .append('\n');
            }
        }
        return sb.toString();
    }
}
