package com.miaokatze.gtsr.common.dimension.prosperity;

import java.util.HashMap;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerRosterFace;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * 繁荣维度噪声高度纯函数（dim1 S4b，plan §1.1 :59-65 列有、分派表并入 S4b；
 * <b>P17 S-A 起按群系分振幅</b>）。
 * <p>
 * <b>关键契约（evolver R7 / plan §3.1 高度红线）</b>：{@link #heightAt(long, int, int)} 是
 * ChunkProviderProsperityRuins 地形填充、古代城（CityPlanner/CityPlan）、残缺机器、outpost、城外废墟
 * 与 {@code /gtsr structure} 指令共用的<b>同一</b>纯函数——同 seed 同坐标必得同一高度，跨 chunk 接缝
 * 一致性由此保证；城市侧<b>禁止读任何方块</b>。
 * <p>
 * <b>P17 S-A 群系化后这条红线为什么仍然成立（决定性证法，不是口头承诺）</b>：身份取自
 * {@link GTSRGenLayerRosterFace}——一条<b>只读 (chainSeed, 坐标) 的纯函数</b>（内存分配账本 +
 * GenLayer 整数链），本类<b>不</b>引用 {@code World}/{@code Chunk}/{@code getBlock}/{@code BiomeGenBase}
 * 任何一处（源级断言：{@code tools/dim1/P17TerrainReliefCheck} REDLINE 组）。四族仍走同一个
 * {@link #heightAt(long, int, int)} 出口 ⇒ 同源由构造保证；身份面取链的 <b>coarse 层</b>
 * （1:4，{@code biomeAtCoarse}），与 {@code GTSRBiomeAuthority.ordinalAt} / {@code CityPlanner.bandIndexAt}
 * 同格同值（行为级逐点对拍：同判据 SOURCE 组），故结构接地与地形永不出两套高度。
 * <p>
 * <b>P18 T3（plan §3.6）振幅核平滑</b>：P17 的档表取数是<b>逐列直接下标</b>——相邻粗格群系不同时
 * 振幅乘子在 4 方块内硬跳（最坏 森1.70 ↔ 沼0.40 = 4.25×），是沼泽悬崖的主因。本类把该取数换成
 * {@link #ampAt(long, int, int)}：同一条 coarse 身份链的粗格档值经 11×11 smoothstep 紧支核
 * （半径 5 粗格 = 20 方块）加权成<b>连续场</b>，硬跳变摊成 40-60 格渐变；粗格档值按 (seed, 粗格)
 * 线程私有缓存（每粗格只求值一次链），取值仍是档表的凸组合 ⇒ 默认口径（全 1 档）与改造前
 * <b>逐位相同</b>，值域/纯函数/四族同源契约不变。
 * <p>
 * 参数口径（02 §2.1 噪声参数表裁剪）：
 * <ul>
 * <li>起伏幅度 ×1.2（02 §2.1 RELIEF_MULT，账本"主起伏 +20%"）；</li>
 * <li>海平面 {@link #SEA_LEVEL}=68（02 §2.1 海拔抬升 +5；<b>v1.20.39 T4 起单点常量</b>），
 * 基准面 BASE=70 使无河地表绝大多数在"海平面"之上——自然水只经河流场
 * （{@link GTSRVoronoiRiverField}）的 populate 后置回填出现，本类只在 heightCore 里做
 * 河谷压低（群系化只改<b>振幅</b>，不改 BASE/域盐/clamp，也不给任何群系加海拔偏移）；</li>
 * <li><b>P17 S-A 改判</b>：02 §1.1「四群系高度性格」不再是"全维度统一缓丘 + 幅度调制"一档，
 * 而是低频 zone 乘子（0.6..1.4，保 C0 连续）之上再乘一档 {@link #RELIEF_AMPLITUDE_BY_ROSTER}
 * （seed 作用域查表）——原 S4b 注释里"per-chunk 群系哈希跳变不可作为高度输入"的否决理由，
 * 针对的是<b>读 Chunk byte 平面 / voronoi 细面</b>那两条取数路（跨 chunk 接缝与城市侧不可同源）；
 * 本类改用<b>零世界读取的 coarse 纯函数面</b>后该理由不再成立，四族同源与接缝一致性都不再受损。
 * 实测四群系 sd 与严格序见 {@code plan/tmp/p17-sa/SA-RESULT.md}（机检：
 * {@code P17TerrainReliefCheck} 的 RELIEF 组把偏序钉死）。</li>
 * </ul>
 * <p>
 * 实现为自研 value noise（splitmix64 哈希 + smoothstep 双线性），本类自身<b>零 {@code net.minecraft}
 * import</b>（身份取数经 {@link GTSRGenLayerRosterFace} 转发，出参只是 int）——离线
 * RasterSink/确定性自检驱动（tools/dim1）可无游戏运行时调用；账本为空（未配槽的离线 JVM）时
 * 身份 = {@link GTSRGenLayerRosterFace#NO_IDENTITY} ⇒ 走 {@link #DEFAULT_RELIEF_AMPLITUDE}
 * = 1.0，与 P17 之前的高度<b>逐位相同</b>（不是巧合：默认档特意取 1.0，使"未装配"与"改造前"
 * 两个口径重合，见 {@link #reliefAmplitudeForRosterIndex(int)} 注释）。
 * <p>
 * <b>P3 起噪声内核不再在本类内实现</b>（plan §5 P3）：{@code valueNoise/floorDiv/smooth/hashUnit}
 * 四方法与 {@code ShatteredTerrainProfile} 的同名件 {@code diff} 实测逐字符等价，已并到
 * {@link GTSRWorldgenHash#valueNoise(long, double, double)} 唯一出处；本类只剩 dim78 专属的
 * <b>高度参数域</b>（波长/幅度/域盐/clamp + P17 的群系振幅档表）。
 */
public final class ProsperityTerrainProfile {

    /** 基准高度（02 §2.1 海平面 68 之上 +2，保证无水地表以陆地为主）。 */
    public static final int BASE_HEIGHT = 70;

    /**
     * <b>基准水位（v1.20.39 T4 起全局单点引用；v1.20.40 P19 §C 起 = 巨湖/无河区口径）</b>：
     * plan §3.2「新增 SEA_LEVEL=68 于 ProsperityTerrainProfile，全局单点引用」。口径 = 水面
     * 所在格线上方：置水最高格 {@code y = SEA_LEVEL - 1 = 67}。<b>河道水面已改走</b>
     * {@link GTSRVoronoiRiverField#poolLevelAt} 的分段池水位（水位阶梯+瀑布，P19 §C）——
     * 本常量保留为巨湖回填（{@code ChunkProviderProsperityRuins.fillSanzuLakes}，批2 重构）
     * 与 {@code isSanzuColumn} 判定（h1≤68）的基准水位，不要与池水位互相改写。
     */
    public static final int SEA_LEVEL = 68;

    /** 起伏倍率（02 §2.1 RELIEF_MULT = 1.2，主起伏 +20%）。 */
    public static final double RELIEF_MULT = 1.2D;

    /** 高度钳制下界（防极端调制穿 y=20 裂隙带以下的观感）。 */
    private static final int MIN_HEIGHT = 40;
    /** 高度钳制上界（城变体最高 16 层 + 顶饰留出余量）。 */
    private static final int MAX_HEIGHT = 110;

    /**
     * dim78 def.seedSalt（身份链域分离盐）。
     * <p>
     * 与 {@code CommonProxy} 构造 def 处、{@code CityPlanner.PROSPERITY_SEED_SALT} 同值的<b>第三处</b>
     * 字面量登记：前两处是既存事实，本类要自建同一条链就必须同盐。三处同值不再靠人眼核——
     * {@code tools/dim1/P17TerrainReliefCheck} 的 SALT 组同时钉「源级字面量相等」与
     * 「行为级同格同值（本类解析出的名册下标 == {@code CityPlanner.bandIndexAt}）」。
     */
    public static final long CHAIN_SEED_SALT = 0x50524F53L;

    /**
     * 群系地势振幅档表（<b>P17 S-A 新增；seed 作用域查表</b>，写法与形状照
     * {@code ProsperityWorldGenerator.MACHINE_WEIGHTS}/{@code SCATTER_WEIGHTS}）：
     * 下标 = L1 维内名册下标（{@link GTSRBiomeAuthority.BiomeId#rosterIndex()}，
     * 注册顺序 锈蚀草原 / 齿轮森林 / 黄铜荒漠 / 起雾沼泽）→ 振幅乘子。
     * <p>
     * 需求原话（成功判据唯一来源）：「青铜森林……地势也相对更起伏」「平原则是矮树」「沼泽则是
     * ……地势最平坦」「沙漠则是没有树，地势相对更平坦一些」⇒ 偏序 <b>森 &gt; 原 ≥ 沙 &gt; 沼</b>。
     * <p>
     * <b>v1.20.41 起上述偏序的「原 ≥ 沙」一支废止</b>，旧句原文保留在上一行不删。覆盖来源是本轮需求 5
     * 原话「黄铜荒漠过于单调了……且地势介于锈蚀草原和齿轮森林之间」——它与更早一轮的「沙漠……地势相对
     * 更平坦一些」直接冲突，按最新指令取新，偏序改钉 <b>森 &gt; 沙 &gt; 原 &gt; 沼</b>（荒漠档
     * 0.80 → 1.30，实测聚合 sd 森 13.145 / 沙 7.688 / 原 6.518 / 沼 1.908）。需求 2 的其余两支
     * （森林最起伏、沼泽最平坦）不变。裁决与授权见 {@code plan/p20-plan.md} §13 C2，判据侧同步重钉见
     * {@code tools/dim1/P17TerrainReliefCheck}。
     * <p>
     * 取档前的事实（{@code plan/tmp/p17-b/B-terrain-river-flora.md} §1.3）：改前四群系 sd 为
     * 森 5.932 / 原 5.903 / 沼 6.607 / 沙 <b>6.746</b> —— 与需求方向<b>完全相反</b>，
     * 逐 seed 严格序命中 0/10；那个差异只是两个无关噪声场在小窗内的相关涨落，不是分化。
     * <p>
     * 本档表的设计约束（实测值见 {@code plan/tmp/p17-sa/SA-RESULT.md}）：
     * <ul>
     * <li><b>四档算数均值精确 = 1.0</b>（(1.10+1.70+0.80+0.40)/4）⇒ 全域期望振幅与改前同阶，
     * 本片只"差异化"，不借机整体加大起伏（那是另一条需求，未获授权）；
     * <b>v1.20.41 起该不变量被需求本身废止</b>：需求 4「齿轮森林地势起伏再更大一些」与需求 5 的荒漠
     * 地势子句联合要求整体加大，荒漠档 0.80 → 1.30 后均值 = <b>1.125</b>（(1.10+1.70+1.30+0.40)/4）。
     * 锚没有取消、只是换值：{@code P17TerrainReliefCheck} 现钉 1.125，任何一档再动而未同步该常量即当场红
     * ⇒ 抓力从"不得整体加大"变为"不得在档表之外整体加大"；旧口径原文保留在本行上方不删；</li>
     * <li>乘子作用位是 <b>zone 乘子之外</b>的独立一档（见 {@link #heightAtWithReliefTier(long, int, int, int)}），
     * 保留 zone 的 0.6..1.4 连续调制，于是同一群系内部仍有"平缓区/起伏区"的长尺度变化；</li>
     * <li>沼泽档最低但<b>不为 0</b>——0 会造出绝对平坦面（与"最平坦"不是同一件事），
     * 且 S-C 的河网要在沼泽里下切河床，需要固体面本身是可变的；</li>
     * <li><b>不触钳制</b>：档形若把森林推到 {@code MIN_HEIGHT}/{@code MAX_HEIGHT} 之外，
     * sd 会被截平（实测同窗下森林 1.90 档有 1259 列触到 y=40 下界，而当前档<b>零触界</b>，
     * 高度域 [41,104] ⊂ [40,110]）——这是选 1.70 而不是 1.90 的决定性理由。</li>
     * </ul>
     * <p>
     * <b>v1.20.39 T5 第 5 元 0.38（plan §3.3「RELIEF=0.38，沼泽级 0.35-0.40 带内取值」）</b>：
     * 遗忘之川不进 selector 等权名册 ⇒ GenLayer 链身份面只产生 0..3，本表第 5 元<b>生产路径取不到</b>
     * （与档表族"4→5 + 长度一致"约定一致，判据/后续消费面用）；"四档算数均值精确 = 1.0"的 P17
     * 不变量按 selector 口径（前 4 元）保持不变。
     */
    public static final double[] RELIEF_AMPLITUDE_BY_ROSTER = { 1.10D, 1.70D, 1.30D, 0.40D, 0.38D };

    /**
     * 默认档（身份不可得 = {@link GTSRGenLayerRosterFace#NO_IDENTITY}，即该维名册零配槽的 EMPTY
     * 降级态，或未装配的离线 JVM）。
     * <p>
     * 取 1.0 是刻意的：它使降级/未装配口径与 P17 改造<b>之前</b>的高度逐位相同，于是
     * ①身份档缺失时"走档表默认档"这条纪律不需要任何等值判断（plan §2 第 8 条），
     * ②离线判据的 BASE 对拍在没有真实账本的 JVM 里天然不红。生产路径取不到该档
     * （实测 524288 chunk 上 -1 = 0 次），故它不是第二个高度真值。
     */
    public static final double DEFAULT_RELIEF_AMPLITUDE = 1.0D;

    private ProsperityTerrainProfile() {}

    /**
     * 名册下标 → 振幅乘子（与 {@code ProsperityWorldGenerator.weightForRosterIndex} 同一形状：
     * 越界/缺席一律回退默认档，<b>不</b>写"某个群系 ⇒ 抑制"的身份等值判断）。
     */
    public static double reliefAmplitudeForRosterIndex(int index) {
        return index >= 0 && index < RELIEF_AMPLITUDE_BY_ROSTER.length ? RELIEF_AMPLITUDE_BY_ROSTER[index]
            : DEFAULT_RELIEF_AMPLITUDE;
    }

    // ═══ P18 T3（plan §3.6）：群系间振幅硬切换 → 11×11 smoothstep 核平滑连续场 ═══
    //
    // 改造前 {@code heightAtWithReliefTier} 对 RELIEF 档表做<b>逐列直接下标</b>查询：相邻粗格群系不同时
    // 振幅乘子在 4 方块内硬跳（最坏 森1.70 ↔ 沼0.40 = 4.25×），是沼泽悬崖的主因。本节把取数换成
    // {@link #ampAt(long, int, int)}：粗格档值经 smoothstep 紧支核加权成连续场，硬跳变摊成 40-60 格渐变。
    // <b>契约全部不动</b>：heightAt 签名、同 seed 纯函数、值域 [40,110] 钳制、四族共用同一出口；
    // 只改"群系振幅"这一档的内部来源（zone 乘子 / 波长 / 域盐 / BASE / clamp 原样）。

    /** smoothstep 核半径（<b>粗格</b>单位；plan §3.6 定 5 ⇒ 核直径 11 粗格 = 44 方块）。 */
    private static final int AMP_KERNEL_RADIUS = 5;

    /**
     * 每线程每 seed 的粗格 <b>amp 终值</b>缓存上限（防内存无限增长）。超限<b>整表清空</b>：
     * amp 是 (seed, 粗格) 的纯函数，清空后按需重算值不变（无 LRU 顺序复杂度，也不引入第二真值）。
     * <b>v1.20.40（P19 U8）两处改判，读数零变化</b>：
     * ① 本表从"缓粗格档值、逐列 69 次查表加权"改为<b>缓粗格 amp 终值</b>——ampAt 的核中心 =
     * 列所在粗格、核偏移固定、各粗格档值只依赖 (seed, 该粗格)，故同一粗格内所有列的 amp
     * <b>数学恒等</b>（旧代码只是对每列重复求同一个和），缓存终值与旧逐列加权<b>逐位相同</b>
     * （MicroProfile 实测 2.09µs/列 → 0.03µs/列）；
     * ② 容量 16384 → 65536：实测（GenBenchCheck 4096 chunk 连续域）16384 档每 seed 触整清，
     * 整清后的再取数全走身份短命链（≈1.8µs/次，见 {@link #chainRosterIndexAt}），表现为
     * chunk 耗时锯齿；65536 格 = 1024×1024 方块工作集，单机连续生成域内零淘汰，仍是固定上限
     * （淘汰有界纪律不变）。
     */
    private static final int AMP_CELL_CACHE_CAP = 65536;

    /** 核参与格点（i²+j²&lt;r²；d=r 的格点 smoothstep 权重恰为 0，不入表）——共 69 点。 */
    private static final int[] AMP_KERNEL_DX;
    private static final int[] AMP_KERNEL_DZ;
    /**
     * 归一核权（Σw = 1）。<b>全 1 档场 ⇒ ampAt == 1.0（构造性凭据）</b>：未装配/EMPTY 降级 JVM
     * （身份恒 -1 → 默认档 1.0）下，核平滑后的振幅与改造前<b>逐位相同</b>，P17 之前的
     * "默认口径"语义原样保留。
     */
    private static final double[] AMP_KERNEL_W;

    static {
        final int r = AMP_KERNEL_RADIUS;
        final int rr = r * r;
        int n = 0;
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                if (i * i + j * j < rr) {
                    n++;
                }
            }
        }
        AMP_KERNEL_DX = new int[n];
        AMP_KERNEL_DZ = new int[n];
        AMP_KERNEL_W = new double[n];
        double wSum = 0.0D;
        int k = 0;
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                final int d2 = i * i + j * j;
                if (d2 >= rr) {
                    continue;
                }
                // plan §3.6：d = √(i²+j²) ≤ 5 参与，w = s(1 - d/5)、s(t) = t²(3-2t)
                final double t = 1.0D - Math.sqrt(d2) / r;
                final double w = t * t * (3.0D - 2.0D * t);
                AMP_KERNEL_DX[k] = i;
                AMP_KERNEL_DZ[k] = j;
                AMP_KERNEL_W[k] = w;
                wSum += w;
                k++;
            }
        }
        for (k = 0; k < n; k++) {
            AMP_KERNEL_W[k] /= wSum;
        }
    }

    /**
     * 粗格 <b>amp 终值</b>缓存（线程私有：GenLayer 链 + vanilla IntCache 均<b>非线程安全</b>，
     * 现状的"每调用一条短命链"纪律本来就是单线程域——缓存沿用同一纪律且以 ThreadLocal 结构上
     * 杜绝跨线程共享）。外层 key = worldSeed（离线判据会在一个 JVM 里扫多个 seed），
     * 内层 key = (cellX, cellZ) 打包 long，值为该粗格的 ampAt 终值（P19 U8 起由"档乘子"改判，
     * 见 {@link #AMP_CELL_CACHE_CAP} 注释——同一粗格内所有列 amp 恒等，缓存终值逐位等价）。
     */
    private static final ThreadLocal<HashMap<Long, HashMap<Long, Double>>> AMP_CELL_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    /**
     * (x,z) 列的<b>群系振幅连续场</b>（P18 T3 新增，plan §3.6）：包含 (x,z) 的 relief 粗格
     * （1:4，{@link GTSRGenLayerChain#COARSE_BLOCK_SHIFT}，与身份面同一条 coarse 层）为核中心，
     * 11×11 邻域 smoothstep 紧支核（{@link #AMP_KERNEL_RADIUS}=5 粗格）加权各粗格的
     * {@link #RELIEF_AMPLITUDE_BY_ROSTER} 档值，Σw 归一。
     * <p>
     * <b>纯函数与取数纪律（与改造前路径完全同源）</b>：
     * <ul>
     * <li>单粗格档值 = {@code reliefAmplitudeForRosterIndex(chainRosterIndexAt(worldSeed, 粗格))}——
     * seed 组合 / 盐 / 维 key / coarse 面与 P17 的三参 {@code heightAt} 内联取数<b>逐字同一</b>
     * （CHAIN_SEED_SALT 的三处字面量现状不扩大）；粗格身份走 {@link #chainRosterIndexAt} 的
     * 单一份共享 memo（P19 U8 起本类三处同式取数合并为一份，消"每格 × 缓存份数"的短命链重复）；</li>
     * <li><b>性能（P19 U8 改判）</b>：核中心 = 列所在粗格 ⇒ 同一粗格内所有列的 amp 数学恒等，
     * 本方法缓存<b>粗格 amp 终值</b>（{@link #AMP_CELL_CACHE}，见容量注释①）——每列 1 次查表，
     * 未命中才做一次 69 点核加权；命中/未命中两条路对同一列给出<b>逐位相同</b>的 double；</li>
     * <li>取值域 [{@code RELIEF_AMPLITUDE_BY_ROSTER} 的 min, max]（核是凸组合）⇒ 平滑只收窄
     * 振幅分布，不可能把任何列推出改造前的钳制带；</li>
     * <li><b>账本时点假设</b>：缓存值反映首次求值时的名册账本。生产路径账本在 mod init 期定型、
     * worldgen 之后开始，假设恒成立；离线判据必须在触 ampAt <b>之前</b>完成
     * {@code recordAllocation}（同 seed 先无账本后有账本地查会命中无账本期的缓存——P18 T3 初跑
     * 实证过一次假红）。{@code P17TerrainReliefCheck} 的 RELIEF 组现序是"显式档复算"（不触身份面）
     * 先于 sourceGroup 的账本装配——该组读数在 T3 后退化为无账本默认口径，属 T8 重钉对象
     * （重钉形态：先装配账本再采样，或按 ampAt 场归因）。</li>
     * </ul>
     */
    public static double ampAt(long worldSeed, int x, int z) {
        final int cellX = x >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int cellZ = z >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final HashMap<Long, HashMap<Long, Double>> bySeed = AMP_CELL_CACHE.get();
        HashMap<Long, Double> cells = bySeed.get(worldSeed);
        if (cells == null) {
            cells = new HashMap<>();
            bySeed.put(worldSeed, cells);
        }
        final Long key = Long.valueOf(packCell(cellX, cellZ));
        final Double cached = cells.get(key);
        if (cached != null) {
            return cached.doubleValue();
        }
        double amp = 0.0D;
        double uniformTier = Double.NaN;
        boolean uniform = true;
        for (int k = 0; k < AMP_KERNEL_DX.length; k++) {
            final double v = reliefAmplitudeForRosterIndex(
                chainRosterIndexAt(worldSeed, cellX + AMP_KERNEL_DX[k], cellZ + AMP_KERNEL_DZ[k]));
            if (k == 0) {
                uniformTier = v;
            } else if (v != uniformTier) {
                uniform = false;
            }
            amp += AMP_KERNEL_W[k] * v;
        }
        // 邻域全同档 ⇒ 加权和数学上恒等于该档值；直接返回避免 Σ(w_k/wSum) 的 ulp 级浮点误差，
        // 使群系腹地与未装配/EMPTY 降级口径（全默认档 1.0）和 P17 硬查表<b>逐位相同</b>。
        final double result = uniform ? uniformTier : amp;
        if (cells.size() >= AMP_CELL_CACHE_CAP) {
            cells.clear();
        }
        cells.put(key, Double.valueOf(result));
        return result;
    }

    /**
     * coarse 身份链的<b>单一份共享 memo</b>（P19 U8 性能批新增；公开供 TerrainVariants /
     * ruins.DensityField 复用，消此前 amp/roster/变体三处各自为政的同式短命链取数）。
     * 取数式与本类既有注释口径<b>逐字同一</b>：
     * {@code GTSRGenLayerRosterFace.rosterIndexAt(worldSeed ^ CHAIN_SEED_SALT,
     * GTSRBiomeAuthority.DIM_KEY_PROSPERITY, cellX << COARSE_BLOCK_SHIFT, cellZ << COARSE_BLOCK_SHIFT)}
     * ——同 seed 同粗格必得同 int，缓存只是记忆化，不构成第二真值；账本时点假设与 {@link #ampAt}
     * 注释同款。容量 {@link #CHAIN_CELL_CACHE_CAP}（固定上限，超限整清重算值不变），
     * 线程私有（GenLayer 链与 vanilla IntCache 均非线程安全的既有纪律）。
     */
    public static int chainRosterIndexAt(long worldSeed, int cellX, int cellZ) {
        final HashMap<Long, HashMap<Long, Integer>> bySeed = CHAIN_CELL_CACHE.get();
        HashMap<Long, Integer> cells = bySeed.get(worldSeed);
        if (cells == null) {
            cells = new HashMap<>();
            bySeed.put(worldSeed, cells);
        }
        final Long key = Long.valueOf(packCell(cellX, cellZ));
        Integer cached = cells.get(key);
        if (cached == null) {
            if (cells.size() >= CHAIN_CELL_CACHE_CAP) {
                cells.clear();
            }
            cached = Integer.valueOf(
                GTSRGenLayerRosterFace.rosterIndexAt(
                    worldSeed ^ CHAIN_SEED_SALT,
                    GTSRBiomeAuthority.DIM_KEY_PROSPERITY,
                    cellX << GTSRGenLayerChain.COARSE_BLOCK_SHIFT,
                    cellZ << GTSRGenLayerChain.COARSE_BLOCK_SHIFT));
            cells.put(key, cached);
        }
        return cached.intValue();
    }

    /** 共享身份 memo 的粗格数上限（= 65536 格 = 1024×1024 方块工作集；同 {@link #AMP_CELL_CACHE_CAP} 口径）。 */
    private static final int CHAIN_CELL_CACHE_CAP = 65536;

    private static final ThreadLocal<HashMap<Long, HashMap<Long, Integer>>> CHAIN_CELL_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    /** (cellX, cellZ) → long 打包（低 32 位 cellZ；算术语义下负坐标两侧一致）。 */
    private static long packCell(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    // ═══ P18 T4（plan §3.2）：河谷压低链并入 heightCore ═══
    //
    // 在 T3 的 amp 平滑结果 h0 之后追加河谷链。v1.20.40（P19 plan §A.1）起为 <b>两段式</b>：
    // 外段 e1 = clamp(1 − s/VALLEY_LEVEL, 0, 1) 把 h0 压向 poolLevel+RIM_EPS（贴水缓坡谷带）、
    // 内段 s ≥ S_ERODE 向 bedFromPool 二次 lerp（s=WET_MIN 收完）；水面/床锚 = 河流场
    // poolLevelAt 的分段池水位（P19 §C），低地防抬升与 [40,110] 钳制语义不变。
    // <b>契约红线全部不动</b>：签名 / 同 seed 纯函数 / 值域 / 四族共用（PlacementGate.groundFn
    // 直引自动获得河谷压低——结构接地随河谷下沉，这是设计意图）。s = 0 的列（河谷外）
    // 一步短路，零河流成本。

    /**
     * 河谷链的名册下标缓存（线程私有，key = (worldSeed, 粗格)。<b>v1.20.40（P19 U8）改判</b>：
     * 本表与 ampAt 的身份取数原是两份各自为政的同式 memo——现合并为 {@link #chainRosterIndexAt}
     * 单一份共享 memo（TerrainVariants / DensityField 同此），本方法保留为 heightCore 内的
     * 就地取数名，取数路径不变；账本时点假设与 ampAt 注释同款。
     */
    private static int rosterIndexCached(long worldSeed, int x, int z) {
        return chainRosterIndexAt(
            worldSeed,
            x >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT,
            z >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT);
    }

    /**
     * (x,z) 列的地表实体高度（最高实体方块的 y）。<b>生产唯一入口</b>。
     * 两个调用点必须传<b>同一</b> seed：{@code World.getSeed()}（不含 def.seedSalt）——
     * ChunkProviderProsperityRuins 与 CityPlanner/CityPlan 均如此（契约见类注释）。
     * <p>
     * <b>P3（plan §5 P3 / §2.1 L2）</b>：噪声内核 {@code valueNoise} 已上收到
     * {@link GTSRWorldgenHash#valueNoise(long, double, double)} 一份（与 dim79 侧逐字符等价）。
     * <b>P17 S-A</b>：本方法在 P3 的基础上多了一步"身份 → 振幅档"的取数（见类注释的红线证法），
     * 波长 / 域盐 / BASE / clamp 全部保持原值；P3 合并前后"逐点一致"的口径由
     * {@code tools/dim1/SurfaceYParityCheck} 的 SITE 摘要继续钉（BASE 快照随本轮重录）。
     * <p>
     * <b>P18 T3（plan §3.6）</b>："身份 → 振幅档"的一步由<b>逐列直接下标</b>改为
     * {@link #ampAt(long, int, int)} 平滑场（类注释 P18 节）；签名 / 纯函数 / 值域 / 四族共用
     * 契约不变。
     */
    public static int heightAt(long worldSeed, int x, int z) {
        return heightAtMemoized(worldSeed, x, z);
    }

    /**
     * heightAt 的<b>列级 memo</b>（P19 U8 性能批，plan §J/U6 遗留"isSanzuColumn 含 heightAt 整链
     * 每次完整求值"的消重面）：heightCore 是 (worldSeed, x, z) 的纯函数 ⇒ 同列重复调用（生产
     * populate 链同一列经 generateTerrain / GTSRRiverPlacer / fillSanzuLakes / fillSwampPools /
     * isSanzuColumn 重复求值 4-5 次）逐位同值，缓存只是记忆化，不构成第二真值。
     * 结构：ThreadLocal <b>直接映射</b>定长表（{@link #HEIGHT_MEMO_CAP}，按 (seed,x,z) 精确键，
     * 冲突即覆盖淘汰——容量有界、无需清空逻辑）；账本时点假设与 ampAt 注释同款（首求值定型）。
     * 无重入：heightCore 内部不回调 heightAt（endFaceBed/strengthAt 等只走河流场侧）。
     */
    private static int heightAtMemoized(long worldSeed, int x, int z) {
        final int idx = heightMemoIndex(worldSeed, x, z);
        final long[] seeds = HEIGHT_MEMO_SEED.get();
        if (seeds[idx] == worldSeed && HEIGHT_MEMO_X.get()[idx] == x && HEIGHT_MEMO_Z.get()[idx] == z) {
            return HEIGHT_MEMO_VAL.get()[idx];
        }
        final int y = heightCore(worldSeed, x, z);
        seeds[idx] = worldSeed;
        HEIGHT_MEMO_X.get()[idx] = x;
        HEIGHT_MEMO_Z.get()[idx] = z;
        HEIGHT_MEMO_VAL.get()[idx] = y;
        return y;
    }

    /** 列级 memo 容量（直接映射、定长 = 容量天然有界；2048 槽 > 生产单 chunk 工作集 18×18+环）。 */
    private static final int HEIGHT_MEMO_CAP = 2048;
    private static final ThreadLocal<long[]> HEIGHT_MEMO_SEED = ThreadLocal
        .withInitial(() -> new long[HEIGHT_MEMO_CAP]);
    private static final ThreadLocal<int[]> HEIGHT_MEMO_X = ThreadLocal.withInitial(() -> new int[HEIGHT_MEMO_CAP]);
    private static final ThreadLocal<int[]> HEIGHT_MEMO_Z = ThreadLocal.withInitial(() -> new int[HEIGHT_MEMO_CAP]);
    private static final ThreadLocal<int[]> HEIGHT_MEMO_VAL = ThreadLocal.withInitial(() -> new int[HEIGHT_MEMO_CAP]);

    /** (seed,x,z) → 槽下标（splitmix 终混取高位；任何确定性散列都可，正确性与下标无关）。 */
    private static int heightMemoIndex(long worldSeed, int x, int z) {
        long k = worldSeed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        k ^= k >>> 33;
        k *= 0xFF51AFD7ED558CCDL;
        k ^= k >>> 33;
        return (int) (k >>> 40) & (HEIGHT_MEMO_CAP - 1);
    }

    /**
     * 身份档显式形态（<b>签名保留口</b>）。
     * <p>
     * <b>P17 语义（已废止）</b>：本方法曾是"三参出口解析身份 → 转调本方法"的目标，rosterIndex
     * 直接选振幅档，离线判据借此用一段算术批量复算四群系高程分布。
     * <b>P18 T3（plan §3.6 / §5 契约）改判</b>：振幅取数改为 {@link #ampAt(long, int, int)}
     * 平滑场后，(x,z) 列的振幅由邻域粗格<b>共同</b>决定，单点 rosterIndex 不再是充分的显式化
     * 参数——本方法与 {@link #heightAt(long, int, int)} 现在转调<b>同一个</b> {@link #heightCore}，
     * <b>rosterIndex 入参被忽略</b>（保留签名是为了 GTSRRiverPlacer / P17RiverNetworkCheck 等
     * 既有调用面零改动；它们传入的档值不再参与振幅）。"三参 == 显式形态"的逐位一致性由构造
     * 保证（两个出口同一函数体），仍可被 {@code P17TerrainReliefCheck} 的 SOURCE 组钉住；
     * 按<b>显式档</b>复算四群系高程分布的旧口径随 RELIEF 组一起在 T8 重钉。
     *
     * @param rosterIndex P18 起不再参与高度计算（见上）；保留签名兼容既有调用面
     */
    public static int heightAtWithReliefTier(long worldSeed, int x, int z, int rosterIndex) {
        return heightAtMemoized(worldSeed, x, z);
    }

    /**
     * 唯一高度算术体（P18 起三参出口与显式形态公共的核心；改造前是
     * {@code heightAtWithReliefTier} 的函数体，振幅取数位由直接下标换成 {@link #ampAt}；
     * v1.20.39 T4 起在 amp 平滑之后追加<b>河谷压低链</b>——plan §3.2）。
     */
    private static int heightCore(long worldSeed, int x, int z) {
        // 低频幅度调制（连续化保留）：波长 384，乘子 0.6..1.4
        final double zone = GTSRWorldgenHash.valueNoise(worldSeed ^ 0x5A0E5A0EL, x / 384.0D, z / 384.0D);
        // P17 S-A：再乘一档群系振幅（森 > 原 ≥ 沙 > 沼；默认档 1.0 = 改造前口径）；
        // P18 T3：该档由平滑场 ampAt 给出（plan §3.6），群系边界 40-60 格渐变、无 4.25× 硬跳
        final double amplitude = (1.0D + 0.4D * zone) * ampAt(worldSeed, x, z);
        // 统一缓丘：主波长 180 ±12（×1.2）+ 次波长 56 ±5.4（×1.2）
        final double h1 = GTSRWorldgenHash.valueNoise(worldSeed, x / 180.0D, z / 180.0D);
        final double h2 = GTSRWorldgenHash.valueNoise(worldSeed ^ 0x11L, x / 56.0D, z / 56.0D);
        // 细起伏：波长 17 ±1.5（不乘 relief，避免高频锯齿被放大）
        final double h3 = GTSRWorldgenHash.valueNoise(worldSeed ^ 0x22L, x / 17.0D, z / 17.0D);
        final double relief = (h1 * 10.0D + h2 * 4.5D) * RELIEF_MULT * amplitude + h3 * 1.5D;
        // ═══ P19 §H 群系内分支地形变体：h0 上注入丘陵/沙丘/夹持形态项（TerrainVariants），
        // 全软门；均匀区逐位同改造前，两段式压低/湖段/钳制语义在其下游原样作用 ═══
        final int h0 = TerrainVariants.variantAdjustment(
            worldSeed,
            x,
            z,
            rosterIndexCached(worldSeed, x, z),
            BASE_HEIGHT + (int) Math.round(relief));
        // ═══ P18 T4 河谷压低（plan §3.2；接在 amp 平滑之后，钳制之前）═══
        // ═══ v1.20.40（P19 plan §A.1）两段式重构（修"河缘直切无河滩"）═══
        // RTG 同构两段式（G1 调查：TerrainBase riverized 外段全谷带压向贴水面 + RealisticBiomeBase
        // erodedNoise 内段窄带才切床），替换旧"单段 lerp 一次从 h0 插到 bed"（根因：河缘一圈陡坎）：
        // <ol>
        // <li><b>外段</b>：e1 = clamp(1−s/VALLEY_LEVEL,0,1)，h0 沿全谷带压向
        // poolLevel+{@link GTSRVoronoiRiverField#RIM_EPS}（贴水面缓坡谷带，水缘在
        // s=WET_MIN 处恰为贴水高度）；</li>
        // <li><b>内段</b>：s ≥ S_ERODE 起向 bedAt 二次 lerp，侵蚀在 s=WET_MIN 处收完
        // （t = clamp((s−S_ERODE)/(WET_MIN−S_ERODE),0,1)）——水道内是床（水面下），
        // WET_MIN+SHORE_FLAT_BAND 内露头列成河滩（浅滩重释 = n_bed 派生）。</li>
        // </ol>
        // 低地防抬升语义保留：仅 h0 &gt; 压低结果+1 时落地（沼泽等低地原样）。
        // s = 0 的列（河谷外）一步短路，零河流成本。
        // ═══ v1.20.39 T5 主干深谷（plan §3.3「valleyLevel×1.3」）：主干带内谷深档 ×1.3
        // （外段 e1 域拉长、谷坡更缓更长）；带外分支同解。═══
        int y = h0;
        final int rosterIndex = rosterIndexCached(worldSeed, x, z);
        final double trunk = GTSRVoronoiRiverField.trunkAt(worldSeed, x, z);
        final double s = -GTSRVoronoiRiverField.strengthAt(worldSeed, x, z, rosterIndex);
        if (s > 0.0D) {
            final int pool = GTSRVoronoiRiverField.poolLevelAt(worldSeed, x, z, rosterIndex);
            final double rim = pool + GTSRVoronoiRiverField.RIM_EPS;
            final double valley = GTSRVoronoiRiverField.VALLEY_LEVEL
                * (trunk > 0.0D ? GTSRVoronoiRiverField.TRUNK_VALLEY_SCALE : 1.0D);
            // —— 外段：全谷带压向贴水缓坡（rim）——
            double lowered = h0;
            final double e1 = Math.max(0.0D, 1.0D - s / valley);
            if (e1 < 1.0D) {
                lowered = h0 * e1 + rim * (1.0D - e1);
            }
            // —— 内段：切床域（s ≥ S_ERODE）向床二次 lerp，s=WET_MIN 收完 ——
            if (s >= GTSRVoronoiRiverField.S_ERODE) {
                final double bed = GTSRVoronoiRiverField.bedFromPool(worldSeed, x, z, rosterIndex, pool);
                final double span = GTSRVoronoiRiverField.WET_MIN - GTSRVoronoiRiverField.S_ERODE;
                final double t = Math.min(1.0D, (s - GTSRVoronoiRiverField.S_ERODE) / span);
                lowered = lowered * (1.0D - t) + bed * t;
            }
            // ═══ v1.20.41 P20 S3 需求 2「看不到河床」主修：岸坡下切 ═══
            // 谷坡环（水陆过渡带，与 GTSRRiverPlacer 的滩料同一谓词 inBankBand）整段再削
            // bankCutAt（∈[BANK_CUT_DEPTH/2, BANK_CUT_DEPTH] 格），使床料在断面上成宽度出露——
            // 改造前 board 恒 1 格 ⇒ 水面贴岸、看不见河床。
            // <b>沼泽档（roster 3）豁免</b>：A7「沼泽床 ∈[66.5,67.5]＝水面近地」是档表语义，
            // 下切会打掉沼地河口径（§5 S3 判据 3 的处置＝下切域限缩 roster ∈ {0,1,2,4}）。
            if (rosterIndex != 3 && GTSRVoronoiRiverField.inBankBand(worldSeed, x, z, s)) {
                lowered -= GTSRVoronoiRiverField.bankCutAt(worldSeed, x, z);
            }
            if (h0 > lowered + 1.0D) {
                y = (int) Math.round(lowered);
            }
        }
        // ═══ v1.20.40（P19 plan §E）沼泽微池压低：roster 3（喷气沼泽）第二激活档。微池列
        // （独立低频 Voronoi，间隔 220、水径 8-16）压至沼泽档床再降 1（≈pool−2±0.5）——
        // 水面 = 本段池水位 pool 的贴地口径（与沼泽河同水面），回填后 1-2 层水成沼地肌理；
        // min 语义 = 低地不抬升。═══
        final double poolPressure = GTSRVoronoiRiverField.swampLakeAt(worldSeed, x, z, rosterIndex);
        if (poolPressure < GTSRVoronoiRiverField.SWAMP_POOL_WATER_LEVEL) {
            final int pool = GTSRVoronoiRiverField.poolLevelAt(worldSeed, x, z, rosterIndex);
            final double bed = GTSRVoronoiRiverField.bedFromPool(worldSeed, x, z, rosterIndex, pool) - 1.0D;
            y = Math.min(y, (int) Math.round(bed));
        }
        // ═══ v1.20.39 T5 巨湖压低（plan §3.3）：仅主干带内激活（lakeAt 的性能门在河流场侧），
        // <b>非河道列也压</b>——湖水区（c_lake < LAKE_WATER_LEVEL）压至渐深湖床（v1.20.40
        // P19 §D：湖滨锚=水面下 1 → 湖心锚=水面下 LAKE_CENTER_DEPTH）；湖滨带
        // [WATER, SHORE) 从湖床线性渐变回当前地形（与河谷 e 的联合 = 先河谷后巨湖、湖水区取
        // 两者之深 ⇒ e 与 bed 在湖心联合作用）。v1.20.40 起湖滨带渐变加 min 语义：与河谷/
        // 微池压低取更深者，防"河道/低地穿湖滨带被渐变抬高成坝"。═══
        if (trunk > 0.0D) {
            final double lake = GTSRVoronoiRiverField.lakeAt(worldSeed, x, z);
            if (lake < GTSRVoronoiRiverField.LAKE_SHORE) {
                final double lakeBed = GTSRVoronoiRiverField.lakeBedAt(worldSeed, x, z, lake);
                if (lake < GTSRVoronoiRiverField.LAKE_WATER_LEVEL) {
                    y = Math.min(y, (int) Math.round(lakeBed));
                    // ═══ v1.20.41 P20 S5（plan §15.5）中心固定岛：湖段 min 压低之后<b>唯一允许的
                    // 抬升支路</b>——岛域（lakeAt < LAKE_ISLAND）把地表从湖床抬到岛面 72。
                    // 抬升面本身是 s01(k) 衰减（岛缘 k=0 ⇒ 值 = 湖心锚 40，与上面的床值同侧连续），
                    // 故岛缘不出现单格悬崖；抬升量恒 ≤ 72 ⇒ 与 MAX_HEIGHT=110 / HEIGHT_SENTINEL=108
                    // 零接触。岛外列 lakeIslandTopAt 返回 NaN 哨兵 ⇒ 本支路一步短路、零改动。═══
                    final double islandTop = GTSRVoronoiRiverField.lakeIslandTopAt(worldSeed, x, z, lake);
                    if (!Double.isNaN(islandTop)) {
                        y = Math.max(y, (int) Math.round(islandTop));
                    }
                } else {
                    // ═══ v1.20.41 P20 S5（plan §15.4 第一判据）湖滨带形状：改造前这里是
                    // <b>线性</b> lerp（在环带两端各留一个折角 = "衔接生硬"的形状根因之一）。
                    // 换成生产侧唯一出口 lakeShoreBlend = s01 缓入缓出 + 多级台阶（riser ≤ 总抬升/4），
                    // 环带<b>宽度不动</b>（理由见该常量的 LAKE_SHORE 注释）。min 语义原样保留 ⇒
                    // 环带恒不高于本列无湖时的原地形 ⇒ "环形堤"在本式下结构上不可表示。═══
                    final double q = GTSRVoronoiRiverField.lakeShoreBlend(lake);
                    y = (int) Math.min(y, Math.round(lakeBed * (1.0D - q) + y * q));
                }
            }
        }
        return y < MIN_HEIGHT ? MIN_HEIGHT : Math.min(y, MAX_HEIGHT);
    }
}
