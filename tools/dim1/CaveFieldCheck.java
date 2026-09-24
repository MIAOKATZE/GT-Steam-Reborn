import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRCaveCarver;
import com.miaokatze.gtsr.common.dimension.framework.cave.ProsperityCaveField;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.TerrainVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeRustedSteppe;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * dim78 洞穴场判据（P22 版 B · S1a 起 A / WAVE / SINGLE 三组；<b>S1b 追加 CARVE / SOURCE / PERF
 * 三组并入链 {@code surface_checks.sh} [2l]</b>）。
 *
 * <p>
 * ═══ S1b 三组口径 ═══
 * <ul>
 * <li><b>CARVE 组（写入器行为）</b>：合成 12×12 chunk 全固体列阵（真实 bedrockTopHash 基岩带 +
 * 主体 prosperityStone + top/filler 表层位 + 整带 cobblestone 哨兵），跑真实
 * {@link GTSRCaveCarver#carve} 前后逐格对账——白名单外一格不动、lava 只在 y&lt;10、
 * 水体保护列（heightAt+3 壳）下无穿透、岛柱保护列零变化、foundTop⇒下一格 filler 改写 top 的
 * 行为在场 + 后效无"挖空格压在 filler 上"。</li>
 * <li><b>SOURCE 组（源级红线，p21 §1.2/§1.4）</b>：两新类 + 覆写体去注释后
 * {@code getBiomeGenForCoords}/{@code worldObj.}/{@code <<4} 三负形状 0 命中；成对正形状
 * {@code biomeAtColumn(}、column|y 下标形态、{@code Blocks.lava}、{@code CAVE_ENABLED} 在场；
 * 另钉基类挂点行的<b>位置序</b>（replaceBlocksForBiome &lt; carveCaves &lt; new Chunk）。</li>
 * <li><b>PERF 组（carver 成本门，p21 §1.7）</b>：per-chunk carve 中位 µs，门 =
 * {@code CARVE_BASELINE × 1.30} 派生式（写法同 {@code GenBenchCheck:41}）；首测立 BASE 入
 * {@code plan/tmp/p22-s1b/PROGRESS.md}。串行纪律沿用（单线程、不并发）。成对反假绿：计时趟
 * 必须真挖（dug&gt;0）。</li>
 * </ul>
 *
 * <p>
 * ═══ 三组口径 ═══
 * <ul>
 * <li><b>A 组（形态）</b>：管束域外恒假 + 公式双源对拍；密度门早出读数（目标带 82–86% 只报不设门，
 * 硬门 = 开区间 (0,50%) 防"整段没接线"）；峡谷深度带 [MIN,MAX] 与谷外恒 0；破面许可 ↔ 封顶对偶
 * （=surface / ≤surface−3 双态都在）；水体保护列 carve 下界 = {@code max(CAVE_Y_MIN, heightAt+3)}
 * <b>五腿独立重算双源对拍</b>（壳底钉 heightAt，R4 禁"挖前地面"）；岛域抽检（湖心反解 → 保护真 +
 * 非岛列重算假）。</li>
 * <li><b>WAVE 组（波长纪律）</b>：<b>读 {@code ProsperityCaveField.java} 源字面值</b>（正则抓
 * {@code *SCALE|*CELL|*WAVE} 的 double 常量，不读派生量），逐个断言 {@code v > 16 && (int)v % 16 != 0}，
 * 并成对钉五个已知波长名必须在场（防"整个文件没写"式假绿）。</li>
 * <li><b>SINGLE 组</b>：同一 (seed,x,z)（与 y/roster）<b>双跑逐位同</b>：全部公开谓词两次独立趟，
 * double 走 {@code doubleToRawLongBits} 逐位比对。</li>
 * </ul>
 *
 * <p>
 * ═══ 成本行（先不设门）═══ 单 chunk 场求值中位耗时 + 密度早出率（S1b 立 BASE 后再上 ×1.30 门）。
 * 串行纪律沿用：单线程、不与其它 harness 并发。
 *
 * <p>
 * ═══ 出口 ═══ stdout 逐组打印 {@code A/WAVE/SINGLE} 判定与读数，末行
 * {@code CAVEFIELD assertions=<n> fail=<m>}；任何红 exit 1。
 */
public final class CaveFieldCheck {

    static final long SEED = 0x5EEDCA7EL;
    /** 被测类源文件（WAVE 组读字面值用；自仓根运行）。 */
    static final String FIELD_SOURCE =
        "src/main/java/com/miaokatze/gtsr/common/dimension/framework/cave/ProsperityCaveField.java";
    /** SOURCE 组被测源文件（S1b：写入器 + 基类挂点 + dim78 provider 覆写体所在文件）。 */
    static final String CARVER_SOURCE =
        "src/main/java/com/miaokatze/gtsr/common/dimension/framework/GTSRCaveCarver.java";
    static final String BASE_SOURCE =
        "src/main/java/com/miaokatze/gtsr/common/dimension/framework/GTSRChunkProviderBase.java";
    static final String PROVIDER_SOURCE =
        "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ChunkProviderProsperityRuins.java";
    /** WAVE 组成对钉：已知波长名必须全部在场。 */
    static final String[] REQUIRED_WAVES = {
        "CAVE_DENSITY_SCALE", "CAVE_TUBE_SCALE", "CAVE_BREAK_SCALE", "CANYON_CELL", "CANYON_WIDTH_WAVE" };
    /**
     * PERF 基线（µs/chunk，carve 段中位实测；S1b 首测三连跑读数 48/37/42µs，取首轮 48 立 BASE，
     * 出处 {@code plan/tmp/p22-s1b/PROGRESS.md} 与 {@code temp/p22-s1b-cf-r1..3.out}）。
     * 门 = BASE × 1.30 派生式（写法同 {@code GenBenchCheck:41}）；口径 = 合成固体列阵、
     * 单线程串行、构造成本不入计时窗。
     */
    static final double CARVE_BASELINE_US_PER_CHUNK = 48.0D;
    /** 派生式对赌门（劣化 &gt;30% 红）。 */
    static final double CARVE_GATE_US_PER_CHUNK = CARVE_BASELINE_US_PER_CHUNK * 1.30D;

    static int total;
    static int fails;

    private CaveFieldCheck() {}

    public static void main(String[] args) throws IOException {
        attachLedger();
        groupA();
        groupWave();
        groupSingle();
        groupCarve();
        groupSource();
        groupPerf();
        costLine();
        System.out.println("CAVEFIELD assertions=" + total + " fail=" + fails);
        if (fails > 0) {
            System.out.println("CAVEFIELD CHECKS: RED");
            System.exit(1);
        }
        System.out.println("CAVEFIELD CHECKS: GREEN");
    }

    /** 账本装配（GenBenchCheck 同形：dim78 四 selector 成员占 180..183，生产形状）。 */
    static void attachLedger() {
        for (int i = 0; i < 4; i++) {
            final BiomeGenBase b = new BiomeGenBase(180 + i) {};
            GTSRBiomeAuthority.recordAllocation(GTSRBiomeAuthority.BiomeId.values()[i], 180 + i, 180 + i, b);
        }
    }

    static void a(String group, String name, boolean ok) {
        total++;
        if (!ok) {
            fails++;
            System.out.println(group + " FAIL: " + name);
        }
    }

    static void read(String line) {
        System.out.println("  READ " + line);
    }

    // ════════════════════════ A 组：形态 ════════════════════════

    static void groupA() {
        // A1 域外恒假：竖向域 [CAVE_Y_MIN, CAVE_Y_MAX] 之外一律 false
        boolean outsideAllFalse = true;
        Lcg r = new Lcg(0xA1L);
        for (int i = 0; i < 200; i++) {
            final int x = r.intInRange(-4000, 4000);
            final int z = r.intInRange(-4000, 4000);
            final int yLow = r.intInRange(-20, ProsperityCaveField.CAVE_Y_MIN - 1);
            final int yHigh = r.intInRange(ProsperityCaveField.CAVE_Y_MAX + 1, 255);
            if (ProsperityCaveField.tubeAt(SEED, x, yLow, z) || ProsperityCaveField.tubeAt(SEED, x, yHigh, z)) {
                outsideAllFalse = false;
            }
        }
        a("A", "tube.outOfVerticalDomain.alwaysFalse", outsideAllFalse);

        // A2 公式双源：域内采样 400 格，tubeAt 与"按公开常量重算的原式"逐格一致；且管格存在
        int tubeHits = 0;
        boolean formulaMatch = true;
        int inDomain = 0;
        r = new Lcg(0xA2L);
        for (int i = 0; i < 400; i++) {
            final int x = r.intInRange(-3000, 3000);
            final int z = r.intInRange(-3000, 3000);
            final int y = r.intInRange(ProsperityCaveField.CAVE_Y_MIN, ProsperityCaveField.CAVE_Y_MAX);
            inDomain++;
            final double u = x / ProsperityCaveField.CAVE_TUBE_SCALE;
            final double w = z / ProsperityCaveField.CAVE_TUBE_SCALE;
            final double drift = ProsperityCaveField.CAVE_TUBE_DRIFT * y / ProsperityCaveField.CAVE_TUBE_SCALE;
            final double n1 = GTSRWorldgenHash.valueNoise(SEED ^ ProsperityCaveField.SALT_CAVE_TUBE_A, u + drift, w);
            final double n2 = GTSRWorldgenHash.valueNoise(SEED ^ ProsperityCaveField.SALT_CAVE_TUBE_B, u, w + drift);
            final boolean expect = Math.min(Math.abs(n1), Math.abs(n2)) < ProsperityCaveField.CAVE_TUBE_HOLE;
            final boolean got = ProsperityCaveField.tubeAt(SEED, x, y, z);
            if (expect != got) {
                formulaMatch = false;
            }
            if (got) {
                tubeHits++;
            }
        }
        a("A", "tube.formula.doubleSource", formulaMatch);
        a("A", "tube.existence.inSampledDomain(" + inDomain + ")", tubeHits > 0);
        read("A tubeHits=" + tubeHits + "/" + inDomain + " 域内采样管格率=" + pct(tubeHits, inDomain));

        // A3 密度门：早出率读数（目标带 82–86% 只报），硬门 (0%,50%) 开区间防不接线
        int open = 0;
        final int cols = 8192;
        r = new Lcg(0xA3L);
        for (int i = 0; i < cols; i++) {
            if (ProsperityCaveField.densityOpenAt(SEED, r.intInRange(-6000, 6000), r.intInRange(-6000, 6000))) {
                open++;
            }
        }
        final int closed = cols - open;
        a("A", "density.openRate.in(0,50%)", open > 0 && open < cols / 2);
        read("A density earlyExit=" + pct(closed, cols) + "（目标带 82–86%，只报不设门） openRate=" + pct(open, cols));

        // A4 峡谷：2048×2048 步距 2 → 深度带 + 谷外恒 0 + 存在性
        int canyonHits = 0;
        int canyonSamples = 0;
        boolean depthBandOk = true;
        for (int z = 0; z < 2048; z += 2) {
            for (int x = 0; x < 2048; x += 2) {
                final double d = ProsperityCaveField.canyonAt(SEED, x, z);
                canyonSamples++;
                if (d > 0.0D) {
                    canyonHits++;
                    if (!(d >= ProsperityCaveField.CANYON_DEPTH_MIN && d <= ProsperityCaveField.CANYON_DEPTH_MAX)) {
                        depthBandOk = false;
                    }
                } else if (d != 0.0D) {
                    depthBandOk = false; // 非谷列必须恒 0（NaN/负即红）
                }
            }
        }
        a("A", "canyon.depthBand[MIN,MAX]+outsideZero", depthBandOk);
        a("A", "canyon.existence", canyonHits >= 100);
        read("A canyon hits=" + canyonHits + "/" + canyonSamples + " 覆盖率=" + pct(canyonHits, canyonSamples));

        // A5 破面许可 ↔ 封顶对偶：surfaceY=70 双态
        int allowed = 0;
        boolean ceilingPairOk = true;
        final int sY = 70;
        r = new Lcg(0xA5L);
        for (int i = 0; i < 4096; i++) {
            final int x = r.intInRange(-5000, 5000);
            final int z = r.intInRange(-5000, 5000);
            final boolean ok = ProsperityCaveField.surfaceBreakAllowed(SEED, x, z);
            final int ceiling = ProsperityCaveField.carveCeilingAt(SEED, x, z, sY);
            if (ok) {
                allowed++;
                if (ceiling != sY) {
                    ceilingPairOk = false;
                }
            } else if (ceiling > sY - ProsperityCaveField.CAVE_SURFACE_CAP) {
                ceilingPairOk = false;
            }
        }
        a("A", "ceiling.pairToBreakAllowed(=surface / <=surface-3)", ceilingPairOk);
        a("A", "ceiling.bothStatesPresent", allowed > 0 && allowed < 4096);
        read("A surfaceBreak allowedRate=" + pct(allowed, 4096) + "（稀疏不成排，只报不设门）");

        // A6 水体保护：五腿独立重算双源对拍 + 壳 = max(CAVE_Y_MIN, heightAt+3) 逐列核
        long prot = 0;
        long colSamples = 0;
        boolean gatePairOk = true;
        for (int z = 0; z < 128; z += 2) {
            for (int x = 0; x < 512; x += 2) {
                final int rIdx = (((x >> 1) + (z >> 1)) & 1) == 0 ? -1 : 3; // 离线默认档与沼泽档都过一遍
                colSamples++;
                final boolean expect = waterLegExpect(SEED, x, z, rIdx);
                final boolean got = ProsperityCaveField.waterColumnProtected(SEED, x, z, rIdx);
                if (expect != got) {
                    gatePairOk = false;
                }
                final int h = ProsperityTerrainProfile.heightAt(SEED, x, z);
                final int floorExpect = got ? Math.max(ProsperityCaveField.CAVE_Y_MIN, h
                    + ProsperityCaveField.CAVE_LAKE_SHELL) : ProsperityCaveField.CAVE_Y_MIN;
                if (ProsperityCaveField.carveFloorY(SEED, x, z, rIdx) != floorExpect) {
                    gatePairOk = false;
                }
                if (got) {
                    prot++;
                }
            }
        }
        a("A", "waterGate.fiveLegs.doubleSource", gatePairOk);
        a("A", "waterGate.protectedExistence", prot > 0);
        read("A waterProtected cols=" + prot + "/" + colSamples + " 壳底=heightAt+"
            + ProsperityCaveField.CAVE_LAKE_SHELL + "（R4 禁挖前地面口径）");

        // A7 岛域抽检：湖心反解 → 岛心列保护必真 + 柱必真；另抽 128 非岛列重算必假
        int islands = 0;
        boolean islandProtectedOk = true;
        for (long s = 0; s < 8 && islands < 8; s++) {
            final long seed = 0x1AFEE7A9E5A7L + s * 0x9E37L;
            for (int z = -9000; z <= 9000 && islands < 8; z += 300) {
                for (int x = -9000; x <= 9000 && islands < 8; x += 300) {
                    if (GTSRVoronoiRiverField.trunkAt(seed, x, z) <= 0.0D) {
                        continue;
                    }
                    final double[] out = new double[4];
                    GTSRVoronoiRiverField.lakeCellCenterAt(seed, x, z, out);
                    if (out[2] == Integer.MIN_VALUE) {
                        continue;
                    }
                    final int cx = (int) Math.round(out[0]);
                    final int cz = (int) Math.round(out[1]);
                    if (GTSRVoronoiRiverField.lakeAt(seed, cx, cz) >= GTSRVoronoiRiverField.LAKE_ISLAND) {
                        continue;
                    }
                    islands++;
                    if (!ProsperityCaveField.protectedColumn(seed, cx, cz)) {
                        islandProtectedOk = false;
                    }
                    if (!GTSRVoronoiRiverField.islandPillarAt(seed, cx, cz)) {
                        islandProtectedOk = false; // 版一导出谓词自证：中心列=中心柱
                    }
                }
            }
        }
        a("A", "island.search.found(>=1)", islands >= 1);
        a("A", "island.protectedColumn.trueAtCenters", islandProtectedOk);
        int nonIslandChecked = 0;
        boolean nonIslandOk = true;
        r = new Lcg(0xA7L);
        for (int i = 0; i < 128; i++) {
            final int x = r.intInRange(-3000, 3000);
            final int z = r.intInRange(-3000, 3000);
            final boolean expect = islandUnionExpect(SEED, x, z);
            nonIslandChecked++;
            if (ProsperityCaveField.protectedColumn(SEED, x, z) != expect) {
                nonIslandOk = false;
            }
        }
        a("A", "protectedColumn.union.doubleSource(" + nonIslandChecked + ")", nonIslandOk);
        read("A islandsFound=" + islands);
    }

    /** 五腿并集独立重算（判据侧直接调版一 public 纯函数，字面阈值同源类常量）。 */
    static boolean waterLegExpect(long seed, int x, int z, int roster) {
        return GTSRVoronoiRiverField.swampRiverPoolAt(seed, x, z, roster) > 0.0D
            || GTSRVoronoiRiverField.swampLakeAt(seed, x, z, roster)
                < GTSRVoronoiRiverField.SWAMP_POOL_WATER_LEVEL
            || TerrainVariants.swampTierAt(seed, x, z, roster) != TerrainVariants.SWAMP_TIER_NONE
            || GTSRVoronoiRiverField.lakeAt(seed, x, z) < GTSRVoronoiRiverField.LAKE_WATER_LEVEL
            || GTSRVoronoiRiverField.trunkAt(seed, x, z) > 0.0D;
    }

    /** 岛∪外扩∪柱并集独立重算（与 protectedColumn 同式，判据侧复制以钉接线）。 */
    static boolean islandUnionExpect(long seed, int x, int z) {
        if (GTSRVoronoiRiverField.islandPillarAt(seed, x, z)) {
            return true;
        }
        final int g = ProsperityCaveField.CAVE_ISLAND_GUARD;
        for (int dz = -g; dz <= g; dz++) {
            for (int dx = -g; dx <= g; dx++) {
                if (GTSRVoronoiRiverField.lakeAt(seed, x + dx, z + dz) < GTSRVoronoiRiverField.LAKE_ISLAND) {
                    return true;
                }
            }
        }
        return false;
    }

    // ════════════════════════ WAVE 组：读源字面值 ════════════════════════

    static void groupWave() throws IOException {
        final String src = new String(Files.readAllBytes(Paths.get(FIELD_SOURCE)), StandardCharsets.UTF_8);
        final Matcher m = Pattern
            .compile("public static final double\\s+(\\w*(?:SCALE|CELL|WAVE))\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)D;")
            .matcher(src);
        final Set<String> seen = new LinkedHashSet<>();
        final List<String> bad = new ArrayList<>();
        while (m.find()) {
            final String name = m.group(1);
            final double v = Double.parseDouble(m.group(2));
            seen.add(name);
            read("WAVE " + name + " = " + v + " (>16=" + (v > 16.0D) + ", %16=" + ((int) v % 16) + ")");
            if (!(v > 16.0D && (int) v % 16 != 0)) {
                bad.add(name);
            }
        }
        a("WAVE", "source.literals.parsed(>=5)", seen.size() >= REQUIRED_WAVES.length);
        a("WAVE", "wavelength.v>16 && (int)v%16!=0  all=" + seen.size(), bad.isEmpty());
        for (final String req : REQUIRED_WAVES) {
            a("WAVE", "requiredWavePresent." + req, seen.contains(req)); // 成对钉：防整段没写式假绿
        }
        if (!bad.isEmpty()) {
            read("WAVE offenders=" + bad);
        }
    }

    // ════════════════════════ SINGLE 组：双跑逐位同 ════════════════════════

    static void groupSingle() {
        long firstDiff = -1;
        int checked = 0;
        final long[] seeds = { SEED, 0xC0FFEEL, 0xBEEF77L };
        for (int s = 0; s < seeds.length; s++) {
            final Lcg r = new Lcg(0x5151L + s);
            for (int i = 0; i < 400; i++) {
                final int x = r.intInRange(-7000, 7000);
                final int z = r.intInRange(-7000, 7000);
                final int y = r.intInRange(-20, 300);
                final int roster = r.intInRange(-1, 3);
                final long h1 = runFingerprint(seeds[s], x, y, z, roster);
                final long h2 = runFingerprint(seeds[s], x, y, z, roster);
                checked++;
                if (h1 != h2 && firstDiff < 0) {
                    firstDiff = checked;
                }
            }
        }
        a("SINGLE", "doubleRun.bitwise.identical n=" + checked, firstDiff < 0);
        if (firstDiff > 0) {
            read("SINGLE firstDiffSample#" + firstDiff);
        }
        read("SINGLE samples=" + checked + "（9 谓词/样本，double 走 doubleToRawLongBits 逐位）");
    }

    /** 单样本全谓词指纹（两趟独立求值后只比指纹，等价于逐位比）。 */
    static long runFingerprint(long seed, int x, int y, int z, int roster) {
        long f = 0x9E3779B97F4A7C15L;
        f = mix(f, ProsperityCaveField.densityOpenAt(seed, x, z) ? 1 : 0);
        f = mix(f, ProsperityCaveField.tubeAt(seed, x, y, z) ? 1 : 0);
        f = mix(f, Double.doubleToRawLongBits(ProsperityCaveField.canyonAt(seed, x, z)));
        f = mix(f, ProsperityCaveField.surfaceBreakAllowed(seed, x, z) ? 1 : 0);
        f = mix(f, ProsperityCaveField.carveCeilingAt(seed, x, z, 70));
        f = mix(f, ProsperityCaveField.waterColumnProtected(seed, x, z, roster) ? 1 : 0);
        f = mix(f, ProsperityCaveField.carveFloorY(seed, x, z, roster));
        f = mix(f, ProsperityCaveField.protectedColumn(seed, x, z) ? 1 : 0);
        f = mix(f, ProsperityCaveField.inVerticalDomain(y) ? 1 : 0);
        return f;
    }

    static long mix(long acc, long v) {
        return GTSRWorldgenHash.splitmix64(acc ^ v);
    }

    // ════════════════════════ S1b · CARVE 组：写入器行为（合成列阵逐格对账）════════════════════════

    /** CARVE 专用种：与 A7 同值（该种子已证含湖心岛 ⇒ 保护列在场可钉）。 */
    static final long CARVE_SEED = 0x1AFEE7A9E5A7L;

    /**
     * 离线 {@code Blocks.lava} 占位装配（配方同 {@code SurfaceHarness.setFinalStatic}：
     * 1.7.10 的 {@code Blocks.lava} 是 {@code blockRegistry.getObject("lava")}，离线注册表为空
     * ⇒ 恒 null；不装则 carver 的 lava 支写 null、判据无法与空气槽区分）。生产 JVM 此路径
     * 不存在（注册表实装），本装配只影响本判据进程。
     */
    static void installOfflineLava() {
        try {
            final sun.misc.Unsafe u = SurfaceHarness.unsafe();
            final java.lang.reflect.Field f = Blocks.class.getField("lava");
            u.putObject(u.staticFieldBase(f), u.staticFieldOffset(f), new net.minecraft.block.BlockStone());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("离线 lava 占位装配失败", e);
        }
    }
    /** 合成列地表高（竖窗内管束/峡谷/壳门全可触发）。 */
    static final int SYNTH_H = 120;
    static int carveCenterX;
    static int carveCenterZ;
    static BiomeGenBase carveBiome;

    static void groupCarve() {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        installOfflineLava();
        carveBiome = new BiomeRustedSteppe(200);
        final BiomeGenBase steppe = carveBiome;
        final Block top = steppe.topBlock;
        final Block filler = steppe.fillerBlock;
        final Block body = BlocksGTSR.prosperityStone;
        a("CARVE", "identity.instancesAssembled", top != null && filler != null && body != null
            && top != filler && body != top && body != filler && body != Blocks.stone);
        // 岛心反解（A7 同式）：12×12 chunk 域以岛心为原点 ⇒ 保护列（岛∪柱）与水体列（湖∪河）必在场
        boolean centerFound = false;
        outer:
        for (int z = -9000; z <= 9000; z += 300) {
            for (int x = -9000; x <= 9000; x += 300) {
                if (GTSRVoronoiRiverField.trunkAt(CARVE_SEED, x, z) <= 0.0D) {
                    continue;
                }
                final double[] out = new double[4];
                GTSRVoronoiRiverField.lakeCellCenterAt(CARVE_SEED, x, z, out);
                if (out[2] == Integer.MIN_VALUE) {
                    continue;
                }
                final int cx = (int) Math.round(out[0]);
                final int cz = (int) Math.round(out[1]);
                if (GTSRVoronoiRiverField.lakeAt(CARVE_SEED, cx, cz) >= GTSRVoronoiRiverField.LAKE_ISLAND) {
                    continue;
                }
                carveCenterX = cx;
                carveCenterZ = cz;
                centerFound = true;
                break outer;
            }
        }
        a("CARVE", "island.centerFound", centerFound);
        final int originChunkX = Math.floorDiv(carveCenterX, 16) - 5;
        final int originChunkZ = Math.floorDiv(carveCenterZ, 16) - 5;
        long changed = 0;
        long dugCells = 0;
        long dugCols = 0;
        long openCols = 0;
        long lava = 0;
        long waterCols = 0;
        long protCols = 0;
        long rewrites = 0;
        boolean wlBad = false;
        boolean bedBad = false;
        boolean lavaBad = false;
        boolean lavaFloorBad = false;
        boolean shellBad = false;
        boolean protBad = false;
        boolean postBad = false;
        for (int cx = originChunkX; cx < originChunkX + 12; cx++) {
            for (int cz = originChunkZ; cz < originChunkZ + 12; cz++) {
                final Block[] pre = new Block[65536];
                final Block[] post = new Block[65536];
                fillSynthetic(pre, cx, cz);
                final BiomeGenBase[] biomes = new BiomeGenBase[256];
                Arrays.fill(biomes, steppe);
                System.arraycopy(pre, 0, post, 0, 65536);
                GTSRCaveCarver.carve(CARVE_SEED, cx * 16, cz * 16, post, new byte[65536], biomes);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        final int col = x << 12 | z << 8;
                        final int wx = cx * 16 + x;
                        final int wz = cz * 16 + z;
                        int surfaceY = -1;
                        for (int y = 255; y >= 0; y--) {
                            if (pre[col | y] != null && pre[col | y] != Blocks.air) {
                                surfaceY = y;
                                break;
                            }
                        }
                        final boolean prot = ProsperityCaveField.protectedColumn(CARVE_SEED, wx, wz);
                        final int roster = ProsperityTerrainProfile.chainRosterIndexAt(CARVE_SEED, wx >> 2, wz >> 2);
                        final boolean wp = !prot && ProsperityCaveField.waterColumnProtected(CARVE_SEED, wx, wz,
                            roster);
                        final int hGround = wp ? ProsperityTerrainProfile.heightAt(CARVE_SEED, wx, wz) : -1;
                        if (prot) {
                            protCols++;
                        } else if (wp) {
                            waterCols++;
                        }
                        boolean colDug = false;
                        for (int y = 0; y <= 255; y++) {
                            final int idx = col | y;
                            final Block b0 = pre[idx];
                            final Block b1 = post[idx];
                            if (b0 == b1) {
                                continue;
                            }
                            changed++;
                            colDug = true;
                            if (b0 == Blocks.bedrock) {
                                bedBad = true; // 基岩永远不动
                            }
                            if (!(b0 == Blocks.stone || b0 == body || b0 == top || b0 == filler)) {
                                wlBad = true; // 白名单外（含 cobble 哨兵位/空气槽）被改动
                            }
                            if (y == surfaceY) {
                                openCols++;
                            }
                            if (wp && y <= hGround) {
                                shellBad = true; // 湖床/河床/潭底之下（含 3 格壳）不允许任何改写
                            }
                            if (prot) {
                                protBad = true;
                            }
                            if (b1 == Blocks.lava) {
                                lava++;
                                dugCells++;
                                if (y >= GTSRCaveCarver.CAVE_LAVA_FLOOR) {
                                    lavaBad = true;
                                }
                            } else if (b1 == null) {
                                dugCells++;
                                if (y < GTSRCaveCarver.CAVE_LAVA_FLOOR) {
                                    lavaFloorBad = true; // y<10 的挖格必须置 lava
                                }
                                if (y - 1 >= 0 && post[col | (y - 1)] == filler && b0 != filler) {
                                    postBad = true; // 后效：挖空格正下方不得残留 filler（foundTop 改写门失守）
                                }
                            } else if (b1 == top && b0 == filler) {
                                rewrites++; // floor 铺 top：filler 原位改写为 top（唯一允许的"改写位"）
                            } else {
                                wlBad = true; // 写值合同外：只允许 null / lava / filler→top 三种终态
                            }
                        }
                        if (colDug) {
                            dugCols++;
                        }
                    }
                }
            }
        }
        final long totalCols = 12L * 12 * 256;
        a("CARVE", "whitelist.noChangeOutsideWhitelist", !wlBad && !bedBad);
        a("CARVE", "presence.dugCells>0(caves在场)", dugCells > 0 && dugCols > 0);
        a("CARVE", "presence.surfaceOpenCols>0(地表洞口在场)", openCols > 0);
        a("CARVE", "lava.onlyBelowCAVE_LAVA_FLOOR", !lavaBad && !lavaFloorBad);
        a("CARVE", "water.waterColsPresent(shell样本非空)", waterCols > 0);
        a("CARVE", "water.shellNoPenetration(heightAt+3下零改写)", !shellBad);
        a("CARVE", "island.protectedColsPresent", protCols > 0);
        a("CARVE", "island.protectedColsZeroDig", !protBad);
        a("CARVE", "floorTop.rewriteFillerToTopPresent", rewrites > 0);
        a("CARVE", "floorTop.postConditionNoDugOverFiller", !postBad);
        read("CARVE chunks=144 cols=" + totalCols + " changed=" + changed + " dugCols=" + dugCols + "("
            + pct(dugCols, totalCols) + ") openCols=" + openCols + " lava=" + lava + " rewrites=" + rewrites
            + " waterCols=" + waterCols + " protCols=" + protCols + " center=(" + carveCenterX + "," + carveCenterZ
            + ")");
        lavaArm();
    }

    /**
     * lava 底定向臂：在密度开 ∧ 非岛柱 ∧ 非水体 的列里搜 {@code tubeAt(y∈{8,9})} 命中的坐标
     * （保护下界使随机区域极少挖到 y&lt;10，靠大样偶遇 = 掷硬币判据，必须定向），逐 chunk 合成后
     * carve，钉"这些格挖后必为 lava、lava 只出现在 y&lt;10"。
     */
    static void lavaArm() {
        final BiomeGenBase steppe = carveBiome;
        Lcg r = new Lcg(0xB17EL);
        int found = 0;
        int checked = 0;
        final int[] chunkKeys = new int[64];
        final int[] colXs = new int[64];
        final int[] colZs = new int[64];
        while (found < 64 && checked < 600000) {
            final int x = r.intInRange(-9000, 9000);
            final int z = r.intInRange(-9000, 9000);
            checked++;
            if (!ProsperityCaveField.densityOpenAt(CARVE_SEED, x, z)) {
                continue;
            }
            if (!ProsperityCaveField.tubeAt(CARVE_SEED, x, 8, z) && !ProsperityCaveField.tubeAt(CARVE_SEED, x, 9, z)) {
                continue;
            }
            if (ProsperityCaveField.protectedColumn(CARVE_SEED, x, z)) {
                continue;
            }
            final int roster = ProsperityTerrainProfile.chainRosterIndexAt(CARVE_SEED, x >> 2, z >> 2);
            if (ProsperityCaveField.waterColumnProtected(CARVE_SEED, x, z, roster)) {
                continue;
            }
            if (found >= chunkKeys.length) {
                break; // 每 chunk 只留首列（防越界写），64 个样本足够
            }
            chunkKeys[found] = (Math.floorDiv(x, 16) << 16) ^ (Math.floorDiv(z, 16) & 0xFFFF);
            colXs[found] = x;
            colZs[found] = z;
            found++;
        }
        a("CARVE", "lava.armSamplesFound(" + found + " cols, " + checked + " probes)", found >= 16);
        int lavaCells = 0;
        boolean lavaBad = false;
        boolean targetBad = false;
        final java.util.HashMap<Integer, Block[]> byChunk = new java.util.HashMap<>();
        for (int i = 0; i < found; i++) {
            final int cx = colXs[i] >> 4;
            final int cz = colZs[i] >> 4;
            final int key = (cx << 16) ^ (cz & 0xFFFF);
            Block[] arr = byChunk.get(key);
            if (arr == null) {
                arr = new Block[65536];
                fillSynthetic(arr, cx, cz);
                final BiomeGenBase[] biomes = new BiomeGenBase[256];
                Arrays.fill(biomes, steppe);
                GTSRCaveCarver.carve(CARVE_SEED, cx * 16, cz * 16, arr, new byte[65536], biomes);
                byChunk.put(key, arr);
            }
            final int lx = colXs[i] & 15;
            final int lz = colZs[i] & 15;
            final int col = lx << 12 | lz << 8;
            for (int y = 8; y <= 9; y++) {
                final Block got = arr[col | y];
                if (ProsperityCaveField.tubeAt(CARVE_SEED, colXs[i], y, colZs[i])) {
                    lavaCells++;
                    if (got != Blocks.lava) {
                        targetBad = true; // 管束命中 ∧ 白名单主体 ∧ y<10 ⇒ 必须是 lava
                    }
                }
            }
            for (int y = 0; y < 8; y++) {
                if (arr[col | y] == Blocks.lava) {
                    lavaBad = true; // 域下界之下不得有 lava
                }
            }
        }
        a("CARVE", "lava.targetedCellsAlwaysLava(" + lavaCells + " cells)", !targetBad && lavaCells >= 16);
        a("CARVE", "lava.noneBelowDomainFloor", !lavaBad);
        read("CARVE lavaArm samples=" + found + " probes=" + checked + " targetCells=" + lavaCells);
    }

    /** 合成列：基岩带（真实 bedrockTopHash）+ 主体 prosperityStone + filler 两位 + top 一位 + cobble 哨兵。 */
    static void fillSynthetic(Block[] blocks, int cx, int cz) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int col = x << 12 | z << 8;
                final int bTop = GTSRWorldgenHash.bedrockTopHash(CARVE_SEED, cx * 16 + x, cz * 16 + z);
                for (int y = 0; y <= bTop; y++) {
                    blocks[col | y] = Blocks.bedrock;
                }
                for (int y = bTop + 1; y <= SYNTH_H - 3; y++) {
                    blocks[col | y] = BlocksGTSR.prosperityStone;
                }
                blocks[col | (SYNTH_H - 2)] = BlocksGTSR.prosperitySteppeBase; // 本列群系 filler
                blocks[col | (SYNTH_H - 1)] = BlocksGTSR.prosperitySteppeBase;
                blocks[col | SYNTH_H] = BlocksGTSR.prosperitySteppeTop; // 本列群系 top
                blocks[col | 64] = Blocks.cobblestone; // 白名单外哨兵（管束/峡谷必跨的竖窗位）
            }
        }
    }

    // ════════════════════════ S1b · SOURCE 组：源级红线（p21 §1.2/§1.4）════════════════════════

    static final Pattern SHIFT4 = Pattern.compile("<<\\s*4(?![0-9])");

    static void groupSource() throws IOException {
        final String carver = stripComments(slurp(CARVER_SOURCE));
        final String field = stripComments(slurp(FIELD_SOURCE));
        final String provider = stripComments(slurp(PROVIDER_SOURCE));
        final String base = stripComments(slurp(BASE_SOURCE));
        a("SOURCE", "files.nonEmpty",
            carver.length() > 1000 && field.length() > 1000 && provider.length() > 1000 && base.length() > 1000);
        // 负形状 ×3：两新类 + 覆写体（基类与 provider 其余部分不在钉内——其既有 worldObj 读点非本判据对象）
        a("SOURCE", "carver.no.getBiomeGenForCoords", !carver.contains("getBiomeGenForCoords"));
        a("SOURCE", "carver.no.worldObj", !carver.contains("worldObj."));
        a("SOURCE", "carver.no.shift4Index", !SHIFT4.matcher(carver).find());
        a("SOURCE", "field.no.getBiomeGenForCoords", !field.contains("getBiomeGenForCoords"));
        a("SOURCE", "field.no.worldObj", !field.contains("worldObj."));
        a("SOURCE", "field.no.shift4Index", !SHIFT4.matcher(field).find());
        // 覆写体切片
        final String body = carveOverrideBody(provider);
        a("SOURCE", "override.bodyFound", !body.isEmpty());
        a("SOURCE", "override.no.getBiomeGenForCoords", !body.contains("getBiomeGenForCoords"));
        a("SOURCE", "override.no.worldObj", !body.contains("worldObj."));
        a("SOURCE", "override.no.shift4Index", !SHIFT4.matcher(body).find());
        a("SOURCE", "override.wiredToCarver", body.contains("GTSRCaveCarver") && body.contains("carve("));
        // 成对正形状（防"整段没写"式假绿）
        a("SOURCE", "carver.pos.biomeAtColumn>=1", countOccurrences(carver, "biomeAtColumn(") >= 1);
        a("SOURCE", "carver.pos.indexShape.columnOrY", carver.contains("<< 12") && carver.contains("<< 8"));
        a("SOURCE", "carver.pos.whitelistLiterals",
            carver.contains("Blocks.stone") && carver.contains("prosperityStone") && carver.contains("topBlock")
                && carver.contains("fillerBlock"));
        a("SOURCE", "carver.pos.lavaAndEnabled",
            carver.contains("Blocks.lava") && carver.contains("CAVE_ENABLED = true"));
        // 基类挂点位置序：replaceBlocksForBiome 之后、new Chunk 之前
        final int i1 = base.indexOf("replaceBlocksForBiome(chunkX, chunkZ, blocks, metadata, biomes);");
        final int i2 = base.indexOf("carveCaves(this.worldObj.getSeed(), chunkX, chunkZ, blocks, metadata, biomes);");
        final int i3 = base.indexOf("new Chunk(this.worldObj, blocks, metadata, chunkX, chunkZ);");
        a("SOURCE", "hook.betweenSurfaceAndChunkAssembly", i1 >= 0 && i2 > i1 && i3 > i2);
        read("SOURCE hookOffsets replace=" + i1 + " carve=" + i2 + " newChunk=" + i3
            + " overrideLen=" + body.length());
    }

    /** 从 provider 去注释文本里切出 carveCaves 覆写体（签名行到方法收尾的 4 缩进 `}`）。 */
    static String carveOverrideBody(String providerStripped) {
        final int start = providerStripped.indexOf("protected void carveCaves(");
        if (start < 0) {
            return "";
        }
        final int end = providerStripped.indexOf("\n    }", start);
        return end < 0 ? "" : providerStripped.substring(start, end);
    }

    static int countOccurrences(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    static String slurp(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    /** 去注释（行/块注释内容置空格、保留换行与字符串/字符字面量），SOURCE 组专用。 */
    static String stripComments(String src) {
        final int n = src.length();
        final StringBuilder sb = new StringBuilder(n);
        int i = 0;
        while (i < n) {
            final char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    sb.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int j = src.indexOf("*/", i + 2);
                final int stop = j < 0 ? n : j + 2;
                for (int k = i; k < stop; k++) {
                    sb.append(src.charAt(k) == '\n' ? '\n' : ' ');
                }
                i = stop;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n) {
                    final char d = src.charAt(j);
                    if (d == '\\') {
                        j += 2;
                        continue;
                    }
                    if (d == c) {
                        j++;
                        break;
                    }
                    if (d == '\n') {
                        break;
                    }
                    j++;
                }
                sb.append(src, i, Math.min(j, n));
                i = j;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    // ════════════════════════ S1b · PERF 组：per-chunk carve 中位（BASE×1.30）════════════════════════

    static final long[] carveSamples = new long[64];

    static void groupPerf() {
        // 装配复用 CARVE 组（groupCarve 先跑）；构造成本不入计时窗，计时窗只有 carve 本身
        if (carveBiome == null) {
            SurfaceHarness.initVanillaBlocks();
            SurfaceHarness.blockFamily();
            carveBiome = new BiomeRustedSteppe(200);
        }
        final BiomeGenBase steppe = carveBiome;
        final int warmup = 8;
        final int chunks = 64;
        long dugTotal = 0;
        for (int pass = 0; pass < warmup + chunks; pass++) {
            final int cx = 4000 + pass; // 逐样本换 chunk：不吃 heightAt/roster memo 的重复坐标红利
            final int cz = 4000;
            final Block[] arr = new Block[65536];
            fillSynthetic(arr, cx, cz);
            final BiomeGenBase[] biomes = new BiomeGenBase[256];
            Arrays.fill(biomes, steppe);
            final long t0 = System.nanoTime();
            dugTotal += GTSRCaveCarver.carve(CARVE_SEED, cx * 16, cz * 16, arr, new byte[65536], biomes);
            if (pass >= warmup) {
                carveSamples[pass - warmup] = (System.nanoTime() - t0) / 1000L;
            }
        }
        Arrays.sort(carveSamples);
        final long median = carveSamples[chunks / 2];
        final long p90 = carveSamples[(int) (chunks * 0.9)];
        a("PERF", "carve.median(" + median + "us)<=BASE(" + CARVE_BASELINE_US_PER_CHUNK + ")x1.30("
            + CARVE_GATE_US_PER_CHUNK + ")", median <= CARVE_GATE_US_PER_CHUNK && median > 0);
        a("PERF", "carve.dugPresence(anti-fake-green)", dugTotal > 0);
        read(String.format("PERF carver median=%dus/chunk p90=%dus mean=%.0fus samples=%d BASE=%.0f gate=%.0f"
            + " dugTotal=%d（单线程串行，不与其他 harness 并发；口径=合成固体列阵）", median, p90,
            mean(carveSamples, chunks), chunks, CARVE_BASELINE_US_PER_CHUNK, CARVE_GATE_US_PER_CHUNK, dugTotal));
    }

    // ════════════════════════ 成本行（先不设门）════════════════════════

    static void costLine() {
        final int warmup = 4;
        final int chunks = 64;
        long openCols = 0;
        long colsTotal = 0;
        long tubeHits = 0;
        for (int pass = 0; pass < warmup + chunks; pass++) {
            final int cx = (pass % 64) * 16;
            final int cz = ((pass / 64) % 4) * 16;
            final long t0 = System.nanoTime();
            long hits = 0;
            long open = 0;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    final int x = (cx << 4) + lx;
                    final int z = (cz << 4) + lz;
                    colsTotal++;
                    final boolean densityOpen = ProsperityCaveField.densityOpenAt(SEED, x, z);
                    if (densityOpen) {
                        open++;
                        for (int y = ProsperityCaveField.CAVE_Y_MIN; y <= ProsperityCaveField.CAVE_Y_MAX; y++) {
                            if (ProsperityCaveField.tubeAt(SEED, x, y, z)) {
                                hits++;
                            }
                        }
                    }
                    ProsperityCaveField.carveCeilingAt(SEED, x, z, ProsperityTerrainProfile.heightAt(SEED, x, z));
                    ProsperityCaveField.carveFloorY(SEED, x, z, 3);
                    ProsperityCaveField.protectedColumn(SEED, x, z);
                    ProsperityCaveField.canyonAt(SEED, x, z);
                }
            }
            if (pass >= warmup) {
                openCols += open;
                tubeHits += hits;
                perChunk[pass - warmup] = (System.nanoTime() - t0) / 1000L;
            }
        }
        java.util.Arrays.sort(perChunk, 0, chunks);
        final long median = perChunk[chunks / 2];
        read(String.format(
            "COST median=%dus/chunk mean=%.0fus/chunk earlyExit=%s tubeHits/chunk=%.1f（单 chunk 场求值，先不设门）",
            median, mean(perChunk, chunks), pct(openCols, colsTotal), tubeHits / (double) chunks));
        a("COST", "cost.line.produced(chunks=" + chunks + ")", median > 0);
    }

    static final long[] perChunk = new long[64];

    static double mean(long[] arr, int n) {
        double s = 0;
        for (int i = 0; i < n; i++) {
            s += arr[i];
        }
        return s / n;
    }

    static String pct(long part, long whole) {
        return whole == 0 ? "n/a" : String.format("%.2f%%", 100.0D * part / whole);
    }

    /** 确定性 LCG（splitmix 族同风格，判据抽样不引 java.util.Random 序依赖）。 */
    static final class Lcg {
        long state;

        Lcg(long seed) {
            state = seed;
        }

        long next() {
            state = state * 6364136223846793005L + 1442695040888963407L;
            return (state >>> 33);
        }

        int intInRange(int lo, int hi) {
            final long span = (long) hi - lo + 1;
            return (int) (lo + next() % span);
        }
    }
}
