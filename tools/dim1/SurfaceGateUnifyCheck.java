import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRustLeaves;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRustLog;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperitySurface;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityTuft;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinedCasing;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinDebris;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperitySurfaceScatter;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityWorldGenerator;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.common.dimension.shattered.ChunkProviderShatteredGrounds;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredAshLog;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredCrossDecor;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredMonolith;
import com.miaokatze.gtsr.common.dimension.shattered.ShatteredDecorPlacer;

/**
 * <b>P4 地表门统一 + 缺陷修复的机检与影响量化</b>（plan §5 P4 / §2.1 横切 roster /
 * §2.4 判据 5「新判据一律先 RED 后 GREEN」/ 审计 A-5 §2、A-2 #3、D-5）。列名申报见下各组。
 * <p>
 * <b>两种模式</b>：
 * <ul>
 * <li>{@code java SurfaceGateUnifyCheck [assert] [srcRoot=src/main/java]}——<b>钉</b>：
 * A 成员枚举（按 {@code BlocksGTSR} 字段名逐位对齐申报集合）/ B 反例一律关门 /
 * C dim78 装饰层别名 ≡ 结构层别名 ≡ 框架谓词（对全部已知方块实例逐位相同 ⇒ 审计 D-5 的
 * "两族结论相反"已消除）+ dim79 两入口等价 + 跨维不串门 / F 两维集合无交集 /
 * E 源级"全仓只剩一处方块集合知识"（自造门残留必须为 0、别名必须是一行委托、
 * 调用点清单与维度键参数逐条对齐申报）/ G <b>灵敏度自检</b>（影子集合增删成员必须改变谓词
 * 输出，否则 D 组申报带对集合改动不敏感＝假绿）/ D 门通过率落在申报带内 + 成员份额守恒；</li>
 * <li>{@code java SurfaceGateUnifyCheck measure [seeds] [regionsPerSeed] [chunksPerAxis]}——
 * <b>量化</b>：真实 {@code generateTerrain} + 真实表层缝 + 真实编排顺序
 * （outpost →（互斥）机器 → 散布 → 装饰；城 buffer 窗用同源 {@link CityPlanner#citiesNear}
 * 排除；前序阶段的方块对后续阶段可见）上测装饰落量与门通过率，只打 {@code MEASURE} 行不断言。
 * <b>同一份工具源码</b>分别在 BASE（本片开工前快照编译的 class）与 AFTER（当前树）各跑一遍
 * ＝判据 3 的前后对比；因此本模式<b>只用生产谓词</b>（{@link ProsperityDecorPlacer#isNaturalTop}
 * / {@link ShatteredDecorPlacer#isNaturalTop}）判门，不直接用 {@link SurfaceGate}
 * ——否则 BASE 跑会因基准树里也存在本片新增的 SurfaceGate 而假绿。</li>
 * </ul>
 * 退出码：0 = 全绿；1 = 有申报项被破坏；2 = 环境/参数不可用。
 * <p>
 * <b>离线装配口径</b>（承袭 {@code SurfaceYParityCheck} / {@code SurfaceByteParityDump}，
 * 三条已知坑同源）：{@code Blocks.*} 用 {@code Unsafe.putObject} 直写 ⇒ 必须先
 * {@code Class.forName("net.minecraft.init.Blocks")}；离线的 {@code Blocks.air} 被换成
 * {@code BlockStone} 实例 ⇒ 本工具的写入器把"句柄 == Blocks.air"显式当作<b>清空</b>
 * （生产语义，残缺机器的 '.' 位依赖它）；{@code World} 用 {@code Unsafe.allocateInstance}
 * 跳过构造器体，再直赋 {@code worldInfo}/{@code provider} 与本工具的网格字段。
 */
public final class SurfaceGateUnifyCheck {

    // ═════════════════════════ 申报表（本工具自带的第二份真值）═════════════════════════

    /**
     * A 组：期望成员（{@code BlocksGTSR} 字段名，按声明序）。<b>故意与 {@link SurfaceGate}
     * 再重复一份</b>——plan §5 P4 判据 4 要求"人为改方块集合必须变红"，只有独立申报表做得到
     * （同 {@code HeightHashSingleSourceCheck} 的口径：期望值不能从被测实现里读回来）。
     */
    private static final String[] EXPECTED78 = { "prosperitySurface", "prosperitySteppeTop", "prosperityForestTop",
        "prosperityWastesTop", "prosperitySwampTop" };
    /** A 组：dim79 期望成员（四 top；dim79 无 meta 冻结地表块，见 SurfaceGate roster 表）。 */
    private static final String[] EXPECTED79 = { "shatteredAshTop", "shatteredSlagTop", "shatteredGlassTop",
        "shatteredTarTop" };

    /**
     * B 组：必须关门的方块（前缀 {@code gtsr:} = {@link BlocksGTSR} 字段，{@code mc:} =
     * {@link Blocks} 字段）。只测<b>离线装配后实际存在</b>的实例（{@code SurfaceHarness}
     * 只直写它申报的那几个 {@code Blocks} 字段），缺席者在 A/B 之前的
     * {@link #assertFieldsAvailable()} 里单独红，不静默跳过。
     */
    private static final String[] MUST_NOT_PASS = { "gtsr:prosperitySteppeBase", "gtsr:prosperityForestBase",
        "gtsr:prosperityWastesBase", "gtsr:prosperitySwampBase", "gtsr:prosperityTuftRust",
        "gtsr:prosperityTuftCopper", "gtsr:prosperityRustLog", "gtsr:prosperityRustLeaves", "gtsr:ruinedCasing",
        "gtsr:ruinDebris", "gtsr:shatteredCorestone", "gtsr:shatteredMonolith", "gtsr:shatteredAshThorn",
        "gtsr:shatteredSpikeCluster", "gtsr:shatteredAshBase", "gtsr:shatteredAshLog", "gtsr:shatteredSlagBase",
        "gtsr:shatteredGlassBase", "gtsr:shatteredTarBase", "mc:stone", "mc:gravel", "mc:bedrock",
        "mc:cobblestone" };

    /**
     * D 组：门通过率申报带（百分点，闭区间）。口径 = 列级（每列 {@code findSurfaceY} 顶格方块
     * 过不过门），样本见 {@code plan/investigation/p4-surface-gate-20260919.md} 表 1/表 2。
     * 容差取「实测 ±0.6pp」量级：吸收跨机器抖动，但<b>不足以容纳删/加一个成员</b>
     * （G 组用影子集合自证这一点）。
     */
    private static final double BAND_PRISTINE_MIN = 99.99D, BAND_PRISTINE_MAX = 100.0D;
    /**
     * D 组：编排链之后 dim78 列级通过率带（残骸 's' 板把更多列抬进可落地面）。
     * <p>
     * <b>P5 期望值更新（非放宽凑绿）</b>：旧带 [68,76] 钉的是"散布以 K=64 含 15% 竖向件落笔"
     * 的状态——彼时约 29pp 的列被散布自己埋成 ruinDebris，故链后通过率只剩 ~70%。P5 选定 K=8
     * 并摘除竖向件后，散布不再吞列，实测口径 C 上移到 <b>94.968pp</b>（8 seed × 8 窗、14091 可散布
     * chunk）。带按 P5 后实测取 [93.5,96.5]；若将来再调散布档，此带必须随实测重标而不是放宽。
     */
    private static final double BAND_CHAIN_MIN = 93.5D, BAND_CHAIN_MAX = 96.5D;
    /**
     * D 组：口径 B（散布前，即 outpost/机器写完、散布尚未落笔）通过率带。
     * 冒烟 E6 的 attempt 级 82.0-85.7% 落在口径 B 与口径 C 之间（scatter 是"边写边探"，
     * 终态口径 C 更低、初态口径 B 更高），故本带是那一族的<b>上界对照</b>，不是同一个估计量。
     */
    private static final double BAND_PRE_SCATTER_MIN = 97.5D, BAND_PRE_SCATTER_MAX = 100.0D;
    /**
     * D 组：链后 {@code prosperitySurface} 列份额带。下界 0 是<b>诚实下界</b>：全新自然区
     * （非城 chunk）的 pristine 地表由 L3 表层缝铺四 top，冻结块只随 outpost/机器模板出现，
     * 故份额取决于该区是否命中 outpost（1/64）与机器（1/24）。上界用于钉住"新增可落面失控"。
     */
    private static final double BAND_FROZEN_SHARE_MIN = 0.0D, BAND_FROZEN_SHARE_MAX = 6.0D;

    /** E 组：生产门调用点清单（文件 → 申报的调用形态标签）。新增 placer 自造门必须先进这张表。 */
    private static final String[][] GATE_CALL_SITES = {
        { "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityDecorPlacer.java", "DECOR" },
        { "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java", "OUTPOST" },
        { "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperitySurfaceScatter.java", "SCATTER" },
        { "com/miaokatze/gtsr/common/dimension/shattered/ShatteredDecorPlacer.java", "SHATTERED" },
        // P7 同步（plan §5 P7 / 审计 A-2 #3 之二）：RuinedMachinePlacer 的"横向直调另一 placer 别名"
        // 残留已消，形态从 MACHINE_RESIDUAL 升为与散布层同级的 MACHINE 直调（下方 E 组两条申报同步）。
        { "com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java", "MACHINE" } };

    /**
     * E 组例外申报：<b>唯一</b>允许继续出现 top 方块成员比较的地方——dim78 的草丛<b>变体选择器</b>
     * {@code tuftForGround}（它不是门，而是"落哪种草"的二分映射：{@code prosperitySurface} 无群系
     * 身份 ⇒ 无法二分 ⇒ 故意只认四自然 top）。次数钉死为 4，多一处少一处都红。
     */
    private static final String[][] MEMBER_COMPARE_EXCEPTIONS = {
        { "ProsperityDecorPlacer.java", "static Block tuftForGround(", "4" } };

    /** E 组：一行委托别名清单（数量与位置钉死；新增别名或把别名改回自持集合都会变红）。 */
    private static final String[] GATE_ALIASES = {
        "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityDecorPlacer.java:isNaturalTop",
        "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java:isNaturalProsperityTop",
        "com/miaokatze/gtsr/common/dimension/shattered/ShatteredDecorPlacer.java:isNaturalTop" };

    /** 残骸登记阈值：链后冻结块份额超过此值 ⇒ 打 NOTE 行（给 P5 的输入，不判红）。 */
    private static final double RESIDUAL_NOTE_PP = 0.30D;

    /** 样本种子（确定性；BASE 与 AFTER 两跑取同一组 ⇒ 逐 chunk 可比）。 */
    private static final long[] SEEDS = { 0x503441L, 0x503442L, 0x503443L, 0x503444L, 0x503445L, 0x503446L, 0x503447L,
        0x503448L, 0x503449L, 0x50344AL };

    // ═══════════════════════════════════ 记账 ═══════════════════════════════════

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();
    /** identity → 声明字段名（如 {@code gtsr:prosperitySurface}），供标签与守恒检查用。 */
    private static final IdentityHashMap<Block, String> NAMES = new IdentityHashMap<>();
    /** 声明字段名 → identity。 */
    private static final Map<String, Block> FIELDS = new TreeMap<>();

    private SurfaceGateUnifyCheck() {}

    public static void main(String[] args) throws Exception {
        final String mode = args.length > 0 ? args[0] : "assert";
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        extraBlocks(); // 必须先于 harvestFields/SurfaceGate 绑定（<clinit> 缓存字段现值）
        harvestFields();
        if ("measure".equals(mode)) {
            final int seeds = args.length > 1 ? Integer.parseInt(args[1]) : 8;
            final int regions = args.length > 2 ? Integer.parseInt(args[2]) : 8;
            final int axis = args.length > 3 ? Integer.parseInt(args[3]) : 16;
            new Sampler(seeds, regions, axis).run()
                .print();
            return;
        }
        final Path root = Paths.get(args.length > 1 ? args[1] : "src/main/java");
        if (!Files.isDirectory(root)) {
            System.out.println("ENV FAIL: 源码根不存在 " + root);
            System.exit(2);
            return;
        }
        System.out.println("# roster 命中实况 " + SurfaceGate.describe(SurfaceGate.DIM78));
        System.out.println("# roster 命中实况 " + SurfaceGate.describe(SurfaceGate.DIM79));
        assertFieldsAvailable();
        assertMemberEnumeration();
        assertNegativeSamples();
        assertAliasEquivalence();
        assertDimExclusion();
        assertSourceLevelSingleTruth(root);
        assertSensitivity();
        assertKeptInstancesDifferOnSameInput();
        assertPassRateBand();
        if (!FAILURES.isEmpty()) {
            System.out.println("SURFACE GATE UNIFY FAIL: " + FAILURES.size() + " assertion(s) failed, passed="
                + passed);
            System.exit(1);
            return;
        }
        System.out.println("SURFACE GATE UNIFY PASS: assertions=" + passed);
    }

    /**
     * 补齐 {@code SurfaceHarness.blockFamily()} 未覆盖的 {@code BlocksGTSR} 字段（构造参数逐字
     * 照抄 {@code loader/BlockLoader} 的生产构造行，本文件不改 P2/P3 的共用 harness）。
     * <p>
     * 不补就没法测的东西：{@code ruinDebris} 缺失 ⇒ 散布层的写入被 {@code ChunkClampedSink}
     * 同源的"句柄必须是 Block"规则丢弃（实测首跑 scatterLanded=0 的根因）；
     * {@code prosperitySurface} 缺失 ⇒ 本片补齐的那一个成员在离线样本里恒不存在，
     * 判据 3 的"修门影响"会被测成假 0；tuft/log/leaves 缺失 ⇒ 装饰落量只剩 gravel。
     */
    private static void extraBlocks() {
        if (BlocksGTSR.prosperitySurface == null) {
            BlocksGTSR.prosperitySurface = new BlockProsperitySurface();
        }
        if (BlocksGTSR.ruinDebris == null) {
            BlocksGTSR.ruinDebris = new BlockRuinDebris();
        }
        if (BlocksGTSR.ruinedCasing == null) {
            BlocksGTSR.ruinedCasing = new BlockRuinedCasing();
        }
        if (BlocksGTSR.prosperityTuftRust == null) {
            BlocksGTSR.prosperityTuftRust = new BlockProsperityTuft("ProsperityTuftRust",
                "gtsr:prosperity_tuft_rust");
        }
        if (BlocksGTSR.prosperityTuftCopper == null) {
            BlocksGTSR.prosperityTuftCopper = new BlockProsperityTuft("ProsperityTuftCopper",
                "gtsr:prosperity_tuft_copper");
        }
        if (BlocksGTSR.prosperityRustLog == null) {
            BlocksGTSR.prosperityRustLog = new BlockProsperityRustLog("ProsperityRustLog",
                "gtsr:prosperity_rust_log_side", "gtsr:prosperity_rust_log_top");
        }
        if (BlocksGTSR.prosperityRustLeaves == null) {
            BlocksGTSR.prosperityRustLeaves = new BlockProsperityRustLeaves("ProsperityRustLeaves",
                "gtsr:prosperity_rust_leaves");
        }
        if (BlocksGTSR.shatteredMonolith == null) {
            BlocksGTSR.shatteredMonolith = new BlockShatteredMonolith();
        }
        if (BlocksGTSR.shatteredSpikeCluster == null) {
            BlocksGTSR.shatteredSpikeCluster = new BlockShatteredCrossDecor("ShatteredSpikeCluster",
                "gtsr:shattered_spike_cluster");
        }
        if (BlocksGTSR.shatteredAshLog == null) {
            BlocksGTSR.shatteredAshLog = new BlockShatteredAshLog("ShatteredAshLog",
                "gtsr:shattered_ash_log_side", "gtsr:shattered_ash_log_top");
        }
        if (BlocksGTSR.shatteredAshThorn == null) {
            BlocksGTSR.shatteredAshThorn = new BlockShatteredCrossDecor("ShatteredAshThorn",
                "gtsr:shattered_ash_thorn");
        }
    }

    private static void harvestFields() {
        grab(BlocksGTSR.class, "gtsr:");
        grab(Blocks.class, "mc:");
    }

    private static void grab(Class<?> holder, String prefix) {
        for (final Field f : holder.getFields()) {
            if (f.getType() != Block.class) {
                continue;
            }
            try {
                final Block v = (Block) f.get(null);
                if (v != null && !FIELDS.containsKey(prefix + f.getName())) {
                    FIELDS.put(prefix + f.getName(), v);
                    NAMES.putIfAbsent(v, prefix + f.getName());
                }
            } catch (IllegalAccessException e) {
                fail("反射读字段失败（getFields 只给 public，不应发生）: " + f);
            }
        }
    }

    /** 前置：申报涉及的方块必须都已落位（缺席时宁可红，也不静默少测）。 */
    private static void assertFieldsAvailable() {
        final List<String> missing = new ArrayList<>();
        for (final String n : EXPECTED78) {
            if (!FIELDS.containsKey("gtsr:" + n)) {
                missing.add(n);
            }
        }
        for (final String n : EXPECTED79) {
            if (!FIELDS.containsKey("gtsr:" + n)) {
                missing.add(n);
            }
        }
        for (final String n : MUST_NOT_PASS) {
            if (!FIELDS.containsKey(n)) {
                missing.add(n);
            }
        }
        check(missing.isEmpty(), "前置：申报方块全部落位（缺席=" + missing + "）");
    }

    // ═══════════════════════════════ A/B/C/F 组（运行时真值）═══════════════════════════════

    /** A 组：两维集合的数量、成员身份与声明序逐一对齐申报表，且每个申报成员都过门。 */
    private static void assertMemberEnumeration() {
        checkSet(EXPECTED78, SurfaceGate.landableTops(SurfaceGate.DIM78), SurfaceGate.DIM78, "A78");
        checkSet(EXPECTED79, SurfaceGate.landableTops(SurfaceGate.DIM79), SurfaceGate.DIM79, "A79");
        check(SurfaceGate.DIM78_SIZE == EXPECTED78.length, "A78 roster 声明数 == 申报表数");
        check(SurfaceGate.DIM79_SIZE == EXPECTED79.length, "A79 roster 声明数 == 申报表数");
    }

    private static void checkSet(String[] expected, Block[] actual, String dimKey, String tag) {
        check(actual.length == expected.length, tag + " " + dimKey + " 集合大小 " + actual.length + " == 申报 "
            + expected.length);
        for (int i = 0; i < Math.min(expected.length, actual.length); i++) {
            final Block b = actual[i];
            check(b != null, tag + " member#" + i + " 非 null（BlocksGTSR 字段已落位）");
            check(("gtsr:" + expected[i]).equals(NAMES.get(b)),
                tag + " member#" + i + " == 申报成员 " + expected[i] + "（实际 " + String.valueOf(NAMES.get(b)) + "）");
            check(SurfaceGate.isNaturalTop(dimKey, actual, b), tag + " 申报成员 " + expected[i] + " 必须过门");
        }
    }

    /** B 组：主体/装饰/残骸/原版方块一律关门；null 与未申报维度键一律关门（fail-closed）。 */
    private static void assertNegativeSamples() {
        for (final String label : MUST_NOT_PASS) {
            final Block b = FIELDS.get(label);
            if (b == null) {
                continue; // assertFieldsAvailable 已就缺席报红
            }
            check(!SurfaceGate.isNaturalTop(SurfaceGate.DIM78, b), "B dim78 必须关门: " + label);
            check(!SurfaceGate.isNaturalTop(SurfaceGate.DIM79, b), "B dim79 必须关门: " + label);
        }
        check(!SurfaceGate.isNaturalTop(SurfaceGate.DIM78, null), "B null 地表必须关门（fail-closed）");
        check(!SurfaceGate.isNaturalTop("nosuch-dim", FIELDS.get("gtsr:prosperitySteppeTop")),
            "B 未申报维度键 ⇒ 门恒关（fail-closed，不静默放宽）");
    }

    /**
     * C 组：dim78 装饰层别名 ≡ 结构层别名 ≡ 框架谓词，对全部已知方块实例逐一相同（= 审计 D-5
     * 缺陷已修的证据；改造前它们在 {@code prosperitySurface} 上结论相反）；dim79 两入口相同；
     * 任一方块不得同时过两维的门。
     */
    private static void assertAliasEquivalence() {
        int mismatch = 0;
        int checked = 0;
        final Block[] roster78 = SurfaceGate.landableTops(SurfaceGate.DIM78);
        final Block[] roster79 = SurfaceGate.landableTops(SurfaceGate.DIM79);
        for (final Block b : FIELDS.values()) {
            checked++;
            final boolean gate = SurfaceGate.isNaturalTop(SurfaceGate.DIM78, roster78, b);
            final boolean decor = ProsperityDecorPlacer.isNaturalTop(b);
            final boolean structure = ProsperityOutpostPlacer.isNaturalProsperityTop(b);
            if (gate != decor || gate != structure) {
                mismatch++;
                fail("C dim78 三入口输出不同: " + NAMES.get(b) + " gate=" + gate + " decor=" + decor + " structure="
                    + structure);
            }
            final boolean g79 = SurfaceGate.isNaturalTop(SurfaceGate.DIM79, roster79, b);
            final boolean d79 = ShatteredDecorPlacer.isNaturalTop(b);
            if (g79 != d79) {
                mismatch++;
                fail("C dim79 两入口输出不同: " + NAMES.get(b));
            }
            if (gate && g79) {
                mismatch++;
                fail("C 跨维串门: " + NAMES.get(b) + " 同时过两维的门");
            }
        }
        check(checked >= 25, "C 比对方块实例数足够（" + checked + " ≥ 25）");
        System.out.println("# C 命中实况：比对 " + checked + " 个已知方块实例，dim78 三入口 / dim79 两入口输出逐位相同");
        check(mismatch == 0, "C 各维入口逐方块等价（比对 " + checked + " 个实例，不一致 " + mismatch + "）");
        final Block frozen = FIELDS.get("gtsr:prosperitySurface");
        check(frozen != null && ProsperityDecorPlacer.isNaturalTop(frozen),
            "C 定向证据：装饰层现已接受 prosperitySurface（改造前此处为 false ⇒ 审计 D-5 缺陷点）");
        check(frozen != null && ProsperityOutpostPlacer.isNaturalProsperityTop(frozen),
            "C 定向证据：结构层对 prosperitySurface 的既有语义未被削弱（放宽是单向的）");
    }

    /** F 组：两维申报集合无交集；dim78 含冻结块、dim79 不含任何 dim78 成员。 */
    private static void assertDimExclusion() {
        final Block[] a = SurfaceGate.landableTops(SurfaceGate.DIM78);
        final Block[] b = SurfaceGate.landableTops(SurfaceGate.DIM79);
        int shared = 0;
        for (final Block x : a) {
            for (final Block y : b) {
                if (x != null && x == y) {
                    shared++;
                }
            }
        }
        check(shared == 0, "F 两维可落地集合无共同成员（shared=" + shared + "）");
        check(a.length > 0 && a[0] == FIELDS.get("gtsr:prosperitySurface"),
            "F dim78 集合首项 = 冻结地表块 prosperitySurface");
        check(b.length == EXPECTED79.length, "F dim79 集合未引入冻结地表块（大小仍 " + EXPECTED79.length + "）");
    }

    // ═══════════════════════════════ E 组：源级单一真值 ═══════════════════════════════

    /** top 方块的成员比较表达式（改造前四份门的形态）。 */
    private static final Pattern MEMBER_COMPARE = Pattern
        .compile("==\\s*(?:BlocksGTSR|com\\.miaokatze\\.gtsr\\.common\\.blocks\\.BlocksGTSR)\\.(?:prosperity|shattered)[A-Za-z]*(?:Top|Surface)\\b");
    /** 别名方法体允许的唯一形态：一行委托（显式集合 + 维度键）。 */
    private static final Pattern DELEGATE = Pattern.compile(
        "return\\s+SurfaceGate\\.isNaturalTop\\(\\s*DIM_KEY\\s*,\\s*SurfaceGate\\.landableTops\\(\\s*DIM_KEY\\s*\\)\\s*,\\s*\\w+\\s*\\)\\s*;");

    /**
     * E 组三件事：① 除 {@code framework/SurfaceGate.java} 外，任何生产文件都不得再出现
     * top/冻结方块的成员比较表达式（＝自造门）；② 别名数量与位置 = 申报的 3 个，方法体必须是
     * 那一行委托且不含任何 {@code BlocksGTSR} 引用；③ 申报的 5 处调用点各自的形态与维度键成立。
     */
    private static void assertSourceLevelSingleTruth(Path root) throws IOException {
        final List<Path> production = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                final String p = slash(file);
                if (p.endsWith(".java") && p.contains("/com/miaokatze/gtsr/")) {
                    production.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        check(production.size() >= 50, "E 生产源码文件集非空（" + production.size() + " 个）");

        int strays = 0;
        int exempt = 0;
        int aliases = 0;
        int gateDefs = 0;
        for (final Path file : production) {
            final String p = slash(file);
            final String codeAll = stripComments(read(file));
            // 门族定义总数：连 SurfaceGate 自己一起数（它是那"一份"，两个重载）
            final java.util.regex.Matcher gd = Pattern
                .compile("static boolean (?:isNaturalTop|isNaturalProsperityTop)\\(").matcher(codeAll);
            while (gd.find()) {
                gateDefs++;
            }
            if (p.endsWith("framework/SurfaceGate.java")) {
                continue;
            }
            final String code = codeAll;
            // 成员比较表达式：只允许落在例外申报的方法体内（tuftForGround），其余一律算自造门
            final int[] excRange = exceptionRangeFor(code, p);
            final Matcher m1 = MEMBER_COMPARE.matcher(code);
            while (m1.find()) {
                if (excRange != null && m1.start() >= excRange[0] && m1.end() <= excRange[1]) {
                    exempt++;
                } else {
                    strays++;
                    fail("E 自造门残留（成员比较表达式，不在例外申报内）: " + base(p) + " → " + m1.group());
                }
            }
            for (final String alias : GATE_ALIASES) {
                final String filePart = alias.substring(0, alias.lastIndexOf(':'));
                if (!p.endsWith(filePart)) {
                    continue;
                }
                final String method = alias.substring(alias.lastIndexOf(':') + 1);
                final String body = methodBody(code, "static boolean " + method + "(");
                if (body == null) {
                    fail("E 别名方法消失（应保留为一行委托）: " + alias);
                    continue;
                }
                aliases++;
                check(DELEGATE.matcher(body).find(), "E 别名方法体 == 一行委托: " + base(p) + '#' + method);
                check(!body.contains("BlocksGTSR"), "E 别名方法体零方块集合知识: " + base(p) + '#' + method);
            }
        }
        check(strays == 0, "E 全仓自造门残留 == 0（实测 " + strays + "）");
        for (final String[] exc : MEMBER_COMPARE_EXCEPTIONS) {
            check(Integer.parseInt(exc[2]) <= exempt,
                "E 例外申报被兑现：" + exc[0] + "#" + exc[1].trim() + " 内成员比较 == 申报 " + exc[2]
                    + "（实测合计 " + exempt + "）");
        }
        check(exempt == 4, "E 例外申报总数守恒（实测 " + exempt + "，只允许 tuftForGround 的 4 处）");
        check(aliases == GATE_ALIASES.length,
            "E 别名定义数 == 申报 " + GATE_ALIASES.length + "（实测 " + aliases + "）");
        // 门族定义总数 = 3 个别名 + 单一谓词的两个重载 ⇒ 5。任何新拷贝（哪怕名字不同但形状相同）都红。
        check(gateDefs == 5,
            "E 全仓门族定义总数 == 申报 5（3 别名 + SurfaceGate 两个重载）（实测 " + gateDefs + "）");

        final String decor = stripComments(read(root.resolve(GATE_CALL_SITES[0][0])));
        check(SurfaceGate.DIM78.equals(dimKeyOf(decor)), "E 装饰层 DIM_KEY 声明 == dim78 键");
        check(count(decor, "isNaturalTop(") == 5,
            "E 装饰层 = 3 个调用点 + 1 个定义 + 1 个框架委托（实测 " + count(decor, "isNaturalTop(") + "）");

        final String outpost = stripComments(read(root.resolve(GATE_CALL_SITES[1][0])));
        check(outpost.contains("isNaturalProsperityTop(world.getBlock(centerX, surfaceY, centerZ))"),
            "E outpost 落点门调用点参数口径未变");
        check(count(outpost, "isNaturalProsperityTop(") == 2,
            "E outpost = 1 个调用点 + 1 个定义（实测 " + count(outpost, "isNaturalProsperityTop(") + "）");
        check(SurfaceGate.DIM78.equals(dimKeyOf(outpost)), "E outpost DIM_KEY 声明 == dim78 键");

        final String scatter = stripComments(read(root.resolve(GATE_CALL_SITES[2][0])));
        check(scatter.contains("SurfaceGate.isNaturalTop(DIM_KEY, SurfaceGate.landableTops(DIM_KEY),"),
            "E 散布层直调单一谓词（显式集合 + 维度键）");
        check(!scatter.contains("ProsperityOutpostPlacer.isNaturalProsperityTop("),
            "E 散布层不再横向直调另一 placer 的谓词（审计 A-2 #3 之一）");
        check(SurfaceGate.DIM78.equals(dimKeyOf(scatter)), "E 散布层 DIM_KEY 声明 == dim78 键");

        final String shattered = stripComments(read(root.resolve(GATE_CALL_SITES[3][0])));
        check(SurfaceGate.DIM79.equals(dimKeyOf(shattered)), "E dim79 装饰层 DIM_KEY 声明 == dim79 键");
        check(count(shattered, "isNaturalTop(") == 3, "E dim79 = 1 个调用点 + 1 个定义 + 1 个委托转发（实测 "
            + count(shattered, "isNaturalTop(") + "）");

        // P7 同步（清单第 5 行 MACHINE_RESIDUAL → MACHINE）：机器层原本的"横向直调另一 placer 别名"
        // （审计 A-2 #3 之二，P4 因允许路径不含该文件而只能申报残留）现已改道为与散布层同形的
        // 框架单一谓词直调。两条申报<b>逐条替换、不增不减</b>（本工具的断言总数仍须是 132）。
        final String machine = stripComments(read(root.resolve(GATE_CALL_SITES[4][0])));
        check(machine.contains("SurfaceGate.isNaturalTop(DIM_KEY, SurfaceGate.landableTops(DIM_KEY),"),
            "E 机器层已改道直调单一谓词（显式集合 + 维度键，与散布层同形）");
        check(!machine.contains("ProsperityOutpostPlacer."),
            "E 机器层不再横向直调另一 placer 的别名（审计 A-2 #3 之二闭合）");
    }

    /** 该文件对应的例外申报方法体范围（无申报返回 null）。 */
    private static int[] exceptionRangeFor(String code, String path) {
        for (final String[] exc : MEMBER_COMPARE_EXCEPTIONS) {
            if (path.endsWith(exc[0])) {
                return bodyRange(code, exc[1]);
            }
        }
        return null;
    }

    /** 方法体的 {@code [start,end)}（花括号配平）；找不到返回 null。 */
    private static int[] bodyRange(String code, String signaturePrefix) {
        final int sig = code.indexOf(signaturePrefix);
        if (sig < 0) {
            return null;
        }
        final int open = code.indexOf('{', sig);
        if (open < 0) {
            return null;
        }
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            if (code.charAt(i) == '{') {
                depth++;
            } else if (code.charAt(i) == '}' && --depth == 0) {
                return new int[] { open, i };
            }
        }
        return null;
    }

    /** 该文件的 {@code DIM_KEY} 声明所指的维度键（只认 {@code SurfaceGate.DIM78/DIM79} 两种写法）。 */
    private static String dimKeyOf(String code) {
        final Matcher m = Pattern.compile("DIM_KEY\\s*=\\s*SurfaceGate\\.(DIM7[89])")
            .matcher(code);
        if (!m.find()) {
            return null;
        }
        return "DIM78".equals(m.group(1)) ? SurfaceGate.DIM78 : SurfaceGate.DIM79;
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** 取方法体（朴素花括号配平，只用于形状断言）。 */
    private static String methodBody(String code, String signaturePrefix) {
        final int sig = code.indexOf(signaturePrefix);
        if (sig < 0) {
            return null;
        }
        final int open = code.indexOf('{', sig);
        if (open < 0) {
            return null;
        }
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            if (code.charAt(i) == '{') {
                depth++;
            } else if (code.charAt(i) == '}' && --depth == 0) {
                return code.substring(open + 1, i);
            }
        }
        return null;
    }

    /** 去注释（保留字符串字面量原样，避免把 {@code "//"} 之类的键文吃掉）。 */
    private static String stripComments(String text) {
        final StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                final int end = skipLiteral(text, i);
                sb.append(text, i, end);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                while (i < text.length() && text.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                final int end = text.indexOf("*/", i + 2);
                i = end < 0 ? text.length() : end + 2;
                sb.append(' ');
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static int skipLiteral(String text, int start) {
        final char quote = text.charAt(start);
        int i = start + 1;
        while (i < text.length()) {
            if (text.charAt(i) == '\\') {
                i += 2;
                continue;
            }
            if (text.charAt(i) == quote) {
                return i + 1;
            }
            i++;
        }
        return i;
    }

    // ═══════════════════════════════ G 组：灵敏度自检（防伪绿）═══════════════════════════════

    /**
     * G 组：人为增删成员 ⇒ 谓词输出必须变。谓词签名带<b>显式集合</b>正是为了让这条自检可写：
     * 这里不就地改生产数组（会污染同进程其它断言），而是把影子集合作为入参喂给同一实现体。
     * 若本组任何一条不成立，说明 D 组申报带对集合改动不敏感＝整套门断言是假绿。
     */
    private static void assertSensitivity() {
        final Block[] roster = SurfaceGate.landableTops(SurfaceGate.DIM78);
        final Block frozen = roster[0];
        check(SurfaceGate.isNaturalTop(SurfaceGate.DIM78, roster, frozen), "G 原集合：prosperitySurface 过门");
        final Block[] minusOne = Arrays.copyOfRange(roster, 1, roster.length);
        check(!SurfaceGate.isNaturalTop(SurfaceGate.DIM78, minusOne, frozen),
            "G 灵敏度：删掉 prosperitySurface ⇒ 必须关门（＝改造前的缺陷形态，D 组必须能抓到）");
        check(SurfaceGate.isNaturalTop(SurfaceGate.DIM78, minusOne, roster[1]),
            "G 灵敏度：删一个成员不影响其它成员（改动最小性）");
        final Block[] plusOne = Arrays.copyOf(roster, roster.length + 1);
        plusOne[roster.length] = FIELDS.get("gtsr:ruinDebris");
        check(SurfaceGate.isNaturalTop(SurfaceGate.DIM78, plusOne, FIELDS.get("gtsr:ruinDebris")),
            "G 灵敏度：加成员 ⇒ 新成员必须开门（＝B 组负例对集合改动不敏感时会被这里抓到）");
        check(!SurfaceGate.isNaturalTop(SurfaceGate.DIM79, plusOne, FIELDS.get("gtsr:shatteredAshTop")),
            "G 灵敏度：dim78 的影子集合不会把 dim79 的门一起放宽（维度键必须生效）");
        // 别名漂移灵敏度：把装饰层"指回旧 4 员集合"后必然与本维框架谓词不同（C 组的 RED 依据）
        check(!SurfaceGate.isNaturalTop(SurfaceGate.DIM78, minusOne, frozen)
            && ProsperityDecorPlacer.isNaturalTop(frozen), "G 灵敏度：旧 4 员集合 ≠ 现装饰层实际判定");
    }

    // ═══════════════════════ H 组：保留实例的"同一输入不同输出"证据 ═══════════════════════

    /**
     * H 组：判据 2 要求"剩余额外实例逐条给语义不同的证据"。本片保留的门族实例只有 2 个
     * （E 组已把数量钉成 2），这里各给一条同输入差异证据，另加一条"选择器语义未被本片改动"的
     * 守恒证据。<b>不改生产代码</b>，用反射调那两个包内私有成员。
     */
    private static void assertKeptInstancesDifferOnSameInput() throws Exception {
        final int side = 16;
        final Block[] grid = new Block[side * side * 256];
        final Block ashTop = FIELDS.get("gtsr:shatteredAshTop");
        final Block debris = FIELDS.get("gtsr:ruinDebris");
        check(ashTop != null && debris != null, "H 前置：dim79 top 与残骸块都已落位");
        // 列 (3,3)：地面 = 自然 top，上方已被占；列 (5,5)：地面 = 自然 top，上方空气
        for (int y = 1; y <= 70; y++) {
            grid[y * side * side + 3 * side + 3] = Blocks.stone;
            grid[y * side * side + 5 * side + 5] = Blocks.stone;
        }
        grid[70 * side * side + 3 * side + 3] = ashTop;
        grid[70 * side * side + 5 * side + 5] = ashTop;
        grid[71 * side * side + 3 * side + 3] = debris; // 让行位被占
        final RegionWorld w = synthWorld(grid, 0, 0, 0x5034504CL, side);

        final java.lang.reflect.Method wrapper = ShatteredDecorPlacer.class
            .getDeclaredMethod("placeableOnNaturalTop", World.class, int.class, int.class, int.class);
        wrapper.setAccessible(true);

        // H1 空气让行：同一地面方块，只因上方占用不同 ⇒ 归属门真、包装件假
        final boolean gate3 = ShatteredDecorPlacer.isNaturalTop(w.getBlock(3, 70, 3));
        final boolean wrap3 = ((Boolean) wrapper.invoke(null, w, 3, 70, 3)).booleanValue();
        final boolean gate5 = ShatteredDecorPlacer.isNaturalTop(w.getBlock(5, 70, 5));
        final boolean wrap5 = ((Boolean) wrapper.invoke(null, w, 5, 70, 5)).booleanValue();
        check(gate3 && gate5, "H1 前提：同一方块在归属门上一致为真");
        check(wrap5 && !wrap3,
            "H1 语义差异证据：placeableOnNaturalTop 与单一谓词对同一地面方块给出不同结论"
                + "（上方被占 → 包装件 " + wrap3 + "、空气 → " + wrap5 + "）⇒ 不可并");

        // H2 y 域：同一方块、同一世界状态，只因 surfaceY 越出本层上界（dim79 MAX_SURFACE_Y=96）
        final boolean wrap96 = ((Boolean) wrapper.invoke(null, w, 5, 70, 5)).booleanValue();
        grid[97 * side * side + 5 * side + 5] = ashTop;
        final boolean gate97 = ShatteredDecorPlacer.isNaturalTop(w.getBlock(5, 97, 5));
        final boolean wrap97 = ((Boolean) wrapper.invoke(null, w, 5, 97, 5)).booleanValue();
        check(wrap96 && gate97 && !wrap97,
            "H2 语义差异证据：y 域门是本层自有（surfaceY=97 越界 ⇒ 包装件 " + wrap97 + "，归属门仍 "
                + gate97 + "）");

        // H3 草丛变体选择器不是门：冻结块过了门却选不出草
        final Block frozen = FIELDS.get("gtsr:prosperitySurface");
        final java.lang.reflect.Method tuft = ProsperityDecorPlacer.class
            .getDeclaredMethod("tuftForGround", Block.class);
        tuft.setAccessible(true);
        final Object picked = tuft.invoke(null, frozen);
        check(SurfaceGate.isNaturalTop(SurfaceGate.DIM78, frozen) && picked == null,
            "H3 语义差异证据：prosperitySurface 过单一谓词但 tuftForGround 返回 null"
                + "（选择器无群系身份可二分，故意更窄）⇒ 修门不会让草长在残骸板上");
        // H4 选择器语义未被本片改动（四自然 top 各自仍指向原草丛）
        check(tuft.invoke(null, FIELDS.get("gtsr:prosperitySteppeTop")) == FIELDS.get("gtsr:prosperityTuftRust")
            && tuft.invoke(null, FIELDS.get("gtsr:prosperityWastesTop")) == FIELDS.get("gtsr:prosperityTuftRust")
            && tuft.invoke(null, FIELDS.get("gtsr:prosperityForestTop")) == FIELDS.get("gtsr:prosperityTuftCopper")
            && tuft.invoke(null, FIELDS.get("gtsr:prosperitySwampTop")) == FIELDS.get("gtsr:prosperityTuftCopper"),
            "H4 守恒：干燥系→锈草、湿生系→铜绿草的映射未被本片触碰");
    }

    /** 供 H 组用的最小合成世界（不生成地形，直接吃给定网格）。 */
    private static RegionWorld synthWorld(Block[] grid, int cx0, int cz0, long seed, int side) throws Exception {
        final sun.misc.Unsafe u = SurfaceHarness.unsafe();
        final RegionWorld w = (RegionWorld) u.allocateInstance(RegionWorld.class);
        final WorldInfo info = (WorldInfo) u.allocateInstance(WorldInfo.class);
        final Field sf = WorldInfo.class.getDeclaredField("randomSeed");
        sf.setAccessible(true);
        u.putLong(info, u.objectFieldOffset(sf), seed);
        setField(w, World.class, "worldInfo", info);
        final RegionProvider rp = (RegionProvider) u.allocateInstance(RegionProvider.class);
        final Field dim = WorldProvider.class.getDeclaredField("dimensionId");
        dim.setAccessible(true);
        u.putInt(rp, u.objectFieldOffset(dim), 78);
        final Field rs = RegionProvider.class.getDeclaredField("seed");
        rs.setAccessible(true);
        rs.setLong(rp, seed);
        setField(w, World.class, "provider", rp);
        setField(w, RegionWorld.class, "grid", grid);
        setInt(w, RegionWorld.class, "originBlockX", cx0 << 4);
        setInt(w, RegionWorld.class, "originBlockZ", cz0 << 4);
        setInt(w, RegionWorld.class, "side", side);
        setLong(w, RegionWorld.class, "seed", seed);
        return w;
    }

    private static void setField(Object target, Class<?> owner, String name, Object value) throws Exception {
        final Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        final sun.misc.Unsafe u = SurfaceHarness.unsafe();
        u.putObject(target, u.objectFieldOffset(f), value);
    }

    private static void setInt(Object target, Class<?> owner, String name, int value) throws Exception {
        final Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        SurfaceHarness.unsafe().putInt(target, SurfaceHarness.unsafe().objectFieldOffset(f), value);
    }

    private static void setLong(Object target, Class<?> owner, String name, long value) throws Exception {
        final Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        SurfaceHarness.unsafe().putLong(target, SurfaceHarness.unsafe().objectFieldOffset(f), value);
    }

    /** D 组：小样本实测通过率与成员份额守恒，并钉在申报带内。 */
    private static void assertPassRateBand() throws Exception {
        final Sampler s = new Sampler(2, 1, 16).run();
        System.out.println("# " + s.describe());
        band("D dim78 列级通过率(pristine)", s.rate78Pristine(), BAND_PRISTINE_MIN, BAND_PRISTINE_MAX);
        band("D dim79 列级通过率(pristine)", s.rate79Pristine(), BAND_PRISTINE_MIN, BAND_PRISTINE_MAX);
        band("D dim78 列级通过率(口径B 散布前)", s.rate78PreScatter(), BAND_PRE_SCATTER_MIN,
            BAND_PRE_SCATTER_MAX);
        band("D dim78 列级通过率(口径C 装饰前)", s.rate78Chain(), BAND_CHAIN_MIN, BAND_CHAIN_MAX);
        band("D prosperitySurface 链后份额", s.share78FrozenChain(), BAND_FROZEN_SHARE_MIN, BAND_FROZEN_SHARE_MAX);
        check(s.memberSum78Chain() == s.pass78Chain, "D 守恒：成员直方之和 == 链后总过门列数（"
            + s.memberSum78Chain() + " vs " + s.pass78Chain + "）");
        check(s.memberSum78Pristine() == s.pass78Pristine, "D 守恒：pristine 成员直方之和 == 总过门列数（"
            + s.memberSum78Pristine() + " vs " + s.pass78Pristine + "）");
        check(s.violationsAbove == 0 && s.violationsBelow == 0,
            "D 前提成立：采样区列内无悬块/无洞穴（above=" + s.violationsAbove + " below=" + s.violationsBelow
                + "）⇒ 列级口径与门实际读到的地表同源");
        check(s.cols78Chain > 40_000L, "D 样本量足够（链后列数 " + s.cols78Chain + " > 4 万）");
    }

    private static void band(String label, double actual, double lo, double hi) {
        check(actual >= lo && actual <= hi, label + " = " + fmt(actual) + "pp ∈ [" + lo + ", " + hi + "]");
    }

    private static String fmt(double pp) {
        return String.format(Locale.ROOT, "%.3f", pp);
    }

    // ═══════════════════════════════════ 采样器 ═══════════════════════════════════

    /**
     * 一个样本 = {@code seeds × regionsPerSeed} 个 {@code axis×axis} chunk 的连续区。
     * 每区做四件事：dim78 真实表层 → 列级门统计(pristine) → 装饰单独跑(pristine 面)
     * → 复制网格跑完整编排链 → 链后列级门统计；另 materialize 一份 dim79 区只做列级统计。
     * 门判定一律走<b>生产谓词</b>（见类注释 measure 模式说明），故 BASE/AFTER 两跑可直接相减。
     */
    private static final class Sampler {

        private final int seeds;
        private final int regions;
        private final int axis;
        private final int side;
        private final int inner;

        private long cols78Pristine, pass78Pristine, cols79Pristine, pass79Pristine;
        private long cols78Chain, pass78Chain;
        private long cols78PreScatter, pass78PreScatter;
        private static final int CENSUS_PRISTINE = 0, CENSUS_PRE_SCATTER = 1, CENSUS_PRE_DECOR = 2;
        private final Map<String, Long> memberChain = new TreeMap<>();
        private final Map<String, Long> memberPristine = new TreeMap<>();
        private long chunksInterior, eligibleChunks;
        private long decorLandedPristine, decorLandedChain, decorChunksWithLandingChain;
        private long decorLandedPristineTotal, chunksEligibleForPristine;
        private long outpostHits, machineLanded, scatterLanded;
        private long violationsAbove, violationsBelow;
        private long skippedNoSurface;
        private final Map<String, Long> topHist78 = new TreeMap<>();

        Sampler(int seeds, int regions, int axis) {
            this.seeds = seeds;
            this.regions = regions;
            this.axis = axis;
            this.side = axis * 16;
            this.inner = Math.max(1, axis - 2);
        }

        Sampler run() throws Exception {
            final BiomeGenBase[] pb = SurfaceHarness.prosperityBiomes();
            final BiomeGenBase[] sb = SurfaceHarness.shatteredBiomes();
            SurfaceHarness.recordAllAllocations(pb, sb);
            final GTSRDimensionDef defP = SurfaceHarness.def(true, pb, SurfaceHarness.prosperityWeights());
            final GTSRDimensionDef defS = SurfaceHarness.def(false, sb, SurfaceHarness.shatteredWeights());
            final Block[] scratch = new Block[65536];
            final byte[] scratchMeta = new byte[65536];
            for (int si = 0; si < seeds; si++) {
                final long seed = SEEDS[si % SEEDS.length];
                final GTSRChunkProviderBase provP = new ChunkProviderProsperityRuins(SurfaceHarness.mockWorld(), seed);
                final GTSRWorldChunkManager mgrP = new GTSRWorldChunkManager(seed, defP);
                final GTSRChunkProviderBase provS = new ChunkProviderShatteredGrounds(SurfaceHarness.mockWorld(),
                    seed);
                final GTSRWorldChunkManager mgrS = new GTSRWorldChunkManager(seed, defS);
                for (int r = 0; r < regions; r++) {
                    // 区原点按 3/5 个群系带 cell（ZONE_CELL_CHUNKS=16）跳开，否则整样本会塌进
                    // 同一个 cell（实测首跑 96% 列都是齿轮森林），四群系权重 45-30-15-10 无从体现
                    final int cx0 = r * axis * 3 + si * 4096;
                    final int cz0 = r * axis * 5 + si * 924817;
                    final Block[] gridA = new Block[side * side * 256];
                    materialize(provP, mgrP, pb, seed, cx0, cz0, gridA, scratch, scratchMeta, true);
                    final Block[] gridB = gridA.clone();
                    final RegionWorld worldA = world(gridA, cx0, cz0, seed);
                    final RegionWorld worldB = world(gridB, cx0, cz0, seed);
                    countColumns(worldA, true, cx0, cz0, memberPristine, false, CENSUS_PRISTINE);
                    runDecorOnly(worldA, seed, cx0, cz0);
                    runChain(worldB, seed, cx0, cz0);
                    if (r == 0) {
                        final Block[] gridS = new Block[side * side * 256];
                        materialize(provS, mgrS, sb, seed, cx0, cz0, gridS, scratch, scratchMeta, false);
                        countColumns(world(gridS, cx0, cz0, seed), false, cx0, cz0, null, false,
                            CENSUS_PRISTINE);
                    }
                }
            }
            return this;
        }

        /** 真实 {@code generateTerrain} + 真实表层缝，铺成一张 {@code side×side×256} 平坦网格。 */
        private void materialize(GTSRChunkProviderBase provider, GTSRWorldChunkManager mgr, BiomeGenBase[] biomes,
            long seed, int cx0, int cz0, Block[] grid, Block[] blocks, byte[] meta, boolean countViolations)
            throws Exception {
            final java.lang.reflect.Method gen = SurfaceHarness.generateTerrain();
            final java.lang.reflect.Method seam = SurfaceHarness.surfaceSeam(provider.getClass());
            for (int dx = 0; dx < axis; dx++) {
                for (int dz = 0; dz < axis; dz++) {
                    final int cx = cx0 + dx;
                    final int cz = cz0 + dz;
                    Arrays.fill(blocks, null);
                    Arrays.fill(meta, (byte) 0);
                    gen.invoke(provider, cx, cz, blocks, meta, null);
                    final BiomeGenBase[] plane = mgr.loadBlockGeneratorData(null, cx * 16, cz * 16, 16, 16);
                    seam.invoke(null, seed, cx * 16, cz * 16, blocks, meta, plane);
                    final int bx = dx * 16;
                    final int bz = dz * 16;
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            // 生产下标口径（GTSRChunkProviderBase.generateTerrain:63「x<<12 | z<<8 | y」，
                            // y 是低位——与 vanilla ChunkProviderGenerate 同侧，不是 x|z<<4|y<<8）
                            final int col = (lx << 12) | (lz << 8);
                            final int dstCol = (bx + lx) + (bz + lz) * side;
                            for (int y = 0; y < 256; y++) {
                                grid[y * side * side + dstCol] = blocks[col | y];
                            }
                            if (countViolations) {
                                final int top = topY(blocks, col);
                                violationsAbove += solidAbove(blocks, col, top);
                                violationsBelow += airBelow(blocks, col, top);
                            }
                        }
                    }
                }
            }
        }

        private static int topY(Block[] blocks, int col) {
            for (int y = 255; y > 0; y--) {
                final Block b = blocks[col | y];
                if (b != null && b.getMaterial() != Material.air) {
                    return y;
                }
            }
            return -1;
        }

        private static int solidAbove(Block[] blocks, int col, int top) {
            int n = 0;
            for (int y = top + 1; y <= 255; y++) {
                final Block b = blocks[col | y];
                if (b != null && b.getMaterial() != Material.air) {
                    n++;
                }
            }
            return n;
        }

        private static int airBelow(Block[] blocks, int col, int top) {
            int n = 0;
            for (int y = 1; y < top; y++) {
                final Block b = blocks[col | y];
                if (b == null || b.getMaterial() == Material.air) {
                    n++;
                }
            }
            return n;
        }

        /**
         * 列级门普查：对采样 chunk 的每一列，用<b>生产列扫</b>取顶、再用<b>各树自己的生产门</b>判定，
         * {@code member} 非空时同时按方块名直方（守恒检查与份额用）。
         * <p>
         * {@code singleChunk=true} ⇒ 只普查 {@code (cursor, cursorZ)} 这一个 chunk，且调用点固定在
         * <b>散布之后、装饰之前</b>（口径 C）——测到的才是装饰真正探到的那一刻；
         * {@code false} ⇒ 扫整个区内部（口径 A 用）。口径 B（散布前）同样走 {@code singleChunk}，
         * 由 {@code census} 决定记到哪一组计数；两者之间世界状态的唯一差别是 scatter 的写入
         * （权威样本实测 100.58 块·chunk⁻¹），这正是口径 B(99.197 %) 与口径 C(70.334 %) 的差。
         */
        private void countColumns(RegionWorld world, boolean dim78, int cx0, int cz0, Map<String, Long> member,
            boolean singleChunk, int census) {
            final int lo = singleChunk ? world.cursor : 1, hi = singleChunk ? world.cursor + 1 : axis - 1;
            final int loz = singleChunk ? world.cursorZ : 1;
            final int hiz = singleChunk ? world.cursorZ + 1 : axis - 1;
            for (int cx = lo; cx < hi; cx++) {
                for (int cz = loz; cz < hiz; cz++) {
                    if (dim78 && !eligible(cx0 + cx, cz0 + cz, world.seed)) {
                        continue; // 城 buffer 窗内生产不跑装饰 ⇒ 门也不参与（与判据 3 同域）
                    }
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            final int x = ((cx0 + cx) << 4) + lx;
                            final int z = ((cz0 + cz) << 4) + lz;
                            final int y = GTSRChunkProviderBase.findSurfaceY(world, x, z);
                            if (y <= 0) {
                                skippedNoSurface++;
                                continue;
                            }
                            final Block b = world.getBlock(x, y, z);
                            if (dim78 && member == memberPristine) {
                                final String lbl = NAMES.get(b);
                                topHist78.merge(lbl == null ? b.getClass()
                                    .getSimpleName() : lbl, 1L, Long::sum);
                            }
                            final boolean ok = dim78 ? ProsperityDecorPlacer.isNaturalTop(b)
                                : ShatteredDecorPlacer.isNaturalTop(b);
                            if (dim78) {
                                if (census == CENSUS_PRE_SCATTER) {
                                    cols78PreScatter++;
                                    if (ok) {
                                        pass78PreScatter++;
                                    }
                                } else if (census == CENSUS_PRE_DECOR) {
                                    cols78Chain++;
                                    if (ok) {
                                        pass78Chain++;
                                    }
                                } else {
                                    cols78Pristine++;
                                    if (ok) {
                                        pass78Pristine++;
                                    }
                                }
                            } else {
                                cols79Pristine++;
                                if (ok) {
                                    pass79Pristine++;
                                }
                            }
                            if (member != null && ok) {
                                final String name = NAMES.get(b);
                                member.merge(name == null ? "(unlabeled)" : name, 1L, Long::sum);
                            }
                        }
                    }
                }
            }
        }

        /** 装饰单独跑在 pristine 面上（判据 3 的"纯门效应"侧）。 */
        private void runDecorOnly(RegionWorld world, long seed, int cx0, int cz0) {
            final CountingSink sink = new CountingSink(world);
            for (int cx = 1; cx < axis - 1; cx++) {
                for (int cz = 1; cz < axis - 1; cz++) {
                    final int gcx = cx0 + cx;
                    final int gcz = cz0 + cz;
                    if (!eligible(gcx, gcz, seed)) {
                        continue;
                    }
                    chunksEligibleForPristine++;
                    sink.chunk(gcx, gcz);
                    final long before = sink.accepted;
                    ProsperityDecorPlacer.decorate(world, seed, gcx, gcz, sink);
                    decorLandedPristine += sink.accepted - before;
                }
            }
            decorLandedPristineTotal = decorLandedPristine;
        }

        /** 真实编排顺序（{@code ProsperityWorldGenerator.generate} 的 2→5 段，含互斥掷骰）。 */
        private void runChain(RegionWorld world, long seed, int cx0, int cz0) {
            final CountingSink sink = new CountingSink(world);
            final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
            for (int cx = 1; cx < axis - 1; cx++) {
                for (int cz = 1; cz < axis - 1; cz++) {
                    final int gcx = cx0 + cx;
                    final int gcz = cz0 + cz;
                    chunksInterior++;
                    if (!eligible(gcx, gcz, seed)) {
                        continue;
                    }
                    eligibleChunks++;
                    sink.chunk(gcx, gcz);
                    if (ProsperityOutpostPlacer.placeAll(world, seed, gcx, gcz, sink)) {
                        outpostHits++;
                    } else {
                        final long before = sink.accepted;
                        RuinedMachinePlacer.placeAll(
                            world,
                            seed,
                            gcx,
                            gcz,
                            ProsperityWorldGenerator.weightForRosterIndex(ordinalAt(authority, gcx, gcz),
                                ProsperityWorldGenerator.MACHINE_WEIGHTS),
                            sink);
                        machineLanded += sink.accepted - before;
                    }
                    // 口径 B：散布之前（outpost/机器已写完）——冒烟 E6 的 attempt 级通过率就在
                    // "散布逐次边写边探"的这一族里，本口径是它的上界对照
                    world.cursor = cx;
                    world.cursorZ = cz;
                    countColumns(world, true, cx0, cz0, null, true, CENSUS_PRE_SCATTER);
                    final long beforeScatter = sink.accepted;
                    ProsperitySurfaceScatter.scatter(
                        world,
                        seed,
                        gcx,
                        gcz,
                        ProsperityWorldGenerator.weightForRosterIndex(ordinalAt(authority, gcx, gcz),
                            ProsperityWorldGenerator.SCATTER_WEIGHTS),
                        sink);
                    scatterLanded += sink.accepted - beforeScatter;
                    // 口径 C：装饰之前（散布已写完）——装饰真正探到的那一刻
                    world.cursor = cx;
                    world.cursorZ = cz;
                    countColumns(world, true, cx0, cz0, memberChain, true, CENSUS_PRE_DECOR);
                    final long beforeDecor = sink.accepted;
                    ProsperityDecorPlacer.decorate(world, seed, gcx, gcz, sink);
                    final long landed = sink.accepted - beforeDecor;
                    decorLandedChain += landed;
                    if (landed > 0) {
                        decorChunksWithLandingChain++;
                    }
                }
            }
        }

        private static int ordinalAt(GTSRBiomeAuthority authority, int cx, int cz) {
            return authority.ordinalAt((cx << 4) + 8, (cz << 4) + 8).ordinal;
        }

        private static boolean eligible(int cx, int cz, long seed) {
            return CityPlanner.citiesNear(seed, cx, cz).length == 0;
        }

        double rate78Pristine() {
            return pct(pass78Pristine, cols78Pristine);
        }

        double rate79Pristine() {
            return pct(pass79Pristine, cols79Pristine);
        }

        double rate78Chain() {
            return pct(pass78Chain, cols78Chain);
        }

        double rate78PreScatter() {
            return pct(pass78PreScatter, cols78PreScatter);
        }

        long natural4Chain() {
            long sum = 0;
            for (int i = 1; i < EXPECTED78.length; i++) {
                sum += memberChain.getOrDefault(member(i), 0L);
            }
            return sum;
        }

        long memberSum78Chain() {
            long sum = 0;
            for (final long v : memberChain.values()) {
                sum += v;
            }
            return sum;
        }

        long memberSum78Pristine() {
            long sum = 0;
            for (final long v : memberPristine.values()) {
                sum += v;
            }
            return sum;
        }

        double share78FrozenChain() {
            return pct(memberChain.getOrDefault(member(0), 0L), cols78Chain);
        }

        /** 申报成员在 {@link #NAMES} 里的实际标签（带 {@code gtsr:} 前缀）。 */
        static String member(int i) {
            return "gtsr:" + EXPECTED78[i];
        }

        String describe() {
            return "SAMPLE seeds=" + seeds + " regionsPerSeed=" + regions + " chunksPerRegion=" + axis + "x" + axis
                + " interiorChunksSampled=" + chunksInterior + " eligibleChunks=" + eligibleChunks
                + " cols78Pristine=" + cols78Pristine + " cols78Chain=" + cols78Chain + " cols79=" + cols79Pristine;
        }

        void print() {
            System.out.println("MEASURE " + describe());
            line("column-gate", "dim=78 surface=pristine", "cols", cols78Pristine, "pass", pass78Pristine,
                rate78Pristine());
            line("column-gate", "dim=79 surface=pristine", "cols", cols79Pristine, "pass", pass79Pristine,
                rate79Pristine());
            line("column-gate", "dim=78 surface=pre-scatter(口径B)", "cols", cols78PreScatter, "pass",
                pass78PreScatter, rate78PreScatter());
            line("column-gate", "dim=78 surface=post-chain(口径C 装饰前)", "cols", cols78Chain, "pass",
                pass78Chain, rate78Chain());
            for (final Map.Entry<String, Long> e : memberChain.entrySet()) {
                System.out.println("MEASURE kind=member surface=post-chain name=" + e.getKey() + " cols="
                    + e.getValue() + " share=" + fmt(pct(e.getValue(), cols78Chain)));
            }
            for (final Map.Entry<String, Long> e : memberPristine.entrySet()) {
                System.out.println("MEASURE kind=member surface=pristine name=" + e.getKey() + " cols="
                    + e.getValue() + " share=" + fmt(pct(e.getValue(), cols78Pristine)));
            }
            System.out.println("MEASURE kind=natural4-sum surface=post-chain cols=" + natural4Chain() + " share="
                + fmt(pct(natural4Chain(), cols78Chain)) + "  (= 改造前装饰门的列级通过率)");
            System.out.println("MEASURE kind=gate-delta name=" + EXPECTED78[0] + " pp="
                + fmt(rate78Chain() - pct(natural4Chain(), cols78Chain))
                + "  (= 修门给装饰门新增的可落面 == 冻结块份额)");
            System.out.println("MEASURE kind=decor surface=pristine eligibleChunks=" + chunksEligibleForPristine
                + " landedTotal=" + decorLandedPristineTotal + " perChunk=" + per(decorLandedPristineTotal,
                    chunksEligibleForPristine));
            System.out.println("MEASURE kind=decor surface=post-chain eligibleChunks=" + eligibleChunks
                + " landedTotal=" + decorLandedChain + " perChunk=" + per(decorLandedChain, eligibleChunks)
                + " chunksWithLanding=" + decorChunksWithLandingChain);
            System.out.println("MEASURE kind=chain outpostHitChunks=" + outpostHits + " machineLanded="
                + machineLanded + " scatterLanded=" + scatterLanded + " scatterLandedPerChunk="
                + per(scatterLanded, eligibleChunks));
            System.out.println("MEASURE kind=premise violationsAbove=" + violationsAbove + " violationsBelow="
                + violationsBelow + " skippedNoSurface=" + skippedNoSurface
                + " (0/0 ⇒ 列级口径与门读到的地表同源)");
            for (final Map.Entry<String, Long> e : topHist78.entrySet()) {
                System.out.println("MEASURE kind=tophist dim=78 surface=pristine name=" + e.getKey() + " cols="
                    + e.getValue() + " share=" + fmt(pct(e.getValue(), cols78Pristine + skippedNoSurface)));
            }
        }

        private static void line(String kind, String tag, String a, long av, String b, long bv, double rate) {
            System.out.println("MEASURE kind=" + kind + " " + tag + " " + a + "=" + av + " " + b + "=" + bv
                + " rate=" + fmt(rate));
        }

        private static String per(long total, long chunks) {
            return String.format(Locale.ROOT, "%.4f", (double) total / Math.max(1, chunks));
        }

        private static double pct(long part, long whole) {
            return whole <= 0 ? 0.0D : 100.0D * part / whole;
        }

        private RegionWorld world(Block[] grid, int cx0, int cz0, long seed) throws Exception {
            final sun.misc.Unsafe u = SurfaceHarness.unsafe();
            final RegionWorld w = (RegionWorld) u.allocateInstance(RegionWorld.class);
            final WorldInfo info = (WorldInfo) u.allocateInstance(WorldInfo.class);
            final Field sf = WorldInfo.class.getDeclaredField("randomSeed");
            sf.setAccessible(true);
            u.putLong(info, u.objectFieldOffset(sf), seed);
            put(w, World.class, "worldInfo", info);
            final RegionProvider rp = (RegionProvider) u.allocateInstance(RegionProvider.class);
            final Field dim = WorldProvider.class.getDeclaredField("dimensionId");
            dim.setAccessible(true);
            u.putInt(rp, u.objectFieldOffset(dim), 78);
            final Field rs = RegionProvider.class.getDeclaredField("seed");
            rs.setAccessible(true);
            rs.setLong(rp, seed);
            put(w, World.class, "provider", rp);
            put(w, RegionWorld.class, "grid", grid);
            final Field ox = RegionWorld.class.getDeclaredField("originBlockX");
            ox.setAccessible(true);
            ox.setInt(w, cx0 << 4);
            final Field oz = RegionWorld.class.getDeclaredField("originBlockZ");
            oz.setAccessible(true);
            oz.setInt(w, cz0 << 4);
            final Field sw = RegionWorld.class.getDeclaredField("side");
            sw.setAccessible(true);
            sw.setInt(w, side);
            final Field sd = RegionWorld.class.getDeclaredField("seed");
            sd.setAccessible(true);
            sd.setLong(w, seed);
            return w;
        }

        private static void put(Object target, Class<?> owner, String field, Object value) throws Exception {
            final Field f = owner.getDeclaredField(field);
            f.setAccessible(true);
            SurfaceHarness.unsafe().putObject(target, SurfaceHarness.unsafe().objectFieldOffset(f), value);
        }
    }

    /** 平坦网格支撑的合成世界（{@code getBlock}/{@code isAirBlock} + 供编排链落格的 {@link #write}）。 */
    public static final class RegionWorld extends World {

        private Block[] grid;
        /** 链后普查用的 chunk 游标（区内部坐标 1..axis-2，两轴各一）。 */
        private int cursor;
        private int cursorZ;
        private int originBlockX;
        private int originBlockZ;
        private int side;
        private long seed;

        private RegionWorld() {
            super((ISaveHandler) null, (String) null, (WorldProvider) null, (WorldSettings) null, (Profiler) null);
        }

        /** 区内索引；界外（含邻区未加载）返回 -1。 */
        private int index(int x, int y, int z) {
            if (y < 0 || y > 255) {
                return -1;
            }
            final int lx = x - originBlockX;
            final int lz = z - originBlockZ;
            if (lx < 0 || lz < 0 || lx >= side || lz >= side) {
                return -1;
            }
            return (y * side + lz) * side + lx;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            if (y > 255) {
                return Blocks.bedrock; // 镜像 vanilla 界外实心口径（同 SurfaceYParityCheck，防上界假绿）
            }
            final int i = index(x, y, z);
            return i < 0 ? null : grid[i];
        }

        @Override
        public boolean isAirBlock(int x, int y, int z) {
            if (y > 255) {
                return false;
            }
            final int i = index(x, y, z);
            if (i < 0) {
                return true; // 区外＝未加载；装饰落点已钳制在 chunk 内，故不影响计数（登记为口径限制）
            }
            final Block b = grid[i];
            return b == null || b.getMaterial() == Material.air;
        }

        /** 编排链的写入落点：离线的 {@code Blocks.air} 是 {@code BlockStone} 替身 ⇒ 显式清空。 */
        void write(int x, int y, int z, Block block) {
            final int i = index(x, y, z);
            if (i >= 0) {
                grid[i] = block == Blocks.air ? null : block;
            }
        }

        @Override
        public int getActualHeight() {
            return 256;
        }

        @Override
        public long getSeed() {
            return seed;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int entityId) {
            return null;
        }
    }

    /** 带维度与种子的 provider（{@code World.getSeed()}/{@code provider.dimensionId} 口径与生产一致）。 */
    public static final class RegionProvider extends WorldProvider {

        private long seed;

        @Override
        public String getDimensionName() {
            return "gtsr-surface-gate-harness";
        }

        @Override
        public long getSeed() {
            return seed;
        }
    }

    /**
     * 计数 + 落格的 Sink：钳制规则与 {@code ChunkClampedSink} 同（{@code x>>4==chunkX &&
     * z>>4==chunkZ}、y 域、句柄必须是 {@link Block}），写入 {@link RegionWorld} 使编排链
     * 各阶段互相可见（生产 populate 语义）。
     */
    private static final class CountingSink implements BlockSink {

        private final RegionWorld world;
        private long accepted;
        private long dropped;
        private int cx;
        private int cz;

        CountingSink(RegionWorld world) {
            this.world = world;
        }

        void chunk(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            if ((x >> 4) != cx || (z >> 4) != cz || y < 0 || y > 255 || !(block instanceof Block)) {
                dropped++;
                return false;
            }
            accepted++;
            world.write(x, y, z, (Block) block);
            return true;
        }
    }

    // ═══════════════════════════════════ 小工具 ═══════════════════════════════════

    private static String slash(Path file) {
        return file.toString()
            .replace('\\', '/');
    }

    private static String base(String path) {
        final int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }

    private static void check(boolean ok, String msg) {
        if (ok) {
            passed++;
            return;
        }
        fail(msg);
    }

    private static void fail(String msg) {
        FAILURES.add(msg);
        System.out.println("  FAIL " + msg);
    }
}
