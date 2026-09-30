import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerRosterFace;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.TerrainVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRRiverPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * <b>v1.20.39 T4 机检：dim78 Voronoi 河流强度场形态（plan §3.1/§3.2 校准回路的判据面）</b>。
 * 纯模型驱动（{@link GTSRVoronoiRiverField}/{@link ProsperityTerrainProfile} 均零世界读取，
 * 无需离线装配账本：按档测量传显式 rosterIndex；heightAt 链在未装配 JVM 走默认档，河谷
 * 压低链仍在。<b>P26-B2（v1.20.49）K 组起补一处离线装配</b>：床/滩料群系分布断言需要方块
 * 身份判等，经 {@code SurfaceHarness} 直填 BlocksGTSR 三沙族字段——<b>不触 L1 账本</b>
 * （不调 recordAllAllocations ⇒ rosterIndexCached 仍 -1，A-H/J 组读数不受影响））。
 * P17RiverNetworkCheck（旧轴向等距线模型）已随旧模型删除，本判据接替其位。
 *
 * <p>
 * ═══ 断言组 A-H + J + K（全部实跑；阈值 = plan §8 验收指标，统计功效参数全部从生产常数派生——
 * v1.20.38 纪律 2：不写字面量窗口）═══
 * <ul>
 * <li><b>A 合同面</b>：strengthAt ∈ [-1,0] 且确定性（双跑逐位）、换 seed 必换场、档表 5 元
 * （T5/T8：4 selector + sanzu 主干宽河档）、默认档=常态河、trunkAt/lakeAt 已激活且确定性
 * （T5/T8 反转原 T4 占位钉）、
 * VALLEY_LEVEL==WET_MIN（平底域==置水域不变量）、沼泽档 widthScale==1.6、床档表值域；</li>
 * <li><b>B 河宽分布</b>：常态（草原/森林档）水道半宽 = 河道中心走到 s&lt;WET_MIN 的距离，
 * 中位数 ∈ [5,8] 格（plan 目标带）；</li>
 * <li><b>C 谷坡过渡带（v1.20.41 P20 S3 改口径；C2 由 S3c 按 §22-B 重定；<b>C2-b 的采样域由 §23-B
 * 提出换域裁决</b>——§22-B 的"谷坡环列"表述按其要求<b>原文保留</b>在本文件（{@link #C2B_LO} 注释与
 * {@code C2-b} 断言文案），换域后的状态见 {@link #C2B_LO} 顶部的处置段）
 * </b>：谷坡环宽 = 水缘→
 * {@code inBankBand} 外缘的连续列距（C1）＋需求 2 的真身两条：<b>C2-a</b> 断面床料连段 ≥3 列的河占比
 * （床料出露的<b>连续性</b>）与 <b>C2-b</b> 岸地面高出水面 ≥2 格的谷坡环列占比（<b>岸坡下切</b>的直接
 * 度量；<b>§23-B 要求的「岸带首列」＝每个湿核外侧第一个干列已按字面实现为只报不钉的读数，带未迁移</b>）
 * ＋下切量值域与环的非退化（C3）。两条旧口径（水缘→谷缘 {@code s==0} 的 v1.20.40 带 [15,35]、
 * 河核断面床料出露率的 [0.90,0.995]）均<b>降为读数保留</b>，错因见各自注释；</li>
 * <li><b>D 蜿蜒度</b>：中心线追踪（步长/步数从 LARGE_BEND_SCALE 派生，覆盖 ≥3 个大弯波长）
 * 曲折率 = 路长/端距 &gt; 1.2；</li>
 * <li><b>E 支流连通</b>：Voronoi 三叉点（2×2 列块内 ≥3 个不同最近细胞签名）非零且成量
 * ——边界网络天然连通的分叉证据；</li>
 * <li><b>F 枯竭验证（v1.20.42 P22 A1c 重立；<b>P23 R1·S6（v1.20.46）再改</b>）</b>：
 * <b>F1</b> <b>全域置水 == 0</b>（wetAt 恒 false 的合同面——主干河置水死后河流场全域无水，
 * 湖/沼泽池走独立通道；旧「枯竭域（trunk≤0）置水==0」双侧口径随 F2 退役收成全域单侧）；
 * <b>F2 已删</b>（主干宽河移除 ⇒「遗忘之川域湿段率 100%」判据对象不存在，plan §3 表序 3）；
 * <b>F3 段激活率带（P23 R1·S6 新增）</b>：零散干床段的激活段占比 ∈ [0.16,0.24]
 * （构造值 RIVER_SEGMENT_ACTIVATE_P=0.20 的哈希均匀性读数，S2 直跑 0.194 在带）。</li>
 * <li><b>G 沼泽河宽 ×1.2</b>：沼泽档/常态档水道半宽比 ∈ [1.05,1.45]（v1.20.40 P19 §A.2 重钉：
 * ×1.6→×1.2 档乘子的行为读数带）；外加 heightAt 河谷集成：河核列 heightAt 与 bedAt 偏差 ≤1.5
 * （压低链真接进了高度）；</li>
 * <li><b>H 分段水位与端面（v1.20.40 P19 §B/§C 新增；<b>P23 R1·S6（v1.20.46）置水断言删、
 * 床阶语义留</b>——wetAt 恒 false 后 H1 的采样域从「遗忘之川域 wet 列」改「激活段河核列」
 * （s≥WET_MIN 即激活段——段激活门在 strengthAt 内），断言带（档数 ≤3 ∧ 份额 ≥1/2）与
 * v1.20.40 原钉一字不动）</b>：H1 同 segKey 河核列 poolLevelAt 档数 ≤3 且最高池份额 ≥1/2
 * （分段水位阶梯的纯函数性——置水死后池水位仍是床面/端面收尾的段界参考面）；H2 端面收尾
 * 沿顺流剖面逐列床高差 ≤ 2（<b>P30-III-1a（v1.20.53 批I 后）锚点域重钉</b>：I1 死邻衰减把
 * 端面从"段界换段面"搬进"结点向衰减带"——原 trunk&gt;0 ∧ s≥WET_MIN 端面入口锚衰减后样本 0
 * （空集红），新锚 = 三叉结点向 s&lt;{@code DRY_RIVERBED_CORE}→≥WET_MIN 衰减带径向剖面；
 * smoothstep 构造 ≤1/列的旧语义由 I1 t₁/t₂ 衰减承载，床口径 bedFromPool 照旧、池阶列按
 * poolLevelAt javadoc 的段间差 ∈ {0,2,4} 设计域排除判据只报读数——细则见 groupH javadoc）。</li>
 * <li><b>I 沼泽河床残潭：<b>已整组退役（P25 D7，对齐生产 RVF swampRiverPoolAt 族删除）</b></b>——
 * I1~I4 四断言与采样几何（POOL_HUNT/POOL_WINDOWS/poolBarrier）随 {@code swampRiverPoolAt}/
 * {@code SWAMP_RIVER_POOL_FILL_TOP} 符号删除整段摘除（缺源即 javac 红窗，纪律同 S1 的 SanzuTrunk
 * CoverageCheck 整文件退役）；退役登记见 {@link #groupJ()} 的 I-RETIRED 行，历史读数（覆盖率
 * 0.1348 等）见版本树与 {@code plan/sum/86} §5。
 * <li><b>J 枯竭河床（P25 D6/D4 判据化新增）</b>：J1 湖+滩压力域（lakeAt &lt; sanzuBiomeShoreAt）
 * 内河流强度 s ≡ 0（strengthAt 湖让位腿的行为直读）；J2 isDryRiverColumn 过渡带几何宽中位
 * ∈ [8,16] 格（阈带 [0.40,0.60) × 42 格/单位 s 的 javadoc 换算式实测复核）；J3 isDryRiverColumn
 * ⇒ !isSanzuColumn 逐列不相交（≥10⁶ 列采样，违例 = 0——谓词内含湖让位双保险腿的行为证）。</li>
 * <li><b>K 床料群系分布（P26-B2 v1.20.49 判据化新增）</b>：K1 沼泽河核列表面==硅砂 ≥0.98 /
 * K2 沼泽谷坡环列==硅砂 ≥0.95（D6 沼泽床/滩料引入硅砂的行为钉——基线沼泽核/环 889/848 全河砾）；
 * K3 非沼泽档（0/1/2）床选型列==河砾 ≥0.98（「其余档恒河砾」腿，非沼泽段床料逐位不变硬约束）；
 * K4 沼泽 pool 档 ⊆ {63,65,67} ∧ bed−pool ∈ [−2.5,−0.5]（床档随枯竭下调 66.5/1.0→64.5/2.0 的
 * 行为钉）。材料表走 {@code GTSRRiverPlacer} public 出口，方块身份经 SurfaceHarness 离线装配
 * （不触 L1 账本），断言带由缩样读数钉（{@code plan/tmp/p26-b2-readings.md}）。</li>
 * </ul>
 *
 * <p>
 * <b>用法</b>：{@code java RiverMorphologyCheck}（退出码 0 = 全绿）。采样种子固定 ⇒ 双跑逐位一致。
 */
public final class RiverMorphologyCheck {

    /** 本判据世界种子（与兄弟片不同值，免得读数互串）。 */
    static final long SEED = 0x7269_7665_4C42L;

    // ── 统计功效参数（派生式：全部从 GTSRVoronoiRiverField 生产常数导出，无字面量窗口）──
    /** 采样窗半径（格）：≥3 个 Voronoi 细胞 ⇒ 窗内必有 ≥9 条边界。 */
    static final int SCAN_EXTENT = (int) (GTSRVoronoiRiverField.SEPARATION * 3);
    /** 采样步距（格）：SEPARATION/128 = 8 < 常态水道全宽（~2×5.8）⇒ 河道不会被步距跳空。 */
    static final int SCAN_STRIDE = (int) (GTSRVoronoiRiverField.SEPARATION / 128);
    /**
     * 河道中心样本数上限/下限基数。<b>P23 R1·S6：32 → 200</b>。段激活门清零 80% 段后，旧 32 上限
     * 的扫描序样本只覆盖最先遇到的少数激活段（聚集样本）——实测偏差：聚集样本常态中位 4.0 /
     * 沼泽同量化 5/5=1.000，而 ±2×SCAN_EXTENT 代表性探针（n=200，temp/p23-s6/WidthProbe）给
     * 常态中位 5.0（hist 3:25/4:38/5:44/6:33/7:11，mode=5）、沼泽中位 6.0（比值 1.20 精确回档）。
     * 物理宽度未变（WIDTH=0.08 未动，激活段强度式逐字同前）——变的是样本代表性。去重半径
     * （WALK_LIMIT/2）不动 ⇒ 每段至多 ~8 心，200 上限 ≈ 全窗激活段（~26 段）的完备去重集。
     */
    static final int N_CENTERS = Math.max(200, (int) (GTSRVoronoiRiverField.SEPARATION / 32));
    /** 半宽走查上限（格）：SEPARATION/7 = 150 > 4×谷半宽上限 ⇒ 谷缘必在限内。 */
    static final int WALK_LIMIT = (int) (GTSRVoronoiRiverField.SEPARATION / 7);
    /** 中心线追踪步长（格）与步数：总路长 = 3×LARGE_BEND_SCALE（覆盖 ≥3 个大弯波长）。 */
    static final int TRACE_STEP = (int) (GTSRVoronoiRiverField.SMALL_BEND_SCALE / 13);
    static final int TRACE_STEPS = (int) (GTSRVoronoiRiverField.LARGE_BEND_SCALE * 3) / TRACE_STEP;
    /** 追踪段数：SEPARATION/SMALL_BEND_SCALE = 13 段。 */
    static final int N_TRACES = (int) (GTSRVoronoiRiverField.SEPARATION / GTSRVoronoiRiverField.SMALL_BEND_SCALE);
    /**
     * H1 的段数样本下限（16 = 旧 N_CENTERS(32)/2 的有效值，P23 R1·S6 起独立钉住——N_CENTERS 为
     * 代表性采样提档到 200 后，H1 的段数下限不再借用它；±SCAN_EXTENT 窗内激活段 ~26 段恒过）。
     */
    static final int H1_SEGS_MIN = 16;
    /**
     * B1 的半宽样本数下限（P32 批5 重钉 100→98；旧值 = N_CENTERS/2 = 100 借用式）。
     * <b>重钉依据（设计内损失，批3 尾三腿对拍归因在案：plan/tmp/p32-batch3-verify.md §4）</b>：
     * waterHalfWidth 四向走 {@code -strengthAt ≥ WET_MIN} 的有界水缘，段心落入湖+滩压力域
     * （J1 湖让位 s≡0）即返回 -1 剔除——P32 T1-3 场重构（真实 blob 形）吞 1 心（批1 n=100 →
     * C-C 树 n=99）+ T1-4 D3 滨带宽低频腿外扩再吞 1 心（n=99 → 98）；中位/p10/p90 分布与批1
     * 逐位同（2.0/3.0/7.0）⇒ 仅代表性健康度腿移位，非河宽行为变化。98/200 = 49% 仍守住
     * 「约半数心有可信水缘」的采样健康度语义（P23 R1·S6 反聚集样本门）。工具固定种子 ⇒ 读数
     * 确定性，钉值即读值；H1_SEGS_MIN 同款「独立钉住、不借用派生式」先例。
     */
    static final int B1_MIN_SAMPLES = 98;
    /** 三叉点扫描步距（格）：SEPARATION/64 = 16 < 三叉点邻域尺度。 */
    static final int FORK_STRIDE = (int) (GTSRVoronoiRiverField.SEPARATION / 64);

    // ── v1.20.41 P20 S3 重钉的带（C 组）──────────────────────────────────────────────
    /**
     * C1 新带（谷坡环宽中位，格）= §6.1 R2 的定值法「以 S3 完成后的实测中位 m 定 [m−6, m+6]」：
     * 本片 post 态实测 m = <b>5.000</b>（样本 32，p10/中位/p90 = 4/5/9，与生产侧
     * {@code inBankBand} javadoc 自陈的"滩带宽 3.5-9 格成块变化"同阶）⇒ 带 =
     * [max(1, 5−6), 5+6] = <b>[1, 11]</b>。<b>§6.1 R2 同句要求"整带必须落在 [10,45] 内"</b>——
     * 该容器是为 R2 原写的"河核外缘→|h−h0|&lt;1 的列距"口径配的；本片的口径按 §20/任务包改为
     * "水缘→inBankBand 外缘"，m 量级即生产侧自陈的滩带宽 3.5-9 格 ⇒ m−6 &lt; 10 是<b>必然</b>，
     * 容器与口径不可同时满足。处置：保留 m±6 的<b>宽度</b>（±6 与 R2 同值），下界按"环必须存在"
     * 收在 1（0 宽 = 环塌没，正是需求 1 的失效形态），<b>不</b>把带整体抬进 [10,45]（那会把带钉成
     * 恒红）。此偏离已在片回执点名，交主代理裁决（备选口径 = R2 原文的 |h−h0|&lt;1，但 h0 在判据侧
     * 无公开出口 ⇒ 要钉它得先在生产的 heightCore 里把 h0 暴露成出口，属生产侧改动，不在本片写锁内）。
     */
    static final double C1_BAND_LO = 1.0D;
    static final double C1_BAND_HI = 11.0D;
    /**
     * <b>旧 C2「河核断面床料出露率」带 = §6.1 R2 钉的 [0.90, 0.995]——v1.20.41 P20 §22-B 裁定
     * 判据定错，本带降为"只报不钉"，原文保留在此</b>。
     * <p>
     * <b>为什么是判据错、不是生产常量错</b>：本带的分母取<b>河核列</b>（{@code s ≥ WET_MIN}），而
     * S3 的谷坡下切与滩料铺放全部落在 {@code s < WET_MIN} 一侧的<b>谷坡环</b>（{@code inBankBand}）
     * ⇒ 两个集合<b>不相交</b> ⇒ 机制对本带的位移<b>结构性恒零</b>（S3b 用 pre↔prodonly 三张逐字相同
     * 的读数证真：探针网格口径两态同为 0.8421）。这不是"漏接线"：同一份生产改动把 C1 的环宽与
     * C3 的下切量都打出了非零读数。
     * <p>
     * <b>唯一能让旧口径变绿的三条手段全部不许动</b>（这正是"改判据不改常量"的理由）：
     * ① {@code SHORE_FLAT_BAND} 0.15→<b>0.06</b>（敏感性表见 {@code plan/tmp/p20-s3c/sensitivity.md}：
     * 0.08→0.8918、0.06→0.9229；本片 T3 明令该常量一字不改）；
     * ② {@code POOL_ANCHOR_AMP}/{@code POOL_ANCHOR_OFFSET}/{@code POOL_LEVEL_STEP}
     * （R-C 用户裁决"水位阶梯保持不动"；旧 POOL_DROP/DESERT_WET_SHARE 已随 P23 R1·S6 删字段）；
     * ③ {@code MIN_HEIGHT}/{@code SEA_LEVEL}（§11 禁改面 + 用户版 1 严禁）。
     * ⇒ 口径由 §22-B 重定为需求 2 的真身两条：{@code C2A}/{@code C2B}（新带见其注释的实测反解）。
     * <p>
     * §5 S3-2 的取值纪律"判据取 max(0.90, b₀+0.05)、不得取 b₀ 以下"当时是按字面执行的：b₀ =
     * 0.8421（一次性探针 {@code plan/tmp/p20-s3b/S3bProbe}，不入库）⇒ max(0.90, 0.8921) = 0.90，
     * 未"顺手放宽"。红是真红，红的是被量错的对象。
     */
    static final double C2_LO = 0.90D;
    static final double C2_HI = 0.995D;

    // ── S3c T2：C2 重定为需求 2「看不到河床，要岸坡下切」的真身两条 ──────────────────────
    /**
     * C2-a 的"看得见河床"宽度阈（列）。<b>出处</b>：§5 S3-2/S3-3 与 {@code BANK_CUT_DEPTH} javadoc
     * 同一条推导——"现 board 恒 1 格 ⇒ 要让床料至少 <b>3</b> 格宽出露 ⇒ 需再削 2 格"。任务包 §22-B
     * 的口径也写作"床料连段 ≥3 列"。⇒ 本值不是新自由度，与既有下切推导同一个数。
     */
    static final int C2A_RUN_MIN = 3;
    /**
     * C2-a 带（<b>河</b>占比：4 条断面里任一条出现 ≥{@link #C2A_RUN_MIN} 列连续床料的河 / 有效河数）。
     * <p>
     * <b>由实测分布反解（§8 纪律）</b>：本片 post 态实测 有效河 n = <b>32</b>、命中 = <b>31</b>
     * ⇒ M = <b>0.969</b>；逐河最长床料连段 p10/中位/p90/max = <b>4 / 14 / 62 / 102</b> 列（断面口径
     * 对照读数 101/127 = 0.795，只报不钉）。样本阈取"再容许 1 条河不达标"：
     * <b>下界 = ⌊(命中−1)/n × 100⌋/100 = 30/32 = 0.9375 → 0.93</b>（跌到 29/32 = 0.906 即红 ⇒
     * 灵敏度 = 2 条河；比二项 4σ 带 [0.85,1.0] 紧，因为本判据的失效形态是"成片河看不见河床"，
     * n=32 的小样本用 σ 定带会把带撑到无意义）。<b>上界 = 1.0，不反向钉</b>：本条方向是"每条河都
     * 看得见河床"，100% 即终态；反向风险（"整片群系都是河床"）由 B1 河宽带 [3,4.5] 与 F1 荒漠过水
     * 占比带 [0.2,0.4] 守，不在本带内重复钉。
     * ⚠ 本轮取实测反解，<b>实机后校准</b>；B3(S5)/B4(S6) 落地后若地面侧合法演进导致本条位移，
     * 按同一规则（"再容许 1 条"）重算，不许改规则来凑。
     */
    static final double C2A_LO = 0.93D;
    static final double C2A_HI = 1.0D;
    /**
     * C2-b 的"岸地面高出水面"阈（格）。<b>派生式，非字面量</b>：= {@code floor(BANK_CUT_DEPTH)} =
     * <b>2</b>——需求 2 的下切量恰为 2 格，把阈钉成"下切真的落到了岸地面与水面的高差上"的量纲，
     * 改 {@code BANK_CUT_DEPTH} 会同步带动本阈（这是想要的耦合：判据跟机制，不跟巧合）。
     */
    static final int C2B_ABOVE_CELLS = (int) Math.floor(GTSRVoronoiRiverField.BANK_CUT_DEPTH);
    /**
     * <b>v1.20.41 P20 §23-B 的处置状态（本片 P20-S3d 新增，非 §22-B 原文；下面整段 §22-B 时期的推导
     * <b>原文逐字保留</b>，同 §14/§15 覆盖 §11 的处理法）</b>：§23-B 裁决把 C2-b 的采样域从「谷坡环列」
     * 换到「<b>岸带首列</b>」＝每个湿核外侧第一个干列（4 条断面 × 向外至多扫 3 列取首列）。新域<b>已按
     * 字面实现并每跑打印</b>（{@code C2B-READ} 的"现行域"段；取样门见 {@link #C2B_BANK_SCAN_MAX} 与
     * {@link #scanCrossSections}），<b>但带暂不迁到新域</b>——实测直接否证了它当前可钉性：岸带首列
     * n = <b>16</b>（88 条含湿核的断面里有 <b>72</b> 条在 3 列窗内取不到干列）、命中 = <b>0</b>
     * ⇒ M = <b>0.000</b>，高出格数 p10/中位/p90/max = 0/0/1/1。根因是<b>窗与机制的尺寸冲突</b>：C1 实测
     * 谷坡环宽中位 = <b>5.000</b> 格 &gt; 3 列窗 ⇒ 窗内的列必然还在 {@code rim = pool + RIM_EPS(1.5)}
     * 的开挖带里（那片域高出格数中位 = −1，正是 §23-B 用来否定环列的那个读数），而"第一个干列"按定义是
     * 贴水的那一列（{@code aboveWaterCells ≥ 0} 的最小列，实测中位 = 0）⇒ "≥2 格"在该域恒不成立。
     * 把带钉在 0.000 附近 = §11 禁止的"为凑绿定带"（比 §23-B 否掉的旧带更空），故本片<b>保留下面这条
     * §22-B 域上的带不动</b>，并把两个可钉的候选锚（放宽扫描窗 → 对照 D 的"无上限第一个干列"；改锚到
     * §23-B 理由句点名的"rim 之上的岸地" → 对照 C = 0.726 那条）作为口径裁决上抛主代理。证据与四域
     * 对照全表 = {@code plan/tmp/p20-s3d/c2b-domain-finding.md}。
     * <p>
     * C2-b 带（<b>谷坡环列</b>中岸地面高出水面 ≥{@link #C2B_ABOVE_CELLS} 格的占比）。高差一律经
     * {@link GTSRVoronoiRiverField#submergedAt} 的<b>同一个</b>出口数出来（{@link #aboveWaterCells}），
     * 本文件零 {@code pool−1} 算术复刻。
     * <p>
     * <b>由实测分布反解（§8 纪律）＋ 本片必须申报的一件事</b>：post 态实测 M = <b>0.094</b>
     * （环列 n = 1422、命中 133），环列"高出水面格数"分布 p10/中位/p90 = <b>−3 / −1 / +1</b>
     * ⇒ <b>谷坡环的地面中位数落在最高水格之下一格</b>。两个对照组读数：4 邻含湿核列的"真岸线"子集
     * = <b>0/370 = 0.000</b>；环外首列（贴谷缘的岸地）= <b>90/124 = 0.726</b>（高出格数 p10/中位/p90
     * = −2/+3/+6）。<b>结构性原因（不是漏接线）</b>：{@code heightCore} 外段把整条谷带压向
     * {@code rim = pool + RIM_EPS(1.5)}，内段再向床 lerp，S3 的下切又在环上削 1-2 格 ⇒ 环列本身就是
     * "水面附近的开挖带"，"高出水面 ≥2 格"在环域上必然稀少；真正把水面托起来的是环外的岸地。
     * <p>
     * <p>
     * 【下面"带的定法"整段是 <b>§22-B 旧域（谷坡环列）</b>的推导，<b>原文逐字保留</b>；该域自 §26 起
     * 降为<b>只报不钉</b>的对照诊断 A，理由见本 javadoc 顶部的处置段与 {@code plan/tmp/p20-s3d/}】
     * ⇒ 带的定法：二项 σ = √(M(1−M)/n) = 0.0077，4σ 统计带 = [0.063, 0.125]，<b>不采用</b>——
     * B3(S5)/B4(S6) 还要改地面侧（heightCore/TerrainVariants），紧带会在合法上演进时产假红
     * （§10 的"混代假红"形态）。改取<b>机制带</b>：下界 <b>0.05</b> ≈ 实测的一半（再向下取整；
     * 跌穿 = 环列里再没有人站在水面上方 ≥2 格 ⇒ 谷坡开挖带整体塌进水面以下）；上界 <b>0.20</b>
     * ≈ 实测的 2 倍（再向上取整；越界 = 环大面积站出水面 ⇒ 外段不再贴水、谷坡消失，需求 1 的硬边
     * 形态回来）。<b>本带钉的是当前基线形状，不是"岸坡下切已达成"的验收结论</b>——后者要看实机，
     * 且口径本身（分母取环列 vs 取环外岸地）已按 §22-B 原句实现，若终验认定应换成环外岸地列，
     * 那是主代理的口径裁决，不在本片擅改。本轮取值、<b>实机后校准</b>。
     * <p>
     * <b>§26 主代理裁决（2026-09-23）：C2-b 被钉域改为「断面出域第一列」</b>＝谷坡环之外、贴谷缘的岸地
     * （= {@link #C2B_BANK_SCAN_MAX} javadoc 里的<b>对照 C</b>，也正是 §23-B <b>理由句</b>点名的
     * "rim 之上的岸地"）。§23-B 的<b>机械表述</b>（"湿核外侧第一个干列、向外至多扫 3 列"）与它的理由句
     * 在这里不重合；S3d 把四个候选锚一次测清（新域 <b>0.000</b> / 无窗对照 D <b>0.014</b> / 被否证的环列
     * <b>0.094</b> / 理由句锚 <b>0.726</b>）并<b>拒绝</b>把带钉在 0.000 上、把裁决上抛——该拒绝正确
     * （擅自换锚 = 判据片替主代理做口径裁决），故 <b>以理由句为准</b>，锚点取 rim 之上的岸地首列。
     * <p>
     * <b>新带由实测反解（§8 纪律），取带规则与 C2-a 同族（"再容许 1 例"，不用二项 σ）</b>：实测
     * n = <b>124</b>、命中 = <b>90</b> ⇒ M = <b>0.726</b>，高出格数 p10/中位/p90 = −2 / <b>+3</b> / +6
     * ⇒ 下界 = ⌊(命中−1)/n × 100⌋/100 = ⌊71.77⌋/100 = <b>0.71</b>（命中 89 即 0.7177 仍绿、掉到 88
     * 即 0.7097 就红）；上界 <b>1.0，不反向钉</b>——本条方向是"每段岸地都站出水面 ≥2 格"，100% 即终态，
     * 反向风险（"谷坡消失、硬边回来"）由 C3 的 {@code bankCutAt} 值域带与 H2 端面横向带守，不在本带重复钉。
     * <b>本轮取实测反解，实机后校准</b>；S5b 改湖岸后若本条位移，按同一规则重算，不许改规则来凑。
     * <p>
     * <b>v1.20.42 P22 A1c 复跑处置：全域口径保持，不缩域不重钉</b>。任务包预期"枯竭域水面分母
     * 消失可能塌缩"——实测否证：本片复跑 <b>0.726（n=124 / 命中 90，与 §26 重钉读数逐位同）</b>。
     * 原因：被钉域（断面出域首列）的分子分母都是<b>纯场量</b>——heightAt 地面 vs 本列段池水位
     * {@code poolLevelAt}（枯竭域里仍是段界参考面，§22-B 旧域残文"该处无水面，池水位只是段界
     * 参考面"的既有口径），不依赖置水；A1a 只改"有没有真水"不改场（采样窗内无主干带列 ⇒
     * endFaceBed/潭下挖对该窗 heightAt 零足迹）。分母未塌缩 ⇒ 无可反解的重钉点，"同 H 组缩域 vs
     * 全域干床口径"二选一按实测定为<b>后者</b>（缩域反而丢掉"枯竭域岸地仍站出名义水面"这半
     * 个验收面）。带 [0.71,1.0] 一字不动。
     */
    /**
     * <b>P23 R1·S6 同规则重算：0.71 → 0.66</b>。B 组样本提档 200 心（段激活门后 32 心聚集样本
     * 失代表性，见 {@link #N_CENTERS}）连带把本域样本从 n=124 抬到 n=624——读数 M=0.671
     * （命中 419；高出格数 p10/中位/p90 = −3/+3/+6，与 §26 锚点读数同形）。按 §26 自订的
     * 取带规则（"再容许 1 例"，⌊(命中−1)/n×100⌋/100）重钉下界 = ⌊418/624×100⌋/100 = 0.66；
     * 上界 1.0 不反向钉。带钉的语义（"走出环外的岸地站在名义水面之上"）与锚点（断面出域首列）
     * 一字不动——这是该 javadoc 下方 §26 段明文允许的"按同一规则重算"，不是改规则凑绿。
     * 旧下界 0.71（n=124/命中 90/M=0.726 反解）的推导原文保留在下段。
     */
    static final double C2B_LO = 0.66D;
    static final double C2B_HI = 1.0D;
    /**
     * C2-b 新采样域（<b>岸带首列</b>，§23-B 裁决 1）的外扫列数上限（格）。<b>出处 = §23-B 裁决 1 的
     * 字面口径</b>："每个湿核外侧第一个干列（4 条断面 × 向外至多扫 3 列取首列）"⇒ 本值不是新增自由度，
     * 是裁定写死的采样窗；"干"的判据只走 {@link GTSRVoronoiRiverField#submergedAt} 一个出口
     * （{@code 干 ⇔ !submergedAt(h,pool) ⇔ aboveWaterCells ≥ 0}，与 {@link #aboveWaterCells} 的恒等式
     * 同源 ⇒ 判据侧零第二处 {@code pool−1} 水线算术，§23-A 的 T1 纪律、出口签名锁死 int）。
     * <p>
     * <b>本域自本片起"只报不钉"</b>（原因与四域对照实测见 {@link #C2B_LO} 顶部那段处置状态）：实测
     * 列 = 16、命中 = 0、占比 = 0.000、高出格数 p10/中位/p90/max = 0/0/1/1、外扫 3 列内取不到干列的断面
     * = 72。同一条走查里另计两条对照：<b>对照 D</b> = 取消 3 列窗、核外第一个干列（本域的唯一变量敏感性
     * 读数，用来区分"窗太窄"与"第一个干列按定义贴水"这两种退化原因）；<b>对照 C</b> = 断面出域第一列
     * （= §23-B 理由句里"rim 之上的岸地"的锚点，实测 0.726）。
     */
    static final int C2B_BANK_SCAN_MAX = 3;

    // ── v1.20.42（P22 A1c）遗忘之川域定位与水面类断言的采样参数（派生式）────────────────
    /** 湿列锚点粗扫窗半径（格）：2×TRUNK_SCALE——与 SanzuTrunkCoverageCheck 主窗同派生（≥3 个主干带波长 ⇒ 窗内必有带脊）。 */
    static final int TRUNK_HUNT_EXTENT = (int) (GTSRVoronoiRiverField.TRUNK_SCALE * 2);
    /** 湿列锚点粗扫步距（格）：SEPARATION/16——与 A4 主干带采样窗同派生。 */
    static final int TRUNK_HUNT_STRIDE = (int) (GTSRVoronoiRiverField.SEPARATION / 16);
    /** 遗忘之川域细扫步距（格）：SCAN_STRIDE×2 = 16（旧 H1 采样步距同一派生；sanzu 水道半宽 ~17 格 > 步距 ⇒ 河核不会被跳空）。 */
    static final int TRUNK_DOM_STRIDE = SCAN_STRIDE * 2;
    // ── P30-III-1a（v1.20.53 批I 后重锚）H2 衰减带剖面几何常量（派生式）──────────────────
    // 旧 FACE_DILATION=12 / TANGENT_RUN_CAP=48 随旧锚点域（trunk>0 ∧ s≥WET_MIN 端面入口列
    // + 切向面探针）整体退役删除——I1 死邻衰减把 s≥WET_MIN 等值线拉离段界数十格，8 邻
    // "换段 ∧ 枯竭"面判与切向 k≤END_FACE_LEN 探针双双永假（批I 收口实测 axisCand=0、
    // walks=0 的空集红），登记见 groupH javadoc 的 P30-III-1a 段。
    /**
     * H2 衰减带外向走步数上限（列）：SEPARATION/6 = 175。端面几何中心 = 三叉结点（t₁/t₂ 的
     * r→0 处），s 从段内值降到 {@link GTSRVoronoiRiverField#DRY_RIVERBED_CORE} 的实测带
     * 外沿 ≤ ~120 格（TAPER_DEAD=0.20 的 r 域换算 × warp 压缩，批I 后探针实测带宽
     * 2-47 列）⇒ 175 覆盖带外沿 + 死侧起点余量。
     */
    static final int H2_WALK_CAP = (int) (GTSRVoronoiRiverField.SEPARATION / 6);
    /**
     * H2 贴脊再居中搜索半窗（列）：8 ≥ 常态水道半宽实测 p90=7（B1 带读数）——剖面走在
     * border 脊（c≈0）上，横向偏移超出水道半宽即入谷壁混核列。
     */
    static final int H2_SNAP = 8;
    /**
     * H2 贴脊每步横向位移上限（列）：2——防再居中一跳跨池阶/谷壁造伪 Δ（探针对照：无此
     * 限的 |Δbed| 伪读 4.00 出自同位振荡跳列；受限后池恒定 run 内 |Δbed| ≤ 0.20）。
     */
    static final int H2_TRACK = 2;
    // ── 残潭组（I 组）猎取几何常量：已随 P25 D7 残潭退役整组删除（登记见 groupJ javadoc）──

    /** 名册下标（0 锈蚀草原 / 1 齿轮森林 / 2 黄铜荒漠 / 3 喷气沼泽）。 */
    static final String[] ROSTER = { "草原", "森林", "荒漠", "沼泽" };

    static int assertions;
    static int failures;
    static final List<String> LINES = new ArrayList<String>();

    public static void main(String[] args) {
        groupA();
        final List<int[]> centers = findCenters();
        say(
            "S-READ 河道中心样本=" + centers.size() + "（扫描窗 ±" + SCAN_EXTENT + " 步距 " + SCAN_STRIDE
                + "）");
        groupB(centers);
        groupC(centers);
        groupD(centers);
        groupE();
        // P23 R1·S6：F2（主干水径带）随主干宽河移除退役；水面类断言不复存在——H1 床阶语义的
        // 采样域改「激活段河核列」（s≥WET_MIN 即激活段），域扫描窗回到 ±SCAN_EXTENT（与 F/E 同窗）。
        final TrunkDomain dom = scanSegDomain();
        groupF();
        groupActivation(dom);
        groupG(centers);
        groupH(dom);
        // P25 D7：I 组（残潭）已整组退役（生产 swampRiverPoolAt 族删除，判据对象不存在）；
        // J 组（枯竭河床）为 P25 新增，且不再需要 I 组的真身份注入（谓词三参形态走默认档，
        // 与 PlacementGate.dryColumnAt ⑥ 腿 3 参调用同口径）。
        groupJ();
        // P26-B2（v1.20.49）：K 组床/滩料群系分布（材料身份需离线方块族装配，放最后——
        // 装配不触 L1 账本，A-H/J 组读数不受影响）。
        groupK();
        report();
    }

    // ══════════════════════ A 合同面 ══════════════════════

    static void groupA() {
        double min = 0.0D;
        double max = -1.0D;
        boolean deterministic = true;
        boolean seedSensitive = false;
        // 采样域取 ±SCAN_EXTENT/2（±64 的小窗对两个 seed 都可能整窗无河 ⇒ 假"零差异"）
        final int lim = SCAN_EXTENT / 2;
        for (int zi = -lim; zi <= lim; zi += SCAN_STRIDE * 4) {
            for (int xi = -lim; xi <= lim; xi += SCAN_STRIDE * 4) {
                final double v = GTSRVoronoiRiverField.strengthAt(SEED, xi, zi, 0);
                min = Math.min(min, v);
                max = Math.max(max, v);
                if (v != GTSRVoronoiRiverField.strengthAt(SEED, xi, zi, 0)) {
                    deterministic = false;
                }
                if (v != GTSRVoronoiRiverField.strengthAt(SEED ^ 0x5AL, xi, zi, 0)) {
                    seedSensitive = true;
                }
            }
        }
        check("A1 strengthAt 值域 ⊆ [-1,0]（0=无影响、-1=河道中心；全域采样）",
            min >= -1.0D && min <= 0.0D && max <= 0.0D, "min=" + f3(min) + " max=" + f3(max));
        check("A2 确定性：同 seed 同坐标双跑逐位一致（含 ThreadLocal disk 缓存命中/未命中两态）",
            deterministic, "双跑出现漂移");
        check("A3 换 seed 必换场（不是常数场）", seedSensitive, "换 seed 后采样域内零差异");
        // v1.20.39 T5/T8 重钉（plan §3.3）：A4 由"T4 骨架占位反向钉（恒 0）"反转为
        // "主干/巨湖场已按设计激活且确定性"——激活本身是 T5 的被验对象，占位钉随之退役；
        // 激活统计量（覆盖份额/巨湖/少支流/细长）由 SanzuTrunkCoverageCheck 专判，此处只钉合同面。
        boolean trunkShapeOk = true;
        boolean trunkDeterministic = true;
        boolean trunkAlive = false;
        // 采样域派生自主干尺度（T5/T8）：主干带波长约 TRUNK_SCALE，±TRUNK_SCALE/2 窗 + SEPARATION/16
        // 步距保证窗内至少有一条主干带脊线（±256 小窗实测 alive=false——带外整窗无 trunk 是几何事实）。
        final int trunkLim = (int) (GTSRVoronoiRiverField.TRUNK_SCALE / 2);
        final int trunkStride = (int) (GTSRVoronoiRiverField.SEPARATION / 16);
        for (int zi = -trunkLim; zi <= trunkLim && trunkShapeOk; zi += trunkStride) {
            for (int xi = -trunkLim; xi <= trunkLim; xi += trunkStride) {
                final double tv = GTSRVoronoiRiverField.trunkAt(SEED, xi, zi);
                final double lv = GTSRVoronoiRiverField.lakeAt(SEED, xi, zi);
                if (tv != GTSRVoronoiRiverField.trunkAt(SEED, xi, zi)
                    || lv != GTSRVoronoiRiverField.lakeAt(SEED, xi, zi)) {
                    trunkDeterministic = false;
                }
                if (tv < 0.0D || tv > 1.0D - GTSRVoronoiRiverField.SANZU_TRUNK_EDGE
                    || lv < -(GTSRVoronoiRiverField.LAKE_PRESSURE_NOISE_AMP
                        + GTSRVoronoiRiverField.LAKE_PRESSURE_NOISE_AMP2)
                    || lv > 1.0D + GTSRVoronoiRiverField.LAKE_PRESSURE_NOISE_AMP
                        + GTSRVoronoiRiverField.LAKE_PRESSURE_NOISE_AMP2) {
                    trunkShapeOk = false;
                }
                if (tv > 0.0D) {
                    trunkAlive = true;
                }
            }
        }
        // P25 D3③ 立、P32 T1-1 补 AMP2 漏记重导（R1 长证据 §6.4 口径）：lakeAt 返回值 = base +
        // LAKE_PRESSURE_NOISE_AMP(0.0025, λ220 腿)×n + LAKE_PRESSURE_NOISE_AMP2(0.0012, λ70 腿)×n
        // ⇒ 值域从 [0,1] 扩为 [−(AMP+AMP2), 1+(AMP+AMP2)] = [−0.0037, 1.0037]（总幅 0.0037 ≈
        // ±8.3 格湖缘径向微摆，dr/dP ≈ 2244 格/单位压力）。旧断言只按低频腿 AMP=0.0025 记账、未含
        // P26-B3 双倍频引入的 AMP2（总幅 0.0037 > 0.0025，理论上可假红、现状未爆）——本次补记。
        // 上界口径：NO_LAKE 哨兵路径不加噪（哨兵值恒 = 1.0），非哨兵列 P32 新场 clamp 后 P ≤ 0.925、
        // 加噪上探 ≈ 0.929 ⇒ 新场实际域 [−0.0037, 0.929] ⊂ 带；湖心负压/边界微越 1 都是轮廓噪声的
        // 设计内读数。
        check("A4 trunkAt/lakeAt 已激活（T5 接线）且确定性：trunk ∈ [0,1-EDGE]、lake ∈ [−(AMP+AMP2),1+(AMP+AMP2)]"
            + "（P26-B3 双频压力加性噪声总幅 ±0.0037 的设计内扩域，P32 T1-1 补 AMP2 漏记）、采样窗非恒零",
            trunkShapeOk && trunkDeterministic && trunkAlive,
            "shapeOk=" + trunkShapeOk + " det=" + trunkDeterministic + " alive=" + trunkAlive);
        final GTSRVoronoiRiverField.RiverStyle[] styles = GTSRVoronoiRiverField.RIVER_STYLE_BY_ROSTER;
        // v1.20.39 T5/T8 重钉（plan §5 档表族 4→5 约定）：第 5 元 = sanzu 主干宽河档
        //（widthScale == TRUNK_WIDTH_SCALE=3.5，无浅滩/无断流门——主干带内全水域）。
        check("A5 档表 5 元（前 4 元草原/森林=常态+浅滩、荒漠=断流、沼泽=沼地河；[4]=sanzu 主干宽河档。"
            + "v1.20.42 P22 A1c 标注：wetGated 自 A1a 起从生产链退役（wetAt 新门全域枯竭），荒漠元仅余"
            + "纯参数差异——本断言钉的是档表族形状（表值保留未删），断流语义已由 F1/F2 换立承接）",
            styles.length == 5 && styles[0].shoals && styles[1].shoals && styles[2].wetGated
                && !styles[3].shoals && !styles[3].wetGated
                && styles[4].widthScale == GTSRVoronoiRiverField.TRUNK_WIDTH_SCALE
                && !styles[4].shoals && !styles[4].wetGated,
            "length=" + styles.length + " sanzuWidthScale=" + (styles.length > 4 ? styles[4].widthScale : -1));
        check("A6 越界/缺席名册一律默认档（常态河）且沼泽 widthScale == 1.2×常态（v1.20.40 P19 §A.2 从 1.6"
            + "收窄——归因 U2 prered 读数 1.2/1.0；带 [1.05,1.45] 见 G1）",
            GTSRVoronoiRiverField.styleForRosterIndex(-1) == GTSRVoronoiRiverField.DEFAULT_STYLE
                && GTSRVoronoiRiverField.styleForRosterIndex(99) == GTSRVoronoiRiverField.DEFAULT_STYLE
                && styles[3].widthScale == 1.2D * styles[0].widthScale,
            "widthScale=" + styles[3].widthScale + "/" + styles[0].widthScale);
        check("A7 床档表值域：常态床目标=64.5（水面 68 ⇒ 水深 2-5 源头）、沼泽床目标=64.5±0.5"
            + "（<b>P26-B2（v1.20.49）重钉</b>：原「沼泽床 ∈ [66.5,67.5]＝水面近地」是湿河时代口径，"
            + "全域枯竭后未随动 ⇒ 沼泽干床砾最高 68.5≈SEA＝『部分河床砂砾偏高』第一主因；随枯竭下调"
            + "66.5→64.5（与常态同锚，跨粗格群系边界零池差）后沼泽 pool 档 {65,67,69}→{63,65,67}、"
            + "床域 [pool−2.5,pool−0.5] 全低于 SEA≥2.5 格——行为钉见 K4）",
            styles[0].bedTarget == GTSRVoronoiRiverField.BED_TARGET
                && GTSRVoronoiRiverField.BED_TARGET == 64.5D
                && styles[3].bedTarget == GTSRVoronoiRiverField.BED_TARGET
                && styles[3].bedTarget - styles[3].bedNoiseAmp >= 64.0D
                && styles[3].bedTarget + styles[3].bedNoiseAmp <= 65.0D,
            "normal=" + styles[0].bedTarget + " swamp=" + styles[3].bedTarget + "±" + styles[3].bedNoiseAmp);
        check("A8 VALLEY_LEVEL == WET_MIN（平底域精确等于置水域：水下平床、水外起坡——高度链不变量）",
            GTSRVoronoiRiverField.VALLEY_LEVEL == GTSRVoronoiRiverField.WET_MIN,
            "valley=" + GTSRVoronoiRiverField.VALLEY_LEVEL + " wetMin=" + GTSRVoronoiRiverField.WET_MIN);
    }

    // ══════════════════════ 河道中心采样 ══════════════════════

    /** s 场局部极大（≥ 四邻）即河道中心候选；按扫描序去重（相互隔开 ≥ WALK_LIMIT/2）。 */
    static List<int[]> findCenters() {
        final List<int[]> out = new ArrayList<int[]>();
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT && out.size() < N_CENTERS; z += SCAN_STRIDE) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT && out.size() < N_CENTERS; x += SCAN_STRIDE) {
                final double v = s(x, z);
                if (v < 0.8D) {
                    continue; // 只在河核强度带内找中心
                }
                if (v < s(x + SCAN_STRIDE, z) || v < s(x - SCAN_STRIDE, z) || v < s(x, z + SCAN_STRIDE)
                    || v < s(x, z - SCAN_STRIDE)) {
                    continue;
                }
                boolean dup = false;
                for (final int[] c : out) {
                    if (Math.abs(c[0] - x) <= WALK_LIMIT / 2 && Math.abs(c[1] - z) <= WALK_LIMIT / 2) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    out.add(new int[] { x, z });
                }
            }
        }
        return out;
    }

    static double s(int x, int z) {
        return -GTSRVoronoiRiverField.strengthAt(SEED, x, z, 0);
    }

    /**
     * 从 (x,z) 沿 (dx,dz) 走到 s ≤ 阈值的步数（-1 = 到 {@link #WALK_LIMIT} 仍在域内，即走到了
     * "顺河"方向——河沿走向连续数百格，属几何事实不是异常）。谷缘判定用 ≤（s 恒 ≥ 0，
     * 严格 < 会永真走不出谷）。
     */
    static int walkOut(int x, int z, int dx, int dz, double threshold) {
        for (int r = 1; r <= WALK_LIMIT; r++) {
            if (s(x + dx * r, z + dz * r) <= threshold) {
                return r - 1; // 阈值前的最后一格仍在域内（中心到域缘的半宽）
            }
        }
        return -1;
    }

    /**
     * 某档的水道半宽：四轴方向的<b>有界</b>方向取最小（顺河方向无界、跳过；四向全无界 = 三叉口，
     * 样本剔除记 -1）。斜向河的轴向往返会量到 w·√2 量级 ⇒ 读数是 [w, w√2] 的分布，判据取中位。
     * <p>
     * <b>P23 R1·S6 域缩样（本段激活门后干床段的正确采样形）</b>：方向有效 = 出走全程留在
     * <b>本段</b>（逐列 segKey 同心列）且在段内自然跌出河核（s&lt;WET_MIN）。改造前沿河向的
     * 走查会一路进入相邻段（未激活概念不存在）直到 WALK_LIMIT ⇒ 天然"无界跳过"；段激活门
     * 后相邻段 80% 被清零 ⇒ 沿河向在<b>段端</b>提前有界——不筛掉它会把"到段端的距离"冒充
     * "水道半宽"（本片重跑中位 5.0 / p90 13 的污染源）。同段判走生产 segKeyAt，零第二真值。
     */
    static int waterHalfWidth(int x, int z, int rosterIndex) {
        final long ownKey = GTSRVoronoiRiverField.segKeyAt(SEED, x, z);
        int best = Integer.MAX_VALUE;
        for (int d = 0; d < 4; d++) {
            final int dx = d == 0 ? 1 : d == 1 ? -1 : 0;
            final int dz = d == 2 ? 1 : d == 3 ? -1 : 0;
            int r = 1;
            boolean ownSeg = true;
            while (r <= WALK_LIMIT
                && -GTSRVoronoiRiverField.strengthAt(SEED, x + dx * r, z + dz * r, rosterIndex)
                    >= GTSRVoronoiRiverField.WET_MIN) {
                if (GTSRVoronoiRiverField.segKeyAt(SEED, x + dx * r, z + dz * r) != ownKey) {
                    ownSeg = false; // 沿河/结点穿段：本段内没有自然水缘，非"半宽"样本
                    break;
                }
                r++;
            }
            if (ownSeg && r <= WALK_LIMIT) {
                best = Math.min(best, r - 1);
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    /** 某档的谷缘半宽（走到 s==0 的有界方向最小；全无界记 -1）。 */
    static int valleyHalfWidth(int x, int z) {
        int best = Integer.MAX_VALUE;
        for (int d = 0; d < 4; d++) {
            final int dx = d == 0 ? 1 : d == 1 ? -1 : 0;
            final int dz = d == 2 ? 1 : d == 3 ? -1 : 0;
            final int w = walkOut(x, z, dx, dz, 0.0D);
            if (w >= 0) {
                best = Math.min(best, w);
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    // ══════════════════════ B 河宽分布 ══════════════════════

    static void groupB(List<int[]> centers) {
        final List<Integer> widths = new ArrayList<Integer>();
        for (final int[] c : centers) {
            final int w = waterHalfWidth(c[0], c[1], 0);
            if (w > 0) {
                widths.add(Integer.valueOf(w));
            }
        }
        final double median = median(widths);
        say("B-READ 常态水道半宽 样本=" + widths.size() + " p10/中位/p90=" + pct(widths, 10) + "/"
            + f3(median) + "/" + pct(widths, 90));
        check("B1 常态河半宽中位数 ∈ [3,4.5] 格（v1.20.40 P19 §A.2 重钉：WIDTH 0.14→0.08 收窄后的目标带"
            + "「常态半宽 3-4.5 格」。P23 R1·S6：样本域 = 激活段（waterHalfWidth 同段纪律）+ 200 心代表性"
            + "采样（旧 32 心在段激活门后成聚集样本——32 心读数中位 5.0/沼泽同量化 1.000 是聚集偏差；"
            + "代表性采样实测中位 4.0 居带、沼泽比 1.250，与 v1.20.40 校准读数逐位同归——WIDTH 未动，"
            + "带 [3,4.5] 一字不动、零放宽。<b>P32 批5 样本数腿重钉：下限 N_CENTERS/2(100)→98 独立钉住"
            + "（B1_MIN_SAMPLES）——S1 场重构吞 1 心 + D3 滨带外扩吞 1 心均设计内湖让位（J1 域），"
            + "分布逐位同批1，见常量 javadoc；中位带不动</b>）",
            widths.size() >= B1_MIN_SAMPLES && median >= 3.0D && median <= 4.5D,
            "中位=" + f3(median) + " n=" + widths.size());
    }

    // ══════════════════════ C 谷坡过渡带（v1.20.41 P20 S3 改口径）══════════════════════

    /**
     * 水缘外的<b>谷坡环宽</b>（列）：沿 4 轴先走开水道核（{@code s ≥ WET_MIN}），再数
     * {@link GTSRVoronoiRiverField#inBankBand} 为真的连续列，取有界方向的最小值（与
     * {@link #valleyHalfWidth} 同口径；四向全无界记 -1）。
     * <p>
     * 环的定义<b>一律取生产出口</b>——带噪声外缘（{@code S_ERODE + BANK_BAND_JITTER·valueNoise}）
     * 不在此重写，否则判据成了第二真值（正是 P20 S3 要消灭的东西）。
     */
    static int bankBandHalfWidth(int x, int z) {
        final long ownKey = GTSRVoronoiRiverField.segKeyAt(SEED, x, z);
        int best = Integer.MAX_VALUE;
        for (int d = 0; d < 4; d++) {
            final int dx = d == 0 ? 1 : d == 1 ? -1 : 0;
            final int dz = d == 2 ? 1 : d == 3 ? -1 : 0;
            int r = 1;
            // 1) 走开水道核（内缘恒 WET_MIN，不抖动——抖动只在外缘，见 inBankBand javadoc）；
            //    P23 R1·S6：穿出本段（换 segKey）= 沿河向，不是"坡宽"样本（waterHalfWidth 同纪律）。
            while (r <= WALK_LIMIT && s(x + dx * r, z + dz * r) >= GTSRVoronoiRiverField.WET_MIN) {
                if (GTSRVoronoiRiverField.segKeyAt(SEED, x + dx * r, z + dz * r) != ownKey) {
                    r = WALK_LIMIT + 1;
                    break;
                }
                r++;
            }
            if (r > WALK_LIMIT) {
                continue; // 顺河方向（含段端有界）：核无界/穿段，不是"坡宽"的样本
            }
            final int edge = r; // 水缘外第一列
            // 2) 数环的连续列（仍在段内；出段即止——环是本段的谷坡环）
            while (r <= WALK_LIMIT
                && GTSRVoronoiRiverField.segKeyAt(SEED, x + dx * r, z + dz * r) == ownKey
                && GTSRVoronoiRiverField.inBankBand(SEED, x + dx * r, z + dz * r, s(x + dx * r, z + dz * r))) {
                r++;
            }
            if (r <= WALK_LIMIT && r - edge > 0) {
                best = Math.min(best, r - edge);
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    /**
     * C2/C3 的<b>河核断面</b>累加器（避免为一条比例回传五个数组）：核心列数 / 床料列数 /
     * 环列数 / 断面内床料最长连段 / 环内列的下切量最小·最大 / 落在河核列上的环命中数（构造上必为
     * 0，见旧 C2 的 {@code ringOnCore} 自检）＋ S3c T2 的两条新量（逐河最长连段分布、环列高出水面
     * 命中数与湿邻子集）。
     */
    static final class CrossSection {
        int coreCols;
        int bedCols;
        int ringCols;
        int ringOnCore;
        int bestRun;
        double cutMin = Double.MAX_VALUE;
        double cutMax = -Double.MAX_VALUE;
        /** 有效断面数（至少含 1 列河核列）。 */
        int sections;
        /** 其中最长床料连段 ≥ {@link #C2A_RUN_MIN} 的断面数（辅助读数，C2-a 的断面口径）。 */
        int sectionsRun;
        /** 有效河数（4 条断面里至少 1 条有效的中心样本）。 */
        int rivers;
        /** 其中"任一断面出现 ≥{@link #C2A_RUN_MIN} 列连续床料"的河数（C2-a 分子）。 */
        int riversRun;
        /** 环列中岸地面高出水面 ≥{@link #C2B_ABOVE_CELLS} 格者（C2-b 分子；§23-B 的新域实测退化 ⇒
         *  本域暂仍是被钉的那条，见 {@link #C2B_LO} 顶部的处置状态）。 */
        int ringAbove;
        /** 辅助：4 邻里有"湿且河核"列的环列数（真正贴水的岸线）。 */
        int ringWet;
        /** 辅助：{@link #ringWet} 中高出水面命中者。 */
        int ringWetAbove;
        /** 逐河最长床料连段（C2-a 带反解用的分布）。 */
        final List<Integer> runs = new ArrayList<Integer>();
        /** 环列的"高出水面格数"分布（C2-b 带反解用；<0 = 低于水面）。 */
        final List<Integer> ringFb = new ArrayList<Integer>();
        /** 辅助：断面出域的第一列（谷坡环之外、贴谷缘的岸地）列数与高出水面命中数。 */
        int outsideCols;
        int outsideAbove;
        final List<Integer> outsideFb = new ArrayList<Integer>();
        /**
         * <b>C2-b 的 §23-B 新域（「岸带首列」，本片实现为<b>只报不钉</b>，理由见 {@link #C2B_LO} 顶部）
         * </b>：每个湿核外侧第一个干列的列数 / 其中高出水面 ≥{@link #C2B_ABOVE_CELLS} 格者 /
         * "高出水面格数"分布。
         */
        int bankCols;
        int bankAbove;
        final List<Integer> bankFb = new ArrayList<Integer>();
        /** 辅助：扫到上限仍未出现干列的断面数（干段/开挖带贴水的极端形态会抬这个数）。 */
        int bankNoDry;
        /**
         * <b>对照 D</b>（只报不钉）：与 {@link #bankCols} 同一条断面、同一个"核外第一个干列"锚点，
         * <b>但取消 {@link #C2B_BANK_SCAN_MAX} 的 3 列窗</b> ⇒ 本域唯一变量的敏感性读数（区分
         * "窗太窄"与"第一个干列按定义贴水"两种退化原因）。
         */
        int wideCols;
        int wideAbove;
        final List<Integer> wideFb = new ArrayList<Integer>();
    }

    /**
     * 本列地面<b>高出水面的格数</b>（>0 = 高出 n 格；0 = 恰在最高水格；<0 = 低于水面）。
     * <b>只经 {@link GTSRVoronoiRiverField#submergedAt} 一个谓词数出来</b>——本文件因此不存在
     * 第二处 {@code pool−1} 水线算术（T1 纪律）。恒等式：{@code !submergedAt(h−k, pool) ⇔
     * h−k ≥ pool−1 ⇔ 高出格数 h−(pool−1) ≥ k}，故"从 k=0 起连续成立的个数 − 1"即高出格数；
     * 一个都不成立时反向数低于水面的深度。值域 |n| ≤ {@code heightAt} 的 [40,110] ⇒ 循环上界 24
     * 是安全常量（触不到即截断，只影响辅助分布不影响判据分子）。
     */
    static int aboveWaterCells(int h, int pool) {
        int k = 0;
        while (k < 24 && !GTSRVoronoiRiverField.submergedAt(h - k, pool)) {
            k++;
        }
        if (k > 0) {
            return k - 1;
        }
        int down = 0;
        while (down < 24 && GTSRVoronoiRiverField.submergedAt(h + down, pool)) {
            down++;
        }
        return -down;
    }

    /**
     * 沿每个中心样本的 4 条断面走查（列距 1，步数上限 {@link #WALK_LIMIT}）：
     * {@code s ≥ WET_MIN} 的列进 C2-a 分母并走 {@link GTSRVoronoiRiverField#bedTopAtSurface} 单一出口
     * 取选型；{@code S_ERODE ≤ s < WET_MIN}（谷坡环）的列走
     * {@link GTSRVoronoiRiverField#bankCutAt} 单一出口取下切量，并走
     * {@link GTSRVoronoiRiverField#submergedAt} 单一出口取"高出水面"读数；{@code s < S_ERODE}
     * 即断出域、收尾。
     * <p>
     * <b>v1.20.41 P20 §23-B 追加的 C2-b 新域取样（本片实现为<b>只报不钉</b>，见 {@link #C2B_LO} 顶部）
     * </b>：每条断面在<b>越过一列湿核（{@code s ≥ WET_MIN} 且 {@code submergedAt} 为真）之后</b>，于核外列
     * （{@code s < WET_MIN}，含环列与出域列）里向外至多扫 {@link #C2B_BANK_SCAN_MAX} 列，取<b>第一个
     * 干列</b>（{@code !submergedAt}）作为该湿核的"岸带首列"样本，每断面至多一列；同一条走查顺带计
     * <b>对照 D</b> = 取消该 3 列窗的第一个干列（本域唯一变量的敏感性读数）。被钉的仍是 {@code ringAbove}
     * 那条环列域（§23-B 的新域实测退化到无带可反解，换锚裁决上抛主代理）。
     * <p>
     * "是否没水"自 v1.20.41 P20 S3c 起<b>不再在本文件出现</b>：一律经
     * {@link GTSRVoronoiRiverField#submergedAt(int, int)}（生产侧落块器共用同一个出口）。C2-b 的
     * "岸地面高出水面 ≥k 格"经 {@link #aboveWaterCells} 数出来，其恒等式是
     * {@code !submergedAt(h−k, pool) ⇔ h−k ≥ pool−1 ⇔ 高出格数 h−(pool−1) ≥ k}（水面 = 池水位 pool、
     * 最高水格 pool−1 的既有口径）——"水线在哪"只有一处定义，本文件零 {@code pool−1} 算术。
     * {@code h ± k} 不担心回绕：{@code heightAt} 值域被 G3 钉在 [40,110]。
     */
    static void scanCrossSections(List<int[]> centers, CrossSection acc) {
        for (final int[] c : centers) {
            int riverBestRun = 0;
            boolean riverValid = false;
            for (int d = 0; d < 4; d++) {
                final int dx = d == 0 ? 1 : d == 1 ? -1 : 0;
                final int dz = d == 2 ? 1 : d == 3 ? -1 : 0;
                int run = 0;
                int sectionBestRun = 0;
                boolean sectionCore = false;
                boolean outSide = false;
                // —— C2-b 的 §23-B 新域（「岸带首列」）与对照 D 的本断面状态 ——
                boolean wetCoreSeen = false;
                boolean bankTaken = false;
                boolean wideTaken = false;
                int bankScan = 0;
                for (int r = 1; r <= WALK_LIMIT; r++) {
                    final int x = c[0] + dx * r;
                    final int z = c[1] + dz * r;
                    final double sv = s(x, z);
                    // —— C2-b 的 §23-B 新域（「岸带首列」）与对照 D（取消窗口的同一锚点）：
                    // 核外列（环列与出域列都算核外）先过这道取样门 = 湿核外侧第一个<b>干</b>列
                    // （"人站在岸上脚下那一列"），新域至多外扫 C2B_BANK_SCAN_MAX 列、每断面至多取一列。
                    // 干/湿与高出格数都只经 submergedAt 一个出口（aboveWaterCells 的恒等式），
                    // 判据侧零第二处 pool−1 水线算术。
                    if (wetCoreSeen && sv < GTSRVoronoiRiverField.WET_MIN && (!bankTaken || !wideTaken)) {
                        final boolean tryBank = !bankTaken && bankScan < C2B_BANK_SCAN_MAX;
                        if (tryBank) {
                            bankScan++;
                        }
                        final int bh = ProsperityTerrainProfile.heightAt(SEED, x, z);
                        final int bp = GTSRVoronoiRiverField.poolLevelAt(SEED, x, z, 0);
                        if (!GTSRVoronoiRiverField.submergedAt(bh, bp)) {
                            final int bf = aboveWaterCells(bh, bp);
                            if (tryBank) {
                                bankTaken = true;
                                acc.bankCols++;
                                acc.bankFb.add(Integer.valueOf(bf));
                                if (bf >= C2B_ABOVE_CELLS) {
                                    acc.bankAbove++;
                                }
                            }
                            if (!wideTaken) {
                                wideTaken = true;
                                acc.wideCols++;
                                acc.wideFb.add(Integer.valueOf(bf));
                                if (bf >= C2B_ABOVE_CELLS) {
                                    acc.wideAbove++;
                                }
                            }
                        }
                    }
                    if (sv < GTSRVoronoiRiverField.S_ERODE) {
                        if (!outSide) {
                            outSide = true;
                            // 辅助读数（只报不钉）：断面出域的第一列 = 谷坡环之外、贴谷缘的"岸地"。
                            // 对照组用途：环列被 heightCore 外段压向 rim = pool + RIM_EPS、再被
                            // bankCutAt 削 1-2 格 ⇒ "高出水面 ≥2 格"在环上结构性稀少；"岸地是不是
                            // 真站在水面上方 ≥2 格"要看环外列。本片不自作主张换 C2-b 的分母。
                            //   ↑ 上一句是 S3c 的原文（保留）。§23-B 已把 C2-b 的分母换成「岸带首列」
                            // = 湿核外侧第一个干列（上方取样门），本行这条"贴谷缘岸地"仍只是对照组读数。
                            final int op = GTSRVoronoiRiverField.poolLevelAt(SEED, x, z, 0);
                            final int of = aboveWaterCells(ProsperityTerrainProfile.heightAt(SEED, x, z), op);
                            acc.outsideCols++;
                            acc.outsideFb.add(Integer.valueOf(of));
                            if (of >= C2B_ABOVE_CELLS) {
                                acc.outsideAbove++;
                            }
                        }
                        break; // 既非核也非环：断面出域
                    }
                    if (sv < GTSRVoronoiRiverField.WET_MIN) {
                        if (GTSRVoronoiRiverField.inBankBand(SEED, x, z, sv)) {
                            final double cut = GTSRVoronoiRiverField.bankCutAt(SEED, x, z);
                            acc.ringCols++;
                            acc.cutMin = Math.min(acc.cutMin, cut);
                            acc.cutMax = Math.max(acc.cutMax, cut);
                            // C2-b：岸地面 vs 本列段池水位（高出格数只经 submergedAt 数出来，见
                            // aboveWaterCells 的恒等式 ⇒ 本文件零第二处水线算术）
                            final int rpool = GTSRVoronoiRiverField.poolLevelAt(SEED, x, z, 0);
                            final int rh = ProsperityTerrainProfile.heightAt(SEED, x, z);
                            final int fb = aboveWaterCells(rh, rpool);
                            acc.ringFb.add(Integer.valueOf(fb));
                            if (fb >= C2B_ABOVE_CELLS) {
                                acc.ringAbove++;
                            }
                            if (touchingWetCore(x, z)) {
                                acc.ringWet++;
                                if (fb >= C2B_ABOVE_CELLS) {
                                    acc.ringWetAbove++;
                                }
                            }
                        }
                        continue; // 环列不是河核列，不进旧 C2 的"选型率"分母
                    }
                    sectionCore = true;
                    acc.coreCols++;
                    final int pool = GTSRVoronoiRiverField.poolLevelAt(SEED, x, z, 0);
                    final int h = ProsperityTerrainProfile.heightAt(SEED, x, z);
                    final boolean sub = GTSRVoronoiRiverField.submergedAt(h, pool);
                    if (sub) {
                        wetCoreSeen = true; // C2-b（§23-B）：过了湿核，外侧第一个干列才成为样本
                    }
                    if (GTSRVoronoiRiverField.inBankBand(SEED, x, z, sv)) {
                        acc.ringOnCore++; // 构造上不可达（inBankBand 对 s≥WET_MIN 恒 false）
                    }
                    if (GTSRVoronoiRiverField.bedTopAtSurface(SEED, x, z, sv, sub)) {
                        acc.bedCols++;
                        run++;
                        acc.bestRun = Math.max(acc.bestRun, run);
                        sectionBestRun = Math.max(sectionBestRun, run);
                    } else {
                        run = 0;
                    }
                }
                if (wetCoreSeen && !bankTaken) {
                    acc.bankNoDry++; // 外扫 3 列内没出现干列 ⇒ 该侧无"岸"，不入 C2-b 分母
                }
                if (sectionCore) {
                    acc.sections++;
                    if (sectionBestRun >= C2A_RUN_MIN) {
                        acc.sectionsRun++;
                    }
                    riverValid = true;
                    riverBestRun = Math.max(riverBestRun, sectionBestRun);
                }
            }
            if (riverValid) {
                acc.rivers++;
                if (riverBestRun >= C2A_RUN_MIN) {
                    acc.riversRun++;
                }
                acc.runs.add(Integer.valueOf(riverBestRun));
            }
        }
    }

    /**
     * 本列 4 邻里是否存在"河核且 {@code wetAt}"的列（真正贴水的岸线；荒漠干段的环列不贴水）。
     * 档口径与本扫描一致取 roster 0（草原/森林常态河档）。
     */
    static boolean touchingWetCore(int x, int z) {
        for (int d = 0; d < 4; d++) {
            final int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
            final int nz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
            if (s(nx, nz) >= GTSRVoronoiRiverField.WET_MIN && GTSRVoronoiRiverField.wetAt(SEED, nx, nz, 0)) {
                return true;
            }
        }
        return false;
    }

    static void groupC(List<int[]> centers) {
        // —— 旧口径（v1.20.40 P19 §A.5：水缘→谷缘 s==0）：v1.20.41 起降为<b>读数</b>，不再是断言 ——
        final List<Integer> legacy = new ArrayList<Integer>();
        for (final int[] c : centers) {
            final int valley = valleyHalfWidth(c[0], c[1]);
            final int water = waterHalfWidth(c[0], c[1], 0);
            if (valley > 0 && water > 0) {
                legacy.add(Integer.valueOf(valley - water));
            }
        }
        say("C-READ 旧口径 谷坡半宽（水缘→谷缘 s==0）样本=" + legacy.size() + " p10/中位/p90="
            + pct(legacy, 10) + "/" + f3(median(legacy)) + "/" + pct(legacy, 90)
            + "（v1.20.40 P19 §A.5 带 [15,35]——v1.20.41 起只报不钉，理由见 C1）");

        // —— C1 新口径：水缘→谷坡环外缘（inBankBand 为真的连续列宽）——
        final List<Integer> band = new ArrayList<Integer>();
        for (final int[] c : centers) {
            final int w = bankBandHalfWidth(c[0], c[1]);
            if (w > 0) {
                band.add(Integer.valueOf(w));
            }
        }
        final double median = median(band);
        say("C-READ 谷坡环宽（水缘→inBankBand 外缘；外缘抖动 " + GTSRVoronoiRiverField.BANK_BAND_JITTER
            + " @λ=" + GTSRVoronoiRiverField.CUT_NOISE_SCALE + "）样本=" + band.size() + " p10/中位/p90="
            + pct(band, 10) + "/" + f3(median) + "/" + pct(band, 90));
        check("C1 谷坡环宽中位数 ∈ [" + C1_BAND_LO + "," + C1_BAND_HI + "] 格（v1.20.41 P20 S3 按新环重钉："
            + "口径从「水缘→谷缘 s==0」改为「水缘→谷坡环外缘」——需求 1 之后可见的过渡带就是 "
            + "inBankBand 那一段，s==0 的谷缘距里 ~85% 是未被河料触碰的原群系地面，钉它等于钉没有交付物"
            + "的宽度。**旧带 [15,35]（v1.20.40 P19 §A.5 两段式压低+收窄校准带，归因 U2/U34 prered 中位 "
            + "19.0）原文保留在此并作废**——旧口径读数仍在上方 C-READ 行（实测中位未随 S3 漂移）",
            band.size() >= N_CENTERS / 2 && median >= C1_BAND_LO && median <= C1_BAND_HI,
            "中位=" + f3(median) + " n=" + band.size() + " 旧口径中位=" + f3(median(legacy)));

        // —— C2/C3 河核断面：选型率读数（旧口径，只报）＋ 新两条 + 下切量值域（都只消费生产出口）——
        final CrossSection xs = new CrossSection();
        scanCrossSections(centers, xs);
        final double exposure = xs.coreCols == 0 ? -1.0D : xs.bedCols / (double) xs.coreCols;
        say("C-READ 河核断面（中心样本 " + centers.size()
            + " × 4 向，列距 1）：河核列=" + xs.coreCols + " 床料列=" + xs.bedCols + " 滩料列="
            + (xs.coreCols - xs.bedCols) + " ⇒ 床料出露率=" + f3(exposure)
            + "；断面内床料最长连段=" + xs.bestRun + " 列；环列=" + xs.ringCols + " 下切量 ∈ ["
            + f3(xs.cutMin) + "," + f3(xs.cutMax) + "]");
        // 旧 C2（河核断面床料出露率）v1.20.41 P20 §22-B 起<b>只报不钉</b>——口径错因与不许动的
        // 三条变绿手段全部写在 C2_LO/C2_HI 的注释里。
        say("C-READ 旧口径 河核断面床料出露率=" + f3(exposure) + "（旧带 [" + C2_LO + "," + C2_HI
            + "]，分母取河核列、与谷坡环不相交 ⇒ 结构性恒零，§22-B 重定口径后降为读数）"
            + " 河核列=" + xs.coreCols + " 滩料列=" + (xs.coreCols - xs.bedCols) + " 环命中核列="
            + xs.ringOnCore);
        // —— C2-a 床料连段 ≥3 列的河占比（"看得见河床"＝床料出露的连续性）——
        final double runShare = xs.rivers == 0 ? -1.0D : xs.riversRun / (double) xs.rivers;
        say("C2A-READ 逐河最长床料连段（" + C2A_RUN_MIN + " 列为阈）：有效河=" + xs.rivers
            + " 命中=" + xs.riversRun + " 占比=" + f3(runShare) + " 连段 p10/中位/p90/max="
            + pct(xs.runs, 10) + "/" + f3(median(xs.runs)) + "/" + pct(xs.runs, 90) + "/"
            + pct(xs.runs, 100) + "；辅助断面口径：有效断面=" + xs.sections + " 命中=" + xs.sectionsRun
            + " 占比=" + f3(xs.sections == 0 ? -1.0D : xs.sectionsRun / (double) xs.sections));
        check("C2-a 断面床料连段 ≥" + C2A_RUN_MIN + " 列的「河」占比 ∈ [" + C2A_LO + "," + C2A_HI
            + "]（v1.20.41 P20 §22-B 重定的 C2 真身之一：需求 2「看不到河床」= 床料出露的<b>连续性</b>。"
            + "分母 = 至少含一列河核列的中心样本（河），分子 = 4 条断面里任一条出现 ≥" + C2A_RUN_MIN
            + " 列连续床料的河；连段走 bedTopAtSurface＋submergedAt 两个生产出口，本文件零选型复刻。"
            + "**口径边界（§22-C 保留）：分母未剔 dropColumn 跳铺列 ⇒ 它是「选型率」口径的连续性，"
            + "落差面的真实落块由 H 组守**）",
            xs.rivers >= N_CENTERS / 2 && runShare >= C2A_LO && runShare <= C2A_HI,
            "河占比=" + f3(runShare) + " 有效河=" + xs.rivers + " 命中=" + xs.riversRun + " 最长连段="
                + xs.bestRun + " 列 断面口径占比=" + f3(xs.sections == 0 ? -1.0D
                    : xs.sectionsRun / (double) xs.sections));
        // —— C2-b：§23-B 新域（岸带首列）与对照 D 只报不钉；被钉的那条仍是 §22-B 的环列域（原因见 C2B_LO）——
        final double bankShare = xs.bankCols == 0 ? -1.0D : xs.bankAbove / (double) xs.bankCols;
        final double wideShare = xs.wideCols == 0 ? -1.0D : xs.wideAbove / (double) xs.wideCols;
        final double aboveShare = xs.ringCols == 0 ? -1.0D : xs.ringAbove / (double) xs.ringCols;
        // §26 主代理裁决：C2-b 的被钉域 = 断面出域首列（rim 之上的岸地），旧环列域降为只报不钉。
        final double outsideShare = xs.outsideCols == 0 ? -1.0D
            : xs.outsideAbove / (double) xs.outsideCols;
        say("C2B-READ §23-B 新域「岸带首列」（每个湿核外侧第一个干列，4 条断面 × 向外至多扫 "
            + C2B_BANK_SCAN_MAX + " 列取首列；阈 = floor(BANK_CUT_DEPTH)，干湿与高出格数只经 submergedAt"
            + " 数出来）<b>只报不钉</b>：列=" + xs.bankCols + " 命中=" + xs.bankAbove + " 占比="
            + f3(bankShare) + " 高出格数 p10/中位/p90/max=" + pct(xs.bankFb, 10) + "/"
            + f3(median(xs.bankFb)) + "/" + pct(xs.bankFb, 90) + "/" + pct(xs.bankFb, 100)
            + " 外扫 " + C2B_BANK_SCAN_MAX + " 列内无干列的断面=" + xs.bankNoDry
            + "；对照 D（同一锚点、取消 " + C2B_BANK_SCAN_MAX + " 列窗＝核外第一个干列）：列="
            + xs.wideCols + " 命中=" + xs.wideAbove + " 占比=" + f3(wideShare) + " 高出格数 p10/中位/p90/max="
            + pct(xs.wideFb, 10) + "/" + f3(median(xs.wideFb)) + "/" + pct(xs.wideFb, 90) + "/"
            + pct(xs.wideFb, 100)
            + "；对照 A（§22-B 旧域·谷坡环列，§26 起<b>只报不钉</b>）：环列=" + xs.ringCols
            + " 命中=" + xs.ringAbove + " 占比=" + f3(aboveShare) + " 高出格数 p10/中位/p90="
            + pct(xs.ringFb, 10) + "/" + f3(median(xs.ringFb)) + "/" + pct(xs.ringFb, 90)
            + "；对照 B（4 邻含湿核列的真岸线）：环列=" + xs.ringWet + " 命中=" + xs.ringWetAbove
            + " 占比=" + f3(xs.ringWet == 0 ? -1.0D : xs.ringWetAbove / (double) xs.ringWet)
            + "；对照 C（<b>自 §26 起为被钉域</b>·断面出域首列＝贴谷缘岸地＝§23-B 理由句所指「rim 之上的"
            + "岸地」，带 [" + C2B_LO + "," + C2B_HI + "]）：列="
            + xs.outsideCols + " 命中=" + xs.outsideAbove + " 占比="
            + f3(xs.outsideCols == 0 ? -1.0D : xs.outsideAbove / (double) xs.outsideCols)
            + " 高出格数 p10/中位/p90=" + pct(xs.outsideFb, 10) + "/" + f3(median(xs.outsideFb)) + "/"
            + pct(xs.outsideFb, 90));
        check("C2-b 岸地面高于水面 ≥" + C2B_ABOVE_CELLS + " 格的「<b>断面出域首列</b>＝走出谷坡环之外、"
            + "贴谷缘的岸地（即 rim 之上的岸地）」占比 ∈ [" + C2B_LO + "," + C2B_HI + "]（v1.20.41 P20 "
            + "<b>§26 主代理裁决</b>换定的锚点：需求 2「看不到河床，要岸坡下切」里"
            + "\"人站在岸上、脚下看得见河床\"那一面的直接度量。分母 = 4 条断面走出谷坡环（{@code s < "
            + "S_ERODE}）时的第一列、每断面至多一列；分子 = 该列 heightAt 地面比本列段池水位高出 ≥"
            + C2B_ABOVE_CELLS + " 格；干湿与高出格数只经 aboveWaterCells→submergedAt 一个谓词数出来，"
            + "全文件零第二处 {@code pool−1} 水线算术。**带由实测反解（§8）**：n = 124 / 命中 = 90 / "
            + "M = 0.726、高出格数 p10/中位/p90 = −2/+3/+6 ⇒ 下界 0.71（取带规则与 C2-a 同族\"再容许 1 "
            + "例\"）、上界 1.0 不反向钉；实机后校准。**P23 R1·S6 同规则重算**：B 组 200 心代表性采样把本域样本抬到 n=624/命中 419 ⇒ M=0.671，按同一规则（再容许 1 例）重钉下界 0.66（见 C2B_LO 注释），锚点与语义一字不动。**被实测否证的三个候选锚**（§22-B 旧域环列 0.094、"
            + "§23-B 字面新域 0.000、无窗对照 D 0.014）自本节起<b>全部只报不钉</b>，读数见上方 C2B-READ"
            + "与 {@code plan/tmp/p20-s3d/c2b-domain-finding.md}。）"
            + "〔<b>旧域（§22-B 谷坡环列）当时那句断言的中段残文·逐字保留</b>，自 §26 起该域只报不钉："
            + "「…不重写带噪声外缘），分子 = 该列 heightAt 地面比本列段池水位高出 ≥" + C2B_ABOVE_CELLS
            + " 格；高差只经 "
            + "aboveWaterCells→submergedAt 一个谓词数出来，全文件不存在第二处水线算术。**口径边界**："
            + "分母含荒漠干段的环列（该处无水面，池水位只是段界参考面）⇒ 读数含干段；C2B-READ 的两组"
            + "对照（湿邻真岸线 / 环外首列岸地）只报不钉。<b>本带钉的是 0.094 这一当前基线形状，不是"
            + "\"岸坡下切已达成\"的验收结论</b>——环列本身是开挖带（外段压向 pool+RIM_EPS、内段再向床、"
            + "环上再削 1-2 格 ⇒ 中位高出格数 = −1），换分母属主代理的口径裁决，见 C2B_LO 注释。」】"
            + "【§23-B 处置状态（P20-S3d）：裁定要求换的「岸带首列」域已按字面实现并打印在本行上方的"
            + " C2B-READ（\"§23-B 新域\"段），但当前基线上实测 n=16 / 命中 0 / 占比 0.000、高出格数"
            + " p10/中位/p90/max = 0/0/1/1（88 条含湿核断面里 72 条在 3 列窗内取不到干列）⇒ 无带可反解"
            + "（钉它 = §11 禁止的凑绿带）。两成因已分开实测：① 窗太窄——3 列窗 < C1 实测环宽中位 5.000 格"
            + " ⇒ 窗内必然仍在开挖带；② 定义级——对照 D 取消窗口后 n 抬到 71 但命中只有 1（0.014、高出格数"
            + " p90 = 0），因为\"第一个干列\"按 {@code submergedAt(h,pool) ⇔ h < pool−1} 的定义恰落在最高"
            + " 水格上（高出格数 = 0），与阈 2 互相拆台。有诊断力的锚是 C2B-READ 的对照 C（断面出域首列"
            + " ＝ §23-B 理由句点名的\"rim 之上的岸地\"，124/90 = 0.726）。带暂留环列域不动，换锚裁决上抛，"
            + "四域对照全表见 plan/tmp/p20-s3d/c2b-domain-finding.md。<b>§26 主代理已裁：选 A</b>——以理由"
            + "句的锚（本条的对照 C＝断面出域首列＝rim 之上的岸地）作为 C2-b 被钉域，带 [0.71, 1.0]；"
            + "谷坡环列域、§23-B 字面新域与无窗对照 D 三处一并降为<b>只报不钉</b>】",
            xs.outsideCols >= N_CENTERS && outsideShare >= C2B_LO && outsideShare <= C2B_HI,
            "出域首列占比=" + f3(outsideShare) + " 列=" + xs.outsideCols + " 命中=" + xs.outsideAbove
                + " 高出格数 p10/中位/p90=" + pct(xs.outsideFb, 10) + "/" + f3(median(xs.outsideFb))
                + "/" + pct(xs.outsideFb, 90) + " ｜ 只报不钉的三域：§22-B 旧环列=" + f3(aboveShare)
                + "（n=" + xs.ringCols + "）、§23-B 字面新域=" + f3(bankShare) + "（n=" + xs.bankCols
                + "）、无窗对照 D=" + f3(wideShare) + "（n=" + xs.wideCols + "）");
        check("C3 谷坡环下切量 ∈ [" + (GTSRVoronoiRiverField.BANK_CUT_DEPTH / 2) + ","
            + GTSRVoronoiRiverField.BANK_CUT_DEPTH + "] 格且环非退化（P20 §20：bankCutAt 值域下界取半"
            + "而非 0——valueNoise 负瓣若清零，环内出现\"整段没切\"的斑块、出露宽度不连续；环列数下限"
            + " = 河核列数的 1/4，防\"环空转\"让 C2 的选型出口退化成纯三目）",
            xs.ringCols >= xs.coreCols / 4 && xs.cutMin >= GTSRVoronoiRiverField.BANK_CUT_DEPTH / 2
                && xs.cutMax <= GTSRVoronoiRiverField.BANK_CUT_DEPTH,
            "cut∈[" + f3(xs.cutMin) + "," + f3(xs.cutMax) + "] 环列=" + xs.ringCols + " 河核列="
                + xs.coreCols);
    }

    // ══════════════════════ D 蜿蜒度 ══════════════════════

    static void groupD(List<int[]> centers) {
        double sum = 0.0D;
        int n = 0;
        double worst = Double.MAX_VALUE;
        for (int k = 0; k < centers.size() && n < N_TRACES; k += Math.max(1, centers.size() / N_TRACES)) {
            final int[] c = centers.get(k);
            // 初始朝向 = 16 向试探中 s 最大者（沿河起歩，不横切）
            double hx = 1.0D;
            double hz = 0.0D;
            double bestInit = -1.0D;
            for (int a = 0; a < 16; a++) {
                final double ang = a * Math.PI / 8.0D;
                final double nx = Math.cos(ang);
                final double nz = Math.sin(ang);
                final double v = s((int) Math.round(c[0] + nx * TRACE_STEP),
                    (int) Math.round(c[1] + nz * TRACE_STEP));
                if (v > bestInit) {
                    bestInit = v;
                    hx = nx;
                    hz = nz;
                }
            }
            int x = c[0];
            int z = c[1];
            double path = 0.0D;
            for (int t = 0; t < TRACE_STEPS; t++) {
                int bx = x;
                int bz = z;
                int bhx = 1;
                int bhz = 0;
                double bestScore = -Double.MAX_VALUE;
                for (int a = 0; a < 16; a++) {
                    final double ang = a * Math.PI / 8.0D;
                    final double nx = Math.cos(ang);
                    final double nz = Math.sin(ang);
                    // 小前向偏置：防 180° 折返，但允许在叉口急转（大偏置会把追踪锁死在直线上）
                    final int tx = (int) Math.round(x + nx * TRACE_STEP);
                    final int tz = (int) Math.round(z + nz * TRACE_STEP);
                    final double score = s(tx, tz) + 0.05D * (nx * hx + nz * hz);
                    if (score > bestScore) {
                        bestScore = score;
                        bx = tx;
                        bz = tz;
                        bhx = tx - x;
                        bhz = tz - z;
                    }
                }
                final double step = Math.hypot(bx - x, bz - z);
                if (step <= 0.0D) {
                    break;
                }
                final double nh = Math.hypot(bhx, bhz);
                path += step;
                x = bx;
                z = bz;
                hx = bhx / nh;
                hz = bhz / nh;
            }
            final double straight = Math.hypot(x - c[0], z - c[1]);
            if (path > 0.0D && straight > TRACE_STEP) {
                final double sinuosity = path / straight;
                sum += sinuosity;
                worst = Math.min(worst, sinuosity);
                n++;
            }
        }
        final double mean = n == 0 ? 0.0D : sum / n;
        say("D-READ 蜿蜒度（中心线追踪 长=" + (TRACE_STEPS * TRACE_STEP) + " 格/段）样本=" + n + " 均="
            + f3(mean) + " 最直段=" + f3(worst == Double.MAX_VALUE ? 0 : worst));
        check("D1 中心线曲折率均值 > 1.2（两级 Disk jitter 蜿蜒的验收读数）",
            n >= N_TRACES / 2 && mean > 1.2D, "均=" + f3(mean) + " n=" + n);
    }

    // ══════════════════════ E 支流连通（三叉点） ══════════════════════

    static void groupE() {
        int forks = 0;
        int checked = 0;
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT; z += FORK_STRIDE) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT; x += FORK_STRIDE) {
                checked++;
                final long h00 = cellHash(x, z);
                final long h10 = cellHash(x + FORK_STRIDE, z);
                final long h01 = cellHash(x, z + FORK_STRIDE);
                final long h11 = cellHash(x + FORK_STRIDE, z + FORK_STRIDE);
                final List<Long> distinct = new ArrayList<Long>();
                for (final long h : new long[] { h00, h10, h01, h11 }) {
                    if (!distinct.contains(Long.valueOf(h))) {
                        distinct.add(Long.valueOf(h));
                    }
                }
                if (distinct.size() >= 3) {
                    forks++;
                }
            }
        }
        say("E-READ 三叉点（2×2 列块 ≥3 个最近细胞）=" + forks + " / 扫描块 " + checked + "（步距 "
            + FORK_STRIDE + " ⇒ 实测顶点密度 " + f3(forks / (double) checked / (FORK_STRIDE * FORK_STRIDE))
            + " 块⁻²）");
        // 理论顶点密度 ≈ 每细胞 2 顶点 / SEPARATION²（Voronoi 恒等式）；断言带 = 理论值的 [1/4, 4] 倍
        final double theory = 2.0D / (GTSRVoronoiRiverField.SEPARATION * GTSRVoronoiRiverField.SEPARATION);
        final double measured = forks / (double) checked / (FORK_STRIDE * FORK_STRIDE);
        check("E1 支流分叉非零且密度与 Voronoi 理论同阶（实测 ∈ 理论 2/SEPARATION² 的 [1/4,4] 倍"
            + "——河网连通的分叉证据，不是孤立平行线）",
            forks > 0 && measured >= theory / 4.0D && measured <= theory * 4.0D,
            "实测=" + f3(measured) + " 理论=" + f3(theory) + " forks=" + forks);
    }

    static long cellHash(int x, int z) {
        return GTSRVoronoiRiverField.nearestCellHash(SEED, x, z);
    }

    // ══════════════════════ 遗忘之川域定位与共享扫描（v1.20.42 P22 A1c） ══════════════════════

    // P23 R1·S6：findSanzuWetAnchor（湿列锚点定位）随 F2 退役删除——wetAt 恒 false 后窗内无
    // wet 列，水面类断言（F2/H1 旧口径/H2 旧锚）整体换域，锚点定位无消费者即删。

    /**
     * 激活段域一趟细扫（H1/F3 共用，P23 R1·S6 重锚；P30-III-1a 起 H2 改结点站位自采、
     * 不再消费本域）：±{@link #SCAN_EXTENT} 步 {@link #TRUNK_DOM_STRIDE}。
     * 档口径 roster 0（poolLevelAt 只吃档表 bedTarget，常态 64.5）。
     * 采集三类量：<b>河核列</b>（s≥WET_MIN——段激活门在 strengthAt 内 ⇒ 河核列天然 ⊂ 激活段；
     * H1 分母）、<b>逐 segKey 池档直方图</b>（H1）、<b>逐 segKey 是否出现 s&gt;0 采样列</b>
     * （F3 段激活率的行为读数：激活段的河谷域 c&lt;WIDTH 带 ≈±15 格 × 边长 ~600 格 ⇒ stride-8
     * 网格期望 ~280 命中，漏检概率 ≈ e^−280 ⇒ 「段内任一采样列 s&gt;0」与门值逐段等价）。
     * 〔P30-III-1a 删面登记：旧第四类量「端面锚样本」（s≥WET_MIN ∧ trunk&gt;0，H2 膨胀扫描
     * 源）随 H2 锚点域换结点站位退役——I1 衰减后该域与端面几何脱钩（见 groupH javadoc）。〕
     */
    static final class TrunkDomain {
        /** 域内河核列（s≥WET_MIN ⇒ 激活段）数（H1 分母）。 */
        long coreCols;
        /** H1：河核列按 segKey 分组后的段内池档直方图。 */
        final HashMap<Long, HashMap<Integer, int[]>> segPools = new HashMap<Long, HashMap<Integer, int[]>>();
        /** F3：窗内全部唯一 segKey（含未激活段）。 */
        final HashSet<Long> segKeys = new HashSet<Long>();
        /** F3：其中出现 s&gt;0 采样列的段（行为激活）。 */
        final HashSet<Long> segKeysLive = new HashSet<Long>();
    }

    static TrunkDomain scanSegDomain() {
        final TrunkDomain dom = new TrunkDomain();
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT; z += TRUNK_DOM_STRIDE) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT; x += TRUNK_DOM_STRIDE) {
                final long key = GTSRVoronoiRiverField.segKeyAt(SEED, x, z);
                dom.segKeys.add(Long.valueOf(key));
                final double s = -GTSRVoronoiRiverField.strengthAt(SEED, x, z, 0);
                if (s > 0.0D) {
                    dom.segKeysLive.add(Long.valueOf(key)); // 河谷域采样命中 ⇒ 该段过激活门
                }
                if (s < GTSRVoronoiRiverField.WET_MIN) {
                    continue;
                }
                dom.coreCols++;
                final int pool = GTSRVoronoiRiverField.poolLevelAt(SEED, x, z, 0);
                HashMap<Integer, int[]> hist = dom.segPools.get(Long.valueOf(key));
                if (hist == null) {
                    dom.segPools.put(Long.valueOf(key), hist = new HashMap<Integer, int[]>());
                }
                final int[] slot = hist.get(Integer.valueOf(pool));
                if (slot == null) {
                    hist.put(Integer.valueOf(pool), new int[] { 1 });
                } else {
                    slot[0]++;
                }
            }
        }
        return dom;
    }

    // ══════════════════════ P30-III-1a：H2 衰减带端面站位（三叉结点）══════════════════════

    // 删面登记（P30-III-1a）：旧 H2 锚点机械三件——endFaceCoreAt（trunk>0 ∧ s≥WET_MIN 端面
    // 入口门谓词）、tangentAt（16 方向 wet 延伸最长切向估计）、hasDepletionFace（8 邻/切向
    // "换段 ∧ 枯竭"面判）——随锚点域换结点站位整段退役删除：I1 死邻衰减把 s≥WET_MIN 等值线
    // 拉离段界数十格（衰减带内 s 已 <WET_MIN），"湿核列 8 邻见换段枯竭列"与"切向
    // k≤END_FACE_LEN=4 内见面"双双结构性永假（批I 收口 axisCand=0 ⇒ walks=0 空集红，
    // 非行为红）。生产侧 endFaceBed 的面探测同因永假而惰化（bedFromPool 返回值与无端面
    // 列同式——衰减带内床恒 pool−depth+n_bed，探针实测带宽 9-18 列的带内 bed 全程平直，
    // 详见 temp/p30-iiia 探针归档），端面收尾语义由 I1 t₁/t₂ 衰减在 heightCore 的 s 位承载。

    /**
     * H2 端面站位采集（P30-III-1a 新锚）：E1 同式三叉点检测（2×2 角最近细胞 ≥3 个）扫
     * ±{@link #SCAN_EXTENT} 步 {@link #FORK_STRIDE}，站位取块心、2×{@link #FORK_STRIDE}
     * Chebyshev 去重（结点邻域 ±~30 格会被步距 16 的相邻块多次命中）。
     * <p>
     * 为什么锚结点：I1 死邻衰减的端面几何中心 = 三叉结点（t₁/t₂ 的 r=(d3−dC)/d3→0 处）——
     * 活段沿 border 走向结点，s 由段内值 smoothstep 渐灭；端面（衰减带）= 结点向
     * s ∈ [{@link GTSRVoronoiRiverField#DRY_RIVERBED_CORE}, {@link GTSRVoronoiRiverField#WET_MIN})
     * 的过渡带。纯场量定位（nearestCellHash 与 strengthAt 同源 warp），零公式复刻。
     */
    static List<int[]> scanEndFaceSites() {
        final List<int[]> sites = new ArrayList<int[]>();
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT; z += FORK_STRIDE) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT; x += FORK_STRIDE) {
                final long h00 = cellHash(x, z);
                final long h10 = cellHash(x + FORK_STRIDE, z);
                final long h01 = cellHash(x, z + FORK_STRIDE);
                final long h11 = cellHash(x + FORK_STRIDE, z + FORK_STRIDE);
                final HashSet<Long> distinct = new HashSet<Long>(4);
                distinct.add(Long.valueOf(h00));
                distinct.add(Long.valueOf(h10));
                distinct.add(Long.valueOf(h01));
                distinct.add(Long.valueOf(h11));
                if (distinct.size() < 3) {
                    continue;
                }
                final int cx = x + FORK_STRIDE / 2;
                final int cz = z + FORK_STRIDE / 2;
                boolean dup = false;
                for (final int[] p : sites) {
                    if (Math.abs(p[0] - cx) <= 2 * FORK_STRIDE && Math.abs(p[1] - cz) <= 2 * FORK_STRIDE) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    sites.add(new int[] { cx, cz });
                }
            }
        }
        return sites;
    }

    // ══════════════════════ F 枯竭验证（v1.20.42 P22 A1c 重立） ══════════════════════

    /**
     * <b>F1（v1.20.42 P22 A1c 换语义重立）</b>：枯竭域（trunkAt≤0 河列）置水 == 0——A1a 探针 C1
     * 的 {@code wetOutsideTrunk=0} 口径判据化（wetAt 新门 = {@code s ≥ WET_MIN ∧ trunk > 0} 的
     * 带外方向）。采样域沿用旧 F 组的荒漠档窗（±{@link #SCAN_EXTENT} 步 {@link #SCAN_STRIDE}、
     * roster 2——新门不吃 roster，采样档只保留读数连续性：v1.20.41 同窗河核列 n=12668、本片同值）。
     * <p>
     * 【<b>旧 F1 断言原文（v1.20.39 T4 立、v1.20.40 P19 §B 改整段闸口径）逐字保留</b>，自
     * v1.20.42（P22 A1a 全域枯竭）起作废换立】：「<b>F1 荒漠断流：过水占比 ∈ [20%,40%]</b>（约
     * 30%±10 ⇒ 串珠断流，不是全干也不是全满）｜ F-READ 荒漠档河核列=… 过水列=… 占比=…%
     * （旧 WET_EDGE=0.6 连续噪声闸，字段已删）」。作废理由：荒漠整段闸（干:湿 = 65:35）
     * 已随 A1a 的 wetAt 新门从生产链退役——全域枯竭后本窗过水占比实测 0.000%（基线复跑红），
     * 旧带钉的"串珠断流"机制不复存在；判据侧对旧闸的消费点自 A1c 片退役，
     * 生产侧退役字段（WET_EDGE/DESERT_WET_SHARE/segWetAt）已随 P23 R1·S6 兑现删除。
     */
    static void groupF() {
        long core = 0;
        long deplete = 0;
        long wetViol = 0;
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT; z += SCAN_STRIDE) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT; x += SCAN_STRIDE) {
                if (-GTSRVoronoiRiverField.strengthAt(SEED, x, z, 2) < GTSRVoronoiRiverField.WET_MIN) {
                    continue;
                }
                core++;
                if (GTSRVoronoiRiverField.trunkAt(SEED, x, z) <= 0.0D) {
                    deplete++;
                }
                // P23 R1·S6 强化：枯竭侧（trunk≤0）与带内侧一并计数——wetAt 恒 false 的合同面
                // 必须全域成立，不再只钉带外半侧（旧 F2 的带内半侧随主干河置水死退役）。
                if (GTSRVoronoiRiverField.wetAt(SEED, x, z, 2)) {
                    wetViol++;
                }
            }
        }
        say("F-READ 置水验证：河核列=" + core + "（其中 trunk≤0 干床侧=" + deplete + "）全域置水违例="
            + wetViol + "（P23 R1 起 wetAt 恒 false——河流场全域无水，湖/沼泽池走独立通道）");
        check("F1 全域置水 == 0（P23 R1·S6 强化：wetAt 恒 false 的合同面——河核列（激活段干床，"
            + "trunk 内外一并）置水违例必须为 0；违例 > 0 = wetAt 死门之外出现第二置水真值源。"
            + "〔旧带原文保留：F1 荒漠断流 过水占比 ∈ [20%,40%（约 30%±10 串珠断流），v1.20.42 起作废"
            + "——荒漠整段闸已退役，见本方法 javadoc；v1.20.42-45 的双侧口径（带外 F1 + 带内 F2）"
            + "随主干河置水死由本条全域单侧承接〕〕",
            core >= N_CENTERS && wetViol == 0,
            "违例=" + wetViol + " 河核列=" + core + "（trunk≤0 干床侧=" + deplete + "）");
    }

    /**
     * <b>F3 段激活率带（P23 R1·S6 新增，接替已删的 F2 位）</b>：零散干床段的激活段占比 ∈
     * [0.16,0.24]——构造值 {@link GTSRVoronoiRiverField#RIVER_SEGMENT_ACTIVATE_P}=0.20 的哈希
     * 均匀性读数（段激活门对 segKey 哈希取阈，激活率应贴着 P 值；带 = P±0.04 ≈ 二项 2σ 内的
     * 收紧域，S2 直跑 0.194 居带）。读数走<b>行为口径</b>（段内任一采样列 s&gt;0 ⇔ 过门，见
     * {@link #scanSegDomain} 的漏检概率推导），零公式复刻。
     * <p>
     * 〔已删断言原文保留：F2 遗忘之川域无枯竭段——域内（trunk&gt;0 河核列）湿段率 == 100%
     * （v1.20.42 P22 A1c 立）；P23 R1 主干宽河移除 ⇒ 判据对象不存在，plan §3 表序 3 裁删。〕
     */
    static void groupActivation(TrunkDomain dom) {
        final int n = dom.segKeys.size();
        final int live = dom.segKeysLive.size();
        final double share = n == 0 ? -1.0D : live / (double) n;
        say("F-READ 段激活率：窗内唯一段（segKey）=" + n + " 激活段（任一采样列 s>0）=" + live
            + " ⇒ 激活率=" + f3(share) + "（构造值 RIVER_SEGMENT_ACTIVATE_P="
            + GTSRVoronoiRiverField.RIVER_SEGMENT_ACTIVATE_P + "；±" + SCAN_EXTENT + " 步距 "
            + TRUNK_DOM_STRIDE + "，行为口径零公式复刻）");
        check("F3 段激活率 ∈ [0.16,0.24]（P23 R1② 零散干床：构造值 0.20 的哈希均匀性——带外 = "
            + "激活门哈希退化成偏斜分布或 P 值被改；S2 直跑 0.194 在带）",
            n >= 32 && share >= 0.16D && share <= 0.24D,
            "激活率=" + f3(share) + " 段=" + n + " 激活=" + live);
    }

    // ══════════════════════ G 沼泽河宽 + heightAt 集成 ══════════════════════

    static void groupG(List<int[]> centers) {
        final List<Integer> normal = new ArrayList<Integer>();
        final List<Integer> swamp = new ArrayList<Integer>();
        for (final int[] c : centers) {
            final int wn = waterHalfWidth(c[0], c[1], 0);
            final int ws = waterHalfWidth(c[0], c[1], 3);
            if (wn > 0) {
                normal.add(Integer.valueOf(wn));
            }
            if (ws > 0) {
                swamp.add(Integer.valueOf(ws));
            }
        }
        final double ratio = median(swamp) / Math.max(1.0D, median(normal));
        say("G-READ 水道半宽 常态中位=" + f3(median(normal)) + " 沼泽中位=" + f3(median(swamp)) + " 比值="
            + f3(ratio) + "（档表 widthScale=1.2）");
        check("G1 沼泽河宽 ≈ 常态 ×1.2（v1.20.40 P19 §A.2 档乘子收窄 1.6→1.2 的行为读数；半宽整数化的"
            + "量化容差后断言带 [1.05,1.45]——归因 U2/U34 prered 实测 1.250 居带中）",
            !swamp.isEmpty() && ratio >= 1.05D && ratio <= 1.45D, "比值=" + f3(ratio));
        // heightAt 集成：河核列地表 = round(bed)（高地支）或低地原样 h0 ≤ bed+1（防抬升支）
        // ⇒ 上界 h1 ≤ bed+1 恒成立；|h1-bed| ≤1.5 的平底占比应是绝大多数（低地支是少数）。
        int n = 0;
        int over = 0;
        int flat = 0;
        double worstOver = 0.0D;
        for (final int[] c : centers) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    final int x = c[0] + dx;
                    final int z = c[1] + dz;
                    if (s(x, z) < GTSRVoronoiRiverField.WET_MIN) {
                        continue;
                    }
                    final int h = ProsperityTerrainProfile.heightAt(SEED, x, z);
                    final double bed = GTSRVoronoiRiverField.bedAt(SEED, x, z, -1);
                    n++;
                    final double dev = h - bed;
                    if (dev > 1.5D) {
                        over++;
                        worstOver = Math.max(worstOver, dev);
                    }
                    if (Math.abs(dev) <= 1.5D) {
                        flat++;
                    }
                }
            }
        }
        final double flatShare = n == 0 ? 0.0D : flat / (double) n;
        say("G-READ heightAt 河谷集成：河核列 n=" + n + " 超上界=" + over + "（最坏 +" + f3(worstOver)
            + "）平底占比=" + f3(100.0D * flatShare) + "%（bedAt 按默认档——未装配 JVM 与 heightCore"
            + " 同走 -1，同一条链；低地支 |dev|>1.5 属设计内：h0 ≤ bed+1 不动，防抬升）");
        check("G2a 河谷压低真并入 heightAt：河核列 h1 ≤ bed+1 恒成立（两支共同上界，无抬升无漏压）",
            n > 0 && over == 0, "over=" + over + "/" + n + " worst=+" + f3(worstOver));
        check("G2b 平底支占绝大多数（≥80% 河核列 h1 = round(bed) ±1.5；其余是低地原样支）",
            flatShare >= 0.8D, "flat=" + f3(100.0D * flatShare) + "%");
        int rangeBad = 0;
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT; z += SCAN_STRIDE * 2) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT; x += SCAN_STRIDE * 2) {
                final int h = ProsperityTerrainProfile.heightAt(SEED, x, z);
                if (h < 40 || h > 110) {
                    rangeBad++;
                }
            }
        }
        check("G3 heightAt 值域 [40,110] 不变（压低链不破钳制契约）", rangeBad == 0, "越界=" + rangeBad);
    }

    // ══════════════════════ H 分段水位与端面（v1.20.40 P19 §B/§C 新增） ══════════════════════

    /**
     * P19 §B/§C 两个新面的正式断言（U2/U34 切片探针读数升格，验收 = 面被钉住；
     * <b>v1.20.42 P22 A1c：采样域缩到遗忘之川域</b>——wetAt 新门下置水只剩主干带内，
     * 全域湿核采样已无水面可钉；{@code wetAt} 的枯竭语义替代了荒漠整段闸）：
     * <ul>
     * <li><b>H1 池水位水平</b>：按 segKey（细胞对签名 = 段）分组后钉 §C 的构造不变量——
     * 一条细胞边只有<b>两个端点结点</b>，每端点的结点裁决（三边池水位取最小，"水往低池走"）
     * 只产生<b>一个</b>低于本段基池的档 ⇒ 每段的 poolLevelAt 档数 ≤ 3（本体 + 两端结点档），
     * 且达到段内最高池（= 边基池）的列占绝对多数。全部同档的严格版（U2 探针 P2 的 48 点）只对
     * 边中段成立——湿核列穿过端结点域时按裁决降档是<b>设计行为</b>（首轮实测湿核列 ~2 成带结点
     * 降档、12/36 向几何探环均有漏检角，故放弃"全同"与几何排除两种钉法），档数 &gt; 3 或最高池
     * 非多数 ⇒ 分段实现里存在逐列噪声型第二真值源；<b>采样域（P22 A1c 起）= 遗忘之川域 wet 列
     * （s≥WET_MIN ∧ trunk&gt;0）</b>，断言带（档数 ≤3 ∧ 份额 ≥1/2）与 v1.20.40 原钉一字不动
     * 〔旧全域口径原文：「H-READ 池水位水平：湿核列=3133 段=101 …（±SCAN_EXTENT 步距 16 全域
     * 湿核列）」——枯竭后该 3133 列全部无水，其池水位只是段界参考面，读数保留在 v1.20.41 判据
     * 归档，不再采样〕；</li>
     * <li><b>H2 端面渐变</b>〔<b>P30-III-1a（v1.20.53 批I 后）锚点域整体重钉</b>——原口径
     * （U34 遗留① 顺流面 A 判据 = 邻列换段 ∧（trunkAt≤0 ∨ s&lt;WET_MIN），{@code endFaceBed}
     * 同判式 + 「湿样本膨胀 → 切向面探针 → dist=1 抬满锚点」两段式）随 I1 死邻衰减<b>结构性
     * 退役</b>：衰减把 s≥WET_MIN 等值线拉离段界数十格（带内 s 已 &lt;WET_MIN），"湿核列 8 邻见
     * 换段枯竭列"永假 ⇒ 批I 收口实测 axisCand=0、walks=0 的<b>空集红</b>（0/0 非行为红）。
     * 端面收尾的原语义（P19 §B smoothstep 床收尾）由 <b>I1 的 t₁/t₂ 衰减在 heightCore 的
     * s 位承载</b>——床收尾观感腿（endFaceBed）的抬升带探测同因永假而惰化（衰减带内床恒
     * pool−depth+n_bed，带宽 9-18 列的带内 bed 全程平直，temp/p30-iiia 探针在案）。新口径：
     * <b>锚 = 三叉结点站位</b>（{@link #scanEndFaceSites}）× 16 方向，资格 = 死侧起点
     * （r={@link #FORK_STRIDE} 含 ±{@link #H2_SNAP} 横窗 sMax &lt; {@code DRY_RIVERBED_CORE}）
     * ∧ {@link #H2_WALK_CAP} 内升过 WET_MIN——即"活段向死邻段方向"的 s 从 ≥WET_MIN 降到
     * &lt;{@code DRY_RIVERBED_CORE} 的沿程过渡带（反向遍历）；<b>剖面 = 贴脊外向走</b>（每步
     * 前进 1 列 + 横向跟踪窗 |Δoff| ≤ {@link #H2_TRACK} 的 max-s 再居中，防跳池阶/谷壁的
     * 伪 Δ）取 [i₄₀−1, i₇₈+3] 列（i₄₀/i₇₈ = s 过 {@code DRY_RIVERBED_CORE}/WET_MIN 的
     * 首列）；<b>床高口径照旧 = bedAt（bedFromPool）</b>，全剖面 lake−shore ≥
     * {@code LAKE_FADE}（t_lake=1，纯 B1 死邻腿——湖让位外沿 fade 属湖岸域另册）。
     * <b>池阶列（poolLevelAt 变列）排除判据、只报读数</b>：结点裁决量化步是 P19 §C 设计域
     * （poolLevelAt javadoc 自陈"段间差 ∈ {0,2,4}"，H1 已钉档数 ≤3；批I 前该列在端面后
     * END_FACE_LEN−1=3 列的采样窗外，重钉后长剖面穿见 ⇒ 非端面形态量）——实测带内池阶
     * |Δpool| ∈ {2,4}、|Δbed−|Δpool|| ≤ 0.12。池恒定 run 内逐列床高差 ≤ 2 断言带与
     * v1.20.40 原钉一字不动。〔只报不钉的地面侧读数：heightAt 沿同剖面的逐列差最坏
     * 3-6（谷形闭合的 s-blend 斜率 × 池阶 × relief 噪声的共址叠加，带宽由 TAPER_DEAD
     * 定）——它不是床收尾量、也不在 ≤2 原带内，读数留档供主代理裁决是否另立地面侧判据。〕
     * 旧口径原文（P22 A1c 两段式锚点采样）归档：「枯竭端面在主干带长直几何下结构性稀少
     * （湿段端只在带缘/结点失准处）——旧 stride-8 盲扫实测 walks=3/0/1 随网格相位乱跳
     * （探针 {@code plan/tmp/p22-a1c/probe.out}）；本片改「湿样本膨胀（±FACE_DILATION ⇒
     * 湿域列分辨率完备）→ 切向面探针（镜像 endFaceBed 的 ±切向 k=1..LEN 判式）→ dist=1
     * 抬满锚点」，读数与生产抬升带对齐（轴 8 邻面检测在枯竭世界的 3 处假阳已证伪：锚点
     * bed=pool−0.5 恰是生产 dist=1 抬满值，但轴方向面 ≠ 生产切向面，见
     * {@code plan/tmp/p22-a1c/probe3/4}）」。段界斜交尖端的横向邻列小槛（U34 遗留）仍不在
     * 顺流口径内。</li>
     * </ul>
     */
    static void groupH(TrunkDomain dom) {
        // —— H1 池水位水平（每段档数 ≤ 3 = 本体 + 两端结点档；最高池 = 边基池占多数）——
        // P23 R1·S6：采样域 = 激活段河核列（s≥WET_MIN ⇒ 激活段；置水死后池水位仍是床面/端面
        // 收尾的段界参考面——床阶语义留、置水断言删，断言带一字不动）。
        int worstLevels = 0;
        int segsOverLevels = 0;
        double worstMaxShare = 1.0D;
        for (final HashMap<Integer, int[]> hist : dom.segPools.values()) {
            int total = 0;
            int maxCount = 0;
            final int levels = hist.size();
            worstLevels = Math.max(worstLevels, levels);
            for (final int[] c : hist.values()) {
                total += c[0];
                maxCount = Math.max(maxCount, c[0]);
            }
            if (levels > 3) {
                segsOverLevels++;
            }
            worstMaxShare = Math.min(worstMaxShare, maxCount / (double) total);
        }
        say("H-READ 池水位水平（激活段河核列，P23 R1·S6 域）：段（segKey）=" + dom.segPools.size()
            + " 段内档数最坏=" + worstLevels + " 档数>3 的段=" + segsOverLevels + " 最高池份额最坏="
            + f3(worstMaxShare) + "（河核列合计 " + dom.coreCols + "）");
        check("H1 每段 poolLevelAt 档数 ≤ 3（本体 + 两端结点裁决档）且段内最高池份额 ≥ 1/2（P19 §C 段内恒"
            + "水面 + 结点取最小；实测最坏 0.500 = 短段两端结点域占满余量；档数超限或基池份额跌穿半数 = "
            + "逐列噪声型第二真值源。P23 R1·S6：采样域改激活段河核列（wetAt 恒 false 后无 wet 列可采，"
            + "床阶语义留、置水断言删），断言带与 v1.20.40 原钉一字不动）",
            dom.segPools.size() >= H1_SEGS_MIN && segsOverLevels == 0 && worstMaxShare >= 0.5D,
            "段=" + dom.segPools.size() + " 档数最坏=" + worstLevels + " 最高池份额最坏=" + f3(worstMaxShare));
        // —— H2 端面渐变（P30-III-1a 重锚：衰减带内端面·结点径向顺流剖面逐列床高差 ≤ 2，
        // 两段式 = 结点站位 × 16 方向资格探针 + 贴脊外向走剖面，见方法 javadoc）——
        final List<int[]> sites = scanEndFaceSites();
        int walks = 0;
        int noExit = 0;
        int lakeExcl = 0;
        int badPairs = 0;
        int poolSteps = 0;
        int worstPoolStep = 0;
        double worst = 0.0D; // 池恒定 run 内 |Δbed|（判据量）
        double worstPoolNoise = 0.0D; // 池阶列 |Δbed − |Δpool||（设计域读数量）
        double worstTerrain = 0.0D; // heightAt 逐列差（地面侧只报不钉的形态读数）
        int bandMin = Integer.MAX_VALUE;
        int bandMax = 0;
        long bandSum = 0;
        for (final int[] j : sites) {
            for (int a = 0; a < 16; a++) {
                final double tx = Math.cos(a * Math.PI / 8.0D);
                final double tz = Math.sin(a * Math.PI / 8.0D);
                final double px = -tz;
                final double pz = tx;
                // 1) 资格探针：死侧起点（r=FORK_STRIDE 含 ±H2_SNAP 横窗步 2 的 sMax <
                // DRY_RIVERBED_CORE——结点处 s≈0，邻边皆活的方向此处已 ≥ 阈）∧ CAP 内
                // （r 每 +2×FORK_STRIDE 复探一次横窗）升过 WET_MIN ⇒ 该向有衰减带。
                double sNear = 0.0D;
                for (int off = -H2_SNAP; off <= H2_SNAP; off += 2) {
                    sNear = Math.max(sNear, s(j[0] + (int) Math.round(tx * FORK_STRIDE + px * off),
                        j[1] + (int) Math.round(tz * FORK_STRIDE + pz * off)));
                }
                if (sNear >= GTSRVoronoiRiverField.DRY_RIVERBED_CORE) {
                    continue;
                }
                boolean rises = false;
                for (int r = FORK_STRIDE; r <= H2_WALK_CAP && !rises; r += 2 * FORK_STRIDE) {
                    for (int off = -H2_SNAP; off <= H2_SNAP && !rises; off += 2) {
                        if (s(j[0] + (int) Math.round(tx * r + px * off),
                            j[1] + (int) Math.round(tz * r + pz * off)) >= GTSRVoronoiRiverField.WET_MIN) {
                            rises = true;
                        }
                    }
                }
                if (!rises) {
                    continue;
                }
                // 2) 贴脊外向走（结点 → 活段）：每步前进 1 列 + 横向跟踪窗（|Δoff| ≤ H2_TRACK，
                // 窗体 ±H2_SNAP）内 max-s 再居中——剖面贴 border 脊（c≈0），不跳池阶/谷壁。
                final int[] xs = new int[H2_WALK_CAP + 1];
                final int[] zs = new int[H2_WALK_CAP + 1];
                final double[] ss = new double[H2_WALK_CAP + 1];
                final int[] pools = new int[H2_WALK_CAP + 1];
                xs[0] = j[0];
                zs[0] = j[1];
                ss[0] = s(j[0], j[1]);
                pools[0] = GTSRVoronoiRiverField.poolLevelAt(SEED, j[0], j[1], 0);
                int prevOff = 0;
                for (int k = 1; k <= H2_WALK_CAP; k++) {
                    final int ax = xs[k - 1] + (int) Math.round(tx);
                    final int az = zs[k - 1] + (int) Math.round(tz);
                    double best = -1.0D;
                    int bx = ax;
                    int bz = az;
                    int bestOff = prevOff;
                    for (int off = Math.max(-H2_SNAP, prevOff - H2_TRACK); off <= Math.min(H2_SNAP,
                        prevOff + H2_TRACK); off++) {
                        final int qx = ax + (int) Math.round(px * off);
                        final int qz = az + (int) Math.round(pz * off);
                        final double v = s(qx, qz);
                        if (v > best) {
                            best = v;
                            bx = qx;
                            bz = qz;
                            bestOff = off;
                        }
                    }
                    prevOff = bestOff;
                    xs[k] = bx;
                    zs[k] = bz;
                    ss[k] = best;
                    pools[k] = GTSRVoronoiRiverField.poolLevelAt(SEED, bx, bz, 0);
                }
                int i40 = -1;
                int i78 = -1;
                for (int k = 0; k <= H2_WALK_CAP; k++) {
                    if (i40 < 0 && ss[k] >= GTSRVoronoiRiverField.DRY_RIVERBED_CORE) {
                        i40 = k;
                    }
                    if (ss[k] >= GTSRVoronoiRiverField.WET_MIN) {
                        i78 = k;
                        break;
                    }
                }
                if (i40 <= 0 || i78 < 0 || i78 <= i40) {
                    noExit++; // 死边向/未及带即止（资格探针的横窗假阳——贴脊线本身不穿过带）
                    continue;
                }
                // 湖纯度：全剖面 t_lake=1（lake−shore ≥ LAKE_FADE）——纯 B1 死邻腿；
                // 湖让位外沿 fade（B2）与硬腿（3a）造成的 s 下降不属本断言的端面域。
                boolean lakePure = true;
                for (int k = i40 - 1; k <= Math.min(H2_WALK_CAP, i78 + 3); k++) {
                    if (GTSRVoronoiRiverField.lakeAt(SEED, xs[k], zs[k])
                        - GTSRVoronoiRiverField.sanzuBiomeShoreAt(SEED, xs[k],
                            zs[k]) < GTSRVoronoiRiverField.LAKE_FADE) {
                        lakePure = false;
                        break;
                    }
                }
                if (!lakePure) {
                    lakeExcl++;
                    continue;
                }
                walks++;
                bandMin = Math.min(bandMin, i78 - i40);
                bandMax = Math.max(bandMax, i78 - i40);
                bandSum += i78 - i40;
                // 3) 逐列床高（bedAt——bedFromPool 口径照旧）：池恒定 run 内 |Δbed| 判 ≤2；
                // 池阶列（Δpool≠0）排除判据、记 |Δpool| 与 |Δbed−|Δpool|| 读数（P19 §C
                // 结点裁决设计域，段间差 ∈ {0,2,4}）。heightAt 同剖面只报形态读数。
                double prevBed = Double.NaN;
                double prevTerrain = Double.NaN;
                int prevPool = pools[i40 - 1];
                for (int k = i40 - 1; k <= Math.min(H2_WALK_CAP, i78 + 3); k++) {
                    final double bed = GTSRVoronoiRiverField.bedAt(SEED, xs[k], zs[k], 0);
                    final int terrain = ProsperityTerrainProfile.heightAt(SEED, xs[k], zs[k]);
                    if (!Double.isNaN(prevBed)) {
                        final double d = Math.abs(bed - prevBed);
                        final int dp = pools[k] - prevPool;
                        if (dp == 0) {
                            worst = Math.max(worst, d);
                            if (d > 2.0D) {
                                badPairs++;
                            }
                        } else {
                            poolSteps++;
                            worstPoolStep = Math.max(worstPoolStep, Math.abs(dp));
                            worstPoolNoise = Math.max(worstPoolNoise, Math.abs(d - Math.abs(dp)));
                        }
                        worstTerrain = Math.max(worstTerrain, Math.abs(terrain - prevTerrain));
                    }
                    prevBed = bed;
                    prevTerrain = terrain;
                    prevPool = pools[k];
                }
            }
        }
        say("H-READ 端面渐变（衰减带内端面·结点径向顺流剖面，P30-III-1a 锚点域）：结点站位="
            + sites.size() + " 剖面数=" + walks + " 不及带=" + noExit + " 湖域排除=" + lakeExcl
            + " 带宽(列) ∈ [" + (bandMin == Integer.MAX_VALUE ? 0 : bandMin) + "," + bandMax + "] 均值="
            + f3(walks == 0 ? 0.0D : bandSum / (double) walks) + " 池恒定 run 内 |Δbed| 最坏="
            + f3(worst) + " 池阶列=" + poolSteps + "（|Δpool| 最坏=" + worstPoolStep
            + " 阶上床噪声最坏=" + f3(worstPoolNoise) + "——P19 §C 设计域只报不钉）"
            + " heightAt 同剖面逐列差最坏=" + f3(worstTerrain) + "（地面侧形态读数，不钉）");
        check("H2 干段端面收尾沿顺流剖面逐列床高差 ≤ 2（P30-III-1a（v1.20.53 批I 后）锚点域重钉："
            + "I1 死邻衰减把端面从「段界换段面」搬进「结点向衰减带」——原 trunk>0 ∧ s≥WET_MIN "
            + "端面入口锚 + 8 邻换段枯竭面判在衰减带分隔下结构性空集（批I 收口 walks=0 空集红，"
            + "非行为红）；原 P19 §B smoothstep 床收尾语义由 I1 t1/t2 衰减在 heightCore 的 s 位"
            + "承载、endFaceBed 抬升腿同因惰化。新口径：锚 = 三叉结点站位 × 16 向资格探针"
            + "（死侧起点 sMax<DRY_RIVERBED_CORE ∧ CAP 内升过 WET_MIN），剖面 = 贴脊外向走"
            + "（横向跟踪窗 |Δoff|≤2 防伪 Δ）[i40−1, i78+3] 列，床高口径照旧 = bedAt，"
            + "湖纯度门 lake−shore≥LAKE_FADE（纯 B1 死邻腿）；池阶列（poolLevelAt 变列——"
            + "P19 §C 结点裁决量化步，javadoc 自陈段间差 ∈ {0,2,4}，H1 已钉档数 ≤3）排除判据"
            + "只报读数。断言带（剖面数 ≥8 ∧ badPairs=0 ∧ 逐列床高差 ≤2）与 v1.20.40 原钉"
            + "一字不动；U34 遗留① 的横向尖端小槛仍不在顺流口径内）",
            walks >= 8 && badPairs == 0, "walks=" + walks + " badPairs=" + badPairs + " worst=" + f3(worst)
                + " 池阶列=" + poolSteps + "(只报)");
    }

    // ══════════════════════ J 枯竭河床（P25 D6/D4；I 组残潭已随 D7 退役） ══════════════════════

    /**
     * <b>I 组退役登记（P25 D7，对齐生产）</b>：沼泽河床残潭场（{@code swampRiverPoolAt}/
     * {@code swampRiverPoolColumnAt} 与 {@code SWAMP_RIVER_POOL_} 前缀常量族）已随 P25 用户裁定整体
     * 退役——判据对象不存在，I1~I4 四断言 + 猎取几何（POOL_HUNT_EXTENT/POOL_WINDOWS/
     * POOL_WINDOW_HALF）
     * + 水体模拟（groupIBody/poolBarrier）整段摘除（生产侧消费面清单见
     * {@code GTSRVoronoiRiverField} 类顶的"已删成员登记"段）。历史实测读数（覆盖率 0.1348、
     * 外流/悬浮/嵌入违例 0）见 {@code plan/sum/86} §5 与版本树，不迁移不复测。
     * <p>
     * <b>J 组：枯竭河床三断言（P25 D6 isDryRiverColumn 判据化 + D4① 湖让位腿行为证）</b>：
     * <ul>
     * <li><b>J1 湖+滩内 s≡0</b>：湖+滩压力域（{@code lakeAt < sanzuBiomeShoreAt}——含噪声腿扩出的
     * 滩带外缘，与 {@code strengthAt} 湖让位腿同一判据面）内 {@code -strengthAt} 恒 0；</li>
     * <li><b>J2 过渡带几何宽 ∈ [8,16] 格（中位）</b>：沿干床核向外 transect 实测
     * {@code s = -strengthAt ∈ [0.40, 0.60)} 阈带的连续列宽——{@code DRY_RIVERBED_JITTER} javadoc
     * 换算式（0.20 × 42 格/单位 ≈ 8.4 格）的行为复核；</li>
     * <li><b>J3 isDryRiverColumn ⇒ !isSanzuColumn（≥10⁶ 列）</b>：谓词内含湖让位双保险腿的
     * 逐列行为证——违例 &gt; 0 = 冻结接口的 {@code !isSanzuColumn} 腿被删/被绕。</li>
     * </ul>
     */
    static void groupJ() {
        final double core = GTSRVoronoiRiverField.DRY_RIVERBED_CORE;
        final double jit = GTSRVoronoiRiverField.DRY_RIVERBED_JITTER;
        // ── J1：湖+滩压力域内 s≡0（strengthAt 湖让位腿行为直读）──
        // 采样域 ±1.5×LAKE_INTERVAL（覆盖 ≥2 个湖站格 ⇒ 窗内必有活湖/滩列），步距 24 < 滩带宽
        //（22~39 格）⇒ 滩带列不会被步距跳空；成量下限 200（湖概率减半后的保守带）。
        final int j1Lim = (int) (GTSRVoronoiRiverField.LAKE_INTERVAL * 1.5D);
        final int j1Stride = 24;
        long lakeShoreCols = 0;
        long lakeShoreNonzero = 0;
        for (int z = -j1Lim; z <= j1Lim; z += j1Stride) {
            for (int x = -j1Lim; x <= j1Lim; x += j1Stride) {
                if (GTSRVoronoiRiverField.lakeAt(SEED, x, z) < sanzuShoreAtReflect(SEED, x, z)) {
                    lakeShoreCols++;
                    if (-GTSRVoronoiRiverField.strengthAt(SEED, x, z) != 0.0D) {
                        lakeShoreNonzero++;
                    }
                }
            }
        }
        say("J-READ 枯竭河床：湖+滩压力域列=" + lakeShoreCols + "（步距 " + j1Stride + " 窗 ±" + j1Lim
            + "）非零强度列=" + lakeShoreNonzero);
        say("I-RETIRED P25 D7 残潭退役：swampRiverPoolAt 族已随生产删除，I1~I4 判据整组摘除（登记见 groupJ javadoc）");
        check("J1 湖+滩内 s≡0（P25 D4① 湖让位腿）：lakeAt < sanzuBiomeShoreAt 的列上"
            + " -strengthAt == 0，违例 = 0 ∧ 样本成量 ≥ 200（湖概率减半后的保守带）",
            lakeShoreCols >= 200 && lakeShoreNonzero == 0,
            "湖+滩列=" + lakeShoreCols + " 非零=" + lakeShoreNonzero);
        // ── J2：isDryRiverColumn 过渡带几何宽（s∈[0.40,0.60) 连续列宽，transect 中位）──
        // 锚点：河核列（s ≥ WET_MIN 且非湖域）向 ±x/±z 四向走出，数阈带列；transect 途中
        // 撞上湖让位悬崖（s 直落 0，非连续过渡）⇒ 该向作废不计（湖边界的带宽不是本判据对象）。
        final List<Integer> bandWidths = new ArrayList<Integer>();
        long j2Tried = 0;
        for (final int[] c : findCenters()) {
            for (int dir = 0; dir < 4 && bandWidths.size() < 128; dir++) {
                final int dx = dir == 0 ? 1 : (dir == 1 ? -1 : 0);
                final int dz = dir == 2 ? 1 : (dir == 3 ? -1 : 0);
                int x = c[0], z = c[1];
                double s = -GTSRVoronoiRiverField.strengthAt(SEED, x, z);
                if (s < GTSRVoronoiRiverField.WET_MIN) {
                    continue;
                }
                j2Tried++;
                int band = 0;
                boolean valid = true;
                boolean entered = false;
                for (int step = 0; step < WALK_LIMIT; step++) {
                    x += dx;
                    z += dz;
                    s = -GTSRVoronoiRiverField.strengthAt(SEED, x, z);
                    if (s == 0.0D) {
                        valid = false; // 湖让位悬崖：transect 出域作废
                        break;
                    }
                    if (s < core) {
                        break; // 走出阈带下缘 ⇒ 结束
                    }
                    if (s < core + jit) {
                        band++;
                        entered = true;
                    }
                }
                if (valid && entered) {
                    bandWidths.add(Integer.valueOf(band));
                }
            }
        }
        final double j2Median = median(bandWidths);
        say("J-READ 过渡带：transect 尝试=" + j2Tried + " 有效=" + bandWidths.size() + " 阈带宽中位="
            + f3(j2Median) + "（s∈[" + f3(core) + "," + f3(core + jit) + ") 连续列宽）");
        check("J2 isDryRiverColumn 过渡带宽中位 ∈ [8,16] 格（P25 D6 阈抖带 [0.40,0.60) ×"
            + " ~42 格/单位 s 的 javadoc 换算式 ≈8.4 格 + 斜穿/噪声蜿蜒余量；有效 transect ≥ 32）",
            bandWidths.size() >= 32 && j2Median >= 8.0D && j2Median <= 16.0D,
            "中位=" + f3(j2Median) + " 有效=" + bandWidths.size() + "/" + j2Tried);
        // ── J3：isDryRiverColumn ⇒ !isSanzuColumn（≥10⁶ 列逐列不相交）──
        // 域 ±3×LAKE_INTERVAL（含湖站格与干床交汇带），步距 6：1001×1001 ≈ 1.002×10⁶ 列。
        final int j3Lim = (int) (GTSRVoronoiRiverField.LAKE_INTERVAL * 3.0D);
        final int j3Stride = 6;
        long j3Cols = 0;
        long j3Dry = 0;
        long j3Sanzu = 0;
        long j3Violations = 0;
        for (int z = -j3Lim; z <= j3Lim; z += j3Stride) {
            for (int x = -j3Lim; x <= j3Lim; x += j3Stride) {
                final boolean dry = GTSRVoronoiRiverField.isDryRiverColumn(SEED, x, z);
                final boolean sanzu = GTSRVoronoiRiverField.isSanzuColumn(SEED, x, z);
                j3Cols++;
                if (dry) {
                    j3Dry++;
                }
                if (sanzu) {
                    j3Sanzu++;
                }
                if (dry && sanzu) {
                    j3Violations++;
                }
            }
        }
        say("J-READ 不相交：列=" + j3Cols + " 干床列=" + j3Dry + " sanzu列=" + j3Sanzu
            + " 交列=" + j3Violations);
        check("J3 isDryRiverColumn ⇒ !isSanzuColumn（P25 D6 冻结接口的湖让位双保险腿）"
            + "：≥10⁶ 列采样交列 = 0 ∧ 干床/sanzu 两域各自成量（≥100 列，防双空集假绿）",
            j3Cols >= 1_000_000L && j3Violations == 0 && j3Dry >= 100 && j3Sanzu >= 100,
            "列=" + j3Cols + " 交=" + j3Violations + " 干床=" + j3Dry + " sanzu=" + j3Sanzu);
    }

    // ══════════════════════ K 床料群系分布（P26-B2 新增） ══════════════════════

    /** K 组采样步距（格）：{@link #SCAN_STRIDE}×2 = 16——床/滩料分布是面读数，stride 8 会把四档全扫推到 ~250 万列求值。 */
    static final int K_STRIDE = SCAN_STRIDE * 2;
    /** K 组沼泽侧样本成量下限（河核列；stride 16 窗 ±SCAN_EXTENT 实测 889，848 环列同窗）。 */
    static final int K_MIN_COLS = 200;
    /** K4 的 bed−pool 域容差（纯 double 端点等值噪声，非观测余量）。 */
    static final double K_EPS = 1e-9D;

    /**
     * <b>K 组：床/滩料群系分布（P26-B2 判据化新增）</b>——补 u49-riverbed §6 点名的判据面缺口
     * （「无任何断言钉床料/滩料的群系分布或硅砂覆盖率；沼泽缺硅砂、河床砾偏高均不在现有判据网内」）。
     * 选型链<b>零复刻</b>：s/pool/submerged/bedTopAtSurface 全走生产出口，材料表走
     * {@link GTSRRiverPlacer#bedMaterial}/{@link GTSRRiverPlacer#flatMaterial}（P26-B2 起 public，
     * 单一真值）；方块身份经 {@code SurfaceHarness.blockFamily} 离线直填（<b>不触 L1 账本</b> ⇒
     * rosterIndexCached 仍 -1、heightAt 仍默认档——与 G2a/G2b 同一近似口径，A-H/J 组不受装配影响）。
     * 口径边界（与生产落块的两处登记偏差）：① 落块的 {@code h > bedrockTopHash} 写入门不在采样面
     * （该门只剔基岩顶邻列，不改分布形状）；② heightAt 离线走默认档（生产沼泽列 amp 0.40 更平、
     * 床更深 0.5-1 格 ⇒ 滩/床分界略有偏移——断言带按本口径缩样读数钉，两态自洽）。
     * 断言带全部由缩样读数钉（{@code plan/tmp/p26-b2-readings.md}，前→后对拍在案）：
     * <ul>
     * <li><b>K1</b> 沼泽河核列表面 == 硅砂占比 ≥0.98（后态实测 1.000，889/889；P26 前基线
     * 沼泽核+环全清一色河砾——本条抓「沼泽段硅砂=0」回归）；</li>
     * <li><b>K2</b> 沼泽谷坡环列表面 == 硅砂占比 ≥0.95（后态实测 1.000，848/848）；</li>
     * <li><b>K3</b> 非沼泽档（0/1/2）床选型列表面 == 河砾占比 ≥0.98（实测 1.000——床料分档的
     * 「其余档恒河砾」腿，非沼泽段床料逐位不变是本片硬约束；滩带露头列按 flatMaterial 档表铺
     * 硅砂/粗沙属设计内，<b>不计入本分母</b>——离线口径草原档滩带列约占核列 17%）；</li>
     * <li><b>K4</b> 沼泽床高带：pool ∈ {63,65,67}（P26 前基线 {65,67,69}，最高档 69=SEA+1 正是
     * 「沼泽床砂砾与地面齐平」读数源）∧ 逐列 bed−pool ∈ [−2.5, −0.5]（下界 = depth 2.0+amp 0.5
     * 的结构域；上界 = {@code endFaceBed} 端面抬满值 pool−0.5，只抬不降）——床档随枯竭下调的
     * 行为钉（回潮 66.5/1.0 ⇒ pool 集合先红）。</li>
     * </ul>
     */
    static void groupK() {
        // 离线装配三沙族方块（身份判等用；先 vanilla 后家族，与 WitheredPlaneCheck 同序——
        // 不调 recordAllAllocations，理由见本组 javadoc）
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        final Block silica = BlocksGTSR.prosperitySilicaSand;
        final Block gravel = BlocksGTSR.prosperityRiverGravel;
        // —— K1/K2/K4：沼泽档（roster 3）——
        long core = 0, coreSilica = 0, bank = 0, bankSilica = 0, bedDomBad = 0;
        final HashSet<Integer> pools = new HashSet<Integer>();
        double bedDomMin = Double.MAX_VALUE, bedDomMax = -Double.MAX_VALUE;
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT; z += K_STRIDE) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT; x += K_STRIDE) {
                final double s = -GTSRVoronoiRiverField.strengthAt(SEED, x, z, 3);
                if (s >= GTSRVoronoiRiverField.WET_MIN) {
                    final int h = ProsperityTerrainProfile.heightAt(SEED, x, z);
                    final int pool = GTSRVoronoiRiverField.poolLevelAt(SEED, x, z, 3);
                    final boolean submerged = GTSRVoronoiRiverField.submergedAt(h, pool);
                    final Block bed = GTSRRiverPlacer.bedMaterial(3);
                    final Block surf = GTSRVoronoiRiverField.bedTopAtSurface(SEED, x, z, s, submerged) ? bed
                        : GTSRRiverPlacer.flatMaterial(3, bed);
                    core++;
                    if (surf == silica) {
                        coreSilica++;
                    }
                    pools.add(Integer.valueOf(pool));
                    final double d = GTSRVoronoiRiverField.bedFromPool(SEED, x, z, 3, pool) - pool;
                    bedDomMin = Math.min(bedDomMin, d);
                    bedDomMax = Math.max(bedDomMax, d);
                    if (d < -2.5D - K_EPS || d > -0.5D + K_EPS) {
                        bedDomBad++;
                    }
                } else if (GTSRVoronoiRiverField.inBankBand(SEED, x, z, s)) {
                    bank++;
                    if (GTSRRiverPlacer.flatMaterial(3, GTSRRiverPlacer.bedMaterial(3)) == silica) {
                        bankSilica++;
                    }
                }
            }
        }
        final double coreShare = core == 0 ? 0.0D : coreSilica / (double) core;
        final double bankShare = bank == 0 ? 0.0D : bankSilica / (double) bank;
        boolean poolsOk = !pools.isEmpty();
        for (final Integer p : pools) {
            if (p.intValue() != 63 && p.intValue() != 65 && p.intValue() != 67) {
                poolsOk = false;
            }
        }
        say("K-READ 沼泽床/滩料：河核列=" + core + " 硅砂=" + coreSilica + "（" + f3(100.0D * coreShare)
            + "%）谷坡环列=" + bank + " 硅砂=" + bankSilica + "（" + f3(100.0D * bankShare) + "%）"
            + " pool档=" + pools + " bed−pool∈[" + f3(bedDomMin) + "," + f3(bedDomMax) + "] 域违例="
            + bedDomBad + "（P26 前基线：核/环河砾 889/848 全清一色、pool {65,67,69}、"
            + "bed−pool∈[−1.488,−0.500]）");
        check("K1 沼泽河核列表面 == prosperitySilicaSand 占比 ≥0.98（P26-B2 D6 沼泽床/滩料引入硅砂的"
            + "行为钉；样本 ≥" + K_MIN_COLS + "。回退形态＝占比跌回 0（基线沼泽核 889/889 全河砾——"
            + "「沼泽河段硅砂=0」正是 u49-riverbed §2 根因读数）",
            core >= K_MIN_COLS && coreShare >= 0.98D,
            "占比=" + f3(100.0D * coreShare) + "% n=" + core + "/" + coreSilica);
        check("K2 沼泽谷坡环列表面 == prosperitySilicaSand 占比 ≥0.95（flatMaterial case 3 河砾→硅砂"
            + "的滩料腿；环列是滩带露头+谷坡环的合并采样面，容差比 K1 宽一档）",
            bank >= K_MIN_COLS && bankShare >= 0.95D,
            "占比=" + f3(100.0D * bankShare) + "% n=" + bank + "/" + bankSilica);
        check("K4 沼泽床高带：pool 档集合 ⊆ {63,65,67} ∧ 逐列 bed−pool ∈ [−2.5,−0.5]（P26-B2 随枯竭"
            + "下调 66.5/1.0→64.5/2.0 的行为钉：pool 量化步长 2 下合法档全部低于 SEA=68；域下界 ="
            + " depth+amp 结构域、上界 = endFaceBed 端面抬满值。回潮旧档 ⇒ pool 集 {65,67,69} 先红）",
            core >= K_MIN_COLS && poolsOk && bedDomBad == 0,
            "pools=" + pools + " bed−pool∈[" + f3(bedDomMin) + "," + f3(bedDomMax) + "] 违例=" + bedDomBad);
        // —— K3：非沼泽档（0/1/2）床选型列恒河砾 ——
        long bedSel = 0, bedSelGravel = 0, flatSel = 0;
        for (int r = 0; r <= 2; r++) {
            for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT; z += K_STRIDE) {
                for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT; x += K_STRIDE) {
                    final double s = -GTSRVoronoiRiverField.strengthAt(SEED, x, z, r);
                    if (s < GTSRVoronoiRiverField.WET_MIN) {
                        continue;
                    }
                    final int h = ProsperityTerrainProfile.heightAt(SEED, x, z);
                    final int pool = GTSRVoronoiRiverField.poolLevelAt(SEED, x, z, r);
                    final boolean submerged = GTSRVoronoiRiverField.submergedAt(h, pool);
                    if (GTSRVoronoiRiverField.bedTopAtSurface(SEED, x, z, s, submerged)) {
                        bedSel++;
                        if (GTSRRiverPlacer.bedMaterial(r) == gravel) {
                            bedSelGravel++;
                        }
                    } else {
                        flatSel++;
                    }
                }
            }
        }
        final double bedShare = bedSel == 0 ? 0.0D : bedSelGravel / (double) bedSel;
        say("K-READ 非沼泽床料（0/1/2 合并）：床选型列=" + bedSel + " 河砾=" + bedSelGravel + "（"
            + f3(100.0D * bedShare) + "%）滩带露头列（设计内、不入分母）=" + flatSel);
        check("K3 非沼泽档床选型列表面 == prosperityRiverGravel 占比 ≥0.98（床料分档「其余档恒河砾」腿"
            + "——非沼泽段床料逐位不变是 P26-B2 硬约束，digest 对拍 temp/p26-base 三 sha 逐位一致在案；"
            + "滩带露头列按 flatMaterial 档表铺硅砂/粗沙属设计内、不入分母）",
            bedSel >= 3 * K_MIN_COLS && bedShare >= 0.98D,
            "占比=" + f3(100.0D * bedShare) + "% n=" + bedSel + "/" + bedSelGravel + " flat=" + flatSel);
    }

    /** 真身份链取列 tier（粗格 4 格对齐，与 A1b 探针/生产 coarse 面同口径）。 */
    static int tierAt(long chainSeed, int x, int z) {
        return GTSRGenLayerRosterFace
            .rosterIndexAt(chainSeed, GTSRBiomeAuthority.DIM_KEY_PROSPERITY, (x >> 2) << 2, (z >> 2) << 2);
    }

    /**
     * {@code sanzuBiomeShoreAt(seed,x,z)} 的反射直调（P25 J1 用）：该方法是 RVF private（生产消费
     * 全在类内），判据侧不复制其公式（噪声盐/波长/单边 ≥ 语义一处抄错即假绿）——与 SLMC 的
     * {@code readPrivateInt(MIN_HEIGHT)} 同一"反射读真值、不抄第二份"先例。
     */
    static double sanzuShoreAtReflect(long seed, int x, int z) {
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

    private static volatile java.lang.reflect.Method SANZU_SHORE_METHOD;

    // ══════════════════════ 统计小件 ══════════════════════

    static double median(List<Integer> v) {
        if (v.isEmpty()) {
            return -1.0D;
        }
        final int[] a = new int[v.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = v.get(i).intValue();
        }
        Arrays.sort(a);
        return a.length % 2 == 1 ? a[a.length / 2] : (a[a.length / 2 - 1] + a[a.length / 2]) / 2.0D;
    }

    static double pct(List<Integer> v, int p) {
        if (v.isEmpty()) {
            return -1.0D;
        }
        final int[] a = new int[v.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = v.get(i).intValue();
        }
        Arrays.sort(a);
        return a[Math.min(a.length - 1, Math.max(0, (int) Math.round(a.length * p / 100.0D - 0.5D)))];
    }

    static String f3(double v) {
        return String.format("%.3f", Double.valueOf(v));
    }

    static void say(String line) {
        LINES.add(line);
        System.out.println(line);
    }

    static void check(String label, boolean ok, String detail) {
        assertions++;
        if (!ok) {
            failures++;
        }
        say((ok ? "PASS " : "FAIL ") + label + (detail.isEmpty() ? "" : " ｜ " + detail));
    }

    static void report() {
        say("═══ RiverMorphologyCheck assertions=" + assertions + " failures=" + failures + " ═══");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
