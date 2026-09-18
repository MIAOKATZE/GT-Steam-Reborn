package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import static com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase.findSurfaceY;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.config.Config;

/**
 * 残缺机器放置器（dim1 S4a，plan §1.2 S4a / 02 §6 裁剪版）：
 * 频率 = 1/{@link Config#prosperityMachineChance} chunk × 群系机器权重（02 §1.1 表），
 * 机型在 {@link RuinedMachineShapes#ALL} 5 机型中均匀掷选，损伤度 20-95、部件缺失概率
 * 10%/25%/45% 双重掷上限 40%（02 §6.3 表），'C' 核心位落积碳壳 meta1——<b>无 TileEntity、无控制器、
 * 无修复/战利品</b>（用户裁剪范围）。放置经 {@link StructureBuilder} → 注入 {@link BlockSink}
 * （世界生成期 ChunkClampedSink / S5 指令期 DirectWorldSink 同一放置路径）。
 * <p>
 * 跨界协议：origin 在 chunk 内做 footprint 收缩钳制，整机完整落在当前 populate chunk 内
 * ——ChunkClampedSink 零丢弃，plan S4a 验收"无跨界缺角/无越界告警"由构造保证。
 * 所有随机从 chunk 确定性哈希派生（02 §0.3），禁用 populate 裸 Random。
 */
public final class RuinedMachinePlacer {

    /** 盐 "mac"（02 §6.2 代码 15 同款）。 */
    private static final long SALT_MACHINE = 0x6D6163L;

    /** 群系机器权重表已由编排器折算传入；本类不做群系查询。 */

    private static volatile boolean registered;

    /** 方块键 → Block 解析表（游戏内放置路径专用；延迟初始化避免离线驱动触碰）。 */
    private static Map<String, Block> blockResolver;

    private RuinedMachinePlacer() {}

    /**
     * 向 {@link StructureRegistry} 登记 5 机型变体（dimension=PROSPERITY，幂等）。
     * 由 ProsperityWorldGenerator 构造时调用（CommonProxy.init 注册线），S5 /gtsr structure
     * 补全同源消费。placer 回调走 DirectWorldSink 语义 flag。
     */
    public static void registerVariants() {
        if (registered) {
            return;
        }
        registered = true;
        for (final RuinedMachineShapes.Shape shape : RuinedMachineShapes.ALL) {
            StructureRegistry.register(
                new StructureRegistry.Entry(
                    shape.name,
                    StructureRegistry.Dimension.PROSPERITY,
                    shape.sizeX,
                    shape.sizeZ,
                    (sink, x, y, z, seed) -> place(
                        new StructureBuilder(sink),
                        new Random(seed),
                        shape,
                        x,
                        y,
                        z,
                        BlockSink.FLAG_DIRECT)));
        }
    }

    /**
     * populate 入口：掷频 → 掷机型 → 选点（footprint 收缩钳制在 chunk 内）→ 落点判定 → 放置。
     *
     * @param biomeMachineWeight 群系机器权重（02 §1.1：草原 0.7/森林 1.2/荒漠 0.9/沼泽 0.8）
     */
    public static void placeAll(World world, long worldSeed, int cx, int cz, float biomeMachineWeight, BlockSink sink) {
        final int chance = Config.prosperityMachineChance;
        if (chance <= 0 || sink == null || biomeMachineWeight <= 0F) {
            return; // 0 = 禁用（plan S4a 失败回退开关）
        }
        final Random r = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) ^ SALT_MACHINE);
        // P(生成/chunk) = 群系机器权重 / chance。分母唯一出处 = Config.prosperityMachineChance
        // （P5 plan §2.4 判据 4 / §3.1 更正 2：改造前这句注释写死过一个具体分母，与代码默认值漂移，
        // 导致机器密度口径三处不一致；此处起只写公式与出处，不写数字。）
        if (r.nextDouble() * chance >= biomeMachineWeight) {
            return;
        }
        final RuinedMachineShapes.Shape shape = RuinedMachineShapes.ALL[r.nextInt(RuinedMachineShapes.ALL.length)];
        // footprint 收缩钳制：origin 使整机（含垫层）完全落在当前 chunk（>16 格形状防御性跳过）
        final int freeX = 16 - shape.sizeX;
        final int freeZ = 16 - shape.sizeZ;
        if (freeX < 0 || freeZ < 0) {
            return;
        }
        final int x = (cx << 4) + r.nextInt(freeX + 1);
        final int z = (cz << 4) + r.nextInt(freeZ + 1);
        // 落点判定：中心列自上而下找地表（WorldGenRunawaySingularity.java:82-91 范式）
        final int surfaceY = findSurfaceY(world, x + shape.sizeX / 2, z + shape.sizeZ / 2);
        if (surfaceY < 20 || surfaceY > 200) {
            return;
        }
        // 让行（02 §8.4）：落点地表必须是实心锈变地表（S-A1 连带放宽：四自然 top ∪ prosperitySurface
        // ——A1 主体换装后自然区 top 为群系新方块，仅认 prosperitySurface 会使自然区机器归零；
        // 矿坑/矿洞空腔/水体自动跳过）
        if (!ProsperityOutpostPlacer.isNaturalProsperityTop(world.getBlock(x + shape.sizeX / 2, surfaceY, z))) {
            return;
        }
        place(new StructureBuilder(sink), r, shape, x, surfaceY + 1, z, BlockSink.FLAG_POPULATE);
    }

    /**
     * 形状放置核心（世界生成与 /gtsr structure 共用）。
     * 基座层（y=0 垫层）恒放置；其余层按损伤度掷缺失（缺失=不触碰地形，保留"残骸被岁月吞没"观感）。
     */
    static void place(StructureBuilder builder, Random r, RuinedMachineShapes.Shape shape, int originX, int originY,
        int originZ, int flags) {
        final int damage = 20 + r.nextInt(76); // 损伤度 20-95（02 §6.3）
        final int missingChance = damage < 40 ? 10 : damage < 70 ? 25 : 45;
        for (int y = 0; y < shape.sizeY; y++) {
            for (int dx = 0; dx < shape.sizeX; dx++) {
                for (int dz = 0; dz < shape.sizeZ; dz++) {
                    final char c = shape.charAt(y, dx, dz);
                    final int wx = originX + dx;
                    final int wy = originY + y;
                    final int wz = originZ + dz;
                    if (c == ' ') {
                        continue; // 不触碰：地形让行
                    }
                    if (c == '.') {
                        builder.setBlock(wx, wy, wz, Blocks.air, 0, flags); // 清空（炉膛/烟道/塌口）
                        continue;
                    }
                    if (y > 0 && r.nextInt(100) < missingChance && r.nextInt(100) < 40) {
                        continue; // 双重掷缺失，单部件缺失率上限 40%（02 §6.3）；垫层不缺失
                    }
                    final Block block = resolveBlock(RuinedMachineShapes.blockKeyOf(c));
                    if (block == null) {
                        continue;
                    }
                    builder.setBlock(wx, wy, wz, block, RuinedMachineShapes.metaOf(c), flags);
                }
            }
        }
    }

    // P3（plan §5 P3 / 审计 A-5 §1 #3）：本类原有一份私有 findSurfaceY(World,int,int)，与
    // ProsperitySurfaceScatter #2 / ProsperityDecorPlacer #4 / ProsperityOutpostPlacer #5 /
    // ShatteredDecorPlacer #6 共 5 份实现体经 diff 实测逐字符等价，已并到框架唯一件
    // GTSRChunkProviderBase.findSurfaceY（本文件静态导入，调用点 originY=surfaceY+1 一字未改）。
    // 注意：机器接地仍走列扫而非 heightAt 纯函数——两套接地并存属审计 A-4，统一归 P7，本片不动。

    /** 方块键解析（含未注册防御：总开关关闭等场景方块可能缺失，跳过该部件不炸生成链）。 */
    private static Block resolveBlock(String key) {
        if (blockResolver == null) {
            final Map<String, Block> resolver = new HashMap<>();
            resolver.put("gtsr:RuinedCasing", BlocksGTSR.ruinedCasing);
            resolver.put("gtsr:RuinDebris", BlocksGTSR.ruinDebris);
            resolver.put("gtsr:ProsperitySurface", BlocksGTSR.prosperitySurface);
            blockResolver = resolver;
        }
        return blockResolver.get(key);
    }
}
