package com.miaokatze.gtsr.common.dimension.framework;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;

/**
 * 群系空间连贯分区选择器（dim78 修复 S-A2，plan §4 S-A2 + §12 修订第 6 条）。
 * <p>
 * <b>P3（plan §2.1 L2「禁止手搓哈希」）</b>：本类原有私有 {@code mix(long)}（splitmix64 终结器）
 * 与 4 个内联乘子常数（{@code CELL_CONSTANT_X/Z}、{@code CHUNK_CONSTANT_X/Z}）已改走
 * {@link GTSRWorldgenHash}——算法体与<b>常数值均逐字搬移，未做任何数值调整</b>；
 * 两处掷骰的输入形状与终结器与改造前<b>逐位相同</b>（对拍见
 * {@code tools/dim1/SurfaceYParityCheck} 的 {@code zone.selector_cell} / {@code zone.edge_band} 站点，
 * 以及 {@code BiomeZoneCheck} 全回归）。域分离盐 {@link #CELL_DOMAIN}/{@link #CHUNK_DOMAIN}
 * 是本类自己的策略常量，保留在本类。
 * <p>
 * 两级确定性掷骰（全 splitmix 终结哈希，<b>零 Minecraft import</b>，离线可复算）：
 * <ol>
 * <li>cell 级基准 zone：zone cell = {@link #ZONE_CELL_CHUNKS} chunk（用户拍板 48→16，群系区不要
 * 太大），每 cell 按权重表掷出基准群系——同 cell 整片同群系，形成空间连贯分区，摆脱 per-chunk
 * 均匀掷骰的盐胡椒分布；</li>
 * <li>边带过渡碎斑：距 cell 边界不足 band（band = cell×{@link #EDGE_BAND_FRACTION} 四舍五入，
 * cell=16 时为 2 chunk）的 chunk，以 {@link #EDGE_BAND_FRACTION} 概率改掷相邻 cell（取基准
 * zone 与本 cell 不同的邻 cell）的 zone，其余保持基准——边界处生成约 12% 的过渡碎斑。</li>
 * </ol>
 * 输出契约：返回值 ∈ [0, biomeCount)（def 群系权重表下标），消费侧仍整 chunk 单群系
 * （{@link GTSRWorldChunkManager#biomeAt} 口径不变）。纯函数：同 seed 同坐标结果恒定，
 * 与 chunk 生成顺序无关。
 * <p>
 * <b>P6 分层（plan §2.2 H-1 + §7.1 已锁定 U2「macro 群系带 64 chunk + micro cell 16」）</b>：
 * 上面的两级掷骰本身<b>一字未改</b>——改的只是"它跑在哪个尺度上"。本类现在是 <b>H-1 群系带身份
 * 的唯一出口</b>，两层各自独立：
 * <ul>
 * <li><b>macro 带（身份层，dim78 = 64 chunk）</b>：{@link #bandIndex} / {@link #bandIdentity}
 * 给群系<b>身份</b>。带尺度由调用方（{@link GTSRWorldChunkManager} 按维读 Config）给出，
 * 本类只负责合法性化（{@link #normalizeMacroCell}：micro 的正整数倍）与换算。</li>
 * <li><b>micro cell（强度层，恒 16 chunk）</b>：{@link #microStrengthTier}/{@link #microStrength}
 * 只给<b>变体/装饰强度档</b>（{@link #MICRO_STRENGTHS} = 0.7/1.0/1.3，均值恰为 1.0 ⇒ 采用后
 * 不改变 P5 钉住的总密度口径），<b>不参与身份</b>。</li>
 * </ul>
 * <b>任何消费方不得再自己算带</b>：身份一律经 {@link GTSRWorldChunkManager#biomeAt} → L1
 * {@link GTSRBiomeAuthority}，或经本类 {@code band*} 出口；{@link #select} 保留"给定 cell 尺度的
 * 分区身份"原语义（{@code tools/dim1/BiomeZoneCheck} 钉的就是它在 cell=16 下的行为，即强度层的
 * 空间粒度）。
 * <p>
 * <b>macro 与 micro 的数值关系（带基准逐位相同；边带改掷的具体块不同）</b>：
 * {@code macroCell = s × MICRO_CELL_CHUNKS} 时，"以 macroCell 为 cell 跑一次本算法"与"先把 chunk
 * 坐标按 s 折算、再以 microCell 跑"在<b>带基准身份</b>上逐位相同——因为
 * {@code floorDiv(floorDiv(a,s),16) == floorDiv(a,16s)}（s、16 均正），故带原点掷骰、邻带集合与
 * 边带宽度（{@code band(64)=round(64×0.12)=8} chunk ↔ 折算口径下 {@code band(16)=2} 个折算格）
 * 全部同式；差别只有两处，且都不改变分布：
 * <ol>
 * <li><b>边带内 12% 改掷的随机数输入</b>是（折算后的）块坐标，故具体哪几块被改掷与原生口径不同，
 * 但改掷率仍是 12%、改掷候选仍是"基准不同的邻带"；折算粒度是 {@code s} chunk 的方块组，即边带碎斑
 * 以 4×4 chunk（s=4）为单位而不是单 chunk（更连贯，非近似）。</li>
 * <li><b>边带宽度按 s 量化</b>：{@code band(16s) = round(16s×0.12)} 与 {@code s×band(16) = 2s}
 * 可差 1 格（s=7/8 时），只影响边界那一圈 chunk 在两种口径下"算不算边带"的分类。</li>
 * </ol>
 * s=2（32）与 s=4（64，U2 锁定档）两处均无量化差，边带宽度都是 8 chunk。实测边带偏离率、带尺度与
 * chunk 尺度份额守恒见 {@code tools/dim1/BiomeBandHierarchyCheck} 的 A1b/D 组；折算实现与上述许可
 * 差异边界由 B 组穷举（s=1..8 含负坐标）钉住。
 * s=1（dim79、以及 dim78 回退 macro=16）时坐标折算退化为恒等变换 ⇒ 与改造前<b>逐位相同</b>
 * （判据「非本片路径零漂移」的依据，B2 组穷举钉住）。
 */
public final class BiomeZoneSelector {

    /**
     * micro cell 尺寸（chunk）：强度层与"单层口径"的空间粒度。
     * <p>
     * 值就是原 {@code ZONE_CELL_CHUNKS}（用户拍板 48→16，plan §12 修订第 6 条），<b>一字未改</b>；
     * P6 起它只作 micro/单层口径，macro 带尺度另见 {@link #bandIndex}。
     */
    public static final int MICRO_CELL_CHUNKS = 16;

    /**
     * 兼容别名（改造前的名字，值同 {@link #MICRO_CELL_CHUNKS}）：dim79 接线与既有自检工具
     * （{@code BiomeZoneCheck}/{@code BiomeAllocationCheck}/{@code SurfaceHarness} 等）仍按本名传参，
     * 传 16 即"单层口径"，行为逐位不变。新代码请用 {@link #MICRO_CELL_CHUNKS}。
     */
    public static final int ZONE_CELL_CHUNKS = MICRO_CELL_CHUNKS;

    /**
     * dim78 群系带域分离盐（{@code "ZONE"+"E"}）——H-1 身份层的<b>唯一申报处</b>。
     * <p>
     * {@code CommonProxy} 的 dim78 selector 接线用的是同一字面量（本类不在允许改动面内，故不合并），
     * 两处一致由 {@code tools/dim1/BiomeBandHierarchyCheck} 的 C 组源级断言钉住；dim79 用
     * {@code 0x5A4F4E46}（同盐 +1 域分离），本片不改其接线、其带尺度亦保持 16（零变化）。
     */
    public static final long ZONE_SALT_PROSPERITY = 0x5A4F4E45L;

    /**
     * micro 层的变体/装饰强度档（plan §7.1 U2 锁定的 0.7/1.0/1.3）。
     * 三档均值 == 1.0F ⇒ 强度层被消费时不改变"整维总量"口径（P5 的 K/落块上限不受影响）。
     */
    public static final float[] MICRO_STRENGTHS = { 0.7F, 1.0F, 1.3F };

    /**
     * 边带比例：既是边带宽度系数（band = cell×此值 四舍五入），也是边带内 chunk 改掷邻 cell
     * 的概率（plan §12 修订第 6 条保留 12%）。
     */
    public static final float EDGE_BAND_FRACTION = 0.12F;

    /** 边带掷骰命中概率的分母（12% → roll%1000 < 120）。 */
    private static final int BAND_ROLL_SCALE = 1000;

    /** cell 级域分离盐（与 chunk 级掷骰、其他 cellSeed 用途解耦）。 */
    private static final long CELL_DOMAIN = 0x5A4F4E455A4F4E45L;
    /** chunk 级域分离盐。 */
    private static final long CHUNK_DOMAIN = 0x2B1E4E4F5A4F4E45L;
    /**
     * micro 强度层域分离盐（P6 新增用途；与 cell/chunk 两级掷骰互不复用，形状照 {@link #CELL_DOMAIN}）。
     * 调用方另需传入本维接线盐（dim78 = {@link #ZONE_SALT_PROSPERITY}），故两维同种子同坐标也不会撞档。
     */
    private static final long MICRO_DOMAIN = 0x4D4943524F535452L; // "MICROSTR"

    private BiomeZoneSelector() {}

    /**
     * 身份层委托（P6）：{@link GTSRWorldChunkManager} 把 def 挂的
     * {@link GTSRDimensionDef.BiomeSelector} 以方法引用交进来，本类负责带尺度折算。
     * <p>
     * 独立声明（而不是直接引用 {@code GTSRDimensionDef.BiomeSelector}）是为了保住本类
     * "零 Minecraft import、离线 JEP330 可复算"的既有性质（{@code GTSRDimensionDef} 携 MC 类型）。
     */
    public interface ZoneDelegate {

        int select(long seed, int chunkX, int chunkZ, int biomeCount, int[] weights);
    }

    /**
     * macro 带尺度合法性化（H-1 唯一口径）：必须落在 micro 的<b>正整数倍</b>上。
     * <p>
     * 非正值或未达 micro 的值一律回退 {@link #MICRO_CELL_CHUNKS}（= 改造前单层行为，"配置写错不炸
     * 世界生成"）；其余向下取整到整数倍（如 100 → 96），保证 {@link #bandIndex} 的坐标折算恒等式成立。
     */
    public static int normalizeMacroCell(int requested) {
        if (requested < MICRO_CELL_CHUNKS) {
            return MICRO_CELL_CHUNKS;
        }
        return (requested / MICRO_CELL_CHUNKS) * MICRO_CELL_CHUNKS;
    }

    /**
     * H-1 群系带<b>身份</b>（显式入参版；城门等零世界读取消费方用）：macro 带尺度上的分区掷骰。
     *
     * @param macroCell 带尺度（chunk；经 {@link #normalizeMacroCell} 合法性化，16 = 单层/改造前口径）
     * @return 群系权重表下标 ∈ [0, biomeCount)
     */
    public static int bandIndex(long seed, int chunkX, int chunkZ, int biomeCount, int[] weights, int macroCell,
        long salt) {
        final int cell = normalizeMacroCell(macroCell);
        if (cell == MICRO_CELL_CHUNKS) {
            // 单层口径：与改造前逐位相同（坐标不折算，直接按 chunk 走带算法）
            return select(seed, chunkX, chunkZ, biomeCount, weights, MICRO_CELL_CHUNKS, salt);
        }
        final int scale = cell / MICRO_CELL_CHUNKS;
        return select(
            seed,
            Math.floorDiv(chunkX, scale),
            Math.floorDiv(chunkZ, scale),
            biomeCount,
            weights,
            MICRO_CELL_CHUNKS,
            salt);
    }

    /**
     * H-1 群系带<b>身份</b>（委托版；{@link GTSRWorldChunkManager} 接线用）：与
     * {@link #bandIndex} 同值，只是域分离盐由接线方的 selector lambda 自带，本类不感知盐。
     *
     * @param delegate  接线方提供的分区掷骰（内部 cell = {@link #MICRO_CELL_CHUNKS}）
     * @param macroCell 带尺度（chunk）
     * @return 群系权重表下标 ∈ [0, biomeCount)
     */
    public static int bandIdentity(ZoneDelegate delegate, long seed, int chunkX, int chunkZ, int biomeCount,
        int[] weights, int macroCell) {
        final int cell = normalizeMacroCell(macroCell);
        if (cell == MICRO_CELL_CHUNKS) {
            return delegate.select(seed, chunkX, chunkZ, biomeCount, weights);
        }
        final int scale = cell / MICRO_CELL_CHUNKS;
        return delegate.select(seed, Math.floorDiv(chunkX, scale), Math.floorDiv(chunkZ, scale), biomeCount, weights);
    }

    /**
     * micro 层强度档 ∈ [0, {@link #MICRO_STRENGTHS})（P6；只作用变体/装饰强度，<b>不参与身份</b>）。
     * <p>
     * 每 micro cell（16 chunk）一整档，与 macro 带身份<b>独立掷骰</b>（不同域分离盐、不同哈希输入
     * 形状），故同一带内各 cell 的强度档互不相关；三档等概率 ⇒ 均值恒 1.0。
     */
    public static int microStrengthTier(long seed, int chunkX, int chunkZ, long salt) {
        final long h = GTSRWorldgenHash.cellSeed(
            seed,
            Math.floorDiv(chunkX, MICRO_CELL_CHUNKS),
            Math.floorDiv(chunkZ, MICRO_CELL_CHUNKS),
            salt ^ MICRO_DOMAIN);
        return (int) Math.floorMod(GTSRWorldgenHash.splitmix64(h), MICRO_STRENGTHS.length);
    }

    /** micro 层强度系数（{@link #microStrengthTier} 的取值口径）。 */
    public static float microStrength(long seed, int chunkX, int chunkZ, long salt) {
        return MICRO_STRENGTHS[microStrengthTier(seed, chunkX, chunkZ, salt)];
    }

    /**
     * 群系 zone 选择（plan §4 S-A2 定夺签名）：cell 级权重掷骰 + 边带 12% chunk 级邻 cell 掷骰。
     *
     * @param seed       世界种子
     * @param chunkX     chunk 坐标 X
     * @param chunkZ     chunk 坐标 Z
     * @param biomeCount 群系表长度（&gt;0）
     * @param weights    权重表（null 或长度不足按下标补 1；单权重 &lt;1 按 1 计，与展开表口径一致）
     * @param cell       zone cell 尺寸（chunk，&gt;0；dim78 接线传 {@link #ZONE_CELL_CHUNKS}）
     * @param salt       用途域分离盐（dim78 接线传 0x5A4F4E45L）
     * @return 群系权重表下标 ∈ [0, biomeCount)
     * @throws IllegalArgumentException biomeCount 或 cell 非正（接线期编程错误，fail fast）
     */
    public static int select(long seed, int chunkX, int chunkZ, int biomeCount, int[] weights, int cell, long salt) {
        if (biomeCount <= 0) {
            throw new IllegalArgumentException("biomeCount must be positive: " + biomeCount);
        }
        if (cell <= 0) {
            throw new IllegalArgumentException("cell must be positive: " + cell);
        }
        final int cellX = Math.floorDiv(chunkX, cell);
        final int cellZ = Math.floorDiv(chunkZ, cell);
        final int base = zoneOfCell(seed, cellX, cellZ, biomeCount, weights, salt);
        if (!isEdgeBand(chunkX, chunkZ, cell)) {
            return base;
        }
        final long roll = chunkRoll(seed, chunkX, chunkZ, salt);
        if (Math.floorMod(roll, BAND_ROLL_SCALE) >= Math.round(BAND_ROLL_SCALE * EDGE_BAND_FRACTION)) {
            return base;
        }
        // 12% 命中：改掷邻 cell——优先取本 chunk 所在边带方向、且基准 zone 不同的邻 cell；
        // 均与基准同 zone 时回退全部 4 邻方向（miss 仅剩"4 邻全同 zone"，约 2%，保证测得
        // 边带偏离率 ≈12%）；4 邻仍无不同 zone 则保持基准（视觉上无差异可过渡）。
        final int band = bandChunks(cell);
        final int localX = Math.floorMod(chunkX, cell);
        final int localZ = Math.floorMod(chunkZ, cell);
        int directionMask = 0;
        if (localX < band) {
            directionMask |= 1; // 西邻
        }
        if (localX >= cell - band) {
            directionMask |= 2; // 东邻
        }
        if (localZ < band) {
            directionMask |= 4; // 北邻
        }
        if (localZ >= cell - band) {
            directionMask |= 8; // 南邻
        }
        int picked = -1;
        int seen = 0;
        for (int pass = 0; pass < 2 && picked == -1; pass++) {
            final int mask = pass == 0 ? directionMask : 0b1111;
            for (int dir = 0; dir < 4; dir++) {
                if ((mask & (1 << dir)) == 0) {
                    continue;
                }
                final int nx = cellX + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                final int nz = cellZ + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                final int zone = zoneOfCell(seed, nx, nz, biomeCount, weights, salt);
                if (zone == base) {
                    continue;
                }
                seen++;
                if (Math.floorMod(roll >>> 20, seen) == 0) {
                    picked = zone;
                }
            }
        }
        return picked == -1 ? base : picked;
    }

    /**
     * cell 级基准 zone（公开给离线自检工具复算边带偏离）：权重掷骰，全 splitmix。
     *
     * @return 群系权重表下标 ∈ [0, biomeCount)
     */
    public static int zoneOfCell(long seed, int cellX, int cellZ, int biomeCount, int[] weights, long salt) {
        if (biomeCount <= 0) {
            throw new IllegalArgumentException("biomeCount must be positive: " + biomeCount);
        }
        // 原实现：seed ^ ((long)cellX * 0x9E37…) ^ ((long)cellZ * 0xC2B2…) ^ salt ^ CELL_DOMAIN
        // 该形状与 GTSRWorldgenHash.cellSeed 完全同式（乘子即其 CELL_MUL_X/Z），故直接复用，逐位相同。
        final long h = GTSRWorldgenHash.cellSeed(seed, cellX, cellZ, salt ^ CELL_DOMAIN);
        return pickWeighted(GTSRWorldgenHash.splitmix64(h), biomeCount, weights);
    }

    /**
     * chunk 是否落在 cell 边带（距任一 cell 边界不足 band chunk；公开给离线自检工具同口径复算）。
     */
    public static boolean isEdgeBand(int chunkX, int chunkZ, int cell) {
        if (cell <= 0) {
            throw new IllegalArgumentException("cell must be positive: " + cell);
        }
        final int band = bandChunks(cell);
        final int localX = Math.floorMod(chunkX, cell);
        final int localZ = Math.floorMod(chunkZ, cell);
        return localX < band || localX >= cell - band || localZ < band || localZ >= cell - band;
    }

    /** 边带宽度（chunk）= cell×边带比例 四舍五入，至少 1（cell=16 → 2）。 */
    private static int bandChunks(int cell) {
        return Math.max(1, Math.round(cell * EDGE_BAND_FRACTION));
    }

    /**
     * chunk 级边带掷骰源（与 cell 级常量/域分离，独立同分布）。
     * <p>
     * 原实现的输入形状是 {@code seed ^ (chunkX * 0xBF58…) ^ (chunkZ * 0x94D0…)}——两个乘子与
     * cell 级<b>不同</b>（其中 0xBF58… 恰是 splitmix64 的标准增量被本仓复用为 x 乘子，
     * 见 {@link GTSRWorldgenHash#SPLITMIX_INCREMENT}），故走通用入口 {@code mixSeed} 而不是
     * {@code cellSeed}；乘子值一字未改。
     */
    private static long chunkRoll(long seed, int chunkX, int chunkZ, long salt) {
        final long h = GTSRWorldgenHash.mixSeed(
            seed,
            chunkX,
            chunkZ,
            GTSRWorldgenHash.SPLITMIX_INCREMENT,
            GTSRWorldgenHash.CHUNK_Z_MUL,
            salt ^ CHUNK_DOMAIN);
        return GTSRWorldgenHash.splitmix64(h);
    }

    /** 权重掷骰：roll 落入累计权重区间选下标；权重 null/缺位按 1、&lt;1 按 1（与展开表口径一致）。 */
    private static int pickWeighted(long roll, int biomeCount, int[] weights) {
        long total = 0;
        for (int i = 0; i < biomeCount; i++) {
            total += weightAt(weights, i);
        }
        long threshold = Math.floorMod(roll, total);
        long acc = 0;
        for (int i = 0; i < biomeCount; i++) {
            acc += weightAt(weights, i);
            if (threshold < acc) {
                return i;
            }
        }
        return biomeCount - 1;
    }

    private static int weightAt(int[] weights, int index) {
        if (weights == null || index >= weights.length) {
            return 1;
        }
        return Math.max(1, weights[index]);
    }
}
