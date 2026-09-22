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
 * ≈ {@link #SEPARATION}×1.05）。v1.20.39 校准回路（RiverMorphologyCheck 实测）从规划起步值
 * 0.012 定到 <b>0.14</b>：起步值按"半宽 ≈ width×separation/2"外推（≈6 格），但 border2 的
 * 真实几何是 ≈ width×separation/4 ⇒ 0.012 只给 ~0.5 格水道；0.14 实测常态水道半宽 ≈5.8 格
 * （目标带 5-8）、谷坡半宽 ≈35 格（目标带 20-40），两带同时命中。</li>
 * </ol>
 *
 * <p>
 * ═══ 群系 riverStyle 档表（plan §3.2 {@link #RIVER_STYLE_BY_ROSTER}，v1.20.39 T5 起 5 元——
 * 第 5 元 sanzu 主干宽河占位档）═══ 下标 = L1 维内名册下标（0 锈蚀草原 / 1 齿轮森林 / 2 黄铜
 * 荒漠 / 3 喷气沼泽），越界/缺席（{@code rosterIndex} = -1，未装配账本的离线 JVM）回退
 * {@link #DEFAULT_STYLE}（常态河）——与 S-A 振幅档表同一条"档缺失走默认档"纪律，无任何
 * 身份等值判断。四档差异全部是<b>纯乘子/纯参数</b>：
 * <ul>
 * <li>草原(0)/森林(1)：常态河 + 浅滩——床噪声幅度 ±2.5，且 shoalNoise&gt;0.6 处床抬到
 * 67.5-68.2（干出浅滩，水面 68 口径之下刚好露头；回填期该列不置水）；</li>
 * <li>荒漠(2)：干谷断流——河谷照刻，置水改连续噪声闸（{@link #WET_EDGE}，起步使约 30%
 * 河段有水，串珠断流，替代旧"整条掷骰"）；</li>
 * <li>沼泽(3)：沼地河——width×1.6、床 66.5±0.5（水面近地，h1=66 列水深 1、h1=67 列干出，
 * 成沼地肌理）；不换河群系（RTG swamp 豁免先例）。</li>
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
     * 大弯蜿蜒：位移幅度（格，圆盘向量全长上限）。规划起步 90，校准回路（蜿蜒度 &gt;1.2 判据）
     * 定到 <b>240</b>：disk 实测 |v| 均值 ≈0.376、波长 ≈1.24 单位（尺度 240 ⇒ λ≈300 格），
     * 垂直边界分量 ≈ 幅度/√2 ⇒ 90 只给 ~24 格横向摆幅（曲折率 ~1.06）；240 实测曲折率 ≈1.27。
     * RTG 的 140 配 separation 1875；本维 separation 1050，同判据带需 ~1.7×。
     */
    public static final double LARGE_BEND = 240.0D;

    /** 小弯蜿蜒：位移尺度（格）。 */
    public static final double SMALL_BEND_SCALE = 80.0D;

    /** 小弯蜿蜒：位移幅度（格）。 */
    public static final double SMALL_BEND = 22.0D;

    /**
     * 河道相对半宽档（c 域阈值）：rs&lt;0 的域 = c&lt;width，即"河谷域"。v1.20.39 校准回路
     * 从规划起步 0.012 定到 0.14（类注释第 3 条：border2 真实几何是 width×separation/4
     * 而非 /2，起步值差一倍因子；0.14 命中 水道半宽 5-8 与 谷坡半宽 20-40 两带）。
     */
    public static final double WIDTH = 0.14D;

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

    /** 荒漠断流噪声闸：wetNoise &gt; 本值才置水（连续噪声 ⇒ 串珠断流，非整条掷骰）。 */
    public static final double WET_EDGE = 0.6D;

    /** 常态床目标高（水面 {@code SEA_LEVEL}=68，常态水深 2-5）。 */
    public static final double BED_TARGET = 64.5D;

    /** 细胞中心哈希偏移幅度（×separation；&lt;0.5 ⇒ 3×3 邻域 Worley F1 数学精确）。 */
    public static final double CELL_JITTER = 0.35D;

    /** 床低频噪声波长（格）——沿程床起伏（±幅度见档表）。 */
    public static final double BED_NOISE_SCALE = 96.0D;

    /** 浅滩噪声波长（格，仅草原/森林档）。 */
    public static final double SHOAL_SCALE = 160.0D;

    /** 浅滩阈值（shoalNoise &gt; 本值的河核列床抬到 67.5-68.2 干出）。 */
    public static final double SHOAL_THRESHOLD = 0.6D;

    /** 荒漠断流噪声波长（格，串珠段长 ~百格量级）。 */
    public static final double WET_NOISE_SCALE = 220.0D;

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
    /** 浅滩噪声盐。 */
    public static final long SALT_SHOAL = 0x5249F106L;
    /** 荒漠断流噪声盐。 */
    public static final long SALT_WET = 0x5249F107L;
    /** 主干带噪声盐（T5）。 */
    public static final long SALT_TRUNK = 0x5249F108L;
    /** 巨湖 Voronoi 细胞 x 偏移盐（T5 第二 Voronoi，与河网细胞盐域分离）。 */
    public static final long SALT_LAKE_CELL_X = 0x5249F109L;
    /** 巨湖 Voronoi 细胞 z 偏移盐。 */
    public static final long SALT_LAKE_CELL_Z = 0x5249F10AL;
    /** 巨湖床噪声盐。 */
    public static final long SALT_LAKE_BED = 0x5249F10BL;

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
     * 巨湖水位（RTG lakeWaterLevel 同位参数）：lakePressure &lt; 本值 = 湖水区（地形压至
     * {@link #LAKE_BED_TARGET}±1，即 62-64，水面 68 ⇒ 水深 4-6）。
     */
    public static final double LAKE_WATER_LEVEL = 0.11D;

    /** 巨湖湖滨带外缘（RTG lakeShoreLevel 同位参数）：[WATER, SHORE) 为床→原地形渐变带。 */
    public static final double LAKE_SHORE = 0.15D;

    /** 巨湖床目标高（62-64 带内中点；±1 噪声见 {@link #lakeBedAt}）。 */
    public static final double LAKE_BED_TARGET = 63.0D;

    /** 巨湖床噪声波长（格）。 */
    public static final double LAKE_BED_NOISE_SCALE = 192.0D;

    /**
     * {@link #lakeAt} 的"无湖"哨兵（带外/带内无湖一律返回它）：≥ {@link #LAKE_SHORE}，与压力同向
     * （低=湖心），消费面（heightCore 压低 / 水面回填）统一判 {@code lake < LAKE_SHORE}。
     */
    public static final double NO_LAKE = 1.0D;

    /**
     * 单一 riverStyle 档（全部纯参数，无身份等值判断；sanzu 第 5 元 T5 补）。
     */
    public static final class RiverStyle {

        /** 河道宽乘子（沼泽 1.6）。 */
        public final double widthScale;
        /** 床目标高（格）。 */
        public final double bedTarget;
        /** 床噪声幅度（±格）。 */
        public final double bedNoiseAmp;
        /** 浅滩档（草原/森林 true）。 */
        public final boolean shoals;
        /** 断流档（荒漠 true：置水走 wetNoise 闸）。 */
        public final boolean wetGated;

        RiverStyle(double widthScale, double bedTarget, double bedNoiseAmp, boolean shoals, boolean wetGated) {
            this.widthScale = widthScale;
            this.bedTarget = bedTarget;
            this.bedNoiseAmp = bedNoiseAmp;
            this.shoals = shoals;
            this.wetGated = wetGated;
        }
    }

    /** 默认档（身份不可得 = 常态河；与 S-A 振幅默认档同纪律）。 */
    public static final RiverStyle DEFAULT_STYLE = new RiverStyle(1.0D, BED_TARGET, 1.5D, false, false);

    /**
     * riverStyle 档表（plan §3.2 + §3.3）：0 草原 / 1 森林 = 常态河+浅滩（±2.5）；2 荒漠 = 干谷断流；
     * 3 沼泽 = 沼地河（×1.6、床 66.5±0.5）；4 sanzu = <b>主干宽河占位档</b>（v1.20.39 T5 按档表族
     * 4→5 约定就位）：宽乘子 = {@link #TRUNK_WIDTH_SCALE}——主干带内 {@link #strengthAt} 对任何
     * 底档统一取 max(底档, 3.5) ⇒ 本档被带入时与主干带分支同值（<b>无双乘</b>），床/置水沿用
     * 常态口径（深谷由 {@link #TRUNK_VALLEY_SCALE} 在高度链施加）。生产链路 rosterIndex=4 不会从
     * GenLayer 链出现（selector 名册仍 4 家，plan §3.3/§5），本元供档表族长度一致与判据/后续消费面。
     */
    public static final RiverStyle[] RIVER_STYLE_BY_ROSTER = {
        /* 0 锈蚀草原 */ new RiverStyle(1.0D, BED_TARGET, 2.5D, true, false),
        /* 1 齿轮森林 */ new RiverStyle(1.0D, BED_TARGET, 2.5D, true, false),
        /* 2 黄铜荒漠 */ new RiverStyle(1.0D, BED_TARGET, 1.5D, false, true),
        /* 3 喷气沼泽 */ new RiverStyle(1.6D, 66.5D, 0.5D, false, false),
        /* 4 遗忘之川 */ new RiverStyle(TRUNK_WIDTH_SCALE, BED_TARGET, 1.5D, false, false) };

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
        // —— 1. 两级 Disk jitter 蜿蜒 ——
        final double[] disk = diskBuffer();
        double px = x;
        double pz = z;
        diskAt(worldSeed, SLOT_DISK_LARGE, LARGE_BEND_SCALE, x, z, disk);
        px += disk[0] * LARGE_BEND;
        pz += disk[1] * LARGE_BEND;
        diskAt(worldSeed, SLOT_DISK_SMALL, SMALL_BEND_SCALE, x, z, disk);
        px += disk[0] * SMALL_BEND;
        pz += disk[1] * SMALL_BEND;
        // —— 2. Voronoi border2（3×3 邻域，格点锚定偏移 ⇒ F1 在窗内数学精确）；
        // 法向（p'→最近中心）同时带出，供主干对齐门用（独立缓冲，防 disk buf 复用踩踏）——
        final double[] normal = NORMAL_BUF.get();
        final double c = borderC(worldSeed, px, pz, normal);
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
        if (!trunkAligned(worldSeed, x, z, normal[0], normal[1]) || trunk < SANZU_TRUNK_INNER) {
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
     * (x,z) 列的<b>床高</b>（double，heightCore 取 round；水面 68 口径）。风格档全部在此：
     * bed = bedTarget + n_bed(±amp)；浅滩档在河核列（s ≥ WET_MIN）且 shoalNoise&gt;阈值处
     * 抬到 67.5-68.2（干出浅滩）。
     */
    public static double bedAt(long worldSeed, int x, int z, int rosterIndex) {
        final RiverStyle style = styleForRosterIndex(rosterIndex);
        double bed = style.bedTarget + style.bedNoiseAmp
            * GTSRWorldgenHash.valueNoise(worldSeed ^ SALT_BED, x / BED_NOISE_SCALE, z / BED_NOISE_SCALE);
        if (style.shoals) {
            final double s = -strengthAt(worldSeed, x, z, rosterIndex);
            if (s >= WET_MIN) {
                final double shoal = unitNoise01(worldSeed ^ SALT_SHOAL, x / SHOAL_SCALE, z / SHOAL_SCALE);
                if (shoal > SHOAL_THRESHOLD) {
                    // 阈值以上线性映射到 [67.5, 68.2]：干出浅滩（h1=round → 68 ⇒ 不置水、露头水面之上）
                    bed = 67.5D + 0.7D * (shoal - SHOAL_THRESHOLD) / (1.0D - SHOAL_THRESHOLD);
                }
            }
        }
        return bed;
    }

    /**
     * (x,z) 列是否<b>置水资格</b>（纯函数；populate 与判据共用，无第二真值）：
     * s ≥ {@link #WET_MIN} 且非浅滩列；荒漠档再过 wetNoise &gt; {@link #WET_EDGE} 连续闸
     * （串珠断流）。列地表 h1&lt;68 的水面回填门在落块器（那里才有 h1）。
     */
    public static boolean wetAt(long worldSeed, int x, int z, int rosterIndex) {
        final RiverStyle style = styleForRosterIndex(rosterIndex);
        final double s = -strengthAt(worldSeed, x, z, rosterIndex);
        if (s < WET_MIN) {
            return false;
        }
        if (style.shoals && unitNoise01(worldSeed ^ SALT_SHOAL, x / SHOAL_SCALE, z / SHOAL_SCALE) > SHOAL_THRESHOLD) {
            return false; // 干出浅滩：该列不置水
        }
        if (style.wetGated && unitNoise01(worldSeed ^ SALT_WET, x / WET_NOISE_SCALE, z / WET_NOISE_SCALE) <= WET_EDGE) {
            return false; // 荒漠干谷段
        }
        return true;
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
     * 边界法向（{@code (nx,nz)}：p' 指向最近 Voronoi 细胞中心的单位向量，即 borderC 的梯度向）
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
     * 巨湖压力场（T5，plan §3.3）：第二 Voronoi（{@link #LAKE_INTERVAL} 格、
     * {@link #SALT_LAKE_CELL_X}/{@link #SALT_LAKE_CELL_Z} 盐域分离、偏移幅度同
     * {@link #CELL_JITTER}×间隔），压力 = dC/dN ∈ [0,1)——<b>低值在细胞中心</b>（与河网的
     * borderC 低值在边界相反）⇒ 每细胞腹地一座<b>离散巨湖</b>（c_lake&lt;{@link #LAKE_WATER_LEVEL}
     * 即湖水区，理论半径 ≈ 0.11×间隔/2 ≈ 66 格），而非沿边界成串的湖网。
     * <p>
     * <b>性能门（plan §9 ③）</b>：仅主干带内求值——trunk ≤ 0 一律返回 {@link #NO_LAKE} 哨兵
     * （第二 Voronoi 一个字都不算），"巨湖场仅主干带内激活"与性能约束同一条判。
     */
    public static double lakeAt(long worldSeed, int x, int z) {
        if (trunkAt(worldSeed, x, z) <= 0.0D) {
            return NO_LAKE;
        }
        final double px = x;
        final double pz = z;
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
     * 巨湖床高（double；{@link #LAKE_BED_TARGET}=63 ± 1 低频噪声 ⇒ 62-64，水面
     * {@code SEA_LEVEL}=68 ⇒ 水深 4-6，plan §3.3 口径）。heightCore 压低与判据共用。
     */
    public static double lakeBedAt(long worldSeed, int x, int z) {
        return LAKE_BED_TARGET + GTSRWorldgenHash
            .valueNoise(worldSeed ^ SALT_LAKE_BED, x / LAKE_BED_NOISE_SCALE, z / LAKE_BED_NOISE_SCALE);
    }

    /**
     * sanzu 群系指派的最小河强（plan §3.3「列满足 trunk&gt;0 且 s ≥ 0.7 且 h1 ≤ 68」的 0.7 档）。
     */
    public static final double SANZU_BIOME_STRENGTH = 0.7D;

    /**
     * <b>sanzu 列谓词（单点真值）</b>：trunk &gt; 0 且 s ≥ {@link #SANZU_BIOME_STRENGTH} 且
     * {@code heightAt ≤ SEA_LEVEL}（plan §3.3 的指派条件逐字）。两个消费面——
     * {@code ChunkProviderProsperityRuins.onPopulate} 的平面写入与本谓词是<b>同一份实现</b>
     * （写什么列、空气压缩机就认什么列，无第二真值）；{@code ProsperityAirLookup} 因链身份面
     * （GenLayer 链 4 家 selector）永远解析不到 populate 后置写入的第 5 群系而复用它。
     * <p>
     * rosterIndex 不带参是<b>构造不变量</b>而非省略：主干带内 strengthAt 的有效宽档 =
     * max(底档, {@link #TRUNK_WIDTH_SCALE})，对全部名册档（1.0/1.6）与缺席档（-1）同为 3.5
     * ⇒ 带内 s 与名册无关，-1 与生产档同解。h1 用 {@code ProsperityTerrainProfile.heightAt}
     * 整链（含河谷压低与巨湖压低——本谓词所在包引用 Profile 的先例 = {@link GTSRRiverPlacer}）。
     */
    public static boolean isSanzuColumn(long worldSeed, int x, int z) {
        if (trunkAt(worldSeed, x, z) <= 0.0D) {
            return false;
        }
        if (-strengthAt(worldSeed, x, z, -1) < SANZU_BIOME_STRENGTH) {
            return false;
        }
        return ProsperityTerrainProfile.heightAt(worldSeed, x, z) <= ProsperityTerrainProfile.SEA_LEVEL;
    }

    // ═══════════════════ Voronoi border2（RTG VoronoiCellOctave 同形） ═══════════════════

    /**
     * Worley 相对深度 c=(dN-dC)/dN ∈ [0,1)：0=细胞边界、→1=细胞腹地。最近/次近中心在
     * round(p/SEPARATION) 的 3×3 邻域内取——格点锚定偏移（|offset| ≤ 0.35×separation &lt; 半格）
     * 保证 F1/F2 在该窗内数学精确（RTG 用 floor+5×5 配 ±1 全格偏移；等价且更省）。
     * 细胞中心偏移 = splitmix64(cellSeed(seed, 格点, 盐)) 的两个高 53 位，各映 [-0.35, +0.35]×separation。
     *
     * @param normalOut 可空出参：非空时填 <b>p' 指向最近细胞中心的单位向量</b>（= borderC 梯度向，
     *                  即边界法向；T5 主干对齐门用）。为空时零开销。
     */
    static double borderC(long worldSeed, double px, double pz, double[] normalOut) {
        final int cellX = (int) Math.floor(px / SEPARATION + 0.5D);
        final int cellZ = (int) Math.floor(pz / SEPARATION + 0.5D);
        double dC = Double.POSITIVE_INFINITY;
        double dN = Double.POSITIVE_INFINITY;
        double bestEx = 0.0D;
        double bestEz = 0.0D;
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
                    dN = dC;
                    dC = d;
                    bestEx = ex;
                    bestEz = ez;
                } else if (d < dN) {
                    dN = d;
                }
            }
        }
        if (normalOut != null && dC > 0.0D) {
            normalOut[0] = bestEx / dC;
            normalOut[1] = bestEz / dC;
        }
        return (dN - dC) / dN;
    }

    /**
     * (x,z) 最近 Voronoi 细胞的格点坐标签名（判据/T5 用：分叉检测 = 2×2 列块内 ≥3 个不同
     * 签名即三叉点；主干带次级抑制也按它判"同一条主边界"）。签名含两个格点坐标，无碰撞口径。
     */
    public static long nearestCellHash(long worldSeed, int x, int z) {
        final double[] disk = diskBuffer();
        diskAt(worldSeed, SLOT_DISK_LARGE, LARGE_BEND_SCALE, x, z, disk);
        double px = x + disk[0] * LARGE_BEND;
        double pz = z + disk[1] * LARGE_BEND;
        diskAt(worldSeed, SLOT_DISK_SMALL, SMALL_BEND_SCALE, x, z, disk);
        px += disk[0] * SMALL_BEND;
        pz += disk[1] * SMALL_BEND;
        final int cellX = (int) Math.floor(px / SEPARATION + 0.5D);
        final int cellZ = (int) Math.floor(pz / SEPARATION + 0.5D);
        int bestX = cellX;
        int bestZ = cellZ;
        double best = Double.POSITIVE_INFINITY;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int gx = cellX + dx;
                final int gz = cellZ + dz;
                final double ex = gx * SEPARATION + cellOffset(worldSeed, gx, gz, SALT_CELL_X, SEPARATION) - px;
                final double ez = gz * SEPARATION + cellOffset(worldSeed, gx, gz, SALT_CELL_Z, SEPARATION) - pz;
                final double d = ex * ex + ez * ez;
                if (d < best) {
                    best = d;
                    bestX = gx;
                    bestZ = gz;
                }
            }
        }
        return ((long) bestX << 32) | (bestZ & 0xFFFFFFFFL);
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

    /** valueNoise → [0,1)。 */
    private static double unitNoise01(long seed, double x, double z) {
        return 0.5D + 0.5D * GTSRWorldgenHash.valueNoise(seed, x, z);
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

    /**
     * borderC 法向出参的独立缓冲（T5）：与 {@link #DISK_BUF} 分开——strengthAt 在读走法向前还会
     * 经 trunkAt/trunkAligned 触发下一次 disk 求值，共用一个数组会被踩踏。
     */
    private static final ThreadLocal<double[]> NORMAL_BUF = ThreadLocal.withInitial(() -> new double[2]);

    private static double[] diskBuffer() {
        return DISK_BUF.get();
    }

    /** disk 槽位：0=大弯、1=小弯、2=主干带（T5；盐在各槽构造期固定）。 */
    private static final int SLOT_DISK_LARGE = 0;
    private static final int SLOT_DISK_SMALL = 1;
    private static final int SLOT_DISK_TRUNK = 2;

    /** （seed, 槽, 尺度）处的 disk 位移 → out[0]/out[1]（单位圆盘内向量 × 幅度在外层乘）。 */
    private static void diskAt(long worldSeed, int slot, double scale, int x, int z, double[] out) {
        final HashMap<Long, OpenSimplexDisk[]> bySeed = DISK_CACHE.get();
        OpenSimplexDisk[] set = bySeed.get(worldSeed);
        if (set == null) {
            if (bySeed.size() >= DISK_CACHE_CAP) {
                bySeed.clear();
            }
            set = new OpenSimplexDisk[] { new OpenSimplexDisk(worldSeed ^ SALT_DISK_LARGE),
                new OpenSimplexDisk(worldSeed ^ SALT_DISK_SMALL), new OpenSimplexDisk(worldSeed ^ SALT_TRUNK) };
            bySeed.put(worldSeed, set);
        }
        set[slot].disk(x / scale, z / scale, out);
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
