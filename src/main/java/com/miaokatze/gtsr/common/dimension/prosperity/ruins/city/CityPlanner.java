package com.miaokatze.gtsr.common.dimension.prosperity.ruins.city;

import com.miaokatze.gtsr.common.dimension.framework.BiomeZoneSelector;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeBrassWastes;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeFumaroleSwamp;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeGearworkForest;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeRustedSteppe;
import com.miaokatze.gtsr.config.Config;

/**
 * 古代城选址规划纯函数（dim1 S4b，plan §3.1 选址与尺度）。
 * <p>
 * cell 网格 = 24 chunk（384 格）；{@code cellSeed = GTSRWorldgenHash.cellSeed(worldSeed,
 * floorDiv(cx,24), floorDiv(cz,24), SALT_CITY)}。存在掷骰 = 混合哈希 % 100 &lt;
 * {@link Config#prosperityCityChance}（默认 45，0 = 全禁用）。中心 chunk = cell 原点 +
 * (8 + hash%8, 8 + hash%8)（cell 中部 8×8 内）；半径 = 4 + hash%4（4-7 chunk，≤8 chunk 钳制）。
 * <p>
 * <b>纯函数</b>：只依赖（worldSeed, cell 坐标）与 Config 常量，零世界读取；同 seed 同 cell
 * 任意次规划逐字节一致（tools/dim1/CityDeterminismCheck 自证）。任何 chunk 都能独立重算
 * 邻域城市——跨 chunk 渲染无共享状态。
 * <p>
 * 检索口径：城影响（半径 7 + plot 外扩 + 变体 footprint 外溢）至多越出本 cell 约 1 chunk，
 * 故 chunk 检索扫描 3×3 cell 邻域（{@link #citiesNear}），渲染/跳过判定统一用
 * {@code |chunk - centerChunk| ≤ radius+1} 窗口（plan §3.4 半径+1 缓冲同口径）。
 * <p>
 * <b>P6 城门（plan §2.1 L6 + §2.2 H-1 + §7.1 U2）</b>：城市只允许出现在<b>锈蚀草原 macro 带</b>。
 * <ul>
 * <li>门条件档由 {@link Config#prosperityCityBiomeGate} 决定（默认 1 = 锚点带；见
 * {@link #cityGateAllows}），带尺度由 {@link Config#prosperityBiomeMacroBandChunks} 决定；</li>
 * <li>身份判定走 {@link BiomeZoneSelector#bandIndex}——与 {@code GTSRWorldChunkManager.biomeAt}
 * （即 L1 {@link GTSRBiomeAuthority} 的绑定源）<b>同一个纯函数、同一张权重表、同一个域分离盐</b>，
 * 故渲染侧与放置侧不可能各说一套；</li>
 * <li><b>禁止</b> {@code world.getBiomeGenForCoords}（plan §2.1 L6：城中心最远跨 8 chunk，读世界会
 * 触发邻 chunk 生成）；本类因此<b>继续保持零世界读取</b>；</li>
 * <li>门<b>只</b>在 {@link #citiesNear} 内生效——渲染（{@code placeCities}）与抑制
 * （{@code cities.length > 0} 跳过散布/机器）本来就共用这一个入口，因此不存在"鬼窗"
 * （渲染说有城、放置器说不许放）。{@link #planFor} 保持<b>不含门</b>的纯几何口径，
 * 使既有确定性自检与 /gtsr structure 直写不受影响。</li>
 * </ul>
 */
public final class CityPlanner {

    /** 城市网格周期（chunk；plan §3.1：CITY_CELL = 24）。 */
    public static final int CITY_CELL = 24;

    /** 城市盐（"cItY" 助记；GTSRWorldgenHash.cellSeed 盐隔离不同用途）。 */
    public static final long SALT_CITY = 0xC174L;

    /** 城门条件档：关（改造前行为，四带均可出城）。 */
    public static final int GATE_OFF = 0;
    /** 城门条件档：城盘锚点（中心 chunk）所在 macro 带为锈蚀草原（U2 锁定默认档）。 */
    public static final int GATE_ANCHOR_BAND = 1;
    /** 城门条件档：城盘（边长 2r+1 方形盘）≥50% chunk 落在锈蚀草原带。 */
    public static final int GATE_DISC_HALF = 2;
    /** 城门条件档：城盘 100% 落在锈蚀草原带。 */
    public static final int GATE_DISC_ALL = 3;

    /**
     * dim78 群系权重表（macro 带尺度守恒的输入）。
     * <p>
     * <b>不新增数值真值</b>：四项分别引用四个群系类自己的 {@code WEIGHT} 常数，顺序与
     * {@code ProsperityBiomes.init} 的 {@code attachBiome} 挂接顺序、
     * {@link GTSRBiomeAuthority.BiomeId} 的维内下标完全一致（0=草原）；三处一致性由
     * {@code tools/dim1/BiomeBandHierarchyCheck} 的 C 组源级断言 + 与真实 def 权重表的逐位对比钉住。
     */
    private static final int[] PROSPERITY_BAND_WEIGHTS = { BiomeRustedSteppe.WEIGHT, BiomeGearworkForest.WEIGHT,
        BiomeBrassWastes.WEIGHT, BiomeFumaroleSwamp.WEIGHT };

    private CityPlanner() {}

    /** 单 cell 规划：无城返回 null。 */
    public static CityPlan planFor(long worldSeed, int cellX, int cellZ) {
        final long cellSeed = GTSRWorldgenHash.cellSeed(worldSeed, cellX, cellZ, SALT_CITY);
        if (mix(cellSeed, 1) % 100L >= Config.prosperityCityChance) {
            return null;
        }
        final int offsetChunkX = 8 + (int) (mix(cellSeed, 2) % 8); // 8..15
        final int offsetChunkZ = 8 + (int) (mix(cellSeed, 3) % 8); // 8..15
        final int radiusChunks = 4 + (int) (mix(cellSeed, 4) % 4); // 4..7
        return new CityPlan(
            worldSeed,
            cellSeed,
            cellX * CITY_CELL + offsetChunkX,
            cellZ * CITY_CELL + offsetChunkZ,
            radiusChunks);
    }

    /**
     * chunk 邻域城市检索（3×3 cell 扫描 + 城门 + 窗口过滤；无城返回空数组零分配）。
     * 渲染与"城内跳过散布/机器"共用本入口（判定一致）。
     * <p>
     * P6 起本方法是<b>城门的唯一生效点</b>：{@link #planFor} 出来的候选城先过
     * {@link #cityGateAllows}，再过缓冲窗。因为渲染侧（{@code ProsperityWorldGenerator.placeCities}）
     * 与抑制侧（{@code cities.length > 0}）拿到的都是这里的结果，两侧对"此处有城"的判定
     * <b>按构造等价</b>（判据「无鬼窗」；逐点核对见 {@code tools/dim1/CityBiomeGateCheck} A 组）。
     */
    public static CityPlan[] citiesNear(long worldSeed, int chunkX, int chunkZ) {
        final int baseCellX = Math.floorDiv(chunkX, CITY_CELL);
        final int baseCellZ = Math.floorDiv(chunkZ, CITY_CELL);
        CityPlan[] out = null;
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final CityPlan plan = planFor(worldSeed, baseCellX + dx, baseCellZ + dz);
                if (plan != null && cityGateAllows(worldSeed, plan) && plan.chunkInBuffer(chunkX, chunkZ)) {
                    if (out == null) {
                        out = new CityPlan[4];
                    }
                    if (n == out.length) {
                        final CityPlan[] bigger = new CityPlan[n * 2];
                        System.arraycopy(out, 0, bigger, 0, n);
                        out = bigger;
                    }
                    out[n++] = plan;
                }
            }
        }
        if (out == null) {
            return new CityPlan[0];
        }
        final CityPlan[] exact = new CityPlan[n];
        System.arraycopy(out, 0, exact, 0, n);
        return exact;
    }

    /**
     * 该 chunk 所在 macro 带的群系身份下标（H-1 唯一出口；dim78 权重表/名册下标口径）。
     * <p>
     * 与 {@code GTSRWorldChunkManager.biomeAt} 走同一个 {@link BiomeZoneSelector#bandIndex}：
     * 同 seed、同 {@link Config#prosperityBiomeMacroBandChunks}、同权重表、同域分离盐
     * （{@link BiomeZoneSelector#ZONE_SALT_PROSPERITY}）。零世界读取（L6 红线）。
     */
    public static int bandIndexAt(long worldSeed, int chunkX, int chunkZ) {
        return BiomeZoneSelector.bandIndex(
            worldSeed,
            chunkX,
            chunkZ,
            PROSPERITY_BAND_WEIGHTS.length,
            PROSPERITY_BAND_WEIGHTS,
            Config.prosperityBiomeMacroBandChunks,
            BiomeZoneSelector.ZONE_SALT_PROSPERITY);
    }

    /** 该 chunk 的 macro 带是否为锈蚀草原（门的原子判定）。 */
    public static boolean steppeBandAt(long worldSeed, int chunkX, int chunkZ) {
        return bandIndexAt(worldSeed, chunkX, chunkZ) == GTSRBiomeAuthority.BiomeId.RUSTED_STEPPE.rosterIndex();
    }

    /**
     * 城门（L6）：候选城是否允许存在。纯函数、零世界读取。
     * <p>
     * 档由 {@link Config#prosperityCityBiomeGate} 给出（0 关 / 1 锚点带 / 2 城盘 ≥50% / 3 城盘全落带；
     * 越界值钳到 [0,3]，未知值按最严档 3 处理）。"城盘"取以中心 chunk 为心、半径
     * {@code getRadiusChunks()} 的方形盘（边长 9..15 chunk，与 {@code CityPlan} 的城界半径同口径），
     * <b>不含</b>缓冲窗外溢（外溢是渲染裁剪口径，不是城的占地）。
     * <p>
     * 代价提示：档 2/3 每城最多 15×15=225 次带掷骰（每次 O(1) 纯哈希），且只在候选城非空时跑；
     * 默认档 1 每候选城 1 次。城市数与暴露面积的门档选择见 plan/investigation/p6-*（四张数字表）。
     */
    public static boolean cityGateAllows(long worldSeed, CityPlan plan) {
        if (plan == null) {
            return false;
        }
        final int tier = Math.max(GATE_OFF, Math.min(GATE_DISC_ALL, Config.prosperityCityBiomeGate));
        if (tier == GATE_OFF) {
            return true;
        }
        if (tier == GATE_ANCHOR_BAND) {
            return steppeBandAt(worldSeed, plan.getCenterChunkX(), plan.getCenterChunkZ());
        }
        final int r = plan.getRadiusChunks();
        final int need = tier == GATE_DISC_ALL ? (2 * r + 1) * (2 * r + 1) : ((2 * r + 1) * (2 * r + 1) + 1) / 2;
        int inBand = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (steppeBandAt(worldSeed, plan.getCenterChunkX() + dx, plan.getCenterChunkZ() + dz)) {
                    inBand++;
                    if (inBand >= need) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * splitmix64 终结混哈希（非负长整）——<b>P3 起算法体在 {@link GTSRWorldgenHash}</b>，
     * 本方法只保留城市选址这一调用场景的<b>专属盐乘子</b>实参。
     * <p>
     * <b>登记差异（审计 A-5 §5 的"同族常数族"说法需更正）</b>：本方法与
     * {@code CityVariants.mix} 只在「完整 splitmix64 终结器 + 非负掩码」上同族，
     * <b>盐乘子不同值</b>——此处用 {@link GTSRWorldgenHash#CITY_PLAN_SALT_MUL}
     * （0xD1B5…），变体侧用 {@link GTSRWorldgenHash#CITY_PLOT_SALT_MUL}（0x9E37…）。
     * 二者对同一 (seed, salt) 的输出<b>不逐位相同</b>，故两片各传自己的乘子、<b>不合并</b>；
     * 逐位对拍见 {@code tools/dim1/SurfaceYParityCheck} 的 {@code city.planner_mix} 站点。
     */
    static long mix(long cellSeed, long salt) {
        return GTSRWorldgenHash.saltRoutedHash(cellSeed, salt, GTSRWorldgenHash.CITY_PLAN_SALT_MUL);
    }
}
