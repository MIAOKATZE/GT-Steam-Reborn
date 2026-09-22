package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import static com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase.findSurfaceY;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;

/**
 * 自然区地表装饰散布器（dim78 S-A1，plan §12 修订 5：修复自然区"光秃秃"；草丛/锈树/碎石三件。
 * <b>P17 S-B2 起按 L1 名册下标分流树与花草，并新增荒漠沙砾趟</b>）。
 * 仅作用于自然区（城 buffer 窗由编排器在装饰调用之前整体 return 天然保证， ProsperityWorldGenerator
 * 编排顺序：城市 → [buffer 窗 return] → 机器 → 散布 → 装饰）。
 * <p>
 * <b>频率登记（P17 S-B2 改判）：</b>改前是"六常量硬编码 + {@code decorate} 零身份入参"
 * （旧 :54-69 + 旧 {@code ProsperityWorldGenerator:182}），实测每 chunk 树数在四群系几乎相等
 * （0.0743/0.0751/0.0505/0.0473，plan/tmp/p17-sb2/out/probe-before.log）且花恒 0 —— 与需求
 * 「青铜森林树更多更大 / 平原矮树 / 沼泽中等 / 沙漠没有树 / 增加更多花草」相反或无表达。
 * 现全部收进一张按名册下标查的档表 {@link #VEG_TIERS_BY_ROSTER}
 * （写法照 {@code ProsperityTerrainProfile.RELIEF_AMPLITUDE_BY_ROSTER} 与
 * {@code ProsperityWorldGenerator.MACHINE_WEIGHTS} 同族），身份缺失（{@code -1}/越界）走
 * {@link #DEFAULT_TIER}＝<b>逐字段等于改前</b>，故降级态与改造前逐位重合，也不构成第二真值源。
 * <b>抑制一律写成档表里该群系的档值 = 0</b>（plan §2 第 8 条），本类不存在任何身份等值判断。
 * <p>
 * <b>两条禁路的落实</b>：① 分流<b>不</b>按地表方块比选 —— {@link #tuftForGround} 的 top 成员比较
 * 次数与四员映射一字未动（{@code SurfaceGateUnifyCheck} 钉死 4 处 / E 别名 3 员 /
 * 本文件 {@code isNaturalTop(} 恰 5 处），新花与新草的可落地面复用该选择器的"返回非空"判定，
 * 树与沙走 {@link #naturalTopAt} 这个<b>只含一处</b> {@code isNaturalTop(} 调用的私有谓词；
 * ② 不用原版花（{@code BlockBush} 随机 tick 自我删除，P17-B/S-B §1 已证）。
 * <p>
 * 确定性：全部随机从 chunk 级哈希派生（盐 {@link #SALT_DECOR}，RuinedMachinePlacer/
 * ProsperitySurfaceScatter 同款 splitmix 范式），同 seed 同坐标跨 chunk 重算一致；禁用 populate 裸
 * Random 语义（Random 仅作哈希种子的取数器）。掷趟顺序固定 = 树 → 花草 → 碎石 → 沙砾。
 * <p>
 * 让行纪律（不覆盖已有结构）：花草/沙砾逐块 {@code isAirBlock} 让行（02 §8.4 优先级 4 口径）；
 * 树整柱干 + 冠层中心列先查空气、有占用整树跳过。接地：逐列 {@code findSurfaceY}
 * （WorldGenRunawaySingularity.java:82-91 范式）。<b>落点门（P4 起）= 框架单一谓词
 * {@link SurfaceGate}（见 {@link #isNaturalTop}）</b>：dim78 声明集 = 四群系自然区 top
 * ∪ {@code prosperitySurface}（后者 = 城内 meta0-5 冻结块与 S-A1 前旧地表；改造前装饰层漏认它，
 * 与结构层结论相反，属审计 D-5 缺陷，本片补齐）。草丛另有 {@link #tuftForGround} 变体选择器，
 * 只认四自然 top ⇒ {@code prosperitySurface} 上不长草（<b>P17 S-B2 起新花/新草同守这条窄口径</b>）。
 * 碎石用原版 gravel（CityVariants 'g' 键先例；BlockFalling 属性要求必须贴地，逐块独立找地表）。
 * <p>
 * 跨界协议：树冠跨度 = 该档 {@code canopyRadius}（改前恒 2 → 现 1..3）⇒ 干位钳制在 chunk 内
 * {@code radius .. 15-radius}（{@link #placeTree}），冠层任何一格都不出 chunk；
 * ChunkClampedSink 零越界丢弃；草丛/花/沙砾落点单格不跨界。
 */
public final class ProsperityDecorPlacer {

    /** 盐 "decor"（0x006465636F72 截断；chunk 级隔离，区别于机器 0x6D6163 / 散布 0x5C4174）。 */
    private static final long SALT_DECOR = 0x6465636FL;

    /**
     * 本类所属维度键（P4：门的显式维度入参）。取 L1 账本同一词汇 {@link SurfaceGate#DIM78}
     * （= {@code GTSRBiomeAuthority.DIM_KEY_PROSPERITY}），不另造字符串。
     */
    private static final String DIM_KEY = SurfaceGate.DIM78;

    /** 草丛放置尝试次数下限/chunk（plan §12 修订 5：草丛 2-4 次/chunk；<b>现仅 {@link #DEFAULT_TIER} 用</b>）。 */
    private static final int TUFT_ATTEMPTS_MIN = 2;

    /** 草丛放置尝试次数上限/chunk（含）。 */
    private static final int TUFT_ATTEMPTS_MAX = 4;

    /** 锈树频率分母：每 chunk 1/16 概率一棵（plan §12 修订 5；<b>现仅 {@link #DEFAULT_TIER} 用</b>）。 */
    private static final int TREE_CHANCE_DENOM = 16;

    /** 碎石堆数上限/chunk（0..2 堆，"碎石少量"）。 */
    private static final int RUBBLE_PILES_MAX = 2;

    /** 单堆碎石块数上限（1..3 块小簇）。 */
    private static final int RUBBLE_PIECES_MAX = 3;

    /** findSurfaceY 上界（散布同款 200 门；heightAt clamp 40..110 之下留余量）。 */
    private static final int MAX_SURFACE_Y = 200;

    /** 一格的旧草 / 新草二分概率分母（{@code nextInt(2)==0} ⇒ 旧草，否则该档新草）。 */
    private static final int LEGACY_TUFT_CHANCE_DENOM = 2;

    /** 木种码：干 = {@code prosperityRustLog}、冠 = {@code prosperityRustLeaves}（改前唯一树种）。 */
    public static final int WOOD_RUST = 0;
    /** 木种码：铜绿档（森林混生种，塔冠）。 */
    public static final int WOOD_COPPER = 1;
    /** 木种码：黄铜档（青铜森林主档，广穹冠）。 */
    public static final int WOOD_BRASS = 2;
    /** 木种码：泥炭沼档（沼泽，平展伞冠）。 */
    public static final int WOOD_MARSH = 3;
    /** 混生档关闭的哨兵值（{@code woodSecondary} 取它 ⇒ 一次随机都不掷，恒用主档）。 */
    public static final int WOOD_NONE = -1;

    /** 花种码：锈华花。 */
    public static final int FLOWER_RUST = 0;
    /** 花种码：铜绿霜花。 */
    public static final int FLOWER_PATINA = 1;
    /** 花种码：黄铜金盏。 */
    public static final int FLOWER_BRASS = 2;
    /** 花种码：沼地兰。 */
    public static final int FLOWER_MARSH = 3;

    /** 新草种码：刚毛草（疏高旱生）。 */
    public static final int GRASS_BRISTLE = 0;
    /** 新草种码：苔薹草（密矮湿生）。 */
    public static final int GRASS_SEDGE = 1;

    /** 沙料码：细硅沙 / 粗粒沙 / 河床砾（{@link #SILICA_SAND} 等，全部非 top）。 */
    public static final int SAND_FINE = 0;
    /** 见 {@link #SAND_FINE}。 */
    public static final int SAND_COARSE = 1;
    /** 见 {@link #SAND_FINE}。 */
    public static final int SAND_GRAVEL = 2;

    /**
     * 一群系一行的植被档（P17 S-B2）。字段全部是"档"，不是开关：
     * <ul>
     * <li>{@code treeRolls × 1/treeChanceDenom} = 每 chunk 树位期望；<b>{@code treeRolls=0} 即该群系零树</b>
     * （沙漠的"没有树"就是这么表达的，不是 return，也不是身份等值判断）；档形不变式
     * {@code treeRolls>0 ⇒ treeChanceDenom>0}（防 {@code nextInt(0)} 抛）；</li>
     * <li>{@code trunkMin/trunkSpan}：干 = {@code trunkMin + nextInt(trunkSpan)}；干高带三群系<b>区间分离</b>
     * （平原 4..6 &lt; 沼泽 7..10 &lt; 森林 11..16），机检 T1 组钉；</li>
     * <li>{@code canopyRadius}：冠层水平半径（改前恒 2）；干位按它内收 ⇒ 零越界；</li>
     * <li>{@code woodPrimary/woodSecondary/woodMixDenom}：木种与混生概率（次档 = {@link #WOOD_NONE} ⇒ 不掷）；
     * 树形由木种决定（见 {@link #canopyFor}）⇒ "种类样式多一点"落在同一 chunk 内的两种干色两副冠形；</li>
     * <li>{@code grassRolls/flowerRolls/sandRolls}：植被趟的尝试次数（改前草 2-4、花 0、沙 0）。</li>
     * </ul>
     */
    public static final class VegTier {

        /** 每 chunk 树位掷骰次数（0 = 该群系零树）。 */
        public final int treeRolls;
        /** 单枚树骰的 1/N 命中门。 */
        public final int treeChanceDenom;
        /** 干高下限。 */
        public final int trunkMin;
        /** 干高浮动（{@code nextInt(span)} ⇒ 闭区间 [min, min+span-1]）。 */
        public final int trunkSpan;
        /** 冠层水平半径（格）。 */
        public final int canopyRadius;
        /** 主木种。 */
        public final int woodPrimary;
        /** 混生木种（{@link #WOOD_NONE} = 关）。 */
        public final int woodSecondary;
        /** 混生命中分母（{@code nextInt(denom)==0} ⇒ 取次档）。 */
        public final int woodMixDenom;
        /** 每 chunk 草尝试次数（旧草与新草各半）。 */
        public final int grassRolls;
        /** 每 chunk 花尝试次数。 */
        public final int flowerRolls;
        /** 每 chunk 沙砾斑次数。 */
        public final int sandRolls;
        /** 该档花种。 */
        public final int flowerKind;
        /** 该档新草种。 */
        public final int grassKind;
        /** 该档沙料族（细沙位图，粗沙/砾按掷点派生）。 */
        public final int sandKind;

        /**
         * public 只为离线判据的<b>反假绿对照臂</b>（{@code P17VegetationFrequencyCheck} 的 ANTI 组要
         * 整行换档来证明"荒漠零树"是档值在起作用、不是不可达分支）；生产侧只读
         * {@link #VEG_TIERS_BY_ROSTER}，不自建档。
         */
        public VegTier(int treeRolls, int treeChanceDenom, int trunkMin, int trunkSpan, int canopyRadius,
            int woodPrimary, int woodSecondary, int woodMixDenom, int grassRolls, int flowerRolls, int sandRolls,
            int flowerKind, int grassKind, int sandKind) {
            this.treeRolls = treeRolls;
            this.treeChanceDenom = treeChanceDenom;
            this.trunkMin = trunkMin;
            this.trunkSpan = trunkSpan;
            this.canopyRadius = canopyRadius;
            this.woodPrimary = woodPrimary;
            this.woodSecondary = woodSecondary;
            this.woodMixDenom = woodMixDenom;
            this.grassRolls = grassRolls;
            this.flowerRolls = flowerRolls;
            this.sandRolls = sandRolls;
            this.flowerKind = flowerKind;
            this.grassKind = grassKind;
            this.sandKind = sandKind;
        }
    }

    /**
     * 群系植被档表（下标 = L1 维内名册下标：锈蚀草原 / 齿轮森林 / 黄铜荒漠 / 起雾沼泽）。
     * 期望树数/chunk = {@code treeRolls/treeChanceDenom} = 原 0.125 / 森 1.000 / 沙 <b>0</b> / 沼 0.333
     * ⇒ 需求偏序 <b>森 &gt; 沼 &gt; 原 &gt; 沙=0</b>；改前实测四群系几乎等密
     * （0.0743/0.0751/0.0505/0.0473）。取档理由与映射论证见 {@code plan/tmp/p17-sb2/DESIGN.md} §2/§3；
     * 实测落带见 {@code plan/tmp/p17-sb2/SB2-RESULT.md} 与本判据 T3 组。
     */
    public static final VegTier[] VEG_TIERS_BY_ROSTER = {
        // 0 锈蚀草原：矮树（4..6）+ 小穹冠 + 旱生刚毛草与锈华花
        new VegTier(1, 8, 4, 3, 1, WOOD_RUST, WOOD_NONE, 0, 6, 4, 0, FLOWER_RUST, GRASS_BRISTLE, SAND_FINE),
        // 1 齿轮森林（用户口径"青铜森林"）：树最多（1.0/chunk）、最大（干 11..16 + 半径 3 冠）、
        // 黄铜主 + 铜绿混生 1/3 ⇒ 同群系两副干色两副冠形
        new VegTier(2, 2, 11, 6, 3, WOOD_BRASS, WOOD_COPPER, 3, 8, 6, 0, FLOWER_PATINA, GRASS_SEDGE, SAND_FINE),
        // 2 黄铜荒漠（用户口径"沙漠"）：treeRolls = 0 ⇒ 一条树骰都不掷（"没有树"真达成，不是"很少"）；
        // 花草压到全档最低但非 0（金盏 + 刚毛草 = 荒漠唯一的植被表达，且仍 > 改前的"花恒 0"），
        // 沙砾斑 4 次 = 需求侧的"铺沙"
        new VegTier(0, 0, 4, 3, 1, WOOD_RUST, WOOD_NONE, 0, 3, 1, 4, FLOWER_BRASS, GRASS_BRISTLE, SAND_FINE),
        // 3 起雾沼泽：中等树（7..10）+ 平展伞冠 + 密矮苔薹草与沼地兰
        new VegTier(1, 3, 7, 4, 2, WOOD_MARSH, WOOD_NONE, 0, 8, 5, 0, FLOWER_MARSH, GRASS_SEDGE, SAND_FINE) };

    /**
     * 默认档的草尝试次数 = 3。改前是"每 chunk {@code 2 + nextInt(3)} 次"（2/3/4 等概率，均值 3），
     * 档表只能表达定值 ⇒ 默认档取<b>均值</b>（不是上限也不是下限）；这一处不是逐位重合，已在
     * {@code plan/tmp/p17-sb2/SB2-RESULT.md} 与 T1 断言里显式申报。
     */
    private static final int DEFAULT_GRASS_ROLLS = (TUFT_ATTEMPTS_MIN + TUFT_ATTEMPTS_MAX) / 2;

    /**
     * 默认档（身份不可得 = {@code rosterIndex < 0} 或越界）。
     * <p>
     * <b>逐字段刻意等于改前</b>：树 1 掷 × 1/{@value #TREE_CHANCE_DENOM}、干 {@value #TUFT_ATTEMPTS_MIN}..5
     * （旧 {@code 3 + nextInt(3)}）、冠半径 2（旧 5×5）、木种 rust、旧草 2..4 次
     * （{@code grassRolls=}​{@value #TUFT_ATTEMPTS_MAX}−1 取均值 3，见 {@link #DEFAULT_GRASS_ROLLS} 说明）、
     * 花 0、沙 0。⇒ 与 S-A 的 {@code DEFAULT_RELIEF_AMPLITUDE = 1.0} 同一纪律：降级/未装配口径与改造前
     * 重合，身份缺失时<b>不需要</b>任何"抑制"判断（plan §2 第 8 条），生产路径取不到该档
     * （P17-A 实测 524288 chunk {@code neg1 = 0}）。
     */
    public static final VegTier DEFAULT_TIER = new VegTier(
        1,
        TREE_CHANCE_DENOM,
        3,
        3,
        2,
        WOOD_RUST,
        WOOD_NONE,
        0,
        DEFAULT_GRASS_ROLLS,
        0,
        0,
        FLOWER_RUST,
        GRASS_BRISTLE,
        SAND_FINE);

    private ProsperityDecorPlacer() {}

    /**
     * 名册下标 → 植被档（与 {@code ProsperityTerrainProfile.reliefAmplitudeForRosterIndex}、
     * {@code ProsperityWorldGenerator.weightForRosterIndex} 同一形状：越界/缺席一律回退默认档，
     * <b>不</b>写"某个群系 ⇒ 抑制"的身份等值判断）。
     */
    public static VegTier tierForRosterIndex(int index) {
        return index >= 0 && index < VEG_TIERS_BY_ROSTER.length ? VEG_TIERS_BY_ROSTER[index] : DEFAULT_TIER;
    }

    /**
     * populate 入口（<b>P17 S-B2 起带身份入参</b>，与 {@code ProsperitySurfaceScatter.scatter} 的
     * {@code biomeScatterWeight} 同族）：拆成"树趟 → 植被趟（花 + 草）→ 碎石趟 → 沙砾趟"，
     * 趟序固定 ⇒ 同 seed 同坐标逐位一致。
     *
     * @param rosterIndex L1 维内名册下标（编排器 {@code GTSRBiomeAuthority.ordinalAt} 的
     *                    {@code ordinal}，chunk 中心采样，与机器/散布权重<b>同一次</b>解析；
     *                    {@code -1} ⇒ {@link #DEFAULT_TIER}）
     */
    public static void decorate(World world, long worldSeed, int chunkX, int chunkZ, int rosterIndex, BlockSink sink) {
        if (sink == null) {
            return;
        }
        final Random rand = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, chunkX, chunkZ) ^ SALT_DECOR);
        final StructureBuilder builder = new StructureBuilder(sink);
        final VegTier tier = tierForRosterIndex(rosterIndex);
        placeTreePass(world, builder, rand, chunkX, chunkZ, tier);
        placeVegetationPass(world, builder, rand, chunkX, chunkZ, tier);
        placeRubble(world, builder, rand, chunkX, chunkZ);
        placeSandPass(world, builder, rand, chunkX, chunkZ, tier);
    }

    // ═════════════════════════════ 树趟（P17 S-B2）═════════════════════════════

    /**
     * 树趟：{@code tier.treeRolls} 枚独立 1/{@code treeChanceDenom} 树骰 ⇒ 一 chunk 可中 0..rolls 棵。
     * {@code treeRolls <= 0}（荒漠档）⇒ 循环体不进、<b>一次随机都不掷</b>，后续趟的随机流与"零树"同源确定。
     */
    private static void placeTreePass(World world, StructureBuilder builder, Random rand, int chunkX, int chunkZ,
        VegTier tier) {
        for (int i = 0; i < tier.treeRolls; i++) {
            if (tier.treeChanceDenom > 0 && rand.nextInt(tier.treeChanceDenom) == 0) {
                placeTree(world, builder, rand, chunkX, chunkZ, tier);
            }
        }
    }

    /** 木种主/次档的按株选取（次档关闭 ⇒ 不掷，恒主档）。 */
    private static int pickWood(Random rand, VegTier tier) {
        if (tier.woodSecondary == WOOD_NONE || tier.woodMixDenom <= 0) {
            return tier.woodPrimary;
        }
        return rand.nextInt(tier.woodMixDenom) == 0 ? tier.woodSecondary : tier.woodPrimary;
    }

    /**
     * 单棵树：干位按该档冠半径内收（{@code radius .. 15-radius} ⇒ 冠层零跨 chunk），
     * 整柱干空气门，占用即整树跳过；干高 = {@code trunkMin + nextInt(trunkSpan)}。
     * 造型 = 木种决定冠形（{@link #canopyFor}）+ 该档冠半径 ⇒ 四群系四副骨架。
     */
    private static void placeTree(World world, StructureBuilder builder, Random rand, int chunkX, int chunkZ,
        VegTier tier) {
        final int radius = Math.max(1, tier.canopyRadius);
        final int x = (chunkX << 4) + radius + rand.nextInt(16 - 2 * radius);
        final int z = (chunkZ << 4) + radius + rand.nextInt(16 - 2 * radius);
        final int surfaceY = naturalTopAt(world, x, z);
        if (surfaceY < 0) {
            return;
        }
        final int wood = pickWood(rand, tier);
        final int trunkHeight = tier.trunkMin + rand.nextInt(Math.max(1, tier.trunkSpan));
        for (int i = 1; i <= trunkHeight; i++) {
            if (!world.isAirBlock(x, surfaceY + i, z)) {
                return; // 让行纪律：整树跳过，不切结构
            }
        }
        final Block log = logOf(wood);
        final Block leaves = leavesOf(wood);
        final int topY = surfaceY + trunkHeight;
        canopyFor(wood, builder, world, x, topY, z, radius, rand, leaves);
        // 干（冠层之后写，干格冠层让行已由 isAirBlock 保证，重写干位无副作用但按惯例后置）
        for (int i = 1; i <= trunkHeight; i++) {
            builder.setBlock(x, surfaceY + i, z, log, 0, BlockSink.FLAG_POPULATE);
        }
    }

    /**
     * 树形（P17 S-B2 "种类样式多一点"的落点）：一副冠形绑一个木种，r = 该档冠层半径。
     * 全部格点相对干顶的偏移都 ≤ r，水平不越 chunk（干位已内收），垂直由 {@code MAX_SURFACE_Y} 与
     * ChunkClampedSink 的 y 域门兜底。
     * <ul>
     * <li>{@link #WOOD_RUST} 穹顶冠：干顶两层 (2r+1) 缺角 + 顶 3×3 + 十字（= 改前形，r=2 时逐字同）；</li>
     * <li>{@link #WOOD_COPPER} 层叠塔冠：自上而下半径 1→r 的四层盘（针叶感，高干才压得住）；</li>
     * <li>{@link #WOOD_BRASS} 广展穹冠：干顶三层 (2r+1)/(2r+1)/(2r-1) + 顶十字 + 四角垂枝；</li>
     * <li>{@link #WOOD_MARSH} 平展伞冠：单层大平顶 + 顶小盘 + 四边下垂侧枝（沼泽"撑开的伞"）。</li>
     * </ul>
     */
    private static void canopyFor(int wood, StructureBuilder builder, World world, int x, int topY, int z, int r,
        Random rand, Block leaves) {
        switch (wood) {
            case WOOD_COPPER: {
                // 塔冠：topY..topY-3（自上而下 1,1,2,r），顶一格收尖
                placeLeafDisc(builder, world, x, topY + 1, z, 0, leaves);
                placeLeafDisc(builder, world, x, topY, z, 1, leaves);
                placeLeafDisc(builder, world, x, topY - 1, z, 1, leaves);
                placeLeafDisc(builder, world, x, topY - 2, z, 2, leaves);
                placeLeafDisc(builder, world, x, topY - 3, z, Math.max(2, r), leaves);
                return;
            }
            case WOOD_BRASS: {
                placeLeafDisc(builder, world, x, topY + 2, z, 1, leaves);
                placeLeafDisc(builder, world, x, topY + 1, z, Math.max(1, r - 1), leaves);
                placeLeafDisc(builder, world, x, topY, z, r, leaves);
                placeLeafDisc(builder, world, x, topY - 1, z, r, leaves);
                // 四角垂枝（对角各 1 格，跨度仍在 r 内）
                for (int d = -1; d <= 1; d += 2) {
                    for (int e = -1; e <= 1; e += 2) {
                        placeLeafIfAir(builder, world, x + d * r, topY - 2, z + e * r, leaves);
                    }
                }
                placeCrossTop(builder, world, x, topY + 3, z, leaves);
                return;
            }
            case WOOD_MARSH: {
                placeLeafDisc(builder, world, x, topY + 1, z, 1, leaves);
                placeLeafDisc(builder, world, x, topY, z, r, leaves);
                // 伞骨下垂：四正边各 1-2 格（30% 概率多挂一格）
                for (int d = -1; d <= 1; d += 2) {
                    for (int e = -1; e <= 1; e += 2) {
                        placeLeafIfAir(builder, world, x + d * r, topY - 1, z + e * (r - 1), leaves);
                        if (rand.nextInt(3) == 0) {
                            placeLeafIfAir(builder, world, x + d * r, topY - 2, z + e * (r - 1), leaves);
                        }
                    }
                }
                return;
            }
            default: {
                // 穹顶冠（= 改前骨架，r=2 时格集合逐字相同）
                for (int dy = -1; dy <= 0; dy++) {
                    placeLeafDisc(builder, world, x, topY + dy, z, r, leaves);
                }
                placeLeafDisc(builder, world, x, topY + 1, z, Math.max(1, r - 1), leaves);
                placeCrossTop(builder, world, x, topY + 2, z, leaves);
                return;
            }
        }
    }

    /** 一层冠：以干为心、半径 rr 的方盘，去掉 |dx|==rr 且 |dz|==rr 的四角（rr==0 ⇒ 单格）。 */
    private static void placeLeafDisc(StructureBuilder builder, World world, int x, int y, int z, int rr,
        Block leaves) {
        if (rr <= 0) {
            placeLeafIfAir(builder, world, x, y, z, leaves);
            return;
        }
        for (int dx = -rr; dx <= rr; dx++) {
            for (int dz = -rr; dz <= rr; dz++) {
                if (Math.abs(dx) == rr && Math.abs(dz) == rr) {
                    continue; // 缺角（改前同款剪形）
                }
                placeLeafIfAir(builder, world, x + dx, y, z + dz, leaves);
            }
        }
    }

    /** 顶层十字（中心 + 四正各一格）。 */
    private static void placeCrossTop(StructureBuilder builder, World world, int x, int y, int z, Block leaves) {
        placeLeafIfAir(builder, world, x, y, z, leaves);
        placeLeafIfAir(builder, world, x + 1, y, z, leaves);
        placeLeafIfAir(builder, world, x - 1, y, z, leaves);
        placeLeafIfAir(builder, world, x, y, z + 1, leaves);
        placeLeafIfAir(builder, world, x, y, z - 1, leaves);
    }

    // ═════════════════════════════ 植被趟（花 + 草）═════════════════════════════

    /**
     * 植被趟（P17 S-B2 新增，改前"花的设施数 = 0"）：花 {@code tier.flowerRolls} 次 +
     * 草 {@code tier.grassRolls} 次；可落地面一律走 {@link #tuftForGround} 的"返回非空"判定
     * （= 四群系自然 top，比框架门更窄 ⇒ 城内冻结板面上不长花草，改前纪律不变）。
     * 草位上旧草（干燥系/湿生系二分）与新草（该档种）各半 ⇒ 六个草丛/花方块全员有真实消费者。
     */
    private static void placeVegetationPass(World world, StructureBuilder builder, Random rand, int chunkX, int chunkZ,
        VegTier tier) {
        final Block flower = flowerOf(tier.flowerKind);
        for (int i = 0; flower != null && i < tier.flowerRolls; i++) {
            placeCrossPlant(builder, world, rand, chunkX, chunkZ, flower, null);
        }
        final Block newTuft = tuftOf(tier.grassKind);
        for (int i = 0; i < tier.grassRolls; i++) {
            // newTuft 为 null（离线未装配）时 placeCrossPlant 内部自动退化为旧草，不掷空方块
            placeCrossPlant(builder, world, rand, chunkX, chunkZ, null, newTuft);
        }
    }

    /**
     * 单格十字植被落点（花 = {@code flower} 非空；草 = {@code flower==null} 且在 {@code newTuft} 位上
     * 以 1/{@value #LEGACY_TUFT_CHANCE_DENOM} 概率回退旧草选择器）。
     */
    private static void placeCrossPlant(StructureBuilder builder, World world, Random rand, int chunkX, int chunkZ,
        Block flower, Block newTuft) {
        final int x = (chunkX << 4) + rand.nextInt(16);
        final int z = (chunkZ << 4) + rand.nextInt(16);
        final int surfaceY = findSurfaceY(world, x, z);
        if (surfaceY <= 0 || surfaceY > MAX_SURFACE_Y) {
            return;
        }
        final Block legacy = tuftForGround(world.getBlock(x, surfaceY, z));
        if (legacy == null || !world.isAirBlock(x, surfaceY + 1, z)) {
            return;
        }
        final Block plant = flower != null ? flower : (rand.nextInt(LEGACY_TUFT_CHANCE_DENOM) == 0 ? legacy : newTuft);
        if (plant == null) {
            return;
        }
        builder.setBlock(x, surfaceY + 1, z, plant, 0, BlockSink.FLAG_POPULATE);
    }

    /**
     * 草丛二分映射（plan §12 修订 5 双草丛实例的群系分配）：锈草丛 = 草原/荒漠（干燥系），
     * 铜绿草丛 = 森林/沼泽（湿生系）；非自然区 top 返回 null（城内/残骸上不长）。
     * <p>
     * <b>P17 S-B2 的两条纪律</b>：方法体一字未动（{@code SurfaceGateUnifyCheck} 钉它恰 4 处 top 成员比较、
     * H4 钉它四员映射）；本方法的<b>返回值</b>在新植被趟里只当"这格地面有没有群系身份"的谓词用，
     * 新花/新草的<b>种类</b>改由名册档表决定 —— 即"分流走身份，不走地表方块比选"。
     */
    private static Block tuftForGround(Block ground) {
        if (ground == BlocksGTSR.prosperitySteppeTop || ground == BlocksGTSR.prosperityWastesTop) {
            return BlocksGTSR.prosperityTuftRust;
        }
        if (ground == BlocksGTSR.prosperityForestTop || ground == BlocksGTSR.prosperitySwampTop) {
            return BlocksGTSR.prosperityTuftCopper;
        }
        return null;
    }

    // ═════════════════════════════ 码 → 方块（惰性解析）═════════════════════════════
    // 一律"用时取字段"，不得在静态字段里缓存 BlocksGTSR 引用：BlocksGTSR 的赋值发生在
    // loader/BlockLoader 的注册段，早于它就绪的静态初始化会把 null 冻进档表（S-B1 同名册的离线装配
    // 也按此口径）。花/草/沙/树四类共 13 个方块在此单一出处，档表只存码不存引用。

    /** 木种码 → 树干方块（未知码按 {@link #WOOD_RUST} 处理，与 {@link #tierForRosterIndex} 同一降级方向）。 */
    public static Block logOf(int wood) {
        switch (wood) {
            case WOOD_COPPER: {
                return BlocksGTSR.prosperityCopperLog;
            }
            case WOOD_BRASS: {
                return BlocksGTSR.prosperityBrassLog;
            }
            case WOOD_MARSH: {
                return BlocksGTSR.prosperityMarshLog;
            }
            default: {
                return BlocksGTSR.prosperityRustLog;
            }
        }
    }

    /** 木种码 → 树冠方块（铜/黄铜/沼三档走 {@code BlockProsperityCanopyLeaves} 双图标，rust 档单图标）。 */
    public static Block leavesOf(int wood) {
        switch (wood) {
            case WOOD_COPPER: {
                return BlocksGTSR.prosperityCopperLeaves;
            }
            case WOOD_BRASS: {
                return BlocksGTSR.prosperityBrassLeaves;
            }
            case WOOD_MARSH: {
                return BlocksGTSR.prosperityMarshLeaves;
            }
            default: {
                return BlocksGTSR.prosperityRustLeaves;
            }
        }
    }

    /** 花种码 → 花方块（四种自有花；未知码回退锈华花）。 */
    public static Block flowerOf(int kind) {
        switch (kind) {
            case FLOWER_PATINA: {
                return BlocksGTSR.prosperityFlowerPatina;
            }
            case FLOWER_BRASS: {
                return BlocksGTSR.prosperityFlowerBrass;
            }
            case FLOWER_MARSH: {
                return BlocksGTSR.prosperityFlowerMarsh;
            }
            default: {
                return BlocksGTSR.prosperityFlowerRust;
            }
        }
    }

    /** 新草种码 → 草方块（刚毛/苔薹；未知码回退刚毛草）。 */
    public static Block tuftOf(int kind) {
        return kind == GRASS_SEDGE ? BlocksGTSR.prosperityTuftSedge : BlocksGTSR.prosperityTuftBristle;
    }

    /** 沙料码 → 非 top 沙/砾方块（未知码回退细硅沙）。 */
    public static Block sandOf(int kind) {
        switch (kind) {
            case SAND_COARSE: {
                return BlocksGTSR.prosperityCoarseSand;
            }
            case SAND_GRAVEL: {
                return BlocksGTSR.prosperityRiverGravel;
            }
            default: {
                return BlocksGTSR.prosperitySilicaSand;
            }
        }
    }

    // ═════════════════════════════ 沙砾趟（P17 S-B2 新增）═════════════════════════════

    /**
     * 荒漠沙砾趟（需求"更多方块种类（沙类）"与本片区铺沙的落点）：{@code tier.sandRolls} 个斑，
     * 每斑 1-3 块贴面覆盖（{@code surfaceY+1} 空气位）。
     * <p>
     * <b>为什么是"覆盖斑"而不是替换 top</b>：替换会把荒漠列从 {@link SurfaceGate} 的 top 名册里抹掉 ⇒
     * {@code SurfaceGateUnifyCheck} D 带（chain 现值 98.967、下界 98.6）连红，且新 top 进名册会让
     * {@code DIM78_SIZE 5→6} 连带三条判据 ⇒ 沙类三块<b>一律非 top</b>、只走覆盖。
     * <b>为什么不在 {@code ProsperitySurfaceScatter} 里铺</b>：散布层的写入落在 D 组"口径 B（散布前）"
     * 之后、"口径 C（装饰前）"之前的采样里（{@code SurfaceGateUnifyCheck:1149}），会把吞列幅度直接抬进
     * 那条带；装饰趟的写入在所有 dim78 采样口径之后。
     * 料种：主档细沙 2/3、粗沙 1/3、1/6 概率整斑换河床砾（S-C 河床料在本片区被真实消费一次）。
     */
    private static void placeSandPass(World world, StructureBuilder builder, Random rand, int chunkX, int chunkZ,
        VegTier tier) {
        for (int i = 0; i < tier.sandRolls; i++) {
            final int cx = (chunkX << 4) + rand.nextInt(16);
            final int cz = (chunkZ << 4) + rand.nextInt(16);
            final int surfaceY = naturalTopAt(world, cx, cz);
            if (surfaceY < 0) {
                continue;
            }
            final Block bulk = rand.nextInt(3) == 0 ? sandOf(SAND_COARSE) : sandOf(tier.sandKind);
            final Block patch = rand.nextInt(6) == 0 ? sandOf(SAND_GRAVEL) : bulk;
            final int pieces = 1 + rand.nextInt(RUBBLE_PIECES_MAX);
            for (int p = 0; p < pieces; p++) {
                final int px = Math.min(15, Math.max(0, (cx & 15) + rand.nextInt(3) - 1)) + (chunkX << 4);
                final int pz = Math.min(15, Math.max(0, (cz & 15) + rand.nextInt(3) - 1)) + (chunkZ << 4);
                final int py = naturalTopAt(world, px, pz);
                if (py < 0 || !world.isAirBlock(px, py + 1, pz)) {
                    continue;
                }
                builder.setBlock(px, py + 1, pz, patch, 0, BlockSink.FLAG_POPULATE);
            }
        }
    }

    /** 碎石：0-2 堆，每堆 1-3 块原版 gravel 小簇；gravel 具 BlockFalling 属性必须逐块贴地。 */
    private static void placeRubble(World world, StructureBuilder builder, Random rand, int chunkX, int chunkZ) {
        final int piles = rand.nextInt(RUBBLE_PILES_MAX + 1);
        for (int i = 0; i < piles; i++) {
            final int cx = (chunkX << 4) + 1 + rand.nextInt(14); // 小簇 ±1 不跨界
            final int cz = (chunkZ << 4) + 1 + rand.nextInt(14);
            final int surfaceY = findSurfaceY(world, cx, cz);
            if (surfaceY <= 0 || surfaceY > MAX_SURFACE_Y || !isNaturalTop(world.getBlock(cx, surfaceY, cz))) {
                continue;
            }
            final int pieces = 1 + rand.nextInt(RUBBLE_PIECES_MAX);
            for (int p = 0; p < pieces; p++) {
                final int px = cx + rand.nextInt(3) - 1;
                final int pz = cz + rand.nextInt(3) - 1;
                final int py = findSurfaceY(world, px, pz); // gravel 逐块贴地（防悬空落后掉落破观感）
                if (py <= 0 || py > MAX_SURFACE_Y || !isNaturalTop(world.getBlock(px, py, pz))) {
                    continue;
                }
                if (!world.isAirBlock(px, py + 1, pz)) {
                    continue;
                }
                builder.setBlock(px, py + 1, pz, Blocks.gravel, 0, BlockSink.FLAG_POPULATE);
            }
        }
    }

    /** 单块叶：仅空气位放置（让行规则）；冠层坐标经干位钳制保证不越界（见类注释跨界协议）。 */
    private static void placeLeafIfAir(StructureBuilder builder, World world, int x, int y, int z, Block leaves) {
        if (!world.isAirBlock(x, y, z)) {
            return;
        }
        builder.setBlock(x, y, z, leaves, 0, BlockSink.FLAG_POPULATE);
    }

    /**
     * "这一列的地面是哪块自然 top，可以放树/沙"——{@link #isNaturalTop} 之上的<b>唯一</b>取列谓词
     * （返回 surfaceY，不过门返回 -1）。本文件 {@code isNaturalTop(} 恰 5 处：定义 + 框架委托 +
     * 本方法 1 处 + 碎石 2 处；新增趟全部经本方法，故 {@code SurfaceGateUnifyCheck} 的那条次数 pin
     * <b>零改动</b>（多一处少一处都红，见其 :557-559）。
     */
    private static int naturalTopAt(World world, int x, int z) {
        final int surfaceY = findSurfaceY(world, x, z);
        if (surfaceY <= 0 || surfaceY > MAX_SURFACE_Y || !isNaturalTop(world.getBlock(x, surfaceY, z))) {
            return -1;
        }
        return surfaceY;
    }

    /**
     * 自然区 top 方块判定（<b>P4 起为一行委托</b>，plan §5 P4 / 审计 A-5 §2 #1）：成员集合与
     * {@link ProsperityOutpostPlacer#isNaturalProsperityTop} 同源于
     * {@link SurfaceGate#landableTops(String)}，本方法自身<b>不含任何方块集合知识</b>；保留本名是
     * 因为既有工具 {@code tools/dim1/ReplaceSurfaceRuntimeCheck:401} 与 {@code :49} 的引用、
     * 以及本类三处调用点的可读性。<b>public 供离线冒烟断言</b>。
     * <p>
     * <b>本片修复的缺陷（审计 D-5）</b>：改造前本方法是 dim78 内唯一<b>不</b>认
     * {@code prosperitySurface} 的门（旧 :192-196 只列四自然 top），于是同一格地表装饰层判"不可落"、
     * 结构层（outpost/机器/散布，同集 5 员）判"可落"——两族结论相反。现补齐为同一 5 员集合。
     * <p>
     * <b>补齐后仍存在的本层更窄面（登记，不视作等价）</b>：草丛走
     * {@link #tuftForGround(Block)}，它是"落哪种草丛"的<b>变体选择器</b>而非门——
     * {@code prosperitySurface} 无群系身份，无法二分 rust/copper，故选择器<b>故意</b>只认四自然 top
     * 并返回 null。因此补齐后 {@code prosperitySurface} 上可以长树/落碎石/铺沙砾，但<b>不长花草</b>
     * （P17 S-B2 的新花与新草同守这条窄口径）。
     * 与 y 域门（{@link #MAX_SURFACE_Y}）和空气让行门同样是本层自有门，均未上收（语义各异，
     * 见 {@code SurfaceGateUnifyCheck} B/D 组）。
     */
    public static boolean isNaturalTop(Block ground) {
        return SurfaceGate.isNaturalTop(DIM_KEY, SurfaceGate.landableTops(DIM_KEY), ground);
    }

    // P3（plan §5 P3 / 审计 A-5 §1 #4）：本类原有一份私有 findSurfaceY(World,int,int)，与
    // ProsperitySurfaceScatter #2 / RuinedMachinePlacer #3 / ProsperityOutpostPlacer #5 /
    // ShatteredDecorPlacer #6 共 5 份实现体逐字符等价（差别只是 Material 用全限定名书写），
    // 已并到框架唯一件 GTSRChunkProviderBase.findSurfaceY（本文件静态导入，四处调用点一字未改）。
}
