import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.storage.ISaveHandler;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityLumenPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * P22 版 B · S4 旧栖晴晕光点两趟判据：A（密度比值 lake&lt;canopy）/ B（光衰减三档[自立口径]）/
 * C（9-chunk 并集同形）/ D（让行只写空气）/ E（源级纪律）五组，范式照 {@code MegaTreeCheck}。
 *
 * <p>═══ A 密度比值 ═══ 12 座活湖锚点的 3×3 窗上，冠下趟与湖上趟<b>分趟独立跑</b>，实测每 chunk
 * 光源数：{@code canopyMean ≥ 0.5}（在场下界，防"零写入假绿"）+ {@code lakeMean < canopyMean}
 * （严格小于）+ {@code lakeMean ≤ 0.8×canopyMean}（容差带：近等值判红，"更稀疏"必须有量级）
 * + {@code lakeTotal ≥ 1}（湖上趟在场）。<b>不钉绝对值</b>——两档密度常量是实机校准旋钮，
 * 判据只钉偏序与比值带。
 *
 * <p>═══ B 光衰减三档（<b>[自立口径]</b>）═══ 主判据 {@code getLightValue()==14} 已在
 * {@code P17BlockRosterCheck}（lumen 档运行时直钉）硬钉；本组<b>离线复刻 vanilla 线性衰减
 * {@code max(0, L−r)}</b>（逐格递减 1，torch 族同式）对三档距离出读：r=1 ≥13 / r=7 ≥7 /
 * r=15 ==0。L 从实例读出（不自造第二份注册事实），衰减式是判据侧自立的几何复刻。
 *
 * <p>═══ C 9-chunk 并集同形 ═══ 对每座锚点：参照臂 = {@code canopyLightsAt} 纯函数光位集（无 sink、
 * 无世界）；重放臂 = 9 个邻 chunk 各自 {@code placeCanopyPass}（各自枚举锚点、各自 ChunkSliceSink）。
 * 断言：并集 == 参照集逐格（缺角即红——窗枚举/去重任何一处漏一个 chunk 就对不上）、写格数和 ==
 * 并集数（单射无重复写）、每片只含本 chunk owns 的格。照 S3 MegaTreeCheck D 组范式。
 *
 * <p>═══ D 让行（只写空气）═══ 合成门世界（可配置 y 门带）：门带压在 y==68（湖上位）⇒ 湖上趟
 * 精确 0 写、冠下趟不受影响（冠下光位 y ≥ 108）；门带压在 y∈[100,255] ⇒ 冠下趟精确 0 写、湖上趟
 * 不受影响；纯空气世界两趟都有产出。三臂合证"光点只落世界空气格，零覆写"。
 *
 * <p>═══ E 源级纪律 ═══ LumenPlacer 源（去注释）零 {@code this.rand}/{@code world.rand}/
 * {@code nextLong} + 盐字面 {@code SALT_LUMEN = 0x4C554D4EL} 在场（<b>成对断言</b>：零裸 rand
 * 且盐在场 ⇒ 唯一随机源是盐派生流）；锚点槽/chunk 槽两条派生式在场；冠形态重放读
 * {@code ProsperityDecorPlacer.SALT_ISLAND_TREE}（盐单源，不复写字面）；盐与三既有盐不同值；
 * DecorPlacer 挂点序 = 岛心树 → mega → lumen → 灌木（四个 indexOf 链式钉）。
 *
 * <p>口径申报：合成世界只供空气门读（isAirBlock/getBlock），锚点/冠形态/湖条件全部走生产纯函数
 * （工具不自算第二份判定）；锚点搜索照 MegaTreeCheck 同式（湖心反解 + 域门双源）。
 * 出口：stdout 逐组判定，末行 {@code LUMEN assertions=<n> fail=<m>}；任何红 exit 1。
 */
public final class LumenLightCheck {

    /** 活湖锚点搜索种（MegaTreeCheck.SEARCH_SEED0 同族步进）。 */
    static final long SEARCH_SEED0 = 0x1AFEE7A9E5A7L;
    static final long SEARCH_SEED_STEP = 0x9E37L;

    /** 生产盐字面（E 组源级钉后才用于断言文本）。 */
    static final long SALT_LUMEN = 0x4C554D4EL;

    static final String LUMEN_SRC =
        "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityLumenPlacer.java";
    static final String DECOR_SRC =
        "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityDecorPlacer.java";

    /** 冠下光位 y 下界（y0≥71 + 干高≥66 − 冠回撤10 − 竖半高18 − 1 ⇒ ≥108；D 组门带取 100 留余量）。 */
    static final int CANOPY_LIGHT_Y_FLOOR = 100;

    /** A 组在场下界（每 chunk 冠下光源均值；不钉绝对值，只防零写入假绿）。 */
    static final double CANOPY_MEAN_FLOOR = 0.5D;

    /** A 组比值带（湖上均值 ≤ 冠下均值 × 本值；严格小于另单列一条）。 */
    static final double LAKE_RATIO_BAND = 0.8D;

    static int total;
    static int fails;

    public static void main(String[] args) throws Exception {
        bootstrap();
        groupA();
        groupB();
        groupC();
        groupD();
        groupE();
        System.out.println("LUMEN assertions=" + total + " fail=" + fails);
        if (fails > 0) {
            System.out.println("LUMEN CHECKS: RED");
            System.exit(1);
        }
        System.out.println("LUMEN CHECKS: GREEN");
    }

    static void bootstrap() throws Exception {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        for (int i = 0; i < 4; i++) {
            final BiomeGenBase b = new BiomeGenBase(180 + i) {};
            GTSRBiomeAuthority.recordAllocation(GTSRBiomeAuthority.BiomeId.values()[i], 180 + i, 180 + i, b);
        }
        if (BlocksGTSR.prosperityRoostGlow == null) {
            throw new IllegalStateException("P22-B S2 旧栖晴晕方块缺席（blockFamily 未同步）");
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

    static String fmt(double v) {
        return String.format("%.3f", Double.valueOf(v));
    }

    // ════════════════════════ 记录 sink / 门世界 ════════════════════════

    /** 落块记录 sink：格→是否旧栖晴晕（D/C 组并集与计数）。 */
    static final class Rec implements BlockSink {

        final Map<Long, Boolean> cells = new LinkedHashMap<>();

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            cells.put(key(x, y, z), Boolean.valueOf(block == BlocksGTSR.prosperityRoostGlow));
            return true;
        }

        int glow() {
            int n = 0;
            for (final Boolean v : cells.values()) {
                if (v.booleanValue()) {
                    n++;
                }
            }
            return n;
        }
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int signFix(int v) {
        return (v << 6) >> 6;
    }

    static int keyX(long k) {
        return signFix((int) (k >> 38));
    }

    static int keyZ(long k) {
        return signFix((int) ((k >> 12) & 0x3FFFFFF));
    }

    static int keyY(long k) {
        return (int) (k & 0xFFF);
    }

    /**
     * 合成门世界（MegaTreeCheck.AirWorld 同族）：默认全空气；[gateLo, gateHi] 门带内非空气
     * （D 组让行臂用）；y&gt;255 镜像 vanilla 实心界外。只供空气门读。
     */
    static final class GateWorld extends World {

        int gateLo = Integer.MIN_VALUE;
        int gateHi = Integer.MIN_VALUE;

        private GateWorld() {
            super((ISaveHandler) null, (String) null, (WorldProvider) null, (WorldSettings) null, (Profiler) null);
        }

        static GateWorld make() throws Exception {
            final sun.misc.Unsafe u = SurfaceHarness.unsafe();
            return (GateWorld) u.allocateInstance(GateWorld.class);
        }

        private boolean gated(int y) {
            return y >= gateLo && y <= gateHi;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            if (y > 255) {
                return Blocks.bedrock;
            }
            return gated(y) ? Blocks.cobblestone : Blocks.air;
        }

        @Override
        public boolean isAirBlock(int x, int y, int z) {
            if (y > 255) {
                return false;
            }
            return !gated(y);
        }

        @Override
        public net.minecraft.entity.Entity getEntityByID(int id) {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        protected net.minecraft.world.chunk.IChunkProvider createChunkProvider() {
            return null;
        }
    }

    // ════════════════════════ 锚点搜索（生产纯函数，双源对拍）════════════════════════

    static final class Anchor {

        final long seed;
        final int ax;
        final int az;
        final int y0;

        Anchor(long seed, int ax, int az, int y0) {
            this.seed = seed;
            this.ax = ax;
            this.az = az;
            this.y0 = y0;
        }
    }

    /** 活湖锚点搜索（MegaTreeCheck.searchAnchor 同式：湖心反解 + 域门）。 */
    static Anchor searchAnchor(long seed, int x, int z) {
        if (GTSRVoronoiRiverField.trunkAt(seed, x, z) <= 0.0D) {
            return null;
        }
        final double[] out = new double[4];
        GTSRVoronoiRiverField.lakeCellCenterAt(seed, x, z, out);
        if ((int) out[2] == Integer.MIN_VALUE) {
            return null;
        }
        final int cx = (int) Math.round(out[0]);
        final int cz = (int) Math.round(out[1]);
        if (GTSRVoronoiRiverField.lakeAt(seed, cx, cz) >= GTSRVoronoiRiverField.LAKE_ISLAND) {
            return null;
        }
        return new Anchor(seed, cx, cz, ProsperityTerrainProfile.heightAt(seed, cx, cz));
    }

    static List<Anchor> findAnchors(int want) {
        final List<Anchor> found = new ArrayList<>();
        final java.util.Set<Long> seen = new java.util.HashSet<>();
        for (long s = 0; s < 64 && found.size() < want; s++) {
            final long seed = SEARCH_SEED0 + s * SEARCH_SEED_STEP;
            for (int z = -9000; z <= 9000 && found.size() < want; z += 300) {
                for (int x = -9000; x <= 9000 && found.size() < want; x += 300) {
                    final Anchor an = searchAnchor(seed, x, z);
                    if (an != null && seen.add(Long.valueOf(((long) an.ax << 32) ^ (an.az & 0xFFFFFFFFL)))) {
                        found.add(an);
                    }
                }
            }
        }
        return found;
    }

    // ════════════════════════ A 组：密度比值（lake < canopy）════════════════════════

    static void groupA() throws Exception {
        final int want = 12;
        final List<Anchor> anchors = findAnchors(want);
        a("A", "activeLakeAnchors.found(" + anchors.size() + "/" + want + ")", anchors.size() >= want);
        if (anchors.isEmpty()) {
            return;
        }
        final World air = GateWorld.make();
        long canopyTotal = 0;
        long lakeTotal = 0;
        int chunks = 0;
        boolean allGlow = true;
        for (final Anchor an : anchors) {
            final int ocx = an.ax >> 4;
            final int ocz = an.az >> 4;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    final Rec c = new Rec();
                    ProsperityLumenPlacer.placeCanopyPass(air, an.seed, ocx + dx, ocz + dz, c);
                    final Rec l = new Rec();
                    ProsperityLumenPlacer.placeLakePass(air, an.seed, ocx + dx, ocz + dz, l);
                    canopyTotal += c.glow();
                    lakeTotal += l.glow();
                    if (c.glow() != c.cells.size() || l.glow() != l.cells.size()) {
                        allGlow = false; // 写入的全部都是旧栖晴晕（无杂块）
                    }
                    chunks++;
                }
            }
        }
        final double canopyMean = (double) canopyTotal / chunks;
        final double lakeMean = (double) lakeTotal / chunks;
        final double ratio = canopyMean > 0.0D ? lakeMean / canopyMean : -1.0D;
        read("A chunks=" + chunks + " canopy=" + canopyTotal + " lake=" + lakeTotal + " canopyMean/chunk="
            + fmt(canopyMean) + " lakeMean/chunk=" + fmt(lakeMean) + " ratio=" + fmt(ratio));
        a("A", "writes.allRoostGlow", allGlow);
        a("A", "canopyMean>=0.5.presence(" + fmt(canopyMean) + ")", canopyMean >= CANOPY_MEAN_FLOOR);
        a("A", "lakeTotal>=1.presence(" + lakeTotal + ")", lakeTotal >= 1);
        a("A", "lakeMean<canopyMean.strict(" + fmt(lakeMean) + "<" + fmt(canopyMean) + ")",
            lakeMean < canopyMean);
        a("A", "lakeMean<=0.8x.canopyMean.band", canopyMean > 0 && lakeMean <= LAKE_RATIO_BAND * canopyMean);
    }

    // ════════════════════════ B 组：光衰减三档（[自立口径]）════════════════════════

    /** vanilla 线性衰减离线复刻：逐格递减 1，下限 0（[自立口径]：判据侧几何复刻，非注册事实）。 */
    static int decay(int light, int distance) {
        return Math.max(0, light - distance);
    }

    static void groupB() {
        final int light = BlocksGTSR.prosperityRoostGlow.getLightValue();
        read("B lightValue=" + light + " (hardpin@P17BlockRosterCheck lumen)");
        a("B", "source.getLightValue==14.crossRead", light == 14);
        a("B", "falloff.r1>=13(" + decay(light, 1) + ")", decay(light, 1) >= 13);
        a("B", "falloff.r7>=7(" + decay(light, 7) + ")", decay(light, 7) >= 7);
        a("B", "falloff.r15==0(" + decay(light, 15) + ")", decay(light, 15) == 0);
    }

    // ════════════════════════ C 组：9-chunk 并集同形 ════════════════════════

    static void groupC() throws Exception {
        final World air = GateWorld.make();
        final List<Anchor> anchors = findAnchors(3);
        a("C", "anchors.forReplay.found(" + anchors.size() + "/3)", anchors.size() >= 3);
        final int[][] buf = new int[ProsperityLumenPlacer.CANOPY_LIGHT_CAP][3];
        boolean unionOk = true;
        boolean injectiveOk = true;
        boolean ownedOk = true;
        boolean nonEmptyOk = true;
        int checked = 0;
        for (final Anchor an : anchors) {
            final int n = ProsperityLumenPlacer.canopyLightsAt(an.seed, an.ax, an.az, an.y0, buf);
            final Map<Long, Boolean> ref = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                ref.put(key(buf[i][0], buf[i][1], buf[i][2]), Boolean.TRUE);
            }
            nonEmptyOk &= n > 0;
            final Map<Long, Boolean> union = new LinkedHashMap<>();
            int sumSizes = 0;
            final int ocx = an.ax >> 4;
            final int ocz = an.az >> 4;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    final Rec piece = new Rec();
                    ProsperityLumenPlacer.placeCanopyPass(air, an.seed, ocx + dx, ocz + dz, piece);
                    for (final Map.Entry<Long, Boolean> e : piece.cells.entrySet()) {
                        if (keyX(e.getKey()) >> 4 != ocx + dx || keyZ(e.getKey()) >> 4 != ocz + dz) {
                            ownedOk = false; // 切片层失守：写了不归本 chunk 的格
                        }
                        union.put(e.getKey(), Boolean.TRUE);
                    }
                    sumSizes += piece.cells.size();
                }
            }
            checked++;
            if (!union.equals(ref)) {
                unionOk = false;
            }
            if (sumSizes != union.size()) {
                injectiveOk = false; // 有格被两个 chunk 重复写
            }
            read("C anchor#" + checked + " ref=" + ref.size() + " union=" + union.size() + " sumPieces=" + sumSizes);
        }
        a("C", "replayUnion9.equalsPureReplay", unionOk);
        a("C", "ownerInjection.noDuplicateWrites", injectiveOk);
        a("C", "slicePieces.allOwned", ownedOk);
        a("C", "anchorLights.nonEmpty(" + checked + ")", nonEmptyOk && checked > 0);
    }

    // ════════════════════════ D 组：让行（只写空气，零覆写）════════════════════════

    static void groupD() throws Exception {
        final List<Anchor> anchors = findAnchors(3);
        a("D", "anchors.forYield.found(" + anchors.size() + "/1)", !anchors.isEmpty());
        if (anchors.isEmpty()) {
            return;
        }
        final int ocx = anchors.get(0).ax >> 4;
        final int ocz = anchors.get(0).az >> 4;

        // D1 湖上位被占（y==68 门带）：湖上趟精确 0 写、冠下趟不受影响（冠下 y ≥ 108）
        final GateWorld lakeBlocked = GateWorld.make();
        lakeBlocked.gateLo = ProsperityTerrainProfile.SEA_LEVEL;
        lakeBlocked.gateHi = ProsperityTerrainProfile.SEA_LEVEL;
        int canopy1 = 0;
        int lake1 = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final Rec c = new Rec();
                ProsperityLumenPlacer.placeCanopyPass(lakeBlocked, anchors.get(0).seed, ocx + dx, ocz + dz, c);
                final Rec l = new Rec();
                ProsperityLumenPlacer.placeLakePass(lakeBlocked, anchors.get(0).seed, ocx + dx, ocz + dz, l);
                canopy1 += c.glow();
                lake1 += l.glow();
            }
        }
        read("D lakeBlockedArm canopy=" + canopy1 + " lake=" + lake1);
        a("D", "yield.lakeYOccupied.lakePass.zeroWrite", lake1 == 0);
        a("D", "yield.lakeYOccupied.canopyUnaffected", canopy1 > 0);

        // D2 冠下 y 域被占（[100,255] 门带 ⇒ 全部冠下光位非空气）：冠下趟精确 0 写、湖上趟不受影响
        final GateWorld canopyBlocked = GateWorld.make();
        canopyBlocked.gateLo = CANOPY_LIGHT_Y_FLOOR;
        canopyBlocked.gateHi = 255;
        int canopy2 = 0;
        int lake2 = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final Rec c = new Rec();
                ProsperityLumenPlacer.placeCanopyPass(canopyBlocked, anchors.get(0).seed, ocx + dx, ocz + dz, c);
                final Rec l = new Rec();
                ProsperityLumenPlacer.placeLakePass(canopyBlocked, anchors.get(0).seed, ocx + dx, ocz + dz, l);
                canopy2 += c.glow();
                lake2 += l.glow();
            }
        }
        read("D canopyBlockedArm canopy=" + canopy2 + " lake=" + lake2);
        a("D", "yield.canopyYOccupied.canopyPass.zeroWrite", canopy2 == 0);
        a("D", "yield.canopyYOccupied.lakeUnaffected", lake2 > 0);
        a("D", "yield.zeroOverwrite.blockedPositionsUntouched",
            lake1 == 0 && canopy2 == 0 && lake2 > 0 && canopy1 > 0);
    }

    // ════════════════════════ E 组：源级纪律 ════════════════════════

    static void groupE() throws java.io.IOException {
        final String lumen = stripComments(slurp(LUMEN_SRC));
        final String decor = stripComments(slurp(DECOR_SRC));
        // 成对断言：零裸 rand 且盐字面在场（缺一即红）
        a("E", "src.lumen.noBareRand",
            !lumen.contains("this.rand") && !lumen.contains("world.rand") && !lumen.contains("nextLong"));
        final Pattern saltPat = Pattern.compile("SALT_LUMEN\\s*=\\s*(0x[0-9A-Fa-f]+)L");
        final Matcher m = saltPat.matcher(lumen);
        final String saltLit = m.find() ? m.group(1) : "(absent)";
        final boolean saltOk = !"(absent)".equals(saltLit) && Long.decode(saltLit).longValue() == SALT_LUMEN;
        read("E saltLumen=" + saltLit + " expect=0x" + Long.toHexString(SALT_LUMEN));
        a("E", "src.saltLumen.literalPinned", saltOk);
        a("E", "src.saltLumen.distinctFrom(island/wind/mega)",
            SALT_LUMEN != 0x49534C4E44L && SALT_LUMEN != 0x77696E64L && SALT_LUMEN != 0x6D656761L);
        // 两种槽位派生式在场（锚点槽=冠下跨 chunk 单射；chunk 槽=湖上 chunk 级自洽）
        a("E", "src.derivation.anchorSlot",
            lumen.contains("chunkSeed(worldSeed, ax >> 4, az >> 4) ^ SALT_LUMEN"));
        a("E", "src.derivation.chunkSlot",
            lumen.contains("chunkSeed(worldSeed, chunkX, chunkZ) ^ SALT_LUMEN"));
        // 冠形态重放的盐单源：读 DecorPlacer 的 SALT_ISLAND_TREE 字段，不复写字面
        a("E", "src.formReplay.sharesIslandSalt",
            lumen.contains("^ ProsperityDecorPlacer.SALT_ISLAND_TREE") && !lumen.contains("0x49534C4E44"));
        // DecorPlacer 挂点序：岛心树 → mega → lumen → 灌木（链式 indexOf 钉）
        final int islandIdx = decor.indexOf("placeIslandTreePass(world, worldSeed, chunkX, chunkZ, sink);");
        final int megaIdx = decor.indexOf("if (trees.megaForm != MEGA_NONE)");
        final int lumenIdx = decor.indexOf("ProsperityLumenPlacer.placeLumenPass(world, worldSeed, chunkX, chunkZ, sink);");
        final int shrubIdx = decor.indexOf("final double shrubDensity");
        read("E hookIdx island=" + islandIdx + " mega=" + megaIdx + " lumen=" + lumenIdx + " shrub=" + shrubIdx);
        a("E", "src.hook.chainIsland<Mega<Lumen<Shrub",
            islandIdx >= 0 && megaIdx > islandIdx && lumenIdx > megaIdx && shrubIdx > lumenIdx);
        // 共享流零取数：委托行不含 rand（签名钉死）
        a("E", "src.hook.noSharedRandArg",
            lumenIdx >= 0 && !decor.substring(lumenIdx, Math.min(decor.length(), lumenIdx + 120)).contains("rand"));
    }

    // ════════════════════════ 工具 ════════════════════════

    static String slurp(String path) throws java.io.IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    /** 去注释（MegaTreeCheck 同款：注释置空格、保留字符串字面量与换行）。 */
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
}
