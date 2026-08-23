package com.miaokatze.gtsr.common.machine.cluster;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamInputHatch;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizon.structurelib.util.Vec3Impl;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/**
 * 蒸汽矿物物流集群结构定义：主段 + 可重复延伸段、四族 tier 与 F 单元收集挂点。
 *
 * <h2>布局与偏移推导</h2>
 * 两个矩阵均以层在前书写：{@code SHAPE[layer][depthRow][column]} = {@code [Y][Z][X]}，顶层、
 * 正面行和左列均为下标 0。注册时保留 {@link StructureUtility#transpose(String[][])}，恢复
 * StructureLib canonical 轴序；故 {@code checkPiece(H, V, D)} 偏移仍是控制器的
 * {@code (column, layer, depthRow)}。
 * <p>
 * 主段 15×20×29 中唯一 '~' 位于 {@code (24,12,7)}，即
 * {@code mainOffsetA/B/C = (24,12,7)}。延伸草稿的 canonical {@code [Z][Y][X]} 已严格按
 * {@code (x′,z′)=(Z_max-z,x)} 旋转，成为 15×8×29 的同风格 {@code [Y][Z′][X′]}。其 x 轴与主段
 * 对齐，短 z′ 轴沿主段背面串接：第 k 段的 z′=0 位于主段末行之后第 {@code 1+8k} 格。
 *
 * <h2>F 单元挂点</h2>
 * F 是不校验方块的单元 NAC 挂点：任意世界方块都通过结构检查；若该位置承载集群单元，则按实际
 * {@code instanceof} 类型动态归入工作、增幅或物流垫。自动建造不提示、不放置方块。物流单元必须仍
 * 面向集群局部右侧。
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
 * StructureDefinition 的 F 元素跨 piece 共享，故段号不能由字符闭包注入。检查回调以
 * {@code getOffsetABC(worldDelta)} 的 {@code abc[2]}（局部 depthRow − 偏移C）反推：主段区间
 * {@code [-7,+12]} 为 0；延伸第 k 段区间 {@code [13+8k,20+8k]} 为 {@code k+1}。这与
 * {@link #extOffsetC(int)} 的 {@code -13-8k} 偏移链互逆。总控 {@code addClusterUnit} 保持
 * 三参签名 {@code (unit, padId, segment)}。
 */
public final class ClusterStructureDef {

    public static final String PIECE_MAIN = "main";
    public static final String PIECE_EXT = "ext";
    public static final int SEGMENT_MAIN = 0;

    // controller in layer-first (column, layer, depthRow) = (24, 12, 7)
    private static final int MAIN_HORIZONTAL_OFF_SET = 24;
    private static final int MAIN_VERTICAL_OFF_SET = 12;
    private static final int MAIN_DEPTH_OFF_SET = 7;
    private static final int MAIN_DEPTH = 20;
    private static final int EXT_DEPTH = 8;

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

    // Script-generated from the canonical GTUDK drafts; do not hand-edit.
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
            "D   D                        ", "DAAAD                        ", "AGGGA                        ",
            "AGGGA                        ", "AGGGA                        ", "AGGGA                        ",
            "AGGGA                        ", "DAAAD                        " },
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
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "D          D                 ", "   DAAAAD                    ", "   A----A                    ",
            " DA------AD                  ", " A--------A    AAAA   A   A  ", " A--------A   A    AAA     A ",
            " A--------A  A              A", " A--------A  A              A", " DA------AD   A    AAA     A ",
            "   A----A      AAAA   A   A  ", "   DAAAAD              AAA   ", "D          D                 ",
            "                             ", "DAAAD                        ", "A---A           D   D        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A           D   D        ", "DAAAD                        " },
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
            "                             ", "DAAAD  DD   DD         D    D", "A---A  DFFFFFD DDD DDD  DA   ",
            "A---AEEEFFFFFEEEE   EEEEA    ", "A---AEEEFFFFFEEEE   EEEEA    ", "A---AEEEFFFFFEEEE   EEEEA    ",
            "A---A  DFFFFFD DDD DDD  DA   ", "DAAAD  DD   DD         D    D" },
        { "D   ----   D                 ", "   D----D                    ", "   --GG---                   ",
            " D--GGGG--D                  ", "---GGGGGG---   AAAA   A   A  ", "--GGGGGGGG--  A    AAA     A ",
            "--GGGGGGGG-- A  BB          A", "---GGGGGG--- A  BB     AAA  A", " D--GGGG--D   A    AAA     A ",
            "  ---GG---     AAAA   A   A  ", "   D----D              AAA   ", "D   ----   D                 ",
            "                             ", "DAAAD  DD   DD  DD DD  D   DD", "A---A  DAAAAAD DAAAAAD  AAAAD",
            "A---AEEEAABAAEEEAFFFAEEAAFFF ", "A---BBBBBBBBBBBBBFFFBBBBBFFF ", "A---AEEEAABAAEEEAFFFAEEAAFFF ",
            "A---A  DAAAAAD DAAAAAD  AAAAD", "DAAAD  DD   DD  DD DD  D   DD" },
        { "D   AAAA   D                 ", "   DAAAAD                    ", "   AABBAAA                   ",
            " DAABBBBAAD    AAAA   AAAAA  ", "AAABBBBBBAAA  AAAAAAAAAAAAAA ", "AABBBBBBBBAA AACCCCAAAAAAAAAA",
            "AABBBBBBBBAA AAABBCAAAAAAAAAA", "AAABBBBBBAAA AAABBCAAAAAAAAAA", " DAABBBBAAD  AACCCCAAAAAAAAAA",
            "  AAABBAAA    AAAAAAAAAAAAAA ", "   DAAAAD      AAAA   AAAAA  ", "D   AAAA   D           AAA   ",
            "                             ", "DAAAD  DACCCAD  ACCCA  DACCAD", "ACCCA  AAAAAAA DAAAAAD AAAAAA",
            "ACCCAEEAAAAAAAEEAAAAAEEAAAAAA", "ACCCAAAAAAAAAAAAAAAAAAAAAAAAA", "ACCCAEEAAAAAAA  AAAAAEEAAAAAA",
            "ACCCA  AAAAAAA DAAAAAD AAAAAA", "DAAAD  DACCCAD  ACCCA  DACCAD" } };
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
        { "D   D                        ", "DAAAD                        ", "AGGGA                        ",
            "AGGGA                        ", "AGGGA                        ", "AGGGA                        ",
            "AGGGA                        ", "DAAAD                        " },
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
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A           D   D        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A           D   D        ", "DAAAD                        " },
        { "                             ", "DAAAD  D     D               ", "A---A          DD   DD  D    ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A          DD   DD  D    ", "DAAAD  D     D               " },
        { "                             ", "DAAAD  DD   DD         D    D", "A---A  DFFFFFD DDD DDD  DA   ",
            "A---AEEEFFFFFEEEE   EEEEA    ", "A---AEEEFFFFFEEEE   EEEEA    ", "A---AEEEFFFFFEEEE   EEEEA    ",
            "A---A  DFFFFFD DDD DDD  DA   ", "DAAAD  DD   DD         D    D" },
        { "                             ", "DAAAD  DD   DD  DD DD  D   DD", "A---A  DAAAAAD DAAAAAD  AAAAD",
            "A---AEEEAABAAEEEAFFFAEEAAFFF ", "A---BBBBBBBBBBBBBFFFBBBBBFFF ", "A---AEEEAABAAEEEAFFFAEEAAFFF ",
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
                ofChain(
                    ofBlocksTiered(
                        ClusterStructureDef::getCasingTier,
                        CASING_FAMILY,
                        -1,
                        (t, tier) -> t.mCasingTier = tier,
                        t -> t.mCasingTier),
                    buildHatchAdder(MTESteamMineralLogisticsCluster.class)
                        .atLeast(InputBus, OutputBus, SteamInputHatch, InputHatch, Energy)
                        .casingIndex(BRONZE_CASING_TEXTURE_ID)
                        .hint(1)
                        .build()))
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
            .addElement('F', unitSlot())
            .addElement('G', isAir())
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
     * @return {@code -13 - 8k}; iterate's abc[2] is depthRow-offsetC, so the extension follows
     *         main's local depth range [-7,+12] without overlap.
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
     * F is a no-validation NAC hook. Any block passes; only cluster unit MTEs are collected. All construction
     * paths skip the position, so no block is hinted or placed.
     */
    private static IStructureElement<MTESteamMineralLogisticsCluster> unitSlot() {
        return new IStructureElement<MTESteamMineralLogisticsCluster>() {

            @Override
            public boolean check(MTESteamMineralLogisticsCluster t, World world, int x, int y, int z) {
                TileEntity te = world.getTileEntity(x, y, z);
                if (!(te instanceof IGregTechTileEntity gte)) return true;
                IMetaTileEntity mte = gte.getMetaTileEntity();
                if (!(mte instanceof MTEClusterUnitBase unit)) return true;
                int padId = padIdOf(unit);
                if (padId < 0) return true;
                if (padId == ClusterTopology.PAD_LOGISTICS) {
                    ForgeDirection clusterRight = t.getExtendedFacing()
                        .getWorldDirection(ForgeDirection.EAST);
                    if (unit.getBaseMetaTileEntity()
                        .getFrontFacing() != clusterRight) return false;
                }
                return t.addClusterUnit(unit, padId, segmentOfWorldPos(t, x, y, z));
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

    private static int padIdOf(MTEClusterUnitBase unit) {
        if (unit instanceof MTEBasicProcessingUnit) return ClusterTopology.PAD_WORKING;
        if (unit instanceof MTEBasicAmplifierUnit) return ClusterTopology.PAD_BOOSTER;
        if (unit instanceof MTEBasicLogisticsUnit) return ClusterTopology.PAD_LOGISTICS;
        return -1;
    }

    /**
     * abc[2] is the registered canonical depth axis: main [-7,+12] is segment 0; extension k
     * [13+8k,20+8k] is k+1. This is the inverse of extOffsetC(k).
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
