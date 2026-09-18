package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import static com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase.findSurfaceY;

import java.util.Random;

import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;

/**
 * 地表人工痕迹散布器（dim1 S4a，plan §1.2 S4a / 02 §3.2 代码 5 裁剪版）：
 * 预算 64 次/chunk × 群系散布权重（02 §1.1 表；S-A5 密度上调 32→64 = plan §12 修订 7"散布可以
 * 更大，本身稀有"），散布物权重表 轨枕30/管道25/铆接板30/烟囱残段15（02 §3.1）。只散布在锈变地表
 * （<b>P4 起直调框架单一谓词 {@link SurfaceGate}</b>：dim78 声明集 = 四自然 top 方块族 ∪
 * prosperitySurface——S-A1 连带放宽，A1 主体换装后自然区 top 为群系新方块；02 §3.2 门。
 * 改造前本类横向直调 {@code ProsperityOutpostPlacer} 的内部谓词（审计 A-2 #3），本处不再互调；
 * {@code RuinedMachinePlacer:106} 的同一处横向直调不在本片允许路径内，残留归 P7）。烟囱残段逐格
 * {@code isAirBlock} 让行（只长在空气里，02 §8.4 优先级 4）。
 * <p>
 * 所有随机从 chunk 确定性哈希派生（盐 "ScAt" 0x5C4174，02 §3.2 原样）；放置经注入的
 * {@link BlockSink}（ChunkClampedSink 钳制），散布落点/短线段钳制在 chunk 内——零越界丢弃。
 * <p>
 * <b>P3（plan §5 P3 / 审计 A-5 §1 #2）</b>：本类原有的私有 {@code findSurfaceY(World,int,int)}
 * 是 dim78/dim79 内 <b>5 份逐字符等价</b>列扫之一（当时的"基准"份），现已并入框架唯一件
 * {@link com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase#findSurfaceY}
 * ——本文件以静态导入引用，故 {@code findSurfaceY(world, x, z)} 调用点一字未改，
 * 接地 y 取值口径（自上而下第一处非空气、兜底 -1）逐位不变。
 */
public final class ProsperitySurfaceScatter {

    /** 每 chunk 预算（S-A5 密度上调 32→64，plan §12 修订 7）。 */
    private static final int BUDGET_PER_CHUNK = 64;

    /** 盐 "ScAt"（02 §3.2 代码 5 同款）。 */
    private static final long SALT_SCATTER = 0x5C4174L;

    /** 本类所属维度键（P4：门的显式维度入参，取 L1 账本同一词汇，不另造字符串）。 */
    private static final String DIM_KEY = SurfaceGate.DIM78;

    /** 散布物权重：0 轨枕 30 / 1 管道 25 / 2 铆接板 30 / 3 烟囱残段 15（02 §3.1）。 */
    private static final int[] WEIGHTS = { 30, 25, 30, 15 };

    private ProsperitySurfaceScatter() {}

    /**
     * populate 入口。
     *
     * @param biomeScatterWeight 群系散布权重（02 §1.1：草原 1.2/森林 1.0/荒漠 1.5/沼泽 1.1）
     */
    public static void scatter(World world, long worldSeed, int cx, int cz, float biomeScatterWeight, BlockSink sink) {
        if (sink == null || biomeScatterWeight <= 0F) {
            return;
        }
        final Random rand = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) ^ SALT_SCATTER);
        final int budget = (int) (BUDGET_PER_CHUNK * biomeScatterWeight);
        final StructureBuilder builder = new StructureBuilder(sink);
        for (int i = 0; i < budget; i++) {
            final int x = (cx << 4) + rand.nextInt(16);
            final int z = (cz << 4) + rand.nextInt(16);
            final int surfaceY = findSurfaceY(world, x, z); // WorldGenRunawaySingularity.java:82-91 范式
            if (surfaceY <= 0 || surfaceY > 200) {
                continue;
            }
            // 只散布在锈变地表上（02 §3.2 代码 5 门；P4 起走框架单一谓词，集合见 SurfaceGate
            // roster 表：四自然 top ∪ prosperitySurface）
            if (!SurfaceGate.isNaturalTop(DIM_KEY, SurfaceGate.landableTops(DIM_KEY),
                world.getBlock(x, surfaceY, z))) {
                continue;
            }
            final int y = surfaceY + 1;
            switch (pickWeighted(rand, WEIGHTS)) {
                case 0:
                    placeSleeper(builder, world, x, y, z);
                    break;
                case 1:
                    placePipeRun(builder, world, x, y, z, rand, cx, cz);
                    break;
                case 2:
                    placeRivetPlate(builder, world, x, y, z);
                    break;
                case 3:
                    placeChimneyStub(builder, world, x, y, z, rand);
                    break;
                default:
            }
        }
    }

    /** 轨枕：单方块 meta0（占位让行：只落空气位，02 §8.4 优先级 4）。 */
    private static void placeSleeper(StructureBuilder builder, World world, int x, int y, int z) {
        if (!world.isAirBlock(x, y, z)) {
            return;
        }
        builder.setBlock(x, y, z, BlocksGTSR.ruinDebris, BlockRuinDebrisMeta.SLEEPER, BlockSink.FLAG_POPULATE);
    }

    /** 废弃管道段：1-3 连直排（哈希方向 ±X/±Z），段内逐格空气让行，越 chunk 截断（零越界丢弃）。 */
    private static void placePipeRun(StructureBuilder builder, World world, int x, int y, int z, Random rand, int cx,
        int cz) {
        final boolean alongX = rand.nextBoolean();
        final int dir = rand.nextBoolean() ? 1 : -1;
        final int local = alongX ? (x - (cx << 4)) : (z - (cz << 4));
        // 段长上限收到 chunk 边界（dir=+1 时留界内余量，dir=-1 时退格数即余量）
        final int free = dir > 0 ? 15 - local : local;
        final int length = Math.min(1 + rand.nextInt(3), Math.max(1, free + 1));
        for (int i = 0; i < length; i++) {
            final int wx = alongX ? x + dir * i : x;
            final int wz = alongX ? z : z + dir * i;
            if (!world.isAirBlock(wx, y, wz)) {
                break; // 让行规则：只长在空气里（02 §3.2）
            }
            builder.setBlock(wx, y, wz, BlocksGTSR.ruinDebris, BlockRuinDebrisMeta.PIPE, BlockSink.FLAG_POPULATE);
        }
    }

    /** 铆接板：单方块 meta2（平铺姿态，占位让行）。 */
    private static void placeRivetPlate(StructureBuilder builder, World world, int x, int y, int z) {
        if (!world.isAirBlock(x, y, z)) {
            return;
        }
        builder.setBlock(x, y, z, BlocksGTSR.ruinDebris, BlockRuinDebrisMeta.RIVET_PLATE, BlockSink.FLAG_POPULATE);
    }

    /** 烟囱残段：2-5 高残塔（逐格空气让行），1/3 概率顶部破口换铆接板（02 §3.2 代码 5 原样）。 */
    private static void placeChimneyStub(StructureBuilder builder, World world, int x, int y, int z, Random rand) {
        final int height = 2 + rand.nextInt(4); // 2-5 残塔
        int placed = 0;
        for (int i = 0; i < height; i++) {
            if (!world.isAirBlock(x, y + i, z)) {
                break; // 让行规则：只长在空气里
            }
            builder.setBlock(x, y + i, z, BlocksGTSR.ruinDebris, BlockRuinDebrisMeta.CHIMNEY, BlockSink.FLAG_POPULATE);
            placed++;
        }
        if (placed > 0 && rand.nextInt(3) == 0) { // 顶部破口掉半块
            builder.setBlock(
                x,
                y + placed - 1,
                z,
                BlocksGTSR.ruinDebris,
                BlockRuinDebrisMeta.RIVET_PLATE,
                BlockSink.FLAG_POPULATE);
        }
    }

    /** 权重选取（02 §3.2 pickWeighted 原样）。 */
    private static int pickWeighted(Random rand, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int roll = rand.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            if (roll < weights[i]) {
                return i;
            }
            roll -= weights[i];
        }
        return 0;
    }

    /** BlockRuinDebris meta 常量引用（避免反向依赖 S2 方块类名在散布层扩散）。 */
    private static final class BlockRuinDebrisMeta {

        static final int SLEEPER = 0;
        static final int PIPE = 1;
        static final int RIVET_PLATE = 2;
        static final int CHIMNEY = 3;
    }
}
