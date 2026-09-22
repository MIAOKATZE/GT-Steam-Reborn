package com.miaokatze.gtsr.common.dimension.prosperity.river;

import java.util.HashMap;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/**
 * <b>dim78 河流强度场（v1.20.39 T4，plan §3.1/§3.2）</b>：两级 OpenSimplex Disk jitter 蜿蜒
 * + Voronoi 细胞边界 border2 的<b>纯函数</b>河流场，整体替换 P17 S-C 的两族轴向等距线模型
 * （{@code GTSRRiverNetwork} 已删除——方形河的根因就是那套"等距线 + 沿线摆动"骨架）。
 *
 * <p>
 * ═══ 数学定义（rs = riverStrength ∈ [-1,0]，0=无影响、-1=河道中心；下文 s = -rs ∈ [0,1]）═══
 * <ol>
 * <li><b>蜿蜒抖动</b>：p=(x,z)；p' = p + disk₁(p/240)×90 + disk₂(p/80)×22。disk = OpenSimplex
 * 二维向量位移（A2 格点 + 12 向 SPH2 梯度，输出单位圆盘内二维向量；实现见内嵌
 * {@link OpenSimplexDisk}，公开域算法紧凑版，不引外部依赖）。大弯 90 格/尺度 240、
 * 小弯 22 格/尺度 80 —— 同一坐标域上两级无关噪声把 Voronoi 输入面揉出自然蜿蜒，
 * 消除"全世界沿坐标轴相关"的原版病（参照 RTG {@code WorldChunkManagerRTG.getRiverStrength}
 * 的 240/80 两级 jitter 形状）。</li>
 * <li><b>Voronoi border2</b>：细胞边长 {@link #SEPARATION}=1050；细胞中心 = 格点 +
 * 确定性哈希偏移（±{@link #CELL_JITTER}×separation，{@code seed+格点坐标} 经
 * {@link GTSRWorldgenHash#cellSeed}+{@link GTSRWorldgenHash#splitmix64} 整数混淆，即仓内
 * mixColumn 族的既有口径，不自建哈希）。对 p' 做 3×3 邻域 Worley：dC=最近中心距、dN=次近
 * 中心距，c=(dN-dC)/dN ∈ [0,1)（边界=0、细胞腹地→1；{@code c} 的定义与 RTG
 * {@code VoronoiCellOctave.border2:165-227} 逐式同形）。Voronoi 边界天然成网 ⇒ 支流连通
 * 是构造性质，不是后处理。</li>
 * <li><b>强度</b>：rs = c &lt; width ? (c/width - 1) : 0。 {@link #WIDTH} 是河/谷域（rs&lt;0）的
 * 相对半宽档：垂直边界方向上，c=width 处离边界 ≈ width×D/2/(2-width) 格（D=相邻中心距
 * ≈ {@link #SEPARATION}×1.05；v1.20.39 实测换算 ≈188×c 格）。v1.20.39 从起步 0.012 校准到
 * 0.14；<b>v1.20.40（P19 plan §A.2）收窄回 0.08</b>：0.14 档常态水道半宽 ≈5.8 格被实机判
 * "河面过宽"，0.08 ⇒ ≈3-4 格（目标带 3-4.5）、谷域半宽 ≈15 格（配合 {@link #LARGE_BEND}
 * 收 150 后谷坡半宽目标带 15-35；正式判据带重钉在 P19 批4）。</li>
 * </ol>
 *
 * <p>
 * ═══ 群系 riverStyle 档表（plan §3.2 {@link #RIVER_STYLE_BY_ROSTER}，v1.20.39 T5 起 5 元——
 * 第 5 元 sanzu 主干宽河占位档）═══ 下标 = L1 维内名册下标（0 锈蚀草原 / 1 齿轮森林 / 2 黄铜
 * 荒漠 / 3 喷气沼泽），越界/缺席（{@code rosterIndex} = -1，未装配账本的离线 JVM）回退
 * {@link #DEFAULT_STYLE}（常态河）——与 S-A 振幅档表同一条"档缺失走默认档"纪律，无任何
 * 身份等值判断。四档差异全部是<b>纯乘子/纯参数</b>（v1.20.40 P19 起水面/床改走
 * {@link #poolLevelAt} 分段水位阶梯 + {@code depth} 深度档，见 P19 plan §A/§B/§C）：
 * <ul>
 * <li>草原(0)/森林(1)：常态河——床 = pool−{@code depth}±n_bed，n_bed 抬到 ≥ 水面的列自然
 * 干出为<b>浅滩/河滩</b>（浅滩重释 = 派生条件，独立 shoalNoise 档已废）；</li>
 * <li>荒漠(2)：干谷断流——河谷照刻，置水改<b>整段闸</b>（{@code wet = hash(segKey)}，干:湿
 * = 65:35，{@link #DESERT_WET_SHARE}；同一段全干或全湿，BOP DryRiver 先例=干段整段连贯；
 * v1.20.39 的 wetNoise 串珠闸已废）；</li>
 * <li>沼泽(3)：沼地河——width×1.2（v1.20.40 从 1.6 收窄）、depth=1（水近地，水深 0-1 成沼地
 * 肌理）；不换河群系（RTG swamp 豁免先例）。</li>
 * </ul>
 *
 * <p>
 * ═══ 契约（与 heightAt 同一条红线）═══ 本类<b>纯静态、零 {@code net.minecraft} 依赖</b>，
 * 只依赖 worldSeed 与坐标 ⇒ 同 seed 同坐标必得同值，跨 chunk 接缝一致；真正落方块在
 * {@link GTSRRiverPlacer}（populate 后置）。线程纪律沿用 heightAt 现状（单线程 chunk 生成域
 * 求值）；唯一的可变状态是<b>线程私有</b>的 per-seed OpenSimplex 置换表缓存
 * （{@link #DISK_CACHE}，参照 T3 ampAt 缓存模式：ThreadLocal、同 seed 一致、上限整清）。
 * Worley 细胞偏移与全部床/浅滩/断流噪声走 {@link GTSRWorldgenHash} 唯一那份核，无第二真值。
 */
public final class GTSRVoronoiRiverField {

    /** Voronoi 细胞边长（格）。相邻河网间距 ≈ separation×(0.7..1.4)。 */
    public static final double SEPARATION = 1050.0D;

    /** 大弯蜿蜒：位移尺度（格）。 */
    public static final double LARGE_BEND_SCALE = 240.0D;

    /**
     * 大弯蜿蜒：位移幅度（格，圆盘向量全长上限）。v1.20.39 校准回路（蜿蜒度 &gt;1.2 判据）从
     * 起步 90 定到 240（disk 实测曲折率 90 档 ~1.06、240 档 ≈1.27）。<b>v1.20.40（P19 plan
     * §A.5）收 150</b>：WARP 压缩比向 RTG 比例回收（RTG 140 配 separation 1875 ⇒ 同比例本维
     * separation 1050 ≈ 150），配合 WIDTH 0.08 收窄——校准目标"谷坡半宽 15-35 格、河缘坡度
     * 肉眼连续"（RiverMorphologyCheck 口径预跑验证，正式判据带 P19 批4 重钉）。
     */
    public static final double LARGE_BEND = 150.0D;

    /** 小弯蜿蜒：位移尺度（格）。 */
    public static final double SMALL_BEND_SCALE = 80.0D;

    /** 小弯蜿蜒：位移幅度（格）。 */
    public static final double SMALL_BEND = 22.0D;

    /**
     * 河道相对半宽档（c 域阈值）：rs&lt;0 的域 = c&lt;width，即"河谷域"。v1.20.39 从 0.012 校准
     * 到 0.14；<b>v1.20.40（P19 plan §A.2）收窄 0.14→0.08</b>——实机"非 sanzu 河过宽"，0.08
     * 按 v1.20.39 实测换算（≈188×c 格）给常态水道半宽 ≈3-4 格（plan 目标带 3-4.5；sanzu 主干
     * 由 {@link #TRUNK_WIDTH_SCALE}×3.5 展宽，不受本档收缩影响）。
     */
    public static final double WIDTH = 0.08D;

    /**
     * 置水最小强度（s = -rs）：s ≥ 本值 = 河道核（平床+置水域）。与 {@link #VALLEY_LEVEL}
     * 同值是刻意的：e=clamp(1-s/VALLEY_LEVEL) 在 s=VALLEY_LEVEL 处恰为 0 ⇒ <b>平底域精确等于
     * 置水域</b>（水面下是平床，水面外起坡），两常数各守各的语义。v1.20.39 校准回路从 0.85 定到
     * <b>0.78</b>：warp 压缩使 0.85 档实测水道半宽中位 4.0（目标带 5-8 下沿之下），0.78 档
     * c 阈值 0.22×WIDTH ⇒ 半宽回中带，谷坡半宽同时留在 20-40。
     */
    public static final double WET_MIN = 0.78D;

    /**
     * 河谷压低混合档（heightCore 的 e=clamp(1-s/本值,0,1)）：e=1 无河影响、e=0 河床平底。
     * 规划起步 0.15 会让平底域伸到 c&lt;0.85×width（88% 谷半宽），坡只余 ~7 格悬崖环——
     * 校准回路按"谷坡半宽 20-40"目标改与 WET_MIN 等值（坡从水缘连续升到谷缘，类注释）。
     */
    public static final double VALLEY_LEVEL = WET_MIN;

    /**
     * <b>v1.20.40（P19 §B）退役</b>：荒漠断流从 wetNoise 串珠闸改为 {@link #segKeyAt} 整段闸
     * （{@link #DESERT_WET_SHARE}），本常量不再参与任何生成判定。<b>保留字段</b>只因
     * {@code tools/dim1/RiverMorphologyCheck} 的 F 组读数行仍引用（判据文件 P19 批4 重钉时随
     * 串珠口径一并退役），生产链路不得消费它。
     */
    public static final double WET_EDGE = 0.6D;

    /**
     * 池水位锚基基准（v1.20.40 P19 §C 起）：{@link #poolLevelAt} 沿细胞边三点采样
     * {@code bedTarget + POOL_ANCHOR_OFFSET ± POOL_ANCHOR_AMP} 取 min 后量化——本值是
     * <b>默认档</b>的 bedTarget，常态锚基面 = 64.5+1.5 = 66，量化后常态池水位 ∈
     * {63,65,67}（{@code SEA_LEVEL}=68 之下 1-5 格，落差瀑布由此出）。
     * 各群系档的 bedTarget 见 {@link #RIVER_STYLE_BY_ROSTER}。
     */
    public static final double BED_TARGET = 64.5D;

    /** 细胞中心哈希偏移幅度（×separation；&lt;0.5 ⇒ 3×3 邻域 Worley F1 数学精确）。 */
    public static final double CELL_JITTER = 0.35D;

    /** 床低频噪声波长（格）——沿程床起伏（±幅度见档表）。 */
    public static final double BED_NOISE_SCALE = 96.0D;

    // ═════════════════ v1.20.40（P19 plan §A/§B/§C）：两段式河谷 + 分段水位阶梯 ═════════════════

    /**
     * 内段切床起点（v1.20.40 P19 §A.1）：heightCore 的<b>两段式</b>压低中，s ≥ 本值起向
     * {@code bedAt} 二次 lerp（外段全谷带先压向 poolLevel+{@link #RIM_EPS} 贴水缓坡）。
     * 对位 RTG actualRiverProportion/valleyLevel ≈ 1.4 比例（G1 调查：TerrainBase 两段式 +
     * RealisticBiomeBase erodedNoise 内段 r&lt;0.1875 才切 57 床）⇒ 本值 = WET_MIN/1.4 ≈ 0.557，
     * 并按新 {@link #WIDTH}=0.08 校准：切床域（s≥本值）横向半宽 ≈ 188×c ≈ 6-7 格 = 水道
     * （≈3-4 格）+ 河滩浅水缘，内段侵蚀在 s=WET_MIN 处恰好收完（heightCore 的
     * t = (s−S_ERODE)/(WET_MIN−S_ERODE)）。
     */
    public static final double S_ERODE = WET_MIN / 1.4D;

    /**
     * 外段谷带贴水余量（格）：heightCore 外段把 h0 压向 {@code poolLevel + 本值}——谷带在
     * 水面上方 1.5 格处贴平（"贴水面缓坡谷带"，修"河缘直切无河滩"的根因：旧单段 lerp 直接从
     * h0 插到床，河缘成一圈陡坎）。
     */
    public static final double RIM_EPS = 1.5D;

    /**
     * 河滩带半宽（s 域）：s ∈ [{@link #WET_MIN}, WET_MIN+本值] 且该列在水面之上的河核列，
     * 落块器铺<b>滩料</b>（沙/砾按群系档表）而非床料——水缘砂砾滩（P19 plan §A.3）。滩带
     * 的"露头"本身来自 n_bed 抬升（浅滩重释，派生条件），本档只圈定滩料适用域。
     */
    public static final double SHORE_FLAT_BAND = 0.15D;

    /**
     * 荒漠整段闸的湿段份额（v1.20.40 P19 §B）：{@code wet(seg) = hash(segKey) < 本值}——
     * 干:湿 = <b>65:35</b>（用户拍板"荒漠干段为主、符合逻辑"；BOP DryRiver 先例=干段整段
     * 连贯）。整段同值 = 同一条细胞边（同 segKey）全干或全湿，跨 chunk 一致。
     */
    public static final double DESERT_WET_SHARE = 0.35D;

    /**
     * 湿段末端面收尾的渐变列数（v1.20.40 P19 plan §B"末端 3-5 列"带内取 4）：湿核列沿边
     * 切向 {@link #END_FACE_LEN} 列内探到"面"（干段/湖水区）时，床从原深沿 smoothstep
     * 渐变抬升到 {@code poolLevel − END_FACE_BED_OFFSET}——水墙变缓坡收尾（端面列高差
     * ≤2/列；实现见 {@link #bedFromPool} 的端面修正与 {@link #endFaceBed}）。
     */
    public static final int END_FACE_LEN = 4;

    /** 端面收尾的床抬升目标：末端列床 = {@code poolLevel − 本值}（水面下 0.5 格的砾滩收口）。 */
    public static final double END_FACE_BED_OFFSET = 0.5D;

    /**
     * 结点（三细胞交汇）判域：evalBorder 的 (d3−dC) &lt; 本值×dN 时该列在三叉结点邻域
     * （横向 ≈ ±本值×dN/2 ≈ ±30 格），{@link #poolLevelAt} 对结点列取共享该结点的三条边池
     * 水位的最小者（水往低处走：结点无水墙，落差集中到边中段）。取值远小于边中段的
     * (d3−dC)/dN ≈ 0.5-1（第三近细胞在邻边另一侧），只在结点附近触发。
     */
    public static final double JUNCTION_ZONE = 0.12D;

    /**
     * 池水位锚基幅度（格，±；v1.20.40 P19 §C 校准）：{@link #poolOfPair} 的边采样取
     * {@code bedTarget + POOL_ANCHOR_OFFSET + 本值×n_bed}（n_bed ∈ (−1,1)）。<b>不复用
     * {@code bedNoiseAmp}</b>——锚基职责是"把段间水位差拉到阶梯档距两侧"（落差/跌水的来源），
     * 与床纹理幅度是两件事。幅度的硬约束：低水位桶（63）要求 min &lt; 64 ⟺ n &lt;
     * (64−66)/幅度 =&lt; 幅度必须 &gt; 2（n 值域 (−1,1)，幅度 2 时桶 63 <b>数学不可达</b>——
     * 初跑实测 pool ∈ {65:99.6%, 67:0.4%}、落差墙 0 面）。幅度 3.0 ⇒ 三桶全可达
     * （63: ~1/3 段、65: ~1/2 段、67: ~1/8 段），段间差分布 ≈ {0:38%, 2:50%, 4:12%}——
     * 落差墙每 ~8 段界一面、2 格跌水过半，阶梯观感连续（正式判据带批4 重钉）。
     */
    public static final double POOL_ANCHOR_AMP = 3.0D;

    /**
     * 池水位锚基偏移（格）：见 {@link #POOL_ANCHOR_AMP}——常态锚基面 = BED_TARGET+本值 = 66，
     * 使量化桶心 {63,65,67} 骑在 {@code SEA_LEVEL}=68 之下 1-5 格（67 桶=湖面下 1，
     * 与巨湖 {@code SEA_LEVEL} 口径衔接）。
     */
    public static final double POOL_ANCHOR_OFFSET = 1.5D;

    /**
     * 池水位阶梯档距（格，v1.20.40 P19 §C）：池水位量化到本档距的格级
     * （{@code floor(min/step)×step+1}）——连续 min 会让相邻段水位挤在 1-2 格差内
     * （初跑实测采样窗 pool ∈ {65,67}、段间差全 0/2、落差墙 0 面），显式阶梯档距使段间
     * 水位差聚到 {0,2,4}：2 格级差 = 段界小跌水（&lt; {@link #POOL_DROP} 不成墙），
     * 4 格级差 = 落差墙/瀑布。
     */
    public static final int POOL_LEVEL_STEP = 2;

    /** 段间落差墙判据（格）：相邻列池水位差 ≥ 本值 → 落差墙（{@code GTSRRiverPlacer} 消费）。 */
    public static final int POOL_DROP = 3;

    /** 域分离盐（与 {@code ProsperityTerrainProfile.CHAIN_SEED_SALT} 无关；用途间互不相关）。 */
    public static final long SALT_DISK_LARGE = 0x5249F101L;
    /** 小弯 disk 盐。 */
    public static final long SALT_DISK_SMALL = 0x5249F102L;
    /** Voronoi 细胞 x 偏移盐。 */
    public static final long SALT_CELL_X = 0x5249F103L;
    /** Voronoi 细胞 z 偏移盐。 */
    public static final long SALT_CELL_Z = 0x5249F104L;
    /** 床噪声盐。 */
    public static final long SALT_BED = 0x5249F105L;
    /**
     * 荒漠断流盐（v1.20.40 P19 §B 起改供<b>整段闸</b>：{@code hash(segKey ^ SALT_WET)} 判
     * 段干/段湿；旧 wetNoise 串珠口径已废，盐域续用同一常量）。
     */
    public static final long SALT_WET = 0x5249F107L;
    /** 段键（细胞对签名）派生盐（v1.20.40 P19 §C 新增）。 */
    public static final long SALT_SEG_KEY = 0x5249F10CL;
    /** 主干带噪声盐（T5）。 */
    public static final long SALT_TRUNK = 0x5249F108L;
    /** 巨湖 Voronoi 细胞 x 偏移盐（T5 第二 Voronoi，与河网细胞盐域分离）。 */
    public static final long SALT_LAKE_CELL_X = 0x5249F109L;
    /** 巨湖 Voronoi 细胞 z 偏移盐。 */
    public static final long SALT_LAKE_CELL_Z = 0x5249F10AL;
    /** 巨湖床噪声盐。 */
    public static final long SALT_LAKE_BED = 0x5249F10BL;
    /**
     * 巨湖破圆 domain-warp 的 disk 盐（v1.20.40 P19 §D 新增；第 4 张 disk 表，与河网两级
     * 蜿蜒/主干带的 disk 盐域分离）。
     */
    public static final long SALT_DISK_LAKE_WARP = 0x5249F10DL;
    /** 沼泽微池 Voronoi 细胞 x 偏移盐（v1.20.40 P19 §E 新增，独立第三 Voronoi）。 */
    public static final long SALT_SWAMP_POOL_X = 0x5249F10EL;
    /** 沼泽微池 Voronoi 细胞 z 偏移盐。 */
    public static final long SALT_SWAMP_POOL_Z = 0x5249F10FL;

    // ═════════════════ T5：遗忘之川主干/巨湖（plan §3.3）═════════════════

    /**
     * 主干带噪声尺度（格）：trunkNoise 取内嵌 {@link OpenSimplexDisk} 的 x 分量（波长 ≈ 1.24 单位
     * ⇒ λ ≈ 1.24×4000 ≈ 4960 ≈ 5000 格，plan §3.3 "波长约 5000 格"）。
     */
    public static final double TRUNK_SCALE = 4000.0D;

    /**
     * 主干带阈值：trunkNoise &gt; 本值即主干带。校准推导（SanzuTrunkCoverageCheck B 组实测回路，
     * 同 T4 WIDTH 的校准纪律；seed 0x5A614E5A，窗 ±2×TRUNK_SCALE，步距 SEPARATION/64）：目标
     * "主干覆盖占河网 1/6-1/8"是<b>河核列（s≥WET_MIN）口径</b>，且份额经三重门（对齐门+内带门）
     * 后实测——无门时 EDGE=0.28 ⇒ 22.3%（梯状平行河网超带）、EDGE=0.33 ⇒ 13.7%；加内带门后
     * 平行次级清零，EDGE=0.33 ⇒ 9.0%、EDGE=0.30 ⇒ 11.5%、<b>EDGE=0.27 ⇒ 12.9% ∈ [12.5%,16.7%]</b>
     * （双跑逐位一致）。T4 骨架 javadoc 的 0.28 读数是无内带门口径，已由本表取代。
     */
    public static final double SANZU_TRUNK_EDGE = 0.27D;

    /**
     * 主干<b>内带门</b>（T5 实测补的第三重门，noise 域余量：trunkNoise ≥ SANZU_TRUNK_EDGE +
     * 本值 才可能是主边界）：对齐门只按<b>方向</b>筛选——抖动格 Voronoi 的三族边界互成 ~60°，
     * 与带向同向的<b>平行族往往整族通过 45.6° 门</b>，带内成"梯状平行河网"（实测 sanzu 最大簇
     * 长短轴比 1.1-3.6，map 呈多条平行河条带联网，违反 plan §3.3"只沿单一主边界延伸"）。
     * 内带门按<b>位置</b>排他：主干带是从带缘向带脊 trunkNoise 单调升的条带，主边界（贴着带脊
     * 走的那条）的河条带整体落在高值内带里，带缘的平行次级则整条落在内带之外 ⇒ 一并清零。
     * 内带宽度 ≈ 本值/|∇trunkNoise|（实测梯度 ~3e-4/格 ⇒ 0.08 ≈ ±130 格的内带收窄），取值
     * 大到只容一条边界（边界间距 ~SEPARATION/√3≈600）穿过、小到不把主边界自身的 ±77 格宽河
     * 条带切出内带——校准读数见 SanzuTrunkCoverageCheck E 组（长短轴比 &gt;5 的验收带）。
     */
    public static final double SANZU_TRUNK_INNER = 0.08D;

    /** 主干带内河宽乘子（plan §3.3 "width×3.5"：河宽 ×3-4 带内取 3.5）。 */
    public static final double TRUNK_WIDTH_SCALE = 3.5D;

    /** 主干带内河谷深度档乘子（plan §3.3 "valleyLevel×1.3"：谷更深）。 */
    public static final double TRUNK_VALLEY_SCALE = 1.3D;

    /**
     * 主干对齐门余弦阈值（T5"少支流"机制，见 {@link #strengthAt} 主干支与 {@link #trunkAligned}）：
     * 边界法向与 trunkNoise 梯度的 |cos| ≥ 本值才保留河流强度，否则清零。v1.20.39 T5 实测定值
     * <b>0.70（≈45.6°）</b>，取代 T4 骨架 javadoc 论证的 0.45（63°）——决定性实测（SanzuTrunkCoverageCheck
     * D/E 组，seed 0x5A614E5A，窗 ±2×TRUNK_SCALE 步距 SEPARATION/64）：抖动格 Voronoi 的三族边界
     * 互成 ~60°，63° 门等于"只清 ~90° 直穿的一族"，另两族 60° 边界全保留 ⇒ 带内河网仍是二维网
     * （实测带内三叉密度仅降 3-4×、sanzu 最大簇长短轴比 1.4-3.6，远低于"细长"判据 &gt;5）。
     * 45.6° 门清零全部 ±60° 横截族；主干连续性不受威胁——Voronoi 边在结点间是<b>直线段</b>
     * （段长 ~SEPARATION/√3≈600），沿带延伸的主边界段方向与带向偏差 ~±15°（带宽 ~600-1000，
     * 穿带边界必越带而出），45.6° 有三倍裕度；穿带后带外无门，普通河照常（同网连通不破）。
     */
    public static final double TRUNK_ALIGN_COS = 0.70D;

    /** 主干梯度差分步距（格）：TRUNK_SCALE/16——梯度方向在带内数百格尺度上稳定。 */
    public static final int TRUNK_GRAD_STEP = (int) (TRUNK_SCALE / 16.0D);

    /** 巨湖 Voronoi 细胞边长（格，RTG lakeInterval 同位参数，起步 1200）。 */
    public static final double LAKE_INTERVAL = 1200.0D;

    /**
     * 巨湖破圆 domain-warp 位移尺度（格，v1.20.40 P19 plan §D 新增）：disk 噪声波长
     * ≈ 1.24×本值 ≈ 400 格——与湖径同阶 ⇒ 同一座湖内位移场显著变化，细胞圆被揉成不规则形。
     */
    public static final double LAKE_WARP_SCALE = 320.0D;

    /**
     * 巨湖破圆 domain-warp 位移幅度（格，圆盘向量全长上限；plan §D"幅度 60-80"带内取 70）：
     * {@link #lakeAt} 在湖 Voronoi 求值前对坐标加
     * {@code disk(p/LAKE_WARP_SCALE)×本值}（第 4 张 OpenSimplexDisk 表，盐
     * {@link #SALT_DISK_LAKE_WARP}）——复用河网蜿蜒同一份实现/ThreadLocal 缓存纪律。
     * 位移 ≪ 3×3 邻域窗半宽（1.5×{@link #LAKE_INTERVAL}），F1/F2 在窗内仍数学精确。
     */
    public static final double LAKE_WARP = 70.0D;

    /**
     * 巨湖水位（RTG lakeWaterLevel 同位参数）：lakePressure &lt; 本值 = 湖水区。
     * <b>v1.20.40（P19 plan §D）0.11→0.13</b>：水径 r ≈ 本值×D/(1+本值)（D=相邻湖心距
     * ≈ (0.7..1.4)×间隔）随本值近线性放大 ⇒ 湖径 ×~1.2 更大；湖水区再经
     * {@link #lakeBedAt} 中心渐深（湖心水面下 {@link #LAKE_CENTER_DEPTH} 格）。
     */
    public static final double LAKE_WATER_LEVEL = 0.13D;

    /** 巨湖湖滨带外缘（RTG lakeShoreLevel 同位参数）：[WATER, SHORE) 为床→原地形渐变带。 */
    public static final double LAKE_SHORE = 0.15D;

    /**
     * 巨湖床基准历史值（v1.20.39 T5 的平底床 63±1）。<b>v1.20.40（P19 §D）中心渐深后
     * 生产公式改由湖滨/湖心锚派生</b>（见 {@link #lakeBedAt}），本常量不再参与生成判定，
     * 保留只因 {@code tools/dim1/SanzuTrunkCoverageCheck} 的床带派生仍引用（判据批4 重钉时
     * 随 C2 带一并退役/重钉）。
     */
    public static final double LAKE_BED_TARGET = 63.0D;

    /**
     * 湖心最大水深（格，v1.20.40 P19 plan §D"湖心最深 8-12"带内取 10）：
     * {@link #lakeBedAt} 的湖心锚 = {@code SEA_LEVEL − 本值} = 58（带 56-60，旧平底床 63±1
     * 水深 4-6 → 湖心水深 8-12）；湖滨锚 = 水面下 1 格（plan §D"湖滨床≈pool−1"口径，
     * 见 {@link #lakeBedAt} 的插值说明）。
     */
    public static final double LAKE_CENTER_DEPTH = 10.0D;

    /**
     * 巨湖床噪声波长（格，v1.20.40 P19 §D 随湖径放大从 192 收 160——床纹波长与湖径保持
     * 同阶（≈湖径的 1.2 倍量级），破圆后的大湖不至于整湖一条直线纹理）。
     */
    public static final double LAKE_BED_NOISE_SCALE = 160.0D;

    /**
     * {@link #lakeAt} 的"无湖"哨兵（带外/带内无湖一律返回它）：≥ {@link #LAKE_SHORE}，与压力同向
     * （低=湖心），消费面（heightCore 压低 / 水面回填）统一判 {@code lake < LAKE_SHORE}。
     */
    public static final double NO_LAKE = 1.0D;

    /**
     * 单一 riverStyle 档（全部纯参数，无身份等值判断；sanzu 第 5 元 T5 补）。
     */
    public static final class RiverStyle {

        /** 河道宽乘子（沼泽 1.2，v1.20.40 从 1.6 收窄）。 */
        public final double widthScale;
        /** 池水位锚基（格）：{@link #poolLevelAt} 沿边采样的 bedTarget（各群系池域差异的来源）。 */
        public final double bedTarget;
        /** 床噪声幅度（±格）：n_bed 抬到 ≥ 池水位的列自然干出（浅滩重释 = 派生条件）。 */
        public final double bedNoiseAmp;
        /**
         * 河床深度档（格，v1.20.40 P19 §C 新增）：{@code bed = poolLevel − depth + n_bed}——
         * 水面恒 = poolLevel，本档决定常态水深（草原/森林 2.0 / 荒漠 2.0 / 沼泽 1.0 /
         * sanzu 2.5）。取值 &lt; {@link #bedNoiseAmp} 的档才会周期性干出浅滩
         * （草原/森林 amp=2.5 &gt; depth=2.0）。
         */
        public final double depth;
        /**
         * 浅滩档标识（草原/森林 true）。<b>v1.20.40 浅滩重释</b>：浅滩不再走独立 shoalNoise
         * 闸（已废），而是"段内 bedAt ≥ 池水位"的派生条件；本布尔仅作档表标识保留
         * （RiverMorphologyCheck A 组钉面），不参与生成判定。
         */
        public final boolean shoals;
        /** 断流档（荒漠 true：置水走 segKey 整段闸）。 */
        public final boolean wetGated;

        RiverStyle(double widthScale, double bedTarget, double bedNoiseAmp, double depth, boolean shoals,
            boolean wetGated) {
            this.widthScale = widthScale;
            this.bedTarget = bedTarget;
            this.bedNoiseAmp = bedNoiseAmp;
            this.depth = depth;
            this.shoals = shoals;
            this.wetGated = wetGated;
        }
    }

    /** 默认档（身份不可得 = 常态河；与 S-A 振幅默认档同纪律）。 */
    public static final RiverStyle DEFAULT_STYLE = new RiverStyle(1.0D, BED_TARGET, 1.5D, 2.0D, false, false);

    /**
     * riverStyle 档表（plan §3.2 + §3.3 + P19 §A.2/§C）：0 草原 / 1 森林 = 常态河
     * （depth 2.0、amp 2.5 → 周期浅滩/河滩）；2 荒漠 = 干谷断流（整段闸，湿段 35%）；
     * 3 沼泽 = 沼地河（×1.2、depth 1.0 → 水深 0-1 沼地肌理）；4 sanzu = <b>主干宽河占位档</b>
     * （v1.20.39 T5 按档表族 4→5 约定就位）：宽乘子 = {@link #TRUNK_WIDTH_SCALE}——主干带内
     * {@link #strengthAt} 对任何底档统一取 max(底档, 3.5) ⇒ 本档被带入时与主干带分支同值
     * （<b>无双乘</b>），depth 2.5（主干河更深）。生产链路 rosterIndex=4 不会从 GenLayer 链
     * 出现（selector 名册仍 4 家，plan §3.3/§5），本元供档表族长度一致与判据/后续消费面。
     */
    public static final RiverStyle[] RIVER_STYLE_BY_ROSTER = {
        /* 0 锈蚀草原 */ new RiverStyle(1.0D, BED_TARGET, 2.5D, 2.0D, true, false),
        /* 1 齿轮森林 */ new RiverStyle(1.0D, BED_TARGET, 2.5D, 2.0D, true, false),
        /* 2 黄铜荒漠 */ new RiverStyle(1.0D, BED_TARGET, 1.5D, 2.0D, false, true),
        /* 3 喷气沼泽 */ new RiverStyle(1.2D, 66.5D, 0.5D, 1.0D, false, false),
        /* 4 遗忘之川 */ new RiverStyle(TRUNK_WIDTH_SCALE, BED_TARGET, 1.5D, 2.5D, false, false) };

    private GTSRVoronoiRiverField() {}

    /** 名册下标 → riverStyle 档（越界/缺席一律默认档，无身份等值判断）。 */
    public static RiverStyle styleForRosterIndex(int rosterIndex) {
        return rosterIndex >= 0 && rosterIndex < RIVER_STYLE_BY_ROSTER.length ? RIVER_STYLE_BY_ROSTER[rosterIndex]
            : DEFAULT_STYLE;
    }

    // ═══════════════════════════ 强度场 ═══════════════════════════

    /**
     * 河流强度 rs ∈ [-1,0]（0=无影响、-1=河道中心）。<b>纯函数</b>；档表走名册面
     * （rosterIndex 选 widthScale，荒漠/沼泽等的床与置水差异见 {@link #bedAt}/{@link #wetAt}）。
     * <p>
     * ═══ T5 主干支（plan §3.3，带内 = 主边界三重门，任一不过整列清零）═══
     * <ol>
     * <li><b>宽河</b>：带内有效宽档 = max(底档, {@link #TRUNK_WIDTH_SCALE})——"河宽×3-4"对任何
     * 底档统一为 3.5×（底档沼泽 1.6 不叠加成 5.6，守住 plan"×3-4"带；两档同值还使带内 s 与
     * rosterIndex 无关，见 {@link #isSanzuColumn} 的构造不变量）；</li>
     * <li><b>少支流·对齐门</b>：边界法向与带脊（trunkNoise 梯度）夹角 &gt;45.6°
     * （|cos| &lt; {@link #TRUNK_ALIGN_COS}）的<b>横截族</b>整段清零；</li>
     * <li><b>少支流·内带门</b>：trunkNoise &lt; EDGE+{@link #SANZU_TRUNK_INNER} 的带缘列清零
     * ——对齐门只筛方向，与带向平行的整族边界照样通过（实测带内成"梯状平行河网"），内带门按
     * 位置排他、只留贴带脊的那一条主边界；带外一切照旧 ⇒ 主干与普通河网<b>同用一套 Voronoi
     * 边界</b>，天然连通（plan §3.3）；</li>
     * <li><b>性能门</b>（plan §9 ③）：c ≥ 带内最大展宽（WIDTH×3.5）的列不求值 trunk——远离边界
     * 的腹地列零主干成本。</li>
     * </ol>
     */
    public static double strengthAt(long worldSeed, int x, int z, int rosterIndex) {
        final double styleScale = styleForRosterIndex(rosterIndex).widthScale;
        // —— 1. 两级 Disk jitter 蜿蜒 + 2. Voronoi border2（P19 U8：同列的 warp+evalBorder 复合
        // 走 warpedBorder 列级 memo——strengthAt/poolLevelAt/segKeyAt/endFaceBed 同列互访
        // 不再各重算一遍；算式与逐位结果同前）——
        final BorderEval border = warpedBorder(worldSeed, x, z);
        final double c = border.c;
        // —— 3. 主干带门（性能：带内最大展宽也够不着的列不求值 trunk）——
        if (c >= WIDTH * Math.max(styleScale, TRUNK_WIDTH_SCALE)) {
            return 0.0D;
        }
        final double trunk = trunkAt(worldSeed, x, z);
        if (trunk <= 0.0D) {
            return c < WIDTH * styleScale ? c / (WIDTH * styleScale) - 1.0D : 0.0D;
        }
        // 带内 = 主边界三重门（plan §3.3"只沿单一主边界延伸"），任一不过即清零（带内无次级河）：
        // ① 宽河：任何底档统一 max(底档, TRUNK_WIDTH_SCALE)；② 对齐门（横截族清零）；
        // ③ 内带门（带缘平行族清零，见 SANZU_TRUNK_INNER）。
        if (!trunkAligned(worldSeed, x, z, border.nx, border.nz) || trunk < SANZU_TRUNK_INNER) {
            return 0.0D;
        }
        final double width = WIDTH * Math.max(styleScale, TRUNK_WIDTH_SCALE);
        // —— 4. 强度 ——
        return c / width - 1.0D;
    }

    /** 三参便捷形态（名册不可得 ⇒ 默认档；离线判据/骨架用）。 */
    public static double strengthAt(long worldSeed, int x, int z) {
        return strengthAt(worldSeed, x, z, -1);
    }

    /**
     * (x,z) 列的<b>床高</b>（double，heightCore 取 round；v1.20.40 P19 §C 起锚定本段池水位）。
     * {@code bed = poolLevel − depth(style) + n_bed(±amp)}——水面恒 = poolLevel（{@link #poolLevelAt}），
     * 深度档见 {@code RiverStyle.depth}；n_bed 抬到 ≥ 池水位的列<b>自然干出</b>为浅滩/河滩
     * （浅滩重释 = 派生条件，独立 shoalNoise 闸已废）。
     * <p>
     * 需要同列池水位做别的事（heightCore 两段式的 rim 目标）时用 {@link #bedFromPool}
     * 传入已求得的 pool，避免重复求值（本方法内部 = 一行转发）。
     */
    public static double bedAt(long worldSeed, int x, int z, int rosterIndex) {
        return bedFromPool(worldSeed, x, z, rosterIndex, poolLevelAt(worldSeed, x, z, rosterIndex));
    }

    /**
     * 池水位显式形态（供 heightCore 复用同一 pool 求 rim 与 bed，消一次重复求值；
     * 与 {@link #bedAt} 同解由构造保证——同一 pool 入参）。
     * <p>
     * <b>v1.20.40（P19 plan §B）端面收尾修正</b>：湿段（含湖滨带）末 {@link #END_FACE_LEN}
     * 列的床沿 smoothstep 渐变抬升至 {@code poolLevel − END_FACE_BED_OFFSET}（见
     * {@link #endFaceBed}）——修"湿段整段闸/湖滨交接处的垂直水墙"。修正对
     * {@link #heightAt} 消费面自动生效（heightCore 内段切床走本方法，同一真值），
     * 置水面（placer 的 {@code h < pool−1} 回填门）随床抬升自动收窄。
     */
    public static double bedFromPool(long worldSeed, int x, int z, int rosterIndex, int poolLevel) {
        final RiverStyle style = styleForRosterIndex(rosterIndex);
        double bed = poolLevel - style.depth
            + style.bedNoiseAmp
                * GTSRWorldgenHash.valueNoise(worldSeed ^ SALT_BED, x / BED_NOISE_SCALE, z / BED_NOISE_SCALE);
        // 端面收尾：只对"可能有干段面（整段闸档）或湖滨交接面（主干带内）"的湿核列求值
        if (style.wetGated || trunkAt(worldSeed, x, z) > 0.0D) {
            bed = endFaceBed(worldSeed, x, z, rosterIndex, poolLevel, bed);
        }
        return bed;
    }

    /**
     * 端面收尾床高（v1.20.40 P19 plan §B"湿段末端 3-5 列床沿程抬升"；私有，消费面仅
     * {@link #bedFromPool}）。非端面/非湿核列原样返回入参 bed。
     * <p>
     * ═══ 面检测（纯函数，跨 chunk 一致）═══ 湿核列（s ≥ {@link #WET_MIN}）沿<b>边切向</b>
     * （border2 法向的 90° 旋转 = 河道走向；两端都查）逐列探 {@link #END_FACE_LEN} 列：
     * <ul>
     * <li><b>面 A·干段端面</b>（整段闸档）：邻列换了段（segKey 不同）且不是湿核列——
     * 段界即水墙位置，靠本列一侧 {@code dist} 列进入渐变域；</li>
     * <li><b>面 B·湖滨交接面</b>（主干带内，plan §B"湖滨带部分湿列同理"）：邻列是湖水区
     * （{@code lakeAt < LAKE_WATER_LEVEL}，其水面向 = SEA_LEVEL）——河口交接处同样收缓坡；</li>
     * </ul>
     * ═══ 抬升式 ═══ {@code w = smoothstep((LEN−dist+1)/LEN)}，床 = bed + (target−bed)×w，
     * target = {@code poolLevel − END_FACE_BED_OFFSET}——末端列（dist=1）抬满到水面下
     * 0.5 格（回填门 {@code h < pool−1} 自动关水 ⇒ 端面收成一格干砾滩），只抬不降
     * （target ≤ bed 时不动）。smoothstep 防坡折，每列高差 ≤ (target−bed)/2 ≪ 2。
     */
    private static double endFaceBed(long worldSeed, int x, int z, int rosterIndex, int poolLevel, double bed) {
        final double s = -strengthAt(worldSeed, x, z, rosterIndex);
        if (s < WET_MIN) {
            return bed; // 只对湿核列收尾（干列/谷坡列的床抬了也没有水墙可灭）
        }
        final boolean gated = styleForRosterIndex(rosterIndex).wetGated;
        final boolean trunkBand = trunkAt(worldSeed, x, z) > 0.0D;
        if (!gated && !trunkBand) {
            return bed;
        }
        // P19 U8：自有段的 warp+evalBorder 复合走 warpedBorder 列级 memo——与调用链上游
        // （heightCore 的 strengthAt/poolLevelAt）同列共解一次求值；算式与逐位结果同前。
        final BorderEval border = warpedBorder(worldSeed, x, z);
        double tx = -border.nz;
        double tz = border.nx;
        final double len = Math.sqrt(tx * tx + tz * tz);
        if (len < 1.0E-9D) {
            return bed;
        }
        tx /= len;
        tz /= len;
        final long ownKey = segKeyOfCells(worldSeed, border.aX, border.aZ, border.bX, border.bZ);
        int dist = 0;
        for (int k = 1; k <= END_FACE_LEN && dist == 0; k++) {
            for (int dir = 0; dir < 2 && dist == 0; dir++) {
                final int sign = dir == 0 ? 1 : -1;
                final int nx = x + (int) Math.round(tx * k * sign);
                final int nz = z + (int) Math.round(tz * k * sign);
                if (gated && segKeyAt(worldSeed, nx, nz) != ownKey) {
                    // 同段内不重判（干湿整段同值）；换段才验"邻段是否湿核"
                    if (-strengthAt(worldSeed, nx, nz, rosterIndex) < WET_MIN || !segWetAt(worldSeed, nx, nz)) {
                        dist = k;
                    }
                }
                if (dist == 0 && trunkBand && lakeAt(worldSeed, nx, nz) < LAKE_WATER_LEVEL) {
                    dist = k; // 湖滨交接面：邻列已入湖水区
                }
            }
        }
        if (dist == 0) {
            return bed;
        }
        final double raw = (END_FACE_LEN - dist + 1) / (double) END_FACE_LEN;
        final double w = raw * raw * (3.0D - 2.0D * raw);
        final double target = poolLevel - END_FACE_BED_OFFSET;
        if (target <= bed) {
            return bed; // 床已不低于收口目标（n_bed 抬出的浅滩）：只抬不降
        }
        return bed + (target - bed) * w;
    }

    /**
     * (x,z) 列是否<b>置水资格</b>（纯函数；populate 与判据共用，无第二真值）：
     * s ≥ {@link #WET_MIN} 且——荒漠档（{@code wetGated}）过<b>整段闸</b>
     * {@code hash(segKey) < DESERT_WET_SHARE}（v1.20.40 P19 §B：同一条细胞边整段同干湿，
     * BOP DryRiver 先例；v1.20.39 的 wetNoise 串珠闸已废），非荒漠段恒真。列在水面之下
     * （h1 &lt; poolLevel−1）的最终回填门在落块器（那里才有 h1）。
     */
    public static boolean wetAt(long worldSeed, int x, int z, int rosterIndex) {
        final RiverStyle style = styleForRosterIndex(rosterIndex);
        final double s = -strengthAt(worldSeed, x, z, rosterIndex);
        if (s < WET_MIN) {
            return false;
        }
        if (!style.wetGated) {
            return true;
        }
        return segWetAt(worldSeed, x, z);
    }

    // ═════════════════ v1.20.40（P19 plan §B/§C）：段键 / 池水位阶梯 / 整段闸 ═════════════════

    /**
     * <b>段键 segKey</b>（细胞对签名）：该列所在 Voronoi 边（最近+次近细胞对）的确定性
     * 签名——同一条边上的列同 segKey ⇒ "段"= 边，细胞段长 ≈ SEPARATION/√3 ≈ 600 格
     * （G1 调查口径）。细胞对无序规范化（按格点字典序）后经 {@link GTSRWorldgenHash}
     * 混淆，对 (A,B) 与 (B,A) 同值；三叉结点邻域的对随距离翻转属构造性质（结点裁决在
     * {@link #poolLevelAt}）。<b>纯函数</b>；warp 式与 {@link #strengthAt} 逐字同一。
     */
    public static long segKeyAt(long worldSeed, int x, int z) {
        final int idx = slotIndex(worldSeed, x, z, MEMO_MASK);
        final LongSlot s = SEGKEY_MEMO.get()[idx];
        if (s.valid && s.seed == worldSeed && s.x == x && s.z == z) {
            return s.val;
        }
        final long v = segKeyAt0(worldSeed, x, z);
        s.seed = worldSeed;
        s.x = x;
        s.z = z;
        s.val = v;
        s.valid = true;
        return v;
    }

    /** segKeyAt 原始求值体（P19 U8 起由 {@link #segKeyAt} 的列级 memo 包裹；算式一字未动）。 */
    private static long segKeyAt0(long worldSeed, int x, int z) {
        final BorderEval border = warpedBorder(worldSeed, x, z);
        return segKeyOfCells(worldSeed, border.aX, border.aZ, border.bX, border.bZ);
    }

    /** 细胞对 → 确定性段键（无序规范化：格点字典序小者在前；splitmix64 终混防对称碰撞）。 */
    private static long segKeyOfCells(long worldSeed, int aX, int aZ, int bX, int bZ) {
        int loX = aX;
        int loZ = aZ;
        int hiX = bX;
        int hiZ = bZ;
        if (aX > bX || (aX == bX && aZ > bZ)) {
            loX = bX;
            loZ = bZ;
            hiX = aX;
            hiZ = aZ;
        }
        final long k1 = GTSRWorldgenHash.splitmix64(GTSRWorldgenHash.cellSeed(worldSeed, loX, loZ, SALT_SEG_KEY));
        final long k2 = GTSRWorldgenHash.splitmix64(GTSRWorldgenHash.cellSeed(worldSeed, hiX, hiZ, SALT_SEG_KEY));
        return GTSRWorldgenHash.splitmix64(k1 ^ Long.rotateLeft(k2, 32));
    }

    /**
     * <b>段池水位</b>（int 格；v1.20.40 P19 plan §C 核心）：该列所在边的<b>纯函数水位</b>——
     * 段内（同 segKey）所有列同值 ⇒ 跨 chunk 接缝一致；段与段之间水位成<b>阶梯</b>，
     * 差 ≥ {@link #POOL_DROP} 的段界由落块器回填成落差墙/瀑布。
     * <p>
     * ═══ 取值式 ═══ 对边两端点与中点（t=0.2/0.5/0.8，端点坐标 = A、B 细胞中心 + 确定性
     * 线性 offset 重构）采样锚基 {@code bedTarget + POOL_ANCHOR_OFFSET ± POOL_ANCHOR_AMP}，
     * 取 min 后按 {@link #POOL_LEVEL_STEP} 量化成阶梯级（"池底最低点抬 1 格 = 水面"）。
     * 采样点由细胞对唯一确定 ⇒ 同段同值与列坐标无关。
     * <p>
     * ═══ 结点裁决 ═══ 列落在三叉结点邻域（{@code (d3−dC) < JUNCTION_ZONE×dN}，
     * {@link BorderEval} 的第三近细胞随 c 同次带出）时，取共享该结点的三条边
     * (A,B)/(A,C)/(B,C) 池水位的<b>最小者</b>——水往低池走：结点不成水墙，落差集中到
     * 边中段（三细胞由 nearestCellHash 同源的 evalBorder 确定性给出）。池水位差 0-2 的
     * 邻段交界只是 1-2 格小跌水（&lt; POOL_DROP，不触发落差墙口径），结点域边界处的
     * 水位台阶 ≤ 同一量级。
     * <p>
     * rosterIndex 只影响采样档（{@code bedTarget} 档），同段内跨粗格群系边界的池差被档差
     * （常态 64.5 vs 沼泽 66.5）限制在 ~2 格——按 {@link #POOL_DROP}=3 之下，不触发墙。
     * 池水位经 {@link #POOL_LEVEL_STEP} 量化成阶梯级（段间差 ∈ {0,2,4}）。
     */
    public static int poolLevelAt(long worldSeed, int x, int z, int rosterIndex) {
        final int idx = slotIndex(worldSeed, x, z, rosterIndex, MEMO_MASK);
        final IntSlot s = POOL_MEMO.get()[idx];
        if (s.valid && s.seed == worldSeed && s.x == x && s.z == z && s.roster == rosterIndex) {
            return s.val;
        }
        final int v = poolLevelAt0(worldSeed, x, z, rosterIndex);
        s.seed = worldSeed;
        s.x = x;
        s.z = z;
        s.roster = rosterIndex;
        s.val = v;
        s.valid = true;
        return v;
    }

    /** poolLevelAt 原始求值体（P19 U8 起由 {@link #poolLevelAt} 的列级 memo 包裹；键含 rosterIndex）。 */
    private static int poolLevelAt0(long worldSeed, int x, int z, int rosterIndex) {
        // P19 U8：warp+evalBorder 复合走 warpedBorder（与 strengthAt 同列共解一次求值）。
        final BorderEval border = warpedBorder(worldSeed, x, z);
        // 结点裁决期间不再触发 evalBorder ⇒ border 字段稳定；仍按本类"取完即拷"纪律提为局部。
        final int aX = border.aX, aZ = border.aZ, bX = border.bX, bZ = border.bZ, cX = border.cX, cZ = border.cZ;
        final double dC = border.dC, dN = border.dN, d3 = border.d3;
        final RiverStyle style = styleForRosterIndex(rosterIndex);
        int pool = poolOfPair(worldSeed, style, aX, aZ, bX, bZ);
        if (d3 - dC < JUNCTION_ZONE * dN) {
            final int poolAC = poolOfPair(worldSeed, style, aX, aZ, cX, cZ);
            if (poolAC < pool) {
                pool = poolAC;
            }
            final int poolBC = poolOfPair(worldSeed, style, bX, bZ, cX, cZ);
            if (poolBC < pool) {
                pool = poolBC;
            }
        }
        return pool;
    }

    /**
     * 一条细胞边（A,B）的池水位：细胞中心（格点锚定 + 确定性 jitter）连线上 t∈{0.2,0.5,0.8}
     * 三点采样锚基（端点+中点；边长 ~600 格 ⇒ 采样间距 ~240 格 &gt; 床噪声波长 96 格，
     * 三点近独立），min 后按 {@link #POOL_LEVEL_STEP} 量化（锚基式见 {@link #POOL_ANCHOR_AMP}）。
     */
    private static int poolOfPair(long worldSeed, RiverStyle style, int aX, int aZ, int bX, int bZ) {
        final double cax = aX * SEPARATION + cellOffset(worldSeed, aX, aZ, SALT_CELL_X, SEPARATION);
        final double caz = aZ * SEPARATION + cellOffset(worldSeed, aX, aZ, SALT_CELL_Z, SEPARATION);
        final double cbx = bX * SEPARATION + cellOffset(worldSeed, bX, bZ, SALT_CELL_X, SEPARATION);
        final double cbz = bZ * SEPARATION + cellOffset(worldSeed, bX, bZ, SALT_CELL_Z, SEPARATION);
        double min = Double.POSITIVE_INFINITY;
        for (int k = 0; k < POOL_SAMPLE_T.length; k++) {
            final double t = POOL_SAMPLE_T[k];
            final double px = cax + (cbx - cax) * t;
            final double pz = caz + (cbz - caz) * t;
            final double base = style.bedTarget + POOL_ANCHOR_OFFSET
                + POOL_ANCHOR_AMP
                    * GTSRWorldgenHash.valueNoise(worldSeed ^ SALT_BED, px / BED_NOISE_SCALE, pz / BED_NOISE_SCALE);
            if (base < min) {
                min = base;
            }
        }
        return (int) Math.floor(min / POOL_LEVEL_STEP) * POOL_LEVEL_STEP + 1;
    }

    /** 池水位边采样参数（两端点近旁 + 中点，见 {@link #poolOfPair}）。 */
    private static final double[] POOL_SAMPLE_T = { 0.2D, 0.5D, 0.8D };

    /**
     * 荒漠<b>整段闸</b>（段干/段湿）：{@code hash(segKey ^ SALT_WET)} 高 53 位归一后
     * &lt; {@link #DESERT_WET_SHARE} ⇒ 湿段。同段（同 segKey）所有列同值 ⇒ 干段整段连贯、
     * 湿段整段有水，跨 chunk 一致。
     */
    public static boolean segWetAt(long worldSeed, int x, int z) {
        final long h = GTSRWorldgenHash.splitmix64(segKeyAt(worldSeed, x, z) ^ SALT_WET);
        return (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR < DESERT_WET_SHARE;
    }

    // ═════════════════ T5（v1.20.39，plan §3.3）：主干带 / 巨湖 / sanzu 列谓词 ═════════════════

    /**
     * 主干带标记 trunk(x,z) = max(0, trunkNoise − {@link #SANZU_TRUNK_EDGE})（plan §3.3 已定机制）。
     * trunkNoise = 内嵌 {@link OpenSimplexDisk}（第 3 张表，盐 {@link #SALT_TRUNK}）在
     * {@link #TRUNK_SCALE} 尺度上的 x 分量，波长约 5000 格 ⇒ 带状高值区沿噪声脊线蜿蜒成
     * "主干带"。<b>纯函数</b>；带内语义（宽河 ×3.5 / 谷 ×1.3 / 对齐门 / 巨湖激活）见
     * {@link #strengthAt} 主干支与 {@link #lakeAt}。
     */
    public static double trunkAt(long worldSeed, int x, int z) {
        final int idx = slotIndex(worldSeed, x, z, MEMO_MASK);
        final DblSlot s = TRUNK_MEMO.get()[idx];
        if (s.valid && s.seed == worldSeed && s.x == x && s.z == z) {
            return s.val;
        }
        final double v = trunkAt0(worldSeed, x, z);
        s.seed = worldSeed;
        s.x = x;
        s.z = z;
        s.val = v;
        s.valid = true;
        return v;
    }

    /** trunkAt 原始求值体（P19 U8 起由 {@link #trunkAt} 的列级 memo 包裹；算式一字未动）。 */
    private static double trunkAt0(long worldSeed, int x, int z) {
        final double[] buf = diskBuffer();
        final double n = trunkNoiseRaw(worldSeed, x, z, buf);
        return n > SANZU_TRUNK_EDGE ? n - SANZU_TRUNK_EDGE : 0.0D;
    }

    /** trunkNoise 原值（disk x 分量；buf 复用 {@link #DISK_BUF}，调用方不得跨下次 disk 调用持有）。 */
    private static double trunkNoiseRaw(long worldSeed, int x, int z, double[] buf) {
        diskAt(worldSeed, SLOT_DISK_TRUNK, TRUNK_SCALE, x, z, buf);
        return buf[0];
    }

    /**
     * <b>主干对齐门（"少支流"机制）</b>：主干带内只保留沿带脊方向延伸的边界。判据 =
     * 边界法向（{@code (nx,nz)}：p' 指向最近 Voronoi 细胞中心的单位向量，即 c 的梯度向）
     * 与 trunkNoise 梯度（前向差分，步距 {@link #TRUNK_GRAD_STEP}——梯度方向在带内数百格尺度上
     * 稳定）的 |cos| ≥ {@link #TRUNK_ALIGN_COS}；横截主干带的次级边界（|cos|≈0）整段清零。
     * 几何论证见 {@link #TRUNK_ALIGN_COS} javadoc（三叉结点 60° 角距 ⇒ 主干恒有保留出边、无断头）。
     */
    private static boolean trunkAligned(long worldSeed, int x, int z, double nx, double nz) {
        final double[] buf = diskBuffer();
        final double n0 = trunkNoiseRaw(worldSeed, x, z, buf);
        final double gx = trunkNoiseRaw(worldSeed, x + TRUNK_GRAD_STEP, z, buf) - n0;
        final double gz = trunkNoiseRaw(worldSeed, x, z + TRUNK_GRAD_STEP, buf) - n0;
        final double len = Math.sqrt(gx * gx + gz * gz);
        if (len < 1.0E-9D) {
            return false; // 带内鞍点：无带脊方向可对齐 ⇒ 不保留（带边缘极窄环，主干主体不受影响）
        }
        return Math.abs((nx * gx + nz * gz) / len) >= TRUNK_ALIGN_COS;
    }

    /**
     * 巨湖压力场（T5，plan §3.3；<b>v1.20.40 P19 §D 破圆</b>）：第二 Voronoi（{@link #LAKE_INTERVAL}
     * 格、{@link #SALT_LAKE_CELL_X}/{@link #SALT_LAKE_CELL_Z} 盐域分离、偏移幅度同
     * {@link #CELL_JITTER}×间隔），压力 = dC/dN ∈ [0,1)——<b>低值在细胞中心</b>（与河网的
     * 河网 border2 低值在边界相反）⇒ 每细胞腹地一座<b>离散巨湖</b>（c_lake&lt;{@link #LAKE_WATER_LEVEL}
     * 即湖水区，水径 ≈ 本值×D/(1+本值)，D ≈ (0.7..1.4)×间隔）。求值前对坐标加一次
     * domain-warp（{@link #LAKE_WARP_SCALE}/{@link #LAKE_WARP}，第 4 张 disk 表）——
     * 湖形从细胞圆破成不规则形，湖径也随揉动逐湖涨落。
     * <p>
     * <b>性能门（plan §9 ③）</b>：仅主干带内求值——trunk ≤ 0 一律返回 {@link #NO_LAKE} 哨兵
     * （第二 Voronoi 一个字都不算），"巨湖场仅主干带内激活"与性能约束同一条判。
     */
    public static double lakeAt(long worldSeed, int x, int z) {
        final int idx = slotIndex(worldSeed, x, z, MEMO_MASK);
        final DblSlot s = LAKE_MEMO.get()[idx];
        if (s.valid && s.seed == worldSeed && s.x == x && s.z == z) {
            return s.val;
        }
        final double v = lakeAt0(worldSeed, x, z);
        s.seed = worldSeed;
        s.x = x;
        s.z = z;
        s.val = v;
        s.valid = true;
        return v;
    }

    /** lakeAt 原始求值体（P19 U8 起由 {@link #lakeAt} 的列级 memo 包裹；算式一字未动）。 */
    private static double lakeAt0(long worldSeed, int x, int z) {
        if (trunkAt(worldSeed, x, z) <= 0.0D) {
            return NO_LAKE;
        }
        // v1.20.40 P19 §D 破圆：湖 Voronoi 输入坐标先揉一次 disk 位移（幅度/波长见常量注释）；
        // 位移 ≪ 3×3 窗半宽（1.5×LAKE_INTERVAL）⇒ F1/F2 窗内仍数学精确。buf 取完即拷出，
        // 不跨后续调用持有（DISK_BUF 单缓冲纪律）。
        final double[] buf = diskBuffer();
        diskAt(worldSeed, SLOT_DISK_LAKE_WARP, LAKE_WARP_SCALE, x, z, buf);
        final double px = x + buf[0] * LAKE_WARP;
        final double pz = z + buf[1] * LAKE_WARP;
        final int cellX = (int) Math.floor(px / LAKE_INTERVAL + 0.5D);
        final int cellZ = (int) Math.floor(pz / LAKE_INTERVAL + 0.5D);
        double dC = Double.POSITIVE_INFINITY;
        double dN = Double.POSITIVE_INFINITY;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int gx = cellX + dx;
                final int gz = cellZ + dz;
                final double ex = gx * LAKE_INTERVAL + cellOffset(worldSeed, gx, gz, SALT_LAKE_CELL_X, LAKE_INTERVAL)
                    - px;
                final double ez = gz * LAKE_INTERVAL + cellOffset(worldSeed, gx, gz, SALT_LAKE_CELL_Z, LAKE_INTERVAL)
                    - pz;
                final double d = Math.sqrt(ex * ex + ez * ez);
                if (d < dC) {
                    dN = dC;
                    dC = d;
                } else if (d < dN) {
                    dN = d;
                }
            }
        }
        return dC / dN;
    }

    /**
     * 巨湖床高（两参便捷形态；显式形态见 {@link #lakeBedAt(long, int, int, double)}）。
     */
    public static double lakeBedAt(long worldSeed, int x, int z) {
        return lakeBedAt(worldSeed, x, z, lakeAt(worldSeed, x, z));
    }

    /**
     * 巨湖床高——<b>v1.20.40（P19 plan §D）中心渐深</b>：床沿湖压力从湖滨锚渐变到湖心锚。
     * <ul>
     * <li><b>湖滨锚</b> = {@code SEA_LEVEL − 1}（plan §D"湖滨床≈pool−1"口径：巨湖水面恒
     * = SEA_LEVEL=68，水缘处床贴水面下 1 格）；</li>
     * <li><b>湖心锚</b> = {@code SEA_LEVEL − LAKE_CENTER_DEPTH} = 58（湖心水深 10，
     * 带 8-12；旧平底床 63±1 水深 4-6 → 湖心可达 56-60）；</li>
     * <li><b>插值域</b> = 湖水区压力 [0, {@link #LAKE_WATER_LEVEL}]，smoothstep 防坡折——
     * 水缘（lake=WATER）床恰为湖滨锚，湖滨带 [WATER, SHORE) 的 heightCore 渐变从同一值
     * 接续抬回原地形（水缘两侧连续，无坡折）；</li>
     * <li>床纹 = ±1 低频 valueNoise（波长 {@link #LAKE_BED_NOISE_SCALE}）叠在渐变之上。</li>
     * </ul>
     * lakePressure 显式形态供 heightCore 复用同一次 {@link #lakeAt} 求值（消重复）；
     * 压力 ≥ WATER（湖滨带）时 u 饱和为 1 ⇒ 床 = 湖滨锚 ±1，与水缘列同解。
     */
    public static double lakeBedAt(long worldSeed, int x, int z, double lakePressure) {
        final double seaLevel = ProsperityTerrainProfile.SEA_LEVEL;
        final double shoreBed = seaLevel - 1.0D;
        final double centerBed = seaLevel - LAKE_CENTER_DEPTH;
        final double u = Math.min(1.0D, Math.max(0.0D, lakePressure / LAKE_WATER_LEVEL));
        final double g = 1.0D - u * u * (3.0D - 2.0D * u);
        return shoreBed + (centerBed - shoreBed) * g
            + GTSRWorldgenHash
                .valueNoise(worldSeed ^ SALT_LAKE_BED, x / LAKE_BED_NOISE_SCALE, z / LAKE_BED_NOISE_SCALE);
    }

    // ═════════════════ v1.20.40（P19 plan §E）：沼泽微池（第二激活档） ═════════════════

    /**
     * 沼泽微池 Voronoi 细胞边长（格，plan §E"interval≈220"）：roster 3（喷气沼泽）的
     * 独立低频微池场，与河网/巨湖的 Voronoi 互不相干（盐域分离）。
     */
    public static final double SWAMP_POOL_INTERVAL = 220.0D;

    /**
     * 沼泽微池水位阈值：pressure = dC/dN &lt; 本值 = 微池水域。水径 r ≈ 本值×D/(1+本值)
     * （D=相邻池心距 ≈ (0.7..1.4)×{@link #SWAMP_POOL_INTERVAL}）⇒ r ≈ 8.0-16.0 格
     * （plan §E"微池半径 8-16"的带内定值：0.055×154/1.055 ≈ 8.0、0.055×308/1.055 ≈ 16.1）。
     */
    public static final double SWAMP_POOL_WATER_LEVEL = 0.055D;

    /** {@link #swampLakeAt} 的"无微池"哨兵（非沼泽 roster/带外一律返回它；与 {@link #NO_LAKE} 同向）。 */
    public static final double NO_SWAMP_POOL = 1.0D;

    /** 微池床低频纹理波长（格；池径同阶，池底起伏 ±0.5 的波长）。 */
    public static final double SWAMP_POOL_BED_SCALE = 48.0D;

    /**
     * 沼泽微池压力场（plan §E 第二激活档）：roster 3 专属的独立低频 Voronoi（3×3 邻域
     * Worley，压力 = dC/dN，低值在池心——与巨湖同形、间隔/盐/阈值不同）。非沼泽 roster
     * 一律 {@link #NO_SWAMP_POOL}（零成本短路；roster 门在参——本类不依赖 GenLayer 身份面，
     * 由消费面传入 {@code rosterIndexCached} / placer tierGrid 的同源下标）。<b>纯函数</b>。
     */
    public static double swampLakeAt(long worldSeed, int x, int z, int rosterIndex) {
        if (rosterIndex != 3) {
            return NO_SWAMP_POOL;
        }
        final int idx = slotIndex(worldSeed, x, z, MEMO_MASK);
        final DblSlot s = SWAMP_MEMO.get()[idx];
        if (s.valid && s.seed == worldSeed && s.x == x && s.z == z) {
            return s.val;
        }
        final double v = swampLakeAt0(worldSeed, x, z, rosterIndex);
        s.seed = worldSeed;
        s.x = x;
        s.z = z;
        s.val = v;
        s.valid = true;
        return v;
    }

    /** swampLakeAt 原始求值体（P19 U8 起由 {@link #swampLakeAt} 的列级 memo 包裹；roster≠3 常量路不进 memo）。 */
    private static double swampLakeAt0(long worldSeed, int x, int z, int rosterIndex) {
        if (rosterIndex != 3) {
            return NO_SWAMP_POOL;
        }
        final int cellX = (int) Math.floor(x / SWAMP_POOL_INTERVAL + 0.5D);
        final int cellZ = (int) Math.floor(z / SWAMP_POOL_INTERVAL + 0.5D);
        double dC = Double.POSITIVE_INFINITY;
        double dN = Double.POSITIVE_INFINITY;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int gx = cellX + dx;
                final int gz = cellZ + dz;
                final double ex = gx * SWAMP_POOL_INTERVAL
                    + cellOffset(worldSeed, gx, gz, SALT_SWAMP_POOL_X, SWAMP_POOL_INTERVAL)
                    - x;
                final double ez = gz * SWAMP_POOL_INTERVAL
                    + cellOffset(worldSeed, gx, gz, SALT_SWAMP_POOL_Z, SWAMP_POOL_INTERVAL)
                    - z;
                final double d = Math.sqrt(ex * ex + ez * ez);
                if (d < dC) {
                    dN = dC;
                    dC = d;
                } else if (d < dN) {
                    dN = d;
                }
            }
        }
        return dC / dN;
    }

    /**
     * sanzu 群系指派的最小河强（plan §3.3「列满足 trunk&gt;0 且 s ≥ 0.7 且 h1 ≤ 68」的 0.7 档）。
     */
    public static final double SANZU_BIOME_STRENGTH = 0.7D;

    /**
     * <b>sanzu 列谓词（单点真值）</b>，两支（plan §3.3 河道支 + v1.20.40 P19 §D 湖面支）：
     * <ol>
     * <li><b>河道支</b>（plan §3.3 逐字）：trunk &gt; 0 且 s ≥ {@link #SANZU_BIOME_STRENGTH} 且
     * {@code heightAt ≤ SEA_LEVEL}；</li>
     * <li><b>湖面支</b>（P19 §D"湖列写 sanzu"）：trunk &gt; 0 且 {@code lakeAt < LAKE_WATER_LEVEL}
     * （湖水区）且 {@code heightAt ≤ SEA_LEVEL}——heightCore 湖段压低保证湖水区列
     * h ≤ 床 ≤ 水面−1 &lt; SEA_LEVEL，两支与回填/空气压缩机消费面同解（air 走同一谓词
     * 自动生效——余汽域扩到湖面 = 拍板合意：深渊执念湖面收集三途余汽）。</li>
     * </ol>
     * 两个消费面——{@code ChunkProviderProsperityRuins.onPopulate} 的平面写入与本谓词是
     * <b>同一份实现</b>（写什么列、空气压缩机就认什么列，无第二真值）；{@code ProsperityAirLookup}
     * 因链身份面（GenLayer 链 4 家 selector）永远解析不到 populate 后置写入的第 5 群系而复用它。
     * <p>
     * rosterIndex 不带参是<b>构造不变量</b>而非省略：主干带内 strengthAt 的有效宽档 =
     * max(底档, {@link #TRUNK_WIDTH_SCALE})，对全部名册档（1.0/1.2）与缺席档（-1）同为 3.5
     * ⇒ 带内 s 与名册无关，-1 与生产档同解。h1 用 {@code ProsperityTerrainProfile.heightAt}
     * 整链（含河谷压低与巨湖压低——本谓词所在包引用 Profile 的先例 = {@link GTSRRiverPlacer}）。
     */
    public static boolean isSanzuColumn(long worldSeed, int x, int z) {
        if (trunkAt(worldSeed, x, z) <= 0.0D) {
            return false;
        }
        if (-strengthAt(worldSeed, x, z, -1) >= SANZU_BIOME_STRENGTH) {
            return ProsperityTerrainProfile.heightAt(worldSeed, x, z) <= ProsperityTerrainProfile.SEA_LEVEL;
        }
        // 湖面支：主干带内湖水区列（湖形经 domain-warp 破圆）——heightAt 门只对候选列求值
        return lakeAt(worldSeed, x, z) < LAKE_WATER_LEVEL
            && ProsperityTerrainProfile.heightAt(worldSeed, x, z) <= ProsperityTerrainProfile.SEA_LEVEL;
    }

    // ═══════════════════ Voronoi border2（RTG VoronoiCellOctave 同形） ═══════════════════

    /**
     * border2 一次求值的复合结果（v1.20.40 P19 §C 重构：最近/次近/第三近细胞随 c 一起带出，
     * 供 {@link #segKeyAt}（细胞对签名）与 {@link #poolLevelAt}（结点三边裁决）复用同一次
     * 3×3 Worley——不再各自重算）。字段语义：
     * <ul>
     * <li>{@code c} = (dN−dC)/dN ∈ [0,1)：0=细胞边界、→1=细胞腹地；</li>
     * <li>{@code (nx,nz)} = p' 指向最近细胞中心的单位向量（= c 梯度向，边界法向；主干
     * 对齐门用）；</li>
     * <li>{@code (aX,aZ)} 最近细胞格点、{@code (bX,bZ)} 次近、{@code (cX,cZ)} 第三近——
     * 同距平手按扫描序（dz 外圈、dx 内圈，严格小于才替换）决出，<b>确定性</b>；</li>
     * <li>{@code dC/dN/d3} 对应三条距离（格）。</li>
     * </ul>
     * 格点锚定偏移（|offset| ≤ 0.35×separation &lt; 半格）保证 F1/F2（以及第三近的取值域）
     * 在 3×3 窗内数学精确。线程纪律：实例由 {@link #EVAL_BUF} 线程私有复用（生成域单线程，
     * 与 {@link #DISK_BUF} 同一条纪律），<b>调用方不得跨下一次 evalBorder 持有</b>。
     */
    static final class BorderEval {

        double c;
        double dC;
        double dN;
        double d3;
        double nx;
        double nz;
        int aX;
        int aZ;
        int bX;
        int bZ;
        int cX;
        int cZ;
    }

    private static final ThreadLocal<BorderEval> EVAL_BUF = ThreadLocal.withInitial(BorderEval::new);

    /**
     * 对 warped 坐标 p' 做一次 3×3 邻域 Worley（最近/次近/第三近 + 法向 + c）。
     * 细胞中心偏移 = splitmix64(cellSeed(seed, 格点, 盐)) 的两个高 53 位，各映
     * [-0.35, +0.35]×separation。
     */
    private static BorderEval evalBorder(long worldSeed, double px, double pz) {
        final BorderEval e = EVAL_BUF.get();
        final int cellX = (int) Math.floor(px / SEPARATION + 0.5D);
        final int cellZ = (int) Math.floor(pz / SEPARATION + 0.5D);
        double dC = Double.POSITIVE_INFINITY;
        double dN = Double.POSITIVE_INFINITY;
        double d3 = Double.POSITIVE_INFINITY;
        double bestEx = 0.0D;
        double bestEz = 0.0D;
        int aX = cellX;
        int aZ = cellZ;
        int bX = cellX;
        int bZ = cellZ;
        int cX = cellX;
        int cZ = cellZ;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int gx = cellX + dx;
                final int gz = cellZ + dz;
                final double ox = cellOffset(worldSeed, gx, gz, SALT_CELL_X, SEPARATION);
                final double oz = cellOffset(worldSeed, gx, gz, SALT_CELL_Z, SEPARATION);
                final double ex = gx * SEPARATION + ox - px;
                final double ez = gz * SEPARATION + oz - pz;
                final double d = Math.sqrt(ex * ex + ez * ez);
                if (d < dC) {
                    d3 = dN;
                    cX = bX;
                    cZ = bZ;
                    dN = dC;
                    bX = aX;
                    bZ = aZ;
                    dC = d;
                    aX = gx;
                    aZ = gz;
                    bestEx = ex;
                    bestEz = ez;
                } else if (d < dN) {
                    d3 = dN;
                    cX = bX;
                    cZ = bZ;
                    dN = d;
                    bX = gx;
                    bZ = gz;
                } else if (d < d3) {
                    d3 = d;
                    cX = gx;
                    cZ = gz;
                }
            }
        }
        e.dC = dC;
        e.dN = dN;
        e.d3 = d3;
        e.aX = aX;
        e.aZ = aZ;
        e.bX = bX;
        e.bZ = bZ;
        e.cX = cX;
        e.cZ = cZ;
        if (dC > 0.0D) {
            e.nx = bestEx / dC;
            e.nz = bestEz / dC;
        } else {
            e.nx = 0.0D;
            e.nz = 0.0D;
        }
        e.c = (dN - dC) / dN;
        return e;
    }

    /**
     * 两级 Disk 蜿蜒的统一 warp（strengthAt / {@link #segKeyAt} / {@link #poolLevelAt} /
     * {@link #nearestCellHash} 四个入口共用同一条 warp 式 ⇒ 细胞对判定全仓同解）。
     * 返回 via out[0]/out[1]（复用 {@link #DISK_BUF} 纪律）。
     */
    private static double[] warp(long worldSeed, int x, int z) {
        final double[] disk = diskBuffer();
        diskAt(worldSeed, SLOT_DISK_LARGE, LARGE_BEND_SCALE, x, z, disk);
        double px = x + disk[0] * LARGE_BEND;
        double pz = z + disk[1] * LARGE_BEND;
        diskAt(worldSeed, SLOT_DISK_SMALL, SMALL_BEND_SCALE, x, z, disk);
        px += disk[0] * SMALL_BEND;
        pz += disk[1] * SMALL_BEND;
        disk[0] = px;
        disk[1] = pz;
        return disk;
    }

    // ═════════════════ P19 U8（plan §J 性能批）：列级纯函数 memo ═════════════════
    //
    // 本类全部公开求值（trunkAt/lakeAt/segKeyAt/poolLevelAt/swampLakeAt 与 warp+evalBorder 复合）
    // 都是 (worldSeed, x, z[, rosterIndex]) 的<b>纯函数</b>——同键必得逐位同值。生产链路里同一列的
    // 这些量会被重复求值 2-8 次（heightCore 的 strengthAt→poolLevelAt→bedFromPool→endFaceBed 互访、
    // placer 的 s/wet/pool 与 heightAt 内链、provider 的 lakeAt/swampLakeAt/isSanzuColumn 各趟、
    // endFaceBed 的段界射线与其邻列互访），memo 只是记忆化：
    // <ul>
    // <li><b>逐位一致</b>：命中返回的 double/long/int 与未命中重算的原始求值体（*At0）逐位相同；
    // 探针 {@code temp/p19-u8/ParityProbe}（16 seed × ≈4.1 万列/seed，heightAt/strengthAt/
    // wetAt/poolLevelAt/lakeAt/segWetAt/isSanzuColumn/trunkAt 全函数 digest）前后对拍为证；</li>
    // <li><b>ThreadLocal 不跨线程</b>（沿用本类 EVAL_BUF/DISK_CACHE 同一条线程纪律）；</li>
    // <li><b>淘汰有界</b>：全部为直接映射定长表——容量固定、槽位按精确键覆盖，无需清空逻辑；
    // 键全字段精确比较（seed/x/z[/roster]），冲突即淘汰重算，无假命中；</li>
    // <li><b>warp+evalBorder 复合</b>（{@link #warpedBorder}）：命中把槽内快照拷回 EVAL_BUF 返回，
    // 未命中求值后把 EVAL_BUF 快照存槽——调用方拿到的永远是 EVAL_BUF 实例（原 buffer 纪律
    // "不跨下一次调用持有"不变）。</li>
    // </ul>

    /** double 列槽（trunkAt/lakeAt/swampLakeAt）。 */
    private static final class DblSlot {

        long seed;
        int x;
        int z;
        boolean valid;
        double val;
    }

    /** long 列槽（segKeyAt）。 */
    private static final class LongSlot {

        long seed;
        long val;
        int x;
        int z;
        boolean valid;
    }

    /** int 列槽（poolLevelAt；rosterIndex 是取值键的一部分，须精确入比）。 */
    private static final class IntSlot {

        long seed;
        int x;
        int z;
        int roster;
        int val;
        boolean valid;
    }

    /** warp+evalBorder 复合快照槽（字段与 {@link BorderEval} 一一对应）。 */
    private static final class BorderSlot {

        long seed;
        int x;
        int z;
        boolean valid;
        double c;
        double dC;
        double dN;
        double d3;
        double nx;
        double nz;
        int aX;
        int aZ;
        int bX;
        int bZ;
        int cX;
        int cZ;
    }

    private static final int MEMO_CAP = 1024;
    private static final int MEMO_MASK = MEMO_CAP - 1;
    /** border 复合槽减半（14 字段 × 1024 ≈ 120KB/线程，512 已覆盖生产 18×18+环的工作集）。 */
    private static final int BORDER_MEMO_MASK = 511;

    private static DblSlot[] dblSlots() {
        final DblSlot[] a = new DblSlot[MEMO_CAP];
        for (int i = 0; i < a.length; i++) {
            a[i] = new DblSlot();
        }
        return a;
    }

    private static LongSlot[] longSlots() {
        final LongSlot[] a = new LongSlot[MEMO_CAP];
        for (int i = 0; i < a.length; i++) {
            a[i] = new LongSlot();
        }
        return a;
    }

    private static IntSlot[] intSlots() {
        final IntSlot[] a = new IntSlot[MEMO_CAP];
        for (int i = 0; i < a.length; i++) {
            a[i] = new IntSlot();
        }
        return a;
    }

    private static BorderSlot[] borderSlots() {
        final BorderSlot[] a = new BorderSlot[BORDER_MEMO_MASK + 1];
        for (int i = 0; i < a.length; i++) {
            a[i] = new BorderSlot();
        }
        return a;
    }

    private static final ThreadLocal<DblSlot[]> TRUNK_MEMO = ThreadLocal.withInitial(GTSRVoronoiRiverField::dblSlots);
    private static final ThreadLocal<DblSlot[]> LAKE_MEMO = ThreadLocal.withInitial(GTSRVoronoiRiverField::dblSlots);
    private static final ThreadLocal<DblSlot[]> SWAMP_MEMO = ThreadLocal.withInitial(GTSRVoronoiRiverField::dblSlots);
    private static final ThreadLocal<LongSlot[]> SEGKEY_MEMO = ThreadLocal
        .withInitial(GTSRVoronoiRiverField::longSlots);
    private static final ThreadLocal<IntSlot[]> POOL_MEMO = ThreadLocal.withInitial(GTSRVoronoiRiverField::intSlots);
    private static final ThreadLocal<BorderSlot[]> BORDER_MEMO = ThreadLocal
        .withInitial(GTSRVoronoiRiverField::borderSlots);

    /** (seed,x,z) → 槽下标（splitmix 终混取高位；正确性只靠全键精确比较，散列仅决定淘汰分布）。 */
    private static int slotIndex(long worldSeed, int x, int z, int mask) {
        long k = worldSeed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        k ^= k >>> 33;
        k *= 0xFF51AFD7ED558CCDL;
        k ^= k >>> 33;
        return (int) (k >>> 40) & mask;
    }

    /** 含 roster 键的槽下标（poolLevelAt 用）。 */
    private static int slotIndex(long worldSeed, int x, int z, int roster, int mask) {
        long k = worldSeed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (roster * 0x27D4EB2F165667C5L);
        k ^= k >>> 33;
        k *= 0xFF51AFD7ED558CCDL;
        k ^= k >>> 33;
        return (int) (k >>> 40) & mask;
    }

    /**
     * warp+evalBorder 复合的列级 memo 入口：对 (worldSeed, x, z) 求两级蜿蜒 + 3×3 border2，
     * 返回装好字段的 {@link EVAL_BUF}（命中=槽快照拷回，未命中=现场求值并存槽）。
     * 消费方照旧"取完即用/即拷"，不得跨下一次调用持有（原 buffer 纪律不变）。
     */
    private static BorderEval warpedBorder(long worldSeed, int x, int z) {
        final int idx = slotIndex(worldSeed, x, z, BORDER_MEMO_MASK);
        final BorderSlot s = BORDER_MEMO.get()[idx];
        final BorderEval e = EVAL_BUF.get();
        if (s.valid && s.seed == worldSeed && s.x == x && s.z == z) {
            // 命中：槽快照 → EVAL_BUF（EVAL_BUF 此刻装的是别的列，不得反向污染槽）。
            e.c = s.c;
            e.dC = s.dC;
            e.dN = s.dN;
            e.d3 = s.d3;
            e.nx = s.nx;
            e.nz = s.nz;
            e.aX = s.aX;
            e.aZ = s.aZ;
            e.bX = s.bX;
            e.bZ = s.bZ;
            e.cX = s.cX;
            e.cZ = s.cZ;
            return e;
        }
        final double[] warped = warp(worldSeed, x, z);
        evalBorder(worldSeed, warped[0], warped[1]);
        // 未命中：现场求值已写入 EVAL_BUF ⇒ EVAL_BUF → 槽。
        s.c = e.c;
        s.dC = e.dC;
        s.dN = e.dN;
        s.d3 = e.d3;
        s.nx = e.nx;
        s.nz = e.nz;
        s.aX = e.aX;
        s.aZ = e.aZ;
        s.bX = e.bX;
        s.bZ = e.bZ;
        s.cX = e.cX;
        s.cZ = e.cZ;
        s.seed = worldSeed;
        s.x = x;
        s.z = z;
        s.valid = true;
        return e;
    }

    /**
     * (x,z) 最近 Voronoi 细胞的格点坐标签名（判据/T5 用：分叉检测 = 2×2 列块内 ≥3 个不同
     * 签名即三叉点；主干带次级抑制也按它判"同一条主边界"）。签名含两个格点坐标，无碰撞口径。
     * v1.20.40 起实现 = {@link #warp} + {@link #evalBorder} 的最近细胞（原独立 3×3 F1 循环
     * 与 evalBorder 的扫描序/平手规则逐字同形 ⇒ 签名逐位不变，仅消重复实现）。
     */
    public static long nearestCellHash(long worldSeed, int x, int z) {
        // P19 U8：warp+evalBorder 复合走 warpedBorder 列级 memo（签名算式与逐位结果同前）。
        final BorderEval border = warpedBorder(worldSeed, x, z);
        return ((long) border.aX << 32) | (border.aZ & 0xFFFFFFFFL);
    }

    /**
     * 细胞中心单轴偏移 ∈ [-0.35, 0.35]×interval（splitmix64 高 53 位 → [0,1) → 对称）。
     * interval 参数化是 T5 巨湖第二 Voronoi（{@link #LAKE_INTERVAL}）所需——河网/巨湖各传各的
     * 间隔，偏移幅度比例与盐域一致，无第二套哈希。
     */
    private static double cellOffset(long worldSeed, int gx, int gz, long salt, double interval) {
        final long h = GTSRWorldgenHash.splitmix64(GTSRWorldgenHash.cellSeed(worldSeed, gx, gz, salt));
        return (((h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR) * 2.0D - 1.0D) * CELL_JITTER * interval;
    }

    // ═════════════════ OpenSimplex 二维 Disk（内嵌紧凑实现，公开域算法） ═════════════════

    /**
     * 每线程每 seed 的 OpenSimplex 置换表缓存（参照 T3 {@code ampAt} 缓存模式：ThreadLocal、
     * 同 seed 一致、超限整清重算值不变）。三张表（大弯/小弯/主干带 T5）成组缓存；key = worldSeed。
     */
    private static final int DISK_CACHE_CAP = 8;

    private static final ThreadLocal<HashMap<Long, OpenSimplexDisk[]>> DISK_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    /** 单线程生成域内的可复用输出缓冲（避免逐列分配）。 */
    private static final ThreadLocal<double[]> DISK_BUF = ThreadLocal.withInitial(() -> new double[2]);

    private static double[] diskBuffer() {
        return DISK_BUF.get();
    }

    /** disk 槽位：0=大弯、1=小弯、2=主干带（T5）、3=巨湖破圆 warp（v1.20.40 P19 §D）。 */
    private static final int SLOT_DISK_LARGE = 0;
    private static final int SLOT_DISK_SMALL = 1;
    private static final int SLOT_DISK_TRUNK = 2;
    private static final int SLOT_DISK_LAKE_WARP = 3;

    /** （seed, 槽, 尺度）处的 disk 位移 → out[0]/out[1]（单位圆盘内向量 × 幅度在外层乘）。 */
    private static void diskAt(long worldSeed, int slot, double scale, int x, int z, double[] out) {
        diskSetFor(worldSeed)[slot].disk(x / scale, z / scale, out);
    }

    /** P19 U8：bySeed HashMap 查找的单入口 memo（生成域内 seed 恒定，命中即免一次哈希查表）。 */
    private static final ThreadLocal<long[]> DISK_LAST_SEED = ThreadLocal.withInitial(() -> new long[1]);

    private static final ThreadLocal<OpenSimplexDisk[]> DISK_LAST_SET = ThreadLocal.withInitial(() -> null);

    /** 线程当前 seed 的置换表组（沿用 {@link #DISK_CACHE} 的上限整清纪律，值不变）。 */
    private static OpenSimplexDisk[] diskSetFor(long worldSeed) {
        final long[] lastSeed = DISK_LAST_SEED.get();
        if (lastSeed[0] == worldSeed && DISK_LAST_SET.get() != null) {
            return DISK_LAST_SET.get();
        }
        final HashMap<Long, OpenSimplexDisk[]> bySeed = DISK_CACHE.get();
        OpenSimplexDisk[] set = bySeed.get(worldSeed);
        if (set == null) {
            if (bySeed.size() >= DISK_CACHE_CAP) {
                bySeed.clear();
            }
            set = new OpenSimplexDisk[] { new OpenSimplexDisk(worldSeed ^ SALT_DISK_LARGE),
                new OpenSimplexDisk(worldSeed ^ SALT_DISK_SMALL), new OpenSimplexDisk(worldSeed ^ SALT_TRUNK),
                new OpenSimplexDisk(worldSeed ^ SALT_DISK_LAKE_WARP) };
            bySeed.put(worldSeed, set);
        }
        lastSeed[0] = worldSeed;
        DISK_LAST_SET.set(set);
        return set;
    }

    /**
     * 二维 OpenSimplex 噪声的 Disk 形态（KdotJPG 公开域算法，RTG {@code SimplexOctave.Disk}
     * 同式紧凑内嵌）：A2 格点 4 邻域、attn⁴×SPH2 十二向单位梯度 ⇒ 输出为单位圆盘内
     * 二维向量（长度 ≤1）。置换表 256 项 LCG 洗牌（构造期一次，之后只读 ⇒ 线程内共享安全）。
     * 查表结构照 RTG {@code LOOKUP_2D}：8 形 × 4 格点 = 32 项，形由 skewed 分量的三个
     * 量化位（a/b/c）选出；每格点存 skewed 格偏 (xsv,ysv) 与预计算的未扭曲位移 (dx,dy)。
     */
    private static final class OpenSimplexDisk {

        private static final double STRETCH_2D = -0.211324865405187D;
        private static final double SQUISH_2D = 0.366025403784439D;

        /** SPH2 十二向单位梯度（×2 平铺；方向表——Disk 的输出方向）。 */
        private static final double[] GRADIENTS_SPH2 = { 0.000000000000000D, 1.000000000000000D, 0.500000000000000D,
            0.866025403784439D, 0.866025403784439D, 0.500000000000000D, 1.000000000000000D, 0.000000000000000D,
            0.866025403784439D, -0.500000000000000D, 0.500000000000000D, -0.866025403784439D, 0.000000000000000D,
            -1.000000000000000D, -0.500000000000000D, -0.866025403784439D, -0.866025403784439D, -0.500000000000000D,
            -1.000000000000000D, 0.000000000000000D, -0.866025403784439D, 0.500000000000000D, -0.500000000000000D,
            0.866025403784439D };

        /**
         * 二维外推梯度（十二边形系，幅值 ~0.13；<b>外推</b>表——Disk 的输出幅度）。与 SPH2 是
         * <b>两张不同的表</b>、各自从置换表按不同取模派生（RTG {@code perm2D}/
         * {@code perm2D_sph2}）：外推用 {@code perm[i]%12}，方向用 {@code (perm[i]/12)%12}。
         * 把两者混用会让 Disk 幅度 ×~7.7（实测：jitter ±~690 折叠全部边界，河网糊成 79% 覆盖）。
         */
        private static final double[] GRADIENTS_2D = { 0.114251372530929D, 0.065963060686016D, 0.131926121372032D,
            0.000000000000000D, 0.114251372530929D, -0.065963060686016D, 0.065963060686016D, -0.114251372530929D,
            0.000000000000000D, -0.131926121372032D, -0.065963060686016D, -0.114251372530929D, -0.114251372530929D,
            -0.065963060686016D, -0.131926121372032D, -0.000000000000000D, -0.114251372530929D, 0.065963060686016D,
            -0.065963060686016D, 0.114251372530929D, -0.000000000000000D, 0.131926121372032D, 0.065963060686016D,
            0.114251372530929D };

        /** LOOKUP_2D 的平铺展开：32 格点的 skewed 格偏与预计算未扭曲位移（构造见静态块）。 */
        private static final int[] LP_XSV = new int[32];
        private static final int[] LP_YSV = new int[32];
        private static final double[] LP_DX = new double[32];
        private static final double[] LP_DY = new double[32];

        static {
            // RTG LOOKUP_2D 构造规则逐字：8 形位 i；前两格点恒 (1,0)/(0,1)，后两格点按位形取
            // (0,0)|(2,0)、(1,-1)|(1,1)（偶形）或 (-1,1)|(1,1)、(0,0)|(0,2)（奇形）。
            // dx = -(xsv + (xsv+ysv)·SQUISH_2D)、dy 对称（格点未扭曲位置的反向位移）。
            for (int i = 0; i < 8; i++) {
                final int i1;
                final int j1;
                final int i2;
                final int j2;
                if ((i & 1) == 0) {
                    if ((i & 2) == 0) {
                        i1 = 0;
                        j1 = 0;
                    } else {
                        i1 = 2;
                        j1 = 0;
                    }
                    if ((i & 4) == 0) {
                        i2 = 1;
                        j2 = -1;
                    } else {
                        i2 = 1;
                        j2 = 1;
                    }
                } else {
                    if ((i & 2) == 0) {
                        i1 = -1;
                        j1 = 1;
                    } else {
                        i1 = 1;
                        j1 = 1;
                    }
                    if ((i & 4) == 0) {
                        i2 = 0;
                        j2 = 0;
                    } else {
                        i2 = 0;
                        j2 = 2;
                    }
                }
                put(i * 4 + 0, 1, 0);
                put(i * 4 + 1, 0, 1);
                put(i * 4 + 2, i1, j1);
                put(i * 4 + 3, i2, j2);
            }
        }

        private static void put(int slot, int xsv, int ysv) {
            LP_XSV[slot] = xsv;
            LP_YSV[slot] = ysv;
            LP_DX[slot] = -(xsv + (xsv + ysv) * SQUISH_2D);
            LP_DY[slot] = -(ysv + (xsv + ysv) * SQUISH_2D);
        }

        private final short[] perm = new short[256];

        OpenSimplexDisk(long seed) {
            // 256 项倒序洗牌（LCG 与 RTG SimplexOctave 构造同族）
            final short[] source = new short[256];
            for (int i = 0; i < 256; i++) {
                source[i] = (short) i;
            }
            for (int i = 255; i >= 0; i--) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                int r = (int) ((seed + 31L) % (i + 1L));
                if (r < 0) {
                    r += i + 1;
                }
                perm[i] = source[r];
                source[r] = source[i];
            }
        }

        /** (x,y) 处的单位圆盘向量 → out（长度 ≤1；幅度在外层乘）。 */
        void disk(double x, double y, double[] out) {
            double dxOut = 0.0D;
            double dyOut = 0.0D;
            final double s = STRETCH_2D * (x + y);
            final int xsb = fastFloor(x + s);
            final int ysb = fastFloor(y + s);
            final double xsi = x + s - xsb;
            final double ysi = y + s - ysb;
            // 点列表索引（RTG/KdotJPG 查表式的三个量化位：a=点选择，b/c=形位）
            final int a = (int) (ysi - xsi + 1);
            final int index = (a << 2) | ((int) (xsi + ysi / 2.0D + a / 2.0D) << 3)
                | ((int) (ysi + xsi / 2.0D + 0.5D - a / 2.0D) << 4);
            final double ssi = (xsi + ysi) * SQUISH_2D;
            final double xi = xsi + ssi;
            final double yi = ysi + ssi;
            for (int k = 0; k < 4; k++) {
                final int p = index + k;
                final double dx = xi + LP_DX[p];
                final double dy = yi + LP_DY[p];
                final double attn = 2.0D - dx * dx - dy * dy;
                if (attn <= 0.0D) {
                    continue;
                }
                final int h = (perm[(xsb + LP_XSV[p]) & 255] ^ ((ysb + LP_YSV[p]) & 255)) & 255;
                final int gi2d = (perm[h] % 12) * 2;
                final int giSph2 = ((perm[h] / 12) % 12) * 2;
                final double extrapolation = GRADIENTS_2D[gi2d] * dx + GRADIENTS_2D[gi2d + 1] * dy;
                final double attnSq = attn * attn;
                final double w = attnSq * attnSq * extrapolation;
                dxOut += w * GRADIENTS_SPH2[giSph2];
                dyOut += w * GRADIENTS_SPH2[giSph2 + 1];
            }
            out[0] = dxOut;
            out[1] = dyOut;
        }

        private static int fastFloor(double v) {
            final int i = (int) v;
            return v < i ? i - 1 : i;
        }
    }
}
