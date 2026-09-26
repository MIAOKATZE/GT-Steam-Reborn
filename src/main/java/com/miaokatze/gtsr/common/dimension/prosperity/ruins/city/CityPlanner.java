package com.miaokatze.gtsr.common.dimension.prosperity.ruins.city;

import java.util.Arrays;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSpans;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
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
 * <b>城门（plan §2.1 L6 + §2.2 H-1 + §7.1 U2；B2 起读链身份面）</b>：城市只允许出现在
 * <b>锈蚀草原群系身份</b>的 chunk 上。
 * <ul>
 * <li>门条件档由 {@link Config#prosperityCityBiomeGate} 决定（默认 1 = 锚点群系；见
 * {@link #cityGateAllows}）；P6 时代的 macro 带尺度键（{@code prosperityBiomeMacroBandChunks}）
 * 自 B2 起不再参与城门（带机制退役，链的空间尺度由 GenLayer zoom 决定）；</li>
 * <li>身份判定走 {@link #bandIndexAt}——在本地按与 {@code GTSRWorldChunkManager.biomeAt}
 * <b>完全相同的构造参数</b>（{@code worldSeed ^ def.seedSalt}、L1 名册已配槽 id 的等权表、
 * chunk 中心块代表点）重建同一条 {@link GTSRGenLayerChain} 粗层，故渲染侧与放置侧不可能
 * 各说一套（逐位同一性由 {@code tools/dim1/BiomeBandHierarchyCheck} C1 在真实链上钉住）；
 * 保持本地重建而不是读 manager 实例，是为了守住本类"纯函数、零世界读取"的既有契约
 * （CityDeterminismCheck 在无任何 manager 绑定的 JVM 里照样自证）；</li>
 * <li><b>禁止</b> {@code world.getBiomeGenForCoords}（plan §2.1 L6：城中心最远跨 8 chunk，读世界会
 * 触发邻 chunk 生成）；本类因此<b>继续保持零世界读取</b>；</li>
 * <li>门<b>只</b>在 {@link #citiesNear} 内生效——渲染（{@code placeCities}）与抑制
 * （{@code cities.length > 0} 跳过散布/机器）本来就共用这一个入口，因此不存在"鬼窗"
 * （渲染说有城、放置器说不许放）。{@link #planFor} 保持<b>不含门</b>的纯几何口径，
 * 使既有确定性自检与 /gtsr structure 直写不受影响。</li>
 * <li><b>P19 §F 湿区避让臂</b>：城门在群系档之外还过一道<b>干区门</b>（纯函数）——
 * 外扩城盘过<b>占比制</b>（{@link PlacementGate#DRY_RATIO_CITY}）+ 城心核心区过<b>全过制</b>
 * strict 臂（{@link PlacementGate#dryFootprintStrict}），列级判据为<b>回填真值口径</b>
 * （回填置水列/湖面/贴河护带算湿，自然洼地放行）。这是"废弃城市泡在湖里"（G4/G3 根因：
 * placer 与选址零水体感知）的选址侧修复。</li>
 * <li><b>P24-B 候选迁移（弃位改挪位）</b>：任一臂在原锚点不过时，该 cell 不再直接弃位，而是
 * 在同一 cell 的 8×8 候选锚点域内按确定性子序再试 1-2 个备选点（{@link #resolveCityPlan}）；
 * 全不过才弃位。列级谓词与档位一字不动——只换"候选落点选择"，把 P23 R1 全域湖带来的
 * 湖心弃位（干区臂弃位率 25.0%→58.33%）从"弃"变"挪"。</li>
 * </ul>
 */
public final class CityPlanner {

    /** 城市网格周期（chunk；plan §3.1：CITY_CELL = 24）。 */
    public static final int CITY_CELL = 24;

    /**
     * cell 内候选城心的偏移域（chunk，含端）：城心 chunk = cell 原点 + {@code [8,15]}（cell 中部
     * 8×8）。{@link #planFor} 首次选点与 {@link #resolveCityPlan} 迁移换点<b>共用本域</b>。
     * <p>
     * <b>为什么迁移不扩域（P24-B 的边界）</b>：相邻 cell 锚点的最小间距 =
     * {@code CITY_CELL - ANCHOR_OFFSET_MAX + ANCHOR_OFFSET_MIN = 17} chunk，是 plan §3.1
     * "中部 8×8" 给出的既有城距包络。P24-B 的迁移只在本域内换点 ⇒ 城距包络与改动前
     * <b>逐格同界</b>（不引入城-城贴近/交叠的新风险），迁移只是"在同一批既有候选落点里
     * 换一个"。
     */
    private static final int ANCHOR_OFFSET_MIN = 8;
    private static final int ANCHOR_OFFSET_MAX = 15;
    /** 候选锚点偏移域边长（chunk；8 ⇒ 每 cell {@code 8×8=64} 个候选落点）。 */
    private static final int ANCHOR_REGION_SIDE = ANCHOR_OFFSET_MAX - ANCHOR_OFFSET_MIN + 1;

    /**
     * 候选迁移（P24-B）每 cell 最多再尝试的<b>备选锚点数</b>（原锚点之外；纯重试上限，
     * 非概率参数）。取 2 = 任务包给的余量档：本样本（12 候选）实测 <b>1 次</b>尝试即捕获
     * 全部迁移收益（cap=1 与 cap=2 读数逐位同：GATE_OFF 弃位 4/12），故第 2 次是"未见种子"
     * 的确定性余量，只在第 1 个备选点也被弃时才付代价（最坏代价 = 3 次锚点判定/cell）。
     */
    private static final int MIGRATION_MAX_ATTEMPTS = 2;

    /** 备选锚点枚举次序的起点盐（{@code mix(cellSeed, 盐)} 定起点；与 planFor 的 1..4 盐分路）。 */
    private static final long MIGRATION_ORDER_SALT = 0x3E7L;
    /**
     * 备选锚点枚举步长（与 64 互质 ⇒ {@code (start + k·13) mod 64} 走遍全部 64 个候选点）。
     * 起点由 cellSeed 决定、步长固定 ⇒ 子序是<b>确定性的纯置换</b>，零额外随机源；
     * 起点随 cell 变使"总是先试 +x 方向"的方向偏置不成立。
     */
    private static final int MIGRATION_SCAN_STRIDE = 13;

    /** 城市盐（"cItY" 助记；GTSRWorldgenHash.cellSeed 盐隔离不同用途）。 */
    public static final long SALT_CITY = 0xC174L;

    /** 城门条件档：关（改造前行为，四带均可出城）。 */
    public static final int GATE_OFF = 0;
    /** 城门条件档：城盘锚点（中心 chunk）所在群系身份为锈蚀草原（U2 锁定默认档）。 */
    public static final int GATE_ANCHOR_BAND = 1;
    /** 城门条件档：城盘（边长 2r+1 方形盘）≥50% chunk 落在锈蚀草原身份。 */
    public static final int GATE_DISC_HALF = 2;
    /** 城门条件档：城盘 100% 落在锈蚀草原身份。 */
    public static final int GATE_DISC_ALL = 3;

    /**
     * dim78 def.seedSalt（与 {@code CommonProxy} 构造 def 处的字面量 {@code 0x50524F53L} 一致；
     * 链种子 = {@code worldSeed ^ 此值}，与 {@code GTSRWorldChunkManager} 的种子礼仪同式）。
     */
    private static final long PROSPERITY_SEED_SALT = 0x50524F53L;

    private CityPlanner() {}

    /** 单 cell 规划：无城返回 null。 */
    public static CityPlan planFor(long worldSeed, int cellX, int cellZ) {
        final long cellSeed = GTSRWorldgenHash.cellSeed(worldSeed, cellX, cellZ, SALT_CITY);
        if (mix(cellSeed, 1) % 100L >= Config.prosperityCityChance) {
            return null;
        }
        final int offsetChunkX = ANCHOR_OFFSET_MIN + (int) (mix(cellSeed, 2) % ANCHOR_REGION_SIDE); // 8..15
        final int offsetChunkZ = ANCHOR_OFFSET_MIN + (int) (mix(cellSeed, 3) % ANCHOR_REGION_SIDE); // 8..15
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
                // P24-B：门 = resolveCityPlan（原锚点过门即原样；不过则同 cell 内迁移换点；
                // 全不过 = 弃位）。落点（placed）才是渲染/缓冲窗的真值——迁移后的城必须
                // 按新锚点渲染与判窗，否则就是"门说能放、渲染放在湿点"。
                final CityPlan placed = resolveCityPlan(worldSeed, planFor(worldSeed, baseCellX + dx, baseCellZ + dz));
                if (placed != null && placed.chunkInBuffer(chunkX, chunkZ)) {
                    if (out == null) {
                        out = new CityPlan[4];
                    }
                    if (n == out.length) {
                        final CityPlan[] bigger = new CityPlan[n * 2];
                        System.arraycopy(out, 0, bigger, 0, n);
                        out = bigger;
                    }
                    out[n++] = placed;
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
     * 该 chunk 的群系身份下标（dim78 名册下标口径；B2 起读链身份面，历史名 bandIndexAt 保留）。
     * <p>
     * 本地按 {@code GTSRWorldChunkManager.biomeAt} 的同一构造参数重建 {@link GTSRGenLayerChain}
     * 粗层：种子 = {@code worldSeed ^ def.seedSalt}、入参 = L1 名册<b>已配槽</b>成员的 biome id
     * 等权表（名册序，与 def 群系表挂接顺序同源）、代表点 = chunk 中心块
     * {@code (chunkX<<4)+8, (chunkZ<<4)+8}。链是纯函数（同 seed 同坐标恒同值），本地重建与
     * manager 常驻链逐位同一（BiomeBandHierarchyCheck C1 钉住），且不引入跨线程共享的可变链
     * 实例（每调用一条短命链，链构造为常数代价）。零世界读取（L6 红线）。
     *
     * @return 名册下标 ∈ [0, 4)；{@code -1} = 名册一个都没配槽（EMPTY 降级，无身份面，
     *         <b>不</b>伪造身份——与 {@code biomeAt} 的 null 口径同源）
     */
    public static int bandIndexAt(long worldSeed, int chunkX, int chunkZ) {
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        final int[] ids = new int[authority.rosterSize()];
        int kept = 0;
        for (final GTSRBiomeAuthority.BiomeId key : GTSRBiomeAuthority.BiomeId.values()) {
            // v1.20.39 T8 真缺陷修复：只吃 selector 成员（!inSelector() 的 roster-only 成员——
            // sanzu——不得进链），与 manager 的 def 表链保持同参同解（plan §3.3；BBH A1 钉）。
            if (!GTSRBiomeAuthority.DIM_KEY_PROSPERITY.equals(key.dimKey()) || !key.inSelector()) {
                continue;
            }
            final BiomeGenBase biome = authority.biomeOf(key);
            if (biome != null) {
                ids[kept++] = biome.biomeID;
            }
        }
        if (kept == 0) {
            return -1;
        }
        final int id = new GTSRGenLayerChain(worldSeed ^ PROSPERITY_SEED_SALT, Arrays.copyOf(ids, kept))
            .biomeAtCoarse((chunkX << 4) + 8, (chunkZ << 4) + 8);
        for (final GTSRBiomeAuthority.BiomeId key : GTSRBiomeAuthority.BiomeId.values()) {
            if (!GTSRBiomeAuthority.DIM_KEY_PROSPERITY.equals(key.dimKey())) {
                continue;
            }
            final BiomeGenBase biome = authority.biomeOf(key);
            if (biome != null && biome.biomeID == id) {
                return key.rosterIndex();
            }
        }
        return -1; // 理论不可达：链输出钳制 ∈ 入参 id 集合（防御，不伪造身份）
    }

    /** 该 chunk 的群系身份是否为锈蚀草原（门的原子判定；链身份面）。 */
    public static boolean steppeBandAt(long worldSeed, int chunkX, int chunkZ) {
        return bandIndexAt(worldSeed, chunkX, chunkZ) == GTSRBiomeAuthority.BiomeId.RUSTED_STEPPE.rosterIndex();
    }

    /**
     * 城盘干区检查的半径上限（chunk，<b>P19 §F 拍板</b>）：城半径上界 7 chunk（{@link #planFor}
     * 的 4+%4）+ 1 chunk 街道/地块外溢缓冲 = 8 chunk —— 与 {@code CityPlan.chunkInBuffer} 的
     * 最大覆盖窗同量级，保证 footprint 包住任何一档城盘的全部内容。
     */
    private static final int CITY_DRY_HALF_CHUNKS = 8;

    /**
     * 城心核心区半宽（方块，<b>P19 §F 拍板 + U5-redirect 占比制修正</b>）：64×64 全过制
     * strict 干区门（{@link PlacementGate#dryFootprintStrict}）——城市核心（街道网中心、
     * 主体地块）落水不可接受；外缘滩带由占比制容忍。
     */
    private static final int CITY_CORE_HALF_BLOCKS = 32;

    /**
     * 城门（L6 + <b>P19 §F 湿区避让 + P24-B 候选迁移</b>）：该 cell 是否有可放置的候选城。
     * 纯函数、零世界读取。<b>本方法是 {@link #resolveCityPlan} 的布尔薄包装</b>——返回 true 只
     * 表示"该 cell 存在一个过门的落点"，落点坐标须经 {@link #resolveCityPlan} 取（原锚点或迁移
     * 后的备选锚点）；生产侧唯一入口 {@link #citiesNear} 正是这样用的。
     * <p>
     * <b>两道臂</b>（在每个候选锚点上依次过）：
     * <ol>
     * <li><b>群系臂</b>（既有语义一字未改）：档由 {@link Config#prosperityCityBiomeGate} 给出
     * （0 关 / 1 锚点群系 / 2 城盘 ≥50% / 3 城盘全落群系；越界值钳到 [0,3]，未知值按最严档 3
     * 处理）。"城盘"取以中心 chunk 为心、半径 {@code getRadiusChunks()} 的方形盘
     * （边长 9..15 chunk，与 {@code CityPlan} 的城界半径同口径），<b>不含</b>缓冲窗外溢
     * （外溢是渲染裁剪口径，不是城的占地）。</li>
     * <li><b>干区臂</b>（P19 新增，群系门<b>关也生效</b>——避水不是群系偏好，是放置事实）：
     * 城心按 {@link #CITY_DRY_HALF_CHUNKS} 外扩的 footprint 过 {@link PlacementGate#dryFootprint}
     * （四角+中心+周界步 8 采样）+ 城心 64×64 过全过制 strict 臂；存在水体/河滩/贴河采样列即
     * <b>该锚点不可放</b>。</li>
     * </ol>
     * <b>弃位语义（P24-B 起）</b>：单个锚点不过 ⇒ 不是立刻弃位，而是在同 cell 的 8×8 候选锚点域
     * 内按确定性子序再试 {@link #MIGRATION_MAX_ATTEMPTS} 个备选点；<b>全部锚点都不过才弃位</b>
     * （弃位后该 cell 无城，城市密度略降属预期）。迁移只换落点、不放松任一谓词。
     * <p>
     * <b>近似口径（拍板记录）</b>：只检查城盘 footprint 的干湿，街道/地块逐列仍走
     * {@code groundFn} 接地——城心盘已干则城内街道一般安全，无需逐列；残留风险：城缘个别列
     * 若贴河道可能仍在滩带，属可接受观感（plan §F）。
     * <p>
     * 代价提示：档 2/3 每城最多 15×15=225 次身份采样（每次本地重建一条短命链，常数代价的
     * 纯哈希族），且只在候选城非空时跑；默认档 1 每候选锚点 1 次。干区臂每锚点一次 footprint
     * 采样（周界/8 ≈ 百余列 × heightAt/lakeAt/strengthAt 三个纯函数，首列湿即早退）；P24-B 后
     * 每个弃位 cell 的代价上界 = (1 + {@link #MIGRATION_MAX_ATTEMPTS}) 倍锚点判定。城市数与
     * 暴露面积的门档选择见 plan/维度计划/调查取证/Phase1按片报告/p6-*（四张数字表）。
     */
    public static boolean cityGateAllows(long worldSeed, CityPlan plan) {
        return resolveCityPlan(worldSeed, plan) != null;
    }

    /**
     * 候选城<b>最终落点解析</b>（P24-B「候选迁移 / 弃位改挪位」）：{@code basePlan} 原锚点过门即
     * 原样返回；否则在<b>同一 cell</b> 的 8×8 候选锚点域（{@link #ANCHOR_OFFSET_MIN}..
     * {@link #ANCHOR_OFFSET_MAX}）内按确定性子序再试最多 {@link #MIGRATION_MAX_ATTEMPTS} 个
     * 备选点，返回第一个过门者；全部不过返回 {@code null}（弃位）。
     * <p>
     * <b>为什么是"迁移"而不是"放松"（P24-B 口径）</b>：P23 R1 全域站格湖后，湖盘半径
     * （水径中位 ≈192 格）与城盘（±128 格）同量级，候选城掷中湖心的概率结构性上升
     * （干区臂弃位率 25.0%→58.33%，见 {@code PlacementGate.DRY_RATIO_CITY} javadoc）。
     * 迁移只改<b>候选落点选择</b>（在同一批既有候选点里换一个更干的），把"弃位"变成"挪位"，
     * <b>不动任何列级谓词</b>——湖/河/潭/护带四腿与城心全过制逐字不变，水的避让强度不降。
     * <p>
     * <b>确定性/纯函数</b>：备选点序 = {@code (mix(cellSeed, MIGRATION_ORDER_SALT) + k·13) mod 64}
     * 的纯置换（起点随 cellSeed 变、步长固定，零额外随机源），只依赖 {@code cellSeed} 与常量；
     * 门判定复用 {@link #cityGateAllowsAt}（与 {@link #planFor} 同一 {@code cellSeed}，掷骰流不
     * 受扰动）。同 seed 同 cell 任意次解析逐位一致（CityDeterminismCheck 自证）。
     * <p>
     * <b>落点边界</b>：备选点仍在 cell 中部 8×8 内 ⇒ 相邻 cell 锚点最小间距仍是 17 chunk
     * （与改动前同界，见 {@link #ANCHOR_OFFSET_MIN}）；半径沿用 basePlan，城盘尺度不变。
     * <p>
     * <b>调用纪律</b>：生产侧唯一入口 {@link #citiesNear} 用本方法取<b>落点</b>再判缓冲窗——
     * 迁移后的城按新锚点渲染，不存在"门放行但渲染落在原湿点"的鬼窗；{@link #cityGateAllows}
     * 是本方法的布尔薄包装（"该 cell 有可放置落点"），保持既有调用面的语义。
     *
     * @param basePlan {@link #planFor} 的原始候选（{@code null} 直接返回 {@code null}）
     * @return 可放置的落点（原锚点或迁移后的备选锚点）；无落点 = {@code null}（弃位）
     */
    public static CityPlan resolveCityPlan(long worldSeed, CityPlan basePlan) {
        if (basePlan == null) {
            return null;
        }
        if (cityGateAllowsAt(worldSeed, basePlan)) {
            return basePlan;
        }
        final long cellSeed = basePlan.getCellSeed();
        final int cellX = Math.floorDiv(basePlan.getCenterChunkX(), CITY_CELL);
        final int cellZ = Math.floorDiv(basePlan.getCenterChunkZ(), CITY_CELL);
        final int baseOffX = basePlan.getCenterChunkX() - cellX * CITY_CELL;
        final int baseOffZ = basePlan.getCenterChunkZ() - cellZ * CITY_CELL;
        final int region = ANCHOR_REGION_SIDE * ANCHOR_REGION_SIDE;
        final int start = (int) (mix(cellSeed, MIGRATION_ORDER_SALT) % region);
        int attempts = 0;
        for (int k = 0; k < region && attempts < MIGRATION_MAX_ATTEMPTS; k++) {
            final int idx = (start + k * MIGRATION_SCAN_STRIDE) % region;
            final int offX = ANCHOR_OFFSET_MIN + idx % ANCHOR_REGION_SIDE;
            final int offZ = ANCHOR_OFFSET_MIN + idx / ANCHOR_REGION_SIDE;
            if (offX == baseOffX && offZ == baseOffZ) {
                continue; // 原锚点已在上一步判过（不过门），不重复计次
            }
            attempts++;
            final CityPlan alt = new CityPlan(
                worldSeed,
                cellSeed,
                cellX * CITY_CELL + offX,
                cellZ * CITY_CELL + offZ,
                basePlan.getRadiusChunks());
            if (cityGateAllowsAt(worldSeed, alt)) {
                return alt;
            }
        }
        return null;
    }

    /**
     * <b>单锚点</b>城门判定（{@link #resolveCityPlan} 的原子步）：群系臂 ∧ 干区臂。与
     * {@link #cityGateAllows} 的区别只在"不迁移"——本方法问的是"<b>这一点</b>是否可放"。
     */
    private static boolean cityGateAllowsAt(long worldSeed, CityPlan plan) {
        if (plan == null || !cityBiomeGateAllows(worldSeed, plan)) {
            return false;
        }
        return cityDryAllows(worldSeed, plan);
    }

    /** 城门的<b>群系臂</b>（{@link #cityGateAllows} 的既有实现体，语义与档位一字未改）。 */
    private static boolean cityBiomeGateAllows(long worldSeed, CityPlan plan) {
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
     * 城门的<b>干区臂</b>（P19 §F；U5-redirect 占比制修正后的城盘封装；P24-B 档值 0.85→0.75）：
     * 外扩城盘过<b>占比制</b>（{@link PlacementGate#DRY_RATIO_CITY}=0.75，外缘允许 25% 滩带），
     * 城心核心区（中心 64×64）另过<b>全过制</b> strict 臂（城市核心泡水不可接受）。
     */
    private static boolean cityDryAllows(long worldSeed, CityPlan plan) {
        final int half = CITY_DRY_HALF_CHUNKS * ChunkSpans.CHUNK_BLOCKS;
        final int cx = plan.getCenterX();
        final int cz = plan.getCenterZ();
        if (!PlacementGate
            .dryFootprint(worldSeed, cx - half, cz - half, cx + half, cz + half, PlacementGate.DRY_RATIO_CITY)) {
            return false;
        }
        final int coreHalf = CITY_CORE_HALF_BLOCKS;
        return PlacementGate.dryFootprintStrict(worldSeed, cx - coreHalf, cz - coreHalf, cx + coreHalf, cz + coreHalf);
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
