package com.miaokatze.gtsr.common.dimension.prosperity;

/**
 * 繁荣维度噪声高度纯函数（dim1 S4b，plan §1.1 :59-65 列有、分派表并入 S4b）。
 * <p>
 * <b>关键契约（evolver R7 / plan §3.1 高度红线）</b>：{@link #heightAt(long, int, int)} 是
 * ChunkProviderProsperityRuins 地形填充与古代城（CityPlanner/CityPlan）落地共用的<b>同一</b>
 * 纯函数——同 seed 同坐标必得同一高度，跨 chunk 接缝一致性由此保证；城市侧<b>禁止读任何方块</b>。
 * <p>
 * 参数口径（02 §2.1 噪声参数表裁剪）：
 * <ul>
 * <li>起伏幅度 ×1.2（02 §2.1 RELIEF_MULT，账本"主起伏 +20%"）；</li>
 * <li>海平面 68（02 §2.1 海拔抬升 +5），基准面 BASE=70 使地表绝大多数在"海平面"之上
 * （gtsr.brine 河流在用户裁剪范围外——本维度不放任何自然水，02 §0.2 河床项不实现）；</li>
 * <li>02 §1.1 四群系高度性格（root/var 按群系差异）按任务包口径简化为
 * "全维度统一缓丘 + 幅度调制"：以波长 384 的低频 zone 噪声做幅度乘子（0.6..1.4），
 * 保留起伏/平缓分区的观感差异且保持 C0 连续（per-chunk 群系哈希跳变不可作为高度输入，
 * 否则城市侧无法同源取值——这是简化的决定性理由）。</li>
 * </ul>
 * <p>
 * 实现为自研 value noise（splitmix64 哈希 + smoothstep 双线性），<b>零 Minecraft 依赖</b>
 * ——离线 RasterSink/确定性自检驱动（tools/dim1）可无游戏运行时调用。
 */
public final class ProsperityTerrainProfile {

    /** 基准高度（02 §2.1 海平面 68 之上 +2，保证无水地表以陆地为主）。 */
    public static final int BASE_HEIGHT = 70;

    /** 起伏倍率（02 §2.1 RELIEF_MULT = 1.2，主起伏 +20%）。 */
    public static final double RELIEF_MULT = 1.2D;

    /** 高度钳制下界（防极端调制穿 y=20 裂隙带以下的观感）。 */
    private static final int MIN_HEIGHT = 40;
    /** 高度钳制上界（城变体最高 16 层 + 顶饰留出余量）。 */
    private static final int MAX_HEIGHT = 110;

    private ProsperityTerrainProfile() {}

    /**
     * (x,z) 列的地表实体高度（最高实体方块的 y）。
     * 两个调用点必须传<b>同一</b> seed：{@code World.getSeed()}（不含 def.seedSalt）——
     * ChunkProviderProsperityRuins 与 CityPlanner/CityPlan 均如此（契约见类注释）。
     */
    public static int heightAt(long worldSeed, int x, int z) {
        // 低频幅度调制（"群系微调"连续化替代）：波长 384，乘子 0.6..1.4
        final double zone = valueNoise(worldSeed ^ 0x5A0E5A0EL, x / 384.0D, z / 384.0D);
        final double amplitude = 1.0D + 0.4D * zone;
        // 统一缓丘：主波长 180 ±12（×1.2）+ 次波长 56 ±5.4（×1.2）
        final double h1 = valueNoise(worldSeed, x / 180.0D, z / 180.0D);
        final double h2 = valueNoise(worldSeed ^ 0x11L, x / 56.0D, z / 56.0D);
        // 细起伏：波长 17 ±1.5（不乘 relief，避免高频锯齿被放大）
        final double h3 = valueNoise(worldSeed ^ 0x22L, x / 17.0D, z / 17.0D);
        final double relief = (h1 * 10.0D + h2 * 4.5D) * RELIEF_MULT * amplitude + h3 * 1.5D;
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
