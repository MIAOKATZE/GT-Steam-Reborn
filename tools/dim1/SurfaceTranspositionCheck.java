import java.util.TreeSet;

import net.minecraft.block.Block;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.Degraded;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.shattered.ChunkProviderShatteredGrounds;
import com.miaokatze.gtsr.common.dimension.shattered.ShatteredTerrainProfile;

/**
 * <b>P2 判据 C 的运行时路径级离线断言</b>：表层消费侧与生产侧的 {@code biomes[]} 下标必须
 * <b>同侧</b>（plan §3 新发现第 5 行 / §5 P2 / §2.1 L3「禁止 {@code biomes[]} 下标转置混用」）。
 * <p>
 * 改造前事实：生产者 {@code GTSRWorldChunkManager.getBiomesForGeneration} 写
 * {@code dx + dz * width}（＝vanilla {@code loadBlockGeneratorData} 的 {@code x + z*width}），
 * 而两 provider 读 {@code biomes[z + x * 16]} —— <b>互为转置</b>。现状每 chunk 整块单一群系，
 * 所以差异不可见；表层一旦按列生效（P3/P5/P6 的高度与列级改造），整 chunk 表层就会成镜像。
 * 本片把内核读取统一为 {@code biomes[x + z * 16]}（框架唯一出口 {@code biomeAtColumn}）。
 * <p>
 * <b>本工具刻意不用"chunk 内 256 格同群系"式断言</b>——那种断言对下标/转置错误结构性免疫
 * （任务包诚实性要求）。这里构造<b>逐列异群系</b>的合成数组（两种非对称图案，各维一种），
 * 断言：
 * <ol>
 * <li>每列裸露面落到的 top / 其下整段 base 方块 ＝ 该列世界坐标被指派的那个群系（dim78）；</li>
 * <li>每列 top ＝ 指派群系 top 且 filler 段之下主体保持 corestone（dim79）；</li>
 * <li>filler 深度 ＝ 用<b>独立重写</b>的同一列哈希按 (worldX, worldZ) 重算值——钉住
 * x/z→世界坐标的映射方向（转置的另一半）；</li>
 * <li>指派群系的 L1 身份（{@code GTSRBiomeAuthority.of(...).biomeId.rosterIndex()}）＝
 * 图案函数使用的名册下标（"与 {@code ordinalAt} 同口径"＝同一 L1 身份出口、同一下标域）；</li>
 * <li>真实 manager 路径：{@code array[x + z*16]} 与 {@code authority.ordinalAt(worldX, worldZ)}
 * 逐列一致（生产侧 ↔ L1 侧同向）；</li>
 * <li><b>灵敏度自检</b>：统计"若改回读 {@code z + x*16} 会有多少列取到别的群系"，必须远大于 0
 * ——证明本文件的断言真的能抓住转置，而不是恰好通过。</li>
 * </ol>
 * 命令见 {@code tools/dim1/surface_checks.sh}。
 */
public class SurfaceTranspositionCheck {

    /** 非零 chunk 原点：同时排除"把 baseX/baseZ 当成 0 或把 x/z 加错侧"这一类错误。 */
    private static final int CHUNK_X = 3;
    private static final int CHUNK_Z = -2;

    private static int failures = 0;
    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        final BiomeGenBase[] p = SurfaceHarness.prosperityBiomes();
        final BiomeGenBase[] s = SurfaceHarness.shatteredBiomes();
        SurfaceHarness.recordAllAllocations(p, s);
        // 逐列异群系断言要求降级门零介入（正常态）
        check(GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY).degraded() == Degraded.NONE
            && GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_SHATTERED).degraded() == Degraded.NONE,
            "前提：两维 degraded==NONE（正常态表层链全量生效）");

        checkProsperityColumns(p);
        checkShatteredColumns(s);
        checkProducerAgreesWithAuthority(p, s);
        checkSensitivity();

        if (failures > 0) {
            System.out.println("SURFACE TRANSPOSITION CHECK FAIL: failures=" + failures + " passed=" + passed);
            System.exit(1);
        }
        System.out.println("SURFACE TRANSPOSITION CHECK PASS: assertions=" + passed);
    }

    // ————— dim78：逐列异群系 ⇒ 表层/主体必须逐列对号 —————

    private static void checkProsperityColumns(BiomeGenBase[] p) throws Exception {
        final String tag = "dim78";
        final Block[] blocks = new Block[65536];
        final byte[] meta = new byte[65536];
        final BiomeGenBase[] array = new BiomeGenBase[256];
        final int[] pattern = new int[256];
        final int baseX = CHUNK_X * 16;
        final int baseZ = CHUNK_Z * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int idx = x + z * 16; // 生产者口径（vanilla 侧）
                pattern[idx] = pattern78(x, z);
                array[idx] = p[pattern[idx]];
            }
        }
        final GTSRChunkProviderBase provider = SurfaceHarness.provider(true);
        SurfaceHarness.runRealSurface(provider, CHUNK_X, CHUNK_Z, blocks, meta, array);

        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        int topOk = 0;
        int bodyOk = 0;
        int depthOk = 0;
        int idOk = 0;
        int bad = 0;
        final TreeSet<Integer> distinct = new TreeSet<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int idx = x + z * 16;
                // 期望值直接由列坐标算出（不经任何下标表达式）：
                // 消费侧若把 (x,z)/(z,x) 读反，取到的是 p[pattern78(z, x)]，与此处不等。
                final BiomeGenBase want = p[pattern78(x, z)];
                distinct.add(pattern[idx]);
                final int wx = baseX + x;
                final int wz = baseZ + z;
                final int height = ProsperityTerrainProfile.heightAt(SurfaceHarness.SEED, wx, wz);
                final int column = x << 12 | z << 8;
                if (blocks[column | height] != want.topBlock) {
                    bad++;
                    continue;
                }
                topOk++;
                if (meta[column | height] != (byte) want.field_150604_aj) {
                    bad++;
                    continue;
                }
                // 裸露面以下整段换 base（filler 复用 base ⇒ 同实例）：逐列必须落在本列群系的 base 上
                boolean body = true;
                for (int y = height - 1; y > 3; y--) {
                    if (blocks[column | y] != want.fillerBlock) {
                        body = false;
                        break;
                    }
                }
                if (body) {
                    bodyOk++;
                }
                // filler 深度 = 独立重算的列哈希（钉 x/z→世界坐标方向）
                final int depth = 1 + (int) (independentMix(SurfaceHarness.SEED, wx, wz) & 1);
                final boolean fillerRun = blocks[column | height - 1] == want.fillerBlock
                    && (depth == 1 || blocks[column | height - depth] == want.fillerBlock);
                if (fillerRun) {
                    depthOk++;
                }
                final GTSRBiomeAuthority.Resolution r = authority.of(want);
                if (r.resolved() && r.biomeId != null && r.biomeId.rosterIndex() == pattern[idx]) {
                    idOk++;
                }
            }
        }
        check(bad == 0 && topOk == 256, tag + " 逐列 top+topMeta 全部对号（转置后不可能成立）cols=" + topOk
            + " bad=" + bad);
        check(bodyOk == 256, tag + " 逐列主体整段换 base 对号 cols=" + bodyOk);
        check(depthOk == 256, tag + " filler 深度与 (worldX,worldZ) 独立重算一致 cols=" + depthOk);
        check(idOk == 256, tag + " 每列群系的 L1 rosterIndex 与指派下标一致 cols=" + idOk);
        check(distinct.size() == 4, tag + " 合成数组逐列覆盖 4 个群系（非整 chunk 单一群系）distinct=" + distinct);
    }

    // ————— dim79：逐列异群系 ⇒ top 对号、filler 段后主体保持 corestone —————

    private static void checkShatteredColumns(BiomeGenBase[] s) throws Exception {
        final String tag = "dim79";
        final Block[] blocks = new Block[65536];
        final byte[] meta = new byte[65536];
        final BiomeGenBase[] array = new BiomeGenBase[256];
        final int[] pattern = new int[256];
        final int baseX = CHUNK_X * 16;
        final int baseZ = CHUNK_Z * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int idx = x + z * 16;
                pattern[idx] = pattern79(x, z);
                array[idx] = s[pattern[idx]];
            }
        }
        final GTSRChunkProviderBase provider = SurfaceHarness.provider(false);
        SurfaceHarness.runRealSurface(provider, CHUNK_X, CHUNK_Z, blocks, meta, array);

        int topOk = 0;
        int fillerOk = 0;
        int bodyKept = 0;
        int bad = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final BiomeGenBase want = s[pattern79(x, z)]; // 同上：不经下标表达式的直接期望
                final int wx = baseX + x;
                final int wz = baseZ + z;
                final int height = ShatteredTerrainProfile.heightAt(SurfaceHarness.SEED, wx, wz);
                final int column = x << 12 | z << 8;
                if (blocks[column | height] != want.topBlock) {
                    bad++;
                    continue;
                }
                topOk++;
                final int depth = 1 + (int) (independentMix(SurfaceHarness.SEED, wx, wz) & 1);
                boolean filler = true;
                for (int y = height - 1; y >= height - depth; y--) {
                    if (blocks[column | y] != want.fillerBlock) {
                        filler = false;
                    }
                }
                if (filler) {
                    fillerOk++;
                }
                boolean kept = true;
                for (int y = height - depth - 1; y > 3; y--) {
                    if (blocks[column | y] != BlocksGTSR.shatteredCorestone) {
                        kept = false;
                        break;
                    }
                }
                if (kept) {
                    bodyKept++;
                }
            }
        }
        check(bad == 0 && topOk == 256, tag + " 逐列 top 全部对号 cols=" + topOk + " bad=" + bad);
        check(fillerOk == 256, tag + " 逐列 filler 段落本列群系 base 且深度合独立重算 cols=" + fillerOk);
        check(bodyKept == 256, tag + " filler 段以下主体保持 corestone（差异②逐列成立）cols=" + bodyKept);
    }

    // ————— 生产侧 ↔ 细层单点 ↔ L1 身份（真实 manager 路径；B1 双面语义） —————

    /**
     * <b>B1 GenLayer 双面化后的同解面</b>（旧断言"plane 列 == chunk 身份 ordinalAt"在 1:1
     * voronoi 平面下于边界列合法不等，已按任务包更新为双面语义）：
     * <ol>
     * <li><b>同源同解</b>：{@code loadBlockGeneratorData} 的 16×16 平面列与<b>细层单点</b>
     * {@code getBiomeGenAt(blockX, blockZ)}（= EndlessIDs/vanilla 懒回填回调的同一落点）逐列相等
     * ——平面批量采样、单点采样、懒回填三条路出自同一条链必须互相同解；</li>
     * <li>平面列全部为本维名册成员（L1 账本 {@code of(...)} 解析得到 roster 身份，无外来群系）；</li>
     * <li><b>双面真实性</b>：平面列与 chunk 身份（{@code ordinalAt} 粗层中心口径）不同处的比例
     * &gt; 0（证明 1:1 细面真实生效而非恒等平铺粗层）且 &lt; 25%（防退化成噪点）。</li>
     * </ol>
     */
    private static void checkProducerAgreesWithAuthority(BiomeGenBase[] p, BiomeGenBase[] s) throws Exception {
        final GTSRDimensionDef def78 = SurfaceHarness.def(true, p, SurfaceHarness.prosperityWeights());
        final GTSRWorldChunkManager mgr78 = SurfaceHarness.manager(true, def78);
        final GTSRBiomeAuthority a78 = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        int ok = 0;
        int foreign = 0;
        int differsFromIdentity = 0;
        for (int cx = -5; cx < 5; cx++) {
            for (int cz = -5; cz < 5; cz++) {
                final BiomeGenBase[] array = mgr78.loadBlockGeneratorData(null, cx * 16, cz * 16, 16, 16);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        final BiomeGenBase column = array[x + z * 16];
                        // 生产者写 dx+dz*width；细层单点（懒回填同落点）按块坐标解析：两侧必须同一实例
                        if (column == mgr78.getBiomeGenAt(cx * 16 + x, cz * 16 + z)) {
                            ok++;
                        }
                        if (column == null || !a78.of(column)
                            .resolved()) {
                            foreign++;
                        }
                        if (column != a78.ordinalAt(cx * 16 + x, cz * 16 + z).biome) {
                            differsFromIdentity++;
                        }
                    }
                }
            }
        }
        check(ok == 100 * 256, "真实 manager 平面列 == 细层单点 getBiomeGenAt（dim78 100 chunk，懒回填同解）hits="
            + ok);
        check(foreign == 0, "dim78 平面列全部为本维名册成员（外来/null 列 " + foreign + "）");
        final double dual78 = (double) differsFromIdentity / (100 * 256);
        check(dual78 > 0.0D && dual78 < 0.25D, "dim78 双面真实性：平面列 ≠ chunk 身份比例 " + pct(dual78)
            + " ∈ (0%,25%)（0=细面恒等平铺粗层，≥25%=噪点化）");

        final GTSRDimensionDef def79 = SurfaceHarness.def(false, s, SurfaceHarness.shatteredWeights());
        final GTSRWorldChunkManager mgr79 = SurfaceHarness.manager(false, def79);
        final GTSRBiomeAuthority a79 = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_SHATTERED);
        int ok79 = 0;
        int foreign79 = 0;
        int differs79 = 0;
        for (int cx = -5; cx < 5; cx++) {
            for (int cz = -5; cz < 5; cz++) {
                final BiomeGenBase[] array = mgr79.loadBlockGeneratorData(null, cx * 16, cz * 16, 16, 16);
                for (int i = 0; i < 256; i++) {
                    final BiomeGenBase column = array[i];
                    if (column != null && column == mgr79.getBiomeGenAt(cx * 16 + (i & 15), cz * 16 + (i >> 4))) {
                        ok79++;
                    }
                    if (column != null && !a79.of(column)
                        .resolved()) {
                        foreign79++;
                    }
                    if (column != a79.ordinalAt(cx * 16 + (i & 15), cz * 16 + (i >> 4)).biome) {
                        differs79++;
                    }
                }
            }
        }
        check(ok79 == 100 * 256, "真实 manager 平面列 == 细层单点 getBiomeGenAt（dim79 100 chunk，i&15=x / "
            + "i>>4=z，懒回填同解）hits=" + ok79);
        check(foreign79 == 0, "dim79 平面列全部为本维名册成员（外来/null 列 " + foreign79 + "）");
        final double dual79 = (double) differs79 / (100 * 256);
        check(dual79 > 0.0D && dual79 < 0.25D, "dim79 双面真实性：平面列 ≠ chunk 身份比例 " + pct(dual79)
            + " ∈ (0%,25%)");
    }

    private static String pct(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", ratio * 100.0D);
    }

    /**
     * 灵敏度自检：数组按 {@code x + z*16} 填，若消费侧退回 {@code z + x*16}，
     * 有多少列会取到<b>不同</b>的群系。为 0 则说明本工具的断言对转置免疫（假绿），必须报警。
     */
    private static void checkSensitivity() {
        // 「若消费侧读回 z + x*16，有多少列会取到别的群系」——数组按 x + z*16 填，
        // 故第 (x,z) 列被指派 f(x,z)，镜像口径会读到 f(z,x)。两者不等的列数即断言的覆盖面。
        int differs78 = 0;
        int differs79 = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (pattern78(x, z) != pattern78(z, x)) {
                    differs78++;
                }
                if (pattern79(x, z) != pattern79(z, x)) {
                    differs79++;
                }
            }
        }
        // 阈值取 96：对角线 16 列两种口径同槽（不可分辨），其余按图案周期性——实测每张图案
        // 有 128/256 列可分辨。低于 96 说明图案退化成对称/整 chunk 同群系，断言将失去抓力。
        check(differs78 >= 96, "灵敏度：dim78 图案若消费侧读回 z+x*16 将影响 " + differs78
            + "/256 列（>=96 才算抓得住转置）");
        check(differs79 >= 96, "灵敏度：dim79 图案若消费侧读回 z+x*16 将影响 " + differs79 + "/256 列");
    }

    private static int pattern78(int x, int z) {
        return (x * 3 + z * 5) & 3;
    }

    private static int pattern79(int x, int z) {
        return (x * 5 + z * 3 + (x & z)) & 3;
    }

    /**
     * 框架 {@code GTSRChunkProviderBase.mixColumn} 的<b>独立</b>重写（断言侧必须自己算一遍，
     * 不能调用被测代码，否则同侧编译错误互相掩盖）。
     */
    private static long independentMix(long worldSeed, int x, int z) {
        long h = worldSeed ^ (x * 0x27D4EB2F165667C5L) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
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
        System.out.println("TRN PASS: " + msg);
    }

    private static void fail(String msg) {
        failures++;
        System.out.println("TRN FAIL: " + msg);
    }
}
