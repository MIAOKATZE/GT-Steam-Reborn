package com.miaokatze.gtsr.common.dimension.shattered;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;

/**
 * 破碎之地地表装饰散布器（dim79 重做 S-B 装饰子项，plan §12 修订 9：至少 1-2 种硬方块风
 * 装饰生成）：石刺簇 / 灰烬枯树 / 灰烬棘丛三件。挂接于 ChunkProviderShatteredGrounds
 * .onPopulate（provider populate 通道；dim79 无城市编排器，全 chunk 无差别散布）。
 * <p>
 * <b>频率登记（硬编码常量，不进 Config——调整走改码，ProsperityDecorPlacer 同款纪律）：</b>
 * 石刺簇 2-4 次尝试/chunk（{@link #SPIKE_ATTEMPTS_MIN}/{@link #SPIKE_ATTEMPTS_MAX}）、
 * 灰烬枯树 1/10 chunk（{@link #DEAD_TREE_CHANCE_DENOM}，干高 2-5，30% 基部伴生棘丛）、
 * 灰烬棘丛 0-2 次/chunk（{@link #THORN_ATTEMPTS_MAX}）。
 * <p>
 * 确定性：全部随机从 chunk 级哈希派生（盐 {@link #SALT_DECOR}，splitmix 终结哈希族），
 * 同 seed 同坐标跨 chunk 重算一致；禁用 populate 裸 Random 语义。
 * <p>
 * 让行纪律（不覆盖已有结构/地形）：逐块 {@code isAirBlock} 让行；枯树整柱干先查空气、
 * 有占用整树跳过。接地：逐列 {@link #findSurfaceY}（全地形列扫，dim78 scatter 同范式）。
 * 落点门：只落在四群系自然区 top 方块上（corestone 主体/独碑平台上不长）。
 * <p>
 * 跨界协议：全部落点单格、枯树干单列（无冠层跨度），天然零跨界，ChunkClampedSink 零丢弃。
 */
public final class ShatteredDecorPlacer {

    /** 盐 "shde"（chunk 级隔离；区别于遗迹 0x73687275/"shru"）。 */
    private static final long SALT_DECOR = 0x73686465L;

    /** 石刺簇放置尝试次数下限/chunk。 */
    private static final int SPIKE_ATTEMPTS_MIN = 2;

    /** 石刺簇放置尝试次数上限/chunk（含）。 */
    private static final int SPIKE_ATTEMPTS_MAX = 4;

    /** 灰烬枯树频率分母：每 chunk 1/10 概率一棵。 */
    private static final int DEAD_TREE_CHANCE_DENOM = 10;

    /** 灰烬棘丛放置尝试次数上限/chunk（0..2 次）。 */
    private static final int THORN_ATTEMPTS_MAX = 2;

    /** 枯树基部伴生棘丛概率分母（1/3）。 */
    private static final int TREE_THORN_CHANCE_DENOM = 3;

    /** findSurfaceY 上界（地形钳制域上沿 96；域外不落）。 */
    private static final int MAX_SURFACE_Y = 96;

    private ShatteredDecorPlacer() {}

    /**
     * populate 入口：石刺簇 2-4 次尝试 → 灰烬枯树 1/10 掷骰 → 灰烬棘丛 0-2 次。掷骰顺序固定
     * （确定性）。方块族未就绪防御（BlockLoader 异常场景整体跳过，不炸生成链）。
     */
    public static void decorate(World world, long worldSeed, int chunkX, int chunkZ, BlockSink sink) {
        if (sink == null || BlocksGTSR.shatteredAshTop == null || BlocksGTSR.shatteredSpikeCluster == null) {
            return;
        }
        final Random rand = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, chunkX, chunkZ) ^ SALT_DECOR);
        final StructureBuilder builder = new StructureBuilder(sink);
        placeSpikes(world, builder, rand, chunkX, chunkZ);
        if (rand.nextInt(DEAD_TREE_CHANCE_DENOM) == 0) {
            placeDeadTree(world, builder, rand, chunkX, chunkZ);
        }
        placeThorns(world, builder, rand, chunkX, chunkZ);
    }

    /** 石刺簇：2-4 次尝试，落点门 = 自然区 top 方块 + 空气让行。 */
    private static void placeSpikes(World world, StructureBuilder builder, Random rand, int chunkX, int chunkZ) {
        final int attempts = SPIKE_ATTEMPTS_MIN + rand.nextInt(SPIKE_ATTEMPTS_MAX - SPIKE_ATTEMPTS_MIN + 1);
        for (int i = 0; i < attempts; i++) {
            final int x = (chunkX << 4) + rand.nextInt(16);
            final int z = (chunkZ << 4) + rand.nextInt(16);
            final int surfaceY = findSurfaceY(world, x, z);
            if (!placeableOnNaturalTop(world, x, surfaceY, z)) {
                continue;
            }
            builder.setBlock(x, surfaceY + 1, z, BlocksGTSR.shatteredSpikeCluster, 0, BlockSink.FLAG_POPULATE);
        }
    }

    /**
     * 灰烬枯树：裸干无叶（石化枯木意象），干高 2-5；整柱干空气门，占用即整树跳过；
     * 1/3 概率基部伴生一株灰烬棘丛（相邻格，让行独立判定）。
     */
    private static void placeDeadTree(World world, StructureBuilder builder, Random rand, int chunkX, int chunkZ) {
        final int x = (chunkX << 4) + rand.nextInt(16);
        final int z = (chunkZ << 4) + rand.nextInt(16);
        final int surfaceY = findSurfaceY(world, x, z);
        if (!placeableOnNaturalTop(world, x, surfaceY, z)) {
            return;
        }
        final int trunkHeight = 2 + rand.nextInt(4); // 干 2-5
        for (int i = 1; i <= trunkHeight; i++) {
            if (!world.isAirBlock(x, surfaceY + i, z)) {
                return; // 让行纪律：整树跳过，不切结构
            }
        }
        for (int i = 1; i <= trunkHeight; i++) {
            builder.setBlock(x, surfaceY + i, z, BlocksGTSR.shatteredAshLog, 0, BlockSink.FLAG_POPULATE);
        }
        if (rand.nextInt(TREE_THORN_CHANCE_DENOM) == 0) {
            final int tx = x + (rand.nextBoolean() ? 1 : -1);
            final int tz = z + (rand.nextBoolean() ? 1 : -1);
            final int tY = findSurfaceY(world, tx, tz);
            if (placeableOnNaturalTop(world, tx, tY, tz)) {
                builder.setBlock(tx, tY + 1, tz, BlocksGTSR.shatteredAshThorn, 0, BlockSink.FLAG_POPULATE);
            }
        }
    }

    /** 灰烬棘丛：0-2 次尝试，落点门同石刺簇。 */
    private static void placeThorns(World world, StructureBuilder builder, Random rand, int chunkX, int chunkZ) {
        final int attempts = rand.nextInt(THORN_ATTEMPTS_MAX + 1);
        for (int i = 0; i < attempts; i++) {
            final int x = (chunkX << 4) + rand.nextInt(16);
            final int z = (chunkZ << 4) + rand.nextInt(16);
            final int surfaceY = findSurfaceY(world, x, z);
            if (!placeableOnNaturalTop(world, x, surfaceY, z)) {
                continue;
            }
            builder.setBlock(x, surfaceY + 1, z, BlocksGTSR.shatteredAshThorn, 0, BlockSink.FLAG_POPULATE);
        }
    }

    /** 落点门：地表 y 在域内、地面为四群系自然区 top、上方空气（让行）。 */
    private static boolean placeableOnNaturalTop(World world, int x, int surfaceY, int z) {
        if (surfaceY <= 0 || surfaceY > MAX_SURFACE_Y) {
            return false;
        }
        return isNaturalTop(world.getBlock(x, surfaceY, z)) && world.isAirBlock(x, surfaceY + 1, z);
    }

    /** 自然区 top 方块判定（四群系独立方块族；放置落点门共用）。 */
    private static boolean isNaturalTop(Block ground) {
        return ground == BlocksGTSR.shatteredAshTop || ground == BlocksGTSR.shatteredSlagTop
            || ground == BlocksGTSR.shatteredGlassTop
            || ground == BlocksGTSR.shatteredTarTop;
    }

    /** 自上而下全地形列扫找地表（dim78 scatter findSurfaceY 范式）；找不到返回 -1。 */
    private static int findSurfaceY(World world, int x, int z) {
        for (int y = 255; y > 0; y--) {
            final Block block = world.getBlock(x, y, z);
            if (block != null && block.getMaterial() != net.minecraft.block.material.Material.air) {
                return y;
            }
        }
        return -1;
    }
}
