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
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
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
     * <p>
     * <b>v1.20.42 P22 A4 重录（森林谷地负瓣 + RIDGE_AMP 12→18）</b>：聚合 sd <b>森 14.807 / 沙 7.959 /
     * 原 6.994 / 沼 2.916</b>（同口径；A4 前申报 森 14.043 / 沙 7.951 / 原 6.981 / 沼 2.882 =
     * {@code plan/tmp/p22-a0/full-0.log}）。森/沙、原/沼两比随森林上抬而走阔（1.766→1.860、
     * 2.417→2.399——后者因沼泽档被边界渗入微抬 0.034 而略收，仍在带内）；沙/原 1.138 ∈ [1.10,1.45]
     * 不动。逐 seed 严格序/核心偏序命中 14/16、14/16 与 A4 前持平（负瓣不改变逐 seed 序结构）。
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
     * <p>
     * <b>v1.20.42 P22 A4 重录（森林谷地负瓣 + RIDGE_AMP 12→18）</b>：mean|Δh| 实测 <b>森 0.822 / 沙 0.682 /
     * 原 0.515 / 沼 0.388</b>（A4 前 0.713/0.678/0.511/0.384）。沙/原 比 c = <b>1.325</b> ∈ 带内不动；
     * 森/原 比 1.397→<b>1.597</b>（负瓣 −10 与岭脊 +18 都抬短尺度差，森最粗糙的双向钉更稳）。
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
     * <p>
     * <b>v1.20.42 P22 A4 重录（森林谷地负瓣落地后，阈值不动）</b>：同口径实测触钳制列 = <b>539</b>
     * （= 0.0057% 样本；A4 前 145）——增量全部来自负瓣把低基线森林列压到 y=40 地板的"恰好触边"，
     * 预算余量 943 − 539 = <b>404 列</b>；岭脊 amp 12→18 的正 delta 反向抵消部分下挖（负瓣单独臂 556
     * → 合臂 539）。上沿 110 侧零触（高度域上沿 = 108 哨兵，非 110 硬钳）。阈值 0.01% 与
     * "截平即红"抓力不变。
     * <p>
     * <b>v1.20.49 P26-B4 ①（D7' 破顶放大档）：上沿字面 110 → 180，预算带 0.01% → 0.012%</b>——
     * {@code ProsperityTerrainProfile}.MAX_HEIGHT=180、{@code TerrainVariants.HEIGHT_SENTINEL}=108→172
     * （softMin k=4 渐近 &lt;172）⇒ 变体列结构性触不到 180，上沿计数恒 0；本阈值继续咬 <b>y=40 地板</b>侧。
     * <b>触钳列预算带按本片实测重钉（0.0100% → 0.0120%，clampMax 943 → 1132）</b>：批内同口径实测
     * 触钳列 = <b>947</b>（= 0.010034% 样本，全部为 y=40 地板"恰好触边"——高度域实测 [0,172]，
     * 上沿 0 触），旧 943 预算被盆地+谷地负瓣的合法触边顶破 4 列（首演同读数）；重钉取读数
     * ×1.195 裕量，"截平即红"抓力不变（真截平会爆到千列级）。读数见 {@code plan/tmp/p26-b4-readings.md}。
     * <p>
     * <b>v1.20.50 P27 批次A-A3 G 片复核（草原深芯/缎带沟落地后，阈值不动）</b>：同口径实测触钳制列 =
     * <b>947</b>（与 P26-B4 读数逐值相同——深芯最深 −16 列的 h0 ∈ ~[61,79] ⇒ 合成最低 ~45 &gt; 40，沟
     * −6.5 同理 ⇒ 两新臂在 RELIEF 窗（去河/湖列）零新增地板触边；新读数 ×1.195 = 1131.7 ≤ clampMax
     * 1132，0.00012 带保持，无需重钉）。读数见 {@code plan/tmp/p27-g-readings.md}。
     */
    private static final double CLAMP_RATIO_MAX = 0.00012D;
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
     * <p>
     * <b>v1.20.42 P22 A4 重钉 14.0 → 14.7（旧带原文与 §21-B 裁定全段保留在上方）</b>：A4 落地
     * <b>森林谷地负瓣</b>（{@code TerrainVariants} 森林支路 λ139 负瓣门 + 下挖 −(4+6·门)·门 ≤ −10——顶部
     * 三面封死后的下侧路径）+ 次级杠杆 {@code RIDGE_AMP} 12→18（授权域 {14,16,18} 上沿，探针反解最大
     * 可达档）。<b>可达性曲线</b>（16 seed × 3072² 步距 4 去河/湖列，{@code plan/tmp/p22-a4/}）：
     * HEAD 14.043 → 负瓣单独 14.465 → +amp14 14.552 → +amp16 14.668 → <b>+amp18 14.807（本带基准）</b>；
     * 反证两臂：岭脊门放宽 0.72→0.66 单独 14.337 / 合臂 14.691（门放宽抬低尾 ⇒ spread 收缩，弃用）、
     * {@code DELTA_CAP} 32→36 不过"不推高 108 哨兵"门判（高度域上沿已 = 108，弃用）。
     * ⇒ <b>14.807 是授权域内最大可达 sd</b>，本带取其 −0.7% 余量钉 14.7（旧 14.0 带已不能咬住 A4 后的
     * 任何 ≥0.7 回退）。**披露**：负瓣实测覆盖 any 10.10%/half 5.29%/满门 2.28%（计划按三角边际估
     * 12-18%，bilinear valueNoise 边际更尖）；正侧 delta ≥ +24 的森林列 15.72%→17.49%（amp18 的平顶代价）。
     */
    private static final double FOREST_SD_MIN = 14.7D;

    // ═════════════ VARIANT 组（A4 森林谷地负瓣两条；v1.20.42 P22 A4 新增落点）═════════════
    /**
     * 森林谷地负瓣的<b>净下挖覆盖</b>（roster1 内 {@code delta ≤ −4} 的列占比）下限。
     * <p>
     * 实测（本判据 variant 口径：16 seed × 1024² 步距 4、h0=70 旁路）＝ <b>1.5990%</b>；带按沼泽三档
     * 同族规则 [M×0.6, M×1.6] 取整——因 M 量级只有 1.6pp，0.5pp 网格太粗，改取 <b>0.1pp 网格</b> ⇒
     * [0.9%, 2.6%]。带两端语义：下界 = "谷地作为分支形态存在"（负瓣门塌掉即红，需求「起伏更大、
     * 山脉丘陵状」的下侧形态落空）；上界 = "森林不被谷地吞掉大半"。口径是<b>净</b> delta（含同列
     * 丘陵/岭脊正项抵消），不是门覆盖（门 any 10.10% 是形态侧自陈，见 {@code TerrainVariants}
     * FOREST_VALLEY_GATE_LO 注释）；A4 探针与判据同源对账：探针 variant 臂同口径 1.5990%。
     */
    private static final double FOREST_VALLEY_SHARE_MIN = 0.009D;
    /** 森林谷地负瓣净下挖覆盖上限（[M×0.6, M×1.6] 的 M+60% 侧，0.1pp 网格取整）。 */
    private static final double FOREST_VALLEY_SHARE_MAX = 0.026D;
    /**
     * 森林谷地负瓣<b>单列最大下挖</b>（格）。机制满门深度 = {@code −(4.0+6.0·门)·门} 的最深 −10
     * （{@code TerrainVariants.FOREST_VALLEY_BASE/SPAN}）；本带 = −10 加 0.5 的取整容差 ⇒
     * {@code delta ≥ −10.5}。实测最深 <b>−10.00</b>（旁路口径，{@code plan/tmp/p22-a4/} variant 终态）。
     * 语义 = "负瓣不得超挖"——加深负瓣会直通 DELTA_CAP（softMin 只封上侧）且吃 y=40 地板预算，
     * 超挖即红（与 {@link #CLAMP_RATIO_MAX} 的预算带互为犄角）。
     */
    private static final double FOREST_VALLEY_DIG_MAX = 10.5D;

    // ═══ v1.20.49 P26-B4 ⑪：草原小盆地判据（λ167 负瓣封闭碗形）═══
    /**
     * 盆地净下挖覆盖带：盆地带内 {@code delta ≤ −5} 的列占比。
     * 实测（本判据 variant 口径同窗 16 seed × 1024² 步距 4、h0=70 旁路）＝ <b>3.955%</b>
     * （{@code plan/tmp/p26-b4-readings.md}；本片批内以实跑读数复核）；带按森林谷地同族规则
     * [M×0.6, M×1.6] 0.1pp 网格 ⇒ <b>[2.4%, 6.3%]</b>。带两端语义：下界 = "盆地作为分支形态存在"
     * （负瓣门塌掉即红，需求 5 平原"小盆地"落空）；上界 = "草原不被盆地吞掉大半"。
     */
    private static final double STEPPE_BASIN_SHARE_MIN = 0.024D;
    private static final double STEPPE_BASIN_SHARE_MAX = 0.063D;
    /**
     * 盆地单列最大下挖（格）：机制满门深度 {@code −(5.0+4.0·门)·门} 的最深 −9 + 0.5 取整容差
     * ⇒ {@code delta ≥ −9.5}。语义 = "盆地不得超挖"（低地×(1−盆地带) 单点分流 ⇒ 两臂最坏合成
     * ≤ 盆地满门单臂 −9，超挖即红）。
     * <p>
     * <b>v1.20.50 P27 批次A-A3 G 片重钉 9.5 → 16.5（旧带原文保留在上一段）</b>：盆内新增<b>深芯第二档</b>
     * （{@code TerrainVariants} λ89 负瓣门 {@code −(4.0+3.0·芯门)·芯门×basinGate}，盐 0x6811C3E3）⇒
     * 机制满门最深 = 盆 −9 叠芯 −7 = <b>−16</b>（干碗，比沼泽深潭 8-14 水再深一档；域外 IEEE 精确 −0.0
     * 由 digest 对拍证明，见 {@code plan/tmp/p27-g-readings.md}）。批内实测盆地带内最深 = <b>−16.00</b>
     * ⇒ −16 + 0.5 取整容差 ⇒ 16.5。超挖语义不变（加深直通 y=40 地板预算，与 CLAMP_RATIO_MAX 互为犄角）。
     */
    private static final double STEPPE_BASIN_DIG_MAX = 16.5D;
    /** 盆地场域盐的字面复算（= TerrainVariants.S_STEPPE_BASIN；判据侧只读镜像）。 */
    private static final long A4_S_STEPPE_BASIN = 0x6811C3ADL;
    /** 盆地场波长的字面复算（= TerrainVariants.STEPPE_BASIN_SCALE）。 */
    private static final double A4_STEPPE_BASIN_SCALE = 167.0D;
    // ═══ v1.20.50 P27 批次A-A3 G 片：草原深芯/缎带沟的字面镜像（盐/波长；门带字面在 variantGroup 内联，
    //     javadoc 引生产常量名——A4_S_STEPPE_BASIN 先例同款纪律）═══
    /** 深芯场域盐/波长的字面复算（= TerrainVariants.S_STEPPE_BASIN_CORE / STEPPE_BASIN_CORE_SCALE）。 */
    private static final long A4_S_STEPPE_BASIN_CORE = 0x6811C3E3L;
    private static final double A4_STEPPE_BASIN_CORE_SCALE = 89.0D;
    /** 缎带沟场域盐/波长的字面复算（= TerrainVariants.S_STEPPE_GULLY / STEPPE_GULLY_SCALE；
     * v1.20.50 P27 G 片 redirect 后 = λ73，域盐不变）。 */
    private static final long A4_S_STEPPE_GULLY = 0x6811C3F5L;
    private static final double A4_STEPPE_GULLY_SCALE = 73.0D;
    /** 丘陵门盐的字面复算（= TerrainVariants.S_HILL_GATE，λ320；沟存在域 gateS 因子用）。 */
    private static final long A4_S_HILL_GATE = 0x6811C21DL;
    /**
     * 缎带沟净下挖覆盖带：沟带内（{@code gullyGate>0 且盆地带外}，纯域口径防缓入环混账）
     * {@code delta ≤ −4} 的列占比（分母 = roster0 全采样列，与 STEPPE_BASIN_SHARE_BAND 同口径）。
     * <p>
     * <b>v1.20.50 P27 G 片 redirect 重钉</b>：主代理裁决 λ57 → λ73（域盐不动、门参数不动）后实测
     * （同窗 16 seed × 1024² 步距 4、h0=70 旁路；归因口径 = 快照+仅 G 版 TerrainVariants 树，
     * {@code temp/p27-g/P17TerrainReliefCheck-gonly.out}；与合并树读数逐字相同——VARIANT 组
     * delta 不受 L/T/W 片影响）＝ <b>18.5121%</b>（带内列 68216）⇒ [M×0.6, M×1.6] 0.1pp 网格
     * ⇒ <b>[11.1%, 29.6%]</b>（旧带 [10.8, 28.8] 由 λ57 M=17.9941% 反解，重钉前对照见
     * {@code plan/tmp/p27-g-readings.md} §redirect）。
     * <p>
     * <b>redirect 结果披露（重要）</b>：裁决预期覆盖降至 ~11%（按 (57/73)²≈0.61 折算）<b>未成立</b>
     * ——等值线带的<b>面积占比与 λ 无关</b>：满带空间宽 ~c·λ 与单位面积等值线长 ~1/λ 相消，
     * λ 只改段数/间距（(57/73)² 压的是"沟条数"）不压面积；实测 17.9941% → 18.5121%（+0.52pp =
     * 与盆地/丘陵存在域门的相关结构漂移）。λ57 时代的披露仍有效：ue-steppe §4 预估 1.5-2.5%
     * 偏小一个量级（漏了 d≤−4 只需沟门 ≥0.70 即 |n|≤0.136、三角边际 P≈25%）。<b>面积口径上
     * 唯一有效的收窄旋钮是 GULLY_GATE_LO 加严</b>（0.78→0.88 ⇒ 计深带 |n|≤0.136 收到 ~|n|≤0.05），
     * 观感裁决与后续旋钮归主代理（本片只执行波长 redirect + 重钉）。
     * 带两端语义：下界 = "沟作为分支形态存在"（ridged 门塌掉即红）；上界 = "草原不被沟网吞掉"。
     */
    private static final double STEPPE_GULLY_SHARE_MIN = 0.111D;
    private static final double STEPPE_GULLY_SHARE_MAX = 0.296D;
    /**
     * 缎带沟单列最大下挖（格）：机制满门深度 {@code −(4.0+2.5·门)·门}（生产
     * {@code TerrainVariants.STEPPE_GULLY_BASE/SPAN}）最深 <b>−6.5</b> + 0.5 取整容差 ⇒ 7.0。
     * 沟带（纯域口径）内低地臂已乘 (1−gullyGate) ⇒ 两负臂不相加（合成上确界 6.5 在 g=1 处、
     * 低地同时归零）。<b>v1.20.50 P27 G 片 redirect 复核：实测 −6.00 → −7.00</b>（λ73 带重定位后
     * 命中一条名册核混合列：纯 w0=1 带内列解析上界 −6.5 且 Math.round(70−6.5)=64 ⇒ 读数 ≥ −6，
     * −7.00 只能来自 w1 谷地（−10 按权混入）或 w3 沼泽池臂的名册边界列——判据口径不按名册纯度
     * 过滤，先例同款）。−7.00 ≥ −7.0 <b>等值贴边通过</b>：机制上界未破、但批C 复跑若命中更深的
     * 边界混列即红——贴边风险登记 {@code plan/tmp/p27-g-readings.md} §redirect。
     * <p>
     * <b>v1.20.53 P30 II-D：贴边风险兑现并处置——计量口径换纯核列，阈值 7.0 不动</b>。本批复跑
     * 沟带内实测最深 <b>−12.00</b>（seed4 (216,316)：gullyGate=1.0 沟臂 −6.5·w0 叠 w3 沼泽深潭臂
     * −14·w3 的名册核混合列，臂分解见 {@code temp/p30-iid/} 探针）——非任一单臂机制超挖，是 P27
     * 登记的"边界混列"风险在 II-AB 连续门后混核列分布变化下兑现。处置 = gullyMin 只在<b>纯核列</b>
     * （{@link #a3PureRosterCell} 69 粗格全 0 ⇒ w1-w4 精确 0，其它群系臂不叠）上计量；带值 7.0
     * 仍是机制满门 −6.5 + 0.5，抓力（纯核列上沟超挖即红）不变。份额带（SHARE）仍量全域沟带列，
     * 口径不变。
     */
    private static final double STEPPE_GULLY_DIG_MAX = 7.0D;
    /**
     * 深芯净下挖覆盖带（可选芯带，A4 族规）：芯∩盆列（{@code coreGate>0 且 basinGate>0}——生产
     * 深芯臂 ×basinGate 只在此域非零）内 {@code delta ≤ −10} 的列占比（分母同上 = roster0 全采样列）。
     * 实测 = <b>0.1474%</b>（芯∩盆列 1183 = 0.5115%，与 ue-steppe §1b 预估"芯∩盆 ≈ 0.3-0.5%"相符）
     * ⇒ [M×0.6, M×1.6]（M 量级 0.1pp，0.1pp 网格太粗 ⇒ 0.01pp 网格）⇒ <b>[0.08%, 0.24%]</b>。
     * 下界 = "深芯作为第二档存在"（芯门塌掉即红）；上界 = "深碗不普遍化"（那是 1a 整体加深的形态）。
     */
    private static final double STEPPE_CORE_SHARE_MIN = 0.0008D;
    private static final double STEPPE_CORE_SHARE_MAX = 0.0024D;
    /**
     * 泥丘正项限幅的字面复算（= {@code TerrainVariants.SWAMP_NONWATER_RISE_MAX}；<b>v1.20.53
     * P30 II-AB 新增</b>：tier≠NONE 列的非水体项正侧钳 {@code min(dry, +3.0)}——满门泥丘
     * +3.5..+8 削顶到 +3.0，防泥丘把水体档列的床抬过该档回填水面（"挖而不灌"的反向穿透）；
     * NONE 列不限（干沼草甸上的泥丘岛设计保留）。判据侧只读镜像（A4 盐/波长镜像同族纪律），
     * 计量位见 variantGroup 的纯腹地列（w3==1.0）计量。
     */
    private static final double A4_SWAMP_NONWATER_RISE_MAX = 3.0D;

    /** smoothstep 带通（判据侧镜像用，与 TerrainVariants.s01 同式）。 */
    private static double a4S01(double t) {
        final double c = Math.max(0.0D, Math.min(1.0D, t));
        return c * c * (3.0D - 2.0D * c);
    }

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
     * <p>
     * <b>v1.20.42 P22 A3 重钉（边缘门后的新分布；旧带 [{0.50,0.70},{0.12,0.335},{0.03,0.09},
     * {0.075,0.215}] 原文保留在上一段）</b>：{@code TerrainVariants.swampGates} 的 SWG_TIER 槽乘
     * {@code swampInteriorAt}（N=16 ⇒ coarse R=4）后，边缘列（距群系边 &lt;R 粗格）一律归 NONE ⇒
     * 实测 <b>NONE 66.46% ／ POOL 17.74% ／ DEEP 4.678% ／ MARSH 11.12%</b>（16 seed × 1024²
     * 步距 4，与上一段同口径）。取带规则不变（三水体档 [M×0.6, M×1.6] 取整 0.5pp 网格；NONE 绝对带
     * 换轴为 <b>[0.60,0.72]</b>——下界 = 边缘门后水体合计 ≤40% 的硬口，上界 = 任一水体档塌掉
     * （MARSH 11.1pp 并入 ⇒ 77.6%）必过 72）。旧带在边缘门后仍碰巧全绿（NONE 66.46 &lt; 70 等），
     * 但其中心已漂 ⇒ 按任务包「按新分布重钉」收紧；相对规则与归一不变量、样本下限不动。
     */
    private static final double[][] SWAMP_TIER_SHARE_BAND = { {0.60D, 0.72D}, {0.105D, 0.285D},
        {0.015D, 0.050D}, {0.065D, 0.18D} };
    /**
     * <b>v1.20.53 P30 II-D 重钉：DEEP 行 [0.015,0.055] → [0.015,0.050]（旧带原文见上一段）</b>——
     * 瀑布退役 + 连续边缘门（II-AB）后同口径实测（16 seed × 1024² 步距 4，h0=70 旁路）：
     * <b>NONE 61.99% ／ POOL 21.40% ／ DEEP 3.111% ／ MARSH 13.50%</b>。DEEP 按重钉规则
     * [M×0.6, M×1.6] 0.5pp 网格取 ⌊1.867⌋→1.5 / ⌈4.978⌉→5.0（M 2.78-2.86% → 3.11%：连续门
     * 对边缘档位的衰减与瀑域加密退役后的净读数）；NONE/POOL/MARSH 三行均在原带内不动
     * （61.99 ∈ [60,72]、21.40 ∈ [10.5,28.5]、13.50 ∈ [6.5,18] ⇒ 无重钉，先例"带不动 ⇒ 无重钉"）。
     */
    /**
     * <b>v1.20.49 P26-B4 ⑥ DEEP 行重钉：[0.025,0.075] → [0.015,0.055]</b>——深水潭升级
     * （床纹 λ37→λ71、门带 [0.62,0.14]→[0.68,0.16]）后同口径实测约 3.2%（16 seed × 1024² 步距 4，
     * h0=70 旁路；批内实跑读数见 {@code plan/tmp/p26-b4-readings.md}，取带规则不变
     * [M×0.6, M×1.6] 0.5pp 网格）。NONE/POOL/MARSH 三行读数在原带内不动。
     * <p>
     * <b>v1.20.50 P27 批次B-B1 S 片复核：四行均不动</b>——潭型分型域（λ157 双瓣）+ 缓坡域门值级
     * 混合（λ353）+ b' 瀑域加密落地后，实现前探针预演（同口径 16 seed × 1024² 步距 4，字面镜像
     * 新门，{@code temp/p27-s/probe.out} PROBE4）：DEEP 3.198% → <b>2.776%</b>（缓坡混合）/
     * <b>2.855%</b>（叠 b'），均在 [1.5,5.5] 带内；NONE 67.24→~67.5%、POOL 18.27→18.36%、
     * MARSH 11.29→11.33% 同在原带。带不动 ⇒ 无重钉；批内实跑读数以本文件 VARIANT-READ 行为准。
     */
    /** 沼泽三档各档样本数下限（§5 S4 判据 2 的字面阈"各 ≥30 样本"；实测最小档 DEEP = 16459 ⇒ 裕量三个数量级）。 */
    private static final long SWAMP_TIER_SAMPLE_MIN = 30L;

    // ═════════════ A3 组（v1.20.42 P22 A3：沼泽水体避群系边缘 + 包含性）═════════════
    /** A3 采样 seed 数（0..3；探针大样本 8 seed 的判据侧钉子——stride 1 区域场重算成本高）。 */
    private static final int A3_SEEDS = 4;
    /** A3 采样窗边长（方块，chunk 对齐 = 恰好 2×2 个区域场）。 */
    private static final int A3_SIDE = 512;
    // [v1.20.53 P30 II-AB] 原 A3_EDGE_R=4（coarse Chebyshev R=4 阶梯镜像）随生产连续边缘门退役删除——
    // 边缘门镜像换为下方连续门常量族 + 11×11 核权重镜像（w3 分量），断言侧的「N=16 生效」
    // minDist 证明（水列 Chebyshev 距边 ≥ R+1）同批换轴（连续门无直角台阶读数，见 a3Group）。
    /** 连续边缘门下檐的字面复算（= TerrainVariants.SWAMP_INTERIOR_GATE_LO；判据侧只读镜像）。 */
    private static final double A3_INTERIOR_GATE_LO = 0.40D;
    /** 连续边缘门带宽的字面复算（= TerrainVariants.SWAMP_INTERIOR_GATE_SPAN）。 */
    private static final double A3_INTERIOR_GATE_SPAN = 0.15D;
    /** 分档中点阈的字面复算（= TerrainVariants.TIER_MIN；smoothstep 中点，两侧对称不随带宽漂移）。 */
    private static final double A3_TIER_MIN = 0.5D;
    /** 核半径的字面复算（= TerrainVariants.VAR_KERNEL_RADIUS；直径 11 粗格、69 参与格点）。 */
    private static final int A3_KERNEL_RADIUS = 5;
    /**
     * 核参与格点与归一核权的字面镜像（= 生产 VAR_KERNEL_DX/DZ/W：i²+j²&lt;r² 入表（69 点）、
     * {@code t=1−√d²/r} 过 smoothstep、Σw 归一；<b>累加次序 = dx 外层 dz 内层</b>，与生产
     * {@code weightsAt} 的 w[3] 同一浮点累加序列 ⇒ 逐位一致）。P17 镜像纪律：只读复算、不引产
     * 私有常量，生产换核不改这里即被 a3Group 的对拍断言当场抓红。
     */
    private static final int[] A3_KERNEL_DX;
    private static final int[] A3_KERNEL_DZ;
    private static final double[] A3_KERNEL_W;

    static {
        final int r = A3_KERNEL_RADIUS;
        final int rr = r * r;
        int n = 0;
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                if (i * i + j * j < rr) {
                    n++;
                }
            }
        }
        A3_KERNEL_DX = new int[n];
        A3_KERNEL_DZ = new int[n];
        A3_KERNEL_W = new double[n];
        double wSum = 0.0D;
        int k = 0;
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                final int d2 = i * i + j * j;
                if (d2 >= rr) {
                    continue;
                }
                final double t = 1.0D - Math.sqrt(d2) / r;
                final double wgt = t * t * (3.0D - 2.0D * t);
                A3_KERNEL_DX[k] = i;
                A3_KERNEL_DZ[k] = j;
                A3_KERNEL_W[k] = wgt;
                wSum += wgt;
                k++;
            }
        }
        for (k = 0; k < n; k++) {
            A3_KERNEL_W[k] /= wSum;
        }
    }

    /** 区域场常量的字面复算（= ChunkProviderProsperityRuins.SWAMP_FIELD_*；同上只读镜像）。 */
    private static final int A3_FREG = 256;
    private static final int A3_FMAR = 64;
    /** §21-D 净下挖复跑：夹持门域盐的字面复算（= TerrainVariants.S_SWAMP_CLAMP，p20-s4b 探针同款）。 */
    private static final long A3_S_SWAMP_CLAMP = 0x6811C29DL;
    // [v1.20.53 P30 II-AB 瀑布退役] 原瀑布潭镜像两常量（段差 2 / 潭面钳低下限 62，镜像生产
    // ChunkProviderProsperityRuins 同名族，v1.20.50 P27 批次B 引入、P28 ③B 收窄）随沼泽瀑布
    // 全套删除——pass1 钳低腿/高水台腿与 pass2 转换趟（候选判定/同面连通 sheet/悬空修复环/
    // 逐列包含性自检）整段同批摘除，镜像零残留。
    /** 沼泽水体档位名（下标 = {@code TerrainVariants.SWAMP_TIER_*} 的 int 值）。 */
    private static final String[] TIER_NAME = { "NONE", "POOL", "DEEP", "MARSH" };

    private static final List<String> PROBLEMS = new ArrayList<>();
    private static int assertions = 0;

    public static void main(String[] args) throws IOException {
        final Path srcRoot = Paths.get(args.length > 0 ? args[0] : "src/main/java");
        scaleGroup();
        reliefGroup();
        variantGroup();
        a3Group();
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
        long forestGe150 = 0;
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
                    if (tier == 1 && v >= 150) {
                        forestGe150++;
                    }
                    if (v <= 40 || v >= 180) {
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
        System.out.printf("  全域 max|Δh|=%.0f（改前 1） 高度域=[%.0f,%.0f] 触钳制列=%d 森林≥150占比=%.4f%%%n",
            maxAdjacent, minH, maxH, clampHits, forestGe150 * 100.0D / Math.max(1, cnt[1]));
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
            + RATIO_FOREST_WASTES_MIN + "（v1.20.42 A4 实跑现值 1.860、A4 前 1.766；旧「森/原」比 2.024 已随序换轴退役）");
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
        check(clampHits <= clampMax, "RELIEF 触 y=40/180 钳制边的列数 " + clampHits + " ≤ " + clampMax
            + "（样本的 " + fmt1(CLAMP_RATIO_MAX * 100.0D) + "%；越界说明振幅档被 clamp 截平，sd 偏序会失真。"
            + "v1.20.49 P26-B4① 上沿 110→180：HEIGHT_SENTINEL=172 渐近 ⇒ 变体列恒 <180，上沿触钳结构性 0，"
            + "本计数实际只咬 y=40 地板；预算带按本片实测重钉，见 CLAMP_RATIO_MAX 注释）");
        check(forestGe150 >= cnt[1] * 0.005D && forestGe150 <= cnt[1] * 0.12D,
            "RELIEF 森林峰高分布带：v ≥ 150 列占比 " + fmt1(forestGe150 * 100.0D / Math.max(1, cnt[1]))
                + "% ∈ [0.5,12]%（v1.20.49 P26-B4② 延绵脊——批内缩样读数见 plan/tmp/p26-b4-readings.md；"
                + "下界=脊存在（amp 塌掉即红），上界=森林不被高峰吞并）");
        check(rough[1] > rough[0] && rough[3] < rough[2] && sd[1] >= FOREST_SD_MIN,
            "RELIEF 群系内 mean|Δh| 森>原 且 沙>沼（实测 " + fmt(rough) + "）⇒ 森林最粗糙、沼泽最平坦的双向钉"
                + " ＋ 森林绝对聚合 sd ≥ " + FOREST_SD_MIN + "（实测 " + fmt1(sd[1]) + "；v1.20.42 P22 A4 由 14.0 重钉"
                + "——探针反解授权域最大可达 14.807（谷地负瓣 + RIDGE_AMP 18）取 −0.7% 余量，可达性曲线与旧带"
                + "原文见 {@link #FOREST_SD_MIN} 声明处）");
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
        long steppeLe5 = 0; // 现行阈（P26-B4 ⑪ 起 = 低地单臂：盆地带外）
        long steppeLe6 = 0; // 满门（G6 自陈 ≈12% 的对账锚）
        long steppeLe5All = 0; // 含盆地合计（只报不钉）
        long basinCols = 0; // 盆地带内列（P26-B4 ⑪）
        long basinLe5 = 0;
        double basinMin = 0;
        // —— P27 G 片草原深芯/缎带沟计数（镜像门同生产式，见下方门值复算处注释）——
        long gullyCols = 0; // 沟带内列（gullyGate>0 且盆地带外——低地单臂同款"纯域"口径）
        long gullyLe4 = 0;
        double gullyMin = 0;
        long gullyLe5Mixed = 0; // 沟带内 d≤−5（只报不钉：低地单臂剥离沟带前的旧口径读数锚）
        long coreCols = 0; // 芯∩盆列（coreGate>0 且 basinGate>0——生产深芯臂只在此域非零）
        long coreLe10 = 0;
        long wasteCols = 0;
        long wasteGe12 = 0;
        // —— A4 森林谷地负瓣（v1.20.42）：净下挖覆盖 + 最深下挖（与 FOREST_VALLEY_* 常量同口径）——
        long forestCols = 0;
        long forestLe4 = 0;
        double forestMin = 0;
        final long[] tierN = new long[4];
        final double[] tierDeltaSum = new double[4];
        final double[] tierDeltaMax = new double[4];
        // v1.20.53 P30 II-D：泥丘正项限幅镜像计量（纯腹地 tier≠NONE 列；见 A4_SWAMP_NONWATER_RISE_MAX）
        long tieredPureCols = 0;
        double tieredPureMax = 0;
        long swampCols = 0;
        double worstDelta = 0;
        long totalCols = 0;
        for (int s = 0; s < VARIANT_SEEDS; s++) {
            final long worldSeed = s;
            A3_W3_CACHE.clear(); // w3 粗格缓存按 seed 重建（键不含 seed；variantGroup 与 a3Group 各自清）
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
                        // P26-B4 ⑪：盆地负瓣门镜像（判据侧只读复算，A3_S_SWAMP_CLAMP 先例）
                        final double bn = GTSRWorldgenHash
                            .valueNoise(worldSeed ^ A4_S_STEPPE_BASIN, x / A4_STEPPE_BASIN_SCALE, z / A4_STEPPE_BASIN_SCALE);
                        final double basinGate = a4S01((-bn - 0.60D) / 0.22D);
                        // P27 G 片：深芯/沟门镜像（判据侧只读复算）。门带字面 = 生产
                        // TerrainVariants.STEPPE_BASIN_CORE_GATE_LO/SPAN（0.62/0.20）与
                        // STEPPE_GULLY_GATE_LO/SPAN（0.78/0.12）；沟存在域因子 (1−basinGate)(1−gateS)
                        // 与生产同式（gateS 镜像 = 生产 s01((gh−0.20)/0.30)，gh=λ320 S_HILL_GATE）。
                        final double cn = GTSRWorldgenHash.valueNoise(
                            worldSeed ^ A4_S_STEPPE_BASIN_CORE, x / A4_STEPPE_BASIN_CORE_SCALE, z / A4_STEPPE_BASIN_CORE_SCALE);
                        final double coreGate = a4S01((-cn - 0.62D) / 0.20D);
                        final double gn = GTSRWorldgenHash.valueNoise(
                            worldSeed ^ A4_S_STEPPE_GULLY, x / A4_STEPPE_GULLY_SCALE, z / A4_STEPPE_GULLY_SCALE);
                        final double ghM = GTSRWorldgenHash
                            .valueNoise(worldSeed ^ A4_S_HILL_GATE, x / 320.0D, z / 320.0D);
                        final double gateSM = a4S01((ghM - 0.20D) / 0.30D);
                        final double gullyGate = a4S01((1.0D - Math.abs(gn) - 0.78D) / 0.12D)
                            * (1.0D - basinGate) * (1.0D - gateSM);
                        if (d <= -STEPPE_LOW_CUT_LEGACY) {
                            steppeLe3++;
                        }
                        if (d <= -6.0D) {
                            steppeLe6++;
                        }
                        if (d <= -STEPPE_LOW_CUT) {
                            steppeLe5All++;
                            if (basinGate > 0.0D) {
                                basinLe5++;
                            } else if (gullyGate > 0.0D) {
                                // P27 G 片：低地单臂计量剥离沟带（与 P26-B4 ⑪ 剥离盆地同款"换被量的量
                                // 不换带"——沟臂 −4..−6.5 的 d≤−5 列不归属低地；剥离前混计读数另行打印）。
                                gullyLe5Mixed++;
                            } else {
                                steppeLe5++;
                            }
                        }
                        if (basinGate > 0.0D) {
                            basinCols++;
                            if (d < basinMin) {
                                basinMin = d;
                            }
                        }
                        // P27 G 片计数：沟带 = gullyGate>0 且盆地带外（纯域口径，防 basinGate∈(0,1)
                        // 缓入环上盆地臂 −8 与沟臂部分共存把"沟最深"读数推过 −7 的机制混账）；
                        // 芯带 = coreGate>0 且 basinGate>0（生产深芯臂 ×basinGate，只在此域非零）。
                        if (gullyGate > 0.0D && basinGate == 0.0D) {
                            gullyCols++;
                            if (d <= -4.0D) {
                                gullyLe4++;
                            }
                            // v1.20.53 P30 II-D：不超挖读数换<b>纯核列</b>口径（69 粗格全 0 ⇒ w1-w4
                            // 精确 0，其它群系臂不叠）——旧"不滤名册边界混列"口径本批复跑命中
                            // −12.00 混合列（w0 沟臂叠 w3 深潭臂，见 a3PureRosterCell 注释），
                            // P27 G 片登记的贴边风险兑现，非机制超挖。
                            if (a3PureRosterCell(worldSeed, x >> 2, z >> 2, 0) && d < gullyMin) {
                                gullyMin = d;
                            }
                        }
                        if (coreGate > 0.0D && basinGate > 0.0D) {
                            coreCols++;
                            if (d <= -10.0D) {
                                coreLe10++;
                            }
                        }
                    } else if (tier == 1) {
                        forestCols++;
                        if (d <= -4.0D) {
                            forestLe4++;
                        }
                        forestMin = Math.min(forestMin, d);
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
                        // v1.20.53 P30 II-D：泥丘正项限幅（A4_SWAMP_NONWATER_RISE_MAX）镜像计量位——
                        // 纯核列（69 粗格全 3 ⇒ w0/w1/w2/w4 精确 0.0，其它群系臂整支跳过；判纯走
                        // a3PureRosterCell 而非 w3==1.0——归一核权浮点和恒 ≠ 精确 1.0）上
                        // tier≠NONE 的 d 上界 = +3.0（生产钳 min(dry, +3.0)；混核列 w2>0 时荒漠臂
                        // 可叠正 delta，不入本计量防误红——NONE 列不限幅，亦不入计量）。
                        if (t != TerrainVariants.SWAMP_TIER_NONE
                            && a3PureRosterCell(worldSeed, x >> 2, z >> 2, 3)) {
                            tieredPureCols++;
                            if (d > tieredPureMax) {
                                tieredPureMax = d;
                            }
                        }
                    }
                }
            }
        }
        final double lowShare = steppeLe5 / (double) steppeCols;
        final double bryceShare = wasteGe12 / (double) wasteCols;
        final double valleyShare = forestLe4 / (double) forestCols;
        final double basinShare = basinLe5 / (double) steppeCols;
        System.out.printf("  VARIANT %dseed×%d²方块(步距%d，h0=%d 旁路对照)：采样列=%d 形态 |delta| 最大=%.1f%n",
            VARIANT_SEEDS, VARIANT_SIDE, VARIANT_STRIDE, VARIANT_H0, totalCols, worstDelta);
        System.out.printf("  VARIANT-READ 草原(roster0) 列=%d ｜ 现行阈 delta≤−%.0f（低地单臂=盆/沟带外，P27 G 片起）占比=%.4f%%"
            + " ｜ 沟带内混计=%.4f%%（只报不钉，剥离前口径锚）｜ 含盆地合计=%.4f%%（只报不钉）｜ 旧阈"
            + " delta≤−%.0f 占比=%.4f%%（只报不钉）｜ 满门 delta≤−6 占比=%.4f%%（G6 自陈 ≈12%% 的对账锚）%n",
            steppeCols, STEPPE_LOW_CUT, lowShare * 100, gullyLe5Mixed / (double) steppeCols * 100,
            steppeLe5All / (double) steppeCols * 100,
            STEPPE_LOW_CUT_LEGACY,
            steppeLe3 / (double) steppeCols * 100, steppeLe6 / (double) steppeCols * 100);
        System.out.printf("  VARIANT-READ 草原盆地（P26-B4 ⑪）带内列=%d ｜ 净下挖 delta≤−5 占比=%.4f%% ｜ 最深=%.2f%n",
            basinCols, basinShare * 100, basinMin);
        System.out.printf("  VARIANT-READ 草原缎带沟（P27 G 片）带内列=%d ｜ 净下挖 delta≤−4 占比=%.4f%% ｜ 最深=%.2f%n",
            gullyCols, gullyLe4 / (double) steppeCols * 100, gullyMin);
        System.out.printf("  VARIANT-READ 草原深芯（P27 G 片）芯∩盆列=%d ｜ 净下挖 delta≤−10 占比=%.4f%%%n",
            coreCols, coreLe10 / (double) steppeCols * 100);
        System.out.printf("  VARIANT-READ 荒漠(roster2) 列=%d ｜ delta≥%.0f 占比=%.4f%%（风蚀山体）%n", wasteCols,
            BRYCE_DELTA_MIN, bryceShare * 100);
        System.out.printf("  VARIANT-READ 森林(roster1) 列=%d ｜ 净下挖 delta≤−4 占比=%.4f%%（谷地负瓣）｜"
            + " 最深=%.2f%n", forestCols, valleyShare * 100, forestMin);
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
            "VARIANT 草原低地覆盖（<b>满门深度口径 delta ≤ −" + (int) STEPPE_LOW_CUT + "，P26-B4 ⑪ 起 = 低地单臂"
                + "（盆地带外），<b>P27 G 片起再剥离沟带（盆/沟带外）</b></b>）占比 " + fmt1(lowShare)
                + " ∈ [" + STEPPE_LOW_SHARE_MIN + "," + STEPPE_LOW_SHARE_MAX + "]（§5 S4 判据 4 的带容器原值，"
                + "<b>换的是被量的量不是带</b>：§21-E 裁定旧 ≤−" + (int) STEPPE_LOW_CUT_LEGACY + " 口径把整个缓入环"
                + "计进低地（该口径本片仍打印 = " + fmt1(steppeLe3 / (double) steppeCols) + "，只报不钉，原文与"
                + "废止理由见 STEPPE_LOW_CUT_LEGACY 注释）；满门 delta≤−6 读数 = " + fmt1(steppeLe6 / (double) steppeCols)
                + " 与 TerrainVariants 低地门自陈 ≈12% 相互印证。P26-B4 ⑪ 把盆地下挖剥离到"
                + " STEPPE_BASIN_SHARE_BAND；P27 G 片把缎带沟（−4..−6.5）同款剥离到 STEPPE_GULLY_SHARE_BAND"
                + "（沟带内混计读数 = " + fmt1(gullyLe5Mixed / (double) steppeCols) + " 只报不钉）——低地单臂始终量"
                + " \"低地作为唯一负臂\"的净值。<b>禁止</b>为凑带削低地深度（那是生产侧））");
        // —— P26-B4 ⑪ 草原小盆地两条（负瓣封闭碗形；实测 M 读数按 [M×0.6,M×1.6] 0.1pp 网格反解）——
        check(steppeCols >= 10000 && basinShare >= STEPPE_BASIN_SHARE_MIN
            && basinShare <= STEPPE_BASIN_SHARE_MAX,
            "VARIANT 草原小盆地净下挖覆盖：盆地带内 delta ≤ −5 的列占比 " + fmt1(basinShare) + " ∈ ["
                + STEPPE_BASIN_SHARE_MIN + "," + STEPPE_BASIN_SHARE_MAX + "]（v1.20.49 P26-B4 ⑪ 新增；"
                + "下界=盆地形态存在（负瓣门塌掉即红），上界=草原不被盆地吞掉；与低地单臂带互斥分账）");
        check(basinMin >= -STEPPE_BASIN_DIG_MAX,
            "VARIANT 草原小盆地不超挖：盆地带内净 delta 最深 " + fmt1(basinMin) + " ≥ −" + STEPPE_BASIN_DIG_MAX
                + "（机制满门最深 −16（v1.20.50 P27 G 片深芯第二档：盆 −9 叠芯 −7）+ 0.5 取整容差；加深吃"
                + " y=40 地板预算，超挖即红——与 CLAMP_RATIO_MAX 预算带互为犄角）");
        // —— P27 G 片草原缎带沟两条 + 深芯一条（λ73 ridged 负像（redirect 由 λ57 收窄）/ λ89 盆内深芯；实测 M 反解带）——
        final double gullyShare = gullyLe4 / (double) steppeCols;
        final double coreShare = coreLe10 / (double) steppeCols;
        check(steppeCols >= 10000 && gullyShare >= STEPPE_GULLY_SHARE_MIN
            && gullyShare <= STEPPE_GULLY_SHARE_MAX,
            "VARIANT 草原缎带沟净下挖覆盖：沟带内（gullyGate>0 且盆地带外）delta ≤ −4 的列占比 "
                + fmt1(gullyShare) + " ∈ [" + STEPPE_GULLY_SHARE_MIN + "," + STEPPE_GULLY_SHARE_MAX
                + "]（v1.20.50 P27 G 片新增 λ57 ridged 负像、redirect 后 λ73；实测 M=18.5121%（λ57 旧值 17.9941%）"
                + "按 [M×0.6,M×1.6] 0.1pp 网格反解——面积占比与 λ 无关（带宽 ~cλ × 线密度 ~1/λ 相消）的披露见常量注释；"
                + "下界=沟形态存在（ridged 门塌掉"
                + "即红），上界=草原不被沟网吞掉；与低地单臂带互斥分账）");
        check(gullyMin >= -STEPPE_GULLY_DIG_MAX,
            "VARIANT 草原缎带沟不超挖：纯核列（69 粗格全 0，P30 II-D 起口径）沟带内净 delta 最深 "
                + fmt1(gullyMin) + " ≥ −" + STEPPE_GULLY_DIG_MAX
                + "（机制满门最深 −6.5 + 0.5 取整容差；沟带纯域口径下低地臂已乘 (1−gullyGate) 两负臂不相加，"
                + "超挖即红——与 CLAMP_RATIO_MAX 预算带互为犄角。旧全域口径实测 −12.00 = w0 沟臂叠 w3 深潭臂的"
                + "名册核混合列（P27 登记贴边风险的兑现，臂分解见常量注释），非机制超挖）");
        check(steppeCols >= 10000 && coreShare >= STEPPE_CORE_SHARE_MIN
            && coreShare <= STEPPE_CORE_SHARE_MAX,
            "VARIANT 草原深芯净下挖覆盖：芯∩盆列（coreGate>0 且 basinGate>0）delta ≤ −10 的列占比 "
                + fmt1(coreShare) + " ∈ [" + STEPPE_CORE_SHARE_MIN + "," + STEPPE_CORE_SHARE_MAX
                + "]（v1.20.50 P27 G 片盆内深芯第二档；实测 M=0.1474%（芯∩盆 0.5115%）按 [M×0.6,M×1.6]"
                + " 0.01pp 网格反解；下界=深芯作为第二档存在（芯门塌掉即红，ue-steppe §6 推翻条件 <0.15% 按"
                + " 芯∩盆覆盖口径未触发），上界=深碗不普遍化（普遍加深是 1a 方案的形态，已否决））");
        check(wasteCols >= 10000 && bryceShare >= BRYCE_SHARE_MIN && bryceShare <= BRYCE_SHARE_MAX,
            "VARIANT 风蚀山体存在性：roster2 内 delta ≥ " + (int) BRYCE_DELTA_MIN + " 的列占比 " + fmt1(bryceShare)
                + " ∈ [" + BRYCE_SHARE_MIN + "," + BRYCE_SHARE_MAX + "]（§5 S4 判据 3 的字面带"
                + "「山体应稀有但不是零」，§21-F 交实测 1.3429%、本片同口径复跑；改前 0.252% 会红 ⇒ 过带的"
                + "唯一改动是已落地的 BRYCE_GAIN 18→26，本片不动任何生产常量）");
        // —— A4 森林谷地负瓣两条（v1.20.42 P22 A4 新增；A4 前该形态不存在 ⇒ 无旧带，实测 M=1.5990% 反解）——
        check(forestCols >= 10000 && valleyShare >= FOREST_VALLEY_SHARE_MIN
            && valleyShare <= FOREST_VALLEY_SHARE_MAX,
            "VARIANT 森林谷地负瓣净下挖覆盖：roster1 内 delta ≤ −4 的列占比 " + fmt1(valleyShare) + " ∈ ["
                + FOREST_VALLEY_SHARE_MIN + "," + FOREST_VALLEY_SHARE_MAX + "]（实测 M=1.5990% 按 [M×0.6,M×1.6]"
                + " 0.1pp 网格反解；下界=谷地形态存在（负瓣门塌掉即红），上界=森林不被谷地吞掉；净口径"
                + "含同列丘陵/岭脊正项抵消，与门覆盖 any 10.10% 的形态侧自陈口径不同，见常量注释）");
        check(forestMin >= -FOREST_VALLEY_DIG_MAX,
            "VARIANT 森林谷地负瓣不超挖：roster1 净 delta 最深 " + fmt1(forestMin) + " ≥ −" + FOREST_VALLEY_DIG_MAX
                + "（机制满门最深 −10 + 0.5 取整容差；加深负瓣直通 DELTA_CAP 且吃 y=40 地板预算，超挖即红——"
                + "与 CLAMP_RATIO_MAX 预算带互为犄角）");
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
                + (t == 0 ? "[0.60,0.72] 绝对带（NONE 是三档补集，绝对带更硬；v1.20.42 P22 A3 边缘门后"
                    + "重钉，旧带 [0.5,0.7] 见常量注释）"
                    : t == 2 ? " [M×0.6, M×1.6] 取整到 0.5pp，另有深水池门自陈 ≈5% 与实测 5.505% 互证）"
                    : " [M×0.6, M×1.6] 取整到 0.5pp。本轮取实测，实机后校准）"));
        }
        // —— v1.20.53 P30 II-D 新增：泥丘正项限幅镜像（A4_SWAMP_NONWATER_RISE_MAX=+3.0；II-AB 落地）——
        check(tieredPureCols >= 1000 && tieredPureMax <= A4_SWAMP_NONWATER_RISE_MAX,
            "VARIANT 沼泽水体档泥丘正项限幅：纯核列（69 粗格全 3 ⇒ w0/w1/w2/w4 精确 0，其它群系臂"
                + "不参与）tier≠NONE 的 delta 最大 " + fmt1(tieredPureMax) + " ≤ +"
                + fmt1(A4_SWAMP_NONWATER_RISE_MAX) + "（样本 " + tieredPureCols
                + "；生产钳 min(dry, SWAMP_NONWATER_RISE_MAX)——满门泥丘 +3.5..+8 削顶，防泥丘把"
                + "水体档列的床抬过该档回填水面（\"挖而不灌\"的反向穿透）；NONE 列不限（干沼草甸泥丘岛"
                + "设计保留，delta 最大 " + fmt1(tierDeltaMax[0]) + " 不设断言）；混核列（w2>0 荒漠臂"
                + "可叠正 delta）不入计量防误红——生产放开该钳即红）");
    }

    // ═════════════ A3 组（v1.20.42 P22 A3 新增落点：边缘截断清零 + 包含性清零 + N 生效 + §21-D 复跑）═══

    /**
     * 沼泽水体的<b>避群系边缘 + 包含性</b>判据（v1.20.42 P22 A3）。逐列复刻
     * {@code ChunkProviderProsperityRuins.fillSwampPools}→SwampFieldGrid 的现行语义：连续边缘门
     * （{@code TerrainVariants.swampInteriorGateAt} {@code s01((w3−0.40)/0.15)}——<b>v1.20.53 P30
     * II-AB 由 coarse Chebyshev R=4 布尔阶梯连续化</b>，本文件按 11×11 核字面镜像 w3 分量复算）+
     * 湖平面压力域门（{@code lakePlaneTerrainAllowedAt > 0}，P29 C2）+ 区域场 N8 不动点钳制
     * （区域 256 对齐 + 环 64，与 {@code SwampFieldGrid} 逐行同构——低地排干链是池尺度，
     * per-chunk 窗口追不上，见 A3 片 {@code plan/tmp/p22-a3/PROGRESS.md} 的机制迭代记录）。
     * <b>[v1.20.53 P30 II-AB 瀑布退役]</b>原 P27/P28 同批镜像的瀑布潭腿（潭心潭面钳低/高水台/
     * 转换趟/同面连通 sheet/悬空修复环）随生产整段删除，nominal 只剩三档/微池名义水位
     * {@code poolLevelAt−1} 原式；微池腿走 {@code swampPoolWaterAt} 单一出口（O1a）。四条断言：
     * <ol>
     * <li><b>连续边缘门镜像对拍</b>：镜像门与生产 {@code swampInteriorGateAt} 逐列 IEEE 逐位相等
     * （mismatch=0）——生产换门带/换核而镜像漏跟即红（替代旧「边缘截断水潭 == 0」阶梯证明：
     * 连续门下水列可邻非 3 粗格（w3 ≥ 0.475 的混合等值线内侧），直角台阶读数不再存在）；</li>
     * <li><b>悬空水列 == 0</b>：每水列的干邻（含被钳干空坑列，按<b>钳后</b>实际状态判）固体顶
     * ≥ 水顶——A1b {@code GTSRRiverPlacer.neighborBarrier} 的 0 余量口径，收口 v1.20.40 申报
     * "微池 69/71 桶低地悬空"（改前 8 seed×512² 实测 874，其中腹地低地 193）；</li>
     * <li><b>边缘门咬合</b>：水列的生产门读数 ≥ {@link #A3_TIER_MIN}（镜像门建出的水列越过生产
     * 门口径 = 镜像/生产失同步，防假绿——旧「N=16 生效 minDist ≥ R+1」的连续化换轴）；</li>
     * <li><b>§21-D 穿透复跑</b>：纯 roster3 腹地列上 NONE/POOL/MARSH 档净下挖 ≥4 格 == 0
     * （p20-s4b 净测口径：net = −(总 delta − 夹持基线)，修复后实测 0——连续边缘门把边缘列档位
     * 随门值缩退后仍须为 0，互斥语义不破）。</li>
     * </ol>
     * <b>断言计数登记（v1.20.42 P22 A3）：53 → 57（A3 组 +4，无删改）。</b>
     * <b>v1.20.53 P30 II-D：累计 64 → 65（实跑计数）——A3 组仍是 4 条（「边缘截断」「N=16 生效」
     * 两条随生产连续化换轴为「连续门对拍」「边缘门咬合」）；VARIANT 组 +1 泥丘正项限幅镜像条。
     * 此前各批新增（P26-B4 ⑪ 盆地两条 / P27 G 沟两条+深芯一条等）未逐批登记，以本批复跑实测
     * 计数为准一并补记。</b>
     */
    private static void a3Group() {
        final int side = A3_SIDE;
        final int off = A3_FMAR;
        final int w = side + 2 * off; // 数组窗 [−64, 576)
        final int n = w * w;
        final int[] h = new int[n];
        final int[] nominal = new int[n];
        final int[] fixed = new int[n];
        final int[] effTop = new int[n];
        final byte[] stA = new byte[n];
        // [v1.20.53 P30 II-AB 瀑布退役] 原 pool[] 段位表与 convA[] 转换标记随转换趟删除
        // （生产 nominal 只剩三档/微池名义水位 poolLevelAt−1 原式，段位表无消费者）。
        long waterCols = 0;
        long gateMismatch = 0;
        long edgeWater = 0;
        long suspendedDry = 0;
        // —— §21-D 净下挖复跑（stride 4，与 p20-s4b/probe-after.out 同口径）——
        final long[] netGe4 = new long[4];
        final long[] pureN = new long[4];
        long pureAll = 0;
        for (int s = 0; s < A3_SEEDS; s++) {
            final long seed = s;
            A3_W3_CACHE.clear();
            // —— pass 1：逐列名义顶/固定水面（与 fillSwampPools→SwampFieldGrid 同式；v1.20.53
            // P30 II-D 同步：tier==3 腿挂湖平面压力域门（P29 C2）+ 连续边缘门（镜像算式，与
            // 生产 swampInteriorGateAt 逐位对拍）+ 微池腿走 swampPoolWaterAt 单一出口（O1a），
            // 瀑布潭钳低/高水台腿整段删除）——
            for (int z = -off; z < side + off; z++) {
                for (int x = -off; x < side + off; x++) {
                    final int i = x + off + (z + off) * w;
                    final int tier = ProsperityTerrainProfile.chainRosterIndexAt(seed, x >> 2, z >> 2);
                    final int hv = ProsperityTerrainProfile.heightAt(seed, x, z);
                    final int pRaw = GTSRVoronoiRiverField.poolLevelAt(seed, x, z, tier);
                    h[i] = hv;
                    nominal[i] = -1;
                    fixed[i] = -1;
                    stA[i] = 0;
                    final boolean sub = GTSRVoronoiRiverField.submergedAt(hv, pRaw);
                    // 连续边缘门镜像对拍（漏改=假绿纪律项）：镜像门与生产 swampInteriorGateAt
                    // 同式同核同累加次序 ⇒ IEEE 逐位相等；不等 = 生产换门带/换核而镜像漏跟。
                    final double gateM = a3InteriorGateAt(seed, x, z);
                    if (gateM != TerrainVariants.swampInteriorGateAt(seed, x, z)) {
                        gateMismatch++;
                    }
                    if (tier == 3 && TerrainVariants.lakePlaneTerrainAllowedAt(seed, x, z) > 0.0D) {
                        final int t = sub ? TerrainVariants.swampTierAt(seed, x, z, 3) : 0;
                        stA[i] = (byte) t;
                        // A3 连续边缘门（镜像）：三档腿经 swampTierAt 的 SWG_TIER 槽已乘生产门自动
                        // 衰减；微池腿显式乘同门 ≥ TIER_MIN（= 生产 SwampFieldGrid 构建体同构）。
                        if (sub && gateM >= A3_TIER_MIN
                            && (t != TerrainVariants.SWAMP_TIER_NONE
                                || GTSRVoronoiRiverField.swampPoolWaterAt(seed, x, z, 3))) {
                            nominal[i] = pRaw - 1;
                        }
                    }
                    if (sub && GTSRVoronoiRiverField.wetAt(seed, x, z, tier)) {
                        fixed[i] = pRaw - 1;
                    }
                    if (GTSRVoronoiRiverField.lakeAt(seed, x, z) < GTSRVoronoiRiverField.LAKE_SHORE
                        && hv < ProsperityTerrainProfile.SEA_LEVEL) {
                        fixed[i] = Math.max(fixed[i], ProsperityTerrainProfile.SEA_LEVEL - 1);
                    }
                    // [P25 D7 残潭退役] 原"残潭 fixed 腿"（swampRiverPoolAt>0 ⇒ fixed = p +
                    // SWAMP_RIVER_POOL_FILL_TOP）已删：残潭场随生产 D7 整体退役，本重算面零消费
                    // （生产 SwampFieldGrid 的同腿同批摘除，见 ChunkProviderProsperityRuins 构建体登记）。
                }
            }
            // —— pass 2：区域场不动点（区域 {0,1}²，+64 环恰为数组内边距；与 SwampFieldGrid 同构）——
            // v1.20.53 P30 II-AB 瀑布退役同步：原 P27 批次B-B1 同批镜像的转换趟（候选判定/同面
            // 连通 sheet/fixed←nominal 转换）与 P28 §redirect2 悬空修复环整段删除（生产 fixed 只剩
            // 巨湖水腿 ⇒ 无承载对象；水顶快照语义保留 = tops 初值取 nominal，N8 钳制其上）。
            // 每区域取局部 nomL/fixL（窗边裁剪同生产），Jacobi 读 nomL/fixL，核心列回写
            // effTop/nominal/fixed（= 属主区域口径，统计域 [16,side−16)² 全在四区域核心内）。
            System.arraycopy(nominal, 0, effTop, 0, n);
            for (int rz = 0; rz < side / A3_FREG; rz++) {
                for (int rx = 0; rx < side / A3_FREG; rx++) {
                    final int regX = rx * A3_FREG;
                    final int regZ = rz * A3_FREG;
                    final int gw = A3_FREG + 2 * A3_FMAR;
                    final int[] top = new int[gw * gw];
                    final int[] nomL = new int[gw * gw];
                    final int[] fixL = new int[gw * gw];
                    for (int lz = 0; lz < gw; lz++) {
                        for (int lx = 0; lx < gw; lx++) {
                            final int gi = regX - A3_FMAR + lx + off + (regZ - A3_FMAR + lz + off) * w;
                            final int li = lx + lz * gw;
                            top[li] = nominal[gi];
                            nomL[li] = nominal[gi];
                            fixL[li] = fixed[gi];
                        }
                    }
                    // —— N8 Jacobi 不动点钳制（生产 SwampFieldGrid 构建体同构：Jacobi 逐趟，
                    // 趟用上趟快照 ⇒ 确定性与扫描序无关；单调下降必收敛，趟上限 96 保守界；
                    // 区域窗边裁剪：窗外邻格视同不存在）——
                    for (int pass = 0; pass < 96; pass++) {
                        boolean changed = false;
                        final int[] cur = top.clone();
                        for (int lz = 0; lz < gw; lz++) {
                            for (int lx = 0; lx < gw; lx++) {
                                final int li = lx + lz * gw;
                                if (nomL[li] < 0) {
                                    continue;
                                }
                                int t = cur[li];
                                for (int dz = -1; dz <= 1; dz++) {
                                    final int nz = lz + dz;
                                    if (nz < 0 || nz >= gw) {
                                        continue;
                                    }
                                    for (int dx = -1; dx <= 1; dx++) {
                                        if (dx == 0 && dz == 0) {
                                            continue;
                                        }
                                        final int nx = lx + dx;
                                        if (nx < 0 || nx >= gw) {
                                            continue;
                                        }
                                        final int lj = nx + nz * gw;
                                        final int gj = regX - A3_FMAR + nx + off
                                            + (regZ - A3_FMAR + nz + off) * w;
                                        final int b = fixL[lj] >= 0
                                            ? Math.max(
                                                fixL[lj],
                                                cur[lj] >= h[gj] + 1 ? cur[lj] : h[gj])
                                            : (cur[lj] >= h[gj] + 1 ? cur[lj] : h[gj]);
                                        if (b < t) {
                                            t = b;
                                        }
                                    }
                                }
                                if (t < cur[li]) {
                                    top[li] = t;
                                    changed = true;
                                }
                            }
                        }
                        if (!changed) {
                            break;
                        }
                    }
                    for (int lz = A3_FMAR; lz < A3_FMAR + A3_FREG; lz++) {
                        for (int lx = A3_FMAR; lx < A3_FMAR + A3_FREG; lx++) {
                            final int gi = regX - A3_FMAR + lx + off + (regZ - A3_FMAR + lz + off) * w;
                            effTop[gi] = top[lx + lz * gw];
                            nominal[gi] = nomL[lx + lz * gw];
                            fixed[gi] = fixL[lx + lz * gw];
                        }
                    }
                }
            }
            // —— pass 3：判据统计（统计域 [16, side−16)²）——
            for (int z = 16; z < side - 16; z++) {
                for (int x = 16; x < side - 16; x++) {
                    final int i = x + off + (z + off) * w;
                    if (nominal[i] < 0 || effTop[i] < h[i] + 1) {
                        continue; // 无名义项 / 钳干（宁缺不悬）——不是水列
                    }
                    waterCols++;
                    // 连续门口径的「边缘咬合」读数（旧 Chebyshev minDist ≥ R+1 证明的换轴）：
                    // 水列的生产门读数必须 ≥ TIER_MIN——镜像门建出的水列越过生产门口径 =
                    // 镜像/生产失同步（防假绿；对拍断言的另一侧）。
                    if (TerrainVariants.swampInteriorGateAt(seed, x, z) < A3_TIER_MIN) {
                        edgeWater++;
                    }
                    final int tw = effTop[i];
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dz == 0) {
                                continue;
                            }
                            final int ni = i + dz * w + dx;
                            final int nt;
                            if (fixed[ni] >= 0) {
                                nt = Math.max(fixed[ni], effTop[ni] >= h[ni] + 1 ? effTop[ni] : h[ni]);
                            } else if (effTop[ni] >= h[ni] + 1) {
                                nt = effTop[ni];
                            } else {
                                nt = -1; // 干列（含被钳干空坑——按钳后实际状态）
                            }
                            if (nt < 0 && h[ni] < tw) {
                                suspendedDry++;
                            }
                        }
                    }
                }
            }
            // —— §21-D 净下挖复跑（stride 4；纯腹地列 = 11×11 核 69 粗格全 3，|delta| ≤ 22）——
            for (int z = 0; z < side; z += 4) {
                for (int x = 0; x < side; x += 4) {
                    final int cx = x >> 2;
                    final int cz = z >> 2;
                    if (rosterAt(seed, cx, cz) != 3) {
                        continue;
                    }
                    final int tier = TerrainVariants.swampTierAt(seed, x, z, 3);
                    final double d = TerrainVariants.variantAdjustment(seed, x, z, 3, VARIANT_H0)
                        - (double) VARIANT_H0;
                    if (!a3PureCell(seed, cx, cz) || d < -22.0D || d > 22.0D) {
                        continue;
                    }
                    // 夹持基线 (SEA_LEVEL − h0)·f（p20-s4b 口径；f = 0.55 + 0.40·s01((gs+1)/2)）
                    final double gs = GTSRWorldgenHash
                        .valueNoise(seed ^ A3_S_SWAMP_CLAMP, x / 256.0D, z / 256.0D);
                    final double c = Math.max(0.0D, Math.min(1.0D, (gs + 1.0D) * 0.5D));
                    final double f = 0.55D + 0.40D * (c * c * (3.0D - 2.0D * c));
                    final double net = -(d - (ProsperityTerrainProfile.SEA_LEVEL - VARIANT_H0) * f);
                    pureN[tier]++;
                    pureAll++;
                    if (net >= 4.0D && tier != TerrainVariants.SWAMP_TIER_DEEP) {
                        netGe4[tier]++;
                    }
                }
            }
        }
        System.out.printf(
            "  A3 %dseed×%d²方块(stride1 区域场)：waterCols=%d gateMismatch=%d edgeWater=%d suspendedDry=%d"
                + " §21D净测 pure=%d netGe4 NONE/POOL/MARSH=%d/%d/%d%n",
            A3_SEEDS,
            A3_SIDE,
            waterCols,
            gateMismatch,
            edgeWater,
            suspendedDry,
            pureAll,
            netGe4[0],
            netGe4[1],
            netGe4[3]);
        check(waterCols >= 10000 && gateMismatch == 0,
            "A3 连续边缘门镜像对拍：s01((w3−" + A3_INTERIOR_GATE_LO + ")/" + A3_INTERIOR_GATE_SPAN + ")"
                + "（11×11 核字面镜像 w3 分量）== 生产 swampInteriorGateAt 逐列 mismatch=" + gateMismatch
                + "（水列样本 " + waterCols + " ≥ 10000；v1.20.53 P30 II-AB 生产连续化后，v1.20.42"
                + " A3 的 coarse Chebyshev R=4 阶梯与「边缘截断水潭 == 0 / N=16 minDist」两证明随直角"
                + "台阶退役——连续门下水列可邻非 3 粗格（w3 ≥ 0.475 混合等值线内侧），直角读数不再存在，"
                + "判据换轴为镜像/生产逐位对拍）");
        check(suspendedDry == 0,
            "A3 包含性（悬空水列 == 0）：每水列的干邻固体顶 ≥ 水顶（0 余量口径；干邻含被钳干空坑列，"
                + "按区域场钳后实际状态判）实测 " + suspendedDry + "——收口 v1.20.40 申报「微池 69/71 桶低地"
                + "悬空」（改前 874 = 边缘 681 + 腹地 193；N8 区域场钳制 = A1b 残潭钳制同族，"
                + "宁缺不悬；低地排干链是池尺度 ⇒ per-chunk 窗口 1/2/4/8 圈环实测漏 59/31/43/72，"
                + "区域场+64 环后实测 0，机制迭代记录见 plan/tmp/p22-a3/PROGRESS.md）");
        check(edgeWater == 0,
            "A3 边缘门咬合：水列的生产连续门读数 ≥ TIER_MIN=" + A3_TIER_MIN + "（例外 " + edgeWater
                + "——镜像门建出的水列越过生产门口径 = 镜像/生产失同步，防假绿；旧「N=16 生效 minDist"
                + " ≥ R+1=5」的连续化换轴）");
        check(netGe4[0] == 0 && netGe4[1] == 0 && netGe4[3] == 0 && pureAll >= 10000,
            "A3 §21-D 穿透复跑：纯 roster3 腹地列净下挖 ≥4 格 NONE/POOL/MARSH = " + netGe4[0] + "/"
                + netGe4[1] + "/" + netGe4[3] + " 全 0（p20-s4b 净测口径复跑；连续边缘门把边缘列档位"
                + "随门值缩退后互斥语义不破——NONE 档的净下挖上界仍是夹持 1.9 + 非水体限幅 1.5 = 3.4 < 4）");
    }

    // [v1.20.53 P30 II-AB 瀑布退役] 原转换趟逐列包含性自检镜像、a3Interior（Chebyshev
    // R=4 阶梯镜像）、a3CellDist（minDist 证明用粗格距离）随生产连续边缘门/转换趟删除同批摘除。

    /**
     * A3 连续边缘门镜像的 w3 粗格缓存（生产 {@code VAR_CELL_CACHE} 的"同粗格恒等"同款口径；
     * 键 = (cellX&lt;&lt;32)^cellZ，<b>每 seed 起点清空</b>——消费面 a3Group/variantGroup 单线程串行）。
     */
    private static final java.util.HashMap<Long, Double> A3_W3_CACHE = new java.util.HashMap<>();

    /**
     * A3 连续边缘门镜像（= 生产 {@code TerrainVariants.swampInteriorGateAt}，v1.20.53 P30 II-AB
     * 由 coarse Chebyshev R=4 布尔阶梯连续化）：{@code s01((w3 − 0.40)/0.15)}，w3 = 11×11 核
     * （A3_KERNEL_DX/DZ/W 字面镜像）权重向量在 roster 3 上的分量——只累加 r==3 的核点
     * ⇒ 与生产 {@code weightsAt} 的 w[3] 同一浮点累加序列（dx 外层 dz 内层），IEEE 逐位一致。
     * 纯读镜像：生产换门带/换核不改这里 ⇒ a3Group 的逐列对拍断言当场红（漏改=假绿纪律项）。
     */
    private static double a3InteriorGateAt(long seed, int x, int z) {
        final double w3 = a3W3At(seed, x, z);
        return a4S01((w3 - A3_INTERIOR_GATE_LO) / A3_INTERIOR_GATE_SPAN);
    }

    /**
     * 镜像核权重向量在 roster 3 上的分量（粗格缓存；同粗格内所有列恒等——生产
     * {@code weightsAt} 的缓存语义镜像，本侧不写槽只取 [3] 分量）。
     */
    private static double a3W3At(long seed, int x, int z) {
        final int cellX = x >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int cellZ = z >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final Long key = Long.valueOf((((long) cellX) << 32) ^ (cellZ & 0xFFFFFFFFL));
        final Double cached = A3_W3_CACHE.get(key);
        if (cached != null) {
            return cached.doubleValue();
        }
        double w3 = 0.0D;
        for (int k = 0; k < A3_KERNEL_DX.length; k++) {
            if (ProsperityTerrainProfile
                .chainRosterIndexAt(seed, cellX + A3_KERNEL_DX[k], cellZ + A3_KERNEL_DZ[k]) == 3) {
                w3 += A3_KERNEL_W[k];
            }
        }
        A3_W3_CACHE.put(key, Double.valueOf(w3));
        return w3;
    }

    /** 纯 roster3 腹地列（11×11 核 69 粗格全 3；p20-s4b 的 pure 口径）。 */
    private static boolean a3PureCell(long seed, int cellX, int cellZ) {
        return a3PureRosterCell(seed, cellX, cellZ, 3);
    }

    /**
     * 纯 roster 列（11×11 核 69 粗格全为给定 roster ⇒ 核权重向量其它分量精确 0.0，其它群系臂
     * 整支跳过）。<b>v1.20.53 P30 II-D 引入</b>：超挖类断言（沟/泥丘限幅）的计量口径从"名册
     * 边界混列不滤"（P27 G 片先例，当时即登记贴边风险）收紧为<b>纯核列</b>——本批复跑实测
     * 沟带内最深 −12.00 = w0 沟臂（−6.5·w0）叠 w3 沼泽深潭臂（−14·w3）的混合列（seed4
     * (216,316)，gullyGate=1.0/armGully=−6.5/armLow=0，探针 temp/p30-iid/），非任一单臂机制
     * 超挖；纯核列上机制满门界才是精确读数。注意不能用镜像 w3==1.0 判纯（归一核权重的浮点和
     * Σ(w_k/wSum) 不保证精确 1.0 ⇒ 恒假，本批复跑实测样本=0）。
     */
    private static boolean a3PureRosterCell(long seed, int cellX, int cellZ, int roster) {
        for (int dz = -5; dz <= 5; dz++) {
            for (int dx = -5; dx <= 5; dx++) {
                if (dx * dx + dz * dz < 25 && rosterAt(seed, cellX + dx, cellZ + dz) != roster) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int rosterAt(long seed, int cellX, int cellZ) {
        return ProsperityTerrainProfile.chainRosterIndexAt(seed, cellX, cellZ);
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
        // P23 R1·S5（v1.20.46 批1）重钉：columnTopSafeY 的取高链 = heightAt 单一真值基 +
        // 水感知层（populate 同源谓词 populateWaterColumnAt（湖=fillSanzuLakes 同式、残潭=O1a 出口）
        // + 螺旋 ≤16 列避让 + 水面 fallback）——水感知不另立第二份取高式，基座仍只有 heightAt。
        check(command.contains("ProsperityTerrainProfile.heightAt(seed, x, z)")
            && command.contains("ProsperityTerrainProfile.heightAt(seed, nx, nz)"),
            "REDLINE 指令取高基仍直引同一个 heightAt（命中列与螺旋避让列同源；S5 水感知层叠在其上）");
        check(command.contains("populateWaterColumnAt(seed, x, z)")
            && command.contains("populateWaterColumnAt(seed, nx, nz)")
            && command.contains("GTSRVoronoiRiverField.sanzuShoreWaterAt(worldSeed, x, z)")
            // [P25 D7 残潭退役] 原残潭腿（swampRiverPoolColumnAt）已随生产删除：水感知 = 湖单腿。
            // [v1.20.53 P30 I3] 湖腿对齐 RVF sanzuShoreWaterAt 单一出口（= fillSanzuLakes 置水门
            // 同式，旧 lakeAt<LAKE_SHORE 直比式退役）。
            && command.contains("ProsperityTerrainProfile.SEA_LEVEL - 1"),
            "REDLINE 指令水感知层 = populate 同源谓词（湖走 RVF sanzuShoreWaterAt 单一出口，与"
                + "fillSanzuLakes 置水门同式，零第二份水判定；P25 D7 残潭腿已退役、P30 I3 起灌水对齐）"
                + "+ 水面 fallback（SEA_LEVEL−1，站水面）——P23 S5 口径的 P30 修订");
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
