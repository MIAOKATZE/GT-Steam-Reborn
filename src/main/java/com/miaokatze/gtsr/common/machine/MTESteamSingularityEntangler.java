package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import org.apache.commons.lang3.tuple.Pair;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElementCheckOnly;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.miaokatze.gtsr.api.compat.GTVersionCompat;
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.gui.MTESteamSingularityEntanglerGui;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/** Tier 1 steam entanglement machine. */
public class MTESteamSingularityEntangler extends MTESingularityMachineBase implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 3;
    private static final int VERTICAL_OFF_SET = 19;
    private static final int DEPTH_OFF_SET = 2;

    private static IStructureDefinition<MTESteamSingularityEntangler> STRUCTURE_DEFINITION;

    private int mCasingTierA = -1;
    private int mCasingTierB = -1;
    private int mCasingTierC = -1;
    private int mCasingTierD = -1;

    public MTESteamSingularityEntangler(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        registerProgressEntries();
    }

    public MTESteamSingularityEntangler(String aName) {
        super(aName);
        registerProgressEntries();
    }

    /** 注册终端数值词条（顺序 = GUI 显示顺序；热量 mHeat 口径 0-1，显示 ×100） */
    private void registerProgressEntries() {
        registerEntry("temperature", "gtsr.gui.entangler.heat", "%.1f%%", EnumChatFormatting.RED, () -> mHeat * 100.0d);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamSingularityEntangler(mName);
    }

    @Override
    protected int getRequiredTier() {
        return 1;
    }

    @Override
    protected double getHeatMax() {
        return 0.005d;
    }

    @Override
    protected long getHeatHalfPoint() {
        return 200000L;
    }

    @Override
    protected boolean includeDenseSteam() {
        return false;
    }

    // v1.10.59 单级机器不显示等级
    @Override
    public boolean isHideTierInGui() {
        return true;
    }

    // NEI 展示用伪合成表：实际处理逻辑在 checkProcessing()，本 map 仅用于 NEI 显示。
    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTSRRecipeMaps.steamSingularityEntanglerRecipes;
    }

    @Override
    protected ItemStack getAggregationOutput() {
        return GTSRItemList.SteamEntangledSingularity.get(1);
    }

    @Nullable
    private static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        return null;
    }

    @Nullable
    private static Integer getPipeTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 13) return 1;
        return null;
    }

    @Nullable
    private static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 1;
        return null;
    }

    @Nullable
    private static Integer getGlassTier(Block block, int meta) {
        if (block == GTVersionCompat.getReinforcedGlassBlock() && meta == GTVersionCompat.getReinforcedGlassMeta()) {
            return 1;
        }
        return null;
    }

    private static IStructureDefinition<MTESteamSingularityEntangler> createStructureDefinition() {
        int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
        List<Pair<Block, Integer>> casingTiers = new ArrayList<>();
        casingTiers.add(Pair.of(GregTechAPI.sBlockCasings2, 0));
        List<Pair<Block, Integer>> pipeTiers = new ArrayList<>();
        pipeTiers.add(Pair.of(GregTechAPI.sBlockCasings2, 13));
        List<Pair<Block, Integer>> frameTiers = new ArrayList<>();
        frameTiers.add(Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID));
        List<Pair<Block, Integer>> glassTiers = new ArrayList<>();
        glassTiers.add(Pair.of(GTVersionCompat.getReinforcedGlassBlock(), GTVersionCompat.getReinforcedGlassMeta()));
        // 特殊定位块（导出为泥土）：接受失控奇点方块或空气（砖高炉式容错：机器运行期间此处生成奇点，结构判定仍有效）。
        // noPlacement()：构建/全息投影不放置奇点方块——该位保持空气，奇点仅由机器运行时惰性生成
        IStructureElementCheckOnly<MTESteamSingularityEntangler> singularityLocator = new IStructureElementCheckOnly<MTESteamSingularityEntangler>() {

            @Override
            public boolean check(MTESteamSingularityEntangler t, World world, int x, int y, int z) {
                // 结构判定：接受失控奇点方块或空气（砖高炉式容错：运行期间此处生成奇点，结构判定仍有效）；
                // CheckOnly 不放置：构建/全息投影保持空气，奇点仅由机器运行时惰性生成
                Block block = world.getBlock(x, y, z);
                return block == BlocksGTSR.runawaySingularity || block.isAir(world, x, y, z);
            }
        };

        return StructureDefinition.<MTESteamSingularityEntangler>builder()
            .addShape(
                STRUCTURE_PIECE_MAIN,
                transpose(
                    new String[][] {
                        { "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              " },
                        { "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              ",
                            "                AAAAA         ", "               AAAAAAA        ",
                            "              AAADDDAAA       ", "              AADDDDDAA       ",
                            "              AADDDDDAA       ", "              AADDDDDAA       ",
                            "              AAADDDAAA       ", "               AAAAAAA        ",
                            "                AAAAA         ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              " },
                        { "                              ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              ",
                            "                AAAAA         ", "              AAAAAAAAA       ",
                            "             AAA-----AAA      ", "             AA-------AA      ",
                            "            AA--C---C--AA     ", "            AA---------AA     ",
                            "            AA---------AA     ", "            AA---------AA     ",
                            "            AA--C---C--AA     ", "             AA-------AA      ",
                            "             AAA-----AAA      ", "              AAAAAAAAA       ",
                            "                AAAAA         ", "                              ",
                            "                              ", "                              ",
                            "                              ", "                              " },
                        { "                              ", "                              ",
                            "                              ", "                              ",
                            "                AAAAA         ", "              AAAAAAAAA       ",
                            "            AAAA-----AAAA     ", "            AA---------AA     ",
                            "           AA-----------AA    ", "           AA-----------AA    ",
                            "          AA----C---C----AA   ", "          AA-------------AA   ",
                            "          AA-------------AA   ", "          AA-------------AA   ",
                            "          AA----C---C----AA   ", "           AA-----------AA    ",
                            "           AA-----------AA    ", "            AA---------AA     ",
                            "            AAAA-----AAAA     ", "              AAAAAAAAA       ",
                            "                AAAAA         ", "                              ",
                            "                              ", "                              " },
                        { "                              ", "                              ",
                            "                              ", "                BBBBB         ",
                            "              BBAAAAABB       ", "            BBAAAAAAAAABB     ",
                            "           BAAAA-----AAAAB    ", "           BAA---------AAB    ",
                            "          BAA-----------AAB   ", "          BAA-----------AAB   ",
                            "         BAA----C---C----AAB  ", "         BAA-------------AAB  ",
                            "         BAA-------------AAB  ", "         BAA-------------AAB  ",
                            "         BAA----C---C----AAB  ", "          BAA-----------AAB   ",
                            "          BAA-----------AAB   ", "           BAA---------AAB    ",
                            "           BAAAA-----AAAAB    ", "            BBAAAAAAAAABB     ",
                            "              BBAAAAABB       ", "                BBBBB         ",
                            "                              ", "                              " },
                        { "                              ", "                              ",
                            "                              ", "                AAAAA         ",
                            "              AA-----AA       ", "            AA---------AA     ",
                            "           A-------------A    ", "           A-------------A    ",
                            "          A---------------A   ", "          A---------------A   ",
                            "         A------C---C------A  ", "         A-----------------A  ",
                            "         A-----------------A  ", "         A-----------------A  ",
                            "         A------C---C------A  ", "          A---------------A   ",
                            "          A---------------A   ", "           A-------------A    ",
                            "           A-------------A    ", "            AA---------AA     ",
                            "              AA-----AA       ", "                AAAAA         ",
                            "                              ", "                              " },
                        { "                              ", "                              ",
                            "                BBBBB         ", "              BBAAAAABB       ",
                            "            BBAA-----AABB     ", "           BAA---------AAB    ",
                            "          BA-------------AB   ", "          BA-------------AB   ",
                            "         BA---------------AB  ", "         BA---------------AB  ",
                            "        BA------C---C------AB ", "        BA-----------------AB ",
                            "        BA-----------------AB ", "        BA-----------------AB ",
                            "        BA------C---C------AB ", "         BA---------------AB  ",
                            "         BA---------------AB  ", "          BA-------------AB   ",
                            "          BA-------------AB   ", "           BAA---------AAB    ",
                            "            BBAA-----AABB     ", "              BBAAAAABB       ",
                            "                BBBBB         ", "                              " },
                        { "                              ", "                              ",
                            "                AAAAA         ", "              AA --- AA       ",
                            "            AA---------AA     ", "           A-------------A    ",
                            "          A---------------A   ", "          A---------------A   ",
                            "         A-----------------A  ", "         A-----------------A  ",
                            "        A-------C---C------ A ", "        A-------------------A ",
                            "        A-------------------A ", "        A-------------------A ",
                            "        A ------C---C------ A ", "         A-----------------A  ",
                            "         A-----------------A  ", "          A---------------A   ",
                            "          A---------------A   ", "           A-------------A    ",
                            "            AA---------AA     ", "              AA --- AA       ",
                            "                AAAAA         ", "                              " },
                        { "                              ", "                BAAAB         ",
                            "              BBAAAAABB       ", "            BBAA --- AABB     ",
                            "  DDD      BAA---------AAB    ", "  DDD     BA-------------AB   ",
                            "  DDD    BA---------------AB  ", "  DDD    BA---------------AB  ",
                            "  DDD   BA-----------------AB ", "  DDD   BA-----------------AB ",
                            "  DDD  BA-------C---C------ AB", "  DDDDDAA-------------------AA",
                            "  DDDDDAA-------------------AA", "  DDDDDAA-------------------AA",
                            "       BA ------C---C------ AB", "        BA-----------------AB ",
                            "        BA-----------------AB ", "         BA---------------AB  ",
                            "         BA---------------AB  ", "          BA-------------AB   ",
                            "           BAA---------AAB    ", "            BBAA --- AABB     ",
                            "              BBAAAAABB       ", "                BAAAB         " },
                        { "                              ", "                ADDDA         ",
                            "              AAC---CAA       ", "  DDD       AA--C---C--AA     ",
                            " DBBBD     A----C---C----A    ", " DBBBD    A-----C---C-----A   ",
                            " DBBBD   A------C---C------A  ", " DBBBD   A------C---C------A  ",
                            " DBBBD  A-------C---C-------A ", " DBBBD  A-------C---C-------A ",
                            " DBBBDDACCCCCCCCCCCCCCCCCCCCCA", " DBBBBBB--------C---C--------D",
                            " DBBBBBB--------C---C--------D", " DBBBBBB--------C---C--------D",
                            "  DDDDDACCCCCCCCCCCCCCCCCCCCCA", "        A-------C---C-------A ",
                            "        A-------C---C-------A ", "         A------C---C------A  ",
                            "         A------C---C------A  ", "          A-----C---C-----A   ",
                            "           A----C---C----A    ", "            AA--C---C--AA     ",
                            "              AAC---CAA       ", "                ADDDA         " },
                        { "                              ", "               ADDDDDA        ",
                            "  DDD         AA-----AA       ", " DBBBD      AA---------AA     ",
                            "DB---BD    A-------------A    ", "DB---BD   A---------------A   ",
                            "DB---BD  A-----------------A  ", "DB---BD  A-----------------A  ",
                            "DB---BD A-------------------A ", "DB---BDAA-------------------AA",
                            "DB---BBB--------C---C--------D", "DB---------------------------D",
                            "DB---------------------------D", "DB---------------------------D",
                            " DBBBBBB--------C---C--------D", "  DDDDDAA-------------------AA",
                            "        A-------------------A ", "         A-----------------A  ",
                            "         A-----------------A  ", "          A---------------A   ",
                            "           A-------------A    ", "            AA---------AA     ",
                            "              AA-----AA       ", "               ADDDDDA        " },
                        { "                              ", "              AADDDDDAA       ",
                            "  DDD       AAAA-----AAAA     ", " DBBBD     AAA---------AAA    ",
                            "DB---BD   AA-------------AA   ", "DB---BD  AA---------------AA  ",
                            "DB---BD AA-----------------AA ", "DB---BD BA-----------------AA ",
                            "DB---BDAA-------------------AA", "DB---BDAA-------------------AA",
                            "DB---BBB--------C---C--------D", "DB---------------------------D",
                            "DB----------------E----------D", "DB---------------------------D",
                            " DBBBBBB--------C---C--------D", "  DDDDDAA-------------------AA",
                            "       AA-------------------AA", "        AA-----------------AA ",
                            "        AA-----------------A  ", "         AA---------------A   ",
                            "          AA-------------AA   ", "           AAA---------AAA    ",
                            "            AAAA-----AAAA     ", "              AADDDDDAA       " },
                        { "                              ", "               ADDDDDA        ",
                            "  DDD         AA-----AA       ", " DBBBD      AA---------AA     ",
                            "DB---BD   CA-------------AC   ", "DB---BD   A---------------A   ",
                            "DB---BD  A-----------------A  ", "DB---BD  A-----------------A  ",
                            "DB---BD A-------------------A ", "DB---BDAA-------------------AA",
                            "DB---BBB--------C---C--------D", "DB---------------------------D",
                            "DB---------------------------D", "DB---------------------------D",
                            " DBBBBBB--------C---C--------D", "  DDDDDAA-------------------AA",
                            "        A-------------------A ", "         A-----------------A  ",
                            "         A-----------------A  ", "          A---------------A   ",
                            "          CA-------------AC   ", "            AA---------AA     ",
                            "              AA-----AA       ", "               ADDDDDA        " },
                        { "                              ", "               CADDDAC        ",
                            "  DDD         AAC---CAA       ", " DBBBD      AA--C---C--AA     ",
                            "DB---BD   CA----C---C--  AC   ", "DB---BD   A-----C---C---- A   ",
                            "DB---BD  A------C---C----- A  ", " DBBBD   A------C---C----- A  ",
                            " DBBBD  A ------C---C------ A ", " DBBBD CA ------C---C------ AC",
                            " DBBBDDACCCCCCCCCCCCCCCCCCCCCA", " DBBBBBB--------C---C--------D",
                            " DBBBBBB--------C---C--------D", " DBBBBBB--------C---C--------D",
                            " CDDDDDACCCCCCCCCCCCCCCCCCCCCA", "       CA ------C---C------ AC",
                            "        A ------C---C------ A ", "         A------C---C------A  ",
                            "         A------C---C------A  ", "          A-----C---C-----A   ",
                            "          CA----C---C----AC   ", "            AA--C---C--AA     ",
                            "              AAC---CAA       ", "               CADDDAC        " },
                        { "                              ", "               CBAAABC        ",
                            "  DDD         BBAAAAABB       ", " DBBBD      BBAA-----AABB     ",
                            "DB---BD   CBAA---------AABC   ", "DB---BD   BA-------------AB   ",
                            "DB---BD  BA---------------AB  ", " DBBBD   BA---------------AB  ",
                            "  DDD   BA-----------------AB ", "  DDD  CBA-----------------ABC",
                            " CDDDC BA-------C---C-------AB", "  DDDDDAA-------------------AA",
                            "  DDDDDAA-------------------AA", "  DDDDDAA-------------------AA",
                            " C   C BA-------C---C-------AB", "       CBA-----------------ABC",
                            "        BA-----------------AB ", "         BA---------------AB  ",
                            "         BA---------------AB  ", "          BA-------------AB   ",
                            "          CBAA---------AABC   ", "            BBAA-----AABB     ",
                            "              BBAAAAABB       ", "               CBAAABC        " },
                        { "                              ", "               C     C        ",
                            "  DDD           AAAAA         ", " DBBBD        AA-----AA       ",
                            "DB---BD   C AA---------AA C   ", "DB---BD    A-------------A    ",
                            "DB---BD   A---------------A   ", " DBBBD    A---------------A   ",
                            "  DDD    A-----------------A  ", "       C A-----------------A C",
                            " C   C  A-------C---C-------A ", "        A-------------------A ",
                            "        A-------------------A ", "        A-------------------A ",
                            " C   C  A-------C---C-------A ", "       C A-----------------A C",
                            "         A-----------------A  ", "          A---------------A   ",
                            "          A---------------A   ", "           A-------------A    ",
                            "          C AA---------AA C   ", "              AA-----AA       ",
                            "                AAAAA         ", "               C     C        " },
                        { "                              ", "               C     C        ",
                            "  DDD           BBBBB         ", " DBBBD        BBAAAAABB       ",
                            "DB---BD   C BBAA-----AABB C   ", "DB---BD    BAA---------AAB    ",
                            "DB---BD   BA-------------AB   ", " DBBBD    BA-------------AB   ",
                            "  DDD    BA---------------AB  ", "       C BA---------------AB C",
                            " C   C  BA------C---C------AB ", "        BA-----------------AB ",
                            "        BA-----------------AB ", "        BA-----------------AB ",
                            " C   C  BA------C---C------AB ", "       C BA---------------AB C",
                            "         BA---------------AB  ", "          BA-------------AB   ",
                            "          BA-------------AB   ", "           BAA---------AAB    ",
                            "          C BBAA-----AABB C   ", "              BBAAAAABB       ",
                            "                BBBBB         ", "               C     C        " },
                        { "                              ", "               C     C        ",
                            "  DDD                         ", " DBBBD          AAAAA         ",
                            "DB---BD   C   AA-----AA   C   ", "DB---BD     AA---------AA     ",
                            "DB---BD    A-------------A    ", " DBBBD     A-------------A    ",
                            "  DDD     A---------------A   ", "       C  A---------------A  C",
                            " C   C   A------C---C------A  ", "         A-----------------A  ",
                            "         A-----------------A  ", "         A-----------------A  ",
                            " C   C   A------C---C------A  ", "       C  A---------------A  C",
                            "          A---------------A   ", "           A-------------A    ",
                            "           A-------------A    ", "            AA---------AA     ",
                            "          C   AA-----AA   C   ", "                AAAAA         ",
                            "                              ", "               C     C        " },
                        { "                              ", "               C     C        ",
                            " CAAAC                        ", "DB---BD         BBBBB         ",
                            "DB---BD   C   BBAAAAABB   C   ", "DB---BD     BBAAAAAAAAABB     ",
                            "DB---BD    BAAAA-----AAAAB    ", " DBBBD     BAA---------AAB    ",
                            "  DDD     BAA-----------AAB   ", "       C  BAA-----------AAB  C",
                            " C   C   BAA----C---C----AAB  ", "         BAA-------------AAB  ",
                            "         BAA-------------AAB  ", "         BAA-------------AAB  ",
                            " C   C   BAA----C---C----AAB  ", "       C  BAA-----------AAB  C",
                            "          BAA-----------AAB   ", "           BAA---------AAB    ",
                            "           BAAAA-----AAAAB    ", "            BBAAAAAAAAABB     ",
                            "          C   BBAAAAABB   C   ", "                BBBBB         ",
                            "                              ", "               C     C        " },
                        { "                              ", "               C     C        ",
                            " CA~AC                        ", "DB---BD                       ",
                            "DB---BD   C     AAAAA     C   ", "DB---BD       AAAAAAAAA       ",
                            "DB---BD     AAAA-----AAAA     ", " DBBBD      AA---------AA     ",
                            "  DDD      AA-----------AA    ", "       C   AA-----------AA   C",
                            " C   C    AA----C---C----AA   ", "          AA-------------AA   ",
                            "          AA-------------AA   ", "          AA-------------AA   ",
                            " C   C    AA----C---C----AA   ", "       C   AA-----------AA   C",
                            "           AA-----------AA    ", "            AA---------AA     ",
                            "            AAAA-----AAAA     ", "              AAAAAAAAA       ",
                            "          C     AAAAA     C   ", "                              ",
                            "                              ", "               C     C        " },
                        { "                              ", "               C     C        ",
                            " CAAAC                        ", "DB---BD                       ",
                            "DB---BD   C               C   ", "DB---BD                       ",
                            "DB---BD     C   AAAAA   C     ", " DBBBD        AAAAAAAAA       ",
                            "  DDD        AAA-----AAA      ", "       C     AA-------AA     C",
                            " C   C      AA--C---C--AA     ", "            AA---------AA     ",
                            "            AA---------AA     ", "            AA---------AA     ",
                            " C   C      AA--C---C--AA     ", "       C     AA-------AA     C",
                            "             AAA ----AAA      ", "              AAAAAAAAA       ",
                            "            C   AAAAA   C     ", "                              ",
                            "          C               C   ", "                              ",
                            "                              ", "               C     C        " },
                        { "                              ", "  AAA          C     C        ",
                            " CAAAC                        ", " DBBBD                        ",
                            " DBBBD    C               C   ", " DBBBD                        ",
                            " DBBBD      C           C     ", "  DDD                         ",
                            "                AAAAA         ", "       C       AAAAAAA       C",
                            " C   C        AAADDDAAA       ", "              AADDDDDAA       ",
                            "              AADDDDDAA       ", "              AADDDDDAA       ",
                            " C   C        AAADDDAAA       ", "       C       AAAAAAA       C",
                            "                AAAAA         ", "                              ",
                            "            C           C     ", "                              ",
                            "          C               C   ", "                              ",
                            "                              ", "               C     C        " },
                        { " AAAAA                        ", " AAAAA         C     C        ",
                            " CAAAC                        ", "  DDD                         ",
                            "  DDD     C               C   ", "  DDD                         ",
                            "  DDD       C           C     ", "                              ",
                            "                              ", "       C                     C",
                            " C   C                        ", "                              ",
                            "                              ", "                              ",
                            " C   C                        ", "       C                     C",
                            "                              ", "                              ",
                            "            C           C     ", "                              ",
                            "          C               C   ", "                              ",
                            "                              ", "               C     C        " } }))
            .addElement(
                'A',
                ofChain(
                    ofBlocksTiered(
                        MTESteamSingularityEntangler::getCasingTier,
                        casingTiers,
                        -1,
                        (t, tier) -> t.mCasingTierA = tier,
                        t -> t.mCasingTierA),
                    buildHatchAdder(MTESteamSingularityEntangler.class).atLeast(SingularityHatchElement.SteamInput)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTESteamSingularityEntangler.class).atLeast(SingularityHatchElement.SteamInputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTESteamSingularityEntangler.class).atLeast(SingularityHatchElement.SteamOutputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTESteamSingularityEntangler.class)
                        .atLeast(SingularityHatchElement.SteamOutputHatch)
                        .casingIndex(casingIndex)
                        .hint(2)
                        .build()))
            .addElement(
                'B',
                ofBlocksTiered(
                    MTESteamSingularityEntangler::getPipeTier,
                    pipeTiers,
                    -1,
                    (t, tier) -> t.mCasingTierB = tier,
                    t -> t.mCasingTierB))
            .addElement(
                'C',
                ofBlocksTiered(
                    MTESteamSingularityEntangler::getFrameTier,
                    frameTiers,
                    -1,
                    (t, tier) -> t.mCasingTierC = tier,
                    t -> t.mCasingTierC))
            .addElement(
                'D',
                ofBlocksTiered(
                    MTESteamSingularityEntangler::getGlassTier,
                    glassTiers,
                    -1,
                    (t, tier) -> t.mCasingTierD = tier,
                    t -> t.mCasingTierD))
            .addElement('E', singularityLocator)
            .addElement('-', isAir())
            .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public IStructureDefinition<MTESingularityMachineBase> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) STRUCTURE_DEFINITION = createStructureDefinition();
        return (IStructureDefinition<MTESingularityMachineBase>) (IStructureDefinition<?>) STRUCTURE_DEFINITION;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mCasingTierA = -1;
        mCasingTierB = -1;
        mCasingTierC = -1;
        mCasingTierD = -1;
        mPressureSteamInputs.clear();
        mTier = getRequiredTier();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;

        int tier = mCasingTierA;
        if (mCasingTierB != tier || mCasingTierC != tier || mCasingTierD != tier || tier != getRequiredTier()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        mTier = tier;

        if ((mInputHatches.isEmpty() && mPressureSteamInputs.isEmpty() && mDualInputHatches.isEmpty())
            || mOutputBusses.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        updateAllHatchTextures();
    }

    @Override
    @Nullable
    protected EntanglementSpec getEntanglementSpec() {
        // D 定位块：形状偏移 (a+15, b-8, c+10)，经 ExtendedFacing 换算世界偏移（与 checkPiece 同源映射）
        Vec3Impl off = getExtendedFacing().getWorldOffset(new Vec3Impl(15, -8, 10));
        return new EntanglementSpec(off.get0(), off.get1(), off.get2(), 9.0D, 0.0D, 0.0D, -1, -1, "white", 40.0D);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        String keyPrefix = getTooltipKeyPrefix();
        MultiblockTooltipBuilder tt = super.createTooltip();
        tt.addSeparator()
            .beginStructureBlock(24, 30, 23, false)
            .addController(StatCollector.translateToLocal(keyPrefix + "ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.entangler.steam_input_hatch"),
                StatCollector.translateToLocal(keyPrefix + "steam_input"),
                1)
            .addOutputBus(StatCollector.translateToLocal(keyPrefix + "output_bus"), 1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("gtsr.tooltip.entangler.desc6"))
            .addStructureInfo(
                EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("gtsr.tooltip.entangler.desc6_2"))
            .addStructureInfo(
                EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "tier1_blocks"))
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
        return tt;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        return processAggregationCycle();
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        // Old mode keys are intentionally ignored; this machine is permanently tier 1 aggregation.
        mTier = 1;
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new MTESteamSingularityEntanglerGui(this);
    }
}
