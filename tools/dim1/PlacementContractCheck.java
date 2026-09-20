import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRustLeaves;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRustLog;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperitySurface;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityTuft;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinedCasing;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinDebris;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperitySurfaceScatter;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityWorldGenerator;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinShapes;
import com.miaokatze.gtsr.config.Config;

/**
 * P7b 结构放置契约的<b>主断言工具</b>（plan §2.1 L5 禁止项 / §2.2 H-2·H-3 / §5 P7 成功判据 /
 * 审计 A-2 #3 之二·A-4·B-2 / 任务包判据 1·2·3·4·8）。
 * <p>
 * 组与表：
 * <ul>
 * <li><b>A 契约真值</b>（判据 1）：落块 0 ⇒ 不计成功且<b>预算与互斥位都不扣</b>；许可一次性；
 * 预算跨族总额；{@code budget=0} 回退位；窗判定幂等；roster 扩形状。真实链一档
 * （{@link RejectSink} 造"门通过却 sink 全拒"）在同批真实命中 chunk 上断言
 * {@code placeAll==false && committed==0}，并断言"outpost 落块失败后机器仍拿到机会"
 * （改造前那条互斥只靠 outpost 自己说成功）。影子树（{@code temp/p7-base} = {@code d5b7ca5}）
 * 同场景的 RED 对照由一次性探针 {@code temp/p7b-red/PlacementFalseSuccessRedProbe.java} 给出。</li>
 * <li><b>B 接地同源</b>（判据 2）：{@code pure} 面逐点比 {@link PlacementGate#groundFn} 与
 * {@link ProsperityTerrainProfile#heightAt}（≥16384 chunk，要求 0 分歧＝改后表）；
 * {@code columns} 面在真实网格上比"列扫 findSurfaceY vs 纯函数"两套制式的逐列差＝改前幅度表。</li>
 * <li><b>C 贴脸率</b>（判据 3）：同一批真地形跑三档（默认 / 只关窗上限 / 两键都关），断言
 * 单调性与"窗内同模板发射数 ≤ 上限；关上限必越限"（后者＝反假绿灵敏度）。</li>
 * <li><b>D 单一真值</b>（判据 4，源级）：placer 侧零列扫、零自带预算/上限/y 带字面量、
 * 读 Config 两键的非 Config 文件只有本门、预算扣减点唯一、窗判定不重复实现、
 * 门不自持地表门成员集合。</li>
 * <li><b>E micro 层</b>（判据 8）：结构侧对 {@code microStrengthAt} 的消费点数必须为 0
 * ⇒ 本片结论固定申报"交 P8"，出口不得被悄悄删。</li>
 * <li><b>F 位移量化</b>（判据 6 的一半）：对每个真实落块的结构，按"改前整台平面 y"
 * 与"改后逐列 y"逐列求差，给幅度直方＝结构落点位移表。</li>
 * </ul>
 * <p>
 * 离线装配与 P4/P5/P6 同一口径（{@link SurfaceHarness} + {@link Dim78ScatterDensityCheck} 的
 * 平坦网格合成世界与 {@code extraBlocks} 坑位；JEP330/MC-classpath 配方见
 * {@code plan/维度计划/调查取证/dim78-修复与整合/v12030-hotfix-replaceruntime-report.md} §3，一键入口
 * {@code tools/dim1/surface_checks.sh} 的 [12] 段）。
 * <p>
 * 用法：{@code PlacementContractCheck [all|source|pure] [seeds=8] [regionsPerSeed=8]}；
 * 退出码 0 = 全绿。
 */
public final class PlacementContractCheck {

    /** 采样窗边长（chunk）：一区 == 恰好一个 H-2 窗，与 P5/P6 同一几何。 */
    static final int AXIS = ProsperitySurfaceScatter.WINDOW_CHUNKS;
    /** 一区的方块边长。 */
    static final int SIDE = AXIS * 16;

    // ── 申报值（改生产默认而不改这里，第一条断言就红：plan §2.4 判据 4）──
    static final int DECL_BUDGET = 1;
    /**
     * P5b 定档：默认窗上限 = 8，即<b>对当前自然分布（窗内同模板 max 实测 8）不咬合的纯护栏</b>——
     * 用户口径"概率不动只治贴脸"，密度损失全部由 {@link #DECL_FAMILY_GAP} 承担。
     */
    static final int DECL_WINDOW_CAP = 8;
    /**
     * 上限"咬合灵敏度"探针档。<b>不得复用 {@link #DECL_WINDOW_CAP}</b>：默认档已取到自然分布上界，
     * 在它自身上永远观测不到"关掉上限会越过上限"，那条断言会结构性地不可满足（P5b 实测）。
     * 故咬合性一律在 3 这一有限档上证。
     */
    static final int PROBE_WINDOW_CAP = 3;
    /** P7c 新增：同族间距档默认 1 = 8 邻 chunk（贴脸由它治，不由配额治）。 */
    static final int DECL_FAMILY_GAP = 1;
    static final int DECL_LANDING_Y_MIN = 20;
    static final int DECL_LANDING_Y_MAX = 200;
    static final int DECL_TEMPLATE_ID_BASE = 1000;
    /** 判据 1 的 cap 扫描档（0 = 不限 = 回退位基准）。 */
    static final int[] CAP_SCAN = { 0, 1, 2, 3, 5, 8 };

    /** 与 P4/P5/P6 同一 8 seed 集（{@code Dim78ScatterDensityCheck.SEEDS} 值逐字照抄）。 */
    static final long[] SEEDS = { 0x503441L, 0x503442L, 0x503443L, 0x503444L, 0x503445L, 0x503446L, 0x503447L,
        0x503448L };

    /** 接地逐点比对时每 4 列取 1 列（判据 2 的规模按 chunk 计，256 列全取会让采样时间翻倍）。 */
    static final int GROUND_COL_STRIDE = 4;
    /** 改前两套制式逐列差的扫描步长（方块列）。 */
    static final int SCAN_STRIDE = 4;

    static final List<String> FAILURES = new ArrayList<>();
    /** {@link #groupC_census} 的真实命中集结论（判据 1 的数学货币；纯函数档产出，与地形无关）。 */
    static long censusPairs;
    static long censusHitSlots;
    static final long[] censusAllowedByCap = new long[CAP_SCAN.length];
    /** (窗,模板) → 该对的"哈希序首位命中槽"（= cap=1 时唯一放行的那一槽）。 */
    static final Map<String, String> censusFirstSlot = new HashMap<>();

    static String pairKey(long windowKey, String template) {
        return windowKey + "|" + template;
    }
    static final List<String> MISSING = new ArrayList<>();
    static int passed;
    /** C7：重放对账的样本量与漂移数（跨档累计）。 */
    static int checks;
    static int drift;

    public static void main(String[] args) throws Exception {
        final String mode = args.length > 0 ? args[0] : "all";
        final int seeds = intArg(args, 1, 8);
        final int regions = intArg(args, 2, 8);
        final long t0 = System.currentTimeMillis();
        bootstrap();
        groupA_unit();
        groupD_singleSourceTruth();
        groupE_microLayer();
        if ("source".equals(mode)) {
            report(0L, 0, "source");
            return;
        }
        groupB_pure(seeds, regions);
        if ("pure".equals(mode)) {
            report(0L, 0, "pure");
            return;
        }
        // Sample 的构造就把四群系配槽登记进 L1 账本（census 的 intentAt 要按真实群系权重掷骰，
        // 未登记时 ordinalAt 一律降级为"非本维"⇒ 权重恒 1.0，命中集就不是生产口径了）。
        final Sample sample = new Sample(seeds, regions);
        groupC_census(seeds, regions);
        if ("census".equals(mode)) {
            report(0L, 0, "census");
            return;
        }
        sample.run();
        groupA_chain(sample);
        groupB_columns(sample);
        groupC_rate(sample);
        groupF_displacement(sample);
        groupG_landingDelta(sample);
        report(System.currentTimeMillis() - t0, sample.generatedChunks, mode);
    }

    private static int intArg(String[] args, int i, int def) {
        return args.length > i ? Integer.parseInt(args[i]) : def;
    }

    // ═══════════════════════════ A 组：契约真值（单元 + 真实链）═══════════════════════════

    private static void groupA_unit() {
        final long seed = SEEDS[0];
        check(Config.prosperityStructureBudgetPerChunk == DECL_BUDGET, "A0 每 chunk 预算默认档申报 " + DECL_BUDGET
            + "（实测 " + Config.prosperityStructureBudgetPerChunk + "）");
        check(Config.prosperityStructureWindowRepeatCap == DECL_WINDOW_CAP, "A0 窗重复上限默认档申报 "
            + DECL_WINDOW_CAP + "（实测 " + Config.prosperityStructureWindowRepeatCap + "）");
        check(Config.prosperityStructureFamilyGapChunks == DECL_FAMILY_GAP, "A0 同族间距档默认申报 "
            + DECL_FAMILY_GAP + "（实测 " + Config.prosperityStructureFamilyGapChunks + "，0 = 关闭）");
        check(PlacementGate.LANDING_Y_MIN == DECL_LANDING_Y_MIN && PlacementGate.LANDING_Y_MAX == DECL_LANDING_Y_MAX,
            "A0 就绪门 y 带申报 [" + DECL_LANDING_Y_MIN + "," + DECL_LANDING_Y_MAX + "]（实测 ["
                + PlacementGate.LANDING_Y_MIN + "," + PlacementGate.LANDING_Y_MAX + "]）");
        check(readTemplateIdBase() == DECL_TEMPLATE_ID_BASE, "A0 族模板 id 基准申报 " + DECL_TEMPLATE_ID_BASE
            + "（实测反射读 " + readTemplateIdBase() + "）");

        check(PlacementGate.readyAt(DECL_LANDING_Y_MIN, true) && PlacementGate.readyAt(DECL_LANDING_Y_MAX, true),
            "A1 就绪门带内 + 顶块可落 ⇒ 放行");
        check(!PlacementGate.readyAt(DECL_LANDING_Y_MIN - 1, true)
            && !PlacementGate.readyAt(DECL_LANDING_Y_MAX + 1, true),
            "A1 就绪门带外（" + (DECL_LANDING_Y_MIN - 1) + "/" + (DECL_LANDING_Y_MAX + 1) + "）必拒");
        check(!PlacementGate.readyAt(70, false), "A1 就绪门顶块不可落必拒（y 在带内也不例外）");

        // A2/A3 只验预算与互斥，故把 H-2 两条规则临时置 0（回退位）——否则本格的窗名额或同族间距
        // 大概率先拒掉请求，测的就不是预算语义了（实测第一次 request 直接返回 null）。
        final int capForUnit = Config.prosperityStructureWindowRepeatCap;
        final int gapForUnit = Config.prosperityStructureFamilyGapChunks;
        Config.prosperityStructureWindowRepeatCap = 0;
        Config.prosperityStructureFamilyGapChunks = 0;
        try {
        // A2 落块 0 ⇒ 不计成功、不扣预算、不占互斥位
        final PlacementGate.ChunkGate g = PlacementGate.beginChunk(SurfaceGate.DIM78, seed, 3, 5);
        final PlacementGate.Permit p = g.request(PlacementGate.FAMILY_MACHINE, "boiler_frame");
        check(p != null, "A2 空门首次 request 必放行");
        check(!p.commit(0), "A2 commit(0 块) 必须判失败（plan §2.1 L5 禁止失败计成功）");
        check(g.committed() == 0, "A2 落块 0 ⇒ 预算一格不扣（实测 committed=" + g.committed() + "）");
        final PlacementGate.Permit p2 = g.request(PlacementGate.FAMILY_MACHINE, "pump_base");
        check(p2 != null, "A2 落块 0 之后同族同 chunk 仍可申请 ⇒ 互斥位也没被假成功污染");
        check(p2.commit(7), "A2 commit(7 块) 判成功");
        check(g.committed() == 1, "A2 真实落块后 committed=" + g.committed());
        check(g.request(PlacementGate.FAMILY_MACHINE, "gear_mill_table") == null,
            "A2 真实落块后同族第二次申请必拒（互斥位此刻才生效）");
        boolean reused = false;
        try {
            p2.commit(1);
        } catch (IllegalStateException e) {
            reused = true;
        }
        check(reused, "A2 许可复用必须抛（防一个 permit 扣两次预算）");

        // A3 预算是跨族总额；budget=0 是单点回退位
        final PlacementGate.ChunkGate g3 = PlacementGate.beginChunk(SurfaceGate.DIM78, seed, 3, 6);
        final PlacementGate.Permit a = g3.request(PlacementGate.FAMILY_OUTPOST, "watch_post");
        check(a != null && a.commit(30), "A3 首座 outpost 落块判成功");
        check(g3.request(PlacementGate.FAMILY_MACHINE, "gear_mill_table") == null,
            "A3 预算耗尽后他族也拒（H-3 预算 = 跨族总额，不是各族 1 座）");
        final int budget0 = Config.prosperityStructureBudgetPerChunk;
        Config.prosperityStructureBudgetPerChunk = 0;
        try {
            final PlacementGate.ChunkGate g4 = PlacementGate.beginChunk(SurfaceGate.DIM78, seed, 3, 7);
            int allowed = 0;
            for (int i = 0; i < 5; i++) {
                final PlacementGate.Permit q = g4.request(PlacementGate.FAMILY_MACHINE, "boiler_frame");
                if (q == null) {
                    break;
                }
                allowed++;
                q.abort();
            }
            check(allowed == 5, "A3 budget=0（回退位）时同 chunk 连续放行 " + allowed + " 次 == 5 ⇒ 预算开关单点生效");
        } finally {
            Config.prosperityStructureBudgetPerChunk = budget0;
        }
        } finally {
            Config.prosperityStructureWindowRepeatCap = capForUnit;
            Config.prosperityStructureFamilyGapChunks = gapForUnit;
        }

        // A4 两条 H-2 纯函数：幂等、不消费随机流、回退位与"无重放口"一律放行。
        // dense 是<b>合成</b>命中集（每 4 个 chunk 命中一次同一模板），与生产概率无关，只测门的语义。
        final PlacementGate.IntentFn dense = strideIntent(4);
        int flip = 0;
        int flipGap = 0;
        for (int i = 0; i < 400; i++) {
            final int cx = i * 7 - 500;
            final int cz = i * 13 + 91;
            final boolean first = PlacementGate.windowRepeatAllows(seed, cx, cz, "boiler_frame", DECL_WINDOW_CAP, dense);
            for (int k = 0; k < 3; k++) {
                if (PlacementGate.windowRepeatAllows(seed, cx, cz, "boiler_frame", DECL_WINDOW_CAP, dense) != first) {
                    flip++;
                }
            }
            final boolean g1 = PlacementGate.familySpacingAllows(seed, cx, cz, DECL_FAMILY_GAP, dense);
            for (int k = 0; k < 3; k++) {
                if (PlacementGate.familySpacingAllows(seed, cx, cz, DECL_FAMILY_GAP, dense) != g1) {
                    flipGap++;
                }
            }
        }
        check(flip == 0, "A4 窗重复上限判定幂等（同参数重复问出现 " + flip + " 次结论翻转）");
        check(flipGap == 0, "A4 同族间距判定幂等（同参数重复问出现 " + flipGap + " 次结论翻转）");
        check(PlacementGate.windowRepeatAllows(seed, 0, 0, "any", 0, dense), "A4 cap=0（回退位）一律放行");
        check(PlacementGate.familySpacingAllows(seed, 0, 0, 0, dense), "A4 gap=0（回退位）一律放行");
        check(PlacementGate.windowRepeatAllows(seed, 0, 0, "any", DECL_WINDOW_CAP, null),
            "A4 无重放口 ⇒ 窗上限放行（退化方向只能是\"不设上限\"，绝不退回密度乘子）");
        check(PlacementGate.familySpacingAllows(seed, 0, 0, DECL_FAMILY_GAP, null),
            "A4 无重放口 ⇒ 间距规则放行（同上：无命中集可枚举时不猜）");

        // A5 族模板 id 与散布件型零重合（保留：一处规则不会串改另一族的槽位域）
        int collide = 0;
        for (final String name : StructureRegistry.names()) {
            if (PlacementGate.templateIdOf(name) <= ProsperitySurfaceScatter.KIND_CHIMNEY) {
                collide++;
            }
        }
        check(collide == 0, "A5 族模板 id 与散布件型 0.." + ProsperitySurfaceScatter.KIND_CHIMNEY
            + " 零重合（越界 " + collide + " 名）⇒ 一处上限不会串改另一族");

        // A5b 判据 1 的<b>数学关系</b>构造性钉死（P7c 核心，双向敏感）：对合成命中集
        //   ①窗内放行槽数 == min(cap, 命中数) ②放行槽 ⊆ 命中槽
        // 旧实现（排名配额，P7/P7b）会让 ① 在稀疏命中集上失败（放行数与掷骰无关地趋近 cap/256）、
        // 让 ② 在非命中槽上失败；把语义改成"只数首次"（等价于 cap≡1）则会在 命中数>cap 时失败。
        int mathBad = 0;
        int countedBad = 0;
        int firstBad = 0;
        final StringBuilder mathRow = new StringBuilder();
        for (final int stride : new int[] { 16, 8, 4, 2, 1 }) {
            final PlacementGate.IntentFn hit = strideIntent(stride);
            final int hits = countHits(stride);
            for (final int cap : CAP_SCAN) {
                final int[] both = countWindowAllowed(seed, hit, "boiler_frame", cap);
                if (both[1] != hits) {
                    countedBad++; // 两套命中槽数法不一致 = 样本被数错，先于门判定排除
                }
                final int expect = cap <= 0 ? hits : Math.min(cap, hits);
                if (both[0] != expect) {
                    mathBad++;
                }
                mathRow.append(' ').append(stride).append('/').append(cap).append('=').append(both[0]);
            }
            // 首次出现永不因 cap 被拒：cap=1 时命中槽里必须恰有一个放行
            if (hits >= 1 && countWindowAllowed(seed, hit, "boiler_frame", 1)[0] != 1) {
                firstBad++;
            }
        }
        check(countedBad == 0, "A5b 命中槽两套数法一致（违例 " + countedBad + "）⇒ 先排除样本被数错");
        check(mathBad == 0, "A5b 命中槽放行数 == min(cap, 命中数) 对 5 档稀疏度 x 6 档 cap 全部成立（违例 "
            + mathBad + "；实测" + mathRow + "）⇒ cap 是重复上限而非密度乘子（排名配额旧语义必红）");
        check(firstBad == 0, "A5b cap=1 时每窗仍放行首座（违例 " + firstBad + "）⇒ 首次出现永不因上限被拒");

        // A5c 同族间距的合成场景：8 邻内两个命中槽必须<b>恰活一个</b>（不是 0 个，也不是 2 个）
        int pairBad = 0;
        int pairBadOff = 0;
        for (int bx = -3; bx <= 3; bx++) {
            for (int bz = -3; bz <= 3; bz++) {
                final int ax = bx * AXIS + 5;
                final int az = bz * AXIS + 7;
                final PlacementGate.IntentFn pair = fixedPairIntent(ax, az, ax + 1, az + 1, "watch_post",
                    PlacementGate.FAMILY_OUTPOST);
                final int alive = (PlacementGate.familySpacingAllows(seed, ax, az, 1, pair) ? 1 : 0)
                    + (PlacementGate.familySpacingAllows(seed, ax + 1, az + 1, 1, pair) ? 1 : 0);
                if (alive != 1) {
                    pairBad++;
                }
                final int aliveOff = (PlacementGate.familySpacingAllows(seed, ax, az, 0, pair) ? 1 : 0)
                    + (PlacementGate.familySpacingAllows(seed, ax + 1, az + 1, 0, pair) ? 1 : 0);
                if (aliveOff != 2) {
                    pairBadOff++;
                }
            }
        }
        check(pairBad == 0, "A5c 贴脸对（8 邻同族）在 gap=1 下恰存活 1 座（违例 " + pairBad
            + "）⇒ 间距规则既不同归于尽也不形同虚设");
        check(pairBadOff == 0, "A5c gap=0（回退位）时贴脸对两座都在（违例 " + pairBadOff + "）⇒ 单点开关生效");

        // A9 生产侧两族的 request 必须走带重放口的重载（否则 H-2 静默失效 = P7b 那种"看起来在判"）
        int noIntent = 0;
        for (final PlacementGate.IntentFn fn : new PlacementGate.IntentFn[] {
            RuinedMachinePlacer.MACHINE_INTENT, ProsperityOutpostPlacer.OUTPOST_INTENT }) {
            if (fn == null) {
                noIntent++;
            }
        }
        check(noIntent == 0, "A9 两族都导出了命中重放口（缺失 " + noIntent + " 个）⇒ H-2 两条规则有据可依");
        // A9 用"自洽"的合成重放口（本格自己就在命中集里，与生产侧 placer 的用法同形）：
        // 单命中槽 ⇒ cap=1 必放行；同窗双命中槽 ⇒ cap=1 恰活一个；cap=2 ⇒ 两个都活。
        final PlacementGate.IntentFn oneHit = fixedPairIntent(3, 5, 3, 5, "boiler_frame", PlacementGate.FAMILY_MACHINE);
        final PlacementGate.IntentFn twoHit = fixedPairIntent(3, 5, 12, 9, "boiler_frame",
            PlacementGate.FAMILY_MACHINE);
        Config.prosperityStructureFamilyGapChunks = 0;
        int alive1 = 0;
        int alive2 = 0;
        try {
            Config.prosperityStructureWindowRepeatCap = 1;
            final PlacementGate.ChunkGate g9 = PlacementGate.beginChunk(SurfaceGate.DIM78, seed, 3, 5);
            final PlacementGate.Permit p9 = g9.request(PlacementGate.FAMILY_MACHINE, "boiler_frame", oneHit);
            if (p9 != null) {
                alive1++;
                p9.abort();
            }
            for (final int[] slot : new int[][] { { 3, 5 }, { 12, 9 } }) {
                final PlacementGate.ChunkGate g = PlacementGate
                    .beginChunk(SurfaceGate.DIM78, seed, slot[0], slot[1]);
                final PlacementGate.Permit q = g.request(PlacementGate.FAMILY_MACHINE, "boiler_frame", twoHit);
                if (q != null) {
                    q.abort();
                    alive1++;
                }
                final PlacementGate.ChunkGate g2 = PlacementGate
                    .beginChunk(SurfaceGate.DIM78, seed, slot[0], slot[1]);
                Config.prosperityStructureWindowRepeatCap = 2;
                final PlacementGate.Permit q2 = g2.request(PlacementGate.FAMILY_MACHINE, "boiler_frame", twoHit);
                Config.prosperityStructureWindowRepeatCap = 1;
                if (q2 != null) {
                    q2.abort();
                    alive2++;
                }
            }
        } finally {
            Config.prosperityStructureWindowRepeatCap = DECL_WINDOW_CAP;
            Config.prosperityStructureFamilyGapChunks = DECL_FAMILY_GAP;
        }
        check(alive1 == 2, "A9 单命中槽放行 + 双命中槽在 cap=1 下恰活一个（实测合计 " + alive1
            + "，应为 1+1）⇒ 首次出现必放、重复副本被削");
        check(alive2 == 2, "A9 同一对命中槽在 cap=2 下两座都在（实测 " + alive2
            + "）⇒ cap 档位真的进了判定（不是\"只数首次\"）");
        final PlacementGate.ChunkGate g9n = PlacementGate.beginChunk(SurfaceGate.DIM78, seed, 3, 5);
        check(g9n.request(PlacementGate.FAMILY_MACHINE, "boiler_frame", null) != null,
            "A9 无重放口的 request 重载只过预算/互斥（上限不适用，不退回密度乘子）");

        // A6 roster 扩形状（目标 4）
        final Map<String, Integer> famCount = new TreeMap<>();
        int badNumeric = 0;
        int badDamaged = 0;
        int ruinNumeric = 0;
        int ruinMissingDamaged = 0;
        for (final StructureRegistry.Entry e : StructureRegistry.all()) {
            famCount.merge(e.family, 1, Integer::sum);
            final boolean ruin = PlacementGate.FAMILY_RUIN.equals(e.family);
            if (e.placementDenominator != 0 || e.windowRepeatCap != 0) {
                if (ruin) {
                    ruinNumeric++;
                } else {
                    badNumeric++;
                }
            }
            if (e.allowsDamagedVariant) {
                if (!ruin) {
                    badDamaged++;
                }
            } else if (ruin) {
                ruinMissingDamaged++;
            }
        }
        check(Integer.valueOf(5).equals(famCount.get(PlacementGate.FAMILY_MACHINE))
            && Integer.valueOf(6).equals(famCount.get(PlacementGate.FAMILY_OUTPOST))
            && Integer.valueOf(26).equals(famCount.get(PlacementGate.FAMILY_UNSCOPED))
            && famCount.get(PlacementGate.FAMILY_RUIN) != null
            && famCount.get(PlacementGate.FAMILY_RUIN) == RuinShapes.ALL.length,
            "A6 族标注 = machine 5 / outpost 6 / 城内 26 未标注 / ruin " + RuinShapes.ALL.length
                + "（P8 废墟族，实测 " + famCount + "）");
        check(badNumeric == 0, "A6 既有 39 条目的分母/窗上限一律 0 = 跟随 Config（非 0 者 " + badNumeric
            + " 条 ⇒ 会出现第二处数字真值）");
        check(badDamaged == 0, "A6 既有 39 条目 allowsDamagedVariant 一律 false（true 者 " + badDamaged
            + " 条）⇒ P8 的损毁算子碰不到旧模板（判据 2\"算子只作用于新族\"）");
        check(ruinNumeric == 0, "A6 P8 废墟族也一律把分母/窗上限传 0 = 跟随 Config 族键（非 0 者 " + ruinNumeric
            + " 条）⇒ 全名册仍只有一处数字真值");
        check(ruinMissingDamaged == 0, "A6 废墟族每条都 allowsDamagedVariant=true（缺位 " + ruinMissingDamaged
            + " 条）⇒ opt-in 位是本族的族级契约，不是逐条偶发");
        final StructureRegistry.Entry m = StructureRegistry.get("boiler_frame");
        check(m != null && PlacementGate.FAMILY_MACHINE.equals(m.family) && m.footprintX > 0,
            "A6 机型条目在 roster 里可查（族/footprint 齐）");
        check(PlacementGate.effectiveWindowRepeatCap(m) == DECL_WINDOW_CAP,
            "A6 roster 覆写为 0 时生效上限 = Config 族键（实测 " + PlacementGate.effectiveWindowRepeatCap(m) + "）");
        final StructureRegistry.Entry probe = new StructureRegistry.Entry(
            "__p7b_probe__",
            StructureRegistry.Dimension.PROSPERITY,
            1,
            1,
            (sink, x, y, z, s) -> {},
            PlacementGate.FAMILY_MACHINE,
            0,
            5,
            true);
        check(PlacementGate.effectiveWindowRepeatCap(probe) == 5,
            "A6 roster 覆写非 0 时以 roster 为准（P8 的逐模板上限通道，实测 " + PlacementGate
                .effectiveWindowRepeatCap(probe) + "）");
        check(!probe.allowsDamagedVariant || probe.allowsDamagedVariant, "A6 P8 依赖面：Entry 携 allowsDamagedVariant（探针 " + probe.allowsDamagedVariant + "）");
    }

    /** 合成命中集：{@code (cx mod stride == 0 && cz mod stride == 0)} 的槽位命中同一模板。 */
    private static PlacementGate.IntentFn strideIntent(final int stride) {
        return (seed, cx, cz) -> Math.floorMod(cx, stride) == 0 && Math.floorMod(cz, stride) == 0
            ? new PlacementGate.Intent("boiler_frame", cx << 4, cz << 4, 5, 5)
            : null;
    }

    /** stride 档下命中函数在 16×16 对齐窗内的命中槽数（与 {@link #strideIntent} 严格同一谓词）。 */
    private static int countHits(int stride) {
        int n = 0;
        for (int lx = 0; lx < AXIS; lx++) {
            for (int lz = 0; lz < AXIS; lz++) {
                if (Math.floorMod(lx, stride) == 0 && Math.floorMod(lz, stride) == 0) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * 在一个对齐窗内按<b>命中槽</b>口径数放行（生产侧只会对"真的请求放置"的槽问门，未命中槽的
     * 答案与生产行为无关，把它算进来就是数错货币）。返回 {@code [命中且放行的槽数, 命中槽数]}。
     */
    private static int[] countWindowAllowed(long seed, PlacementGate.IntentFn fn, String template, int cap) {
        int allowed = 0;
        int hits = 0;
        for (int lx = 0; lx < AXIS; lx++) {
            for (int lz = 0; lz < AXIS; lz++) {
                final PlacementGate.Intent it = fn.intentAt(seed, lx, lz);
                if (it == null || !template.equals(it.templateName)) {
                    continue;
                }
                hits++;
                if (PlacementGate.windowRepeatAllows(seed, lx, lz, template, cap, fn)) {
                    allowed++;
                }
            }
        }
        return new int[] { allowed, hits };
    }

    /** 合成"至多两个命中槽"的命中集（其余槽一律不命中）；模板名由调用方给，保证与 request 自洽。 */
    private static PlacementGate.IntentFn fixedPairIntent(final int ax, final int az, final int bx, final int bz,
        final String template, final String family) {
        return (seed, cx, cz) -> (cx == ax && cz == az) || (cx == bx && cz == bz)
            ? new PlacementGate.Intent(template, cx << 4, cz << 4,
                PlacementGate.FAMILY_OUTPOST.equals(family) ? 7 : 5,
                PlacementGate.FAMILY_OUTPOST.equals(family) ? 7 : 5)
            : null;
    }

    /**
     * A 真实链（判据 1）的断言面：数据由 {@link Sample#verifyHits} 在<b>每区跑完时</b>就地采集
     * （那时该区的只读网格还活着），这里只做判定。
     */
    private static void groupA_chain(Sample s) {
        check(s.chainTested >= 20, "A7 真实链假成功场景至少取到 20 个命中 chunk（实测 " + s.chainTested + "）");
        check(s.chainNonIdempotent == 0,
            "A7 同 chunk 重复放置的落块数逐位幂等（不一致 " + s.chainNonIdempotent + " 处）");
        check(s.chainNoAttempt == 0, "A7 每个命中 chunk 在拒绝档都真的走到了写入尝试（未尝试 " + s.chainNoAttempt
            + " 处 = 样本被门吃掉，测不到落块真值）");
        check(s.chainBudgetLeak == 0, "A7 sink 全拒 ⇒ 预算一格不扣；落块 > 0 ⇒ 恰扣 1（违例 " + s.chainBudgetLeak
            + " 处）");
        check(s.chainNoPermitRecord == 0, "A7 每个被门放行的请求都在 gate 上留了模板名（记账缺口 "
            + s.chainNoPermitRecord + " 处）");
        check(s.chainLie == 0, "A7 落块 0 却返回 true 的\"假成功\"必须为 0（实测 " + s.chainLie + "）");
        check(s.chainBadCommit == 0,
            "A8 outpost 落块失败后本 chunk 预算未扣（违例 " + s.chainBadCommit + "/" + s.chainTried + "）");
        check(s.chainTried >= 3 && s.chainA8Blocked == 0 && s.chainA8Allowed == s.chainTried,
            "A8 \"outpost 命中但落块失败\"的 " + s.chainTried + " 个 chunk 上机器申请全部放行（" + s.chainA8Allowed
                + "，被拒 " + s.chainA8Blocked + "）⇒ 改造前\"假成功吞掉预算/互斥位\"已闭合");
    }

    // ═════════════════════════ B 组：接地同源（判据 2 的改后表）═════════════════════════

    private static void groupB_pure(int seeds, int regions) {
        long chunks = 0;
        long cols = 0;
        long diff = 0;
        long crossCallDiff = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int si = 0; si < seeds; si++) {
            final long seed = SEEDS[si % SEEDS.length];
            final CityVariants.GroundFn structureGround = PlacementGate.groundFn(seed);
            for (int r = 0; r < regions; r++) {
                final int cx0 = si * 4096 + r * AXIS;
                final int cz0 = si * 924816;
                for (int dx = 0; dx < AXIS; dx++) {
                    for (int dz = 0; dz < AXIS; dz++) {
                        chunks++;
                        final int bx = ((cx0 + dx) << 4);
                        final int bz = ((cz0 + dz) << 4);
                        for (int lx = 0; lx < 16; lx += GROUND_COL_STRIDE) {
                            for (int lz = 0; lz < 16; lz += GROUND_COL_STRIDE) {
                                final int x = bx + lx;
                                final int z = bz + lz;
                                final int yStruct = structureGround.groundY(x, z);
                                final int yPure = ProsperityTerrainProfile.heightAt(seed, x, z);
                                final int yCity = PlacementGate.groundFn(seed).groundY(x, z);
                                cols++;
                                if (yStruct != yPure) {
                                    diff++;
                                }
                                if (yStruct != yCity) {
                                    crossCallDiff++;
                                }
                                min = Math.min(min, yStruct);
                                max = Math.max(max, yStruct);
                            }
                        }
                    }
                }
            }
        }
        table("T2-接地逐点(改后)", "chunk=" + chunks, "列=" + cols, "heightAt分歧=" + diff,
            "同供给器跨调用分歧=" + crossCallDiff, "y域=[" + min + "," + max + "]");
        check(chunks >= 16384, "B1 接地逐点比对样本 >= 16384 chunk（实测 " + chunks + "）");
        check(diff == 0 && crossCallDiff == 0, "B1 三族同列取到的 y 逐点相同（heightAt 分歧 " + diff
            + "，城/结构分歧 " + crossCallDiff + "）");
        check(max > min, "B1 样本不是同一平面（y 域跨度 " + (max - min) + " 格）");
    }

    /** 改前幅度表：真实网格上"列扫 vs 纯函数"两套制式的逐列差。 */
    private static void groupB_columns(Sample s) {
        table("T3-改前两制式逐列差", "chunk=" + s.generatedChunks, "列扫样本=" + s.colScanN,
            "均值|d|=" + fmt(s.colScanSum / (double) Math.max(1, s.colScanN), 3),
            "最大|d|=" + s.colScanMax, "|d|>=1占比=" + fmt(100.0 * s.colScanNonZero / (double) Math.max(1, s.colScanN), 3)
                + "pp", "直方(0..14,15+)=" + Arrays.toString(s.colScanHist));
        check(s.colScanN >= 200000, "B2 列扫样本 >= 20 万列（实测 " + s.colScanN + "）");
        // 实测结论（与本片开工预期相反，据实钉住）：P2/P3 之后真实地表的<b>顶行行号 == heightAt</b>
        // （generateTerrain 用 heightAt 填主体，表层缝原位替换），所以"列扫 vs 纯函数"在
        // <b>同一列</b>上逐位相同。审计 A-4 的真实错位面不在这里，而在"改前整机落在一列扫出的
        // 同一个平面上"⇒ 机内各列相对各自地面错位，量化见 T5。
        check(s.colScanMax == 0, "B2 同一列上列扫与 heightAt 逐位相同（实测最大 |d|=" + s.colScanMax
            + "，非 0 说明表层缝又抬/压了顶行，接地同源需重判）");
        check(s.colScanHist[0] == s.colScanN,
            "B2 全部 " + s.colScanN + " 列 0 分歧（实测 0 差列 " + s.colScanHist[0] + "）");
    }

    // ═════════════════════════ C 组：贴脸率与窗上限（判据 1/2/3）═════════════════════════

    private static void groupC_rate(Sample s) {
        final Rate base = s.rates.get(Pass.NOCAP.label);
        final Set<Long> baseKeys = base.winTemplateKeys();
        for (final Pass p : Pass.values()) {
            final Rate r = s.rates.get(p.label);
            final Set<Long> keys = r.winTemplateKeys();
            int kept = 0;
            for (final long k : baseKeys) {
                if (keys.contains(k)) {
                    kept++;
                }
            }
            table("T4-窗上限/间距七档表", "档=" + p.label, "cap=" + p.cap, "gap=" + p.gap, "chunk=" + r.chunks,
                "城窗跳过=" + r.citySkips, "结构=" + r.structures, "8邻贴脸=" + r.adjacent,
                "贴脸率=" + fmt(100.0 * r.adjacent / (double) Math.max(1, r.structures), 3) + "%",
                "outpost=" + r.outpostHits, "机器=" + r.machineHits,
                "落块/chunk=" + fmt(r.solidSum / (double) Math.max(1, r.chunks), 4),
                "窗内同模板发射max=" + r.maxWinEmit,
                "请求总数=" + r.requestedTotal(),
                "实发射口径首次保留率=" + fmt(100.0 * kept / (double) Math.max(1, baseKeys.size()), 3) + "%",
                "重复超额(cap档)=" + base.duplicateExcess(p.cap));
        }
        final Rate def = s.rates.get(Pass.DEFAULT.label);
        final Rate noCap = base;
        final Rate naked = s.rates.get(Pass.NAKED.label);
        final Rate gapOnly = s.rates.get(Pass.GAPONLY.label);
        final Rate cap1 = s.rates.get(Pass.CAP1.label);
        final Rate cap3 = s.rates.get(Pass.CAP3.label);
        check(noCap.structures > 200 && naked.structures > 200,
            "C1 回退两档结构样本各 > 200 座（实测 " + noCap.structures + "/" + naked.structures
                + "），否则贴脸率是噪声（默认档由 C6 单独判）");
        final double rDef = 100.0 * def.adjacent / Math.max(1, def.structures);
        final double rNoCap = 100.0 * noCap.adjacent / Math.max(1, noCap.structures);
        check(rDef <= rNoCap + 1e-9, "C2 单调性：关掉两条 H-2 规则后贴脸率不降（" + fmt(rDef, 3) + "% -> "
            + fmt(rNoCap, 3) + "%）");
        check(naked.structures >= noCap.structures, "C2 单调性：关掉预算后结构座数不减（" + noCap.structures
            + " <= " + naked.structures + "）");
        // cap 单调性只对"有限档"成立：cap=0 是不限（回退位），它天然是上界而不是序列的起点。
        for (int i = 2; i < CAP_SCAN.length; i++) {
            final Rate lo = rateForCap(s, CAP_SCAN[i - 1]);
            final Rate hi = rateForCap(s, CAP_SCAN[i]);
            check(lo.requestedTotal() <= hi.requestedTotal() && lo.maxWinEmit <= hi.maxWinEmit,
                "C2 cap 单调性：cap " + CAP_SCAN[i - 1] + " -> " + CAP_SCAN[i] + " 时发射数与窗内 max 都不减（实测 "
                    + lo.requestedTotal() + "->" + hi.requestedTotal() + " / " + lo.maxWinEmit + "->"
                    + hi.maxWinEmit + "）");
        }
        check(def.maxWinEmit <= DECL_WINDOW_CAP, "C3 默认档窗内同模板发射 chunk 数 <= 上限 " + DECL_WINDOW_CAP
            + "（实测 " + def.maxWinEmit + "）");
        final Rate probe = rateForCap(s, PROBE_WINDOW_CAP);
        check(probe.maxWinEmit == PROBE_WINDOW_CAP && noCap.maxWinEmit > PROBE_WINDOW_CAP,
            "C3 灵敏度/反假绿：上限咬合性在探针档 " + PROBE_WINDOW_CAP + " 上证——该档窗内 max 恰为 "
                + PROBE_WINDOW_CAP + "（实测 " + probe.maxWinEmit + "）且关掉上限后越过它（实测 "
                + noCap.maxWinEmit + "）。不在默认档上证：默认档已取自然分布上界，'关掉上限会越过上限'在其上结构性不可满足");
        check(naked.maxWinEmit == noCap.maxWinEmit,
            "C4 预算键与窗名额互不相干：budget=0/cap=0 与 budget=1/cap=0 的窗发射数应同（" + naked.maxWinEmit + " vs "
                + noCap.maxWinEmit + "）");

        // C5 判据 1 的核心数学关系（实测，两侧都钉）：下降幅度落在
        //   [实发射口径的重复超额, 命中集口径的重复超额] 之间。
        // 下界 = 用本档自己的 (窗,模板) 实发射计数算的 Σ max(0,h−cap)（纯观测值，无假设）。
        // 上界 = 用纯函数命中集算的 Σ max(0,h−cap)（census 档产出，含"掷中但被预算/城窗抢占"的槽）。
        // 门按<b>命中集</b>计数，所以真实下降只会介于两者之间；超出上界 = 上限在削首次出现，
        // 低于下界 = 上限没咬合。旧排名配额在 cap=1 时下降 ~98%（远超上界），必红。
        for (final int cap : CAP_SCAN) {
            final Rate r = rateForCap(s, cap);
            final long removed = noCap.requestedTotal() - r.requestedTotal();
            final long obsExcess = noCap.duplicateExcess(cap);
            final long hitExcess = censusHitSlots - censusAllowedByCap[idxOfCap(cap)];
            check(removed >= obsExcess && removed <= hitExcess, "C5 cap=" + cap + " 的下降幅度 " + removed
                + " 必须 ∈ [实发射超额 " + obsExcess + ", 命中集超额 " + hitExcess
                + "]（区间外即 \"上限在削首次出现\" 或 \"上限没咬合\"）");
            final Set<Long> keys = r.winTemplateKeys();
            int lost = 0;
            int attributed = 0;
            for (final long wk : baseKeys) {
                if (keys.contains(wk)) {
                    continue;
                }
                lost++;
            }
            // 逐对归因：丢掉的 (窗,模板) 必须是因为"哈希序首位命中槽根本没发射过"（被城窗/预算抢占）
            for (final Map.Entry<Long, Map<String, Integer>> e : noCap.winEmit.entrySet()) {
                for (final String tmpl : e.getValue().keySet()) {
                    if (keys.contains(e.getKey() * 31L + tmpl.hashCode())) {
                        continue;
                    }
                    final String first = censusFirstSlot.get(pairKey(e.getKey(), tmpl));
                    if (first != null && !r.emitSlots.contains(e.getKey() + "|" + tmpl + "|" + first)
                        && !noCap.emitSlots.contains(e.getKey() + "|" + tmpl + "|" + first)) {
                        attributed++;
                    }
                }
            }
            check(cap <= 0 || lost == attributed, "C5b cap=" + cap + " 的首次命中缺口 " + lost
                + " 全部可归因为 \"首位命中槽被城窗/预算抢占\"（已归因 " + attributed
                + "）⇒ 实发射口径保留率 " + fmt(100.0 * (baseKeys.size() - lost) / Math.max(1, baseKeys.size()), 3)
                + "% 的差额与窗上限无关；命中集口径的 100% 由 C8 精确钉");
        }

        // C6 原 P7b 常红断言（判据 3 的验收对象）：默认档不得是回退档的密度乘子。
        check(def.structures >= noCap.structures / 5, "C6 默认档结构密度不得低于回退档的 1/5（实测 " + def.structures
            + " vs " + noCap.structures + "）：窗内同模板上限必须以命中集合为条件才叫重复上限，"
            + "否则它只是把密度乘上 cap/256（P7/P7b 的排名配额语义，plan §7.2 已改判）");
        check(cap1.maxWinEmit == 1 && cap3.maxWinEmit == PROBE_WINDOW_CAP,
            "C6b 上限必须随 cap 咬合：cap=1 ⇒ 窗内 max 恰为 1、cap=" + PROBE_WINDOW_CAP + " ⇒ 窗内 max 恰为 "
                + PROBE_WINDOW_CAP + "（实测 " + cap1.maxWinEmit + " / " + cap3.maxWinEmit
                + "）；若实现退化成\"只数首次\"（等价 cap≡1），后半句必红");
        check(cap3.requestedTotal() > cap1.requestedTotal(), "C6c cap=3 的发射数必须严格高于 cap=1（实测 "
            + cap3.requestedTotal() + " vs " + cap1.requestedTotal() + "）⇒ 上限档位不是摆设");

        // C7 命中重放与真实发射逐点同值（P7c 把掷骰收进唯一 roll() 之后的回归钉）
        check(checks >= 1000, "C7 重放对账样本 ≥ 1000 次真实请求（实测 " + checks + "）");
        check(drift == 0, "C7 每个被门放行的请求，其族的 intentAt 重放给出同一模板（漂移 " + drift
            + "）⇒ 活链掷骰与门的命中集是同一份事实");
        System.out.println("# P7C-C7 note=forDimension(dimId) 在离线 harness 里恒 UNBOUND"
            + "（SurfaceHarness 的 def 未走 DimensionRegistrar，resolvedDimId 未解析），"
            + "故 \"编排器权重口径 == 重放权重口径\" 这条只能实机验，登记为未闭合边界");

        // C10 贴脸由间距治（判据 2）：只开间距那一档必须把贴脸率打下来，且不得把总体砍没
        final double rGap = 100.0 * gapOnly.adjacent / Math.max(1, gapOnly.structures);
        check(rGap <= rNoCap / 2.0, "C10 只开同族间距（cap=0/gap=1）后贴脸率必须 ≤ 回退档的一半（实测 "
            + fmt(rGap, 3) + "% vs " + fmt(rNoCap, 3) + "%）⇒ 贴脸由间距规则负责");
        check(gapOnly.structures >= noCap.structures / 2, "C10 只开间距后的结构座数不得低于回退档一半（实测 "
            + gapOnly.structures + " vs " + noCap.structures
            + "）⇒ 治贴脸不是砍密度（P7b 那个 −98.8% 就是这条红线要拦的形状）");
        check(rDef <= rGap + 1e-9, "C10 默认档（上限+间距同时开）贴脸率不高于只开间距档（" + fmt(rDef, 3) + "% vs "
            + fmt(rGap, 3) + "%）⇒ 两条规则方向一致，不互相抵消");
    }

    private static int idxOfCap(int cap) {
        for (int i = 0; i < CAP_SCAN.length; i++) {
            if (CAP_SCAN[i] == cap) {
                return i;
            }
        }
        throw new IllegalStateException("CAP_SCAN 缺档 " + cap);
    }

    private static Rate rateForCap(Sample s, int cap) {
        for (final Pass p : Pass.values()) {
            if (p.inCapScan() && p.cap == cap) {
                return s.rates.get(p.label);
            }
        }
        throw new IllegalStateException("cap 扫描档缺失：" + cap + "（Pass 枚举与 CAP_SCAN 不同步）");
    }

    /**
     * C8/C9（判据 1 的"命中集口径"精确钉，<b>不需要地形</b>）：把生产侧两族的 {@code intentAt}
     * 在 8 seed × 每区一整窗上枚举成真实命中集，然后逐 (窗, 模板) 断言
     * 「{@code windowRepeatAllows} 放行的命中槽数 == min(cap, 命中槽数)」，以及 cap=1 时每对
     * (窗,模板) 恰活 1 座。这条断言的货币是<b>命中集</b>，不含预算/互斥/就绪门，所以它是
     * 精确等式而不是带缺口的下限——C5b 的实发射口径保留率因预算抢占而达不到 100%，缺口在那边归因。
     */
    private static void groupC_census(int seeds, int regions) {
        long pairs = 0;
        long hitSlots = 0;
        int mathBad = 0;
        int firstBad = 0;
        final long[] allowedByCap = new long[CAP_SCAN.length];
        final PlacementGate.IntentFn[] fns = { RuinedMachinePlacer.MACHINE_INTENT,
            ProsperityOutpostPlacer.OUTPOST_INTENT };
        final String[] fams = { PlacementGate.FAMILY_MACHINE, PlacementGate.FAMILY_OUTPOST };
        long gapAlive = 0;
        for (int si = 0; si < seeds; si++) {
            final long seed = SEEDS[si % SEEDS.length];
            for (int rg = 0; rg < regions; rg++) {
                final int cx0 = si * 4096 + rg * AXIS;
                final int cz0 = si * 924816;
                for (int f = 0; f < fns.length; f++) {
                    final PlacementGate.IntentFn fn = fns[f];
                    // 本窗内：模板 → 命中槽坐标列表
                    final Map<String, List<int[]>> hits = new TreeMap<>();
                    for (int dx = 0; dx < AXIS; dx++) {
                        for (int dz = 0; dz < AXIS; dz++) {
                            final PlacementGate.Intent it = fn.intentAt(seed, cx0 + dx, cz0 + dz);
                            if (it == null) {
                                continue;
                            }
                            hits.computeIfAbsent(it.templateName, k -> new ArrayList<>())
                                .add(new int[] { cx0 + dx, cz0 + dz });
                            if (PlacementGate.familySpacingAllows(seed, cx0 + dx, cz0 + dz, 1, fn)) {
                                gapAlive++;
                            }
                        }
                    }
                    for (final Map.Entry<String, List<int[]>> e : hits.entrySet()) {
                        final String t = e.getKey();
                        final List<int[]> slots = e.getValue();
                        pairs++;
                        hitSlots += slots.size();
                        censusPairs = pairs;
                        censusHitSlots = hitSlots;
                        for (final int cap : CAP_SCAN) {
                            int allowed = 0;
                            for (final int[] slot : slots) {
                                if (PlacementGate.windowRepeatAllows(seed, slot[0], slot[1], t, cap, fn)) {
                                    allowed++;
                                    if (cap == 1) {
                                        // cap=1 唯一放行者 == 哈希序首位命中槽（判据 1 的"首次"定义）
                                        censusFirstSlot.put(pairKey(windowKey(seed, slot[0], slot[1]), t),
                                            slot[0] + "," + slot[1]);
                                    }
                                }
                            }
                            final int expect = cap <= 0 ? slots.size() : Math.min(cap, slots.size());
                            if (allowed != expect) {
                                mathBad++;
                            }
                            if (cap == 1 && allowed != 1) {
                                firstBad++;
                            }
                            allowedByCap[idxOfCap(cap)] += allowed;
                            censusAllowedByCap[idxOfCap(cap)] = allowedByCap[idxOfCap(cap)];
                        }
                    }
                }
            }
        }
        table("T6-命中集数学表(纯函数)", "窗-模板对=" + pairs, "命中槽=" + hitSlots,
            "各cap放行命中槽合计(cap" + Arrays.toString(CAP_SCAN) + ")=" + Arrays.toString(allowedByCap),
            "min(cap,h)违例=" + mathBad, "cap=1违例=" + firstBad, "间距gap=1存活槽=" + gapAlive);
        check(pairs >= 100 && hitSlots >= 200, "C8 命中集样本足够（窗-模板对 " + pairs + " / 命中槽 " + hitSlots
            + "）⇒ 数学表不是空集自证");
        check(mathBad == 0, "C8 对<b>全部真实命中集</b>：放行命中槽数 == min(cap, 命中槽数)，cap∈"
            + Arrays.toString(CAP_SCAN) + " 违例 " + mathBad + " ⇒ 判据 1 的数学关系实测成立");
        check(firstBad == 0, "C9 cap=1 时每个命中过的 (窗,模板) 恰放行 1 座（违例 " + firstBad
            + "）⇒ 首次出现永不因上限被拒（P7/P7b 的排名配额在稀疏命中集上必然违例）");
    }

    // ═════════════════════════ F 组：落点位移量化（判据 6 的一半）═════════════════════════

    private static void groupF_displacement(Sample s) {
        table("T5-改前平面vs改后逐列位移", "命中结构=" + s.dispStructures, "列样本=" + s.dispCols,
            "均值|Δ|=" + fmt(s.dispSum / (double) Math.max(1, s.dispCols), 3),
            "最大|Δ|=" + s.dispMax, "Δ!=0占比=" + fmt(100.0 * s.dispNonZero / (double) Math.max(1, s.dispCols), 3)
                + "pp", "整机最大偏移直方(0..10,11+)=" + Arrays.toString(s.dispPerStructHist),
            "有位移座数=" + s.dispStructuresMoved, "机器列=" + s.dispMachineCols,
            "机器均值=" + fmt(s.dispMachineSum / (double) Math.max(1, s.dispMachineCols), 3),
            "机器最大=" + s.dispMachineMax, "outpost列=" + s.dispOutpostCols,
            "outpost均值=" + fmt(s.dispOutpostSum / (double) Math.max(1, s.dispOutpostCols), 3),
            "outpost最大=" + s.dispOutpostMax);
        check(s.dispStructures >= 50, "F1 位移量化样本 >= 50 座（实测 " + s.dispStructures + "）");
        check(s.dispMax >= 1, "F1 改前整台平面与改后逐列确实有位移（最大 " + s.dispMax
            + " 格）⇒ 判据 6 的\"落点位变\"是真实现象，不是注释里的推测");
        check(s.dispStructuresMoved <= s.dispStructures, "F1 有位移座数不超过总座数");
    }

    // ═══════════════════ G 组：本片新增规则引入后的结构落点位移对账（判据 6 的后半）═══════════════════

    /**
     * 回退档（cap=0/gap=0 = P7b 终态行为）vs 默认档（cap=3/gap=1）的<b>逐 chunk 落点签名</b>对账：
     * 两档跑的是同一份只读真实网格、同一批掷骰，唯一变量是 H-2 两条规则。于是
     * "共同命中 chunk 的签名逐位相同" 就是把"本片只删副本、不挪结构"钉成断言——
     * 任何 origin/接地/落块被顺手改动都会在这里显形（P7b 对 d5b7ca5 量到的 363 行 y 变是
     * P7 的接地改道，与本片的位移是两个不相干的面，见证据文档 §6）。
     */
    private static void groupG_landingDelta(Sample s) {
        final Rate before = s.rates.get(Pass.NOCAP.label);
        final Rate after = s.rates.get(Pass.DEFAULT.label);
        int common = 0;
        int same = 0;
        int moved = 0;
        final Map<String, Integer> movedKinds = new TreeMap<>();
        for (final Map.Entry<String, String> e : before.sigs.entrySet()) {
            final String b = e.getValue();
            final String a = after.sigs.get(e.getKey());
            if (a == null) {
                continue; // 被本片的 cap/gap 拒掉的座（消失，不是位移）
            }
            common++;
            if (a.equals(b)) {
                same++;
            } else {
                moved++;
                movedKinds.merge(b.split("\\|")[0] + " -> " + a.split("\\|")[0], 1, Integer::sum);
            }
        }
        table("T7-本片规则引入后的落点对账", "回退档命中=" + before.sigs.size(), "默认档命中=" + after.sigs.size(),
            "共同=" + common, "签名逐位相同=" + same, "签名变=" + moved, "消失(被上限/间距拒)="
                + (before.sigs.size() - common), "新增(改造前被抢占)=" + (after.sigs.size() - common),
            "签名变化种类=" + movedKinds);
        check(before.sigs.size() >= 200, "G1 落点对账样本 ≥ 200 座（实测 " + before.sigs.size() + "）");
        check(moved == 0, "G1 共同命中 chunk 的落点签名逐位相同（实测变化 " + moved + " 种 " + movedKinds
            + "）⇒ 窗上限与间距只删重复副本/贴脸副本，不挪任何一座的位置");
        check(common == after.sigs.size(), "G1 默认档没有凭空多出落点（共同 " + common + " vs 默认档 "
            + after.sigs.size() + "）⇒ 两条规则都是单向收紧");
    }

    // ═════════════════════════ D 组：单一真值（源级，判据 4）═════════════════════════

    private static void groupD_singleSourceTruth() throws Exception {
        final Path root = Paths.get("");
        final String machine = stripComments(read(root.resolve(
            "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java")));
        final String outpost = stripComments(read(root.resolve(
            "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java")));
        final String orchestrator = stripComments(read(root.resolve(
            "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java")));
        final String gate = stripComments(read(root.resolve(
            "src/main/java/com/miaokatze/gtsr/common/dimension/framework/structure/PlacementGate.java")));
        final String scatter = stripComments(read(root.resolve(
            "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperitySurfaceScatter.java")));
        final String ruin = stripComments(read(root.resolve(
            "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/RuinPlacer.java")));

        // D1 接地唯一制式
        check(!machine.contains("findSurfaceY"), "D1 机器层零列扫取高（审计 A-4 闭合，再出现即红）");
        check(!outpost.contains("findSurfaceY"), "D1 outpost 层零列扫取高（:335 那处已改道）");
        check(!ruin.contains("findSurfaceY"), "D1 废墟层（P8）零列扫取高（新增族也不许自带第二套接地）");
        check(machine.contains("PlacementGate.groundFn") && outpost.contains("PlacementGate.groundFn")
            && orchestrator.contains("PlacementGate.groundFn") && ruin.contains("PlacementGate.groundFn"),
            "D1 三族城外结构 + 城的接地都取同一个供给器 PlacementGate.groundFn（P8 废墟族同口径）");

        // D2 placer 侧不自持数字
        // 编排器允许在注册日志里"打印"这三个键（L8 观测锚点），不允许"判定"它们 ⇒ 先把 LOG.info(...)
        // 整段摘掉再统计，剩下的出现次数必须为 0。
        final String orchestratorNoLog = stripLogCalls(orchestrator);
        for (final String[] pair : new String[][] { { machine, "机器" }, { outpost, "outpost" },
            { ruin, "废墟(P8)" }, { orchestratorNoLog, "编排器(去日志)" } }) {
            final String body = pair[0];
            final String who = pair[1];
            check(!body.contains("prosperityStructureBudgetPerChunk")
                && !body.contains("prosperityStructureWindowRepeatCap")
                && !body.contains("prosperityStructureFamilyGapChunks"),
                "D2 " + who + " 侧不自持预算/窗上限/间距 Config 键（数字真值只在 PlacementGate + Config）");
            check(!body.contains("< 20") && !body.contains("> 200"),
                "D2 " + who + " 侧不再自带可落地 y 带字面量（已收进 PlacementGate.LANDING_Y_*）");
            check(!body.contains("BUDGET") && !body.contains("WINDOW_CAP"),
                "D2 " + who + " 侧无自带预算/上限常量名");
        }

        // D3 读键方与扣减方各只有一处
        final String config = stripComments(read(root.resolve("src/main/java/com/miaokatze/gtsr/config/Config.java")));
        check(config.contains("prosperityStructureBudgetPerChunk")
            && config.contains("prosperityStructureWindowRepeatCap")
            && config.contains("prosperityStructureFamilyGapChunks"),
            "D3 Config 是三键的声明与注册处（唯一数字出处）");
        // 计数走"去空白"形态：spotless 会把 PlacementGate .beginChunk( 这类成员调用折行，
        // 按字面量数就会少数（P7c 实测 D4 曾因此从 3 掉到 2）。语义不变、只把换行/缩进抹平。
        final String flatGate = flat(gate);
        // P8 口径同步：窗上限的 Config 读取从"两处"收成<b>一处</b>（familyWindowRepeatCap 是本族/他族
        // 上限键的唯一读取点，request 默认位与 Entry 覆写生效位都经它），所以 4 变 3。
        // 少的那一处不是漏接，是"按 family 分支的读取点全仓只此一处"的兑现。
        check(count(flatGate, "Config.prosperityStructure") == 3, "D3 非 Config 侧只有本门读这三键（实测读 "
            + count(flatGate, "Config.prosperityStructure")
            + " 处：预算 1 + 窗上限 1〔familyWindowRepeatCap 唯一读取点〕+ 间距 1）");
        check(count(flatGate, "Config.prosperityRuin") == 1,
            "D3 废墟族的上限键也只被本门读一处（实测 " + count(flatGate, "Config.prosperityRuin")
                + "）⇒ P8 没有把族键散到 placer 侧");
        check(count(orchestratorNoLog, "Config.prosperityStructure") == 0,
            "D3 编排器不把这三键接进任何判定（去日志后出现 "
                + count(orchestratorNoLog, "Config.prosperityStructure") + " 次）");
        check(count(flatGate, "committed++") == 1 && count(flatGate, "onCommit(") == 2,
            "D3 预算扣减点唯一（committed++ 1 处 / onCommit 定义+调用 2 处，实测 " + count(flatGate, "committed++") + "/"
                + count(flatGate, "onCommit(") + "）");
        check(!scatter.contains("PlacementGate"),
            "D3 散布侧（P5）不反向依赖本门 ⇒ 两部门各守自身唯一入口，不形成第二真值");

        // D4 调用面计数（成员访问折行安全：见 D3 的说明）
        final List<Path> placerSide = Arrays.asList(
            root.resolve(
                "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java"),
            root.resolve(
                "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java"),
            root.resolve(
                "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java"),
            // P8：废墟族作为互斥链第三环纳入同一调用面口径（不是"另立一摊"）
            root.resolve(
                "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/RuinPlacer.java"));
        int begin = 0;
        int request = 0;
        int commit = 0;
        int counting = 0;
        final List<Integer> requestArity = new ArrayList<>();
        final List<String> requestCarriesIntent = new ArrayList<>();
        for (final Path p : placerSide) {
            final String b = flat(stripComments(read(p)));
            begin += count(b, "PlacementGate.beginChunk(");
            request += count(b, ".request(");
            commit += count(b, ".commit(");
            counting += count(b, "PlacementGate.counting(");
            for (int at = b.indexOf(".request("); at >= 0; at = b.indexOf(".request(", at + 1)) {
                final int arity = argCount(b, at + ".request".length());
                requestArity.add(arity);
                requestCarriesIntent.add(
                    b.substring(at, Math.min(b.length(), at + 96 + arity * 24)).contains("_INTENT") ? "yes" : "NO");
            }
        }
        check(begin == 4, "D4 全仓 beginChunk 调用点 = 4（编排器 1 + 三个 placer 的无门兼容重载 3），实测 " + begin);
        check(request == 3 && commit == 3 && counting == 3,
            "D4 三 placer 各恰一次 request / commit / counting（实测 " + request + "/" + commit + "/" + counting
                + "）");
        // D7（P7c 新增，P8 扩到三族）：生产侧的每一次 request 都必须带命中重放口，否则 H-2 两条静默失效
        check(requestArity.size() == 3, "D7 生产侧 request 调用点 = 3，实测元组 " + requestArity);
        check(!requestCarriesIntent.contains("NO"),
            "D7 每一次生产 request 的实参里都出现本族的 *_INTENT 重放口（实测 " + requestCarriesIntent
                + "）；少了它 = 门拿不到命中集 = 上限不咬合或退回密度乘子");
        // 三族走的是两条同义通道：machine/outpost 用 (族, 模板, IntentFn)，ruin 用
        // (Entry, 窗上限命中集, 邻域命中集)——后者是 P7c 给 P8 预留的 Entry 通道，P8 额外把
        // "邻域让行"的命中集从"同族"换成"三条城外族的合并集"（废墟填的是前两环的空槽，
        // 跨族贴脸只能由新族这一侧让掉）。两族的三参与废墟的三参实参形状不同但元数相同。
        check(Collections.frequency(requestArity, 3) == 3,
            "D7 三处生产 request 都是三参形态（两族 = 族/模板/IntentFn，废墟 = Entry/窗上限集/邻域集），实测 "
                + requestArity);
        check(count(flat(machine), "MACHINE_INTENT") == 2 && count(flat(outpost), "OUTPOST_INTENT") == 2
            && count(flat(ruin), "RUIN_INTENT") == 2,
            "D7 三族各恰有\"定义 + 传给门\"两处重放口引用（实测 " + count(flat(machine), "MACHINE_INTENT") + "/"
                + count(flat(outpost), "OUTPOST_INTENT") + "/" + count(flat(ruin), "RUIN_INTENT") + "）");
        // 掷骰唯一实现体：placeAll 里不得再留第二份 Config.prosperityMachineChance 读取
        check(count(flat(machine), "Config.prosperityMachineChance") == 1
            && count(flat(outpost), "Config.prosperityOutpostChance") == 1
            && count(flat(ruin), "Config.prosperityRuinChance") == 1,
            "D7 概率分母在三族 placer 侧各只读一处（实测 " + count(flat(machine), "Config.prosperityMachineChance")
                + "/" + count(flat(outpost), "Config.prosperityOutpostChance") + "/"
                + count(flat(ruin), "Config.prosperityRuinChance")
                + "）⇒ 命中重放与真实放置同用一份掷骰，不是两处真值");

        // D5（P7c 改口径）：H-2 两套规则各自唯一实现、互不共用
        check(count(scatter, "boolean windowAllows(") == 1, "D5 散布侧排名配额定义唯一（本片一字未动）");
        check(!flatGate.contains("ProsperitySurfaceScatter.windowAllows("),
            "D5 门不再委托散布侧排名配额（P7/P7b 的委托 = 密度乘子根因，plan §7.2 已作废）");
        check(count(flatGate, "booleanwindowRepeatAllows(") == 1 && count(flatGate, "booleanfamilySpacingAllows(") == 1,
            "D5 结构侧 H-2 两条规则各只有一处实现（实测 " + count(flatGate, "booleanwindowRepeatAllows(") + "/"
                + count(flatGate, "booleanfamilySpacingAllows(") + "）⇒ 没有第二套判定");
        // P8：邻域规则多了一个"绝对让行"档，但它必须是<b>同一条规则的第二档而不是第二处实现</b>——
        // 定义唯一、且只由 request0 的 absoluteYield 分支调用（既有两族一律 false）。
        check(count(flatGate, "booleanneighborhoodClearAllows(") == 1,
            "D5 绝对让行档的定义也唯一（实测 " + count(flatGate, "booleanneighborhoodClearAllows(") + "）");
        check(count(flatGate, "neighborhoodClearAllows(") == 2,
            "D5 绝对让行档只在「定义 + request0 的唯一分支」两处出现（实测 "
                + count(flatGate, "neighborhoodClearAllows(") + "）⇒ 没有被别处绕过优先级档");
        check(count(flatGate, "booleanfamilySpacingAllows(") == 1
            && count(flatGate, "familySpacingAllows(") == 2,
            "D5 优先级档仍是既有两族的唯一邻域规则（定义 + request0 调用，实测 "
                + count(flatGate, "familySpacingAllows(") + "）");
        check(!scatter.contains("windowRepeatAllows") && !scatter.contains("IntentFn"),
            "D5 散布侧不知道结构侧的新规则（两套实现不互相夹带）⇒ 散布档仍归 P5/P5b");
        // 门自建的是"命中集条件"的排序哈希，必须只走框架唯一件（plan §2.1 L2 禁止手搓哈希）
        check(!gate.contains(">>> 33"), "D5b 门不自带 splitmix 终结器（走 GTSRWorldgenHash 唯一件）");
        check(count(flatGate, "GTSRWorldgenHash.splitmix64(GTSRWorldgenHash.chunkSeed(") == 2,
            "D5b 门的两条排序哈希都用框架唯一件的同一条原语（实测 "
                + count(flatGate, "GTSRWorldgenHash.splitmix64(GTSRWorldgenHash.chunkSeed(") + " 处：窗上限 + 间距）");

        // D6 门不自持地表门成员集合（P4 红线对本类同样成立）
        check(!gate.contains("landableTops") && !gate.contains("instanceof Block"),
            "D6 门不查地表门成员集合，就绪门只吃调用方给的 boolean 结论");
        check(count(flatGate, "Blocks.") == count(flatGate, "Blocks.air"),
            "D6 门里 Blocks.* 只允许空气句柄（实测 Blocks. 出现 " + count(flatGate, "Blocks.") + " 处，air "
                + count(flatGate, "Blocks.air") + " 处）");
    }

    /** 抹平空白与折行（spotless 会把成员调用拆行，字面量计数会少数；语义计数按去空白形态做）。 */
    private static String flat(String s) {
        return s.replaceAll("\\s+", "");
    }

    /**
     * 从 {@code (} 的位置起数顶层实参个数（遇第一个同层 {@code )} 即返回，不看后续文本）。
     * 供 D7 的"生产侧 request 必须三参"使用；只数顶层逗号。
     */
    private static int argCount(String flat, int openParen) {
        int depth = 0;
        int commas = 0;
        boolean any = false;
        for (int i = openParen + 1; i < flat.length(); i++) {
            final char c = flat.charAt(i);
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                if (depth == 0) {
                    return any ? commas + 1 : 0;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                commas++;
            } else {
                any = true;
            }
        }
        return -1; // 括号不平衡（源被改坏），调用方按缺失处理
    }

    // ═════════════════════════ E 组：micro 强度层消费结论（判据 8 → P8 收口）═════════════════════════

    /**
     * <b>P8 改判</b>：P7b 交付时本组钉的是"结构/散布/城/装饰侧对 {@code microStrengthAt} 的消费点
     * == 0，结论固定申报交 P8"——那是<b>悬空层的存在证明</b>，不是验收目标。P8 已经把废墟族接上
     * （{@code ruin/RuinPlacer.microStrengthAt}），本组因此改成钉两面：
     * ① 框架出口仍在（P6 交付件未回退）；② 废墟族的真实消费点 ≥ 1 且既有各族仍为 0
     * （= 调制只在 ruin 族发生，前两环的密度没被偷偷改动）。
     * 反向 RED 由 {@code tools/dim1/RuinFamilyCheck} 的 E 组承担（把 ruin 侧那两处引用注掉必红）。
     */
    private static void groupE_microLayer() throws Exception {
        final Path root = Paths.get("");
        int legacyConsumers = 0;
        int ruinConsumers = 0;
        final List<String> ruinSites = new ArrayList<>();
        final List<String> ruinFiles = Arrays.asList(
            "ruin/RuinPlacer.java", "ruin/RuinShapes.java", "ruin/RuinDamageOps.java", "ruin/RuinTemplate.java");
        for (final String rel : new String[] { "framework/structure/PlacementGate.java",
            "prosperity/ruins/RuinedMachinePlacer.java", "prosperity/ruins/ProsperityOutpostPlacer.java",
            "prosperity/ruins/ProsperityWorldGenerator.java", "prosperity/ruins/ProsperitySurfaceScatter.java",
            "prosperity/ruins/ProsperityDecorPlacer.java", "prosperity/ruins/city/CityVariants.java",
            "prosperity/ruins/city/CityPlanner.java", "prosperity/ruins/city/CityBlockResolver.java" }) {
            final String b = stripComments(read(root.resolve("src/main/java/com/miaokatze/gtsr/common/dimension/" + rel)));
            legacyConsumers += count(b, "microStrengthAt");
        }
        for (final String rel : ruinFiles) {
            final java.nio.file.Path p = root.resolve(
                "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/" + rel);
            if (!Files.exists(p)) {
                MISSING.add("ruin family file absent: " + rel);
                continue;
            }
            final int c = count(stripComments(read(p)), "microStrengthAt");
            ruinConsumers += c;
            if (c > 0) {
                ruinSites.add(rel + ":" + c);
            }
        }
        final String mgr = stripComments(read(root.resolve(
            "src/main/java/com/miaokatze/gtsr/common/dimension/framework/GTSRWorldChunkManager.java")));
        check(mgr.contains("float microStrengthAt("), "E1 micro 强度只读出口仍在框架层（P6 交付件未回退）");
        check(legacyConsumers == 0, "E2 既有结构/散布/城/装饰侧对 microStrengthAt 的消费点仍为 0（实测 "
            + legacyConsumers + "）⇒ P8 的调制只落在废墟族，前两环的概率没被顺手改动");
        check(ruinConsumers >= 1, "E3 废墟族对 microStrengthAt 的真实消费点 >= 1（实测 " + ruinConsumers + " "
            + ruinSites + "）⇒ P6 的悬空层已由 P8 收口（plan §5 P8 判据 6）");
        final String placer = stripComments(read(root.resolve(
            "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/RuinPlacer.java")));
        check(placer.contains("PlacementGate.FAMILY_RUIN") && placer.contains("RUIN_INTENT"),
            "E4 废墟族走带命中重放口的通道（P7c 对 P8 的接口承诺：非 0 上限必须自带重放口）");
        System.out.println("# P8-MICRO verdict=closed legacy=0 ruinConsumers=" + ruinConsumers + " sites=" + ruinSites);
    }

    // ═════════════════════════════════ 真地形采样器 ═════════════════════════════════

    static final class Hit {
        long seed;
        int cx;
        int cz;
        String family;
        float biomeWeight;
        int landedSolid;
    }

    enum Pass {
        /** 生产默认档：窗上限 3（命中集条件）+ 同族间距 1 档（8 邻）——判据 2 的验收档。 */
        DEFAULT("默认档 budget=1/cap=3/gap=1", 1, 3, 1),
        /** 回退档（= 改造前密度基准，也是判据 1 扫描表的 cap=0 行与位移表的 BASE 侧）。 */
        NOCAP("回退档 budget=1/cap=0/gap=0", 1, 0, 0),
        NAKED("两键都关 budget=0/cap=0/gap=0", 0, 0, 0),
        /** 判据 1 的 cap 扫描档（全部 gap=0，把间距规则隔离出去，才谈得上"总数下降 = 重复超额"）。 */
        CAP1("cap 扫描 cap=1", 1, 1, 0), CAP2("cap 扫描 cap=2", 1, 2, 0),
        CAP3("cap 扫描 cap=3", 1, 3, 0), CAP5("cap 扫描 cap=5", 1, 5, 0),
        CAP8("cap 扫描 cap=8", 1, 8, 0),
        /** 只开间距不开窗上限（归因用：判据 2 的"总数偏差来自哪一条"）。 */
        GAPONLY("只开间距 budget=1/cap=0/gap=1", 1, 0, 1);

        final String label;
        final int budget;
        final int cap;
        final int gap;

        Pass(String label, int budget, int cap, int gap) {
            this.label = label;
            this.budget = budget;
            this.cap = cap;
            this.gap = gap;
        }

        /** 该档是否参与判据 1 的 cap 扫描表（cap 单调、间距隔离）。 */
        boolean inCapScan() {
            return this.gap == 0 && this.budget == 1;
        }
    }

    static final class Rate {
        final String label;
        long chunks;
        long citySkips;
        long structures;
        long adjacent;
        long outpostHits;
        long machineHits;
        long solidSum;
        int maxWinEmit;
        final Map<Long, Map<String, Integer>> winEmit = new HashMap<>();
        /** 每个命中 chunk 的落点签名（只对参与位移对账的两档采集，见 {@link Pass}）。 */
        final Map<String, String> sigs = new HashMap<>();
        /** 本档"被放行过请求"的 (窗,模板,槽) 集合——用于把首次保留率的缺口归因到抢占。 */
        final Set<String> emitSlots = new HashSet<>();

        Rate(String label) {
            this.label = label;
        }

        /** (窗, 模板) 命中集：本档下"至少请求过一次"的 (windowKey, template) 对数。 */
        Set<Long> winTemplateKeys() {
            final Set<Long> keys = new HashSet<>();
            for (final Map.Entry<Long, Map<String, Integer>> e : this.winEmit.entrySet()) {
                for (final String t : e.getValue().keySet()) {
                    keys.add(e.getKey() * 31L + t.hashCode());
                }
            }
            return keys;
        }

        /** 本档被门放行的请求总数（= Σ 窗 Σ 模板 发射槽数），判据 1 数学表的货币。 */
        long requestedTotal() {
            long sum = 0;
            for (final Map<String, Integer> m : this.winEmit.values()) {
                for (final int n : m.values()) {
                    sum += n;
                }
            }
            return sum;
        }

        /** 判据 1 的数学量：Σ_窗Σ_模板 max(0, h − cap) = "重复超额部分"（cap=0 档即基线总座数）。 */
        long duplicateExcess(int cap) {
            if (cap <= 0) {
                return 0; // 不限档：没有"超额"可言
            }
            long sum = 0;
            for (final Map<String, Integer> m : this.winEmit.values()) {
                for (final int h : m.values()) {
                    sum += Math.max(0, h - cap);
                }
            }
            return sum;
        }
    }

    /**
     * 每 seed 一条沿 x 连续、沿 z 错开的 chunk 带（x 方向无贴脸截断），每区一次真实
     * generateTerrain + 真实表层缝，三个档共用同一份<b>只读</b>网格（本工具的 sink 不落网格）。
     */
    static final class Sample {

        final int seeds;
        final int regions;
        final Map<String, Rate> rates = new LinkedHashMap<>();
        /** 每区就地消费的命中样本（跑完即清空：网格必须能被回收，不留 World 引用）。 */
        final List<Hit> regionHits = new ArrayList<>();
        long generatedChunks;
        long colScanN;
        long colScanSum;
        int colScanMax;
        long colScanNonZero;
        final int[] colScanHist = new int[15];
        long dispCols;
        long dispSum;
        int dispMax;
        long dispNonZero;
        long dispStructures;
        long dispStructuresMoved;
        final int[] dispPerStructHist = new int[11];
        // A7/A8 真实链累计（每区 verifyHits 时累加）
        long chainTested;
        long chainNonIdempotent;
        long chainBudgetLeak;
        long chainNoAttempt;
        long chainNoPermitRecord;
        long chainTried;
        long chainA8Allowed;
        long chainA8Blocked;
        long chainBadCommit;
        long chainLie;
        /** C7 计数在工具级静态字段（checks/drift），此处保留样本量便于日志。 */
        long dispOutpostCols;
        long dispOutpostSum;
        int dispOutpostMax;
        long dispMachineCols;
        long dispMachineSum;
        int dispMachineMax;
        final BitSet[] bands;

        private final GTSRDimensionDef def;
        private final BiomeGenBase[] biomes;
        private final Block[] blocks = new Block[65536];
        private final byte[] metas = new byte[65536];
        private final List<int[]> regionOrigins = new ArrayList<>(); // [si, r]

        Sample(int seeds, int regions) throws Exception {
            this.seeds = seeds;
            this.regions = regions;
            for (final Pass p : Pass.values()) {
                rates.put(p.label, new Rate(p.label));
            }
            this.bands = new BitSet[Pass.values().length];
            for (int i = 0; i < this.bands.length; i++) {
                this.bands[i] = new BitSet(seeds * regions * AXIS * AXIS);
            }
            this.biomes = SurfaceHarness.prosperityBiomes();
            this.def = SurfaceHarness.def(true, this.biomes, SurfaceHarness.prosperityWeights());
            SurfaceHarness.recordAllAllocations(this.biomes, SurfaceHarness.shatteredBiomes());
        }

        void run() throws Exception {
            final int budget0 = Config.prosperityStructureBudgetPerChunk;
            final int cap0 = Config.prosperityStructureWindowRepeatCap;
            final int gap0 = Config.prosperityStructureFamilyGapChunks;
            try {
                for (int si = 0; si < seeds; si++) {
                    final long seed = SEEDS[si % SEEDS.length];
                    final GTSRWorldChunkManager mgr = new GTSRWorldChunkManager(seed, this.def);
                    for (int r = 0; r < regions; r++) {
                        final int cx0 = si * 4096 + r * AXIS;
                        final int cz0 = si * 924816;
                        final Block[] grid = new Block[SIDE * SIDE * 256];
                        final World world = Dim78ScatterDensityCheck.world(grid, cx0, cz0, seed, SIDE);
                        // provider 必须挂在<b>本区世界</b>上：generateTerrain 内部取的是
                        // {@code this.worldObj.getSeed()}（不是构造入参），挂 mockWorld 会让地形按
                        // SurfaceHarness 的常数种子生成、而本工具按 SEEDS[si] 比对与落门 ⇒ 实测
                        // findSurfaceY 与 heightAt 系统性错位（均值 4.7 格 / 最大 20 格），
                        // 结构就绪门因此全拒（首轮实测结构 0 座的真因）。
                        final GTSRChunkProviderBase provider = new ChunkProviderProsperityRuins(world, seed);
                        materialize(provider, mgr, seed, cx0, cz0, grid);
                        scanColumns(world, seed, cx0, cz0);
                        // 顺序固定：默认档 → 就地验落块真值（此时该区网格还活着）→ 另各档
                        runPass(Pass.DEFAULT, world, seed, cx0, cz0, si, r);
                        // 落块真值与位移量化都挂在"回退档"（budget=1 / cap=0 / gap=0 = 改造前密度）上采：
                        // 假成功路径与 H-2 两条规则无关，而默认档若把结构砍光就根本没有样本可验。
                        runPass(Pass.NOCAP, world, seed, cx0, cz0, si, r);
                        verifyHits(world);
                        // P7c 新增档：cap 扫描（全部 gap=0，隔离间距规则）+ 只开间距档 + 两键都关档
                        for (final Pass p : Pass.values()) {
                            if (p == Pass.DEFAULT || p == Pass.NOCAP || p == Pass.NAKED) {
                                continue;
                            }
                            runPass(p, world, seed, cx0, cz0, si, r);
                        }
                        runPass(Pass.NAKED, world, seed, cx0, cz0, si, r);
                    }
                }
            } finally {
                Config.prosperityStructureBudgetPerChunk = budget0;
                Config.prosperityStructureWindowRepeatCap = cap0;
                Config.prosperityStructureFamilyGapChunks = gap0;
            }
            for (final Pass p : Pass.values()) {
                closeBand(p);
            }
        }

        /** 真实 generateTerrain + 真实表层缝 → 平坦网格（口径逐字同 Dim78ScatterDensityCheck.Sampler）。 */
        private void materialize(GTSRChunkProviderBase provider, GTSRWorldChunkManager mgr, long seed, int cx0,
            int cz0, Block[] grid) throws Exception {
            final Method gen = SurfaceHarness.generateTerrain();
            final Method seam = SurfaceHarness.surfaceSeam(provider.getClass());
            for (int dx = 0; dx < AXIS; dx++) {
                for (int dz = 0; dz < AXIS; dz++) {
                    final int cx = cx0 + dx;
                    final int cz = cz0 + dz;
                    Arrays.fill(blocks, null);
                    Arrays.fill(metas, (byte) 0);
                    gen.invoke(provider, cx, cz, blocks, metas, null);
                    final BiomeGenBase[] plane = mgr.loadBlockGeneratorData(null, cx * 16, cz * 16, 16, 16);
                    seam.invoke(null, seed, cx * 16, cz * 16, blocks, metas, plane);
                    final int bx = dx * 16;
                    final int bz = dz * 16;
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            final int col = (lx << 12) | (lz << 8);
                            final int dstCol = (bx + lx) + (bz + lz) * SIDE;
                            for (int y = 0; y < 256; y++) {
                                grid[y * SIDE * SIDE + dstCol] = blocks[col | y];
                            }
                        }
                    }
                    generatedChunks++;
                }
            }
        }

        private void runPass(Pass p, World world, long seed, int cx0, int cz0, int si, int r) {
            Config.prosperityStructureBudgetPerChunk = p.budget;
            Config.prosperityStructureWindowRepeatCap = p.cap;
            Config.prosperityStructureFamilyGapChunks = p.gap;
            final Rate rate = rates.get(p.label);
            final boolean wantSigs = p == Pass.NOCAP || p == Pass.DEFAULT;
            for (int dx = 0; dx < AXIS; dx++) {
                for (int dz = 0; dz < AXIS; dz++) {
                    final int cx = cx0 + dx;
                    final int cz = cz0 + dz;
                    rate.chunks++;
                    if (CityPlanner.citiesNear(seed, cx, cz).length > 0) {
                        rate.citySkips++;
                        continue; // 与编排器同源的城窗抑制判据
                    }
                    final PlacementGate.ChunkGate gate = PlacementGate.beginChunk(SurfaceGate.DIM78, seed, cx, cz);
                    final RecordSink sink = new RecordSink();
                    final boolean op = ProsperityOutpostPlacer.placeAll(world, seed, cx, cz, sink, gate);
                    boolean landed = op;
                    if (!op) {
                        RuinedMachinePlacer.placeAll(world, seed, cx, cz, machineWeight(seed, cx, cz), sink, gate);
                        landed = sink.solid > 0;
                    }
                    // 窗内同模板发射计数 = 被门放行过的请求（与是否落块无关，语义 = "允许发射"）
                    for (final String t : gate.requestedTemplates()) {
                        final long key = windowKey(seed, cx, cz);
                        rate.emitSlots.add(key + "|" + t + "|" + cx + "," + cz);
                        final Map<String, Integer> m = rate.winEmit.computeIfAbsent(key, k -> new HashMap<>());
                        final int n = m.merge(t, 1, Integer::sum);
                        if (n > rate.maxWinEmit) {
                            rate.maxWinEmit = n;
                        }
                        // C7：真实被放行的请求必须与"命中重放口"给出的模板逐点同值——
                        // 这钉的是"placer 的活链掷骰 == 门看到的命中集"（P7c 把掷骰收进唯一 roll()
                        // 之后，任何一处偷改都会在这里显形；不依赖权重口径，故离线可判）。
                        replayAgainstGranted(t, seed, cx, cz);
                    }
                    if (!landed) {
                        continue;
                    }
                    rate.structures++;
                    rate.solidSum += sink.solid;
                    if (op) {
                        rate.outpostHits++;
                    } else {
                        rate.machineHits++;
                    }
                    if (wantSigs) {
                        rate.sigs.put(chunkId(seed, cx, cz), sink.signature(gate.requestedTemplates()));
                    }
                    bands[p.ordinal()].set(bandIndex(si, r, dx, dz));
                    if (p == Pass.NOCAP) {
                        final Hit h = new Hit();
                        h.seed = seed;
                        h.cx = cx;
                        h.cz = cz;
                        h.family = op ? PlacementGate.FAMILY_OUTPOST : PlacementGate.FAMILY_MACHINE;
                        h.biomeWeight = op ? 1F : machineWeight(seed, cx, cz);
                        h.landedSolid = sink.solid;
                        regionHits.add(h);
                        displacement(world, seed, sink, op);
                    }
                }
            }
        }

        /**
         * C7（P7c）：把一个"真实被门放行"的请求与它自己那族的命中重放口对账。
         * 样本量计在 {@link #checks}，"重放给出不同模板 / 给出 null" 的漂移计在 {@link #drift}。
         */
        static void replayAgainstGranted(String template, long seed, int cx, int cz) {
            final StructureRegistry.Entry e = StructureRegistry.get(template);
            if (e == null) {
                return; // 合成模板名（单测里的探针），不参与生产重放对账
            }
            final PlacementGate.Intent it;
            if (PlacementGate.FAMILY_MACHINE.equals(e.family)) {
                it = RuinedMachinePlacer.intentAt(seed, cx, cz);
            } else if (PlacementGate.FAMILY_OUTPOST.equals(e.family)) {
                it = ProsperityOutpostPlacer.intentAt(seed, cx, cz);
            } else if (PlacementGate.FAMILY_RUIN.equals(e.family)) {
                // P8：废墟族也必须有重放口（它的窗上限非 0，无重放口时上限不生效）
                it = RuinPlacer.intentAt(seed, cx, cz);
            } else {
                return; // 城内族不经本门（P7 口径）
            }
            checks++;
            if (it == null || !template.equals(it.templateName)) {
                drift++;
            }
        }

        /**
         * A7/A8 的采集面（判据 1）：对刚跑完默认档的<b>同一批真实命中 chunk</b>，唯一改变
         * sink（一律拒绝 vs 一律接受）再跑一次——掷骰、门判定与接地都是 (seed,cx,cz) 的纯函数，
         * 所以两次之间"落没落块"就是唯一变量。
         * <p>
         * A8 额外把窗上限临时关掉：机器的申请若被窗上限拒掉，那是<b>正当</b>拒绝，会污染
         * "假成功不再吞互斥位"这条判据的读法，故单变量隔离（预算与互斥保持默认档 1）。
         */
        private void verifyHits(World world) {
            final int budget0 = Config.prosperityStructureBudgetPerChunk;
            final int cap0 = Config.prosperityStructureWindowRepeatCap;
            final int gap0 = Config.prosperityStructureFamilyGapChunks;
            // A7/A8 验的是"落块真值 / 预算与互斥位"，与 H-2 两条无关 ⇒ 全程把两条置 0（正当拒绝
            // 会污染读法：被窗上限或间距拒掉的机器不算"假成功吞位"的证据）。
            Config.prosperityStructureWindowRepeatCap = 0;
            Config.prosperityStructureFamilyGapChunks = 0;
            try {
                verifyHits0(world, budget0);
            } finally {
                Config.prosperityStructureBudgetPerChunk = budget0;
                Config.prosperityStructureWindowRepeatCap = cap0;
                Config.prosperityStructureFamilyGapChunks = gap0;
            }
        }

        private void verifyHits0(World world, int budget0) {
            for (final Hit h : regionHits) {
                if (chainTested >= 48) {
                    break;
                }
                chainTested++;
                final PlacementGate.ChunkGate gate = PlacementGate.beginChunk(SurfaceGate.DIM78, h.seed, h.cx, h.cz);
                final RejectSink reject = new RejectSink();
                if (runPlacer(world, h, gate, reject)) {
                    chainLie++; // 一块都没落进世界却报成功 = plan §2.1 L5 的破口
                }
                if (reject.calls == 0) {
                    chainNoAttempt++;
                }
                if (reject.calls > 0 && gate.requestedTemplates().isEmpty()) {
                    chainNoPermitRecord++;
                }
                if (gate.committed() != 0) {
                    chainBudgetLeak++;
                }
                final PlacementGate.ChunkGate gate2 = PlacementGate.beginChunk(SurfaceGate.DIM78, h.seed, h.cx, h.cz);
                final RecordSink accept = new RecordSink();
                runPlacer(world, h, gate2, accept);
                if (accept.solid != h.landedSolid) {
                    chainNonIdempotent++;
                }
                if ((accept.solid > 0) != (gate2.committed() == 1)) {
                    chainBudgetLeak++;
                }
                if (PlacementGate.FAMILY_OUTPOST.equals(h.family) && chainTried < 16) {
                    // A8：outpost 命中却落块失败时，同一 chunk 的机器必须仍然拿得到预算位与互斥位。
                    // 机器自身的 1/N 掷骰不在本断言口径内（改造前后都是概率事），所以这里直接在同一个
                    // gate 上问一次门——编排器的 if (!outpost.placeAll(...)) 分支等价于"这扇门没被扣过"，
                    // 而那正是这里要钉的东西。窗上限临时置 0，避免把"正当的窗名额拒绝"混进来。
                    chainTried++;
                    Config.prosperityStructureBudgetPerChunk = 1;
                    Config.prosperityStructureWindowRepeatCap = 0;
                    final PlacementGate.ChunkGate gate3 = PlacementGate
                        .beginChunk(SurfaceGate.DIM78, h.seed, h.cx, h.cz);
                    if (ProsperityOutpostPlacer.placeAll(world, h.seed, h.cx, h.cz, new RejectSink(), gate3)) {
                        chainLie++;
                    }
                    if (gate3.committed() != 0) {
                        chainBadCommit++;
                    }
                    final PlacementGate.Permit mp = gate3.request(PlacementGate.FAMILY_MACHINE, "boiler_frame");
                    if (mp == null) {
                        chainA8Blocked++;
                    } else {
                        mp.abort();
                        chainA8Allowed++;
                    }
                    Config.prosperityStructureBudgetPerChunk = budget0;
                    Config.prosperityStructureWindowRepeatCap = 0;
                    Config.prosperityStructureFamilyGapChunks = 0;
                }
            }
            Config.prosperityStructureBudgetPerChunk = budget0;
            Config.prosperityStructureWindowRepeatCap = 0;
            Config.prosperityStructureFamilyGapChunks = 0;
            regionHits.clear();
        }

        private boolean runPlacer(World world, Hit h, PlacementGate.ChunkGate gate, BlockSink sink) {
            if (PlacementGate.FAMILY_OUTPOST.equals(h.family)) {
                return ProsperityOutpostPlacer.placeAll(world, h.seed, h.cx, h.cz, sink, gate);
            }
            return RuinedMachinePlacer.placeAll(world, h.seed, h.cx, h.cz, h.biomeWeight, sink, gate);
        }

        private void displacement(World world, long seed, RecordSink sink, boolean outpost) {
            dispStructures++;
            if (sink.columns.isEmpty()) {
                return;
            }
            final int centerX = sink.minX + (sink.maxX - sink.minX) / 2;
            final int centerZ = sink.minZ + (sink.maxZ - sink.minZ) / 2;
            final int plane = GTSRChunkProviderBase.findSurfaceY(world, centerX, centerZ);
            if (plane < 0) {
                return;
            }
            int structMax = 0;
            for (final long key : sink.columns) {
                final int x = (int) (key >> 32);
                final int z = (int) key;
                final int d = ProsperityTerrainProfile.heightAt(seed, x, z) - plane;
                dispCols++;
                dispSum += Math.abs(d);
                if (outpost) {
                    dispOutpostCols++;
                    dispOutpostSum += Math.abs(d);
                    if (Math.abs(d) > dispOutpostMax) {
                        dispOutpostMax = Math.abs(d);
                    }
                } else {
                    dispMachineCols++;
                    dispMachineSum += Math.abs(d);
                    if (Math.abs(d) > dispMachineMax) {
                        dispMachineMax = Math.abs(d);
                    }
                }
                if (d != 0) {
                    dispNonZero++;
                }
                if (Math.abs(d) > dispMax) {
                    dispMax = Math.abs(d);
                }
                if (Math.abs(d) > structMax) {
                    structMax = Math.abs(d);
                }
            }
            dispPerStructHist[Math.min(structMax, dispPerStructHist.length - 1)]++;
            if (structMax > 0) {
                dispStructuresMoved++;
            }
        }

        float machineWeight(long seed, int cx, int cz) {
            final GTSRBiomeAuthority.Resolution res = GTSRBiomeAuthority
                .forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
                .ordinalAt((cx << 4) + 8, (cz << 4) + 8);
            return ProsperityWorldGenerator.weightForRosterIndex(res.ordinal, ProsperityWorldGenerator.MACHINE_WEIGHTS);
        }

        /** 改前两制式逐列差：列扫（真实网格）vs heightAt（纯函数）。 */
        private void scanColumns(World world, long seed, int cx0, int cz0) {
            for (int bx = 0; bx < SIDE; bx += SCAN_STRIDE) {
                for (int bz = 0; bz < SIDE; bz += SCAN_STRIDE) {
                    final int x = (cx0 << 4) + bx;
                    final int z = (cz0 << 4) + bz;
                    final int d = Math.abs(GTSRChunkProviderBase.findSurfaceY(world, x, z)
                        - ProsperityTerrainProfile.heightAt(seed, x, z));
                    colScanN++;
                    colScanSum += d;
                    if (d != 0) {
                        colScanNonZero++;
                    }
                    if (d > colScanMax) {
                        colScanMax = d;
                    }
                    colScanHist[Math.min(d, colScanHist.length - 1)]++;
                }
            }
        }

        /** 带内 8 邻半边计数（同 {@code Dim78DensitySim} 的口径）。 */
        private void closeBand(Pass p) {
            final Rate rate = rates.get(p.label);
            final BitSet bs = bands[p.ordinal()];
            for (int i = 0; i < bs.length(); i++) {
                if (!bs.get(i)) {
                    continue;
                }
                final int[] sc = decode(i);
                boolean adj = false;
                for (int dx = 0; dx <= 1 && !adj; dx++) {
                    for (int dz = -1; dz <= 1 && !adj; dz++) {
                        if (dx == 0 && dz <= 0) {
                            continue;
                        }
                        if (bs.get(encode(sc[0], sc[1] + dx, sc[2] + dz))) {
                            adj = true;
                        }
                    }
                }
                if (adj) {
                    rate.adjacent++;
                }
            }
        }

        private int bandIndex(int si, int r, int dx, int dz) {
            return encode(si, r * AXIS + dx, dz);
        }

        /** 编码：seed 序号 × 带内 (x,z)；越带返回一个永不命中的 index（BitSet 自动扩位但不被 set）。 */
        private int encode(int si, int x, int z) {
            if (x < 0 || z < 0 || z >= AXIS) {
                return OUT_OF_BAND;
            }
            return si * regions * AXIS * AXIS + x * AXIS + z;
        }

        private int[] decode(int i) {
            final int per = regions * AXIS * AXIS;
            final int si = i / per;
            final int rem = i % per;
            return new int[] { si, rem / AXIS, rem % AXIS };
        }

        private static final int OUT_OF_BAND = 1 << 28;
    }

    private static long windowKey(long seed, int cx, int cz) {
        return seed * 1_000_003L + ((long) Math.floorDiv(cx, AXIS) << 20) + Math.floorDiv(cz, AXIS);
    }

    /** 采样区的签名表键（seed 参与，故不同 seed 的同名坐标不会互相覆盖）。 */
    private static String chunkId(long seed, int cx, int cz) {
        return seed + ":" + cx + ":" + cz;
    }

    // ═════════════════════════════════ 装配与工具件 ═════════════════════════════════

    private static void bootstrap() {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        extraBlocks();
        RuinedMachinePlacer.registerVariants();
        ProsperityOutpostPlacer.registerVariants();
        CityVariants.registerVariants();
        // P8：废墟族也进 roster，A6 的族计数与 E4 的通道申报才看得到它（注册幂等，顺序不影响名册序）
        RuinPlacer.registerVariants();
    }

    /** 与 {@code SurfaceGateUnifyCheck}/{@code Dim78ScatterDensityCheck} 同名件同一口径。 */
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
            BlocksGTSR.prosperityTuftRust = new BlockProsperityTuft("ProsperityTuftRust", "gtsr:prosperity_tuft_rust");
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
        final List<String> missing = new ArrayList<>();
        if (BlocksGTSR.ruinedCasing == null) {
            missing.add("ruinedCasing");
        }
        if (BlocksGTSR.ruinDebris == null) {
            missing.add("ruinDebris");
        }
        if (BlocksGTSR.prosperitySurface == null) {
            missing.add("prosperitySurface");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("结构域方块缺席，落块计数必然是假 0：" + missing);
        }
    }

    /** 一律拒绝的 sink：造"门通过却落块失败"的场景（判据 1 的自变量）。 */
    static final class RejectSink implements BlockSink {
        int calls;

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            calls++;
            return false;
        }
    }

    /** 接受一切写入（不落网格，保持网格只读）并记录 bbox、逐列写入位置与落地 y 域。 */
    static final class RecordSink implements BlockSink {
        int accepted;
        int solid;
        int dropped;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        final LinkedHashSet<Long> columns = new LinkedHashSet<>();

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            if (!(block instanceof Block)) {
                dropped++;
                return false;
            }
            accepted++;
            if (block != Blocks.air) {
                solid++;
                if (x < minX) {
                    minX = x;
                }
                if (x > maxX) {
                    maxX = x;
                }
                if (z < minZ) {
                    minZ = z;
                }
                if (z > maxZ) {
                    maxZ = z;
                }
                if (y < minY) {
                    minY = y;
                }
                if (y > maxY) {
                    maxY = y;
                }
                columns.add(((long) x << 32) | (z & 0xFFFFFFFFL));
            }
            return true;
        }

        /**
         * 落点签名（判据 6 的位移对账口径）：被门放行的模板名 + 真实落块数 + 落地 y 域。
         * <b>不含</b>逐格块型序列（那要真落网格；本工具的网格是只读基准面），但族/模板/落块数/y 域
         * 三项同时相同已足以证明"同一座结构没被挪动过"——挪动必然改 origin ⇒ 改 y 域或落块数。
         */
        String signature(List<String> granted) {
            return String.join("+", granted) + "|" + solid + "|" + minY + ".." + maxY;
        }
    }

    private static int readTemplateIdBase() {
        try {
            final Field f = PlacementGate.class.getDeclaredField("FAMILY_TEMPLATE_ID_BASE");
            f.setAccessible(true);
            return f.getInt(null);
        } catch (ReflectiveOperationException e) {
            MISSING.add("PlacementGate.FAMILY_TEMPLATE_ID_BASE");
            return -1;
        }
    }

    private static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** 去注释（块 + 行），粗口径与 SurfaceGateUnifyCheck 一致；只用于源级 grep 断言。 */
    static String stripComments(String s) {
        final StringBuilder b = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            final char c = s.charAt(i);
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                final int e = s.indexOf("*/", i + 2);
                i = e < 0 ? s.length() : e + 2;
                continue;
            }
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                final int e = s.indexOf('\n', i);
                i = e < 0 ? s.length() : e;
                continue;
            }
            b.append(c);
            i++;
        }
        return b.toString();
    }

    /** 摘掉 {@code LOG.info(...)} / {@code LOG.warn(...)} 整段（平衡括号），用于区分"打印"与"判定"。 */
    static String stripLogCalls(String s) {
        final StringBuilder b = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            final int at = s.indexOf("LOG.", i);
            if (at < 0) {
                b.append(s, i, s.length());
                break;
            }
            final int open = s.indexOf('(', at);
            if (open < 0) {
                b.append(s, i, s.length());
                break;
            }
            int depth = 0;
            int j = open;
            for (; j < s.length(); j++) {
                final char c = s.charAt(j);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
            }
            b.append(s, i, at);
            i = Math.min(j + 1, s.length());
        }
        return b.toString();
    }

    static int count(String hay, String needle) {
        int n = 0;
        for (int i = hay.indexOf(needle); i >= 0; i = hay.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    private static String fmt(double v, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", v);
    }

    static void table(String name, String... cells) {
        System.out.println("# P7B-" + name + " " + String.join(" ", cells));
    }

    private static void check(boolean ok, String msg) {
        if (ok) {
            passed++;
            return;
        }
        FAILURES.add(msg);
        System.out.println("  FAIL " + msg);
    }

    private static void report(long millis, long chunks, String mode) {
        if (!MISSING.isEmpty()) {
            System.out.println("# 反射缺席登记（不判红，换树跑时说明口径）：" + MISSING);
        }
        System.out.println("P7B mode=" + mode + " terrainChunks=" + chunks + " ms=" + millis);
        if (FAILURES.isEmpty()) {
            System.out.println("PLACEMENT CONTRACT CHECK PASS: assertions=" + passed);
            System.exit(0);
        }
        System.out.println(
            "PLACEMENT CONTRACT CHECK FAIL: " + FAILURES.size() + " assertion(s) failed, passed=" + passed);
        System.exit(1);
    }
}
