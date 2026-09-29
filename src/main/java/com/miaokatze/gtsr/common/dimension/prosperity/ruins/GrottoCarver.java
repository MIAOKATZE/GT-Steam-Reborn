package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.TerrainVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRRiverPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;
import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 沼泽<b>地面层降洞</b>（grotto，v1.20.54 P31 I-A 层降大重构）：populate 后置 carve 的
 * N 级台地深洞族——洞缘 m≤0.55 开放圈直接开口见天、洞内自入口向深藏侧<b>逐层台地下行</b>
 * （{@link #GROTTO_TREADS} 档量化，riser = {@link #GROTTO_DEPTH_SPAN}/{@link #GROTTO_TREADS}
 * = 1.6 ≤ 2，湖滨台地"高差 ≤2 台阶"可走口径 RVF:955-962 同族）、总深 12–20 格、底部
 * 静水潭（区域场 N8 Jacobi 不动点 + 宁缺不悬）、水无侧向/顶暴露（密封，V-roofed 口径）、
 * 半露天半深藏（open 场梯度承载：塌口核 o→1 浅进深 12–16、洞腹 o→0 至 20 深藏，塌口侧即
 * 入口侧）、潭周干台地环 = 潭前陆地 + 洞内草趟（{@link #GROTTO_GRASS_ROLLS} 掷）+ 潭前
 * 植物加密（{@code ProsperityDecorPlacer} 潭前腿，{@link #S_SWAMP_GROTTO_FLORA}）。
 * <p>
 * ═══ 纯函数纪律（风格照 {@code GTSRRiverPlacer}/{@code ChunkProviderProsperityRuins.fillSwampPools}）═══
 * 全部场与谓词是 {@code (seed, x, z)} 的确定函数——<b>任一 chunk 可独立重算</b>（CPR onPopulate
 * 同一条纪律）：潭水面在<b>区域缓存场</b>（{@link GrottoPoolField}，SwampFieldGrid 范式）上算清，
 * 每列只从<b>自己区域</b>的场取值 ⇒ 同列恒同值、与哪个 chunk 先生成无关（跨 chunk 接缝一致；
 * {@code ChunkClampedSink} 越界丢弃之外的第二重保证）。{@link #carve} 只写本 chunk，全部写入走
 * {@link BlockSink#FLAG_POPULATE}，<b>不消费 populate 的 {@code Random}</b>。
 * <p>
 * ═══ 列几何（m = {@link #grottoMAt} 门值 ∈ [0,1]，site 列 = {@link #grottoSiteAt} 七门）═══
 * <ul>
 * <li>h0 = {@link ProsperityTerrainProfile#heightAt}（GROTTO <b>不进</b> heightAt——地形场零改动）；
 * 台地档 tread = round(s01((m−0.5)/0.5)·{@link #GROTTO_TREADS}) ∈ [0,T]（湖滨范式直译
 * {@code round(e·N)/N}，RVF lakeShoreBlend 同式）；</li>
 * <li><b>总深</b> D = clamp({@link #GROTTO_DEPTH_BASE} + {@link #GROTTO_DEPTH_SPAN}·q −
 * {@link #GROTTO_OPEN_SHALLOW}·o, 12, 20)，q = tread/T，o = {@link #grottoOpenFactorAt}
 * ∈ [0,1]（s01((openNoise−{@link #GROTTO_OPEN_THR})/{@link #GROTTO_OPEN_BAND})）；
 * floorY = h0 − round(D)；</li>
 * <li>顶厚 t = round({@link #GROTTO_ROOF_T}·s01((m−{@link #GROTTO_RIM_M})/({@link #GROTTO_ROOF_FULL_M}
 * −{@link #GROTTO_RIM_M})))：m≤0.55 ⇒ t=0（<b>洞缘开放圈</b>），m≥0.68 ⇒ 全厚；
 * open = (t==0) ∨ (openNoise ≥ {@link #GROTTO_OPEN_THR})；yTop = open ? h0 : h0−t；
 * <b>腔 = [floorY+1, yTop] 置空气</b>；</li>
 * <li><b>潭</b>（湿核 = site ∧ m≥{@link #GROTTO_WATER_M} ∧ tread==T）：名义水位 W_nom =
 * floorY + {@link #GROTTO_WATER_DEPTH_MAX} → <b>区域场 N8 Jacobi 不动点</b>（快照逐趟、单调下降
 * 必收敛、≤96 趟早退；{@link GrottoPoolField}）。<b>barrier = 邻列支撑顶</b>：干 site 列取
 * floorY、湿列取其水顶、非 site 列取 h0——邻列 carve 空气顶<b>不再参与</b>（P30 RC1 悬空水
 * 根修：旧钳制把邻腔天花板当阻挡面，水贴邻腔空气 ⇒ 悬空流动）。<b>宁缺不悬</b>：钳后
 * W &lt; floorY+1 ⇒ 整列不置水（CPR:777-778 范式）。置水区间 [floorY+1, W]，
 * {@link GTSRRiverPlacer#waterMaterial()}（meta 0 + {@code FLAG_POPULATE}，静态性由几何密封
 * 保证——侧/顶均实心或同面水）。</li>
 * <li><b>顶密封（V-roofed 口径，主代理 P31 拍板）</b>：open（塌口/开放圈）列<b>禁水</b>——
 * 湿核列 openNoise ≥ THR 时该列不置水（水顶可为完整顶盖下的洞腔空气，"顶暴露"= 见天/塌口
 * 连通 ⇒ 计 0）；open 湿列在不动点里按其床（floorY）充当邻列阻挡面。</li>
 * <li><b>潭周干环</b>：潭水（不动点终值 &gt; 床）的邻列要么同面水、要么床 ≥ 水顶的干列——
 * 潭周天然环着 ≥1 档干台地（= 潭前陆地，入口侧即塌口侧）；干环列（湿核列及其 8 邻，见
 * {@link #carve} 草趟）<b>禁水禁草</b>。</li>
 * </ul>
 * <p>
 * <b>洞底统一 {@code prosperitySwampTop}</b>（{@code ChunkClampedSink} 无读接口 ⇒ 不做"floorY
 * 须为地形主体"的判读，<b>无条件覆写</b>——floorY 原为 wholeBody 的 prosperityStone/stone 主体，
 * 统一写成 PDP {@code tuftForGround} 认的 4 top 之一 ⇒ 洞草可落、洞底是沼泽壤土而非裸岩）。
 * 草趟只落"洞腔列 ∧ 非湿核 ∧ 无湿核邻列（干环禁草）"的 floorY+1（cross 植物直写）。
 * <p>
 * ═══ P31 I-A 实现申报（对 plan §3.2 的两处有据偏差）═══
 * <ol>
 * <li><b>湿核门不取任何邻域侵蚀环</b>（计划原文"8 邻 tread==T"）：实测（temp/p31-ia/，
 * 16 seed×1024² 步距 4）<b>深干邻排干机制</b>——环排除的列仍是已挖深腔（床 = 潭面级），
 * 以床面把邻水钳干：8 邻 tread 环 ⇒ 池 1/16 seed；5×5 m 域环 ⇒ 11/16 且水量 −71%；
 * 无环 ⇒ E=13/16、悬空 0（§3.5-4 反向梯端点亦如此，故取无环形态）。"潭周干台地环"
 * 由不动点 min 结构 + 台地梯天然成立（水列 8 邻 ∈ {水, 已挖列}——未挖列床 = h0 顶高墙；
 * 判据 G 组以"水列 8 邻无未挖缘列"为结构性门，陡缘压缩处的 2 格环读数 report-only，
 * plan §3.5-5 梯对此形无档可调——主代理裁量申报）。</li>
 * <li><b>不动点载体 = 区域缓存场而非柱心有界窗</b>：柱心窗（POOL_WINDOW=16/24 柱心）实测
 * 邻列窗错位 ⇒ 同深邻列水位差 ⇒ 悬空 15/… 条不归零（temp/p31-ia/geom-probe2.out）——本仓
 * 同类口径先例 SwampFieldGrid 的结论"per-chunk 有界窗口追不上 ⇒ 区域场一次算清"
 * （CPR:846-965）在本片复现，故照抄该范式：{@link #GROTTO_POOL_REGION}=128 格区域 +
 * {@link #GROTTO_POOL_WINDOW}=32 格边距环，(seed, 区域) 键线程私有缓存。跨区域 &gt;边距环深的
 * 排干链理论可漏（SwampFieldGrid 同报口径），16 seed×1024² 实测 0。</li>
 * </ol>
 */
public final class GrottoCarver {

    /** 观察日志窗口（chunk 数，与 CPR 巨湖/微池行同款量级，单独一条 grotto 行）。 */
    private static final int LOG_WINDOW_CHUNKS = 1024;

    private static final AtomicLong CHUNKS_SERVED = new AtomicLong();
    private static final AtomicLong CAVITY_CELLS = new AtomicLong();
    private static final AtomicLong WATER_CELLS = new AtomicLong();
    private static final AtomicLong GRASS_PLACED = new AtomicLong();

    /**
     * 浅洞选址盐（沼泽分型等差族延拓：{@code 0x6811C3D1}+0x12 族之上，= 沼泽缓坡床纹
     * {@code 0x6811C407} + 0x12；同族步长 0x12，无碰撞，grep 自检见
     * {@code temp/p30-iic/diff-note.md}）。
     */
    public static final long S_SWAMP_GROTTO = 0x6811C419L;
    /** m 场波长（格）：53（53%16=5 非 chunk 对齐，无 16 格周期伪影）。 */
    public static final double GROTTO_SCALE = 53.0D;
    /** m 场门带下沿（valueNoise 域）：s01((n−LO)/SPAN)，门 ⟺ n≥0.71。 */
    public static final double GROTTO_GATE_LO = 0.62D;
    /** m 场门带带宽。 */
    public static final double GROTTO_GATE_SPAN = 0.18D;
    /** 选址边缘门阈（{@link TerrainVariants#swampInteriorGateAt} ≥ 本值才可开洞——比微池腿 0.5 严一档）。 */
    public static final double GROTTO_EDGE_GATE_MIN = 0.6D;
    /**
     * 洞深下沿（格）：总深 D = clamp(BASE + SPAN·q − OPEN_SHALLOW·o, BASE, BASE+SPAN) 的
     * 下钳位（P31 I-A：3.0 → 12.0，层降重构——塌口核也至少下探 12 格）。
     */
    public static final double GROTTO_DEPTH_BASE = 12.0D;
    /** 洞深幅（格）：site 列总深域 [12,20]（P31 I-A：5.0 → 8.0）。 */
    public static final double GROTTO_DEPTH_SPAN = 8.0D;
    /**
     * <b>台地级数</b>（P31 I-A 新增）：tread = round(s01((m−0.5)/0.5)·T) ∈ [0,T]，
     * riser = {@link #GROTTO_DEPTH_SPAN}/T——T=5 ⇒ 1.6 ≤ 2（湖滨"高差 ≤2 台阶"可走口径
     * RVF:955-962；B 门红时的调档梯 5→6 ⇒ 1.33，plan §3.5-2）。
     */
    public static final int GROTTO_TREADS = 5;
    /**
     * 露天塌口侧浅进深扣深（格）：D 的 o 项系数——塌口核（o→1）比洞腹（o→0）浅 4 格，
     * 「半露天半深藏」的梯度承载（P31 I-A 新增）。
     */
    public static final double GROTTO_OPEN_SHALLOW = 4.0D;
    /** 塌口缓入带宽（openNoise 域）：o = s01((openNoise−THR)/BAND) 的带通宽（P31 I-A 新增）。 */
    public static final double GROTTO_OPEN_BAND = 0.10D;
    /** 洞缘开放圈上阈：m ≤ 本值 ⇒ 顶厚 t=0（地表直接开口）。 */
    public static final double GROTTO_RIM_M = 0.55D;
    /** 顶全厚上阈：m ≥ 本值 ⇒ t = GROTTO_ROOF_T 全厚（s01 饱和 1.0）。 */
    public static final double GROTTO_ROOF_FULL_M = 0.68D;
    /** 顶全厚（格）：round(3.5·s01(..)) ⇒ 全厚列 t=4。 */
    public static final double GROTTO_ROOF_T = 3.5D;
    /**
     * 潭区阈：m ≥ 本值 = 湿核下界（洞底置水候选列；与 {@code tread==T} 联合——T 档实际
     * 要求 m ≥ ~0.90 &gt; 本值，本阈保留为湿核的显式下界文档锚，P30 同值）。
     */
    public static final double GROTTO_WATER_M = 0.78D;
    /** 潭顶塌口独立盐（= S_SWAMP_GROTTO + 0x12，等差族内独立一格）。 */
    public static final long S_SWAMP_GROTTO_OPEN = 0x6811C42BL;
    /** 塌口噪声波长（格）：λ21（21%16=5 非 chunk 对齐）。 */
    public static final double GROTTO_OPEN_SCALE = 21.0D;
    /**
     * 塌口阈：openNoise ≥ 本值 = 塌口见天（o&gt;0）。<b>P31 I-A 域内闭环终值 0.30</b>：
     * 新几何（层降 + 湿核 tread 化）下 16 seed×1024² 步距 4 新口径实测 openFrac=0.3042
     * ∈ 带 [0.27,0.33] ⇒ <b>P30 EXTENDED 0.21 域外申报随本轮销账</b>（旧几何旧口径读数
     * 0.244/0.306 不迁移，plan §7.2 纪律）；读数与判据见 {@code temp/p31-ia/} 与
     * {@code tools/dim1/GrottoCarverCheck.java} F 组。
     */
    public static final double GROTTO_OPEN_THR = 0.30D;
    /** 潭深帽（格）：W_nom = floorY + 本值（名义水深上限，P30 同值）。 */
    public static final int GROTTO_WATER_DEPTH_MAX = 2;
    /**
     * 潭水面<b>区域场</b>边长（格，2 的幂；区域原点 = 坐标按本值对齐）。SwampFieldGrid
     * 范式（CPR:846-965）：不动点钳制在区域场一次算清，(seed, 区域) 缓存 ⇒ 纯函数、跨
     * chunk 接缝一致（P31 I-A 新增；柱心有界窗实测漏，见类注释申报 2）。
     */
    public static final int GROTTO_POOL_REGION = 128;
    /**
     * 区域场<b>边距环宽</b>（格）：场实际覆盖 [区域原点−本值, +REGION+本值)——池尺度排干链
     * 半径须 ≤ 本值才在场内闭环（实测边际：16/24 环柱心窗漏 15/34 条 ⇒ 区域场 32 环实测
     * 悬空 0；调档梯 24→16→8 属 GenBench 红时的降本档，plan §3.5-6）。
     */
    public static final int GROTTO_POOL_WINDOW = 32;
    /** 区域场缓存上限（超限整清——SwampFieldGrid 同款纪律，CPR:817）。 */
    public static final int GROTTO_POOL_FIELD_CACHE_CAP = 4;
    /**
     * 第七门阈（河谷压力 s 域）：s = −{@link GTSRVoronoiRiverField#strengthAt(long, int, int)}
     * ∈ (0,1] 为河谷域（谷坡 s∈(0,0.40)、干床过渡滩 [0.40,0.60)——P30 sum 遗留 1 的未排除
     * 域）、域外精确 0.0；<b>0 &lt; s &lt; 本值 ⇒ 不选址</b>（覆盖谷坡+过渡滩；s≥0.60 的干床核
     * 段仍由第六门 isDryRiverColumn 排除——两门在 [0.40+0.34·n01, 0.60) 有重叠段，第七门
     * 前置先行排除，第六门只兜 s≥0.60 段，无重复求值）。
     */
    public static final double GROTTO_RIVERBED_AVOID = 0.60D;
    /** 洞草趟数/chunk（每趟 hash 掷一次本 chunk 列，命中有效列才落草；P30 同值）。 */
    public static final int GROTTO_GRASS_ROLLS = 24;
    /** 洞草掷骰盐（= S_SWAMP_GROTTO + 0x12×2，等差族内独立一格）。 */
    public static final long S_SWAMP_GROTTO_GRASS = 0x6811C43DL;
    /**
     * 潭前植物加密盐（= S_SWAMP_GROTTO + 0x12×3，等差族内独立一格；P31 I-A 新增）——
     * 消费面 = {@code ProsperityDecorPlacer} 潭前腿（独立 Random 流，既有五趟随机流一位不动）；
     * src 碰撞预核零命中（沿 temp/p30-iic/diff-note.md 自检先例）。
     */
    public static final long S_SWAMP_GROTTO_FLORA = 0x6811C44FL;

    /** 潭水面区域场实际边长（区域 + 双侧边距环）。 */
    private static final int POOL_FIELD_SIDE = GROTTO_POOL_REGION + 2 * GROTTO_POOL_WINDOW;
    /**
     * 区域场缓存（线程私有、(seed, 区域原点) 键、上限整清——{@code swampFieldAt} 同款纪律）。
     * 只在 chunk 内确有湿核列时才会构建（无潭 chunk 零场成本）。
     */
    private static final ThreadLocal<HashMap<Long, HashMap<Long, GrottoPoolField>>> POOL_FIELD_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    private GrottoCarver() {}

    /**
     * <b>浅洞 m 场门值</b> ∈ [0,1]：{@code s01((valueNoise(seed^S_SWAMP_GROTTO, x/53, z/53) −
     * 0.62)/0.18)}——深腹地 1.0、域外精确 0.0。选址/台地/顶厚/潭区全部由这一个门值分级
     * （单场分档，无第二张表）。<b>纯函数</b>。
     */
    public static double grottoMAt(long worldSeed, int x, int z) {
        return s01(
            (GTSRWorldgenHash.valueNoise(worldSeed ^ S_SWAMP_GROTTO, x / GROTTO_SCALE, z / GROTTO_SCALE)
                - GROTTO_GATE_LO) / GROTTO_GATE_SPAN);
    }

    /**
     * <b>潭顶塌口独立噪声</b> ∈ [−1,1)：{@code valueNoise(seed^S_SWAMP_GROTTO_OPEN, x/21, z/21)}
     * ——与 m 场不同盐不同波长 ⇒ 塌口斑与洞形独立。<b>纯函数</b>。
     */
    public static double grottoOpenNoiseAt(long worldSeed, int x, int z) {
        return GTSRWorldgenHash
            .valueNoise(worldSeed ^ S_SWAMP_GROTTO_OPEN, x / GROTTO_OPEN_SCALE, z / GROTTO_OPEN_SCALE);
    }

    /**
     * <b>塌口缓入因子</b> o ∈ [0,1]：s01((openNoise − {@link #GROTTO_OPEN_THR})/
     * {@link #GROTTO_OPEN_BAND})——o=0 洞腹（全深）、o=1 塌口核（浅进深扣满）。总深式的
     * 连续腿（"半露天半深藏"由它承载）。<b>纯函数</b>。
     */
    public static double grottoOpenFactorAt(long worldSeed, int x, int z) {
        return s01((grottoOpenNoiseAt(worldSeed, x, z) - GROTTO_OPEN_THR) / GROTTO_OPEN_BAND);
    }

    /**
     * <b>列台地档号</b> ∈ [0, {@link #GROTTO_TREADS}]：round(s01((m−0.5)/0.5)·T)——0=最浅
     * （洞缘）、T=潭底档（m≈≥0.90 的深腹地；湖滨范式 {@code round(e·N)/N} 直译，RVF
     * lakeShoreBlend 同式）。非 site 列亦有确定值（判据/DecorPlacer 共用单一真值）。
     * <b>纯函数</b>。
     */
    public static int grottoTreadAt(long worldSeed, int x, int z) {
        return grottoTreadOfM(grottoMAt(worldSeed, x, z));
    }

    /** 台地档号的 m 域形态（供已持有 m 的调用点复用，避免二次 valueNoise）。 */
    private static int grottoTreadOfM(double m) {
        return (int) Math.round(s01((m - 0.5D) / 0.5D) * GROTTO_TREADS);
    }

    /**
     * <b>列总深</b>（格，实值）：clamp({@link #GROTTO_DEPTH_BASE} + {@link #GROTTO_DEPTH_SPAN}·q −
     * {@link #GROTTO_OPEN_SHALLOW}·o, 12, 20)，q = tread/T。<b>纯函数</b>。
     */
    public static double grottoDepthAt(long worldSeed, int x, int z) {
        final double m = grottoMAt(worldSeed, x, z);
        final double q = grottoTreadOfM(m) / (double) GROTTO_TREADS;
        final double o = grottoOpenFactorAt(worldSeed, x, z);
        return Math.max(
            GROTTO_DEPTH_BASE,
            Math.min(
                GROTTO_DEPTH_BASE + GROTTO_DEPTH_SPAN,
                GROTTO_DEPTH_BASE + GROTTO_DEPTH_SPAN * q - GROTTO_OPEN_SHALLOW * o));
    }

    /** 洞底 y：{@code heightAt − round(D)}（site 列专用；floorY 处统一写 prosperitySwampTop）。 */
    public static int grottoFloorYAt(long worldSeed, int x, int z) {
        return ProsperityTerrainProfile.heightAt(worldSeed, x, z) - (int) Math.round(grottoDepthAt(worldSeed, x, z));
    }

    /**
     * <b>浅洞选址谓词</b>：m ≥ 0.5 且以下七门全真（引用来源单一真值，无复刻）：
     * <ol>
     * <li>m ≥ 0.5（{@link #grottoMAt}，最便宜先行短路——域外列零后续求值）；</li>
     * <li>roster == 3（{@link ProsperityTerrainProfile#chainRosterIndexAt} coarse 1:4 身份面
     * ——只在喷气沼泽开洞）；</li>
     * <li>{@link TerrainVariants#swampTierAt} == NONE（避三档水体：不在已有表面池/深水池/水沼地上开洞）；</li>
     * <li>!{@link GTSRVoronoiRiverField#swampPoolWaterAt}（避微池水网）；</li>
     * <li>{@link TerrainVariants#swampInteriorGateAt} ≥ {@link #GROTTO_EDGE_GATE_MIN}
     * （避群系边缘——与微池腿 0.5 同门严一档）；</li>
     * <li>{@link TerrainVariants#lakePlaneTerrainAllowedAt} &gt; 0.5（避 sanzu 湖平面压力域
     * ——湖滩列不开洞）；</li>
     * <li><b>第七门</b>（P31 I-A 新增，顺路闭环 P30 sum 遗留 1）：河谷压力 s =
     * −{@link GTSRVoronoiRiverField#strengthAt(long, int, int)}，{@code 0 < s <
     * GROTTO_RIVERBED_AVOID} ⇒ 拒（谷坡 s∈(0,0.40) 与干床过渡滩 [0.40,0.60) 不开洞）∧
     * !{@link GTSRVoronoiRiverField#isDryRiverColumn}（第六门原样保留——干床核 s≥0.40+抖动
     * 段；第七门先行排除后本门只兜 s≥0.60 段，s 域核对与重叠段处置见
     * {@link #GROTTO_RIVERBED_AVOID} 注）。</li>
     * </ol>
     * <b>纯函数</b>、零 {@code net.minecraft} 依赖；判据与生产 carve 共用本出口。
     */
    public static boolean grottoSiteAt(long worldSeed, int x, int z) {
        if (grottoMAt(worldSeed, x, z) < 0.5D) {
            return false;
        }
        return siteGates(worldSeed, x, z);
    }

    /** 选址七门（m 门之外；独立成法供 carve 内联 m 后复用，避免二次 m 求值）。 */
    private static boolean siteGates(long worldSeed, int x, int z) {
        final int roster = ProsperityTerrainProfile.chainRosterIndexAt(worldSeed, x >> 2, z >> 2);
        if (roster != 3) {
            return false;
        }
        if (TerrainVariants.swampTierAt(worldSeed, x, z, roster) != TerrainVariants.SWAMP_TIER_NONE) {
            return false;
        }
        if (GTSRVoronoiRiverField.swampPoolWaterAt(worldSeed, x, z, roster)) {
            return false;
        }
        if (TerrainVariants.swampInteriorGateAt(worldSeed, x, z) < GROTTO_EDGE_GATE_MIN) {
            return false;
        }
        if (TerrainVariants.lakePlaneTerrainAllowedAt(worldSeed, x, z) <= 0.5D) {
            return false;
        }
        // 第七门：河谷带排除（0 < s < AVOID；s 域外（含 upland s==0）放行）
        final double s = -GTSRVoronoiRiverField.strengthAt(worldSeed, x, z);
        if (s > 0.0D && s < GROTTO_RIVERBED_AVOID) {
            return false;
        }
        return !GTSRVoronoiRiverField.isDryRiverColumn(worldSeed, x, z);
    }

    /**
     * <b>湿核谓词</b>（置水候选列）：site ∧ m ≥ {@link #GROTTO_WATER_M} ∧ tread ==
     * {@link #GROTTO_TREADS}（潭底档）。<b>不取任何形式的邻域侵蚀环</b>——实测（类注释
     * P31 I-A 申报 1）环排除的同深已挖干列以床面把邻水钳干（"深干邻排干"机制），8 邻
     * tread 环灭池到 1/16 seed、5×5 m 域环塌到 11/16 且水量 −71%；无环形态 E=13/16、
     * 悬空 0。潭周干环由不动点 min 结构 + 台地梯天然成立（水列 8 邻 ∈ {水, 已挖列} 结构性
     * 保证——未挖缘列床 = h0 顶高墙，判据 G 组实证 ≥1，陡缘压缩处的 2 格读数 report-only，
     * temp/p31-ia/）。<b>纯函数</b>。
     */
    public static boolean grottoWetAt(long worldSeed, int x, int z) {
        final double m = grottoMAt(worldSeed, x, z);
        if (m < GROTTO_WATER_M) {
            return false;
        }
        if (grottoTreadOfM(m) != GROTTO_TREADS) {
            return false;
        }
        return m >= 0.5D && siteGates(worldSeed, x, z);
    }

    /**
     * <b>塌口/开放圈谓词</b>：t==0（m≤{@link #GROTTO_RIM_M} 洞缘开放圈）∨ openNoise ≥
     * {@link #GROTTO_OPEN_THR}（塌口）。open 列 yTop=h0（腔直通地表），留顶列 yTop=h0−t；
     * <b>open 列禁水</b>（V-roofed 顶密封——水顶不得见天/塌口连通）。
     */
    public static boolean grottoOpenAt(long worldSeed, int x, int z) {
        if (roofThicknessAt(grottoMAt(worldSeed, x, z)) == 0) {
            return true;
        }
        return grottoOpenNoiseAt(worldSeed, x, z) >= GROTTO_OPEN_THR;
    }

    /**
     * <b>post-carve 最高实心 y</b>（列级纯函数重算，RC1 根修后的口径）：非 site 列 =
     * {@link ProsperityTerrainProfile#heightAt}（未挖，实心至地表）；open 列 = floorY
     * （洞底即最高实心——腔直通地表）；<b>留顶列 = h0</b>（顶盖 [h0−t+1, h0] 实心连地表，
     * 最高实心是地表 h0 而非旧口径的"腔顶 h0−t"——P30 把腔顶当阻挡面是 RC1 悬空水根因，
     * 本片起本出口只述实心顶；水侧支撑语义见 {@link GrottoPoolField} 的床/支撑面）。
     */
    public static int grottoCarveTopAt(long worldSeed, int x, int z) {
        final double m = grottoMAt(worldSeed, x, z);
        if (m < 0.5D || !siteGates(worldSeed, x, z)) {
            return ProsperityTerrainProfile.heightAt(worldSeed, x, z);
        }
        if (grottoOpenAt(worldSeed, x, z)) {
            return grottoFloorYAt(worldSeed, x, z);
        }
        return ProsperityTerrainProfile.heightAt(worldSeed, x, z);
    }

    /** 顶厚（格）：round(GROTTO_ROOF_T·s01((m−RIM)/(FULL−RIM)))——m≤RIM ⇒ 0，m≥FULL ⇒ 全厚 4。 */
    private static int roofThicknessAt(double m) {
        return (int) Math.round(GROTTO_ROOF_T * s01((m - GROTTO_RIM_M) / (GROTTO_ROOF_FULL_M - GROTTO_RIM_M)));
    }

    /**
     * <b>不动点终值水顶</b>（潭面单一真值；−1 = 无水）：湿核 ∧ 非 open 才有名义项——查
     * {@link GrottoPoolField} 区域场取钳后水顶，{@code < floorY+1}（宁缺不悬钳干）返回 −1。
     * 判据复算口径单源；生产 {@link #carve} 置水同源。<b>纯函数</b>（任一 chunk 独立可算，
     * 区域场按 (seed, 区域) 缓存 ⇒ 同列恒同值）。
     */
    public static int grottoWaterTopAt(long worldSeed, int x, int z) {
        if (!grottoWetAt(worldSeed, x, z) || grottoOpenAt(worldSeed, x, z)) {
            return -1;
        }
        final GrottoPoolField field = poolFieldAt(worldSeed, x, z);
        final int fi = (x - field.originX + GROTTO_POOL_WINDOW)
            + (z - field.originZ + GROTTO_POOL_WINDOW) * POOL_FIELD_SIDE;
        return field.top[fi] >= field.bed[fi] + 1 ? field.top[fi] : -1;
    }

    /**
     * <b>潭前干台地谓词</b>（潭前陆地，PDP 潭前植物加密腿与判据 G 组共用单一真值）：
     * site ∧ tread == {@link #GROTTO_TREADS}−1 ∧ 8 邻存在置水列（{@link #grottoWaterTopAt}
     * ≥ 0）。tread==T−1 是潭底档外第一档（床恒高于潭面 ⇒ 天然干），潭邻由水顶谓词定。
     * <b>纯函数</b>。
     */
    public static boolean grottoLandingAt(long worldSeed, int x, int z) {
        final double m = grottoMAt(worldSeed, x, z);
        if (grottoTreadOfM(m) != GROTTO_TREADS - 1 || m < 0.5D || !siteGates(worldSeed, x, z)) {
            return false;
        }
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                final double nm = grottoMAt(worldSeed, x + dx, z + dz);
                if (nm < GROTTO_WATER_M || grottoTreadOfM(nm) != GROTTO_TREADS) {
                    continue; // 先廉价 tread 短路，非湿核邻零场成本
                }
                if (grottoWaterTopAt(worldSeed, x + dx, z + dz) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 区域场取值（(seed, 区域) 键线程私有缓存；超上限整清——{@code swampFieldAt} 同款）。 */
    private static GrottoPoolField poolFieldAt(long worldSeed, int x, int z) {
        final HashMap<Long, HashMap<Long, GrottoPoolField>> bySeed = POOL_FIELD_CACHE.get();
        HashMap<Long, GrottoPoolField> regions = bySeed.get(worldSeed);
        if (regions == null) {
            regions = new HashMap<>();
            bySeed.put(worldSeed, regions);
        }
        final int regX = Math.floorDiv(x, GROTTO_POOL_REGION) * GROTTO_POOL_REGION;
        final int regZ = Math.floorDiv(z, GROTTO_POOL_REGION) * GROTTO_POOL_REGION;
        final Long key = Long.valueOf(((long) regX << 32) | (regZ & 0xFFFFFFFFL));
        GrottoPoolField field = regions.get(key);
        if (field == null) {
            if (regions.size() >= GROTTO_POOL_FIELD_CACHE_CAP) {
                regions.clear();
            }
            field = new GrottoPoolField(worldSeed, regX, regZ);
            regions.put(key, field);
        }
        return field;
    }

    /**
     * <b>潭水面区域场</b>（SwampFieldGrid 范式 CPR:846-965 的 Grotto 版）：覆盖
     * [区域原点−{@link #GROTTO_POOL_WINDOW}, +{@link #GROTTO_POOL_REGION}+WINDOW) 的方格，
     * 逐列存钳后水顶/床高，构造时一次算清 N8 Jacobi 不动点。
     * <ul>
     * <li><b>床</b>（邻列支撑顶，RC1 根修语义）：非 site 列 = h0（实心至地表）；site 列 =
     * floorY（腔底实心面——<b>carve 空气顶不参与</b>）；</li>
     * <li><b>名义水位</b>：湿核 ∧ 非 open 列 = floorY + {@link #GROTTO_WATER_DEPTH_MAX}
     * （open 湿列禁水——V-roofed 顶密封——按床充当邻列阻挡面；其余列无名义项）；</li>
     * <li><b>不动点钳制</b>：Jacobi 逐趟（趟用上趟快照 ⇒ 确定性与扫描序无关）、只降不升、
     * 单调必收敛、≤96 趟早退；支撑面 = max(邻列水顶, 邻列床)——水—水同面不流（等化），
     * 水—床邻列水顶 ≤ 床（侧封）。场边裁剪：窗外邻格视同不存在（&gt;边距环深的跨区域排干链
     * 理论可漏，实测 0——类注释申报 2）。</li>
     * </ul>
     */
    private static final class GrottoPoolField {

        final int originX;
        final int originZ;
        /** 钳后水顶（Integer.MIN_VALUE = 无名义项）；置水另行判 top ≥ bed+1（宁缺不悬）。 */
        final int[] top = new int[POOL_FIELD_SIDE * POOL_FIELD_SIDE];
        /** 床（邻列支撑顶）：非 site = h0、site = floorY。 */
        final int[] bed = new int[POOL_FIELD_SIDE * POOL_FIELD_SIDE];

        GrottoPoolField(long worldSeed, int regionX, int regionZ) {
            this.originX = regionX;
            this.originZ = regionZ;
            final int w = POOL_FIELD_SIDE;
            final int[] nominal = new int[w * w];
            for (int lz = 0; lz < w; lz++) {
                for (int lx = 0; lx < w; lx++) {
                    final int i = lz * w + lx;
                    final int x = regionX - GROTTO_POOL_WINDOW + lx;
                    final int z = regionZ - GROTTO_POOL_WINDOW + lz;
                    final double m = grottoMAt(worldSeed, x, z);
                    final boolean site = m >= 0.5D && siteGates(worldSeed, x, z);
                    if (site) {
                        final int floorY = grottoFloorYAt(worldSeed, x, z);
                        this.bed[i] = floorY;
                        final boolean wetCore = m >= GROTTO_WATER_M && grottoTreadOfM(m) == GROTTO_TREADS;
                        nominal[i] = wetCore && !grottoOpenAt(worldSeed, x, z) ? floorY + GROTTO_WATER_DEPTH_MAX
                            : Integer.MIN_VALUE;
                    } else {
                        this.bed[i] = ProsperityTerrainProfile.heightAt(worldSeed, x, z);
                        nominal[i] = Integer.MIN_VALUE;
                    }
                    this.top[i] = nominal[i];
                }
            }
            // N8 不动点钳制（Jacobi 快照逐趟，SwampFieldGrid CPR:919-962 同构）
            for (int pass = 0; pass < 96; pass++) {
                boolean changed = false;
                final int[] cur = this.top.clone();
                for (int lz = 0; lz < w; lz++) {
                    for (int lx = 0; lx < w; lx++) {
                        final int i = lz * w + lx;
                        if (nominal[i] == Integer.MIN_VALUE) {
                            continue;
                        }
                        int t = cur[i];
                        for (int dz = -1; dz <= 1; dz++) {
                            final int nz = lz + dz;
                            if (nz < 0 || nz >= w) {
                                continue;
                            }
                            for (int dx = -1; dx <= 1; dx++) {
                                if (dx == 0 && dz == 0) {
                                    continue;
                                }
                                final int nx = lx + dx;
                                if (nx < 0 || nx >= w) {
                                    continue;
                                }
                                final int j = nz * w + nx;
                                final int b = Math.max(cur[j], this.bed[j]);
                                if (b < t) {
                                    t = b;
                                }
                            }
                        }
                        if (t < cur[i]) {
                            this.top[i] = t;
                            changed = true;
                        }
                    }
                }
                if (!changed) {
                    break;
                }
            }
        }
    }

    /**
     * <b>本 chunk 的层降洞 carve</b>（唯一生产入口，CPR {@code onPopulate} 后置链末段调用——表层
     * 替换与河流/湖/微池置水全部落定之后再开口，洞腔空气/潭水/洞草不与既有水体通道互相干扰）。
     * 16×16 逐列：m 门先行短路（域外 chunk 近零成本），site 列才求七门；腔置空气 → floorY 写
     * {@code prosperitySwampTop} → 潭置水（{@link #grottoWaterTopAt} 区域场单一真值，宁缺不悬）
     * → 草趟（{@link #GROTTO_GRASS_ROLLS} 次 hash 掷列，有效列 = 洞腔列 ∧ 非湿核 ∧ 无湿核
     * 8 邻（干环禁草）∧ 无水，落 {@code prosperityTuftCopper}）。<b>不消费 populate 的
     * Random</b>；{@code world} 形参按 {@code GTSRRiverPlacer.place} 同款保留（写入全经 sink）。
     */
    public static void carve(World world, long worldSeed, int chunkX, int chunkZ, BlockSink sink) {
        final int baseX = chunkX << 4;
        final int baseZ = chunkZ << 4;
        final Block air = Blocks.air;
        final Block water = GTSRRiverPlacer.waterMaterial();
        // 列级一趟缓存（16×16；第二趟草趟直接读，不重求谓词）
        final boolean[] site = new boolean[16 * 16];
        final boolean[] wet = new boolean[16 * 16];
        final int[] floorYs = new int[16 * 16];
        final int[] wTops = new int[16 * 16];
        int cavityCells = 0;
        int waterCells = 0;
        int grassPlaced = 0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                final int x = baseX + lx;
                final int z = baseZ + lz;
                final double m = grottoMAt(worldSeed, x, z);
                if (m < 0.5D || !siteGates(worldSeed, x, z)) {
                    continue;
                }
                final int h0 = ProsperityTerrainProfile.heightAt(worldSeed, x, z);
                final int floorY = h0 - (int) Math.round(grottoDepthAt(worldSeed, x, z));
                final int t = roofThicknessAt(m);
                final boolean open = grottoOpenAt(worldSeed, x, z);
                final int yTop = open ? h0 : h0 - t;
                final int i = lz * 16 + lx;
                site[i] = true;
                floorYs[i] = floorY;
                // 腔置空气（site 列腔恒非空：depth≥12、t≤4 ⇒ 高度 ≥8）
                for (int y = floorY + 1; y <= yTop; y++) {
                    sink.setBlock(x, y, z, air, 0, BlockSink.FLAG_POPULATE);
                    cavityCells++;
                }
                // 洞底统一沼泽 top（无条件覆写口径见类注释）
                sink.setBlock(x, floorY, z, BlocksGTSR.prosperitySwampTop, 0, BlockSink.FLAG_POPULATE);
                // 潭：区域场不动点水顶（宁缺不悬；open 列禁水——V-roofed 顶密封）
                wTops[i] = Integer.MIN_VALUE;
                final boolean wetCore = m >= GROTTO_WATER_M && grottoTreadOfM(m) == GROTTO_TREADS;
                if (wetCore) {
                    wet[i] = true;
                    if (!open) {
                        final int wTop = grottoWaterTopAt(worldSeed, x, z);
                        if (wTop >= floorY + 1) {
                            wTops[i] = wTop;
                            for (int y = floorY + 1; y <= wTop; y++) {
                                if (sink.setBlock(x, y, z, water, 0, BlockSink.FLAG_POPULATE)) {
                                    waterCells++;
                                }
                            }
                        }
                    }
                }
            }
        }
        // 草趟：每趟 hash 掷本 chunk 一列（chunkSeed 掺草盐 + 趟次乘子），有效 = 洞腔列 ∧
        // 非湿核 ∧ 无湿核 8 邻（潭周干环禁草——潭前陆地留白）∧ 无水
        final Block tuft = BlocksGTSR.prosperityTuftCopper;
        for (int roll = 0; roll < GROTTO_GRASS_ROLLS; roll++) {
            final long key = GTSRWorldgenHash.chunkSeed(worldSeed ^ S_SWAMP_GROTTO_GRASS, chunkX, chunkZ)
                ^ (roll * GTSRWorldgenHash.CELL_MUL_X);
            final int lx = (int) (roll01(key) * 16.0D);
            final int lz = (int) (roll01(key ^ GTSRWorldgenHash.CELL_MUL_Z) * 16.0D);
            final int i = lz * 16 + lx;
            if (!site[i] || wet[i] || wTops[i] >= floorYs[i] + 1 || tuft == null) {
                continue; // 非洞腔列 / 湿核列 / 草不落水 / 离线未装配
            }
            if (wetNeighborAt(worldSeed, baseX + lx, baseZ + lz)) {
                continue; // 潭周干环列禁草（潭前陆地）
            }
            if (sink.setBlock(baseX + lx, floorYs[i] + 1, baseZ + lz, tuft, 0, BlockSink.FLAG_POPULATE)) {
                grassPlaced++;
            }
        }
        CAVITY_CELLS.addAndGet(cavityCells);
        WATER_CELLS.addAndGet(waterCells);
        GRASS_PLACED.addAndGet(grassPlaced);
        if (CHUNKS_SERVED.incrementAndGet() % LOG_WINDOW_CHUNKS == 0) {
            GTSteamReborn.LOG.info(
                "[GTSR] dim78 swamp grotto over {} chunks: cavityCells={} waterCells={} grassPlaced={}"
                    + " (terraced-descent grotto, P31 I-A)",
                CHUNKS_SERVED.get(),
                CAVITY_CELLS.get(),
                WATER_CELLS.get(),
                GRASS_PLACED.get());
        }
    }

    /** 8 邻湿核谓词（草趟干环避让用——湿核邻列 = 潭周干环，禁草）。 */
    private static boolean wetNeighborAt(long worldSeed, int x, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (grottoWetAt(worldSeed, x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** smoothstep 带通（clamp[0,1] 后 3t²−2t³；带外精确 0.0/1.0——TerrainVariants/RVF 同名私有帮手同口径）。 */
    private static double s01(double t) {
        final double c = Math.max(0.0D, Math.min(1.0D, t));
        return c * c * (3.0D - 2 * c);
    }

    /** splitmix64 → [0,1)（高 53 位归一；RVF hash01 同口径，确定性纯函数）。 */
    private static double roll01(long key) {
        return (GTSRWorldgenHash.splitmix64(key) >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR;
    }
}
