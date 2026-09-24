import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.blocks.BlockProsperityFalling;
import com.miaokatze.gtsr.common.blocks.BlockProsperityStone;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.Degraded;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.ProsperityBiomes;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityNaturalBase;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityNaturalTop;

/**
 * dim78 表层行为矩阵断言（P2b 起由<b>源码文本钉</b>改为<b>行为钉</b>；主代理裁决：文本钉是
 * 重构必碎、且从未断言过行为的技术债）。四群系矩阵
 * {@code BiomeId → (topBlock, TOP_META, fillerBlock, FILLER_META, baseBlock)} 的期望值全部来自
 * <b>单一真值</b>——名册枚举 {@link BiomeId} 的迭代序 + 群系类自身声明（实例字段 topBlock/
 * fillerBlock/field_150604_aj 与类常量 TOP_META/FILLER_META 反射自读），工具内<b>不抄任何
 * 常量表</b>；实际值由<b>真实表层链</b>（provider 静态缝 {@link ChunkProviderProsperityRuins
 * #applyBiomeSurface}，provideChunk 事件段之后的同一实现体）逐格产出后对账。
 * <p>
 * <b>真实注册链</b>：群系实例经 {@link ProsperityBiomes#init}（→ {@code GTSRBiomeBase.allocate}
 * → {@code isBiomeIdFree} 空闲校验 → 构造即占槽 → {@code recordAllocation}）产出，<b>不</b>手工
 * {@code new BiomeXxx(id)} 绕过配槽——绕过正是上一轮"断言全绿、实机 no-op"假绿的根因
 * （v12030 报告 §3；配槽行为本身由 {@code BiomeAllocationCheck} 四场景独立钉住）。
 * <p>
 * 断言面（每格失败信息点名 BiomeId + 列坐标 + 期望/实际方块）：
 * <ol>
 * <li>注册链产出 5/5（v1.20.39 T5 起 4 selector + sanzu roster-only）、degraded==NONE、
 * 账本身份与名册互洽（{@code identityOf} 对全部实例逐一命中、
 * 对外来群系（plains）返回 null）；</li>
 * <li>群系声明自洽：{@code field_150604_aj == TOP_META} 常量、meta 常量 ∈ 0..15、
 * top/filler 属独立方块族（§12 修订 2：非冻结的 prosperitySurface）；</li>
 * <li>表层链实际输出＝声明：每列裸露面落 top 且 meta==TOP_META；其下 filler 段（深度按
 * {@code mixColumn} 的<b>独立重写</b>重算，同 {@code SurfaceTranspositionCheck} 断言侧纪律）
 * 落 filler 且 meta==FILLER_META；filler 段以下整段主体 == <b>prosperityStone</b>
 * （v1.20.39 T2/T8 重钉：G4 地底石化后 baseBlockOf 全群系统一返石，旧"主体 == 该群系 filler
 * 方块"的 §12 修订 4 等式随石化退役）且
 * meta==0、全程无 stone 残留；</li>
 * <li>四元组互异 / top 冲突 / filler 冲突（运行时实例逐对比对）；</li>
 * <li>注册链与配置的<b>接线级</b>保底（离线无法执行 FML/GameRegistry 与 lang 加载，这三段
 * 保留源码文本口径并在输出行明示：方块声明+registerBlock、四槽挂接锚点+id 段、lang 键）。</li>
 * </ol>
 * <p>
 * 运行（不再是 JEP330 单文件——本工具需要 MC/Forge classpath 驱动真实链）：
 * {@code bash tools/dim1/surface_checks.sh}（编译+运行均在 [1]/[3] 步）。
 * 可证伪性演示记录：{@code plan/维度计划/调查取证/Phase1按片报告/p2b-identity-closure-20260919.md}。
 */
public class SurfaceBiomeMatrixCheck {

    /** 表层链驱动用的非零 chunk 原点（同时钉住 baseX/baseZ 参与列哈希的一侧）。 */
    private static final int CHUNK_X = 5;
    private static final int CHUNK_Z = -3;

    private static final String[] NATURAL_BLOCKS = { "prosperitySteppeTop", "prosperitySteppeBase",
        "prosperityForestTop", "prosperityForestBase", "prosperityWastesTop", "prosperityWastesBase",
        "prosperitySwampTop", "prosperitySwampBase", "prosperityTuftRust", "prosperityTuftCopper",
        "prosperityRustLog", "prosperityRustLeaves",
        // P17-SB1 名册同步（12→24：木 3 档 + 花 4 + 草 2；P17-S-B2 再 24→27 加沙 3 件。
        // 全部非 top，不进 SurfaceGate 成员表）。
        // 漏进本数组＝lang/注册文本钉静默不覆盖（P17-D §3★），故新方块随名册入册。
        "prosperityCopperLog", "prosperityCopperLeaves", "prosperityBrassLog", "prosperityBrassLeaves",
        "prosperityMarshLog", "prosperityMarshLeaves", "prosperityFlowerRust", "prosperityFlowerPatina",
        "prosperityFlowerBrass", "prosperityFlowerMarsh", "prosperityTuftSedge", "prosperityTuftBristle",
        // P17-S-B2 名册同步（24→27：沙/砂砾 3 件；同样全部非 top，不进 SurfaceGate 成员表）。
        "prosperitySilicaSand", "prosperityCoarseSand", "prosperityRiverGravel",
        // v1.20.39 T2/T8 名册同步（27→28：prosperityStone 地底石化主体石；非 top，不进 SurfaceGate）。
        "prosperityStone",
        // v1.20.43 P22-B S2 名册同步（28→31：岛心巨树三件套 p21 §4；全部非 top，不进 SurfaceGate，
        // 漏进本数组＝lang/注册文本钉静默不覆盖——同 P17-D §3★ 纪律）。
        "prosperityZenithLog", "prosperityJadeLeaves", "prosperityRoostGlow" };

    private static final String[] LANG_FILES = { "src/main/resources/assets/gtsr/lang/en_US.lang",
        "src/main/resources/assets/gtsr/lang/zh_CN.lang" };

    private static final String SRC = "src/main/java/com/miaokatze/gtsr";

    private static final Map<Block, String> LABELS = new IdentityHashMap<>();
    private static final Map<String, String[]> MATRIX = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        quietLogging();
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        buildLabels();
        final Path root = findRepoRoot();

        // —— 1. 真实注册链：ProsperityBiomes.init → allocate → 构造 → recordAllocation ——
        final GTSRDimensionDef def = new GTSRDimensionDef(
            GTSRBiomeAuthority.DIM_KEY_PROSPERITY, "Prosperity Matrix", 0x50524F53L, 78, 100, () -> true,
            WorldProviderProsperityRuins.class, ChunkProviderProsperityRuins::new);
        ProsperityBiomes.init(def);
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        check(authority.degraded() == Degraded.NONE,
            "real registration chain must yield degraded=NONE, got " + authority.degraded()
                + " (allocation=[" + authority.allocationSummary() + "])");
        check(def.getBiomeTable().size() == 4, "def biome table size=" + def.getBiomeTable().size());
        check(ChunkProviderProsperityRuins.identityOf(BiomeGenBase.plains) == null,
            "identityOf(plains) must be null (foreign biome not nameable through L1)");

        // —— 2. 声明侧（期望值全部来自 roster + 群系类自身声明） ——
        final Map<BiomeId, BiomeGenBase> roster = new LinkedHashMap<>();
        final List<BiomeId> keys = new ArrayList<>();
        for (final BiomeId key : BiomeId.values()) {
            if (!key.dimKey().equals(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)) {
                continue;
            }
            final BiomeGenBase biome = authority.biomeOf(key);
            check(biome != null, key + " not allocated (ledger biomeOf=null)");
            check(ChunkProviderProsperityRuins.identityOf(biome) == key,
                key + " identityOf mismatch — provider L1 table not nameable");
            // v1.20.39 T5/T8 重钉（plan §3.3）：SANZU_RIVER 走名册配槽但<b>不挂 def 表</b>
            //（def 表保持 4 项 selector 名册）——rosterIndex 4 对 def 表越界是设计事实不是漂移。
            if (key != BiomeId.SANZU_RIVER) {
                check(biome == def.getBiomeTable().get(key.rosterIndex()),
                    key + " rosterIndex " + key.rosterIndex() + " != def table slot order");
            } else {
                check(!def.getBiomeTable().contains(biome),
                    "SANZU_RIVER must stay out of the def (selector) biome table (roster-only, plan §3.3)");
            }
            final int topMeta = classConst(biome.getClass(), "TOP_META");
            final int fillerMeta = classConst(biome.getClass(), "FILLER_META");
            check(biome.field_150604_aj == topMeta,
                key + " field_150604_aj=" + biome.field_150604_aj + " != TOP_META=" + topMeta);
            check(topMeta >= 0 && topMeta < 16, key + " TOP_META out of 0..15: " + topMeta);
            check(fillerMeta >= 0 && fillerMeta < 16, key + " FILLER_META out of 0..15: " + fillerMeta);
            // T8 重钉注：sanzu top = prosperityRiverGravel（BlockProsperityFalling，河床语义）、
            // filler = prosperityStone（BlockProsperityStone）——与四 selector 群系的
            // NaturalTop/NaturalBase 族不同类，按 T5 群系声明单独钉类族。
            if (key == BiomeId.SANZU_RIVER) {
                check(biome.topBlock instanceof BlockProsperityFalling,
                    key + " topBlock not BlockProsperityFalling: " + label(biome.topBlock));
                check(biome.fillerBlock instanceof BlockProsperityStone,
                    key + " fillerBlock not BlockProsperityStone: " + label(biome.fillerBlock));
            } else {
                check(biome.topBlock instanceof BlockProsperityNaturalTop,
                    key + " topBlock not BlockProsperityNaturalTop: " + label(biome.topBlock));
                check(biome.fillerBlock instanceof BlockProsperityNaturalBase,
                    key + " fillerBlock not BlockProsperityNaturalBase: " + label(biome.fillerBlock));
            }
            check(biome.topBlock != BlocksGTSR.prosperitySurface
                && biome.fillerBlock != BlocksGTSR.prosperitySurface,
                key + " still wired to frozen meta block prosperitySurface (plan §12-2 violation)");
            roster.put(key, biome);
            keys.add(key);
            MATRIX.put(key.name(), new String[] { label(biome.topBlock), String.valueOf(topMeta),
                label(biome.fillerBlock), String.valueOf(fillerMeta) });
        }
        check(keys.size() == 5, "prosperity roster members != 5 (T5 sanzu 5th): " + keys);

        // —— 3. 行为侧：真实表层链逐格对账（取代旧文本钉 return BiomeXxx.FILLER_META /
        //        return BlocksGTSR.xBase / instanceof BiomeXxx>=2） ——
        // P22 A2b：p20 §31-1 假绿处置——本工具走「(b) 显式绕过混合带」这条路，且把"绕过"从
        // 巧合变断言。本 JVM 不向 DimensionRegistrar 登记 dim78 def ⇒ 混合带链盐解析不到 ⇒
        // forChunk 恒 null（P20S1Probe/A2bProbe 用反射登记 def 正是为了"让它生效"，本工具不登记
        // 正是为了"逐格对声明恒等"）。今后若有人给本 harness 补登记，下面第一条 check 立即变红，
        // 强制在"改断言允许 repainted"与"保持绕过+迁走恒等钉"之间重新裁决，不得静默。
        // P22 A2b G1 后 filler 写格也走同一 selector（fillerAt）——恒等钉同时覆盖两条出口。
        final GTSRChunkProviderBase provider = SurfaceHarness.provider(true);
        check(
            com.miaokatze.gtsr.common.dimension.framework.GTSRSurfaceBorderBand
                .forChunk(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, SurfaceHarness.SEED, CHUNK_X * 16, CHUNK_Z * 16) == null,
            "p20 §31-1 bypass evidence broken: border band active in this JVM, identity pin would flip to repainted"
                + " — re-adjudicate (allow same-roster top/filler) OR keep unbound harness");
        int wetCols = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField.lakeWetBandAt(
                    SurfaceHarness.SEED, CHUNK_X * 16 + x, CHUNK_Z * 16 + z)) {
                    wetCols++;
                }
            }
        }
        check(wetCols == 0, "driven chunk unexpectedly contains wet-band columns: " + wetCols);
        System.out.println(
            "MATRIX BYPASS PATH (p22-a2b, p20 §31-1): (b) 显式绕过混合带 — forChunk==null（本 JVM 未登记 def）"
                + " + 驱动窗湿带列=0（砾/羽化檐不介入）；混合带 top/filler 改派行为由 P20S1Probe 与"
                + " plan/tmp/p22-a2b/A2bProbe 钉（含 top 零漂移与 dim79 逐字节对拍）");
        for (final BiomeId key : keys) {
            assertSurfaceMatrix(provider, key, roster.get(key));
        }

        // —— 4. 四元组互异（运行时实例逐对比对） ——
        for (int i = 0; i < keys.size(); i++) {
            for (int j = i + 1; j < keys.size(); j++) {
                final BiomeGenBase a = roster.get(keys.get(i));
                final BiomeGenBase b = roster.get(keys.get(j));
                final String[] ma = MATRIX.get(keys.get(i).name());
                final String[] mb = MATRIX.get(keys.get(j).name());
                check(a.topBlock != b.topBlock, "topBlock collision: " + keys.get(i) + " vs " + keys.get(j));
                check(a.fillerBlock != b.fillerBlock,
                    "fillerBlock collision: " + keys.get(i) + " vs " + keys.get(j));
                final boolean identical = java.util.Arrays.equals(ma, mb);
                check(!identical,
                    "surface quadruple identical: " + keys.get(i) + " vs " + keys.get(j));
            }
        }

        // —— 5. 接线级保底（离线不可执行 FML/GameRegistry 与 lang 加载，保留文本口径） ——
        final String blocks = read(root, SRC + "/common/blocks/BlocksGTSR.java");
        final String loader = read(root, SRC + "/loader/BlockLoader.java");
        for (final String field : NATURAL_BLOCKS) {
            check(blocks.contains("public static Block " + field + ";"), "BlocksGTSR missing field " + field);
            check(loader.contains("registerBlock(BlocksGTSR." + field + ","),
                "BlockLoader missing register " + field);
            check(loader.contains("BlocksGTSR." + field), "BlockLoader missing init " + field);
        }
        check(loader.contains("prosperity natural blocks registered"), "BlockLoader missing evidence log anchor");
        // —— 6. biomeId 分配锚点（真实链已由 1 段跑通；此处只钉 Config 段与日志锚点存在） ——
        final String biomes = read(root, SRC + "/common/dimension/prosperity/biome/ProsperityBiomes.java");
        final String config = read(root, SRC + "/config/Config.java");
        // v1.20.39 T5/T8 重钉（plan §3.3）：名册槽位数 4→5（第 5 元 sanzu 走同一扫描配槽）。
        check(biomes.contains("private static final int BIOME_SLOT_COUNT = 5;"), "BIOME_SLOT_COUNT != 5");
        check(biomes.contains("[GTSR] prosperity biomes: "), "missing success log anchor (plan §6.1 grep point)");
        check(biomes.contains("already occupied by"), "missing degrade warn anchor (plan R3)");
        final int idStart = Integer
            .parseInt(expectGroup(config, "prosperityBiomeIdStart = (\\d+);", "Config"));
        final int shatteredStart = Integer
            .parseInt(expectGroup(config, "shatteredBiomeIdStart = (\\d+);", "Config"));
        check(idStart == 180, "prosperityBiomeIdStart moved: " + idStart);
        check(idStart + 4 <= shatteredStart, "id range overlap: prosperity " + idStart + ".." + (idStart + 3));
        for (final BiomeId key : keys) {
            check(authority.actualIdOf(key) >= idStart && authority.actualIdOf(key) < shatteredStart,
                key + " allocated id " + authority.actualIdOf(key) + " outside configured band");
        }
        // —— 7. lang 键齐（en_US + zh_CN，NATURAL_BLOCKS.length 键 × 2 文件；P17-SB1 起 =24） ——
        for (final String lang : LANG_FILES) {
            final String text = read(root, lang);
            for (final String field : NATURAL_BLOCKS) {
                final String k = "tile." + Character.toUpperCase(field.charAt(0)) + field.substring(1) + ".name=";
                check(text.contains(k), lang + " missing lang key " + k);
            }
        }

        // —— 汇总证据 ——
        System.out.println("SURFACE MATRIX (BiomeId -> topBlock/topMeta/fillerBlock/fillerMeta, runtime-declared):");
        for (final Map.Entry<String, String[]> e : MATRIX.entrySet()) {
            System.out.println(
                "  " + String.format("%-16s", e.getKey()) + " -> " + String.join(" / ", e.getValue()));
        }
        System.out.println("MATRIX PASS: biomes=5(incl. sanzu) quadruplesDistinct=true providerTableSync=behavioral(L1 identityOf)");
        System.out.println(
            "REGISTRATION PASS: blocks=" + NATURAL_BLOCKS.length
                + " (8 terrain + 4 decor + 6 wood + 6 flora + 3 sand + 1 stone(G4) + 3 island-tree(P22-S2))"
                + " declared+registered (wiring-level)");
        System.out
            .println("BIOMEID PASS: real-chain 5/5 slots (incl. sanzu roster-only) via ProsperityBiomes.init, idStart=" + idStart
                + ".. band, no-degrade anchors present");
        System.out.println("LANG PASS: " + LANG_FILES.length + " lang files x " + NATURAL_BLOCKS.length + " keys");
    }

    /**
     * 单群系整 chunk 行为对账：真实 {@code generateTerrain} + 真实表层缝，然后逐列检查
     * top 格 / filler 段 / 整段主体的方块与 meta。期望值全部来自声明侧（参数 {@code biome}
     * 实例字段与类常量），实际值全部来自输出数组。
     */
    private static void assertSurfaceMatrix(GTSRChunkProviderBase provider, BiomeId key, BiomeGenBase biome)
        throws Exception {
        final Block[] blocks = new Block[65536];
        final byte[] meta = new byte[65536];
        final BiomeGenBase[] biomes = new BiomeGenBase[256];
        java.util.Arrays.fill(biomes, biome);
        SurfaceHarness.runRealSurface(provider, CHUNK_X, CHUNK_Z, blocks, meta, biomes);
        final int topMeta = classConst(biome.getClass(), "TOP_META");
        final int fillerMeta = classConst(biome.getClass(), "FILLER_META");
        final int baseX = CHUNK_X * 16;
        final int baseZ = CHUNK_Z * 16;
        int cols = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int column = x << 12 | z << 8;
                int bedrockTop = -1;
                while (blocks[column | (bedrockTop + 1)] == Blocks.bedrock) {
                    bedrockTop++;
                }
                int h = 255;
                while (h > bedrockTop && blocks[column | h] == null) {
                    h--;
                }
                final String where = key + "@" + (baseX + x) + "," + (baseZ + z);
                check(blocks[column | h] == biome.topBlock, where + " top cell expected "
                    + label(biome.topBlock) + " got " + label(blocks[column | h]));
                check(meta[column | h] == (byte) topMeta, where + " top meta expected " + topMeta
                    + " got " + meta[column | h]);
                final int depth = 1 + (int) (independentMix(SurfaceHarness.SEED, baseX + x, baseZ + z) & 1);
                int y = h - 1;
                for (int d = 0; d < depth && y > bedrockTop; d++, y--) {
                    check(blocks[column | y] == biome.fillerBlock, where + " filler#" + d + " expected "
                        + label(biome.fillerBlock) + " got " + label(blocks[column | y]));
                    check(meta[column | y] == (byte) fillerMeta, where + " filler#" + d + " meta expected "
                        + fillerMeta + " got " + meta[column | y]);
                }
                for (; y > bedrockTop; y--) {
                    // v1.20.39 T2/T8 重钉（plan §3.7 G4 石化）：wholeBody 期望 = prosperityStone
                    //（baseBlockOf 全群系统一返石；壤土 base 只在 filler 段，上一循环已钉）。
                    check(blocks[column | y] == BlocksGTSR.prosperityStone, where + " body expected stone="
                        + label(BlocksGTSR.prosperityStone) + " got " + label(blocks[column | y])
                        + " (baseBlockOf L1 table drift or stone left)");
                    check(meta[column | y] == 0, where + " body meta must stay 0, got " + meta[column | y]);
                }
                cols++;
            }
        }
        System.out.println("  MATRIX OK: " + String.format("%-16s", key.name())
            + " top/filler/body+meta match across all " + cols + " columns (real surface chain)");
    }

    /** {@code GTSRChunkProviderBase.mixColumn} 的独立重写（断言侧纪律，同 SurfaceTranspositionCheck）。 */
    private static long independentMix(long worldSeed, int x, int z) {
        long h = worldSeed ^ (x * 0x27D4EB2F165667C5L) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }

    /** 群系类自身的 public static final int 常量（单一真值，反射读取）。 */
    private static int classConst(Class<?> holder, String name) throws ReflectiveOperationException {
        return holder.getField(name).getInt(null);
    }

    private static void buildLabels() throws IllegalAccessException {
        for (final java.lang.reflect.Field f : BlocksGTSR.class.getFields()) {
            if (f.getType() == Block.class) {
                final Object v = f.get(null);
                if (v != null) {
                    LABELS.putIfAbsent((Block) v, "BlocksGTSR." + f.getName());
                }
            }
        }
        for (final java.lang.reflect.Field f : Blocks.class.getFields()) {
            if (f.getType() == Block.class) {
                final Object v = f.get(null);
                if (v != null) {
                    LABELS.putIfAbsent((Block) v, "Blocks." + f.getName());
                }
            }
        }
    }

    private static String label(Block b) {
        if (b == null) {
            return "null";
        }
        final String l = LABELS.get(b);
        return l != null ? l : b.getClass().getSimpleName() + "@" + System.identityHashCode(b);
    }

    /** 正则提取（唯一匹配），缺失/多匹配即 FAIL。 */
    private static String expectGroup(String src, String regex, String where) throws IOException {
        final Matcher m = Pattern.compile(regex).matcher(src);
        check(m.find(), where + " missing pattern: " + regex);
        final String group = m.group(1);
        check(!m.find(), where + " duplicated pattern: " + regex);
        return group;
    }

    private static String read(Path root, String rel) throws IOException {
        final Path path = root.resolve(rel);
        check(Files.exists(path), "missing file: " + rel);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 从 cwd 向上找仓库根（含 src/main/java 的最近祖先），支持子目录运行。 */
    private static Path findRepoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++) {
            if (Files.exists(dir.resolve("src/main/java"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root with src/main/java not found from cwd");
    }

    /** 生产链的 [GTSR] INFO 日志在离线 JVM 不需要（注册链判定全走账本），压掉保持输出确定。 */
    private static void quietLogging() {
        try {
            final Path cfg = Paths.get("temp", "sbm-log4j2.xml");
            if (cfg.getParent() != null) {
                Files.createDirectories(cfg.getParent());
            }
            Files.write(cfg, ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Configuration status=\"OFF\"><Loggers><Root level=\"OFF\"/></Loggers></Configuration>\n")
                    .getBytes(StandardCharsets.UTF_8));
            System.setProperty("log4j.configurationFile", cfg.toAbsolutePath().toString());
        } catch (Exception e) {
            System.out.println("NOTE: log4j quiet bootstrap failed (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            System.out.println("MATRIX FAIL: " + msg);
            System.exit(1);
        }
    }
}
