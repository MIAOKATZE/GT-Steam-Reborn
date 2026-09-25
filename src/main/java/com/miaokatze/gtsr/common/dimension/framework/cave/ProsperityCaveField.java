package com.miaokatze.gtsr.common.dimension.framework.cave;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.TerrainVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * <b>dim78 洞穴 / 裂缝纯函数场（P22 版 B · S1a，p21 §1.1 + §1.5 原样 + 主代理裁决 1 改写 §1.4/§1.6）</b>。
 *
 * <p>
 * ═══ 契约 ═══ 本类<b>纯静态、自身零 {@code net.minecraft} 依赖</b>（照 {@code GTSRWorldgenHash.valueNoise}
 * 上收口径），可被 {@code tools/dim1/CaveFieldCheck} 离线直调；同 (seed, 坐标) 必得同值，跨 chunk
 * 无缝。噪声内核唯一出处 = {@link GTSRWorldgenHash#valueNoise(long, double, double)}（[-1,1)），
 * 本类不自建第二份噪声、不搬 {@code GTSRVoronoiRiverField} 的河流形状（峡谷场范式取 RTG 河流场：
 * 格点 jitter + 到垂直平分线的垂距 + 沿程正弦收放，新盐 {@link #SALT_CANYON}，与仓内 Voronoi
 * 不同源以免污染河流判据）。
 *
 * <p>
 * ═══ 依赖方向申报（回执同步）═══ framework/cave → prosperity 是<b>本切片新写的 import 方向</b>；
 * 包级循环 {@code framework ↔ prosperity} 在本仓<b>并非新增</b>——RVF 自身 import
 * {@code framework.structure.GTSRWorldgenHash}（prosperity→framework 既存），而
 * {@code framework.structure.PlacementGate} 早已 import RVF / ProsperityTerrainProfile /
 * {@code prosperity.ruins.*}（framework→prosperity 先例既存）。故本类直接静态调用版一导出的
 * public 纯函数，不改 Function 形参注入。
 *
 * <p>
 * ═══ 波长纪律（p21 §1.5）═══ 全部波长字面（{@code *_SCALE} / {@code CANYON_CELL} /
 * {@code CANYON_WIDTH_WAVE}）避开 16 的倍数（RTG {@code TerrainBase.java:96} 的 chunk 对齐条纹
 * 教训）；{@code CaveFieldCheck} WAVE 组读<b>源字面值</b>逐个断言 {@code v > 16 && (int)v % 16 != 0}。
 * 新增任何波长必须同轮入 WAVE 组。
 *
 * <p>
 * ═══ 与 carver 的分工（S1b）═══ 本类只给"哪里能挖 / 挖到哪为止"的谓词；数组写入、可挖白名单、
 * 表层时序与身份取数在 {@code framework/GTSRCaveCarver} + provider 覆写体（下一批）。消费序固定为：
 * {@link #densityOpenAt} 列级早出 → {@link #tubeAt} 逐格 → {@link #carveFloorY}/{@link #protectedColumn}
 * 保护门 → {@link #carveCeilingAt}/{@link #canyonAt} 封顶与谷切。
 */
public final class ProsperityCaveField {

    // ═════════════════ §1.5 常量表（p21 原样，全部波长 %16≠0）════════════════

    /** 列级密度门噪声波长（156 % 16 = 12）：成本主控，约 82–86% 列在 {@link #densityOpenAt} 早出。 */
    public static final double CAVE_DENSITY_SCALE = 156.0D;

    /** 密度门阈值：valueNoise（[-1,1)）{@code < 0.34} 的列整列不求值管束场（p21 §1.5 原式）。 */
    public static final double CAVE_DENSITY_MIN = 0.34D;

    /** 管束水平格距波长（68 % 16 = 4）。 */
    public static final double CAVE_TUBE_SCALE = 68.0D;

    /** y→水平漂移：两束分别沿 x/z 随 y 漂移，交叉成 3D 管网（p21 §1.5 原式 min(|n1|,|n2|)）。 */
    public static final double CAVE_TUBE_DRIFT = 0.62D;

    /** 管半径门：|n| 小于该值即入管（越大洞越粗）。 */
    public static final double CAVE_TUBE_HOLE = 0.055D;

    /** 地表破面许可场波长（44 % 16 = 12）。 */
    public static final double CAVE_BREAK_SCALE = 44.0D;

    /** 破面许可范数门：‖(nx,nz)‖ 大于该值才允许挖穿地表（否则封顶表面下 ≥{@link #CAVE_SURFACE_CAP}）。 */
    public static final double CAVE_BREAK_NORM_MIN = 0.72D;

    /** 峡谷主干 cell 边长（340 % 16 = 4）：到线距离场的格距。 */
    public static final double CANYON_CELL = 340.0D;

    /** 峡谷格点 jitter 幅度（cell 的分数，无量纲 ⇒ 不属波长纪律）。 */
    public static final double CANYON_JITTER = 0.35D;

    /** 峡谷场唯一新盐（ASCII "CANY"，p21 §1.5 点名）；轴向分离用显式翻转常数，不再扩盐。 */
    public static final long SALT_CANYON = 0x43414E59L;

    /** 峡谷边出现率（‰）：Voronoi 边的对称哈希掩码，稀疏成离散裂缝。 */
    public static final int CANYON_EDGE_PERMILLE = 400;

    /** 峡谷半宽沿程正弦波长（92 % 16 = 12）：收放周期，入 WAVE 组。 */
    public static final double CANYON_WIDTH_WAVE = 92.0D;

    /** 谷半宽下限（格）。 */
    public static final double CANYON_HALF_W_MIN = 3.0D;

    /** 谷半宽上限（格）：沿程正弦只在 [MIN, MAX] 内收放。 */
    public static final double CANYON_HALF_W_MAX = 9.0D;

    /** 谷深下限（顶面下切，格）：谷底在边缘半宽处最浅。 */
    public static final double CANYON_DEPTH_MIN = 26.0D;

    /** 谷深上限（格）：谷心最深；R-A 岸底落差 40 的口径不受影响（峡谷与湖域不重叠由保护门侧保证）。 */
    public static final double CANYON_DEPTH_MAX = 40.0D;

    /** 竖向域下界（与基岩带取大者的"洞穴侧"下界；基岩钳制在 carver 侧做）。 */
    public static final int CAVE_Y_MIN = 8;

    /** 竖向上界离世界顶（256）的偏移（p21 §1.5 原样）。 */
    public static final int CAVE_Y_MAX_OFFSET = 1;

    /** 竖向域上界 = 255 − {@link #CAVE_Y_MAX_OFFSET}。 */
    public static final int CAVE_Y_MAX = 255 - CAVE_Y_MAX_OFFSET;

    // ═════════════════ 裁决 1（P0-FILL R1/R3/R4）：保护门常量 ═════════════════

    /** 湖床/河床保底壳（格）：受保护列 carve 下界 = {@code max(CAVE_Y_MIN, heightAt + 本值)}。 */
    public static final int CAVE_LAKE_SHELL = 3;

    /** 岛域外扩不挖半径（格）：{@link #protectedColumn} 的守卫环宽。 */
    public static final int CAVE_ISLAND_GUARD = 4;

    /** 未获破面许可时封顶离表面的最小格数（p21 §1.5"封顶在表面下 ≥3"）。 */
    public static final int CAVE_SURFACE_CAP = 3;

    // ═════════════════ 场内盐（ASCII 风格同 SALT_CANYON；每场独立盐，零共享流）════════════════

    /** 密度门盐（"DENS"）。 */
    public static final long SALT_CAVE_DENSITY = 0x44454E53L;
    /** 管束 A 束盐（"TUBA"）。 */
    public static final long SALT_CAVE_TUBE_A = 0x54554241L;
    /** 管束 B 束盐（"TUBB"）。 */
    public static final long SALT_CAVE_TUBE_B = 0x54554242L;
    /** 破面许可 x 分量盐（"BRKA"）。 */
    public static final long SALT_CAVE_BREAK_A = 0x42524B41L;
    /** 破面许可 z 分量盐（"BRKB"）。 */
    public static final long SALT_CAVE_BREAK_B = 0x42524B42L;
    /** 峡谷 jitter 的 Z 轴翻转常数（同一 SALT_CANYON 域内做轴向分离，非新场盐）。 */
    private static final long CANYON_AXIS_FLIP = 0x5A4158494C54L;

    private ProsperityCaveField() {}

    // ═════════════════ 形态谓词（p21 §1.5）════════════════

    /** 竖向域门：y ∈ [{@link #CAVE_Y_MIN}, {@link #CAVE_Y_MAX}] 之外一律不挖。 */
    public static boolean inVerticalDomain(int y) {
        return y >= CAVE_Y_MIN && y <= CAVE_Y_MAX;
    }

    /**
     * 列级密度门（成本主控）：{@code noise < 0.34} 的列整列不求值管束场。
     * 消费序约定（S1b carver 必须遵守）：本谓词为假 ⇒ 该列 {@link #tubeAt} 一个都不算。
     */
    public static boolean densityOpenAt(long worldSeed, int x, int z) {
        final double n = GTSRWorldgenHash
            .valueNoise(worldSeed ^ SALT_CAVE_DENSITY, x / CAVE_DENSITY_SCALE, z / CAVE_DENSITY_SCALE);
        return n >= CAVE_DENSITY_MIN;
    }

    /**
     * 3D 管束（p21 §1.5 原式）：两束噪声分别沿 x/z 随 y 漂移 {@link #CAVE_TUBE_DRIFT}，
     * {@code min(|n1|, |n2|) < }{@link #CAVE_TUBE_HOLE} 即入管。域外（竖向）恒假。
     * <b>前置</b>：调用方必须先过 {@link #densityOpenAt}（本方法自身不重复查密度门）。
     */
    public static boolean tubeAt(long worldSeed, int x, int y, int z) {
        if (!inVerticalDomain(y)) {
            return false;
        }
        final double u = x / CAVE_TUBE_SCALE;
        final double w = z / CAVE_TUBE_SCALE;
        final double drift = (CAVE_TUBE_DRIFT * y) / CAVE_TUBE_SCALE;
        final double n1 = GTSRWorldgenHash.valueNoise(worldSeed ^ SALT_CAVE_TUBE_A, u + drift, w);
        final double n2 = GTSRWorldgenHash.valueNoise(worldSeed ^ SALT_CAVE_TUBE_B, u, w + drift);
        final double a = Math.abs(n1);
        final double b = Math.abs(n2);
        return (a < b ? a : b) < CAVE_TUBE_HOLE;
    }

    /**
     * 峡谷到线距离场 + 沿程正弦收放（RTG 河流场范式，非本仓 Voronoi）：
     * 3×3 jitter 站扫描取最近/次近两站，最近两站的<b>垂直平分线</b>即谷线；站对掩码
     * （对称 splitmix 哈希，千分率 {@link #CANYON_EDGE_PERMILLE}）决定该线是否存在；
     * 点到线的垂距 {@code |d1²−d2²| / (2D)} 小于沿程正弦半宽即入谷。
     * <p>
     * <b>O1b 不加列级 memo 的申报（v1.20.43 P22 版 B）</b>：生产侧唯一调用点
     * {@code GTSRCaveCarver.carveColumn} 每 chunk 256 列各求值<b>一次</b>、无同列重入、
     * 无跨方法重入（全仓唯一生产消费点）⇒ 直接映射 memo 命中率恒 0，纯增哈希+比较开销。
     * 实测（CaveFieldCheck PERF carver 空载串行各 3 跑）：无 memo 中位 44/59/62µs vs
     * memo 中位 50/44/61µs——差值在样本噪声带内、无收益。数据与结论见
     * {@code plan/tmp/p22-o1/PROGRESS.md}；若未来新增 canyonAt 消费面（同列多次求值），
     * 照 {@code ProsperityTerrainProfile.heightAtMemoized} 先例补包裹层即可（算式零改动）。
     *
     * @return 顶面下切深度（格）：谷心最深 {@link #CANYON_DEPTH_MAX}、入谷边缘收至
     *         {@link #CANYON_DEPTH_MIN}；谷外恒 {@code 0.0}
     */
    public static double canyonAt(long worldSeed, int x, int z) {
        final int gx0 = floorCell(x / CANYON_CELL);
        final int gz0 = floorCell(z / CANYON_CELL);
        double d1 = Double.POSITIVE_INFINITY;
        double d2 = Double.POSITIVE_INFINITY;
        double s1x = 0.0D;
        double s1z = 0.0D;
        double s2x = 0.0D;
        double s2z = 0.0D;
        int g1x = 0;
        int g1z = 0;
        int g2x = 0;
        int g2z = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int gx = gx0 + dx;
                final int gz = gz0 + dz;
                final double sx = (gx + jitter(worldSeed, gx, gz, 0L)) * CANYON_CELL;
                final double sz = (gz + jitter(worldSeed, gx, gz, CANYON_AXIS_FLIP)) * CANYON_CELL;
                final double ex = sx - x;
                final double ez = sz - z;
                final double d = ex * ex + ez * ez;
                if (d < d1) {
                    d2 = d1;
                    s2x = s1x;
                    s2z = s1z;
                    g2x = g1x;
                    g2z = g1z;
                    d1 = d;
                    s1x = sx;
                    s1z = sz;
                    g1x = gx;
                    g1z = gz;
                } else if (d < d2) {
                    d2 = d;
                    s2x = sx;
                    s2z = sz;
                    g2x = gx;
                    g2z = gz;
                }
            }
        }
        if (d2 == Double.POSITIVE_INFINITY) {
            return 0.0D;
        }
        // 站对掩码：对无序站对做对称哈希（swap 不变 ⇒ 线两侧判定同一个值）
        final long ca = GTSRWorldgenHash.cellSeed(worldSeed, g1x, g1z, SALT_CANYON);
        final long cb = GTSRWorldgenHash.cellSeed(worldSeed, g2x, g2z, SALT_CANYON);
        final long h = GTSRWorldgenHash.splitmix64((ca ^ cb) ^ Long.rotateLeft(ca + cb, 21));
        if ((h >>> 54) % 1000L >= CANYON_EDGE_PERMILLE) {
            return 0.0D;
        }
        final double ex = s2x - s1x;
        final double ez = s2z - s1z;
        final double dd = Math.sqrt(ex * ex + ez * ez);
        if (dd <= 0.0D) {
            return 0.0D;
        }
        final double perp = Math.abs(d1 - d2) / (2.0D * dd);
        if (perp >= CANYON_HALF_W_MAX) {
            return 0.0D;
        }
        final double mx = (s1x + s2x) * 0.5D;
        final double mz = (s1z + s2z) * 0.5D;
        final double along = ((x - mx) * -ez + (z - mz) * ex) / dd;
        final double half = CANYON_HALF_W_MIN + (CANYON_HALF_W_MAX - CANYON_HALF_W_MIN)
            * (0.5D + 0.5D * Math.sin(along * (2.0D * Math.PI / CANYON_WIDTH_WAVE)));
        if (perp >= half) {
            return 0.0D;
        }
        return CANYON_DEPTH_MIN + (CANYON_DEPTH_MAX - CANYON_DEPTH_MIN) * (1.0D - perp / half);
    }

    /**
     * 地表破面许可（p21 §1.5）：独立双分量场的 {@code ‖n‖ > 0.72} 才允许洞挖穿地表；
     * 未获许可的列由 {@link #carveCeilingAt} 封顶在表面下 ≥{@link #CAVE_SURFACE_CAP} 格 ⇒
     * 地表洞口稀疏且不成排。
     */
    public static boolean surfaceBreakAllowed(long worldSeed, int x, int z) {
        final double nx = GTSRWorldgenHash
            .valueNoise(worldSeed ^ SALT_CAVE_BREAK_A, x / CAVE_BREAK_SCALE, z / CAVE_BREAK_SCALE);
        final double nz = GTSRWorldgenHash
            .valueNoise(worldSeed ^ SALT_CAVE_BREAK_B, x / CAVE_BREAK_SCALE, z / CAVE_BREAK_SCALE);
        return nx * nx + nz * nz > CAVE_BREAK_NORM_MIN * CAVE_BREAK_NORM_MIN;
    }

    /**
     * 洞顶钳制：本列洞穴允许挖到的最高 y（对偶于 {@link #carveFloorY}）。
     * 获破面许可 ⇒ 可到 {@code surfaceY}（地表洞口）；否则封顶 {@code surfaceY - CAVE_SURFACE_CAP}，
     * 且不低于 {@link #CAVE_Y_MIN}。
     */
    public static int carveCeilingAt(long worldSeed, int x, int z, int surfaceY) {
        final int cap = surfaceBreakAllowed(worldSeed, x, z) ? surfaceY : surfaceY - CAVE_SURFACE_CAP;
        return cap < CAVE_Y_MIN ? CAVE_Y_MIN : cap;
    }

    // ═════════════════ 裁决 1 保护门（P0-FILL R1/R3/R4 改写 p21 §1.4/§1.6）════════════════

    /**
     * 水体列保护（裁决 1 四源并集，消费面全部走版一导出的 public 纯函数）：
     * <ol>
     * <li>残潭：{@code swampRiverPoolAt(seed,x,z,roster) > 0}（RVF:1700，五腿内含 roster==3 短路）；</li>
     * <li>微池：{@code swampLakeAt(seed,x,z,roster) < SWAMP_POOL_WATER_LEVEL}（RVF:1588/1577；
     * 非沼泽 roster 恒 {@code NO_SWAMP_POOL=1.0}，比较式天然短路）；</li>
     * <li>三档：{@code TerrainVariants.swampTierAt(...) != SWAMP_TIER_NONE}（TV:814/506）；</li>
     * <li>湖域/主干：{@code lakeAt < LAKE_WATER_LEVEL}（湖床区，RVF:1171/468）∨
     * {@code trunkAt > 0}（主干带，RVF:1112）——p21 §1.4 原式两腿保留。</li>
     * </ol>
     * 命中列的 carve 下界由 {@link #carveFloorY} 夹壳（悬空水/漏潭 = 版 A A3 收口的 874 列缺陷，
     * 钳制按"无洞地形"算成，carver 挖穿壳即复现，见 P0-FILL 表 3）。
     */
    public static boolean waterColumnProtected(long worldSeed, int x, int z, int rosterIndex) {
        // O1a（v1.20.43 P22 版 B）：①②③④腿的比较式并入 RVF/TerrainVariants 布尔单一出口
        // （纯包装，腿序与短路语义逐字保持）；⑤主干腿是本谓词独有子式，保持内联。
        if (GTSRVoronoiRiverField.swampRiverPoolColumnAt(worldSeed, x, z, rosterIndex)) {
            return true;
        }
        if (GTSRVoronoiRiverField.swampPoolWaterAt(worldSeed, x, z, rosterIndex)) {
            return true;
        }
        if (TerrainVariants.swampTieredAt(worldSeed, x, z, rosterIndex)) {
            return true;
        }
        if (GTSRVoronoiRiverField.lakeWaterAt(worldSeed, x, z)) {
            return true;
        }
        return GTSRVoronoiRiverField.trunkAt(worldSeed, x, z) > 0.0D;
    }

    /**
     * 受保护列的 carve 下界（裁决 1 / R4 钉口径）：命中 {@link #waterColumnProtected} 的列夹到
     * {@code max(CAVE_Y_MIN, heightAt(x,z) + CAVE_LAKE_SHELL)} ⇒ 湖床/河床/潭底之下永远留 3 格壳。
     * <b>壳底钉 {@link ProsperityTerrainProfile#heightAt}（挖后含潭/池下挖的地形面），
     * 禁"挖前地面"口径、禁旁路 heightCore</b>（R4：误用挖前面即把潭底壳算浅）。
     * 非保护列返回 {@link #CAVE_Y_MIN}（基岩带取大者在 carver 侧做）。
     */
    public static int carveFloorY(long worldSeed, int x, int z, int rosterIndex) {
        if (!waterColumnProtected(worldSeed, x, z, rosterIndex)) {
            return CAVE_Y_MIN;
        }
        final int shell = ProsperityTerrainProfile.heightAt(worldSeed, x, z) + CAVE_LAKE_SHELL;
        return shell > CAVE_Y_MIN ? shell : CAVE_Y_MIN;
    }

    /**
     * 岛/柱整列不挖（R3 改写 p21 §1.6："空心柱"风险已转"岛面塌陷"，门更宽）：
     * 岛域 = {@code lakeAt < LAKE_ISLAND}（RVF:1171/577）的命中列及外扩 {@link #CAVE_ISLAND_GUARD}
     * 格外扩环（81 列抽检，lakeAt 自带列级 memo）∪ 柱域 {@code islandPillarAt}（RVF:1501，版一已导出）。
     */
    public static boolean protectedColumn(long worldSeed, int x, int z) {
        if (GTSRVoronoiRiverField.islandPillarAt(worldSeed, x, z)) {
            return true;
        }
        for (int dz = -CAVE_ISLAND_GUARD; dz <= CAVE_ISLAND_GUARD; dz++) {
            for (int dx = -CAVE_ISLAND_GUARD; dx <= CAVE_ISLAND_GUARD; dx++) {
                if (GTSRVoronoiRiverField.lakeAt(worldSeed, x + dx, z + dz) < GTSRVoronoiRiverField.LAKE_ISLAND) {
                    return true;
                }
            }
        }
        return false;
    }

    // ═════════════════ 内部工具 ═════════════════

    /** 峡谷格点 jitter（∈ [-CANYON_JITTER, +CANYON_JITTER)，cell 的分数；轴用 flip 常数分离）。 */
    private static double jitter(long worldSeed, int gx, int gz, long axisFlip) {
        final long h = GTSRWorldgenHash
            .splitmix64(GTSRWorldgenHash.cellSeed(worldSeed, gx, gz, SALT_CANYON) ^ axisFlip);
        return (((h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR) * 2.0D - 1.0D) * CANYON_JITTER;
    }

    /** 负坐标安全的 double→int 下取整（同 {@code GTSRWorldgenHash.floorDiv} 口径，本类私有不复用）。 */
    private static int floorCell(double v) {
        final int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
