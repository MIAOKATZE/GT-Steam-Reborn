package com.miaokatze.gtsr.common.dimension.prosperity.ruins.city;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;

/**
 * 古代城地块/街道纯函数描述（dim1 S4b，plan §3.2 街道与地块 + §3.1 渲染协议）。
 * <p>
 * 坐标系：街道网世界坐标轴向对齐——主街间距 16 格、街宽 3 格（锈石铺面 meta5，街中线每
 * 8 格嵌一枚轨枕 meta0）；过城市中心的两条主街加宽至 5 格（"中央大道"）。街网围出 13×13
 * 地块：plot (k,l) 占 u∈[k*16+2, k*16+14]（u = wx - centerX）。plotSeed = cellSeed ^
 * (plotIndex * 0x9E37)（plotIndex = k*1000003+l，k/l 组合编码，plan 公式派生）。
 * <p>
 * <b>分区（v1.20.34 起沿主街分档）</b>：plot 中心距最近中央大道的曼哈顿距离 md
 * （md = min(dk,dl)，dk = k≥0 ? k : -1-k，dl 同理；plot 步数口径）：md&lt;2 核心区（civic 池）/
 * md&lt;4 工业环（halls+infra 池）/ md≥4 边缘废墟环（towers+rubble 池，空地率↑）——档位跟随
 * 异形轮廓自然弯折（沿主街为骨、远端为缘），替代旧同心方环 max(|k|,|l|)。
 * 地块 roll：空地 25%（核心/工业）或 45%（边缘，"城市消融进荒野"），空地落 rubble 小件。
 * <p>
 * <b>城界（v1.20.34 单城形态异形化：原版村庄式不规则轮廓）</b>：城市不再是近圆盘——边界为
 * 「各向异性 xy 缩放 ⊗ 三频 Fourier 径向扰动」复合轮廓（同 seed 构造期定值）：
 * <ol>
 * <li>各向异性：坐标先旋转到 cellSeed 派生的主轴朝向 ψ，拉长轴除以 (1+w)、压缩轴除以
 * (1-w)（w ∈ [0.03,0.10]），把圆盘基座扭成椭圆（破坏径向对称）；</li>
 * <li>Fourier：warp 空间内 r(θ) = R×(1 + a₂cos(2θ+φ₂) + a₃cos(3θ+φ₃) + a₅cos(5θ+φ₅))
 * ——2/3/5 三频（奇次破上下/左右对称）、相位各自随机；</li>
 * <li>总预算：世界侧最大延伸 ≤ R×(1+TOT)，TOT ∈ [0.25,0.35]（城半径的 25-35%，随半径
 * 自适应——4 chunk 城 16-22 格、7 chunk 城 28-39 格；替代旧固定 12 格钳制）。谐波族预算
 * Σaₖ ≤ (1+TOT)/(1+w)−1 构造期钳定，保证上述包络。</li>
 * </ol>
 * {@link #insideCity(int, int)} 仍为<b>全城唯一边界判定</b>：街道裁剪
 * （{@link #forEachStreetColumn}）与 plot 几何门（{@link #plotInCircle} 的偏置采样）一律
 * 委托之——全仓禁止旁路独立边界实现（S-A3 红线不变）。
 * <p>
 * <b>plot 裁剪（悬突/内凹）</b>：plot 几何门不是光滑的"中心在界内"——采样点沿径向偏移
 * 一个低频噪声偏置 {@link #plotEdgeBias(int, int)}（±7% 城半径，波长 ≈ 2.7 plot，成簇
 * 悬突/内凹）后再问 {@link #insideCity(int, int)}，城界附近的地块取舍因此非光滑。
 * <b>连通性</b>：次街按噪声分段断开（见下），任何因断路而与中央大道失去四邻接连通的
 * plot 在门内直接剔除（{@link #plotInCircle} = 几何门 ∧ 连通闭包；孤立地块数恒 0）。
 * <p>
 * <b>街道分段</b>：两条正交中央大道（过中心、宽 5）永续穿越全城；次街（宽 3）按
 * 「街线 × 街段」粒度断续——每条街线独立低频噪声（波长 ≈ 3.7 段）过阈值，段开放率约
 * 70-80%（断开率 10-40% 区间内），交叉格随四邻段任一开放成路口（原版村庄 Road 逐段
 * 碰撞缩短的分段观感的确定性等价方案；蓝本 build/rfg/minecraft-src MapGenVillage.Road）。
 * <p>
 * <b>渲染协议（plan §3.1）</b>：populate chunk C 时只遍历 footprint 与 C 相交的地块，重算
 * 同一纯函数；{@link PlotVisitor}/{@link StreetVisitor} 回调面向纯几何（本类零 Minecraft
 * 依赖），写入钳制由调用方（ChunkSliceSink+ChunkClampedSink）协议层完成——锚点一次性副作用
 * 不存在（城内无 TE/箱子，纯方块，天然幂等）。连通闭包为<b>懒计算纯派生</b>（首次
 * {@link #plotInCircle} 调用时构建，值只依赖构造期参数，双跑逐字节一致；1.7.10 populate
 * 单线程，无并发竞争）。
 * <p>
 * <b>缓冲窗</b>：{@link #chunkInBuffer} 的 chunk 半径 = radiusChunks + 自适应裕量
 * （总扰动 35% + plot 偏置 7% 折 chunk + (c) 内容 chunk 裕量；4 chunk 城 +3 / 7 chunk 城 +4），保证
 * 异形轮廓 + 悬突 plot 的全部内容（街道/plot/footprint 外溢，<b>含 P16-B3 申报边长 &gt;16 的城内
 * 巨构</b>）仍在渲染/抑制窗口内（渲染与"城内跳过散布"同口径，plan §3.4）。
 * <p>
 * <b>P16-B3 巨构几何余量（三处同步，单一输入 {@code CityVariants.MAX_SIDE}）</b>：渲染遍历
 * plot bbox 双侧外扩 (a) 16→24、{@link #contentReachBlocks()} 内容余量 (b) 24→32、缓冲窗
 * 内容 chunk 裕量 (c) 由写死 +1 改为 ⌈(plot 半跨+巨构外溢)/16⌉（当前边长 24 下数值仍为 1，
 * 边长 ≥30 自动升 2）。三处公式与"为什么是这个数"的推导集中写在常量区注释，扩巨构不再
 * 逐处考古（任务包 B3 item 3）。跨片写入仍走 {@code CitySliceSink}（framework {@code
 * ChunkSliceSink} 的城内薄壳），本类零第二套切片器。
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

    // ═══ P16-B3 巨构几何余量（三处同步口径；唯一输入 = CityVariants.MAX_SIDE，plan §1 G11）═══
    //
    // 旧口径三处各自写死（16 / 24 / +1），立论都是"城内最大 footprint=16 ⇒ 外溢 ≤16"。
    // 巨构（申报边长 ≤45）落地时若只放大其中一处，另两处就成了暗雷（渲染漏角 / 检索漏检 /
    // 缓冲窗外的城外结构压进巨构占地）。现在三处全部从名册现算的 MAX_SIDE 派生，
    // 任何后续扩巨构自动同步；当前读数（MAX_SIDE=24）：(a) 16→24、(b) 24→32、(c) 公式化、
    // 数值在边长 ≤29 时保持 +1 不变（7 格半跨 + 6 格外溢 = 13 ≤ 1 chunk，不无谓扩大抑制窗）。

    /** 巨构外溢（格/自 plot 边）= ⌈(最大申报边长 − 地块深)/2⌉；24 → 6（锚点居中，Java 整除口径与 {@link #forEachPlotInChunk} 一致）。 */
    private static final int MEGA_OVERHANG_BLOCKS = (CityVariants.MAX_SIDE - PLOT_DEPTH + 1) / 2;

    /** (a) 渲染遍历的 plot bbox 双侧外扩：旧=写死 16（"最大 footprint 16"假设），新=max(单 chunk, 巨构申报边长)；相交测试要求余量 ≥ 2×外溢，24 ≥ 2×6 富余一倍。 */
    private static final int FOOTPRINT_MARGIN_BLOCKS = Math.max(STREET_SPACING, CityVariants.MAX_SIDE);

    /** plot 采样点（k·16+8）到地块远边界的格数（6.5 取整放大）；(b)(c) 共用。 */
    private static final int PLOT_SAMPLE_TO_EDGE = (PLOT_DEPTH + 3) / 2;

    /** (b) 内容余量尾常数（自 maxWorldRadius+bias 再放的格数）：旧=24（=8+16），新=8+FOOTPRINT_MARGIN_BLOCKS=32，与 (a) 同一 margin 输入。 */
    private static final int CONTENT_MARGIN_BLOCKS = PLOT_SAMPLE_TO_EDGE + FOOTPRINT_MARGIN_BLOCKS;

    /** (c) 缓冲窗的内容 chunk 裕量：旧=写死 +1，新=⌈(半跨+外溢)/16⌉；13 格 → 1（当前不变），边长 ≥30 自动升 2——巨构整体 bbox 必落渲染/抑制窗内，两侧不脱钩。 */
    private static final int MEGA_SPILL_CHUNKS = Math
        .max(1, (PLOT_SAMPLE_TO_EDGE + MEGA_OVERHANG_BLOCKS + STREET_SPACING - 1) / STREET_SPACING);

    private static final long PLOT_SALT_TIER = 0x71E2L; // district roll 分路盐

    // ═══ 城界轮廓常量区（v1.20.34 异形化；集中收口便于二分回退）═══

    /** 轮廓总扰动预算占城半径比例下界（世界侧最大延伸 ≤ R×(1+0.25)）。 */
    public static final double EDGE_TOT_FRAC_MIN = 0.25;
    /** 轮廓总扰动预算占城半径比例上界（世界侧最大延伸 ≤ R×(1+0.35)；SanityCheck 对拍口径）。 */
    public static final double EDGE_TOT_FRAC_MAX = 0.35;
    /** 各向异性 xy 缩放强度域下界 w（长短轴比下限 ≈ (1+0.03)/(1-0.03) ≈ 1.06）。 */
    private static final double EDGE_ANISO_MIN = 0.03;
    /** 各向异性 xy 缩放强度域上界 w（长短轴比上限 ≈ (1+0.10)/(1-0.10) ≈ 1.22）。 */
    private static final double EDGE_ANISO_MAX = 0.10;
    /** 谐波族预算填充度下界（族预算 = 上限 × [0.8,1.0]，保证凹凸可辨不塌成圆）。 */
    private static final double EDGE_HARMONIC_FILL_MIN = 0.80;
    /** 单谐波原始权重域下界（归一化前；防某频塌零）。 */
    private static final double EDGE_W_MIN = 0.25;
    /** 总扰动预算 TOT 派生盐（mix(cellSeed, 盐)）。 */
    private static final long SALT_EDGE_TOT = 0x707L;
    /** a_k 派生盐基（mix(cellSeed, 盐基+k)，k=2/3/5；族总量=盐基本身，与 CityPlanner.planFor 的 1..4 盐分路）。 */
    private static final long SALT_EDGE_AMPLITUDE = 0xA1B0L;
    /** φ_k 派生盐基（mix(cellSeed, 盐基+k)，k=2/3/5）。 */
    private static final long SALT_EDGE_PHASE = 0xC0D0L;
    /** 各向异性强度 w 派生盐。 */
    private static final long SALT_EDGE_ANISO = 0xE77L;
    /** 各向异性主轴朝向 ψ 派生盐。 */
    private static final long SALT_EDGE_ANISO_ANGLE = 0xFA5EL;
    /** 2π（相位域）。 */
    private static final double TWO_PI = Math.PI * 2.0;

    // ═══ plot 裁剪 / 街道分段常量区 ═══

    /** plot 级低频边界偏置幅值（城半径比例；悬突/内凹深度 ≈ ±0.07R）。 */
    public static final double PLOT_EDGE_BIAS_FRAC = 0.07;
    /** plot 偏置低频噪声频率（1/plot；波长 ≈ 2.7 plot → 悬突成簇非椒盐）。 */
    private static final double PLOT_NOISE_FREQ = 0.37;
    /** plot 边界偏置噪声派生盐。 */
    private static final long SALT_PLOT_EDGE_NOISE = 0xB1A5L;
    /** 次街段断续噪声频率（段⁻¹；波长 ≈ 3.7 段 → 连 2-4 段、断 1-2 段的观感）。 */
    public static final double STREET_BREAK_FREQ = 0.27;
    /**
     * 次街段断续阈值（valueNoise ∈ [-1,1)；&lt; 阈值 = 该段断开）。t=-0.22 → 断开率均值 ≈ 27%
     * （分布实测见 CityShapeCheck），多城抽样逐城落在 10-40% 目标区间内且双侧留量。
     */
    public static final double STREET_BREAK_THRESHOLD = -0.22;
    /** 街线断续派生盐基（lineSeed = mix(cellSeed, 盐) ^ 线号×方向乘子）。 */
    private static final long SALT_STREET_LINE = 0x57EEL;
    /** 纵/横街线的线号乘子（去相关平行街线）。 */
    private static final long STREET_LINE_MUL_V = 0x2F13L;
    private static final long STREET_LINE_MUL_H = 0x5BD1L;

    private final long worldSeed;
    private final long cellSeed;
    private final int centerChunkX;
    private final int centerChunkZ;
    private final int radiusChunks;
    private final int centerX;
    private final int centerZ;
    private final int radiusBlocks;
    /** 轮廓总扰动预算（城半径比例，构造期定值 ∈ [0.25,0.35]）。 */
    private final double edgeTotFrac;
    /** 各向异性强度 w 与主轴朝向 ψ（构造期定值）。 */
    private final double anisoW;
    private final double anisoCos;
    private final double anisoSin;
    /** warp 空间拉伸/压缩轴除数（1+w / 1-w，构造期定值）。 */
    private final double axStretch;
    private final double azShrink;
    /** k 次谐波振幅（radiusBlocks 比例；构造期钳定后定值，频次 2/3/5）。 */
    private final double amp2;
    private final double amp3;
    private final double amp5;
    /** k 次谐波相位（弧度 [0,2π)；构造期定值）。 */
    private final double phi2;
    private final double phi3;
    private final double phi5;
    /** warp 空间边界半径上界 R×(1+Σaₖ)（快速外退用，构造期定值）。 */
    private final double warpRadiusMax;
    /** 世界侧最大延伸 R×(1+TOT)（缓冲窗/plot 网格/reach 的共同上界，构造期定值）。 */
    private final double maxWorldRadius;
    /** 缓冲窗 chunk 裕量（radiusChunks 之外再加的 chunk 数；构造期定值）。 */
    private final int bufferMarginChunks;
    /** plot 边界偏置低频噪声种子（mix(cellSeed, 盐)，构造期定值）。 */
    private final long plotNoiseSeed;
    /** 街线断续种子基（mix(cellSeed, 盐)，构造期定值）。 */
    private final long streetLineSeedBase;
    /** plot 连通闭包（懒计算；下标 (k+lim)*n+(l+lim)，lim=plotIndexLimit()）。 */
    private boolean[] plotReachMemo;

    CityPlan(long worldSeed, long cellSeed, int centerChunkX, int centerChunkZ, int radiusChunks) {
        this.worldSeed = worldSeed;
        this.cellSeed = cellSeed;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.radiusChunks = radiusChunks;
        this.centerX = centerChunkX * 16 + 8;
        this.centerZ = centerChunkZ * 16 + 8;
        this.radiusBlocks = radiusChunks * 16;
        // —— 轮廓参数派生（全部 cellSeed + 盐决定，纯函数）——
        // 总预算 TOT ∈ [0.25,0.35]（城半径 25-35%，随半径自适应）
        this.edgeTotFrac = EDGE_TOT_FRAC_MIN
            + (EDGE_TOT_FRAC_MAX - EDGE_TOT_FRAC_MIN) * frac(CityPlanner.mix(cellSeed, SALT_EDGE_TOT));
        // 各向异性 w ∈ [0.03,0.10]、主轴朝向 ψ ∈ [0,2π)
        this.anisoW = EDGE_ANISO_MIN
            + (EDGE_ANISO_MAX - EDGE_ANISO_MIN) * frac(CityPlanner.mix(cellSeed, SALT_EDGE_ANISO));
        final double psi = TWO_PI * frac(CityPlanner.mix(cellSeed, SALT_EDGE_ANISO_ANGLE));
        this.anisoCos = Math.cos(psi);
        this.anisoSin = Math.sin(psi);
        this.axStretch = 1.0 + this.anisoW;
        this.azShrink = 1.0 - this.anisoW;
        // 谐波族预算：Σa ≤ (1+TOT)/(1+w) − 1（warp 空间边界 ×(1+w) ≤ 世界侧 (1+TOT)·R 的构造保证）
        final double harmCap = (1.0 + this.edgeTotFrac) / this.axStretch - 1.0;
        final double harmSum = harmCap * (EDGE_HARMONIC_FILL_MIN
            + (1.0 - EDGE_HARMONIC_FILL_MIN) * frac(CityPlanner.mix(cellSeed, SALT_EDGE_AMPLITUDE)));
        // 三频原始权重 ∈ [0.25,1.0] → 归一化到族预算（频次 2/3/5：奇次破对称）
        final double w2 = EDGE_W_MIN + (1.0 - EDGE_W_MIN) * frac(CityPlanner.mix(cellSeed, SALT_EDGE_AMPLITUDE + 2));
        final double w3 = EDGE_W_MIN + (1.0 - EDGE_W_MIN) * frac(CityPlanner.mix(cellSeed, SALT_EDGE_AMPLITUDE + 3));
        final double w5 = EDGE_W_MIN + (1.0 - EDGE_W_MIN) * frac(CityPlanner.mix(cellSeed, SALT_EDGE_AMPLITUDE + 5));
        final double wSum = w2 + w3 + w5;
        this.amp2 = w2 / wSum * harmSum;
        this.amp3 = w3 / wSum * harmSum;
        this.amp5 = w5 / wSum * harmSum;
        this.phi2 = TWO_PI * frac(CityPlanner.mix(cellSeed, SALT_EDGE_PHASE + 2));
        this.phi3 = TWO_PI * frac(CityPlanner.mix(cellSeed, SALT_EDGE_PHASE + 3));
        this.phi5 = TWO_PI * frac(CityPlanner.mix(cellSeed, SALT_EDGE_PHASE + 5));
        this.warpRadiusMax = this.radiusBlocks * (1.0 + this.amp2 + this.amp3 + this.amp5);
        this.maxWorldRadius = this.radiusBlocks * (1.0 + this.edgeTotFrac);
        // 缓冲窗裕量：总扰动上界 35% + plot 偏置 7% 折 chunk + (c) 巨构内容 chunk 裕量
        // （P16-B3：旧写死 +1，现随 CityVariants.MAX_SIDE 派生；当前 24 边长下数值仍为 1，
        // 推导与"为什么不放大"见常量区 MEGA_SPILL_CHUNKS 注释）
        this.bufferMarginChunks = Math
            .max(1, (int) Math.ceil((EDGE_TOT_FRAC_MAX + PLOT_EDGE_BIAS_FRAC) * this.radiusBlocks / STREET_SPACING))
            + MEGA_SPILL_CHUNKS;
        this.plotNoiseSeed = CityPlanner.mix(cellSeed, SALT_PLOT_EDGE_NOISE);
        this.streetLineSeedBase = CityPlanner.mix(cellSeed, SALT_STREET_LINE);
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

    /**
     * chunk 是否在城市缓冲窗（radiusChunks + 自适应裕量 chunk；渲染与"城内跳过"同口径，
     * plan §3.4）。裕量按轮廓总扰动上界 + plot 悬突偏置折算，保证异形轮廓全部内容
     * （街道/plot/footprint 外溢）仍落在窗内。
     */
    public boolean chunkInBuffer(int chunkX, int chunkZ) {
        return Math.abs(chunkX - this.centerChunkX) <= this.radiusChunks + this.bufferMarginChunks
            && Math.abs(chunkZ - this.centerChunkZ) <= this.radiusChunks + this.bufferMarginChunks;
    }

    // ═══ 城界（单一判定；v1.20.34 各向异性 × 三频 Fourier 复合轮廓）═══

    /**
     * 世界坐标 (wx,wz) 是否落在城界内——<b>全城唯一边界判定</b>（S-A3 红线：街道裁剪与
     * plot 几何门一律委托本函数，全仓禁止旁路独立边界实现）。边界 =「各向异性 xy 缩放
     * ⊗ 三频 Fourier 径向扰动」：
     * <p>
     * ① (du,dv) 旋转到主轴系 ψ 后，拉长轴 /(1+w)、压缩轴 /(1-w)（warp 进椭圆坐标系）；
     * ② warp 空间内 r(θ) = R×(1 + a₂cos(2θ+φ₂) + a₃cos(3θ+φ₃) + a₅cos(5θ+φ₅))；
     * ③ 世界侧最大延伸 ≤ R×(1+TOT)，TOT ∈ [{@value #EDGE_TOT_FRAC_MIN},{@value #EDGE_TOT_FRAC_MAX}]
     * （谐波族预算构造期钳定 Σaₖ ≤ (1+TOT)/(1+w)−1）。缓冲窗（radiusChunks+裕量）与
     * {@link #contentReachBlocks()} 均按该包络留量，3×3 cell 检索不漏检。
     */
    public boolean insideCity(int wx, int wz) {
        final double du = wx - this.centerX;
        final double dv = wz - this.centerZ;
        // ① 各向异性 xy 缩放（旋转 + 轴向缩放；构造期定值）
        final double xu = (du * this.anisoCos + dv * this.anisoSin) / this.axStretch;
        final double yu = (dv * this.anisoCos - du * this.anisoSin) / this.azShrink;
        final double distSq = xu * xu + yu * yu;
        // 快速外退：warp 空间超出边界上界 R×(1+Σaₖ) 必在城外，跳过三角函数
        if (distSq > this.warpRadiusMax * this.warpRadiusMax) {
            return false;
        }
        // ② 三频 Fourier 径向边界（频次 2/3/5）
        final double theta = Math.atan2(yu, xu);
        final double rBoundary = this.radiusBlocks * (1.0 + this.amp2 * Math.cos(2.0 * theta + this.phi2)
            + this.amp3 * Math.cos(3.0 * theta + this.phi3)
            + this.amp5 * Math.cos(5.0 * theta + this.phi5));
        return distSq <= rBoundary * rBoundary;
    }

    /** 总扰动包络（格）= TOT×radiusBlocks（≤ {@value #EDGE_TOT_FRAC_MAX}×R，自证用）。 */
    public double edgePerturbationBlocks() {
        return this.edgeTotFrac * this.radiusBlocks;
    }

    /**
     * 城市内容 reach（格，自中心）：radiusBlocks + 总扰动 + plot 偏置 + (b) 内容余量
     * （plot 半跨 + footprint 外溢口径；P16-B3 随 {@link CityVariants#MAX_SIDE} 派生，
     * 旧=24、现=32；SanityCheck 回调坐标上界口径）。
     */
    public int contentReachBlocks() {
        return this.radiusBlocks
            + (int) Math.ceil(this.maxWorldRadius - this.radiusBlocks + PLOT_EDGE_BIAS_FRAC * this.radiusBlocks)
            + CONTENT_MARGIN_BLOCKS;
    }

    // ═══ 街道（纯几何；v1.20.34 次街分段断续）═══

    /** 普通街带：u ≡ -1/0/+1 (mod 16)。 */
    private static boolean streetBand(int u) {
        final int m = Math.floorMod(u, STREET_SPACING);
        return m <= 1 || m >= STREET_SPACING - 1;
    }

    /** 街带 u 所属街线号（band 中心 u ≈ line×16；streetBand(u) 为真时才有效）。 */
    private static int streetLineOf(int u) {
        return Math.floorDiv(u + 1, STREET_SPACING);
    }

    /** 街带/plot 带坐标 t 所属段号（plot 行/列口径：t ∈ [n*16+2, n*16+14] → n）。 */
    private static int segmentOf(int t) {
        return Math.floorDiv(t - 2, STREET_SPACING);
    }

    /**
     * 次街段开放判定（街线 × 街段粒度断续的原子决策；纯函数，公开供离线断言独立重算）。
     * <p>
     * 街线 line（u ≈ line×16；line=0 即中央大道所在线）沿街以相邻横街切分成段 index
     * （= 其服务的 plot 行/列号）。line=0 恒开（大道永续）；其余线各挂独立低频噪声
     * （波长 ≈ 3.7 段）过 {@value #STREET_BREAK_THRESHOLD} 阈值——同线段成簇开/断
     * （连 2-4 段、断 1-2 段），平行线互不相关。
     */
    public boolean streetSegmentOpen(boolean vertical, int line, int index) {
        if (line == 0) {
            return true; // 中央大道
        }
        final long lineSeed = this.streetLineSeedBase ^ (line * (vertical ? STREET_LINE_MUL_V : STREET_LINE_MUL_H));
        return GTSRWorldgenHash.valueNoise(lineSeed, index * STREET_BREAK_FREQ, 0.5) >= STREET_BREAK_THRESHOLD;
    }

    /** (wx,wz) 是否街道格（中央大道永续；次街按街段断续）。 */
    public boolean onStreet(int wx, int wz) {
        final int u = wx - this.centerX;
        final int v = wz - this.centerZ;
        if (Math.abs(u) <= AVENUE_WIDTH / 2 || Math.abs(v) <= AVENUE_WIDTH / 2) {
            return true; // 两条正交主街（中央大道）穿越全城
        }
        final boolean bandU = streetBand(u);
        final boolean bandV = streetBand(v);
        if (!bandU && !bandV) {
            return false;
        }
        if (bandU) {
            final int lineU = streetLineOf(u);
            if (bandV) {
                // 交叉格：四邻段（纵×2 + 横×2）任一开放即成路口（与连通图的边同口径）
                if (streetSegmentOpen(true, lineU, streetLineOf(v) - 1)
                    || streetSegmentOpen(true, lineU, streetLineOf(v))
                    || streetSegmentOpen(false, streetLineOf(v), lineU - 1)
                    || streetSegmentOpen(false, streetLineOf(v), lineU)) {
                    return true;
                }
            } else if (streetSegmentOpen(true, lineU, segmentOf(v))) {
                return true;
            }
        }
        if (bandV && streetSegmentOpen(false, streetLineOf(v), segmentOf(u))) {
            return true;
        }
        return false;
    }

    /** 街中线（轨枕嵌位线）：u ≡ 0 (mod 16) 或中央大道中线。 */
    private static boolean streetCenterLine(int u) {
        return Math.floorMod(u, STREET_SPACING) == 0;
    }

    // ═══ 地块（纯决策；v1.20.34 偏置几何门 + 连通闭包）═══

    /** plotIndex 组合编码（k/l → 单值；plan 公式 plotSeed = cellSeed ^ (plotIndex*0x9E37)）。 */
    private static long plotIndex(int k, int l) {
        return k * 1000003L + l;
    }

    /** plotSeed（plan §3.2 公式原样，plotIndex 组合编码为派生口径）。 */
    public long plotSeed(int k, int l) {
        return this.cellSeed ^ (plotIndex(k, l) * 0x9E37L);
    }

    /**
     * plot (k,l) 的低频边界偏置（格，带符号；公开供离线断言独立重算）。
     * valueNoise（波长 ≈ 2.7 plot）× ±{@value #PLOT_EDGE_BIAS_FRAC}×R —— 正值容忍悬突
     * （向外探）、负值制造内凹，城界附近地块取舍非光滑且成簇。
     */
    public double plotEdgeBias(int k, int l) {
        return GTSRWorldgenHash.valueNoise(this.plotNoiseSeed, k * PLOT_NOISE_FREQ, l * PLOT_NOISE_FREQ)
            * PLOT_EDGE_BIAS_FRAC
            * this.radiusBlocks;
    }

    /**
     * plot 网格索引上界（|k|,|l| ≤ 本值；覆盖偏置悬突后全部可能保留的 plot ——
     * 保留 plot 中心距 ≤ (1+TOT)×R + 偏置上界）。渲染遍历、describe 与断言共用本口径。
     */
    public int plotIndexLimit() {
        return (int) Math.ceil((this.maxWorldRadius + PLOT_EDGE_BIAS_FRAC * this.radiusBlocks) / STREET_SPACING) + 1;
    }

    /**
     * plot 几何门：采样点沿径向偏移 bias 格后再问唯一边界函数（悬突/内凹的来源）。
     * 仍 100% 委托 {@link #insideCity(int, int)}——没有第二套边界公式。
     */
    private boolean plotGeometricIn(int k, int l) {
        final double du = k * STREET_SPACING + 8.0;
        final double dv = l * STREET_SPACING + 8.0;
        final double d = Math.sqrt(du * du + dv * dv);
        if (d < 1e-9) {
            return true;
        }
        final double scale = (d + plotEdgeBias(k, l)) / d;
        return insideCity(this.centerX + (int) Math.round(du * scale), this.centerZ + (int) Math.round(dv * scale));
    }

    /** plot (k,l) → (k+1,l) 跨越的纵街段（线 k+1、行 l；大道线 0 恒开）。 */
    private boolean plotEdgeXOpen(int k, int l) {
        return k + 1 == 0 || streetSegmentOpen(true, k + 1, l);
    }

    /** plot (k,l) → (k,l+1) 跨越的横街段（线 l+1、列 k；大道线 0 恒开）。 */
    private boolean plotEdgeYOpen(int k, int l) {
        return l + 1 == 0 || streetSegmentOpen(false, l + 1, k);
    }

    /**
     * plot (k,l) 是否保留（门 = 几何门 ∧ 连通闭包；S-A3 单一判定纪律的 plot 侧入口）。
     * <p>
     * 连通闭包：从大道邻接（k∈{-1,0} 或 l∈{-1,0}）且过几何门的 plot 出发，经<b>开放</b>街段
     * 四邻接传播——因次街断开而孤立的地块在此直接剔除（孤立地块数恒 0，验收断言钉住）。
     * 闭包为懒计算纯派生（首次调用构建，值只依赖构造期参数；同 seed 双跑逐字节一致）。
     */
    public boolean plotInCircle(int k, int l) {
        ensurePlotReach();
        final int lim = plotIndexLimit();
        final int n = 2 * lim + 1;
        final int kk = k + lim;
        final int ll = l + lim;
        if (kk < 0 || kk >= n || ll < 0 || ll >= n) {
            return false; // 网格宇宙之外必不保留（渲染/断言宇宙 = 本网格）
        }
        return this.plotReachMemo[kk * n + ll];
    }

    /** 连通闭包懒构建（BFS；确定性——同 seed 同 cell 任意次构建逐位一致）。 */
    private void ensurePlotReach() {
        if (this.plotReachMemo != null) {
            return;
        }
        final int lim = plotIndexLimit();
        final int n = 2 * lim + 1;
        final boolean[] reach = new boolean[n * n];
        // 种子：过几何门 ∧ 大道邻接（大道永续 → 这些 plot 必可达）
        final int[] queue = new int[n * n];
        int head = 0;
        int tail = 0;
        for (int k = -lim; k <= lim; k++) {
            for (int l = -lim; l <= lim; l++) {
                if ((k == -1 || k == 0 || l == -1 || l == 0) && plotGeometricIn(k, l)) {
                    final int idx = (k + lim) * n + (l + lim);
                    reach[idx] = true;
                    queue[tail++] = idx;
                }
            }
        }
        // 四邻接传播：跨段 = 该方向街段开放 ∧ 邻 plot 过几何门
        while (head < tail) {
            final int idx = queue[head++];
            final int k = idx / n - lim;
            final int l = idx % n - lim;
            if (k + 1 <= lim && !reach[idx + n] && plotEdgeXOpen(k, l) && plotGeometricIn(k + 1, l)) {
                reach[idx + n] = true;
                queue[tail++] = idx + n;
            }
            if (k - 1 >= -lim && !reach[idx - n] && plotEdgeXOpen(k - 1, l) && plotGeometricIn(k - 1, l)) {
                reach[idx - n] = true;
                queue[tail++] = idx - n;
            }
            if (l + 1 <= lim && !reach[idx + 1] && plotEdgeYOpen(k, l) && plotGeometricIn(k, l + 1)) {
                reach[idx + 1] = true;
                queue[tail++] = idx + 1;
            }
            if (l - 1 >= -lim && !reach[idx - 1] && plotEdgeYOpen(k, l - 1) && plotGeometricIn(k, l - 1)) {
                reach[idx - 1] = true;
                queue[tail++] = idx - 1;
            }
        }
        this.plotReachMemo = reach;
    }

    /** plot (k,l) 的世界原点（最小角；u = k*16+2）。 */
    public int plotOriginX(int k) {
        return this.centerX + k * STREET_SPACING + 2;
    }

    public int plotOriginZ(int l) {
        return this.centerZ + l * STREET_SPACING + 2;
    }

    /**
     * 分区（v1.20.34：距最近中央大道的曼哈顿距离分档，跟随异形轮廓；0=核心 / 1=工业环 /
     * 2=边缘废墟环）。md = min(dk,dl)，dk = k≥0 ? k : -1-k（plot 步数；k∈{-1,0} 邻接
     * 纵向大道），dl 同理；md&lt;2 / md&lt;4 / ≥4 与旧同心环 &lt;2/&lt;5/≥5 比例感等价。
     */
    public int districtOf(int k, int l) {
        final int dk = k >= 0 ? k : -1 - k;
        final int dl = l >= 0 ? l : -1 - l;
        final int md = Math.min(dk, dl);
        return md < 2 ? 0 : md < 4 ? 1 : 2;
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

    // 变体池（注册名 = CityVariants.ALL 基础名；P16-B3 巨构接入面——选型仍是 plotVariant 那
    // 一行 plotSeed 哈希，池变化只改"谁可能被选中/概率"，不新增随机源）。
    // 巨构归属：great_forge 只进工业环（md 2..3，24 宽悬挑距中央大道 ≥2 街距；核心区会罩大道、
    // 边缘区与"消融进荒野"语义相反），titan_gearworks 只进边缘环（md≥4 天际线地标，工业环
    // 不同池是防同城双巨构连片）——两池各 +1 名，CORE 池零变化。理由全文见
    // CityMegaVariants 类注释与 B3 回执。
    private static final String[] CORE_POOL = { "dome_hall", "clock_tower", "market_colonnade", "manor_ruin",
        "tram_depot", "fountain_basin" };
    private static final String[] INDUSTRIAL_POOL = { "boiler_house", "pump_house", "forge_hall", "engine_room",
        "gas_holder", "pressure_tank_row", "chimney_stack", "cooling_tower", "gear_tower", "viaduct", "crane_ruin",
        "rail_platform", "canal_gate", "broken_bridge", "great_forge" };
    private static final String[] EDGE_POOL = { "watch_tower", "water_tower", "chimney_stack", "gear_tower",
        "cooling_tower", "clock_tower", "fallen_arch", "broken_pillars", "machine_plinth", "slag_heap",
        "titan_gearworks" };
    private static final String[] RUBBLE_POOL = { "broken_pillars", "slag_heap", "fallen_arch", "machine_plinth" };

    /**
     * district 选型池只读视图（0=核心 / 1=工业环 / 2=边缘环）——<b>只为离线选型断言开</b>
     * （{@code CityShapeCheck} E 组核"巨构只出现在被裁定的池、核心区池永不出巨构"）；
     * 生产选型唯一入口仍是 {@link #plotVariant}。返回内部数组引用，调用方不得写。
     */
    public static String[] poolForDistrict(final int district) {
        return district == 0 ? CORE_POOL : district == 1 ? INDUSTRIAL_POOL : EDGE_POOL;
    }

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
                    continue; // 城界外（街道只在城内；与 plot 门同源 = insideCity 单一判定）
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
     * plot 网格 = plot (k,l) 过保留门（{@link #plotInCircle}：偏置几何门 ∧ 连通闭包，
     * 几何腿委托 {@link #insideCity(int, int)} 单一判定，与街道裁剪同口径）；bbox 相交 =
     * plot 矩形（原点扩 (a) footprint 余量 {@link #FOOTPRINT_MARGIN_BLOCKS}，P16-B3 起 16→24
     * 随巨构申报边长派生）与 chunk 矩形相交。
     */
    public void forEachPlotInChunk(int chunkX, int chunkZ, PlotVisitor visitor) {
        final int kLim = plotIndexLimit();
        final int kMin = -kLim;
        final int kMax = kLim;
        final int m = FOOTPRINT_MARGIN_BLOCKS; // 变体 footprint 外溢余量（=max(单chunk, 巨构申报边长)）
        final int minX = chunkX * 16 - m;
        final int minZ = chunkZ * 16 - m;
        final int maxX = minX + 16 + 2 * m - 1; // 16 + 24 + 23
        final int maxZ = minZ + 16 + 2 * m - 1;
        for (int k = kMin; k <= kMax; k++) {
            final int px = plotOriginX(k);
            if (px < minX - PLOT_DEPTH - m || px > maxX + m) {
                continue; // 快速横切（原点沿 x 单调，间距 16）
            }
            for (int l = kMin; l <= kMax; l++) {
                if (!plotInCircle(k, l)) {
                    continue; // 保留门之外（城界外/悬突反侧/连通剔除）
                }
                final int pz = plotOriginZ(l);
                // plot bbox [px, px+PLOT_DEPTH-1]×[pz, pz+PLOT_DEPTH-1] 外扩 footprint 余量后与 chunk 相交
                if (px + PLOT_DEPTH - 1 + m < minX || px - m > maxX
                    || pz + PLOT_DEPTH - 1 + m < minZ
                    || pz - m > maxZ) {
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
     * 覆盖中心/半径/全部保留 plot 决策（变体/朝向/损伤档）与街道参数。
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
        final int lim = plotIndexLimit();
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
