package com.miaokatze.gtsr.common.dimension.shattered;

/**
 * 破碎之地噪声高度纯函数（dim79 重做 S-B1，plan §5 S-B1）：照
 * {@link com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile}
 * 的零 MC value-noise 范式<b>独立参数化</b>（不共享抽象，YAGNI——evolver 2b 口径）。
 * <p>
 * <b>参数登记（可调常量，dim79-redo-slice-B-report.md 同步登记）</b>：
 * <ul>
 * <li>基准高度 BASE_HEIGHT = 64（旧浮岛模型 ISLAND_FLOOR 24 / CEIL 144 作废）；</li>
 * <li>钳制域 36..96（{@link #MIN_HEIGHT}/{@link #MAX_HEIGHT}，参数设计上 relief 极值
 * ±25.2+1.5 落在域内，钳制仅作病理兜底）；</li>
 * <li>双频起伏：主波长 160 幅度 ±12 + 次波长 48 幅度 ±6；细起伏波长 17 幅度 ±1.5
 * （不乘幅度调制，避免高频锯齿放大）；</li>
 * <li>低频幅度调制：波长 384，乘子 0.6..1.4（起伏/平缓分区观感，C0 连续）；</li>
 * <li>噪声域盐 0x5E471/0x5E472/0x5E473/0x5E47A（与 prosperity 族域分离）。</li>
 * </ul>
 * <p>
 * 实现为自研 value noise（splitmix64 哈希 + smoothstep 双线性），<b>零 Minecraft 依赖</b>
 * ——tools/dim1/ShatteredTerrainCheck（JEP330）可无游戏运行时调用：双跑逐字节一致、
 * clamp 域、相邻列差有界、无 NaN 四断言驱动。
 */
public final class ShatteredTerrainProfile {

    /** 基准高度（旧浮岛 24..144 域作废；完整正常地形以 64 为基准面）。 */
    public static final int BASE_HEIGHT = 64;

    /** 高度钳制下界（病理兜底；正常参数域 relief ≥ -25.2-1.5 不触底）。 */
    public static final int MIN_HEIGHT = 36;
    /** 高度钳制上界（同上；ruins MIN/MAX_SURFACE_Y 按本域重标定）。 */
    public static final int MAX_HEIGHT = 96;

    private ShatteredTerrainProfile() {}

    /**
     * (x,z) 列的地表实体高度（最高实体方块的 y）。纯函数：同 seed 同坐标恒同值。
     * 调用点（ChunkProviderShatteredGrounds）必须传 {@code world.getSeed()}（不含 def.seedSalt）
     * ——seedSalt 已由 GTSRWorldProviderBase 加在 provider 构造 seed 上，heightAt 只认世界种子。
     */
    public static int heightAt(long worldSeed, int x, int z) {
        // 低频幅度调制：波长 384，乘子 0.6..1.4
        final double zone = valueNoise(worldSeed ^ 0x5E47AL, x / 384.0D, z / 384.0D);
        final double amplitude = 1.0D + 0.4D * zone;
        // 双频起伏：主波长 160 ±12 + 次波长 48 ±6（乘幅度调制）
        final double h1 = valueNoise(worldSeed ^ 0x5E471L, x / 160.0D, z / 160.0D);
        final double h2 = valueNoise(worldSeed ^ 0x5E472L, x / 48.0D, z / 48.0D);
        // 细起伏：波长 17 ±1.5（不乘调制）
        final double h3 = valueNoise(worldSeed ^ 0x5E473L, x / 17.0D, z / 17.0D);
        final double relief = (h1 * 12.0D + h2 * 6.0D) * amplitude + h3 * 1.5D;
        final int y = BASE_HEIGHT + (int) Math.round(relief);
        return y < MIN_HEIGHT ? MIN_HEIGHT : Math.min(y, MAX_HEIGHT);
    }

    /**
     * 2D value noise（[-1,1)）：格点哈希 + smoothstep 双线性插值。
     * 纯函数；坐标越界安全（int 哈希乘法溢出环绕，同输入恒同输出）。
     */
    private static double valueNoise(long seed, double x, double z) {
        final int x0 = floorDiv(x);
        final int z0 = floorDiv(z);
        final double tx = smooth(x - x0);
        final double tz = smooth(z - z0);
        final double n00 = hashUnit(seed, x0, z0);
        final double n10 = hashUnit(seed, x0 + 1, z0);
        final double n01 = hashUnit(seed, x0, z0 + 1);
        final double n11 = hashUnit(seed, x0 + 1, z0 + 1);
        final double a = n00 + (n10 - n00) * tx;
        final double b = n01 + (n11 - n01) * tx;
        return a + (b - a) * tz;
    }

    /** 负坐标安全的 double→int 取整（Math.floor 慢路径避免）。 */
    private static int floorDiv(double v) {
        final int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /** smoothstep 权重（3t²-2t³）。 */
    private static double smooth(double t) {
        return t * t * (3.0D - 2.0D * t);
    }

    /** splitmix64 终结哈希 → [-1,1) 单位值（与 GTSRWorldgenHash/ChunkManager 同族常数族）。 */
    private static double hashUnit(long seed, int x, int z) {
        long h = seed ^ (x * 0x27D4EB2F165667C5L) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return ((h >>> 11) / (double) (1L << 52)) * 2.0D - 1.0D;
    }
}
