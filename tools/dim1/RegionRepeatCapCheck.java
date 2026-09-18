import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperitySurfaceScatter;
import com.miaokatze.gtsr.config.Config;

/**
 * <b>P5 判据 2 的 H-2 钉：每 16×16 chunk 窗内同模板重复上限</b>
 * （plan §5 P5「RegionRepeatCapCheck（窗内同模板 ≤ 阈值）」/ §2.2 H-2 / §7.1 U3「柱 ≤2/窗」）。
 * <p>
 * ═══ 列名申报 ═══
 * <pre>
 *  A <b>纯函数硬钉</b>（不依赖采样）：对 64 个窗 × 4 个模板 × 256 槽位穷举 {@code windowAllows}，
 *    断言"每个窗内被放行的槽位数 == min(cap, 256)"<b>恰好</b>成立 ⇒ 这是<b>结构上界</b>，
 *    不是统计稀疏化。cap=1/2/3/4 各测一遍，并测 cap=0 与 cap=256 的"关闭"口径。
 *  A2 一致性：同一槽位在任意调用次数下结论相同（确定性，plan §2.3 判据 1）；
 *    负坐标窗（含 (-16,-16) 与 (-1,-1) 跨窗）分区正确——同窗成员判定一致、跨窗不串名额；
 *    不同模板的排序相互独立（同一槽位在 cap=1 下不会 4 个模板都放行到同一处）。
 *  B 行为级（真实编排链，8 seed × 8 窗）：生产默认档下窗内竖向件发射 chunk 数 = 0（柱阵归零的
 *    另一面）；平面件型的窗发射数打印但不设阈（申报：其窗上限归 P7 PlacementGate）。
 *  C pinned 场景：竖向件放回（开关=true）、其余按默认 ⇒ 实测"窗内柱发射 chunk 数"必须 ≤ 2 且
 *    ≥ 1（=1..2 才说明上限真的在限流，而不是恒放行或恒关门）。
 *  D 灵敏度（判据 2「人为放宽上限必须变红」的进程内版本）：把窗上限放宽到 64 ⇒ C 的 ≤2 必须不成立
 *    （若放宽后仍然 ≤2，说明本判据对上限改动不敏感＝假绿，本组自己红）。
 *    跨进程的影子树版本（改 Config 默认值后整套复跑）在 {@code tools/dim1/surface_checks.sh} 的 [10]。
 * </pre>
 * <b>用法</b>：{@code java RegionRepeatCapCheck [seeds] [regionsPerSeed]}（默认 8 × 8）
 * 退出码：0 = 全绿；1 = 有申报项被破坏；2 = 环境不可用。
 */
public final class RegionRepeatCapCheck {

    /** 申报：H-2 窗边长（chunk 数）。 */
    static final int DECL_WINDOW_CHUNKS = 16;
    /** 申报：窗内竖向件发射上限（plan §7.1 U3「柱 ≤2/窗」）。 */
    static final int DECL_WINDOW_CAP = 2;
    /** 本工具用的窗数（A 组穷举的样本窗）。 */
    private static final int PIN_WINDOWS = 64;

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        final int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 8;
        final int regions = args.length > 1 ? Integer.parseInt(args[1]) : 8;
        Dim78ScatterDensityCheck.bootstrap();

        final int defWindowCap = Config.prosperityScatterWindowRepeatCap;
        final int defContours = Config.prosperityScatterContoursPerChunk;
        final int defBlocks = Config.prosperityScatterBlocksPerChunk;
        final int defAttempts = Config.prosperityScatterAttemptsPerChunk;
        final boolean defVertical = Config.prosperityScatterVerticalPieces;

        check(Config.prosperityScatterWindowRepeatCap == DECL_WINDOW_CAP,
            "A 申报的窗上限 == Config 默认值（" + DECL_WINDOW_CAP + "，实测 " + defWindowCap + "）");
        check(ProsperitySurfaceScatter.WINDOW_CHUNKS == DECL_WINDOW_CHUNKS,
            "A 生产窗边长 = " + DECL_WINDOW_CHUNKS + "（实测 " + ProsperitySurfaceScatter.WINDOW_CHUNKS + "）");
        check(ProsperitySurfaceScatter.windowCapFor(ProsperitySurfaceScatter.KIND_CHIMNEY) == defWindowCap,
            "A 竖向件确实挂上了窗上限（windowCapFor(CHIMNEY)=" + ProsperitySurfaceScatter.windowCapFor(
                ProsperitySurfaceScatter.KIND_CHIMNEY) + "）");
        check(ProsperitySurfaceScatter.windowCapFor(ProsperitySurfaceScatter.KIND_SLEEPER) == 0
            && ProsperitySurfaceScatter.windowCapFor(ProsperitySurfaceScatter.KIND_PIPE) == 0
                && ProsperitySurfaceScatter.windowCapFor(ProsperitySurfaceScatter.KIND_RIVET_PLATE) == 0,
            "A 平面件型的窗上限申报为 0=关闭（其窗上限归 P7 PlacementGate，本片不越界）");

        groupA_pureFunctionHardCap();
        groupA2_consistencyAndNegativeCoords();

        // —— B/C/D：同一份真实编排链样本，三行只差"竖向件开关 + 窗上限"——
        final List<Dim78ScatterDensityCheck.Row> rows = Arrays.asList(
            new Dim78ScatterDensityCheck.Row("DEFAULT-生产默认档", defContours, defBlocks, defAttempts,
                Boolean.valueOf(defVertical), defWindowCap),
            new Dim78ScatterDensityCheck.Row("PIN-竖向件放回+窗上限默认", defContours, defBlocks, defAttempts,
                Boolean.TRUE, defWindowCap),
            new Dim78ScatterDensityCheck.Row("RELAX-竖向件放回+窗上限放宽到64", defContours, defBlocks, defAttempts,
                Boolean.TRUE, 64));
        final Dim78ScatterDensityCheck.Sampler sampler =
            new Dim78ScatterDensityCheck.Sampler(seeds, regions, 16, rows);
        sampler.run();
        final Dim78ScatterDensityCheck.Stats def = sampler.stats("DEFAULT-生产默认档");
        final Dim78ScatterDensityCheck.Stats pin = sampler.stats("PIN-竖向件放回+窗上限默认");
        final Dim78ScatterDensityCheck.Stats relax = sampler.stats("RELAX-竖向件放回+窗上限放宽到64");

        check(def.windows() >= seeds * regions - 4,
            "B 样本窗数足够（" + def.windows() + " ≥ " + (seeds * regions - 4) + "）");
        check(def.maxWinEmitChim() == 0 && def.chimneyMean() == 0.0D,
            "B 判据1/2 默认档：窗内柱发射 chunk 数 = " + def.maxWinEmitChim() + "、柱/chunk = "
                + fmt(def.chimneyMean()) + "（竖向件已从散布源头摘出）");
        check(def.maxWinEmitFlat() > 0,
            "B 反证：平面件型在窗内确实有发射（" + def.maxWinEmitFlat()
                + " 个发射 chunk）⇒ 上面的 0 不是「整个样本没散布」造成的假绿");

        check(pin.maxWinEmitChim() <= DECL_WINDOW_CAP,
            "C 判据2 pinned 场景：窗内柱发射 chunk 数最大值 " + pin.maxWinEmitChim() + " ≤ " + DECL_WINDOW_CAP);
        check(pin.maxWinEmitChim() >= 1,
            "C 反假绿：pinned 场景窗内柱发射数 ≥ 1（实测 " + pin.maxWinEmitChim()
                + "；恒 0 说明竖向件根本没放回，上限判据失去对象）");
        check(pin.chimneyMean() > 0.0D && pin.chimneyMean() < 0.05D,
            "C pinned 场景柱/chunk = " + fmt(pin.chimneyMean()) + " ∈ (0, 0.05)（现状 9.2，即 2/窗 ≈ 0.008）");
        check(pin.chimneyChunkPp() > 0.0D && pin.chimneyChunkPp() < 5.0D,
            "C pinned 场景含柱 chunk 占比 = " + fmt(pin.chimneyChunkPp()) + "pp ∈ (0, 5)");

        check(relax.maxWinEmitChim() > DECL_WINDOW_CAP,
            "D 灵敏度：窗上限放宽到 64 后柱发射数 " + relax.maxWinEmitChim() + " 必须越过申报上限 "
                + DECL_WINDOW_CAP + "（否则 C 组对上限改动不敏感＝假绿）");
        check(relax.chimneyMean() > pin.chimneyMean() * 5.0D,
            "D 灵敏度：放宽后柱/chunk 从 " + fmt(pin.chimneyMean()) + " 涨到 " + fmt(relax.chimneyMean())
                + "（必须显著上涨）");

        // 复位（写过的键还原，防同进程后续用器读到污染值）
        Config.prosperityScatterWindowRepeatCap = defWindowCap;
        Config.prosperityScatterVerticalPieces = defVertical;

        System.out.println("P5-REPEAT windows=" + def.windows() + " defaultMaxWinEmitChim=" + def.maxWinEmitChim()
            + " pinnedMaxWinEmitChim=" + pin.maxWinEmitChim() + " relaxedMaxWinEmitChim="
            + relax.maxWinEmitChim() + " flatMaxWinEmit=" + def.maxWinEmitFlat());
        if (FAILURES.isEmpty()) {
            System.out.println("REGION REPEAT CAP CHECK PASS: assertions=" + passed);
            System.exit(0);
        }
        System.out.println(
            "REGION REPEAT CAP CHECK FAIL: " + FAILURES.size() + " assertion(s) failed, passed=" + passed);
        System.exit(1);
    }

    /** A 组：对 cap=1..4 与各关闭口径穷举"放行槽位数 == cap"，证明它是硬上界而不是概率。 */
    private static void groupA_pureFunctionHardCap() {
        for (int cap = 1; cap <= 4; cap++) {
            int badWindows = 0;
            int maxSeen = 0;
            int minSeen = Integer.MAX_VALUE;
            for (int w = 0; w < PIN_WINDOWS; w++) {
                for (int template = 0; template < 4; template++) {
                    final int allowed = allowedSlotsInWindow(1000L + w, w * 16, 7000, template, cap);
                    maxSeen = Math.max(maxSeen, allowed);
                    minSeen = Math.min(minSeen, allowed);
                    if (allowed != cap) {
                        badWindows++;
                    }
                }
            }
            check(badWindows == 0,
                "A cap=" + cap + "：64 窗 × 4 模板的放行槽位数都恰好 = cap（min=" + minSeen + " max=" + maxSeen
                    + "，越界窗数 " + badWindows + "）");
        }
        check(allowedSlotsInWindow(4242L, 0, 0, 3, 0) == 256,
            "A cap=0 口径 = 关闭（256 槽全放行）");
        check(allowedSlotsInWindow(4242L, 0, 0, 3, 256) == 256,
            "A cap ≥ 窗内槽数口径 = 结构上无法约束 ⇒ 全放行（与实现的短路一致）");
        check(allowedSlotsInWindow(4242L, 0, 0, 3, 999) == 256, "A cap=999 同上（回退口径可完全解除）");
    }

    /**
     * 一个窗内某模板的放行槽位数（穷举 256 槽，走生产同一个 {@code windowAllows}）。
     * <p>
     * <b>原点必须先按 16 对齐</b>：传入的 baseCx/baseCz 可能落在窗中间（首版就踩了这个坑——
     * 用未对齐的 z=7000 枚举会得到 min=0/max=2，看着像实现有 bug，其实是枚举块跨了两个窗）。
     */
    private static int allowedSlotsInWindow(long seed, int baseCx, int baseCz, int template, int cap) {
        final int ox = Math.floorDiv(baseCx, DECL_WINDOW_CHUNKS) * DECL_WINDOW_CHUNKS;
        final int oz = Math.floorDiv(baseCz, DECL_WINDOW_CHUNKS) * DECL_WINDOW_CHUNKS;
        int n = 0;
        for (int lx = 0; lx < DECL_WINDOW_CHUNKS; lx++) {
            for (int lz = 0; lz < DECL_WINDOW_CHUNKS; lz++) {
                if (ProsperitySurfaceScatter.windowAllows(seed, ox + lx, oz + lz, template, cap)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** A2 组：确定性、跨窗分区、负坐标与模板独立性。 */
    private static void groupA2_consistencyAndNegativeCoords() {
        final long seed = 0x5EEDL;
        check(ProsperitySurfaceScatter.windowAllows(seed, 33, -7, 3, 2) == ProsperitySurfaceScatter.windowAllows(
            seed,
            33,
            -7,
            3,
            2), "A2 同输入重复调用结论一致（纯函数/确定性）");
        // 跨窗不串名额：(-1,-1) 属于 (-16..-1) 窗，(0,0) 属于 (0..15) 窗
        final boolean a = ProsperitySurfaceScatter.windowAllows(seed, -1, -1, 3, 1);
        final boolean b = ProsperitySurfaceScatter.windowAllows(seed, 0, 0, 3, 1);
        check(allowedSlotsInWindow(seed, -16, -16, 3, 1) == 1,
            "A2 负坐标窗（原点 -16）cap=1 仍恰好放行 1 个槽位（floorDiv 分区正确）");
        check(allowedSlotsInWindow(seed, -16, -16, 2, 2) == 2,
            "A2 负坐标窗 cap=2 恰好放行 2 个槽位");
        System.out.println("# A2 诊断：cap=1 时 (-1,-1) 放行=" + a + "、(0,0) 放行=" + b);
        // 模板独立性：cap=1 时同窗内 4 个模板的放行槽位不必相同（排序相互独立）
        final java.util.Set<Integer> distinctSlots = new java.util.HashSet<>();
        for (int t = 0; t < 4; t++) {
            for (int lx = 0; lx < DECL_WINDOW_CHUNKS; lx++) {
                for (int lz = 0; lz < DECL_WINDOW_CHUNKS; lz++) {
                    if (ProsperitySurfaceScatter.windowAllows(seed, lx, 16 + lz, t, 1)) {
                        distinctSlots.add(lx * 16 + lz);
                    }
                }
            }
        }
        check(distinctSlots.size() >= 2,
            "A2 四个模板在 cap=1 下的放行槽位不全相同（实测落在 " + distinctSlots.size()
                + " 个不同槽位）⇒ 盐按模板隔离生效");
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static void check(boolean ok, String msg) {
        if (ok) {
            passed++;
            return;
        }
        FAILURES.add(msg);
        System.out.println("  FAIL " + msg);
    }
}
