import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * <b>v1.20.39 T4 机检：dim78 Voronoi 河流强度场形态（plan §3.1/§3.2 校准回路的判据面）</b>。
 * 纯模型驱动（{@link GTSRVoronoiRiverField}/{@link ProsperityTerrainProfile} 均零世界读取，
 * 无需离线装配账本：按档测量传显式 rosterIndex；heightAt 链在未装配 JVM 走默认档，河谷
 * 压低链仍在）。P17RiverNetworkCheck（旧轴向等距线模型）已随旧模型删除，本判据接替其位。
 *
 * <p>
 * ═══ 七组断言（全部实跑；阈值 = plan §8 验收指标，统计功效参数全部从生产常数派生——
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
 * <li><b>F 荒漠断流</b>：荒漠档河核列 wetAt 占比 ∈ [0.2,0.4]（约 30%±10 河段有水，串珠断流）；</li>
 * <li><b>G 沼泽河宽 ×1.2</b>：沼泽档/常态档水道半宽比 ∈ [1.05,1.45]（v1.20.40 P19 §A.2 重钉：
 * ×1.6→×1.2 档乘子的行为读数带）；外加 heightAt 河谷集成：河核列 heightAt 与 bedAt 偏差 ≤1.5
 * （压低链真接进了高度）；</li>
 * <li><b>H 分段水位与端面（v1.20.40 P19 §B/§C 新增；U2 探针 P2/P3 读数升格）</b>：
 * H1 同 segKey 湿核列 poolLevelAt 全同（段内恒水面的纯函数性）；H2 干段端面收尾沿顺流剖面
 * 逐列床高差 ≤ 2（smoothstep 构造 ≤1/列；限顺流方向——段界斜交尖端的横向邻列不在口径内）。</li>
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
    /** 河道中心样本数下限：max(32, SEPARATION/32) —— 中位数判据的最小样本量。 */
    static final int N_CENTERS = Math.max(32, (int) (GTSRVoronoiRiverField.SEPARATION / 32));
    /** 半宽走查上限（格）：SEPARATION/7 = 150 > 4×谷半宽上限 ⇒ 谷缘必在限内。 */
    static final int WALK_LIMIT = (int) (GTSRVoronoiRiverField.SEPARATION / 7);
    /** 中心线追踪步长（格）与步数：总路长 = 3×LARGE_BEND_SCALE（覆盖 ≥3 个大弯波长）。 */
    static final int TRACE_STEP = (int) (GTSRVoronoiRiverField.SMALL_BEND_SCALE / 13);
    static final int TRACE_STEPS = (int) (GTSRVoronoiRiverField.LARGE_BEND_SCALE * 3) / TRACE_STEP;
    /** 追踪段数：SEPARATION/SMALL_BEND_SCALE = 13 段。 */
    static final int N_TRACES = (int) (GTSRVoronoiRiverField.SEPARATION / GTSRVoronoiRiverField.SMALL_BEND_SCALE);
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
     * ② {@code POOL_ANCHOR_AMP}/{@code POOL_ANCHOR_OFFSET}/{@code POOL_LEVEL_STEP}/{@code POOL_DROP}
     * 与 {@code DESERT_WET_SHARE}（R-C 用户裁决"水位阶梯/瀑布墙/断流闸保持不动"）；
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
     */
    static final double C2B_LO = 0.71D;
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
        groupF();
        groupG(centers);
        groupH();
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
                if (tv < 0.0D || tv > 1.0D - GTSRVoronoiRiverField.SANZU_TRUNK_EDGE || lv < 0.0D || lv > 1.0D) {
                    trunkShapeOk = false;
                }
                if (tv > 0.0D) {
                    trunkAlive = true;
                }
            }
        }
        check("A4 trunkAt/lakeAt 已激活（T5 接线）且确定性：trunk ∈ [0,1-EDGE]、lake ∈ [0,1]、采样窗非恒零",
            trunkShapeOk && trunkDeterministic && trunkAlive,
            "shapeOk=" + trunkShapeOk + " det=" + trunkDeterministic + " alive=" + trunkAlive);
        final GTSRVoronoiRiverField.RiverStyle[] styles = GTSRVoronoiRiverField.RIVER_STYLE_BY_ROSTER;
        // v1.20.39 T5/T8 重钉（plan §5 档表族 4→5 约定）：第 5 元 = sanzu 主干宽河档
        //（widthScale == TRUNK_WIDTH_SCALE=3.5，无浅滩/无断流门——主干带内全水域）。
        check("A5 档表 5 元（前 4 元草原/森林=常态+浅滩、荒漠=断流、沼泽=沼地河；[4]=sanzu 主干宽河档）",
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
        check("A7 床档表值域：常态床目标=64.5（水面 68 ⇒ 水深 2-5 源头）、沼泽床 ∈ [66.5,67.5]（水面近地）",
            styles[0].bedTarget == GTSRVoronoiRiverField.BED_TARGET
                && GTSRVoronoiRiverField.BED_TARGET == 64.5D
                && styles[3].bedTarget + styles[3].bedNoiseAmp >= 66.5D
                && styles[3].bedTarget - styles[3].bedNoiseAmp <= 67.5D,
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
     */
    static int waterHalfWidth(int x, int z, int rosterIndex) {
        int best = Integer.MAX_VALUE;
        for (int d = 0; d < 4; d++) {
            final int dx = d == 0 ? 1 : d == 1 ? -1 : 0;
            final int dz = d == 2 ? 1 : d == 3 ? -1 : 0;
            int r = 1;
            while (r <= WALK_LIMIT
                && -GTSRVoronoiRiverField.strengthAt(SEED, x + dx * r, z + dz * r, rosterIndex)
                    >= GTSRVoronoiRiverField.WET_MIN) {
                r++;
            }
            if (r <= WALK_LIMIT) {
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
            + "「常态半宽 3-4.5 格」；归因 U2/U34 prered 中位 4.0 在带）",
            widths.size() >= N_CENTERS / 2 && median >= 3.0D && median <= 4.5D,
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
        int best = Integer.MAX_VALUE;
        for (int d = 0; d < 4; d++) {
            final int dx = d == 0 ? 1 : d == 1 ? -1 : 0;
            final int dz = d == 2 ? 1 : d == 3 ? -1 : 0;
            int r = 1;
            // 1) 走开水道核（内缘恒 WET_MIN，不抖动——抖动只在外缘，见 inBankBand javadoc）
            while (r <= WALK_LIMIT && s(x + dx * r, z + dz * r) >= GTSRVoronoiRiverField.WET_MIN) {
                r++;
            }
            if (r > WALK_LIMIT) {
                continue; // 顺河方向：核无界，不是"坡宽"的样本
            }
            final int edge = r; // 水缘外第一列
            // 2) 数环的连续列
            while (r <= WALK_LIMIT
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
            + "例\"）、上界 1.0 不反向钉；实机后校准。**被实测否证的三个候选锚**（§22-B 旧域环列 0.094、"
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

    // ══════════════════════ F 荒漠断流 ══════════════════════

    static void groupF() {
        long core = 0;
        long wet = 0;
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT; z += SCAN_STRIDE) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT; x += SCAN_STRIDE) {
                if (-GTSRVoronoiRiverField.strengthAt(SEED, x, z, 2) < GTSRVoronoiRiverField.WET_MIN) {
                    continue;
                }
                core++;
                if (GTSRVoronoiRiverField.wetAt(SEED, x, z, 2)) {
                    wet++;
                }
            }
        }
        final double share = core == 0 ? -1.0D : wet / (double) core;
        say("F-READ 荒漠档河核列=" + core + " 过水列=" + wet + " 占比=" + f3(100.0D * share) + "%"
            + "（WET_EDGE=" + GTSRVoronoiRiverField.WET_EDGE + " 连续噪声闸）");
        check("F1 荒漠断流：过水占比 ∈ [20%,40%]（约 30%±10 ⇒ 串珠断流，不是全干也不是全满）",
            core >= N_CENTERS && share >= 0.2D && share <= 0.4D, "占比=" + f3(100.0D * share) + "% n=" + core);
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
     * P19 §B/§C 两个新面的正式断言（U2/U34 切片探针读数升格，验收 = 面被钉住）：
     * <ul>
     * <li><b>H1 池水位水平</b>：按 segKey（细胞对签名 = 段）分组后钉 §C 的构造不变量——
     * 一条细胞边只有<b>两个端点结点</b>，每端点的结点裁决（三边池水位取最小，"水往低池走"）
     * 只产生<b>一个</b>低于本段基池的档 ⇒ 每段的 poolLevelAt 档数 ≤ 3（本体 + 两端结点档），
     * 且达到段内最高池（= 边基池）的列占绝对多数。全部同档的严格版（U2 探针 P2 的 48 点）只对
     * 边中段成立——湿核列穿过端结点域时按裁决降档是<b>设计行为</b>（首轮实测湿核列 ~2 成带结点
     * 降档、12/36 向几何探环均有漏检角，故放弃"全同"与几何排除两种钉法），档数 &gt; 3 或最高池
     * 非多数 ⇒ 分段实现里存在逐列噪声型第二真值源；</li>
     * <li><b>H2 端面渐变</b>（U34 遗留① 的顺流口径）：荒漠整段闸档湿核列的干段端面（面 A：
     * 邻列换段且（非湿核 或 邻段未激活——与 endFaceBed 的面检测同判式））收尾，沿面向段内的
     * <b>顺流剖面</b>逐列床高差 ≤ 2——endFaceBed 的 smoothstep 抬升式每列 ≤ (target−bed)/2 ≪ 2
     * （U34 探针 P5：沿程剖面 ≤1/列）。段界斜交尖端的<b>横向</b>邻列小槛（同探针 2/80 的已知
     * 遗留）不在顺流口径内。</li>
     * </ul>
     */
    static void groupH() {
        // —— H1 池水位水平（每段档数 ≤ 3 = 本体 + 两端结点档；最高池 = 边基池占多数）——
        final java.util.HashMap<Long, java.util.HashMap<Integer, int[]>> segPools =
            new java.util.HashMap<Long, java.util.HashMap<Integer, int[]>>();
        int cols = 0;
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT; z += SCAN_STRIDE * 2) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT; x += SCAN_STRIDE * 2) {
                if (s(x, z) < GTSRVoronoiRiverField.WET_MIN) {
                    continue;
                }
                cols++;
                final long key = GTSRVoronoiRiverField.segKeyAt(SEED, x, z);
                final int pool = GTSRVoronoiRiverField.poolLevelAt(SEED, x, z, 0);
                java.util.HashMap<Integer, int[]> hist = segPools.get(Long.valueOf(key));
                if (hist == null) {
                    segPools.put(Long.valueOf(key), hist = new java.util.HashMap<Integer, int[]>());
                }
                final int[] slot = hist.get(Integer.valueOf(pool));
                if (slot == null) {
                    hist.put(Integer.valueOf(pool), new int[] { 1 });
                } else {
                    slot[0]++;
                }
            }
        }
        int worstLevels = 0;
        int segsOverLevels = 0;
        double worstMaxShare = 1.0D;
        for (final java.util.HashMap<Integer, int[]> hist : segPools.values()) {
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
        say("H-READ 池水位水平：湿核列=" + cols + " 段（segKey）=" + segPools.size() + " 段内档数最坏=" + worstLevels
            + " 档数>3 的段=" + segsOverLevels + " 最高池份额最坏=" + f3(worstMaxShare));
        check("H1 每段 poolLevelAt 档数 ≤ 3（本体 + 两端结点裁决档）且段内最高池份额 ≥ 1/2（P19 §C 段内恒"
            + "水面 + 结点取最小；实测最坏 0.500 = 短段两端结点域占满余量；档数超限或基池份额跌穿半数 = "
            + "逐列噪声型第二真值源）",
            segPools.size() >= N_CENTERS / 2 && segsOverLevels == 0 && worstMaxShare >= 0.5D,
            "段=" + segPools.size() + " 档数最坏=" + worstLevels + " 最高池份额最坏=" + f3(worstMaxShare));
        // —— H2 端面渐变（顺流剖面逐列床高差 ≤ 2）——
        // 扫描步距用 SCAN_STRIDE（端面 dist=1 抬满列是稀有面：U34 探针 80 抬升列/5665 湿核列
        // ≈1.4%，步距放大后窗内只剩个位数锚点——首轮 ×4 步距实测 walks=1）。
        int walks = 0;
        int badPairs = 0;
        double worst = 0.0D;
        for (int z = -SCAN_EXTENT; z <= SCAN_EXTENT; z += SCAN_STRIDE) {
            for (int x = -SCAN_EXTENT; x <= SCAN_EXTENT; x += SCAN_STRIDE) {
                if (-GTSRVoronoiRiverField.strengthAt(SEED, x, z, 2) < GTSRVoronoiRiverField.WET_MIN
                    || !GTSRVoronoiRiverField.wetAt(SEED, x, z, 2)) {
                    continue;
                }
                // 端面锚（dist=1 列）：8 邻域内"换段且（非湿核 或 邻段未激活）"的邻列 = 干段面
                // （与 endFaceBed 面 A 同判式）；床已抬满（round(bed) ≥ pool ⇒ dist=1 抬满到 pool−0.5）
                final long ownKey = GTSRVoronoiRiverField.segKeyAt(SEED, x, z);
                final int pool = GTSRVoronoiRiverField.poolLevelAt(SEED, x, z, 2);
                if (Math.round(GTSRVoronoiRiverField.bedAt(SEED, x, z, 2)) < pool) {
                    continue;
                }
                int fdx = 0;
                int fdz = 0;
                seek: for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        final int nx = x + dx;
                        final int nz = z + dz;
                        if (GTSRVoronoiRiverField.segKeyAt(SEED, nx, nz) != ownKey
                            && (-GTSRVoronoiRiverField.strengthAt(SEED, nx, nz, 2) < GTSRVoronoiRiverField.WET_MIN
                                || !GTSRVoronoiRiverField.segWetAt(SEED, nx, nz))) {
                            fdx = dx;
                            fdz = dz;
                            break seek;
                        }
                    }
                }
                if (fdx == 0 && fdz == 0) {
                    continue;
                }
                // 顺流剖面 = 面向的反方向（同一几何直线收进段内）走 END_FACE_LEN−1 列
                double prev = GTSRVoronoiRiverField.bedAt(SEED, x, z, 2);
                boolean any = false;
                for (int k = 1; k < GTSRVoronoiRiverField.END_FACE_LEN; k++) {
                    final int nx = x - fdx * k;
                    final int nz = z - fdz * k;
                    if (-GTSRVoronoiRiverField.strengthAt(SEED, nx, nz, 2) < GTSRVoronoiRiverField.WET_MIN
                        || GTSRVoronoiRiverField.segKeyAt(SEED, nx, nz) != ownKey) {
                        break; // 出湿核/出段：剖面只在本段湿核列上判
                    }
                    final double bed = GTSRVoronoiRiverField.bedAt(SEED, nx, nz, 2);
                    final double d = Math.abs(bed - prev);
                    worst = Math.max(worst, d);
                    if (d > 2.0D) {
                        badPairs++;
                    }
                    prev = bed;
                    any = true;
                }
                if (any) {
                    walks++;
                }
            }
        }
        say("H-READ 端面渐变（荒漠档顺流剖面）：剖面数=" + walks + " 逐列 |Δbed|>2 的对=" + badPairs + " 最坏="
            + f3(worst));
        check("H2 干段端面收尾沿顺流剖面逐列床高差 ≤ 2（P19 §B smoothstep 抬升式；U34 遗留① 的"
            + "横向尖端小槛不在顺流口径内）",
            walks >= 8 && badPairs == 0, "walks=" + walks + " badPairs=" + badPairs + " worst=" + f3(worst));
    }

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
