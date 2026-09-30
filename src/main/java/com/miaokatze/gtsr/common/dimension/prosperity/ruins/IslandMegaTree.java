package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/**
 * 垂天玄柯<b>岛心巨树形态</b>（P27 方案 B「分层相位表纯分叉顶冠」——椭球冠壳退役；方块 = 已注册
 * {@code BlocksGTSR.prosperityZenithLog} / {@code prosperityJadeLeaves}，复用既有类，无方块侧工作）。
 * <p>
 * <b>形态（五件套）</b>——
 * <ol>
 * <li><b>连续幂收分巨柱干</b>：r(i) = 1.5 + 5.0·((H−i)/H)^0.85，底 r6.5 圆盘（盘测试 d²≤r²+r，
 * 同 {@code MegaTreeForms} redwood 范式）连续收分到顶 r1.5，<b>禁分段台阶</b>；干高 133..146 ⇒
 * 岛面上总高（= 干顶帽团顶 trunkH+1）134..147，y0 带 [71,73] ⇒ 冠顶 ≤ 220（255 余量 ≥30）；
 * 干北侧 2×2 树洞（y0+8..y0+28 一段<b>选择不写</b>，「干无条件写」语义不受影响——洞=不写，不是写洞）；</li>
 * <li><b>抱岛鳍根</b>：9..11 片径向鳍墙，自干底外伸 16..23 格、鳍高 12..16 随距离线性归零，
 * 贴岛坡的基线自 y0 线性降至水线 {@code SEA_LEVEL}=68（鳍格最低 y ≥ 69，<b>不触水体</b>，
 * 干穿水的 p21 A3 口径不经根扩展）；楔形缺口留在鳍间；</li>
 * <li><b>分层相位表分叉顶冠（本档主体，取代旧三级枝+椭球壳两件）</b>：干顶 3 轮 21 条一级骨架枝
 * （相位 0.50/0.66/0.82 × 干高，8/7/6 分布），方位 = 全局哈希旋转 + 序号×{@link #GOLDEN_ANGLE}
 * （外轮 8 枝按黄金角散布，形成更密的扇区覆盖）；臂长下轮 50..57（外张微垂：
 * 抛物线弧升 +0.18→−0.10，承载外轮廓与垂帘锚）、中轮 38..42（+0.30→+0.02 平展）、上轮 30..36
 * （+0.40→+0.12 聚拢上扬）；<b>枝身 2 格粗</b>（沿臂每步水平圆盘 d²≤r²+r、r={@link #TIER1_LIMB_R}≈1.4），
 * 二级保持 1 格线；每枝 55..70% 处 2..3 叉（长 0.42×母臂，外扬 +0.45 / 内垂 −0.10 重力双角）；</li>
 * <li><b>末级叶团 + 枝身叶串（「茂密」的双承载）</b>：枝端 r6..8 叠 5 层、叉端 r6..8 叠 4 层
 * （盘半径逐层递收 r,r−1,r−2,…）+ 干顶帽团（r5 三层收口）+ 沿一级枝每 10..14 格 r3..4 叶串单盘
 * （盘面与枝身同层、包住木芯）；叶团盘几何 = {@link #tuftCellAt} 包内私有复刻
 * {@code ProsperityDecorPlacer#placeLeafDisc} 的方盘缺角剪形（<b>不动 DecorPlacer</b>，本类写/查两侧
 * 共享同一谓词 ⇒ 盘几何单一真值在本类内闭合）；</li>
 * <li><b>冠缘垂帘</b>：12..16 条 1×1 叶柱，锚 = <b>最外轮（下轮）叶团底</b>（骨架枚举收集、按角度序
 * 确定性排序——不再依赖「任意半径带必有壳层」的旧假设），自团底垂 35..60 格，<b>最低端 y ≥
 * {@link #DROOP_MIN_TIP_Y}=102</b>（形态级硬钳：垂帘并入冠形态枚举 ⇒ 冠下光位 = 帘底−1 ≥ 101
 * &gt; 判据 Y_FLOOR=100，冠最低格 y ≥ 101 的红线同源）。</li>
 * </ol>
 * 枯枝点缀：干中下部 4..6 段哈希门控裸木刺（长 5..9，无叶团）。
 * <p>
 * <b>确定性契约（跨 chunk 单射的前提，形态换代前后一字不差）</b>：整棵树的形态只由
 * {@code (treeRand 种子, ax, az, y0)} 决定——四者对每个被跨 chunk 都相同（锚点纯函数 + 树 rand
 * 以<b>锚点槽</b>派生，见 {@code ProsperityDecorPlacer.placeIslandTreePass}），且 {@code treeRand}
 * 的取数点全部在无 World 分支的固定序上：<b>干高 → 255 门 → 缺角盐 → 垂帘盐</b>（唯四 nextInt；
 * 其余形态参数——轮参/角抖/臂长/叉参/团半径/叶串相位/垂帘槽/帘长——全部走「盐混入序号哈希」
 * （{@link #mix}，新骨架统一占 5000-5999 块），零新增取数点），World 读只出现在逐格叶门里、
 * 不参与形态决策（让行口径 = {@code placeLeafIfAir} 同款 owner-local，被让掉的格<b>不消耗</b> rand）。
 * ⇒ 任何 chunk 重算得<b>同一棵</b>树，各写各的 {@code ChunkSliceSink} owns 片。
 * <p>
 * <b>写格纪律</b>：干/枝/鳍根/枯桩<b>无条件写</b>（穿水段 y≤{@code SEA_LEVEL}−1 由木取代水，
 * p21 A3 口径——本档干自 y0+1 起写、鳍根最低 y=69，实际穿水只发生在判据合成的低锚臂）；
 * 叶团/叶串/垂帘走 {@link ProsperityDecorPlacer#placeLeafIfAir}（只覆写 air/草/雪，绝不切
 * 结构/地形/水体；全部叶类格 y ≥ 102 ⇒ 写集与空世界逐格相同）。{@code crownTopAt > 255}
 * 整树早退（派生式 helper，{@link #placeInto} 与 {@link #crownUnderside} 共用同一静态——照
 * {@code MegaTreeForms} 的 255 红线，不硬写）。<b>径向硬界（派生式）</b>：任一冠格（木+叶）到
 * 树轴水平距离 ≤ {@link MegaTreeAnchors#CANOPY_RADIUS}（reachMax+tuftR ≤ R 的逐格形式，
 * 替旧死值 58²）；一级臂长另按 {@code arm ≤ R − tuftR} 参数级收口。
 * <p>
 * <b>单一枚举源</b>：冠形态（叶团格 + 叶串格 + 垂帘列）全部由 {@link #forEachCrownCell} 唯一枚举
 * （写路径与 {@link #crownUnderside} 查询共用；骨架几何收在 {@link #forEachBranchTuft} 一处，
 * 木格写路径与冠枚举共用同一骨架——木格本身不进冠形态枚举），LumenPlacer 只消费不复写。public 面 =
 * 离线判据 {@code tools/dim1/MegaTreeCheck} 直调同一形态重放（"单世界一次性写入"对拍臂）；
 * 生产侧唯一调用者是 {@code ProsperityDecorPlacer.placeIslandTreePass}。
 */
public final class IslandMegaTree {

    /** 干高下限（{@code TRUNK_HEIGHT_MIN + nextInt(14)} ⇒ 133..146，均值 139.5；岛面上总高 134..147）。 */
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
    /** 一级枝轮相位表（相位 × 干高 = 着枝高度；0.50/0.66/0.82 三轮，P27 输入契约首选表）。 */
    static final double[] TIER1_PHASE = { 0.50D, 0.66D, 0.82D };
    /** 每轮一级枝数（8/7/6 = 21 枝；保留黄金角散布并加密各层枝叶）。 */
    static final int[] TIER1_PER_PHASE = { 8, 7, 6 };
    /** 每轮臂长下限/跨度（下轮 50..57 外张承载外轮廓与垂帘锚；上轮 38..42 / 30..36 渐短聚拢）。 */
    static final int[] TIER1_ARM_MIN = { 50, 38, 30 };
    static final int[] TIER1_ARM_SPAN = { 8, 5, 7 };
    /** 每轮抛物线弧升 a·s+(b−a)·s²/(2·arm)：下轮外张微垂、中轮平展、上轮聚拢上扬。 */
    static final double[] TIER1_SLOPE_START = { 0.18D, 0.30D, 0.40D };
    static final double[] TIER1_SLOPE_END = { -0.10D, 0.02D, 0.12D };
    /** 一级枝身盘厚（d²≤r²+r 谓词、r≈1.4 ⇒ 水平 ~3×3 盘 =「2 格粗」观感；二级保持 1 格线）。 */
    static final double TIER1_LIMB_R = 1.4D;
    /** 二级叉（沿旧语义）：每母枝 2..3 叉于母臂 55..70% 处、长 0.42×母臂；外扬/内垂双角并存。 */
    static final int TIER2_MIN = 2;
    static final int TIER2_SPAN = 2;
    static final double TIER2_FORK_LO = 0.55D;
    static final double TIER2_FORK_HI = 0.70D;
    static final double TIER2_LEN_RATIO = 0.42D;
    static final double TIER2_SLOPE_OUT = 0.45D;
    static final double TIER2_SLOPE_IN = -0.10D;
    /** 二级子臂方位偏离系数（±0.45 rad 全幅；旧 3200+8i+j 角抖同式）。 */
    static final double TIER2_SPREAD = 0.0009D;
    /** 末级叶团：盘半径 6..8（mix 5400+i）；枝端叠 5 层、叉端 4 层（半径 r,r−1,r−2,… 逐层递收）。 */
    static final int TUFT_R_MIN = 6;
    static final int TUFT_R_SPAN = 3;
    static final int TUFT_LAYERS_TIP = 5;
    static final int TUFT_LAYERS_FORK = 4;
    /** 干顶帽团（leader cap）：干顶−2 起 r5 三层——填补顶轮聚拢后的中轴绿量（椭球壳退役后的顶冠收口）。 */
    static final int CAP_TUFT_R = 5;
    static final int CAP_TUFT_LAYERS = 3;
    /** 枝身叶串（茂密感的决定项）：沿一级枝每 10..14 格挂 r3..4 单盘（盘面与枝身同层、包住木芯）。 */
    static final int GARLAND_STEP_MIN = 10;
    static final int GARLAND_STEP_SPAN = 5;
    static final int GARLAND_R_MIN = 3;
    static final int GARLAND_R_SPAN = 2;
    /** 冠顶参考上浮（255 门派生式余量：干顶帽团顶 = trunkH+1 ⇒ +4 封顶界）。 */
    static final int CROWN_TOP_RISE = 4;
    /** 鳍根：9..11 片 / 外伸 16..23 / 鳍高 12..16（随干高同比放大档）。 */
    static final int ROOT_FIN_MIN = 9;
    static final int ROOT_FIN_SPAN = 3;
    static final int ROOT_REACH_MIN = 16;
    static final int ROOT_REACH_SPAN = 8;
    static final int ROOT_FIN_H_MIN = 12;
    static final int ROOT_FIN_H_SPAN = 5;
    /** 垂帘：12..16 槽（最外轮叶团底锚，不足取全）/ 帘长 35..60 / 最低端 y ≥ 102（Y_FLOOR=100 的形态级保证）。 */
    static final int DROOP_SLOT_MIN = 12;
    static final int DROOP_SLOT_SPAN = 5;
    static final int DROOP_LEN_MIN = 35;
    static final int DROOP_LEN_SPAN = 26;
    static final int DROOP_MIN_TIP_Y = 102;
    /** 黄金角步进（137.5° = 2.39996 rad；一级枝方位步进防对齐）。 */
    static final double GOLDEN_ANGLE = 2.399963D;
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

    /**
     * 径向硬界平方（<b>派生式</b>）：任一冠格（木+叶）到树轴水平距离 ≤
     * {@link MegaTreeAnchors#CANOPY_RADIUS}——「reachMax + tuftR ≤ CANOPY_RADIUS」上界的逐格形式
     * （替旧死值 58²；改半径档自动跟随，木/叶/枚举三路共用同一常量）。
     */
    static final int CANOPY_REACH_SQ = MegaTreeAnchors.CANOPY_RADIUS * MegaTreeAnchors.CANOPY_RADIUS;

    private IslandMegaTree() {}

    /**
     * 冠形态参考层（<b>干顶</b>；{@link #forEachCrownCell} 的 dy 原点，写路径与
     * {@code ProsperityLumenPlacer.canopyLightsAt} 的绝对高换算共用本单源——替旧
     * 「干顶−冠回撤」双处各算）。public = 消费方换算绝对坐标。
     */
    public static int crownCenterY(int y0, int trunkH) {
        return y0 + trunkH;
    }

    /**
     * 冠顶绝对高（255 门派生式 helper：{@link #placeInto} 与 {@link #crownUnderside} 共用同一静态，
     * 两处不可能长出两份）：干顶 + {@link #CROWN_TOP_RISE}（帽团顶 trunkH+1 的 +4 封顶余量）。
     */
    private static int crownTopAt(int y0, int trunkH) {
        return y0 + trunkH + CROWN_TOP_RISE;
    }

    /**
     * 把一棵垂天玄柯写进 {@code builder}（调用方负责套 {@code ChunkSliceSink}——本方法对坐标
     * 不做任何 owns 过滤，跨界由切片层单射）。{@code treeRand} 必须由锚点槽派生（跨 chunk 同种子），
     * 见类注释确定性契约。public = 离线判据重放口（见类注释）。
     * <p>
     * 取数序（固定，先于一切 World 读）：干高 → 255 门 → 缺角盐 → 垂帘盐；写序：冠叶（叶团+叶串+垂帘，
     * 经 {@link #forEachCrownCell} 单一枚举源）→ 鳍根 → 分枝骨架（纯木格）→ 干（含树洞让写）→ 枯桩。
     *
     * @return 是否落树（{@link #crownTopAt} &gt; 255 时整树早退返回 false，零写入）
     */
    public static boolean placeInto(World world, StructureBuilder builder, Random treeRand, int ax, int az, int y0) {
        final int trunkH = TRUNK_HEIGHT_MIN + treeRand.nextInt(TRUNK_HEIGHT_SPAN);
        if (crownTopAt(y0, trunkH) > 255) {
            return false; // 255 红线：整树早退，不硬写（照 MegaTreeForms :93）
        }
        final int notchSalt = treeRand.nextInt();
        final int droopSalt = treeRand.nextInt(); // 垂帘盐：rand 前缀第 4 味（唯四 nextInt 的后两味）
        final Block log = BlocksGTSR.prosperityZenithLog;
        final Block leaves = BlocksGTSR.prosperityJadeLeaves;
        crownTufts(world, builder, ax, az, y0, trunkH, notchSalt, droopSalt, leaves);
        rootFins(builder, ax, az, y0, notchSalt, log);
        tierBranches(builder, ax, az, y0, trunkH, notchSalt, log);
        trunk(builder, ax, az, y0, trunkH, log);
        snags(builder, ax, az, y0, trunkH, notchSalt, log);
        com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterStructure
            .placeInto(world, builder, ax, az, y0, trunkH);
        return true;
    }

    /**
     * 盐混入序号的确定性哈希（缺角抖动范式 {@code (salt + dx*7349 + …) & mask} 的泛化）：所有
     * 新形态参数（轮参/角抖/臂长/叉参/团半径/叶串相位/鳍根/垂帘槽/枯桩）经它派生，<b>零 nextInt</b>
     * ⇒ rand 前缀保持最短（干高 → 255 门 → 缺角盐 → 垂帘盐），{@link #crownUnderside} 重放口径零膨胀。
     * P27 骨架 mix 序号块（5000-5999；旧 2000-3299 随旧枝表退役）：5000 轮旋转、5100+i 角抖、
     * 5200+i 臂长、5300+8i+{0..4} 叉参（0=叉数 1=叉位 2+j=子臂角）、5400+i 团半径、5500+i 叶串步距
     * （5550+i 叶串半径）；鳍根 31/100+f/200+f/300+f、枯桩 77/4000+s/4100+s/4200+s、垂帘 1/50+k 沿用。
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

    /** 骨架访问器（{@link #forEachBranchTuft} 的最小回调面；dx/dz 相对锚列、dy 相对冠参考=干顶）。 */
    private interface SkeletonVisitor {

        /** 一级枝身步（写路径落 2 格粗水平盘；冠枚举忽略——木格不进冠形态）。 */
        void limb(int dx, int dy, int dz);

        /** 二级枝身步（1 格线；冠枚举忽略）。 */
        void forkLimb(int dx, int dy, int dz);

        /**
         * 末级叶团：盘心 (dx, dy, dz) = 枝/叉端点，叠 {@code layers} 层（层 ℓ 在 dy+ℓ、半径 r−(ℓ−1)）；
         * {@code outerWhorl} = 最外（下）轮——垂帘锚候选。
         */
        void tuft(int dx, int dy, int dz, int r, int layers, boolean outerWhorl);

        /** 枝身叶串点：单盘 r、盘面与枝身同层（dy 即枝身层）。 */
        void garland(int dx, int dy, int dz, int r);
    }

    /**
     * 分叉骨架<b>纯枚举</b>（P27 单一真值的骨架单元：干顶 3 轮 12 条一级枝 + 二级叉 + 末级叶团 +
     * 枝身叶串 + 干顶帽团，dy 相对冠参考 = 干顶；几何含干顶−2 截短与径向硬界，木格写路径
     * {@link #tierBranches} 与冠枚举 {@link #forEachCrownCell} 共用同一骨架，形态不可能长出第二副
     * 真值）。迭代序固定（轮 → 轮内枝 → 枝内先臂身后叉、叉内子臂、叉端团 → 枝端团 → 干顶帽团）；
     * 全部参数走 {@link #mix} 5000-5999 块（见其 javadoc 序号表），零 nextInt。
     */
    private static void forEachBranchTuft(int trunkH, int notchSalt, SkeletonVisitor v) {
        final int capDy = -2; // 枝头不越干顶−2（沿旧语义；dy 相对冠参考 = 干顶）
        final double baseRot = mix(notchSalt, 5000) / 2147483647.0D * 2.0D * Math.PI;
        int idx = 0;
        for (int p = 0; p < TIER1_PHASE.length; p++) {
            final int attachDy = 1 + (int) Math.round(trunkH * TIER1_PHASE[p]) - trunkH;
            for (int q = 0; q < TIER1_PER_PHASE[p]; q++, idx++) {
                final double angle = baseRot + idx * GOLDEN_ANGLE + (mix(notchSalt, 5100 + idx) % 1000 - 500) * 0.0003D;
                final int tuftR = TUFT_R_MIN + mix(notchSalt, 5400 + idx) % TUFT_R_SPAN;
                int arm = TIER1_ARM_MIN[p] + mix(notchSalt, 5200 + idx) % TIER1_ARM_SPAN[p];
                if (arm > MegaTreeAnchors.CANOPY_RADIUS - tuftR) {
                    arm = MegaTreeAnchors.CANOPY_RADIUS - tuftR; // 参数级收口：reachMax + tuftR ≤ R（派生式）
                }
                final int garlandStep = GARLAND_STEP_MIN + mix(notchSalt, 5500 + idx) % GARLAND_STEP_SPAN;
                final int garlandR = GARLAND_R_MIN + mix(notchSalt, 5550 + idx) % GARLAND_R_SPAN;
                final double cosA = Math.cos(angle);
                final double sinA = Math.sin(angle);
                int tipDx = 0;
                int tipDy = attachDy;
                int tipDz = 0;
                int sGar = garlandStep;
                for (int s = 1; s <= arm; s++) {
                    final int bx = (int) Math.round(cosA * s);
                    final int bz = (int) Math.round(sinA * s);
                    final int by = attachDy + (int) Math.round(
                        TIER1_SLOPE_START[p] * s
                            + (TIER1_SLOPE_END[p] - TIER1_SLOPE_START[p]) * (double) s * s / (2.0D * arm));
                    if (by > capDy) {
                        break; // 截短：不越干顶收窄段
                    }
                    if (bx * bx + bz * bz > CANOPY_REACH_SQ) {
                        continue; // 径向硬界（派生式）：界内截短
                    }
                    v.limb(bx, by, bz);
                    if (s == sGar && s < arm) {
                        v.garland(bx, by, bz, garlandR); // 枝身叶串：每 garlandStep 格一团
                        sGar += garlandStep;
                    }
                    tipDx = bx;
                    tipDy = by;
                    tipDz = bz;
                }
                // 二级叉：母臂 forkFrac 处起 2..3 条子臂，外扬/内垂双角；叉端落末级叶团（4 层）
                final int n2 = TIER2_MIN + mix(notchSalt, 5300 + idx * 8) % TIER2_SPAN;
                final double forkFrac = TIER2_FORK_LO
                    + (TIER2_FORK_HI - TIER2_FORK_LO) * (mix(notchSalt, 5300 + idx * 8 + 1) % 1000) / 1000.0D;
                final int s2 = (int) Math.round(forkFrac * arm);
                final int px = (int) Math.round(cosA * s2);
                final int pz = (int) Math.round(sinA * s2);
                final int py = attachDy + (int) Math.round(
                    TIER1_SLOPE_START[p] * s2
                        + (TIER1_SLOPE_END[p] - TIER1_SLOPE_START[p]) * (double) s2 * s2 / (2.0D * arm));
                final int len2 = Math.max(4, (int) Math.round(TIER2_LEN_RATIO * arm));
                for (int j = 0; j < n2; j++) {
                    final double childAngle = angle
                        + (mix(notchSalt, 5300 + idx * 8 + 2 + j) % 1000 - 500) * TIER2_SPREAD;
                    final double slope = (j & 1) == 0 ? TIER2_SLOPE_OUT : TIER2_SLOPE_IN;
                    final double cCos = Math.cos(childAngle);
                    final double cSin = Math.sin(childAngle);
                    int endX = px;
                    int endY = py;
                    int endZ = pz;
                    for (int c = 1; c <= len2; c++) {
                        final int bx = px + (int) Math.round(cCos * c);
                        final int bz = pz + (int) Math.round(cSin * c);
                        final int by = py + (int) Math.round(slope * c);
                        if (by > capDy) {
                            break; // 截短：不越干顶收窄段
                        }
                        if (bx * bx + bz * bz > CANOPY_REACH_SQ) {
                            continue; // 径向硬界（派生式）：界内截短
                        }
                        v.forkLimb(bx, by, bz);
                        endX = bx;
                        endY = by;
                        endZ = bz;
                    }
                    v.tuft(endX, endY, endZ, tuftR, TUFT_LAYERS_FORK, p == 0);
                }
                // 枝端末级叶团（5 层，r6..8 全幅）
                v.tuft(tipDx, tipDy, tipDz, tuftR, TUFT_LAYERS_TIP, p == 0);
            }
        }
        // 干顶帽团：填补顶轮聚拢后的中轴绿量（非垂帘锚）
        v.tuft(0, capDy, 0, CAP_TUFT_R, CAP_TUFT_LAYERS, false);
    }

    /**
     * 分枝骨架木格写路径（P27 纯木：叶团/叶串归 {@link #crownTufts} 冠枚举）：一级枝身沿臂每步落
     * <b>2 格粗水平圆盘</b>（d²≤r²+r、r={@link #TIER1_LIMB_R}≈1.4，同干盘谓词）；二级 1 格线。
     * 无条件写（穿水臂 p21 A3 口径）；与主干重叠由后写的 trunk 覆盖为同一 log。几何/截短/径向界
     * 全部由 {@link #forEachBranchTuft} 单一骨架给出，本方法零形态决策。
     */
    private static void tierBranches(StructureBuilder builder, int ax, int az, int y0, int trunkH, int notchSalt,
        Block log) {
        final int refY = crownCenterY(y0, trunkH);
        final int limbR2 = (int) Math.floor(TIER1_LIMB_R * TIER1_LIMB_R + TIER1_LIMB_R);
        final int limbBound = (int) Math.ceil(Math.sqrt(limbR2));
        forEachBranchTuft(trunkH, notchSalt, new SkeletonVisitor() {

            @Override
            public void limb(int dx, int dy, int dz) {
                for (int ox = -limbBound; ox <= limbBound; ox++) {
                    for (int oz = -limbBound; oz <= limbBound; oz++) {
                        if (ox * ox + oz * oz > limbR2) {
                            continue; // 圆盘界外
                        }
                        builder.setBlock(ax + dx + ox, refY + dy, az + dz + oz, log, 0, BlockSink.FLAG_POPULATE);
                    }
                }
            }

            @Override
            public void forkLimb(int dx, int dy, int dz) {
                builder.setBlock(ax + dx, refY + dy, az + dz, log, 0, BlockSink.FLAG_POPULATE);
            }

            @Override
            public void tuft(int dx, int dy, int dz, int r, int layers, boolean outerWhorl) {
                // 木格写路径不吃叶团（冠枚举专属）
            }

            @Override
            public void garland(int dx, int dy, int dz, int r) {
                // 同上
            }
        });
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
     * 冠叶写路径：{@link #forEachCrownCell} 单一枚举源（末级叶团格 + 枝身叶串格 + 垂帘列）逐格走
     * {@link ProsperityDecorPlacer#placeLeafIfAir}（owner-local 让行，不耗 rand；径向硬界的逐格
     * 裁剪收在枚举内 ⇒ 写集 ⊆ 半径 64 bbox 与查询同口径）。
     */
    private static void crownTufts(World world, StructureBuilder builder, int ax, int az, int y0, int trunkH,
        int notchSalt, int droopSalt, Block leaves) {
        final int refY = crownCenterY(y0, trunkH);
        forEachCrownCell(
            trunkH,
            y0,
            notchSalt,
            droopSalt,
            (dx, dy, dz) -> {
                ProsperityDecorPlacer.placeLeafIfAir(builder, world, ax + dx, refY + dy, az + dz, leaves);
            });
    }

    /** 冠格访问器：dx/dz 相对锚点列、dy 相对冠参考层=干顶（写路径与查询路径共用的最小回调面）。 */
    private interface CrownCellVisitor {

        void visit(int dx, int dy, int dz);
    }

    /**
     * 叶团盘单格谓词（{@code ProsperityDecorPlacer#placeLeafDisc} 方盘缺角剪形的<b>包内私有复刻</b>：
     * |dx|==rr 且 |dz|==rr 四角缺角——本片不动 DecorPlacer（跨片写禁令），写路径经
     * {@code placeLeafIfAir} 逐格走门、查询路径纯读，两侧共享本谓词 ⇒ 盘几何单一真值在本类内闭合，
     * 与 DecorPlacer/MegaTreeForms 的同款剪形零行为耦合）。
     */
    private static boolean tuftCellAt(int dx, int dz, int rr) {
        return Math.abs(dx) != rr || Math.abs(dz) != rr;
    }

    /**
     * 冠形态<b>单一枚举源</b>（P27 方案 B：末级叶团格 + 枝身叶串格 + 垂帘列——木格不进枚举，留在
     * {@link #tierBranches} 写路径）：骨架 {@link #forEachBranchTuft} 给出全部叶团/叶串几何，本枚举
     * 只做盘展开（层 ℓ 半径 r−(ℓ−1)、叶串单盘同层）+ 径向硬界逐格裁剪 + 垂帘并入。写路径
     * （{@link #crownTufts} 落叶）与只读查询（{@link #crownUnderside} 冠底带）走<b>同一份</b>枚举
     * ⇒ 垂帘列的冠底自动下探到帘底，LumenPlacer 只消费不复写。重放口径 = 形态尝试集（盘几何格），
     * 让行留在写路径叶门（既有先例）。
     */
    private static void forEachCrownCell(int trunkH, int y0, int notchSalt, int droopSalt, CrownCellVisitor visitor) {
        final List<int[]> droopAnchors = new ArrayList<>(); // {dx, 团底层 dy, dz}——最外轮叶团底
        forEachBranchTuft(trunkH, notchSalt, new SkeletonVisitor() {

            @Override
            public void limb(int dx, int dy, int dz) {
                // 木格不进冠形态枚举
            }

            @Override
            public void forkLimb(int dx, int dy, int dz) {
                // 同上
            }

            @Override
            public void tuft(int dx, int dy, int dz, int r, int layers, boolean outerWhorl) {
                for (int l = 1; l <= layers; l++) {
                    final int rr = r - (l - 1);
                    if (rr <= 0) {
                        break;
                    }
                    for (int ox = -rr; ox <= rr; ox++) {
                        for (int oz = -rr; oz <= rr; oz++) {
                            if (!tuftCellAt(ox, oz, rr)) {
                                continue; // 盘角缺角（与 placeLeafDisc 同款剪形）
                            }
                            final int cdx = dx + ox;
                            final int cdz = dz + oz;
                            if (cdx * cdx + cdz * cdz > CANOPY_REACH_SQ) {
                                continue; // 径向硬界（派生式）逐格裁剪
                            }
                            visitor.visit(cdx, dy + l, cdz);
                        }
                    }
                }
                if (outerWhorl) {
                    droopAnchors.add(new int[] { dx, dy + 1, dz }); // 垂帘锚 = 最外轮叶团底（第一盘层）
                }
            }

            @Override
            public void garland(int dx, int dy, int dz, int r) {
                for (int ox = -r; ox <= r; ox++) {
                    for (int oz = -r; oz <= r; oz++) {
                        if (!tuftCellAt(ox, oz, r)) {
                            continue;
                        }
                        final int cdx = dx + ox;
                        final int cdz = dz + oz;
                        if (cdx * cdx + cdz * cdz > CANOPY_REACH_SQ) {
                            continue;
                        }
                        visitor.visit(cdx, dy, cdz);
                    }
                }
            }
        });
        droopCurtains(trunkH, y0, droopSalt, droopAnchors, visitor);
    }

    /**
     * 冠缘垂帘（P27 换锚）：{@link #DROOP_SLOT_MIN}..+SPAN 条（不足候选数取全）1×1 叶柱，锚 =
     * <b>最外（下）轮叶团底</b>——骨架枚举收集的候选按角度序确定性排序后取前 {@code slots} 个
     * （替旧「黄金角槽位 + 半径带找壳层」的椭球假设）；自团底垂 {@link #DROOP_LEN_MIN}..+SPAN 格，
     * 硬钳 {@code 帘底 y ≥ DROOP_MIN_TIP_Y}（相对层换算：tipDy ≥ 102 − 冠参考绝对高）。全部经
     * visitor 枚举 ⇒ 并入冠单一真值，写路径与冠底查询自动同步。
     */
    private static void droopCurtains(int trunkH, int y0, int droopSalt, List<int[]> anchors,
        CrownCellVisitor visitor) {
        if (anchors.isEmpty()) {
            return;
        }
        anchors.sort((a, b) -> Double.compare(Math.atan2(a[2], a[0]), Math.atan2(b[2], b[0]))); // 角度序（确定性）
        final int slots = Math.min(DROOP_SLOT_MIN + mix(droopSalt, 1) % DROOP_SLOT_SPAN, anchors.size());
        final int minTipDy = DROOP_MIN_TIP_Y - crownCenterY(y0, trunkH);
        for (int k = 0; k < slots; k++) {
            final int[] a = anchors.get(k);
            int len = DROOP_LEN_MIN + mix(droopSalt, 50 + k) % DROOP_LEN_SPAN;
            if (a[1] - len < minTipDy) {
                len = a[1] - minTipDy; // 形态级硬钳：帘底 ≥ 102（Lumen Y_FLOOR=100 的保证）
            }
            for (int t = 1; t <= len; t++) {
                visitor.visit(a[0], a[1] - t, a[2]);
            }
        }
    }

    /**
     * 冠底列查询（<b>只读枚举口</b>，消费方 = {@code ProsperityLumenPlacer} 冠下光点趟）：以与
     * {@link #placeInto} <b>完全相同的 rand 消费前缀</b>（干高 → 255 早退 → 缺角盐 → 垂帘盐）重放
     * 冠形态，再走 {@link #forEachCrownCell} 同一枚举（叶团格 + 叶串格 + 垂帘列），给出每个冠
     * footprint 列（dx,dz ∈ [−{@link MegaTreeAnchors#CANOPY_RADIUS}, +{@link MegaTreeAnchors#CANOPY_RADIUS}]）的
     * <b>最低冠形态层 dy</b>（冠底带；垂帘列下探到帘底；木格不在枚举内——木格不参与冠形态）。
     * <b>零 World、零写入</b>，{@code placeInto} 行为零牵连；重放口径 = 形态格集（盘几何的尝试集），
     * 不是世界让行后的实写集——让行是逐格叶门的职责，不进形态查询。
     *
     * @param out 长度 ≥ {@link #CROWN_QUERY_SIDE}²；无冠形态格的列填 {@code Integer.MIN_VALUE} 哨兵
     * @return 干高 trunkH（{@link #crownTopAt} &gt; 255 整树早退 ⇒ 返回 −1，与 placeInto 同判）
     */
    public static int crownUnderside(Random treeRand, int y0, int[] out) {
        final int trunkH = TRUNK_HEIGHT_MIN + treeRand.nextInt(TRUNK_HEIGHT_SPAN);
        if (crownTopAt(y0, trunkH) > 255) {
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
