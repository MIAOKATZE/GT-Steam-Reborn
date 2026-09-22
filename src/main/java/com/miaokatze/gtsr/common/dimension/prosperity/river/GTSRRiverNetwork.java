package com.miaokatze.gtsr.common.dimension.prosperity.river;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;

/**
 * <b>dim78 河流水系的河道算式（纯函数模型）</b>（dim1 P17 S-C；需求原话「增加河流」+
 * 「沼泽则是中等树，但<b>河网密集</b>，地势最平坦」的唯一实现体）。
 *
 * <p>
 * ═══ 本类只回答三个问题，且全部是 {@code (worldSeed, x, z, 身份档)} 的<b>纯函数</b> ═══
 * <ol>
 * <li>{@link #riverMask(long, int, int, double, double)}：这一列在不在河道骨架里（哪一族）、
 * 该族水道在这一档身份下是否"过水"；</li>
 * <li>{@link #holdsWater(int, int[], int)}：几何闸——在"下切 2 格、填 1 格水"的断面模型下，
 * 本列的水格会不会<b>侧向流走</b>（1.7.10 流体语义见下）；</li>
 * <li>断面 y 口径：{@link #waterY(int)} / {@link #bedY(int)} / {@link #airY(int)}。</li>
 * </ol>
 * 本类<b>零 {@code net.minecraft} 引用</b>（连 {@code Block} 都不 import）⇒ 离线判据可直接驱动
 * 同一段算式；真正落方块在 {@link GTSRRiverPlacer}。
 *
 * <p>
 * ═══ 为什么必须是"后置灌河"（P17-Q2 已裁决，这里是与之耦合的两条事实） ═══
 * <ul>
 * <li>{@code plan/tmp/p17-b/B-terrain-river-flora.md} 实证：在 {@code generateTerrain} 阶段写水会
 * 让"水在 stone 之上"的列<b>整列不铺表层、不换主体</b>——框架表层内核的门是
 * {@code GTSRChunkProviderBase:348}（只认主体方块）+ {@code :352}/{@code isAirOrEmpty:90-92}
 * （要求"上方是空气或空槽"），水既不是主体也不算空气 ⇒ 该列 top/filler/base 三段全不写。
 * 因此水只能在 Chunk 组装、表层替换<b>之后</b>写，唯一钩子是 {@code onPopulate}；</li>
 * <li>事后 carve 的形照 {@code WorldGenLakes}（{@code ChunkProviderGenerate:414-420} 是原版唯一的
 * 事后 carve 先例）：逐格 {@code world.setBlock(..., flags=2)} —— <b>flag 2 只送客户端、不触发邻块
 * 更新</b>（{@code World.java:487-489} 的 flag 语义 + {@code :547-563} 的 {@code markAndNotifyBlock}
 * 只在 {@code flag&1} 时才 {@code notifyBlockChange→notifyBlocksOfNeighborChange}）。本仓同口径的
 * 协议常量就是 {@code BlockSink.FLAG_POPULATE}，写入钳在本 chunk 由 {@code ChunkClampedSink} 强制。</li>
 * </ul>
 *
 * <p>
 * ═══ 水为什么不平铺外溢（几何闸的完整证明，逐条可对拍；{@code tools/dim1/P17RiverNetworkCheck}
 * 的 FLOW 组把这段证明变成真实运行读数，而不是注释） ═══
 * 1.7.10 的静态水 {@code Blocks.water}（{@code net.minecraft.block.BlockStaticLiquid}）有两个事实：
 * <ol>
 * <li>它<b>自己不流</b>：构造器 {@code BlockStaticLiquid.java:15} 对水 {@code setTickRandomly(false)}，
 * {@code updateTick}（{@code :50-95}）只在 {@code Material.lava} 分支里动作 ⇒ 源方块永不自发扩散；
 * 且 {@code ChunkClampedSink} 走 flag 2 ⇒ 我们自己写下的邻块不会去 {@code onNeighborBlockChange}
 * 它（{@code WorldGenLakes:108} 同一手法）。</li>
 * <li>但它<b>会被邻块变化叫醒</b>：{@code BlockStaticLiquid.java:27-35} 的
 * {@code onNeighborBlockChange} → {@code setNotStationary}（{@code :40-45}）把它换成
 * {@code Blocks.flowing_water}（meta 原样保留 = 0）并排一次 tick。这一步<b>无法避免</b>
 * （之后的结构/装饰/玩家行为都可能通知到它），所以必须证明"变成流水之后也不会走掉"：</li>
 * </ol>
 * 看 {@code BlockDynamicLiquid.updateTick}（{@code net.minecraft.block.BlockDynamicLiquid}）：
 * <ul>
 * <li>入口 {@code l = func_149804_e(...)}（{@code BlockLiquid.java:97-100}）= 本块 meta（非本族
 * 材质则 -1）。我们写的是 meta 0 ⇒ {@code l == 0} ⇒ 走 {@code :112-115} 的 {@code else} 分支 →
 * {@code func_149811_n}（{@code :20-24}）把方块<b>换回静态水</b>（id+1，meta 保持 0）——
 * <b>源方块自己会复位</b>；</li>
 * <li>同一次 tick 尾部还会试着外扩：{@code :117}（向下）要 {@code func_149809_q(below)} 为真，
 * 即下方材质不挡移动；{@code :135-168}（四向）对每个方向先 {@code func_149808_o} 打分，
 * 而 {@code :276-286} 的打分是「邻格是air/非源水」<b>且</b>「邻格下方能容流」才记 0，
 * 邻格本身固体则整个条件不成立（保持 1000）；最后 {@code func_149813_h}（{@code :172-189}）
 * 落地前还要过一次 {@code func_149809_q} —— 固体目标一律不写。</li>
 * </ul>
 * 于是"<b>四同层邻格挡移动 + 床格挡移动</b>"就是水格不动的<b>充分条件</b>。本类的断面恰好如此：
 * 水面 {@code y = H-1}、床 {@code y = H-2}（固体砂砾）、上方 {@code y = H} 是我们清出的空气
 * （{@code y+1} 只影响"能不能看见"，不参与 {@code func_149808_o}/{@code func_149809_q} 的判定）。
 * 剩下的问题就是<b>邻列的固体顶够不够高</b>：邻列若不属河道，其固体顶在自己的地表 {@code H(n)}；
 * 若属河道，其固体顶在 {@code H(n)-1}（床料），{@code H(n)} 那一格是空气。故：
 *
 * <pre>
 *   水格 p（y = H(p)-1）安全 ⇔ 四个邻列 q 满足
 *        q 属河道：H(q) ≥ H(p)      （否则 (q, H(p)-1) 是被清空的空气）
 *        q 属岸列：H(q) ≥ H(p)-1    （(q, H(p)-1) 落在固体列内即可）
 * </pre>
 *
 * 这正是 {@link #holdsWater(int, int[], int)}。两个边界情形值得单独说明（都是<b>可证</b>的，
 * 不是"看起来行"）：
 * <ul>
 * <li>{@code H(q) = H(p)} 且 q 也过水 ⇒ 邻格是同层<b>源水</b>（meta 0），
 * {@code func_149808_o:276} 的第二个条件 {@code (material != water || meta != 0)} 不成立 ⇒
 * 同层相邻源水互不流动 ⇒ 沼泽那种大片平坦段可以整条连通过水；</li>
 * <li>{@code H(q) = H(p)+1} 且 q 过水 ⇒ 邻格是 q 的床料（固体）⇒ 直接挡住；
 * 而 {@code H(q) = H(p)-1} 被本闸拒掉 ⇒ 本列<b>不过水</b>（留成干谷底），
 * 于是不会出现"高一级水格朝低一级的空气缺口扩散"那条需要算 {@code meta 1} 台阶的路径。</li>
 * </ul>
 * 直观说法：<b>只有"本列不比任何邻列的固体顶高"的水面才会被灌</b>——河面是一处处局部洼地的
 * 水平面，而不是贴着地形梯度往下淌的水。沼泽振幅档最低（{@code RELIEF_AMPLITUDE_BY_ROSTER[3]=0.40}，
 * 群系内 mean|Δh| 实测 0.059 ⇒ 相邻列几乎同高），所以沼泽的河<b>连得最长</b>，与"河网密集"同向；
 * 齿轮森林档 1.70（mean|Δh| 0.134）会有更多"跌水断点"，实测读数见
 * {@code plan/tmp/p17-sc2/SC2-RESULT.md} §3（B-DENSE / B-SPACING 两行）。
 * 实测读数（S-C2 收口后跑出来的第一手值）：沼泽每 chunk 通道列 18.543、水列 16.587，
 * 到最近水列 19.333 格；草原/森林同为中档 11.714/12.571 通道列、4.476/4.976 水列、
 * 29.213/29.520 格；荒漠 7.000 通道列、0 水列（只成干谷）。
 *
 * <p>
 * ═══ 骨架形状：两族"摆动等距线"，不是原版 GenLayer 河（{@code GenLayerRiver:38-40} 把河磨成 1 格宽） ═══
 * E-W 一族与 N-S 一族，各自等距 {@link #SPACING_EW}/{@link #SPACING_NS} 格一条线，线的位置
 * 沿走向被一条 long-wavelength 噪声摆动（{@link #MEANDER_AMPLITUDE} 格）。选它的理由：
 * <ul>
 * <li><b>纯函数 + 逐列独立</b>：每列只需自己的 (x,z) 与身份档，跨 chunk 天然连续，不需要
 * 任何共享链状态、不需要读邻 Chunk 方块（框架的跨 chunk 方块读取红线由 {@code SOURCE} 组源级钉）；</li>
 * <li>两族相交 ⇒ "河网"（不是单条河），密度、宽度、过水概率三项都能按身份档连续调，
 * 在群系边界处是<b>渐变收窄</b>而不是"河突然断了"（宽度按档乘子缩放；原版名册河是 1 格宽且
 * 不可按群系调宽）；</li>
 * <li>摆动用 {@link GTSRWorldgenHash#valueNoise} 唯一那份核（不自建噪声实现，L2 红线；
 * {@code tools/dim1/HeightHashSingleSourceCheck} 的 C 组把"噪声核定义处数"钉死为 1）。</li>
 * </ul>
 *
 * <p>
 * ═══ 身份档（写法对齐 {@code RELIEF_AMPLITUDE_BY_ROSTER} 与 {@code StructureBurialTiers}） ═══
 * 下标 = L1 维内名册下标（0 锈蚀草原 / 1 齿轮森林 / 2 黄铜荒漠 / <b>3 起雾沼泽</b>）。
 * 两张表都是<b>纯乘子/纯概率</b>：本类没有任何"身份 == 某群系 ⇒ 抑制"的等值判断
 * （plan §2 第 8 条），荒漠"只留干谷"是靠 {@link #RIVER_WET_BY_ROSTER}{@code [2] = 0.0}
 * 这个档值实现的。档缺失（身份不可得 = {@code -1}）一律走 {@link #DEFAULT_RIVER_BAND}/
 * {@link #DEFAULT_RIVER_WET}。
 */
public final class GTSRRiverNetwork {

    // ————————————————————————— 河道骨架参数 —————————————————————————

    /** E-W 族相邻水道的间距（格）。两条族共用同一量级，实际平均最近水道距离 ≈ 52 格。 */
    public static final int SPACING_EW = 112;

    /** N-S 族相邻水道的间距（格）；与 {@link #SPACING_EW} <b>不同值</b>，避免两族交点成规则网格。 */
    public static final int SPACING_NS = 96;

    /** 摆动噪声的走向波长（格）：一条水道在顺流方向上的蛇行尺度。 */
    public static final double MEANDER_SCALE = 260.0D;

    /**
     * 摆动幅度（格，正负各一半）。取 {@code < min(SPACING)/2 - 2*半宽档最大乘子×基准半宽} ⇒
     * 相邻平行线永不交叉（实测断言在 {@code P17RiverNetworkCheck} 的 SHAPE 组）：
     * {@code 112/2 - 26 - 2*(2.10*1.00) = 8.8 > 0}。
     */
    public static final int MEANDER_AMPLITUDE = 26;

    /** 基准半宽（格，档乘子 1.0 时的河道半宽）；实际半宽 = 本值 × 身份档乘子。 */
    public static final double BASE_HALF_WIDTH = 2.10D;

    /** 族编号：E-W（横列，垂直坐标是 z）。 */
    public static final int FAMILY_EW = 0;

    /** 族编号：N-S（纵列，垂直坐标是 x）。 */
    public static final int FAMILY_NS = 1;

    /** {@link #riverMask} 的位：E-W 族在本列成道。 */
    public static final int MASK_CHANNEL_EW = 1;

    /** {@link #riverMask} 的位：N-S 族在本列成道。 */
    public static final int MASK_CHANNEL_NS = 2;

    /** {@link #riverMask} 的位：E-W 族本条水道按档过水（否则只是干谷）。 */
    public static final int MASK_WET_EW = 4;

    /** {@link #riverMask} 的位：N-S 族本条水道按档过水。 */
    public static final int MASK_WET_NS = 8;

    /** 过水位掩码（判"本列有没有资格过水"时用）。 */
    public static final int MASK_WET_ANY = MASK_WET_EW | MASK_WET_NS;

    /** 成道位掩码。 */
    public static final int MASK_CHANNEL_ANY = MASK_CHANNEL_EW | MASK_CHANNEL_NS;

    // ————————————————————————— 断面几何（"下切 2 格、填 1 格水"） —————————————————————————

    /**
     * 下切总深度（格）：地表 y 那一格清成空气 + 其下一格过水 ⇒ 河面比两岸天然地面低 1 格。
     * 计划 §1-S-C 的原话口径「河床在 populate 阶段下切 2 格、填 1 格水」。
     */
    public static final int CUT_DEPTH = 2;

    /** 水格相对地表的降格数（{@code waterY = H - 1}）。 */
    public static final int WATER_DROP = 1;

    /** 床料相对地表的降格数（{@code bedY = H - 2}，恰在水格之下）。 */
    public static final int BED_DROP = 2;

    /** 干谷（档=不过水，或几何闸未过）时床料的降格数：下切 1 格后铺床，谷底仍是固体。 */
    public static final int DRY_BED_DROP = 1;

    // ————————————————————————— 身份档表 —————————————————————————

    /**
     * 河道带宽乘子表：下标 = L1 维内名册下标（0 草原 / 1 森林 / 2 荒漠 / 3 沼泽）。
     * 值是 {@link #BASE_HALF_WIDTH} 的乘子，<b>0 = 本档不成任何河道</b>（本表四档都不为 0——
     * 荒漠走的是"有谷无水"，抑制位放在 {@link #RIVER_WET_BY_ROSTER}）。
     * <p>
     * 需求原话只有沼泽那半句（「河网密集」）与简报的三档形状（沼泽最密 / 森林·草原中 / 荒漠最稀），
     * 故草原与森林取同一中档、沼泽取满档、荒漠取 0.25（半宽 0.525 格 ⇒ 实测约 1 格宽的细谷）。
     */
    public static final float[] RIVER_BAND_BY_ROSTER = { 0.55F, 0.55F, 0.25F, 1.00F };

    /**
     * 带宽默认档（身份不可得 = 该维名册零配槽的 EMPTY 降级态，或未装配 L1 账本的离线 JVM）。
     * <b>取 0 = 一条河道都不成</b>，与 S-A 的振幅默认档 1.0、S-D 的埋深默认档 0.0 是同一条纪律：
     * "未装配 L1 账本" 与 "改造前" 两个口径必须重合——于是所有不 bootstrap 名册的既存判据读数
     * 天然一字不动，本片的新增面只在真有身份面时生效（plan §2 第 8 条；该档在生产路径取不到，
     * P17-A 实测 524288 chunk 上 -1 = 0 次）。
     */
    public static final float DEFAULT_RIVER_BAND = 0.0F;

    /**
     * 过水概率表（同下标口径）：某条水道<b>整条</b>按本档概率被灌上水，未中的那条只留干谷。
     * <ul>
     * <li>沼泽 {@code 1.00} —— 凡成道皆过水 ⇒ "河网密集"；</li>
     * <li>草原/森林 {@code 0.55} —— 约一半水道有水；</li>
     * <li><b>荒漠 {@code 0.00} —— 一条都不过水，只留干谷</b>：这就是 plan §2 第 8 条要的
     * "抑制写成档值 0"，本类因此不需要任何身份等值判断；</li>
     * <li>默认档 {@code 0.0}：与 {@link #DEFAULT_RIVER_BAND} 同一条纪律（未装配身份面 ⇒ 不新增水系），
     * 且它<b>永远不该</b>被读成"荒漠档"——荒漠的抑制位在表内第 3 格，是档值不是身份判断。</li>
     * </ul>
     * 概率掷在<b>水道编号</b>上（{@link #wetFamily}），不逐列掷 ⇒ 不会出现"一条河里隔几格一个水塘"
     * 的斑点状水系。
     */
    public static final float[] RIVER_WET_BY_ROSTER = { 0.55F, 0.55F, 0.00F, 1.00F };

    /** 过水概率默认档（身份不可得 ⇒ 不过水，理由同 {@link #DEFAULT_RIVER_BAND}）。 */
    public static final float DEFAULT_RIVER_WET = 0.0F;

    /** 摆动场盐（族间/用途间域分离；与 {@code ProsperityTerrainProfile.CHAIN_SEED_SALT} 无关）。 */
    public static final long SALT_MEANDER = 0x5249FEE1L;

    /** 过水掷骰盐。 */
    public static final long SALT_WET = 0x5249FE2AL;

    private GTSRRiverNetwork() {}

    // ————————————————————————— 档表出口（形状照 reliefAmplitudeForRosterIndex / weightForRosterIndex）
    // —————————————————————————

    /** 名册下标 → 带宽乘子（越界/缺席一律默认档，<b>无身份等值判断</b>）。 */
    public static double bandForRosterIndex(int index) {
        return index >= 0 && index < RIVER_BAND_BY_ROSTER.length ? RIVER_BAND_BY_ROSTER[index] : DEFAULT_RIVER_BAND;
    }

    /** 名册下标 → 过水概率（同上）。 */
    public static double wetForRosterIndex(int index) {
        return index >= 0 && index < RIVER_WET_BY_ROSTER.length ? RIVER_WET_BY_ROSTER[index] : DEFAULT_RIVER_WET;
    }

    // ————————————————————————— 断面 y —————————————————————————

    /** 过水列的空气格 y（地表那一格被清成空气）。 */
    public static int airY(int surfaceY) {
        return surfaceY;
    }

    /** 过水列的水面 y。 */
    public static int waterY(int surfaceY) {
        return surfaceY - WATER_DROP;
    }

    /** 过水列的床料 y（恰在水面之下 ⇒ 判据要钉的"水↔床"成层关系）。 */
    public static int bedY(int surfaceY) {
        return surfaceY - BED_DROP;
    }

    /** 干谷列的床料 y（谷底固体）。 */
    public static int dryBedY(int surfaceY) {
        return surfaceY - DRY_BED_DROP;
    }

    // ————————————————————————— 骨架 —————————————————————————

    /**
     * 本列的河道位掩码（成道位 + 过水位）。<b>纯函数</b>，同一 (seed,x,z,档) 在任何 chunk、
     * 任何调用次序下同一个值 ⇒ 跨 chunk 连续、双跑逐位一致。
     *
     * @param bandScale 本列身份档的带宽乘子（{@link #bandForRosterIndex}）
     * @param wetRate   本列身份档的过水概率（{@link #wetForRosterIndex}）
     */
    public static int riverMask(long worldSeed, int x, int z, double bandScale, double wetRate) {
        final double half = BASE_HALF_WIDTH * bandScale;
        if (half <= 0.0D) {
            return 0;
        }
        int mask = 0;
        // E-W 族：垂直坐标 z、走向坐标 x
        int line = Math.floorDiv(z, SPACING_EW);
        if (Math.abs(z - centreAlongRow(worldSeed, FAMILY_EW, line, x)) <= half) {
            mask |= MASK_CHANNEL_EW;
            if (wetFamily(worldSeed, FAMILY_EW, line, wetRate)) {
                mask |= MASK_WET_EW;
            }
        }
        // N-S 族：垂直坐标 x、走向坐标 z
        line = Math.floorDiv(x, SPACING_NS);
        if (Math.abs(x - centreAlongCol(worldSeed, FAMILY_NS, line, z)) <= half) {
            mask |= MASK_CHANNEL_NS;
            if (wetFamily(worldSeed, FAMILY_NS, line, wetRate)) {
                mask |= MASK_WET_NS;
            }
        }
        return mask;
    }

    /** 该列是否落在任一族的河道里（{@link #riverMask} 的成道位投影）。 */
    public static boolean isChannel(int mask) {
        return (mask & MASK_CHANNEL_ANY) != 0;
    }

    /** 该列所在水道是否被灌上水（过水位投影；最终是否真灌水还要过 {@link #holdsWater}）。 */
    public static boolean wantsWater(int mask) {
        return (mask & MASK_WET_ANY) != 0;
    }

    /**
     * E-W 族第 {@code line} 条水道在走向坐标 {@code alongX} 处的中心线 z 坐标。
     * 摆动取 {@code valueNoise(场种子, alongX/MEANDER_SCALE, line)}——整数 z 使 smoothstep 权重
     * 为 0 ⇒ 只用第 {@code line} 行格点，等价于"每条水道一维独立蛇行"，且相邻水道的摆动互不相关。
     */
    public static double centreAlongRow(long worldSeed, int family, int line, int alongX) {
        final int spacing = family == FAMILY_EW ? SPACING_EW : SPACING_NS;
        return spacing * (line + 0.5D) + meander(worldSeed, family, line, alongX);
    }

    /** N-S 族的同款（走向坐标是 z，返回中心线 x）。 */
    public static double centreAlongCol(long worldSeed, int family, int line, int alongZ) {
        return centreAlongRow(worldSeed, family, line, alongZ);
    }

    /** 摆动位移（格，±{@link #MEANDER_AMPLITUDE}）。 */
    private static double meander(long worldSeed, int family, int line, int along) {
        final long fieldSeed = GTSRWorldgenHash.cellSeed(worldSeed, family, 0, SALT_MEANDER);
        return MEANDER_AMPLITUDE * GTSRWorldgenHash.valueNoise(fieldSeed, along / MEANDER_SCALE, line);
    }

    /**
     * 第 {@code line} 条水道是否过水：对<b>水道编号</b>（不是列）掷一次档概率。
     * {@code wetRate >= 1} 与 {@code <= 0} 两端的短路是刻意的——它们让"满档"与"荒漠零档"
     * 两条最常见的取值不发哈希，也保证档值为 0 时<b>不可能</b>因哈希运气而漏出水。
     */
    public static boolean wetFamily(long worldSeed, int family, int line, double wetRate) {
        if (wetRate <= 0.0D) {
            return false;
        }
        if (wetRate >= 1.0D) {
            return true;
        }
        final long rollSeed = GTSRWorldgenHash.cellSeed(worldSeed, line, family, SALT_WET);
        // unitNoise ∈ [-1,1) ⇒ 映到 [0,1)
        final double unit = 0.5D + 0.5D * GTSRWorldgenHash.unitNoise(rollSeed, 0, 0);
        return unit < wetRate;
    }

    // ————————————————————————— 几何闸（水不外溢的充分条件） —————————————————————————

    /**
     * 本列水格是否被四邻的固体顶围住（完整证明见类注释"水为什么不平铺外溢"一节）。
     *
     * @param surfaceY      本列天然地表 y（= {@code ProsperityTerrainProfile.heightAt}）
     * @param neighbourY    四个邻列的天然地表 y，顺序固定为 -x / +x / -z / +z
     * @param neighbourMask 四个邻列的 {@link #riverMask}（同顺序；只用其成道位）
     * @return {@code true} = 可以灌水（水格 {@code y = surfaceY-1} 的四同层邻格与床格都是固体）
     */
    public static boolean holdsWater(int surfaceY, int[] neighbourY, int[] neighbourMask) {
        for (int i = 0; i < 4; i++) {
            // 邻列属河道 ⇒ 它的地表那一格被清空，固体顶在 H-1，需要 H(nb) ≥ self；
            // 邻列是岸 ⇒ 固体顶在 H，只需 H(nb) ≥ self-1。
            final int required = (neighbourMask[i] & MASK_CHANNEL_ANY) != 0 ? surfaceY : surfaceY - 1;
            if (neighbourY[i] < required) {
                return false;
            }
        }
        return true;
    }
}
