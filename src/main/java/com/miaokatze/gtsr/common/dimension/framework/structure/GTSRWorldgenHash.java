package com.miaokatze.gtsr.common.dimension.framework.structure;

/**
 * 全维度统一世界生成哈希源（dim1 S4a，plan §1.2 S4a / 02 §8.3 代码 21 原样）。
 * <p>
 * worldSeed^chunkX^chunkZ 风格派生（确定性种子口径，02 §0.3）：同一种子同一坐标永远派生同一结果，
 * 任何 chunk 都能独立重算——跨 chunk 生成无需共享状态。纯函数，零 Minecraft 依赖（可离线实例化）。
 */
public final class GTSRWorldgenHash {

    private GTSRWorldgenHash() {}

    // ══════════════════════════════════════════════════════════════════════════════════
    // P3（plan §5 P3 / §2.1 L2「禁止手搓哈希」/ §2.4 判据 4）——L2 哈希单一真值
    //
    // 以下常量的<b>值</b>逐字取自改造前各手搓站点（清单见
    // plan/investigation/dim78-architecture-audit-20260918.md A-5 §5 与其更正），本片只把
    // 它们搬到本类一处，<b>不改动任何数值语义</b>。"改造前后逐位相同"由
    // tools/dim1/SurfaceYParityCheck 的哈希摘要对拍断言（BASE=本片开工前快照，AFTER=当前树）。
    //
    // 既有三个公开入口（chunkSeed / cellSeed / blockSeed）的<b>算法与常数均未变</b>：
    // 只把内联字面量换成同值具名常数（cellSeed 的 x/z 乘子即下面的 CELL_MUL_X/Z，
    // blockSeed 的即 COLUMN_MUL_X/Z），生成字节与取值完全一致——这同样是"新增"而非"修改"。
    // ══════════════════════════════════════════════════════════════════════════════════

    /** splitmix64 混合常数 ①（改造前在 7 个类里各抄一遍）。 */
    public static final long SPLITMIX_MUL_1 = 0xFF51AFD7ED558CCDL;
    /** splitmix64 混合常数 ②（改造前同上，7 处各一份）。 */
    public static final long SPLITMIX_MUL_2 = 0xC4CEB9FE1A85EC53L;
    /**
     * splitmix64 的标准"黄金比增量"。本仓把它<b>复用</b>为两处 z/x 乘子
     * （{@code GTSRWorldChunkManager.HASH_CONSTANT_Z} 与 {@code BiomeZoneSelector.CHUNK_CONSTANT_X}）——
     * 这是历史事实，不是笔误，本片不改。
     */
    public static final long SPLITMIX_INCREMENT = 0xBF58476D1CE4E5B9L;
    /** cell 级 x 乘子（= {@link #cellSeed} 原内联常数；{@code BiomeZoneSelector}/{@code bedrockTop} 同源）。 */
    public static final long CELL_MUL_X = 0x9E3779B97F4A7C15L;
    /** cell 级 z 乘子（= {@link #cellSeed} 原内联常数）。 */
    public static final long CELL_MUL_Z = 0xC2B2AE3D27D4EB4FL;
    /** 列/格点 x 乘子（= {@link #blockSeed} 原内联常数；噪声格点与表层 filler 深度哈希共用）。 */
    public static final long COLUMN_MUL_X = 0x27D4EB2F165667C5L;
    /** 列/格点 z 乘子（= {@link #blockSeed} 原内联常数）。 */
    public static final long COLUMN_MUL_Z = 0x165667B19E3779F9L;
    /** chunk 级边带掷骰 z 乘子（原 {@code BiomeZoneSelector.CHUNK_CONSTANT_Z}）。 */
    public static final long CHUNK_Z_MUL = 0x94D049BB133111EBL;
    /**
     * 城市<b>选址</b>掷骰盐乘子（原 {@code CityPlanner.mix} 内联）。
     * <b>注意：与下面的 {@link #CITY_PLOT_SALT_MUL} 不同值</b>——两份城市 mix 只在终结器与
     * 非负掩码上同族，盐乘子一个用 {@code 0xD1B5…}、一个用 {@code 0x9E37…}；
     * 二者注释互相声称"同族常数族"，实测<b>不逐位相同</b>，故保留两值、只共用一处入口。
     */
    public static final long CITY_PLAN_SALT_MUL = 0xD1B54A32D192ED03L;
    /** 城市<b>变体</b>（plot）掷骰盐乘子（原 {@code CityVariants.mix} 内联，值同 {@link #CELL_MUL_X}）。 */
    public static final long CITY_PLOT_SALT_MUL = CELL_MUL_X;
    /** 长整非负掩码（原 {@code CityPlanner.mix}/{@code CityVariants.mix} 各自内联）。 */
    public static final long NON_NEGATIVE = 0x7FFFFFFFFFFFFFFFL;
    /** int 非负 31 位掩码（原 {@code GTSRWorldChunkManager.hash} 内联）。 */
    public static final int NON_NEGATIVE_31 = 0x7FFFFFFF;
    /** 单位化除数 2^53：{@code >>>11} 留 53 位有效值，必须除 2^53 才得 [0,1)。 */
    public static final long UNIT_DIVISOR = 1L << 53;

    /** chunk 级哈希（残缺机器/散布的 per-chunk 决策种子）。 */
    public static long chunkSeed(long worldSeed, int cx, int cz) {
        return worldSeed ^ ((long) cx * 341873128712L + (long) cz * 132897987541L);
    }

    /**
     * cell 级哈希（大矿坑 cell=16 / 矿洞 cell=4 / 城市 cell=24 / 通用）：盐隔离同 cell 不同用途，
     * 避免不同生成器在同一 cell 上做出同相关的决策。
     */
    public static long cellSeed(long worldSeed, int gx, int gz, long salt) {
        return worldSeed ^ ((long) gx * CELL_MUL_X) ^ ((long) gz * CELL_MUL_Z) ^ salt;
    }

    /** block 级哈希（逐方块确定性掷骰，如损伤/缺失判定）。 */
    public static long blockSeed(long worldSeed, int x, int y, int z) {
        return worldSeed ^ ((long) x * COLUMN_MUL_X) ^ ((long) y << 32) ^ ((long) z * COLUMN_MUL_Z);
    }

    /**
     * 完整 splitmix64 终结器（三轮 xor-shift + 两轮乘法）。
     * <p>
     * 改造前的等价实现体：{@code BiomeZoneSelector.mix}、{@code GTSRWorldChunkManager.hash} 的
     * 终结段、两份 {@code *TerrainProfile.hashUnit} 的终结段、{@code CityPlanner.mix} 与
     * {@code CityVariants.mix} 的终结段。<b>纯 long 位运算，跨平台确定。</b>
     */
    public static long splitmix64(long h) {
        h ^= h >>> 33;
        h *= SPLITMIX_MUL_1;
        h ^= h >>> 33;
        h *= SPLITMIX_MUL_2;
        h ^= h >>> 33;
        return h;
    }

    /**
     * <b>截断</b>版 splitmix64 终结器（一轮乘法即返回）——表层 filler 深度哈希的历史口径
     * （原 {@code GTSRChunkProviderBase.mixColumn}，其历史为两 provider 各一份逐字符相同的 7 行）。
     * <p>
     * 与 {@link #splitmix64} <b>不等价</b>（少一轮乘法和末次 xor-shift），故单列一个入口而不是
     * 复用完整终结器——复用会改变 filler 深度取值，即改变地表填充层厚度。
     */
    public static long splitmix64Truncated(long h) {
        h ^= h >>> 33;
        h *= SPLITMIX_MUL_1;
        h ^= h >>> 33;
        return h;
    }

    /**
     * 单步 xor-shift（<b>无乘法</b>）——基岩顶深度哈希的历史口径。
     * 同样与上面两个终结器不等价，勿混用。
     */
    public static long xorShiftOnce(long h) {
        return h ^ (h >>> 33);
    }

    /**
     * 通用输入混合「{@code seed ^ (x*xMul) ^ (z*zMul) ^ salt}」。乘子由调用点<b>指名</b>
     * （{@link #CELL_MUL_X}/{@link #SPLITMIX_INCREMENT}/{@link #CHUNK_Z_MUL}…），本类是唯一出处；
     * {@code int} 乘 {@code long} 的提升语义逐字承袭改造前各站点。
     */
    public static long mixSeed(long seed, int x, int z, long xMul, long zMul, long salt) {
        return seed ^ (x * xMul) ^ (z * zMul) ^ salt;
    }

    /** 列向格点输入（噪声格点与表层 filler 深度共用；等价于 {@code blockSeed(seed, x, 0, z)}）。 */
    public static long columnSeed(long seed, int x, int z) {
        return seed ^ (x * COLUMN_MUL_X) ^ (z * COLUMN_MUL_Z);
    }

    /**
     * 表层 filler 深度哈希（原 {@code GTSRChunkProviderBase.mixColumn} 的完整实现体）。
     * 入参是<b>世界坐标</b>，故跨 chunk 无缝。
     */
    public static long fillerColumnHash(long worldSeed, int x, int z) {
        return splitmix64Truncated(columnSeed(worldSeed, x, z));
    }

    /**
     * 噪声格点单位值 ∈ [-1,1)（原两份 {@code *TerrainProfile.hashUnit} 的完整实现体，
     * 二者逐字符等价）。除数口径见 {@link #UNIT_DIVISOR}（v1.20.30 削平事故根因即除错 2^52）。
     */
    public static double unitNoise(long seed, int x, int z) {
        final long h = splitmix64(columnSeed(seed, x, z));
        return ((h >>> 11) / (double) UNIT_DIVISOR) * 2.0D - 1.0D;
    }

    /**
     * 盐乘子路由的非负长整哈希（原 {@code CityPlanner.mix} 与 {@code CityVariants.mix} 的形状；
     * 二者<b>只剩盐乘子不同</b>，乘子必须显式传入——见 {@link #CITY_PLAN_SALT_MUL} 的告警注释）。
     */
    public static long saltRoutedHash(long seed, long salt, long saltMultiplier) {
        return splitmix64(seed ^ (salt * saltMultiplier)) & NON_NEGATIVE;
    }

    /** 基岩顶深度 y（0..3）：确定性列哈希，跨 chunk 无缝（原两 provider 各一份逐字符相同的 {@code bedrockTop}）。 */
    public static int bedrockTopHash(long worldSeed, int x, int z) {
        return (int) (xorShiftOnce(cellSeed(worldSeed, x, z, 0L)) & 3);
    }

    /**
     * 2D value noise（[-1,1)）：格点哈希 + smoothstep 双线性插值。
     * <p>
     * <b>P3 单一真值</b>：改造前 {@code ProsperityTerrainProfile} 与 {@code ShatteredTerrainProfile}
     * 各带一份 40 行实现体，{@code diff} 实测<b>逐字符等价</b>（连注释一并相同），故合并到此处；
     * 两维的真实差异只在各自的 {@code heightAt} 参数域（波长/振幅/BASE/MIN/MAX），
     * 那部分<b>不合并</b>，留在各自 Profile。纯函数；坐标越界安全
     * （int 哈希乘法溢出环绕，同输入恒同输出）。零 Minecraft 依赖。
     */
    public static double valueNoise(long seed, double x, double z) {
        final int x0 = floorDiv(x);
        final int z0 = floorDiv(z);
        final double tx = smooth(x - x0);
        final double tz = smooth(z - z0);
        final double n00 = unitNoise(seed, x0, z0);
        final double n10 = unitNoise(seed, x0 + 1, z0);
        final double n01 = unitNoise(seed, x0, z0 + 1);
        final double n11 = unitNoise(seed, x0 + 1, z0 + 1);
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
}
