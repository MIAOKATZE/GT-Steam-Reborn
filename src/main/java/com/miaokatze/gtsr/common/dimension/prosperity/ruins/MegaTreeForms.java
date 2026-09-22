package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/**
 * 巨树三形态（T7，plan §3.7 表 mega 列 / §3.8）：①{@link ProsperityDecorPlacer#MEGA_REDWOOD}
 * 圆锥实心层圆干 + 顶部轮枝 + 冠顶 6 层盘收尖（森林，Highlands WorldGenTreeRedwood 的
 * {@code theta=atan(2.2/H)} 收分参数化）；②{@link ProsperityDecorPlacer#MEGA_GREATOAK} vanilla
 * BigTree 分叉参数化（heightAttenuation 0.618 / leafDensity 0.8 / leafDistanceLimit 4 量级，
 * 草原）+ 遇障自底向上射线截断（validTreeLocation 式）；③{@link ProsperityDecorPlacer#MEGA_BAYOU}
 * 底部 4 层 2×2+十字板根 + 平展伞冠（沼泽/sanzu，marsh 冠形放大），可落在水缘
 * （水下列 + ±2 邻列有自然 top ⇒ 干自 {@code SEA_LEVEL}=68 起，即"水面 y=68 边缘"）。
 * <p>
 * <b>通用纪律（plan §3.7）</b>：
 * <ul>
 * <li><b>大净空预检，失败跳过/截断，不硬写</b>：Redwood 走 isCubeClear(4, h) 立方净空（失败整树跳过）；
 * GreatOak 走主干列自底向上射线（障碍高度截断树高，截得太矮跳过）；Bayou 走主干柱空气门
 * （"允许顶部局部越界、主干柱仍需空气"的放宽口径——冠/顶不设整域门）；</li>
 * <li><b>叶只覆写 air/草/雪</b>：全部叶落块经 {@link ProsperityDecorPlacer#placeLeafIfAir}（T7 扩展口径）；</li>
 * <li><b>跨界协议零越界</b>：三种形态的水平最大偏移都 ≤ 各自干位内收量（Redwood/Bayou 干位
 * 内收 6 ⇒ 最大偏移 6；GreatOak 枝+叶团最大偏移 7 ⇒ 干位内收 7），任何一格不出 chunk；
 * 板根/轮枝的<b>干</b>落块只写空气位（Bayou 板根额外允许写水位）；</li>
 * <li>接地/水缘判定与灌木/普通树共用 {@link ProsperityDecorPlacer#naturalTopAt} 同一谓词。</li>
 * </ul>
 */
final class MegaTreeForms {

    /** Redwood/Bayou 的干位内收量（= 冠/板根最大水平偏移；16−2×6=4 个落位）。 */
    private static final int MARGIN_CANOPY = 6;
    /** GreatOak 的干位内收量（枝长 3 + 叶团半径 4 = 最大偏移 7；16−2×7=2 个落位）。 */
    private static final int MARGIN_OAK = 7;
    /** Redwood 收分角参数（Highlands WorldGenTreeRedwood：{@code theta = atan(2.2/H)}，底径 ≈ 2）。 */
    private static final double REDWOOD_TAPER = 2.2D;
    /** GreatOak 高度衰减（vanilla BigTree {@code heightAttenuation = 0.618}：叶团上收）。 */
    private static final double OAK_ATTENUATION = 0.618D;
    /** GreatOak 叶密度（vanilla BigTree {@code leafDensity 0.8} 量级，百分数）。 */
    private static final int OAK_LEAF_DENSITY_PCT = 80;
    /** GreatOak 截断下限：低于它不成巨树（validTreeLocation 式截断后太矮 ⇒ 跳过不硬写）。 */
    private static final int OAK_MIN_HEIGHT = 14;
    /** Bayou 水缘探测的邻列距离（±2，均在干位内收量内 ⇒ 探测不出 chunk）。 */
    private static final int BAYOU_EDGE_PROBE = 2;
    /** 四正向（Redwood 轮枝臂向）。 */
    private static final int[][] CARDINALS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
    /** 八方向罗盘（GreatOak 枝向）。 */
    private static final int[][] COMPASS = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 },
        { 1, -1 } };

    private MegaTreeForms() {}

    /** 形态分发（消费方：{@code ProsperityDecorPlacer.placeTreePass} 巨树趟）。返回是否落了树。 */
    static boolean place(World world, long worldSeed, StructureBuilder builder, Random rand, int chunkX, int chunkZ,
        int form, Block log, Block leaves) {
        switch (form) {
            case ProsperityDecorPlacer.MEGA_GREATOAK: {
                return greatOak(world, builder, rand, chunkX, chunkZ, log, leaves);
            }
            case ProsperityDecorPlacer.MEGA_REDWOOD: {
                return redwood(world, builder, rand, chunkX, chunkZ, log, leaves);
            }
            case ProsperityDecorPlacer.MEGA_BAYOU: {
                return bayou(world, worldSeed, builder, rand, chunkX, chunkZ, log, leaves);
            }
            default: {
                return false;
            }
        }
    }

    // ═══════════════════════════ ② GreatOak 式（草原，干 20-28）═══════════════════════════

    /**
     * vanilla BigTree 参数化：主干占干高上段的 {@code h-3}，其上 3-4 根缓升枝 + 枝端/干顶叶团；
     * 遇障自底向上射线探测（主干列首个非空气位截断 desired 高度）。
     */
    private static boolean greatOak(World world, StructureBuilder builder, Random rand, int chunkX, int chunkZ,
        Block log, Block leaves) {
        final int x = (chunkX << 4) + MARGIN_OAK + rand.nextInt(16 - 2 * MARGIN_OAK);
        final int z = (chunkZ << 4) + MARGIN_OAK + rand.nextInt(16 - 2 * MARGIN_OAK);
        final int y0 = ProsperityDecorPlacer.naturalTopAt(world, x, z);
        if (y0 < 0) {
            return false;
        }
        final int desired = 20 + rand.nextInt(9);
        if (y0 + desired + 3 > 255) {
            return false;
        }
        // 自底向上射线（validTreeLocation 式）：主干列第一个非空气位截断树高
        int free = 0;
        while (free < desired && world.isAirBlock(x, y0 + 1 + free, z)) {
            free++;
        }
        final int h = Math.min(desired, free);
        if (h < OAK_MIN_HEIGHT) {
            return false; // 截得太矮不成巨树，跳过不硬写
        }
        final int trunkTop = y0 + h - 3;
        // 干顶大叶团（leafDistanceLimit 4 量级）
        leafCluster(builder, world, rand, x, trunkTop + 2, z, 4, leaves);
        // 3-4 根枝：8 向缓升，枝端小叶团
        final int branches = 3 + rand.nextInt(2);
        for (int b = 0; b < branches; b++) {
            final int[] dir = COMPASS[rand.nextInt(8)];
            int bx = x;
            int bz = z;
            int by = trunkTop - 1 - rand.nextInt(3);
            final int len = 2 + rand.nextInt(2);
            for (int k = 0; k < len; k++) {
                bx += dir[0];
                bz += dir[1];
                if ((k & 1) == 1) {
                    by++;
                }
                builder.setBlock(bx, by, bz, log, 0, BlockSink.FLAG_POPULATE);
            }
            leafCluster(builder, world, rand, bx, by + 1, bz, 3, leaves);
        }
        // 主干（冠层之后写，同 placeTree 惯例）
        for (int i = 1; i <= h - 3; i++) {
            builder.setBlock(x, y0 + i, z, log, 0, BlockSink.FLAG_POPULATE);
        }
        return true;
    }

    /** BigTree 叶团：半径 R 的缺角盘堆，{@code heightAttenuation} 让上两层收径、下两层放一档。 */
    private static void leafCluster(StructureBuilder builder, World world, Random rand, int cx, int cy, int cz, int r,
        Block leaves) {
        for (int dy = -2; dy <= 2; dy++) {
            int rr = r - (int) Math.round(OAK_ATTENUATION * dy);
            if (dy < 0) {
                rr = r + 1;
            }
            for (int dx = -rr; dx <= rr; dx++) {
                for (int dz = -rr; dz <= rr; dz++) {
                    if (Math.abs(dx) == rr && Math.abs(dz) == rr) {
                        continue;
                    }
                    if (rand.nextInt(100) < OAK_LEAF_DENSITY_PCT) {
                        ProsperityDecorPlacer.placeLeafIfAir(builder, world, cx + dx, cy + dy, cz + dz, leaves);
                    }
                }
            }
        }
    }

    // ═══════════════════════════ ① Redwood 式（森林，干 28-38）═══════════════════════════

    /**
     * 圆锥实心层圆干（每层半径 {@code round(tan(theta)·(H-i))}，底径 ≈ 2 收尖到 0）+
     * 顶部 3 轮轮枝（4 正向横臂 + 臂端叶团）+ 冠顶 6 层盘收尖。
     * isCubeClear(4, h) 大净空预检，失败整树跳过。
     */
    private static boolean redwood(World world, StructureBuilder builder, Random rand, int chunkX, int chunkZ,
        Block log, Block leaves) {
        final int x = (chunkX << 4) + MARGIN_CANOPY + rand.nextInt(16 - 2 * MARGIN_CANOPY);
        final int z = (chunkZ << 4) + MARGIN_CANOPY + rand.nextInt(16 - 2 * MARGIN_CANOPY);
        final int y0 = ProsperityDecorPlacer.naturalTopAt(world, x, z);
        if (y0 < 0) {
            return false;
        }
        final int h = 28 + rand.nextInt(11);
        final int topY = y0 + h;
        if (topY + 6 > 255) {
            return false;
        }
        if (!isCubeClear(world, x, z, y0 + 1, 4, h)) {
            return false; // 大净空预检失败 ⇒ 跳过不硬写
        }
        final double taper = Math.tan(Math.atan(REDWOOD_TAPER / h));
        // 冠顶 6 层盘收尖（5,4,3,2,1 + 十字）
        ProsperityDecorPlacer.placeLeafDisc(builder, world, x, topY + 1, z, 5, leaves);
        ProsperityDecorPlacer.placeLeafDisc(builder, world, x, topY + 2, z, 4, leaves);
        ProsperityDecorPlacer.placeLeafDisc(builder, world, x, topY + 3, z, 3, leaves);
        ProsperityDecorPlacer.placeLeafDisc(builder, world, x, topY + 4, z, 2, leaves);
        ProsperityDecorPlacer.placeLeafDisc(builder, world, x, topY + 5, z, 1, leaves);
        ProsperityDecorPlacer.placeCrossTop(builder, world, x, topY + 6, z, leaves);
        // 顶部轮枝：3 轮 × 4 正向横臂（顶轮臂长 4，下两轮 3），臂端叶团
        for (int w = 0; w < 3; w++) {
            final int wy = topY - 2 - w * 4;
            if (wy - y0 < 10) {
                break;
            }
            final int arm = 4 - w;
            for (int d = 0; d < 4; d++) {
                final int dx = CARDINALS[d][0];
                final int dz = CARDINALS[d][1];
                for (int k = 1; k <= arm; k++) {
                    builder.setBlock(x + dx * k, wy, z + dz * k, log, 0, BlockSink.FLAG_POPULATE);
                }
                final int ex = x + dx * arm;
                final int ez = z + dz * arm;
                ProsperityDecorPlacer.placeLeafDisc(builder, world, ex, wy, ez, 2, leaves);
                ProsperityDecorPlacer.placeLeafDisc(builder, world, ex, wy + 1, ez, 1, leaves);
                ProsperityDecorPlacer.placeLeafDisc(builder, world, ex, wy - 1, ez, 1, leaves);
            }
        }
        // 圆锥实心干（层圆：dx²+dz² ≤ r²+r 的近似圆盘；i=h 时 r=0 即干顶尖格）
        for (int i = 1; i <= h; i++) {
            final int r = (int) Math.round(taper * (h - i));
            if (r <= 0) {
                builder.setBlock(x, y0 + i, z, log, 0, BlockSink.FLAG_POPULATE);
                continue;
            }
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= r * r + r) {
                        builder.setBlock(x + dx, y0 + i, z + dz, log, 0, BlockSink.FLAG_POPULATE);
                    }
                }
            }
        }
        return true;
    }

    // ═══════════════════════════ ③ Bayou 式（沼泽/sanzu，干 20-26）═══════════════════════════

    /**
     * 板根干：底部 4 层 2×2+十字展开（{@code |dx|,|dz|≤2} 的加臂方形），其上单干到 h；
     * 顶部平展伞冠（r6 大平顶 + r4 次层 + 顶小盘 + 四角下垂，marsh 冠形放大）。
     * 落地：自然 top 列直接落；水下列（{@code heightAt < SEA_LEVEL}）且 ±2 邻列有自然 top
     * （水缘）⇒ 干自 y={@code SEA_LEVEL}=68 起（"可落在水面 y=68 边缘"）。
     */
    private static boolean bayou(World world, long worldSeed, StructureBuilder builder, Random rand, int chunkX,
        int chunkZ, Block log, Block leaves) {
        final int x = (chunkX << 4) + MARGIN_CANOPY + rand.nextInt(16 - 2 * MARGIN_CANOPY);
        final int z = (chunkZ << 4) + MARGIN_CANOPY + rand.nextInt(16 - 2 * MARGIN_CANOPY);
        int y0 = ProsperityDecorPlacer.naturalTopAt(world, x, z);
        if (y0 < 0) {
            // 水缘档：列在水下 + ±2 四正邻列至少一列有自然 top（邻列探测不出 chunk，见 MARGIN_CANOPY）
            if (ProsperityTerrainProfile.heightAt(worldSeed, x, z) >= ProsperityTerrainProfile.SEA_LEVEL) {
                return false;
            }
            if (ProsperityDecorPlacer.naturalTopAt(world, x + BAYOU_EDGE_PROBE, z) < 0
                && ProsperityDecorPlacer.naturalTopAt(world, x - BAYOU_EDGE_PROBE, z) < 0
                && ProsperityDecorPlacer.naturalTopAt(world, x, z + BAYOU_EDGE_PROBE) < 0
                && ProsperityDecorPlacer.naturalTopAt(world, x, z - BAYOU_EDGE_PROBE) < 0) {
                return false;
            }
            y0 = ProsperityTerrainProfile.SEA_LEVEL - 1;
        }
        final int h = 20 + rand.nextInt(7);
        final int base = y0 + 1;
        final int topY = base + h - 1;
        if (topY + 3 > 255) {
            return false;
        }
        // 主干柱空气门（T7 放宽口径：板根段之上、干顶之下整段主干列须空气；冠/顶部不设整域门）
        for (int i = 4; i < h; i++) {
            if (!world.isAirBlock(x, base + i, z)) {
                return false;
            }
        }
        // 平展伞冠（marsh 冠形放大：大平顶 + 次层 + 顶小盘 + 四角下垂）
        ProsperityDecorPlacer.placeLeafDisc(builder, world, x, topY + 1, z, 6, leaves);
        ProsperityDecorPlacer.placeLeafDisc(builder, world, x, topY + 2, z, 4, leaves);
        ProsperityDecorPlacer.placeLeafDisc(builder, world, x, topY + 3, z, 1, leaves);
        for (int d = -1; d <= 1; d += 2) {
            for (int e = -1; e <= 1; e += 2) {
                ProsperityDecorPlacer.placeLeafIfAir(builder, world, x + d * 6, topY, z + e * 5, leaves);
                ProsperityDecorPlacer.placeLeafIfAir(builder, world, x + d * 5, topY, z + e * 6, leaves);
                if (rand.nextInt(3) == 0) {
                    ProsperityDecorPlacer.placeLeafIfAir(builder, world, x + d * 6, topY - 1, z + e * 5, leaves);
                }
            }
        }
        // 板根（底 4 层 2×2 + 十字；只写空气/水位）
        for (int i = 0; i < 4; i++) {
            final int y = base + i;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if ((Math.abs(dx) <= 1 && Math.abs(dz) <= 1) || dx == 0 || dz == 0) {
                        placeLogSoft(builder, world, x + dx, y, z + dz, log);
                    }
                }
            }
        }
        // 单干段
        for (int i = 4; i < h; i++) {
            builder.setBlock(x, base + i, z, log, 0, BlockSink.FLAG_POPULATE);
        }
        return true;
    }

    /** 板根/干辅助落块：只写空气或水位（不切地形、不切结构）。 */
    private static void placeLogSoft(StructureBuilder builder, World world, int x, int y, int z, Block log) {
        if (!world.isAirBlock(x, y, z) && world.getBlock(x, y, z) != Blocks.water) {
            return;
        }
        builder.setBlock(x, y, z, log, 0, BlockSink.FLAG_POPULATE);
    }

    /** 大净空预检：以 (x,z) 为心、半宽 {@code half}、高 {@code height}（自 y1 起）的立方体全空气。 */
    private static boolean isCubeClear(World world, int x, int z, int y1, int half, int height) {
        for (int dy = 0; dy < height; dy++) {
            final int y = y1 + dy;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    if (!world.isAirBlock(x + dx, y, z + dz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
