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
 * 蒸汽动力矿物处理物流工程集群结构定义：20 深主段 + 8 深可重复延伸段（最多 9 段，总段数 10）、
 * 四族 tier、'e' 粒子候选空气位、F/H/G 三类模块挂点与 A 总控仓室自由化（外壳/输入仓两态）。
 *
 * <h2>布局与偏移推导</h2>
 * 两个矩阵均以层在前书写：{@code SHAPE[layer][depthRow][column]} = {@code [Y][Z][X]}，顶层、
 * 正面行和左列均为下标 0。注册时保留 {@link StructureUtility#transpose(String[][])}，恢复
 * StructureLib canonical 轴序；故 {@code checkPiece(H, V, D)} 偏移仍是控制器的
 * {@code (column, layer, depthRow)}。
 * <p>
 * 主段 15×20×29 中唯一 '~' 位于 {@code (24,12,7)}，即
 * {@code mainOffsetA/B/C = (24,12,7)}，局部深度区间 {@code [-7,+12]}。延伸段 15×8×29 与主段
 * 同列对齐、串接在主段背面：第 k 段 {@code extOffsetC(k) = 7 - 20 - 8k = -13 - 8k}，占据局部深度
 * {@code [13+8k, 20+8k]}，与主段无重叠、无间隙。满配 9 延伸段对应
 * {@link ClusterTopology#MAX_EXTENSION_SEGMENTS}；总段数上限
 * {@link ClusterTopology#MAX_SEGMENTS}=10。
 *
 * <h2>'e' 粒子候选空气位</h2>
 * 严格空气位（{@code isAir()}），同时是 {@link ClusterParticleFx} 的集群级粒子候选位：主段
 * 55 格（y3 行 14-18 壁 15 格 + y13 中央塔 40 格）+ 每延伸段 15 格（y3 行 2-6 壁）。客户端按
 * {@link #clusterAirFxOffsets(int)}（同步到的延伸段数）一次性注册候选；字符本身不校验额外方块，
 * 偏移算术复用 {@link #extOffsetC(int)}/mainOffset 偏移族（与挂点/断层探测同源，勿手推符号）。
 *
 * <h2>F/H/G 模块挂点（unitSlot）</h2>
 * 三字符均为不校验方块的单元 NAC 挂点：任意世界方块都通过结构检查；若该位置承载集群模块控制器，
 * 按字符限定的类型（F=加工 / H=增幅 / G=物流）动态接入 {@code (segment, padId)} 槽位。<b>挂点一律
 * 不校验朝向</b>（用户拍板 2026-08-24：物流挂点同样不校验 WEST/EAST 朝向，作废一切朝向检查）；
 * 模块能否实际成型由模块自身多方块空间与空气要求决定。自动建造不提示、不放置方块。同段同类第二个
 * 模块不接入，并经 {@link #drainModuleConflicts()} 记录
 * {@link ClusterStructureError#moduleConflict(int, int)}（模块冲突检查点，总控 E1b 收尾取走）。
 * 挂点只存在于基础层后部 8 深（延伸图案）区域：F {@code (10,10,14)/(8,10,16)/(12,10,16)/(10,10,18)}、
 * H y9 {@code (18,9,15)/(17,9,16)/(19,9,16)/(18,9,17)}、G {@code (27,12,16)}；每延伸段同图案
 * 各一组。等级 1 全息即完整 20 深基础层（含挂点）。
 *
 * <h2>A 总控仓室自由化（外壳/输入仓两态）</h2>
 * 矩阵内任意 A 位可为四族 tiered 外壳（默认形态）或 anyOf(标准输入仓 / 蒸汽输入仓 / 耐压蒸汽
 * 输入仓) 三者之一（禁用 atLeast；原 P 专用字符已删除）。数量校验在总控 checkMachine：通用
 * 输入仓 1..10、蒸汽仓类合计 0..10。B/C/D/E 字符仍为纯结构方块。
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
 * {@code [-7,+12]} 为 0；延伸第 k 段区间 {@code [13+8k, 20+8k]} 为 {@code k+1}。这与
 * {@link #extOffsetC(int)} 的 {@code -13-8k} 偏移链互逆（20 深主段原始公式，中间 12 深时期
 * 曾平移为 {@code -5-8k}）。总控 {@code addClusterUnit} 保持三参签名
 * {@code (unit, padId, segment)}。
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
    /** C 燃烧室族：与单元 {@code tieredFireboxElement} 共用（熔炉模块 D 位同族）。 */
    public static final List<Pair<Block, Integer>> FIREBOX_FAMILY = ImmutableList.of(
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
     * 主段（15×20×29）＝12 深基础字面（{@code plan/集群扣除延伸层.java}，行 0..11，F/H/G/P 注入
     * 全部移除恢复草稿）+ 延伸段同图案（行 12..19，即 {@code plan/蒸汽动力矿物处理物流工程集群-
     * 延伸层-修.java} 字面）拼接。拼接后基础层唯一挂点集（坐标 (col,layer,depthRow)）：
     * 加工 F 十字 {@code (10,10,14)/(8,10,16)/(12,10,16)/(10,10,18)}、增幅 H 四格 y9
     * {@code (18,9,15)/(17,9,16)/(19,9,16)/(18,9,17)}、物流 G {@code (27,12,16)}。
     * 增幅塔 y7 草稿 H 四格位改 ' ' skip（H 挂点让位至 y9 同图案）；y3 塔 'AIIIA' 的 15 格 I
     * 归一化为 '-'（严格空气），行 14-18 壁 'A---A' 改 'AeeeA'（粒子候选空气位）；y13 中央塔
     * 40 格 F 菱形（深 2-9、列 2-9）改 'e'（移除塔内挂点，粒子候选空气位，延伸段无塔）；
     * y12 深 15 列恢复 {@code A~A} 草稿字面。等级 1 全息即完整 20 深基础层（含挂点）。
     */
    private static final String[][] SHAPE_MAIN = {
        { "                             ", "                             ", "    AAAA                     ",
            "   A----A                    ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "   A----A                    ",
            "    AAAA                     ", "                             ", "                             ",
            "                             ", "                             ", "                             ",
            "                             ", "                             ", "                             ",
            "                             ", "                             " },
        { "                             ", "                             ", "    AAAA                     ",
            "   A----A                    ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "   A----A                    ",
            "    AAAA                     ", "                             ", "                             ",
            "                             ", "                             ", "                             ",
            "                             ", "                             ", "                             ",
            "                             ", "                             " },
        { "                             ", "                             ", "    AAAA                     ",
            "   A----A                    ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "   A----A                    ",
            "    AAAA                     ", "                             ", "                             ",
            "                             ", " AAA                         ", "A   A                        ",
            "A   A                        ", "A   A                        ", "A   A                        ",
            "A   A                        ", " AAA                         " },
        { "DDDDDDDDDDDD                 ", "D          D                 ", "D   AAAA   D                 ",
            "D  A----A  D                 ", "D A------A D                 ", "D A------A D                 ",
            "D A------A D                 ", "D A------A D                 ", "D  A----A  D                 ",
            "D   AAAA   D                 ", "D          D                 ", "DDDDDDDDDDDD                 ",
            "D   D                        ", "DAAAD                        ", "AeeeA                        ",
            "AeeeA                        ", "AeeeA                        ", "AeeeA                        ",
            "AeeeA                        ", "DAAAD                        " },
        { "D          D                 ", "                             ", "    AAAA                     ",
            "   A----A                    ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "   A----A                    ",
            "    AAAA                     ", "                             ", "D          D                 ",
            "                             ", "DAAAD                        ", "ADDDA                        ",
            "ADDDA                        ", "ADDDA                        ", "ADDDA                        ",
            "ADDDA                        ", "DAAAD                        " },
        { "D          D                 ", "                             ", "   DAAAAD                    ",
            "  DA----AD                   ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "  DA----AD                   ",
            "   DAAAAD                    ", "                             ", "D          D                 ",
            "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "D          D                 ", "                             ", "   DAAAAD                    ",
            "  DA----AD                   ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "  DA----AD                   ",
            "   DAAAAD                    ", "                             ", "D          D                 ",
            "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "D          D                 ", "                             ", "   DAAAAD                    ",
            "  DA----AD                   ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "  DA----AD                   ",
            "   DAAAAD                    ", "                             ", "D          D                 ",
            "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "D          D                 ", "   D    D                    ", "   AAAAAA                    ",
            " DA------AD                  ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", " DA------AD                  ",
            "   AAAAAA                    ", "   D    D                    ", "D          D                 ",
            "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "D          D                 ", "   DAAAAD                    ", "   A----A                    ",
            " DA------AD                  ", " A--------A            AAA   ", " A--------A    AAAA   AEEEA  ",
            " A--------A   AEEEEAAAEEEEEA ", " A--------A   AEEEEAAAEEEEEA ", " DA------AD    AAAA   AEEEA  ",
            "   A----A              AAA   ", "   DAAAAD                    ", "D          D                 ",
            "                             ", "DAAAD                        ", "A---A                        ",
            "A---A             H          ", "A---A            H H         ", "A---A             H          ",
            "A---A                        ", "DAAAD                        " },
        { "D          D                 ", "   DAAAAD                    ", "   A----A                    ",
            " DA------AD                  ", " A--------A    AAAA   A   A  ", " A--------A   A    AAA     A ",
            " A--------A  A              A", " A--------A  A              A", " DA------AD   A    AAA     A ",
            "   A----A      AAAA   A   A  ", "   DAAAAD              AAA   ", "D          D                 ",
            "                             ", "DAAAD                        ", "A---A     F     D   D        ",
            "A---A                        ", "A---A   F   F                ", "A---A                        ",
            "A---A     F     D   D        ", "DAAAD                        " },
        { "D          D                 ", "   DAAAAD                    ", "   A----A                    ",
            " DA------AD                  ", " A--------A    AAAA   A   A  ", " A--------A   A    AAA     A ",
            " A--------A  ABBBB          A", " A--------A  ABBBB          A", " DA------AD   A    AAA     A ",
            "   A----A      AAAA   A   A  ", "   DAAAAD              AAA   ", "D          D                 ",
            "                             ", "DAAAD  D     D               ", "A---A          DD   DD  D    ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A          DD   DD  D    ", "DAAAD  D     D               " },
        { "D          D                 ", "   DAAAAD                    ", "   A----A                    ",
            " DA------AD                  ", " A--------A    EEEE   A   A  ", " A--------A   A    EEE     E ",
            " A--------A  ABBBB          E", " A--------A  ABBBB     A~A  E", " DA------AD   A    EEE     E ",
            "   A----A      EEEE   E   E  ", "   DAAAAD              EEE   ", "D          D                 ",
            "                             ", "DAAAD  DD   DD         D    D", "A---A  D     D DDD DDD  DA   ",
            "A---AEEE     EEEE   EEEEA    ", "A---AEEE     EEEE   EEEEA  G ", "A---AEEE     EEEE   EEEEA    ",
            "A---A  D     D DDD DDD  DA   ", "DAAAD  DD   DD         D    D" },
        { "D   ----   D                 ", "   D----D                    ", "   --ee---                   ",
            " D--eeee--D                  ", "---eeeeee---   AAAA   A   A  ", "--eeeeeeee--  A    AAA     A ",
            "--eeeeeeee-- A  BB          A", "---eeeeee--- A  BB     AAA  A", " D--eeee--D   A    AAA     A ",
            "  ---ee---     AAAA   A   A  ", "   D----D              AAA   ", "D   ----   D                 ",
            "                             ", "DAAAD  DD   DD  DD DD  D   DD", "A---A  DAAAAAD DAAAAAD  AAAAD",
            "A---AEEEAABAAEEEA   AEEAA    ", "A---BBBBBBBBBBBBB   BBBBB    ", "A---AEEEAABAAEEEA   AEEAA    ",
            "A---A  DAAAAAD DAAAAAD  AAAAD", "DAAAD  DD   DD  DD DD  D   DD" },
        { "D   AAAA   D                 ", "   DAAAAD                    ", "   AABBAAA                   ",
            " DAABBBBAAD    AAAA   AAAAA  ", "AAABBBBBBAAA  AAAAAAAAAAAAAA ", "AABBBBBBBBAA AACCCCAAAAAAAAAA",
            "AABBBBBBBBAA AAABBCAAAAAAAAAA", "AAABBBBBBAAA AAABBCAAAAAAAAAA", " DAABBBBAAD  AACCCCAAAAAAAAAA",
            "  AAABBAAA    AAAAAAAAAAAAAA ", "   DAAAAD      AAAA   AAAAA  ", "D   AAAA   D           AAA   ",
            "                             ", "DAAAD  DACCCAD  ACCCA  DACCAD", "ACCCA  AAAAAAA DAAAAAD AAAAAA",
            "ACCCAEEAAAAAAAEEAAAAAEEAAAAAA", "ACCCAAAAAAAAAAAAAAAAAAAAAAAAA", "ACCCAEEAAAAAAA  AAAAAEEAAAAAA",
            "ACCCA  AAAAAAA DAAAAAD AAAAAA", "DAAAD  DACCCAD  ACCCA  DACCAD" } };

    /**
     * 延伸段（15×8×29）＝草稿 {@code plan/蒸汽动力矿物处理物流工程集群-延伸层-修.java} 字面矩阵 +
     * 修正：草稿 F 四格字面保留（y10：深2列10 / 深4列8 / 深4列12 / 深6列10）；草稿 y10 的 H 四格
     * 位改 ' ' skip、y7 的 H 四格位（深3列18 / 深4列17 / 深4列19 / 深5列18）同改 ' ' skip，H 挂点
     * 移至 y9 同列位（与主段 y9 同图案）；G 字面保留（y12 深4 列27）；y3 塔 'AIIIA' 的 15 格 I
     * 归一化为 '-'（严格空气），行 2-6 壁 'A---A' 改 'AeeeA'（粒子候选空气位，每段 15 格）；
     * y12 深3 列0..2 恢复草稿 {@code A--} 字面（P 注入移除，输入仓自由化后由 A 元素统一承载）。
     * 本矩阵与 SHAPE_MAIN 行 12..19 同图案（延伸段即基础层后方同形段，无中央塔）。
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
        { "D   D                        ", "DAAAD                        ", "AeeeA                        ",
            "AeeeA                        ", "AeeeA                        ", "AeeeA                        ",
            "AeeeA                        ", "DAAAD                        " },
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
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A                        ",
            "A---A             H          ", "A---A            H H         ", "A---A             H          ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A     F     D   D        ",
            "A---A                        ", "A---A   F   F                ", "A---A                        ",
            "A---A     F     D   D        ", "DAAAD                        " },
        { "                             ", "DAAAD  D     D               ", "A---A          DD   DD  D    ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A          DD   DD  D    ", "DAAAD  D     D               " },
        { "                             ", "DAAAD  DD   DD         D    D", "A---A  D     D DDD DDD  DA   ",
            "A---AEEE     EEEE   EEEEA    ", "A---AEEE     EEEE   EEEEA  G ", "A---AEEE     EEEE   EEEEA    ",
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
            .addElement('e', isAir())
            .addElement('A', casingOrControllerInputSlot())
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
     * @return {@code -13 - 8k}; iterate's abc[2] is depthRow-offsetC, so extension k occupies
     *         local depth {@code [13+8k, 20+8k]} right behind main's {@code [-7, +12]} without
     *         overlap (the original 20-deep formula, restored as MAIN_DEPTH went 12→20).
     */
    public static int extOffsetC(int k) {
        return MAIN_DEPTH_OFF_SET - MAIN_DEPTH - EXT_DEPTH * k;
    }

    /** Preserves the existing public signature for external callers addressing extension zero. */
    public static int extOffsetC() {
        return extOffsetC(0);
    }

    /**
     * 集群级 FX 粒子候选位（粒子动画精准施加 'e' 标记位，r9）：主段 55 格 + 每延伸段 15 格
     * 'e' 空气位，返回控制器相对 ABC 偏移（客户端按同步到的延伸段数一次性注册到
     * {@link ClusterParticleFx#registerClusterAirCandidates}）。
     *
     * <p>
     * ⚠ 偏移算术强制复用现有偏移推导族：主段用 {@code mainOffsetA/B/C()}，延伸第 k 段用
     * {@code extOffsetA(k)/extOffsetB()/extOffsetC(k)}（与挂点收集/断层探测同一段偏移公式，
     * 禁止手推符号）；控制器相对 ABC 偏移即 {@code (col-offsetA, layer-offsetB, depthRow-offsetC)}，
     * 与单元侧 'e' 候选扫描同构。
     *
     * @param extensionCount 已成型延伸段数（0..{@link ClusterTopology#MAX_EXTENSION_SEGMENTS}，
     *                       越界按上限截断）
     */
    public static List<int[]> clusterAirFxOffsets(int extensionCount) {
        List<int[]> offsets = new ArrayList<>();
        collectAirFxOffsets(SHAPE_MAIN, mainOffsetA(), mainOffsetB(), mainOffsetC(), offsets);
        int k = Math.min(Math.max(extensionCount, 0), ClusterTopology.MAX_EXTENSION_SEGMENTS);
        for (int i = 0; i < k; i++) {
            collectAirFxOffsets(SHAPE_EXT, extOffsetA(i), extOffsetB(), extOffsetC(i), offsets);
        }
        return offsets;
    }

    /** 单段 'e' 位收集：层在前 [layer][depthRow][column]，偏移 = (col-a, layer-b, row-c)。 */
    private static void collectAirFxOffsets(String[][] shape, int offsetA, int offsetB, int offsetC, List<int[]> out) {
        for (int layer = 0; layer < shape.length; layer++) {
            for (int row = 0; row < shape[layer].length; row++) {
                String line = shape[layer][row];
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == 'e') {
                        out.add(new int[] { col - offsetA, layer - offsetB, row - offsetC });
                    }
                }
            }
        }
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
     * A 总控仓室自由化元素：tiered 外壳（默认形态，四族 casing 之一）或 anyOf(标准输入仓 /
     * 蒸汽输入仓 / 耐压蒸汽输入仓)——矩阵内任意 A 位皆可承载输入仓（原 P 专用位已恢复草稿字面，
     * 由本元素统一承载；'P' 字符删除）。禁用 atLeast（GT5U atLeast 是“各元素至少一个”语义，
     * 此处不适用）。自定义 adder：标准输入仓走 {@code addInputHatchToMachineList} 纳入
     * mInputHatches；耐压蒸汽输入仓在本 Enhanced 基座上无标准注册通道（其类不是 MTEHatchInput），
     * updateTexture 接受成型后经 {@code registerPressureSteamHatch} 直收总控
     * pressureSteamHatches 列表（终验反馈 FA：取代旧硬编码偏移枚举收集；checkMachine 复位段先清
     * 列表、失配延伸段由总控 prune 剔除）。数量上限由总控 checkMachine 校验（通用输入仓 1..10、
     * 蒸汽仓类合计 0..10）。
     */
    private static IStructureElement<MTESteamMineralLogisticsCluster> casingOrControllerInputSlot() {
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

    /** A 位（外壳/输入仓两态）注册 adder：见 {@link #casingOrControllerInputSlot()}。casingIndex 可能为 null（不请求贴图）。 */
    private static boolean addControllerInputHatch(MTESteamMineralLogisticsCluster t, IGregTechTileEntity te,
        Short casingIndex) {
        if (te == null) return false;
        IMetaTileEntity mte = te.getMetaTileEntity();
        if (mte instanceof MTEHatchPressureSteamInput hatch) {
            hatch.updateTexture(casingIndex == null ? 0 : casingIndex.intValue());
            t.registerPressureSteamHatch(hatch);
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
     * abc[2] is the registered canonical depth axis: main {@code [-7,+12]} is segment 0; extension k
     * {@code [13+8k, 20+8k]} is {@code k+1}. This is the inverse of {@link #extOffsetC(int)}
     * {@code -13-8k} (formula itself unchanged; only MAIN_DEPTH grew 12→20, so both boundaries
     * shifted by 8 automatically).
     * 包私有：总控 prune 未成型延伸段耐压仓时复用（FA）。
     */
    static int segmentOfWorldPos(MTESteamMineralLogisticsCluster t, int x, int y, int z) {
        IGregTechTileEntity base = t.getBaseMetaTileEntity();
        Vec3Impl abc = t.getExtendedFacing()
            .getOffsetABC(new Vec3Impl(x - base.getXCoord(), y - base.getYCoord(), z - base.getZCoord()));
        int localDepth = abc.get2();
        if (localDepth < MAIN_DEPTH - MAIN_DEPTH_OFF_SET) return SEGMENT_MAIN;
        return 1 + (localDepth - (MAIN_DEPTH - MAIN_DEPTH_OFF_SET)) / EXT_DEPTH;
    }

    private ClusterStructureDef() {}
}
