import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.BiomePlaneAccess;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.shattered.ChunkProviderShatteredGrounds;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinPlacer;
import com.miaokatze.gtsr.common.dimension.shattered.WorldGenShatteredRuins;

/**
 * P12（L8）诊断行列名申报 + 一次性门离线断言（plan §5 P12 / §2.3 判据 2）。
 * <p>
 * 与生产共用<b>同一实现体</b>：{@code GTSRChunkProviderBase.installStandardDiagSupplement()}（LoadComplete 同款注入）、
 * {@code GTSRChunkProviderBase.emitEntryDiagOnce/buildEntryDiagLine}（provideChunk 首行同款入口）、
 * {@code logSurfaceNotLaidOnce/logCreatureWeightAbsorbedOnce}（真锚点）。零手写样例。
 * <p>
 * 场景：
 * <ol>
 * <li><b>A dim78 正常态</b>：SurfaceHarness 真实装配（recordAllocation×8 + 真 def/manager/bind），
 * 诊断行必须含全部 19 列（dim/def/bound/plane/biomes/allocated/degraded/occupant/surface/
 * layWhenDegraded/macro/chain/identity/biomeTable/scatter/structure/creature/roster/textures；
 * chain/identity 为 B1 GenLayer 观测列），
 * 且 {@code degraded=NONE surface=laid}；<b>P0（v1.20.33）起 plane 列另做"缺列必红"的反假绿
 * 自检</b>（把该列从样例行里抹掉后列断言必须变红，否则 {@code contains} 钉是恒真的）；</li>
 * <li><b>B dim79 强制降级态</b>：四名册成员全部 {@code recordNoSlot}（owner 快照带
 * {@code red:} 前缀）+ 空表 def ⇒ {@code degraded=EMPTY surface=not-laid(EMPTY)}；随后
 * ①512 次 emit 尝试 ⇒ 诊断行仍恰 1 行（DIAG_EMITTED 一次性门），②512 chunk 真表层缝生成
 * ⇒ "surface NOT laid" 锚点恰 1 行（P2 既有门复验），③512 次 {@code getPossibleCreatures}
 * ⇒ "weights ABSORBED" 锚点恰 1 行（P12 新增，返回 null 语义一字未动）。</li>
 * </ol>
 * 运行（cwd=仓库根；需 {@code src/main/resources} 进 classpath 让 textures 列取到 49/49 真值，
 * 缺资源根时该列按申报口径输出 {@code NA(...)} 而非伪值）：
 * <pre>
 * java -cp temp/p4-surface/tools;temp/p4-surface/classes;src/main/resources;&lt;CP&gt; DiagLineCheck
 * </pre>
 * 由 {@code tools/dim1/surface_checks.sh} [2g] 挂链。
 */
public class DiagLineCheck {

    /** 列名申报（判据 1 的"逐项对照"机器面；缺一列即红）。B1 起含 genlayer 的 chain/identity 两列。 */
    private static final String[] COLUMNS = { "dim=", "def=", "bound=", "plane=", "biomes=[", "allocated=",
        "degraded=", "occupant=", "surface=", "layWhenDegraded=", "macro=", "chain=", "identity=", "biomeTable=",
        "scatter=[", "structure=[", "creature=[", "roster=", "textures=" };

    private static final int ATTEMPTS = 512;

    private static int assertions;

    public static void main(String[] args) throws Exception {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        // 名册 bootstrap（与 RosterIntegrityCheck 同源五注册口）：roster 列的真值来源
        RuinedMachinePlacer.registerVariants();
        CityVariants.registerVariants();
        ProsperityOutpostPlacer.registerVariants();
        RuinPlacer.registerVariants();
        WorldGenShatteredRuins.registerVariants();
        // 生产同款内容段注入：反射调 CommonProxy$DiagAssembly（该类与 CommonProxy 互不引用；
        // 直接 import 会让工具 javac 隐式编译 CommonProxy 本体并牵出 gregtech/AE2，实测不可达）。
        final Class<?> assembly = Class.forName("com.miaokatze.gtsr.main.CommonProxy$DiagAssembly");
        assembly.getMethod("install").invoke(null);
        // 装配镜像钉：CommonProxy 源文本必须仍经 DiagAssembly 装那四段（防"生产改了工具没跟"）。
        // 去空白后比对：项目 spotless 会把长串接折行，按原样 contains 会在纯格式化后假红。
        final String proxySrc = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths
            .get("src/main/java/com/miaokatze/gtsr/main/CommonProxy.java")), "UTF-8");
        final String proxyFlat = proxySrc.replaceAll("\\s+", "");
        check(proxyFlat.contains("scatter=[\"+ProsperitySurfaceScatter.diagSummary()"),
            "钉1：CommonProxy 装配不再走 scatter.diagSummary");
        check(proxyFlat.contains("Config.prosperityStructureBudgetPerChunk"), "钉2：CommonProxy 装配不再读结构预算");
        check(proxyFlat.contains("GTSRCreatureRoster.diagSummary(dimKey)"), "钉3：CommonProxy 装配不再走 roster.diagSummary");
        check(proxyFlat.contains("DiagAssembly.install()") && proxyFlat.contains("loadComplete"),
            "钉4：loadComplete 不再接线 DiagAssembly");

        checkDim78Normal();
        checkDim79ForcedEmpty();

        // boot 汇总行（LoadComplete 同款组装函数）
        final String boot = (String) assembly.getMethod("bootSummaryLine").invoke(null);
        check(boot.startsWith("[GTSR][diag]"), "BOOT 行前缀缺失: " + boot);
        check(boot.contains("dim78=") && boot.contains("dim79=") && boot.contains("occupant=")
            && boot.contains("roster=") && boot.contains("textures="), "BOOT 行列不全: " + boot);
        System.out.println("BOOTLINE " + boot);

        System.out.println(
            "DIAGLINE PASS: assertions=" + assertions + " columns=" + COLUMNS.length + "/scenario×2 once=" + ATTEMPTS
                + "->1");
    }

    // ═══════════════════════════ 场景 A：dim78 正常态 ═══════════════════════════

    private static void checkDim78Normal() throws Exception {
        final BiomeGenBase[] p = SurfaceHarness.prosperityBiomes();
        final BiomeGenBase[] s = SurfaceHarness.shatteredBiomes();
        SurfaceHarness.recordAllAllocations(p, s);
        final GTSRDimensionDef def78 = SurfaceHarness.def(true, p, SurfaceHarness.prosperityWeights());
        final GTSRWorldChunkManager mgr78 = SurfaceHarness.manager(true, def78);
        // 与生产 bind 同源，但把维度 id 钉成 78（离线 def 未过 DimensionRegistrar，resolvedDimId=-1）
        GTSRBiomeAuthority.bind(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, 78, mgr78::biomeAt);
        final World world78 = harnessWorld(78, mgr78);
        final GTSRChunkProviderBase provider78 = new ChunkProviderProsperityRuins(world78, SurfaceHarness.SEED);

        check(GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
            .degraded() == GTSRBiomeAuthority.Degraded.NONE, "A 前提：dim78 应处 NONE");
        final boolean first = GTSRChunkProviderBase.emitEntryDiagOnce(world78,
            GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        check(first, "A 首次进维必须打诊断行");
        int dup = 0;
        for (int i = 1; i < ATTEMPTS; i++) {
            if (GTSRChunkProviderBase.emitEntryDiagOnce(world78, GTSRBiomeAuthority.DIM_KEY_PROSPERITY)) {
                dup++;
            }
        }
        check(dup == 0, "A 诊断行一次性被破坏（" + ATTEMPTS + " 次尝试多打 " + dup + " 行）");

        final String line = GTSRChunkProviderBase.buildEntryDiagLine(78, GTSRBiomeAuthority.DIM_KEY_PROSPERITY, mgr78);
        System.out.println("DIAGLINE78 " + line);
        assertColumns(line, "A");
        checkPlaneColumn(line, "A");
        check(line.contains("degraded=NONE") && line.contains("surface=laid"), "A 列值错: " + line);
        // P16-B1：roster 列的期望值改由 S8 的独立期望量派生（原来这里写死 "roster=47"）。
        // 不取 StructureRegistry.names().size()——诊断行本身就是从它算出来的，那样这条判据恒真；
        // EXPECTED_TOTAL 由 P0 基线 + 两张模板表派生，且 S8 另有一遍"名集合逐名相等"的对账，
        // 所以这仍是一个独立预期量（名册少了/多了都会在两处各自红）。
        check(line.contains("roster=" + S8RegistryRosterCheck.EXPECTED_TOTAL),
            "A roster 列应为 " + S8RegistryRosterCheck.EXPECTED_TOTAL + "（P0 基线 + 废墟族 + 跨片巨构族）: "
                + line);
        check(line.contains("allocated=4/4"), "A allocated 列错: " + line);
        // 正常态不得出现"权重被吞"锚点（NONE 零介入，与表层门同一纪律）
        check(!GTSRChunkProviderBase.logCreatureWeightAbsorbedOnce(
            GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)),
            "A 正常态不应打 ABSORBED 锚点");
        final java.util.List<BiomeGenBase.SpawnListEntry> table78 = provider78.getPossibleCreatures(
            EnumCreatureType.creature, 8, 64, 8);
        check(table78 != null, "A 正常态 getPossibleCreatures 不应落 null");
        check(!GTSRChunkProviderBase.logCreatureWeightAbsorbedOnce(
            GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)),
            "A 正常态解析成功后仍打了 ABSORBED 锚点");
    }

    // ═══════════════════════ 场景 B：dim79 强制 EMPTY 降级 ═══════════════════════

    private static void checkDim79ForcedEmpty() throws Exception {
        for (final BiomeId id : BiomeId.values()) {
            if (GTSRBiomeAuthority.DIM_KEY_SHATTERED.equals(id.dimKey())) {
                GTSRBiomeAuthority.recordNoSlot(id, 190 + id.rosterIndex(), "red:Offline Occupant " + id.name());
            }
        }
        final GTSRBiomeAuthority auth79 = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_SHATTERED);
        check(auth79.degraded() == GTSRBiomeAuthority.Degraded.EMPTY, "B 前提：dim79 应处 EMPTY");
        final GTSRDimensionDef def79 = SurfaceHarness.emptyDef(false);
        final GTSRWorldChunkManager mgr79 = SurfaceHarness.manager(false, def79);
        GTSRBiomeAuthority.bind(GTSRBiomeAuthority.DIM_KEY_SHATTERED, 79, mgr79::biomeAt);
        final World world79 = harnessWorld(79, mgr79);
        final GTSRChunkProviderBase provider79 = new ChunkProviderShatteredGrounds(world79, SurfaceHarness.SEED);

        final boolean first = GTSRChunkProviderBase.emitEntryDiagOnce(world79, GTSRBiomeAuthority.DIM_KEY_SHATTERED);
        check(first, "B 首次进维必须打诊断行");
        int dup = 0;
        for (int i = 1; i < ATTEMPTS; i++) {
            if (GTSRChunkProviderBase.emitEntryDiagOnce(world79, GTSRBiomeAuthority.DIM_KEY_SHATTERED)) {
                dup++;
            }
        }
        check(dup == 0, "B 诊断行一次性被破坏（多打 " + dup + " 行）");

        final String line = GTSRChunkProviderBase.buildEntryDiagLine(79, GTSRBiomeAuthority.DIM_KEY_SHATTERED, mgr79);
        System.out.println("DIAGLINE79 " + line);
        assertColumns(line, "B");
        checkPlaneColumn(line, "B");
        check(line.contains("degraded=EMPTY"), "B degraded 列错: " + line);
        check(line.contains("surface=not-laid(EMPTY)"), "B surface 列错: " + line);
        check(line.contains("allocated=0/4"), "B allocated 列错: " + line);
        check(line.contains("occupant=[") && line.contains("red:"), "B owner 快照未进诊断行: " + line);

        // ② 512 chunk 真表层缝（32×16）：EMPTY ⇒ "surface NOT laid" 锚点必须已被首 chunk 消耗（一次性）
        final Method gen = SurfaceHarness.generateTerrain();
        final Method seam = SurfaceHarness.surfaceSeam(ChunkProviderShatteredGrounds.class);
        for (int cx = -8; cx < -8 + 32; cx++) {
            for (int cz = -8; cz < -8 + 16; cz++) {
                final Block[] blocks = new Block[65536];
                final byte[] meta = new byte[65536];
                gen.invoke(provider79, cx, cz, blocks, meta, null);
                final BiomeGenBase[] biomes = mgr79.loadBlockGeneratorData(null, cx * 16, cz * 16, 16, 16);
                seam.invoke(null, SurfaceHarness.SEED, cx * 16, cz * 16, blocks, meta, biomes);
            }
        }
        check(!GTSRChunkProviderBase.logSurfaceNotLaidOnce(GTSRBiomeAuthority.DIM_KEY_SHATTERED, "EMPTY"),
            "B 512 chunk 后 NOT-laid 锚点仍可调出第二行（一次性失效）");
        // 只读查询（无副作用！不能用 logSurfaceNotLaidOnce 去"探测"未打出的锚点——那会把它消耗掉）
        check(GTSRChunkProviderBase.surfaceNotLaidAnchorTaken(GTSRBiomeAuthority.DIM_KEY_SHATTERED, "EMPTY"),
            "B EMPTY 锚点应已被首 chunk 消耗");
        check(!GTSRChunkProviderBase.surfaceNotLaidAnchorTaken(GTSRBiomeAuthority.DIM_KEY_SHATTERED, "NULL_TABLE"),
            "B 不应同时打出 NULL_TABLE 第二锚点");

        // ③ 512 次 getPossibleCreatures（EMPTY 解析必落空）⇒ 吞权重锚点恰 1 行且返回语义不变
        for (int i = 0; i < ATTEMPTS; i++) {
            final java.util.List<BiomeGenBase.SpawnListEntry> absorbed = provider79.getPossibleCreatures(
                EnumCreatureType.creature, i, 64, i);
            if (i < 4) {
                check(absorbed == null, "B EMPTY 态 getPossibleCreatures 返回语义漂移（必须 null）");
            }
        }
        check(!GTSRChunkProviderBase.logCreatureWeightAbsorbedOnce(auth79),
            "B 512 次解析落空后 ABSORBED 锚点仍可调出第二行（一次性失效）");
        System.out.println("DIAGLINE-B note: dim79 空表下 creature 四群系 tables 全 0 由 line 的 creature=[...] 列申报");
    }

    // ═══════════════════════════ 装配与断言原语 ═══════════════════════════

    /** mock World 的 provider 直接挂真实 manager（{@code World.getWorldChunkManager()} 实读 provider.worldChunkMgr）。 */
    private static World harnessWorld(int dimId, GTSRWorldChunkManager mgr) {
        final World world = SurfaceHarness.mockWorld();
        world.provider.dimensionId = dimId;
        world.provider.worldChunkMgr = mgr;
        return world;
    }

    private static void assertColumns(String line, String tag) {
        for (final String col : COLUMNS) {
            check(line.contains(col), tag + " 诊断行缺列 " + col + "：line=" + line);
        }
    }

    /**
     * P0（v1.20.33）新增的 {@code plane=} 列专属断言（防假绿）：
     * ①取值只能是 {@code short|byte} 且与 {@code hookPresent()} 同真同假；②行内值必须等于
     * {@link BiomePlaneAccess#runtimeMode()}（生产同一实现体，不许工具自己拼字符串）；
     * ③<b>把该列从样例行里抹掉后列断言必须变红</b>——没有这条，"缺列即红"的 {@code contains}
     * 钉在列名写错时会是恒真的绿。
     */
    private static void checkPlaneColumn(String line, String tag) {
        final String mode = BiomePlaneAccess.runtimeMode();
        check("short".equals(mode) || "byte".equals(mode), tag + " plane 列取值只能是 short|byte，实得 " + mode);
        check(("short".equals(mode)) == BiomePlaneAccess.hookPresent(),
            tag + " runtimeMode 与 hookPresent 脱钩：mode=" + mode + " hookPresent=" + BiomePlaneAccess.hookPresent());
        check(line.contains("plane=" + mode),
            tag + " 诊断行 plane 列与 runtimeMode 不一致（期望 plane=" + mode + "）: " + line);
        final String stripped = line.replaceAll(" plane=[A-Za-z]+", "");
        check(!stripped.equals(line), tag + " 反假绿前提失败：plane 列抹不掉（列文本形态漂移）: " + line);
        check(missingColumns(stripped) >= 1,
            tag + " 反假绿失败：抹掉 plane 列后列断言仍为绿 ⇒ 该列的存在检查恒真（缺列不会变红）");
    }

    /** COLUMNS 里在行中缺席的列数（0 = 全齐）。 */
    private static int missingColumns(String line) {
        int n = 0;
        for (final String col : COLUMNS) {
            if (!line.contains(col)) {
                n++;
            }
        }
        return n;
    }

    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) {
            System.out.println("DIAGLINE FAIL: " + message);
            System.exit(1);
        }
    }
}
