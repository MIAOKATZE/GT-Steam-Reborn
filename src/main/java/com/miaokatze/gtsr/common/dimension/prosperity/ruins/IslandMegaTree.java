package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.Arrays;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;

/**
 * 垂天玄柯<b>岛心巨树形态</b>（P22 版 B · S3；方块 = 已注册 {@code BlocksGTSR.prosperityZenithLog}
 * / {@code prosperityJadeLeaves}，复用既有类，无方块侧工作）。
 * <p>
 * <b>形态</b>（p21 §4「巨湖中心岛固定生成」）：干 5×5（顶部两段收窄到 3×3、1×1）× 干高 66..74
 * （≈70）+ 3 轮巨枝（每轮 5 臂，BOP 红杉范式：{@link #BRANCH_SLOPE}=0.381 逐格爬升、
 * {@link #HEIGHT_ATTENUATION}=0.618 上层轮收短、臂端叶团）+ 冠壳 = 以干顶为心的<b>逐层环带盘</b>
 * （每层只写半径 r−2..r 的外壳带 + 外沿<b>缺角抖动</b>，<b>禁实心球</b>）。
 * 分支算法只取 BOP 的参数范式（衰减/斜率/截短三件），无逐字复制。
 * <p>
 * <b>确定性契约（跨 chunk 单射的前提）</b>：整棵树的形态只由 {@code (treeRand 种子, ax, az, y0)}
 * 决定——四者对每个被跨 chunk 都相同（锚点纯函数 + 树 rand 以<b>锚点槽</b>派生，见
 * {@code ProsperityDecorPlacer.placeIslandTreePass}），且 {@code treeRand} 的取数点全部在
 * 无 World 分支的固定序上（干高 → 缺角盐 → 3 轮相位），World 读只出现在逐格叶门里、不参与
 * 形态决策（让行口径 = {@code placeLeafIfAir} 同款 owner-local，被让掉的格<b>不消耗</b> rand）。
 * ⇒ 任何 chunk 重算得<b>同一棵</b>树，各写各的 {@code ChunkSliceSink} owns 片。
 * <p>
 * <b>写格纪律</b>：干/枝<b>无条件写</b>（穿水段 y≤{@code SEA_LEVEL}−1 由木取代水，p21 A3 口径
 * ——水回填在 onPopulate、装饰在 GameRegistry.generateWorld 之后，见 ChunkProviderProsperityRuins
 * onPopulate 注释链序）；冠壳叶走 {@link ProsperityDecorPlacer#placeLeafIfAir}（只覆写
 * air/草/雪，绝不切结构/地形/水体）。{@code y0 + 干高 + 冠超出 > 255} 整树早退（照
 * {@code MegaTreeForms} 的 255 红线，不硬写）。
 * <p>
 * <b>可见性</b>：public 供离线判据 {@code tools/dim1/MegaTreeCheck} 直调同一形态重放
 * （"单世界一次性写入"对拍臂；工具不自算第二份判定，先例 {@code RuinedMachinePlacer.spanReadyAt}
 * 的重放口纪律）；生产侧唯一调用者是 {@code ProsperityDecorPlacer.placeIslandTreePass}。
 */
public final class IslandMegaTree {

    /** 干高下限（{@code TRUNK_HEIGHT_MIN + nextInt(9)} ⇒ 66..74，均值 70）。 */
    static final int TRUNK_HEIGHT_MIN = 66;
    /** 干高掷骰跨度。 */
    static final int TRUNK_HEIGHT_SPAN = 9;
    /** 干 5×5 段的高度上界（自顶向下 12 格起收窄到 3×3、5 格起 1×1）。 */
    static final int TRUNK_FULL_BELOW_TOP = 12;
    static final int TRUNK_MID_BELOW_TOP = 5;
    /** 冠心相对干顶的回撤（冠壳竖向半径 {@link #CROWN_HALF_HEIGHT} ⇒ 冠顶 = y0+干高−{@code 本值}+18）。 */
    static final int CROWN_CENTER_INSET = 10;
    /** 冠壳竖向半高。 */
    static final int CROWN_HALF_HEIGHT = 18;
    /** 冠壳单层外壳带厚度（内缘 = r−2 ⇒ 逐层 2 格厚环带盘，非实心；预算实测带单树 ≤9500/单 chunk ≤3600 的主旋钮）。 */
    static final int CROWN_SHELL_BAND = 2;
    /** 薄层阈值：r &lt; 本值的层整盘写（环带带不出壳的薄帽层）。 */
    static final int CROWN_THIN_R = 4;
    /** 缺角抖动：外沿一格环带按确定性哈希跳 ~2/8（缺角，不是整圈平滑边）。 */
    static final int NOTCH_MASK = 8;
    static final int NOTCH_SKIP = 2;
    /** BOP 红杉范式：枝逐格爬升率（branchSlope 量级）。 */
    static final double BRANCH_SLOPE = 0.381D;
    /** BOP 红杉范式：上层轮臂长衰减（heightAttenuation 量级，0.618^轮序）。 */
    static final double HEIGHT_ATTENUATION = 0.618D;
    /** 每轮臂数 / 轮数。 */
    static final int BRANCHES_PER_RING = 5;
    static final int RING_COUNT = 3;
    /** 底轮臂长（11 格；上两轮按衰减收短，下限 4）。 */
    static final int RING_ARM_BASE = 11;
    static final int RING_ARM_MIN = 4;
    /** 三轮的干高相位（0.45/0.60/0.75 × 干高，自底向上）。 */
    static final double[] RING_HEIGHT_FRACTION = { 0.45D, 0.60D, 0.75D };
    /** 臂端叶团半径（主盘 + 上一层小盘）。 */
    static final int ARM_TUFT_R = 2;

    /**
     * 冠底查询表边长（2×{@link MegaTreeAnchors#CANOPY_RADIUS}+1 = 31；public = 消费方
     * {@code ProsperityLumenPlacer} 与判据按同一 footprint 分配表，防两处各写一个 31）。
     */
    public static final int CROWN_QUERY_SIDE = 2 * MegaTreeAnchors.CANOPY_RADIUS + 1;

    private IslandMegaTree() {}

    /**
     * 把一棵垂天玄柯写进 {@code builder}（调用方负责套 {@code ChunkSliceSink}——本方法对坐标
     * 不做任何 owns 过滤，跨界由切片层单射）。{@code treeRand} 必须由锚点槽派生（跨 chunk 同种子），
     * 见类注释确定性契约。public = 离线判据重放口（见类注释可见性段）。
     *
     * @return 是否落树（{@code y0+干高+冠超出 &gt; 255} 时整树早退返回 false，零写入）
     */
    public static boolean placeInto(World world, StructureBuilder builder, Random treeRand, int ax, int az, int y0) {
        final int trunkH = TRUNK_HEIGHT_MIN + treeRand.nextInt(TRUNK_HEIGHT_SPAN);
        final int crownTop = y0 + trunkH - CROWN_CENTER_INSET + CROWN_HALF_HEIGHT;
        if (crownTop > 255) {
            return false; // 255 红线：整树早退，不硬写（照 MegaTreeForms :93）
        }
        final int notchSalt = treeRand.nextInt();
        final double[] ringPhase = new double[RING_COUNT];
        for (int k = 0; k < RING_COUNT; k++) {
            ringPhase[k] = treeRand.nextDouble() * 2.0D * Math.PI;
        }
        final Block log = BlocksGTSR.prosperityZenithLog;
        final Block leaves = BlocksGTSR.prosperityJadeLeaves;
        crownShell(world, builder, ax, az, y0, trunkH, notchSalt, leaves);
        branchRings(builder, ax, az, y0, trunkH, ringPhase, world, leaves);
        trunk(builder, ax, az, y0, trunkH, log);
        return true;
    }

    /** 主干：底部 5×5，自顶向下 12 格起 3×3、5 格起 1×1；无条件写（穿水段由木取代水，p21 A3）。 */
    private static void trunk(StructureBuilder builder, int ax, int az, int y0, int trunkH, Block log) {
        for (int i = 1; i <= trunkH; i++) {
            final int half = i <= trunkH - TRUNK_FULL_BELOW_TOP ? 2 : (i <= trunkH - TRUNK_MID_BELOW_TOP ? 1 : 0);
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    builder.setBlock(ax + dx, y0 + i, az + dz, log, 0, BlockSink.FLAG_POPULATE);
                }
            }
        }
    }

    /**
     * 3 轮巨枝（BOP 红杉范式）：轮 k 自干高相位 {@link #RING_HEIGHT_FRACTION} 起枝，5 臂按
     * 72° 均分 + 轮相位；臂长 = {@link #RING_ARM_BASE}×0.618^k（下限 4）；每水平步升
     * {@link #BRANCH_SLOPE} 格。<b>截短</b>（遇障范式的本域退化）：枝头一旦超过干顶−2 即停
     * （岛面上方由锚点平台保证净空，域内唯一硬障碍是自己的干顶收窄段——界内截短，不硬伸）。
     * 臂端小叶团（主盘 r2 + 上一层 r1）补足低两轮不被冠壳覆盖的绿量。
     */
    private static void branchRings(StructureBuilder builder, int ax, int az, int y0, int trunkH, double[] ringPhase,
        World world, Block leaves) {
        final Block log = BlocksGTSR.prosperityZenithLog;
        final int capY = y0 + trunkH - 2;
        for (int k = 0; k < RING_COUNT; k++) {
            final int ringY = y0 + 1 + (int) Math.round(trunkH * RING_HEIGHT_FRACTION[k]);
            final int arm = Math.max(RING_ARM_MIN, (int) Math.round(RING_ARM_BASE * Math.pow(HEIGHT_ATTENUATION, k)));
            for (int b = 0; b < BRANCHES_PER_RING; b++) {
                final double angle = ringPhase[k] + b * 2.0D * Math.PI / BRANCHES_PER_RING;
                final double cosA = Math.cos(angle);
                final double sinA = Math.sin(angle);
                int tipX = ax;
                int tipZ = az;
                int tipY = ringY;
                for (int s = 1; s <= arm; s++) {
                    final int bx = ax + (int) Math.round(cosA * s);
                    final int bz = az + (int) Math.round(sinA * s);
                    final int by = ringY + (int) Math.round(BRANCH_SLOPE * s);
                    if (by > capY) {
                        break; // 截短：不越干顶收窄段
                    }
                    builder.setBlock(bx, by, bz, log, 0, BlockSink.FLAG_POPULATE);
                    tipX = bx;
                    tipZ = bz;
                    tipY = by;
                }
                ProsperityDecorPlacer.placeLeafDisc(builder, world, tipX, tipY + 1, tipZ, ARM_TUFT_R, leaves);
                ProsperityDecorPlacer.placeLeafDisc(builder, world, tipX, tipY + 2, tipZ, ARM_TUFT_R - 1, leaves);
            }
        }
    }

    /**
     * 冠壳：以 (ax, y0+trunkH−{@link #CROWN_CENTER_INSET}, az) 为心、竖半高 {@link #CROWN_HALF_HEIGHT}
     * 的逐层盘；每层半径 {@code round(R·sqrt(1−(dy/H)²))}，只写 {@code r−3..r} 的<b>外壳带</b>
     * （薄帽层整盘），外沿一格按 {@code notchSalt} 混入的确定性哈希缺角（~2/8 跳过）。
     * <b>禁实心球</b>：任一 r ≥ {@link #CROWN_THIN_R} 的层，内缘以内一律不写。
     * 全部落块经 {@link ProsperityDecorPlacer#placeLeafIfAir}（owner-local 让行，不耗 rand）。
     * <b>P22-B S4 起，全部形态判定上收到 {@link #forEachCrownCell} 单一枚举源</b>（本方法只剩
     * "枚举 → 逐格写叶"一步；迭代序与收上之前逐格相同 ⇒ 写序列零变化，MegaTreeCheck D 组双跑序列
     * 断言不动即绿）。
     */
    private static void crownShell(World world, StructureBuilder builder, int ax, int az, int y0, int trunkH,
        int notchSalt, Block leaves) {
        final int cy = y0 + trunkH - CROWN_CENTER_INSET;
        forEachCrownCell(
            trunkH,
            notchSalt,
            (dx, dy, dz) -> {
                ProsperityDecorPlacer.placeLeafIfAir(builder, world, ax + dx, cy + dy, az + dz, leaves);
            });
    }

    /** 冠壳格访问器：dx/dz 相对锚点列、dy 相对冠心层（写路径与查询路径共用的最小回调面）。 */
    private interface CrownCellVisitor {

        void visit(int dx, int dy, int dz);
    }

    /**
     * 冠壳形态<b>单一枚举源</b>（P22-B S4 抽出）：逐层盘半径、外壳带剪形、缺角抖动三条判定
     * <b>一字不动</b>地从原 {@code crownShell} 内联体上收至此——写路径（冠壳落叶）与只读查询
     * （{@link #crownUnderside} 冠底带）走<b>同一份</b>枚举，形态不可能长出第二副真值
     * （p21 §4 消费方纪律："禁止在 LumenPlacer 里复写一套冠形态公式"的落点）。
     */
    private static void forEachCrownCell(int trunkH, int notchSalt, CrownCellVisitor visitor) {
        final double radius = MegaTreeAnchors.CANOPY_RADIUS;
        for (int dy = -CROWN_HALF_HEIGHT; dy <= CROWN_HALF_HEIGHT; dy++) {
            final int r = (int) Math.round(
                radius * Math
                    .sqrt(Math.max(0.0D, 1.0D - (double) dy * dy / ((double) CROWN_HALF_HEIGHT * CROWN_HALF_HEIGHT))));
            if (r <= 0) {
                visitor.visit(0, dy, 0);
                continue;
            }
            final int inner = Math.max(0, r - CROWN_SHELL_BAND);
            final int r2 = r * r + r;
            final int innerSq = inner * inner;
            final int notchSq = (r - 1) * (r - 1);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    final int d2 = dx * dx + dz * dz;
                    if (d2 > r2 || (r >= CROWN_THIN_R && d2 < innerSq)) {
                        continue; // 圆盘界外 / 壳内空腔（禁实心）
                    }
                    if (d2 > notchSq
                        && ((notchSalt + dx * 7349 + dz * 911 + dy * 293) & (NOTCH_MASK - 1)) < NOTCH_SKIP) {
                        continue; // 缺角抖动：外沿带确定性跳格
                    }
                    visitor.visit(dx, dy, dz);
                }
            }
        }
    }

    /**
     * 冠底列查询（P22-B S4 <b>只读枚举口</b>，消费方 = {@code ProsperityLumenPlacer} 冠下光点趟）：
     * 以与 {@link #placeInto} <b>完全相同的 rand 消费前缀</b>（干高 → 255 早退 → 缺角盐）重放冠形态，
     * 再走 {@link #forEachCrownCell} 同一枚举，给出每个冠 footprint 列（dx,dz ∈
     * [−{@link MegaTreeAnchors#CANOPY_RADIUS}, +{@link MegaTreeAnchors#CANOPY_RADIUS}]）的<b>最低
     * 冠壳层 dy</b>（冠底带；三轮枝相位不在前缀内——枝不参与冠壳，见 {@link #branchRings}）。
     * <b>零 World、零写入</b>，{@code placeInto} 行为零牵连；重放口径 = 形态格集（缺角跳格后的
     * 尝试集），不是世界让行后的实写集——让行是逐格叶门的职责，不进形态查询。
     *
     * @param out 长度 ≥ {@link #CROWN_QUERY_SIDE}²；无冠壳格的列填 {@code Integer.MIN_VALUE} 哨兵
     * @return 干高 trunkH（{@code y0+干高−冠回撤+竖半高 > 255} 整树早退 ⇒ 返回 −1，与 placeInto 同判）
     */
    public static int crownUnderside(Random treeRand, int y0, int[] out) {
        final int trunkH = TRUNK_HEIGHT_MIN + treeRand.nextInt(TRUNK_HEIGHT_SPAN);
        final int crownTop = y0 + trunkH - CROWN_CENTER_INSET + CROWN_HALF_HEIGHT;
        if (crownTop > 255) {
            return -1; // 255 红线：整树不落 ⇒ 冠下无光点（与 placeInto 同判）
        }
        final int notchSalt = treeRand.nextInt();
        Arrays.fill(out, Integer.MIN_VALUE);
        forEachCrownCell(trunkH, notchSalt, (dx, dy, dz) -> {
            final int idx = (dx + MegaTreeAnchors.CANOPY_RADIUS) * CROWN_QUERY_SIDE
                + (dz + MegaTreeAnchors.CANOPY_RADIUS);
            if (out[idx] == Integer.MIN_VALUE || dy < out[idx]) {
                out[idx] = dy;
            }
        });
        return trunkH;
    }
}
