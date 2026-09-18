package com.miaokatze.gtsr.common.dimension.shattered;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkClampedSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * 破碎遗迹散布生成器（dim1 S6b 建链；dim79 重做 S-B4 同步新方块集，plan §5 S-B4）：
 * 仅破碎维度生效（shatteredDimId 守卫 + planDimension.shatteredDimension 双保险），频率 = 1/48 chunk
 * （常量封板，Config 不加键）。落点 = 全地形表面：自上而下第一处"非空气且上方为空气"的地表面
 * （全地形列扫，dim78 scatter findSurfaceY 范式；浮岛"岛面 + shattered 族门"随旧模型作废），
 * y ∈ [40, 96]（按新地形钳制域 36..96 重标定：下沿留 4 格谷地余量、上沿即钳制顶）。
 * <p>
 * 结构 = 3×3 独碑岩缺角平台（逐格 20% 缺失，嵌入地表面替换表层）+ 1-2 个残缺机器骨架
 * （{@link #HUSK_SMALL}/{@link #HUSK_TALL} 两变体，'C' 核心位 = BlockRuinedCasing 积碳壳 meta1——
 * <b>无 TileEntity、无箱子、无战利品</b>（失稳值/碎片在裁剪范围外））。放置经 {@link StructureBuilder}
 * → 注入 {@link BlockSink}（世界生成期 ChunkClampedSink / S5 指令期 DirectWorldSink 同一放置路径）；
 * 两变体登记 {@link StructureRegistry}（dimension=SHATTERED，S5 /gtsr structure 补全同源）。
 * <p>
 * 跨界协议：平台中心钳制在 chunk 内 [1,14]，husk footprint 2×2 的四个合法低角锚格均落在平台 3×3 内
 * ——全部写入完整落在当前 populate chunk，ChunkClampedSink 零丢弃。随机从 chunk 确定性哈希派生
 * （02 §0.3 口径，同 RuinedMachinePlacer），禁用 populate 裸 Random。
 */
public class WorldGenShatteredRuins implements IWorldGenerator {

    /** 盐 "shru"（与残缺机器 SALT_MACHINE 同风格隔离随机流）。 */
    private static final long SALT_RUINS = 0x73687275L;

    /** 遗迹掷骰分母：平均 1/48 chunk 一处（04 §7；常量封板，Config 不加键）。 */
    private static final int RUIN_CHANCE_DIVISOR = 48;

    /** 最低表面高度（S-B4 按 ShatteredTerrainProfile 钳制域 36..96 重标定：谷地下沿余量 4 格）。 */
    private static final int MIN_SURFACE_Y = 40;

    /** 最高表面高度（钳制域上沿；防异常地形）。 */
    private static final int MAX_SURFACE_Y = 96;

    /** husk_small：2×3×2 矮残骸（残墙断口 + 双残柱 + 基板核心位）。 */
    public static final HuskShape HUSK_SMALL = new HuskShape(
        "husk_small",
        2,
        3,
        2,
        new String[][] {
            // y=2 顶层：残墙断口
            { "# ", "  " },
            // y=1：双残柱
            { "# ", "# " },
            // y=0：基板 + 核心位（'C' 落积碳壳 meta1）
            { "C#", "##" } });

    /** husk_tall：2×5×2 烟囱残梗（4 高断口烟囱 + 基板核心位）。 */
    public static final HuskShape HUSK_TALL = new HuskShape(
        "husk_tall",
        2,
        5,
        2,
        new String[][] {
            // y=4 顶层：烟囱断口
            { "# ", "  " },
            // y=3..2：烟囱残身
            { "# ", "  " }, { "# ", "  " },
            // y=1：双残柱
            { "# ", "# " },
            // y=0：基板 + 核心位
            { "C#", "##" } });

    private static final HuskShape[] ALL = { HUSK_SMALL, HUSK_TALL };

    /** 注册幂等防御（重复构造不再登记）。 */
    private static volatile boolean registered;

    /** 注册证据日志只发一次（plan S6b 验收 grep 锚点，同 S4a 口径）。 */
    private static volatile boolean evidenceLogged;

    /**
     * 残缺机器骨架变体形状（记号同 RuinedMachineShapes：'#'=积碳外层 meta0、'C'=核心位积碳壳 meta1、
     * 空格=不触碰（骨架不做腔体清空，地形让行）；layers[0] = 顶层，逐层向下）。
     */
    public static final class HuskShape {

        public final String name;
        public final int sizeX;
        public final int sizeY;
        public final int sizeZ;
        /** layers[0] = 顶层；layers[layer] 为该层 sizeZ 行、每行 sizeX 字符。 */
        public final String[][] layers;

        HuskShape(String name, int sizeX, int sizeY, int sizeZ, String[][] layers) {
            this.name = name;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.layers = layers;
            if (layers.length != sizeY) {
                throw new IllegalStateException(
                    "[GTSR] shattered husk shape " + name + ": layer count " + layers.length + " != sizeY " + sizeY);
            }
            for (int y = 0; y < layers.length; y++) {
                if (layers[y].length != sizeZ) {
                    throw new IllegalStateException(
                        "[GTSR] shattered husk shape " + name + ": layer " + y + " row count != sizeZ " + sizeZ);
                }
                for (int z = 0; z < layers[y].length; z++) {
                    if (layers[y][z].length() != sizeX) {
                        throw new IllegalStateException(
                            "[GTSR] shattered husk shape " + name
                                + ": layer "
                                + y
                                + " row "
                                + z
                                + " length != sizeX "
                                + sizeX);
                    }
                }
            }
        }

        /** 取（世界向上 y ∈ 0..sizeY-1，dx ∈ 0..sizeX-1，dz ∈ 0..sizeZ-1）处字符（S4a 同款）。 */
        public char charAt(int y, int dx, int dz) {
            return this.layers[this.sizeY - 1 - y][dz].charAt(dx);
        }
    }

    /**
     * 向 {@link StructureRegistry} 登记 2 骨架变体（dimension=SHATTERED，幂等）。
     * 由本类构造时调用（CommonProxy.init 注册线），S5 /gtsr structure 补全同源消费。
     */
    public static void registerVariants() {
        if (registered) {
            return;
        }
        registered = true;
        for (final HuskShape husk : ALL) {
            StructureRegistry.register(
                new StructureRegistry.Entry(
                    husk.name,
                    StructureRegistry.Dimension.SHATTERED,
                    husk.sizeX,
                    husk.sizeZ,
                    (sink, x, y, z,
                        seed) -> placeHusk(new StructureBuilder(sink), husk, x, y, z, BlockSink.FLAG_DIRECT)));
        }
    }

    public WorldGenShatteredRuins() {
        registerVariants();
        if (evidenceLogged) {
            return;
        }
        evidenceLogged = true;
        GTSteamReborn.LOG.info(
            "[GTSR] shattered ruins generator registered: dimId={} chance=1/{} variants=[husk_small, husk_tall] platform=3x3_shattered_monolith",
            Config.shatteredDimId,
            RUIN_CHANCE_DIVISOR);
    }

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator,
        IChunkProvider chunkProvider) {
        // 维度过滤：仅破碎维度（shatteredDimId<0 视为禁用双保险；维度禁用时该 id 不存在）
        if (world.provider.dimensionId != Config.shatteredDimId || Config.shatteredDimId < 0) {
            return;
        }
        if (!Config.planDimension.shatteredDimension) {
            return;
        }
        // 方块族未就绪防御（BlockLoader 失败等异常场景整体跳过，不炸生成链）
        if (BlocksGTSR.shatteredCorestone == null || BlocksGTSR.ruinedCasing == null) {
            return;
        }
        // 随机流：chunk 确定性哈希派生（02 §0.3，同 RuinedMachinePlacer 口径）
        final Random r = new Random(GTSRWorldgenHash.chunkSeed(world.getSeed(), chunkX, chunkZ) ^ SALT_RUINS);
        if (r.nextInt(RUIN_CHANCE_DIVISOR) != 0) {
            return;
        }
        // 平台中心钳制在 [1,14]：3×3 平台完整落在当前 chunk（ChunkClampedSink 零丢弃）
        final int x = (chunkX << 4) + 1 + r.nextInt(14);
        final int z = (chunkZ << 4) + 1 + r.nextInt(14);
        final int surfaceY = findSurfaceY(world, x, z);
        if (surfaceY < MIN_SURFACE_Y || surfaceY > MAX_SURFACE_Y) {
            return;
        }
        final BlockSink sink = new ChunkClampedSink(world, chunkX, chunkZ);
        final StructureBuilder builder = new StructureBuilder(sink);
        // 1. 3×3 独碑岩缺角平台：嵌入地表面（逐格 20% 缺失，04 §7 口径承袭；缺失格保留原地形仍受支撑）
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (r.nextInt(5) == 0) {
                    continue; // 缺角
                }
                builder.setBlock(x + dx, surfaceY, z + dz, BlocksGTSR.shatteredMonolith, 0, BlockSink.FLAG_POPULATE);
            }
        }
        // 2. 残缺机器骨架 1-2 座：footprint 2×2 低角锚格 ∈ {(0,0),(-1,0),(0,-1),(-1,-1)}，均落在平台 3×3 内
        final int husks = 1 + r.nextInt(2);
        final int[][] anchors = { { 0, 0 }, { -1, 0 }, { 0, -1 }, { -1, -1 } };
        for (int i = 0; i < husks; i++) {
            final int j = i + r.nextInt(anchors.length - i); // 部分洗牌（确定性）
            final int[] tmp = anchors[i];
            anchors[i] = anchors[j];
            anchors[j] = tmp;
            final HuskShape husk = ALL[r.nextInt(ALL.length)];
            placeHusk(builder, husk, x + anchors[i][0], surfaceY + 1, z + anchors[i][1], BlockSink.FLAG_POPULATE);
        }
    }

    /**
     * 骨架放置核心（世界生成与 S5 /gtsr structure 共用路径）：'C' 落积碳壳 meta1（无 TE）、
     * '#' 落 meta0，空格不触碰（地形让行）。形状本身即残缺剪影，不做额外损伤掷骰。
     */
    static void placeHusk(StructureBuilder builder, HuskShape husk, int originX, int originY, int originZ, int flags) {
        for (int y = 0; y < husk.sizeY; y++) {
            for (int dx = 0; dx < husk.sizeX; dx++) {
                for (int dz = 0; dz < husk.sizeZ; dz++) {
                    final char c = husk.charAt(y, dx, dz);
                    if (c == ' ') {
                        continue; // 不触碰
                    }
                    builder.setBlock(
                        originX + dx,
                        originY + y,
                        originZ + dz,
                        BlocksGTSR.ruinedCasing,
                        c == 'C' ? 1 : 0,
                        flags);
                }
            }
        }
    }

    /**
     * 表面查找（S-B4 改全地形列扫，dim78 scatter findSurfaceY 范式）：自上而下第一处
     * "非空气且上方为空气"的地表面；找不到返回 -1。浮岛模型下的"shattered 族表面门 +
     * 兜底回退"随旧方块集/浮岛作废——完整地形下最高裸露面即地表。
     */
    static int findSurfaceY(World world, int x, int z) {
        for (int y = 255; y > 0; y--) {
            final Block block = world.getBlock(x, y, z);
            if (block == null || block.getMaterial() == Material.air) {
                continue;
            }
            if (!world.isAirBlock(x, y + 1, z)) {
                continue; // 非真表面（洞穴顶/悬块下层，防御口径承袭）
            }
            return y;
        }
        return -1;
    }
}
