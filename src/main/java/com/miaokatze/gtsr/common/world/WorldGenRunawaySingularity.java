package com.miaokatze.gtsr.common.world;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.config.Config;

import cpw.mods.fml.common.IWorldGenerator;

/**
 * 失控奇点自然生成（nature 词条）
 * 仅主世界(0)与下界(-1)生效；生成频率由配置 singularitySpawnFrequency 控制（默认 48×48 区块 1 个）；
 * 是否破坏方块由配置 singularityDestroyBlocks 控制（默认开）。
 * 生成参数固定为 nature 特殊状态：range 8-16 / speed 1-3 / damage 0 / duration -1(无限) / color white / fxRadius 10-25。
 * 位置：地表(或水面)方块上方 2-6 格的空气中。
 * <p>
 * dim1 S6b 破碎维度分支：dim == shatteredDimId 且总开关开时同一生成器追加破碎分支——
 * 频率 = singularitySpawnFrequency / max(1, shatteredSingularityMultiplier)（默认 8 → 等效 ×8 密度）；
 * 参数掷骰 30% nature/white（口径同主世界），否则常规攻击态 magenta|purple / speed 2-4 / damage 2.0；
 * type 仍 NATURAL（机器管理逻辑零触碰）。主世界/下界路径的频率公式、参数掷骰顺序与生成参数逐字节不动
 * （破碎分支掷骰仅在 shattered 为真时消耗随机数）。
 */
public class WorldGenRunawaySingularity implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator,
        IChunkProvider chunkProvider) {
        // 维度过滤：仅主世界(0)、下界(-1)与破碎维度分支（dim1 S6b）
        int dim = world.provider.dimensionId;
        // 破碎分支判定：dimId 命中 + 总开关开；shatteredDimId<0（冲突禁用记 -1）视为禁用不入分支
        boolean shattered = dim == Config.shatteredDimId && Config.shatteredDimId >= 0
            && Config.planDimension.shatteredDimension;
        if (dim != 0 && dim != -1 && !shattered) {
            return;
        }
        // 生成频率由配置 singularitySpawnFrequency 控制：平均 N×N 区块 1 个 → 每 chunk 概率 1/(N²)，N 钳制 ≥1 防御
        int freq = Math.max(1, Config.singularitySpawnFrequency);
        // 破碎分支（dim1 S6b）：freq = base / max(1, multiplier)，Config.shatteredSingularityMultiplier 默认 8
        if (shattered) {
            freq = Math.max(1, Config.singularitySpawnFrequency / Math.max(1, Config.shatteredSingularityMultiplier));
        }
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
        // 越界检查（主世界高度上限 256，下界 128；破碎维度走 255 上限）
        int maxY = dim == -1 ? 127 : 255;
        if (y > maxY) {
            return;
        }
        // 目标位置必须可替换(air)
        if (!world.isAirBlock(blockX, y, blockZ)) {
            return;
        }
        // 破碎分支参数掷骰（仅 shattered 为真时消耗随机数；主世界/下界掷骰序列与改动前一致）：
        // 30% nature/white（口径同主世界），否则常规攻击态 magenta|purple 二选一
        int attributeId = TileRunawaySingularity.ATTRIBUTE_NATURE;
        String color = "white";
        boolean shatteredAttack = false;
        if (shattered) {
            if (random.nextInt(100) < 30) {
                // nature 特殊态：参数同主世界口径
            } else {
                // 常规吸引/伤害态：0-999 段（-1..-4 为特殊态，-1 是纯动画 null 态，不承伤不吸引——GTSRCommand.parseSpecial 同语义）
                attributeId = 0;
                color = random.nextBoolean() ? "magenta" : "purple";
                shatteredAttack = true;
            }
        }
        // NBT 参数：range 8-16 / duration -1(无限) / fxRadius 10-25；speed 与 damage 按分支取值（下方三目仅消费一次 nextInt(3)）
        double range = 8.0D + random.nextInt(9); // 8-16
        double speed = shatteredAttack ? 2.0D + random.nextInt(3) : 1.0D + random.nextInt(3); // 攻击态 2-4 / 其余 1-3
        double damage = shatteredAttack ? 2.0D : 0.0D; // 攻击态接触伤害 2.0 / nature 0
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
