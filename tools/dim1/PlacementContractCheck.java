import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * {@code plan/investigation/v12030-hotfix-replaceruntime-report.md} §3，一键入口
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
    static final int DECL_WINDOW_CAP = 2;
    static final int DECL_LANDING_Y_MIN = 20;
    static final int DECL_LANDING_Y_MAX = 200;
    static final int DECL_TEMPLATE_ID_BASE = 1000;

    /** 与 P4/P5/P6 同一 8 seed 集（{@code Dim78ScatterDensityCheck.SEEDS} 值逐字照抄）。 */
    static final long[] SEEDS = { 0x503441L, 0x503442L, 0x503443L, 0x503444L, 0x503445L, 0x503446L, 0x503447L,
        0x503448L };

    /** 接地逐点比对时每 4 列取 1 列（判据 2 的规模按 chunk 计，256 列全取会让采样时间翻倍）。 */
    static final int GROUND_COL_STRIDE = 4;
    /** 改前两套制式逐列差的扫描步长（方块列）。 */
    static final int SCAN_STRIDE = 4;

    static final List<String> FAILURES = new ArrayList<>();
    static final List<String> MISSING = new ArrayList<>();
    static int passed;

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
        final Sample sample = new Sample(seeds, regions);
        sample.run();
        groupA_chain(sample);
        groupB_columns(sample);
        groupC_rate(sample);
        groupF_displacement(sample);
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

        // A2/A3 只验预算与互斥，故把窗上限临时置 0（回退位）——否则 (seed,3,5) 这一格大概率
        // 根本拿不到窗名额，测的就不是预算语义了（实测第一次 request 直接返回 null）。
        final int capForUnit = Config.prosperityStructureWindowRepeatCap;
        Config.prosperityStructureWindowRepeatCap = 0;
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
        }

        // A4 窗名额判定幂等且不消费随机流；cap=0 一律放行
        int flip = 0;
        for (int i = 0; i < 400; i++) {
            final int cx = i * 7 - 500;
            final int cz = i * 13 + 91;
            final boolean first = PlacementGate.windowAllowsFor(seed, cx, cz, "chimney_base", DECL_WINDOW_CAP);
            for (int k = 0; k < 3; k++) {
                if (PlacementGate.windowAllowsFor(seed, cx, cz, "chimney_base", DECL_WINDOW_CAP) != first) {
                    flip++;
                }
            }
        }
        check(flip == 0, "A4 窗名额判定幂等（同参数重复问出现 " + flip + " 次结论翻转）");
        check(PlacementGate.windowAllowsFor(seed, 0, 0, "any", 0), "A4 cap=0（回退位）一律放行");

        // A5 与散布侧同一实现、两段模板 id 不重合（PlacementGate 类注释第 4 条的承诺）
        int collide = 0;
        int mismatch = 0;
        for (final String name : StructureRegistry.names()) {
            final int id = PlacementGate.templateIdOf(name);
            if (id <= ProsperitySurfaceScatter.KIND_CHIMNEY) {
                collide++;
            }
            for (int i = 0; i < 40; i++) {
                final int cx = i * 11 + 3;
                final int cz = i * 5 - 17;
                if (PlacementGate.windowAllowsFor(seed, cx, cz, name, DECL_WINDOW_CAP) != ProsperitySurfaceScatter
                    .windowAllows(seed, cx, cz, id, DECL_WINDOW_CAP)) {
                    mismatch++;
                }
            }
        }
        check(collide == 0, "A5 族模板 id 与散布件型 0.." + ProsperitySurfaceScatter.KIND_CHIMNEY
            + " 零重合（越界 " + collide + " 名）⇒ 一处上限不会串改另一族");
        check(mismatch == 0,
            "A5 同一 (seed,cx,cz,模板) 两部门窗名额逐点同结论（分歧 " + mismatch + " 次）⇒ 不存在第二套真值");

        // A6 roster 扩形状（目标 4）
        final Map<String, Integer> famCount = new TreeMap<>();
        int badNumeric = 0;
        int badDamaged = 0;
        for (final StructureRegistry.Entry e : StructureRegistry.all()) {
            famCount.merge(e.family, 1, Integer::sum);
            if (e.placementDenominator != 0 || e.windowRepeatCap != 0) {
                badNumeric++;
            }
            if (e.allowsDamagedVariant) {
                badDamaged++;
            }
        }
        check(Integer.valueOf(5).equals(famCount.get(PlacementGate.FAMILY_MACHINE))
            && Integer.valueOf(6).equals(famCount.get(PlacementGate.FAMILY_OUTPOST))
            && Integer.valueOf(26).equals(famCount.get(PlacementGate.FAMILY_UNSCOPED)),
            "A6 族标注 = machine 5 / outpost 6 / 城内 26 未标注（实测 " + famCount + "）");
        check(badNumeric == 0, "A6 既有 39 条目的分母/窗上限一律 0 = 跟随 Config（非 0 者 " + badNumeric
            + " 条 ⇒ 会出现第二处数字真值）");
        check(badDamaged == 0, "A6 既有 39 条目 allowsDamagedVariant 一律 false（true 者 " + badDamaged
            + " 条）⇒ 损毁算子的 opt-in 位留给 P8");
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

    // ═════════════════════════ C 组：贴脸率（判据 3）═════════════════════════

    private static void groupC_rate(Sample s) {
        for (final Rate r : s.rates.values()) {
            table("T4-贴脸率", "档=" + r.label, "chunk=" + r.chunks, "城窗跳过=" + r.citySkips,
                "结构=" + r.structures, "8邻贴脸=" + r.adjacent,
                "贴脸率=" + fmt(100.0 * r.adjacent / (double) Math.max(1, r.structures), 3) + "%",
                "outpost=" + r.outpostHits, "机器=" + r.machineHits,
                "落块/chunk=" + fmt(r.solidSum / (double) Math.max(1, r.chunks), 4),
                "窗内同模板发射max=" + r.maxWinEmit);
        }
        final Rate def = s.rates.get(Pass.DEFAULT.label);
        final Rate noCap = s.rates.get(Pass.NOCAP.label);
        final Rate naked = s.rates.get(Pass.NAKED.label);
        check(noCap.structures > 200 && naked.structures > 200,
            "C1 回退两档结构样本各 > 200 座（实测 " + noCap.structures + "/" + naked.structures
                + "），否则贴脸率是噪声（默认档由 C6 单独判）");
        final double rDef = 100.0 * def.adjacent / Math.max(1, def.structures);
        final double rNoCap = 100.0 * noCap.adjacent / Math.max(1, noCap.structures);
        final double rNaked = 100.0 * naked.adjacent / Math.max(1, naked.structures);
        check(rDef <= rNoCap + 1e-9, "C2 单调性：关掉窗上限后贴脸率不降（" + fmt(rDef, 3) + "% -> " + fmt(rNoCap, 3)
            + "%）");
        check(naked.structures >= noCap.structures && noCap.structures >= def.structures,
            "C2 单调性：上限越松结构座数不减（" + def.structures + " <= " + noCap.structures + " <= "
                + naked.structures + "）");
        check(def.maxWinEmit <= DECL_WINDOW_CAP, "C3 默认档窗内同模板发射 chunk 数 <= 上限 " + DECL_WINDOW_CAP
            + "（实测 " + def.maxWinEmit + "）");
        check(noCap.maxWinEmit > DECL_WINDOW_CAP, "C3 灵敏度/反假绿：关掉窗上限后同模板发射数必须越过 "
            + DECL_WINDOW_CAP + "（实测 " + noCap.maxWinEmit + "；未越限说明默认档的 <= 是巧合而非上限在咬合）");
        check(naked.maxWinEmit == noCap.maxWinEmit,
            "C4 预算键与窗名额互不相干：budget=0/cap=0 与 budget=1/cap=0 的窗发射数应同（" + naked.maxWinEmit + " vs "
                + noCap.maxWinEmit + "）");
        // C6 反假绿（本片的阻断性发现）：重复上限应当"削峰"，不该把总体砍没。
        // windowAllows 的排名与掷骰独立 ⇒ P(放行)=cap/256，对 1/64、1/16 的稀疏事件是
        // <b>密度乘子</b>而不是上限（实测默认档 cap=2 在 16384 chunk 上 0 座）。
        check(def.structures >= noCap.structures / 5,
            "C6 默认档结构密度不得低于回退档的 1/5（实测 " + def.structures + " vs " + noCap.structures
                + "）：窗内同模板上限必须<b>以命中集合为条件</b>才叫重复上限，"
                + "否则它只是把密度乘上 cap/256 ⇒ 待主代理裁决（见证据文档 §3 与 §7 待裁决项）");
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

        // D1 接地唯一制式
        check(!machine.contains("findSurfaceY"), "D1 机器层零列扫取高（审计 A-4 闭合，再出现即红）");
        check(!outpost.contains("findSurfaceY"), "D1 outpost 层零列扫取高（:335 那处已改道）");
        check(machine.contains("PlacementGate.groundFn") && outpost.contains("PlacementGate.groundFn")
            && orchestrator.contains("PlacementGate.groundFn"),
            "D1 三族接地都取同一个供给器 PlacementGate.groundFn");

        // D2 placer 侧不自持数字
        // 编排器允许在注册日志里"打印"这两个键（L8 观测锚点），不允许"判定"它们 ⇒ 先把 LOG.info(...)
        // 整段摘掉再统计，剩下的出现次数必须为 0。
        final String orchestratorNoLog = stripLogCalls(orchestrator);
        for (final String[] pair : new String[][] { { machine, "机器" }, { outpost, "outpost" },
            { orchestratorNoLog, "编排器(去日志)" } }) {
            final String body = pair[0];
            final String who = pair[1];
            check(!body.contains("prosperityStructureBudgetPerChunk")
                && !body.contains("prosperityStructureWindowRepeatCap"),
                "D2 " + who + " 侧不自持预算/窗上限 Config 键（数字真值只在 PlacementGate + Config）");
            check(!body.contains("< 20") && !body.contains("> 200"),
                "D2 " + who + " 侧不再自带可落地 y 带字面量（已收进 PlacementGate.LANDING_Y_*）");
            check(!body.contains("BUDGET") && !body.contains("WINDOW_CAP"),
                "D2 " + who + " 侧无自带预算/上限常量名");
        }

        // D3 读键方与扣减方各只有一处
        final String config = stripComments(read(root.resolve("src/main/java/com/miaokatze/gtsr/config/Config.java")));
        check(config.contains("prosperityStructureBudgetPerChunk")
            && config.contains("prosperityStructureWindowRepeatCap"),
            "D3 Config 是两键的声明与注册处（唯一数字出处）");
        check(count(gate, "Config.prosperityStructure") == 3, "D3 非 Config 侧只有本门读这两键（实测读 "
            + count(gate, "Config.prosperityStructure") + " 处：预算 1 + 窗上限 2〔重载默认位 + Entry 覆写生效位〕）");
        check(count(orchestratorNoLog, "Config.prosperityStructure") == 0,
            "D3 编排器不把这两键接进任何判定（去日志后出现 "
                + count(orchestratorNoLog, "Config.prosperityStructure") + " 次）");
        check(count(gate, "committed++") == 1 && count(gate, "onCommit(") == 2,
            "D3 预算扣减点唯一（committed++ 1 处 / onCommit 定义+调用 2 处，实测 " + count(gate, "committed++") + "/"
                + count(gate, "onCommit(") + "）");
        check(!scatter.contains("PlacementGate"),
            "D3 散布侧（P5）不反向依赖本门 ⇒ 两部门各守自身唯一入口，不形成第二真值");

        // D4 调用面计数
        final List<Path> placerSide = Arrays.asList(
            root.resolve(
                "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java"),
            root.resolve(
                "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java"),
            root.resolve(
                "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java"));
        int begin = 0;
        int request = 0;
        int commit = 0;
        int counting = 0;
        for (final Path p : placerSide) {
            final String b = stripComments(read(p));
            begin += count(b, "PlacementGate.beginChunk(");
            request += count(b, ".request(");
            commit += count(b, ".commit(");
            counting += count(b, "PlacementGate.counting(");
        }
        check(begin == 3, "D4 全仓 beginChunk 调用点 = 3（编排器 1 + 两个 placer 的无门兼容重载 2），实测 " + begin);
        check(request == 2 && commit == 2 && counting == 2,
            "D4 两 placer 各恰一次 request / commit / counting（实测 " + request + "/" + commit + "/" + counting + "）");

        // D5 窗判定不重复实现
        check(gate.contains("ProsperitySurfaceScatter") && gate.contains(".windowAllows(")
            && !gate.contains("windowSlotHash"),
            "D5 门不自建槽位哈希，窗名额唯一实现仍在散布侧（本类只委托）");
        check(count(scatter, "boolean windowAllows(") == 1, "D5 全仓 windowAllows 定义唯一（散布侧）");

        // D6 门不自持地表门成员集合（P4 红线对本类同样成立）
        check(!gate.contains("landableTops") && !gate.contains("instanceof Block"),
            "D6 门不查地表门成员集合，就绪门只吃调用方给的 boolean 结论");
        check(count(gate, "Blocks.") == count(gate, "Blocks.air"),
            "D6 门里 Blocks.* 只允许空气句柄（实测 Blocks. 出现 " + count(gate, "Blocks.") + " 处，air "
                + count(gate, "Blocks.air") + " 处）");
    }

    // ═════════════════════════ E 组：micro 强度层消费结论（判据 8）═════════════════════════

    private static void groupE_microLayer() throws Exception {
        final Path root = Paths.get("");
        int consumers = 0;
        final List<String> seen = new ArrayList<>();
        for (final String rel : new String[] { "framework/structure/PlacementGate.java",
            "prosperity/ruins/RuinedMachinePlacer.java", "prosperity/ruins/ProsperityOutpostPlacer.java",
            "prosperity/ruins/ProsperityWorldGenerator.java", "prosperity/ruins/ProsperitySurfaceScatter.java",
            "prosperity/ruins/ProsperityDecorPlacer.java", "prosperity/ruins/city/CityVariants.java",
            "prosperity/ruins/city/CityPlanner.java", "prosperity/ruins/city/CityBlockResolver.java" }) {
            final String b = stripComments(read(root.resolve("src/main/java/com/miaokatze/gtsr/common/dimension/" + rel)));
            final int c = count(b, "microStrengthAt");
            consumers += c;
            if (c > 0) {
                seen.add(rel + ":" + c);
            }
        }
        final String mgr = stripComments(read(root.resolve(
            "src/main/java/com/miaokatze/gtsr/common/dimension/framework/GTSRWorldChunkManager.java")));
        check(mgr.contains("float microStrengthAt("), "E1 micro 强度只读出口仍在框架层（P6 交付件未回退）");
        check(consumers == 0, "E2 结构/散布/城/装饰侧对 microStrengthAt 的消费点 = 0（实测 " + consumers + " " + seen
            + "）⇒ 本片结论固定申报\"交 P8\"，不留悬空层（证据文档 §8）");
        System.out.println("# P7B-MICRO verdict=deferred-to-P8 consumers=" + consumers);
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
        DEFAULT("默认档 budget=1/cap=2", 1, 2), NOCAP("只关窗上限 budget=1/cap=0", 1, 0), NAKED(
            "两键都关 budget=0/cap=0", 0, 0);

        final String label;
        final int budget;
        final int cap;

        Pass(String label, int budget, int cap) {
            this.label = label;
            this.budget = budget;
            this.cap = cap;
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

        Rate(String label) {
            this.label = label;
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
                        // 顺序固定：默认档 → 就地验落块真值（此时该区网格还活着）→ 另两档
                        runPass(Pass.DEFAULT, world, seed, cx0, cz0, si, r);
                        // 落块真值与位移量化都挂在"回退档"（budget=1 / cap=0 = 改造前密度）上采：
                        // 假成功路径与窗上限无关，而默认档若把结构砍到 0 就根本没有样本可验
                        // （实测默认档 256 chunk 内 0 座，见 T4 与 C6）。
                        runPass(Pass.NOCAP, world, seed, cx0, cz0, si, r);
                        verifyHits(world);
                        runPass(Pass.NAKED, world, seed, cx0, cz0, si, r);
                    }
                }
            } finally {
                Config.prosperityStructureBudgetPerChunk = budget0;
                Config.prosperityStructureWindowRepeatCap = cap0;
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
            final Rate rate = rates.get(p.label);
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
                        final Map<String, Integer> m = rate.winEmit.computeIfAbsent(key, k -> new HashMap<>());
                        final int n = m.merge(t, 1, Integer::sum);
                        if (n > rate.maxWinEmit) {
                            rate.maxWinEmit = n;
                        }
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
                    Config.prosperityStructureWindowRepeatCap = cap0;
                }
            }
            Config.prosperityStructureBudgetPerChunk = budget0;
            Config.prosperityStructureWindowRepeatCap = cap0;
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

    // ═════════════════════════════════ 装配与工具件 ═════════════════════════════════

    private static void bootstrap() {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        extraBlocks();
        RuinedMachinePlacer.registerVariants();
        ProsperityOutpostPlacer.registerVariants();
        CityVariants.registerVariants();
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

    /** 接受一切写入（不落网格，保持网格只读）并记录 bbox 与逐列写入位置。 */
    static final class RecordSink implements BlockSink {
        int accepted;
        int solid;
        int dropped;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
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
                columns.add(((long) x << 32) | (z & 0xFFFFFFFFL));
            }
            return true;
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
