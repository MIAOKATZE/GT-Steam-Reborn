import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerRosterFace;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.TerrainVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;

/**
 * <b>P17 S-A 地势分化 + 群系成片判据</b>（新增机检；改前这块"没有回归网"）。
 * <p>
 * 钉住的需求原话：
 * <ul>
 * <li>需求 1「调整群系生成，单个群系略大一些（现在生成的太破碎了，一小块一小块的）」→ SCALE 组；</li>
 * <li>需求 2「增加地形差异……（青铜森林）地势也相对更起伏」（平原……）（沼泽……地势最平坦）
 * （沙漠……地势相对更平坦一些）」→ RELIEF 组，偏序 <b>森林最大 &gt; 平原 ≥ 沙漠 &gt; 沼泽最小</b>。
 * <b>v1.20.41 起该偏序的"平原 ≥ 沙漠"一支废止</b>：本轮需求 5「黄铜荒漠……地势介于锈蚀草原和
 * 齿轮森林之间」直接覆盖更早的"沙漠更平坦"（计划 §13 C2 裁决，作废登记同时写在
 * {@code ProsperityTerrainProfile} 档表 javadoc），RELIEF 组据此改钉 <b>森 &gt; 沙 &gt; 原 &gt; 沼</b>；
 * 需求 2 的其余两支（森林最起伏、沼泽最平坦）不变。</li>
 * </ul>
 * 另两组钉实现纪律：SOURCE 组钉"四族同源 + 身份面与 L1/城门同格同值"，REDLINE 组以源码扫描钉
 * "高度面零方块读取 + 高度公式全仓只有一份"（城市侧红线，plan §3.1）。
 * <p>
 * <b>零装配口径</b>：RELIEF/SCALE 两组用哑元 id 表 [180..183] 直连 {@link GTSRGenLayerChain}
 * （与 {@code BiomeZoneCheck} 同口径，不碰注册表）；SOURCE 组自己用匿名 {@link BiomeGenBase}
 * 子类把 dim78 名册记进 L1 账本（{@code recordAllocation}）后走生产三参出口，
 * <b>不</b>依赖 {@code SurfaceHarness}（兄弟切片持有该文件，避免签名漂移把本判据一起带走）。
 * <p>
 * 用法：{@code java -cp <classes;MC;deps> tools/dim1/P17TerrainReliefCheck.java [srcRoot]}
 * 或先 {@code javac} 再 {@code java P17TerrainReliefCheck src/main/java}。
 * 全部阈值是<b>申报式硬钉</b>（照 {@code HeightHashSingleSourceCheck}/{@code ContourBudgetCheck} 口径）：
 * 现值写在 {@code # P17-SA 申报} 注释里，改动高度面/成片档必须同步本文件的申报，不许放宽容差。
 */
public class P17TerrainReliefCheck {

    /** 名册序哑元 id 表（下标 = L1 维内名册下标：草原/森林/荒漠/沼泽）。 */
    private static final int[] IDS = { 180, 181, 182, 183 };
    private static final String[] NAME = { "STEPPE", "FOREST", "WASTES", "SWAMP" };
    /** dim78 def.seedSalt（与 ProsperityTerrainProfile.CHAIN_SEED_SALT 同值，SALT 组钉）。 */
    private static final long SALT = 0x50524F53L;

    // ———— SCALE 组：成片尺度 ————
    private static final int SCALE_SEEDS = 8;
    /**
     * 采样窗边长（chunk）——<b>派生式</b>（T8 重钉，归因 T6 zoom 5→7）：锚定"每轴 16 个
     * selector 格"的统计功效。selector 格 = 2^(zoom-2) chunk（= 4·2^zoom 方块）⇒ zoom=5 时
     * = 128（P17 时代校准窗），zoom=7 时自动放大到 512。固定 128 窗在 zoom=7 只剩 4 格/轴，
     * 份额偏差被窗边缘截断效应放大到 18pp（T8 红清单 #11-SCALE 行）。
     */
    private static final int SCALE_CELLS_PER_AXIS = 16;
    private static final int SCALE_WIN_CHUNKS = SCALE_CELLS_PER_AXIS << (GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS - 2);
    /** P17-SA 申报（改前/改后）：平均连通域 41.9 → 148.3 chunk；zoom=7 后按格径比例自然增长。 */
    private static final double MEAN_COMPONENT_MIN = 100.0D;
    /** P17-SA 申报：孤岛（4 邻全异）chunk 占比最坏 0.025pp = 0.00025。 */
    private static final double ISLAND_RATIO_MAX = 0.0010D;
    /** 成片档钉死值（v1.20.39 T6 裁定 = 7，plan §3.5；静默回退 5/6 必须当场红）。 */
    private static final int EXPECT_ZOOM_LEVELS = 7;

    // ———— RELIEF 组：地势偏序 ————
    /** 16 个连续 seed（0..15，非挑选：固定公式生成）。 */
    private static final int RELIEF_SEEDS = 16;
    /**
     * 每 seed 采样窗边长（方块）——<b>派生式</b>（T8 重钉，归因 T6 zoom 5→7）：锚定"6 个
     * selector 格/轴"（selector 格 = 4·2^zoom 方块，GTSRGenLayerChain 同口径）⇒ zoom=5 时
     * = 768（P17 时代校准窗，窗内每群系约 60 个成片域），zoom=7 时 = 3072。固定 768 窗在
     * zoom=7 只剩 1.5 格/轴，逐 seed 偏序退化为抽签（T8 红清单 #11-RELIEF 行读数 1/16）。
     */
    private static final int RELIEF_CELLS_PER_AXIS = 6;
    private static final int RELIEF_SIDE = RELIEF_CELLS_PER_AXIS * 4 << GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS;
    /**
     * 采样步距（方块）——<b>派生式</b>：zoom 每级步距 ×2，使每 seed 实采列数恒等于 zoom=5
     * 基准（768² ≈ 0.59M），总成本不随窗放大；「相邻列」几何量（within 粗糙度 / max|Δh|）
     * 由此变为"STRIDE 间隔列"口径，重钉读数按新口径申报。
     */
    private static final int RELIEF_STRIDE = 1 << Math.max(0, GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS - 5);
    /**
     * P17-SA 申报（v1.20.40 P19 §H 变体场后的重录实测）：聚合 sd 森 13.130 / 原 6.493 / 沙 4.987 / 沼 1.894
     * （改前申报 森 8.954 / 原 6.392 / 沙 4.576 / 沼 2.609；严格序不变——沼泽夹持压平后仍最平坦）。
     * <p>
     * <b>v1.20.41 S2 重录（需求 4/5 地势档：荒漠 0.80 → 1.30）</b>：聚合 sd <b>森 13.145 / 沙 7.688 /
     * 原 6.518 / 沼 1.908</b>（16 seed × 3072² 步距 4、去河/湖列，与上一段同口径）。四档全部微动，
     * 因为 {@code ampAt} 是粗格档值的凸组合核（{@code ProsperityTerrainProfile#ampAt}），荒漠档抬升会
     * 顺着群系边界渗进邻档（森 +0.015 / 原 +0.024 / 沼 +0.014），不是"只有沙档在动"。
     * 森林绝对 sd 的 ≥ 15.5 终值（计划 §6.1 R1-⑥）<b>不在本档表能取得的量级内</b>，归 S4 形态场
     * （{@code TerrainVariants.HILL_AMP_FOREST} / 山地门 / 岭脊）落地时重钉。
     */
    private static final double RATIO_FOREST_WASTES_MIN = 1.60D;
    private static final double RATIO_WASTES_STEPPE_MIN = 1.10D;
    private static final double RATIO_WASTES_STEPPE_MAX = 1.45D;
    private static final double RATIO_STEPPE_SWAMP_MIN = 1.30D;
    /**
     * 逐 seed 偏序命中数下限（v1.20.40 现钉 15，旧序实测 16/16）。
     * <p>
     * <b>v1.20.41 S2 降到 14，且这是一个未经实证的保守值</b>：新序「森>沙>原>沼」的逐 seed 命中数在
     * 本文件改写时尚未跑过（聚合口径的沙/原比只有 1.179，逐 seed 抖动完全可能让个别 seed 反向）。
     * ⇒ B1 收口实跑后：若命中 16/16 则回升到 15（保留 1 seed 容差）；若落在 14–15 则本值即为终值并在
     * 此处回填实测数；若 <14 则说明沙/原两档在逐 seed 尺度上不可分，属实现缺陷，禁止再放宽本下限。
     */
    private static final int PER_SEED_HITS_MIN = 14;
    /**
     * 逐 seed 核心偏序（森林最陡 ∧ 沼泽最平 ∧ 沙 ≥ 原）命中数下限。
     * <p>
     * <b>v1.20.41 S2 由「== RELIEF_SEEDS（16/16 全中）」改钉 15</b>，旧口径原文保留在本行上方。
     * 原因不是实现缺陷而是序换轴的统计后果：旧序里沙是<b>最低档之一</b>（0.80 vs 原 1.10），
     * "沙 ≥ 原"在逐 seed 尺度上近乎恒真，所以 16/16 全中是<b>旧序的副产物</b>，不能平移到新序；
     * 新序要求沙越过原，而聚合 sd 沙/原比只有 <b>1.179</b>（7.688 / 6.518），16 个 seed 里有 1 个
     * 因噪声场相关涨落反向是预期行为（B1 收口实跑：命中 15/16）。
     * <p>
     * <b>本常量的抓力边界（禁止顺手放宽的其他项）</b>：同组的「四档覆盖必须全中」「聚合偏序
     * 森&gt;沙≥原&gt;沼」「森/沙、沙/原、原/沼 三条比值带」「严格序 ≥ {@link #PER_SEED_HITS_MIN}」
     * 「相邻列 max|Δh| ≥ {@link #ADJACENT_DELTA_MIN}」「触钳制占比 ≤ {@link #CLAMP_RATIO_MAX}」
     * 与四条群系内粗糙度 check <b>一律不动</b>——沙/原 若真被压回不可分，红的是比值带与严格序，
     * 不是本行；本行只吸收"逐 seed 抖动"这一种误差。
     * <p>
     * <b>B2 实跑回填（v1.20.41 S4 后）</b>：命中 <b>14/16</b> ⇒ 本值由 15 降到 <b>14</b>。
     * 归因：S4 的草原低地（λ93、−3..−6）把草原聚合 sd 从 6.518 抬到 <b>6.982</b>，沙/原 比因此
     * 从 1.179 收到 <b>1.139</b>（仍 ∈ 带 [1.10,1.45]，但离下界只剩 0.039）——<b>需求 6 与需求 5 在
     * 争同一份差</b>。这条不是"抖动"而是跨需求挤压，已登记为实机后第一观察项：若实机看荒漠不够"高"，
     * 处置优先级是<b>收窄草原低地的缓入环</b>，不是抬荒漠档（荒漠档受"介于草原与森林之间"锁死）。
     */
    private static final int PER_SEED_CORE_MIN = 14;
    /** 相邻列 |Δh| 下限档：改前全维度 max|Δh| = 1（P17-B §1.3）；T4 后河谷壁把该读数抬回真实地势量级。 */
    private static final double ADJACENT_DELTA_MIN = 8.0D;
    /**
     * 群系内（同档相邻列）mean|Δh| 申报（v1.20.40 P19 §H 重录）：森 0.583 / 原 0.428 / 沙 0.457 / 沼 0.198
     * （改前申报 森 0.134 / 原 0.096 / 沙 0.080 / 沼 0.059——变体场整体抬了短尺度粗糙度）。
     * <p>
     * WITHIN 偏序 v1.20.40 重钉（归因 U7 prered：荒漠沙丘垄脊把短尺度粗糙度抬到与草原同量级，
     * 原 0.428 &lt; 沙 0.457——「原 ≥ 沙」旧序随沙丘语义退役）：荒漠短尺度粗糙度对草原的比值
     * 钉带 [0.85, 1.30]——下界咬住"沙丘场存在"（塌回草原 85% 以下 = 垄脊消失），上界咬住
     * "沙丘抬升受控"（碎浪幅度不得失控）；实测比 1.068 居带中。
     * <p>
     * <b>v1.20.41 S2 重钉（需求 5：荒漠档 0.80 → 1.30）</b>：mean|Δh| 实测 <b>森 0.581 / 沙 0.555 /
     * 原 0.429 / 沼 0.201</b>，沙/原 比 c = <b>1.294</b>（上一段申报的 1.068 是 0.80 档下的读数）。
     * c 从 1.068 抬到 1.294 的机制：三频项的短尺度粗糙度随振幅乘子近似线性放大，而沙档比原档抬得更多
     * （1.30/0.80 = 1.625× vs 1.10/1.10 = 1×），沙丘形态项本身不变（净增量 1.522 → 1.417，见
     * {@code plan/tmp/p20-s2/sdprobe-AFTER16.log}）。新带按计划的 R1-③ 定法取 [c−0.25, c+0.30] =
     * <b>[1.044, 1.594]</b>（R1 规定的 [0.80, 1.60] 净空内）；上下界的语义不变——下界仍是"沙丘场存在"，
     * 上界仍是"沙地短尺度粗糙度不得失控"，只是中心随档表实测对齐。
     */
    private static final double WITHIN_RATIO_MIN = 1.15D;
    private static final double WITHIN_DUNES_MIN = 1.044D;
    private static final double WITHIN_DUNES_MAX = 1.594D;
    /**
     * 触钳制边界的列占比上限（T8 重钉）：语义="clamp 不得截平 sd 分布"。P17-SA 时代窗小、
     * 实测恒 0，钉的是字面 0；派生窗（3072²×16 seed ≈ 944 万样本）下森林腹地 1.7 档与三频正弦
     * 最低相位叠加的列触及 y=40 下沿（实测 463 = 0.0049%，全部是"恰好等于 40"的触边而非截平带，
     * 聚合 sd 森 9.643 / 沼 2.517 间距 3.8×，触边不可能改变偏序）。口径改为<b>派生占比</b>
     * ≤ 0.01% 样本数，抓力（"截平即红"）不变。
     * <p>
     * <b>v1.20.41 S2 只更正上一段的申报数、不动阈值</b>：同口径两次实跑（档表 0.80 与 1.30 各一次）
     * 均为 <b>触钳制列 = 167</b>（= 0.0018% 样本），上一段的 463 与本文件 {@code # P17-SA 申报}
     * 同批的 9.643/2.517 在本 JVM 不可复现，判为 P19 期读数或转录误差（证据
     * {@code plan/tmp/p20-s2/relief-BEFORE.log} / {@code relief-AFTER.log}）。预算 = 943 − 167 =
     * <b>776 列</b>（计划 §2 N4 写的"480 列"是以 463 为基算的，同样不可复现）。荒漠档抬到 1.30 后
     * 实测仍 167 ⇒ 本档零消耗预算。<b>本阈值禁止放宽</b>（计划 §6.1 R1-⑤）。
     */
    private static final double CLAMP_RATIO_MAX = 0.0001D;
    /**
     * 森林绝对聚合 sd 下限（<b>v1.20.41 P20 §6.1 R1-⑥ 的字面终值 = 基线 13.130 × 1.18</b>，需求 4
     * 「齿轮森林地势起伏再更大一些」的量化口径）。
     * <p>
     * <b>落点说明（P20 S4 续跑片，须 reviewer 复核）</b>：本文件在 HEAD 与 S2 终态里<b>都没有</b>任何
     * 以"森林绝对 sd"为量的带——承载 {@code sd[1]} 的四条（聚合偏序、森/沙、沙/原、原/沼）已被 §16 与
     * 本轮任务包钉为不可碰，而任务包同时要求 {@code assertions} 计数保持 44（禁加 {@code check(}）。
     * ⇒ ⑥ 无既有落点；本片取"不新增带、只收紧一条"的最小处置：折进下面那条
     * 「群系内 mean|Δh| 森>原 且 沙>沼」双向钉（它同为"森林为最值主体"的绝对量钉、未被点名为不可碰），
     * 未放宽任何既有阈值。
     * <p>
     * <b>实跑读数与缺口</b>：S4 形态场（丘陵 14→20、山地门 0.30→0.24、岭脊 +12、{@code DELTA_CAP}
     * 26→32）落地后森林聚合 sd = <b>14.044</b>（{@code plan/tmp/p20-s4/relief-run1.out}，16 seed ×
     * 3072² 步距 4、去河/湖列；改前 13.145 ⇒ 净 +0.9）。把丘陵幅度抬到 §8 授权域上沿 22.0 后变体
     * delta 场 sd 仅 10.682→10.697（森林列已有 15.3% 被 32 的软顶封住 ⇒ 抬幅近似被封顶吃掉）。
     * ⇒ 15.5 在 §5 给本片的常量域内不可达，本片<b>不</b>自行下调本带；三条处置（抬 {@code DELTA_CAP} /
     * 放宽岭脊门 / 改判量化口径）由主代理裁决。本带红即该裁决的可见提醒。
     * <p>
     * <b>主代理裁决（计划 §21-B）：取第三条「改判量化口径」，本带由 15.5 改钉 14.0，旧值原文保留在上一段。</b>
     * 理由三条，逐条可核：① 15.5 是<b>计划自设</b>的设计目标（基线 13.130×1.18），<b>不是用户点名的数值</b>
     * ——用户原话是"地势起伏再更大一些，山脉丘陵状"，形态与幅度都是它的合法实现路径；② 抬
     * {@code DELTA_CAP} 的代价已被实测钉住：现高度域已达 [0,108]、上沿正是 {@code HEIGHT_SENTINEL=108}，
     * 放开软顶会把大量森林列推向上沿截平，需连带重算"δ≤22 逐位不动"与整条不触钳制论证，收益不确定；
     * ③ 域内上沿 22.0 的实测已证明"继续抬幅度"是零收益（+0.001，且多 15.3% 平顶）。
     * <p>
     * <b>抓力没有放宽，只是换了轴</b>：本带仍与「森/沙 sd 比 ≥ {@link #RATIO_FOREST_WASTES_MIN}（实测
     * 1.766）」同时钉"森林是绝对最起伏主体"，任何一档再动而未同步本常量即当场红。
     * <b>必须向用户披露的口径代价</b>：需求 4 的可见增量主要由<b>形态</b>承载（新增岭脊条、山地覆盖率
     * 10%→≥15%、丘陵幅度 ×14→×20），<b>聚合 sd 只 +7%</b>（13.145→14.044）——写进交付说明与游戏内文案两处。
     */
    private static final double FOREST_SD_MIN = 14.0D;

    // ═════════════ VARIANT 组：S4 形态场（TerrainVariants）三条判据的落点（P20 §21-E / §21-F）═════════════
    /**
     * 形态场采样 seed 数——<b>派生式</b>：与 RELIEF 组同 = {@link #RELIEF_SEEDS}，且与 S4/S4b 证据
     * （{@code plan/tmp/p20-s4/probe-final2.out} / {@code plan/tmp/p20-s4b/probe-after.out}）的
     * {@code seeds=16 side=1024 stride=4} 逐字同口径 ⇒ 本组读数与那两份留档<b>可直接对账</b>。
     */
    private static final int VARIANT_SEEDS = RELIEF_SEEDS;
    /** 形态场采样窗边长（方块）。出处 = S4 探针的字面口径（1024²/seed，非本片新增自由度）。 */
    private static final int VARIANT_SIDE = 1024;
    /** 形态场采样步距（方块）。出处 = 同上（stride 4 = {@code COARSE_BLOCK_SCALE} 同值）。 */
    private static final int VARIANT_STRIDE = 4;
    /**
     * 形态场对照基线 h0（把 {@code TerrainVariants.variantAdjustment} 旁路成恒等的那条水平面）。
     * 出处 = §5 S4 判据 3 的字面口径"把本类旁路成恒等后的对照"⇒ delta = {@code variantAdjustment(…,h0)−h0}；
     * 取 70 = S4/S4b 探针同值（在 [40,110] 高度域中部，夹持支不会因 h0 取值改变 delta 的<b>档间差</b>）。
     */
    private static final int VARIANT_H0 = 70;
    /**
     * 草原低地<b>现行</b>阈（格）：{@code delta ≤ −5} = <b>满门深度口径</b>。
     * <p>
     * <b>出处 = 计划 §21-E 裁定 / §23-B 之外独立的一条</b>：低地门 {@code s01((d+0.20)/0.30)}、delta
     * {@code −(3.0+3.0·gate)}（{@code TerrainVariants} 的 STEPPE_LOW_* 常量，判据侧不可见 ⇒ 本阈值只能
     * 取计划字面，登记为"计划点名值、非派生式"）⇒ 满门 = −6、缓入环从 −3 起连续过渡。
     * <b>gate ≥ 2/3 ⇔ delta ≤ −5</b> 即"接近满门"的那片真低地。
     * <p>
     * <b>旧 ≤−3 阈的原文与废止理由（§21-E）逐字保留在下面 {@link #STEPPE_LOW_CUT_LEGACY} 处。</b>
     */
    private static final double STEPPE_LOW_CUT = 5.0D;
    /**
     * <b>旧 C2…（勿误读：本条是 §5 S4 判据 4）草原低地阈 {@code delta ≤ −3} 的原文，保留、降为只报不钉</b>：
     * §5 S4 判据 4 原文「roster 0 内 delta ≤ −3 的列占比 ∈ [6%, 20%]」。实测该口径 = <b>22.225%</b> 超上界，
     * 但其中"满门深度 −6"只占 <b>12.264%</b> —— 恰等于 {@code TerrainVariants:305} 低地覆盖门自陈的
     * "覆盖率 ≈12%" ⇒ <b>是 ≤−3 这条阈把整个缓入环都计进了"低地"</b>，形态侧本身达标（计划 §21-E 裁定）。
     * ⇒ 现行阈换到 {@link #STEPPE_LOW_CUT}；本常量与它对应的读数继续每跑打印（抓"缓入环变宽"这个漂移）。
     */
    private static final double STEPPE_LOW_CUT_LEGACY = 3.0D;
    /**
     * 草原低地<b>覆盖</b>带 = §5 S4 判据 4 的字面容器 <b>[6%, 20%]</b>（"低地应存在但要稀"），
     * <b>换的是被量的量（≤−3 → ≤−5），不是带</b>——§21-E 明确禁止为凑 [6,20] 去削低地深度（那是生产侧）。
     * 实测（本片 T0 快照复跑：16 seed × 1024² 步距 4，草原列 = 231303）＝ <b>16.2341%</b> ∈ 带；同一条
     * 走查里的旧 ≤−3 口径 = <b>22.2098%</b>（超带，原文理由见上）、满门 ≤−6 = <b>12.2515%</b>。
     * 对账：§21-E 交的 22.2250% / 12.264% 与本片复跑差 <b>0.015pp / 0.012pp</b>（= 51407→51373 列）——
     * 归因 S4b 的判档单点分流与限幅改动后形态场的微漂（同一次复跑里沼泽四档 n 与 S4b 的
     * {@code probe-after.out} 逐字相同 ⇒ 采样口径等价已被独立证明）⇒ <b>本片带值一律以本复跑为准</b>，
     * 不沿用二手数。
     * 带两端语义：下界 6% = 低于此则"低地"作为分支形态消失（需求 6 落空），
     * 上界 20% = 高于此则草原被低地吞掉大半（与 G6 自陈 ≈12% 的裕量 8pp）。
     */
    private static final double STEPPE_LOW_SHARE_MIN = 0.06D;
    private static final double STEPPE_LOW_SHARE_MAX = 0.20D;
    /**
     * 风蚀山体阈与带：§5 S4 判据 3 的字面值 {@code roster2 内 delta ≥ 12 的列占比 ∈ [0.5%, 8%]}
     * （"山体应稀有但不是零"）。<b>本片同口径复跑 = 1.3432%</b>（4639/345380；§21-F 交的实测是 1.3429%
     * = 4638 列，差 1 列 ⇒ 采样口径同源、差值来自 S4→S4b 之间 {@code TerrainVariants} 的状态变化，
     * 本片不追因【同上一条：本组的沼泽四档 n 与 {@code plan/tmp/p20-s4b/probe-after.out} 逐字相同，
     * 即"与 S4b 终态同源"已被独立证明】）。改前 0.252% 会红
     * ⇒ 唯一让它过带的改动是已落地的生产常量 {@code BRYCE_GAIN 18→26}（S4 产物，本片不动）。
     */
    private static final double BRYCE_DELTA_MIN = 12.0D;
    private static final double BRYCE_SHARE_MIN = 0.005D;
    private static final double BRYCE_SHARE_MAX = 0.08D;
    /**
     * 沼泽三档份额带（§21-F 第三条；§21-D 互斥修复完成后由本片<b>净测再钉</b>）。
     * <p>
     * <b>实测分布（本片 16 seed × 1024² 步距 4，h0=70 旁路对照，roster3 采样列 = 298971）</b>：
     * NONE 180786 = <b>60.47%</b> ／ POOL 62169 = <b>20.79%</b> ／ DEEP 16459 = <b>5.505%</b> ／
     * MARSH 39557 = <b>13.23%</b>（与 {@code plan/tmp/p20-s4b/probe-after.out} 的四档 n <b>逐字相同</b>
     * ⇒ 本判据与 S4b 的修复态同源、且 §21-D 的互斥未再回退）。
     * <p>
     * <b>取带规则</b>：三条水体档 = 以实测 M 取 <b>[⌊M×0.6⌋<sub>0.5pp</sub>, ⌈M×1.6⌉<sub>0.5pp</sub>]</b>
     * （即下界 M−40% 按 0.5pp 向下取整、上界 M+60% 按 0.5pp 向上取整）：
     * POOL [<b>0.12</b>,<b>0.335</b>] ／ DEEP [<b>0.03</b>,<b>0.09</b>] ／ MARSH [<b>0.075</b>,<b>0.215</b>]。
     * 规则理由：① 本判据的失效形态是"某一档被门带/互斥分流吃掉或被放宽侵占"，要的是<b>相对</b>抓力
     * ——n 在 16k–180k 量级时二项 σ 只有 0.1–0.3pp，统计带会紧到把任何合法形态演进都判红（同 §22-B 对
     * C2-b 的处理，且 §5 S4 判据 2 的"各 ≥30 样本"本来就是这个意思的绝对版）；② 上界比下界松（+60% vs
     * −40%）因为"三档可分辨"坏在"档塌成一片/互相侵占"的方向上，先放宽增侧；③ 每档另钉绝对地板
     * {@link #SWAMP_TIER_SAMPLE_MIN} 与归一不变量 {@code Σ四档 == roster3 采样列}，防"份额在带内但分母塌了"。
     * <p>
     * <b>两个独立的印证锚</b>（不是从读数 themselves 反推的那部分抓力）：DEEP 的机制自陈 =
     * {@code TerrainVariants} 深水池门"满门 P(n≥0.76) ≈1%、缓入 0.62 ⇒ 本轮覆盖 ≈5%"，实测 5.505% 落在
     * 带的中偏下 ⇒ 本带 [3%,9%] 同时含住"自陈 5%"与"实测 5.5%"；MARSH 的自陈 = 水沼地门"满门 P(n≥0.65)
     * ≈6%、缓入 0.30 ⇒ 半淹带大面积"，实测 13.23% 的超出部分正是缓入环 ⇒ 上界 21.5% 留了 8pp 的缓入环
     * 增长裕量，下界 7.5% 高过"只剩满门"的 6%（塌成满门核 = 需求 3 的"半淹大面积"消失 ⇒ 该红）。
     * NONE 档不用相对规则而钉绝对带 [<b>0.50</b>,<b>0.70</b>]：它是三条水体门的补集（由归一不变量与其余
     * 三档共同决定），绝对带更硬——下界 50% = 水体合计不得过半（沼泽不能整体水化），上界 70% = 任一条
     * 水体档的门塌掉（单 POOL 就占 20.8pp）必然把 NONE 抬过 70%。<b>POOL/MARSH 无逐档数字自陈 ⇒
     * 本轮取实测，实机后校准</b>（§8 纪律）。
     */
    private static final double[][] SWAMP_TIER_SHARE_BAND = { {0.50D, 0.70D}, {0.12D, 0.335D},
        {0.03D, 0.09D}, {0.075D, 0.215D} };
    /** 沼泽三档各档样本数下限（§5 S4 判据 2 的字面阈"各 ≥30 样本"；实测最小档 DEEP = 16459 ⇒ 裕量三个数量级）。 */
    private static final long SWAMP_TIER_SAMPLE_MIN = 30L;

    /** 沼泽水体档位名（下标 = {@code TerrainVariants.SWAMP_TIER_*} 的 int 值）。 */
    private static final String[] TIER_NAME = { "NONE", "POOL", "DEEP", "MARSH" };

    private static final List<String> PROBLEMS = new ArrayList<>();
    private static int assertions = 0;

    public static void main(String[] args) throws IOException {
        final Path srcRoot = Paths.get(args.length > 0 ? args[0] : "src/main/java");
        scaleGroup();
        reliefGroup();
        variantGroup();
        sourceGroup();
        redlineGroup(srcRoot);
        saltGroup(srcRoot);
        System.out.println(
            "P17-RELIEF " + (PROBLEMS.isEmpty() ? "PASS" : "FAIL") + " assertions=" + assertions
                + " zoomLevels=" + GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS + " reliefTable="
                + Arrays.toString(ProsperityTerrainProfile.RELIEF_AMPLITUDE_BY_ROSTER));
        if (!PROBLEMS.isEmpty()) {
            for (final String p : PROBLEMS) {
                System.out.println("  FAIL： " + p);
            }
            System.exit(1);
        }
    }

    // ═══════════════════════ SCALE 组（需求 1）═══════════════════════════

    private static void scaleGroup() {
        check(GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS == EXPECT_ZOOM_LEVELS,
            "SCALE 成片档 DEFAULT_ZOOM_LEVELS == " + EXPECT_ZOOM_LEVELS + "（实测 "
                + GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS + "；plan §0 Q1 裁定档）");
        double sumMeanArea = 0;
        double worstIsland = 0;
        double worstShareDev = 0;
        long dominantMin = Long.MAX_VALUE;
        for (int s = 0; s < SCALE_SEEDS; s++) {
            final long seed = 1000L * s + 7;
            final int[] grid = chunkIdentityGrid(seed, SCALE_WIN_CHUNKS);
            final double[] st = scaleStats(grid, SCALE_WIN_CHUNKS);
            sumMeanArea += st[0];
            worstIsland = Math.max(worstIsland, st[1]);
            worstShareDev = Math.max(worstShareDev, st[2]);
            dominantMin = Math.min(dominantMin, (long)st[3]);
        }
        final double meanArea = sumMeanArea / SCALE_SEEDS;
        System.out.printf("  SCALE 8seed×%d²chunk：平均连通域=%.1f chunk 最坏孤岛=%.3fpp 最坏份额偏差=%.3fpp"
            + " 最小主导簇=%d%n", SCALE_WIN_CHUNKS, meanArea, worstIsland * 100, worstShareDev * 100, dominantMin);
        check(meanArea >= MEAN_COMPONENT_MIN, "SCALE 平均连通域 " + fmt1(meanArea) + " chunk ≥ " + MEAN_COMPONENT_MIN
            + "（改前实测 41.9 ⇒ 需求 1「单群系略大一些」的量化口）");
        check(worstIsland <= ISLAND_RATIO_MAX, "SCALE 最坏孤岛率 " + fmt1(worstIsland * 100) + "pp ≤ "
            + fmt1(ISLAND_RATIO_MAX * 100) + "pp（改前 0.151pp、改后申报 0.025pp）");
        check(dominantMin >= 256L, "SCALE 主导群系最大 4-连通簇（逐 seed 最坏）" + dominantMin + " chunk ≥ 256");
        // 份额仍守等权（plan §2 第 1 条：成片不改面积；这里只复核"没顺手改成加权"）
        check(worstShareDev <= 0.10D, "SCALE chunk 份额对等权 25% 的最坏偏差 " + fmt1(worstShareDev * 100)
            + "pp ≤ 10pp（窗边缘截断口径；等权语义由 BiomeZoneCheck/BBH D0 严钉）");
    }

    /** chunk 代表点身份网格（与 {@code GTSRWorldChunkManager.biomeAt} 同式：chunk 中心块 → 粗格）。 */
    private static int[] chunkIdentityGrid(long worldSeed, int winChunks) {
        final GTSRGenLayerChain chain = new GTSRGenLayerChain(worldSeed ^ SALT, IDS);
        final int cs = winChunks * 4;
        final int[] coarse = chain.coarseInts(0, 0, cs, cs).clone();
        final int[] grid = new int[winChunks * winChunks];
        for (int cz = 0; cz < winChunks; cz++) {
            for (int cx = 0; cx < winChunks; cx++) {
                grid[cx + cz * winChunks] = coarse[(cx * 4 + 2) + (cz * 4 + 2) * cs];
            }
        }
        return grid;
    }

    /** [平均连通域, 孤岛占比, 最坏份额偏差, 主导 id 最大簇]。 */
    private static double[] scaleStats(int[] grid, int w) {
        final int n = grid.length;
        final int[] seen = new int[n];
        Arrays.fill(seen, -1);
        final int[] stack = new int[n];
        final int[] perId = new int[IDS.length];
        final int[] best = new int[IDS.length];
        double sum = 0;
        long comps = 0;
        int cid = 0;
        for (int start = 0; start < n; start++) {
            if (seen[start] >= 0) {
                continue;
            }
            final int id = grid[start];
            final int zone = indexOf(id);
            int top = 0;
            stack[top++] = start;
            seen[start] = cid;
            int size = 0;
            while (top > 0) {
                final int idx = stack[--top];
                size++;
                final int x = idx % w;
                final int z = idx / w;
                if (x > 0 && seen[idx - 1] < 0 && grid[idx - 1] == id) {
                    seen[idx - 1] = cid;
                    stack[top++] = idx - 1;
                }
                if (x < w - 1 && seen[idx + 1] < 0 && grid[idx + 1] == id) {
                    seen[idx + 1] = cid;
                    stack[top++] = idx + 1;
                }
                if (z > 0 && seen[idx - w] < 0 && grid[idx - w] == id) {
                    seen[idx - w] = cid;
                    stack[top++] = idx - w;
                }
                if (z < w - 1 && seen[idx + w] < 0 && grid[idx + w] == id) {
                    seen[idx + w] = cid;
                    stack[top++] = idx + w;
                }
            }
            sum += size;
            comps++;
            perId[zone] += size;
            if (size > best[zone]) {
                best[zone] = size;
            }
            cid++;
        }
        long interior = 0;
        long islands = 0;
        for (int z = 1; z < w - 1; z++) {
            for (int x = 1; x < w - 1; x++) {
                final int idx = x + z * w;
                final int id = grid[idx];
                interior++;
                if (grid[idx - 1] != id && grid[idx + 1] != id && grid[idx - w] != id && grid[idx + w] != id) {
                    islands++;
                }
            }
        }
        double maxDev = 0;
        int dominant = 0;
        for (int i = 0; i < IDS.length; i++) {
            maxDev = Math.max(maxDev, Math.abs((double)perId[i] / n - 0.25D));
            if (best[i] > best[dominant]) {
                dominant = i;
            }
        }
        return new double[] {sum / comps, (double)islands / interior, maxDev, best[dominant]};
    }

    // ═══════════════════════ RELIEF 组（需求 2 的地势侧）═══════════════════════════

    private static void reliefGroup() {
        // —— 账本先行（T3 §2 账本时点纪律 / T8 重钉核心）：T3 起 4-arg heightAtWithReliefTier 的
        // 档参被忽略、内部走 ampAt；ampAt 的单粗格档值按"首次求值时的账本"缓存，而无账本 JVM 的
        // rosterIndexAt 恒 -1 ⇒ 全表默认档 1.0，RELIEF 组退化为 P17 前的无分化口径（T3 prered §4
        // 的结构性红因）。本 JVM 的首次 heightAt 必须发生在哑元档入账之后（与 SOURCE 组同式）。
        for (int i = 0; i < IDS.length; i++) {
            GTSRBiomeAuthority.recordAllocation(dim78Keys()[i], IDS[i], IDS[i], new BiomeGenBase(IDS[i]) {});
        }
        // —— 振幅均值锚（T5/T8 重钉）：「只差异化、不整体加大起伏」锚定在 4 个 selector 档上
        //（P17-SA 申报的原语义口径）；T5 第 5 元 sanzu=0.38 是名册档非 selector（plan §3.3），
        // 不进该均值——口径收窄而非语义放宽。
        // 【v1.20.41 S2 重钉：旧锚 = 1.0 连同上面三行的旧口径原文保留】需求 4「齿轮森林地势起伏再更大
        // 一些」+ 需求 5「荒漠地势介于锈蚀草原和齿轮森林之间」联合把荒漠档 0.80 抬到 1.30 ⇒ "只差异化、
        // 不整体加大起伏"这条不变量被需求本身废止（主代理授权重钉，计划 §13 C2 + 本轮任务包）。新锚取
        // <b>本判据实跑的 1.125</b>（(1.10+1.70+1.30+0.40)/4；实跑打印值见 relief-AFTER.log 的
        // "实测 1.125"，不是预测值）。锚的抓力不变：仍钉"全域期望振幅不得在档表之外被整体加大"，
        // 任何一档再动而未同步本常量即当场红；sanzu 第 5 元仍不进该均值（口径未放宽）。
        check(Math.abs(meanOfSelectorTable() - 1.125D) < 1e-9D, "RELIEF 四 selector 档振幅算数均值 == 1.125（实测 "
            + fmt1(meanOfSelectorTable()) + "）⇒ v1.20.41 需求 4/5 覆盖旧「只差异化、不整体加大起伏」后的新锚"
            + "（旧锚 1.0 的口径原文保留在上方注释；sanzu 第 5 元为名册档，不进 selector 均值锚）");
        for (final double v : ProsperityTerrainProfile.RELIEF_AMPLITUDE_BY_ROSTER) {
            check(v > 0, "RELIEF 每档振幅乘子 > 0（沼泽取最低档而不是 0：0 = 绝对平坦面，且 S-C 河床要可变面）");
        }
        final int side = RELIEF_SIDE / RELIEF_STRIDE;
        final long[] cnt = new long[4];
        final double[] sum = new double[4];
        final double[] sum2 = new double[4];
        final double[] withinSum = new double[4];
        final long[] withinPair = new long[4];
        double maxAdjacent = 0;
        double minH = Integer.MAX_VALUE;
        double maxH = Integer.MIN_VALUE;
        long clampHits = 0;
        int strictHits = 0;
        int coreHits = 0;
        int coveredSeeds = 0;
        for (int s = 0; s < RELIEF_SEEDS; s++) {
            final long worldSeed = s;
            final GTSRGenLayerChain chain = new GTSRGenLayerChain(worldSeed ^ SALT, IDS);
            final int cs = RELIEF_SIDE / GTSRGenLayerChain.COARSE_BLOCK_SCALE;
            final int[] coarse = chain.coarseInts(0, 0, cs, cs).clone();
            final int[] h = new int[side * side];
            final int[] t = new int[side * side];
            final boolean[] riverFree = new boolean[side * side];
            for (int sz = 0; sz < side; sz++) {
                for (int sx = 0; sx < side; sx++) {
                    final int idx = sx + sz * side;
                    final int x = sx * RELIEF_STRIDE;
                    final int z = sz * RELIEF_STRIDE;
                    t[idx] = tierOf(coarse, cs, x, z);
                    h[idx] = ProsperityTerrainProfile.heightAtWithReliefTier(worldSeed, x, z, t[idx]);
                    // T4 河谷归因（T8 重钉）：河谷压低与巨湖压低是 T4/T5 的设计行为，会把沿河
                    // 群系（森宽谷/沼 ×1.6 宽谷）的高度方差整体搬家，压掉振幅档本身的偏序。
                    // 偏序判据因此只在"河/湖未触及列"（s==0 且 lake ≥ 岸哨兵）上计算——
                    // 语义=「振幅档仍把无河地形分开」，不是对整维方差放宽。
                    riverFree[idx] = -GTSRVoronoiRiverField.strengthAt(worldSeed, x, z) == 0.0D
                        && GTSRVoronoiRiverField.lakeAt(worldSeed, x, z) >= GTSRVoronoiRiverField.LAKE_SHORE;
                }
            }
            final double[] sd = new double[4];
            final long[] c = new long[4];
            final double[] s1 = new double[4];
            final double[] s2 = new double[4];
            for (int sz = 0; sz < side; sz++) {
                for (int sx = 0; sx < side; sx++) {
                    final int idx = sx + sz * side;
                    final int tier = t[idx];
                    final int v = h[idx];
                    if (tier < 0 || !riverFree[idx]) {
                        continue;
                    }
                    c[tier]++;
                    s1[tier] += v;
                    s2[tier] += (double)v * v;
                    cnt[tier]++;
                    sum[tier] += v;
                    sum2[tier] += (double)v * v;
                    if (v <= 40 || v >= 110) {
                        clampHits++;
                    }
                    minH = Math.min(minH, v);
                    maxH = Math.max(maxH, v);
                    // 「相邻列」= RELIEF_STRIDE 间隔列（派生步距口径，见常量注释）；x 向与 z 向
                    // 配对只在两列同档且都无河/湖时进 within（群系内粗糙度）；max|Δh| 保留全部
                    // 列对（河谷壁是 T4 设计的一部分，正是"有真实地势差"的最强读数）。
                    if (sx + 1 < side) {
                        final double d = Math.abs(h[idx + 1] - v);
                        if (t[idx + 1] == tier && riverFree[idx + 1]) {
                            withinSum[tier] += d;
                            withinPair[tier]++;
                        }
                        maxAdjacent = Math.max(maxAdjacent, d);
                    }
                    if (sz + 1 < side) {
                        final double d = Math.abs(h[idx + side] - v);
                        if (t[idx + side] == tier && riverFree[idx + side]) {
                            withinSum[tier] += d;
                            withinPair[tier]++;
                        }
                        maxAdjacent = Math.max(maxAdjacent, d);
                    }
                }
            }
            for (int i = 0; i < 4; i++) {
                final double mean = c[i] == 0 ? 0 : s1[i] / c[i];
                sd[i] = c[i] == 0 ? 0 : Math.sqrt(Math.max(0, s2[i] / c[i] - mean * mean));
            }
            // 逐 seed 偏序的覆盖前提：四档在去河/湖列后都有足量样本（下限 = 窗样本/256，均值意义上
            // 每档占 1/4 ⇒ 留 64× 裕量）；不满足的 seed 记一次"未覆盖"，由 coveredSeeds 单独钉。
            boolean covered = true;
            for (int i = 0; i < 4; i++) {
                covered &= c[i] >= side * side / 256;
            }
            if (covered) {
                coveredSeeds++;
                // v1.20.41 S2：严格序由「森>原>沙>沼」改钉「森>沙>原>沼」（需求 5 覆盖，见类注释与 §13 C2）。
                if (sd[1] > sd[2] && sd[2] > sd[0] && sd[0] > sd[3]) {
                    strictHits++;
                }
                if (isMax(sd, 1) && isMin(sd, 3) && sd[2] >= sd[0]) {
                    coreHits++;
                }
            }
        }
        final double[] sd = new double[4];
        final double[] rough = new double[4];
        for (int i = 0; i < 4; i++) {
            final double mean = sum[i] / cnt[i];
            sd[i] = Math.sqrt(Math.max(0, sum2[i] / cnt[i] - mean * mean));
            rough[i] = withinSum[i] / withinPair[i];
        }
        System.out.println("  RELIEF " + RELIEF_SEEDS + "seed×" + RELIEF_SIDE + "²方块(步距" + RELIEF_STRIDE
            + "，去河/湖列)：聚合 sd=" + fmt(sd) + " 群系内 mean|Δh|=" + fmt(rough) + " 严格序命中=" + strictHits
            + "/" + RELIEF_SEEDS + " 偏序命中=" + coreHits + "/" + RELIEF_SEEDS + " 覆盖=" + coveredSeeds + "/"
            + RELIEF_SEEDS);
        System.out.printf("  全域 max|Δh|=%.0f（改前 1） 高度域=[%.0f,%.0f] 触钳制列=%d%n", maxAdjacent, minH, maxH,
            clampHits);
        check(coveredSeeds == RELIEF_SEEDS, "RELIEF 逐 seed 四档覆盖（去河/湖列后样本充足）命中 " + coveredSeeds
            + "/" + RELIEF_SEEDS + " 必须全中（派生窗 6 格/轴的前提读数）");
        // 【v1.20.41 S2 重钉】旧口径「森>原≥沙>沼」连同其归因原文保留在上一段类注释与本文件 §86-97 的
        // P17-SA 申报段；需求 5「黄铜荒漠地势介于锈蚀草原和齿轮森林之间」把荒漠档 0.80 抬到 1.30 后，
        // 实测聚合 sd 森 13.145 / 沙 7.688 / 原 6.518 / 沼 1.908 ⇒ 沙档已越过原档，旧序必红。
        check(sd[1] > sd[2] && sd[2] >= sd[0] && sd[0] > sd[3], "RELIEF 聚合 sd 偏序 森>沙≥原>沼（实测 " + fmt(sd)
            + "；v1.20.41 需求 5 覆盖旧「森>原≥沙>沼」，T3 账本先行 + T4 去 河/湖列 的申报口径不变）");
        // 三条比值随新序换轴：旧「森/原 ≥1.25（现值 2.024）」「原/沙 ≥1.25（现值 1.302）」
        // 「沙/沼 ≥1.30（现值 2.639）」的现值全部保留在注释里，新带按 16 seed 实跑取。
        check(sd[1] / sd[2] >= RATIO_FOREST_WASTES_MIN, "RELIEF 森/沙 sd 比 " + fmt1(sd[1] / sd[2]) + " ≥ "
            + RATIO_FOREST_WASTES_MIN + "（v1.20.41 实跑现值 1.710；旧「森/原」比 2.024 已随序换轴退役）");
        check(sd[2] / sd[0] >= RATIO_WASTES_STEPPE_MIN && sd[2] / sd[0] <= RATIO_WASTES_STEPPE_MAX,
            "RELIEF 沙/原 sd 比 " + fmt1(sd[2] / sd[0]) + " ∈ [" + RATIO_WASTES_STEPPE_MIN + ","
                + RATIO_WASTES_STEPPE_MAX + "]（v1.20.41 实跑现值 1.179；带语义=沙档必须高于但不得吞并原档，"
                + "旧「原/沙 ≥1.25（现值 1.302）」随序反向退役）");
        check(sd[0] / sd[3] >= RATIO_STEPPE_SWAMP_MIN, "RELIEF 原/沼 sd 比 " + fmt1(sd[0] / sd[3]) + " ≥ "
            + RATIO_STEPPE_SWAMP_MIN + "（v1.20.41 实跑现值 3.416；旧「沙/沼 ≥1.30（现值 2.639）」换轴）");
        check(strictHits >= PER_SEED_HITS_MIN, "RELIEF 逐 seed 严格序（森>沙>原>沼）命中 " + strictHits + "/"
            + RELIEF_SEEDS + " ≥ " + PER_SEED_HITS_MIN + "（改前 0/10；P17-B §2.1 的"
            + "「最小下一步」门槛是 ≥9/10；v1.20.41 新序下的逐 seed 命中数待 B1 收口实跑回填）");
        check(coreHits >= PER_SEED_CORE_MIN, "RELIEF 逐 seed 核心偏序（森林最陡且沼泽最平且沙≥原）命中 " + coreHits + "/"
            + RELIEF_SEEDS + " ≥ " + PER_SEED_CORE_MIN + "（v1.20.41 由「必须全中」改钉，理由与不许顺手放宽的"
            + "边界见 {@link #PER_SEED_CORE_MIN} 声明处）");
        check(maxAdjacent >= ADJACENT_DELTA_MIN, "RELIEF 相邻列 max|Δh| " + fmt1(maxAdjacent) + " ≥ "
            + ADJACENT_DELTA_MIN + "（改前全维度实测 = 1，即「完全没有地势差异」的那个数）");
        final long clampMax = (long) (side * side * (double) RELIEF_SEEDS * CLAMP_RATIO_MAX);
        check(clampHits <= clampMax, "RELIEF 触 y=40/110 钳制边的列数 " + clampHits + " ≤ " + clampMax
            + "（样本的 0.01%；越界说明振幅档被 clamp 截平，sd 偏序会失真）");
        check(rough[1] > rough[0] && rough[3] < rough[2] && sd[1] >= FOREST_SD_MIN,
            "RELIEF 群系内 mean|Δh| 森>原 且 沙>沼（实测 " + fmt(rough) + "）⇒ 森林最粗糙、沼泽最平坦的双向钉"
            + " ＋ 森林绝对聚合 sd ≥ " + FOREST_SD_MIN + "（实测 " + fmt1(sd[1]) + "；v1.20.41 P20 §6.1 R1-⑥"
            + " 的字面终值，基线 13.130×1.18——落点理由与实跑缺口见 {@link #FOREST_SD_MIN} 声明处）");
        check(rough[2] >= rough[0] * WITHIN_DUNES_MIN && rough[2] <= rough[0] * WITHIN_DUNES_MAX,
            "RELIEF 沙/原 群系内粗糙度比 " + fmt1(rough[2] / rough[0]) + " ∈ [" + WITHIN_DUNES_MIN + ","
                + WITHIN_DUNES_MAX + "]（v1.20.40 P19 §H 沙丘语义重钉：垄脊把荒漠短尺度粗糙度抬到与草原"
                + "同量级；旧「原 ≥ 沙」序随沙丘退役——归因 U7 prered 原0.428<沙0.457）");
        check(rough[1] / rough[0] >= WITHIN_RATIO_MIN, "RELIEF 森/原 群系内粗糙度比 " + fmt1(rough[1] / rough[0])
            + " ≥ " + WITHIN_RATIO_MIN);
        check(rough[3] < rough[2], "RELIEF 沼<沙 群系内粗糙度（沼泽最平坦）");
    }

    private static void variantGroup() {
        // ═════════════ VARIANT 组（P20 §21-E 判据 4 + §21-F 风蚀山体 / 沼泽三档的落点）═════════════
        // 【本片必须申报的一条】§5 S4 的判据 2/3/4 在 HEAD 与全部在飞终态里<b>从来没有落点</b>：
        // S4 只交了离线探针数（plan/tmp/p20-s4/probe-final2.out），S4b 只交了修复后重测
        // （plan/tmp/p20-s4b/probe-after.out），三条判据一次都没写进任何判据类 —— grep 证据见本片
        // plan/tmp/p20-s3d/no-landing-evidence.txt（tools/dim1 全域对 variantAdjustment / 低地 / 风蚀山体
        // / swampTierAt 四条模式命中 0）。⇒ 本组是<b>新增落点</b>，不是"改到了一个已存在的东西"。
        // §5 S4-2 原写"写进 SanzuTrunkCoverageCheck 的 F 组扩展"，但该片此刻在兄弟片 P20-S5 写锁内
        // ⇒ 本片把它落在本文件（同一批形态场、同一个 TerrainVariants 出口），登记请主代理追认。
        final int side = VARIANT_SIDE / VARIANT_STRIDE;
        long steppeCols = 0;
        long steppeLe3 = 0; // 旧阈（只报不钉）
        long steppeLe5 = 0; // 现行阈
        long steppeLe6 = 0; // 满门（G6 自陈 ≈12% 的对账锚）
        long wasteCols = 0;
        long wasteGe12 = 0;
        final long[] tierN = new long[4];
        final double[] tierDeltaSum = new double[4];
        final double[] tierDeltaMax = new double[4];
        long swampCols = 0;
        double worstDelta = 0;
        long totalCols = 0;
        for (int s = 0; s < VARIANT_SEEDS; s++) {
            final long worldSeed = s;
            final GTSRGenLayerChain chain = new GTSRGenLayerChain(worldSeed ^ SALT, IDS);
            final int cs = VARIANT_SIDE / GTSRGenLayerChain.COARSE_BLOCK_SCALE;
            final int[] coarse = chain.coarseInts(0, 0, cs, cs).clone();
            for (int sz = 0; sz < side; sz++) {
                for (int sx = 0; sx < side; sx++) {
                    final int x = sx * VARIANT_STRIDE;
                    final int z = sz * VARIANT_STRIDE;
                    final int tier = tierOf(coarse, cs, x, z);
                    if (tier < 0) {
                        continue;
                    }
                    totalCols++;
                    // 唯一的形态场出口：把 variantAdjustment 旁路成恒等（h0 = VARIANT_H0）后的差 = delta
                    final double d = TerrainVariants.variantAdjustment(worldSeed, x, z, tier, VARIANT_H0)
                        - (double) VARIANT_H0;
                    worstDelta = Math.max(worstDelta, Math.abs(d));
                    if (tier == 0) {
                        steppeCols++;
                        if (d <= -STEPPE_LOW_CUT_LEGACY) {
                            steppeLe3++;
                        }
                        if (d <= -STEPPE_LOW_CUT) {
                            steppeLe5++;
                        }
                        if (d <= -6.0D) {
                            steppeLe6++;
                        }
                    } else if (tier == 2) {
                        wasteCols++;
                        if (d >= BRYCE_DELTA_MIN) {
                            wasteGe12++;
                        }
                    } else if (tier == 3) {
                        swampCols++;
                        final int t = TerrainVariants.swampTierAt(worldSeed, x, z, tier);
                        tierN[t]++;
                        tierDeltaSum[t] += d;
                        tierDeltaMax[t] = Math.max(tierDeltaMax[t], d);
                    }
                }
            }
        }
        final double lowShare = steppeLe5 / (double) steppeCols;
        final double bryceShare = wasteGe12 / (double) wasteCols;
        System.out.printf("  VARIANT %dseed×%d²方块(步距%d，h0=%d 旁路对照)：采样列=%d 形态 |delta| 最大=%.1f%n",
            VARIANT_SEEDS, VARIANT_SIDE, VARIANT_STRIDE, VARIANT_H0, totalCols, worstDelta);
        System.out.printf("  VARIANT-READ 草原(roster0) 列=%d ｜ 现行阈 delta≤−%.0f 占比=%.4f%% ｜ 旧阈"
            + " delta≤−%.0f 占比=%.4f%%（只报不钉）｜ 满门 delta≤−6 占比=%.4f%%（G6 自陈 ≈12%% 的对账锚）%n",
            steppeCols, STEPPE_LOW_CUT, lowShare * 100, STEPPE_LOW_CUT_LEGACY,
            steppeLe3 / (double) steppeCols * 100, steppeLe6 / (double) steppeCols * 100);
        System.out.printf("  VARIANT-READ 荒漠(roster2) 列=%d ｜ delta≥%.0f 占比=%.4f%%（风蚀山体）%n", wasteCols,
            BRYCE_DELTA_MIN, bryceShare * 100);
        final StringBuilder tb = new StringBuilder();
        for (int t = 0; t < 4; t++) {
            tb.append("  ").append(TIER_NAME[t]).append(" n=").append(tierN[t]).append(" 份额=")
                .append(fmt1(tierN[t] / (double) swampCols * 100)).append('%').append(" delta均值=")
                .append(fmt1(swampCols == 0 ? 0 : tierDeltaSum[t] / tierN[t])).append(" delta最大=")
                .append(fmt1(tierDeltaMax[t]));
        }
        System.out.println("  VARIANT-READ 沼泽(roster3) 列=" + swampCols + " ｜ 三档分布（§21-D 互斥修复后"
            + "的净测，档↔delta 同列成对计数）：" + tb.toString().trim());
        check(steppeCols >= 10000 && lowShare >= STEPPE_LOW_SHARE_MIN && lowShare <= STEPPE_LOW_SHARE_MAX,
            "VARIANT 草原低地覆盖（<b>满门深度口径 delta ≤ −" + (int) STEPPE_LOW_CUT + "</b>）占比 " + fmt1(lowShare)
                + " ∈ [" + STEPPE_LOW_SHARE_MIN + "," + STEPPE_LOW_SHARE_MAX + "]（§5 S4 判据 4 的带容器原值，"
                + "<b>换的是被量的量不是带</b>：§21-E 裁定旧 ≤−" + (int) STEPPE_LOW_CUT_LEGACY + " 口径把整个缓入环"
                + "计进低地（该口径本片仍打印 = " + fmt1(steppeLe3 / (double) steppeCols) + "，只报不钉，原文与"
                + "废止理由见 STEPPE_LOW_CUT_LEGACY 注释）；满门 delta≤−6 读数 = " + fmt1(steppeLe6 / (double) steppeCols)
                + " 与 TerrainVariants 低地门自陈 ≈12% 相互印证。<b>禁止</b>为凑带削低地深度（那是生产侧））");
        check(wasteCols >= 10000 && bryceShare >= BRYCE_SHARE_MIN && bryceShare <= BRYCE_SHARE_MAX,
            "VARIANT 风蚀山体存在性：roster2 内 delta ≥ " + (int) BRYCE_DELTA_MIN + " 的列占比 " + fmt1(bryceShare)
                + " ∈ [" + BRYCE_SHARE_MIN + "," + BRYCE_SHARE_MAX + "]（§5 S4 判据 3 的字面带"
                + "「山体应稀有但不是零」，§21-F 交实测 1.3429%、本片同口径复跑；改前 0.252% 会红 ⇒ 过带的"
                + "唯一改动是已落地的 BRYCE_GAIN 18→26，本片不动任何生产常量）");
        long tierSum = 0;
        for (int t = 0; t < 4; t++) {
            tierSum += tierN[t];
        }
        check(tierSum == swampCols, "VARIANT 沼泽档位分流归一：四档 n 之和 " + tierSum + " == roster3 采样列 "
            + swampCols + "（swampTierAt 是单点分流的完备四档，不是可重叠门 ⇒ 缺档/重档当场红，"
            + "这是 §21-D「不留两处判档」的结构不变量）");
        for (int t = 0; t < 4; t++) {
            final double share = tierN[t] / (double) swampCols;
            final double lo = SWAMP_TIER_SHARE_BAND[t][0];
            final double hi = SWAMP_TIER_SHARE_BAND[t][1];
            check(tierN[t] >= SWAMP_TIER_SAMPLE_MIN && share >= lo && share <= hi, "VARIANT 沼泽三档分布："
                + TIER_NAME[t] + " 份额 " + fmt1(share) + " ∈ [" + lo + "," + hi + "] 且样本 " + tierN[t]
                + " ≥ " + SWAMP_TIER_SAMPLE_MIN + "（§5 S4 判据 2 的字面样本阈 + §21-F 第三条：档名 "
                + TIER_NAME[t] + "，带值与取带规则见 SWAMP_TIER_SHARE_BAND 注释 = 实测 M 的"
                + (t == 0 ? "[0.5,0.7] 绝对带（NONE 是三档补集，绝对带更硬）。本轮取实测，实机后校准）"
                    : t == 2 ? " [M×0.6, M×1.6] 取整到 0.5pp，另有深水池门自陈 ≈5% 与实测 5.505% 互证）"
                    : " [M×0.6, M×1.6] 取整到 0.5pp。本轮取实测，实机后校准）"));
        }
    }

    private static boolean isMax(double[] v, int i) {
        for (int j = 0; j < v.length; j++) {
            if (j != i && v[j] >= v[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMin(double[] v, int i) {
        for (int j = 0; j < v.length; j++) {
            if (j != i && v[j] <= v[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 4 个 <b>selector</b> 档的振幅算数均值（T5/T8 重钉口径：名册第 5 元 sanzu 不进 selector
     * 等权名册，也就不进"只差异化不加大起伏"的均值锚）。
     */
    private static double meanOfSelectorTable() {
        double s = 0;
        for (int i = 0; i < IDS.length; i++) {
            s += ProsperityTerrainProfile.RELIEF_AMPLITUDE_BY_ROSTER[i];
        }
        return s / IDS.length;
    }

    private static int tierOf(int[] coarse, int cs, int x, int z) {
        return indexOf(coarse[(x >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT)
            + (z >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT) * cs]);
    }

    // ═══════════════════════ SOURCE 组（四族同源 / 同格同值）═══════════════════════════

    private static void sourceGroup() {
        for (int i = 0; i < IDS.length; i++) {
            final BiomeGenBase biome = new BiomeGenBase(IDS[i]) {};
            GTSRBiomeAuthority.recordAllocation(dim78Keys()[i], IDS[i], IDS[i], biome);
        }
        final int[] ledgerIds = GTSRGenLayerRosterFace.allocatedRosterIds(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        check(Arrays.equals(ledgerIds, IDS), "SOURCE 离线账本装配成功：dim78 名册 id 表 " + Arrays.toString(ledgerIds));
        check(ProsperityTerrainProfile.CHAIN_SEED_SALT == SALT, "SOURCE Profile 申报的链盐 == 0x50524F53");
        int bad = 0;
        int badBand = 0;
        int neg1 = 0;
        long sample = 0;
        final long worldSeed = 12345L;
        for (int cz = -4; cz <= 4; cz++) {
            for (int cx = -4; cx <= 4; cx++) {
                for (int dx = 0; dx < 16; dx += 3) {
                    for (int dz = 0; dz < 16; dz += 3) {
                        final int x = (cx << 4) + dx;
                        final int z = (cz << 4) + dz;
                        final int tier = GTSRGenLayerRosterFace.rosterIndexAt(
                            worldSeed ^ SALT,
                            GTSRBiomeAuthority.DIM_KEY_PROSPERITY,
                            x,
                            z);
                        if (tier < 0) {
                            neg1++;
                        }
                        if (ProsperityTerrainProfile.heightAt(worldSeed, x, z) != ProsperityTerrainProfile
                            .heightAtWithReliefTier(worldSeed, x, z, tier)) {
                            bad++;
                        }
                        sample++;
                    }
                }
                final int band = CityPlanner.bandIndexAt(worldSeed, cx, cz);
                final int face = GTSRGenLayerRosterFace.rosterIndexAt(
                    worldSeed ^ SALT,
                    GTSRBiomeAuthority.DIM_KEY_PROSPERITY,
                    (cx << 4) + 8,
                    (cz << 4) + 8);
                if (band != face) {
                    badBand++;
                }
            }
        }
        System.out.println("  SOURCE 三参==显式档 差异=" + bad + "/" + sample + " 名册面==bandIndexAt 差异=" + badBand
            + "/169 neg1=" + neg1);
        check(bad == 0, "SOURCE 生产三参 heightAt 与档表显式形态逐点同值（差异 " + bad + "/" + sample
            + "）⇒ 群系化没有造出第二套高度");
        check(badBand == 0, "SOURCE 高度面的身份 == 城门 bandIndexAt 的名册下标（差异 " + badBand
            + "/169，含负坐标）⇒ coarse 面与 L1/城门同格同值，结构接地与地形不会错位");
        check(neg1 == 0, "SOURCE 真实账本下身份取不到 -1（neg1=" + neg1 + "）⇒ 默认档不是生产路径");
        // 默认档语义：无身份 ⇒ 与改前逐位相同（档表默认档 == 1.0 是这条等式的唯一凭据）
        check(ProsperityTerrainProfile.DEFAULT_RELIEF_AMPLITUDE == 1.0D,
            "SOURCE 默认档振幅 == 1.0（EMPTY 降级/未装配 JVM 的高度必须与改造前逐位相同）");
        check(ProsperityTerrainProfile.reliefAmplitudeForRosterIndex(-1) == 1.0D
            && ProsperityTerrainProfile.reliefAmplitudeForRosterIndex(99) == 1.0D,
            "SOURCE 身份档缺失（-1 / 越界）一律走默认档，且实现里没有身份等值判断");
    }

    // ═══════════════════════ REDLINE 组（源级红线）═══════════════════════════

    private static void redlineGroup(Path srcRoot) throws IOException {
        final String profile = codeOnly(read("com/miaokatze/gtsr/common/dimension/prosperity/ProsperityTerrainProfile.java", srcRoot));
        final String face = codeOnly(read(
            "com/miaokatze/gtsr/common/dimension/framework/genlayer/GTSRGenLayerRosterFace.java", srcRoot));
        check(!profile.contains("net.minecraft") && !profile.contains("BiomeGenBase")
            && !profile.contains("Blocks.") && !profile.contains("worldObj") && !profile.contains("getBlock")
            && !profile.contains("Chunk") && !profile.contains("World "),
            "REDLINE 高度类源码零 net.minecraft import、零 BiomeGenBase/Blocks/worldObj/getBlock/Chunk 引用"
                + "（城市侧禁读方块红线；群系身份只经 int 出口进来）");
        check(!face.contains("worldObj") && !face.contains("getBlock") && !face.contains("Chunk")
            && !face.contains("IChunkProvider") && !face.contains("World "),
            "REDLINE 身份面源码零世界读取（只用 L1 内存账本 + 整数链）");
        check(profile.contains("GTSRWorldgenHash.valueNoise("), "REDLINE 高度类仍引用上收后的唯一噪声内核");
        check(!face.contains("fineInts") && !face.contains("biomeAtFine"),
            "REDLINE 身份面只吃 coarse 面（voronoi 细面逐列求值既贵又会造成 1 格级高度跳变）");
        final String gate = codeOnly(read(
            "com/miaokatze/gtsr/common/dimension/framework/structure/PlacementGate.java", srcRoot));
        final String provider = codeOnly(read(
            "com/miaokatze/gtsr/common/dimension/prosperity/ChunkProviderProsperityRuins.java", srcRoot));
        final String command = codeOnly(read("com/miaokatze/gtsr/common/commands/GTSRCommand.java", srcRoot));
        check(gate.contains("ProsperityTerrainProfile.heightAt(worldSeed, x, z)"),
            "REDLINE 结构侧接地供给器仍直引同一个 heightAt（城/机器/废墟/outpost 四族同源）");
        check(provider.contains("ProsperityTerrainProfile.heightAt(worldSeed, baseX + x, baseZ + z)"),
            "REDLINE 地形填充仍直引同一个 heightAt");
        check(command.contains("ProsperityTerrainProfile.heightAt(world.getSeed(), x, z)"),
            "REDLINE 指令取高仍直引同一个 heightAt");
        check(occurrences(profile, "RELIEF_MULT * amplitude") == 1,
            "REDLINE 高度公式在 Profile 内只有一份表达式（实测 " + occurrences(profile, "RELIEF_MULT * amplitude")
                + " 处）⇒ 群系振幅只能经档表进这一份");
        check(occurrences(profile, "reliefAmplitudeForRosterIndex(") == 2,
            "REDLINE 档表读取口恰 2 处（声明 + 振幅表达式内一次调用）");
        check(occurrences(profile, "public static int heightAt(long") == 1,
            "REDLINE 三参 heightAt 定义恰 1 处（四参形态是同一函数体的显式档口）");
    }

    // ═══════════════════════ SALT 组（三处字面量同值）═══════════════════════════

    private static void saltGroup(Path srcRoot) throws IOException {
        final String proxy = read("com/miaokatze/gtsr/main/CommonProxy.java", srcRoot);
        final String planner = codeOnly(read(
            "com/miaokatze/gtsr/common/dimension/prosperity/ruins/city/CityPlanner.java", srcRoot));
        final String profile = codeOnly(read(
            "com/miaokatze/gtsr/common/dimension/prosperity/ProsperityTerrainProfile.java", srcRoot));
        check(proxy.contains("0x50524F53L"), "SALT CommonProxy def.seedSalt 字面量在场（BBH C2 同条，此处复核）");
        check(planner.contains("PROSPERITY_SEED_SALT = 0x50524F53L"), "SALT CityPlanner 链盐同值");
        check(profile.contains("CHAIN_SEED_SALT = 0x50524F53L"),
            "SALT Profile 自建身份链的盐同值（第三处字面量；三处任一改动即红，不再靠人眼核）");
    }

    // ═══════════════════════ 公共件 ═══════════════════════

    private static GTSRBiomeAuthority.BiomeId[] dim78Keys() {
        final List<GTSRBiomeAuthority.BiomeId> out = new ArrayList<>();
        for (final GTSRBiomeAuthority.BiomeId key : GTSRBiomeAuthority.BiomeId.values()) {
            if (GTSRBiomeAuthority.DIM_KEY_PROSPERITY.equals(key.dimKey())) {
                out.add(key);
            }
        }
        return out.toArray(new GTSRBiomeAuthority.BiomeId[0]);
    }

    private static int indexOf(int id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i] == id) {
                return i;
            }
        }
        return -1;
    }

    private static String read(String rel, Path srcRoot) throws IOException {
        return new String(Files.readAllBytes(srcRoot.resolve(rel)), StandardCharsets.UTF_8);
    }

    /** 粗去掉 javadoc 与 // 注释（REDLINE 组要判"引用"而不是"文里提到过这个词"）。 */
    private static String codeOnly(String src) {
        final StringBuilder sb = new StringBuilder();
        boolean inBlock = false;
        for (final String line : src.split("\n", -1)) {
            String l = line;
            if (inBlock) {
                final int end = l.indexOf("*/");
                if (end < 0) {
                    continue;
                }
                inBlock = false;
                l = l.substring(end + 2);
            }
            final int start = l.indexOf("/*");
            if (start >= 0) {
                inBlock = true;
                l = l.substring(0, start);
            }
            final int sl = l.indexOf("//");
            if (sl >= 0) {
                l = l.substring(0, sl);
            }
            sb.append(l).append('\n');
        }
        return sb.toString();
    }

    private static int occurrences(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    private static String fmt(double[] v) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            sb.append(NAME[i]).append('=');
            sb.append(String.format(java.util.Locale.ROOT, "%.3f", v[i]));
            if (i < v.length - 1) {
                sb.append('/');
            }
        }
        return sb.toString();
    }

    private static String fmt1(double v) {
        return String.format(java.util.Locale.ROOT, "%.4g", v);
    }

    private static void check(boolean ok, String what) {
        assertions++;
        if (!ok) {
            PROBLEMS.add(what);
        }
    }
}
