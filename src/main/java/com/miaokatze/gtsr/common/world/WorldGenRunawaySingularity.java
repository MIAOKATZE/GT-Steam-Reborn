package com.miaokatze.gtsr.common.world;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.common.dimension.framework.GTSROwnedGenerator;
import com.miaokatze.gtsr.config.Config;

import cpw.mods.fml.common.IWorldGenerator;

/** Natural singularities in the Overworld and Nether only. Existing vanilla-dimension random order is preserved. */
public class WorldGenRunawaySingularity implements IWorldGenerator, GTSROwnedGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator,
        IChunkProvider chunkProvider) {
        // 维度过滤：仅主世界(0)、下界(-1)。
        int dim = world.provider.dimensionId;
        if (dim != 0 && dim != -1) {
            return;
        }
        // 生成频率由配置 singularitySpawnFrequency 控制：平均 N×N 区块 1 个 → 每 chunk 概率 1/(N²)，N 钳制 ≥1 防御
        int freq = Math.max(1, Config.singularitySpawnFrequency);
        double chance = 1.0D / (freq * (double) freq);
        if (random.nextDouble() >= chance) {
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
        // 越界检查（主世界高度上限 256，下界 128）。
        int maxY = dim == -1 ? 127 : 255;
        if (y > maxY) {
            return;
        }
        // 目标位置必须可替换(air)
        if (!world.isAirBlock(blockX, y, blockZ)) {
            return;
        }
        int attributeId = TileRunawaySingularity.ATTRIBUTE_NATURE;
        String color = "white";
        // NBT 参数与主世界/下界的既有随机取数顺序保持不变。
        double range = 8.0D + random.nextInt(9); // 8-16
        double speed = 1.0D + random.nextInt(3);
        double damage = 0.0D;
        TileRunawaySingularity.spawnSingularity(
            world,
            blockX,
            y,
            blockZ,
            range,
            speed,
            damage,
            -1,
            attributeId,
            color,
            10.0D + random.nextInt(16),
            Config.singularityDestroyBlocks,
            TileRunawaySingularity.SingularityType.NATURAL); // 自然生成分型：机器管理逻辑零触碰
    }

    /**
     * 查找地表/水面的 y（返回该处方块 y）。
     * 从顶向下扫，返回第一个非空气方块的 y；该方块即地表(或水面，水 Material=water≠air 视作地表)。
     * 调用处会用 surfaceY + 2~6 偏移，使奇点落在该地表方块上方 2-6 格。
     * 下界无统一地表，取顶部实心层(从 127 向下扫)上方。
     * 找不到返回 -1。
     * <p>
     * <b>P3 登记：本列扫刻意不并入框架唯一件
     * {@link com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase#findSurfaceY}</b>
     * （审计 A-5 §1 #8）。除扫描体形状与框架件逐字符相同外，本件另有两点框架件没有的语义：
     * <ol>
     * <li>带 {@code dim} 参并据此分支起点——{@code dim == -1}（下界）从 <b>127</b> 起扫，
     * 其余从 255 起；框架件恒 255；</li>
     * <li>它是 {@code IWorldGenerator} 实例方法、服务<b>主世界/下界</b>，不是新维度 L2 高度场的
     * 一部分（plan §2.1 的 L2 契约只管 dim78/79），把它接进框架件会让主世界生成反向依赖
     * 维度 provider 基类。</li>
     * </ol>
     * 两点任一被"统一"都会改变奇点生成高度或引入跨层依赖，故本片只登记不合并。
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
