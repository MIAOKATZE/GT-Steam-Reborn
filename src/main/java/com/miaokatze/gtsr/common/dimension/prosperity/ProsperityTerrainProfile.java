package com.miaokatze.gtsr.common.dimension.prosperity;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerRosterFace;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;

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
 * 参数口径（02 §2.1 噪声参数表裁剪）：
 * <ul>
 * <li>起伏幅度 ×1.2（02 §2.1 RELIEF_MULT，账本"主起伏 +20%"）；</li>
 * <li>海平面 68（02 §2.1 海拔抬升 +5），基准面 BASE=70 使地表绝大多数在"海平面"之上
 * （gtsr.brine 河流在用户裁剪范围外——本维度不放任何自然水，02 §0.2 河床项不实现；
 * 群系化只改<b>振幅</b>，不改 BASE/域盐/clamp，也不给任何群系加海拔偏移）；</li>
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
     */
    public static final double[] RELIEF_AMPLITUDE_BY_ROSTER = { 1.10D, 1.70D, 0.80D, 0.40D };

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
     */
    public static int heightAt(long worldSeed, int x, int z) {
        return heightAtWithReliefTier(
            worldSeed,
            x,
            z,
            GTSRGenLayerRosterFace
                .rosterIndexAt(worldSeed ^ CHAIN_SEED_SALT, GTSRBiomeAuthority.DIM_KEY_PROSPERITY, x, z));
    }

    /**
     * 身份档显式形态：与 {@link #heightAt(long, int, int)} <b>同一个</b>函数体，只是把
     * "seed 作用域 coarse 身份面"的解析结果由调用方给出。
     * <p>
     * 存在理由有二：① 离线判据可以在一次窗口采样里批量拿身份（逐列重建链在大窗下是纯浪费），
     * 从而用<b>同一段算术</b>复算四群系高程分布；② "同格同值"因此可被逐点对拍钉住
     * （{@code P17TerrainReliefCheck} 断言本方法在档表解析下与三参出口逐位相同）。
     * <b>生产四族一律走三参出口</b>——本方法不是第二条高度真值，参数只是同一查表的显式化。
     *
     * @param rosterIndex L1 维内名册下标，或 {@link GTSRGenLayerRosterFace#NO_IDENTITY}（走默认档）
     */
    public static int heightAtWithReliefTier(long worldSeed, int x, int z, int rosterIndex) {
        // 低频幅度调制（连续化保留）：波长 384，乘子 0.6..1.4
        final double zone = GTSRWorldgenHash.valueNoise(worldSeed ^ 0x5A0E5A0EL, x / 384.0D, z / 384.0D);
        // P17 S-A：再乘一档群系振幅（森 > 原 ≥ 沙 > 沼；默认档 1.0 = 改造前口径）
        final double amplitude = (1.0D + 0.4D * zone) * reliefAmplitudeForRosterIndex(rosterIndex);
        // 统一缓丘：主波长 180 ±12（×1.2）+ 次波长 56 ±5.4（×1.2）
        final double h1 = GTSRWorldgenHash.valueNoise(worldSeed, x / 180.0D, z / 180.0D);
        final double h2 = GTSRWorldgenHash.valueNoise(worldSeed ^ 0x11L, x / 56.0D, z / 56.0D);
        // 细起伏：波长 17 ±1.5（不乘 relief，避免高频锯齿被放大）
        final double h3 = GTSRWorldgenHash.valueNoise(worldSeed ^ 0x22L, x / 17.0D, z / 17.0D);
        final double relief = (h1 * 10.0D + h2 * 4.5D) * RELIEF_MULT * amplitude + h3 * 1.5D;
        final int y = BASE_HEIGHT + (int) Math.round(relief);
        return y < MIN_HEIGHT ? MIN_HEIGHT : Math.min(y, MAX_HEIGHT);
    }
}
