import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStone;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.BiomeZoneSelector;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeBrassWastes;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeFumaroleSwamp;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeGearworkForest;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeRustedSteppe;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityNaturalBase;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityNaturalTop;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.shattered.ChunkProviderShatteredGrounds;
import com.miaokatze.gtsr.common.dimension.shattered.WorldProviderShatteredLands;
import com.miaokatze.gtsr.common.dimension.shattered.ShatteredDecorPlacer;
import com.miaokatze.gtsr.common.dimension.shattered.biome.BiomeAshenPrairie;
import com.miaokatze.gtsr.common.dimension.shattered.biome.BiomeSlagwoodGrove;
import com.miaokatze.gtsr.common.dimension.shattered.biome.BiomeTarBasin;
import com.miaokatze.gtsr.common.dimension.shattered.biome.BiomeVitreousWaste;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredCorestone;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredSurfaceBase;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredSurfaceTop;

/**
 * <b>运行时路径级离线断言</b>（v1.20.30 终验修复轮，教训：v1.20.30 的表层替换在实机全量 no-op，
 * 而当时的断言只盯纯函数/源码文本，漏检了 {@code Block[]} 原始数组 null≠Blocks.air 的裸露面判定）。
 * <p>
 * 本工具以合成 biomes 数组直接驱动 <b>生产代码本体</b>
 * {@link ChunkProviderProsperityRuins#applyBiomeSurface} /
 * {@link ChunkProviderShatteredGrounds#applyBiomeSurface}（即 provideChunk 事件段之后的同一实现体），
 * 断言：
 * <ol>
 * <li>dim78 四群系：表层落群系 top（meta=TOP_META）、其下 1-2 格 filler、其余主体 stone→群系 base
 * 整段换装——产出方块非 stone；</li>
 * <li>dim79 四群系：表层落群系 top、其下 1-2 格 filler（复用 base）、主体保持 corestone——
 * 产出地表非 corestone；</li>
 * <li>回退语义：null 群系列零改动；非本维群系（plains）走 L1 点名不到回退（top/filler 换、
 * base 仍 stone；P2b 起 provider 身份读面已收口 {@code GTSRBiomeAuthority}，判定语义与
 * 旧 instanceof 未命中口径逐格一致）；</li>
 * <li>装饰/结构落点门冒烟：替换<b>前</b>的 stone/corestone 列不过门（解释实机"城外无结构/无装饰"），
 * 替换<b>后</b>的地表方块四群系各自命中 {@link ProsperityDecorPlacer#isNaturalTop}/
 * {@link ProsperityOutpostPlacer#isNaturalProsperityTop}/{@link ShatteredDecorPlacer#isNaturalTop}；</li>
 * <li>biome 数组来源与时序（H1③/H4；<b>P2b 起改为逐列转置敏感断言</b>——原"chunk 内 256 格
 * 同群系"断言对下标/转置错误结构性免疫，plan §6 诚实性公理禁用）：
 * GTSRWorldChunkManager.loadBlockGeneratorData 在生产者口径 {@code dx + dz*width} 下，
 * 对 64×64 块坐标窗<b>逐列</b>等于 {@code mgr.biomeAt(块坐标>>4)}，并带"若生产者写反成
 * {@code dz + dx*width} 会有多少格错位"的灵敏度自检；采样 400 chunk 四群系全覆盖
 * （"看不到其他群系"证伪为表层问题）。</li>
 * </ol>
 * <p>
 * <b>与既有工具的边界</b>：ShatteredTerrainCheck/BiomeZoneCheck 等零 MC 纯函数与接线级断言保持原样；
 * 本工具与 SurfaceDegradationCheck/SurfaceTranspositionCheck/SurfaceByteParityDump 同属
 * MC-classpath 行为断言（需要 patched MC 类可加载，命令见运行手册
 * plan/investigation/v12030-hotfix-replaceruntime-report.md §6）。
 * <p>
 * 命令（Git-Bash 下必须 MSYS2_ARG_CONV_EXCL='*'，classpath 用分号）：
 * <pre>
 * MC=bin/patchedMc; G=~/.gradle/caches/modules-2/files-2.1
 * CP="build/classes/java/main;$MC;$G/com.google.guava/guava/17.0/.../guava-17.0.jar;\
 * $G/org.apache.commons/commons-lang3/3.3.2/.../commons-lang3-3.3.2.jar;\
 * $G/org.apache.logging.log4j/log4j-api/2.0-beta9-fixed/.../log4j-api-2.0-beta9-fixed.jar;\
 * $G/org.apache.logging.log4j/log4j-core/2.0-beta9-fixed/.../log4j-core-2.0-beta9-fixed.jar"
 * javac -cp "$CP" -d temp/checkout tools/dim1/ReplaceSurfaceRuntimeCheck.java
 * MSYS2_ARG_CONV_EXCL='*' java -cp "temp/checkout;$CP" ReplaceSurfaceRuntimeCheck
 * </pre>
 */
public class ReplaceSurfaceRuntimeCheck {

    private static final long WORLD_SEED = 0x53484C53L;
    /** 合成地形列床岩顶（y=0..BEDROCK_TOP 床岩）。 */
    private static final int BEDROCK_TOP = 2;
    /** 合成地形列高度族（多列变化：H0 + (x*5+z*3)%9）。 */
    private static final int H0 = 70;

    private static int failures = 0;
    private static int passed = 0;

    public static void main(String[] args) {
        // 离线 vanilla 字段装配（替代 Block.registerBlocks()：其 addObject 走 FML Loader/GameData，
        // 离线 JVM 的 LaunchClassLoader 类加载器链不可满足）。本工具与生产代码共用这些 public static
        // 字段做<b>身份比较</b>（扫描条件==stone、列底标记 bedrock、isAirOrEmpty 的 air），不触注册表
        // id，故 bedrock/iron_bars 以等价身份实例充当，stone/gravel 用真实 vanilla 类保持语义。
        initVanillaBlocksOffline();
        setupBlockFamily();
        // dim78 四群系
        BiomeGenBase[] prosperity = {
            new BiomeRustedSteppe(180), new BiomeGearworkForest(181),
            new BiomeBrassWastes(182), new BiomeFumaroleSwamp(183) };
        // P2b 身份读面收口 L1 后，provider 的 baseBlockOf/fillerMetaOf 经 GTSRBiomeAuthority
        // 账本解析身份——合成实例必须先经 L1 写入入口 recordAllocation 入账（口径同
        // SurfaceHarness.recordAllAllocations，本工具不共用其群系数组故逐槽自记），
        // 否则两跳（实例账本 → 实际 id 反查）都点名不到，主体会误回退 stone。
        final GTSRBiomeAuthority.BiomeId[] pKeys = { GTSRBiomeAuthority.BiomeId.RUSTED_STEPPE,
            GTSRBiomeAuthority.BiomeId.GEARWORK_FOREST, GTSRBiomeAuthority.BiomeId.BRASS_WASTES,
            GTSRBiomeAuthority.BiomeId.FUMAROLE_SWAMP };
        for (int i = 0; i < 4; i++) {
            GTSRBiomeAuthority.recordAllocation(pKeys[i], 180 + i, 180 + i, prosperity[i]);
        }
        Block[] pTop = { BlocksGTSR.prosperitySteppeTop, BlocksGTSR.prosperityForestTop,
            BlocksGTSR.prosperityWastesTop, BlocksGTSR.prosperitySwampTop };
        Block[] pBase = { BlocksGTSR.prosperitySteppeBase, BlocksGTSR.prosperityForestBase,
            BlocksGTSR.prosperityWastesBase, BlocksGTSR.prosperitySwampBase };
        checkDistinctFamily(pTop, "prosperityTop", "stone");
        checkDistinctFamily(pBase, "prosperityBase", "stone");
        for (int i = 0; i < 4; i++) {
            assertProsperityColumnDriven(prosperity[i], pTop[i], pBase[i], i);
        }
        assertProsperityFallbacks();
        // dim79 四群系
        BiomeGenBase[] shattered = {
            new BiomeAshenPrairie(190), new BiomeSlagwoodGrove(191),
            new BiomeVitreousWaste(192), new BiomeTarBasin(193) };
        Block[] sTop = { BlocksGTSR.shatteredAshTop, BlocksGTSR.shatteredSlagTop,
            BlocksGTSR.shatteredGlassTop, BlocksGTSR.shatteredTarTop };
        Block[] sBase = { BlocksGTSR.shatteredAshBase, BlocksGTSR.shatteredSlagBase,
            BlocksGTSR.shatteredGlassBase, BlocksGTSR.shatteredTarBase };
        checkDistinctFamily(sTop, "shatteredTop", "corestone");
        for (int i = 0; i < 4; i++) {
            assertShatteredColumnDriven(shattered[i], sTop[i], sBase[i], i);
        }
        // 装饰/结构落点门冒烟（替换前必不过门 = 实机症状根因；替换后必过门 = 修复生效）
        assertGateSmoke(pTop, sTop);
        // biome 数组来源链（下标口径/时序契约消费端 + 四群系可达性）
        assertChunkManagerChain(prosperity, shattered);

        if (failures > 0) {
            System.out.println("RUNTIME CHECK FAIL: " + failures + " assertion(s) failed, passed=" + passed);
            System.exit(1);
        }
        System.out.println("RUNTIME CHECK PASS: assertions=" + passed);
    }

    // ————— Block 族装配（镜像 BlockLoader 的构造参数；GameRegistry 注册步骤离线不需要） —————

    private static void initVanillaBlocksOffline() {
        // RFB 编译产物 Blocks.* 是 static final（javac 不可直赋），且本 JVM 无 FML 注册链——用
        // Unsafe.putObject 写入 identity 实例。断言与生产代码共用同一批 static 字段（getstatic 读取，
        // final 对象字段无编译期内联），语义按身份比较保真：stone 用真实 BlockStone，其余以独立
        // BlockStone(id) 实例充当（bedrock 列底标记 / air 占位（isAirOrEmpty 的 null 分支已覆盖
        // 未写入槽位）/ iron_bars 仅进 resolver 键表，id 互异保证互相不相等）。
        try {
            // Blocks.<clinit> 必须<b>先于</b> putObject 运行：反射 getField 不触发类初始化，若在未初始化
            // 的类上写 final 字段，随后首个 getstatic 触发的 <clinit> 会把字段重置回 null 抹掉写入
            //（JDK26 实测；游戏内 FML 启动顺序天然满足此前提）。
            Class.forName("net.minecraft.init.Blocks");
            java.lang.reflect.Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            uf.setAccessible(true);
            sun.misc.Unsafe u = (sun.misc.Unsafe) uf.get(null);
            setFinalStatic(u, new BlockStone(), "stone");
            setFinalStatic(u, new BlockStone(), "cobblestone");
            setFinalStatic(u, new BlockStone(), "gravel");
            setFinalStatic(u, new BlockStone(), "bedrock");
            setFinalStatic(u, new BlockStone(), "iron_bars");
            setFinalStatic(u, new BlockStone(), "air");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("vanilla 离线装配失败：Unsafe 不可用", e);
        }
    }

    private static void setFinalStatic(sun.misc.Unsafe u, Object value, String field) throws NoSuchFieldException {
        java.lang.reflect.Field f = Blocks.class.getField(field);
        u.putObject(u.staticFieldBase(f), u.staticFieldOffset(f), value);
    }

    private static void setupBlockFamily() {
        BlocksGTSR.prosperitySteppeTop = new BlockProsperityNaturalTop(
            "ProsperitySteppeTop", "gtsr:prosperity_steppe_top", "gtsr:prosperity_steppe_top_side",
            "gtsr:prosperity_steppe_base");
        BlocksGTSR.prosperitySteppeBase = new BlockProsperityNaturalBase(
            "ProsperitySteppeBase", "gtsr:prosperity_steppe_base");
        BlocksGTSR.prosperityForestTop = new BlockProsperityNaturalTop(
            "ProsperityForestTop", "gtsr:prosperity_forest_top", "gtsr:prosperity_forest_top_side",
            "gtsr:prosperity_forest_base");
        BlocksGTSR.prosperityForestBase = new BlockProsperityNaturalBase(
            "ProsperityForestBase", "gtsr:prosperity_forest_base");
        BlocksGTSR.prosperityWastesTop = new BlockProsperityNaturalTop(
            "ProsperityWastesTop", "gtsr:prosperity_wastes_top", "gtsr:prosperity_wastes_top_side",
            "gtsr:prosperity_wastes_base");
        BlocksGTSR.prosperityWastesBase = new BlockProsperityNaturalBase(
            "ProsperityWastesBase", "gtsr:prosperity_wastes_base");
        BlocksGTSR.prosperitySwampTop = new BlockProsperityNaturalTop(
            "ProsperitySwampTop", "gtsr:prosperity_swamp_top", "gtsr:prosperity_swamp_top_side",
            "gtsr:prosperity_swamp_base");
        BlocksGTSR.prosperitySwampBase = new BlockProsperityNaturalBase(
            "ProsperitySwampBase", "gtsr:prosperity_swamp_base");
        BlocksGTSR.shatteredCorestone = new BlockShatteredCorestone();
        BlocksGTSR.shatteredAshTop = new BlockShatteredSurfaceTop(
            "ShatteredAshTop", "gtsr:shattered_ash_top", "gtsr:shattered_ash_top_side", "gtsr:shattered_ash_base");
        BlocksGTSR.shatteredAshBase = new BlockShatteredSurfaceBase("ShatteredAshBase", "gtsr:shattered_ash_base");
        BlocksGTSR.shatteredSlagTop = new BlockShatteredSurfaceTop(
            "ShatteredSlagTop", "gtsr:shattered_slag_top", "gtsr:shattered_slag_top_side", "gtsr:shattered_slag_base");
        BlocksGTSR.shatteredSlagBase = new BlockShatteredSurfaceBase(
            "ShatteredSlagBase", "gtsr:shattered_slag_base");
        BlocksGTSR.shatteredGlassTop = new BlockShatteredSurfaceTop(
            "ShatteredGlassTop", "gtsr:shattered_glass_top", "gtsr:shattered_glass_top_side",
            "gtsr:shattered_glass_base");
        BlocksGTSR.shatteredGlassBase = new BlockShatteredSurfaceBase(
            "ShatteredGlassBase", "gtsr:shattered_glass_base");
        BlocksGTSR.shatteredTarTop = new BlockShatteredSurfaceTop(
            "ShatteredTarTop", "gtsr:shattered_tar_top", "gtsr:shattered_tar_top_side", "gtsr:shattered_tar_base");
        BlocksGTSR.shatteredTarBase = new BlockShatteredSurfaceBase(
            "ShatteredTarBase", "gtsr:shattered_tar_base");
    }

    // ————— 合成 chunk 构造（镜像 generateTerrain 的写入形态：床岩 + 主体石 + 其上 null） —————

    private static Block[] syntheticChunk(Block body, BiomeGenBase biome) {
        final Block[] blocks = new Block[65536]; // 关键口径：未写入槽位 = null（非 Blocks.air）
        final byte[] meta = new byte[65536];
        fillColumn(blocks, meta, body, biome);
        return blocks;
    }

    private static byte[] syntheticMeta() {
        return new byte[65536];
    }

    /** 16x16 全列按单一群系填充（每列 height 变化 + 床岩带）。 */
    private static void fillColumn(Block[] blocks, byte[] meta, Block body, BiomeGenBase biome) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int column = x << 12 | z << 8;
                final int height = H0 + (x * 5 + z * 3) % 9;
                for (int y = 0; y <= BEDROCK_TOP; y++) {
                    blocks[column | y] = Blocks.bedrock;
                }
                for (int y = BEDROCK_TOP + 1; y <= height; y++) {
                    blocks[column | y] = body;
                }
            }
        }
    }

    private static int columnHeight(int x, int z) {
        return H0 + (x * 5 + z * 3) % 9;
    }

    // ————— dim78 断言：top/filler/base 全链换装 —————

    private static void assertProsperityColumnDriven(BiomeGenBase biome, Block expectedTop, Block expectedBase,
        int index) {
        final Block[] blocks = syntheticChunk(Blocks.stone, biome);
        final byte[] meta = syntheticMeta();
        final BiomeGenBase[] biomes = new BiomeGenBase[256];
        java.util.Arrays.fill(biomes, biome);
        ChunkProviderProsperityRuins.applyBiomeSurface(WORLD_SEED, 0, 0, blocks, meta, biomes);
        int topOk = 0;
        int baseOk = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int column = x << 12 | z << 8;
                final int height = columnHeight(x, z);
                if (blocks[column | height] != expectedTop) {
                    fail("dim78#" + index + " surface not group top at (" + x + "," + z + "): "
                        + blocks[column | height]);
                    return;
                }
                if (meta[column | height] != (byte) biome.field_150604_aj) {
                    fail("dim78#" + index + " top meta drift at (" + x + "," + z + ")");
                    return;
                }
                // 表层下方：1-2 格 filler（=base 族），其余直到床岩全为 base；不得残留 stone
                int y = height - 1;
                int filler = 0;
                while (y > BEDROCK_TOP && filler < 2 && blocks[column | y] == biome.fillerBlock) {
                    if (meta[column | y] != (byte) 0) {
                        fail("dim78#" + index + " filler meta non-zero at (" + x + "," + z + ")");
                        return;
                    }
                    filler++;
                    y--;
                }
                if (filler < 1) {
                    fail("dim78#" + index + " filler segment missing at (" + x + "," + z + ")");
                    return;
                }
                boolean baseRunOk = true;
                boolean stoneLeft = false;
                for (; y > BEDROCK_TOP; y--) {
                    final Block b = blocks[column | y];
                    if (b == Blocks.stone) {
                        stoneLeft = true;
                    }
                    if (b != expectedBase) {
                        baseRunOk = false;
                    }
                }
                if (stoneLeft || !baseRunOk) {
                    fail("dim78#" + index + " body not fully converted at (" + x + "," + z + ") stoneLeft="
                        + stoneLeft + " baseRunOk=" + baseRunOk);
                    return;
                }
                topOk++;
                baseOk++;
            }
        }
        pass("dim78#" + index + " " + biome.biomeName + ": top/filler/base full-chain replaced cols=" + topOk);
    }

    private static void assertProsperityFallbacks() {
        // null biome：列零改动
        final Block[] blocks = syntheticChunk(Blocks.stone, null);
        final byte[] meta = syntheticMeta();
        final BiomeGenBase[] biomes = new BiomeGenBase[256]; // 全 null
        ChunkProviderProsperityRuins.applyBiomeSurface(WORLD_SEED, 0, 0, blocks, meta, biomes);
        boolean untouched = true;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (blocks[(x << 12 | z << 8) | columnHeight(x, z)] != Blocks.stone) {
                    untouched = false;
                }
            }
        }
        check(untouched, "dim78 null-biome columns must stay untouched");
        // 外来群系（plains）：top/filler 换、base 回退 stone（P2b 后为 L1 名册点名不到回退，
        // 与旧 instanceof 未命中口径逐格一致）
        final Block[] b2 = syntheticChunk(Blocks.stone, BiomeGenBase.plains);
        final byte[] m2 = syntheticMeta();
        final BiomeGenBase[] biomes2 = new BiomeGenBase[256];
        java.util.Arrays.fill(biomes2, BiomeGenBase.plains);
        ChunkProviderProsperityRuins.applyBiomeSurface(WORLD_SEED, 0, 0, b2, m2, biomes2);
        check(b2[column0() | columnHeight(0, 0)] == BiomeGenBase.plains.topBlock,
            "dim78 plains surface = plains.topBlock");
        // plains 主体回退 stone：找任一深层格
        int deepY = BEDROCK_TOP + 1;
        check(b2[column0() | deepY] == Blocks.stone, "dim78 plains body stays stone (L1 unresolved fallback)");
    }

    private static int column0() {
        return 0;
    }

    // ————— dim79 断言：表层 top/filler、主体 corestone 保留 —————

    private static void assertShatteredColumnDriven(BiomeGenBase biome, Block expectedTop, Block expectedFiller,
        int index) {
        final Block[] blocks = syntheticChunk(BlocksGTSR.shatteredCorestone, biome);
        final byte[] meta = syntheticMeta();
        final BiomeGenBase[] biomes = new BiomeGenBase[256];
        java.util.Arrays.fill(biomes, biome);
        ChunkProviderShatteredGrounds.applyBiomeSurface(WORLD_SEED, 0, 0, blocks, meta, biomes);
        int surfaceOk = 0;
        int corestoneLeft = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int column = x << 12 | z << 8;
                final int height = columnHeight(x, z);
                final Block surface = blocks[column | height];
                if (surface != expectedTop) {
                    fail("dim79#" + index + " surface not group top at (" + x + "," + z + "): " + surface);
                    return;
                }
                if (surface == BlocksGTSR.shatteredCorestone) {
                    fail("dim79#" + index + " surface still corestone at (" + x + "," + z + ")");
                    return;
                }
                int y = height - 1;
                int filler = 0;
                while (y > BEDROCK_TOP && filler < 2 && blocks[column | y] == expectedFiller) {
                    filler++;
                    y--;
                }
                if (filler < 1) {
                    fail("dim79#" + index + " filler segment missing at (" + x + "," + z + ")");
                    return;
                }
                for (; y > BEDROCK_TOP; y--) {
                    if (blocks[column | y] != BlocksGTSR.shatteredCorestone) {
                        fail("dim79#" + index + " body must stay corestone at (" + x + "," + z + ") y=" + y);
                        return;
                    }
                    corestoneLeft++;
                }
                surfaceOk++;
            }
        }
        check(corestoneLeft > 0, "dim79#" + index + " body corestone kept after surface replacement");
        pass("dim79#" + index + " " + biome.biomeName + ": surface non-corestone cols=" + surfaceOk);
    }

    // ————— 落点门冒烟（装饰/城外结构在替换前后各自过不过门） —————

    private static void assertGateSmoke(Block[] pTop, Block[] sTop) {
        check(!ProsperityDecorPlacer.isNaturalTop(Blocks.stone), "pre-fix dim78 surface(stone) must NOT pass decor gate");
        check(!ProsperityOutpostPlacer.isNaturalProsperityTop(Blocks.stone),
            "pre-fix dim78 surface(stone) must NOT pass outpost gate");
        check(!ShatteredDecorPlacer.isNaturalTop(BlocksGTSR.shatteredCorestone),
            "pre-fix dim79 surface(corestone) must NOT pass decor gate");
        for (int i = 0; i < 4; i++) {
            check(ProsperityDecorPlacer.isNaturalTop(pTop[i]), "post-fix dim78 top#" + i + " passes decor gate");
            check(ProsperityOutpostPlacer.isNaturalProsperityTop(pTop[i]), "post-fix dim78 top#" + i + " passes outpost gate");
            check(ShatteredDecorPlacer.isNaturalTop(sTop[i]), "post-fix dim79 top#" + i + " passes decor gate");
        }
    }

    // ————— biome 数组来源与下标口径（框架 provideChunk 消费链上游） —————

    private static void assertChunkManagerChain(BiomeGenBase[] prosperity, BiomeGenBase[] shattered) {
        final GTSRDimensionDef def = new GTSRDimensionDef(
            "prosperity-ruins", "Prosperity Ruins", 0x50524F53L, 78, 100, () -> true,
            WorldProviderProsperityRuins.class, ChunkProviderProsperityRuins::new);
        def.setBiomeSelector((seed, chunkX, chunkZ, biomeCount, weights) -> BiomeZoneSelector.select(
            seed, chunkX, chunkZ, biomeCount, weights, BiomeZoneSelector.ZONE_CELL_CHUNKS, 0x5A4F4E45L));
        def.addBiome(prosperity[0], BiomeRustedSteppe.WEIGHT);
        def.addBiome(prosperity[1], BiomeGearworkForest.WEIGHT);
        def.addBiome(prosperity[2], BiomeBrassWastes.WEIGHT);
        def.addBiome(prosperity[3], BiomeFumaroleSwamp.WEIGHT);
        final GTSRWorldChunkManager mgr = new GTSRWorldChunkManager(WORLD_SEED, def);
        // ① 下标口径逐列断言（P2b：替换旧"chunk 内 256 格同群系"假绿——那种断言对下标/转置
        //    错误结构性免疫，plan §6 诚实性公理禁用）。生产者口径 dx + dz*width 下 64×64 块
        //    坐标窗逐格 == biomeAt(块坐标>>4)；灵敏度自检：若生产侧写反成 dz + dx*width，
        //    会有多少格错位——阈值按实测留余量（本轮实测 4/8 窗敏感、6418/32768 格错位），
        //    归零即说明本断言对转置免疫（假绿），直接判失败。窗取 64×64 而非 32×32：
        //    小窗可能整窗落入单一群系而失去抓力（32 实测仅 3/8 窗敏感）。
        final BiomeGenBase[] first = mgr.loadBlockGeneratorData(null, 0, 0, 16, 16);
        check(first != null && first.length == 256, "loadBlockGeneratorData 256-entry array");
        final int w = 64;
        final int windowCount = 8;
        int cells = 0;
        int badCells = 0;
        int driftTotal = 0;
        int sensitiveWindows = 0;
        for (int k = 0; k < windowCount; k++) {
            final int x0 = -23 + k * 53;
            final int z0 = 11 - k * 37;
            final BiomeGenBase[] area = mgr.loadBlockGeneratorData(null, x0, z0, w, w);
            check(area != null && area.length == w * w, "64x64 area window #" + k + " length");
            int winDrift = 0;
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < w; dz++) {
                    final BiomeGenBase want = mgr.biomeAt((x0 + dx) >> 4, (z0 + dz) >> 4);
                    cells++;
                    if (area[dx + dz * w] != want) {
                        badCells++;
                    }
                    if (area[dz + dx * w] != want) {
                        winDrift++;
                    }
                }
            }
            driftTotal += winDrift;
            if (winDrift > 0) {
                sensitiveWindows++;
            }
        }
        check(badCells == 0, "producer index contract per-cell: area[dx+dz*64]==biomeAt(cell>>4) over " + cells
            + " cells in " + windowCount + " windows, bad=" + badCells);
        check(sensitiveWindows >= 2 && driftTotal >= 1024,
            "transposition sensitivity: >=2/8 windows broken by dz+dx*64 and >=1024 cells drift (else this"
                + " assertion is structurally immune = forbidden false green), sensitive=" + sensitiveWindows + "/"
                + windowCount + " driftCells=" + driftTotal + "/" + cells);
        // ② 四群系可达（"看不到其他群系"=表层问题的反证：selector 真出四群系）
        Set<Integer> seen = new HashSet<>();
        for (int cx = -80; cx < 80; cx++) {
            for (int cz = -80; cz < 80; cz++) {
                final BiomeGenBase b = mgr.biomeAt(cx, cz);
                for (int i = 0; i < 4; i++) {
                    if (b == prosperity[i]) {
                        seen.add(i);
                    }
                }
            }
        }
        check(seen.size() == 4, "BiomeZoneSelector reaches all 4 prosperity biomes over 25600 chunks, seen=" + seen);
        // ③ dim79 同款 def 链
        final GTSRDimensionDef def79 = new GTSRDimensionDef(
            "shattered-lands", "Shattered Lands", 0x53484C53L, 79, 101, () -> true,
            WorldProviderShatteredLands.class, ChunkProviderShatteredGrounds::new);
        def79.setBiomeSelector((seed, chunkX, chunkZ, biomeCount, weights) -> BiomeZoneSelector.select(
            seed, chunkX, chunkZ, biomeCount, weights, BiomeZoneSelector.ZONE_CELL_CHUNKS, 0x5A4F4E46L));
        def79.addBiome(shattered[0], BiomeAshenPrairie.WEIGHT);
        def79.addBiome(shattered[1], BiomeSlagwoodGrove.WEIGHT);
        def79.addBiome(shattered[2], BiomeVitreousWaste.WEIGHT);
        def79.addBiome(shattered[3], BiomeTarBasin.WEIGHT);
        final GTSRWorldChunkManager mgr79 = new GTSRWorldChunkManager(WORLD_SEED, def79);
        Set<Integer> seen79 = new HashSet<>();
        for (int cx = -80; cx < 80; cx++) {
            for (int cz = -80; cz < 80; cz++) {
                final BiomeGenBase b = mgr79.biomeAt(cx, cz);
                for (int i = 0; i < 4; i++) {
                    if (b == shattered[i]) {
                        seen79.add(i);
                    }
                }
            }
        }
        check(seen79.size() == 4, "dim79 selector reaches all 4 shattered biomes over 25600 chunks, seen=" + seen79);
    }

    // ————— 通用工具 —————

    private static void checkDistinctFamily(Block[] family, String name, String vsWhat) {
        Set<Block> set = new HashSet<>();
        boolean clean = true;
        for (Block b : family) {
            if (b == null) {
                clean = false;
            }
            set.add(b);
            if (b == Blocks.stone || b == BlocksGTSR.shatteredCorestone) {
                clean = false;
            }
        }
        check(clean && set.size() == family.length, name + " family: 4 distinct non-" + vsWhat + " blocks");
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
        System.out.println("RUNTIME PASS: " + msg);
    }

    private static void fail(String msg) {
        failures++;
        System.out.println("RUNTIME FAIL: " + msg);
    }
}
