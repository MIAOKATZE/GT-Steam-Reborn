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
     * <b>海平面（水面口径，v1.20.39 T4 起全局单点引用）</b>：plan §3.2「新增 SEA_LEVEL=68 于
     * ProsperityTerrainProfile，全局单点引用（消除"仅注释"现状）」。口径 = 水面所在格线上方：
     * 置水最高格 {@code y = SEA_LEVEL - 1 = 67}；河床目标 {@link GTSRVoronoiRiverField#BED_TARGET}
     * = 64.5 ⇒ 常态水深 2-5。注意它与 {@link #BASE_HEIGHT} 是<b>两个独立口径</b>（后者只是无河
     * 地形的基准面），不要互相改写。
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
     * 取档前的事实（{@code plan/tmp/p17-b/B-terrain-river-flora.md} §1.3）：改前四群系 sd 为
     * 森 5.932 / 原 5.903 / 沼 6.607 / 沙 <b>6.746</b> —— 与需求方向<b>完全相反</b>，
     * 逐 seed 严格序命中 0/10；那个差异只是两个无关噪声场在小窗内的相关涨落，不是分化。
     * <p>
     * 本档表的设计约束（实测值见 {@code plan/tmp/p17-sa/SA-RESULT.md}）：
     * <ul>
     * <li><b>四档算数均值精确 = 1.0</b>（(1.10+1.70+0.80+0.40)/4）⇒ 全域期望振幅与改前同阶，
     * 本片只"差异化"，不借机整体加大起伏（那是另一条需求，未获授权）；</li>
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
    public static final double[] RELIEF_AMPLITUDE_BY_ROSTER = { 1.10D, 1.70D, 0.80D, 0.40D, 0.38D };

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
     * 每线程每 seed 的粗格档值缓存上限（防内存无限增长）。超限<b>整表清空</b>：档值是
     * (seed, 粗格) 的纯函数，清空后按需重算值不变（无 LRU 顺序复杂度，也不引入第二真值）。
     * 16384 格 ≈ 128×128 粗格 = 512×512 方块的工作集，远大于单次地形填充所需的
     * (4+2×5)² = 196 粗格，批内零淘汰。
     */
    private static final int AMP_CELL_CACHE_CAP = 16384;

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
     * 粗格档值缓存（线程私有：GenLayer 链 + vanilla IntCache 均<b>非线程安全</b>，现状的
     * "每调用一条短命链"纪律本来就是单线程域——缓存沿用同一纪律且以 ThreadLocal 结构上
     * 杜绝跨线程共享）。外层 key = worldSeed（离线判据会在一个 JVM 里扫多个 seed），
     * 内层 key = (cellX, cellZ) 打包 long，值为该粗格的 RELIEF 档乘子。
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
     * <li>单粗格档值 = {@code reliefAmplitudeForRosterIndex(rosterIndexAt(worldSeed ^ CHAIN_SEED_SALT,
     * DIM_KEY_PROSPERITY, 粗格左上块))}——seed 组合 / 盐 / 维 key / coarse 面与 P17 的三参
     * {@code heightAt} 内联取数<b>逐字同一</b>（CHAIN_SEED_SALT 的三处字面量现状不扩大）；</li>
     * <li>性能红线：粗格档值查询 = 一条 GenLayer 短命链求值（贵），121 次/列不可接受 ⇒
     * <b>每粗格只求值一次</b>（{@link #AMP_CELL_CACHE} 按线程按 seed 缓存，上限
     * {@link #AMP_CELL_CACHE_CAP}，超限整清重算）；每列只剩 69 次查表 + 乘加（便宜）；</li>
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
        double amp = 0.0D;
        double uniformTier = Double.NaN;
        boolean uniform = true;
        for (int k = 0; k < AMP_KERNEL_DX.length; k++) {
            final int cx = cellX + AMP_KERNEL_DX[k];
            final int cz = cellZ + AMP_KERNEL_DZ[k];
            final Long key = Long.valueOf(packCell(cx, cz));
            Double tier = cells.get(key);
            if (tier == null) {
                if (cells.size() >= AMP_CELL_CACHE_CAP) {
                    cells.clear();
                }
                tier = Double.valueOf(
                    reliefAmplitudeForRosterIndex(
                        GTSRGenLayerRosterFace.rosterIndexAt(
                            worldSeed ^ CHAIN_SEED_SALT,
                            GTSRBiomeAuthority.DIM_KEY_PROSPERITY,
                            cx << GTSRGenLayerChain.COARSE_BLOCK_SHIFT,
                            cz << GTSRGenLayerChain.COARSE_BLOCK_SHIFT)));
                cells.put(key, tier);
            }
            final double v = tier.doubleValue();
            if (k == 0) {
                uniformTier = v;
            } else if (v != uniformTier) {
                uniform = false;
            }
            amp += AMP_KERNEL_W[k] * v;
        }
        // 邻域全同档 ⇒ 加权和数学上恒等于该档值；直接返回避免 Σ(w_k/wSum) 的 ulp 级浮点误差，
        // 使群系腹地与未装配/EMPTY 降级口径（全默认档 1.0）和 P17 硬查表<b>逐位相同</b>。
        return uniform ? uniformTier : amp;
    }

    /** (cellX, cellZ) → long 打包（低 32 位 cellZ；算术语义下负坐标两侧一致）。 */
    private static long packCell(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    // ═══ P18 T4（plan §3.2）：河谷压低链并入 heightCore ═══
    //
    // 在 T3 的 amp 平滑结果 h0 之后追加：s = -strengthAt（riverStyle 按同一条 coarse 身份面取档）、
    // e = clamp(1 - s/VALLEY_LEVEL, 0, 1)、bed = GTSRVoronoiRiverField.bedAt；
    // h0 > bed+1 时 h1 = round(h0×e + bed×(1-e))，否则不动（防低地被抬升），最终钳 [40,110] 不变。
    // <b>契约红线全部不动</b>：签名 / 同 seed 纯函数 / 值域 / 四族共用（PlacementGate.groundFn
    // 直引自动获得河谷压低——结构接地随河谷下沉，这是设计意图）。s = 0 的列（河谷外）
    // 一步短路，零河流成本。

    /**
     * 河谷链的名册下标缓存（线程私有，key = (worldSeed, 粗格)；与 {@link #AMP_CELL_CACHE} 同一
     * 纪律——ampAt 缓存的是"粗格 → 振幅档值"，本表缓存"粗格 → 名册下标"供 riverStyle 取档，
     * 两表各自独立，互不重构对方的取数路径）。取数式与 {@link #ampAt} 内联取数逐字同一
     * （同一条 coarse 身份链 + 同盐常数引用）；账本时点假设与 ampAt 注释同款。
     */
    private static final ThreadLocal<HashMap<Long, HashMap<Long, Integer>>> ROSTER_CELL_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    /** 名册下标粗格缓存上限（同 {@link #AMP_CELL_CACHE_CAP} 口径）。 */
    private static final int ROSTER_CELL_CACHE_CAP = 16384;

    /** (x,z) 列的名册下标（coarse 身份面，缓存；离线未装配账本 = {@code NO_IDENTITY}）。 */
    private static int rosterIndexCached(long worldSeed, int x, int z) {
        final int cellX = x >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int cellZ = z >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final HashMap<Long, HashMap<Long, Integer>> bySeed = ROSTER_CELL_CACHE.get();
        HashMap<Long, Integer> cells = bySeed.get(worldSeed);
        if (cells == null) {
            cells = new HashMap<>();
            bySeed.put(worldSeed, cells);
        }
        final Long key = Long.valueOf(packCell(cellX, cellZ));
        Integer tier = cells.get(key);
        if (tier == null) {
            if (cells.size() >= ROSTER_CELL_CACHE_CAP) {
                cells.clear();
            }
            tier = Integer.valueOf(
                GTSRGenLayerRosterFace.rosterIndexAt(
                    worldSeed ^ CHAIN_SEED_SALT,
                    GTSRBiomeAuthority.DIM_KEY_PROSPERITY,
                    cellX << GTSRGenLayerChain.COARSE_BLOCK_SHIFT,
                    cellZ << GTSRGenLayerChain.COARSE_BLOCK_SHIFT));
            cells.put(key, tier);
        }
        return tier.intValue();
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
        return heightCore(worldSeed, x, z);
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
        return heightCore(worldSeed, x, z);
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
        final int h0 = BASE_HEIGHT + (int) Math.round(relief);
        // ═══ P18 T4 河谷压低（plan §3.2；接在 amp 平滑之后，钳制之前）═══
        // s = -strengthAt ∈ [0,1]（0=河谷外 ⇒ 一步短路，无河列零河流成本）；e = 1 无影响、
        // 0 = 平床（VALLEY_LEVEL 与 WET_MIN 等值 ⇒ 平底域精确等于置水域，水外即起坡）；
        // h0 ≤ bed+1 的低地（沼泽等）不动——防抬升；h1 线性幂衰减起步（目检生硬可在调参回路
        // 换 smoothstep(e)，属实现内常数）。
        // ═══ v1.20.39 T5 主干深谷（plan §3.3「valleyLevel×1.3」）：主干带内谷深档 ×1.3——
        // 带内 s 域经宽河展宽（width×3.5）延伸到 ~1，×1.3 后 e 在河心仍略 >0（床近平非精确平底），
        // 谷坡拉长、谷更深；带外分支逐位不变。═══
        int y = h0;
        final int rosterIndex = rosterIndexCached(worldSeed, x, z);
        final double trunk = GTSRVoronoiRiverField.trunkAt(worldSeed, x, z);
        final double s = -GTSRVoronoiRiverField.strengthAt(worldSeed, x, z, rosterIndex);
        if (s > 0.0D) {
            final double valley = GTSRVoronoiRiverField.VALLEY_LEVEL
                * (trunk > 0.0D ? GTSRVoronoiRiverField.TRUNK_VALLEY_SCALE : 1.0D);
            final double e = Math.max(0.0D, 1.0D - s / valley);
            if (e < 1.0D) {
                final double bed = GTSRVoronoiRiverField.bedAt(worldSeed, x, z, rosterIndex);
                if (h0 > bed + 1.0D) {
                    y = (int) Math.round(h0 * e + bed * (1.0D - e));
                }
            }
        }
        // ═══ v1.20.39 T5 巨湖压低（plan §3.3）：仅主干带内激活（lakeAt 的性能门在河流场侧），
        // <b>非河道列也压</b>——湖水区（c_lake < LAKE_WATER_LEVEL）压至湖床 62-64（水面 68 ⇒
        // 深 4-6）；湖滨带 [WATER, SHORE) 从湖床线性渐变回当前地形（与河谷 e 的联合 = 先河谷
        // 后巨湖、湖水区取两者之深 ⇒ e 与 bed 在湖心联合作用）。═══
        if (trunk > 0.0D) {
            final double lake = GTSRVoronoiRiverField.lakeAt(worldSeed, x, z);
            if (lake < GTSRVoronoiRiverField.LAKE_SHORE) {
                final double lakeBed = GTSRVoronoiRiverField.lakeBedAt(worldSeed, x, z);
                if (lake < GTSRVoronoiRiverField.LAKE_WATER_LEVEL) {
                    y = Math.min(y, (int) Math.round(lakeBed));
                } else {
                    final double t = (lake - GTSRVoronoiRiverField.LAKE_WATER_LEVEL)
                        / (GTSRVoronoiRiverField.LAKE_SHORE - GTSRVoronoiRiverField.LAKE_WATER_LEVEL);
                    y = (int) Math.round(lakeBed * (1.0D - t) + y * t);
                }
            }
        }
        return y < MIN_HEIGHT ? MIN_HEIGHT : Math.min(y, MAX_HEIGHT);
    }
}
