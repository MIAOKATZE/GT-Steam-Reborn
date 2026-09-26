package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.Arrays;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/**
 * 垂天玄柯<b>岛心巨树形态</b>（P26-B5「垂帘擎天」放大档彻底重设计；方块 = 已注册
 * {@code BlocksGTSR.prosperityZenithLog} / {@code prosperityJadeLeaves}，复用既有类，无方块侧工作）。
 * <p>
 * <b>形态（D5' 放大档）</b>：五件套——
 * <ol>
 * <li><b>连续幂收分巨柱干</b>：r(i) = 1.5 + 5.0·((H−i)/H)^0.85，底 r6.5 圆盘（盘测试 d²≤r²+r，
 * 同 {@code MegaTreeForms} redwood 范式）连续收分到顶 r1.5，<b>禁分段台阶</b>；干高 133..146 ⇒
 * 岛面上总高（= trunkH − {@link #CROWN_CENTER_INSET} + {@link #CROWN_HALF_HEIGHT}）165..178，
 * y0 带 [71,73] ⇒ crownTop ≤ 251（255 余量 ≥4）；干北侧 2×2 树洞（y0+8..y0+28 一段
 * <b>选择不写</b>，「干无条件写」语义不受影响——洞=不写，不是写洞）；</li>
 * <li><b>抱岛鳍根</b>：9..11 片径向鳍墙，自干底外伸 16..23 格、鳍高 12..16 随距离线性归零，
 * 贴岛坡的基线自 y0 线性降至水线 {@code SEA_LEVEL}=68（鳍格最低 y ≥ 69，<b>不触水体</b>，
 * 干穿水的 p21 A3 口径不经根扩展）；楔形缺口留在鳍间；</li>
 * <li><b>递归三级分枝</b>（取代旧 4 轮×5 臂单级轮枝）：一级 7 枝着枝相位 0.42/0.58/0.74 干高
 * （2/3/2 分布），臂长 38..42，<b>抛物线弧升</b>（初坡 0.34 线性衰减到端坡 −0.05，端部近水平微垂）；
 * 二级每母枝 55..70% 处 2..3 叉、长 0.42×母枝，<b>重力双角并存</b>（外侧顶端正扬 +0.45 /
 * 内侧重力下垂 −0.10）；三级 = 臂端叶团链（二级端 r3→r2→r1 三盘、一级端 r2→r1 两盘）；</li>
 * <li><b>梯度带厚椭球冠壳</b>：r64 × 竖半高 40，|dy| ≤ 19 带 2、20..40 带 1（冠中厚、上下薄的
 * 密度梯度），外沿缺角抖动沿用（外沿 1 格 2/8 跳，机制字面不动），<b>禁实心球</b>不变；</li>
 * <li><b>冠缘垂帘</b>：12..16 条 1×1 叶柱，槽位 = 黄金角 137.5° 步进 + 盐抖动，落位冠缘半径带
 * 56..62，自该列冠壳底层垂下 35..60 格，<b>最低端 y ≥ {@link #DROOP_MIN_TIP_Y}=102</b>
 * （形态级硬钳：垂帘并入冠形态枚举 ⇒ 冠下光位 = 帘底−1 ≥ 101 &gt; 判据 Y_FLOOR=100）。</li>
 * </ol>
 * 枯枝点缀：干中下部 4..6 段哈希门控裸木刺（长 5..9，无叶团）。
 * <p>
 * <b>确定性契约（跨 chunk 单射的前提，重设计前后一字不差）</b>：整棵树的形态只由
 * {@code (treeRand 种子, ax, az, y0)} 决定——四者对每个被跨 chunk 都相同（锚点纯函数 + 树 rand
 * 以<b>锚点槽</b>派生，见 {@code ProsperityDecorPlacer.placeIslandTreePass}），且 {@code treeRand}
 * 的取数点全部在无 World 分支的固定序上：<b>干高 → 255 门 → 缺角盐 → 垂帘盐</b>（垂帘盐 =
 * 本档唯一新增 nextInt；其余新形态参数——鳍根数/展/高、枝长/叉位/叉角、垂帘槽位/帘长、枯桩位——
 * 全部走「盐混入序号哈希」（缺角抖动范式泛化，{@link #mix}），零新增取数点），World 读只出现在
 * 逐格叶门里、不参与形态决策（让行口径 = {@code placeLeafIfAir} 同款 owner-local，被让掉的格
 * <b>不消耗</b> rand）。⇒ 任何 chunk 重算得<b>同一棵</b>树，各写各的 {@code ChunkSliceSink} owns 片。
 * <p>
 * <b>写格纪律</b>：干/枝/鳍根/枯桩<b>无条件写</b>（穿水段 y≤{@code SEA_LEVEL}−1 由木取代水，
 * p21 A3 口径——本档干自 y0+1 起写、鳍根最低 y=69，实际穿水只发生在判据合成的低锚臂）；
 * 冠壳叶/垂帘/叶团走 {@link ProsperityDecorPlacer#placeLeafIfAir}（只覆写 air/草/雪，绝不切
 * 结构/地形/水体；全部叶类格 y ≥ 102 ⇒ 写集与空世界逐格相同）。{@code y0 + 干高 − 冠回撤 +
 * 冠竖半高 > 255} 整树早退（照 {@code MegaTreeForms} 的 255 红线，不硬写）。
 * <p>
 * <b>单一枚举源</b>：冠形态（壳层 + 垂帘）全部由 {@link #forEachCrownCell} 唯一枚举（写路径与
 * {@link #crownUnderside} 查询共用，逐层壳判定收在 {@link #shellCellAt} 一处），LumenPlacer 只
 * 消费不复写。public 面 = 离线判据 {@code tools/dim1/MegaTreeCheck} 直调同一形态重放
 * （"单世界一次性写入"对拍臂）；生产侧唯一调用者是 {@code ProsperityDecorPlacer.placeIslandTreePass}。
 */
public final class IslandMegaTree {

    /** 干高下限（{@code TRUNK_HEIGHT_MIN + nextInt(14)} ⇒ 133..146，均值 139.5；岛面上总高 165..178）。 */
    static final int TRUNK_HEIGHT_MIN = 133;
    /** 干高掷骰跨度。 */
    static final int TRUNK_HEIGHT_SPAN = 14;
    /** 连续幂收分：干半径 r(i) = R_TOP + (R_BASE−R_TOP)·((H−i)/H)^POW，底 r6.5 → 顶 r1.5（禁分段台阶）。 */
    static final double TRUNK_R_BASE = 6.5D;
    static final double TRUNK_R_TOP = 1.5D;
    static final double TRUNK_TAPER_POW = 0.85D;
    /** 干北侧 2×2 树洞的干高区间（i ∈ [8,28]，相对 y0；洞 = 选择不写，非「写洞」）。 */
    static final int TRUNK_HOLE_Y_MIN = 8;
    static final int TRUNK_HOLE_Y_MAX = 28;
    /** 冠心相对干顶的回撤（冠顶 = y0+干高−{@code 本值}+{@link #CROWN_HALF_HEIGHT}）。 */
    static final int CROWN_CENTER_INSET = 8;
    /** 冠壳竖向半高（r64 × 半高 40 的扁椭球壳）。 */
    static final int CROWN_HALF_HEIGHT = 40;
    /** 冠壳带厚梯度：|dy| ≤ {@link #CROWN_BAND_THICK_MAX_ABS_DY} 用厚带，其余用薄带（密度梯度旋钮）。 */
    static final int CROWN_SHELL_BAND_THICK = 2;
    static final int CROWN_SHELL_BAND_THIN = 1;
    static final int CROWN_BAND_THICK_MAX_ABS_DY = 19;
    /** 薄层阈值：r &lt; 本值的层整盘写（环带带不出壳的薄帽层）。 */
    static final int CROWN_THIN_R = 4;
    /** 缺角抖动：外沿一格环带按确定性哈希跳 ~2/8（缺角，不是整圈平滑边；机制字面自 P22 不动）。 */
    static final int NOTCH_MASK = 8;
    static final int NOTCH_SKIP = 2;
    /** 一级枝总数与三档干高相位（2/3/2 分布；相位 × 干高 = 着枝高度）。 */
    static final double[] TIER1_PHASE = { 0.42D, 0.58D, 0.74D };
    static final int[] TIER1_PER_PHASE = { 2, 3, 2 };
    /** 一级臂长 38..42（系统绿端 = 臂 + 0.42×臂 + 叶团 r3 ≈ 61 ≤ 冠半径−3，A 组 bbox 界内）。 */
    static final int TIER1_ARM_MIN = 38;
    static final int TIER1_ARM_SPAN = 5;
    /** 一级抛物线弧升：初坡 0.34 线性衰减到端坡 −0.05（端部近水平微垂——重力真实感）。 */
    static final double TIER1_SLOPE_START = 0.34D;
    static final double TIER1_SLOPE_END = -0.05D;
    /** 二级叉：每母枝 2..3 叉、位于母臂 55..70% 处、长 0.42×母臂；外扬/内垂双角并存。 */
    static final int TIER2_MIN = 2;
    static final int TIER2_SPAN = 2;
    static final double TIER2_FORK_LO = 0.55D;
    static final double TIER2_FORK_HI = 0.70D;
    static final double TIER2_LEN_RATIO = 0.42D;
    static final double TIER2_SLOPE_OUT = 0.45D;
    static final double TIER2_SLOPE_IN = -0.10D;
    /** 三级叶团链基半径（二级端 r3→r2→r1 三盘；一级端 r2→r1 两盘）。 */
    static final int ARM_TUFT_R = 3;
    /** 枝系（含枯桩）木格的径向硬界（叶团可再外伸 ARM_TUFT_R ⇒ 最远 61 &lt; CANOPY_RADIUS，界内截短）。 */
    static final int BRANCH_REACH_SQ = 58 * 58;
    /** 鳍根：9..11 片 / 外伸 16..23 / 鳍高 12..16（随干高同比放大档）。 */
    static final int ROOT_FIN_MIN = 9;
    static final int ROOT_FIN_SPAN = 3;
    static final int ROOT_REACH_MIN = 16;
    static final int ROOT_REACH_SPAN = 8;
    static final int ROOT_FIN_H_MIN = 12;
    static final int ROOT_FIN_H_SPAN = 5;
    /** 垂帘：12..16 槽 / 帘长 35..60 / 最低端 y ≥ 102（LumenLightCheck Y_FLOOR=100 的形态级保证）。 */
    static final int DROOP_SLOT_MIN = 12;
    static final int DROOP_SLOT_SPAN = 5;
    static final int DROOP_LEN_MIN = 35;
    static final int DROOP_LEN_SPAN = 26;
    static final int DROOP_MIN_TIP_Y = 102;
    /** 黄金角步进（137.5° = 2.39996 rad；垂帘槽位与一级枝方位共用该步进防对齐）。 */
    static final double GOLDEN_ANGLE = 2.399963D;
    /** 垂帘槽位半径带下界（上界 = CANOPY_RADIUS−2，即冠缘外圈 7 格带）。 */
    static final int DROOP_R_INNER = MegaTreeAnchors.CANOPY_RADIUS - 8;
    /** 枯桩：4..6 段 / 长 5..9（裸木无叶，哈希门控）。 */
    static final int SNAG_MIN = 4;
    static final int SNAG_SPAN = 3;
    static final int SNAG_LEN_MIN = 5;
    static final int SNAG_LEN_SPAN = 5;

    /**
     * 冠底查询表边长（2×{@link MegaTreeAnchors#CANOPY_RADIUS}+1 = 129；public = 消费方
     * {@code ProsperityLumenPlacer} 与判据按同一 footprint 分配表，防两处各写一个 129）。
     */
    public static final int CROWN_QUERY_SIDE = 2 * MegaTreeAnchors.CANOPY_RADIUS + 1;

    private IslandMegaTree() {}

    /**
     * 把一棵垂天玄柯写进 {@code builder}（调用方负责套 {@code ChunkSliceSink}——本方法对坐标
     * 不做任何 owns 过滤，跨界由切片层单射）。{@code treeRand} 必须由锚点槽派生（跨 chunk 同种子），
     * 见类注释确定性契约。public = 离线判据重放口（见类注释）。
     * <p>
     * 取数序（固定，先于一切 World 读）：干高 → 255 门 → 缺角盐 → 垂帘盐；写序：冠壳（含垂帘列）
     * → 鳍根 → 三级枝 → 干（含树洞让写）→ 枯桩。
     *
     * @return 是否落树（{@code y0+干高−冠回撤+冠竖半高 > 255} 时整树早退返回 false，零写入）
     */
    public static boolean placeInto(World world, StructureBuilder builder, Random treeRand, int ax, int az, int y0) {
        final int trunkH = TRUNK_HEIGHT_MIN + treeRand.nextInt(TRUNK_HEIGHT_SPAN);
        final int crownTop = y0 + trunkH - CROWN_CENTER_INSET + CROWN_HALF_HEIGHT;
        if (crownTop > 255) {
            return false; // 255 红线：整树早退，不硬写（照 MegaTreeForms :93）
        }
        final int notchSalt = treeRand.nextInt();
        final int droopSalt = treeRand.nextInt(); // 垂帘盐：本档唯一新增 nextInt（D5' 枚举序）
        final Block log = BlocksGTSR.prosperityZenithLog;
        final Block leaves = BlocksGTSR.prosperityJadeLeaves;
        crownShell(world, builder, ax, az, y0, trunkH, notchSalt, droopSalt, leaves);
        rootFins(builder, ax, az, y0, notchSalt, log);
        tierBranches(builder, world, ax, az, y0, trunkH, notchSalt, leaves);
        trunk(builder, ax, az, y0, trunkH, log);
        snags(builder, ax, az, y0, trunkH, notchSalt, log);
        return true;
    }

    /**
     * 盐混入序号的确定性哈希（缺角抖动范式 {@code (salt + dx*7349 + …) & mask} 的泛化）：所有
     * 新形态参数（鳍根/枝系/垂帘槽/枯桩）经它派生，<b>零 nextInt</b> ⇒ rand 前缀保持最短
     * （干高 → 255 门 → 缺角盐 → 垂帘盐），{@link #crownUnderside} 重放口径零膨胀。
     * 返回恒非负（[0, 2^31−1]），供 {@code % span} 直接取档。
     */
    static int mix(int salt, int idx) {
        int h = salt ^ (idx * 0x9E3779B9);
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h & 0x7FFFFFFF;
    }

    /**
     * 主干：连续幂收分圆盘（r(i) = 1.5 + 5.0·((H−i)/H)^0.85，盘测试 d²≤r²+r）逐层上叠；
     * 无条件写（穿水段由木取代水，p21 A3）；干北侧 2×2 树洞（i ∈ 8..28）选择不写。
     */
    private static void trunk(StructureBuilder builder, int ax, int az, int y0, int trunkH, Block log) {
        final int span = (int) Math.ceil(TRUNK_R_BASE);
        for (int i = 1; i <= trunkH; i++) {
            final double u = (double) (trunkH - i) / (double) trunkH;
            final double r = TRUNK_R_TOP + (TRUNK_R_BASE - TRUNK_R_TOP) * Math.pow(u, TRUNK_TAPER_POW);
            final int r2 = (int) Math.floor(r * r + r);
            final int bound = Math.min(span, (int) Math.ceil(Math.sqrt(r2)));
            final boolean hole = i >= TRUNK_HOLE_Y_MIN && i <= TRUNK_HOLE_Y_MAX;
            for (int dx = -bound; dx <= bound; dx++) {
                for (int dz = -bound; dz <= bound; dz++) {
                    if (dx * dx + dz * dz > r2) {
                        continue; // 圆盘界外
                    }
                    if (hole && dx >= 0 && dx <= 1 && dz >= -2 && dz <= -1) {
                        continue; // 树洞：选择不写（「干无条件写」语义不受影响）
                    }
                    builder.setBlock(ax + dx, y0 + i, az + dz, log, 0, BlockSink.FLAG_POPULATE);
                }
            }
        }
    }

    /**
     * 抱岛鳍根：每片自干心以半格步长外伸的径向鳍墙（1..2 格厚，走线连续），鳍高自 finH 随距离线性
     * 归零；基线 g(d) 自 y0 线性降到 {@code SEA_LEVEL}（贴岛坡的确定性近似——岛面平台 y0≈72 到
     * 水线 68 的岛坡主体即这段），鳍格写 g+1..g+h ⇒ 最低 y=69，<b>不触水体</b>（根取代水不发生，
     * R13 通道天然关闭）。无条件写；近干段与主干重叠由后写的 trunk 覆盖为同一 log。
     */
    private static void rootFins(StructureBuilder builder, int ax, int az, int y0, int notchSalt, Block log) {
        final int fins = ROOT_FIN_MIN + mix(notchSalt, 31) % ROOT_FIN_SPAN;
        for (int f = 0; f < fins; f++) {
            final double angle = f * 2.0D * Math.PI / fins + (mix(notchSalt, 100 + f) % 1000 - 500) * 0.0003D;
            final int reach = ROOT_REACH_MIN + mix(notchSalt, 200 + f) % ROOT_REACH_SPAN;
            final int finH = ROOT_FIN_H_MIN + mix(notchSalt, 300 + f) % ROOT_FIN_H_SPAN;
            final double cosA = Math.cos(angle);
            final double sinA = Math.sin(angle);
            for (int t = 1; t <= reach * 2; t++) {
                final double d = t * 0.5D;
                final int fx = ax + (int) Math.round(cosA * d);
                final int fz = az + (int) Math.round(sinA * d);
                final int g = y0 + (int) Math.round((ProsperityTerrainProfile.SEA_LEVEL - y0) * d / (double) reach);
                final int h = (int) Math.round(finH * (1.0D - d / (double) reach));
                for (int k = 1; k <= h; k++) {
                    builder.setBlock(fx, g + k, fz, log, 0, BlockSink.FLAG_POPULATE);
                }
            }
        }
    }

    /**
     * 递归三级分枝（取代旧 4 轮×5 臂单级）：一级 7 枝按相位 {@link #TIER1_PHASE}（2/3/2）着枝、
     * 方位 = 全局哈希旋转 + 序号×{@link #GOLDEN_ANGLE}（防对齐），臂长 {@link #TIER1_ARM_MIN}..+SPAN，
     * 逐格高度 = 抛物线弧升 {@code a·s + (b−a)·s²/(2·arm)}（a/b = {@link #TIER1_SLOPE_START/END}，
     * 端部近水平微垂）；二级每母枝 2..3 叉于母臂 55..70% 处、长 0.42×母臂，<b>重力双角</b>：
     * 偶序叉顶端正扬 +0.45、奇序叉重力下垂 −0.10；三级 = 臂端叶团链（二级端 r3→r2→r1、一级端
     * r2→r1，经 {@code placeLeafDisc} 叶门）。<b>界内截短</b>：枝头越干顶−2 即停（旧语义保留），
     * 木格径向超 {@link #BRANCH_REACH_SQ} 让格（叶团最远 61 &lt; 冠半径 ⇒ A 组 bbox 界内）。
     */
    private static void tierBranches(StructureBuilder builder, World world, int ax, int az, int y0, int trunkH,
        int notchSalt, Block leaves) {
        final Block log = BlocksGTSR.prosperityZenithLog;
        final int capY = y0 + trunkH - 2;
        final double baseRot = mix(notchSalt, 1001) / 2147483647.0D * 2.0D * Math.PI;
        int idx = 0;
        for (int p = 0; p < TIER1_PHASE.length; p++) {
            final int attachY = y0 + 1 + (int) Math.round(trunkH * TIER1_PHASE[p]);
            for (int q = 0; q < TIER1_PER_PHASE[p]; q++, idx++) {
                final double angle = baseRot + idx * GOLDEN_ANGLE;
                final int arm = TIER1_ARM_MIN + mix(notchSalt, 2000 + idx) % TIER1_ARM_SPAN;
                final double cosA = Math.cos(angle);
                final double sinA = Math.sin(angle);
                int tipX = ax;
                int tipZ = az;
                int tipY = attachY;
                for (int s = 1; s <= arm; s++) {
                    final int bx = ax + (int) Math.round(cosA * s);
                    final int bz = az + (int) Math.round(sinA * s);
                    final int by = attachY + (int) Math.round(
                        TIER1_SLOPE_START * s + (TIER1_SLOPE_END - TIER1_SLOPE_START) * (double) s * s / (2.0D * arm));
                    if (by > capY) {
                        break; // 截短：不越干顶收窄段
                    }
                    final int rx = bx - ax;
                    final int rz = bz - az;
                    if (rx * rx + rz * rz > BRANCH_REACH_SQ) {
                        continue; // 径向界内截短（叶团预留 3 格）
                    }
                    builder.setBlock(bx, by, bz, log, 0, BlockSink.FLAG_POPULATE);
                    tipX = bx;
                    tipZ = bz;
                    tipY = by;
                }
                // 二级叉：母臂 forkFrac 处起 2..3 条子臂，外扬/内垂双角
                final int n2 = TIER2_MIN + mix(notchSalt, 3000 + idx) % TIER2_SPAN;
                final double forkFrac = TIER2_FORK_LO
                    + (TIER2_FORK_HI - TIER2_FORK_LO) * (mix(notchSalt, 3100 + idx) % 1000) / 1000.0D;
                final int s2 = (int) Math.round(forkFrac * arm);
                final int px = ax + (int) Math.round(cosA * s2);
                final int pz = az + (int) Math.round(sinA * s2);
                final int py = attachY + (int) Math.round(
                    TIER1_SLOPE_START * s2 + (TIER1_SLOPE_END - TIER1_SLOPE_START) * (double) s2 * s2 / (2.0D * arm));
                final int len2 = Math.max(4, (int) Math.round(TIER2_LEN_RATIO * arm));
                for (int j = 0; j < n2; j++) {
                    final double childAngle = angle + (mix(notchSalt, 3200 + idx * 8 + j) % 1000 - 500) * 0.0009D;
                    final double slope = (j & 1) == 0 ? TIER2_SLOPE_OUT : TIER2_SLOPE_IN;
                    final double cCos = Math.cos(childAngle);
                    final double cSin = Math.sin(childAngle);
                    int endX = px;
                    int endZ = pz;
                    int endY = py;
                    for (int c = 1; c <= len2; c++) {
                        final int bx = px + (int) Math.round(cCos * c);
                        final int bz = pz + (int) Math.round(cSin * c);
                        final int by = py + (int) Math.round(slope * c);
                        if (by > capY) {
                            break; // 截短：不越干顶收窄段
                        }
                        final int rx = bx - ax;
                        final int rz = bz - az;
                        if (rx * rx + rz * rz > BRANCH_REACH_SQ) {
                            continue; // 径向界内截短
                        }
                        builder.setBlock(bx, by, bz, log, 0, BlockSink.FLAG_POPULATE);
                        endX = bx;
                        endZ = bz;
                        endY = by;
                    }
                    // 三级：叶团链 r3→r2→r1（渐小三盘）
                    ProsperityDecorPlacer.placeLeafDisc(builder, world, endX, endY + 1, endZ, ARM_TUFT_R, leaves);
                    ProsperityDecorPlacer.placeLeafDisc(builder, world, endX, endY + 2, endZ, ARM_TUFT_R - 1, leaves);
                    ProsperityDecorPlacer.placeLeafDisc(builder, world, endX, endY + 3, endZ, ARM_TUFT_R - 2, leaves);
                }
                // 一级臂端补一团小叶（r2→r1）：低相位臂不被冠壳覆盖的绿量
                ProsperityDecorPlacer.placeLeafDisc(builder, world, tipX, tipY + 1, tipZ, ARM_TUFT_R - 1, leaves);
                ProsperityDecorPlacer.placeLeafDisc(builder, world, tipX, tipY + 2, tipZ, ARM_TUFT_R - 2, leaves);
            }
        }
    }

    /**
     * 枯枝残桩：干中下部（树洞段之上、干高 1/3 之下）4..6 段裸木刺，长 5..9、远端微垂；
     * 无条件写、无叶团。纯点缀，量级 <b>0.1k 格</b>。
     */
    private static void snags(StructureBuilder builder, int ax, int az, int y0, int trunkH, int notchSalt, Block log) {
        final int count = SNAG_MIN + mix(notchSalt, 77) % SNAG_SPAN;
        final int ySpan = Math.max(1, trunkH / 3 - TRUNK_HOLE_Y_MAX - 2);
        for (int s = 0; s < count; s++) {
            final double angle = mix(notchSalt, 4000 + s) / 2147483647.0D * 2.0D * Math.PI;
            final int len = SNAG_LEN_MIN + mix(notchSalt, 4100 + s) % SNAG_LEN_SPAN;
            final int attachY = y0 + TRUNK_HOLE_Y_MAX + 2 + mix(notchSalt, 4200 + s) % ySpan;
            final double cosA = Math.cos(angle);
            final double sinA = Math.sin(angle);
            for (int c = 1; c <= len; c++) {
                final int bx = ax + (int) Math.round(cosA * (5 + c));
                final int bz = az + (int) Math.round(sinA * (5 + c));
                final int by = attachY - (c / 4); // 远端每 4 格垂 1（枯死下垂感）
                builder.setBlock(bx, by, bz, log, 0, BlockSink.FLAG_POPULATE);
            }
        }
    }

    /**
     * 冠壳写路径：以 (ax, y0+trunkH−{@link #CROWN_CENTER_INSET}, az) 为心的梯度带厚椭球壳 +
     * 冠缘垂帘，全部经 {@link #forEachCrownCell} 单一枚举源逐格走
     * {@link ProsperityDecorPlacer#placeLeafIfAir}（owner-local 让行，不耗 rand）。
     */
    private static void crownShell(World world, StructureBuilder builder, int ax, int az, int y0, int trunkH,
        int notchSalt, int droopSalt, Block leaves) {
        final int cy = y0 + trunkH - CROWN_CENTER_INSET;
        forEachCrownCell(
            trunkH,
            y0,
            notchSalt,
            droopSalt,
            (dx, dy, dz) -> {
                ProsperityDecorPlacer.placeLeafIfAir(builder, world, ax + dx, cy + dy, az + dz, leaves);
            });
    }

    /** 冠壳格访问器：dx/dz 相对锚点列、dy 相对冠心层（写路径与查询路径共用的最小回调面）。 */
    private interface CrownCellVisitor {

        void visit(int dx, int dy, int dz);
    }

    /** 冠壳单层半径：dy 层的椭球赤道半径 round(R·sqrt(1−(dy/H)²))。 */
    private static int layerRadius(int dy) {
        return (int) Math.round(
            MegaTreeAnchors.CANOPY_RADIUS * Math
                .sqrt(Math.max(0.0D, 1.0D - (double) dy * dy / ((double) CROWN_HALF_HEIGHT * CROWN_HALF_HEIGHT))));
    }

    /**
     * 冠壳<b>单格判定</b>（单一真值的最小单元：层半径 → 带厚梯度 → 缺角抖动三条，
     * {@link #forEachCrownCell} 主枚举与 {@link #droopCurtains} 的「该列冠壳底层」寻找共用同一份，
     * 形态不可能长出第二副真值）。r ≤ 0 的极帽层只写中心列。
     */
    private static boolean shellCellAt(int notchSalt, int dx, int dy, int dz) {
        final int r = layerRadius(dy);
        if (r <= 0) {
            return dx == 0 && dz == 0;
        }
        final int band = Math.abs(dy) <= CROWN_BAND_THICK_MAX_ABS_DY ? CROWN_SHELL_BAND_THICK : CROWN_SHELL_BAND_THIN;
        final int inner = Math.max(0, r - band);
        final int d2 = dx * dx + dz * dz;
        if (d2 > r * r + r || (r >= CROWN_THIN_R && d2 < inner * inner)) {
            return false; // 圆盘界外 / 壳内空腔（禁实心）
        }
        if (d2 > (r - 1) * (r - 1) && ((notchSalt + dx * 7349 + dz * 911 + dy * 293) & (NOTCH_MASK - 1)) < NOTCH_SKIP) {
            return false; // 缺角抖动：外沿带确定性跳格
        }
        return true;
    }

    /**
     * 冠形态<b>单一枚举源</b>（P22-B S4 上收、P26-B5 并入垂帘）：壳层逐格（dy 外层、dx/dz 内层，
     * 迭代序确定）+ 垂帘列（槽序）。写路径（冠壳落叶）与只读查询（{@link #crownUnderside} 冠底带）
     * 走<b>同一份</b>枚举 ⇒ 垂帘列的冠底自动下探到帘底，LumenPlacer 只消费不复写。
     */
    private static void forEachCrownCell(int trunkH, int y0, int notchSalt, int droopSalt, CrownCellVisitor visitor) {
        for (int dy = -CROWN_HALF_HEIGHT; dy <= CROWN_HALF_HEIGHT; dy++) {
            final int r = layerRadius(dy);
            if (r <= 0) {
                visitor.visit(0, dy, 0);
                continue;
            }
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (shellCellAt(notchSalt, dx, dy, dz)) {
                        visitor.visit(dx, dy, dz);
                    }
                }
            }
        }
        droopCurtains(y0, trunkH, notchSalt, droopSalt, visitor);
    }

    /**
     * 冠缘垂帘（P26-B5）：{@link #DROOP_SLOT_MIN}..+SPAN 条确定性角槽（黄金角步进 + 垂帘盐抖动），
     * 槽位半径带 [{@link #DROOP_R_INNER}, CANOPY_RADIUS−2]；每槽自<b>该列冠壳底层</b>（经
     * {@link #shellCellAt} 同一判定自下而上找）垂一条 1×1 叶柱，长 {@link #DROOP_LEN_MIN}..+SPAN，
     * 硬钳 {@code 帘底 y ≥ DROOP_MIN_TIP_Y}（相对层换算：tipDy ≥ 102 − 冠心绝对高）。全部经
     * visitor 枚举 ⇒ 并入冠单一真值，写路径与冠底查询自动同步。
     */
    private static void droopCurtains(int y0, int trunkH, int notchSalt, int droopSalt, CrownCellVisitor visitor) {
        final int cy = y0 + trunkH - CROWN_CENTER_INSET;
        final int slots = DROOP_SLOT_MIN + mix(droopSalt, 1) % DROOP_SLOT_SPAN;
        final int rSpan = MegaTreeAnchors.CANOPY_RADIUS - 2 - DROOP_R_INNER;
        final int minTipDy = DROOP_MIN_TIP_Y - cy;
        for (int k = 0; k < slots; k++) {
            final double angle = k * GOLDEN_ANGLE + (mix(droopSalt, 10 + k) % 1000) * 0.0006D;
            final int radius = DROOP_R_INNER + mix(droopSalt, 30 + k) % rSpan;
            final int dx = (int) Math.round(Math.cos(angle) * radius);
            final int dz = (int) Math.round(Math.sin(angle) * radius);
            int bottom = Integer.MAX_VALUE;
            for (int dy = -CROWN_HALF_HEIGHT; dy <= CROWN_HALF_HEIGHT; dy++) {
                if (shellCellAt(notchSalt, dx, dy, dz)) {
                    bottom = dy;
                    break;
                }
            }
            if (bottom == Integer.MAX_VALUE) {
                continue; // 该列被缺角全跳（防御，正常不可达——外圈 7 格带必有壳层）
            }
            int len = DROOP_LEN_MIN + mix(droopSalt, 50 + k) % DROOP_LEN_SPAN;
            if (bottom - len < minTipDy) {
                len = bottom - minTipDy; // 形态级硬钳：帘底 ≥ 102（Lumen Y_FLOOR=100 的保证）
            }
            for (int t = 1; t <= len; t++) {
                visitor.visit(dx, bottom - t, dz);
            }
        }
    }

    /**
     * 冠底列查询（P22-B S4 <b>只读枚举口</b>，消费方 = {@code ProsperityLumenPlacer} 冠下光点趟）：
     * 以与 {@link #placeInto} <b>完全相同的 rand 消费前缀</b>（干高 → 255 早退 → 缺角盐 → 垂帘盐）
     * 重放冠形态，再走 {@link #forEachCrownCell} 同一枚举（含垂帘列），给出每个冠 footprint 列
     * （dx,dz ∈ [−{@link MegaTreeAnchors#CANOPY_RADIUS}, +{@link MegaTreeAnchors#CANOPY_RADIUS}]）的
     * <b>最低冠形态层 dy</b>（冠底带；垂帘列下探到帘底；枝系不在前缀内——枝不参与冠形态）。
     * <b>零 World、零写入</b>，{@code placeInto} 行为零牵连；重放口径 = 形态格集（缺角跳格后的
     * 尝试集），不是世界让行后的实写集——让行是逐格叶门的职责，不进形态查询。
     *
     * @param out 长度 ≥ {@link #CROWN_QUERY_SIDE}²；无冠形态格的列填 {@code Integer.MIN_VALUE} 哨兵
     * @return 干高 trunkH（{@code y0+干高−冠回撤+竖半高 > 255} 整树早退 ⇒ 返回 −1，与 placeInto 同判）
     */
    public static int crownUnderside(Random treeRand, int y0, int[] out) {
        final int trunkH = TRUNK_HEIGHT_MIN + treeRand.nextInt(TRUNK_HEIGHT_SPAN);
        final int crownTop = y0 + trunkH - CROWN_CENTER_INSET + CROWN_HALF_HEIGHT;
        if (crownTop > 255) {
            return -1; // 255 红线：整树不落 ⇒ 冠下无光点（与 placeInto 同判）
        }
        final int notchSalt = treeRand.nextInt();
        final int droopSalt = treeRand.nextInt();
        Arrays.fill(out, Integer.MIN_VALUE);
        forEachCrownCell(trunkH, y0, notchSalt, droopSalt, (dx, dy, dz) -> {
            final int idx = (dx + MegaTreeAnchors.CANOPY_RADIUS) * CROWN_QUERY_SIDE
                + (dz + MegaTreeAnchors.CANOPY_RADIUS);
            if (out[idx] == Integer.MIN_VALUE || dy < out[idx]) {
                out[idx] = dy;
            }
        });
        return trunkH;
    }
}
