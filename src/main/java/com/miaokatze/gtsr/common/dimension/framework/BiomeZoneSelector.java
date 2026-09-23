package com.miaokatze.gtsr.common.dimension.framework;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;

/**
 * micro 强度层掷骰（dim78 修复 S-A2 引入；<b>B2 起身份带职责整体退役</b>，GenLayer 迁移收尾）。
 * <p>
 * <b>B2 退役清单（本类不再提供任何群系身份）</b>：S-A2/P6 时代的 cell 级权重掷骰
 * （{@code select}/{@code zoneOfCell}/{@code pickWeighted}，权重 45/30/15/10）、macro 带折算出口
 * （{@code bandIndex}/{@code bandIdentity}/{@code ZoneDelegate}/{@code normalizeMacroCell}）与边带
 * 12% 过渡碎斑（{@code EDGE_BAND_FRACTION}/{@code isEdgeBand}/{@code bandChunks}/{@code chunkRoll}）
 * 已全部删除——群系身份自 B1 起唯一出口是 {@link GTSRWorldChunkManager#biomeAt} 背后的
 * {@link com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain}（等权轮盘 +
 * Zoom×DEFAULT_ZOOM_LEVELS(=7，v1.20.40 P19 由 5 抬到 7；旧值 (=5) 是本页落后于常量的假陈述，P20 债① 改齐)
 * + Smooth 粗层），城市门（{@code CityPlanner}）与装饰 micro 档（本类）都改吃该身份面。
 * 历史 selector 盐（{@code ZONE_SALT_PROSPERITY}）随机制一并退役，不再有申报处。
 * <p>
 * 本类保留下来的唯一职责是 <b>micro 强度层</b>（P6 U2「micro cell 16 chunk + 0.7/1.0/1.3」）：
 * 每 16 chunk cell 一整档的变体/装饰强度调制，<b>不参与群系身份</b>（身份只有链一条出口）。
 * 掷骰算法体（域分离盐 {@link #MICRO_DOMAIN} + {@link GTSRWorldgenHash} splitmix 终结器）
 * 一字未改，B2 前后同 seed 同坐标恒同档；其可用性门槛（「链缺席 ⇒ 中性 1.0」）在
 * {@link GTSRWorldChunkManager#microStrengthAt} 一侧，随身份面（{@code genChain}）判。
 * <p>
 * 纯函数：零 Minecraft import、离线 JEP330 可复算；同 seed 同坐标结果恒定，与 chunk 生成顺序无关。
 */
public final class BiomeZoneSelector {

    /**
     * micro cell 尺寸（chunk）：强度层的空间粒度。
     * <p>
     * 值就是原 {@code ZONE_CELL_CHUNKS}（用户拍板 48→16，plan §12 修订第 6 条），<b>一字未改</b>；
     * B2 起它只描述强度层粒度（身份的空间结构由 GenLayer 链自带的 zoom 尺度决定）。
     */
    public static final int MICRO_CELL_CHUNKS = 16;

    /**
     * micro 层的变体/装饰强度档（plan §7.1 U2 锁定的 0.7/1.0/1.3）。
     * 三档均值 == 1.0F ⇒ 强度层被消费时不改变"整维总量"口径（P5 的 K/落块上限不受影响）。
     */
    public static final float[] MICRO_STRENGTHS = { 0.7F, 1.0F, 1.3F };

    /** micro 强度层域分离盐（与任何其他 cellSeed 用途解耦，形状照原版）。 */
    private static final long MICRO_DOMAIN = 0x4D4943524F535452L; // "MICROSTR"

    private BiomeZoneSelector() {}

    /**
     * micro 层强度档 ∈ [0, {@link #MICRO_STRENGTHS})（只作用变体/装饰强度，<b>不参与身份</b>）。
     * <p>
     * 每 micro cell（16 chunk）一整档，与链身份<b>独立掷骰</b>（不同域分离盐、不同哈希输入
     * 形状），故同一群系区内各 cell 的强度档互不相关；三档等概率 ⇒ 均值恒 1.0。
     * 调用方另需传入本维接线盐（dim78 = def.seedSalt），故两维同种子同坐标也不会撞档。
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
}
