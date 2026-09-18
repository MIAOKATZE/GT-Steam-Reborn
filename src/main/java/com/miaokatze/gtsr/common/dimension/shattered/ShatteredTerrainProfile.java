package com.miaokatze.gtsr.common.dimension.shattered;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;

/**
 * 破碎之地噪声高度纯函数（dim79 重做 S-B1，plan §5 S-B1）：照
 * {@link com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile}
 * 的零 MC value-noise 范式<b>独立参数化</b>（不共享抽象，YAGNI——evolver 2b 口径）。
 * <p>
 * <b>P3 更正上面的"不共享抽象"口径</b>（plan §5 P3 / §3.1 更正 1）：两维的<b>噪声内核</b>
 * （{@code valueNoise/floorDiv/smooth/hashUnit} 40 行）经 {@code diff} 实测<b>逐字符等价</b>，
 * 已合并到 {@link GTSRWorldgenHash#valueNoise(long, double, double)} 唯一出处；
 * <b>独立参数化</b>仍然成立且本片一字未改——真正各维一套的是下面的 BASE/波长/幅度/域盐/clamp。
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
     * <p>
     * <b>P3（plan §5 P3 / §2.1 L2）</b>：噪声内核 {@code valueNoise} 已上收到
     * {@link GTSRWorldgenHash#valueNoise(long, double, double)} 一份（与 dim78 侧逐字符等价）；
     * 本方法内的<b>波长 / 幅度 / 域盐 / clamp 全部保持原值</b>——它们才是两维的真实差异，
     * 合并前后 {@code heightAt} 逐点一致（{@code tools/dim1/SurfaceYParityCheck} 1 万点断言）。
     */
    public static int heightAt(long worldSeed, int x, int z) {
        // 低频幅度调制：波长 384，乘子 0.6..1.4
        final double zone = GTSRWorldgenHash.valueNoise(worldSeed ^ 0x5E47AL, x / 384.0D, z / 384.0D);
        final double amplitude = 1.0D + 0.4D * zone;
        // 双频起伏：主波长 160 ±12 + 次波长 48 ±6（乘幅度调制）
        final double h1 = GTSRWorldgenHash.valueNoise(worldSeed ^ 0x5E471L, x / 160.0D, z / 160.0D);
        final double h2 = GTSRWorldgenHash.valueNoise(worldSeed ^ 0x5E472L, x / 48.0D, z / 48.0D);
        // 细起伏：波长 17 ±1.5（不乘调制）
        final double h3 = GTSRWorldgenHash.valueNoise(worldSeed ^ 0x5E473L, x / 17.0D, z / 17.0D);
        final double relief = (h1 * 12.0D + h2 * 6.0D) * amplitude + h3 * 1.5D;
        final int y = BASE_HEIGHT + (int) Math.round(relief);
        return y < MIN_HEIGHT ? MIN_HEIGHT : Math.min(y, MAX_HEIGHT);
    }
}
