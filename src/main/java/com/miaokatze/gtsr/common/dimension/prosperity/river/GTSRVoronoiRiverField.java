package com.miaokatze.gtsr.common.dimension.prosperity.river;

import java.util.HashMap;

import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
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
 * 干出为<b>浅滩/河滩</b>（浅滩重释 = 派生条件，独立 shoalNoise 档已废；<b>P26-B2（v1.20.49）
 * amp 2.5→2.0 后 amp ≤ depth ⇒ 周期性干出退役</b>，露头只剩滩带水缘）；</li>
 * <li>荒漠(2)：干谷断流——<b>P22 A1a（v1.20.42）起与全域一致</b>（原"整段闸 干:湿 = 65:35"
 * 已退役，见顶部已删字段登记）；<b>P23 R1（v1.20.46 批2 S2）主干移除
 * 后 {@link #wetAt} 恒 false——全域置水死，河流场只剩零散干床（段激活门
 * {@link #RIVER_SEGMENT_ACTIVATE_P}），水归湖/沼泽池独立通道</b>；</li>
 * <li>沼泽(3)：沼地河——width×1.2（v1.20.40 从 1.6 收窄）、<b>P26-B2（v1.20.49）bedTarget
 * 66.5→64.5 / depth 1→2（湿河"水近地"口径随枯竭下调，床域 [pool−2.5, pool−1.5] 全低于
 * SEA ≥2.5 格——"沼泽干床砂砾与地面齐平"观感修复）</b>；不换河群系（RTG swamp 豁免先例）。</li>
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
     * <b>已删字段登记（P23 R1·S6，v1.20.46）</b>：WET_EDGE（v1.20.40 退役的 wetNoise 串珠闸）、
     * DESERT_WET_SHARE/segWetAt/SALT_WET 消费口径（荒漠整段闸）、LAKE_BED_TARGET（平底床历史值）、
     * POOL_DROP（落差墙判据）、SANZU_TRUNK_INNER/TRUNK_ALIGN_COS（主干三重门）、
     * SANZU_BIOME_STRENGTH（sanzu 河道支）——判据侧引用随 STC 退役与 RMC/SLMC 重钉一并摘除后，
     * 生产零消费字段本片兑现删除（历史 javadoc 见版本树）。
     */

    /**
     * <b>已删成员登记（P25，本轮）</b>：沼泽河床<b>残潭场整体退役</b>（用户裁定）——
     * {@code swampRiverPoolAt}/{@code swampRiverPoolColumnAt} 两方法与专属常量
     * {@code SWAMP_RIVER_POOL_SCALE/BAND_CENTER/BAND_WIDTH/DIG_BASE/DIG_SPAN/FILL_TOP}、
     * 盐 {@code SALT_SWAMP_RIVER_POOL}（0x5249F112L，值不回收）一并删除。引入史：v1.20.42
     * （P22 A1b）。退役理由（P25 用户裁定）：「小湖泊不上枯竭河流」——枯竭河床的残水语义改由
     * 干河床谓词 {@link #isDryRiverColumn}（D6）承载，微池/三档两水体通道不受影响。消费面
     * 摘除跨片：本类内零残留；PTP 潭底下挖支路本片已删；PlacementGate.dryColumnAt 腿④、
     * ChunkProviderProsperityRuins（SwampFieldGrid fixed 潭腿）、GTSRRiverPlacer（潭置水支路）、
     * GTSRCommand.populateWaterColumnAt（潭腿）、TerrainVariants 注释引用——归 S3 片收口
     * （file:line 清单见本片回执）。
     */

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

    // ═════════════ v1.20.41（P20 S3 需求 1 + 需求 2）：谷坡环水陆过渡带 + 岸坡下切 ═════════════

    /**
     * 岸坡下切深度（格，只向更深；P20 S3 需求 2"看不到河床"的主修量）。推导（§13 C10 只取比值，
     * 禁抄 RTG 绝对值）：RTG 的 {@code erodedNoise} 用 {@code actualRiverProportion = 300/1600 =
     * 18.75%} 只把<b>水道中心</b>那一段压到床高，其余宽度线性插回原地形；本仓对应的"切床域"是
     * {@code s ∈ [S_ERODE=0.557, WET_MIN=0.78)}，横向半宽 ≈ 188×(0.78−0.557) ≈ 4.2-9 格，占谷半宽
     * （≈188×0.78 ≈ 19 格）的 22%-47%，与 RTG 的 18.75% 同阶 ⇒ 域宽照搬即可，深度另算：
     * 现地面 board 恒 1 格（回填门 {@code h < pool−1}、床 = pool−depth±amp），要让床料在断面上
     * 至少 3 格宽出露需在现有水深上再削 2 格 ⇒ <b>本轮取 2.0，实机后在 {1.5, 2.0, 2.5} 内校准</b>
     * （取值同时受 {@code RiverMorphologyCheck} H2「端面顺流剖面逐列床高差 ≤ 2」带约束——下切量
     * 会被 {@link #endFaceBed} 的 smoothstep 权重 (1−w) 放大到 (target−bed) 项上）。
     * <p>
     * <b>沼泽档（roster 3）豁免——v1.20.49 P26-B2 移除</b>：原豁免护的是 A7 旧档表语义
     * 「沼泽床 ∈ [66.5,67.5]＝水面近地」（结构上不可能被运行时下切打红），其语义地板只能由
     * 实现域守住；§5 S3 判据 3 给的处置正是"下切域限缩到 roster ∈ {0,1,2,4}"。P26-B2 沼泽
     * 床档随枯竭下调（66.5/1.0→64.5/2.0）后该语义退役，heightCore 调用侧的
     * {@code rosterIndex != 3} 条件已删（复核读数见 {@code plan/tmp/p26-b2-readings.md}）。
     */
    public static final double BANK_CUT_DEPTH = 2.0D;

    /**
     * 下切量与谷坡环外缘扰动<b>共用的噪声波长</b>（格；两者盐不同 ⇒ 域互不相关）。
     * H-1：{@code 33 % 16 = 1} ✔。取 {@link #BED_NOISE_SCALE}=96 的 1/3（意图 32 是 16 的倍数
     * ⇒ 规避账本 §7 RTG 的 chunk 对齐条纹坑，落 33）。
     */
    public static final double CUT_NOISE_SCALE = 33.0D;

    /**
     * 谷坡环<b>外缘</b>的噪声扰动幅度（s 域；需求 1"消硬边"的第二半——环与群系表土的分界若
     * 是一条等值线，只是把硬边从"水陆"搬到了"滩料/草皮"）。比值移植（§13 C10）：RTG
     * {@code SurfaceVanillaRiver} 的材质分界是 {@code river + noise2(i/10,j/10)×0.15 > 0.8}，
     * 即扰动占归一河强域的 15%；本仓 s 同样归一到 [0,1] ⇒ 直译会是 0.15，但本仓滩带宽只有
     * {@code WET_MIN − S_ERODE = 0.223}（RTG 的对应带 0.2），0.15 会把外缘摆动到"环几乎不存在"
     * ⇒ 取其半 = 0.08。换算成格：谷域内 ds/dcol ≈ 0.78/19 ≈ 0.041 ⇒ 0.08 ≈ 外缘摆动 ±2 格。
     */
    public static final double BANK_BAND_JITTER = 0.08D;

    /** 岸坡下切量噪声盐（{@link #SALT_BED} 之外的独立域，P20 S3 新增）。 */
    public static final long SALT_BANK_CUT = 0x5249F110L;
    /** 谷坡环外缘扰动噪声盐（与 {@link #SALT_BANK_CUT} 亦互不相关）。 */
    public static final long SALT_BANK_BAND = 0x5249F111L;

    /**
     * <b>谷坡环 = 水陆过渡带</b>判定（P20 S3 需求 1）：{@code S_ERODE + 扰动 ≤ s < WET_MIN}。
     * 该域的地表已被 {@code heightCore} 的两段式压低（外段贴水缓坡 + 内段向床 lerp）但落在
     * 置水域（{@code s ≥ WET_MIN}）之外，改造前由 {@code GTSRRiverPlacer} 的
     * {@code s < WET_MIN → continue} 一格河料都不铺 ⇒ 裸露面仍是原群系草皮，与一列之隔的河床砾
     * 直接相邻＝水陆硬边（账本 §5 {@code [主验]} bullet 2）。现由本谓词圈出、铺<b>滩料</b>
     * （不铺床料、不回填水）。
     * <p>
     * 内缘恒取 {@link #WET_MIN}（核/环的铺料与统计分界，抖动会把水↔滩的边界糊进核内），
     * 只有外缘抖动 ⇒ 滩带宽在 3.5-9 格间成块变化（RTG 材质分界的同款肌理）。
     */
    public static boolean inBankBand(long worldSeed, int x, int z, double s) {
        if (s >= WET_MIN) {
            return false;
        }
        final double jitter = GTSRWorldgenHash
            .valueNoise(worldSeed ^ SALT_BANK_BAND, x / CUT_NOISE_SCALE, z / CUT_NOISE_SCALE);
        return s >= S_ERODE + BANK_BAND_JITTER * jitter;
    }

    /**
     * 河道断面的<b>地表料选型</b>（单一真值；生产侧 {@code GTSRRiverPlacer} 与离线判据
     * {@code RiverMorphologyCheck} 共用同一出口，P20 S3 起从落块器内联式提出）。返回
     * {@code true} = 铺<b>床料</b>（群系分档：P26-B2 v1.20.49 起沼泽=硅砂、其余=河砾，见
     * {@code GTSRRiverPlacer.bedMaterial}；此前恒 {@code prosperityRiverGravel}），{@code false}
     * = 铺<b>滩料</b>（群系档表，见 {@code GTSRRiverPlacer.flatMaterial}）：
     * <ol>
     * <li>谷坡环（{@link #inBankBand}）→ 滩料（需求 1 的过渡带，恒不铺床料）；</li>
     * <li>水下（{@code submerged}，即 {@link #submergedAt} 为真）→ 床料；</li>
     * <li>滩带外露头（{@code s > WET_MIN + }{@link #SHORE_FLAT_BAND}）→ 床料（干砾浅滩）；</li>
     * <li>其余（滩带内露头）→ 滩料。</li>
     * </ol>
     * 域外列（{@code s < S_ERODE} 且不在环内）本方法无意义——调用方一律先按 S_ERODE 短路。
     * <p>
     * ⚠ {@code submerged} 入参的<b>算法</b>不属于本方法的口径：调用方一律走 {@link #submergedAt}
     * 取，禁止在调用方内联 {@code h < pool−1}（P20 S3b 曾在判据里内联复刻一次，S3c 收掉）。
     */
    public static boolean bedTopAtSurface(long worldSeed, int x, int z, double s, boolean submerged) {
        if (inBankBand(worldSeed, x, z, s)) {
            return false;
        }
        return submerged || s > WET_MIN + SHORE_FLAT_BAND;
    }

    /**
     * 「本列是否<b>没在本段水面之下</b>」的<b>唯一出口</b>（v1.20.41 P20 S3c 需求 2 侧的第二真值清除）：
     * 水面 = 段池水位 {@code pool}、<b>最高水格 = {@code pool − 1}</b>（{@code GTSRRiverPlacer} 类注释
     * 的既有口径，P19 §C），固体顶 {@code h} 落在最高水格或其下即没水。三处消费面共用本式——
     * <ol>
     * <li>落块器 {@code GTSRRiverPlacer.place} 的<b>地表料选型</b>（喂 {@link #bedTopAtSurface}）；</li>
     * <li>落块器同一列的<b>水面回填门</b>（原第二处内联式，S3c 一并收口）；</li>
     * <li>离线判据 {@code RiverMorphologyCheck} 的断面扫描（P20 S3b 偏离③自报的复刻式）。</li>
     * </ol>
     * <b>形参必须保持 {@code int}</b>（不得"顺手改宽"成 double）：{@code pool - 1} 的 int 减法在
     * {@code pool = Integer.MIN_VALUE} 处按 JLS 回绕为 {@code MAX_VALUE} ⇒ 本式在该极值为真；
     * 若形参是 double 则该处为假。对拍探针
     * {@code plan/tmp/p20-s3c/SubmergedParity.java}（一次性，不入库）实测：<b>合计 255,428 对样本、
     * 差异数 = 0</b>——固定随机合成域 {@code Random(0x5EED)} 100,000 对（含 pool 边界 ±2 与
     * {@code Integer.MIN_VALUE/MAX_VALUE} 极值）＋ 关键 pool × h∈pool±4 穷举 192 对 ＋ 生产真值列
     * 155,236 列（{@code (heightAt, poolLevelAt)} 实取）；另开 double 形参对照臂，该臂与 int 路真值
     * 不同 <b>2,093</b> 例（全部来自 int 回绕极值）⇒ 证明 int 签名不可迁移。
     * 参数为 int ⇒ NaN/±Infinity 在本域<b>不可表示、进不来</b>（不存在"NaN 语义"要复刻）。
     */
    public static boolean submergedAt(int h, int pool) {
        return h < pool - 1;
    }

    /**
     * 本列的<b>岸坡下切量</b>（格，恒 ∈ [{@link #BANK_CUT_DEPTH}/2, {@link #BANK_CUT_DEPTH}]；
     * P20 S3 需求 2 的唯一出口，生产侧 {@code ProsperityTerrainProfile.heightCore} 与离线判据共用）。
     * 域 = {@link #inBankBand} 的谷坡环；沼泽档（roster 3）的豁免曾在调用侧（heightCore）落、
     * <b>P26-B2（v1.20.49）已随沼泽床档下调移除</b>（见 {@link #BANK_CUT_DEPTH} 注）——本函数
     * 是纯场，档位语义归地形侧，避免"两处各挡一次"的第二真值。
     * <p>
     * 取值下界取半而非 0：{@code valueNoise} 的负瓣若把下切清零，环内会出现"整段没切"的斑块，
     * 断面出露宽度不连续（{@code RiverMorphologyCheck} 的床料出露率判据正是钉这个）。
     */
    public static double bankCutAt(long worldSeed, int x, int z) {
        final double n = GTSRWorldgenHash
            .valueNoise(worldSeed ^ SALT_BANK_CUT, x / CUT_NOISE_SCALE, z / CUT_NOISE_SCALE);
        return BANK_CUT_DEPTH * (0.5D + 0.25D * (n + 1.0D));
    }

    /**
     * 荒漠整段闸的湿段份额。<b>已删字段（P23 R1·S6，v1.20.46）</b>：v1.20.40 立档、v1.20.42
     * 随 {@link #wetAt} 新门从生产链退役、判据侧（RMC F 组读数行）引用随 S6 重钉摘除后兑现
     * 删除（干:湿 = 65:35 的历史校准原文见版本树）。
     */

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
     * 水位差聚到 {0,2,4}：2 格级差 = 段界小跌水（&lt; 旧落差墙阈 3，字段已删见类顶登记），
     * 4 格级差 = 落差墙/瀑布。
     */
    public static final int POOL_LEVEL_STEP = 2;

    /**
     * 段间落差墙判据（格）。<b>已删字段（P23 R1·S6，v1.20.46）</b>：批2 S2 删
     * {@code GTSRRiverPlacer} 的 dropColumn 检测支路后生产零消费，判据侧（RMC 注释）引用随
     * S6 重钉摘除后兑现删除（历史语义"相邻列池水位差 ≥ 3 → 落差墙"见版本树）。
     */

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
     * 岛底柱柱位抖动的域盐（v1.20.41 P20 S5 新增，plan §15.5）：与巨湖细胞盐/床盐/盘盐四域
     * 分离——柱位抖动只吃"湖格 × 柱序号"，不吃列坐标，故同一座湖的 5 根柱对全部列一致。
     * 值落在本类盐段尾（{@code …10CL} 已被 {@link #SALT_SEG_KEY} 占用 ⇒ 取 {@code …110L}）。
     */
    public static final long SALT_LAKE_PILLAR = 0x5249F110L;
    /**
     * 巨湖破圆 domain-warp 的 disk 盐（v1.20.40 P19 §D 新增；第 4 张 disk 表，与河网两级
     * 蜿蜒/主干带的 disk 盐域分离）。
     */
    public static final long SALT_DISK_LAKE_WARP = 0x5249F10DL;
    /**
     * 巨湖破圆 domain-warp <b>副倍频</b>的 disk 盐（P25 D3② 新增；第 5 张 disk 表，与主 warp
     * 盘及河网两级蜿蜒/主干带的 disk 盐域分离）。取本类盐段尾 {@code …116L}（…115L 已被
     * {@link #SALT_DRY_RIVER} 占用）。
     */
    public static final long SALT_DISK_LAKE_SUB = 0x5249F116L;
    /** 沼泽微池 Voronoi 细胞 x 偏移盐（v1.20.40 P19 §E 新增，独立第三 Voronoi）。 */
    public static final long SALT_SWAMP_POOL_X = 0x5249F10EL;
    /** 沼泽微池 Voronoi 细胞 z 偏移盐。 */
    public static final long SALT_SWAMP_POOL_Z = 0x5249F10FL;
    /**
     * 沼泽河床残潭噪声盐。<b>已删盐（P25）</b>：随残潭场整体退役删除（v1.20.42 引入、P25
     * 用户裁定退役，见类顶登记与下方残潭退役段）。历史值 {@code 0x5249F112L}——值已释放但
     * <b>不回收、不复用</b>（防跨版本 digest 混淆；新盐一律续取段尾 …115L 起）。
     */

    // ═════════════════ P23 R1（v1.20.46 批2 S2）：主干移除后的零散干床段激活门 ═════════════════

    /**
     * <b>干床段激活率</b>（P23 R1② 零散干床）：主干宽河移除后，{@link #strengthAt} 对每条
     * Voronoi 边段（同 {@link #segKeyAt}）按 {@code hash01(segKey ^ SALT_RIVER_SEGMENT_GATE)
     * < 本值} 独立决定是否保留干河床——未激活段整段清零（干床不再成网，约 1/5 段零散保留；
     * 同段同值 ⇒ 跨 chunk 一致）。判定在 {@link #WET_MIN} 置水门之外独立存在：干床段不置水
     * （{@link #wetAt} 恒 false），只保留谷形/床/滩料。
     */
    public static final double RIVER_SEGMENT_ACTIVATE_P = 0.20D;

    /**
     * 干床段激活门的盐（P23 R1②）：取本类盐段尾 {@code …113L}（…112L 当时已被残潭盐占用；
     * P25 残潭退役后该值空闲、不回收）。
     */
    public static final long SALT_RIVER_SEGMENT_GATE = 0x5249F113L;

    // ═════════════════ P30 I1（v1.20.53）：枯竭河末端定向衰减（死邻 taper + 湖腿平滑）═════════════════

    /**
     * <b>死邻衰减带半宽</b>（border2 c 域比值，P30 I1 新增）：<b>数学式</b>——
     * {@link #strengthAt} 过 3b 段激活门后，对本列所在边 (A,B) 的两个结点邻边 (A,C)/(B,C)
     * 各判一次死活（{@code hash01(segKeyOfCells(细胞对) ^ SALT_RIVER_SEGMENT_GATE)
     * ≥ RIVER_SEGMENT_ACTIVATE_P}——与 3b 门同式同盐同阈，仅细胞对不同）；r₁=(d3−dC)/d3、
     * r₂=(d3−dN)/d3（两邻边各自的 border2 c 值，{@link BorderEval} 第三近细胞随 c 同次带出），
     * t₁/t₂ = 死邻 ? s01(r/本值) : 1.0，返回式 ×t₁×t₂。t 带通在 r ≥ 本值处精确 1.0 ⇒
     * <b>衰减只伸到结点侧 r &lt; 本值的邻带</b>（c 域格宽换算 ≈188×c ⇒ 0.20 档 ≈38 格）。
     * <p>
     * <b>活-活零漂守卫</b>：两邻边皆活（!deadAC &amp;&amp; !deadBC）时 strengthAt 直接走原返回式
     * （不触任何 r/t 计算）——活-活结点 IEEE 逐位零漂。
     * <p>
     * <b>事实背景</b>：结点两邻边按 {@link #RIVER_SEGMENT_ACTIVATE_P}=0.20 独立激活 ⇒
     * 至少一邻死概率 1−0.20² = 0.96（≈九成结点）——活段端头几乎都在死邻处经本带自然渐灭，
     * 消"枯竭河末端刀切山体"（谷形整深到段界、界外原地形）。<b>校准域 0.10-0.25</b>
     * （≈19-47 格；0.10 衰减更快更陡、0.25 更缓更长）。
     */
    public static final double TAPER_DEAD = 0.20D;

    /**
     * <b>湖腿平滑带宽</b>（湖压力域，P30 I1 新增）：<b>数学式</b>——过了 3a 湖让位硬腿
     * （{@code lakeAt < sanzuBiomeShoreAt ⇒ return 0}，硬腿原样保留——湖+滩内 s ≡ 0 的
     * isSanzuColumn/D6/微池让位语义不动）的列，返回式乘
     * {@code t_lake = s01((lakeAt − sanzuBiomeShoreAt)/本值)}——硬腿判据处强度恰为 0
     * （连续衔接腿内恒 0），腿外本值压力带宽内 smoothstep 0→1，湖缘由刀切变渐灭。
     * 格宽换算（{@link #sanzuBiomeShoreAt} 注的 D_eff 口径）：本值×2670/1.1794 ⇒
     * 0.02 档 ≈45 格。<b>校准域 0.01-0.06</b>（≈23-136 格）。湖带外（压力差 ≥ 本值）
     * s01 带通精确 1.0 ⇒ 乘法逐位恒等。
     * <p>
     * <b>P32（批3 T1-4 换算统一，v1.20.55）</b>：统一线性式（见 {@link #LAKE_WATER_LEVEL} 的
     * P32 段）下 <b>本值 0.02 ⇒ 名义带宽 0.02×2670 ≈ <b>53 格</b></b>；上文 ×2670/1.1794
     * 割线式（45 格）是 P23-P28 圆场口径，随场换废弃（历史保留）。校准域新格数 ≈27-160。
     */
    public static final double LAKE_FADE = 0.02D;

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
     * 主干<b>内带门</b>。<b>已删字段（P23 R1·S6，v1.20.46）</b>：批2 S2 随 {@link #strengthAt}
     * 的 trunk 支整段删除而生产零消费，判据侧（STC）随整文件退役后兑现删除（历史值 0.08 与
     * 三重门校准原文见版本树）。
     */

    /** 主干带内河宽乘子（plan §3.3 "width×3.5"：河宽 ×3-4 带内取 3.5）。 */
    public static final double TRUNK_WIDTH_SCALE = 3.5D;

    /**
     * 主干对齐门余弦阈值。<b>已删字段（P23 R1·S6，v1.20.46）</b>：批2 S2 随
     * {@code trunkAligned}（无消费者即删）删除而生产零消费，判据侧（STC）随整文件退役后
     * 兑现删除（历史值 0.70≈45.6° 与"少支流"几何论证见 v1.20.45 版本树）。
     */

    /** 主干梯度差分步距（格）：TRUNK_SCALE/16——梯度方向在带内数百格尺度上稳定。 */
    public static final int TRUNK_GRAD_STEP = (int) (TRUNK_SCALE / 16.0D);

    /**
     * 巨湖 Voronoi 细胞边长（格，RTG lakeInterval 同位参数，起步 1200）。
     * <b>P25（用户裁定）：1200 → 3000</b>——湖概率减半轮（用户裁定"湖太多"；站距 ×2.5 ⇒
     * 同面积湖站数 1/6.25、按探索半径的可见湖频次约减半档）。派生随动：D_eff ≈ 0.89×本值
     * ≈ 2670（{@link #LAKE_WATER_LEVEL} 的 W' 反解口径）、{@link #LAKE_STATION_D_MIN} 自动
     * = 0.375×本值 = 1125、3×3 站格窗半宽 1.5×本值 = 4500 ≫ 合成 warp 位移 ≤ 97
     * ⇒ F1/F2 窗内精确性论证不变（P32 起 warp ΣA=35，论证更松）。
     */
    public static final double LAKE_INTERVAL = 3000.0D;

    /**
     * <b>小湖淘汰腿</b>（P23 R1 新增，v1.20.46 批2 S2）：{@link #lakeStationDistances} 的第二近
     * 站距 {@code dd[1] < 本值} ⇒ {@link #NO_LAKE}——防双站过近破形。淘汰是逐列判定 ⇒ 贴近
     * 邻站的湖侧被裁（与旧 trunk 门同族的 NO_LAKE 硬边语义，S6 归因重钉）。
     * <p>
     * <b>取值推导（直跑实测重导，偏离计划初值 900 申报）</b>：站心第二近站距实测分布
     * p10/p25/med/p75/p90 = 585/725/847/987/1098（seed 0x5A614E5A，16×16 站窗）——计划初值
     * 0.75×1200=900 会裁掉 62% 站、中位湖水半径被压到 ≈40（验收 200±30 不可达）。湖对最近邻
     * 方向的水半径 {@code r = W·d/(1+W) = 0.187·d}（{@link #LAKE_WATER_LEVEL} 换算式），D_MIN
     * 裁刀生效当 {@code d − D_MIN < r ⇔ d < D_MIN/0.813}。取"水半径跌出 200 档 2/3（&lt;135 ⇒
     * d&lt;722）即判破形"的淘汰档 ⇒ <b>D_MIN = 450 = 0.375×LAKE_INTERVAL</b>（裁刀线 d&lt;553、
     * 站心整湖淘汰线 d&lt;450，实测站心 dead ≈3%）：中位湖（d=847 ⇒ 水缘处 dN=688 &gt; 450）零裁。
     * S6 SLMC 逐湖内切圆组读数后可单常量再校。
     * <p>
     * <b>P25 数值同步</b>：{@link #LAKE_INTERVAL} 1200 → 3000 ⇒ 本值自动 = 0.375×3000 =
     * <b>1125</b>（0.375 系数不动）。上文 P23 的 900/585/847 等直跑读数是 1200 档历史标定，
     * 原文保留；站距分布随间隔 ×2.5 等比搬迁，S2 SLMC 复测后可单常量再校。
     * <p>
     * <b>P32（批3 T1-4 换算统一，v1.20.55）</b>：上文的圆场换算 {@code r = W·d/(1+W)}
     * （P23-P28 圆场口径）随场换废弃（统一式见 {@link #LAKE_WATER_LEVEL} 的 P32 段）——
     * 淘汰线语义改<b>按 dN 直读</b>：站格（{@link #LAKE_INTERVAL}/细胞盐/warp）P32 未动 ⇒
     * 本值与裁刀行为数值不变，只改注。
     */
    public static final double LAKE_STATION_D_MIN = 0.375D * LAKE_INTERVAL;

    /**
     * 巨湖破圆 domain-warp 位移尺度（格；v1.20.40 P19 plan §D 新增，<b>P23 R1（v1.20.46 批2
     * S2）320 → 700</b>）：disk 噪声波长 ≈ 1.24×本值 ≈ 870 格——与标称水半径 200 同阶
     * （≈4.3×半径）⇒ 同一座湖内位移场显著变化，细胞圆被揉成不规则形。放大湖后噪声波长按
     * 同比例跟随（原 320 ≈ 200/92 × 320 ≈ 2.2×旧水径；新 700 ≈ 3.5×新水径，同为"湖内变场"档）。
     */
    public static final double LAKE_WARP_SCALE = 700.0D;

    /**
     * 巨湖破圆 domain-warp 位移幅度（格，圆盘向量全长上限）。P23 R1（v1.20.46 批2 S2）
     * 70 → 100 = 标称水半径 200 的 50%；P25（D3 轮廓自然化）100 → 85——主盘让出 15 格
     * 幅度给新增副倍频盘（{@link #LAKE_WARP_SUB}，同点相加）。{@link #lakeAt} 在湖 Voronoi
     * 求值前对坐标加 {@code disk₁(p/LAKE_WARP_SCALE)×本值 + disk₂(p/LAKE_WARP_SUB_SCALE)×LAKE_WARP_SUB}
     * （第 4/5 张 OpenSimplexDisk 表，盐 {@link #SALT_DISK_LAKE_WARP}/{@link #SALT_DISK_LAKE_SUB}）。
     * <p>
     * <b>P25 收敛账（javadoc 钉，格数口径；P32 起被下段取代，原文保留）</b>：合成位移场最大
     * 斜率上界 Σ2πA/λ = 2π×85/700 + 2π×12/340 = 0.763 + 0.222 = <b>0.985 &lt; 1</b> ⇒
     * {@link #lakeCellCenterAt} 两次不动点仍良态（两盘同点相加 ⇒ 斜率线性可加）；ΣA = 85 + 12 =
     * <b>97 ≤ 100</b> ⇒ 圆场内切圆下界 ≈ r0 − ΣA = 200 − 97 = <b>103 ≥ 100 格</b>（r0 = 标称
     * 水半径；P23 R1 单盘账 2π×100/700 ≈ 0.898、下界 100 格见版本树）。
     * <p>
     * <b>P32（T1-3 湖基场重构，v1.20.55）：85 → 30</b>——形状多样性改由逐站形状参数承担
     * （超椭圆/主副 blob 两层制 + 站参数抽签，见 {@link #SALT_LAKE_SHAPE} 段），warp 的职责
     * 收窄为"参数边界去数学化"（消超椭圆直边感/椭圆弧感）与副尺度细廓：30 = 15%·r0 足够打破
     * 几何感，同时把内切/岛预算让给形状参数（旧场 CV 0.077 由 warp 97 贡献，新场 35 档估计
     * 0.03-0.04，非圆度主力转为形状参数）。
     * <p>
     * <b>P32 收敛账（javadoc 钉，格数口径；λ 沿 P25 同一保守上界——scale 直接当 λ）</b>：
     * 合成位移场最大斜率上界 Σ2πA/λ = 2π×30/700 + 2π×5/340 = 0.269 + 0.092 = <b>0.362 &lt; 1</b>
     * ⇒ {@link #lakeCellCenterAt} 两次不动点仍良态（两盘同点相加 ⇒ 斜率线性可加）；ΣA = 30 + 5
     * = <b>35</b> ⇒ 世界内切 ≥ b_floor − ΣA − 微噪 8.3 = 185 − 35 − 8.3 = <b>141.7 ≥ 100 格</b>
     * （b_floor = {@link #LAKE_SHAPE_B_FLOOR}；E-A 终案 §2 三账——G-C 内切/岛绝对腿/G-I 岛干
     * ——共同钉死 ΣA=35 档，见 plan/tmp/p32-ea-blob-final.md §2 表）。
     */
    public static final double LAKE_WARP = 30.0D;

    /**
     * 巨湖破圆 domain-warp <b>副倍频</b>位移尺度（格，P25 D3 新增）：disk 噪声波长 ≈ 1.24×340
     * ≈ 420 格——主盘波长（≈1.24×700 ≈ 870）的 ~1/2，湖缘高频小弯的特征长度。
     */
    public static final double LAKE_WARP_SUB_SCALE = 340.0D;

    /**
     * 巨湖破圆副倍频位移幅度（格，P25 D3 新增；第 5 张 disk 表，盐 {@link #SALT_DISK_LAKE_SUB}）。
     * 与主盘<b>同点相加</b>（p' = p + disk₁(p)×{@link #LAKE_WARP} + disk₂(p)×本值，两盘各自
     * 取值后合并，见 {@link #lakeStationScan}）⇒ 合成位移 ≤ 35、合成斜率上界 0.362 &lt; 1
     * （收敛账见 {@link #LAKE_WARP}）。<b>P32（T1-3）：12 → 5</b>——ΣA = 30 + 5 = 35 把内切/
     * 岛预算让给形状参数（E-A §2 表：岛账 b_floor ≥ 139 + ΣA + 8.3 ≈ 147 + ΣA ⇒ ΣA=35 档配
     * b_floor=185 留 3 格余量）；λ 不变（700/340 频率分层保留）。P25 档"12 ≈ 当时滩带总宽
     * （≈22 格）的 ~55%"口径随场换废弃（历史保留）。
     */
    public static final double LAKE_WARP_SUB = 5.0D;

    /**
     * 巨湖水位（RTG lakeWaterLevel 同位参数）：lakePressure &lt; 本值 = 湖水区。
     * P23 R1（v1.20.46 批2 S2）0.13 → 0.23（标称水半径 200 格）。
     * <p>
     * <b>换算式（javadoc 钉）</b>：水线处 P=W ⇒ dC = W·dN，dC+dN ≈ 相邻湖站距 D ⇒
     * <b>r_w = W·D/(1+W)</b>。v1.20.45 校准点：W=0.13 ⇒ 世界水半径中位 ≈92 格（D_eff≈800）。
     * 直跑校准（seed 0x5A614E5A，16 射线×64 湖）：W=0.30 档实测水半径中位 247 ⇒ D_eff =
     * 247×1.30/0.30 ≈ 1070 ⇒ 目标 200 反解 <b>W = 200/(1070−200) ≈ 0.23</b>（复测中位 192，
     * 验收带 ≈200±30 内；越界按本式重导）。
     * <p>
     * <b>P25（D1 概率减半）：0.23 → 0.081</b>——{@link #LAKE_INTERVAL} 1200 → 3000 后按同一
     * 反解式重导：<b>W' = r/(D_eff−r) = 200/2470</b>（D_eff ≈ 0.89×3000 = 2670，0.89 沿用
     * P23 实测的 D_eff/间隔 比）⇒ 标称水半径 200 格不变。P23 的 1070/0.23 档算式见上文
     * （历史保留）；S2 SLMC 复测水半径带 ≈200±30 后按本式重导。
     * <p>
     * <b>P32（批3 T1-4 换算统一，v1.20.55）——水缘 = s=0 偏移场语义（主口径声明）</b>：
     * 新湖场 {@code P = clamp(W + s/dN)}（s = 形状边界偏移场，{@link #lakeAt0}）下水缘
     * <b>精确</b> = {@code P=W ⇔ s=0}（形状边界本身）⇒ 统一换算式钉死为线性式
     * <b>{@code 格数 = ΔP×dN/|∇s|}</b>（名义口径 {@code ΔP×D_eff = ΔP×2670}，|∇s| 参考腿=1、
     * dN 名义 = 0.89×{@link #LAKE_INTERVAL}；dN 实测中位 2072-2289 ⇒ ×格数有同比例下修容差，
     * 沿用「warp 处 ±数格出入」披露口径）。本类全部压力→格换算（{@link #LAKE_SHORE}、
     * {@link #SANZU_BIOME_SHORE_JITTER}、{@link #LAKE_AMP_BELT_DELTA}、
     * {@link #LAKE_PRESSURE_NOISE_AMP}/{@link #LAKE_PRESSURE_NOISE_AMP2}、{@link #LAKE_FADE}
     * 等）自此按本式重导。"半径"概念改<b>逐湖 32 射线水径分布</b>（SLMC G-W 等效水半径中位
     * 213-215 格 ∈ [160,230]，p10/p90 实测见判据侧）——上文 {@code r_w = W·D/(1+W)} 及
     * {@code dr/dP = D/(1+S')²}、{@code D/((1+W')(1+S'))} 三条圆场公式（P23-P28 圆场
     * r(P)=P·D/(1+P) 导出物）<b>随场换废弃</b>，历史标定原文保留在上文各段。
     */
    public static final double LAKE_WATER_LEVEL = 0.081D;

    /**
     * 巨湖床基准历史值。<b>已删字段（P23 R1·S6，v1.20.46）</b>：v1.20.40 中心渐深起生产零
     * 消费（公式改由湖滨/湖心锚派生，见 {@link #lakeBedAt}），判据侧（STC 床带派生）随整文件
     * 退役后兑现删除（历史值 63±1 见版本树）。
     */

    /**
     * 湖心最大水深（格）。<b>v1.20.41 P20 S5（plan §15.3）10.0 → 28.0</b>，本值旧口径原文保留：
     * 「v1.20.40 P19 plan §D 湖心最深 8-12 带内取 10 ⇒ 湖心锚 = {@code SEA_LEVEL − 本值} = 58」。
     * <p>
     * 改 28 的依据（§15.1 用户二次裁决「改判水深 28，放弃 40」+ S0a 实测否证旧假设）：湖心锚
     * = {@code 68 − 28 = 40}，恰落 {@code ProsperityTerrainProfile.MIN_HEIGHT = 40} 的全局高度地板
     * 上——**不动** {@code MIN_HEIGHT}/{@code SEA_LEVEL}/{@code BASE_HEIGHT}。§13 C1 的「岸底落差 ≥40」
     * 已被 {@code plan/tmp/p20-baseline/c1-drop.txt} 证伪（裸地面中位仅 69、p90 76 ⇒ 落差上界
     * 中位 29 / p90 36 / 极端 54，≥40 的湖占比 3.63%），故本值改由**水深**口径钉（判据 D1/D2），
     * 该旧口径原文保留在同一份证据文件里，不静默删除。
     */
    public static final double LAKE_CENTER_DEPTH = 28.0D;

    /**
     * 巨湖床噪声波长（格，v1.20.40 P19 §D 随湖径放大从 192 收 160——床纹波长与湖径保持
     * 同阶（≈湖径的 1.2 倍量级），破圆后的大湖不至于整湖一条直线纹理）。
     */
    public static final double LAKE_BED_NOISE_SCALE = 160.0D;

    // ═══ P24-D（v1.20.47）湖床剖面重设计：深盆外移到岛缘外 + 多段线性外坡 ═══
    //
    // 目标：让 §15.6-1 的 S1 三档列数比（浅[3,8] : 中[9,20] : 深[≥26] ≥ 1:2:2）在
    // 「保湖半径 200 / 岛半径 40 / 越往湖心整体越深」三条硬约束下成立。
    //
    // 事实（plan/tmp/ultra/lake-s1.md §3，本轮离线复算复核）：S1 的统计域 = 湖水区 ∧ 非岛域 =
    // {p < LAKE_WATER_LEVEL} ∖ {p < LAKE_ISLAND} = u ∈ [LAKE_ISLAND/LAKE_WATER_LEVEL = 0.652, 1]
    // （u = p/LAKE_WATER_LEVEL）。旧剖面（平台 0.55 + 单段 smoothstep）的深档落在 u < 0.586，
    // 整条 ⊂ 岛域 ⇒ 深档恒 0；且中/浅在 smoothstep 的"中部压缩"下车给只有 1.34（床口径 1.74）。
    // （P25 重定标后岛缘 u = 0.055/0.081 = 0.679 < 0.790——平台仍在岛缘之外，下述判据结构不变。）
    //
    // 设计式（u ∈ [0,1]，bed = shoreBed + (centerBed−shoreBed)·g + 0.5 + 0.5·noise）：
    // g(u) = 1 u ≤ LAKE_BED_PLATEAU(0.790)
    // g(u) = 线性插值(U_k, G_k) → (U_{k+1}, G_{k+1}) U_k < u ≤ U_{k+1}
    // U = {0.790, 0.850, 0.950, 0.982, 1.000}
    // G = {1.000, 0.775, 0.360, 0.130, 0.000}
    //
    // 理由（三段各自对应一条判据，均为纯模型 835 万列离线复算 + SLMC 实测双证）：
    // ① 深档露出：深档 = h ≤ 41，等价于 (g, noise) 落在宽 1/27 的 g 窗内 ⇒ <b>把近 g=1 的
    // 平坦段从 0.55 外移到 0.790</b>（> 岛缘 0.652）后，该 g 窗映射成一段有面积的 u 区间
    // （岛外环带 0.652→0.790），深档从 0 抬到 ≈岛缘外整圈；
    // ② 中带加厚：中档 = h ∈ [47,58] ⇔ g ∈ [0.36, 0.77]，本设计让这一段占据 <b>0.850→0.950</b>
    // 的宽 u 区间（斜率 4.15，比旧 smoothstep 的"中部压缩"把更多 u 面积交给中档）；
    // ③ 浅带收窄：浅档 = g ∈ [0.13, 0.36]，压在 <b>0.950→0.982</b> 的窄段（斜率 7.19），
    // 面积随之从 90 万→30 万量级；末段 0.982→1.000 把 g 收回 0，水缘床回到湖滨锚（深度 ≤1）
    // ⇒ 水缘不出现 ≥5 格悬崖（A1）。
    // 单调不增 ⇒ "越往湖心整体越深"（岛周深潭 = 剖面在 u<0.790 的平坦段，属局部重塑不是"边缘最深"）。
    //
    // 读数：SLMC S1 由 1:1.337:0.000 变为 <b>1:3.06:3.24（绿）</b>；离线模型（4 seed×6144²=835 万
    // S1 域列，逐列 y_pre 直证）同值。坡度上界 7.22 g/u ⇒ 27×7.22×q99(du/列)=1.6 格/列，
    // 仍低于 A1 的 5 格阈与 A2 的 p95 ≤ 2（A 组实测未变，见 SLMC A-READ）。

    /**
     * 深盆平台<b>外缘</b>（u 域，u = {@code lakeAt / LAKE_WATER_LEVEL}）：{@code u ≤ 本值} 的整片湖心区
     * 床高恒取湖心锚（40 + 非负床纹），外坡由 {@link #LAKE_BED_KNOT_U}/{@link #LAKE_BED_KNOT_G}
     * 的多段线性给出。<b>P24-D（v1.20.47）由 0.55 → 0.790</b>（外移到岛缘 0.652 之外，见上面的设计式）。
     * <p>
     * <b>⚠ 下面 0.55 档（P20 S5b，单段 smoothstep 外坡）的原文保留不删，作历史证据</b>——它是"S1 三档比
     * 在本形状族内不可达"的实测依据（中/浅恒 1.34–1.74 &lt; 2），P24-D 正是据此改判形状族。文中
     * "维持 0.55"、"外段 smoothstep"、"平台占比"等表述均为该旧口径，<b>已被 P24-D 覆盖</b>。
     * <p>
     * ── 以下为 0.55 档原文 ──
     * <p>
     * 湖床渐深的<b>深盆平台占比</b>（湖压力 u = {@code lakeAt/LAKE_WATER_LEVEL} ∈ [0,1] 的域内比例，
     * v1.20.41 P20 S5 新增；<b>S5b 复扫后维持 0.55</b>）：{@code u ≤ 本值} 的整片湖心区床高恒等于
     * 湖心锚（40 + 非负床纹），只在 {@code (本值, 1]} 的外段做 smoothstep 抬升到湖滨锚 67。
     * <p>
     * <b>本常量存在的理由（S5b 按 §25 改对的口径分层——S5 原注释把动机记在 D1/D2 上，那是错的）</b>：
     * <ul>
     * <li>§15.3 的 <b>D1/D2 是「逐湖」占比</b>（"湖心床 minH ≤ 41 的<b>湖</b>占比 ≥90%"、"逐湖水深 ≥26
     * 的<b>湖</b>占比 ≥90%"）⇒ <b>纯碗形即满足</b>（碗心天然触底），与本值几乎无关：S5b 实扫
     * plateau ∈ {0.35, 0.40, 0.45, 0.50, 0.55, 0.60} 六档，D1/D2 <b>逐档都是 100%</b>（证据
     * {@code plan/tmp/p20-s5b/plateau-scan.md}）；</li>
     * <li>真正需要平台的是 <b>§15.6-1 的三档列数比 浅:中:深 ≥ 1:2:2</b>——它是<b>逐列面积</b>口径，
     * 纯碗形下"水深 ≥26 的湖底列"只占面积 <b>(0.166)² ≈ 2.8%</b>（推导：{@code g ≥ 25/27 ⇒ u ≤ 0.166}），
     * 方向与判据相反，单靠 {@code LAKE_CENTER_DEPTH} 无解。平台把深盆从"针尖"摊成"整片湖底"。</li>
     * </ul>
     * <p>
     * <b>为什么维持 0.55（§25 的"取小值"条件在全档都不成立，S5b 扫描是证据）</b>：§25 给的规则是
     * "若取 &lt;0.55 仍能满足 1:2:2 与 D1/D2 ⇒ 取小值"。实测两数：① <b>三档比的中/浅档在本形状族内
     * 恒 ≈1.70–1.73</b>（床公式口径六档扫描，见 plateau-scan.md）⇒ <b>1:2:2 对任何本值都不可达</b>，
     * "仍满足"这个前提从未成立；② 唯一<b>已成立</b>的一半是"深 ≥ 2×浅"，且它随本值单调
     * （heightAt 实测：0.55 → 浅:中:深 = 1:1.338:<b>2.151</b>；0.40 → 1:1.253:<b>1.007</b>）
     * ⇒ 取 0.40 会把目前<b>唯一达标</b>的那一半也打掉。于是在"整条判据不可达"的事实下，本值取该族的
     * argmax = 0.55，而不是任意"更小以保渐深"。<b>实机后校准 ∈ {0.55, 0.60}</b>；
     * §15.6-1 本身要转绿必须换床剖面形状（幂律/多段），属常量域外的形状裁决，已交回主代理。
     * <p>
     * <b>"浴缸感"由哪四条实测守住（§25 点名的申报义务，S5b 在 0.55 档实跑）</b>：
     * ① 外段坡面 <b>0.98 格/列</b>（导数上界见下）＋ 实测相邻列高差 <b>p95 = 1 格</b>、
     * 径向 ≥5 格单级跳变 <b>0.28%</b> ⇒ 水线以上没有陡壁；
     * ② 外缘 |Δh| ≥ 5 的列占比 <b>0.17%</b>（A1 带 &lt;1%）⇒ 湖岸衔接不被平台边缘打断；
     * ③ 多级缓坡的<b>结构</b>级数 = {@link #LAKE_SHORE_TREADS} + 1 = <b>5</b> 个量化级（A4a；实测台阶数受
     * 岸地面中位仅 69 的总抬升量限制，逐湖中位 3 级，已按 §23-B 法降级为只报不钉）；
     * ④ 湿带实测宽 = 环带列的 <b>33.87%</b>、逐湖弧宽中位 <b>6.6 格</b>（A5-READ）⇒ 水陆之间是渐变湿滩。
     * 面积侧的直读：床公式口径"深井 ≥26"占 58.96%，但 {@code heightCore} 的
     * {@code Math.min(原地形)} 钳制把外半稀释掉 ⇒ <b>heightAt 实测只占 35.84%</b>，
     * "全湖 &gt;50% 是 26+ 平底"这一 §25 担心的形态在实测里不成立。
     * <p>
     * 外段坡度上界（衔接安全性）：最大导数在 v=0.5（u=0.775）处，{@code dg/du = 1.5/(1−0.55) =
     * 3.33} ⇒ 每单位压力降 27×3.33 = 90 格高，除以实测 {@code dr/dW = 708.6 格/单位压力 × 0.13}
     * ≈ 92 格/单位 u ⇒ <b>≈0.98 格/列</b> ⇒ 远低于 A1 的 5 格悬崖阈与 A2 的 p95 ≤ 2。
     * <p>
     * <b>S5 原推导（本轮废止其比值结论，原文保留不删）</b>："三档列数比按面积算（面积 ∝ u²）——深档
     * u ∈ [0, 0.55 + 0.146·0.45 = 0.616] ⇒ 面积占比 0.379；中档（水深 9–20）u ∈ [0.664, 0.831] ⇒ 0.311；
     * 浅档（3–8）u ∈ [0.838, 0.934] ⇒ 0.176 ⇒ 比 ≈ 1 : 1.77 : 2.26。0.55 是该族里让'深 ≥ 2×浅、
     * 中 ≥ 2×浅'同时最接近成立的点"——它的<b>结论方向对、量级错</b>：中/浅实测恒 ≈1.72（六档），
     * 到不了 2；该算式把"深井"档按 u 区间宽估，漏了取整与外段 {@code min(原地形)} 钳制的稀释。
     * 它同时把本常量的动机记在 D1/D2 上（见上面的口径分层第一条），<b>该句是错的、已废止</b>。
     */
    public static final double LAKE_BED_PLATEAU = 0.790D;

    /**
     * 湖床外坡剖面的<b>控制点</b>（P24-D 新增，与 {@link #LAKE_BED_PLATEAU} 拼接：{@code u ≤ 平台} 恒 g=1）：
     * 数组下标对齐，{@code U} 严格升序、{@code G} 单调不增，段内线性插值。
     * <p>
     * 形状的<b>唯一真值</b>（生产 {@link #lakeBedAt} 与离线判据 {@code SanzuLakeMorphologyCheck.bedFormula}
     * 共用同一组字面量并逐位对拍 P0 钉住，禁第二份实现）。设计式与逐段理由见本常量上方 P24-D 段。
     * <p>
     * 取值表（u, g）：(0.790, 1.000) → (0.850, 0.775) → (0.950, 0.360) → (0.982, 0.130) → (1.000, 0.000)；
     * 对应水深带：深（h≤41，g 窗宽 1/27）/ 深-中过渡（h 42–46）/ 中（h 47–58）/ 浅（h 59–64，窄）/ 水缘（h≥65）。
     */
    public static final double[] LAKE_BED_KNOT_U = { 0.790D, 0.850D, 0.950D, 0.982D, 1.000D };

    /** 与 {@link #LAKE_BED_KNOT_U} 对齐的 g 控制点（单调不增）。 */
    public static final double[] LAKE_BED_KNOT_G = { 1.000D, 0.775D, 0.360D, 0.130D, 0.000D };

    // ═════════════ v1.20.41 P20 S5（plan §15）：中心固定岛 / 岛底柱 / 湖滨多级缓坡 / 湿带 ═════════════

    /**
     * 中心固定岛的**岛内压力腿阈**（三条腿之一；另两条见 {@link #LAKE_ISLAND_PLATEAU} 与
     * {@link #LAKE_ISLAND_RADIUS}）：{@code lakeAt <} 本值 = 岛域（压力腿）。必须 &lt;
     * {@link #LAKE_WATER_LEVEL}（岛在湖水区内）⇒ 岛域恒 ⊂ 湖水区，§15.4 的 A 组判据由构造隔离。
     * <p>
     * <b>§15.5 原文与该行的"实测反解"凭据（原文保留不删）</b>："0.030 档岛半径中位 30.375 ⇒ 干列
     * ≈2903，是判据上界 1050 的 2.8 倍、只有 2.12% 湖落在几何解带内；0.013 档岛半径中位 16.875、
     * 均值 15.918 ⇒ <b>岛直径 ≈30–32 格</b>，正是用户「岛大小约 30 格」，带内命中 65.69%（峰档）"。
     * <p>
     * <b>⚠ v1.20.41 P20 S5c 假因登记（上面那句"0.013 档 ⇒ 直径 30–32"已被证伪，本值随之改判）</b>：
     * 反解用的 16.875 / 15.918 / 30.375 全是 S0a 的 <b>9 格射线均值半径</b>
     * （{@code plan/tmp/p20-baseline/c5-island.txt}，RAY_STRIDE = LAKE_STRIDE/4 = 9）——射线法读的是
     * "岛面等值线跌到水面以下"的那一格，而判据数的是<b>逐列干面积</b>；穹顶形状下两者不重合 ⇒
     * 射线法<b>系统性高估</b>。S5b/S5c 逐列实测：0.013 档岛<b>域</b>（{@code lakeAt < 0.013}）中位
     * 397 列 = 半径 11.24（直径 22.5，<b>不是 30–32</b>），其中<b>干</b>列中位只有 <b>23</b>（半径 2.7）。
     * 逐列真值反解：穹顶的干面积 = 岛域面积 × 0.0486（干阈 {@code h ≥ SEA_LEVEL = 68} ⇔
     * {@code s01(k) ≥ 28/32} ⇔ {@code k ≥ 0.7795}），要干列进 {@code [600,1050]} 需岛域半径 63–68 格
     * ⇒ 纯压力腿要达标必须 {@code LAKE_ISLAND ≈ 0.075–0.080}，代价是水下浅棚占掉中位湖面积的 33%
     * （直接吃光 §15.6-1 的三档与"越往中心越深"）⇒ <b>单抬本值不可解</b>。§15.5 表那一行按本节
     * 口径覆盖（同 §14/§15 覆盖 §11、§26 覆盖 §23-B 的处理法：原文留、假因点名、不静默删）。
     * <p>
     * <b>本值 0.15 的取法（P23 R1，v1.20.46 批2 S2，岛半径 40 档）</b>：压力腿域半径
     * {@code r(I) = I·D/(1+I)}（D = 相邻湖站距）须 ≥ 绝对腿上限 {@link #LAKE_ISLAND_RADIUS}=75
     * 才不先于绝对腿裁形 ⇒ I ≥ 75/(D−75)；D 的有效下界 = 淘汰线上沿 ≈ 585（本档
     * {@link #LAKE_STATION_D_MIN}=450 的 1.3 倍，见其推导）⇒ I ≥ 75/510 ≈ 0.147，取 0.15
     * 留余量（D=585 时域半径 76.4、D=847 时 110.5 ⇒ 绝对腿正常钉形）。
     * 岛域 ⊂ 湖水区（0.15 &lt; {@link #LAKE_WATER_LEVEL}）由构造保持。v1.20.45 及更早的
     * 0.045/0.013 档校准史见版本树（S5c 假因登记：射线法系统性高估岛半径，逐列真值口径为准）。
     * <p>
     * <b>P25（D1 概率减半）：0.15 → 0.055</b>——同一"压力腿域 ≥ 绝对腿上限"反解式按新
     * {@link #LAKE_STATION_D_MIN}=1125 重导：活湖下界 D_low ≈ 1.3×1125 = 1462.5 ⇒
     * <b>I ≥ 75/(D_low−75) = 75/1387.5 ≈ 0.0541，取 0.055</b>（D_low 档域半径 76.3 ≥ 75 ⇒
     * 绝对腿仍正常钉形；中位 D=2670 档域半径 ≈139 ≫ 75）。<b>I' &lt; W'</b>（0.055 &lt;
     * 0.081）⇒ 岛域 ⊂ 湖水区不变式保持（含 {@link #LAKE_ISLAND_GATE_JITTER} 单边 +8% 后
     * T_max = 0.0594 &lt; 0.081，余量 0.0216 ≫ 压力噪声幅 0.0035）。G-I 带（岛缘→水缘的
     * 环带格宽，D ∈ [1462,2100] 档 ≈ [33,48]）随本轮重定标，<b>[32,48] 复测钉</b>归 S2。
     */
    public static final double LAKE_ISLAND = 0.055D;

    /**
     * 岛面的<b>平台半宽</b>（k 域，∈ (0,1]；v1.20.41 P20 S5c 新增）：岛抬升形状由纯穹顶
     * {@code s01(k)} 改为 {@code s01(min(1, k/本值))} ⇒ <b>k ≥ 本值的整段都是满高岛面</b>
     * （平台），全部 32 格落差被压进外檐 {@code k < 本值} 的环带里。取 1.0 时与纯穹顶<b>逐位等价</b>。
     * <p>
     * <b>为什么必须有这条支路（S5c 的解析结论，先算后改）</b>：干列判据要求干面积 ∈[600,1050]
     * = 干半径 {@code r_d ∈ [13.82,18.28]}，而穹顶形状下干半径 = 岛域半径 × (1 − 0.7795)（干阈
     * {@code h ≥ 68 = SEA_LEVEL} 反解 s01(k) ≥ 28/32 ⇒ k ≥ 0.7795），故纯穹顶要达标必须把岛域
     * 半径推到 63–68 格 ⇒ {@code LAKE_ISLAND ≈ 0.075–0.080} ⇒ 岛域（水下浅棚）面积
     * {@code π·68² ≈ 14500} 列 = 中位湖水面积的 <b>33%</b>，把 §15.6-1 的三档与"越往中心越深"
     * 整个吃掉。改平台后同样的 {@code r_d} 只需岛域半径 25–34 格（占湖面积 4–8%）。
     * <p>
     * <b>与外檐坡度的取舍</b>（同一条式的两端）：最大径向爬升 = {@code 48/(本值 × 岛域半径)}
     * （s01 导数上界 1.5、落差 32 格）。穹顶在 0.013 档的实测值是 {@code 48/11.04 = 4.35} 格/列
     * ——即<b>改造前的岛缘本来就比注释里宣称的 2.8 陡</b>（2.8 是用被证伪的射线半径 17 算的，
     * 见 {@link #LAKE_ISLAND} 的假因登记）。平台系在本值 ∈[0.6,0.8] 时坡度降到 1.4–2.9 格/列，
     * <b>同时</b>把岛做宽 ⇒ 形态与判据两个方向都变好，不是拿坡度换面积。
     * <p>
     * <b>终值 0.60</b>（与 {@link #LAKE_ISLAND_RADIUS} = 30 配套，反解式 {@code 干半径 = 半径上限 ×
     * (1 − 0.7795·本值) = 30 × 0.532 = 15.96 ⇒ 干面积 800 列 = 直径 31.9 格，落在 §15.5 的
     * [600,1050] 带内且留足两侧余量}）：再小（0.50 档实测干 811、带内同为 90.63%）只把外檐坡度
     * 从 2.67 推到 3.70 格/列、换不到任何判据收益；再大（0.75 档）坡度 1.73 但要配 38.5 的半径上限、
     * 水下浅棚从 2827 列摊到 4657 列 ⇒ 三档比 S1 被多剔一圈床列。0.60 是"坡度不陡于既有穹顶 +
     * 浅棚最小"的那一点。实测岛缘悬崖列（{@code islandCliffCols}，A1 同阈 5 格、只报不钉）见扫描表。
     */
    public static final double LAKE_ISLAND_PLATEAU = 0.60D;

    /**
     * 岛域的<b>绝对半径上限</b>（格，从湖心量；v1.20.41 P20 S5c 新增）。岛域 = 两条腿的<b>交</b>：
     * 压力腿 {@code lakeAt < LAKE_ISLAND}（尺度归一，随湖大小缩放）与本腿
     * {@code r(湖心) < 本值}（绝对）。{@code k = min(压力腿 k, 1 − r/本值)}。
     * <p>
     * <b>为什么必须有绝对腿（扫描数据选的，不是感觉选的）</b>：需求 8 原话「岛大小约 30 格」是
     * <b>绝对</b>尺寸，§15.5 的 C_DRY 带 {@code [600,1050]}（= 半径带 {@code [13.82,18.28]}，
     * 宽 1.32 倍）也是绝对的；而纯压力腿的岛面积 ∝ 湖面积，实测逐湖尺度散布
     * p10/p90 = 0.78/1.23（半径）⇒ 干面积散布 2.5 倍 &gt; 带的 1.75 倍 ⇒ <b>带内命中率数学上限
     * ≈56%</b>（实测峰档 46.88%，见 {@code plan/tmp/p20-s5c/island-scan.md}），永远喂不饱
     * §15.6-3 的 60% 门。加绝对腿后大湖被钉在同一个直径、命中率随之以外的湖只剩"比上限还小"的
     * 左尾 ⇒ 需求 8 的"约 30 格"与已钉的判据带第一次真正互洽。
     * <p>
     * <b>取值</b>：本值 × (1 − 0.7795·{@link #LAKE_ISLAND_PLATEAU}) = 干半径目标（干阈
     * {@code h ≥ SEA_LEVEL = 68} 的反解）。上限只收不放 ⇒ 小湖由压力腿自己缩回去，
     * <b>不会</b>把岛推进 §15.4 的环带（A 组的取样域是 {@code [LAKE_WATER_LEVEL, LAKE_SHORE)}，
     * 岛域恒 ⊂ 湖水区 ⇒ 由构造隔离，S5c 双跑复核）。终值与推导见 {@link #LAKE_ISLAND} 的假因段。
     * <p>
     * <b>终值 75（P23 R1，v1.20.46 批2 S2，岛半径 40 档）</b>：干半径 = 本值 ×
     * (1 − 0.7795·{@link #LAKE_ISLAND_PLATEAU}) = 75 × 0.5323 ≈ <b>39.9 ≈ 40 格</b>
     * （0.7795 = 干阈 {@code h ≥ SEA_LEVEL=68} 的 s01 反解 k 值；PLATEAU 0.60 不变）。
     * 本值是<b>warped 空间</b>的绝对半径（dC 同域）——世界空间岛半径 = c×39.9（c = warp
     * 压缩系数，同 {@link #LAKE_WATER_LEVEL} 换算式），直跑岛心采样校准（验收带 ≈40±8）。
     * v1.20.45 的 30 档（干直径 ≈31.9）与推导史见版本树（S5c 扫描表 island-scan.md）。
     * <p>
     * <b>P25（D3⑤(a)）站级半径抖动</b>：{@link #lakeIslandTopAt} 的绝对腿改用
     * R_eff = 本值×(1 ± {@link #LAKE_ISLAND_RADIUS_JITTER}·u)（u = 站键 hash01 的 [−1,1)
     * 映射，逐湖常量）⇒ R_eff ∈ [67.5, 82.5]、干半径 ∈ [35.9, 43.9]——仍在本值 ≈40±8 的
     * 验收带内，岛底柱不变式（环 13+抖 2+半宽 2 = 17 &lt; 35.9）保持。
     */
    public static final double LAKE_ISLAND_RADIUS = 75.0D;

    /**
     * 岛绝对半径的<b>站级抖幅</b>（P25 D3⑤(a) 新增）：R_eff = {@link #LAKE_ISLAND_RADIUS}×
     * (1 ± 本值·u)，u = hash01(站键) 映射 [−1,1) ⇒ R_eff ∈ [0.90, 1.10)×75。<b>站键 = 本列
     * 未 warp 的标称湖格</b>（floor(p/{@link #LAKE_INTERVAL}+0.5)，键盐
     * {@link #SALT_LAKE_ISLAND_RADIUS} 经 cellSeed 混淆）——岛列到所属格点 ≤ 0.35×间隔 +
     * 合成 warp 97 + R_eff ≈ 1238 &lt; 半间隔 1500 ⇒ <b>同一座岛的岛列恒得同一站键</b>（无
     * 跨格缝），且零独立 warp/第二不动点（半径腿仍吃同一次 3×3 扫描的 dC）。（P32 起
     * 合成 warp ΣA = 35 ⇒ 该账 ≤ 1168，论证不变。）
     */
    public static final double LAKE_ISLAND_RADIUS_JITTER = 0.10D;

    /**
     * 岛压力腿阈的<b>阈值抖动</b>（P25 D3⑤(b) 新增）：T = {@link #LAKE_ISLAND}×(1 + 本值×n01)，
     * n01 = λ{@link #LAKE_ISLAND_GATE_SCALE} 低频 valueNoise 的 [0,1) 归一（盐
     * {@link #SALT_LAKE_ISLAND_GATE}）。<b>单边只收域</b>（T ≥ LAKE_ISLAND；与
     * {@link #sanzuBiomeShoreAt} 同一"只往安全侧摆"纪律——T_max = 0.0594 &lt; W'=0.081，
     * 岛域 ⊂ 湖水区不变式保持）。kPress 归一分母随 T 同源（不留"过阈即 k≤0"死带）。
     */
    public static final double LAKE_ISLAND_GATE_JITTER = 0.08D;

    /**
     * 岛阈抖噪声波长（格，P25 D3⑤(b) 新增）：岛缘特征长（干半径 ≈40）的 ~2/3 量级；
     * 60 % 16 = 12 ≠ 0 ✔（账本 §9 chunk 对齐条纹坑的取值纪律）。
     */
    public static final double LAKE_ISLAND_GATE_SCALE = 60.0D;

    /**
     * 岛站级半径抖动的站键盐（P25 D3⑤ 新增）：盐段尾续取 {@code …118L}（…115/…116/…117 已被
     * {@link #SALT_DRY_RIVER}/{@link #SALT_DISK_LAKE_SUB}/{@link #SALT_LAKE_PRESSURE} 占用）。
     */
    public static final long SALT_LAKE_ISLAND_RADIUS = 0x5249F118L;

    /**
     * 岛压力腿阈抖噪声盐（P25 D3⑤ 新增）：盐段尾续取 {@code …119L}（…118L 已被
     * {@link #SALT_LAKE_ISLAND_RADIUS} 占用）。
     */
    public static final long SALT_LAKE_ISLAND_GATE = 0x5249F119L;

    /**
     * 岛绝对腿<b>周向起伏</b>噪声盐（P26-B3 D1·I1' 新增，v1.20.49）：盐段尾续取
     * {@code …11AL}（…117/…118/…119 已被湖压力/岛半径/岛阈抖占用；…11B 归
     * {@link #SALT_LAKE_PRESSURE2}）。I1' = kAbs 的 dC 起伏噪声（破岛圆的周向起伏，见
     * {@link #LAKE_ISLAND_EDGE_LO_AMP}）。
     * <p>
     * <b>P29-A2（多频加性改造）起本盐服务低频大瓣腿</b>（LO 腿，λ
     * {@link #LAKE_ISLAND_EDGE_LO_SCALE}=124）——同盐改 λ/幅度先例 = P28-L 岛腿
     * λ90→56（D1·I2，盐不动只改档）；中频腿独立盐 {@link #SALT_LAKE_ISLAND_EDGE_MID}。
     */
    public static final long SALT_LAKE_ISLAND_EDGE = 0x5249F11AL;

    /**
     * 岛绝对腿<b>多频 ridged 脊形加性——低频大瓣幅度</b>（格，P29-A2 新增，A2-R 改 ridged）：
     * {@code kAbs = 1 − (dC + 本值·g₁ + MID·g₂)/rEff}，g = s01((1−|n|−{@link
     * #LAKE_ISLAND_EDGE_RIDGE_GATE_LO})/{@link #LAKE_ISLAND_EDGE_RIDGE_GATE_SPAN})——
     * <b>ridged 门形</b>（脊核 |n|≈0 处满门=深咬、带外=0；P28-L 湖缘缎带腿同款先例——该腿
     * 已于 P32 随湖基场重构整体移除，先例算式见版本树），n₁ = λ
     * {@link #LAKE_ISLAND_EDGE_LO_SCALE}、n₂ = λ{@link #LAKE_ISLAND_EDGE_MID_SCALE}、
     * MID 见 {@link #LAKE_ISLAND_EDGE_MID_AMP}。
     * <p>
     * <b>为什么 ridged（A2-R，主代理裁决①）</b>：P29-A2 首档（plain 加性 n₁/n₂）实测岛环
     * 有效瓣 med=2（p29-a-readings §5）——valueNoise 沿半径 40 环的振荡极值仅 ~0.4×(周长/λ)
     * 个，plain 形的半径极大=噪声极值，瓣数天花板 ≈2-3；ridged 门在<b>零交叉</b>处成脊
     * （交叉数≈极值总数），且门形"带外精确 0"让脊间段保持基线半径 ⇒ 每个脊都是满对比度的
     * 内咬缺刻（prominence ≈ 幅度本值），瓣数=脊数可由 λ 直接调度。
     * <p>
     * <b>几何账（本值 12 + MID 7）</b>：单侧内咬 pull ∈ [0,19] ⇒ 干沿半径 = 0.5323·rEff −
     * pull ⇒ 可见缺刻深 LO 12/MID 7（prominence ≥4 门的几何保障）；岛域 ⊂ 湖水区不变式：
     * 压力腿 islandGate（lakeIslandTopAt 先门）未动 ⇒ 保持；加性项只平移等值线不反号。
     * <b>柱不变式</b>：kAbs = 0 域半径 ≥ rEff,min(67.5) − 19 = <b>48.5 ≫ 17</b>（环 13+抖 2+
     * 半宽 2，P25 D3⑤(a) 同款账）。<b>spread 天花板账（登记，不降门）</b>：单侧拉扯
     * p90−p10 ≤ LO+MID = 19 &lt; 24 目标——A2-R 按"先达瓣数门、spread 按实测算术给重钉建议"
     * 执行（读数 §A2-R）。R2 降级链（C1/C1b 岛干面积带越幅 &gt;40% 或 MegaTree 重钉
     * &gt;2 格）：12 → 8（p29 计划 §2-A 失败路径）。
     */
    public static final double LAKE_ISLAND_EDGE_LO_AMP = 12.0D;

    /**
     * 岛绝对腿多频 ridged——<b>低频大瓣波长</b>（格，P29-A2 新增）：λ = 124（P29 与当时湖缘
     * 缎带 λ 同档的"低频宽湾"波长纪律——岛大瓣与湖湾臂频谱对齐；缎带腿已于 P32 随湖基场重构
     * 整体移除，本值独立存续）；
     * 124 % 16 = 12 ≠ 0 ✔（账本 §9 取值纪律）。环密度账：半径 40 环（周长 ≈251）在 λ124
     * 档 ≈1 个大脊（岛剪影级单侧大咬）。<b>求值域</b>：只在岛域列（压力腿过门后，全湖
     * ≈5% 列）求值 ⇒ 岛域外零成本（短路保持）。<b>盐</b>：复用
     * {@link #SALT_LAKE_ISLAND_EDGE}（同盐改 λ/幅度先例：P28-L λ90→56）。
     */
    public static final double LAKE_ISLAND_EDGE_LO_SCALE = 124.0D;

    /**
     * 岛绝对腿多频 ridged——<b>中频缺刻幅度</b>（格，P29-A2 新增，A2-R 改 ridged）：同一
     * 加性式的第二频（见 {@link #LAKE_ISLAND_EDGE_LO_AMP} 算式），7 格缺刻深叠在 12 格大脊上
     * ⇒ 单点最大内咬 19。失败迭代档（岛瓣数 med &lt;5 或 min &lt;4 时）：SCALE 逐档下调
     * （p29 redirect 执行要求 5；λ 必须满足 %16≠0——16 档禁用）。
     */
    public static final double LAKE_ISLAND_EDGE_MID_AMP = 7.0D;

    /**
     * 岛绝对腿多频 ridged——<b>中频缺刻波长</b>（格，P29-A2 新增；<b>A2-R：52 → 20 → 18 → 14</b>）：
     * 环密度算术（redirect 执行要求 1"确保环上格点密度足以出 ≥5 个脊"）——valueNoise 脊密度
     * 实测系数 ≈0.32×(周长/λ)（零交叉 + 浅 |n| 低谷入带）：λ52 ≈2 脊（A2 首档瓣 med=2 根因，
     * simr.out）；λ20 ≈5.6 脊（r1 实测 med=5 ✔ min=2 ✗）；λ18 ≈6.4 脊（r2 实测 med=6 ✔
     * min=3 ✗，两低瓣湖环周长 188/213）。<b>min 门（≥4）反解：λ ≤ 0.32×188/4 ≈ 15 ⇒ 迭代 2
     * 取 λ14</b>（14 % 16 = 14 ≠ 0 ✔；16 档 %16=0 违反账本 §9 纪律禁用）：中位环（周长 251）
     * ≈5.7 脊、小环 ≈4.3 脊。缺刻宽 ≈6-8 格、脊距 ≈30-44 格——岛缘"多缺刻不规则海岸线"
     * 读感（与 LO λ124 大脊双尺度分层）。<b>盐</b>：{@link #SALT_LAKE_ISLAND_EDGE_MID}
     * 独立盐域（同盐不同 λ = 同底层格点场重采样的强相关档，必须分盐——
     * {@link #SALT_LAKE_PRESSURE2} 纪律同款）。
     */
    public static final double LAKE_ISLAND_EDGE_MID_SCALE = 14.0D;

    /**
     * 岛缘 ridged 门<b>脊阈</b>（A2-R 新增，redirect 执行要求 1）：g =
     * s01((1−|n|−本值)/{@link #LAKE_ISLAND_EDGE_RIDGE_GATE_SPAN})——带外（|n| ≥ 1−本值 =
     * 0.24）g=0 精确 ⇒ 脊间段半径=基线（prominence=幅度满对比）；脊核（|n| ≤ 0.12）满门。
     * 取 0.76（P29 与当时湖缘缎带门 0.68 同族偏紧档——缎带已于 P32 随湖基场重构整体移除，
     * 档位独立存续；门带宽 vs 缺刻分离度的折中：更宽（0.68）则 λ20 缺刻粘连、更窄（0.84）
     * 则缺刻变浅——simr.out 扫描 3×2 档取 spread/瓣数最优）。
     */
    public static final double LAKE_ISLAND_EDGE_RIDGE_GATE_LO = 0.76D;

    /**
     * 岛缘 ridged 门<b>缓入宽度</b>（A2-R 新增）：0.12 ⇒ 缺刻缘坡 ≈ 幅度 ÷ 半带宽弧长
     * ≈ 7÷5 ≈ 1.4 格/列（λ20 档）&lt; 滩台阶 riser 3.5——缓入纪律与 P29 时缎带门 SPAN
     * 同款（缎带腿已于 P32 移除）。
     */
    public static final double LAKE_ISLAND_EDGE_RIDGE_GATE_SPAN = 0.12D;

    /**
     * 岛绝对腿<b>中频细部噪声盐</b>（P29-A2 新增，v1.20.52 计划轨道）：盐段尾续取
     * {@code …11EL}——实现期 grep 全 src+tools/dim1 已核零占用（…11D 归湖缘缎带盐——
     * <b>P32 随缎带腿整体移除而退役，不回收不复用</b>，退役登记见 {@link #SALT_LAKE_SHAPE}
     * 段；…11E 归本盐；…11F 归 {@link #SALT_LAKE_SHAPE}（P32 T1-3 站形状参数抽签盐）；
     * …120 归 {@link #SALT_SANZU_BIOME_SHORE_WIDTH}（P32 批3 T1-4 滩宽低频调制盐；…121 段尾
     * 空闲）；占用则回退 …120（p29 计划 §2-A A2，实测未占用 ⇒ 取 …11E）。
     * 退役盐不回收不复用。
     */
    public static final long SALT_LAKE_ISLAND_EDGE_MID = 0x5249F11EL;

    /**
     * 岛面相对 {@link ProsperityTerrainProfile#SEA_LEVEL} 的抬升（格）⇒ 岛面 = 68 + 4 = <b>72</b>。
     * 取 4 的依据（§5 S5-5）：P20 S3 的 {@link #BANK_CUT_DEPTH} 会再削岸 2 格 + 1 格容差 + 1 格
     * 观感余量；<b>本轮取 4，实机后校准</b>。离 {@code MAX_HEIGHT=110} 余 38、离
     * {@code HEIGHT_SENTINEL=108} 余 36 ⇒ 与两个上界零接触（§15.5 表最后一列）。
     */
    public static final double LAKE_ISLAND_LIFT = 4.0D;

    /**
     * 湖滨带 [WATER, SHORE) 的<b>台阶级数</b>（plan §15.4「多级缓坡 ≥3 个高差 ≤2 的台阶，
     * 非单一大台阶」）：把原来的线性 lerp 换成 {@code round(s01(t)·N)/N} 阶梯，N 级 ⇒ 每级
     * 踏面是等压环、每级 riser = 总抬升/N。取 4 的理由：环带实测宽 ≈14 格（见 {@link #LAKE_SHORE}
     * 注释）⇒ 4 级 = 每级踏面 ≈3.5 格，肉眼可读成"层叠滩地"而非噪声；<b>实机后校准 ∈{3,4,5}</b>。
     */
    public static final int LAKE_SHORE_TREADS = 4;

    /**
     * <b>湖域振幅带的压力域带宽</b>（P27-L D1·2c 新增，v1.20.50；消费面 =
     * {@code ProsperityTerrainProfile.heightCore} 的振幅位）：带内 {@code lake ≤ shoreAt} 恒取
     * sanzu 振幅档（{@code RELIEF_AMPLITUDE_BY_ROSTER[4]}=0.38，档表单源、禁字面量），带外
     * {@code s01((lake−shoreAt)/本值)} 收敛回 {@code ampAt} 连续场——smoothstep 起点导数 0 ⇒
     * C1 连续，shoreAt 抖动只摆动起点不造坎（GenLayer 红线约束下"湖域列用 sanzu 振幅档"的唯一
     * 等价物：核原料从身份面换成压力面）。
     * <b>换算式（格数口径，javadoc 钉）</b>：带格宽 ≈ 本值 × D_eff/((1+W')(1+S')) ≈ 本值×
     * 2670/1.1794 ≈ <b>68 格</b>（0.03 档）——与 ampAt 11×11 核（半径 5 粗格 ≈ 44~60 格渐变）
     * 同量级 ⇒ 振幅带与身份面平滑核的外缘尺度对齐（ue-lake §3 设计一致性钉：皮肤带 56 格 vs
     * 振幅带 ≈68 格）。缩样三档 {0.02, 0.03, 0.05} ≈ {45, 68, 113} 格的定档读数见
     * {@code plan/tmp/p27-l-readings.md}。
     * <p>
     * <b>P28-L（v1.20.51 D1·滩宽×5 随动）：0.03 → 0.10</b>——滩带主体扩 5 倍（设计环 22→113 格，
     * {@link #LAKE_SHORE}）后带外过渡按同一反解式随比例放大：0.10×2670/1.1794 ≈ <b>226 格</b>
     * （与 ampAt 平滑核 44-60 格的"外缘尺度对齐"关系放宽为"包络"语义——滩主体本身已是缓坡带，
     * 过渡带只需保证 shoreAt 抖动起点外无 4.25× 硬跳，226 格平滑宽度足够）。快速臂阈（PTP
     * heightCore 三常量和）0.1285 → 0.2685 常量组合自动跟随。
     * <p>
     * <b>P32（批3 T1-4 换算统一，v1.20.55）</b>：统一线性式（见 {@link #LAKE_WATER_LEVEL} 的
     * P32 段）下 <b>本值 0.10 ⇒ 名义带宽 0.10×2670 ≈ <b>267 格</b>（0.03 旧档 → 80 格）</b>；
     * 上文本值×{@code D_eff/((1+W')(1+S'))} ≈ ×2670/1.1794 割线式与 68/226 格读数是 P23-P28
     * 圆场口径，随场换废弃（历史保留）。
     */
    public static final double LAKE_AMP_BELT_DELTA = 0.10D;

    /** 湿带（水陆之间不积水的半湿表层带）贴水判据：地表距水面的最大格数（plan §15.4）。 */
    public static final int LAKE_WET_BAND_DROP = 2;

    /**
     * 岛底柱的<b>柱环半径</b>（格）。<b>P23 R1（v1.20.46 批2 S2）6 → 13</b>：随岛干半径
     * 16 → 40 等比放大（≈2.17×就近整数档）；环 13 + 抖 2 + 截面半宽 2 = 17 &lt; 40 恒落岛内。
     * v1.20.45 及更早的 6 档校准史（"5 柱全落岛内" 75.00% 等）见版本树。
     */
    public static final double LAKE_PILLAR_RING = 13.0D;

    /**
     * 岛底柱<b>抖动幅度</b>（格）。<b>P23 R1：±1 → ±2</b>（随岛尺寸等比放大；取整后落在
     * {−2,−1,0,+1,+2}）。
     */
    public static final int LAKE_PILLAR_JITTER = 2;

    /**
     * 岛底柱<b>截面半宽</b>（格；1 ⇒ 3×3、<b>P23 R1：1 → 2 ⇒ 5×5</b> = 25 列/柱，随岛尺寸
     * 等比放大——任务包"9×3×5 或等比"的等比落地）。
     */
    public static final int LAKE_PILLAR_HALF_SECTION = 2;

    /**
     * 岛底柱<b>根数</b>。<b>P23 R1：5 → 9</b> = 1 根中心柱 + 8 根环柱（45° 均分，随岛尺寸
     * 等比放大）；y 域 [{@link #LAKE_PILLAR_FLOOR_Y}, {@link #LAKE_PILLAR_TOP_Y}] = [40,71]
     * 机制不动。
     */
    public static final int LAKE_PILLAR_COUNT = 9;

    /**
     * 岛底柱写入的<b>最低 y</b>（湖床锚）：派生式 = {@code SEA_LEVEL − LAKE_CENTER_DEPTH} = 40，
     * 与 {@link #lakeBedAt} 的湖心锚同一条式子（不另立第二真值，也不引用 Profile 的 private
     * {@code MIN_HEIGHT}）——柱脚正好坐在湖床上。
     */
    public static final int LAKE_PILLAR_FLOOR_Y = ProsperityTerrainProfile.SEA_LEVEL - (int) LAKE_CENTER_DEPTH;

    /**
     * 岛底柱写入的<b>最高 y</b>（岛底锚）：派生式 = {@code SEA_LEVEL + LAKE_ISLAND_LIFT − 1} = 71，
     * 正好顶在岛面（72）之下 ⇒ 柱与岛面逐格相接、中间不隔水。
     */
    public static final int LAKE_PILLAR_TOP_Y = ProsperityTerrainProfile.SEA_LEVEL + (int) LAKE_ISLAND_LIFT - 1;

    /**
     * 湖滨带<b>外缘</b>（RTG lakeShoreLevel 同位参数）：[WATER, SHORE) 为床→原地形渐变带。
     * P23 R1（v1.20.46 批2 S2）0.15 → 0.26：随 {@link #LAKE_WATER_LEVEL} 0.23 取 ΔP=0.03。
     * <b>换算式（javadoc 钉）</b>：带格宽 ≈ ΔP × D/((1+W)(1+S))，D_eff≈1070 ⇒ ≈0.03×1070/1.47
     * ≈ <b>22 格</b>（v1.20.45 为 ΔP=0.02×≈708 ≈14 格；放湖后带格宽按比例放大，4 级台阶踏面
     * ≈5 格，多级缓坡语义不变）。sanzu 群系滩带外边在 {@link #sanzuBiomeShoreAt}（本值之上
     * 再叠噪声 0~+17 格 ⇒ 滩带总宽 ≈22~39 格 ∈ 目标带 10~40），与本带的口径解耦见该处。
     * <p>
     * <b>P25（D1 概率减半）：0.26 → 0.091</b>——同一反解式按新档重导：ΔP = 0.091−0.081 =
     * 0.010，<b>滩带总宽 ≈ ΔP×D_eff/((1+W')(1+S')) = 0.010×2670/(1.081×1.091) ≈ 22.6 ≈
     * 22 格</b>（带格宽不动——台阶踏面语义保持；P23 的 0.03×1070 档算式见上文，历史保留）。
     * <p>
     * <b>P28-L（v1.20.51 D1·滩宽×5）：0.091 → 0.131</b>——用户①"河滩宽幅增至当前 5 倍"：
     * ΔP = 0.131−0.081 = 0.050，<b>滩带总宽 ≈ 0.050×2670/(1.081×1.091) ≈ 113.2 ≈ 113 格</b>
     * （设计滩环 22→113 ≈ ×5；噪声腿同比 ×5 见 {@link #SANZU_BIOME_SHORE_JITTER} ⇒ 滩带总宽
     * ≈113~198 格）。台阶踏面随带宽同比放大（4 级 × ≈28 格/级，"层叠滩地"读感由 L3 缩样复核
     * TREADS 档位，本批不动）。滩缘漏水根修（抖动滩缘贴水线钳制）在 PTP heightCore 末段。
     * <p>
     * <b>P32（批3 T1-4 换算统一，v1.20.55）</b>：统一线性式（见 {@link #LAKE_WATER_LEVEL} 的
     * P32 段）下 <b>ΔP = 0.050 ⇒ 滩带（[W,S) 踏面带）名义宽 0.050×2670 ≈ <b>133.5 格</b></b>
     * （探针实测均值 113.6 格 = dN 实测中位下修容差所致，temp/p32-q2/sweep2.out FIXED 腿）；
     * 上文 {@code ΔP×D/((1+W)(1+S))} 割线式与 P28-L 的 113 格读数是 P23-P28 圆场口径，随场换
     * 废弃（历史保留）。滩带物理宽 = 等压线间距 {@code ΔP×dN/|∇s|}，|∇s| 逐方向变化 ⇒ 踏面宽
     * 随岸段结构性变化（D3 方案 A，SLMC CVW 判据面）；群系平面总宽另叠加外缘两腿（见
     * {@link #SANZU_BIOME_SHORE_WIDTH_DELTA}）。
     */
    public static final double LAKE_SHORE = 0.131D;

    /**
     * 湖压力<b>加性轮廓噪声</b>波长（格，P25 D3③ 新增）：λ ≈ 220——滩带总宽（≈22 格）的
     * ~10 倍、标称水径 200 的 ~1/9 ⇒ 湖缘在"数十格一段"的尺度上缓摆，不与滩带/台阶宽打架。
     * <b>P26-B3（D1·A1）起为本档 = 双倍频的低频腿</b>（高频腿见
     * {@link #LAKE_PRESSURE_NOISE_SCALE2}）。
     */
    public static final double LAKE_PRESSURE_NOISE_SCALE = 220.0D;

    /**
     * 湖压力加性噪声幅度（压力域，P25 D3③ 新增，{@link #lakeAt0} 返回值上加
     * 本值×valueNoise ∈ [−1,1)）。<b>径向格数口径（javadoc 钉）</b>：水缘处 dr/dP =
     * D_eff/(1+S')² ≈ 2670/1.091² ≈ 2244 格/单位压力 ⇒ <b>幅 = 8 格/(D_eff/(1+S')²) ≈
     * 8/2244 ≈ 0.0035</b> ⇒ 湖缘径向微摆 ±8 格。
     * <b>P26-B3（D1·A1，v1.20.49）：0.0035 → 0.0025</b>——双倍频改造的低频腿降档（高频腿
     * {@link #LAKE_PRESSURE_NOISE_AMP2}=0.0012），总幅 0.0025+0.0012 = 0.0037 ≈ 8.3 格与
     * 改前 ±8 同量级（evolve-lake A1：λ70 特征长 ≈35 格 ≈ 滩带 1.5 倍，湖缘"细褶"与既有
     * warp 大形正交——"自然不规则"的频谱缺口）；与 warp 揉形（坐标域形变 ≤97 格）、
     * {@link #SANZU_BIOME_SHORE_JITTER}（滩外缘单边 0~17 格）三尺度正交分级：
     * 形变(warp) &gt; 走线(n01 单边) &gt; 微廓(本噪声双倍频)。
     * <p>
     * <b>P32（批3 T1-4 换算统一，v1.20.55）</b>：统一线性式（见 {@link #LAKE_WATER_LEVEL} 的
     * P32 段）下 <b>本值 0.0025 ⇒ 名义径向幅 0.0025×2670 ≈ <b>6.7 格</b></b>；上文
     * {@code dr/dP = D_eff/(1+S')² ≈ 2670/1.091² ≈ 2244 格/单位压力}（"水缘处导数式"）是
     * P23-P28 圆场口径，随场换废弃（历史保留；新场水缘 = s=0 精确，转换处处同式）。
     */
    public static final double LAKE_PRESSURE_NOISE_AMP = 0.0025D;

    /**
     * 湖压力加性轮廓噪声<b>第二倍频波长</b>（格，P26-B3 D1·A1 新增）：λ = 70——滩带总宽
     * （≈22 格）的 ~1/3、低频腿 λ220 的 ~1/3 ⇒ 湖缘细褶特征长 ≈35 格；70 % 16 = 6 ≠ 0 ✔
     * （账本 §9 chunk 对齐条纹坑的取值纪律）。
     */
    public static final double LAKE_PRESSURE_NOISE_SCALE2 = 70.0D;

    /**
     * 湖压力加性噪声<b>第二倍频幅度</b>（压力域，P26-B3 D1·A1 新增）：与低频腿同式相加，
     * 幅 0.0012 × 2244 格/单位压力 ≈ 2.7 格高频微摆；R1 降级链 = 0.0012 → 0.0008
     * （G-C 内切圆 slack 保护，见 SLMC G-C 复测读数）。
     * <p>
     * <b>P32（批3 T1-4 换算统一，v1.20.55）</b>：统一线性式（见 {@link #LAKE_WATER_LEVEL} 的
     * P32 段）下 <b>本值 0.0012 ⇒ 名义径向幅 0.0012×2670 ≈ <b>3.2 格</b></b>；上文 ×2244
     * （2.7 格）是 P23-P28 圆场口径，随场换废弃（历史保留）。
     */
    public static final double LAKE_PRESSURE_NOISE_AMP2 = 0.0012D;

    /**
     * 湖压力加性轮廓噪声盐（P25 D3③ 新增）：盐段尾续取 {@code …117L}（…115/…116 已被
     * {@link #SALT_DRY_RIVER}/{@link #SALT_DISK_LAKE_SUB} 占用；…112L 随残潭退役空闲、
     * 不回收不复用）。
     */
    public static final long SALT_LAKE_PRESSURE = 0x5249F117L;

    /**
     * 湖压力<b>第二倍频</b>噪声盐（P26-B3 D1·A1 新增）：盐段尾续取 {@code …11BL}（…11A
     * 已被 {@link #SALT_LAKE_ISLAND_EDGE} 占用）。独立盐域纪律同
     * {@link #SALT_DISK_LAKE_WARP}/{@link #SALT_DISK_LAKE_SUB}（两张盘各自独立盐）——同盐
     * 不同 λ 的两档是"同一底层格点场重采样"（格点重合处取值恒同 ⇒ 两档强相关），非独立
     * 倍频 ⇒ 必须分盐。
     */
    public static final long SALT_LAKE_PRESSURE2 = 0x5249F11BL;

    // ═══ P32（v1.20.55 批2 S1，T1-3）：非圆 blob 湖形场——站形状参数域（E-A 终案）═══
    //
    // 场结构（{@link #lakeAt0} 重构，湖缘缎带腿整体移除——P28-L 引入、P29-A1 重标定的六常量
    // 随场换废弃，历史 javadoc 见版本树）：P = clamp(W + s/dN, 0, 1) + 双倍频微噪（λ220/λ70
    // 原样）。s = 到 argmin 站<b>形状边界</b>的带符号距（内负外正，warp 空间锚点相对系）；
    // dN = 次近站距（3×3 Worley 原样）。每站一套形状参数（本段常量域 + {@link #SALT_LAKE_SHAPE}
    // 抽签，512 槽直映缓存——{@link #stationParams}）：
    //   class0（0.45）单超椭圆 n∈{2,3}——"方形"由 n=3 指数承担（同半轴 45° 向径 1.122·r0，
    //   内切不损失，不靠深伸缩）；
    //   class1（0.38）主 blob + 单副 blob（梨 / L-前形态）；class2（0.17）主 blob + 双副
    //   blob（张角 ≥60°，L / 花生签名形状）。（P32 批2 S1b 档2：0.50/0.35/0.15→0.45/0.38/0.17，
    //   SF/CV 形状读数驱动——读数档 plan/tmp/p32-t1p3b-iter.md）
    // 两层制：主形扛内切保证（{@link #LAKE_SHAPE_B_FLOOR}）与岛容器；副 blob 只做剪影多样性
    // （远置 offset_s ≤ 0.82r0 出真 L 臂——档1 放宽，读数档见 plan/tmp/p32-t1p3b-iter.md）。
    // D_MIN 淘汰先于形状求值（哨兵不触缓存不加噪）。
    // 依据：plan/p32-plan.md S1 + plan/tmp/p32-ea-blob-final.md（E-A 终案 §3/§4/§9 表）+
    // P5 原型 temp/p32-t1p2/p32-blob-defs.jshell（20/20 逻辑 PASS，直接移植）。

    /** 站形状参数的尺度基准：标称水半径 r0（格，E-A §9 表；与 {@link #LAKE_WATER_LEVEL} 的 200 格标称口径同源）。 */
    public static final double LAKE_SHAPE_R0 = 200.0D;

    /**
     * 主形<b>内切地板</b> b_floor（格，E-A §4 两层制）：class0 保面积 b = r0²/a ≥ 本值
     * （a ≤ 1.08·r0 ⇒ b ≥ 185.19）；class1/2 主 blob ρ_p ∈ [1.00,1.15]·r0、心偏移 ≤ 0.05·r0
     * ⇒ 锚点内切 ≥ ρ_p − 10 ≥ 190 ≥ 本值。钉死依据（E-A §2 表三账）：岛绝对腿账 b_floor ≥
     * 139 + ΣA + 微噪 8.3 ≈ 147 + ΣA = 182（ΣA=35 档）⇒ 取 185 留 3 格余量；R6 旧域
     * a ≤ 1.7r0/b ≥ 118 被三账全线否决（b=118 ⇒ G-C 74.7 / 岛域 10.7 / G-I 干 ≈6）。
     */
    public static final double LAKE_SHAPE_B_FLOOR = 185.0D;

    /**
     * 副 blob 融合半径 k_smin（格）：多项式 smin（h = clamp(0.5+0.5(a−b)/k)，smin =
     * a + h·(b−a) − k·h·(1−h)，ATG MathUtil.polymax 对偶；C¹ 切向连续、缝处曲率有界跳变）
     * ——真并集外的伪内部下探 ≤ k/4 = 10 格（凹腰圆润，设计内）。40 ≈ b_floor 的 22%、
     * 滩带宽 113（{@link #LAKE_SHORE}）的 ~35% ⇒ 融合痕与滩带不打架。
     */
    public static final double LAKE_SHAPE_K_SMIN = 40.0D;

    /** 站形状类占比：class0（单超椭圆）概率 = 本值（抽签 u &lt; 本值 ⇒ class0；档2：0.50→0.45）。 */
    public static final double LAKE_SHAPE_P_CLASS1 = 0.45D;

    /** 站形状类占比累计：u &lt; 本值 ⇒ class1；否则 class2（档2：0.85→0.83 ⇒ class1/2 = 0.38/0.17）。 */
    public static final double LAKE_SHAPE_P_CLASS2 = 0.83D;

    /** class0 内部超椭圆指数占比：n=3（方形感）：n=2（椭圆）= 本值 : 1−本值 = 0.75 : 0.25（档2：0.70→0.75）。 */
    public static final double LAKE_SHAPE_P_N3 = 0.75D;

    /** class0 n=2 强制伸缩下限（×{@link #LAKE_SHAPE_R0}——弱档仍有非圆感，b ≥ 192）。 */
    public static final double LAKE_SHAPE_A_N2_MIN = 1.04D;

    /** class0 伸缩上限（×r0；n=2/n=3 共用——保面积 b = r0²/a ≥ b_floor = 185.19）。 */
    public static final double LAKE_SHAPE_A_N2_MAX = 1.08D;

    /** class0 n=3 伸缩下限（×r0——方形由指数承担，可取 1.00）。 */
    public static final double LAKE_SHAPE_A_N3_MIN = 1.00D;

    /** 见 {@link #LAKE_SHAPE_A_N2_MAX}（n=3 上限同值，单常量复用）。 */
    public static final double LAKE_SHAPE_A_N3_MAX = LAKE_SHAPE_A_N2_MAX;

    /** class1/2 主 blob 半径域下限（×r0）。 */
    public static final double LAKE_SHAPE_RHO_P_MIN = 1.00D;

    /** class1/2 主 blob 半径域上限（×r0 ⇒ 锚点内切 ≥ 190 ≥ b_floor）。 */
    public static final double LAKE_SHAPE_RHO_P_MAX = 1.15D;

    /** class1/2 主 blob 心偏移帽（×r0，沿朝向 ≤ 10 格——主形仍以锚点为容器）。 */
    public static final double LAKE_SHAPE_CENTER_OFF_MAX = 0.05D;

    /** 副 blob 半径域下限（×r0）。 */
    public static final double LAKE_SHAPE_RHO_S_MIN = 0.45D;

    /** class1 副 blob 半径域上限（×r0；P32 批2 S1b 档1：0.80→0.86，SF/CV 形状读数驱动）。 */
    public static final double LAKE_SHAPE_RHO_S_MAX = 0.86D;

    /** class2 副 blob 半径压帽（×r0，控并集面积；档1 同步 0.70→0.76）。 */
    public static final double LAKE_SHAPE_RHO_S_MAX_C2 = 0.76D;

    /**
     * 副 blob 心偏移域（×r0，自主 blob 心）与<b>重叠约束</b>：offset_s ≤ ρ_p + ρ_s −
     * 0.3·{@link #LAKE_SHAPE_K_SMIN}（连通并集；域内恒松、钳制保形——ρ_p+ρ_s−12 ≥ 278 ≫
     * 0.82·r0 = 164（档1 放宽 0.75→0.82 后复核仍松），P5 L4g 实测 violations=0）。
     * <p>
     * <b>腰宽构造账（账本 C"最小臂宽 ≥ 40 构造性免重校"，批3 专项探针验证；档1 ρ_s 上调后
     * 复核）</b>：两圆割线腰宽 2h = 2√(ρ_p² − x²)、x = (d² + ρ_p² − ρ_s²)/2d（d = 两圆心距，
     * 按重叠约束上界 d = ρ_p + ρ_s − 0.3k 保守取值——域帽 {@link #LAKE_SHAPE_OFF_S_MAX}·r0
     * 恒更小，真实腰更宽）。最坏角（ρ_p = 200、ρ_s = 90、d = 278）⇒ x = 196.4、<b>2h ≈ 76
     * ≥ 40</b>；ρ_s 上调对腰宽单调有利（ρ_s = 0.86r0 = 172 ⇒ 2h ≈ 93）⇒ 最坏角仍在 ρ_s 下限
     * 处不变 ⇒ L 形内凹臂在侵蚀门（核心 5×5 粗格 = 20 格窗）下构造性存活。
     */
    public static final double LAKE_SHAPE_OFF_S_MIN = 0.35D;

    /** 见 {@link #LAKE_SHAPE_OFF_S_MIN}（上限 0.82·r0 = 164（档1：0.75→0.82）⇒ 域界伸展见 {@link #LAKE_SHAPE_R_MAX}）。 */
    public static final double LAKE_SHAPE_OFF_S_MAX = 0.82D;

    /** class2 双副 blob 张角下限（弧度 = 60°；抽签域 [本值, π]）。 */
    public static final double LAKE_SHAPE_C2_SEP_MIN = Math.PI / 3.0D;

    /**
     * 伸展帽 r_max（格，域界申报而非运行时钳制；P32 批2 S1b 档1 随 offset_s 0.75→0.82 /
     * ρ_s 0.80→0.86 上限放宽重算 310→336）：offset_s + ρ_s ≤ 0.82 + 0.86 = 1.68·r0 = 336，
     * 加主 blob 心偏移 ≤ 10 与 smin 外鼓 ≤ k/4 = 10 ⇒ 解析最坏 ≤ <b>356 ≤ 450 = 0.40·
     * {@link #LAKE_STATION_D_MIN}</b>（不变式断言值，对 D_MIN 改档鲁棒，余量 94；可见 flip
     * 紧界 0.406×2·D_MIN = 914——E-A §5/§8）。P5 旧域（0.75+0.80）实测 maxReach = 309.75
     * （plan/tmp/p32-t1p2-bench.md L4h；档1 新域 L 复测读数见 plan/tmp/p32-t1p3b-iter.md）。
     * 前向 argmin 锚点制下 flip 跳变发生在全消费阈之外的饱和区（flip 处 P ≥ 0.081 + 1 −
     * 2·336/2250 = 0.782 ≫ 0.2685 PTP 快速臂上界）⇒ 不可见；SLMC FINE_WINDOW_MAX = 560
     * 覆盖 336 + 滩 133 + 檐 40 = 509 ≤ 560 仍闭（RAYCAP32 = 0 复核）。
     */
    public static final double LAKE_SHAPE_R_MAX = 336.0D;

    /**
     * <b>⟂-邻站定向</b>触发距（格，E-A §8）：站参数填充期（缓存内一次成本）抽 8 邻站
     * cellOffset（既有站格盐 {@link #SALT_LAKE_CELL_X}/{@link #SALT_LAKE_CELL_Z}，零新真值），
     * 最近邻 D &lt; 本值时触发（站距分布 ⇒ ~5-10% 站）：class0 伸展长轴 ⟂ 邻站方向
     * （θ = 邻站角 + 90° ± {@link #LAKE_SHAPE_PERP_JITTER}）、class1/2 副 blob 禁置邻站
     * 方向 ±{@link #LAKE_SHAPE_FORBID_HALF} 扇区——向邻站伸展降至 b ∈ [185,200] ⇒ 残留
     * 近对水切 ≤ 25 格（无规则 155 格）。确定性纯函数保持（邻站偏移同 seed 同值；站参数
     * 不再站独立，但同 seed 同站恒同值）。
     */
    public static final double LAKE_SHAPE_PERP_D = 1600.0D;

    /** ⟂-邻站定向的 class0 长轴抖幅（弧度 = ±15°）。 */
    public static final double LAKE_SHAPE_PERP_JITTER = Math.PI / 12.0D;

    /** ⟂-邻站定向的副 blob 禁置扇区半宽（弧度 = ±45°）。 */
    public static final double LAKE_SHAPE_FORBID_HALF = Math.PI / 4.0D;

    /**
     * 站形状参数抽签盐（P32 T1-3 新增）：盐段尾续取 {@code …11FL}（…11E 归
     * {@link #SALT_LAKE_ISLAND_EDGE_MID}；…11D 随缎带退役空闲、不回收不复用；…120 归
     * {@link #SALT_SANZU_BIOME_SHORE_WIDTH}（P32 批3），…121 段尾空闲）。
     */
    public static final long SALT_LAKE_SHAPE = 0x5249F11FL;

    /**
     * 湖缘 ridged 缎带盐 {@code 0x5249F11DL}。<b>已删盐（P32 T1-3，v1.20.55）</b>：随缎带腿
     * 整体移除（P28-L D1·R1 引入、P29-A1 重标定，六常量——尺度/幅度/门阈/门宽/缘带半宽/盐——
     * 与缘带短路/ribbonGate 支路的历史 javadoc 见版本树）退役——<b>不回收、不复用</b>
     * （防跨版本 digest 混淆；先例 SALT_WET 残潭盐）。
     */

    /**
     * {@link #lakeAt} 的"无湖"哨兵（无湖/D_MIN 淘汰一律返回它）：≥ {@link #LAKE_SHORE}，与压力同向
     * （低=湖心），消费面（heightCore 压低 / 水面回填）统一判 {@code lake < LAKE_SHORE}。
     * <b>P23 R1</b>：trunk 带外哨兵语义随全域站格化作废——现仅 D_MIN 淘汰与普通远场返回。
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
         * 水面恒 = poolLevel，本档决定常态水深（草原/森林 2.0 / 荒漠 2.0 / <b>沼泽 2.0
         * （v1.20.49 P26-B2 随枯竭下调：原 1.0 是湿河时代"水深 0-1 沼地肌理"口径，全域枯竭后
         * 语义退役，与沼泽 bedTarget 66.5→64.5 一同下调）</b> / sanzu 2.5）。取值 &lt;
         * {@link #bedNoiseAmp} 的档才会周期性干出浅滩（<b>P26-B2 起 amp ≤ depth 全表成立：
         * 草原/森林 amp 2.5→2.0 后「amp=2.5 &gt; depth=2.0 ⇒ 周期性干出浅滩」的设计性干出退役
         * ——枯竭语境下 n_bed 抬到 ≥ 池水位的干出读作"砂砾偏高"，观感修复</b>）。
         */
        public final double depth;
        /**
         * 浅滩档标识（草原/森林 true）。<b>v1.20.40 浅滩重释</b>：浅滩不再走独立 shoalNoise
         * 闸（已废），而是"段内 bedAt ≥ 池水位"的派生条件；本布尔仅作档表标识保留
         * （RiverMorphologyCheck A 组钉面），不参与生成判定。
         */
        public final boolean shoals;
        /**
         * 断流档（荒漠 true：置水走 segKey 整段闸）。<b>v1.20.42（P22 A1a）从生产链退役</b>：
         * {@link #wetAt} 收紧为 {@code s ≥ WET_MIN ∧ trunkAt > 0}（全域枯竭＋sanzu 豁免）后，
         * 本布尔不再参与任何生成判定（{@link #bedFromPool} 的 wetGated 腿已删）。字段保留只因
         * 档表构造签名与判据侧口径不改（先例见类顶部已删字段登记）。
         */
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
     * （depth 2.0、<b>amp 2.0（v1.20.49 P26-B2 从 2.5 收平：amp ≤ depth 修正，"周期浅滩/河滩"
     * 的设计性干出退役——滩带出露只剩 s ∈ [WET_MIN, WET_MIN+SHORE_FLAT_BAND] 的水缘滩，归因
     * 见 depth 字段注）</b>）；2 荒漠 = 干谷断流（<b>P22 A1a 起整段闸退役：
     * 与全域一致按 {@link #wetAt} 新门枯竭</b>，本档仅余纯参数差异）；3 沼泽 = 沼地河
     * （×1.2、<b>bedTarget 64.5 / depth 2.0（v1.20.49 P26-B2 随枯竭下调：原 66.5/1.0 是
     * "湿河近地＝水面近地"口径——旧 A7 档表断言沼泽床 ∈ [66.5,67.5]，全域枯竭后未随动 ⇒
     * 沼泽干床砾最高 68.5 ≈ SEA_LEVEL=68、与沼泽地面（均值 ~70）近齐平＝"部分河床砂砾偏高"
     * 第一主因；下调后沼泽 pool 档 {65,67,69}→{63,65,67}，床域 [pool−2.5, pool−1.5] ⊆
     * [60.5,65.5] 全低于 SEA ≥2.5 格）</b>）；4 sanzu = <b>主干宽河占位档</b>
     * （v1.20.39 T5 按档表族 4→5 约定就位）：宽乘子 = {@link #TRUNK_WIDTH_SCALE}——主干带内
     * {@link #strengthAt} 对任何底档统一取 max(底档, 3.5) ⇒ 本档被带入时与主干带分支同值
     * （<b>无双乘</b>），depth 2.5（主干河更深）。生产链路 rosterIndex=4 不会从 GenLayer 链
     * 出现（selector 名册仍 4 家，plan §3.3/§5），本元供档表族长度一致与判据/后续消费面。
     */
    public static final RiverStyle[] RIVER_STYLE_BY_ROSTER = {
        /* 0 锈蚀草原（P26-B2：amp 2.5→2.0，amp≤depth 修正） */
        new RiverStyle(1.0D, BED_TARGET, 2.0D, 2.0D, true, false),
        /* 1 齿轮森林（P26-B2：amp 2.5→2.0，amp≤depth 修正） */
        new RiverStyle(1.0D, BED_TARGET, 2.0D, 2.0D, true, false),
        /* 2 黄铜荒漠 */ new RiverStyle(1.0D, BED_TARGET, 1.5D, 2.0D, false, true),
        /* 3 喷气沼泽（P26-B2 随枯竭下调：66.5/1.0 → 64.5/2.0） */
        new RiverStyle(1.2D, BED_TARGET, 0.5D, 2.0D, false, false),
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
     * ═══ P23 R1①（v1.20.46 批2 S2）：主干宽河彻底移除 ═══ 原 T5 的 trunk&gt;0 三重门支路
     * （宽河 max(底档,3.5) / 对齐门 / 内带门）<b>整段删除</b>（不保留干峡谷骨架）——带内带外
     * 同一常态档公式，主干带噪声（{@link #trunkAt}）不再是本方法的输入。带外支路原样保留，
     * 并在其上加<b>段激活门</b>（R1② 零散干床）：每条 Voronoi 边段（同 segKey）按确定性哈希
     * 以 {@link #RIVER_SEGMENT_ACTIVATE_P}=0.20 独立激活，未激活段整段清零。门在性能短路
     * 之后 ⇒ 远离边界的腹地列零成本。
     * <p>
     * ═══ P25（D4①）湖让位腿 ═══ 性能短路之后、段激活门之前插
     * {@code lakeAt < sanzuBiomeShoreAt ⇒ return 0.0}——湖+滩（含噪声腿扩出的滩带外缘）内
     * 河流强度恒 0。注意本方法真值域 [-1,0)（0=无河）⇒ <b>湖+滩内 s ≡ 0 ⇒ 干床/谷/床料/滩
     * 全灭</b>（heightCore 的 {@code s > 0} 支路整体短路），{@link #isDryRiverColumn}（D6，
     * −strengthAt ≥ 0.40）在湖/滩内<b>天然不触发</b>。位置在性能短路之后：仅河核列
     * （c &lt; WIDTH×style，全列 ≪1%）付一次 lakeAt memo 查表 + 一次低频 valueNoise。
     * <p>
     * ═══ P30 I1（v1.20.53）：枯竭河末端定向衰减 ═══ 段激活门只按段清零，活段端头在死邻段
     * 界处"刀切山体"（谷形整深到界、界外骤回原地形）。3b 门之后对返回式乘两个<b>定向</b>因子
     * （{@link #TAPER_DEAD}/{@link #LAKE_FADE} 注有完整数学式与校准域）：
     * <ul>
     * <li><b>B1 死邻衰减</b>：本列所在边 (A,B) 过门后，对结点另两条边 (A,C)/(B,C) 各判一次
     * 死活（hash01(segKeyOfCells(细胞对) ^ {@link #SALT_RIVER_SEGMENT_GATE}) ≥
     * {@link #RIVER_SEGMENT_ACTIVATE_P}——与 3b 门同式同盐同阈，仅细胞对不同）；
     * r₁=(d3−dC)/d3、r₂=(d3−dN)/d3，t₁/t₂ = 死邻 ? s01(r/{@link #TAPER_DEAD}) : 1.0。
     * <b>衰减只朝死邻方向——r1/r2 各管一条死边</b>（r₁ 管 (A,C) 死、r₂ 管 (B,C) 死），
     * 活邻不贡献因子（对应 t 恒 1.0）。<b>活-活守卫</b>：!deadAC &amp;&amp; !deadBC 时直接走
     * 原返回式、不触任何 r/t 计算 ⇒ 活-活结点 IEEE 逐位零漂。</li>
     * <li><b>B2 湖腿平滑</b>：3a 硬腿原样保留（湖+滩内 s ≡ 0 语义不动），过腿列乘
     * t_lake = s01((lakeAt − sanzuBiomeShoreAt)/{@link #LAKE_FADE})——湖缘由刀切变渐灭
     * （湖带外 t_lake 精确 1.0）。</li>
     * </ul>
     * 两因子都在本方法（<b>单一真值</b>）内合成 ⇒ 所有消费面自动跟随，无第二份判定：
     * heightCore 切谷（{@code ProsperityTerrainProfile.heightCore} 的 s 位）、
     * {@link #isDryRiverColumn}（D6 干河床核）、PlacementGate⑥枯竭河床列
     * （{@code dryColumnAt} 腿⑥，经 isDryRiverColumn）、微池让位（S3 湖腿的干床让位）、
     * 枯竭气息（{@code ProsperityAirLookup} 的 witheredRiverbed → WitheredBreath）。
     */
    public static double strengthAt(long worldSeed, int x, int z, int rosterIndex) {
        final double styleScale = styleForRosterIndex(rosterIndex).widthScale;
        // —— 1. 两级 Disk jitter 蜿蜒 + 2. Voronoi border2（P19 U8：同列的 warp+evalBorder 复合
        // 走 warpedBorder 列级 memo——strengthAt/poolLevelAt/segKeyAt/endFaceBed 同列互访
        // 不再各重算一遍；算式与逐位结果同前）——
        final BorderEval border = warpedBorder(worldSeed, x, z);
        final double c = border.c;
        // —— 3. 性能短路：河/谷域（c ≥ 本档宽）之外的列零后续成本 ——
        if (c >= WIDTH * styleScale) {
            return 0.0D;
        }
        // —— 3a. P25 D4① 湖让位硬腿：湖+滩内无河（0=无河；见方法 javadoc 的 P25 段）。
        // P30 I1：湖/滩两压力值提为局部量供 3d 湖腿平滑复用（同两次调用、同序，零新求值）——
        final double lake = lakeAt(worldSeed, x, z);
        final double shoreAt = sanzuBiomeShoreAt(worldSeed, x, z);
        if (lake < shoreAt) {
            return 0.0D;
        }
        // —— 3b. 段激活门（P23 R1②）：同段（同 segKey）所有列同值 ⇒ 激活段整段连贯、
        // 未激活段整段清零，跨 chunk 一致；segKeyAt 已列级 memo。——
        if (hash01(segKeyAt(worldSeed, x, z) ^ SALT_RIVER_SEGMENT_GATE) >= RIVER_SEGMENT_ACTIVATE_P) {
            return 0.0D;
        }
        // —— 3c. P30 I1 死邻判定的局部快照先行（BorderEval 类纪律：EVAL_BUF 不得跨下一次
        // evalBorder 持有——此刻它仍是本列值：3b 的 segKeyAt 同列走 SEGKEY_MEMO，未命中时
        // warpedBorder 同列命中 BORDER_MEMO、按槽快照原值回填），任何 hash/segKey 调用前
        // 取全九量。两结点邻边 (A,C)/(B,C) 的死活判定与 3b 门严格同式同盐同阈，只是
        // segKey 的细胞对换成结点另两条边——
        final int aX = border.aX;
        final int aZ = border.aZ;
        final int bX = border.bX;
        final int bZ = border.bZ;
        final int cX = border.cX;
        final int cZ = border.cZ;
        final double dC = border.dC;
        final double dN = border.dN;
        final double d3 = border.d3;
        final boolean deadAC = hash01(segKeyOfCells(worldSeed, aX, aZ, cX, cZ) ^ SALT_RIVER_SEGMENT_GATE)
            >= RIVER_SEGMENT_ACTIVATE_P;
        final boolean deadBC = hash01(segKeyOfCells(worldSeed, bX, bZ, cX, cZ) ^ SALT_RIVER_SEGMENT_GATE)
            >= RIVER_SEGMENT_ACTIVATE_P;
        // —— 3d. P30 I1 末端定向衰减合成（方法 javadoc 的 P30 I1 段）：B2 湖腿平滑 t_lake
        // （过了 3a 硬腿的列；湖带外 s01 带通精确 1.0）+ B1 死邻 taper t1×t2——
        final double rs = c / (WIDTH * styleScale) - 1.0D;
        final double tLake = s01((lake - shoreAt) / LAKE_FADE);
        if (!deadAC && !deadBC) {
            // 活-活守卫（P30 I1）：两结点邻边皆活 ⇒ 不触任何 r/t 计算，直接走原返回式；
            // 湖带外 t_lake ≡ 1.0 ⇒ 活-活结点 IEEE 逐位零漂。
            return rs * tLake;
        }
        // B1：r1=(d3−dC)/d3（邻边 (A,C) 的 border2 c 值）、r2=(d3−dN)/d3（邻边 (B,C)）——
        // 衰减只朝死邻方向，r1/r2 各管一条死边，活邻不贡献因子（对应 t 恒 1.0）。
        final double t1 = deadAC ? s01(((d3 - dC) / d3) / TAPER_DEAD) : 1.0D;
        final double t2 = deadBC ? s01(((d3 - dN) / d3) / TAPER_DEAD) : 1.0D;
        // —— 4. 强度（P23 R1①：trunk>0 宽河三重门支路已删，主干带内外同一常态档式；
        // P30 I1：末端定向衰减 ×t1×t2×t_lake）——
        return rs * t1 * t2 * tLake;
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
     * {@link #endFaceBed}）——修"段端交接处的垂直水墙"（P22 A1a 起面 A 以 {@link #wetAt}
     * 新湿门为真值：枯竭边界同治）。修正对
     * {@link #heightAt} 消费面自动生效（heightCore 内段切床走本方法，同一真值），
     * 置水面（placer 的 {@code h < pool−1} 回填门）随床抬升自动收窄。
     */
    public static double bedFromPool(long worldSeed, int x, int z, int rosterIndex, int poolLevel) {
        final RiverStyle style = styleForRosterIndex(rosterIndex);
        double bed = poolLevel - style.depth
            + style.bedNoiseAmp
                * GTSRWorldgenHash.valueNoise(worldSeed ^ SALT_BED, x / BED_NOISE_SCALE, z / BED_NOISE_SCALE);
        // 端面收尾入口门（P22 A1a）：只对"可能有端面"的湿核列求值——v1.20.42 起置水 ⇔
        // trunk 带内核列（wetAt 新门），带外河核列必枯竭（无水墙可收），荒漠 wetGated 腿退役。
        // P23 R1（批2 S2）：wetAt 恒 false 后"水墙"不复存在，本门保留的是床形端面收尾的保守观感
        // （主干带内激活段的床缘渐变），不再承载防水墙语义——保留不删（S6 收口登记）。
        if (trunkAt(worldSeed, x, z) > 0.0D) {
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
     * <li><b>面 A·枯竭端面</b>（<b>P22 A1a 重接</b>，以 {@link #wetAt} 新门为真值）：本列湿
     * （过 s ≥ WET_MIN 早退后 ⇔ trunk 带内核列）∧ 邻列换段（segKey 不同）且枯竭（邻列
     * {@code trunkAt ≤ 0} 或 {@code s < WET_MIN}，即新湿门为假）——全域枯竭后湿段在段界
     * 交给干河床的位置即水墙位置，靠本列一侧 {@code dist} 列进入渐变域（旧"荒漠整段闸 +
     * 整段闸"腿退役，见类顶已删字段登记）；</li>
     * <li><b>面 B·湖滨交接面</b>（主干带内，plan §B"湖滨带部分湿列同理"）：邻列是湖水区
     * （{@code lakeAt < LAKE_WATER_LEVEL}，其水面向 = SEA_LEVEL）——河口交接处同样收缓坡；</li>
     * </ul>
     * ═══ 抬升式 ═══ {@code w = smoothstep((LEN−dist+1)/LEN)}，床 = bed + (target−bed)×w，
     * target = {@code poolLevel − END_FACE_BED_OFFSET}——末端列（dist=1）抬满到水面下
     * 0.5 格（回填门 {@code h < pool−1} 自动关水 ⇒ 端面收成一格干砾滩），只抬不降
     * （target ≤ bed 时不动）。smoothstep 防坡折，每列高差 ≤ (target−bed)/2 ≪ 2。
     * <b>不变量（P22 A1a）：每个湿段端面床抬升收尾——枯竭边界无竖直水墙。</b>
     */
    private static double endFaceBed(long worldSeed, int x, int z, int rosterIndex, int poolLevel, double bed) {
        final double s = -strengthAt(worldSeed, x, z, rosterIndex);
        if (s < WET_MIN) {
            return bed; // 只对湿核列收尾（干列/谷坡列的床抬了也没有水墙可灭）
        }
        // P22 A1a：s ≥ WET_MIN 且要求置水 ⇒ trunk 带内核列（wetAt 新门）；带外河核列必枯竭
        // （干河床），床不收尾。荒漠 gated 腿（整段闸）随新门一并退役。
        if (trunkAt(worldSeed, x, z) <= 0.0D) {
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
                // 面 A·枯竭端面（P22 A1a）：邻列换段才判（段内枯竭带整段同观感，不重判）；
                // "邻段干" = 邻列新湿门为假（trunk ≤ 0 或 s < WET_MIN；整段闸已删，见类顶登记）。
                if (segKeyAt(worldSeed, nx, nz) != ownKey
                    && (trunkAt(worldSeed, nx, nz) <= 0.0D || -strengthAt(worldSeed, nx, nz, rosterIndex) < WET_MIN)) {
                    dist = k;
                }
                if (dist == 0 && lakeAt(worldSeed, nx, nz) < LAKE_WATER_LEVEL) {
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
     * (x,z) 列是否<b>置水资格</b>（纯函数；populate 与判据共用，无第二真值）。
     * <p>
     * ═══ P23 R1（v1.20.46 批2 S2）：主干河置水死 ═══ 方法体恒 {@code return false}——主干宽河
     * 移除后河流场全域无水（干河床），湖/沼泽池走各自独立通道（provider {@code fillSanzuLakes}
     * 的 {@link #lakeAt} 湖面、{@code fillSwampPools} 的三档/微池、落块器的沼泽残潭支路），
     * 不经本门。签名保留：GTSRRiverPlacer/PlacementGate/SwampFieldGrid 既有调用面与判据口径
     * 不改（调用点成为死路径，S6 收口登记）。历史：v1.20.42 P22 A1a 曾收紧为
     * {@code s ≥ WET_MIN && trunkAt > 0}（全域枯竭＋sanzu 豁免），P23 R1 把带内豁免一并拆除。
     * rosterIndex 形参不参与判定（保留签名）。
     */
    public static boolean wetAt(long worldSeed, int x, int z, int rosterIndex) {
        return false;
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
     * 差 ≥ 旧落差墙阈（3 格，字段已删见类顶登记）的段界随落差墙检测支路删除而不再回填。
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
     * 邻段交界只是 1-2 格小跌水（&lt; 旧落差墙阈 3，不触发墙口径），结点域边界处的
     * 水位台阶 ≤ 同一量级。
     * <p>
     * rosterIndex 只影响采样档（{@code bedTarget} 档），同段内跨粗格群系边界的池差被档差
     * 限制（<b>P26-B2 起</b>常态 64.5 vs 沼泽 64.5 同锚 ⇒ 档差 0，跨界零池差；P26-B2 前沼泽
     * 66.5 档差 ~2 格也在旧落差墙阈 3 之下，不触发墙——历史口径留档）。
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
     * 荒漠<b>整段闸</b> segWetAt。<b>已删方法（P23 R1·S6，v1.20.46）</b>：v1.20.42 随
     * {@link #wetAt} 新门从生产链退役，判据侧（RMC）引用随 S6 重钉摘除后兑现删除
     * （{@code hash(segKey ^ SALT_WET) < 0.35} 的历史算式见版本树；{@link #SALT_WET} 盐保留）。
     */

    /**
     * splitmix64 → [0,1)（高 53 位归一；原 segWetAt 同款口径抽出，P23 R1②
     * {@link #strengthAt} 段激活门派生用）：确定性纯函数，无第二份真值。
     */
    private static double hash01(long key) {
        return (GTSRWorldgenHash.splitmix64(key) >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR;
    }

    /**
     * smoothstep 带通（clamp 后 3t²−2t³；带外精确 0.0/1.0）。P30 I1（v1.20.53）随枯竭末端
     * 定向衰减引入（{@link #strengthAt} 的 t1/t2/t_lake 三处共享）；口径与
     * {@code TerrainVariants} 的同名私有帮手一致——<b>带外精确常值</b>是活-活守卫与湖带外列
     * IEEE 逐位零漂的构造前提（×1.0 逐位恒等）。
     */
    private static double s01(double t) {
        final double c = Math.max(0.0D, Math.min(1.0D, t));
        return c * c * (3.0D - 2.0D * c);
    }

    // ═════════════════ T5（v1.20.39，plan §3.3）：主干带 / 巨湖 / sanzu 列谓词 ═════════════════

    /**
     * 主干带标记 trunk(x,z) = max(0, trunkNoise − {@link #SANZU_TRUNK_EDGE})（plan §3.3 已定机制）。
     * trunkNoise = 内嵌 {@link OpenSimplexDisk}（第 3 张表，盐 {@link #SALT_TRUNK}）在
     * {@link #TRUNK_SCALE} 尺度上的 x 分量，波长约 5000 格 ⇒ 带状高值区沿噪声脊线蜿蜒成
     * "主干带"。<b>纯函数</b>。
     * <p>
     * <b>P23 R1①（v1.20.46 批2 S2）降格</b>：主干宽河移除后，本场的生产消费者只剩地形/床收尾/
     * 残潭两类"带语义"残留——{@code ProsperityTerrainProfile.heightCore} 的残潭预筛（trunk≤0 腿，
     * 谷深 ×1.3 已随 P23 R1 退役）、{@link #bedFromPool}/{@link #endFaceBed} 的端面入口门、
     * {@code swampRiverPoolAt} 的带内零潭腿；湖已解耦（{@link #lakeAt0} 去 trunk 门）、
     * {@link #strengthAt}/{@link #wetAt}/{@link #isSanzuColumn} 不再消费它。
     * <b>P25：残潭场整体退役</b>——上列消费里 heightCore 残潭预筛与残潭零潭腿已随残潭删除
     * 消失 ⇒ 生产消费者只剩 {@link #bedFromPool}/{@link #endFaceBed} 的端面入口/枯竭端面判定
     * （主干带语义的最后两处）。
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

    // P23 R1①（v1.20.46 批2 S2）：主干对齐门 trunkAligned（边界法向 × trunkNoise 梯度的 |cos|
    // ≥ TRUNK_ALIGN_COS）已随 strengthAt 的 trunk>0 三重门支路删除——本类零消费者即删，
    // 常量 TRUNK_ALIGN_COS 已删（见类顶登记）；TRUNK_GRAD_STEP 保留（历史梯度步距，零消费）。

    /**
     * 巨湖压力场（T5，plan §3.3；v1.20.40 P19 §D 破圆；P23 R1①（v1.20.46 批2 S2）全域站格
     * 独立激活；<b>P32 T1-3（v1.20.55）非圆 blob 形状场重构</b>）：第二 Voronoi
     * （{@link #LAKE_INTERVAL} 格、{@link #SALT_LAKE_CELL_X}/{@link #SALT_LAKE_CELL_Z} 盐域分离、
     * 偏移幅度同 {@link #CELL_JITTER}×间隔）按站组织——<b>每细胞腹地一座离散巨湖</b>，形状 =
     * 逐站抽签的超椭圆/主副 blob 两层制（见 {@link #SALT_LAKE_SHAPE} 段）——压力
     * P = clamp(W + s/dN, 0, 1) + 双倍频微噪 ∈ [0,1)（<b>低值在湖形内</b>）。
     * 求值前对坐标加一次 domain-warp（{@link #LAKE_WARP_SCALE}/{@link #LAKE_WARP} +
     * 副倍频盘，第 4/5 张 disk 表）——湖缘去参数边界的"数学感"（大形揉动改由形状参数承担，
     * warp 降幅账见 {@link #LAKE_WARP}）。湖缘 ridged 缎带腿（P28-L/P29-A1）已随本重构整体
     * 移除。{@link #LAKE_STATION_D_MIN} 小湖淘汰腿防双站过近破形（P23 R1 起不变）。
     * <p>
     * <b>P23 R1：与主干带解耦</b>——trunk 门删除（全域每列都付一次湖 Voronoi 成本）。
     */
    public static double lakeAt(long worldSeed, int x, int z) {
        final int idx = slotIndex(worldSeed, x, z, MEMO_MASK);
        final LakeSlot s = LAKE_MEMO.get()[idx];
        if (s.valid && s.seed == worldSeed && s.x == x && s.z == z) {
            return s.val;
        }
        s.seed = worldSeed;
        s.x = x;
        s.z = z;
        s.val = lakeAt0(worldSeed, x, z, s);
        s.valid = true;
        return s.val;
    }

    /**
     * lakeAt 原始求值体（P19 U8 起由 {@link #lakeAt} 的列级 memo 包裹）。
     * <p>
     * <b>P23 R1①（v1.20.46 批2 S2）</b>：trunk 激活门删除——湖改为<b>全域 {@link #LAKE_INTERVAL}
     * 站格独立判定</b>；新增 {@link #LAKE_STATION_D_MIN} 小湖淘汰腿（第二近站过近 ⇒ NO_LAKE，
     * 防双站过近破形）。
     * <p>
     * <b>P24-C3（v1.20.47）</b>：把本次 3×3 扫描的 dC/dN 顺手快照进列槽 {@code slot}
     * （{@code d0/d1/distValid}），供 {@link #lakeIslandTopAt} 的绝对半径腿<b>零重扫复用</b>。
     * 纯快照、无新真值。
     * <p>
     * <b>P32（T1-3 湖基场重构，v1.20.55）：<code>P = clamp(W + s/dN, 0, 1) + 双倍频微噪</code></b>
     * ——s = 到 <b>argmin 站形状边界</b>的带符号距（内负外正，warp 空间锚点相对系；站形状参数 =
     * 超椭圆/主副 blob 两层制，抽签盐 {@link #SALT_LAKE_SHAPE}，512 槽直映缓存
     * {@link #stationParams}）；dN = 次近站距。求值序：<b>D_MIN 淘汰先于形状求值</b>（哨兵路径
     * 不触站参数缓存、不加噪——镜像原"哨兵不加噪"纪律）；快照后取 argmin 站参数求 s。
     * <b>钳基压（加噪前）</b>：内侧 P&lt;0 区域（锚点未钳 −0.10 量级）钳 0——零读者区分 P=0 与
     * P&lt;0（lakeBedAt 的 u=clamp(P/W)、四档帽/island 阈谓词、kPress 归一全为阈/归一口径，
     * E-A §1 全读者扫描）；上钳只在<b>孤立对 flip 带</b>激活（flip 处 base = W + 1 − 2r/D，
     * D &gt; 2r/W ≈ 4570 的孤立站对可越 1 ⇒ 饱和 1.0 = {@link #NO_LAKE} 语义——远场"无湖"列，
     * 两值对全部消费者同判 {@code lake < 阈}，值域断言带 [−0.0037, 1.0037] 内；E-A §1 的
     * "0.925 恒不激活"账按 D_MIN 反推、未含远 flip，实测读数修正）。缎带缘带短路/ribbonGate
     * 支路（P28-L D1·R1）随本重构整体
     * 移除——非哨兵路径收敛为无条件 base + 双噪。微噪两腿 λ{@link #LAKE_PRESSURE_NOISE_SCALE}/
     * λ{@link #LAKE_PRESSURE_NOISE_SCALE2} 原样（加在返回值上、<b>不进</b> dC/dN 快照与岛绝对
     * 腿）。LakeSlot.d0/d1 快照契约不变（d1 仍为死字段，维持现状）。
     */
    private static double lakeAt0(long worldSeed, int x, int z, LakeSlot slot) {
        // P32 T1-3：同一次 warp + 3×3 平方选站扫描带出 argmin 站（lakeStationScan 4 槽）——
        // dC/dN 快照契约原样（岛的绝对半径腿零重扫复用，P24-C3）。
        final double[] dd = LAKE_SCAN_BUF.get();
        lakeStationScan(worldSeed, x, z, dd);
        if (slot != null) {
            slot.d0 = dd[0];
            slot.d1 = dd[1];
            slot.distValid = true;
        }
        // P23 R1：小湖淘汰腿——第二近站 dd[1] 过近（双站破形域）整列 NO_LAKE。P32 起淘汰
        // 先于形状求值：哨兵路径不触站参数缓存、不加噪（NO_LAKE 语义纯净）。
        if (dd[1] < LAKE_STATION_D_MIN) {
            return NO_LAKE;
        }
        final LakeShapeSlot sp = stationParams(worldSeed, (int) dd[2], (int) dd[3]);
        final double[] rel = LAKE_SCAN_AUX.get();
        final double s = shapeSDist(sp, rel[0], rel[1]);
        // P32：加噪前钳基压（clamp(·,0,1)——下钳消"深于锚点平台的无信息区"，上钳纯防御）。
        double base = LAKE_WATER_LEVEL + s / dd[1];
        base = base < 0.0D ? 0.0D : (base > 1.0D ? 1.0D : base);
        // P25 D3③ + P26-B3 D1·A1：压力加性轮廓噪声双倍频（λ220 低频腿 + λ70 高频腿，独立盐域；
        // 总幅 0.0037 ≈ ±8.3 格湖缘径向微摆，换算式见 LAKE_PRESSURE_NOISE_AMP）。
        // D_MIN 淘汰哨兵路径在上方已 return ⇒ NO_LAKE 域零形状求值/零噪声（对拍口径保持）。
        return base
            + LAKE_PRESSURE_NOISE_AMP * GTSRWorldgenHash.valueNoise(
                worldSeed ^ SALT_LAKE_PRESSURE,
                x / LAKE_PRESSURE_NOISE_SCALE,
                z / LAKE_PRESSURE_NOISE_SCALE)
            + LAKE_PRESSURE_NOISE_AMP2 * GTSRWorldgenHash.valueNoise(
                worldSeed ^ SALT_LAKE_PRESSURE2,
                x / LAKE_PRESSURE_NOISE_SCALE2,
                z / LAKE_PRESSURE_NOISE_SCALE2);
    }

    /**
     * <b>湖置水列谓词</b>（v1.20.43 P22 版 B O1a，{@link #lakeAt} 的布尔单一出口，
     * {@code submergedAt} 先例同款）：{@code lakeAt < LAKE_WATER_LEVEL} 的逐字包装——湖床/湖水区
     * （水径推导见 {@link #LAKE_WATER_LEVEL}）。消费面：{@code PlacementGate.dryColumnAt} 腿②
     * （O1a 出口口径；原 ProsperityCaveField 水柱保护腿已随洞穴移除（P23 S1）退役）。
     * <p>
     * ⚠ 与湖<b>岸</b>口径（{@code lakeAt < LAKE_SHORE}，SwampFieldGrid / ProsperityLumenPlacer /
     * fillSanzuLakes 消费）<b>不同阈不并收</b>——O1a 纪律：阈值语义差异不得强行统一，岸径腿保持内联。
     */
    public static boolean lakeWaterAt(long worldSeed, int x, int z) {
        return lakeAt(worldSeed, x, z) < LAKE_WATER_LEVEL;
    }

    /**
     * <b>sanzu 群系压力域布尔出口</b>（P27-L D1·4a 新增，{@code lakeWaterAt} 先例同款）：
     * {@code lakeAt < sanzuBiomeShoreAt} 的逐字包装——湖+滩+抖动滩缘的<b>群系平面压力域</b>。
     * 消费面：{@code ChunkProviderProsperityRuins.fillSanzuLakes} 的置水门（4a 灌水对齐：灌水域
     * 对齐群系压力域，补上抖动滩缘 [SHORE, shoreAt) 内 h&lt;SEA 的干坑，水域 ⊆ 平面压力域）。
     * <p>
     * ⚠ 与 {@link #lakeWaterAt}（腿②：{@code lakeAt < LAKE_WATER_LEVEL}，PlacementGate 消费，
     * <b>逐字不动</b>——改它会动结构禁入域）<b>不同阈不并收</b>：本出口只服务「灌水对齐」，岸/水
     * 两口径语义差异不得强行统一（O1a 纪律同款）。
     */
    public static boolean sanzuShoreWaterAt(long worldSeed, int x, int z) {
        return lakeAt(worldSeed, x, z) < sanzuBiomeShoreAt(worldSeed, x, z);
    }

    /**
     * 巨湖 Voronoi 的<b>一次共用几何求值</b>（v1.20.41 P20 S5d 从 {@link #lakeAt0} 原样抽出）：
     * 对坐标加一次 domain-warp 后做 3×3 Worley 扫描，把 {@code out[0] = dC}（到最近湖站的
     * <b>绝对</b>格距，未归一）与 {@code out[1] = dN}（次近格距）写给调用方。<b>P25 D3② 起
     * warp = 主盘+副倍频盘同点相加</b>（{@link #LAKE_WARP_SCALE}/{@link #LAKE_WARP} 第 4 张表 +
     * {@link #LAKE_WARP_SUB_SCALE}/{@link #LAKE_WARP_SUB} 第 5 张表，盐域分离；P32 起幅度
     * 30/5，收敛账见 {@link #LAKE_WARP}）；<b>out 契约不变：仍只写 [0]/[1] 两槽</b>，判据侧
     * double[2] 调用面零改动。两个消费口：
     * <ul>
     * <li>{@link #lakeAt0} = {@code clamp(W + s/dN, 0, 1) + 微噪}（s = argmin 站形状距，P32）；</li>
     * <li>{@link #lakeIslandTopAt} 的<b>绝对半径腿</b> = {@code 1 − dC/LAKE_ISLAND_RADIUS}（plan §28-A
     * 路 (b)）：需求 8 的"岛约 30 格"是绝对尺寸，要的是绝对量 dC，而 {@code dC/dN} 会随湖大小涨落。</li>
     * </ul>
     * <b>为什么绝对腿吃 dC 而不是"到 {@link #lakeCellCenterAt} 中心的距离"</b>：后者要先反解压力零点
     * （两次不动点），而位移场是幅度 {@link #LAKE_WARP} = 70、波长 {@link #LAKE_WARP_SCALE} = 320 的正弦
     * ⇒ <b>最大</b>斜率 {@code 2π·70/320 = 1.374 > 1} ⇒ 收缩比 0.22 的估计在陡区不成立，两步后残差可达
     * 几十格，把岛整体推离自家湖心（S5c 的只报量 minP 实测：outlier #23 = 2.37e-02 / #30 = 1.86e-02
     * vs 其余 30 座 1.17e-04）。dC 是本列 3×3 扫描的直接观测量、<b>零反解</b> ⇒ 对该残差免疫；且它的
     * 等值线天然活在 warp 后的坐标空间里，与压力腿同域，两条腿取 min 才可比。
     * <p>
     * 位移 ≪ 3×3 窗半宽（{@code 1.5×LAKE_INTERVAL}）⇒ F1/F2 窗内仍数学精确（原 {@link #lakeAt0} 的
     * 申报随扫描一起搬来）。{@code out} 由调用方持有（热路径零分配），本式不跨调用保存任何东西。
     * <p>
     * <b>公开理由（v1.20.41 S5d）</b>：离线判据 {@code SanzuLakeMorphologyCheck} 要区分"C1b 的 dry=0"
     * 是<b>绝对腿</b>裁的（dC 已超干半径）还是<b>压力腿</b>裁的（p 已超干阈），必须直读 dC/dN 两站；
     * 本仓判据纪律是"全部读数是生产纯函数的直调 ⇒ 判据侧零重写任何形状式"（该文件类注释），
     * 故开本出口，与 {@link #lakeCellCenterAt} 同级待遇。<b>生产侧唯一消费口仍是 {@link #lakeAt0}
     * 与 {@link #lakeIslandTopAt}</b>。
     * <p>
     * <b>P32 T1-3：内部委托 {@link #lakeStationScan}</b>（同一次 warp + 3×3 平方选站 + argmin 记录
     * ——lakeAt0 需要的 argmin 站出口与 dC/dN 共用同一次扫描，无第二份形状；本出口仍只写
     * [0]/[1]，dC 语义 = warp 后空间的锚点绝对距，不变）。
     */
    public static void lakeStationDistances(long worldSeed, int x, int z, double[] out) {
        final double[] sd = LAKE_SCAN_BUF.get();
        lakeStationScan(worldSeed, x, z, sd);
        out[0] = sd[0];
        out[1] = sd[1];
    }

    /**
     * 巨湖 Voronoi 的<b>内部 4 槽扫描</b>（P32 T1-3 从 {@link #lakeStationDistances} 原样抽出并
     * 加 argmin 出口）：warp 合成 + 3×3 平方距离选站，{@code out} 契约 = {@code [0]=dC、
     * [1]=dN、[2]=argmin 站格 gx、[3]=argmin 站格 gz}（后两槽以 double 携带整数格号，调用方
     * 自行取整）。<b>argmin 在平方选站循环内顺手记录</b>（零额外哈希——锚点坐标 ex/ez 循环内
     * 已在算）；平手规则 = 前向/反解同一条首遇获胜 {@code <}（与 {@link #lakeCellCenterAt} 的
     * 3×3 同迭代序、同严格比较 ⇒ 双向恒选同站，sqrt 单调 ⇒ 平方序与开根序逐位一致）。
     * 附带把 {@code [px − argmin 锚 X, pz − argmin 锚 Z]} 写进 {@link #LAKE_SCAN_AUX}
     * （形状求值的锚点相对坐标；即取即用纪律，调用方在触任何 diskAt 前消费）。
     */
    private static void lakeStationScan(long worldSeed, int x, int z, double[] out) {
        // v1.20.40 P19 §D 破圆：湖 Voronoi 输入坐标先揉一次 disk 位移（幅度/波长见常量注释）；
        // buf 取完即拷出，不跨后续 diskAt 持有（DISK_BUF 单缓冲纪律）。
        // ── P23 R1·S6 性能尝试（v1.20.46）：已试并已回退 ── 曾把 3×3 站帧按 (seed,cellX,cellZ)
        // 单格缓存（消 18 次 cellOffset）+ 平方距离选站（消 7 次 sqrt），两者都<b>逐位等价</b>
        // （对拍证据 temp/p23-s6/lake-{before,after}.bin：4,194,304 列×3 值 + 1,048,576 列
        // islandTop，SHA256 相同），但<b>本机实测零收益</b>（同列热路径 lakeStationDistances
        // 旧 0.062-0.070us vs 新 0.061-0.078us；GenBench 交替 13/18 次中位 370.9 vs 372.0us）
        // ——省下的哈希成本被两个 ThreadLocal.get() 抵掉 ⇒ 按"S6 性能尝试不可行即回退"纪律
        // 还原原式（该轮 GenBench 只做 BASE 重立，不做代码优化）。详见 GenBenchCheck javadoc。
        // <b>P24-C4（v1.20.47）拆开重做</b>：只保留"平方距离选站"这一半（无新 ThreadLocal.get、
        // 纯栈上算术），站帧缓存那一半仍不回退（S6 已证其 ThreadLocal 开销吃掉收益）。
        final double[] buf = diskBuffer();
        diskAt(worldSeed, SLOT_DISK_LAKE_WARP, LAKE_WARP_SCALE, x, z, buf);
        // P25 D3②：副倍频盘与主盘<b>同点相加</b>（各自在原始 (x,z) 取值；buf 单缓冲纪律——
        // 主盘分量先拷出再取副盘，不跨 diskAt 持有）。P32 起合成位移 ≤ 30+5 = 35、斜率上界
        // 0.362 < 1（收敛账见 LAKE_WARP）。
        final double wx = buf[0] * LAKE_WARP;
        final double wz = buf[1] * LAKE_WARP;
        diskAt(worldSeed, SLOT_DISK_LAKE_SUB, LAKE_WARP_SUB_SCALE, x, z, buf);
        final double px = x + wx + buf[0] * LAKE_WARP_SUB;
        final double pz = z + wz + buf[1] * LAKE_WARP_SUB;
        final int cellX = (int) Math.floor(px / LAKE_INTERVAL + 0.5D);
        final int cellZ = (int) Math.floor(pz / LAKE_INTERVAL + 0.5D);
        // ── P24-C4（v1.20.47）：平方距离选站，只对最终 dC/dN 各开一次 sqrt（消 7 次 sqrt）──
        // 等价性：sqrt 在 [0,∞) 上严格单调且正确舍入 ⇒ "sq 序"与"sqrt(sq) 序"逐位一致（含相等/平手
        // 的 `<` 语义）；选中的两个 sq 各自开根，得到的 double 与原式对该站算出的 d 逐位相同。
        // 9 站 → 2 次 sqrt（原 9 次）。构造性逐位等价，对拍证据见 temp/lake-d/tri-*.bin。
        // P32 T1-3：argmin 站格与胜者相对坐标在循环内顺手记录（零额外哈希）。
        double sC = Double.POSITIVE_INFINITY;
        double sN = Double.POSITIVE_INFINITY;
        int aGx = 0;
        int aGz = 0;
        double winEx = 0.0D;
        double winEz = 0.0D;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int gx = cellX + dx;
                final int gz = cellZ + dz;
                final double ex = gx * LAKE_INTERVAL + cellOffset(worldSeed, gx, gz, SALT_LAKE_CELL_X, LAKE_INTERVAL)
                    - px;
                final double ez = gz * LAKE_INTERVAL + cellOffset(worldSeed, gx, gz, SALT_LAKE_CELL_Z, LAKE_INTERVAL)
                    - pz;
                final double sq = ex * ex + ez * ez;
                if (sq < sC) {
                    sN = sC;
                    sC = sq;
                    aGx = gx;
                    aGz = gz;
                    winEx = ex;
                    winEz = ez;
                } else if (sq < sN) {
                    sN = sq;
                }
            }
        }
        out[0] = Math.sqrt(sC);
        out[1] = Math.sqrt(sN);
        out[2] = aGx;
        out[3] = aGz;
        final double[] aux = LAKE_SCAN_AUX.get();
        aux[0] = -winEx;
        aux[1] = -winEz;
    }

    // ═══ P32 T1-3：站形状参数——512 槽 ThreadLocal 直映缓存 ═══
    //
    // S6 教训边界申报（P23-S6 站帧缓存回退的适用性，RVF:1855-1863 实读）：该教训 = "HashMap 缓
    // <b>廉</b>价值（18 次 cellOffset ≈ 数十 ns）被装箱 + 两次 ThreadLocal.get() 吃光 ⇒ 零收益"。
    // 本缓存两个参数都变了：(i) 重算价 = 抽签 4-10 次 hash01 + cos/sin 预计算 + ⟂-邻站 16 次
    // cellOffset ≈ 150-300ns/站次（10-30× S6 的廉价值）；(ii) <b>直映数组</b>探测（一次
    // ThreadLocal.get + 下标 + 标签比对，无装箱）≈ 5-10ns——与仓内最热路径 LAKE_MEMO 同款范式。
    // 教训适用于"HashMap 缓廉价值"，不适用于"数组缓贵值"。命中率：argmin 站在整块 Voronoi 胞
    // （~3000×3000 连续区）内不变 ⇒ 单 chunk 命中 ≈ 100%；驱逐 = 碰撞重算（标签 (seed,gx,gz)
    // 全比对，值是纯函数 ⇒ 无陈旧风险）。哨兵路径（D_MIN 门内）不触本缓存（防污染 + 零成本）。

    /** 站参数缓存容量（2 的幂，mask 直取）。 */
    private static final int LAKE_SHAPE_CAP = 512;

    /** {@link #LAKE_SHAPE_CAP} − 1（直映下标掩码）。 */
    private static final int LAKE_SHAPE_MASK = LAKE_SHAPE_CAP - 1;

    /**
     * 站形状参数槽（P32 T1-3）。<b>槽态纯净纪律</b>：{@link #fillShapeSlot} 对<b>全字段</b>显式写
     * ——class1/2 分支必须清零 class0 的 a/b/invA/invB（值虽不被 cls1/2 求值消费，但直映槽会残留
     * 前一 class0 occupant 的字段；"命中/未命中两态逐位一致"纪律要求槽内容是 (seed,gx,gz) 的
     * 纯函数——P5 原型期 L3a 抓出的 bug，实装必须保留清零，plan/tmp/p32-t1p2-bench.md §3.1）。
     */
    private static final class LakeShapeSlot {

        long seed;
        int gx;
        int gz;
        boolean valid;
        /** 0=单超椭圆 / 1=主+单副 blob / 2=主+双副 blob。 */
        int cls;
        /** class0 超椭圆指数（2/3；class1/2 恒 0）。 */
        int n;
        /** 副 blob 数（0/1/2）。 */
        int nSub;
        /** class0 半轴（a ≥ r0 ≥ b 保面积）；class1/2 清零。 */
        double a;
        double b;
        double invA;
        double invB;
        /** 朝向（填充期预计算 cos/sin）。 */
        double cosT;
        double sinT;
        /** 主 blob 心与半径（世界系锚点相对坐标，填充期预计算）。 */
        double cx;
        double cz;
        double rhoP;
        /** 副 blob 1/2（世界系，填充期预计算）。 */
        double s1x;
        double s1z;
        double rhoS1;
        double s2x;
        double s2z;
        double rhoS2;
    }

    /** lakeAt 站参数专用槽表初始化（容量/淘汰纪律同 {@link #lakeSlots()}）。 */
    private static LakeShapeSlot[] lakeShapeSlots() {
        final LakeShapeSlot[] a = new LakeShapeSlot[LAKE_SHAPE_CAP];
        for (int i = 0; i < a.length; i++) {
            a[i] = new LakeShapeSlot();
        }
        return a;
    }

    private static final ThreadLocal<LakeShapeSlot[]> LAKE_SHAPE_CACHE = ThreadLocal
        .withInitial(GTSRVoronoiRiverField::lakeShapeSlots);

    /**
     * argmin 站的形状参数（512 槽直映缓存：{@code index = (gx·0x9E3779B1 ^ gz·0x85EBCA6B)
     * & 511} 混合散列、标签 (seed,gx,gz) 全键比对——正确性只靠全键比较，散列仅决定淘汰分布；
     * 未命中 = 抽签填充 + 预计算）。哨兵路径（D_MIN 门内）不触本缓存。
     */
    private static LakeShapeSlot stationParams(long worldSeed, int gx, int gz) {
        final LakeShapeSlot s = LAKE_SHAPE_CACHE.get()[(gx * 0x9E3779B1 ^ gz * 0x85EBCA6B) & LAKE_SHAPE_MASK];
        if (s.valid && s.seed == worldSeed && s.gx == gx && s.gz == gz) {
            return s;
        }
        fillShapeSlot(s, worldSeed, gx, gz);
        return s;
    }

    /**
     * 站形状参数抽签（P32 T1-3）。抽签流：{@code cellSeed(seed,gx,gz,SALT_LAKE_SHAPE)} 后逐次
     * splitmix64 续链（首抽 = hash01(cellSeed)，同 :1638-1640 站键口径）；draw 计数
     * class0 = 4（⟂ 触发 +1）/ class1 = 7 / class2 = 10 + cos/sin 各 1 次预计算。
     * <b>⟂-邻站定向</b>（{@link #LAKE_SHAPE_PERP_D} 触发时）：class0 长轴 ⟂ 邻站方向；
     * class1 单副角旋出禁置扇区（{@link #perpExitAngle}）；class2 双副<b>整对同旋</b>（bisector
     * → 邻站反向）——张角 sep 逐位保持，两副 blob 距邻站方向 ≥ π − sep/2 ≥ π/2 &gt; π/4 恒出
     * 扇区。确定性纯函数（邻站偏移同 seed 同值）。
     */
    private static void fillShapeSlot(LakeShapeSlot s, long worldSeed, int gx, int gz) {
        long h = GTSRWorldgenHash.cellSeed(worldSeed, gx, gz, SALT_LAKE_SHAPE);
        h = GTSRWorldgenHash.splitmix64(h);
        final double u = (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR;
        h = GTSRWorldgenHash.splitmix64(h);
        final double th = (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR * (2.0D * Math.PI);
        final int cls = u < LAKE_SHAPE_P_CLASS1 ? 0 : (u < LAKE_SHAPE_P_CLASS2 ? 1 : 2);
        // 槽态纯净：全字段显式写（含 cls1/2 清零 cls0 半轴——L3a bug 修复面，见 LakeShapeSlot 注）。
        s.seed = worldSeed;
        s.gx = gx;
        s.gz = gz;
        s.valid = true;
        s.cls = cls;
        s.n = 0;
        s.nSub = 0;
        s.a = 0.0D;
        s.b = 0.0D;
        s.invA = 0.0D;
        s.invB = 0.0D;
        s.cx = 0.0D;
        s.cz = 0.0D;
        s.rhoP = 0.0D;
        s.s1x = 0.0D;
        s.s1z = 0.0D;
        s.rhoS1 = 0.0D;
        s.s2x = 0.0D;
        s.s2z = 0.0D;
        s.rhoS2 = 0.0D;
        // ── ⟂-邻站定向裁决（缓存填充期一次成本；16 次 cellOffset，零新真值）──
        double theta = th;
        boolean perp = false;
        double nbAng = 0.0D;
        {
            final double ax = gx * LAKE_INTERVAL
                + cellOffset(worldSeed, gx, gz, SALT_LAKE_CELL_X, LAKE_INTERVAL);
            final double az = gz * LAKE_INTERVAL
                + cellOffset(worldSeed, gx, gz, SALT_LAKE_CELL_Z, LAKE_INTERVAL);
            double bestSq = LAKE_SHAPE_PERP_D * LAKE_SHAPE_PERP_D;
            for (int nz = -1; nz <= 1; nz++) {
                for (int nx = -1; nx <= 1; nx++) {
                    if (nx == 0 && nz == 0) {
                        continue;
                    }
                    final double ox = (gx + nx) * LAKE_INTERVAL
                        + cellOffset(worldSeed, gx + nx, gz + nz, SALT_LAKE_CELL_X, LAKE_INTERVAL) - ax;
                    final double oz = (gz + nz) * LAKE_INTERVAL
                        + cellOffset(worldSeed, gx + nx, gz + nz, SALT_LAKE_CELL_Z, LAKE_INTERVAL) - az;
                    final double dsq = ox * ox + oz * oz;
                    if (dsq < bestSq) {
                        bestSq = dsq;
                        nbAng = Math.atan2(oz, ox);
                        perp = true;
                    }
                }
            }
        }
        if (perp && cls == 0) {
            h = GTSRWorldgenHash.splitmix64(h);
            final double jit = ((h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR) * 2.0D - 1.0D;
            theta = nbAng + 0.5D * Math.PI + jit * LAKE_SHAPE_PERP_JITTER;
        }
        s.cosT = Math.cos(theta);
        s.sinT = Math.sin(theta);
        if (cls == 0) {
            h = GTSRWorldgenHash.splitmix64(h);
            final double v = (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR;
            s.n = v < LAKE_SHAPE_P_N3 ? 3 : 2;
            final double aMin = s.n == 2 ? LAKE_SHAPE_A_N2_MIN : LAKE_SHAPE_A_N3_MIN;
            h = GTSRWorldgenHash.splitmix64(h);
            final double t = (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR;
            s.a = (aMin + t * (LAKE_SHAPE_A_N3_MAX - aMin)) * LAKE_SHAPE_R0;
            s.b = LAKE_SHAPE_R0 * LAKE_SHAPE_R0 / s.a;
            s.invA = 1.0D / s.a;
            s.invB = 1.0D / s.b;
        } else {
            h = GTSRWorldgenHash.splitmix64(h);
            final double rhoPt = (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR;
            s.rhoP = (LAKE_SHAPE_RHO_P_MIN + rhoPt * (LAKE_SHAPE_RHO_P_MAX - LAKE_SHAPE_RHO_P_MIN))
                * LAKE_SHAPE_R0;
            h = GTSRWorldgenHash.splitmix64(h);
            final double co = (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR * LAKE_SHAPE_CENTER_OFF_MAX
                * LAKE_SHAPE_R0;
            s.cx = co * s.cosT;
            s.cz = co * s.sinT;
            final double rhoSMax = cls == 2 ? LAKE_SHAPE_RHO_S_MAX_C2 : LAKE_SHAPE_RHO_S_MAX;
            if (cls == 1) {
                s.nSub = 1;
                h = GTSRWorldgenHash.splitmix64(h);
                double ang = (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR * (2.0D * Math.PI);
                h = GTSRWorldgenHash.splitmix64(h);
                final double rhoS = (LAKE_SHAPE_RHO_S_MIN
                    + (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR * (rhoSMax - LAKE_SHAPE_RHO_S_MIN))
                    * LAKE_SHAPE_R0;
                h = GTSRWorldgenHash.splitmix64(h);
                final double offSt = (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR;
                // 重叠约束（域内恒松，钳制保形）：offset_s ≤ ρ_p + ρ_s − 0.3·k_smin。
                final double offS = Math.min(
                    (LAKE_SHAPE_OFF_S_MIN + offSt * (LAKE_SHAPE_OFF_S_MAX - LAKE_SHAPE_OFF_S_MIN))
                        * LAKE_SHAPE_R0,
                    s.rhoP + rhoS - 0.3D * LAKE_SHAPE_K_SMIN);
                if (perp) {
                    ang = perpExitAngle(ang, nbAng);
                }
                s.rhoS1 = rhoS;
                s.s1x = s.cx + offS * Math.cos(ang);
                s.s1z = s.cz + offS * Math.sin(ang);
            } else {
                s.nSub = 2;
                h = GTSRWorldgenHash.splitmix64(h);
                final double ang0 = (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR * (2.0D * Math.PI);
                h = GTSRWorldgenHash.splitmix64(h);
                final double sep = LAKE_SHAPE_C2_SEP_MIN
                    + (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR
                        * (Math.PI - LAKE_SHAPE_C2_SEP_MIN);
                h = GTSRWorldgenHash.splitmix64(h);
                final double rhoS1 = (LAKE_SHAPE_RHO_S_MIN
                    + (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR * (rhoSMax - LAKE_SHAPE_RHO_S_MIN))
                    * LAKE_SHAPE_R0;
                h = GTSRWorldgenHash.splitmix64(h);
                final double offS1t = (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR;
                final double offS1 = Math.min(
                    (LAKE_SHAPE_OFF_S_MIN + offS1t * (LAKE_SHAPE_OFF_S_MAX - LAKE_SHAPE_OFF_S_MIN))
                        * LAKE_SHAPE_R0,
                    s.rhoP + rhoS1 - 0.3D * LAKE_SHAPE_K_SMIN);
                h = GTSRWorldgenHash.splitmix64(h);
                final double rhoS2 = (LAKE_SHAPE_RHO_S_MIN
                    + (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR * (rhoSMax - LAKE_SHAPE_RHO_S_MIN))
                    * LAKE_SHAPE_R0;
                h = GTSRWorldgenHash.splitmix64(h);
                final double offS2t = (h >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR;
                final double offS2 = Math.min(
                    (LAKE_SHAPE_OFF_S_MIN + offS2t * (LAKE_SHAPE_OFF_S_MAX - LAKE_SHAPE_OFF_S_MIN))
                        * LAKE_SHAPE_R0,
                    s.rhoP + rhoS2 - 0.3D * LAKE_SHAPE_K_SMIN);
                double a0 = ang0;
                if (perp) {
                    // 整对同旋（bisector → 邻站反向）：sep 逐位保持，两副出禁置扇区（见方法注）。
                    a0 = nbAng + Math.PI - sep / 2.0D;
                }
                final double ang1 = a0 + sep;
                s.rhoS1 = rhoS1;
                s.s1x = s.cx + offS1 * Math.cos(a0);
                s.s1z = s.cz + offS1 * Math.sin(a0);
                s.rhoS2 = rhoS2;
                s.s2x = s.cx + offS2 * Math.cos(ang1);
                s.s2z = s.cz + offS2 * Math.sin(ang1);
            }
        }
    }

    /**
     * ⟂ 触发时<b>单副</b> blob 的禁置扇区旋出：{@code ang' = 邻站角 + π + δ/2}
     * （δ = norm(ang − 邻站角) ∈ (−π, π]）。ang' 距邻站方向 ≥ π − |δ|/2 ≥ π/2 &gt;
     * {@link #LAKE_SHAPE_FORBID_HALF} = π/4 恒出扇区，且保留原抽签的方向多样性（连续映射）。
     */
    private static double perpExitAngle(double ang, double center) {
        double d = ang - center;
        d -= Math.floor(d / (2.0D * Math.PI)) * (2.0D * Math.PI);
        if (d > Math.PI) {
            d -= 2.0D * Math.PI;
        }
        return center + Math.PI + 0.5D * d;
    }

    /**
     * 多项式 smin（P32 T1-3；ATG MathUtil.polymax 对偶）：{@code h = clamp(0.5+0.5(a−b)/k)}、
     * {@code smin = a + h·(b−a) − k·h·(1−h)}——C¹ 切向连续，真并集外的伪内部下探 ≤ k/4。
     */
    private static double sminPoly(double a, double b, double k) {
        double h = 0.5D + 0.5D * (a - b) / k;
        h = h < 0.0D ? 0.0D : (h > 1.0D ? 1.0D : h);
        return a + h * (b - a) - k * h * (1.0D - h);
    }

    /**
     * 到站形状<b>边界</b>的带符号距 s（锚点相对系，内负外正；{@link #lakeAt0} 消费）：
     * <ul>
     * <li><b>class0</b>：径向伪 SDF {@code s = (q − 1)·b}（q = 超椭圆向径函数——n=2 走
     * sqrt、n=3 走 cbrt，零 pow；边界轨迹精确（P=W ⇔ q=1），沿长轴向深压缩 ≤ ×(a/b) ≤ 1.08
     * ——内切/岛账按短轴（最坏向）推导，不受影响；E-A §4 口径注记，远场斜率账见
     * plan/tmp/p32-t1p2-bench.md §3.2）；</li>
     * <li><b>class1/2</b>：{@code s = smin(d_i − ρ_i)}（主 blob 先、副 blob 依次 smin 融合，
     * k = {@link #LAKE_SHAPE_K_SMIN}；偏差 ≤ k/4 已计入内切 slack）。</li>
     * </ul>
     */
    private static double shapeSDist(LakeShapeSlot sp, double dx, double dz) {
        if (sp.cls == 0) {
            final double rx = dx * sp.cosT + dz * sp.sinT;
            final double rz = -dx * sp.sinT + dz * sp.cosT;
            final double ux = rx * sp.invA;
            final double uz = rz * sp.invB;
            final double q;
            if (sp.n == 2) {
                q = Math.sqrt(ux * ux + uz * uz);
            } else {
                final double mx = Math.abs(ux);
                final double mz = Math.abs(uz);
                q = Math.cbrt(mx * mx * mx + mz * mz * mz);
            }
            return (q - 1.0D) * sp.b;
        }
        double ddx = dx - sp.cx;
        double ddz = dz - sp.cz;
        double v = Math.sqrt(ddx * ddx + ddz * ddz) - sp.rhoP;
        if (sp.nSub >= 1) {
            ddx = dx - sp.s1x;
            ddz = dz - sp.s1z;
            v = sminPoly(v, Math.sqrt(ddx * ddx + ddz * ddz) - sp.rhoS1, LAKE_SHAPE_K_SMIN);
        }
        if (sp.nSub >= 2) {
            ddx = dx - sp.s2x;
            ddz = dz - sp.s2z;
            v = sminPoly(v, Math.sqrt(ddx * ddx + ddz * ddz) - sp.rhoS2, LAKE_SHAPE_K_SMIN);
        }
        return v;
    }

    /**
     * 巨湖床高（两参便捷形态；显式形态见 {@link #lakeBedAt(long, int, int, double)}）。
     */
    public static double lakeBedAt(long worldSeed, int x, int z) {
        return lakeBedAt(worldSeed, x, z, lakeAt(worldSeed, x, z));
    }

    /**
     * 巨湖床高——<b>v1.20.40（P19 plan §D）中心渐深</b>；<b>P24-D（v1.20.47）外坡剖面由单段 smoothstep
     * 改为多段线性</b>（设计式与理由见 {@link #LAKE_BED_PLATEAU} 上方的 P24-D 段与
     * {@link #LAKE_BED_KNOT_U}）：床沿湖压力从湖滨锚经"深盆平台 → 外坡控制点"渐变到湖心锚。
     * <ul>
     * <li><b>湖滨锚</b> = {@code SEA_LEVEL − 1}（plan §D"湖滨床≈pool−1"口径：巨湖水面恒
     * = SEA_LEVEL=68，水缘处床贴水面下 1 格）；</li>
     * <li><b>湖心锚</b> = {@code SEA_LEVEL − LAKE_CENTER_DEPTH} = <b>40</b>（v1.20.41 S5 水深 28 ⇒
     * 湖心锚正好贴全局高度地板 {@code MIN_HEIGHT=40}；v1.20.40 旧口径"锚 = 58、水深 10 带 8-12"
     * 原文保留在 {@link #LAKE_CENTER_DEPTH} 的注释里）；</li>
     * <li><b>插值域</b> = 湖水区压力 [0, {@link #LAKE_WATER_LEVEL}] 归一成的 u ∈ [0,1]，
     * <b>深盆平台 + 多段线性外坡</b>（平台外缘 {@link #LAKE_BED_PLATEAU}、控制点
     * {@link #LAKE_BED_KNOT_U}/{@link #LAKE_BED_KNOT_G}）：u ≤ 平台 ⇒ 恒取湖心锚，平台之外沿控制点
     * 逐段线性抬到湖滨锚——防坡折与"三档列数比"的形状见 {@link #LAKE_BED_PLATEAU}；
     * 水缘（lake=WATER）床恰为湖滨锚，湖滨带 [WATER, SHORE) 的 heightCore 渐变从同一值
     * 接续抬回原地形（水缘两侧连续，无坡折）；</li>
     * <li>床纹 = 低频 valueNoise（波长 {@link #LAKE_BED_NOISE_SCALE}）叠在渐变之上，<b>v1.20.41 S5
     * 起仿射 remap 到非负档</b>：本仓 {@code GTSRWorldgenHash.valueNoise} 的值域是
     * {@code [-1,1)}（{@code unitNoise} 双线性插值，见其 javadoc），改造前直接相加 ⇒ 床纹
     * {@code ±1}。<b>水深 10 时无所谓</b>（湖心锚 58，±1 全在地板之上）；水深 28 后湖心锚 = 40 =
     * {@code MIN_HEIGHT} ⇒ 负瓣会被 {@code heightCore} 末尾的钳制整段截平（一半的床纹列塌成同一个
     * 整数，sd 失真、判据 D1/D2 的分布也失真）。故床纹项改为 {@code 0.5 + 0.5·noise ∈ [0,1)}：
     * 湖心床恒 ∈ [40,41) ⇒ <b>零截平</b>，且仍取到 40/41 两个整数值（各向同性、不退化成单值）。
     * 注：计划 §5 S5-3 字面写的是非负半波 {@code 0.5 + 0.5·|noise|}，该式给 [0.5,1] ⇒ 床恒 ∈
     * [40.5,41) ⇒ 取整后<b>只剩一个值 41</b>（床纹退化成平地），与同一句自陈的目标"床恒 ∈ [40,41]
     * ⇒ 零截平、零 sd 失真"矛盾；本式（无绝对值的仿射 remap）才是该目标的解，已按实测申报为偏离，
     * <b>覆盖 §15 轮次里该字面的一切引用</b>——含 §8 常量取值纪律总表"床纹改非负半波 =
     * {@code 0.5 + 0.5·|noise|}"那一行（与 §5 S5-3 同源，S7 收口按本式回填，实现片不改计划文件）。
     * 值域证死出处：{@code GTSRWorldgenHash.java:141-147}（{@code unitNoise} 双线性 ⇒ [-1,1)）。</li>
     * </ul>
     * lakePressure 显式形态供 heightCore 复用同一次 {@link #lakeAt} 求值（消重复）；
     * 压力 ≥ WATER（湖滨带）时 u 饱和为 1 ⇒ 床 = 湖滨锚 ±1，与水缘列同解。
     */
    public static double lakeBedAt(long worldSeed, int x, int z, double lakePressure) {
        final double seaLevel = ProsperityTerrainProfile.SEA_LEVEL;
        final double shoreBed = seaLevel - 1.0D;
        final double centerBed = seaLevel - LAKE_CENTER_DEPTH;
        final double u = Math.min(1.0D, Math.max(0.0D, lakePressure / LAKE_WATER_LEVEL));
        final double g = lakeBedProfileG(u);
        return shoreBed + (centerBed - shoreBed) * g
            + 0.5D
            + 0.5D * GTSRWorldgenHash
                .valueNoise(worldSeed ^ SALT_LAKE_BED, x / LAKE_BED_NOISE_SCALE, z / LAKE_BED_NOISE_SCALE);
    }

    /**
     * 湖床外坡的 g(u) 纯函数（P24-D 新增；{@link #lakeBedAt} 与离线判据共用同一组控制点真值）：
     * {@code u ≤ LAKE_BED_PLATEAU} 恒 1；其上按 {@link #LAKE_BED_KNOT_U}/{@link #LAKE_BED_KNOT_G}
     * 段内线性插值，末点外恒 0。设计理由见 {@link #LAKE_BED_PLATEAU} 上方的 P24-D 段。
     */
    private static double lakeBedProfileG(double u) {
        if (u <= LAKE_BED_KNOT_U[0]) {
            return LAKE_BED_KNOT_G[0];
        }
        for (int k = 0; k + 1 < LAKE_BED_KNOT_U.length; k++) {
            final double u1 = LAKE_BED_KNOT_U[k + 1];
            if (u <= u1) {
                final double u0 = LAKE_BED_KNOT_U[k];
                final double t = (u - u0) / (u1 - u0);
                return LAKE_BED_KNOT_G[k] + (LAKE_BED_KNOT_G[k + 1] - LAKE_BED_KNOT_G[k]) * t;
            }
        }
        return LAKE_BED_KNOT_G[LAKE_BED_KNOT_G.length - 1];
    }

    /**
     * 湖滨带 [WATER, SHORE) 的<b>多级缓坡混合权重</b>（plan §15.4「多级缓坡 ≥3 个高差 ≤2 的台阶，
     * 非单一大台阶」的唯一实现真值，v1.20.41 P20 S5 新增；生产侧
     * {@code ProsperityTerrainProfile.heightCore} 的湖段与离线判据共用本式，无第二真值）。
     * <p>
     * 形状 = 先 {@code s01}（smoothstep 缓入缓出，替掉改造前的<b>线性</b> lerp——线性在环带两端
     * 各留一个折角，正是"衔接生硬"的来源），再量化成 {@link #LAKE_SHORE_TREADS} 级台阶
     * （{@code round(e·N)/N}）⇒ 从湖床到原地形之间出现 N 段等压踏面，每级 riser = 总抬升/N。
     * <p>
     * <b>为什么本式不可能形成"环形堤"</b>（§15.4 第三条形态的禁止项）：调用侧对本式的结果始终走
     * {@code y = Math.min(y, …)} 语义 ⇒ 环带列的高度<b>恒 ≤ 该列无湖时的原地形</b>，任何各向同性的
     * 等距抬升在结构上不可表示；残留的堤感只可能来自 riser 过大，由判据 A2（相邻列差值 p95）
     * 与 A4（台阶数）钉住。
     *
     * @param lakePressure {@link #lakeAt} 的原值（调用侧已保证 ∈ [WATER, SHORE)；带外入参按端点截断）
     * @return 混合权重 ∈ [0,1]：0 = 取满湖床，1 = 完全回到原地形
     */
    public static double lakeShoreBlend(double lakePressure) {
        final double t = (lakePressure - LAKE_WATER_LEVEL) / (LAKE_SHORE - LAKE_WATER_LEVEL);
        final double c = t < 0.0D ? 0.0D : (t > 1.0D ? 1.0D : t);
        final double e = c * c * (3.0D - 2.0D * c);
        return Math.round(e * LAKE_SHORE_TREADS) / (double) LAKE_SHORE_TREADS;
    }

    /**
     * 中心固定岛的<b>岛面高</b>（plan §15.5，v1.20.41 P20 S5 新增，逐列纯函数）：湖心区
     * {@code lakeAt < LAKE_ISLAND} 内把地表从湖床抬到 {@code SEA_LEVEL + LAKE_ISLAND_LIFT} = 72。
     * <p>
     * 形状 = {@code centerBed + (islandTop − centerBed) · s01(min(1, k/LAKE_ISLAND_PLATEAU))}，其中
     * {@code k = min(压力腿 (LAKE_ISLAND − lake)/LAKE_ISLAND, 绝对腿 1 − dC/LAKE_ISLAND_RADIUS)}
     * （岛缘 k=0、岛心 k=1；绝对腿自 v1.20.41 S5d 起吃 <b>dC</b>=本列到最近湖站的绝对格距，旧式
     * {@code 1 − r(lakeCellCenterAt)/本值} 的不动点反解残差问题见下面 S5c 的 ⚠ 依赖声明与其后的 S5d 段）
     * 。<b>P25 D3⑤：两腿各加自然化抖动</b>——压力腿阈 ×(1+0.08·n01(λ60))（单边只收域）、
     * 绝对腿半径 ×(1±0.10·hash01(站键))（逐湖常量），两腿继续复用同一次 3×3 扫描的 dC、
     * 禁独立 warp/第二不动点（细节见 {@link #LAKE_ISLAND_GATE_JITTER}/
     * {@link #LAKE_ISLAND_RADIUS_JITTER}）。用 s01 而非线性/硬切
     * 的理由（§5 S5-5「岛缘无单格悬崖」）：<b>旧句"岛半径实测中位 ≈17 格 ⇒ 每格最大爬升 ≈2.8"
     * 的 17 是被证伪的 9 格射线估值（见 {@link #LAKE_ISLAND} 的假因登记），当时岛域真半径只有
     * ≈11.2 ⇒ 旧穹顶的最大爬升 = 48/11.2 ≈ 4.3 格/列，接近而不是远低于 A1 的 5 格阈</b>。
     * v1.20.41 S5c 改平台 + 绝对半径后：坡 = {@code 48/(PLATEAU × 岛域半径) = 48/(0.6×30) = 2.67}
     * 格/列 ⇒ <b>岛变宽的同时坡度反而比改造前缓</b>（且岛缘列全部落在湖水区内，A1/A2/A4c 的取样域
     * 是环带 {@code [WATER, SHORE)} ⇒ 由构造不吃这条坡；只报读数 {@code islandCliffCols} 可见）。
     * <p>
     * 抬升量恒 ≤ 72（岛面）⇒ 与 {@code MAX_HEIGHT=110}、{@code HEIGHT_SENTINEL=108} 零接触。
     * 岛缘处本式给 40（= 湖心锚），而 {@link #lakeBedAt} 在同压列给 ≈40.8 ⇒ 调用侧
     * {@code Math.max} 取床值，接缝处不出现凹陷。
     *
     * @param lakePressure {@link #lakeAt} 的原值
     * @return 岛面高；列不在岛域（{@code lakePressure ≥ LAKE_ISLAND}，或被绝对半径裁出 ⇒
     *         {@code k ≤ 0}）时返回 {@link Double#NaN} 作"无抬升"哨兵（调用侧按 {@code !NaN} 分支，
     *         禁止与 0 混淆——0 是合法高度域外的值，NaN 不可比较 ⇒ 误用必显形）
     */
    public static double lakeIslandTopAt(long worldSeed, int x, int z, double lakePressure) {
        // P25 D3⑤(b)：压力腿阈 ×(1 + 0.08·n01(λ60))——单边只收域（T ≥ LAKE_ISLAND，岛域 ⊂ 湖水区
        // 不变式保持，见 LAKE_ISLAND_GATE_JITTER）；kPress 归一分母随 T 同源（不留"过阈即 k≤0"
        // 死带）。n01 与半径抖动（下方 (a) 腿）独立盐、互不相关。
        final double nGate = 0.5D + 0.5D * GTSRWorldgenHash
            .valueNoise(worldSeed ^ SALT_LAKE_ISLAND_GATE, x / LAKE_ISLAND_GATE_SCALE, z / LAKE_ISLAND_GATE_SCALE);
        final double islandGate = LAKE_ISLAND * (1.0D + LAKE_ISLAND_GATE_JITTER * nGate);
        if (lakePressure >= islandGate) {
            return Double.NaN;
        }
        final double seaLevel = ProsperityTerrainProfile.SEA_LEVEL;
        final double centerBed = seaLevel - LAKE_CENTER_DEPTH;
        final double islandTop = seaLevel + LAKE_ISLAND_LIFT;
        final double kPress = (islandGate - lakePressure) / islandGate;
        // v1.20.41 P20 S5c（plan §15.5 假因收束）：岛域的第二条腿 = 到湖心的<b>绝对</b>距离。
        // 只靠压力腿时岛线性于湖尺度（lakeAt = dC/dN 是尺度归一量），而 C_DRY 带是<b>绝对</b>面积带
        // （r ∈ [13.82,18.28] = 半径 ±15%），实测逐湖尺度散布 p10/p90 = 0.78/1.23（半径 ±23%）
        // ⇒ 面积散布 2.5 倍 > 带的 1.75 倍 ⇒ 任何"纯压力腿"的岛都数学上吃不满这条带
        // （S5c 扫描：0.030/0.60 档 43.75%、0.045/0.75 档 46.88%，见 island-scan.md）。
        // 取 min(压力腿, 绝对腿) 后：大湖被绝对腿钉在固定直径（正是需求 8 的「岛大小约 30 格」），
        // 小湖仍由压力腿兜住 ⇒ 岛不会越出自家湖域、绝不进 §15.4 的环带（A 组由构造隔离）。
        // 本条腿只在压力腿已判"岛域内"（lakePressure < LAKE_ISLAND，全湖 ≈5% 的列）时才付代价。
        double k = kPress;
        // ⚠ 依赖声明（S5c 实测登记，别把这条腿当无损）：绝对腿吃的是 lakeCellCenterAt 的两次不动点
        // 反解，其收缩比按"幅度/波长 = 70/320 = 0.22"估，但正弦位移场的<b>最大</b>斜率是
        // 2π·70/320 = 1.37 > 1 ⇒ 两步后残差在陡区不保证 < 1 格。S5c 的只报量 minP 实测：32 座细扫湖里
        // 30 座的窗内最小压力 ≈1e-4（反解到位），2 座 = 1.9e-2 / 2.4e-2（干阈 2.4e-2 附近）
        // ⇒ 这两座的岛被本条腿裁到近空（判据 C1b 的 min=0 即此，且<b>基线穹顶同样为 0</b> ⇒ 非本片引入）。
        // 彻底免疫的改法是把本条腿写成 dC 式（k_abs = 1 − dC/本值，dC = 未归一的站距，与压力腿同一
        // 次 3×3 扫描、不依赖反解）；本片不改，登记给主代理（与 §15.6-1 的 S1 一并裁决）。
        // ── v1.20.41 P20 S5d（plan §28-A 路 (b)）：上面"彻底免疫的改法"<b>已照本式落地</b>，本条登记
        // 就此关闭；原文保留在上八行（同 §14/§15 覆盖 §11、§26 覆盖 §23-B 的处理法）。本腿现在与
        // 压力腿共用同一次 {@link #lakeStationDistances} 扫描（一次 diskAt + 一次 3×3），<b>零反解</b>
        // ⇒ 对不动点残差免疫；代价比改造前<b>更低</b>（旧式 = lakeCellCenterAt：diskAt ×3 + 3×3 扫描
        // + 两次不动点迭代）。等值线活在 warp 后的坐标空间里 ⇒ 与压力腿（同一空间的 dC/dN）同域可比。
        // ── P24-C3（v1.20.47）：本腿复用例级槽里由 lakeAt 那次扫描存下的 dC/dN，<b>不再重扫</b> ──
        // 改造前这里无条件再跑一次 lakeStationDistances（1 diskAt + 18 cellOffset + 9 sqrt），
        // 而同一列在上游 heightCore 的 lakeAt 调用里刚算过同一对 dC/dN（岛域列占湖水列
        // 0/4.0/25.4/1.2%，混合 ≈1.3–3%）。复用的取值与重算逐位相同（同 seed 同列纯函数），
        // 命中时只付一次 LAKE_MEMO.get() + 全键比较（远小于一次 disk+3×3）。
        // 槽未命中（如判据侧直调本函数、或该列 lakeAt 已被淘汰）则回退重扫 ⇒ 语义不变。
        double dC;
        final LakeSlot ls = LAKE_MEMO.get()[slotIndex(worldSeed, x, z, MEMO_MASK)];
        if (ls.valid && ls.seed == worldSeed && ls.x == x && ls.z == z && ls.distValid) {
            dC = ls.d0;
        } else {
            final double[] dd = LAKE_DIST_BUF.get();
            lakeStationDistances(worldSeed, x, z, dd);
            dC = dd[0];
        }
        {
            // P25 D3⑤(a)：站级半径抖动——R_eff = LAKE_ISLAND_RADIUS×(1±0.10·hash01(站键))，
            // 站键 = 未 warp 标称湖格（同岛恒同键，间距论证见 LAKE_ISLAND_RADIUS_JITTER）。
            // 与 (b) 腿同用本次扫描的 dC，零独立 warp/第二不动点。
            final int stCellX = (int) Math.floor(x / LAKE_INTERVAL + 0.5D);
            final int stCellZ = (int) Math.floor(z / LAKE_INTERVAL + 0.5D);
            final double uJit = 2.0D
                * hash01(GTSRWorldgenHash.cellSeed(worldSeed, stCellX, stCellZ, SALT_LAKE_ISLAND_RADIUS)) - 1.0D;
            final double rEff = LAKE_ISLAND_RADIUS * (1.0D + LAKE_ISLAND_RADIUS_JITTER * uJit);
            // P29-A2-R（主代理裁决①，岛腿 ridged 脊形）：kAbs = 1 − (dC + LO·g₁ + MID·g₂)/rEff，
            // g = s01((1−|n|−RIDGE_GATE_LO)/RIDGE_GATE_SPAN)——缎带腿同款 ridged 门（脊核 |n|≈0
            // 满门=深咬、带外 g=0 精确 ⇒ 脊间段半径=基线）。旧式两档退役路径：P26-B3 乘性
            // 1 − dC·(1+JIT·n)/rEff（0.53 可见折扣，"圆团"根因）→ P29-A2 plain 加性（环上
            // valueNoise 极值 ~0.4×(周长/λ) 个 ⇒ 瓣天花板 2-3，p29-a-readings §5）→ 本档
            // ridged 门形（脊=零交叉，瓣数由 λ 直接调度：λ124 ≈1 大脊 + λ20 ≈5 缺刻，几何账
            // 全文见 LAKE_ISLAND_EDGE_LO_AMP）。柱不变式：kAbs=0 域半径 ≥ 67.5−19 = 48.5 ≫ 17；
            // 噪声在岛域列（压力腿已过门）才求值 ⇒ 域外零成本；岛域 ⊂ 湖水区不变式不受影响
            // （pull ≥ 0 单侧内咬，kAbs 等值线族只内移不反号；n₁ 复用 SALT_LAKE_ISLAND_EDGE
            // 改档先例，n₂ 独立盐 SALT_LAKE_ISLAND_EDGE_MID）。
            final double nLo = GTSRWorldgenHash.valueNoise(
                worldSeed ^ SALT_LAKE_ISLAND_EDGE,
                x / LAKE_ISLAND_EDGE_LO_SCALE,
                z / LAKE_ISLAND_EDGE_LO_SCALE);
            final double tLo = (1.0D - Math.abs(nLo) - LAKE_ISLAND_EDGE_RIDGE_GATE_LO)
                / LAKE_ISLAND_EDGE_RIDGE_GATE_SPAN;
            final double tLoc = tLo < 0.0D ? 0.0D : (tLo > 1.0D ? 1.0D : tLo);
            final double ridgeLo = tLoc * tLoc * (3.0D - 2.0D * tLoc) * LAKE_ISLAND_EDGE_LO_AMP;
            final double nMid = GTSRWorldgenHash.valueNoise(
                worldSeed ^ SALT_LAKE_ISLAND_EDGE_MID,
                x / LAKE_ISLAND_EDGE_MID_SCALE,
                z / LAKE_ISLAND_EDGE_MID_SCALE);
            final double tMid = (1.0D - Math.abs(nMid) - LAKE_ISLAND_EDGE_RIDGE_GATE_LO)
                / LAKE_ISLAND_EDGE_RIDGE_GATE_SPAN;
            final double tMidc = tMid < 0.0D ? 0.0D : (tMid > 1.0D ? 1.0D : tMid);
            final double ridgeMid = tMidc * tMidc * (3.0D - 2.0D * tMidc) * LAKE_ISLAND_EDGE_MID_AMP;
            final double kAbs = 1.0D - (dC + ridgeLo + ridgeMid) / rEff;
            if (kAbs < k) {
                k = kAbs;
            }
        }
        if (k <= 0.0D) {
            // 被绝对腿裁出岛域（只可能发生在 LAKE_ISLAND_RADIUS 小于该湖的尺度半径时）
            return Double.NaN;
        }
        // v1.20.41 P20 S5c：k 先按 LAKE_ISLAND_PLATEAU 归一再截到 1 ⇒ k ≥ 平台半宽 = 满高岛面。
        // 本值 = 1.0 时本行恒等（kn == kPress），与改造前的纯穹顶逐位一致。
        final double kn = k >= LAKE_ISLAND_PLATEAU ? 1.0D : k / LAKE_ISLAND_PLATEAU;
        final double s = kn * kn * (3.0D - 2.0D * kn);
        return centerBed + (islandTop - centerBed) * s;
    }

    /**
     * 湖细胞中心（v1.20.41 P20 S5 新增，供 {@link #islandPillarAt} 与离线判据取"岛心/柱位基准"）：
     * 与 {@link #lakeAt0} <b>同一次</b> domain-warp + 同一次 3×3 Worley 扫描（不另立第二份形状），
     * 把<b>压力零点（真正的湖心）</b>的<b>世界坐标</b>与<b>格坐标</b>写进调用方给的 4 元缓冲。
     * <p>
     * <b>为什么不能直接返回最近湖站</b>（v1.20.41 P20 S5b 修的实质缺陷）：{@link #lakeAt0} 的输入坐标
     * 先被 {@code + disk × LAKE_WARP} 揉过一次 ⇒ 压力零点是方程 {@code X + W(X) = 湖站} 的解，
     * <b>不是</b>湖站本身；两者相差一个 warp 位移，S0a 实测该位移<b>中位 26.862 格、p90 44.820、
     * max 60.951</b>（{@code plan/tmp/p20-baseline/lake-model-fit.txt} §3）。而湖域
     * （{@code lakeAt < LAKE_ISLAND}）的实测半径只有 ≈11 格 ⇒ 拿湖站当岛心会把整座岛推到湖域之外，
     * {@link #islandPillarAt} 的域门（同一条 {@code lakeAt < LAKE_ISLAND}）随之恒假 ⇒
     * <b>大多数湖一根柱都写不出来</b>（S5b 判据实测：逐湖柱连通分量数中位 0、"5 柱全中"仅 9.38%）。
     * 修法 = 对 {@code X = 湖站 − W(X)} 做两次不动点迭代（P23 R1 档：{@code |W| ≤ LAKE_WARP = 100}、
     * 位移场波长 {@code LAKE_WARP_SCALE = 700} ⇒ 最大斜率 2π×100/700 ≈ 0.898 &lt; 1，每步收缩比
     * ≈ 100/700 ≪ 1 ⇒ 两步后残差 &lt; 1 格；v1.20.45 档 70/320 的旧读数见版本树；P25 起位移场
     * = 主盘+副盘合成：|W| ≤ 97、斜率上界 0.985 &lt; 1（历史账）；<b>P32 起降幅 30/5 ⇒ |W| ≤ 35、
     * 斜率上界 0.362 &lt; 1（现账，见 {@link #LAKE_WARP}）</b>），
     * 仍然只吃同一份 {@code diskAt} 表与同一条 {@code cellOffset} ⇒ 零新增真值。
     *
     * @param out 长度 ≥4 的缓冲：{@code [0]=中心世界 X、[1]=中心世界 Z、[2]=格 gx、[3]=格 gz}；
     *            失败标记 {@code out[2] = Integer.MIN_VALUE}（P23 R1 起 trunk 门已删、全域站格
     *            均有湖心零点，本标记仅作防御性出口保留——{@link #nearestActiveLakeCenter} 的
     *            跳过臂随之失活）
     */
    public static void lakeCellCenterAt(long worldSeed, int x, int z, double[] out) {
        out[2] = Integer.MIN_VALUE;
        final double[] buf = diskBuffer();
        diskAt(worldSeed, SLOT_DISK_LAKE_WARP, LAKE_WARP_SCALE, x, z, buf);
        // P25 D3②：与 lakeStationDistances 同一"主盘+副盘同点相加"揉动（同一份形状真值——
        // 湖心反解若只用主盘，压力零点会偏出 ≤12 格，柱位/传送湖心与湖域门错位）。
        final double wx = buf[0] * LAKE_WARP;
        final double wz = buf[1] * LAKE_WARP;
        diskAt(worldSeed, SLOT_DISK_LAKE_SUB, LAKE_WARP_SUB_SCALE, x, z, buf);
        final double px = x + wx + buf[0] * LAKE_WARP_SUB;
        final double pz = z + wz + buf[1] * LAKE_WARP_SUB;
        final int cellX = (int) Math.floor(px / LAKE_INTERVAL + 0.5D);
        final int cellZ = (int) Math.floor(pz / LAKE_INTERVAL + 0.5D);
        double best = Double.POSITIVE_INFINITY;
        double bestEx = 0.0D;
        double bestEz = 0.0D;
        int bestGx = 0;
        int bestGz = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int gx = cellX + dx;
                final int gz = cellZ + dz;
                final double sx = gx * LAKE_INTERVAL + cellOffset(worldSeed, gx, gz, SALT_LAKE_CELL_X, LAKE_INTERVAL);
                final double sz = gz * LAKE_INTERVAL + cellOffset(worldSeed, gx, gz, SALT_LAKE_CELL_Z, LAKE_INTERVAL);
                final double ddx = sx - px;
                final double ddz = sz - pz;
                final double d = Math.sqrt(ddx * ddx + ddz * ddz);
                if (d < best) {
                    best = d;
                    bestEx = sx;
                    bestEz = sz;
                    bestGx = gx;
                    bestGz = gz;
                }
            }
        }
        // 反解压力零点 X + W(X) = 湖站：两次不动点（P32 两盘合成斜率上界 0.362 < 1、有效收缩比
        // ≈ ΣA/λ = 30/700+5/340 ≈ 0.057 ⇒ 残差 ≪ 1 格；P25 档 0.985/0.15 与 P23 单盘账
        // 0.898/0.143 见版本树）。
        // buf 是 DISK_BUF 单缓冲，取值即用于是本行，不跨下次 diskAt 持有；两盘分量同样先拷后取。
        double centerX = bestEx;
        double centerZ = bestEz;
        for (int it = 0; it < 2; it++) {
            diskAt(
                worldSeed,
                SLOT_DISK_LAKE_WARP,
                LAKE_WARP_SCALE,
                (int) Math.round(centerX),
                (int) Math.round(centerZ),
                buf);
            final double fx = buf[0] * LAKE_WARP;
            final double fz = buf[1] * LAKE_WARP;
            diskAt(
                worldSeed,
                SLOT_DISK_LAKE_SUB,
                LAKE_WARP_SUB_SCALE,
                (int) Math.round(centerX),
                (int) Math.round(centerZ),
                buf);
            centerX = bestEx - fx - buf[0] * LAKE_WARP_SUB;
            centerZ = bestEz - fz - buf[1] * LAKE_WARP_SUB;
        }
        out[0] = centerX;
        out[1] = centerZ;
        out[2] = bestGx;
        out[3] = bestGz;
    }

    // ═════════ S5 传送定位出口（P23 plan §2 S5，纯新增段；零 Chunk 读、纯函数、不加新常量—— ═════════
    // 枚举窗/射线/步长全为字面量；湖形只经 lakeAt / lakeCellCenterAt / LAKE_* 的公开函数表面，
    // 批2 S2 重写湖区（去 trunk 门、warp 与半径改档）后本两出口自动跟随新湖形（签名与语义表面不变约定）。

    /**
     * <b>最近活湖湖心</b>（S5，tpdim 传送定位通道）：以查询列 (x,z) 所在 {@link #LAKE_INTERVAL}
     * 站格为心、≤3 环（7×7 站）枚举每站的 {@link #lakeCellCenterAt} 压力零点，活湖门与
     * {@code MegaTreeAnchors.anchorAt} 同一条腿——湖心列 {@code lakeAt < }{@link #LAKE_ISLAND}
     * （死湖无岛穹无锚，同一域门），返回欧氏最近活湖心。
     * <p>
     * <b>站内代表列 = 该站标称格点</b>（{@code gx·LAKE_INTERVAL}）：站锚定偏移 ≤ 0.35×间隔 = 1050、
     * 合成 warp ≤ {@link #LAKE_WARP}+{@link #LAKE_WARP_SUB} = 97 ⇒ 自家站到格点 ≤ 1147 &lt; 他站
     * ≥ 3000−1050−97 = 1853 ⇒ 格点查询恒命中自家站（{@code MegaTreeAnchors.ENUM_STEP} 采样论证
     * 同一口径；P25 前的 420/490/710 读数是 1200 档历史值，随 D1 重定标作废），且湖心反解只依赖
     * 获胜站坐标 ⇒ 同站恒得同一湖心，无需去重（P32 起 warp ΣA=35 ⇒ 该账 ≤ 1085 &lt; 1915，
     * 论证更松）。<b>P23 R1（v1.20.46 批2 S2）已兑现</b>：
     * {@link #lakeCellCenterAt} 的 trunk 门删除 ⇒ 原"主干带外站跳过"臂失活、全域站格自动生效
     * （死湖门仍裁 D_MIN 淘汰与 {@code lakeAt ≥ LAKE_ISLAND} 的站）。非热路径（指令级），
     * 49 站全扫取最小欧氏距离，确定性。
     *
     * @param outXY 长度 ≥2 的输出：{@code [0]=湖心 X、[1]=湖心 Z}（世界列坐标，四舍五入）
     * @return {@code true} = outXY 已写最近活湖心；{@code false} = 7×7 站内无活湖（不承诺 out 内容）
     */
    public static boolean nearestActiveLakeCenter(long worldSeed, int x, int z, int[] outXY) {
        return nearestActiveLake(worldSeed, x, z, outXY, null);
    }

    /**
     * <b>最近可安全传送的遗忘之湖</b>（P25 S5b）：仍在 {@link #nearestActiveLakeCenter} 的 7×7 站窗内
     * 按湖心欧氏距离取最近，但额外要求该湖经 {@link #sanzuArrivalColumn} 能找到<b>群系平面内的干滩列</b>；
     * 无干滩湖直接跳过。P25 D2 的 R=2 侵蚀门会让少数活湖出现「几何干列存在、sanzu 干列为 0」
     * （穷举证：一例压力滩带 32769 列 / 几何干列 3081 列 / sanzu 干列 0），因此传送不能把
     * 「最近活湖」与「最近可落脚湖」混成一个真值，更不能回退湿滩。
     *
     * @param outCenterXY  长度 ≥2：命中湖心
     * @param outArrivalXZ 长度 ≥2：同湖安全干滩列
     * @return {@code true} = 两个输出均已写；{@code false} = 7×7 站窗内无安全干滩湖
     */
    public static boolean nearestSanzuArrival(long worldSeed, int x, int z, int[] outCenterXY, int[] outArrivalXZ) {
        return nearestActiveLake(worldSeed, x, z, outCenterXY, outArrivalXZ);
    }

    /** outArrivalXZ == null 时保持原「最近活湖」语义；非 null 时只接纳有安全干滩的湖。 */
    private static boolean nearestActiveLake(long worldSeed, int x, int z, int[] outCenterXY, int[] outArrivalXZ) {
        final double[] c = new double[4];
        final int baseGx = (int) Math.floor(x / LAKE_INTERVAL + 0.5D);
        final int baseGz = (int) Math.floor(z / LAKE_INTERVAL + 0.5D);
        boolean found = false;
        double bestD = Double.POSITIVE_INFINITY;
        int bestX = 0;
        int bestZ = 0;
        int bestArrivalX = 0;
        int bestArrivalZ = 0;
        for (int gz = baseGz - 3; gz <= baseGz + 3; gz++) {
            for (int gx = baseGx - 3; gx <= baseGx + 3; gx++) {
                // 站标称格点（warp 前整数列）：自家站恒最近，见方法注释的间距论证
                final int qx = (int) Math.round(gx * LAKE_INTERVAL);
                final int qz = (int) Math.round(gz * LAKE_INTERVAL);
                lakeCellCenterAt(worldSeed, qx, qz, c);
                if ((int) c[2] == Integer.MIN_VALUE) {
                    continue; // 防御臂（P23 R1 去 trunk 门后理论不可达；见 lakeCellCenterAt 注释）
                }
                final int ax = (int) Math.round(c[0]);
                final int az = (int) Math.round(c[1]);
                if (lakeAt(worldSeed, ax, az) >= LAKE_ISLAND) {
                    continue; // 死湖（与 MegaTreeAnchors.anchorAt 同一条活湖门）
                }
                final double ddx = ax - x;
                final double ddz = az - z;
                final double d = ddx * ddx + ddz * ddz;
                if (d >= bestD) {
                    continue;
                }
                final int[] arrival = outArrivalXZ == null ? null : sanzuArrivalColumn(worldSeed, ax, az);
                if (outArrivalXZ != null && arrival == null) {
                    continue; // 活湖但无群系内干滩：传送通道跳过，几何/巨树通道仍承认该湖
                }
                bestD = d;
                bestX = ax;
                bestZ = az;
                if (arrival != null) {
                    bestArrivalX = arrival[0];
                    bestArrivalZ = arrival[1];
                }
                found = true;
            }
        }
        if (!found) {
            return false;
        }
        outCenterXY[0] = bestX;
        outCenterXY[1] = bestZ;
        if (outArrivalXZ != null) {
            outArrivalXZ[0] = bestArrivalX;
            outArrivalXZ[1] = bestArrivalZ;
        }
        return true;
    }

    /**
     * <b>sanzu 传送落点列</b>（S5，滩带<b>干</b>列——避岛心树干/岛底柱，且不落湖水面）：自湖心
     * (cx,cz) 沿 <b>32 条 11.25° 均分射线</b>向外步进（步长 4 格、行程上限 600 格 &lt;
     * {@link #LAKE_INTERVAL}/2，全程留在本站湖域内），在每条射线的滩带段（压力 ∈
     * [{@link #LAKE_ISLAND}, {@link #LAKE_SHORE})）里取<b>第一个群系平面内干滩列</b>，最终返回
     * 距湖心最近者。P25 D2 侵蚀门后的实测反例：8/16 射线会漏掉一座仅在约 34° 方向可达的干滩，
     * 32 射线命中；另一座湖即使 128 射线、步长 1 仍无干滩，方窗穷举亦为 0。因此本出口<b>不再</b>
     * 返回湿滩兜底：找不到安全列就返回 {@code null}，由 {@link #nearestSanzuArrival} 跳过该湖。
     * <p>
     * <b>干列口径 = {@link ProsperityTerrainProfile#SEA_LEVEL}{@code − 1}</b>（= 湖水面/最高水格
     * y = 67，<b>生成侧同源字面量</b>，见 {@code ChunkProviderProsperityRuins.fillSanzuLakes} 的
     * 「{@code h < SEA_LEVEL} 才从 {@code h+1} 灌水到 {@code SEA_LEVEL−1}」）：该门与"本列是否
     * 被灌了湖水面"<b>逐列等价</b>（{@code h ≥ 67 ⇒} 灌水区间 [{@code h+1}, 67] 为空 ⇒ 列顶上方无
     * 水格）。<b>不能只取 SEA_LEVEL=68 当干门下沿</b>：步长 4 会跳过 h==68 的那一环（实测 h 67→69
     * 直跳）。故干门取<b>闭区间 [67, 68+{@link #SANZU_DRY_BEACH_RISE}] = [67,69]</b>（P26-B3 D2·b1
     * 随滩缘腿 +1）：下界保证无水格、上界复用 sanzu 平面的 h 门上沿（h=69 干滩列 P26-B3 起在
     * 遗忘之湖滩带平面内）⇒ 落点恒同时满足「无水格」与「在遗忘之湖滩带平面内」。
     * <p>
     * <b>为什么必须跳过滩带内半缘</b>：P24-D 湖床剖面重设计（{@link #LAKE_BED_PLATEAU} 0.55→0.790）
     * 把深盆平台推到岛缘之外 ⇒ 滩带 0.15→0.23 段是 h≈40–41 的深水（P25 档：滩带
     * {@link #LAKE_ISLAND}→{@link #LAKE_WATER_LEVEL} 段，0.055→0.081），滩带<b>内缘</b>首个命中恒为深水
     * （旧版"首个 ∈ 滩带"的缺陷：落点 h=40~41、距湖心 64–188 格，指令层 {@code columnTopSafeY} 的
     * ≤16 列螺旋够不到岸 ⇒ 兜底 y=67 把玩家放进湖水面）。
     * <p>
     * 可达性：活湖湖心必向外穿越压力滩带，但 P25 D2 的侵蚀群系门不保证每座湖仍有群系内干滩，
     * 故本方法允许返回 {@code null}；传送调用方必须经 {@link #nearestSanzuArrival} 在 7×7 站窗内
     * 跳过无安全落点湖。参数全字面量、不加新常量；湖形变更自动跟随。<b>纯函数、零 Chunk 读</b>
     * （只经 {@link #lakeAt}、{@link #isSanzuColumn} 与
     * {@link ProsperityTerrainProfile#heightAt} 三个纯函数出口）。
     *
     * @param cx 湖心世界列 X（{@link #nearestActiveLakeCenter} 的输出）
     * @param cz 湖心世界列 Z
     * @return {@code {x, z}} 群系平面内干滩列；{@code null} = 本湖无安全落点
     */
    public static int[] sanzuArrivalColumn(long worldSeed, int cx, int cz) {
        int dryX = 0;
        int dryZ = 0;
        double dryBest = Double.POSITIVE_INFINITY;
        for (int dir = 0; dir < 32; dir++) {
            final double ux = Math.cos(dir * Math.PI / 16.0D);
            final double uz = Math.sin(dir * Math.PI / 16.0D);
            // 步长 4：滩带压力宽度（岛域外缘到湖岸）数十格，粗扫不跳带；上限 600 格 ≪ 半站距
            // （P25 D1 档 = 1500），足以覆盖滩带外缘（水径 ≈200 + ΣA 97 + 噪声腿 ≈17）+ 容差
            for (int step = 4; step <= 600; step += 4) {
                final int px = cx + (int) Math.round(ux * step);
                final int pz = cz + (int) Math.round(uz * step);
                final double p = lakeAt(worldSeed, px, pz);
                if (p >= LAKE_SHORE) {
                    break; // 越过湖岸（滩带外缘）⇒ 本射线收束（岛心向内段 p < LAKE_ISLAND 继续外推）
                }
                if (p < LAKE_ISLAND) {
                    continue; // 仍在岛域（含湖心），未进滩带
                }
                final double ddx = px - cx;
                final double ddz = pz - cz;
                final double d = ddx * ddx + ddz * ddz;
                final int h = ProsperityTerrainProfile.heightAt(worldSeed, px, pz);
                if (h >= ProsperityTerrainProfile.SEA_LEVEL - 1
                    && h <= ProsperityTerrainProfile.SEA_LEVEL + SANZU_DRY_BEACH_RISE) {
                    // 优先级 1：本射线首个<b>群系平面内</b>的干滩列即止。上界 SEA+RISE = sanzu 平面
                    // h 门上沿（P26-B3 D2·b1 随滩缘腿 +1：h=69 干滩列首次入平面 ⇒ "整湖无干滩"
                    // 情形直接缓解）；但侵蚀门会把滩带外缘锯齿列蚀出群系平面——h 门通过 ≠
                    // isSanzuColumn（Tpdim 钉16e 实测 11/15 落点被蚀）⇒ 候选必须过全谓词；
                    // 被蚀列不取也不 break（带内侧仍是平面内干滩，继续外推）。
                    if (isSanzuColumn(worldSeed, px, pz)) {
                        if (d < dryBest) {
                            dryBest = d;
                            dryX = px;
                            dryZ = pz;
                        }
                        break;
                    }
                }
            }
        }
        return dryBest != Double.POSITIVE_INFINITY ? new int[] { dryX, dryZ } : null;
    }

    /**
     * <b>岛底柱列谓词</b>（plan §15.5，需求 8「岛和底部是有柱子相连的」；v1.20.41 P20 S5 新增）：
     * 本列是否落在某根岛的底柱截面上。
     * <p>
     * <b>柱位集合</b> = 1 根中心柱 + 8 根环柱（{@link #LAKE_PILLAR_COUNT} = 9，P23 R1 随岛放大
     * 的等比档），环柱极角 45° 均分（0°,45°,…,315°）、半径 {@link #LAKE_PILLAR_RING} = 13 加
     * {@link #LAKE_PILLAR_JITTER} = ±2 的确定性抖动（抖动脉冲 = {@link #cellOffset} 同一条
     * splitmix 哈希，键 = 湖格 × 柱序号 ⇒ <b>同一座湖的柱位对全部列一致</b>，不会逐列各抖各的
     * 把截面抖散）；截面 {@code 2·LAKE_PILLAR_HALF_SECTION + 1 = 5×5}。
     * <p>
     * <b>为什么必须是逐列纯函数而不是结构通道</b>（§13 C5 / §15.5 表末行）：柱高
     * {@code y ∈ [LAKE_PILLAR_FLOOR_Y, LAKE_PILLAR_TOP_Y]} = 32 格 &gt;
     * {@code ChunkSpans.MAX_SLICE_HEIGHT = 12}，字符盘模板路承不住；而本谓词只判"本列是不是柱"，
     * 每一列由<b>它自己所在 chunk</b> 的那一趟 {@code fillSanzuLakes} 写满同一个 y 区间 ⇒
     * 跨 chunk 天然无缝，且 3×3 截面骑在 chunk 边界上也只是"两边各写自己那几列"，
     * 不构成任何跨界写（{@code ChunkClampedSink} 的丢弃计数应为 0，见 S5 回执）。
     * <p>
     * 域门 {@code lakeAt < LAKE_ISLAND}：只可能给岛域内的列 ⇒ <b>P23 R1 档：环柱半径 13 + 抖 2
     * + 半宽 2 = 17 &lt; 岛干半径 40</b>（v1.20.45 档为 8 &lt; 15.96，S5c 复核），柱不越岛缘；
     * 柱位落在平台段（{@code k ≥ 0.60}）⇒ 柱顶正对满高岛面 72。
     */
    public static boolean islandPillarAt(long worldSeed, int x, int z) {
        if (lakeAt(worldSeed, x, z) >= LAKE_ISLAND) {
            return false;
        }
        final double[] c = LAKE_CENTER_BUF.get();
        lakeCellCenterAt(worldSeed, x, z, c);
        if (c[2] == Integer.MIN_VALUE) {
            return false;
        }
        final int gx = (int) c[2];
        final int gz = (int) c[3];
        for (int p = 0; p < LAKE_PILLAR_COUNT; p++) {
            // 中心柱（p=0）不抖环半径（抖了就不是"岛心正下方那根"了），只抖一个格内相位；
            // P23 R1：8 根环柱 45° 均分（p=1..8 ⇒ 极角 0°,45°,…,315°，原 4 根斜向环的放大档）
            final double radius = p == 0 ? 0.0D : LAKE_PILLAR_RING + pillarJitter(worldSeed, gx, gz, p, 0L);
            final double angle = p == 0 ? 0.0D : Math.PI / 4.0D * (p - 1);
            final int px = (int) Math.round(c[0] + Math.cos(angle) * radius);
            final int pz = (int) Math.round(c[1] + Math.sin(angle) * radius);
            if (Math.abs(x - px) <= LAKE_PILLAR_HALF_SECTION && Math.abs(z - pz) <= LAKE_PILLAR_HALF_SECTION) {
                return true;
            }
        }
        return false;
    }

    /**
     * 柱位抖动（格，∈ {−{@link #LAKE_PILLAR_JITTER}, …, +{@link #LAKE_PILLAR_JITTER}} 的整数）：
     * 复用 {@link #cellOffset} 的同一条 splitmix 哈希，把 interval 归一到 ±1 再取整——
     * 柱位是<b>湖格级</b>常量（同一 (gx,gz,柱序,轴) 永远同一抖值），不是列级噪声。
     */
    private static int pillarJitter(long worldSeed, int gx, int gz, int pillarIndex, long axisSalt) {
        final double raw = cellOffset(
            worldSeed,
            gx * LAKE_PILLAR_COUNT + pillarIndex,
            gz * 2 + (int) axisSalt,
            SALT_LAKE_PILLAR,
            1.0D / CELL_JITTER);
        return (int) Math.max(-LAKE_PILLAR_JITTER, Math.min(LAKE_PILLAR_JITTER, Math.round(raw)));
    }

    /**
     * <b>湖滨湿带列谓词</b>（plan §15.4 第三条形态「湿带：水陆之间一条<b>不积水</b>的半湿表层带」；
     * v1.20.41 P20 S5 新增）：本列是否是湖滨带里贴水的那一段。
     * <p>
     * 三门：① 在湖滨带内（{@code WATER ≤ lakeAt < LAKE_SHORE}，湖水区本身已有真水，不算湿带）；
     * ② 地表在水面之<b>上</b>或正好齐平（{@code h ≤ SEA_LEVEL} ⇒ 高于水面 1 格以内，不会有一圈
     * 干台地插在中间）；③ 地表距水面 ≤ {@link #LAKE_WET_BAND_DROP} 格（{@code h ≥ SEA_LEVEL − 2}
     * ⇒ 太深的沟是给水的，不该铺湿料）。
     * <p>
     * <b>本谓词只回答"是不是湿带"，不写任何方块</b>：表层的实际改派在
     * {@code ChunkProviderProsperityRuins} 的 {@code SurfaceTopSelector} 里做，而该 selector 是
     * <b>包一层</b> P20 S1 的 {@code GTSRSurfaceBorderBand}（先原样委托群系交界混合带、只在湿带列
     * 改派同一名册内的湿料），故表层身份仍是<b>单一真值</b>，见那里的类注释契约段。
     */
    public static boolean lakeWetBandAt(long worldSeed, int x, int z) {
        final double lake = lakeAt(worldSeed, x, z);
        if (lake < LAKE_WATER_LEVEL || lake >= LAKE_SHORE) {
            return false;
        }
        final int sea = ProsperityTerrainProfile.SEA_LEVEL;
        final int h = ProsperityTerrainProfile.heightAt(worldSeed, x, z);
        return h <= sea && h >= sea - LAKE_WET_BAND_DROP;
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

    // ═══ v1.20.49 P26-B4 ⑦：沼泽浅水水网区域门（λ151；微池压力腿的域内加密）═══

    /**
     * 水网域场盐（波长 {@link #SWAMP_NET_SCALE}=151；151 % 16 = 7 ✔ H-1）。取 RVF 盐段
     * {@code 0x5249F1xx} 的下一未用值（上一已用 = 湖压二倍频 {@code 0x5249F11B}；退役盐
     * {@code 0x5249F112} 不回收不复用）。
     */
    public static final long SALT_SWAMP_NET = 0x5249F11CL;
    /** 水网域波长（区域直径 ~60-120 格）。 */
    public static final double SWAMP_NET_SCALE = 151.0D;
    /** 水网域门下檐/带宽（{@code s01((n−0.45)/0.20)} ⇒ 覆盖 ~10-15% 沼泽腹地）。 */
    public static final double SWAMP_NET_GATE_LO = 0.45D;
    /** 水网域门带宽。 */
    public static final double SWAMP_NET_GATE_SPAN = 0.20D;
    /**
     * 域内压力腿加密系数：返回压力除以 {@code (1 + 本值·netGate)} ⇒ 水径 8-16 → <b>14-26 格</b>。
     * <b>取值由缩样读数钉死（v1.20.49 P26-B4）</b>：域门是缓入软门，水面面积比对 B <b>凸增长</b>
     * （B=0.6 ⇒ 半径比 1.20、B=2.0 ⇒ 2.13，两点实测见 {@code plan/tmp/p26-b4-readings.md}）
     * ⇒ 取 <b>B=1.3（两点插值）⇒ 半径比 ~1.7 ⇒ 水径 ~14-27 落 14-26 目标带</b>。
     * <b>域外 netGate 精确 0 ⇒ 除数 (1+0.0) = 1.0，IEEE 除法恒等 ⇒ 微池读数与改造前逐位相同</b>
     * （82 纪律：不新增置水真值——地形侧压低（heightCore 微池段）、回填侧
     * {@code fillSwampPools}/SwampFieldGrid 的 nominal 微池腿与 {@code GTSRRiverPlacer.neighborBarrier}
     * 微池腿都读本方法 ⇒ 同一谓词自动跟随，N8 钳制零改动）。水深维持 1-2 层
     * （{@link #SWAMP_POOL_WATER_LEVEL}=0.055 不动）；域内不压制泥丘场 ⇒ 泥炭丘天然成草甸岛。
     */
    public static final double SWAMP_NET_PRESSURE_BOOST = 1.3D;

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

    /**
     * swampLakeAt 原始求值体（P19 U8 起由 {@link #swampLakeAt} 的列级 memo 包裹；roster≠3 常量路不进 memo）。
     * <p>
     * ═══ P25（D4②）两让位腿（微池是最浅层水体，让位湖/滩与干河床）═══
     * <ol>
     * <li><b>湖腿</b>：湖/滩压力域（{@code lakeAt < sanzuBiomeShoreAt}）内不起微池。⚠ 不调
     * {@link #isSanzuColumn} 全谓词：其 h 腿 → {@code heightAt} → heightCore →
     * {@link #swampLakeAt}（本方法）构成<b>直接递归环</b>（roster==3 必环）⇒ 取其<b>压力腿</b>
     * 同判（lakeAt/sanzuBiomeShoreAt 皆不触 heightAt）；h 腿的"地表贴水"语义由微池自身的
     * 水位面（pool−1）与滩带认领兜住，不改变"滩带归湖"的几何结论（偏离登记见本片回执）。</li>
     * <li><b>干床让位腿</b>：{@code −strengthAt ≥ }{@link #DRY_RIVERBED_CORE}（D6 共享常量——
     * 一切压力域阈值引用 RVF 常量，无第二真值）⇒ 不起微池——枯竭语义下干河床核是"床"不是"池"。
     * strengthAt 的 P25 湖腿使湖/滩内 s ≡ 0 ⇒ 本腿在湖/滩内恒不触发（两腿天然互斥）。</li>
     * </ol>
     * 两腿都在 Voronoi 扫描<b>之前</b>（让位列零扫描成本）。
     */
    private static double swampLakeAt0(long worldSeed, int x, int z, int rosterIndex) {
        if (rosterIndex != 3) {
            return NO_SWAMP_POOL;
        }
        // P25 D4② 湖腿（压力腿形式，环规避——见方法 javadoc）。
        if (lakeAt(worldSeed, x, z) < sanzuBiomeShoreAt(worldSeed, x, z)) {
            return NO_SWAMP_POOL;
        }
        // P25 D4② 干床让位腿（DRY_RIVERBED_CORE 与 D6 isDryRiverColumn 共享常量）。
        if (-strengthAt(worldSeed, x, z, rosterIndex) >= DRY_RIVERBED_CORE) {
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
        // ═══ v1.20.49 P26-B4 ⑦：浅水水网区域门——压力腿域内加密（λ151 软门，域外逐位 0）═══
        // 净压力 = dC/dN ÷ (1 + SWAMP_NET_PRESSURE_BOOST·netGate)：域内等效阈值放大 ⇒ 水径 8-16 →
        // 14-26、池心间干廊收窄成水网；域外 netGate=0 ⇒ 除数 1.0、IEEE 除法恒等（x/1.0 == x 逐位），
        // 微池读数不变。让位腿（湖/干床）在 Voronoi 扫描之前 return ⇒ 水网域构造性不进湖/滩。
        final double nn = GTSRWorldgenHash
            .valueNoise(worldSeed ^ SALT_SWAMP_NET, x / SWAMP_NET_SCALE, z / SWAMP_NET_SCALE);
        final double nt = Math.max(0.0D, Math.min(1.0D, (nn - SWAMP_NET_GATE_LO) / SWAMP_NET_GATE_SPAN));
        final double netGate = nt * nt * (3.0D - 2.0D * nt);
        return dC / dN / (1.0D + SWAMP_NET_PRESSURE_BOOST * netGate);
    }

    /**
     * <b>微池置水列谓词</b>（v1.20.43 P22 版 B O1a，{@link #swampLakeAt} 的布尔单一出口，
     * {@code submergedAt} 先例同款）：{@code swampLakeAt < SWAMP_POOL_WATER_LEVEL} 的逐字包装——
     * 非沼泽 roster 恒 {@link #NO_SWAMP_POOL}（比较式天然 false，roster 门短路语义保持）。
     * 消费面同一真值（O1a 出口口径；原 ProsperityCaveField 水柱保护腿已随洞穴移除（P23 S1）
     * 退役）：{@code GTSRRiverPlacer.neighborBarrier} 微池腿、{@code ChunkProviderProsperityRuins.SwampFieldGrid}
     * nominal 门的微池腿。
     */
    public static boolean swampPoolWaterAt(long worldSeed, int x, int z, int rosterIndex) {
        return swampLakeAt(worldSeed, x, z, rosterIndex) < SWAMP_POOL_WATER_LEVEL;
    }

    // ═════════════════ P25 D7：残潭退役 + 干河床谓词（D6）═════════════════

    /**
     * <b>沼泽河床残潭场已整体退役（P25 用户裁定）</b>：{@code swampRiverPoolAt}/
     * {@code swampRiverPoolColumnAt} 两方法与专属常量族（SWAMP_RIVER_POOL_SCALE/BAND_CENTER/
     * BAND_WIDTH/DIG_BASE/DIG_SPAN/FILL_TOP）本片删除。<b>引入史</b>：v1.20.42（P22 A1b），
     * 机制 = 沼泽河床枯竭后在床面残留断续下沉嵌入式水潭（六腿软门 + 潭底下挖 + 潭置水，
     * 历史校准读数见版本树）。<b>退役理由（P25 用户裁定）</b>：「小湖泊不上枯竭河流」——
     * 枯竭河床的残水语义改由干河床谓词 {@link #isDryRiverColumn}（D6）承载；微池/三档两水体
     * 通道不受影响（swampLakeAt0 的 P25 让位腿另立，见该处）。盐 {@code 0x5249F112L} 已随
     * 删除释放、不回收不复用（见其原位登记）。<b>消费面摘除跨片</b>：本类内零残留；PTP 潭底
     * 下挖支路本片已删；PlacementGate.dryColumnAt 腿④、ChunkProviderProsperityRuins
     * （SwampFieldGrid fixed 潭腿）、GTSRRiverPlacer（潭置水支路）、GTSRCommand.
     * populateWaterColumnAt（潭腿）、TerrainVariants javadoc 引用——归 S3 片收口（file:line
     * 清单见本片回执）。
     */

    /**
     * 干河床<b>核阈</b>（s = −strengthAt 域，P25 D6 新增）：s ≥ 本值（+噪声抖动）= 干河床核。
     * 与 {@link #WET_MIN}=0.78 的关系：干床核 0.40 &lt; 湿核 0.78 ⇒ 干床域 ⊂ 谷域（干床列先过
     * 谷、再判干湿）；P23 R1 后全域置水死（{@link #wetAt} 恒 false），"河"的地表语义只剩本
     * 谓词圈定的干床。
     */
    public static final double DRY_RIVERBED_CORE = 0.40D;

    /**
     * 干河床核阈的<b>噪声抖幅</b>（s 域，P25 D6 新增；<b>P25 S5 复测后 0.20 → 0.34 按实测梯度重导</b>）：
     * 阈 = {@link #DRY_RIVERBED_CORE} + 本值×n01（n01 ∈ [0,1)，λ{@link #DRY_RIVERBED_NOISE_SCALE}）。
     * <b>换算式（格数口径，javadoc 钉）</b>：初版按全剖面平均梯度估 ≈42 格/单位 s ⇒ 0.20 跨度
     * ≈8.4 格；S5 实测（RMC J2，128 有效 transect）阈带 [0.40,0.60) 段<b>局部</b>梯度 ≈
     * 7.0 格 / 0.20 单位 = <b>35 格/单位</b>（sqrt 剖面中段比全剖面平均陡）⇒ 中位带宽仅 7.0 &lt; 目标
     * [8,16]。按同一实测梯度反解目标中位 12 格 ⇒ <b>本值 = 12/35 ≈ 0.34</b> ⇒ 预期中位 ≈11.9 格
     * （等值线在阈带内蜿蜒，非硬边）；越界按本式重导。
     */
    public static final double DRY_RIVERBED_JITTER = 0.34D;

    /**
     * 干河床阈抖噪声波长（格，P25 D6 新增）：<b>选型域 [29,53] 且 %16≠0</b>（同
     * {@link #CUT_NOISE_SCALE}=33 的 chunk 对齐条纹坑纪律；已退役的残潭波长 29 亦同纪律）；
     * 37 % 16 = 5 ✔——斑块特征长 ≈ λ/2 ≈ 18.5 格，与过渡滩宽（8~16 格）同阶。
     */
    public static final double DRY_RIVERBED_NOISE_SCALE = 37.0D;

    /**
     * 干河床阈抖噪声盐（P25 D6 新增）：取本类盐段尾 {@code …115L}（…114L 已被
     * {@link #SALT_SANZU_BIOME_SHORE} 占用；…112L 随残潭退役空闲、不回收）。
     */
    public static final long SALT_DRY_RIVER = 0x5249F115L;

    /**
     * <b>干河床列谓词</b>（P25 D6 新增；<b>批间冻结接口</b>——S2/S3/S4/S5 按此签名消费，
     * 不得改名改参）：
     *
     * <pre>
     * {@code
     * isDryRiverColumn(seed, x, z) ⇔ −strengthAt ≥ DRY_RIVERBED_CORE
     *                                + DRY_RIVERBED_JITTER × n01(seed^SALT_DRY_RIVER,
     *                                    x/DRY_RIVERBED_NOISE_SCALE, z/DRY_RIVERBED_NOISE_SCALE)
     *                              ∧ !isSanzuColumn(seed, x, z)
     * }
     * </pre>
     *
     * （n01 = valueNoise 的 [0,1) 归一，{@link #sanzuBiomeShoreAt} 同款口径）。语义腿：
     * <ul>
     * <li><b>湖让位</b>：{@link #strengthAt} 的 P25 湖腿使湖+滩内 s ≡ 0 &lt; 0.40 ⇒ 谓词在
     * 湖/滩内<b>天然不触发</b>；显式 {@code !isSanzuColumn} 腿是双保险，兼裁滩缘（噪声单边
     * 腿扩出 {@link #LAKE_SHORE} 之外）与 h 腿贴水的滩列。</li>
     * <li><b>过渡带</b>：阈带 [0.40, 0.60) × 42 格/单位 ⇒ 边缘过渡滩 ≈ 8~16 格（换算式见
     * {@link #DRY_RIVERBED_JITTER}）。</li>
     * <li><b>求值序</b>：先廉价 s 核阈短路（绝大多数列一次 memo 化 strengthAt 即出），再
     * isSanzuColumn（含 5×5 粗格净空门），最后噪声项。</li>
     * <li><b>消费面共享常量</b>：{@link #swampLakeAt0} 的微池干床让位腿引用同一
     * {@link #DRY_RIVERBED_CORE}（压力域阈值一律引 RVF 常量，无第二真值）。</li>
     * </ul>
     * <b>纯函数</b>、零 {@code net.minecraft} 依赖；3 参形态（无 rosterIndex）取默认档
     * widthScale=1.0（与 PlacementGate.dryColumnAt 的 3 参调用同口径）。
     */
    public static boolean isDryRiverColumn(long worldSeed, int x, int z) {
        final double s = -strengthAt(worldSeed, x, z);
        if (s < DRY_RIVERBED_CORE) {
            return false;
        }
        if (isSanzuColumn(worldSeed, x, z)) {
            return false;
        }
        final double n01 = 0.5D + 0.5D * GTSRWorldgenHash
            .valueNoise(worldSeed ^ SALT_DRY_RIVER, x / DRY_RIVERBED_NOISE_SCALE, z / DRY_RIVERBED_NOISE_SCALE);
        return s >= DRY_RIVERBED_CORE + DRY_RIVERBED_JITTER * n01;
    }

    /**
     * sanzu 群系指派的最小河强。<b>已删字段（P23 R1·S6，v1.20.46）</b>：批2 S2 随
     * {@link #isSanzuColumn} 的河道支删除而生产零消费，判据侧（SLMC 分支列读数）引用随 S6
     * 重钉摘除后兑现删除（历史值 0.7 档见版本树）。
     */

    /**
     * sanzu 滩带噪声波长（格，P23 R1 新增）：与湖尺度匹配的低频档（任务包带 100~200，取 150
     * ——标称水半径 200 的 ~75%，滩带外缘在湖岸尺度上缓变、不成锯齿）。
     * <b>P25（D2①）：150 → 260</b>——幅随 D1 重定标（D_eff 1070 → 2670，特征长同比放大；
     * 260 ≈ 1.3×标称水径 200，仍属"湖岸尺度缓变"档）。
     */
    public static final double SANZU_BIOME_SHORE_NOISE_SCALE = 260.0D;

    /**
     * sanzu 滩带噪声调制幅度（压力域，P23 R1 新增）。<b>换算式（javadoc 钉）</b>：滩带外缘阈值
     * {@code sanzuBiomeShoreAt = LAKE_SHORE + n01×本值}，噪声腿格宽 ≈ 本值 ×
     * D_eff/((1+{@link #LAKE_WATER_LEVEL})(1+{@link #LAKE_SHORE}))（P23 档：0.03×1070/1.55 ≈
     * <b>0~17 格</b>，叠加湖滨带 ≈22 格 ⇒ 滩带总宽 ≈22~39 格）。n01 ∈ [0,1) ⇒ 阈值 ≥
     * {@link #LAKE_SHORE} &gt; {@link #LAKE_WATER_LEVEL} ⇒ <b>恒覆盖置水区</b>（凡
     * {@code lakeAt < LAKE_SHORE ∧ h < 68} 的置水列（fillSanzuLakes 口径）必在本谓词域内——
     * E"湖滨灌水由滩带认领自解"的几何前提）。
     * <p>
     * <b>P25（D1⑤/D2①）：0.03 → 0.0075</b>——按"噪声腿格宽 0~17 格"反解（目标带不动）：
     * <b>本值 = 17 ÷ (D_eff/((1+W')(1+S'))) = 17×(1.081×1.091)/2670 ≈ 0.00751，取 0.0075</b>
     * ⇒ 噪声腿格宽 ≈ 0.0075×2670/1.1794 ≈ 0~17.0 格、滩带总宽 ≈ 22~39 格逐点起伏
     * （目标带 10~40 内；S2 实测钉）。
     * <p>
     * <b>P28-L（v1.20.51 D1·滩宽×5 随动）：0.0075 → 0.0375</b>——设计滩环 22→113 格
     * （{@link #LAKE_SHORE} 0.131）后噪声腿同比 ×5：0.0375×2670/1.1794 ≈ <b>0~85 格</b> ⇒
     * 滩带总宽 ≈ <b>113~198 格</b>（抖动/环比例保持 P25 口径 17/22 ≈ 0.77）。单边调制语义
     * 不动（恒覆盖置水区前提原样）；"恒覆盖"面随滩宽放大 ⇒ 灌水门认领域同比放大（设计内）。
     * <p>
     * <b>P32（批3 T1-4 换算统一 + D3 两段制，v1.20.55）</b>：① 换算口径统一为线性式
     * {@code 格数 = ΔP×dN/|∇s|}（名义 ΔP×D_eff = ΔP×2670，见 {@link #LAKE_WATER_LEVEL} 的
     * P32 段）——本值 {@code 0.0375×2670 ≈ 0~100 格}（旧 {@code D_eff/((1+W')(1+S'))} 割线式
     * 随 P23-P28 圆场退役，历史段落原文保留）；② 本值此后 = <b>高频缘抖动腿</b>（λ260，
     * "外缘在湖岸尺度上缓变、不成锯齿"），滩带<b>岸段带宽</b>的低频调制由新独立腿
     * {@link #SANZU_BIOME_SHORE_WIDTH_DELTA}（λ700）承担——两段分工见该处。
     */
    public static final double SANZU_BIOME_SHORE_JITTER = 0.0375D;

    /**
     * sanzu 滩带噪声盐（P23 R1 新增）：取本类盐段尾 {@code …114L}（{@code …113L} 已被
     * {@link #SALT_RIVER_SEGMENT_GATE} 占用）。
     */
    public static final long SALT_SANZU_BIOME_SHORE = 0x5249F114L;

    /**
     * sanzu 滩带<b>岸段带宽低频调制幅度</b>（压力域，P32 批3 T1-4 D3 新增）：
     * {@link #sanzuBiomeShoreAt} 的第二非负项 {@code 本值×(0.5+0.5×n01_low)}，n01_low =
     * 独立低频 valueNoise 的 [0,1) 归一 ⇒ 项域 <b>[0.5×本值, 本值)</b>——<b>构造性非负</b>
     * ⇒ 阈值恒 ≥ {@link #LAKE_SHORE}，"shore 只外扩"单边不变式保持（三先例纪律：只外扩
     * 禁对称——本式先例 {@link #SANZU_BIOME_SHORE_JITTER}、岛阈抖乘 ≥1 因子
     * {@link #LAKE_ISLAND_GATE_JITTER}、河岸环内缘冻结 {@code inBankBand}；p31-r2 §6.2）。
     * <p>
     * <b>两段语义分工（D3 原义"滩带宽度随低频噪声变化"）</b>：高频腿（λ260 ×
     * {@link #SANZU_BIOME_SHORE_JITTER}）= <b>外缘抖动</b>——缘带逐段起伏不成锯齿；本低频腿
     * （λ700 × 本值）= <b>岸段带宽调制</b>——同一湖不同岸段的滩带总宽在数十格尺度上分宽窄
     * （换算名义口径 ×2670：本值域 [0.0125, 0.025) ⇒ <b>+33~67 格</b>；探针实测均值 +42 格，
     * temp/p32-q2/sweep2.out）。E-B 失败梯执行：S1b 结构性岸段化（方案 A）CVW 中位 0.114
     * &lt; 0.15 ⇒ 启用备选 B（plan/p32-plan.md 批3 Q2，主代理裁决）。
     * <p>
     * <b>灌水门对齐</b>：{@link #sanzuShoreWaterAt}（= {@code lakeAt < sanzuBiomeShoreAt}，
     * fillSanzuLakes 单一出口）认领域随宽滩段同比外扩——"凡 {@code lakeAt < LAKE_SHORE ∧
     * h < 68} 的置水列必在平面域内"恒覆盖链只放宽不收紧，语义一致（设计内）。
     * <p>
     * <b>固定阈消费面披露（两口径不并收纪律，本类 sanzuShoreWaterAt/lakeWaterAt 注的
     * 「不同阈不并收」先例族，O1a）</b>：PTP 快速臂阈
     * {@code SHORE+JITTER+Δ}=0.2685、微池第四肢 {@code SHORE+JITTER}=0.1685、TV
     * {@code lakePlaneTerrainAllowedAt} 门 0 域上确界 0.1685 等字面常量和消费点<b>保持固定阈
     * 语义</b>——平面压力域新上确界 {@code SHORE+JITTER+本值} = 0.1935 &gt; 0.1685 ⇒
     * (0.1685, 0.1935) 宽滩段列上这些门不再为 0（快速臂 0.2685 仍在平面外 ✓、NO_LAKE=1.0
     * ≫ 0.1935 ✓）；重钉归批3 Q3/批5（P25-2 面积占比、P25-3 噪声腿带随平面加宽出带）。
     * <p>
     * <b>终值依据（δW/λ 扫档，temp/p32-q2/sweep2.out）</b>：δW ∈ {0.015, 0.025, 0.035} ×
     * λ ∈ [500,900] 五档，SLMC 同口径 32 射线全带 [W, shoreAt) CVW（湖发现逐式镜像
     * SLMC：±12000 窗/步距 93/≥7 样本/每 seed 40 座）——0.025/700 档 <b>中位 0.155 ≥ 0.15
     * ∧ p10 0.100 ≥ 0.08 ∧ max/中位 2.220 ≤ 2.5</b>（对照 S1b 固定阈基线 0.114/0.066/2.396；
     * 低频纯贡献腿中位 0.123）。校准域：δW {0.015, 0.035} 两邻档、λ {500, 900} 带内。
     */
    public static final double SANZU_BIOME_SHORE_WIDTH_DELTA = 0.025D;

    /**
     * sanzu 滩带岸段带宽低频调制<b>波长</b>（格，P32 批3 T1-4 D3 新增）：λ = 700——岸段尺度
     * （标称水径 200 的 3.5×，一座典型湖的环周 ~1300 格上 ~2 个波长 ⇒ 岸段宽窄分明而非整湖
     * 同宽同窄）；与高频腿 λ260 之比 2.69 非整数（频域分离）；700 % 16 = 12 ≠ 0 ✔
     * （账本 §9 chunk 对齐条纹坑纪律）。数值上与 {@link #LAKE_WARP_SCALE}=700 同值纯属巧合
     * （噪声族不同：valueNoise vs disk warp，盐域独立，零耦合）。低频盐
     * {@link #SALT_SANZU_BIOME_SHORE_WIDTH}；校准域 [500, 900]（探针五档读数见
     * {@link #SANZU_BIOME_SHORE_WIDTH_DELTA} 注）。
     */
    public static final double SANZU_BIOME_SHORE_WIDTH_SCALE = 700.0D;

    /**
     * sanzu 滩带岸段带宽低频调制盐（P32 批3 T1-4 D3 新增）：取盐段尾 {@code …120L}
     * （…11F 归 {@link #SALT_LAKE_SHAPE}；…11D 随缎带退役空闲、不回收不复用）——
     * 与 {@link #SALT_SANZU_BIOME_SHORE}（…114）不同域 ⇒ 两腿噪声互不相关。
     */
    public static final long SALT_SANZU_BIOME_SHORE_WIDTH = 0x5249F120L;

    /**
     * sanzu 群系滩带外缘阈值（P23 R1 新增；<b>P27-L（v1.20.50）private → public</b>——单一真值
     * 直通消费面：PTP 振幅带（heightCore 湖段）与 {@link #sanzuShoreWaterAt}（灌水单一出口）+
     * C 片客户端水色梯度（{@code BlockAbyssalFluid} 场缓存只准复算 {@code lakeAt}+本式，禁复算
     * isSanzuColumn 全谓词），不加包装函数第二份）：
     * <b>两段制（P32 批3 T1-4 D3 起）</b>——{@link #LAKE_SHORE} + n01×
     * {@link #SANZU_BIOME_SHORE_JITTER}（高频缘抖动腿，λ260）+ {@code (0.5+0.5×n01_low)×}
     * {@link #SANZU_BIOME_SHORE_WIDTH_DELTA}（低频岸段带宽腿，λ700，独立盐
     * {@link #SALT_SANZU_BIOME_SHORE_WIDTH}；两段语义与换算见该常量注）。单边调制（阈值只往
     * 岸外扩，两腿皆构造性非负 ⇒ 阈值恒 ≥ {@link #LAKE_SHORE}）：对称 ± 式会在噪声低瓣把阈值
     * 压回 {@link #LAKE_SHORE} 之下、破坏"恒覆盖置水区"，<b>两腿皆禁止改对称</b>（三先例纪律，
     * 见 {@link #SANZU_BIOME_SHORE_WIDTH_DELTA} 注）。
     */
    public static double sanzuBiomeShoreAt(long worldSeed, int x, int z) {
        final double n = GTSRWorldgenHash.valueNoise(
            worldSeed ^ SALT_SANZU_BIOME_SHORE,
            x / SANZU_BIOME_SHORE_NOISE_SCALE,
            z / SANZU_BIOME_SHORE_NOISE_SCALE);
        final double nLow = GTSRWorldgenHash.valueNoise(
            worldSeed ^ SALT_SANZU_BIOME_SHORE_WIDTH,
            x / SANZU_BIOME_SHORE_WIDTH_SCALE,
            z / SANZU_BIOME_SHORE_WIDTH_SCALE);
        final double n01Low = (nLow + 1.0D) * 0.5D;
        return LAKE_SHORE + (n + 1.0D) * 0.5D * SANZU_BIOME_SHORE_JITTER
            + SANZU_BIOME_SHORE_WIDTH_DELTA * (0.5D + 0.5D * n01Low);
    }

    /**
     * <b>sanzu 列谓词（单点真值）</b>：<b>P23 R1（v1.20.46 批2 S2）遗忘之湖 = 湖面 + 可变宽
     * 湖滩带</b>——trunk&gt;0 门与河道支（s ≥ 0.7 档，字段已删见类顶登记）随主干宽河移除
     * 一并删除，湖面支阈值由 {@link #LAKE_WATER_LEVEL} 放宽为 {@link #sanzuBiomeShoreAt}
     * （水线起、噪声调制的滩带外缘，宽 ≈10~40 格）。
     * <p>
     * ═══ h 腿两档（P26-B3 D2·b1，v1.20.49）═══ 核心列（h ≤ SEA_LEVEL=68）维持 P23 语义
     * （"地表贴水"的可信滩面，非平凡真值）；新增<b>滩缘腿</b> h ≤ SEA_LEVEL+
     * {@link #SANZU_DRY_BEACH_RISE}（=69）——只放进噪声腿扩出的 0~17 格干滩环（滩带外列地形
     * 已回原高 ≈69、裸地中位 69：+1 格恰覆盖干滩环、h≥70 的常规平地/丘陵天然不进），解
     * "整湖无干滩"（sanzuArrivalColumn 穷举先例：一湖压力滩带 32769 列/几何干列 3081/sanzu
     * 干列 0）。<b>置水域不随动</b>：fillSanzuLakes 的置水门 {@code h < SEA_LEVEL} 原样 ⇒
     * 滩缘列（h=69）天然不满足置水门——本腿只扩群系平面不扩水面（82 纪律 1）。
     * <p>
     * ═══ h 腿四档（P27-L D1·1b+1e，v1.20.50）═══ 核心档（h ≤ SEA，5×5 表 #1）<b>逐字不动</b>
     * （P25 逐位承诺保持）；新增<b>岛档</b>（{@code lake < LAKE_ISLAND} → h ≤
     * {@link #SANZU_ISLAND_PLANE_TOP_Y}=72，3×3 表 #3——岛面干列入平面，平面岛域 ≈ 岛抬升域）
     * 与<b>滩坡档</b>（{@code lake < LAKE_SHORE} → h ≤ SEA+RISE+
     * {@link #SANZU_SHORE_SLOPE_HALO}=79，3×3 表 #4——平面沿设计滩环爬坡入岸坡，与干滩羽化
     * 材质域外沿对齐）；抖动滩缘 [SHORE, shoreAt) 维持 69 帽（表 #2 逐字不动，防噪声腿外溢）。
     * <p>
     * ═══ P25（D2）侵蚀式粗格净空门（sanzu 外缘软化）→ <b>P26-B3 两档 → P27-L 四档</b>═══
     * 原始谓词之上加"粗格邻域全真"门：粗格 4 格粒（{@code >>}COARSE_BLOCK_SHIFT，与 coarse
     * 身份面同一条 1:4 粒），其<b>代表列</b>（格基列 {@code cell<<2}）上"原始谓词全真"才入群系
     * ——形态学侵蚀消掉半岛/尖角/毛边。核心列（h ≤ SEA）R = {@link #SANZU_CLEAR_RADIUS_CELLS}
     * = 2（5×5，名义 8 格侵蚀带，逐字不动）；其余三档 R =
     * {@link #SANZU_FRINGE_CLEAR_RADIUS_CELLS} = 1（3×3，名义 4 格），各持独立 memo 表
     * （{@link #SANZU_CLEAR_CACHE} / {@link #SANZU_FRINGE_CLEAR_CACHE} /
     * {@link #SANZU_ISLAND_CLEAR_CACHE} / {@link #SANZU_SLOPE_CLEAR_CACHE}，
     * {@code TerrainVariants.swampInteriorAt} 同构先例：线程私有、上限整清重算值不变）；单边 ≥
     * 不变式（sanzuBiomeShoreAt 只往岸外扩）原样保留。核心列的 5×5 核心谓词路径与 P25 逐字
     * 相同 ⇒ 核心群系平面逐位不动。
     * <p>
     * 消费面与写平面同前：{@code ChunkProviderProsperityRuins.assignSanzuRiverBiome} 的平面
     * 写入、{@code ProsperityAirLookup} 的三途余汽（<b>语义披露已获用户接受：余汽随本谓词自动
     * 扩到干滩</b>）、{@code PlacementGate.dryColumnAt} ⑤腿（结构禁入滩缘）与
     * {@link #isDryRiverColumn} 的 {@code !isSanzuColumn} 互斥腿全部经本单点谓词自动跟随，
     * 无第二真值。湖面本身的 h ≤ 床 &lt; SEA_LEVEL 由 heightCore 湖段压低保证（P23 湖放大后
     * 自动跟随场值，见 PTP 湖段）。
     */
    public static boolean isSanzuColumn(long worldSeed, int x, int z) {
        final double lake = lakeAt(worldSeed, x, z);
        if (!(lake < sanzuBiomeShoreAt(worldSeed, x, z))) {
            return false;
        }
        final int h = ProsperityTerrainProfile.heightAt(worldSeed, x, z);
        // h 腿（P26-B3 两档 → P27-L 四档）：核心 h ≤ SEA 维持 P25 语义（逐字不动）；岛档
        // lake<LAKE_ISLAND → h ≤ SANZU_ISLAND_PLANE_TOP_Y（派生式 72，岛面干列入平面）；滩坡档
        // lake<LAKE_SHORE → h ≤ SEA+RISE+SANZU_SHORE_SLOPE_HALO（设计滩环内爬坡帽 79）；抖动
        // 滩缘 [SHORE, shoreAt) 维持 69 帽（P26-B3 逐字，防噪声腿外溢）。
        final boolean coreLeg = h <= ProsperityTerrainProfile.SEA_LEVEL;
        if (!coreLeg) {
            if (lake < LAKE_ISLAND) {
                if (h > SANZU_ISLAND_PLANE_TOP_Y) {
                    return false;
                }
            } else if (lake < LAKE_SHORE) {
                if (h > ProsperityTerrainProfile.SEA_LEVEL + SANZU_DRY_BEACH_RISE + SANZU_SHORE_SLOPE_HALO) {
                    return false;
                }
            } else if (h > ProsperityTerrainProfile.SEA_LEVEL + SANZU_DRY_BEACH_RISE) {
                return false;
            }
        }
        // —— 侵蚀门（P25 D2 → P26-B3 两档 → P27-L 四档）：核心 5×5 核心谓词（逐字同 P25，
        // memo 表 #1 与 P25 逐位相同）｜岛档/滩坡档 3×3 各自独立 memo 表（#3/#4）｜滩缘 3×3
        // （#2，P26-B3 逐字）——
        final int cellX = x >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int cellZ = z >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        if (coreLeg) {
            for (int dz = -SANZU_CLEAR_RADIUS_CELLS; dz <= SANZU_CLEAR_RADIUS_CELLS; dz++) {
                for (int dx = -SANZU_CLEAR_RADIUS_CELLS; dx <= SANZU_CLEAR_RADIUS_CELLS; dx++) {
                    if (!sanzuCellClearAt(worldSeed, cellX + dx, cellZ + dz)) {
                        return false;
                    }
                }
            }
        } else if (lake < LAKE_ISLAND) {
            for (int dz = -SANZU_FRINGE_CLEAR_RADIUS_CELLS; dz <= SANZU_FRINGE_CLEAR_RADIUS_CELLS; dz++) {
                for (int dx = -SANZU_FRINGE_CLEAR_RADIUS_CELLS; dx <= SANZU_FRINGE_CLEAR_RADIUS_CELLS; dx++) {
                    if (!sanzuIslandCellClearAt(worldSeed, cellX + dx, cellZ + dz)) {
                        return false;
                    }
                }
            }
        } else if (lake < LAKE_SHORE) {
            for (int dz = -SANZU_FRINGE_CLEAR_RADIUS_CELLS; dz <= SANZU_FRINGE_CLEAR_RADIUS_CELLS; dz++) {
                for (int dx = -SANZU_FRINGE_CLEAR_RADIUS_CELLS; dx <= SANZU_FRINGE_CLEAR_RADIUS_CELLS; dx++) {
                    if (!sanzuSlopeCellClearAt(worldSeed, cellX + dx, cellZ + dz)) {
                        return false;
                    }
                }
            }
        } else {
            for (int dz = -SANZU_FRINGE_CLEAR_RADIUS_CELLS; dz <= SANZU_FRINGE_CLEAR_RADIUS_CELLS; dz++) {
                for (int dx = -SANZU_FRINGE_CLEAR_RADIUS_CELLS; dx <= SANZU_FRINGE_CLEAR_RADIUS_CELLS; dx++) {
                    if (!sanzuFringeCellClearAt(worldSeed, cellX + dx, cellZ + dz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 净空门的侵蚀半径（粗格数，P25 D2）：R=2 ⇒ 5×5 = 25 邻格 = 名义 8 格侵蚀带——软边宽与
     * 滩带总宽（≈22 格）同量级偏小，只削毛边不啃滩。校准域 {1,2,3}（名义 4/8/12 格）。
     * <b>P26-B3 起本值只管核心列（h ≤ SEA）</b>；滩缘列见
     * {@link #SANZU_FRINGE_CLEAR_RADIUS_CELLS}。
     */
    private static final int SANZU_CLEAR_RADIUS_CELLS = 2;

    /**
     * <b>滩缘腿干滩升高（格，P26-B3 D2·b1 新增）</b>：h 腿上沿 = SEA_LEVEL + 本值。取 1 ⇒
     * 恰好放进噪声腿 0~17 格干滩环（滩带外列回原高 ≈69、裸地中位 69）；校准域 {0,1,2}——
     * 0 = b1 退档（还原 P25「h ≤ SEA」单档，R3 降级腿）；2 会放进中位 69 的普通平地
     * （面积失控 ⇒ "无滩之滩"），慎用。消费面：{@link #isSanzuColumn} h 腿 /
     * {@link #sanzuArrivalColumn} 干列窗 / CPR 湖滩干滩料档（LakeWetBandTopSelector）三处
     * 引同一常量，不另立第二份升高值。
     */
    public static final int SANZU_DRY_BEACH_RISE = 1;

    /**
     * <b>岛档平面帽</b>（P27-L D1·1e 新增，v1.20.50）：岛压力域（{@code lakeAt < LAKE_ISLAND}）内
     * 非核心列（h &gt; SEA_LEVEL）的 h 上沿。派生式 = {@code SEA_LEVEL + (int) LAKE_ISLAND_LIFT}
     * = <b>72</b>（{@link #LAKE_PILLAR_TOP_Y} 的派生式先例同款，不另立第二真值）——恰为岛面顶高：
     * 岛面由 {@link #lakeIslandTopAt} 抬到 SEA+LIFT ⇒ 岛面干列（h ∈ (68,72]）整段可入平面；
     * 岛缘水下床列（h ≤ SEA）由核心档覆盖 ⇒ 平面岛域 ≈ 岛抬升域。岛压力域（中位半径 ≈139）
     * 大于绝对半径岛（≈40），但差额列全是水下床列（核心档已盖）⇒ 无假阳性；本帽只复用谓词
     * 已算出的 lakeAt，零新场求值（1e 选型，ue-lake §1）。
     */
    public static final int SANZU_ISLAND_PLANE_TOP_Y = ProsperityTerrainProfile.SEA_LEVEL + (int) LAKE_ISLAND_LIFT;

    /**
     * <b>滩坡档帽的外檐</b>（格，P27-L D1·1b 新增，v1.20.50）：滩坡档 h 上沿 = SEA_LEVEL+
     * {@link #SANZU_DRY_BEACH_RISE}+本值 = 68+1+10 = <b>79</b>。取 10 = 干滩羽化的最大外檐
     * （P22 A2b 的 {@code WETB_HALO_MAX} 档，概率羽化域内 h 可到 79）——谓词 h 帽与材质羽化域
     * 外沿对齐 ⇒ 平面沿<b>设计滩环</b>（宽 22~39 格）爬坡入岸坡，而抖动滩缘（[SHORE, shoreAt)）
     * 维持 69 帽不外溢。缩样三档 {69(基线), 74, 79} 的定档读数见
     * {@code plan/tmp/p27-l-readings.md}（占比超 2.0% 且 74 帽仍超 ⇒ 弃滩坡档，D1 回退点）。
     * <p>
     * <b>单源纪律</b>：本常量提升为 RVF/CPR 共享真值——CPR {@code LakeWetBandTopSelector}
     * 的 {@code WETB_HALO_MAX} 引用本值（{@code SANZU_DRY_BEACH_RISE} 三处同源先例同款），
     * 高度帽与材质檐不再各持一份 10。
     */
    public static final int SANZU_SHORE_SLOPE_HALO = 10;

    /**
     * 非核心档净空门侵蚀半径（粗格数，P26-B3 D2 新增；<b>P27-L 起岛档/滩坡档共用</b>）：R=1
     * ⇒ 3×3 = 9 邻格 = 名义 4 格侵蚀带——只削毛边，不吃掉 0~17 格噪声腿干滩环的主体（R=2 的
     * 名义 8 格会吃掉其绝大部分）；岛档同理（5×5 会把平面岛干半径 40 蚀到 ≈32，见
     * {@link #sanzuIslandCellClearAt}）、滩坡档同理（22~39 格设计滩环）。校准域 {1,2}；R3 降级腿 =
     * 收紧本档（或 {@link #SANZU_DRY_BEACH_RISE} 退 0）。
     */
    private static final int SANZU_FRINGE_CLEAR_RADIUS_CELLS = 1;

    /** 净空门粗格 memo 上限（超限整清重算值不变，swampInteriorAt 同款）。 */
    private static final int SANZU_CLEAR_CACHE_CAP = 65536;

    private static final ThreadLocal<HashMap<Long, HashMap<Long, Boolean>>> SANZU_CLEAR_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    /** 滩缘净空门粗格 memo（P26-B3 D2 第二张表，同 {@link #SANZU_CLEAR_CACHE} 范式与上限）。 */
    private static final ThreadLocal<HashMap<Long, HashMap<Long, Boolean>>> SANZU_FRINGE_CLEAR_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    /** 岛档净空门粗格 memo（P27-L 第三张表，同 {@link #SANZU_CLEAR_CACHE} 范式与上限）。 */
    private static final ThreadLocal<HashMap<Long, HashMap<Long, Boolean>>> SANZU_ISLAND_CLEAR_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    /** 滩坡档净空门粗格 memo（P27-L 第四张表，同 {@link #SANZU_CLEAR_CACHE} 范式与上限）。 */
    private static final ThreadLocal<HashMap<Long, HashMap<Long, Boolean>>> SANZU_SLOPE_CLEAR_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    /**
     * 粗格代表列（格基列 {@code (cellX<<2, cellZ<<2)}）的<b>原始谓词</b>（压力腿 ∧ h 腿，
     * P25 D2 前的 isSanzuColumn 两腿逐字）：按 (seed, cellX, cellZ) memo——未命中一次最坏
     * 25 格 × (lakeAt+heightAt)，摊销后每新粗格 ~几个新格。<b>无递归环</b>：heightAt →
     * heightCore → strengthAt（P25 湖腿 → lakeAt/valueNoise）与 swampLakeAt（P25 湖腿的
     * 压力形式，不回 isSanzuColumn）均不触本谓词。
     * <p>
     * <b>P26-B3 起本表 = 核心谓词（h ≤ SEA_LEVEL）</b>——只服务 isSanzuColumn 的核心档
     * 5×5 门；滩缘档（h ≤ SEA+RISE）走 {@link #sanzuFringeCellClearAt} 第二张表（同范式，
     * 不复用本表 ⇒ 核心档 memo 值与 P25 逐位相同）。
     */
    private static boolean sanzuCellClearAt(long worldSeed, int cellX, int cellZ) {
        final HashMap<Long, HashMap<Long, Boolean>> bySeed = SANZU_CLEAR_CACHE.get();
        HashMap<Long, Boolean> cells = bySeed.get(worldSeed);
        if (cells == null) {
            cells = new HashMap<>();
            bySeed.put(worldSeed, cells);
        }
        final Long key = Long.valueOf(((long) cellX << 32) | (cellZ & 0xFFFFFFFFL));
        final Boolean cached = cells.get(key);
        if (cached != null) {
            return cached.booleanValue();
        }
        final int rx = cellX << GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int rz = cellZ << GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final boolean clear = lakeAt(worldSeed, rx, rz) < sanzuBiomeShoreAt(worldSeed, rx, rz)
            && ProsperityTerrainProfile.heightAt(worldSeed, rx, rz) <= ProsperityTerrainProfile.SEA_LEVEL;
        if (cells.size() >= SANZU_CLEAR_CACHE_CAP) {
            cells.clear();
        }
        cells.put(key, Boolean.valueOf(clear));
        return clear;
    }

    /**
     * 滩缘档粗格代表列的<b>滩缘原始谓词</b>（P26-B3 D2 新增，压力腿 ∧ h ≤ SEA_LEVEL+
     * {@link #SANZU_DRY_BEACH_RISE}——b1 扩腿后的 isSanzuColumn 两腿逐字）：按 (seed, cellX,
     * cellZ) memo 于 {@link #SANZU_FRINGE_CLEAR_CACHE}（第二张表，与核心表
     * {@link #sanzuCellClearAt} 分离 ⇒ 两档互不污染、核心档值与 P25 逐位相同）。求值面、
     * 上限整清、无递归环论证全部同 {@link #sanzuCellClearAt}（h 腿阈 +1 是唯一差异）。
     */
    private static boolean sanzuFringeCellClearAt(long worldSeed, int cellX, int cellZ) {
        final HashMap<Long, HashMap<Long, Boolean>> bySeed = SANZU_FRINGE_CLEAR_CACHE.get();
        HashMap<Long, Boolean> cells = bySeed.get(worldSeed);
        if (cells == null) {
            cells = new HashMap<>();
            bySeed.put(worldSeed, cells);
        }
        final Long key = Long.valueOf(((long) cellX << 32) | (cellZ & 0xFFFFFFFFL));
        final Boolean cached = cells.get(key);
        if (cached != null) {
            return cached.booleanValue();
        }
        final int rx = cellX << GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int rz = cellZ << GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final boolean clear = lakeAt(worldSeed, rx, rz) < sanzuBiomeShoreAt(worldSeed, rx, rz)
            && ProsperityTerrainProfile.heightAt(worldSeed, rx, rz)
                <= ProsperityTerrainProfile.SEA_LEVEL + SANZU_DRY_BEACH_RISE;
        if (cells.size() >= SANZU_CLEAR_CACHE_CAP) {
            cells.clear();
        }
        cells.put(key, Boolean.valueOf(clear));
        return clear;
    }

    /**
     * 岛档粗格代表列的<b>岛档原始谓词</b>（P27-L 1e 新增，{@code lakeAt < LAKE_ISLAND} ∧
     * {@code heightAt ≤ SANZU_ISLAND_PLANE_TOP_Y}——岛压力域 ⊂ 湖水区 ⊂ 群系压力域 ⇒ 压力腿
     * 由 {@code lakeAt < LAKE_ISLAND} 一条腿蕴含，不重复比较）：按 (seed, cellX, cellZ) memo 于
     * {@link #SANZU_ISLAND_CLEAR_CACHE}（第三张表，与核心/滩缘/滩坡各表分离 ⇒ 各档互不污染、
     * 核心档 memo 值与 P25 逐位相同）。求值面、上限整清、无递归环论证全部同
     * {@link #sanzuCellClearAt}（h 腿阈 = 岛帽 72 是唯一差异）。岛是湖内紧致团块、四邻皆水列
     * （核心档已盖）⇒ 3×3 侵蚀只收岛缘毛边（R=2 会把平面岛干半径 40 蚀到 ≈32，观感 = 岛上
     * 长邻接群系环——故岛档与滩坡档共用滩缘档的 R=1）。
     */
    private static boolean sanzuIslandCellClearAt(long worldSeed, int cellX, int cellZ) {
        final HashMap<Long, HashMap<Long, Boolean>> bySeed = SANZU_ISLAND_CLEAR_CACHE.get();
        HashMap<Long, Boolean> cells = bySeed.get(worldSeed);
        if (cells == null) {
            cells = new HashMap<>();
            bySeed.put(worldSeed, cells);
        }
        final Long key = Long.valueOf(((long) cellX << 32) | (cellZ & 0xFFFFFFFFL));
        final Boolean cached = cells.get(key);
        if (cached != null) {
            return cached.booleanValue();
        }
        final int rx = cellX << GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int rz = cellZ << GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final boolean clear = lakeAt(worldSeed, rx, rz) < LAKE_ISLAND
            && ProsperityTerrainProfile.heightAt(worldSeed, rx, rz) <= SANZU_ISLAND_PLANE_TOP_Y;
        if (cells.size() >= SANZU_CLEAR_CACHE_CAP) {
            cells.clear();
        }
        cells.put(key, Boolean.valueOf(clear));
        return clear;
    }

    /**
     * 滩坡档粗格代表列的<b>滩坡原始谓词</b>（P27-L 1b 新增，{@code lakeAt < LAKE_SHORE} ∧
     * {@code heightAt ≤ SEA_LEVEL+SANZU_DRY_BEACH_RISE+SANZU_SHORE_SLOPE_HALO}（=79）——
     * {@code lakeAt < LAKE_SHORE} ⇒ 群系压力腿蕴含，不重复比较）：按 (seed, cellX, cellZ)
     * memo 于{@link #SANZU_SLOPE_CLEAR_CACHE}（第四张表，同范式与上限）。坡上新列来自地形
     * 连续域、毛边风险与滩缘同类 ⇒ R=1（3×3）；R=2 的 8 格侵蚀会吃掉 22~39 格设计滩环的
     * 可观份额。求值面、上限整清、无递归环论证全部同 {@link #sanzuCellClearAt}。
     */
    private static boolean sanzuSlopeCellClearAt(long worldSeed, int cellX, int cellZ) {
        final HashMap<Long, HashMap<Long, Boolean>> bySeed = SANZU_SLOPE_CLEAR_CACHE.get();
        HashMap<Long, Boolean> cells = bySeed.get(worldSeed);
        if (cells == null) {
            cells = new HashMap<>();
            bySeed.put(worldSeed, cells);
        }
        final Long key = Long.valueOf(((long) cellX << 32) | (cellZ & 0xFFFFFFFFL));
        final Boolean cached = cells.get(key);
        if (cached != null) {
            return cached.booleanValue();
        }
        final int rx = cellX << GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int rz = cellZ << GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final boolean clear = lakeAt(worldSeed, rx, rz) < LAKE_SHORE
            && ProsperityTerrainProfile.heightAt(worldSeed, rx, rz)
                <= ProsperityTerrainProfile.SEA_LEVEL + SANZU_DRY_BEACH_RISE + SANZU_SHORE_SLOPE_HALO;
        if (cells.size() >= SANZU_CLEAR_CACHE_CAP) {
            cells.clear();
        }
        cells.put(key, Boolean.valueOf(clear));
        return clear;
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
    // wetAt/poolLevelAt/lakeAt/isSanzuColumn/trunkAt 全函数 digest）前后对拍为证；</li>
    // <li><b>ThreadLocal 不跨线程</b>（沿用本类 EVAL_BUF/DISK_CACHE 同一条线程纪律）；</li>
    // <li><b>淘汰有界</b>：全部为直接映射定长表——容量固定、槽位按精确键覆盖，无需清空逻辑；
    // 键全字段精确比较（seed/x/z[/roster]），冲突即淘汰重算，无假命中；</li>
    // <li><b>warp+evalBorder 复合</b>（{@link #warpedBorder}）：命中把槽内快照拷回 EVAL_BUF 返回，
    // 未命中求值后把 EVAL_BUF 快照存槽——调用方拿到的永远是 EVAL_BUF 实例（原 buffer 纪律
    // "不跨下一次调用持有"不变）。</li>
    // </ul>

    /** double 列槽（trunkAt/swampLakeAt）。 */
    private static final class DblSlot {

        long seed;
        int x;
        int z;
        boolean valid;
        double val;
    }

    /**
     * lakeAt 专用列槽（P24-C3 新增）：在 {@link DblSlot} 上多带一份同列
     * {@link #lakeStationDistances} 的 (dC, dN) 快照，供 {@link #lakeIslandTopAt} 的绝对半径腿复用
     * （消岛域列的第 2 次 disk+3×3）。{@code distValid=false} = 该槽无站距快照（回退重扫）。
     */
    private static final class LakeSlot {

        long seed;
        int x;
        int z;
        boolean valid;
        double val;
        boolean distValid;
        double d0;
        double d1;
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

    /** lakeAt 专用槽表初始化（P24-C3；容量/淘汰纪律同 {@link #dblSlots()}）。 */
    private static LakeSlot[] lakeSlots() {
        final LakeSlot[] a = new LakeSlot[MEMO_CAP];
        for (int i = 0; i < a.length; i++) {
            a[i] = new LakeSlot();
        }
        return a;
    }

    private static final ThreadLocal<DblSlot[]> TRUNK_MEMO = ThreadLocal.withInitial(GTSRVoronoiRiverField::dblSlots);
    private static final ThreadLocal<LakeSlot[]> LAKE_MEMO = ThreadLocal.withInitial(GTSRVoronoiRiverField::lakeSlots);
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
     * 同 seed 一致、超限整清重算值不变）。五张表（大弯/小弯/主干带 T5/巨湖 warp/巨湖 warp 副倍频
     * P25）成组缓存；key = worldSeed。
     */
    private static final int DISK_CACHE_CAP = 8;

    private static final ThreadLocal<HashMap<Long, OpenSimplexDisk[]>> DISK_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    /** 单线程生成域内的可复用输出缓冲（避免逐列分配）。 */
    private static final ThreadLocal<double[]> DISK_BUF = ThreadLocal.withInitial(() -> new double[2]);

    private static double[] diskBuffer() {
        return DISK_BUF.get();
    }

    /**
     * 湖细胞中心的可复用缓冲（v1.20.41 P20 S5：{@link #islandPillarAt} 逐列调用
     * {@link #lakeCellCenterAt} 时避免每列分配 4 元数组——同 {@link #DISK_BUF} 的单线程生成域纪律）。
     */
    private static final ThreadLocal<double[]> LAKE_CENTER_BUF = ThreadLocal.withInitial(() -> new double[4]);

    /**
     * {@code dC/dN} 两站的（绝对站距、次近站距）可复用缓冲（v1.20.41 P20 S5d：{@link #lakeAt0} 与
     * {@link #lakeIslandTopAt} 各一次 {@link #lakeStationDistances} 求值的零分配出口——同
     * {@link #DISK_BUF}/{@link #LAKE_CENTER_BUF} 的单线程生成域纪律；两处调用都在取值后立即用完，
     * 不跨下一次 {@code diskAt} 持有）。
     */
    private static final ThreadLocal<double[]> LAKE_DIST_BUF = ThreadLocal.withInitial(() -> new double[2]);

    /**
     * {@link #lakeStationScan} 的 4 槽输出缓冲（P32 T1-3：[dC, dN, argminGx, argminGz]——
     * {@link #lakeAt0} 与 {@link #lakeStationDistances} 内部用，同 {@link #LAKE_DIST_BUF} 的
     * 单线程生成域零分配纪律）。
     */
    private static final ThreadLocal<double[]> LAKE_SCAN_BUF = ThreadLocal.withInitial(() -> new double[4]);

    /**
     * {@link #lakeStationScan} 的锚点相对坐标附带出口（P32 T1-3：[px − argmin 锚 X,
     * pz − argmin 锚 Z]，形状求值输入）。<b>即取即用</b>：{@link #lakeAt0} 在 scan 之后、
     * 任何下次 diskAt/scan 之前消费（{@link #stationParams}/{@link #fillShapeSlot} 只走
     * cellOffset/hash01，不触 DISK_BUF ⇒ 期间无失效源）——同 {@link #DISK_BUF} 单缓冲纪律。
     */
    private static final ThreadLocal<double[]> LAKE_SCAN_AUX = ThreadLocal.withInitial(() -> new double[2]);

    /** disk 槽位：0=大弯、1=小弯、2=主干带（T5）、3=巨湖破圆 warp（v1.20.40 P19 §D）、4=副倍频（P25 D3②）。 */
    private static final int SLOT_DISK_LARGE = 0;
    private static final int SLOT_DISK_SMALL = 1;
    private static final int SLOT_DISK_TRUNK = 2;
    private static final int SLOT_DISK_LAKE_WARP = 3;
    private static final int SLOT_DISK_LAKE_SUB = 4;

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
                new OpenSimplexDisk(worldSeed ^ SALT_DISK_LAKE_WARP),
                new OpenSimplexDisk(worldSeed ^ SALT_DISK_LAKE_SUB) };
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
