import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStone;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import com.miaokatze.gtsr.common.blocks.BlockProsperityFalling;
import com.miaokatze.gtsr.common.blocks.BlockProsperityStone;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeBrassWastes;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeFumaroleSwamp;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeGearworkForest;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeRustedSteppe;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeSanzuRiver;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityCanopyLeaves;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityNaturalBase;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityNaturalTop;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRoostGlow;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRustLog;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityTuft;
import com.miaokatze.gtsr.common.dimension.shattered.ChunkProviderShatteredGrounds;
import com.miaokatze.gtsr.common.dimension.shattered.WorldProviderShatteredLands;
import com.miaokatze.gtsr.common.dimension.shattered.biome.BiomeAshenPrairie;
import com.miaokatze.gtsr.common.dimension.shattered.biome.BiomeSlagwoodGrove;
import com.miaokatze.gtsr.common.dimension.shattered.biome.BiomeTarBasin;
import com.miaokatze.gtsr.common.dimension.shattered.biome.BiomeVitreousWaste;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredCorestone;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredSurfaceBase;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredSurfaceTop;

/**
 * P2 表层离线断言的共用装配（{@code SurfaceDegradationCheck} / {@code SurfaceTranspositionCheck} /
 * {@code SurfaceByteParityDump} 共用；配方承袭 {@code ReplaceSurfaceRuntimeCheck}，不重复注释）。
 * <p>
 * 关键口径（写在这里以免三个工具各踩一次坑）：
 * <ul>
 * <li>{@code Blocks.*} 是 {@code static final} 且 {@code Block.registerBlocks()} 走 FML Loader，
 * 离线 JVM 不可满足 ⇒ 用 {@code Unsafe.putObject} 直写 identity 实例；<b>必须先
 * {@code Class.forName("net.minecraft.init.Blocks")}</b>，否则随后的 {@code <clinit>} 会抹掉写入；</li>
 * <li>{@code new Block[65536]} 未写入槽位是 <b>null 而非 Blocks.air</b>（框架
 * {@code isAirOrEmpty} 的存在理由）；</li>
 * <li>{@code BlocksGTSR} 的 12+ 个 {@code public static Block} 字段由 {@link #blockFamily()} 直填
 * （非 final，可直赋），构造参数镜像 {@code BlockLoader}；</li>
 * <li>群系身份走 L1 真入口：{@link GTSRBiomeAuthority#recordAllocation} +
 * {@link GTSRWorldChunkManager} 构造内的 {@code bind}——<b>不</b>伪造账本。</li>
 * </ul>
 */
final class SurfaceHarness {

    /** 与 ReplaceSurfaceRuntimeCheck 同一世界种子。 */
    static final long SEED = 0x53484C53L;
    static final int[] PROSPERITY_IDS = { 180, 181, 182, 183 };
    /**
     * 遗忘之川首选槽（v1.20.39 T5/T8，plan §3.3）：{@code prosperityBiomeIdStart(180) + 4}。
     * <b>sanzu 配槽进 L1 账本但不挂 def 群系表</b>（def 表保持 4 项 selector 名册）⇒ 账本
     * rosterSize=5，离线装配若只记 4 元则 degraded=SHORT——T8 红清单 ⑥⑦⑧⑬⑭ 的
     * 「dim78 应处 NONE」装配假红单一根因，故两个"全配"记账口都补第 5 元。
     */
    static final int SANZU_ID = 184;
    static final int[] SHATTERED_IDS = { 190, 191, 192, 193 };
    static final BiomeId[] PROSPERITY_KEYS = { BiomeId.RUSTED_STEPPE, BiomeId.GEARWORK_FOREST,
        BiomeId.BRASS_WASTES, BiomeId.FUMAROLE_SWAMP };
    static final BiomeId[] SHATTERED_KEYS = { BiomeId.ASHEN_PRAIRIE, BiomeId.SLAGWOOD_GROVE,
        BiomeId.VITREOUS_WASTE, BiomeId.TAR_BASIN };

    private SurfaceHarness() {}

    static void initVanillaBlocks() {
        try {
            Class.forName("net.minecraft.init.Blocks");
            final sun.misc.Unsafe u = unsafe();
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
        final Field f = Blocks.class.getField(field);
        u.putObject(u.staticFieldBase(f), u.staticFieldOffset(f), value);
    }

    static void blockFamily() {
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
            "ShatteredSlagTop", "gtsr:shattered_slag_top", "gtsr:shattered_slag_top_side",
            "gtsr:shattered_slag_base");
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
        // P17-SB1 名册同步（三处静默面之一，P17-D §3★）：木 3 档 + 花 4 + 草 2 的离线实例。
        // 构造参数逐字照抄 loader/BlockLoader P17-SB1 段；不接任何生成逻辑，只保证 blockFamily 的
        // 12 个消费工具能真实测到这些实例（缺席＝静默不覆盖，比红更危险）。
        BlocksGTSR.prosperityCopperLog = new BlockProsperityRustLog("ProsperityCopperLog",
            "gtsr:prosperity_copper_log_side", "gtsr:prosperity_copper_log_top");
        BlocksGTSR.prosperityCopperLeaves = new BlockProsperityCanopyLeaves("ProsperityCopperLeaves",
            "gtsr:prosperity_copper_leaves_side", "gtsr:prosperity_copper_leaves_top");
        BlocksGTSR.prosperityBrassLog = new BlockProsperityRustLog("ProsperityBrassLog",
            "gtsr:prosperity_brass_log_side", "gtsr:prosperity_brass_log_top");
        BlocksGTSR.prosperityBrassLeaves = new BlockProsperityCanopyLeaves("ProsperityBrassLeaves",
            "gtsr:prosperity_brass_leaves_side", "gtsr:prosperity_brass_leaves_top");
        BlocksGTSR.prosperityMarshLog = new BlockProsperityRustLog("ProsperityMarshLog",
            "gtsr:prosperity_marsh_log_side", "gtsr:prosperity_marsh_log_top");
        BlocksGTSR.prosperityMarshLeaves = new BlockProsperityCanopyLeaves("ProsperityMarshLeaves",
            "gtsr:prosperity_marsh_leaves_side", "gtsr:prosperity_marsh_leaves_top");
        BlocksGTSR.prosperityFlowerRust = new BlockProsperityTuft("ProsperityFlowerRust",
            "gtsr:prosperity_flower_rust");
        BlocksGTSR.prosperityFlowerPatina = new BlockProsperityTuft("ProsperityFlowerPatina",
            "gtsr:prosperity_flower_patina");
        BlocksGTSR.prosperityFlowerBrass = new BlockProsperityTuft("ProsperityFlowerBrass",
            "gtsr:prosperity_flower_brass");
        BlocksGTSR.prosperityFlowerMarsh = new BlockProsperityTuft("ProsperityFlowerMarsh",
            "gtsr:prosperity_flower_marsh");
        BlocksGTSR.prosperityTuftSedge = new BlockProsperityTuft("ProsperityTuftSedge",
            "gtsr:prosperity_tuft_sedge");
        BlocksGTSR.prosperityTuftBristle = new BlockProsperityTuft("ProsperityTuftBristle",
            "gtsr:prosperity_tuft_bristle");
        // P17-S-B2 名册同步（三处静默面之一）：沙/砂砾 3 件的离线实例，构造参数逐字照抄 BlockLoader。
        // 全部非表层 top（不进 SurfaceGate 名册），消费方 = ProsperityDecorPlacer 的荒漠沙砾趟 + S-C 河床。
        // v1.20.39 T2（plan §3.9）：coarseSand/riverGravel 切 BlockProsperityFalling（镜像 BlockLoader，
        // 注册名不变）；silicaSand 保持非重力。
        BlocksGTSR.prosperitySilicaSand = new BlockProsperityNaturalBase("ProsperitySilicaSand",
            "gtsr:prosperity_silica_sand");
        BlocksGTSR.prosperityCoarseSand = new BlockProsperityFalling("ProsperityCoarseSand",
            "gtsr:prosperity_coarse_sand");
        BlocksGTSR.prosperityRiverGravel = new BlockProsperityFalling("ProsperityRiverGravel",
            "gtsr:prosperity_river_gravel");
        // v1.20.39 T2（plan §3.7 地底石化）：地底 wholeBody 主体石离线实例（缺席＝baseBlockOf 回 null、
        // 行为侧读数失真——同上"静默面同步"纪律），构造参数逐字照抄 BlockLoader。
        BlocksGTSR.prosperityStone = new BlockProsperityStone("ProsperityStone", "gtsr:prosperity_stone");
        // v1.20.43 P22-B S2 名册同步（三处静默面之一）：岛心巨树三件套离线实例，构造参数逐字照抄
        // BlockLoader P22 段（垂天玄柯/漱玉清羽/旧栖晴晕；全部非表层 top，不进 SurfaceGate 名册，
        // 消费方 = S3 巨树重放趟 / S4 光源趟，本 harness 只保 blockFamily 真实装配可供名册判据）。
        BlocksGTSR.prosperityZenithLog = new BlockProsperityRustLog("ProsperityZenithLog",
            "gtsr:prosperity_zenith_log_side", "gtsr:prosperity_zenith_log_top");
        BlocksGTSR.prosperityJadeLeaves = new BlockProsperityCanopyLeaves("ProsperityJadeLeaves",
            "gtsr:prosperity_jade_leaves_side", "gtsr:prosperity_jade_leaves_top");
        BlocksGTSR.prosperityRoostGlow = new BlockProsperityRoostGlow("ProsperityRoostGlow",
            "gtsr:prosperity_roost_glow");
    }

    static BiomeGenBase[] prosperityBiomes() {
        return new BiomeGenBase[] { new BiomeRustedSteppe(PROSPERITY_IDS[0]),
            new BiomeGearworkForest(PROSPERITY_IDS[1]), new BiomeBrassWastes(PROSPERITY_IDS[2]),
            new BiomeFumaroleSwamp(PROSPERITY_IDS[3]) };
    }

    static BiomeGenBase[] shatteredBiomes() {
        return new BiomeGenBase[] { new BiomeAshenPrairie(SHATTERED_IDS[0]),
            new BiomeSlagwoodGrove(SHATTERED_IDS[1]), new BiomeVitreousWaste(SHATTERED_IDS[2]),
            new BiomeTarBasin(SHATTERED_IDS[3]) };
    }

    static int[] prosperityWeights() {
        return new int[] { BiomeRustedSteppe.WEIGHT, BiomeGearworkForest.WEIGHT, BiomeBrassWastes.WEIGHT,
            BiomeFumaroleSwamp.WEIGHT };
    }

    static int[] shatteredWeights() {
        return new int[] { BiomeAshenPrairie.WEIGHT, BiomeSlagwoodGrove.WEIGHT, BiomeVitreousWaste.WEIGHT,
            BiomeTarBasin.WEIGHT };
    }

    /** 名册成员入账（正常态：degraded=NONE）。 */
    static void recordAllAllocations(BiomeGenBase[] p, BiomeGenBase[] s) {
        for (int i = 0; i < 4; i++) {
            GTSRBiomeAuthority.recordAllocation(PROSPERITY_KEYS[i], PROSPERITY_IDS[i], PROSPERITY_IDS[i], p[i]);
            GTSRBiomeAuthority.recordAllocation(SHATTERED_KEYS[i], SHATTERED_IDS[i], SHATTERED_IDS[i], s[i]);
        }
        recordSanzuAllocation();
    }

    /** 只让 dim78 的前 {@code allocated} 个成员入账，其余记 no-slot ⇒ 该维 degraded=SHORT。 */
    static void recordProsperityShort(int allocated, BiomeGenBase[] p) {
        for (int i = 0; i < 4; i++) {
            if (i < allocated) {
                GTSRBiomeAuthority.recordAllocation(PROSPERITY_KEYS[i], PROSPERITY_IDS[i], PROSPERITY_IDS[i], p[i]);
            } else {
                GTSRBiomeAuthority.recordNoSlot(PROSPERITY_KEYS[i], PROSPERITY_IDS[i], "offline:no-slot");
            }
        }
    }

    /**
     * 只把 dim78 的 4+1 个成员全部入账（把该维从 SHORT 拉回 NONE，用于"门零介入"对照场景）。
     * T8 重钉注：sanzu 为 T5 名册第 5 元（plan §3.3），不补记则账本 4/5 恒 SHORT，
     * NONE 对照档自为假红（SurfaceDegradationCheck NONE-control 红的归因）。
     */
    static void recordProsperityAll(BiomeGenBase[] p) {
        for (int i = 0; i < 4; i++) {
            GTSRBiomeAuthority.recordAllocation(PROSPERITY_KEYS[i], PROSPERITY_IDS[i], PROSPERITY_IDS[i], p[i]);
        }
        recordSanzuAllocation();
    }

    /**
     * 遗忘之川入账（roster-only，plan §3.3）：与生产 {@code ProsperityBiomes.attachSanzuRiver}
     * 同口径——{@code recordAllocation(SANZU_RIVER, 184, 184, 实例)}，<b>不</b>进任何 def 群系表。
     */
    private static void recordSanzuAllocation() {
        GTSRBiomeAuthority.recordAllocation(BiomeId.SANZU_RIVER, SANZU_ID, SANZU_ID, new BiomeSanzuRiver(SANZU_ID));
    }

    /** 把 dim78 全部成员记为无槽（且没有任何 allocation 记录）⇒ degraded=EMPTY。 */
    static void recordProsperityEmpty() {
        for (int i = 0; i < 4; i++) {
            GTSRBiomeAuthority.recordNoSlot(PROSPERITY_KEYS[i], PROSPERITY_IDS[i], "offline:no-slot");
        }
    }

    /**
     * 生产同款 def 接线（seedSalt/权重表与 CommonProxy 一致）。
     * <p>
     * B2 起 def 不再挂 selector——群系身份由 {@code GTSRWorldChunkManager} 构造期按
     * {@code seed ^ seedSalt} + 群系表 id 等权表自建 GenLayer 链；权重只作 def 元数据留存。
     */
    static GTSRDimensionDef def(boolean is78, BiomeGenBase[] biomes, int[] weights) {
        final GTSRDimensionDef def = is78
            ? new GTSRDimensionDef(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, "Parity Prosperity", 0x50524F53L, 78, 100,
                () -> true, WorldProviderProsperityRuins.class, ChunkProviderProsperityRuins::new)
            : new GTSRDimensionDef(GTSRBiomeAuthority.DIM_KEY_SHATTERED, "Parity Shattered", 0x53484C53L, 79, 101,
                () -> true, WorldProviderShatteredLands.class, ChunkProviderShatteredGrounds::new);
        for (int i = 0; i < biomes.length; i++) {
            def.addBiome(biomes[i], weights[i]);
        }
        return def;
    }

    /**
     * 空群系表 def（模拟 L0 全部无槽：def 表为空 ⇒ manager {@code weightedBiomes==null} ⇒
     * {@code loadBlockGeneratorData} 整数组 null，与实机 EMPTY 态同一产出路径）。
     */
    static GTSRDimensionDef emptyDef(boolean is78) {
        return def(is78, new BiomeGenBase[0], new int[0]);
    }

    /**
     * 只提供 {@code getSeed()} 的 mock World。
     * <p>
     * 三处离线障碍与对策（本轮实测得出）：
     * <ol>
     * <li>{@code World} 是 abstract（{@code createChunkProvider}/{@code func_152379_p}/
     * {@code getEntityByID}）⇒ {@code allocateInstance(World.class)} 抛
     * {@code InstantiationException}，故用补齐三个抽象方法的 {@link HarnessWorld}；</li>
     * <li>{@code Unsafe.allocateInstance} <b>不执行构造器体</b> ⇒ {@code super(...)} 的 null
     * 实参不会被解引用，存档 / Profiler / WorldProvider 依赖整体绕开；</li>
     * <li>本 MC 的 {@code World.getSeed()} 实现是 {@code this.provider.getSeed()}（不是
     * {@code worldInfo.getSeed()}），而 {@code World.provider} 是 {@code public final} 字段
     * ⇒ 必须再塞一个覆写 {@code getSeed()} 的 {@link HarnessProvider}（{@code final} 实例字段
     * 由 {@code Unsafe.putObject} 直写）。{@code worldInfo.randomSeed} 一并写入，供任何走
     * {@code WorldInfo} 的读取路径取到同一种子。</li>
     * </ol>
     */
    static World mockWorld() {
        try {
            final sun.misc.Unsafe u = unsafe();
            final WorldInfo info = (WorldInfo) u.allocateInstance(WorldInfo.class);
            final Field seedField = WorldInfo.class.getDeclaredField("randomSeed");
            seedField.setAccessible(true);
            u.putLong(info, u.objectFieldOffset(seedField), SEED);
            final World world = (World) u.allocateInstance(HarnessWorld.class);
            final Field infoField = World.class.getDeclaredField("worldInfo");
            infoField.setAccessible(true);
            u.putObject(world, u.objectFieldOffset(infoField), info);
            final Field providerField = World.class.getDeclaredField("provider");
            providerField.setAccessible(true);
            u.putObject(world, u.objectFieldOffset(providerField), u.allocateInstance(HarnessProvider.class));
            return world;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("mock World 装配失败", e);
        }
    }

    static final class HarnessProvider extends WorldProvider {

        @Override
        public String getDimensionName() {
            return "gtsr-parity-harness";
        }

        @Override
        public long getSeed() {
            return SEED;
        }
    }

    static final class HarnessWorld extends World {

        private HarnessWorld() {
            // 两个 World 构造器仅 WorldProvider/WorldSettings 顺序不同 ⇒ 实参必须逐个带类型转换，
            // 否则 javac 报 ambiguous。本构造器体在 allocateInstance 路径下永不执行。
            super((ISaveHandler) null, (String) null, (WorldProvider) null, (WorldSettings) null, (Profiler) null);
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int entityId) {
            return null;
        }
    }

    static sun.misc.Unsafe unsafe() throws ReflectiveOperationException {
        final Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        return (sun.misc.Unsafe) uf.get(null);
    }

    /** provider 的 {@code generateTerrain} 覆写实现（反射调用真实生产代码，零复制）。 */
    static Method generateTerrain() throws NoSuchMethodException {
        final Method m = GTSRChunkProviderBase.class.getDeclaredMethod("generateTerrain", int.class, int.class,
            Block[].class, byte[].class, BiomeGenBase[].class);
        m.setAccessible(true);
        return m;
    }

    /** provider 的表层静态缝（provideChunk 事件段之后的同一实现体）。 */
    static Method surfaceSeam(Class<?> providerClass) throws NoSuchMethodException {
        return providerClass.getMethod("applyBiomeSurface", long.class, int.class, int.class, Block[].class,
            byte[].class, BiomeGenBase[].class);
    }

    static GTSRChunkProviderBase provider(boolean is78) {
        return is78 ? new ChunkProviderProsperityRuins(mockWorld(), SEED)
            : new ChunkProviderShatteredGrounds(mockWorld(), SEED);
    }

    static GTSRWorldChunkManager manager(boolean is78, GTSRDimensionDef def) {
        return new GTSRWorldChunkManager(SEED, def);
    }

    /** 合成 chunk 地形：真实 generateTerrain + 真实表层缝（正常态对拍入口）。 */
    static void runRealSurface(GTSRChunkProviderBase provider, int cx, int cz, Block[] blocks, byte[] meta,
        BiomeGenBase[] biomes) throws ReflectiveOperationException {
        generateTerrain().invoke(provider, cx, cz, blocks, meta, null);
        surfaceSeam(provider.getClass()).invoke(null, SEED, cx * 16, cz * 16, blocks, meta, biomes);
    }
}
