import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.Degraded;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.Resolution;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.ProsperityBiomes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityWorldGenerator;
import com.miaokatze.gtsr.common.dimension.shattered.ChunkProviderShatteredGrounds;
import com.miaokatze.gtsr.common.dimension.shattered.WorldProviderShatteredLands;
import com.miaokatze.gtsr.common.dimension.shattered.biome.ShatteredBiomes;
import com.miaokatze.gtsr.config.Config;

/**
 * <b>P1 群系配槽与身份出口——运行时路径级离线断言</b>（dim78 体系化重构 P1，plan §5 P1 成功判据）。
 * <p>
 * 与 v1.20.30 的 {@code ReplaceSurfaceRuntimeCheck} 的关键区别：本工具<b>走真实注册链</b>
 * （{@code ProsperityBiomes.init} / {@code ShatteredBiomes.init} → {@code GTSRBiomeBase.allocate}
 * → {@code new BiomeXxx(actualId)} → {@code BiomeGenBase} 构造自占 {@code biomeList} 槽），
 * <b>不</b>手工 {@code new BiomeRustedSteppe(183)} 绕过空闲校验与槽位竞争——绕过正是上一轮
 * "表层断言全绿、实机全 no-op" 假绿的根因（教训见 v12030 报告 §3）。占位场景用
 * {@code new BiomeGenBase(id)} 真实注册他方群系实例（vanilla 构造撞号即抛
 * "Double biome register"，故本工具的占位与真实整合包同构）。
 * <p>
 * 四场景（<b>一个场景一个 JVM</b>——biomeList 与 L1 账本都是进程级全局且占槽不可回退，
 * 同 JVM 跑多场景会互相污染，这是刻意的设计而不是遗漏）：
 * <table border="1">
 * <caption>场景</caption>
 * <tr><th>A 正常</th><td>无外部占位 ⇒ 四群系各得首选槽、degraded=NONE；L1 与 chunk manager
 * 同源；权重对拍（改造前 {@code id-idStart} 减法 vs 改造后 {@code ordinalAt().ordinal}）逐位一致。</td></tr>
 * <tr><th>B 短表</th><td>预占 180/181/182 为实机日志里的真实占用者（Firefly Forest / Mushroom
 * Forest / Oak Savanna）⇒ <b>配槽顺延到 183/184/185/186 且四群系全注册成功</b>（旧实现此时只剩
 * 1 个），degraded=NONE。</td></tr>
 * <tr><th>C 空表</th><td>预占 190..254（含顺延可达的后续槽）⇒ shattered degraded=EMPTY、
 * def 群系表为空、{@code biomeAt}/{@code getBiomesForGeneration}/{@code loadBlockGeneratorData}
 * 一律返回 <b>null 而非 plains</b>（本片核心行为改变）；同 JVM 内 prosperity 仍正常 ⇒ 证明
 * 两维配槽互不影响。</td></tr>
 * <tr><th>D 别名防线</th><td>断言上界常量与哨兵：{@code idCeiling()==254}、255/256 一律判不出槽、
 * {@code idStart=256} 与"Config 收紧到 250"都不得产生 ≥255 的 id；并用 byte 掩码实测
 * id≥256 的<b>静默别名</b>（260 读回 4）作为硬上界的机制级证据；另造 SHORT 态（idStart=252）。</td></tr>
 * </table>
 * <p>
 * <b>运行配方</b>（JEP330 单文件不够——需要 MC/Forge 类；沿用 v12030 报告 §3 的 classpath，
 * 并把新写的源码目录顶在前面以覆盖 jar 内旧类）：
 * <pre>
 * G=$(cygpath -w ~/.gradle/caches/modules-2/files-2.1)
 * CP="build\\classes\\java\\main;build\\classes\\java\\patchedMc;$G\\com.google.guava\\guava\\17.0\\...\\guava-17.0.jar;\
 * $G\\org.apache.commons\\commons-lang3\\3.3.2\\...\\commons-lang3-3.3.2.jar;\
 * $G\\org.apache.logging.log4j\\log4j-api\\2.0-beta9-fixed\\...\\log4j-api-...jar;\
 * $G\\org.apache.logging.log4j\\log4j-core\\2.0-beta9-fixed\\...\\log4j-core-...jar"
 * # 1) 编译本片改动（gradlew 全量构建本片禁止，故单文件级 javac）
 * MSYS2_ARG_CONV_EXCL='*' javac -encoding UTF-8 -nowarn -cp "$CP" -d temp/p1 \
 *   src/main/java/com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeBase.java \
 *   src/main/java/com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeAuthority.java \
 *   src/main/java/com/miaokatze/gtsr/common/dimension/framework/GTSRWorldChunkManager.java \
 *   src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/biome/ProsperityBiomes.java \
 *   src/main/java/com/miaokatze/gtsr/common/dimension/shattered/biome/ShatteredBiomes.java \
 *   src/main/java/com/miaokatze/gtsr/config/Config.java \
 *   src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java
 * # 2) 编译并运行断言（四场景各一次）
 * MSYS2_ARG_CONV_EXCL='*' javac -encoding UTF-8 -nowarn -cp "temp\p1;$CP" -d temp/bac tools/dim1/BiomeAllocationCheck.java
 * for S in A B C D; do MSYS2_ARG_CONV_EXCL='*' java -cp "temp/bac;temp\p1;$CP" BiomeAllocationCheck $S; done
 * </pre>
 * INFO 级日志由 {@link #enableOfflineLogging()} 在 {@code LogManager} 初始化前自举一份最小
 * log4j2 配置（写 {@code temp/biome-allocation-log4j2.xml}），使被断言代码的 {@code [GTSR]} 日志
 * 原文真的打到 stdout——汇总行/owner 快照行因此可被逐字核对，而不是工具自己转述。
 * <p>
 * 已知边界（诚实登记）：① {@code World}/{@code Chunk} 实例离线不可得，故"byte 平面写→读回"只用
 * {@code BiomeGenBase.getBiome((byte)id & 255)} 复现其掩码语义（本轮 javap {@code
 * Chunk.getBiomeGenForWorldCoords} 证实读面就是这条链），255 哨兵的"回调 + 写回落盘"分支只在
 * 源码级引用；② {@code DimensionRegistrar}/{@code WorldProvider} 需 FML 注册链，离线不可跑，
 * 故 {@code forDimension(dimId)} 用与生产同一 {@link GTSRBiomeAuthority#bind} 入口绑定后断言；
 * ③ 实机三态（{@code -Dgtsr.dimprobe=1/2/3}）与 {@code GTSRChunkProviderBase.provideChunk:121-124}
 * 在 EMPTY 态的 null 守卫（P2 责任）本片均未执行/未修改。
 */
public class BiomeAllocationCheck {

    private static final long SEED = 0x50524F53L;
    /** dim78 首选段（Config 默认值；场景内会临时改写）。 */
    private static final int PROSPERITY_START_DEFAULT = 180;
    /** dim79 首选段。 */
    private static final int SHATTERED_START_DEFAULT = 190;
    /** 空间采样网格（chunk 坐标 -SAMPLE..SAMPLE）。 */
    private static final int SAMPLE = 60;

    private static int passed;
    private static int failures;

    public static void main(String[] args) throws Exception {
        enableOfflineLogging();
        final String scenario = args.length > 0 ? args[0].toUpperCase() : "A";
        System.out.println("=== BiomeAllocationCheck scenario " + scenario + " ===");
        switch (scenario) {
            case "A":
                scenarioANormal();
                break;
            case "B":
                scenarioBSliding();
                break;
            case "C":
                scenarioCEmpty();
                break;
            case "D":
                scenarioDAliasGuard();
                break;
            default:
                System.out.println("UNKNOWN SCENARIO: " + scenario);
                System.exit(2);
        }
        if (failures > 0) {
            System.out.println("BIOME ALLOCATION FAIL (" + scenario + "): " + failures + " assertion(s) failed, passed="
                + passed);
            System.exit(1);
        }
        System.out.println("BIOME ALLOCATION PASS (" + scenario + "): assertions=" + passed);
    }

    // ————— 场景 A：正常态（无外部占位） —————

    private static void scenarioANormal() {
        assertCeilingPrimitives();
        final GTSRDimensionDef def78 = prosperityDef();
        final GTSRDimensionDef def79 = shatteredDef();
        ProsperityBiomes.init(def78);
        ShatteredBiomes.init(def79);

        final GTSRBiomeAuthority a78 = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        final GTSRBiomeAuthority a79 = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_SHATTERED);
        check(a78.degraded() == Degraded.NONE, "A degraded=NONE for dim78, got " + a78.degraded());
        check(a79.degraded() == Degraded.NONE, "A degraded=NONE for dim79, got " + a79.degraded());
        // v1.20.39 T5/T8 重钉（plan §3.3）：账本口径 4→5 元——第 5 元 SANZU_RIVER 经同一配槽机制入账
        //（首选 184=idStart+4），但<b>不挂 def 群系表</b>（def 表保持 4 项 selector 名册）。
        check(a78.allocationSummary().equals("180->180, 181->181, 182->182, 183->183, 184->184"),
            "A dim78 allocation summary drift: " + a78.allocationSummary());
        check(a79.allocationSummary().equals("190->190, 191->191, 192->192, 193->193"),
            "A dim79 allocation summary drift: " + a79.allocationSummary());
        check(def78.getBiomeTable().size() == 4, "A dim78 def biome table size=" + def78.getBiomeTable().size());
        checkWeights(def78.getBiomeWeights(), new int[] { 45, 30, 15, 10 }, "A dim78 roster weights");
        checkWeights(def79.getBiomeWeights(), new int[] { 40, 30, 20, 10 }, "A dim79 roster weights");

        // 名册顺序 == 首选槽顺序（注册链未被改动的前置事实）；sanzu（T5 第 5 元）单独补钉：
        // 账本可解析 + 落在首选 184 + 实例<b>不在</b> def 表（roster-only，plan §3.3 的反向钉）。
        final BiomeId[] roster78 = { BiomeId.RUSTED_STEPPE, BiomeId.GEARWORK_FOREST, BiomeId.BRASS_WASTES,
            BiomeId.FUMAROLE_SWAMP };
        for (int i = 0; i < 4; i++) {
            check(a78.actualIdOf(roster78[i]) == PROSPERITY_START_DEFAULT + i,
                "A " + roster78[i] + " actualId=" + a78.actualIdOf(roster78[i]));
            check(a78.biomeOf(roster78[i]) == def78.getBiomeTable()
                .get(i), "A ledger instance != def table entry #" + i);
        }
        check(a78.actualIdOf(BiomeId.SANZU_RIVER) == PROSPERITY_START_DEFAULT + 4,
            "A SANZU_RIVER actualId=" + a78.actualIdOf(BiomeId.SANZU_RIVER));
        check(a78.biomeOf(BiomeId.SANZU_RIVER) != null && !def78.getBiomeTable()
            .contains(a78.biomeOf(BiomeId.SANZU_RIVER)),
            "A SANZU_RIVER must be ledger-resolvable but absent from the def (selector) table");
        // byte 平面往返（机制级：id<=254 时 (byte)id&255 读回同一实例）
        for (int i = 0; i < 4; i++) {
            final int id = a78.actualIdOf(roster78[i]);
            check(id <= GTSRBiomeBase.HARD_ID_MAX, "A allocated id exceeds ceiling: " + id);
            check(BiomeGenBase.getBiome(((byte) id) & 255) == a78.biomeOf(roster78[i]),
                "A byte-plane round trip not identity for id=" + id);
        }

        // L1 与生成期同源（同一张表、同一 selector）
        final GTSRWorldChunkManager mgr = new GTSRWorldChunkManager(SEED, def78);
        GTSRBiomeAuthority.bind(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, 78, mgr::biomeAt);
        final GTSRBiomeAuthority byDimId = GTSRBiomeAuthority.forDimension(78);
        check(byDimId == a78, "A forDimension(78) resolves to the dim78 authority");
        check(mgr.degraded() == Degraded.NONE && !mgr.isEmptyDegraded(), "A manager degraded=" + mgr.degraded());
        final Map<BiomeId, Integer> hits = new EnumMap<>(BiomeId.class);
        int mismatch = 0;
        int weightDrift = 0;
        final Map<Integer, Integer> oldIndexSeen = new java.util.TreeMap<>();
        for (int cx = -SAMPLE; cx <= SAMPLE; cx++) {
            for (int cz = -SAMPLE; cz <= SAMPLE; cz++) {
                final BiomeGenBase generated = mgr.biomeAt(cx, cz);
                final Resolution r = byDimId.ordinalAt((cx << 4) + 8, (cz << 4) + 8);
                if (generated == null || !r.resolved() || r.biome != generated) {
                    mismatch++;
                    continue;
                }
                hits.merge(r.biomeId, 1, Integer::sum);
                // 权重对拍：改造前的减法口径 vs 改造后的 L1 口径，走同一段取表代码
                final int oldIndex = generated.biomeID - PROSPERITY_START_DEFAULT;
                oldIndexSeen.merge(oldIndex, 1, Integer::sum);
                if (ProsperityWorldGenerator.weightForRosterIndex(oldIndex, ProsperityWorldGenerator.MACHINE_WEIGHTS)
                    != ProsperityWorldGenerator.weightForRosterIndex(r.ordinal, ProsperityWorldGenerator.MACHINE_WEIGHTS)
                    || ProsperityWorldGenerator.weightForRosterIndex(oldIndex, ProsperityWorldGenerator.SCATTER_WEIGHTS)
                        != ProsperityWorldGenerator
                            .weightForRosterIndex(r.ordinal, ProsperityWorldGenerator.SCATTER_WEIGHTS)) {
                    weightDrift++;
                }
            }
        }
        check(mismatch == 0, "A L1 resolution differs from chunk generation on " + mismatch + " chunks");
        check(hits.size() == 4, "A all four prosperity biomes reachable in " + sampledChunks() + " chunks, hits=" + hits);
        check(weightDrift == 0,
            "A weight parity (old id-idStart subtraction vs new L1 ordinal) drift=" + weightDrift
                + " oldIndexSeen=" + oldIndexSeen);
        check(oldIndexSeen.size() == 4, "A old subtraction covered 4 distinct indices: " + oldIndexSeen.keySet());
        for (final BiomeId id : roster78) {
            check(hits.getOrDefault(id, 0) > 0, "A biome absent: " + id);
        }
        System.out.println("A distribution (chunks): " + hits + " ; allocation=[" + a78.allocationSummary()
            + "] degraded=" + a78.degraded());
    }

    // ————— 场景 B：短表占位 ⇒ 逐群系顺延（本片核心行为改变） —————

    private static void scenarioBSliding() {
        assertCeilingPrimitives();
        // 用户实机 log.txt:10908-10916 的三个真实占用者（名字逐字取自日志）
        occupy(180, "Firefly Forest");
        occupy(181, "Mushroom Forest");
        occupy(182, "Oak Savanna");
        check(!GTSRBiomeBase.isBiomeIdFree(180), "B slot 180 must read as taken after occupation");

        final GTSRDimensionDef def78 = prosperityDef();
        ProsperityBiomes.init(def78);
        final GTSRBiomeAuthority a78 = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        // 核心：四群系全部注册（旧实现此处只有 183 一个，即"1/4 slots free"）；
        // T5/T8 重钉：账本口径 5 元——sanzu（首选 184 被顺延中的 forest 占用）继续滑到 187。
        check(def78.getBiomeTable().size() == 4,
            "B all four biomes must register via sliding, table=" + def78.getBiomeTable().size());
        check(a78.allocatedCount() == 5, "B ledger allocated=" + a78.allocatedCount());
        check(a78.degraded() == Degraded.NONE, "B degraded=NONE expected (all roster got slots), got " + a78.degraded());
        check(a78.allocationSummary().equals("180->183, 181->184, 182->185, 183->186, 184->187"),
            "B non-contiguous sliding summary drift: " + a78.allocationSummary());
        final int[] expected = { 183, 184, 185, 186 };
        final BiomeId[] roster78 = { BiomeId.RUSTED_STEPPE, BiomeId.GEARWORK_FOREST, BiomeId.BRASS_WASTES,
            BiomeId.FUMAROLE_SWAMP };
        for (int i = 0; i < 4; i++) {
            final int actual = a78.actualIdOf(roster78[i]);
            check(actual == expected[i], "B " + roster78[i] + " expected " + expected[i] + " got " + actual);
            check(actual > 180 + i, "B " + roster78[i] + " must slide forward from its preferred id");
            check(actual <= GTSRBiomeBase.HARD_ID_MAX, "B allocated id beyond ceiling: " + actual);
        }
        // owner 快照（L8）：被占首选槽的占用者名必须入账
        final String owners = a78.occupantSummary();
        check(owners.contains("180=other:Firefly Forest") && owners.contains("181=other:Mushroom Forest")
            && owners.contains("182=other:Oak Savanna"), "B owner snapshot missing real occupants: " + owners);
        // 顺延后的身份仍正确：旧减法在 186 上失真（186-180=6 越界 ⇒ 权重恒 1.0）
        final GTSRWorldChunkManager mgr = new GTSRWorldChunkManager(SEED, def78);
        GTSRBiomeAuthority.bind(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, 78, mgr::biomeAt);
        final GTSRBiomeAuthority l1 = GTSRBiomeAuthority.forDimension(78);
        final Map<BiomeId, Integer> hits = new EnumMap<>(BiomeId.class);
        int oldDegraded = 0;
        for (int cx = -SAMPLE; cx <= SAMPLE; cx++) {
            for (int cz = -SAMPLE; cz <= SAMPLE; cz++) {
                final Resolution r = l1.ordinalAt((cx << 4) + 8, (cz << 4) + 8);
                if (!r.resolved() || r.biome != mgr.biomeAt(cx, cz)) {
                    fail("B L1 resolution wrong at chunk " + cx + "," + cz + ": " + r);
                    return;
                }
                hits.merge(r.biomeId, 1, Integer::sum);
                final int oldIndex = r.biome.biomeID - PROSPERITY_START_DEFAULT;
                if (ProsperityWorldGenerator.weightForRosterIndex(oldIndex, ProsperityWorldGenerator.MACHINE_WEIGHTS)
                    == 1.0F && ProsperityWorldGenerator.MACHINE_WEIGHTS[r.ordinal] != 1.0F) {
                    oldDegraded++;
                }
            }
        }
        check(hits.size() == 4, "B four biomes still all reachable after sliding, hits=" + hits);
        check(oldDegraded > 0,
            "B the pre-P1 subtraction must be demonstrably wrong for slid ids (expected >0 fallback-to-1.0 chunks)");
        System.out.println("B allocation=[" + a78.allocationSummary() + "] degraded=" + a78.degraded()
            + " ; old-substitution would have degraded " + oldDegraded + " of " + sampledChunks() + " chunks");
    }

    // ————— 场景 C：空表 ⇒ degraded=EMPTY 且不回退 plains —————

    private static void scenarioCEmpty() {
        assertCeilingPrimitives();
        // dim79 首选段 + 顺延可达的全部后续槽（190..254）都预占：实机只占了 190-193，
        // 但本片改造后顺延能吃掉空槽，故必须占满整段才能验 EMPTY 这条底线
        occupy(190, "Eerie");
        occupy(191, "Eldritch");
        occupy(192, "Magical Forest");
        occupy(193, "Tainted Land");
        for (int id = 194; id <= GTSRBiomeBase.HARD_ID_MAX; id++) {
            occupy(id, "Reserved Band " + id);
        }
        // 两维互不影响：shattered 全被占的同时 prosperity 仍应正常四槽
        final GTSRDimensionDef def78 = prosperityDef();
        ProsperityBiomes.init(def78);
        final GTSRDimensionDef def79 = shatteredDef();
        ShatteredBiomes.init(def79);

        final GTSRBiomeAuthority a78 = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        final GTSRBiomeAuthority a79 = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_SHATTERED);
        check(a78.degraded() == Degraded.NONE && def78.getBiomeTable().size() == 4,
            "C dim78 unaffected by dim79 starvation: table=" + def78.getBiomeTable().size() + " " + a78.degraded());
        check(def79.getBiomeTable().isEmpty(), "C dim79 biome table must be empty, size=" + def79.getBiomeTable()
            .size());
        check(a79.allocatedCount() == 0, "C dim79 allocated=" + a79.allocatedCount());
        check(a79.degraded() == Degraded.EMPTY, "C dim79 degraded=EMPTY expected, got " + a79.degraded());
        check(a79.allocationSummary()
            .equals("190->NONE, 191->NONE, 192->NONE, 193->NONE"),
            "C empty allocation summary drift: " + a79.allocationSummary());

        final GTSRWorldChunkManager mgr = new GTSRWorldChunkManager(SEED, def79);
        check(mgr.isEmptyDegraded(), "C manager.isEmptyDegraded() true");
        check(mgr.degraded() == Degraded.EMPTY, "C manager.degraded()=EMPTY, got " + mgr.degraded());
        // 关键回归：空表不得回退 plains（改造前此处恒返回 BiomeGenBase.plains）
        check(mgr.biomeAt(0, 0) == null, "C biomeAt must be null (no plains fallback), got " + mgr.biomeAt(0, 0));
        check(mgr.getBiomeGenAt(8, 8) == null, "C getBiomeGenAt must be null in EMPTY");
        int plainsSeen = 0;
        final BiomeGenBase[] gen = mgr.getBiomesForGeneration(null, 0, 0, 16, 16);
        final BiomeGenBase[] loaded = mgr.loadBlockGeneratorData(null, 0, 0, 16, 16);
        for (int i = 0; i < 256; i++) {
            if (gen[i] == BiomeGenBase.plains || loaded[i] == BiomeGenBase.plains) {
                plainsSeen++;
            }
        }
        check(plainsSeen == 0, "C EMPTY table must not surface plains anywhere, seen=" + plainsSeen);
        boolean allNull = true;
        for (final BiomeGenBase b : gen) {
            allNull &= b == null;
        }
        check(allNull, "C getBiomesForGeneration entries must all be null in EMPTY");
        final float[] rain = mgr.getRainfall(null, 0, 0, 16, 16);
        check(rain != null && rain.length == 256, "C getRainfall survives EMPTY (no NPE) len=" + (rain == null ? -1
            : rain.length));
        GTSRBiomeAuthority.bind(GTSRBiomeAuthority.DIM_KEY_SHATTERED, 79, mgr::biomeAt);
        final Resolution r = GTSRBiomeAuthority.forDimension(79).ordinalAt(8, 8);
        check(!r.resolved() && r.ordinal == -1 && r.degraded == Degraded.EMPTY && r.biome == null,
            "C ordinalAt must expose EMPTY, not a fake biome: " + r);
        check(ProsperityWorldGenerator.weightForRosterIndex(r.ordinal, ProsperityWorldGenerator.MACHINE_WEIGHTS) == 1.0F,
            "C consumers fall back to weight 1.0 under EMPTY");
        check(!mgr.areBiomesViable(0, 0, 8, new ArrayList<>()), "C areBiomesViable false under EMPTY (no crash)");
        System.out.println("C dim79 allocation=[" + a79.allocationSummary() + "] degraded=" + a79.degraded()
            + " ; dim78 unaffected=[" + a78.allocationSummary() + "] degraded=" + a78.degraded());
    }

    // ————— 场景 D：byte 平面别名防线与上界常量 —————

    private static void scenarioDAliasGuard() {
        assertCeilingPrimitives();
        check(GTSRBiomeBase.HARD_ID_MAX == 254, "D HARD_ID_MAX must be 254, got " + GTSRBiomeBase.HARD_ID_MAX);
        check(GTSRBiomeBase.NO_SLOT == -1, "D NO_SLOT sentinel drift");
        // 255 是 vanilla Chunk byte 平面的懒回填哨兵：空闲也不得判可用
        check(BiomeGenBase.getBiome(255) == null, "D slot 255 is free in this JVM (precondition)");
        check(!GTSRBiomeBase.isBiomeIdFree(255), "D 255 must stay unusable (lazy-backfill sentinel)");
        check(GTSRBiomeBase.allocate(255, 300) == GTSRBiomeBase.NO_SLOT, "D allocate(255,300) must refuse the sentinel");
        check(GTSRBiomeBase.allocate(254, 300) == 254, "D allocate(254,300) must yield exactly 254");
        // 别名机制实测：id>=256 经 byte 掩码后指向别的群系（这就是硬上界存在的原因）
        final int aliasId = 260;
        final int readBack = ((byte) aliasId) & 255;
        final BiomeGenBase aliasTarget = BiomeGenBase.getBiome(readBack);
        check(readBack == aliasId - 256, "D byte masking alias evidence: " + aliasId + " -> " + readBack);
        check(aliasTarget != null && aliasTarget.biomeID == readBack,
            "D aliased slot resolves to a foreign biome instance: " + readBack + "="
                + (aliasTarget == null ? "null" : aliasTarget.biomeName));
        System.out.println("D alias evidence: id " + aliasId + " stored as byte reads back as " + readBack + " = '"
            + (aliasTarget == null ? "null" : aliasTarget.biomeName) + "' (silent alias, hence ceiling 254)");

        // ① idStart=256（越界配置）⇒ 一个槽都不给，且绝不产出 >=255 的 id
        Config.prosperityBiomeIdStart = 256;
        final GTSRDimensionDef defA = prosperityDef();
        ProsperityBiomes.init(defA);
        final GTSRBiomeAuthority a78 = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        check(defA.getBiomeTable().isEmpty(), "D idStart=256 must allocate nothing, table=" + defA.getBiomeTable()
            .size());
        check(a78.degraded() == Degraded.EMPTY, "D idStart=256 degraded=EMPTY, got " + a78.degraded());
        assertNoHighBiomeIds();

        // ② Config 收紧到 250（首选段 251 之上）⇒ 同样只能判无槽（Config 只能收紧不能放宽）
        Config.prosperityBiomeIdStart = 251;
        Config.biomeIdScanLimit = 250;
        final GTSRDimensionDef defB = prosperityDef();
        ProsperityBiomes.init(defB);
        check(defB.getBiomeTable().isEmpty(),
            "D tightened scanLimit=250 must allocate nothing from 251, table=" + defB.getBiomeTable().size());
        check(a78.degraded() == Degraded.EMPTY, "D tightened-limit degraded=EMPTY, got " + a78.degraded());
        assertNoHighBiomeIds();
        Config.biomeIdScanLimit = 254;

        // ③ idStart=252 ⇒ 只拿得到 252/253/254 三槽，沼泽与 sanzu（T5/T8 第 5 元）无槽 ⇒ SHORT
        Config.prosperityBiomeIdStart = 252;
        final GTSRDimensionDef defC = prosperityDef();
        ProsperityBiomes.init(defC);
        check(defC.getBiomeTable().size() == 3, "D idStart=252 must register exactly 3, table=" + defC.getBiomeTable()
            .size());
        check(a78.degraded() == Degraded.SHORT, "D idStart=252 degraded=SHORT, got " + a78.degraded());
        check(a78.allocationSummary()
            .equals("252->252, 253->253, 254->254, 255->NONE, 256->NONE"),
            "D SHORT summary drift: " + a78.allocationSummary());
        assertNoHighBiomeIds();
        System.out.println("D short-table allocation=[" + a78.allocationSummary() + "] degraded=" + a78.degraded());
        Config.prosperityBiomeIdStart = PROSPERITY_START_DEFAULT;
    }

    /** 全注册表扫描：我方群系的 id 决不允许 ≥255（含 255 自身）。 */
    private static void assertNoHighBiomeIds() {
        final BiomeGenBase[] table = BiomeGenBase.getBiomeGenArray();
        int ours = 0;
        boolean violation = false;
        for (int id = 0; id < table.length; id++) {
            final BiomeGenBase b = table[id];
            if (b instanceof GTSRBiomeBase) {
                ours++;
                if (id > GTSRBiomeBase.HARD_ID_MAX) {
                    violation = true;
                }
            }
        }
        check(!violation, "D no gtsr biome may sit above id 254 (table length=" + table.length + ", ours=" + ours
            + ")");
        check(table.length - 1 >= GTSRBiomeBase.HARD_ID_MAX,
            "D registry table (" + table.length + ") covers the byte-plane ceiling");
    }

    /** 三场景共用的上界/空闲原语断言。 */
    private static void assertCeilingPrimitives() {
        check(GTSRBiomeBase.idCeiling() == Math.min(254, BiomeGenBase.getBiomeGenArray().length - 1),
            "CEIL idCeiling = min(254, tableLen-1), got " + GTSRBiomeBase.idCeiling());
        check(GTSRBiomeBase.idCeiling() <= GTSRBiomeBase.HARD_ID_MAX,
            "CEIL idCeiling never exceeds the hard ceiling: " + GTSRBiomeBase.idCeiling());
        check(!GTSRBiomeBase.isBiomeIdFree(-1), "CEIL isBiomeIdFree(-1) false");
        check(!GTSRBiomeBase.isBiomeIdFree(255), "CEIL isBiomeIdFree(255) false (sentinel)");
        check(!GTSRBiomeBase.isBiomeIdFree(256), "CEIL isBiomeIdFree(256) false (beyond byte plane)");
        check(!GTSRBiomeBase.isBiomeIdFree(1), "CEIL slot 1 (plains) reads as taken");
        check(GTSRBiomeBase.occupantName(1).equals("other:Plains"),
            "CEIL owner snapshot labels foreign biomes with other: prefix, got " + GTSRBiomeBase.occupantName(1));
        // 升序扫描语义：返回 [from..ceiling] 内第一个空闲槽（vanilla 占了 0..~40，故必然落在其上方）
        final int firstFree = GTSRBiomeBase.allocate(0, GTSRBiomeBase.HARD_ID_MAX);
        check(GTSRBiomeBase.isBiomeIdFree(firstFree) && firstFree >= 0 && firstFree <= GTSRBiomeBase.HARD_ID_MAX,
            "allocate(0,254) yields the first free slot in range, got " + firstFree);
    }

    // ————— def 构造（逐字镜像 CommonProxy 的接线；B2 起无 selector，身份 = 链） —————

    private static GTSRDimensionDef prosperityDef() {
        final GTSRDimensionDef def = new GTSRDimensionDef(
            GTSRBiomeAuthority.DIM_KEY_PROSPERITY, "Prosperity Ruins", SEED, 78, 178, () -> true,
            WorldProviderProsperityRuins.class, ChunkProviderProsperityRuins::new);
        return def;
    }

    private static GTSRDimensionDef shatteredDef() {
        final GTSRDimensionDef def = new GTSRDimensionDef(
            GTSRBiomeAuthority.DIM_KEY_SHATTERED, "Shattered Lands", 0x53484C53L, 79, 179, () -> true,
            WorldProviderShatteredLands.class, ChunkProviderShatteredGrounds::new);
        return def;
    }

    /** 用真实注册链预占一个槽（与外部 mod 抢占同构：撞号时 vanilla 构造直接抛）。 */
    private static void occupy(int id, String name) {
        if (BiomeGenBase.getBiome(id) != null) {
            fail("precondition: slot " + id + " already taken by " + GTSRBiomeBase.occupantName(id));
            return;
        }
        new Foreign(id, name);
        if (BiomeGenBase.getBiome(id) == null) {
            fail("precondition: occupying " + id + " did not register");
        }
    }

    private static final class Foreign extends BiomeGenBase {

        Foreign(int id, String name) {
            super(id);
            setBiomeName(name);
        }
    }

    // ————— 断言与自举工具 —————

    private static int sampledChunks() {
        return (2 * SAMPLE + 1) * (2 * SAMPLE + 1);
    }

    private static void checkWeights(int[] actual, int[] expected, String what) {
        final List<Integer> a = new ArrayList<>();
        for (final int v : actual) {
            a.add(v);
        }
        final List<Integer> e = new ArrayList<>();
        for (final int v : expected) {
            e.add(v);
        }
        check(a.equals(e), what + " expected " + e + " got " + a);
    }

    private static void check(boolean ok, String msg) {
        if (ok) {
            pass(msg);
        } else {
            fail(msg);
        }
    }

    private static void pass(String msg) {
        passed++;
        System.out.println("PASS: " + msg);
    }

    private static void fail(String msg) {
        failures++;
        System.out.println("FAIL: " + msg);
    }

    /**
     * 让被断言代码的 {@code [GTSR]} INFO 日志真的打出来（log4j2 无配置时默认只到 ERROR）。
     * 必须在任何 {@code LogManager} 初始化之前调用，故放在 main 第一行。失败只影响日志样本，
     * 不影响断言。
     */
    private static void enableOfflineLogging() {
        try {
            final Path cfg = Paths.get("temp", "biome-allocation-log4j2.xml");
            if (cfg.getParent() != null) {
                Files.createDirectories(cfg.getParent());
            }
            Files.write(
                cfg,
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<Configuration status=\"OFF\">\n"
                    + "  <Appenders><Console name=\"STDOUT\" target=\"SYSTEM_OUT\">"
                    + "<PatternLayout pattern=\"%level{length=1} %m%n\"/></Console></Appenders>\n"
                    + "  <Loggers><Root level=\"info\"><AppenderRef ref=\"STDOUT\"/></Root></Loggers>\n"
                    + "</Configuration>\n").getBytes(StandardCharsets.UTF_8));
            System.setProperty("log4j.configurationFile", cfg.toAbsolutePath().toString());
        } catch (Exception e) {
            System.out.println("NOTE: offline log4j2 bootstrap failed (" + e.getClass().getSimpleName()
                + ") — [GTSR] log lines may be suppressed, assertions unaffected");
        }
    }
}
