package com.miaokatze.gtsr.common.world;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * 失控奇点自然生成（nature 词条）
 * 仅主世界(0)与下界(-1)生效；平均每 24×24=576 个区块生成 1 个。
 * 生成参数固定为 nature 特殊状态：range 8-16 / speed 1-3 / damage 0 / duration -1(无限) / color white / fxRadius 2.5。
 * 位置：地表(或水面)方块上方 2-6 格的空气中。
 */
public class WorldGenRunawaySingularity implements IWorldGenerator {

    /** 平均每 24×24=576 个区块生成 1 个 → 每 chunk 概率 1/576 */
    private static final double CHANCE_PER_CHUNK = 1.0D / 576.0D;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator,
        IChunkProvider chunkProvider) {
        // 维度过滤：仅主世界(0)与下界(-1)
        int dim = world.provider.dimensionId;
        if (dim != 0 && dim != -1) {
            return;
        }
        if (random.nextDouble() >= CHANCE_PER_CHUNK) {
            return;
        }
        // chunk 内随机位置
        int blockX = chunkX * 16 + random.nextInt(16);
        int blockZ = chunkZ * 16 + random.nextInt(16);
        // 地表/水面 y（返回该处方块 y，奇点将放在其上方 2-6 格）
        int surfaceY = findSurfaceY(world, blockX, blockZ, dim);
        if (surfaceY < 0) {
            return;
        }
        int y = surfaceY + 2 + random.nextInt(5);
        // 越界检查（主世界高度上限 256，下界 128）
        int maxY = dim == -1 ? 127 : 255;
        if (y > maxY) {
            return;
        }
        // 目标位置必须可替换(air)
        if (!world.isAirBlock(blockX, y, blockZ)) {
            return;
        }
        // NBT 参数：range 8-16 / speed 1-3 / damage 0 / duration -1(无限) / nature(-4) / white / fxRadius 2.5
        double range = 8.0D + random.nextInt(9); // 8-16
        double speed = 1.0D + random.nextInt(3); // 1-3
        TileRunawaySingularity.spawnSingularity(
            world,
            blockX,
            y,
            blockZ,
            range,
            speed,
            0.0D,
            -1,
            TileRunawaySingularity.ATTRIBUTE_NATURE,
            "white",
            2.5D);
    }

    /**
     * 查找地表/水面的 y（返回该处方块 y）。
     * 从顶向下扫，返回第一个非空气方块的 y；该方块即地表(或水面，水 Material=water≠air 视作地表)。
     * 调用处会用 surfaceY + 2~6 偏移，使奇点落在该地表方块上方 2-6 格。
     * 下界无统一地表，取顶部实心层(从 127 向下扫)上方。
     * 找不到返回 -1。
     */
    private int findSurfaceY(World world, int x, int z, int dim) {
        int topY = dim == -1 ? 127 : 255;
        for (int y = topY; y > 0; y--) {
            Block block = world.getBlock(x, y, z);
            if (block != null && block.getMaterial() != Material.air) {
                return y;
            }
        }
        return -1;
    }
}
