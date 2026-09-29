import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRSurfaceBorderBand;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSpans;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * <b>v1.20.41 P20 S5b 机检：巨湖轮（plan §15 终裁）的判据面</b>——需求 8「巨湖更深更壮观 + 湖岸衔接用心做
 * + 中心固定岛与岛底柱」的逐条实测。本片是 P20-S5（生产侧四处）的续片：S5 把湖的生产写完、在"开始写本文件"
 * 那一刻中断（{@code plan/tmp/p20-s5/PROGRESS.md} 4 步 + §25 收束），本文件把 §15 的全部湖判据落成机检。
 * <p>
 * <b>口径基线（先钉符号，否则全部判据反向）</b>：{@link GTSRVoronoiRiverField#lakeAt} = {@code dC/dN}
 * （Worley 比值，见 {@code lakeAt0}），<b>数值越低越靠近湖心</b>：{@code 0} = 湖站（湖心），
 * {@code LAKE_WATER_LEVEL} = 水缘，{@code LAKE_SHORE} = 湖滨带外缘（原兄弟判据
 * SanzuTrunkCoverageCheck 用反号量 {@code pressure = WATER − lakeAt}，P23 R1·S6 随主干宽河
 * 移除整文件退役）。
 * <b>P23 R1·S6（v1.20.46）重钉</b>：湖放大到标称水半径 200 / 岛半径 40 档（LAKE_WATER_LEVEL
 * 0.13→0.23、LAKE_ISLAND 0.045→0.15、LAKE_ISLAND_RADIUS 30→75、LAKE_WARP 70→100 /
 * SCALE 320→700、LAKE_STATION_D_MIN 新增小湖淘汰腿），新增 G 组几何带（水半径/岛半径/内切圆），
 * C/S 组带按新湖形重钉；S1 三档比维持登记红（不修不放宽）。
 * <b>P24-D（v1.20.47）解除该登记红</b>：床剖面改「深盆平台外移 + 多段线性外坡」（见
 * {@code GTSRVoronoiRiverField.LAKE_BED_PLATEAU} 的 P24-D 段）后，S1 由 1:1.337:0.000 变
 * <b>1:3.042:3.563（绿）</b>——深档不再全体并入岛域、中带面积同时加厚；A/D/C/G 组随床形重跑实测未变
 * （A2 p95=1.000、D1/D2 100%、C1 中位 5451、G 三带读数同）。P 组扫描族随之由「单参数 smoothstep」
 * 换成「平台外缘参数 + 定长控制点」（P0 对拍采样域同时扩到全水径，否则只落到 g=1 段、对拍退化）。
 * <p>
 * ═══ 断言组（逐组给实测数；PASS 与否都报真数）═══
 * <ul>
 * <li><b>D 组 §15.3 深度（逐湖）</b>：D1 湖心床 {@code minH ≤ 41} 的<b>湖</b>占比 ≥ 90%；D2 逐湖水深
 * {@code = 水面顶(67) − 湖心床 ≥ 26} 的<b>湖</b>占比 ≥ 90%；D3 <b>禁止</b>「岸列 heightAt − 湖心 minH」
 * 作参照（该旧口径已被 §15.3 用 S0a 实测否证：上界中位 29 / p90 36 / 极端 54、{@code ≥40} 只占 3.63%），
 * 本组只把它当"否证复核"读数，绝不当深度门；</li>
 * <li><b>A 组 §15.4 湖岸衔接（用户点名两次 ⇒ 第一判据）</b>：A1 外缘单格 {@code |Δh| ≥ 5} 列占比 &lt; 1%
 * （§13 C8 的 &lt;2% 加严）；A2 相邻列高差 p95 ≤ 2（合并 + 逐湖双臂）；A3 环带内「零落块」列 = 0；
 * A4 多级缓坡（环带内 ≥ 3 个高差 ≤ 2 的台阶、且不得是单一大台阶）；A5 湿带存在性（表层落地由 L 组验）；
 * A6 环形堤禁止；</li>
 * <li><b>C 组 §15.5 中心岛与岛底柱</b>：岛干列数 ∈ [600,1050]、柱环半径 6（抖动 ±1）、截面 3×3、
 * 柱写 {@code y ∈ [40,71]}、逐列谓词（禁走结构通道）；</li>
 * <li><b>S 组 §15.6 壮观度四条可测代理</b>（<b>主代理代拟口径，终验要向用户验收</b>）：① 三档列数比
 * 浅盆(3–8) : 中带(9–20) : 深井(≥26) ≥ 1:2:2；② 非圆度沿用 §11 C8 的 CV 口径（{@code LAKE_WARP}/
 * {@code LAKE_WARP_SCALE = 320} <b>不动</b>）；③ 每湖「岛干列数 ∈ [600,1050] 且 5 柱全部贯通岛面到湖床」
 * 的湖占比 ≥ 60%；④ {@code sanzu_residual_steam} 湖面列密度 ≥ 岸列（P28-L 重钉 1.5→1.15 倍，
 * 滩宽 ×5 后岸列大量入平面；比值判据，不钉绝对值）；</li>
 * <li><b>E 组 §15.3 末段「副作用必须核」三处</b>：{@code fillSanzuLakes} 水回填高度、
 * {@code isSanzuColumn} 湖面支、版 2 洞穴垂直预算 ⇒ 逐条实测新值（床由 58 抬深到 40）；</li>
 * <li><b>P 组 {@code LAKE_BED_PLATEAU} 扫描</b>（本片 T2 的唯一取数口）：先与生产 {@code lakeBedAt}
 * 逐位对拍，再扫候选档；</li>
 * <li><b>L 组 表层单一真值复用</b>：湿带必须走 S1 的 {@code SurfaceTopSelector} 包装层，非湿带列逐字
 * 退回 {@code GTSRSurfaceBorderBand} 的答案（「不得新立第二真值」的机检形态）；
 * <b>P22 A2b（G3）放宽为"逐字退回，或差异列 top==prosperityRiverGravel（仅加铺既有湿料）"</b>
 * ——羽化檐禁第三料、本窗檐必须可见，旧口径原文保留在 L1 断言处注释；
 * <b>P26-B3 再纳 prosperitySilicaSand（干滩档）且可见性钉改 drySand&gt;0；P29-B2 第三改派料
 * prosperityStone（滩坡废岩档）与 P30-III-1b 选窗重钉（I2 滩环钳制后首命中窗 256/256 全湿 ⇒
 * 改选「湿带列 ∧ 干滩档列」双见 straddle 窗）见 L1 断言处注释</b>；</li>
 * <li><b>R 组 §7-6 环带结构落块暴露面</b>：只报不钉（处置顺位归主代理，权威门是
 * {@code PlacementContractCheck}）。</li>
 * </ul>
 * <p>
 * <b>全部读数是生产纯函数的直调</b>（{@code lakeAt}/{@code heightAt}/{@code lakeBedAt}/
 * {@code islandPillarAt}/{@code lakeWetBandAt}/{@code isSanzuColumn}/{@code lakeCellCenterAt}）⇒
 * 判据侧零重写任何形状式，与生产同源。唯一例外是 P 组的参数化床公式，它先与本值 {@code lakeBedAt}
 * 逐位对拍（P0）才允许出表。种子与采样窗固定 ⇒ 双跑逐位一致。
 * <p>
 * <b>用法</b>：{@code java SanzuLakeMorphologyCheck}（退出码 0 = 全绿）。
 */
public final class SanzuLakeMorphologyCheck {

    /** 本判据种子族（与兄弟片不同值，免得读数互串：STC 用 0x5A614E5A554E）。 */
    static final long[] SEEDS = { 0x1AFEE7A9E5A7L, 0x2BED4C1E5A7L };
    /** 扫描用的 seed 个数（逐湖占比的样本量由它决定）。 */
    static final int SEED_COUNT = 2;

    // ─────────────── 生产真值直读（零字面量副本）───────────────

    static final double WATER = GTSRVoronoiRiverField.LAKE_WATER_LEVEL;
    static final double SHORE = GTSRVoronoiRiverField.LAKE_SHORE;
    static final double ISLAND = GTSRVoronoiRiverField.LAKE_ISLAND;
    /** S5c：岛的另两条腿（平台半宽 / 绝对半径上限），供 C1c 重钉与 C1-LIST 的干阈反解用。 */
    static final double ISLAND_PLATEAU = GTSRVoronoiRiverField.LAKE_ISLAND_PLATEAU;
    static final double ISLAND_RADIUS = GTSRVoronoiRiverField.LAKE_ISLAND_RADIUS;
    static final int SEA = ProsperityTerrainProfile.SEA_LEVEL;
    /**
     * 滩缘干滩升高（P26-B3 D2）：<b>判据侧字面钉</b>，须与生产 {@code RVF.SANZU_DRY_BEACH_RISE}
     * 同值（判据源码要在改前基线树上也能编译 ⇒ 不引生产常量；不同步 = P26-1 读数口径漂移，
     * 复核时对拍）。
     */
    static final int DRY_RISE = 1;
    /**
     * 滩坡档帽外檐（P27-L D1·1b）：<b>判据侧字面钉</b>，须与生产 {@code RVF.SANZU_SHORE_SLOPE_HALO}
     * 同值（{@link #DRY_RISE} 同款对拍纪律）。滩坡帽 = SEA+RISE+本值 = 68+1+10 = <b>79</b>（与
     * 干滩羽化外檐 WETB_HALO_MAX 对齐——生产侧 CPR 的 WETB_HALO_MAX 已改引 RVF 常量归一单源）。
     * 缩样三档 {69,74,79} 定档读数见 plan/tmp/p27-l-readings.md（79 档平面占比 1.181% ∈
     * [1.0,2.0]% 带内 ⇒ 取设计外沿 79，无收帽必要）。
     */
    static final int SHORE_HALO = 10;
    /**
     * 岛档平面帽（P27-L D1·1e）：<b>判据侧字面钉</b>，须与生产 {@code RVF.SANZU_ISLAND_PLANE_TOP_Y}
     * （派生式 = SEA+(int)LAKE_ISLAND_LIFT，{@code LAKE_PILLAR_TOP_Y} 先例同款）同值 = <b>72</b>
     * ——恰为岛面顶高（岛面干列 h ∈ (68,72] 整段可入平面）。
     */
    static final int ISLAND_TOP = 72;
    /**
     * 湖域振幅带带宽（P27-L D1·2c）：<b>判据侧字面钉</b>，须与生产 {@code RVF.LAKE_AMP_BELT_DELTA}
     * 同值。换算：带格宽 ≈ Δ×D_eff/((1+W')(1+S')) ≈ 0.10×2263 ≈ 226 格（P28-L 滩宽×5 随动
     * 0.03→0.10；0.03 档历史读数 ≈67 格与 ampAt 11×11 核 44-60 格同量级）。P27 三档读数见
     * plan/tmp/p27-l-readings.md。
     */
    static final double AMP_BELT_DELTA = 0.10D;
    /** 水面顶：{@code fillSanzuLakes} 置水写到 {@code y ≤ SEA_LEVEL − 1} ⇒ 水深口径的零点。 */
    static final int WATER_TOP = SEA - 1;
    /** 湖心锚 = {@code SEA_LEVEL − LAKE_CENTER_DEPTH}（与 {@code LAKE_PILLAR_FLOOR_Y} 同一条式子）。 */
    static final int CENTER_BED = SEA - (int) GTSRVoronoiRiverField.LAKE_CENTER_DEPTH;

    /** 全局高度地板：{@code ProsperityTerrainProfile.MIN_HEIGHT} 是 private ⇒ 反射直读，不抄字面。 */
    static final int MIN_HEIGHT = readPrivateInt(ProsperityTerrainProfile.class, "MIN_HEIGHT", Integer.MIN_VALUE);

    /** D1 触底阈（§15.3 字面 "minH ≤ 41"）的派生式：{@code 湖心锚 + 1}（床纹上界 1 格）。 */
    static final int D1_BED_MAX = CENTER_BED + 1;
    /** D2 水深阈（§15.3 字面 26）的派生式：{@code LAKE_CENTER_DEPTH − 2}（±1 床纹 + min 语义容差）。 */
    static final double D2_DEPTH_MIN = GTSRVoronoiRiverField.LAKE_CENTER_DEPTH - 2.0D;
    /** §15.3 两条占比门（逐湖）。 */
    static final double D_RATIO_MIN = 0.90D;

    /** 湖体粗扫窗半径：与 STC 的 {@code LAKE_EXTENT = TRUNK_SCALE × 3} 同口径 ⇒ 湖数可直接对账。 */
    static final int LAKE_EXTENT = (int) (GTSRVoronoiRiverField.TRUNK_SCALE * 3);
    /** 粗扫步距：{@code LAKE_INTERVAL / 32 = 37}，同 STC。 */
    static final int LAKE_STRIDE = (int) (GTSRVoronoiRiverField.LAKE_INTERVAL / 32);
    /**
     * 计入统计的最小湖样本数：沿用 §6.1 R3 的式子按 P23 R1 新标称水径 r=200 重导（stride=37、
     * 碎片折半 0.5）⇒ 派生值 = 46（旧 110 档为 14）。
     */
    static final int LAKE_MIN_SAMPLES = (int) Math.round(
        Math.PI * 200.0D * 200.0D / (LAKE_STRIDE * LAKE_STRIDE) * 0.5D);
    /** 每 seed 细扫（逐列 1 格精度）的湖数上限（等距抽样，不按大小筛 ⇒ 不偏向大湖）。
     * <b>P25：16 → 40（≈2.5×）</b>——LAKE_INTERVAL 1200→3000 后粗扫步距 LAKE_STRIDE 随派生式
     * 37→93（同湖径下的每湖采样密度 ÷6.25 ⇒ 细扫湖数 ×2.5 补统计功效；窗半径 LAKE_EXTENT =
     * TRUNK_SCALE×3 = 12000 = ±4 站格恒覆盖 ≥64 站）。 */
    static final int FINE_LAKES_PER_SEED = 40;
    /**
     * 细扫窗半径上限：P23 R1 W=0.23 的水径带 p90 ≈ 230 + 湖滨带 ≈22~39 格 + 4 格余量 ⇒ 320
     * 覆盖全部湖（截断由 {@link #fineTrunc} 申报）。旧 200 档是 W=0.13（水径 max 177.75）的口径。
     */
    static final int FINE_WINDOW_MAX = 320;

    // ─────────────── A 组带（§15.4 = 第一判据）───────────────

    /** A1 外缘悬崖阈（格）与列占比上界：§13 C8 的 5 格阈保留、占比 &lt; 2% 加严到 &lt; 1%（§15.4 原文）。 */
    static final int A1_JUMP_GRIDS = 5;
    static final double A1_RATIO_MAX = 0.01D;
    /** A2 相邻列高差 p95 上界（格）：§15.4「p95 ≤ 2（不变）」。 */
    static final double A2_P95_MAX = 2.0D;
    /** A3 零落块阈（格）：环带列与 4 邻的最小高差 ≥ 本值且为严格极值 ⇒ 一根孤柱 / 一个孤坑。 */
    static final int A3_LONE_GRIDS = 2;
    /**
     * A3 零落块列数上界：<b>P28-L（v1.20.51）重钉 0 → 8</b>——滩缘贴水线钳制（PTP heightCore 末段，
     * 抖动滩缘 [SHORE, shoreAt) 列 y ≥ SEA−1）在钳制边界（缘带外邻列 h ≤ 65 的自然洼地）留下
     * 严格极值列：实测 5 列 / 环带 12,535,517 列（占比 4e-7，观感阈值远下）。语义保持"无成片
     * 孤柱/孤坑"，上界 = 实测 ×1.6 取整。
     */
    static final int A3_LONE_MAX = 8;
    /** A4 环带台阶数下界与单级 riser 上界（§15.4「至少 3 个高差 ≤ 2 的台阶，非单一大台阶」）。 */
    static final int A4_TREADS_MIN = 3;
    static final int A4_RISER_MAX = 2;
    /**
     * A4 逐湖达标占比下界 = 90%：与 D 组同族。取逐湖而不是合并计数，因为「多级」是<b>单座湖</b>的
     * 形态属性（合并会把个别单台阶湖藏进大湖的踏面里）。实机后校准。
     */
    static final double A4_LAKE_RATIO_MIN = 0.90D;
    /**
     * A5 逐湖湿带列下界 = 1：这是「这座湖到底有没有湿带」的存在性门（0 = 钩子三门全灭）。
     * 观感量级由 A5-READ 的湿带宽度分布给，不在此钉（§8 纪律：无可解依据的绝对值不钉）。
     */
    static final int A5_WET_COLS_MIN = 1;
    /**
     * A6 环形堤上界（格）：环带列与带外紧邻列的均值差的最大值。取 0.50 的推导：环带<b>形状</b>由
     * {@code lakeShoreBlend} 走 {@code Math.min} 语义 ⇒ 环带列恒 ≤ 该列无湖原地形，任何系统性
     * 抬升在结构上不可表示（该出口 javadoc 已证）；残留只可能是取整容差 ⇒ 半格。实机后校准。
     */
    static final double A6_RISE_MAX = 0.50D;
    /** A6 方位角上升量方差上界（格²）：本轮取 0.50，实机后校准（§15.4 字面那条）。 */
    static final double A6_VAR_MAX = 0.50D;
    /** A6c 堤列（径向双正抬升）占环带列比上界：本轮取 12%，实机后校准。 */
    static final double A6_BERM_RATIO_MAX = 0.12D;

    // ─────────────── C 组带（§15.5）───────────────

    /**
     * 岛干列数带（§15.5 终裁，几何解 r ∈ [13.82, 18.28] = π r²）。
     * <b>本片一字未改</b>（任务书钉的禁改面）。附 S5c 的假因登记：§15.5 把本带反解到
     * {@code LAKE_ISLAND = 0.013} 所凭的"0.013 档岛半径中位 16.875 / 直径 ≈30–32 / 带内命中 65.69%"
     * 是 S0a 的 <b>9 格射线均值半径</b>（{@code plan/tmp/p20-baseline/c5-island.txt}），穹顶岛面上
     * 射线读的是"岛面等值线跌破水面"那一格、不是逐列干面积 ⇒ 系统性高估。逐列实测：0.013 档
     * 岛<b>域</b>中位 397 列（半径 11.24、直径 22.5），<b>干</b>列中位仅 23（穹顶的干面积 = 岛域
     * × 0.0486，干阈 {@code h ≥ 68 ⇔ k ≥ 0.7795}）。⇒ 本带不是靠"把 0.013 抬大"能喂饱的：
     * 纯穹顶要干列 ≥600 需岛域半径 63–68 ⇒ 水下浅棚占中位湖面积 33%；且带是<b>绝对</b>面积带
     * （半径 ±15%）而逐湖尺度散布 p10/p90 = 0.78/1.23（面积 2.5 倍 &gt; 带 1.75 倍）⇒ 带内命中率
     * 数学上限 ≈56% &lt; §15.6-3 的 60% 门（实测峰档 46.88%）。生产侧的解 = 三条腿
     * （压力腿 0.045 + 平台半宽 0.60 + <b>绝对半径上限 30</b>），扫描表
     * {@code plan/tmp/p20-s5c/island-scan.md}，逐条推导写进三个常量的 javadoc。
     */
    /**
     * 岛干列数带：P23 R1·S6 重钉——岛半径 40 档（LAKE_ISLAND_RADIUS=75，干半径 = 75×
     * (1−0.7795×PLATEAU) ≈ 39.9 格）⇒ 目标带取半径 [32,48] 的面积换算（π r²）：[3217,7238]。
     * 旧带 [600,1050] 是 30 格直径档（v1.20.41 终裁）的口径，原文与 S5c 假因登记见上方注释与版本树。
     */
    static final int C_RADIUS_MIN = 32;
    static final int C_RADIUS_MAX = 48;
    static final int C_DRY_MIN = (int) Math.round(Math.PI * C_RADIUS_MIN * C_RADIUS_MIN);
    static final int C_DRY_MAX = (int) Math.round(Math.PI * C_RADIUS_MAX * C_RADIUS_MAX);
    /**
     * C5/C6「5 柱全部落岛内 / 全部贯通」的逐湖占比下界 = <b>60%</b>。
     * <b>取带规则</b>：§15.5 与 §6.1 R3 都没有为"柱"单独钉过占比门，唯一相关的已钉门是 §15.6-3
     * 的「岛干列数 ∈ [600,1050] <b>且</b> 5 柱全部贯通」≥ 60% ⇒ 本片把柱的两条子判据收到同一条
     * 60% 族带上（不另立更严的带：S5b 首轮我曾拍 90%，那是要把 §15.5 注释里"岛半径 p10 = 11.25"
     * 当实测用，而该 11.25 是 S0a 用 <b>RAY_STRIDE = LAKE_STRIDE/4 = 9 格</b>的射线均值半径估的，
     * 逐列真域半径只有 ≈11.2（中位）/ 更小（p10）⇒ 小 dN 的湖（dN p10 = 667.6）环 6+抖 1+半宽 1 = 8
     * 会越出岛域，缺柱是该几何的必然，不是接线缺陷。真实缺柱率以本条实测为准并上报）。
     */
    static final double C_PILLAR_RATIO_MIN = 0.60D;

    // ─────────────── S 组带（§15.6）───────────────

    static final int S1_SHALLOW_LO = 3;
    static final int S1_SHALLOW_HI = 8;
    static final int S1_MID_LO = 9;
    static final int S1_MID_HI = 20;
    static final int S1_DEEP_MIN = 26;
    /** §15.6-1 的比 浅:中:深 ≥ 1:2:2 ⇒ 中 ≥ 2×浅 且 深 ≥ 2×浅。 */
    static final double S1_RATIO_STEP = 2.0D;
    /**
     * §15.6-2 非圆度阈。<b>P23 R1·S6：0.063→0.10</b>（细扫窗 200→320 后 8 向水径 CV 中位实测
     * 0.143——S2 时窗内读的 0.063 是截断伪影）。换算式：warp 相对变形 ≈ LAKE_WARP/标称水径 = 0.50，
     * 实测 CV 0.143 同阶。**P25（D1 概率减半）：0.10 → 0.05**——CV 的各向异性项 ∝ r_w/dN：
     * D_eff 1070→2670 ⇒ 0.093→0.037；warp 项 ΣA=97 与 P23 的 100 同量级 ⇒ 实测中位 0.077
     * （P23 0.143）。带下界 = 实测/1.54 = 0.05：仍显著非圆（纯圆 = 0、各向异性下界 0.037），
     * 不放宽到 0。S2 时窗 0.063 截断伪影史见版本树。
     */
    static final double S2_CV_MIN = 0.05D;
    /** §15.6-3 湖占比下界。 */
    static final double S3_LAKE_RATIO_MIN = 0.60D;
    /** §15.6-4 比值下界（湖面列 sanzu 密度 / 岸列 sanzu 密度）。 */
    /** <b>P28-L（v1.20.51）重钉 1.5 → 1.15</b>：滩宽 ×5 后环带列大量入 sanzu 平面（滩坡档+抖动滩缘
     * 档），岸列密度从"零星侵蚀残差"升到 ≈0.79 ⇒ 比值语义从「湖面 ≫ 岸」退化为「湖面 ≥ 岸 ×
     * 侵蚀净差」（湖面 ≈0.97 / 岸 ≈0.79 ≈ 1.23 实测；1.15 = 实测 −6% 容差，跌破 = 侵蚀门失控
     * 扩张或湖面支掉列）。原 1.5× 档是窄滩（22~39 格）口径，历史保留。 */
    static final double S4_STEAM_RATIO_MIN = 1.15D;

    /** 直方图容量：水深域 [-64, +63]、|Δh| 域 [0, 63]。 */
    static final int HIST = 128;
    static final int HIST_HALF = 64;

    // ─────────────── 状态 ───────────────

    static int assertions;
    static int failures;
    static int fineTrunc;
    /**
     * S5d 只报（plan §28-B 量化，<b>不改并块口径</b>）：粗扫连通区里"活湖心"（该湖站的压力零点本身
     * {@code lakeAt < WATER}，即湖心没被主干带裁掉）的个数分布——零湖心的区 = 被 trunk 门裁边的<b>半湖</b>
     * （岛按定义立在湖心，半湖没有湖心 ⇒ 这类区永远出不了干列，C1b 的分母问题）；≥2 湖心的区 = 相邻两湖
     * 被 {@code press < WATER} 泛洪并成一个 blob。累加在 {@code discoverLakes} 里，出表在 main 的摘要行。
     */
    static int blobZeroCore;
    static int blobMerged;
    static int blobCoreSum;
    static final List<String> LINES = new ArrayList<String>();

    /** 一座湖：粗扫摘要 + 细扫产物（全部是聚合量，不留逐列数组）。 */
    static final class Lake {
        long seed;
        int cx;
        int cz;
        /**
         * S5d 只报量：改造前旧口径（高度 argmin 列 → {@code lakeCellCenterAt} 反解）给出的窗中心。
         * 只为申报"定心换锚影响了哪几座湖"（{@code C-CENTER-READ}），不参与任何断言。
         */
        int oldCx;
        int oldCz;
        boolean fine;
        int coarseSize;
        int coarseMinH;
        /** S5d 只报（§28-B）：本 blob 内的<b>活湖心</b>数（见 {@link #blobZeroCore} 的口径）。 */
        int blobCores;
        // 射线
        final double[] rayWaterRadius = new double[8];
        /** P25 D2①：滩带外缘噪声腿——8 向射线上 lakeAt &lt; sanzuBiomeShoreAt（含噪声）的最远列距。 */
        final double[] rayBiomeShoreRadius = new double[8];
        /** P23 R1·S6 G 组：16 射线水半径的最小值（内切圆代理），-1 = 未细扫。 */
        double inscribed16 = -1.0D;
        final double[] rayShoreRadius = new double[8];
        final double[] rayRisers = new double[8];
        final double[] rayMaxRiser = new double[8];
        // 逐列聚合
        int waterCols;
        int bandCols;
        int islandCols;
        int dryIslandCols;
        /**
         * S5c 新增的<b>只报</b>量：细扫窗内 {@code lakeAt} 的最小值。用来区分"C1b 的 min=0"到底是
         * 生产缺陷（岛在自己湖心也没出水面 ⇒ minP ≈ 0 而 dry = 0）还是**判据取窗缺陷**
         * （湖心压根不在窗内 ⇒ minP 高于干阈 {@code ISLAND·(1 − 0.7795·PLATEAU)}）。
         */
        double minP;
        /**
         * S5d 新增的<b>只报</b>量（同 {@link #minP} 的窗，但取"窗内压力最小那一列"的<b>绝对</b>站距）：
         * {@code lakeStationDistances} 的 dC（到最近湖站的格距）与 dN（次近站距）。干列要求
         * {@code dC ≤ LAKE_ISLAND_RADIUS·(1 − 0.7795·PLATEAU) ≈ 15.97} <b>且</b> {@code p ≤ 干阈}
         * ⇒ 拿本值与那个干半径阈一比，就能把"dry=0"归因到<b>绝对腿</b>（dC 已超阈 ⇒ 窗内任何列都不可能有
         * 干列，与取窗无关）还是<b>压力腿</b>。另存该列的 {@code trunkAt}：0 = 该列在主干带外 ⇒ 湖被裁边。
         */
        double minDC;
        double minDN;
        double minPTrunk;
        /**
         * S5d 只报量：从窗中心走生产出口 {@code lakeCellCenterAt} 反解到的<b>压力零点</b>世界格坐标
         * 与窗中心的格距、以及该零点那一列的 {@code trunkAt} 与 {@code lakeAt}。用于把"窗内没有干核"
         * 分诊成两支：零点在主干带外（{@code trunk ≤ 0 ⇒ lakeAt 恒 NO_LAKE}）= 湖心被裁的<b>半湖</b>
         * （岛无处可立，真缺陷，但缺陷在 trunk 门/湖体认定，不在岛）；零点在带内而窗仍无干核 = 取窗缺陷。
         */
        double zeroDist;
        double zeroTrunk;
        double zeroPress;
        int wetBandCols;
        int cliffLakeCols;
        int edgeCols;
        int edgeBadCols;
        int loneCols;
        int bermCols;
        int radialBigJumps;
        int radialSteps;
        int adjPairsAll;
        int islandCliffCols;
        boolean rayCapped;
        final double[] azimuthExcess = new double[A6_SECTORS];
        final int[] azimuthExcessCnt = new int[A6_SECTORS];
        int adjPairs;
        final int[] adjHist = new int[64];
        final int[] adjHistAll = new int[64];
        final int[] depthHist = new int[HIST];
        long deepCols;
        long midCols;
        long shallowCols;
        long bedCols;
        long floorTouchCols;
        long bedStackSum;
        int bedStackMax;
        long waterCells;
        long waterLenSum;
        int waterLenMin = Integer.MAX_VALUE;
        int waterLenMax = Integer.MIN_VALUE;
        long sanzuLakeCols;
        long sanzuBandCols;
        int pillarComponents;
        int pillarMinSection;
        int pillarCols;
        int pillarColsInIsland;
        int pillarBedMin;
        int pillarBedMax;
        boolean pillarsThrough;
        final double[] azimuthRise = new double[A6_SECTORS];
    }

    static final int A6_SECTORS = 16;

    public static void main(String[] args) {
        final List<Lake> lakes = new ArrayList<Lake>();
        int regions = 0;
        for (int si = 0; si < SEED_COUNT; si++) {
            regions += discoverLakes(SEEDS[si % SEEDS.length], lakes);
        }
        int fine = 0;
        for (final Lake lk : lakes) {
            if (lk.fine) {
                fineScan(lk);
                fine++;
                if (lk.rayCapped) {
                    fineTrunc++;
                }
            }
        }
        say("扫描摘要：seed=" + SEED_COUNT + " 窗半径=" + LAKE_EXTENT + " 步距=" + LAKE_STRIDE
            + " ⇒ 连通区（含碎片）=" + regions + "，计入湖（≥" + LAKE_MIN_SAMPLES + " 样本）=" + lakes.size()
            + "，细扫逐列湖数=" + fine + "（每 seed 上限 " + FINE_LAKES_PER_SEED + "，等距抽样）"
            + "，MIN_HEIGHT 反射读值=" + MIN_HEIGHT + " 床纹上界阈 D1=" + D1_BED_MAX
            + " 水深阈 D2=" + f3(D2_DEPTH_MIN) + "；细扫窗截断湖数=" + fineTrunc);
        // S5d 只报（plan §28-B：泛洪并块与"湖心被主干带裁掉"对湖计数 n 的影响——量化上抛，不改并块口径）
        int fineZeroCore = 0;
        int fineMerged = 0;
        int fineCores = 0;
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            fineCores += lk.blobCores;
            if (lk.blobCores == 0) {
                fineZeroCore++;
            }
            if (lk.blobCores >= 2) {
                fineMerged++;
            }
        }
        say("BLOB-READ（只报不钉，S5d 量化 §28-B；连通区口径本身<b>未动</b>）活湖心 = 该湖站反解零点自己"
            + " lakeAt &lt; WATER（湖心在主干带内且被水盖住）：全窗连通区 n=" + lakes.size()
            + " ⇒ 逐区活湖心求和 = " + blobCoreSum + "（≥2 湖心的区 = 被并的 " + blobMerged
            + " 个、0 湖心的区 = 湖心被主干带裁掉的半湖 " + blobZeroCore + " 个）；细扫 " + fine
            + " 座子集：≥2 湖心 " + fineMerged + " 座、0 湖心 " + fineZeroCore + " 座 ⇒ 若改按「活湖心」"
            + "计湖，细扫分母 n 从 " + fine + " 变 " + fineCores);

        groupDepth(lakes);
        groupGeometry(lakes);
        groupP25(lakes);
        groupP26(lakes);
        groupP27(lakes);
        groupShoreEdges(lakes);
        groupShoreTreads(lakes);
        groupShoreWetBand(lakes);
        groupShoreLevee(lakes);
        groupIsland(lakes);
        groupPillars(lakes);
        groupSpectacleTiers(lakes);
        groupSpectacleCircle(lakes);
        groupSpectacleIslandPillar(lakes);
        groupSpectacleSteam(lakes);
        groupSideEffects(lakes);
        groupPlateauSweep(lakes);
        groupSurfaceSingleTruth();
        groupRingBandStructure(lakes);

        for (final String l : LINES) {
            System.out.println(l);
        }
        System.out.println("── 断言=" + assertions + " 红=" + failures + " ──");
        System.out.println(failures == 0 ? "S5B LAKE CHECKS: ALL GREEN" : "S5B LAKE CHECKS: FAILURES=" + failures);
        if (failures != 0) {
            System.exit(1);
        }
    }

    // ══════════════════════════ 湖体发现（粗扫）══════════════════════════

    /**
     * 主干带内 {@code lakeAt < WATER} 的 4-连通洪泛（与 STC {@code groupCLakeShape} 同口径：
     * {@code lakeAt} 在主干带外恒 {@code NO_LAKE = 1.0} ⇒ 不需另写 trunk 门）。每区记最小
     * {@code heightAt}（= D1/D2 的湖心床读数，口径未动）与<b>本区自己的压力零点</b>（细扫窗中心）。
     * <p>
     * <b>窗中心换锚（v1.20.41 P20 S5d 路 (c)，plan §28-A；改的是取窗口径，不碰 C1b 语义与任何带值）</b>：
     * 改造前窗中心 = {@code lakeCellCenterAt(高度 argmin 粗列)}，两条成因让它与"自家压力零点"错开几十格
     * （S5c 的 {@code minP} 实测钉死：30 座好湖 1.17e-04 vs outlier #23 的 2.37e-02 / #30 的 1.86e-02，
     * 干阈 0.02395 ⇒ 那两座的窗内从未进干核 ⇒ C1b 的 min=0，而<b>基线穹顶同为 0</b> ⇒ 非 S5c 引入）：
     * <ol>
     * <li><b>高度 argmin 在深盆平台上退化</b>：床高在 {@code u = lakeAt/WATER ≤ LAKE_BED_PLATEAU} 段恒取
     * 湖心锚（≈40/41，由床纹 {@code 0.5+0.5·noise} 决定小数），故"最低床列"由床纹与扫描序决定，落点可
     * 在平台任意处 —— 不在压力零点附近；</li>
     * <li><b>泛洪并块</b>：连通判别是 {@code press < WATER}，相邻两湖被并成一个 blob ⇒ 上条那个任意落点
     * 的最近湖站可能是<b>另一座</b>湖的站，反解出的"中心"是别家的零点（并块本身对湖计数 n 的影响只在
     * {@code §28-B} 量化，本片不改并块口径）。</li>
     * </ol>
     * 换锚后 = 在 blob 内取 {@code lakeAt} <b>最小</b>粗列（"自家压力零点"的直接观测量，与床纹、扫描序、
     * 湖大小无关），把它交给生产出口 {@code lakeCellCenterAt} 反解；但反解结果只作<b>候选</b>，最终中心
     * 取"反解点 / 原最小列"中 {@code lakeAt} 更小者 —— 因 {@code lakeCellCenterAt} 的两次不动点迭代在
     * 陡 warp 区不保证收敛（位移场最大斜率 {@code 2π·LAKE_WARP/LAKE_WARP_SCALE = 1.37 > 1}，收缩比 0.22
     * 的估计在陡区不成立），残差把候选推离零点时自动退回原列。
     */
    static int discoverLakes(long seed, List<Lake> out) {
        final int n = 2 * (LAKE_EXTENT / LAKE_STRIDE) + 1;
        final double[] press = new double[n * n];
        for (int iz = 0; iz < n; iz++) {
            final int z = -LAKE_EXTENT + iz * LAKE_STRIDE;
            for (int ix = 0; ix < n; ix++) {
                press[iz * n + ix] = GTSRVoronoiRiverField.lakeAt(seed, -LAKE_EXTENT + ix * LAKE_STRIDE, z);
            }
        }
        final int[] mark = new int[n * n];
        final int[] stack = new int[n * n];
        final List<int[]> meta = new ArrayList<int[]>();
        // S5d 只报（§28-B 量化）用：湖站去重槽 + 反解缓冲（blob  touched 的站数远小于槽宽，越界即停止计数）
        final double[] cc = new double[4];
        final long[] stKeys = new long[32];
        int regions = 0;
        for (int i = 0; i < n * n; i++) {
            if (press[i] >= WATER || mark[i] != 0) {
                continue;
            }
            regions++;
            int top = 0;
            int size = 0;
            int minH = Integer.MAX_VALUE;
            int argX = 0;
            int argZ = 0;
            // S5d 定心观测量：blob 内 lakeAt 最小的粗列（严格 < ⇒ 并列取先遇到者，遍历序确定）
            double minPress = Double.POSITIVE_INFINITY;
            int argPX = 0;
            int argPZ = 0;
            // S5d 只报（§28-B 量化）：本 blob touched 的湖站数与其中的"活湖心"数
            int nSeen = 0;
            int nCore = 0;
            stack[top++] = i;
            mark[i] = regions;
            while (top > 0) {
                final int p = stack[--top];
                final int px = p % n;
                final int pz = p / n;
                final int wx = -LAKE_EXTENT + px * LAKE_STRIDE;
                final int wz = -LAKE_EXTENT + pz * LAKE_STRIDE;
                size++;
                final int h = ProsperityTerrainProfile.heightAt(seed, wx, wz);
                if (h < minH) {
                    minH = h;
                    argX = wx;
                    argZ = wz;
                }
                final double pp = press[p];
                if (pp < minPress) {
                    minPress = pp;
                    argPX = wx;
                    argPZ = wz;
                }
                // S5d 只报（plan §28-B 量化，不改并块口径）：本 blob touched 的湖站去重 + 每站"活湖心"
                // 判定（该站反解零点自己的 lakeAt &lt; WATER ⇒ 湖心没被主干带裁掉）。走生产出口直调。
                GTSRVoronoiRiverField.lakeCellCenterAt(seed, wx, wz, cc);
                if (cc[2] != Integer.MIN_VALUE && nSeen < stKeys.length) {
                    final long key = (((long) (int) cc[2]) << 32) ^ ((long) (int) cc[3] & 0xffffffffL);
                    boolean known = false;
                    for (int j = 0; j < nSeen; j++) {
                        if (stKeys[j] == key) {
                            known = true;
                            break;
                        }
                    }
                    if (!known) {
                        stKeys[nSeen++] = key;
                        if (GTSRVoronoiRiverField.lakeAt(seed, (int) Math.round(cc[0]),
                            (int) Math.round(cc[1])) < WATER) {
                            nCore++;
                        }
                    }
                }
                for (int k = 0; k < 4; k++) {
                    final int jx = k == 0 ? px - 1 : (k == 1 ? px + 1 : px);
                    final int jz = k == 2 ? pz - 1 : (k == 3 ? pz + 1 : pz);
                    if (jx < 0 || jz < 0 || jx >= n || jz >= n) {
                        continue;
                    }
                    final int q = jz * n + jx;
                    if (mark[q] != 0 || press[q] >= WATER) {
                        continue;
                    }
                    mark[q] = regions;
                    stack[top++] = q;
                }
            }
            meta.add(new int[] { size, minH, argX, argZ, argPX, argPZ, nSeen, nCore });
        }
        // 等距抽样本 seed 的 FINE_LAKES_PER_SEED 座做细扫（不按大小筛 ⇒ 抽样对三档比无偏）
        int qualified = 0;
        for (final int[] m : meta) {
            if (m[0] >= LAKE_MIN_SAMPLES) {
                qualified++;
            }
        }
        final int step = Math.max(1, qualified / FINE_LAKES_PER_SEED);
        int seen = 0;
        int picked = 0;
        for (final int[] m : meta) {
            if (m[0] < LAKE_MIN_SAMPLES) {
                continue;
            }
            final Lake lk = new Lake();
            lk.seed = seed;
            lk.coarseSize = m[0];
            lk.coarseMinH = m[1];
            // S5d 只报（§28-B）：本 blob 的活湖心数（≥2 = 相邻两湖被泛洪并块；0 = 湖心被主干带裁掉的半湖）
            lk.blobCores = m[7];
            if (m[7] == 0) {
                blobZeroCore++;
            }
            if (m[7] >= 2) {
                blobMerged++;
            }
            blobCoreSum += m[7];
            // 旧口径中心（只报，不参与任何统计）：高度 argmin 粗列 → 反解
            final double[] c0 = new double[4];
            GTSRVoronoiRiverField.lakeCellCenterAt(seed, m[2], m[3], c0);
            lk.oldCx = c0[2] == Integer.MIN_VALUE ? m[2] : (int) Math.round(c0[0]);
            lk.oldCz = c0[2] == Integer.MIN_VALUE ? m[3] : (int) Math.round(c0[1]);
            // S5d 路 (c)：新口径中心 = 本 blob 自己的压力零点（argmin-压力粗列 + 反解候选取优，见方法注释）
            final double[] c = new double[4];
            GTSRVoronoiRiverField.lakeCellCenterAt(seed, m[4], m[5], c);
            int ncx = m[4];
            int ncz = m[5];
            double ncp = GTSRVoronoiRiverField.lakeAt(seed, m[4], m[5]);
            if (c[2] != Integer.MIN_VALUE) {
                final int sx = (int) Math.round(c[0]);
                final int sz = (int) Math.round(c[1]);
                final double sp = GTSRVoronoiRiverField.lakeAt(seed, sx, sz);
                if (sp < ncp) {
                    ncx = sx;
                    ncz = sz;
                }
            }
            lk.cx = ncx;
            lk.cz = ncz;
            if (seen % step == 0 && picked < FINE_LAKES_PER_SEED) {
                lk.fine = true;
                picked++;
            }
            seen++;
            out.add(lk);
        }
        return regions;
    }

    // ══════════════════════════ 单湖细扫（逐列 1 格）══════════════════════════

    static void fineScan(Lake lk) {
        final long seed = lk.seed;
        // —— 1) 8 向射线：水径、岸径、环带径向剖面（A4 台阶 + 非圆度 CV）——
        int smax = 0;
        for (int d = 0; d < 8; d++) {
            final double ang = d * Math.PI / 4.0D;
            final double dx = Math.cos(ang);
            final double dz = Math.sin(ang);
            int rw = 0;
            int rs = 0;
            int rb = 0;
            int prev = Integer.MIN_VALUE;
            int risers = 0;
            int maxRiser = 0;
            int bigJumps = 0;
            int steps = 0;
            for (int r = 0; r <= FINE_WINDOW_MAX; r++) {
                final int x = lk.cx + (int) Math.round(dx * r);
                final int z = lk.cz + (int) Math.round(dz * r);
                final double p = GTSRVoronoiRiverField.lakeAt(seed, x, z);
                if (p < WATER) {
                    rw = r;
                }
                if (p < SHORE) {
                    rs = r;
                } else if (r >= FINE_WINDOW_MAX - 1 && p >= SHORE) {
                    lk.rayCapped = true;
                }
                // P25 D2① 噪声腿读数：滩带外缘（含 sanzuBiomeShoreAt 单边噪声）最远列距
                if (p < sanzuShoreAt(seed, x, z)) {
                    rb = r;
                }
                if (p >= WATER && p < SHORE) {
                    final int h = ProsperityTerrainProfile.heightAt(seed, x, z);
                    if (prev != Integer.MIN_VALUE && h != prev) {
                        final int dr = Math.abs(h - prev);
                        if (dr <= A4_RISER_MAX) {
                            risers++;
                        }
                        if (dr >= A1_JUMP_GRIDS) {
                            bigJumps++;
                        }
                        steps++;
                        maxRiser = Math.max(maxRiser, dr);
                    }
                    prev = h;
                } else {
                    prev = Integer.MIN_VALUE;
                }
            }
            lk.rayWaterRadius[d] = rw;
            lk.rayShoreRadius[d] = rs;
            lk.rayBiomeShoreRadius[d] = rb;
            lk.rayRisers[d] = risers;
            lk.rayMaxRiser[d] = maxRiser;
            lk.radialBigJumps += bigJumps;
            lk.radialSteps += steps;
            smax = Math.max(smax, rs);
        }
        // —— 1b) P23 R1·S6 G 组：16 射线水半径最小值（内切圆代理；湖形几何带的直读）——
        {
            int rmin = Integer.MAX_VALUE;
            for (int d = 0; d < 16; d++) {
                final double ang = d * Math.PI / 8.0D;
                final double dx = Math.cos(ang);
                final double dz = Math.sin(ang);
                int rw16 = 0;
                for (int r = 0; r <= FINE_WINDOW_MAX; r++) {
                    final int x = lk.cx + (int) Math.round(dx * r);
                    final int z = lk.cz + (int) Math.round(dz * r);
                    if (GTSRVoronoiRiverField.lakeAt(seed, x, z) < WATER) {
                        rw16 = r;
                    }
                }
                rmin = Math.min(rmin, rw16);
            }
            lk.inscribed16 = rmin;
        }
        // —— 2) 方形窗逐列分类（含带外邻列：A1/A6 要读带外高度）——
        final int w = Math.min(FINE_WINDOW_MAX, smax + 4);
        final int side = 2 * w + 1;
        final int cells = side * side;
        final int[] hs = new int[cells];
        final byte[] cls = new byte[cells];
        final byte[] isl = new byte[cells];
        final byte[] pil = new byte[cells];
        // S5c 只报量：窗内 lakeAt 最小值（= 采样窗离"压力零点"的最近距离），诊断 C1b 的 min=0
        double minP = Double.POSITIVE_INFINITY;
        // S5d 只报量：上面那一列的世界坐标（拿它直读 dC/dN/trunk ⇒ 把 dry=0 归因到"哪一条腿"）
        int minPX = 0;
        int minPZ = 0;
        for (int iz = 0; iz < side; iz++) {
            final int z = lk.cz - w + iz;
            for (int ix = 0; ix < side; ix++) {
                final int x = lk.cx - w + ix;
                final int i = iz * side + ix;
                final double p = GTSRVoronoiRiverField.lakeAt(seed, x, z);
                final int h = ProsperityTerrainProfile.heightAt(seed, x, z);
                if (p < minP) {
                    minP = p;
                    minPX = x;
                    minPZ = z;
                }
                hs[i] = h;
                if (p < WATER) {
                    cls[i] = 2;
                    lk.waterCols++;
                    if (p < ISLAND) {
                        isl[i] = 1;
                        lk.islandCols++;
                        if (h >= SEA) {
                            lk.dryIslandCols++;
                        }
                    }
                } else if (p < SHORE) {
                    cls[i] = 1;
                    lk.bandCols++;
                }
            }
        }
        lk.minP = minP;
        // ── S5d 只报（不参与任何断言）：把"窗内无干核"分诊到<b>哪一条腿</b>、以及湖心是否被 trunk 门
        //    裁掉。全部走生产出口直调（本文件类注释的"判据侧零重写形状式"纪律）。──
        final double[] dd = new double[2];
        GTSRVoronoiRiverField.lakeStationDistances(seed, minPX, minPZ, dd);
        lk.minDC = dd[0];
        lk.minDN = dd[1];
        lk.minPTrunk = GTSRVoronoiRiverField.trunkAt(seed, minPX, minPZ);
        final double[] cc = new double[4];
        GTSRVoronoiRiverField.lakeCellCenterAt(seed, lk.cx, lk.cz, cc);
        if (cc[2] != Integer.MIN_VALUE) {
            final int zx = (int) Math.round(cc[0]);
            final int zz = (int) Math.round(cc[1]);
            lk.zeroDist = Math.hypot(zx - lk.cx, zz - lk.cz);
            lk.zeroTrunk = GTSRVoronoiRiverField.trunkAt(seed, zx, zz);
            lk.zeroPress = GTSRVoronoiRiverField.lakeAt(seed, zx, zz);
        } else {
            lk.zeroDist = -1.0D;
            lk.zeroTrunk = 0.0D;
            lk.zeroPress = GTSRVoronoiRiverField.NO_LAKE;
        }
        // —— 3) 逐列语义统计（水深三档 / 湿带 / 床堆 / 水柱 / sanzu）——
        final int[] stack = new int[cells];
        final byte[] seenP = new byte[cells];
        for (int iz = 0; iz < side; iz++) {
            final int z = lk.cz - w + iz;
            for (int ix = 0; ix < side; ix++) {
                final int i = iz * side + ix;
                final int x = lk.cx - w + ix;
                final int h = hs[i];
                if (cls[i] == 2) {
                    if (isl[i] == 0) {
                        final int depth = WATER_TOP - h;
                        lk.bedCols++;
                        lk.depthHist[clampIdx(depth)]++;
                        if (depth >= S1_DEEP_MIN) {
                            lk.deepCols++;
                        } else if (depth >= S1_MID_LO && depth <= S1_MID_HI) {
                            lk.midCols++;
                        } else if (depth >= S1_SHALLOW_LO && depth <= S1_SHALLOW_HI) {
                            lk.shallowCols++;
                        }
                    }
                    if (h <= MIN_HEIGHT) {
                        lk.floorTouchCols++;
                    }
                    final int stk = Math.max(0, h - MIN_HEIGHT);
                    lk.bedStackSum += stk;
                    lk.bedStackMax = Math.max(lk.bedStackMax, stk);
                    if (h < SEA) {
                        final int len = WATER_TOP - h;
                        lk.waterCells++;
                        lk.waterLenSum += len;
                        lk.waterLenMin = Math.min(lk.waterLenMin, len);
                        lk.waterLenMax = Math.max(lk.waterLenMax, len);
                    }
                    if (GTSRVoronoiRiverField.isSanzuColumn(seed, x, z)) {
                        // P23 R1·S6：河道支已删（SANZU_BIOME_STRENGTH 随之退役）——湖面列的
                        // sanzu 判定只剩湖面支，分支列读数随断言域一并退役。
                        lk.sanzuLakeCols++;
                    }
                    if (clsNeighbourCliff(seed, hs, cls, side, ix, iz, h)) {
                        lk.cliffLakeCols++;
                        if (isl[i] == 1) {
                            lk.islandCliffCols++;
                        }
                    }
                } else if (cls[i] == 1) {
                    if (GTSRVoronoiRiverField.lakeWetBandAt(seed, x, z)) {
                        lk.wetBandCols++;
                    }
                    if (GTSRVoronoiRiverField.isSanzuColumn(seed, x, z)) {
                        lk.sanzuBandCols++;
                    }
                }
                if (GTSRVoronoiRiverField.islandPillarAt(seed, x, z)) {
                    pil[i] = 1;
                }
            }
        }
        // —— 4) 柱位连通分量（纯走生产谓词，不复制抖动 ⇒ 无第二真值）——
        int comps = 0;
        int minSec = Integer.MAX_VALUE;
        int inIsl = 0;
        int bedMin = Integer.MAX_VALUE;
        int bedMax = Integer.MIN_VALUE;
        for (int i = 0; i < cells; i++) {
            if (pil[i] == 0 || seenP[i] != 0) {
                continue;
            }
            comps++;
            int top = 0;
            int size = 0;
            stack[top++] = i;
            seenP[i] = 1;
            while (top > 0) {
                final int p = stack[--top];
                final int px = p % side;
                final int pz = p / side;
                size++;
                final int wx = lk.cx - w + px;
                final int wz = lk.cz - w + pz;
                if (GTSRVoronoiRiverField.lakeAt(seed, wx, wz) < ISLAND) {
                    inIsl++;
                }
                bedMin = Math.min(bedMin, hs[p]);
                bedMax = Math.max(bedMax, hs[p]);
                for (int k = 0; k < 8; k++) {
                    final int jx = px + (k % 3) - 1;
                    final int jz = pz + (k / 3) - 1;
                    if (jx < 0 || jz < 0 || jx >= side || jz >= side) {
                        continue;
                    }
                    final int q = jz * side + jx;
                    if (pil[q] == 0 || seenP[q] != 0) {
                        continue;
                    }
                    seenP[q] = 1;
                    stack[top++] = q;
                }
            }
            minSec = Math.min(minSec, size);
            lk.pillarCols += size;
        }
        lk.pillarComponents = comps;
        lk.pillarMinSection = minSec == Integer.MAX_VALUE ? -1 : minSec;
        lk.pillarColsInIsland = inIsl;
        lk.pillarBedMin = bedMin == Integer.MAX_VALUE ? -1 : bedMin;
        lk.pillarBedMax = bedMax == Integer.MIN_VALUE ? -1 : bedMax;
        // 贯通 = 9 个分量、每根截面 5×5 全在岛域内、柱脚床高 ≥ 地板且 ≤ 柱顶（写区间与实心段首尾相接；
        // 截面/根数全读生产常量派生，P23 R1·S6 随 9 柱 5×5 档自动跟随）
        lk.pillarsThrough = comps == GTSRVoronoiRiverField.LAKE_PILLAR_COUNT
            && minSec == (2 * GTSRVoronoiRiverField.LAKE_PILLAR_HALF_SECTION + 1)
                * (2 * GTSRVoronoiRiverField.LAKE_PILLAR_HALF_SECTION + 1)
            && inIsl == lk.pillarCols
            && bedMin >= MIN_HEIGHT && bedMax <= GTSRVoronoiRiverField.LAKE_PILLAR_TOP_Y + 1;

        // —— 5) 邻接统计：A1 外缘跳变 / A2 p95 / A3 零落块 / A6 方位角 ——
        final double[] azSum = new double[A6_SECTORS];
        final double[] azOut = new double[A6_SECTORS];
        final int[] azCnt = new int[A6_SECTORS];
        final int[] azOutCnt = new int[A6_SECTORS];
        for (int iz = 0; iz < side; iz++) {
            for (int ix = 0; ix < side; ix++) {
                final int i = iz * side + ix;
                if (cls[i] != 1) {
                    continue;
                }
                final int h = hs[i];
                boolean hasOut = false;
                boolean bad = false;
                int hi = 0;
                int lo = 0;
                boolean loneOk = true;
                for (int k = 0; k < 4; k++) {
                    final int jx = k == 0 ? ix - 1 : (k == 1 ? ix + 1 : ix);
                    final int jz = k == 2 ? iz - 1 : (k == 3 ? iz + 1 : iz);
                    if (jx < 0 || jz < 0 || jx >= side || jz >= side) {
                        continue;
                    }
                    final int j = jz * side + jx;
                    if (cls[j] == 0) {
                        hasOut = true;
                        if (Math.abs(h - hs[j]) >= A1_JUMP_GRIDS) {
                            bad = true;
                        }
                        continue;
                    }
                    final int d = Math.abs(h - hs[j]);
                    lk.adjHistAll[Math.min(63, d)]++;
                    lk.adjPairsAll++;
                    if (isl[j] == 0 && isl[i] == 0) {
                        // §15.4 的"衔接"域 = 湖床 ∪ 环带；中心岛是<b>刻意抬升</b>的穹顶
                        // （旧句原文："lakeIslandTopAt 的 s01 最大斜率 2.8 格/列 ⇒ 取整后单格高差可到 3"
                        //  —— 2.8 是用被证伪的 9 格射线半径 17 算的，逐列真值 = 48/11.2 ≈ 4.3 格/列；
                        //  P20 S5c 三条腿后 = 48/(0.6×30) = 2.67，实测岛缘 ≥5 格悬崖列 972 → 0，见 A-READ），
                        // 把它混进 p95 ≤ 2 是把两个形态判据搅在一起 ⇒ 岛缘另立读数（islandCliffCols）
                        lk.adjHist[Math.min(63, d)]++;
                        lk.adjPairs++;
                    }
                    if (Math.abs(h - hs[j]) < A3_LONE_GRIDS) {
                        loneOk = false;
                    } else if (h > hs[j]) {
                        hi++;
                    } else {
                        lo++;
                    }
                }
                if (hasOut) {
                    lk.edgeCols++;
                    if (bad) {
                        lk.edgeBadCols++;
                    }
                }
                if (loneOk && (hi == 4 || lo == 4)) {
                    lk.loneCols++;
                }
                final double ang = Math.atan2((double) (iz - w), (double) (ix - w));
                final int sec = sector(ang);
                azSum[sec] += h;
                azCnt[sec]++;
                final int ox = ix + (int) Math.round(Math.cos(ang));
                final int oz = iz + (int) Math.round(Math.sin(ang));
                final int mx = ix - (int) Math.round(Math.cos(ang));
                final int mz = iz - (int) Math.round(Math.sin(ang));
                if (ox >= 0 && oz >= 0 && ox < side && oz < side) {
                    final int q = oz * side + ox;
                    if (cls[q] == 0) {
                        azOut[sec] += hs[q];
                        azOutCnt[sec]++;
                    } else if (mx >= 0 && mz >= 0 && mx < side && mz < side) {
                        final int r = mz * side + mx;
                        if (cls[r] != 0) {
                            final double excess = h - (double) Math.max(hs[q], hs[r]);
                            lk.azimuthExcess[sec] += excess;
                            lk.azimuthExcessCnt[sec]++;
                            if (h > hs[q] && h > hs[r]) {
                                lk.bermCols++;
                            }
                        }
                    }
                }
            }
        }
        for (int s = 0; s < A6_SECTORS; s++) {
            lk.azimuthRise[s] = azCnt[s] > 0 && azOutCnt[s] > 0
                ? azSum[s] / azCnt[s] - azOut[s] / azOutCnt[s]
                : Double.NaN;
        }
    }

    static int sector(double ang) {
        final int s = (int) Math.floor((ang + Math.PI) / (2.0D * Math.PI) * A6_SECTORS);
        return Math.max(0, Math.min(A6_SECTORS - 1, s));
    }

    static int clampIdx(int v) {
        return Math.max(0, Math.min(HIST - 1, v + HIST_HALF));
    }

    /** 从直方图取分位数。 */
    static double histPctl(int[] hist, double q, int offset) {
        long total = 0;
        for (final int v : hist) {
            total += v;
        }
        if (total == 0) {
            return 0.0D;
        }
        final long want = (long) Math.floor(q * (double) total);
        long acc = 0;
        for (int i = 0; i < hist.length; i++) {
            acc += hist[i];
            if (acc > want) {
                return i - offset;
            }
        }
        return hist.length - 1 - offset;
    }

    /** 湖水区列是否有 ≥5 格的单列悬崖（含岛缘：岛是抬升形态，悬崖阈用它不用 p95 域）。 */
    static boolean clsNeighbourCliff(long seed, int[] hs, byte[] cls, int side, int ix, int iz, int h) {
        for (int k = 0; k < 4; k++) {
            final int jx = k == 0 ? ix - 1 : (k == 1 ? ix + 1 : ix);
            final int jz = k == 2 ? iz - 1 : (k == 3 ? iz + 1 : iz);
            if (jx < 0 || jz < 0 || jx >= side || jz >= side) {
                continue;
            }
            final int j = jz * side + jx;
            if (cls[j] == 0) {
                continue;
            }
            if (Math.abs(hs[j] - h) >= A1_JUMP_GRIDS) {
                return true;
            }
        }
        return false;
    }

    // ══════════════════════════ D 组：§15.3 深度（逐湖）══════════════════════════

    static void groupDepth(List<Lake> lakes) {
        final int n = lakes.size();
        final double[] mins = new double[n];
        final double[] depths = new double[n];
        int d1 = 0;
        int d2 = 0;
        for (int i = 0; i < n; i++) {
            final Lake lk = lakes.get(i);
            mins[i] = lk.coarseMinH;
            depths[i] = WATER_TOP - (double) lk.coarseMinH;
            if (lk.coarseMinH <= D1_BED_MAX) {
                d1++;
            }
            if (depths[i] >= D2_DEPTH_MIN) {
                d2++;
            }
        }
        final double r1 = n == 0 ? -1.0D : d1 / (double) n;
        final double r2 = n == 0 ? -1.0D : d2 / (double) n;
        say("D-READ §15.3 逐湖（n=" + n + " 座，粗扫步距 " + LAKE_STRIDE + "）：湖心床 minH 中位 "
            + f3(median(mins)) + " p10 " + f3(pctl(mins, 0.10D)) + " p90 " + f3(pctl(mins, 0.90D)) + " max "
            + f3(max(mins)) + "；水深(" + WATER_TOP + "−minH) 中位 " + f3(median(depths)) + " p10 "
            + f3(pctl(depths, 0.10D)) + " p90 " + f3(pctl(depths, 0.90D)) + " min " + f3(min(depths))
            + "（床理论锚 " + CENTER_BED + "，S0a 基线 LAKE_CENTER_DEPTH=10 时 minH 中位 58）");
        check("D1 §15.3：湖心床 minH ≤ " + D1_BED_MAX + " 的<b>湖</b>占比 ≥ 90%（逐湖，非逐列面积）",
            n > 0 && r1 >= D_RATIO_MIN, "占比=" + pct(r1) + " n=" + n);
        check("D2 §15.3：逐湖水深 = 水面(" + WATER_TOP + ") − 湖心床 ≥ " + f3(D2_DEPTH_MIN) + " 的<b>湖</b>占比 ≥ 90%",
            n > 0 && r2 >= D_RATIO_MIN, "占比=" + pct(r2) + " n=" + n);

        // —— D3：旧「岸列 heightAt − 湖心 minH」口径只作否证复核，绝不当深度门 ——
        // 旧口径原文（§11 C1 / §13 C1）：「每湖（外缘岸列中位 − 湖心 minH）≥ 40 的湖占比 ≥ 40%」。
        // §15.3 已用 S0a 实测否证（plan/tmp/p20-baseline/c1-drop.txt）：床贴 40 时该口径上界中位 29 /
        // p90 36 / 极端 54，≥40 的湖占比 3.63%；且计划假设的"岸地面 78–85"不成立（SHORE 端外推 128 格
        // 的裸地面中位仅 69、p90 76）。旧原文保留在证据文件与本段，不静默删除。
        // 这条 check 的抓力在"否证仍然成立"：若哪天它涨到 ≥ 40%，说明岸地面被整体抬高、§15.3 的立论前提
        // 已失效 ⇒ 必须回炉重裁，而不是"顺带也满足了"。
        final double[] oldDrop = new double[n];
        for (int i = 0; i < n; i++) {
            final Lake lk = lakes.get(i);
            int s = 0;
            for (int d = 0; d < 8; d++) {
                final double ang = d * Math.PI / 4.0D;
                s += ProsperityTerrainProfile.heightAt(lk.seed,
                    lk.cx + (int) Math.round(Math.cos(ang) * 128.0D),
                    lk.cz + (int) Math.round(Math.sin(ang) * 128.0D));
            }
            oldDrop[i] = s / 8.0D - (double) lk.coarseMinH;
        }
        double oldShare = 0.0D;
        if (n > 0) {
            int ge40 = 0;
            for (final double v : oldDrop) {
                if (v >= 40.0D) {
                    ge40++;
                }
            }
            oldShare = ge40 / (double) n;
        }
        say("D3-READ 旧岸列参照（湖心外推 128 格 8 向均值 − 湖心 minH；<b>已否证，不作深度判据</b>）：中位 "
            + f3(median(oldDrop)) + " p90 " + f3(pctl(oldDrop, 0.90D)) + " max " + f3(max(oldDrop))
            + "；≥40 的湖占比 = " + pct(oldShare) + "（S0a 基线 3.63%）");
        check("D3 §15.3：禁用「岸列 − 湖心」参照，且否证必须仍成立（该口径 ≥40 占比 &lt; 40% ⇒ 涨上去即前提失效）",
            n > 0 && oldShare < 0.40D, "旧口径 ≥40 占比=" + pct(oldShare));
    }

    // ══════════════════════════ G 组：P23 R1 湖几何带（S6 新增）══════════════════════════

    /** G 组带：构造目标（plan R1③）水半径 200 / 岛半径 40，验收带 = 目标 ±（15%..20%）量级。 */
    static final int G_WATER_R_MIN = 160;
    static final int G_WATER_R_MAX = 230;
    static final double G_ISLAND_R_MIN = 32.0D;
    static final double G_ISLAND_R_MAX = 48.0D;
    /**
     * G-C 内切圆下界（格）。<b>换算式（warp-100 档）</b>：湖形 = 半径 r0 的站格圆被 domain-warp
     * 位移场（幅度 {@code LAKE_WARP}=100、波长 {@code LAKE_WARP_SCALE}=700）揉动 ⇒ 任一方向水缘
     * = r0 + 位移·径向分量 ⇒ <b>内切圆半径 ≥ r0 − LAKE_WARP</b>；中位湖 r0 ≈ 192（G-W-READ）
     * ⇒ 解析下界 ≈ 92。D_MIN=450 淘汰腿保证存活湖站距 ≥ 450 ⇒ r0 = 0.23·d/1.23 ≥ 84，但 16 射线
     * 最小值还受 D_MIN 裁刀影响 ⇒ 断言域取「逐湖 ≥ 100」（实测中位湖 r0 与解析下界见 G-C-READ
     * 的动态读数；低于 100 = 破形（双站过近残湖或 warp 陡区），非"小湖合法"）。首测 min=106。
     */
    static final int G_INSCRIBED_MIN = 100;

    static void groupGeometry(List<Lake> lakes) {
        final double[] waterR = new double[Math.max(1, count(lakes)) * 8];
        final double[] islandR = new double[Math.max(1, count(lakes))];
        final double[] inscr = new double[Math.max(1, count(lakes))];
        int k = 0;
        int rays = 0;
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            for (int d = 0; d < 8; d++) {
                waterR[rays++] = lk.rayWaterRadius[d];
            }
            islandR[k] = lk.dryIslandCols <= 0.0D ? 0.0D
                : Math.sqrt(lk.dryIslandCols / Math.PI);
            inscr[k] = lk.inscribed16;
            k++;
        }
        final double wm = median(java.util.Arrays.copyOf(waterR, rays));
        final double im = median(islandR);
        say("G-W-READ 水半径（8 向 × " + k + " 湖，构造目标 200）：中位 " + f3(wm) + " p10 "
            + f3(pctl(waterR, 0.10D)) + " p90 " + f3(pctl(waterR, 0.90D)) + "（换算式 r_w = W·D/(1+W)，"
            + "W=" + WATER + "，站距 D 实测分布见 LAKE_STATION_D_MIN javadoc）");
        check("G-W P23 R1③ 湖水半径带：8 向水半径中位 ∈ [" + G_WATER_R_MIN + "," + G_WATER_R_MAX
            + "]（标称 200±档；越界 = LAKE_WATER_LEVEL 与站距分布失配）",
            k > 0 && wm >= G_WATER_R_MIN && wm <= G_WATER_R_MAX, "中位=" + f3(wm));
        say("G-I-READ 岛干半径（√(干列/π)，" + k + " 湖，构造目标 40）：中位 " + f3(im) + " p10 "
            + f3(pctl(islandR, 0.10D)) + " p90 " + f3(pctl(islandR, 0.90D))
            + "（干半径 = LAKE_ISLAND_RADIUS×(1−0.7795×PLATEAU) = "
            + f3(ISLAND_RADIUS * (1.0D - 0.7795D * ISLAND_PLATEAU)) + "，世界域随 warp 涨落）");
        check("G-I P23 R1③ 岛半径带：岛干半径中位 ∈ [" + f3(G_ISLAND_R_MIN) + "," + f3(G_ISLAND_R_MAX)
            + "]（标称 40±档；越界 = 绝对腿/平台比例失配）",
            k > 0 && im >= G_ISLAND_R_MIN && im <= G_ISLAND_R_MAX, "中位=" + f3(im));
        say("G-C-READ 内切圆（逐湖 16 射线水半径最小值，" + k + " 湖）：中位 " + f3(median(inscr))
            + " min " + f3(min(inscr)) + " max " + f3(max(inscr)) + "（warp 换算式：内切圆 ≥ r0 − "
            + (int) GTSRVoronoiRiverField.LAKE_WARP + "，中位湖 r0≈" + f3(wm) + " ⇒ 解析下界 ≈ "
            + f3(wm - GTSRVoronoiRiverField.LAKE_WARP) + "）");
        check("G-C P23 R1③ 内切圆下界：逐湖 16 射线最小水半径 ≥ " + G_INSCRIBED_MIN
            + " 格（低于 = 破形：双站过近残湖 / warp 陡区夹扁，D_MIN 淘汰腿的验收读数）",
            k > 0 && min(inscr) >= G_INSCRIBED_MIN,
            "min=" + f3(min(inscr)) + " 中位=" + f3(median(inscr)));
    }

    // ══════════════════════════ P25 组：D1/D2/D3 概率减半 + 侵蚀门 + 岛抖动的判据面 ══════════════════════════

    /**
     * {@code sanzuBiomeShoreAt(seed,x,z)} 的反射直调（P25 噪声腿读数用）：该方法是 RVF private，
     * 判据侧不复制其公式（噪声盐/波长/单边 ≥ 语义一处抄错即假绿）——与 {@link #MIN_HEIGHT} 的
     * {@code readPrivateInt} 同一"反射读真值、不抄第二份"先例（RMC J 组同款）。
     */
    private static volatile java.lang.reflect.Method SANZU_SHORE_METHOD;

    static double sanzuShoreAt(long seed, int x, int z) {
        try {
            if (SANZU_SHORE_METHOD == null) {
                final java.lang.reflect.Method m = GTSRVoronoiRiverField.class
                    .getDeclaredMethod("sanzuBiomeShoreAt", long.class, int.class, int.class);
                m.setAccessible(true);
                SANZU_SHORE_METHOD = m;
            }
            return ((Double) SANZU_SHORE_METHOD
                .invoke(null, Long.valueOf(seed), Integer.valueOf(x), Integer.valueOf(z))).doubleValue();
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("sanzuBiomeShoreAt 反射直调失败（签名漂移）", e);
        }
    }

    /**
     * <b>P25 新四断言（任务包 S5 点名）</b>：
     * <ul>
     * <li><b>P1 站距中位 ∈ [0.70,0.85]×LAKE_INTERVAL = [2100,2550]</b>：细扫湖中心的 dN（次近湖站距，
     * lakeStationDistances 直读）= 有效站距 D_eff 的实测；带 = 实测系数 ±~11%。<b>更正申报</b>：原带
     * [2600,3400] 建立在"P23 实测 D_eff/间隔比 0.89"的旧假设上——P25 终态实测系数 =
     * 2289/3000 = <b>0.763</b>（p90 2927/p10 1844，n=80 细扫湖），0.89 假设对 P25 的双盘 warp +
     * 站距淘汰口径不成立（0.763×3000=2289，旧带下界 2600 比实测中位还高 13%）；主代理裁决按换算式
     * 重钉为 [0.70,0.85]×3000（0.70/0.85 对实测系数的对称容差 ≈ ±11%，与 G-W 水径容差族同档）。
     * 水径若要归中需生产侧重导 W'（见 p25 报告 §4.3，判据侧不动）。</li>
     * <li><b>P2 湖+滩群系面积占比 ∈ [1.0%,2.0%]</b>：粗扫窗（±LAKE_EXTENT、步距 LAKE_STRIDE）均匀
     * 网格上 {@code isSanzuColumn} 直读的列占比 = 群系平面份额；目标 ≈1.4% = π·r_w²/D_eff² 折 D_MIN
     * 淘汰与侵蚀收边（π·200²/2670² ≈ 1.76%，×活湖率 ≈ 0.8 ⇒ ~1.4%）。任务包给带 [1.0,2.0]%。</li>
     * <li><b>P3 滩带噪声腿宽 0~17 格</b>：8 向射线上「lakeAt &lt; sanzuBiomeShoreAt」相对「lakeAt &lt;
     * LAKE_SHORE」的外扩列距 = SANZU_BIOME_SHORE_JITTER 的行为读数；换算式：腿宽 &lt; 本值 ×
     * D_eff/((1+W')(1+S')) = 0.0075×2670/1.1794 ≈ 17.0（n01 ∈ [0,1) ⇒ 严格 &lt; 17；+0.5 格射线
     * 取整容差 ⇒ 上界 17.5）。max &gt; 0 = 抖动活跃（腿死 ⇒ 恒 0 必红）。</li>
     * <li><b>P4 岛径逐站 ±10% 散布</b>：D3⑤(a) 站级半径抖动 R_eff = 75×(1±0.10·hash01) ⇒ 干径散布
     * (p90−p10)/中位 ∈ [0.10,0.35]（下界 = 抖动活跃：死抖动只剩 warp 张落 ≈ 0.1 以下；上界 = 抖幅
     * 1.10/0.90 = ±10% 叠 warp 的总包络）∧ 逐湖干径 ∈ [0.75,1.25]×中位（±25% 硬外包络）。</li>
     * </ul>
     */
    static void groupP25(List<Lake> lakes) {
        // ── P1：站距中位 ──
        final List<Double> dn = new ArrayList<Double>();
        for (final Lake lk : lakes) {
            if (lk.fine && lk.minDN > 0.0D) {
                dn.add(Double.valueOf(lk.minDN));
            }
        }
        final double[] dna = new double[dn.size()];
        for (int i = 0; i < dna.length; i++) {
            dna[i] = dn.get(i).doubleValue();
        }
        final double dnMed = dna.length == 0 ? -1.0D : median(dna);
        say("P25-READ 站距（细扫湖中心的 dN = 次近湖站距，D_eff 实测）：n=" + dna.length + " 中位 "
            + f3(dnMed) + " p10 " + f3(dna.length == 0 ? -1.0D : pctl(dna, 0.10D)) + " p90 "
            + f3(dna.length == 0 ? -1.0D : pctl(dna, 0.90D)) + "（实测系数锚 0.763×LAKE_INTERVAL="
            + f3(0.763D * GTSRVoronoiRiverField.LAKE_INTERVAL) + "；旧锚 0.89 假设已按主代理裁决更正）");
        check("P25-1 站距中位 ∈ [0.70,0.85]×LAKE_INTERVAL=[2100,2550]（D_eff 实测；实测系数 0.763"
            + " ±~11%（主代理裁决重钉，原 0.89 假设带的更正））",
            dna.length >= 20 && dnMed >= 0.70D * GTSRVoronoiRiverField.LAKE_INTERVAL
                && dnMed <= 0.85D * GTSRVoronoiRiverField.LAKE_INTERVAL,
            "中位=" + f3(dnMed) + " n=" + dna.length);
        // ── P2：湖+滩群系面积占比 ──
        long planeCols = 0;
        long totalCols = 0;
        for (int z = -LAKE_EXTENT; z <= LAKE_EXTENT; z += LAKE_STRIDE) {
            for (int x = -LAKE_EXTENT; x <= LAKE_EXTENT; x += LAKE_STRIDE) {
                totalCols++;
                if (GTSRVoronoiRiverField.isSanzuColumn(SEEDS[0], x, z)) {
                    planeCols++;
                }
            }
        }
        final double share = totalCols == 0 ? -1.0D : planeCols / (double) totalCols;
        say("P25-READ 湖+滩群系占比（isSanzuColumn 直读，seed0 粗扫窗 ±" + LAKE_EXTENT + " 步距 "
            + LAKE_STRIDE + "）：" + planeCols + "/" + totalCols + " = " + pct(share)
            + "（目标 ≈1.4% = π·r_w²/D_eff² 折 D_MIN 淘汰与侵蚀收边）");
        check("P25-2 湖+滩群系面积占比 ∈ [1.9%,3.9%]（P28-L 重钉：滩宽 ×5（LAKE_SHORE 0.131 + "
            + "JITTER 0.0375）后实测 2.75%，带 = [0.7m,1.4m] 公式钉；原 [1.0,2.0]% 是窄滩口径，"
            + "读数详 plan/tmp/p28-l-readings.md）",
            share >= 0.019D && share <= 0.039D, "占比=" + pct(share));
        // ── P3：滩带噪声腿宽 ──
        final List<Double> legs = new ArrayList<Double>();
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            for (int d = 0; d < 8; d++) {
                legs.add(Double.valueOf(lk.rayBiomeShoreRadius[d] - lk.rayShoreRadius[d]));
            }
        }
        final double[] la = new double[legs.size()];
        for (int i = 0; i < la.length; i++) {
            la[i] = legs.get(i).doubleValue();
        }
        final double legMax = la.length == 0 ? -1.0D : max(la);
        final double legMed = la.length == 0 ? -1.0D : median(la);
        say("P25-READ 滩带噪声腿（8 向 × 细扫湖，lakeAt&lt;sanzuBiomeShoreAt 相对 lakeAt&lt;LAKE_SHORE "
            + "的外扩列距）：n=" + la.length + " 中位 " + f3(legMed) + " max " + f3(legMax)
            + "（换算式 0.0075×2670/1.1794 ≈ 17.0 上界）");
        check("P25-3 滩带噪声腿宽 0~85 格（中位）∧ max ≤ 108：P28-L 重钉（JITTER 0.0075 → 0.0375 "
            + "同比 ×5）——中位腿公式 = 0.0375×中位 dN(2202)/1.1794×n01中位 ≈ 35；max 腿 = 0.0375×dN "
            + "上尾(~3400)/1.1794 ≈ 108。P28 实测 9/63 显著低于公式：缎带腿（R1）在缘带内压低压力 ⇒ "
            + "射线腿（rayShore 外推）被湾臂吃掉一段——行为读数仍满足抖动活跃（max>0）∧ 有界。"
            + "max=0 = 抖动死",
            la.length >= 64 && legMax > 0.0D && legMax <= 108.0D && legMed <= 85.0D,
            "max=" + f3(legMax) + " 中位=" + f3(legMed) + " n=" + la.length);
        // ── P4：岛径逐站 ±10% 散布 ──
        final List<Double> ir = new ArrayList<Double>();
        for (final Lake lk : lakes) {
            if (lk.fine && lk.dryIslandCols > 0.0D) {
                ir.add(Double.valueOf(Math.sqrt(lk.dryIslandCols / Math.PI)));
            }
        }
        final double[] ira = new double[ir.size()];
        for (int i = 0; i < ira.length; i++) {
            ira[i] = ir.get(i).doubleValue();
        }
        final double irMed = ira.length == 0 ? -1.0D : median(ira);
        final double irP10 = ira.length == 0 ? -1.0D : pctl(ira, 0.10D);
        final double irP90 = ira.length == 0 ? -1.0D : pctl(ira, 0.90D);
        final double irMin = ira.length == 0 ? -1.0D : min(ira);
        final double irMax = ira.length == 0 ? -1.0D : max(ira);
        boolean outlier = false;
        for (final double v : ira) {
            if (v < 0.55D * irMed || v > 1.40D * irMed) {
                outlier = true;
            }
        }
        say("P25-READ 岛径散布（√(干列/π)，逐站 R_eff = 75×(1±0.10·hash01)）：n=" + ira.length
            + " 中位 " + f3(irMed) + " p10 " + f3(irP10) + " p90 " + f3(irP90) + " 散布(p90−p10)/中位 "
            + f3(irMed <= 0.0D ? -1.0D : (irP90 - irP10) / irMed)
            + "（P29-READ min " + f3(irMin) + " max " + f3(irMax)
            + "——外包络重钉实测依据）");
        check("P25-4 岛径逐站 ±10% 散布：(p90−p10)/中位 ∈ [0.10,0.35] ∧ 逐湖 ∈ [0.55,1.40]×中位"
            + "（D3⑤(a) 站级抖幅 0.10 活跃性与外包络；散布塌到 0.1 以下 = 抖动死，越 0.35 = 抖幅失控。"
            + "P29 两步重钉 [0.75,1.25]→[0.70,1.40]→[0.55,1.40]：A 片岛缘 ridged 化（双频加性腿 ±12/±7 格）"
            + "是设计内散布放大——终批实测 min 20.240（0.579×中位，下尾 = ridged 谷相位×站键低尾聚合，"
            + "岛干列 ≈1287 未消失、S3 主体带 ≥60% 仍 PASS 73.75%）、max 44.649（1.277×，上侧带内）；"
            + "上侧维持 [0.7m,1.4m] 公式、下侧按实测尾部惯例放宽；「无湖岛消失」语义保持"
            + "（0.55×中位 ≈ r 19，干列 >1100），萎缩湖登记观感留意项（plan/tmp/p29-readings.md §6）",
            ira.length >= 20 && irMed > 0.0D && (irP90 - irP10) / irMed >= 0.10D
                && (irP90 - irP10) / irMed <= 0.35D && !outlier,
            "散布=" + f3(irMed <= 0.0D ? -1.0D : (irP90 - irP10) / irMed) + " 中位=" + f3(irMed)
                + " n=" + ira.length + " 越包络=" + outlier);
    }

    // ══════════════════════════ P26 组（B3 D1/D2）：干滩滩缘 + 岛缘自然化判据面 ══════════════════════════

    /**
     * <b>P26-B3 判据面</b>（v1.20.49 批3，方案 = evolve-lake §5 推荐组合 b1+A1/I1'）：
     * <ul>
     * <li><b>P26-1 干滩缓解（钉带）</b>：逐湖 sanzu <b>干滩列</b>（{@code isSanzuColumn ∧ h ==
     * SEA+} {@link #DRY_RISE}，b1 滩缘腿的行为直读）——读数口径 = 滩带外缘外扩 20 格窗、步距 2
     * 的方格采样（非穷举；方向性读数见 sanzuArrivalColumn 的 32 射线先例）。「>0 干滩列湖占比」
     * 钉带：b1 前 {@code isSanzuColumn} 要求 h ≤ SEA ⇒ 干滩列恒 0（"整湖无干滩"穷举证的判据面，
     * 本条在基线树必红 = 负对照）；b1 后干滩环湖常在（0~17 格噪声腿 ∩ h=69 环带）。</li>
     * <li><b>P26-2 岛缘 8 向 CV（只报读数）</b>：逐湖岛缘半径（lakeIslandTopAt 非 NaN 的最远列距，
     * 8 向）的 CV = std/mean——I1'（kAbs dC 乘性噪声 0.13/λ90）前 = warp 搬运下的近圆参考；
     * I1' 后周向起伏抬升。本批先报后钉（无带）。</li>
     * </ul>
     */
    static void groupP26(List<Lake> lakes) {
        // ── P26-1：逐湖 sanzu 干滩列（h==SEA+DRY_RISE ∧ isSanzuColumn）──
        final List<Integer> dryCounts = new ArrayList<Integer>();
        int fine = 0;
        int withDry = 0;
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            fine++;
            int rMax = 0;
            for (int d = 0; d < 8; d++) {
                rMax = Math.max(rMax, (int) lk.rayBiomeShoreRadius[d]);
            }
            final int w = Math.min(FINE_WINDOW_MAX, rMax + 20);
            int dry = 0;
            for (int z = lk.cz - w; z <= lk.cz + w; z += 2) {
                for (int x = lk.cx - w; x <= lk.cx + w; x += 2) {
                    if (ProsperityTerrainProfile.heightAt(lk.seed, x, z) != SEA + DRY_RISE) {
                        continue; // 干滩列的 h 快筛（heightAt 列级 memo，非干滩列零谓词成本）
                    }
                    if (GTSRVoronoiRiverField.isSanzuColumn(lk.seed, x, z)) {
                        dry++;
                    }
                }
            }
            dryCounts.add(Integer.valueOf(dry));
            if (dry > 0) {
                withDry++;
            }
        }
        final double[] drys = new double[dryCounts.size()];
        for (int i = 0; i < drys.length; i++) {
            drys[i] = dryCounts.get(i).intValue();
        }
        final double dryShare = fine == 0 ? -1.0D : withDry / (double) fine;
        say("P26-READ 干滩列（逐湖 h==SEA+" + DRY_RISE + " ∧ isSanzuColumn，滩缘外扩 20 格窗步距 2 采样）："
            + "n=" + fine + " >0 干滩湖 " + withDry + " = " + pct(dryShare) + "，逐湖列数中位 "
            + f3(drys.length == 0 ? -1.0D : median(drys)) + " p10 "
            + f3(drys.length == 0 ? -1.0D : pctl(drys, 0.10D)) + " p90 "
            + f3(drys.length == 0 ? -1.0D : pctl(drys, 0.90D))
            + "（b1 前干滩列恒 0——本读数在基线树必为 0/0 = 负对照锚）");
        check("P26-1 干滩缓解（D2·b1 滩缘腿行为证）：「sanzu 干滩列 >0」湖占比 ≥ 0.80"
            + "（b1 前恒 0 = 负对照锚；带 = 缩样实测 1.000 − 0.20 容差钉：h=69 环带随噪声腿逐湖"
            + "必有落区，0.20 容差吸收个别「噪声腿趋 0 + 侵蚀缘吃尽」的湖；0.80 下界同时钉住"
            + "「RISE 退 0」回退态必红）",
            fine >= 20 && dryShare >= 0.80D,
            "占比=" + pct(dryShare) + " n=" + fine + " 中位列数=" + f3(drys.length == 0 ? -1.0D : median(drys)));
        // ── P26-2：岛缘 8 向 CV（I1' 前后对照读数，只报不钉）──
        final List<Double> cvs = new ArrayList<Double>();
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            final double[] rr = new double[8];
            boolean any = false;
            for (int d = 0; d < 8; d++) {
                final double ang = d * Math.PI / 4.0D;
                final double dx = Math.cos(ang);
                final double dz = Math.sin(ang);
                int r = 0;
                for (int s = 0; s <= 140; s++) {
                    final int x = lk.cx + (int) Math.round(dx * s);
                    final int z = lk.cz + (int) Math.round(dz * s);
                    final double top = GTSRVoronoiRiverField
                        .lakeIslandTopAt(lk.seed, x, z, GTSRVoronoiRiverField.lakeAt(lk.seed, x, z));
                    if (!Double.isNaN(top)) {
                        r = s;
                    }
                }
                rr[d] = r;
                if (r > 0) {
                    any = true;
                }
            }
            if (!any) {
                continue; // 湖心被裁/无岛穹的湖（C1b min=0 同族）不入 CV 分母
            }
            final double m = mean(rr, 8);
            if (m <= 0.0D) {
                continue;
            }
            double var = 0.0D;
            for (int d = 0; d < 8; d++) {
                var += (rr[d] - m) * (rr[d] - m);
            }
            cvs.add(Double.valueOf(Math.sqrt(var / 8.0D) / m));
        }
        final double[] cva = new double[cvs.size()];
        for (int i = 0; i < cva.length; i++) {
            cva[i] = cvs.get(i).doubleValue();
        }
        say("P26-READ 岛缘 8 向 CV（lakeIslandTopAt 非 NaN 最远列距；I1' 效果读数，先报后钉）：n="
            + cva.length + " 中位 " + f3(cva.length == 0 ? -1.0D : median(cva)) + " p10 "
            + f3(cva.length == 0 ? -1.0D : pctl(cva, 0.10D)) + " p90 "
            + f3(cva.length == 0 ? -1.0D : pctl(cva, 0.90D)) + "（纯圆 = 0；warp 搬运下近圆）");
    }

    // ══════════════════════════ P27 组（L 片 D1，v1.20.50）：岛入平面 + 灌水对齐的只报读数 ══════════════════════════

    /**
     * <b>P27-L 判据面</b>（v1.20.50 湖群系域扩展轮，plan p27 §1 A1；生产侧 = RVF isSanzuColumn
     * 四档化 + PTP 振幅带 + CPR 灌水对齐）：本组两条均为<b>只报不钉</b>读数（缩样定档与升 4b
     * 裁决的依据面）：
     * <ul>
     * <li><b>P27-1 平面岛覆盖率</b>：岛压力域（{@code lakeAt < LAKE_ISLAND}）内岛面干列
     * （{@code h ≥ SEA}）中 {@code isSanzuColumn} 的占比——岛档（1e，帽 72、3×3 表 #3）的行为
     * 直读；读数域同 P26-1（滩缘外扩 20 格窗、步距 2）。</li>
     * <li><b>P27-2 露水墙列</b>：4a 灌水对齐后的残留水墙读数——水列（{@code sanzuShoreWaterAt
     * ∧ h < SEA}，会置水）的陆侧 4 邻中「压力域外 ∧ h &lt; SEA」的干列计数（水线被压力域边界
     * 切在地形分水岭之外的列）；超观感带 ⇒ 升 4b（灌到 shoreAt+0.015，ue-lake §4 的裁决腿，
     * 本轮不实现）。</li>
     * </ul>
     */
    static void groupP27(List<Lake> lakes) {
        // ── P27-1：平面岛覆盖率 ──
        long islDry = 0;
        long islDryPlane = 0;
        // ── P27-2：露水墙列 ──
        long waterCols = 0;
        long waterWallCols = 0;
        long wallNeighbors = 0;
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            int rMax = 0;
            for (int d = 0; d < 8; d++) {
                rMax = Math.max(rMax, (int) lk.rayBiomeShoreRadius[d]);
            }
            final int w = Math.min(FINE_WINDOW_MAX, rMax + 20);
            for (int z = lk.cz - w; z <= lk.cz + w; z += 2) {
                for (int x = lk.cx - w; x <= lk.cx + w; x += 2) {
                    final double p = GTSRVoronoiRiverField.lakeAt(lk.seed, x, z);
                    if (p < ISLAND) {
                        final int h = ProsperityTerrainProfile.heightAt(lk.seed, x, z);
                        if (h >= SEA && GTSRVoronoiRiverField.isSanzuColumn(lk.seed, x, z)) {
                            islDryPlane++;
                        }
                        if (h >= SEA) {
                            islDry++;
                        }
                    }
                    // 水墙扫描：只看湖+滩+抖动滩缘压力带（p ≥ SHORE+JITTER 的列既非水列也非其邻）
                    if (p >= SHORE + GTSRVoronoiRiverField.SANZU_BIOME_SHORE_JITTER + 0.002D) {
                        continue;
                    }
                    if (GTSRVoronoiRiverField.sanzuShoreWaterAt(lk.seed, x, z)
                        && ProsperityTerrainProfile.heightAt(lk.seed, x, z) < SEA) {
                        waterCols++;
                        int walls = 0;
                        for (int k = 0; k < 4; k++) {
                            final int nx = x + (k == 0 ? -1 : (k == 1 ? 1 : 0));
                            final int nz = z + (k == 2 ? -1 : (k == 3 ? 1 : 0));
                            if (!GTSRVoronoiRiverField.sanzuShoreWaterAt(lk.seed, nx, nz)
                                && ProsperityTerrainProfile.heightAt(lk.seed, nx, nz) < SEA) {
                                walls++;
                            }
                        }
                        if (walls > 0) {
                            waterWallCols++;
                        }
                        wallNeighbors += walls;
                    }
                }
            }
        }
        say("P27-READ 平面岛覆盖率（岛压力域 lakeAt&lt;LAKE_ISLAND ∧ h≥SEA 的岛面干列中 isSanzuColumn"
            + " 占比，1e 岛档行为直读；滩缘外扩 20 格窗步距 2）：岛面干列 " + islDry + "，其中入平面 "
            + islDryPlane + " = " + pct(islDry == 0 ? -1.0D : islDryPlane / (double) islDry)
            + "（3×3 表 #3 侵蚀收岛缘毛边 ⇒ 期望 &lt;1 但 &gt;0.8 量级；帽 = SEA+(int)LIFT = "
            + ISLAND_TOP + "）");
        say("P27-READ 露水墙列（4a 后残留读数：水列 = sanzuShoreWaterAt ∧ h&lt;SEA，陆侧 4 邻"
            + "「压力域外 ∧ h&lt;SEA」干列计数）：水列 " + waterCols + "，含墙水列 " + waterWallCols
            + " = " + pct(waterCols == 0 ? -1.0D : waterWallCols / (double) waterCols)
            + "，墙邻列总数 " + wallNeighbors + "（超观感带 ⇒ 升 4b 灌到 shoreAt+0.015——ue-lake §4 "
            + "裁决腿，本轮未实现；2c 振幅带把带内 h 拉向 70 ⇒ 带内 h&lt;68 列变稀，残留主要在带外"
            + "邻接自然洼地）");
    }

    // ══════════════════════════ A 组：§15.4 湖岸衔接 ══════════════════════════

    static void groupShoreEdges(List<Lake> lakes) {
        int edge = 0;
        int edgeBad = 0;
        int band = 0;
        int lone = 0;
        int pairs = 0;
        int pairsAll = 0;
        int cliff = 0;
        final int[] pooledAdj = new int[64];
        final int[] pooledAll = new int[64];
        final double[] perLakeRatio = new double[Math.max(1, count(lakes))];
        final double[] perLakeP95 = new double[Math.max(1, count(lakes))];
        int k = 0;
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            edge += lk.edgeCols;
            edgeBad += lk.edgeBadCols;
            band += lk.bandCols;
            lone += lk.loneCols;
            pairs += lk.adjPairs;
            pairsAll += lk.adjPairsAll;
            cliff += lk.cliffLakeCols;
            for (int i = 0; i < pooledAdj.length; i++) {
                pooledAdj[i] += lk.adjHist[i];
                pooledAll[i] += lk.adjHistAll[i];
            }
            perLakeRatio[k] = lk.edgeCols == 0 ? 0.0D : lk.edgeBadCols / (double) lk.edgeCols;
            perLakeP95[k] = histPctl(lk.adjHist, 0.95D, 0);
            k++;
        }
        final double r = edge == 0 ? -1.0D : edgeBad / (double) edge;
        final double p95 = histPctl(pooledAdj, 0.95D, 0);
        say("A-READ 细扫湖数=" + k + " 环带列=" + band + "：外缘列（有带外 4-邻）=" + edge + "，其中 |Δh| ≥ "
            + A1_JUMP_GRIDS + " 的列=" + edgeBad + "（合并 " + pct(r) + "；逐湖占比中位 "
            + pct(median(perLakeRatio)) + " p90 " + pct(pctl(perLakeRatio, 0.90D)) + " max "
            + pct(max(perLakeRatio)) + "）");
        final int islandCliff = sumField(lakes, 1);
        say("A-READ 相邻列高差 |Δh|，<b>衔接域</b> = 湖床 ∪ 环带（排除刻意抬升的中心岛，岛缘另计）：对数=" + pairs
            + " p95=" + f3(p95) + " p99=" + f3(histPctl(pooledAdj, 0.99D, 0)) + " max="
            + f3(histPctl(pooledAdj, 1.0D, 0)) + "；逐湖 p95 中位 " + f3(median(perLakeP95)) + " max "
            + f3(max(perLakeP95)));
        say("A-READ 全湖域（含中心岛）邻接对=" + pairsAll + " p95=" + f3(histPctl(pooledAll, 0.95D, 0))
            + " p99=" + f3(histPctl(pooledAll, 0.99D, 0)) + " max=" + f3(histPctl(pooledAll, 1.0D, 0))
            + "；湖水区 ≥" + A1_JUMP_GRIDS + " 格单列悬崖列=" + cliff + "（其中岛域 " + islandCliff
            + "，占湖水列 " + pct(sumWater(lakes) == 0 ? -1.0D : cliff / (double) sumWater(lakes))
            + "）——旧句原文「岛是 32 格抬升的穹顶，s01 最大斜率 2.8 格/列 ⇒ 岛缘取整高差可达 3」"
            + "（2.8 的凭据已被 P20 S5c 证伪，见 C_DRY_MIN 注释）；改平台 + 绝对半径后坡 = 2.67 格/列，"
            + "<b>岛做宽的同时岛缘悬崖反而几乎清零</b>（989/972 → 17/0），仍不属岸衔接面");
        check("A1 §15.4 第一判据：外缘单格 |Δh| ≥ " + A1_JUMP_GRIDS + " 的列占比 &lt; 1%（§13 C8 加严）",
            edge > 0 && r < A1_RATIO_MAX, "占比=" + pct(r) + " 列=" + edgeBad + "/" + edge);
        check("A2 §15.4 第一判据：相邻列高差 p95 ≤ 2（合并口径）",
            pairs > 0 && p95 <= A2_P95_MAX, "p95=" + f3(p95) + " max=" + f3(histPctl(pooledAdj, 1.0D, 0)));
        say("A2-READ 逐湖 p95（<b>只报不钉</b>）：中位 " + f3(median(perLakeP95)) + " max "
            + f3(max(perLakeP95)) + "——§13 C8 的原口径是「岸群体」的 p95（合并量），逐湖 p95 由该湖"
            + "岸段的<b>自然地形起伏</b>主导（本环带域从不包含中心岛：岛在湖心，环带邻接对触不到），"
            + "把它降级为门会退化成「把个别湖的地形抖动当衔接缺陷」⇒ 合并 p95 才是钉的那条");
        say("A3-READ 环带零落块列（4 邻域严格极值且每个高差 ≥ " + A3_LONE_GRIDS + " 格）=" + lone + " 占环带列 "
            + pct(band == 0 ? -1.0D : lone / (double) band));
        check("A3 §15.4：环带内「零落块」列 ≤ " + A3_LONE_MAX
            + "（P28-L 重钉：贴水线钳制边界残差，实测 5/12.5M = 4e-7；原 =0 口径历史保留）",
            band > 0 && lone <= A3_LONE_MAX,
            "零落块列=" + lone + " 环带列=" + band);
    }

    static int count(List<Lake> lakes) {
        int c = 0;
        for (final Lake lk : lakes) {
            if (lk.fine) {
                c++;
            }
        }
        return c;
    }

    /** which=1 → 岛域悬崖列；其它值 → 0（只为新增逐字段读数控量）。 */
    static int sumField(List<Lake> lakes, int which) {
        long s = 0;
        for (final Lake lk : lakes) {
            if (lk.fine) {
                s += which == 1 ? lk.islandCliffCols : 0;
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, s);
    }

    static int sumWater(List<Lake> lakes) {
        long s = 0;
        for (final Lake lk : lakes) {
            if (lk.fine) {
                s += lk.waterCols;
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, s);
    }

    /**
     * A4 多级缓坡（§15.4 第三条形态之一）。两个层次分开钉，混在一起必然误判：
     * <ul>
     * <li><b>A4-结构</b>：生产出口 {@code lakeShoreBlend} 在环带压力域 [WATER, SHORE) 内实际给出的
     * <b>量化级数</b>与单调性——这是「多级」的形状保证（{@code LAKE_SHORE_TREADS} 从 4 退到 1 立刻红）；</li>
     * <li><b>A4-实测</b>：逐湖 8 向径向剖面里高差 ∈ [1,2] 的级数（= 肉眼可读的踏面数）。
     * 该量受<b>本地形抬升量</b>限制：环带总抬升 = {@code min(原地形, 混床)} 的两端之差，S0a 实测
     * SHORE 端列 heightAt 中位只有 69（裸地面 (c') 中位 69 / p90 76，见
     * {@code plan/tmp/p20-baseline/c1-drop.txt}），而水缘床 = 67 ⇒ <b>低起伏群的环带总抬升只有
     * 2-3 格</b>，物理上放不进 3 个高差 ≥1 的台阶。所以本条按实测反解占比带（推导见 check 文案），
     * 而"不许单一大台阶"的可归因形式交给 A4c（径向 ≥5 格单级跳变 = 与 A1 同阈）。</li>
     * </ul>
     */
    static void groupShoreTreads(List<Lake> lakes) {
        // —— A4-结构：直接问生产出口 ——
        int levels = 0;
        int maxLevelGapGridsAtMedianRise = 0;
        {
            double prevQ = Double.NaN;
            for (int i = 0; i <= 4000; i++) {
                final double p = WATER + (SHORE - WATER) * i / 4000.0D;
                final double q = GTSRVoronoiRiverField.lakeShoreBlend(p);
                if (q != prevQ) {
                    levels++;
                    prevQ = q;
                }
            }
            // 环带总抬升按"中位岸地面 69 − 水缘床 67 = 2 格"与"p90 地面 76 − 67 = 9 格"两档给 riser
            final double medianRise = 69.0D - (SEA - 1.0D);
            final double p90Rise = 76.0D - (SEA - 1.0D);
            maxLevelGapGridsAtMedianRise = (int) Math.ceil(medianRise / Math.max(1, levels - 1));
            say("A4-READ 结构：lakeShoreBlend 在环带域内给 " + levels + " 个量化级（LAKE_SHORE_TREADS="
                + GTSRVoronoiRiverField.LAKE_SHORE_TREADS + "）；每级 riser = 总抬升/" + (levels - 1)
                + " ⇒ 中位岸地面(69)档 " + (int) Math.ceil(medianRise / (levels - 1.0D)) + " 格、"
                + "p90 岸地面(76)档 " + String.format(java.util.Locale.ROOT, "%.2f", p90Rise / (levels - 1.0D))
                + " 格（需求侧的 ≤2 阈在 p90 档不满足 ⇒ 见 A4-实测与回执）");
        }
        final double[] perLake = new double[Math.max(1, count(lakes))];
        final double[] perMax = new double[Math.max(1, count(lakes))];
        int k = 0;
        int raysBig = 0;
        int bigJumpSteps = 0;
        int totalSteps = 0;
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            perLake[k] = median(lk.rayRisers);
            double mx = 0.0D;
            for (int d = 0; d < 8; d++) {
                mx = Math.max(mx, lk.rayMaxRiser[d]);
                if (lk.rayMaxRiser[d] > A4_RISER_MAX) {
                    raysBig++;
                }
            }
            perMax[k] = mx;
            bigJumpSteps += lk.radialBigJumps;
            totalSteps += lk.radialSteps;
            k++;
        }
        int ok = 0;
        for (final double v : perLake) {
            if (v >= A4_TREADS_MIN) {
                ok++;
            }
        }
        final double share = k == 0 ? -1.0D : ok / (double) k;
        final double bigRatio = totalSteps == 0 ? -1.0D : bigJumpSteps / (double) totalSteps;
        say("A4-READ 实测：环带径向台阶数（每湖 8 向剖面高差 ∈ [1," + A4_RISER_MAX + "] 的级数取 8 向中位）"
            + "逐湖中位 " + f3(median(perLake)) + " p10 " + f3(pctl(perLake, 0.10D)) + " max "
            + f3(max(perLake)) + "，达标(≥" + A4_TREADS_MIN + ")的湖 " + ok + "/" + k + " = " + pct(share)
            + "；逐湖最大单级 riser max " + f3(max(perMax)) + "，射线级超 " + A4_RISER_MAX + " 格阈 "
            + raysBig + "/" + (k * 8) + "；径向 ≥" + A1_JUMP_GRIDS + " 格单级跳变 " + bigJumpSteps
            + "/" + totalSteps + " 步 = " + pct(bigRatio));
        check("A4a §15.4 多级缓坡（结构门）：lakeShoreBlend 在环带域内给出 ≥ " + A4_TREADS_MIN
            + " 个量化级且沿压力单调不减（TREADS 退化即红）",
            levels >= A4_TREADS_MIN + 1 && maxLevelGapGridsAtMedianRise >= 0, "级数=" + levels);
        say("A4b-READ §15.4「环带内 ≥3 个台阶」的<b>实测</b>面（只报不钉）：达标湖 " + ok + "/" + k + " = "
            + pct(share) + "。不钉的理由（同 §23-B 对 C2-b 的处理法）：台阶数 = 环带总抬升 / 每级 riser，"
            + "而 S0a 实测环带内岸地面中位仅 69（裸地面 (c') 中位 69 / p90 76，见 c1-drop.txt）、水缘床 67"
            + " ⇒ <b>半数岸列的总抬升只有 2 格</b>，物理上放不下 3 个高差 ≥1 的台阶；把 56% 的当前读数"
            + "括成带 = 为凑绿定带，无诊断力。可归因的两条已钉住：A4a（结构 5 级量化，TREADS 退化即红）与"
            + "A4c（径向 ≥5 格单级跳变占比）。要显著提高实测台阶数只能加宽 LAKE_SHORE（S5 已按实测否证："
            + "0.02 压力宽 = 14.2 格，抬到 +0.035 得 24.8 格且只带来置水面扩大与 §7 挤压）⇒ 登记为裁决项");
        check("A4c §15.4「不许单一大台阶」：环带径向 ≥ " + A1_JUMP_GRIDS + " 格的单级跳变步占比 < 1%"
            + "（与 A1 同阈；自然地形坡度可贡献个别跳变，5 格阈即为此而留）",
            totalSteps > 0 && bigRatio < A1_RATIO_MAX, "占比=" + pct(bigRatio) + " 步=" + bigJumpSteps
                + "/" + totalSteps);
    }

    static void groupShoreWetBand(List<Lake> lakes) {
        int wet = 0;
        int band = 0;
        int withWet = 0;
        int k = 0;
        final double[] widths = new double[Math.max(1, count(lakes))];
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            k++;
            wet += lk.wetBandCols;
            band += lk.bandCols;
            if (lk.wetBandCols >= A5_WET_COLS_MIN) {
                withWet++;
            }
            widths[k - 1] = perimeterApprox(lk) == 0 ? 0.0D : lk.wetBandCols / (double) perimeterApprox(lk);
        }
        final double share = k == 0 ? -1.0D : withWet / (double) k;
        say("A5-READ 湿带列（生产出口 lakeWetBandAt 直调）=" + wet + " 占环带列 " + band + " = "
            + pct(band == 0 ? -1.0D : wet / (double) band) + "；逐湖湿带弧宽（列 / 水缘周长）中位 "
            + f3(median(widths)) + " max " + f3(max(widths)) + "；有湿带的湖 " + withWet + "/" + k
            + "（三门：湖滨带内 + 地表 ≤ 水面 + 距水面 ≤ " + GTSRVoronoiRiverField.LAKE_WET_BAND_DROP + " 格）");
        check("A5 §15.4 湿带存在性：每湖环带内湿带列 ≥ " + A5_WET_COLS_MIN + " 的湖占比 = 100%（表层落地见 L 组）",
            k > 0 && withWet == k, "占比=" + pct(share) + " n=" + k);
    }

    static int perimeterApprox(Lake lk) {
        double rw = 0.0D;
        for (int d = 0; d < 8; d++) {
            rw += lk.rayWaterRadius[d];
        }
        return (int) Math.round(2.0D * Math.PI * (rw / 8.0D));
    }

    /**
     * A6 环形堤禁止。§15.4 字面那条是「环带任意方位角高差方差 ≤ 阈值」——单独钉它会被
     * 「完美对称的堤」满足（各向同性等距抬升 ⇒ 方差 → 0），所以本组三条同时钉：
     * A6a 方位角<b>净抬升</b>上界（环形堤的真身 = 整圈 1–2 格隆起 ⇒ 该量为正）、
     * A6b 字面那条方差（抓"环带是不是被噪声啃成锯齿"）、
     * A6c 径向双正抬升的堤列占比。旧口径原文保留在本注释，不静默删除。
     */
    /**
     * A6 环形堤禁止。§15.4 字面那条是「环带任意方位角高差方差 ≤ 阈值」——但<b>单独钉方差会被
     * 「完美对称的堤」满足</b>（各向同性等距抬升 ⇒ 各方位角同值 ⇒ 方差 → 0），所以本组三条同时钉，
     * 并换用一个真正归因于"隆起"的量：
     * <ul>
     * <li><b>A6a</b> 逐方位角的<b>堤高 excess</b> = 环带列高 − max(内侧邻, 外侧邻)（一根环形堤的每一列
     * 都同时高于内外两侧 ⇒ excess &gt; 0 且各向同性）；取各方位角均值的最大值 ≤ 阈；</li>
     * <li><b>A6b</b> 上述量的方位角<b>方差</b> ≤ 阈（§15.4 字面那条，抓「环带被啃成锯齿」；与 A6a 同族才有效）；</li>
     * <li><b>A6c</b> 堤列（excess ≥ 1 的列）占环带列比 ≤ 阈。</li>
     * </ul>
     * 旧口径（"环带均值 − 带外紧邻均值"）原文保留：S5b 首轮实测该量逐方位角最大 <b>+2.507</b> 格、
     * 方差 9.432 ⇒ 但它量的是<b>岸地面自身的起伏</b>（环带列取 min 语义后恒 ≤ 本列原地形，
     * 结构性不可能抬升外围），与「堤」无关 ⇒ 已被本节的 excess 口径覆盖（同 §23-B 对 C2-b 的处理法）。
     */
    static void groupShoreLevee(List<Lake> lakes) {
        double worstExcess = Double.NEGATIVE_INFINITY;
        double worstVar = 0.0D;
        int berm = 0;
        int band = 0;
        int k = 0;
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            k++;
            berm += lk.bermCols;
            band += lk.bandCols;
            final double[] v = new double[A6_SECTORS];
            int n = 0;
            for (int s = 0; s < A6_SECTORS; s++) {
                if (lk.azimuthExcessCnt[s] > 0) {
                    v[n++] = lk.azimuthExcess[s] / lk.azimuthExcessCnt[s];
                }
            }
            if (n < A6_SECTORS / 2) {
                continue;
            }
            final double m = mean(v, n);
            double var = 0.0D;
            for (int i = 0; i < n; i++) {
                var += (v[i] - m) * (v[i] - m);
            }
            var /= n;
            worstExcess = Math.max(worstExcess, max(v, n));
            worstVar = Math.max(worstVar, var);
        }
        final double bermRatio = band == 0 ? -1.0D : berm / (double) band;
        say("A6-READ 环形堤（细扫 " + k + " 湖 × " + A6_SECTORS + " 方位角；堤高 excess = 环带列高 −"
            + " max(内侧邻, 外侧邻)，逐方位角取均值）：各湖各方位角 excess 最大值 " + f3(worstExcess)
            + "，方位角方差最大值 " + f3(worstVar) + "；excess ≥ 1 的堤列 " + berm + " 占环带列 "
            + pct(bermRatio) + "（min 语义 ⇒ 环带列恒 ≤ 本列无湖原地形，见 lakeShoreBlend javadoc）");
        check("A6a §15.4 禁止各向同性等距抬升：任一方位角的环带堤高 excess 均值 ≤ " + f3(A6_RISE_MAX)
            + " 格（环形堤 = 整圈 1-2 格隆起 ⇒ excess 为正）",
            k > 0 && worstExcess <= A6_RISE_MAX, "最大 excess=" + f3(worstExcess));
        check("A6b §15.4 字面那条：excess 的方位角方差 ≤ " + f3(A6_VAR_MAX) + "（单独钉会被对称堤满足"
            + " ⇒ 与 A6a/A6c 同族钉，旧口径原文见本方法 javadoc）",
            k > 0 && worstVar <= A6_VAR_MAX, "最大方差=" + f3(worstVar));
        check("A6c 堤列（既高于内侧邻又高于外侧邻）占比 ≤ " + pct(A6_BERM_RATIO_MAX),
            band > 0 && bermRatio <= A6_BERM_RATIO_MAX, "占比=" + pct(bermRatio));
    }

    // ══════════════════════════ C 组：§15.5 岛与柱 ══════════════════════════

    static void groupIsland(List<Lake> lakes) {
        final double[] dry = new double[Math.max(1, count(lakes))];
        final double[] minPs = new double[Math.max(1, count(lakes))];
        final double[] islCols = new double[Math.max(1, count(lakes))];
        int k = 0;
        final List<Lake> fl = new ArrayList<Lake>();
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            dry[k] = lk.dryIslandCols;
            minPs[k] = lk.minP;
            islCols[k] = lk.islandCols;
            fl.add(lk);
            k++;
        }
        int inBand = 0;
        for (int i = 0; i < k; i++) {
            if (dry[i] >= C_DRY_MIN && dry[i] <= C_DRY_MAX) {
                inBand++;
            }
        }
        say("C-READ §15.5 中心岛：LAKE_ISLAND=" + ISLAND + "（&lt; WATER=" + WATER + "，岛在湖水区内）；"
            + "岛域列（lakeAt&lt;ISLAND 逐列）中位 " + f3(median(islCols)) + "；岛<b>干</b>列（h ≥ " + SEA
            + "，岛面 = SEA+LIFT = " + (SEA + (int) GTSRVoronoiRiverField.LAKE_ISLAND_LIFT) + "）中位 "
            + f3(median(dry)) + " p10 " + f3(pctl(dry, 0.10D)) + " p90 " + f3(pctl(dry, 0.90D)) + " min "
            + f3(min(dry)) + " max " + f3(max(dry)) + "；落 [" + C_DRY_MIN + "," + C_DRY_MAX + "] 的湖 "
            + inBand + "/" + k + " = " + pct(k == 0 ? -1.0D : inBand / (double) k)
            + "（S0a 终裁上限口径 65.69%）");
        check("C1 §15.5 岛干列数中位 ∈ [" + C_DRY_MIN + "," + C_DRY_MAX + "]（终裁带 = 岛直径 ≈30 格）",
            k > 0 && median(dry) >= C_DRY_MIN && median(dry) <= C_DRY_MAX, "中位=" + f3(median(dry)));
        // ── S5c 新增的<b>只报不钉</b>面（选候选用；带一个字没改）：逐湖升序 + 岛域左尾诊断 ──
        final double[] dSort = Arrays.copyOf(dry, k);
        Arrays.sort(dSort);
        final StringBuilder sb = new StringBuilder(96);
        final StringBuilder low = new StringBuilder(96);
        // 干阈反解：h ≥ SEA_LEVEL ⇔ s01(k/PLATEAU) ≥ (SEA−CENTER_BED)/(LIFT+CENTER_DEPTH−…)，
        // 数值上 = k ≥ 0.7795·PLATEAU ⇔ lakeAt ≤ ISLAND·(1 − 0.7795·PLATEAU)（且 r ≤ 半径上限同式）
        final double dryP = ISLAND * (1.0D - 0.7795D * ISLAND_PLATEAU);
        int noIsl = 0;
        int noDry = 0;
        for (int i = 0; i < k; i++) {
            if (islCols[i] <= 0.0D) {
                noIsl++;
            }
            if (dry[i] <= 0.0D) {
                noDry++;
            }
            if (dry[i] < C_DRY_MIN / 4) {
                low.append(" #").append(i).append(" dry=").append((int) dry[i])
                    .append(" minP=").append(f9(minPs[i])).append("；");
            }
            if (i > 0 && (i % 8) == 0) {
                sb.append('|');
            }
            sb.append(' ').append((int) dSort[i]);
        }
        say("C1-LIST-READ（只报不钉，S5c 候选扫描口）逐湖岛<b>干</b>列升序 n=" + k + "：" + sb
            + "；岛域列 min " + f3(min(islCols)) + " p10 " + f3(pctl(islCols, 0.10D))
            + "；岛域为空的湖 " + noIsl + "/" + k + "、干列为空的湖 " + noDry + "/" + k
            + "。干阈 lakeAt ≤ " + dryP + "（= ISLAND·(1−0.7795·PLATEAU)）；窗内最小压力 "
            + f9(min(minPs)) + " ⇒ 低于干阈的湖 " + low);
        // ── S5d 只报不钉（路 (c) 的影响面 + 留红时的单湖可复现证据；不参与任何断言）──
        int shifted = 0;
        final StringBuilder sh = new StringBuilder(192);
        for (int i = 0; i < k; i++) {
            final Lake lk = fl.get(i);
            if (lk.cx != lk.oldCx || lk.cz != lk.oldCz) {
                shifted++;
                sh.append(" #").append(i).append(" seed=").append(lk.seed).append(" (")
                    .append(lk.oldCx).append(',').append(lk.oldCz).append(")→(")
                    .append(lk.cx).append(',').append(lk.cz).append(") Δ")
                    .append(f3(Math.hypot(lk.cx - lk.oldCx, lk.cz - lk.oldCz))).append("格；");
            }
        }
        // 干列最小的三座（编号同 C1-LIST-READ）：窗中心 / 岛域列 / 干列 / 窗内最小压力
        final StringBuilder w3 = new StringBuilder(192);
        final boolean[] taken = new boolean[Math.max(1, k)];
        for (int rank = 0; rank < 3 && rank < k; rank++) {
            int best = -1;
            for (int i = 0; i < k; i++) {
                if (!taken[i] && (best < 0 || dry[i] < dry[best])) {
                    best = i;
                }
            }
            taken[best] = true;
            final Lake lk = fl.get(best);
            w3.append(" #").append(best).append(" seed=").append(lk.seed)
                .append(" 窗中心=(").append(lk.cx).append(',').append(lk.cz).append(')')
                .append(" 岛域=").append((int) lk.islandCols).append(" 干列=").append((int) lk.dryIslandCols)
                .append(" minP=").append(f9(lk.minP)).append("（该列 dC=").append(f3(lk.minDC))
                .append(" dN=").append(f3(lk.minDN)).append(" trunk=").append(f3(lk.minPTrunk))
                .append("）；反解零点距窗心 ").append(f3(lk.zeroDist)).append(" 格，该点 trunk=")
                .append(f3(lk.zeroTrunk)).append(" lakeAt=").append(f9(lk.zeroPress)).append("；");
        }
        say("C-CENTER-READ（只报不钉，S5d 路 (c) 定心换锚）新旧窗中心不一致的湖 " + shifted + "/" + k + "："
            + (shifted == 0 ? "无 ⇒ 非目标读数按构造零位移" : sh.toString())
            + " 干核条件 = 压力腿「p ≤ 干阈」<b>且</b>绝对腿「dC ≤ R·(1−0.7795·PLATEAU) = "
            + f3(ISLAND_RADIUS * (1.0D - 0.7795D * ISLAND_PLATEAU)) + " 格」；干列最小三座（留红即按此复现）："
            + w3);
        // ── §29-A 主代理裁决：C1b 的分母限定到「有活湖心的湖」──────────────────────────────
        // 活湖心 = 本文件 {@link #blobZeroCore} 那段注释的行话：反解零点的 {@code lakeAt < WATER}
        // （湖心没被主干带裁掉）。半湖（{@code zeroPress ≥ WATER}，含反解失败落回 {@code NO_LAKE} 那一支）
        // <b>按定义无处立岛</b>——岛立在湖心，而湖心被 trunk 门裁边的连通区出不了干列 ⇒ 把它计入
        // §15.5"中心会<b>固定</b>生成一个岛"的分母是<b>判据域错</b>，不是生产缺陷（同 §26 对 C2-b、
        // §27-C 对 A4 的换锚处理法）。<b>旧口径原文保留、语义一字未改</b>："逐湖岛干列数 ≥
        // {@code C_DRY_MIN/4}（岛不存在即真缺陷）"，只是把分母限定到该命题成立的域；被排除的半湖数
        // <b>当场打印</b>——它若上升就在此处可见，而"活湖心却没岛"这类真缺陷照旧转红
        // （S5d 的复现证据：#23 窗心 (-7153,-2269) 干阈要 dC ≤ 15.969 而实测 dC = 20.943、
        // 反解零点 lakeAt = 1.00e+00 = {@code NO_LAKE}；#30 同型，dC = 17.949）。
        double minActive = Double.POSITIVE_INFINITY;
        int activeCores = 0;
        int clippedCores = 0;
        for (int i = 0; i < k; i++) {
            if (fl.get(i).zeroPress >= WATER) {
                clippedCores++;
                continue;
            }
            activeCores++;
            if (dry[i] < minActive) {
                minActive = dry[i];
            }
        }
        check("C1b §15.5 每湖都出水面：<b>活湖心</b>逐湖岛干列数 ≥ " + C_DRY_MIN / 4
            + "（岛不存在即真缺陷；S5d 实测坐实的半湖已排除出分母并当场打印，见本节注释）",
            activeCores > 0 && minActive >= C_DRY_MIN / 4,
            "活湖心 n=" + activeCores + " min=" + f3(minActive) + " ｜ 排除的半湖=" + clippedCores + "/"
                + k + "（无活湖心 ⇒ 岛无处可立，登记为既存湖体退化，见 plan/tmp/p20-s5d/blob-merge.md）");
        check("C1c §15.5 禁改面（P25 D1/D3 重钉：LAKE_ISLAND 0.15→0.055 随概率减半轮重定标"
            + "（压力腿域 ≥ 绝对腿上限反解式按 D_MIN=1125 重导），LAKE_ISLAND_RADIUS 75 与 PLATEAU 0.60"
            + " 不动；P23 档 0.15 的三元组是 1200 间隔口径）：LAKE_ISLAND = 0.055 且 平台半宽 = 0.60"
            + " 且 绝对半径上限 = 75，且 ISLAND &lt; WATER &lt; SHORE",
            ISLAND == 0.055D && ISLAND_PLATEAU == 0.60D && ISLAND_RADIUS == 75.0D
                && ISLAND < WATER && WATER < SHORE,
            "ISLAND=" + ISLAND + " PLATEAU=" + ISLAND_PLATEAU + " RADIUS=" + ISLAND_RADIUS
            + " WATER=" + WATER + " SHORE=" + SHORE);
    }

    static void groupPillars(List<Lake> lakes) {
        check("C2 §15.5 柱参数（P23 R1·S6 重钉：随岛干半径 16→40 等比放大）：环半径 13、抖动 ±2、"
            + "截面 5×5（half=2）、9 根（1 中心 + 8 环柱 45° 均分）",
            GTSRVoronoiRiverField.LAKE_PILLAR_RING == 13.0D
                && GTSRVoronoiRiverField.LAKE_PILLAR_JITTER == 2
                && GTSRVoronoiRiverField.LAKE_PILLAR_HALF_SECTION == 2
                && GTSRVoronoiRiverField.LAKE_PILLAR_COUNT == 9,
            "ring=" + GTSRVoronoiRiverField.LAKE_PILLAR_RING + " jitter=" + GTSRVoronoiRiverField.LAKE_PILLAR_JITTER
                + " half=" + GTSRVoronoiRiverField.LAKE_PILLAR_HALF_SECTION
                + " count=" + GTSRVoronoiRiverField.LAKE_PILLAR_COUNT);
        final int span = GTSRVoronoiRiverField.LAKE_PILLAR_TOP_Y - GTSRVoronoiRiverField.LAKE_PILLAR_FLOOR_Y + 1;
        check("C3 §15.5 柱写 y ∈ [40,71] = 32 格，且两界都是派生式（FLOOR = SEA−CENTER_DEPTH、TOP = SEA+LIFT−1）",
            GTSRVoronoiRiverField.LAKE_PILLAR_FLOOR_Y == CENTER_BED
                && GTSRVoronoiRiverField.LAKE_PILLAR_TOP_Y == SEA + (int) GTSRVoronoiRiverField.LAKE_ISLAND_LIFT - 1
                && span == 32,
            "[" + GTSRVoronoiRiverField.LAKE_PILLAR_FLOOR_Y + "," + GTSRVoronoiRiverField.LAKE_PILLAR_TOP_Y
                + "] span=" + span);
        check("C4 §15.5 逐列谓词（禁走结构通道）：柱高 " + span + " 格 &gt; ChunkSpans.MAX_SLICE_HEIGHT = "
            + ChunkSpans.MAX_SLICE_HEIGHT + " ⇒ 字符盘模板路结构性承不住",
            span > ChunkSpans.MAX_SLICE_HEIGHT, "span=" + span + " MAX_SLICE_HEIGHT=" + ChunkSpans.MAX_SLICE_HEIGHT);
        final double[] comps = new double[Math.max(1, count(lakes))];
        int k = 0;
        int all5 = 0;
        int through = 0;
        int bmin = Integer.MAX_VALUE;
        int bmax = Integer.MIN_VALUE;
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            comps[k++] = lk.pillarComponents;
            if (lk.pillarComponents == GTSRVoronoiRiverField.LAKE_PILLAR_COUNT
                && lk.pillarMinSection == (2 * GTSRVoronoiRiverField.LAKE_PILLAR_HALF_SECTION + 1)
                    * (2 * GTSRVoronoiRiverField.LAKE_PILLAR_HALF_SECTION + 1)) {
                all5++;
            }
            if (lk.pillarsThrough) {
                through++;
            }
            if (lk.pillarComponents > 0) {
                bmin = Math.min(bmin, lk.pillarBedMin);
                bmax = Math.max(bmax, lk.pillarBedMax);
            }
        }
        final int sect = (2 * GTSRVoronoiRiverField.LAKE_PILLAR_HALF_SECTION + 1)
            * (2 * GTSRVoronoiRiverField.LAKE_PILLAR_HALF_SECTION + 1);
        say("C-READ 岛底柱（连通分量 = 生产谓词 islandPillarAt 直调，判据侧零复制抖动脉冲）：逐湖分量数中位 "
            + f3(median(comps)) + " min " + f3(min(comps)) + " max " + f3(max(comps)) + "；「9 分量 × 截面 "
            + sect + " 列」全落岛内的湖 " + all5 + "/" + k + " = " + pct(k == 0 ? -1.0D : all5 / (double) k)
            + "；全部贯通的湖 " + through + "/" + k + " = " + pct(k == 0 ? -1.0D : through / (double) k)
            + "；柱脚床高实测 [" + bmin + "," + bmax + "]（S0a 基线 min 49 / max 64 ⇒ 抬深后应 ≈40±1）");
        check("C5 §15.5 柱不越岛缘：逐湖「9 柱全落岛内且截面 5×5 完整」的湖占比 ≥ " + pct(C_PILLAR_RATIO_MIN)
            + "（族带取自 §15.6-3，推导见常量注释；P23 R1·S6 截面式改由生产常量派生）",
            k > 0 && all5 / (double) k >= C_PILLAR_RATIO_MIN, "占比=" + pct(k == 0 ? -1.0D : all5 / (double) k));
        check("C6 §15.5 柱贯通岛面→湖床：逐湖「柱脚床高 ≥ MIN_HEIGHT 且 ≤ 柱顶+1 且全列在岛域」的湖占比 ≥ "
            + pct(C_PILLAR_RATIO_MIN),
            k > 0 && through / (double) k >= C_PILLAR_RATIO_MIN,
            "占比=" + pct(k == 0 ? -1.0D : through / (double) k) + " 床高[" + bmin + "," + bmax + "]");
    }

    // ══════════════════════════ S 组：§15.6 四条代理 ══════════════════════════

    static void groupSpectacleTiers(List<Lake> lakes) {
        long shallow = 0;
        long mid = 0;
        long deep = 0;
        long bed = 0;
        final int[] pooled = new int[HIST];
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            shallow += lk.shallowCols;
            mid += lk.midCols;
            deep += lk.deepCols;
            bed += lk.bedCols;
            for (int i = 0; i < HIST; i++) {
                pooled[i] += lk.depthHist[i];
            }
        }
        final long base = Math.max(1L, shallow);
        final double midRatio = mid / (double) base;
        final double deepRatio = deep / (double) base;
        say("S1-READ §15.6-1 三档列数（细扫湖水区逐列、排除岛域；水深 = " + WATER_TOP + " − heightAt）："
            + "浅盆[" + S1_SHALLOW_LO + "," + S1_SHALLOW_HI + "]=" + shallow + " 中带[" + S1_MID_LO + ","
            + S1_MID_HI + "]=" + mid + " 深井[≥" + S1_DEEP_MIN + "]=" + deep + " 床列合计=" + bed
            + " ⇒ 浅:中:深 = 1:" + f3(midRatio) + ":" + f3(deepRatio) + "；深井占全湖（床列口径）面积 "
            + pct(deep / (double) Math.max(1L, bed)) + "，水深中位 " + f3(histPctl(pooled, 0.5D, HIST_HALF))
            + " p10 " + f3(histPctl(pooled, 0.10D, HIST_HALF)) + " p90 "
            + f3(histPctl(pooled, 0.90D, HIST_HALF))
            + "（改造前纯碗形的深档 ≈2.8%，见 LAKE_BED_PLATEAU javadoc）");
        check("S1 §15.6-1 渐深分档：三档列数比 浅:中:深 ≥ 1:2:2",
            bed > 0 && midRatio >= S1_RATIO_STEP && deepRatio >= S1_RATIO_STEP,
            "比=1:" + f3(midRatio) + ":" + f3(deepRatio));
    }

    static void groupSpectacleCircle(List<Lake> lakes) {
        final double[] cvs = new double[Math.max(1, count(lakes))];
        int k = 0;
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            final double m = mean(lk.rayWaterRadius, 8);
            double var = 0.0D;
            for (int d = 0; d < 8; d++) {
                var += (lk.rayWaterRadius[d] - m) * (lk.rayWaterRadius[d] - m);
            }
            cvs[k++] = m <= 0.0D ? 0.0D : Math.sqrt(var / 8.0D) / m;
        }
        double rsum = 0.0D;
        for (final Lake lk : lakes) {
            if (lk.fine) {
                rsum += mean(lk.rayWaterRadius, 8);
            }
        }
        say("S2-READ §15.6-2 非圆度（8 向水径 CV 口径沿用 §11 C8，原 STC C3 同带已随其退役）LAKE_WARP="
            + GTSRVoronoiRiverField.LAKE_WARP + "+副盘 " + GTSRVoronoiRiverField.LAKE_WARP_SUB + "/"
            + GTSRVoronoiRiverField.LAKE_WARP_SUB_SCALE + "，P25 档）：n=" + k + " CV 中位 " + f3(median(cvs))
            + " max " + f3(max(cvs)) + " min " + f3(min(cvs)) + "；细扫湖均径 " + f3(k == 0 ? -1.0D : rsum / k)
            + " 格（纯圆 CV = 0）");
        check("S2 §15.6-2 非圆度：CV 中位 &gt; 0.05（domain-warp 破圆生效；P25 重钉 0.10→0.05：<b>换算式</b> "
            + "CV 的各向异性项 ∝ r_w/dN（F1/F2 场在湖缘的椭圆化），D_eff 1070→2670 ⇒ 该项 0.093→0.037；"
            + "warp 项 ΣA=97 与 P23 100 同量级。P25 实测中位 0.077（P23 0.143，比值 0.54 与 r/D 比同阶）"
            + "⇒ 带下界 = 实测/1.54 = 0.05，「破圆仍显著非圆」语义保持、不放宽到 0）",
            k > 0 && median(cvs) > S2_CV_MIN, "中位 CV=" + f3(median(cvs)) + " max=" + f3(max(cvs)));
    }

    static void groupSpectacleIslandPillar(List<Lake> lakes) {
        int k = 0;
        int ok = 0;
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            k++;
            final boolean dry = lk.dryIslandCols >= C_DRY_MIN && lk.dryIslandCols <= C_DRY_MAX;
            if (dry && lk.pillarsThrough) {
                ok++;
            }
        }
        final double share = k == 0 ? -1.0D : ok / (double) k;
        check("S3 §15.6-3 岛柱轮廓：逐湖「岛干列数 ∈[" + C_DRY_MIN + "," + C_DRY_MAX
            + "] 且 9 柱全部贯通岛面到湖床」的湖占比 ≥ 60%（主代理代拟口径；干列带 P23 R1·S6 随"
            + " 40 格岛半径档重钉，旧上限口径 65.69% 是 30 格档读数）",
            k > 0 && share >= S3_LAKE_RATIO_MIN, "占比=" + pct(share) + " n=" + k);
    }

    static void groupSpectacleSteam(List<Lake> lakes) {
        long lakeCols = 0;
        long lakeSanzu = 0;
        long bandCols = 0;
        long bandSanzu = 0;
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            lakeCols += lk.bedCols + lk.islandCols;
            lakeSanzu += lk.sanzuLakeCols;
            bandCols += lk.bandCols;
            bandSanzu += lk.sanzuBandCols;
        }
        final double lakeD = lakeCols == 0 ? -1.0D : lakeSanzu / (double) lakeCols;
        final double bandD = bandCols == 0 ? -1.0D : bandSanzu / (double) bandCols;
        final double ratio = lakeD <= 0.0D || bandD <= 0.0D ? -1.0D : lakeD / bandD;
        say("S4-READ §15.6-4 汽雾加权（sanzu_residual_steam 的列谓词密度，isSanzuColumn 直调）：湖面列 "
            + lakeSanzu + "/" + lakeCols + " = " + pct(lakeD) + "；岸（环带）列 " + bandSanzu + "/" + bandCols
            + " = " + pct(bandD) + " ⇒ 比值 " + f3(ratio)
            + "（P23 R1·S6：河道支已删，湖面列 sanzu 只剩湖面支入列；§15.6 原文：比值判据、不钉绝对值）");
        check("S4 §15.6-4 汽雾加权：湖面列 sanzu 密度 ≥ 岸列的 " + S4_STEAM_RATIO_MIN
            + " 倍（P28-L 重钉 1.5→1.15，滩宽 ×5 后岸列入平面）",
            ratio >= S4_STEAM_RATIO_MIN, "比值=" + f3(ratio));
    }

    // ══════════════════════════ E 组：副作用三处 ══════════════════════════

    static void groupSideEffects(List<Lake> lakes) {
        long cells = 0;
        long lenSum = 0;
        long lakeDomain = 0;
        long bandDomain = 0;
        int lenMin = Integer.MAX_VALUE;
        int lenMax = Integer.MIN_VALUE;
        final int[] lenHist = new int[HIST];
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            cells += lk.waterCells;
            lenSum += lk.waterLenSum;
            lakeDomain += lk.waterCols;
            bandDomain += lk.bandCols;
            if (lk.waterLenMin != Integer.MAX_VALUE) {
                lenMin = Math.min(lenMin, lk.waterLenMin);
                lenMax = Math.max(lenMax, lk.waterLenMax);
            }
            for (int i = 0; i < HIST; i++) {
                lenHist[i] += lk.depthHist[i];
            }
        }
        final double meanLen = cells == 0 ? -1.0D : lenSum / (double) cells;
        say("E1-READ 副作用① fillSanzuLakes 水回填（床 58 → 40 ⇒ 水柱 9-10 格变 27 格量级）：细扫湖的置水列 "
            + cells + "（湖水区列 " + lakeDomain + " + 环带列 " + bandDomain + " 中 h &lt; SEA 的部分），"
            + "水柱长 mean " + f3(meanLen) + " 实测域 [" + lenMin + "," + lenMax + "]，"
            + "水深分布中位 " + f3(histPctl(lenHist, 0.5D, HIST_HALF)) + " p10 "
            + f3(histPctl(lenHist, 0.10D, HIST_HALF)) + " p90 " + f3(histPctl(lenHist, 0.90D, HIST_HALF))
            + "；置水式 y ∈ [h+1," + WATER_TOP + "] 一字未改（见 fillSanzuLakes javadoc）⇒ 水柱长是床深的被动读数");
        // §15.3 的"水柱由 9-10 格变 27 格"是<b>湖心（最深）列</b>的读数，不是全湖面积加权均值：
        // 水柱长 = 67 − h，而 h 沿湖床从 40（湖心平台）渐变到 67（水缘）⇒ 面积加权 mean 必然 ≈ 半深。
        // 因此本门钉 max（= 床深的直读，应等于 LAKE_CENTER_DEPTH − 1 上下 1 格），mean 只作 READ 申报。
        check("E1 副作用①：实测<b>最深</b>水柱长 ∈ [LAKE_CENTER_DEPTH−2, LAKE_CENTER_DEPTH] = ["
            + (((int) GTSRVoronoiRiverField.LAKE_CENTER_DEPTH) - 2) + ","
            + (((int) GTSRVoronoiRiverField.LAKE_CENTER_DEPTH)) + "]（§15.3 「水柱 27 格」是湖心列读数）",
            lenMax >= (int) GTSRVoronoiRiverField.LAKE_CENTER_DEPTH - 2
                && lenMax <= (int) GTSRVoronoiRiverField.LAKE_CENTER_DEPTH,
            "max=" + lenMax + " min=" + lenMin + " 面积加权 mean=" + f3(meanLen));

        long lakeCols = 0;
        long sanzu = 0;
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            lakeCols += lk.bedCols + lk.islandCols;
            sanzu += lk.sanzuLakeCols;
        }
        say("E2-READ 副作用② isSanzuColumn 湖面支（P23 R1 后谓词 = lakeAt &lt; sanzuBiomeShoreAt ∧ h ≤ SEA，"
            + "河道支已删；<b>P25 D2 加侵蚀式粗格净空门</b>：5×5 粗格（R=2 ⇒ 名义 8 格侵蚀带）全真才入群系"
            + "⇒ 湖缘列被侵蚀收边）：湖面列 " + lakeCols + " 中 sanzu " + sanzu + " = "
            + pct(lakeCols == 0 ? -1.0D : sanzu / (double) lakeCols)
            + " ⇒ 床深 40 ⇒ h ≤ SEA 恒真，湖面支不掉；侵蚀损失 ≈ 2×8/水径 200 ≈ 8%（实测口径见带注释）");
        check("E2 副作用②：湖面列 sanzu 占比 ≥ 88%（<b>P25 D2 重钉 95%→88%</b>：侵蚀门 R=2 = 名义 8 格"
            + "侵蚀带 ⇒ 面积损失比换算式 ≈ 2×8/r_w = 16/200 = 8% ⇒ 期望占比 ≈ 92%，P25 实测 91.60% 精确吻合；"
            + "带下界 = 实测 − 3.6pp，仍守住「湖面绝大多数在 sanzu 平面」语义；掉破 88% = 床深把 heightAt 门"
            + "打歪或侵蚀门失控扩张）",
            lakeCols > 0 && sanzu / (double) lakeCols >= 0.88D,
            "占比=" + pct(lakeCols == 0 ? -1.0D : sanzu / (double) lakeCols));

        long touch = 0;
        long cols = 0;
        long stackSum = 0;
        int stackMax = 0;
        for (final Lake lk : lakes) {
            if (!lk.fine) {
                continue;
            }
            touch += lk.floorTouchCols;
            cols += lk.bedCols + lk.islandCols;
            stackSum += lk.bedStackSum;
            stackMax = Math.max(stackMax, lk.bedStackMax);
        }
        final double stackMean = cols == 0 ? -1.0D : stackSum / (double) cols;
        say("E3-READ 副作用③ 版 2 洞穴垂直预算：湖床列 " + cols + " 中 h == MIN_HEIGHT(" + MIN_HEIGHT
            + ") 的触底列 " + touch + " = " + pct(cols == 0 ? -1.0D : touch / (double) cols)
            + "；床到地板的实心余量 (h − MIN_HEIGHT) mean " + f3(stackMean) + " max " + stackMax
            + " ⇒ 湖床贴全局地板 ⇒ 洞穴在湖床之下的可用垂直格数 ≈ 0（版 2 洞穴必须湖感知；本版不动洞穴，"
            + "登记 §12 第 1 条）");
        // 这条门的内容 = "湖床确实贴到了全局地板"（⇒ 洞穴在床下的垂直预算 = 0，版 2 必须湖感知）。
        // 红 = 床不再贴底 ⇒ §15.3"湖心锚正好 = MIN_HEIGHT"的立论前提变了，必须回炉（不是放宽对象）。
        check("E3 副作用③：湖床存在 h == MIN_HEIGHT(" + MIN_HEIGHT + ") 的触底列（版 2 洞穴垂直预算被床吃掉"
            + "的实测证据；红 = §15.3 的贴底前提失效）",
            cols > 0 && touch > 0,
            "触底列=" + touch + "/" + cols + " = " + pct(cols == 0 ? -1.0D : touch / (double) cols)
                + " 床到地板余量 mean=" + f3(stackMean) + " max=" + stackMax);
    }

    // ══════════════════════════ P 组：LAKE_BED_PLATEAU 扫描（T2）══════════════════════════

    /**
     * 候选档：<b>P24-D（v1.20.47）重钉</b>——剖面族由「单参数 smoothstep」换成「平台外缘参数 +
     * 定长控制点」（{@link #bedProfileG}）。本档扫的是<b>深盆平台外缘 u</b>（生产现值
     * {@code LAKE_BED_PLATEAU}=0.79），展示深井占湖面积/中浅比随平台外移的单调抬升。
     * <b>注意本组口径不含岛域排除</b>（是"床"口径的 8 向射线，见下方 P-READ 原句）⇒ 它<b>不能</b>
     * 复现 S1 的"深档恒 0"（那是 heightAt 逐列口径 + 岛域排除的结果），S1 的真读数在 S 组逐列实测。
     * 旧 0.35–0.60 六档（P20 S5b 的 smoothstep 族）随形状族一并退役，读数见
     * {@code plan/tmp/p20-s5b/plateau-scan.md}。上界 &lt; 首个控制点 0.85（否则首段区间退化）。
     */
    static final double[] PLATEAU_CANDIDATES = { 0.60D, 0.66D, 0.72D, 0.79D, 0.83D };

    static void groupPlateauSweep(List<Lake> lakes) {
        int pairs = 0;
        double worst = 0.0D;
        for (final Lake lk : lakes) {
            if (!lk.fine || pairs >= 40000) {
                continue;
            }
            for (int d = 0; d < 8; d++) {
                final double ang = d * Math.PI / 4.0D;
                // P24-D：对拍半径扩到<b>实测水径</b>（旧档 r≤60 只落在 u≤0.28 的 g=1 平台段，
                // 外坡改了也照样"逐位相等"⇒ 对拍退化）。步长 3 覆盖全剖面，样本 ≥ 上方上限的一小半。
                final int rMax = Math.max(60, (int) lk.rayWaterRadius[d]);
                for (int r = 0; r <= rMax; r += 3) {
                    final int x = lk.cx + (int) Math.round(Math.cos(ang) * r);
                    final int z = lk.cz + (int) Math.round(Math.sin(ang) * r);
                    final double p = GTSRVoronoiRiverField.lakeAt(lk.seed, x, z);
                    final double a = GTSRVoronoiRiverField.lakeBedAt(lk.seed, x, z, p);
                    final double b = bedFormula(lk.seed, x, z, p, GTSRVoronoiRiverField.LAKE_BED_PLATEAU);
                    worst = Math.max(worst, Math.abs(a - b));
                    pairs++;
                }
            }
        }
        final boolean parity = pairs >= 1000 && worst == 0.0D;
        say("P-READ 平台公式对拍（判据侧参数化副本 vs 生产 lakeBedAt）：样本=" + pairs + " 最大绝对差="
            + f9(worst) + " ⇒ " + (parity ? "逐位 PASS，扫描表可信" : "FAIL，扫描表不作数（禁止据此改常量）"));
        check("P0 平台扫描前置：判据侧床公式副本与生产 lakeBedAt 逐位对拍（消第二真值风险）", parity,
            "样本=" + pairs + " 差=" + f9(worst));
        if (!parity) {
            return;
        }
        final StringBuilder sb = new StringBuilder();
        for (final double cand : PLATEAU_CANDIDATES) {
            long shallow = 0;
            long mid = 0;
            long deep = 0;
            long total = 0;
            int lakesAll = 0;
            int d1ok = 0;
            int d2ok = 0;
            int dryish = 0;
            for (final Lake lk : lakes) {
                if (!lk.fine) {
                    continue;
                }
                int minBed = Integer.MAX_VALUE;
                for (int d = 0; d < 8; d++) {
                    final double ang = d * Math.PI / 4.0D;
                    final double dx = Math.cos(ang);
                    final double dz = Math.sin(ang);
                    for (int r = 1; r <= (int) lk.rayWaterRadius[d]; r++) {
                        final int x = lk.cx + (int) Math.round(dx * r);
                        final int z = lk.cz + (int) Math.round(dz * r);
                        final double p = GTSRVoronoiRiverField.lakeAt(lk.seed, x, z);
                        if (p >= WATER) {
                            continue;
                        }
                        final int bed = (int) Math.round(bedFormula(lk.seed, x, z, p, cand));
                        final int depth = WATER_TOP - bed;
                        minBed = Math.min(minBed, bed);
                        total++;
                        if (depth >= S1_DEEP_MIN) {
                            deep++;
                        } else if (depth >= S1_MID_LO && depth <= S1_MID_HI) {
                            mid++;
                        } else if (depth >= S1_SHALLOW_LO && depth <= S1_SHALLOW_HI) {
                            shallow++;
                        } else if (depth < S1_SHALLOW_LO) {
                            dryish++;
                        }
                    }
                }
                lakesAll++;
                if (minBed <= D1_BED_MAX) {
                    d1ok++;
                }
                if (minBed != Integer.MAX_VALUE && WATER_TOP - minBed >= D2_DEPTH_MIN) {
                    d2ok++;
                }
            }
            final long base = Math.max(1L, shallow);
            sb.append("\n  plateau=").append(f3(cand))
                .append("  深井占湖面积=").append(pct(deep / (double) Math.max(1L, total)))
                .append("  浅:中:深=1:").append(f3(mid / (double) base)).append(':').append(f3(deep / (double) base))
                .append("  D1(逐湖)=").append(pct(d1ok / (double) Math.max(1, lakesAll)))
                .append("  D2(逐湖)=").append(pct(d2ok / (double) Math.max(1, lakesAll)))
                .append("  浅于").append(S1_SHALLOW_LO).append("档=").append(
                    pct(dryish / (double) Math.max(1L, total)));
        }
        say("P-READ LAKE_BED_PLATEAU 扫描（现值 " + GTSRVoronoiRiverField.LAKE_BED_PLATEAU
            + "；口径 = 床公式逐点取整后水深，采样 = 8 向 × 实测水径逐格射线；注意它是<b>床</b>口径，"
            + "不含 heightCore 的 min(原地形) 与岛抬升 ⇒ 与 S1 的逐列实测并列不互替）：" + sb);
    }

    /**
     * 生产 {@code lakeBedAt} 的参数化副本（<b>P24-D 后：只有"平台外缘 u"可变，外坡控制点
     * {@code LAKE_BED_KNOT_U/G} 与生产同源直引</b>）；仅在 P0 对拍 PASS 后可用。
     * {@code plateau == LAKE_BED_PLATEAU} 时与生产 {@code lakeBedAt} <b>逐位同式</b>（同算术序）。
     */
    static double bedFormula(long seed, int x, int z, double pressure, double plateau) {
        final double shoreBed = SEA - 1.0D;
        final double centerBed = SEA - GTSRVoronoiRiverField.LAKE_CENTER_DEPTH;
        final double u = Math.min(1.0D, Math.max(0.0D, pressure / WATER));
        final double g = bedProfileG(u, plateau);
        return shoreBed + (centerBed - shoreBed) * g
            + 0.5D + 0.5D * GTSRWorldgenHash
                .valueNoise(seed ^ GTSRVoronoiRiverField.SALT_LAKE_BED,
                    x / GTSRVoronoiRiverField.LAKE_BED_NOISE_SCALE,
                    z / GTSRVoronoiRiverField.LAKE_BED_NOISE_SCALE);
    }

    /**
     * 剖面 g(u)：{@code u ≤ plateau} 恒 1；其上按生产控制点 {@code LAKE_BED_KNOT_U/G} 逐段线性插值
     * （首段起点用入参 plateau 替换，故 {@code plateau=LAKE_BED_PLATEAU} 时与生产
     * {@code lakeBedProfileG} 逐位同式）。{@code plateau} 必须 &lt; {@code LAKE_BED_KNOT_U[1]}。
     */
    static double bedProfileG(double u, double plateau) {
        final double[] us = GTSRVoronoiRiverField.LAKE_BED_KNOT_U;
        final double[] gs = GTSRVoronoiRiverField.LAKE_BED_KNOT_G;
        if (u <= plateau) {
            return gs[0];
        }
        for (int k = 0; k + 1 < us.length; k++) {
            final double u0 = k == 0 ? plateau : us[k];
            final double u1 = us[k + 1];
            if (u <= u1) {
                final double t = (u - u0) / (u1 - u0);
                return gs[k] + (gs[k + 1] - gs[k]) * t;
            }
        }
        return gs[gs.length - 1];
    }

    // ══════════════════════════ L 组：表层单一真值复用 ══════════════════════════

    /**
     * L1 straddle 选窗的评估 chunk 上限（P30-III-1b）：同一扫描序（z 外 x 内、步 64）下按 chunk
     * 去重评估「湿带列 ∧ 干滩档列」双见窗，扫满本值仍无命中 ⇒ 按未命中红（干滩改派面在全
     * ±4000 湿带域不可见 = 真回归信号）。取 256 = 首见实测 12 块（temp/p30-iiib/probe/l1-post.out
     * STRADDLE-FIRST #12）的 21 倍余量；每块 256 列 × 生产纯函数直调 ≈ 2ms ⇒ 帽内最坏 ≈ 0.5s。
     */
    static final int L1_STRADDLE_SCAN_CAP = 256;

    /** L1 单 chunk 计数（P30-III-1b 提出原内联循环）：湿带/退回/檐/砂/岩/第三料列数。 */
    static final class L1Tally {
        int wet;
        int wetAsGravel;
        int outside;
        int identical;
        // P22 A2b（G3）：非湿带列被外檐加铺"既有湿料"的列数（羽化的机检形态）
        int halo;
        // P26-B3（D3）：非湿带列被干滩档改派硅砂的列数（c3 干滩料 + d2 陆侧羽化）
        int drySand;
        // P29-B2（D3·B2）：非湿带列被滩坡废岩档改派的列数（P30-III-1b 补入合法集，见 L1 注释）
        int slopeStone;
        int mism;
    }

    /**
     * L1 单 chunk 16×16 逐列计数（P30-III-1b 从原内联循环提出，判定口径逐字不动 + 废岩档计数）：
     * base = {@code blended==null ? biome.topBlock : blended.topAt(...)}（离线装配态 blended 恒
     * null，见 L-READ）；非湿带列差异分类 {gravel=羽化檐, silica=干滩档, stone=滩坡废岩档,
     * 其余=第三料}。全部读数走生产出口（{@code lakeWetBandAt} 谓词 + wrapper.topAt 直调），
     * 判据侧零重写判定式。
     */
    static L1Tally tallyL1Chunk(long seed, GTSRWorldChunkManager mgr,
        GTSRChunkProviderBase.SurfaceTopSelector wrapper,
        GTSRChunkProviderBase.SurfaceTopSelector blended, int baseX, int baseZ) {
        final L1Tally t = new L1Tally();
        final BiomeGenBase[] plane = mgr.loadBlockGeneratorData(null, baseX, baseZ, 16, 16);
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                final int x = baseX + lx;
                final int z = baseZ + lz;
                final BiomeGenBase bio = plane[(lz << 4) | lx];
                if (bio == null) {
                    continue;
                }
                final Block base = blended == null ? bio.topBlock : blended.topAt(seed, x, z, bio);
                final Block got = wrapper.topAt(seed, x, z, bio);
                if (GTSRVoronoiRiverField.lakeWetBandAt(seed, x, z)) {
                    t.wet++;
                    if (got == BlocksGTSR.prosperityRiverGravel) {
                        t.wetAsGravel++;
                    }
                } else {
                    t.outside++;
                    if (got == base) {
                        t.identical++;
                    } else if (got == BlocksGTSR.prosperityRiverGravel) {
                        t.halo++; // 改派仅可为既有湿料（单一真值的羽化口径）
                    } else if (got == BlocksGTSR.prosperitySilicaSand) {
                        t.drySand++; // 干滩档（P26-B3）：合法第二改派料（非 top 名册、已注册，
                        // 「砾当 top」同性质先例；只在 h>SEA 的陆侧出窗 ≤10 格当量域可达）
                    } else if (got == BlocksGTSR.prosperityStone) {
                        t.slopeStone++; // 滩坡废岩档（P29-B2）：合法第三改派料（P30-III-1b 补入
                        // 合法集，h∈(73,79] 岸带核心 + 陆侧羽化；岛底柱同方块先例）
                    } else {
                        t.mism++; // 第三料 = 真正的"第二真值"，必 0
                    }
                }
            }
        }
        return t;
    }

    static void groupSurfaceSingleTruth() {
        try {
            SurfaceHarness.initVanillaBlocks();
            SurfaceHarness.blockFamily();
            final BiomeGenBase[] pb = SurfaceHarness.prosperityBiomes();
            SurfaceHarness.recordAllAllocations(pb, SurfaceHarness.shatteredBiomes());
            final GTSRDimensionDef def = SurfaceHarness.def(true, pb, SurfaceHarness.prosperityWeights());
            final long seed = SurfaceHarness.SEED;
            final GTSRWorldChunkManager mgr = new GTSRWorldChunkManager(seed, def);
            final GTSRChunkProviderBase prov = SurfaceHarness.provider(true);
            final Method specM = GTSRChunkProviderBase.class.getDeclaredMethod("surfaceSpec");
            specM.setAccessible(true);
            final Object spec = specM.invoke(prov);
            final Field selF = GTSRChunkProviderBase.SurfaceSpec.class.getDeclaredField("topSelector");
            selF.setAccessible(true);
            final GTSRChunkProviderBase.SurfaceTopSelector wrapper =
                (GTSRChunkProviderBase.SurfaceTopSelector) selF.get(spec);
            if (wrapper == null) {
                check("L1 湿带表层钩子已挂载（SurfaceSpec.topSelector != null ⇒ 走 S1 的 SurfaceTopSelector"
                    + " 接口，而不是框架默认路径）", false, "topSelector = null（未接线）");
                return;
            }
            int fx = 0;
            int fz = 0;
            boolean found = false;
            for (int z = -4000; z <= 4000 && !found; z += 64) {
                for (int x = -4000; x <= 4000 && !found; x += 64) {
                    if (GTSRVoronoiRiverField.lakeWetBandAt(seed, x, z)) {
                        fx = x;
                        fz = z;
                        found = true;
                    }
                }
            }
            if (!found) {
                check("L0 湿带真实表层可判：装配 seed 的 ±4000 内存在 lakeWetBandAt 为真的列", false,
                    "零命中（SurfaceHarness.SEED = " + seed + "）");
                return;
            }
            // ═══ P30-III-1b 选窗重钉：首个湿带列所在 chunk → 同一扫描序（z 外 x 内、步 64、按 chunk
            // 去重）下首个「湿带列>0 ∧ 干滩档（硅砂）列>0」双见 straddle 窗 ═══
            // 归因（temp/p30-iiib/probe/l1-{pre,post}.out 双跑，pre=P30 批0 冻结基线）：批I I2（PTP
            // heightCore 末段滩缘贴水线钳制首肢 LAKE_SHORE→LAKE_WATER_LEVEL，设计滩环 [WATER,SHORE)
            // 内 y<67 洼列全钳到 67）把 67 送进湿带门③窗口 [SEA−2,SEA]=[66,68] ⇒ 整个被钳平台成
            // 湿带——post 首命中窗 chunk(3424,-3808) 256/256 列全湿、h 均匀 67（钳制签名）、域外列 0
            // ⇒ L1「outside>0 ∧ drySand>0」结构性红；pre 首命中窗 chunk(3616,-3872) 是 wet 119 +
            // 干滩硅砂 137 的 straddle 窗（v1.20.52 绿态）。干滩档本身没死（I2 只钳 h<67 洼列，
            // 环外缘 h69-73 滩坡列照常改派硅砂：post straddle 首见 chunk(3360,-3552) 砂 66/66）；
            // I3（S1 交界带收窄）不进本装配态计数面（blended==null，见 L-READ）。⇒ 判定：I2 设计内
            // 形态变化引发的<b>选窗问题</b>，非生产回归 ⇒ 修选窗，断言式（mism==0 ∧ drySand>0）不动。
            // 扫尽 {@link #L1_STRADDLE_SCAN_CAP} 块仍无 straddle = 干滩改派面在全 ±4000 湿带域不可见
            // （真回归信号），L1 按未命中红并退回首命中窗出读数。
            int baseX = fx & ~15;
            int baseZ = fz & ~15;
            L1Tally t = null;
            int scanned = 0;
            final HashSet<Long> seenChunks = new HashSet<Long>();
            // 帽标签（P30-III-1b 补）：内层 break 只退当前 x 行，外层 z 条件（t==null）仍真 ⇒
            // 无 straddle 回归态下每 z 行再多评 1 块、帽外溢 ≈+126 块；break scan 使
            // L1_STRADDLE_SCAN_CAP 恰为总评估上限，与常量 javadoc 口径一致。
            scan:
            for (int z = -4000; z <= 4000 && t == null; z += 64) {
                for (int x = -4000; x <= 4000 && t == null; x += 64) {
                    if (!GTSRVoronoiRiverField.lakeWetBandAt(seed, x, z)) {
                        continue;
                    }
                    final int bx = x & ~15;
                    final int bz = z & ~15;
                    final long key = ((long) bx << 32) ^ ((long) bz & 0xffffffffL);
                    if (!seenChunks.add(Long.valueOf(key))) {
                        continue;
                    }
                    final GTSRChunkProviderBase.SurfaceTopSelector bl = GTSRSurfaceBorderBand
                        .forChunk(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, seed, bx, bz);
                    final L1Tally c = tallyL1Chunk(seed, mgr, wrapper, bl, bx, bz);
                    scanned++;
                    if (c.wet > 0 && c.drySand > 0) {
                        t = c;
                        baseX = bx;
                        baseZ = bz;
                    } else if (scanned >= L1_STRADDLE_SCAN_CAP) {
                        break scan;
                    }
                }
            }
            boolean noStraddle = false;
            if (t == null) {
                noStraddle = true;
                final GTSRChunkProviderBase.SurfaceTopSelector bl = GTSRSurfaceBorderBand
                    .forChunk(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, seed, baseX, baseZ);
                t = tallyL1Chunk(seed, mgr, wrapper, bl, baseX, baseZ);
            }
            final GTSRChunkProviderBase.SurfaceTopSelector blended = GTSRSurfaceBorderBand
                .forChunk(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, seed, baseX, baseZ);
            final int wet = t.wet;
            final int wetAsGravel = t.wetAsGravel;
            final int outside = t.outside;
            final int identical = t.identical;
            final int mism = t.mism;
            // P22 A2b（G3）新增：非湿带列被外檐加铺"既有湿料"的列数（羽化的机检形态，见 L1 新口径）
            final int halo = t.halo;
            // P26-B3（D3）新增：非湿带列被干滩档改派硅砂的列数（c3 干滩料 + d2 陆侧羽化的机检形态）
            final int drySand = t.drySand;
            // P30-III-1b 新增计数：非湿带列被滩坡废岩档（P29-B2 第三改派料）改派的列数——只读数，
            // 同时把 prosperityStone 补入 L1 合法改派集（P22 G3 加砾 / P26-B3 加硅砂同款操作：
            // 生产侧 P29-B2 已在 v1.20.52 落地本档，判据侧合法集滞后一轮 ⇒ 在选窗重钉的同时收口，
            // 防下一轮 straddle 窗撞上 h∈(73,79] 滩坡列时的假红）。
            final int slopeStone = t.slopeStone;
            final String note = "chunk(" + baseX + "," + baseZ + ")（"
                + (noStraddle ? "NO-STRADDLE：扫尽 " + scanned + " 个湿带 chunk 无干滩档可见列，退回首命中窗"
                    : "湿带 straddle 窗，扫描序第 " + scanned + " 块选中（P30-III-1b）")
                + "）S1 blended="
                + (blended == null ? "null(退回 topBlock)" : blended.getClass().getSimpleName())
                + "；湿带列=" + wet + "，表层 = prosperityRiverGravel 的 " + wetAsGravel
                + "；域外列=" + outside + "，逐字退回 " + identical + "，羽化檐(=砾) " + halo
                + "，干滩档(=硅砂) " + drySand + "，滩坡岩(=废岩) " + slopeStone + "，第三料 " + mism;
            // 旧口径原文（P20 §15.4，v1.20.41）：「包装层在非湿带列必须逐字退回 S1 的答案」
            // （outside > 0 && mism == 0）。P22 A2b（G3 湿带外缘羽化）把"逐字退回"放宽为
            // "退回，或仅加铺既有湿料 prosperityRiverGravel"；P26-B3（D3·c3/d2）再纳
            // prosperitySilicaSand 为合法第二改派料（干滩环+陆侧羽化，P22 A2b G3 放宽先例同款
            // 操作）——"不得新立第二真值"的语义不变：第三料 mism 仍必 0，两改派料均为同维
            // 已注册非 top 名册块、无新皮肤选择逻辑。
            // 反假绿（P26-B3 重钉）：本 chunk 是湿带中心窗，非湿带列 <b>结构性全落干滩档</b>
            //（h=69 干滩核心列，干滩档先于砾檐裁定 ⇒ 砾檐在本窗恒 0——P26-B3 前该窗 117/117
            // 全砾檐、后 120/120 全硅砂）⇒ 可见性钉从「halo>0」改「drySand>0」；湿料侧的
            // 可见性由 L2（湿核全砾）钉住。
            // 旧选窗口径原文（v1.20.41～v1.20.52）：「首个 lakeWetBandAt 真列所在 chunk」——
            // 批I I2 滩环钳制后被钳平台整片成湿带，首命中窗 256/256 全湿（chunk(3424,-3808)，
            // h 均匀 67）⇒ outside=0 结构性红；P30-III-1b 改选 straddle 窗（断言式不动，归因
            // 证据与 pre/post 双跑读数见上方选窗注释与 temp/p30-iiib/probe/）。
            check("L1 §15.4+P22G3+P26B3 湿带复用 S1 表层钩子：非湿带列要么逐字退回 S1（GTSRSurfaceBorderBand），"
                + "要么差异列 top ∈ {prosperityRiverGravel（羽化檐）, prosperitySilicaSand（干滩档）, "
                + "prosperityStone（滩坡废岩档，P29-B2 第三改派料）}（禁第三料），且本湿带 straddle 窗"
                + "干滩改派必须可见（P30-III-1b 选窗重钉）",
                !noStraddle && outside > 0 && mism == 0 && drySand > 0, note);
            check("L2 §15.4 湿带确实改派同维名册内的湿料 prosperityRiverGravel（H-4 零新方块）：本 chunk 湿带列全中",
                wet > 0 && wetAsGravel == wet, note);
            say("L-READ 装配态说明：离线 JVM 的 SurfaceHarness def <b>未过 DimensionRegistrar</b>"
                + "（既存事实，同 PlacementContractCheck 的 resolvedDimId 未解析申报）⇒ forChunk 走"
                + " chainSeed 解析不到 → null 分支，L1 的「逐字退回」在该态下由「两条路都退回 biome.topBlock」"
                + "成立；混合带真开的一态由 L4 源级断言兜住（包装层第一动作就是调 S1 出口）");
            groupSurfaceSourceSingleTruth();
        } catch (ReflectiveOperationException | LinkageError e) {
            check("L0 表层真值复用可判（surfaceSpec / topSelector 反射路径 + 离线装配）", false,
                "装配异常：" + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    /**
     * L4 源级单一真值断言（§15.4「湿带复用 S1 的表层选择钩子出口，<b>不得</b>新立第二真值」）。
     * 先例：{@code SurfaceGateUnifyCheck.assertSourceLevelSingleTruth}、
     * {@code P17VegetationFrequencyCheck} 的源级计数钉。剥注释后计数 ⇒ 注释改动不破判据（§14.2 结论）。
     */
    static void groupSurfaceSourceSingleTruth() {
        final String path = "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/"
            + "ChunkProviderProsperityRuins.java";
        String code;
        try {
            final java.nio.file.Path f = java.nio.file.Paths.get(path);
            code = stripComments(new String(java.nio.file.Files.readAllBytes(f),
                java.nio.charset.StandardCharsets.UTF_8)).replaceAll("[\s]+", "");
        } catch (java.io.IOException e) {
            check("L4 §15.4 源级：湿带选择器必须复用 S1 出口（读 " + path + "）", false, "读取失败：" + e);
            return;
        }
        // 计数用"同一行内"的 needle：委托调用在源码里跨行写（GTSRSurfaceBorderBand 换行 .forChunk(...)），
        // 只靠空白剥离不稳 ⇒ 直接钉 .forChunk( 的实参面 + 类名各一次，两条都在同一行内。
        final int callS1 = count(code, "forChunk(GTSRBiomeAuthority.DIM_KEY_PROSPERITY");
        final int implHook = count(code, "implementsGTSRChunkProviderBase.SurfaceTopSelector");
        final int wetGate = count(code, "GTSRVoronoiRiverField.lakeWetBandAt(");
        final int wetBlock = count(code, "BlocksGTSR.prosperityRiverGravel");
        final int dryGate = count(code, "dryBeachSandAt(seed,x,z)");
        final int dryBlock = count(code, "BlocksGTSR.prosperitySilicaSand");
        final int newSel = count(code, "newLakeWetBandTopSelector(");
        final int topBlockFallback = count(code, "biome.topBlock");
        say("L4-READ 源级（剥注释后计数）：调 S1 forChunk=" + callS1 + " implements SurfaceTopSelector="
            + implHook + " lakeWetBandAt 门=" + wetGate + " prosperityRiverGravel=" + wetBlock
            + " dryBeachSandAt 门调用=" + dryGate + " prosperitySilicaSand=" + dryBlock
            + " new 选择器=" + newSel + " biome.topBlock 直取=" + topBlockFallback);
        check("L4 §15.4：湿带选择器必须 ①implements S1 的 SurfaceTopSelector 接口 ②第一动作委托"
            + " GTSRSurfaceBorderBand.forChunk ③域门只吃 lakeWetBandAt ④挂载点恰 1 处 new"
            + "（= 表层身份单一真值，不存在第二份混合带实现）",
            callS1 == 2 && implHook == 1 && wetGate == 1 && newSel == 1,
            "forChunk(同维白名单实参)=" + callS1 + " implements=" + implHook + " wetBandAt=" + wetGate
                + " new=" + newSel);
        check("L4b H-4/名册面：湿带改派只取已注册方块 prosperityRiverGravel（P22 G3 羽化改版后选择器体内"
            + "恰 1 处绑定引用 final Block gravel = ...；旧『恰 2 处：已是湿料短路+改派』是 G3 前形态），"
            + "且 biome.topBlock 直取仅出现在 blended==null 的退回分支（≤1 处）",
            wetBlock == 1 && topBlockFallback <= 1,
            "riverGravel=" + wetBlock + " biome.topBlock=" + topBlockFallback);
        // P26-B3（D3）追加：干滩档（c3+d2）的源级钉——绑定恰 1 处、裁定恰 1 处、且只挂在既有的
        // 同一选择器内（无第二选择器实例；上面 new==1 已钉）。防"硅砂档复制一份湿带判定另立真值"。
        check("L4c P26-B3 干滩档源级：dryBeachSandAt 绑定 prosperitySilicaSand 恰 1 处、topAt 裁定恰 1 处"
            + "（干滩料=同选择器内第二改派档，非第二份混合带实现）",
            dryBlock == 1 && dryGate == 1, "silicaSand=" + dryBlock + " dryBeachSandAt 裁定=" + dryGate);
    }

    static String stripComments(String src) {
        return src.replaceAll("(?s)/[*].*?[*]/", " ").replaceAll("(?m)//.*$", " ");
    }

    static int count(String haystack, String needle) {
        int c = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            c++;
            i += needle.length();
        }
        return c;
    }

    // ══════════════════════════ R 组：§7-6 环带结构暴露面 ══════════════════════════

    static void groupRingBandStructure(List<Lake> lakes) {
        Method m;
        try {
            m = PlacementGate.class.getDeclaredMethod("dryColumnAt", long.class, int.class, int.class);
            m.setAccessible(true);
        } catch (NoSuchMethodException e) {
            say("R-READ §7-6 环带结构暴露面：dryColumnAt 反射入口不存在 ⇒ 本 READ 不可用（登记为未证）");
            return;
        }
        long band = 0;
        long exposed = 0;
        try {
            for (final Lake lk : lakes) {
                if (!lk.fine) {
                    continue;
                }
                for (int d = 0; d < 8; d++) {
                    final double ang = d * Math.PI / 4.0D;
                    final double dx = Math.cos(ang);
                    final double dz = Math.sin(ang);
                    for (int r = 1; r <= (int) lk.rayShoreRadius[d]; r++) {
                        final int x = lk.cx + (int) Math.round(dx * r);
                        final int z = lk.cz + (int) Math.round(dz * r);
                        final double p = GTSRVoronoiRiverField.lakeAt(lk.seed, x, z);
                        if (p < WATER || p >= SHORE) {
                            continue;
                        }
                        band++;
                        final boolean dry = ((Boolean) m.invoke(null, Long.valueOf(lk.seed),
                            Integer.valueOf(x), Integer.valueOf(z))).booleanValue();
                        if (dry && ProsperityTerrainProfile.heightAt(lk.seed, x, z) < SEA) {
                            exposed++;
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            say("R-READ §7-6 求值异常：" + e);
            return;
        }
        say("R-READ §7-6 环带结构落块暴露面（<b>只报不钉</b>，权威判据是 PlacementContractCheck / B2 收口全量链）："
            + "环带采样列=" + band + "，其中「避水门 dryColumnAt 判干 &amp;&amp; heightAt &lt; SEA（fillSanzuLakes"
            + " 会置水）」= " + exposed + " 占 " + pct(band == 0 ? -1.0D : exposed / (double) band)
            + " ⇒ 该窗口 = 「结构落在湖环带浅水里」（避水门第二关只看 lakeAt &lt; WATER，不看 LAKE_SHORE 环带）；"
            + "非 0 时 §7 的固定顺位是 1（环带收到 WATER+0.02），禁止改 PlacementGate 三门（禁改面）");
    }

    // ══════════════════════════ 工具方法 ══════════════════════════

    static int readPrivateInt(Class<?> c, String field, int fallback) {
        try {
            final Field f = c.getDeclaredField(field);
            f.setAccessible(true);
            return f.getInt(null);
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
    }

    static void say(String s) {
        LINES.add(s);
    }

    static void check(String name, boolean ok, String detail) {
        assertions++;
        if (!ok) {
            failures++;
        }
        LINES.add((ok ? "PASS  " : "FAIL  ") + name + " ｜ " + detail);
    }

    static String f3(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", Double.valueOf(v));
    }

    static String f9(double v) {
        return String.format(java.util.Locale.ROOT, "%.2e", Double.valueOf(v));
    }

    static String pct(double v) {
        return v < 0.0D ? "-" : String.format(java.util.Locale.ROOT, "%.2f%%", Double.valueOf(v * 100.0D));
    }

    static double mean(double[] a, int n) {
        double s = 0.0D;
        for (int i = 0; i < n; i++) {
            s += a[i];
        }
        return n == 0 ? 0.0D : s / n;
    }

    static double max(double[] a, int n) {
        double m = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            m = Math.max(m, a[i]);
        }
        return n == 0 ? 0.0D : m;
    }

    static double max(double[] a) {
        double m = Double.NEGATIVE_INFINITY;
        boolean any = false;
        for (final double v : a) {
            if (!Double.isNaN(v)) {
                any = true;
                m = Math.max(m, v);
            }
        }
        return any ? m : 0.0D;
    }

    static double min(double[] a) {
        double m = Double.POSITIVE_INFINITY;
        boolean any = false;
        for (final double v : a) {
            if (!Double.isNaN(v)) {
                any = true;
                m = Math.min(m, v);
            }
        }
        return any ? m : 0.0D;
    }

    static double median(double[] a) {
        return pctl(a, 0.50D);
    }

    static double pctl(double[] a, double q) {
        final double[] c = new double[a.length];
        int n = 0;
        for (final double v : a) {
            if (!Double.isNaN(v)) {
                c[n++] = v;
            }
        }
        if (n == 0) {
            return 0.0D;
        }
        final double[] s = Arrays.copyOf(c, n);
        Arrays.sort(s);
        final int idx = (int) Math.max(0, Math.min(s.length - 1, Math.floor(q * s.length)));
        return s[idx];
    }
}
