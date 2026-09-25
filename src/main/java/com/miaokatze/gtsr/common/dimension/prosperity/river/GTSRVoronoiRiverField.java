package com.miaokatze.gtsr.common.dimension.prosperity.river;

import java.util.HashMap;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.TerrainVariants;

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
 * <li>荒漠(2)：干谷断流——<b>P22 A1a（v1.20.42）起与全域一致</b>：置水门
 * {@link #wetAt} = {@code s ≥ WET_MIN ∧ trunk > 0}，带外全枯竭（干河床），原"整段闸
 * 干:湿 = 65:35"（{@link #DESERT_WET_SHARE}/{@link #segWetAt}）退役；</li>
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
     * <b>沼泽档（roster 3）豁免</b>：本值不作用于沼地河（见 {@link #bankCut}）——A7「沼泽床
     * ∈ [66.5,67.5]＝水面近地」是档表断言（结构上不可能被运行时下切打红），其语义地板只能由
     * 实现域守住；§5 S3 判据 3 给的处置正是"下切域限缩到 roster ∈ {0,1,2,4}"。
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
     * {@code true} = 铺<b>床料</b>（{@code prosperityRiverGravel}），{@code false} = 铺<b>滩料</b>
     * （群系档表，见 {@code GTSRRiverPlacer.flatMaterial}）：
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
     * 域 = {@link #inBankBand} 的谷坡环；沼泽档（roster 3）的豁免在调用侧（heightCore）落，
     * 不在本函数落——本函数是纯场，档位语义归地形侧，避免"两处各挡一次"的第二真值。
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
     * 荒漠整段闸的湿段份额（v1.20.40 P19 §B）：{@code wet(seg) = hash(segKey) < 本值}——
     * 干:湿 = <b>65:35</b>（用户拍板"荒漠干段为主、符合逻辑"；BOP DryRiver 先例=干段整段
     * 连贯）。整段同值 = 同一条细胞边（同 segKey）全干或全湿，跨 chunk 一致。
     * <p>
     * <b>v1.20.42（P22 A1a）退役</b>：置水门收紧为 {@link #wetAt} 的
     * {@code s ≥ WET_MIN ∧ trunkAt > 0}（全域枯竭＋sanzu 豁免），整段闸不再是任何生成判定
     * 的一部分。<b>保留字段</b>只因判据侧消费点（tools/dim1 下 RMC 等）本片不改（归 A1c 片
     * 一并退役），生产链路不得消费它——先例同 {@link #WET_EDGE}。
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
    /** 沼泽微池 Voronoi 细胞 x 偏移盐（v1.20.40 P19 §E 新增，独立第三 Voronoi）。 */
    public static final long SALT_SWAMP_POOL_X = 0x5249F10EL;
    /** 沼泽微池 Voronoi 细胞 z 偏移盐。 */
    public static final long SALT_SWAMP_POOL_Z = 0x5249F10FL;
    /**
     * 沼泽河床残潭噪声盐（v1.20.42 P22 A1b 新增）：值落在本类盐段尾——{@code …110L} 起
     * 已被 {@link #SALT_BANK_CUT}/{@link #SALT_LAKE_PILLAR}（同值两用）与
     * {@link #SALT_BANK_BAND}（{@code …111L}）占满 ⇒ 取 {@code …112L}。
     */
    public static final long SALT_SWAMP_RIVER_POOL = 0x5249F112L;

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

    /**
     * 巨湖床基准历史值（v1.20.39 T5 的平底床 63±1）。<b>v1.20.40（P19 §D）中心渐深后
     * 生产公式改由湖滨/湖心锚派生</b>（见 {@link #lakeBedAt}），本常量不再参与生成判定，
     * 保留只因 {@code tools/dim1/SanzuTrunkCoverageCheck} 的床带派生仍引用（判据批4 重钉时
     * 随 C2 带一并退役/重钉）。
     */
    public static final double LAKE_BED_TARGET = 63.0D;

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

    /**
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
    public static final double LAKE_BED_PLATEAU = 0.55D;

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
     * <b>本值 0.045 的取法（扫描数据选的，表 = {@code plan/tmp/p20-s5c/island-scan.md}）</b>：
     * 压力腿与绝对腿的<b>拐点</b>。逐列实测的湖尺度散布 p10/中位/p90 = 0.78/1.00/1.23（半径），
     * 岛域半径 = 849·本值·尺度 ⇒ 取本值 = 0.045 时 {@code 849·0.045·0.78 ≈ 30 =}{@link #LAKE_ISLAND_RADIUS}
     * ⇒ <b>约九成湖被绝对腿钉在同一个岛直径</b>（需求 8 的"约 30 格"是绝对尺寸，判据带也是绝对面积带），
     * 再小则绝对腿不 binding、散布原样透进干面积（带内命中率掉回 43–47%），再大只是把压力腿的
     * 空转区摊大（S1 三档被多剔除一圈<b>其实是湖床</b>的列：0.065 档 10175 列 vs 0.045 档 4817 列，
     * 深/浅档比 0.689 vs 1.484）。实档读数：干列中位 827（p10 742 / p90 831）、带内 29/32 = 90.63%。
     */
    public static final double LAKE_ISLAND = 0.045D;

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
     * <b>终值 30</b>：与 {@link #LAKE_ISLAND} = 0.045 成拐点配对（{@code 849·0.045·尺度p10 0.78 ≈ 30}）
     * ⇒ 约九成湖被钉在干直径 ≈32 格。本值是<b>几何半径</b>（格），不是噪声波长 ⇒ §9 的
     * "新增波长 %16≠0" 纪律对本值不适用（无任何噪声采样吃它；它只进 {@link #lakeIslandTopAt} 的
     * 一条除法）。
     */
    public static final double LAKE_ISLAND_RADIUS = 30.0D;

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

    /** 湿带（水陆之间不积水的半湿表层带）贴水判据：地表距水面的最大格数（plan §15.4）。 */
    public static final int LAKE_WET_BAND_DROP = 2;

    /**
     * 岛底柱的<b>柱环半径</b>（格，plan §15.5 终值 6，替换 §8 先验 8）：0.013 档岛半径 p10 = 11.25，
     * 环半径 6 + 抖动 1 + 截面半宽 1 = 8 &lt; 11.25 ⇒ <b>全档不越岛缘</b>。原环 8+2=10 在 0.014 档
     * 下"5/5 全落岛内"仅 60.65% ⇒ 约四成湖会缺柱，故收环。
     * <b>v1.20.41 S5c 复核：值不动（6）</b>，成立条件反而变宽 —— 三条腿落地后岛干半径 = 15.96、
     * 岛域半径实测 min 19.6，"5 柱全落岛内"的湖占比 68.75% → <b>75.00%</b>（判据 C5 实跑）。
     */
    public static final double LAKE_PILLAR_RING = 6.0D;

    /** 岛底柱<b>抖动幅度</b>（格，终裁 ±1，替换 §8 先验 ±2）：取整后落在 {−1,0,+1}。 */
    public static final int LAKE_PILLAR_JITTER = 1;

    /** 岛底柱<b>截面半宽</b>（格，1 ⇒ 3×3 = 9 列/柱；§13 C5 已裁定逐列谓词允许 3×3/5×5）。 */
    public static final int LAKE_PILLAR_HALF_SECTION = 1;

    /** 岛底柱<b>根数</b>：1 根中心柱 + 4 根斜向环柱（§15.6 判据 3 的"5 柱全部贯通"口径）。 */
    public static final int LAKE_PILLAR_COUNT = 5;

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
     * <p>
     * <b>v1.20.41 S5 裁决：保持 0.15，不照 §8 抬到 WATER+0.035。</b>§8 的"+0.035 ⇒ 环带 ≈16 格"
     * 用的是判据注释里的旧斜率 600 格/单位压力；S0a 实测 {@code dr/dW|0.13 = 708.579}
     * （{@code plan/tmp/p20-baseline/lake-model-fit.txt} §3）⇒ 现值 0.02 压力宽 = <b>环带 ≈14.2 格</b>，
     * 已经在 §8 想要的 16 格量级内，多级缓坡（§15.4）在 14 格里足够铺 4 级台阶。抬到 0.035 只会
     * 把环带推到 ≈24.8 格：无收益地扩大置水面、抬高 §7 的结构挤压风险与 §7-6 的环带落块敞口。
     * 衔接的短板是<b>形状</b>（线性 → 台阶 + 湿带），不是宽度 ⇒ 本片改形状、不改本值。
     */
    public static final double LAKE_SHORE = 0.15D;

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
        /**
         * 断流档（荒漠 true：置水走 segKey 整段闸）。<b>v1.20.42（P22 A1a）从生产链退役</b>：
         * {@link #wetAt} 收紧为 {@code s ≥ WET_MIN ∧ trunkAt > 0}（全域枯竭＋sanzu 豁免）后，
         * 本布尔不再参与任何生成判定（{@link #bedFromPool} 的 wetGated 腿已删）。字段保留只因
         * 档表构造签名与判据侧口径本片不改（归 A1c 片）——先例同 {@link #WET_EDGE}。
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
     * （depth 2.0、amp 2.5 → 周期浅滩/河滩）；2 荒漠 = 干谷断流（<b>P22 A1a 起整段闸退役：
     * 与全域一致按 {@link #wetAt} 新门枯竭</b>，本档仅余纯参数差异）；3 沼泽 = 沼地河
     * （×1.2、depth 1.0 → 水深 0-1 沼地肌理）；4 sanzu = <b>主干宽河占位档</b>
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
     * segWetAt"腿退役）；</li>
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
                // "邻段干" = 邻列新湿门为假（trunk ≤ 0 或 s < WET_MIN），不再走 segWetAt。
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
     * ═══ v1.20.42（P22 A1a）全域枯竭＋sanzu 豁免 ═══ 门收紧为<b>单点一行语义</b>：
     * {@code s ≥ WET_MIN && trunkAt(seed,x,z) > 0}——除遗忘之川域（主干带）外全部河段不再置水
     * （干河床，沿用既有断流段观感），带内全有水、无枯竭段。三腿依据：
     * <ol>
     * <li>{@link #strengthAt} 带内有效宽档 = max(底档, {@link #TRUNK_WIDTH_SCALE}) ⇒ 带内 s 与
     * roster 无关（{@link #isSanzuColumn} 的构造不变量同源）⇒ 带内主干宽河自动豁免；</li>
     * <li>带外河核列（trunk ≤ 0 的常态河核）整域枯竭——荒漠整段闸（{@link #segWetAt}）与
     * 非荒漠恒湿两腿一并退役，wetness 从"段粒度"变"带粒度"；</li>
     * <li>湖水回填走 provider {@code fillSanzuLakes} 的 {@link #lakeAt} 独立通道（不经本门）
     * ⇒ 湖域不受影响；沼泽微池（{@code fillSwampPools}）同。</li>
     * </ol>
     * rosterIndex 形参自此不参与判定（保留签名：GTSRRiverPlacer/PlacementGate 既有调用面与
     * 判据口径不改）。列在水面之下（h1 &lt; poolLevel−1）的最终回填门在落块器（那里才有 h1）。
     * 枯竭边界的端面收尾由 {@link #endFaceBed} 面 A 以本门为真值重接（防竖直水墙）。
     */
    public static boolean wetAt(long worldSeed, int x, int z, int rosterIndex) {
        return -strengthAt(worldSeed, x, z, rosterIndex) >= WET_MIN && trunkAt(worldSeed, x, z) > 0.0D;
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
     * <p>
     * <b>v1.20.42（P22 A1a）从生产链退役</b>：{@link #wetAt} 收紧为
     * {@code s ≥ WET_MIN ∧ trunkAt > 0}（全域枯竭＋sanzu 豁免）后，本方法不再是任何生产
     * 判型的输入（原消费点 wetAt / endFaceBed 面 A 均已改走新湿门）。方法与 {@link #SALT_WET}
     * <b>保留</b>只因判据侧消费点（tools/dim1 下 RMC）本片不改（归 A1c 片），生产链路不得
     * 消费它——先例同 {@link #WET_EDGE}。
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
        // v1.20.41 P20 S5d：3×3 站距扫描原样抽到 {@link #lakeStationDistances}（算术一字未动 ⇒
        // lakeAt 逐位不变，S5d 双跑对拍为证），本式仍取 dC/dN。抽出的动机见那里：岛的绝对半径腿
        // 要的是 <b>dC 本身</b>（绝对量），而 dC/dN 是尺度归一量 —— 同一次扫描、同一份形状真值。
        final double[] dd = LAKE_DIST_BUF.get();
        lakeStationDistances(worldSeed, x, z, dd);
        return dd[0] / dd[1];
    }

    /**
     * <b>湖置水列谓词</b>（v1.20.43 P22 版 B O1a，{@link #lakeAt} 的布尔单一出口，
     * {@code submergedAt} 先例同款）：{@code lakeAt < LAKE_WATER_LEVEL} 的逐字包装——湖床/湖水区
     * （水径推导见 {@link #LAKE_WATER_LEVEL}）。消费面两处同一真值：{@code PlacementGate.dryColumnAt}
     * 腿② 与 {@code ProsperityCaveField.waterColumnProtected} 腿④。
     * <p>
     * ⚠ 与湖<b>岸</b>口径（{@code lakeAt < LAKE_SHORE}，SwampFieldGrid / ProsperityLumenPlacer /
     * fillSanzuLakes 消费）<b>不同阈不并收</b>——O1a 纪律：阈值语义差异不得强行统一，岸径腿保持内联。
     */
    public static boolean lakeWaterAt(long worldSeed, int x, int z) {
        return lakeAt(worldSeed, x, z) < LAKE_WATER_LEVEL;
    }

    /**
     * 巨湖 Voronoi 的<b>一次共用几何求值</b>（v1.20.41 P20 S5d 从 {@link #lakeAt0} 原样抽出）：对坐标
     * 加一次 domain-warp（{@link #LAKE_WARP_SCALE}/{@link #LAKE_WARP}，第 4 张 disk 表）后做 3×3 Worley
     * 扫描，把 {@code out[0] = dC}（到最近湖站的<b>绝对</b>格距，未归一）与 {@code out[1] = dN}（次近
     * 格距）写给调用方。两个消费口：
     * <ul>
     * <li>{@link #lakeAt0} = {@code dC/dN}（尺度归一压力，湖域/水缘/床形用它）；</li>
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
     */
    public static void lakeStationDistances(long worldSeed, int x, int z, double[] out) {
        // v1.20.40 P19 §D 破圆：湖 Voronoi 输入坐标先揉一次 disk 位移（幅度/波长见常量注释）；
        // buf 取完即拷出，不跨后续 diskAt 持有（DISK_BUF 单缓冲纪律）。
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
        out[0] = dC;
        out[1] = dN;
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
     * <li><b>湖心锚</b> = {@code SEA_LEVEL − LAKE_CENTER_DEPTH} = <b>40</b>（v1.20.41 S5 水深 28 ⇒
     * 湖心锚正好贴全局高度地板 {@code MIN_HEIGHT=40}；v1.20.40 旧口径"锚 = 58、水深 10 带 8-12"
     * 原文保留在 {@link #LAKE_CENTER_DEPTH} 的注释里）；</li>
     * <li><b>插值域</b> = 湖水区压力 [0, {@link #LAKE_WATER_LEVEL}] 归一成的 u ∈ [0,1]，
     * <b>深盆平台 + 外段 smoothstep</b>（平台占比 {@link #LAKE_BED_PLATEAU}）：u ≤ 平台 ⇒ 恒取湖心锚，
     * 平台之外沿 smoothstep 抬到湖滨锚——防坡折与"三档列数比"的形状见 {@link #LAKE_BED_PLATEAU}；
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
        final double v = Math.min(1.0D, Math.max(0.0D, (u - LAKE_BED_PLATEAU) / (1.0D - LAKE_BED_PLATEAU)));
        final double g = 1.0D - v * v * (3.0D - 2.0D * v);
        return shoreBed + (centerBed - shoreBed) * g
            + 0.5D
            + 0.5D * GTSRWorldgenHash
                .valueNoise(worldSeed ^ SALT_LAKE_BED, x / LAKE_BED_NOISE_SCALE, z / LAKE_BED_NOISE_SCALE);
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
     * 。用 s01 而非线性/硬切
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
        if (lakePressure >= LAKE_ISLAND) {
            return Double.NaN;
        }
        final double seaLevel = ProsperityTerrainProfile.SEA_LEVEL;
        final double centerBed = seaLevel - LAKE_CENTER_DEPTH;
        final double islandTop = seaLevel + LAKE_ISLAND_LIFT;
        final double kPress = (LAKE_ISLAND - lakePressure) / LAKE_ISLAND;
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
        final double[] dd = LAKE_DIST_BUF.get();
        lakeStationDistances(worldSeed, x, z, dd);
        {
            final double kAbs = 1.0D - dd[0] / LAKE_ISLAND_RADIUS;
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
     * 修法 = 对 {@code X = 湖站 − W(X)} 做两次不动点迭代（{@code |W| ≤ LAKE_WARP = 70}、位移场波长
     * {@code LAKE_WARP_SCALE = 320} ⇒ 每步收缩比 ≈ 70/320 ≪ 1，两步后残差 &lt; 1 格），
     * 仍然只吃同一份 {@code diskAt} 表与同一条 {@code cellOffset} ⇒ 零新增真值。
     *
     * @param out 长度 ≥4 的缓冲：{@code [0]=中心世界 X、[1]=中心世界 Z、[2]=格 gx、[3]=格 gz}；
     *            主干带外（{@code trunk ≤ 0} ⇒ 无湖）时写 {@code out[2] = Integer.MIN_VALUE} 作失败标记
     */
    public static void lakeCellCenterAt(long worldSeed, int x, int z, double[] out) {
        out[2] = Integer.MIN_VALUE;
        if (trunkAt(worldSeed, x, z) <= 0.0D) {
            return;
        }
        final double[] buf = diskBuffer();
        diskAt(worldSeed, SLOT_DISK_LAKE_WARP, LAKE_WARP_SCALE, x, z, buf);
        final double px = x + buf[0] * LAKE_WARP;
        final double pz = z + buf[1] * LAKE_WARP;
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
        // 反解压力零点 X + W(X) = 湖站：两次不动点（收缩比 ≈ LAKE_WARP/LAKE_WARP_SCALE = 0.22 ⇒
        // 残差 < 1 格）。buf 是 DISK_BUF 单缓冲，取值即用于是本行，不跨下次 diskAt 持有。
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
            centerX = bestEx - buf[0] * LAKE_WARP;
            centerZ = bestEz - buf[1] * LAKE_WARP;
        }
        out[0] = centerX;
        out[1] = centerZ;
        out[2] = bestGx;
        out[3] = bestGz;
    }

    /**
     * <b>岛底柱列谓词</b>（plan §15.5，需求 8「岛和底部是有柱子相连的」；v1.20.41 P20 S5 新增）：
     * 本列是否落在某根岛的底柱截面上。
     * <p>
     * <b>柱位集合</b> = 1 根中心柱 + 4 根斜向环柱（{@link #LAKE_PILLAR_COUNT} = 5），环柱极角取
     * 45°/135°/225°/315°、半径 {@link #LAKE_PILLAR_RING} = 6 加 {@link #LAKE_PILLAR_JITTER} = ±1 的
     * 确定性抖动（抖动脉冲 = {@link #cellOffset} 同一条 splitmix 哈希，键 = 湖格 × 柱序号 ⇒
     * <b>同一座湖的柱位对全部列一致</b>，不会逐列各抖各的把截面抖散）；截面
     * {@code 2·LAKE_PILLAR_HALF_SECTION + 1 = 3×3}。
     * <p>
     * <b>为什么必须是逐列纯函数而不是结构通道</b>（§13 C5 / §15.5 表末行）：柱高
     * {@code y ∈ [LAKE_PILLAR_FLOOR_Y, LAKE_PILLAR_TOP_Y]} = 32 格 &gt;
     * {@code ChunkSpans.MAX_SLICE_HEIGHT = 12}，字符盘模板路承不住；而本谓词只判"本列是不是柱"，
     * 每一列由<b>它自己所在 chunk</b> 的那一趟 {@code fillSanzuLakes} 写满同一个 y 区间 ⇒
     * 跨 chunk 天然无缝，且 3×3 截面骑在 chunk 边界上也只是"两边各写自己那几列"，
     * 不构成任何跨界写（{@code ChunkClampedSink} 的丢弃计数应为 0，见 S5 回执）。
     * <p>
     * 域门 {@code lakeAt < LAKE_ISLAND}：只可能给岛域内的列 ⇒ 环柱半径 6 + 抖 1 + 半宽 1 = 8
     * 恒小于岛半径 p10 = 11.25（§15.5），柱不越岛缘。<b>v1.20.41 S5c 复核（原句保留）</b>：那个
     * 11.25 是 §15.5 的 9 格射线估值，逐列真值更小时刻反而更紧；S5c 三条腿落地后岛<b>干</b>半径
     * = 15.96、岛域半径实测 min 19.6 ⇒ 8 &lt; 15.96 有余量，且柱位全落在平台段（{@code k ≥ 0.60}）
     * ⇒ 柱顶正对满高岛面 72（判据实测柱脚床高由 [40,72] 收到 [72,72]）。
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
            // 中心柱（p=0）不抖环半径（抖了就不是"岛心正下方那根"了），只抖一个格内相位
            final double radius = p == 0 ? 0.0D : LAKE_PILLAR_RING + pillarJitter(worldSeed, gx, gz, p, 0L);
            final double angle = p == 0 ? 0.0D : Math.PI / 4.0D + (p - 1) * Math.PI / 2.0D;
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
     * <b>微池置水列谓词</b>（v1.20.43 P22 版 B O1a，{@link #swampLakeAt} 的布尔单一出口，
     * {@code submergedAt} 先例同款）：{@code swampLakeAt < SWAMP_POOL_WATER_LEVEL} 的逐字包装——
     * 非沼泽 roster 恒 {@link #NO_SWAMP_POOL}（比较式天然 false，roster 门短路语义保持）。
     * 消费面三处同一真值：{@code ProsperityCaveField.waterColumnProtected} 腿②、
     * {@code GTSRRiverPlacer.neighborBarrier} 微池腿、{@code ChunkProviderProsperityRuins.SwampFieldGrid}
     * nominal 门的微池腿。
     */
    public static boolean swampPoolWaterAt(long worldSeed, int x, int z, int rosterIndex) {
        return swampLakeAt(worldSeed, x, z, rosterIndex) < SWAMP_POOL_WATER_LEVEL;
    }

    // ═════════════════ v1.20.42（P22 A1b）：沼泽河床残潭场 ═════════════════

    /**
     * 沼泽河床残潭噪声波长（格）：{@code 29 % 16 = 13} ✔（账本 §9 的 chunk 对齐条纹坑，
     * 同 {@link #CUT_NOISE_SCALE}=33 的取值纪律）。斑块尺度 ≈ λ/2 ≈ 10-15 格（"断断续续"）。
     * 校准域 [23, 37] 且 %16≠0。
     */
    public static final double SWAMP_RIVER_POOL_SCALE = 29.0D;

    /**
     * 残潭门带中心（{@link GTSRWorldgenHash#valueNoise} 的 n 域）：门 = s01((n−本值)/宽度)。
     * 校准读数（P22 A1b 探针，seed 20260924，沼泽河床 19,624 列）：0.55 档带率 ~6%（互斥腿
     * 剔 34% 三档列后覆盖 0.036，低于目标带下沿）；0.50 档带率 ~8.3%、覆盖 0.052（贴下沿）；
     * 0.46 档带率 ~11%（计划"床面约 10-15% 列成潭"的中带）、覆盖 ~0.075。校准域可调（配套
     * {@link #SWAMP_RIVER_POOL_BAND_WIDTH}），潭覆盖率目标 ∈ [0.05, 0.25]（沼泽河床列口径）。
     */
    public static final double SWAMP_RIVER_POOL_BAND_CENTER = 0.46D;

    /** 残潭门带宽度（n 域，软边）。 */
    public static final double SWAMP_RIVER_POOL_BAND_WIDTH = 0.20D;

    /**
     * 潭底额外下挖基深（格，v1.20.42 P22 A1b）：地形侧 dig = 本值 + {@link #SWAMP_RIVER_POOL_DIG_SPAN}·门
     * （只对潭列 gate&gt;0 生效，非潭列逐位不变——均匀退化纪律）。校准（探针 VDIAG）：
     * 2.5 档会让床纹高瓣 + 浅门的列落到 {@code h = pool−3 = 名义潭顶} ⇒ 挖而无水的"空坑"，
     * 邻潭水柱在坑沿产生 39 处不外流违规；基深 ≥3.0 数学上恒 {@code h ≤ pool−4 < 潭顶} ⇒
     * 未被钳制的潭列必有水。终值 [3.0, 4.0] ⊂ 计划下挖域 [1.5, 4]。
     */
    public static final double SWAMP_RIVER_POOL_DIG_BASE = 3.0D;

    /** 潭底下挖跨度（格，× 门值 ⇒ 床成软边浅心；基深+跨度 = 4.0 封顶，不越计划域）。 */
    public static final double SWAMP_RIVER_POOL_DIG_SPAN = 1.0D;

    /**
     * 潭水顶相对段池水位的偏移（格）：水柱 {@code y ∈ [h+1, pool+本值]}，缺省 −3 ⇒ 水顶
     * {@code pool−3} 恒低于干床顶最小值 {@code pool−2} 至少 1 格（<b>不外流</b>构造不变量，
     * 见 {@code GTSRRiverPlacer} 潭置水支路的邻列钳制）＋嵌入河床面以下 ≥1 格。校准域
     * {−2, −3, −4}（保持不变量：|本值| ≥ 2）。
     */
    public static final int SWAMP_RIVER_POOL_FILL_TOP = -3;

    /**
     * <b>沼泽河床残潭场</b>（v1.20.42 P22 A1b，纯函数软门 ∈ [0,1]）：沼泽群系（roster 3）的
     * 河流河床枯竭后（A1a 起 {@link #wetAt} 新门 ⇒ 河核列 s ≥ {@link #WET_MIN} ∧ trunk ≤ 0 =
     * 干河床），在床面上残留断断续续的下沉嵌入式水潭。门腿（全部短路，任一不过 = 0）：
     * <ol>
     * <li>{@code rosterIndex == 3}（沼泽档；非沼泽零成本短路）；</li>
     * <li>{@code trunkAt ≤ 0}——<b>遗忘之川域（主干带）内零潭</b>（带内本就有水，无"残"可言）；</li>
     * <li>{@code s ≥ WET_MIN}（河核列 = 河床面，谷坡列不成潭）；</li>
     * <li><b>微池互斥</b>：{@link #swampLakeAt} ≥ {@link #SWAMP_POOL_WATER_LEVEL}——微池域由
     * provider {@code fillSwampPools} 按 {@code pool−1} 回填，潭列若与微池重叠会被顶破
     * "水顶低于干床 ≥1 格"的硬不变量（同因下一腿）；</li>
     * <li><b>沼泽三档水体互斥</b>：{@link TerrainVariants#swampTierAt} == {@code SWAMP_TIER_NONE}
     * ——三档列同样被 {@code fillSwampPools} 按 {@code pool−1} 回填（tier 门），重叠即水面顶到
     * 床面。两腿互斥让残潭只落在"干河床"上，{@code fillSwampPools} 对潭列恒短路
     * （tier NONE ∧ 非微池 ⇒ 其入口 continue），潭水顶由 {@code GTSRRiverPlacer} 独占；
     * 依赖声明：本类→TerrainVariants 是单向引用（后者不引用本类，无环）。</li>
     * </ol>
     * 门带值 = s01((n−{@link #SWAMP_RIVER_POOL_BAND_CENTER})/{@link #SWAMP_RIVER_POOL_BAND_WIDTH})
     * （n 为独立盐 {@link #SALT_SWAMP_RIVER_POOL}、波长 {@link #SWAMP_RIVER_POOL_SCALE} 的
     * valueNoise）。消费面三处同一真值：{@code ProsperityTerrainProfile.heightCore} 的潭底下挖、
     * {@code GTSRRiverPlacer} 的潭置水、{@code PlacementGate.dryColumnAt} 的结构避潭。
     */
    public static double swampRiverPoolAt(long worldSeed, int x, int z, int rosterIndex) {
        if (rosterIndex != 3) {
            return 0.0D;
        }
        if (trunkAt(worldSeed, x, z) > 0.0D) {
            return 0.0D;
        }
        if (-strengthAt(worldSeed, x, z, rosterIndex) < WET_MIN) {
            return 0.0D;
        }
        if (swampLakeAt(worldSeed, x, z, rosterIndex) < SWAMP_POOL_WATER_LEVEL) {
            return 0.0D;
        }
        if (TerrainVariants.swampTierAt(worldSeed, x, z, rosterIndex) != TerrainVariants.SWAMP_TIER_NONE) {
            return 0.0D;
        }
        final double n = GTSRWorldgenHash
            .valueNoise(worldSeed ^ SALT_SWAMP_RIVER_POOL, x / SWAMP_RIVER_POOL_SCALE, z / SWAMP_RIVER_POOL_SCALE);
        final double t = (n - SWAMP_RIVER_POOL_BAND_CENTER) / SWAMP_RIVER_POOL_BAND_WIDTH;
        final double c = t < 0.0D ? 0.0D : (t > 1.0D ? 1.0D : t);
        return c * c * (3.0D - 2.0D * c);
    }

    /**
     * <b>残潭列谓词</b>（v1.20.43 P22 版 B O1a，{@link #swampRiverPoolAt} 的布尔单一出口，
     * {@code submergedAt} 先例同款）：{@code swampRiverPoolAt > 0} 的逐字包装——潭列（含下挖潭底）。
     * 消费面同一真值：{@code PlacementGate.dryColumnAt} 腿④（结构避潭）、
     * {@code ProsperityCaveField.waterColumnProtected} 腿①（洞不穿潭）、{@code GTSRRiverPlacer}
     * 潭置水支路（本列门 + 潭邻钳内外两环）、{@code ChunkProviderProsperityRuins.SwampFieldGrid}
     * fixed 门的潭腿。roster 实参由各消费面自定（0 最保守 / 生产 coarse 链 / 3 字面 / 邻列实档），
     * 本出口<b>不统一</b> roster 语义（O1a 纪律：阈值/roster 语义差异不得强行统一）。
     * 需要<b>门值</b>的消费面（heightCore 的潭底下挖）仍取 {@link #swampRiverPoolAt} 本值。
     */
    public static boolean swampRiverPoolColumnAt(long worldSeed, int x, int z, int rosterIndex) {
        return swampRiverPoolAt(worldSeed, x, z, rosterIndex) > 0.0D;
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
