package com.miaokatze.gtsr.common.machine.cluster;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.PressureSteamInputHatch;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamInputHatch;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/**
 * 蒸汽动力矿物处理物流工程集群结构定义：12 深主段 + 8 深可重复延伸段（最多 9 段，总段数 10）、
 * 四族 tier、F/H/G 三类模块挂点与 P 主控输入仓位。
 *
 * <h2>布局与偏移推导</h2>
 * 两个矩阵均以层在前书写：{@code SHAPE[layer][depthRow][column]} = {@code [Y][Z][X]}，顶层、
 * 正面行和左列均为下标 0。注册时保留 {@link StructureUtility#transpose(String[][])}，恢复
 * StructureLib canonical 轴序；故 {@code checkPiece(H, V, D)} 偏移仍是控制器的
 * {@code (column, layer, depthRow)}。
 * <p>
 * 主段 15×12×29 中唯一 '~' 位于 {@code (24,12,7)}，即
 * {@code mainOffsetA/B/C = (24,12,7)}，局部深度区间 {@code [-7,+4]}。延伸段 15×8×29 与主段
 * 同列对齐、串接在主段背面：第 k 段 {@code extOffsetC(k) = 7 - 12 - 8k = -5 - 8k}，占据局部深度
 * {@code [5+8k, 12+8k]}，与主段无重叠、无间隙。满配 9 延伸段对应
 * {@link ClusterTopology#MAX_EXTENSION_SEGMENTS}；总段数上限
 * {@link ClusterTopology#MAX_SEGMENTS}=10。
 *
 * <h2>F/H/G 模块挂点（unitSlot）</h2>
 * 三字符均为不校验方块的单元 NAC 挂点：任意世界方块都通过结构检查；若该位置承载集群模块控制器，
 * 按字符限定的类型（F=加工 / H=增幅 / G=物流）动态接入 {@code (segment, padId)} 槽位。<b>挂点一律
 * 不校验朝向</b>（用户拍板 2026-08-24：物流挂点同样不校验 WEST/EAST 朝向，作废一切朝向检查）；
 * 模块能否实际成型由模块自身多方块空间与空气要求决定。自动建造不提示、不放置方块。同段同类第二个
 * 模块不接入，并经 {@link #drainModuleConflicts()} 记录
 * {@link ClusterStructureError#moduleConflict(int, int)}（模块冲突检查点，总控 E1b 收尾取走）。
 *
 * <h2>P 主控输入仓</h2>
 * 主段 {@code (23,12,7)}/{@code (25,12,7)}（控制器左右）与延伸段 {@code (0,12,3)} 为可选输入仓：
 * tiered 外壳或 anyOf(标准输入仓 / 蒸汽输入仓 / 耐压蒸汽输入仓) 三者之一（禁用 atLeast）。
 * A 外壳回退纯 tiered casing，不再容纳任何 bus / energy hatch。
 *
 * <h2>四族 tier（0-3）</h2>
 * <ul>
 * <li>A 外壳：casings1:10 青铜 / casings2:0 钢 / casings4:2 钛 / casings4:0 钨钢；</li>
 * <li>B 管道：casings2:12/13/14/15；</li>
 * <li>C 燃烧室：casings3:13 / casings3:14 / casings4:3 / casings3:15；</li>
 * <li>D 框架：blockframes meta 300/305/28/316。</li>
 * </ul>
 *
 * <h2>段号逆变换</h2>
 * StructureDefinition 的挂点元素跨 piece 共享，故段号不能由字符闭包注入。检查回调以
 * {@code getOffsetABC(worldDelta)} 的 {@code abc[2]}（局部 depthRow − 偏移C）反推：主段区间
 * {@code [-7,+4]} 为 0；延伸第 k 段区间 {@code [5+8k,12+8k]} 为 {@code k+1}。这与
 * {@link #extOffsetC(int)} 的 {@code -5-8k} 偏移链互逆（由旧 20 深主段的 {@code -13-8k}
 * 平移 8 而来）。总控 {@code addClusterUnit} 保持三参签名 {@code (unit, padId, segment)}。
 */
public final class ClusterStructureDef {

    public static final String PIECE_MAIN = "main";
    public static final String PIECE_EXT = "ext";
    public static final int SEGMENT_MAIN = 0;

    // controller in layer-first (column, layer, depthRow) = (24, 12, 7)
    private static final int MAIN_HORIZONTAL_OFF_SET = 24;
    private static final int MAIN_VERTICAL_OFF_SET = 12;
    private static final int MAIN_DEPTH_OFF_SET = 7;
    private static final int MAIN_DEPTH = ClusterParams.SEGMENT_DEPTH_MAIN;
    private static final int EXT_DEPTH = ClusterParams.SEGMENT_DEPTH_EXT;

    // TODO-E6: hatchAdder.casingIndex 当前为静态 T1 青铜 hint；tier 联动底材随贴图切片落地
    private static final int BRONZE_CASING_TEXTURE_ID = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    private static final List<Pair<Block, Integer>> CASING_FAMILY = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings1, 10),
        Pair.of(GregTechAPI.sBlockCasings2, 0),
        Pair.of(GregTechAPI.sBlockCasings4, 2),
        Pair.of(GregTechAPI.sBlockCasings4, 0));
    private static final List<Pair<Block, Integer>> PIPE_FAMILY = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings2, 12),
        Pair.of(GregTechAPI.sBlockCasings2, 13),
        Pair.of(GregTechAPI.sBlockCasings2, 14),
        Pair.of(GregTechAPI.sBlockCasings2, 15));
    private static final List<Pair<Block, Integer>> FIREBOX_FAMILY = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings3, 13),
        Pair.of(GregTechAPI.sBlockCasings3, 14),
        Pair.of(GregTechAPI.sBlockCasings4, 3),
        Pair.of(GregTechAPI.sBlockCasings3, 15));
    private static final List<Pair<Block, Integer>> FRAME_FAMILY = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockFrames, 300),
        Pair.of(GregTechAPI.sBlockFrames, 305),
        Pair.of(GregTechAPI.sBlockFrames, 28),
        Pair.of(GregTechAPI.sBlockFrames, 316));

    /**
     * 主段（15×12×29）＝草稿 {@code plan/集群扣除延伸层.java} 字面矩阵 + 挂点重放：
     * 加工 F 十字 {@code (10,10,6)/(8,10,8)/(12,10,8)/(10,10,10)}、增幅 H 四格 y7
     * {@code (18,7,7)/(17,7,8)/(19,7,8)/(18,7,9)}、物流 G {@code (27,12,8)}、输入仓 P
     * {@code (23,12,7)/(25,12,7)}；y13 的 40 格 F 菱形（深 2-9、列 2-9）按草稿字面保留。
     * 'I' 字符不再使用（延伸段塔位归一化为 '-'）。
     */
    private static final String[][] SHAPE_MAIN = {
        { "                             ", "                             ", "    AAAA                     ",
            "   A----A                    ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "   A----A                    ",
            "    AAAA                     ", "                             ", "                             " },
        { "                             ", "                             ", "    AAAA                     ",
            "   A----A                    ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "   A----A                    ",
            "    AAAA                     ", "                             ", "                             " },
        { "                             ", "                             ", "    AAAA                     ",
            "   A----A                    ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "   A----A                    ",
            "    AAAA                     ", "                             ", "                             " },
        { "DDDDDDDDDDDD                 ", "D          D                 ", "D   AAAA   D                 ",
            "D  A----A  D                 ", "D A------A D                 ", "D A------A D                 ",
            "D A------A D                 ", "D A------A D                 ", "D  A----A  D                 ",
            "D   AAAA   D                 ", "D          D                 ", "DDDDDDDDDDDD                 " },
        { "D          D                 ", "                             ", "    AAAA                     ",
            "   A----A                    ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "   A----A                    ",
            "    AAAA                     ", "                             ", "D          D                 " },
        { "D          D                 ", "                             ", "   DAAAAD                    ",
            "  DA----AD                   ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "  DA----AD                   ",
            "   DAAAAD                    ", "                             ", "D          D                 " },
        { "D          D                 ", "                             ", "   DAAAAD                    ",
            "  DA----AD                   ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "  DA----AD                   ",
            "   DAAAAD                    ", "                             ", "D          D                 " },
        { "D          D                 ", "                             ", "   DAAAAD                    ",
            "  DA----AD                   ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A        H          ", "  DA----AD       H H         ",
            "   DAAAAD         H          ", "                             ", "D          D                 " },
        { "D          D                 ", "   D    D                    ", "   AAAAAA                    ",
            " DA------AD                  ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", " DA------AD                  ",
            "   AAAAAA                    ", "   D    D                    ", "D          D                 " },
        { "D          D                 ", "   DAAAAD                    ", "   A----A                    ",
            " DA------AD                  ", " A--------A            AAA   ", " A--------A    AAAA   AEEEA  ",
            " A--------A   AEEEEAAAEEEEEA ", " A--------A   AEEEEAAAEEEEEA ", " DA------AD    AAAA   AEEEA  ",
            "   A----A              AAA   ", "   DAAAAD                    ", "D          D                 " },
        { "D          D                 ", "   DAAAAD                    ", "   A----A                    ",
            " DA------AD                  ", " A--------A    AAAA   A   A  ", " A--------A   A    AAA     A ",
            " A--------F  A              A", " A--------A  A              A", " DA-----FAD F A    AAA     A ",
            "   A----A      AAAA   A   A  ", "   DAAAAD F            AAA   ", "D          D                 " },
        { "D          D                 ", "   DAAAAD                    ", "   A----A                    ",
            " DA------AD                  ", " A--------A    AAAA   A   A  ", " A--------A   A    AAA     A ",
            " A--------A  ABBBB          A", " A--------A  ABBBB          A", " DA------AD   A    AAA     A ",
            "   A----A      AAAA   A   A  ", "   DAAAAD              AAA   ", "D          D                 " },
        { "D          D                 ", "   DAAAAD                    ", "   A----A                    ",
            " DA------AD                  ", " A--------A    EEEE   A   A  ", " A--------A   A    EEE     E ",
            " A--------A  ABBBB          E", " A--------A  ABBBB     P~P  E", " DA------AD   A    EEE     G ",
            "   A----A      EEEE   E   E  ", "   DAAAAD              EEE   ", "D          D                 " },
        { "D   ----   D                 ", "   D----D                    ", "   --FF---                   ",
            " D--FFFF--D                  ", "---FFFFFF---   AAAA   A   A  ", "--FFFFFFFF--  A    AAA     A ",
            "--FFFFFFFF-- A  BB          A", "---FFFFFF--- A  BB     AAA  A", " D--FFFF--D   A    AAA     A ",
            "  ---FF---     AAAA   A   A  ", "   D----D              AAA   ", "D   ----   D                 " },
        { "D   AAAA   D                 ", "   DAAAAD                    ", "   AABBAAA                   ",
            " DAABBBBAAD    AAAA   AAAAA  ", "AAABBBBBBAAA  AAAAAAAAAAAAAA ", "AABBBBBBBBAA AACCCCAAAAAAAAAA",
            "AABBBBBBBBAA AAABBCAAAAAAAAAA", "AAABBBBBBAAA AAABBCAAAAAAAAAA", " DAABBBBAAD  AACCCCAAAAAAAAAA",
            "  AAABBAAA    AAAAAAAAAAAAAA ", "   DAAAAD      AAAA   AAAAA  ", "D   AAAA   D           AAA   " } };

    /**
     * 延伸段（15×8×29）＝草稿 {@code plan/蒸汽动力矿物处理物流工程集群-延伸层-修.java} 字面矩阵 +
     * 挂点重放：草稿 F 四格字面保留（y10：深2列10 / 深4列8 / 深4列12 / 深6列10）；草稿 y10 的
     * H 四格上移到 y7（深3列18 / 深4列17 / 深4列19 / 深5列18），原位改回 '-' 严格空气；G 字面
     * 保留（y12 深4 列27）；y3 塔 'AIIIA' 的 15 格 I 归一化为 '-'（严格空气）；可选输入仓 P
     * {@code (0,12,3)}。
     */
    private static final String[][] SHAPE_EXT = {
        { "                             ", "                             ", "                             ",
            "                             ", "                             ", "                             ",
            "                             ", "                             " },
        { "                             ", "                             ", "                             ",
            "                             ", "                             ", "                             ",
            "                             ", "                             " },
        { "                             ", " AAA                         ", "A   A                        ",
            "A   A                        ", "A   A                        ", "A   A                        ",
            "A   A                        ", " AAA                         " },
        { "D   D                        ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "ADDDA                        ",
            "ADDDA                        ", "ADDDA                        ", "ADDDA                        ",
            "ADDDA                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A                        ",
            "A---A             H          ", "A---A            H H         ", "A---A             H          ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A     F     D   D        ",
            "A---A             -          ", "A---A   F   F    - -         ", "A---A             -          ",
            "A---A     F     D   D        ", "DAAAD                        " },
        { "                             ", "DAAAD  D     D               ", "A---A          DD   DD  D    ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A          DD   DD  D    ", "DAAAD  D     D               " },
        { "                             ", "DAAAD  DD   DD         D    D", "A---A  D     D DDD DDD  DA   ",
            "P---AEEE     EEEE   EEEEA    ", "A---AEEE     EEEE   EEEEA  G ", "A---AEEE     EEEE   EEEEA    ",
            "A---A  D     D DDD DDD  DA   ", "DAAAD  DD   DD         D    D" },
        { "                             ", "DAAAD  DD   DD  DD DD  D   DD", "A---A  DAAAAAD DAAAAAD  AAAAD",
            "A---AEEEAABAAEEEA   AEEAA    ", "A---BBBBBBBBBBBBB   BBBBB    ", "A---AEEEAABAAEEEA   AEEAA    ",
            "A---A  DAAAAAD DAAAAAD  AAAAD", "DAAAD  DD   DD  DD DD  D   DD" },
        { "                             ", "DAAAD  DACCCAD  ACCCA  DACCAD", "ACCCA  AAAAAAA DAAAAAD AAAAAA",
            "ACCCAEEAAAAAAAEEAAAAAEEAAAAAA", "ACCCAAAAAAAAAAAAAAAAAAAAAAAAA", "ACCCAEEAAAAAAA  AAAAAEEAAAAAA",
            "ACCCA  AAAAAAA DAAAAAD AAAAAA", "DAAAD  DACCCAD  ACCCA  DACCAD" } };

    public static IStructureDefinition<MTESteamMineralLogisticsCluster> create() {
        return StructureDefinition.<MTESteamMineralLogisticsCluster>builder()
            .addShape(PIECE_MAIN, transpose(SHAPE_MAIN))
            .addShape(PIECE_EXT, transpose(SHAPE_EXT))
            .addElement('-', isAir())
            .addElement(
                'A',
                ofBlocksTiered(
                    ClusterStructureDef::getCasingTier,
                    CASING_FAMILY,
                    -1,
                    (t, tier) -> t.mCasingTier = tier,
                    t -> t.mCasingTier))
            .addElement(
                'B',
                ofBlocksTiered(
                    ClusterStructureDef::getPipeTier,
                    PIPE_FAMILY,
                    -1,
                    (t, tier) -> t.mPipeTier = tier,
                    t -> t.mPipeTier))
            .addElement(
                'C',
                ofBlocksTiered(
                    ClusterStructureDef::getFireboxTier,
                    FIREBOX_FAMILY,
                    -1,
                    (t, tier) -> t.mFireboxTier = tier,
                    t -> t.mFireboxTier))
            .addElement(
                'D',
                ofBlocksTiered(
                    ClusterStructureDef::getFrameTier,
                    FRAME_FAMILY,
                    -1,
                    (t, tier) -> t.mFrameTier = tier,
                    t -> t.mFrameTier))
            .addElement('E', ofBlock(GregTechAPI.sBlockGlass1, 10))
            .addElement('F', unitSlot(MTEBasicProcessingUnit.class, ClusterTopology.PAD_WORKING))
            .addElement('G', unitSlot(MTEBasicLogisticsUnit.class, ClusterTopology.PAD_LOGISTICS))
            .addElement('H', unitSlot(MTEBasicAmplifierUnit.class, ClusterTopology.PAD_BOOSTER))
            .addElement('P', controllerInputSlot())
            .build();
    }

    public static int mainOffsetA() {
        return MAIN_HORIZONTAL_OFF_SET;
    }

    public static int mainOffsetB() {
        return MAIN_VERTICAL_OFF_SET;
    }

    public static int mainOffsetC() {
        return MAIN_DEPTH_OFF_SET;
    }

    /** Preserves the existing public signature: rotated extensions fully align on A. */
    public static int extOffsetA(int k) {
        return MAIN_HORIZONTAL_OFF_SET;
    }

    public static int extOffsetB() {
        return MAIN_VERTICAL_OFF_SET;
    }

    /**
     * @return {@code -5 - 8k}; iterate's abc[2] is depthRow-offsetC, so extension k occupies
     *         local depth {@code [5+8k, 12+8k]} right behind main's {@code [-7, +4]} without overlap
     *         (old 20-deep formula {@code -13-8k} shifted by 8).
     */
    public static int extOffsetC(int k) {
        return MAIN_DEPTH_OFF_SET - MAIN_DEPTH - EXT_DEPTH * k;
    }

    /** Preserves the existing public signature for external callers addressing extension zero. */
    public static int extOffsetC() {
        return extOffsetC(0);
    }

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 0;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        if (block == GregTechAPI.sBlockCasings4 && meta == 2) return 2;
        if (block == GregTechAPI.sBlockCasings4 && meta == 0) return 3;
        return null;
    }

    @Nullable
    public static Integer getPipeTier(Block block, int meta) {
        if (block != GregTechAPI.sBlockCasings2) return null;
        if (meta == 12) return 0;
        if (meta == 13) return 1;
        if (meta == 14) return 2;
        if (meta == 15) return 3;
        return null;
    }

    @Nullable
    public static Integer getFireboxTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings3) {
            if (meta == 13) return 0;
            if (meta == 14) return 1;
            if (meta == 15) return 3;
            return null;
        }
        if (block == GregTechAPI.sBlockCasings4 && meta == 3) return 2;
        return null;
    }

    @Nullable
    public static Integer getFrameTier(Block block, int meta) {
        if (block != GregTechAPI.sBlockFrames) return null;
        if (meta == 300) return 0;
        if (meta == 305) return 1;
        if (meta == 28) return 2;
        if (meta == 316) return 3;
        return null;
    }

    /**
     * P 主控输入仓元素：tiered 外壳（可选位默认形态）或 anyOf(标准输入仓 / 蒸汽输入仓 / 耐压蒸汽
     * 输入仓)。禁用 atLeast（GT5U atLeast 是“各元素至少一个”语义，此处不适用）。自定义 adder：
     * 标准输入仓走 {@code addInputHatchToMachineList} 纳入 mInputHatches；耐压蒸汽输入仓在本
     * Enhanced 基座上无标准注册通道（其类不是 MTEHatchInput），先 updateTexture 接受成型，
     * 仓列表归总控持有（TODO-E1b，参照 MTEKineticProcessingArray.mPressureSteamInputs 范式）。
     */
    private static IStructureElement<MTESteamMineralLogisticsCluster> controllerInputSlot() {
        return ofChain(
            ofBlocksTiered(
                ClusterStructureDef::getCasingTier,
                CASING_FAMILY,
                -1,
                (t, tier) -> t.mCasingTier = tier,
                t -> t.mCasingTier),
            buildHatchAdder(MTESteamMineralLogisticsCluster.class)
                .anyOf(InputHatch, SteamInputHatch, PressureSteamInputHatch)
                .adder(ClusterStructureDef::addControllerInputHatch)
                .casingIndex(BRONZE_CASING_TEXTURE_ID)
                .hint(1)
                .build());
    }

    /** P 位注册 adder：见 {@link #controllerInputSlot()}。casingIndex 可能为 null（不请求贴图）。 */
    private static boolean addControllerInputHatch(MTESteamMineralLogisticsCluster t, IGregTechTileEntity te,
        Short casingIndex) {
        if (te == null) return false;
        IMetaTileEntity mte = te.getMetaTileEntity();
        if (mte instanceof MTEHatchPressureSteamInput hatch) {
            hatch.updateTexture(casingIndex == null ? 0 : casingIndex.intValue());
            // TODO-E1b: 总控侧耐压蒸汽输入仓列表（经济结算经 GTSRHatchFluidAccess 读该列表）
            return true;
        }
        return t.addInputHatchToMachineList(te, casingIndex == null ? 0 : casingIndex.intValue());
    }

    /**
     * 模块冲突记录缓冲（模块冲突检查点）。仅服务器主线程的结构检查路径写入；总控（E1b）在
     * checkMachine 收尾调用 {@link #drainModuleConflicts()} 取走并入 errors 列表。容量上限 64
     * 防止无人取走时无界增长（超限丢最旧）。
     */
    private static final List<ClusterStructureError> MODULE_CONFLICT_BUFFER = new ArrayList<>();

    /** 取走并清空本结构检查周期内记录的模块冲突错误（E1b 总控 checkMachine 收尾调用）。 */
    public static List<ClusterStructureError> drainModuleConflicts() {
        List<ClusterStructureError> drained = new ArrayList<>(MODULE_CONFLICT_BUFFER);
        MODULE_CONFLICT_BUFFER.clear();
        return drained;
    }

    private static void recordModuleConflict(ClusterStructureError error) {
        if (MODULE_CONFLICT_BUFFER.size() >= 64) MODULE_CONFLICT_BUFFER.remove(0);
        MODULE_CONFLICT_BUFFER.add(error);
    }

    /**
     * F/H/G 通用单元挂点：不校验方块、不校验朝向（用户拍板）。世界位置不是 GT TileEntity 或不是
     * {@code unitType}（含子类）时通过且不占位；是正确类型时按 {@code (segment, padId)} 接入
     * {@code addClusterUnit(unit, padId, segment)}；同段同类已占位（接入返回 false）则不接入并记录
     * {@link ClusterStructureError#moduleConflict(int, int)}。挂点不参与结构实体计数，全息建造不
     * 提示、不放置方块（survival 路径 SKIP）。
     */
    private static IStructureElement<MTESteamMineralLogisticsCluster> unitSlot(
        Class<? extends MTEClusterUnitBase> unitType, int padId) {
        return new IStructureElement<MTESteamMineralLogisticsCluster>() {

            @Override
            public boolean check(MTESteamMineralLogisticsCluster t, World world, int x, int y, int z) {
                TileEntity te = world.getTileEntity(x, y, z);
                if (!(te instanceof IGregTechTileEntity gte)) return true;
                IMetaTileEntity mte = gte.getMetaTileEntity();
                if (!unitType.isInstance(mte)) return true;
                int segment = segmentOfWorldPos(t, x, y, z);
                if (segment < 0 || segment >= ClusterTopology.MAX_SEGMENTS) return true;
                if (!t.addClusterUnit((MTEClusterUnitBase) mte, padId, segment)) {
                    recordModuleConflict(ClusterStructureError.moduleConflict(segment, padId));
                }
                return true;
            }

            @Override
            public boolean spawnHint(MTESteamMineralLogisticsCluster t, World world, int x, int y, int z,
                ItemStack trigger) {
                return true;
            }

            @Override
            public boolean placeBlock(MTESteamMineralLogisticsCluster t, World world, int x, int y, int z,
                ItemStack trigger) {
                return true;
            }

            @Override
            public IStructureElement.PlaceResult survivalPlaceBlock(MTESteamMineralLogisticsCluster t, World world,
                int x, int y, int z, ItemStack trigger, IItemSource source, EntityPlayerMP actor,
                Consumer<IChatComponent> chatter) {
                return IStructureElement.PlaceResult.SKIP;
            }
        };
    }

    /**
     * abc[2] is the registered canonical depth axis: main {@code [-7,+4]} is segment 0; extension k
     * {@code [5+8k, 12+8k]} is {@code k+1}. This is the inverse of {@link #extOffsetC(int)}
     * {@code -5-8k} (formula itself unchanged from the 20-deep era; only MAIN_DEPTH shrank 20→12).
     */
    private static int segmentOfWorldPos(MTESteamMineralLogisticsCluster t, int x, int y, int z) {
        IGregTechTileEntity base = t.getBaseMetaTileEntity();
        Vec3Impl abc = t.getExtendedFacing()
            .getOffsetABC(new Vec3Impl(x - base.getXCoord(), y - base.getYCoord(), z - base.getZCoord()));
        int localDepth = abc.get2();
        if (localDepth < MAIN_DEPTH - MAIN_DEPTH_OFF_SET) return SEGMENT_MAIN;
        return 1 + (localDepth - (MAIN_DEPTH - MAIN_DEPTH_OFF_SET)) / EXT_DEPTH;
    }

    private ClusterStructureDef() {}
}
