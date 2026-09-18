package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;

/**
 * 自然区地表装饰散布器（dim78 S-A1，plan §12 修订 5：修复自然区"光秃秃"；草丛/锈树/碎石三件）。
 * 仅作用于自然区（城 buffer 窗由编排器在装饰调用之前整体 return 天然保证， ProsperityWorldGenerator
 * 编排顺序：城市 → [buffer 窗 return] → 机器 → 散布 → 装饰）。
 * <p>
 * <b>频率登记（硬编码常量，不进 Config——文件域归后续切片；调整走改码）：</b>
 * 草丛 2-4 次尝试/chunk（{@link #TUFT_ATTEMPTS_MIN}/{@link #TUFT_ATTEMPTS_MAX}）、
 * 锈树 1/16 chunk（{@link #TREE_CHANCE_DENOM}）、碎石 0-2 堆/chunk（{@link #RUBBLE_PILES_MAX}）。
 * <p>
 * 确定性：全部随机从 chunk 级哈希派生（盐 {@link #SALT_DECOR}，RuinedMachinePlacer/
 * ProsperitySurfaceScatter 同款 splitmix 范式），同 seed 同坐标跨 chunk 重算一致；禁用 populate 裸
 * Random 语义（Random 仅作哈希种子的取数器）。
 * <p>
 * 让行纪律（不覆盖已有结构）：草丛/碎石逐块 {@code isAirBlock} 让行（02 §8.4 优先级 4 口径）；
 * 锈树整柱干 + 冠层中心列先查空气、有占用整树跳过。接地：逐列 {@code findSurfaceY}
 * （WorldGenRunawaySingularity.java:82-91 范式）。落点门：只落在四群系自然区 top 方块上
 * （城内 meta0-5 / 散布残骸上不长）。碎石用原版 gravel（CityVariants 'g' 键先例；
 * BlockFalling 属性要求必须贴地，逐块独立找地表）。
 * <p>
 * 跨界协议：树冠 ±2 格跨度 → 干位钳制在 chunk 内 2..13（ ProsperitySurfaceScatter "段长收到
 * chunk 边界"同款惯例），ChunkClampedSink 零越界丢弃；草丛/碎石落点单格不跨界。
 */
public final class ProsperityDecorPlacer {

    /** 盐 "decor"（0x006465636F72 截断；chunk 级隔离，区别于机器 0x6D6163 / 散布 0x5C4174）。 */
    private static final long SALT_DECOR = 0x6465636FL;

    /** 草丛放置尝试次数下限/chunk（plan §12 修订 5：草丛 2-4 次/chunk）。 */
    private static final int TUFT_ATTEMPTS_MIN = 2;

    /** 草丛放置尝试次数上限/chunk（含）。 */
    private static final int TUFT_ATTEMPTS_MAX = 4;

    /** 锈树频率分母：每 chunk 1/16 概率一棵（plan §12 修订 5）。 */
    private static final int TREE_CHANCE_DENOM = 16;

    /** 碎石堆数上限/chunk（0..2 堆，"碎石少量"）。 */
    private static final int RUBBLE_PILES_MAX = 2;

    /** 单堆碎石块数上限（1..3 块小簇）。 */
    private static final int RUBBLE_PIECES_MAX = 3;

    /** findSurfaceY 上界（散布同款 200 门；heightAt clamp 40..110 之下留余量）。 */
    private static final int MAX_SURFACE_Y = 200;

    private ProsperityDecorPlacer() {}

    /**
     * populate 入口：草丛 2-4 次尝试 → 锈树 1/16 掷骰 → 碎石 0-2 堆。掷骰顺序固定（确定性）。
     */
    public static void decorate(World world, long worldSeed, int chunkX, int chunkZ, BlockSink sink) {
        if (sink == null) {
            return;
        }
        final Random rand = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, chunkX, chunkZ) ^ SALT_DECOR);
        final StructureBuilder builder = new StructureBuilder(sink);
        placeTufts(world, builder, rand, chunkX, chunkZ);
        if (rand.nextInt(TREE_CHANCE_DENOM) == 0) {
            placeTree(world, builder, rand, chunkX, chunkZ);
        }
        placeRubble(world, builder, rand, chunkX, chunkZ);
    }

    /** 草丛：2-4 次尝试，落点门 = 自然区 top 方块 + 空气让行；草丛按地表群系二分映射。 */
    private static void placeTufts(World world, StructureBuilder builder, Random rand, int chunkX, int chunkZ) {
        final int attempts = TUFT_ATTEMPTS_MIN + rand.nextInt(TUFT_ATTEMPTS_MAX - TUFT_ATTEMPTS_MIN + 1);
        for (int i = 0; i < attempts; i++) {
            final int x = (chunkX << 4) + rand.nextInt(16);
            final int z = (chunkZ << 4) + rand.nextInt(16);
            final int surfaceY = findSurfaceY(world, x, z);
            if (surfaceY <= 0 || surfaceY > MAX_SURFACE_Y) {
                continue;
            }
            final Block tuft = tuftForGround(world.getBlock(x, surfaceY, z));
            if (tuft == null || !world.isAirBlock(x, surfaceY + 1, z)) {
                continue;
            }
            builder.setBlock(x, surfaceY + 1, z, tuft, 0, BlockSink.FLAG_POPULATE);
        }
    }

    /**
     * 草丛二分映射（plan §12 修订 5 双草丛实例的群系分配）：锈草丛 = 草原/荒漠（干燥系），
     * 铜绿草丛 = 森林/沼泽（湿生系）；非自然区 top 返回 null（城内/残骸上不长）。
     */
    private static Block tuftForGround(Block ground) {
        if (ground == BlocksGTSR.prosperitySteppeTop || ground == BlocksGTSR.prosperityWastesTop) {
            return BlocksGTSR.prosperityTuftRust;
        }
        if (ground == BlocksGTSR.prosperityForestTop || ground == BlocksGTSR.prosperitySwampTop) {
            return BlocksGTSR.prosperityTuftCopper;
        }
        return null;
    }

    /**
     * 锈树：干位钳制 chunk 内 2..13（冠层 ±2 不跨界），整柱干 + 冠层中心列空气门，占用即整树跳过；
     * 造型 = 干 3-5 + 冠两层 5×5 缺角 + 一层 3×3 + 顶层十字（vanilla oak 简化口径），逐块空气让行。
     */
    private static void placeTree(World world, StructureBuilder builder, Random rand, int chunkX, int chunkZ) {
        final int x = (chunkX << 4) + 2 + rand.nextInt(12);
        final int z = (chunkZ << 4) + 2 + rand.nextInt(12);
        final int surfaceY = findSurfaceY(world, x, z);
        if (surfaceY <= 0 || surfaceY > MAX_SURFACE_Y || !isNaturalTop(world.getBlock(x, surfaceY, z))) {
            return;
        }
        final int trunkHeight = 3 + rand.nextInt(3); // 干 3-5
        for (int i = 1; i <= trunkHeight; i++) {
            if (!world.isAirBlock(x, surfaceY + i, z)) {
                return; // 让行纪律：整树跳过，不切结构
            }
        }
        final int topY = surfaceY + trunkHeight;
        // 冠层两层 5×5 缺四角（含干顶 y=topY，干格非空气天然让行）
        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    placeLeafIfAir(builder, world, x + dx, topY + dy, z + dz);
                }
            }
        }
        // 3×3 层 + 顶层十字
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                placeLeafIfAir(builder, world, x + dx, topY + 1, z + dz);
            }
        }
        placeLeafIfAir(builder, world, x, topY + 2, z);
        placeLeafIfAir(builder, world, x + 1, topY + 2, z);
        placeLeafIfAir(builder, world, x - 1, topY + 2, z);
        placeLeafIfAir(builder, world, x, topY + 2, z + 1);
        placeLeafIfAir(builder, world, x, topY + 2, z - 1);
        // 干（冠层之后写，干格冠层让行已由 isAirBlock 保证，重写干位无副作用但按惯例后置）
        for (int i = 1; i <= trunkHeight; i++) {
            builder.setBlock(x, surfaceY + i, z, BlocksGTSR.prosperityRustLog, 0, BlockSink.FLAG_POPULATE);
        }
    }

    /** 单块叶：仅空气位放置（让行规则）；冠层坐标经干位钳制保证不越界（见类注释跨界协议）。 */
    private static void placeLeafIfAir(StructureBuilder builder, World world, int x, int y, int z) {
        if (!world.isAirBlock(x, y, z)) {
            return;
        }
        builder.setBlock(x, y, z, BlocksGTSR.prosperityRustLeaves, 0, BlockSink.FLAG_POPULATE);
    }

    /** 碎石：0-2 堆，每堆 1-3 块原版 gravel 小簇；gravel 具 BlockFalling 属性必须逐块贴地。 */
    private static void placeRubble(World world, StructureBuilder builder, Random rand, int chunkX, int chunkZ) {
        final int piles = rand.nextInt(RUBBLE_PILES_MAX + 1);
        for (int i = 0; i < piles; i++) {
            final int cx = (chunkX << 4) + 1 + rand.nextInt(14); // 小簇 ±1 不跨界
            final int cz = (chunkZ << 4) + 1 + rand.nextInt(14);
            final int surfaceY = findSurfaceY(world, cx, cz);
            if (surfaceY <= 0 || surfaceY > MAX_SURFACE_Y || !isNaturalTop(world.getBlock(cx, surfaceY, cz))) {
                continue;
            }
            final int pieces = 1 + rand.nextInt(RUBBLE_PIECES_MAX);
            for (int p = 0; p < pieces; p++) {
                final int px = cx + rand.nextInt(3) - 1;
                final int pz = cz + rand.nextInt(3) - 1;
                final int py = findSurfaceY(world, px, pz); // gravel 逐块贴地（防悬空落后掉落破观感）
                if (py <= 0 || py > MAX_SURFACE_Y || !isNaturalTop(world.getBlock(px, py, pz))) {
                    continue;
                }
                if (!world.isAirBlock(px, py + 1, pz)) {
                    continue;
                }
                builder.setBlock(px, py + 1, pz, Blocks.gravel, 0, BlockSink.FLAG_POPULATE);
            }
        }
    }

    /** 自然区 top 方块判定（四群系独立方块族；放置落点门共用）。 */
    private static boolean isNaturalTop(Block ground) {
        return ground == BlocksGTSR.prosperitySteppeTop || ground == BlocksGTSR.prosperityForestTop
            || ground == BlocksGTSR.prosperityWastesTop
            || ground == BlocksGTSR.prosperitySwampTop;
    }

    /** 自上而下找地表（WorldGenRunawaySingularity.java:82-91 / ProsperitySurfaceScatter 范式）；找不到返回 -1。 */
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
